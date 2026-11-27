package com.example.ai

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiStudioAssistant"

enum class AiQuickAction {
    FIX_BUGS,
    MAKE_RESPONSIVE,
    CONVERT_RTL_ARABIC,
    ADD_DARK_MODE,
    TRANSLATE_EN_AR,
    OPTIMIZE_SEO_SPEED,
    EXPLAIN_CODE,
    SUGGEST_BRAND,
}

data class StoreDescriptionsResult(
    val ar: String,
    val en: String,
    val fr: String,
    val es: String,
)

class GeminiStudioAssistant(
    private val appContext: Context? = null,
) {

    private fun prepareFirebase() {
        appContext?.let { ensureFirebaseInitialized(it) }
    }

    private val generalModel by lazy {
        prepareFirebase()
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    private val storeSchema = Schema.obj(
        properties = mapOf(
            "ar" to Schema.string(description = "Arabic Play Store short & full description"),
            "en" to Schema.string(description = "English Play Store short & full description"),
            "fr" to Schema.string(description = "French Play Store short & full description"),
            "es" to Schema.string(description = "Spanish Play Store short & full description"),
        ),
    )

    private val storeModel by lazy {
        prepareFirebase()
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = storeSchema
            },
        )
    }

    fun streamGenerateWebsite(
        description: String,
        preferArabic: Boolean,
        isMobileAppMode: Boolean = false,
    ): Flow<String> = flow {
        val langNote = if (preferArabic) {
            "Use natural Arabic with <html lang=\"ar\" dir=\"rtl\"> by default, clean Cairo/system Arabic typography, and RTL layout."
        } else {
            "Use clean English UI copy and <html lang=\"en\" dir=\"ltr\">."
        }
        val modeGuide = if (isMobileAppMode) {
            """
            Build a COMPLETE interactive HTML5 Mobile Application designed to run inside an Android/iOS WebView or PWA:
            - App shell layout with 100dvh, fixed top app bar, smooth bottom navigation bar switching between 3-4 functional views/tabs, and floating action controls.
            - Full offline interactivity in vanilla JavaScript with localStorage persistence (saving items, settings, scores, cart, or tasks cleanly).
            - Touch-friendly >=44px tap targets, smooth CSS transitions, dark/modern glassmorphic card UI, crisp inline SVG icons, and responsive viewport meta (width=device-width, initial-scale=1, viewport-fit=cover).
            - Zero placeholder/dead buttons: every button, form, filter, and modal MUST genuinely work in JS.
            """.trimIndent()
        } else {
            """
            Build a COMPLETE modern responsive Website with embedded <style> and <script>:
            - Responsive mobile-first layout (flexbox/grid), navigation header with mobile menu, hero section, rich feature cards, interactive JS widgets/forms, footer, and SEO meta tags.
            - High-contrast accessible palette, sleek animations, and crisp inline SVG visuals.
            - Zero dead buttons: every interactive control and form must work in JS.
            """.trimIndent()
        }
        val prompt = """
            You are an expert senior frontend & mobile HTML5 engineer inside WebForge Studio.
            $langNote
            $modeGuide
            Return ONLY the complete single-file HTML5 document starting directly with <!DOCTYPE html> (NO markdown ``` fences, NO explanations).
            User Request: $description
        """.trimIndent()

        val acc = StringBuilder()
        try {
            withTimeout(60_000L) {
                generalModel.generateContentStream(prompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        acc.append(piece)
                        emit(acc.toString())
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini site generation timed out", e)
            throw IllegalStateException("AI request timed out after 60s. Please retry.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini site generation failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Gemini AI failed.", e)
        }
    }

    suspend fun runQuickTransform(
        action: AiQuickAction,
        currentCode: String,
        preferArabic: Boolean,
    ): Pair<String, String?> {
        val expectsCode = action != AiQuickAction.EXPLAIN_CODE && action != AiQuickAction.SUGGEST_BRAND
        val instruction = when (action) {
            AiQuickAction.FIX_BUGS -> "Find and fix all HTML, CSS, and JavaScript bugs in this code. Keep the design intact."
            AiQuickAction.MAKE_RESPONSIVE -> "Make this website 100% responsive for mobile, tablet, and desktop with viewport meta and fluid CSS."
            AiQuickAction.CONVERT_RTL_ARABIC -> "Convert this page to Arabic RTL (<html lang=\"ar\" dir=\"rtl\">), translating UI text cleanly to natural Arabic."
            AiQuickAction.ADD_DARK_MODE -> "Add a sleek Dark/Light mode toggle button with CSS variables and localStorage persistence."
            AiQuickAction.TRANSLATE_EN_AR -> if (preferArabic) {
                "Translate all visible text in this HTML page to natural Arabic while preserving all tags and layout."
            } else {
                "Translate all visible text in this HTML page to natural English while preserving all tags and layout."
            }
            AiQuickAction.OPTIMIZE_SEO_SPEED -> "Optimize this HTML for SEO (title, meta description, Open Graph, semantic tags) and fast performance."
            AiQuickAction.EXPLAIN_CODE -> if (preferArabic) {
                "اشرح هذا الكود باختصار ووضوح بالعربية مع ذكر أهم المكونات وأي نصائح للتحسين."
            } else {
                "Explain this HTML/CSS/JS code clearly and concisely, listing key components and improvement tips."
            }
            AiQuickAction.SUGGEST_BRAND -> if (preferArabic) {
                "اقترح 5 أسماء جذابة، وشعار نصي (Tagline)، ولوحة ألوان HEX احترافية مناسبة لهذا الموقع."
            } else {
                "Suggest 5 catchy app/site names, a tagline, and a matching HEX color palette for this project."
            }
        }

        val prompt = if (expectsCode) {
            """
                $instruction
                Return ONLY the complete updated HTML code starting with <!DOCTYPE html> (no markdown fences, no commentary).
                CODE:
                $currentCode
            """.trimIndent()
        } else {
            """
                $instruction
                CODE:
                $currentCode
            """.trimIndent()
        }

        return try {
            val response = withTimeout(60_000L) {
                generalModel.generateContent(prompt)
            }
            val raw = response.text?.trim().orEmpty()
            if (raw.isEmpty()) throw IllegalStateException("Empty response from Gemini.")
            if (expectsCode) {
                val cleanHtml = stripCodeFence(raw)
                val summary = if (preferArabic) "تم تجهيز التعديلات. راجع الفروقات قبل التطبيق." else "Proposed changes ready. Review diff before applying."
                summary to cleanHtml
            } else {
                raw to null
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Quick transform timed out", e)
            throw IllegalStateException("AI request timed out after 60s.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Quick transform failed", e)
            throw IllegalStateException(e.localizedMessage ?: "AI operation failed.", e)
        }
    }

    suspend fun chatEditProject(
        userMessage: String,
        currentCode: String,
        preferArabic: Boolean,
    ): Pair<String, String?> {
        val langHint = if (preferArabic) "Reply briefly in Arabic." else "Reply briefly in English."
        val prompt = """
            You are WebForge AI Assistant inside an HTML/CSS/JS studio.
            $langHint
            If the user asks to modify or add to the code, first write a 1-2 sentence summary of what you changed, then output the COMPLETE updated code wrapped strictly between <<<CODE>>> and <<<END_CODE>>> markers.
            If the user is only asking a question, just answer clearly without code markers.

            CURRENT CODE:
            $currentCode

            USER REQUEST:
            $userMessage
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                generalModel.generateContent(prompt)
            }
            val raw = response.text?.trim().orEmpty()
            if (raw.isEmpty()) throw IllegalStateException("Empty response from Gemini.")
            val startIdx = raw.indexOf("<<<CODE>>>")
            val endIdx = raw.indexOf("<<<END_CODE>>>")
            if (startIdx >= 0 && endIdx > startIdx) {
                val explanation = raw.substring(0, startIdx).trim()
                    .ifBlank { if (preferArabic) "إليك التعديل المقترح:" else "Here is the proposed update:" }
                val proposed = stripCodeFence(raw.substring(startIdx + "<<<CODE>>>".length, endIdx).trim())
                explanation to proposed
            } else {
                raw to null
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI chat timed out", e)
            throw IllegalStateException("AI chat timed out after 60s.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI chat failed", e)
            throw IllegalStateException(e.localizedMessage ?: "AI chat failed.", e)
        }
    }

    suspend fun generateStoreDescriptions(appName: String, htmlSnippet: String): StoreDescriptionsResult {
        val prompt = """
            Generate compelling app store listing descriptions (short tagline + feature summary, ~80 words each) in Arabic, English, French, and Spanish for an app named "$appName" built from this HTML snippet:
            ${htmlSnippet.take(1500)}
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                storeModel.generateContent(prompt)
            }
            val jsonText = response.text ?: throw IllegalStateException("Empty AI JSON response.")
            val json = JSONObject(jsonText)
            StoreDescriptionsResult(
                ar = json.getString("ar"),
                en = json.getString("en"),
                fr = json.getString("fr"),
                es = json.getString("es"),
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Store description generation timed out", e)
            throw IllegalStateException("AI store description timed out.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Store description generation failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Failed to generate store descriptions.", e)
        }
    }

    private fun stripCodeFence(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val firstNewline = trimmed.indexOf('\n')
        val body = if (firstNewline >= 0) trimmed.substring(firstNewline + 1) else trimmed
        return body.removeSuffix("```").trim()
    }
}
