package dev.antigravity.fluidengine.notify

import dev.antigravity.fluidengine.foundation.live.FluidLiveAction
import dev.antigravity.fluidengine.foundation.live.FluidLiveKind
import dev.antigravity.fluidengine.foundation.live.FluidLiveOrigin
import dev.antigravity.fluidengine.foundation.live.FluidLiveOutcome
import dev.antigravity.fluidengine.foundation.live.FluidLivePoint
import dev.antigravity.fluidengine.foundation.live.FluidLiveStage
import dev.antigravity.fluidengine.foundation.live.FluidLiveTime
import dev.antigravity.fluidengine.foundation.live.FluidLiveUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F8.19 · Il piano della notifica di un'attività viva, per le tre generazioni di Android. Pura, sulla JVM. */
class FluidLivePlanTest {
  private val labels = FluidNotifyLabels()
  private val elapsed = 1_000_000L
  private val wall = 1_800_000_000_000L

  private fun plan(update: FluidLiveUpdate, sdk: Int, outcome: FluidLiveOutcome? = null) =
    fluidLivePlan(update, sdk, elapsed, wall, labels, outcome)

  private val transfer = FluidLiveUpdate("video", FluidLiveKind.Transfer, "Converto il video", text = "3 di 10 file", progress = 0.4f)

  @Test
  fun leGenerazioniDaiNumeriDiAndroid() {
    assertEquals(FluidNotifyGeneration.Classic, FluidNotifyGeneration.of(26))
    assertEquals(FluidNotifyGeneration.Classic, FluidNotifyGeneration.of(32))
    assertEquals(FluidNotifyGeneration.Permission, FluidNotifyGeneration.of(33))
    assertEquals(FluidNotifyGeneration.Permission, FluidNotifyGeneration.of(35))
    assertEquals(FluidNotifyGeneration.Live, FluidNotifyGeneration.of(36))
    assertEquals(FluidNotifyGeneration.Live, FluidNotifyGeneration.of(37))
  }

  @Test
  fun primaDelSedici_unaNotificaConLAvanzamento() {
    for (sdk in listOf(30, 34)) {
      val p = plan(transfer, sdk)!!
      assertTrue(p.ongoing)
      assertFalse("niente promozione prima di Android 16 ($sdk)", p.requestPromotion)
      assertNull("niente gettone prima di Android 16", p.chip)
      assertEquals(FluidLiveBar(max = 100, progress = 40, indeterminate = false), p.bar)
      assertEquals("progress", p.category)
      assertEquals("3 di 10 file", p.text)
      assertFalse(p.finished)
    }
  }

  @Test
  fun sedici_ilLiveUpdateConIlGettone() {
    val p = plan(transfer, 36)!!
    assertTrue(p.requestPromotion)
    assertEquals("40%", p.chip)
    assertTrue("sotto i 7 caratteri il gettone si legge intero", p.chip!!.length < 7)
    assertTrue(p.ongoing)
    assertEquals(FluidNotifyGeneration.Live, p.generation)
  }

  @Test
  fun unaSincronizzazioneNonDiventaMaiLiveUpdate() {
    val sync = FluidLiveUpdate("sync", FluidLiveKind.Sync, "Sincronizzo")
    val p = plan(sync, 36)!!
    assertFalse(p.requestPromotion)
    assertNull(p.chip)
    assertTrue("senza misura la barra gira", p.bar!!.indeterminate)
  }

  @Test
  fun unViaggioHaLeTappeEIPunti() {
    val trip = FluidLiveUpdate(
      "viaggio", FluidLiveKind.Navigation, "Bus 12 verso casa", text = "Scendi a Piazza Dante · arrivo 18:42",
      progress = 0.5f,
      stages = listOf(FluidLiveStage(120), FluidLiveStage(600), FluidLiveStage(80)),
      points = listOf(FluidLivePoint(120), FluidLivePoint(720), FluidLivePoint(9_999)),
      chip = "18:42",
    )
    val p = plan(trip, 36)!!
    assertEquals(800, p.bar!!.max)
    assertEquals(400, p.bar!!.progress)
    assertEquals(3, p.bar!!.stages.size)
    assertEquals("un punto oltre la barra non c'è", listOf(120, 720), p.bar!!.points.map { it.position })
    assertEquals("navigation", p.category)
    assertEquals("18:42", p.chip)
    // Prima del 16 le tappe diventano una barra sola con lo stesso conto.
    val old = plan(trip, 33)!!
    assertEquals(800, old.bar!!.max)
    assertEquals("navigation", old.category)
  }

