package com.shiyin.player.data.repository

import android.content.Context
import com.shiyin.player.data.local.LocalLibraryStore
import com.shiyin.player.data.local.SettingsStore
import com.shiyin.player.data.model.Track
import com.shiyin.player.data.source.AudiusSource
import com.shiyin.player.data.source.BaiduSource
import com.shiyin.player.data.source.JamendoSource
import com.shiyin.player.data.source.LocalSource
import com.shiyin.player.data.source.MusicSource
import com.shiyin.player.data.source.SubsonicSource
import com.shiyin.player.data.source.WebDavSource

/**
 * 聚合所有音源。任何一个源挂掉都不影响其它源，只有全部失败才把错误抛给界面。
 *
 * 「发现」走网络推荐（Audius / Jamendo）；
 * 「搜索」把本地库、自建曲库、网盘一起搜，谁先有我就能播谁的。
 */
class MusicRepository(
    context: Context,
    private val settings: SettingsStore,
) {

    private val audius = AudiusSource()
    private val jamendo = JamendoSource { settings.jamendoId() }
    private val local = LocalSource(context)
    private val subsonic = SubsonicSource { settings.subsonicConfig() }
    private val webdav = WebDavSource(
        LocalLibraryStore(context, LocalLibraryStore.WEBDAV_FILE),
    ) { settings.webdavConfig() }
    private val baidu = BaiduSource(
        LocalLibraryStore(context, LocalLibraryStore.BAIDU_FILE),
        loadConfig = { settings.baiduConfig() },
        saveToken = { access, refresh, at -> settings.saveBaiduToken(access, refresh, at) },
    )

    suspend fun feed(genre: String?): Result<List<Track>> = collect(remoteSources()) { source ->
        source.feed(genre, PAGE_SIZE)
    }

    suspend fun search(query: String): Result<List<Track>> = collect(searchSources()) { source ->
        source.search(query, PAGE_SIZE)
    }

    /** 本地音乐全量列表，供「我的 - 本地」使用。 */
    suspend fun localTracks(force: Boolean = false): List<Track> = local.all(force)

    /** 上一次扫描的落盘快照，冷启动时立刻有内容可显示。 */
    suspend fun localCached(): List<Track> = local.cached()

    /** 重新扫一遍媒体库（后台静默调用）。 */
    suspend fun refreshLocal(): List<Track> = local.refresh()

    suspend fun deleteLocal(track: Track) = local.delete(track)

    suspend fun forgetLocal(track: Track) = local.forget(track)

    fun hasLocalPermission(): Boolean = local.hasPermission()

    fun localPermission(): String = local.requiredPermission()

    fun invalidateLocal() = local.invalidate()

    fun invalidateWebdav(clearSnapshot: Boolean = false) = webdav.invalidate(clearSnapshot)

    /**
     * 网盘曲库全量。「我的 → 网盘」用它。
     *
     * WebDAV 没有「曲库」概念，只能遍历目录树，所以这里有 150 个目录 / 3000 首 / 6 层的硬上限。
     * 遍历结果会落盘（webdav_library.json），冷启动直接读快照，不用每次重爬。
     */
    suspend fun webdavTracks(force: Boolean = false): List<Track> = webdav.scan(force)

    /** 上一次扫描的落盘快照，冷启动时立刻有内容可显示。 */
    suspend fun webdavCached(): List<Track> = webdav.cached()

    /** 重新遍历一遍网盘目录。 */
    suspend fun refreshWebdav(): List<Track> = webdav.refresh()

    /** 上次遍历完成的时间戳，界面用来显示「x 分钟前」。 */
    fun webdavScannedAt(): Long = webdav.scannedAt()

    // ---------------- 百度网盘直连 ----------------

    suspend fun baiduTracks(force: Boolean = false): List<Track> = baidu.scan(force)

    suspend fun baiduCached(): List<Track> = baidu.cached()

    suspend fun refreshBaidu(): List<Track> = baidu.refresh()

    fun baiduScannedAt(): Long = baidu.scannedAt()

    fun invalidateBaidu(clearSnapshot: Boolean = false) = baidu.invalidate(clearSnapshot)

    suspend fun saveBaidu(refreshToken: String, rootDir: String) = settings.saveBaidu(refreshToken, rootDir)

    /** 授权码换 token（设置页「一键授权」）。成功后 refresh_token 已经写进设置里。 */
    suspend fun authorizeBaidu(code: String): Result<String> = baidu.authorize(code)

    suspend fun testBaidu(): Result<String> {
        val cfg = settings.baiduConfig()
            ?: return Result.failure(IllegalStateException("请先填写 refresh_token"))
        return baidu.test(cfg)
    }

    /**
     * 把 `baidu://` 播放地址换成真实直链。
     *
     * 播放器（[com.shiyin.player.playback.PlaybackService]）和下载器都走这里，
     * 保证「能播的也能下」。
     */
    suspend fun baiduDlink(streamUrl: String): String? = baidu.dlinkFor(streamUrl)

    /** 下载/播放前的统一入口：百度地址现换直链，其它源原样返回。 */
    suspend fun resolvePlayableUrl(track: Track): String =
        if (track.streamUrl.startsWith("baidu://")) baiduDlink(track.streamUrl) ?: track.streamUrl
        else track.streamUrl

    suspend fun testSubsonic(): Result<String> {
        // 地址被清空 = 用户想关掉这个源，不是填错。
        val config = settings.subsonicConfig()
            ?: return Result.success("已清空，这一栏不再参与搜索")
        // 最常见的误填：把 AList / 坚果云的 WebDAV 地址填进了 Subsonic。
        // 两套协议完全不同（Subsonic 走 /rest/*.view，WebDAV 走 PROPFIND），永远连不上。
        val endpoint = config.endpoint.lowercase()
        if (endpoint.endsWith("/dav") || endpoint.contains("/dav/") ||
            endpoint.endsWith("/webdav") || endpoint.contains("/webdav/")
        ) {
            return Result.failure(
                IllegalStateException(
                    "这个地址是 WebDAV 的，Subsonic 连不上。\n" +
                        "AList 只提供 WebDAV，没有 Subsonic 接口 —— 请把地址清空，留这一栏不用即可。\n" +
                        "真要用 Subsonic，得另外装 Navidrome（默认端口 4533）。",
                ),
            )
        }
        return subsonic.test(config)
    }

    suspend fun testWebdav(): Result<String> {
        val config = settings.webdavConfig()
            ?: return Result.failure(IllegalStateException("请先填写 WebDAV 地址"))
        return webdav.test(config)
    }

    /** 网盘扫描比较慢，设置页保存后可以主动触发一次，把结果预热进缓存。 */
    suspend fun prefetchWebdav(): Result<Int> = runCatching {
        webdav.scan(force = true).size
    }

    private suspend fun remoteSources(): List<MusicSource> {
        val list = mutableListOf<MusicSource>(audius)
        if (settings.jamendoId().isNotBlank()) list += jamendo
        return list
    }

    private suspend fun searchSources(): List<MusicSource> {
        val list = mutableListOf<MusicSource>()
        list += remoteSources()
        if (settings.isLocalEnabled() && local.hasPermission()) list += local
        if (settings.subsonicConfig() != null) list += subsonic
        if (settings.webdavConfig() != null) list += webdav
        if (settings.baiduConfig() != null) list += baidu
        return list
    }

    private suspend fun collect(
        sources: List<MusicSource>,
        block: suspend (MusicSource) -> List<Track>,
    ): Result<List<Track>> {
        val merged = ArrayList<Track>()
        var lastError: Throwable? = null
        for (source in sources) {
            try {
                merged += block(source)
            } catch (t: Throwable) {
                lastError = t
            }
        }
        if (merged.isEmpty() && lastError != null) {
            return Result.failure(lastError)
        }
        return Result.success(merged.distinctBy { it.id })
    }

    companion object {
        private const val PAGE_SIZE = 30
    }
}
