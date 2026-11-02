package com.shiyin.player.data.lyrics

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.shiyin.player.data.model.AppJson
import com.shiyin.player.data.model.Track
import com.shiyin.player.data.remote.Http
import com.shiyin.player.data.remote.RemoteAuth
import com.shiyin.player.data.remote.UrlCodec
import com.shiyin.player.data.remote.asArrOrNull
import com.shiyin.player.data.remote.asObjOrNull
import com.shiyin.player.data.remote.bool
import com.shiyin.player.data.remote.int
import com.shiyin.player.data.remote.md5Hex
import com.shiyin.player.data.remote.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.File
import kotlin.math.abs

/**
 * 歌词仓库。按优先级取：本地 .lrc → 磁盘缓存 → LRCLIB 在线。
 *
 * LRCLIB（lrclib.net）是开放歌词库，免密钥、返回带时间轴的 LRC，
 * 接口：/api/get（精确匹配）、/api/search（模糊兜底）。
 * 实测 /api/get 返回 HTTP 200 + syncedLyrics 字段可用。
 */
class LyricsRepository(private val context: Context) {

    private val memory = HashMap<String, Lyrics?>()
    private val lock = Any()

    suspend fun load(track: Track, force: Boolean = false): Lyrics? = withContext(Dispatchers.IO) {
        if (!force) {
            synchronized(lock) { if (memory.containsKey(track.id)) return@withContext memory[track.id] }
        }

        val result = readDisk(track)
            ?: localSidecar(track)
            ?: remoteSidecar(track)
            ?: fetchRemote(track)

        // 找不到也记下来，避免每次打开播放页都重新发请求
        synchronized(lock) { memory[track.id] = result }
        if (result != null) writeDisk(track, result)
        result
    }

    // ---------------- 手动搜歌词 ----------------

    /**
     * 在线搜歌词，返回候选列表而不是直接替换。
     *
     * 自动匹配只靠元数据猜，同名现场版、翻唱、伴奏都会撞车；
     * 让用户看一眼候选（歌手、时长、有没有时间轴）再决定，命中率会高很多。
     */
    suspend fun search(query: String, hint: Track? = null): List<LyricsCandidate> = withContext(Dispatchers.IO) {
        val typed = query.trim()
        val keyword = when {
            typed.isNotBlank() -> typed
            hint != null -> listOf(hint.title, hint.artist).filter { it.isNotBlank() }.joinToString(" ")
            else -> return@withContext emptyList()
        }

        // 1) LRCLIB：开放歌词库，能一次拿到多条候选
        val remote = runCatching {
            val url = "https://lrclib.net/api/search?q=" + UrlCodec.encode(keyword) + "&limit=20"
            parseLrclibSearch(Http.getQuick(url))
        }.getOrDefault(emptyList())
        if (remote.isNotEmpty()) return@withContext remote

        // 2) lyrics.ovh：只有纯文本，但接口稳定，按「艺人/歌名」查
        val pair = splitQuery(keyword) ?: hint?.let { it.artist to it.title }
        if (pair != null) {
            val (artist, title) = pair
            if (artist.isNotBlank() && title.isNotBlank()) {
                val url = "https://api.lyrics.ovh/v1/" + UrlCodec.encode(artist) + "/" + UrlCodec.encode(title)
                val one = runCatching { parseLyricsOvh(Http.getQuick(url), title, artist) }.getOrNull()
                if (one != null) return@withContext listOf(one)
            }
        }
        emptyList()
    }

    /** 把用户选中的候选写进缓存，之后打开这首歌都用它。 */
    suspend fun apply(track: Track, candidate: LyricsCandidate): Lyrics? = withContext(Dispatchers.IO) {
        val lyrics = candidate.toLyrics() ?: return@withContext null
        synchronized(lock) { memory[track.id] = lyrics }
        writeDisk(track, lyrics)
        lyrics
    }

    /** 用户自己粘贴的 LRC / 纯文本歌词。 */
    suspend fun applyManual(track: Track, raw: String): Lyrics? = withContext(Dispatchers.IO) {
        val lyrics = parseManualLyrics(raw) ?: return@withContext null
        synchronized(lock) { memory[track.id] = lyrics }
        writeDisk(track, lyrics)
        lyrics
    }

