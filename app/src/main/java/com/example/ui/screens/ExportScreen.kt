package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppExportConfig
import com.example.data.model.HostingProvider
import com.example.ui.WebForgeUiState
import com.example.ui.components.AndroidConfigSheet
import com.example.ui.components.AppShellPreviewDialog
import com.example.ui.components.ExportOptionCard
import com.example.ui.components.GradientButton
import com.example.ui.components.HostingDeploySheet
import com.example.ui.components.IosDesktopSheet
import com.example.ui.components.MintCtaBrush
import com.example.ui.components.PwaSheet
import com.example.ui.components.QrCodeDialog
import com.example.ui.components.StorePackDialog
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.LocalWebForgeColors
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.WebForgeAccent
import com.example.ui.theme.WebForgePrimary
import com.example.ui.theme.WebForgeSoftViolet
import com.example.ui.theme.WebForgeSuccess
import com.example.ui.theme.WebForgeWarning

@Composable
fun ExportScreen(
    state: WebForgeUiState,
    onExportWebsiteZip: (includePwa: Boolean, saveToDevice: Boolean) -> Unit,
    onExportSingleHtml: (saveToDevice: Boolean) -> Unit,
    onExportAndroidZip: (saveToDevice: Boolean) -> Unit,
    onExportKeystoreGuide: (saveToDevice: Boolean) -> Unit,
    onExportIosZip: (saveToDevice: Boolean) -> Unit,
    onExportElectronZip: (saveToDevice: Boolean) -> Unit,
    onExportStorePack: (includeAiDescriptions: Boolean, saveToDevice: Boolean) -> Unit,
    onExportQrPng: (url: String, saveToDevice: Boolean) -> Unit,
    onInjectPwa: () -> Unit,
    onDeploy: (HostingProvider, token: String, primaryParam: String, secondaryParam: String, saveToken: Boolean) -> Unit,
    onResetDeployState: () -> Unit,
    onUpdateAppConfig: ((AppExportConfig) -> AppExportConfig) -> Unit,
    onPickCustomIcon: () -> Unit,
    onResetDefaultIcon: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onCopiedFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val ext = LocalWebForgeColors.current
    val clipboard = LocalClipboardManager.current

    var showWebsiteSheet by remember { mutableStateOf(false) }
    var showAndroidSheet by remember { mutableStateOf(false) }
    var showIosDesktopSheet by remember { mutableStateOf(false) }
    var showPwaSheet by remember { mutableStateOf(false) }
    var showStorePackDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var showAppShellPreview by remember { mutableStateOf(false) }
    var qrUrlOverride by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
            Text(
                text = strings.exportTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )

            if (!state.publishedUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ext.success.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, ext.success.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.deploySuccessTitle, style = MaterialTheme.typography.labelSmall, color = ext.success, fontWeight = FontWeight.Bold)
                            Text(
                                text = state.publishedUrl,
                                style = TextStyle(fontFamily = MonoFontFamily, fontSize = 12.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(state.publishedUrl))
                            onCopiedFeedback()
                        }) {
                            Icon(Icons.Rounded.ContentCopy, strings.copyUrl, tint = ext.success, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            ExportOptionCard(
                title = strings.websiteCardTitle,
                subtitle = strings.websiteCardSubtitle,
                badgeColor = WebForgePrimary,
                icon = Icons.Rounded.Language,
                testTag = "export_website_card",
                onClick = { showWebsiteSheet = true },
                trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
            Spacer(modifier = Modifier.height(10.dp))

            ExportOptionCard(
                title = strings.singleHtmlTitle,
                subtitle = strings.singleHtmlSubtitle,
                badgeColor = WebForgeWarning,
                icon = Icons.Rounded.Code,
                testTag = "export_single_html_card",
                onClick = { onExportSingleHtml(true) },
                trailing = { Icon(Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
            Spacer(modifier = Modifier.height(10.dp))

            ExportOptionCard(
                title = strings.androidApkCardTitle,
                subtitle = strings.androidApkCardSubtitle,
                badgeColor = WebForgeSuccess,
                icon = Icons.Rounded.Android,
                testTag = "export_android_card",
                onClick = { showAndroidSheet = true },
                trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
            Spacer(modifier = Modifier.height(10.dp))

            ExportOptionCard(
                title = strings.iosDesktopTitle,
                subtitle = strings.iosDesktopSubtitle,
                badgeColor = WebForgeAccent,
                icon = Icons.Rounded.PhoneIphone,
                testTag = "export_ios_desktop_card",
                onClick = { showIosDesktopSheet = true },
                trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
            Spacer(modifier = Modifier.height(10.dp))

            ExportOptionCard(
                title = strings.pwaCardTitle,
                subtitle = strings.pwaCardSubtitle,
                badgeColor = WebForgeAccent,
                icon = Icons.Rounded.InstallMobile,
                testTag = "export_pwa_card",
                onClick = { showPwaSheet = true },
                trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
            Spacer(modifier = Modifier.height(10.dp))

            ExportOptionCard(
                title = strings.storePackTitle,
                subtitle = strings.storePackSubtitle,
                badgeColor = WebForgeSoftViolet,
                icon = Icons.Rounded.Storefront,
                testTag = "export_store_pack_card",
                onClick = { showStorePackDialog = true },
                trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
            Spacer(modifier = Modifier.height(10.dp))

            ExportOptionCard(
                title = strings.qrCardTitle,
                subtitle = strings.qrCardSubtitle,
                badgeColor = WebForgeSoftViolet,
                icon = Icons.Rounded.QrCode2,
                testTag = "export_qr_card",
                onClick = {
                    qrUrlOverride = state.previewDisplayUrl
                    showQrDialog = true
                },
                trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            )

            Spacer(modifier = Modifier.height(18.dp))

            GradientButton(
                text = strings.downloadBundle,
                icon = Icons.Rounded.Download,
                brush = MintCtaBrush,
                contentColor = Color(0xFF0C0A20),
                testTag = "download_bundle_button",
                onClick = { onExportWebsiteZip(true, true) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(22.dp))
        }
    }

    if (showWebsiteSheet) {
        HostingDeploySheet(
            projectName = state.projectName,
            customDomain = state.appConfig.customDomain,
            savedTokens = state.settings.savedTokens,
            deployState = state.deployState,
            isOffline = state.isEffectiveOffline,
            isRtl = state.settings.language.isRtl,
            onCustomDomainChange = { dom -> onUpdateAppConfig { it.copy(customDomain = dom) } },
            onDismiss = {
                onResetDeployState()
                showWebsiteSheet = false
            },
            onDeploy = onDeploy,
            onCopyUrl = { url ->
                clipboard.setText(AnnotatedString(url))
                onCopiedFeedback()
            },
            onOpenUrl = onOpenUrl,
            onShowQr = { url ->
                qrUrlOverride = url
                showWebsiteSheet = false
                showQrDialog = true
            },
            onExportZip = { saveToDevice -> onExportWebsiteZip(false, saveToDevice) },
        )
    }

    if (showAndroidSheet) {
        AndroidConfigSheet(
            config = state.appConfig,
            isRtl = state.settings.language.isRtl,
            onConfigChange = onUpdateAppConfig,
            onPickCustomIcon = onPickCustomIcon,
            onResetDefaultIcon = onResetDefaultIcon,
            onExportAndroidProject = onExportAndroidZip,
            onExportKeystoreGuide = onExportKeystoreGuide,
            onPreviewAppShell = {
                showAndroidSheet = false
                showAppShellPreview = true
            },
            onDismiss = { showAndroidSheet = false },
        )
    }

    if (showIosDesktopSheet) {
        IosDesktopSheet(
            onExportIosZip = onExportIosZip,
            onExportElectronZip = onExportElectronZip,
            onDismiss = { showIosDesktopSheet = false },
        )
    }

    if (showPwaSheet) {
        PwaSheet(
            projectName = state.projectName,
            config = state.appConfig,
            onInjectPwa = onInjectPwa,
            onExportPwaZip = { saveToDevice -> onExportWebsiteZip(true, saveToDevice) },
            onDismiss = { showPwaSheet = false },
        )
    }

    if (showStorePackDialog) {
        StorePackDialog(
            isGenerating = state.isGeneratingStorePack,
            isOffline = state.isEffectiveOffline,
            isRtl = state.settings.language.isRtl,
            onExportStorePack = onExportStorePack,
            onDismiss = { showStorePackDialog = false },
        )
    }

    if (showQrDialog) {
        QrCodeDialog(
            initialUrl = qrUrlOverride ?: state.previewDisplayUrl,
            onCopyUrl = { url ->
                clipboard.setText(AnnotatedString(url))
                onCopiedFeedback()
            },
            onExportQr = onExportQrPng,
            onDismiss = { showQrDialog = false },
        )
    }

    if (showAppShellPreview) {
        AppShellPreviewDialog(
            html = state.htmlContent,
            assets = state.assets,
            config = state.appConfig,
            onDismiss = { showAppShellPreview = false },
        )
    }
}
