package com.shiyin.player.data.curated

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CuratedCatalogTest {

    @Test
    fun `专辑 id 唯一且数量够用作「风格专辑」`() {
        val ids = CuratedCatalog.albums.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue("至少 8 张风格专辑，实际 ${ids.size}", ids.size >= 8)
    }

    @Test
    fun `每张专辑都有名字、简介和足够的曲目`() {
        CuratedCatalog.albums.forEach { album ->
            assertTrue(album.name.isNotBlank())
            assertTrue(album.tagline.isNotBlank())
            assertTrue(album.blurb.isNotBlank())
            assertTrue("${album.name} 曲目太少", album.songs.size >= 8)
        }
    }

    @Test
    fun `同一张专辑内不出现重复曲目`() {
        CuratedCatalog.albums.forEach { album ->
            val keys = album.songs.map { SongMatch.normalizeTitle(it.title) to SongMatch.normalizeArtist(it.artist) }
            assertEquals("${album.name} 有重复曲目", keys.size, keys.toSet().size)
        }
    }

    @Test
    fun `曲目与艺人都不为空`() {
        CuratedCatalog.albums.flatMap { it.songs }.forEach { song ->
            assertTrue("歌名不能为空", song.title.isNotBlank())
            assertTrue("${song.title} 缺少艺人", song.artist.isNotBlank())
        }
    }

    @Test
    fun `总曲目数与按 id 查找可用`() {
        assertEquals(CuratedCatalog.albums.sumOf { it.songs.size }, CuratedCatalog.totalSongs)
        val first = CuratedCatalog.albums.first()
        assertEquals(first, CuratedCatalog.byId(first.id))
        assertEquals(null, CuratedCatalog.byId("不存在的专辑"))
    }

    @Test
    fun `每张专辑都能被归一化后的歌名索引到`() {
        // 歌名归一化后不能是空串，否则永远匹配不到本地文件
        CuratedCatalog.albums.flatMap { it.songs }.forEach { song ->
            assertTrue("${song.title} 归一化后为空", SongMatch.normalizeTitle(song.title).isNotBlank())
        }
    }
}
