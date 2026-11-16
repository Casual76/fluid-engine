package dev.antigravity.fluidengine.ui.glass.field

import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.GlassScrollMotion
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G1, rilievi A-R3, B-R5, B-R10: «cosa fa il vetro mentre scorri». Le manopole (`flipdwell`, `flipscroll`, `flipby`,
 * `flipcover`), il filtro che trattiene il giro, il moto dello scorrimento, la quota di scuro. Di serie tutto com'era.
 */
class GlassFlipScrollTest {

  @After
  fun shipped() {
    GlassAdaptiveTuning.apply(null)
  }

  @Test
  fun withoutKnobsEverythingIsAsBefore() {
    val knobs = GlassAdaptiveKnobs.parse(emptyMap())
    assertEquals(GlassAdaptiveMinDwellMillis, knobs.dwellMillis)
    assertEquals(GlassFlipScroll.Live, knobs.scroll)
    assertEquals(GlassFlipBy.Luminance, knobs.by)
    assertEquals(GlassAdaptiveKnobs.Shipped, knobs)
    // La lettura di serie è la luminanza, anche quando la quota di scuro c'è.
    val stats = GlassBackdropStats(0.3f, 0f, 0f, 0.3f, 0.3f, 0.3f, 1f, darkShare = 1f)
    assertEquals(0.3f, glassFlipValue(stats, GlassFlipBy.Luminance), 0f)
  }

  @Test
  fun theKnobsAreReadAndTheWrongOnesStayShipped() {
    val knobs = GlassAdaptiveKnobs.parse(
      mapOf("FlipDwell" to "1000", "flipscroll" to "REST", "flipby" to "coverage", "flipcover" to "0,8"),
    )
    assertEquals(1000L, knobs.dwellMillis)
    assertEquals(GlassFlipScroll.Rest, knobs.scroll)
    assertEquals(GlassFlipBy.Coverage, knobs.by)
    assertEquals(0.8f, knobs.cover, 1e-6f)
    assertEquals(setOf("flipdwell", "flipscroll", "flipby", "flipcover"), knobs.taken.keys)
    val wrong = GlassAdaptiveKnobs.parse(mapOf("flipdwell" to "9000", "flipscroll" to "sideways", "flipcover" to "0.5"))
    assertEquals(GlassAdaptiveKnobs.Shipped, wrong)
    assertEquals(GlassFlipScroll.Ahead, GlassAdaptiveKnobs.parse(mapOf("flipscroll" to "ahead")).scroll)
  }

  @Test
  fun aOneSecondDwellNeverFlipsTwiceInLessThanASecond() {
    // La permanenza di serie è 1 s (criterio F2-a: mai due giri in meno di un secondo sullo stesso vetro; era 400 ms).
    assertEquals(1_000L, GlassAdaptiveMinDwellMillis)
    for (filter in listOf(
      GlassAdaptiveKnobs.parse(emptyMap()).filter(initialDark = false),
      GlassAdaptiveKnobs.parse(mapOf("flipdwell" to "1000")).filter(initialDark = false),
    )) {
      var t = 0L
      val flips = mutableListOf<Long>()
      // Un riquadro scuro ogni 600 ms, scuro per 300: con 400 ms di permanenza girerebbe a ogni passaggio.
      filter.sample(t, 0.9f)
      while (t < 6_000L) {
        t += 20L
        val dark = (t % 600L) < 300L
        if (filter.sample(t, if (dark) 0.05f else 0.9f)) flips += t
      }
      assertTrue("qualche giro: $flips", flips.isNotEmpty())
      for (i in 1 until flips.size) assertTrue("${flips[i - 1]} → ${flips[i]}", flips[i] - flips[i - 1] >= 1_000L)
    }
  }

