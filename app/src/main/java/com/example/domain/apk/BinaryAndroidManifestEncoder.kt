package com.example.domain.apk

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure-Kotlin encoder for Android Binary XML (AXML) and Binary Resource Table (resources.arsc).
 * Produces valid compiled AndroidManifest.xml and resources.arsc files directly on-device without aapt2.
 */
object BinaryAndroidManifestEncoder {

    private const val CHUNK_AXML_FILE = 0x00080003
    private const val CHUNK_STRING_POOL = 0x001C0001
    private const val CHUNK_RESOURCE_IDS = 0x00080180
    private const val CHUNK_START_NAMESPACE = 0x00100100
    private const val CHUNK_END_NAMESPACE = 0x00100101
    private const val CHUNK_START_TAG = 0x00100102
    private const val CHUNK_END_TAG = 0x00100103

    private const val RES_TABLE_TYPE: Short = 0x0002
    private const val RES_STRING_POOL_TYPE: Short = 0x0001
    private const val RES_TABLE_PACKAGE_TYPE: Short = 0x0200
    private const val RES_TABLE_TYPE_SPEC_TYPE: Short = 0x0202
    private const val RES_TABLE_TYPE_TYPE: Short = 0x0201

    private const val TYPE_REFERENCE = 0x01
    private const val TYPE_STRING = 0x03
    private const val TYPE_INT_DEC = 0x10
    private const val TYPE_INT_HEX = 0x11
    private const val TYPE_INT_BOOLEAN = 0x12

    // Verified AOSP android.R.attr resource IDs
    private const val ATTR_THEME = 0x01010000
    private const val ATTR_LABEL = 0x01010001
    private const val ATTR_ICON = 0x01010002
    private const val ATTR_NAME = 0x01010003
    private const val ATTR_EXPORTED = 0x01010010
    private const val ATTR_SCREEN_ORIENTATION = 0x0101001e
    private const val ATTR_CONFIG_CHANGES = 0x0101001f
    private const val ATTR_MIN_SDK = 0x0101020c
    private const val ATTR_VERSION_CODE = 0x0101021b
    private const val ATTR_VERSION_NAME = 0x0101021c
    private const val ATTR_TARGET_SDK = 0x01010270
    private const val ATTR_ALLOW_BACKUP = 0x01010280
    private const val ATTR_HARDWARE_ACCEL = 0x010102d3
    private const val ATTR_SUPPORTS_RTL = 0x010103af
    private const val ATTR_USES_CLEARTEXT = 0x010104ec

    private const val RES_APP_NAME = 0x7f010000
    private const val RES_IC_LAUNCHER = 0x7f020000
    // Verified from AOSP public-final.xml: 0x01030129 = Theme.DeviceDefault.NoActionBar
    private const val ANDROID_THEME_DEVICE_DEFAULT_NO_ACTION_BAR = 0x01030129

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    data class AxmlAttr(
        val ns: String?,
        val name: String,
        val resId: Int,
        val type: Int,
        val data: Int,
        val rawStr: String? = null,
    )

