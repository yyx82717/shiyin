package com.shiyin.player.data.source

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 本地搜索的关键词归一化。
 *
 * 中文用户搜「周杰伦」「JAY」「jay」「ＪＡＹ」应该是一回事，
 * 搜「晴天」也要能命中「晴天 (Live)」。
 */
class LocalQueryTest {

    @Test
    fun `全角字母数字转半角并转小写`() {
        assertEquals("jay", LocalSource.Query.normalize("ＪＡＹ"))
        assertEquals("2002", LocalSource.Query.normalize("２００２"))
    }

    @Test
    fun `去掉空格括号与标点`() {
        assertEquals("晴天live", LocalSource.Query.normalize("晴天 (Live)"))
        assertEquals("斑马斑马", LocalSource.Query.normalize("斑马，斑马"))
        assertEquals("周杰伦", LocalSource.Query.normalize(" 周杰伦 "))
    }

    @Test
    fun `中文全角空格也算空白`() {
        assertEquals("abc", LocalSource.Query.normalize("a\u3000b c"))
    }
}
