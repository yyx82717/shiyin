package com.shiyin.player.data.model

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaySessionTest {

    private fun track(id: String) = Track(
        id = id,
        title = id,
        artist = "某歌手",
        streamUrl = "http://host/$id.mp3",
        source = "webdav",
    )

    private val now = 1_700_000_000_000L

    private fun session(
        tracks: List<Track> = listOf(track("a"), track("b")),
        index: Int = 0,
        savedAt: Long = now,
    ) = PlaySession(tracks = tracks, index = index, positionMs = 42_000, savedAt = savedAt)

    @Test
    fun `正常现场可以还原`() {
        assertTrue(session().restorable(now))
    }

    @Test
    fun `空队列不收`() {
        assertFalse(session(tracks = emptyList(), index = 0).restorable(now))
    }

    @Test
    fun `索引越界不收`() {
        assertFalse(session(index = 5).restorable(now))
        assertFalse(session(index = -1).restorable(now))
    }

    @Test
    fun `太旧的现场不收`() {
        val old = now - PlaySession.MAX_AGE_MS - 1
        assertFalse(session(savedAt = old).restorable(now))
    }

    @Test
    fun `时间戳缺失或来自未来都不收`() {
        assertFalse(session(savedAt = 0L).restorable(now))
        assertFalse(session(savedAt = now + 60_000).restorable(now))
    }

    @Test
    fun `序列化能过一轮不丢字段`() {
        val original = session(index = 1).copy(shuffle = true, repeatMode = 2)
        val json = AppJson.encodeToString(original)
        val back = AppJson.decodeFromString(PlaySession.serializer(), json)
        assertEquals(original, back)
    }

    @Test
    fun `老版本快照缺字段也能读出来`() {
        val raw = """{"tracks":[],"index":0,"positionMs":0,"shuffle":false,"repeatMode":0,"savedAt":0}"""
        val back = AppJson.decodeFromString(PlaySession.serializer(), raw)
        assertEquals(0L, back.positionMs)
        assertTrue(back.tracks.isEmpty())
    }
}
