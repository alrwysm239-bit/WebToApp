package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.GeminiConfig
import com.example.ai.configureAppCheck
import com.example.data.model.AppExportConfig
import com.example.data.model.AppOrientation
import com.example.data.model.AppSourceMode
import com.example.domain.ZipArchiveManager
import com.example.domain.apk.OnDeviceApkBuilder
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertTrue(appName == "WebForge" || appName == "ويب فورج")
    }

    @Test
    fun `configureAppCheck initializes default FirebaseApp without throwing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        configureAppCheck(context, null)
        assertTrue("FirebaseApp should initialize cleanly with Context", FirebaseApp.getApps(context).isNotEmpty())
    }

    @Test
    fun `geminiConfig prioritizes user SharedPreferences key over buildConfig fallback`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        GeminiConfig.init(context)
        GeminiConfig.saveUserKey("", context)
        assertEquals("", GeminiConfig.getSavedUserKey(context))

        GeminiConfig.saveUserKey("  AIzaTestUserKey123  ", context)
        assertEquals("AIzaTestUserKey123", GeminiConfig.getSavedUserKey(context))
        assertEquals("AIzaTestUserKey123", GeminiConfig.resolveApiKey(context))

        GeminiConfig.saveUserKey("", context)
        val expectedFallback = BuildConfig.GEMINI_API_KEY.trim().let {
            if (it.isNotEmpty() && !it.startsWith("PUT_YOUR")) it else ""
        }
        assertEquals(expectedFallback, GeminiConfig.resolveApiKey(context))
    }

    @Test
    fun `onDeviceApkBuilder produces valid installable apk with persistent signing certificate`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bigArabicHtml = buildString {
            append("<!DOCTYPE html><html lang='ar' dir='rtl'><head><meta charset='UTF-8'>")
            append("<meta name='viewport' content='width=device-width,initial-scale=1.0'><title>وهج TV</title></head><body>")
            repeat(2000) { idx -> append("<div class='ch'>قناة مباشرة $idx — بث مستقر</div>") }
            append("</body></html>")
        }
        val config = AppExportConfig(
            appName = "وهج TV",
            packageName = "com.wahgtv",
            sourceMode = AppSourceMode.HTML,
            orientation = AppOrientation.AUTO,
            appLockEnabled = true,
            appLockPassword = "1234",
            adsEnabled = true,
            bottomNavBarEnabled = true,
        )

        val apk1 = OnDeviceApkBuilder.buildSignedApk(
            context = context,
            projectName = "وهج TV",
            html = bigArabicHtml,
            assets = emptyList(),
            config = config,
        )
        val apk2 = OnDeviceApkBuilder.buildSignedApk(
            context = context,
            projectName = "وهج TV",
            html = bigArabicHtml,
            assets = emptyList(),
            config = config,
        )

        // Verify persistent signing certificate matches across rebuilds
        fun extractEntry(apk: ByteArray, entryName: String): ByteArray {
            ZipInputStream(ByteArrayInputStream(apk)).use { zis ->
                var e = zis.nextEntry
                while (e != null) {
                    if (e.name == entryName) return zis.readBytes()
                    e = zis.nextEntry
                }
            }
            error("Missing $entryName")
        }
        assertArrayEquals(
            "Signing certificate must persist across rebuilds",
            extractEntry(apk1, "META-INF/CERT.RSA"),
            extractEntry(apk2, "META-INF/CERT.RSA"),
        )

        val sdkRoot = System.getenv("ANDROID_SDK_ROOT") ?: System.getenv("ANDROID_HOME")
        if (sdkRoot != null) {
            val buildTools = File(sdkRoot, "build-tools").listFiles()?.filter { it.isDirectory }?.maxByOrNull { it.name }
            if (buildTools != null) {
                val outApk = File.createTempFile("ondevice-builder-", ".apk")
                outApk.writeBytes(apk1)
                try {
                    val aapt2 = File(buildTools, "aapt2").absolutePath
                    val badging = ProcessBuilder(aapt2, "dump", "badging", outApk.absolutePath)
                        .redirectErrorStream(true).start()
                    val badgingOut = badging.inputStream.bufferedReader().readText()
                    assertEquals("aapt2 dump badging failed:\n$badgingOut", 0, badging.waitFor())
                    assertTrue(badgingOut.contains("name='com.wahgtv'"))
                    assertTrue(badgingOut.contains("وهج TV"))

                    val apksigner = File(buildTools, "apksigner").absolutePath
                    val verify = ProcessBuilder(apksigner, "verify", "--verbose", "--min-sdk-version", "24", outApk.absolutePath)
                        .redirectErrorStream(true).start()
                    val verifyOut = verify.inputStream.bufferedReader().readText()
                    assertEquals("apksigner verify failed:\n$verifyOut", 0, verify.waitFor())
                } finally {
                    outApk.delete()
                }
            }
        }
    }

    @Test
    fun `website deploy map includes manifest sw and head registration links`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val files = ZipArchiveManager.buildWebsiteFileMap(
            context = context,
            projectName = "MyPwaSite",
            html = "<html><head><title>Hi</title></head><body>Hello</body></html>",
            assets = emptyList(),
            config = AppExportConfig(appName = "MyPwaSite", themeColorHex = "#5B3DF5"),
            includePwa = true,
        )
        assertTrue(files.containsKey("manifest.webmanifest"))
        assertTrue(files.containsKey("sw.js"))
        assertTrue(files.containsKey("icon-192.png"))
        assertTrue(files.containsKey("icon-512.png"))

        val indexHtml = files["index.html"]!!.toString(Charsets.UTF_8)
        val headEnd = indexHtml.indexOf("</head>", ignoreCase = true)
        assertTrue(headEnd > 0)
        val headPart = indexHtml.substring(0, headEnd)
        assertTrue(headPart.contains("manifest.webmanifest"))
        assertTrue(headPart.contains("serviceWorker.register"))
    }
}
