package dev.antigravity.fluidengine.ui.fluid.share

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Un codice QR gia' calcolato: [size] × [size] moduli, scuri dove [get] e' vero, senza la zona vuota intorno (la aggiunge
 * chi lo disegna, [FluidQrCode]). Lo produce un [FluidQrEncoder]; l'engine non porta un codificatore in `engine-ui`.
 */
@Immutable
class FluidQrMatrix(val size: Int, private val dark: BooleanArray) {
  init {
    require(size > 0 && dark.size == size * size) { "Una matrice QR e' quadrata: ${dark.size} moduli per lato $size" }
  }

  /** Vero se il modulo in colonna [x] e riga [y] e' scuro. */
  operator fun get(x: Int, y: Int): Boolean = dark[y * size + x]
}

/**
 * Chi trasforma un testo in un QR. `engine-ui` non lo porta: lo da' il modulo facoltativo `engine-qr` (ZXing core,
 * Apache-2.0), che l'app include se vuole «Mostra come QR». Senza codificatore il foglio non mostra quell'azione.
 */
fun interface FluidQrEncoder {
  /** Il QR di [text], o null se il testo non entra in un codice. */
  fun encode(text: String): FluidQrMatrix?
}

/** Il codificatore dei QR della composizione; null (di serie) toglie «Mostra come QR» dal foglio. */
val LocalFluidQrEncoder: ProvidableCompositionLocal<FluidQrEncoder?> = staticCompositionLocalOf { null }

/**
 * Un QR disegnato: moduli [color] su [background], con la zona vuota di 4 moduli che i lettori chiedono (ISO/IEC 18004).
 * Il codice si fa bitmap una volta sola, un pixel per modulo, e si ingrandisce senza filtro: i bordi restano netti a ogni
 * taglia, e un ridisegno non ricalcola niente. Il contrasto e' quello dell'app: [color] scuro su [background] chiaro,
 * mai il contrario (molti lettori non leggono un QR invertito).
 */
@Composable
fun FluidQrCode(
  matrix: FluidQrMatrix,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  color: Color = Color.Black,
  background: Color = Color.White,
) {
  val quiet = FluidQrQuietZone
  val image = remember(matrix, color, background) {
    val side = matrix.size + quiet * 2
    val pixels = IntArray(side * side) { background.toArgb() }
    val ink = color.toArgb()
    for (y in 0 until matrix.size) for (x in 0 until matrix.size) {
      if (matrix[x, y]) pixels[(y + quiet) * side + x + quiet] = ink
    }
    Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888).asImageBitmap()
  }
  val described = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
  Canvas(modifier.aspectRatio(1f).then(described)) {
    val side = minOf(size.width, size.height).toInt()
    drawImage(
      image = image,
      srcOffset = IntOffset.Zero,
      srcSize = IntSize(image.width, image.height),
      dstOffset = IntOffset(((size.width - side) / 2).toInt(), ((size.height - side) / 2).toInt()),
      dstSize = IntSize(side, side),
      filterQuality = FilterQuality.None,
    )
  }
}

/** La zona vuota attorno al codice, in moduli: 4, il minimo della norma. */
internal const val FluidQrQuietZone: Int = 4
