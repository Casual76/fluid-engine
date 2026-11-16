package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.glass.field.GlassAdaptiveMaterial
import dev.antigravity.fluidengine.ui.glass.field.GlassAdaptiveTarget
import dev.antigravity.fluidengine.ui.glass.field.GlassBackdropStats
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidLargeDarkenMillis
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G1, rilievi A-R1, A-R5, A-R6 (i grandi che restano indietro di secondi) e C-R3 / D-R2 (il movimento ridotto):
 * l'inseguitore che non riparte da fermo, una **rampa** di letture (non solo il gradino), l'inchiostro di un grande che
 * passa di lato quando il corpo passa il punto di uguale contrasto, e il passaggio immediato col movimento ridotto.
 */
class GlassChaseTest {

  private val profile = GlassLiquidProfile.Default

  // ---- L'inseguitore, puro ------------------------------------------------------------------------------------

  @Test
  fun onAStepItArrivesInTheSettleTimeWithoutOvershoot() {
    val chase = GlassChase(GlassLiquidLargeDarkenMillis)
    chase.snap(0f)
    chase.aim(1f)
    var t = 0L
    var last = 0f
    var at750 = Float.NaN
    while (t < 1_500L) {
      chase.step(16L)
      t += 16L
      assertTrue("monotono a $t ms: ${chase.value} dopo $last", chase.value >= last - 1e-6f)
      assertTrue("mai oltre il bersaglio a $t ms: ${chase.value}", chase.value <= 1f + 1e-6f)
      last = chase.value
      if (t in 736L..751L) at750 = chase.value
    }
    // R4 A6: «da 143 a 48 in ≈1,5 s»; a metà tempo già quasi tutto.
    assertTrue("a 750 ms: $at750", at750 in 0.8f..0.95f)
    assertEquals("a 1,5 s", 1f, chase.value, 0.01f)
  }

  @Test
  fun onARampItFollowsWithASmallConstantLag() {
    // Una fascia che sale di 0,125 al secondo (0 → 1 in 8 s), letta ogni 120 ms: il bersaglio si sposta a scalini.
    val chase = GlassChase(GlassLiquidLargeDarkenMillis)
    chase.snap(0f)
    var t = 0L
    var maxLagAfterStart = 0f
    while (t <= 7_000L) {
      if (t % 120L == 0L) chase.aim((t / 8_000f).coerceAtMost(1f))
      chase.step(16L)
      t += 16L
      val quota = t / 8_000f
      if (t >= 1_500L) maxLagAfterStart = maxOf(maxLagAfterStart, quota - chase.value)
    }
    // 2·v/ω ≈ 0,05 più lo scalino delle letture (0,015): il tween che ripartiva a ogni lettura restava indietro di ≈0,45.
    assertTrue("ritardo sulla rampa: $maxLagAfterStart", maxLagAfterStart < 0.09f)
  }

  @Test
  fun aNewTargetKeepsPositionAndVelocity() {
    val chase = GlassChase(GlassLiquidLargeDarkenMillis)
    chase.snap(0f)
    chase.aim(1f)
    repeat(10) { chase.step(16L) }
    val value = chase.value
    val velocity = chase.velocity
    assertTrue("si sta muovendo: $velocity", velocity > 0.5f)
    chase.aim(0.6f)
    assertEquals("la posizione resta", value, chase.value, 0f)
    assertEquals("la velocità resta: non riparte da fermo", velocity, chase.velocity, 0f)
    chase.step(16L)
    assertTrue("e continua nello stesso verso", chase.value > value)
  }

  // ---- Lo stato di un grande con una rampa di letture ----------------------------------------------------------

  /** Uno sfondo nero per la quota [q] e grigio 226 per il resto (la scena di R4 A6), come lo vede la miniatura. */
  private fun twoParts(q: Float): GlassBackdropStats {
    val light = 226f / 255f
    val mean = (1f - q) * light
    val variance = q * (1f - q) * light * light
    return GlassBackdropStats(mean, variance, 0f, mean, mean, mean, 1f)
  }

  private fun reading(stats: GlassBackdropStats) = GlassAdaptiveTarget(
    dark = stats.luminance < 0.45f, luminance = stats.luminance, smoothedLuminance = stats.luminance, texture = 0f,
    color = Color(stats.luminance, stats.luminance, stats.luminance), measured = true,
  )

  private fun frames(untilMillis: Long, onFrame: (Long) -> Unit = {}, block: suspend CoroutineScope.() -> Unit) {
    val clock = BroadcastFrameClock()
    runBlocking(clock) {
      var t = 0L
      val job: Job = launch { block() }
      while (t <= untilMillis) {
        repeat(3) { yield() }
        clock.sendFrame((10_000L + t) * 1_000_000L)
        repeat(3) { yield() }
        onFrame(t)
        t += 16L
      }
      job.cancel()
    }
  }

