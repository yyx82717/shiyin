package com.shiyin.player.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shiyin.player.ui.components.AddToPlaylistHost
import com.shiyin.player.ui.update.UpdateDialog
import com.shiyin.player.ui.components.MiniPlayer
import com.shiyin.player.ui.curated.CuratedAlbumScreen
import com.shiyin.player.ui.home.HomeScreen
import com.shiyin.player.ui.home.HomeViewModel
import com.shiyin.player.ui.library.LibraryScreen
import com.shiyin.player.ui.library.PlaylistDetailScreen
import com.shiyin.player.ui.player.PlayerScreen
import com.shiyin.player.ui.player.PlayerViewModel
import com.shiyin.player.ui.search.SearchScreen
import com.shiyin.player.ui.search.SearchViewModel
import com.shiyin.player.ui.settings.SettingsScreen
import com.shiyin.player.ui.settings.SettingsViewModel

private data class TabItem(val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabItem("发现", Icons.Rounded.Home),
    TabItem("搜索", Icons.Rounded.Search),
    TabItem("我的", Icons.Rounded.Favorite),
    TabItem("设置", Icons.Rounded.Settings),
)

@Composable
fun AppRoot(settingsVm: SettingsViewModel) {
    val playerVm: PlayerViewModel = viewModel()
    val homeVm: HomeViewModel = viewModel()
    val searchVm: SearchViewModel = viewModel()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var openPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var openAlbumId by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val playlists by settingsVm.playlists.collectAsStateWithLifecycle()

    // 自动更新弹窗：仅当发现新版本（availableUpdate 的 versionCode 变化）才弹。
    // 同一版本（哪怕用户关掉/跳过）不会重复弹，只有出版本更高的才弹。
    var showUpdateDialog by remember { mutableStateOf(false) }
    LaunchedEffect(settingsVm.availableUpdate?.versionCode) {
        if (settingsVm.availableUpdate != null) showUpdateDialog = true
    }
    // 应用启动后静默查一次更新（受设置里的「自动检查」开关与当天限频控制）。
    LaunchedEffect(Unit) { settingsVm.maybeAutoCheck() }

    // 记录播放历史，并自动收进「默认列表」
    LaunchedEffect(playerVm.current?.id) {
        playerVm.current?.let { settingsVm.recordPlay(it) }
    }

    // 播放错误提示
    LaunchedEffect(playerVm.message) {
        playerVm.message?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            playerVm.consumeMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column {
                    playerVm.current?.let { track ->
                        val progress = if (playerVm.durationMs > 0) {
                            playerVm.positionMs.toFloat() / playerVm.durationMs
                        } else {
                            0f
                        }
                        MiniPlayer(
                            track = track,
                            isPlaying = playerVm.isPlaying,
                            progress = progress,
                            onToggle = { playerVm.toggle() },
                            onNext = { playerVm.next() },
                            onOpen = { showPlayer = true },
                        )
                    }
                    BottomTabs(current = tab, onSelect = { tab = it })
                }
            },
        ) { innerPadding ->
            Box(Modifier.padding(innerPadding).fillMaxSize()) {
                when (tab) {
                    0 -> HomeScreen(
                        vm = homeVm,
                        playerVm = playerVm,
                        localTracks = settingsVm.localTracks,
                        onOpenAlbum = { openAlbumId = it },
                    )
                    1 -> SearchScreen(vm = searchVm, playerVm = playerVm)
                    2 -> LibraryScreen(
                        settingsVm = settingsVm,
                        playerVm = playerVm,
                        onOpenPlaylist = { openPlaylistId = it },
                    )
                    else -> SettingsScreen(vm = settingsVm, onOpenUpdate = { showUpdateDialog = true })
                }
            }
        }

        openPlaylistId?.let { playlistId ->
            PlaylistDetailScreen(
                playlistId = playlistId,
                settingsVm = settingsVm,
                playerVm = playerVm,
                onBack = { openPlaylistId = null },
            )
        }

        openAlbumId?.let { albumId ->
            CuratedAlbumScreen(
                albumId = albumId,
                localTracks = settingsVm.localTracks,
                playerVm = playerVm,
                onBack = { openAlbumId = null },
            )
        }

        if (showPlayer && playerVm.current != null) {
            PlayerScreen(
                playerVm = playerVm,
                settingsVm = settingsVm,
                onClose = { showPlayer = false },
            )
        }

        // 自动更新弹窗：发现新版本时由上面的 LaunchedEffect 拉起
        if (showUpdateDialog && settingsVm.availableUpdate != null) {
            UpdateDialog(vm = settingsVm, onClose = { showUpdateDialog = false })
        }

        // 全局只有一个「加入播放列表」弹窗，搜索结果、本地、专辑、播放页都复用它
        AddToPlaylistHost(
            track = playerVm.addTarget,
            playlists = playlists,
            onPick = { playlistId ->
                playerVm.addTarget?.let { settingsVm.addToPlaylist(playlistId, it) }
            },
            onCreate = { name ->
                val target = playerVm.addTarget
                settingsVm.createPlaylist(name) { playlistId ->
                    target?.let { settingsVm.addToPlaylist(playlistId, it) }
                }
            },
            onPlayNext = { playerVm.addTarget?.let { playerVm.playNext(it) } },
            onDismiss = { playerVm.clearAddTarget() },
        )
    }
}

@Composable
private fun BottomTabs(current: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(top = 8.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TABS.forEachIndexed { index, item ->
            val selected = current == index
            val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onSelect(index) }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Icon(item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(21.dp))
                Spacer(Modifier.height(3.dp))
                Text(
                    item.label,
                    color = tint,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
