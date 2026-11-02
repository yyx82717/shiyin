package com.shiyin.player.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Subsonic / Navidrome 响应解析测试。
 *
 * 这里的重点是「失败要说人话」：自建服务器最常见的问题是密码错、地址错，
 * 如果解析层把 failed 当成「0 首结果」，用户只会看到空列表，根本不知道哪里错了。
 */
class SubsonicJsonTest {

    private val buildUrl: (String, Map<String, String>) -> String = { method, params ->
        "URL[$method]" + params.toSortedMap().entries.joinToString("&") { "${it.key}=${it.value}" }
    }

    private fun parse(body: String, container: String = "randomSongs") =
        parseSubsonicTracks(body, container, "subsonic", buildUrl)

    @Test
    fun `正常响应解析出曲目与地址`() {
        val body = """
            {"subsonic-response":{"status":"ok","version":"1.16.1","randomSongs":{"song":[
              {"id":"300","title":"稻香","artist":"周杰伦","album":"魔杰座","coverArt":"al-1","duration":223},
              {"id":"301","title":"Yellow","artist":"Coldplay","duration":"267"}
            ]}}}
        """.trimIndent()

        val tracks = parse(body)

        assertEquals(2, tracks.size)
        assertEquals("subsonic:300", tracks[0].id)
        assertEquals("稻香", tracks[0].title)
        assertEquals("周杰伦", tracks[0].artist)
        assertEquals("魔杰座", tracks[0].album)
        assertEquals(223L, tracks[0].durationSec)
        assertEquals("URL[stream]id=300", tracks[0].streamUrl)
        assertEquals("URL[getCoverArt]id=al-1&size=512", tracks[0].artwork)

        // duration 有时是字符串，也要能转
        assertEquals(267L, tracks[1].durationSec)
        // 没有 coverArt 就不要拼封面地址，否则会请求一个 400
        assertNull(tracks[1].artwork)
        assertEquals("subsonic", tracks[1].source)
    }

    @Test
    fun `搜索接口的容器名是 searchResult3`() {
        val body = """
            {"subsonic-response":{"status":"ok","searchResult3":{"song":[
              {"id":"1","title":"只有一首","artist":"某人","duration":100}
            ]}}}
        """.trimIndent()

        // 用错容器名 → 应当解析不到（这正是接口写错时的表现）
        assertTrue(parse(body, "randomSongs").isEmpty())
        assertEquals(1, parse(body, "searchResult3").size)
    }

    @Test
    fun `只有一首歌时返回对象而不是数组也能解析`() {
        val body = """
            {"subsonic-response":{"status":"ok","randomSongs":{"song":
              {"id":"9","title":"单曲","artist":"某人","duration":10}
            }}}
        """.trimIndent()

        assertEquals("单曲", parse(body).single().title)
    }

    @Test
    fun `失败状态抛出服务器给的原因`() {
        val body = """
            {"subsonic-response":{"status":"failed","version":"1.16.1",
             "error":{"code":40,"message":"Wrong username or password"}}}
        """.trimIndent()

        try {
            parse(body)
            fail("应当抛出认证失败")
        } catch (e: IllegalStateException) {
            assertEquals("Wrong username or password", e.message)
        }
    }

    @Test
    fun `缺字段的曲目要么兜底要么跳过`() {
        val body = """
            {"subsonic-response":{"status":"ok","randomSongs":{"song":[
              {"id":"1","title":"   ","duration":1},
              {"id":"2","title":"有专辑艺人","albumArtist":"合辑艺人"},
              {"title":"没有 id"}
            ]}}}
        """.trimIndent()

        val tracks = parse(body)
        // 没有 id 的那条被丢掉，因为拼不出可播放的 stream 地址
        assertEquals(2, tracks.size)
        assertEquals("未命名曲目", tracks[0].title)
        assertEquals("未知艺人", tracks[0].artist)
        assertEquals("合辑艺人", tracks[1].artist)
        assertEquals(0L, tracks[1].durationSec)
    }

    @Test
    fun `响应结构不对时返回空而不是崩溃`() {
        assertTrue(parse("{}").isEmpty())
        assertTrue(parse("not json").isEmpty())
        assertTrue(parse("""{"subsonic-response":{"status":"ok"}}""").isEmpty())
    }
}
