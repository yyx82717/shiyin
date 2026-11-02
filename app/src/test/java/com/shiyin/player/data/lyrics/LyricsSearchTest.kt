package com.shiyin.player.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsSearchTest {

    private val sample = """
        [
          {
            "id": 3396226,
            "trackName": "晴天",
            "artistName": "周杰伦",
            "albumName": "叶惠美",
            "duration": 269,
            "instrumental": false,
            "plainLyrics": "故事的小黄花",
            "syncedLyrics": "[00:01.00]故事的小黄花"
          },
          {
            "id": 1,
            "trackName": "晴天 (伴奏)",
            "artistName": "周杰伦",
            "albumName": null,
            "duration": 269,
            "instrumental": false,
            "plainLyrics": null,
            "syncedLyrics": null
          }
        ]
    """.trimIndent()

    @Test
    fun `解析 LRCLIB 搜索结果，丢掉没有歌词内容的条目`() {
        val list = parseLrclibSearch(sample)
        assertEquals(1, list.size)
        val hit = list.first()
        assertEquals("晴天", hit.title)
        assertEquals("周杰伦", hit.artist)
        assertEquals("叶惠美", hit.album)
        assertEquals(269L, hit.durationSec)
        assertTrue(hit.synced)
        assertEquals("LRCLIB", hit.source)
    }

    @Test
    fun `纯器乐条目保留但没有内容`() {
        val body = """[{"id":9,"trackName":"夜的钢琴曲五","artistName":"石进","instrumental":true,"duration":180}]"""
        val list = parseLrclibSearch(body)
        assertEquals(1, list.size)
        assertFalse(list.first().hasContent)
        assertNull(list.first().toLyrics())
    }

    @Test
    fun `服务器返回 HTML 时返回空列表而不是崩溃`() {
        assertTrue(parseLrclibSearch("<html><body>502 Bad Gateway</body></html>").isEmpty())
        assertTrue(parseLrclibSearch("").isEmpty())
        assertTrue(parseLrclibSearch("{\"data\":[]}").isEmpty())
    }

    @Test
    fun `同名同长只保留一条`() {
        val body = """[
            {"id":1,"trackName":"晴天","artistName":"周杰伦","duration":269,"syncedLyrics":"[00:01.00]a"},
            {"id":2,"trackName":"晴天","artistName":"周杰伦","duration":269,"plainLyrics":"a"}
        ]"""
        assertEquals(1, parseLrclibSearch(body).size)
    }

    @Test
    fun `手动粘贴带时间轴按同步歌词解析`() {
        val lyrics = parseManualLyrics("[00:12.00]第一句\n[00:15.50]第二句")
        assertNotNull(lyrics)
        assertEquals(2, lyrics!!.lines.size)
        assertTrue(lyrics.synced)
        assertEquals(12_000L, lyrics.lines[0].timeMs)
        assertEquals(15_500L, lyrics.lines[1].timeMs)
    }

    @Test
    fun `手动粘贴纯文本按静态歌词解析`() {
        val lyrics = parseManualLyrics("第一句\n\n第二句")
        assertNotNull(lyrics)
        assertFalse(lyrics!!.synced)
        assertEquals(2, lyrics.lines.size)
        assertEquals(-1L, lyrics.lines[0].timeMs)
    }

    @Test
    fun `空白内容不会生成歌词`() {
        assertNull(parseManualLyrics("   \n  "))
    }

    @Test
    fun `lyrics ovh 只有纯文本也能用`() {
        val body = """{"lyrics":"Look at the stars\nAnd everything you do"}"""
        val candidate = parseLyricsOvh(body, "Yellow", "Coldplay")
        assertNotNull(candidate)
        assertEquals("Coldplay", candidate!!.artist)
        assertFalse(candidate.synced)
        val lyrics = candidate.toLyrics()
        assertEquals(2, lyrics?.lines?.size ?: 0)
    }

    @Test
    fun `候选转歌词优先用带时间轴的那份`() {
        val candidate = LyricsCandidate(
            key = "x",
            title = "晴天",
            artist = "周杰伦",
            album = null,
            durationSec = 269,
            synced = true,
            source = "LRCLIB",
            syncedRaw = "[00:01.00]故事的小黄花",
            plainRaw = "故事的小黄花",
        )
        val lyrics = candidate.toLyrics()
        assertTrue(lyrics!!.synced)
        assertEquals(1_000L, lyrics.lines.first().timeMs)
    }
}
