package com.shiyin.player.data.lyrics

import kotlinx.serialization.Serializable

/**
 * 一个字（或一个词）的时间。
 *
 * 逐字歌词的意义不只是「好看」：中文歌一句里换气、拖腔的位置全靠这个，
 * 只有整行时间的话，一句 4 秒的歌词你没法知道唱到第几个字。
 */
@Serializable
data class LyricWord(val startMs: Long, val text: String)

/**
 * 一行歌词。
 *
 * - `timeMs < 0`：这行没有时间轴（纯文本歌词），只做静态展示。
 * - `words` 非空：这一行有逐字时间轴，界面会做逐字填充。
 * - `translation` / `roman`：第二语言（翻译）和音译，显示在原文下方。
 */
@Serializable
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val translation: String? = null,
    val roman: String? = null,
    /** 结束时间，-1 表示未知（最后一行、或普通 LRC 没有这个信息）。 */
    val endMs: Long = -1L,
) {
    val hasWordTiming: Boolean get() = words.isNotEmpty()

    /**
     * 把逐字时间轴摊到「每个字符」上，长度与 [text] 严格一致。
     *
     * 一个 word 里可能有好几个字（`<0.5>今天` 就是两个字共用一个起唱时间），
     * 中间没有更细的信息，按词时长均分——比整词一起亮要细腻得多。
     * 单词时长为 0（同一时间连发）时给 400ms 兜底，否则除零。
     */
    fun charStartTimes(): List<Long> {
        if (words.isEmpty()) return List(text.length) { timeMs }
        val out = ArrayList<Long>(text.length)
        words.forEachIndexed { index, word ->
            val next = words.getOrNull(index + 1)?.startMs
            val span = ((next ?: endMs.coerceAtLeast(word.startMs + 400)) - word.startMs)
                .coerceAtLeast(word.text.length.toLong())
            for (c in word.text.indices) {
                out += word.startMs + span * c / word.text.length
            }
        }
        return out
    }
}

@Serializable
data class Lyrics(
    val lines: List<LyricLine> = emptyList(),
    /** 是否带时间轴，决定界面是否跟随播放滚动。 */
    val synced: Boolean = false,
    /** 来源说明，显示在歌词页角落。 */
    val source: String = "",
    /** 纯音乐，没有歌词。 */
    val instrumental: Boolean = false,
) {
    val isEmpty: Boolean get() = lines.isEmpty()

    /** 整首歌词有没有逐字时间轴，界面据此决定是否启动逐帧刷新。 */
    val hasWordTiming: Boolean get() = lines.any { it.hasWordTiming }
}

/**
 * 时间标签 → 毫秒。
 *
 * LRC 的毫秒位可以是一位、两位或三位（`.5` / `.50` / `.500`），
 * 一律当「小数秒」补到三位，否则 `.5` 会被算成 5 毫秒。
 */
internal fun timeTagToMs(minutes: String, seconds: String, fraction: String): Long? {
    val m = minutes.toLongOrNull() ?: return null
    val s = seconds.toLongOrNull() ?: return null
    val ms = when (fraction.length) {
        0 -> 0L
        1 -> fraction.toLong() * 100
        2 -> fraction.toLong() * 10
        else -> fraction.take(3).toLong()
    }
    return m * 60_000 + s * 1_000 + ms
}

/**
 * 裁掉词表首尾的空白字符，但不动中间的任何一个字符。
 *
 * 逐字渲染靠「第 n 个字符 ↔ 第 n 个时间」对齐，中间少一个空格就会整体错位，
 * 所以这里只允许裁开头和结尾。
 */
internal fun trimWords(words: List<LyricWord>): List<LyricWord> {
    if (words.isEmpty()) return emptyList()
    val out = words.toMutableList()
    out[0] = out[0].copy(text = out[0].text.trimStart())
    val last = out.lastIndex
    out[last] = out[last].copy(text = out[last].text.trimEnd())
    return out.filter { it.text.isNotEmpty() }
}

/**
 * LRC 解析。
 *
 * 支持三种写法，它们经常混在同一个文件里：
 * 1. 标准 LRC：`[00:12.00] 一句歌词`
 * 2. 一行多时间标签：`[00:12.00][01:20.00] 副歌`
 * 3. **增强 LRC（逐字）**：`[00:12.00]<00:12.00>逐<00:12.30>字<00:12.60>歌`
 *
 * 第 3 种以前会被当成正文原样显示（界面上出现一堆 `<00:12.30>`），
 * 现在解析成 [LyricWord]，界面才能做逐字高亮。
 *
 * 另外会合并双语歌词：两句时间戳完全相同、一前一后，多是一原文一翻译，
 * 合成一行显示（译文压在原文下面），而不是当成两句各占一行。
 */
object LrcParser {

    private val TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val INLINE = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")

