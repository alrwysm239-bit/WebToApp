package com.example

import com.example.domain.HtmlToolkit
import com.example.domain.apk.ApkSignerAndPackager
import com.example.domain.apk.BinaryAndroidManifestEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ApkEncoderUnitTest {

    @Test
    fun `generated apk passes aapt2 badging xmltree resources alignment and apksigner v1 v2 verification`() {
        val sdkRoot = System.getenv("ANDROID_SDK_ROOT") ?: System.getenv("ANDROID_HOME")
        val dexFile = File("src/main/assets/apk_template/classes.dex")
        assertTrue("classes.dex must exist at ${dexFile.absolutePath}", dexFile.exists())

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
                ApkSignerAndPackager.ZipInputEntry("assets/app_config.json", """{"appName":"وهج TV","sourceMode":"html"}""".toByteArray(), storeUncompressed = false),
                ApkSignerAndPackager.ZipInputEntry("assets/www/index.html", "<h1>Hi</h1>".toByteArray(), storeUncompressed = false),
            ),
            appSignerName = appName,
        )
        assertTrue(apkBytes.size > 10_000)

        // Verify 4-byte alignment of all STORED entries in final signed APK
        verifyStoredEntriesAligned4Bytes(apkBytes)

        if (sdkRoot != null) {
            val buildTools = File(sdkRoot, "build-tools").listFiles()?.filter { it.isDirectory }?.maxByOrNull { it.name }
            if (buildTools != null) {
                val outApk = File.createTempFile("webforge-test-", ".apk")
                outApk.writeBytes(apkBytes)
                try {
                    val aapt2 = File(buildTools, "aapt2").absolutePath

                    // 1. dump badging
                    val aaptProc = ProcessBuilder(aapt2, "dump", "badging", outApk.absolutePath)
                        .redirectErrorStream(true).start()
                    val aaptOut = aaptProc.inputStream.bufferedReader().readText()
                    assertEquals("aapt2 dump badging failed:\n$aaptOut", 0, aaptProc.waitFor())
                    assertTrue("Missing package name in badging:\n$aaptOut", aaptOut.contains("name='$pkg'"))
                    assertTrue("Missing app label in badging:\n$aaptOut", aaptOut.contains(appName))
                    assertTrue("Missing launchable activity:\n$aaptOut", aaptOut.contains("launchable-activity: name='com.webforge.shell.MainActivity'"))

                    // 2. dump xmltree AndroidManifest.xml — ensure Theme.DeviceDefault.NoActionBar (0x01030129)
                    val xmlProc = ProcessBuilder(aapt2, "dump", "xmltree", outApk.absolutePath, "--file", "AndroidManifest.xml")
                        .redirectErrorStream(true).start()
                    val xmlOut = xmlProc.inputStream.bufferedReader().readText()
                    assertEquals("aapt2 dump xmltree failed:\n$xmlOut", 0, xmlProc.waitFor())
                    assertTrue("Expected NoActionBar theme 0x01030129:\n$xmlOut", xmlOut.contains("0x01030129"))
                    assertFalse("Broken Light theme 0x0103012b must NOT be present:\n$xmlOut", xmlOut.contains("0x0103012b"))

                    // 3. dump resources
                    val resProc = ProcessBuilder(aapt2, "dump", "resources", outApk.absolutePath)
                        .redirectErrorStream(true).start()
                    val resOut = resProc.inputStream.bufferedReader().readText()
                    assertEquals("aapt2 dump resources failed:\n$resOut", 0, resProc.waitFor())
                    assertTrue("Missing string/app_name in resources:\n$resOut", resOut.contains("app_name"))
                    assertTrue("Missing mipmap/ic_launcher in resources:\n$resOut", resOut.contains("ic_launcher"))

                    // 4. apksigner v1 + v2 verify
                    val apksigner = File(buildTools, "apksigner").absolutePath
                    val signProc = ProcessBuilder(apksigner, "verify", "--verbose", "--min-sdk-version", "21", outApk.absolutePath)
                        .redirectErrorStream(true).start()
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

    @Test
    fun `safeFileName preserves arabic and injectViewportSeoAndIcon skips script head strings`() {
        assertEquals("وهج-TV", HtmlToolkit.safeFileName("وهج TV"))
        val tricky = "<script>const x = '<head></head>';</script><div>Hello</div>"
        val fixed = HtmlToolkit.injectViewportSeoAndIcon(tricky, "Test", "Desc", "#0C0A20")
        assertTrue(fixed.indexOf("<meta name=\"viewport\"") < fixed.indexOf("<script>"))
    }

    private fun verifyStoredEntriesAligned4Bytes(apk: ByteArray) {
        val buf = ByteBuffer.wrap(apk).order(ByteOrder.LITTLE_ENDIAN)
        var offset = 0
        while (offset + 30 <= apk.size) {
            val sig = buf.getInt(offset)
            if (sig != 0x04034b50) break
            val method = buf.getShort(offset + 8).toInt() and 0xFFFF
            val compSize = buf.getInt(offset + 18)
            val nameLen = buf.getShort(offset + 26).toInt() and 0xFFFF
            val extraLen = buf.getShort(offset + 28).toInt() and 0xFFFF
            val name = String(apk, offset + 30, nameLen, Charsets.UTF_8)
            val dataOffset = offset + 30 + nameLen + extraLen
            if (method == 0) {
                assertEquals("STORED entry $name is not 4-byte aligned", 0, dataOffset % 4)
            }
            offset = dataOffset + compSize
        }
    }
}