    /** 允许用「歌手 - 歌名」或「歌名 歌手」的写法手动指定。 */
    private fun splitQuery(query: String): Pair<String, String>? {
        val separators = listOf(" - ", " – ", "—", "／", "/")
        for (sep in separators) {
            val index = query.indexOf(sep)
            if (index > 0) {
                val left = query.substring(0, index).trim()
                val right = query.substring(index + sep.length).trim()
                if (left.isNotBlank() && right.isNotBlank()) return left to right
            }
        }
        return null
    }

    // ---------------- 本地 .lrc ----------------

    private fun localSidecar(track: Track): Lyrics? {
        val audio = localAudioFile(track) ?: return null
        val dir = audio.parentFile ?: return null
        val base = audio.nameWithoutExtension

        // .ttml 排在前面：同名的两份歌词里，带逐字时间的那个更值得用
        val candidates = listOf(
            "$base.ttml", "$base.TTML", "$base.Ttml",
            "$base.lrc", "$base.LRC", "$base.Lrc", "${audio.name}.lrc",
        ).map { File(dir, it) }
        val lrc = candidates.firstOrNull { it.isFile && it.length() > 0L } ?: return null

        val raw = readTextSmart(lrc)
        val label = "本地 ." + lrc.extension.lowercase()
        val synced = LyricsText.parse(raw)
        if (synced.isNotEmpty()) return Lyrics(synced, true, label)

        val plain = LrcParser.plain(raw)
        return if (plain.isNotEmpty()) Lyrics(plain, false, label) else null
    }

    // ---------------- 网盘旁挂 .lrc ----------------

    /**
     * 网盘音源的同目录 `.lrc`。
     *
     * 只对 `webdav` 做：它的 `streamUrl` 就是真实文件地址，机主把自己下载的歌连同歌词
     * 一起放上去是常见做法（酷我、QQ 音乐下载就会生成同名 .lrc）。
     * Audius / Jamendo 给的是 CDN 直链，旁边不会有人放歌词，发这个请求纯属浪费。
     */
    private fun remoteSidecar(track: Track): Lyrics? {
        if (track.source != "webdav") return null
        val urls = Sidecar.sidecarUrls(track.streamUrl)
        if (urls.isEmpty()) return null

        val host = runCatching { Uri.parse(track.streamUrl).host }.getOrNull()
        val headers = buildMap {
            RemoteAuth.headerFor(host)?.let { put("Authorization", it) }
        }

        for (url in urls) {
            val raw = runCatching { Http.getText(url, headers) }.getOrNull()
            if (raw.isNullOrBlank()) continue

            // 常见坑：服务器对不存在的文件不返回 404，而是回一个 HTML 错误页。
            // 先挡掉，否则会被拆成一堆标签文本当成纯文本歌词。
            if (raw.contains("<html", ignoreCase = true) ||
                raw.contains("<!DOCTYPE", ignoreCase = true)
            ) {
                continue
            }

            val label = "网盘 ." + url.substringAfterLast('.', "lrc")
            val synced = LyricsText.parse(raw)
            if (synced.isNotEmpty()) return Lyrics(synced, true, label)

            val plain = LrcParser.plain(raw)
            if (plain.isNotEmpty()) return Lyrics(plain, false, label)
        }
        return null
    }

    /** 只有本地库的曲目才有真实文件路径；其它源没有同名 lrc 的概念。 */
    private fun localAudioFile(track: Track): File? {        if (!track.id.startsWith("local:")) return null
        val songId = track.id.removePrefix("local:").toLongOrNull() ?: return null
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)

