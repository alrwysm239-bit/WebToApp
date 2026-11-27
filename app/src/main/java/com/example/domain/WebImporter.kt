package com.example.domain

import android.util.Base64
import com.example.data.model.ProjectAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

object WebImporter {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val textExtensions = setOf("html", "htm", "css", "js", "mjs", "json", "svg", "txt", "xml")

    suspend fun importFromUrl(rawUrl: String): ImportedProjectResult = withContext(Dispatchers.IO) {
        val url = rawUrl.trim().let {
            if (it.startsWith("http://", true) || it.startsWith("https://", true)) it else "https://$it"
        }
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "WebForge-Studio/2.0")
            .get()
            .build()

        val html = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code} while fetching URL")
            resp.body?.string().orEmpty()
        }
        require(html.isNotBlank()) { "Fetched page was empty." }

        val origin = url.substringBefore('/', url).let {
            val parts = url.split("/")
            if (parts.size >= 3) "${parts[0]}//${parts[2]}/" else url
        }
        val withBase = if (!html.contains("<base ", ignoreCase = true) && html.contains("<head", ignoreCase = true)) {
            val headMatch = Regex("""<head[^>]*>""", RegexOption.IGNORE_CASE).find(html)
            if (headMatch != null) {
                html.replaceRange(headMatch.range, "${headMatch.value}\n  <base href=\"$origin\">")
            } else html
        } else html

        val title = HtmlToolkit.extractTitle(withBase) ?: url.substringAfter("://").substringBefore('/')
        ImportedProjectResult(
            suggestedName = title,
            entryHtml = withBase,
            assets = emptyList(),
        )
    }

    suspend fun importFromGitHubRepo(
        ownerInput: String,
        repoInput: String,
        branch: String = "main",
        token: String = "",
    ): ImportedProjectResult = withContext(Dispatchers.IO) {
        val owner = ownerInput.trim()
        val repo = repoInput.trim()
        require(owner.isNotEmpty() && repo.isNotEmpty()) { "GitHub owner and repository are required." }

        val treeUrl = "https://api.github.com/repos/$owner/$repo/git/trees/${branch.ifBlank { "main" }}?recursive=1"
        val reqBuilder = Request.Builder()
            .url(treeUrl)
            .header("Accept", "application/vnd.github+json")
        if (token.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer ${token.trim()}")
        }

        val treeJson = client.newCall(reqBuilder.get().build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("GitHub repo fetch failed (${resp.code}): $body")
            JSONObject(body)
        }

        val treeArray = treeJson.getJSONArray("tree")
        val blobPaths = mutableListOf<String>()
        for (i in 0 until treeArray.length()) {
            val item = treeArray.getJSONObject(i)
            if (item.optString("type") == "blob") {
                val path = item.getString("path")
                val size = item.optInt("size", 0)
                if (size in 1..450_000) {
                    blobPaths.add(path)
                }
            }
        }

        // Prioritize index.html and web files, cap at 25 files for fast responsive import
        val selectedPaths = blobPaths
            .sortedBy { p ->
                when {
                    p.equals("index.html", true) -> 0
                    p.endsWith(".html", true) -> 1
                    p.endsWith(".css", true) -> 2
                    p.endsWith(".js", true) -> 3
                    else -> 4
                }
            }
            .take(25)

        val assets = mutableListOf<ProjectAsset>()
        for (path in selectedPaths) {
            val rawUrl = "https://raw.githubusercontent.com/$owner/$repo/${branch.ifBlank { "main" }}/$path"
            val rawReq = Request.Builder().url(rawUrl).apply {
                if (token.isNotBlank()) header("Authorization", "Bearer ${token.trim()}")
            }.get().build()

            val bytes = client.newCall(rawReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.bytes() else null
            } ?: continue

            val ext = path.substringAfterLast('.', "").lowercase(Locale.US)
            val mime = guessMime(ext)
            if (ext in textExtensions) {
                assets.add(
                    ProjectAsset(
                        path = path,
                        mimeType = mime,
                        textContent = bytes.toString(Charsets.UTF_8),
                        sizeBytes = bytes.size,
                    ),
                )
            } else {
                assets.add(
                    ProjectAsset(
                        path = path,
                        mimeType = mime,
                        base64Content = Base64.encodeToString(bytes, Base64.NO_WRAP),
                        sizeBytes = bytes.size,
                    ),
                )
            }
        }

        val entry = assets.firstOrNull { it.path.equals("index.html", true) }
            ?: assets.firstOrNull { it.path.endsWith(".html", true) }
        val entryHtml = entry?.textContent ?: HtmlToolkit.defaultStarterHtml(isArabic = false)
        val remaining = if (entry != null) assets.filterNot { it.path == entry.path } else assets

        ImportedProjectResult(
            suggestedName = HtmlToolkit.extractTitle(entryHtml) ?: repo,
            entryHtml = entryHtml,
            assets = remaining,
        )
    }

    private fun guessMime(ext: String): String = when (ext) {
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "js", "mjs" -> "application/javascript"
        "json" -> "application/json"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }
}
