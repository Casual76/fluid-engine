package dev.antigravity.fluidengine.ui.fluid

import dev.antigravity.fluidengine.ui.glass.field.GlassEdgeBlurKernel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * La grammatica del contenuto (F3.4) contro i numeri di R7 A.3 / A4 e R4 B2, più i contratti: chi resta non si tocca,
 * l'interruzione riparte da dove si trova, niente immagine doppia nitida.
 */
class FluidGlassContentTransitionTest {

  /** La clip di 16:02 (R7 A.3), T ≈ 434 ms: i confronti numerici si fanno alla sua durata. */
  private val clip = GlassContentTiming(durationMs = 434f)
  private val peak = GlassContentDefaults.PeakBlur

  private fun exitOf(frame: GlassContentFrame) = frame.layers.singleOrNull { it.role == GlassContentRole.Exit }?.look
  private fun enterOf(frame: GlassContentFrame) = frame.layers.single {
    it.role == GlassContentRole.Enter || it.role == GlassContentRole.Born
  }.look

  @Test fun outgoingBlurRisesLikeTheMeasuredCurve() {
    // R7 A.3, chi esce: σ/h a 33 / 67 / 100 / 133 / 167 ms = 0,02 / 0,04 / 0,065 / 0,10 / 0,13.
    val measured = listOf(33.0 to 0.02f, 67.0 to 0.04f, 100.0 to 0.065f, 133.0 to 0.10f, 167.0 to 0.13f)
    for ((ms, sigma) in measured) {
      val look = exitOf(glassContentFrame(ms, "cartella", "graffetta", clip))!!
      assertEquals("σ a $ms ms", sigma, look.blur, 0.012f)
    }
  }

  @Test fun blurPeaksAtHalfTheJourneyAndTheNewContentIsSharpAtT() {
    // R7: σ massima ≈0,15 h a 200–234 ms; la nuova nitida a ≈430 ms.
    val samples = (0..600).map { it.toDouble() to glassContentFrame(it.toDouble(), "a", "b", clip) }
    val peakMs = samples.maxBy { (_, f) -> f.layers.maxOf { it.look.blur } }.first
    assertTrue("picco a $peakMs ms", peakMs in 200.0..234.0)
    assertEquals(peak, samples.maxOf { (_, f) -> f.layers.maxOf { it.look.blur } }, 1e-4f)
    assertEquals(0f, enterOf(glassContentFrame(434.0, "a", "b", clip)).blur, 1e-4f)
    // L'opacità arriva a 1 a 1,15 T («contrasto pieno»), e lì il cambio è finito.
    val end = glassContentFrame(434.0 * 1.15 + 0.5, "a", "b", clip)
    assertTrue(end.finished)
    assertEquals(1f, enterOf(end).alpha, 1e-4f)
    assertEquals(1f, enterOf(end).scale, 1e-4f)
  }

  @Test fun theTwoIconsCrossAtTheBlurMaximumForSeventyToHundredMilliseconds() {
    for (duration in listOf(400f, 434f)) {
      val timing = GlassContentTiming(durationMs = duration)
      var together = 0
      var crossMs = -1
      var sumAtCross = 0f
      for (ms in 0..700) {
        val frame = glassContentFrame(ms.toDouble(), "a", "b", timing)
        val exit = exitOf(frame) ?: continue
        val enter = enterOf(frame)
        if (exit.alpha > 0.1f && enter.alpha > 0.1f) together++
        if (crossMs < 0 && enter.alpha >= exit.alpha) {
          crossMs = ms
          sumAtCross = exit.alpha + enter.alpha
        }
      }
      // R7 A.3: insieme per 70–100 ms; incrocio a 0,45–0,5 T con α ≈0,35 ciascuna (somma ≈0,7).
      assertTrue("insieme per $together ms con T = $duration", together in 70..100)
      assertTrue("incrocio a $crossMs ms con T = $duration", crossMs / duration in 0.43f..0.5f)
      assertEquals(0.7f, sumAtCross, 0.05f)
      // All'incrocio la sfocatura è al massimo (R4 B2: «le icone si incrociano al massimo della sfocatura»).
      val atCross = glassContentFrame(crossMs.toDouble(), "a", "b", timing)
      assertTrue(atCross.layers.all { it.look.blur > 0.9f * peak })
    }
  }

