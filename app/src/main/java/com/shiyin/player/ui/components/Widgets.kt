package com.shiyin.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shiyin.player.data.model.AudioQuality
import com.shiyin.player.data.model.AudioQualityOf
import com.shiyin.player.data.model.Track
import com.shiyin.player.ui.theme.Violet

/** 统一的封面控件：加载失败/无图时显示渐变占位，不会出现空洞。 */
@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    corner: Dp = 16.dp,
    iconSize: Dp = 26.dp,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF332A55), Color(0xFF17222C)),
                ),
            ),
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.align(Alignment.Center).size(iconSize),
            )
        }
    }
}

@Composable
fun PillChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier, hint: String = "正在从网络拉取曲目…") {
    Box(modifier.fillMaxWidth().padding(vertical = 56.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
fun MessageBox(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            if (actionText != null && onAction != null) {
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onAction)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(actionText, color = MaterialTheme.colorScheme.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** 列表行：封面 + 标题 + 艺人，播放中高亮。 */
@Composable
fun TrackRow(
    track: Track,
    playing: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Artwork(track.artwork, Modifier.size(52.dp), corner = 12.dp, iconSize = 18.dp)
            if (playing) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = Violet, modifier = Modifier.size(22.dp))
                }
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                track.title,
                color = if (playing) Violet else MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    track.artist,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // 无损 / 高品标在这里：一眼能看出这首值不值得为它开高点音量
                val badge = AudioQualityOf.badge(track)
                if (badge.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    QualityBadge(track, badge)
                }
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

/**
 * 音质徽章。
 *
 * 只标「容器 + 档位」，不标具体码率 —— 网盘根本拿不到码率，写个假的更糟。
 * 无损用主题色描边，有损用灰阶，靠颜色就能扫一眼分辨。
 */
@Composable
private fun QualityBadge(track: Track, text: String) {
    val quality = AudioQualityOf.of(track)
    val tint = when (quality) {
        AudioQuality.LOSSLESS -> MaterialTheme.colorScheme.primary
        AudioQuality.HIGH -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    }
    Text(
        text,
        color = tint,
        fontSize = 8.5.sp,
        letterSpacing = 0.3.sp,
        maxLines = 1,
        modifier = Modifier
            .border(0.8.dp, tint.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
            .padding(horizontal = 3.5.dp, vertical = 1.dp),
    )
}
