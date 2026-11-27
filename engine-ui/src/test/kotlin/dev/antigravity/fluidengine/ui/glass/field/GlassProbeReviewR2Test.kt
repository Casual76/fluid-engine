package dev.antigravity.fluidengine.ui.glass.field

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * F7.25b · Le due regole pure delle correzioni della review R2 (`artifacts/liquid-glass/review/R2/muse-vetro.md`): quanto
 * aspetta una sonda rimasta senza lettore prima di riprovare (difetto 5), e quando ripassa dopo una lettura saltata se
 * le sue sorgenti non avvisano (difetto 7). Il comportamento col lettore vero è in `F7p25bProbeReviewTest` (`sample`).
 */
class GlassProbeReviewR2Test {

  @Test
  fun `dopo un lettore mancato l'attesa raddoppia fino al tetto delle letture fallite`() {
    assertEquals(
      listOf(240L, 480L, 960L, 1_920L, 2_000L, 2_000L),
      (1..6).map { glassProbeReaderRetryMillis(it) },
    )
    assertEquals("anche dopo moltissimi tentativi", GlassProbeMaxBackoffMillis, glassProbeReaderRetryMillis(10_000))
  }

  @Test
  fun `dopo una lettura saltata una sorgente muta ripassa alla cadenza fissa, finche' il tetto lo permette`() {
    val scheduler = GlassProbeScheduler()
    scheduler.setUnannounced(true)
    var now = 1_000L
    // Le letture saltate di fila: la cadenza resta finché le letture a vuoto non superano il tetto (F2.3c).
    repeat(GlassProbeMaxRetries) {
      scheduler.onReadSent(now, rerecord = true)
      scheduler.onReadDone(now, GlassProbeOutcome.Skipped)
      assertEquals("saltata la ${it + 1}ª", now + GlassProbeCadenceMillis, scheduler.afterSkipMillis(now))
      now += GlassProbeCadenceMillis
    }
    scheduler.onReadSent(now, rerecord = true)
    scheduler.onReadDone(now, GlassProbeOutcome.Skipped)
    assertEquals("oltre il tetto si aspetta un evento", Long.MAX_VALUE, scheduler.afterSkipMillis(now))
  }

  @Test
  fun `dopo una lettura saltata una sorgente che avvisa aspetta il suo evento`() {
    val scheduler = GlassProbeScheduler()
    scheduler.setUnannounced(false)
    scheduler.onReadSent(1_000L, rerecord = true)
    scheduler.onReadDone(1_000L, GlassProbeOutcome.Skipped)
    assertEquals(Long.MAX_VALUE, scheduler.afterSkipMillis(1_000L))
  }
}
