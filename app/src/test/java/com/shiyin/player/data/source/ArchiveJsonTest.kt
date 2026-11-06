package com.shiyin.player.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArchiveJsonTest {

    @Test
    fun `parseArchiveIdentifiers extracts ids up to cap`() {
        val body = """{
            "response": {
                "docs": [
                    {"identifier": "test_123", "title": "Song A"},
                    {"identifier": "abc_xyz", "title": "Song B"},
                    {"identifier": "third_one", "title": "Song C"}
                ]
            }
        }"""
        val expected: List<String> = listOf("test_123", "abc_xyz", "third_one")
        val actual: List<String> = parseArchiveIdentifiers(body, 12)
        assertEquals(expected, actual)
    }

    @Test
    fun `parseArchiveIdentifiers returns empty on garbage`() {
        assertEquals(emptyList<String>(), parseArchiveIdentifiers("not json", 12))
        assertEquals(emptyList<String>(), parseArchiveIdentifiers("{}", 12))
    }

    @Test
    fun `parseArchiveTrack picks first audio file`() {
        val body = """{
            "files": [
                {"name": "cover.jpg", "format": "Image"},
                {"name": "track.mp3", "format": "MP3"},
                {"name": "track.ogg", "format": "Ogg Vorbis"}
            ],
            "metadata": {
                "title": "My Song",
                "creator": "Some Artist"
            }
        }"""
        val track = parseArchiveTrack(body, "abc_xyz")
        assertEquals("archive:abc_xyz", track!!.id)
        assertEquals("My Song", track.title)
        assertEquals("Some Artist", track.artist)
        assertEquals("https://archive.org/download/abc_xyz/track.mp3", track.streamUrl)
        assertEquals("archive", track.source)
    }

    @Test
    fun `parseArchiveTrack handles creator array`() {
        val body = """{
            "files": [{"name": "a.mp3", "format": "MP3"}],
            "metadata": {"title": "Song", "creator": ["First", "Second"]}
        }"""
        assertEquals("First", parseArchiveTrack(body, "id1")!!.artist)
    }

    @Test
    fun `parseArchiveTrack falls back when no audio`() {
        val body = """{
            "files": [{"name": "cover.jpg", "format": "Image"}],
            "metadata": {"title": "Song"}
        }"""
        assertNull(parseArchiveTrack(body, "id2"))
    }
}
