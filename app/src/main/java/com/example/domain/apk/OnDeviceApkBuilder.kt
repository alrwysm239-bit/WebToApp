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

        val configJson = buildConfigJson(safeAppName, config).toByteArray(Charsets.UTF_8)

        val entries = mutableListOf(
            ApkSignerAndPackager.ZipInputEntry("AndroidManifest.xml", manifestBytes, storeUncompressed = false),
            ApkSignerAndPackager.ZipInputEntry("resources.arsc", resourcesBytes, storeUncompressed = true, alignBytes = 4),
            ApkSignerAndPackager.ZipInputEntry("classes.dex", dexBytes, storeUncompressed = false),
            ApkSignerAndPackager.ZipInputEntry("res/mipmap-xxxhdpi-v4/ic_launcher.png", icon512, storeUncompressed = true, alignBytes = 4),
            ApkSignerAndPackager.ZipInputEntry("assets/splash_icon.png", splash256, storeUncompressed = true, alignBytes = 4),
            ApkSignerAndPackager.ZipInputEntry("assets/app_config.json", configJson, storeUncompressed = false),
        )

        if (config.sourceMode == AppSourceMode.HTML) {
            val nonBlankHtml = html.ifBlank { HtmlToolkit.defaultStarterHtml(isArabic = true) }
            val hasFullStructure = nonBlankHtml.contains("<html", ignoreCase = true) &&
                nonBlankHtml.contains("viewport", ignoreCase = true)
            val preparedHtml = if (hasFullStructure) {
                nonBlankHtml
            } else {
                HtmlToolkit.injectViewportSeoAndIcon(
                    rawHtml = nonBlankHtml,
                    title = safeAppName,
                    description = config.seoDescription,
                    themeColorHex = config.themeColorHex,
                    hasFavicon = false,
                )
            }
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
     * onto any iPhone/iPad Home Screen as a fullscreen WebClip App with custom Icon & Name.
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
                val singleHtml = injectIosRuntimeShellHtml(
                    CodeLinterAndAudit.bundleSingleSelfContainedHtml(html, assets),
                    safeName,
                    config,
                )
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
                  <true/>
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

    /**
     * Generates a standards-structured iOS .ipa archive (Payload/<AppSlug>.app/...) containing
     * Info.plist, PkgInfo, multi-resolution AppIcons, embedded HTML/CSS/JS web bundle with
     * App Lock + Ads injection, ready for AltStore / Sideloadly / Cloud macOS IPA re-signing.
     */
    fun buildIosIpaArchive(
        context: Context,
        projectName: String,
        html: String,
        assets: List<ProjectAsset>,
        config: AppExportConfig,
    ): ByteArray {
        val safeName = config.appName.ifBlank { projectName }.trim().ifBlank { "WebForge App" }
        val appSlug = HtmlToolkit.slugify(safeName).replace("-", "").ifBlank { "WebForgeApp" }
        val pkg = sanitizePackageName(config.packageName.ifBlank { "com.webforge.$appSlug" })
        val iconSource = ZipArchiveManager.resolveIconPngBytes(context, config.customIconBase64)
        val appDir = "Payload/$appSlug.app"

        val orientationList = when (config.orientation) {
            AppOrientation.PORTRAIT -> "<string>UIInterfaceOrientationPortrait</string>"
            AppOrientation.LANDSCAPE -> "<string>UIInterfaceOrientationLandscapeLeft</string>\n    <string>UIInterfaceOrientationLandscapeRight</string>"
            AppOrientation.AUTO -> "<string>UIInterfaceOrientationPortrait</string>\n    <string>UIInterfaceOrientationLandscapeLeft</string>\n    <string>UIInterfaceOrientationLandscapeRight</string>"
        }
        val admobPlist = if (config.admobAppId.isNotBlank()) {
            "\n  <key>GADApplicationIdentifier</key>\n  <string>${escapeXml(config.admobAppId.trim())}</string>"
        } else ""
        val urlSchemePlist = if (config.deepLinkScheme.isNotBlank()) {
            """
            
              <key>CFBundleURLTypes</key>
              <array>
                <dict>
                  <key>CFBundleURLSchemes</key>
                  <array><string>${escapeXml(config.deepLinkScheme.trim())}</string></array>
                </dict>
              </array>
            """.trimIndent()
        } else ""

        val infoPlist = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
              <key>CFBundleDevelopmentRegion</key>
              <string>en</string>
              <key>CFBundleDisplayName</key>
              <string>${escapeXml(safeName)}</string>
              <key>CFBundleExecutable</key>
              <string>$appSlug</string>
              <key>CFBundleIdentifier</key>
              <string>$pkg</string>
              <key>CFBundleInfoDictionaryVersion</key>
              <string>6.0</string>
              <key>CFBundleName</key>
              <string>${escapeXml(safeName)}</string>
              <key>CFBundlePackageType</key>
              <string>APPL</string>
              <key>CFBundleShortVersionString</key>
              <string>${escapeXml(config.versionName.ifBlank { "1.0.0" })}</string>
              <key>CFBundleVersion</key>
              <string>${config.versionCode.coerceAtLeast(1)}</string>
              <key>LSRequiresIPhoneOS</key>
              <true/>
              <key>UIStatusBarHidden</key>
              <${config.fullscreen}/>
              <key>UIRequiresFullScreen</key>
              <true/>
              <key>UISupportedInterfaceOrientations</key>
              <array>
                $orientationList
              </array>$admobPlist$urlSchemePlist
            </dict>
            </plist>
        """.trimIndent()

        val entryHtml = if (config.sourceMode == AppSourceMode.URL && config.sourceUrl.isNotBlank()) {
            val u = config.sourceUrl.trim().let { if (it.startsWith("http")) it else "https://$it" }
            val urlWrapper = """
                <!DOCTYPE html><html><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1.0,viewport-fit=cover">
                <title>${escapeXml(safeName)}</title>
                <style>html,body,iframe{margin:0;padding:0;width:100%;height:100%;border:0;background:${normalizeHex(config.themeColorHex)}}</style>
                </head><body>
                <iframe src="${escapeXml(u)}" allow="camera;microphone;geolocation;fullscreen"></iframe>
                </body></html>
            """.trimIndent()
            injectIosRuntimeShellHtml(urlWrapper, safeName, config)
        } else {
            injectIosRuntimeShellHtml(html.ifBlank { HtmlToolkit.defaultStarterHtml(isArabic = true) }, safeName, config)
        }

        val files = linkedMapOf<String, ByteArray>()
        files["$appDir/Info.plist"] = infoPlist.toByteArray(Charsets.UTF_8)
        files["$appDir/PkgInfo"] = "APPL????".toByteArray(Charsets.US_ASCII)
        files["$appDir/WebForgeConfig.json"] = buildConfigJson(safeName, config).toByteArray(Charsets.UTF_8)
        files["$appDir/AppIcon60x60@2x.png"] = ZipArchiveManager.resizePng(iconSource, 120)
        files["$appDir/AppIcon60x60@3x.png"] = ZipArchiveManager.resizePng(iconSource, 180)
        files["$appDir/AppIcon76x76@2x~ipad.png"] = ZipArchiveManager.resizePng(iconSource, 152)
        files["$appDir/www/index.html"] = entryHtml.toByteArray(Charsets.UTF_8)
        files["$appDir/www/favicon.png"] = ZipArchiveManager.resizePng(iconSource, 64)
        assets.forEach { asset ->
            ZipArchiveManager.assetToBytes(asset)?.let { bytes ->
                files["$appDir/www/${asset.path.trimStart('/')}"] = bytes
            }
        }
        files["iTunesArtwork"] = ZipArchiveManager.resizePng(iconSource, 512)
        return ZipArchiveManager.zipFileMap(files)
    }

    fun injectIosRuntimeShellHtml(rawHtml: String, appName: String, config: AppExportConfig): String {
        var out = HtmlToolkit.injectViewportSeoAndIcon(
            rawHtml = rawHtml,
            title = appName,
            description = config.seoDescription,
            themeColorHex = config.themeColorHex,
            hasFavicon = false,
        )
        val iosMeta = """
            <meta name="apple-mobile-web-app-capable" content="yes">
            <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
            <meta name="apple-mobile-web-app-title" content="${escapeXml(appName)}">
        """.trimIndent()
        out = if (out.contains("</head>", ignoreCase = true)) {
            out.replaceFirst(Regex("</head>", RegexOption.IGNORE_CASE), "$iosMeta\n</head>")
        } else "$iosMeta\n$out"

        val extras = StringBuilder()
        if (config.appLockEnabled && config.appLockPassword.isNotBlank()) {
            val safePass = JSONObject.quote(config.appLockPassword)
            val safeTitle = escapeXml(config.appLockTitle.ifBlank { "$appName 🔒" })
            extras.append(
                """
                <div id="wf-lock-gate" style="position:fixed;inset:0;z-index:2147483647;background:#0C0A20;color:#F4F6FF;display:flex;align-items:center;justify-content:center;font-family:system-ui,sans-serif;padding:24px;">
                  <div style="width:100%;max-width:320px;background:#1C1840;border:1px solid #5B3DF5;border-radius:20px;padding:24px;text-align:center;">
                    <h2 style="margin:0 0 8px 0;font-size:20px;">$safeTitle</h2>
                    <p style="margin:0 0 16px 0;color:#A8AED8;font-size:13px;">أدخل كلمة المرور / Enter Password</p>
                    <input id="wf-lock-input" type="password" placeholder="••••••" style="width:100%;box-sizing:border-box;padding:12px;border-radius:12px;border:1px solid #00C6FF;background:#0C0A20;color:#fff;font-size:16px;text-align:center;margin-bottom:10px;"/>
                    <div id="wf-lock-err" style="color:#FF5C7C;font-size:12px;min-height:18px;margin-bottom:8px;"></div>
                    <button onclick="(function(){var v=document.getElementById('wf-lock-input').value;if(v===$safePass){document.getElementById('wf-lock-gate').remove();}else{document.getElementById('wf-lock-err').textContent='كلمة المرور غير صحيحة';}})()" style="width:100%;padding:12px;border:0;border-radius:12px;font-weight:700;font-size:15px;color:#0C0A20;background:linear-gradient(90deg,#00C6FF,#34E0A1);">فتح / Unlock</button>
                  </div>
                </div>
                """.trimIndent(),
            )
        }
        if (config.bottomNavBarEnabled) {
            extras.append(
                """
                <div style="position:fixed;left:0;right:0;bottom:${if (config.adsEnabled && config.adPlacementBottomBanner) "56px" else "0"};z-index:2147483000;height:48px;background:#0C0A20ee;border-top:1px solid #2A245C;display:flex;align-items:center;justify-content:space-around;font-family:system-ui,sans-serif;font-size:12px;">
                  <button onclick="history.back()" style="background:none;border:0;color:#00C6FF;font-weight:700;">◀ رجوع</button>
                  <button onclick="location.reload()" style="background:none;border:0;color:#F4F6FF;font-weight:700;">↻ تحديث</button>
                  <button onclick="if(navigator.share){navigator.share({title:document.title,url:location.href});}" style="background:none;border:0;color:#34E0A1;font-weight:700;">↗ مشاركة</button>
                </div>
                """.trimIndent(),
            )
        }
        if (config.adsEnabled && config.adPlacementBottomBanner) {
            val customAd = config.customHtmlAdScript.trim()
            val adContent = customAd.ifBlank {
                val net = when {
                    config.admobBannerId.isNotBlank() -> "AdMob (${escapeXml(config.admobBannerId)})"
                    config.metaAudienceBannerId.isNotBlank() -> "Meta Ads (${escapeXml(config.metaAudienceBannerId)})"
                    config.unityGameId.isNotBlank() -> "Unity Ads (${escapeXml(config.unityGameId)})"
                    config.appLovinSdkKey.isNotBlank() -> "AppLovin"
                    else -> "Ad Slot"
                }
                "<span style='font-size:11px;color:#A8AED8;font-family:system-ui,sans-serif'>AD • $net</span>"
            }
            extras.append(
                """
                <div id="wf-ios-ad-banner" style="position:fixed;left:0;right:0;bottom:0;z-index:2147483001;min-height:52px;background:#141132;border-top:1px solid #2A245C;display:flex;align-items:center;justify-content:center;overflow:hidden;">
                  $adContent
                </div>
                """.trimIndent(),
            )
        }
        if (extras.isNotEmpty()) {
            out = if (out.contains("</body>", ignoreCase = true)) {
                out.replaceFirst(Regex("</body>", RegexOption.IGNORE_CASE), "${extras}\n</body>")
            } else "$out\n$extras"
        }
        return out
    }

    private fun buildConfigJson(safeAppName: String, config: AppExportConfig): String = JSONObject().apply {
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
        put("appLockEnabled", config.appLockEnabled)
        put("appLockPassword", config.appLockPassword)
        put("appLockTitle", config.appLockTitle)
        put("adsEnabled", config.adsEnabled)
        put("admobAppId", config.admobAppId.trim())
        put("admobBannerId", config.admobBannerId.trim())
        put("admobInterstitialId", config.admobInterstitialId.trim())
        put("metaAudienceBannerId", config.metaAudienceBannerId.trim())
        put("unityGameId", config.unityGameId.trim())
        put("appLovinSdkKey", config.appLovinSdkKey.trim())
        put("customHtmlAdScript", config.customHtmlAdScript)
        put("adPlacementBottomBanner", config.adPlacementBottomBanner)
        put("blockAdsTrackers", config.blockAdsTrackers)
        put("preventScreenshots", config.preventScreenshots)
        put("bottomNavBarEnabled", config.bottomNavBarEnabled)
        put("exitDialogEnabled", config.exitDialogEnabled)
        put("deepLinkScheme", config.deepLinkScheme.trim())
        put("aboutAppText", config.aboutAppText.trim())
    }.toString(2)

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
