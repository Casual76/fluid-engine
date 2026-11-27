package dev.antigravity.fluidengine.ai.bridge.assist

import dev.antigravity.fluidengine.ai.bridge.BridgeProtocol
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Un collegamento fra il cliente e l'ospite senza Binder: le stesse chiamate e lo stesso canale di ritorno, e la
 * possibilita' di perdere un invio, far tacere PampAI o farla morire. Ricorda tutto quello che passa ([wire]).
 */
internal class DirectConnector(
  private val host: AiAssistHost?,
  private val caller: AiAssistCaller,
  private val negotiateWith: (JsonObject?) -> JsonObject = { AiAssistProtocol.negotiate(it) },
) : AiAssistConnector {
  val wire = ArrayList<String>()
  val methods = ArrayList<String>()
  var dropBatches: Set<Int> = emptySet()
  var dieAfterBatches: Int? = null
  /** R3-S1: PampAI manda l'ultimo invio (quello con la fine) e muore, e l'app sente la morte prima dell'invio. */
  var dieWithDone = false
  var opened = 0
  var closed = 0

  override fun open(host: AiAssistHostInfo): AiAssistConnection {
    opened++
    return object : AiAssistConnection {
      var batches = 0

      override fun call(method: String, json: JsonObject, onBatch: ((String) -> Unit)?, onHostDied: (() -> Unit)?): AiAssistReply {
        methods += method
        wire += json.toString()
        val h = this@DirectConnector.host
        val response = when (method) {
          AiAssistProtocol.METHOD_NEGOTIATE -> negotiateWith(json)
          AiAssistProtocol.METHOD_SCOPE -> h?.scope(caller)
          AiAssistProtocol.METHOD_ASK -> if (h == null) AiAssistProtocol.askResponse("muta") else {
            val listener = onBatch?.let { send ->
              AiAssistListener { batch ->
                val index = batches++
                wire += batch
                dieAfterBatches?.let { if (index >= it) { onHostDied?.invoke(); return@AiAssistListener true } }
                if (dieWithDone && AiAssistProtocol.batchOf(batch)!!.events.any { it.type == AiAssistProtocol.TYPE_DONE }) {
                  onHostDied?.invoke()
                  send(batch)
                  return@AiAssistListener true
                }
                if (index !in dropBatches) send(batch)
                true
              }
            }
            h.ask(caller, json, listener).response
          }
          AiAssistProtocol.METHOD_POLL -> h?.poll(caller, json) ?: AiAssistProtocol.pollResponse(emptyList(), false)
          AiAssistProtocol.METHOD_CANCEL -> h?.cancel(caller, json) ?: AiAssistProtocol.cancelResponse(false)
          else -> BridgeProtocol.errorResponse("metodo sconosciuto: $method")
        }
        response?.let { wire += it.toString() }
        return AiAssistReply.Ok(response)
      }

      override fun close() { closed++ }
    }
  }
}

/** F9.1 · Il cliente delle app: PampAI assente, i pezzi, l'annullamento, i tempi, PampAI che muore, gli invii persi. */
class AiAssistClientTest {

  private val info = AiAssistHostInfo("dev.pampa.pampai", "dev.pampa.pampai.ai.assist", 10_123)

  private fun TestScope.client(connector: AiAssistConnector, located: AiAssistClient.Located = AiAssistClient.Located.Found(info), config: AiAssistClientConfig = AiAssistClientConfig()) =
    AiAssistClient(config, { located }, connector, StandardTestDispatcher(testScheduler))

  private fun words(n: Int, stepMillis: Long = 20L) = ScriptedExecutor { _, sink ->
    sink.status("Controllo i voti", Apps.School)
    repeat(n) { delay(stepMillis); sink.text("parola$it ") }
  }

  @Test fun `PampAI che non c'e', o da aggiornare, lo dice subito e non apre niente`() = runTest {
    val connector = DirectConnector(null, Apps.school)
    val absent = client(connector, AiAssistClient.Located.Missing(AiAssistError(AiAssistError.NotInstalled)))
    assertEquals(listOf(AiAssistProgress(error = AiAssistError(AiAssistError.NotInstalled))), absent.ask(AiAssistRequest(AiAssistTask.answer("q"))).toList())
    assertEquals(AiAssistError.NotInstalled, absent.check().error?.code)
    assertEquals(0, connector.opened)
    val old = client(connector, AiAssistClient.Located.Missing(AiAssistError(AiAssistError.NeedsUpdate)))
    assertEquals(AiAssistError.NeedsUpdate, old.run(AiAssistRequest(AiAssistTask.answer("q"))).error?.code)
    assertEquals("PampAI non c'è", AiAssistError(AiAssistError.NotInstalled).message())
    assertEquals("PampAI isn't installed", AiAssistError(AiAssistError.NotInstalled).message("en"))
  }

