package com.example.domain.apk

import android.content.Context
import android.util.Base64
import com.example.data.model.AppExportConfig
import com.example.data.model.AppOrientation
import com.example.data.model.AppSourceMode
import com.example.data.model.BackButtonBehavior
import com.example.data.model.ProjectAsset
import com.example.domain.CodeLinterAndAudit
import com.example.domain.HtmlToolkit
import com.example.domain.ZipArchiveManager
import org.json.JSONObject
import java.util.UUID

object OnDeviceApkBuilder {

    fun buildSignedApk(
        context: Context,
        projectName: String,
        html: String,
        assets: List<ProjectAsset>,
        config: AppExportConfig,
    ): ByteArray {
        val safeAppName = config.appName.ifBlank { projectName }.trim().ifBlank { "WebForge App" }
        val pkg = sanitizePackageName(
            config.packageName.ifBlank { "com.webforge.${HtmlToolkit.toSafePackageSegment(safeAppName)}" },
        )
        val dexBytes = context.assets.open("apk_template/classes.dex").use { it.readBytes() }
        val iconSource = ZipArchiveManager.resolveIconPngBytes(context, config.customIconBase64)
        val icon512 = ZipArchiveManager.resizePng(iconSource, 192)
        val splash256 = ZipArchiveManager.resizePng(iconSource, 256)

        val orientationInt = when (config.orientation) {
            AppOrientation.PORTRAIT -> 1
            AppOrientation.LANDSCAPE -> 0
            AppOrientation.AUTO -> -1
        }

        val manifestBytes = BinaryAndroidManifestEncoder.encodeManifest(
            packageName = pkg,
            versionCode = config.versionCode.coerceAtLeast(1),
            versionName = config.versionName.ifBlank { "1.0.0" },
            permissions = config.permissions.toList(),
            screenOrientationInt = orientationInt,
            minSdk = 24,
            targetSdk = 34,
        )
        val resourcesBytes = BinaryAndroidManifestEncoder.encodeResourcesArsc(
            packageName = pkg,
            appName = safeAppName,
        )

        val configJson = JSONObject().apply {
            put("appName", safeAppName)
            put("sourceMode", config.sourceMode.key)
            put("startUrl", config.sourceUrl.trim())
            put("themeColor", normalizeHex(config.themeColorHex))
            put("statusBarColor", normalizeHex(config.statusBarColorHex))
            put("navBarColor", normalizeHex(config.navBarColorHex))
            put("orientation", config.orientation.key)
            put("fullscreen", config.fullscreen)
            put("pullToRefresh", config.pullToRefresh)
            put("keepScreenOn", config.keepScreenOn)
            put("enableZoom", config.enableZoom)
            put("desktopMode", config.desktopMode)
            put("showProgressBar", config.showProgressBar)
            put("showFloatingBar", config.showFloatingBar)
            put("openExternalLinks", config.openExternalLinksInBrowser)
            put("backBehavior", if (config.backButtonBehavior == BackButtonBehavior.CONFIRM_EXIT) "confirm" else "history")
            put("customUserAgent", config.customUserAgent.trim())
            put("splashEnabled", config.splashEnabled)
            put("splashDurationMs", config.splashDurationMs.coerceIn(500, 6000))
            put("splashTitle", config.splashTitle.ifBlank { safeAppName })
            put("splashSubtitle", config.splashSubtitle.trim())
            put("splashBgColor", normalizeHex(config.splashBgColorHex))
            put("splashAccentColor", normalizeHex(config.themeColorHex))
            put("showSplashSpinner", true)
        }.toString(2).toByteArray(Charsets.UTF_8)

        val entries = mutableListOf(
            ApkSignerAndPackager.ZipInputEntry("AndroidManifest.xml", manifestBytes, storeUncompressed = false),
            ApkSignerAndPackager.ZipInputEntry("resources.arsc", resourcesBytes, storeUncompressed = true, alignBytes = 4),
            ApkSignerAndPackager.ZipInputEntry("classes.dex", dexBytes, storeUncompressed = false),
            ApkSignerAndPackager.ZipInputEntry("res/mipmap-xxxhdpi-v4/ic_launcher.png", icon512, storeUncompressed = true, alignBytes = 4),
            ApkSignerAndPackager.ZipInputEntry("assets/splash_icon.png", splash256, storeUncompressed = true, alignBytes = 4),
            ApkSignerAndPackager.ZipInputEntry("assets/app_config.json", configJson, storeUncompressed = false),
        )

        if (config.sourceMode == AppSourceMode.HTML) {
            val preparedHtml = HtmlToolkit.injectViewportSeoAndIcon(
                rawHtml = html,
                title = safeAppName,
                description = config.seoDescription,
                themeColorHex = config.themeColorHex,
                hasFavicon = false,
            )
            entries.add(
                ApkSignerAndPackager.ZipInputEntry(
                    "assets/www/index.html",
                    preparedHtml.toByteArray(Charsets.UTF_8),
                    storeUncompressed = false,
                ),
            )
            assets.forEach { asset ->
                ZipArchiveManager.assetToBytes(asset)?.let { bytes ->
                    entries.add(
                        ApkSignerAndPackager.ZipInputEntry(
                            "assets/www/${asset.path.trimStart('/')}",
                            bytes,
                            storeUncompressed = !asset.isText,
                            alignBytes = 4,
                        ),
                    )
                }
            }
        }

        return ApkSignerAndPackager.packageAndSignApk(entries, appSignerName = safeAppName)
    }

