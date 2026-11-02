package com.shiyin.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiyin.player.data.lyrics.LyricsCandidate
import com.shiyin.player.ui.formatMs

/**
 * 在线搜歌词。
 *
 * 自动匹配是按「歌名 + 艺人 + 时长」猜的，同名的现场版/翻唱/伴奏都会撞车。
 * 这里把候选摊开给用户挑：歌手、时长、有没有时间轴一目了然。
 * 网络搜不到时还能直接粘贴自己的 LRC——两条路都走不通再谈「没有歌词」。
 */
@Composable
fun LyricsSearchDialog(
    initialQuery: String,
    candidates: List<LyricsCandidate>,
    searching: Boolean,
    message: String?,
    onSearch: (String) -> Unit,
    onPick: (LyricsCandidate) -> Unit,
    onManual: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf(initialQuery) }
    var pasteMode by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (pasteMode) "粘贴歌词" else "搜歌词",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (pasteMode) "在线搜" else "粘贴歌词",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { pasteMode = !pasteMode }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        },
        text = {
            if (pasteMode) {
                Column {
                    Text(
                        "把 .lrc 或 .ttml 的内容粘进来。带 [00:12.34] 时间轴的会跟着播放滚动；TTML（Apple Music 那种）还能逐字点亮。" +
                        "两者都没有的按纯文本展示。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    TextField(
                        value = manual,
                        onValueChange = { manual = it },
                        placeholder = {
                            Text("[00:12.30]第一句歌词", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        colors = dialogFieldColors(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 240.dp),
                    )
                    if (message != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                }
            } else {
                Column {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = {
                            Text("歌手 - 歌名，例如：周杰伦 - 晴天", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        colors = dialogFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable(enabled = !searching) { onSearch(query) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                if (searching) "搜索中…" else "搜索",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (searching) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.align(Alignment.CenterVertically).size(14.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    when {
                        candidates.isNotEmpty() -> Column(
                            Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                        ) {
                            candidates.forEach { candidate ->
                                CandidateRow(candidate) { onPick(candidate) }
                            }
                        }

                        message != null -> Text(
                            message,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp,
                        )

                        else -> Text(
                            "不填就直接搜这首歌。结果里标了「时间轴」的会跟着播放滚动。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (pasteMode) {
                TextButton(onClick = { onManual(manual) }) {
                    Text("应用", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

@Composable
private fun CandidateRow(candidate: LyricsCandidate, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Text(
            candidate.title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                candidate.artist,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (candidate.durationSec > 0) {
                Text(
                    "  ·  ${formatMs(candidate.durationSec * 1000)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 10.5.sp,
                )
            }
            Spacer(Modifier.size(6.dp))
            Text(
                if (candidate.synced) "时间轴" else "纯文本",
                color = if (candidate.synced) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 9.5.sp,
            )
            Text(
                "  ·  ${candidate.source}",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                fontSize = 9.5.sp,
            )
        }
    }
}

@Composable
private fun dialogFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedTextColor = MaterialTheme.colorScheme.onBackground,
    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
)
