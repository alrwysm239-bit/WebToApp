package com.example.ai

import android.content.Context
import com.example.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
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

/** Reads Gemini config: 1) Admin Proxy URL (no client key needed), 2) Admin/local key, 3) BuildConfig.GEMINI_API_KEY. */
object GeminiConfig {
    private const val PREFS_NAME = "webforge_gemini_prefs"
    private const val KEY_API_KEY = "gemini_api_key"
    private const val KEY_PROXY_URL = "gemini_proxy_url"
    private const val KEY_AI_ENABLED = "gemini_ai_enabled"
    private const val KEY_ADMIN_PW_SALT = "admin_pw_salt"
    private const val KEY_ADMIN_PW_HASH = "admin_pw_hash"

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun getSavedUserKey(context: Context? = appContext): String {
        val ctx = context?.applicationContext ?: appContext ?: return ""
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_API_KEY, "")
            ?.trim()
            .orEmpty()
    }

    fun saveUserKey(key: String, context: Context? = appContext) {
        val ctx = context?.applicationContext ?: appContext ?: return
        val clean = key.trim()
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply {
                if (clean.isEmpty()) remove(KEY_API_KEY) else putString(KEY_API_KEY, clean)
            }
            .commit()
    }

    fun getProxyUrl(context: Context? = appContext): String {
        val ctx = context?.applicationContext ?: appContext ?: return ""
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PROXY_URL, "")
            ?.trim()
            .orEmpty()
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
        val cleanProxy = proxyUrl.trim()
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
        geminiApiKey: String,
        passwordSalt: String,
        passwordHash: String,
        context: Context? = appContext,
    ) {
        val ctx = context?.applicationContext ?: appContext ?: return
        val cleanProxy = proxyUrl.trim()
        val cleanKey = geminiApiKey.trim()
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AI_ENABLED, aiEnabled)
            .apply {
                if (cleanProxy.isEmpty()) remove(KEY_PROXY_URL) else putString(KEY_PROXY_URL, cleanProxy)
                if (cleanKey.isEmpty()) remove(KEY_API_KEY) else putString(KEY_API_KEY, cleanKey)
                if (passwordSalt.isBlank()) remove(KEY_ADMIN_PW_SALT) else putString(KEY_ADMIN_PW_SALT, passwordSalt)
                if (passwordHash.isBlank()) remove(KEY_ADMIN_PW_HASH) else putString(KEY_ADMIN_PW_HASH, passwordHash)
            }
            .commit()
    }

    fun resolveApiKey(context: Context? = appContext): String {
        val userKey = getSavedUserKey(context)
        if (userKey.isNotEmpty()) return userKey
        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        if (buildKey.isNotEmpty() && !buildKey.startsWith("PUT_YOUR")) return buildKey
        return ""
    }

    val API_KEY: String get() = resolveApiKey()
    const val MODEL = "gemini-2.5-flash"
    val FALLBACK_MODELS = listOf(
        "gemini-2.5-flash",
        "gemini-3.8-flash",
        "gemini-2.5-flash-lite",
        "gemini-3.5-flash-lite",
    )
}

class GeminiTextResponse(val text: String?)

/** Direct Gemini REST / Proxy client with automatic model fallback and retry on 503/429. */
object GeminiRest {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models/"
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    private val RETRYABLE_CODES = setOf(404, 408, 429, 500, 502, 503, 504)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private class RetryableGeminiException(message: String) : IllegalStateException(message)

