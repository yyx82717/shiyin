package com.shiyin.player.data.source

/**
 * 外部音源的连接参数。
 *
 * 这几个源服务的都是「用户自己拥有或有权访问的内容」：
 *   - 本地库：手机存储里的音频文件
 *   - Subsonic：用户自建的音乐服务器（Navidrome / Airsonic / Gonic）
 *   - WebDAV：用户自己的网盘（AList / 坚果云 / Nextcloud）
 * 所以不涉及任何第三方平台的版权内容。
 */

data class SubsonicConfig(
    val baseUrl: String = "",
    val user: String = "",
    val password: String = "",
) {
    val usable: Boolean get() = baseUrl.isNotBlank() && user.isNotBlank()

    /** 统一去掉尾部斜杠，方便拼接 /rest/xxx。 */
    val endpoint: String get() = baseUrl.trim().trimEnd('/')
}

data class WebDavConfig(
    val baseUrl: String = "",
    val user: String = "",
    val password: String = "",
) {
    val usable: Boolean get() = baseUrl.isNotBlank()

    /** WebDAV 的目录地址必须以 / 结尾，否则 PROPFIND 会被重定向。 */
    val endpoint: String get() = baseUrl.trim().trimEnd('/') + "/"
}

/**
 * 百度网盘直连配置。
 *
 * 只需要一个 refresh_token：它是长期凭据（30 天不刷新才失效，且每刷新一次会轮换），
 * 用它能换到 access_token，然后就能列目录、取播放地址。
 * 整条链路上没有任何服务器需要常开 —— 手机自己就是客户端。
 */
data class BaiduConfig(
    val refreshToken: String = "",
    val rootDir: String = "/我的音乐",
    val accessToken: String = "",
    val accessTokenAt: Long = 0L,
) {
    /**
     * 「配好了」不等于「填了东西」。
     *
     * 之前这里只看非空，于是把 32 位授权码贴进 refresh_token 栏也算「已配置」，
     * 界面上绿灯亮着、歌一首都没有 —— 状态和事实相反是最难排查的一种。现在要求
     * 长度像真的 refresh_token，贴错就当场显示未配置。
     */
    val usable: Boolean get() = BaiduApi.looksLikeRefreshToken(refreshToken)

    /** access_token 有效期 30 天，留 3 天余量提前换。 */
    val tokenFresh: Boolean
        get() = accessToken.isNotBlank() &&
            System.currentTimeMillis() - accessTokenAt < TOKEN_SAFE_MS

    companion object {
        private const val TOKEN_SAFE_MS = 27L * 24 * 60 * 60 * 1000
    }
}
