package com.shiyin.player.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiyin.player.BuildConfig
import com.shiyin.player.data.local.SettingsStore
import com.shiyin.player.ui.components.PillChip
import com.shiyin.player.ui.theme.HeroBrush

@Composable
fun SettingsScreen(vm: SettingsViewModel, onOpenUpdate: () -> Unit = {}) {
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val jamendoId by vm.jamendoId.collectAsStateWithLifecycle()
    val localEnabled by vm.localEnabled.collectAsStateWithLifecycle()
    val subsonic by vm.subsonic.collectAsStateWithLifecycle()
    val webdav by vm.webdav.collectAsStateWithLifecycle()

    var jamendoInput by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    LaunchedEffect(jamendoId) {
        if (jamendoInput.isBlank()) jamendoInput = jamendoId
    }

    // —— 本地库权限 ——
    var granted by remember { mutableStateOf(vm.hasLocalPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { ok ->
        granted = ok
        if (ok) vm.loadLocal(force = true)
    }

    // —— Subsonic / WebDAV 表单 ——
    var subUrl by remember { mutableStateOf("") }
    var subUser by remember { mutableStateOf("") }
    var subPass by remember { mutableStateOf("") }
    LaunchedEffect(subsonic) {
        subUrl = subsonic.baseUrl
        subUser = subsonic.user
        subPass = subsonic.password
    }

    var davUrl by remember { mutableStateOf("") }
    var davUser by remember { mutableStateOf("") }
    var davPass by remember { mutableStateOf("") }
    LaunchedEffect(webdav) {
        davUrl = webdav.baseUrl
        davUser = webdav.user
        davPass = webdav.password
    }

    // —— 百度网盘直连表单 ——
    val baidu by vm.baiduConfig.collectAsStateWithLifecycle()
    var baiduToken by remember { mutableStateOf("") }
    var baiduRoot by remember { mutableStateOf("/我的音乐") }
    LaunchedEffect(baidu) {
        baiduToken = baidu.refreshToken
        baiduRoot = baidu.rootDir
    }

    // 一键授权：WebView 里登录百度，把页面上的授权码读出来，回来换 token。
    // 回传的既可能是 32 位授权码，也可能是整串 refresh_token，交给 ViewModel 自己认。
    val context = LocalContext.current
    val baiduAuthLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val text = result.data?.getStringExtra(BaiduAuthActivity.EXTRA_TEXT)
        if (result.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) {
            baiduToken = text
            vm.applyBaiduCredential(text, baiduRoot)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(6.dp))
        Text(
            "设置",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        )

        Spacer(Modifier.height(22.dp))
        SectionTitle("外观")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillChip("跟随系统", themeMode == SettingsStore.THEME_FOLLOW_SYSTEM) { vm.setTheme(SettingsStore.THEME_FOLLOW_SYSTEM) }
            PillChip("深色", themeMode == SettingsStore.THEME_DARK) { vm.setTheme(SettingsStore.THEME_DARK) }
            PillChip("浅色", themeMode == SettingsStore.THEME_LIGHT) { vm.setTheme(SettingsStore.THEME_LIGHT) }
        }

        Spacer(Modifier.height(26.dp))
        SectionTitle("音源")
        Text(
            "这里的每个源服务的都是「你有权访问的内容」：你自己的文件、你自己的服务器、你自己的网盘，以及开放授权曲库。四个源可以同时开，搜索时会一起搜。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.5.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        // ---------- 本地音乐 ----------
        CardBlock {
            CardHeader(
                name = "本地音乐",
                tag = when {
                    !granted -> "未授权"
                    vm.localLoading -> "扫描中…"
                    vm.localTracks.isNotEmpty() -> "${vm.localTracks.size} 首"
                    else -> "已授权"
                },
                highlight = granted,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "手机存储里的 mp3 / flac / m4a / wav 等音频文件全部纳入曲库，自动读专辑封面，不需要联网。" +
                    "扫描结果会存下来，重开应用直接显示，不用再等一遍扫描；列表里可以删除原文件。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActionButton(
                    text = if (granted) "重新扫描" else "授权并扫描",
                    loading = vm.localLoading,
                ) {
                    if (granted) {
                        vm.loadLocal(force = true)
                    } else {
                        permissionLauncher.launch(vm.localPermission())
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("并入搜索", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                Spacer(Modifier.size(8.dp))
                Switch(
                    checked = localEnabled,
                    onCheckedChange = { vm.setLocalEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF1A1033),
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---------- Subsonic ----------
        CardBlock {
            CardHeader(name = "自建曲库 · Subsonic", tag = if (subsonic.usable) "已配置" else "未配置", highlight = subsonic.usable)
            Spacer(Modifier.height(6.dp))
            Text(
                "Navidrome / Airsonic / Gonic 等服务器。把收藏的音乐放在家里的电脑或群晖上，手机随时全库访问，格式和容量不受限制。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "注意：这和 WebDAV 是两套协议。AList、坚果云、群晖的 /dav 地址填在这里连不上，要填到下面的「网盘音乐」。",
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
            )
            Spacer(Modifier.height(12.dp))
            MiniField("服务器地址", subUrl, { subUrl = it }, placeholder = "http://192.168.1.10:4533")
            Spacer(Modifier.height(8.dp))
            MiniField("用户名", subUser, { subUser = it }, placeholder = "yourname")
            Spacer(Modifier.height(8.dp))
            MiniField("密码", subPass, { subPass = it }, placeholder = "登录密码", password = true)
            Spacer(Modifier.height(12.dp))
            ActionButton(
                text = if (vm.testing == "subsonic") "连接中…" else "保存并测试",
                loading = vm.testing == "subsonic",
            ) {
                vm.saveSubsonic(subUrl, subUser, subPass)
                vm.testSubsonic()
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---------- 百度网盘直连 ----------
        CardBlock {
            CardHeader(
                name = "百度网盘 · 直连",
                tag = if (baidu.usable) "已配置" else "未配置",
                highlight = baidu.usable,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "用你自己的百度账号授权一次，App 就直接读网盘里的音乐目录 —— 不需要 AList、不需要开电脑、不需要任何服务器。手机有网就能听。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(12.dp))
            ActionButton(
                text = if (vm.testing == "baidu") "处理中…" else "一键授权登录",
                loading = vm.testing == "baidu",
            ) {
                baiduAuthLauncher.launch(Intent(context, BaiduAuthActivity::class.java))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "授权在百度自己的页面完成，App 只拿授权码换访问凭证，密码不经过这里。" +
                    "授权不会被自动取消，除非你在百度账号里手动解除。",
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "也可以直接贴一串凭据：百度页面上的 32 位授权码、或一长串 refresh_token，两种都认。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
            )
            Spacer(Modifier.height(8.dp))
            MiniField(
                "授权码 / refresh_token",
                baiduToken,
                { baiduToken = it },
                placeholder = "32 位授权码，或 122. 开头的长串",
                password = false,
            )
            Spacer(Modifier.height(8.dp))
            MiniField("音乐目录", baiduRoot, { baiduRoot = it }, placeholder = "/我的音乐")
            Spacer(Modifier.height(12.dp))
            ActionButton(
                text = if (vm.testing == "baidu") "读取中…" else "保存并读取",
                loading = vm.testing == "baidu",
            ) {
                vm.applyBaiduCredential(baiduToken, baiduRoot)
            }
            vm.testMessage?.let { message ->
                Spacer(Modifier.height(10.dp))
                Text(
                    message,
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---------- WebDAV ----------
        CardBlock {
            CardHeader(name = "网盘音乐 · WebDAV", tag = if (webdav.usable) "已配置" else "未配置", highlight = webdav.usable)
            Spacer(Modifier.height(6.dp))
            Text(
                "填一个 WebDAV 目录，App 会递归扫描其中的音频文件。用 AList 还能把阿里云盘、夸克、OneDrive 一起挂进来，当成一个曲库用。需要中间层常开（电脑或 NAS），关机就断。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "百度网盘不用走这里 —— 上面那个「直连」更快，也不依赖电脑。",
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
            )
            Spacer(Modifier.height(12.dp))
            MiniField("目录地址", davUrl, { davUrl = it }, placeholder = "https://dav.example.com/music/")
            Spacer(Modifier.height(8.dp))
            MiniField("用户名", davUser, { davUser = it }, placeholder = "可留空（公开目录）")
            Spacer(Modifier.height(8.dp))
            MiniField("密码", davPass, { davPass = it }, placeholder = "登录密码", password = true)
            Spacer(Modifier.height(12.dp))
            ActionButton(
                text = if (vm.testing == "webdav") "扫描中…" else "保存并扫描",
                loading = vm.testing == "webdav",
            ) {
                vm.saveWebdav(davUrl, davUser, davPass)
                vm.testWebdav()
            }
            vm.testMessage?.let { message ->
                Spacer(Modifier.height(10.dp))
                Text(
                    message,
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---------- Jamendo ----------
        CardBlock {
            CardHeader(name = "Jamendo", tag = if (jamendoId.isBlank()) "未启用" else "已启用", highlight = jamendoId.isNotBlank())
            Spacer(Modifier.height(6.dp))
            Text(
                "CC 授权曲库。到 devportal.jamendo.com 免费注册即可拿到 client_id，填入后自动并入搜索结果。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(12.dp))
            TextField(
                value = jamendoInput,
                onValueChange = {
                    jamendoInput = it
                    saved = false
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text("粘贴 client_id", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActionButton(text = "保存") {
                    vm.saveJamendoId(jamendoInput)
                    saved = true
                }
                if (saved) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        "已保存，重新搜索即可看到 Jamendo 结果",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        CardBlock {
            CardHeader(name = "Audius", tag = "内置", highlight = true)
            Spacer(Modifier.height(6.dp))
            Text(
                "独立音乐人自主上传的开放曲库，无需密钥，国内可直连。首页推荐与搜索默认走这里。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
            )
        }

        Spacer(Modifier.height(26.dp))
        SectionTitle("更新")

        val updateUrl by vm.updateUrl.collectAsStateWithLifecycle()
        val autoCheck by vm.autoCheckUpdate.collectAsStateWithLifecycle()
        var urlInput by remember { mutableStateOf(updateUrl) }
        LaunchedEffect(updateUrl) { urlInput = updateUrl }
        val availableUpdate = vm.availableUpdate
        val checkState = vm.updateCheckState

        CardBlock {
            CardHeader(
                name = "自动更新",
                tag = if (autoCheck) "已开启" else "已关闭",
                highlight = autoCheck,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "填一个更新源目录地址（局域网 / 公网都行），把 update.json 和新版 apk 放进去，" +
                    "应用启动就会自动检查并提示升级；把地址删空即关闭更新。具体格式见 README。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(12.dp))
            MiniField(
                label = "更新源地址",
                value = urlInput,
                onValueChange = { urlInput = it; vm.setUpdateUrl(it) },
                placeholder = "https://example.com/shiyin/",
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("启动时自动检查", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = autoCheck,
                    onCheckedChange = { vm.setAutoCheck(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF1A1033),
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActionButton(
                    text = if (checkState == UpdateCheckState.Checking) "检查中…" else "检查更新",
                    loading = checkState == UpdateCheckState.Checking,
                ) { vm.checkUpdateManually() }
                Spacer(Modifier.weight(1f))
            }
            val checkMsg = when (checkState) {
                UpdateCheckState.Idle -> null
                UpdateCheckState.Checking -> "正在检查…"
                UpdateCheckState.NoUpdate -> "已是最新版本"
                is UpdateCheckState.Error -> checkState.message
            }
            if (checkMsg != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    checkMsg,
                    color = if (checkState is UpdateCheckState.Error) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                )
            }
            availableUpdate?.let { info ->
                Spacer(Modifier.height(10.dp))
                Text(
                    "发现新版本 v${info.versionName}（你当前 ${vm.currentVersionName}）",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.5.sp,
                )
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onOpenUpdate) { Text("查看并更新", fontSize = 12.sp) }
            }
        }
        Spacer(Modifier.height(12.dp))

        Spacer(Modifier.height(26.dp))
        SectionTitle("关于")
        CardBlock {
            Text(
                "拾音 v${BuildConfig.VERSION_NAME}",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "本应用不抓取、不缓存商业音乐平台的版权内容。要听某首歌，要么文件在你自己手上（本地/服务器/网盘），要么它来自开放授权曲库。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "想加新的音源，实现项目里的 MusicSource 接口再注册到 MusicRepository 即可，界面不用改。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
            )
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun CardBlock(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun CardHeader(name: String, tag: String, highlight: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            tag,
            color = if (highlight) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun MiniField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false,
) {
    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp)
    Spacer(Modifier.height(5.dp))
    TextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        placeholder = { Text(placeholder, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedTextColor = MaterialTheme.colorScheme.onBackground,
    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
)

@Composable
private fun ActionButton(text: String, loading: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(HeroBrush)
            .clickable(enabled = !loading, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(
                    color = Color(0xFF1A1033),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.size(6.dp))
            }
            Text(text, color = Color(0xFF1A1033), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
