package com.example.ai

import android.content.Context
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/**
 * Global proxy configuration for Gemini REST requests.
 * Uses DEFAULT_PROXY_URL automatically for all users (admin, members, guests) without sign-in
 * and without storing any API key on the client device.
 */
object GeminiConfig {
    const val DEFAULT_PROXY_URL = "https://late-feather-3c04webforge-ai.alrwysm239.workers.dev"
    const val MODEL = "gemini-2.5-flash"

    private const val PREFS_NAME = "webforge_gemini_prefs"
    private const val KEY_PROXY_URL = "gemini_proxy_url"
    private const val KEY_AI_ENABLED = "gemini_ai_enabled"
    private const val KEY_ADMIN_PW_SALT = "admin_pw_salt"
    private const val KEY_ADMIN_PW_HASH = "admin_pw_hash"

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Validates an optional admin proxy URL.
     * Blank input is allowed and means "use DEFAULT_PROXY_URL".
     * Non-blank input MUST start with "https://" after trim().
     */
    fun validateProxyUrl(raw: String): Result<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Result.success("")
        if (!trimmed.startsWith("https://")) {
            return Result.failure(
                IllegalArgumentException(
                    "رابط الخادم الوسيط غير صالح: يجب أن يبدأ بـ https:// بعد إزالة المسافات."
                )
            )
        }
        val normalized = trimmed.removeSuffix("/v1beta").trimEnd('/')
        val afterScheme = normalized.removePrefix("https://")
        val host = afterScheme.substringBefore('/')
        if (afterScheme.isBlank() ||
            trimmed.any { it.isWhitespace() } ||
            host.isBlank() ||
            !host.contains('.')
        ) {
            return Result.failure(
                IllegalArgumentException(
                    "رابط الخادم الوسيط غير مكتمل: أدخل عنوان خادم صحيح يبدأ بـ https:// مثل $DEFAULT_PROXY_URL"
                )
            )
        }
        return Result.success(normalized)
    }

    /** Returns the custom proxy URL configured by the admin, or empty string if none is set. */
    fun getCustomProxyUrl(context: Context? = appContext): String {
        val ctx = context?.applicationContext ?: appContext ?: return ""
        val saved = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PROXY_URL, "")
            ?.trim()
            .orEmpty()
        return validateProxyUrl(saved).getOrDefault("")
    }

    /** Returns the effective proxy URL: admin override if valid & non-blank, otherwise DEFAULT_PROXY_URL. */
    fun resolveProxyUrl(context: Context? = appContext): String {
        val custom = getCustomProxyUrl(context)
        return custom.ifBlank { DEFAULT_PROXY_URL }
    }

    fun getProxyUrl(context: Context? = appContext): String = resolveProxyUrl(context)

    fun saveCustomProxyUrl(rawUrl: String, context: Context? = appContext): Result<String> {
        val validated = validateProxyUrl(rawUrl)
        val clean = validated.getOrElse { return Result.failure(it) }
        val ctx = context?.applicationContext ?: appContext
        ctx?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.apply {
                if (clean.isEmpty()) remove(KEY_PROXY_URL) else putString(KEY_PROXY_URL, clean)
            }
            ?.commit()
        return Result.success(clean)
    }

    fun isAiEnabled(context: Context? = appContext): Boolean {
        val ctx = context?.applicationContext ?: appContext ?: return true
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AI_ENABLED, true)
    }

    fun getLocalAdminPasswordSalt(context: Context? = appContext): String {
        val ctx = context?.applicationContext ?: appContext ?: return ""
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ADMIN_PW_SALT, "")
            .orEmpty()
    }

    fun getLocalAdminPasswordHash(context: Context? = appContext): String {
        val ctx = context?.applicationContext ?: appContext ?: return ""
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ADMIN_PW_HASH, "")
            .orEmpty()
    }

    fun applyPublicRuntime(aiEnabled: Boolean, proxyUrl: String, context: Context? = appContext) {
        val ctx = context?.applicationContext ?: appContext ?: return
        val cleanProxy = validateProxyUrl(proxyUrl).getOrDefault("")
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AI_ENABLED, aiEnabled)
            .apply {
                if (cleanProxy.isEmpty()) remove(KEY_PROXY_URL) else putString(KEY_PROXY_URL, cleanProxy)
            }
            .commit()
    }

    fun applyAdminCloudSettings(
        aiEnabled: Boolean,
        proxyUrl: String,
        passwordSalt: String,
        passwordHash: String,
        context: Context? = appContext,
    ) {
        val ctx = context?.applicationContext ?: appContext ?: return
        val cleanProxy = validateProxyUrl(proxyUrl).getOrDefault("")
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AI_ENABLED, aiEnabled)
            .apply {
                if (cleanProxy.isEmpty()) remove(KEY_PROXY_URL) else putString(KEY_PROXY_URL, cleanProxy)
                if (passwordSalt.isBlank()) remove(KEY_ADMIN_PW_SALT) else putString(KEY_ADMIN_PW_SALT, passwordSalt)
                if (passwordHash.isBlank()) remove(KEY_ADMIN_PW_HASH) else putString(KEY_ADMIN_PW_HASH, passwordHash)
            }
            .commit()
    }

    fun resetCustomSettings(context: Context? = appContext) {
        val ctx = context?.applicationContext ?: appContext ?: return
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PROXY_URL)
            .putBoolean(KEY_AI_ENABLED, true)
            .commit()
    }
}

