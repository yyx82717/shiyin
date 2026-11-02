package com.shiyin.player.data.remote

import java.security.MessageDigest

/** MD5 十六进制串。Subsonic 的 token 认证和 WebDAV 条目 ID 都用它。 */
internal fun md5Hex(input: String): String {
    val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
    val sb = StringBuilder(bytes.size * 2)
    for (b in bytes) {
        val v = b.toInt() and 0xFF
        if (v < 0x10) sb.append('0')
        sb.append(Integer.toHexString(v))
    }
    return sb.toString()
}
