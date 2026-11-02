package com.shiyin.player.data.audio

import org.junit.Assert.assertTrue
import org.junit.Test

class AudioProbeTest {

    /** 造一个标准 WAV 头：RIFF/WAVE + fmt(16) + data。 */
    private fun wavHeader(
        formatTag: Int = 1,
        channels: Int = 2,
        sampleRate: Int = 44_100,
        bits: Int = 16,
        dataBytes: Int = 1_000,
        dataHead: ByteArray? = null,
    ): ByteArray {
        val head = java.io.ByteArrayOutputStream()
        fun ascii(s: String) = head.write(s.toByteArray(Charsets.US_ASCII))
        fun int32(v: Int) {
            head.write(v and 0xFF)
            head.write((v shr 8) and 0xFF)
            head.write((v shr 16) and 0xFF)
            head.write((v shr 24) and 0xFF)
        }
        fun int16(v: Int) {
            head.write(v and 0xFF)
            head.write((v shr 8) and 0xFF)
        }

        val byteRate = sampleRate * channels * bits / 8
        ascii("RIFF")
        int32(36 + dataBytes)
        ascii("WAVE")
        ascii("fmt ")
        int32(16)
        int16(formatTag)
        int16(channels)
        int32(sampleRate)
        int32(byteRate)
        int16(channels * bits / 8)
        int16(bits)
        ascii("data")
        int32(dataBytes)
        if (dataHead != null) head.write(dataHead)
        return head.toByteArray()
    }

    private fun describe(verdict: AudioProbe.Verdict): String = when (verdict) {
        is AudioProbe.Verdict.Ok -> verdict.description
        is AudioProbe.Verdict.Suspicious -> verdict.description
        is AudioProbe.Verdict.Broken -> verdict.description
        is AudioProbe.Verdict.Unknown -> verdict.description
    }

    @Test
    fun `标准 PCM 判为正常`() {
        val verdict = AudioProbe.inspect(wavHeader())
        assertTrue(verdict is AudioProbe.Verdict.Ok)
        assertTrue(describe(verdict).contains("44100 Hz"))
        assertTrue(describe(verdict).contains("16 bit"))
        assertTrue(describe(verdict).contains("2 声道"))
    }

    @Test
    fun `AC3 封装在 WAV 里要报出来`() {
        val verdict = AudioProbe.inspect(wavHeader(formatTag = 0x2000))
        assertTrue(verdict is AudioProbe.Verdict.Suspicious)
        assertTrue(describe(verdict).contains("AC-3"))
    }

    @Test
    fun `DTS 格式码要报出来`() {
        val verdict = AudioProbe.inspect(wavHeader(formatTag = 0x0008))
        assertTrue(verdict is AudioProbe.Verdict.Suspicious)
        assertTrue(describe(verdict).contains("DTS"))
    }

    @Test
    fun `头写 PCM 但数据是 DTS 码流的假 WAV`() {
        // 这是车载 CD 合集里最常见的一种：扩展名和 header 都骗人
        val dts = byteArrayOf(0x7F, 0xFE.toByte(), 0x80.toByte(), 0x01, 0x00, 0x00, 0x00, 0x00)
        val verdict = AudioProbe.inspect(wavHeader(dataHead = dts))
        assertTrue(verdict is AudioProbe.Verdict.Suspicious)
        assertTrue(describe(verdict).contains("DTS"))
    }

    @Test
    fun `普通 PCM 的数据段不会被误判成 DTS`() {
        // 真实 16-bit 采样是从 0x3cff 3cff 这种开始的，不该命中同步字
        val pcm = byteArrayOf(0x3C, 0xFF.toByte(), 0x23, 0x01, 0xE5.toByte(), 0xFE.toByte())
        val verdict = AudioProbe.inspect(wavHeader(dataHead = pcm))
        assertTrue(verdict is AudioProbe.Verdict.Ok)
    }

    @Test
    fun `不是 RIFF 的文件给出开头字节方便排查`() {
        // 至少 12 字节才进入 RIFF 判断，否则会先被「内容太少」拦下
        val verdict = AudioProbe.inspect("ID3\u0004\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000".toByteArray())
        assertTrue(verdict is AudioProbe.Verdict.Unknown)
        assertTrue(describe(verdict).contains("ID3"))
    }

    @Test
    fun `太短的内容不下结论`() {
        val verdict = AudioProbe.inspect(byteArrayOf(1, 2, 3))
        assertTrue(verdict is AudioProbe.Verdict.Unknown)
    }

    @Test
    fun `找不到 fmt 段说明文件不完整`() {
        val head = "RIFF\u0000\u0000\u0000\u0000WAVEdata\u0000\u0000\u0000\u0000".toByteArray(Charsets.US_ASCII)
        val verdict = AudioProbe.inspect(head)
        assertTrue(verdict is AudioProbe.Verdict.Broken)
    }
}
