package com.shiyin.player.data.lyrics

import com.shiyin.player.data.remote.asArrOrNull
import com.shiyin.player.data.remote.asObjOrNull
import com.shiyin.player.data.remote.bool
import com.shiyin.player.data.remote.int
import com.shiyin.player.data.remote.parseJsonOrNull
import com.shiyin.player.data.remote.str

/**
 * 一条可选的歌词搜索结果。
 *
 * 自动匹配只拿「歌名 + 艺人 + 时长」去猜，猜错是常事：
 * 同名的现场版、翻唱、伴奏都可能被命中。所以这里保留完整候选，
 * 让人自己挑一条——所见即所得，比后台猜准确。
 */
data class LyricsCandidate(
    val key: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationSec: Long,
    /** 是否带时间轴，界面上会标出来。 */
    val synced: Boolean,
    val source: String,
    val syncedRaw: String? = null,
    val plainRaw: String? = null,
) {
    val hasContent: Boolean get() = !syncedRaw.isNullOrBlank() || !plainRaw.isNullOrBlank()
}

/**
 * LRCLIB `/api/search` 的响应解析。
 *
 * 该接口返回的是数组，字段与 `/api/get` 一致：
 * id / trackName / artistName / albumName / duration / instrumental / plainLyrics / syncedLyrics。
 * 纯器乐的条目（instrumental=true）也保留，选中后会显示「纯音乐」。
 */
internal fun parseLrclibSearch(body: String, limit: Int = 20): List<LyricsCandidate> {
    val arr = parseJsonOrNull(body).asArrOrNull() ?: return emptyList()
    return arr.asSequence()
        .mapNotNull { element -> element.asObjOrNull() }
        .mapNotNull { o ->
            val title = o.str("trackName")?.trim().orEmpty()
            val artist = o.str("artistName")?.trim().orEmpty()
            if (title.isBlank() && artist.isBlank()) return@mapNotNull null

            val synced = o.str("syncedLyrics")
            val plain = o.str("plainLyrics")
            val instrumental = o.bool("instrumental") == true
            if (synced.isNullOrBlank() && plain.isNullOrBlank() && !instrumental) return@mapNotNull null

            LyricsCandidate(
                key = "lrclib:" + (o.str("id") ?: "$artist-$title"),
                title = title.ifBlank { "未命名" },
                artist = artist.ifBlank { "未知艺人" },
                album = o.str("albumName")?.trim()?.takeIf { it.isNotBlank() },
                durationSec = o.int("duration")?.toLong() ?: 0L,
                synced = !synced.isNullOrBlank(),
                source = "LRCLIB",
                syncedRaw = synced,
                plainRaw = plain,
            )
        }
        .distinctBy { it.title.lowercase() to it.artist.lowercase() to it.durationSec }
        .take(limit)
        .toList()
}

/** lyrics.ovh 只有纯文本，接口返回 {"lyrics": "..."}。 */
internal fun parseLyricsOvh(body: String, queryTitle: String, queryArtist: String): LyricsCandidate? {
    val obj = parseJsonOrNull(body).asObjOrNull() ?: return null
    val text = obj.str("lyrics")?.replace("\r", "")?.trim().orEmpty()
    if (text.isBlank()) return null
    return LyricsCandidate(
        key = "ovh:$queryArtist-$queryTitle",
        title = queryTitle,
        artist = queryArtist,
        album = null,
        durationSec = 0L,
        synced = false,
        source = "lyrics.ovh",
        plainRaw = text,
    )
}

/** 把候选变成可以直接展示的歌词；解析不出内容就返回 null。 */
internal fun LyricsCandidate.toLyrics(): Lyrics? {
    if (!syncedRaw.isNullOrBlank()) {
        val lines = LrcParser.parse(syncedRaw)
        if (lines.isNotEmpty()) return Lyrics(lines, true, source)
    }
    if (!plainRaw.isNullOrBlank()) {
        val lines = LrcParser.plain(plainRaw)
        if (lines.isNotEmpty()) return Lyrics(lines, false, source)
    }
    return null
}

/** 用户手动粘贴的文本：TTML 或 LRC 都认，没有时间轴就按纯文本展示。 */
internal fun parseManualLyrics(raw: String, source: String = "手动粘贴"): Lyrics? {
    val text = raw.trim()
    if (text.isBlank()) return null
    val synced = LyricsText.parse(text)
    if (synced.isNotEmpty()) return Lyrics(synced, true, source)
    val plain = LrcParser.plain(text)
    return if (plain.isEmpty()) null else Lyrics(plain, false, source)
}
