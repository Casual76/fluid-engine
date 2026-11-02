package dev.antigravity.fluidengine.ai.orchestrator

import dev.antigravity.fluidengine.ai.net.AiError
import dev.antigravity.fluidengine.ai.provider.ProviderId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il cancello delle conferme (3.0.0): le richieste fanno la fila invece di far cadere la prima come
 * rifiutata, ognuna con la sua finestra, e lo stato AwaitingConfirmation lo scrive il cancello. Tempo
 * virtuale: un minuto di attesa non costa niente.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiConfirmationGateTest {

  private val outcomes = mutableListOf<Pair<String, ConfirmationOutcome>>()

  private fun TestScope.asking(gate: AiConfirmationGate, title: String, detail: String? = "dettaglio di $title") =
    launch { outcomes += title to gate.ask(title, detail) }

  private fun working(provider: ProviderId = ProviderId.GEMINI) = AssistantState.Working("che tempo fa?", 2, 6, "thinking", 0, provider)

  // ------------------------------------------------------------------------------- la fila

  @Test
  fun `una seconda richiesta non fa cadere la prima, aspetta il suo turno`() = runTest {
    val gate = AiConfirmationGate(timeoutMillis = 60_000)
    asking(gate, "A")
    asking(gate, "B")
    runCurrent()

    val first = gate.current.value!!
    assertEquals("A", first.title)
    assertEquals("dettaglio di A", first.detail)
    assertEquals("la seconda e' in fila, non a schermo", 1, gate.queued.value)
    assertTrue("nessuna delle due ha una risposta", outcomes.isEmpty())

    // Un id che non e' quello a schermo non conta: quello di B non esiste ancora.
    gate.resolve(first.id + 1, true)
    runCurrent()
    assertTrue(outcomes.isEmpty())

    gate.resolve(first.id, true)
    runCurrent()
    assertEquals(listOf("A" to ConfirmationOutcome.CONFIRMED), outcomes)
    val second = gate.current.value!!
    assertEquals("B", second.title)
    assertTrue("gli id salgono nell'ordine in cui compaiono", second.id > first.id)
    assertEquals(0, gate.queued.value)

    gate.resolve(second.id, false)
    runCurrent()
    assertEquals(listOf("A" to ConfirmationOutcome.CONFIRMED, "B" to ConfirmationOutcome.REJECTED), outcomes)
    assertNull(gate.current.value)
  }

  @Test
  fun `la fila e' nell'ordine di arrivo`() = runTest {
    val gate = AiConfirmationGate(timeoutMillis = 60_000)
    listOf("uno", "due", "tre", "quattro").forEach { asking(gate, it) }
    runCurrent()
    val shown = mutableListOf<String>()
    repeat(4) {
      val pending = gate.current.value!!
      shown += pending.title
      gate.resolve(pending.id, true)
      runCurrent()
    }
    assertEquals(listOf("uno", "due", "tre", "quattro"), shown)
    assertEquals(shown, outcomes.map { it.first })
    assertTrue(outcomes.all { it.second == ConfirmationOutcome.CONFIRMED })
    assertNull(gate.current.value)
    assertEquals(0, gate.queued.value)
  }

  @Test
  fun `ogni richiesta ha la sua finestra, che comincia quando compare`() = runTest {
    val gate = AiConfirmationGate(timeoutMillis = 60_000)
    asking(gate, "A")
    asking(gate, "B")
    runCurrent()
    advanceTimeBy(60_001)
    runCurrent()
    // A e' scaduta, e B e' comparsa adesso: non ha consumato un secondo mentre aspettava.
    assertEquals(listOf("A" to ConfirmationOutcome.TIMEOUT), outcomes)
    assertEquals("B", gate.current.value?.title)
    advanceTimeBy(59_000)
    runCurrent()
    assertEquals("B ha ancora la sua finestra", "B", gate.current.value?.title)
    advanceTimeBy(2_000)
    runCurrent()
    assertEquals(listOf("A" to ConfirmationOutcome.TIMEOUT, "B" to ConfirmationOutcome.TIMEOUT), outcomes)
    assertNull(gate.current.value)
  }

  @Test
  fun `cancel ferma la conferma a schermo e tutte quelle in fila, e il cancello riparte pulito`() = runTest {
    val gate = AiConfirmationGate(timeoutMillis = 60_000)
    asking(gate, "A")
    asking(gate, "B")
    asking(gate, "C")
    runCurrent()
    assertEquals(2, gate.queued.value)

    gate.cancel()
    runCurrent()
    assertEquals(
      listOf("A" to ConfirmationOutcome.REJECTED, "B" to ConfirmationOutcome.REJECTED, "C" to ConfirmationOutcome.REJECTED),
      outcomes,
    )
    assertNull("nessuna delle in fila e' mai comparsa", gate.current.value)
    assertEquals(0, gate.queued.value)

    // Una richiesta di dopo la cancellazione e' una domanda nuova: compare e si risolve.
    outcomes.clear()
    asking(gate, "D")
    runCurrent()
    assertEquals("D", gate.current.value?.title)
    gate.resolve(gate.current.value!!.id, true)
    runCurrent()
    assertEquals(listOf("D" to ConfirmationOutcome.CONFIRMED), outcomes)
  }

  @Test
  fun `cancel con il cancello libero non fa niente di male alla prossima richiesta`() = runTest {
    val gate = AiConfirmationGate(timeoutMillis = 60_000)
    gate.cancel()
    asking(gate, "A")
    runCurrent()
    assertEquals("A", gate.current.value?.title)
    gate.resolve(gate.current.value!!.id, true)
    runCurrent()
    assertEquals(listOf("A" to ConfirmationOutcome.CONFIRMED), outcomes)
  }

  @Test
  fun `una richiesta in fila cancellata esce dalla fila senza disturbare le altre`() = runTest {
    val gate = AiConfirmationGate(timeoutMillis = 60_000)
    asking(gate, "A")
    val queuedAway = asking(gate, "B")
    asking(gate, "C")
    runCurrent()
    assertEquals(2, gate.queued.value)

    queuedAway.cancel(CancellationException("il tool e' stato tagliato"))
    runCurrent()
    assertEquals("la fila si accorcia", 1, gate.queued.value)
    assertEquals("A", gate.current.value?.title)

    gate.resolve(gate.current.value!!.id, true)
    runCurrent()
    assertEquals("dopo A tocca a C, B non c'e' piu'", "C", gate.current.value?.title)
    gate.resolve(gate.current.value!!.id, true)
    runCurrent()
    assertEquals(listOf("A" to ConfirmationOutcome.CONFIRMED, "C" to ConfirmationOutcome.CONFIRMED), outcomes)
    assertEquals(0, gate.queued.value)
  }

  @Test
  fun `la richiesta a schermo cancellata lascia il posto alla prossima`() = runTest {
    val gate = AiConfirmationGate(timeoutMillis = 60_000)
    val onScreen = asking(gate, "A")
    asking(gate, "B")
    runCurrent()
    onScreen.cancel(CancellationException("tool tagliato dal suo tetto"))
    runCurrent()
    assertEquals("B", gate.current.value?.title)
    gate.resolve(gate.current.value!!.id, true)
    runCurrent()
    assertEquals(listOf("B" to ConfirmationOutcome.CONFIRMED), outcomes)
    assertNull(gate.current.value)
  }

  @Test
  fun `il cancello si costruisce dalla configurazione, e ne prende la finestra`() {
    val config = AiOrchestratorConfig(confirmationTimeoutMillis = 42_000L)
    assertEquals(42_000L, AiConfirmationGate(config).timeoutMillis)
    assertEquals(AiConfirmationGate.DEFAULT_TIMEOUT_MILLIS, AiConfirmationGate().timeoutMillis)
  }

  // ------------------------------------------------------------- lo stato AwaitingConfirmation

  @Test
  fun `legato a uno stato, il cancello scrive AwaitingConfirmation con le parole della richiesta, e alla risposta lo rimette com'era`() = runTest {
    val state = MutableStateFlow<AssistantState>(working())
    val gate = AiConfirmationGate(timeoutMillis = 60_000).apply { bindState(state) }
    asking(gate, "Aggiungere l'evento?", "venerdi', verifica di storia")
    runCurrent()

    val awaiting = state.value as AssistantState.AwaitingConfirmation
    assertEquals("la domanda e' quella dello stato di prima", "che tempo fa?", awaiting.question)
    assertEquals(ProviderId.GEMINI, awaiting.provider)
    assertEquals(gate.current.value, awaiting.confirmation)
    assertEquals("Aggiungere l'evento?", awaiting.confirmation.title)
    assertEquals("venerdi', verifica di storia", awaiting.confirmation.detail)

    gate.resolve(awaiting.confirmation.id, true)
    runCurrent()
    assertEquals(working(), state.value)
  }

  @Test
  fun `lo stato torna com'era anche quando la conferma scade o la domanda e' fermata`() = runTest {
    val state = MutableStateFlow<AssistantState>(working())
    val gate = AiConfirmationGate(timeoutMillis = 1_000).apply { bindState(state) }
    asking(gate, "A")
    runCurrent()
    assertTrue(state.value is AssistantState.AwaitingConfirmation)
    advanceTimeBy(1_001)
    runCurrent()
    assertEquals(working(), state.value)

    outcomes.clear()
    asking(gate, "B")
    runCurrent()
    assertTrue(state.value is AssistantState.AwaitingConfirmation)
    gate.cancel()
    runCurrent()
    assertEquals(working(), state.value)
    assertEquals(listOf("B" to ConfirmationOutcome.REJECTED), outcomes)
  }

  @Test
  fun `due conferme in fila compaiono nello stato una dopo l'altra, ognuna con le sue parole`() = runTest {
    val state = MutableStateFlow<AssistantState>(working(ProviderId.GROQ))
    val gate = AiConfirmationGate(timeoutMillis = 60_000).apply { bindState(state) }
    asking(gate, "Togliere la stella a X?")
    asking(gate, "Togliere la stella alla linea 6?")
    runCurrent()
    assertEquals("Togliere la stella a X?", (state.value as AssistantState.AwaitingConfirmation).confirmation.title)
    gate.resolve(gate.current.value!!.id, true)
    runCurrent()
    val second = state.value as AssistantState.AwaitingConfirmation
    assertEquals("Togliere la stella alla linea 6?", second.confirmation.title)
    assertEquals("la domanda e il provider restano quelli di prima", "che tempo fa?", second.question)
    assertEquals(ProviderId.GROQ, second.provider)
    gate.resolve(second.confirmation.id, true)
    runCurrent()
    assertEquals(working(ProviderId.GROQ), state.value)
  }

  @Test
  fun `se lo stato e' cambiato nel frattempo, il cancello non lo calpesta con uno vecchio`() = runTest {
    val state = MutableStateFlow<AssistantState>(working())
    val gate = AiConfirmationGate(timeoutMillis = 60_000).apply { bindState(state) }
    asking(gate, "A")
    runCurrent()
    // L'utente tocca "ferma": la sessione scrive Cancelled, poi la domanda lascia il cancello.
    val cancelled = AssistantState.Cancelled("che tempo fa?", null)
    state.value = cancelled
    gate.cancel()
    runCurrent()
    assertEquals(cancelled, state.value)
  }

  @Test
  fun `uno stato a riposo o gia' finito non fa ricomparire nessuna conferma`() = runTest {
    val idle = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val gate = AiConfirmationGate(timeoutMillis = 60_000).apply { bindState(idle) }
    asking(gate, "A")
    runCurrent()
    assertEquals("la conferma c'e' lo stesso, sul cancello", "A", gate.current.value?.title)
    assertEquals(AssistantState.Idle, idle.value)
    gate.resolve(gate.current.value!!.id, true)
    runCurrent()

    val failed = AssistantState.Failed("q", FailureKind.NETWORK, AiError.Network("giu'"), null, null)
    idle.value = failed
    asking(gate, "B")
    runCurrent()
    assertEquals(failed, idle.value)
  }

  @Test
  fun `senza provider nello stato si dice quello di riserva`() = runTest {
    val state = MutableStateFlow<AssistantState>(AssistantState.Transcribing)
    val gate = AiConfirmationGate(timeoutMillis = 60_000).apply { bindState(state, fallbackProvider = ProviderId.OPENROUTER) }
    asking(gate, "A")
    runCurrent()
    val awaiting = state.value as AssistantState.AwaitingConfirmation
    assertEquals(ProviderId.OPENROUTER, awaiting.provider)
    assertEquals("", awaiting.question)
  }

  @Test
  fun `slegato, il cancello non scrive piu' niente`() = runTest {
    val state = MutableStateFlow<AssistantState>(working())
    val gate = AiConfirmationGate(timeoutMillis = 60_000).apply { bindState(state) }
    gate.unbindState()
    asking(gate, "A")
    runCurrent()
    assertEquals(working(), state.value)
    assertEquals("A", gate.current.value?.title)
  }

  // --------------------------------------------------------- le parole di uno stato, per tutti

  @Test
  fun `la domanda e il provider di uno stato si leggono con gli stessi nomi in ogni app`() {
    val provider = ProviderId.GEMINI
    val confirmation = PendingConfirmation(1, "t", null)
    assertEquals("q", AssistantState.Classifying("q", provider).questionOrNull())
    assertEquals("q", AssistantState.Working("q", 1, 6, "thinking", 0, provider).questionOrNull())
    assertEquals("q", AssistantState.WaitingRateLimit("q", provider, 3).questionOrNull())
    assertEquals("q", AssistantState.SwitchingProvider("q", ProviderId.GROQ, provider).questionOrNull())
    assertEquals("q", AssistantState.Answering("q", "...", provider).questionOrNull())
    assertEquals("q", AssistantState.AwaitingConfirmation("q", confirmation, provider).questionOrNull())
    assertEquals("q", AssistantState.Failed("q", FailureKind.UNKNOWN, null, null, null).questionOrNull())
    assertEquals("q", AssistantState.Cancelled("q", null).questionOrNull())
    assertNull(AssistantState.Idle.questionOrNull())
    assertNull(AssistantState.Listening(0).questionOrNull())
    assertNull(AssistantState.Failed(null, FailureKind.NO_KEYS, null, null, null).questionOrNull())

    assertEquals(provider, AssistantState.Classifying("q", provider).providerOrNull())
    assertEquals(provider, AssistantState.AwaitingConfirmation("q", confirmation, provider).providerOrNull())
    assertEquals("passa a un altro: conta quello di arrivo", provider, AssistantState.SwitchingProvider("q", ProviderId.GROQ, provider).providerOrNull())
    assertEquals(ProviderId.GROQ, AssistantState.Failed("q", FailureKind.UNKNOWN, null, null, null, provider = ProviderId.GROQ).providerOrNull())
    assertNull(AssistantState.Idle.providerOrNull())
    assertNull(AssistantState.Transcribing.providerOrNull())
  }
}
