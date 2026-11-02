package com.shiyin.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiyin.player.data.lyrics.LyricLine
import kotlinx.coroutines.launch

/**
 * 歌词面板。
 *
 * 带时间轴：列表跟随播放滚动，当前行高亮，点任意行跳转。
 * 带逐字时间轴（增强 LRC / TTML）：当前行按字点亮，而不是整行一起亮。
 * 纯文本：可滚动浏览，不做高亮。
 *
 * [positionMs] 是播放器每 500ms 推一次的粗位置；逐字要在字与字之间推进，
 * 500ms 一格会明显卡顿，所以内部会用帧时钟把它补成逐帧（见 [rememberSmoothPosition]）。
 */
@Composable
fun LyricsPanel(
    lines: List<LyricLine>,
    currentIndex: Int,
    loading: Boolean,
    source: String?,
    instrumental: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onSearchLyrics: (() -> Unit)? = null,
    positionMs: Long = 0L,
    playing: Boolean = false,
) {
    Box(modifier.fillMaxSize()) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        "正在匹配歌词…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            instrumental -> CenterHint("纯音乐，没有歌词")

            lines.isEmpty() -> CenterHint("没有找到这首歌的歌词\n点右上角「搜歌词」在线搜一条，或粘贴自己的 LRC / TTML")

            lines.first().timeMs < 0L -> PlainLyrics(lines, modifier)

            else -> SyncedLyrics(
                lines = lines,
                currentIndex = currentIndex,
                onSeek = onSeek,
                modifier = modifier,
                positionMs = positionMs,
                playing = playing,
            )
        }

        if (!source.isNullOrBlank() && lines.isNotEmpty()) {
            Text(
                "歌词来源：$source",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                fontSize = 9.5.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
            )
        }

        // 自动匹配错了、或者干脆没找到，都可以从这里手动搜一条
        if (onSearchLyrics != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                    .clickable(onClick = onSearchLyrics)
                    .padding(horizontal = 11.dp, vertical = 5.dp),
            ) {
                Text(
                    "搜歌词",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun SyncedLyrics(
    lines: List<LyricLine>,
    currentIndex: Int,
    onSeek: (Long) -> Unit,
    modifier: Modifier,
    positionMs: Long,
    playing: Boolean,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 用户一旦手动滑动歌词，就暂停自动跟随——不然下一句一到就把人拽回去，很烦。
    // 暂停后右下角出现「回到当前」，点一下恢复跟随。
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var following by remember { mutableStateOf(true) }

    LaunchedEffect(dragged) { if (dragged) following = false }

    LaunchedEffect(currentIndex, following) {
        if (following && currentIndex >= 0) {
            // 让当前行稍微偏上，前后文都能看到
            listState.animateScrollToItem((currentIndex - 2).coerceAtLeast(0))
        }
    }

    // 只有当这首歌词里真的有逐字时间轴时才启动逐帧时钟，普通 LRC 不白烧电量
    val hasWordTiming = remember(lines) { lines.any { it.hasWordTiming } }
    val clock = rememberSmoothPosition(positionMs, playing && hasWordTiming)

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(lines) { index, line ->
                val active = index == currentIndex
                LyricRow(
                    line = line,
                    active = active,
                    // 只把时钟喂给「当前且带逐字」的那一行，
                    // 这样每帧只有一行重组，不是整个列表
                    clock = if (active && line.hasWordTiming) clock else null,
                    onClick = { onSeek(line.timeMs) },
                )
            }
        }

        if (!following && currentIndex >= 0) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 18.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f))
                    .clickable {
                        following = true
                        scope.launch {
                            listState.animateScrollToItem((currentIndex - 2).coerceAtLeast(0))
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    "回到当前",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * 一行歌词。
 *
 * [clock] 非空时按逐字时间上色：已唱的字用主色，未唱的压到 32% 透明度。
 * 读取 `clock.value` 发生在这个函数里，所以逐帧重组只波及这一行。
 */
@Composable
private fun LyricRow(
    line: LyricLine,
    active: Boolean,
    clock: State<Long>?,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
    val content: AnnotatedString = if (clock != null) {
        wordStyled(line, clock.value, sung = primary, unsung = primary.copy(alpha = 0.3f))
    } else {
        AnnotatedString(line.text)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = content,
            color = if (active) primary else idle,
            fontSize = if (active) 15.5.sp else 14.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp,
        )
        line.translation?.let {
            Text(
                text = it,
                color = if (active) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                fontSize = if (active) 11.5.sp else 10.5.sp,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        line.roman?.let {
            Text(
                text = it,
                color = if (active) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.22f),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
            )
        }
    }
}

/**
 * 按字符上色。逐字时间轴摊平到每个字之后，第 n 个字到点就点亮。
 *
 * 字符数可能和时间轴对不上（解析器裁过首尾空白），越界的一律当作还没唱。
 */
private fun wordStyled(
    line: LyricLine,
    positionMs: Long,
    sung: Color,
    unsung: Color,
): AnnotatedString {
    val starts = line.charStartTimes()
    return buildAnnotatedString {
        line.text.forEachIndexed { index, ch ->
            val at = starts.getOrNull(index) ?: Long.MAX_VALUE
            withStyle(SpanStyle(color = if (positionMs >= at) sung else unsung)) {
                append(ch)
            }
        }
    }
}

/**
 * 把 500ms 一格的粗位置补成逐帧位置。
 *
 * 播放器每 500ms 才推一次 `positionMs`，直接拿它做逐字高亮会一格一格地跳。
 * 这里记住「拿到这个位置时的帧时间」，之后每帧用 `positionMs + 经过时间` 推算，
 * 直到下一次推送把它拉回真实值。
 *
 * [playing] 为 false 时不做插值（暂停了位置本来就不该动）。
 */
@Composable
private fun rememberSmoothPosition(positionMs: Long, playing: Boolean): State<Long> {
    val state = remember { mutableLongStateOf(positionMs) }
    LaunchedEffect(positionMs, playing) {
        state.longValue = positionMs
        if (!playing) return@LaunchedEffect
        var baseFrame = -1L
        var shown = positionMs
        while (true) {
            withFrameMillis { frame ->
                if (baseFrame < 0L) baseFrame = frame
                val next = positionMs + (frame - baseFrame)
                // 插值可能比真实位置跑快一点（缓冲、卡顿），下一次锚点回拉时
                // 只要差得不多就原地停住，别让已经亮的字闪回去；
                // 差得多说明用户拖了进度条，那就照实跳。
                shown = if (next >= shown || shown - next > 1_200L) next else shown
                state.longValue = shown
            }
        }
    }
    return state
}

@Composable
private fun PlainLyrics(lines: List<LyricLine>, modifier: Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        lines.forEach { line ->
            Text(
                line.text,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                fontSize = 13.5.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun CenterHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.5.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}
