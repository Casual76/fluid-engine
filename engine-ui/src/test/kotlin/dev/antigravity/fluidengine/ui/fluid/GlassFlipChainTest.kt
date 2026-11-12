package dev.antigravity.fluidengine.ui.fluid

import dev.antigravity.fluidengine.ui.glass.field.GlassAdaptiveFilter
import dev.antigravity.fluidengine.ui.glass.field.GlassProbeMinIntervalMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La catena intera del giro, senza dispositivo: le letture della sonda alla sua cadenza, il filtro di F2.1
 * ([GlassAdaptiveFilter]) e la legge del giro ([GlassFlipLaw]). È la stessa simulazione della traccia `giro` del banco
 * (`artifacts/liquid-glass/bench/f2-3/giro.py`): qui con i numeri dell'engine.
 */
class GlassFlipChainTest {

  /** Lo sfondo sotto il tasto a 6:52 (R4 B6): una banda bianca, poi lo scuro che sale e copre tutto a t = 0. */
  private fun b6(t: Long): Float = when {
    t < -330 -> 0.60f
    t < -170 -> 0.88f
    t < 0 -> 0.88f - 0.77f * (t + 170) / 170f
    else -> 0.11f
  }

  /**
   * Fa girare la catena da [from] a [to] ms, con letture ogni [GlassProbeMinIntervalMillis] a partire da [phase] e
   * consegnate [latency] ms dopo (la lettura asincrona: il fotogramma dopo). Restituisce gli istanti dei giri decisi.
   */
  private fun decisions(from: Long, to: Long, phase: Long, latency: Long = 20L, background: (Long) -> Float): List<Pair<Long, Boolean>> {
    val filter = GlassAdaptiveFilter(initialDark = false)
    val out = mutableListOf<Pair<Long, Boolean>>()
    var dark = false
    var nextRead = from + phase
    for (t in from..to) {
      if (t == nextRead + latency) {
        filter.sample(t, background(nextRead))
        nextRead += GlassProbeMinIntervalMillis
      } else {
        filter.advance(t)
      }
      if (filter.measured && filter.dark != dark) {
        dark = filter.dark
        out += t to dark
      }
    }
    return out
  }

  @Test
  fun onTheB6SceneTheFilterAloneGivesApplesDelay() {
    // Per ogni fase delle letture rispetto allo scuro, il giro si decide fra 100 e 200 ms dopo la copertura piena: il
    // ritardo di 130 ms di R4 B6 lo dà il filtro, e la legge non ne aggiunge (GlassFlipTimings.Ios26.bodyDelayMillis).
    val times = (0 until GlassProbeMinIntervalMillis step 5).map { phase ->
      val flips = decisions(-1_000L, 800L, phase) { b6(it) }.filter { it.second }
      assertEquals("fase $phase: $flips", 1, flips.size)
      flips.single().first
    }
    assertTrue("decisioni $times", times.all { it in 100L..200L })
    assertEquals(0, GlassFlipTimings.Ios26.bodyDelayMillis)
  }

  @Test
  fun aCheckerboardNearTheThresholdDoesNotFlipBackAndForth() {
    // Uno sfondo che alterna caselle grandi quanto il vetro, una appena sotto la soglia scura e una appena sotto quella
    // chiara, mentre scorre: a ogni lettura uno dei due estremi, la media attorno a 0,5. Al piu' un giro, mai indietro.
    for (period in listOf(120L, 240L, 360L, 480L)) {
      for (phase in listOf(0L, 40L, 80L)) {
        val flips = decisions(0L, 6_000L, phase) { t -> if ((t / period) % 2L == 0L) 0.60f else 0.40f }
        assertTrue("periodo $period, fase $phase: $flips", flips.size <= 1)
      }
    }
    // La scacchiera del banco, piu' piccola del vetro: media 0,505 che trema di ±0,003. Nessun giro.
    val board = decisions(0L, 6_000L, 0L) { t -> 0.505f + if ((t / 120L) % 2L == 0L) 0.003f else -0.003f }
    assertTrue("scacchiera: $board", board.isEmpty())
    // Rumore di lettura attorno alla soglia scura (±0,08): nessun giro avanti e indietro.
    val random = java.util.Random(7)
    val noisy = decisions(0L, 6_000L, 0L) { 0.5f + (random.nextFloat() - 0.5f) * 0.16f }
    assertTrue("rumore: $noisy", noisy.size <= 1)
  }

  @Test
  fun theWholeTurnOnTheB6SceneMatchesTheTable() {
    // Un giro intero: decisione, poi la legge. Il corpo arriva entro 500 ms dalla copertura piena (Apple: ~430), il
    // contrasto zero cade fra 200 e 300 ms (Apple: 230–270), l'inchiostro è pieno entro 900 (Apple: coda a 830).
    val decided = decisions(-1_000L, 800L, phase = 60L) { b6(it) }.first { it.second }.first
    val law = GlassFlipLaw(GlassFlipTimings.Ios26)
    law.snap(dark = false)
    law.turn(decided, dark = true)
    val bodyDone = (decided..decided + 2_000L).first { law.body(it) >= 1f }
    val zero = (decided..decided + 2_000L).first { GlassFlipLaw.inkOnDark(law.ink(it)) }
    val inkDone = (decided..decided + 2_000L).first { law.ink(it) >= 1f }
    assertTrue("corpo pieno a $bodyDone ms", bodyDone in 300L..500L)
    assertTrue("contrasto zero a $zero ms", zero in 200L..300L)
    assertTrue("inchiostro pieno a $inkDone ms", inkDone in 700L..900L)
  }
}
