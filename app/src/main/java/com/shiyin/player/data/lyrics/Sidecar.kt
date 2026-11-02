package com.shiyin.player.data.lyrics

/**
 * 网盘曲目的「旁挂歌词」地址推算。
 *
 * 酷我 / QQ 音乐下载时会把歌词写成和音频同名、同目录的 `.lrc`。
 * 本地库能直接用 `File` 找到它（见 `LyricsRepository.localSidecar`），
 * 但 WebDAV 网盘上的曲目没有文件系统，只能按 URL 换扩展名去取。
 *
 * 这里只做纯粹的字符串处理，不碰网络，方便单独测。
 */
object Sidecar {

    /** 认得出是音频的扩展名。多认几个没坏处，取不到自然会回退到在线歌词。 */
    private val AUDIO_EXT = setOf(
        "mp3", "flac", "m4a", "aac", "wav", "ogg", "opus", "ape", "wma", "alac",
    )

    /**
     * 音频 URL → 同目录同名 `.lrc` 的 URL，推不出来时返回 null。
     *
     * 会返回 null 的情况：不是 http(s)、路径里没有扩展名、扩展名不是音频、本来就是 .lrc。
     * URL 的查询串和锚点会被丢掉 —— 歌词文件不需要那些鉴权参数，带上反而可能被服务器拒。
     */
    fun lrcUrlFor(audioUrl: String): String? = urlWith(audioUrl, "lrc")

    /**
     * 依次尝试的同名歌词地址：先 `.ttml`（可能带逐字时间轴），再 `.lrc`（一般只有整行）。
     *
     * 顺序不能反：TTML 的信息是 LRC 的超集，同一首歌两份都在时没有理由用差的那份。
     */
    fun sidecarUrls(audioUrl: String): List<String> =
        listOf("ttml", "lrc").mapNotNull { urlWith(audioUrl, it) }

    private fun urlWith(audioUrl: String, extension: String): String? {
        val url = audioUrl.trim()
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }

        val cut = url.indexOfFirst { it == '?' || it == '#' }
        val path = if (cut >= 0) url.substring(0, cut) else url

        val slash = path.lastIndexOf('/')
        val dot = path.lastIndexOf('.')
        // dot <= slash+1 有两种情况：整个路径没有点；或者点在最后一个斜杠之后紧跟着（`/.hidden`）
        if (dot <= slash + 1) return null

        val ext = path.substring(dot + 1).lowercase()
        if (ext !in AUDIO_EXT) return null

        return path.substring(0, dot) + "." + extension
    }
}
