package com.shiyin.player.data.download

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 下载文件名与本地地址测试。
 *
 * 这几行代码出错不会报异常，只会表现为「下完了放不出来」——
 * 后缀错了播放器不认，`file://` 少一道斜杠会被当成主机名。
 */
class DownloadNamingTest {

    @Test
    fun `带查询串的地址能取到后缀`() {
        assertEquals("mp3", audioExtensionOf("https://cdn.example.com/a/track.mp3?token=abc"))
        assertEquals("flac", audioExtensionOf("https://cdn.example.com/a/track.flac"))
        assertEquals("flac", audioExtensionOf("https://cdn.example.com/a/TRACK.FLAC"))
    }

    @Test
    fun `没有后缀时落到 m4a`() {
        // Audius 的 stream 接口就是这种：路径里看不到扩展名，302 之后才是音频
        assertEquals("m4a", audioExtensionOf("https://api.audius.co/v1/tracks/W08NKdZ/stream?app_name=shiyin"))
        assertEquals("m4a", audioExtensionOf("https://example.com/download"))
        assertEquals("m4a", audioExtensionOf(""))
    }

    @Test
    fun `过长或带符号的伪后缀不被当成扩展名`() {
        assertEquals("m4a", audioExtensionOf("https://example.com/a.verylongextension"))
        assertEquals("m4a", audioExtensionOf("https://example.com/v1.2/track"))
    }

    @Test
    fun `本地路径转成三斜杠 file 地址`() {
        // 少一道斜杠会被解析成「host 是 storage」，ExoPlayer 直接打不开
        assertEquals(
            "file:///storage/emulated/0/Music/a.mp3",
            fileUri("/storage/emulated/0/Music/a.mp3"),
        )
        assertEquals("file:///data/user/0/com.shiyin.player/files/downloads/x.m4a",
            fileUri("/data/user/0/com.shiyin.player/files/downloads/x.m4a"))
    }

    @Test
    fun `缺前导斜杠时自动补上`() {
        assertEquals("file:///a.mp3", fileUri("a.mp3"))
    }
}
