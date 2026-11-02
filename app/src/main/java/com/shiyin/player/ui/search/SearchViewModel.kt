package com.shiyin.player.ui.search

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shiyin.player.ShiYinApp
import com.shiyin.player.data.curated.CuratedAlbum
import com.shiyin.player.data.curated.CuratedCatalog
import com.shiyin.player.data.curated.CuratedSong
import com.shiyin.player.data.curated.SongMatch
import com.shiyin.player.data.model.Track
import com.shiyin.player.data.search.Pinyin
import com.shiyin.player.ui.readableMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 一条「华语精选」命中：歌 + 所属专辑 + 本机对应的文件（如果有）。 */
data class CuratedHit(
    val song: CuratedSong,
    val album: CuratedAlbum,
    val track: Track?,
)

class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = ShiYinApp.instance.repository
    private var job: Job? = null

    var query by mutableStateOf("")
        private set
    var results by mutableStateOf<List<Track>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** 内置华语曲库里的命中，本地查，不走网络。 */
    var curatedHits by mutableStateOf<List<CuratedHit>>(emptyList())
        private set

    /**
     * 只靠拼音匹配到的本地曲目。
     *
     * 单独开一段而不是混进「其他来源」：用户打 `zjl` 时想看的就是「我库里有哪些周杰伦」，
     * 混进网盘/开放曲库的结果反而更难认。
     */
    var pinyinHits by mutableStateOf<List<Track>>(emptyList())
        private set

    /** 搜索历史，最新的在最前。点一下直接复搜。 */
    val history: StateFlow<List<String>> = ShiYinApp.instance.settings.searchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var localIndex: Map<String, List<Track>> = emptyMap()
    private var localTracks: List<Track> = emptyList()

    init {
        viewModelScope.launch {
            val local = runCatching { repository.localCached() }.getOrDefault(emptyList())
            localTracks = local
            localIndex = SongMatch.buildIndex(local)
            if (query.isNotBlank()) refreshCurated(query)
        }
    }

    fun onQueryChange(value: String) {
        query = value
        refreshCurated(value)
        job?.cancel()
        if (value.isBlank()) {
            results = emptyList()
            pinyinHits = emptyList()
            error = null
            loading = false
            return
        }
        job = viewModelScope.launch {
            delay(420)
            runSearch(value.trim())
        }
    }

    fun retry() {
        if (query.isNotBlank()) {
            job?.cancel()
            job = viewModelScope.launch { runSearch(query.trim()) }
        }
    }

    /** 搜索结果里本地能播的那几首，供「播放全部」用。 */
    fun playableCurated(): List<Track> = curatedHits.mapNotNull { it.track }

    private fun refreshCurated(keyword: String) {
        val key = SongMatch.normalizeTitle(keyword)
        if (key.isBlank()) {
            curatedHits = emptyList()
            pinyinHits = emptyList()
            return
        }
        val latin = Pinyin.normalizeQuery(keyword)
        val pairs = CuratedCatalog.albums.asSequence()
            .flatMap { album -> album.songs.asSequence().map { album to it } }

        val textHits = pairs.filter { (_, song) ->
            SongMatch.normalizeTitle(song.title).contains(key) ||
                SongMatch.normalizeArtist(song.artist).contains(key)
        }
        // 拼音命中排在文字命中后面：字面匹配总是更可信
        val pinyinCurated = if (latin != null) {
            pairs.filter { (_, song) ->
                Pinyin.score(latin, song.title) > 0 || Pinyin.score(latin, song.artist) > 0
            }
        } else {
            emptySequence()
        }

        curatedHits = (textHits + pinyinCurated)
            .distinctBy { "${it.second.title}·${it.second.artist}" }
            .take(MAX_CURATED_HITS)
            .map { (album, song) -> CuratedHit(song, album, SongMatch.find(song, localIndex)) }
            .toList()

        refreshPinyinTracks(latin)
    }

    /** 本地曲库里按拼音命中的那几首。纯内存计算，不发请求。 */
    private fun refreshPinyinTracks(latin: String?) {
        if (latin == null || localTracks.isEmpty()) {
            pinyinHits = emptyList()
            return
        }
        pinyinHits = localTracks.asSequence()
            .map { it to (Pinyin.score(latin, it.title) + Pinyin.score(latin, it.artist)) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(MAX_PINYIN_HITS)
            .map { it.first }
            .toList()
    }

    private suspend fun runSearch(keyword: String) {
        loading = true
        error = null
        // 只在真的发出请求时记一笔，防抖期间的半截词不算
        runCatching { ShiYinApp.instance.settings.pushSearchHistory(keyword) }
        repository.search(keyword)
            .onSuccess { list ->
                results = list
                if (list.isEmpty()) error = "没有找到「$keyword」相关的曲目"
            }
            .onFailure { error = it.readableMessage() }
        loading = false
    }

    fun clearHistory() {
        viewModelScope.launch { runCatching { ShiYinApp.instance.settings.clearSearchHistory() } }
    }

    private companion object {
        const val MAX_CURATED_HITS = 12
        const val MAX_PINYIN_HITS = 20
    }
}
