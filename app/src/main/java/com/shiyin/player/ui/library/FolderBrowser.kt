package com.shiyin.player.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiyin.player.data.model.FolderNode
import com.shiyin.player.data.model.FolderTree
import com.shiyin.player.data.model.Track
import com.shiyin.player.ui.components.MessageBox
import com.shiyin.player.ui.components.TrackRow

/**
 * 文件夹视图。
 *
 * 网盘里几百首歌平铺成一列，除了搜索没有别的抓手；而用户找歌时想的往往是
 * 「那首在《范特西》文件夹里」。这里按目录逐层进，点目录播放整个子树。
 *
 * 只做「看」和「放」，不做移动/删除 —— 网盘的文件操作交给网盘自己。
 */
@Composable
fun FolderBrowser(
    tracks: List<Track>,
    playingId: String?,
    onPlay: (List<Track>, Int) -> Unit,
    modifier: Modifier = Modifier,
    emptyHint: String = "这个来源还没有歌，先去扫描一次",
) {
    // 曲库换了（重扫完）就从根目录重新开始，避免停在一个已经不存在的路径上
    val tree = remember(tracks) { FolderTree.build(tracks) }
    var path by rememberSaveable(tracks.size, tracks.firstOrNull()?.id) { mutableStateOf("") }
    val node = remember(tree, path) { FolderTree.at(tree, path) }

    // 先接住返回键：在子目录里应该回上一级，而不是退出页面
    BackHandler(enabled = node.path.isNotEmpty()) {
        path = FolderTree.parentOf(node.path)
    }

    Column(modifier.fillMaxSize()) {
        if (node.path.isNotEmpty()) {
            Breadcrumbs(
                path = node.path,
                onJump = { path = it },
            )
        }

        when {
            tree.totalCount == 0 -> MessageBox(title = "这里是空的", subtitle = emptyHint)

            node.children.isEmpty() && node.direct.isEmpty() ->
                MessageBox(title = "这个文件夹是空的", subtitle = "换一个目录看看")

            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(node.children, key = { "folder:${it.path}" }) { child ->
                    FolderRow(
                        node = child,
                        onOpen = { path = child.path },
                        onPlayAll = {
                            val all = child.allTracks()
                            if (all.isNotEmpty()) onPlay(all, 0)
                        },
                    )
                }
                // 当前层直接放的歌排在子目录后面，跟文件管理器一致
                itemsIndexed(node.direct, key = { _, track -> track.id }) { index, track ->
                    TrackRow(
                        track = track,
                        playing = playingId == track.id,
                        // 队列取「这个目录直接放的歌」，不从整棵树算起：
                        // 在专辑文件夹里点第三首，下一首应该是这盘专辑的第四首
                        onClick = { onPlay(node.direct, index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Breadcrumbs(path: String, onJump: (String) -> Unit) {
    val crumbs = remember(path) { FolderTree.breadcrumbs(path) }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "根目录",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 11.5.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onJump("") }
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )
        crumbs.forEach { (full, name) ->
            Text(
                "/",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                fontSize = 11.5.sp,
            )
            val isLast = full == path
            Text(
                name,
                color = if (isLast) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                fontSize = 11.5.sp,
                fontWeight = if (isLast) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (isLast) Modifier else Modifier.clickable { onJump(full) })
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun FolderRow(node: FolderNode, onOpen: () -> Unit, onPlayAll: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                node.name,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append("${node.totalCount} 首")
                    if (node.children.isNotEmpty()) append(" · ${node.children.size} 个子目录")
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.5.sp,
            )
        }
        IconButton(onClick = onPlayAll) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = "播放这个文件夹",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp),
        )
    }
}