  @Test
  fun ilTimerUsaIlCronometroDelSistema() {
    val timer = FluidLiveUpdate("timer", FluidLiveKind.Timer, "Pasta", time = FluidLiveTime.until(elapsed + 300_000L))
    val p = plan(timer, 36)!!
    assertEquals(FluidLiveChronometer(wall + 300_000L, countDown = true), p.chronometer)
    assertNull("il gettone mostra il cronometro da solo", p.chip)
    assertNull("niente barra senza avanzamento", p.bar)
    assertEquals("stopwatch", p.category)
    assertEquals("progress", plan(timer, 34)!!.category)
  }

  @Test
  fun inPausa_ilTempoEFermoENelTesto() {
    val paused = FluidLiveUpdate(
      "timer", FluidLiveKind.Timer, "Pasta", time = FluidLiveTime.fixed(245_000L), paused = true, text = "Fuoco medio",
    )
    val p = plan(paused, 36)!!
    assertNull(p.chronometer)
    assertEquals("In pausa · 4:05 · Fuoco medio", p.text)
  }

  @Test
  fun chiSegueUnAttivitaDiAltriHaIlTastoPerSmettere() {
    val actions = listOf(FluidLiveAction("A", {}), FluidLiveAction("B", {}), FluidLiveAction("C", {}))
    val followed = FluidLiveUpdate(
      "mamma", FluidLiveKind.Navigation, "Alessio è sul 12", actions = actions, origin = FluidLiveOrigin.Followed,
    )
    val p = plan(followed, 36)!!
    assertTrue("seguire per scelta può diventare Live Update", p.requestPromotion)
    assertEquals(3, p.actions.size)
    assertEquals(FluidLiveUnfollowKey, p.actions.last().key)
    assertEquals("Smetti di seguire", p.actions.last().label)
    val others = followed.copy(origin = FluidLiveOrigin.Others)
    assertFalse("avviata da altri senza una scelta: mai Live Update", plan(others, 36)!!.requestPromotion)
  }

  @Test
  fun alPiuTreTasti() {
    val many = transfer.copy(actions = (1..5).map { FluidLiveAction("T$it", {}) })
    assertEquals(3, plan(many, 30)!!.actions.size)
  }

  @Test
  fun laFineChiudeConLEsitoERestaQuattroOre() {
    val done = plan(transfer, 36, FluidLiveOutcome.succeeded("Video pronto"))!!
    assertTrue(done.finished)
    assertFalse(done.ongoing)
    assertFalse("un'attività finita non è più promossa", done.requestPromotion)
    assertEquals("Video pronto", done.text)
    assertEquals("done", done.icon)
    assertEquals(4L * 60 * 60 * 1000, done.timeoutMillis)
    assertNull(done.bar)
    assertTrue(done.actions.isEmpty())
    val failed = plan(transfer, 30, FluidLiveOutcome.failed())!!
    assertEquals("Non riuscito", failed.text)
    assertEquals("failed", failed.icon)
    assertNull("un esito silenzioso non ha notifica", plan(transfer, 36, FluidLiveOutcome.Silent))
  }

  @Test
  fun soloLAvanzamentoCambiato_siRiconosce() {
    val a = plan(transfer, 36)!!
    val b = plan(transfer.copy(progress = 0.41f), 36)!!
    val c = plan(transfer.copy(progress = 0.41f, text = "4 di 10 file"), 36)!!
    assertTrue(b.onlyProgressDiffers(a))
    assertFalse("cambia la riga: è uno stato nuovo", c.onlyProgressDiffers(a))
    assertEquals("meno dell'1% non cambia la notifica", a, plan(transfer.copy(progress = 0.404f), 36))
  }

  @Test
  fun lOrologioFermo() {
    assertEquals("0:00", fluidLiveClock(0))
    assertEquals("4:05", fluidLiveClock(245_000))
    assertEquals("1:02:03", fluidLiveClock(3_723_000))
  }
}
