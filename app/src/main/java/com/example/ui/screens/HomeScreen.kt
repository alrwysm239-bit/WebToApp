package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ProjectSummary
import com.example.domain.TemplateCatalog
import com.example.ui.WebForgeUiState
import com.example.ui.components.GlowingFrameCard
import com.example.ui.components.GradientButton
import com.example.ui.components.MintCtaBrush
import com.example.ui.components.PrimaryCtaBrush
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.LocalWebForgeColors
import com.example.ui.theme.WebForgeAccent
import com.example.ui.theme.WebForgePrimary
import com.example.ui.theme.WebForgeSoftViolet
import com.example.ui.theme.WebForgeSuccess
import com.example.ui.theme.WebForgeWarning

@Composable
fun HomeScreen(
    state: WebForgeUiState,
    savedProjects: List<ProjectSummary>,
    onNewBlankProject: () -> Unit,
    onContinueEditor: () -> Unit,
    onPublishFreeClick: () -> Unit,
    onOpenApkBuilder: () -> Unit,
    onOpenIosBuilder: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenToolsHub: () -> Unit,
    onOpenAiStudio: (initialPrompt: String?) -> Unit,
    onOpenImportDialog: () -> Unit,
    onReopenProject: (Long) -> Unit,
    onOpenUrl: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val ext = LocalWebForgeColors.current
    val isRtl = state.settings.language.isRtl
    var quickPrompt by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth()) {
            // Hero Studio Card with Quick Gemini AI App & Website Generator
            GlowingFrameCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.appSubtitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${strings.activeProjectBadge}: ${state.projectName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Surface(
                            onClick = onContinueEditor,
                            shape = RoundedCornerShape(12.dp),
                            color = ext.success.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, ext.success.copy(alpha = 0.45f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.PlayArrow, null, tint = ext.success, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(strings.navEditor, style = MaterialTheme.typography.labelSmall, color = ext.success, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    val homeAiChips = remember(isRtl) {
                        if (isRtl) {
                            listOf(
                                "تطبيق متجر إلكتروني" to "تطبيق متجر إلكتروني بالعربية مع سلة تسوق وحفظ محلي",
                                "تطبيق مهام" to "تطبيق مهام To-Do ذكي مع تبويبات وإحصائيات",
                                "لعبة ذكاء" to "لعبة تفاعلية حديثة بنظام نقاط وتصميم نيون",
                                "موقع تعريفي" to "موقع شركة أو بورتفوليو احترافي متجاوب",
                            )
                        } else {
                            listOf(
                                "Shop App" to "Interactive mobile store app with localStorage cart",
                                "To-Do App" to "Mobile task manager app with stats & tabs",
                                "Game" to "Neon puzzle mini-game with scoreboard",
                                "Landing Site" to "Modern responsive landing page website",
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        homeAiChips.forEach { (label, prompt) ->
                            FilterChip(
                                selected = quickPrompt == prompt,
                                onClick = {
                                    quickPrompt = prompt
                                    onOpenAiStudio(prompt)
                                },
                                label = { Text(label) },
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    OutlinedTextField(
                        value = quickPrompt,
                        onValueChange = { quickPrompt = it },
                        label = { Text(strings.aiGenerateHint) },
                        maxLines = 2,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ext.accent,
                            unfocusedBorderColor = ext.cardBorder,
                            focusedContainerColor = MaterialTheme.colorScheme.background,
                            unfocusedContainerColor = MaterialTheme.colorScheme.background,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_ai_prompt_input"),
                    )

                    Spacer(Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GradientButton(
                            text = if (isRtl) "صناعة تطبيق / موقع AI ✨" else strings.aiStudioTitle,
                            icon = Icons.Rounded.AutoAwesome,
                            brush = PrimaryCtaBrush,
                            onClick = { onOpenAiStudio(quickPrompt.takeIf { it.isNotBlank() }) },
                            modifier = Modifier.weight(1f),
                            testTag = "home_open_ai_button",
                        )
                        GradientButton(
                            text = strings.newProject,
                            icon = Icons.Rounded.AddCircleOutline,
                            brush = MintCtaBrush,
                            contentColor = Color(0xFF0C0A20),
                            onClick = onNewBlankProject,
                            modifier = Modifier.weight(1f),
                            testTag = "home_new_project_button",
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 6 Core Studio Hub Tiles (2 columns)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HubTileCard(
                    title = strings.templatesTitle,
                    subtitle = "${TemplateCatalog.allTemplates.size} HTML5",
                    icon = Icons.Rounded.DashboardCustomize,
                    accent = WebForgePrimary,
                    onClick = onOpenTemplates,
                    modifier = Modifier.weight(1f).testTag("home_templates_card"),
                )
                HubTileCard(
                    title = strings.toolsHubTitle,
                    subtitle = "Audit • WebP • Icons",
                    icon = Icons.Rounded.Build,
                    accent = WebForgeAccent,
                    onClick = onOpenToolsHub,
                    modifier = Modifier.weight(1f).testTag("home_tools_card"),
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HubTileCard(
                    title = strings.myProjects,
                    subtitle = "${savedProjects.size} saved",
                    icon = Icons.Rounded.FolderCopy,
                    accent = WebForgeSuccess,
                    onClick = onOpenProjects,
                    modifier = Modifier.weight(1f).testTag("home_projects_card"),
                )
                HubTileCard(
                    title = strings.importFromUrl,
                    subtitle = "URL & GitHub",
                    icon = Icons.Rounded.CloudDownload,
                    accent = WebForgeSoftViolet,
                    onClick = onOpenImportDialog,
                    modifier = Modifier.weight(1f).testTag("home_import_card"),
                )
            }

            Spacer(Modifier.height(10.dp))

            HubTileCard(
                title = strings.homeBuildApkTitle,
                subtitle = strings.homeBuildApkSubtitle,
                icon = Icons.Rounded.Android,
                accent = WebForgeSuccess,
                onClick = onOpenApkBuilder,
                modifier = Modifier.fillMaxWidth().testTag("home_build_apk_card"),
            )

            Spacer(Modifier.height(10.dp))

            HubTileCard(
                title = strings.homeBuildIosTitle,
                subtitle = strings.homeBuildIosSubtitle,
                icon = Icons.Rounded.PhoneIphone,
                accent = WebForgeAccent,
                onClick = onOpenIosBuilder,
                modifier = Modifier.fillMaxWidth().testTag("home_build_ios_card"),
            )

            Spacer(Modifier.height(10.dp))

            HubTileCard(
                title = strings.freePublishTitle,
                subtitle = strings.freePublishSubtitle,
                icon = Icons.Rounded.Public,
                accent = WebForgeSuccess,
                onClick = onPublishFreeClick,
                modifier = Modifier.fillMaxWidth().testTag("home_free_publish_card"),
            )

            Spacer(Modifier.height(10.dp))

            HubTileCard(
                title = strings.settingsTitle,
                subtitle = "${state.settings.language.displayLabel} • ${state.settings.editorTheme.label}",
                icon = Icons.Rounded.Settings,
                accent = WebForgeWarning,
                onClick = onOpenProjects,
                modifier = Modifier.fillMaxWidth().testTag("home_settings_card"),
            )

            // Recent / Favorite Projects Preview
            if (savedProjects.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = strings.savedProjectsTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                val topProjects = remember(savedProjects) {
                    savedProjects.sortedByDescending { it.isFavorite }.take(4)
                }
                topProjects.forEach { proj ->
                    Surface(
                        onClick = { onReopenProject(proj.id) },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, ext.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (proj.isFavorite) {
                                        Icon(
                                            imageVector = Icons.Rounded.Star,
                                            contentDescription = null,
                                            tint = ext.warning,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = proj.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = "${proj.assetCount + 1} ${strings.assetsCountLabel} • ${proj.htmlLength} chars",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = strings.reopenProject,
                                style = MaterialTheme.typography.labelMedium,
                                color = ext.accent,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Surface(
                onClick = { onOpenUrl("https://t.me/qweqwe2019bot2bot") },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, ext.cardBorder),
                modifier = Modifier.fillMaxWidth().testTag("home_rights_footer_card"),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "جميع الحقوق محفوظة لصالح قناة تطبيقاتي | Apk Plus على تليجرام",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = ext.accent,
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { onOpenUrl("https://t.me/Hamodi9005") }) {
                        Text("تواصل مع المطور: @Hamodi9005", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun HubTileCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ext = LocalWebForgeColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, ext.cardBorder),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
