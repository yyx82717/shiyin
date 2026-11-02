package com.shiyin.player.data.source

import com.shiyin.player.data.model.Track
import com.shiyin.player.data.remote.asArrayOrSingle
import com.shiyin.player.data.remote.asObjOrNull
import com.shiyin.player.data.remote.parseJsonOrNull
import com.shiyin.player.data.remote.int
import com.shiyin.player.data.remote.str

/**
 * Subsonic 响应解析。
 *
 * 抽成纯函数便于单元测试。三个真实踩点：
 * 1. 失败时是 `subsonic-response.status == "failed"` + `error.message`，要抛出去让界面显示原因，
 *    而不是当成「0 首结果」——否则用户会以为曲库是空的；
 * 2. 容器名不同接口不一样：`getRandomSongs` 是 `randomSongs`，`search3` 是 `searchResult3`；
 * 3. 只有一首歌时部分服务端返回对象而不是数组，用 `asArrayOrSingle()` 兜住。
 */
internal fun parseSubsonicTracks(
    body: String,
    containerName: String,
    sourceId: String,
    buildUrl: (String, Map<String, String>) -> String,
): List<Track> {
    val root = parseJsonOrNull(body).asObjOrNull() ?: return emptyList()
    val response = root["subsonic-response"].asObjOrNull() ?: return emptyList()

    if (response.str("status") == "failed") {
        val message = response["error"].asObjOrNull()?.str("message") ?: "服务器返回失败"
        throw IllegalStateException(message)
    }

    val songs = response[containerName].asObjOrNull()?.get("song").asArrayOrSingle()
        ?: return emptyList()

    return songs.mapNotNull { element ->
        val song = element.asObjOrNull() ?: return@mapNotNull null
        val songId = song.str("id") ?: return@mapNotNull null

        Track(
            id = "$sourceId:$songId",
            title = song.str("title")?.trim().orEmpty().ifBlank { "未命名曲目" },
            artist = song.str("artist") ?: song.str("albumArtist") ?: "未知艺人",
            album = song.str("album"),
            artwork = song.str("coverArt")?.let { coverId ->
                buildUrl("getCoverArt", mapOf("id" to coverId, "size" to "512"))
            },
            streamUrl = buildUrl("stream", mapOf("id" to songId)),
            durationSec = song.int("duration")?.toLong() ?: 0L,
            source = sourceId,
        )
    }
}
