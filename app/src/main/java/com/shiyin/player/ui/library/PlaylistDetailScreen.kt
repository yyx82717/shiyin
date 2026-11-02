package com.shiyin.player.ui.library

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiyin.player.data.model.DEFAULT_PLAYLIST_ID
import com.shiyin.player.data.model.M3u
import com.shiyin.player.ui.components.ConfirmDialog
import com.shiyin.player.ui.components.MessageBox
import com.shiyin.player.ui.components.TextInputDialog
import com.shiyin.player.ui.components.TrackRow
import com.shiyin.player.ui.player.PlayerViewModel
import com.shiyin.player.ui.settings.SettingsViewModel
import com.shiyin.player.ui.theme.HeroBrush

@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    settingsVm: SettingsViewModel,
    playerVm: PlayerViewModel,
    onBack: () -> Unit,
) {
    val playlists by settingsVm.playlists.collectAsStateWithLifecycle()
    val playlist = playlists.firstOrNull { it.id == playlistId }

    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val context = LocalContext.current
    // 导出走系统文件选择器：落到哪个目录由用户决定，应用不需要申请存储权限
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl"),
    ) { uri ->
        val target = playlist
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        val payload = M3u.write(target.name, target.tracks)
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(payload.toByteArray()) }
                ?: error("无法写入")
        }.onSuccess {
            Toast.makeText(context, "已导出 ${target.tracks.size} 首", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "导出失败：${it.message ?: "写入被拒绝"}", Toast.LENGTH_LONG).show()
        }
    }

    if (playlist == null) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding(),
        ) {
            BackRow(onBack)
            MessageBox(
                title = "歌单已删除",
                subtitle = "它可能刚刚被移除了",
                actionText = "返回",
                onAction = onBack,
            )
        }
        return
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            BackRow(onBack)

            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    playlist.name,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (playlist.id == DEFAULT_PLAYLIST_ID) {
                        "${playlist.tracks.size} 首 · 听过的歌自动收进来，最多 300 首"
                    } else {
                        "${playlist.tracks.size} 首"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(HeroBrush)
                            .clickable {
                                if (playlist.tracks.isNotEmpty()) playerVm.play(playlist.tracks, 0)
                            }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFF1A1033),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(4.dp))
                        Text("播放全部", color = Color(0xFF1A1033), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.weight(1f))

                    IconButton(
                        onClick = {
                            if (playlist.tracks.isEmpty()) {
                                Toast.makeText(context, "空歌单没什么可导出的", Toast.LENGTH_SHORT).show()
                            } else {
                                exportLauncher.launch("${playlist.name}.m3u8")
                            }
                        },
                    ) {
                        Icon(
                            Icons.Rounded.IosShare,
                            contentDescription = "导出为 m3u8",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp),
                        )
                    }

                    if (playlist.id != DEFAULT_PLAYLIST_ID) {
                        IconButton(onClick = { renaming = true }) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = "重命名",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        IconButton(onClick = { deleting = true }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "删除歌单",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (playlist.tracks.isEmpty()) {
                MessageBox(
                    title = "歌单还是空的",
                    subtitle = "在播放页点「加入歌单」，把喜欢的歌收进来",
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    itemsIndexed(playlist.tracks, key = { _, track -> track.id }) { index, track ->
                        TrackRow(
                            track = track,
                            playing = playerVm.current?.id == track.id,
                            onClick = { playerVm.play(playlist.tracks, index) },
                            trailing = {
                                IconButton(onClick = { settingsVm.removeFromPlaylist(playlist.id, track.id) }) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = "移出歌单",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(17.dp),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (renaming) {
        TextInputDialog(
            title = "重命名歌单",
            initial = playlist.name,
            onConfirm = { settingsVm.renamePlaylist(playlist.id, it) },
            onDismiss = { renaming = false },
        )
    }

    if (deleting) {
        ConfirmDialog(
            title = "删除歌单",
            message = "「${playlist.name}」将被删除，歌本身不会从曲库消失。",
            onConfirm = {
                settingsVm.deletePlaylist(playlist.id)
                onBack()
            },
            onDismiss = { deleting = false },
        )
    }
}

@Composable
private fun BackRow(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
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
