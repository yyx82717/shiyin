package com.shiyin.player.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TTML 歌词解析。
 *
 * 这个格式没有唯一实现，各家（Apple Music、AMLL、B 站导出工具）写法都有出入，
 * 所以用例覆盖的是「变体」而不是「标准」：时间格式、译文段落位置、
 * 缺 begin/end 的 span、HTML 错误页。
 */
class TtmlParserTest {

    // 注意：这里故意不带 XML 声明。真实文件里的 `<?xml ...?>` 见下面单独的用例，
    // 它前面不能有任何空白，否则整份文档解析不了。
    private fun ttml(body: String) =
        """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">""" +
            "<body><div>" + body + "</div></body></tt>"

    @Test
    fun `带 XML 声明和 BOM 的文件也能解析`() {
        // 下载回来的 .ttml 常带 BOM；而 `<?xml?>` 前面多一个字节就会整份解析失败
        val raw = "\uFEFF" + """
            <?xml version="1.0" encoding="UTF-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1s" end="3s">一句</p></div></body></tt>
        """.trimIndent()

        assertEquals("一句", TtmlParser.parse(raw).single().text)
    }

    @Test
    fun `声明前面有空白也当没看见`() {
        val raw = "\n\n   <?xml version=\"1.0\"?><tt><body><div><p begin=\"1s\">x</p></div></body></tt>"
        assertEquals("x", TtmlParser.parse(raw).single().text)
    }

    @Test
    fun `逐字段落拆成词表，整行时间来自 p`() {
        val raw = ttml(
            """
            <p begin="00:00:12.500" end="00:00:15.200">
              <span begin="00:00:12.500" end="00:00:12.900">今</span>
              <span begin="00:00:12.900" end="00:00:13.400">天</span>
            </p>
            """.trimIndent(),
        )

        val lines = TtmlParser.parse(raw)
        assertEquals(1, lines.size)
        val line = lines.single()
        assertEquals(12_500L, line.timeMs)
        assertEquals("今天", line.text)
        assertEquals(2, line.words.size)
        assertEquals(listOf(12_500L, 12_900L), line.words.map { it.startMs })
        assertTrue(line.hasWordTiming)
    }

    @Test
    fun `译文段落挂到原文行上而不是自占一行`() {
        val raw = ttml(
            """
            <p begin="00:00:12.500" end="00:00:15.200">
              <span begin="00:00:12.500" end="00:00:13.000">今天</span>
            </p>
            <p begin="00:00:12.500" end="00:00:15.200" ttm:role="x-translation">Today</p>
            """.trimIndent(),
        )

        val lines = TtmlParser.parse(raw)
        assertEquals(1, lines.size)
        assertEquals("今天", lines.single().text)
        assertEquals("Today", lines.single().translation)
    }

    @Test
    fun `译文段落写在原文前面也能挂上`() {
        // 有的导出工具把译文放前面，按文档顺序读的话原文还没建出来
        val raw = ttml(
            """
            <p begin="00:00:05.000" end="00:00:07.000" ttm:role="x-translation">Yesterday</p>
            <p begin="00:00:05.000" end="00:00:07.000">
              <span begin="00:00:05.000" end="00:00:06.000">昨天</span>
            </p>
            """.trimIndent(),
        )

        val lines = TtmlParser.parse(raw)
        assertEquals(1, lines.size)
        assertEquals("昨天", lines.single().text)
        assertEquals("Yesterday", lines.single().translation)
    }

    @Test
    fun `音译单独挂到 roman 字段`() {
        val raw = ttml(
            """
            <p begin="00:00:01.000" end="00:00:03.000">
              <span begin="00:00:01.000" end="00:00:02.000">君</span>
            </p>
            <p begin="00:00:01.000" end="00:00:03.000" ttm:role="x-roman">kimi</p>
            """.trimIndent(),
        )

        val line = TtmlParser.parse(raw).single()
        assertEquals("kimi", line.roman)
        assertNull(line.translation)
    }

    @Test
    fun `秒为单位的写法也认`() {
        val raw = ttml(
            """
            <p begin="10s" end="12s">
              <span begin="10s">一</span>
              <span begin="11s">二</span>
            </p>
            """.trimIndent(),
        )

        val line = TtmlParser.parse(raw).single()
        assertEquals(10_000L, line.timeMs)
        assertEquals(12_000L, line.endMs)
        assertEquals(listOf(10_000L, 11_000L), line.words.map { it.startMs })
        assertEquals("一二", line.text)
    }

    @Test
    fun `span 缺时间时按段落均分补齐`() {
        val raw = ttml(
            """
            <p begin="00:00:10.000" end="00:00:14.000">
              <span>一</span><span>二</span><span>三</span><span>四</span>
            </p>
            """.trimIndent(),
        )

        val line = TtmlParser.parse(raw).single()
        assertEquals(4, line.words.size)
        // 4 秒 4 个词 → 每个 1 秒，且时间单调
        assertEquals(listOf(10_000L, 11_000L, 12_000L, 13_000L), line.words.map { it.startMs })
    }

    @Test
    fun `没有 span 的段落退化成整行时间`() {
        val raw = ttml("""<p begin="00:00:03.000" end="00:00:05.000">只有一句</p>""")

        val line = TtmlParser.parse(raw).single()
        assertEquals("只有一句", line.text)
        assertTrue(line.words.isEmpty())
        assertFalse(line.hasWordTiming)
    }

    @Test
    fun `br 换行当空格，不会把两段文字粘在一起`() {
        val raw = ttml(
            """
            <p begin="00:00:01.000" end="00:00:03.000">
              <span begin="00:00:01.000">Hello</span><br/><span begin="00:00:02.000">world</span>
            </p>
            """.trimIndent(),
        )

        assertEquals("Hello world", TtmlParser.parse(raw).single().text)
    }

    @Test
    fun `HTML 错误页不会被当成 TTML`() {
        val html = """
            <!DOCTYPE html><html><head><title>404</title></head>
            <body><h1>Not Found</h1><table><tr><td>tt</td></tr></table></body></html>
        """.trimIndent()
        assertFalse(LyricsText.looksLikeTtml(html))
        assertTrue(LyricsText.parse(html).isEmpty())
    }

    @Test
    fun `坏掉的 XML 不会抛异常`() {
        assertTrue(TtmlParser.parse("<tt><body><div><p begin=").isEmpty())
        assertTrue(TtmlParser.parse("").isEmpty())
        assertTrue(TtmlParser.parse("随便一段文字").isEmpty())
    }
}
