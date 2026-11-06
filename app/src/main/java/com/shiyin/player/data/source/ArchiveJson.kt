package com.shiyin.player.data.source

import com.shiyin.player.data.model.Track
import com.shiyin.player.data.remote.UrlCodec
import com.shiyin.player.data.remote.asArrOrNull
import com.shiyin.player.data.remote.asObjOrNull
import com.shiyin.player.data.remote.parseJsonOrNull
import com.shiyin.player.data.remote.str
import kotlinx.serialization.json.JsonPrimitive

/**
 * Internet Archive 高级搜索响应里抽出 identifier（最多 [cap] 个）。
 *
 * 文档结构：`response.docs[].identifier`。identifier 才是拿音频详情的钥匙，
 * 搜索接口本身不给文件名——所以下一步要对每个 identifier 单独拉 metadata。
 */
internal fun parseArchiveIdentifiers(body: String, cap: Int): List<String> {
    val root = parseJsonOrNull(body).asObjOrNull() ?: return emptyList()
    val docs = root["response"]?.asObjOrNull()?.get("docs")?.asArrOrNull() ?: return emptyList()
    return docs.mapNotNull { it.asObjOrNull()?.str("identifier") }
        .filter { it.isNotBlank() }
        .take(cap)
}

/**
 * 把一个 identifier 的 metadata 响应转成可播放 Track。
 *
 * - 选第一个音频文件（mp3 / ogg / vorbis）作为播放地址；
 * - 封面用 Archive 官方缩略图服务（`/services/img/{id}`）；
 * - 时长服务端不在列表里给，留 0，交给播放器自己探测。
 *
 * 没有音频文件、或解析失败的 identifier 返回 null，由上层直接丢弃——
 * 这保证「搜不到就算了」，不会把播不了的条目塞给用户。
 */
internal fun parseArchiveTrack(metaBody: String, identifier: String): Track? {
    val meta = parseJsonOrNull(metaBody).asObjOrNull() ?: return null
    val files = meta["files"]?.asArrOrNull() ?: return null
    val audioName = files.firstOrNull { f ->
        val fmt = f.asObjOrNull()?.str("format").orEmpty().lowercase()
        fmt.contains("mp3") || fmt.contains("vorbis") || fmt.contains("ogg")
    }?.asObjOrNull()?.str("name") ?: return null

    val md = meta["metadata"]?.asObjOrNull()
    val title = md?.str("title")?.trim()?.ifBlank { null } ?: identifier
    val creator = (md?.str("creator")
        ?: md?.get("creator")?.asArrOrNull()?.firstOrNull()?.let { (it as? JsonPrimitive)?.content })
        ?.trim()?.ifBlank { null } ?: "Internet Archive"

    return Track(
        id = "archive:$identifier",
        title = title,
        artist = creator,
        album = null,
        artwork = "https://archive.org/services/img/$identifier",
        streamUrl = "https://archive.org/download/$identifier/${UrlCodec.encode(audioName)}",
        durationSec = 0L,
        source = "archive",
    )
}
