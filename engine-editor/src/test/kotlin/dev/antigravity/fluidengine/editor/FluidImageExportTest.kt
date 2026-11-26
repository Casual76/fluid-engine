package dev.antigravity.fluidengine.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F8.15 · Il salvataggio coi file, le bitmap e il Canvas veri di Android (Robolectric, grafica nativa): il ritaglio, la
 * rotazione e lo specchio finiscono nei pixel giusti; l'EXIF del verso si applica prima e si scrive «normale» dopo; i
 * metadati di scatto restano, la posizione solo se si vuole; il file di partenza resta identico; una foto piu' grande
 * del tetto esce ridotta e lo dice; la lettura a strisce da' gli stessi pixel di quella intera. E lo shader dell'anteprima
 * da' gli stessi numeri del calcolo della CPU.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FluidImageExportTest {

  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
  private val dir = File(context.cacheDir, "f8p15-export").apply { mkdirs() }

  @After fun clean() {
    dir.deleteRecursively()
    FluidImageExport.stripPixels = 4_000_000
  }

  /** Una foto di prova a quattro quadranti: rosso in alto a sinistra, verde in alto a destra, blu in basso a sinistra, bianco. */
  private fun quadrants(w: Int, h: Int): Bitmap {
    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(b)
    val p = Paint()
    p.color = Color.RED; c.drawRect(0f, 0f, w / 2f, h / 2f, p)
    p.color = Color.GREEN; c.drawRect(w / 2f, 0f, w.toFloat(), h / 2f, p)
    p.color = Color.BLUE; c.drawRect(0f, h / 2f, w / 2f, h.toFloat(), p)
    p.color = Color.WHITE; c.drawRect(w / 2f, h / 2f, w.toFloat(), h.toFloat(), p)
    return b
  }

  private fun save(bitmap: Bitmap, name: String, format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG): File =
    File(dir, name).also { f -> f.outputStream().use { bitmap.compress(format, 100, it) } }

  private fun near(c: Int, r: Int, g: Int, b: Int, tol: Int = 40) =
    abs(Color.red(c) - r) <= tol && abs(Color.green(c) - g) <= tol && abs(Color.blue(c) - b) <= tol

  private fun decode(f: File) = BitmapFactory.decodeFile(f.absolutePath)!!

  @Test fun ilRitaglioELaRotazioneFinisconoNeiPixelGiusti() = runBlocking {
    val src = save(quadrants(400, 300), "q.png")
    // Solo il quadrante in alto a destra (verde), girato a sinistra: esce alto 200 e largo 150.
    val g = FluidCropGeometry(centerX = 0.75f, centerY = 0.25f, width = 0.5f, height = 0.5f).rotatedLeft()
    val out = File(dir, "out.png")
    val result = FluidImageExport.export(context, FluidEditorSource.uri(Uri.fromFile(src)), FluidImageEdit(g), out,
      FluidImageExport.Format.Png)
    assertEquals(150, result.width)
    assertEquals(200, result.height)
    assertFalse(result.reduced)
    val bmp = decode(out)
    assertEquals(150, bmp.width)
    assertTrue(near(bmp.getPixel(75, 100), 0, 255, 0))
    assertTrue(near(bmp.getPixel(3, 3), 0, 255, 0))
  }

  @Test fun loSpecchioScambiaISinistraEDestra() = runBlocking {
    val src = save(quadrants(400, 300), "q.png")
    val out = File(dir, "out.png")
    FluidImageExport.export(context, FluidEditorSource.uri(Uri.fromFile(src)),
      FluidImageEdit(FluidCropGeometry().flippedHorizontally()), out, FluidImageExport.Format.Png)
    val bmp = decode(out)
    assertTrue(near(bmp.getPixel(50, 50), 0, 255, 0))   // il verde ora e' a sinistra
    assertTrue(near(bmp.getPixel(350, 50), 255, 0, 0))  // il rosso a destra
  }

  @Test fun lEsifDelVersoSiApplicaPrimaESiScriveNormale() = runBlocking {
    // Un JPEG «girato» dall'EXIF di 90 gradi: dritto, il rosso sta in alto a destra.
    val src = save(quadrants(400, 300), "girata.jpg", Bitmap.CompressFormat.JPEG)
    ExifInterface(src.absolutePath).apply {
      setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
      setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:08:14 18:30:00")
      setAttribute(ExifInterface.TAG_MAKE, "Prova")
      setAttribute(ExifInterface.TAG_GPS_LATITUDE, "43/1,46/1,12/1")
      setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
      setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "11/1,15/1,0/1")
      setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
      saveAttributes()
    }
    val before = src.readBytes()
    val out = File(dir, "out.jpg")
    val result = FluidImageExport.export(context, FluidEditorSource.uri(Uri.fromFile(src)), FluidImageEdit.Original, out,
      keepLocation = false)
    assertEquals(300, result.width)
    assertEquals(400, result.height)
    val bmp = decode(out)
    assertTrue(near(bmp.getPixel(250, 50), 255, 0, 0))
    val exif = ExifInterface(out.absolutePath)
    assertEquals(ExifInterface.ORIENTATION_NORMAL, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1))
    assertEquals("2026:08:14 18:30:00", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
    assertEquals("Prova", exif.getAttribute(ExifInterface.TAG_MAKE))
    assertFalse(exif.getLatLong(FloatArray(2)))
    // Con la posizione.
    val withPlace = File(dir, "luogo.jpg")
    FluidImageExport.export(context, FluidEditorSource.uri(Uri.fromFile(src)), FluidImageEdit.Original, withPlace)
    val place = FloatArray(2)
    assertTrue(ExifInterface(withPlace.absolutePath).getLatLong(place))
    assertEquals(43.77f, place[0], 0.01f)
    // L'originale non e' cambiato di un byte.
    assertArrayEquals(before, src.readBytes())
  }

  @Test fun unaFotoOltreIlTettoEsceRidottaEDiceDiEsserlo() = runBlocking {
    val src = save(quadrants(800, 600), "grande.png")
    val out = File(dir, "out.png")
    val result = FluidImageExport.export(context, FluidEditorSource.uri(Uri.fromFile(src)), FluidImageEdit.Original, out,
      FluidImageExport.Format.Png, maxPixels = 120_000)
    assertTrue(result.reduced)
    assertTrue(result.width * result.height <= 120_000)
    assertEquals(4f / 3f, result.width.toFloat() / result.height, 0.02f)
  }

  @Test fun leStrisceDannoGliStessiPixelDellaLetturaIntera() = runBlocking {
    // Una scala che cambia riga per riga, cosi' una cucitura fra due strisce si vedrebbe.
    val w = 300
    val h = 400
    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    for (y in 0 until h) for (x in 0 until w) b.setPixel(x, y, Color.rgb((x * 255) / w, (y * 255) / h, 128))
    val src = save(b, "scala.png")
    val edit = FluidImageEdit(FluidCropGeometry(angle = 7f).fitted(w, h))
    val whole = File(dir, "intera.png")
    FluidImageExport.export(context, FluidEditorSource.uri(Uri.fromFile(src)), edit, whole, FluidImageExport.Format.Png)
    assertEquals(1, FluidImageExport.lastStrips)
    FluidImageExport.stripPixels = 300 * 40
    val strips = File(dir, "strisce.png")
    FluidImageExport.export(context, FluidEditorSource.uri(Uri.fromFile(src)), edit, strips, FluidImageExport.Format.Png)
    assertTrue("strisce: ${FluidImageExport.lastStrips}", FluidImageExport.lastStrips >= 8)
    val a = decode(whole)
    val c = decode(strips)
    var worst = 0
    for (y in 0 until a.height) for (x in 0 until a.width) {
      val p = a.getPixel(x, y)
      val q = c.getPixel(x, y)
      worst = maxOf(worst, abs(Color.red(p) - Color.red(q)), abs(Color.green(p) - Color.green(q)), abs(Color.blue(p) - Color.blue(q)))
    }
    assertTrue("differenza massima $worst", worst <= 2)
  }

  @Test fun leRegolazioniSalvateSonoQuelleDellaCpu() = runBlocking {
    val src = save(quadrants(200, 100), "q.png")
    val out = File(dir, "out.png")
    val adj = FluidAdjustments.of(FluidAdjustment.Exposure to 40f, FluidAdjustment.Saturation to -100f)
    FluidImageExport.export(context, FluidEditorSource.uri(Uri.fromFile(src)), FluidImageEdit(adjustments = adj), out,
      FluidImageExport.Format.Png)
    val bmp = decode(out)
    // Il rosso puro, +0,6 stop, poi in bianco e nero (Rec. 709).
    val expected = IntArray(1)
    AdjustKernel.process(intArrayOf(Color.RED), 1, 1, AdjustPlan(adj),
      AdjustContext(FluidAffine(1f, 0f, 0f, 1f, 0f, 0f), null, null, 1), expected)
    val p = bmp.getPixel(50, 25)
    assertEquals(Color.red(expected[0]).toDouble(), Color.red(p).toDouble(), 1.0)
    assertEquals(Color.red(p), Color.green(p))
  }

  @Test fun renderDaUnaBitmapInMemoriaColLatoLungoChiesto() = runBlocking {
    val result = FluidImageExport.render(context, FluidEditorSource.bitmap(quadrants(1000, 500)), FluidImageEdit.Original, 400)
    assertEquals(400, result.width)
    assertEquals(200, result.height)
  }

  @Test fun loShaderDiceGliStessiNumeriDellaCpu() {
    val effect = runCatching { RuntimeShader(AdjustShaderSource.text) }
    assertTrue("lo shader non si compila: ${effect.exceptionOrNull()}", effect.isSuccess)
    // Una scala di colori, le stesse regolazioni sulle due strade.
    val w = 64
    val h = 16
    val src = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    for (y in 0 until h) for (x in 0 until w) src.setPixel(x, y, Color.rgb(x * 4, (y * 16) % 256, 255 - x * 4))
    val image = EditorImage(src, w, h)
    val adj = FluidAdjustments.of(
      FluidAdjustment.Exposure to 30f, FluidAdjustment.Contrast to 40f, FluidAdjustment.Shadows to 50f,
      FluidAdjustment.Highlights to -40f, FluidAdjustment.Warmth to 25f, FluidAdjustment.Vibrance to 60f,
      FluidAdjustment.BlackPoint to 20f, FluidAdjustment.Brilliance to 30f,
    )
    val shader = AdjustShader(src, image.definitionBitmap)
    shader.update(adj, image.toOutputFraction(FluidCropGeometry()), 1f)
    // Un RuntimeShader non si disegna su un Canvas di software: un RenderNode, reso dal renderer hardware di Robolectric.
    val node = RenderNode("f8p15-shader").apply {
      setPosition(0, 0, w, h)
      val canvas = beginRecording()
      try { shader.draw(canvas) } finally { endRecording() }
    }
    val render = HardwareRenderer::class.java.getDeclaredMethod("createHardwareBitmap", RenderNode::class.java,
      Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
    val gpu = (render.invoke(null, node, w, h) as Bitmap).copy(Bitmap.Config.ARGB_8888, false)
    val cpu = IntArray(w * h)
    AdjustKernel.process(image.pixels, w, h, AdjustPlan(adj),
      AdjustContext(image.toOutputFraction(FluidCropGeometry()), null, null, 1), cpu)
    var worst = 0
    for (i in 0 until w * h) {
      val p = gpu.getPixel(i % w, i / w)
      val q = cpu[i]
      worst = maxOf(worst, abs(Color.red(p) - Color.red(q)), abs(Color.green(p) - Color.green(q)), abs(Color.blue(p) - Color.blue(q)))
    }
    assertTrue("lo shader e la CPU differiscono di $worst livelli", worst <= 2)
  }
}
