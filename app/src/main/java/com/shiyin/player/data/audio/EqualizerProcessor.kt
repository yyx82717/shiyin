package com.shiyin.player.data.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import kotlin.math.abs

/**
 * 10 段图示均衡器（PCM 16-bit，直接挂在 ExoPlayer 的音频链上）。
 *
 * 为什么不用系统的 `android.media.audiofx.Equalizer`：
 * 那个是「按设备给的频段」来的，多数手机只有 5 段（60/230/910/3600/14000），
 * 还要额外占用一个 audio session，切歌、抢焦点时容易失效。自己处理 PCM
 * 就能固定 10 段，行为和手机上装的是什么机器无关。
 *
 * 几个必须处理好的点：
 * 1. **不能在关闭时把自己摘出链条**。ExoPlayer 只在音频格式变化时重新 configure，
 *    如果 `isActive` 返回 false，中途打开 EQ 要等下一首歌才生效。所以这里永远
 *    active，关闭时走零拷贝的直通路径。
 * 2. **增益要渐变**。拖动滑块时如果直接换系数，波形会跳，听感上就是「咔」一声。
 *    这里每 256 帧最多移动 0.35dB，12dB 大概 200ms 走完。
 * 3. **超采样率的频段要放弃**。22.05kHz 的文件上 16kHz 段已经贴着奈奎斯特，
 *    硬算出来的系数会把声音搞坏，直接当 0dB。
 */
@UnstableApi
class EqualizerProcessor : BaseAudioProcessor() {

    /** 当前生效的目标增益（dB），由 UI 线程写、音频线程读。 */
    @Volatile
    private var target: FloatArray = FloatArray(EQ_BAND_COUNT)

    private val current = FloatArray(EQ_BAND_COUNT)
    private val coeffs = Array(EQ_BAND_COUNT) { BiquadCoeffs.IDENTITY }

    /** 每个（频段 × 声道）各自四个状态量：x[n-1]、x[n-2]、y[n-1]、y[n-2]。 */
    private var state = FloatArray(EQ_BAND_COUNT * 2 * 4)

    private var sampleRate = 44_100
    private var channelCount = 2

    /** 直通：目标与当前增益全为 0 时不跑滤波，只复制样本。 */
    @Volatile
    private var bypass = true

    /** 音频格式是否已被接受。未 configure 前不处理任何数据。 */
    private var ready = false


    val bandFreqs: FloatArray get() = EQ_BAND_FREQS

    /** 从设置/界面调用。可以在任意线程调。 */
    fun update(enabled: Boolean, gains: List<Float>) {
        val next = FloatArray(EQ_BAND_COUNT) { index ->
            if (enabled) (gains.getOrNull(index) ?: 0f).coerceIn(EQ_MIN_DB, EQ_MAX_DB) else 0f
        }
        target = next
        // 目标一旦非 0 就必须退出直通，否则 advanceGains 永远不跑，EQ 就是死的
        bypass = next.all { abs(it) < 0.01f } && current.all { abs(it) < 0.01f }
    }

