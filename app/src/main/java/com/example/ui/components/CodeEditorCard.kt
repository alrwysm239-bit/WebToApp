package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.FindReplace
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CodeIssue
import com.example.data.model.EditorTheme
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.LocalWebForgeColors
import com.example.ui.theme.MonoFontFamily

class CodeSyntaxTransformation(
    private val tagColor: Color,
    private val attrNameColor: Color,
    private val attrValueColor: Color,
    private val commentColor: Color,
    private val keywordColor: Color,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val highlighted = buildAnnotatedString {
            append(raw)

            Regex(""""[^"\n]*"|'[^'\n]*'""").findAll(raw).forEach { m ->
                addStyle(SpanStyle(color = attrValueColor), m.range.first, m.range.last + 1)
            }

            Regex("""</?[a-zA-Z0-9:-]+|/?>""").findAll(raw).forEach { m ->
                addStyle(
                    SpanStyle(color = tagColor, fontWeight = FontWeight.Medium),
                    m.range.first,
                    m.range.last + 1,
                )
            }

            Regex("""\s([a-zA-Z0-9:-]+)(?==)""").findAll(raw).forEach { m ->
                val group = m.groups[1] ?: return@forEach
                addStyle(SpanStyle(color = attrNameColor), group.range.first, group.range.last + 1)
            }

            Regex("""\b(function|const|let|var|return|if|else|document|window|console|flex|grid|linear-gradient|important)\b""")
                .findAll(raw)
                .forEach { m ->
                    addStyle(SpanStyle(color = keywordColor), m.range.first, m.range.last + 1)
                }

            Regex("""<!--[\s\S]*?-->|/\*[\s\S]*?\*/""").findAll(raw).forEach { m ->
                addStyle(SpanStyle(color = commentColor), m.range.first, m.range.last + 1)
            }
        }
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}

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
    val lineCount = remember(code) { code.count { it == '\n' } + 1 }
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    var showSearchReplace by remember { mutableStateOf(false) }
    var showEmmetBar by remember { mutableStateOf(false) }
    var showLintPanel by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var emmetQuery by remember { mutableStateOf("div.card>h2+p") }

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

    val syntaxTransformation = remember(palette) {
        CodeSyntaxTransformation(
            tagColor = palette.tag,
            attrNameColor = palette.attr,
            attrValueColor = palette.value,
            commentColor = palette.comment,
            keywordColor = palette.keyword,
        )
    }

    val effectiveTextColor = if (editorTheme == EditorTheme.WEBFORGE_MIDNIGHT && !ext.isDark) {
        MaterialTheme.colorScheme.onSurface
    } else palette.text

    val codeTextStyle = TextStyle(
        fontFamily = MonoFontFamily,
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp + 6).sp,
        color = effectiveTextColor,
        textDirection = TextDirection.Ltr,
    )

    Column(modifier = modifier) {
        // Editor Micro-Toolbar (Paste, Undo, Redo, Find & Replace, Emmet, Lint)
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
                        val matchCount = remember(code, searchQuery) {
                            if (searchQuery.isEmpty()) 0 else code.windowed(searchQuery.length).count { it.equals(searchQuery, true) }
                        }
                        Text(
                            text = "$matchCount matches",
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.accent,
                            modifier = Modifier.align(Alignment.CenterVertically).padding(end = 8.dp),
                        )
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

        // Code editor is always LTR; full-area tap focuses BasicTextField and opens soft keyboard reliably
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            GlowingFrameCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 230.dp, max = 330.dp),
                containerColor = palette.bg,
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 230.dp, max = 330.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        },
                ) {
                    val minEditableWidth = (maxWidth - 48.dp).coerceAtLeast(220.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 230.dp)
                            .verticalScroll(verticalScroll),
                    ) {
                        Column(
                            modifier = Modifier
                                .background(palette.gutter)
                                .defaultMinSize(minHeight = 230.dp)
                                .padding(horizontal = 10.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.End,
                        ) {
                            for (line in 1..lineCount) {
                                Text(
                                    text = line.toString(),
                                    style = codeTextStyle.copy(
                                        color = palette.comment,
                                        fontSize = (fontSizeSp - 1).coerceAtLeast(9).sp,
                                    ),
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 230.dp)
                                .horizontalScroll(horizontalScroll)
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                        ) {
                            BasicTextField(
                                value = code,
                                onValueChange = onCodeChange,
                                textStyle = codeTextStyle,
                                cursorBrush = SolidColor(ext.accent),
                                visualTransformation = syntaxTransformation,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    autoCorrectEnabled = false,
                                    imeAction = ImeAction.Default,
                                ),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier
                                            .widthIn(min = minEditableWidth)
                                            .defaultMinSize(minHeight = 206.dp),
                                    ) {
                                        if (code.isEmpty()) {
                                            Text(
                                                text = "<!-- Paste or write HTML / CSS / JS here -->",
                                                style = codeTextStyle.copy(color = palette.comment),
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                                modifier = Modifier
                                    .widthIn(min = minEditableWidth)
                                    .defaultMinSize(minHeight = 206.dp)
                                    .focusRequester(focusRequester)
                                    .testTag("html_code_editor"),
                            )
                        }
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
