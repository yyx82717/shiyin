package com.shiyin.player.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioQualityOfTest {

    private fun track(
        format: String? = null,
        bitrate: Int = 0,
        url: String = "http://host/a.mp3",
    ) = Track(
        id = "x",
        title = "x",
        artist = "y",
        streamUrl = url,
        format = format,
        bitrateKbps = bitrate,
    )

    @Test
    fun `无损容器直接判无损`() {
        listOf("flac", "wav", "ape", "alac", "dsf", "dff").forEach { format ->
            assertEquals(format, AudioQuality.LOSSLESS, AudioQualityOf.of(track(format = format)))
        }
    }

    @Test
    fun `有损格式没有码率时按高品算`() {
        // 网盘只给得出文件名，拿不到码率；标成「标准」会把 320 的歌冤枉了
        assertEquals(AudioQuality.HIGH, AudioQualityOf.of(track(format = "mp3")))
        assertEquals(AudioQuality.HIGH, AudioQualityOf.of(track(format = "m4a")))
    }

    @Test
    fun `有真实码率时按码率分档`() {
        assertEquals(AudioQuality.HIGH, AudioQualityOf.of(track(format = "mp3", bitrate = 320)))
        assertEquals(AudioQuality.HIGH, AudioQualityOf.of(track(format = "mp3", bitrate = 256)))
        assertEquals(AudioQuality.STANDARD, AudioQualityOf.of(track(format = "mp3", bitrate = 192)))
        assertEquals(AudioQuality.STANDARD, AudioQualityOf.of(track(format = "mp3", bitrate = 128)))
    }

    @Test
    fun `老数据没有 format 字段时从地址推`() {
        assertEquals(
            AudioQuality.LOSSLESS,
            AudioQualityOf.of(track(url = "http://host/a/b%E5%8D%A7%E9%93%BA/b.flac")),
        )
        assertEquals(AudioQuality.HIGH, AudioQualityOf.of(track(url = "http://host/x/歌.mp3?x=1")))
        assertEquals(AudioQuality.UNKNOWN, AudioQualityOf.of(track(url = "content://media/external/audio/media/1")))
    }

    @Test
    fun `未知容器不硬猜`() {
        assertEquals(AudioQuality.UNKNOWN, AudioQualityOf.of(track(format = "rar")))
        assertEquals("", AudioQualityOf.badge(track(format = "rar")))
    }

    @Test
    fun `徽章文案把格式和档位拼起来`() {
        assertEquals("FLAC · 无损", AudioQualityOf.badge(track(format = "flac")))
        assertEquals("MP3 · 高品", AudioQualityOf.badge(track(format = "mp3")))
    }

    @Test
    fun `无损的排位高于高品`() {
        assertTrue(AudioQuality.LOSSLESS.rank > AudioQuality.HIGH.rank)
        assertTrue(AudioQuality.HIGH.rank > AudioQuality.STANDARD.rank)
    }
}
