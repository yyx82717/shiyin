package com.shiyin.player.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audius 响应解析测试。
 *
 * 样例结构照抄真实接口返回（`data` 数组 + `user`/`artwork` 嵌套）。
 * 关键点：不可流播的曲目必须丢掉，否则用户点下去只会看到「播放失败」。
 */
class AudiusJsonTest {

    private fun parse(body: String) = parseAudiusTracks(body, "https://api.audius.co", "shiyin")

    @Test
    fun `正常曲目解析出完整信息`() {
        val body = """
            {"data":[
              {"id":"W08NKdZ","title":"Lofi Study","duration":180,"is_streamable":true,
               "user":{"name":"Chillhop","handle":"chillhop"},
               "artwork":{"150x150":"https://cdn/150.jpg","480x480":"https://cdn/480.jpg","1000x1000":"https://cdn/1000.jpg"}},
              {"id":"abc123","title":"  Lofi 夜  ","duration":211,"is_streamable":true,
               "user":{"handle":"someone"}}
            ]}
        """.trimIndent()

        val tracks = parse(body)

        assertEquals(2, tracks.size)
        assertEquals("audius:W08NKdZ", tracks[0].id)
        assertEquals("Lofi Study", tracks[0].title)
        assertEquals("Chillhop", tracks[0].artist)
        assertEquals("Chillhop", tracks[0].album)
        assertEquals(180L, tracks[0].durationSec)
        // 封面按大图优先：同一张图有 150/480/1000 三种尺寸，要挑最大的
        assertEquals("https://cdn/1000.jpg", tracks[0].artwork)
        assertEquals(
            "https://api.audius.co/v1/tracks/W08NKdZ/stream?app_name=shiyin",
            tracks[0].streamUrl,
        )

        // 只有 handle 时用 handle 当艺人；标题两端空白要剪掉
        assertEquals("someone", tracks[1].artist)
        assertEquals("Lofi 夜", tracks[1].title)
        assertNull(tracks[1].artwork)
    }

    @Test
    fun `不可流播的曲目被过滤`() {
        val body = """
            {"data":[
              {"id":"playable","title":"能播","is_streamable":true},
              {"id":"paid","title":"会员专属","is_streamable":false}
            ]}
        """.trimIndent()

        val tracks = parse(body)
        assertEquals(1, tracks.size)
        assertEquals("能播", tracks.single().title)
    }

    @Test
    fun `缺 id 或标题为空的曲目兜底处理`() {
        val body = """
            {"data":[
              {"title":"没有 id"},
              {"id":"x1","title":"   ","user":{}},
              {"id":"x2","title":"有标题"}
            ]}
        """.trimIndent()

        val tracks = parse(body)
        assertEquals(2, tracks.size)
        assertEquals("未命名曲目", tracks[0].title)
        // user 是空对象时不该拿到空串艺人
        assertEquals("Audius 音乐人", tracks[0].artist)
        assertEquals(0L, tracks[0].durationSec)
    }

    @Test
    fun `空响应与畸形响应都返回空列表`() {
        assertTrue(parse("").isEmpty())
        assertTrue(parse("{}").isEmpty())
        assertTrue(parse("""{"data":[]}""").isEmpty())
        assertTrue(parse("<html>502 Bad Gateway</html>").isEmpty())
    }
}
