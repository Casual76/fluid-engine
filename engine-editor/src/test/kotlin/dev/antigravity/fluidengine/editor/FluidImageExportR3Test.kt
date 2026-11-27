package dev.antigravity.fluidengine.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * R3 (review R3, Muse 3, 11 e 12) · Il salvataggio dell'editor:
 * - una codifica che fallisce a meta' (il disco pieno) non esce come un successo e non rovina il file che c'era;
 * - di serie la posizione GPS dello scatto resta nella copia, come Foto di iOS (scelta di Alessio, R3-M2b), e
 *   `keepLocation = false` la toglie davvero, campo per campo;
 * - la bitmap del risultato sta nella memoria nativa, non nell'heap Java: il tetto di pixel di serie si misura sulla RAM
 *   del telefono (Muse 11 non confermato, la prova del perche').
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], shadows = [FailingCompressBitmap::class])
class FluidImageExportR3Test {

  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
  private val dir = File(context.cacheDir, "r3-export").apply { mkdirs() }

  @After fun clean() {
    FailingCompressBitmap.failJpegQuality = -1
    FailingCompressBitmap.failures = 0
    dir.deleteRecursively()
  }

  private fun photo(w: Int, h: Int): Bitmap {
    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(b)
    val p = Paint()
    p.color = Color.RED; c.drawRect(0f, 0f, w / 2f, h.toFloat(), p)
    p.color = Color.BLUE; c.drawRect(w / 2f, 0f, w.toFloat(), h.toFloat(), p)
    return b
  }

  private fun save(bitmap: Bitmap, name: String, format: Bitmap.CompressFormat): File =
    File(dir, name).also { f -> f.outputStream().use { bitmap.compress(format, 100, it) } }

  @Test fun unaCodificaCheFallisceNonEUnSuccessoENonRovinaIlFileDiPrima() = runBlocking {
    val src = save(photo(400, 300), "foto.png", Bitmap.CompressFormat.PNG)
    // Il file scelto dall'app c'e' gia' (una versione salvata prima): resta com'era se il salvataggio nuovo non riesce.
    val target = File(dir, "modificata.jpg").apply { writeBytes("la versione di prima".toByteArray()) }
    val before = target.readBytes()
    FailingCompressBitmap.failJpegQuality = 95
    try {
      val result = FluidImageExport.export(context, FluidEditorSource.uri(Uri.fromFile(src)), FluidImageEdit.Original, target)
      fail("il codificatore ha scritto un terzo del file e l'export dice di avercela fatta: $result, ${target.length()} byte")
    } catch (expected: IOException) {
    }
    assertEquals("la codifica e' fallita davvero (l'ombra e' passata)", 1, FailingCompressBitmap.failures)
    assertArrayEquals("il file di prima e' rimasto intero", before, target.readBytes())
    // Nessun file a meta' accanto.
    assertEquals(listOf("foto.png", "modificata.jpg"), dir.listFiles().orEmpty().map { it.name }.sorted())
    // E lo stesso dallo stato dell'editor, verso un indirizzo (passa da un file nella cache).
    val state = FluidImageEditorState(FluidEditorSource.uri(Uri.fromFile(src)), FluidImageEdit.Original, FluidImageEdit.Original, context)
    val viaUri = File(dir, "via-uri.jpg").apply { writeBytes(before) }
    try {
      state.export(Uri.fromFile(viaUri))
      fail("export verso un indirizzo dopo una codifica fallita")
    } catch (expected: IOException) {
    }
    assertArrayEquals("l'indirizzo non e' stato toccato", before, viaUri.readBytes())
  }

