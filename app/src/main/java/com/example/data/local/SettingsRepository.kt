package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppLanguage
import com.example.data.model.EditorTheme
import com.example.data.model.HostingProvider
import com.example.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.webForgeDataStore by preferencesDataStore(name = "webforge_settings")

data class SavedTokens(
    val githubToken: String = "",
    val githubOwner: String = "",
    val netlifyToken: String = "",
    val vercelToken: String = "",
    val cloudflareToken: String = "",
    val cloudflareAccountId: String = "",
    val firebaseToken: String = "",
    val firebaseSiteId: String = "",
)

data class UserSettings(
    val language: AppLanguage = AppLanguage.AR,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val autoSave: Boolean = true,
    val offlineMode: Boolean = false,
    val hasCompletedOnboarding: Boolean = false,
    val editorFontSize: Int = 13,
    val editorTheme: EditorTheme = EditorTheme.WEBFORGE_MIDNIGHT,
    val lastProjectId: Long = 0L,
    val savedTokens: SavedTokens = SavedTokens(),
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AUTO_SAVE = booleanPreferencesKey("auto_save")
        val OFFLINE_MODE = booleanPreferencesKey("offline_mode")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val EDITOR_FONT_SIZE = intPreferencesKey("editor_font_size")
        val EDITOR_THEME = stringPreferencesKey("editor_theme")
        val LAST_PROJECT_ID = longPreferencesKey("last_project_id")

        val GITHUB_TOKEN = stringPreferencesKey("tok_github")
        val GITHUB_OWNER = stringPreferencesKey("tok_github_owner")
        val NETLIFY_TOKEN = stringPreferencesKey("tok_netlify")
        val VERCEL_TOKEN = stringPreferencesKey("tok_vercel")
        val CLOUDFLARE_TOKEN = stringPreferencesKey("tok_cf")
        val CLOUDFLARE_ACCOUNT = stringPreferencesKey("tok_cf_acct")
        val FIREBASE_TOKEN = stringPreferencesKey("tok_fb")
        val FIREBASE_SITE = stringPreferencesKey("tok_fb_site")
    }

    val settingsFlow: Flow<UserSettings> = context.webForgeDataStore.data.map { prefs ->
        UserSettings(
            language = AppLanguage.fromCode(prefs[Keys.LANGUAGE]),
            themeMode = ThemeMode.fromKey(prefs[Keys.THEME_MODE]),
            autoSave = prefs[Keys.AUTO_SAVE] ?: true,
            offlineMode = prefs[Keys.OFFLINE_MODE] ?: false,
            hasCompletedOnboarding = prefs[Keys.ONBOARDING_DONE] ?: false,
            editorFontSize = (prefs[Keys.EDITOR_FONT_SIZE] ?: 13).coerceIn(10, 20),
            editorTheme = EditorTheme.fromKey(prefs[Keys.EDITOR_THEME]),
            lastProjectId = prefs[Keys.LAST_PROJECT_ID] ?: 0L,
            savedTokens = SavedTokens(
                githubToken = prefs[Keys.GITHUB_TOKEN] ?: "",
                githubOwner = prefs[Keys.GITHUB_OWNER] ?: "",
                netlifyToken = prefs[Keys.NETLIFY_TOKEN] ?: "",
                vercelToken = prefs[Keys.VERCEL_TOKEN] ?: "",
                cloudflareToken = prefs[Keys.CLOUDFLARE_TOKEN] ?: "",
                cloudflareAccountId = prefs[Keys.CLOUDFLARE_ACCOUNT] ?: "",
                firebaseToken = prefs[Keys.FIREBASE_TOKEN] ?: "",
                firebaseSiteId = prefs[Keys.FIREBASE_SITE] ?: "",
            ),
        )
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.webForgeDataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        context.webForgeDataStore.edit { it[Keys.THEME_MODE] = themeMode.key }
    }

    suspend fun setAutoSave(enabled: Boolean) {
        context.webForgeDataStore.edit { it[Keys.AUTO_SAVE] = enabled }
    }

    suspend fun setOfflineMode(enabled: Boolean) {
        context.webForgeDataStore.edit { it[Keys.OFFLINE_MODE] = enabled }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.webForgeDataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    suspend fun setEditorFontSize(size: Int) {
        context.webForgeDataStore.edit { it[Keys.EDITOR_FONT_SIZE] = size.coerceIn(10, 20) }
    }

    suspend fun setEditorTheme(theme: EditorTheme) {
        context.webForgeDataStore.edit { it[Keys.EDITOR_THEME] = theme.key }
    }

    suspend fun setLastProjectId(projectId: Long) {
        context.webForgeDataStore.edit { it[Keys.LAST_PROJECT_ID] = projectId }
    }

    suspend fun saveProviderCredentials(
        provider: HostingProvider,
        token: String,
        primaryParam: String,
    ) {
        context.webForgeDataStore.edit { prefs ->
            when (provider) {
                HostingProvider.FREE_INSTANT -> Unit
                HostingProvider.GITHUB_PAGES -> {
                    prefs[Keys.GITHUB_TOKEN] = token
                    if (primaryParam.isNotBlank()) prefs[Keys.GITHUB_OWNER] = primaryParam
                }
                HostingProvider.NETLIFY -> prefs[Keys.NETLIFY_TOKEN] = token
                HostingProvider.VERCEL -> prefs[Keys.VERCEL_TOKEN] = token
                HostingProvider.CLOUDFLARE_PAGES -> {
                    prefs[Keys.CLOUDFLARE_TOKEN] = token
                    if (primaryParam.isNotBlank()) prefs[Keys.CLOUDFLARE_ACCOUNT] = primaryParam
                }
                HostingProvider.FIREBASE_HOSTING -> {
                    prefs[Keys.FIREBASE_TOKEN] = token
                    if (primaryParam.isNotBlank()) prefs[Keys.FIREBASE_SITE] = primaryParam
                }
            }
        }
    }

    suspend fun clearSavedTokens() {
        context.webForgeDataStore.edit { prefs ->
            prefs.remove(Keys.GITHUB_TOKEN)
            prefs.remove(Keys.GITHUB_OWNER)
            prefs.remove(Keys.NETLIFY_TOKEN)
            prefs.remove(Keys.VERCEL_TOKEN)
            prefs.remove(Keys.CLOUDFLARE_TOKEN)
            prefs.remove(Keys.CLOUDFLARE_ACCOUNT)
            prefs.remove(Keys.FIREBASE_TOKEN)
            prefs.remove(Keys.FIREBASE_SITE)
        }
    }

    suspend fun clearAll() {
        context.webForgeDataStore.edit { it.clear() }
    }
}
