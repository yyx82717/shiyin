package com.shiyin.player.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiyin.player.data.model.DEFAULT_PLAYLIST_ID
import com.shiyin.player.data.model.DEFAULT_PLAYLIST_NAME
import com.shiyin.player.data.model.Playlist
import com.shiyin.player.data.model.Track

/**
 * 「一键加入播放列表」的统一入口。
 *
 * 搜索页、本地页、精选专辑、播放页都调它，避免每个页面各写一套弹窗。
 * 第一行固定是「默认列表」——它不需要事先创建，听到的歌本来就会自动进去。
 */
@Composable
fun AddToPlaylistHost(
    track: Track?,
    playlists: List<Playlist>,
    onPick: (playlistId: String) -> Unit,
    onCreate: (name: String) -> Unit,
    onDismiss: () -> Unit,
    /** 「插到下一首播放」。不传就不显示这一行。 */
    onPlayNext: (() -> Unit)? = null,
) {
    if (track == null) return

    val context = LocalContext.current
    var creating by remember { mutableStateOf(false) }

    val defaultPlaylist = playlists.firstOrNull { it.id == DEFAULT_PLAYLIST_ID }
    val others = playlists.filterNot { it.id == DEFAULT_PLAYLIST_ID }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column {
                Text("加入播放列表", color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${track.title} · ${track.artist}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        text = {
            Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                if (onPlayNext != null) {
                    PickerRow(name = "插到下一首播放", meta = "不进歌单") {
                        onPlayNext()
                        onDismiss()
                    }
                }

                PickerRow(
                    name = DEFAULT_PLAYLIST_NAME,
                    meta = "${defaultPlaylist?.tracks?.size ?: 0} 首 · 播放自动收录",
                    highlight = true,
                ) {
                    onPick(DEFAULT_PLAYLIST_ID)
                    Toast.makeText(context, "已加入$DEFAULT_PLAYLIST_NAME", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }

                others.forEach { playlist ->
                    PickerRow(name = playlist.name, meta = "${playlist.tracks.size} 首") {
                        onPick(playlist.id)
                        Toast.makeText(context, "已加入「${playlist.name}」", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { creating = true }
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Spacer(Modifier.size(10.dp))
                    Text("新建歌单并加入", color = MaterialTheme.colorScheme.primary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )

    if (creating) {
        TextInputDialog(
            title = "新建歌单",
            onConfirm = { name -> onCreate(name) },
            onDismiss = {
                creating = false
                onDismiss()
            },
        )
    }
}

@Composable
private fun PickerRow(
    name: String,
    meta: String,
    highlight: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (highlight) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.size(7.dp))
        }
        Text(
            name,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 13.5.sp,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(8.dp))
        Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp, maxLines = 1)
    }
}
