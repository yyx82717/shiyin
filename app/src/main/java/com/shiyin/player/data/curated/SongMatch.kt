package com.shiyin.player.data.curated

import com.shiyin.player.data.model.Track

/**
 * 精选专辑与本地曲库的匹配。
 *
 * 精选专辑只有「歌名 + 艺人」，用户手机里的文件名却千奇百怪：
 * `晴天.mp3`、`周杰伦 - 晴天 (Live).flac`、`07.晴天【高音质】`……
 * 这里把两边都归一化再比，命中率高很多，也不会把「晴天」和「晴天娃娃」搞混。
 *
 * 全部是纯函数，方便直接上 JVM 单元测试。
 */
object SongMatch {

    private val BRACKET = Regex("""[(\[（【《<][^)\]）】》>]*[)\]）】》>]""")
    private val NOISE = Regex(
        """\b(feat|ft|remix|live|cover|version)\b|伴奏|纯音乐|片段|铃声|翻自|高音质|无损|完整版|超清|抖音""",
        RegexOption.IGNORE_CASE,
    )
    private val PUNCT = Regex("""[\s\-_·・.,，。、!！?？:：;；'"“”‘’/\\|&+~#@*()（）\[\]【】《》<>]""")

    /** 歌名归一化：去括号补充说明、去「伴奏/Live/feat」这类标记、去标点空格、转小写。 */
    fun normalizeTitle(raw: String): String {
        var s = toHalfWidth(raw).lowercase()
        // 括号内容通常是版本说明，(Live) / （伴奏） / 【高音质】
        s = BRACKET.replace(s, " ")
        s = s.replace(Regex("""\.[a-z0-9]{2,4}$"""), " ") // 残留的扩展名
        s = NOISE.replace(s, " ")
        s = PUNCT.replace(s, "")
        return s.trim()
    }

    /** 艺人归一化：一首歌常有「A & B」「A/B」「A feat. B」，只取第一个，避免因为合作艺人写法不同而漏匹配。 */
    fun normalizeArtist(raw: String): String {
        var s = toHalfWidth(raw).lowercase()
        s = s.split("&", "/", "、", ",", "，", "feat.", "ft.").firstOrNull().orEmpty()
        s = PUNCT.replace(s, "")
        return s.trim()
    }

    fun toHalfWidth(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            sb.append(
                when {
                    ch == '\u3000' -> ' '
                    ch in '！'..'～' -> (ch - 0xFEE0)
                    else -> ch
                },
            )
        }
        return sb.toString()
    }

    /** 建索引：归一化歌名 → 本地曲目，避免每张专辑都全表扫一遍。 */
    fun buildIndex(tracks: List<Track>): Map<String, List<Track>> {
        val map = HashMap<String, MutableList<Track>>()
        for (track in tracks) {
            for (key in titleKeys(track.title)) {
                map.getOrPut(key) { mutableListOf() }.add(track)
            }
        }
        return map.filterKeys { it.isNotBlank() }
    }

    /**
     * 一个文件名可能对应多个可比的 key。
     *
     * 手机里的文件经常是「周杰伦 - 晴天.mp3」，而精选专辑里只有「晴天」，
     * 所以除了整串归一化，再把「-」后面那段单独当一个 key。
     */
    internal fun titleKeys(raw: String): Set<String> {
        val keys = LinkedHashSet<String>()
        val full = normalizeTitle(raw)
        if (full.isNotBlank()) keys += full

        val tail = raw.substringAfterLast(" - ", "").ifBlank { raw.substringAfterLast('-', "") }
        if (tail.isNotBlank()) {
            val tailKey = normalizeTitle(tail)
            if (tailKey.isNotBlank() && tailKey != full) keys += tailKey
        }
        return keys
    }

    /**
     * 找一首歌在本地曲库里的对应文件。
     *
     * 先要求在歌名命中的人里找艺人也能对上的；都对不上时退一步接受歌名相同，
     * 因为「同名不同人」在现场版/翻唱里很常见，而用户点开专辑就是想听这首歌。
     */
    fun find(song: CuratedSong, index: Map<String, List<Track>>): Track? {
        val key = normalizeTitle(song.title)
        if (key.isBlank()) return null
        val candidates = index[key] ?: return null
        if (candidates.size == 1) return candidates.first()

        val artistKey = normalizeArtist(song.artist)
        if (artistKey.isNotBlank()) {
            candidates.firstOrNull {
                val a = normalizeArtist(it.artist)
                a.isNotBlank() && (a.contains(artistKey) || artistKey.contains(a))
            }?.let { return it }
        }
        return candidates.first()
    }
}
