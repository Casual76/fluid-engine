package dev.antigravity.fluidengine.ai.provider

import dev.antigravity.fluidengine.ai.net.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ProviderAttemptLifecycleTest {
  private class Ledger : ProviderAttemptLifecycle {
    val admitted = mutableListOf<ProviderAttempt>()
    val settled = mutableListOf<ProviderSettlement>()
    var deny = false
    var observerFails = false
    override suspend fun beforeAttempt(attempt: ProviderAttempt) {
      if (deny) throw ProviderAdmissionDenied("budget")
      admitted += attempt
    }
    override suspend fun afterAttempt(settlement: ProviderSettlement) {
      settled += settlement
      if (observerFails) error("storage unavailable")
    }
  }
  private fun provider(server: TinyHttpServer) = object : OpenAiCompatProvider(AiHttp("fixture"), server.base, "fixture") {
    override val id = ProviderId.GROQ
    override fun JsonObjectBuilder.providerFields(request: ChatRequest, stream: Boolean, dropped: Set<String>) {
      if ("reasoning_effort" !in dropped) put("reasoning_effort", "high")
    }
    override suspend fun listModels() = ModelCatalogue(emptyList(), emptyList())
    override suspend fun transcribe(audio: File, mime: String, options: TranscribeOptions): Transcript = error("unused")
  }
  private fun request(ledger: Ledger) = ChatRequest("exact", listOf(Message.User("hello")), lifecycle = ledger, maxOutputTokens = 40)
  private val ok = """{"choices":[{"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}],"usage":{"prompt_tokens":3,"completion_tokens":2,"total_tokens":5}}"""

  @Test fun denialPreventsSocketAndSettlement() = runBlocking {
    TinyHttpServer().use { server ->
      val calls = AtomicInteger()
      server.handle("/chat/completions") { _, r -> calls.incrementAndGet(); r.reply(200, ok) }
      val ledger = Ledger().apply { deny = true }
      try { provider(server).complete(request(ledger)); fail() } catch (_: ProviderAdmissionDenied) {}
      assertEquals(0, calls.get()); assertTrue(ledger.settled.isEmpty())
    }
  }

  @Test fun internalFallbackHasFreshReservationAndActualBounds() = runBlocking {
    TinyHttpServer().use { server ->
      val calls = AtomicInteger(); val ledger = Ledger()
      server.handle("/chat/completions") { _, r ->
        assertEquals(calls.get() + 1, ledger.admitted.size)
        if (calls.incrementAndGet() == 1) r.reply(400, """{"error":{"message":"unsupported max_completion_tokens"}}""") else r.reply(200, ok)
      }
      provider(server).complete(request(ledger))
      assertEquals(2, ledger.admitted.map { it.attemptId }.toSet().size)
      assertEquals(listOf(40, null), ledger.admitted.map { it.maxOutputTokens })
      assertEquals(listOf(ProviderAttemptOutcome.FAILED, ProviderAttemptOutcome.SUCCESS), ledger.settled.map { it.outcome })
      assertNull(ledger.settled[0].usage); assertEquals(5, ledger.settled[1].usage?.totalTokens)
    }
  }

  @Test fun outerRetryAnd429KeepDistinctIdentityAndRateMetadata() = runBlocking {
    TinyHttpServer().use { server ->
      val calls = AtomicInteger(); val ledger = Ledger(); val p = provider(server)
      server.handle("/chat/completions") { _, r ->
        if (calls.incrementAndGet() == 1) r.reply(429, "{}", mapOf("Retry-After" to "2", "x-ratelimit-remaining-requests" to "0")) else r.reply(200, ok)
      }
      try { p.complete(request(ledger)); fail() } catch (_: AiError.RateLimited) {}
      p.complete(request(ledger))
      assertEquals(2, ledger.admitted.map { it.attemptId }.toSet().size)
      assertEquals(2.0, ledger.settled.first().retryAfterSec)
      assertEquals(0, ledger.settled.first().rateLimit.remainingRequests)
      assertEquals(ProviderDispatchCertainty.RESPONSE_RECEIVED, ledger.settled.first().certainty)
    }
  }

  @Test fun settlementFailureNeverReplaysSuccessAndOtherObserverStillSettles() = runBlocking {
    TinyHttpServer().use { server ->
      val calls = AtomicInteger(); val inherited = Ledger().apply { observerFails = true }; val local = Ledger()
      server.handle("/chat/completions") { _, r -> calls.incrementAndGet(); r.reply(200, ok) }
      try {
        withContext(ProviderExecutionContext("job", "helper", inherited)) { provider(server).complete(request(local)) }
        fail()
      } catch (_: ProviderObserverFailure) {}
      assertEquals(1, calls.get()); assertEquals(1, inherited.settled.size); assertEquals(1, local.settled.size)
      assertEquals("job", local.admitted.single().executionId)
      assertEquals(ProviderAttemptOutcome.SUCCESS, local.settled.single().outcome)
    }
  }

  @Test fun cancellationOfPartialStreamSettlesUnknownOnce() = runBlocking {
    TinyHttpServer().use { server ->
      val ledger = Ledger()
      server.handle("/chat/completions") { _, r ->
        r.begin(200); r.write("data: {\"choices\":[{\"delta\":{\"content\":\"part\"}}]}\n\n")
      }
      provider(server).stream(request(ledger)).take(1).collect()
      val end = ledger.settled.single()
      assertEquals(ProviderAttemptOutcome.CANCELLED, end.outcome)
      assertTrue(end.partialResponse); assertNull(end.usage)
      assertEquals(ProviderDispatchCertainty.RESPONSE_RECEIVED, end.certainty)
    }
  }

  @Test fun streamInBandErrorAndNonStreamRecoveryEachSettle() = runBlocking {
    TinyHttpServer().use { server ->
      val ledger = Ledger(); val calls = AtomicInteger(); val p = provider(server)
      server.handle("/chat/completions") { _, r ->
        if (calls.incrementAndGet() == 1) { r.begin(200); r.write("data: {\"error\":{\"message\":\"broken\",\"code\":500}}\n\n") }
        else r.reply(200, ok)
      }
      try { p.stream(request(ledger)).collect(); fail() } catch (_: AiError) {}
      p.complete(request(ledger))
      assertEquals(2, ledger.settled.size)
      assertEquals(listOf(true, false), ledger.admitted.map { it.streaming })
      assertNull(ledger.settled.first().usage)
    }
  }

  @Test fun laterDenialReleasesEarlierAdmissionWithoutDispatch() = runBlocking {
    TinyHttpServer().use { server ->
      val inherited = Ledger(); val local = Ledger().apply { deny = true }
      try { withContext(ProviderExecutionContext("job", "router", inherited)) { provider(server).complete(request(local)) }; fail() }
      catch (_: ProviderAdmissionDenied) {}
      val end = inherited.settled.single()
      assertEquals(ProviderAttemptOutcome.NOT_DISPATCHED, end.outcome)
      assertEquals(ProviderDispatchCertainty.NOT_DISPATCHED, end.certainty)
    }
  }

  @Test fun auditFailureIsControlSignalBeforeAdmission() = runBlocking {
    TinyHttpServer().use { server ->
      val ledger = Ledger()
      try { provider(server).complete(request(ledger).copy(beforeDispatch = { throw AiError.BadRequest(400, "reasoning_effort") })); fail() }
      catch (_: ProviderObserverFailure) {}
      assertTrue(ledger.admitted.isEmpty())
    }
  }

  @Test fun geminiFallbackAndStreamHaveSameLifecycle() = runBlocking {
    TinyHttpServer().use { server ->
      val ledger = Ledger(); val calls = AtomicInteger()
      server.handle("/models/exact:generateContent") { _, r ->
        if (calls.incrementAndGet() == 1) r.reply(400, """{"error":{"message":"unsupported thinking"}}""")
        else r.reply(200, """{"candidates":[{"content":{"parts":[{"text":"ok"}]},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":4,"candidatesTokenCount":1,"totalTokenCount":5}}""")
      }
      server.handle("/models/exact:streamGenerateContent") { _, r ->
        r.begin(200)
        r.write("data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]},\"finishReason\":\"STOP\"}],\"usageMetadata\":{\"promptTokenCount\":4,\"totalTokenCount\":5}}\n\ndata: [DONE]\n\n")
      }
      val p = GeminiProvider(AiHttp("fixture"), "fixture", server.base)
      p.complete(request(ledger).copy(webSearch = true))
      p.stream(request(ledger).copy(webSearch = true)).collect()
      assertTrue(ledger.admitted.all { it.webSearch && it.webSearchMaxResults == null && it.inputTokenUpperBound == null })
      assertEquals(3, ledger.settled.size)
      assertEquals(listOf("medium", "low", "medium"), ledger.admitted.map { it.dispatch.effective })
      assertEquals(listOf(ProviderAttemptOutcome.FAILED, ProviderAttemptOutcome.SUCCESS, ProviderAttemptOutcome.SUCCESS), ledger.settled.map { it.outcome })
      assertEquals(5, ledger.settled.last().usage?.totalTokens)
    }
  }

  @Test fun contextAuditCoversInternallyConstructedRouterRequest() = runBlocking {
    TinyHttpServer().use { server ->
      val ledger = Ledger(); val audit = mutableListOf<ProviderDispatch>()
      server.handle("/chat/completions") { _, r -> r.reply(200, ok) }
      withContext(ProviderExecutionContext("job", "router", ledger, beforeDispatch = { audit += it })) {
        dev.antigravity.fluidengine.ai.orchestrator.AiRouter(emptyList(), null, "fixture", emptyList()).classifyWithTurn(
          provider(server), "exact", "hi", null, emptySet(), "en", false)
      }
      assertEquals(1, audit.size); assertEquals(1, ledger.settled.size)
      assertEquals(120, ledger.admitted.single().maxOutputTokens)
      assertTrue(ledger.admitted.single().structuredOutput)
    }
  }

  @Test fun unknownUsageOnSuccessStaysNullAndContextInstanceIsNotDuplicated() = runBlocking {
    TinyHttpServer().use { server ->
      val ledger = Ledger()
      server.handle("/chat/completions") { _, r -> r.reply(200, """{"choices":[{"message":{"content":"ok"},"finish_reason":"stop"}]}""") }
      withContext(ProviderExecutionContext("job", "round", ledger)) { provider(server).complete(request(ledger)) }
      assertEquals(1, ledger.admitted.size); assertEquals(1, ledger.settled.size)
      assertNull(ledger.settled.single().usage)
    }
  }

  @Test fun malformedResponseStillSettlesDispatchedUnknownUsage() = runBlocking {
    TinyHttpServer().use { server ->
      val ledger = Ledger()
      server.handle("/chat/completions") { _, r -> r.reply(200, "not JSON") }
      try { provider(server).complete(request(ledger)); fail() } catch (_: AiError.Parse) {}
      val end = ledger.settled.single()
      assertEquals(ProviderAttemptOutcome.FAILED, end.outcome)
      assertNull(end.usage)
      assertTrue(end.certainty != ProviderDispatchCertainty.NOT_DISPATCHED)
    }
  }

  @Test fun realOrchestratorRetriesTrimAndStreamRecoveryReserveIndependently() = runBlocking {
    for (mode in listOf("retry", "trim", "recovery")) {
      TinyHttpServer().use { server ->
        val ledger = Ledger(); val calls = AtomicInteger()
        server.handle("/chat/completions") { _, r ->
          if (calls.incrementAndGet() == 1) {
            when (mode) {
              "retry" -> r.reply(400, """{"error":{"code":"tool_use_failed","message":"Failed to call a function"}}""")
              "trim" -> r.reply(413, """{"error":{"message":"Request too large"}}""")
              else -> { r.begin(200); r.write("data: {\"choices\":[{\"delta\":{\"content\":\"part\"}}]}\n\ndata: {\"error\":{\"message\":\"broken\",\"code\":500}}\n\n") }
            }
          } else if (mode == "recovery") r.reply(200, ok)
          else { r.begin(200); r.write("data: {\"choices\":[{\"delta\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n") }
        }
        val group = object : dev.antigravity.fluidengine.ai.tools.AiToolGroup {
          override val id = "fixture"
          override val statusKey = "fixture"
          override val hint = "fixture"
        }
        val router = dev.antigravity.fluidengine.ai.orchestrator.AiRouter(listOf(group), null, "fixture", listOf(group))
        val engine = dev.antigravity.fluidengine.ai.orchestrator.AiOrchestrator(
          dev.antigravity.fluidengine.ai.tools.ToolRegistry<Unit>(emptyList(), listOf(group)), router,
          dev.antigravity.fluidengine.ai.orchestrator.AiDiagnosticsLog())
        val input = dev.antigravity.fluidengine.ai.orchestrator.AskInput(
          question = "hi", mode = dev.antigravity.fluidengine.ai.orchestrator.AskMode.TEXT, language = "en",
          settings = dev.antigravity.fluidengine.ai.keys.AiSettings(), providers = listOf(ReadyProvider(provider(server), "exact", "stt", "router")),
          toolContext = Unit, systemPrompt = "fixture", conversation = dev.antigravity.fluidengine.ai.orchestrator.Conversation(1L, 0L),
          actionsEnabled = false, preselectedGroups = setOf(group), pinProvider = true)
        val result = withContext(ProviderExecutionContext("job", mode, ledger)) {
          engine.ask(input, MutableStateFlow(dev.antigravity.fluidengine.ai.orchestrator.AssistantState.Idle))
        }
        assertEquals(mode, "ok", result.answer)
        assertEquals(mode, 2, calls.get())
        assertEquals(mode, 2, ledger.settled.size)
        assertEquals(mode, 2, ledger.admitted.map { it.attemptId }.toSet().size)
        assertEquals(mode, ProviderAttemptOutcome.FAILED, ledger.settled.first().outcome)
        assertEquals(mode, ProviderAttemptOutcome.SUCCESS, ledger.settled.last().outcome)
        assertEquals(mode, mode != "recovery", ledger.admitted.last().streaming)
      }
    }
  }

  @Test fun rejectionHeadersAndMalformedJsonRetainResponseCertainty() = runBlocking {
    TinyHttpServer().use { server ->
      val ledger = Ledger()
      server.handle("/chat/completions") { _, r -> r.reply(200, "bad json", mapOf("x-ratelimit-remaining-requests" to "7")) }
      try { provider(server).complete(request(ledger)); fail() } catch (_: AiError.Parse) {}
      assertEquals(200, ledger.settled.single().httpStatus)
      assertEquals(7, ledger.settled.single().rateLimit.remainingRequests)
      assertEquals(ProviderDispatchCertainty.RESPONSE_RECEIVED, ledger.settled.single().certainty)
    }
  }
}
