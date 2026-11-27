package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ai.AiQuickAction
import com.example.data.model.AiChatMessage
import com.example.data.model.AiTaskState
import com.example.data.model.DiffType
import com.example.data.model.ProjectAsset
import com.example.data.model.TemplateItem
import com.example.domain.CodeLinterAndAudit
import com.example.domain.TemplateCatalog
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.LocalWebForgeColors
import com.example.ui.theme.MonoFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesBrowserSheet(
    isRtl: Boolean,
    onSelectTemplate: (TemplateItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val ext = LocalWebForgeColors.current
    var search by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf("ALL") }

    val categories = remember(isRtl) {
        val all = TemplateCatalog.allTemplates.map { if (isRtl) it.categoryAr else it.categoryEn }.distinct()
        listOf("ALL") + all
    }

    val filtered = remember(search, selectedCat, isRtl) {
        TemplateCatalog.allTemplates.filter { item ->
            val cat = if (isRtl) item.categoryAr else item.categoryEn
            val matchesCat = selectedCat == "ALL" || cat == selectedCat
            val title = if (isRtl) item.titleAr else item.titleEn
            val desc = if (isRtl) item.descriptionAr else item.descriptionEn
            val matchesSearch = search.isBlank() ||
                title.contains(search, true) ||
                desc.contains(search, true)
            matchesCat && matchesSearch
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
            Text(strings.templatesTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text(strings.searchPlaceholder) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("templates_search_input"),
            )

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCat == cat,
                        onClick = { selectedCat = cat },
                        label = { Text(if (cat == "ALL") (if (isRtl) "الكل" else "All") else cat) },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            filtered.forEach { tpl ->
                Surface(
                    onClick = {
                        onSelectTemplate(tpl)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.background,
                    border = BorderStroke(1.dp, ext.cardBorder),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(tpl.accentHex)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = (if (isRtl) tpl.titleAr else tpl.titleEn).take(1),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isRtl) tpl.titleAr else tpl.titleEn,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = if (isRtl) tpl.descriptionAr else tpl.descriptionEn,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = ext.accent.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(
                                text = if (isRtl) tpl.categoryAr else tpl.categoryEn,
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.accent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerSheet(
    assets: List<ProjectAsset>,
    selectedPath: String?,
    onSelectFile: (String?) -> Unit,
    onCreateFile: (String) -> Unit,
    onRenameFile: (oldPath: String, newPath: String) -> Unit,
    onDeleteFile: (String) -> Unit,
    onAttachLocalFile: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val ext = LocalWebForgeColors.current
    var newFilePath by remember { mutableStateOf("styles/custom.css") }
    var renamingAsset by remember { mutableStateOf<String?>(null) }
    var renameInput by remember { mutableStateOf("") }

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
            Text(strings.fileManagerTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newFilePath,
                    onValueChange = { newFilePath = it },
                    label = { Text("path/file.css | .js | .html") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        if (newFilePath.isNotBlank()) {
                            onCreateFile(newFilePath)
                            newFilePath = ""
                        }
                    },
                    modifier = Modifier.testTag("create_file_button"),
                ) {
                    Icon(Icons.Rounded.Add, "Create", tint = ext.success)
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedGlowButton(
                text = strings.uploadHtmlOrZip,
                icon = Icons.Rounded.AttachFile,
                onClick = onAttachLocalFile,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Surface(
                onClick = {
                    onSelectFile(null)
                    onDismiss()
                },
                shape = RoundedCornerShape(14.dp),
                color = if (selectedPath == null) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, if (selectedPath == null) ext.accent else ext.cardBorder),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Description, null, tint = ext.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("index.html (Entry)", fontWeight = FontWeight.Bold)
                }
            }

            assets.forEach { asset ->
                val isSelected = selectedPath == asset.path
                Surface(
                    onClick = {
                        if (asset.isText) {
                            onSelectFile(asset.path)
                            onDismiss()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.background,
                    border = BorderStroke(1.dp, if (isSelected) ext.accent else ext.cardBorder),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(asset.path, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("${asset.mimeType} • ${asset.sizeBytes} B", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            renamingAsset = asset.path
                            renameInput = asset.path
                        }) {
                            Icon(Icons.Rounded.DriveFileRenameOutline, "Rename", tint = ext.accent, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { onDeleteFile(asset.path) }) {
                            Icon(Icons.Rounded.DeleteOutline, "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (renamingAsset != null) {
        AlertDialog(
            onDismissRequest = { renamingAsset = null },
            title = { Text("Rename / Move") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = renamingAsset
                    if (target != null) onRenameFile(target, renameInput)
                    renamingAsset = null
                }) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = { renamingAsset = null }) { Text(strings.cancel) }
            },
        )
    }
}

@Composable
fun UrlGithubImportDialog(
    onImportUrl: (String) -> Unit,
    onImportGitHub: (owner: String, repo: String, branch: String, token: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val ext = LocalWebForgeColors.current
    var mode by remember { mutableStateOf(0) } // 0 = URL, 1 = GitHub
    var urlInput by remember { mutableStateOf("https://example.com") }
    var owner by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("main") }
    var ghToken by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
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
                    Text(strings.importFromUrl, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, strings.close)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == 0,
                        onClick = { mode = 0 },
                        label = { Text("Web URL") },
                    )
                    FilterChip(
                        selected = mode == 1,
                        onClick = { mode = 1 },
                        label = { Text("GitHub Repo") },
                    )
                }

                Spacer(Modifier.height(10.dp))
                if (mode == 0) {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("https://...") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("import_url_input"),
                    )
                    Spacer(Modifier.height(14.dp))
                    GradientButton(
                        text = strings.importFromUrl,
                        icon = Icons.Rounded.CloudDownload,
                        onClick = {
                            onImportUrl(urlInput)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = owner,
                        onValueChange = { owner = it },
                        label = { Text("GitHub Owner / Username") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = repo,
                        onValueChange = { repo = it },
                        label = { Text("Repository Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = branch,
                            onValueChange = { branch = it },
                            label = { Text("Branch") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = ghToken,
                            onValueChange = { ghToken = it },
                            label = { Text("Token (Optional)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    GradientButton(
                        text = strings.importFromGithub,
                        icon = Icons.Rounded.CloudDownload,
                        onClick = {
                            onImportGitHub(owner, repo, branch.ifBlank { "main" }, ghToken)
                            onDismiss()
                        },
                        enabled = owner.isNotBlank() && repo.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiAiStudioSheet(
    initialPrompt: String?,
    initialMobileAppMode: Boolean = true,
    currentCode: String,
    aiTaskState: AiTaskState,
    chatHistory: List<AiChatMessage>,
    isOffline: Boolean,
    isRtl: Boolean,
    onGenerateSite: (prompt: String, mobileAppMode: Boolean) -> Unit,
    onQuickAction: (AiQuickAction) -> Unit,
    onSendChat: (String) -> Unit,
    onApplyProposedCode: (String) -> Unit,
    onApplyAndPreview: (String) -> Unit,
    onApplyAndBuildApk: (String) -> Unit,
    onRetryLastAction: () -> Unit,
    onDiscardTask: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val ext = LocalWebForgeColors.current
    var sitePrompt by remember(initialPrompt) { mutableStateOf(initialPrompt.orEmpty()) }
    var isMobileAppMode by remember(initialMobileAppMode) { mutableStateOf(initialMobileAppMode) }
    var chatInput by remember { mutableStateOf("") }

    val ideaPresets = remember(isRtl) {
        if (isRtl) {
            listOf(
                "متجر إلكتروني" to "تطبيق متجر إلكتروني متكامل بالعربية مع أقسام المنتجات، سلة تسوق تحفظ محلياً، البحث، وإتمام الطلب",
                "تطبيق مهام To-Do" to "تطبيق إدارة المهام اليومية والأولويات مع فئات وإحصائيات إنجاز وحفظ تلقائي localStorage",
                "آلة حاسبة علمية" to "تطبيق آلة حاسبة علمية حديثة مع سجل العمليات الحسابية ووضع داكن وأزرار سلسة",
                "لعبة ذكاء X-O" to "لعبة إكس أو وتحدي الذاكرة بتصميم نيون جذاب مع نظام نقاط ومؤثرات صوتية برمجية",
                "تطبيق اختبارات Quiz" to "تطبيق اختبارات ثقافية وبرمجية تفاعلي مع مؤقت عد تنازلي وحساب النتيجة والرتبة",
                "قائمة مطعم + واتساب" to "تطبيق منيو مطعم وكافيه فاخر مع سلة طلبات وإرسال الفاتورة مباشرة عبر واتساب",
                "محفظة أعمال" to "موقع بورتفوليو شخصي احترافي مع معرض مشاريع ونموذج تواصل وإحصائيات",
                "لوحة تحكم SaaS" to "لوحة تحكم تحليلات ومبيعات تفاعلية مع رسوم بيانية SVG وجداول ومقاييس أداء",
            )
        } else {
            listOf(
                "E-Commerce App" to "Complete mobile e-commerce app with product catalog, search, localStorage cart, and checkout modal",
                "Task Manager" to "Interactive To-Do & habit tracker mobile app with tabs, priority filters, stats, and localStorage sync",
                "Calculator App" to "Sleek scientific calculator mobile app with calculation history drawer and haptic-style visual feedback",
                "Puzzle Game" to "Modern neon Tic-Tac-Toe & Memory Match mini-game app with scoreboard and difficulty modes",
                "Quiz Challenge" to "Interactive trivia quiz app with countdown timer, progress bar, and score breakdown",
                "Restaurant Menu" to "Digital restaurant menu app with food categories, cart drawer, and WhatsApp order summary",
                "Portfolio Site" to "Modern responsive developer portfolio website with animated hero, project gallery, and contact form",
                "SaaS Dashboard" to "Interactive analytics dashboard web-app with SVG charts, KPI cards, and dark/light toggle",
            )
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = ext.accent)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isRtl) "استوديو Gemini لصناعة تطبيقات ومواقع HTML" else strings.aiStudioTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (isRtl) "اكتب فكرتك وسيبني Gemini كود HTML5 تفاعلي جاهز للمعاينة والتحويل إلى APK أو موقع" else "Describe your app or site — Gemini builds live interactive HTML5 ready for preview or APK",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            if (isOffline) {
                Text(strings.offlineDeployBlocked, color = ext.warning, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }

            // Build Mode Selector: Interactive HTML5 App vs Responsive Website
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = isMobileAppMode,
                    onClick = { isMobileAppMode = true },
                    leadingIcon = { Icon(Icons.Rounded.PhoneAndroid, null, modifier = Modifier.size(16.dp)) },
                    label = { Text(if (isRtl) "تطبيق جوال HTML5" else "Mobile HTML5 App") },
                    modifier = Modifier.testTag("ai_mode_app_chip"),
                )
                FilterChip(
                    selected = !isMobileAppMode,
                    onClick = { isMobileAppMode = false },
                    leadingIcon = { Icon(Icons.Rounded.Language, null, modifier = Modifier.size(16.dp)) },
                    label = { Text(if (isRtl) "موقع ويب متكامل" else "Responsive Website") },
                    modifier = Modifier.testTag("ai_mode_website_chip"),
                )
            }

            Spacer(Modifier.height(6.dp))

            // 1-Tap Prompt Presets
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ideaPresets.forEach { (title, fullPrompt) ->
                    FilterChip(
                        selected = sitePrompt == fullPrompt,
                        onClick = { sitePrompt = fullPrompt },
                        label = { Text(title) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Prompt-to-App / Website Generator
            OutlinedTextField(
                value = sitePrompt,
                onValueChange = { sitePrompt = it },
                label = { Text(strings.aiGenerateHint) },
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("ai_site_prompt_input"),
            )
            Spacer(Modifier.height(8.dp))
            GradientButton(
                text = if (isRtl) {
                    if (isMobileAppMode) "صناعة التطبيق الآن بذكاء Gemini ✨" else "إنشاء الموقع الآن بذكاء Gemini ✨"
                } else {
                    if (isMobileAppMode) "Build HTML5 App with Gemini ✨" else "Build Website with Gemini ✨"
                },
                icon = Icons.Rounded.AutoAwesome,
                enabled = !isOffline && sitePrompt.isNotBlank() && aiTaskState !is AiTaskState.Loading && aiTaskState !is AiTaskState.Streaming,
                onClick = { onGenerateSite(sitePrompt, isMobileAppMode) },
                modifier = Modifier.fillMaxWidth(),
                testTag = "ai_generate_site_button",
            )

            Spacer(Modifier.height(14.dp))

            // 8 Quick AI Actions
            Text(
                text = if (isRtl) "أوامر ذكية سريعة على الكود الحالي" else "Quick AI Code Actions",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val actions = listOf(
                    AiQuickAction.FIX_BUGS to (if (isRtl) "إصلاح الأخطاء" else "Fix Bugs"),
                    AiQuickAction.MAKE_RESPONSIVE to (if (isRtl) "تحسين التجاوب" else "Responsive"),
                    AiQuickAction.CONVERT_RTL_ARABIC to (if (isRtl) "تحويل لعربي RTL" else "Arabic RTL"),
                    AiQuickAction.ADD_DARK_MODE to (if (isRtl) "إضافة الوضع الداكن" else "Dark Mode"),
                    AiQuickAction.TRANSLATE_EN_AR to (if (isRtl) "ترجمة الصفحة" else "Translate"),
                    AiQuickAction.OPTIMIZE_SEO_SPEED to (if (isRtl) "تحسين SEO والسرعة" else "SEO & Speed"),
                    AiQuickAction.EXPLAIN_CODE to (if (isRtl) "شرح الكود" else "Explain Code"),
                    AiQuickAction.SUGGEST_BRAND to (if (isRtl) "اقتراح اسم وألوان" else "Brand & Colors"),
                )
                actions.forEach { (action, label) ->
                    FilterChip(
                        selected = false,
                        onClick = { if (!isOffline) onQuickAction(action) },
                        label = { Text(label) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // AI Task Output & Instant Preview / APK Pipeline
            when (aiTaskState) {
                AiTaskState.Idle -> Unit
                AiTaskState.Loading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = ext.accent)
                        Spacer(Modifier.width(10.dp))
                        Text(if (isRtl) "Gemini يقوم بكتابة التطبيق..." else "Gemini is coding...")
                    }
                }
                is AiTaskState.Streaming -> {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            border = BorderStroke(1.dp, ext.accent),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
                        ) {
                            Text(
                                text = aiTaskState.text.takeLast(1800),
                                style = TextStyle(fontFamily = MonoFontFamily, fontSize = 11.sp, color = ext.accent),
                                modifier = Modifier.verticalScroll(rememberScrollState()).padding(10.dp),
                            )
                        }
                    }
                }
                is AiTaskState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("ai_error_card"),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = aiTaskState.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedGlowButton(
                                    text = if (isRtl) "إعادة المحاولة" else "Retry",
                                    icon = Icons.Rounded.Refresh,
                                    onClick = onRetryLastAction,
                                    modifier = Modifier.weight(1f),
                                    testTag = "ai_retry_button",
                                )
                                TextButton(onClick = onDiscardTask) {
                                    Text(strings.close)
                                }
                            }
                        }
                    }
                }
                is AiTaskState.Success -> {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        border = BorderStroke(1.dp, ext.success),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(aiTaskState.text, style = MaterialTheme.typography.bodySmall, color = ext.success, fontWeight = FontWeight.SemiBold)
                            val proposed = aiTaskState.proposedCode
                            if (proposed != null) {
                                Spacer(Modifier.height(8.dp))
                                DiffPreviewBox(oldCode = currentCode, newCode = proposed)
                                Spacer(Modifier.height(10.dp))
                                GradientButton(
                                    text = if (isRtl) "تشغيل ومعاينة التطبيق فوراً ▶" else "Apply & Live Preview ▶",
                                    icon = Icons.Rounded.PlayArrow,
                                    brush = MintCtaBrush,
                                    contentColor = Color(0xFF0C0A20),
                                    onClick = { onApplyAndPreview(proposed) },
                                    modifier = Modifier.fillMaxWidth(),
                                    testTag = "ai_apply_preview_button",
                                )
                                Spacer(Modifier.height(8.dp))
                                GradientButton(
                                    text = if (isRtl) "اعتماد وتحويل إلى تطبيق أندرويد APK" else "Apply & Build Android APK",
                                    icon = Icons.Rounded.Android,
                                    onClick = { onApplyAndBuildApk(proposed) },
                                    modifier = Modifier.fillMaxWidth(),
                                    testTag = "ai_apply_apk_button",
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedGlowButton(
                                        text = strings.aiApplyDiff,
                                        icon = Icons.Rounded.Check,
                                        onClick = { onApplyProposedCode(proposed) },
                                        modifier = Modifier.weight(1f),
                                        testTag = "ai_apply_diff_button",
                                    )
                                    OutlinedGlowButton(
                                        text = strings.aiDiscardDiff,
                                        icon = Icons.Rounded.Close,
                                        onClick = onDiscardTask,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Interactive Project AI Chat History
            if (chatHistory.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                chatHistory.takeLast(6).forEach { msg ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (msg.isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = (if (msg.isUser) "You: " else "Gemini: ") + msg.text,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (msg.proposedCode != null && !msg.isApplied) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { onApplyAndPreview(msg.proposedCode) }) {
                                        Text(if (isRtl) "معاينة ▶" else "Preview ▶", color = ext.success)
                                    }
                                    TextButton(onClick = { onApplyProposedCode(msg.proposedCode) }) {
                                        Text(strings.aiApplyDiff, color = ext.accent)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = chatInput,
                    onValueChange = { chatInput = it },
                    label = { Text(if (isRtl) "اطلب ميزة جديدة أو تعديلاً من Gemini..." else "Ask Gemini or request a new feature...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        if (chatInput.isNotBlank() && !isOffline) {
                            onSendChat(chatInput)
                            chatInput = ""
                        }
                    },
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, "Send", tint = ext.accent)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DiffPreviewBox(oldCode: String, newCode: String) {
    val diff = remember(oldCode, newCode) {
        CodeLinterAndAudit.computeLineDiff(oldCode, newCode).take(60)
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(
            color = Color(0xFF0A091B),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 170.dp),
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(8.dp)) {
                diff.forEach { line ->
                    val prefix = when (line.type) {
                        DiffType.ADDED -> "+ "
                        DiffType.REMOVED -> "- "
                        DiffType.UNCHANGED -> "  "
                    }
                    val color = when (line.type) {
                        DiffType.ADDED -> Color(0xFF34E0A1)
                        DiffType.REMOVED -> Color(0xFFFF5C7C)
                        DiffType.UNCHANGED -> Color(0xFFA8AED8)
                    }
                    Text(
                        text = prefix + line.text,
                        style = TextStyle(fontFamily = MonoFontFamily, fontSize = 10.5.sp, color = color),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
