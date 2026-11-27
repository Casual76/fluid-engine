package dev.antigravity.fluidengine.ui.fluid

import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F7.26 · Review R2, difetto 4: a fine lancio il materiale risale con la rampa ([FluidGlassQuality.onSettled]), anche
 * quando il dito ferma un lancio veloce. Senza un orologio dei fotogrammi ([FluidGlassQuality.settleScope] nullo) torna
 * intero subito, come prima: lo prova `FluidGlassQualityTest.theEndOfAFlingRestoresTheMaterialOutright`.
 */
class FluidGlassQualitySettleTest {
  // F7.26 · Review R2, difetto 4: il dito che ferma un lancio veloce non vede il materiale tornare pieno in un fotogramma.
  @Test
  fun unLancioFermatoColDitoRisalePianoNonInUnFotogramma() {
    val clock = androidx.compose.runtime.BroadcastFrameClock()
    val scope = kotlinx.coroutines.CoroutineScope(clock + kotlinx.coroutines.Dispatchers.Unconfined)
    try {
      val quality = FluidGlassQuality().also { it.settleScope = scope }
      quality.onScrollDelta(FluidGlassQuality.MinQualityVelocity * 2f)
      assertEquals(FluidGlassQuality.ScrollingFloor, quality.level, 0.0001f)
      quality.onSettled()
      assertEquals("nello stesso fotogramma niente salto", FluidGlassQuality.ScrollingFloor, quality.level, 0.0001f)
      var time = 0L
      var frames = 0
      var previous = quality.level
      while (quality.level < 1f && frames < 200) {
        time += 8_333_333L
        clock.sendFrame(time)
        frames++
        assertTrue("la rampa sale sempre", quality.level > previous)
        assertTrue("al più il 12 % della distanza per fotogramma", quality.level - previous <= 0.12f * (1f - previous) + 0.006f)
        previous = quality.level
      }
      assertEquals(1f, quality.level, 0f)
      println("F726RAMPA | dal pavimento al pieno in $frames fotogrammi")
      assertTrue("$frames fotogrammi", frames in 30..50)

      // Un nuovo scorrimento interrompe la rampa: comanda di nuovo la velocità.
      quality.onScrollDelta(FluidGlassQuality.MinQualityVelocity * 2f)
      quality.onSettled()
      clock.sendFrame(time + 8_333_333L)
      quality.onScrollDelta(FluidGlassQuality.MinQualityVelocity * 2f)
      repeat(5) { clock.sendFrame(time + (it + 2) * 8_333_333L) }
      assertEquals(FluidGlassQuality.ScrollingFloor, quality.level, 0.0001f)
    } finally {
      scope.cancel()
    }
  }
}
