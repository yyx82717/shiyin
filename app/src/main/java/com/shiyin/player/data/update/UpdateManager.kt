package com.shiyin.player.data.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.shiyin.player.data.model.UpdateInfo
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * 负责「把新版 apk 下下来、校验、拉起系统安装器」。
 *
 * 为什么用系统 [DownloadManager] 而不是自己用 OkHttp 写文件：
 * 1. 它有系统级进度通知、断点续传、网络切换处理，自己写一遍全是坑；
 * 2. 下载落点是应用私有外部目录（`Android/data/.../files/Download`），不给存储权限也能写；
 * 3. 私有目录配 FileProvider 暴露给安装器，安装完系统会自动清理关联，比扔公共 Download 干净。
 *
 * 这个 object 是无状态的：下载 id 和目标文件交给调用方（ViewModel）持有。
 */
object UpdateManager {

    /** 下载进度/结果。ViewModel 轮询 [query] 拿到它来驱动 UI。 */
    sealed class DownloadState {
        /** 还没开始或 id 无效。 */
        data object Idle : DownloadState()

        /** 下载中。 */
        data class Running(val progress: Int, val bytesSoFar: Long, val totalBytes: Long) : DownloadState()

        /** 下载并落盘成功，带上文件供后续校验/安装。 */
        data class Success(val file: File) : DownloadState()

        /** 失败，附原因（断网、404、存储不足等）。 */
        data class Failed(val reason: String) : DownloadState()
    }

    /** 安装结果。 */
    sealed class InstallResult {
        /** 已经把系统安装器拉起来了，后面交给用户点。 */
        data object Launched : InstallResult()

        /** 当前应用没有「安装未知应用」权限，得先引导用户去设置里开。 */
        data object NeedsPermission : InstallResult()

        /** 安装拉不起来（文件没了/没授权文件访问）。 */
        data class Failed(val reason: String) : InstallResult()
    }

    /** 目标文件：按 versionCode 分子目录，避免不同版本互相覆盖或旧包残留误装。 */
    fun apkFile(context: Context, info: UpdateInfo): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates")
        val name = "${info.versionCode}_" + info.apk.substringAfterLast('/').ifBlank { "update.apk" }
        return File(dir, name)
    }

    /**
     * 发起下载，返回 downloadId。
     *
     * 已存在同名文件先删掉（同版本重下场景），否则 DownloadManager 会直接失败。
     */
    fun downloadApk(context: Context, info: UpdateInfo, baseUrl: String): Long {
        val target = apkFile(context, info)
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()

        val url = UpdateInfo.resolveApkUrl(baseUrl, info.apk)
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("拾音 更新到 ${info.versionName}")
            setDescription("正在下载安装包")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
            // 落应用私有外部目录，无需存储权限
            setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "updates/${target.name}",
            )
        }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(request)
    }

    /** 查一次下载状态。轻量，ViewModel 里每几百毫秒轮一次即可。 */
    fun query(context: Context, downloadId: Long): DownloadState {
        if (downloadId <= 0L) return DownloadState.Idle
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
        if (cursor == null || !cursor.moveToFirst()) {
            cursor?.close()
            return DownloadState.Failed("下载任务不存在")
        }
        try {
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val localUri = cursor.getString(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI),
                    )
                    val file = localUri?.let { Uri.parse(it) }?.let { uriToFile(context, it) }
                    return if (file != null && file.exists()) {
                        DownloadState.Success(file)
                    } else {
                        DownloadState.Failed("下载完成但找不到文件")
                    }
                }
                DownloadManager.STATUS_FAILED -> {
                    val reason = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON),
                    )
                    return DownloadState.Failed(describeReason(reason))
                }
                DownloadManager.STATUS_PAUSED -> {
                    val soFar = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                    )
                    val total = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
                    )
                    return DownloadState.Running(percent(soFar, total), soFar, total)
                }
                else -> {
                    val soFar = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                    )
                    val total = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
                    )
                    return DownloadState.Running(percent(soFar, total), soFar, total)
                }
            }
        } finally {
            cursor.close()
        }
    }

    /** SHA-256 校验。expected 为空（清单没填）直接放行；填了就严格比对。 */
    fun verifySha256(file: File, expected: String?): Boolean {
        val want = expected?.trim()?.replace("\\s".toRegex(), "")?.lowercase()
        if (want.isNullOrEmpty()) return true
        if (!file.exists()) return false
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(64 * 1024)
            var read: Int
            while (fis.read(buf).also { read = it } != -1) {
                digest.update(buf, 0, read)
            }
        }
        val got = digest.digest().joinToString("") { "%02x".format(it) }
        return got == want
    }

    /**
     * 拉起系统安装器。
     *
     * Android 8+（API 26）起，非应用商店来源的安装必须先让用户给本应用授权
     * 「安装未知应用」。没授权就返回 [InstallResult.NeedsPermission]，由 UI 引导去设置。
     * 这是系统硬要求，应用自己绕不过去，所以「自动更新」只能做到「自动查 + 自动下 + 弹窗让你点装」。
     */
    fun install(context: Context, file: File): InstallResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                return InstallResult.NeedsPermission
            }
        }
        return runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            InstallResult.Launched
        }.getOrElse { InstallResult.Failed(it.message ?: "无法启动安装") }
    }

    /** 引导去「安装未知应用」授权的 Intent。仅 Android O+ 有意义。 */
    fun unknownSourcesIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    // ---------------- 内部工具 ----------------

    private fun uriToFile(context: Context, uri: Uri): File? {
        // DownloadManager 落私有目录时 COLUMN_LOCAL_URI 是 file://，直接转 File
        if (uri.scheme == "file") return uri.path?.let { File(it) }
        // 极少数情况给 content://，用 FileProvider 同套逻辑反向取路径成本高，交给调用方判 exists
        return runCatching {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?.let { File(it, "updates") }
        }.getOrNull()
    }

    private fun percent(soFar: Long, total: Long): Int {
        if (total <= 0L) return 0
        return ((soFar * 100) / total).toInt().coerceIn(0, 100)
    }

    private fun describeReason(reason: Int): String = when (reason) {
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "下载被服务器中断"
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "手机存储空间不足"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "找不到存储位置"
        DownloadManager.ERROR_CANNOT_RESUME -> "下载无法继续"
        DownloadManager.ERROR_FILE_ERROR -> "文件写入失败"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "更新源重定向次数过多"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "更新源返回了无法处理的响应"
        else -> "下载失败（代码 $reason）"
    }
}
