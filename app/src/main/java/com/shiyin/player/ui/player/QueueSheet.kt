package com.shiyin.player.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiyin.player.ui.components.TrackRow

/**
 * 待播队列。
 *
 * 在这之前，队列只以播放页角落一行「队列 12 首」的形式存在 —— 知道有几首，
 * 但看不到是哪几首，更没法改顺序。同类播放器（椒盐、Poweramp、Musicolet）都把
 * 这个面板当核心功能：点一行直接跳过去，长按能挪位置，播到一半发现排错了能清掉后半段。
 *
 * 排序没做拖拽，而是「上移 / 下移」——
 * 拖拽在长列表里容易和滚动打架，而队列通常只有几十首，点上移两下就到了。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    playerVm: PlayerViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val queue = playerVm.queue
    val currentIndex = playerVm.queueIndex
    val upcoming = (queue.size - currentIndex - 1).coerceAtLeast(0)
    val listState = rememberLazyListState()

    // 打开时把当前这首滚到视野里：队列几十首时，不然要自己找
    LaunchedEffect(currentIndex) {
        if (currentIndex in queue.indices) {
            runCatching { listState.scrollToItem((currentIndex - 1).coerceAtLeast(0)) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "待播队列",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        when {
                            queue.isEmpty() -> "队列是空的"
                            upcoming == 0 -> "共 ${queue.size} 首 · 这是最后一首"
                            else -> "共 ${queue.size} 首 · 后面还有 $upcoming 首"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
                if (upcoming > 0) {
                    TextButton(onClick = { playerVm.clearUpNext() }) {
                        Text("清空待播", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }

            if (queue.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "还没有在播的歌。随便点一首，它所在的整个列表都会排进来。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 40.dp),
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.72f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    itemsIndexed(queue, key = { index, track -> "$index#${track.id}" }) { index, track ->
                        val isCurrent = index == currentIndex
                        val isUpcoming = index > currentIndex
                        var menuOpen by remember { mutableStateOf(false) }

                        TrackRow(
                            track = track,
                            playing = isCurrent,
                            onClick = { playerVm.playAt(index) },
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isCurrent) {
                                        Text(
                                            "正在播",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(end = 4.dp),
                                        )
                                    }
                                    Box {
                                        IconButton(onClick = { menuOpen = true }) {
                                            Icon(
                                                Icons.Rounded.MoreVert,
                                                contentDescription = "队列操作",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                            DropdownMenuItem(
                                                text = { Text("立即播放", fontSize = 13.sp) },
                                                leadingIcon = {
                                                    Icon(Icons.Rounded.PlaylistPlay, null, modifier = Modifier.size(17.dp))
                                                },
                                                onClick = {
                                                    menuOpen = false
                                                    playerVm.playAt(index)
                                                },
                                            )
                                            if (isUpcoming) {
                                                DropdownMenuItem(
                                                    text = { Text("上移一位", fontSize = 13.sp) },
                                                    leadingIcon = {
                                                        Icon(Icons.Rounded.ArrowUpward, null, modifier = Modifier.size(17.dp))
                                                    },
                                                    onClick = {
                                                        menuOpen = false
                                                        playerVm.moveInQueue(index, index - 1)
                                                    },
                                                )
                                            }
                                            if (index < queue.lastIndex) {
                                                DropdownMenuItem(
                                                    text = { Text("下移一位", fontSize = 13.sp) },
                                                    leadingIcon = {
                                                        Icon(Icons.Rounded.ArrowDownward, null, modifier = Modifier.size(17.dp))
                                                    },
                                                    onClick = {
                                                        menuOpen = false
                                                        playerVm.moveInQueue(index, index + 1)
                                                    },
                                                )
                                            }
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        if (isCurrent) "移除并播下一首" else "从队列移除",
                                                        fontSize = 13.sp,
                                                    )
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Rounded.Close, null, modifier = Modifier.size(17.dp))
                                                },
                                                onClick = {
                                                    menuOpen = false
                                                    playerVm.removeFromQueue(index)
                                                },
                                            )
                                            if (queue.size > 3) {
                                                DropdownMenuItem(
                                                    text = {
                                                        Text("清空它后面的全部", fontSize = 13.sp)
                                                    },
                                                    leadingIcon = {
                                                        Icon(Icons.Rounded.DeleteOutline, null, modifier = Modifier.size(17.dp))
                                                    },
                                                    onClick = {
                                                        menuOpen = false
                                                        playerVm.clearUpNext()
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
