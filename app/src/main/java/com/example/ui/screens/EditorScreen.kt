package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.model.DeviceFrame
import com.example.ui.WebForgeUiState
import com.example.ui.components.CodeEditorCard
import com.example.ui.components.GlowingFrameCard
import com.example.ui.components.GradientButton
import com.example.ui.components.MintCtaBrush
import com.example.ui.components.OutlinedGlowButton
import com.example.ui.components.PrimaryCtaBrush
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.LocalWebForgeColors

private fun readSystemClipboardText(context: Context): String {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return ""
    val clip = cm.primaryClip ?: return ""
    if (clip.itemCount == 0) return ""
    val item = clip.getItemAt(0)
    return item.coerceToText(context)?.toString().orEmpty()
}

@Composable
fun EditorScreen(
    state: WebForgeUiState,
    onProjectNameChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSelectAssetTab: (String?) -> Unit,
    onOpenFileManager: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenUrlGithubImport: () -> Unit,
    onOpenAiStudio: () -> Unit,
    onPasteHtml: (String) -> Unit,
    onUploadClick: () -> Unit,
    onConvertClick: () -> Unit,
    onPublishFreeClick: () -> Unit,
    onFormatClick: () -> Unit,
    onMinifyClick: () -> Unit,
    onFixLinksClick: () -> Unit,
    onAutoSeoClick: () -> Unit,
    onApplyLintFix: (String) -> Unit,
    onExpandEmmet: (String) -> Unit,
    onLoadStarter: () -> Unit,
    onClearCode: () -> Unit,
    onSaveProject: () -> Unit,
    onConsoleLog: (String, String) -> Unit,
    onCopiedFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val ext = LocalWebForgeColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    var splitViewEnabled by remember { mutableStateOf(false) }
    var showPasteModal by remember { mutableStateOf(false) }
    var pasteModalText by remember { mutableStateOf("") }

    fun handleSmartPaste() {
        coroutineScope.launch {
            val sysText = withContext(Dispatchers.IO) {
                readSystemClipboardText(context)
            }.ifBlank { clipboard.getText()?.text.orEmpty() }
            if (sysText.isNotBlank()) {
                onPasteHtml(sysText)
            } else {
                pasteModalText = ""
                showPasteModal = true
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth()) {
            // Clean single-line title + compact action bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.newProject,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    var lineCount by remember { mutableStateOf(1) }
                    androidx.compose.runtime.LaunchedEffect(state.currentEditorCode) {
                        val s = state.currentEditorCode
                        lineCount = withContext(Dispatchers.Default) {
                            var lines = 1
                            for (i in 0 until s.length) {
                                if (s[i] == '\n') lines++
                            }
                            lines
                        }
                    }
                    Text(
                        text = "$lineCount ${strings.linesCount} • ${state.assets.size + 1} ${strings.assetsCountLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { splitViewEnabled = !splitViewEnabled },
                        modifier = Modifier.size(40.dp).testTag("toggle_split_view_button"),
                    ) {
                        Icon(Icons.Rounded.Splitscreen, strings.splitView, tint = if (splitViewEnabled) ext.success else ext.accent)
                    }
                    IconButton(
                        onClick = onOpenFileManager,
                        modifier = Modifier.size(40.dp).testTag("open_file_manager_button"),
                    ) {
                        Icon(Icons.Rounded.FolderOpen, strings.fileManagerTitle, tint = ext.accent)
                    }
                    IconButton(
                        onClick = onOpenAiStudio,
                        modifier = Modifier.size(40.dp).testTag("editor_open_ai_button"),
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, strings.aiStudioTitle, tint = ext.softViolet)
                    }
                    IconButton(
                        onClick = onLoadStarter,
                        modifier = Modifier.size(40.dp).testTag("starter_template_button"),
                    ) {
                        Icon(Icons.Rounded.RestartAlt, strings.starterTemplate, tint = ext.accent)
                    }
                    IconButton(
                        onClick = onSaveProject,
                        modifier = Modifier.size(40.dp).testTag("quick_save_button"),
                    ) {
                        Icon(Icons.Rounded.BookmarkAdd, strings.saveProjectNow, tint = ext.success)
                    }
                }
            }

            AnimatedVisibility(visible = state.isImporting) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    color = ext.accent,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = state.projectName,
                onValueChange = onProjectNameChange,
                label = { Text(strings.projectNameLabel) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = ext.cardBorder,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
                trailingIcon = {
                    Row {
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(state.currentEditorCode))
                                onCopiedFeedback()
                            },
                            modifier = Modifier.testTag("copy_code_button"),
                        ) {
                            Icon(Icons.Rounded.ContentCopy, strings.copyCode, tint = ext.accent, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = onClearCode,
                            modifier = Modifier.testTag("clear_code_button"),
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, strings.clearCode, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("project_name_input"),
            )

            // Multi-file tabs
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.selectedAssetPath == null,
                    onClick = { onSelectAssetTab(null) },
                    label = { Text("index.html") },
                    leadingIcon = { Icon(Icons.Rounded.Code, null, modifier = Modifier.size(15.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        selectedLabelColor = ext.accent,
                    ),
                )
                state.assets.forEach { asset ->
                    if (asset.isText) {
                        FilterChip(
                            selected = state.selectedAssetPath == asset.path,
                            onClick = { onSelectAssetTab(asset.path) },
                            label = { Text(asset.path) },
                            leadingIcon = { Icon(Icons.Rounded.Description, null, modifier = Modifier.size(15.dp)) },
                        )
                    } else {
                        AssistChip(
                            onClick = onOpenFileManager,
                            label = { Text(asset.path) },
                            leadingIcon = { Icon(Icons.Rounded.FolderZip, null, modifier = Modifier.size(15.dp)) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Syntax-Highlighted Code Editor with full-box focus/keyboard, Paste, Emmet & Lint
            CodeEditorCard(
                code = state.currentEditorCode,
                onCodeChange = onCodeChange,
                fontSizeSp = state.settings.editorFontSize,
                editorTheme = state.settings.editorTheme,
                canUndo = state.canUndo,
                canRedo = state.canRedo,
                onUndo = onUndo,
                onRedo = onRedo,
                codeIssues = state.codeIssues,
                onApplyFix = onApplyLintFix,
                onExpandEmmet = onExpandEmmet,
                onPasteFromToolbar = ::handleSmartPaste,
                isRtlUi = state.settings.language.isRtl,
            )

            AnimatedVisibility(visible = splitViewEnabled) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        text = strings.livePreview,
                        style = MaterialTheme.typography.labelMedium,
                        color = ext.success,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    GlowingFrameCard(
                        modifier = Modifier.fillMaxWidth().height(230.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp))) {
                            LiveHtmlWebView(
                                html = state.htmlContent,
                                assets = state.assets,
                                deviceFrame = DeviceFrame.PHONE,
                                loadKey = "split-${state.previewRevision}-${state.htmlContent.hashCode()}",
                                isOffline = state.isEffectiveOffline,
                                onConsoleLog = onConsoleLog,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = strings.toolsTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToolChip(strings.toolFormat, Icons.Rounded.AutoFixHigh, ext.accent, onFormatClick, "tool_format_button")
                ToolChip(strings.toolMinify, Icons.Rounded.Compress, ext.softViolet, onMinifyClick, "tool_minify_button")
                ToolChip(strings.toolFixLinks, Icons.Rounded.Link, ext.success, onFixLinksClick, "tool_fix_links_button")
                ToolChip(strings.toolAutoSeo, Icons.Rounded.Language, ext.warning, onAutoSeoClick, "tool_auto_seo_button")
                ToolChip(strings.templatesTitle, Icons.Rounded.DashboardCustomize, ext.accent, onOpenTemplates, "editor_templates_button")
                ToolChip(strings.importFromUrl, Icons.Rounded.CloudDownload, ext.softViolet, onOpenUrlGithubImport, "editor_url_import_button")
            }

            Spacer(modifier = Modifier.height(14.dp))

            GradientButton(
                text = strings.pasteHtmlCode,
                icon = Icons.Rounded.ContentPaste,
                brush = PrimaryCtaBrush,
                testTag = "paste_html_button",
                onClick = ::handleSmartPaste,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedGlowButton(
                text = strings.uploadHtmlOrZip,
                icon = Icons.Rounded.UploadFile,
                testTag = "upload_html_zip_button",
                onClick = onUploadClick,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(10.dp))

            GradientButton(
                text = strings.convertAndPreview,
                icon = Icons.Rounded.PlayArrow,
                brush = MintCtaBrush,
                contentColor = Color(0xFF0C0A20),
                testTag = "convert_preview_button",
                onClick = onConvertClick,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedGlowButton(
                text = strings.freePublishButton,
                icon = Icons.Rounded.Public,
                testTag = "editor_free_publish_button",
                onClick = onPublishFreeClick,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showPasteModal) {
        AlertDialog(
            onDismissRequest = { showPasteModal = false },
            title = { Text(strings.pasteInBoxTitle, fontWeight = FontWeight.Bold) },
            text = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 260.dp),
                        factory = { ctx ->
                            android.widget.EditText(ctx).apply {
                                hint = "<html>...</html>"
                                textSize = 12f
                                setTextColor(android.graphics.Color.WHITE)
                                setHintTextColor(android.graphics.Color.GRAY)
                                setBackgroundColor(android.graphics.Color.parseColor("#0C0A20"))
                                setPadding(24, 24, 24, 24)
                                gravity = android.view.Gravity.TOP or android.view.Gravity.START
                                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                                    android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                                addTextChangedListener(object : android.text.TextWatcher {
                                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                                        pasteModalText = s?.toString().orEmpty()
                                    }
                                    override fun afterTextChanged(s: android.text.Editable?) = Unit
                                })
                                post {
                                    requestFocus()
                                    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                                    imm?.showSoftInput(this, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                                }
                            }
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pasteModalText.isNotBlank()) {
                        onPasteHtml(pasteModalText)
                    }
                    showPasteModal = false
                }) {
                    Text(strings.save, color = ext.success, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteModal = false }) {
                    Text(strings.cancel)
                }
            },
        )
    }
}

@Composable
private fun ToolChip(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    testTag: String,
) {
    val ext = LocalWebForgeColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, ext.cardBorder),
        modifier = Modifier.testTag(testTag),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
