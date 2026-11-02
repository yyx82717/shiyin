package com.shiyin.player.data.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 二阶 IIR（biquad）系数，形式统一为：
 *
 *     y[n] = b0·x[n] + b1·x[n-1] + b2·x[n-2] − a1·y[n-1] − a2·y[n-2]
 *
 * a0 已经归一化掉了，所以 [a1] / [a2] 是归一化后的值。
 * 这里放的全是纯数学，能直接在 JVM 上单测 —— 均衡器最难调的就是系数，
 * 靠耳朵试错不如把响应曲线算出来对着看。
 */
data class BiquadCoeffs(
    val b0: Float,
    val b1: Float,
    val b2: Float,
    val a1: Float,
    val a2: Float,
) {
    /** 系数为「原样通过」时可以直接跳过整个滤波，省下大把 CPU。 */
    val isIdentity: Boolean
        get() = b0 == 1f && b1 == 0f && b2 == 0f && a1 == 0f && a2 == 0f

    /**
     * 在某个频率上的幅度响应（线性倍数，1.0 = 不增不减）。
     * 用来验证「+6dB 的频段在中心频率上真的是 2 倍」，也给单元测试用。
     */
    fun magnitudeAt(freqHz: Double, sampleRate: Int): Double {
        if (sampleRate <= 0) return 1.0
        val w = 2.0 * PI * freqHz / sampleRate
        // z^-1 = e^{-jω}
        val cw = cos(w)
        val sw = sin(w)
        val c2w = cos(2 * w)
        val s2w = sin(2 * w)
        val numRe = b0 + b1 * cw + b2 * c2w
        val numIm = -(b1 * sw + b2 * s2w)
        val denRe = 1.0 + a1 * cw + a2 * c2w
        val denIm = -(a1 * sw + a2 * s2w)
        val num = sqrt(numRe * numRe + numIm * numIm)
        val den = sqrt(denRe * denRe + denIm * denIm)
        return if (den == 0.0) 1.0 else num / den
    }

    companion object {
        val IDENTITY = BiquadCoeffs(1f, 0f, 0f, 0f, 0f)

        /**
         * 峰值滤波器（RBJ cookbook peaking EQ）。
         *
         * 只抬/压中心频率附近，两侧回落到 0dB，所以 10 个频段叠在一起也不会
         * 把整体音量带跑偏 —— 这正是听感上「调 2kHz 不影响低音」的原因。
         *
         * [freqHz] 超过奈奎斯特频率时返回原样：低采样率文件（比如 22.05k 的
         * 老下载）配上 16kHz 频段就是这种情况，硬算会得到一塌糊涂的系数。
         */
        fun peaking(freqHz: Double, gainDb: Double, q: Double, sampleRate: Int): BiquadCoeffs {
            if (sampleRate <= 0 || q <= 0.0) return IDENTITY
            if (abs(gainDb) < 0.01) return IDENTITY
            if (freqHz <= 0.0 || freqHz >= sampleRate * 0.49) return IDENTITY

            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * freqHz / sampleRate
            val alpha = sin(w0) / (2.0 * q)

            val b0 = 1.0 + alpha * a
            val b1 = -2.0 * cos(w0)
            val b2 = 1.0 - alpha * a
            val a0 = 1.0 + alpha / a
            val a1 = -2.0 * cos(w0)
            val a2 = 1.0 - alpha / a

            return BiquadCoeffs(
                b0 = (b0 / a0).toFloat(),
                b1 = (b1 / a0).toFloat(),
                b2 = (b2 / a0).toFloat(),
                a1 = (a1 / a0).toFloat(),
                a2 = (a2 / a0).toFloat(),
            )
        }
    }
}
