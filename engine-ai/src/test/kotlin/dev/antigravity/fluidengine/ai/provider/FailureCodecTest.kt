package dev.antigravity.fluidengine.ai.provider

import dev.antigravity.fluidengine.ai.net.AiError
import dev.antigravity.fluidengine.ai.net.BadReason
import dev.antigravity.fluidengine.ai.net.RateLimitInfo
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Come i codec consegnano all'orchestratore le risposte andate male (2.8.0). */
class FailureCodecTest {

  @Test
  fun `la chiamata malformata di Gemini e' una risposta vuota con fine OTHER, senza frase segnaposto`() {
    val body = Json.parseToJsonElement("""{"candidates":[{"content":{"parts":[]},"finishReason":"MALFORMED_FUNCTION_CALL"}]}""")
    val turn = GeminiCodec.parseResponse(body, RateLimitInfo.EMPTY)
    assertNull(turn.message.text)
    assertTrue(turn.message.toolCalls.isEmpty())
    assertEquals(FinishReason.OTHER, turn.finishReason)
    // Lo stesso nello stream.
    val state = GeminiCodec.StreamState()
    GeminiCodec.parseStreamChunk("""{"candidates":[{"content":{"parts":[]},"finishReason":"MALFORMED_FUNCTION_CALL"}]}""", state)
    assertEquals(FinishReason.OTHER, state.finish)
  }

  @Test
  fun `un blocco di Gemini porta la sua ragione, e un errore a stream aperto si classifica`() {
    val blocked = runCatching { GeminiCodec.parseResponse(Json.parseToJsonElement("""{"promptFeedback":{"blockReason":"SAFETY"}}"""), RateLimitInfo.EMPTY) }.exceptionOrNull()
    assertTrue(blocked is AiError.BadRequest && blocked.reason == BadReason.BLOCKED)
    val tooLong = runCatching {
      GeminiCodec.parseStreamChunk("""{"error":{"code":400,"message":"The input token count (1200000) exceeds the maximum number of tokens allowed (1048576).","status":"INVALID_ARGUMENT"}}""", GeminiCodec.StreamState())
    }.exceptionOrNull()
    assertTrue("atteso CONTEXT_TOO_LONG, avuto $tooLong", tooLong is AiError.BadRequest && tooLong.reason == BadReason.CONTEXT_TOO_LONG)
    assertEquals(400, (tooLong as AiError).httpCode)
    val overloaded = runCatching {
      GeminiCodec.parseStreamChunk("""{"error":{"code":503,"message":"The model is overloaded.","status":"UNAVAILABLE"}}""", GeminiCodec.StreamState())
    }.exceptionOrNull()
    assertTrue(overloaded is AiError.Server)
  }
}