class GeminiTextResponse(val text: String?)

data class ProxyConnectionTestResult(
    val success: Boolean,
    val testedUrl: String,
    val latencyMs: Long,
    val message: String,
)

/** OkHttp REST client routing all requests through {proxyUrl}/v1beta/models/{model}:generateContent or :streamGenerateContent?alt=sse. */
object GeminiRest {
    private const val MAX_RETRYABLE_RETRIES = 2 // 1 initial attempt + 2 retries on 429 / 5xx / transient IO
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private class RetryableProxyException(message: String) : IllegalStateException(message)

    internal fun buildEndpointUrl(
        proxyBaseUrl: String,
        model: String,
        stream: Boolean,
    ): String {
        val cleanBase = proxyBaseUrl.trim().removeSuffix("/v1beta").trimEnd('/')
        val action = if (stream) "streamGenerateContent?alt=sse" else "generateContent"
        return "$cleanBase/v1beta/models/$model:$action"
    }

    private fun resolveTargetUrl(model: String, stream: Boolean, proxyOverride: String? = null): String {
        if (!GeminiConfig.isAiEnabled()) {
            throw IllegalStateException("تم إيقاف خدمة الذكاء الاصطناعي مؤقتاً من قِبل مشرف التطبيق.")
        }
        val baseProxy = if (proxyOverride != null) {
            val validated = GeminiConfig.validateProxyUrl(proxyOverride).getOrElse { throw it }
            validated.ifBlank { GeminiConfig.DEFAULT_PROXY_URL }
        } else {
            GeminiConfig.resolveProxyUrl()
        }
        return buildEndpointUrl(baseProxy, model, stream)
    }

    private fun buildBody(prompt: String, jsonMode: Boolean): String {
        val part = JSONObject().put("text", prompt)
        val content = JSONObject().put("parts", JSONArray().put(part))
        val root = JSONObject().put("contents", JSONArray().put(content))
        if (jsonMode) {
            val props = JSONObject()
            val names = listOf("ar", "en", "fr", "es")
            for (n in names) props.put(n, JSONObject().put("type", "STRING"))
            val schema = JSONObject()
                .put("type", "OBJECT")
                .put("properties", props)
                .put("required", JSONArray(names))
            root.put(
                "generationConfig",
                JSONObject().put("responseMimeType", "application/json").put("responseSchema", schema),
            )
        }
        return root.toString()
    }

