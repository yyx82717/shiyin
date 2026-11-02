package com.shiyin.player.data.model

/**
 * 扩展 M3U（`.m3u8`）读写。
 *
 * 为什么值得做：歌单是用户唯一自己攒下来的东西，而现在它只活在这台手机的
 * DataStore 里 —— 换机、刷机、卸载重装就没了。同类播放器（Poweramp、Retro、
 * Musicolet）都能导出 m3u，通用格式是「可带走」的最低保证。
 *
 * 只实现扩展 M3U 里真正有用的两条指令：`#EXTM3U` 和 `#EXTINF`。
 * 其余指令（`#EXTGRP`、`#EXTVLCOPT` 之类）读的时候一律忽略，写的时候不生成 ——
 * 少写一条就少一个别人读不懂的地方。
 */
data class M3uEntry(
    val uri: String,
    val title: String? = null,
    val artist: String? = null,
    val durationSec: Long = 0L,
) {
    /** 「艺人 - 歌名」，缺一半就只显示有的那一半。 */
    val display: String
        get() = when {
            !artist.isNullOrBlank() && !title.isNullOrBlank() -> "$artist - $title"
            !title.isNullOrBlank() -> title!!
            else -> uri.substringAfterLast('/')
        }
}

object M3u {

    private const val HEADER = "#EXTM3U"