  @Test fun noFrameShowsTwoReadableIcons() {
    // L'immagine doppia nitida è l'errore che la sfocatura copre (R7 A.4): quando tutte e due si vedono, tutte e due
    // sono sfocate almeno 0,08 h (la soglia della finestra della tinta, R7 A4).
    for (ms in 0..600) {
      val frame = glassContentFrame(ms.toDouble(), "a", "b", clip)
      val exit = exitOf(frame) ?: continue
      val enter = enterOf(frame)
      if (exit.alpha >= 0.1f && enter.alpha >= 0.1f) {
        assertTrue("$ms ms: σ uscente ${exit.blur}", exit.blur >= 0.08f)
        assertTrue("$ms ms: σ entrante ${enter.blur}", enter.blur >= 0.08f)
      }
    }
  }

  @Test fun scalesFollowRoleAndPredecessor() {
    // Chi esce scende a 0,9; chi entra al posto di un altro parte da 0,93; chi nasce senza predecessore da 0,75.
    assertEquals(0.9f, exitOf(glassContentFrame(200.0, "a", "b", clip))!!.scale, 1e-3f)
    assertEquals(1f, exitOf(glassContentFrame(80.0, "a", "b", clip))!!.scale, 1e-3f)
    val replaced = glassContentFrame(200.0, "a", "b", clip)
    assertEquals(GlassContentRole.Enter, replaced.layers.last().role)
    assertEquals(0.93f, enterOf(replaced).scale, 1e-3f)
    val born = glassContentFrame(200.0, null, "più", clip)
    assertEquals(listOf(GlassContentRole.Born), born.layers.map { it.role })
    assertEquals(0.75f, enterOf(born).scale, 1e-3f)
    // Il «più» di R7 A4: 0,89 a 334 ms, 1 a T.
    assertEquals(0.89f, enterOf(glassContentFrame(334.0, null, "più", clip)).scale, 0.02f)
  }

  @Test fun tintChangesInsideTheBlur() {
    // R7 A4: la tinta si incrocia fra 0,25 T e 0,5 T, mentre σ è sopra 0,08 h; a ≈130 ms è già cominciata.
    for (ms in 0..600) {
      val frame = glassContentFrame(ms.toDouble(), "a", "b", clip)
      if (frame.tint > 0.01f && frame.tint < 0.99f) {
        val visible = frame.layers.filter { it.look.alpha > 0.05f }
        assertTrue("$ms ms: tinta ${frame.tint} fuori dalla sfocatura", visible.all { it.look.blur >= 0.07f })
      }
    }
    assertTrue(glassContentFrame(133.0, "a", "b", clip).tint > 0.05f)
    assertEquals(1f, glassContentFrame(0.5 * 434.0, "a", "b", clip).tint, 1e-4f)
  }

  @Test fun theSameKeyIsNeverTouched() {
    // R7 A3, D2 di Alessio: stessa chiave nei due stati → nitido, opaco, a scala 1, dal primo fotogramma.
    val journey = GlassContentJourney.start("più", "più", clip)
    for (ms in 0..500 step 10) {
      val frame = journey.frame(ms.toDouble())
      assertTrue(frame.finished)
      assertEquals(listOf(GlassContentLook.Sharp), frame.layers.map { it.look })
    }
    val rest = GlassContentJourney.rest("più", clip)
    assertSame(rest, rest.retarget(123.0, "più"))
  }

