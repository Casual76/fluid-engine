package dev.antigravity.fluidengine.viewer.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.min

/**
 * F8.17 · Il riconoscimento vero (Tesseract 5.5 di bytedeco, le librerie di Windows, lo stesso modello `ita` degli
 * asset) sulle immagini di prova: pagine pulite, una schermata, la foto di una lettera in prospettiva e con poca luce
 * (dalle foto dello scanner). L'errore per carattere (CER) e il tempo finiscono in build/ocr-misure.txt per il rapporto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OcrRecognitionTest {
  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
  private val truth = JSONObject(stream("ocr/righe.json").readBytes().toString(Charsets.UTF_8))

  private fun stream(name: String) = javaClass.classLoader!!.getResourceAsStream(name)!!

  private fun image(name: String): Bitmap = stream("ocr/$name").use { BitmapFactory.decodeStream(it) }!!

  private fun lines(name: String): List<String> {
    val images = truth.getJSONArray("images")
    for (i in 0 until images.length()) {
      val e = images.getJSONObject(i)
      if (e.getString("name") == name) return (0 until e.getJSONArray("lines").length()).map { e.getJSONArray("lines").getJSONObject(it).getString("text") }
    }
    error(name)
  }

  @Test fun leggeLeImmaginiDiProvaInItaliano() {
    val recognizer = FluidTesseractRecognizer(context)
    assertTrue("Tesseract c'è nelle prove", recognizer.available)
    val report = StringBuilder("immagine | CER % | parole vere ritrovate % | parole trovate | ms\n")
    val texts = StringBuilder()
    val failures = ArrayList<String>()
    // Le tolleranze, sulle misure del 25/11/2026 (il rapporto ha i numeri): le pagine pulite quasi senza errori,
    // la schermata con le bolle colorate e la foto in prospettiva con qualche errore in più.
    // La foto della lettera è in prospettiva forte (il foglio a trapezio, le righe storte di angoli diversi): Tesseract
    // non raddrizza la pagina e ne perde una parte. È il limite scritto nel rapporto; qui la soglia tiene solo il livello.
    val cases = listOf("circolare.png" to 0.02, "lettera.png" to 0.02, "ricevuta.png" to 0.03, "schermata.png" to 0.06,
      "foto-lettera.jpg" to 0.5)
    for ((name, limit) in cases + (Scanned to 0.08)) {
      val bitmap = if (name == Scanned) scanned() else image(name)
      val start = System.nanoTime()
      val text = runBlocking { recognizer.recognize(bitmap) }
      val ms = (System.nanoTime() - start) / 1_000_000
      val expected = lines(if (name == "foto-lettera.jpg" || name == Scanned) "lettera.png" else name).joinToString(" ")
      val cer = cer(expected, text.lines.joinToString(" ") { it.text })
      // Le parole vere ritrovate (in qualunque ordine): l'errore per carattere punisce anche le righe rimesse in un
      // ordine diverso, che in una foto storta sono il problema più grande.
      val recall = recall(expected, text.words.map { it.text })
      report.append("$name | ${"%.1f".format(cer * 100)} | ${"%.1f".format(recall * 100)} | ${text.words.size} | $ms\n")
      texts.append("== $name\n").append(text.lines.joinToString("\n") { it.text }).append("\n")
      if (cer > limit) failures += "$name: CER ${cer * 100}% oltre ${limit * 100}%"
      // I riquadri stanno nell'immagine.
      for (w in text.words) assertTrue(w.quad.bounds.left >= 0f && w.quad.bounds.right <= 1.001f)
    }
    File("build").mkdirs()
    File("build/ocr-misure.txt").writeText(report.toString())
    File("build/ocr-testo.txt").writeText(texts.toString())
    assertTrue(failures.joinToString("\n"), failures.isEmpty())
  }

  /** La foto storta della lettera passata dallo scanner: i bordi trovati, la pagina raddrizzata, il filtro «Documento». */
  private fun scanned(): Bitmap {
    val photo = image("foto-lettera.jpg")
    val quad = dev.antigravity.fluidengine.scan.FluidScanEdges.detect(photo)!!.quad
    val straight = dev.antigravity.fluidengine.scan.FluidScanImages.straighten(photo, quad)
    return dev.antigravity.fluidengine.scan.FluidScanImages.filter(straight, dev.antigravity.fluidengine.scan.FluidScanFilter.Document)
  }

  private val Scanned = "foto-lettera-scansionata"

  @Test fun iRiquadriStannoSulleRigheVere() {
    // La circolare pulita: ogni riga vera ha una riga trovata che la copre per più di metà.
    val bitmap = image("circolare.png")
    val text = runBlocking { FluidTesseractRecognizer(context).recognize(bitmap) }
    val images = truth.getJSONArray("images")
    val entry = (0 until images.length()).map(images::getJSONObject).first { it.getString("name") == "circolare.png" }
    val w = entry.getInt("width").toFloat()
    val h = entry.getInt("height").toFloat()
    var covered = 0
    val rows = entry.getJSONArray("lines")
    for (i in 0 until rows.length()) {
      val b = rows.getJSONObject(i).getJSONArray("box")
      val r = Rect(b.getInt(0) / w, b.getInt(1) / h, b.getInt(2) / w, b.getInt(3) / h)
      if (text.lines.any { overlap(it.quad.bounds, r) > 0.5f }) covered++
    }
    assertTrue("righe coperte $covered su ${rows.length()}", covered >= rows.length() - 1)
  }

  @Test fun ilTestoSelezionatoECercato() {
    fun word(t: String, x: Float, y: Float) = FluidRecognizedWord(t, FluidTextQuad.of(Rect(x, y, x + 0.1f, y + 0.04f)), 0.9f)
    val l1 = FluidRecognizedLine(listOf(word("Perché", 0.1f, 0.1f), word("domani", 0.25f, 0.1f)), FluidTextQuad.of(Rect(0.1f, 0.1f, 0.35f, 0.14f)))
    val l2 = FluidRecognizedLine(listOf(word("alle", 0.1f, 0.2f), word("18.30", 0.25f, 0.2f)), FluidTextQuad.of(Rect(0.1f, 0.2f, 0.35f, 0.24f)))
    val text = FluidRecognizedText(listOf(FluidRecognizedBlock(listOf(l1, l2), FluidTextQuad.of(Rect(0.1f, 0.1f, 0.35f, 0.24f)))), 1000, 1000)
    assertEquals("domani\nalle", text.text(1, 2))
    assertEquals(listOf(FluidTextMatch(0, 0)), text.search("perche"))
    assertEquals(listOf(FluidTextMatch(0, 1)), text.search("ché dom"))
    assertEquals(2, wordAt(text, Offset(0.12f, 0.22f), 0.01f))
    assertEquals(null, wordAt(text, Offset(0.9f, 0.9f), 0.01f))
    // Due righe toccate: due rettangoli, come la selezione di iOS.
    assertEquals(2, spanRects(text, 1, 2, androidx.compose.ui.geometry.Size(100f, 100f)).size)
    // Il quadrilatero gira con l'immagine.
    val q = FluidTextQuad.of(Rect(0.1f, 0.2f, 0.3f, 0.25f))
    val back = q.rotated(1).rotated(3)
    assertTrue((back.topLeft - q.topLeft).getDistance() < 1e-5f && (back.bottomRight - q.bottomRight).getDistance() < 1e-5f)
    assertTrue((q.rotated(1).topLeft - Offset(0.8f, 0.1f)).getDistance() < 1e-5f)
  }

  private fun overlap(a: Rect, b: Rect): Float {
    val i = a.intersect(b)
    if (i.width <= 0f || i.height <= 0f) return 0f
    return i.width * i.height / (b.width * b.height)
  }

  companion object {
    /** La parte delle parole vere (senza punteggiatura, minuscole) che si ritrovano fra quelle lette, contate una per una. */
    fun recall(expected: String, words: List<String>): Double {
      fun norm(w: String) = w.lowercase().trim { !it.isLetterOrDigit() }
      val truth = expected.replace(Regex("_{2,}"), " ").split(Regex("\\s+")).map(::norm).filter { it.isNotEmpty() }
      val pool = words.map(::norm).filter { it.isNotEmpty() }.groupingBy { it }.eachCount().toMutableMap()
      var found = 0
      for (w in truth) {
        val n = pool[w] ?: 0
        if (n > 0) { found++; pool[w] = n - 1 }
      }
      return found.toDouble() / truth.size.coerceAtLeast(1)
    }

    /** L'errore per carattere: la distanza di Levenshtein sul testo con gli spazi ridotti, divisa per la lunghezza vera. */
    fun cer(expected: String, actual: String): Double {
      // Le righe di trattini bassi di un modulo («Firma ______») non sono testo: il riconoscimento le scarta di proposito.
      val a = expected.replace(Regex("_{2,}"), " ").replace(Regex("\\s+"), " ").trim()
      val b = actual.replace(Regex("\\s+"), " ").trim()
      var prev = IntArray(b.length + 1) { it }
      var cur = IntArray(b.length + 1)
      for (i in 1..a.length) {
        cur[0] = i
        for (j in 1..b.length) {
          val cost = if (a[i - 1] == b[j - 1]) 0 else 1
          cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
        }
        val t = prev; prev = cur; cur = t
      }
      return prev[b.length].toDouble() / a.length.coerceAtLeast(1)
    }
  }
}
