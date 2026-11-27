package com.example

import com.example.domain.apk.ApkSignerAndPackager
import com.example.domain.apk.BinaryAndroidManifestEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ApkEncoderUnitTest {

    @Test
    fun `generated apk passes aapt2 badging and apksigner v1 v2 verification`() {
        val sdkRoot = System.getenv("ANDROID_SDK_ROOT") ?: System.getenv("ANDROID_HOME")
        val dexFile = File("src/main/assets/apk_template/classes.dex")
        assertTrue("classes.dex must exist at ${dexFile.absolutePath}", dexFile.exists())

        // 1x1 PNG minimal bytes
        val tinyPng = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4.toByte(),
            0x89.toByte(), 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
            0x54, 0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00,
            0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4.toByte(), 0x00,
            0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(),
            0x42, 0x60, 0x82.toByte(),
        )

        val pkg = "com.whagtv.app"
        val appName = "وهج TV"
        val manifest = BinaryAndroidManifestEncoder.encodeManifest(
            packageName = pkg,
            versionCode = 2,
            versionName = "1.2.0",
            permissions = listOf("INTERNET", "ACCESS_NETWORK_STATE"),
            screenOrientationInt = -1,
        )
        val arsc = BinaryAndroidManifestEncoder.encodeResourcesArsc(packageName = pkg, appName = appName)
        val apkBytes = ApkSignerAndPackager.packageAndSignApk(
            entries = listOf(
                ApkSignerAndPackager.ZipInputEntry("AndroidManifest.xml", manifest, storeUncompressed = false),
                ApkSignerAndPackager.ZipInputEntry("resources.arsc", arsc, storeUncompressed = true, alignBytes = 4),
                ApkSignerAndPackager.ZipInputEntry("classes.dex", dexFile.readBytes(), storeUncompressed = false),
                ApkSignerAndPackager.ZipInputEntry("res/mipmap-xxxhdpi-v4/ic_launcher.png", tinyPng, storeUncompressed = true, alignBytes = 4),
                ApkSignerAndPackager.ZipInputEntry("assets/www/index.html", "<h1>Hi</h1>".toByteArray(), storeUncompressed = false),
            ),
            appSignerName = appName,
        )
        assertTrue(apkBytes.size > 10_000)

        if (sdkRoot != null) {
            val buildTools = File(sdkRoot, "build-tools").listFiles()?.filter { it.isDirectory }?.maxByOrNull { it.name }
            if (buildTools != null) {
                val outApk = File.createTempFile("webforge-test-", ".apk")
                outApk.writeBytes(apkBytes)
                try {
                    val aapt2 = File(buildTools, "aapt2").absolutePath
                    val aaptProc = ProcessBuilder(aapt2, "dump", "badging", outApk.absolutePath)
                        .redirectErrorStream(true)
                        .start()
                    val aaptOut = aaptProc.inputStream.bufferedReader().readText()
                    assertEquals("aapt2 dump badging failed:\n$aaptOut", 0, aaptProc.waitFor())
                    assertTrue("Missing package name in badging:\n$aaptOut", aaptOut.contains("name='$pkg'"))
                    assertTrue("Missing app label in badging:\n$aaptOut", aaptOut.contains(appName))

                    val apksigner = File(buildTools, "apksigner").absolutePath
                    val signProc = ProcessBuilder(apksigner, "verify", "--verbose", "--min-sdk-version", "21", outApk.absolutePath)
                        .redirectErrorStream(true)
                        .start()
                    val signOut = signProc.inputStream.bufferedReader().readText()
                    assertEquals("apksigner verify failed:\n$signOut", 0, signProc.waitFor())
                    assertTrue("v1 verify missing:\n$signOut", signOut.contains("Verified using v1 scheme (JAR signing): true"))
                    assertTrue("v2 verify missing:\n$signOut", signOut.contains("Verified using v2 scheme (APK Signature Scheme v2): true"))
                } finally {
                    outApk.delete()
                }
            }
        }
    }
}
