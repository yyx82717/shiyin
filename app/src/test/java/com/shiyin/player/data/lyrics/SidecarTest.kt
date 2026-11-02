package com.shiyin.player.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 网盘旁挂歌词的 URL 推算。
 *
 * 这段逻辑直接决定「网盘里的 .lrc 能不能被找到」，推错一个字符就是 404，
 * 而且失败是静默的（回退到在线搜索），线上很难察觉，所以边界都钉住。
 */
class SidecarTest {

    @Test
    fun `普通 mp3 换成同名 lrc`() {
        assertEquals(
            "http://192.168.8.192:5244/dav/music/song/a.mp3".replace(".mp3", ".lrc"),
            Sidecar.lrcUrlFor("http://192.168.8.192:5244/dav/music/song/a.mp3"),
        )
    }

    @Test
    fun `flac 也能换`() {
        assertEquals(
            "http://host/dav/m/x.flac".replace(".flac", ".lrc"),
            Sidecar.lrcUrlFor("http://host/dav/m/x.flac"),
        )
    }

    @Test
    fun `百分号编码的中文路径原样保留`() {
        val url = "http://h:5244/dav/music/song/%E5%AD%99%E9%9C%B2/%E4%B8%80%E4%B8%AA.flac"
        assertEquals(
            "http://h:5244/dav/music/song/%E5%AD%99%E9%9C%B2/%E4%B8%80%E4%B8%AA.lrc",
            Sidecar.lrcUrlFor(url),
        )
    }

    @Test
    fun `查询串被丢掉`() {
        assertEquals(
            "http://h/dav/music/a.lrc",
            Sidecar.lrcUrlFor("http://h/dav/music/a.mp3?sign=abc&t=1"),
        )
    }

    @Test
    fun `锚点被丢掉`() {
        assertEquals("http://h/dav/music/a.lrc", Sidecar.lrcUrlFor("http://h/dav/music/a.mp3#x"))
    }

    @Test
    fun `扩展名大小写不敏感`() {
        assertEquals("http://h/dav/music/a.lrc", Sidecar.lrcUrlFor("http://h/dav/music/a.MP3"))
        assertEquals("http://h/dav/music/a.lrc", Sidecar.lrcUrlFor("http://h/dav/music/a.FlAc"))
    }

    @Test
    fun `不是音频扩展名就不猜`() {
        assertNull(Sidecar.lrcUrlFor("http://h/dav/music/readme.txt"))
        assertNull(Sidecar.lrcUrlFor("http://h/dav/music/cover.jpg"))
    }

    @Test
    fun `已经是 lrc 不再套娃`() {
        assertNull(Sidecar.lrcUrlFor("http://h/dav/music/a.lrc"))
    }

    @Test
    fun `没有扩展名返回 null`() {
        assertNull(Sidecar.lrcUrlFor("http://h/dav/music/noext"))
    }

    @Test
    fun `非 http 协议返回 null`() {
        assertNull(Sidecar.lrcUrlFor("file:///sdcard/a.mp3"))
        assertNull(Sidecar.lrcUrlFor("/sdcard/a.mp3"))
        assertNull(Sidecar.lrcUrlFor(""))
    }

    @Test
    fun `目录名里的点不会被误当扩展名`() {
        // /a.b/song 里最后的点是目录名的一部分，路径本身没有扩展名
        assertNull(Sidecar.lrcUrlFor("http://h/dav/a.b/song"))
    }

    @Test
    fun `斜杠后紧跟点的隐藏文件不认`() {
        assertNull(Sidecar.lrcUrlFor("http://h/dav/.mp3"))
    }

    @Test
    fun `只有文件名没有目录也能处理`() {
        assertEquals("http://h/a.lrc", Sidecar.lrcUrlFor("http://h/a.mp3"))
    }
}
