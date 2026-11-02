package com.shiyin.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import com.shiyin.player.data.audio.EQ_BAND_COUNT
import com.shiyin.player.data.audio.EQ_BAND_FREQS
import com.shiyin.player.data.audio.EQ_MAX_DB
import com.shiyin.player.data.audio.EQ_MIN_DB
import com.shiyin.player.data.audio.EqPresets
import com.shiyin.player.data.audio.bandLabel
import com.shiyin.player.data.model.AudioQualityOf
import com.shiyin.player.ui.components.PillChip
import com.shiyin.player.ui.settings.SettingsViewModel
import kotlin.math.roundToInt

/**
 * 播放设置面板：倍速、睡眠定时、淡入淡出、均衡器。
 *
 * 放在播放页而不是设置页，是因为这四项都是「听着听着想调一下」的东西，
 * 来回跳页面会打断听歌。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSheet(
    playerVm: PlayerViewModel,
    settingsVm: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val eq = settingsVm.eqSettings

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                "播放设置",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )

            // ---------------- 倍速 ----------------
            SectionTitle("倍速")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                    val active = kotlin.math.abs(playerVm.speed - value) < 0.01f
                    PillChip(speedLabel(value), active) { playerVm.applySpeed(value) }
                }
            }
            Hint(
                if (kotlin.math.abs(playerVm.speed - 1f) < 0.01f) {
                    "变速不变调（用的是 Sonic 时间伸缩，不是快进磁带）。"
                } else {
                    "当前 ${speedLabel(playerVm.speed)}，音高保持原样。"
                },
            )

            // ---------------- 睡眠定时 ----------------
            SectionTitle("睡眠定时")
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PillChip("关闭", playerVm.sleepRemainingMs < 0 && !playerVm.sleepAtTrackEnd) {
                    playerVm.setSleepTimer(0)
                }
                listOf(15, 30, 45, 60, 90).forEach { minutes ->
                    PillChip("$minutes 分", false) { playerVm.setSleepTimer(minutes) }
                }
                PillChip("播完这首", playerVm.sleepAtTrackEnd) {
                    playerVm.setSleepOnTrackEnd(!playerVm.sleepAtTrackEnd)
                }
            }
            Hint(
                when {
                    playerVm.sleepAtTrackEnd -> "当前这首放完就暂停，队列和进度都留着。"
                    playerVm.sleepRemainingMs >= 0 ->
                        "还剩 ${formatRemaining(playerVm.sleepRemainingMs)}，到点会先淡出再暂停。"
                    else -> "定时只暂停不停止：醒了按一下播放就能接着听。"
                },
            )

            // ---------------- 淡入淡出 ----------------
            SectionTitle("淡入淡出")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 500, 1_000, 2_000, 3_000).forEach { ms ->
                    val active = settingsVm.fadeMs == ms
                    PillChip(if (ms == 0) "关" else "${ms / 1000f}秒".replace(".0秒", "秒"), active) {
                        settingsVm.applyFade(ms)
                    }
                }
            }
            Hint(
                if (settingsVm.fadeMs == 0) {
                    "关闭时切歌和暂停都是硬切。"
                } else {
                    "新歌从静音推上来，暂停和定时到点先淡出再停。" +
                        "真正的交叉淡入需要两个播放器同时出声，这里做不到。"
                },
            )

            // ---------------- 均衡器 ----------------
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "均衡器",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (eq.enabled) "10 段" else "已关闭",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.5.sp,
                )
                Spacer(Modifier.weight(1f))
                Switch(checked = eq.enabled, onCheckedChange = { settingsVm.setEqEnabled(it) })
            }

            if (eq.enabled) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EqPresets.ALL.forEach { preset ->
                        PillChip(preset.label, eq.preset == preset.id) { settingsVm.applyEqPreset(preset) }
                    }
                    PillChip("归零", eq.isFlat) { settingsVm.resetEq() }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    for (index in 0 until EQ_BAND_COUNT) {
                        BandSlider(
                            freqLabel = bandLabel(EQ_BAND_FREQS[index]),
                            gain = eq.gains.getOrElse(index) { 0f },
                            onChange = { settingsVm.setEqGain(index, it, persist = false) },
                            onCommit = { settingsVm.commitEqGain(index) },
                        )
                    }
                }

                Hint("每段 ±12dB。手机上外放听不出低频段的变化，插耳机再调会更明显。")
            }

            // ---------------- 音频信息 / 体检 ----------------
            playerVm.current?.let { track ->
                SectionTitle("当前音频")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        AudioQualityOf.badge(track).ifBlank { "格式未知" },
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    if (track.bitrateKbps > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${track.bitrateKbps} kbps",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.5.sp,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    PillChip(if (playerVm.probing) "读取中…" else "检查文件头", false) {
                        playerVm.probeCurrentAudio()
                    }
                }
                playerVm.probeMessage?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        message,
                        color = if (message.startsWith("✓")) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp,
                    )
                }
                Hint(
                    "放出来是沙沙声时点一下：网盘上不少「.wav」其实是 DTS / AC-3 码流，" +
                        "手机没有对应的解码器，只能换一个文件。",
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.5.sp,
        lineHeight = 15.sp,
        modifier = Modifier.padding(top = 8.dp),
    )
}

private fun speedLabel(value: Float): String {
    val rounded = (value * 100).roundToInt()
    return if (rounded % 100 == 0) "${rounded / 100}×" else "${rounded / 100f}×"
}

private fun formatRemaining(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val minutes = total / 60
    val seconds = total % 60
    return if (minutes > 0) "${minutes} 分 ${seconds} 秒" else "${seconds} 秒"
}

/**
 * 一个频段的竖向推子。
 *
 * Compose 没有竖向 Slider，旋转 90° 的那个写法在测量时会互相打架（旋转后
 * 占位尺寸不变，手指位置和绘制位置对不上）。这里直接按 y 坐标换算增益：
 * 顶部 +12dB、中间 0、底部 -12dB。
 */
