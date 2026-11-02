package com.shiyin.player.ui.home

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shiyin.player.ShiYinApp
import com.shiyin.player.data.curated.CuratedAlbum
import com.shiyin.player.data.curated.CuratedCatalog
import com.shiyin.player.data.model.Track
import com.shiyin.player.ui.readableMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class GenreChip(val label: String, val query: String?)

val GENRES = listOf(
    GenreChip("精选", null),
    GenreChip("电子", "Electronic"),
    GenreChip("嘻哈", "Hip-Hop/Rap"),
    GenreChip("低保真", "Lo-Fi"),
    GenreChip("氛围", "Ambient"),
    GenreChip("浩室", "House"),
    GenreChip("爵士", "Jazz"),
    GenreChip("摇滚", "Alternative"),
    GenreChip("古典", "Classical"),
    GenreChip("流行", "Pop"),
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = ShiYinApp.instance.repository
    private var job: Job? = null

    var tracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var genreIndex by mutableIntStateOf(0)
        private set

    /** 内置华语风格专辑，离线可用，不依赖任何网络音源。 */
    val albums: List<CuratedAlbum> = CuratedCatalog.albums

    init {
        load(0)
    }

    fun selectGenre(index: Int) {
        if (index == genreIndex && tracks.isNotEmpty()) return
        genreIndex = index
        load(index)
    }

    fun refresh() = load(genreIndex)

    private fun load(index: Int) {
        job?.cancel()
        job = viewModelScope.launch {
            loading = true
            error = null
            repository.feed(GENRES[index].query)
                .onSuccess { list ->
                    tracks = list
                    if (list.isEmpty()) error = "这个曲风暂时没有拉到内容，换个分类试试"
                }
                .onFailure { error = it.readableMessage() }
            loading = false
        }
    }
}
