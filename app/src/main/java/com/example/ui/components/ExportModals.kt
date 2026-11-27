package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.SavedTokens
import com.example.data.model.AppExportConfig
import com.example.data.model.AppOrientation
import com.example.data.model.AppSourceMode
import com.example.data.model.BackButtonBehavior
import com.example.data.model.DeployState
import com.example.data.model.HostingProvider
import com.example.domain.HostingDeployer
import com.example.domain.HtmlToolkit
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.LocalWebForgeColors
import com.example.ui.theme.MonoFontFamily
import kotlinx.coroutines.delay
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostingDeploySheet(
    projectName: String,
    customDomain: String,
    savedTokens: SavedTokens,
    deployState: DeployState,
    isOffline: Boolean,
    isRtl: Boolean,
    initialProvider: HostingProvider = HostingProvider.FREE_INSTANT,
    onCustomDomainChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onDeploy: (HostingProvider, token: String, primaryParam: String, secondaryParam: String, saveToken: Boolean) -> Unit,
    onCopyUrl: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onShowQr: (String) -> Unit,
    onExportZip: (saveToDevice: Boolean) -> Unit,
) {
    val strings = LocalStrings.current
    val ext = LocalWebForgeColors.current
    val clipboard = LocalClipboardManager.current
    val tokenVerifier = remember { HostingDeployer() }
    var selectedProvider by remember(initialProvider) { mutableStateOf(initialProvider) }
    var token by remember { mutableStateOf("") }
    var primaryParam by remember { mutableStateOf("") }
    var secondaryParam by remember { mutableStateOf(HtmlToolkit.slugify(projectName)) }
    var saveTokenLocally by remember { mutableStateOf(true) }
    var showDnsGuide by remember { mutableStateOf(false) }
    var isVerifyingGithub by remember { mutableStateOf(false) }
    var githubTokenValid by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(selectedProvider, savedTokens) {
        githubTokenValid = null
        when (selectedProvider) {
            HostingProvider.FREE_INSTANT -> {
                token = ""
                primaryParam = ""
            }
            HostingProvider.GITHUB_PAGES -> {
                token = savedTokens.githubToken
                primaryParam = savedTokens.githubOwner
                secondaryParam = HtmlToolkit.slugify(projectName)
            }
            HostingProvider.NETLIFY -> {
                token = savedTokens.netlifyToken
                primaryParam = ""
            }
            HostingProvider.VERCEL -> {
                token = savedTokens.vercelToken
                primaryParam = ""
            }
            HostingProvider.CLOUDFLARE_PAGES -> {
                token = savedTokens.cloudflareToken
                primaryParam = savedTokens.cloudflareAccountId
            }
            HostingProvider.FIREBASE_HOSTING -> {
                token = savedTokens.firebaseToken
                primaryParam = savedTokens.firebaseSiteId
            }
        }
    }

    LaunchedEffect(selectedProvider, token) {
        if (selectedProvider != HostingProvider.GITHUB_PAGES || token.isBlank()) {
            isVerifyingGithub = false
            githubTokenValid = null
            return@LaunchedEffect
        }
        isVerifyingGithub = true
        delay(450L)
        val login = tokenVerifier.verifyGitHubToken(token)
        isVerifyingGithub = false
        if (login != null) {
            githubTokenValid = true
            primaryParam = login
        } else {
            githubTokenValid = false
        }
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
            Text(strings.directHostingDeploy, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedGlowButton(
                    text = strings.saveZipToDevice,
                    icon = Icons.Rounded.Download,
                    onClick = { onExportZip(true) },
                    modifier = Modifier.weight(1f),
                    testTag = "save_website_zip_button",
                )
                OutlinedGlowButton(
                    text = strings.shareZip,
                    icon = Icons.Rounded.Share,
                    onClick = { onExportZip(false) },
                    modifier = Modifier.weight(1f),
                    testTag = "share_website_zip_button",
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5 Cloud Hosting Providers
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HostingProvider.entries.forEach { prov ->
                    FilterChip(
                        selected = selectedProvider == prov,
                        onClick = { selectedProvider = prov },
                        label = { Text(prov.title) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (selectedProvider == HostingProvider.FREE_INSTANT) {
                Surface(
                    color = ext.success.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, ext.success.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = strings.freePublishNoTokenNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.success,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            } else {
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("${strings.tokenLabel} (${selectedProvider.title})") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("deploy_token_input"),
                )

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val tokenHelpUrl = when (selectedProvider) {
                        HostingProvider.GITHUB_PAGES -> "https://github.com/settings/tokens/new?scopes=repo&description=WebForge"
                        HostingProvider.NETLIFY -> "https://app.netlify.com/user/applications#personal-access-tokens"
                        HostingProvider.VERCEL -> "https://vercel.com/account/tokens"
                        HostingProvider.CLOUDFLARE_PAGES -> "https://dash.cloudflare.com/profile/api-tokens"
                        HostingProvider.FIREBASE_HOSTING -> "https://console.firebase.google.com/"
                        HostingProvider.FREE_INSTANT -> ""
                    }
                    if (tokenHelpUrl.isNotEmpty()) {
                        TextButton(
                            onClick = { onOpenUrl(tokenHelpUrl) },
                            modifier = Modifier.testTag("get_token_browser_button"),
                        ) {
                            Icon(Icons.Rounded.OpenInBrowser, null, Modifier.size(16.dp), tint = ext.accent)
                            Spacer(Modifier.width(4.dp))
                            Text(if (isRtl) "احصل على التوكن" else "Get Token", color = ext.accent)
                        }
                    }
                    TextButton(
                        onClick = {
                            val clipText = clipboard.getText()?.text?.trim().orEmpty()
                            if (clipText.isNotEmpty()) {
                                token = clipText
                            }
                        },
                        modifier = Modifier.testTag("paste_token_clipboard_button"),
                    ) {
                        Icon(Icons.Rounded.ContentPaste, null, Modifier.size(16.dp), tint = ext.success)
                        Spacer(Modifier.width(4.dp))
                        Text(if (isRtl) "لصق من الحافظة" else "Paste from Clipboard", color = ext.success)
                    }
                }

                if (selectedProvider == HostingProvider.GITHUB_PAGES && token.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp),
                    ) {
                        when {
                            isVerifyingGithub -> {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = ext.accent)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isRtl) "جاري التحقق من التوكن..." else "Verifying GitHub token...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.accent,
                                )
                            }
                            githubTokenValid == true -> {
                                Text(
                                    text = if (isRtl) "التوكن صالح ✓ (${primaryParam})" else "Token valid ✓ (${primaryParam})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ext.success,
                                )
                            }
                            githubTokenValid == false -> {
                                Text(
                                    text = if (isRtl) "التوكن غير صالح" else "Invalid token",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                when (selectedProvider) {
                    HostingProvider.FREE_INSTANT -> Unit
                    HostingProvider.GITHUB_PAGES -> {
                        OutlinedTextField(
                            value = primaryParam,
                            onValueChange = { primaryParam = it },
                            label = { Text("GitHub Username / Owner") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = secondaryParam,
                            onValueChange = { secondaryParam = it },
                            label = { Text("Repository Name") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    HostingProvider.NETLIFY -> {
                        OutlinedTextField(
                            value = primaryParam,
                            onValueChange = { primaryParam = it },
                            label = { Text("Netlify Site ID (Optional — auto-created if empty)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    HostingProvider.VERCEL -> {
                        Text(
                            text = if (isRtl) "سيتم إنشاء نشر فوري على Vercel وتوليد رابط *.vercel.app" else "Deploys instantly to Vercel with a live *.vercel.app URL",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HostingProvider.CLOUDFLARE_PAGES -> {
                        OutlinedTextField(
                            value = primaryParam,
                            onValueChange = { primaryParam = it },
                            label = { Text("Cloudflare Account ID") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    HostingProvider.FIREBASE_HOSTING -> {
                        OutlinedTextField(
                            value = primaryParam,
                            onValueChange = { primaryParam = it },
                            label = { Text("Firebase Hosting Site ID") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Checkbox(checked = saveTokenLocally, onCheckedChange = { saveTokenLocally = it })
                    Text(
                        text = if (isRtl) "حفظ التوكن محلياً للنشر السريع لاحقاً" else "Remember token locally for next deploy",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // Custom Domain (CNAME) & DNS Guide
            TextButton(onClick = { showDnsGuide = !showDnsGuide }) {
                Icon(Icons.Rounded.Dns, null, Modifier.size(16.dp), tint = ext.accent)
                Spacer(Modifier.width(6.dp))
                Text(if (isRtl) "ربط دومين خاص (CNAME + DNS)" else "Custom Domain (CNAME & DNS Guide)", color = ext.accent)
            }
            AnimatedVisibility(visible = showDnsGuide) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    border = BorderStroke(1.dp, ext.cardBorder),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        OutlinedTextField(
                            value = customDomain,
                            onValueChange = onCustomDomainChange,
                            label = { Text("Custom Domain (e.g. www.mysite.com)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (isRtl) {
                                "يُضاف ملف CNAME تلقائياً في ZIP والنشر. في لوحة DNS أضف سجل CNAME يشير إلى مضيفك (مثل user.github.io) أو سجل A للـ Apex."
                            } else {
                                "A CNAME file is automatically bundled into your ZIP/deploy. Point your DNS CNAME record to your host (e.g. user.github.io) or A records for apex domains."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (deployState) {
                is DeployState.InProgress -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = ext.accent)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("${strings.deploying} (${deployState.stepLabel})")
                    }
                }
                is DeployState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(deployState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                is DeployState.Success -> {
                    Surface(
                        color = ext.success.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, ext.success),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CheckCircle, null, tint = ext.success)
                                Spacer(Modifier.width(8.dp))
                                Text(strings.deploySuccessTitle, fontWeight = FontWeight.Bold, color = ext.success)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(deployState.url, style = TextStyle(fontFamily = MonoFontFamily, fontSize = 12.sp))
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { onCopyUrl(deployState.url) }) {
                                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.copyUrl)
                                }
                                TextButton(onClick = { onOpenUrl(deployState.url) }) {
                                    Icon(Icons.Rounded.OpenInBrowser, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.openUrl)
                                }
                                TextButton(onClick = { onShowQr(deployState.url) }) { Text("QR") }
                            }
                            Spacer(Modifier.height(6.dp))
                            OutlinedGlowButton(
                                text = if (isRtl) "بناء APK مجاني عبر PWABuilder" else "Build Free APK via PWABuilder",
                                icon = Icons.Rounded.Android,
                                onClick = {
                                    val encoded = URLEncoder.encode(deployState.url, "UTF-8")
                                    onOpenUrl("https://www.pwabuilder.com/reportcard?site=$encoded")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "pwabuilder_reportcard_button",
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                DeployState.Idle -> Unit
            }

            if (isOffline) {
                Text(strings.offlineDeployBlocked, style = MaterialTheme.typography.bodySmall, color = ext.warning)
                Spacer(modifier = Modifier.height(8.dp))
            }

            GradientButton(
                text = if (selectedProvider == HostingProvider.FREE_INSTANT) strings.freePublishButton else strings.deployNow,
                icon = Icons.Rounded.CloudUpload,
                enabled = !isOffline && deployState !is DeployState.InProgress,
                onClick = { onDeploy(selectedProvider, token, primaryParam, secondaryParam, saveTokenLocally) },
                modifier = Modifier.fillMaxWidth(),
                testTag = "deploy_now_button",
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidConfigSheet(
    config: AppExportConfig,
    publishedUrl: String?,
    isBuildingApk: Boolean,
    isRtl: Boolean,
    onConfigChange: ((AppExportConfig) -> AppExportConfig) -> Unit,
    onPickCustomIcon: () -> Unit,
    onResetDefaultIcon: () -> Unit,
    onBuildDirectApk: (saveToDevice: Boolean) -> Unit,
    onExportAndroidProject: (saveToDevice: Boolean) -> Unit,
    onExportKeystoreGuide: (saveToDevice: Boolean) -> Unit,
    onPreviewAppShell: () -> Unit,
    onOpenStoreGuide: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val ext = LocalWebForgeColors.current
    val colorPresets = listOf("#5B3DF5", "#00C6FF", "#0C0A20", "#34E0A1", "#FF5C7C", "#FFFFFF")
    val availablePermissions = listOf("INTERNET", "ACCESS_NETWORK_STATE", "CAMERA", "ACCESS_FINE_LOCATION", "RECORD_AUDIO", "POST_NOTIFICATIONS", "VIBRATE")
    var showAdvancedDeveloper by remember { mutableStateOf(false) }

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
            Text(strings.androidConfigTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                color = ext.success.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, ext.success.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (isRtl) {
                        "بناء وتوقيع ملف APK مباشرة من داخل التطبيق مجاناً 100% — بدون GitHub، بدون مفاتيح، وبدون أي توكن!"
                    } else {
                        "Builds and signs a real installable APK directly on-device — 100% free, no GitHub, no keys, no token!"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.success,
                    modifier = Modifier.padding(12.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // App Source Mode: HTML vs Website URL
            Text(strings.sourceModeLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = config.sourceMode == AppSourceMode.HTML,
                    onClick = { onConfigChange { it.copy(sourceMode = AppSourceMode.HTML) } },
                    label = { Text(strings.sourceHtmlMode) },
                    modifier = Modifier.testTag("android_source_html_chip"),
                )
                FilterChip(
                    selected = config.sourceMode == AppSourceMode.URL,
                    onClick = {
                        onConfigChange {
                            val defaultUrl = it.sourceUrl.ifBlank { publishedUrl.orEmpty() }
                            it.copy(sourceMode = AppSourceMode.URL, sourceUrl = defaultUrl)
                        }
                    },
                    label = { Text(strings.sourceUrlMode) },
                    modifier = Modifier.testTag("android_source_url_chip"),
                )
            }

            AnimatedVisibility(visible = config.sourceMode == AppSourceMode.URL) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = config.sourceUrl,
                        onValueChange = { v -> onConfigChange { it.copy(sourceUrl = v) } },
                        label = { Text(strings.sourceUrlInputLabel) },
                        leadingIcon = { Icon(Icons.Rounded.Link, null, tint = ext.accent) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("android_source_url_input"),
                    )
                    if (!publishedUrl.isNullOrBlank() && config.sourceUrl != publishedUrl) {
                        TextButton(onClick = { onConfigChange { it.copy(sourceUrl = publishedUrl) } }) {
                            Text(if (isRtl) "استخدام رابط الموقع المنشور ($publishedUrl)" else "Use published URL", color = ext.accent)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // App Icon Picker Row
            Text(strings.appIconLabel, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                AppIconPreview(
                    customIconBase64 = config.customIconBase64,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (config.customIconBase64 == null) strings.defaultWebForgeIcon else strings.chooseCustomIcon,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = onPickCustomIcon) {
                            Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.chooseCustomIcon)
                        }
                        if (config.customIconBase64 != null) {
                            TextButton(onClick = onResetDefaultIcon) {
                                Icon(Icons.Rounded.Restore, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(strings.resetDefaultIcon)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = config.appName,
                onValueChange = { v -> onConfigChange { it.copy(appName = v) } },
                label = { Text(strings.appDisplayNameLabel) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("android_app_name_input"),
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = config.packageName,
                onValueChange = { v -> onConfigChange { it.copy(packageName = v) } },
                label = { Text(strings.packageNameLabel) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("android_package_input"),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = config.versionName,
                    onValueChange = { v -> onConfigChange { it.copy(versionName = v) } },
                    label = { Text("Version Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = config.versionCode.toString(),
                    onValueChange = { v -> onConfigChange { it.copy(versionCode = v.toIntOrNull() ?: 1) } },
                    label = { Text("Code") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(0.7f),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Splash Screen Studio Card
            Surface(
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, ext.cardBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(strings.splashSectionTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ext.accent)
                    ToggleLine(strings.splashEnabledLabel, config.splashEnabled) {
                        onConfigChange { c -> c.copy(splashEnabled = it) }
                    }
                    AnimatedVisibility(visible = config.splashEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = config.splashTitle.ifEmpty { config.appName },
                                onValueChange = { v -> onConfigChange { it.copy(splashTitle = v) } },
                                label = { Text(strings.splashTitleLabel) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = config.splashSubtitle,
                                onValueChange = { v -> onConfigChange { it.copy(splashSubtitle = v) } },
                                label = { Text(strings.splashSubtitleLabel) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(strings.splashDurationLabel, style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(1000 to "1.0s", 1600 to "1.6s", 2500 to "2.5s").forEach { (ms, label) ->
                                    FilterChip(
                                        selected = config.splashDurationMs == ms,
                                        onClick = { onConfigChange { it.copy(splashDurationMs = ms) } },
                                        label = { Text(label) },
                                    )
                                }
                            }
                            Text(strings.splashBgColorLabel, style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                colorPresets.forEach { hex ->
                                    val parsed = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(ext.glowBorder)
                                    val selected = config.splashBgColorHex.equals(hex, ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(parsed)
                                            .border(if (selected) 3.dp else 1.dp, if (selected) ext.accent else ext.cardBorder, CircleShape)
                                            .clickable { onConfigChange { it.copy(splashBgColorHex = hex) } },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(strings.screenColorLabel, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                colorPresets.forEach { hex ->
                    val parsed = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(ext.glowBorder)
                    val selected = config.themeColorHex.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(parsed)
                            .border(if (selected) 3.dp else 1.dp, if (selected) ext.accent else ext.cardBorder, CircleShape)
                            .clickable { onConfigChange { it.copy(themeColorHex = hex, statusBarColorHex = hex, navBarColorHex = hex) } },
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(strings.orientationLabel, style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(config.orientation == AppOrientation.AUTO, { onConfigChange { it.copy(orientation = AppOrientation.AUTO) } }, { Text(strings.orientationAuto) })
                FilterChip(config.orientation == AppOrientation.PORTRAIT, { onConfigChange { it.copy(orientation = AppOrientation.PORTRAIT) } }, { Text(strings.orientationPortrait) })
                FilterChip(config.orientation == AppOrientation.LANDSCAPE, { onConfigChange { it.copy(orientation = AppOrientation.LANDSCAPE) } }, { Text(strings.orientationLandscape) })
            }

            // Rich Native WebToApp Behavior Toggles
            ToggleLine(if (isRtl) "وضع ملء الشاشة (Fullscreen)" else "Fullscreen Immersive Mode", config.fullscreen) {
                onConfigChange { c -> c.copy(fullscreen = it) }
            }
            ToggleLine(strings.bottomNavBarLabel, config.bottomNavBarEnabled) {
                onConfigChange { c -> c.copy(bottomNavBarEnabled = it) }
            }
            ToggleLine(strings.showFloatingBarLabel, config.showFloatingBar) {
                onConfigChange { c -> c.copy(showFloatingBar = it) }
            }
            ToggleLine(strings.showProgressBarLabel, config.showProgressBar) {
                onConfigChange { c -> c.copy(showProgressBar = it) }
            }
            ToggleLine(if (isRtl) "السحب للتحديث (Pull to Refresh)" else "Pull to Refresh", config.pullToRefresh) {
                onConfigChange { c -> c.copy(pullToRefresh = it) }
            }
            ToggleLine(strings.enableZoomLabel, config.enableZoom) {
                onConfigChange { c -> c.copy(enableZoom = it) }
            }
            ToggleLine(strings.desktopModeLabel, config.desktopMode) {
                onConfigChange { c -> c.copy(desktopMode = it) }
            }
            ToggleLine(if (isRtl) "إبقاء الشاشة مضاءة" else "Keep Screen On", config.keepScreenOn) {
                onConfigChange { c -> c.copy(keepScreenOn = it) }
            }
            ToggleLine(strings.openExternalLinksLabel, config.openExternalLinksInBrowser) {
                onConfigChange { c -> c.copy(openExternalLinksInBrowser = it) }
            }
            ToggleLine(strings.blockAdsTrackersLabel, config.blockAdsTrackers) {
                onConfigChange { c -> c.copy(blockAdsTrackers = it) }
            }
            ToggleLine(strings.preventScreenshotsLabel, config.preventScreenshots) {
                onConfigChange { c -> c.copy(preventScreenshots = it) }
            }
            ToggleLine(strings.exitDialogLabel, config.exitDialogEnabled) {
                onConfigChange { c -> c.copy(exitDialogEnabled = it) }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // App Launch Password Lock Card
            Surface(
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, ext.cardBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, null, tint = ext.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.appLockSectionTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ext.accent)
                    }
                    ToggleLine(strings.appLockEnabledLabel, config.appLockEnabled) {
                        onConfigChange { c -> c.copy(appLockEnabled = it) }
                    }
                    AnimatedVisibility(visible = config.appLockEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = config.appLockTitle,
                                onValueChange = { v -> onConfigChange { it.copy(appLockTitle = v) } },
                                label = { Text(strings.appLockTitleLabel) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = config.appLockPassword,
                                onValueChange = { v -> onConfigChange { it.copy(appLockPassword = v) } },
                                label = { Text(strings.appLockPasswordLabel) },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("app_lock_password_input"),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Multi-Network In-App Ads Monetization Card (Android & iOS)
            Surface(
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, ext.cardBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.MonetizationOn, null, tint = ext.success, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.adsSectionTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ext.success)
                    }
                    ToggleLine(strings.adsEnabledLabel, config.adsEnabled) {
                        onConfigChange { c -> c.copy(adsEnabled = it) }
                    }
                    AnimatedVisibility(visible = config.adsEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ToggleLine(if (isRtl) "إظهار بانر الإعلان بالأسفل" else "Show Bottom Ad Banner", config.adPlacementBottomBanner) {
                                onConfigChange { c -> c.copy(adPlacementBottomBanner = it) }
                            }
                            OutlinedTextField(
                                value = config.admobAppId,
                                onValueChange = { v -> onConfigChange { it.copy(admobAppId = v) } },
                                label = { Text("AdMob App ID (ca-app-pub-...)") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = config.admobBannerId,
                                onValueChange = { v -> onConfigChange { it.copy(admobBannerId = v) } },
                                label = { Text("AdMob Banner ID") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = config.admobInterstitialId,
                                onValueChange = { v -> onConfigChange { it.copy(admobInterstitialId = v) } },
                                label = { Text("AdMob Interstitial ID") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = config.metaAudienceBannerId,
                                onValueChange = { v -> onConfigChange { it.copy(metaAudienceBannerId = v) } },
                                label = { Text("Meta / Facebook Audience Placement ID") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = config.unityGameId,
                                    onValueChange = { v -> onConfigChange { it.copy(unityGameId = v) } },
                                    label = { Text("Unity Game ID") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = config.appLovinSdkKey,
                                    onValueChange = { v -> onConfigChange { it.copy(appLovinSdkKey = v) } },
                                    label = { Text("AppLovin Key") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            OutlinedTextField(
                                value = config.customHtmlAdScript,
                                onValueChange = { v -> onConfigChange { it.copy(customHtmlAdScript = v) } },
                                label = { Text(if (isRtl) "كود إعلانات HTML/JS (AdSense / Adsterra / Monetag)" else "Custom HTML/JS Ad Script (AdSense/Adsterra)") },
                                maxLines = 3,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(if (isRtl) "سلوك زر الرجوع" else "Back Button Behavior", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = config.backButtonBehavior == BackButtonBehavior.WEB_HISTORY,
                    onClick = { onConfigChange { it.copy(backButtonBehavior = BackButtonBehavior.WEB_HISTORY) } },
                    label = { Text("Web History") },
                )
                FilterChip(
                    selected = config.backButtonBehavior == BackButtonBehavior.CONFIRM_EXIT,
                    onClick = { onConfigChange { it.copy(backButtonBehavior = BackButtonBehavior.CONFIRM_EXIT) } },
                    label = { Text("Confirm Exit") },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(if (isRtl) "صلاحيات الأندرويد" else "Android Permissions", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                availablePermissions.forEach { perm ->
                    val checked = perm in config.permissions
                    FilterChip(
                        selected = checked,
                        onClick = {
                            onConfigChange { c ->
                                val next = if (checked) c.permissions - perm else c.permissions + perm
                                c.copy(permissions = next)
                            }
                        },
                        label = { Text(perm) },
                    )
                }
            }

            // Primary Direct APK Build Action
            Spacer(modifier = Modifier.height(14.dp))
            AnimatedVisibility(visible = isBuildingApk) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ext.success)
                    Spacer(Modifier.height(4.dp))
                    Text(strings.buildingApkMsg, style = MaterialTheme.typography.bodySmall, color = ext.success)
                }
            }

            GradientButton(
                text = strings.buildDirectApkBtn,
                icon = Icons.Rounded.Build,
                brush = MintCtaBrush,
                contentColor = Color(0xFF0C0A20),
                enabled = !isBuildingApk,
                onClick = { onBuildDirectApk(true) },
                modifier = Modifier.fillMaxWidth(),
                testTag = "build_direct_apk_button",
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedGlowButton(
                    text = strings.shareApkBtn,
                    icon = Icons.Rounded.Share,
                    onClick = { if (!isBuildingApk) onBuildDirectApk(false) },
                    modifier = Modifier.weight(1f),
                    testTag = "share_direct_apk_button",
                )
                OutlinedGlowButton(
                    text = strings.previewAppShell,
                    icon = Icons.Rounded.PhoneAndroid,
                    onClick = onPreviewAppShell,
                    modifier = Modifier.weight(1f),
                    testTag = "preview_app_shell_button",
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            GradientButton(
                text = strings.exportAabProjectBtn,
                icon = Icons.Rounded.Android,
                onClick = { onExportAndroidProject(true) },
                modifier = Modifier.fillMaxWidth(),
                testTag = "export_aab_project_button",
            )
            Spacer(modifier = Modifier.height(6.dp))
            TextButton(onClick = onOpenStoreGuide, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Storefront, null, Modifier.size(16.dp), tint = ext.accent)
                Spacer(Modifier.width(6.dp))
                Text(strings.freeStorePublishBtn, color = ext.accent)
            }

            // Collapsible Advanced / Source Export Options
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = { showAdvancedDeveloper = !showAdvancedDeveloper }) {
                Icon(if (showAdvancedDeveloper) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.size(18.dp), tint = ext.accent)
                Spacer(Modifier.width(6.dp))
                Text(if (isRtl) "خيارات متقدمة (Deep Link / TWA / User-Agent / About)" else "Advanced Options (Deep Link / TWA / User-Agent)", color = ext.accent)
            }

            AnimatedVisibility(visible = showAdvancedDeveloper) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = config.deepLinkScheme,
                        onValueChange = { v -> onConfigChange { it.copy(deepLinkScheme = v) } },
                        label = { Text("Deep Link Scheme (e.g. myapp)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = config.aboutAppText,
                        onValueChange = { v -> onConfigChange { it.copy(aboutAppText = v) } },
                        label = { Text(if (isRtl) "نص نافذة حول التطبيق (About)" else "About App Dialog Text") },
                        maxLines = 2,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = config.customUserAgent,
                        onValueChange = { v -> onConfigChange { it.copy(customUserAgent = v) } },
                        label = { Text("Custom User-Agent (Optional)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = config.twaHost,
                        onValueChange = { v -> onConfigChange { it.copy(twaHost = v) } },
                        label = { Text("TWA Domain Host (Optional, for assetlinks.json)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = config.twaSha256,
                        onValueChange = { v -> onConfigChange { it.copy(twaSha256 = v) } },
                        label = { Text("TWA Signing SHA-256 Fingerprint") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedGlowButton(
                            text = strings.shareZip,
                            icon = Icons.Rounded.Share,
                            onClick = { onExportAndroidProject(false) },
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedGlowButton(
                            text = "Keystore Guide",
                            icon = Icons.Rounded.Key,
                            onClick = { onExportKeystoreGuide(true) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ToggleLine(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
