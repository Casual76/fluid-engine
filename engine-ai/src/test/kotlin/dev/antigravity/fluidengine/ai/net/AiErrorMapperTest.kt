package dev.antigravity.fluidengine.ai.net

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiErrorMapperTest {

  private fun headers(vararg pairs: Pair<String, String>): Map<String?, List<String>> =
    pairs.associate { (k, v) -> k as String? to listOf(v) }

  @Test
  fun `il 429 di Groq legge retry-after dall'header e i limiti dagli x-ratelimit`() {
    val error = AiErrorMapper.map(
      429,
      headers(
        "Retry-After" to "3",
        "x-ratelimit-limit-tokens" to "8000",
        "x-ratelimit-remaining-tokens" to "120",
        "x-ratelimit-reset-tokens" to "2m59.56s",
        "x-ratelimit-remaining-requests" to "29",
      ),
      """{"error":{"message":"Rate limit reached for model qwen. Please try again in 7.66s","type":"tokens"}}""",
    ) as AiError.RateLimited
    assertEquals(3.0, error.retryAfterSec!!, 1e-9)
    assertEquals(8000, error.rateLimit.limitTokens)
    assertEquals(120, error.rateLimit.remainingTokens)
    assertEquals(29, error.rateLimit.remainingRequests)
    assertEquals(179.56, error.rateLimit.resetTokensSec!!, 1e-6)
    assertFalse(error.freeModelCap)
  }

  @Test
  fun `senza header il retry viene dalla frase di Groq`() {
    val error = AiErrorMapper.map(
      429,
      emptyMap(),
      """{"error":{"message":"Rate limit reached. Please try again in 1m2.5s."}}""",
    ) as AiError.RateLimited
    assertEquals(62.5, error.retryAfterSec!!, 1e-9)
  }

  @Test
  fun `Gemini mette il ritardo in RetryInfo`() {
    val body = """{"error":{"code":429,"message":"Resource has been exhausted","status":"RESOURCE_EXHAUSTED",
      "details":[{"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"37s"}]}}"""
    val error = AiErrorMapper.map(429, emptyMap(), body) as AiError.RateLimited
    assertEquals(37.0, error.retryAfterSec!!, 1e-9)
  }

  @Test
  fun `il tetto giornaliero dei modelli gratuiti di OpenRouter si riconosce`() {
    val body = """{"error":{"code":429,"message":"Rate limit exceeded: free-models-per-day. Add 10 credits to unlock 1000 free model requests per day","metadata":{"headers":{}}}}"""
    val error = AiErrorMapper.map(429, emptyMap(), body) as AiError.RateLimited
    assertTrue(error.freeModelCap)
  }

  @Test
  fun `chiave sbagliata in tutte le forme`() {
    assertTrue(AiErrorMapper.map(401, emptyMap(), """{"error":{"message":"Invalid API Key"}}""") is AiError.Unauthorized)
    assertTrue(AiErrorMapper.map(403, emptyMap(), "") is AiError.Unauthorized)
    val gemini = """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}"""
    assertTrue(AiErrorMapper.map(400, emptyMap(), gemini) is AiError.Unauthorized)
  }

  @Test
  fun `400 generico, 404, 5xx e 408 nelle loro categorie`() {
    assertTrue(AiErrorMapper.map(400, emptyMap(), """{"error":{"message":"reasoning_effort not supported"}}""") is AiError.BadRequest)
    assertTrue(AiErrorMapper.map(404, emptyMap(), "") is AiError.BadRequest)
    assertTrue(AiErrorMapper.map(503, emptyMap(), "") is AiError.Server)
    assertTrue(AiErrorMapper.map(408, emptyMap(), "") is AiError.Server)
    assertEquals("HTTP 502", (AiErrorMapper.map(502, emptyMap(), "   ") as AiError.Server).message)
  }

  @Test
  fun `le durate di Groq si sommano`() {
    assertEquals(3723.5, AiErrorMapper.parseDuration("1h2m3.5s")!!, 1e-9)
    assertEquals(0.25, AiErrorMapper.parseDuration("250ms")!!, 1e-9)
    assertEquals(7.66, AiErrorMapper.parseDuration("7.66s")!!, 1e-9)
    assertNull(AiErrorMapper.parseDuration("presto"))
  }

  @Test
  fun `un reset di OpenRouter in millisecondi epoch diventa secondi da adesso`() {
    val now = 1_700_000_000_000L
    val info = AiErrorMapper.parseRateLimit(mapOf("x-ratelimit-reset-requests" to (now + 45_000).toString()), now)
    assertEquals(45.0, info.resetRequestsSec!!, 1e-9)
  }

  @Test
  fun `le eccezioni di rete si incartano e le cancellazioni no`() {
    assertTrue(AiErrorMapper.wrap(SocketTimeoutException()) is AiError.Timeout)
    assertTrue(AiErrorMapper.wrap(UnknownHostException("api")) is AiError.Network)
    val cancellation = kotlinx.coroutines.CancellationException("stop")
    assertTrue(AiErrorMapper.wrap(cancellation) === cancellation)
  }

  // --- 2.8.0: il perche' di un 400, sui corpi veri dei tre provider ---

  private fun bad(code: Int, body: String): AiError.BadRequest {
    val error = AiErrorMapper.map(code, emptyMap(), body)
    assertTrue("atteso BadRequest, avuto $error", error is AiError.BadRequest)
    return error as AiError.BadRequest
  }

  @Test
  fun `Groq tool_use_failed e' un campionamento andato male, con il codice nei dettagli`() {
    val body = """{"error":{"message":"Failed to call a function. Please adjust your prompt. See 'failed_generation' for more details.",
      "type":"invalid_request_error","code":"tool_use_failed","failed_generation":"<function=meteo_ora{\"luogo\": \"Bologna\"}</function>"}}"""
    val error = bad(400, body)
    assertEquals(BadReason.TOOL_USE_FAILED, error.reason)
    assertEquals(400, error.httpCode)
    assertTrue(error.providerMessage!!.startsWith("tool_use_failed: Failed to call a function"))
    // La variante della validazione dello schema ha lo stesso codice.
    val schema = """{"error":{"message":"tool call validation failed: parameters for tool meteo_ora did not match schema: errors: [missing properties: 'luogo']","type":"invalid_request_error","code":"tool_use_failed"}}"""
    assertEquals(BadReason.TOOL_USE_FAILED, bad(400, schema).reason)
  }

  @Test
  fun `un modello sparito si riconosce nei tre dialetti`() {
    val decommissioned = """{"error":{"message":"The model `llama3-70b-8192` has been decommissioned and is no longer supported. Please refer to https://console.groq.com/docs/deprecations for a recommendation on which model to use instead.","type":"invalid_request_error","code":"model_decommissioned"}}"""
    assertEquals(BadReason.MODEL_UNAVAILABLE, bad(400, decommissioned).reason)
    val notFound = """{"error":{"message":"The model `qwen/qwen9` does not exist or you do not have access to it.","type":"invalid_request_error","code":"model_not_found"}}"""
    assertEquals(BadReason.MODEL_UNAVAILABLE, bad(404, notFound).reason)
    val gemini = """{"error":{"code":404,"message":"models/gemini-1.5-pro-001 is not found for API version v1beta, or is not supported for generateContent. Call ListModels to see the list of available models and their supported methods.","status":"NOT_FOUND"}}"""
    val geminiError = bad(404, gemini)
    assertEquals(BadReason.MODEL_UNAVAILABLE, geminiError.reason)
    assertTrue(geminiError.providerMessage!!.startsWith("NOT_FOUND: models/gemini-1.5-pro-001"))
    val noEndpoints = """{"error":{"message":"No endpoints found for mistralai/mistral-7b-instruct:free.","code":404},"user_id":"user_2abc"}"""
    assertEquals(BadReason.MODEL_UNAVAILABLE, bad(404, noEndpoints).reason)
    val invalidId = """{"error":{"message":"deepseek/deepseek-r9:free is not a valid model ID","code":400},"user_id":"user_2abc"}"""
    assertEquals(BadReason.MODEL_UNAVAILABLE, bad(400, invalidId).reason)
  }

  @Test
  fun `una richiesta che non ci sta, compreso il 413 per minuto di Groq che non e' un 429`() {
    val tpm = """{"error":{"message":"Request too large for model `qwen/qwen3-32b` in organization `org_01abc` service tier `on_demand` on tokens per minute (TPM): Limit 6000, Requested 9837, please reduce your message size and try again.","type":"tokens","code":"rate_limit_exceeded"}}"""
    assertEquals(BadReason.CONTEXT_TOO_LONG, bad(413, tpm).reason)
    val groq = """{"error":{"message":"Please reduce the length of the messages or completion.","type":"invalid_request_error","param":"messages","code":"context_length_exceeded"}}"""
    assertEquals(BadReason.CONTEXT_TOO_LONG, bad(400, groq).reason)
    val gemini = """{"error":{"code":400,"message":"The input token count (1200000) exceeds the maximum number of tokens allowed (1048576).","status":"INVALID_ARGUMENT"}}"""
    assertEquals(BadReason.CONTEXT_TOO_LONG, bad(400, gemini).reason)
    val openRouter = """{"error":{"message":"This endpoint's maximum context length is 131072 tokens. However, you requested about 180000 tokens (178000 of text input, 2000 in the output). Please reduce the length of either one, or use the \"middle-out\" transform to compress your prompt automatically.","code":400,"metadata":{"provider_name":null}}}"""
    assertEquals(BadReason.CONTEXT_TOO_LONG, bad(400, openRouter).reason)
  }

  @Test
  fun `l'errore a valle di OpenRouter si legge in metadata raw`() {
    val body = """{"error":{"message":"Provider returned error","code":400,"metadata":{"raw":"{\"error\":{\"message\":\"Failed to call a function. Please adjust your prompt.\",\"type\":\"invalid_request_error\",\"code\":\"tool_use_failed\"}}","provider_name":"Groq"}}}"""
    val error = bad(400, body)
    assertEquals(BadReason.TOOL_USE_FAILED, error.reason)
    assertTrue(error.providerMessage!!.startsWith("Provider returned error ("))
  }

  @Test
  fun `un 400 che non si sa leggere resta generico, e la moderazione non e' una chiave sbagliata`() {
    val gemini = """{"error":{"code":400,"message":"Invalid JSON payload received. Unknown name \"foo\" at 'generation_config': Cannot find field.","status":"INVALID_ARGUMENT"}}"""
    assertEquals(BadReason.GENERIC, bad(400, gemini).reason)
    assertEquals(BadReason.GENERIC, bad(404, "").reason)
    val moderation = """{"error":{"code":403,"message":"meta-llama/llama-guard requires moderation on OpenAI. Your input was flagged for \"violence\"","metadata":{"reasons":["violence"],"flagged_input":"...","provider_name":"OpenAI","model_slug":"x"}}}"""
    assertEquals(BadReason.BLOCKED, bad(403, moderation).reason)
    assertTrue(AiErrorMapper.map(403, emptyMap(), """{"error":{"message":"Forbidden"}}""") is AiError.Unauthorized)
    // La convenzione di prima regge: un "bloccato: ..." costruito a mano vale BLOCKED.
    assertEquals(BadReason.BLOCKED, AiError.BadRequest(200, "bloccato: SAFETY").reason)
  }

  @Test
  fun `codice e messaggio del provider per i Dettagli, corti e senza chiavi`() {
    val unauthorized = AiErrorMapper.map(400, emptyMap(), """{"error":{"message":"Incorrect API key provided: sk-or-v1-abcdef1234567890abcdef. Bearer gsk_abcdefghijklmnop"}}""")
    assertTrue(unauthorized is AiError.Unauthorized)
    assertEquals(400, unauthorized.httpCode)
    val details = unauthorized.providerMessage!!
    assertFalse(details.contains("abcdef1234567890"))
    assertFalse(details.contains("gsk_abcdefghijklmnop"))
    assertTrue(details.contains("***"))
    val long = AiErrorMapper.map(500, emptyMap(), """{"error":{"message":"${"x".repeat(900)}"}}""")
    assertEquals(500, long.httpCode)
    assertTrue(long.providerMessage!!.length <= AiErrorMapper.PROVIDER_MESSAGE_CHARS)
    val limited = AiErrorMapper.map(429, emptyMap(), """{"error":{"message":"slow down"}}""")
    assertEquals(429, limited.httpCode)
    assertEquals("slow down", limited.providerMessage)
    // Gli errori che non vengono dal provider non hanno ne' codice ne' frase del provider.
    assertNull(AiError.Timeout("t").httpCode)
    assertNull(AiError.Network("n").providerMessage)
  }

  @Test
  fun `un errore dentro uno stream aperto si legge come uno HTTP`() {
    val toolUse = kotlinx.serialization.json.Json.parseToJsonElement(
      """{"message":"Failed to call a function. Please adjust your prompt.","type":"invalid_request_error","code":"tool_use_failed"}""",
    )
    val error = AiErrorMapper.inBandError(toolUse, "errore")
    assertTrue(error is AiError.BadRequest && error.reason == BadReason.TOOL_USE_FAILED)
    assertEquals(200, error.httpCode)
    // Un errore qualunque resta un errore del server: una riprova, come prima.
    val generic = AiErrorMapper.inBandError(kotlinx.serialization.json.Json.parseToJsonElement("""{"message":"overloaded"}"""), "errore")
    assertTrue(generic is AiError.Server)
    assertEquals("errore", AiErrorMapper.inBandError(null, "errore").message)
    // E il codec di Groq lo usa: il tool_use_failed a stream aperto non e' piu' un 5xx.
    val chunk = """{"error":{"message":"Failed to call a function.","type":"invalid_request_error","code":"tool_use_failed"}}"""
    val thrown = runCatching { dev.antigravity.fluidengine.ai.provider.OpenAiCompatCodec.parseStreamChunk(chunk, dev.antigravity.fluidengine.ai.provider.OpenAiCompatCodec.StreamState()) }.exceptionOrNull()
    assertTrue("atteso BadRequest, avuto $thrown", thrown is AiError.BadRequest && thrown.reason == BadReason.TOOL_USE_FAILED)
  }
}
