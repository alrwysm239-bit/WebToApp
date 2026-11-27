package com.example.domain

import android.util.Base64
import com.example.data.model.AuditFinding
import com.example.data.model.AuditReport
import com.example.data.model.CodeIssue
import com.example.data.model.DiffLine
import com.example.data.model.DiffType
import com.example.data.model.ProjectAsset
import java.util.Locale

object CodeLinterAndAudit {

    fun expandEmmet(abbreviation: String): String {
        val clean = abbreviation.trim()
        if (clean == "!" || clean.equals("html:5", ignoreCase = true)) {
            return HtmlToolkit.defaultStarterHtml(isArabic = true)
        }
        val parts = clean.split(">").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return ""

        fun buildSegment(segment: String): Pair<String, String> {
            val siblings = segment.split("+").map { it.trim() }.filter { it.isNotEmpty() }
            if (siblings.size > 1) {
                val combined = siblings.joinToString("\n") { s ->
                    val (open, close) = buildSegment(s)
                    "$open$close"
                }
                return combined to ""
            }
            val repeatSplit = segment.split("*")
            val base = repeatSplit[0]
            val count = repeatSplit.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 10) ?: 1

            val idMatch = Regex("#([a-zA-Z0-9_-]+)").find(base)?.groupValues?.get(1)
            val classes = Regex("\\.([a-zA-Z0-9_-]+)").findAll(base).map { it.groupValues[1] }.toList()
            val tag = base.substringBefore('#').substringBefore('.').ifBlank { "div" }

            val attrs = buildString {
                if (idMatch != null) append(" id=\"$idMatch\"")
                if (classes.isNotEmpty()) append(" class=\"${classes.joinToString(" ")}\"")
            }
            if (count > 1) {
                val multi = (1..count).joinToString("\n") { idx -> "<$tag$attrs>Item $idx</$tag>" }
                return multi to ""
            }
            return "<$tag$attrs>" to "</$tag>"
        }

        if (parts.size == 1) {
            val (o, c) = buildSegment(parts.first())
            return "$o$c"
        }

