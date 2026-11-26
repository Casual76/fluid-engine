package dev.antigravity.fluidengine.ai.bridge.assist

import dev.antigravity.fluidengine.ai.bridge.BridgeProtocol
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