  @Test fun diSerieLaPosizioneRestaEKeepLocationFalseLaToglie() = runBlocking {
    val src = save(photo(400, 300), "scatto.jpg", Bitmap.CompressFormat.JPEG)
    ExifInterface(src.absolutePath).apply {
      setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:08:14 18:30:00")
      setAttribute(ExifInterface.TAG_GPS_LATITUDE, "43/1,46/1,12/1")
      setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
      setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "11/1,15/1,0/1")
      setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
      saveAttributes()
    }
    assertTrue("la foto di prova ha la posizione", ExifInterface(src.absolutePath).getLatLong(FloatArray(2)))
    val state = FluidImageEditorState(FluidEditorSource.uri(Uri.fromFile(src)), FluidImageEdit.Original, FluidImageEdit.Original, context)
    // Di serie (scelta di Alessio, R3-M2b): la posizione resta, come in Foto di iOS. L'export diretto e quello dello stato.
    val library = File(dir, "libreria.jpg")
    FluidImageExport.export(context, FluidEditorSource.uri(Uri.fromFile(src)), FluidImageEdit.Original, library)
    val fromState = File(dir, "dallo-stato.jpg")
    state.export(fromState)
    for (file in listOf(library, fromState)) {
      val place = FloatArray(2)
      assertTrue("${file.name}: di serie la posizione non e' rimasta", ExifInterface(file.absolutePath).getLatLong(place))
      assertEquals(43.77f, place[0], 0.01f)
      assertEquals(11.25f, place[1], 0.01f)
    }
    // Con keepLocation = false (un allegato che parte senza il foglio di condivisione) si toglie davvero.
    val attachment = File(dir, "allegato.jpg")
    FluidImageExport.export(context, FluidEditorSource.uri(Uri.fromFile(src)), FluidImageEdit.Original, attachment,
      keepLocation = false)
    val fromStateWithout = File(dir, "dallo-stato-senza.jpg")
    state.export(fromStateWithout, keepLocation = false)
    for (file in listOf(attachment, fromStateWithout)) {
      val exif = ExifInterface(file.absolutePath)
      // I campi GPS uno per uno, non una ricerca nel testo: nessuno deve esserci.
      for (tag in listOf(ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP)) {
        assertNull("${file.name}: $tag = ${exif.getAttribute(tag)}", exif.getAttribute(tag))
      }
      assertFalse("${file.name}: la posizione e' rimasta", exif.getLatLong(FloatArray(2)))
      // I metadati di scatto restano.
      assertEquals("2026:08:14 18:30:00", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
    }
  }

  /**
   * Muse 11 dice che il risultato da 50 megapixel (200 MB) finisce nell'heap Java (256 MB su un telefono da 8 GB). Da
   * Android 8 (l'engine parte da 8) i pixel di una bitmap stanno nella memoria nativa: l'heap Java non li conta. Qui il
   * risultato vero dell'export (12 megapixel, 48 MB di pixel) e l'heap Java misurato prima e dopo, con la bitmap viva.
   */
  @Test fun laBitmapDelRisultatoNonStaNellHeapJava() = runBlocking {
    val big = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
    val src = save(big, "grande.jpg", Bitmap.CompressFormat.JPEG)
    big.recycle()
    val runtime = Runtime.getRuntime()
    fun javaHeap(): Long {
      repeat(3) { System.gc(); Thread.sleep(50) }
      return runtime.totalMemory() - runtime.freeMemory()
    }
    val before = javaHeap()
    val rendered = FluidImageExport.renderFull(context, FluidEditorSource.uri(Uri.fromFile(src)), FluidImageEdit.Original, maxPixels = 50_000_000L)
    val during = javaHeap()
    val pixels = rendered.bitmap.byteCount.toLong()
    println("R3 heap Java: prima ${before / 1024 / 1024} MiB, con il risultato vivo ${during / 1024 / 1024} MiB; pixel del risultato ${pixels / 1024 / 1024} MiB; tetto di serie ${FluidImageExport.defaultMaxPixels(context)} pixel")
    assertEquals(4000, rendered.width)
    assertTrue("i pixel del risultato contano nell'heap Java: +${(during - before) / 1024 / 1024} MiB", during - before < pixels / 4)
    rendered.bitmap.recycle()
  }
}
