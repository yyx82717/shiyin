package com.shiyin.player.data.audio

import java.util.Locale
import kotlinx.serialization.Serializable

/** 10 段中心频率。低频段用 1/1 倍频程，高频段拉开一点，符合人耳在各频段的敏感度差异。 */
val EQ_BAND_FREQS: FloatArray = floatArrayOf(
    31f, 62f, 125f, 250f, 500f, 1_000f, 2_000f, 4_000f, 8_000f, 16_000f,
)

const val EQ_BAND_COUNT = 10

/** 增益上下限。再大就不只是「调音」，而是在制造削波了。 */
const val EQ_MIN_DB = -12f
const val EQ_MAX_DB = 12f

/** 每个频段的 Q。1.4 大约是一个倍频程宽，相邻频段有一点重叠，听起来才连贯。 */
const val EQ_Q = 1.4f

/** 频段短标签：31 / 125 / 1k / 16k */
fun bandLabel(freqHz: Float): String = when {
    freqHz >= 10_000f -> "${(freqHz / 1000f).toInt()}k"
    freqHz >= 1_000f -> {
        val k = freqHz / 1000f
        if (k == k.toInt().toFloat()) "${k.toInt()}k" else String.format(Locale.US, "%.1fk", k)
    }
    else -> freqHz.toInt().toString()
}

@Serializable
data class EqSettings(
    val enabled: Boolean = false,
    /** 长度固定为 [EQ_BAND_COUNT]，单位 dB。 */
    val gains: List<Float> = List(EQ_BAND_COUNT) { 0f },
    /** 当前套用的预设 id；手动改过任一频段后变成 [EqPresets.CUSTOM]。 */
    val preset: String = EqPresets.FLAT,
) {
    /** 补齐/裁剪成 10 段并把值夹到合法范围：老配置、手改过的 JSON 都能安全进来。 */
    fun normalized(): EqSettings {
        val fixed = List(EQ_BAND_COUNT) { index ->
            (gains.getOrNull(index) ?: 0f).coerceIn(EQ_MIN_DB, EQ_MAX_DB)
        }
        return copy(gains = fixed)
    }

    val isFlat: Boolean get() = gains.all { kotlin.math.abs(it) < 0.05f }
}

data class EqPreset(val id: String, val label: String, val gains: List<Float>)

object EqPresets {

    const val FLAT = "flat"
    const val CUSTOM = "custom"

    val ALL: List<EqPreset> = listOf(
        EqPreset(FLAT, "平直", List(EQ_BAND_COUNT) { 0f }),
        // 低频抬一点、中高频收一点：手机外放和耳机上都能立刻听出「更饱满」
        EqPreset("pop", "流行", listOf(2.5f, 2f, 1f, 0.5f, 0f, -0.5f, 0f, 1f, 2f, 2f)),
        EqPreset("rock", "摇滚", listOf(4f, 3f, 1.5f, -0.5f, -1.5f, 0f, 1.5f, 2.5f, 3f, 2.5f)),
        EqPreset("vocal", "人声", listOf(-2f, -1.5f, 0f, 1.5f, 3f, 3.5f, 2.5f, 1f, 0f, -1f)),
        EqPreset("bass", "低音", listOf(7f, 5.5f, 3.5f, 1.5f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqPreset("treble", "高音", listOf(0f, 0f, 0f, 0f, 0f, 1f, 2.5f, 4f, 5.5f, 6f)),
        EqPreset("classical", "古典", listOf(3f, 2f, 0f, 0f, 0f, 0f, -1f, 0f, 2f, 3f)),
        // 睡前听：把容易刺耳的 2–8k 压下来，音量感更柔
        EqPreset("night", "夜间", listOf(3f, 2f, 0.5f, 0f, -1f, -2f, -2.5f, -2f, -1.5f, -2f)),
    )

    fun byId(id: String): EqPreset? = ALL.firstOrNull { it.id == id }
}
