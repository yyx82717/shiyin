package com.shiyin.player.data.repository

import com.shiyin.player.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SearchMergeTest {

    private fun t(id: String, title: String, artist: String, source: String) = Track(
        id = id,
        title = title,
        artist = artist,
        streamUrl = "x",
        source = source,
    )

    @Test
    fun `same song across sources merges, local wins`() {
        val tracks = listOf(
            t("audius:9", "晴天", "周杰伦", "audius"),
            t("local:1", "晴天", "周杰伦", "local"),
            t("webdav:5", "稻香", "周杰伦", "webdav"),
        )
        val out = MusicRepository.mergeAndRank(tracks, "晴天")
        // 晴天（本地 + audius 合并成 1 条）+ 稻香 1 条 → 共 2
        assertEquals(2, out.size)
        val qingtian = out.first()
        assertEquals("晴天", qingtian.title)
        assertEquals("local", qingtian.source) // 本地优先
        assertFalse(out.any { it.source == "audius" && it.title == "晴天" })
    }

    @Test
    fun `relevance ranking puts exact title match first`() {
        val tracks = listOf(
            t("a:1", "Random Song", "X", "audius"),
            t("a:2", "晴天", "周杰伦", "audius"),
        )
        val out = MusicRepository.mergeAndRank(tracks, "晴天")
        assertEquals("晴天", out.first().title)
    }

    @Test
    fun `different artists with same title stay separate`() {
        val tracks = listOf(
            t("a:1", "晴天", "周杰伦", "audius"),
            t("a:2", "晴天", "林俊杰", "audius"),
        )
        val out = MusicRepository.mergeAndRank(tracks, "晴天")
        assertEquals(2, out.size)
    }
}
