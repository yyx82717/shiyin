package com.shiyin.player.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LRC 解析测试。
 *
 * 这些用例都来自真实的 .lrc 文件形态：元信息头、一行多时间标签、
 * 毫秒位数不一致（`.5` / `.50` / `.500`）、只有标签没有文字的空行。
 */
class LrcParserTest {

    @Test
    fun `元信息头被忽略，带时间轴的行按时间排序`() {
        val raw = """
            [ti:Yellow]
            [ar:Coldplay]
            [al:Parachutes]
            [by:someone]
            [00:12.50]Look at the stars
            [00:17.80][01:20.00]Look how they shine for you
            [00:22.10]And everything you do
        """.trimIndent()

        val lines = LrcParser.parse(raw)

        assertEquals(4, lines.size)
        assertEquals(listOf(12_500L, 17_800L, 22_100L, 80_000L), lines.map { it.timeMs })
        // 一行两个标签 → 同一句歌词出现在两个时间点
        assertEquals("Look how they shine for you", lines[1].text)
        assertEquals("Look how they shine for you", lines[3].text)
    }

    @Test
    fun `毫秒一到三位都按三位补齐`() {
        val raw = "[00:01.5]a\n[00:02.50]b\n[00:03.500]c\n[00:04]d"
        // 1.5→1500、2.50→2500、3.500→3500、4→4000：小数部分一律当毫秒补到三位
        assertEquals(
            listOf(1_500L, 2_500L, 3_500L, 4_000L),
            LrcParser.parse(raw).map { it.timeMs },
        )
    }

    @Test
    fun `用冒号分隔毫秒的写法也能解析`() {
        // 部分播放器导出的是 [00:12:50]，而不是标准的 [00:12.50]
        val lines = LrcParser.parse("[00:12:50]hello")
        assertEquals(12_500L, lines.single().timeMs)
        assertEquals("hello", lines.single().text)
    }

    @Test
    fun `没有文字的行被丢弃`() {
        val raw = "[00:09.00]\n[offset:500]\n[00:10.00]真的一句"
        val lines = LrcParser.parse(raw)
        assertEquals(1, lines.size)
        assertEquals("真的一句", lines.single().text)
    }

    @Test
    fun `重复行去重`() {
        val lines = LrcParser.parse("[00:20.00]b\n[00:10.00]a\n[00:10.00]a")
        assertEquals(2, lines.size)
        assertEquals(listOf(10_000L, 20_000L), lines.map { it.timeMs })
    }

    @Test
    fun `空内容不会崩`() {
        assertTrue(LrcParser.parse("").isEmpty())
        assertTrue(LrcParser.parse("   \n\n ").isEmpty())
        assertTrue(LrcParser.parse("\uFEFF").isEmpty())
    }

    @Test
    fun `纯文本歌词每行时间为负一`() {
        val lines = LrcParser.plain("第一行\n\n第二行\n")
        assertEquals(2, lines.size)
        assertTrue(lines.all { it.timeMs < 0 })
        assertEquals("第二行", lines[1].text)
    }

    @Test
    fun `超过一小时的曲目时间戳正确`() {
        // 现场版、混音带经常超过 60 分钟，分钟位可以到三位数
        val lines = LrcParser.parse("[100:00.00]long")
        assertEquals(6_000_000L, lines.single().timeMs)
    }

    // ---------------- 增强 LRC（逐字） ----------------

    @Test
    fun `增强 LRC 的行内时间标签被解析成逐字时间`() {
        val lines = LrcParser.parse("[00:01.00]<00:01.00>今<00:01.50>天<00:02.50>好")
        val line = lines.single()

        assertEquals(1_000L, line.timeMs)
        // 正文里不能再残留 <00:01.50> 这种标签 —— 老版本会原样显示出来
        assertEquals("今天好", line.text)
        assertEquals(listOf(1_000L, 1_500L, 2_500L), line.words.map { it.startMs })
        assertEquals(listOf("今", "天", "好"), line.words.map { it.text })
        assertTrue(line.hasWordTiming)
    }

    @Test
    fun `一个词里多个字时按词内均分`() {
        // 「今天」两个字共用一个起唱时间，中间没有更细的信息
        val line = LrcParser.parse("[00:01.00]<00:01.00>今天<00:03.00>好").single()
        assertEquals("今天好", line.text)
        assertEquals(listOf(1_000L, 2_000L, 3_000L), line.charStartTimes())
    }

    @Test
    fun `逐字时间与正文严格等长`() {
        val line = LrcParser.parse("[00:01.00]<00:01.00> 今天 <00:03.00>好 ").single()
        // 首尾空白被裁掉，中间的空格保留，否则逐字会和文字错位
        assertEquals(line.text.length, line.charStartTimes().size)
        assertEquals(line.text.length, line.text.length)
        assertEquals(line.charStartTimes(), line.charStartTimes().sorted())
    }

    @Test
    fun `普通 LRC 每一行字符时间都等于行首时间`() {
        val line = LrcParser.parse("[00:05.00]没有逐字").single()
        assertTrue(line.words.isEmpty())
        assertEquals(List(line.text.length) { 5_000L }, line.charStartTimes())
    }

    // ---------------- 双语 ----------------

    @Test
    fun `时间戳相同的两行合并成原文加译文`() {
        val raw = "[00:10.00]Hello darkness\n[00:10.00]你好 黑暗\n[00:15.00]I've come\n"
        val lines = LrcParser.parse(raw)

        assertEquals(2, lines.size)
        assertEquals("Hello darkness", lines[0].text)
        assertEquals("你好 黑暗", lines[0].translation)
        assertNull(lines[1].translation)
    }

    @Test
    fun `时间差一点点的双语行也能合并`() {
        // 有的转换工具会给出 10ms 的偏差，肉眼是同一句
        val raw = "[00:10.00]Hello\n[00:10.01]你好\n"
        val lines = LrcParser.parse(raw)
        assertEquals(1, lines.size)
        assertEquals("你好", lines.single().translation)
    }

    @Test
    fun `逐字行不参与双语合并`() {
        // 逐字歌词里同一时间出现两行，更可能是合唱叠句；合并会把时间轴弄乱
        val raw = "[00:10.00]<00:10.00>唱<00:10.40>啊\n[00:10.00]<00:10.00>唱<00:10.40>啊\n"
        val lines = LrcParser.parse(raw)
        assertEquals(1, lines.size) // 完全重复的被去重，但没有被吞成译文
        assertNull(lines.single().translation)
    }
}
