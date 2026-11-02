package com.shiyin.player.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uImportTest {

    private fun track(
        id: String,
        title: String,
        artist: String = "某歌手",
        url: String = "http://host/$id.mp3",
    ) = Track(id = id, title = title, artist = artist, streamUrl = url, source = "webdav")

    @Test
    fun `按完整地址命中`() {
        val t = track("a", "东风破", url = "http://host/music/a.flac")
        val result = M3uImport.match(listOf(M3uEntry("http://host/music/a.flac")), listOf(t))
        assertEquals(listOf(t), result.matched)
        assertTrue(result.missed.isEmpty())
    }

    @Test
    fun `地址和文件名都对不上时不瞎认`() {
        val t = track("a", "东风破", url = "content://media/external/audio/media/9527")
        val result = M3uImport.match(listOf(M3uEntry("/sdcard/其他目录/a.mp3")), listOf(t))
        // 该 track 的文件名是 "9527"，对不上 "a"，应当落空
        assertTrue(result.matched.isEmpty())
        assertEquals(1, result.missed.size)
    }

    @Test
    fun `按文件名命中`() {
        val t = track("a", "东风破", url = "content://media/external/audio/media/9527")
        val result = M3uImport.match(listOf(M3uEntry("/sdcard/Music/9527.mp3")), listOf(t))
        assertEquals(listOf(t), result.matched)
    }

    @Test
    fun `按艺人加歌名命中`() {
        val t = track("a", "东风破", artist = "周杰伦")
        val result = M3uImport.match(
            listOf(M3uEntry("http://别的地址/x.mp3", title = "东风破", artist = "周杰伦")),
            listOf(t),
        )
        assertEquals(listOf(t), result.matched)
    }

    @Test
    fun `只有歌名时，曲库里唯一才认`() {
        val only = track("a", "晴天", artist = "周杰伦")
        assertEquals(
            listOf(only),
            M3uImport.match(listOf(M3uEntry("x", title = "晴天")), listOf(only)).matched,
        )

        val ambiguous = listOf(track("a", "晴天", "周杰伦"), track("b", "晴天", "李荣浩"))
        assertTrue(M3uImport.match(listOf(M3uEntry("x", title = "晴天")), ambiguous).matched.isEmpty())
    }

    @Test
    fun `全角括号与空格不影响匹配`() {
        val t = track("a", "晴天（Live）", artist = "周杰伦")
        val result = M3uImport.match(
            listOf(M3uEntry("x", title = "晴天(Live)", artist = "周杰伦 ")),
            listOf(t),
        )
        assertEquals(listOf(t), result.matched)
    }

    @Test
    fun `同一条记录写两遍只收一次`() {
        val t = track("a", "东风破", url = "http://host/a.mp3")
        val result = M3uImport.match(
            listOf(M3uEntry("http://host/a.mp3"), M3uEntry("http://HOST/a.mp3")),
            listOf(t),
        )
        assertEquals(1, result.matched.size)
    }

    @Test
    fun `统计文案说清命中几首`() {
        val t = track("a", "东风破")
        val ok = M3uImport.match(listOf(M3uEntry("http://host/a.mp3")), listOf(t))
        assertTrue(ok.summary().contains("全部对上"))
        val partial = M3uImport.match(
            listOf(M3uEntry("http://host/a.mp3"), M3uEntry("http://host/zzz.mp3")),
            listOf(t),
        )
        assertTrue(partial.summary().contains("对上 1 首"))
    }

    @Test
    fun `归一化只留字母数字与汉字`() {
        assertEquals("周杰伦晴天live", M3uImport.normalize("周杰伦 - 晴天（Live）"))
        assertEquals("abc123", M3uImport.normalize("  ABC-123  "))
        assertNull(M3uImport.normalize("《》、"))
        assertNull(M3uImport.normalize(null))
    }
}
