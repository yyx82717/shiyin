package com.shiyin.player.data.source

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Jamendo 曲风标签拼装。
 *
 * Jamendo 的 `fuzzytags` 用 `+` 表示「或」。之前的实现先把空格换成 `+`、
 * 再整体做 URL 转义，`+` 被编成 `%2B`，服务端收到的是一个不存在的标签，
 * 曲风筛选整个失效。这组用例就是钉住这个行为。
 */
class JamendoTagsTest {

    @Test
    fun `单个标签原样输出`() {
        assertEquals("jazz", jamendoFuzzyTags("Jazz"))
        assertEquals("lofi", jamendoFuzzyTags("  lofi  "))
    }

    @Test
    fun `多个词用裸加号连接而不是百分号二B`() {
        assertEquals("hip+hop", jamendoFuzzyTags("Hip Hop"))
        assertEquals("lo-fi+chill", jamendoFuzzyTags("lo-fi, chill"))
    }

    @Test
    fun `词本身单独转义后再连接`() {
        assertEquals("%E7%94%B5%E5%AD%90+%E9%9F%B3%E4%B9%90", jamendoFuzzyTags("电子 音乐"))
    }

    @Test
    fun `空输入不会拼出多余分隔符`() {
        assertEquals("", jamendoFuzzyTags(""))
        assertEquals("jazz", jamendoFuzzyTags(" + jazz + "))
    }
}
