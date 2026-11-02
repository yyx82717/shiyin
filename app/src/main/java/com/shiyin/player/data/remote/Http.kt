package com.shiyin.player.data.remote

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 服务端返回非 2xx。
 *
 * 原来只抛「服务器返回 400」，把响应体丢了 —— 而百度的原因全写在 body 里
 * （`{"error":"Trigger security policy"}`、`{"errno":-6}`）。用户只看到一串数字，
 * 既不知道是自己 token 失效还是参数写错，也没法反馈给我定位。
 * 所以这里把 body 一起带出来，由上层翻译成人话。
 */
class HttpStatusException(val code: Int, val body: String) : IOException("服务器返回 $code")

/** 全局共享的 HTTP 客户端：播放器下载音频也复用它，避免重复建连接池。 */
object Http {

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
        headers.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw HttpStatusException(response.code, body)
            if (body.isEmpty()) throw IOException("响应为空")
            return body
        }
    }

    /**
     * 短超时的 GET，给歌词这类「拿不到就算了」的请求用。
     * 全局 35 秒超时套在歌词上，网络不好时界面会白等半分钟，体验很差。
     */
    fun getQuick(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()
        quickClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw HttpStatusException(response.code, body)
            if (body.isEmpty()) throw IOException("响应为空")
            return body
        }
    }

    /**
     * 取纯文本（`.lrc` 这类），带 GBK 回退。
     *
     * 不能用 `body.string()`：OkHttp 按响应头里的 charset 解码，而网盘给 `.lrc`
     * 常常只回 `text/plain` 不带 charset，默认按 UTF-8 解。国内歌词文件相当一部分是 GBK，
     * 硬解出来是一串 `�`，等于没歌词。这里先按 UTF-8 解，出现替换字符再退 GBK。
     */
    /**
     * 只取前 [maxBytes] 个字节（用 Range 请求）。
     *
     * 给「音频体检」用：判断一个 .wav 到底是不是 PCM，读几 KB 就够，
     * 没必要为了看一眼文件头把几十兆的音频拉下来。
     */
    fun headBytes(url: String, maxBytes: Int, headers: Map<String, String> = emptyMap()): ByteArray? {
        val builder = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-${maxBytes - 1}")
            .header("User-Agent", USER_AGENT)
        headers.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val bytes = response.body?.bytes() ?: return null
            return if (bytes.size > maxBytes) bytes.copyOf(maxBytes) else bytes
        }
    }

    fun getText(url: String, headers: Map<String, String> = emptyMap()): String {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "*/*")
            .header("User-Agent", USER_AGENT)
        headers.forEach { (k, v) -> builder.header(k, v) }

        quickClient.newCall(builder.build()).execute().use { response ->
            val bytes = response.body?.bytes() ?: throw IOException("响应为空")
            if (!response.isSuccessful) {
                throw HttpStatusException(response.code, String(bytes, Charsets.UTF_8).take(500))
            }
            val utf8 = String(bytes, Charsets.UTF_8)
            if (!utf8.contains('\uFFFD')) return utf8
            return runCatching { String(bytes, charset("GBK")) }.getOrDefault(utf8)
        }
    }

    /** 复用连接池，避免每次请求新建 client。 */
    private val quickClient: OkHttpClient by lazy {
        client.newBuilder()
            .callTimeout(8, TimeUnit.SECONDS)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    /**
     * WebDAV 的目录列举（PROPFIND）。
     *
     * depth=1 只列一层，调用方自己控制递归深度——很多服务器默认禁止 Depth: infinity，
     * 直接发 infinity 会拿到 403。
     */
    fun propfind(
        url: String,
        user: String? = null,
        password: String = "",
        depth: String = "1",
    ): String {
        val body = PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType())
        val builder = Request.Builder()
            .url(url)
            .method("PROPFIND", body)
            .header("Depth", depth)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
        if (!user.isNullOrBlank()) {
            builder.header("Authorization", Credentials.basic(user, password))
        }
        client.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw HttpStatusException(response.code, body.take(500))
            return body
        }
    }

    /** 基础认证头，播放器拉 WebDAV 音频时要带上。 */
    fun basicAuth(user: String, password: String): String = Credentials.basic(user, password)

    const val USER_AGENT = "ShiYin/1.0 (Android)"

    private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:displayname/>
    <d:getcontentlength/>
    <d:resourcetype/>
  </d:prop>
</d:propfind>"""
}
