package com.shiyin.player.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 百度直连的纯逻辑：URL 怎么拼、响应怎么读、文件名怎么拆。
 *
 * 这几条一旦错了，表现是「授权成功但一首歌都读不到」或者「列表有歌但点了播不了」，
 * 都是最难从界面反推原因的那类问题，所以每个都锁在测试里。
 */
class BaiduApiTest {

    // ---------------- URL 拼装 ----------------

    @Test
    fun `换 token 的地址带上 grant_type 和客户端凭据`() {
        val url = BaiduApi.tokenUrl("122.abc")
        assertTrue(url.startsWith("https://openapi.baidu.com/oauth/2.0/token?"))
        assertTrue(url.contains("grant_type=refresh_token"))
        assertTrue(url.contains("refresh_token=122.abc"))
        assertTrue(url.contains("client_id=${BaiduApi.CLIENT_ID}"))
    }

    @Test
    fun `refresh_token 里的特殊字符会被转义`() {
        val url = BaiduApi.tokenUrl("122.a b+c/d")
        // 空格必须是 %20，不能是 '+'（在 query 里 + 会被当作空格，百度也不认）
        assertTrue(url.contains("refresh_token=122.a%20b%2Bc%2Fd"))
        assertFalse(url.contains("refresh_token=122.a b+c/d"))
    }

    @Test
    fun `列目录必须带 web 标记`() {
        val url = BaiduApi.listUrl("tok", "/我的音乐", 0, 1000)
        assertTrue(url.contains("method=list"))
        assertTrue(url.contains("access_token=tok"))
        assertTrue(url.contains("web=web"))
        // 中文目录要编码
        assertTrue(url.contains("dir=%2F%E6%88%91%E7%9A%84%E9%9F%B3%E4%B9%90"))
    }

    @Test
    fun `播放地址走 App 通道参数`() {
        val url = BaiduApi.mediaUrl("tok", "/我的音乐/a.mp3", 123L)
        assertTrue(url.startsWith("https://pan.baidu.com/api/mediainfo?"))
        assertTrue(url.contains("type=VideoURL"))
        assertTrue(url.contains("fs_id=123"))
        assertTrue(url.contains("media=1"))
        assertTrue(url.contains("clienttype=1"))
        assertTrue(url.contains("origin=dlna"))
        // devuid 直接写编码后的 %251：写 "0%1" 会被二次转义成 0%251 才对，直接写编码值更稳
        assertTrue(url.contains("devuid=0%251"))
        assertTrue(url.contains("channel=${BaiduApi.CHANNEL}"))
    }

    // ---------------- 解析 ----------------

    @Test
    fun `列目录能读出文件与文件夹`() {
        val json = """
            {"errno":0,"list":[
              {"fs_id":11,"path":"/我的音乐/已下载/a.mp3","server_filename":"a.mp3","size":1024,"isdir":0},
              {"fs_id":12,"path":"/我的音乐/旧","server_filename":"旧","size":0,"isdir":1}
            ]}
        """.trimIndent()
        val list = BaiduApi.parseList(json)!!
        assertEquals(2, list.size)
        assertFalse(list[0].isDir)
        assertEquals(1024L, list[0].size)
        assertTrue(list[1].isDir)
    }

    @Test
    fun `空目录返回空列表而不是 null`() {
        assertEquals(emptyList<BaiduApi.BaiduEntry>(), BaiduApi.parseList("""{"errno":0}"""))
    }

    @Test
    fun `出错时返回 null 好让调用方区分空目录`() {
        assertNull(BaiduApi.parseList("""{"errno":-6}"""))
        assertNull(BaiduApi.parseList("<html>被劫持了</html>"))
    }

    @Test
    fun `换 token 成功`() {
        val json = """{"access_token":"121.xx","refresh_token":"122.yy","expires_in":2592000}"""
        val token = BaiduApi.parseToken(json)!!
        assertEquals("121.xx", token.accessToken)
        assertEquals("122.yy", token.refreshToken)
        assertEquals(2592000L, token.expiresInSec)
    }

    @Test
    fun `换 token 失败时给出可读原因`() {
        val json = """{"error":"invalid_grant","error_description":"Refresh token has been used"}"""
        assertNull(BaiduApi.parseToken(json))
        val message = BaiduApi.tokenError(json).orEmpty()
        assertTrue(message.contains("已失效"))
        assertTrue(message.contains("重新授权"))
    }

