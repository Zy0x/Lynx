package com.noir.lynx.feature.floating.ui.tools

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FloatingBrowserTool(
    initialUrl: String = "https://www.google.com",
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var inputUrlText by remember { mutableStateOf(initialUrl) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableStateOf(0) }
    val focusManager = LocalFocusManager.current

    val bookmarks = remember {
        listOf(
            "🗺️ Genshin Map" to "https://act.hoyolab.com/ys/app/interactive-map/index.html",
            "⚔️ MLBB Wiki" to "https://mobile-legends.fandom.com/wiki/Mobile_Legends_Wiki",
            "🎮 Game8" to "https://game8.co",
            "📺 YouTube" to "https://m.youtube.com",
            "🔍 Google" to "https://www.google.com"
        )
    }

    fun navigateTo(url: String) {
        val target = if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else if (url.contains(".") && !url.contains(" ")) {
            "https://$url"
        } else {
            "https://www.google.com/search?q=" + java.net.URLEncoder.encode(url, "UTF-8")
        }
        inputUrlText = target
        currentUrl = target
        webViewInstance?.loadUrl(target)
        focusManager.clearFocus()
    }

    Column(modifier = modifier.fillMaxSize().background(Color(0xFF0F141C))) {
        // Navigation bar: Back, Forward, URL input, Refresh / Stop
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141D2B))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Back
            IconButton(
                onClick = { webViewInstance?.goBack() },
                enabled = canGoBack,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = if (canGoBack) Color(0xFF00E5FF) else Color(0xFF556070),
                    modifier = Modifier.size(16.dp)
                )
            }

            // Forward
            IconButton(
                onClick = { webViewInstance?.goForward() },
                enabled = canGoForward,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Forward",
                    tint = if (canGoForward) Color(0xFF00E5FF) else Color(0xFF556070),
                    modifier = Modifier.size(16.dp)
                )
            }

            // URL input box
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF0A0F17),
                border = BorderStroke(0.6.dp, Color(0xFF223046)),
                modifier = Modifier.weight(1f).height(28.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    BasicTextField(
                        value = inputUrlText,
                        onValueChange = { inputUrlText = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.SansSerif
                        ),
                        cursorBrush = SolidColor(Color(0xFF00E5FF)),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { navigateTo(inputUrlText) }),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Reload / Go
            IconButton(
                onClick = {
                    if (isLoading) webViewInstance?.stopLoading()
                    else navigateTo(inputUrlText)
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                    contentDescription = "Reload",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // Progress bar
        if (isLoading && loadProgress in 1..99) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Color(0xFF00E5FF),
                trackColor = Color(0x3300E5FF)
            )
        }

        // Quick Gaming Bookmarks Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A0E15))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            bookmarks.forEach { (label, url) ->
                val isCurrent = currentUrl.startsWith(url.take(20))
                Surface(
                    onClick = { navigateTo(url) },
                    shape = RoundedCornerShape(6.dp),
                    color = if (isCurrent) Color(0xFF00E5FF).copy(alpha = 0.18f) else Color(0xFF141E2B),
                    border = BorderStroke(0.6.dp, if (isCurrent) Color(0xFF00E5FF) else Color(0xFF223046)),
                    modifier = Modifier.height(22.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isCurrent) Color(0xFF00E5FF) else Color(0xFFCCD6E0)
                        )
                    }
                }
            }
        }

        // Interactive WebView
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading = true
                            url?.let {
                                currentUrl = it
                                inputUrlText = it
                            }
                            canGoBack = view?.canGoBack() ?: false
                            canGoForward = view?.canGoForward() ?: false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            canGoBack = view?.canGoBack() ?: false
                            canGoForward = view?.canGoForward() ?: false
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            return false
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            loadProgress = newProgress
                            isLoading = newProgress < 100
                        }
                    }

                    webViewInstance = this
                    loadUrl(initialUrl)
                }
            },
            update = { view ->
                webViewInstance = view
            }
        )
    }
}
