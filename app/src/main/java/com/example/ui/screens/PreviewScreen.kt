package com.example.ui.screens

import android.annotation.SuppressLint
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Publish
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.TabletMac
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.DeviceFrame
import com.example.data.model.ProjectAsset
import com.example.domain.ZipArchiveManager
import com.example.ui.WebForgeUiState
import com.example.ui.components.GlowingFrameCard
import com.example.ui.components.GradientButton
import com.example.ui.components.PrimaryCtaBrush
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.LocalWebForgeColors
import com.example.ui.theme.MonoFontFamily
import java.io.ByteArrayInputStream

private const val LOCAL_BASE_URL = "https://webforge.local/"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PreviewScreen(
    state: WebForgeUiState,
    onSelectDevice: (DeviceFrame) -> Unit,
    onToggleOrientation: () -> Unit,
    onReload: () -> Unit,
    onConsoleLog: (String, String) -> Unit,
    onShowQr: () -> Unit,
    onPublishClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val ext = LocalWebForgeColors.current
    var showConsole by remember { mutableStateOf(false) }
    var showFullscreen by remember { mutableStateOf(false) }
    var jsCommand by remember { mutableStateOf("") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 900.dp)
                .fillMaxSize(),
        ) {
            // Header + Console, Rotate, Fullscreen, QR & Reload
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = strings.livePreview,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = { showConsole = !showConsole },
                        shape = RoundedCornerShape(12.dp),
                        color = if (showConsole) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, ext.cardBorder),
                        modifier = Modifier.testTag("toggle_console_button"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Rounded.Terminal, strings.consoleTitle, tint = ext.accent, modifier = Modifier.size(16.dp))
                            Text(state.consoleLogs.size.toString(), style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    IconButton(
                        onClick = onToggleOrientation,
                        modifier = Modifier.testTag("rotate_preview_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ScreenRotation,
                            contentDescription = strings.rotateScreen,
                            tint = if (state.isLandscapePreview) ext.success else ext.accent,
                        )
                    }

                    IconButton(
                        onClick = { showFullscreen = true },
                        modifier = Modifier.testTag("fullscreen_preview_button"),
                    ) {
                        Icon(Icons.Rounded.Fullscreen, strings.fullscreen, tint = ext.accent)
                    }

                    IconButton(onClick = onShowQr) {
                        Icon(Icons.Rounded.QrCode2, strings.qrCardTitle, tint = ext.softViolet)
                    }

                    IconButton(
                        onClick = onReload,
                        modifier = Modifier.testTag("reload_preview_button"),
                    ) {
                        Icon(Icons.Rounded.Refresh, strings.reloadPreview, tint = ext.accent)
                    }
                }
            }

            // URL Bar Pill
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, ext.cardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(ext.success))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.previewDisplayUrl,
                            style = TextStyle(fontFamily = MonoFontFamily, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Responsive Device Frame Preview with Landscape Support
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val targetWidth by animateDpAsState(
                    targetValue = when (state.selectedDevice) {
                        DeviceFrame.PHONE -> if (state.isLandscapePreview) minOf(maxWidth, 540.dp) else minOf(maxWidth, 340.dp)
                        DeviceFrame.TABLET -> if (state.isLandscapePreview) maxWidth else minOf(maxWidth, 560.dp)
                        DeviceFrame.PC -> maxWidth
                    },
                    animationSpec = spring(stiffness = 380f),
                    label = "deviceFrameWidth",
                )
                val cornerRadius by animateDpAsState(
                    targetValue = when (state.selectedDevice) {
                        DeviceFrame.PHONE -> 28.dp
                        DeviceFrame.TABLET -> 20.dp
                        DeviceFrame.PC -> 14.dp
                    },
                    label = "deviceFrameCorner",
                )

                GlowingFrameCard(
                    modifier = Modifier
                        .width(targetWidth)
                        .fillMaxSize(),
                    shape = RoundedCornerShape(cornerRadius),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        when (state.selectedDevice) {
                            DeviceFrame.PHONE -> {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(modifier = Modifier.width(54.dp).height(5.dp).clip(CircleShape).background(ext.cardBorder))
                                }
                            }
                            DeviceFrame.TABLET -> {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(ext.cardBorder))
                                }
                            }
                            DeviceFrame.PC -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF5F56)))
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFFBD2E)))
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF27C93F)))
                                }
                            }
                        }

                        val assetsSnapshot = remember(state.assets) { state.assets }
                        val loadKey = remember(state.htmlContent, state.assets, state.previewRevision, state.selectedDevice) {
                            "${state.previewRevision}-${state.selectedDevice.name}-${state.htmlContent.hashCode()}"
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(16.dp)),
                        ) {
                            LiveHtmlWebView(
                                html = state.htmlContent,
                                assets = assetsSnapshot,
                                deviceFrame = state.selectedDevice,
                                loadKey = loadKey,
                                isOffline = state.isEffectiveOffline,
                                onConsoleLog = onConsoleLog,
                                onWebViewReady = { webViewRef = it },
                            )
                        }
                    }
                }
            }

            // Collapsible Interactive JS Console (Eruda-style evaluator + logs)
            AnimatedVisibility(visible = showConsole) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, ext.cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = strings.consoleTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.accent,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(82.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            if (state.consoleLogs.isEmpty()) {
                                Text(
                                    text = strings.noConsoleLogs,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                state.consoleLogs.forEach { log ->
                                    Text(
                                        text = "[${log.level}] ${log.message}",
                                        style = TextStyle(
                                            fontFamily = MonoFontFamily,
                                            fontSize = 11.sp,
                                            color = if (log.level == "ERROR") MaterialTheme.colorScheme.error else ext.success,
                                        ),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = jsCommand,
                                    onValueChange = { jsCommand = it },
                                    label = { Text("Eval JS (e.g. document.title)") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = {
                                        val cmd = jsCommand.trim()
                                        if (cmd.isNotEmpty()) {
                                            onConsoleLog("CMD", "> $cmd")
                                            webViewRef?.evaluateJavascript(cmd) { result ->
                                                onConsoleLog("RESULT", result ?: "undefined")
                                            }
                                            jsCommand = ""
                                        }
                                    },
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Send, "Run JS", tint = ext.accent)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            ) {
                DeviceTogglePill(
                    label = strings.phone,
                    icon = Icons.Rounded.PhoneAndroid,
                    selected = state.selectedDevice == DeviceFrame.PHONE,
                    onClick = { onSelectDevice(DeviceFrame.PHONE) },
                    testTag = "device_phone_pill",
                )
                DeviceTogglePill(
                    label = strings.tablet,
                    icon = Icons.Rounded.TabletMac,
                    selected = state.selectedDevice == DeviceFrame.TABLET,
                    onClick = { onSelectDevice(DeviceFrame.TABLET) },
                    testTag = "device_tablet_pill",
                )
                DeviceTogglePill(
                    label = strings.pc,
                    icon = Icons.Rounded.DesktopWindows,
                    selected = state.selectedDevice == DeviceFrame.PC,
                    onClick = { onSelectDevice(DeviceFrame.PC) },
                    testTag = "device_pc_pill",
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            GradientButton(
                text = strings.publishWebsite,
                icon = Icons.Rounded.Publish,
                brush = PrimaryCtaBrush,
                testTag = "publish_website_button",
                onClick = onPublishClick,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(14.dp))
        }
    }

    if (showFullscreen) {
        Dialog(
            onDismissRequest = { showFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                LiveHtmlWebView(
                    html = state.htmlContent,
                    assets = state.assets,
                    deviceFrame = DeviceFrame.PHONE,
                    loadKey = "fs-${state.previewRevision}-${state.htmlContent.hashCode()}",
                    isOffline = state.isEffectiveOffline,
                    onConsoleLog = onConsoleLog,
                )
                Surface(
                    onClick = { showFullscreen = false },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                ) {
                    Icon(Icons.Rounded.Close, strings.close, tint = Color.White, modifier = Modifier.padding(10.dp))
                }
            }
        }
    }
}

@Composable
private fun DeviceTogglePill(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val ext = LocalWebForgeColors.current
    val bg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        label = "pillBg",
    )
    val contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(bg)
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else ext.cardBorder,
                shape = RoundedCornerShape(50.dp),
            )
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(icon, label, tint = contentColor, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = contentColor, fontWeight = FontWeight.Bold)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LiveHtmlWebView(
    html: String,
    assets: List<ProjectAsset>,
    deviceFrame: DeviceFrame,
    loadKey: String,
    isOffline: Boolean,
    onConsoleLog: (String, String) -> Unit,
    onWebViewReady: (WebView) -> Unit = {},
) {
    val latestAssets = remember(assets) { assets.associateBy { it.path.lowercase().trimStart('/') } }
    val lastLoadedKey = remember { mutableStateOf("") }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .testTag("live_preview_webview"),
        factory = { context ->
            WebView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(0xFF0C0A20.toInt())
                settings.javaScriptEnabled = true
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        if (consoleMessage != null) {
                            onConsoleLog(
                                consoleMessage.messageLevel().name,
                                consoleMessage.message(),
                            )
                        }
                        return true
                    }
                }
                onWebViewReady(this)
            }
        },
        update = { webView ->
            onWebViewReady(webView)
            webView.settings.cacheMode = if (isOffline) {
                WebSettings.LOAD_CACHE_ELSE_NETWORK
            } else {
                WebSettings.LOAD_DEFAULT
            }

            val htmlBytes = html.toByteArray(Charsets.UTF_8)
            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): WebResourceResponse? {
                    val url = request?.url ?: return null
                    if (url.toString().startsWith(LOCAL_BASE_URL)) {
                        val relPath = url.path?.trimStart('/')?.lowercase().orEmpty()
                        val cors = mapOf("Access-Control-Allow-Origin" to "*")
                        if (relPath.isEmpty() || relPath == "index.html") {
                            return WebResourceResponse("text/html", "UTF-8", 200, "OK", cors, ByteArrayInputStream(htmlBytes))
                        }
                        val asset = latestAssets[relPath]
                            ?: latestAssets.entries.firstOrNull { it.key.endsWith("/$relPath") }?.value
                        if (asset != null) {
                            val bytes = ZipArchiveManager.assetToBytes(asset) ?: return null
                            return WebResourceResponse(
                                asset.mimeType,
                                "UTF-8",
                                200,
                                "OK",
                                cors,
                                ByteArrayInputStream(bytes),
                            )
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }
            }

            val targetScale = when (deviceFrame) {
                DeviceFrame.PHONE -> 0
                DeviceFrame.TABLET -> 65
                DeviceFrame.PC -> 42
            }
            webView.setInitialScale(targetScale)

            if (lastLoadedKey.value != loadKey) {
                lastLoadedKey.value = loadKey
                webView.loadUrl("${LOCAL_BASE_URL}index.html")
            }
        },
    )
}
