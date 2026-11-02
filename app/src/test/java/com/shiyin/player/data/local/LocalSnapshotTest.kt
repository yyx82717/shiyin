package com.shiyin.player.data.local

import com.shiyin.player.data.model.AppJson
import com.shiyin.player.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSnapshotTest {

    private fun track(id: String) = Track(
        id = "local:$id",
        title = "华语歌 $id",
        artist = "歌手",
        album = "专辑",
        artwork = "file:///cache/album_1.jpg",
        streamUrl = "content://media/external/audio/media/$id",
        durationSec = 240,
        source = "local",
    )

    @Test
    fun `快照可以原样读回`() {
        val snapshot = LocalSnapshot(savedAt = 1_700_000_000_000, tracks = listOf(track("1"), track("2")))
        val raw = AppJson.encodeToString(LocalSnapshot.serializer(), snapshot)
        val back = AppJson.decodeFromString(LocalSnapshot.serializer(), raw)

        assertEquals(snapshot.savedAt, back.savedAt)
        assertEquals(2, back.tracks.size)
        assertEquals("local:1", back.tracks.first().id)
        assertEquals("华语歌 1", back.tracks.first().title)
        assertEquals("content://media/external/audio/media/1", back.tracks.first().streamUrl)
    }

    @Test
    fun `空快照与缺字段都能容忍`() {
        val empty = AppJson.decodeFromString(LocalSnapshot.serializer(), "{}")
        assertEquals(0L, empty.savedAt)
        assertTrue(empty.tracks.isEmpty())

        // 旧版本写下的快照可能缺 durationSec / source，不应该整份丢掉
        val legacy = AppJson.decodeFromString(
            LocalSnapshot.serializer(),
            """{"savedAt":1,"tracks":[{"id":"local:9","title":"老歌","artist":"","streamUrl":"content://x"}]}""",
        )
        assertEquals(1, legacy.tracks.size)
        assertEquals(0L, legacy.tracks.first().durationSec)
    }

    @Test
    fun `坏掉的 JSON 返回 null 而不是抛异常`() {
        assertNull(runCatching { AppJson.decodeFromString(LocalSnapshot.serializer(), "not json") }.getOrNull())
        assertNull(runCatching { AppJson.decodeFromString(LocalSnapshot.serializer(), "") }.getOrNull())
    }
}
