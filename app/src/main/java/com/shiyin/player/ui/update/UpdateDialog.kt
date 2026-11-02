package com.shiyin.player.ui.update

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiyin.player.data.update.UpdateManager
import com.shiyin.player.ui.settings.SettingsViewModel
import java.util.Locale

/**
 * 更新弹窗。只在发现新版本（[SettingsViewModel.availableUpdate] 非 null）时出现。
 *
 * 关键原则：**绝不静默装**。这里只是把信息摆清楚（新版本号、当前版本、大小、更新说明），
 * 下载和安装都要用户亲手点。Android 8+ 若没开「安装未知应用」权限，点安装会先引导去设置。
 */
@Composable
fun UpdateDialog(vm: SettingsViewModel, onClose: () -> Unit) {
    val info = vm.availableUpdate ?: return
    val context = LocalContext.current
    val ds = vm.downloadState
    val sizeText = if (info.size != null && info.size > 0) {
        " · 约 ${"%.1f".format(Locale.US, info.size / 1024f / 1024f)} MB"
    } else {
        ""
    }

    AlertDialog(
        onDismissRequest = {
            // 点外部 / 返回键 = 「稍后」，保留 availableUpdate 以便设置页还能提示
            vm.dismissUpdate()
            onClose()
        },
        title = {
            Text(
                "发现新版本 v${info.versionName}",
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
            )
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "你当前：${vm.currentVersionName}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                if (sizeText.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        sizeText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (info.notes.isNotBlank()) {
                    Text(
                        info.notes,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                }

                // 下载进度 / 结果
                when (ds) {
                    is UpdateManager.DownloadState.Running -> {
                        LinearProgressIndicator(
                            progress = { ds.progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "下载中 ${ds.progress}%",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    is UpdateManager.DownloadState.Success -> {
                        Text(
                            "下载完成，可以安装了",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.5.sp,
                        )
                    }
                    is UpdateManager.DownloadState.Failed -> {
                        Text(
                            "下载失败：${ds.reason}",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                        )
                    }
                    else -> Unit
                }

                // 「跳过此版本」：记下来后自动检查不再弹它
                Spacer(Modifier.height(10.dp))
                TextButton(
                    onClick = {
                        vm.skipUpdate()
                        onClose()
                    },
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(horizontal = (-8).dp),
                ) {
                    Text("跳过此版本", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            when (ds) {
                is UpdateManager.DownloadState.Success -> {
                    Button(onClick = {
                        when (val r = vm.installUpdate()) {
                            UpdateManager.InstallResult.Launched -> { /* 交给系统安装器，用户点 */ }
                            UpdateManager.InstallResult.NeedsPermission ->
                                context.startActivity(UpdateManager.unknownSourcesIntent(context))
                            is UpdateManager.InstallResult.Failed ->
                                Toast.makeText(context, r.reason, Toast.LENGTH_LONG).show()
                        }
                    }) { Text("安装") }
                }
                is UpdateManager.DownloadState.Failed -> {
                    Button(onClick = { vm.startDownload() }) { Text("重试") }
                }
                is UpdateManager.DownloadState.Running -> {
                    Button(onClick = { }, enabled = false) { Text("下载中…") }
                }
                else -> {
                    Button(onClick = { vm.startDownload() }) { Text("下载并更新") }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                vm.dismissUpdate()
                onClose()
            }) { Text("稍后") }
        },
    )
}
