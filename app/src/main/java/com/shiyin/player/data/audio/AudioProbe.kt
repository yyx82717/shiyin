package com.shiyin.player.data.audio

/**
 * 音频文件头体检。
 *
 * 起因：用户网盘里的 WAV 放出来全是「沙沙」声。查了发现那个文件是标准 16-bit
 * PCM、数据也正常，所以沙沙声只可能来自「文件根本不是它自称的格式」——
 * 国内下载来的车载 CD 合集里，`.wav` 扩展名下塞 DTS 码流是常事，
 * ExoPlayer 按 PCM 硬解就是一片白噪声。
 *
 * 这里只读前几 KB，把结论说成人话。纯字节分析，能在 JVM 上单测。
 */
object AudioProbe {

    sealed interface Verdict {
        data class Ok(val description: String) : Verdict
        data class Suspicious(val description: String) : Verdict
        data class Broken(val description: String) : Verdict
        data class Unknown(val description: String) : Verdict
    }

    /** WAVE 的格式码。 */
    private const val WAVE_FORMAT_PCM = 0x0001
    private const val WAVE_FORMAT_IEEE_FLOAT = 0x0003
    private const val WAVE_FORMAT_EXTENSIBLE = 0xFFFE
    private const val WAVE_FORMAT_MPEG = 0x0050
    private const val WAVE_FORMAT_AC3 = 0x2000
    private const val WAVE_FORMAT_DTS = 0x0008

    /** DTS 码流的同步字（小端/大端两种写法）。 */
    private val DTS_SYNC = listOf(
        byteArrayOf(0x7F, 0xFE.toByte(), 0x80.toByte(), 0x01),
        byteArrayOf(0xFE.toByte(), 0x7F, 0x01, 0x80.toByte()),
    )

    /** AC-3 的同步字。 */
    private val AC3_SYNC = byteArrayOf(0x0B, 0x77)

