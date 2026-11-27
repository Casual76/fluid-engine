package dev.antigravity.fluidengine.ai.bridge.assist

import dev.antigravity.fluidengine.ai.bridge.ReadyState
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R3-S1 · I difetti 2 e 7 della review R3 (`sol-ai.md`): cio' che il ponte dell'assistenza lascia uscire verso l'app che
 * chiama o verso la cronologia, e la domanda che passa dopo la revoca del consenso.
 *
 * Un gettone si cerca a pezzi: un valore sintetico, scritto qui sotto, esce se ne esce **un tratto qualunque di 8
 * caratteri** ([leak]). I valori sono lettere e cifre a caso, senza parole: niente nel testo intorno ne' un id li compone
 * per caso, e anche un pezzo di gettone (la meta' prima di un taglio) conta come uscito.
 */
class AiAssistR3Test {

  /** Il primo tratto di 8 caratteri di [value] che compare in [out], o null. */
  private fun leak(out: String, value: String): String? = value.windowed(8).firstOrNull { it in out }

  /** Un testo con un segreto dentro: [secret] e' il valore che non deve uscire. */
  private class Sample(val name: String, val text: String, val secret: String)

  private val token = "Zq8mT3vXk2Lp9Rw4Nc7Hs1Jd"
  private val samples = listOf(
    Sample("Authorization: Bearer", "Il servizio ha risposto: Authorization: Bearer $token e poi basta. ", token),
    Sample("bearer a capo", "Usa il bearer\n$token per entrare. ", token),
    Sample("api_key con gli spazi", "La configurazione dice api_key = Wd4Kq9Zx2Mv7Lp3T fine. ", "Wd4Kq9Zx2Mv7Lp3T"),
    Sample("x-goog-api-key", "Header x-goog-api-key: AIza${""}SyQ4m2Zr8Kx7Vn3Lp9Wd5Tb1Hc6Jf0Gs2Ye ok. ", "Q4m2Zr8Kx7Vn3Lp9Wd5Tb1Hc6Jf0Gs2Ye"),
    Sample("Authorization: Basic", "Serve Authorization: Basic cGFtcGE6c2VncmV0aXNzaW1vMjAyNg== per il server. ", "cGFtcGE6c2VncmV0aXNzaW1vMjAyNg"),
    Sample("gsk_ in una frase", "La chiave gsk_${""}R7tY2uI9oP4aS1dF6gH3jK8lZ5xC0vB2nM è sbagliata. ", "R7tY2uI9oP4aS1dF6gH3jK8lZ5xC0vB2nM"),
    Sample("JWT", "Il gettone eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ6cTh4In0.Qm9Xp2Lk7Rt4Vz1N scade. ", "eyJzdWIiOiJ6cTh4In0"),
  )

  /** Tutto il testo passato a pezzi nei punti [cuts], poi la fine. */
  private fun streamed(text: String, cuts: List<Int>, stream: AiAssistRedaction.Stream = AiAssistRedaction.Stream()): String {
    val out = StringBuilder()
    var from = 0
    for (cut in cuts + text.length) {
      out.append(stream.push(text.substring(from, cut)))
      from = cut
    }
    return out.append(stream.drain()).toString()
  }

  @Test fun `2 - il gettone spezzato in due pezzi, in ogni punto, non esce`() {
    for (s in samples) {
      assertNull("${s.name}: il testo intero e' gia' pulito", leak(AiAssistRedaction.scrub(s.text), s.secret))
      for (i in 1 until s.text.length) {
        val out = streamed(s.text, listOf(i))
        leak(out, s.secret)?.let { throw AssertionError("${s.name}, taglio a $i: «$it» e' uscito in «$out»") }
        assertEquals("${s.name}, taglio a $i: il resto del testo cambia", AiAssistRedaction.scrub(s.text), out)
      }
    }
  }

  @Test fun `2 - il gettone spezzato in tre pezzi, o un carattere alla volta, non esce`() {
    for (s in samples) {
      for (i in 1 until s.text.length) for (j in i + 1 until s.text.length) {
        val out = streamed(s.text, listOf(i, j))
        leak(out, s.secret)?.let { throw AssertionError("${s.name}, tagli a $i e $j: «$it» e' uscito in «$out»") }
      }
      val out = streamed(s.text, (1 until s.text.length).toList())
      leak(out, s.secret)?.let { throw AssertionError("${s.name}, un carattere alla volta: «$it» e' uscito in «$out»") }
      assertEquals(s.name, AiAssistRedaction.scrub(s.text), out)
    }
  }

  @Test fun `2 - una parola piu' lunga del tetto non spezza il gettone che ha dentro`() {
    // Un indirizzo lungo con la chiave in fondo, e un JWT lungo: tutti e due oltre il tetto della parola.
    val url = "https://servizio.example.org/percorso/molto/lungo/della/pagina?pagina=2&api_key=Wd4Kq9Zx2Mv7Lp3TbN6&x=1 fine. "
    val jwt = "eyJhbGciOiJIUzI1NiJ9." + "eyJ" + "Ab3Cd5Ef7Gh9Jk2Lm4Np6Qr8St0Uv".repeat(12) + ".Qm9Xp2Lk7Rt4Vz1N"
    val long = listOf(
      Sample("indirizzo", url, "Wd4Kq9Zx2Mv7Lp3TbN6") to 32,
      Sample("JWT lungo", "Il gettone $jwt scade. ", "Ab3Cd5Ef7Gh9Jk2Lm4Np6Qr8St0Uv") to 256,
    )
    for ((s, hold) in long) {
      for (i in 1 until s.text.length step 3) {
        val out = streamed(s.text, listOf(i), AiAssistRedaction.Stream(maxHold = hold))
        leak(out, s.secret)?.let { throw AssertionError("${s.name}, taglio a $i: «$it» e' uscito in «$out»") }
      }
      val out = streamed(s.text, (1 until s.text.length step 7).toList(), AiAssistRedaction.Stream(maxHold = hold))
      leak(out, s.secret)?.let { throw AssertionError("${s.name}, a pezzi di 7: «$it» e' uscito in «$out»") }
    }
    // Una parola lunga senza niente che somigli a una chiave esce intera, prima della fine.
    val plain = "x".repeat(600)
    val stream = AiAssistRedaction.Stream(maxHold = 32)
    val early = plain.chunked(10).joinToString("") { stream.push(it) }
    assertTrue("la parola lunga resta ferma: ${early.length}", early.length >= 600 - 32)
    assertEquals(plain, early + stream.drain())
  }

  @Test fun `2 - Bearer in un pezzo e il valore nel seguente - verso l'app non passa`() = runTest {
    val host = host(ScriptedExecutor { _, sink ->
      sink.text("Il servizio ha risposto Authorization: Bearer ")
      delay(60)
      sink.text("$token e basta. ")
    })
    val listener = RecordingListener({ testScheduler.currentTime })
    host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("q"))), listener)
    advanceUntilIdle()
    val wire = listener.batches.joinToString { AiAssistProtocol.batch(it.second.requestId, it.second.events) }
    leak(wire, token)?.let { throw AssertionError("«$it» e' arrivato all'app: $wire") }
    assertTrue(listener.progress().text.contains(AiAssistRedaction.Hidden))
  }

  @Test fun `2 - una chiave tagliata dalla fine (annullata o oltre il tetto) non esce a meta'`() = runTest {
    val key = "gsk_${""}Q7mZ2xW9kR4t"
    val cancelled = host(ScriptedExecutor { _, sink -> sink.text("La chiave è $key"); awaitCancellation() })
    val first = RecordingListener({ testScheduler.currentTime })
    val outcome = cancelled.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("q"))), first)
    runCurrent()
    cancelled.cancel(Apps.school, AiAssistProtocol.cancelRequest(outcome.requestId!!))
    advanceUntilIdle()
    val wire = first.batches.joinToString { AiAssistProtocol.batch(it.second.requestId, it.second.events) }
    leak(wire, key.drop(4))?.let { throw AssertionError("«$it» e' arrivato all'app: $wire") }

    val capped = host(ScriptedExecutor { _, sink -> sink.text("Ecco " + "parola ".repeat(20) + "gsk_${""}R7tY2uI9oP4aS1dF6gH3jK8lZ5xC0vB2nM fine. ") },
      limits = AiAssistLimits(maxOutputChars = 160))
    val second = RecordingListener({ testScheduler.currentTime })
    capped.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("q"))), second)
    advanceUntilIdle()
    assertTrue(second.progress().done)
    val cut = second.batches.joinToString { AiAssistProtocol.batch(it.second.requestId, it.second.events) }
    leak(cut, "R7tY2uI9oP4aS1dF6gH3jK8lZ5xC0vB2nM")?.let { throw AssertionError("«$it» e' arrivato all'app: $cut") }
  }

  @Test fun `2 - il nome del modello e del fornitore non passano nella riga di stato ne' nel motivo`() = runTest {
    val model = Regex("(?i)(?<![A-Za-z0-9])llama-3\\.1-8b-instant(?![A-Za-z0-9])")
    val provider = Regex("(?i)(?<![A-Za-z0-9])(?:openrouter|groq)(?![A-Za-z0-9])")
    val host = host(ScriptedExecutor { _, sink ->
      sink.status("Chiedo a llama-3.1-8b-instant su Groq")
      delay(60)
      throw AiAssistException(AiAssistError(AiAssistError.LimitReached, retryAfterMillis = 1_000, reason = "OpenRouter: crediti finiti"))
    })
    val listener = RecordingListener({ testScheduler.currentTime })
    host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("q"))), listener)
    advanceUntilIdle()
    val wire = listener.batches.joinToString { AiAssistProtocol.batch(it.second.requestId, it.second.events) }
    assertFalse("il modello e' arrivato all'app: $wire", model.containsMatchIn(wire))
    assertFalse("il fornitore e' arrivato all'app: $wire", provider.containsMatchIn(wire))
    // Lo stato di PampAI non pronta, con il fornitore nel motivo.
    val notReady = host(streamingNothing(), ready = { ReadyState(false, "Groq non risponde") })
    val error = AiAssistProtocol.errorOf(notReady.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("q"))), null).response)!!
    assertFalse("il fornitore e' nel motivo: ${error.reason}", provider.containsMatchIn(error.reason.orEmpty()))
  }

  @Test fun `2 - il titolo della pagina passa dalla pulizia prima della cronologia`() = runTest {
    val history = InMemoryAiAssistHistory()
    val consents = allowedConsents().apply { setPageConsent(Apps.School, AiAssistConsentState.Allowed) }
    val host = host(ScriptedExecutor { _, sink -> sink.text("Fatto. ") }, consents = consents, history = history)
    val page = AiAssistPage("Impostazioni · api_key=Wd4Kq9Zx2Mv7Lp3T", listOf("riga"))
    host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("Cosa vuol dire?"), page = page)), null)
    advanceUntilIdle()
    val title = history.entries().single().pageTitle.orEmpty()
    leak(title, "Wd4Kq9Zx2Mv7Lp3T")?.let { throw AssertionError("«$it» e' nella cronologia: $title") }
    assertTrue(title, title.startsWith("Impostazioni"))
  }

  // ── 7 · la revoca a meta' dell'ammissione ───────────────────────────────────────────────────────────────────

  @Test fun `7 - il si' alla pagina tolto mentre la domanda passa da ready - la pagina non arriva all'esecutore`() = runTest {
    val executor = ScriptedExecutor { _, sink -> sink.text("Allora ") }
    val consents = allowedConsents().apply { setPageConsent(Apps.School, AiAssistConsentState.Allowed) }
    var during: (() -> Unit)? = null
    // `ready` e' l'ultimo passo prima di registrare la domanda: l'utente revoca proprio li'.
    val host = host(executor, consents, ready = { during?.invoke(); during = null; ReadyState(true) })
    during = { host.setPageConsent(Apps.School, AiAssistConsentState.Denied) }
    val page = AiAssistPage("Matematica · verifica", listOf("Voto 5½ di Mario Rossi"))
    val withPage = host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("Perché?"), page = page)), null)
    advanceUntilIdle()
    assertTrue("la pagina e' arrivata all'esecutore dopo la revoca: ${executor.calls.map { it.page?.title }}", executor.calls.none { it.page != null })
    assertEquals(AiAssistError.ConsentRequired, withPage.response.errorCode())

    // Lo stesso con l'app bloccata a meta'.
    during = { host.setBlocked(Apps.School, true) }
    val blocked = host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("Perché?"))), null)
    advanceUntilIdle()
    assertTrue("la domanda dell'app bloccata e' partita", executor.calls.isEmpty())
    assertEquals(AiAssistError.NotAuthorized, blocked.response.errorCode())
  }

  // ── i sospetti del rapporto ───────────────────────────────────────────────────────────────────────────────

  @Test fun `sospetto 1 - PampAI che muore subito dopo l'ultimo invio - l'ultimo stato e' la risposta intera`() = runTest {
    val host = host(ScriptedExecutor { _, sink -> sink.text("Fatto e finito. ") })
    val connector = DirectConnector(host, Apps.school).apply { dieWithDone = true }
    val info = AiAssistHostInfo("dev.pampa.pampai", "dev.pampa.pampai.ai.assist", 10_123)
    val client = AiAssistClient(AiAssistClientConfig(), { AiAssistClient.Located.Found(info) }, connector, StandardTestDispatcher(testScheduler))
    val states = client.ask(AiAssistRequest(AiAssistTask.answer("q"))).toList()
    val last = states.last()
    assertTrue("l'ultimo stato non e' finito: $last", last.finished)
    assertTrue(last.done)
    assertEquals("Fatto e finito. ", last.text)
  }

  @Test fun `sospetto 2 - dopo una scrittura di un tool la domanda interrotta non offre Riprova`() = runTest {
    suspend fun interruptedAfter(receipt: AiAssistReceipt?): AiAssistProgress {
      val host = host(ScriptedExecutor { _, sink ->
        receipt?.let { sink.receipt(it) }
        delay(60)
        throw AiAssistException(AiAssistError(AiAssistError.Interrupted))
      })
      val listener = RecordingListener({ testScheduler.currentTime })
      host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("Aggiungi la verifica"))), listener)
      advanceUntilIdle()
      return listener.progress()
    }
    val wrote = interruptedAfter(AiAssistReceipt("Verifica aggiunta al calendario", Apps.School))
    assertEquals(AiAssistError.Interrupted, wrote.error?.code)
    assertTrue("il codice da solo resta riprovabile (F9.1d)", wrote.error!!.retryable)
    assertFalse("Riprova rifarebbe la scrittura", wrote.retryable)
    assertFalse(interruptedAfter(AiAssistReceipt("Evento forse creato", outcome = AiAssistReceipt.OutcomeUncertain)).retryable)
    assertTrue("una scrittura fallita si puo' riprovare", interruptedAfter(AiAssistReceipt("Evento non creato", outcome = AiAssistReceipt.OutcomeFailed)).retryable)
    assertTrue(interruptedAfter(null).retryable)
  }

  private fun streamingNothing() = ScriptedExecutor { _, _ -> }
}
