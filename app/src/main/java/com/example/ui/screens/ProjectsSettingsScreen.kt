package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ai.GeminiConfig
import com.example.data.model.AppLanguage
import com.example.data.model.EditorTheme
import com.example.data.model.ProjectSnapshot
import com.example.data.model.ProjectSort
import com.example.data.model.ProjectSummary
import com.example.data.model.ThemeMode
import com.example.ui.WebForgeUiState
import com.example.ui.components.AdminControlPanelDialog
import com.example.ui.components.GlowingFrameCard
import com.example.ui.components.GradientButton
import com.example.ui.components.OutlinedGlowButton
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.LocalWebForgeColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ProjectsSettingsScreen(
    state: WebForgeUiState,
    savedProjects: List<ProjectSummary>,
    historySnapshots: List<ProjectSnapshot>,
    onSaveProjectNow: () -> Unit,
    onSaveSnapshot: () -> Unit,
    onReopenProject: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    onDuplicateProject: (Long) -> Unit,
    onDeleteProject: (Long) -> Unit,
    onRestoreSnapshot: (Long) -> Unit,
    onExportBackup: (saveToDevice: Boolean) -> Unit,
    onImportBackup: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onEditorFontSizeChange: (Int) -> Unit,
    onEditorThemeChange: (EditorTheme) -> Unit,
    onAutoSaveChange: (Boolean) -> Unit,
    onOfflineModeChange: (Boolean) -> Unit,
    onClearTokens: () -> Unit,
    onClearAllData: () -> Unit,
    onReopenOnboarding: () -> Unit,
    onNotifyMessage: (String) -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val ext = LocalWebForgeColors.current
    val context = LocalContext.current
    val formatter = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }

    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf(ProjectSort.RECENT) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showAdminPanel by remember { mutableStateOf(false) }

    val displayedProjects = remember(savedProjects, searchQuery, sortBy) {
        val filtered = savedProjects.filter {
            searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)
        }
        when (sortBy) {
            ProjectSort.RECENT -> filtered.sortedByDescending { it.updatedAt }
            ProjectSort.NAME -> filtered.sortedBy { it.name.lowercase() }
            ProjectSort.FAVORITES -> filtered.sortedWith(compareByDescending<ProjectSummary> { it.isFavorite }.thenByDescending { it.updatedAt })
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
            // Save & Snapshot
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientButton(
                    text = strings.saveProjectNow,
                    icon = Icons.Rounded.Save,
                    onClick = onSaveProjectNow,
                    modifier = Modifier.weight(1f),
                    testTag = "save_project_now_button",
                )
                OutlinedGlowButton(
                    text = strings.saveSnapshot,
                    icon = Icons.Rounded.BookmarkAdd,
                    onClick = onSaveSnapshot,
                    modifier = Modifier.weight(1f),
                    testTag = "save_snapshot_button",
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // JSON Backup Export / Import
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedGlowButton(
                    text = strings.backupExport,
                    icon = Icons.Rounded.Download,
                    onClick = { onExportBackup(true) },
                    modifier = Modifier.weight(1f),
                )
                OutlinedGlowButton(
                    text = strings.backupImport,
                    icon = Icons.Rounded.UploadFile,
                    onClick = onImportBackup,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(strings.savedProjectsTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            // Project Search & Sort
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(strings.searchPlaceholder) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("projects_search_input"),
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(sortBy == ProjectSort.RECENT, { sortBy = ProjectSort.RECENT }, { Text("Recent") })
                FilterChip(sortBy == ProjectSort.NAME, { sortBy = ProjectSort.NAME }, { Text("Name") })
                FilterChip(sortBy == ProjectSort.FAVORITES, { sortBy = ProjectSort.FAVORITES }, { Text("Favorites ★") })
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (displayedProjects.isEmpty()) {
                GlowingFrameCard(modifier = Modifier.fillMaxWidth(), glowing = false) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.FolderOpen, null, tint = ext.accent, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(strings.noSavedProjects, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                displayedProjects.forEach { project ->
                    val isActive = project.id == state.activeProjectId
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(if (isActive) 1.5.dp else 1.dp, if (isActive) ext.glowBorder else ext.cardBorder),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = project.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (isActive) {
                                            Spacer(Modifier.width(8.dp))
                                            Surface(color = ext.success.copy(alpha = 0.2f), shape = RoundedCornerShape(50.dp)) {
                                                Text(
                                                    text = strings.activeProjectBadge,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = ext.success,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "${formatter.format(Date(project.updatedAt))} • ${project.assetCount + 1} ${strings.assetsCountLabel}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Row {
                                    IconButton(onClick = { onToggleFavorite(project.id, project.isFavorite) }) {
                                        Icon(
                                            imageVector = if (project.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                            contentDescription = "Favorite",
                                            tint = if (project.isFavorite) ext.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    IconButton(onClick = { onDuplicateProject(project.id) }) {
                                        Icon(Icons.Rounded.ContentCopy, strings.duplicateProject, tint = ext.accent, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { onDeleteProject(project.id) }) {
                                        Icon(Icons.Rounded.DeleteOutline, strings.deleteProject, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(19.dp))
                                    }
                                }
                            }
                            if (!isActive) {
                                TextButton(
                                    onClick = { onReopenProject(project.id) },
                                    modifier = Modifier.testTag("reopen_project_${project.id}"),
                                ) {
                                    Icon(Icons.Rounded.FolderOpen, null, Modifier.size(16.dp), tint = ext.accent)
                                    Spacer(Modifier.width(6.dp))
                                    Text(strings.reopenProject, color = ext.accent)
                                }
                            }
                        }
                    }
                }
            }

            if (historySnapshots.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.History, null, tint = ext.softViolet, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.historySnapshots, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                historySnapshots.forEach { snap ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, ext.cardBorder),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(snap.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "${formatter.format(Date(snap.createdAt))} • ${snap.htmlSizeBytes} B",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { onRestoreSnapshot(snap.id) }) {
                                Icon(Icons.Rounded.Restore, null, Modifier.size(16.dp), tint = ext.accent)
                                Spacer(Modifier.width(4.dp))
                                Text(strings.reopenProject, color = ext.accent)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text(strings.settingsTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))

            GlowingFrameCard(modifier = Modifier.fillMaxWidth(), glowing = false) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Admin-Managed Gemini AI Proxy Status & Protected Control Panel Entry
                    val aiEnabled = GeminiConfig.isAiEnabled(context)
                    val activeProxy = GeminiConfig.resolveProxyUrl(context)
                    val isCustomProxy = GeminiConfig.getCustomProxyUrl(context).isNotBlank()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AdminPanelSettings, null, tint = ext.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("الذكاء الاصطناعي وإدارة التطبيق", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = when {
                            !aiEnabled -> "خدمة الذكاء الاصطناعي متوقفة مؤقتاً من لوحة تحكم المشرف."
                            isCustomProxy -> "الذكاء الاصطناعي مفعّل للجميع عبر خادم المشرف المخصص ✓ (بدون تسجيل دخول أو مفتاح)"
                            else -> "الذكاء الاصطناعي مفعّل وجاهز تلقائياً للجميع ✓ (${activeProxy.removePrefix("https://")})"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (aiEnabled) ext.success else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedGlowButton(
                        text = "لوحة تحكم المشرف (حساب Google + كلمة سر)",
                        icon = Icons.Rounded.Shield,
                        onClick = { showAdminPanel = true },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "open_admin_panel_button",
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = ext.cardBorder)

                    // 4 Languages
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Language, null, tint = ext.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.languageSetting, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = state.settings.language == lang,
                                onClick = { onLanguageChange(lang) },
                                label = { Text(lang.displayLabel) },
                                modifier = Modifier.testTag("lang_${lang.code}_chip"),
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = ext.cardBorder)

                    // App Theme Mode
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Palette, null, tint = ext.softViolet, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.themeSetting, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(state.settings.themeMode == ThemeMode.DARK, { onThemeModeChange(ThemeMode.DARK) }, { Text(strings.darkTheme) }, modifier = Modifier.testTag("theme_dark_chip"))
                        FilterChip(state.settings.themeMode == ThemeMode.LIGHT, { onThemeModeChange(ThemeMode.LIGHT) }, { Text(strings.lightTheme) }, modifier = Modifier.testTag("theme_light_chip"))
                        FilterChip(state.settings.themeMode == ThemeMode.SYSTEM, { onThemeModeChange(ThemeMode.SYSTEM) }, { Text(strings.systemTheme) })
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = ext.cardBorder)

                    // Editor Theme + Font Size
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.TextFields, null, tint = ext.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Editor Font (${state.settings.editorFontSize}sp) & Theme", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = state.settings.editorFontSize.toFloat(),
                        onValueChange = { onEditorFontSizeChange(it.roundToInt()) },
                        valueRange = 10f..20f,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        EditorTheme.entries.forEach { et ->
                            FilterChip(
                                selected = state.settings.editorTheme == et,
                                onClick = { onEditorThemeChange(et) },
                                label = { Text(et.label) },
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = ext.cardBorder)

                    // Auto-Save
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(strings.autoSaveSetting, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(strings.autoSaveSubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.settings.autoSave,
                            onCheckedChange = onAutoSaveChange,
                            colors = SwitchDefaults.colors(checkedTrackColor = ext.success),
                            modifier = Modifier.testTag("autosave_switch"),
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = ext.cardBorder)

                    // Offline Mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CloudOff, null, tint = ext.warning, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.offlineModeSetting, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            Text(strings.offlineModeSubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.settings.offlineMode,
                            onCheckedChange = onOfflineModeChange,
                            colors = SwitchDefaults.colors(checkedTrackColor = ext.warning),
                            modifier = Modifier.testTag("offline_mode_switch"),
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = ext.cardBorder)

                    // Clear Tokens, Clear All Data & Reopen Onboarding
                    TextButton(onClick = onClearTokens) {
                        Text(strings.clearTokens, color = ext.warning)
                    }
                    TextButton(onClick = { showClearConfirm = true }) {
                        Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.clearAllData, color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onReopenOnboarding) {
                        Icon(Icons.Rounded.Info, null, tint = ext.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("WebForge v2.0 • Onboarding", color = ext.accent)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Developer Attribution & Telegram Channels Card
            GlowingFrameCard(modifier = Modifier.fillMaxWidth(), glowing = false) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, null, tint = ext.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("الحقوق والتواصل والاشتراك", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "جميع الحقوق محفوظة لصالح قناة تطبيقاتي | Apk Plus على تليجرام",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ext.accent,
                    )
                    GradientButton(
                        text = "تواصل مع المطور (تليجرام)",
                        icon = Icons.AutoMirrored.Rounded.Send,
                        onClick = { onOpenUrl("https://t.me/Hamodi9005") },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "developer_telegram_contact_button",
                    )
                    Text(
                        text = "اشترك في قنواتنا على تليجرام:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedGlowButton(
                        text = "قناة تطبيقاتي | Apk Plus (@qweqwe2019bot2bot)",
                        icon = Icons.AutoMirrored.Rounded.Send,
                        onClick = { onOpenUrl("https://t.me/qweqwe2019bot2bot") },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "channel_apk_plus_button",
                    )
                    OutlinedGlowButton(
                        text = "قناة @Abu2Saleh",
                        icon = Icons.AutoMirrored.Rounded.Send,
                        onClick = { onOpenUrl("https://t.me/Abu2Saleh") },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "channel_abu2saleh_button",
                    )
                    OutlinedGlowButton(
                        text = "قناة @pixels_x_mods_apk",
                        icon = Icons.AutoMirrored.Rounded.Send,
                        onClick = { onOpenUrl("https://t.me/pixels_x_mods_apk") },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "channel_pixels_mods_button",
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showAdminPanel) {
        AdminControlPanelDialog(
            onDismiss = { showAdminPanel = false },
            onNotifyMessage = onNotifyMessage,
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(strings.clearAllData) },
            text = { Text("This permanently deletes all local saved projects, snapshots, and tokens.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    onClearAllData()
                }) { Text(strings.deleteProject, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text(strings.cancel) }
            },
        )
    }
}
