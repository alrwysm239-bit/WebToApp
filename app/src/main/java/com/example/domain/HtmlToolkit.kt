package com.example.domain

import java.util.Locale

data class MinifyResult(
    val code: String,
    val originalBytes: Int,
    val minifiedBytes: Int,
) {
    val savedPercent: Int
        get() = if (originalBytes <= 0) 0
        else (((originalBytes - minifiedBytes).coerceAtLeast(0) * 100.0) / originalBytes).toInt()
}

object HtmlToolkit {

    private val voidTags = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr"
    )

    fun extractTitle(html: String): String? {
        val match = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)
        return match?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun slugify(input: String): String {
        val cleaned = input.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return cleaned.ifEmpty { "webforge-site" }
    }

    fun toSafePackageSegment(input: String): String {
        val cleaned = input.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]"), "")
        return if (cleaned.isEmpty() || cleaned.first().isDigit()) "app$cleaned" else cleaned
    }

    fun formatHtml(rawHtml: String): String {
        val trimmed = rawHtml.trim()
        if (trimmed.isEmpty()) return ""

        val tokenRegex = Regex(
            """(<!--[\s\S]*?-->|<!DOCTYPE[^>]*>|<script\b[^>]*>[\s\S]*?</script>|<style\b[^>]*>[\s\S]*?</style>|<pre\b[^>]*>[\s\S]*?</pre>|</?[a-zA-Z][^>]*>)""",
            RegexOption.IGNORE_CASE,
        )

        val output = StringBuilder()
        var indent = 0
        var lastIndex = 0

        fun appendIndented(line: String, level: Int) {
            if (line.isBlank()) return
            repeat(level.coerceAtLeast(0)) { output.append("  ") }
            output.append(line.trim()).append('\n')
        }

        for (match in tokenRegex.findAll(trimmed)) {
            val textBetween = trimmed.substring(lastIndex, match.range.first)
            if (textBetween.isNotBlank()) {
                val collapsed = textBetween.replace(Regex("""\s+"""), " ").trim()
                appendIndented(collapsed, indent)
            }

            val token = match.value.trim()
            val lower = token.lowercase(Locale.US)

            when {
                lower.startsWith("<!") || lower.startsWith("<?") -> {
                    appendIndented(token, indent)
                }
                lower.startsWith("</") -> {
                    indent = (indent - 1).coerceAtLeast(0)
                    appendIndented(token, indent)
                }
                lower.startsWith("<script") || lower.startsWith("<style") || lower.startsWith("<pre") -> {
                    appendIndented(token, indent)
                }
                else -> {
                    val tagName = Regex("""^<\s*([a-zA-Z0-9:-]+)""").find(lower)?.groupValues?.get(1) ?: ""
                    val isSelfClosing = token.endsWith("/>") || tagName in voidTags
                    appendIndented(token, indent)
                    if (!isSelfClosing) {
                        indent++
                    }
                }
            }
            lastIndex = match.range.last + 1
        }

        if (lastIndex < trimmed.length) {
            val tail = trimmed.substring(lastIndex).replace(Regex("""\s+"""), " ").trim()
            if (tail.isNotEmpty()) appendIndented(tail, indent)
        }

        return output.toString().trimEnd()
    }

    fun minifyHtml(rawHtml: String): MinifyResult {
        val originalBytes = rawHtml.toByteArray(Charsets.UTF_8).size
        if (rawHtml.isBlank()) return MinifyResult("", originalBytes, 0)

        var result = rawHtml
        // Preserve <pre> and <textarea> blocks using placeholders
        val preserved = mutableListOf<String>()
        result = Regex("""<(pre|textarea)\b[^>]*>[\s\S]*?</\1>""", RegexOption.IGNORE_CASE).replace(result) { m ->
            preserved.add(m.value)
            "__WF_PRESERVE_${preserved.lastIndex}__"
        }

        // Minify inline <style> blocks
        result = Regex("""(<style\b[^>]*>)([\s\S]*?)(</style>)""", RegexOption.IGNORE_CASE).replace(result) { m ->
            "${m.groupValues[1]}${minifyCss(m.groupValues[2])}${m.groupValues[3]}"
        }

        // Minify inline <script> blocks (without src)
        result = Regex("""(<script\b(?![^>]*\bsrc=)[^>]*>)([\s\S]*?)(</script>)""", RegexOption.IGNORE_CASE).replace(result) { m ->
            "${m.groupValues[1]}${minifyJs(m.groupValues[2])}${m.groupValues[3]}"
        }

        // Strip HTML comments (keep conditional comments)
        result = Regex("""<!--(?!\[if)[\s\S]*?-->""").replace(result, "")
        // Collapse inter-tag whitespace
        result = Regex(""">\s+<""").replace(result, "><")
        // Collapse consecutive spaces/newlines
        result = Regex("""\s{2,}""").replace(result, " ").trim()

        // Restore preserved blocks
        preserved.forEachIndexed { idx, block ->
            result = result.replace("__WF_PRESERVE_${idx}__", block)
        }

        val minifiedBytes = result.toByteArray(Charsets.UTF_8).size
        return MinifyResult(result, originalBytes, minifiedBytes)
    }

    fun minifyCss(rawCss: String): String {
        return rawCss
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""\s*([{}:;,>+~])\s*"""), "$1")
            .replace(";}", "}")
            .trim()
    }

    fun minifyJs(rawJs: String): String {
        val lines = rawJs.lines().map { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("//")) "" else trimmed
        }.filter { it.isNotEmpty() }
        return lines.joinToString("\n")
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""[ \t]{2,}"""), " ")
            .trim()
    }

    fun fixRelativeLinks(rawHtml: String, assetPaths: List<String>): Pair<String, Int> {
        var fixCount = 0
        val normalizedAssetMap = assetPaths.associateBy { it.lowercase(Locale.US).trimStart('/') }

        val attrRegex = Regex("""(\b(?:href|src|action|poster)\s*=\s*)(["'])([^"']+)\2""", RegexOption.IGNORE_CASE)
        var updated = attrRegex.replace(rawHtml) { match ->
            val prefix = match.groupValues[1]
            val quote = match.groupValues[2]
            val url = match.groupValues[3].trim()

            if (url.startsWith("http://", ignoreCase = true) ||
                url.startsWith("https://", ignoreCase = true) ||
                url.startsWith("data:", ignoreCase = true) ||
                url.startsWith("mailto:", ignoreCase = true) ||
                url.startsWith("tel:", ignoreCase = true) ||
                url.startsWith("#") ||
                url.startsWith("javascript:", ignoreCase = true)
            ) {
                match.value
            } else {
                var clean = url
                    .replace("\\", "/")
                    .removePrefix("./")
                    .trimStart('/')
                while (clean.startsWith("../")) {
                    clean = clean.removePrefix("../")
                }
                val matchedAsset = normalizedAssetMap[clean.lowercase(Locale.US)]
                    ?: assetPaths.firstOrNull { it.endsWith("/$clean", ignoreCase = true) }
                    ?: clean
                if (matchedAsset != url) {
                    fixCount++
                }
                "$prefix$quote$matchedAsset$quote"
            }
        }

        val cssUrlRegex = Regex("""url\(\s*(["']?)([^)"']+)\1\s*\)""", RegexOption.IGNORE_CASE)
        updated = cssUrlRegex.replace(updated) { match ->
            val quote = match.groupValues[1]
            val url = match.groupValues[2].trim()
            if (url.startsWith("http") || url.startsWith("data:") || url.startsWith("#")) {
                match.value
            } else {
                val clean = url.replace("\\", "/").removePrefix("./").trimStart('/')
                val matched = normalizedAssetMap[clean.lowercase(Locale.US)] ?: clean
                if (matched != url) fixCount++
                "url($quote$matched$quote)"
            }
        }

        return updated to fixCount
    }

    fun injectViewportSeoAndIcon(
        rawHtml: String,
        title: String,
        description: String,
        themeColorHex: String,
        hasFavicon: Boolean = true,
    ): String {
        var html = rawHtml.trim()
        if (!html.contains("<!doctype", ignoreCase = true)) {
            html = "<!DOCTYPE html>\n$html"
        }
        if (!html.contains("<html", ignoreCase = true)) {
            html = "<!DOCTYPE html>\n<html lang=\"ar\" dir=\"rtl\">\n<head>\n</head>\n<body>\n$rawHtml\n</body>\n</html>"
        } else if (!html.contains("<head", ignoreCase = true)) {
            html = Regex("""(<html[^>]*>)""", RegexOption.IGNORE_CASE)
                .replaceFirst(html, "$1\n<head>\n</head>")
        }

        val tagsToInject = buildString {
            if (!html.contains("charset", ignoreCase = true)) {
                appendLine("  <meta charset=\"UTF-8\">")
            }
            if (!html.contains("name=\"viewport\"", ignoreCase = true) &&
                !html.contains("name='viewport'", ignoreCase = true)
            ) {
                appendLine("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, viewport-fit=cover\">")
            }
            if (!html.contains("<title", ignoreCase = true)) {
                appendLine("  <title>${escapeHtmlAttr(title)}</title>")
            }
            if (!html.contains("name=\"description\"", ignoreCase = true)) {
                appendLine("  <meta name=\"description\" content=\"${escapeHtmlAttr(description)}\">")
            }
            if (!html.contains("property=\"og:title\"", ignoreCase = true)) {
                appendLine("  <meta property=\"og:title\" content=\"${escapeHtmlAttr(title)}\">")
            }
            if (!html.contains("property=\"og:description\"", ignoreCase = true)) {
                appendLine("  <meta property=\"og:description\" content=\"${escapeHtmlAttr(description)}\">")
            }
            if (!html.contains("name=\"theme-color\"", ignoreCase = true)) {
                appendLine("  <meta name=\"theme-color\" content=\"${escapeHtmlAttr(themeColorHex)}\">")
            }
            if (hasFavicon && !html.contains("rel=\"icon\"", ignoreCase = true)) {
                appendLine("  <link rel=\"icon\" type=\"image/png\" href=\"favicon.png\">")
            }
        }

        if (tagsToInject.isBlank()) return html

        val headMatch = Regex("""<head[^>]*>""", RegexOption.IGNORE_CASE).find(html)
        return if (headMatch != null) {
            html.replaceRange(headMatch.range, "${headMatch.value}\n$tagsToInject")
        } else {
            "$tagsToInject\n$html"
        }
    }

    fun injectPwaLinks(rawHtml: String): String {
        var html = rawHtml
        if (!html.contains("manifest.webmanifest", ignoreCase = true) &&
            !html.contains("manifest.json", ignoreCase = true)
        ) {
            val manifestTag = "  <link rel=\"manifest\" href=\"manifest.webmanifest\">\n  <link rel=\"apple-touch-icon\" href=\"icon-192.png\">"
            val closeHead = Regex("""</head>""", RegexOption.IGNORE_CASE).find(html)
            html = if (closeHead != null) {
                html.replaceRange(closeHead.range, "$manifestTag\n${closeHead.value}")
            } else {
                "$manifestTag\n$html"
            }
        }
        if (!html.contains("serviceWorker.register", ignoreCase = true)) {
            val swScript = """
  <script>
    if ('serviceWorker' in navigator) {
      window.addEventListener('load', function() {
        navigator.serviceWorker.register('./sw.js').catch(function() {});
      });
    }
  </script>""".trimIndent()
            val closeBody = Regex("""</body>""", RegexOption.IGNORE_CASE).find(html)
            html = if (closeBody != null) {
                html.replaceRange(closeBody.range, "$swScript\n${closeBody.value}")
            } else {
                "$html\n$swScript"
            }
        }
        return html
    }

    private fun escapeHtmlAttr(value: String): String =
        value.replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    fun defaultStarterHtml(isArabic: Boolean): String {
        return if (isArabic) {
            """<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>موقعي التفاعلي — WebForge</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: system-ui, -apple-system, sans-serif;
      background: linear-gradient(160deg, #0C0A20 0%, #1C1840 100%);
      color: #F4F6FF;
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 24px;
    }
    .card {
      width: 100%;
      max-width: 440px;
      background: rgba(28, 24, 64, 0.92);
      border: 1.5px solid #5B3DF5;
      border-radius: 24px;
      padding: 28px;
      box-shadow: 0 20px 50px rgba(91, 61, 245, 0.25);
      text-align: center;
    }
    .badge {
      display: inline-block;
      padding: 6px 14px;
      border-radius: 999px;
      background: rgba(0, 198, 255, 0.16);
      color: #00C6FF;
      font-size: 13px;
      font-weight: 700;
      margin-bottom: 14px;
    }
    h1 { font-size: 24px; margin-bottom: 10px; }
    p { color: #A8AED8; font-size: 14px; line-height: 1.7; margin-bottom: 22px; }
    .btn {
      width: 100%;
      border: none;
      border-radius: 14px;
      padding: 14px 20px;
      font-size: 15px;
      font-weight: 700;
      color: #fff;
      cursor: pointer;
      background: linear-gradient(90deg, #5B3DF5, #00C6FF);
      transition: transform 0.15s ease;
    }
    .btn:active { transform: scale(0.97); }
    .counter {
      margin-top: 16px;
      color: #34E0A1;
      font-weight: 700;
      font-size: 15px;
    }
  </style>
</head>
<body>
  <main class="card">
    <span class="badge">✨ مبني بواسطة WebForge</span>
    <h1>أهلاً بك في موقعك الجديد</h1>
    <p>عدّل هذا الكود أو الصق صفحتك، ثم عاينها مباشرة وصدّرها كموقع أو تطبيق أندرويد و PWA.</p>
    <button class="btn" onclick="increment()">جرّب التفاعل الفوري ⚡</button>
    <div id="count" class="counter">عدد النقرات: 0</div>
  </main>
  <script>
    let clicks = 0;
    function increment() {
      clicks++;
      document.getElementById('count').textContent = 'عدد النقرات: ' + clicks;
      console.log('Button clicked:', clicks);
    }
  </script>
</body>
</html>"""
        } else {
            """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>My Interactive Site — WebForge</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: system-ui, -apple-system, sans-serif;
      background: linear-gradient(160deg, #0C0A20 0%, #1C1840 100%);
      color: #F4F6FF;
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 24px;
    }
    .card {
      width: 100%;
      max-width: 440px;
      background: rgba(28, 24, 64, 0.92);
      border: 1.5px solid #5B3DF5;
      border-radius: 24px;
      padding: 28px;
      box-shadow: 0 20px 50px rgba(91, 61, 245, 0.25);
      text-align: center;
    }
    .badge {
      display: inline-block;
      padding: 6px 14px;
      border-radius: 999px;
      background: rgba(0, 198, 255, 0.16);
      color: #00C6FF;
      font-size: 13px;
      font-weight: 700;
      margin-bottom: 14px;
    }
    h1 { font-size: 24px; margin-bottom: 10px; }
    p { color: #A8AED8; font-size: 14px; line-height: 1.7; margin-bottom: 22px; }
    .btn {
      width: 100%;
      border: none;
      border-radius: 14px;
      padding: 14px 20px;
      font-size: 15px;
      font-weight: 700;
      color: #fff;
      cursor: pointer;
      background: linear-gradient(90deg, #5B3DF5, #00C6FF);
    }
    .counter {
      margin-top: 16px;
      color: #34E0A1;
      font-weight: 700;
      font-size: 15px;
    }
  </style>
</head>
<body>
  <main class="card">
    <span class="badge">✨ Built with WebForge</span>
    <h1>Welcome to Your Web App</h1>
    <p>Edit this HTML, preview across devices, and export as a Website, PWA, or Android WebView App.</p>
    <button class="btn" onclick="increment()">Test Live Interaction ⚡</button>
    <div id="count" class="counter">Clicks: 0</div>
  </main>
  <script>
    let clicks = 0;
    function increment() {
      clicks++;
      document.getElementById('count').textContent = 'Clicks: ' + clicks;
      console.log('Button clicked:', clicks);
    }
  </script>
</body>
</html>"""
        }
    }
}
