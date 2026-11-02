package com.shiyin.player.data.search

import android.icu.text.Transliterator
import android.os.Build
import androidx.annotation.RequiresApi
import java.text.Normalizer
import java.util.Locale

/**
 * 汉字转拼音，只为了解决一件事：**用键盘打中文歌名**。
 *
 * 中文用户搜歌的习惯是打首字母（`zjl` → 周杰伦，`dfp` → 东风破），
 * 但输入法在搜索框里打字母出来就是字母，`contains` 永远匹配不到汉字。
 * 同类播放器（椒盐、Retro、Musicolet）都做了这一层，拾音之前只能老老实实切输入法。
 *
 * 转换用 Android 自带的 ICU 音译器（API 24 起可用，正好是 minSdk），
 * 不引第三方拼音表 —— 那玩意儿动辄几百 KB，还得自己维护多音字。
 *
 * 为了能在 JVM 单元测试里跑，纯逻辑部分把「音译」抽成参数注入；
 * 只有默认入口才真的调 ICU。
 */
object Pinyin {

    /** 一条文本的两种检索形态。 */
    data class Key(val initials: String, val full: String) {
        val isEmpty: Boolean get() = initials.isEmpty() && full.isEmpty()
    }

    /**
     * 系统音译器。
     *
     * `android.icu.text.Transliterator` 是 API 29 才有的 —— 更老的系统上拿不到，
     * 这时拼音检索整体退化成「不做」（普通搜索照旧）。宁可少一个功能，
     * 也不要自己塞一张几万字的拼音表：多音字维护不动，包体还白白大一圈。
     */
    private val transliterator: Transliterator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) createTransliterator() else null
    }

    /** 拼音检索在当前系统上能不能用，界面用它决定要不要解释「为什么打 zjl 没反应」。 */
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createTransliterator(): Transliterator? =
        runCatching { Transliterator.getInstance("Han-Latin") }.getOrNull()

    /** 逐字缓存。曲库几百首、每次搜索都会问一遍，不缓存等于每次都重新音译。 */
    private val cache = object : LinkedHashMap<String, Key>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Key>?): Boolean =
            size > MAX_CACHE
    }

    private const val MAX_CACHE = 4_000

    fun keyOf(text: String): Key {
        if (text.isBlank()) return Key("", "")
        synchronized(cache) { cache[text]?.let { return it } }
        val computed = buildKey(text, ::icuTransliterate)
        synchronized(cache) { cache[text] = computed }
        return computed
    }

    fun keyOf(text: String, transliterate: (String) -> String): Key = buildKey(text, transliterate)

    /**
     * 把用户输入收成「纯小写字母」的检索词。
     *
     * 返回 null 表示这不是一次拼音查询（含空格、中文、符号等），
     * 调用方据此决定要不要走拼音匹配 —— 免得「周杰伦」也被当成拼音去比。
     */
    fun normalizeQuery(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.length < 2) return null
        if (!trimmed.all { it in 'a'..'z' || it in 'A'..'Z' }) return null
        return trimmed.lowercase(Locale.US)
    }

    /**
     * 命中强度：3 = 首字母前缀（`zjl` 打头），2 = 首字母包含，
     * 1 = 全拼包含（`jielun`、`dongfengpo`），0 = 不匹配。
     *
     * 分档只是为了让「更准的排前面」，不是打分系统。
     */
    fun score(query: String, text: String): Int = scoreOf(query, keyOf(text))

    /** 纯函数版本：不碰 ICU，测试可以直接喂一个假的 Key。 */
    fun scoreOf(query: String, key: Key): Int {
        if (query.isEmpty() || key.isEmpty) return 0
        return when {
            key.initials.startsWith(query) -> 3
            key.initials.contains(query) -> 2
            key.full.contains(query) -> 1
            else -> 0
        }
    }

    // ---------------- 内部 ----------------

    private fun buildKey(text: String, translate: (String) -> String): Key {
        val latin = stripTones(runCatching { translate(text) }.getOrDefault(text))
        val tokens = latin.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return Key("", "")
        val initials = buildString(tokens.size) { tokens.forEach { append(it[0]) } }
        val full = tokens.joinToString(separator = "")
        return Key(initials, full)
    }

    /** 去声调：`zhōu` → `zhou`。NFD 拆出组合符号，丢掉再拼回去。 */
    private fun stripTones(raw: String): String {
        val decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD)
        val out = StringBuilder(decomposed.length)
        for (ch in decomposed) {
            when {
                Character.getType(ch) == Character.NON_SPACING_MARK.toInt() -> Unit
                ch.lowercaseChar() in 'a'..'z' || ch in '0'..'9' -> out.append(ch.lowercaseChar())
                else -> out.append(' ')
            }
        }
        return out.toString()
    }

    private fun icuTransliterate(text: String): String {
        val engine = transliterator ?: return text
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            transliterateWith(engine, text)
        } else {
            text
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun transliterateWith(engine: Transliterator, text: String): String =
        runCatching { engine.transliterate(text) }.getOrDefault(text)
}
