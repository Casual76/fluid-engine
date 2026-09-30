package dev.antigravity.fluidengine.ai.net

import dev.antigravity.fluidengine.ai.orchestrator.AiOrchestratorConfig
import dev.antigravity.fluidengine.ai.provider.ChatDelta
import dev.antigravity.fluidengine.ai.provider.ChatRequest
import dev.antigravity.fluidengine.ai.provider.Message
import dev.antigravity.fluidengine.ai.provider.ModelCatalogue
import dev.antigravity.fluidengine.ai.provider.ModelTier
import dev.antigravity.fluidengine.ai.provider.OpenAiCompatProvider
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.ai.provider.ReasoningLevel
import dev.antigravity.fluidengine.ai.provider.TranscribeOptions
import dev.antigravity.fluidengine.ai.provider.Transcript
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Il dialetto OpenAI contro il server finto: nessun campo in piu', nessun catalogo. */
private class LocalCompatProvider(http: AiHttp, base: String) : OpenAiCompatProvider(http, base, "k") {
  override val id: ProviderId = ProviderId.GROQ
  override fun JsonObjectBuilder.providerFields(request: ChatRequest, stream: Boolean, dropped: Set<String>) = Unit
  override suspend fun listModels(): ModelCatalogue = ModelCatalogue(emptyList(), emptyList())
  override suspend fun transcribe(audio: File, mime: String, options: TranscribeOptions): Transcript = Transcript("", null)
}

/**
 * Il timeout di lettura per richiesta (2.8.0), contro un server vero: un modello che pensa tace
 * piu' del timeout corto e meno di quello lungo, e deve arrivare in fondo solo col lungo. Con i
 * trenta secondi fissi di prima, il thinking di Gemini diventava Timeout -> riprova -> TIMEOUT.
 * I numeri sono scalati (mezzo secondo e tre secondi) perche' il test non duri minuti.
 */
class StreamTimeoutTest {

  private lateinit var server: TinyHttpServer

  /** Il client coi default corti: cio' che la richiesta non dice vale mezzo secondo. */
  private val http = AiHttp(userAgent = "test", connectTimeoutMillis = 2_000, readTimeoutMillis = 500, streamChunkTimeoutMillis = 500)

  /** Gli stessi due numeri dell'orchestratore, scalati. */
  private val config = AiOrchestratorConfig(streamChunkTimeoutMillis = 500, thinkingChunkTimeoutMillis = 3_000)

  @Before
  fun start() {
    server = TinyHttpServer()
  }

  @After
  fun stop() {
    server.close()
  }

  /** Uno stream che dice una cosa, tace [silenceMillis], e poi finisce. */
  private fun thinkingStream(path: String, silenceMillis: Long) {
    server.handle(path) { _, response ->
      response.begin(200, mapOf("Content-Type" to "text/event-stream"))
      response.write("data: primo\n\n")
      Thread.sleep(silenceMillis)
      response.write("data: dopo il silenzio\n\n")
      response.write("data: [DONE]\n\n")
    }
  }

  @Test
  fun `un silenzio piu' lungo del timeout corto ma piu' corto del lungo passa solo col lungo`() = runBlocking<Unit> {
    thinkingStream("/think", silenceMillis = 1_200)
    val short = config.readTimeoutFor(ModelTier.CHAT, ReasoningLevel.NONE, budgetRemainingMillis = 60_000)
    val long = config.readTimeoutFor(ModelTier.DEEP, ReasoningLevel.NONE, budgetRemainingMillis = 60_000)
    assertEquals(500, short)
    assertEquals(3_000, long)

    val cut = runCatching {
      withTimeout(10_000) { http.postJsonStream("${server.base}/think", emptyMap(), buildJsonObject { }, chunkTimeoutMillis = short).toList() }
    }.exceptionOrNull()
    assertTrue("atteso Timeout, avuto $cut", cut is AiError.Timeout)

    val events = withTimeout(10_000) { http.postJsonStream("${server.base}/think", emptyMap(), buildJsonObject { }, chunkTimeoutMillis = long).toList() }
    assertEquals(listOf("primo", "dopo il silenzio"), events)
  }

  @Test
  fun `il provider porta il timeout della richiesta fino alla socket, in stream e senza`() = runBlocking<Unit> {
    server.handle("/chat/completions") { request, response ->
      val streaming = String(request.body, Charsets.UTF_8).contains("\"stream\":true")
      Thread.sleep(1_200)
      if (streaming) {
        response.begin(200, mapOf("Content-Type" to "text/event-stream"))
        response.write("data: {\"choices\":[{\"delta\":{\"content\":\"Sereno.\"}}]}\n\n")
        response.write("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n")
        response.write("data: [DONE]\n\n")
      } else {
        response.reply(200, """{"choices":[{"message":{"content":"Sereno."},"finish_reason":"stop"}]}""")
      }
    }
    val provider = LocalCompatProvider(http, server.base)
    val request = ChatRequest(model = "m", messages = listOf(Message.User("?")), reasoning = ReasoningLevel.HIGH)

    val cut = runCatching { withTimeout(10_000) { provider.stream(request).toList() } }.exceptionOrNull()
    assertTrue("atteso Timeout senza timeout per richiesta, avuto $cut", cut is AiError.Timeout)

    val patient = request.copy(readTimeoutMillis = config.readTimeoutFor(ModelTier.CHAT, request.reasoning, 60_000))
    val deltas = withTimeout(10_000) { provider.stream(patient).toList() }
    assertEquals("Sereno.", deltas.filterIsInstance<ChatDelta.Text>().joinToString("") { it.text })

    // La riprova senza stream (quella di un flusso spezzato a meta') usa lo stesso numero.
    val shortComplete = runCatching { provider.complete(request) }.exceptionOrNull()
    assertTrue("atteso Timeout, avuto $shortComplete", shortComplete is AiError.Timeout)
    assertEquals("Sereno.", provider.complete(patient).message.text)
  }

  @Test
  fun `un tool_use_failed non passa dall'auto-riparazione dei campi, anche se ne nomina uno`() = runBlocking<Unit> {
    val hits = AtomicInteger()
    server.handle("/chat/completions") { _, response ->
      hits.incrementAndGet()
      response.reply(
        400,
        """{"error":{"message":"tool call validation failed: parameters for tool meteo did not match schema: errors: [missing properties: 'temperature']","type":"invalid_request_error","code":"tool_use_failed"}}""",
      )
    }
    val provider = LocalCompatProvider(http, server.base)
    val error = runCatching { provider.stream(ChatRequest(model = "m", messages = listOf(Message.User("?")), temperature = 0.3, readTimeoutMillis = 3_000)).toList() }.exceptionOrNull()
    assertTrue("atteso BadRequest, avuto $error", error is AiError.BadRequest && error.reason == BadReason.TOOL_USE_FAILED)
    assertEquals(1, hits.get())
  }
}
