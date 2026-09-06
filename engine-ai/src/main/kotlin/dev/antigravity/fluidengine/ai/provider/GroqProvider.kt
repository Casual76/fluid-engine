package dev.antigravity.fluidengine.ai.provider

import dev.antigravity.fluidengine.ai.net.AiHttp
import dev.antigravity.fluidengine.ai.net.get
import dev.antigravity.fluidengine.ai.net.string
import java.io.File
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put

/**
 * Groq: chat completions OpenAI-compatible con `reasoning_effort` e `reasoning_format`, Whisper in
 * multipart, catalogo in `GET /models`. Free tier: 30 richieste e 8K token al minuto per modello —
 * il motivo di meta' delle scelte dell'orchestratore.
 */
open class GroqProvider(http: AiHttp, apiKey: String) : OpenAiCompatProvider(http, BASE_URL, apiKey) {

  override val id: ProviderId = ProviderId.GROQ

  /**
   * La ricerca web su Groq e' un altro modello: il compound cerca da se' e non accetta strumenti
   * ne' ragionamento configurabile. Chi la chiede lo fa in una chiamata senza tool, quindi qui
   * si cambia solo il modello e si spegne il resto.
   */
  override fun effectiveRequest(request: ChatRequest): ChatRequest =
    if (request.webSearch) request.copy(model = SEARCH_MODEL, tools = emptyList(), reasoning = ReasoningLevel.NONE) else request

  override fun JsonObjectBuilder.providerFields(request: ChatRequest, stream: Boolean, dropped: Set<String>) {
    if ("reasoning_effort" !in dropped) {
      put(
        "reasoning_effort",
        when (request.reasoning) {
          ReasoningLevel.NONE -> "none"
          ReasoningLevel.LOW -> "low"
          ReasoningLevel.MEDIUM -> "medium"
          ReasoningLevel.HIGH -> "high"
        },
      )
    }
    // Con i tool Groq accetta solo parsed o hidden; il ragionamento non si mostra mai, quindi hidden.
    if (request.reasoning != ReasoningLevel.NONE && "reasoning_format" !in dropped) put("reasoning_format", "hidden")
  }

  override suspend fun listModels(): ModelCatalogue {
    val response = http.getJson("$BASE_URL/models", headers())
    return ModelCatalog.groq(response.body)
  }

  override suspend fun transcribe(audio: File, mime: String, options: TranscribeOptions): Transcript {
    val fields = linkedMapOf("model" to options.model, "response_format" to "json", "temperature" to "0")
    options.language?.let { fields["language"] = it }
    options.prompt?.takeIf { it.isNotBlank() }?.let { fields["prompt"] = it.take(PROMPT_MAX_CHARS) }
    val response = http.postMultipart(
      url = "$BASE_URL/audio/transcriptions",
      headers = headers(),
      fields = fields,
      file = audio,
      fileMime = mime,
      fileName = audio.name,
    )
    return Transcript(text = response.body["text"].string()?.trim().orEmpty(), language = options.language)
  }

  companion object {
    const val BASE_URL = "https://api.groq.com/openai/v1"

    /** Il sistema compound di Groq che cerca sul web da solo; il piccolo basta per cinque fonti. */
    const val SEARCH_MODEL = "groq/compound-mini"

    /** Il prompt di Whisper vale al massimo 224 token: 800 caratteri di nomi di posti ci stanno. */
    const val PROMPT_MAX_CHARS = 800
  }
}
