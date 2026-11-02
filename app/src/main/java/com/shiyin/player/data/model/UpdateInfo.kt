package com.shiyin.player.data.model

import kotlinx.serialization.Serializable

/**
 * 更新清单。放在「更新源地址」这个目录 URL 下的 `update.json` 里。
 *
 * 设计取舍：整个自动更新就靠这一个明文 JSON 驱动，不引入任何中心服务器。
 * 小圈子分发本来就是「你自己控制一个能 http 访问的目录」，把 `update.json`
 * 和新版 apk 放进去，应用填那个目录地址即可。字段都给了默认值，老格式不崩。
 *
 * 约定：
 * - `apk` 可以是相对路径（相对更新源目录，如 `app-release.apk`）或完整 http(s) URL。
 * - `sha256` 可选。填了就强校验，下载完对不上直接拒装；不填则只校验能装上。
 *   小圈子图省事可以不算，但强烈建议填上 —— 这是挡住「源被篡改投毒」的最后一道闸。
 */
@Serializable
data class UpdateInfo(
    /** 必须大于当前装的 versionCode 才算「有更新」。这是唯一判断依据，不比版本名。 */
    val versionCode: Int = 0,
    /** 给人看的版本号，如 `1.6.0`。 */
    val versionName: String = "",
    /** 新包文件名或完整地址。 */
    val apk: String = "",
    /** 更新说明，展示在弹窗里。允许是多行文本。 */
    val notes: String = "",
    /** 包体字节数，可选，仅用于在弹窗里显示「约 15 MB」。 */
    val size: Long? = null,
    /** 十六进制 SHA-256，可选但强烈建议。 */
    val sha256: String? = null,
) {
    /** 这份清单本身合不合法：至少得有能比大小的版本码和一个能下载的包地址。 */
    val valid: Boolean
        get() = versionCode > 0 && apk.isNotBlank()

    companion object {
        /**
         * 把 `apk` 字段解析成最终下载地址。
         *
         * 已经是 http(s) 绝对地址就原样返回；否则拼到更新源目录下。
         * 两端斜杠都做了归一，用户填 `http://x/` + `apk/app.apk` 不会拼出双斜杠。
         */
        fun resolveApkUrl(baseUrl: String, apk: String): String {
            val trimmed = apk.trim()
            if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
            ) {
                return trimmed
            }
            val base = baseUrl.trim().trimEnd('/')
            val file = trimmed.trimStart('/')
            return if (base.isEmpty()) file else "$base/$file"
        }
    }
}
