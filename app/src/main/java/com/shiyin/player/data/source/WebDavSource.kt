package com.shiyin.player.data.source

import android.util.Xml
import com.shiyin.player.data.local.LocalLibraryStore
import com.shiyin.player.data.local.SnapshotAge
import com.shiyin.player.data.model.Track
import com.shiyin.player.data.remote.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

/**
 * WebDAV 网盘音源。
 *
 * 这是「全库」的第三层含义，也是最接近「全平台」的合法做法：
 * 用 AList / Nextcloud / 坚果云 / 群晖 WebDAV，把你各个网盘里的音乐目录挂上来。
 * AList 能同时挂百度网盘、阿里云盘、夸克、OneDrive、Google Drive，
 * 挂好之后这个 App 就能把它们的音乐当一个库来放——文件是你自己的，不涉及抓取别人的版权内容。
 *
 * 实现：PROPFIND Depth:1 逐层遍历，限制目录数/文件数/深度，防止把一个大网盘拖死。
 * XML 解析在 `DavXml.kt`，那边是纯逻辑，有单元测试覆盖。
 */
class WebDavSource(
    private val store: LocalLibraryStore,
    private val provider: suspend () -> WebDavConfig?,
) : MusicSource {

    override val id = "webdav"
    override val label = "网盘音乐"
    override val note = "WebDAV 目录（AList / 坚果云 / Nextcloud 等）"

    @Volatile
    private var cache: List<Track>? = null

    @Volatile
    private var cacheAt = 0L

    /** 磁盘快照是否已经读过（哪怕读出来是空的）。 */
    @Volatile
    private var snapshotLoaded = false

    override suspend fun feed(genre: String?, limit: Int): List<Track> = scan().take(limit)

    override suspend fun search(query: String, limit: Int): List<Track> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        return scan()
            .filter { it.title.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true) }
            .take(limit)
    }

    /**
     * 换了地址就必须连快照一起清掉，否则「网盘」页会一直显示上一个库的歌，
     * 点下去全是 404。只改账号密码则不影响，不用清。
     */
    fun invalidate(clearSnapshot: Boolean = false) {
        cache = null
        cacheAt = 0L
        snapshotLoaded = false
        if (clearSnapshot) store.clear()
    }

    /** 上次扫描完成的时间（磁盘快照的 savedAt），没扫过就是 0。 */
    fun scannedAt(): Long = cacheAt.takeIf { it > 0L } ?: store.read()?.savedAt ?: 0L

    /** 立刻能给出的列表：内存缓存 → 磁盘快照 → 空。不触发遍历，毫秒级。 */
    suspend fun cached(): List<Track> = withContext(Dispatchers.IO) {
        cache?.let { return@withContext it }
        if (provider()?.usable != true) return@withContext emptyList()

        val snapshot = store.read() ?: return@withContext emptyList()
        snapshotLoaded = true
        cache = snapshot.tracks
        cacheAt = snapshot.savedAt
        snapshot.tracks
    }

    /** 完整遍历一遍目录树并更新磁盘快照。 */
    suspend fun refresh(): List<Track> = withContext(Dispatchers.IO) {
        val cfg = provider()?.takeIf { it.usable } ?: return@withContext emptyList()
        val list = runCatching { crawl(cfg) }.getOrNull() ?: return@withContext (cache ?: emptyList())
        cache = list
        cacheAt = System.currentTimeMillis()
        snapshotLoaded = true
        store.write(list)
        list
    }

    /**
     * 扫描整个目录树。
     *
     * 有快照就直接用；进程重启后虽然内存空了，但磁盘快照还在，
     * 所以冷启动进「网盘」是秒出的，不会又等十几秒重爬一遍。
     * 只有从没扫过（第一次用）才真的遍历。
     */
    suspend fun scan(force: Boolean = false): List<Track> {
        if (force) return refresh()
        val hit = cached()
        if (hit.isNotEmpty()) return hit
        // 扫过但一首都扫到（目录空 / 配置不对）：半小时内不反复重试，
        // 过了这个点再试一次，免得用户把 AList 修好了还得手动点「重新扫描」。
        if (snapshotLoaded &&
            SnapshotAge.isFresh(scannedAt(), System.currentTimeMillis(), EMPTY_RETRY_MS)
        ) {
            return emptyList()
        }
        return refresh()
    }

    /** 设置页的「测试并扫描」：只列根目录一层，快速确认地址和账号是否可用。 */
    suspend fun test(cfg: WebDavConfig): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val xml = Http.propfind(cfg.endpoint, cfg.user.ifBlank { null }, cfg.password, "1")
            val entries = parse(xml, cfg.endpoint)
            if (entries.isEmpty()) {
                error("连接成功但没有返回任何条目，确认地址指向的是一个目录")
            }
            val audio = entries.count { !it.isDir && isAudioFileName(it.name) }
            val dirs = entries.count { it.isDir }
            "连接成功：根目录下 $dirs 个文件夹、$audio 个音频文件"
        }
    }

    /**
     * 按层并发爬目录树。
     *
     * 串行时 AList 挂百度网盘那种「一层套一层」的结构，45 个目录要等一分半；
     * 按层切成小批并发（默认 5 路），总耗时降到十几秒。限制并发数是为了别把
     * 网盘服务器打到限流 —— 百度的 API 对突发请求很敏感。
     */
    private suspend fun crawl(cfg: WebDavConfig): List<Track> = coroutineScope {
        val out = ArrayList<Track>()
        val visited = HashSet<String>()
        val user = cfg.user.ifBlank { null }
        var frontier = listOf(cfg.endpoint to 0)
        var dirsScanned = 0
        visited.add(normalize(cfg.endpoint))

        while (frontier.isNotEmpty() && dirsScanned < MAX_DIRS && out.size < MAX_FILES) {
            val batch = frontier.take(MAX_DIRS - dirsScanned)
            dirsScanned += batch.size

            val fetched = batch.chunked(FETCH_CONCURRENCY).flatMap { chunk ->
                chunk.map { (dir, depth) ->
                    async(Dispatchers.IO) {
                        val xml = runCatching { Http.propfind(dir, user, cfg.password, "1") }.getOrNull()
                        Triple(dir, depth, xml)
                    }
                }.awaitAll()
            }

            val next = ArrayList<Pair<String, Int>>()
            for ((dir, depth, xml) in fetched) {
                if (xml == null) continue
                for (entry in parse(xml, dir)) {
                    // 服务器会把请求的这个目录本身也列进来，跳过，否则永远遍历不完
                    if (normalize(entry.href) == normalize(dir)) continue

                    if (entry.isDir) {
                        if (depth >= MAX_DEPTH) continue
                        if (isIgnoredDir(entry.name)) continue
                        if (visited.add(normalize(entry.href))) {
                            next += entry.href to depth + 1
                        }
                    } else if (isAudioFileName(entry.name)) {
                        out += toTrack(entry, cfg.endpoint)
                    }
                }
            }
            frontier = next
        }
        out
    }

    private fun parse(xml: String, base: String): List<DavEntry> =
        parseDavMultiStatus(xml, base, newParser())

    private fun newParser(): XmlPullParser = Xml.newPullParser()

    private fun toTrack(entry: DavEntry, base: String): Track {
        val (artist, title) = splitDavFileName(entry.name)
        // 目录取「相对配置地址」的那一段：用户填的是 .../dav，就不要再把 dav 也显示出来
        val relative = entry.href.removePrefix(normalize(base)).trim('/')
        val folder = relative.substringBeforeLast('/', "").ifBlank { null }
        return Track(
            id = entry.id,
            title = title.ifBlank { entry.name },
            artist = artist.ifBlank { "未知艺人" },
            album = null,
            artwork = null,
            streamUrl = entry.href,
            durationSec = 0L,
            source = id,
            folder = folder,
            format = entry.name.substringAfterLast('.', "").lowercase().ifBlank { null },
        )
    }

    private fun normalize(url: String): String = url.trimEnd('/')

    /** 群晖/威联通/回收站的垃圾目录，进去只会浪费时间。 */
    private fun isIgnoredDir(name: String): Boolean {
        if (name.isBlank()) return true
        if (name.startsWith(".")) return true
        return name.lowercase() in IGNORED_DIRS
    }

    private companion object {
        // AList 同时挂本地库 + 百度网盘时，目录数会到 50 左右，60 不够用。
        const val MAX_DIRS = 150
        const val MAX_FILES = 3000
        const val MAX_DEPTH = 6

        /** 上次扫到 0 首时，隔多久再自动试一次。 */
        const val EMPTY_RETRY_MS = 30 * 60_000L

        /** 同层并发抓取的数量。太高容易被网盘判成爬虫。 */
        const val FETCH_CONCURRENCY = 5

        val IGNORED_DIRS = setOf(
            "@eadir", "#recycle", "#snapshot", "lost+found", "\$recycle.bin",
            "system volume information", ".thumbnails", "thumbnails", "@recently-snapshot",
        )
    }
}
