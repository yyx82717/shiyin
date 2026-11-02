package com.shiyin.player.ui.settings

import android.app.Application
import android.content.IntentSender
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shiyin.player.ShiYinApp
import com.shiyin.player.data.audio.EQ_BAND_COUNT
import com.shiyin.player.data.audio.EQ_MAX_DB
import com.shiyin.player.data.audio.EQ_MIN_DB
import com.shiyin.player.data.audio.EqPreset
import com.shiyin.player.data.audio.EqPresets
import com.shiyin.player.data.audio.EqSettings
import com.shiyin.player.data.download.Downloader
import com.shiyin.player.data.local.SettingsStore
import com.shiyin.player.data.local.SnapshotAge
import com.shiyin.player.data.model.Playlist
import com.shiyin.player.BuildConfig
import com.shiyin.player.data.model.Track
import com.shiyin.player.data.model.UpdateInfo
import com.shiyin.player.data.source.BaiduApi
import com.shiyin.player.data.source.BaiduConfig
import com.shiyin.player.data.source.DeleteOutcome
import com.shiyin.player.data.source.SubsonicConfig
import com.shiyin.player.data.source.WebDavConfig
import com.shiyin.player.data.update.UpdateChecker
import com.shiyin.player.data.update.UpdateManager
import com.shiyin.player.ui.readableMessage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val store: SettingsStore = ShiYinApp.instance.settings
    private val repository = ShiYinApp.instance.repository
    private val downloader = ShiYinApp.instance.downloader

    val themeMode = store.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsStore.THEME_FOLLOW_SYSTEM)
    val favorites = store.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val history = store.history.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlists = store.playlists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val downloads = store.downloads.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val jamendoId = store.jamendoIdFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val localEnabled = store.localEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val subsonic = store.subsonicConfigFlow.stateIn(viewModelScope, SharingStarted.Eagerly, SubsonicConfig())
    val webdav = store.webdavConfigFlow.stateIn(viewModelScope, SharingStarted.Eagerly, WebDavConfig())
    val updateUrl = store.updateUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val autoCheckUpdate = store.autoCheckUpdate.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val downloadProgress: StateFlow<Map<String, Downloader.Progress>> = downloader.progress

    /** 本地曲库，供「我的 - 本地」直接展示。 */
    var localTracks by mutableStateOf<List<Track>>(emptyList())
        private set

    var localLoading by mutableStateOf(false)
        private set

    /** 网盘曲库（WebDAV 扫描结果），供「我的 - 网盘」直接展示。 */
    var remoteTracks by mutableStateOf<List<Track>>(emptyList())
        private set

    var remoteLoading by mutableStateOf(false)
        private set

    /** 上次网盘遍历完成的时间。0 表示还没扫过。 */
    var remoteScannedAt by mutableLongStateOf(0L)
        private set

    /** 「刚刚 / 12 分钟前 / 3 小时前」，给网盘页显示，让用户知道列表有多新。 */
    val remoteScanLabel: String
        get() = SnapshotAge.describe(remoteScannedAt, System.currentTimeMillis())

    // ---------------- 播放内核（均衡器 / 倍速 / 淡入淡出） ----------------

    private val equalizer = ShiYinApp.instance.equalizer

    /** 均衡器设置。「预览」和「保存」分开：拖动滑块时只推给音频链，不写盘。 */
    var eqSettings by mutableStateOf(EqSettings())
        private set

    var playbackSpeed by mutableFloatStateOf(1f)
        private set

    /** 淡入淡出时长（毫秒），0 = 关闭。 */
    var fadeMs by mutableIntStateOf(0)
        private set

    fun setEqEnabled(enabled: Boolean) {
        val next = eqSettings.copy(enabled = enabled)
        eqSettings = next
        equalizer.commit(next)
    }

    /**
     * 改单个频段。[persist] 为 false 时只推给音频链 —— 滑块每移动一格都写盘，
     * DataStore 会被拖垮，而且松开后还要再写一次同样的值。
     */
    fun setEqGain(index: Int, db: Float, persist: Boolean) {
        if (index !in eqSettings.gains.indices) return
        val gains = eqSettings.gains.toMutableList()
        gains[index] = db.coerceIn(EQ_MIN_DB, EQ_MAX_DB)
        val next = eqSettings.copy(gains = gains, preset = EqPresets.CUSTOM)
        eqSettings = next
        if (persist) equalizer.commit(next) else equalizer.preview(next)
    }

    /** 松手时把当前值落盘。不传数值：拖动过程中状态已经是最新的了。 */
    fun commitEqGain(index: Int) {
        if (index !in eqSettings.gains.indices) return
        equalizer.commit(eqSettings)
    }

    fun applyEqPreset(preset: EqPreset) {
        val next = eqSettings.copy(gains = preset.gains, preset = preset.id, enabled = true)
        eqSettings = next
        equalizer.commit(next)
    }

    fun resetEq() {
        val next = eqSettings.copy(gains = List(EQ_BAND_COUNT) { 0f }, preset = EqPresets.FLAT)
        eqSettings = next
        equalizer.commit(next)
    }

    fun applySpeed(speed: Float) {
        val fixed = speed.coerceIn(
            SettingsStore.MIN_SPEED_PERCENT / 100f,
            SettingsStore.MAX_SPEED_PERCENT / 100f,
        )
        playbackSpeed = fixed
        viewModelScope.launch { store.savePlaybackSpeed(fixed) }
    }

    fun applyFade(ms: Int) {
        val fixed = ms.coerceIn(0, SettingsStore.MAX_FADE_MS)
        fadeMs = fixed
        viewModelScope.launch { store.saveFadeMs(fixed) }
    }

    // ---------------- 百度网盘直连 ----------------

    val baiduConfig = store.baiduConfigFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, BaiduConfig())

    /** 百度网盘曲库（直连，不经 AList）。 */
    var baiduTracks by mutableStateOf<List<Track>>(emptyList())
        private set

    var baiduLoading by mutableStateOf(false)
        private set

    var baiduScannedAt by mutableLongStateOf(0L)
        private set

    val baiduScanLabel: String
        get() = SnapshotAge.describe(baiduScannedAt, System.currentTimeMillis())

    /** 删文件的结果提示，界面消费一次就清掉。 */
    var deleteMessage by mutableStateOf<String?>(null)
        private set

    /** Android 10+ 删别人的文件要弹系统确认，这里暂存待确认的曲目。 */
    var pendingDelete by mutableStateOf<Track?>(null)
        private set

    private var consentSender: IntentSender? = null

    /** 正在测试哪个源（"subsonic" / "webdav"），用于按钮转圈。 */
    var testing by mutableStateOf<String?>(null)
        private set

    var testMessage by mutableStateOf<String?>(null)
        private set

    private var lastLocalScan = 0L

    init {
        // 播放设置只需要启动时的一份初值：之后界面状态自己说了算，
        // 不订阅 DataStore（它是全局的，收藏一变动也会触发重发，会把拖动中的值顶回去）
        viewModelScope.launch {
            eqSettings = runCatching { store.eqSettings.first() }.getOrDefault(EqSettings())
            equalizer.preview(eqSettings)
            playbackSpeed = runCatching { store.playbackSpeed.first() }.getOrDefault(1f)
            fadeMs = runCatching { store.fadeMs.first() }.getOrDefault(0)
        }

        // 先用上一次的快照把界面填满，再在后台补扫。
        // 这样冷启动进「本地」不用先看一片空白等扫描。
        viewModelScope.launch {
            localTracks = runCatching { repository.localCached() }.getOrDefault(emptyList())
            if (repository.hasLocalPermission()) {
                val fresh = runCatching { repository.refreshLocal() }.getOrDefault(localTracks)
                localTracks = fresh
                lastLocalScan = System.currentTimeMillis()
            }
        }

        // 网盘同理：读快照是毫秒级的，不等本地媒体库扫完，单独一个协程。
        // 以前这里没有快照，每次重开 App 都要重新遍历一遍 AList，几十秒才出歌。
        viewModelScope.launch {
            val hit = runCatching { repository.webdavCached() }.getOrDefault(emptyList())
            if (hit.isNotEmpty()) {
                remoteTracks = hit
                remoteScannedAt = repository.webdavScannedAt()
            }
        }

        // 百度网盘同样先读快照。这里尤其重要：列一次目录要发几十个请求，
        // 而大多数人每天听的都是同一批歌。
        viewModelScope.launch {
            val hit = runCatching { repository.baiduCached() }.getOrDefault(emptyList())
            if (hit.isNotEmpty()) {
                baiduTracks = hit
                baiduScannedAt = repository.baiduScannedAt()
            }
        }
    }

    fun setTheme(mode: Int) = viewModelScope.launch { store.setThemeMode(mode) }

    fun saveJamendoId(value: String) = viewModelScope.launch { store.setJamendoId(value) }

    fun toggleFavorite(track: Track) = viewModelScope.launch { store.toggleFavorite(track) }

    /** 记录播放历史，并自动收进「默认列表」——不用先建歌单。 */
    fun recordPlay(track: Track) = viewModelScope.launch {
        store.pushHistory(track)
        store.addToDefault(track)
    }

    fun hasLocalPermission(): Boolean = repository.hasLocalPermission()

    fun localPermission(): String = repository.localPermission()

    fun setLocalEnabled(value: Boolean) = viewModelScope.launch { store.setLocalEnabled(value) }

    /**
     * 进入「本地」页时调用。
     *
     * 已经有快照就直接显示（不等扫描），五分钟内不重复扫；
     * 用户手动点「重新扫描」才强制全量刷新。
     */
    fun loadLocal(force: Boolean = false) {
        if (localLoading) return
        val now = System.currentTimeMillis()
        if (!force && now - lastLocalScan < LOCAL_SCAN_TTL_MS) return
        viewModelScope.launch {
            if (!force) {
                runCatching { repository.localCached() }
                    .getOrDefault(emptyList())
                    .takeIf { it.isNotEmpty() }
                    ?.let { localTracks = it }
            }
            localLoading = true
            localTracks = runCatching { repository.localTracks(force) }.getOrDefault(localTracks)
            lastLocalScan = System.currentTimeMillis()
            localLoading = false
        }
    }

    // ---------------- 删除本地原文件 ----------------

    fun deleteLocalTrack(track: Track) {
        viewModelScope.launch {
            when (val outcome = repository.deleteLocal(track)) {
                is DeleteOutcome.Deleted -> {
                    localTracks = localTracks.filterNot { it.id == track.id }
                    deleteMessage = "已删除《${track.title}》"
                }

                is DeleteOutcome.NeedConsent -> {
                    consentSender = outcome.sender
                    pendingDelete = track
                }

                is DeleteOutcome.Failed -> deleteMessage = outcome.reason
            }
        }
    }

    fun consentSender(): IntentSender? = consentSender

    /** 系统弹窗确认后调用：媒体库条目已经没了，只同步本地列表和快照。 */
    fun onLocalDeleted(track: Track) {
        pendingDelete = null
        consentSender = null
        viewModelScope.launch {
            repository.forgetLocal(track)
            localTracks = localTracks.filterNot { it.id == track.id }
            deleteMessage = "已删除《${track.title}》"
        }
    }

    fun cancelDelete() {
        pendingDelete = null
        consentSender = null
    }

    fun consumeDeleteMessage(): String? {
        val message = deleteMessage
        deleteMessage = null
        return message
    }

    fun clearDefaultPlaylist() = viewModelScope.launch { store.clearDefault() }

    fun saveSubsonic(url: String, user: String, password: String) = viewModelScope.launch {
        store.saveSubsonic(SubsonicConfig(url, user, password))
    }

    fun saveWebdav(url: String, user: String, password: String) = viewModelScope.launch {
        // 地址一变就是另一个库了，旧快照必须扔掉，否则列表里全是打不开的旧链接。
        val changed = webdav.value.baseUrl.trim() != url.trim()
        store.saveWebdav(WebDavConfig(url, user, password))
        repository.invalidateWebdav(clearSnapshot = changed)
        if (changed) {
            remoteTracks = emptyList()
            remoteScannedAt = 0L
        }
    }

    fun testSubsonic() = viewModelScope.launch {
        testing = "subsonic"
        testMessage = "正在连接…"
        testMessage = repository.testSubsonic().fold(
            onSuccess = { it },
            onFailure = { "连接失败：${it.readableMessage()}" },
        )
        testing = null
    }

    fun testWebdav() = viewModelScope.launch {
        testing = "webdav"
        testMessage = "正在扫描，目录多的话要等几十秒…"
        testMessage = repository.testWebdav().fold(
            onSuccess = {
                // 连接通了就顺势把整棵树扫完，用户不用再点一次；
                // 否则只会看到「根目录下 N 个文件夹」，以为歌没进来。
                val tracks = runCatching { repository.webdavTracks(force = true) }.getOrDefault(emptyList())
                remoteTracks = tracks
                remoteScannedAt = repository.webdavScannedAt()
                if (tracks.isEmpty()) {
                    "连接成功，但没扫到音频文件。$it"
                } else {
                    "连接成功，已收录 ${tracks.size} 首 —— 去「我的 → 网盘」看"
                }
            },
            onFailure = { "连接失败：${it.readableMessage()}" },
        )
        testing = null
    }

    /**
     * 进入「网盘」页时调用。
     *
     * 有快照就立刻显示，不再等遍历；快照超过半小时才在后台静默重扫一次。
     * 用户想看最新的内容，点「重新扫描」强制全量刷新。
     */
    fun loadRemote(force: Boolean = false) {
        if (remoteLoading) return

        if (!force) {
            // 快照还新鲜，直接用，一次网络请求都不发。
            if (remoteTracks.isNotEmpty() &&
                SnapshotAge.isFresh(remoteScannedAt, System.currentTimeMillis(), REMOTE_STALE_MS)
            ) {
                return
            }
            // 快照旧了：先把旧的显示出来，再后台补一次，不挡着用户点歌。
            if (remoteTracks.isNotEmpty()) {
                silentRefreshRemote()
                return
            }
        }

        viewModelScope.launch {
            if (!force) {
                runCatching { repository.webdavCached() }.getOrDefault(emptyList())
                    .takeIf { it.isNotEmpty() }
                    ?.let {
                        remoteTracks = it
                        remoteScannedAt = repository.webdavScannedAt()
                    }
            }
            remoteLoading = remoteTracks.isEmpty()
            remoteTracks = runCatching { repository.webdavTracks(force) }.getOrDefault(remoteTracks)
            remoteScannedAt = repository.webdavScannedAt()
            remoteLoading = false
        }
    }

    /** 后台补扫：不显示加载层，扫完直接换列表，失败就保留旧结果。 */
    private fun silentRefreshRemote() {
        viewModelScope.launch {
            remoteLoading = true
            val fresh = runCatching { repository.refreshWebdav() }.getOrDefault(emptyList())
            if (fresh.isNotEmpty()) remoteTracks = fresh
            remoteScannedAt = repository.webdavScannedAt()
            remoteLoading = false
        }
    }

    // ---------------- 百度网盘 ----------------

    fun saveBaidu(refreshToken: String, rootDir: String) = viewModelScope.launch {
        store.saveBaidu(BaiduApi.sanitizeCredential(refreshToken), rootDir)
        // 换了授权就是换了账号/目录，旧快照里的 fs_id 在新账号下全都不存在
        repository.invalidateBaidu(clearSnapshot = true)
        baiduTracks = emptyList()
        baiduScannedAt = 0L
    }

    /**
     * 「保存并读取」和「一键授权」共用这一个入口。
     *
     * 用户手上可能有两种东西：一长串 refresh_token，或者百度页面上那串 32 位授权码。
     * 老版本只会把输入当 refresh_token 存下去，于是「把页面上的码填进 token 栏」
     * 这个几乎人人都会做的动作，换来一句「请求参数不合法」，还不知道该改什么。
     * 现在先认形状：是授权码就当场换 token，不用人来分辨。
     */
    fun applyBaiduCredential(raw: String, rootDir: String) {
        val token = BaiduApi.sanitizeCredential(raw)
        when {
            token.isBlank() ->
                testMessage = "先点上面的「一键授权登录」，或者把授权码 / refresh_token 粘到下面的框里。"
            BaiduApi.looksLikeAuthCode(token) -> viewModelScope.launch {
                // 授权码换 token 的流程不会碰目录，这里先把用户改过的目录存下
                store.saveBaiduRoot(rootDir)
                authorizeBaidu(token)
            }
            BaiduApi.looksLikeRefreshToken(token) -> {
                saveBaidu(token, rootDir)
                testBaidu()
            }
            else -> testMessage =
                "这串既不像授权码（32 位字母数字），也不像 refresh_token（以 122. 开头的一长串），" +
                    "检查一下是不是少复制了。"
        }
    }

    fun testBaidu() = viewModelScope.launch {
        testing = "baidu"
        testMessage = "正在授权并读取目录…"
        testMessage = repository.testBaidu().fold(
            onSuccess = { detail ->
                // 通了就顺手把整棵树读完，省得用户以为「配好了但没歌」
                val tracks = runCatching { repository.baiduTracks(force = true) }.getOrDefault(emptyList())
                baiduTracks = tracks
                baiduScannedAt = repository.baiduScannedAt()
                if (tracks.isEmpty()) {
                    "连接成功，但目录里没扫到音频文件。$detail"
                } else {
                    "连接成功，已收录 ${tracks.size} 首 —— 去「我的 → 百度网盘」看"
                }
            },
            onFailure = { "连接失败：${it.readableMessage()}" },
        )
        testing = null
    }

    /**
     * 一键授权拿到授权码后调用。
     *
     * 这里故意不调 [testBaidu]：那会再换一次 access_token，而百度每换一次
     * refresh_token 就轮换一次，多换一次就是多一次被限流的机会。
     * 换成直接读目录，验证和导入一步到位。
     */
    fun authorizeBaidu(code: String) = viewModelScope.launch {
        testing = "baidu"
        testMessage = "正在用授权码换取访问凭证…"
        repository.authorizeBaidu(code).fold(
            onSuccess = {
                repository.invalidateBaidu(clearSnapshot = true)
                baiduTracks = emptyList()
                baiduScannedAt = 0L
                testMessage = "授权成功，正在读取网盘目录…"
                val tracks = runCatching { repository.baiduTracks(force = true) }.getOrDefault(emptyList())
                baiduTracks = tracks
                baiduScannedAt = repository.baiduScannedAt()
                testMessage = if (tracks.isEmpty()) {
                    "授权成功，但目录里没扫到音频文件 —— 检查上面「音乐目录」填得对不对。"
                } else {
                    "授权成功，已收录 ${tracks.size} 首 —— 去「我的 → 百度网盘」看"
                }
            },
            onFailure = { testMessage = "授权失败：${it.readableMessage()}" },
        )
        testing = null
    }

    /**
     * 进入「百度网盘」页时调用。
     *
     * 快照新鲜就直接用，超过半小时才后台补扫 —— 列网盘目录要发几十个请求，
     * 不该每次打开都跑一遍。
     */
    fun loadBaidu(force: Boolean = false) {
        if (baiduLoading) return

        if (!force) {
            if (baiduTracks.isNotEmpty() &&
                SnapshotAge.isFresh(baiduScannedAt, System.currentTimeMillis(), REMOTE_STALE_MS)
            ) {
                return
            }
            if (baiduTracks.isNotEmpty()) {
                viewModelScope.launch {
                    baiduLoading = true
                    val fresh = runCatching { repository.refreshBaidu() }.getOrDefault(emptyList())
                    if (fresh.isNotEmpty()) baiduTracks = fresh
                    baiduScannedAt = repository.baiduScannedAt()
                    baiduLoading = false
                }
                return
            }
        }

        viewModelScope.launch {
            if (!force) {
                runCatching { repository.baiduCached() }.getOrDefault(emptyList())
                    .takeIf { it.isNotEmpty() }
                    ?.let {
                        baiduTracks = it
                        baiduScannedAt = repository.baiduScannedAt()
                    }
            }
            baiduLoading = baiduTracks.isEmpty()
            baiduTracks = runCatching { repository.baiduTracks(force) }.getOrDefault(baiduTracks)
            baiduScannedAt = repository.baiduScannedAt()
            baiduLoading = false
        }
    }

    fun clearTestMessage() {
        testMessage = null
    }

    // ---------------- 下载 ----------------

    fun download(track: Track) = downloader.start(track)

    fun cancelDownload(track: Track) = downloader.cancel(track.id)

    fun retryDownload(track: Track) = downloader.retry(track)

    fun deleteDownload(track: Track) = downloader.delete(track)

    // ---------------- 歌单 ----------------

    fun createPlaylist(name: String, onCreated: (String) -> Unit = {}) {
        viewModelScope.launch { onCreated(store.createPlaylist(name)) }
    }

    /** 从 m3u8 导入：整单一次落盘，避免几十次 DataStore 写入。 */
    fun importPlaylist(name: String, tracks: List<Track>, onCreated: (String) -> Unit = {}) {
        viewModelScope.launch { onCreated(store.createPlaylistWithTracks(name, tracks)) }
    }

    fun renamePlaylist(playlistId: String, name: String) =
        viewModelScope.launch { store.renamePlaylist(playlistId, name) }

    fun deletePlaylist(playlistId: String) =
        viewModelScope.launch { store.deletePlaylist(playlistId) }

    fun addToPlaylist(playlistId: String, track: Track) =
        viewModelScope.launch { store.addToPlaylist(playlistId, track) }

    fun removeFromPlaylist(playlistId: String, trackId: String) =
        viewModelScope.launch { store.removeFromPlaylist(playlistId, trackId) }

    fun playlistById(id: String): Playlist? = playlists.value.firstOrNull { it.id == id }

    // ---------------- 自动更新 ----------------

    /** 当前安装的版本，给设置页和弹窗展示。 */
    val currentVersionName: String = BuildConfig.VERSION_NAME
    val currentVersionCode: Int = BuildConfig.VERSION_CODE

    /** 检查 / 下载过程里的状态机，驱动设置页按钮与提示。 */
    var updateCheckState by mutableStateOf<UpdateCheckState>(UpdateCheckState.Idle)
        private set

    /** 启动或手动检查后发现的新版本；非 null 即表示「有可用更新」。 */
    var availableUpdate by mutableStateOf<UpdateInfo?>(null)
        private set

    /** 下载进度态，驱动弹窗里的进度条。 */
    var downloadState by mutableStateOf<UpdateManager.DownloadState>(UpdateManager.DownloadState.Idle)
        private set

    /**
     * 启动时由宿主调一次：开了自动检查、当天没查过、且填了源，就静默查一次。
     * 失败/无更新一律不声张（silent=true）；只有「发现新版」才通过 [availableUpdate] 冒泡到弹窗。
     */
    fun maybeAutoCheck() {
        viewModelScope.launch {
            val auto = store.autoCheckUpdate.first()
            val url = store.updateUrl.first().trim()
            if (!auto || url.isBlank()) return@launch
            val last = store.lastUpdateCheckAt()
            if (System.currentTimeMillis() - last < 12 * 60 * 60 * 1000L) return@launch
            runCheck(url, silent = true)
        }
    }

    fun setUpdateUrl(url: String) {
        viewModelScope.launch { store.setUpdateUrl(url) }
    }

    fun setAutoCheck(value: Boolean) {
        viewModelScope.launch { store.setAutoCheckUpdate(value) }
    }

    /** 手动点「检查更新」：实时反映状态，失败也给明确提示（不静默）。 */
    fun checkUpdateManually() {
        viewModelScope.launch {
            val url = store.updateUrl.first().trim()
            if (url.isBlank()) {
                updateCheckState = UpdateCheckState.Error("还没填更新源地址")
                return@launch
            }
            runCheck(url, silent = false)
        }
    }

    private suspend fun runCheck(url: String, silent: Boolean) {
        updateCheckState = UpdateCheckState.Checking
        store.markUpdateChecked()
        val info = UpdateChecker.fetch(url)
        if (info == null) {
            updateCheckState = if (silent) UpdateCheckState.Idle
            else UpdateCheckState.Error("拉不到更新信息（检查更新源地址能否访问）")
            return
        }
        if (!UpdateChecker.isNewer(info, currentVersionCode)) {
            updateCheckState = if (silent) UpdateCheckState.Idle else UpdateCheckState.NoUpdate
            return
        }
        // 这个版本被用户「跳过」过，就不再自动弹；手动检查也看不到，避免反复烦
        if (store.skippedVersionCode() == info.versionCode) {
            updateCheckState = UpdateCheckState.Idle
            return
        }
        availableUpdate = info
        updateCheckState = UpdateCheckState.Idle
    }

    /** 弹窗里点「下载」：下完若 sha256 校验通过就停在 Ready，等用户点安装。 */
    fun startDownload() {
        val info = availableUpdate ?: return
        viewModelScope.launch {
            val url = store.updateUrl.first().trim()
            val id = UpdateManager.downloadApk(getApplication(), info, url)
            pollDownload(id, info)
        }
    }

    private suspend fun pollDownload(downloadId: Long, info: UpdateInfo) {
        while (true) {
            delay(400)
            when (val st = UpdateManager.query(getApplication(), downloadId)) {
                is UpdateManager.DownloadState.Running -> downloadState = st
                is UpdateManager.DownloadState.Success -> {
                    downloadState = st
                    if (!UpdateManager.verifySha256(st.file, info.sha256)) {
                        downloadState = UpdateManager.DownloadState.Failed(
                            "安装包校验失败：SHA-256 不一致，可能下载损坏或被篡改",
                        )
                    }
                    return
                }
                is UpdateManager.DownloadState.Failed -> {
                    downloadState = st
                    return
                }
                else -> downloadState = st
            }
        }
    }

    /**
     * 弹窗里点「安装」。返回结果给 UI：拉起成功、需要引导开安装权限、或失败。
     * 用 applicationContext，避免 Activity 销毁后 startActivity 丢失。
     */
    fun installUpdate(): UpdateManager.InstallResult {
        val info = availableUpdate ?: return UpdateManager.InstallResult.Failed("没有可安装的更新")
        val file = (downloadState as? UpdateManager.DownloadState.Success)?.file
            ?: return UpdateManager.InstallResult.Failed("安装包还没下好")
        val result = UpdateManager.install(getApplication(), file)
        if (result is UpdateManager.InstallResult.Failed) {
            downloadState = UpdateManager.DownloadState.Failed(result.reason)
        }
        return result
    }

    /** 「跳过此版本」：记 versionCode，自动检查不再弹它；手动检查也看不到。 */
    fun skipUpdate() {
        val info = availableUpdate ?: return
        viewModelScope.launch { store.skipVersion(info.versionCode) }
        availableUpdate = null
        downloadState = UpdateManager.DownloadState.Idle
    }

    /** 仅仅收起弹窗（没跳过）：保留 availableUpdate，设置页还能提示「有可用更新」。 */
    fun dismissUpdate() {
        downloadState = UpdateManager.DownloadState.Idle
    }

    private companion object {

        /** 刚扫过就不重复扫，避免来回切 tab 反复读媒体库。 */
        const val LOCAL_SCAN_TTL_MS = 5 * 60_000L

        /** 网盘快照多久算过期。过期只在后台补扫，不影响打开页面。 */
        const val REMOTE_STALE_MS = 30 * 60_000L
    }
}

/** 检查更新的状态，给设置页按钮文案和提示用。 */
sealed class UpdateCheckState {
    data object Idle : UpdateCheckState()
    data object Checking : UpdateCheckState()
    data object NoUpdate : UpdateCheckState()
    data class Error(val message: String) : UpdateCheckState()
}
