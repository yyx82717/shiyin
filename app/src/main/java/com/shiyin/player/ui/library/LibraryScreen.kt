package com.shiyin.player.ui.library

import android.app.Activity
import android.content.IntentSender
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiyin.player.data.model.DEFAULT_PLAYLIST_ID
import com.shiyin.player.data.model.M3u
import com.shiyin.player.data.model.M3uImport
import com.shiyin.player.data.model.Playlist
import com.shiyin.player.data.model.Track
import com.shiyin.player.ui.components.ConfirmDialog
import com.shiyin.player.ui.components.LoadingBox
import com.shiyin.player.ui.components.MessageBox
import com.shiyin.player.ui.components.PillChip
import com.shiyin.player.ui.components.TextInputDialog
import com.shiyin.player.ui.components.TrackRow
import com.shiyin.player.ui.isChinese
import com.shiyin.player.ui.player.PlayerViewModel
import com.shiyin.player.ui.settings.SettingsViewModel
import com.shiyin.player.ui.theme.HeroBrush

private const val TAB_FAVORITES = 0
private const val TAB_HISTORY = 1
private const val TAB_LOCAL = 2
private const val TAB_DOWNLOADS = 3
private const val TAB_PLAYLISTS = 4
private const val TAB_REMOTE = 5
private const val TAB_BAIDU = 6

