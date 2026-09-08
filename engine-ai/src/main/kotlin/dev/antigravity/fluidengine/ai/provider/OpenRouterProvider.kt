package dev.antigravity.fluidengine.ai.provider

import dev.antigravity.fluidengine.ai.net.AiHttp
import dev.antigravity.fluidengine.ai.net.double
import dev.antigravity.fluidengine.ai.net.get
import dev.antigravity.fluidengine.ai.net.string
import java.io.File
import java.util.Base64
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Cosa `GET /key` dice della chiave: crediti, uso, se e' un account senza acquisti. */
data class OpenRouterKeyInfo(
  val label: String?,
  val limitUsd: Double?,
  val limitRemainingUsd: Double?,
  val usageUsd: Double?,
  val usageDailyUsd: Double?,
  val isFreeTier: Boolean,
)

/**
 * Cosa dire a OpenRouter sull'addestramento, quando si manda una domanda.
 *
 * L'account ha gia' la sua politica, scelta dall'utente su openrouter.ai: e' quella che decide a
 * quali endpoint la chiave puo' arrivare. Un'app che manda `data_collection: "deny"` a ogni
 * richiesta non la rende piu' sicura -- la **sovrascrive con la piu' stretta**, e su un modello i
 * cui endpoint gratuiti registrano i prompt il conto e' zero endpoint disponibili: "No endpoints
 * found matching your data policy", e il modello smette di funzionare senza che nessuno abbia
 * cambiato niente.
 */
enum class OpenRouterDataPolicy {
  /** Non si dice niente: vale quella dell'account, cioe' la scelta che l'utente ha gia' fatto. */
  ACCOUNT,

  /** Si chiede esplicitamente `deny`: piu' stretta dell'account, e taglia fuori chi registra. */
  DENY,
}

/**
 * OpenRouter: lo stesso dialetto di Groq piu' il routing (`models` di riserva, `provider` con la
 * privacy), il `reasoning` unificato, l'uso col costo. Le trascrizioni viaggiano in JSON base64
 * su `/audio/transcriptions`. Il catalogo e' pubblico e grande: lo legge [ModelCatalog].
 */
open class OpenRouterProvider(
  http: AiHttp,
  apiKey: String,
  private val referer: String,
  private val title: String,
  /** Fino a due modelli che OpenRouter prova da solo se il primario fallisce. */
  private val fallbackModels: List<String> = emptyList(),
  private val dataPolicy: OpenRouterDataPolicy = OpenRouterDataPolicy.ACCOUNT,
) : OpenAiCompatProvider(http, BASE_URL, apiKey) {

  override val id: ProviderId = ProviderId.OPENROUTER

  override val extraHeaders: Map<String, String> = mapOf("HTTP-Referer" to referer, "X-Title" to title)

  override val supportsFileParts: Boolean = true

  override fun JsonObjectBuilder.providerFields(request: ChatRequest, stream: Boolean, dropped: Set<String>) {
    if (fallbackModels.isNotEmpty()) {
      put("models", buildJsonArray { (listOf(request.model) + fallbackModels).distinct().forEach { add(JsonPrimitive(it)) } })
    }
    // Un PDF nel messaggio: il parser di OpenRouter lo trasforma in testo per qualsiasi modello.
    // `pdf-text` e' il motore gratuito; per le scansioni ci pensa l'app con le immagini.
    // La ricerca web e' un altro plugin: OpenRouter cerca, mette i risultati nel prompt e cita.
    if (request.hasDocuments || request.webSearch) {
      put(
        "plugins",
        buildJsonArray {
          if (request.hasDocuments) add(buildJsonObject { put("id", "file-parser"); put("pdf", buildJsonObject { put("engine", "pdf-text") }) })
          if (request.webSearch) add(buildJsonObject { put("id", "web"); put("max_results", request.webSearchMaxResults) })
        },
      )
    }
    put(
      "provider",
      buildJsonObject {
        // Solo la richiesta piu' stretta si manda: "allow" non serve dirlo (l'account lo dice gia'),
        // e "deny" imposto sempre e comunque e' quello che lasciava senza endpoint.
        if (dataPolicy == OpenRouterDataPolicy.DENY) put("data_collection", "deny")
        put("sort", "price")
      },
    )
    put("usage", buildJsonObject { put("include", true) })
    if ("reasoning" !in dropped) {
      put(
        "reasoning",
        buildJsonObject {
          put(
            "effort",
            when (request.reasoning) {
              ReasoningLevel.NONE -> "none"
              ReasoningLevel.LOW -> "low"
              ReasoningLevel.MEDIUM -> "medium"
              ReasoningLevel.HIGH -> "high"
            },
          )
          // Il testo del ragionamento non serve a nessuno qui: solo i dettagli da rimandare.
          put("exclude", true)
        },
      )
    }
  }

  override suspend fun listModels(): ModelCatalogue {
    val response = http.getJson("$BASE_URL/models", headers())
    return ModelCatalog.openRouter(response.body)
  }

  suspend fun keyInfo(): OpenRouterKeyInfo {
    val data = http.getJson("$BASE_URL/key", headers()).body["data"]
    return OpenRouterKeyInfo(
      label = data["label"].string(),
      limitUsd = data["limit"].double(),
      limitRemainingUsd = data["limit_remaining"].double(),
      usageUsd = data["usage"].double(),
      usageDailyUsd = data["usage_daily"].double(),
      isFreeTier = data["is_free_tier"].string()?.toBooleanStrictOrNull() ?: (data["is_free_tier"].double()?.let { it != 0.0 } ?: true),
    )
  }

  override suspend fun transcribe(audio: File, mime: String, options: TranscribeOptions): Transcript {
    val format = when {
      mime.contains("wav") -> "wav"
      mime.contains("mp4") || mime.contains("m4a") -> "m4a"
      mime.contains("ogg") -> "ogg"
      mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
      else -> "wav"
    }
    val body = buildJsonObject {
      put("model", options.model)
      put(
        "input_audio",
        buildJsonObject {
          put("data", Base64.getEncoder().encodeToString(audio.readBytes()))
          put("format", format)
        },
      )
      options.language?.let { put("language", it) }
      put("temperature", 0)
      put("response_format", "json")
    }
    val response = http.postJson("$BASE_URL/audio/transcriptions", headers(), body)
    return Transcript(text = response.body["text"].string()?.trim().orEmpty(), language = options.language)
  }

  companion object {
    const val BASE_URL = "https://openrouter.ai/api/v1"
  }
}