  /** La quota a cui il corpo stimato passa il punto di uguale contrasto, con l'isteresi, nel verso [down]. */
  private fun expectedTurn(down: Boolean): Float {
    val qs = (0..1000).map { it / 1000f }
    return if (down) {
      qs.first { q -> glassLargeInkSideByBody(glassLargeBodyLuma(twoParts(q).luminance, q, profile), onDark = false) == 1 }
    } else {
      qs.reversed().first { q -> glassLargeInkSideByBody(glassLargeBodyLuma(twoParts(q).luminance, q, profile), onDark = true) == 0 }
    }
  }

  @Test
  fun aLargeFollowsARampAndItsInkTurnsAtTheEqualContrastQuota() {
    val state = GlassAdaptiveState()
    state.reset(darkSide = false, flipping = false, inking = true)
    val out = GlassAdaptiveMaterial()
    var quota = 0f
    var maxLag = 0f
    var turnDown = Float.NaN
    var turnUp = Float.NaN
    var lastInk = false
    frames(untilMillis = 16_500L, onFrame = { t ->
      val side = state.fill(out).darkness
      if (t in 1_500L..7_800L) maxLag = maxOf(maxLag, quota - side)
      val ink = state.inkOnDark
      if (ink && !lastInk && turnDown.isNaN()) turnDown = quota
      if (!ink && lastInk && turnUp.isNaN()) turnUp = quota
      lastInk = ink
    }) {
      // La prima misura: bianco, il lato chiaro subito.
      state.follow(reading(twoParts(0f)), twoParts(0f), darkSide = false, continuous = true)
      state.turn(state.wanted(reading(twoParts(0f))), darkSide = false) { twoParts(0f).luminance }
      // Poi la fascia sale in 8 s e scende in 8 s; la sonda legge ogni 120 ms. Come i due `collectLatest` di
      // `rememberGlassAdaptiveState`: una lettura nuova cancella l'inseguimento di prima, un lato nuovo il giro di prima.
      var follow: Job? = null
      var turn: Job? = null
      var lastWanted = state.wanted(reading(twoParts(0f)))
      var nextReading = 0L
      while (true) {
        val t = withFrameMillis { it } - 10_000L
        quota = if (t <= 8_000L) t / 8_000f else ((16_000L - t) / 8_000f).coerceAtLeast(0f)
        if (t >= nextReading) {
          nextReading = t + 120L
          val s = twoParts(quota)
          follow?.cancel()
          follow = launch { state.follow(reading(s), s, darkSide = false, continuous = true) }
        }
        val wanted = state.wanted(reading(twoParts(quota)))
        if (wanted != lastWanted) {
          lastWanted = wanted
          val l = twoParts(quota).luminance
          turn?.cancel()
          turn = launch { state.turn(wanted, darkSide = false) { l } }
        }
      }
    }
    // Il lato continuo insegue la rampa da vicino (prima: ≈0,45 di ritardo).
    assertTrue("ritardo sulla rampa: $maxLag", maxLag < 0.12f)
    // L'inchiostro passa allo scuro e torna al chiaro vicino alla quota di uguale contrasto, non a 0,97 e 0,03.
    val down = expectedTurn(down = true)
    val up = expectedTurn(down = false)
    assertTrue("andata: girato a $turnDown, uguale contrasto a $down", !turnDown.isNaN() && turnDown - down in -0.02f..0.12f)
    assertTrue("ritorno: girato a $turnUp, uguale contrasto a $up", !turnUp.isNaN() && up - turnUp in -0.02f..0.12f)
  }

  @Test
  fun theEqualContrastBodyIsAboutOneHundredEighteen() {
    // Gli inchiostri del giro (#121214 e #F2F2F5) hanno lo stesso contrasto su una luminanza relativa di ≈0,18.
    assertEquals(118f / 255f, GlassLargeInkEqualContrastLuma, 3f / 255f)
    // Sopra il bianco un grande chiaro tiene l'inchiostro scuro; sopra il nero, scurito, quello chiaro.
    assertEquals(0, glassLargeInkSideByBody(glassLargeBodyLuma(226f / 255f, 0f, profile), onDark = false))
    assertEquals(1, glassLargeInkSideByBody(glassLargeBodyLuma(0f, 1f, profile), onDark = false))
    // Una lettura che trema di un centesimo attorno al punto non fa ballare l'inchiostro.
    val at = GlassLargeInkEqualContrastLuma
    assertEquals(1, glassLargeInkSideByBody(at + 0.01f, onDark = true))
    assertEquals(0, glassLargeInkSideByBody(at - 0.01f, onDark = false))
  }

  // ---- La staffetta tasto → menu (C-R2) ------------------------------------------------------------------------