  @Test fun goingToNothingLetsTheContentLeave() {
    val journey = GlassContentJourney.start("rispondi", null, clip)
    assertEquals(null, journey.targetKey)
    assertTrue(journey.frame(0.66 * 434.0 + 0.5).finished)
    assertTrue(journey.frame(0.66 * 434.0 + 0.5).layers.isEmpty())
    assertTrue(journey.settle().layers.isEmpty())
  }

  @Test fun everyCurveMovesWithoutJumps() {
    // Nessuno scalino: a passi di 1 ms nessuna grandezza cambia più di quanto le curve consentono (la più ripida,
    // l'opacità di chi esce fra 0,47 e 0,56 T, scende di ≈0,005 al millisecondo).
    assertNoJumps(GlassContentJourney.start("a", "b", clip), 0.0..600.0)
    assertNoJumps(GlassContentJourney.start(null, "b", clip), 0.0..600.0)
  }

  @Test fun anInterruptionRestartsFromWhereItIs() {
    // Invariante 16: uno stato nuovo a metà riparte dall'aspetto di adesso, senza scalini, e chi arriva non compare
    // di colpo.
    for (at in listOf(30.0, 90.0, 150.0, 200.0, 260.0, 330.0, 420.0, 480.0)) {
      val before = GlassContentJourney.start("a", "b", clip)
      val after = before.retarget(at, "c")
      val was = before.frame(at).layers.associateBy { it.id }
      val now = after.frame(at)
      for (layer in now.layers) {
        val old = was[layer.id]
        if (old == null) {
          assertEquals("$at ms: il nuovo compare di colpo", 0f, layer.look.alpha, 1e-4f)
        } else {
          assertEquals("$at ms: α di ${layer.key}", old.look.alpha, layer.look.alpha, 1e-4f)
          assertEquals("$at ms: scala di ${layer.key}", old.look.scale, layer.look.scale, 1e-4f)
          assertEquals("$at ms: σ di ${layer.key}", old.look.blur, layer.look.blur, 1e-4f)
        }
      }
      assertEquals("c", after.targetKey)
      assertNoJumps(after, at..at + 700.0)
      val end = after.frame(at + 700.0)
      assertTrue(end.finished)
      assertEquals(listOf<Any?>("c"), end.layers.map { it.key })
      assertEquals(GlassContentLook.Sharp, end.layers.single().look)
    }
  }

  @Test fun anInterruptionKeepsTheTintContinuous() {
    val before = GlassContentJourney.start("a", "b", clip)
    val after = before.retarget(150.0, "c")
    // Il composable rifà partire il colore da quello visibile; la frazione della tinta riparte da zero, senza saltare.
    assertEquals(0f, after.frame(150.0).tint, 1e-4f)
    var last = 0f
    for (ms in 150..800) {
      val tint = after.frame(ms.toDouble()).tint
      assertTrue(tint >= last - 1e-5f)
      assertTrue(abs(tint - last) < 0.03f)
      last = tint
    }
    assertEquals(1f, last, 1e-4f)
  }

  @Test fun bouncingBackAndForthStaysBoundedAndEnds() {
    var journey = GlassContentJourney.start("a", "b", clip)
    var t = 0.0
    val keys = listOf("a", "b", "a", "c", "a")
    for (key in keys) {
      t += 60.0
      journey = journey.retarget(t, key).pruned(t)
      // Gli strati non crescono senza limite: chi ha finito di uscire si toglie.
      assertTrue(journey.layers.size <= keys.size + 1)
    }
    assertNoJumps(journey, t..t + 800.0)
    val end = journey.frame(t + 800.0)
    assertTrue(end.finished)
    assertEquals("a", end.layers.single().key)
  }

