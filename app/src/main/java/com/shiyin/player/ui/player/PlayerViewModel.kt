package com.shiyin.player.ui.player

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.shiyin.player.ShiYinApp
import com.shiyin.player.data.audio.AudioProbe
import com.shiyin.player.data.download.Downloads
import com.shiyin.player.data.lyrics.Lyrics
import com.shiyin.player.data.lyrics.LyricsCandidate
import com.shiyin.player.data.model.PlaySession
import com.shiyin.player.data.model.Track
import com.shiyin.player.data.model.toMediaItem
import com.shiyin.player.data.model.toTrack
import com.shiyin.player.data.remote.Http
import com.shiyin.player.data.remote.RemoteAuth
import com.shiyin.player.playback.PlaybackService
import com.shiyin.player.playback.SleepTimer
import java.util.concurrent.Executor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 界面与播放服务的桥梁。
 *
 * MediaController 连的是 PlaybackService 里的 ExoPlayer，
 * 所以切页面、退到后台、锁屏都不会打断播放。
 */
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val directExecutor = Executor { it.run() }

    var current by mutableStateOf<Track?>(null)
        private set
    var queue by mutableStateOf<List<Track>>(emptyList())
        private set

    /** 当前播的是队列里的第几首，队列面板拿它来高亮。 */
    var queueIndex by mutableIntStateOf(0)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var shuffleOn by mutableStateOf(false)
        private set
    var repeatMode by mutableStateOf(Player.REPEAT_MODE_OFF)
        private set
    var ready by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    // ---------------- 播放内核 ----------------

    /** 当前倍速（1.0 = 原速）。界面直接读它显示。 */
    var speed by mutableStateOf(1f)
        private set

    /** 淡入淡出时长（毫秒），0 = 关闭。 */
    var fadeMs by mutableStateOf(0)
        private set

    /** 睡眠定时剩余毫秒；-1 表示没开。 */
    var sleepRemainingMs by mutableLongStateOf(-1L)
        private set

    /** 「播完当前这首就停」而不是定时。 */
    var sleepAtTrackEnd by mutableStateOf(false)
        private set

    private var fadeJob: Job? = null

    /** 「音频体检」的结论，显示在播放设置面板底部。 */
    var probeMessage by mutableStateOf<String?>(null)
        private set

    var probing by mutableStateOf(false)
        private set
    private val settings = ShiYinApp.instance.settings

    var lyrics by mutableStateOf<Lyrics?>(null)
        private set
    var lyricsLoading by mutableStateOf(false)
        private set

    /** 在线搜歌词的候选列表 / 是否在搜 / 搜不到时的提示。 */
    var lyricsCandidates by mutableStateOf<List<LyricsCandidate>>(emptyList())
        private set
    var lyricsSearching by mutableStateOf(false)
        private set
    var lyricsSearchMessage by mutableStateOf<String?>(null)
        private set

    private var lyricsTrackId: String? = null

    /** 连续几首放不出来。健康的队列里这个值一开播就归零。 */
    private var errorStreak = 0

    /** 正在从上次的现场恢复，这期间不要回写，否则会把自己写的当成用户操作。 */
    private var restoring = false

    /** 上次落盘的现场指纹，同一格进度不重复写磁盘。 */
    private var lastSavedFingerprint: String? = null

    /** 「加入歌单」的目标曲目，由各页面统一触发，AppRoot 里只挂一个弹窗。 */
    var addTarget by mutableStateOf<Track?>(null)
        private set

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)

        /**
         * 放不出来就自己往下走。
         *
         * 网盘直链过期、文件其实是 DTS 码流、WebDAV 被限速——这些错误以前只会弹一行字，
         * 然后整条队列停在那儿等用户手点。现在自动跳过并说明原因，
         * 连着三首都放不出来才停（多半是断网，再跳也没意义）。
         */
        override fun onPlayerError(error: PlaybackException) {
            val c = controller
            val name = current?.title
            errorStreak++
            if (c != null && errorStreak <= MAX_AUTO_SKIP && c.hasNextMediaItem()) {
                message = "${name ?: "这首"}放不了，已跳过 · ${readableError(error.errorCodeName)}"
                runCatching {
                    c.seekToNextMediaItem()
                    c.prepare()
                    c.play()
                }.onFailure {
                    message = "播放失败：${readableError(error.errorCodeName)}"
                    errorStreak = 0
                }
            } else {
                message = if (errorStreak > 1) {
                    "连续 $errorStreak 首放不出来，先停一下 · ${readableError(error.errorCodeName)}"
                } else {
                    "${name ?: "这首"}放不了 · ${readableError(error.errorCodeName)}"
                }
                errorStreak = 0
            }
        }

        // 起播 / 暂停后恢复 / 切歌，三种情况都要从静音推上来，
        // 否则「淡入」只在第一次播放时有效
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                // 真的出声了才算「这首没问题」。放在 onMediaItemTransition 里会归零得太早，
                // 那样整条队列都是坏文件时就会一路跳到底。
                errorStreak = 0
                startFadeIn()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (controller?.playWhenReady == true) startFadeIn()
        }
    }

    init {
        connect()

        // 倍速和淡入淡出跟着设置走：在设置页改完，播放页立刻就是新值
        viewModelScope.launch {
            settings.playbackSpeed.collect {
                speed = it
                controller?.setPlaybackSpeed(it)
            }
        }
        viewModelScope.launch {
            settings.fadeMs.collect { fadeMs = it }
        }

        viewModelScope.launch {
            while (isActive) {
                // 睡眠定时由播放服务执行（界面划掉也照样生效），这里只负责显示
                sleepRemainingMs = SleepTimer.remainingMs()
                sleepAtTrackEnd = SleepTimer.stopAtTrackEnd
                controller?.let { player ->
                    if (player.isPlaying) positionMs = player.currentPosition.coerceAtLeast(0L)
                }
                delay(500)
            }
        }

        // 现场落盘：每 10 秒看一眼，只有「换歌了 / 进度走了 10 秒以上」才真写
        viewModelScope.launch {
            while (isActive) {
                delay(SESSION_SAVE_INTERVAL_MS)
                saveSessionIfNeeded()
            }
        }
    }

    private fun connect() {
        val app = getApplication<Application>()
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val pending = MediaController.Builder(app, token).buildAsync()
        future = pending
        pending.addListener({
            runCatching {
                val c = pending.get()
                controller = c
                c.addListener(listener)
                c.setPlaybackSpeed(speed)
                sync(c)
                restoreSession(c)
                ready = true
            }.onFailure {
                message = "播放服务启动失败，请重试"
            }
        }, directExecutor)
    }

    private fun sync(player: Player) {
        isPlaying = player.isPlaying
        current = player.currentMediaItem?.toTrack()
        queueIndex = player.currentMediaItemIndex.coerceAtLeast(0)
        val reported = player.duration
        durationMs = if (reported > 0) reported else current?.durationMs ?: 0L
        positionMs = player.currentPosition.coerceAtLeast(0L)
        shuffleOn = player.shuffleModeEnabled
        repeatMode = player.repeatMode
        queue = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).toTrack() }
    }

    /**
     * 接着上次听。
     *
     * 恢复的只是现场：队列、第几首、听到哪儿。不自动播 —— 打开应用就出声是惊吓不是功能。
     * 超过 [PlaySession.MAX_AGE_MS] 的现场直接丢掉。
     */
    private fun restoreSession(c: MediaController) {
        if (queue.isNotEmpty()) return
        viewModelScope.launch {
            val saved = runCatching { settings.sessionNow() }.getOrNull() ?: return@launch
            if (!saved.restorable(System.currentTimeMillis())) {
                runCatching { settings.clearSession() }
                return@launch
            }
            restoring = true
            runCatching {
                c.setMediaItems(saved.tracks.map { Downloads.apply(it).toMediaItem() }, saved.index, saved.positionMs)
                c.repeatMode = saved.repeatMode
                c.shuffleModeEnabled = saved.shuffle
                c.playWhenReady = false
                c.prepare()
            }.onFailure {
                restoring = false
                return@launch
            }
            sync(c)
            restoring = false
            lastSavedFingerprint = null
        }
    }

    private fun saveSessionIfNeeded() {
        if (restoring) return
        val c = controller ?: return
        val items = (0 until c.mediaItemCount).mapNotNull { c.getMediaItemAt(it).toTrack() }
        if (items.isEmpty()) return
        val index = c.currentMediaItemIndex.coerceIn(0, items.lastIndex)
        val position = c.currentPosition.coerceAtLeast(0L)
        // 每 10 秒的进度算同一个指纹：用户听歌时不该每 5 秒写一次磁盘
        val fingerprint = "${items.size}:$index:${items[index].id}:${position / SESSION_POSITION_BUCKET_MS}"
        if (fingerprint == lastSavedFingerprint) return
        lastSavedFingerprint = fingerprint
        viewModelScope.launch {
            runCatching {
                settings.saveSession(
                    PlaySession(
                        tracks = items,
                        index = index,
                        positionMs = position,
                        shuffle = c.shuffleModeEnabled,
                        repeatMode = c.repeatMode,
                        savedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    fun play(tracks: List<Track>, startIndex: Int = 0) {
        val c = controller
        if (tracks.isEmpty()) return
        if (c == null) {
            message = "播放器正在启动，请稍后再试"
            return
        }
        // 已下载的曲目换成 file:// 地址，断网也能放
        val items = tracks.map { Downloads.apply(it).toMediaItem() }
        val index = startIndex.coerceIn(0, items.lastIndex)
        // 新队列就是新现场，别让上一次的指纹挡住这次落盘
        lastSavedFingerprint = null
        c.setMediaItems(items, index, 0L)
        c.prepare()
        c.play()
    }

    /** 当前播放的曲目变了就拉一次歌词，已有缓存不会重复请求。 */
    fun ensureLyrics(track: Track, force: Boolean = false) {
        if (!force && lyricsTrackId == track.id) return
        lyricsTrackId = track.id
        lyrics = null
        lyricsLoading = true
        viewModelScope.launch {
            val loaded = runCatching { ShiYinApp.instance.lyrics.load(track, force) }.getOrNull()
            // 快速切歌时旧请求可能后返回，用 id 对齐丢弃
            if (lyricsTrackId == track.id) {
                lyrics = loaded
                lyricsLoading = false
            }
        }
    }

    // ---------------- 在线搜歌词 ----------------

    fun clearLyricsSearch() {
        lyricsCandidates = emptyList()
        lyricsSearching = false
        lyricsSearchMessage = null
    }

    /**
     * 按关键词在线搜歌词。不传关键词时用当前曲目的「歌名 + 艺人」。
     * 「歌手 - 歌名」的写法会先拆开再去查，命中率比整串丢进去高。
     */
    fun searchLyrics(query: String) {
        val track = current ?: return
        lyricsSearching = true
        lyricsSearchMessage = null
        lyricsCandidates = emptyList()
        viewModelScope.launch {
            val found = runCatching { ShiYinApp.instance.lyrics.search(query, track) }
                .getOrDefault(emptyList())
            lyricsCandidates = found
            lyricsSearchMessage = if (found.isEmpty()) {
                "没搜到歌词。可以换成「歌手 - 歌名」，或直接粘贴自己的 LRC。"
            } else {
                null
            }
            lyricsSearching = false
        }
    }

    fun useCandidate(candidate: LyricsCandidate) {
        val track = current ?: return
        viewModelScope.launch {
            val applied = runCatching { ShiYinApp.instance.lyrics.apply(track, candidate) }.getOrNull()
            if (applied != null) {
                lyrics = applied
                lyricsLoading = false
                clearLyricsSearch()
            } else {
                lyricsSearchMessage = "这条歌词内容为空，换一条试试"
            }
        }
    }

    fun useManualLyrics(raw: String) {
        val track = current ?: return
        viewModelScope.launch {
            val applied = runCatching { ShiYinApp.instance.lyrics.applyManual(track, raw) }.getOrNull()
            if (applied != null) {
                lyrics = applied
                lyricsLoading = false
                clearLyricsSearch()
            } else {
                lyricsSearchMessage = "没有识别到歌词内容"
            }
        }
    }

    // ---------------- 加入歌单 ----------------

    fun requestAddToPlaylist(track: Track) {
        addTarget = track
    }

    fun clearAddTarget() {
        addTarget = null
    }

    /** 当前时间点应该高亮第几行；-1 表示没有可高亮的行（纯文本歌词/前奏）。 */
    fun currentLyricIndex(): Int {
        val lines = lyrics?.lines ?: return -1
        if (lines.isEmpty()) return -1
        var index = -1
        for (i in lines.indices) {
            val time = lines[i].timeMs
            if (time < 0L) return -1
            if (time > positionMs) break
            index = i
        }
        return index
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) {
            pauseWithFade()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    /** 暂停：先淡出再真暂停，避免「啪」一下切断。 */
    fun pauseWithFade() {
        val c = controller ?: return
        if (fadeMs <= 0) {
            c.pause()
            return
        }
        fadeTo(0f, fadeOutDuration()) { c.pause() }
    }

    // ---------------- 倍速 ----------------

    fun applySpeed(value: Float) = viewModelScope.launch {
        settings.savePlaybackSpeed(value)
    }

    // ---------------- 睡眠定时 ----------------

    /**
     * [minutes] 分钟后暂停；传 0 取消。
     *
     * 真正的倒计时在 [SleepTimer] + PlaybackService 里跑：定时是播放的属性，
     * 界面被划掉时不能跟着一起消失。这里只写一个时间戳，界面负责显示。
     */
    fun setSleepTimer(minutes: Int) {
        SleepTimer.set(minutes)
        sleepRemainingMs = SleepTimer.remainingMs()
        sleepAtTrackEnd = SleepTimer.stopAtTrackEnd
    }

    /** 播完当前这首就停：不打断正在放的歌，也不影响已经排好的队列。 */
    fun setSleepOnTrackEnd(enabled: Boolean) {
        SleepTimer.setStopAtTrackEnd(enabled)
        sleepRemainingMs = SleepTimer.remainingMs()
        sleepAtTrackEnd = SleepTimer.stopAtTrackEnd
    }

    // ---------------- 淡入淡出 ----------------

    private fun fadeOutDuration(): Int = fadeMs.coerceIn(250, 800)

    private fun startFadeIn() {
        if (fadeMs <= 0) {
            controller?.volume = 1f
            return
        }
        fadeTo(1f, fadeMs)
    }

    /**
     * 音量包络。
     *
     * 注意这不是「交叉淡入淡出」：真正的 crossfade 需要两个播放器同时出声，
     * ExoPlayer 单实例做不到。这里做的是「新歌淡入 + 暂停/定时淡出」，
     * 切歌时中间会有一瞬间的静音，听感上已经比硬切好很多。
     */
    private fun fadeTo(target: Float, durationMs: Int, then: (() -> Unit)? = null) {
        val c = controller ?: return
        fadeJob?.cancel()
        if (durationMs <= 0) {
            c.volume = target
            then?.invoke()
            return
        }
        fadeJob = viewModelScope.launch {
            val from = c.volume
            val steps = (durationMs / FADE_STEP_MS).coerceAtLeast(4)
            for (i in 1..steps) {
                if (!isActive) return@launch
                c.volume = from + (target - from) * i / steps
                delay(FADE_STEP_MS.toLong())
            }
            c.volume = target
            then?.invoke()
        }
    }

    fun next() {
        controller?.takeIf { it.hasNextMediaItem() }?.seekToNext()
    }

    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 4000) c.seekTo(0) else c.seekToPrevious()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms.coerceAtLeast(0L))
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    // ---------------- 队列 ----------------

    /** 队列面板里点某一行：不是「排队」，是直接跳过去播。 */
    fun playAt(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.seekTo(index, 0L)
        c.prepare()
        c.play()
    }

    /** 各处列表里的「下一首播放」：插到当前曲目后面，不打断正在放的。 */
    fun playNext(track: Track) {
        val c = controller ?: return
        val at = (c.currentMediaItemIndex + 1).coerceIn(0, c.mediaItemCount)
        c.addMediaItem(at, Downloads.apply(track).toMediaItem())
        message = "下一首播放：${track.title}"
    }

    fun moveInQueue(from: Int, to: Int) {
        val c = controller ?: return
        if (from !in 0 until c.mediaItemCount) return
        if (to !in 0 until c.mediaItemCount) return
        if (from == to) return
        c.moveMediaItem(from, to)
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        if (c.mediaItemCount <= 1) {
            c.stop()
            c.clearMediaItems()
            viewModelScope.launch { runCatching { settings.clearSession() } }
            return
        }
        c.removeMediaItem(index)
    }

    /** 把当前这首之后的全部删掉 —— 播到一半发现队列是错的，这个比一首首删快。 */
    fun clearUpNext() {
        val c = controller ?: return
        val from = c.currentMediaItemIndex + 1
        if (from < c.mediaItemCount) c.removeMediaItems(from, c.mediaItemCount)
        message = "已清空待播"
    }

    // ---------------- 音频体检 ----------------

    /**
     * 读当前曲目的文件头，判断它到底是不是它自称的格式。
     *
     * 网盘上一堆「.wav」其实是 DTS 码流：文件名是 wav，header 也写着 PCM，
     * 播出来就是沙沙声，用户在界面上完全看不出原因。这里把结论直接说出来。
     */
    fun probeCurrentAudio() {
        val track = current ?: return
        if (probing) return
        probing = true
        probeMessage = null
        viewModelScope.launch {
            probeMessage = runCatching {
                val url = ShiYinApp.instance.repository.resolvePlayableUrl(track)
                val host = runCatching { Uri.parse(url).host }.getOrNull()
                val headers = buildMap {
                    RemoteAuth.headerFor(host)?.let { put("Authorization", it) }
                }
                val bytes = Http.headBytes(url, 16 * 1024, headers)
                if (bytes == null || bytes.isEmpty()) {
                    "读不到文件头：网络或授权失败"
                } else {
                    when (val verdict = AudioProbe.inspect(bytes)) {
                        is AudioProbe.Verdict.Ok -> "✓ ${verdict.description}，文件头正常"
                        is AudioProbe.Verdict.Suspicious -> "⚠ ${verdict.description}"
                        is AudioProbe.Verdict.Broken -> "✗ ${verdict.description}"
                        is AudioProbe.Verdict.Unknown -> "? ${verdict.description}"
                    }
                }
            }.getOrElse { "检查失败：${it.message ?: it.javaClass.simpleName}" }
            probing = false
        }
    }

    fun consumeMessage(): String? {
        val m = message
        message = null
        return m
    }

    /** 把 Media3 的错误码翻译成人话：这些用户能自己判断要不要处理。 */
    private fun readableError(code: String): String = when {
        code.contains("FILE_NOT_FOUND") || code.contains("BAD_HTTP_STATUS") -> "文件不存在或链接已失效"
        code.contains("NETWORK") || code.contains("TIMEOUT") -> "网络不通"
        code.contains("PERMISSION") || code.contains("IO_NO_PERMISSION") -> "没有读取权限"
        code.contains("DECODING") || code.contains("PARSING_CONTAINER") || code.contains("UNSUPPORTED") ->
            "编码不支持或文件损坏"
        code.contains("BEHIND_LIVE") || code.contains("LIVE_WINDOW") -> "直播窗口已过期"
        else -> code
    }

    private companion object {
        /** 音量包络的步进间隔。40ms ≈ 25 帧每秒，跨进程设置音量也不会卡。 */
        const val FADE_STEP_MS = 40

        /** 连续这么多首放不出来就停下：多半是断网了，再跳也是白跳。 */
        const val MAX_AUTO_SKIP = 3

        const val SESSION_SAVE_INTERVAL_MS = 10_000L

        /** 进度以 10 秒为一格，同一格内不重复落盘。 */
        const val SESSION_POSITION_BUCKET_MS = 10_000L
    }

    override fun onCleared() {
        controller?.release()
        controller = null
        future?.let { MediaController.releaseFuture(it) }
        future = null
        super.onCleared()
    }
}
