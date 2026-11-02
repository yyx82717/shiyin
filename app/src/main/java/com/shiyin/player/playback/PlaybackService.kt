package com.shiyin.player.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.shiyin.player.ShiYinApp
import com.shiyin.player.data.audio.ShiYinAudioChain
import com.shiyin.player.data.remote.Http
import com.shiyin.player.data.remote.RemoteAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * 后台播放服务。
 *
 * MediaSessionService 会自动接管：通知栏播放控制、锁屏控制、蓝牙耳机按键、
 * 来电时自动暂停（音频焦点）、拔出耳机自动暂停（becoming noisy）。
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    /** 淡入淡出时长，跟设置页保持一致；0 表示硬切。 */
    @Volatile
    private var fadeMs = 0

    /**
     * 睡眠定时的检查器。
     *
     * 每秒一次：定时到点、或者「播完这首」已经贴到结尾，就淡出后暂停。
     * 放在服务里，界面被划掉也照常生效。
     */
    private val sleepTicker = object : Runnable {
        override fun run() {
            val player = mediaSession?.player
            if (player != null) checkSleep(player)
            handler.postDelayed(this, 1_000L)
        }
    }

    private companion object {
        const val BAIDU_SCHEME = "baidu"

        /** 「播完这首」的提前量：轮询是 1 秒一次，太贴近结尾会踩空。 */
        const val TRACK_END_MARGIN_MS = 1_200L
    }

    override fun onCreate() {
        super.onCreate()

        val httpFactory = OkHttpDataSource.Factory(Http.client)
            .setUserAgent(Http.USER_AGENT)

        // 只给「需要认证的主机」加 Authorization 头：
        // MediaItem 本身没法带自定义请求头，所以在这里按主机名做一次注入。
        // 本地 content:// 和 Audius 的公网地址都不在表里，不会被带上凭据。
        //
        // 另外这里还负责把 baidu:// 换成真实直链：百度的播放地址 8 小时就过期，
        // 不能存进歌单，只能在真正开播的那一刻现换。换不到就原样放行，
        // 由 ExoPlayer 报错、界面提示，而不是在这里崩掉。
        val authAwareFactory = ResolvingDataSource.Factory(httpFactory) { dataSpec ->
            val uri = dataSpec.uri
            if (uri.scheme == BAIDU_SCHEME) {
                val real = runBlocking {
                    runCatching { ShiYinApp.instance.repository.baiduDlink(uri.toString()) }.getOrNull()
                }
                if (real != null) dataSpec.withUri(Uri.parse(real)) else dataSpec
            } else {
                val header = RemoteAuth.headerFor(uri.host)
                if (header != null) {
                    dataSpec.withRequestHeaders(mapOf("Authorization" to header))
                } else {
                    dataSpec
                }
            }
        }

        val dataSourceFactory = DefaultDataSource.Factory(this, authAwareFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        // 自建渲染器：只为把均衡器挂进音频链。
        // float 输出必须关掉 —— 均衡器处理的是 16-bit PCM，格式对不上会被整体跳过。
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessorChain(ShiYinAudioChain(ShiYinApp.instance.equalizer.processor))
                .setEnableFloatOutput(false)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .build()
        }

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, player).build()

        scope.launch {
            ShiYinApp.instance.settings.fadeMs.collect { fadeMs = it }
        }
        handler.postDelayed(sleepTicker, 1_000L)
    }

    private fun checkSleep(player: Player) {
        val expired = SleepTimer.endAtMs > 0L && System.currentTimeMillis() >= SleepTimer.endAtMs
        var reachedEnd = false
        if (SleepTimer.stopAtTrackEnd && player.isPlaying) {
            val total = player.duration
            if (total > 0L && player.currentPosition >= total - TRACK_END_MARGIN_MS) {
                reachedEnd = true
            }
        }
        if (!expired && !reachedEnd) return

        SleepTimer.clear()
        fadeOutAndPause(player)
    }

    /** 到点时先降音量再暂停：直接 pause 会「咔」一下，半夜尤其明显。 */
    private fun fadeOutAndPause(player: Player) {
        val duration = fadeMs.coerceIn(0, 800)
        if (duration <= 0) {
            player.pause()
            return
        }
        val steps = 12
        val stepMs = (duration / steps).coerceAtLeast(20).toLong()
        val from = player.volume
        for (i in 1..steps) {
            handler.postDelayed(
                { player.volume = from * (1f - i / steps.toFloat()) },
                stepMs * i,
            )
        }
        handler.postDelayed({
            player.pause()
            // 音量必须还原：下次从通知栏点播放时，界面可能还没连上，
            // 届时就没人负责把音量推回来了，听起来就是「没声音」
            player.volume = 1f
        }, stepMs * steps + 30L)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** 用户从最近任务划掉 App 时：没在播就退出服务，在播则继续。 */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        SleepTimer.clear()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
