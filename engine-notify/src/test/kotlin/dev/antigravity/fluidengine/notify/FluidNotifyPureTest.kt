package dev.antigravity.fluidengine.notify

import dev.antigravity.fluidengine.foundation.FluidRecurrenceEnd
import dev.antigravity.fluidengine.foundation.FluidRecurrenceFrequency
import dev.antigravity.fluidengine.foundation.FluidRecurrenceRule
import dev.antigravity.fluidengine.foundation.notify.FluidNotifyFix
import dev.antigravity.fluidengine.foundation.notify.FluidNotifyLiveState
import dev.antigravity.fluidengine.foundation.notify.FluidReminderDelivery
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F8.19 · Le parti pure di engine-notify: la porta del ritmo, i promemoria nel tempo, il riepilogo, la memoria, i controlli. */
class FluidNotifyPureTest {
  private val rome = ZoneId.of("Europe/Rome")
  private val london = ZoneId.of("Europe/London")
  private val tokyo = ZoneId.of("Asia/Tokyo")

  private fun millis(text: String, zone: ZoneId = rome) = ZonedDateTime.of(LocalDateTime.parse(text), zone).toInstant().toEpochMilli()

  // --- la porta del ritmo ------------------------------------------------------------------------------------------------

  private class FakeTimer {
    var now = 0L
    val queue = ArrayList<Pair<Long, () -> Unit>>()
    fun schedule(delay: Long, block: () -> Unit) {
      queue += (now + delay) to block
    }
    fun advance(to: Long) {
      while (true) {
        val next = queue.filter { it.first <= to }.minByOrNull { it.first } ?: break
        queue.remove(next)
        now = next.first
        next.second()
      }
      now = to
    }
  }

  @Test
  fun laPorta_maiPiuDiQuattroAlSecondo() {
    val timer = FakeTimer()
    val gate = FluidNotifyGate({ timer.now }, timer::schedule)
    val times = ArrayList<Long>()
    repeat(10) { i -> gate.submit("k$i") { times += timer.now } }
    assertEquals("quattro subito", 4, times.size)
    timer.advance(5_000)
    assertEquals(10, times.size)
    // In ogni finestra di un secondo, al massimo quattro.
    for (t in times) assertTrue(times.count { it >= t && it < t + 1_000 } <= 4)
  }

  @Test
  fun laPorta_tieneSoloLUltimoStato() {
    val timer = FakeTimer()
    val gate = FluidNotifyGate({ timer.now }, timer::schedule)
    repeat(4) { i -> gate.submit("altro$i") {} }
    val shown = ArrayList<Int>()
    for (v in 1..100) gate.submit("barra") { shown += v }
    timer.advance(2_000)
    assertEquals("cento aggiornamenti in attesa diventano uno, l'ultimo", listOf(100), shown)
    assertEquals(99, gate.coalesced)
  }

  @Test
  fun laPorta_rispettaNonPrima() {
    val timer = FakeTimer()
    val gate = FluidNotifyGate({ timer.now }, timer::schedule)
    var at = -1L
    gate.submit("x", notBefore = 700) { at = timer.now }
    assertEquals(-1L, at)
    timer.advance(699)
    assertEquals(-1L, at)
    timer.advance(800)
    assertEquals(700L, at)
  }

  @Test
  fun laPorta_unAvanzamentoContinuo_dieciSecondi() {
    // Cinquanta aggiornamenti al secondo per dieci secondi, uno ogni 20 ms: la porta ne lascia passare al massimo quattro
    // al secondo, e l'ultimo valore arriva sempre.
    val timer = FakeTimer()
    val gate = FluidNotifyGate({ timer.now }, timer::schedule)
    var posts = 0
    var last = 0
    for (step in 1..500) {
      timer.advance(step * 20L)
      gate.submit("barra") {
        posts++
        last = step
      }
    }
    timer.advance(20_000)
    assertTrue("pubblicazioni: $posts", posts <= 4 * 11)
    assertEquals(500, last)
  }

  // --- i promemoria nel tempo -------------------------------------------------------------------------------------------

  @Test
  fun unOraLocaleResta_alleOttoAncheCambiandoFuso() {
    val time = FluidReminderTime.local(LocalDateTime.parse("2026-12-03T08:00"))
    val inRome = fluidReminderDue(time, null, rome, null, 0L)!!
    val inLondon = fluidReminderDue(time, null, london, null, 0L)!!
    assertEquals(millis("2026-12-03T08:00", rome), inRome)
    assertEquals(millis("2026-12-03T08:00", london), inLondon)
    assertEquals("un'ora dopo sull'orologio di parete", 60 * 60 * 1000L, inLondon - inRome)
  }

