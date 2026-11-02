package com.shiyin.player.data.model

/**
 * 音质档位，用于曲目行上的小字标注。
 *
 * 判断口径分两层：
 * 1. **容器**说了算的地方就按容器算 —— `.flac` / `.wav` / `.ape` 是无损，没有争议。
 * 2. 有损格式得看码率 —— 本地媒体库能给出真实的 kbps，网盘拿不到，
 *    只能按「mp3 一般是 320」估。**宁可标「高品」也不标「无损」**：
 *    标错了会让人以为听到了本来不存在的东西。
 */
enum class AudioQuality(val label: String, val rank: Int) {
    LOSSLESS("无损", 3),
    HIGH("高品", 2),
    STANDARD("标准", 1),
    UNKNOWN("", 0),
}

/** 真正的无损容器（PCM 或无损压缩）。DSD 也算。 */
private val LOSSLESS_FORMATS = setOf(
    "flac", "wav", "wave", "ape", "alac", "aiff", "aif", "dsf", "dff", "wv", "tta", "shn",
)

/** 有损容器。 */
private val LOSSY_FORMATS = setOf(
    "mp3", "m4a", "aac", "ogg", "oga", "opus", "wma", "mpc", "amr", "m4b",
)

object AudioQualityOf {

    /** 从 `Track.format` 取格式；老数据没有这个字段就从播放地址的扩展名猜。 */
    fun formatOf(track: Track): String {
        val declared = track.format?.trim()?.lowercase().orEmpty()
        if (declared.isNotEmpty()) return declared
        return extensionOf(track.streamUrl)
    }

    fun of(track: Track): AudioQuality {
        val format = formatOf(track)
        if (format.isEmpty()) return AudioQuality.UNKNOWN

        if (format in LOSSLESS_FORMATS) return AudioQuality.LOSSLESS
        if (format !in LOSSY_FORMATS) return AudioQuality.UNKNOWN

        // 有损：有真实码率就按码率分，没有就按「多数是 320/256」算高品
        val kbps = track.bitrateKbps
        return when {
            kbps <= 0 -> AudioQuality.HIGH
            kbps >= 256 -> AudioQuality.HIGH
            kbps >= 160 -> AudioQuality.STANDARD
            else -> AudioQuality.STANDARD
        }
    }

    /** 曲目行上的小字，比如「FLAC · 无损」；判断不出来就返回空串，不占位。 */
    fun badge(track: Track): String {
        val quality = of(track)
        if (quality == AudioQuality.UNKNOWN) return ""
        val format = formatOf(track).uppercase()
        return if (format.isEmpty()) quality.label else "$format · ${quality.label}"
    }

    private fun extensionOf(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val name = path.substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.lastIndex) return ""
        return name.substring(dot + 1).lowercase()
    }
}
