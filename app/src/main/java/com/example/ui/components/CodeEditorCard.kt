package com.example.ui.components

import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.Layout
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.WrapText
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.FindReplace
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.CodeIssue
import com.example.data.model.EditorTheme
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.LocalWebForgeColors
import com.example.ui.theme.MonoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val SYNTAX_HIGHLIGHT_MAX_CHARS = 12_000
private const val HUGE_FILE_PREVIEW_THRESHOLD = 90_000
private const val HUGE_FILE_PREVIEW_SLICE = 45_000

private data class EditorPalette(
    val bg: Color,
    val gutter: Color,
    val text: Color,
    val tag: Color,
    val attr: Color,
    val value: Color,
    val comment: Color,
    val keyword: Color,
)

private data class SyntaxSpanRange(val start: Int, val end: Int, val color: Int)

private class NativeCodeHolder(
    val vScroll: ScrollView,
    val hScroll: HorizontalScrollView,
    val editText: EditText,
    val gutterView: TextView,
    var suppressWatcher: Boolean = false,
    var lastKnownText: String = "",
    var lastGutterLines: Int = -1,
    var isInitialized: Boolean = false,
    val mainHandler: Handler = Handler(Looper.getMainLooper()),
    var pendingEmit: Runnable? = null,
)

@Composable
fun CodeEditorCard(
    code: String,
    onCodeChange: (String) -> Unit,
    fontSizeSp: Int = 13,
    editorTheme: EditorTheme = EditorTheme.WEBFORGE_MIDNIGHT,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    codeIssues: List<CodeIssue> = emptyList(),
    onApplyFix: (String) -> Unit = {},
    onExpandEmmet: (String) -> Unit = {},
    onPasteFromToolbar: () -> Unit = {},
    isRtlUi: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val ext = LocalWebForgeColors.current
    val strings = LocalStrings.current

    val isHugeFile = code.length > HUGE_FILE_PREVIEW_THRESHOLD
    val isFastMode = code.length > SYNTAX_HIGHLIGHT_MAX_CHARS
    var forceFullHugeEdit by remember(code.length > HUGE_FILE_PREVIEW_THRESHOLD) { mutableStateOf(false) }

    val displayedCode = remember(code, isHugeFile, forceFullHugeEdit) {
        if (isHugeFile && !forceFullHugeEdit) {
            code.take(HUGE_FILE_PREVIEW_SLICE)
        } else {
            code
        }
    }
    val isReadOnlyPreviewSlice = isHugeFile && !forceFullHugeEdit

    val autoWrapSuggested = remember(displayedCode.length) {
        displayedCode.length > SYNTAX_HIGHLIGHT_MAX_CHARS
    }
    var wordWrap by remember(autoWrapSuggested) { mutableStateOf(autoWrapSuggested) }
    var isEditorLoading by remember { mutableStateOf(code.length > 8_000) }

    var showSearchReplace by remember { mutableStateOf(false) }
    var showEmmetBar by remember { mutableStateOf(false) }
    var showLintPanel by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var emmetQuery by remember { mutableStateOf("div.card>h2+p") }
    var holderRef by remember { mutableStateOf<NativeCodeHolder?>(null) }

    val latestOnCodeChange by rememberUpdatedState(onCodeChange)
    val latestIsReadOnlySlice by rememberUpdatedState(isReadOnlyPreviewSlice)

    val palette = remember(editorTheme, ext) {
        when (editorTheme) {
            EditorTheme.WEBFORGE_MIDNIGHT -> EditorPalette(
                bg = ext.codeBackground,
                gutter = ext.codeGutter,
                text = Color(0xFFF4F6FF),
                tag = ext.syntaxTag,
                attr = ext.syntaxAttrName,
                value = ext.syntaxAttrValue,
                comment = ext.syntaxComment,
                keyword = Color(0xFFFF7EB6),
            )
            EditorTheme.DRACULA_NEON -> EditorPalette(
                bg = Color(0xFF191A2E),
                gutter = Color(0xFF131424),
                text = Color(0xFFF8F8F2),
                tag = Color(0xFFFF79C6),
                attr = Color(0xFF50FA7B),
                value = Color(0xFFF1FA8C),
                comment = Color(0xFF6272A4),
                keyword = Color(0xFF8BE9FD),
            )
            EditorTheme.CYAN_MATRIX -> EditorPalette(
                bg = Color(0xFF06151B),
                gutter = Color(0xFF040E13),
                text = Color(0xFFD9FFF5),
                tag = Color(0xFF00E5FF),
                attr = Color(0xFF34E0A1),
                value = Color(0xFFA7FFEB),
                comment = Color(0xFF4B7B7E),
                keyword = Color(0xFF76FF03),
            )
            EditorTheme.SOLAR_LIGHT -> EditorPalette(
                bg = Color(0xFFFAF9F4),
                gutter = Color(0xFFEDEBE0),
                text = Color(0xFF18172B),
                tag = Color(0xFF0077B6),
                attr = Color(0xFF5B3DF5),
                value = Color(0xFF0B8A5B),
                comment = Color(0xFF8B8E9F),
                keyword = Color(0xFFD63384),
            )
        }
    }

    val effectiveTextColor = if (editorTheme == EditorTheme.WEBFORGE_MIDNIGHT && !ext.isDark) {
        MaterialTheme.colorScheme.onSurface
    } else palette.text

    // Non-blocking deferred text sync + gutter + optional lightweight syntax highlight
    LaunchedEffect(displayedCode, palette, holderRef, isReadOnlyPreviewSlice) {
        val holder = holderRef ?: return@LaunchedEffect

        // Yield 1 frame on initial enter so NavHost / tab transitions never stutter
        if (!holder.isInitialized) {
            delay(20)
            holder.isInitialized = true
        }

        if (holder.lastKnownText != displayedCode) {
            isEditorLoading = displayedCode.length > 15_000
            if (displayedCode.length > 15_000) delay(16)
            holder.suppressWatcher = true
            holder.lastKnownText = displayedCode
            holder.editText.setText(displayedCode)
            holder.editText.isEnabled = true
            holder.editText.isFocusable = !isReadOnlyPreviewSlice
            holder.editText.isFocusableInTouchMode = !isReadOnlyPreviewSlice
            holder.suppressWatcher = false
            isEditorLoading = false
        } else {
            isEditorLoading = false
        }

        val gutterUpdate = withContext(Dispatchers.Default) {
            var lines = 1
            for (i in 0 until displayedCode.length) {
                if (displayedCode[i] == '\n') lines++
            }
            if (holder.lastGutterLines == lines) null
            else {
                val capped = lines.coerceAtMost(1500)
                lines to buildString(capped * 4) {
                    for (i in 1..capped) {
                        append(i)
                        if (i < capped) append('\n')
                    }
                    if (lines > capped) append("\n...")
                }
            }
        }
        if (gutterUpdate != null) {
            holder.lastGutterLines = gutterUpdate.first
            holder.gutterView.text = gutterUpdate.second
        }

        val editable = holder.editText.text ?: return@LaunchedEffect
        if (displayedCode.isEmpty() || displayedCode.length > SYNTAX_HIGHLIGHT_MAX_CHARS) {
            val existing = editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)
            if (existing.isNotEmpty()) {
                existing.forEach { editable.removeSpan(it) }
            }
            return@LaunchedEffect
        }

        delay(160)
        val spans = withContext(Dispatchers.Default) {
            computeSyntaxSpans(displayedCode, palette)
        }
        val currentEditable = holder.editText.text ?: return@LaunchedEffect
        if (currentEditable.length == displayedCode.length) {
            currentEditable.getSpans(0, currentEditable.length, ForegroundColorSpan::class.java).forEach {
                currentEditable.removeSpan(it)
            }
            spans.forEach { sp ->
                if (sp.start >= 0 && sp.end <= currentEditable.length && sp.start < sp.end) {
                    currentEditable.setSpan(
                        ForegroundColorSpan(sp.color),
                        sp.start,
                        sp.end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            }
        }
    }

    Column(modifier = modifier) {
        // Editor Micro-Toolbar (Paste, Wrap, Fast Mode, Undo, Redo, Find & Replace, Emmet, Lint)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onPasteFromToolbar,
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, ext.accent.copy(alpha = 0.6f)),
                modifier = Modifier.testTag("editor_toolbar_paste"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Rounded.ContentPaste, null, tint = ext.accent, modifier = Modifier.size(15.dp))
                    Text(strings.pasteHtmlCode, style = MaterialTheme.typography.labelSmall, color = ext.accent, fontWeight = FontWeight.Bold)
                }
            }

            Surface(
                onClick = { wordWrap = !wordWrap },
                shape = RoundedCornerShape(10.dp),
                color = if (wordWrap) ext.accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (wordWrap) ext.accent else ext.cardBorder),
                modifier = Modifier.testTag("editor_toggle_wrap"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.WrapText, null, tint = if (wordWrap) ext.accent else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                    Text(strings.wrapLinesLabel, style = MaterialTheme.typography.labelSmall)
                }
            }

            if (isFastMode) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ext.success.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, ext.success.copy(alpha = 0.5f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Rounded.Speed, null, tint = ext.success, modifier = Modifier.size(14.dp))
                        Text(
                            text = "${(code.length / 1024).coerceAtLeast(1)} KB ⚡",
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.success,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            IconButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier.size(40.dp).testTag("editor_undo_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo) ext.accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(19.dp),
                )
            }
            IconButton(
                onClick = onRedo,
                enabled = canRedo,
                modifier = Modifier.size(40.dp).testTag("editor_redo_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo) ext.accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(19.dp),
                )
            }

            Surface(
                onClick = { showSearchReplace = !showSearchReplace },
                shape = RoundedCornerShape(10.dp),
                color = if (showSearchReplace) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, ext.cardBorder),
                modifier = Modifier.testTag("toggle_search_replace"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Rounded.FindReplace, null, tint = ext.accent, modifier = Modifier.size(15.dp))
                    Text("Find", style = MaterialTheme.typography.labelSmall)
                }
            }

            Surface(
                onClick = { showEmmetBar = !showEmmetBar },
                shape = RoundedCornerShape(10.dp),
                color = if (showEmmetBar) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, ext.cardBorder),
                modifier = Modifier.testTag("toggle_emmet_bar"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Rounded.Bolt, null, tint = ext.warning, modifier = Modifier.size(15.dp))
                    Text(strings.toolEmmet, style = MaterialTheme.typography.labelSmall)
                }
            }

            val lintColor = if (codeIssues.isEmpty()) ext.success else ext.warning
            Surface(
                onClick = { showLintPanel = !showLintPanel },
                shape = RoundedCornerShape(10.dp),
                color = lintColor.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, lintColor.copy(alpha = 0.5f)),
                modifier = Modifier.testTag("toggle_lint_panel"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Rounded.BugReport, null, tint = lintColor, modifier = Modifier.size(15.dp))
                    Text(
                        text = "${strings.toolLint} (${codeIssues.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = lintColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        AnimatedVisibility(visible = isHugeFile && !forceFullHugeEdit) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ext.warning.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, ext.warning.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (isRtlUi) {
                            "ملف ضخم (${code.length / 1024} KB) — وضع العرض السريع لمنع التعليق. المعاينة والبناء يستخدمان الكود كاملاً."
                        } else {
                            "Large file (${code.length / 1024} KB) — Fast Preview active for 0-lag. Preview & Builds use full code."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.warning,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { forceFullHugeEdit = true }) {
                        Text(if (isRtlUi) "تحرير كامل" else "Edit Full", color = ext.accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        AnimatedVisibility(visible = showSearchReplace) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, ext.cardBorder),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text(strings.searchPlaceholder) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = replaceQuery,
                            onValueChange = { replaceQuery = it },
                            label = { Text(strings.replacePlaceholder) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = {
                                if (searchQuery.isNotEmpty()) {
                                    onCodeChange(code.replace(searchQuery, replaceQuery, ignoreCase = false))
                                }
                            },
                        ) {
                            Text("Replace All")
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = showEmmetBar) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, ext.cardBorder),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = emmetQuery,
                        onValueChange = { emmetQuery = it },
                        label = { Text("Emmet (! , ul>li*3 , div.card>h2+p)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onExpandEmmet(emmetQuery) }) {
                        Icon(Icons.Rounded.Bolt, null, Modifier.size(16.dp), tint = ext.warning)
                        Spacer(Modifier.width(4.dp))
                        Text("Expand", color = ext.warning)
                    }
                }
            }
        }

        AnimatedVisibility(visible = showLintPanel && codeIssues.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, ext.warning.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    codeIssues.forEach { issue ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "L${issue.line}: ${if (isRtlUi) issue.messageAr else issue.messageEn}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (issue.severity == "ERROR") MaterialTheme.colorScheme.error else ext.warning,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { onApplyFix(issue.fixType) }) {
                                Icon(Icons.Rounded.AutoFixHigh, null, Modifier.size(14.dp), tint = ext.success)
                                Spacer(Modifier.width(4.dp))
                                Text("Fix", color = ext.success)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Hardware-accelerated non-blocking EditText container (0ms tab switch, instant keyboard)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            GlowingFrameCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(275.dp),
                containerColor = palette.bg,
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(275.dp)) {
                    val bgArgb = palette.bg.toArgb()
                    val gutterArgb = palette.gutter.toArgb()
                    val textArgb = effectiveTextColor.toArgb()
                    val commentArgb = palette.comment.toArgb()

                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(275.dp)
                            .testTag("html_code_editor"),
                        factory = { ctx ->
                            val density = ctx.resources.displayMetrics.density
                            fun dp(v: Int) = (v * density).toInt()

                            val vScroll = ScrollView(ctx).apply {
                                isFillViewport = true
                                setBackgroundColor(bgArgb)
                                isVerticalScrollBarEnabled = true
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                            }

                            val row = LinearLayout(ctx).apply {
                                orientation = LinearLayout.HORIZONTAL
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                )
                            }

                            val gutterView = TextView(ctx).apply {
                                setBackgroundColor(gutterArgb)
                                setTextColor(commentArgb)
                                typeface = Typeface.MONOSPACE
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, (fontSizeSp - 1).coerceAtLeast(9).toFloat())
                                gravity = Gravity.END
                                setPadding(dp(8), dp(12), dp(8), dp(12))
                                setLineSpacing(dp(4).toFloat(), 1f)
                                text = "1"
                                minWidth = dp(38)
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                            }

                            val hScroll = HorizontalScrollView(ctx).apply {
                                isFillViewport = true
                                isHorizontalScrollBarEnabled = !wordWrap
                                layoutParams = LinearLayout.LayoutParams(
                                    0,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    1f,
                                )
                            }

                            val editText = EditText(ctx).apply {
                                background = null
                                setTextColor(textArgb)
                                setHintTextColor(commentArgb)
                                hint = "<!-- Paste or write HTML / CSS / JS here -->"
                                typeface = Typeface.MONOSPACE
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp.toFloat())
                                setLineSpacing(dp(4).toFloat(), 1f)
                                gravity = Gravity.TOP or Gravity.START
                                textDirection = View.TEXT_DIRECTION_LTR
                                layoutDirection = View.LAYOUT_DIRECTION_LTR
                                setPadding(dp(12), dp(12), dp(16), dp(24))
                                minHeight = dp(265)
                                inputType = InputType.TYPE_CLASS_TEXT or
                                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                                imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
                                    hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                                }
                                setHorizontallyScrolling(!wordWrap)
                                isFocusable = !isReadOnlyPreviewSlice
                                isFocusableInTouchMode = !isReadOnlyPreviewSlice
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                )
                                setOnClickListener {
                                    if (!latestIsReadOnlySlice) {
                                        requestFocus()
                                        val imm = ctx.getSystemService(InputMethodManager::class.java)
                                        imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                                    }
                                }
                            }

                            val holder = NativeCodeHolder(
                                vScroll = vScroll,
                                hScroll = hScroll,
                                editText = editText,
                                gutterView = gutterView,
                            )
                            holderRef = holder

                            editText.addTextChangedListener(object : TextWatcher {
                                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                                override fun afterTextChanged(s: Editable?) {
                                    if (holder.suppressWatcher || latestIsReadOnlySlice) return
                                    holder.pendingEmit?.let { holder.mainHandler.removeCallbacks(it) }
                                    val emitTask = Runnable {
                                        val newStr = s?.toString().orEmpty()
                                        if (newStr != holder.lastKnownText) {
                                            holder.lastKnownText = newStr
                                            latestOnCodeChange(newStr)
                                        }
                                    }
                                    holder.pendingEmit = emitTask
                                    holder.mainHandler.postDelayed(emitTask, 90L)
                                }
                            })

                            hScroll.addView(editText)
                            row.addView(gutterView)
                            row.addView(hScroll)
                            vScroll.addView(row)
                            vScroll.tag = holder
                            vScroll
                        },
                        update = { vScroll ->
                            val holder = vScroll.tag as? NativeCodeHolder ?: return@AndroidView
                            vScroll.setBackgroundColor(bgArgb)
                            holder.gutterView.setBackgroundColor(gutterArgb)
                            holder.gutterView.setTextColor(commentArgb)
                            holder.gutterView.setTextSize(TypedValue.COMPLEX_UNIT_SP, (fontSizeSp - 1).coerceAtLeast(9).toFloat())
                            holder.editText.setTextColor(textArgb)
                            holder.editText.setHintTextColor(commentArgb)
                            holder.editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp.toFloat())
                            holder.editText.setHorizontallyScrolling(!wordWrap)
                            holder.hScroll.isHorizontalScrollBarEnabled = !wordWrap
                            holder.editText.isFocusable = !isReadOnlyPreviewSlice
                            holder.editText.isFocusableInTouchMode = !isReadOnlyPreviewSlice
                            if (wordWrap) {
                                val targetW = holder.hScroll.width
                                if (targetW > 0 && holder.editText.maxWidth != targetW) {
                                    holder.editText.maxWidth = targetW
                                }
                            } else if (holder.editText.maxWidth != Int.MAX_VALUE) {
                                holder.editText.maxWidth = Int.MAX_VALUE
                            }
                        },
                    )

                    if (isEditorLoading) {
                        LinearProgressIndicator(
                            color = ext.accent,
                            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick HTML snippet chips
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val snippets = listOf(
                    "<div>" to "\n<div class=\"box\"></div>",
                    "<style>" to "\n<style>\n  body { margin: 0; }\n</style>",
                    "<script>" to "\n<script>\n  console.log('ready');\n</script>",
                    "<button>" to "\n<button onclick=\"alert('Hi')\">Click</button>",
                    "<img/>" to "\n<img src=\"image.png\" alt=\"photo\" />",
                    "Emmet !" to null,
                )
                snippets.forEach { (label, insert) ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                if (insert != null) onCodeChange(code + insert)
                                else onExpandEmmet("!")
                            },
                    ) {
                        Text(
                            text = label,
                            style = TextStyle(
                                fontFamily = MonoFontFamily,
                                fontSize = 11.5.sp,
                                color = ext.accent,
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
    }
}

private fun computeSyntaxSpans(raw: String, palette: EditorPalette): List<SyntaxSpanRange> {
    val spans = ArrayList<SyntaxSpanRange>(256)
    val valueColor = palette.value.toArgb()
    val tagColor = palette.tag.toArgb()
    val attrColor = palette.attr.toArgb()
    val kwColor = palette.keyword.toArgb()
    val commentColor = palette.comment.toArgb()

    Regex(""""[^"\n]*"|'[^'\n]*'""").findAll(raw).forEach { m ->
        spans.add(SyntaxSpanRange(m.range.first, m.range.last + 1, valueColor))
    }
    Regex("""</?[a-zA-Z0-9:-]+|/?>""").findAll(raw).forEach { m ->
        spans.add(SyntaxSpanRange(m.range.first, m.range.last + 1, tagColor))
    }
    Regex("""\s([a-zA-Z0-9:-]+)(?==)""").findAll(raw).forEach { m ->
        val g = m.groups[1] ?: return@forEach
        spans.add(SyntaxSpanRange(g.range.first, g.range.last + 1, attrColor))
    }
    Regex("""\b(function|const|let|var|return|if|else|document|window|console|flex|grid|linear-gradient|important)\b""")
        .findAll(raw)
        .forEach { m ->
            spans.add(SyntaxSpanRange(m.range.first, m.range.last + 1, kwColor))
        }
    Regex("""<!--[\s\S]*?-->|/\*[\s\S]*?\*/""").findAll(raw).forEach { m ->
        spans.add(SyntaxSpanRange(m.range.first, m.range.last + 1, commentColor))
    }
    return spans
}
