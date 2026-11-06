package com.shiyin.player.data.update

import com.shiyin.player.data.model.UpdateInfo
import com.shiyin.player.data.model.AppJson
import com.shiyin.player.data.remote.Http

/**
 * 负责「去更新源把 update.json 拉回来并判断要不要更新」。
 *
 * 网络层抽成 `fetcher` 参数，生产环境用 [Http.get]，单元测试里传一个假函数 ——
 * 否则单测得真去联网。这是这个项目里一贯的做法（Pinyin 也把音译器抽成可注入）。
 */
object UpdateChecker {

    /**
     * 拉取并解析更新清单。
     *
     * @param baseUrl 更新源目录，如 `https://example.com/shiyin`。会自动补上 `/update.json`。
     * @param fetcher 真正发请求的函数，默认走 OkHttp（同步）。返回 JSON 文本。
     *         抽成参数才能单元测试——否则单测得真去联网。
     * @return 解析成功的 [UpdateInfo]；网络失败、JSON 损坏、或清单本身不合法（缺字段）都返回 null。
     *         调用方据此区分「没有更新」和「查不了」，前者静默，后者仅在手动检查时提示。
     */
    fun fetch(
        baseUrl: String,
        fetcher: (String) -> String = { Http.get(it) },
    ): UpdateInfo? {
        val trimmed = baseUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        // 兼容两种填法，避免「填了完整文件路径却被再拼一次 /update.json 导致 404」：
        //   - 目录（推荐）：https://example.com/shiyin            → 补 /update.json
        //   - 完整文件：    https://example.com/shiyin/update.json → 直接用
        val url = if (trimmed.endsWith(".json", ignoreCase = true)) trimmed
                  else "$trimmed/update.json"
        val raw = runCatching { fetcher(url) }.getOrNull() ?: return null
        val info = runCatching { AppJson.decodeFromString(UpdateInfo.serializer(), raw) }.getOrNull()
            ?: return null
        return info.takeIf { it.valid }
    }

    /** 远程版本是否比本地新。只看 versionCode，版本名只是给人看的。 */
    fun isNewer(info: UpdateInfo, currentVersionCode: Int): Boolean =
        info.versionCode > currentVersionCode
}
