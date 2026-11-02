package com.shiyin.player.data.model

import kotlinx.serialization.Serializable

/**
 * 一条已下载到本机的曲目。
 *
 * 文件放在应用私有目录（filesDir/downloads），不需要任何存储权限，
 * 卸载 App 时随应用一起清掉。
 */
@Serializable
data class DownloadEntry(
    val track: Track,
    val filePath: String,
    val sizeBytes: Long = 0L,
    val at: Long = 0L,
)
