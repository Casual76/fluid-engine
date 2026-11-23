package dev.antigravity.fluidengine.viewer.graphics

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.ImageBitmap
import dev.antigravity.fluidengine.viewer.FluidThumbnailPainter
import dev.antigravity.fluidengine.viewer.FluidViewerRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * F6C.17 · La miniatura di un carattere, come quella di File e del Libro Font: «Aa» grande nel carattere stesso, su un
 * foglio, con sotto l'alfabeto e il nome della famiglia. Per una collezione, il primo carattere. Il file si scrive in
 * cache per il tempo del disegno, come fa la pagina.
 */
internal suspend fun fontThumbnail(request: FluidViewerRequest, sizePx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
  val font = FontFile(request.source.open().use { it.readGraphic(MaxFontBytes) })
  val face = font.faces.firstOrNull() ?: return@withContext null
  val file = File.createTempFile("viewer-font-thumb-", ".sfnt", request.context.cacheDir)
  try {
    file.writeBytes(font.bytes)
    val typeface = Typeface.Builder(file).setTtcIndex(0).build() ?: return@withContext null
    FluidThumbnailPainter.page(sizePx) { canvas, content, unit ->
      val big = FluidThumbnailPainter.paint(content.height() * 0.34f).apply {
        this.typeface = typeface
        textAlign = Paint.Align.CENTER
      }
      while (big.measureText(Sample) > content.width() * 0.92f && big.textSize > 4f) big.textSize *= 0.92f
      canvas.drawText(Sample, content.centerX(), content.top + content.height() * 0.5f, big)
      val alphabet = FluidThumbnailPainter.paint(unit * 0.72f).apply {
        this.typeface = typeface
        textAlign = Paint.Align.CENTER
      }
      canvas.drawText(FluidThumbnailPainter.ellipsize(Alphabet, alphabet, content.width()).toString(), content.centerX(),
        content.top + content.height() * 0.7f, alphabet)
      val name = FluidThumbnailPainter.paint(unit * 0.6f, FluidThumbnailPainter.InkSecondary).apply { textAlign = Paint.Align.CENTER }
      canvas.drawText(FluidThumbnailPainter.ellipsize(face.family, name, content.width()).toString(), content.centerX(),
        content.bottom - unit * 0.6f, name)
    }
  } finally {
    file.delete()
  }
}

/** Il campione grande, come su iOS e macOS. */
private const val Sample = "Aa"

/** La riga piccola sotto il campione. */
private const val Alphabet = "ABCDEFG abcdefg 0123"
