package com.shiyin.player.data.source

import com.shiyin.player.data.model.Track
import com.shiyin.player.data.remote.asArrOrNull
import com.shiyin.player.data.remote.asObjOrNull
import com.shiyin.player.data.remote.parseJsonOrNull
import com.shiyin.player.data.remote.bool
import com.shiyin.player.data.remote.str

/**
 * Audius 响应解析。
 *
 * 抽成纯函数是为了能被 JVM 单元测试覆盖——这里有两个容易踩空的点：
 * 1. `is_streamable == false` 的曲目必须丢掉，否则点了播放会直接报错；
 * 2. 封面字段是一张尺寸映射表（1000x1000 / 480x480 / 150x150），要按大→小兜底。
 */
internal fun parseAudiusTracks(body: String, base: String, appName: String): List<Track> {
    val root = parseJsonOrNull(body).asObjOrNull() ?: return emptyList()
    val data = root["data"].asArrOrNull() ?: return emptyList()

    return data.mapNotNull { element ->
        val o = element.asObjOrNull() ?: return@mapNotNull null
        if (o.bool("is_streamable") == false) return@mapNotNull null

        val trackId = o.str("id") ?: return@mapNotNull null
        val user = o["user"].asObjOrNull()
        val artwork = o["artwork"].asObjOrNull()
        val title = o.str("title")?.trim().orEmpty()

        Track(
            id = "audius:$trackId",
            title = title.ifBlank { "未命名曲目" },
            artist = user?.str("name") ?: user?.str("handle") ?: "Audius 音乐人",
            album = user?.str("name"),
            artwork = artwork?.str("1000x1000")
                ?: artwork?.str("480x480")
                ?: artwork?.str("150x150"),
            streamUrl = "$base/v1/tracks/$trackId/stream?app_name=$appName",
            durationSec = o.str("duration")?.toLongOrNull() ?: 0L,
            source = "audius",
        )
    }
}
