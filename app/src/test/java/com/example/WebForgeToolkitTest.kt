package com.example

import com.example.data.model.AppExportConfig
import com.example.data.model.AppOrientation
import com.example.data.model.DiffType
import com.example.data.model.ProjectAsset
import com.example.domain.CodeLinterAndAudit
import com.example.domain.HtmlToolkit
import com.example.domain.QrCodeGenerator
import com.example.domain.TemplateCatalog
import com.example.domain.ZipArchiveManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebForgeToolkitTest {

    @Test
    fun `formatHtml formats nested tags with indentation`() {
        val raw = "<div><h1>Hello</h1><p>World</p></div>"
        val formatted = HtmlToolkit.formatHtml(raw)
        assertTrue(formatted.contains("  <h1>"))
        assertTrue(formatted.contains("  <p>"))
    }

    @Test
    fun `minifyHtml reduces size and strips comments`() {
        val raw = """
            <!-- comment -->
            <html>
              <head>
                <style>
                  body { color: red;  margin: 0; }
                </style>
              </head>
              <body>   <h1> Hi </h1>   </body>
            </html>
        """.trimIndent()
        val result = HtmlToolkit.minifyHtml(raw)
        assertTrue(result.minifiedBytes < result.originalBytes)
        assertTrue(!result.code.contains("<!-- comment -->"))
        assertTrue(result.code.contains("body{color:red;margin:0}"))
    }

    @Test
    fun `fixRelativeLinks normalizes dot-slash and parent paths`() {
        val raw = """<link rel="stylesheet" href="./css/style.css"><img src="../assets/logo.png">"""
        val (fixed, count) = HtmlToolkit.fixRelativeLinks(raw, listOf("css/style.css", "assets/logo.png"))
        assertEquals(2, count)
        assertTrue(fixed.contains("""href="css/style.css""""))
        assertTrue(fixed.contains("""src="assets/logo.png""""))
    }

    @Test
    fun `injectViewportSeoAndIcon adds missing meta tags`() {
        val raw = "<html><head></head><body><h1>Test</h1></body></html>"
        val enhanced = HtmlToolkit.injectViewportSeoAndIcon(
            rawHtml = raw,
            title = "My Site",
            description = "Site Desc",
            themeColorHex = "#5B3DF5",
            hasFavicon = true,
        )
        assertTrue(enhanced.contains("name=\"viewport\""))
        assertTrue(enhanced.contains("<title>My Site</title>"))
        assertTrue(enhanced.contains("name=\"description\""))
        assertTrue(enhanced.contains("rel=\"icon\""))
    }

    @Test
    fun `qrCodeGenerator produces valid square matrix`() {
        val matrix = QrCodeGenerator.encodeMatrix("https://webforge.app")
        assertTrue(matrix.size >= 21)
        assertEquals(matrix.size * matrix.size, matrix.bits.size)
    }

    @Test
    fun `buildPwaManifest includes app name color and orientation`() {
        val manifest = ZipArchiveManager.buildPwaManifest(
            config = AppExportConfig(
                appName = "ForgeApp",
                themeColorHex = "#00C6FF",
                orientation = AppOrientation.PORTRAIT,
            ),
            projectName = "ForgeApp",
        )
        assertTrue(manifest.contains("\"name\": \"ForgeApp\""))
        assertTrue(manifest.contains("\"theme_color\": \"#00C6FF\""))
        assertTrue(manifest.contains("\"orientation\": \"portrait\""))
    }

    @Test
    fun `templateCatalog contains at least 20 templates`() {
        assertTrue(TemplateCatalog.allTemplates.size >= 20)
        assertTrue(TemplateCatalog.allTemplates.all { it.html.contains("<html", ignoreCase = true) })
    }

    @Test
    fun `emmet expansion builds nested html and repeated items`() {
        val card = CodeLinterAndAudit.expandEmmet("div.card>h2+p")
        assertTrue(card.contains("<div class=\"card\">"))
        assertTrue(card.contains("<h2></h2>"))
        val list = CodeLinterAndAudit.expandEmmet("ul>li*3")
        assertTrue(list.contains("<ul>"))
        assertEquals(3, Regex("<li>").findAll(list).count())
    }

    @Test
    fun `lint and audit detect and fix issues cleanly`() {
        val broken = "<html><body><img src=\"http://cdn.site/pic.png\"></body></html>"
        val issues = CodeLinterAndAudit.detectCodeIssues(broken)
        assertTrue(issues.any { it.fixType == "ADD_ALT" })
        assertTrue(issues.any { it.fixType == "UPGRADE_HTTPS" })

        val fixedHttps = CodeLinterAndAudit.applyCodeFix(broken, "UPGRADE_HTTPS", "App")
        assertTrue(fixedHttps.contains("https://cdn.site/pic.png"))

        val report = CodeLinterAndAudit.auditWebsite(broken, emptyList())
        assertTrue(report.overallScore in 0..100)
        assertEquals(6, report.findings.size)
    }

    @Test
    fun `single self contained html inlines css and js assets`() {
        val html = """<html><head><link rel="stylesheet" href="app.css"></head><body><script src="app.js"></script></body></html>"""
        val assets = listOf(
            ProjectAsset(path = "app.css", mimeType = "text/css", textContent = "body{color:#fff}", sizeBytes = 15),
            ProjectAsset(path = "app.js", mimeType = "application/javascript", textContent = "console.log(1);", sizeBytes = 15),
        )
        val bundled = CodeLinterAndAudit.bundleSingleSelfContainedHtml(html, assets)
        assertTrue(bundled.contains("<style>\nbody{color:#fff}\n</style>"))
        assertTrue(bundled.contains("<script>\nconsole.log(1);\n</script>"))
    }

    @Test
    fun `computeLineDiff marks added and removed lines`() {
        val diff = CodeLinterAndAudit.computeLineDiff("<h1>Old</h1>\n<p>Keep</p>", "<h1>New</h1>\n<p>Keep</p>")
        assertTrue(diff.any { it.type == DiffType.REMOVED && it.text.contains("Old") })
        assertTrue(diff.any { it.type == DiffType.ADDED && it.text.contains("New") })
        assertTrue(diff.any { it.type == DiffType.UNCHANGED && it.text.contains("Keep") })
    }

    @Test
    fun `freeInstantHostingProvider is first and resolves without token`() {
        assertEquals(
            com.example.data.model.HostingProvider.FREE_INSTANT,
            com.example.data.model.HostingProvider.entries.first(),
        )
        assertEquals(
            com.example.data.model.HostingProvider.FREE_INSTANT,
            com.example.data.model.HostingProvider.fromKey("free_instant"),
        )
    }
}