  @Test
  fun aMenuPanelIsBornWhereItsFlippedButtonIs() {
    // Il tasto: un piccolo che gira, già sul nero.
    val button = GlassAdaptiveState()
    button.reset(darkSide = false, flipping = true, inking = true)
    val out = GlassAdaptiveMaterial()
    var panelSide = Float.NaN
    var panelInk = false
    var panelAfterUnmeasured = Float.NaN
    var panelAfterReading = Float.NaN
    frames(untilMillis = 200L) {
      button.follow(reading(twoParts(1f)), twoParts(1f), darkSide = false, continuous = false)
      button.turn(1, darkSide = false) { 0f }
      // Il pannello del menu: un grande, nato dallo stato del tasto.
      val panel = GlassAdaptiveState()
      panel.reset(darkSide = false, flipping = false, inking = true)
      panel.adopt(button, continuous = true)
      panelSide = panel.fill(out).darkness
      panelInk = panel.inkOnDark
      // La sua sonda non ha ancora letto: resta dov'è nato, non torna al tema.
      panel.follow(GlassAdaptiveTarget.unmeasured(false), null, darkSide = false, continuous = true)
      panelAfterUnmeasured = panel.fill(out).darkness
      // La prima lettura (metà nero): insegue da lì, non scatta.
      launch { panel.follow(reading(twoParts(0.5f)), twoParts(0.5f), darkSide = false, continuous = true) }
      repeat(3) { withFrameMillis { } }
      panelAfterReading = panel.fill(out).darkness
    }
    assertEquals("il primo fotogramma del pannello è il lato del tasto", 1f, panelSide, 0.001f)
    assertTrue("e il suo inchiostro", panelInk)
    assertEquals("senza lettura resta lì", 1f, panelAfterUnmeasured, 0.001f)
    assertTrue("la prima lettura insegue, non scatta: $panelAfterReading", panelAfterReading > 0.9f)
  }

  // ---- Il movimento ridotto -----------------------------------------------------------------------------------

  @Test
  fun withReducedMotionALargeGoesToItsSideAtOnce() {
    val state = GlassAdaptiveState()
    state.reset(darkSide = false, flipping = false, inking = true)
    state.reducedMotion = true
    val out = GlassAdaptiveMaterial()
    var after = Float.NaN
    frames(untilMillis = 64L) {
      state.follow(reading(twoParts(0f)), twoParts(0f), darkSide = false, continuous = true)
      state.follow(reading(twoParts(1f)), twoParts(1f), darkSide = false, continuous = true)
      after = state.fill(out).darkness
    }
    assertEquals("subito al lato, senza inseguire", 1f, after, 0.001f)
  }

  @Test
  fun withTheSystemAnimationScaleAtZeroTheLargeAndTheSmallGoAtOnceToo() {
    // Il movimento ridotto del sistema (scala delle animazioni a zero) arriva come `MotionDurationScale` nel contesto
    // degli effetti, anche se l'app non dice niente (`LocalFluidMotionPolicy` di serie).
    val zero = object : androidx.compose.ui.MotionDurationScale {
      override val scaleFactor: Float = 0f
    }
    val large = GlassAdaptiveState()
    large.reset(darkSide = false, flipping = false, inking = true)
    val small = GlassAdaptiveState()
    small.reset(darkSide = false, flipping = true, inking = true)
    val out = GlassAdaptiveMaterial()
    var largeSide = Float.NaN
    var smallSide = Float.NaN
    var smallInk = false
    frames(untilMillis = 64L) {
      kotlinx.coroutines.withContext(zero) {
        large.follow(reading(twoParts(0f)), twoParts(0f), darkSide = false, continuous = true)
        large.follow(reading(twoParts(1f)), twoParts(1f), darkSide = false, continuous = true)
        largeSide = large.fill(out).darkness
        small.turn(0, darkSide = false) { 1f }
        small.turn(1, darkSide = false) { 0f }
        smallSide = small.sideNow
        smallInk = small.inkOnDark
      }
    }
    assertEquals("il grande subito al lato", 1f, largeSide, 0.001f)
    assertEquals("il piccolo subito scuro", 1f, smallSide, 0f)
    assertTrue("e l'inchiostro con lui", smallInk)
  }

  @Test
  fun withReducedMotionASmallFlipsBodyAndInkTogetherWithoutTheTransparentFrame() {
    val state = GlassAdaptiveState()
    state.reset(darkSide = false, flipping = true, inking = true)
    state.reducedMotion = true
    var side = Float.NaN
    var alphaOnDark = Float.NaN
    frames(untilMillis = 64L) {
      state.turn(0, darkSide = false) { 1f } // la prima misura: chiaro
      state.turn(1, darkSide = false) { 0f } // il nero: subito
      side = state.sideNow
      state.noteComposedInk(state.inkOnDark)
      alphaOnDark = state.inkAlpha()
    }
    assertEquals("corpo subito scuro", 1f, side, 0f)
    assertTrue("inchiostro dalla parte scura", alphaOnDark == 1f)
    assertFalse("nessun passaggio dal trasparente", alphaOnDark < 1f)
  }
}
