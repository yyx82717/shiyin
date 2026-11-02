package com.shiyin.player.ui.curated

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiyin.player.data.curated.CuratedCatalog
import com.shiyin.player.data.curated.CuratedSong
import com.shiyin.player.data.curated.SongMatch
import com.shiyin.player.data.model.Track
import com.shiyin.player.ui.PlatformLinks
import com.shiyin.player.ui.player.PlayerViewModel
import com.shiyin.player.ui.theme.HeroBrush

/**
 * 华语风格专辑详情。
 *
 * 每首歌只有两种结局：
 * 1. 你手机里有这个文件 → 直接进播放队列；
 * 2. 没有 → 一键跳到官方平台（QQ音乐 / 网易云 / 酷狗）去听。
 *
 * 应用本身不下载、不解析任何平台的音频，也不内置歌词。
 */
@Composable
fun CuratedAlbumScreen(
    albumId: String,
    localTracks: List<Track>,
    playerVm: PlayerViewModel,
    onBack: () -> Unit,
) {
    val album = CuratedCatalog.byId(albumId)
    if (album == null) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
            BackRow(onBack)
            Text(
                "专辑不存在",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }

    val index = remember(localTracks) { SongMatch.buildIndex(localTracks) }
    val matched = remember(album, index) {
        album.songs.map { song -> SongMatch.find(song, index) }
    }
    val playable = remember(album, matched) {
        album.songs.indices.mapNotNull { i -> matched[i]?.let { i to it } }
    }

    var platformFor by remember { mutableStateOf<CuratedSong?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        BackRow(onBack)

        LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(album.accent).copy(alpha = 0.9f),
                                        Color(album.accent).copy(alpha = 0.3f),
                                    ),
                                ),
                            )
                            .padding(16.dp),
                    ) {
                        Column(Modifier.align(Alignment.CenterStart)) {
                            Text(
                                album.name,
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                album.tagline,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                            )
                        }
                        Text(
                            "${album.songs.size} 首",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        album.blurb,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp,
                        lineHeight = 18.sp,
                    )

                    Spacer(Modifier.height(14.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier
                                .clip(CircleShape)
                                .background(HeroBrush)
                                .clickable(enabled = playable.isNotEmpty()) {
                                    playerVm.play(playable.map { it.second }, 0)
                                }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = Color(0xFF1A1033),
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.size(4.dp))
                            Text(
                                if (playable.isEmpty()) "本机暂无可播" else "播放本地 ${playable.size} 首",
                                color = Color(0xFF1A1033),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.size(12.dp))
                        Text(
                            "本地曲库匹配 ${playable.size}/${album.songs.size}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.5.sp,
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        "没匹配到的歌点一下就跳到官方平台收听，应用内不抓取任何平台音源。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                }
            }

            itemsIndexed(album.songs, key = { _, song -> "${song.title}-${song.artist}" }) { i, song ->
                val track = matched[i]
                CuratedSongRow(
                    index = i + 1,
                    song = song,
                    track = track,
                    playing = track != null && playerVm.current?.id == track.id,
                    onPlay = {
                        if (track != null) {
                            val startIndex = playable.indexOfFirst { it.first == i }.coerceAtLeast(0)
                            playerVm.play(playable.map { it.second }, startIndex)
                        } else {
                            platformFor = song
                        }
                    },
                    onAdd = track?.let { t -> { playerVm.requestAddToPlaylist(t) } },
                    onPlatform = { platformFor = song },
                )
            }
        }
    }

    platformFor?.let { song ->
        PlatformPickerDialog(
            song = song,
            onDismiss = { platformFor = null },
        )
    }
}

@Composable
private fun BackRow(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.Rounded.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
