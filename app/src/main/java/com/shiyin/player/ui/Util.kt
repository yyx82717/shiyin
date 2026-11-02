package com.shiyin.player.ui

import com.shiyin.player.data.model.Track
import java.util.Locale

/** 毫秒 → m:ss */
fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

/** 把网络异常翻译成用户能看懂的话。 */
fun Throwable.readableMessage(): String {
    val raw = message.orEmpty()
    return when {
        raw.contains("Unable to resolve host", ignoreCase = true) ||
            raw.contains("UnknownHost", ignoreCase = true) -> "网络似乎不通，请检查连接"
        raw.contains("timeout", ignoreCase = true) -> "连接超时，请稍后重试"
        raw.contains("HTTP 5") -> "服务器开小差了，稍后再试"
        raw.startsWith("HTTP 4") -> "请求被拒绝（$raw）"
        raw.isBlank() -> "加载失败，请稍后重试"
        else -> raw
    }
}

/** 音源 id → 展示名 */
fun sourceLabel(source: String): String = when (source) {
    "audius" -> "Audius"
    "jamendo" -> "Jamendo"
    "local" -> "本地"
    "subsonic" -> "自建"
    "webdav" -> "网盘"
    "archive" -> "Internet Archive"
    else -> "自定义源"
}

/** 是否含汉字。用来做「只看华语」这种朴素筛选，不依赖任何语言标签。 */
fun String.hasCjk(): Boolean = any { it in '\u4e00'..'\u9fff' }

fun Track.isChinese(): Boolean = title.hasCjk() || artist.hasCjk() || album.orEmpty().hasCjk()