    /**
     * Generates an Apple iOS Configuration Profile (.mobileconfig) that installs directly
     * onto any iPhone/iPad Home Screen as a fullscreen WebClip App with custom Icon & Name,
     * with ZERO Xcode, Mac, GitHub, or token required.
     */
    fun buildIosWebClipMobileConfig(
        context: Context,
        projectName: String,
        html: String,
        assets: List<ProjectAsset>,
        config: AppExportConfig,
        publishedUrl: String?,
    ): ByteArray {
        val safeName = config.appName.ifBlank { projectName }.trim().ifBlank { "WebForge App" }
        val pkg = sanitizePackageName(config.packageName.ifBlank { "com.webforge.iosapp" })
        val iconSource = ZipArchiveManager.resolveIconPngBytes(context, config.customIconBase64)
        val icon180 = ZipArchiveManager.resizePng(iconSource, 180)
        val iconB64 = Base64.encodeToString(icon180, Base64.NO_WRAP).chunked(68).joinToString("\n      ")

        val targetUrl = when {
            config.sourceMode == AppSourceMode.URL && config.sourceUrl.isNotBlank() -> {
                val u = config.sourceUrl.trim()
                if (u.startsWith("http://") || u.startsWith("https://")) u else "https://$u"
            }
            !publishedUrl.isNullOrBlank() && publishedUrl.startsWith("http") -> publishedUrl
            else -> {
                val singleHtml = CodeLinterAndAudit.bundleSingleSelfContainedHtml(html, assets)
                val b64Html = Base64.encodeToString(singleHtml.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                "data:text/html;charset=utf-8;base64,$b64Html"
            }
        }

        val rootUuid = UUID.nameUUIDFromBytes(pkg.toByteArray()).toString().uppercase()
        val clipUuid = UUID.nameUUIDFromBytes("$pkg.webclip".toByteArray()).toString().uppercase()

        val plist = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
              <key>PayloadContent</key>
              <array>
                <dict>
                  <key>FullScreen</key>
                  <${if (config.fullscreen) "true" else "true"}/>
                  <key>Icon</key>
                  <data>
                  $iconB64
                  </data>
                  <key>IgnoreManifestScope</key>
                  <true/>
                  <key>IsRemovable</key>
                  <true/>
                  <key>Label</key>
                  <string>${escapeXml(safeName)}</string>
                  <key>PayloadDescription</key>
                  <string>WebForge iOS Web App</string>
                  <key>PayloadDisplayName</key>
                  <string>${escapeXml(safeName)}</string>
                  <key>PayloadIdentifier</key>
                  <string>$pkg.webclip</string>
                  <key>PayloadType</key>
                  <string>com.apple.webClip.managed</string>
                  <key>PayloadUUID</key>
                  <string>$clipUuid</string>
                  <key>PayloadVersion</key>
                  <integer>1</integer>
                  <key>Precomposed</key>
                  <true/>
                  <key>URL</key>
                  <string>${escapeXml(targetUrl)}</string>
                </dict>
              </array>
              <key>PayloadDescription</key>
              <string>Install ${escapeXml(safeName)} on iPhone / iPad</string>
              <key>PayloadDisplayName</key>
              <string>${escapeXml(safeName)}</string>
              <key>PayloadIdentifier</key>
              <string>$pkg.profile</string>
              <key>PayloadRemovalDisallowed</key>
              <false/>
              <key>PayloadType</key>
              <string>Configuration</string>
              <key>PayloadUUID</key>
              <string>$rootUuid</string>
              <key>PayloadVersion</key>
              <integer>1</integer>
            </dict>
            </plist>
        """.trimIndent()
        return plist.toByteArray(Charsets.UTF_8)
    }

    fun sanitizePackageName(raw: String): String {
        val parts = raw.lowercase()
            .split('.')
            .map { seg -> seg.replace(Regex("[^a-z0-9_]"), "") }
            .filter { it.isNotEmpty() }
            .map { seg -> if (seg.first().isDigit()) "a$seg" else seg }
        return when {
            parts.size >= 2 -> parts.joinToString(".")
            parts.size == 1 -> "com.webforge.${parts[0]}"
            else -> "com.webforge.app"
        }
    }

    private fun normalizeHex(raw: String): String {
        val c = raw.trim().let { if (it.startsWith("#")) it else "#$it" }
        return if (Regex("^#[0-9A-Fa-f]{6}$").matches(c)) c else "#0C0A20"
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
