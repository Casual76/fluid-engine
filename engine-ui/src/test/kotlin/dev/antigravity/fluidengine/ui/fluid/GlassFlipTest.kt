package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.glass.field.GlassAdaptiveMaterial
import dev.antigravity.fluidengine.ui.glass.field.GlassAdaptiveTarget
import dev.antigravity.fluidengine.ui.glass.field.GlassBackdropStats
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il giro chiaro/scuro dei piccoli (piano «iOS in Android», F2.3, R4 B6): la legge pura ([GlassFlipLaw]) e lo stato che
 * la fa correre sui fotogrammi ([GlassAdaptiveState.turn]), con l'inchiostro che cambia parte solo da trasparente.
 */
class GlassFlipTest {

  private val ios26 = GlassFlipTimings.Ios26

  /** Campiona [read] ogni millisecondo da [from] a [to] compresi. */
  private fun samples(from: Long, to: Long, read: (Long) -> Float): List<Float> = (from..to).map(read)

  @Test
  fun theBodyTakesItsTimeAndAlmostAllOfItInTheFirstTwoThirds() {
    val law = GlassFlipLaw(ios26)
    law.snap(dark = false)
    assertTrue(law.turn(1_000L, dark = true))
    assertEquals(0f, law.body(1_000L), 0f)
    // «Corpo in 300 ms, quasi tutto nei primi 200» (R4 B6).
    assertTrue("a 200 ms il corpo e' a ${law.body(1_200L)}", law.body(1_200L) >= 0.85f)
    assertTrue(law.body(1_299L) < 1f)
    assertEquals(1f, law.body(1_300L), 0f)
    assertEquals(1f, law.body(5_000L), 0f)
  }

  @Test
  fun aDelayHoldsTheBodyStill() {
    val law = GlassFlipLaw(GlassFlipTiming(bodyDelayMillis = 130, bodyMillis = 300, inkCrossMillis = 110, inkMillis = 700))
    law.snap(dark = false)
    law.turn(0L, dark = true)
    assertEquals(0f, law.body(0L), 0f)
    assertEquals(0f, law.body(130L), 0f)
    assertTrue(law.body(140L) > 0f)
    assertEquals(1f, law.body(430L), 0f)
  }

  @Test
  fun theInkFadesIntoTheBodyAndComesBackFromTheOtherSide() {
    val law = GlassFlipLaw(ios26)
    law.snap(dark = false)
    law.turn(0L, dark = true)
    assertEquals(0f, law.ink(0L), 0f)
    // Il fotogramma a contrasto zero: l'inchiostro svanito nel corpo, a inkCrossMillis.
    assertEquals(0.5f, law.ink(ios26.inkCrossMillis.toLong()), 1e-6f)
    assertEquals(0f, GlassFlipLaw.inkAlpha(law.ink(ios26.inkCrossMillis.toLong())), 1e-5f)
    assertFalse(GlassFlipLaw.inkOnDark(law.ink(ios26.inkCrossMillis - 1L)))
    assertTrue(GlassFlipLaw.inkOnDark(law.ink(ios26.inkCrossMillis + 1L)))
    // Pieno alla fine, e non prima.
    assertTrue(law.ink(ios26.inkMillis - 1L) < 1f)
    assertEquals(1f, law.ink(ios26.inkMillis.toLong()), 0f)
    assertTrue(law.settled(ios26.inkMillis.toLong()))
    assertFalse(law.settled(ios26.inkMillis - 1L))
  }

  @Test
  fun bothChannelsAreMonotoneAndTheInkChangesSideOnce() {
    for (dark in listOf(true, false)) {
      val law = GlassFlipLaw(ios26)
      law.snap(dark = !dark)
      law.turn(0L, dark = dark)
      val body = samples(0L, 800L, law::body)
      val ink = samples(0L, 800L, law::ink)
      for (i in 1 until body.size) {
        if (dark) {
          assertTrue("corpo a $i ms", body[i] >= body[i - 1])
          assertTrue("inchiostro a $i ms", ink[i] >= ink[i - 1])
        } else {
          assertTrue("corpo a $i ms", body[i] <= body[i - 1])
          assertTrue("inchiostro a $i ms", ink[i] <= ink[i - 1])
        }
      }
      val sides = ink.map(GlassFlipLaw::inkOnDark)
      assertEquals("l'inchiostro cambia parte una volta", 1, sides.zipWithNext().count { (a, b) -> a != b })
    }
  }