        var inner = ""
        for (i in parts.indices.reversed()) {
            val (open, close) = buildSegment(parts[i])
            inner = if (close.isEmpty()) {
                open
            } else if (inner.isEmpty()) {
                "$open$close"
            } else {
                "$open\n  ${inner.replace("\n", "\n  ")}\n$close"
            }
        }
        return inner
    }

    fun detectCodeIssues(html: String): List<CodeIssue> {
        val sample = if (html.length > 120_000) html.substring(0, 120_000) else html
        val issues = mutableListOf<CodeIssue>()
        if (!sample.contains("<!doctype", ignoreCase = true)) {
            issues.add(
                CodeIssue(
                    line = 1,
                    severity = "WARN",
                    messageAr = "ينقص إعلان <!DOCTYPE html> في بداية الملف",
                    messageEn = "Missing <!DOCTYPE html> declaration",
                    fixType = "ADD_DOCTYPE",
                ),
            )
        }
        if (!sample.contains("name=\"viewport\"", ignoreCase = true) &&
            !sample.contains("name='viewport'", ignoreCase = true)
        ) {
            issues.add(
                CodeIssue(
                    line = 1,
                    severity = "WARN",
                    messageAr = "لا يوجد وسم viewport للتجاوب مع الجوال",
                    messageEn = "Missing mobile viewport meta tag",
                    fixType = "ADD_SEO",
                ),
            )
        }
        if (!sample.contains("<title", ignoreCase = true)) {
            issues.add(
                CodeIssue(
                    line = 1,
                    severity = "WARN",
                    messageAr = "الصفحة بدون عنوان <title>",
                    messageEn = "Missing <title> tag",
                    fixType = "ADD_SEO",
                ),
            )
        }
        val imgMissingAlt = Regex("""<img\b([^>]*)>""", RegexOption.IGNORE_CASE)
            .findAll(sample)
            .firstOrNull { !it.groupValues[1].contains("alt=", ignoreCase = true) }
        if (imgMissingAlt != null) {
            val line = sample.substring(0, imgMissingAlt.range.first).count { it == '\n' } + 1
            issues.add(
                CodeIssue(
                    line = line,
                    severity = "WARN",
                    messageAr = "صورة بدون نص بديل alt (مهم للوصول والسيو)",
                    messageEn = "<img> missing alt attribute for accessibility",
                    fixType = "ADD_ALT",
                ),
            )
        }
        val httpResource = Regex("""(?:src|href)\s*=\s*["']http://[^"']+["']""", RegexOption.IGNORE_CASE).find(sample)
        if (httpResource != null) {
            val line = sample.substring(0, httpResource.range.first).count { it == '\n' } + 1
            issues.add(
                CodeIssue(
                    line = line,
                    severity = "ERROR",
                    messageAr = "رابط غير آمن http:// قد يُحظر في المتصفح",
                    messageEn = "Insecure http:// resource may cause mixed-content block",
                    fixType = "UPGRADE_HTTPS",
                ),
            )
        }
        if (html.contains("<script", ignoreCase = true) && !html.contains("</script>", ignoreCase = true)) {
            issues.add(
                CodeIssue(
                    line = html.count { it == '\n' } + 1,
                    severity = "ERROR",
                    messageAr = "وسم <script> مفتوح بدون إغلاق </script>",
                    messageEn = "Unclosed <script> tag detected",
                    fixType = "CLOSE_SCRIPT",
                ),
            )
        }
        return issues
    }

    fun applyCodeFix(html: String, fixType: String, appName: String): String = when (fixType) {
        "ADD_DOCTYPE" -> if (html.contains("<!doctype", ignoreCase = true)) html else "<!DOCTYPE html>\n$html"
        "ADD_SEO" -> HtmlToolkit.injectViewportSeoAndIcon(html, appName, "Built with WebForge", "#5B3DF5", true)
        "ADD_ALT" -> Regex("""<img\b([^>]*)>""", RegexOption.IGNORE_CASE)
            .replace(html) { m ->
                if (m.groupValues[1].contains("alt=", ignoreCase = true)) m.value
                else "<img alt=\"image\"${m.groupValues[1]}>"
            }
        "UPGRADE_HTTPS" -> html.replace("http://", "https://", ignoreCase = true)
        "CLOSE_SCRIPT" -> if (html.contains("</script>", ignoreCase = true)) html else "$html\n</script>"
        else -> html
    }

    fun bundleSingleSelfContainedHtml(
        html: String,
        assets: List<ProjectAsset>,
    ): String {
        var result = html
        val assetMap = assets.associateBy { it.path.lowercase(Locale.US).trimStart('/') }

        // Inline <link rel="stylesheet" href="...">
        result = Regex("""<link\b[^>]*href=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE).replace(result) { m ->
            val fullTag = m.value
            val href = m.groupValues[1].removePrefix("./").trimStart('/').lowercase(Locale.US)
            if (fullTag.contains("stylesheet", ignoreCase = true)) {
                val cssAsset = assetMap[href]
                if (cssAsset?.textContent != null) {
                    "<style>\n${cssAsset.textContent}\n</style>"
                } else fullTag
            } else fullTag
        }

        // Inline <script src="..."></script>
        result = Regex("""<script\b[^>]*src=["']([^"']+)["'][^>]*>\s*</script>""", RegexOption.IGNORE_CASE).replace(result) { m ->
            val src = m.groupValues[1].removePrefix("./").trimStart('/').lowercase(Locale.US)
            val jsAsset = assetMap[src]
            if (jsAsset?.textContent != null) {
                "<script>\n${jsAsset.textContent}\n</script>"
            } else m.value
        }

        // Inline binary images <img src="..."> -> data:<mime>;base64,...
        result = Regex("""(\bsrc\s*=\s*["'])([^"']+)(["'])""", RegexOption.IGNORE_CASE).replace(result) { m ->
            val rawPath = m.groupValues[2].removePrefix("./").trimStart('/').lowercase(Locale.US)
            val asset = assetMap[rawPath]
            if (asset != null) {
                val b64 = asset.base64Content ?: asset.textContent?.let {
                    Base64.encodeToString(it.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                }
                if (b64 != null) "${m.groupValues[1]}data:${asset.mimeType};base64,$b64${m.groupValues[3]}"
                else m.value
            } else m.value
        }

        return result
    }

    fun injectTrackingAndCookieBanner(
        html: String,
        analyticsId: String,
        adsensePubId: String,
        includeCookieBanner: Boolean,
    ): String {
        var result = html
        val headSnippets = StringBuilder()

        if (analyticsId.isNotBlank() && !result.contains(analyticsId)) {
            headSnippets.appendLine(
                """
                <!-- Google Analytics 4 -->
                <script async src="https://www.googletagmanager.com/gtag/js?id=$analyticsId"></script>
                <script>
                  window.dataLayer = window.dataLayer || [];
                  function gtag(){dataLayer.push(arguments);}
                  gtag('js', new Date());
                  gtag('config', '$analyticsId');
                </script>
                """.trimIndent(),
            )
        }
        if (adsensePubId.isNotBlank() && !result.contains(adsensePubId)) {
            headSnippets.appendLine(
                """<script async src="https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=$adsensePubId" crossorigin="anonymous"></script>""",
            )
        }
        if (headSnippets.isNotBlank()) {
            val headClose = Regex("""</head>""", RegexOption.IGNORE_CASE).find(result)
            result = if (headClose != null) {
                result.replaceRange(headClose.range, "$headSnippets\n${headClose.value}")
            } else {
                "$headSnippets\n$result"
            }
        }

        if (includeCookieBanner && !result.contains("wf-cookie-banner")) {
            val banner = """
                <div id="wf-cookie-banner" style="position:fixed;bottom:16px;left:16px;right:16px;z-index:9999;background:#1C1840;color:#F4F6FF;border:1px solid #5B3DF5;border-radius:16px;padding:14px 18px;display:flex;align-items:center;justify-content:space-between;gap:12px;box-shadow:0 12px 32px rgba(0,0,0,0.45);font-family:system-ui,sans-serif;font-size:13px;">
                  <span>نستخدم ملفات تعريف الارتباط لتحسين تجربتك / We use cookies to enhance your experience.</span>
                  <button onclick="localStorage.setItem('wf_cookies','1');document.getElementById('wf-cookie-banner').remove();" style="background:linear-gradient(90deg,#5B3DF5,#00C6FF);color:#fff;border:none;border-radius:10px;padding:8px 16px;font-weight:700;cursor:pointer;">موافق / OK</button>
                </div>
                <script>if(localStorage.getItem('wf_cookies')==='1'){var b=document.getElementById('wf-cookie-banner');if(b)b.remove();}</script>
            """.trimIndent()
            val bodyClose = Regex("""</body>""", RegexOption.IGNORE_CASE).find(result)
            result = if (bodyClose != null) {
                result.replaceRange(bodyClose.range, "$banner\n${bodyClose.value}")
            } else {
                "$result\n$banner"
            }
        }
        return result
    }

    fun auditWebsite(html: String, assets: List<ProjectAsset>): AuditReport {
        val findings = mutableListOf<AuditFinding>()
        val assetSet = assets.map { it.path.lowercase(Locale.US).trimStart('/') }.toSet()

        // 1. Performance
        var perf = 100
        val totalBytes = html.toByteArray().size + assets.sumOf { it.sizeBytes }
        val isLightweight = totalBytes < 500_000
        if (!isLightweight) perf -= 20
        findings += AuditFinding(
            category = "Performance",
            passed = isLightweight,
            titleAr = "حجم الحزمة الإجمالي (${totalBytes / 1024} KB)",
            titleEn = "Total Bundle Weight (${totalBytes / 1024} KB)",
            detailAr = if (isLightweight) "الحجم ممتاز وسريع التحميل" else "يُنصح بضغط الصور وتقليل حجم الملفات",
            detailEn = if (isLightweight) "Fast load payload" else "Compress images and minify assets",
        )
        val hasRenderBlockingCheck = !html.contains("<script src=", ignoreCase = true) ||
            html.contains("defer", ignoreCase = true) || html.contains("async", ignoreCase = true)
        if (!hasRenderBlockingCheck) perf -= 15
        findings += AuditFinding(
            category = "Performance",
            passed = hasRenderBlockingCheck,
            titleAr = "السكربتات غير المعطِّلة للرسم",
            titleEn = "Non-blocking Scripts",
            detailAr = if (hasRenderBlockingCheck) "لا توجد سكربتات خارجية تعطل التحميل" else "أضف defer أو async لوسوم <script src>",
            detailEn = if (hasRenderBlockingCheck) "No render-blocking external scripts" else "Add defer or async to external script tags",
        )

        // 2. SEO
        var seo = 100
        val hasTitle = html.contains("<title", ignoreCase = true)
        val hasDesc = html.contains("name=\"description\"", ignoreCase = true)
        val hasViewport = html.contains("name=\"viewport\"", ignoreCase = true)
        val hasOg = html.contains("property=\"og:", ignoreCase = true)
        if (!hasTitle) seo -= 25
        if (!hasDesc) seo -= 25
        if (!hasViewport) seo -= 25
        if (!hasOg) seo -= 15
        findings += AuditFinding(
            category = "SEO",
            passed = hasTitle && hasDesc && hasViewport,
            titleAr = "وسوم السيو الأساسية وOpen Graph",
            titleEn = "Core SEO & Open Graph Meta",
            detailAr = if (hasTitle && hasDesc && hasViewport) "مكتملة وجاهزة لمحركات البحث" else "استخدم أداة 'SEO وأيقونة' لإكمالها تلقائياً",
            detailEn = if (hasTitle && hasDesc && hasViewport) "Complete for search engines" else "Run Auto SEO tool to inject missing tags",
        )

        // 3. Accessibility
        var a11y = 100
        val hasLang = html.contains("lang=", ignoreCase = true)
        val imgsMissingAlt = Regex("""<img\b(?![^>]*\balt=)[^>]*>""", RegexOption.IGNORE_CASE).findAll(html).count()
        if (!hasLang) a11y -= 20
        if (imgsMissingAlt > 0) a11y -= (imgsMissingAlt * 15).coerceAtMost(40)
        findings += AuditFinding(
            category = "Accessibility",
            passed = hasLang && imgsMissingAlt == 0,
            titleAr = "إمكانية الوصول (lang و alt)",
            titleEn = "Accessibility (lang & img alt)",
            detailAr = if (hasLang && imgsMissingAlt == 0) "متوافق مع قارئات الشاشة" else "أضف سمة lang و alt للصور ($imgsMissingAlt صورة)",
            detailEn = if (hasLang && imgsMissingAlt == 0) "Screen-reader friendly" else "Add lang attribute and alt text ($imgsMissingAlt missing)",
        )

        // 4. Security
        var sec = 100
        val hasHttp = Regex("""(?:src|href)\s*=\s*["']http://""", RegexOption.IGNORE_CASE).containsMatchIn(html)
        val hasEval = html.contains("eval(", ignoreCase = true)
        if (hasHttp) sec -= 30
        if (hasEval) sec -= 25
        findings += AuditFinding(
            category = "Security",
            passed = !hasHttp && !hasEval,
            titleAr = "فحص الأمان (HTTPS و eval)",
            titleEn = "Security Hygiene (HTTPS & eval)",
            detailAr = if (!hasHttp && !hasEval) "لا توجد روابط غير مشفرة أو دوال خطرة" else "استبدل http:// بـ https:// وتجنب eval()",
            detailEn = if (!hasHttp && !hasEval) "No mixed content or unsafe eval()" else "Upgrade http:// links and avoid eval()",
        )

        // 5. Broken Relative Links
        var linksScore = 100
        val broken = mutableListOf<String>()
        Regex("""(?:src|href)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).findAll(html).forEach { m ->
            val ref = m.groupValues[1].trim()
            if (!ref.startsWith("http") && !ref.startsWith("data:") && !ref.startsWith("#") &&
                !ref.startsWith("mailto:") && !ref.startsWith("tel:") &&
                !ref.endsWith("favicon.png") && !ref.endsWith("manifest.webmanifest") &&
                !ref.endsWith("icon-192.png")
            ) {
                val normalized = ref.removePrefix("./").trimStart('/').lowercase(Locale.US)
                if (normalized !in assetSet && assets.isNotEmpty()) {
                    broken.add(ref)
                }
            }
        }
        if (broken.isNotEmpty()) linksScore = (100 - broken.size * 20).coerceAtLeast(20)
        findings += AuditFinding(
            category = "Links",
            passed = broken.isEmpty(),
            titleAr = "سلامة الروابط المحلية",
            titleEn = "Local Asset References",
            detailAr = if (broken.isEmpty()) "جميع الروابط سليمة" else "روابط مفقودة محتملة: ${broken.take(3).joinToString()}",
            detailEn = if (broken.isEmpty()) "All local references resolved" else "Missing assets: ${broken.take(3).joinToString()}",
        )

        val overall = ((perf + seo + a11y + sec + linksScore) / 5.0).toInt().coerceIn(0, 100)
        val grade = when {
            overall >= 92 -> "A+"
            overall >= 85 -> "A"
            overall >= 75 -> "B"
            overall >= 60 -> "C"
            else -> "D"
        }
        return AuditReport(
            overallScore = overall,
            grade = grade,
            performanceScore = perf.coerceIn(0, 100),
            seoScore = seo.coerceIn(0, 100),
            accessibilityScore = a11y.coerceIn(0, 100),
            securityScore = sec.coerceIn(0, 100),
            linksScore = linksScore.coerceIn(0, 100),
            findings = findings,
        )
    }

    fun computeLineDiff(oldText: String, newText: String): List<DiffLine> {
        val oldLines = oldText.lines()
        val newLines = newText.lines()
        val oldSet = oldLines.toSet()
        val newSet = newLines.toSet()
        val result = mutableListOf<DiffLine>()

        var i = 0
        var j = 0
        while (i < oldLines.size || j < newLines.size) {
            val o = oldLines.getOrNull(i)
            val n = newLines.getOrNull(j)
            when {
                o != null && n != null && o == n -> {
                    result.add(DiffLine(DiffType.UNCHANGED, o))
                    i++
                    j++
                }
                o != null && o !in newSet -> {
                    result.add(DiffLine(DiffType.REMOVED, o))
                    i++
                }
                n != null && n !in oldSet -> {
                    result.add(DiffLine(DiffType.ADDED, n))
                    j++
                }
                else -> {
                    if (o != null) {
                        result.add(DiffLine(DiffType.REMOVED, o))
                        i++
                    }
                    if (n != null) {
                        result.add(DiffLine(DiffType.ADDED, n))
                        j++
                    }
                }
            }
            if (result.size >= 400) break
        }
        return result
    }
}