    /** 当前目标增益，给界面回显用。 */
    fun gains(): FloatArray = target.copyOf()

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // 只处理 16-bit 单/双声道（手机上的音乐基本都是）。
        // 其余格式（24/32-bit WAV、float 输出、5.1）安静退出，不阻断播放。
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
            inputAudioFormat.channelCount !in 1..2
        ) {
            ready = false
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8_000)
        channelCount = inputAudioFormat.channelCount
        state = FloatArray(EQ_BAND_COUNT * channelCount * 4)
        rebuildCoeffs()
        ready = true
        return inputAudioFormat
    }

    // isActive 用 BaseAudioProcessor 的默认实现（outputAudioFormat != NOT_SET）。
    // 对 16-bit PCM 我们现在是活的，随时开关都能立刻生效；遇到 24-bit / 5.1
    // 就退回 NOT_SET 自动退出链条 —— **不能抛异常**：音频链上任何一个处理器抛
    // UnhandledAudioFormatException，ExoPlayer 会认定整条链失败，
    // 结果是那些本来能播的歌直接没声音。

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!ready || !inputBuffer.hasRemaining()) return

        val bytesPerFrame = 2 * channelCount
        val frames = inputBuffer.remaining() / bytesPerFrame
        if (frames <= 0) return

        val out = replaceOutputBuffer(frames * bytesPerFrame)
        val src = inputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()
        val dst = out.order(ByteOrder.nativeOrder()).asShortBuffer()

        var done = 0
        while (done < frames) {
            val chunk = minOf(SMOOTH_BLOCK_FRAMES, frames - done)
            if (!bypass) advanceGains()
            if (bypass) {
                copyThrough(src, dst, chunk * channelCount)
            } else {
                filter(src, dst, chunk)
            }
            done += chunk
        }

        inputBuffer.position(inputBuffer.position() + frames * bytesPerFrame)
        // 视图的 position 是独立的，要让 BaseAudioProcessor 知道写了多少
        out.position(frames * bytesPerFrame)
        out.flip()
    }

    override fun onFlush() {
        state.fill(0f)
    }

    override fun onReset() {
        state.fill(0f)
        current.fill(0f)
        coeffs.fill(BiquadCoeffs.IDENTITY)
        bypass = target.all { abs(it) < 0.01f }
        ready = false
    }

    private fun copyThrough(src: ShortBuffer, dst: ShortBuffer, count: Int) {
        for (i in 0 until count) dst.put(src.get())
    }

    private fun filter(src: ShortBuffer, dst: ShortBuffer, frames: Int) {
        val ch = channelCount
        for (f in 0 until frames) {
            for (c in 0 until ch) {
                var x = src.get().toFloat()
                for (b in 0 until EQ_BAND_COUNT) {
                    val k = coeffs[b]
                    if (k.isIdentity) continue
                    val i = (b * ch + c) * 4
                    val y = k.b0 * x + k.b1 * state[i] + k.b2 * state[i + 1] -
                        k.a1 * state[i + 2] - k.a2 * state[i + 3]
                    state[i + 1] = state[i]
                    state[i] = x
                    state[i + 3] = state[i + 2]
                    state[i + 2] = y
                    x = y
                }
                // 10 段一起 +12dB 确实会冲出 16-bit 范围，钳位比破音好
                dst.put(
                    when {
                        x > 32_767f -> Short.MAX_VALUE
                        x < -32_768f -> Short.MIN_VALUE
                        else -> x.toInt().toShort()
                    },
                )
            }
        }
    }

    /** 让当前增益朝目标走一小步，并重算系数。 */
    private fun advanceGains() {
        var changed = false
        var stillMoving = false
        for (i in 0 until EQ_BAND_COUNT) {
            val diff = target[i] - current[i]
            if (abs(diff) < GAIN_EPSILON) {
                if (current[i] != target[i]) {
                    current[i] = target[i]
                    changed = true
                }
            } else {
                current[i] += diff.coerceIn(-SMOOTH_STEP_DB, SMOOTH_STEP_DB)
                changed = true
                stillMoving = true
            }
        }
        if (changed) rebuildCoeffs()
        if (!stillMoving) {
            bypass = target.all { abs(it) < 0.01f } && current.all { abs(it) < 0.01f }
        }
    }

    private fun rebuildCoeffs() {
        for (i in 0 until EQ_BAND_COUNT) {
            coeffs[i] = BiquadCoeffs.peaking(
                freqHz = EQ_BAND_FREQS[i].toDouble(),
                gainDb = current[i].toDouble(),
                q = EQ_Q.toDouble(),
                sampleRate = sampleRate,
            )
        }
    }

    private companion object {
        /** 每多少帧平滑一次增益。44.1kHz 下约 5.8ms，人耳听不出台阶。 */
        const val SMOOTH_BLOCK_FRAMES = 256
        const val SMOOTH_STEP_DB = 0.35f
        const val GAIN_EPSILON = 0.02f
    }
}
