package com.shiyin.player.data.remote

import java.net.URLEncoder

/**
 * URL 转义。
 *
 * 这里刻意不用 `android.net.Uri.encode`：一旦拼 URL 的逻辑挂在 Android 类上，
 * 就只能跑在设备/模拟器里，没法用 JVM 单元测试验证参数拼得对不对。
 * `URLEncoder` 是纯 JDK 实现，行为确定，且在 Android 上同样可用。
 *
 * 唯一差异是 URLEncoder 把空格编成 `+`（表单语义），而路径段里 `+` 不代表空格，
 * 所以统一换成 `%20`，查询串和路径两种情况都正确。
 */
internal object UrlCodec {

    fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
