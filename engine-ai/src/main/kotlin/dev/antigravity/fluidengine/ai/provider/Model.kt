package dev.antigravity.fluidengine.ai.provider

import dev.antigravity.fluidengine.ai.net.RateLimitInfo
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Un pezzo di un messaggio dell'utente: testo, un'immagine, un documento. Le immagini e i
 * documenti viaggiano in base64 dentro la richiesta (nessun provider dei tre offre un upload a
 * parte che valga la pena): chi li mette qui ha gia' deciso che sono abbastanza piccoli.
 */
sealed interface ContentPart {
  data class Text(val text: String) : ContentPart

  /** Un'immagine (JPEG o PNG) per un modello che vede. */
  class Image(val bytes: ByteArray, val mime: String) : ContentPart

  /** Un documento (di regola un PDF) per un modello, o un provider, che legge i documenti. */
  class Document(val bytes: ByteArray, val mime: String, val name: String) : ContentPart

  val isText: Boolean get() = this is Text
}

/**
 * Il modello neutro della conversazione: quello che l'orchestratore vede, qualunque provider ci
 * sia sotto. Gli adapter lo traducono nel dialetto di ciascuno (OpenAI per Groq e OpenRouter,
 * `contents/parts` per Gemini) e ne conservano in [Message.Assistant.raw] cio' che il provider
 * pretende di rivedere identico al turno dopo (le thought signature di Gemini, i
 * `reasoning_details` di OpenRouter).
 */
sealed interface Message {
  data class System(val text: String) : Message

  /** Il messaggio dell'utente: una lista di parti, che nel caso comune e' un testo solo. */
  data class User(val parts: List<ContentPart>) : Message {
    constructor(text: String) : this(listOf(ContentPart.Text(text)))

    /** Il solo testo, per chi non sa che fare di immagini e documenti (storia, stime di token). */
    val text: String get() = parts.filterIsInstance<ContentPart.Text>().joinToString("\n") { it.text }

    val hasBinaryParts: Boolean get() = parts.any { !it.isText }
  }

  data class Assistant(
    val text: String?,
    val toolCalls: List<ToolCall> = emptyList(),
    /** Le parti grezze del provider che le ha prodotte, da rimandare intatte; null se non servono. */
    val raw: JsonElement? = null,
    val rawProvider: ProviderId? = null,
  ) : Message

  data class ToolResult(val callId: String, val name: String, val content: String) : Message
}

data class ToolCall(
  val id: String,
  val name: String,
  val arguments: JsonObject,
  /** Gemini 3: la firma del pensiero che ha prodotto la chiamata; va rimandata verbatim. */
  val thoughtSignature: String? = null,
)

/** Una funzione offerta al modello: nome, cosa fa, e lo schema JSON dei parametri. */
data class ToolSpec(val name: String, val description: String, val parameters: JsonObject)

sealed interface ToolChoice {
  data object Auto : ToolChoice
  data object None : ToolChoice
  data object Required : ToolChoice
  data class Named(val name: String) : ToolChoice
}

/** Quanto pensare in questa chiamata; ogni adapter lo mappa sui suoi parametri. */
enum class ReasoningLevel { NONE, LOW, MEDIUM, HIGH }

/**
 * I tre livelli di modello di un provider, scelti in base al compito e non una volta per tutte:
 * [ROUTER] e' il modello piccolo e veloce che sceglie i gruppi di strumenti; [CHAT] risponde e
 * chiama i tool; [DEEP] entra quando servono documenti, immagini o un contesto grande.
 */
enum class ModelTier { ROUTER, CHAT, DEEP }

/** Cosa un modello sa prendere in ingresso oltre al testo. */
data class ModelCapabilities(val vision: Boolean, val documents: Boolean) {
  fun accepts(part: ContentPart): Boolean = when (part) {
    is ContentPart.Text -> true
    is ContentPart.Image -> vision
    is ContentPart.Document -> documents
  }

  companion object {
    val TEXT_ONLY = ModelCapabilities(vision = false, documents = false)
  }
}

