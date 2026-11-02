package com.shiyin.player.data.source

import com.shiyin.player.data.remote.UrlCodec
import com.shiyin.player.data.remote.asArrOrNull
import com.shiyin.player.data.remote.asObjOrNull
import com.shiyin.player.data.remote.int
import com.shiyin.player.data.remote.long
import com.shiyin.player.data.remote.parseJsonOrNull
import com.shiyin.player.data.remote.str
import java.net.URLDecoder

/**
 * 百度网盘直连的纯逻辑层：URL 怎么拼、响应怎么读。
 *
 * 为什么不用官方开放平台的 `filemetas`：那条通道签发的直链带着 `origin_appid`，
 * 百度对第三方应用标识做了限速，实测只有 80 KB/s，播 flac 会卡成幻灯片；
 * 而且在部分文件上直接 403。
 *
 * 网盘 App 自己的「边下边播」通道是 `POST /api/mediainfo`（`media=1`、`clienttype=1`、
 * 安卓客户端 channel 标识、`origin=dlna`），签发的地址不排队也不限速，
 * 同一个文件实测 1.3–3.4 MB/s。AList 的 `crack_video` 走的就是这条路。
 *
 * 所以这里复刻的是「App 播放器怎么问网盘要地址」，读的仍然是用户自己的文件。
 */
object BaiduApi {

    /** 公开的开放平台应用凭据（AList 一直用这一对，用于 refresh_token 换 access_token）。 */
    const val CLIENT_ID = "hq9yQ9w9kR4YHj1kyYafLygVocobh7Sf"
    const val CLIENT_SECRET = "YH2VpZcFJHYNnV6vLfHQXDBhcE7ZChyE"

    /** 网盘安卓客户端的渠道串。这个值决定百度把请求当成「App 内播放」而不是第三方抓取。 */
    const val CHANNEL = "android_15_25010PN30C_bd-netdisk_1523a"

    const val TOKEN_HOST = "https://openapi.baidu.com/oauth/2.0/token"
    const val API_HOST = "https://pan.baidu.com"

    private val AUDIO_EXT = setOf(
        ".mp3", ".flac", ".m4a", ".wav", ".ogg", ".aac", ".ape", ".wma", ".opus", ".dsf", ".dff",
    )

    fun tokenUrl(refreshToken: String): String =
        "$TOKEN_HOST?grant_type=refresh_token" +
            "&refresh_token=${UrlCodec.encode(refreshToken)}" +
            "&client_id=$CLIENT_ID&client_secret=$CLIENT_SECRET"

    // ---------------- OAuth 授权 ----------------

    /** 百度用 oob（out-of-band）回调：授权完页面直接显示一串授权码，不跳回 App。 */
    const val REDIRECT_URI = "oob"

    fun authorizeUrl(): String =
        "https://openapi.baidu.com/oauth/2.0/authorize?response_type=code" +
            "&client_id=$CLIENT_ID&redirect_uri=$REDIRECT_URI" +
            "&scope=basic,netdisk&display=page"

    fun exchangeUrl(code: String): String =
        "$TOKEN_HOST?grant_type=authorization_code" +
            "&code=${UrlCodec.encode(sanitizeCredential(code))}" +
            "&client_id=$CLIENT_ID&client_secret=$CLIENT_SECRET" +
            "&redirect_uri=$REDIRECT_URI"

    // ---------------- 凭据识别 ----------------

    /**
     * 去掉所有空白字符。
     *
     * 手机粘贴几乎必带东西：自动换行插进来的 `\n`、从聊天框带出来的空格、末尾的回车。
     * 这些肉眼完全看不出来，但放进 URL 就是一个 400。所以但凡用户给的凭据，
     * 第一件事是把空白抹掉。
     */
    fun sanitizeCredential(raw: String): String = raw.filterNot { it.isWhitespace() }

    /**
     * refresh_token 的形状：`122.` 开头，一共 80 多个字符。
     *
     * 这里不写死 83，是因为百度换过长度；用「够长」来判，够用且不会把正常 token 判错。
     * 32 位授权码远远不够长，所以能稳定区分开。
     */
    fun looksLikeRefreshToken(raw: String): Boolean = sanitizeCredential(raw).length >= 60

    /**
     * 百度 oob 页面上那串 32 位授权码。
     *
     * 用户最容易犯的错就是把它当 refresh_token 填进设置页 —— 那一栏写着「83 个字符」，
     * 但页面上只有这一串能复制，于是必然填错。既然两样东西都只有用户手上才有，
     * 那就让 App 自己认：认出来是授权码，就当场换 token，不用人来分辨。
     */
    fun looksLikeAuthCode(raw: String): Boolean =
        AUTH_CODE_RE.matches(sanitizeCredential(raw))

