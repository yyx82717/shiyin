package com.shiyin.player.data.curated

import com.shiyin.player.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongMatchTest {

    private fun track(
        id: String,
        title: String,
        artist: String,
        album: String? = null,
    ) = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        streamUrl = "content://media/$id",
        durationSec = 200,
        source = "local",
    )

    @Test
    fun `去掉扩展名与括号里的版本说明`() {
        assertEquals("晴天", SongMatch.normalizeTitle("晴天.mp3"))
        assertEquals("晴天", SongMatch.normalizeTitle("晴天 (Live)"))
        assertEquals("晴天", SongMatch.normalizeTitle("晴天【高音质】"))
        assertEquals("晴天", SongMatch.normalizeTitle("晴天（伴奏）"))
    }

    @Test
    fun `全角字母数字与标点统一`() {
        assertEquals("jay", SongMatch.normalizeTitle("ＪＡＹ"))
        assertEquals("斑马斑马", SongMatch.normalizeTitle("斑马，斑马"))
        assertEquals("dontstop", SongMatch.normalizeTitle("Don't  Stop"))
    }

    @Test
    fun `live 只在独立单词时才当噪音词`() {
        // 「alive」不能被当成 live 去掉，否则歌名只剩一个 a
        assertEquals("alive", SongMatch.normalizeTitle("Alive"))
        assertEquals("", SongMatch.normalizeTitle("(Live)"))
    }

    @Test
    fun `同名歌曲优先挑艺人能对上的那个`() {
        val index = SongMatch.buildIndex(
            listOf(
                track("1", "晴天", "李荣浩"),
                track("2", "晴天", "周杰伦"),
            ),
        )
        val hit = SongMatch.find(CuratedSong("晴天", "周杰伦"), index)
        assertEquals("2", hit?.id)
    }

    @Test
    fun `文件名带艺人也算命中`() {
        val index = SongMatch.buildIndex(listOf(track("1", "周杰伦 - 晴天", "周杰伦")))
        val hit = SongMatch.find(CuratedSong("晴天", "周杰伦"), index)
        assertEquals("1", hit?.id)
    }

    @Test
    fun `歌曲不在本地时返回 null`() {
        val index = SongMatch.buildIndex(listOf(track("1", "稻香", "周杰伦")))
        assertNull(SongMatch.find(CuratedSong("晴天", "周杰伦"), index))
    }

    @Test
    fun `合作艺人的写法不影响匹配`() {
        val index = SongMatch.buildIndex(listOf(track("1", "千里之外", "周杰伦 & 费玉清")))
        assertEquals("1", SongMatch.find(CuratedSong("千里之外", "周杰伦 & 费玉清"), index)?.id)
        assertEquals("1", SongMatch.find(CuratedSong("千里之外", "周杰伦"), index)?.id)
    }

    @Test
    fun `专辑命中数按歌名统计`() {
        val album = CuratedCatalog.albums.first { it.id == "guofeng" }
        val songs = album.songs.take(3)
        val index = SongMatch.buildIndex(songs.map { track(it.title, it.title, it.artist) })
        assertEquals(3, CuratedCatalog.matchedCount(album, index))
        assertTrue(album.songs.size > 3)
    }
}
