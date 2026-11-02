package com.shiyin.player.data.local

import android.content.Context
import com.shiyin.player.data.model.AppJson
import com.shiyin.player.data.model.Track
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class LocalSnapshot(
    val savedAt: Long = 0L,
    val tracks: List<Track> = emptyList(),
)

/**
 * 曲库快照（本地媒体库 + 网盘目录共用）。
 *
 * 之前每次冷启动进「本地」都是空白 + 重新扫描，用户感受就是「明明扫过了，重开又要找一遍」。
 * 现在把扫描结果落盘：启动时先秒出上一次的结果，再在后台静默补扫一次。
 * 网盘那边更明显 —— WebDAV 遍历一次要十几秒，只放内存缓存等于每次重启都要重爬一遍。
 *
 * 用文件而不是 DataStore 存：几千首歌的 JSON 有 1MB 上下，
 * Preferences 存大字符串要整份读改写，反而更慢，也更容易把首选项写坏。
 */
class LocalLibraryStore(
    private val context: Context,
    private val fileName: String = SNAPSHOT_FILE,
) {

    private val file: File get() = File(context.filesDir, fileName)

    fun read(): LocalSnapshot? {
        val f = file
        if (!f.isFile || f.length() == 0L) return null
        return runCatching {
            AppJson.decodeFromString(LocalSnapshot.serializer(), f.readText())
        }.getOrNull()
    }

    fun write(tracks: List<Track>) {
        runCatching {
            val snapshot = LocalSnapshot(savedAt = System.currentTimeMillis(), tracks = tracks)
            // 先写临时文件再改名，避免写一半被系统杀掉后留下半截 JSON
            val tmp = File(context.filesDir, "$fileName.tmp")
            tmp.writeText(AppJson.encodeToString(LocalSnapshot.serializer(), snapshot))
            if (!tmp.renameTo(file)) {
                file.writeText(AppJson.encodeToString(LocalSnapshot.serializer(), snapshot))
                tmp.delete()
            }
        }
    }

    fun clear() {
        runCatching { file.delete() }
    }

    /** 供单元测试校验序列化格式是否稳定。 */
    fun encode(snapshot: LocalSnapshot): String =
        AppJson.encodeToString(LocalSnapshot.serializer(), snapshot)

    fun decode(raw: String): LocalSnapshot? = runCatching {
        AppJson.decodeFromString(LocalSnapshot.serializer(), raw)
    }.getOrNull()

    companion object {
        /** 手机本地媒体库。 */
        const val LOCAL_FILE = "local_library.json"

        /** 网盘（WebDAV）目录遍历结果。 */
        const val WEBDAV_FILE = "webdav_library.json"

        /** 百度网盘直连的目录遍历结果。 */
        const val BAIDU_FILE = "baidu_library.json"

        private const val SNAPSHOT_FILE = LOCAL_FILE
    }
}