  @Test
  fun aTurnThatChangesItsMindHalfwayDoesNotJump() {
    val law = GlassFlipLaw(ios26)
    law.snap(dark = false)
    law.turn(0L, dark = true)
    val bodyThen = law.body(150L)
    val inkThen = law.ink(150L)
    assertTrue(law.turn(150L, dark = false))
    // Nello stesso istante, gli stessi valori: nessun salto.
    assertEquals(bodyThen, law.body(150L), 1e-6f)
    assertEquals(inkThen, law.ink(150L), 1e-6f)
    // Fra un millisecondo e l'altro, passi piccoli (il passo piu' ripido della curva a pieno giro e' sotto 1/100).
    val body = samples(140L, 1_200L, law::body)
    val ink = samples(140L, 1_200L, law::ink)
    for (i in 1 until body.size) {
      assertTrue("corpo a ${140 + i} ms: ${body[i - 1]} -> ${body[i]}", abs(body[i] - body[i - 1]) < 0.02f)
      assertTrue("inchiostro a ${140 + i} ms: ${ink[i - 1]} -> ${ink[i]}", abs(ink[i] - ink[i - 1]) < 0.03f)
    }
    // Torna al chiaro, e il ritorno di un giro fatto in parte e' piu' breve di un giro intero.
    assertEquals(0f, law.body(150L + ios26.bodyMillis), 0f)
    assertEquals(0f, law.ink(150L + ios26.inkMillis), 0f)
    assertTrue(law.settled(150L + ios26.inkMillis))
  }

  @Test
  fun turningTowardsTheSideItIsGoingToChangesNothing() {
    val law = GlassFlipLaw(ios26)
    law.snap(dark = true)
    assertFalse(law.turn(10L, dark = true))
    assertEquals(1f, law.body(10L), 0f)
    assertEquals(1f, law.ink(10L), 0f)
    law.turn(20L, dark = false)
    assertFalse(law.turn(60L, dark = false))
  }

  @Test
  fun theSwiftVariantIsShorterAndKeepsTheSameShape() {
    val swift = GlassFlipLaw(GlassFlipTimings.Swift)
    swift.snap(dark = false)
    swift.turn(0L, dark = true)
    assertEquals(1f, swift.body(GlassFlipTimings.Swift.bodyMillis.toLong()), 0f)
    assertEquals(0.5f, swift.ink(GlassFlipTimings.Swift.inkCrossMillis.toLong()), 1e-6f)
    assertTrue(swift.settled(GlassFlipTimings.Swift.inkMillis.toLong()))
  }

  // ---- Lo stato sui fotogrammi ----------------------------------------------------------------------------------

