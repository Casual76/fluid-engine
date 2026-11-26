package dev.antigravity.fluidengine.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot

/**
 * F8.17 · Il nucleo dello scanner sulle foto di prova (tools/scan-samples/genera.py): i bordi trovati contro gli angoli
 * veri, con luce buona e cattiva, prospettiva, rotazione, ombra, basso contrasto e scontrino; nessun foglio sul tavolo
 * vuoto; le proporzioni vere, i filtri e il PDF. Le misure finiscono in build/scan-misure.txt per il rapporto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScanCoreTest {
  private val meta = JSONObject(resource("scan/fogli.json").readBytes().toString(Charsets.UTF_8))

  private fun resource(name: String) = javaClass.classLoader!!.getResourceAsStream(name)!!

  private fun photo(name: String): Bitmap = resource("scan/$name.jpg").use { BitmapFactory.decodeStream(it) }!!

  /** L'errore peggiore fra gli angoli trovati e quelli veri, in frazioni della diagonale. */
  private fun error(found: FluidScanQuad, truth: FluidScanQuad, aspect: Float): Float = found.distanceTo(truth, aspect)

  private fun truth(entry: JSONObject): FluidScanQuad {
    val q = entry.getJSONArray("quad")
    val pts = (0 until 4).map { FluidScanPoint(q.getJSONArray(it).getDouble(0).toFloat(), q.getJSONArray(it).getDouble(1).toFloat()) }
    return FluidScanQuad(pts[0], pts[1], pts[2], pts[3])
  }

  @Test fun iBordiSulleFotoDiProva() {
    val report = StringBuilder("foto | errore massimo (% diagonale) | fiducia | ms\n")
    // La tolleranza: 2% della diagonale con la luce buona, 3,5% con quella cattiva (a occhio: un angolo spostato del
    // 2% su una foto 1200 × 1600 è 40 pixel, mezza riga di testo; il ritaglio a mano lo corregge).
    val limits = mapOf("buona-luce" to 0.02f, "prospettiva" to 0.02f, "ruotato" to 0.02f, "ombra" to 0.025f,
      "scontrino" to 0.02f, "poca-luce" to 0.035f, "basso-contrasto" to 0.035f)
    val photos = meta.getJSONArray("photos")
    val failures = ArrayList<String>()
    for (i in 0 until photos.length()) {
      val entry = photos.getJSONObject(i)
      val name = entry.getString("name")
      val bitmap = photo(name)
      val start = System.nanoTime()
      val found = FluidScanEdges.detect(bitmap)
      val ms = (System.nanoTime() - start) / 1_000_000
      val aspect = bitmap.width.toFloat() / bitmap.height
      if (entry.isNull("quad")) {
        report.append("$name | nessun foglio (${if (found == null) "giusto" else "SBAGLIATO, fiducia ${found.confidence}"}) | - | $ms\n")
        if (found != null) failures += "$name: sul tavolo vuoto c'è un foglio ($found)"
        continue
      }
      if (found == null) {
        report.append("$name | NON TROVATO | - | $ms\n")
        failures += "$name: il foglio non si trova"
        continue
      }
      val err = error(found.quad, truth(entry), aspect)
      report.append("$name | ${"%.2f".format(err * 100)} | ${"%.2f".format(found.confidence)} | $ms\n")
      if (err > limits.getValue(name)) failures += "$name: errore ${err * 100}% oltre ${limits[name]!! * 100}% ($found)"
    }
    File("build").mkdirs()
    File("build/scan-misure.txt").writeText(report.toString())
    assertTrue(failures.joinToString("\n"), failures.isEmpty())
  }

  @Test fun ilFotogrammaGiratoDaLoStessoFoglio() {
    // Il sensore dà il fotogramma girato di 90°: gli angoli devono tornare quelli della foto dritta.
    val bitmap = photo("buona-luce")
    val upright = FluidScanEdges.detect(bitmap)!!.quad
    val sensor = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, android.graphics.Matrix().apply { postRotate(-90f) }, true)
    val frame = FluidScanFrame.of(sensor).let { FluidScanFrame(it.width, it.height, it.luma, it.stride, rotationDegrees = 90) }
    val turned = FluidScanEdges.create().detect(frame)!!
    assertEquals(bitmap.width.toFloat() / bitmap.height, turned.aspect, 0.01f)
    assertTrue("stesso foglio", upright.distanceTo(turned.quad, turned.aspect) < 0.01f)
  }

  @Test fun leProporzioniVereDiUnA4InProspettiva() {
    val entry = (0 until meta.getJSONArray("photos").length()).map { meta.getJSONArray("photos").getJSONObject(it) }
      .first { it.getString("name") == "prospettiva" }
    // Il foglio di prova è un A4 (1240 × 1754): 0,707. I lati visti, col trapezio, direbbero altro.
    val aspect = truth(entry).estimatedAspect(1200, 1600)
    assertEquals(1240f / 1754f, aspect, 0.06f)
  }

  @Test fun gliAngoliSiOrdinanoComeIlFoglio() {
    val q = FluidScanQuad.ordered(listOf(FluidScanPoint(0.9f, 0.8f), FluidScanPoint(0.1f, 0.2f), FluidScanPoint(0.85f, 0.15f), FluidScanPoint(0.12f, 0.9f)))
    assertEquals(FluidScanPoint(0.1f, 0.2f), q.topLeft)
    assertEquals(FluidScanPoint(0.85f, 0.15f), q.topRight)
    assertEquals(FluidScanPoint(0.9f, 0.8f), q.bottomRight)
    assertEquals(FluidScanPoint(0.12f, 0.9f), q.bottomLeft)
    assertTrue(q.isConvex())
    // Un quarto di giro sul foglio: l'angolo in alto a sinistra diventa quello che era in basso a sinistra.
    assertEquals(q.bottomLeft, q.turnedClockwise().topLeft)
    // Girare l'immagine di 360° non cambia niente.
    assertTrue(q.distanceTo(q.rotated(90).rotated(270)) < 1e-5f)
  }

  @Test fun ilRaddrizzamentoDaUnaPaginaDritta() {
    val bitmap = photo("prospettiva")
    val entry = (0 until meta.getJSONArray("photos").length()).map { meta.getJSONArray("photos").getJSONObject(it) }
      .first { it.getString("name") == "prospettiva" }
    val page = FluidScanImages.straighten(bitmap, truth(entry), maxLongSide = 1000)
    assertEquals(1000, page.height)
    assertEquals(1240f / 1754f, page.width.toFloat() / page.height, 0.05f)
    // Gli angoli della pagina sono carta, non tavolo: chiari.
    for ((x, y) in listOf(4 to 4, page.width - 5 to 4, 4 to page.height - 5, page.width - 5 to page.height - 5)) {
      val c = page.getPixel(x, y)
      assertTrue("angolo ($x, $y) chiaro", ScanFilters.luma(c) > 170)
    }
  }

  @Test fun ilFiltroDocumentoTogleLOmbra() {
    // Una pagina grigia con un'ombra a gradiente e una riga d'inchiostro: dopo il filtro la carta è bianca ovunque.
    val w = 400
    val h = 560
    val pixels = IntArray(w * h) { i ->
      val x = i % w
      val shade = (230 - 110 * x / w)
      val ink = (i / w) in 270..285 && x in 40..360
      val v = if (ink) shade / 5 else shade
      (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    ScanFilters.apply(pixels, w, h, FluidScanFilter.Document)
    val paperLeft = ScanFilters.luma(pixels[100 * w + 20])
    val paperRight = ScanFilters.luma(pixels[100 * w + 380])
    val ink = ScanFilters.luma(pixels[278 * w + 200])
    assertTrue("carta a sinistra bianca ($paperLeft)", paperLeft >= 245)
    assertTrue("carta all'ombra bianca ($paperRight)", paperRight >= 245)
    assertTrue("inchiostro scuro ($ink)", ink <= 70)
    val bw = IntArray(w * h) { i -> val x = i % w; val v = 230 - 110 * x / w; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
    for (y in 270..285) for (x in 40..360) bw[y * w + x] = 0xFF101010.toInt()
    ScanFilters.apply(bw, w, h, FluidScanFilter.BlackAndWhite)
    assertEquals(0xFFFFFFFF.toInt(), bw[100 * w + 380])
    assertEquals(0xFF000000.toInt(), bw[278 * w + 200])
  }

  @Test fun ilPdfHaLePagineEGliIndirizziGiusti() {
    val jpeg = { w: Int, h: Int ->
      val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
      ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
    }
    val out = ByteArrayOutputStream()
    ScanPdfWriter.write(listOf(ScanPdfWriter.Page(jpeg(1240, 1754), 1240, 1754), ScanPdfWriter.Page(jpeg(600, 1600), 600, 1600)),
      "Scansione 25 nov 2026, 14.32", out)
    val bytes = out.toByteArray()
    val text = String(bytes, Charsets.ISO_8859_1)
    assertTrue(text.startsWith("%PDF-1.4"))
    assertTrue(text.contains("/Count 2"))
    assertTrue("A4 riconosciuto", text.contains("/MediaBox [0 0 595.28 841.89]"))
    // La tabella xref: ogni scostamento punta davvero al suo oggetto.
    val xref = text.substring(text.lastIndexOf("startxref") + 10).trim().lines().first().toInt()
    val rows = text.substring(xref).lines().drop(3).takeWhile { it.endsWith(" n ") }
    assertEquals(9, rows.size)
    rows.forEachIndexed { i, row ->
      val offset = row.substring(0, 10).toInt()
      assertTrue("oggetto ${i + 1}", text.startsWith("${i + 1} 0 obj", offset))
    }
    assertEquals(612f to 792f, ScanPdfWriter.pageSize(850, 1100))
    assertEquals(841.89f to 595.28f, ScanPdfWriter.pageSize(1754, 1240))
  }

  @Test fun loScattoDaSoloUnaVoltaPerFoglio() {
    val tracker = FluidScanTracker()
    val quad = FluidScanQuad(FluidScanPoint(0.2f, 0.2f), FluidScanPoint(0.8f, 0.2f), FluidScanPoint(0.8f, 0.8f), FluidScanPoint(0.2f, 0.8f))
    val det = FluidScanDetection(quad, 0.9f, 0.75f)
    var captures = 0
    var t = 0L
    repeat(40) {
      val track = tracker.update(det, t)
      if (track.shouldCapture) captures++
      t += 66
    }
    assertEquals("una volta sola sullo stesso foglio", 1, captures)
    // Il foglio sparisce e torna: si può scattare di nuovo.
    repeat(8) { tracker.update(null, t); t += 66 }
    repeat(20) { if (tracker.update(det, t).shouldCapture) captures++; t += 66 }
    assertEquals(2, captures)
    // In manuale non scatta mai da solo.
    val manual = FluidScanTracker()
    var t2 = 0L
    repeat(40) { assertTrue(!manual.update(det, t2, auto = false).shouldCapture); t2 += 66 }
    // Un foglio piccolo chiede di avvicinarsi.
    val small = FluidScanQuad(FluidScanPoint(0.45f, 0.45f), FluidScanPoint(0.55f, 0.45f), FluidScanPoint(0.55f, 0.55f), FluidScanPoint(0.45f, 0.55f))
    assertEquals(FluidScanHint.Closer, FluidScanTracker().update(FluidScanDetection(small, 0.9f, 1f), 0).hint)
  }

  @Test fun laManoCheTremaNonScatta() {
    val tracker = FluidScanTracker()
    val base = FluidScanQuad(FluidScanPoint(0.2f, 0.2f), FluidScanPoint(0.8f, 0.2f), FluidScanPoint(0.8f, 0.8f), FluidScanPoint(0.2f, 0.8f))
    var t = 0L
    repeat(30) { i ->
      val d = if (i % 2 == 0) 0.03f else -0.03f
      val shaken = base.shifted(FluidScanPoint(d, 0f))
      assertTrue(!tracker.update(FluidScanDetection(shaken, 0.9f, 1f), t).shouldCapture)
      t += 66
    }
    assertTrue(abs(hypot(1f, 1f)) > 0f)
  }
}
