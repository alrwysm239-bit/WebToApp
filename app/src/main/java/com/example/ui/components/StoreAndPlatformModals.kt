package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.AppExportConfig
import com.example.data.model.AppOrientation
import com.example.data.model.AppSourceMode
import com.example.data.model.ProjectAsset
import com.example.domain.QrCodeGenerator
import com.example.domain.ZipArchiveManager
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.LocalWebForgeColors
import com.example.ui.theme.MonoFontFamily
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PwaSheet(
    projectName: String,
    config: AppExportConfig,
    onInjectPwa: () -> Unit,
    onExportPwaZip: (saveToDevice: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val ext = LocalWebForgeColors.current
    val manifestPreview = remember(config, projectName) {
        ZipArchiveManager.buildPwaManifest(config, projectName)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(strings.pwaConfigTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(strings.pwaSubtitleDetail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.background,
                    border = BorderStroke(1.dp, ext.cardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = manifestPreview,
                        style = TextStyle(fontFamily = MonoFontFamily, fontSize = 11.5.sp, color = ext.accent),
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            OutlinedGlowButton(
                text = strings.injectPwaIntoHtml,
                icon = Icons.Rounded.InstallMobile,
                onClick = onInjectPwa,
                modifier = Modifier.fillMaxWidth(),
                testTag = "inject_pwa_button",
            )
            Spacer(Modifier.height(10.dp))
            GradientButton(
                text = strings.exportPwaZip,
                icon = Icons.Rounded.Download,
                onClick = { onExportPwaZip(true) },
                modifier = Modifier.fillMaxWidth(),
                testTag = "export_pwa_zip_button",
            )
            Spacer(Modifier.height(8.dp))
            OutlinedGlowButton(
                text = strings.shareZip,
                icon = Icons.Rounded.Share,
                onClick = { onExportPwaZip(false) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosDesktopSheet(
    config: AppExportConfig,
    publishedUrl: String?,
    isRtl: Boolean,
    onConfigChange: ((AppExportConfig) -> AppExportConfig) -> Unit,
    onExportIosIpa: (saveToDevice: Boolean) -> Unit,
    onExportIosWebClip: (saveToDevice: Boolean) -> Unit,
    onExportIosZip: (saveToDevice: Boolean) -> Unit,
    onExportElectronZip: (saveToDevice: Boolean) -> Unit,
    onOpenStoreGuide: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val ext = LocalWebForgeColors.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(strings.iosDesktopTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(strings.iosDesktopSubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Source Mode: HTML vs Website URL
            Text(strings.sourceModeLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = config.sourceMode == AppSourceMode.HTML,
                    onClick = { onConfigChange { it.copy(sourceMode = AppSourceMode.HTML) } },
                    label = { Text(strings.sourceHtmlMode) },
                )
                FilterChip(
                    selected = config.sourceMode == AppSourceMode.URL,
                    onClick = {
                        onConfigChange {
                            it.copy(sourceMode = AppSourceMode.URL, sourceUrl = it.sourceUrl.ifBlank { publishedUrl.orEmpty() })
                        }
                    },
                    label = { Text(strings.sourceUrlMode) },
                )
            }

            if (config.sourceMode == AppSourceMode.URL) {
                OutlinedTextField(
                    value = config.sourceUrl,
                    onValueChange = { v -> onConfigChange { it.copy(sourceUrl = v) } },
                    label = { Text(strings.sourceUrlInputLabel) },
                    leadingIcon = { Icon(Icons.Rounded.Link, null, tint = ext.accent) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIconPreview(
                    customIconBase64 = config.customIconBase64,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)),
                )
                OutlinedTextField(
                    value = config.appName,
                    onValueChange = { v -> onConfigChange { it.copy(appName = v) } },
                    label = { Text(strings.appDisplayNameLabel) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(config.orientation == AppOrientation.AUTO, { onConfigChange { it.copy(orientation = AppOrientation.AUTO) } }, { Text(strings.orientationAuto) })
                FilterChip(config.orientation == AppOrientation.PORTRAIT, { onConfigChange { it.copy(orientation = AppOrientation.PORTRAIT) } }, { Text(strings.orientationPortrait) })
                FilterChip(config.orientation == AppOrientation.LANDSCAPE, { onConfigChange { it.copy(orientation = AppOrientation.LANDSCAPE) } }, { Text(strings.orientationLandscape) })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(if (isRtl) "شاشة كاملة للآيفون (Fullscreen)" else "iOS Fullscreen Mode", style = MaterialTheme.typography.bodySmall)
                Switch(checked = config.fullscreen, onCheckedChange = { v -> onConfigChange { it.copy(fullscreen = v) } })
            }

            // Inherited Lock & Ads summary pill
            if (config.appLockEnabled || config.adsEnabled) {
                Surface(
                    color = ext.success.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, ext.success.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = buildString {
                            if (isRtl) append("مُفعّل داخل تطبيق الآيفون: ") else append("Active in iOS build: ")
                            if (config.appLockEnabled) append("🔒 Password Lock  ")
                            if (config.adsEnabled) append("💰 Ads Banner/Script")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = ext.success,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }

            Surface(
                color = ext.accent.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, ext.accent.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (isRtl) {
                        "تصدير حزمة IPA حقيقية (Payload/*.app) للتثبيت عبر AltStore / Sideloadly أو استخدام ملف .mobileconfig المباشر بدون Xcode، بالإضافة لمشروع Xcode + بناء سحابي IPA مجاني على macOS."
                    } else {
                        "Exports a real .ipa Payload bundle (for AltStore/Sideloadly), a zero-Xcode .mobileconfig iOS installer, and an Xcode Swift project with free macOS Cloud .ipa GitHub Actions."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.accent,
                    modifier = Modifier.padding(12.dp),
                )
            }

            GradientButton(
                text = strings.exportIosIpaBtn,
                icon = Icons.Rounded.PhoneIphone,
                brush = MintCtaBrush,
                contentColor = Color(0xFF0C0A20),
                onClick = { onExportIosIpa(true) },
                modifier = Modifier.fillMaxWidth(),
                testTag = "export_ios_ipa_button",
            )
            OutlinedGlowButton(
                text = if (isRtl) "مشاركة ملف الآيفون (.ipa)" else "Share iOS Package (.ipa)",
                icon = Icons.Rounded.Share,
                onClick = { onExportIosIpa(false) },
                modifier = Modifier.fillMaxWidth(),
            )

            GradientButton(
                text = strings.iosDirectProfileBtn,
                icon = Icons.Rounded.PhoneIphone,
                onClick = { onExportIosWebClip(true) },
                modifier = Modifier.fillMaxWidth(),
                testTag = "export_ios_webclip_button",
            )
            OutlinedGlowButton(
                text = if (isRtl) "مشاركة ملف التثبيت المباشر (.mobileconfig)" else "Share Direct iOS App (.mobileconfig)",
                icon = Icons.Rounded.Share,
                onClick = { onExportIosWebClip(false) },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedGlowButton(
                text = if (isRtl) "تصدير مشروع Xcode + بناء IPA سحابي (.zip)" else "Export Xcode + Cloud IPA Workflow (.zip)",
                icon = Icons.Rounded.PhoneIphone,
                onClick = { onExportIosZip(true) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedGlowButton(
                text = "Export Electron Desktop (.zip)",
                icon = Icons.Rounded.DesktopWindows,
                onClick = { onExportElectronZip(true) },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onOpenStoreGuide, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Storefront, null, Modifier.size(16.dp), tint = ext.accent)
                Spacer(Modifier.width(6.dp))
                Text(strings.freeStorePublishBtn, color = ext.accent)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun StorePackDialog(
    isGenerating: Boolean,
    isOffline: Boolean,
    isRtl: Boolean,
    onExportStorePack: (includeAiDescriptions: Boolean, saveToDevice: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val ext = LocalWebForgeColors.current
    var includeAi by remember { mutableStateOf(!isOffline) }

    Dialog(onDismissRequest = onDismiss) {
        GlowingFrameCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Storefront, null, tint = ext.accent)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.storePackTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, strings.close) }
                }

                Spacer(Modifier.height(8.dp))
                Text(strings.storePackSubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Checkbox(
                        checked = includeAi && !isOffline,
                        enabled = !isOffline && !isGenerating,
                        onCheckedChange = { includeAi = it },
                    )
                    Icon(Icons.Rounded.AutoAwesome, null, tint = ext.softViolet, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isRtl) "توليد أوصاف المتجر بالذكاء الاصطناعي (AR/EN/FR/ES)" else "Generate AI Store Listings (AR/EN/FR/ES)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (isGenerating) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = ext.accent)
                    }
                }

                Spacer(Modifier.height(12.dp))
                GradientButton(
                    text = strings.saveZipToDevice,
                    icon = Icons.Rounded.Download,
                    enabled = !isGenerating,
                    onClick = { onExportStorePack(includeAi && !isOffline, true) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedGlowButton(
                    text = strings.shareZip,
                    icon = Icons.Rounded.Share,
                    onClick = { if (!isGenerating) onExportStorePack(includeAi && !isOffline, false) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun QrCodeDialog(
    initialUrl: String,
    onCopyUrl: (String) -> Unit,
    onExportQr: (String, saveToDevice: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val ext = LocalWebForgeColors.current
    var urlInput by remember(initialUrl) { mutableStateOf(initialUrl) }
    val matrix = remember(urlInput) { QrCodeGenerator.encodeMatrix(urlInput) }

    Dialog(onDismissRequest = onDismiss) {
        GlowingFrameCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.qrCodeTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, strings.close) }
                }

                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .size(210.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .padding(14.dp),
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cellSize = size.width / matrix.size
                        for (y in 0 until matrix.size) {
                            for (x in 0 until matrix.size) {
                                if (matrix.bits[y * matrix.size + x]) {
                                    drawRect(
                                        color = Color(0xFF0C0A20),
                                        topLeft = Offset(x * cellSize, y * cellSize),
                                        size = Size(cellSize + 0.5f, cellSize + 0.5f),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text(strings.qrTargetUrlLabel) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedGlowButton(
                        text = strings.copyUrl,
                        icon = Icons.Rounded.ContentCopy,
                        onClick = { onCopyUrl(urlInput) },
                        modifier = Modifier.weight(1f),
                    )
                    GradientButton(
                        text = strings.shareQrPng,
                        icon = Icons.Rounded.Share,
                        onClick = { onExportQr(urlInput, false) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { onExportQr(urlInput, true) }) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(16.dp), tint = ext.accent)
                    Spacer(Modifier.width(4.dp))
                    Text(strings.saveQrPng, color = ext.accent)
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AppShellPreviewDialog(
    html: String,
    assets: List<ProjectAsset>,
    config: AppExportConfig,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    var showSplash by remember { mutableStateOf(config.splashEnabled) }
    var isLocked by remember(config.appLockEnabled, config.appLockPassword) {
        mutableStateOf(config.appLockEnabled && config.appLockPassword.isNotBlank())
    }
    var lockInput by remember { mutableStateOf("") }
    var lockWrong by remember { mutableStateOf(false) }
    var simulatedLandscape by remember { mutableStateOf(config.orientation == AppOrientation.LANDSCAPE) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val cleanHtml = remember(html) {
        html.removePrefix("\uFEFF").ifBlank {
            "<!DOCTYPE html><html><body style='margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#0C0A20;color:#F4F6FF;font-family:sans-serif'><h3>Empty HTML</h3></body></html>"
        }
    }
    val latestAssetsMap by rememberUpdatedState(
        assets.associateBy { it.path.lowercase().trimStart('/') },
    )
    val lastLoadedSig = remember { mutableStateOf("") }

    val splashBgInt = remember(config.splashBgColorHex) {
        runCatching { android.graphics.Color.parseColor(config.splashBgColorHex) }
            .getOrDefault(0xFF0C0A20.toInt())
    }
    val splashBg = Color(splashBgInt)
    val accentBg = remember(config.themeColorHex) {
        runCatching { Color(android.graphics.Color.parseColor(config.themeColorHex)) }
            .getOrDefault(Color(0xFF5B3DF5))
    }

    LaunchedEffect(config.splashEnabled, config.splashDurationMs) {
        if (config.splashEnabled) {
            delay(config.splashDurationMs.toLong().coerceIn(500L, 4500L))
            showSplash = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(splashBg)) {
            Column(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            setBackgroundColor(splashBgInt)
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
                            settings.builtInZoomControls = config.enableZoom
                            settings.displayZoomControls = false
                            if (config.desktopMode) {
                                settings.userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                            } else if (config.customUserAgent.isNotBlank()) {
                                settings.userAgentString = config.customUserAgent
                            }
                            runCatching {
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            }
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                    val url = request?.url ?: return null
                                    if (url.host.equals("webforge.local", ignoreCase = true)) {
                                        val rel = url.path?.trimStart('/')?.lowercase().orEmpty()
                                        val cors = mapOf("Access-Control-Allow-Origin" to "*")
                                        val assetsMap = latestAssetsMap
                                        val asset = assetsMap[rel]
                                            ?: assetsMap.entries.firstOrNull { it.key.endsWith("/$rel") }?.value
                                        if (asset != null) {
                                            val bytes = ZipArchiveManager.assetToBytes(asset) ?: return null
                                            return WebResourceResponse(asset.mimeType, "UTF-8", 200, "OK", cors, ByteArrayInputStream(bytes))
                                        }
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }
                            }
                            webViewRef = this
                        }
                    },
                    update = { webView ->
                        val sig = "${config.sourceMode.key}:${config.sourceUrl}:${cleanHtml.hashCode()}:${assets.size}"
                        if (lastLoadedSig.value != sig) {
                            lastLoadedSig.value = sig
                            if (config.sourceMode == AppSourceMode.URL && config.sourceUrl.isNotBlank()) {
                                val u = config.sourceUrl.trim()
                                webView.loadUrl(if (u.startsWith("http://") || u.startsWith("https://")) u else "https://$u")
                            } else {
                                webView.loadDataWithBaseURL(
                                    "https://webforge.local/",
                                    cleanHtml,
                                    "text/html",
                                    "UTF-8",
                                    "https://webforge.local/index.html",
                                )
                            }
                        }
                    },
                )

                // Live Ad Banner Preview Slot
                if (config.adsEnabled && config.adPlacementBottomBanner) {
                    Surface(
                        color = Color(0xFF14122B),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val activeNet = buildList {
                            if (config.admobBannerId.isNotBlank()) add("AdMob")
                            if (config.metaAudienceBannerId.isNotBlank()) add("Meta Ads")
                            if (config.unityGameId.isNotBlank()) add("Unity")
                            if (config.appLovinSdkKey.isNotBlank()) add("AppLovin")
                            if (config.customHtmlAdScript.isNotBlank()) add("HTML Ad")
                        }.ifEmpty { listOf("Ad Banner") }.joinToString(" • ")
                        Text(
                            text = "AD BANNER PREVIEW ($activeNet)",
                            color = Color(0xFF00C6FF),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 14.dp),
                        )
                    }
                }

                // Live Native Bottom Nav Preview Bar
                if (config.bottomNavBarEnabled) {
                    Surface(color = Color(0xFF1A1638), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            IconButton(onClick = { if (webViewRef?.canGoBack() == true) webViewRef?.goBack() }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Color.White)
                            }
                            IconButton(onClick = { webViewRef?.reload() }) {
                                Icon(Icons.Rounded.Home, null, tint = Color.White)
                            }
                            IconButton(onClick = { webViewRef?.reload() }) {
                                Icon(Icons.Rounded.Refresh, null, tint = Color.White)
                            }
                            IconButton(onClick = { if (webViewRef?.canGoForward() == true) webViewRef?.goForward() }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Color.White)
                            }
                        }
                    }
                }
            }

            if (showSplash) {
                Box(modifier = Modifier.fillMaxSize().background(splashBg), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AppIconPreview(config.customIconBase64, Modifier.size(92.dp).clip(RoundedCornerShape(22.dp)))
                        Spacer(Modifier.height(14.dp))
                        Text(config.splashTitle.ifBlank { config.appName }, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(config.splashSubtitle.ifBlank { strings.splashPreviewLabel }, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.72f))
                        Spacer(Modifier.height(14.dp))
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = accentBg, strokeWidth = 2.5.dp)
                    }
                }
            } else if (isLocked) {
                Box(
                    modifier = Modifier.fillMaxSize().background(splashBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(28.dp),
                    ) {
                        Icon(Icons.Rounded.Lock, null, tint = accentBg, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = config.appLockTitle.ifBlank { config.appName },
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = lockInput,
                            onValueChange = { lockInput = it; lockWrong = false },
                            label = { Text(strings.appLockPasswordLabel) },
                            singleLine = true,
                            isError = lockWrong,
                            shape = RoundedCornerShape(14.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            if (lockInput == config.appLockPassword) isLocked = false else lockWrong = true
                        }) {
                            Text("Unlock App")
                        }
                    }
                }
            }

            if (config.showFloatingBar) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                ) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (webViewRef?.canGoBack() == true) webViewRef?.goBack() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { simulatedLandscape = !simulatedLandscape }) {
                            Icon(Icons.Rounded.ScreenRotation, strings.rotateScreen, tint = if (simulatedLandscape) accentBg else Color.White, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { webViewRef?.reload() }) {
                            Icon(Icons.Rounded.Refresh, strings.reloadPreview, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Surface(
                onClick = onDismiss,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            ) {
                Icon(Icons.Rounded.Close, strings.close, tint = Color.White, modifier = Modifier.padding(10.dp))
            }
        }
    }
}

private data class BuilderPortalEntry(
    val category: String,
    val label: String,
    val url: String,
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FreeWebBuilderPortalDialog(
    publishedUrl: String?,
    isRtl: Boolean,
    isOffline: Boolean,
    onPublishFreeFirst: () -> Unit,
    onCopyUrl: (String) -> Unit,
    onOpenExternal: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val ext = LocalWebForgeColors.current
    val categories = listOf("ALL", "ANDROID", "IOS", "HOST")
    var selectedCategory by remember { mutableStateOf("ALL") }

    val allPortals = remember {
        listOf(
            BuilderPortalEntry("ANDROID", "PWABuilder (Store / APK / iOS)", "https://www.pwabuilder.com/"),
            BuilderPortalEntry("ANDROID", "WebIntoApp (HTML/ZIP -> APK & iOS)", "https://www.webintoapp.com/app-maker"),
            BuilderPortalEntry("ANDROID", "AppsGeyser (Free Android APK)", "https://appsgeyser.com/"),
            BuilderPortalEntry("IOS", "Median.co (Android & iOS IPA)", "https://median.co/"),
            BuilderPortalEntry("IOS", "AppMySite (iOS & Android Builder)", "https://www.appmysite.com/"),
            BuilderPortalEntry("HOST", "Tiiny.host (Upload HTML/ZIP Free)", "https://tiiny.host/"),
            BuilderPortalEntry("HOST", "Static.app (Upload HTML/ZIP Free)", "https://static.app/"),
            BuilderPortalEntry("HOST", "Netlify Drop (Upload HTML/ZIP)", "https://app.netlify.com/drop"),
        )
    }
    val visiblePortals = remember(selectedCategory) {
        if (selectedCategory == "ALL") allPortals else allPortals.filter { it.category == selectedCategory }
    }

    var selectedPortalUrl by remember { mutableStateOf(allPortals.first().url) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        pendingFileCallback?.onReceiveValue(if (uris.isEmpty()) null else uris.toTypedArray())
        pendingFileCallback = null
    }

    LaunchedEffect(selectedPortalUrl) {
        webViewRef?.loadUrl(selectedPortalUrl)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Public, null, tint = ext.accent, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = strings.freeWebBuilderTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (webViewRef?.canGoBack() == true) webViewRef?.goBack() }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = ext.accent)
                            }
                            IconButton(onClick = { webViewRef?.reload() }) {
                                Icon(Icons.Rounded.Refresh, strings.reloadPreview, tint = ext.accent)
                            }
                            IconButton(onClick = { onOpenExternal(webViewRef?.url ?: selectedPortalUrl) }) {
                                Icon(Icons.Rounded.OpenInBrowser, strings.openUrl, tint = ext.accent)
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Rounded.Close, strings.close)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        categories.forEach { cat ->
                            val label = when (cat) {
                                "ANDROID" -> "Android APK/AAB"
                                "IOS" -> "iOS / IPA"
                                "HOST" -> if (isRtl) "استضافة HTML مجانية" else "Free HTML Host"
                                else -> if (isRtl) "الكل" else "All"
                            }
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = {
                                    selectedCategory = cat
                                    val first = if (cat == "ALL") allPortals.first() else allPortals.first { it.category == cat }
                                    selectedPortalUrl = first.url
                                },
                                label = { Text(label) },
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        visiblePortals.forEach { portal ->
                            FilterChip(
                                selected = selectedPortalUrl == portal.url,
                                onClick = { selectedPortalUrl = portal.url },
                                label = { Text(portal.label) },
                            )
                        }
                    }

                    if (!publishedUrl.isNullOrBlank()) {
                        Surface(
                            color = ext.success.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, ext.success.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = publishedUrl,
                                    style = TextStyle(fontFamily = MonoFontFamily, fontSize = 11.5.sp, color = ext.success),
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { onCopyUrl(publishedUrl) }) {
                                    Text(strings.copyUrl, color = ext.success)
                                }
                            }
                        }
                    } else if (!isOffline) {
                        TextButton(onClick = onPublishFreeFirst) {
                            Text(
                                text = if (isRtl) "انشر كود HTML الآن مجاناً لنسخ الرابط واستخدامه في البوابة ↗" else "Publish HTML free first to get a URL for the portal ↗",
                                color = ext.accent,
                            )
                        }
                    }
                }
            }

            if (isOffline) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(strings.offlineDeployBlocked, color = ext.warning)
                }
            } else {
                if (loadProgress in 1..99) {
                    LinearProgressIndicator(
                        progress = { loadProgress / 100f },
                        color = ext.accent,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            webViewRef = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.allowFileAccess = true
                            settings.allowContentAccess = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            settings.userAgentString =
                                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                            runCatching {
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            }
                            setDownloadListener(DownloadListener { url, _, _, _, _ ->
                                runCatching {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            })
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadProgress = newProgress
                                }

                                override fun onShowFileChooser(
                                    webView: WebView?,
                                    filePathCallback: ValueCallback<Array<Uri>>?,
                                    fileChooserParams: FileChooserParams?,
                                ): Boolean {
                                    pendingFileCallback?.onReceiveValue(null)
                                    pendingFileCallback = filePathCallback
                                    fileChooserLauncher.launch(arrayOf("*/*"))
                                    return true
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean {
                                    val u = request?.url?.toString().orEmpty()
                                    if (!u.startsWith("http://") && !u.startsWith("https://")) {
                                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
                                        return true
                                    }
                                    return false
                                }
                            }
                            loadUrl(selectedPortalUrl)
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun FreeStorePublishGuideDialog(
    isRtl: Boolean,
    onExportAabProject: () -> Unit,
    onExportStorePack: () -> Unit,
    onExportIosIpa: () -> Unit,
    onExportIosXcodeZip: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val ext = LocalWebForgeColors.current

    Dialog(onDismissRequest = onDismiss) {
        GlowingFrameCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Storefront, null, tint = ext.accent)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.freeStorePublishBtn, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, strings.close) }
                }

                Surface(
                    color = MaterialTheme.colorScheme.background,
                    border = BorderStroke(1.dp, ext.success.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("1. Google Play Store (.aab / APK)", fontWeight = FontWeight.Bold, color = ext.success)
                        Text(
                            text = if (isRtl) {
                                "• صدّر حزمة .aab + .apk عبر الزر بالأسفل وارفعها على GitHub Actions لبناء .aab جاهز للنشر مجاناً.\n• استخدم حزمة المتجر لتجهيز الأيقونة 512px وصورة Feature Graphic 1024x500 وسياسة الخصوصية وأوصاف AI."
                            } else {
                                "• Export the Gradle .aab + .apk Cloud Build ZIP and let GitHub Actions compile a release-ready .aab free.\n• Generate Store Pack for 512px icon, 1024x500 feature graphic, privacy policy & AI descriptions."
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        GradientButton(
                            text = strings.exportAabProjectBtn,
                            icon = Icons.Rounded.Android,
                            onClick = onExportAabProject,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedGlowButton(
                            text = strings.storePackTitle,
                            icon = Icons.Rounded.Download,
                            onClick = onExportStorePack,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.background,
                    border = BorderStroke(1.dp, ext.accent.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("2. Apple App Store & Free iOS (.ipa / WebClip)", fontWeight = FontWeight.Bold, color = ext.accent)
                        Text(
                            text = if (isRtl) {
                                "• للتثبيت المجاني بدون حساب مطور مدفوع ($99/سنة): استخدم ملف .ipa المُصدّر عبر AltStore / Sideloadly أو ملف .mobileconfig مباشرة من Safari.\n• للنشر الرسمي على App Store: صدّر مشروع Xcode + GitHub macOS .ipa Workflow ووقعّه بشهادة Apple / TestFlight."
                            } else {
                                "• Zero-cost iOS install (no $99/yr Apple fee): sideload the exported .ipa via AltStore / Sideloadly or install .mobileconfig directly.\n• Official App Store / TestFlight: export Xcode + GitHub macOS IPA Workflow to build & submit from the cloud."
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        GradientButton(
                            text = strings.exportIosIpaBtn,
                            icon = Icons.Rounded.PhoneIphone,
                            onClick = onExportIosIpa,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedGlowButton(
                            text = if (isRtl) "تصدير Xcode + macOS IPA Workflow (.zip)" else "Export Xcode + Cloud IPA Workflow (.zip)",
                            icon = Icons.Rounded.Share,
                            onClick = onExportIosXcodeZip,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppIconPreview(
    customIconBase64: String?,
    modifier: Modifier = Modifier,
) {
    val customBitmap = remember(customIconBase64) {
        if (customIconBase64.isNullOrBlank()) null
        else runCatching {
            val bytes = Base64.decode(customIconBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }

    if (customBitmap != null) {
        Image(bitmap = customBitmap, contentDescription = null, modifier = modifier)
    } else {
        Image(painter = painterResource(id = R.drawable.webforge_icon_default), contentDescription = null, modifier = modifier)
    }
}
