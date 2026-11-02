package com.shiyin.player

import android.app.Application
import com.shiyin.player.data.audio.EqualizerController
import com.shiyin.player.data.download.Downloader
import com.shiyin.player.data.download.Downloads
import com.shiyin.player.data.local.SettingsStore
import com.shiyin.player.data.lyrics.LyricsRepository
import com.shiyin.player.data.remote.Http
import com.shiyin.player.data.remote.RemoteAuth
import com.shiyin.player.data.repository.MusicRepository
import com.shiyin.player.data.source.WebDavConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl

class ShiYinApp : Application() {

    val settings: SettingsStore by lazy { SettingsStore(this) }
    val repository: MusicRepository by lazy { MusicRepository(this, settings) }
    val lyrics: LyricsRepository by lazy { LyricsRepository(this) }
    val downloader: Downloader by lazy { Downloader(this, settings) }
    val equalizer: EqualizerController by lazy { EqualizerController(settings) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // WebDAV 的账号一旦变化就刷新播放器用的鉴权表，
        // 这样 ExoPlayer 拉网盘音频时才能带上 Authorization。
        scope.launch {
            settings.webdavConfigFlow.collect { config -> syncRemoteAuth(config) }
        }

        // 下载索引：决定播放时是否走本地文件
        scope.launch {
            settings.downloads.collect { entries -> Downloads.update(entries) }
        }
    }

    private fun syncRemoteAuth(config: WebDavConfig) {
        val table = HashMap<String, String>()
        if (config.usable && config.user.isNotBlank()) {
            val host = runCatching { config.endpoint.toHttpUrl().host }.getOrNull()
            if (host != null) table[host] = Http.basicAuth(config.user, config.password)
        }
        RemoteAuth.update(table)
    }

    companion object {
        lateinit var instance: ShiYinApp
            private set
    }
}
