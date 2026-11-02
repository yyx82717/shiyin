package com.shiyin.player.data.model

import kotlinx.serialization.Serializable

/**
 * 歌单。
 *
 * 直接存完整 Track 快照，而不是只存 id：
 * 音源可能临时不可用，但歌单里的歌名/歌手/封面还得显示得出来，
 * 而且这样歌单本身就能离线浏览。
 */
@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val tracks: List<Track> = emptyList(),
    val createdAt: Long = 0L,
)
