package com.shiyin.player.data.lyrics

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * TTML 歌词解析（Apple Music / AMLL 那一套格式）。
 *
 * 这是目前唯一被广泛使用的、真正带**逐字**时间轴的开放歌词格式：
 *
 * ```xml
 * <tt><body><div>
 *   <p begin="00:12.500" end="00:15.200">
 *     <span begin="00:12.500" end="00:12.900">今</span>
 *     <span begin="00:12.900" end="00:13.400">天</span>
 *   </p>
 *   <p begin="00:12.500" end="00:15.200" ttm:role="x-translation">Today</p>
 * </div></body></tt>
 * ```
 *
 * 约定不统一是这个格式最大的坑，这里按「宽进」处理：
 * - 时间支持 `HH:MM:SS.mmm`、`MM:SS.mmm`、`12.5s`、纯秒数；
 * - `ttm:role` 只认出现 `translation` / `roman` 关键字，其余一律当正文；
 * - 译文段落出现时间可能在前也可能在后，按时间就近挂到原文行上；
 * - 缺 `end` 的 span 按下一个 span 的 `begin` 补，最后一个按段落 `end` 补。
 *
 * 只读结构不读样式（`tts:*`），播放器用不上字体颜色那些。
 */
object TtmlParser {

    fun parse(raw: String): List<LyricLine> {
        if (raw.isBlank()) return emptyList()
        // BOM 和开头的空白必须先清掉：XML 声明 `<?xml ...?>` 只允许出现在文件最开头，
        // 前面多一个换行或 BOM 就会被判定为「文档序言里有内容」，整份歌词直接解不出来。
        // 下载回来的歌词文件带 BOM 是常事。
        val text = raw.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        val doc = runCatching {
            // 禁 DTD / 外部实体：歌词文件来自网络，不能让它去解析外部资源
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
            factory.newDocumentBuilder().parse(InputSource(StringReader(text)))
        }.getOrNull() ?: return emptyList()

        val root = doc.documentElement ?: return emptyList()
        if (localName(root) != "tt") return emptyList()

        val paragraphs = ArrayList<Element>()
        collect(root, "p", paragraphs)
        if (paragraphs.isEmpty()) return emptyList()

        val lines = ArrayList<LyricLine>()
        // 先记下没挂上主人的译文/音译，等所有正文行都建好后再按时间就近挂
        val orphans = ArrayList<Pair<Long, Pair<String, String>>>() // time → (kind, text)

        for (p in paragraphs) {
            val begin = parseTime(attr(p, "begin"), -1L)
            if (begin < 0L) continue
            val end = parseTime(attr(p, "end"), -1L)
            val role = attr(p, "role").lowercase()

            val (text, words, sub) = readParagraph(p, begin, end)

            when {
                role.contains("translation") -> {
                    val value = (text.ifBlank { sub["translation"].orEmpty() }).trim()
                    if (value.isNotEmpty()) orphans += begin to ("translation" to value)
                }
                role.contains("roman") || role.contains("translit") -> {
                    val value = text.trim()
                    if (value.isNotEmpty()) orphans += begin to ("roman" to value)
                }
                text.isBlank() -> Unit
                else -> lines += LyricLine(
                    timeMs = begin,
                    text = text,
                    words = words,
                    translation = sub["translation"]?.trim()?.ifBlank { null },
                    roman = sub["roman"]?.trim()?.ifBlank { null },
                    endMs = end,
                )
            }
        }

        for ((time, pair) in orphans) {
            val (kind, value) = pair
            val target = lines.minByOrNull {
                val gap = kotlin.math.abs(it.timeMs - time)
                // 超过 2 秒就不认为是同一句了
                if (gap > 2_000L) Long.MAX_VALUE else gap
            } ?: continue
            val index = lines.indexOf(target)
            if (index < 0) continue
            val line = lines[index]
            lines[index] = if (kind == "translation") {
                if (line.translation == null) line.copy(translation = value) else line
            } else {
                if (line.roman == null) line.copy(roman = value) else line
            }
        }

        return lines.sortedBy { it.timeMs }
    }

