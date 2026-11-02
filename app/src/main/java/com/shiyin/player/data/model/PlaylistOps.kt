package com.shiyin.player.data.model

/**
 * 歌单的纯逻辑部分（去重、封顶、排序），抽出来单独放，方便用 JVM 单元测试覆盖。
 *
 * 「默认列表」是系统歌单：只要开始播放就会自动收进来，用户不用先建歌单。
 * 它用固定 id 而不是随机 id，重装/升级后仍能找回同一条记录。
 */
const val DEFAULT_PLAYLIST_ID = "default"
const val DEFAULT_PLAYLIST_NAME = "默认列表"

/** 默认列表最多留这么多首，超了就把最早的挤出去，避免无限膨胀。 */
const val DEFAULT_PLAYLIST_LIMIT = 300

/** 默认列表永远排在最前面，其余按创建时间倒序。 */
fun List<Playlist>.sortedForDisplay(): List<Playlist> =
    sortedWith(
        compareByDescending<Playlist> { it.id == DEFAULT_PLAYLIST_ID }
            .thenByDescending { it.createdAt },
    )

fun defaultPlaylist(): Playlist = Playlist(
    id = DEFAULT_PLAYLIST_ID,
    name = DEFAULT_PLAYLIST_NAME,
    tracks = emptyList(),
    createdAt = 0L,
)

/**
 * 追加一首歌。
 *
 * - 已经在列表里就把它提到最前面（最近听过的排前面），不产生重复项；
 * - 超过 [limit] 首时丢掉最早的。
 */
fun Playlist.appended(track: Track, limit: Int = DEFAULT_PLAYLIST_LIMIT): Playlist {
    val rest = tracks.filterNot { it.id == track.id }
    val merged = (listOf(track) + rest).take(limit.coerceAtLeast(1))
    return copy(tracks = merged)
}

/** 普通歌单是「收藏式」的追加：已有的不重复加，也不打乱顺序。 */
fun Playlist.appendIfAbsent(track: Track): Playlist =
    if (tracks.any { it.id == track.id }) this else copy(tracks = tracks + track)

fun List<Playlist>.upsert(playlist: Playlist): List<Playlist> {
    val exists = any { it.id == playlist.id }
    return if (exists) map { if (it.id == playlist.id) playlist else it } else this + playlist
}
