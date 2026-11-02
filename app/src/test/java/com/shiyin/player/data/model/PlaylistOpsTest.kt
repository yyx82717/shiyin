package com.shiyin.player.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistOpsTest {

    private fun track(id: String) = Track(
        id = id,
        title = "歌 $id",
        artist = "艺人",
        streamUrl = "https://example.com/$id",
        durationSec = 180,
    )

    @Test
    fun `默认列表排在最前面`() {
        val list = listOf(
            Playlist("a", "我的歌单", createdAt = 200),
            Playlist(DEFAULT_PLAYLIST_ID, DEFAULT_PLAYLIST_NAME, createdAt = 0),
            Playlist("b", "通勤", createdAt = 100),
        ).sortedForDisplay()

        assertEquals(listOf(DEFAULT_PLAYLIST_ID, "a", "b"), list.map { it.id })
    }

    @Test
    fun `追加新歌放在最前面`() {
        val result = defaultPlaylist().appended(track("1")).appended(track("2"))
        assertEquals(listOf("2", "1"), result.tracks.map { it.id })
    }

    @Test
    fun `重复播放不会产生重复项而是提到最前`() {
        val result = defaultPlaylist()
            .appended(track("1"))
            .appended(track("2"))
            .appended(track("1"))

        assertEquals(listOf("1", "2"), result.tracks.map { it.id })
        assertEquals(2, result.tracks.size)
    }

    @Test
    fun `超过上限时丢掉最早的`() {
        var playlist = defaultPlaylist()
        (1..5).forEach { playlist = playlist.appended(track(it.toString()), limit = 3) }
        // 新→旧：5,4,3
        assertEquals(listOf("5", "4", "3"), playlist.tracks.map { it.id })
    }

    @Test
    fun `普通歌单只追加不重排`() {
        val playlist = Playlist("p", "收藏", tracks = listOf(track("1"), track("2")))
        val same = playlist.appendIfAbsent(track("2"))
        assertEquals(2, same.tracks.size)
        assertEquals("1", same.tracks.first().id)

        val added = playlist.appendIfAbsent(track("3"))
        assertEquals(listOf("1", "2", "3"), added.tracks.map { it.id })
    }

    @Test
    fun `upsert 会更新已有歌单而不是复制一份`() {
        val origin = Playlist("p", "旧名字", tracks = emptyList())
        val renamed = origin.copy(name = "新名字")
        val updated = listOf(origin).upsert(renamed)

        assertEquals(1, updated.size)
        assertEquals("新名字", updated.first().name)
    }

    @Test
    fun `upsert 对不存在的歌单做插入`() {
        val updated = emptyList<Playlist>().upsert(defaultPlaylist())
        assertEquals(1, updated.size)
        assertTrue(updated.first().tracks.isEmpty())
    }
}
