package com.shiyin.player.data.download

import android.content.Context
import com.shiyin.player.ShiYinApp
import com.shiyin.player.data.local.SettingsStore
import com.shiyin.player.data.model.DownloadEntry
import com.shiyin.player.data.model.Track
import com.shiyin.player.data.remote.Http
import com.shiyin.player.data.remote.RemoteAuth
import com.shiyin.player.data.remote.md5Hex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 离线下载器。
 *
 * 文件落到应用私有目录（filesDir/downloads），不需要存储权限，卸载即清理。
 * 下载协程挂在 Application 级别的 scope 上，切页面不会中断；
 * 进程被系统杀掉则会停，重新进入 App 后需要重新点下载（曲库里的文件不会重复下）。
 */
class Downloader(
    private val context: Context,
    private val store: SettingsStore,
) {

    enum class Phase { RUNNING, DONE, FAILED, CANCELED }

    data class Progress(
        val phase: Phase,
        val bytes: Long = 0L,
        val total: Long = -1L,
        val error: String? = null,
    ) {
        val fraction: Float
            get() = if (total > 0L) (bytes.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    }

    private val _progress = MutableStateFlow<Map<String, Progress>>(emptyMap())
    val progress: StateFlow<Map<String, Progress>> = _progress

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()

    fun start(track: Track) {
        // 本地文件没有下载的意义；已在下载队列或已下载则忽略
        if (track.source == "local") return
        if (Downloads.isDownloaded(track.id)) return
        if (jobs.containsKey(track.id)) return

        val job = scope.launch { run(track) }
        jobs[track.id] = job
        job.invokeOnCompletion { jobs.remove(track.id) }
    }

    fun cancel(trackId: String) {
        jobs[trackId]?.cancel()
    }

    fun delete(track: Track) {
        jobs[track.id]?.cancel()
        scope.launch {
            store.downloadsNow().firstOrNull { it.track.id == track.id }?.let { entry ->
                runCatching { File(entry.filePath).delete() }
            }
            store.removeDownload(track.id)
            Downloads.update(store.downloadsNow())
            _progress.update { it - track.id }
        }
    }

    /** 重试失败的下载：先清掉失败状态再入队。 */
    fun retry(track: Track) {
        _progress.update { it - track.id }
        start(track)
    }

    private suspend fun run(track: Track) {
        set(track.id, Progress(Phase.RUNNING))
        val tmp = File(dir(), md5Hex(track.id) + ".part")

        try {
            val file = fetch(track, tmp)
            store.addDownload(
                DownloadEntry(
                    track = track,
                    filePath = file.absolutePath,
                    sizeBytes = file.length(),
                    at = System.currentTimeMillis(),
                ),
            )
            // 立刻刷新索引，避免刚下完点播放还走网络
            Downloads.update(store.downloadsNow())
            set(track.id, Progress(Phase.DONE))
        } catch (cancelled: CancellationException) {
            runCatching { tmp.delete() }
            set(track.id, Progress(Phase.CANCELED))
            throw cancelled
        } catch (t: Throwable) {
            runCatching { tmp.delete() }
            set(track.id, Progress(Phase.FAILED, error = t.message ?: "下载失败"))
        }
    }

    private suspend fun fetch(track: Track, tmp: File): File {
        // 百度网盘存的是 baidu://fs_id/路径 这种「待解析」地址，直链 8 小时就过期，
        // 所以下载前必须现换一次；换不到就按原地址走，让下面的失败提示说清楚原因。
        val playUrl = if (track.streamUrl.startsWith("baidu://")) {
            runCatching { ShiYinApp.instance.repository.resolvePlayableUrl(track) }
                .getOrDefault(track.streamUrl)
        } else {
            track.streamUrl
        }
        if (playUrl.startsWith("baidu://")) error("百度网盘地址没换成直链，检查是否已授权")

        val builder = Request.Builder()
            .url(playUrl)
            .header("User-Agent", Http.USER_AGENT)
        // 网盘（WebDAV）的直链要 Basic 认证。表里只登记了用户自己的服务器，
        // 所以只有命中主机时才加头，不会把凭据发给第三方音源。
        RemoteAuth.headerFor(playUrl.toHttpUrlOrNull()?.host)?.let {
            builder.header("Authorization", it)
        }
        val request = builder.build()

        val dest = File(dir(), md5Hex(track.id) + "." + audioExtensionOf(playUrl))

        Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("服务器返回 ${response.code}")
            val body = response.body ?: error("响应为空")
            val total = body.contentLength()
            var done = 0L

            body.byteStream().use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        done += read
                        set(track.id, Progress(Phase.RUNNING, done, total))
                    }
                }
            }
        }

        if (tmp.length() <= 0L) error("下载到的文件是空的")
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
        return dest
    }

    private fun dir(): File = File(context.filesDir, "downloads").apply { mkdirs() }

    private fun set(trackId: String, progress: Progress) {
        _progress.update { it + (trackId to progress) }
    }
}
