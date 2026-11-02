package com.shiyin.player.data.model

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 全局统一的 JSON 配置：容忍未知字段，便于兼容各家 API 的字段增减。 */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    isLenient = true
}

private const val META_TRACK = "shiyin.track"

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artwork: String? = null,
    val streamUrl: String,
    val durationSec: Long = 0L,
    val source: String = "audius",
    /**
     * 所在目录（`Music/周杰伦/范特西` 这种，不带开头的 `/`）。
     *
     * 老快照里没有这个字段，解出来是 null，文件夹视图会把它们归到根目录，
     * 不会因为升级而丢歌。
     */
    val folder: String? = null,
    /** 容器/编码：`flac` / `wav` / `mp3` / `m4a`…，用来标「无损 / 高品」。 */
    val format: String? = null,
    /** 码率（kbps）。只有本地媒体库给得出，网盘没有这个信息，是 0。 */
    val bitrateKbps: Int = 0,
) {
    val durationMs: Long get() = durationSec * 1000L
}

/** 把业务模型塞进 MediaItem 的 metadata，跨进程/通知栏都能还原出完整 Track。 */
fun Track.toMediaItem(): MediaItem {
    val payload = AppJson.encodeToString(Track.serializer(), this)
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setArtworkUri(artwork?.let { Uri.parse(it) })
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .setExtras(Bundle().apply { putString(META_TRACK, payload) })
        .build()
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(streamUrl)
        .setMediaMetadata(metadata)
        .build()
}

fun MediaItem.toTrack(): Track? {
    val raw = mediaMetadata.extras?.getString(META_TRACK) ?: return null
    return runCatching { AppJson.decodeFromString(Track.serializer(), raw) }.getOrNull()
}
