package com.shiyin.player.data.source

import com.shiyin.player.data.model.Track
import com.shiyin.player.data.remote.Http
import com.shiyin.player.data.remote.UrlCodec
import com.shiyin.player.data.remote.asArrOrNull
import com.shiyin.player.data.remote.asObjOrNull
import com.shiyin.player.data.remote.parseJsonOrNull
import com.shiyin.player.data.remote.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Jamendo 的 `fuzzytags` 用 `+` 表示「或」。
 *
 * 之前写成 `encode(genre.replace(" ", "+"))`，`+` 又被转义成 `%2B`，
 * 服务端收到的是 `hip%2Bhop` 这一个不存在的标签，曲风筛选等于失效。
 * 正确做法是每个词单独转义，再用未转义的 `+` 连接。
 */
internal fun jamendoFuzzyTags(genre: String): String =
    genre.lowercase()
        .split(' ', '+', ',')
        .filter { it.isNotBlank() }
        .joinToString("+") { UrlCodec.encode(it) }

/**
 * Jamendo 免费音乐库（CC 授权）。
 *
 * 需要免费申请一个 client_id：https://devportal.jamendo.com
 * 在设置页填入后本音源自动启用；没填就自动跳过，不影响 Audius。
 */
class JamendoSource(private val clientIdProvider: suspend () -> String) : MusicSource {

    override val id = "jamendo"
    override val label = "Jamendo"
    override val note = "CC 授权曲库，需在设置页填入免费 client_id"

    private val base = "https://api.jamendo.com/v3.0/tracks/"

    override suspend fun feed(genre: String?, limit: Int): List<Track> {
        val cid = clientIdProvider()
        if (cid.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            val url = buildString {
                append(base)
                append("?client_id=").append(cid)
                append("&format=json&limit=").append(limit)
                append("&order=popularity_week&audioformat=mp32&imagesize=500")
                if (!genre.isNullOrBlank()) append("&fuzzytags=").append(jamendoFuzzyTags(genre))
            }
            parse(Http.get(url))
        }
    }

    override suspend fun search(query: String, limit: Int): List<Track> {
        val cid = clientIdProvider()
        if (cid.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            val url = "$base?client_id=$cid&format=json&limit=$limit&search=${UrlCodec.encode(query)}&audioformat=mp32&imagesize=500"
            parse(Http.get(url))
        }
    }

    private fun parse(body: String): List<Track> {
        val root = parseJsonOrNull(body).asObjOrNull() ?: return emptyList()
        val results = root["results"].asArrOrNull() ?: return emptyList()
        return results.mapNotNull { element ->
            val o = element.asObjOrNull() ?: return@mapNotNull null
            val trackId = o.str("id") ?: return@mapNotNull null
            val audio = o.str("audio") ?: return@mapNotNull null
            Track(
                id = "jamendo:$trackId",
                title = o.str("name")?.trim()?.ifBlank { "未命名曲目" } ?: "未命名曲目",
                artist = o.str("artist_name") ?: "Jamendo 音乐人",
                album = o.str("album_name"),
                artwork = o.str("image") ?: o.str("album_image"),
                streamUrl = audio,
                durationSec = o.str("duration")?.toLongOrNull() ?: 0L,
                source = id,
            )
        }
    }
}