        @Suppress("DEPRECATION")
        val path = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Audio.Media.DATA),
                null,
                null,
                null,
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()

        return path?.let { File(it) }?.takeIf { it.isFile }
    }

    /** .lrc 在国内大量是 GBK 编码，UTF-8 读出来会出现替换字符，这时回退 GBK。 */
    private fun readTextSmart(file: File): String {
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return ""
        val utf8 = String(bytes, Charsets.UTF_8)
        if (!utf8.contains('\uFFFD')) return utf8
        return runCatching { String(bytes, charset("GBK")) }.getOrDefault(utf8)
    }

    // ---------------- LRCLIB ----------------

    private fun fetchRemote(track: Track): Lyrics? {
        exactMatch(track)?.let { return it }
        searchMatch(track)?.let { return it }
        // LRCLIB 偶尔连不上（SNI 干扰），再兜一层纯文本歌词
        return lyricsOvh(track)
    }

    /**
     * lyrics.ovh 只有纯文本、没有时间轴，作为最后兜底。
     * 接口：/v1/{artist}/{title} → {"lyrics": "..."}
     */
    private fun lyricsOvh(track: Track): Lyrics? {
        val url = "https://api.lyrics.ovh/v1/" +
            UrlCodec.encode(track.artist) + "/" + UrlCodec.encode(track.title)
        val obj = runCatching {
            AppJson.parseToJsonElement(Http.getQuick(url)).asObjOrNull()
        }.getOrNull() ?: return null

        val text = obj.str("lyrics") ?: return null
        val lines = LrcParser.plain(text.replace("\r", ""))
        return if (lines.isEmpty()) null else Lyrics(lines, false, "lyrics.ovh")
    }

    private fun exactMatch(track: Track): Lyrics? {
        val url = buildString {
            append("https://lrclib.net/api/get?artist_name=").append(UrlCodec.encode(track.artist))
            append("&track_name=").append(UrlCodec.encode(track.title))
            track.album?.takeIf { it.isNotBlank() }?.let {
                append("&album_name=").append(UrlCodec.encode(it))
            }
            if (track.durationSec > 0) append("&duration=").append(track.durationSec)
        }
        val obj = runCatching {
            AppJson.parseToJsonElement(Http.getQuick(url)).asObjOrNull()
        }.getOrNull() ?: return null
        return obj.toLyrics()
    }

    private fun searchMatch(track: Track): Lyrics? {
        val query = listOf(track.title, track.artist)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (query.isBlank()) return null

        val url = "https://lrclib.net/api/search?q=" + UrlCodec.encode(query) + "&limit=5"
        val arr = runCatching {
            AppJson.parseToJsonElement(Http.getQuick(url)).asArrOrNull()
        }.getOrNull() ?: return null

        val candidates = arr.mapNotNull { element ->
            element.asObjOrNull()?.takeIf {
                !it.str("syncedLyrics").isNullOrBlank() || !it.str("plainLyrics").isNullOrBlank()
            }
        }
        if (candidates.isEmpty()) return null

        // 优先选时长最接近的，时长未知的排后面而不是直接丢掉
        val best = candidates.minByOrNull { candidate ->
            val duration = candidate.int("duration") ?: 0
            if (track.durationSec > 0 && duration > 0) abs(duration - track.durationSec.toInt()) else 600
        }
        return best?.toLyrics()
    }

    private fun JsonObject.toLyrics(): Lyrics? {
        if (bool("instrumental") == true) {
            return Lyrics(emptyList(), false, "LRCLIB", instrumental = true)
        }

        val syncedRaw = str("syncedLyrics")
        if (!syncedRaw.isNullOrBlank()) {
            val lines = LyricsText.parse(syncedRaw)
            if (lines.isNotEmpty()) return Lyrics(lines, true, "LRCLIB")
        }

        val plainRaw = str("plainLyrics")
        if (!plainRaw.isNullOrBlank()) {
            val lines = LrcParser.plain(plainRaw)
            if (lines.isNotEmpty()) return Lyrics(lines, false, "LRCLIB")
        }
        return null
    }

    // ---------------- 磁盘缓存 ----------------

    private fun cacheDir(): File = File(context.filesDir, "lyrics").apply { mkdirs() }

    private fun diskFile(track: Track): File = File(cacheDir(), md5Hex(track.id) + ".json")

    private fun readDisk(track: Track): Lyrics? {
        val file = diskFile(track)
        if (!file.isFile) return null
        return runCatching {
            AppJson.decodeFromString(Lyrics.serializer(), file.readText())
        }.getOrNull()?.takeIf { !it.isEmpty || it.instrumental }
    }

    private fun writeDisk(track: Track, lyrics: Lyrics) {
        runCatching {
            diskFile(track).writeText(AppJson.encodeToString(Lyrics.serializer(), lyrics))
        }
    }

    fun clearCache() {
        synchronized(lock) { memory.clear() }
        runCatching { cacheDir().deleteRecursively() }
    }
}
