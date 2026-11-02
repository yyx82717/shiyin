package com.shiyin.player.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateInfoTest {

    @Test
    fun `解析完整 update json`() {
        val json = """
        {
            "versionCode": 15,
            "versionName": "1.6.0",
            "apk": "app-release.apk",
            "notes": "修复若干 bug",
            "size": 15728640,
            "sha256": "abc123"
        }
        """.trimIndent()
        val info = AppJson.decodeFromString(UpdateInfo.serializer(), json)
        assertEquals(15, info.versionCode)
        assertEquals("1.6.0", info.versionName)
        assertEquals("app-release.apk", info.apk)
        assertEquals("修复若干 bug", info.notes)
        assertEquals(15728640L, info.size)
        assertEquals("abc123", info.sha256)
        assertTrue(info.valid)
    }

    @Test
    fun `缺字段用默认值，且缺少 apk 视为无效`() {
        val json = """{"versionCode": 15}"""
        val info = AppJson.decodeFromString(UpdateInfo.serializer(), json)
        assertEquals("", info.apk)
        assertEquals("", info.notes)
        assertFalse(info.valid) // 没有 apk 地址，没法下载，清单无效
    }

    @Test
    fun `相对 apk 拼到更新源目录下`() {
        assertEquals(
            "https://x.com/shiyin/app.apk",
            UpdateInfo.resolveApkUrl("https://x.com/shiyin/", "app.apk"),
        )
        assertEquals(
            "https://x.com/shiyin/app.apk",
            UpdateInfo.resolveApkUrl("https://x.com/shiyin", "/app.apk"),
        )
    }

    @Test
    fun `绝对 apk 地址原样返回`() {
        val abs = "https://cdn.example.com/releases/app.apk"
        assertEquals(abs, UpdateInfo.resolveApkUrl("https://x.com/shiyin", abs))
    }
}
