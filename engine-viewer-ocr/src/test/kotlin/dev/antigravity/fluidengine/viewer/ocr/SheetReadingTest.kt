package dev.antigravity.fluidengine.viewer.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.scan.FluidScanEdges
import dev.antigravity.fluidengine.scan.FluidScanPoint
import dev.antigravity.fluidengine.scan.FluidScanQuad
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * F8.17d · «Scansiona, poi leggi» col Tesseract vero (le librerie di Windows, il modello `ita` degli asset):
 * - la foto storta della lettera si legge dal foglio raddrizzato (dal 41% di caratteri sbagliati a pochi punti) e le
 *   righe tornano sulla foto **dove stanno le righe vere**, storte come il foglio;
 * - le immagini senza un foglio storto (la schermata, le pagine dritte, un foglio fotografato di fronte) si leggono
 *   intere, come prima;
 * - una parola si tocca sul suo quadrilatero storto, non sul rettangolo che la contiene.
 * Le misure finiscono in build/ocr-foglio.txt per il rapporto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SheetReadingTest {
  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
  private val truth = JSONObject(stream("ocr/righe.json").readBytes().toString(Charsets.UTF_8))

  private fun stream(name: String) = javaClass.classLoader!!.getResourceAsStream(name)!!

  private fun image(name: String): Bitmap = stream("ocr/$name").use { BitmapFactory.decodeStream(it) }!!

  private fun entry(name: String): JSONObject {
    val images = truth.getJSONArray("images")
    return (0 until images.length()).map(images::getJSONObject).first { it.getString("name") == name }
  }

  /**
   * Il foglio vero della foto: `foto-lettera.jpg` è la foto «prospettiva» dello scanner (tools/scan-samples/genera.py),
   * con la pagina `lettera.png` posata sul tavolo a questi angoli (engine-scan-core, test/resources/scan/fogli.json).
   */
  private val sheet = FluidScanQuad(FluidScanPoint(0.25f, 0.20625f), FluidScanPoint(0.75833f, 0.1875f),
    FluidScanPoint(0.925f, 0.89375f), FluidScanPoint(0.07917f, 0.92188f))

  @Test fun laFotoStortaSiLeggeDalFoglio() {
    val recognizer = FluidTesseractRecognizer(context)
    assertTrue("Tesseract c'è nelle prove", recognizer.available)
    val photo = image("foto-lettera.jpg")
    val letter = entry("lettera.png")
    val rows = letter.getJSONArray("lines")
    val expected = (0 until rows.length()).joinToString(" ") { rows.getJSONObject(it).getString("text") }
    val direct = runBlocking { recognizer.recognize(photo) }
    val start = System.nanoTime()
    val text = runBlocking { SheetReading.recognize(recognizer, photo) }
    val ms = (System.nanoTime() - start) / 1_000_000
    val cerDirect = OcrRecognitionTest.cer(expected, direct.lines.joinToString(" ") { it.text })
    val cer = OcrRecognitionTest.cer(expected, text.lines.joinToString(" ") { it.text })
    val recall = OcrRecognitionTest.recall(expected, text.words.map { it.text })
    assertEquals(photo.width, text.width)
    assertEquals(photo.height, text.height)

    // Ogni riga vera, portata sulla foto con la prospettiva vera, ha una riga letta con (quasi) lo stesso testo e il
    // centro entro l'1% della diagonale della foto.
    val map = sheet.pageMap()!!
    val pw = letter.getInt("width").toFloat()
    val ph = letter.getInt("height").toFloat()
    var placed = 0
    val misplaced = ArrayList<String>()
    for (i in 0 until rows.length()) {
      val row = rows.getJSONObject(i)
      val b = row.getJSONArray("box")
      val where = map.photoPoint((b.getInt(0) + b.getInt(2)) / 2f / pw, (b.getInt(1) + b.getInt(3)) / 2f / ph)
      val wanted = row.getString("text").replace(Regex("_{2,}"), " ").trim()
      val read = text.lines.minByOrNull { OcrRecognitionTest.cer(wanted, it.text) } ?: continue
      if (OcrRecognitionTest.cer(wanted, read.text) > 0.25) { misplaced += "«$wanted» non letta"; continue }
      val c = read.quad.center()
      val off = hypot((c.x - where.x) * photo.width, (c.y - where.y) * photo.height) / hypot(photo.width.toFloat(), photo.height.toFloat())
      if (off <= 0.01f) placed++ else misplaced += "«$wanted» spostata di ${"%.2f".format(off * 100)}% della diagonale"
    }
    val upright = text.lines.count { it.quad.isUpright }
    val report = "foto della lettera storta: CER ${"%.1f".format(cerDirect * 100)}% letta intera, " +
      "${"%.1f".format(cer * 100)}% dal foglio; parole ritrovate ${"%.1f".format(recall * 100)}%; $ms ms (JVM, PC); " +
      "righe al loro posto $placed su ${rows.length()}; righe dritte $upright su ${text.lines.size}\n" +
      misplaced.joinToString("") { "  $it\n" }
    println(report)
    File("build").mkdirs()
    File("build/ocr-foglio.txt").writeText(report + "\n== testo letto dal foglio\n" + text.lines.joinToString("\n") { it.text } + "\n")

    // Le soglie, sulle misure del 26/11/2026 (2,4% sul tablet con la pagina passata dallo scanner).
    assertTrue("dal foglio si legge meglio ($cer contro $cerDirect)", cer < cerDirect / 4)
    assertTrue("CER ${cer * 100}%", cer < 0.05)
    assertTrue("parole ritrovate ${recall * 100}%", recall > 0.95)
    assertTrue("righe al loro posto $placed su ${rows.length()}: $misplaced", placed >= rows.length() - 1)
    // Le righe sono storte come il foglio: la selezione e il velo le seguono.
    assertTrue("righe dritte $upright su ${text.lines.size}", upright <= 1)
  }

  @Test fun leImmaginiSenzaUnFoglioStortoSiLeggonoIntere() {
    for (name in listOf("schermata.png", "circolare.png", "lettera.png", "ricevuta.png")) {
      val img = image(name)
      assertNull(name, SheetReading.sheetToStraighten(FluidScanEdges.detect(img), img.width, img.height))
    }
    // Un foglio fotografato di fronte (bianco sul tavolo di legno, coi lati sugli assi): si trova, ma non si raddrizza.
    // La grana della foto (le venature del legno e il rumore del sensore) serve ai bordi come in una foto vera.
    val photo = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888)
    Canvas(photo).apply {
      drawColor(0xFF3A2A1E.toInt())
      val grain = Paint()
      for (y in 0 until 1600 step 6) {
        grain.color = if ((y / 6) % 2 == 0) 0xFF46331F.toInt() else 0xFF33251A.toInt()
        drawRect(0f, y.toFloat(), 1200f, y + 6f, grain)
      }
      drawRect(200f, 240f, 1000f, 1360f, Paint().apply { color = 0xFFF4F2EC.toInt() })
      val ink = Paint().apply { color = 0xFF202020.toInt() }
      for (row in 0 until 12) drawRect(280f, 340f + row * 60f, 880f - (row % 3) * 90f, 356f + row * 60f, ink)
    }
    val noise = java.util.Random(17)
    val px = IntArray(photo.width * photo.height).also { photo.getPixels(it, 0, photo.width, 0, 0, photo.width, photo.height) }
    for (i in px.indices) {
      val d = noise.nextInt(17) - 8
      val c = px[i]
      fun ch(s: Int) = ((c shr s and 0xFF) + d).coerceIn(0, 255)
      px[i] = (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
    photo.setPixels(px, 0, photo.width, 0, 0, photo.width, photo.height)
    val found = FluidScanEdges.detect(photo)
    assertNotNull("il foglio dritto si trova", found)
    assertNull("ma non si raddrizza", SheetReading.sheetToStraighten(found, photo.width, photo.height))
    // Lo stesso foglio girato di 10°: si raddrizza.
    val turned = Bitmap.createBitmap(photo, 0, 0, photo.width, photo.height, android.graphics.Matrix().apply { postRotate(10f, 600f, 800f) }, true)
    val tilt = FluidScanEdges.detect(turned)
    assertNotNull("il foglio girato si trova", tilt)
    assertNotNull("e si raddrizza", SheetReading.sheetToStraighten(tilt, turned.width, turned.height))
  }

  @Test fun spentoSiLeggeLaFotoIntera() {
    val photo = image("foto-lettera.jpg")
    FluidLiveText.clearCache()
    FluidLiveText.straightenSheets = false
    try {
      val text = runBlocking { FluidLiveText.read(context, "spento", findSheet = true) { photo.copy(Bitmap.Config.ARGB_8888, false) } }!!
      // Lo stesso testo della foto letta intera, senza passare dal foglio.
      val direct = runBlocking { FluidTesseractRecognizer(context).recognize(photo) }
      assertEquals(direct.lines.map { it.text }, text.lines.map { it.text })
    } finally {
      FluidLiveText.straightenSheets = true
      FluidLiveText.clearCache()
    }
  }

  @Test fun unaParolaStortaSiToccaSulSuoQuadrilatero() {
    // Due righe girate di 20°, vicine: il rettangolo dritto di una parola della prima copre anche la seconda riga.
    val angle = Math.toRadians(20.0)
    val ux = Offset(cos(angle).toFloat(), sin(angle).toFloat())
    val uy = Offset(-sin(angle).toFloat(), cos(angle).toFloat())
    fun quad(x: Float, y: Float, w: Float, h: Float): FluidTextQuad {
      val o = Offset(0.2f, 0.2f) + ux * x + uy * y
      return FluidTextQuad(o, o + ux * w, o + ux * w + uy * h, o + uy * h)
    }
    fun line(y: Float, words: List<String>): FluidRecognizedLine {
      var x = 0f
      val ws = words.map { t -> FluidRecognizedWord(t, quad(x, y, 0.12f, 0.03f), 0.9f).also { x += 0.14f } }
      return FluidRecognizedLine(ws, quad(0f, y, x - 0.02f, 0.03f))
    }
    val text = FluidRecognizedText(listOf(FluidRecognizedBlock(listOf(line(0f, listOf("Gentile", "signora", "Ferri")),
      line(0.035f, listOf("le", "confermiamo", "martedì"))), quad(0f, 0f, 0.4f, 0.065f))), 1000, 1000)
    val second = text.words[4].quad
    val tap = (second.topLeft + second.bottomRight) / 2f
    assertTrue("il rettangolo della prima riga copre il tocco", text.words[1].quad.bounds.contains(tap))
    assertEquals("si prende la parola della seconda riga", 4, wordAt(text, tap, 0.005f))
    assertEquals(0f, second.distanceTo(tap), 0f)
    assertTrue(!text.words[1].quad.contains(tap))
    // La selezione di una riga storta è storta come la riga, alta quanto la riga.
    val span = spanQuads(text, 3, 4).single()
    val lineQuad = text.lines[1].quad
    assertTrue(!span.isUpright)
    assertTrue((span.topLeft - lineQuad.topLeft).getDistance() < 1e-4f)
    assertTrue((span.bottomRight - text.words[4].quad.bottomRight).getDistance() < 1e-3f)
  }

  private fun FluidTextQuad.center() = (topLeft + topRight + bottomRight + bottomLeft) / 4f
}
