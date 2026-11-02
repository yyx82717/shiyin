package com.shiyin.player.data.source

import android.util.Log
import com.shiyin.player.data.local.LocalLibraryStore
import com.shiyin.player.data.local.SnapshotAge
import com.shiyin.player.data.model.Track
import com.shiyin.player.data.remote.Http
import com.shiyin.player.data.remote.HttpStatusException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 百度网盘音源：**直连**，不需要 AList、不需要自建服务器、不需要任何设备常开。
 *
 * 别的网盘音源（[WebDavSource]）走的是 WebDAV 标准协议，必须有中间层把网盘翻译成
 * WebDAV；百度网盘没有 WebDAV，所以以前只能靠 AList。这里换了个思路：
 * 直接说百度 App 自己的语言，问它要播放地址。
 *
 * 三件事：
 * 1. refresh_token 换 access_token（轮换后写回，否则下次无法授权）；
 * 2. 递归列目录，结果落盘快照 —— 冷启动秒出，不用每次重扫；
 * 3. 播放时现取直链（[dlink]）。直链 8 小时过期，绝不能存进歌单。
 */
class BaiduSource(
    private val store: LocalLibraryStore,
    private val loadConfig: suspend () -> BaiduConfig?,
    private val saveToken: suspend (access: String, refresh: String, at: Long) -> Unit,
) : MusicSource {

    override val id = "baidu"
    override val label = "百度网盘"
    override val note = "直接读取你自己网盘里的音乐目录，不需要 AList 中转"

    @Volatile
    private var cache: List<Track>? = null

    @Volatile
    private var cacheAt = 0L

    @Volatile
    private var snapshotLoaded = false

    /** 刷新 token 必须串行：百度每刷新一次就作废旧 refresh_token，并发会把彼此踢下线。 */
    private val tokenLock = Mutex()

    /**
     * 授权失败后的冷却截止时间。
     *
     * 百度对「短时间反复刷新 token」有安全策略，撞上之后继续猛试只会把冷却越拖越长。
     * 失败一次就停五分钟，期间直接把上次的原因抛给界面，一次请求都不发。
     */
    @Volatile
    private var blockedUntil = 0L

    @Volatile
    private var blockedReason: String? = null

    /** 直链缓存 fsId → (地址, 取到的时间)。8 小时有效，留 7 小时余量。 */
    private val links = ConcurrentHashMap<Long, CachedLink>()

    private data class CachedLink(val url: String, val at: Long)

    // ---------------- MusicSource ----------------

    override suspend fun feed(genre: String?, limit: Int): List<Track> = scan().take(limit)

    override suspend fun search(query: String, limit: Int): List<Track> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        return scan()
            .filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.artist.contains(q, ignoreCase = true)
            }
            .take(limit)
    }

    // ---------------- 快照 ----------------

    fun invalidate(clearSnapshot: Boolean = false) {
        cache = null
        cacheAt = 0L
        snapshotLoaded = false
        links.clear()
        // 换了授权就重新试一次，别让上一份 token 的冷却期挡住新的
        blockedUntil = 0L
        blockedReason = null
        if (clearSnapshot) {
            store.clear()
        }
    }

    fun scannedAt(): Long = cacheAt.takeIf { it > 0L } ?: store.read()?.savedAt ?: 0L

    suspend fun cached(): List<Track> = withContext(Dispatchers.IO) {
        cache?.let { return@withContext it }
        if (loadConfig()?.usable != true) return@withContext emptyList()
        val snapshot = store.read() ?: return@withContext emptyList()
        snapshotLoaded = true
        cache = snapshot.tracks
        cacheAt = snapshot.savedAt
        snapshot.tracks
    }

    suspend fun refresh(): List<Track> = withContext(Dispatchers.IO) {
        val cfg = loadConfig()?.takeIf { it.usable } ?: return@withContext emptyList()
        val list = runCatching { crawl(cfg) }.getOrNull() ?: return@withContext (cache ?: emptyList())
        cache = list
        cacheAt = System.currentTimeMillis()
        snapshotLoaded = true
        store.write(list)
        list
    }

    suspend fun scan(force: Boolean = false): List<Track> {
        if (force) return refresh()
        val hit = cached()
        if (hit.isNotEmpty()) return hit
        if (snapshotLoaded &&
            SnapshotAge.isFresh(scannedAt(), System.currentTimeMillis(), EMPTY_RETRY_MS)
        ) {
            return emptyList()
        }
        return refresh()
    }

    // ---------------- 授权 ----------------

    /** 拿到可用的 access_token；没有就现换一次并写回。 */
    suspend fun accessToken(): String = tokenLock.withLock {
        val cfg = loadConfig() ?: error("还没有配置百度网盘")
        val refresh = BaiduApi.sanitizeCredential(cfg.refreshToken)
        if (refresh.isBlank()) error("还没有填写 refresh_token")
        if (cfg.tokenFresh) return cfg.accessToken
        if (System.currentTimeMillis() < blockedUntil) {
            error(blockedReason ?: "百度授权暂时不可用，稍后再试")
        }

        val body = try {
            Http.get(BaiduApi.tokenUrl(refresh))
        } catch (e: HttpStatusException) {
            // 400 的 body 里才写着真实原因（invalid_grant / Trigger security policy…）
            fail(BaiduApi.tokenError(e.body) ?: "百度授权服务器返回 ${e.code}")
        }
        BaiduApi.tokenError(body)?.let { fail(it) }
        val token = BaiduApi.parseToken(body)
            ?: fail("授权服务器返回的内容无法识别，先确认 refresh_token 复制完整（以 122. 开头的一长串）")
        // 百度的 refresh_token 是一次性的：刷新后旧的立刻作废，必须马上存新的
        saveToken(token.accessToken, token.refreshToken, System.currentTimeMillis())
        token.accessToken
    }

    /**
     * 用授权码换 token（设置页的「一键授权」走这里）。
     *
     * 授权码是**一次性**的，换成功就立刻把 refresh_token 落盘；失败也记冷却，
     * 免得用户反复点按钮把一个还没过期的授权码也刷废。
     */
    suspend fun authorize(code: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = BaiduApi.sanitizeCredential(code)
            // 先本地挡一道：把明显不是授权码的东西发出去，只会换来一句「参数不合法」
            if (!BaiduApi.looksLikeAuthCode(clean)) {
                error(
                    if (clean.isBlank()) "没有拿到授权码"
                    else "这串不是百度授权码（应该是 32 位字母数字）。取消后重新点「一键授权登录」即可。"
                )
            }
            val body = try {
                Http.get(BaiduApi.exchangeUrl(clean))
            } catch (e: HttpStatusException) {
                fail(BaiduApi.tokenError(e.body, forAuthCode = true) ?: "百度授权服务器返回 ${e.code}")
            }
            BaiduApi.tokenError(body, forAuthCode = true)?.let { fail(it) }
            val token = BaiduApi.parseToken(body)
                ?: fail("授权码无效或已被使用（每个授权码只能换一次，重新授权即可）")
            saveToken(token.accessToken, token.refreshToken, System.currentTimeMillis())
            blockedUntil = 0L
            blockedReason = null
            token.refreshToken
        }
    }

    /** 记下失败原因、进入冷却，然后把消息抛出去。冷却避免后台反复重试把限流拖长。 */
    private fun fail(message: String): Nothing {
        blockedReason = message
        blockedUntil = System.currentTimeMillis() + AUTH_COOLDOWN_MS
        error(message)
    }

    /** 设置页的「保存并测试」：换 token、列一次根目录，返回能看到的文件数。 */
    suspend fun test(cfg: BaiduConfig): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val refresh = BaiduApi.sanitizeCredential(cfg.refreshToken)
            if (!BaiduApi.looksLikeRefreshToken(refresh)) {
                error("refresh_token 看着不像真的（应该以 122. 开头、一长串）。只填页面上的 32 位授权码也行，App 会自动换。")
            }
            val body = try {
                Http.get(BaiduApi.tokenUrl(refresh))
            } catch (e: HttpStatusException) {
                fail(BaiduApi.tokenError(e.body) ?: "百度授权服务器返回 ${e.code}")
            }
            BaiduApi.tokenError(body)?.let { fail(it) }
            val token = BaiduApi.parseToken(body)
                ?: fail("refresh_token 无效或已过期")
            saveToken(token.accessToken, token.refreshToken, System.currentTimeMillis())
            // 这一次成功了，说明授权是好的，清掉之前的冷却
            blockedUntil = 0L
            blockedReason = null

            val listBody = try {
                Http.get(BaiduApi.listUrl(token.accessToken, cfg.rootDir, 0, 1000))
            } catch (e: HttpStatusException) {
                fail("列目录被拒绝（${e.code}）：${e.body.take(120)}")
            }
            val errno = BaiduApi.errnoOf(listBody)
            if (errno != 0) fail(BaiduApi.errorMessage(errno))
            val entries = BaiduApi.parseList(listBody) ?: error("目录内容无法解析")
            val dirs = entries.count { it.isDir }
            val audio = entries.count { !it.isDir && BaiduApi.isAudioFile(it.name) }
            "连接成功：${cfg.rootDir} 下 $dirs 个文件夹、$audio 首音频"
        }
    }

    // ---------------- 遍历 ----------------

    private suspend fun crawl(cfg: BaiduConfig): List<Track> {
        val token = accessToken()
        val out = ArrayList<Track>()
        val visited = HashSet<String>()
        var frontier = listOf(cfg.rootDir.trimEnd('/').ifBlank { "/" })
        var dirsScanned = 0

        while (frontier.isNotEmpty() && dirsScanned < MAX_DIRS && out.size < MAX_FILES) {
            val batch = frontier.take(MAX_DIRS - dirsScanned)
            dirsScanned += batch.size
            val next = ArrayList<String>()

            for (dir in batch) {
                var start = 0
                while (out.size < MAX_FILES) {
                    val body = runCatching {
                        Http.get(BaiduApi.listUrl(token, dir, start, PAGE_SIZE))
                    }.getOrNull() ?: break
                    val entries = BaiduApi.parseList(body) ?: break

                    for (entry in entries) {
                        if (entry.isDir) {
                            if (visited.add(entry.path)) next += entry.path
                        } else if (BaiduApi.isAudioFile(entry.name)) {
                            if (out.size >= MAX_FILES) break
                            out += toTrack(entry)
                        }
                    }
                    if (entries.size < PAGE_SIZE) break
                    start += PAGE_SIZE
                }
            }
            frontier = next
        }
        return out
    }

    private fun toTrack(entry: BaiduApi.BaiduEntry): Track {
        val (artist, title) = BaiduApi.splitFileName(entry.name)
        return Track(
            id = BaiduApi.trackId(entry.fsId),
            title = title.ifBlank { entry.name },
            artist = artist.ifBlank { "未知艺人" },
            album = entry.path.substringBeforeLast('/', "").substringAfterLast('/').ifBlank { null },
            artwork = null,
            streamUrl = BaiduApi.streamUrl(entry.fsId, entry.path),
            durationSec = 0L,
            source = id,
            folder = entry.path.trim('/').substringBeforeLast('/', "").ifBlank { null },
            format = entry.name.substringAfterLast('.', "").lowercase().ifBlank { null },
        )
    }

    // ---------------- 直链 ----------------

    /**
     * 把 `baidu://` 地址换成真实可播的直链。
     *
     * 直链 8 小时过期，所以缓存 7 小时；过期的下次播放重新取。
     * 取不到返回 null，由调用方决定是跳过还是提示。
     */
    suspend fun dlink(fsId: Long, path: String): String? {
        val hit = links[fsId]
        if (hit != null && System.currentTimeMillis() - hit.at < LINK_TTL_MS) return hit.url

        return withContext(Dispatchers.IO) {
            runCatching {
                val token = accessToken()
                val body = try {
                    Http.get(BaiduApi.mediaUrl(token, path, fsId))
                } catch (e: HttpStatusException) {
                    error("取播放地址被拒绝（${e.code}）：${e.body.take(160)}")
                }
                BaiduApi.mediaError(body)?.let { error(it) }
                val url = BaiduApi.parseDlink(body) ?: error(BaiduApi.errorMessage(BaiduApi.errnoOf(body)))
                links[fsId] = CachedLink(url, System.currentTimeMillis())
                url
            }.onFailure {
                // 以前这里静默吞掉异常，用户只看到「点歌没反应」，完全不知道是授权失效还是文件被删
                Log.w(TAG, "取直链失败 fsId=$fsId：${it.message}")
            }.getOrNull()
        }
    }

    suspend fun dlinkFor(streamUrl: String): String? {
        val ref = BaiduApi.parseStreamUrl(streamUrl) ?: return null
        return dlink(ref.fsId, ref.path)
    }

    private companion object {
        const val TAG = "BaiduSource"
        const val PAGE_SIZE = 1000
        const val MAX_DIRS = 200
        const val MAX_FILES = 3000
        const val EMPTY_RETRY_MS = 30 * 60_000L

        /** 授权失败后的冷静期，避免后台反复重试把百度的限流拖得更长。 */
        const val AUTH_COOLDOWN_MS = 5 * 60_000L

        /** 直链 8 小时有效，留一小时余量。 */
        const val LINK_TTL_MS = 7 * 60 * 60_000L
    }
}
