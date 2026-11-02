package com.shiyin.player.ui.curated

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiyin.player.data.curated.CuratedSong
import com.shiyin.player.data.model.Track
import com.shiyin.player.ui.PlatformLinks

/**
 * 一条精选曲目。
 *
 * 本地有文件 → 直接播，右边给出「本地」标记和加入歌单的加号；
 * 本地没有 → 点一下弹官方平台选择，跳出去听。
 * 专辑页和搜索结果页共用这一个行组件，行为完全一致。
 */
@Composable
fun CuratedSongRow(
    index: Int,
    song: CuratedSong,
    track: Track?,
    playing: Boolean,
    onPlay: () -> Unit,
    onAdd: (() -> Unit)?,
    onPlatform: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            index.toString().padStart(2, '0'),
            color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            fontSize = 11.sp,
            modifier = Modifier.size(width = 26.dp, height = 16.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                buildString {
                    append(song.artist)
                    if (song.year > 0) append(" · ").append(song.year)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (track != null) {
            if (onAdd != null) {
                IconButton(onClick = onAdd) {
                    Icon(
                        Icons.Rounded.PlaylistAdd,
                        contentDescription = "加入歌单",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            Text(
                "本地",
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Medium,
            )
        } else {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onPlatform)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "去听",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.size(3.dp))
                Icon(
                    Icons.Rounded.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
    }
}

/** 选一个官方平台去听这首歌。应用内不提供它的音频。 */
@Composable
fun PlatformPickerDialog(song: CuratedSong, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val query = "${song.title} ${song.artist}"
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column {
                Text("去官方平台收听", color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "$query · 应用内不提供这首歌的音频",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PlatformLinks.platforms.forEach { platform ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                PlatformLinks.open(context, platform, query)
                                onDismiss()
                            }
                            .padding(horizontal = 10.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            platform.label,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 13.5.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
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
}
