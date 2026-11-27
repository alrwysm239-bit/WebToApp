package com.example.ai

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

private const val FALLBACK_PROJECT_ID = "gen-lang-client-0836729242"
private const val FALLBACK_APP_ID = "1:181827572742:android:82b94c6b7d8fe22a96df87"
private const val FALLBACK_SENDER_ID = "181827572742"
private const val FALLBACK_STORAGE_BUCKET = "gen-lang-client-0836729242.firebasestorage.app"

private fun ensureFirebaseInitialized(context: Context): FirebaseApp? {
    val appContext = context.applicationContext ?: context
    FirebaseApp.getApps(appContext).firstOrNull()?.let { return it }

    val defaultApp = runCatching { FirebaseApp.initializeApp(appContext) }.getOrNull()
    if (defaultApp != null) return defaultApp

    return runCatching {
        val res = appContext.resources
        val pkg = appContext.packageName
        fun strRes(name: String): String = res.getIdentifier(name, "string", pkg)
            .takeIf { it != 0 }
            ?.let { runCatching { appContext.getString(it) }.getOrNull() }
            .orEmpty()

        val apiKey = strRes("google_api_key").ifBlank { strRes("google_crash_reporting_api_key") }
        val appId = strRes("google_app_id").ifBlank { FALLBACK_APP_ID }
        val projectId = strRes("project_id").ifBlank { FALLBACK_PROJECT_ID }
        val senderId = strRes("gcm_defaultSenderId").ifBlank { FALLBACK_SENDER_ID }
        val bucket = strRes("google_storage_bucket").ifBlank { FALLBACK_STORAGE_BUCKET }

        if (apiKey.isNotBlank()) {
            val options = FirebaseOptions.Builder()
                .setApiKey(apiKey)
                .setApplicationId(appId)
                .setProjectId(projectId)
                .setGcmSenderId(senderId)
                .setStorageBucket(bucket)
                .build()
            FirebaseApp.initializeApp(appContext, options)
        } else {
            null
        }
    }.onFailure { e ->
        Log.e(TAG, "Fallback FirebaseApp initialization failed", e)
    }.getOrNull()
}

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        val firebaseApp = ensureFirebaseInitialized(context) ?: run {
            Log.w(TAG, "FirebaseApp unavailable; skipping App Check setup.")
            return
        }

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = firebaseApp.persistenceKey
                val prefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE,
                )
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
            } else {
                Log.w(TAG, "No debug token found in Intent extra.")
            }

            FirebaseAppCheck.getInstance(firebaseApp).installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance(),
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed.")
        } else {
            val resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
            if (resId != 0) {
                val siteKey = context.getString(resId)
                if (siteKey.isNotBlank()) {
                    FirebaseAppCheck.getInstance(firebaseApp).installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey),
                    )
                    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                } else {
                    Log.e(TAG, "recaptcha_site_key resource string is blank.")
                }
            } else {
                Log.e(TAG, "recaptcha_site_key string resource not found in package.")
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}
