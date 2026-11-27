package com.example.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import com.example.ai.StoreDescriptionsResult
import com.example.data.model.AppExportConfig
import com.example.data.model.ProjectAsset
import java.io.ByteArrayOutputStream

data class WebPConversionResult(
    val fileName: String,
    val originalBytes: Int,
    val compressedBytes: Int,
    val webpData: ByteArray,
)

object AssetAndStoreGenerator {

    fun compressImageToWebP(
        context: Context,
        uri: Uri,
        quality: Int = 80,
    ): WebPConversionResult {
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Unable to read image")
        val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size)
            ?: error("Invalid image format")
        val out = ByteArrayOutputStream()
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        bmp.compress(format, quality.coerceIn(10, 100), out)
        val compressed = out.toByteArray()
        return WebPConversionResult(
            fileName = "optimized_${System.currentTimeMillis() % 10000}.webp",
            originalBytes = raw.size,
            compressedBytes = compressed.size,
            webpData = compressed,
        )
    }

    fun generateIconAndSplashPackZip(
        context: Context,
        config: AppExportConfig,
    ): ByteArray {
        val iconSource = ZipArchiveManager.resolveIconPngBytes(context, config.customIconBase64)
        val files = linkedMapOf<String, ByteArray>()
        listOf(48, 72, 96, 128, 144, 152, 192, 384, 512).forEach { sz ->
            files["icons/icon-${sz}x${sz}.png"] = ZipArchiveManager.resizePng(iconSource, sz)
        }
        files["splash/splash-1080x1920.png"] = renderSplashGraphic(iconSource, config)
        return ZipArchiveManager.zipFileMap(files)
    }

    fun generateFeatureGraphicPng(context: Context, config: AppExportConfig): ByteArray {
        val iconBytes = ZipArchiveManager.resolveIconPngBytes(context, config.customIconBase64)
        val iconBmp = BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.size)
        val w = 1024
        val h = 500
        val outBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBmp)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(Color.parseColor("#0C0A20"), Color.parseColor("#1C1840"), Color.parseColor("#5B3DF5")),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        // Subtle glowing cyan orb
        val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(48, 0, 198, 255)
        }
        canvas.drawCircle(860f, 110f, 180f, orbPaint)

        if (iconBmp != null) {
            val scaledIcon = Bitmap.createScaledBitmap(iconBmp, 176, 176, true)
            canvas.drawBitmap(scaledIcon, 84f, 162f, null)
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 58f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(config.appName.take(24), 300f, 238f, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00C6FF")
            textSize = 28f
        }
        canvas.drawText(config.seoDescription.take(48), 300f, 294f, subPaint)

        val out = ByteArrayOutputStream()
        outBmp.compress(Bitmap.CompressFormat.PNG, 95, out)
        return out.toByteArray()
    }

    fun generateScreenshotGraphicPng(context: Context, config: AppExportConfig): ByteArray {
        val iconBytes = ZipArchiveManager.resolveIconPngBytes(context, config.customIconBase64)
        return renderSplashGraphic(iconBytes, config)
    }

    fun generateStorePackZip(
        context: Context,
        config: AppExportConfig,
        descriptions: StoreDescriptionsResult?,
    ): ByteArray {
        val iconSource = ZipArchiveManager.resolveIconPngBytes(context, config.customIconBase64)
        val files = linkedMapOf<String, ByteArray>()
        files["store-icon-512x512.png"] = ZipArchiveManager.resizePng(iconSource, 512)
        files["feature-graphic-1024x500.png"] = generateFeatureGraphicPng(context, config)
        files["promo-screenshot-1080x1920.png"] = generateScreenshotGraphicPng(context, config)
        files["privacy-policy.html"] = buildPrivacyPolicyHtml(config.appName).toByteArray(Charsets.UTF_8)
        files["terms-of-use.html"] = buildTermsHtml(config.appName).toByteArray(Charsets.UTF_8)

        val descText = if (descriptions != null) {
            """
            === ${config.appName} Store Listings (Generated by Gemini AI) ===

            [Arabic / العربية]
            ${descriptions.ar}

            [English]
            ${descriptions.en}

            [Français]
            ${descriptions.fr}

            [Español]
            ${descriptions.es}
            """.trimIndent()
        } else {
            """
            === ${config.appName} Store Listing ===
            Description: ${config.seoDescription}
            """.trimIndent()
        }
        files["store-descriptions.txt"] = descText.toByteArray(Charsets.UTF_8)
        return ZipArchiveManager.zipFileMap(files)
    }

    fun generateKeystoreGuideZip(config: AppExportConfig): ByteArray {
        val alias = HtmlToolkit.slugify(config.appName).take(16)
        val files = linkedMapOf<String, ByteArray>()
        files["generate-keystore.sh"] = """
            #!/usr/bin/env bash
            # Run locally with JDK keytool to generate your Play Store upload keystore
            keytool -genkeypair -v \
              -keystore release-keystore.jks \
              -keyalg RSA -keysize 2048 -validity 10000 \
              -alias $alias
            echo "Base64 for GitHub Secrets (KEYSTORE_BASE64):"
            base64 -w 0 release-keystore.jks
        """.trimIndent().toByteArray(Charsets.UTF_8)

        files["signing-gradle-snippet.kts"] = """
            signingConfigs {
                create("release") {
                    storeFile = file("release-keystore.jks")
                    storePassword = System.getenv("KEYSTORE_PASSWORD")
                    keyAlias = "$alias"
                    keyPassword = System.getenv("KEY_PASSWORD")
                }
            }
        """.trimIndent().toByteArray(Charsets.UTF_8)

        files["KEYSTORE_GUIDE.md"] = """
            # Android App Signing Keystore Guide (${config.appName})

            1. Run `bash generate-keystore.sh` on your machine (requires Java JDK `keytool`).
            2. Keep `release-keystore.jks` and passwords strictly private—never commit them to Git.
            3. Add `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` (`$alias`), and `KEY_PASSWORD` to GitHub Repository Secrets for automated Play Store AAB signing.
        """.trimIndent().toByteArray(Charsets.UTF_8)
        return ZipArchiveManager.zipFileMap(files)
    }

    fun buildIosCapacitorZip(
        context: Context,
        projectName: String,
        html: String,
        assets: List<ProjectAsset>,
        config: AppExportConfig,
    ): ByteArray {
        val safeName = config.appName.ifBlank { projectName }
        val pkg = config.packageName.ifBlank { "com.webforge.app" }
        val files = linkedMapOf<String, ByteArray>()
        files["capacitor.config.json"] = """
            {
              "appId": "$pkg",
              "appName": "$safeName",
              "webDir": "www",
              "ios": {
                "contentInset": "automatic",
                "backgroundColor": "${config.themeColorHex}"
              }
            }
        """.trimIndent().toByteArray(Charsets.UTF_8)
        files["package.json"] = """
            {
              "name": "${HtmlToolkit.slugify(safeName)}",
              "version": "${config.versionName}",
              "scripts": {
                "ios:sync": "npx cap add ios && npx cap sync ios && npx cap open ios"
              },
              "dependencies": {
                "@capacitor/core": "^6.0.0",
                "@capacitor/ios": "^6.0.0"
              },
              "@capacitor/cli": "^6.0.0"
            }
        """.trimIndent().toByteArray(Charsets.UTF_8)
        files["www/index.html"] = html.toByteArray(Charsets.UTF_8)
        val iconBytes = ZipArchiveManager.resolveIconPngBytes(context, config.customIconBase64)
        files["www/favicon.png"] = ZipArchiveManager.resizePng(iconBytes, 64)
        assets.forEach { asset ->
            ZipArchiveManager.assetToBytes(asset)?.let { files["www/${asset.path}"] = it }
        }
        files["README_IOS.md"] = "Run `npm install && npm run ios:sync` on macOS with Xcode to build the iOS IPA.\n".toByteArray(Charsets.UTF_8)
        return ZipArchiveManager.zipFileMap(files)
    }

    fun buildElectronDesktopZip(
        context: Context,
        projectName: String,
        html: String,
        assets: List<ProjectAsset>,
        config: AppExportConfig,
    ): ByteArray {
        val safeName = config.appName.ifBlank { projectName }
        val files = linkedMapOf<String, ByteArray>()
        files["package.json"] = """
            {
              "name": "${HtmlToolkit.slugify(safeName)}",
              "productName": "$safeName",
              "version": "${config.versionName}",
              "main": "main.js",
              "scripts": {
                "start": "electron .",
                "build": "electron-builder"
              },
              "devDependencies": {
                "electron": "^32.0.0",
                "electron-builder": "^25.0.0"
              }
            }
        """.trimIndent().toByteArray(Charsets.UTF_8)
        files["main.js"] = """
            const { app, BrowserWindow } = require('electron');
            const path = require('path');

            function createWindow() {
              const win = new BrowserWindow({
                width: 1200,
                height: 800,
                backgroundColor: '${config.themeColorHex}',
                icon: path.join(__dirname, 'icon.png'),
                webPreferences: { contextIsolation: true, nodeIntegration: false }
              });
              win.loadFile(path.join(__dirname, 'www', 'index.html'));
            }

            app.whenReady().then(createWindow);
            app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
        """.trimIndent().toByteArray(Charsets.UTF_8)
        val iconBytes = ZipArchiveManager.resolveIconPngBytes(context, config.customIconBase64)
        files["icon.png"] = ZipArchiveManager.resizePng(iconBytes, 256)
        files["www/index.html"] = html.toByteArray(Charsets.UTF_8)
        assets.forEach { asset ->
            ZipArchiveManager.assetToBytes(asset)?.let { files["www/${asset.path}"] = it }
        }
        return ZipArchiveManager.zipFileMap(files)
    }

    private fun renderSplashGraphic(iconSource: ByteArray, config: AppExportConfig): ByteArray {
        val w = 1080
        val h = 1920
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val parsedBg = runCatching { Color.parseColor(config.themeColorHex) }.getOrDefault(Color.parseColor("#0C0A20"))
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(Color.parseColor("#0C0A20"), parsedBg),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        val iconBmp = BitmapFactory.decodeByteArray(iconSource, 0, iconSource.size)
        if (iconBmp != null) {
            val scaled = Bitmap.createScaledBitmap(iconBmp, 260, 260, true)
            canvas.drawBitmap(scaled, (w - 260) / 2f, (h - 260) / 2f - 80f, null)
        }

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, 255, 255, 255)
        }
        canvas.drawRoundRect(RectF(180f, 1140f, 900f, 1260f), 28f, 28f, cardPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 54f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(config.appName.take(22), w / 2f, 1220f, textPaint)

        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 92, out)
        return out.toByteArray()
    }

    fun buildPrivacyPolicyHtml(appName: String): String = """
        <!DOCTYPE html>
        <html lang="ar" dir="rtl">
        <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0"><title>سياسة الخصوصية - $appName</title></head>
        <body style="font-family:system-ui,sans-serif;max-width:760px;margin:40px auto;padding:20px;line-height:1.8;">
          <h1>سياسة الخصوصية — $appName</h1>
          <p>يحترم تطبيق $appName خصوصيتك بالكامل. يتم تشغيل المحتوى محلياً أو عبر متصفح آمن دون جمع بيانات شخصية حساسة بدون موافقتك الصريحة.</p>
          <h2>Privacy Policy — $appName</h2>
          <p>$appName respects your privacy and does not sell or share personal data with unauthorized third parties.</p>
        </body>
        </html>
    """.trimIndent()

    fun buildTermsHtml(appName: String): String = """
        <!DOCTYPE html>
        <html lang="ar" dir="rtl">
        <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0"><title>شروط الاستخدام - $appName</title></head>
        <body style="font-family:system-ui,sans-serif;max-width:760px;margin:40px auto;padding:20px;line-height:1.8;">
          <h1>شروط الاستخدام — $appName</h1>
          <p>باستخدامك لتطبيق $appName فإنك توافق على استخدامه للأغراض المشروعة فقط واحترام حقوق الملكية الفكرية.</p>
          <h2>Terms of Use — $appName</h2>
          <p>By using $appName, you agree to comply with applicable laws and acceptable usage terms.</p>
        </body>
        </html>
    """.trimIndent()
}
