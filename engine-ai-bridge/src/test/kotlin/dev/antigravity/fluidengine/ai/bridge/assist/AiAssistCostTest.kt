package dev.antigravity.fluidengine.ai.bridge.assist

import dev.antigravity.fluidengine.ai.bridge.BridgeProtocol
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F9.1 · Quanto costa il formato al PC (JVM): scrivere e rileggere un invio tipico, i suoi byte, una domanda con la
 * pagina piena. I numeri vanno nel rapporto (la riga `MISURA` nell'uscita della prova); le soglie sono larghe, perche'
 * un PC carico non faccia cadere il lotto: servono solo a dire che il formato non e' il collo di bottiglia di Binder.
 */
class AiAssistCostTest {

  private fun batch(): String = AiAssistProtocol.batch(
    "3f2b1c9e-1d4a-4c3b-9a8e-7f6d5c4b3a21",
    (1L..5L).map { AiAssistWireEvent(it, AiAssistProtocol.TYPE_TEXT, delta = "la media scende perché il cinque pesa ") },
  )

  @Test fun `un invio di cinque pezzi si scrive e si rilegge in pochi microsecondi`() {
    val text = batch()
    val bytes = text.toByteArray(Charsets.UTF_16LE).size // in un Parcel le stringhe sono UTF-16
    repeat(5_000) { AiAssistProtocol.batchOf(batch()) } // riscaldamento
    val rounds = 20_000
    val t = System.nanoTime()
    repeat(rounds) { AiAssistProtocol.batchOf(batch())!! }
    val micros = (System.nanoTime() - t) / 1_000.0 / rounds
    val ask = AiAssistProtocol.askRequest(AiAssistRequest(
      AiAssistTask.answer("Perché questo voto abbassa la media?"),
      page = AiAssistPage("T".repeat(120), (1..8).map { "r".repeat(160) }, "s".repeat(2_000)),
    )).toString()
    println("MISURA invio di 5 pezzi: ${String.format(java.util.Locale.ITALY, "%.2f", micros)} µs scrivere+rileggere, ${text.length} caratteri, $bytes byte nel Parcel; domanda con la pagina piena ${ask.length} caratteri, ${ask.toByteArray(Charsets.UTF_16LE).size} byte")
    assertTrue("$micros µs", micros < 2_000)
    assertTrue("la domanda con la pagina piena resta lontana da 1 MB: ${ask.length}", ask.length < 3_000)
  }

  /**
   * F9.1d: la memoria che l'ospite tiene dopo tante domande, contata e non stimata dal processo. Sul tablet la misura col
   * PSS del processo `:pampai` (F9.1) era caduta sul limite di 30 domande al minuto; qui le 48 domande stanno nel limite
   * (una ogni 2,1 s di tempo virtuale) e dopo un minuto di quiete l'ospite tiene solo la domanda nuova, piu' le voci di
   * «Dalle app» (che restano finche' l'utente non le toglie, F9.1c): testo, non strutture che crescono.
   */
  @Test fun `la memoria dell'ospite - dopo 48 domande restano solo le voci di Dalle app`() = runTest {
    val answer = "La media scende perché il cinque pesa quanto gli altri voti messi insieme. ".repeat(4)
    val history = InMemoryAiAssistHistory()
    val host = host(ScriptedExecutor { _, sink -> sink.status("Controllo i voti", Apps.School); sink.text(answer) }, history = history)
    var refused = 0
    repeat(48) { i ->
      val response = host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("Perché questo voto abbassa la media? $i"))), null).response
      if (response.errorCode() != null) refused++
      advanceTimeBy(2_100)
    }
    advanceUntilIdle()
    assertEquals("nessuna domanda rifiutata: una ogni 2,1 s sta nel limite", 0, refused)
    val lastMinute = host.retained()
    assertTrue("tenute solo quelle dell'ultimo minuto, per assist_poll: $lastMinute", lastMinute <= 29)
    advanceTimeBy(61_000)
    host.ask(Apps.school, askJson(AiAssistRequest(AiAssistTask.answer("e adesso?"))), null)
    advanceUntilIdle()
    assertEquals("dopo un minuto di quiete l'ospite tiene solo la domanda nuova", 1, host.retained())
    assertEquals(49, history.entries().size)
    val chars = history.entries().sumOf { it.question.length + it.answer.length + (it.pageTitle?.length ?: 0) + it.appName.length }
    println("MISURA memoria dell'ospite dopo 48 domande: $lastMinute domande tenute per assist_poll, poi 1; «Dalle app» 49 voci, $chars caratteri (circa ${chars * 2 / 1024} kB di testo)")
    assertTrue("le voci sono testo, qualche decina di kB: $chars", chars < 40_000)
  }

  @Test fun `i tool restano alla 1 e alla 2 - la negoziazione dei tool non cambia`() {
    assertEquals(2, BridgeProtocol.MAX_PROTOCOL)
    assertEquals(1, BridgeProtocol.PROTOCOL)
    // Un cliente dei tool di oggi con l'ospite dell'assistenza: nessun protocollo comune.
    assertTrue(AiAssistProtocol.negotiate(BridgeProtocol.negotiationRequest())["error"] != null)
    // Un cliente dell'assistenza con un ospite dei tool: nessun protocollo comune, quindi `needs_update`.
    assertTrue(BridgeProtocol.negotiate(AiAssistProtocol.negotiationRequest())["error"] != null)
    // Con le due liste insieme, ognuno trova la sua.
    val both = buildJsonObject { put("versions", buildJsonArray { listOf(1, 2, 3).forEach { add(JsonPrimitive(it)) } }) }
    assertEquals(JsonPrimitive(2), BridgeProtocol.negotiate(both)["protocol"])
    assertEquals(JsonPrimitive(3), AiAssistProtocol.negotiate(both)["protocol"])
  }
}
