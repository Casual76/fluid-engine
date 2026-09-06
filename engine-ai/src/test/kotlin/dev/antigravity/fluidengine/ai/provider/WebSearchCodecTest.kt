package dev.antigravity.fluidengine.ai.provider

import dev.antigravity.fluidengine.ai.net.AiHttp
import dev.antigravity.fluidengine.ai.net.RateLimitInfo
import dev.antigravity.fluidengine.ai.net.asArray
import dev.antigravity.fluidengine.ai.net.at
import dev.antigravity.fluidengine.ai.net.get
import dev.antigravity.fluidengine.ai.net.string
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le sonde: le sottoclassi vedono i metodi protetti con cui i provider scrivono la richiesta. */
private class OpenRouterProbe : OpenRouterProvider(AiHttp("test"), "k", "https://example.test", "Test") {
  fun fields(request: ChatRequest) = buildJsonObject { providerFields(request, stream = false, dropped = emptySet()) }
}

private class GroqProbe : GroqProvider(AiHttp("test"), "k") {
  fun effective(request: ChatRequest) = effectiveRequest(request)
}

class WebSearchCodecTest {

  private val request = ChatRequest(model = "m", messages = listOf(Message.User("chi ha vinto ieri?")), tools = listOf(ToolSpec("x", "x", buildJsonObject { put("type", "object") })))

  @Test
  fun `OpenRouter chiede il plugin web solo con la ricerca, accanto al parser dei PDF`() {
    val probe = OpenRouterProbe()
    assertNull(probe.fields(request)["plugins"])
    val plugins = probe.fields(request.copy(webSearch = true, webSearchMaxResults = 3))["plugins"].asArray()
    assertEquals(1, plugins.size)
    assertEquals("web", plugins[0]["id"].string())
    assertEquals(3.0, plugins[0]["max_results"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content.toDouble() })
    val withPdf = request.copy(webSearch = true, messages = listOf(Message.User(listOf(ContentPart.Text("?"), ContentPart.Document(byteArrayOf(1), "application/pdf", "a.pdf")))))
    assertEquals(listOf("file-parser", "web"), probe.fields(withPdf)["plugins"].asArray().map { it["id"].string() })
  }

  @Test
  fun `Groq con la ricerca passa al compound, senza strumenti ne' ragionamento`() {
    val probe = GroqProbe()
    assertEquals(request, probe.effective(request))
    val effective = probe.effective(request.copy(webSearch = true))
    assertEquals(GroqProvider.SEARCH_MODEL, effective.model)
    assertTrue(effective.tools.isEmpty())
    assertEquals(ReasoningLevel.NONE, effective.reasoning)
  }

  @Test
  fun `le fonti OpenAI si leggono dalle annotations di OpenRouter e dai search_results di Groq, senza doppioni`() {
    val body = Json.parseToJsonElement(
      """{"choices":[{"message":{"role":"assistant","content":"Ha vinto la Juve.",
        "annotations":[{"type":"url_citation","url_citation":{"url":"https://a.it/1","title":"Gazzetta","content":"Juve 2-1"}},
                       {"type":"url_citation","url_citation":{"url":"https://a.it/1","title":"doppione"}}],
        "executed_tools":[{"type":"search","search_results":{"results":[{"title":"Corriere","url":"https://b.it/2","content":"..."},{"url":""}]}}]},
        "finish_reason":"stop"}],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}""",
    )
    val turn = OpenAiCompatCodec.parseCompletion(body, RateLimitInfo.EMPTY, ProviderId.OPENROUTER)
    assertEquals(listOf("https://a.it/1", "https://b.it/2"), turn.citations.map { it.url })
    assertEquals("Gazzetta", turn.citations[0].title)
    assertEquals("Juve 2-1", turn.citations[0].snippet)
    assertEquals("Corriere", turn.citations[1].title)
    assertTrue(OpenAiCompatCodec.parseCompletion(Json.parseToJsonElement("""{"choices":[{"message":{"content":"x"}}]}"""), RateLimitInfo.EMPTY, ProviderId.GROQ).citations.isEmpty())
  }

  @Test
  fun `nello stream OpenAI le fonti si accumulano fino alla fine`() {
    val state = OpenAiCompatCodec.StreamState()
    OpenAiCompatCodec.parseStreamChunk("""{"choices":[{"delta":{"content":"Ha vinto","annotations":[{"type":"url_citation","url_citation":{"url":"https://a.it/1","title":"A"}}]}}]}""", state)
    OpenAiCompatCodec.parseStreamChunk("""{"choices":[{"delta":{"content":" la Juve."},"finish_reason":"stop"}]}""", state)
    assertEquals(listOf(Citation("https://a.it/1", "A", null)), state.citations.values.toList())
  }

  @Test
  fun `le fonti di Gemini vengono dai grounding chunk del web`() {
    val body = Json.parseToJsonElement(
      """{"candidates":[{"content":{"role":"model","parts":[{"text":"Ha vinto la Juve."}]},"finishReason":"STOP",
        "groundingMetadata":{"groundingChunks":[{"web":{"uri":"https://g.it/1","title":"gazzetta.it"}},{"retrievedContext":{"uri":"x"}},{"web":{"uri":"https://g.it/1"}}]}}],
        "usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":5,"totalTokenCount":15}}""",
    )
    val turn = GeminiCodec.parseResponse(body, RateLimitInfo.EMPTY)
    assertEquals(listOf(Citation("https://g.it/1", "gazzetta.it")), turn.citations)
    val state = GeminiCodec.StreamState()
    GeminiCodec.parseStreamChunk("""{"candidates":[{"content":{"parts":[{"text":"Ha"}]},"groundingMetadata":{"groundingChunks":[{"web":{"uri":"https://g.it/2","title":"t"}}]}}]}""", state)
    GeminiCodec.parseStreamChunk("""{"candidates":[{"content":{"parts":[{"text":" vinto."}]},"finishReason":"STOP"}]}""", state)
    assertEquals(listOf("https://g.it/2"), state.citations.keys.toList())
    assertEquals(FinishReason.STOP, state.finish)
  }

  @Test
  fun `il codec OpenAI ignora un JsonObjectBuilder vuoto`() {
    // Una guardia sul tipo: le sonde costruiscono con lo stesso builder dei provider.
    val builder: JsonObjectBuilder.() -> Unit = { put("a", 1) }
    assertEquals(1.0, buildJsonObject(builder)["a"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content.toDouble() })
    assertTrue(GeminiCodec.citations(null).isEmpty())
    assertTrue(OpenAiCompatCodec.citations(null).isEmpty())
    assertNull(Json.parseToJsonElement("""{"a":[]}""")["a"].at(0))
  }
}