    private val AUTH_CODE_RE = Regex("[0-9A-Za-z]{32}")

    /**
     * 从授权成功页面的文字里抠出授权码。
     *
     * 百度 oob 页面会显示「授权码：xxxxxxxx」，但排版改过几次，所以先按标签匹配，
     * 匹配不到再退回「页面上唯一一段 32 位十六进制」。两者都没有就返回 null，
     * 由界面提示用户手动复制。
     */
    fun extractCode(pageText: String): String? {
        if (pageText.isBlank()) return null
        Regex("""授权码[^0-9A-Za-z]{0,6}([0-9A-Za-z]{32})""")
            .find(pageText)?.let { return it.groupValues[1] }
        return Regex("""\b([0-9a-f]{32})\b""").find(pageText)?.groupValues?.get(1)
    }

    /** 列目录。`web=web` 是网页端的标记，少了它会返回空列表。 */
    fun listUrl(token: String, dir: String, start: Int, limit: Int): String =
        "$API_HOST/rest/2.0/xpan/file?method=list" +
            "&access_token=${UrlCodec.encode(token)}" +
            "&dir=${UrlCodec.encode(dir)}" +
            "&start=$start&limit=$limit&web=web"

    /**
     * 取播放直链。
     *
     * `devuid` 在 AList 里写作 `0%1`，经过 query 编码后发给百度的是 `0%251`，
     * 这里直接写编码后的值，避免被二次转义。
     */
    fun mediaUrl(token: String, path: String, fsId: Long): String =
        "$API_HOST/api/mediainfo?type=VideoURL" +
            "&path=${UrlCodec.encode(path)}" +
            "&fs_id=$fsId" +
            "&devuid=0%251&clienttype=1&channel=$CHANNEL" +
            "&nom3u8=1&dlink=1&media=1&origin=dlna" +
            "&access_token=${UrlCodec.encode(token)}"

    fun isAudioFile(name: String): Boolean {
        val lower = name.lowercase()
        return AUDIO_EXT.any { lower.endsWith(it) }
    }

    /** 百度返回的 errno，0 是成功。 */
    fun errnoOf(json: String): Int =
        parseJsonOrNull(json).asObjOrNull()?.int("errno") ?: -1

    internal fun errorMessage(errno: Int): String = when (errno) {
        -6 -> "登录状态失效，需要在设置里重新授权百度网盘"
        111 -> "授权已过期，需要重新授权"
        31023 -> "百度拒绝了这次请求（目录或文件不可访问）"
        31066 -> "文件不存在或已被删除"
        31064 -> "没有权限读取这个文件"
        2 -> "百度网盘返回参数错误"
        -7 -> "请求太频繁，稍等一下再试"
        20016 -> "触发限流，稍后再试"
        9019 -> "没有权限访问该接口，token 可能不完整"
        else -> "百度网盘返回错误码 $errno"
    }

    data class BaiduEntry(
        val fsId: Long,
        val path: String,
        val name: String,
        val size: Long,
        val isDir: Boolean,
    )

    /** 解析目录列表。失败返回 null，调用方据此区分「空目录」和「出错了」。 */
    fun parseList(json: String): List<BaiduEntry>? {
        val root = parseJsonOrNull(json).asObjOrNull() ?: return null
        if (root.int("errno") != 0) return null
        val arr = root["list"].asArrOrNull() ?: return emptyList()
        return arr.mapNotNull { element ->
            val item = element.asObjOrNull() ?: return@mapNotNull null
            val fsId = item.long("fs_id") ?: return@mapNotNull null
            val path = item.str("path") ?: return@mapNotNull null
            val name = item.str("server_filename") ?: path.substringAfterLast('/')
            BaiduEntry(
                fsId = fsId,
                path = path,
                name = name,
                size = item.long("size") ?: 0L,
                isDir = item.int("isdir") == 1,
            )
        }
    }

    data class TokenResult(
        val accessToken: String,
        val refreshToken: String,
        val expiresInSec: Long,
    )

    fun parseToken(json: String): TokenResult? {
        val root = parseJsonOrNull(json).asObjOrNull() ?: return null
        // 失败时是 error / error_description，没有 access_token
        val access = root.str("access_token") ?: return null
        val refresh = root.str("refresh_token") ?: return null
        return TokenResult(access, refresh, root.long("expires_in") ?: 2_592_000L)
    }