    /** 写出一个能被 VLC / Poweramp / foobar 直接打开的歌单。 */
    fun write(playlistName: String, tracks: List<Track>, nowMs: Long = System.currentTimeMillis()): String {
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date(nowMs))
        return buildString {
            appendLine(HEADER)
            appendLine("#PLAYLIST:${playlistName.replace('\n', ' ').trim()}")
            appendLine("# 拾音 ShiYin 导出于 $date，共 ${tracks.size} 首")
            tracks.forEach { track ->
                appendLine("#EXTINF:${track.durationSec.coerceAtLeast(0)},${extInfo(track)}")
                appendLine(track.streamUrl)
            }
        }
    }

    private fun extInfo(track: Track): String = when {
        track.artist.isBlank() -> track.title.replace('\n', ' ')
        else -> "${track.artist.replace('\n', ' ')} - ${track.title.replace('\n', ' ')}"
    }

    /**
     * 解析歌单。
     *
     * 宽容优先：BOM、CRLF、空行、`#EXTINF` 后面不带路径、路径出现在 `#EXTINF` 之前，
     * 都能读出来。用户手上的 m3u 来自各种播放器，挑剔格式只会让人放弃导入。
     */
    fun parse(text: String): List<M3uEntry> {
        val entries = ArrayList<M3uEntry>()
        var pendingTitle: String? = null
        var pendingArtist: String? = null
        var pendingDuration = 0L

        text.removePrefix("\uFEFF").lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() -> Unit

                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val body = line.substring(8)
                    val comma = body.indexOf(',')
                    val head = if (comma >= 0) body.substring(0, comma) else body
                    val tail = if (comma >= 0) body.substring(comma + 1) else ""
                    // 流媒体的长度常写成 -1，统一收成 0：界面上「0 秒」比「负 1 秒」好解释
                    pendingDuration = (head.trim().substringBefore(' ').toDoubleOrNull()?.toLong() ?: 0L)
                        .coerceAtLeast(0L)
                    val info = tail.trim()
                    val dash = info.indexOf(" - ")
                    if (dash > 0) {
                        pendingArtist = info.substring(0, dash).trim().ifBlank { null }
                        pendingTitle = info.substring(dash + 3).trim().ifBlank { null }
                    } else {
                        pendingArtist = null
                        pendingTitle = info.ifBlank { null }
                    }
                }

                // 其他 # 开头的都是指令或注释，跳过
                line.startsWith("#") -> Unit

                else -> {
                    entries += M3uEntry(
                        uri = line,
                        title = pendingTitle,
                        artist = pendingArtist,
                        durationSec = pendingDuration,
                    )
                    pendingTitle = null
                    pendingArtist = null
                    pendingDuration = 0L
                }
            }
        }
        return entries
    }

    /** 取 `#PLAYLIST:` 里的名字，没有就返回 null，交调用方用文件名兜底。 */
    fun playlistName(text: String): String? = text.removePrefix("\uFEFF")
        .lineSequence()
        .firstOrNull { it.trim().startsWith("#PLAYLIST:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        ?.ifBlank { null }
}

/** 一条 m3u 记录和曲库对上之后的结果。 */
data class M3uImportResult(
    val matched: List<Track>,
    val missed: List<M3uEntry>,
) {
    val isEmpty: Boolean get() = matched.isEmpty() && missed.isEmpty()

    /** 给用户的一句话结论：命中几首、几首没找到。 */
    fun summary(): String = when {
        matched.isEmpty() -> "一条都没对上（${missed.size} 首不在曲库里）"
        missed.isEmpty() -> "全部对上，共 ${matched.size} 首"
        else -> "对上 ${matched.size} 首，${missed.size} 首不在曲库里"
    }
}

/**
 * 把 m3u 记录映射回曲库里的 Track。
 *
 * 按「地址 → 文件名 → 艺人-歌名 → 歌名」四级匹配，从强到弱。
 * 歌名这一级只在曲库里唯一时才认 —— 同名歌对错人的代价比漏一首大得多。
 */
object M3uImport {

    fun match(entries: List<M3uEntry>, library: List<Track>): M3uImportResult {
        if (entries.isEmpty() || library.isEmpty()) return M3uImportResult(emptyList(), entries)

        val byUrl = HashMap<String, Track>(library.size * 2)
        val byFile = HashMap<String, Track>(library.size * 2)
        val byArtistTitle = HashMap<String, Track>(library.size * 2)
        val byTitle = HashMap<String, MutableList<Track>>(library.size * 2)

        library.forEach { track ->
            normalize(track.streamUrl)?.let { byUrl.putIfAbsent(it, track) }
            fileNameOf(track.streamUrl)?.let { byFile.putIfAbsent(it, track) }
            normalizeArtistTitle(track.artist, track.title)?.let { byArtistTitle.putIfAbsent(it, track) }
            normalize(track.title)?.let { byTitle.getOrPut(it) { ArrayList(1) }.add(track) }
        }

        val matched = ArrayList<Track>(entries.size)
        val missed = ArrayList<M3uEntry>()
        val seen = HashSet<String>()

        entries.forEach { entry ->
            val hit = byUrl[normalize(entry.uri).orEmpty()]
                ?: fileNameOf(entry.uri)?.let { byFile[it] }
                ?: entry.artist?.let { artist ->
                    normalizeArtistTitle(artist, entry.title.orEmpty())?.let { byArtistTitle[it] }
                }
                ?: entry.title?.let { title ->
                    normalize(title)?.let { key -> byTitle[key]?.takeIf { it.size == 1 }?.first() }
                }
            // 同一个文件在 m3u 里出现两次（不同写法）只收一次
            if (hit != null && seen.add(hit.id)) matched += hit else if (hit == null) missed += entry
        }
        return M3uImportResult(matched, missed)
    }

    /** 只留字母数字与汉字，其余全部丢掉 —— 全角括号、空格、破折号差异都不再影响匹配。 */
    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val sb = StringBuilder(raw.length)
        raw.lowercase().forEach { ch ->
            if (ch in 'a'..'z' || ch in '0'..'9' || ch in '\u4e00'..'\u9fff') sb.append(ch)
        }
        return sb.toString().ifBlank { null }
    }

    private fun normalizeArtistTitle(artist: String, title: String): String? {
        val a = normalize(artist) ?: return null
        val t = normalize(title) ?: return null
        return "$a-$t"
    }

    /** `content://media/external/audio/media/123` 这类地址取最后一段，`a/b/歌.mp3` 取 `歌mp3`。 */
    private fun fileNameOf(uri: String): String? {
        val last = uri.substringBefore('?').trimEnd('/').substringAfterLast('/')
        if (last.isBlank()) return null
        return normalize(last.substringBeforeLast('.', last))
    }
}
