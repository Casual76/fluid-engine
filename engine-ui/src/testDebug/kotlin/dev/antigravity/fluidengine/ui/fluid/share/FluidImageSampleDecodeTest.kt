package dev.antigravity.fluidengine.ui.fluid.share

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import dev.antigravity.fluidengine.ui.fluid.output.fluidDecodeForPrint
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * F8.10 · la lettura a campioni di un'immagine vera, da un Uri: la miniatura del foglio di condivisione e l'immagine da
 * stampare passano dallo stesso posto (`FluidImageSample`). La stampa aveva lo stesso difetto della miniatura del
 * 25 novembre (la lettura delle misure, che torna sempre null, faceva uscire tutto): prima tornava sempre null.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FluidImageSampleDecodeTest {
  private val context: Context get() = RuntimeEnvironment.getApplication()
  private val dir get() = File(context.filesDir, "f8-10-campioni")

  @After fun stop() {
    dir.deleteRecursively()
  }

  private fun png(name: String, width: Int, height: Int): Uri {
    val file = File(dir.apply { mkdirs() }, name)
    val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(220, 30, 30)) }
    file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return Uri.fromFile(file)
  }

  @Test fun aThumbnailIsReadAtASampleThatStillFillsItsBox() {
    val thumbnail = assertNotNullAndGet(fluidLoadThumbnail(context.contentResolver, png("Grande.png", 1600, 1200), 128))
    // 1200 / 8 = 150 (sopra 128), 1200 / 16 = 75 (sotto): un ottavo.
    assertEquals(200, thumbnail.width)
    assertEquals(150, thumbnail.height)
  }

  @Test fun anImageToPrintIsReadNoLongerThanAnA4At300Dpi() {
    val whole = fluidDecodeForPrint(context.contentResolver, png("Foto.png", 1200, 900))
    assertNotNull("l'immagine da stampare non arriva (la lettura delle misure faceva uscire null)", whole)
    assertEquals(1200, whole!!.width)
    val reduced = fluidDecodeForPrint(context.contentResolver, png("Poster.png", 1200, 900), maxSide = 500)
    assertEquals(300, reduced!!.width)
    assertEquals(225, reduced.height)
  }

  @Test fun somethingThatIsNotAnImageGivesNothing() {
    val file = File(dir.apply { mkdirs() }, "Nota.txt").apply { writeText("non sono un'immagine") }
    assertNull(fluidLoadThumbnail(context.contentResolver, Uri.fromFile(file), 64))
    assertNull(fluidDecodeForPrint(context.contentResolver, Uri.fromFile(file)))
  }

  private fun <T : Any> assertNotNullAndGet(value: T?): T {
    assertNotNull(value)
    return value!!
  }
}
