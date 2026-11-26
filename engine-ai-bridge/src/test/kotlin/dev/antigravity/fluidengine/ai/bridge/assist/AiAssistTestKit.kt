package dev.antigravity.fluidengine.ai.bridge.assist

import dev.antigravity.fluidengine.ai.bridge.ReadyState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.JsonObject

/** Le app delle prove. */
internal object Apps {
  const val School = "dev.prova.scuola"
  const val Weather = "dev.prova.meteo"
  const val Stranger = "dev.prova.estranea"

  val policy = AiAssistPolicy(
    mapOf(
      School to AiAssistPolicy.Entry(School, "Scuola", AiAssistPolicy.AllScopes),
      Weather to AiAssistPolicy.Entry(Weather, "Meteo", setOf(AiAssistPolicy.ScopeAnswer, AiAssistPolicy.ScopeTools)),
    ),
  )

  val school = AiAssistCaller(School, "Scuola", toolsAuthority = "$School.ai.tools")
  val weather = AiAssistCaller(Weather, "Meteo", toolsAuthority = null)
  val stranger = AiAssistCaller(Stranger, "Estranea")
}

/** Un esecutore a copione, che ricorda le chiamate e se e' stato cancellato. */
internal class ScriptedExecutor(private val script: suspend (AiAssistCall, AiAssistSink) -> Unit) : AiAssistExecutor {
  val calls = ArrayList<AiAssistCall>()
  var cancelled = 0
  var finished = 0

  override suspend fun execute(call: AiAssistCall, sink: AiAssistSink) {
    calls += call
    try {
      script(call, sink)
      finished++
    } catch (e: kotlinx.coroutines.CancellationException) {
      cancelled++
      throw e
    }
  }
}

/** Un canale di ritorno finto: ricorda gli invii e quando sono arrivati. */
internal class RecordingListener(private val clock: () -> Long, var alive: Boolean = true) : AiAssistListener {
  val batches = ArrayList<Pair<Long, AiAssistProtocol.Batch>>()
  var closed = false

  override fun deliver(batch: String): Boolean {
    if (!alive) return false
    batches += clock() to AiAssistProtocol.batchOf(batch)!!
    return true
  }

  override fun closed() { closed = true }

  val events: List<AiAssistWireEvent> get() = batches.flatMap { it.second.events }

  fun progress(): AiAssistProgress = AiAssistAssembler().also { it.add(events) }.progress
}

/** Il registro delle prove: le due app hanno gia' detto sì a PampAI (il primo uso si prova in `AiAssistFirstUseTest`). */
internal fun allowedConsents(): InMemoryAiAssistConsentStore = InMemoryAiAssistConsentStore().apply {
  setAppConsent(Apps.School, AiAssistConsentState.Allowed)
  setAppConsent(Apps.Weather, AiAssistConsentState.Allowed)
}

internal fun TestScope.host(
  executor: AiAssistExecutor,
  consents: AiAssistConsentStore = allowedConsents(),
  limits: AiAssistLimits = AiAssistLimits(),
  ready: () -> ReadyState = { ReadyState(true) },
  // La prova stessa, non `backgroundScope`: `advanceUntilIdle` fa girare solo il lavoro in primo piano.
  scope: CoroutineScope = this,
  history: AiAssistHistory? = null,
): AiAssistHost = AiAssistHost(executor, consents, Apps.policy, limits, ready, scope, clock = { testScheduler.currentTime }, history = history, wallClock = { 1_764_000_000_000L + testScheduler.currentTime })

internal fun askJson(request: AiAssistRequest): JsonObject = AiAssistProtocol.askRequest(request)

internal fun JsonObject.errorCode(): String? = AiAssistProtocol.errorOf(this)?.code

/** Parole che non devono mai arrivare all'app: i fornitori, i modelli, le chiavi. */
internal val Leaks = listOf("gsk_", "AIza", "sk-or-", "groq", "gemini", "openrouter", "llama", "qwen", "apiKey", "Bearer")
