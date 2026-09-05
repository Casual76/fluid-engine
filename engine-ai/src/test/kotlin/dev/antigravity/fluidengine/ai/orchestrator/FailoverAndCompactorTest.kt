package dev.antigravity.fluidengine.ai.orchestrator

import dev.antigravity.fluidengine.ai.net.AiError
import dev.antigravity.fluidengine.ai.net.RateLimitInfo
import dev.antigravity.fluidengine.ai.provider.Message
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.ai.provider.ToolCall
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FailoverAndCompactorTest {

  private val policy = FailoverPolicy()
  private fun limited(retry: Double? = 5.0) = AiError.RateLimited(retry, RateLimitInfo.EMPTY, message = "429")

  @Test
  fun `429 con un provider dopo passa a quello`() {
    val decision = policy.decide(limited(), ProviderId.GROQ, listOf(ProviderId.GEMINI, ProviderId.OPENROUTER), 0, 0, 80_000)
    assertEquals(FailoverDecision.Switch(ProviderId.GEMINI), decision)
  }

  @Test
  fun `429 senza alternative aspetta il retry-after, al massimo due volte e mai oltre il budget`() {
    assertEquals(FailoverDecision.Wait(5), policy.decide(limited(4.2), ProviderId.GROQ, emptyList(), 0, 0, 80_000))
    assertEquals(FailoverDecision.Wait(5), policy.decide(limited(null), ProviderId.GROQ, emptyList(), 1, 0, 80_000))
    assertTrue(policy.decide(limited(5.0), ProviderId.GROQ, emptyList(), 2, 0, 80_000) is FailoverDecision.Fail)
    val tooLong = policy.decide(limited(70.0), ProviderId.GROQ, emptyList(), 0, 0, 80_000)
    assertTrue(tooLong is FailoverDecision.Fail && (tooLong as FailoverDecision.Fail).retryAfterSec == 70)
    val noBudget = policy.decide(limited(20.0), ProviderId.GROQ, emptyList(), 0, 0, 25_000)
    assertTrue(noBudget is FailoverDecision.Fail)
  }

  @Test
  fun `5xx e rete riprovano una volta, poi passano, poi falliscono`() {
    val server = AiError.Server(503, "down")
    assertEquals(FailoverDecision.RetrySame, policy.decide(server, ProviderId.GROQ, listOf(ProviderId.GEMINI), 0, 0, 80_000))
    assertEquals(FailoverDecision.Switch(ProviderId.GEMINI), policy.decide(server, ProviderId.GROQ, listOf(ProviderId.GEMINI), 0, 1, 80_000))
    val fail = policy.decide(AiError.Network("no"), ProviderId.GEMINI, emptyList(), 0, 1, 80_000)
    assertTrue(fail is FailoverDecision.Fail && (fail as FailoverDecision.Fail).kind == FailureKind.NETWORK)
    val timeout = policy.decide(AiError.Timeout("t"), ProviderId.GEMINI, emptyList(), 0, 1, 80_000)
    assertEquals(FailureKind.TIMEOUT, (timeout as FailoverDecision.Fail).kind)
  }

  @Test
  fun `chiave sbagliata e richiesta bloccata non si mascherano`() {
    val unauthorized = policy.decide(AiError.Unauthorized("no"), ProviderId.GROQ, listOf(ProviderId.GEMINI), 0, 0, 80_000)
    assertEquals(FailureKind.UNAUTHORIZED, (unauthorized as FailoverDecision.Fail).kind)
    val blocked = policy.decide(AiError.BadRequest(200, "bloccato: SAFETY"), ProviderId.GEMINI, listOf(ProviderId.GROQ), 0, 0, 80_000)
    assertEquals(FailureKind.BLOCKED, (blocked as FailoverDecision.Fail).kind)
  }

  @Test
  fun `il budget di tempo e la riserva finale sono dell'app`() {
    var now = 0L
    val budget = TimeBudget(startMillis = 0L, totalMillis = 100_000L, finalReserveMillis = 30_000L, clock = { now })
    assertEquals(100_000L, budget.remainingMillis)
    assertTrue(!budget.forceFinal)
    now = 75_000L
    assertTrue(budget.forceFinal)
    now = 200_000L
    assertEquals(0L, budget.remainingMillis)
  }

  @Test
  fun `il compattatore tiene le coppie, il traffico tool solo dell'ultima, e taglia dal piu' vecchio`() {
    val conversation = Conversation(1L, 0L)
    repeat(3) { i -> conversation.exchanges += Exchange("domanda $i", "risposta $i " + "x".repeat(700), emptyList(), ProviderId.GROQ, i.toLong()) }
    val call = ToolCall("call_1", "adesso", JsonObject(emptyMap()))
    conversation.lastToolRound = listOf(Message.Assistant(null, listOf(call)), Message.ToolResult("call_1", "adesso", "temperatura: 21 °C"))
    val full = HistoryCompactor.compact(conversation, budgetTokens = 60_000)
    // 3 domande + 3 risposte + assistant con tool + risultato = 8, con il traffico tool prima dell'ultima risposta.
    assertEquals(8, full.size)
    assertTrue(full[4] is Message.User && (full[4] as Message.User).text == "domanda 2")
    assertTrue(full[5] is Message.Assistant && (full[5] as Message.Assistant).toolCalls.isNotEmpty())
    assertTrue(full[6] is Message.ToolResult)
    assertEquals("risposta 0 " + "x".repeat(600 - 11), (full[1] as Message.Assistant).text)
    assertTrue(((full[7] as Message.Assistant).text?.length ?: 0) > 700)
    val tight = HistoryCompactor.compact(conversation, budgetTokens = 250)
    assertTrue(tight.none { it is Message.ToolResult })
    assertTrue(tight.size <= 2)
    assertTrue(tight.first() is Message.User && (tight.first() as Message.User).text == "domanda 2")
  }

  @Test
  fun `i chip si estraggono con id e valore, quelli che l'app non riconosce spariscono e basta`() {
    val known = setOf("radar", "luogo", "nowcast")
    val (text, chips) = ChipParser.extract(
      "Pioverà alle 18, dice il radar.\n\n[[radar]] [[luogo:Bologna]] [[sconosciuto]] [[nowcast]] [[orario]]",
      accept = { it.id in known },
    )
    assertEquals("Pioverà alle 18, dice il radar.", text)
    assertEquals(listOf(AnswerChip("radar"), AnswerChip("luogo", "Bologna"), AnswerChip("nowcast")), chips)
    val (_, none) = ChipParser.extract("niente chip qui")
    assertTrue(none.isEmpty())
  }

  @Test
  fun `la conversazione scade dopo dieci minuti di silenzio`() {
    val conversation = Conversation(1L, 0L)
    assertTrue(!conversation.isExpired(9 * 60_000L))
    assertTrue(conversation.isExpired(11 * 60_000L))
  }

  @Test
  fun `il cancello delle conferme risponde a chi aspetta, e una seconda richiesta fa cadere la prima`() = runBlocking {
    val gate = AiConfirmationGate(timeoutMillis = 200)
    assertNull(gate.current.value)
    var outcome: ConfirmationOutcome? = null
    val job = launch { outcome = gate.ask("Aggiungere l'evento?", "venerdì, verifica di storia") }
    while (gate.current.value == null) delay(2)
    val pending = gate.current.value!!
    assertEquals("Aggiungere l'evento?", pending.title)
    gate.resolve(pending.id + 99, true) // un id sbagliato non conta
    gate.resolve(pending.id, true)
    job.join()
    assertEquals(ConfirmationOutcome.CONFIRMED, outcome)
    assertNull(gate.current.value)

    val timedOut = gate.ask("Nessuno risponde", null)
    assertEquals(ConfirmationOutcome.TIMEOUT, timedOut)
  }
}
