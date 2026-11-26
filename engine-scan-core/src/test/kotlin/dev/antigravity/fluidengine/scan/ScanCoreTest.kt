package dev.antigravity.fluidengine.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.hypot

/**
 * F8.17 · Il nucleo dello scanner sulle foto di prova (tools/scan-samples/genera.py): i bordi trovati contro gli angoli
 * veri, con luce buona e cattiva, prospettiva, rotazione, ombra, basso contrasto e scontrino; nessun foglio sul tavolo
 * vuoto; le proporzioni vere, il raddrizzamento e i filtri. Le misure finiscono in build/scan-misure.txt per il
 * rapporto. Da F8.17d stanno in engine-scan-core, col codice che provano.
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

  /** Il foglio della foto «prospettiva» (fogli.json): la lettera vista di sbieco. */
  private val tilted = FluidScanQuad(FluidScanPoint(0.25f, 0.20625f), FluidScanPoint(0.75833f, 0.1875f),
    FluidScanPoint(0.925f, 0.89375f), FluidScanPoint(0.07917f, 0.92188f))

  @Test fun laProspettivaDellaPaginaTornaSullaFoto() {
    val map = tilted.pageMap()!!
    val corners = listOf(Triple(0f, 0f, tilted.topLeft), Triple(1f, 0f, tilted.topRight), Triple(1f, 1f, tilted.bottomRight),
      Triple(0f, 1f, tilted.bottomLeft))
    for ((u, v, p) in corners) {
      val m = map.photoPoint(u, v)
      assertEquals("x di ($u, $v)", p.x, m.x, 1e-4f)
      assertEquals("y di ($u, $v)", p.y, m.y, 1e-4f)
    }
    // Il centro della pagina è l'incrocio delle diagonali del foglio, non la media degli angoli: è la prospettiva.
    val a = tilted.topLeft; val b = tilted.bottomRight; val c = tilted.topRight; val d = tilted.bottomLeft
    val den = (a.x - b.x) * (c.y - d.y) - (a.y - b.y) * (c.x - d.x)
    val t = ((a.x - c.x) * (c.y - d.y) - (a.y - c.y) * (c.x - d.x)) / den
    val cross = FluidScanPoint(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y))
    val center = map.photoPoint(0.5f, 0.5f)
    assertEquals(cross.x, center.x, 1e-4f)
    assertEquals(cross.y, center.y, 1e-4f)
    assertTrue("non la media degli angoli", hypot(center.x - tilted.center.x, center.y - tilted.center.y) > 0.005f)
    // Rientrare è rientrare sulla pagina: l'angolo nuovo è il punto (e, e) della pagina.
    val inner = tilted.inset(0.005f)
    assertEquals(map.photoPoint(0.005f, 0.005f), inner.topLeft)
    assertEquals(map.photoPoint(0.995f, 0.995f), inner.bottomRight)
    val full = FluidScanQuad.Full.inset(0.01f)
    assertEquals(0.01f, full.topLeft.x, 1e-5f)
    assertEquals(0.99f, full.bottomRight.y, 1e-5f)
    assertEquals(tilted, tilted.inset(0f))
  }

  /**
   * F8.17d · Il filo di tavolo sul bordo della pagina (visto sul Tab S9): coi bordi trovati dalla foto e il filtro
   * «Documento», la striscia più scura fra i quattro bordi della pagina (i 3 pixel più esterni) deve essere carta.
   */
  @Test fun ilBordoDellaPaginaNonHaIlFiloDelTavolo() {
    val report = StringBuilder()
    val failures = ArrayList<String>()
    var darkBefore = 0
    for (name in listOf("buona-luce", "prospettiva", "ruotato", "poca-luce", "ombra", "basso-contrasto")) {
      val photo = photo(name)
      val quad = FluidScanEdges.detect(photo)!!.quad
      fun darkestBorder(inset: Float): Float {
        val page = FluidScanImages.filter(FluidScanImages.straighten(photo, quad, maxLongSide = 1200, edgeInset = inset),
          FluidScanFilter.Document)
        val w = page.width
        val h = page.height
        val pixels = IntArray(w * h).also { page.getPixels(it, 0, w, 0, 0, w, h) }
        fun strip(xs: IntRange, ys: IntRange): Float {
          var sum = 0.0
          var n = 0
          for (y in ys) for (x in xs) { sum += ScanFilters.luma(pixels[y * w + x]); n++ }
          return (sum / n).toFloat()
        }
        val band = 3
        return minOf(strip(0 until w, 0 until band), strip(0 until w, h - band until h),
          strip(0 until band, 0 until h), strip(w - band until w, 0 until h))
      }
      val before = darkestBorder(0f)
      val after = darkestBorder(FluidScanImages.EdgeInset)
      report.append("$name: bordo più scuro ${"%.0f".format(before)} senza rientro, ${"%.0f".format(after)} col rientro\n")
      if (after < 225f) failures += "$name: il bordo della pagina è scuro ($after)"
      if (before < 150f) darkBefore++
    }
    println(report)
    File("build").mkdirs()
    File("build/scan-bordo.txt").writeText(report.toString())
    // La misura vede il difetto: senza rientro il filo scuro c'è (il 26/11/2026 su 5 foto su 6, fino a 1 su 255).
    assertTrue("senza rientro il filo si vede su $darkBefore foto", darkBefore >= 3)
    assertTrue(failures.joinToString("\n") + "\n" + report, failures.isEmpty())
  }
}
