package com.shiyin.player.data.model

import kotlinx.serialization.Serializable

/**
 * 上次退出时的播放现场。
 *
 * 只存「够还原」的部分：队列、当前第几首、听到哪儿、随机与循环状态。
 * 不存播放/暂停 —— 重新打开应用不该自己出声，这是所有播放器的默契。
 */
@Serializable
data class PlaySession(
    val tracks: List<Track> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0L,
    val shuffle: Boolean = false,
    /** Media3 的 `Player.REPEAT_MODE_*`，原样存整数，避免多一层映射。 */
    val repeatMode: Int = 0,
    /** 写入时间，用来判断这份现场是不是已经过期。 */
    val savedAt: Long = 0L,
) {
    /** 队列为空、索引越界、或者太久以前的现场，都不值得还原。 */
    fun restorable(now: Long, maxAgeMs: Long = MAX_AGE_MS): Boolean {
        if (tracks.isEmpty()) return false
        if (index !in tracks.indices) return false
        if (savedAt <= 0L) return false
        return now - savedAt in 0..maxAgeMs
    }

    companion object {
        /** 超过这个时间的现场就当没存过：半个月前的进度接上只会让人莫名其妙。 */
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

        /** 队列快照的上限。再长就不是「继续听」，而是把整个曲库写进偏好设置。 */
        const val MAX_TRACKS = 300
    }
}