    /**
     * 读一个 `<p>`：正文字、逐字时间、以及段内的译文/音译。
     *
     * 按文档顺序走一遍子节点，因为 `<br/>` 的语义依赖位置（它表示「这里断开」）。
     * 段内 span 没有时间的按「整段均分」补 —— 这比丢掉强，至少逐字高亮还能跑，
     * 只是节奏不准。
     */
    private fun readParagraph(
        p: Element,
        begin: Long,
        end: Long,
    ): Triple<String, List<LyricWord>, Map<String, String>> {
        val spans = ArrayList<Triple<Long, Long, String>>()   // start, stop, text
        val sub = HashMap<String, String>()
        val directText = StringBuilder()
        var breakPending = false

        fun walk(node: Node) {
            forEachChild(node) { child ->
                when {
                    child.nodeType == Node.TEXT_NODE -> directText.append(child.nodeValue.orEmpty())
                    localName(child) == "br" -> {
                        directText.append(' ')
                        breakPending = true
                    }
                    child is Element && localName(child) == "span" -> {
                        val role = attr(child, "role").lowercase()
                        // 最外层的 span 才算一个词，里面的嵌套 span 只贡献文字
                        val value = spanText(child)
                        when {
                            role.contains("translation") ->
                                sub["translation"] = (sub["translation"].orEmpty() + value).trim()
                            role.contains("roman") || role.contains("translit") ->
                                sub["roman"] = (sub["roman"].orEmpty() + value).trim()
                            else -> {
                                val text = if (breakPending && value.isNotEmpty()) " $value" else value
                                breakPending = false
                                spans += Triple(
                                    parseTime(attr(child, "begin"), -1L),
                                    parseTime(attr(child, "end"), -1L),
                                    text,
                                )
                            }
                        }
                    }
                    else -> walk(child)
                }
            }
        }
        walk(p)

        if (spans.isEmpty()) {
            val text = cleanText(directText.toString().ifBlank { spanText(p) })
            return Triple(text, emptyList(), sub)
        }

        // 补时间：没有 begin 的按「夹在已知时间之间均分」处理
        val total = (if (end > begin) end else begin + 3_000L) - begin
        val step = total / spans.size
        val resolved = ArrayList<LyricWord>(spans.size)
        spans.forEachIndexed { index, (start, stop, value) ->
            val s = if (start >= 0L) start else begin + step * index
            val e = when {
                stop >= 0L -> stop
                index + 1 < spans.size -> spans.getOrNull(index + 1)?.first?.takeIf { it >= 0L } ?: (s + step)
                else -> if (end > s) end else s + step
            }
            resolved += LyricWord(s, value)
        }

        val words = trimWords(resolved)
        val text = words.joinToString("") { it.text }.ifBlank { cleanText(directText.toString()) }
        return Triple(text, words, sub)
    }

    /** 元素下所有文字，`<br/>` 当空格。 */
    private fun spanText(element: Element): String {
        val sb = StringBuilder()
        fun walk(node: Node) {
            when {
                node.nodeType == Node.TEXT_NODE -> sb.append(node.nodeValue.orEmpty())
                localName(node) == "br" -> sb.append(' ')
                else -> forEachChild(node) { walk(it) }
            }
        }
        forEachChild(element) { walk(it) }
        return cleanText(sb.toString())
    }

    private fun cleanText(raw: String): String =
        raw.replace('\u00A0', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()

    /**
     * 解析时间轴。返回 -1 表示解析不了。
     *
     * 支持：`00:00:12.500`、`00:12.500`、`12.5s`、`12.5`、`1500ms`。
     */
    private fun parseTime(raw: String, fallback: Long): Long {
        val text = raw.trim()
        if (text.isEmpty()) return fallback
        if (text.endsWith("ms", ignoreCase = true)) {
            return text.dropLast(2).trim().toDoubleOrNull()?.toLong() ?: fallback
        }
        if (text.endsWith("s", ignoreCase = true)) {
            return text.dropLast(1).trim().toDoubleOrNull()?.let { (it * 1000).toLong() } ?: fallback
        }
        if (!text.contains(':')) {
            return text.toDoubleOrNull()?.let { (it * 1000).toLong() } ?: fallback
        }

        val parts = text.split(':')
        if (parts.size < 2) return fallback
        // 时:分:秒:帧 的写法把最后一节当帧码，按 30fps 折算 —— 猜的，但比丢掉整行好
        val framesSec = if (parts.size >= 4) {
            val fps = 30.0
            (parts.last().toDoubleOrNull() ?: 0.0) / fps
        } else {
            0.0
        }
        val secondsPart = when (parts.size) {
            2 -> parts[1]
            3 -> parts[2]
            else -> parts[2]
        }
        val hours = if (parts.size >= 3) parts[0].toLongOrNull() ?: 0L else 0L
        val minutes = if (parts.size >= 3) parts[1].toLongOrNull() ?: 0L else parts[0].toLongOrNull() ?: 0L
        val seconds = secondsPart.toDoubleOrNull() ?: return fallback
        return hours * 3_600_000 + minutes * 60_000 + ((seconds + framesSec) * 1000).toLong()
    }

    private fun attr(element: Element, name: String): String {
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val node = attrs.item(i)
            if (localName(node) == name) return node.nodeValue.orEmpty()
        }
        return ""
    }

    private fun localName(node: Node): String {
        val local = node.localName
        if (!local.isNullOrBlank()) return local
        val name = node.nodeName.orEmpty()
        return if (name.contains(':')) name.substringAfterLast(':') else name
    }

    private fun forEachChild(node: Node, action: (Node) -> Unit) {
        val children = node.childNodes ?: return
        for (i in 0 until children.length) action(children.item(i))
    }

    private fun collect(root: Node, name: String, out: MutableList<Element>) {
        forEachChild(root) { node ->
            if (node is Element && localName(node) == name) out += node
            collect(node, name, out)
        }
    }
}
