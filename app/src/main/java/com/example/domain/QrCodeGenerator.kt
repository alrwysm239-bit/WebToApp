package com.example.domain

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.ByteArrayOutputStream

data class QrMatrix(
    val size: Int,
    val bits: BooleanArray,
)

object QrCodeGenerator {

    fun encodeMatrix(content: String, sizeHint: Int = 48): QrMatrix {
        val text = content.ifBlank { "https://webforge.app" }
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizeHint, sizeHint, hints)
        val w = matrix.width
        val h = matrix.height
        val bits = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                bits[y * w + x] = matrix[x, y]
            }
        }
        return QrMatrix(size = w, bits = bits)
    }

    fun generatePngBytes(content: String, pixelSize: Int = 720): ByteArray {
        val text = content.ifBlank { "https://webforge.app" }
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, pixelSize, pixelSize, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        val darkColor = Color.rgb(12, 10, 32)
        val lightColor = Color.WHITE
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix[x, y]) darkColor else lightColor
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }
}
