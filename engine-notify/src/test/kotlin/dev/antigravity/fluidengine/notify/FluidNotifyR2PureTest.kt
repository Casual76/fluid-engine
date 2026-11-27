package dev.antigravity.fluidengine.notify

import dev.antigravity.fluidengine.foundation.live.FluidLiveKind
import dev.antigravity.fluidengine.foundation.notify.FluidReminderDelivery
import dev.antigravity.fluidengine.foundation.live.FluidLiveStage
import dev.antigravity.fluidengine.foundation.live.FluidLiveTime
import dev.antigravity.fluidengine.foundation.live.FluidLiveUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R2 · I difetti puri di F8.19 (il piano delle attività vive e la memoria), sulla JVM. */
class FluidNotifyR2PureTest {
  private val labels = FluidNotifyLabels()
  private val elapsed = 1_000_000L
  private val wall = 1_800_000_000_000L

  @Test
  fun r2m_tappeSenzaAvanzamento_laBarraGira() {
    val update = FluidLiveUpdate("viaggio", FluidLiveKind.Navigation, "Verso casa", stages = listOf(FluidLiveStage(3), FluidLiveStage(7)))
    val bar = fluidLivePlan(update, 36, elapsed, wall, labels)!!.bar!!
    assertTrue("senza misura la barra gira anche con le tappe", bar.indeterminate)
    assertEquals(2, bar.stages.size)
  }

  @Test
  fun r2m_inPausa_unTempoCheCorre_restaFermoNelTesto() {
    val update = FluidLiveUpdate(
      "timer", FluidLiveKind.Timer, "Pasta", text = "Spaghetti", time = FluidLiveTime.until(elapsed + 245_000L), paused = true,
    )
    val plan = fluidLivePlan(update, 34, elapsed, wall, labels)!!
    assertEquals("In pausa · 4:05 · Spaghetti", plan.text)
    assertEquals(null, plan.chronometer)
  }

  @Test
  fun r2q5_laMemoria_ricordaUnPromemoriaNonMostrato() {
    val missed = FluidReminderDelivery("pasta", "Scola la pasta", 1_000L, 1_000L, "Le notifiche erano spente", shown = false)
    val back = FluidNotifyCodec.decode(FluidNotifyCodec.encode(FluidNotifyMemory(deliveries = listOf(missed, missed.copy(id = "te", shown = true)))))
    assertEquals(listOf(false, true), back.deliveries.map { it.shown })
    assertEquals(missed, back.deliveries.first())
  }
}
