package com.shiyin.player.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiyin.player.data.curated.CuratedSong
import com.shiyin.player.data.search.Pinyin
import com.shiyin.player.ui.PlatformLinks
import com.shiyin.player.ui.components.LoadingBox
import com.shiyin.player.ui.components.MessageBox
import com.shiyin.player.ui.components.PillChip
import com.shiyin.player.ui.components.TrackRow
import com.shiyin.player.ui.curated.CuratedSongRow
import com.shiyin.player.ui.curated.PlatformPickerDialog
import com.shiyin.player.ui.player.PlayerViewModel
import com.shiyin.player.ui.sourceLabel

private val HINTS = listOf("周杰伦", "成都", "大鱼", "国风", "lofi", "chill")

/**
 * 曲库里没有的时候，把关键词交给官方平台。
 * 只跳转官方页面，不下载、不解析任何音频地址。
 */
@Composable
private fun PlatformFooter(query: String) {
    if (query.isBlank()) return
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) {
        Text(
            "曲库里没有想听的？把关键词交给官方平台",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.5.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlatformLinks.platforms.forEach { platform ->
                PillChip(platform.label, false) { PlatformLinks.open(context, platform, query) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "跳转到官方 App 或网页播放，不在本应用内抓取音频。",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontSize = 10.5.sp,
        )
    }
}

@Composable
private fun SectionLabel(text: String, hint: String? = null) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp)) {
        Text(text, color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (hint != null) {
            Spacer(Modifier.height(2.dp))
            Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp)
        }
    }
}

