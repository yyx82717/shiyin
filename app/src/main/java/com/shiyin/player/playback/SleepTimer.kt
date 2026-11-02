package com.shiyin.player.playback

/**
 * 睡眠定时。
 *
 * 为什么放在服务层而不是 ViewModel：定时是「播放」的属性，不是界面的属性。
 * 用户锁屏听歌、从最近任务里划掉界面之后，ViewModel 会没，歌却还在放；
 * 定时要是跟着界面走，就会出现「设了 30 分钟，结果一整夜都在响」。
 *
 * 同一个进程里服务与界面直接共享这个对象，不需要跨进程通信。
 */
object SleepTimer {

    /** 到期时间戳（毫秒）。0 表示没设定时。 */
    @Volatile
    var endAtMs: Long = 0L
        private set

    /** 「播完当前这首就停」。 */
    @Volatile
    var stopAtTrackEnd: Boolean = false
        private set

    val active: Boolean get() = endAtMs > 0L || stopAtTrackEnd

    fun set(minutes: Int) {
        stopAtTrackEnd = false
        endAtMs = if (minutes <= 0) 0L else System.currentTimeMillis() + minutes * 60_000L
    }

    fun setStopAtTrackEnd(enabled: Boolean) {
        endAtMs = 0L
        stopAtTrackEnd = enabled
    }

    fun clear() {
        endAtMs = 0L
        stopAtTrackEnd = false
    }

    /** 剩余毫秒；没开定时返回 -1。 */
    fun remainingMs(now: Long = System.currentTimeMillis()): Long =
        if (endAtMs <= 0L) -1L else (endAtMs - now).coerceAtLeast(0L)
}
