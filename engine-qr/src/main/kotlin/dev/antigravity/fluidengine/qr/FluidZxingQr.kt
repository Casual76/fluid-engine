/*
 * engine-qr (4.0, F8.10): il codificatore dei QR per «Mostra come QR» del foglio di condivisione, con ZXing core
 * (Apache-2.0). Il contratto e il disegno stanno in engine-ui; qui c'e' solo il calcolo. Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.qr

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dev.antigravity.fluidengine.ui.fluid.FluidLicense
import dev.antigravity.fluidengine.ui.fluid.share.FluidQrEncoder
import dev.antigravity.fluidengine.ui.fluid.share.FluidQrMatrix
import dev.antigravity.fluidengine.ui.fluid.share.LocalFluidQrEncoder

/**
 * Il codificatore QR dell'engine: ZXing core, correzione d'errore **M** (il 15% del codice si puo' sporcare o coprire:
 * quella che usano Apple e le app di pagamento per un codice mostrato su uno schermo), testo in UTF-8, nessuna zona vuota
 * (la aggiunge [dev.antigravity.fluidengine.ui.fluid.share.FluidQrCode]). Un testo che non entra in un QR torna null.
 */
object FluidZxingQr : FluidQrEncoder {
  override fun encode(text: String): FluidQrMatrix? {
    if (text.isEmpty()) return null
    val hints = mapOf(
      EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
      EncodeHintType.CHARACTER_SET to "UTF-8",
      EncodeHintType.MARGIN to 0,
    )
    val bits = try {
      QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
    } catch (_: WriterException) {
      return null
    } catch (_: IllegalArgumentException) {
      return null
    }
    // Con le misure a zero ZXing da' un pixel per modulo: la matrice e' gia' quella del codice.
    val size = bits.width
    return FluidQrMatrix(size, BooleanArray(size * size) { bits[it % size, it / size] })
  }
}

/**
 * Accende i QR per [content]: dentro, il foglio di condivisione mostra «Mostra come QR». Si mette una volta, intorno
 * alla radice dell'app (accanto a `FluidTheme`), o intorno alla sola pagina che condivide.
 */
@Composable
fun ProvideFluidQr(content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalFluidQrEncoder provides FluidZxingQr, content = content)
}

/** Da aggiungere a `FluidEngineLicenses` in `fluidLicensesSection(licences = …)` quando l'app include engine-qr. */
val FluidQrLicenses: List<FluidLicense> = listOf(
  FluidLicense(
    name = "ZXing",
    author = "ZXing authors",
    licence = "Apache License 2.0",
    url = "https://github.com/zxing/zxing",
    role = "Il codice QR di un link o di un testo, nel foglio di condivisione.",
  ),
)
