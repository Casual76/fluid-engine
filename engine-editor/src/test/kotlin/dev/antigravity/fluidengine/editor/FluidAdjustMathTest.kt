package dev.antigravity.fluidengine.editor

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F8.15 · I numeri delle regolazioni su immagini di prova, sulla JVM (nessuna classe di Android). I pixel attesi si
 * calcolano qui con le formule scritte a mano (sRGB, Rec. 709, la moltiplicazione di `CIExposureAdjust`), non con il
 * codice del modulo: se una costante cambia per sbaglio, la prova lo dice.
 */
class FluidAdjustMathTest {

  private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
  private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
  private fun r(c: Int) = (c shr 16) and 0xFF
  private fun g(c: Int) = (c shr 8) and 0xFF
  private fun b(c: Int) = c and 0xFF

  private fun srgbToLinear(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
  private fun linearToSrgb(l: Double) = if (l <= 0.0031308) l * 12.92 else 1.055 * l.pow(1 / 2.4) - 0.055

  /** Un'immagine piatta di [w] × [h] col colore [c], passata nel calcolo con [adjustments]. */
  private fun run(adjustments: FluidAdjustments, pixels: IntArray, w: Int, h: Int, map: DefinitionMap? = null): IntArray {
    val out = IntArray(pixels.size)
    val ctx = AdjustContext(
      toOutput = FluidAffine(1f / w, 0f, 0f, 1f / h, 0f, 0f),
      toMap = map?.let { FluidAffine(it.width.toFloat() / w, 0f, 0f, it.height.toFloat() / h, 0f, 0f) },
      map = map,
      sharpRadius = 1,
    )
    AdjustKernel.process(pixels, w, h, AdjustPlan(adjustments), ctx, out)
    return out
  }

  private fun one(adjustments: FluidAdjustments, c: Int): Int = run(adjustments, IntArray(9) { c }, 3, 3)[4]

  @Test fun ilNeutroLasciaOgniPixelAlBit() {
    val pixels = IntArray(256 * 3) { i -> rgb(i % 256, (i * 7) % 256, (i * 13) % 256) }
    assertTrue(pixels.contentEquals(run(FluidAdjustments.Neutral, pixels, 256, 3)))
  }

  @Test fun anchePiuRegolazioniAZeroDopoUnCambioSonoNeutre() {
    val a = FluidAdjustments.Neutral.with(FluidAdjustment.Exposure, 30f).with(FluidAdjustment.Exposure, 0f)
    assertTrue(a.isNeutral)
    assertEquals(FluidAdjustments.Neutral, a)
  }

  @Test fun lEsposizioneMoltiplicaLaLuceLineare() {
    // +100 = +1,5 stop: il grigio 128 va a 0,2158 × 2^1,5 = 0,6105 lineare = 0,803 in sRGB = 205.
    val expected = (linearToSrgb(srgbToLinear(128 / 255.0) * 2.0.pow(1.5)) * 255).roundToInt()
    assertEquals(205, expected)
    val up = one(FluidAdjustments.of(FluidAdjustment.Exposure to 100f), gray(128))
    assertEquals(expected, r(up))
    assertEquals(expected, g(up))
    assertEquals(expected, b(up))
    // −50 = −0,75 stop.
    val down = one(FluidAdjustments.of(FluidAdjustment.Exposure to -50f), gray(128))
    assertEquals((linearToSrgb(srgbToLinear(128 / 255.0) * 2.0.pow(-0.75)) * 255).roundToInt(), r(down))
    // Il bianco resta bianco, il nero resta nero.
    assertEquals(255, r(one(FluidAdjustments.of(FluidAdjustment.Exposure to 100f), gray(255))))
    assertEquals(0, r(one(FluidAdjustments.of(FluidAdjustment.Exposure to -100f), gray(0))))
  }

  @Test fun laSaturazioneAMenoCentoEIlBiancoENeroRec709() {
    val c = rgb(200, 60, 30)
    val out = one(FluidAdjustments.of(FluidAdjustment.Saturation to -100f), c)
    val l = (0.2126 * 200 + 0.7152 * 60 + 0.0722 * 30).roundToInt()
    assertEquals(l, r(out))
    assertEquals(l, g(out))
    assertEquals(l, b(out))
    // A +100 i canali si allontanano dalla luminanza del doppio (e si tagliano a 0 e 255).
    val more = one(FluidAdjustments.of(FluidAdjustment.Saturation to 50f), rgb(150, 100, 100))
    val l2 = 0.2126 * 150 + 0.7152 * 100 + 0.0722 * 100
    assertEquals((l2 + (150 - l2) * 1.5).roundToInt(), r(more))
    assertEquals((l2 + (100 - l2) * 1.5).roundToInt(), g(more))
  }

  @Test fun laVividezzaSpingeIColoriSpentiPiuDiQuelliVivi() {
    val dull = rgb(140, 120, 110)
    val vivid = rgb(230, 40, 30)
    val a = FluidAdjustments.of(FluidAdjustment.Vibrance to 100f)
    val dullOut = one(a, dull)
    val vividOut = one(a, vivid)
    val dullGain = (r(dullOut) - b(dullOut)).toFloat() / (140 - 110)
    val vividGain = (r(vividOut) - g(vividOut)).toFloat() / (230 - 40)
    assertTrue("spento $dullGain, vivo $vividGain", dullGain > vividGain + 0.3f)
  }

  @Test fun ilCaloreScaldaIlGrigioATonalitaUguale() {
    val warm = one(FluidAdjustments.of(FluidAdjustment.Warmth to 60f), gray(128))
    assertTrue(r(warm) > g(warm) && g(warm) > b(warm))
    val cold = one(FluidAdjustments.of(FluidAdjustment.Warmth to -60f), gray(128))
    assertTrue(b(cold) > g(cold) && g(cold) > r(cold))
    // La luminanza resta quella di prima, entro due livelli.
    val l = 0.2126 * srgbToLinear(r(warm) / 255.0) + 0.7152 * srgbToLinear(g(warm) / 255.0) + 0.0722 * srgbToLinear(b(warm) / 255.0)
    assertEquals(128.0, linearToSrgb(l) * 255, 2.0)
    // La tinta positiva va verso il magenta: meno verde.
    val magenta = one(FluidAdjustments.of(FluidAdjustment.Tint to 80f), gray(128))
    assertTrue(g(magenta) < r(magenta) && g(magenta) < b(magenta))
  }

  @Test fun ilContrastoTieneFermoIlGrigioMedioEAllargaIlResto() {
    val a = FluidAdjustments.of(FluidAdjustment.Contrast to 100f)
    assertEquals(128, r(one(a, gray(128))), 1)
    // smoothstep: 0,25 -> 0,15625; 0,75 -> 0,84375.
    assertEquals((0.15625 * 255).roundToInt(), r(one(a, gray((0.25 * 255).roundToInt()))), 1)
    assertEquals((0.84375 * 255).roundToInt(), r(one(a, gray((0.75 * 255).roundToInt()))), 1)
    // −100: tutto al 60% attorno a 0,5: il nero va a 0,2, il bianco a 0,8.
    val flat = FluidAdjustments.of(FluidAdjustment.Contrast to -100f)
    assertEquals(51, r(one(flat, gray(0))))
    assertEquals(204, r(one(flat, gray(255))))
  }

  private fun assertEquals(expected: Int, actual: Int, tolerance: Int) =
    assertTrue("atteso $expected ± $tolerance, ottenuto $actual", abs(expected - actual) <= tolerance)

  @Test fun puntoDiNeroLuciOmbreELuminositaSuUnaScala() {
    val ramp = IntArray(256) { gray(it) }
    fun curve(a: FluidAdjustments) = run(a, ramp, 256, 1).map { r(it) }
    // Ogni curva sale sempre (nessuna inversione dei toni) e tiene fermi gli estremi dove deve.
    for (a in listOf(
      FluidAdjustment.Shadows to 100f, FluidAdjustment.Shadows to -100f, FluidAdjustment.Highlights to 100f,
      FluidAdjustment.Highlights to -100f, FluidAdjustment.Brightness to 100f, FluidAdjustment.Brightness to -100f,
      FluidAdjustment.Brilliance to 100f, FluidAdjustment.Brilliance to -100f, FluidAdjustment.BlackPoint to 100f,
      FluidAdjustment.BlackPoint to -100f, FluidAdjustment.Contrast to 100f, FluidAdjustment.Contrast to -100f,
    )) {
      val c = curve(FluidAdjustments.of(a))
      for (i in 1 until 256) assertTrue("${a.first} ${a.second}: scende a $i", c[i] >= c[i - 1])
    }
    // Le ombre muovono solo la meta' bassa, le luci solo quella alta.
    val sh = curve(FluidAdjustments.of(FluidAdjustment.Shadows to 100f))
    assertTrue(sh[42] > 42 + 20)
    assertEquals(200, sh[200])
    val hi = curve(FluidAdjustments.of(FluidAdjustment.Highlights to -100f))
    assertTrue(hi[213] < 213 - 20)
    assertEquals(60, hi[60])
    assertEquals(255, hi[255])
    // Il punto di nero +100: il 12% piu' scuro diventa nero; −100: il nero sale al 10%.
    val bp = curve(FluidAdjustments.of(FluidAdjustment.BlackPoint to 100f))
    assertEquals(0, bp[30])
    assertEquals(255, bp[255])
    assertEquals((0.10 * 255).roundToInt(), curve(FluidAdjustments.of(FluidAdjustment.BlackPoint to -100f))[0])
    // La luminosita': gamma 2^∓0,75 sul medio, estremi fermi.
    val br = curve(FluidAdjustments.of(FluidAdjustment.Brightness to 100f))
    assertEquals(((128 / 255.0).pow(2.0.pow(-0.75)) * 255).roundToInt(), br[128])
    assertEquals(0, br[0])
    assertEquals(255, br[255])
  }

  @Test fun laVignettaturaScurisceGliAngoliENonIlCentro() {
    val w = 101
    val h = 61
    val out = run(FluidAdjustments.of(FluidAdjustment.Vignette to 100f), IntArray(w * h) { gray(200) }, w, h)
    assertEquals(200, r(out[30 * w + 50]))
    // L'angolo: la caduta piena, ×0,45.
    assertEquals(200 * 0.45, r(out[0]).toDouble(), 3.0)
    val light = run(FluidAdjustments.of(FluidAdjustment.Vignette to -100f), IntArray(w * h) { gray(150) }, w, h)
    assertTrue(r(light[0]) > 200)
    assertEquals(150, r(light[30 * w + 50]))
  }

  @Test fun laNitidezzaAllargaIlGradinoEUnaZonaPiattaNonCambia() {
    val w = 9
    val h = 3
    val step = IntArray(w * h) { i -> if (i % w < 4) gray(80) else gray(170) }
    val out = run(FluidAdjustments.of(FluidAdjustment.Sharpness to 100f), step, w, h)
    // Il laplaciano a croce: dal lato scuro del gradino (−90 a destra) d = 1,6·(−90/255)/8 = −0,0706 → 80 − 18 = 62.
    assertEquals(80 - (1.6 * 90 / 8).roundToInt(), r(out[w + 3]), 1)
    assertEquals(170 + (1.6 * 90 / 8).roundToInt(), r(out[w + 4]), 1)
    assertEquals(80, r(out[w + 0]))
    assertEquals(170, r(out[w + 8]))
  }

  @Test fun laDefinizioneAlzaIlDettaglioLargoSecondoLaMappa() {
    // Una mappa piatta a 100: un pixel a 150 ha +50/255 di dettaglio, ×0,9 a +100.
    val map = DefinitionMap(4, 4, ByteArray(16) { 100.toByte() })
    val out = run(FluidAdjustments.of(FluidAdjustment.Definition to 100f), IntArray(9) { gray(150) }, 3, 3, map)
    assertEquals(150 + (0.9 * 50).roundToInt(), r(out[4]), 1)
  }

  @Test fun laMappaDellaDefinizioneEMediaESiLeggeAiCentriDeiPixel() {
    val w = 64
    val h = 128
    val pixels = IntArray(w * h) { i -> if ((i / w) < h / 2) gray(0) else gray(255) }
    val map = DefinitionMap.of(pixels, w, h)
    assertEquals(32, map.width)
    assertEquals(64, map.height)
    // Lontano dal confine la mappa e' il colore pieno; al confine e' a meta'.
    assertEquals(0f, map.sample(16f, 5f), 0.01f)
    assertEquals(1f, map.sample(16f, 60f), 0.01f)
    assertEquals(0.5f, map.sample(16f, 32f), 0.15f)
  }

  @Test fun autoSchiarisceUnaFotoBuiaEScaldaUnaFotoBlu() {
    val dark = IntArray(64 * 64) { i -> gray(25 + (i % 64)) }
    val a = FluidAutoAdjust.suggest(dark)
    assertTrue("esposizione ${a[FluidAdjustment.Exposure]}", a[FluidAdjustment.Exposure] > 10f)
    assertTrue(a[FluidAdjustment.Contrast] > 0f)
    val blue = IntArray(64 * 64) { i -> rgb(90 + i % 40, 120 + i % 40, 190 + i % 40) }
    assertTrue(FluidAutoAdjust.suggest(blue)[FluidAdjustment.Warmth] > 5f)
    // Una foto gia' a posto resta quasi com'e'.
    val good = IntArray(64 * 64) { i -> rgb(i % 256, (i / 16) % 256, (i * 3) % 256) }
    FluidAdjustment.All.forEach { assertTrue("${it.id} ${FluidAutoAdjust.suggest(good)[it]}", abs(FluidAutoAdjust.suggest(good)[it]) <= 40f) }
  }

  @Test fun loShaderDiceGliStessiNumeriDelleCurve() {
    val text = AdjustShaderSource.text
    for (v in listOf(AdjustCurves.BlackPointCut, AdjustCurves.ShadowsUp, AdjustCurves.HighlightsDown, AdjustCurves.BrightnessGamma,
      AdjustCurves.ContrastFlat, AdjustCurves.VignetteDark, AdjustCurves.DetailLimit)) {
      assertTrue("manca $v", text.contains(String.format(java.util.Locale.ROOT, "%.6f", v)))
    }
    // Gli uniform hanno l'ordine di PlanNumbers.
    val n = PlanNumbers.of(FluidAdjustments.of(FluidAdjustment.Saturation to -40f, FluidAdjustment.Vignette to 30f))
    assertEquals(0.6f, n[9], 1e-6f)
    assertEquals(0.3f, n[13], 1e-6f)
  }
}