@Composable
fun LibraryScreen(
    settingsVm: SettingsViewModel,
    playerVm: PlayerViewModel,
    onOpenPlaylist: (String) -> Unit,
) {
    val favorites by settingsVm.favorites.collectAsStateWithLifecycle()
    val history by settingsVm.history.collectAsStateWithLifecycle()
    val downloads by settingsVm.downloads.collectAsStateWithLifecycle()
    val playlists by settingsVm.playlists.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(TAB_FAVORITES) }
    var showCreate by remember { mutableStateOf(false) }
    var deletingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var clearingDefault by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Track?>(null) }
    var localOnlyChinese by rememberSaveable { mutableStateOf(false) }
    // 「平铺 / 文件夹」是三个来源共用的偏好：来回切 tab 不用重新点一次
    var folderMode by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val localTracks = settingsVm.localTracks

    // 切到「本地」时补扫一次；已有快照会立刻显示，不会先白屏
    LaunchedEffect(tab) {
        if (tab == TAB_LOCAL) settingsVm.loadLocal()
        // 两个网盘页都有落盘快照，切过来直接出列表，后台再补扫
        if (tab == TAB_REMOTE) settingsVm.loadRemote()
        if (tab == TAB_BAIDU) settingsVm.loadBaidu()
    }

    // 删除结果提示
    LaunchedEffect(settingsVm.deleteMessage) {
        settingsVm.consumeDeleteMessage()?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    // Android 10+ 删除别人的文件要先过系统确认弹窗
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val track = settingsVm.pendingDelete
        if (result.resultCode == Activity.RESULT_OK && track != null) {
            settingsVm.onLocalDeleted(track)
        } else {
            settingsVm.cancelDelete()
        }
    }

    LaunchedEffect(settingsVm.pendingDelete) {
        val track = settingsVm.pendingDelete ?: return@LaunchedEffect
        val sender: IntentSender? = settingsVm.consentSender()
        if (sender != null) {
            consentLauncher.launch(IntentSenderRequest.Builder(sender).build())
        } else {
            settingsVm.cancelDelete()
        }
    }

    // Android 9 及以下删文件需要写权限
    var retryAfterPermission by remember { mutableStateOf<Track?>(null) }
    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val track = retryAfterPermission
        retryAfterPermission = null
        if (granted && track != null) settingsVm.deleteLocalTrack(track)
    }

    fun requestDelete(track: Track) {
        val needsWrite = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        if (needsWrite) {
            retryAfterPermission = track
            writePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            settingsVm.deleteLocalTrack(track)
        }
    }

    val downloadedTracks = downloads.map { it.track }

    // ---- 从 m3u8 导入歌单 ----
    // 只挑得动的：把 m3u 里的记录跟「本地 + 下载 + 网盘 + 百度 + 收藏」全部对一遍，
    // 对不上的直接告诉用户几首，不硬塞进歌单。
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val text = context.contentResolver.openInputStream(uri)
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
                .orEmpty()
            val entries = M3u.parse(text)
            val library = (localTracks + downloadedTracks + settingsVm.remoteTracks +
                settingsVm.baiduTracks + favorites).distinctBy { it.id }
            val result = M3uImport.match(entries, library)
            val fallbackName = uri.lastPathSegment?.substringAfterLast('/') ?: "导入的歌单"
            val name = M3u.playlistName(text)
                ?: fallbackName.substringBeforeLast('.').ifBlank { "导入的歌单" }
            if (result.matched.isEmpty()) {
                Toast.makeText(context, "没对上任何曲目（${entries.size} 条记录）", Toast.LENGTH_LONG).show()
            } else {
                settingsVm.importPlaylist(name, result.matched)
                Toast.makeText(context, "${result.summary()}，已导入「$name」", Toast.LENGTH_LONG).show()
                tab = TAB_PLAYLISTS
            }
        }.onFailure {
            Toast.makeText(context, "读不了这个文件：${it.message ?: "格式不支持"}", Toast.LENGTH_LONG).show()
        }
    }

    val baseList: List<Track> = when (tab) {
        TAB_FAVORITES -> favorites
        TAB_HISTORY -> history
        TAB_LOCAL -> localTracks
        TAB_DOWNLOADS -> downloadedTracks
        TAB_REMOTE -> settingsVm.remoteTracks
        TAB_BAIDU -> settingsVm.baiduTracks
        else -> emptyList()
    }
    val list = if (tab == TAB_LOCAL && localOnlyChinese) baseList.filter { it.isChinese() } else baseList

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(6.dp))
            Text(
                "我的",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PillChip("收藏 ${favorites.size}", tab == TAB_FAVORITES) { tab = TAB_FAVORITES }
                PillChip("最近", tab == TAB_HISTORY) { tab = TAB_HISTORY }
                PillChip(
                    if (localTracks.isEmpty()) "本地" else "本地 ${localTracks.size}",
                    tab == TAB_LOCAL,
                ) { tab = TAB_LOCAL }
                PillChip(
                    if (downloads.isEmpty()) "下载" else "下载 ${downloads.size}",
                    tab == TAB_DOWNLOADS,
                ) { tab = TAB_DOWNLOADS }
                PillChip(
                    if (settingsVm.remoteTracks.isEmpty()) "网盘" else "网盘 ${settingsVm.remoteTracks.size}",
                    tab == TAB_REMOTE,
                ) { tab = TAB_REMOTE }
                PillChip(
                    if (settingsVm.baiduTracks.isEmpty()) "百度网盘" else "百度 ${settingsVm.baiduTracks.size}",
                    tab == TAB_BAIDU,
                ) { tab = TAB_BAIDU }
                PillChip("歌单 ${playlists.size}", tab == TAB_PLAYLISTS) { tab = TAB_PLAYLISTS }
            }

            if (tab == TAB_LOCAL) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillChip("全部 ${localTracks.size}", !localOnlyChinese && !folderMode) {
                        localOnlyChinese = false
                        folderMode = false
                    }
                    PillChip("只看华语", localOnlyChinese && !folderMode) {
                        localOnlyChinese = true
                        folderMode = false
                    }
                    PillChip("按文件夹", folderMode) { folderMode = !folderMode }
                    PillChip(if (settingsVm.localLoading) "扫描中…" else "重新扫描", false) {
                        settingsVm.loadLocal(force = true)
                    }
                }
            }

            if (tab == TAB_PLAYLISTS) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillChip("从 m3u8 导入", false) { importLauncher.launch(arrayOf("*/*")) }
                    PillChip("新建歌单", false) { showCreate = true }
                }
            }

            if (tab == TAB_REMOTE) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillChip("按文件夹", folderMode) { folderMode = !folderMode }
                    PillChip(if (settingsVm.remoteLoading) "扫描中…" else "重新扫描", false) {
                        settingsVm.loadRemote(force = true)
                    }
                    // 列表来自本地快照，告诉用户这是什么时候扫的，别以为没更新
                    PillChip(settingsVm.remoteScanLabel, false) {}
                }
            }

            if (tab == TAB_BAIDU) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillChip("按文件夹", folderMode) { folderMode = !folderMode }
                    PillChip(if (settingsVm.baiduLoading) "读取中…" else "重新扫描", false) {
                        settingsVm.loadBaidu(force = true)
                    }
                    PillChip(settingsVm.baiduScanLabel, false) {}
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        Box(Modifier.weight(1f)) {
            when {
                tab == TAB_PLAYLISTS -> PlaylistList(
                    playlists = playlists,
                    onCreate = { showCreate = true },
                    onOpen = onOpenPlaylist,
                    onDelete = { deletingPlaylist = it },
                    onClearDefault = { clearingDefault = true },
                )

                folderMode && (tab == TAB_LOCAL || tab == TAB_REMOTE || tab == TAB_BAIDU) ->
                    FolderBrowser(
                        tracks = list,
                        playingId = playerVm.current?.id,
                        onPlay = { queue, index -> playerVm.play(queue, index) },
                        emptyHint = emptySubtitle(tab, settingsVm, localOnlyChinese),
                    )

                tab == TAB_LOCAL && settingsVm.localLoading && list.isEmpty() ->
                    LoadingBox(hint = "正在扫描手机里的音频文件…")

                tab == TAB_REMOTE && settingsVm.remoteLoading && list.isEmpty() ->
                    LoadingBox(hint = "正在遍历网盘目录…\n目录多的话要等几十秒")

                tab == TAB_BAIDU && settingsVm.baiduLoading && list.isEmpty() ->
                    LoadingBox(hint = "正在读取百度网盘目录…")

                list.isEmpty() -> MessageBox(
                    title = emptyTitle(tab, localOnlyChinese),
                    subtitle = emptySubtitle(tab, settingsVm, localOnlyChinese),
                )

                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    itemsIndexed(list, key = { _, track -> track.id }) { index, track ->
                        TrackRow(
                            track = track,
                            playing = playerVm.current?.id == track.id,
                            onClick = { playerVm.play(list, index) },
                            trailing = {
                                RowTrailing(
                                    tab = tab,
                                    track = track,
                                    favorites = favorites,
                                    settingsVm = settingsVm,
                                    onAddToPlaylist = { playerVm.requestAddToPlaylist(track) },
                                    onDeleteFile = { deleteTarget = track },
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        TextInputDialog(
            title = "新建歌单",
            onConfirm = { name -> settingsVm.createPlaylist(name) },
            onDismiss = { showCreate = false },
        )
    }

    deletingPlaylist?.let { playlist ->
        ConfirmDialog(
            title = "删除歌单",
            message = "「${playlist.name}」将被删除，歌本身不会从曲库消失。",
            onConfirm = { settingsVm.deletePlaylist(playlist.id) },
            onDismiss = { deletingPlaylist = null },
        )
    }

    if (clearingDefault) {
        ConfirmDialog(
            title = "清空默认列表",
            message = "只清空这个自动收录的列表，手机里的文件和收藏都不受影响。",
            confirmText = "清空",
            onConfirm = { settingsVm.clearDefaultPlaylist() },
            onDismiss = { clearingDefault = false },
        )
    }

    deleteTarget?.let { track ->
        ConfirmDialog(
            title = "删除本地文件",
            message = "会从手机存储里删掉《${track.title}》这个音频文件，无法恢复（系统可能会再弹一次确认）。",
            onConfirm = { requestDelete(track) },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun RowTrailing(
    tab: Int,
    track: Track,
    favorites: List<Track>,
    settingsVm: SettingsViewModel,
    onAddToPlaylist: () -> Unit,
    onDeleteFile: () -> Unit,
) {
    val liked = favorites.any { it.id == track.id }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onAddToPlaylist) {
            Icon(
                Icons.Rounded.PlaylistAdd,
                contentDescription = "加入播放列表",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }

        when (tab) {
            TAB_DOWNLOADS -> IconButton(onClick = { settingsVm.deleteDownload(track) }) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "删除下载",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }

            TAB_LOCAL -> LocalRowMenu(
                liked = liked,
                onToggleFavorite = { settingsVm.toggleFavorite(track) },
                onDeleteFile = onDeleteFile,
            )

            else -> IconButton(onClick = { settingsVm.toggleFavorite(track) }) {
                Icon(
                    if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "收藏",
                    tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** 本地曲目： ⋮ 菜单里放收藏与「删除原文件」，避免一行挤四个图标。 */
@Composable
private fun LocalRowMenu(
    liked: Boolean,
    onToggleFavorite: () -> Unit,
    onDeleteFile: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = "更多",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(if (liked) "取消收藏" else "收藏", fontSize = 13.sp) },
                onClick = {
                    expanded = false
                    onToggleFavorite()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        "删除原文件",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    onDeleteFile()
                },
            )
        }
    }
}

@Composable
private fun PlaylistList(
    playlists: List<Playlist>,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (Playlist) -> Unit,
    onClearDefault: () -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(HeroBrush)
                        .clickable(onClick = onCreate)
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = null,
                        tint = Color(0xFF1A1033),
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.size(5.dp))
                    Text("新建歌单", color = Color(0xFF1A1033), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        items(playlists, key = { it.id }) { playlist ->
            val isDefault = playlist.id == DEFAULT_PLAYLIST_ID
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(playlist.id) }
                    .padding(horizontal = 20.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isDefault) Icons.Rounded.AutoAwesome else Icons.Rounded.PlaylistPlay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        playlist.name,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (isDefault) {
                            "${playlist.tracks.size} 首 · 听过的歌自动收进来"
                        } else {
                            "${playlist.tracks.size} 首"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
                if (isDefault) {
                    if (playlist.tracks.isNotEmpty()) {
                        IconButton(onClick = onClearDefault) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = "清空默认列表",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                } else {
                    IconButton(onClick = { onDelete(playlist) }) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "删除歌单",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        if (playlists.none { it.id != DEFAULT_PLAYLIST_ID }) {
            item {
                MessageBox(
                    title = "还没有自己的歌单",
                    subtitle = "点上面的「新建歌单」，或者在任何列表里点 + 把歌收进来。默认列表已经在自动记录你听过的歌。",
                )
            }
        }
    }
}

private fun emptyTitle(tab: Int, chineseOnly: Boolean = false): String = when (tab) {
    TAB_FAVORITES -> "还没有收藏的歌"
    TAB_HISTORY -> "还没有播放记录"
    TAB_LOCAL -> if (chineseOnly) "本地没有华语歌" else "本地曲库是空的"
    TAB_DOWNLOADS -> "还没有下载的歌"
    TAB_REMOTE -> "网盘里没扫到歌"
    TAB_BAIDU -> "百度网盘里没读到歌"
    else -> ""
}

private fun emptySubtitle(tab: Int, settingsVm: SettingsViewModel, chineseOnly: Boolean = false): String = when (tab) {
    TAB_FAVORITES -> "在播放页点♥，曲子就会出现在这里"
    TAB_HISTORY -> "听过的歌会自动记在这里，最多保留 80 首"
    TAB_LOCAL -> when {
        chineseOnly -> "把「只看华语」切回「全部」，或者把华语歌拷进手机后重新扫描。"
        !settingsVm.hasLocalPermission() ->
            "到「设置 - 音源 - 本地音乐」授权读取音频文件，手机里的歌就会全部出现在这里。"
        // 这一条最容易被误解：电脑上的文件夹不会自动出现在「本地」
        else -> "「本地」只认手机自己的存储。\n" +
            "电脑上的 F:\\KwDownload\\song 要通过 AList 挂出来，在「网盘」里看 —— 也就是设置里的 WebDAV 地址。"
    }
    TAB_DOWNLOADS -> "在播放页点下载图标，曲子会存到手机里，之后断网也能听"
    TAB_REMOTE ->
        if (!settingsVm.webdav.value.usable) {
            "到「设置 → 音源 → 网盘音乐 · WebDAV」填上 AList 的地址（例如 http://192.168.8.192:5244/dav），" +
                "点「保存并扫描」，这里就会出现网盘里的全部歌曲。"
        } else {
            "地址已经配好了，但这次没扫到音频文件。\n" +
                "确认 AList 里那个音乐目录挂上了、文件是 mp3 / flac / m4a / wav 这类格式，" +
                "然后点上面的「重新扫描」。"
        }
    TAB_BAIDU ->
        if (!settingsVm.baiduConfig.value.usable) {
            "到「设置 → 音源 → 百度网盘 · 直连」点「一键授权登录」，登录同意后会自动返回。" +
                "App 就能直接读你网盘里的音乐目录 —— 不用 AList，也不用开电脑。"
        } else {
            "授权看起来是通的，但这个目录里没读到音频文件。\n" +
                "确认「音乐目录」填的是网盘里真实存在的路径（默认 /我的音乐），" +
                "或者点上面的「重新扫描」。"
        }
    else -> ""
}
