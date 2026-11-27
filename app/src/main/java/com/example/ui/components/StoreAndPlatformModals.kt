package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.util.Base64
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    onExportIosZip: (saveToDevice: Boolean) -> Unit,
    onExportElectronZip: (saveToDevice: Boolean) -> Unit,
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
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(strings.iosDesktopTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(strings.iosDesktopSubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            GradientButton(
                text = "Export Capacitor iOS (.zip)",
                icon = Icons.Rounded.PhoneIphone,
                onClick = { onExportIosZip(true) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedGlowButton(
                text = "Share Capacitor iOS (.zip)",
                icon = Icons.Rounded.Share,
                onClick = { onExportIosZip(false) },
                modifier = Modifier.fillMaxWidth(),
            )
            GradientButton(
                text = "Export Electron Desktop (.zip)",
                icon = Icons.Rounded.DesktopWindows,
                brush = MintCtaBrush,
                contentColor = Color(0xFF0C0A20),
                onClick = { onExportElectronZip(true) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedGlowButton(
                text = "Share Electron Desktop (.zip)",
                icon = Icons.Rounded.Share,
                onClick = { onExportElectronZip(false) },
                modifier = Modifier.fillMaxWidth(),
            )
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
    var showSplash by remember { mutableStateOf(true) }
    val bgColor = remember(config.themeColorHex) {
        runCatching { Color(android.graphics.Color.parseColor(config.themeColorHex)) }
            .getOrDefault(Color(0xFF0C0A20))
    }

    LaunchedEffect(Unit) {
        delay(1300)
        showSplash = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
            val latestAssets = remember(assets) { assets.associateBy { it.path.lowercase().trimStart('/') } }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                val url = request?.url ?: return null
                                if (url.toString().startsWith("https://webforge.local/")) {
                                    val rel = url.path?.trimStart('/')?.lowercase() ?: return null
                                    val asset = latestAssets[rel] ?: return null
                                    val bytes = ZipArchiveManager.assetToBytes(asset) ?: return null
                                    return WebResourceResponse(asset.mimeType, "UTF-8", ByteArrayInputStream(bytes))
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }
                        loadDataWithBaseURL("https://webforge.local/", html, "text/html", "UTF-8", null)
                    }
                },
            )

            if (showSplash) {
                Box(modifier = Modifier.fillMaxSize().background(bgColor), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AppIconPreview(config.customIconBase64, Modifier.size(92.dp).clip(RoundedCornerShape(22.dp)))
                        Spacer(Modifier.height(14.dp))
                        Text(config.appName, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(strings.splashPreviewLabel, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
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
