package com.shiyin.player.data.audio

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 均衡器的系数是纯数学，能在 JVM 上算准，就没必要拿耳朵去猜。
 * 这里验证的是「+6dB 的频段在中心频率上真的放大一倍」这个物理意义。
 */
class BiquadTest {

    @Test
    fun `0dB 时系数是原样通过`() {
        val coeffs = BiquadCoeffs.peaking(1_000.0, 0.0, 1.4, 44_100)
        assertTrue(coeffs.isIdentity)
    }

    @Test
    fun `正增益在中心频率上按 dB 放大`() {
        val coeffs = BiquadCoeffs.peaking(1_000.0, 6.0, 1.4, 44_100)
        val magnitude = coeffs.magnitudeAt(1_000.0, 44_100)
        // +6dB ≈ 1.995 倍
        assertEquals(1.995, magnitude, 0.02)
    }

    @Test
    fun `负增益在中心频率上按 dB 衰减`() {
        val coeffs = BiquadCoeffs.peaking(1_000.0, -6.0, 1.4, 44_100)
        val magnitude = coeffs.magnitudeAt(1_000.0, 44_100)
        assertEquals(0.501, magnitude, 0.02)
    }

    @Test
    fun `远离中心频率时几乎不受影响`() {
        val coeffs = BiquadCoeffs.peaking(1_000.0, 12.0, 1.4, 44_100)
        // 一个倍频程以外应该已经回落得差不多，这正是「调 1k 不影响低音」的原因
        val low = coeffs.magnitudeAt(60.0, 44_100)
        val high = coeffs.magnitudeAt(16_000.0, 44_100)
        assertTrue("低频被带跑了：$low", abs(low - 1.0) < 0.1)
        assertTrue("高频被带跑了：$high", abs(high - 1.0) < 0.1)
    }

    @Test
    fun `频率超过奈奎斯特时放弃而不是算出一堆垃圾系数`() {
        // 22.05kHz 的采样率上，16kHz 已经贴着 11.025kHz 的奈奎斯特线
        val coeffs = BiquadCoeffs.peaking(16_000.0, 12.0, 1.4, 22_050)
        assertTrue(coeffs.isIdentity)
    }

    @Test
    fun `非法参数直接原样通过`() {
        assertTrue(BiquadCoeffs.peaking(0.0, 6.0, 1.4, 44_100).isIdentity)
        assertTrue(BiquadCoeffs.peaking(1_000.0, 6.0, 0.0, 44_100).isIdentity)
        assertTrue(BiquadCoeffs.peaking(1_000.0, 6.0, 1.4, 0).isIdentity)
    }

    @Test
    fun `预设的频段数量与常量一致`() {
        EqPresets.ALL.forEach { preset ->
            assertEquals(preset.label, EQ_BAND_COUNT, preset.gains.size)
            preset.gains.forEach {
                assertTrue("${preset.label} 超出 ±12dB：$it", it in EQ_MIN_DB..EQ_MAX_DB)
            }
        }
    }

    @Test
    fun `设置的长度不对时会被补齐成十段`() {
        val broken = EqSettings(enabled = true, gains = listOf(3f, -3f))
        val fixed = broken.normalized()
        assertEquals(EQ_BAND_COUNT, fixed.gains.size)
        assertEquals(3f, fixed.gains[0], 0.001f)
        assertEquals(-3f, fixed.gains[1], 0.001f)
        assertEquals(0f, fixed.gains[9], 0.001f)
    }

    @Test
    fun `超出范围的增益会被夹住`() {
        val loud = EqSettings(enabled = true, gains = List(EQ_BAND_COUNT) { 40f })
        assertTrue(loud.normalized().gains.all { it == EQ_MAX_DB })
    }

    @Test
    fun `频段标签按 k 缩写`() {
        assertEquals("31", bandLabel(31f))
        assertEquals("125", bandLabel(125f))
        assertEquals("1k", bandLabel(1_000f))
        assertEquals("4k", bandLabel(4_000f))
        assertEquals("16k", bandLabel(16_000f))
    }
}
