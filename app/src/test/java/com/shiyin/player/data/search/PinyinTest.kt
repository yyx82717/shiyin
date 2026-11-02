package com.shiyin.player.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 拼音匹配的核心逻辑测试。
 *
 * 这里不碰 `android.icu`：音译器在 JVM 单元测试里是桩实现，直接调会抛
 * "not mocked"。所以测试走 [Pinyin.keyOf] 的注入版本，用一张小表冒充 ICU 的输出 ——
 * 真正要保证的是「带声调的一串音节 → 首字母 + 全拼」这一步算得对。
 */
class PinyinTest {

    /** 假音译器：只认识几个词，其余原样返回（模拟 ICU 遇到英文/数字的行为）。 */
    private val fake: (String) -> String = { input ->
        when (input) {
            "周杰伦" -> "zhōu jié lún"
            "东风破" -> "dōng fēng pò"
            "东风破 (周杰伦)" -> "dōng fēng pò (zhōu jié lún)"
            "Jay Chou" -> "Jay Chou"
            else -> input
        }
    }

    @Test
    fun `去声调并取首字母`() {
        val key = Pinyin.keyOf("周杰伦", fake)
        assertEquals("zjl", key.initials)
        assertEquals("zhoujielun", key.full)
    }

    @Test
    fun `中文标题里的括号与英文不干扰首字母`() {
        val key = Pinyin.keyOf("东风破 (周杰伦)", fake)
        assertEquals("dfpzjl", key.initials)
        assertTrue(key.full.startsWith("dongfengpo"))
    }

    @Test
    fun `纯英文按词取首字母`() {
        val key = Pinyin.keyOf("Jay Chou", fake)
        assertEquals("jc", key.initials)
        assertEquals("jaychou", key.full)
    }

    @Test
    fun `空白与纯符号得到空 Key`() {
        assertTrue(Pinyin.keyOf("   ", fake).isEmpty)
        assertTrue(Pinyin.keyOf("《》【】", fake).isEmpty)
    }

    @Test
    fun `查询归一化只认纯字母`() {
        assertEquals("zjl", Pinyin.normalizeQuery("ZJL"))
        assertEquals("zjl", Pinyin.normalizeQuery("  zjl  "))
        assertNull(Pinyin.normalizeQuery("周杰伦"))
        assertNull(Pinyin.normalizeQuery("zjl 周"))
        assertNull(Pinyin.normalizeQuery("z"))
        assertNull(Pinyin.normalizeQuery("j j"))
    }

    @Test
    fun `命中强度分三档`() {
        val key = Pinyin.keyOf("周杰伦", fake)
        assertEquals(3, Pinyin.scoreOf("zjl", key))
        assertEquals(2, Pinyin.scoreOf("jl", key))
        assertEquals(1, Pinyin.scoreOf("jielun", key))
        assertEquals(0, Pinyin.scoreOf("ljj", key))
    }

    @Test
    fun `首字母前缀优先于全拼包含`() {
        val title = Pinyin.keyOf("东风破 (周杰伦)", fake)
        assertTrue(Pinyin.scoreOf("dfp", title) > Pinyin.scoreOf("dongfeng", title))
    }
}
