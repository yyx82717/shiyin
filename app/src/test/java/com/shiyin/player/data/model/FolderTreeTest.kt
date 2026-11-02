package com.shiyin.player.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderTreeTest {

    private fun track(
        id: String,
        folder: String? = null,
        url: String = "http://host/a/$id.mp3",
        source: String = "webdav",
    ) = Track(
        id = id,
        title = id,
        artist = "未知艺人",
        streamUrl = url,
        source = source,
        folder = folder,
    )

    @Test
    fun `统一斜杠并去掉卷标前缀`() {
        assertEquals("Music/周杰伦", FolderTree.normalize("/storage/emulated/0/Music/周杰伦/"))
        assertEquals("Music/a", FolderTree.normalize("\\\\sdcard\\Music\\a"))
        assertEquals("a/b", FolderTree.normalize("a//b/"))
        assertEquals("", FolderTree.normalize(null))
        assertEquals("", FolderTree.normalize("   "))
    }

    @Test
    fun `建树把歌放到各自的目录里`() {
        val tracks = listOf(
            track("a1", folder = "Music/周杰伦/范特西"),
            track("a2", folder = "Music/周杰伦/范特西"),
            track("b1", folder = "Music/周杰伦/叶惠美"),
            track("c1", folder = "Music/陈奕迅"),
            track("d1", folder = null, url = "http://host/root.mp3"),
        )
        val root = FolderTree.build(tracks)

        assertEquals(5, root.totalCount)
        // 根目录下只有一首没有目录信息的歌
        assertEquals(1, root.direct.size)
        assertEquals(1, root.children.size)

        val music = root.children.single()
        assertEquals("Music", music.name)
        assertEquals(4, music.totalCount)
        assertEquals(2, music.children.size)

        val jay = music.children.first { it.name == "周杰伦" }
        assertEquals(0, jay.direct.size)
        assertEquals(3, jay.totalCount)
        assertEquals(2, jay.children.first { it.name == "范特西" }.direct.size)
    }

    @Test
    fun `路径中间缺目录也能补齐`() {
        // 只有一个深层路径时，中间的 Music/周杰伦 也要作为可点的目录出现
        val root = FolderTree.build(listOf(track("a", folder = "Music/周杰伦/范特西")))
        val music = root.children.single()
        assertEquals("Music", music.name)
        val jay = music.children.single()
        assertEquals("周杰伦", jay.name)
        assertEquals("范特西", jay.children.single().name)
    }

    @Test
    fun `按路径取节点`() {
        val root = FolderTree.build(
            listOf(
                track("a", folder = "Music/周杰伦/范特西"),
                track("b", folder = "Music/陈奕迅"),
            ),
        )
        assertEquals("范特西", FolderTree.at(root, "Music/周杰伦/范特西").name)
        assertEquals("范特西", FolderTree.at(root, "/Music/周杰伦/范特西/").name)
        // 取不到就退回根，界面不会白屏
        assertEquals(root.path, FolderTree.at(root, "不存在的/路径").path)
        assertEquals(root.path, FolderTree.at(root, "").path)
    }

    @Test
    fun `面包屑逐级给出完整路径`() {
        val crumbs = FolderTree.breadcrumbs("/Music/周杰伦/范特西/")
        assertEquals(listOf("Music", "Music/周杰伦", "Music/周杰伦/范特西"), crumbs.map { it.first })
        assertEquals(listOf("Music", "周杰伦", "范特西"), crumbs.map { it.second })
        assertTrue(FolderTree.breadcrumbs("").isEmpty())
    }

    @Test
    fun `老快照没有目录字段时从地址里猜`() {
        assertEquals(
            "dav/music/a",
            FolderTree.folderOf(track("x", folder = null, url = "http://192.168.8.192:5244/dav/music/a/x.mp3")),
        )
        // 百度地址的第一段是 fs_id，不是目录
        assertEquals(
            "我的音乐/周杰伦",
            FolderTree.folderOf(
                track("y", folder = null, url = "baidu://123456/我的音乐/周杰伦/y.mp3", source = "baidu"),
            ),
        )
        // 根目录上的文件没有父目录
        assertEquals("", FolderTree.folderOf(track("z", folder = null, url = "http://host/z.mp3")))
    }

    @Test
    fun `显式目录优先于地址猜测`() {
        val t = track("x", folder = "真正/目录", url = "http://host/别的/x.mp3")
        assertEquals("真正/目录", FolderTree.folderOf(t))
    }

    @Test
    fun `同一目录内保持传入顺序`() {
        val tracks = listOf(
            track("c", folder = "a"),
            track("b", folder = "a"),
            track("a", folder = "a"),
        )
        val node = FolderTree.build(tracks).children.single()
        assertEquals(listOf("c", "b", "a"), node.direct.map { it.id })
    }

    @Test
    fun `子目录按名字排序而不是按出现顺序`() {
        val root = FolderTree.build(
            listOf(
                track("1", folder = "z"),
                track("2", folder = "a"),
                track("3", folder = "m"),
            ),
        )
        assertEquals(listOf("a", "m", "z"), root.children.map { it.name })
    }

    @Test
    fun `空曲库时根节点为空`() {
        val root = FolderTree.build(emptyList())
        assertEquals(0, root.totalCount)
        assertTrue(root.children.isEmpty())
        assertNull(root.direct.firstOrNull())
    }
}