    @Test
    fun `从 mediainfo 里取直链`() {
        val json = """{"errno":0,"info":{"dlink":"https://d.pcs.baidu.com/file/abc?fid=1&rt=pr"}}"""
        assertEquals("https://d.pcs.baidu.com/file/abc?fid=1&rt=pr", BaiduApi.parseDlink(json))
        assertNull(BaiduApi.mediaError(json))
    }

    @Test
    fun `拿不到直链时把 errno 翻成人话`() {
        val json = """{"errno":31066}"""
        assertNull(BaiduApi.parseDlink(json))
        assertEquals("文件不存在或已被删除", BaiduApi.mediaError(json))
    }

    @Test
    fun `音频扩展名判断不分大小写`() {
        assertTrue(BaiduApi.isAudioFile("A.MP3"))
        assertTrue(BaiduApi.isAudioFile("b.Flac"))
        assertTrue(BaiduApi.isAudioFile("c.wav"))
        assertFalse(BaiduApi.isAudioFile("整轨.zip"))
        assertFalse(BaiduApi.isAudioFile("封面.jpg"))
    }

    // ---------------- 播放地址编码 ----------------

    @Test
    fun `播放地址往返后路径不丢`() {
        val path = "/我的音乐/已下载/(红尘情歌) 男女对唱 - Dj晓东.mp3"
        val url = BaiduApi.streamUrl(123456L, path)
        val ref = BaiduApi.parseStreamUrl(url)!!
        assertEquals(123456L, ref.fsId)
        assertEquals(path, ref.path)
    }

    @Test
    fun `不是百度地址就返回 null`() {
        assertNull(BaiduApi.parseStreamUrl("https://example.com/a.mp3"))
        assertNull(BaiduApi.parseStreamUrl("baidu://没有斜杠"))
        assertNull(BaiduApi.parseStreamUrl("baidu://123/"))
    }

    // ---------------- 文件名拆分 ----------------

    @Test
    fun `拆分歌手与歌名`() {
        val (artist, title) = BaiduApi.splitFileName("周杰伦 - 晴天.mp3")
        assertEquals("周杰伦", artist)
        assertEquals("晴天", title)
    }

    @Test
    fun `没有空格分隔也能拆`() {
        val (artist, title) = BaiduApi.splitFileName("马健涛-搀扶.mp3")
        assertEquals("马健涛", artist)
        assertEquals("搀扶", title)
    }

    @Test
    fun `去掉下载站后缀与序号前缀`() {
        val (artist, title) = BaiduApi.splitFileName("01.孙露 - 别亦难[www.xx.com].flac")
        assertEquals("孙露", artist)
        assertEquals("别亦难", title)
    }

    @Test
    fun `没有歌手时整体当歌名`() {
        val (artist, title) = BaiduApi.splitFileName("远山之外是远山.mp3")
        assertEquals("", artist)
        assertEquals("远山之外是远山", title)
    }

    @Test
    fun `标题里的连字符不会被误当成歌手分隔`() {
        // "A-B" 右边只有一个字符，多半是歌名本身，不该拆成歌手
        val (artist, _) = BaiduApi.splitFileName("a-b.mp3")
        assertEquals("", artist)
    }

    // ---------------- 一键授权 ----------------

    @Test
    fun `授权地址带 oob 回调与网盘权限`() {
        val url = BaiduApi.authorizeUrl()
        assertTrue(url.startsWith("https://openapi.baidu.com/oauth/2.0/authorize"))
        assertTrue(url.contains("client_id=${BaiduApi.CLIENT_ID}"))
        assertTrue(url.contains("redirect_uri=oob"))
        // 少了 netdisk 就只能读基础信息，列不了目录
        assertTrue(url.contains("scope=basic,netdisk"))
    }

    @Test
    fun `授权码换取 token 的地址做了编码`() {
        val url = BaiduApi.exchangeUrl("a b+c/d")
        assertTrue(url.contains("grant_type=authorization_code"))
        // 空格在拼 URL 之前就被抹掉了（粘贴带进来的空白必然换来一个 400），
        // 剩下的 + 和 / 仍然要转义，否则百度会切错参数
        assertTrue(url.contains("code=ab%2Bc%2Fd"))
        assertTrue(url.contains("redirect_uri=oob"))
    }

    @Test
    fun `从授权页文字里取出授权码`() {
        val code = "0123456789abcdef0123456789abcdef"
        assertEquals(code, BaiduApi.extractCode("授权成功\n授权码：$code\n请复制保存"))
    }

    @Test
    fun `授权码和标签之间有空格或换行也能取到`() {
        val code = "fedcba9876543210fedcba9876543210"
        assertEquals(code, BaiduApi.extractCode("授权码\n\n   $code"))
    }

