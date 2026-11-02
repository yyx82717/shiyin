package com.shiyin.player.data.source

import com.shiyin.player.data.model.Track
import com.shiyin.player.data.remote.Http
import com.shiyin.player.data.remote.UrlCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Audius 开放曲库。
 *
 * 选它的原因：无需 API Key、国内可直连、艺人在平台上自主上传并选择免费流播，
 * 所以「自动拉取 + 播放」在授权上是干净的。
 *
 * 接口（v1）：
 *   GET https://api.audius.co/v1/tracks/trending?app_name=&limit=&time=&genre=
 *   GET https://api.audius.co/v1/tracks/search?app_name=&query=&limit=
 *   GET https://api.audius.co/v1/tracks/{id}/stream?app_name=   → 302 到音频文件
 */
class AudiusSource : MusicSource {

    override val id = "audius"
    override val label = "Audius"
    override val note = "独立音乐人自主上传的开放曲库，无需密钥，国内可直连"

    private val base = "https://api.audius.co"
    private val appName = "shiyin"

    override suspend fun feed(genre: String?, limit: Int): List<Track> = withContext(Dispatchers.IO) {
        val url = buildString {
            append(base).append("/v1/tracks/trending?app_name=").append(appName)
            append("&limit=").append(limit)
            append("&time=week")
            if (!genre.isNullOrBlank()) append("&genre=").append(UrlCodec.encode(genre))
        }
        parse(Http.get(url))
    }

    override suspend fun search(query: String, limit: Int): List<Track> = withContext(Dispatchers.IO) {
        val url = "$base/v1/tracks/search?app_name=$appName&query=${UrlCodec.encode(query)}&limit=$limit"
        parse(Http.get(url))
    }

    private fun parse(body: String): List<Track> = parseAudiusTracks(body, base, appName)
}