    fun encodeManifest(
        packageName: String,
        versionCode: Int,
        versionName: String,
        permissions: List<String>,
        screenOrientationInt: Int,
        minSdk: Int = 24,
        targetSdk: Int = 34,
    ): ByteArray {
        val attrResMap = listOf(
            "theme" to ATTR_THEME,
            "label" to ATTR_LABEL,
            "icon" to ATTR_ICON,
            "name" to ATTR_NAME,
            "exported" to ATTR_EXPORTED,
            "screenOrientation" to ATTR_SCREEN_ORIENTATION,
            "configChanges" to ATTR_CONFIG_CHANGES,
            "minSdkVersion" to ATTR_MIN_SDK,
            "versionCode" to ATTR_VERSION_CODE,
            "versionName" to ATTR_VERSION_NAME,
            "targetSdkVersion" to ATTR_TARGET_SDK,
            "allowBackup" to ATTR_ALLOW_BACKUP,
            "hardwareAccelerated" to ATTR_HARDWARE_ACCEL,
            "supportsRtl" to ATTR_SUPPORTS_RTL,
            "usesCleartextTraffic" to ATTR_USES_CLEARTEXT,
        )

        val strings = mutableListOf<String>()
        val stringIdx = LinkedHashMap<String, Int>()
        fun intern(s: String): Int = stringIdx.getOrPut(s) {
            strings.add(s)
            strings.lastIndex
        }

        // Resource-ID-mapped attribute names MUST come first in exact order
        attrResMap.forEach { (name, _) -> intern(name) }

        val fullPerms = (permissions + "INTERNET").distinct().map { p ->
            if (p.startsWith("android.permission.")) p else "android.permission.$p"
        }

        val nsPrefix = intern("android")
        val nsUri = intern(ANDROID_NS)
        intern("manifest")
        intern("package")
        intern(packageName)
        intern(versionName)
        intern("uses-sdk")
        intern("uses-permission")
        fullPerms.forEach { intern(it) }
        intern("application")
        intern("activity")
        intern("com.webforge.shell.MainActivity")
        intern("intent-filter")
        intern("action")
        intern("android.intent.action.MAIN")
        intern("category")
        intern("android.intent.category.LAUNCHER")

        val stringPoolBytes = encodeUtf16StringPool(strings)
        val resMapBytes = ByteBuffer.allocate(8 + attrResMap.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(CHUNK_RESOURCE_IDS)
            putInt(8 + attrResMap.size * 4)
            attrResMap.forEach { (_, id) -> putInt(id) }
        }.array()

        val body = ByteArrayOutputStream()
        writeStartNamespace(body, nsPrefix, nsUri)

        // <manifest package="..." android:versionCode="..." android:versionName="...">
        writeStartTag(
            body,
            ns = null,
            name = "manifest",
            attrs = listOf(
                AxmlAttr(ANDROID_NS, "versionCode", ATTR_VERSION_CODE, TYPE_INT_DEC, versionCode),
                AxmlAttr(ANDROID_NS, "versionName", ATTR_VERSION_NAME, TYPE_STRING, intern(versionName), versionName),
                AxmlAttr(null, "package", 0, TYPE_STRING, intern(packageName), packageName),
            ),
            intern = ::intern,
        )

        // <uses-sdk android:minSdkVersion="24" android:targetSdkVersion="34"/>
        writeStartTag(
            body,
            ns = null,
            name = "uses-sdk",
            attrs = listOf(
                AxmlAttr(ANDROID_NS, "minSdkVersion", ATTR_MIN_SDK, TYPE_INT_DEC, minSdk),
                AxmlAttr(ANDROID_NS, "targetSdkVersion", ATTR_TARGET_SDK, TYPE_INT_DEC, targetSdk),
            ),
            intern = ::intern,
        )
        writeEndTag(body, null, "uses-sdk", ::intern)

        fullPerms.forEach { perm ->
            writeStartTag(
                body,
                ns = null,
                name = "uses-permission",
                attrs = listOf(
                    AxmlAttr(ANDROID_NS, "name", ATTR_NAME, TYPE_STRING, intern(perm), perm),
                ),
                intern = ::intern,
            )
            writeEndTag(body, null, "uses-permission", ::intern)
        }

        // <application ...>
        writeStartTag(
            body,
            ns = null,
            name = "application",
            attrs = listOf(
                AxmlAttr(ANDROID_NS, "theme", ATTR_THEME, TYPE_REFERENCE, ANDROID_THEME_DEVICE_DEFAULT_NO_ACTION_BAR),
                AxmlAttr(ANDROID_NS, "label", ATTR_LABEL, TYPE_REFERENCE, RES_APP_NAME),
                AxmlAttr(ANDROID_NS, "icon", ATTR_ICON, TYPE_REFERENCE, RES_IC_LAUNCHER),
                AxmlAttr(ANDROID_NS, "allowBackup", ATTR_ALLOW_BACKUP, TYPE_INT_BOOLEAN, -1),
                AxmlAttr(ANDROID_NS, "hardwareAccelerated", ATTR_HARDWARE_ACCEL, TYPE_INT_BOOLEAN, -1),
                AxmlAttr(ANDROID_NS, "supportsRtl", ATTR_SUPPORTS_RTL, TYPE_INT_BOOLEAN, -1),
                AxmlAttr(ANDROID_NS, "usesCleartextTraffic", ATTR_USES_CLEARTEXT, TYPE_INT_BOOLEAN, -1),
            ),
            intern = ::intern,
        )

        // <activity android:name="com.webforge.shell.MainActivity" ...>
        writeStartTag(
            body,
            ns = null,
            name = "activity",
            attrs = listOf(
                AxmlAttr(ANDROID_NS, "name", ATTR_NAME, TYPE_STRING, intern("com.webforge.shell.MainActivity"), "com.webforge.shell.MainActivity"),
                AxmlAttr(ANDROID_NS, "exported", ATTR_EXPORTED, TYPE_INT_BOOLEAN, -1),
                AxmlAttr(ANDROID_NS, "screenOrientation", ATTR_SCREEN_ORIENTATION, TYPE_INT_DEC, screenOrientationInt),
                AxmlAttr(ANDROID_NS, "configChanges", ATTR_CONFIG_CHANGES, TYPE_INT_HEX, 0x0FB0),
            ),
            intern = ::intern,
        )

        writeStartTag(body, null, "intent-filter", emptyList(), ::intern)
        writeStartTag(
            body,
            ns = null,
            name = "action",
            attrs = listOf(
                AxmlAttr(ANDROID_NS, "name", ATTR_NAME, TYPE_STRING, intern("android.intent.action.MAIN"), "android.intent.action.MAIN"),
            ),
            intern = ::intern,
        )
        writeEndTag(body, null, "action", ::intern)
        writeStartTag(
            body,
            ns = null,
            name = "category",
            attrs = listOf(
                AxmlAttr(ANDROID_NS, "name", ATTR_NAME, TYPE_STRING, intern("android.intent.category.LAUNCHER"), "android.intent.category.LAUNCHER"),
            ),
            intern = ::intern,
        )
        writeEndTag(body, null, "category", ::intern)
        writeEndTag(body, null, "intent-filter", ::intern)

        writeEndTag(body, null, "activity", ::intern)
        writeEndTag(body, null, "application", ::intern)
        writeEndTag(body, null, "manifest", ::intern)
        writeEndNamespace(body, nsPrefix, nsUri)

        val bodyBytes = body.toByteArray()
        val totalSize = 8 + stringPoolBytes.size + resMapBytes.size + bodyBytes.size
        return ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(CHUNK_AXML_FILE)
            putInt(totalSize)
            put(stringPoolBytes)
            put(resMapBytes)
            put(bodyBytes)
        }.array()
    }

    fun encodeResourcesArsc(packageName: String, appName: String): ByteArray {
        val valueStrings = listOf(appName, "res/mipmap-xxxhdpi-v4/ic_launcher.png")
        val valuePool = encodeUtf16StringPool(valueStrings)
        val typeStringsPool = encodeUtf16StringPool(listOf("string", "mipmap"))
        val keyStringsPool = encodeUtf16StringPool(listOf("app_name", "ic_launcher"))

        // TypeSpec for type 1 (string)
        val typeSpec1 = encodeTypeSpec(typeId = 1, entryCount = 1)
        // Type 1 (string): 1 entry ("app_name") -> string index 0
        val type1 = encodeTypeSingleEntry(typeId = 1, keyIdx = 0, dataType = TYPE_STRING, data = 0)

        // TypeSpec for type 2 (mipmap)
        val typeSpec2 = encodeTypeSpec(typeId = 2, entryCount = 1)
        // Type 2 (mipmap): 1 entry ("ic_launcher") -> string index 1
        val type2 = encodeTypeSingleEntry(typeId = 2, keyIdx = 1, dataType = TYPE_STRING, data = 1)

        val pkgHeaderSize = 288
        val typeStringsOffset = pkgHeaderSize
        val keyStringsOffset = typeStringsOffset + typeStringsPool.size
        val pkgSize = pkgHeaderSize + typeStringsPool.size + keyStringsPool.size +
            typeSpec1.size + type1.size + typeSpec2.size + type2.size

        val pkgBuf = ByteBuffer.allocate(pkgSize).order(ByteOrder.LITTLE_ENDIAN)
        pkgBuf.putShort(RES_TABLE_PACKAGE_TYPE)
        pkgBuf.putShort(pkgHeaderSize.toShort())
        pkgBuf.putInt(pkgSize)
        pkgBuf.putInt(0x7f) // Package ID
        val pkgChars = packageName.take(127)
        for (i in 0 until 128) {
            pkgBuf.putShort(if (i < pkgChars.length) pkgChars[i].code.toShort() else 0)
        }
        pkgBuf.putInt(typeStringsOffset)
        pkgBuf.putInt(2) // lastPublicType
        pkgBuf.putInt(keyStringsOffset)
        pkgBuf.putInt(2) // lastPublicKey
        pkgBuf.putInt(0) // typeIdOffset
        pkgBuf.put(typeStringsPool)
        pkgBuf.put(keyStringsPool)
        pkgBuf.put(typeSpec1)
        pkgBuf.put(type1)
        pkgBuf.put(typeSpec2)
        pkgBuf.put(type2)

        val pkgBytes = pkgBuf.array()
        val totalSize = 12 + valuePool.size + pkgBytes.size
        return ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(RES_TABLE_TYPE)
            putShort(12.toShort())
            putInt(totalSize)
            putInt(1) // packageCount
            put(valuePool)
            put(pkgBytes)
        }.array()
    }

    private fun encodeTypeSpec(typeId: Int, entryCount: Int): ByteArray {
        val chunkSize = 16 + entryCount * 4
        return ByteBuffer.allocate(chunkSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(RES_TABLE_TYPE_SPEC_TYPE)
            putShort(16.toShort())
            putInt(chunkSize)
            put(typeId.toByte())
            put(0)
            putShort(0)
            putInt(entryCount)
            repeat(entryCount) { putInt(0) }
        }.array()
    }

    private fun encodeTypeSingleEntry(typeId: Int, keyIdx: Int, dataType: Int, data: Int): ByteArray {
        val configSize = 64
        val headerSize = 20 + configSize // 84
        val entryTableSize = 4
        val entrySize = 16 // ResTable_entry(8) + Res_value(8)
        val entriesStart = headerSize + entryTableSize
        val chunkSize = entriesStart + entrySize

        return ByteBuffer.allocate(chunkSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(RES_TABLE_TYPE_TYPE)
            putShort(headerSize.toShort())
            putInt(chunkSize)
            put(typeId.toByte())
            put(0) // res0
            putShort(0) // res1
            putInt(1) // entryCount
            putInt(entriesStart)
            // ResTable_config (64 bytes, first int = size = 64)
            putInt(configSize)
            repeat(configSize - 4) { put(0) }
            // Entry offset 0
            putInt(0)
            // ResTable_entry
            putShort(8.toShort()) // size
            putShort(0.toShort()) // flags
            putInt(keyIdx) // key string index
            // Res_value
            putShort(8.toShort()) // size
            put(0) // res0
            put(dataType.toByte())
            putInt(data)
        }.array()
    }

    private fun encodeUtf16StringPool(strings: List<String>): ByteArray {
        val strCount = strings.size
        val offsets = IntArray(strCount)
        val strData = ByteArrayOutputStream()
        for (i in strings.indices) {
            offsets[i] = strData.size()
            val s = strings[i]
            val len = s.length
            if (len > 0x7FFF) {
                strData.write(((len shr 8) or 0x80) and 0xFF)
                strData.write(len and 0xFF)
            } else {
                strData.write(len and 0xFF)
                strData.write((len shr 8) and 0xFF)
            }
            for (ch in s) {
                val c = ch.code
                strData.write(c and 0xFF)
                strData.write((c shr 8) and 0xFF)
            }
            strData.write(0)
            strData.write(0)
        }
        while (strData.size() % 4 != 0) {
            strData.write(0)
        }
        val rawStrBytes = strData.toByteArray()
        val headerSize = 28
        val stringsStart = headerSize + strCount * 4
        val chunkSize = stringsStart + rawStrBytes.size

        return ByteBuffer.allocate(chunkSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(CHUNK_STRING_POOL)
            putInt(chunkSize)
            putInt(strCount)
            putInt(0) // styleCount
            putInt(0) // flags = UTF-16LE
            putInt(stringsStart)
            putInt(0) // stylesStart
            offsets.forEach { putInt(it) }
            put(rawStrBytes)
        }.array()
    }

    private fun writeStartNamespace(out: ByteArrayOutputStream, prefixIdx: Int, uriIdx: Int) {
        val buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(CHUNK_START_NAMESPACE)
            putInt(24)
            putInt(1)
            putInt(-1)
            putInt(prefixIdx)
            putInt(uriIdx)
        }
        out.write(buf.array())
    }

    private fun writeEndNamespace(out: ByteArrayOutputStream, prefixIdx: Int, uriIdx: Int) {
        val buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(CHUNK_END_NAMESPACE)
            putInt(24)
            putInt(1)
            putInt(-1)
            putInt(prefixIdx)
            putInt(uriIdx)
        }
        out.write(buf.array())
    }

    private fun writeStartTag(
        out: ByteArrayOutputStream,
        ns: String?,
        name: String,
        attrs: List<AxmlAttr>,
        intern: (String) -> Int,
    ) {
        val sortedAttrs = attrs.sortedWith(
            compareBy<AxmlAttr> { if (it.ns == null) 1 else 0 }.thenBy { it.resId },
        )
        val chunkSize = 36 + sortedAttrs.size * 20
        val buf = ByteBuffer.allocate(chunkSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(CHUNK_START_TAG)
            putInt(chunkSize)
            putInt(1)
            putInt(-1)
            putInt(if (ns == null) -1 else intern(ns))
            putInt(intern(name))
            putShort(0x0014.toShort()) // attributeStart
            putShort(0x0014.toShort()) // attributeSize
            putShort(sortedAttrs.size.toShort())
            putShort(0) // idIndex
            putShort(0) // classIndex
            putShort(0) // styleIndex
            sortedAttrs.forEach { attr ->
                putInt(if (attr.ns == null) -1 else intern(attr.ns))
                putInt(intern(attr.name))
                putInt(if (attr.type == TYPE_STRING && attr.rawStr != null) intern(attr.rawStr) else -1)
                putShort(8.toShort())
                put(0)
                put(attr.type.toByte())
                putInt(attr.data)
            }
        }
        out.write(buf.array())
    }

    private fun writeEndTag(
        out: ByteArrayOutputStream,
        ns: String?,
        name: String,
        intern: (String) -> Int,
    ) {
        val buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(CHUNK_END_TAG)
            putInt(24)
            putInt(1)
            putInt(-1)
            putInt(if (ns == null) -1 else intern(ns))
            putInt(intern(name))
        }
        out.write(buf.array())
    }
}