  @Test
  fun aHeldFilterKeepsItsSideAndDecidesWhenReleased() {
    val filter = GlassAdaptiveFilter(initialDark = false)
    filter.sample(0L, 0.9f)
    filter.holding = true
    var t = 0L
    while (t < 1_000L) {
      t += 20L
      assertFalse("trattenuto a $t", filter.sample(t, 0.05f))
    }
    assertFalse(filter.dark)
    assertEquals("trattenuto non chiede scadenze", Long.MAX_VALUE, filter.nextDeadlineMillis())
    filter.holding = false
    assertTrue("rilasciato decide subito con la media", filter.advance(t + 1L))
    assertTrue(filter.dark)
  }

  @Test
  fun theFirstReadingDecidesEvenWhileHeld() {
    val filter = GlassAdaptiveFilter(initialDark = false)
    filter.holding = true
    assertTrue(filter.sample(0L, 0.05f))
    assertTrue(filter.dark)
  }

  @Test
  fun coverageTurnsAtTheShareAndComesBackSymmetrically() {
    val filter = GlassAdaptiveKnobs.parse(mapOf("flipby" to "coverage", "flipwait" to "20")).filter(initialDark = false)
    fun share(q: Float) = GlassBackdropStats(0.5f, 0f, 0f, 0.5f, 0.5f, 0.5f, 1f, darkShare = q)
    filter.sample(0L, glassFlipValue(share(0f), GlassFlipBy.Coverage))
    var t = 0L
    var turnedAt = Float.NaN
    var q = 0f
    while (q < 1f && turnedAt.isNaN()) {
      t += 500L
      q += 0.05f
      // La media insegue la lettura (τ = 20 ms): 100 ms dopo è arrivata, e il filtro decide alla sua scadenza.
      val v = glassFlipValue(share(q), GlassFlipBy.Coverage)
      if (filter.sample(t, v) || filter.advance(t + 100L)) turnedAt = q
    }
    assertEquals("verso lo scuro al 75%", 0.75f, turnedAt, 0.051f)
    var backAt = Float.NaN
    while (q > 0f && backAt.isNaN()) {
      t += 500L
      q -= 0.05f
      val v = glassFlipValue(share(q), GlassFlipBy.Coverage)
      if (filter.sample(t, v) || filter.advance(t + 100L)) backAt = q
    }
    assertEquals("di ritorno al 25%", 0.25f, backAt, 0.051f)
  }

  @Test
  fun theReducerCountsTheDarkShare() {
    val side = GlassProbeStatsSide * 2
    val reducer = GlassProbeStatsReducer(readSide = side, statsSide = GlassProbeStatsSide)
    reducer.begin()
    val row = ByteArray(side * 4)
    for (y in 0 until side) {
      // La metà di sopra nera, quella di sotto bianca.
      val v = if (y < side / 2) 0 else 255
      for (x in 0 until side) {
        row[x * 4] = v.toByte(); row[x * 4 + 1] = v.toByte(); row[x * 4 + 2] = v.toByte(); row[x * 4 + 3] = 255.toByte()
      }
      reducer.addRow(y, row, 0, 4)
    }
    assertEquals(0.5f, reducer.finish().darkShare, 1e-6f)
  }

  @Test
  fun theScrollMotionGivesAVelocityAndForgetsIt() {
    val motion = GlassScrollMotion()
    assertEquals(0f, motion.velocityAt(0L), 0f)
    var t = 1_000L
    repeat(6) {
      motion.note(t, -20f) // 20 px verso l'alto ogni 16 ms
      t += 16L
    }
    val v = motion.velocityAt(t - 16L)
    assertTrue("verso l'alto, svelto: $v", v < -1_000f)
    assertEquals("fermo dopo la finestra", 0f, motion.velocityAt(t + GlassScrollMotionWindowMillisForTest), 0f)
    assertEquals(t - 16L, motion.lastAtMillis)
  }

  private companion object {
    val GlassScrollMotionWindowMillisForTest: Long =
      dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.GlassScrollMotionWindowMillis + 1L
  }
}
