package com.shiyin.player.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser

/**
 * WebDAV `multistatus` 解析测试。
 *
 * 这是整个 App 里最容易出错的一段：真实服务器返回的 XML 一定带命名空间前缀，
 * 目录的 href 一定以 `/` 结尾，中文文件名一定被百分号转义。
 * 这三件事任意一件处理错，结果都是「网盘里明明有歌，App 里一首都没有」。
 */
class DavXmlTest {

    /** 用 kxml2 的解析器直接在 JVM 上跑；Android 侧走 android.util.Xml，行为一致。 */
    private fun parser(): XmlPullParser = KXmlParser()

    private val base = "http://192.168.1.10:5244/dav/music/"

    private val sample = """
        <?xml version="1.0" encoding="utf-8"?>
        <D:multistatus xmlns:D="DAV:">
          <D:response>
            <D:href>/dav/music/</D:href>
            <D:propstat>
              <D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
              <D:status>HTTP/1.1 200 OK</D:status>
            </D:propstat>
          </D:response>
          <D:response>
            <D:href>/dav/music/%E5%91%A8%E6%9D%B0%E4%BC%A6%20-%20%E7%A8%BB%E9%A6%99.flac</D:href>
            <D:propstat>
              <D:prop><D:resourcetype/></D:prop>
            </D:propstat>
          </D:response>
          <D:response>
            <D:href>/dav/music/Sub/</D:href>
            <D:propstat>
              <D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
            </D:propstat>
          </D:response>
          <D:response>
            <D:href>/dav/music/cover.jpg</D:href>
            <D:propstat>
              <D:prop><D:resourcetype/></D:prop>
            </D:propstat>
          </D:response>
        </D:multistatus>
    """.trimIndent()

    @Test
    fun `解析出全部条目并识别目录`() {
        val entries = parseDavMultiStatus(sample, base, parser())

        assertEquals(4, entries.size)
        assertEquals(
            listOf("music", "周杰伦 - 稻香.flac", "Sub", "cover.jpg"),
            entries.map { it.name },
        )
        assertEquals(listOf(true, false, true, false), entries.map { it.isDir })
    }

    @Test
    fun `目录以斜杠结尾时名字不会变成空串`() {
        // 这是最容易踩的一脚：/dav/music/Sub/ 的最后一段是空串，
        // 不滤掉空段就会得到空名字，然后被「忽略目录」规则整棵子树都丢掉。
        val entries = parseDavMultiStatus(sample, base, parser())
        val sub = entries.single { it.name == "Sub" }
        assertTrue(sub.isDir)
        assertEquals("http://192.168.1.10:5244/dav/music/Sub/", sub.href)
    }

    @Test
    fun `href 解析为可直接播放的完整地址`() {
        val entries = parseDavMultiStatus(sample, base, parser())
        val track = entries.single { it.name.endsWith(".flac") }
        // okhttp 会把中文路径按 UTF-8 重新编码成合法 URL
        assertTrue(track.href.startsWith("http://192.168.1.10:5244/dav/music/"))
        assertTrue(track.href.endsWith(".flac"))
        assertFalse(track.isDir)
    }

    @Test
    fun `小写前缀和绝对地址都能识别`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>https://cloud.example.com/remote.php/dav/files/me/Music/Adele - Hello.mp3</d:href>
                <d:propstat><d:prop><d:resourcetype/></d:prop></d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val entries = parseDavMultiStatus(xml, "https://cloud.example.com/remote.php/dav/files/me/Music/", parser())

        assertEquals(1, entries.size)
        // 展示名是解码后的，播放地址是编码后的——两者不能混用
        assertEquals("Adele - Hello.mp3", entries.single().name)
        assertEquals(
            "https://cloud.example.com/remote.php/dav/files/me/Music/Adele%20-%20Hello.mp3",
            entries.single().href,
        )
    }

    @Test
    fun `空响应和畸形响应都不会崩`() {
        assertTrue(parseDavMultiStatus("", base, parser()).isEmpty())
        assertTrue(parseDavMultiStatus("<D:multistatus xmlns:D=\"DAV:\"/>", base, parser()).isEmpty())
    }

    @Test
    fun `文件名拆成艺人和标题`() {
        assertEquals("周杰伦" to "稻香", splitDavFileName("周杰伦 - 稻香.flac"))
        assertEquals("" to "稻香", splitDavFileName("稻香.mp3"))
        // 下划线常被用来代替空格
        assertEquals("Adele" to "Hello", splitDavFileName("Adele_-_Hello.mp3"))
        // 标题里本身带 " - " 时，只按第一个分隔
        assertEquals("A" to "B - C", splitDavFileName("A - B - C.m4a"))
        // 开头就是分隔符，不能解析出空艺人
        assertEquals("" to "- 无题", splitDavFileName(" - 无题.mp3"))
    }

    @Test
    fun `音频扩展名判定`() {
        assertTrue(isAudioFileName("a.mp3"))
        assertTrue(isAudioFileName("a.FLAC"))
        assertTrue(isAudioFileName("a.ape"))
        assertFalse(isAudioFileName("cover.jpg"))
        assertFalse(isAudioFileName("readme"))
    }

    @Test
    fun `无法解析的 base 返回 null`() {
        assertNull(resolveDavHref("not-a-url", "/dav/a.mp3"))
    }
}
