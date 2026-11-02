package com.shiyin.player.data.source

import com.shiyin.player.data.remote.md5Hex
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/**
 * WebDAV `multistatus` 响应解析。
 *
 * 单独拆出来是为了能在 JVM 单元测试里直接跑：真正的坑全在 XML 上——
 * 命名空间前缀（`D:response` / `d:href`）、`<collection/>` 判断目录、
 * 根目录 `href` 和请求地址不完全相等（末尾斜杠、URL 转义）。
 * `XmlPullParser` 由调用方传入，Android 侧传 `android.util.Xml.newPullParser()`。
 */
internal data class DavEntry(
    /** 解析后的完整地址，直接可作播放用。 */
    val href: String,
    /** 文件名（目录则是目录名）。 */
    val name: String,
    val isDir: Boolean,
) {
    val id: String get() = "webdav:" + md5Hex(href)
}

/**
 * 把 WebDAV 文件名拆成艺人 / 标题。
 *
 * `艺人 - 标题.mp3` 是最常见的命名约定（很多下载器和网易云/QQ音乐的导出都用它），
 * 没有分隔符时整串当标题，艺人留空由调用方兜底成「未知艺人」。
 */
internal fun splitDavFileName(fileName: String): Pair<String, String> {
    val baseName = fileName.substringBeforeLast('.').replace('_', ' ').trim()
    if (baseName.isBlank()) return "" to fileName
    val separator = baseName.indexOf(" - ")
    if (separator <= 0) return "" to baseName
    val artist = baseName.substring(0, separator).trim()
    val title = baseName.substring(separator + 3).trim()
    // " - xxx" 这种开头就是分隔符的，别把空艺人算进去
    if (artist.isBlank() || title.isBlank()) return "" to baseName
    return artist to title
}

internal fun isAudioFileName(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext.isNotEmpty() && ext in AUDIO_EXTENSIONS
}

internal val AUDIO_EXTENSIONS = setOf(
    "mp3", "flac", "m4a", "aac", "ogg", "oga", "opus", "wav",
    "ape", "wv", "wma", "aiff", "aif", "alac", "dsf", "dff", "mka",
)

/**
 * 解析 PROPFIND 的 `D:multistatus` 响应。
 *
 * 关闭命名空间处理，统一用 `名字里冒号之后的部分` 做匹配，
 * 这样 `<D:href>`、`<d:href>`、`<href>` 三种写法都能认。
 */
internal fun parseDavMultiStatus(
    xml: String,
    base: String,
    parser: XmlPullParser,
): List<DavEntry> {
    if (xml.isBlank()) return emptyList()

    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    parser.setInput(StringReader(xml))

    val entries = ArrayList<DavEntry>()
    var href: String? = null
    var isDir = false
    var event = parser.eventType

    while (event != XmlPullParser.END_DOCUMENT) {
        if (event == XmlPullParser.START_TAG) {
            when (parser.name.substringAfter(':').lowercase()) {
                "response" -> {
                    href = null
                    isDir = false
                }

                "href" -> href = runCatching { parser.nextText() }.getOrNull()

                // resourceType 里有 collection 子元素 ⇔ 这是个目录
                "collection" -> isDir = true
            }
        } else if (event == XmlPullParser.END_TAG &&
            parser.name.substringAfter(':').equals("response", ignoreCase = true)
        ) {
            val raw = href
            if (!raw.isNullOrBlank()) {
                resolveDavHref(base, raw)?.let { (url, name) ->
                    entries += DavEntry(url, name, isDir)
                }
            }
        }
        event = parser.next()
    }
    return entries
}

/** href 可能是相对路径（`/dav/music/a.mp3`），也可能是绝对 URL，统一用 base 解析。 */
internal fun resolveDavHref(base: String, href: String): Pair<String, String>? {
    val httpBase = base.toHttpUrlOrNull() ?: return null
    val url = httpBase.resolve(href.trim()) ?: return null
    // 目录的 href 通常以 "/" 结尾，直接取最后一段会得到空串，所以先滤掉空段。
    val name = url.pathSegments.lastOrNull { it.isNotEmpty() }.orEmpty()
    if (name.isBlank()) return null
    return url.toString() to name
}