  @Test
  fun unIstanteResta_loStessoIstante() {
    val at = millis("2026-12-03T08:00", rome)
    val time = FluidReminderTime.at(at)
    assertEquals(at, fluidReminderDue(time, null, tokyo, null, 0L))
    assertEquals(at, fluidReminderDue(time, null, london, null, 0L))
  }

  @Test
  fun lOraLegale_lOraCheMancaVaAvanti() {
    // Il 28 marzo 2027 in Italia le 2:30 non esistono: il promemoria suona alle 3:30, come `ZonedDateTime.of`.
    val time = FluidReminderTime.local(LocalDateTime.parse("2027-03-28T02:30"))
    assertEquals(millis("2027-03-28T03:30", rome), fluidReminderDue(time, null, rome, null, 0L))
  }

  @Test
  fun unoConsegnato_nonSuonaPiu() {
    val time = FluidReminderTime.local(LocalDateTime.parse("2026-12-03T08:00"))
    assertNull(fluidReminderDue(time, null, rome, lastScheduledMillis = millis("2026-12-03T08:00"), nowMillis = 0L))
  }

  @Test
  fun laRipetizione_ilProssimoGiorno() {
    val time = FluidReminderTime.local(LocalDateTime.parse("2026-12-01T08:00"))
    val daily = FluidRecurrenceRule(FluidRecurrenceFrequency.Daily)
    val due = fluidReminderDue(time, daily, rome, millis("2026-12-02T08:00"), millis("2026-12-03T07:00"))
    assertEquals(millis("2026-12-03T08:00"), due)
  }

  @Test
  fun laRipetizione_spentoTreGiorni_soloLaPiuRecente() {
    val time = FluidReminderTime.local(LocalDateTime.parse("2026-12-01T08:00"))
    val daily = FluidRecurrenceRule(FluidRecurrenceFrequency.Daily)
    val due = fluidReminderDue(time, daily, rome, millis("2026-12-01T08:00"), millis("2026-12-04T12:00"))
    assertEquals("si consegna subito quella di oggi, non le tre perse", millis("2026-12-04T08:00"), due)
  }

  @Test
  fun laRipetizioneFinita_nonSuonaPiu() {
    val time = FluidReminderTime.local(LocalDateTime.parse("2026-12-01T08:00"))
    val twice = FluidRecurrenceRule(FluidRecurrenceFrequency.Daily, end = FluidRecurrenceEnd.AfterCount(2))
    assertNull(fluidReminderDue(time, twice, rome, millis("2026-12-02T08:00"), millis("2026-12-02T09:00")))
  }

  @Test
  fun laRipetizione_dopoUnCambioDiFuso_restaAlleOtto() {
    val time = FluidReminderTime.local(LocalDateTime.parse("2026-12-01T08:00"))
    val daily = FluidRecurrenceRule(FluidRecurrenceFrequency.Daily)
    val due = fluidReminderDue(time, daily, tokyo, millis("2026-12-02T08:00", rome), millis("2026-12-02T12:00", rome))
    assertEquals(millis("2026-12-03T08:00", tokyo), due)
  }

  @Test
  fun ilPercheDelRitardo() {
    val l = FluidNotifyLabels()
    assertNull(fluidReminderLateReason(0, 30_000, afterRestore = true, exact = true, labels = l))
    assertEquals(l.lateDeviceOff, fluidReminderLateReason(0, 600_000, afterRestore = true, exact = true, labels = l))
    assertEquals(l.lateInexact, fluidReminderLateReason(0, 600_000, afterRestore = false, exact = false, labels = l))
    assertEquals(l.lateSystem, fluidReminderLateReason(0, 600_000, afterRestore = false, exact = true, labels = l))
  }

  // --- il riepilogo -----------------------------------------------------------------------------------------------------

  @Test
  fun ilProssimoRiepilogo() {
    val times = listOf(18 * 60, 8 * 60)
    assertEquals(millis("2026-12-03T18:00"), fluidDigestNext(times, millis("2026-12-03T10:00"), rome))
    assertEquals(millis("2026-12-04T08:00"), fluidDigestNext(times, millis("2026-12-03T19:00"), rome))
    assertEquals("alle 18:00 in punto si passa al dopo", millis("2026-12-04T08:00"), fluidDigestNext(times, millis("2026-12-03T18:00"), rome))
  }

  // --- la memoria -------------------------------------------------------------------------------------------------------

