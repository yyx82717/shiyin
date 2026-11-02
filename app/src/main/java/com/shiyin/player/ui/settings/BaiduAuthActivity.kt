package com.shiyin.player.ui.settings

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.shiyin.player.data.source.BaiduApi
import com.shiyin.player.ui.theme.ShiYinTheme

/**
 * 百度账号授权。
 *
 * 百度开放平台只有 oob（out-of-band）回调：授权完不跳回 App，而是在页面上显示一串
 * 32 位授权码，让用户自己复制回去。让用户在手机上复制再粘贴 83 个字符的 token，
 * 本身就是「400 服务器错误」这类问题的温床——多一个空格、少结尾一位就全废。
 *
 * 所以这里把流程收进 WebView：用户只负责登录并点「同意」，授权码由脚本从页面上读出来，
 * 直接回传给设置页去换 token。
 *
 * 关键细节：百度那串码**放在一个只读输入框里**（旁边是「复制授权码」按钮）。
 * 输入框的内容不进 `innerText`，所以只抓正文的老写法永远抓不到——这就是 1.4.1
 * 里「一键授权点了没反应」的真正原因。现在先扫 input/textarea 的 value，再退回正文。
 */
// WebView 里开 JS 是为了读取百度授权成功后页面上显示的授权码。
// 加载的地址写死在 BaiduApi.authorizeUrl()（百度官方域名），不加载任何用户输入的内容，
// 也没有 addJavascriptInterface 暴露原生对象，所以不存在 XSS 面。
@SuppressLint("SetJavaScriptEnabled")
class BaiduAuthActivity : ComponentActivity() {

    private val hint = mutableStateOf<String?>(null)

    /** 每换一页就重新数一次重试次数：登录页上数完不该影响成功页。 */
    private var attempts = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShiYinTheme {
                AuthScreen(
                    hint = hint.value,
                    onCancel = { finish() },
                    onCreated = { web ->
                        web.webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                attempts = 0
                                poll(view)
                            }
                        }
                        web.loadUrl(BaiduApi.authorizeUrl())
                    },
                    onClipboard = { useClipboard() },
                )
            }
        }
    }

    /**
     * 反复读页面，直到读出授权码。
     *
     * 页面是逐步渲染的：`onPageFinished` 触发时那一块 DOM 可能还没插进去，
     * 所以要重试。每页最多试 [MAX_ATTEMPTS] 次（约 9 秒），超时就提示用户走剪贴板，
     * 不在后台空转。
     */
    private fun poll(view: WebView) {
        if (isFinishing || isDestroyed) return
        if (attempts >= MAX_ATTEMPTS) {
            if (hint.value == null) hint.value = TIMEOUT_HINT
            return
        }
        attempts++
        view.evaluateJavascript(EXTRACT_JS) { raw ->
            val code = raw?.trim()?.trim('"').orEmpty()
            if (BaiduApi.looksLikeAuthCode(code)) {
                finishWith(code)
            } else {
                Handler(Looper.getMainLooper()).postDelayed({ poll(view) }, RETRY_MS)
            }
        }
    }

    /**
     * 兜底：用户自己点了百度页面上的「复制授权码」，或者手上本来就有 refresh_token。
     *
     * 读剪贴板不需要弹权限框（前台 App 可以直接读），比让用户长按输入框再切回来省事。
     */
    private fun useClipboard() {
        val text = runCatching {
            val cm = getSystemService(ClipboardManager::class.java)
            if (cm == null || cm.primaryClip == null || cm.primaryClip!!.itemCount == 0) {
                ""
            } else {
                cm.primaryClip!!.getItemAt(0).coerceToText(this)?.toString().orEmpty()
            }
        }.getOrDefault("")

        val clean = BaiduApi.sanitizeCredential(text)
        when {
            BaiduApi.looksLikeAuthCode(clean) -> finishWith(clean)
            BaiduApi.looksLikeRefreshToken(clean) -> finishWith(clean)
            else -> hint.value = CLIPBOARD_HINT
        }
    }

    private fun finishWith(value: String) {
        if (isFinishing) return
        setResult(RESULT_OK, Intent().putExtra(EXTRA_TEXT, value))
        finish()
    }

    companion object {
        /** 回传的是「一段凭据文字」，可能 32 位授权码，也可能整串 refresh_token。 */
        const val EXTRA_TEXT = "credential"

        private const val MAX_ATTEMPTS = 15
        private const val RETRY_MS = 600L

        private const val TIMEOUT_HINT =
            "没自动识别到授权码。点页面上那个蓝色的「复制授权码」，再点下面的「从剪贴板读取」。"

        private const val CLIPBOARD_HINT =
            "剪贴板里没有授权码。先在百度页面点「复制授权码」，再回来点这个按钮。"

        /**
         * 三步找码：
         * 1. `input` / `textarea` 的 value —— 百度把码放在只读输入框里，这是主路径；
         * 2. 正文里「授权码：xxx」这种带标签的写法；
         * 3. 正文里任意一段独立的 32 位十六进制。
         *
         * 第 1 步必须先确认页面上出现了「授权码」三个字，否则登录页上某个 32 位
         * 隐藏域会被误当成授权码拿去换 token，白白浪费一次机会。
         */
        private const val EXTRACT_JS = """(function(){
  var t = document.body ? document.body.innerText : '';
  if (t.indexOf('授权码') < 0) { return ''; }
  function ok(s){ return /^[0-9A-Za-z]{32}$/.test(s); }
  var e = document.querySelectorAll('input, textarea');
  for (var i = 0; i < e.length; i++) {
    var v = (e[i].value || '').replace(/\s+/g, '');
    if (ok(v)) { return v; }
  }
  var m = t.match(/授权码[^0-9A-Za-z]{0,8}([0-9A-Za-z]{32})/);
  if (m) { return m[1]; }
  m = t.match(/(?:^|[^0-9A-Za-z])([0-9a-fA-F]{32})(?![0-9A-Za-z])/);
  return m ? m[1] : '';
})()"""
    }
}

// WebView 里开 JS 是为了读取百度授权成功后页面上显示的授权码。
// 加载的地址写死在 BaiduApi.authorizeUrl()（百度官方域名），不加载任何用户输入的内容，
// 也没有 addJavascriptInterface 暴露原生对象，所以不存在 XSS 面。
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AuthScreen(
    hint: String?,
    onCancel: () -> Unit,
    onCreated: (WebView) -> Unit,
    onClipboard: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("授权百度网盘", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "登录并同意后自动返回，不需要复制粘贴",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onCancel) { Text("取消") }
        }

        // 百度页面是按应用名生成的，这里用的是公开应用凭据，标题会写「AList」。
        // 用户看到的第一反应一定是「我是不是点错了」，所以先说清楚。
        Text(
            "页面标题写着「AList」是正常的 —— 那是公开应用凭据的名字，不影响授权给本 App。",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )

        if (hint != null) {
            Text(
                hint,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        onCreated(this)
                    }
                },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "没自动返回？点页面上的「复制授权码」，再用这里",
                modifier = Modifier.weight(1f),
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
            TextButton(onClick = onClipboard) { Text("从剪贴板读取", fontSize = 12.sp) }
        }
        Spacer(Modifier.height(4.dp))
    }
}
