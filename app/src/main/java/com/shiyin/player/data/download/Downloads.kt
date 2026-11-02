package com.shiyin.player.data.download

import com.shiyin.player.data.model.DownloadEntry
import com.shiyin.player.data.model.Track
import java.io.File

/**
 * 已下载曲目的索引，主键是 track.id → 本地文件路径。
 *
 * 由 ShiYinApp 订阅 DataStore 里的下载列表来刷新。
 * 播放前用它把网络地址换成 file:// 地址，这样断网也能放。
 */
object Downloads {

    @Volatile
    private var index: Map<String, String> = emptyMap()

    fun update(entries: List<DownloadEntry>) {
        index = entries.associate { it.track.id to it.filePath }
    }

    fun localPath(trackId: String): String? = index[trackId]

    fun isDownloaded(trackId: String): Boolean = index.containsKey(trackId)

    /** 有本地副本就换成文件地址，否则原样返回。 */
    fun apply(track: Track): Track {
        val path = index[track.id] ?: return track
        val file = File(path)
        if (!file.isFile) return track
        return track.copy(streamUrl = fileUri(path))
    }

    fun totalSize(entries: List<DownloadEntry>): Long = entries.sumOf { it.sizeBytes }
}

/**
 * 本地文件路径 → `file://` 地址。
 *
 * 不用 `Uri.fromFile` 是为了让这段能在 JVM 单元测试里验证：ExoPlayer 只认
 * `file:///...` 这种三斜杠形式，写成 `file://C:/...` 会被当成「主机名是 C」而打不开。
 */
internal fun fileUri(path: String): String =
    if (path.startsWith("/")) "file://$path" else "file:///$path"

/**
 * 从下载地址推断文件后缀。
 *
 * 音源给的地址经常带查询串（`.../track.mp3?app_name=shiyin`）或干脆没有后缀
 * （Audius 的 stream 接口就是 302 到签名地址，URL 里看不到扩展名），
 * 这时统一落到 `m4a` —— 安卓系统对它的识别最好，ExoPlayer 也能按容器嗅探。
 */
internal fun audioExtensionOf(url: String): String {
    val ext = url.substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('/', "")
        .substringAfterLast('.', "")
        .lowercase()
    val valid = ext.length in 1..5 && ext.all { it.isLetterOrDigit() }
    return if (valid) ext else "m4a"
}
