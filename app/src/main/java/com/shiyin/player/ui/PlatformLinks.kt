package com.shiyin.player.ui

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 「全平台找歌」的合法形态。
 *
 * 我们不提取任何平台的音频文件，只把关键词交给平台的官方页面/官方 App。
 * 曲库里没有的歌，点一下就能跳到对应平台去听——体验上覆盖了「全平台」，
 * 但版权内容仍然在官方渠道里播放。
 */
object PlatformLinks {

    data class Platform(val label: String, val buildUrl: (String) -> String)

    val platforms = listOf(
        Platform("QQ音乐") { query -> "https://y.qq.com/n/ryqq/search?w=" + Uri.encode(query) },
        Platform("网易云") { query -> "https://music.163.com/#/search/m/?s=" + Uri.encode(query) },
        Platform("酷狗") { query -> "https://www.kugou.com/yy/html/search.html#searchType=song&searchKeyWord=" + Uri.encode(query) },
    )

    fun open(context: Context, platform: Platform, query: String) {
        if (query.isBlank()) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(platform.buildUrl(query)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