    private fun extractText(obj: JSONObject): String {
        val cands = obj.optJSONArray("candidates") ?: return ""
        val parts = cands.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            sb.append(parts.optJSONObject(i)?.optString("text", "").orEmpty())
        }
        return sb.toString()
    }

    private fun isRetryableHttpCode(code: Int): Boolean = code == 429 || code in 500..599

    private fun arabicErrorMessage(code: Int, raw: String): String {
        val serverMsg = runCatching {
            JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")
        val detail = serverMsg.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
        return when {
            code == 429 -> "تم تجاوز حد الطلبات مؤقتاً (خطأ 429). انتظر قليلاً ثم اضغط إعادة المحاولة."
            code in 500..599 -> "خادم الذكاء الاصطناعي مشغول أو غير متاح حالياً (خطأ $code). يرجى إعادة المحاولة."
            code == 401 || code == 403 -> "رفض الخادم الوسيط الطلب (خطأ $code). تحقق من إعدادات الخادم والمفتاح على Worker."
            code == 404 -> "المسار أو النموذج غير موجود على الخادم الوسيط (خطأ 404). تحقق من رابط Proxy URL."
            code == 400 -> "تعذر تنفيذ الطلب بسبب صيغة غير صالحة (خطأ 400)$detail"
            else -> "فشل الاتصال بخادم الذكاء الاصطناعي (خطأ $code)$detail"
        }
    }

    private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation {
            try {
                cancel()
            } catch (_: Exception) {
            }
        }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
        })
    }

    suspend fun testConnection(proxyUrlInput: String): ProxyConnectionTestResult {
        val validated = GeminiConfig.validateProxyUrl(proxyUrlInput)
        val cleanOverride = validated.getOrElse { err ->
            return ProxyConnectionTestResult(
                success = false,
                testedUrl = proxyUrlInput.trim(),
                latencyMs = 0L,
                message = err.localizedMessage ?: "رابط الخادم الوسيط غير صالح.",
            )
        }
        val effectiveProxy = cleanOverride.ifBlank { GeminiConfig.DEFAULT_PROXY_URL }
        val endpoint = buildEndpointUrl(effectiveProxy, GeminiConfig.MODEL, stream = false)
        val startMs = System.currentTimeMillis()
        return try {
            val response = generate(
                prompt = "أجب بكلمة واحدة فقط: متصل",
                jsonMode = false,
                proxyOverride = effectiveProxy,
                bypassAiEnabledGate = true,
            )
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            val preview = response.text?.trim().orEmpty().take(60).ifBlank { "OK" }
            ProxyConnectionTestResult(
                success = true,
                testedUrl = endpoint,
                latencyMs = elapsed,
                message = "الاتصال ناجح ✓ ($elapsed مللي ثانية) — الرد: $preview",
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            ProxyConnectionTestResult(
                success = false,
                testedUrl = endpoint,
                latencyMs = elapsed,
                message = e.localizedMessage ?: "فشل اختبار الاتصال بالخادم الوسيط.",
            )
        }
    }

    suspend fun generate(
        prompt: String,
        jsonMode: Boolean = false,
        proxyOverride: String? = null,
        bypassAiEnabledGate: Boolean = false,
    ): GeminiTextResponse {
        val bodyJson = buildBody(prompt, jsonMode)
        val url = if (bypassAiEnabledGate) {
            val clean = (proxyOverride ?: GeminiConfig.resolveProxyUrl()).ifBlank { GeminiConfig.DEFAULT_PROXY_URL }
            buildEndpointUrl(clean, GeminiConfig.MODEL, stream = false)
        } else {
            resolveTargetUrl(GeminiConfig.MODEL, stream = false, proxyOverride = proxyOverride)
        }

        var lastError: Throwable? = null
        val totalAttempts = 1 + MAX_RETRYABLE_RETRIES
        for (attempt in 0 until totalAttempts) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .post(bodyJson.toRequestBody(JSON_TYPE))
                    .build()
                val resp = client.newCall(request).awaitResponse()
                return withContext(Dispatchers.IO) {
                    resp.use {
                        val raw = it.body?.string().orEmpty()
                        if (!it.isSuccessful) {
                            val msg = arabicErrorMessage(it.code, raw)
                            if (isRetryableHttpCode(it.code)) {
                                throw RetryableProxyException(msg)
                            }
                            throw IllegalStateException(msg)
                        }
                        GeminiTextResponse(extractText(JSONObject(raw)))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RetryableProxyException) {
                lastError = e
                if (attempt < MAX_RETRYABLE_RETRIES) {
                    delay(800L * (attempt + 1))
                }
            } catch (e: IOException) {
                lastError = IllegalStateException(
                    "تعذر الاتصال بخادم الذكاء الاصطناعي. تحقق من الإنترنت أو رابط الخادم ثم أعد المحاولة.",
                    e,
                )
                if (attempt < MAX_RETRYABLE_RETRIES) {
                    delay(800L * (attempt + 1))
                }
            }
        }

        throw IllegalStateException(
            lastError?.message ?: "خادم الذكاء الاصطناعي غير متاح مؤقتاً. يرجى إعادة المحاولة.",
            lastError,
        )
    }

    fun stream(prompt: String, proxyOverride: String? = null): Flow<String> = flow {
        val bodyJson = buildBody(prompt, false)
        val url = resolveTargetUrl(GeminiConfig.MODEL, stream = true, proxyOverride = proxyOverride)

        var lastError: Throwable? = null
        val totalAttempts = 1 + MAX_RETRYABLE_RETRIES
        for (attempt in 0 until totalAttempts) {
            var emittedAny = false
            try {
                val request = Request.Builder()
                    .url(url)
                    .post(bodyJson.toRequestBody(JSON_TYPE))
                    .build()
                val call = client.newCall(request)
                val jobHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
                try {
                    val resp = call.awaitResponse()
                    resp.use {
                        if (!it.isSuccessful) {
                            val raw = it.body?.string().orEmpty()
                            val msg = arabicErrorMessage(it.code, raw)
                            if (isRetryableHttpCode(it.code)) {
                                throw RetryableProxyException(msg)
                            }
                            throw IllegalStateException(msg)
                        }
                        val source = it.body?.source()
                            ?: throw RetryableProxyException("استجابة فارغة من خادم الذكاء الاصطناعي. يرجى إعادة المحاولة.")
                        while (true) {
                            val line = source.readUtf8Line() ?: break
                            if (!line.startsWith("data:")) continue
                            val data = line.substring(5).trim()
                            if (data.isEmpty() || data == "[DONE]") continue
                            val obj = JSONObject(data)
                            if (obj.has("error")) {
                                val errObj = obj.optJSONObject("error")
                                val errCode = errObj?.optInt("code", 503) ?: 503
                                val errMsg = arabicErrorMessage(errCode, data)
                                if (!emittedAny && isRetryableHttpCode(errCode)) {
                                    throw RetryableProxyException(errMsg)
                                }
                                throw IllegalStateException(errMsg)
                            }
                            val piece = extractText(obj)
                            if (piece.isNotEmpty()) {
                                emittedAny = true
                                emit(piece)
                            }
                        }
                    }
                    if (!emittedAny) {
                        throw RetryableProxyException("لم يصل محتوى من خادم الذكاء الاصطناعي. يرجى إعادة المحاولة.")
                    }
                    return@flow
                } finally {
                    jobHandle?.dispose()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RetryableProxyException) {
                if (emittedAny) throw e
                lastError = e
                if (attempt < MAX_RETRYABLE_RETRIES) {
                    delay(800L * (attempt + 1))
                }
            } catch (e: IOException) {
                if (emittedAny) {
                    throw IllegalStateException("انقطع الاتصال أثناء البث. يرجى إعادة المحاولة.", e)
                }
                lastError = IllegalStateException(
                    "تعذر الاتصال بخادم الذكاء الاصطناعي. تحقق من الإنترنت أو رابط الخادم ثم أعد المحاولة.",
                    e,
                )
                if (attempt < MAX_RETRYABLE_RETRIES) {
                    delay(800L * (attempt + 1))
                }
            }
        }

        throw IllegalStateException(
            lastError?.message ?: "خادم الذكاء الاصطناعي غير متاح مؤقتاً. يرجى إعادة المحاولة.",
            lastError,
        )
    }.flowOn(Dispatchers.IO)
}