  @Test
  fun laMemoria_andataERitorno() {
    val memory = FluidNotifyMemory(
      reminders = listOf(
        FluidStoredReminder(
          "verifica", FluidReminderTime.local(LocalDateTime.parse("2026-12-03T20:00")),
          FluidReminderContent("Domani: verifica di storia", "Capitoli 4 e 5", actions = listOf(FluidNotifyAction("Fatto", "fatto"))),
          repeat = FluidRecurrenceRule(FluidRecurrenceFrequency.Weekly, end = FluidRecurrenceEnd.AfterCount(3)),
          exact = true, alarm = false, lastScheduledMillis = 5L, nextMillis = 9L, armedExact = true,
        ),
        FluidStoredReminder("pasta", FluidReminderTime.at(1234L), FluidReminderContent("Pasta"), null, exact = false, alarm = true),
      ),
      deliveries = listOf(FluidReminderDelivery("verifica", "Domani: verifica di storia", 1L, 70_001L, "Il telefono era spento")),
      digestEnabled = true,
      digestTimes = listOf(7 * 60, 21 * 60),
      digestChannels = setOf("circolari"),
      digestQueue = listOf(
        FluidNotifyMessage(
          "c1", "circolari", "Circolare 42", "Uscita anticipata", thread = "circ",
          reply = FluidNotifyReply(choices = listOf("Ok")), person = "Segreteria", timeMillis = 77L, urgent = false,
        ),
      ),
    )
    val back = FluidNotifyCodec.decode(FluidNotifyCodec.encode(memory))
    assertEquals(memory, back)
  }

  @Test
  fun laMemoriaRotta_nonRompeNiente() {
    val store = FluidNotifyStore(null)
    assertEquals(FluidNotifyMemory(), store.read())
    val partial = FluidNotifyCodec.decode("""{"v":9,"reminders":[{"id":"x"}],"digestEnabled":true,"futuro":1}""")
    assertTrue("un promemoria illeggibile si salta", partial.reminders.isEmpty())
    assertTrue(partial.digestEnabled)
  }

  // --- i controlli di «Le notifiche non arrivano?» ---------------------------------------------------------------------

  private fun inputs(
    allowed: Boolean = true,
    bucket: Int? = 10,
    restricted: Boolean = false,
    samsung: Boolean = true,
    exact: Boolean = true,
    sdk: Int = 36,
    focus: Boolean = false,
    channelOn: Boolean = true,
    live: FluidNotifyLiveState = FluidNotifyLiveState.Allowed,
  ) = FluidNotifyHealthInputs(
    sdk = sdk, notificationsAllowed = allowed,
    channels = listOf(FluidNotifyChannel("voti", "Voti nuovi", "Quando arriva un voto") to channelOn),
    exactAllowed = exact, hasReminders = true, standbyBucket = bucket, backgroundRestricted = restricted,
    samsung = samsung, focusActive = focus, live = live,
  )

  @Test
  fun tuttoAPosto() {
    val health = fluidNotifyHealth(inputs(), FluidNotifyLabels())
    assertTrue(health.allGood)
    assertFalse(health.suspended)
    assertEquals(listOf("permission", "suspended", "exact", "focus", "live"), health.checks.map { it.id })
  }

  @Test
  fun oneUiHaMessoLAppADormire() {
    val labels = FluidNotifyLabels()
    val rare = fluidNotifyHealth(inputs(bucket = 40), labels)
    assertTrue(rare.suspended)
    assertEquals("prima ciò che non va", "suspended", rare.checks.first().id)
    assertEquals(labels.checkSuspendedSamsung, rare.checks.first().detail)
    assertEquals(FluidNotifyFix.Battery, rare.checks.first().fix)
    val restricted = fluidNotifyHealth(inputs(restricted = true, samsung = false), labels)
    assertTrue(restricted.suspended)
    assertEquals(labels.checkSuspendedOther, restricted.checks.first().detail)
  }

  @Test
  fun ciòCheNonVa_vienePrima_conLaSuaCorrezione() {
    val health = fluidNotifyHealth(inputs(exact = false, focus = true, channelOn = false, live = FluidNotifyLiveState.Off), FluidNotifyLabels())
    assertEquals(listOf("channel:voti", "exact", "focus", "live", "permission", "suspended"), health.checks.map { it.id })
    assertEquals(FluidNotifyFix.channel("voti"), health.checks[0].fix)
    assertEquals(FluidNotifyFix.ExactAlarms, health.checks[1].fix)
    assertEquals(FluidNotifyFix.Focus, health.checks[2].fix)
    assertEquals(FluidNotifyFix.LiveUpdates, health.checks[3].fix)
  }

  @Test
  fun permessoNegato_iCanaliNonContano() {
    val health = fluidNotifyHealth(inputs(allowed = false, channelOn = false), FluidNotifyLabels())
    assertEquals("permission", health.checks.first().id)
    assertEquals(FluidNotifyFix.AppNotifications, health.checks.first().fix)
    assertTrue(health.checks.none { it.id.startsWith("channel:") })
  }

  @Test
  fun primaDelDodici_nienteAllarmiEsatti_primaDelSedici_nienteLiveUpdate() {
    val health = fluidNotifyHealth(inputs(sdk = 30, live = FluidNotifyLiveState.NotSupported), FluidNotifyLabels())
    assertTrue(health.checks.none { it.id == "exact" || it.id == "live" })
  }
}
