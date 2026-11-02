package com.shiyin.player.data.remote

/**
 * 播放器需要的鉴权头登记表。
 *
 * ExoPlayer 的 MediaItem 不能直接携带自定义请求头，而 WebDAV 网盘基本都要 Basic 认证。
 * 所以这里维护一张「主机 -> Authorization」表：
 *   - App 启动 / 设置变更时由 ShiYinApp 写入
 *   - PlaybackService 用 ResolvingDataSource 按 URL 主机名附加请求头
 * 这样只给用户自己的网盘发凭据，不会把密码带给 Audius 之类的第三方。
 */
object RemoteAuth {

    @Volatile
    private var table: Map<String, String> = emptyMap()

    fun update(entries: Map<String, String>) {
        table = entries.mapKeys { it.key.lowercase() }
    }

    fun headerFor(host: String?): String? {
        if (host.isNullOrBlank()) return null
        return table[host.lowercase()]
    }
}