    /**
     * 分析文件头。
     *
     * [head] 至少 44 字节才能判断 WAV；给得多一点（几 KB）能顺带看数据段开头。
     */
    fun inspect(head: ByteArray): Verdict {
        if (head.size < 12) return Verdict.Unknown("读到的内容太少（${head.size} 字节），没法判断")

        val riff = ascii(head, 0, 4)
        val wave = ascii(head, 8, 4)
        if (riff != "RIFF" || wave != "WAVE") {
            // 十六进制 + 可读字符一起给：遇到 ID3 开头的文件，一眼就知道是 mp3 改了扩展名
            return Verdict.Unknown("这不是 WAV 文件（开头是 ${preview(head, 0, 4)}）")
        }

        var pos = 12
        var fmt: Fmt? = null
        var dataOffset = -1
        var dataSize = 0
        var guard = 0

        while (pos + 8 <= head.size && guard < 32) {
            val id = ascii(head, pos, 4)
            val size = readIntLe(head, pos + 4)
            if (size < 0 || size > head.size * 64) return Verdict.Broken("文件头损坏：$id 段长度异常（$size）")

            if (id == "fmt " && pos + 8 + 16 <= head.size) {
                val base = pos + 8
                fmt = Fmt(
                    tag = readShortLe(head, base),
                    channels = readShortLe(head, base + 2),
                    sampleRate = readIntLe(head, base + 4),
                    bitsPerSample = readShortLe(head, base + 14),
                    validBits = if (size >= 20 && readShortLe(head, base + 18) > 0) {
                        readShortLe(head, base + 18)
                    } else {
                        0
                    },
                    subFormat = if (size >= 40 && readShortLe(head, base) == WAVE_FORMAT_EXTENSIBLE) {
                        hex(head, base + 24, 2)
                    } else {
                        ""
                    },
                )
            }
            if (id == "data") {
                dataOffset = pos + 8
                dataSize = size
                break
            }
            pos += 8 + size + (size and 1)
            guard++
        }

        val f = fmt ?: return Verdict.Broken("找不到 fmt 段，文件可能不完整")
        val description = buildString {
            append("${f.sampleRate} Hz · ${f.bitsPerSample} bit · ${f.channels} 声道")
            if (f.validBits in 1..f.bitsPerSample && f.validBits != f.bitsPerSample) {
                append("（有效 ${f.validBits} bit）")
            }
        }

        // 1) 格式码直接说明了「这不是 PCM」
        when (f.tag) {
            WAVE_FORMAT_AC3 -> return Verdict.Suspicious(
                "WAV 里装的是 AC-3（杜比）码流，不是 PCM。手机没有杜比解码器，" +
                    "硬播就是沙沙声。$description",
            )
            WAVE_FORMAT_DTS -> return Verdict.Suspicious(
                "WAV 里装的是 DTS 码流，不是 PCM。手机没有 DTS 解码器，硬播就是沙沙声。$description",
            )
            WAVE_FORMAT_MPEG -> return Verdict.Suspicious(
                "WAV 里装的是 MP3 码流。部分设备能放，放不了就会是沙沙声。$description",
            )
        }

        // 2) 头里写着 PCM，但数据段开头是别的压缩码流 —— 车载 CD 集的经典伪装
        if (dataOffset in 0..(head.size - 8)) {
            val dataHead = head.copyOfRange(dataOffset, minOf(head.size, dataOffset + 64))
            if (containsDtsSync(dataHead)) {
                return Verdict.Suspicious(
                    "文件头写着 PCM，但音频数据是 DTS 码流 —— 这种“假 WAV”手机解不了，" +
                        "放出来就是沙沙声。$description",
                )
            }
            if (startsWithAc3(dataHead)) {
                return Verdict.Suspicious(
                    "文件头写着 PCM，但音频数据是 AC-3 码流，手机解不了。$description",
                )
            }
        }

        if (f.tag != WAVE_FORMAT_PCM && f.tag != WAVE_FORMAT_IEEE_FLOAT &&
            f.tag != WAVE_FORMAT_EXTENSIBLE
        ) {
            return Verdict.Suspicious("未知的 WAV 格式码 0x${"%04X".format(f.tag)}。$description")
        }

        // 3) 长度对不上：文件被截断或还在下载
        if (dataSize > 0 && dataOffset > 0) {
            val declaredEnd = dataOffset + dataSize
            if (declaredEnd > 64 * 1024 && declaredEnd < dataOffset) {
                return Verdict.Broken("data 段长度算出来是负数，文件头有问题。$description")
            }
        }

        return Verdict.Ok("正常 PCM。$description")
    }

    private data class Fmt(
        val tag: Int,
        val channels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int,
        val validBits: Int,
        val subFormat: String,
    )

    private fun containsDtsSync(bytes: ByteArray): Boolean {
        // 只在开头 64 字节里找：真 PCM 的低位字节不会凑巧连成这个序列，
        // 而 DTS 的同步字每帧开头都会重复出现
        for (sync in DTS_SYNC) {
            for (i in 0..(bytes.size - sync.size)) {
                var hit = true
                for (k in sync.indices) {
                    if (bytes[i + k] != sync[k]) {
                        hit = false
                        break
                    }
                }
                if (hit) return true
            }
        }
        return false
    }

    private fun startsWithAc3(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == AC3_SYNC[0] && bytes[1] == AC3_SYNC[1]

    private fun ascii(bytes: ByteArray, offset: Int, length: Int): String {
        if (offset + length > bytes.size) return ""
        return String(bytes, offset, length, Charsets.US_ASCII)
    }

    /** 十六进制 + 可打印字符，比如 `49 44 33 04（ID3.）`。 */
    private fun preview(bytes: ByteArray, offset: Int, length: Int): String {
        if (offset + length > bytes.size) return ""
        val hex = hex(bytes, offset, length)
        val text = (offset until offset + length).joinToString("") {
            val value = bytes[it].toInt() and 0xFF
            if (value in 32..126) value.toChar().toString() else "."
        }
        return "$hex（$text）"
    }

    private fun hex(bytes: ByteArray, offset: Int, length: Int): String {
        if (offset + length > bytes.size) return ""
        return (offset until offset + length).joinToString(" ") {
            "%02X".format(bytes[it])
        }
    }

    private fun readShortLe(bytes: ByteArray, offset: Int): Int {
        if (offset + 2 > bytes.size) return -1
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readIntLe(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return -1
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }
}
