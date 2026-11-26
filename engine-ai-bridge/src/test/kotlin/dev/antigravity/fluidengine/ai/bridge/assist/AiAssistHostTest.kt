package dev.antigravity.fluidengine.ai.bridge.assist

import dev.antigravity.fluidengine.ai.bridge.ReadyState
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F9.1 · L'ospite dell'assistenza, senza Android e col tempo virtuale: chi passa e chi no, la pagina col consenso, i
 * pezzi a parole intere ogni 50 ms, l'annullamento, i tempi massimi, le misure, e niente chiavi verso l'app.
 */
class AiAssistHostTest {

  private val words = "La media scende perché il cinque pesa quanto la verifica di ottobre. "

  private fun streaming(text: String = words, stepMillis: Long = 10L) = ScriptedExecutor { _, sink ->
    sink.status("Controllo i voti", Apps.School)
    text.split(Regex("(?<= )")).forEach { delay(stepMillis); sink.text(it) }
  }

  @Test fun `la risposta arriva a pezzi, a parole intere, al massimo ogni 50 ms, e finisce col testo intero`() = runTest {
    val executor = streaming()
    val host = host(executor)
    val listener = RecordingListener({ testScheduler.currentTime })
    val outcome = host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("Perché scende?"))), listener)
    assertNotNull(outcome.requestId)
    advanceUntilIdle()

    val progress = listener.progress()
    assertTrue(progress.done)
    assertEquals(words, progress.text)
    assertNull("la fine toglie la riga di stato", progress.status)
    // I pezzi di testo sono parole intere: ognuno finisce con uno spazio.
    listener.events.filter { it.type == AiAssistProtocol.TYPE_TEXT }.forEach { assertTrue("«${it.delta}»", it.delta!!.endsWith(" ")) }
    // Fra due invii almeno 50 ms, tranne la fine che parte subito.
    val times = listener.batches.map { it.first }
    times.zipWithNext().dropLast(1).forEach { (a, b) -> assertTrue("invii a $a e $b", b - a >= 50) }
    assertTrue("12 parole ogni 10 ms in pochi invii, non uno per parola", listener.batches.size <= 5)
    // I numeri non hanno buchi.
    assertEquals((1L..listener.events.size).toList(), listener.events.map { it.seq })
    assertTrue("il canale si chiude a fine domanda", listener.closed)
    assertEquals(1, executor.finished)
  }

  @Test fun `la riga di stato porta l'app dei tool, le ricevute la loro`() = runTest {
    val host = host(ScriptedExecutor { _, sink ->
      sink.status("Controllo il meteo", "dev.pampa.fluidweather")
      sink.receipt(AiAssistReceipt("Promemoria aggiunto", "dev.pampa.pampanotes"))
      sink.receipt(AiAssistReceipt("Evento forse creato", outcome = "chissa"))
      delay(100)
      sink.text("Fatto. ")
    })
    val listener = RecordingListener({ testScheduler.currentTime })
    host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("ok"))), listener)
    runCurrent()
    assertEquals(AiAssistStatusLine("Controllo il meteo", "dev.pampa.fluidweather"), listener.progress().status)
    advanceUntilIdle()
    val p = listener.progress()
    assertEquals(listOf("dev.pampa.pampanotes", null), p.receipts.map { it.app })
    assertEquals("un esito sconosciuto e' «da verificare», mai «fatto»", AiAssistReceipt.OutcomeUncertain, p.receipts[1].outcome)
  }

  @Test fun `un'app fuori tabella o bloccata riceve not_authorized, e l'esecutore non parte`() = runTest {
    val executor = streaming()
    val consents = allowedConsents()
    val host = host(executor, consents)
    assertEquals(AiAssistError.NotAuthorized, host.ask(Apps.stranger, askJson(AiAssistRequest(AiAssistTask.answer("ciao"))), null).response.errorCode())
    assertEquals(AiAssistError.NotAuthorized, host.scope(Apps.stranger).errorCode())
    host.setBlocked(Apps.School, true)
    assertEquals(AiAssistError.NotAuthorized, host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("ciao"))), null).response.errorCode())
    advanceUntilIdle()
    assertTrue(executor.calls.isEmpty())
    host.setBlocked(Apps.School, false)
    assertNull(host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("ciao"))), null).response.errorCode())
  }

  @Test fun `l'ambito - un compito non concesso e' not_authorized, uno sconosciuto needs_update`() = runTest {
    val executor = streaming()
    val host = host(executor)
    val weather = { task: AiAssistTask -> host.ask(Apps.weather, askJson(AiAssistRequest(task)), null).response.errorCode() }
    assertEquals(AiAssistError.NotAuthorized, weather(AiAssistTask.rewrite("testo", AiAssistTask.StyleFriendly)))
    assertEquals(AiAssistError.NotAuthorized, weather(AiAssistTask.summarize("testo")))
    assertEquals(AiAssistError.NeedsUpdate, weather(AiAssistTask("traduci", "testo")))
    assertEquals(AiAssistError.NeedsUpdate, host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.rewrite("t", "poetico"))), null).response.errorCode())
    assertEquals(AiAssistError.NotAuthorized, host.ask(Apps.weather, askJson(AiAssistRequest(AiAssistTask.answer("q"), page = AiAssistPage("Roma"))), null).response.errorCode())
    advanceUntilIdle()
    assertTrue(executor.calls.isEmpty())
    val scope = AiAssistProtocol.statusOf(host.scope(Apps.weather), Apps.Weather)
    assertEquals(setOf(AiAssistTask.KindAnswer), scope.tasks)
    assertTrue(scope.tools)
    assertFalse(scope.page)
  }

  @Test fun `riscrivere e riassumere non usano mai tool, senza l'ambito tools neanche rispondere`() = runTest {
    val executor = ScriptedExecutor { _, sink -> sink.text("ok ") }
    val host = host(executor)
    host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.rewrite("ciao prof", AiAssistTask.StyleProfessional))), null)
    host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("voti?"))), null)
    advanceUntilIdle()
    assertEquals(listOf(AiAssistRequest.ToolsNone, AiAssistRequest.ToolsCaller), executor.calls.map { it.tools })
    assertEquals("la scuola espone i suoi tool: l'esecutore li riceve dal sistema", "${Apps.School}.ai.tools", executor.calls[1].caller.toolsAuthority)
  }

  @Test fun `la pagina senza il sì - consent_required, e all'esecutore non arriva niente`() = runTest {
    val executor = streaming()
    val consents = allowedConsents()
    val host = host(executor, consents)
    val page = AiAssistPage("Matematica · verifica", listOf("Voto 5½"))
    val ask = askJson(AiAssistRequest(AiAssistTask.answer("Perché?"), page = page))
    assertEquals(AiAssistError.ConsentRequired, host.ask(Apps.school, ask, null).response.errorCode())
    consents.setPageConsent(Apps.School, AiAssistConsentState.Denied)
    assertEquals(AiAssistError.ConsentRequired, host.ask(Apps.school, ask, null).response.errorCode())
    advanceUntilIdle()
    assertTrue("niente pagina e niente domanda prima del sì", executor.calls.isEmpty())

    host.setPageConsent(Apps.School, AiAssistConsentState.Allowed)
    host.ask(Apps.school, ask, null)
    advanceUntilIdle()
    assertEquals(page, executor.calls.single().page)
  }

  @Test fun `la revoca ferma la domanda in corso con la pagina, e quella dopo riceve consent_required`() = runTest {
    val executor = ScriptedExecutor { _, sink -> sink.text("Allora "); awaitCancellation() }
    val consents = allowedConsents().apply { setPageConsent(Apps.School, AiAssistConsentState.Allowed) }
    val host = host(executor, consents)
    val withPage = RecordingListener({ testScheduler.currentTime })
    val withoutPage = RecordingListener({ testScheduler.currentTime })
    host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("a"), page = AiAssistPage("Voti"))), withPage)
    host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("b"))), withoutPage)
    advanceTimeBy(200)
    host.setPageConsent(Apps.School, AiAssistConsentState.Denied)
    advanceTimeBy(200)
    assertEquals(AiAssistError.ConsentRequired, withPage.progress().error?.code)
    assertFalse("la domanda senza pagina continua", withoutPage.progress().finished)
    assertEquals(1, executor.cancelled)
    assertEquals(AiAssistError.ConsentRequired, host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("c"), page = AiAssistPage("Voti"))), null).response.errorCode())
    // Bloccare l'app ferma anche l'altra.
    host.setBlocked(Apps.School, true)
    advanceTimeBy(200)
    assertEquals(AiAssistError.NotAuthorized, withoutPage.progress().error?.code)
    assertEquals(0, host.inFlight(Apps.School))
  }

  @Test fun `annullare ferma l'esecutore e chiude con cancelled, un'altra app non puo' annullare`() = runTest {
    val executor = ScriptedExecutor { _, sink -> repeat(1000) { delay(10); sink.text("parola ") } }
    val host = host(executor)
    val listener = RecordingListener({ testScheduler.currentTime })
    val id = host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("lunga"))), listener).requestId!!
    advanceTimeBy(300)
    assertFalse(AiAssistProtocol.cancelRequest(id).let { host.cancel(Apps.weather, it) }["cancelled"].toString().toBoolean())
    assertTrue(host.cancel(Apps.school, AiAssistProtocol.cancelRequest(id))["cancelled"].toString().toBoolean())
    advanceUntilIdle()
    val p = listener.progress()
    assertEquals(AiAssistError.Cancelled, p.error?.code)
    assertTrue("il testo di prima resta", p.text.startsWith("parola "))
    assertEquals(1, executor.cancelled)
    assertEquals(0, executor.finished)
  }

  @Test fun `il tempo massimo chiude con timeout all'ora giusta, anche con un esecutore che non si ferma`() = runTest {
    val executor = ScriptedExecutor { _, _ -> withContext(NonCancellable) { delay(600_000) } }
    val host = host(executor)
    val listener = RecordingListener({ testScheduler.currentTime })
    host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("lenta"), timeoutMillis = 3_000)), listener)
    advanceTimeBy(2_999)
    assertFalse(listener.progress().finished)
    advanceTimeBy(2)
    runCurrent()
    assertEquals(AiAssistError.Timeout, listener.progress().error?.code)
    assertEquals("la fine parte all'ora, non quando l'esecutore si decide", 3_000L, listener.batches.last().first)
    assertEquals(0, host.inFlight(Apps.School))
  }

  @Test fun `il tempo chiesto dall'app sta fra 1 e 120 secondi`() = runTest {
    val executor = ScriptedExecutor { _, _ -> awaitCancellation() }
    val host = host(executor)
    val long = RecordingListener({ testScheduler.currentTime })
    val short = RecordingListener({ testScheduler.currentTime })
    host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("a"), timeoutMillis = 3_600_000)), long)
    host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("b"), timeoutMillis = 1)), short)
    advanceUntilIdle()
    assertEquals(1_000L, short.batches.last().first)
    assertEquals(120_000L, long.batches.last().first)
    assertEquals(listOf(120_000L, 1_000L), executor.calls.map { it.timeoutMillis })
  }

  @Test fun `se l'app muore (il canale non risponde) la domanda si ferma`() = runTest {
    val executor = ScriptedExecutor { _, sink -> repeat(1000) { delay(10); sink.text("parola ") } }
    val host = host(executor)
    val listener = RecordingListener({ testScheduler.currentTime })
    val id = host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("a"))), listener).requestId!!
    advanceTimeBy(200)
    listener.alive = false
    advanceTimeBy(200)
    assertEquals(1, executor.cancelled)
    assertEquals(0, host.inFlight(Apps.School))
    // E la morte annunciata dal sistema (linkToDeath) fa lo stesso.
    val other = RecordingListener({ testScheduler.currentTime })
    val second = host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("b"))), other).requestId!!
    advanceTimeBy(100)
    host.callerGone(second)
    advanceTimeBy(100)
    assertEquals(2, executor.cancelled)
    assertTrue(id != second)
  }

  @Test fun `le misure - troppe insieme busy, troppe al minuto limit_reached, testo troppo lungo too_large`() = runTest {
    val executor = ScriptedExecutor { _, _ -> delay(1_000) }
    val host = host(executor, limits = AiAssistLimits(maxPerApp = 2, maxTotal = 3, startsPerMinute = 4))
    val ask = { caller: AiAssistCaller -> host.ask(caller, askJson(AiAssistRequest(AiAssistTask.answer("q"))), null).response }
    assertNull(ask(Apps.school).errorCode())
    assertNull(ask(Apps.school).errorCode())
    assertEquals(AiAssistError.Busy, ask(Apps.school).errorCode())
    assertNull(ask(Apps.weather).errorCode())
    assertEquals("quattro in tutto: la terza dell'altra app non entra", AiAssistError.Busy, ask(Apps.weather).errorCode())
    advanceTimeBy(1_500)
    assertNull(ask(Apps.school).errorCode())
    assertNull(ask(Apps.school).errorCode())
    advanceTimeBy(1_500)
    val limited = AiAssistProtocol.errorOf(ask(Apps.school))!!
    assertEquals(AiAssistError.LimitReached, limited.code)
    assertEquals("la prima domanda del minuto esce fra 57 s", 57_000L, limited.retryAfterMillis)
    advanceTimeBy(57_000)
    assertNull(ask(Apps.school).errorCode())
    advanceUntilIdle()
    val big = "a".repeat(24_001)
    assertEquals(AiAssistError.TooLarge, host.ask(Apps.weather, askJson(AiAssistRequest(AiAssistTask.answer(big))), null).response.errorCode())
    assertEquals(AiAssistError.Failed, host.ask(Apps.weather, askJson(AiAssistRequest(AiAssistTask.answer("   "))), null).response.errorCode())
  }

  @Test fun `PampAI non pronta - not_ready con la sua riga, ripulita`() = runTest {
    val executor = streaming()
    val host = host(executor, ready = { ReadyState(false, "aggiungi una chiave (gsk_abcdefghijklmnopqrstuvwx non vale)") })
    val error = AiAssistProtocol.errorOf(host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("q"))), null).response)!!
    assertEquals(AiAssistError.NotReady, error.code)
    assertFalse(error.reason!!.contains("gsk_"))
    val status = AiAssistProtocol.statusOf(host.scope(Apps.school), Apps.School)
    assertFalse(status.ready)
    assertEquals(AiAssistError.NotReady, status.error?.code)
  }

  @Test fun `il limite del fornitore passa con l'attesa, un'eccezione qualunque passa senza il suo messaggio`() = runTest {
    val host = host(ScriptedExecutor { call, _ ->
      if (call.task.text == "limite") throw AiAssistException(AiAssistError(AiAssistError.LimitReached, retryAfterMillis = 90_000))
      throw IllegalStateException("401 da Groq: chiave gsk_ABCDEFGHIJKLMNOPQRSTUVWX1234 non valida per llama-3.1-8b-instant")
    })
    val limit = RecordingListener({ testScheduler.currentTime })
    val crash = RecordingListener({ testScheduler.currentTime })
    host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("limite"))), limit)
    host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("altro"))), crash)
    advanceUntilIdle()
    assertEquals(AiAssistError(AiAssistError.LimitReached, 90_000), limit.progress().error)
    assertEquals(AiAssistError(AiAssistError.Failed), crash.progress().error)
    val wire = crash.batches.joinToString { AiAssistProtocol.batch(it.second.requestId, it.second.events) }
    Leaks.forEach { assertFalse("«$it» e' arrivato all'app: $wire", wire.contains(it, ignoreCase = true)) }
  }

  @Test fun `nessuna chiave verso l'app, nemmeno spezzata fra due pezzi o in una riga di stato`() = runTest {
    val key = "gsk_" + "Q".repeat(40)
    val host = host(ScriptedExecutor { _, sink ->
      sink.status("Leggo AIzaSyA1234567890abcdefghijklmnopqrst")
      sink.text("La tua chiave è ${key.take(10)}")
      delay(60)
      sink.text("${key.drop(10)} e finisce qui. ")
      sink.receipt(AiAssistReceipt("Salvata sk-or-v1-0123456789abcdef0123456789abcdef"))
    })
    val listener = RecordingListener({ testScheduler.currentTime })
    host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("q"))), listener)
    advanceUntilIdle()
    val wire = listener.batches.joinToString { AiAssistProtocol.batch(it.second.requestId, it.second.events) }
    listOf("gsk_", "AIza", "sk-or-", "QQQQ").forEach { assertFalse("«$it» e' arrivato all'app: $wire", wire.contains(it)) }
    assertTrue(listener.progress().text.contains(AiAssistRedaction.Hidden))
    // Il formato non ha campi per fornitore, modello o chiave.
    listener.events.flatMap { it.toJson().keys }.toSet().forEach { assertTrue(it, it in setOf("seq", "type", "line", "app", "delta", "outcome", "text", "code", "retryAfterMillis", "reason")) }
  }

  @Test fun `una risposta oltre il tetto si chiude con quello che c'e'`() = runTest {
    val executor = ScriptedExecutor { _, sink -> repeat(10_000) { delay(1); sink.text("abcdefghi ") } }
    val host = host(executor, limits = AiAssistLimits(maxOutputChars = 1_000))
    val listener = RecordingListener({ testScheduler.currentTime })
    host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("q"))), listener)
    advanceUntilIdle()
    val p = listener.progress()
    assertTrue(p.done)
    assertTrue(p.text.length <= 1_000)
    assertEquals(1, executor.cancelled)
  }

  @Test fun `assist_poll - gli eventi dopo after, e solo all'app che ha chiesto`() = runTest {
    val host = host(streaming())
    val id = host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("q"))), null).requestId!!
    advanceUntilIdle()
    val all = host.poll(Apps.school, AiAssistProtocol.pollRequest(id, 0))
    val events = AiAssistProtocol.eventsOf(all["events"] as kotlinx.serialization.json.JsonArray)
    assertEquals("true", all["finished"].toString())
    assertTrue(events.last().terminal)
    val after = AiAssistProtocol.eventsOf(host.poll(Apps.school, AiAssistProtocol.pollRequest(id, 2))["events"] as kotlinx.serialization.json.JsonArray)
    assertEquals(events.drop(2), after)
    assertEquals(AiAssistError.Interrupted, host.poll(Apps.weather, AiAssistProtocol.pollRequest(id, 0)).errorCode())
    // Una domanda finita si dimentica dopo un minuto.
    advanceTimeBy(61_000)
    assertEquals(AiAssistError.Interrupted, host.poll(Apps.school, AiAssistProtocol.pollRequest(id, 0)).errorCode())
  }
}