  @Test fun `i pezzi arrivano in ordine e l'ultimo stato e' la risposta intera`() = runTest {
    val host = host(words(30))
    val connector = DirectConnector(host, Apps.school)
    val states = client(connector).ask(AiAssistRequest(AiAssistTask.answer("q"))).toList()
    val last = states.last()
    assertTrue(last.done)
    assertEquals((0 until 30).joinToString("") { "parola$it " }, last.text)
    assertEquals(AiAssistProgress(), states.first())
    assertEquals("Controllo i voti", states[1].status?.line)
    // Il testo cresce soltanto.
    states.zipWithNext().forEach { (a, b) -> assertTrue(b.text.startsWith(a.text)) }
    assertTrue("pochi stati, non uno per parola: ${states.size}", states.size < 20)
    assertEquals("la connessione si chiude", connector.opened, connector.closed)
    assertFalse("una domanda finita non si annulla", AiAssistProtocol.METHOD_CANCEL in connector.methods)
  }

  @Test fun `anche con le domande ripetute (assist_poll) si arriva alla stessa risposta`() = runTest {
    val host = host(words(10))
    val connector = DirectConnector(host, Apps.school)
    val last = client(connector, config = AiAssistClientConfig(pollIntervalMillis = 50)).run(AiAssistRequest(AiAssistTask.answer("q")))
    assertTrue(last.done)
    assertEquals((0 until 10).joinToString("") { "parola$it " }, last.text)
    assertTrue(connector.methods.count { it == AiAssistProtocol.METHOD_POLL } >= 4)
  }

  @Test fun `cancellare chi raccoglie annulla la domanda in PampAI, che smette di lavorare`() = runTest {
    val executor = words(1_000)
    val host = host(executor)
    val connector = DirectConnector(host, Apps.school)
    val seen = ArrayList<AiAssistProgress>()
    val job = launch { client(connector).ask(AiAssistRequest(AiAssistTask.answer("q"))).collect { seen += it } }
    advanceTimeBy(500)
    job.cancel()
    advanceUntilIdle()
    assertTrue(AiAssistProtocol.METHOD_CANCEL in connector.methods)
    assertEquals(1, executor.cancelled)
    assertEquals(0, host.inFlight(Apps.School))
    assertTrue(seen.last().text.isNotEmpty())
    assertEquals(connector.opened, connector.closed)
  }

  @Test fun `PampAI che tace - l'app chiude da sola col tempo della domanda piu' la grazia, e annulla`() = runTest {
    val connector = DirectConnector(null, Apps.school)
    val started = testScheduler.currentTime
    val last = client(connector).run(AiAssistRequest(AiAssistTask.answer("q"), timeoutMillis = 3_000))
    assertEquals(AiAssistError.Timeout, last.error?.code)
    assertEquals(3_000L + 5_000L, testScheduler.currentTime - started)
    assertTrue(AiAssistProtocol.METHOD_CANCEL in connector.methods)
  }

  @Test fun `il tempo massimo dell'ospite arriva come timeout`() = runTest {
    val host = host(ScriptedExecutor { _, _ -> awaitCancellation() })
    val last = client(DirectConnector(host, Apps.school)).run(AiAssistRequest(AiAssistTask.answer("q"), timeoutMillis = 2_000))
    assertEquals(AiAssistError.Timeout, last.error?.code)
    assertEquals("PampAI non ha risposto in tempo", last.error?.message())
  }

  @Test fun `PampAI che muore a meta' - interrupted, col testo arrivato fin li'`() = runTest {
    val host = host(words(100))
    val connector = DirectConnector(host, Apps.school).apply { dieAfterBatches = 3 }
    val last = client(connector).run(AiAssistRequest(AiAssistTask.answer("q")))
    assertEquals(AiAssistError.Interrupted, last.error?.code)
    assertTrue(last.text.startsWith("parola0 "))
    assertEquals("PampAI si è fermata", last.error?.message())
  }