  @Test fun withoutBlurTheOldIsGoneBeforeTheNewArrives() {
    // Sotto API 31: niente sfocatura, quindi niente sovrapposizione (sarebbe un'immagine doppia nitida).
    for (ms in 0..600) {
      val frame = glassContentFrame(ms.toDouble(), "a", "b", clip, blurAvailable = false)
      assertTrue(frame.layers.all { it.look.blur == 0f })
      val exit = exitOf(frame)
      if (exit != null) assertFalse("$ms ms", exit.alpha > 0.01f && enterOf(frame).alpha > 0.01f)
    }
    assertTrue(glassContentFrame(434.0 * 1.15 + 1, "a", "b", clip, blurAvailable = false).finished)
  }

  @Test fun monotoneCurvesPassThroughTheirKeysAndNeverOvershoot() {
    val curve = GlassContentCurves.enterAlpha
    val keys = listOf(0.36f to 0f, 0.47f to 0.35f, 0.62f to 0.6f, 0.77f to 0.78f, 1f to 0.9f, 1.15f to 1f)
    keys.forEach { (u, v) -> assertEquals(v, curve.at(u), 1e-5f) }
    var last = -1f
    for (i in 0..1500) {
      val v = curve.at(i / 1000f)
      assertTrue(v >= last - 1e-6f)
      assertTrue(v in 0f..1f)
      last = v
    }
    for (i in 0..1000) assertTrue(GlassContentCurves.exitAlpha.at(i / 1000f) in 0f..1f)
  }

  @Test fun riseTimeInvertsTheBlurRise() {
    for (i in 0..36) {
      val u = i / 100f
      assertEquals(u, GlassContentCurves.riseTimeFor(GlassContentCurves.blurRise(u)), 2e-3f)
    }
    assertEquals(GlassContentDefaults.EnterAt, GlassContentCurves.riseTimeFor(1f), 0f)
  }

  @Test fun blurIsMeasuredOnTheGlyphAndConvertedToTheHwuiRadius() {
    // σ massima su un'icona da 26 dp: 0,15 della sagoma (0,75 del riquadro) ≈ 2,9 dp, cioè i 3,0–3,4 pt misurati
    // da R7 (16–18 px su un vetro di 235 px = 44 pt).
    val density = 3f
    val sigmaPx = glassContentSigmaPx(peak, 26f * density)
    assertEquals(2.93f, sigmaPx / density, 0.02f)
    val radius = glassContentBlurRadiusPx(sigmaPx)
    assertEquals(sigmaPx, GlassEdgeBlurKernel.radiusToSigma(radius), 1e-3f)
    assertEquals(0f, glassContentBlurRadiusPx(0f), 0f)
  }

  @Test fun theSteepMixShortensTheDoubleImageAtTheEndOfAJourney() {
    // Il rischio di F3 («Rischi e ripieghi»): `w` mescola nitido e sfocato. Negli ultimi 150 ms di un viaggio (nove
    // fotogrammi a 60 Hz) con una rampa lineare le due copie si leggono insieme per cinque fotogrammi o più; con la
    // curva ripida per due al più.
    val ramp = (0..9).map { 1f - it / 9f }
    val linear = glassMixAmbiguousFrames(ramp)
    val steep = glassMixAmbiguousFrames(ramp.map { glassMixSteep(it) })
    assertTrue("rampa lineare: $linear", linear >= 5)
    assertTrue("curva ripida: $steep", steep <= 2)
    // La comparsa (430 ms, 26 fotogrammi): la curva ripida dimezza il tratto ma non lo chiude.
    val appear = (0..26).map { it / 26f }
    val appearSteep = glassMixAmbiguousFrames(appear.map { glassMixSteep(it) })
    assertTrue(appearSteep * 2 <= glassMixAmbiguousFrames(appear))
    assertTrue("comparsa: $appearSteep", appearSteep >= 3)
    // La curva ripida tiene i capi: a riposo il materiale non cambia di un bit.
    assertEquals(0f, glassMixSteep(0f), 0f)
    assertEquals(1f, glassMixSteep(1f), 0f)
  }

