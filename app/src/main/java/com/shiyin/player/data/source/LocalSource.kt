package com.shiyin.player.data.source

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.core.content.ContextCompat
import com.shiyin.player.data.local.LocalLibraryStore
import com.shiyin.player.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 删除本地文件的结果：直接删掉了 / 需要用户在系统弹窗里点确认 / 失败。 */
sealed interface DeleteOutcome {
    data object Deleted : DeleteOutcome
    data class NeedConsent(val sender: IntentSender) : DeleteOutcome
    data class Failed(val reason: String) : DeleteOutcome
}

/**
 * 本地音乐库：手机存储里所有能被媒体库索引到的音频文件。
 *
 * 这是「全库」的第一层含义——你自己拥有、下载、拷贝进来的音乐，
 * 不管来自哪个平台，只要文件在手机上就能统一管理、统一播放。
 *
 * 三件事在这里一起解决：
 * 1. 扫描结果落盘（[LocalLibraryStore]），冷启动直接出列表，不用再「重新找一遍」；
 * 2. 删除原文件，Android 10+ 走系统的「是否删除」确认弹窗；
 * 3. 封面按专辑提取缩略图缓存，滚动时不重复解码。
 */
class LocalSource(
    private val context: Context,
    private val store: LocalLibraryStore = LocalLibraryStore(context),
) : MusicSource {

    override val id = "local"
    override val label = "本地音乐"
    override val note = "手机存储里的音频文件，不需要联网"

    @Volatile
    private var cache: List<Track>? = null

    @Volatile
    private var cacheAt = 0L

    /** 是否已经从磁盘快照里读过一次（哪怕读到的是空列表）。 */
    @Volatile
    private var snapshotLoaded = false

    /** 当前系统版本下读取音频需要的权限。 */
    fun requiredPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        requiredPermission(),
    ) == PackageManager.PERMISSION_GRANTED

    override suspend fun feed(genre: String?, limit: Int): List<Track> = all().take(limit)

    override suspend fun search(query: String, limit: Int): List<Track> {
        val q = Query.normalize(query)
        if (q.isBlank()) return emptyList()
        return all()
            .filter {
                Query.normalize(it.title).contains(q) ||
                    Query.normalize(it.artist).contains(q) ||
                    Query.normalize(it.album.orEmpty()).contains(q)
            }
            .take(limit)
    }

    /**
     * 关键词归一化：中文用户常用的全角字母/数字、书名号括号、
     * 以及「周杰伦 / Jay Chou」这类混排，都先统一再比较，
     * 否则搜「晴天」搜不到「晴天 (Live)」，搜「jay」搜不到「JAY」。
     */
    internal object Query {
        fun normalize(raw: String): String {
            val sb = StringBuilder(raw.length)
            for (ch in raw) {
                val c = when {
                    ch == '\u3000' -> ' '
                    ch in '！'..'～' -> (ch - 0xFEE0) // 全角 ASCII → 半角
                    else -> ch
                }
                sb.append(c)
            }
            return sb.toString()
                .lowercase()
                .replace(Regex("""[()（）\[\]【】《》<>"'“”·、,，.。!！?？:：;；_\-\s]"""), "")
                .trim()
        }
    }

    fun invalidate() {
        cache = null
        cacheAt = 0L
        snapshotLoaded = false
    }

    /** 立刻能给出的列表：内存缓存 → 磁盘快照 → 空。不会触发扫描，所以是毫秒级的。 */
    suspend fun cached(): List<Track> = withContext(Dispatchers.IO) {
        cache?.let { return@withContext it }
        if (!hasPermission()) return@withContext emptyList()

        val snapshot = store.read() ?: return@withContext emptyList()
        snapshotLoaded = true
        cache = snapshot.tracks
        cacheAt = snapshot.savedAt
        snapshot.tracks
    }

    /** 重新扫一遍媒体库并更新快照。 */
    suspend fun refresh(): List<Track> = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            cache = emptyList()
            cacheAt = System.currentTimeMillis()
            snapshotLoaded = true
            return@withContext emptyList()
        }
        val scanned = runCatching { queryMediaStore() }.getOrNull()
            ?: return@withContext (cache ?: emptyList())

        cache = scanned
        cacheAt = System.currentTimeMillis()
        snapshotLoaded = true
        store.write(scanned)
        scanned
    }

    /** 兼容 MusicSource：有快照就用快照，一首都没见过才扫。 */
    suspend fun all(force: Boolean = false): List<Track> {
        if (force) return refresh()
        val hit = cached()
        if (hit.isNotEmpty()) return hit
        // 快照存在但确实是空的（用户把歌全删了）就别反复扫；
        // 没有快照说明这台机器还没扫过，扫一次。
        if (snapshotLoaded) return emptyList()
        return refresh()
    }

    /** 播放页/歌单里删掉某一首后，把它从内存与快照里同步移除。 */
    suspend fun forget(track: Track) = withContext(Dispatchers.IO) {
        val next = (cache ?: cached()).filterNot { it.id == track.id }
        cache = next
        cacheAt = System.currentTimeMillis()
        snapshotLoaded = true
        store.write(next)
    }

    /**
     * 删除手机里的原始音频文件。
     *
     * Android 10 起，媒体库里的文件不归应用所有就只能走系统确认：
     * 30+ 用 MediaStore.createDeleteRequest，29 用 RecoverableSecurityException 里的 IntentSender。
     * 29 以下（含已授权 WRITE_EXTERNAL_STORAGE 的情况）可以直接删。
     */
    suspend fun delete(track: Track): DeleteOutcome = withContext(Dispatchers.IO) {
        val uri = mediaUri(track) ?: return@withContext DeleteOutcome.Failed("这首歌不在系统媒体库里")
        if (!hasPermission()) return@withContext DeleteOutcome.Failed("需要先授权读取音频文件")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val sender = runCatching {
                MediaStore.createDeleteRequest(context.contentResolver, listOf(uri)).intentSender
            }.getOrNull()
            return@withContext if (sender != null) {
                DeleteOutcome.NeedConsent(sender)
            } else {
                DeleteOutcome.Failed("系统没有提供删除入口")
            }
        }

        try {
            val rows = context.contentResolver.delete(uri, null, null)
            if (rows > 0) {
                forget(track)
                DeleteOutcome.Deleted
            } else {
                DeleteOutcome.Failed("文件没有被删除，可能已经不存在了")
            }
        } catch (e: SecurityException) {
            val sender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                (e as? RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
            } else {
                null
            }
            if (sender != null) DeleteOutcome.NeedConsent(sender) else DeleteOutcome.Failed("系统拒绝删除该文件")
        }
    }

    /** 系统确认弹窗点「允许」之后，媒体库条目已经没了，本地列表同步刷新。 */
    suspend fun onDeleted(track: Track) = forget(track)

    private fun mediaUri(track: Track): Uri? {
        if (!track.id.startsWith("local:")) return null
        val songId = track.id.removePrefix("local:").toLongOrNull() ?: return null
        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
    }

    @Suppress("DEPRECATION")
    private fun queryMediaStore(): List<Track> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            // 只为「文件夹」视图取所在目录。从 API 1 就存在，旧机器也能用
            MediaStore.Audio.Media.DATA,
            // 音质标注：容器 + 码率，这两列是本地库比网盘强的地方
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.BITRATE,
        )
        // IS_MUSIC 过滤掉铃声/提示音；15 秒以下基本是音效，不进曲库
        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 15000"
        val order = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        val result = ArrayList<Track>()
        val artCache = HashMap<Long, String?>()

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            order,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val mimeIndex = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
            val bitrateIndex = cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE)

            while (cursor.moveToNext() && result.size < MAX_TRACKS) {
                val songId = cursor.getLong(idIndex)
                val albumId = cursor.getLong(albumIdIndex)
                val artKey = if (albumId > 0) albumId else -songId

                val artwork = if (artCache.containsKey(artKey)) {
                    artCache[artKey]
                } else {
                    val art = runCatching { artworkFor(songId, artKey) }.getOrNull()
                    artCache[artKey] = art
                    art
                }

                result += Track(
                    id = "local:$songId",
                    title = cursor.getString(titleIndex)?.trim().orEmpty().ifBlank { "未命名曲目" },
                    artist = cursor.getString(artistIndex)
                        ?.trim()
                        ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                        ?: "未知艺人",
                    album = cursor.getString(albumIndex)?.trim()?.takeIf { it.isNotBlank() },
                    artwork = artwork,
                    streamUrl = ContentUris
                        .withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
                        .toString(),
                    durationSec = cursor.getLong(durationIndex) / 1000L,
                    source = id,
                    folder = if (dataIndex >= 0) {
                        cursor.getString(dataIndex)
                            ?.substringBeforeLast('/', "")
                            ?.ifBlank { null }
                    } else {
                        null
                    },
                    format = if (mimeIndex >= 0) formatFromMime(cursor.getString(mimeIndex)) else null,
                    // MediaStore 的 BITRATE 单位就是 bit/s，转成 kbps
                    bitrateKbps = if (bitrateIndex >= 0) cursor.getInt(bitrateIndex) / 1000 else 0,
                )
            }
        }
        return result
    }

    /** 按专辑提取封面缩略图并落盘缓存，返回 file:// 地址给 Coil 直接用。 */
    private fun artworkFor(songId: Long, artKey: Long): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        val dir = File(context.cacheDir, "local_art").apply { mkdirs() }
        val file = File(dir, "album_$artKey.jpg")
        if (file.exists() && file.length() > 0L) return Uri.fromFile(file).toString()

        val mediaUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
        val bitmap: Bitmap = runCatching {
            context.contentResolver.loadThumbnail(mediaUri, Size(512, 512), null)
        }.getOrNull() ?: return null

        val ok = runCatching {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        }.isSuccess

        return if (ok && file.length() > 0L) Uri.fromFile(file).toString() else null
    }

    /** MIME 里只有 `audio/mpeg` 这种粗粒度信息，映射成人们在文件管理器里看到的扩展名。 */
    private fun formatFromMime(mime: String?): String? {
        val value = mime?.trim()?.lowercase().orEmpty()
        if (value.isEmpty()) return null
        return when {
            value.contains("flac") -> "flac"
            value.contains("mpeg") -> "mp3"
            value.contains("mp4") || value.contains("m4a") -> "m4a"
            value.contains("x-wav") || value.contains("wav") -> "wav"
            value.contains("opus") -> "opus"
            value.contains("ogg") -> "ogg"
            value.contains("aac") -> "aac"
            value.contains("ape") -> "ape"
            value.contains("dsd") || value.contains("dsf") -> "dsf"
            else -> value.substringAfterLast('/').ifBlank { null }
        }
    }

    private companion object {
        const val MAX_TRACKS = 5000
    }
}
