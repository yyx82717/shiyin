package com.shiyin.player.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import com.shiyin.player.data.curated.CuratedAlbum
import com.shiyin.player.data.curated.CuratedCatalog
import com.shiyin.player.data.curated.SongMatch
import com.shiyin.player.data.model.Track
import com.shiyin.player.ui.components.Artwork
import com.shiyin.player.ui.components.LoadingBox
import com.shiyin.player.ui.components.MessageBox
import com.shiyin.player.ui.components.PillChip
import com.shiyin.player.ui.player.PlayerViewModel

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    playerVm: PlayerViewModel,
    localTracks: List<Track>,
    onOpenAlbum: (String) -> Unit,
) {
    // 本地曲库变了就重建索引，专辑卡片上的「本地可播 N 首」始终是最新的
    val localIndex = remember(localTracks) { SongMatch.buildIndex(localTracks) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Header(vm, localTracks.size)
        }

        // 华语精选：不联网也有内容，是「中文曲库」的第一入口
        item(span = { GridItemSpan(maxLineSpan) }) {
            CuratedSection(
                albums = vm.albums,
                matchedCount = { CuratedCatalog.matchedCount(it, localIndex) },
                localCount = localTracks.size,
                onOpen = onOpenAlbum,
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(
                    "开放曲库 · 每周热门",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GENRES.forEachIndexed { index, genre ->
                        PillChip(genre.label, vm.genreIndex == index) { vm.selectGenre(index) }
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        }

        val errorText = vm.error
        when {
            vm.loading && vm.tracks.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) { LoadingBox() }

            vm.tracks.isEmpty() && errorText != null -> item(span = { GridItemSpan(maxLineSpan) }) {
                MessageBox(
                    title = "没能加载到曲目",
                    subtitle = errorText,
                    actionText = "重新加载",
                    onAction = { vm.refresh() },
                )
            }

            else -> itemsIndexed(vm.tracks, key = { _, t -> t.id }) { index, track ->
                TrackCard(track) { playerVm.play(vm.tracks, index) }
            }
        }
    }
}

@Composable
private fun Header(vm: HomeViewModel, localCount: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "拾音",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp,
            )
            Text(
                if (localCount > 0) "华语精选 · 本地曲库 $localCount 首" else "华语精选 · 开放曲库",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { vm.refresh() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Refresh,
                contentDescription = "刷新",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun CuratedSection(
    albums: List<CuratedAlbum>,
    matchedCount: (CuratedAlbum) -> Int,
    localCount: Int,
    onOpen: (String) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "华语风格专辑",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                )
                Text(
                    if (localCount > 0) {
                        "按风格整理的中文歌单，能在本机找到的直接播"
                    } else {
                        "按风格整理的中文歌单，支持跳官方平台收听"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(albums, key = { it.id }) { album ->
                AlbumCard(album = album, matched = matchedCount(album), onClick = { onOpen(album.id) })
            }
        }
    }
}

@Composable
private fun AlbumCard(album: CuratedAlbum, matched: Int, onClick: () -> Unit) {
    Column(
        Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(album.accent).copy(alpha = 0.85f), Color(album.accent).copy(alpha = 0.35f)),
                ),
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(
            album.name,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            album.tagline,
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 9.5.sp,
            lineHeight = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "${album.songs.size} 首",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            if (matched > 0) "本地可播 $matched 首" else "点击查看",
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 9.5.sp,
        )
    }
}

@Composable
private fun TrackCard(track: Track, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Artwork(
            url = track.artwork,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            corner = 18.dp,
            iconSize = 30.dp,
        )
        Spacer(Modifier.height(9.dp))
        Text(
            track.title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            track.artist,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
