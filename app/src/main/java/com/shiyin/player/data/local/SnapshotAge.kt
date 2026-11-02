package com.shiyin.player.data.local

/**
 * 曲库快照的「新鲜度」判断，纯函数，方便单元测试。
 *
 * 本地和网盘都靠它决定：这次打开页面到底要不要重新扫一遍。
 * 以前网盘只靠内存缓存，进程一被杀就没了，冷启动必然重爬十几秒；
 * 现在有落盘快照 + 这个过期判断，绝大多数打开都是零网络请求。
 */
object SnapshotAge {

    /** savedAt 为 0（从没扫过）永远算过期。 */
    fun isFresh(savedAt: Long, now: Long, ttlMs: Long): Boolean {
        if (savedAt <= 0L) return false
        // 时钟被往回调过（换时区 / 手动改时间）时，负数年龄按「刚扫过」处理，
        // 否则会陷入「看起来永远过期」的反复扫描。
        val age = now - savedAt
        return age < ttlMs
    }

    /** 「刚刚扫描 / 12 分钟前扫描 / 3 小时前扫描」，给界面显示。 */
    fun describe(savedAt: Long, now: Long): String {
        if (savedAt <= 0L) return "未扫描"
        val seconds = (now - savedAt) / 1000L
        if (seconds < 60L) return "刚刚扫描"
        val minutes = seconds / 60L
        return when {
            minutes < 60L -> "$minutes 分钟前扫描"
            minutes < 24 * 60L -> "${minutes / 60L} 小时前扫描"
            else -> "${minutes / (24 * 60L)} 天前扫描"
        }
    }
}