    /**
     * 把百度返回的 `error` 翻译成人话。
     *
     * [forAuthCode] 区分两种调用：换 refresh_token 失败和用授权码换 token 失败。
     * 同样是 `invalid_grant`，前者是 token 被别处刷掉了，后者是授权码过期/已用过，
     * 说的是两件事，给的解法也完全不同。
     */
    fun tokenError(json: String, forAuthCode: Boolean = false): String? {
        val root = parseJsonOrNull(json).asObjOrNull() ?: return "授权服务器没有返回有效内容"
        val error = root.str("error") ?: return null
        val desc = root.str("error_description").orEmpty().trim()
        return when (error) {
            "invalid_grant" -> if (forAuthCode) {
                "授权码无效或已过期。授权码只有 10 分钟寿命、且只能换一次 —— 重新授权即可。"
            } else {
                "refresh_token 已失效：它被别的程序刷新过（比如 AList 也在用同一份授权），" +
                    "或者授权被撤销了。需要重新授权一次。"
            }
            "invalid_client" -> "应用凭据无效（client_id / client_secret 被停用）"
            "Trigger security policy" ->
                "百度以「安全策略」拒绝了这次刷新：这个 refresh_token 已经被别的程序刷新过" +
                    "（最常见就是 AList 也在用同一份授权，两边互相抢），" +
                    "或者短时间内刷新太频繁被临时限流。" +
                    "处理办法：先停用 AList 里的百度挂载，等几分钟再试；还不行就重新授权。"
            "invalid_request" ->
                "百度说这次请求的参数不合法。最常见是凭据不完整 —— refresh_token 应该是一串" +
                    "80 多个字符、以 122. 开头的长串；如果你手上只有页面上那串 32 位授权码，" +
                    "直接填也完全可以，App 会自己认出来并换成正式凭证。"
            "expired_token" -> "授权已过期，需要重新授权。"
            else -> "授权失败：$error" + if (desc.isNotBlank()) "（$desc）" else ""
        }
    }

    /** 从 mediainfo 响应里取直链，拿不到给 null。 */
    fun parseDlink(json: String): String? {
        val root = parseJsonOrNull(json).asObjOrNull() ?: return null
        return root["info"].asObjOrNull()?.str("dlink")
    }

    fun mediaError(json: String): String? {
        val root = parseJsonOrNull(json).asObjOrNull() ?: return "网盘返回了无法解析的内容"
        val errno = root.int("errno") ?: return null
        return if (errno == 0) null else errorMessage(errno)
    }

    // ---------------- Track 的 id / 播放地址编码 ----------------

    fun trackId(fsId: Long): String = "baidu:$fsId"

    /**
     * 播放地址用自定义 scheme 表示「还没换成真实直链的百度文件」。
     *
     * 直链 8 小时就过期，不能写进歌单快照里，否则第二天点开全是 403。
     * 所以列表里只存 `baidu://<fs_id>/<编码后的路径>`，播放时由播放器现换。
     */
    fun streamUrl(fsId: Long, path: String): String = "baidu://$fsId/${UrlCodec.encode(path)}"

    data class StreamRef(val fsId: Long, val path: String)

    fun parseStreamUrl(url: String): StreamRef? {
        if (!url.startsWith("baidu://")) return null
        val rest = url.removePrefix("baidu://")
        val slash = rest.indexOf('/')
        if (slash <= 0) return null
        val fsId = rest.substring(0, slash).toLongOrNull() ?: return null
        val encoded = rest.substring(slash + 1)
        if (encoded.isBlank()) return null
        val path = runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrNull() ?: return null
        return StreamRef(fsId, path)
    }

    /**
     * 从文件名拆出「歌手 - 歌名」。
     *
     * 网盘里的文件名比本地库脏得多：常见 `歌手-歌名.mp3`、`歌手 - 歌名`、
     * `01.歌名`、`(串烧)歌手-歌名[www.xxx.com]`，还夹着下载站的推广后缀。
     * 拆不准不影响播放，但直接影响歌词能不能匹配上，所以尽量把噪音清掉。
     */
    fun splitFileName(rawName: String): Pair<String, String> {
        var name = rawName.substringBeforeLast('.', rawName)
        // 去掉下载站后缀： [www.xxx.com]、【xx音乐】、(更多资源关注xxx)
        name = name
            .replace(Regex("""\[[^]]*]"""), " ")
            .replace(Regex("""【[^】]*】"""), " ")
            .replace(Regex("""(?i)[（(][^)）]*(www\.|\.com|\.net|\.cn|关注|公众号|下载)[^)）]*[)）]"""), " ")
            .replace(Regex("""^\s*\d{1,3}[.、\s]\s*"""), "")
            .replace('_', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()

        val separator = listOf(" - ", "-", "–", "—").firstOrNull { name.contains(it) }
        if (separator != null) {
            val index = name.indexOf(separator)
            val artist = name.substring(0, index).trim()
            val title = name.substring(index + separator.length).trim()
            // 「标题-副标题」这种没有歌手的情况：右边太短就整体当歌名
            if (artist.isNotBlank() && title.length >= 2) return artist to title
        }
        return "" to name.ifBlank { rawName }
    }
}
