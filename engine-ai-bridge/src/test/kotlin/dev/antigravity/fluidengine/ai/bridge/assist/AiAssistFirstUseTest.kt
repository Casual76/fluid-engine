package dev.antigravity.fluidengine.ai.bridge.assist

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F9.1b · Le tre scelte di Alessio del 26/11/2026: la prima volta che un'app usa PampAI, PampAI chiede (un foglio solo
 * se c'e' anche la pagina; il «no» si ricorda); senza PampAI il tasto si vede spento con «Installa PampAI», ma a
 * un'app non autorizzata non si dice altro; le domande finiscono nella chat «Dalle app», senza la pagina intera.
 */
class AiAssistFirstUseTest {

  private fun answering() = ScriptedExecutor { _, sink ->
    sink.status("Controllo i voti", Apps.School)
    sink.receipt(AiAssistReceipt("Verifica aggiunta al calendario", Apps.School))
    listOf("La ", "media ", "scende. ").forEach { delay(10); sink.text(it) }
  }

  @Test fun `la prima volta la domanda chiede il sì per l'app, e con la pagina tutti e due insieme`() = runTest {
    val executor = answering()
    val host = host(executor, consents = InMemoryAiAssistConsentStore())
    val plain = AiAssistProtocol.errorOf(host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("Perché?"))), null).response)!!
    assertEquals(AiAssistError.ConsentRequired, plain.code)
    assertEquals(setOf(AiAssistError.NeedsApp), plain.needs)
    assertEquals("Per usare PampAI da quest'app serve il tuo sì", plain.message())
    val withPage = AiAssistProtocol.errorOf(host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("Perché?"), page = AiAssistPage("Voti"))), null).response)!!
    assertEquals(setOf(AiAssistError.NeedsApp, AiAssistError.NeedsPage), withPage.needs)
    advanceUntilIdle()
    assertTrue("prima del sì all'esecutore non arriva niente", executor.calls.isEmpty())
    // Lo stato dice «da chiedere», con l'ambito: l'app puo' mostrare il tasto acceso.
    val status = AiAssistProtocol.statusOf(host.scope(Apps.school), Apps.School)
    assertEquals(AiAssistConsentState.NotAsked, status.appConsent)
    assertTrue(status.ready)
    assertTrue(AiAssistEntry.of(status).enabled)
  }

  @Test fun `un foglio solo - Consenti, Senza la pagina, Non consentire`() = runTest {
    val host = host(answering(), consents = InMemoryAiAssistConsentStore())
    val consent = AiAssistConsent(host)
    val ask = consent.begin(Apps.School, withPage = true) as AiAssistConsent.Step.Ask
    assertTrue(ask.askApp && ask.askPage)
    assertEquals(listOf("Consenti", "Senza la pagina", "Non consentire"), ask.choices.map { it.label })
    assertEquals(
      "Scuola vuole usare PampAI e mandarle la pagina che hai davanti quando le chiedi qualcosa. Le domande e la pagina vanno al tuo fornitore di IA, con la tua chiave. Consenti?",
      ask.question,
    )
    // «Senza la pagina»: l'app sì, la pagina no.
    assertEquals(AiAssistConsent.Step.Decided(AiAssistConsentState.Allowed, AiAssistConsentState.Denied), consent.answer(ask, ask.choices[1]))
    assertEquals(AiAssistConsent.Step.Decided(AiAssistConsentState.Allowed, AiAssistConsentState.Denied), consent.begin(Apps.School, withPage = true))
    // Il meteo: solo l'app (non ha l'ambito della pagina), due tasti.
    val weather = consent.begin(Apps.Weather, withPage = true) as AiAssistConsent.Step.Ask
    assertTrue(weather.askApp)
    assertFalse(weather.askPage)
    assertEquals(listOf("Consenti", "Non consentire"), weather.choices.map { it.label })
    assertTrue(weather.question.startsWith("Meteo vuole usare PampAI: quando le chiedi qualcosa da Meteo"))
    assertEquals(AiAssistConsent.Step.Refused, consent.begin(Apps.Stranger))
  }

  @Test fun `il no si ricorda - nessuna domanda di nuovo, not_authorized, e si cambia dalle impostazioni`() = runTest {
    val executor = answering()
    val host = host(executor, consents = InMemoryAiAssistConsentStore())
    val consent = AiAssistConsent(host)
    val ask = consent.begin(Apps.School, withPage = true) as AiAssistConsent.Step.Ask
    val decided = consent.answer(ask, ask.choices.last())
    assertEquals(AiAssistConsentState.Denied, decided.app)
    assertEquals("la pagina resta da chiedere: non serve piu'", AiAssistConsentState.NotAsked, decided.page)
    assertEquals(AiAssistConsent.Step.Decided(AiAssistConsentState.Denied, AiAssistConsentState.NotAsked), consent.begin(Apps.School, withPage = true))
    assertEquals(AiAssistError.NotAuthorized, host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("q"))), null).response.errorCode())
    // A un'app rifiutata non si dice niente di PampAI: solo l'errore.
    assertEquals(setOf("error"), host.scope(Apps.school).keys)
    // «App che usano PampAI»: l'utente cambia idea.
    host.setAppConsent(Apps.School, AiAssistConsentState.Allowed)
    assertNull(host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("q"))), null).response.errorCode())
    advanceUntilIdle()
    assertEquals(1, executor.calls.size)
    // Dimenticare torna alla prima volta, e ferma le domande in corso.
    val slow = ScriptedExecutor { _, _ -> awaitCancellation() }
    val other = host(slow)
    val listener = RecordingListener({ testScheduler.currentTime })
    other.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("q"))), listener)
    advanceTimeBy(100)
    other.forget(Apps.School)
    advanceTimeBy(100)
    assertEquals(AiAssistError.ConsentRequired, listener.progress().error?.code)
    assertEquals(setOf(AiAssistError.NeedsApp), listener.progress().error?.needs)
  }

  @Test fun `il tasto - spento con Installa senza PampAI, con Aggiorna se vecchia, muto se non autorizzata`() {
    assertEquals(AiAssistEntry(enabled = false), AiAssistEntry.of(null))
    assertEquals(AiAssistEntry(enabled = true), AiAssistEntry.of(AiAssistStatus(ready = true)))
    val absent = AiAssistEntry.of(AiAssistStatus(false, error = AiAssistError(AiAssistError.NotInstalled)))
    assertEquals(AiAssistEntry(false, "PampAI non c'è", AiAssistEntry.ActionInstall, "Installa PampAI"), absent)
    val old = AiAssistEntry.of(AiAssistStatus(false, error = AiAssistError(AiAssistError.NeedsUpdate)))
    assertEquals(AiAssistEntry.ActionUpdate, old.action)
    assertEquals("Aggiorna PampAI", old.actionLabel)
    val blocked = AiAssistEntry.of(AiAssistStatus(false, error = AiAssistError(AiAssistError.NotAuthorized)))
    assertEquals("non autorizzata: niente «Installa», niente altro", AiAssistEntry(false, "PampAI non ha autorizzato quest'app"), blocked)
    val notReady = AiAssistEntry.of(AiAssistStatus(false, error = AiAssistError(AiAssistError.NotReady)))
    assertEquals(AiAssistEntry.ActionOpen, notReady.action)
    assertEquals("Install PampAI", AiAssistEntry.of(AiAssistStatus(false, error = AiAssistError(AiAssistError.NotInstalled)), "en").actionLabel)
    assertTrue(AiAssistTexts.installHint().contains("Pampa Store"))
  }

  @Test fun `Dalle app - l'app, la domanda, il titolo della pagina, la risposta e le ricevute, non le righe della pagina`() = runTest {
    val history = InMemoryAiAssistHistory()
    val consents = allowedConsents().apply { setPageConsent(Apps.School, AiAssistConsentState.Allowed) }
    val host = host(answering(), consents = consents, history = history)
    val page = AiAssistPage("Matematica · verifica", listOf("Voto 5½ di Mario Rossi", "Media 6,8"), selection = "testo selezionato riservato")
    host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("Perché questo voto abbassa la media?"), page = page)), null)
    advanceUntilIdle()
    val e = history.entries().single()
    assertEquals(Apps.School, e.app)
    assertEquals("Scuola", e.appName)
    assertEquals(AiAssistTask.KindAnswer, e.kind)
    assertEquals("Perché questo voto abbassa la media?", e.question)
    assertEquals("Matematica · verifica", e.pageTitle)
    assertEquals("La media scende. ", e.answer)
    assertEquals(listOf(AiAssistReceipt("Verifica aggiunta al calendario", Apps.School)), e.receipts)
    assertEquals(AiAssistHistoryEntry.OutcomeDone, e.outcome)
    assertTrue(e.atMillis >= 1_764_000_000_000L)
    val all = e.toString()
    listOf("Mario Rossi", "Media 6,8", "riservato").forEach { assertFalse("«$it» della pagina e' finito nella cronologia", all.contains(it)) }
  }

  @Test fun `Dalle app - il testo di partenza e il risultato interi, le domande fermate si, le rifiutate no`() = runTest {
    val history = InMemoryAiAssistHistory()
    val long = "Gentile professoressa, " + "domani sarò assente per una visita medica. ".repeat(300)
    val host = host(ScriptedExecutor { call, sink ->
      if (call.task.text.startsWith("lunga")) awaitCancellation()
      sink.text(long)
    }, history = history)
    val draft = "ciao prof domani non ci sono perche' ho la visita, la chiave e' gsk_" + "A".repeat(30) + " e poi\n" + "x".repeat(400)
    host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.rewrite(draft, AiAssistTask.StyleProfessional))), null)
    val id = host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("lunga domanda"))), null).requestId!!
    advanceTimeBy(200)
    host.cancel(Apps.school, AiAssistProtocol.cancelRequest(id))
    // Rifiutate prima di partire: non si salvano.
    host.ask(Apps.stranger, askJson(AiAssistRequest(AiAssistTask.answer("q"))), null)
    host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("q"), page = AiAssistPage("Voti"))), null)
    advanceUntilIdle()
    val entries = history.entries()
    assertEquals(2, entries.size)
    val rewrite = entries.first { it.kind == AiAssistTask.KindRewrite }
    assertEquals(AiAssistTask.StyleProfessional, rewrite.style)
    // F9.1c: il testo di partenza intero (a capo compresi), con la chiave tolta.
    assertEquals(AiAssistRedaction.scrub(draft), rewrite.question)
    assertTrue(rewrite.question.endsWith("x".repeat(400)))
    assertFalse("la chiave non finisce nella cronologia", rewrite.question.contains("gsk_"))
    assertEquals(draft.length, rewrite.inputChars)
    // E il risultato intero, anche oltre 8000 caratteri.
    assertTrue(long.length > 8_000)
    assertEquals(long, rewrite.answer)
    assertEquals(AiAssistError.Cancelled, entries.first { it.question == "lunga domanda" }.outcome)
  }

  @Test fun `Dalle app - resta finche' non la togli, una per una o tutte`() = runTest {
    val history = InMemoryAiAssistHistory()
    val host = host(answering(), history = history)
    repeat(3) { host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("domanda $it"))), null); advanceUntilIdle() }
    host.ask(Apps.weather, askJson(AiAssistRequest(AiAssistTask.answer("meteo"))), null)
    // Un anno dopo le domande ci sono ancora: niente si cancella da solo.
    advanceTimeBy(365L * 24 * 3_600_000)
    advanceUntilIdle()
    assertEquals(4, history.entries().size)
    val first = history.entries().first { it.question == "domanda 0" }
    assertTrue(history.remove(first.requestId))
    assertFalse(history.remove(first.requestId))
    assertEquals(listOf("meteo", "domanda 2", "domanda 1"), history.entries().map { it.question })
    history.forget(Apps.School)
    assertEquals(listOf("meteo"), history.entries().map { it.question })
    history.forget()
    assertTrue(history.entries().isEmpty())
  }

  @Test fun `il filo - needs negli errori e nella fine, e un ospite di F9_1 vale come sì`() = runTest {
    val error = AiAssistError(AiAssistError.ConsentRequired, needs = setOf(AiAssistError.NeedsPage, AiAssistError.NeedsApp))
    val json = AiAssistProtocol.errorResponse(error)
    assertEquals("""["app","page"]""", (json["needs"] as JsonArray).toString())
    assertEquals(error, AiAssistProtocol.errorOf(json))
    val event = AiAssistWireEvent(3, AiAssistProtocol.TYPE_FAILED, code = AiAssistError.ConsentRequired, needs = setOf(AiAssistError.NeedsApp))
    assertEquals(event, AiAssistWireEvent.of(event.toJson()))
    val assembler = AiAssistAssembler().apply { add(listOf(AiAssistWireEvent(1, AiAssistProtocol.TYPE_TEXT, delta = "a "), AiAssistWireEvent(2, AiAssistProtocol.TYPE_STATUS, line = "x"), event)) }
    assertEquals(setOf(AiAssistError.NeedsApp), assembler.progress.error?.needs)
    // Un ospite di F9.1 non manda «app»: si legge come sì gia' detto.
    val old = AiAssistProtocol.scopeResponse(setOf("answer"), true, true, AiAssistConsentState.Allowed, true, null)
    val oldJson = kotlinx.serialization.json.JsonObject(old - "app")
    assertEquals(AiAssistConsentState.Allowed, AiAssistProtocol.statusOf(oldJson, "p").appConsent)
  }
}
