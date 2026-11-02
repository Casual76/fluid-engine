package dev.antigravity.fluidengine.ai.orchestrator

import dev.antigravity.fluidengine.ai.net.AiError
import dev.antigravity.fluidengine.ai.net.BadReason
import dev.antigravity.fluidengine.ai.net.RateLimitInfo
import dev.antigravity.fluidengine.ai.provider.ChatDelta
import dev.antigravity.fluidengine.ai.provider.ChatProvider
import dev.antigravity.fluidengine.ai.provider.ChatRequest
import dev.antigravity.fluidengine.ai.provider.ChatTurn
import dev.antigravity.fluidengine.ai.provider.ModelCatalogue
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.ai.provider.ReadyProvider
import dev.antigravity.fluidengine.ai.provider.TranscribeOptions
import dev.antigravity.fluidengine.ai.provider.Transcript
import dev.antigravity.fluidengine.ai.speech.Transcriber
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Un provider che trascrive male: lancia sempre [error]. */
private class DeafProvider(override val id: ProviderId, private val failure: Throwable) : ChatProvider {
  override suspend fun complete(request: ChatRequest): ChatTurn = error("non si chiama")
  override fun stream(request: ChatRequest): Flow<ChatDelta> = emptyFlow()
  override suspend fun listModels(): ModelCatalogue = ModelCatalogue(emptyList(), emptyList())
  override suspend fun transcribe(audio: File, mime: String, options: TranscribeOptions): Transcript = throw failure
}

/**
 * Da un errore del provider alla ragione che si dice all'utente, per la voce (3.0.0). La domanda
 * scritta passa dal [FailoverPolicy]; la trascrizione no, e prima dava a ogni guasto la faccia di
 * "non sono riuscito a capire l'audio" anche quando l'audio era perfetto e mancava la rete: l'utente
 * alzava la voce e ripeteva, e non serviva a niente.
 */
class FailureKindsTest {

  private fun rateLimited(retryAfterSec: Double? = 2.1) = AiError.RateLimited(retryAfterSec, RateLimitInfo.EMPTY, message = "429")

  @Test
  fun `la tabella dei guasti, con un'altra ragione per quelli che non si sanno leggere`() {
    assertEquals(FailureKind.UNAUTHORIZED, FailureKinds.of(AiError.Unauthorized("401")))
    assertEquals(FailureKind.RATE_LIMITED, FailureKinds.of(rateLimited()))
    assertEquals(FailureKind.TIMEOUT, FailureKinds.of(AiError.Timeout("lento")))
    assertEquals(FailureKind.NETWORK, FailureKinds.of(AiError.Network("giu'")))
    assertEquals(FailureKind.PROVIDER, FailureKinds.of(AiError.Server(503, "down")))
    assertEquals(FailureKind.BLOCKED, FailureKinds.of(AiError.BadRequest(400, "x", BadReason.BLOCKED)))
    assertEquals("il convenzionale 'bloccato: ...' vale ancora", FailureKind.BLOCKED, FailureKinds.of(AiError.BadRequest(400, "bloccato: contenuto")))
    assertEquals(FailureKind.MODEL_UNAVAILABLE, FailureKinds.of(AiError.BadRequest(404, "x", BadReason.MODEL_UNAVAILABLE)))

    // Il resto e' "un'altra cosa": per chi chiede e' UNKNOWN di serie...
    assertEquals(FailureKind.UNKNOWN, FailureKinds.of(AiError.BadRequest(400, "formato")))
    assertEquals(FailureKind.UNKNOWN, FailureKinds.of(AiError.BadRequest(413, "troppo", BadReason.CONTEXT_TOO_LONG)))
    assertEquals(FailureKind.UNKNOWN, FailureKinds.of(AiError.Parse("json")))
    assertEquals(FailureKind.UNKNOWN, FailureKinds.of(IllegalStateException("boh")))
  }

