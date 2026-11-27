package com.example.domain

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream

class HostingDeployer {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val zipMediaType = "application/zip".toMediaType()
    private val octetMediaType = "application/octet-stream".toMediaType()

    suspend fun deployFreeInstant(
        singleHtml: String,
        zipBytes: ByteArray,
        onProgress: (String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        onProgress("1/2")
        // Primary: ship.page anonymous ZIP deploy (supports full multi-file HTML/CSS/JS/images, zero token)
        val shipResult = runCatching {
            val shipReq = Request.Builder()
                .url("https://ship.page/deploy")
                .header("Content-Type", "application/zip")
                .header("Accept", "application/json")
                .post(zipBytes.toRequestBody(zipMediaType))
                .build()
            client.newCall(shipReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("ship.page (${resp.code}): $body")
                val url = JSONObject(body).optString("url").trim()
                if (!url.startsWith("http")) error("Invalid URL from ship.page")
                url
            }
        }
        if (shipResult.isSuccess) return@withContext shipResult.getOrThrow()

        // Fallback: htmlshare.net anonymous single-HTML publish API (zero token)
        onProgress("2/2")
        val payload = JSONObject().apply {
            put("html", singleHtml)
        }.toString()
        val htmlShareReq = Request.Builder()
            .url("https://api.htmlshare.net/v1/publish")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .post(payload.toRequestBody(jsonMediaType))
            .build()
        client.newCall(htmlShareReq).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val shipErr = shipResult.exceptionOrNull()?.message.orEmpty()
                error("Free instant publish failed ($shipErr | htmlshare ${resp.code}): $body")
            }
            val url = JSONObject(body).optString("url").trim()
            if (!url.startsWith("http")) error("Invalid URL returned by hosting service.")
            url
        }
    }

    suspend fun deployToGitHubPages(
        token: String,
        owner: String,
        repo: String,
        files: Map<String, ByteArray>,
        onProgress: (String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        val cleanOwner = owner.trim()
        val cleanRepo = HtmlToolkit.slugify(repo.trim().ifBlank { "webforge-site" })
        require(cleanToken.isNotEmpty() && cleanOwner.isNotEmpty()) {
            "GitHub username and Personal Access Token are required."
        }

        onProgress("1/3")
        val checkReq = Request.Builder()
            .url("https://api.github.com/repos/$cleanOwner/$cleanRepo")
            .header("Authorization", "Bearer $cleanToken")
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()
        val repoExists = client.newCall(checkReq).execute().use { it.isSuccessful }

        if (!repoExists) {
            val createBody = JSONObject().apply {
                put("name", cleanRepo)
                put("auto_init", true)
                put("private", false)
            }.toString()
            val createReq = Request.Builder()
                .url("https://api.github.com/user/repos")
                .header("Authorization", "Bearer $cleanToken")
                .header("Accept", "application/vnd.github+json")
                .post(createBody.toRequestBody(jsonMediaType))
                .build()
            client.newCall(createReq).execute().use { resp ->
                if (!resp.isSuccessful) error("GitHub repo creation failed (${resp.code}): ${resp.body?.string().orEmpty()}")
            }
        }

        onProgress("2/3")
        for ((path, content) in files) {
            val existingSha = fetchGithubFileSha(cleanToken, cleanOwner, cleanRepo, path)
            val payload = JSONObject().apply {
                put("message", "Deploy $path via WebForge")
                put("content", Base64.encodeToString(content, Base64.NO_WRAP))
                if (existingSha != null) put("sha", existingSha)
            }.toString()

            val putReq = Request.Builder()
                .url("https://api.github.com/repos/$cleanOwner/$cleanRepo/contents/$path")
                .header("Authorization", "Bearer $cleanToken")
                .header("Accept", "application/vnd.github+json")
                .put(payload.toRequestBody(jsonMediaType))
                .build()
            client.newCall(putReq).execute().use { resp ->
                if (!resp.isSuccessful) error("GitHub upload failed for $path (${resp.code})")
            }
        }

        onProgress("3/3")
        val pagesBody = JSONObject().apply {
            put("source", JSONObject().apply {
                put("branch", "main")
                put("path", "/")
            })
        }.toString()
        val pagesReq = Request.Builder()
            .url("https://api.github.com/repos/$cleanOwner/$cleanRepo/pages")
            .header("Authorization", "Bearer $cleanToken")
            .header("Accept", "application/vnd.github+json")
            .post(pagesBody.toRequestBody(jsonMediaType))
            .build()
        client.newCall(pagesReq).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 409) {
                error("GitHub Pages activation failed (${resp.code}): ${resp.body?.string().orEmpty()}")
            }
        }

        "https://$cleanOwner.github.io/$cleanRepo/"
    }

    private fun fetchGithubFileSha(token: String, owner: String, repo: String, path: String): String? {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/contents/$path")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val body = resp.body?.string() ?: return@use null
            runCatching { JSONObject(body).optString("sha").takeIf { it.isNotEmpty() } }.getOrNull()
        }
    }

    suspend fun deployToNetlify(
        token: String,
        siteIdInput: String,
        siteName: String,
        zipBytes: ByteArray,
        onProgress: (String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        require(cleanToken.isNotEmpty()) { "Netlify Personal Access Token is required." }

        onProgress("1/2")
        var siteId = siteIdInput.trim()
        if (siteId.isEmpty()) {
            val createBody = JSONObject().apply {
                put("name", "${HtmlToolkit.slugify(siteName)}-${(1000..9999).random()}")
            }.toString()
            val createReq = Request.Builder()
                .url("https://api.netlify.com/api/v1/sites")
                .header("Authorization", "Bearer $cleanToken")
                .post(createBody.toRequestBody(jsonMediaType))
                .build()
            client.newCall(createReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Netlify site creation failed (${resp.code}): $body")
                siteId = JSONObject(body).getString("id")
            }
        }

        onProgress("2/2")
        val deployReq = Request.Builder()
            .url("https://api.netlify.com/api/v1/sites/$siteId/deploys")
            .header("Authorization", "Bearer $cleanToken")
            .post(zipBytes.toRequestBody(zipMediaType))
            .build()
        client.newCall(deployReq).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Netlify deploy failed (${resp.code}): $body")
            val json = JSONObject(body)
            json.optString("ssl_url").ifBlank { json.getString("url") }
        }
    }

    suspend fun deployToVercel(
        token: String,
        projectName: String,
        files: Map<String, ByteArray>,
        onProgress: (String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        val cleanName = HtmlToolkit.slugify(projectName)
        require(cleanToken.isNotEmpty()) { "Vercel Access Token is required." }

        onProgress("1/2")
        val fileArray = JSONArray()
        files.forEach { (path, data) ->
            fileArray.put(
                JSONObject().apply {
                    put("file", path.trimStart('/'))
                    put("data", Base64.encodeToString(data, Base64.NO_WRAP))
                    put("encoding", "base64")
                },
            )
        }
        val payload = JSONObject().apply {
            put("name", cleanName)
            put("files", fileArray)
            put("projectSettings", JSONObject().apply {
                put("framework", JSONObject.NULL)
            })
        }.toString()

        onProgress("2/2")
        val req = Request.Builder()
            .url("https://api.vercel.com/v13/deployments")
            .header("Authorization", "Bearer $cleanToken")
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Vercel deploy failed (${resp.code}): $body")
            val json = JSONObject(body)
            val url = json.getString("url")
            if (url.startsWith("http")) url else "https://$url"
        }
    }

    suspend fun deployToCloudflarePages(
        token: String,
        accountId: String,
        projectName: String,
        files: Map<String, ByteArray>,
        onProgress: (String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        val cleanAcct = accountId.trim()
        val cleanProject = HtmlToolkit.slugify(projectName)
        require(cleanToken.isNotEmpty() && cleanAcct.isNotEmpty()) {
            "Cloudflare API Token and Account ID are required."
        }

        onProgress("1/2")
        val ensureProjectReq = Request.Builder()
            .url("https://api.cloudflare.com/client/v4/accounts/$cleanAcct/pages/projects")
            .header("Authorization", "Bearer $cleanToken")
            .post(
                JSONObject().apply {
                    put("name", cleanProject)
                    put("production_branch", "main")
                }.toString().toRequestBody(jsonMediaType),
            )
            .build()
        client.newCall(ensureProjectReq).execute().close() // 409 if exists is fine

        onProgress("2/2")
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
        val manifestObj = JSONObject()
        files.forEach { (path, bytes) ->
            val hash = sha256Hex(bytes).take(32)
            val webPath = if (path.startsWith("/")) path else "/$path"
            manifestObj.put(webPath, hash)
            multipart.addFormDataPart(hash, path, bytes.toRequestBody(octetMediaType))
        }
        multipart.addFormDataPart("manifest", manifestObj.toString())

        val deployReq = Request.Builder()
            .url("https://api.cloudflare.com/client/v4/accounts/$cleanAcct/pages/projects/$cleanProject/deployments")
            .header("Authorization", "Bearer $cleanToken")
            .post(multipart.build())
            .build()

        client.newCall(deployReq).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Cloudflare Pages deploy failed (${resp.code}): $body")
            val json = JSONObject(body)
            val result = json.optJSONObject("result")
            result?.optString("url")?.takeIf { it.isNotEmpty() } ?: "https://$cleanProject.pages.dev"
        }
    }

    suspend fun deployToFirebaseHosting(
        oauthAccessToken: String,
        siteId: String,
        files: Map<String, ByteArray>,
        onProgress: (String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val token = oauthAccessToken.trim()
        val site = siteId.trim()
        require(token.isNotEmpty() && site.isNotEmpty()) {
            "Firebase Site ID and OAuth Access Token are required."
        }

        onProgress("1/4")
        val versionBody = JSONObject().apply {
            put("config", JSONObject().apply { put("headers", JSONArray()) })
        }.toString()
        val createVersionReq = Request.Builder()
            .url("https://firebasehosting.googleapis.com/v1beta1/sites/$site/versions")
            .header("Authorization", "Bearer $token")
            .post(versionBody.toRequestBody(jsonMediaType))
            .build()

        val versionName = client.newCall(createVersionReq).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Firebase create version failed (${resp.code}): $body")
            JSONObject(body).getString("name")
        }

        onProgress("2/4")
        val hashedFiles = mutableMapOf<String, ByteArray>()
        val filesJson = JSONObject()
        files.forEach { (path, raw) ->
            val gzipped = gzip(raw)
            val sha256 = sha256Hex(gzipped)
            val webPath = if (path.startsWith("/")) path else "/$path"
            filesJson.put(webPath, sha256)
            hashedFiles[sha256] = gzipped
        }

        val populateReq = Request.Builder()
            .url("https://firebasehosting.googleapis.com/v1beta1/$versionName:populateFiles")
            .header("Authorization", "Bearer $token")
            .post(JSONObject().apply { put("files", filesJson) }.toString().toRequestBody(jsonMediaType))
            .build()

        val (uploadUrl, requiredHashes) = client.newCall(populateReq).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Firebase populateFiles failed (${resp.code}): $body")
            val json = JSONObject(body)
            val url = json.optString("uploadUrl")
            val hashesArr = json.optJSONArray("uploadRequiredHashes")
            val list = buildList {
                if (hashesArr != null) {
                    for (i in 0 until hashesArr.length()) add(hashesArr.getString(i))
                }
            }
            url to list
        }

        onProgress("3/4")
        for (hash in requiredHashes) {
            val gzBytes = hashedFiles[hash] ?: continue
            val uploadReq = Request.Builder()
                .url("$uploadUrl/$hash")
                .header("Authorization", "Bearer $token")
                .post(gzBytes.toRequestBody(octetMediaType))
                .build()
            client.newCall(uploadReq).execute().use { resp ->
                if (!resp.isSuccessful) error("Firebase upload failed (${resp.code})")
            }
        }

        onProgress("4/4")
        val finalizeReq = Request.Builder()
            .url("https://firebasehosting.googleapis.com/v1beta1/$versionName?update_mask=status")
            .header("Authorization", "Bearer $token")
            .patch(JSONObject().apply { put("status", "FINALIZED") }.toString().toRequestBody(jsonMediaType))
            .build()
        client.newCall(finalizeReq).execute().use { resp ->
            if (!resp.isSuccessful) error("Firebase finalize failed (${resp.code})")
        }

        val releaseReq = Request.Builder()
            .url("https://firebasehosting.googleapis.com/v1beta1/sites/$site/releases?versionName=$versionName")
            .header("Authorization", "Bearer $token")
            .post("{}".toRequestBody(jsonMediaType))
            .build()
        client.newCall(releaseReq).execute().use { resp ->
            if (!resp.isSuccessful) error("Firebase release failed (${resp.code})")
        }

        "https://$site.web.app"
    }

    private fun gzip(data: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(data) }
        return baos.toByteArray()
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
