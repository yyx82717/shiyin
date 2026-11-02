package com.shiyin.player.data.source

import com.shiyin.player.data.model.Track
import com.shiyin.player.data.remote.Http
import com.shiyin.player.data.remote.UrlCodec
import com.shiyin.player.data.remote.asObjOrNull
import com.shiyin.player.data.remote.md5Hex
import com.shiyin.player.data.remote.parseJsonOrNull
import com.shiyin.player.data.remote.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom

/**
 * Subsonic 兼容曲库：Navidrome、Airsonic、Gonic、Ampache、Jellyfin(插件) 等。
 *
 * 这是「全库」的第二层含义——把你自己收藏的音乐放到一台服务器/树莓派/群晖上，
 * 手机端就能拿到完整曲库、搜索、流播，容量和格式都不受限制。
 *
 * 认证用官方推荐的 token 方案：t = md5(password + salt)，密码不随请求发出。
 * 接口：/rest/ping.view、/rest/getRandomSongs、/rest/search3、/rest/stream、/rest/getCoverArt
 */
class SubsonicSource(
    private val provider: suspend () -> SubsonicConfig?,
) : MusicSource {

    override val id = "subsonic"
    override val label = "自建曲库"
    override val note = "Subsonic / Navidrome / Airsonic 等自建音乐服务器"

    override suspend fun feed(genre: String?, limit: Int): List<Track> = withContext(Dispatchers.IO) {
        val cfg = provider()?.takeIf { it.usable } ?: return@withContext emptyList()
        val params = mutableMapOf("size" to limit.coerceIn(1, 500).toString())
        if (!genre.isNullOrBlank()) params["genre"] = genre
        val body = Http.get(url(cfg, "getRandomSongs", params))
        parse(cfg, body, "randomSongs")
    }

    override suspend fun search(query: String, limit: Int): List<Track> = withContext(Dispatchers.IO) {
        val cfg = provider()?.takeIf { it.usable } ?: return@withContext emptyList()
        if (query.isBlank()) return@withContext emptyList()
        val body = Http.get(
            url(
                cfg,
                "search3",
                mapOf(
                    "query" to query,
                    "songCount" to limit.coerceIn(1, 500).toString(),
                    "artistCount" to "0",
                    "albumCount" to "0",
                ),
            ),
        )
        parse(cfg, body, "searchResult3")
    }

    /** 设置页的「测试连接」。成功时返回服务器版本，方便用户确认真的连上了。 */
    suspend fun test(cfg: SubsonicConfig): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val root = parseJsonOrNull(Http.get(url(cfg, "ping", emptyMap())))
                .asObjOrNull()
                ?.get("subsonic-response")
                .asObjOrNull()
                ?: error("服务器没有返回 Subsonic 格式，检查地址是否正确")

            if (root.str("status") == "failed") {
                error(root["error"].asObjOrNull()?.str("message") ?: "认证失败")
            }
            val version = root.str("version") ?: "未知"
            "连接成功，服务端版本 $version"
        }
    }

    private fun parse(cfg: SubsonicConfig, body: String, containerName: String): List<Track> =
        parseSubsonicTracks(body, containerName, id) { method, params -> url(cfg, method, params) }

    private fun url(cfg: SubsonicConfig, method: String, params: Map<String, String>): String {
        val salt = randomSalt()
        val token = md5Hex(cfg.password + salt)
        val sb = StringBuilder(cfg.endpoint)
            .append("/rest/").append(method)
            .append("?u=").append(UrlCodec.encode(cfg.user))
            .append("&t=").append(token)
            .append("&s=").append(salt)
            .append("&v=1.16.1&c=shiyin&f=json")
        params.forEach { (key, value) ->
            if (value.isNotEmpty()) sb.append('&').append(key).append('=').append(UrlCodec.encode(value))
        }
        return sb.toString()
    }

    private fun randomSalt(): String {
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { String.format("%02x", it) }
    }
}
