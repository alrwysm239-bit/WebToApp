package com.example.data.model

enum class AppLanguage(val code: String, val isRtl: Boolean, val displayLabel: String) {
    AR("ar", true, "العربية (RTL)"),
    EN("en", false, "English (LTR)"),
    FR("fr", false, "Français"),
    ES("es", false, "Español");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: AR
    }
}

enum class ThemeMode(val key: String) {
    DARK("dark"),
    LIGHT("light"),
    SYSTEM("system");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: DARK
    }
}

enum class EditorTheme(val key: String, val label: String) {
    WEBFORGE_MIDNIGHT("midnight", "WebForge Midnight"),
    DRACULA_NEON("dracula", "Dracula Neon"),
    CYAN_MATRIX("matrix", "Cyan Matrix"),
    SOLAR_LIGHT("solar", "Solar Light");

    companion object {
        fun fromKey(key: String?): EditorTheme =
            entries.firstOrNull { it.key == key } ?: WEBFORGE_MIDNIGHT
    }
}

enum class DeviceFrame {
    PHONE,
    TABLET,
    PC,
}

enum class AppOrientation(val key: String, val manifestValue: String) {
    AUTO("auto", "unspecified"),
    PORTRAIT("portrait", "portrait"),
    LANDSCAPE("landscape", "landscape");

    companion object {
        fun fromKey(key: String?): AppOrientation =
            entries.firstOrNull { it.key == key } ?: AUTO
    }
}

enum class BackButtonBehavior(val key: String) {
    WEB_HISTORY("history"),
    CONFIRM_EXIT("confirm");

    companion object {
        fun fromKey(key: String?): BackButtonBehavior =
            entries.firstOrNull { it.key == key } ?: WEB_HISTORY
    }
}

enum class HostingProvider(val key: String, val title: String) {
    FREE_INSTANT("free_instant", "Free Instant (No Token)"),
    GITHUB_PAGES("github_pages", "GitHub Pages"),
    NETLIFY("netlify", "Netlify"),
    VERCEL("vercel", "Vercel"),
    CLOUDFLARE_PAGES("cloudflare_pages", "Cloudflare Pages"),
    FIREBASE_HOSTING("firebase_hosting", "Firebase Hosting");

    companion object {
        fun fromKey(key: String?): HostingProvider? =
            entries.firstOrNull { it.key == key }
    }
}

enum class ProjectSort(val key: String) {
    RECENT("recent"),
    NAME("name"),
    FAVORITES("favorites"),
}

data class ProjectAsset(
    val path: String,
    val mimeType: String,
    val textContent: String? = null,
    val base64Content: String? = null,
    val sizeBytes: Int = 0,
) {
    val isText: Boolean
        get() = textContent != null
}

data class AppExportConfig(
    val appName: String = "My Web App",
    val packageName: String = "com.webforge.mywebapp",
    val versionName: String = "1.0.0",
    val versionCode: Int = 1,
    val themeColorHex: String = "#5B3DF5",
    val statusBarColorHex: String = "#0C0A20",
    val orientation: AppOrientation = AppOrientation.AUTO,
    val fullscreen: Boolean = false,
    val pullToRefresh: Boolean = true,
    val keepScreenOn: Boolean = false,
    val backButtonBehavior: BackButtonBehavior = BackButtonBehavior.WEB_HISTORY,
    val permissions: Set<String> = setOf("INTERNET", "ACCESS_NETWORK_STATE"),
    val customIconBase64: String? = null,
    val seoDescription: String = "Built with WebForge Studio",
    val customDomain: String = "",
    val twaHost: String = "",
    val twaSha256: String = "",
    val admobAppId: String = "",
)

data class ConsoleEntry(
    val level: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
)

data class ProjectSummary(
    val id: Long,
    val name: String,
    val updatedAt: Long,
    val assetCount: Int,
    val htmlLength: Int,
    val publishedUrl: String?,
    val lastProvider: HostingProvider?,
    val isFavorite: Boolean = false,
)

data class ProjectSnapshot(
    val id: Long,
    val projectId: Long,
    val label: String,
    val createdAt: Long,
    val htmlSizeBytes: Int,
)

data class CodeIssue(
    val line: Int,
    val severity: String, // "ERROR" | "WARN"
    val messageAr: String,
    val messageEn: String,
    val fixType: String,
)

data class AuditFinding(
    val category: String, // Performance, SEO, Accessibility, Security, Links
    val passed: Boolean,
    val titleAr: String,
    val titleEn: String,
    val detailAr: String,
    val detailEn: String,
)

data class AuditReport(
    val overallScore: Int,
    val grade: String,
    val performanceScore: Int,
    val seoScore: Int,
    val accessibilityScore: Int,
    val securityScore: Int,
    val linksScore: Int,
    val findings: List<AuditFinding>,
)

data class TemplateItem(
    val id: String,
    val categoryAr: String,
    val categoryEn: String,
    val titleAr: String,
    val titleEn: String,
    val descriptionAr: String,
    val descriptionEn: String,
    val accentHex: Long,
    val html: String,
)

data class DiffLine(
    val type: DiffType,
    val text: String,
)

enum class DiffType {
    UNCHANGED,
    ADDED,
    REMOVED,
}

data class AiChatMessage(
    val isUser: Boolean,
    val text: String,
    val proposedCode: String? = null,
    val isApplied: Boolean = false,
)

sealed interface AiTaskState {
    data object Idle : AiTaskState
    data object Loading : AiTaskState
    data class Streaming(val text: String) : AiTaskState
    data class Success(val text: String, val proposedCode: String? = null) : AiTaskState
    data class Error(val message: String) : AiTaskState
}

sealed interface DeployState {
    data object Idle : DeployState
    data class InProgress(val stepLabel: String) : DeployState
    data class Success(val url: String, val provider: HostingProvider) : DeployState
    data class Error(val message: String) : DeployState
}
