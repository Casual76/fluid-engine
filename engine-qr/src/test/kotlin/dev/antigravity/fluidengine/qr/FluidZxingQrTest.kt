package dev.antigravity.fluidengine.qr

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F8.10 · Il QR del foglio si rilegge: il codice che esce dal codificatore, disegnato a 4 pixel per modulo con la zona
 * vuota di 4 moduli (come lo disegna `FluidQrCode`), torna lo stesso testo col lettore di ZXing. Sulla JVM, senza Android.
 */
class FluidZxingQrTest {
  private fun decode(text: String): String {
    val matrix = assertNotNullAndGet(FluidZxingQr.encode(text))
    val scale = 4
    val quiet = 4
    val side = (matrix.size + quiet * 2) * scale
    val pixels = ByteArray(side * side) { 0xFF.toByte() }
    for (y in 0 until matrix.size) for (x in 0 until matrix.size) {
      if (!matrix[x, y]) continue
      for (dy in 0 until scale) for (dx in 0 until scale) {
        pixels[((y + quiet) * scale + dy) * side + (x + quiet) * scale + dx] = 0
      }
    }
    val source = object : LuminanceSource(side, side) {
      override fun getRow(y: Int, row: ByteArray?): ByteArray = pixels.copyOfRange(y * side, (y + 1) * side)
      override fun getMatrix(): ByteArray = pixels
    }
    return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), mapOf(DecodeHintType.CHARACTER_SET to "UTF-8")).text
  }

  private fun assertNotNullAndGet(value: dev.antigravity.fluidengine.ui.fluid.share.FluidQrMatrix?) = value.also { assertNotNull(it) }!!

  @Test fun linkRoundTrips() {
    val url = "https://pampa.example/app/dev.antigravity.classeviva?ref=qr"
    assertEquals(url, decode(url))
  }

  @Test fun accentedTextRoundTrips() {
    val text = "Verifica di matematica — giovedì 26/11, 8 ½ · così è"
    assertEquals(text, decode(text))
  }

  @Test fun matrixIsSquareWithoutMargin() {
    val matrix = FluidZxingQr.encode("https://example.org")!!
    // Versione 2 (25 moduli) per un link corto con la correzione M; i tre occhi negli angoli sono pieni.
    assertTrue("lato ${matrix.size}", matrix.size in 21..29)
    assertTrue(matrix[0, 0] && matrix[matrix.size - 1, 0] && matrix[0, matrix.size - 1])
  }

  @Test fun emptyAndTooLongGiveNull() {
    assertNull(FluidZxingQr.encode(""))
    assertNull(FluidZxingQr.encode("x".repeat(5000)))
  }
}