  @Test
  fun `per la trascrizione il resto e' una trascrizione fallita`() {
    val other = FailureKind.TRANSCRIPTION
    assertEquals(other, FailureKinds.of(AiError.BadRequest(400, "audio rifiutato"), other))
    assertEquals(other, FailureKinds.of(AiError.Parse("risposta illeggibile"), other))
    assertEquals(other, FailureKinds.of(RuntimeException("boh"), other))
    // E quello che ha una ragione vera la dice, qualunque sia `other`.
    assertEquals(FailureKind.NETWORK, FailureKinds.of(AiError.Network("niente campo"), other))
    assertEquals(FailureKind.RATE_LIMITED, FailureKinds.of(rateLimited(), other))
  }

  @Test
  fun `la tabella dice le stesse cose del failover per lo stesso guasto`() {
    // Con un solo servizio e nessuna riprova che avanza, la tabella del failover e' la fine di una domanda:
    // i guasti che la voce legge devono avere la stessa ragione.
    val policy = FailoverPolicy()
    val errors = listOf(
      AiError.Unauthorized("401"),
      rateLimited(retryAfterSec = 500.0),
      AiError.Timeout("lento"),
      AiError.Network("giu'"),
      AiError.Server(503, "down"),
      AiError.BadRequest(400, "x", BadReason.BLOCKED),
      AiError.BadRequest(404, "x", BadReason.MODEL_UNAVAILABLE),
    )
    errors.forEach { error ->
      val decision = policy.decide(error, ProviderId.GROQ, remaining = emptyList(), waitsDone = 9, retriesDone = 9, budgetRemainingMillis = 1_000_000, pinned = true)
      assertEquals("$error", (decision as FailoverDecision.Fail).kind, FailureKinds.of(error))
    }
  }

  @Test
  fun `AssistantFailure of porta la ragione, l'errore e l'attesa in secondi interi`() {
    val limited = AssistantFailure.of(rateLimited(2.1), other = FailureKind.TRANSCRIPTION)
    assertEquals(FailureKind.RATE_LIMITED, limited.kind)
    assertEquals("si arrotonda per eccesso: aspettare meno non serve", 3, limited.retryAfterSec)
    assertEquals(2.1, (limited.error as AiError.RateLimited).retryAfterSec!!, 0.0)

    val noWait = AssistantFailure.of(rateLimited(null))
    assertNull(noWait.retryAfterSec)

    val network = AiError.Network("niente campo")
    val failure = AssistantFailure.of(network, provider = ProviderId.GEMINI)
    assertEquals(FailureKind.NETWORK, failure.kind)
    assertSame(network, failure.error)
    assertEquals(ProviderId.GEMINI, failure.provider)
    assertNull(failure.retryAfterSec)

    val unknown = AssistantFailure.of(RuntimeException("boh"), other = FailureKind.TRANSCRIPTION)
    assertEquals(FailureKind.TRANSCRIPTION, unknown.kind)
    assertNull("un'eccezione che non e' un AiError non e' un AiError", unknown.error)
  }

  @Test
  fun `un AssistantFailure che c'e' gia' non si rifa`() {
    val original = AssistantFailure(FailureKind.NO_KEYS, null)
    assertSame(original, AssistantFailure.of(original, other = FailureKind.TRANSCRIPTION))
  }

  @Test
  fun `la trascrizione che fallisce per la rete dice rete, non audio incomprensibile`() = runBlocking {
    val error = AiError.Network("niente campo")
    val transcriber = Transcriber { listOf(ReadyProvider(DeafProvider(ProviderId.GROQ, error), "chat", "stt", null)) }
    val thrown = runCatching { transcriber.transcribe(File("ask.wav"), "it", null) }.exceptionOrNull()!!
    assertEquals(FailureKind.NETWORK, AssistantFailure.of(thrown, other = FailureKind.TRANSCRIPTION).kind)

    val rejected = Transcriber { listOf(ReadyProvider(DeafProvider(ProviderId.GROQ, AiError.BadRequest(400, "audio rifiutato")), "chat", "stt", null)) }
    val thrownBad = runCatching { rejected.transcribe(File("ask.wav"), "it", null) }.exceptionOrNull()!!
    assertEquals("un audio rifiutato e' una trascrizione fallita", FailureKind.TRANSCRIPTION, AssistantFailure.of(thrownBad, other = FailureKind.TRANSCRIPTION).kind)
  }
}
