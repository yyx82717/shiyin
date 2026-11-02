package com.shiyin.player.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * URL 拼装与摘要测试。
 *
 * 这两件事出错都不会崩，只会「悄悄变成错的请求」：
 * 参数里的空格编错，搜索就成了两个词；token 算错，整个自建曲库都连不上。
 */
class UrlCodecTest {

    @Test
    fun `空格编码为百分号二十而不是加号`() {
        assertEquals("hip%20hop", UrlCodec.encode("hip hop"))
    }

    @Test
    fun `中文按 UTF-8 编码`() {
        assertEquals("%E7%A8%BB%E9%A6%99", UrlCodec.encode("稻香"))
        assertEquals("%E7%94%B5%E5%AD%90%20%E9%9F%B3%E4%B9%90", UrlCodec.encode("电子 音乐"))
    }

    @Test
    fun `查询串的保留字符必须转义`() {
        // 不转义的话 & 会被当成参数分隔符，等于把查询截断
        assertEquals("a%26b%3Dc", UrlCodec.encode("a&b=c"))
        assertEquals("a%2Fb", UrlCodec.encode("a/b"))
        assertEquals("100%25", UrlCodec.encode("100%"))
    }

    @Test
    fun `普通字符保持原样`() {
        assertEquals("abc123", UrlCodec.encode("abc123"))
    }

    @Test
    fun `md5 与 Subsonic 官方 token 示例一致`() {
        // 官方文档：password = "sesame"，salt = "c19b2d" → token = 26719a1196d2a940705a59634eb18eab
        assertEquals("26719a1196d2a940705a59634eb18eab", md5Hex("sesame" + "c19b2d"))
    }

    @Test
    fun `md5 输出补齐前导零`() {
        // 这类摘要最容易漏的是把 0x0f 打成 "f" 而不是 "0f"，长度就会不对
        assertEquals(32, md5Hex("").length)
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", md5Hex(""))
    }
}