@Composable
fun SearchScreen(
    vm: SearchViewModel,
    playerVm: PlayerViewModel,
) {
    var platformSong by remember { mutableStateOf<CuratedSong?>(null) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp)) {
            Text(
                "搜索",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            )
            Spacer(Modifier.height(12.dp))
            TextField(
                value = vm.query,
                onValueChange = vm::onQueryChange,
                singleLine = true,
                shape = CircleShape,
                placeholder = {
                    Text("搜歌名、音乐人、风格（含华语精选与本地）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp),
                    )
                },
                trailingIcon = {
                    if (vm.query.isNotEmpty()) {
                        IconButton(onClick = { vm.onQueryChange("") }) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "清空",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(10.dp))

        if (vm.query.isBlank()) {
            val history by vm.history.collectAsStateWithLifecycle()
            Column(Modifier.padding(horizontal = 20.dp)) {
                if (history.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "最近搜过",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "清空",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 10.5.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { vm.clearHistory() }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    history.take(6).chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { keyword ->
                                PillChip(keyword, false) { vm.onQueryChange(keyword) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text("试试这些", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HINTS.take(3).forEach { hint -> PillChip(hint, false) { vm.onQueryChange(hint) } }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HINTS.drop(3).forEach { hint -> PillChip(hint, false) { vm.onQueryChange(hint) } }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "搜中文歌名会先在「华语精选」里找一遍：本机有文件就直接播，" +
                        "没有就给官方平台入口。同时也会搜本地曲库、自建服务器、网盘和开放曲库。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    fontSize = 10.5.sp,
                    lineHeight = 16.sp,
                )
                if (!Pinyin.supported) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "拼音搜索（打 zjl 找周杰伦）需要 Android 10 及以上，当前系统用不了这一项；其他功能不受影响。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        fontSize = 10.5.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
            // ---- 华语精选：本地匹配，不需要联网 ----
            if (vm.curatedHits.isNotEmpty()) {
                val playableTracks = vm.playableCurated()
                item {
                    SectionLabel(
                        text = "华语精选 · ${vm.curatedHits.size} 首",
                        hint = if (playableTracks.isNotEmpty()) {
                            "其中 ${playableTracks.size} 首在本机能直接播"
                        } else {
                            "本机没有匹配到文件，点「去听」跳官方平台"
                        },
                    )
                }
                itemsIndexed(
                    vm.curatedHits,
                    key = { _, hit -> "${hit.song.title}-${hit.song.artist}" },
                ) { position, hit ->
                    val track = hit.track
                    CuratedSongRow(
                        index = position + 1,
                        song = hit.song,
                        track = track,
                        playing = track != null && playerVm.current?.id == track.id,
                        onPlay = {
                            if (track != null) {
                                val start = playableTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                                playerVm.play(playableTracks, start)
                            } else {
                                platformSong = hit.song
                            }
                        },
                        onAdd = track?.let { t -> { playerVm.requestAddToPlaylist(t) } },
                        onPlatform = { platformSong = hit.song },
                    )
                }
                item { Divider() }
            }

            // ---- 拼音命中：本地曲库里按首字母/全拼找到的 ----
            if (vm.pinyinHits.isNotEmpty()) {
                item {
                    SectionLabel(
                        text = "拼音匹配 · ${vm.pinyinHits.size} 首",
                        hint = "按首字母或全拼在你的本地曲库里找到的",
                    )
                }
                itemsIndexed(vm.pinyinHits, key = { _, t -> "py-${t.id}" }) { index, track ->
                    TrackRow(
                        track = track,
                        playing = playerVm.current?.id == track.id,
                        onClick = {
                            val start = vm.pinyinHits.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                            playerVm.play(vm.pinyinHits, start)
                        },
                        trailing = {
                            IconButton(onClick = { playerVm.requestAddToPlaylist(track) }) {
                                Icon(
                                    Icons.Rounded.PlaylistAdd,
                                    contentDescription = "加入歌单",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                    )
                }
                item { Divider() }
            }

            // ---- 开放曲库 / 本地 / 网盘 ----
            when {
                vm.loading -> item { LoadingBox(hint = "正在搜索…") }

                vm.results.isNotEmpty() -> {
                    item { SectionLabel("其他来源 · ${vm.results.size} 首", "本地曲库、自建服务器、网盘、开放曲库") }
                    itemsIndexed(vm.results, key = { _, t -> t.id }) { index, track ->
                        TrackRow(
                            track = track,
                            playing = playerVm.current?.id == track.id,
                            onClick = { playerVm.play(vm.results, index) },
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                    ) {
                                        Text(
                                            sourceLabel(track.source),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                    IconButton(onClick = { playerVm.requestAddToPlaylist(track) }) {
                                        Icon(
                                            Icons.Rounded.PlaylistAdd,
                                            contentDescription = "加入播放列表",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }

            // ---- 全空引导：告诉用户搜了哪些源、还能连哪些 ----
            if (!vm.loading && vm.results.isEmpty() && vm.curatedHits.isEmpty() && vm.pinyinHits.isEmpty()) {
                item {
                    EmptySearchGuide(
                        query = vm.query,
                        searched = vm.searchedSources,
                        error = vm.error,
                        onRetry = { vm.retry() },
                    )
                }
            }

            item { PlatformFooter(vm.query) }
        }
        }
    }

    platformSong?.let { song ->
        PlatformPickerDialog(song = song, onDismiss = { platformSong = null })
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)),
    )
}

/**
 * 「什么都没搜到」时的引导卡片。
 *
 * 之前空状态只甩一句「没有结果」+ 重试，用户不知道问题出在哪——
 * 是他没连网盘？还是这个词本就不在这几个源里？
 * 现在把它说清楚：列出本次实际纳入搜索的音源，并提示还能在设置里连更多源 / 导入歌单。
 */
@Composable
private fun EmptySearchGuide(
    query: String,
    searched: List<String>,
    error: String?,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        if (error != null) {
            Text(
                "搜索出错了",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                error,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        } else {
            Text(
                "没有找到「$query」",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "已经在这些音源里找过了：",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(10.dp))
        if (searched.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(searched) { label -> PillChip(label, false) {} }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "曲库由你自己的文件、网盘和开放曲库组成。想搜到更多？在「设置」里连接百度网盘 / WebDAV / 自建服务器，或导入 M3U 歌单。",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onRetry) { Text("重试") }
    }
}
