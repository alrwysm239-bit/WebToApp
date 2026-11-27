package com.example.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.example.R
import com.example.data.model.AppExportConfig
import com.example.data.model.BackButtonBehavior
import com.example.data.model.ProjectAsset
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ImportedProjectResult(
    val suggestedName: String,
    val entryHtml: String,
    val assets: List<ProjectAsset>,
)

object ZipArchiveManager {

    private val textExtensions = setOf("html", "htm", "css", "js", "mjs", "json", "svg", "txt", "xml", "webmanifest")

    fun importFromUri(context: Context, uri: Uri): ImportedProjectResult {
        val fileName = resolveDisplayName(context, uri) ?: "imported_project"
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Unable to read selected file")

        val isZip = fileName.endsWith(".zip", ignoreCase = true) ||
            (rawBytes.size >= 4 && rawBytes[0] == 0x50.toByte() && rawBytes[1] == 0x4B.toByte())

        if (!isZip) {
            val htmlText = rawBytes.toString(Charsets.UTF_8)
            val baseName = fileName.substringBeforeLast('.').ifBlank { "Web Project" }
            val title = HtmlToolkit.extractTitle(htmlText) ?: baseName
            return ImportedProjectResult(
                suggestedName = title,
                entryHtml = htmlText,
                assets = emptyList(),
            )
        }

        val allFiles = mutableListOf<ProjectAsset>()
        ZipInputStream(ByteArrayInputStream(rawBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val safePath = sanitizeZipPath(entry.name)
                    if (safePath != null && !safePath.startsWith("__MACOSX/")) {
                        val bytes = zis.readBytes()
                        val ext = safePath.substringAfterLast('.', "").lowercase(Locale.US)
                        val mime = guessMimeType(ext)
                        if (ext in textExtensions) {
                            allFiles.add(
                                ProjectAsset(
                                    path = safePath,
                                    mimeType = mime,
                                    textContent = bytes.toString(Charsets.UTF_8),
                                    sizeBytes = bytes.size,
                                ),
                            )
                        } else {
                            allFiles.add(
                                ProjectAsset(
                                    path = safePath,
                                    mimeType = mime,
                                    base64Content = Base64.encodeToString(bytes, Base64.NO_WRAP),
                                    sizeBytes = bytes.size,
                                ),
                            )
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val normalizedFiles = stripCommonRootPrefix(allFiles)
        val htmlCandidate = normalizedFiles.firstOrNull { it.path.equals("index.html", ignoreCase = true) }
            ?: normalizedFiles.filter {
                it.path.endsWith(".html", ignoreCase = true) || it.path.endsWith(".htm", ignoreCase = true)
            }.minByOrNull { it.path.count { c -> c == '/' } }

        val entryHtml = htmlCandidate?.textContent
            ?: HtmlToolkit.defaultStarterHtml(isArabic = true)
        val remainingAssets = if (htmlCandidate != null) {
            normalizedFiles.filterNot { it.path == htmlCandidate.path }
        } else {
            normalizedFiles
        }

        val suggestedName = HtmlToolkit.extractTitle(entryHtml)
            ?: fileName.substringBeforeLast('.').ifBlank { "Imported Site" }

        return ImportedProjectResult(
            suggestedName = suggestedName,
            entryHtml = entryHtml,
            assets = remainingAssets,
        )
    }

    fun readAssetFromUri(context: Context, uri: Uri): ProjectAsset {
        val fileName = resolveDisplayName(context, uri) ?: "asset_${System.currentTimeMillis()}"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Unable to read file")
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        val mime = guessMimeType(ext)
        return if (ext in textExtensions) {
            ProjectAsset(fileName, mime, textContent = bytes.toString(Charsets.UTF_8), sizeBytes = bytes.size)
        } else {
            ProjectAsset(fileName, mime, base64Content = Base64.encodeToString(bytes, Base64.NO_WRAP), sizeBytes = bytes.size)
        }
    }

    fun buildWebsiteZip(
        context: Context,
        projectName: String,
        html: String,
        assets: List<ProjectAsset>,
        config: AppExportConfig,
        includePwa: Boolean,
    ): ByteArray {
        val entries = buildWebsiteFileMap(context, projectName, html, assets, config, includePwa)
        return zipFileMap(entries)
    }

    fun buildWebsiteFileMap(
        context: Context,
        projectName: String,
        html: String,
        assets: List<ProjectAsset>,
        config: AppExportConfig,
        includePwa: Boolean,
    ): LinkedHashMap<String, ByteArray> {
        val files = linkedMapOf<String, ByteArray>()
        val iconSource = resolveIconPngBytes(context, config.customIconBase64)
        val domainBase = config.customDomain.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { if (it.startsWith("http")) it.trimEnd('/') else "https://${it.trimEnd('/')}" }
            ?: "https://${HtmlToolkit.slugify(projectName)}.web.app"

        var finalHtml = HtmlToolkit.injectViewportSeoAndIcon(
            rawHtml = html,
            title = config.appName.ifBlank { projectName },
            description = config.seoDescription,
            themeColorHex = config.themeColorHex,
            hasFavicon = true,
        )
        if (includePwa) {
            finalHtml = HtmlToolkit.injectPwaLinks(finalHtml)
        }

        val htmlBytes = finalHtml.toByteArray(Charsets.UTF_8)
        files["index.html"] = htmlBytes
        files["404.html"] = htmlBytes
        files["favicon.png"] = resizePng(iconSource, 64)
        files["robots.txt"] = "User-agent: *\nAllow: /\nSitemap: $domainBase/sitemap.xml\n".toByteArray(Charsets.UTF_8)
        files["sitemap.xml"] = """
            <?xml version="1.0" encoding="UTF-8"?>
            <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
              <url>
                <loc>$domainBase/</loc>
                <priority>1.0</priority>
              </url>
            </urlset>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        if (config.customDomain.isNotBlank()) {
            val hostOnly = config.customDomain.trim()
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore('/')
            files["CNAME"] = hostOnly.toByteArray(Charsets.UTF_8)
        }

        if (config.twaHost.isNotBlank() && config.twaSha256.isNotBlank()) {
            val pkg = config.packageName.ifBlank { "com.webforge.app" }
            files[".well-known/assetlinks.json"] = """
                [{
                  "relation": ["delegate_permission/common.handle_all_urls"],
                  "target": {
                    "namespace": "android_app",
                    "package_name": "$pkg",
                    "sha256_cert_fingerprints": ["${config.twaSha256.trim()}"]
                  }
                }]
            """.trimIndent().toByteArray(Charsets.UTF_8)
        }

        assets.forEach { asset ->
            val bytes = assetToBytes(asset)
            if (bytes != null) {
                files[asset.path] = bytes
            }
        }

        if (includePwa) {
            listOf(72, 96, 128, 144, 152, 192, 384, 512).forEach { sz ->
                files["icon-$sz.png"] = resizePng(iconSource, sz)
            }
            files["manifest.webmanifest"] = buildPwaManifest(config, projectName).toByteArray(Charsets.UTF_8)
            val cacheAssets = listOf("./", "./index.html", "./manifest.webmanifest", "./icon-192.png", "./icon-512.png") +
                assets.map { "./${it.path}" }
            files["sw.js"] = buildServiceWorker(projectName, cacheAssets).toByteArray(Charsets.UTF_8)
        }

        files["firebase.json"] = """
            {
              "hosting": {
                "public": ".",
                "ignore": ["firebase.json", "**/.*", "**/node_modules/**"]
              }
            }
        """.trimIndent().toByteArray(Charsets.UTF_8)

        return files
    }

    fun buildAndroidWebViewProjectZip(
        context: Context,
        projectName: String,
        html: String,
        assets: List<ProjectAsset>,
        config: AppExportConfig,
    ): ByteArray {
        val safeAppName = config.appName.ifBlank { projectName }.trim()
        val packageName = config.packageName.ifBlank {
            "com.webforge.${HtmlToolkit.toSafePackageSegment(safeAppName)}"
        }
        val packagePath = packageName.replace('.', '/')
        val themeColor = normalizeHexColor(config.themeColorHex)
        val statusColor = normalizeHexColor(config.statusBarColorHex)
        val iconSource = resolveIconPngBytes(context, config.customIconBase64)

        val finalHtml = HtmlToolkit.injectViewportSeoAndIcon(
            rawHtml = html,
            title = safeAppName,
            description = config.seoDescription,
            themeColorHex = themeColor,
            hasFavicon = true,
        )

        val files = linkedMapOf<String, ByteArray>()

        // Capacitor metadata + Web assets so developers can use either native Gradle or Capacitor CLI
        files["capacitor.config.json"] = """
            {
              "appId": "$packageName",
              "appName": "${escapeJson(safeAppName)}",
              "webDir": "app/src/main/assets/www",
              "bundledWebRuntime": false,
              "android": {
                "backgroundColor": "$themeColor"
              }
            }
        """.trimIndent().toByteArray(Charsets.UTF_8)

        files["settings.gradle.kts"] = """
            pluginManagement {
                repositories { google(); mavenCentral(); gradlePluginPortal() }
            }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories { google(); mavenCentral() }
            }
            rootProject.name = "${escapeKotlinString(safeAppName)}"
            include(":app")
        """.trimIndent().toByteArray(Charsets.UTF_8)

        files["build.gradle.kts"] = """
            plugins {
                id("com.android.application") version "8.7.3" apply false
                id("org.jetbrains.kotlin.android") version "2.0.21" apply false
            }
        """.trimIndent().toByteArray(Charsets.UTF_8)

        files["gradle.properties"] = """
            org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
            android.useAndroidX=true
            kotlin.code.style=official
        """.trimIndent().toByteArray(Charsets.UTF_8)

        files["gradle/wrapper/gradle-wrapper.properties"] = """
            distributionBase=GRADLE_USER_HOME
            distributionPath=wrapper/dists
            distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip
            zipStoreBase=GRADLE_USER_HOME
            zipStorePath=wrapper/dists
        """.trimIndent().toByteArray(Charsets.UTF_8)

        files["app/build.gradle.kts"] = """
            plugins {
                id("com.android.application")
                id("org.jetbrains.kotlin.android")
            }

            android {
                namespace = "$packageName"
                compileSdk = 35

                defaultConfig {
                    applicationId = "$packageName"
                    minSdk = 24
                    targetSdk = 35
                    versionCode = ${config.versionCode}
                    versionName = "${escapeKotlinString(config.versionName)}"
                }

                buildTypes {
                    release {
                        isMinifyEnabled = false
                        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                    }
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                kotlinOptions { jvmTarget = "17" }
            }

            dependencies {
                implementation("androidx.core:core-ktx:1.15.0")
                implementation("androidx.appcompat:appcompat:1.7.0")
                implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
                implementation("androidx.webkit:webkit:1.12.1")
            }
        """.trimIndent().toByteArray(Charsets.UTF_8)

        files["app/proguard-rules.pro"] = "# WebForge ProGuard\n".toByteArray(Charsets.UTF_8)

        val permTags = config.permissions.joinToString("\n") { p ->
            when (p) {
                "CAMERA" -> """    <uses-permission android:name="android.permission.CAMERA" />"""
                "LOCATION" -> """    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />"""
                "VIBRATE" -> """    <uses-permission android:name="android.permission.VIBRATE" />"""
                "ACCESS_NETWORK_STATE" -> """    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />"""
                else -> """    <uses-permission android:name="android.permission.INTERNET" />"""
            }
        }
        val admobMeta = if (config.admobAppId.isNotBlank()) {
            """
                <meta-data
                    android:name="com.google.android.gms.ads.APPLICATION_ID"
                    android:value="${escapeXml(config.admobAppId.trim())}" />
            """.trimIndent()
        } else ""
        val deepLinkFilter = if (config.deepLinkScheme.isNotBlank()) {
            """
                <intent-filter>
                    <action android:name="android.intent.action.VIEW" />
                    <category android:name="android.intent.category.DEFAULT" />
                    <category android:name="android.intent.category.BROWSABLE" />
                    <data android:scheme="${escapeXml(config.deepLinkScheme.trim())}" />
                </intent-filter>
            """.trimIndent()
        } else ""

        val orientationAttr = if (config.orientation.manifestValue == "unspecified") "" else
            "\n            android:screenOrientation=\"${config.orientation.manifestValue}\""

        files["app/src/main/AndroidManifest.xml"] = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
            $permTags

                <application
                    android:allowBackup="true"
                    android:icon="@mipmap/ic_launcher"
                    android:roundIcon="@mipmap/ic_launcher_round"
                    android:label="@string/app_name"
                    android:supportsRtl="true"
                    android:theme="@style/Theme.WebForgeApp">
                    $admobMeta
                    <activity
                        android:name=".MainActivity"
                        android:exported="true"
                        android:configChanges="orientation|screenSize|keyboardHidden"$orientationAttr>
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                        $deepLinkFilter
                    </activity>
                </application>
            </manifest>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        files["app/src/main/res/values/strings.xml"] = """
            <resources>
                <string name="app_name">${escapeXml(safeAppName)}</string>
            </resources>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        files["app/src/main/res/values/colors.xml"] = """
            <resources>
                <color name="splash_color">$themeColor</color>
                <color name="status_bar_color">$statusColor</color>
            </resources>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        files["app/src/main/res/values/themes.xml"] = """
            <resources>
                <style name="Theme.WebForgeApp" parent="Theme.AppCompat.DayNight.NoActionBar">
                    <item name="android:statusBarColor">@color/status_bar_color</item>
                    <item name="android:navigationBarColor">@color/splash_color</item>
                    <item name="android:windowBackground">@color/splash_color</item>
                </style>
            </resources>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val confirmExit = config.backButtonBehavior == BackButtonBehavior.CONFIRM_EXIT || config.exitDialogEnabled
        val launchTargetUrl = if (config.sourceMode == com.example.data.model.AppSourceMode.URL && config.sourceUrl.isNotBlank()) {
            val u = config.sourceUrl.trim()
            if (u.startsWith("http")) u else "https://$u"
        } else {
            "https://appassets.androidplatform.net/assets/www/index.html"
        }

        files["app/src/main/java/$packagePath/MainActivity.kt"] = """
            package $packageName

            import android.annotation.SuppressLint
            import android.graphics.Color
            import android.os.Bundle
            import android.text.InputType
            import android.view.Gravity
            import android.view.View
            import android.view.WindowManager
            import android.webkit.WebChromeClient
            import android.webkit.WebResourceRequest
            import android.webkit.WebResourceResponse
            import android.webkit.WebSettings
            import android.webkit.WebView
            import android.webkit.WebViewClient
            import android.widget.EditText
            import android.widget.FrameLayout
            import android.widget.ImageView
            import android.widget.Toast
            import androidx.activity.OnBackPressedCallback
            import androidx.appcompat.app.AlertDialog
            import androidx.appcompat.app.AppCompatActivity
            import androidx.core.view.WindowCompat
            import androidx.core.view.WindowInsetsCompat
            import androidx.core.view.WindowInsetsControllerCompat
            import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
            import androidx.webkit.WebViewAssetLoader

            class MainActivity : AppCompatActivity() {

                private lateinit var webView: WebView
                private var lastBackPressMs = 0L

                @SuppressLint("SetJavaScriptEnabled")
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)

                    val bgColor = Color.parseColor("$themeColor")
                    val statusColor = Color.parseColor("$statusColor")
                    window.statusBarColor = statusColor

                    if (${config.keepScreenOn}) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    if (${config.preventScreenshots}) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                    if (${config.fullscreen}) {
                        WindowCompat.setDecorFitsSystemWindows(window, false)
                        WindowInsetsControllerCompat(window, window.decorView).apply {
                            hide(WindowInsetsCompat.Type.systemBars())
                            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        }
                    }

                    val root = FrameLayout(this).apply { setBackgroundColor(bgColor) }
                    webView = WebView(this).apply {
                        setBackgroundColor(bgColor)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                    }

                    val swipeRefresh = SwipeRefreshLayout(this).apply {
                        isEnabled = ${config.pullToRefresh}
                        setOnRefreshListener { webView.reload() }
                        addView(webView)
                    }

                    val splashOverlay = FrameLayout(this).apply {
                        setBackgroundColor(bgColor)
                        val iconView = ImageView(this@MainActivity).apply { setImageResource(R.drawable.app_icon) }
                        val sizePx = (96 * resources.displayMetrics.density).toInt()
                        addView(iconView, FrameLayout.LayoutParams(sizePx, sizePx, Gravity.CENTER))
                    }

                    val assetLoader = WebViewAssetLoader.Builder()
                        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
                        .build()

                    webView.webChromeClient = WebChromeClient()
                    webView.webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                            assetLoader.shouldInterceptRequest(request.url)

                        override fun onPageFinished(view: WebView?, url: String?) {
                            swipeRefresh.isRefreshing = false
                            splashOverlay.animate().alpha(0f).setDuration(260).withEndAction {
                                splashOverlay.visibility = View.GONE
                            }.start()
                        }
                    }

                    root.addView(swipeRefresh)
                    root.addView(splashOverlay)
                    setContentView(root)

                    if (${config.appLockEnabled && config.appLockPassword.isNotBlank()}) {
                        promptUnlock("${escapeKotlinString(config.appLockPassword)}")
                    }

                    webView.loadUrl("${escapeKotlinString(launchTargetUrl)}")

                    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                        override fun handleOnBackPressed() {
                            if (webView.canGoBack()) {
                                webView.goBack()
                            } else if (${config.exitDialogEnabled}) {
                                AlertDialog.Builder(this@MainActivity)
                                    .setTitle("${escapeKotlinString(safeAppName)}")
                                    .setMessage("Exit application?")
                                    .setPositiveButton("Exit") { _, _ -> finish() }
                                    .setNegativeButton("Stay", null)
                                    .show()
                            } else if ($confirmExit) {
                                val now = System.currentTimeMillis()
                                if (now - lastBackPressMs < 2000L) {
                                    isEnabled = false
                                    onBackPressedDispatcher.onBackPressed()
                                } else {
                                    lastBackPressMs = now
                                    Toast.makeText(this@MainActivity, "Press back again to exit", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                isEnabled = false
                                onBackPressedDispatcher.onBackPressed()
                            }
                        }
                    })
                }

                private fun promptUnlock(expected: String) {
                    val input = EditText(this).apply {
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    }
                    AlertDialog.Builder(this)
                        .setTitle("${escapeKotlinString(safeAppName)}")
                        .setMessage("Enter password to unlock")
                        .setView(input)
                        .setCancelable(false)
                        .setPositiveButton("Unlock") { _, _ ->
                            if (input.text?.toString() != expected) promptUnlock(expected)
                        }
                        .show()
                }
            }
        """.trimIndent().toByteArray(Charsets.UTF_8)

        files["app/src/main/res/drawable/app_icon.png"] = resizePng(iconSource, 256)
        mapOf("mdpi" to 48, "hdpi" to 72, "xhdpi" to 96, "xxhdpi" to 144, "xxxhdpi" to 192).forEach { (d, sz) ->
            val png = resizePng(iconSource, sz)
            files["app/src/main/res/mipmap-$d/ic_launcher.png"] = png
            files["app/src/main/res/mipmap-$d/ic_launcher_round.png"] = png
        }

        val wwwHtml = if (config.adsEnabled && config.customHtmlAdScript.isNotBlank()) {
            com.example.domain.apk.OnDeviceApkBuilder.injectIosRuntimeShellHtml(finalHtml, safeAppName, config)
        } else finalHtml
        files["app/src/main/assets/www/index.html"] = wwwHtml.toByteArray(Charsets.UTF_8)
        files["app/src/main/assets/www/favicon.png"] = resizePng(iconSource, 64)
        assets.forEach { asset ->
            assetToBytes(asset)?.let { files["app/src/main/assets/www/${asset.path}"] = it }
        }

        // GitHub Actions building BOTH APK and Google Play AAB automatically
        files[".github/workflows/build-apk.yml"] = """
            name: Build Android APK & Play Store AAB
            on:
              push:
                branches: [ main, master ]
              workflow_dispatch:
            jobs:
              build:
                runs-on: ubuntu-latest
                steps:
                  - uses: actions/checkout@v4
                  - uses: actions/setup-java@v4
                    with:
                      distribution: temurin
                      java-version: 17
                  - uses: gradle/actions/setup-gradle@v4
                    with:
                      gradle-version: 8.11.1
                  - run: gradle assembleDebug bundleRelease
                  - uses: actions/upload-artifact@v4
                    with:
                      name: ${HtmlToolkit.slugify(safeAppName)}-apk-and-aab
                      path: |
                        app/build/outputs/apk/debug/*.apk
                        app/build/outputs/bundle/release/*.aab
        """.trimIndent().toByteArray(Charsets.UTF_8)

        files["README.md"] = """
            # $safeAppName — Android APK & Google Play AAB Project
            Generated by **WebForge Studio**.
            - Push to GitHub to trigger `.github/workflows/build-apk.yml` for automatic **Google Play `.aab`** + **`.apk`** builds!
            - Ads Config: AdMob=${config.admobAppId}, Meta=${config.metaAudienceBannerId}, Unity=${config.unityGameId}, AppLovin=${config.appLovinSdkKey}
        """.trimIndent().toByteArray(Charsets.UTF_8)

        return zipFileMap(files)
    }

    fun buildPwaManifest(config: AppExportConfig, projectName: String): String {
        val name = config.appName.ifBlank { projectName }
        val shortName = name.take(12)
        val color = normalizeHexColor(config.themeColorHex)
        val orientation = when (config.orientation.key) {
            "portrait" -> "portrait"
            "landscape" -> "landscape"
            else -> "any"
        }
        val iconsJson = listOf(72, 96, 128, 144, 152, 192, 384, 512).joinToString(",\n                ") { sz ->
            """{"src": "icon-$sz.png", "sizes": "${sz}x${sz}", "type": "image/png", "purpose": "any maskable"}"""
        }
        return """
            {
              "name": "${escapeJson(name)}",
              "short_name": "${escapeJson(shortName)}",
              "description": "${escapeJson(config.seoDescription)}",
              "start_url": "./index.html",
              "display": "standalone",
              "orientation": "$orientation",
              "background_color": "$color",
              "theme_color": "$color",
              "icons": [
                $iconsJson
              ]
            }
        """.trimIndent()
    }

    fun buildServiceWorker(projectName: String, urls: List<String>): String {
        val cacheName = "webforge-${HtmlToolkit.slugify(projectName)}-v1"
        val arrayEntries = urls.distinct().joinToString(",\n  ") { "'$it'" }
        return """
            const CACHE_NAME = '$cacheName';
            const ASSETS = [
              $arrayEntries
            ];

            self.addEventListener('install', (event) => {
              event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.addAll(ASSETS)));
              self.skipWaiting();
            });

            self.addEventListener('activate', (event) => {
              event.waitUntil(
                caches.keys().then((keys) =>
                  Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
                )
              );
              self.clients.claim();
            });

            self.addEventListener('fetch', (event) => {
              event.respondWith(
                caches.match(event.request).then((cached) => cached || fetch(event.request))
              );
            });
        """.trimIndent()
    }

    fun resolveIconPngBytes(context: Context, customIconBase64: String?): ByteArray {
        if (!customIconBase64.isNullOrBlank()) {
            val decoded = runCatching { Base64.decode(customIconBase64, Base64.DEFAULT) }.getOrNull()
            if (decoded != null && decoded.isNotEmpty()) {
                val bmp = BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
                if (bmp != null) {
                    val out = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    return out.toByteArray()
                }
            }
        }
        val defaultBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.webforge_icon_default)
        val out = ByteArrayOutputStream()
        defaultBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    fun encodeImageUriToBase64Png(context: Context, uri: Uri, maxSize: Int = 512): String? {
        return runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val scaled = Bitmap.createScaledBitmap(original, maxSize, maxSize, true)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, 95, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
    }

    fun saveTempExportFile(context: Context, fileName: String, bytes: ByteArray): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        return file
    }

    fun assetToBytes(asset: ProjectAsset): ByteArray? = when {
        asset.textContent != null -> asset.textContent.toByteArray(Charsets.UTF_8)
        asset.base64Content != null -> runCatching { Base64.decode(asset.base64Content, Base64.DEFAULT) }.getOrNull()
        else -> null
    }

    fun resizePng(sourcePng: ByteArray, size: Int): ByteArray {
        val bmp = BitmapFactory.decodeByteArray(sourcePng, 0, sourcePng.size) ?: return sourcePng
        val scaled = Bitmap.createScaledBitmap(bmp, size, size, true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    fun zipFileMap(entries: Map<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            entries.forEach { (path, data) ->
                zos.putNextEntry(ZipEntry(path))
                zos.write(data)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun sanitizeZipPath(rawPath: String): String? {
        val normalized = rawPath.replace("\\", "/").trimStart('/')
        if (normalized.isEmpty() || normalized.contains("..")) return null
        return normalized
    }

    private fun stripCommonRootPrefix(assets: List<ProjectAsset>): List<ProjectAsset> {
        if (assets.isEmpty()) return assets
        val firstSegments = assets.mapNotNull { it.path.substringBefore('/', "").takeIf { _ -> it.path.contains('/') } }
        if (firstSegments.size == assets.size && firstSegments.distinct().size == 1) {
            val prefix = "${firstSegments.first()}/"
            return assets.map { it.copy(path = it.path.removePrefix(prefix)) }
        }
        return assets
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }

    private fun guessMimeType(ext: String): String = when (ext) {
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "js", "mjs" -> "application/javascript"
        "json", "webmanifest" -> "application/json"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "ico" -> "image/x-icon"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        "ttf" -> "font/ttf"
        else -> "application/octet-stream"
    }

    private fun normalizeHexColor(input: String): String {
        val clean = input.trim().let { if (it.startsWith("#")) it else "#$it" }
        return if (Regex("^#[0-9A-Fa-f]{6}$").matches(clean)) clean else "#5B3DF5"
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun escapeKotlinString(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")
}