    @Test
    fun `没有标签时退回到页面上唯一的 32 位串`() {
        val code = "aaaabbbbccccddddaaaabbbbccccdddd"
        assertEquals(code, BaiduApi.extractCode("请复制 $code 关闭本页"))
    }

    @Test
    fun `页面还没渲染出授权码时返回 null`() {
        assertNull(BaiduApi.extractCode(""))
        assertNull(BaiduApi.extractCode("登录百度账号"))
    }

    @Test
    fun `长度不足 32 位的串不会被误认成授权码`() {
        assertNull(BaiduApi.extractCode("错误码 0123abcd"))
    }

    // ---------------- 错误提示 ----------------

    @Test
    fun `安全策略拒绝时给出可操作的中文提示`() {
        val body = """{"error":"Trigger security policy","error_description":"Please try again later"}"""
        val message = BaiduApi.tokenError(body).orEmpty()
        assertTrue(message.contains("安全策略"))
        // 必须提到 AList，这是实际最常见的原因（两边抢同一份授权）
        assertTrue(message.contains("AList"))
    }

    @Test
    fun `refresh_token 失效时说清要重新授权`() {
        val message = BaiduApi.tokenError("""{"error":"invalid_grant"}""").orEmpty()
        assertTrue(message.contains("失效"))
        assertTrue(message.contains("重新授权"))
    }

    @Test
    fun `未知错误保留原始错误码方便排查`() {
        val message = BaiduApi.tokenError("""{"error":"some_new_error","error_description":"whatever"}""").orEmpty()
        assertTrue(message.contains("some_new_error"))
        assertTrue(message.contains("whatever"))
    }

    // ---------------- 凭据识别 ----------------

    /** 真实的 refresh_token 是「122.」加一长串，一共 80 多个字符。 */
    private val realToken = "122." + "a1B2c3D4e5".repeat(7) + "abcdefghi"

    @Test
    fun `粘贴时带进来的空白和换行会被抹掉`() {
        // 手机上复制长串几乎必然夹带换行/空格，肉眼看不见，但放进 URL 就是一个 400
        assertEquals(realToken, BaiduApi.sanitizeCredential(" $realToken\n"))
        assertEquals(
            "122.abc",
            BaiduApi.sanitizeCredential("122.\nabc "),
        )
        assertEquals("", BaiduApi.sanitizeCredential("  \n\t "))
    }

    @Test
    fun `83 位的长串认成 refresh_token`() {
        assertEquals(83, realToken.length)
        assertTrue(BaiduApi.looksLikeRefreshToken(realToken))
        assertFalse(BaiduApi.looksLikeAuthCode(realToken))
    }

    @Test
    fun `页面上的 32 位授权码认成授权码而不是 refresh_token`() {
        // 这就是用户最容易干的事：页面上只有这一串可复制，于是填进 token 栏
        val code = "545429ad3294dbd1ad469a1b2c3d4e5f"
        assertEquals(32, code.length)
        assertTrue(BaiduApi.looksLikeAuthCode(code))
        assertTrue(BaiduApi.looksLikeAuthCode("  $code\n"))
        // 关键：它不能被当成 refresh_token 存下去，否则「已配置」是假的
        assertFalse(BaiduApi.looksLikeRefreshToken(code))
    }

    @Test
    fun `短串和乱码两样都不是`() {
        assertFalse(BaiduApi.looksLikeAuthCode("a2fbf43afa41def"))
        assertFalse(BaiduApi.looksLikeRefreshToken("a2fbf43afa41def"))
        assertFalse(BaiduApi.looksLikeAuthCode(""))
        assertFalse(BaiduApi.looksLikeRefreshToken(""))
    }

    @Test
    fun `授权码换 token 失败时说的是授权码过期`() {
        // 同一个 invalid_grant，在两条路径上含义不同：
        // 换 token 失败 = 码过期/已用；换 refresh_token 失败 = 被别的程序抢刷了
        val message = BaiduApi.tokenError(
            """{"error":"invalid_grant","error_description":"invalid code"}""",
            forAuthCode = true,
        ).orEmpty()
        assertTrue(message.contains("授权码"))
        assertTrue(message.contains("过期"))
        assertFalse(message.contains("AList"))
    }

    @Test
    fun `参数不合法时提示两种凭据都收`() {
        val message = BaiduApi.tokenError("""{"error":"invalid_request"}""").orEmpty()
        assertTrue(message.contains("122."))
        assertTrue(message.contains("授权码"))
    }
}
