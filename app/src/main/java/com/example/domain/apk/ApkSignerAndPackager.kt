package com.example.domain.apk

import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/**
 * Packages an Android APK ZIP with 4-byte aligned STORED resources.arsc & PNGs,
 * and signs it with BOTH APK Signature Scheme v1 (JAR) and v2 (APK Sig Block 42)
 * using pure Kotlin cryptography (no external keystores or servers required).
 */
object ApkSignerAndPackager {

    private const val CHUNK_SIZE = 1024 * 1024 // 1 MiB
    private const val APK_SIG_V2_BLOCK_ID = 0x7109871a
    private const val SIG_ALG_RSA_PKCS1_SHA256 = 0x0103
    private val B64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray()

    data class ZipInputEntry(
        val name: String,
        val data: ByteArray,
        val storeUncompressed: Boolean,
        val alignBytes: Int = 1,
    )

    fun packageAndSignApk(
        entries: List<ZipInputEntry>,
        appSignerName: String = "WebForge Studio",
        keyStoreDir: File? = null,
    ): ByteArray {
        val uniqueEntries = entries.associateBy { it.name }.values.toList()
        val (keyPair, certDer) = loadOrCreateSigningIdentity(keyStoreDir, appSignerName)

        val mfText = buildManifestMf(uniqueEntries)
        val mfBytes = mfText.toByteArray(Charsets.UTF_8)
        val sfBytes = buildCertSf(mfBytes).toByteArray(Charsets.UTF_8)
        val rsaBytes = buildPkcs7Signature(sfBytes, keyPair.private, certDer)

        val allEntries = uniqueEntries + listOf(
            ZipInputEntry("META-INF/MANIFEST.MF", mfBytes, storeUncompressed = false),
            ZipInputEntry("META-INF/CERT.SF", sfBytes, storeUncompressed = false),
            ZipInputEntry("META-INF/CERT.RSA", rsaBytes, storeUncompressed = false),
        )

        val rawZip = buildAlignedZip(allEntries)
        return applyApkSignatureV2(rawZip, keyPair, certDer)
    }

