package com.shiyin.player.ui.player

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.shiyin.player.data.download.Downloader
import com.shiyin.player.data.model.Track
import com.shiyin.player.ui.components.Artwork
import com.shiyin.player.ui.formatMs
import com.shiyin.player.ui.settings.SettingsViewModel
import com.shiyin.player.ui.sourceLabel
import com.shiyin.player.ui.theme.HeroBrush

@Composable
fun PlayerScreen(
    playerVm: PlayerViewModel,
    settingsVm: SettingsViewModel,
    onClose: () -> Unit,
) {
    val track = playerVm.current ?: return
    val favorites by settingsVm.favorites.collectAsStateWithLifecycle()
    val downloads by settingsVm.downloads.collectAsStateWithLifecycle()
    val downloadProgress by settingsVm.downloadProgress.collectAsStateWithLifecycle()
    val liked = favorites.any { it.id == track.id }
    val downloaded = downloads.any { it.track.id == track.id }
    val progress = downloadProgress[track.id]

    var showLyrics by remember(track.id) { mutableStateOf(false) }
    var showLyricsSearch by remember { mutableStateOf(false) }
    var showPlaybackSheet by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    val context = LocalContext.current

    LaunchedEffect(track.id) {
        playerVm.ensureLyrics(track)
    }

    val total = playerVm.durationMs
    var dragging by remember { mutableStateOf<Float?>(null) }
    val liveProgress = if (total > 0) (playerVm.positionMs.toFloat() / total).coerceIn(0f, 1f) else 0f
    val shownProgress = dragging ?: liveProgress

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        if (!track.artwork.isNullOrBlank()) {
            AsyncImage(
                model = track.artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(70.dp).alpha(0.45f),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                ),
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "收起",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "正在播放",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp,
                    letterSpacing = 1.5.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (showLyrics) "封面" else "歌词",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { showLyrics = !showLyrics }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            BoxWithConstraints(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val side = minOf(maxWidth, maxHeight) - 4.dp

                if (showLyrics) {
                    LyricsPanel(
                        lines = playerVm.lyrics?.lines ?: emptyList(),
                        currentIndex = playerVm.currentLyricIndex(),
                        loading = playerVm.lyricsLoading,
                        source = playerVm.lyrics?.source,
                        instrumental = playerVm.lyrics?.instrumental ?: false,
                        onSeek = { if (it >= 0L) playerVm.seekTo(it) },
                        modifier = Modifier.fillMaxSize(),
                        positionMs = playerVm.positionMs,
                        playing = playerVm.isPlaying,
                        onSearchLyrics = {
                            playerVm.clearLyricsSearch()
                            showLyricsSearch = true
                        },
                    )
                } else {
                    Artwork(
                        url = track.artwork,
                        modifier = Modifier.size(side),
                        corner = 28.dp,
                        iconSize = 46.dp,
                    )
                }
            }

            Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(
                    track.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    track.artist,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(6.dp))

            Slider(
                value = shownProgress,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { if (total > 0) playerVm.seekTo((it * total).toLong()) }
                    dragging = null
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.onBackground,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                ),
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatMs(shownProgress.let { (it * total).toLong() }), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp)
                Text(formatMs(total), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp)
            }

            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { playerVm.toggleShuffle() }) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "随机",
                        tint = if (playerVm.shuffleOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = { playerVm.previous() }, modifier = Modifier.size(52.dp)) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "上一首",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Box(
                    Modifier
                        .size(66.dp)
                        .clip(CircleShape)
                        .background(HeroBrush)
                        .clickable { playerVm.toggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (playerVm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playerVm.isPlaying) "暂停" else "播放",
                        tint = Color(0xFF1A1033),
                        modifier = Modifier.size(34.dp),
                    )
                }
                IconButton(onClick = { playerVm.next() }, modifier = Modifier.size(52.dp)) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "下一首",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(onClick = { playerVm.cycleRepeat() }) {
                    Icon(
                        if (playerVm.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = "循环",
                        tint = if (playerVm.repeatMode == Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { settingsVm.toggleFavorite(track) }) {
                    Icon(
                        if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "收藏",
                        tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(21.dp),
                    )
                }

                DownloadButton(
                    track = track,
                    downloaded = downloaded,
                    progress = progress,
                    onDownload = {
                        settingsVm.download(track)
                        Toast.makeText(context, "已加入下载", Toast.LENGTH_SHORT).show()
                    },
                    onCancel = { settingsVm.cancelDownload(track) },
                    onRetry = { settingsVm.retryDownload(track) },
                )

                IconButton(onClick = { playerVm.requestAddToPlaylist(track) }) {
                    Icon(
                        Icons.Rounded.PlaylistAdd,
                        contentDescription = "加入歌单",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(21.dp),
                    )
                }

                IconButton(onClick = { showQueue = true }) {
                    Icon(
                        Icons.Rounded.QueueMusic,
                        contentDescription = "待播队列",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(21.dp),
                    )
                }

                IconButton(onClick = { showPlaybackSheet = true }) {
                    Icon(
                        Icons.Rounded.Tune,
                        contentDescription = "播放设置",
                        tint = if (playerVm.speed != 1f || playerVm.sleepRemainingMs >= 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(21.dp),
                    )
                }

                Spacer(Modifier.weight(1f))

                // 倍速和睡眠定时都在后台悄悄生效，不显示出来用户会以为没设上
                Text(
                    buildString {
                        append(sourceLabel(track.source))
                        if (kotlin.math.abs(playerVm.speed - 1f) > 0.01f) {
                            append(" · ${playerVm.speed}×")
                        }
                        if (playerVm.sleepRemainingMs >= 0) {
                            val left = playerVm.sleepRemainingMs / 1000
                            append(" · 睡眠 ${left / 60}:${(left % 60).toString().padStart(2, '0')}")
                        } else if (playerVm.sleepAtTrackEnd) {
                            append(" · 播完停")
                        }
                        append(" · 队列 ${playerVm.queue.size} 首")
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.5.sp,
                    // 这行小字现在也能点：队列是「看一眼」的高频操作，别逼用户去找图标
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { showQueue = true }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
    }

    if (showQueue) {
        QueueSheet(playerVm = playerVm, onDismiss = { showQueue = false })
    }

    if (showPlaybackSheet) {
        PlaybackSheet(
            playerVm = playerVm,
            settingsVm = settingsVm,
            onDismiss = { showPlaybackSheet = false },
        )
    }

    if (showLyricsSearch) {
        val candidateTrack = playerVm.current
        LyricsSearchDialog(
            initialQuery = candidateTrack?.let { "${it.title} ${it.artist}" }.orEmpty(),
            candidates = playerVm.lyricsCandidates,
            searching = playerVm.lyricsSearching,
            message = playerVm.lyricsSearchMessage,
            onSearch = { query -> playerVm.searchLyrics(query) },
            onPick = { candidate -> playerVm.useCandidate(candidate) },
            onManual = { raw -> playerVm.useManualLyrics(raw) },
            onDismiss = { showLyricsSearch = false },
        )
    }
}

/**
 * 下载按钮的四种状态：可下载 / 下载中（带环形进度，点一下取消）/ 失败（点重试）/ 已下载。
 * 本地曲目直接不显示——文件本来就在手机上。
 */
@Composable
private fun DownloadButton(
    track: Track,
    downloaded: Boolean,
    progress: Downloader.Progress?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    if (track.source == "local") return

    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    val running = progress?.takeIf { it.phase == Downloader.Phase.RUNNING }
    val failed = progress?.phase == Downloader.Phase.FAILED

    when {
        downloaded -> IconButton(onClick = {}) {
            Icon(
                Icons.Rounded.DownloadDone,
                contentDescription = "已下载",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(21.dp),
            )
        }

        running != null -> {
            val accent = MaterialTheme.colorScheme.primary
            val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
            Box(
                Modifier
                    .size(48.dp)
                    .clickable { onCancel() },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(18.dp)
                        .drawBehind {
                            val stroke = 2.dp.toPx()
                            drawArc(
                                color = trackColor,
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = stroke),
                            )
                            drawArc(
                                color = accent,
                                startAngle = -90f,
                                sweepAngle = 360f * running.fraction,
                                useCenter = false,
                                style = Stroke(width = stroke),
                            )
                        },
                )
            }
        }

        failed -> IconButton(onClick = onRetry) {
            Icon(
                Icons.Rounded.Refresh,
                contentDescription = "重试下载",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(21.dp),
            )
        }

        else -> IconButton(onClick = onDownload) {
            Icon(
                Icons.Rounded.Download,
                contentDescription = "下载",
                tint = tint,
                modifier = Modifier.size(21.dp),
            )
        }
    }
}
