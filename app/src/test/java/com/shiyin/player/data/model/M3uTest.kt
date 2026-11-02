package com.shiyin.player.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uTest {

    private fun track(
        id: String,
        title: String = id,
        artist: String = "某歌手",
        url: String = "http://host/$id.mp3",
        durationSec: Long = 200L,
    ) = Track(
        id = id,
        title = title,
        artist = artist,
        streamUrl = url,
        source = "webdav",
        durationSec = durationSec,
    )

    @Test
    fun `写出去再读回来，歌名艺人和时长都不丢`() {
        val text = M3u.write("我的歌单", listOf(track("a", "东风破", "周杰伦", durationSec = 253)))
        val entries = M3u.parse(text)
        assertEquals(1, entries.size)
        assertEquals("周杰伦", entries[0].artist)
        assertEquals("东风破", entries[0].title)
        assertEquals(253L, entries[0].durationSec)
        assertEquals("http://host/a.mp3", entries[0].uri)
    }

    @Test
    fun `头部带歌单名和首行标记`() {
        val text = M3u.write("夜间", listOf(track("a")))
        assertTrue(text.startsWith("#EXTM3U\n"))
        assertEquals("夜间", M3u.playlistName(text))
    }

    @Test
    fun `BOM CRLF 与空行都能读`() {
        val raw = "\uFEFF#EXTM3U\r\n\r\n#EXTINF:12,某歌手 - 歌\r\nC:\\Music\\a.mp3\r\n"
        val entries = M3u.parse(raw)
        assertEquals(1, entries.size)
        assertEquals("C:\\Music\\a.mp3", entries[0].uri)
        assertEquals(12L, entries[0].durationSec)
    }

    @Test
    fun `带属性与负时长（流媒体）不炸`() {
        val raw = "#EXTINF:-1 tvg-id=\"x\",Radio\nhttp://host/live\n"
        val entries = M3u.parse(raw)
        assertEquals(1, entries.size)
        assertEquals(0L, entries[0].durationSec)
        assertEquals("Radio", entries[0].title)
    }

    @Test
    fun `没有 EXTINF 的裸路径也收`() {
        val entries = M3u.parse("/sdcard/Music/a.flac\n/sdcard/Music/b.flac\n")
        assertEquals(2, entries.size)
        assertNull(entries[0].title)
        assertEquals("/sdcard/Music/b.flac", entries[1].uri)
    }

    @Test
    fun `EXTINF 后面没有路径时不留幽灵条目`() {
        val entries = M3u.parse("#EXTINF:100,孤儿\n")
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `缺艺人时 display 只显示歌名`() {
        assertEquals("东风破", M3uEntry("x.mp3", title = "东风破").display)
        assertEquals("a.mp3", M3uEntry("/m/a.mp3").display)
    }
}