data class ChatRequest(
  val model: String,
  val messages: List<Message>,
  val tools: List<ToolSpec> = emptyList(),
  val toolChoice: ToolChoice = ToolChoice.Auto,
  val parallelToolCalls: Boolean = true,
  val reasoning: ReasoningLevel = ReasoningLevel.MEDIUM,
  /** Uscita strutturata (stadio 1): lo schema JSON della risposta attesa. */
  val jsonSchema: JsonObject? = null,
  val maxOutputTokens: Int? = null,
  val temperature: Double? = null,
) {
  /** Vero se qualche messaggio porta un documento: OpenRouter allora vuole il plugin che li legge. */
  val hasDocuments: Boolean get() = messages.any { it is Message.User && it.parts.any { p -> p is ContentPart.Document } }
}

enum class FinishReason { STOP, TOOL_CALLS, LENGTH, BLOCKED, OTHER }

data class Usage(
  val promptTokens: Int,
  val completionTokens: Int,
  val totalTokens: Int,
  /** Solo OpenRouter lo dice (in dollari); gli altri sono gratuiti o non lo espongono. */
  val costUsd: Double? = null,
) {
  operator fun plus(other: Usage): Usage = Usage(
    promptTokens = promptTokens + other.promptTokens,
    completionTokens = completionTokens + other.completionTokens,
    totalTokens = totalTokens + other.totalTokens,
    costUsd = if (costUsd == null && other.costUsd == null) null else (costUsd ?: 0.0) + (other.costUsd ?: 0.0),
  )
}

data class ChatTurn(
  val message: Message.Assistant,
  val finishReason: FinishReason,
  val usage: Usage?,
  val rateLimit: RateLimitInfo,
)

/** I pezzi di uno stream, nell'ordine in cui arrivano. */
sealed interface ChatDelta {
  data class Text(val text: String) : ChatDelta

  /** Un frammento di tool call; l'indice tiene insieme i pezzi della stessa chiamata. */
  data class ToolCallPart(
    val index: Int,
    val id: String?,
    val name: String?,
    val argumentsFragment: String?,
    val thoughtSignature: String? = null,
  ) : ChatDelta

  /** Parti grezze del provider da conservare (thought signature, reasoning_details). */
  data class Raw(val raw: JsonElement) : ChatDelta

  data class Finish(val reason: FinishReason, val usage: Usage?, val rateLimit: RateLimitInfo) : ChatDelta
}

data class Transcript(val text: String, val language: String?)

data class TranscribeOptions(val model: String, val language: String?, val prompt: String?)

enum class ModelKind { CHAT, STT, OTHER }

/** Quello che serve per scegliere un modello in un elenco; i prezzi solo dove esistono. */
data class ModelInfo(
  val id: String,
  val displayName: String,
  val kind: ModelKind,
  val contextWindow: Int? = null,
  val maxOutputTokens: Int? = null,
  val supportsTools: Boolean = true,
  val supportsReasoning: Boolean = false,
  val audioInput: Boolean = false,
  /** Vede le immagini. Dai cataloghi dove lo dicono, da un'euristica sul nome altrove. */
  val supportsVision: Boolean = false,
  /** Legge un documento allegato (Gemini in linea, OpenRouter col suo parser); Groq mai. */
  val supportsDocuments: Boolean = false,
  val free: Boolean = false,
  /** Dollari per milione di token, in ingresso e in uscita; null se il provider non prezza. */
  val pricePromptPerM: Double? = null,
  val priceCompletionPerM: Double? = null,
) {
  val capabilities: ModelCapabilities get() = ModelCapabilities(supportsVision, supportsDocuments)
}

data class ModelCatalogue(val chat: List<ModelInfo>, val stt: List<ModelInfo>) {
  fun chat(id: String): ModelInfo? = chat.firstOrNull { it.id == id }
}

interface ChatProvider {
  val id: ProviderId

  suspend fun complete(request: ChatRequest): ChatTurn

  fun stream(request: ChatRequest): Flow<ChatDelta>

  suspend fun listModels(): ModelCatalogue

  suspend fun transcribe(audio: File, mime: String, options: TranscribeOptions): Transcript
}
