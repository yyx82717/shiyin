package com.shiyin.player.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 快照新鲜度判断。
 *
 * 这段逻辑决定「打开网盘页要不要重新爬一遍」，判错的代价很直观：
 * 判太严 → 每次打开都等十几秒；判太松 → 用户以为歌单是新的，其实缺了刚加的歌。
 */
class SnapshotAgeTest {

    private val ttl = 30 * 60_000L
    private val now = 1_000_000_000_000L

    @Test
    fun `从没扫过算过期`() {
        assertFalse(SnapshotAge.isFresh(0L, now, ttl))
    }

    @Test
    fun `刚扫完是新鲜的`() {
        assertTrue(SnapshotAge.isFresh(now - 1_000L, now, ttl))
    }

    @Test
    fun `刚好卡在阈值上算过期`() {
        // 边界用严格小于：正好 30 分钟就重扫，避免「永远不刷新」
        assertFalse(SnapshotAge.isFresh(now - ttl, now, ttl))
    }

    @Test
    fun `差一毫秒还没过期`() {
        assertTrue(SnapshotAge.isFresh(now - ttl + 1, now, ttl))
    }

    @Test
    fun `系统时间被往回调过也不无限重扫`() {
        // savedAt 在未来（改过时间），年龄是负数，按新鲜处理
        assertTrue(SnapshotAge.isFresh(now + 60_000L, now, ttl))
    }

    @Test
    fun `描述未扫描`() {
        assertEquals("未扫描", SnapshotAge.describe(0L, now))
    }

    @Test
    fun `描述刚刚`() {
        assertEquals("刚刚扫描", SnapshotAge.describe(now - 30_000L, now))
    }

    @Test
    fun `描述分钟`() {
        assertEquals("12 分钟前扫描", SnapshotAge.describe(now - 12 * 60_000L, now))
    }

    @Test
    fun `描述小时`() {
        assertEquals("3 小时前扫描", SnapshotAge.describe(now - 3 * 60 * 60_000L, now))
    }

    @Test
    fun `描述天`() {
        assertEquals("2 天前扫描", SnapshotAge.describe(now - 2 * 24 * 60 * 60_000L, now))
    }
}
