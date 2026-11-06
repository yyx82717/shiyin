package com.shiyin.player.data.update

import com.shiyin.player.data.model.UpdateInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    private val json = """
    {"versionCode":15,"versionName":"1.6.0","apk":"app.apk","notes":"n","size":100,"sha256":"s"}
    """.trimIndent()

    @Test
    fun `fetch 成功解析`() {
        val info = UpdateChecker.fetch("https://x.com/shiyin") { json }
        assertEquals(15, info?.versionCode)
        assertEquals("app.apk", info?.apk)
    }

    @Test
    fun `目录形式自动补 update_json`() {
        var hit: String? = null
        UpdateChecker.fetch("https://x.com/shiyin/") { hit = it; json }
        assertEquals("https://x.com/shiyin/update.json", hit)
    }

    @Test
    fun `完整文件路径不再重复拼接`() {
        var hit: String? = null
        UpdateChecker.fetch("https://x.com/shiyin/update.json") { hit = it; json }
        assertEquals("https://x.com/shiyin/update.json", hit)
    }

    @Test
    fun `空 baseUrl 直接返回 null`() {
        assertNull(UpdateChecker.fetch("   ") { json })
    }

    @Test
    fun `fetcher 抛异常时返回 null 不崩溃`() {
        assertNull(UpdateChecker.fetch("https://x.com") { throw RuntimeException("网络断了") })
    }

    @Test
    fun `坏 json 返回 null`() {
        assertNull(UpdateChecker.fetch("https://x.com") { "这不是 json" })
    }

    @Test
    fun `缺少 apk 字段视为无效清单返回 null`() {
        val bad = """{"versionCode":15}"""
        assertNull(UpdateChecker.fetch("https://x.com") { bad })
    }

    @Test
    fun `isNewer 只看 versionCode`() {
        val info = UpdateInfo(versionCode = 15, apk = "a.apk")
        assertTrue(UpdateChecker.isNewer(info, 14))
        assertFalse(UpdateChecker.isNewer(info, 15))
        assertFalse(UpdateChecker.isNewer(info, 16))
    }
}