    /** 两句时间戳差在这个范围内且紧挨着，就当成「原文 + 译文」。 */
    private const val BILINGUAL_TOLERANCE_MS = 30L

    fun parse(raw: String): List<LyricLine> {
        if (raw.isBlank()) return emptyList()
        val out = ArrayList<LyricLine>()

        for (line in raw.lineSequence()) {
            val tags = TAG.findAll(line).toList()
            if (tags.isEmpty()) continue

            val body = line.substring(tags.last().range.last + 1)
            val (text, words) = splitWords(body)

            for ((tagIndex, tag) in tags.withIndex()) {
                val timeMs = timeTagToMs(
                    tag.groupValues[1], tag.groupValues[2], tag.groupValues[3],
                ) ?: continue
                // 增强 LRC 一般只有行首一个时间标签。万一有多个，逐字时间没法平移，
                // 只在第一个标签上保留 words，其余的退化成整行时间。
                val lineWords = if (tagIndex == 0) words else emptyList()
                val display = if (lineWords.isEmpty()) text else lineWords.joinToString("") { it.text }
                if (display.isEmpty()) continue
                out += LyricLine(timeMs = timeMs, text = display, words = lineWords)
            }
        }

        val unique = out.distinctBy { it.timeMs to it.text }.sortedBy { it.timeMs }
        return mergeBilingual(unique)
    }

    /**
     * 把「<时间>字<时间>字」拆成词表。
     *
     * 第一个时间标签之前的文字没有独立时间，挂到 0、由调用方按行首时间修正；
     * 标签之后的文字一直延续到下一个标签，中间的空格原样保留（英文歌词靠它分词）。
     */
    private fun splitWords(body: String): Pair<String, List<LyricWord>> {
        val marks = INLINE.findAll(body).toList()
        if (marks.isEmpty()) return body.trim() to emptyList()

        val raw = ArrayList<LyricWord>()
        val head = body.substring(0, marks.first().range.first)
        if (head.isNotBlank()) {
            raw += LyricWord(0L, head.trimStart())
        }
        for ((index, mark) in marks.withIndex()) {
            val start = timeTagToMs(mark.groupValues[1], mark.groupValues[2], mark.groupValues[3]) ?: continue
            val end = if (index + 1 < marks.size) marks[index + 1].range.first else body.length
            val segment = body.substring(mark.range.last + 1, end)
            if (segment.isEmpty()) continue
            raw += LyricWord(start, segment)
        }
        if (raw.isEmpty()) return "" to emptyList()

        // 只裁掉整行首尾的空白：中间的字符一个都不能少，否则逐字时间和文字会错位
        val words = trimWords(raw)
        if (words.isEmpty()) return "" to emptyList()
        return words.joinToString("") { it.text } to words
    }

    /**
     * 合并双语行：相邻两行时间几乎相同、其中一行还没译文 → 后者当译文。
     *
     * 逐字行不参与合并：增强 LRC 里同一时间出现两行，更可能是合唱叠句，
     * 合成一行会把逐字时间轴弄乱。
     */
    private fun mergeBilingual(lines: List<LyricLine>): List<LyricLine> {
        val out = ArrayList<LyricLine>(lines.size)
        for (line in lines) {
            val prev = out.lastOrNull()
            val mergeable = prev != null &&
                prev.translation == null &&
                prev.words.isEmpty() &&
                line.words.isEmpty() &&
                line.text != prev.text &&
                line.timeMs - prev.timeMs <= BILINGUAL_TOLERANCE_MS
            if (mergeable) {
                out[out.lastIndex] = prev!!.copy(translation = line.text)
            } else {
                out += line
            }
        }
        return out
    }

    /** 纯文本歌词：没有时间轴，每行 timeMs = -1。 */
    fun plain(raw: String): List<LyricLine> =
        raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { LyricLine(-1L, it) }
            .toList()
}

/**
 * 歌词文本的统一入口：先看是不是 TTML，再按 LRC 解析。
 *
 * 两种格式经常同目录并存（`.ttml` 带逐字、`.lrc` 只有整行），
 * 所以靠内容判断而不是扩展名 —— 用户把 TTML 存成 `.lrc` 也不会解析失败。
 */
object LyricsText {

    /** TTML 必须出现 `<tt`；HTML 错误页里也可能有它，所以先排除 html。 */
    fun looksLikeTtml(raw: String): Boolean {
        val head = raw.take(2048)
        if (head.contains("<html", ignoreCase = true) ||
            head.contains("<!DOCTYPE html", ignoreCase = true)
        ) {
            return false
        }
        return head.contains("<tt", ignoreCase = true)
    }

    fun parse(raw: String): List<LyricLine> =
        if (looksLikeTtml(raw)) TtmlParser.parse(raw) else LrcParser.parse(raw)
}