  @Test fun `un invio perso si recupera con assist_poll, anche l'ultimo`() = runTest {
    val host = host(words(20))
    val connector = DirectConnector(host, Apps.school).apply { dropBatches = setOf(2) }
    val last = client(connector).run(AiAssistRequest(AiAssistTask.answer("q")))
    assertTrue(last.done)
    assertEquals((0 until 20).joinToString("") { "parola$it " }, last.text)
    assertTrue(AiAssistProtocol.METHOD_POLL in connector.methods)

    val lostEnd = DirectConnector(host(words(3)), Apps.school).apply { dropBatches = (1..50).toSet() }
    val end = client(lostEnd).run(AiAssistRequest(AiAssistTask.answer("q")))
    assertTrue("la fine persa arriva con la domanda di controllo", end.done)
  }

  @Test fun `check dice cosa l'app puo' chiedere, e il sì per la pagina`() = runTest {
    val consents = allowedConsents()
    val host = host(words(1), consents)
    val status = client(DirectConnector(host, Apps.school)).check()
    assertTrue(status.ready)
    assertEquals(AiAssistTask.Kinds, status.tasks)
    assertTrue(status.page)
    assertEquals(AiAssistConsentState.NotAsked, status.pageConsent)
    host.setPageConsent(Apps.School, AiAssistConsentState.Allowed)
    assertEquals(AiAssistConsentState.Allowed, client(DirectConnector(host, Apps.school)).check().pageConsent)
    assertEquals(AiAssistError.NotAuthorized, client(DirectConnector(host, Apps.stranger)).check().error?.code)
  }

  @Test fun `un rifiuto arriva come errore, col suo codice`() = runTest {
    val host = host(words(1))
    val last = client(DirectConnector(host, Apps.school)).run(AiAssistRequest(AiAssistTask.answer("q"), page = AiAssistPage("Voti")))
    assertEquals(AiAssistError.ConsentRequired, last.error?.code)
    assertEquals("Per mandare la pagina serve il tuo sì in PampAI", last.error?.message())
  }

  @Test fun `un ospite dei tool (v1 e v2) chiamato per sbaglio - needs_update`() = runTest {
    // Il negotiate di un ospite dei tool non conosce la 3.
    val toolHost = DirectConnector(null, Apps.school) { BridgeProtocol.negotiate(it) }
    assertEquals(AiAssistError.NeedsUpdate, client(toolHost).run(AiAssistRequest(AiAssistTask.answer("q"))).error?.code)
    assertEquals(AiAssistError.NeedsUpdate, client(toolHost).check().error?.code)
    // Un ospite vecchio che non ha nemmeno negotiate.
    val ancient = DirectConnector(null, Apps.school) { BridgeProtocol.errorResponse("metodo sconosciuto: negotiate") }
    assertEquals(AiAssistError.NeedsUpdate, client(ancient).run(AiAssistRequest(AiAssistTask.answer("q"))).error?.code)
  }

  @Test fun `niente chiavi ne' modelli in tutto quello che passa verso l'app`() = runTest {
    val host = host(ScriptedExecutor { _, sink ->
      sink.status("Chiedo a llama-3.1-8b-instant con gsk_" + "Z".repeat(30))
      sink.text("Ecco: AIzaSyB" + "x".repeat(33) + " fine. ")
      throw IllegalStateException("OpenRouter 402: sk-or-v1-" + "a".repeat(40))
    })
    val connector = DirectConnector(host, Apps.school)
    val last = client(connector).run(AiAssistRequest(AiAssistTask.answer("q")))
    assertEquals(AiAssistError.Failed, last.error?.code)
    assertNull(last.error?.reason)
    val toApp = connector.wire.joinToString("\n")
    listOf("gsk_", "AIza", "sk-or-", "OpenRouter").forEach { assertFalse("«$it» e' arrivato all'app", toApp.contains(it)) }
    // Il codice 402 si cerca da solo: l'id della richiesta (un UUID) e gli orari possono avere le cifre 402 dentro un
    // numero più lungo, e la prova cadeva circa una volta su cento (lotto lg-unione-r3, 27/11).
    assertFalse("«402» e' arrivato all'app", Regex("(?<![0-9A-Za-z])402(?![0-9A-Za-z])").containsMatchIn(toApp))
  }

  @Test fun `la richiesta dell'app non porta altro che domanda, pagina, tool, lingua e tempo`() = runTest {
    val connector = DirectConnector(host(words(1)), Apps.school)
    client(connector).run(AiAssistRequest(AiAssistTask.answer("q")))
    val ask = AiAssistProtocol.parse(connector.wire.first { it.contains("\"task\"") })!!
    assertEquals(setOf("protocol", "task", "tools", "lang", "timeoutMillis"), ask.keys)
    assertEquals(buildJsonObject { put("kind", "answer"); put("text", "q") }, ask["task"])
  }
}