    private fun buildAlignedZip(entries: List<ZipInputEntry>): ByteArray {
        val out = ByteArrayOutputStream()
        val cdRecords = mutableListOf<ByteArray>()

        for (entry in entries) {
            val nameBytes = entry.name.toByteArray(Charsets.UTF_8)
            val localOffset = out.size()
            val crc = CRC32().apply { update(entry.data) }.value

            val method: Int
            val compressedData: ByteArray
            if (entry.storeUncompressed) {
                method = 0 // STORED
                compressedData = entry.data
            } else {
                method = 8 // DEFLATED
                compressedData = deflateRaw(entry.data)
            }

            // Compute extra padding for 4-byte alignment if STORED
            val headerEnd = localOffset + 30 + nameBytes.size
            val padLen = if (entry.storeUncompressed && entry.alignBytes > 1) {
                (entry.alignBytes - (headerEnd % entry.alignBytes)) % entry.alignBytes
            } else 0
            val extra = ByteArray(padLen)

            // Local file header (30 bytes + name + extra)
            val lfh = ByteBuffer.allocate(30 + nameBytes.size + extra.size).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(0x04034b50)
                putShort(20.toShort()) // version needed
                putShort(0x0800.toShort()) // UTF-8 flag
                putShort(method.toShort())
                putShort(0) // mod time
                putShort(0x5421.toShort()) // mod date
                putInt(crc.toInt())
                putInt(compressedData.size)
                putInt(entry.data.size)
                putShort(nameBytes.size.toShort())
                putShort(extra.size.toShort())
                put(nameBytes)
                put(extra)
            }.array()
            out.write(lfh)
            out.write(compressedData)

            // Central directory record (46 bytes + name)
            val cd = ByteBuffer.allocate(46 + nameBytes.size).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(0x02014b50)
                putShort(20.toShort()) // version made by
                putShort(20.toShort()) // version needed
                putShort(0x0800.toShort()) // UTF-8 flag
                putShort(method.toShort())
                putShort(0)
                putShort(0x5421.toShort())
                putInt(crc.toInt())
                putInt(compressedData.size)
                putInt(entry.data.size)
                putShort(nameBytes.size.toShort())
                putShort(0) // extra length
                putShort(0) // comment length
                putShort(0) // disk start
                putShort(0) // internal attrs
                putInt(0) // external attrs
                putInt(localOffset)
                put(nameBytes)
            }.array()
            cdRecords.add(cd)
        }

        val cdStart = out.size()
        cdRecords.forEach { out.write(it) }
        val cdSize = out.size() - cdStart

        val eocd = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0x06054b50)
            putShort(0)
            putShort(0)
            putShort(cdRecords.size.toShort())
            putShort(cdRecords.size.toShort())
            putInt(cdSize)
            putInt(cdStart)
            putShort(0)
        }.array()
        out.write(eocd)
        return out.toByteArray()
    }

    private fun applyApkSignatureV2(rawZip: ByteArray, keyPair: KeyPair, certDer: ByteArray): ByteArray {
        val eocdOffset = rawZip.size - 22
        val eocdForDigest = rawZip.copyOfRange(eocdOffset, rawZip.size)
        val eocdBuf = ByteBuffer.wrap(eocdForDigest).order(ByteOrder.LITTLE_ENDIAN)
        val cdSize = eocdBuf.getInt(12)
        val cdOffset = eocdBuf.getInt(16)

        val contents = rawZip.copyOfRange(0, cdOffset)
        val centralDir = rawZip.copyOfRange(cdOffset, cdOffset + cdSize)

        // Compute APK Signature Scheme v2 Merkle SHA-256 Digest
        val chunkDigests = mutableListOf<ByteArray>()
        appendSectionChunks(contents, chunkDigests)
        appendSectionChunks(centralDir, chunkDigests)
        appendSectionChunks(eocdForDigest, chunkDigests)

        val topDigestInput = ByteArrayOutputStream().apply {
            write(0x5A)
            write(int32LE(chunkDigests.size))
            chunkDigests.forEach { write(it) }
        }.toByteArray()
        val finalDigest = MessageDigest.getInstance("SHA-256").digest(topDigestInput)

        // Build SignedData
        val digestsList = lenPrefix32(
            ByteArrayOutputStream().apply {
                write(int32LE(SIG_ALG_RSA_PKCS1_SHA256))
                write(lenPrefix32(finalDigest))
            }.toByteArray(),
        )
        val certsList = lenPrefix32(certDer)
        val signedData = ByteArrayOutputStream().apply {
            write(lenPrefix32(digestsList))
            write(lenPrefix32(certsList))
            write(int32LE(0)) // additional attributes
        }.toByteArray()

        val sigBytes = Signature.getInstance("SHA256withRSA").run {
            initSign(keyPair.private)
            update(signedData)
            sign()
        }
        val signaturesList = lenPrefix32(
            ByteArrayOutputStream().apply {
                write(int32LE(SIG_ALG_RSA_PKCS1_SHA256))
                write(lenPrefix32(sigBytes))
            }.toByteArray(),
        )
        val pubKeyBytes = keyPair.public.encoded
        val signer = ByteArrayOutputStream().apply {
            write(lenPrefix32(signedData))
            write(lenPrefix32(signaturesList))
            write(lenPrefix32(pubKeyBytes))
        }.toByteArray()
        val signers = lenPrefix32(lenPrefix32(signer))

        // Pair: uint64(id+value length) + uint32(id) + value
        val pairValueLen = 4L + signers.size
        val pairTotalLen = 8 + 4 + signers.size
        val sizeAfterFirst8 = pairTotalLen.toLong() + 8L + 16L
        val sigBlock = ByteBuffer.allocate(8 + sizeAfterFirst8.toInt()).order(ByteOrder.LITTLE_ENDIAN).apply {
            putLong(sizeAfterFirst8)
            putLong(pairValueLen)
            putInt(APK_SIG_V2_BLOCK_ID)
            put(signers)
            putLong(sizeAfterFirst8)
            put("APK Sig Block 42".toByteArray(Charsets.US_ASCII))
        }.array()

        val newCdOffset = cdOffset + sigBlock.size
        val updatedEocd = eocdForDigest.copyOf()
        ByteBuffer.wrap(updatedEocd).order(ByteOrder.LITTLE_ENDIAN).putInt(16, newCdOffset)

        return ByteArrayOutputStream(rawZip.size + sigBlock.size).apply {
            write(contents)
            write(sigBlock)
            write(centralDir)
            write(updatedEocd)
        }.toByteArray()
    }

    private fun appendSectionChunks(section: ByteArray, outDigests: MutableList<ByteArray>) {
        var offset = 0
        val md = MessageDigest.getInstance("SHA-256")
        if (section.isEmpty()) return
        while (offset < section.size) {
            val len = minOf(CHUNK_SIZE, section.size - offset)
            md.reset()
            md.update(0xA5.toByte())
            md.update(int32LE(len))
            md.update(section, offset, len)
            outDigests.add(md.digest())
            offset += len
        }
    }

    private fun buildManifestMf(entries: List<ZipInputEntry>): String = buildString {
        append("Manifest-Version: 1.0\r\nCreated-By: WebForge Android Builder\r\n\r\n")
        val md = MessageDigest.getInstance("SHA-256")
        entries.forEach { e ->
            val b64 = encodeBase64(md.digest(e.data))
            append(wrapManifestAttribute("Name: ${e.name}"))
            append("\r\n")
            append(wrapManifestAttribute("SHA-256-Digest: $b64"))
            append("\r\n\r\n")
        }
    }

    private fun buildCertSf(manifestBytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val manifestB64 = encodeBase64(md.digest(manifestBytes))
        val manifestText = manifestBytes.toString(Charsets.UTF_8)
        val sections = manifestText.split("\r\n\r\n").drop(1).filter { it.isNotBlank() }
        return buildString {
            append("Signature-Version: 1.0\r\nCreated-By: WebForge Android Builder\r\n")
            append("SHA-256-Digest-Manifest: $manifestB64\r\n")
            append("X-Android-APK-Signed: 2\r\n\r\n")
            sections.forEach { sec ->
                val rawSec = "$sec\r\n\r\n".toByteArray(Charsets.UTF_8)
                val secDigest = encodeBase64(md.digest(rawSec))
                val nameHeaderLines = sec.substringBefore("\r\nSHA-256-Digest:")
                append(nameHeaderLines)
                append("\r\n")
                append(wrapManifestAttribute("SHA-256-Digest: $secDigest"))
                append("\r\n\r\n")
            }
        }
    }

    private fun wrapManifestAttribute(line: String): String {
        val bytes = line.toByteArray(Charsets.UTF_8)
        if (bytes.size <= 70) return line
        val sb = StringBuilder()
        var i = 0
        var first = true
        while (i < line.length) {
            val maxBytes = if (first) 70 else 69
            var end = i
            var byteCount = 0
            while (end < line.length) {
                val chLen = line.substring(end, end + 1).toByteArray(Charsets.UTF_8).size
                if (byteCount + chLen > maxBytes && end > i) break
                byteCount += chLen
                end++
            }
            if (!first) sb.append("\r\n ")
            sb.append(line.substring(i, end))
            i = end
            first = false
        }
        return sb.toString()
    }

    private fun encodeBase64(data: ByteArray): String {
        val sb = StringBuilder(((data.size + 2) / 3) * 4)
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val hasB1 = i + 1 < data.size
            val hasB2 = i + 2 < data.size
            val b1 = if (hasB1) data[i + 1].toInt() and 0xFF else 0
            val b2 = if (hasB2) data[i + 2].toInt() and 0xFF else 0
            sb.append(B64_CHARS[b0 ushr 2])
            sb.append(B64_CHARS[((b0 and 0x03) shl 4) or (b1 ushr 4)])
            sb.append(if (hasB1) B64_CHARS[((b1 and 0x0F) shl 2) or (b2 ushr 6)] else '=')
            sb.append(if (hasB2) B64_CHARS[b2 and 0x3F] else '=')
            i += 3
        }
        return sb.toString()
    }

    // Minimal valid ASN.1 DER PKCS#7 SignedData wrapping the SHA256withRSA signature + X.509 certificate
    private fun buildPkcs7Signature(sfBytes: ByteArray, privateKey: PrivateKey, certDer: ByteArray): ByteArray {
        val digestAlgoSha256 = derSeq(derOid("2.16.840.1.101.3.4.2.1") + derNull())
        val sigAlgoRsa = derSeq(derOid("1.2.840.113549.1.1.1") + derNull())
        val contentInfo = derSeq(derOid("1.2.840.113549.1.7.1")) // pkcs7-data (detached)

        // Extract Issuer & SerialNumber from TBSCertificate inside certDer
        val certSeq = readDerChildren(certDer)
        val tbsChildren = readDerChildren(certSeq[0])
        val serialDer = tbsChildren[1]
        val issuerDer = tbsChildren[3]
        val issuerAndSerial = derSeq(issuerDer + serialDer)

        val sigBytes = Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update(sfBytes)
            sign()
        }

        val signerInfo = derSeq(
            derInt(1) + // version
                issuerAndSerial +
                digestAlgoSha256 +
                sigAlgoRsa +
                derOctetString(sigBytes),
        )
        val signedData = derSeq(
            derInt(1) + // version
                derSet(digestAlgoSha256) +
                contentInfo +
                derExplicitTag(0, certDer) +
                derSet(signerInfo),
        )
        return derSeq(
            derOid("1.2.840.113549.1.7.2") + // pkcs7-signedData
                derExplicitTag(0, signedData),
        )
    }

    private fun loadOrCreateSigningIdentity(dir: File?, cn: String): Pair<KeyPair, ByteArray> {
        if (dir != null) {
            val privFile = File(dir, "signing_private.pk8")
            val pubFile = File(dir, "signing_public.x509")
            val certFile = File(dir, "signing_cert.der")
            if (privFile.exists() && pubFile.exists() && certFile.exists()) {
                val loaded = runCatching {
                    val kf = KeyFactory.getInstance("RSA")
                    val priv = kf.generatePrivate(PKCS8EncodedKeySpec(privFile.readBytes()))
                    val pub = kf.generatePublic(X509EncodedKeySpec(pubFile.readBytes()))
                    val cert = certFile.readBytes()
                    check(cert.isNotEmpty())
                    KeyPair(pub, priv) to cert
                }.getOrNull()
                if (loaded != null) return loaded
            }
        }
        val keyPair = generateRsa2048KeyPair()
        val certDer = generateSelfSignedX509Cert(keyPair, cn)
        if (dir != null) {
            runCatching {
                dir.mkdirs()
                File(dir, "signing_private.pk8").writeBytes(keyPair.private.encoded)
                File(dir, "signing_public.x509").writeBytes(keyPair.public.encoded)
                File(dir, "signing_cert.der").writeBytes(certDer)
            }
        }
        return keyPair to certDer
    }

    private fun generateRsa2048KeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        return kpg.generateKeyPair()
    }

    private fun generateSelfSignedX509Cert(keyPair: KeyPair, cn: String): ByteArray {
        val safeCn = cn.replace(Regex("[^a-zA-Z0-9 _-]"), "").ifBlank { "WebForge" }.take(32)
        val serial = BigInteger.valueOf((System.currentTimeMillis() and 0x7FFFFFFFL).coerceAtLeast(1L))
        val sigAlg = derSeq(derOid("1.2.840.113549.1.1.11") + derNull()) // sha256WithRSAEncryption
        val name = derSeq(
            derSet(
                derSeq(derOid("2.5.4.3") + derUtf8String(safeCn)),
            ),
        )
        val now = Date(System.currentTimeMillis() - 86_400_000L)
        val future = Date(System.currentTimeMillis() + 3650L * 86_400_000L)
        val fmt = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val validity = derSeq(derUtcTime(fmt.format(now)) + derUtcTime(fmt.format(future)))

        val tbs = derSeq(
            derExplicitTag(0, derInt(2)) + // v3
                derBigInt(serial) +
                sigAlg +
                name +
                validity +
                name +
                keyPair.public.encoded,
        )
        val sig = Signature.getInstance("SHA256withRSA").run {
            initSign(keyPair.private)
            update(tbs)
            sign()
        }
        val bitStringSig = derBitString(sig)
        return derSeq(tbs + sigAlg + bitStringSig)
    }

    private fun deflateRaw(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        DeflaterOutputStream(out, deflater).use { it.write(input) }
        deflater.end()
        return out.toByteArray()
    }

    private fun lenPrefix32(bytes: ByteArray): ByteArray = int32LE(bytes.size) + bytes
    private fun int32LE(v: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()

    // Minimal ASN.1 DER primitives
    private fun derTag(tag: Int, content: ByteArray): ByteArray {
        val len = content.size
        val lenBytes = when {
            len < 128 -> byteArrayOf(len.toByte())
            len <= 0xFF -> byteArrayOf(0x81.toByte(), len.toByte())
            len <= 0xFFFF -> byteArrayOf(0x82.toByte(), (len shr 8).toByte(), len.toByte())
            else -> byteArrayOf(0x83.toByte(), (len shr 16).toByte(), (len shr 8).toByte(), len.toByte())
        }
        return byteArrayOf(tag.toByte()) + lenBytes + content
    }

    private fun derSeq(content: ByteArray) = derTag(0x30, content)
    private fun derSet(content: ByteArray) = derTag(0x31, content)
    private fun derExplicitTag(num: Int, content: ByteArray) = derTag(0xA0 or num, content)
    private fun derNull() = byteArrayOf(0x05, 0x00)
    private fun derInt(v: Int) = derTag(0x02, BigInteger.valueOf(v.toLong()).toByteArray())
    private fun derBigInt(v: BigInteger) = derTag(0x02, v.toByteArray())
    private fun derOctetString(b: ByteArray) = derTag(0x04, b)
    private fun derBitString(b: ByteArray) = derTag(0x03, byteArrayOf(0x00) + b)
    private fun derUtf8String(s: String) = derTag(0x0C, s.toByteArray(Charsets.UTF_8))
    private fun derUtcTime(s: String) = derTag(0x17, s.toByteArray(Charsets.US_ASCII))

    private fun derOid(oid: String): ByteArray {
        val parts = oid.split('.').map { it.toInt() }
        val out = ByteArrayOutputStream()
        out.write(parts[0] * 40 + parts[1])
        for (i in 2 until parts.size) {
            var v = parts[i]
            val stack = ArrayDeque<Int>()
            stack.addFirst(v and 0x7F)
            v = v ushr 7
            while (v > 0) {
                stack.addFirst((v and 0x7F) or 0x80)
                v = v ushr 7
            }
            stack.forEach { out.write(it) }
        }
        return derTag(0x06, out.toByteArray())
    }

    private fun readDerChildren(tlv: ByteArray): List<ByteArray> {
        var idx = 1
        val lenByte = tlv[idx++].toInt() and 0xFF
        if ((lenByte and 0x80) != 0) {
            idx += (lenByte and 0x7F)
        }
        val list = mutableListOf<ByteArray>()
        while (idx < tlv.size) {
            val start = idx
            idx++ // tag
            val lb = tlv[idx++].toInt() and 0xFF
            val len = if ((lb and 0x80) == 0) lb else {
                val count = lb and 0x7F
                var acc = 0
                repeat(count) { acc = (acc shl 8) or (tlv[idx++].toInt() and 0xFF) }
                acc
            }
            val end = idx + len
            list.add(tlv.copyOfRange(start, end))
            idx = end
        }
        return list
    }
}