  @Test fun theRadiusFollowingWShowsOneEdgeWhereTheMixShowsTwo() {
    // Una linea di testo larga 2,5 dp vista da vicino, sfocata con σ = 4 dp. La mescola somma la linea nitida e quella
    // sfocata: il gradiente ha due massimi su ciascun lato (due bordi, l'immagine doppia). Il raggio che scende con
    // `w` dà una gaussiana sola: un massimo per lato.
    val sigma = 4f
    for (w in listOf(0.3f, 0.5f, 0.7f)) {
      val mix = profile { x -> w * box(x) + (1f - w) * blurredBox(x, sigma) }
      val single = profile { x -> blurredBox(x, max(glassMixRadius(w, sigma), 0.05f)) }
      assertEquals("mescola a w = $w", 2, edgesOnTheLeft(mix))
      assertEquals("raggio a w = $w", 1, edgesOnTheLeft(single))
    }
  }

  // --- aiuti ------------------------------------------------------------------------------------------------------

  private fun assertNoJumps(journey: GlassContentJourney, range: ClosedFloatingPointRange<Double>) {
    var previous: Map<Long, GlassContentLook>? = null
    var t = range.start
    while (t <= range.endInclusive) {
      val frame = journey.frame(t)
      val looks = frame.layers.associate { it.id to it.look }
      previous?.let { before ->
        for ((id, look) in looks) {
          val old = before[id] ?: GlassContentLook(0f, look.scale, look.blur).also {
            // Uno strato che compare deve farlo a opacità zero.
            assertEquals("$t ms: strato $id compare a ${look.alpha}", 0f, look.alpha, 0.01f)
          }
          assertTrue("$t ms: α di $id salta", abs(look.alpha - old.alpha) <= 0.012f)
          assertTrue("$t ms: scala di $id salta", abs(look.scale - old.scale) <= 0.004f)
          assertTrue("$t ms: σ di $id salta", abs(look.blur - old.blur) <= 0.0015f)
        }
        // Uno strato che sparisce deve farlo a opacità zero.
        for ((id, look) in before) if (id !in looks) assertTrue("$t ms: $id sparisce a ${look.alpha}", look.alpha < 0.01f)
      }
      previous = looks
      t += 1.0
    }
    assertNotNull(previous)
  }

  private fun box(x: Float) = if (abs(x) <= 1.25f) 1f else 0f

  /** La linea larga 2,5 sfocata con σ: differenza di due funzioni d'errore. */
  private fun blurredBox(x: Float, sigma: Float): Float =
    0.5f * (erf((x + 1.25f) / (sigma * sqrt(2f))) - erf((x - 1.25f) / (sigma * sqrt(2f))))

  private fun profile(f: (Float) -> Float): FloatArray = FloatArray(401) { f(-20f + it * 0.1f) }

  /**
   * Quanti bordi si vedono a sinistra della linea: i massimi locali del gradiente. La soglia assoluta (1e-4 per passo di
   * 0,1 dp) sta sopra l'errore della funzione d'errore (1,5e-7) e sotto il gobbo sfocato (≈1e-3): conta le due copie,
   * non il rumore delle code.
   */
  private fun edgesOnTheLeft(profile: FloatArray): Int {
    val grad = FloatArray(200) { i -> max(0f, profile[i + 1] - profile[i]) }
    var count = 0
    for (i in 1 until grad.size - 1) {
      if (grad[i] > 1e-4f && grad[i] > grad[i - 1] && grad[i] >= grad[i + 1]) count++
    }
    return count
  }

  /** La funzione d'errore (Abramowitz e Stegun 7.1.26, errore sotto 1,5e-7): basta per contare i massimi. */
  private fun erf(x: Float): Float {
    val sign = if (x < 0f) -1f else 1f
    val a = abs(x.toDouble())
    val t = 1.0 / (1.0 + 0.3275911 * a)
    val y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) *
      t * exp(-a * a)
    return sign * y.toFloat()
  }
}