    private fun resolveTarget(model: String, stream: Boolean): Pair<String, String?> {
        if (!GeminiConfig.isAiEnabled()) {
            throw IllegalStateException("تم إيقاف خدمة الذكاء الاصطناعي مؤقتاً من قِبل مشرف التطبيق.")
        }
        val action = if (stream) "streamGenerateContent?alt=sse" else "generateContent"
        val proxy = GeminiConfig.getProxyUrl().trim()
        if (proxy.isNotEmpty()) {
            val cleanProxy = proxy.trimEnd('/')
            val url = if (cleanProxy.contains("/v1beta/")) {
                "$cleanProxy/models/$model:$action"
            } else {
                "$cleanProxy/v1beta/models/$model:$action"
            }
            return url to null
        }
        val k = GeminiConfig.resolveApiKey().trim()
        if (k.isEmpty()) {
            throw IllegalStateException(
                "خدمة الذكاء الاصطناعي غير مهيّأة بعد. يرجى من مشرف التطبيق الدخول إلى «الإعدادات» ← «لوحة تحكم المشرف» لإضافة المفتاح أو الخادم الوسيط."
            )
        }
        return (BASE + model + ":" + action) to k
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

    private fun errorMessage(code: Int, raw: String): String {
        val msg = try {
            JSONObject(raw).optJSONObject("error")?.optString("message")
        } catch (e: Exception) {
            null
        }
        if (code in setOf(400, 401, 403) &&
            (msg?.contains("API key", ignoreCase = true) == true || raw.contains("API_KEY_INVALID", ignoreCase = true))
        ) {
            return "مفتاح Gemini غير صالح ($code). يمكن للمشرف تحديث المفتاح من «الإعدادات» ← «لوحة تحكم المشرف»."
        }
        return "Gemini error $code: ${if (msg.isNullOrBlank()) raw.take(300) else msg}"
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

    suspend fun generate(prompt: String, jsonMode: Boolean = false): GeminiTextResponse {
        val bodyJson = buildBody(prompt, jsonMode)
        val attempts = GeminiConfig.FALLBACK_MODELS + listOf(GeminiConfig.MODEL)
        var lastError: Throwable? = null

        for ((index, model) in attempts.withIndex()) {
            try {
                val (url, key) = resolveTarget(model, stream = false)
                val reqBuilder = Request.Builder()
                    .url(url)
                    .post(bodyJson.toRequestBody(JSON_TYPE))
                if (!key.isNullOrBlank()) reqBuilder.header("x-goog-api-key", key)
                val resp = client.newCall(reqBuilder.build()).awaitResponse()
                return withContext(Dispatchers.IO) {
                    resp.use {
                        val raw = it.body?.string().orEmpty()
                        if (!it.isSuccessful) {
                            val msg = errorMessage(it.code, raw)
                            if (it.code in RETRYABLE_CODES) {
                                throw RetryableGeminiException(msg)
                            }
                            throw IllegalStateException(msg)
                        }
                        GeminiTextResponse(extractText(JSONObject(raw)))
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: RetryableGeminiException) {
                lastError = e
                if (index < attempts.lastIndex) {
                    kotlinx.coroutines.delay(if (index >= GeminiConfig.FALLBACK_MODELS.size - 1) 1200L else 400L)
                }
            } catch (e: IOException) {
                lastError = e
                if (index < attempts.lastIndex) {
                    kotlinx.coroutines.delay(600L)
                }
            }
        }

        throw IllegalStateException(
            lastError?.message ?: "Gemini servers are temporarily busy. Please try again in a moment.",
            lastError,
        )
    }

    fun stream(prompt: String): Flow<String> = flow {
        val bodyJson = buildBody(prompt, false)
        val attempts = GeminiConfig.FALLBACK_MODELS + listOf(GeminiConfig.MODEL)
        var lastError: Throwable? = null

        for ((index, model) in attempts.withIndex()) {
            var emittedAny = false
            try {
                val (url, key) = resolveTarget(model, stream = true)
                val reqBuilder = Request.Builder()
                    .url(url)
                    .post(bodyJson.toRequestBody(JSON_TYPE))
                if (!key.isNullOrBlank()) reqBuilder.header("x-goog-api-key", key)
                val call = client.newCall(reqBuilder.build())
                val jobHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
                try {
                    val resp = call.awaitResponse()
                    resp.use {
                        if (!it.isSuccessful) {
                            val raw = it.body?.string().orEmpty()
                            val msg = errorMessage(it.code, raw)
                            if (it.code in RETRYABLE_CODES) {
                                throw RetryableGeminiException(msg)
                            }
                            throw IllegalStateException(msg)
                        }
                        val source = it.body?.source() ?: throw RetryableGeminiException("Empty response from Gemini.")
                        while (true) {
                            val line = source.readUtf8Line() ?: break
                            if (!line.startsWith("data:")) continue
                            val data = line.substring(5).trim()
                            if (data.isEmpty() || data == "[DONE]") continue
                            val obj = JSONObject(data)
                            if (obj.has("error")) {
                                val errObj = obj.optJSONObject("error")
                                val errCode = errObj?.optInt("code", 503) ?: 503
                                val errMsg = errorMessage(errCode, data)
                                if (!emittedAny && errCode in RETRYABLE_CODES) {
                                    throw RetryableGeminiException(errMsg)
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
                        throw RetryableGeminiException("Empty stream from Gemini.")
                    }
                    return@flow
                } finally {
                    jobHandle?.dispose()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: RetryableGeminiException) {
                if (emittedAny) throw e
                lastError = e
                if (index < attempts.lastIndex) {
                    kotlinx.coroutines.delay(if (index >= GeminiConfig.FALLBACK_MODELS.size - 1) 1200L else 400L)
                }
            } catch (e: IOException) {
                if (emittedAny) throw e
                lastError = e
                if (index < attempts.lastIndex) {
                    kotlinx.coroutines.delay(600L)
                }
            }
        }

        throw IllegalStateException(
            lastError?.message ?: "Gemini servers are temporarily busy. Please try again in a moment.",
            lastError,
        )
    }.flowOn(Dispatchers.IO)
}