  /** Un fotogramma ogni [step] ms; prima di ognuno chiama [beforeFrame] (il disegno del fotogramma precedente). */
  private class Frames(private val step: Long, private val beforeFrame: () -> Unit) : MonotonicFrameClock {
    var now = 10_000L
    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
      beforeFrame()
      now += step
      return onFrame(now * 1_000_000L)
    }
  }

  @Test
  fun theFirstMeasureDoesNotAnimateAndTheNextOneTurns() {
    val state = GlassAdaptiveState()
    state.reset(darkSide = false, flipping = true, inking = true)
    val out = GlassAdaptiveMaterial()
    // Prima della misura: il lato del tema, al bit.
    assertEquals(0f, state.fill(out).darkness, 0f)
    assertEquals(1f, state.inkAlpha(), 0f)
    // La prima misura dice scuro: ci si va subito (niente giro all'apertura di una pagina).
    val frames = Frames(16L) {}
    runBlocking(frames) { state.turn(1, darkSide = false) { 0.1f } }
    assertEquals(1f, state.fill(out).darkness, 0f)
    assertTrue(state.inkOnDark)
    assertTrue(state.appearance.isDark)
    assertTrue(state.appearance.isMeasured)
    // Una lettura persa non rimette il lato del tema.
    runBlocking(frames) { state.turn(-1, darkSide = false) { Float.NaN } }
    assertEquals(1f, state.fill(out).darkness, 0f)
  }

  @Test
  fun neverAFrameWithTheInkOfTheWrongSide() {
    val state = GlassAdaptiveState()
    state.reset(darkSide = false, flipping = true, inking = true)
    val out = GlassAdaptiveMaterial()
    // Il caso peggiore: la composizione arriva con un fotogramma di ritardo sul cambio di parte.
    var composed = false
    state.noteComposedInk(composed)
    val drawn = mutableListOf<Triple<Float, Float, Boolean>>()
    val frames = Frames(16L) {
      drawn += Triple(state.fill(out).darkness, state.inkAlpha(), state.inkOnDark)
      if (composed != state.inkOnDark) {
        composed = state.inkOnDark
        state.noteComposedInk(composed)
      }
    }
    runBlocking(frames) {
      state.turn(0, darkSide = false) { 0.9f } // la prima misura: chiaro, subito
      state.turn(1, darkSide = false) { 0.1f } // poi lo sfondo diventa scuro: il giro
    }
    drawn += Triple(state.fill(out).darkness, state.inkAlpha(), state.inkOnDark)
    // Il corpo arriva allo scuro, l'inchiostro a quello del vetro scuro, pieno.
    assertEquals(1f, drawn.last().first, 0f)
    assertEquals(1f, drawn.last().second, 0f)
    assertTrue(drawn.last().third)
    // C'e' un fotogramma a contrasto zero (inchiostro trasparente)...
    assertTrue(drawn.any { it.second == 0f })
    // ...e il corpo non torna mai indietro.
    for (i in 1 until drawn.size) assertTrue(drawn[i].first >= drawn[i - 1].first)
    // Il giro dura quanto dice la legge, a fotogrammi da 16 ms.
    assertTrue("fotogrammi: ${drawn.size}", drawn.size in 40..50)
  }

  @Test
  fun aSurfaceWhoseContentDoesNotFollowTheInkNeverTurns() {
    val state = GlassAdaptiveState()
    state.reset(darkSide = false, flipping = false, inking = false)
    val out = GlassAdaptiveMaterial()
    runBlocking(Frames(16L) {}) { state.turn(1, darkSide = false) { 0.1f } }
    assertTrue(state.fill(out).darkness.isNaN())
    assertEquals(1f, state.inkAlpha(), 0f)
    assertFalse(state.appearance.isMeasured)
  }

  // ---- La banda morta delle letture ------------------------------------------------------------------------------

  private fun reading(luminance: Float, dark: Boolean = luminance < 0.45f, measured: Boolean = true) = GlassAdaptiveTarget(
    dark = dark, luminance = luminance, smoothedLuminance = luminance, texture = 0f,
    color = Color(luminance, luminance, luminance), measured = measured,
  )

  private fun stats(luminance: Float) = GlassBackdropStats(
    luminance = luminance, variance = 0f, gradientEnergy = 0f, red = luminance, green = luminance, blue = luminance,
    coverage = 1f,
  )

  @Test
  fun aReadingWithinTheDeadBandDoesNotMoveTheMaterial() {
    val a = reading(0.500f)
    // Mezzo centesimo: la stessa lettura, anche se la media del filtro si e' mossa.
    assertTrue(glassReadingsAlike(a, stats(0.500f), reading(0.505f).copy(smoothedLuminance = 0.7f), stats(0.505f)))
    // Due centesimi, un altro lato, la prima misura: letture nuove.
    assertFalse(glassReadingsAlike(a, stats(0.500f), reading(0.520f), stats(0.520f)))
    assertFalse(glassReadingsAlike(a, stats(0.500f), reading(0.500f, dark = true), stats(0.500f)))
    assertFalse(glassReadingsAlike(reading(1f, measured = false), null, a, stats(0.5f)))
    // Senza misura tutte uguali.
    assertTrue(glassReadingsAlike(reading(1f, measured = false), null, reading(0f, dark = false, measured = false), null))
  }

  // ---- Il lato per superficie ----------------------------------------------------------------------------------

  @Test
  fun theSideOfAMeasuredGlassComesBeforeTheTheme() {
    val light = Color(0xFFFFFFFF)
    val measuredDark = FluidGlassAppearance(isDark = true, darkness = 1f, backdropLuminance = 0.1f, isMeasured = true)
    val unmeasuredDark = FluidGlassAppearance(isDark = true, darkness = 1f, backdropLuminance = Float.NaN, isMeasured = false)
    // Dentro un vetro misurato: il lato del vetro, anche contro il tema e contro il lato dichiarato.
    assertTrue(GlassDefaults.surfaceSide(measuredDark, declaredSide = false, surface = light))
    // Un vetro non ancora misurato non cambia niente: il tema, come prima.
    assertFalse(GlassDefaults.surfaceSide(unmeasuredDark, declaredSide = null, surface = light))
    assertFalse(GlassDefaults.surfaceSide(null, declaredSide = null, surface = light))
    assertTrue(GlassDefaults.surfaceSide(null, declaredSide = true, surface = light))
  }
}
