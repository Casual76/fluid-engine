package dev.antigravity.fluidengine.ui.glass.field

import dev.antigravity.fluidengine.ui.fluid.GlassFlipTimings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le manopole di debug del giro (S3): `fliptime`, `flipwait`, `flipon`, `flipoff`. Senza manopole il giro e il filtro
 * sono quelli di serie; ogni manopola cambia il suo numero e nient'altro; una coppia di soglie rovesciata si scarta.
 */
class GlassAdaptiveTuningTest {

  @After
  fun backToShipped() {
    GlassAdaptiveTuning.apply(null)
  }

  @Test
  fun withoutKnobs_theFlipAndTheFilterAreTheShippedOnes() {
    assertEquals(emptyMap<String, String>(), GlassAdaptiveTuning.apply(null))
    assertSame(GlassFlipTimings.Ios26, GlassAdaptiveTuning.flipTiming)
    val shipped = GlassAdaptiveKnobs.parse(emptyMap())
    assertSame(GlassAdaptiveKnobs.Shipped, shipped)
    assertEquals(GlassAdaptiveTimeConstantMillis, shipped.waitMillis, 0f)
    assertEquals(GlassAdaptiveDarkBelow, shipped.darkBelow, 0f)
    assertEquals(GlassAdaptiveLightAbove, shipped.lightAbove, 0f)
    // Manopole che non sono del giro non lo toccano.
    assertEquals(GlassAdaptiveKnobs.Shipped, GlassAdaptiveKnobs.parse(mapOf("edgedim" to "0.4", "blur" to "24")))
  }

  @Test
  fun fliptime_picksTheNamedTimings() {
    assertSame(GlassFlipTimings.Swift, GlassAdaptiveKnobs.parse(mapOf("fliptime" to "swift")).timing)
    assertSame(GlassFlipTimings.Ios26, GlassAdaptiveKnobs.parse(mapOf(" FlipTime " to " IOS26 ")).timing)
    val slow = GlassAdaptiveKnobs.parse(mapOf("fliptime" to "slow"))
    assertSame(GlassFlipTimings.Slow, slow.timing)
    assertEquals(mapOf("fliptime" to "slow"), slow.taken)
    // `slow`: corpo 450 ms, inchiostro pieno a 1000, svanito nella proporzione di iOS 26.
    assertEquals(450, GlassFlipTimings.Slow.bodyMillis)
    assertEquals(1000, GlassFlipTimings.Slow.inkMillis)
    assertEquals(160, GlassFlipTimings.Slow.inkCrossMillis)
    // Un nome che non c'è resta di serie e non si prende.
    val wrong = GlassAdaptiveKnobs.parse(mapOf("fliptime" to "lentissimo"))
    assertSame(GlassFlipTimings.Ios26, wrong.timing)
    assertTrue(wrong.taken.isEmpty())
    // Installata, la legge chi nasce dopo.
    GlassAdaptiveTuning.apply(mapOf("fliptime" to "swift"))
    assertSame(GlassFlipTimings.Swift, GlassAdaptiveTuning.flipTiming)
  }

  @Test
  fun flipwait_isTheTimeConstantOfTheFilter() {
    val quick = GlassAdaptiveKnobs.parse(mapOf("flipwait" to "100"))
    assertEquals(100f, quick.waitMillis, 0f)
    assertEquals(mapOf("flipwait" to "100"), quick.taken)
    assertEquals(150.5f, GlassAdaptiveKnobs.parse(mapOf("flipwait" to "150,5")).waitMillis, 0f)
    // Fuori da 20..2000 ms, o non un numero: di serie.
    for (bad in listOf("10", "5000", "presto", "NaN")) {
      val parsed = GlassAdaptiveKnobs.parse(mapOf("flipwait" to bad))
      assertEquals(bad, GlassAdaptiveTimeConstantMillis, parsed.waitMillis, 0f)
      assertTrue(bad, parsed.taken.isEmpty())
    }
    // Il filtro ci mette meno a decidere: dopo un salto da bianco a nero la media scende sotto 0,45 prima.
    fun crossingAfterAStep(knobs: GlassAdaptiveKnobs): Long {
      val filter = knobs.filter(initialDark = false)
      filter.sample(0L, 1f)
      filter.sample(1_000L, 0f)
      return filter.nextDeadlineMillis() - 1_000L
    }
    val shippedCrossing = crossingAfterAStep(GlassAdaptiveKnobs.Shipped)
    val quickCrossing = crossingAfterAStep(quick)
    assertTrue("di serie $shippedCrossing ms, con 100 ms $quickCrossing", quickCrossing < shippedCrossing)
    assertEquals(shippedCrossing / 2.0, quickCrossing.toDouble(), 2.0)
  }

  @Test
  fun flipOnAndOff_areTheTwoThresholds() {
    val early = GlassAdaptiveKnobs.parse(mapOf("flipon" to "0.55", "flipoff" to "0,7"))
    assertEquals(0.55f, early.darkBelow, 0f)
    assertEquals(0.7f, early.lightAbove, 0f)
    assertEquals(mapOf("flipon" to "0.55", "flipoff" to "0.7"), early.taken)
    // Una sola soglia: l'altra resta di serie.
    val late = GlassAdaptiveKnobs.parse(mapOf("flipon" to "0.35"))
    assertEquals(0.35f, late.darkBelow, 0f)
    assertEquals(GlassAdaptiveLightAbove, late.lightAbove, 0f)
    // Una coppia rovesciata (o una soglia che scavalca l'altra di serie) si scarta tutta.
    for (pair in listOf(mapOf("flipon" to "0.7", "flipoff" to "0.5"), mapOf("flipoff" to "0.4"), mapOf("flipon" to "0.62"))) {
      val parsed = GlassAdaptiveKnobs.parse(pair)
      assertEquals("$pair", GlassAdaptiveDarkBelow, parsed.darkBelow, 0f)
      assertEquals("$pair", GlassAdaptiveLightAbove, parsed.lightAbove, 0f)
      assertTrue("$pair", parsed.taken.isEmpty())
    }
    // Il filtro le usa: una prima lettura a 0,5 resta sul chiaro di serie, gira con la soglia a 0,55.
    val shipped = GlassAdaptiveKnobs.Shipped.filter(initialDark = false)
    shipped.sample(0L, 0.5f)
    assertFalse(shipped.dark)
    val earlyFilter = early.filter(initialDark = false)
    earlyFilter.sample(0L, 0.5f)
    assertTrue(earlyFilter.dark)
    // E il ritorno: dal lato scuro una lettura a 0,65 torna sul chiaro di serie, non con il ritorno a 0,70.
    val back = early.filter(initialDark = true)
    back.sample(0L, 0.65f)
    assertTrue(back.dark)
    // Installate, le prende la sonda che nasce dopo.
    GlassAdaptiveTuning.apply(mapOf("flipon" to "0.55", "flipoff" to "0.7"))
    val probe = GlassProbeState(initialDark = false)
    probe.filter.sample(0L, 0.5f)
    assertTrue(probe.filter.dark)
  }
}