@Composable
private fun BandSlider(
    freqLabel: String,
    gain: Float,
    onChange: (Float) -> Unit,
    onCommit: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (kotlin.math.abs(gain) < 0.1f) "0" else "${if (gain > 0) "+" else ""}${gain.roundToInt()}",
            color = if (kotlin.math.abs(gain) < 0.1f) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
            fontSize = 9.5.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))

        val trackColor = MaterialTheme.colorScheme.surfaceVariant
        val activeColor = MaterialTheme.colorScheme.primary
        val ratio = (gain / EQ_MAX_DB).coerceIn(-1f, 1f)

        Box(
            Modifier
                .height(118.dp)
                .width(26.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        onChange(gainAt(down.position, size.height))
                        drag(down.id) { change ->
                            onChange(gainAt(change.position, size.height))
                        }
                        onCommit()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.height(112.dp).width(6.dp),
            ) {
                // 轨道
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(3.dp))
                        .background(trackColor),
                )
                // 填充从中线向外长：上正下负，跟硬件面板的方向相反才会「往上抬」
                Column(Modifier.matchParentSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        if (ratio > 0f) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(ratio)
                                    .background(activeColor),
                            )
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        if (ratio < 0f) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(-ratio)
                                    .background(Color(0xFF8A8A8A).copy(alpha = 0.85f)),
                            )
                        }
                    }
                }
                // 中线
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            freqLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp,
            maxLines = 1,
        )
    }
}

/** y 坐标 → 增益：顶部 +12dB、底部 -12dB。 */
private fun gainAt(position: Offset, heightPx: Int): Float {
    if (heightPx <= 0) return 0f
    val ratio = 1f - (position.y / heightPx) // 0 在上
    return ((ratio * 2f - 1f) * EQ_MAX_DB).coerceIn(EQ_MIN_DB, EQ_MAX_DB)
}
