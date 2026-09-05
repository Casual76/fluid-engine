package dev.antigravity.fluidengine.ai.provider

import dev.antigravity.fluidengine.ai.net.AiError
import dev.antigravity.fluidengine.ai.net.RateLimitInfo
import dev.antigravity.fluidengine.ai.net.asArray
import dev.antigravity.fluidengine.ai.net.at
import dev.antigravity.fluidengine.ai.net.double
import dev.antigravity.fluidengine.ai.net.get
import dev.antigravity.fluidengine.ai.net.string
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Il dialetto OpenAI delle chat completions, che Groq e OpenRouter parlano entrambi: messaggi,
 * tool, scelte, e le due letture (risposta intera, pezzo di stream). Puro, cosi' i test fissano
 * la forma esatta del JSON che parte e leggono i pezzi registrati che arrivano.
 */
object OpenAiCompatCodec {

  /**
   * [fileParts]: il provider accetta la parte `file` (OpenRouter, col suo parser). Dove non la
   * accetta, un documento diventa una riga di testo che dice che c'era: meglio di un 400.
   */
  fun messages(messages: List<Message>, fileParts: Boolean = false): JsonArray = buildJsonArray {
    messages.forEach { message ->
      add(
        when (message) {
          is Message.System -> buildJsonObject {
            put("role", "system")
            put("content", message.text)
          }
          is Message.User -> buildJsonObject {
            put("role", "user")
            if (message.hasBinaryParts) put("content", userParts(message.parts, fileParts)) else put("content", message.text)
          }
          is Message.Assistant -> buildJsonObject {
            put("role", "assistant")
            put("content", message.text?.let { JsonPrimitive(it) } ?: JsonNull)
            if (message.toolCalls.isNotEmpty()) {
              put(
                "tool_calls",
                buildJsonArray {
                  message.toolCalls.forEach { call ->
                    add(
                      buildJsonObject {
                        put("id", call.id)
                        put("type", "function")
                        put(
                          "function",
                          buildJsonObject {
                            put("name", call.name)
                            put("arguments", call.arguments.toString())
                          },
                        )
                      },
                    )
                  }
                },
              )
            }
            // OpenRouter vuole rivedere i suoi reasoning_details nei giri con tool.
            if (message.rawProvider == ProviderId.OPENROUTER) {
              (message.raw["reasoning_details"] as? JsonArray)?.let { put("reasoning_details", it) }
            }
          }
          is Message.ToolResult -> buildJsonObject {
            put("role", "tool")
            put("tool_call_id", message.callId)
            put("name", message.name)
            put("content", message.content)
          }
        },
      )
    }
  }

  private fun userParts(parts: List<ContentPart>, fileParts: Boolean): JsonArray = buildJsonArray {
    parts.forEach { part ->
      add(
        when (part) {
          is ContentPart.Text -> buildJsonObject { put("type", "text"); put("text", part.text) }
          is ContentPart.Image -> buildJsonObject {
            put("type", "image_url")
            put("image_url", buildJsonObject { put("url", dataUrl(part.mime, part.bytes)) })
          }
          is ContentPart.Document -> if (fileParts) {
            buildJsonObject {
              put("type", "file")
              put("file", buildJsonObject { put("filename", part.name); put("file_data", dataUrl(part.mime, part.bytes)) })
            }
          } else {
            buildJsonObject { put("type", "text"); put("text", "[allegato ${part.name}: questo modello non legge i documenti]") }
          }
        },
      )
    }
  }

  fun dataUrl(mime: String, bytes: ByteArray): String = "data:$mime;base64," + Base64.getEncoder().encodeToString(bytes)

  fun tools(tools: List<ToolSpec>): JsonArray = buildJsonArray {
    tools.forEach { tool ->
      add(
        buildJsonObject {
          put("type", "function")
          put(
            "function",
            buildJsonObject {
              put("name", tool.name)
              put("description", tool.description)
              put("parameters", tool.parameters)
            },
          )
        },
      )
    }
  }

  fun toolChoice(choice: ToolChoice): JsonElement = when (choice) {
    ToolChoice.Auto -> JsonPrimitive("auto")
    ToolChoice.None -> JsonPrimitive("none")
    ToolChoice.Required -> JsonPrimitive("required")
    is ToolChoice.Named -> buildJsonObject {
      put("type", "function")
      put("function", buildJsonObject { put("name", choice.name) })
    }
  }

  fun responseFormat(schema: JsonObject): JsonObject = buildJsonObject {
    put("type", "json_schema")
    put(
      "json_schema",
      buildJsonObject {
        put("name", "answer")
        put("schema", schema)
        put("strict", true)
      },
    )
  }

  fun finishReason(raw: String?, hasToolCalls: Boolean): FinishReason = when (raw) {
    "tool_calls", "function_call" -> FinishReason.TOOL_CALLS
    "length" -> FinishReason.LENGTH
    "content_filter" -> FinishReason.BLOCKED
    "stop", null -> if (hasToolCalls) FinishReason.TOOL_CALLS else FinishReason.STOP
    "error" -> FinishReason.OTHER
    else -> FinishReason.OTHER
  }

  fun usage(json: JsonElement?): Usage? {
    val node = json ?: return null
    val prompt = node["prompt_tokens"].double()?.toInt() ?: return null
    val completion = node["completion_tokens"].double()?.toInt() ?: 0
    val total = node["total_tokens"].double()?.toInt() ?: (prompt + completion)
    return Usage(prompt, completion, total, costUsd = node["cost"].double())
  }

  /** Una risposta intera: il primo `choice`, il suo messaggio, la ragione, l'uso. */
  fun parseCompletion(body: JsonElement?, rateLimit: RateLimitInfo, provider: ProviderId): ChatTurn {
    body["error"]?.let { throw AiError.Server(200, body["error"]["message"].string() ?: "errore del provider") }
    val choice = body["choices"].at(0) ?: throw AiError.Parse("nessun choice nella risposta")
    val message = choice["message"] ?: throw AiError.Parse("choice senza message")
    val calls = message["tool_calls"].asArray().mapIndexedNotNull { index, call ->
      val name = call["function"]["name"].string() ?: return@mapIndexedNotNull null
      ToolCall(
        id = call["id"].string()?.takeIf { it.isNotBlank() } ?: ToolCallAssembler.localCallId(),
        name = name,
        arguments = ToolCallAssembler.parseArguments(call["function"]["arguments"].string() ?: ""),
      ).also { if (index > 200) throw AiError.Parse("troppe tool call") }
    }
    val text = message["content"].string()?.takeIf { it.isNotEmpty() }
    return ChatTurn(
      message = Message.Assistant(
        text = text,
        toolCalls = calls,
        raw = message.takeIf { provider == ProviderId.OPENROUTER },
        rawProvider = provider.takeIf { provider == ProviderId.OPENROUTER },
      ),
      finishReason = finishReason(choice["finish_reason"].string(), calls.isNotEmpty()),
      usage = usage(body["usage"]),
      rateLimit = rateLimit,
    )
  }

  /** Lo stato che uno stream accumula fra un pezzo e l'altro: la ragione, l'uso, i dettagli grezzi. */
  class StreamState {
    var finish: String? = null
    var usage: Usage? = null
    val reasoningDetails = mutableListOf<JsonElement>()
  }

  /**
   * Un pezzo di stream -> i delta che contiene. Un `error` a stream gia' aperto (Groq lo fa) e un
   * choice con `finish_reason: "error"` (OpenRouter) sono errori del server, non testo.
   */
  fun parseStreamChunk(payload: String, state: StreamState): List<ChatDelta> {
    val chunk = runCatching { Json.parseToJsonElement(payload) }.getOrElse { throw AiError.Parse("pezzo di stream non JSON", it) }
    chunk["error"]?.let { error ->
      throw AiError.Server(200, error["message"].string() ?: "errore del provider durante lo stream")
    }
    val deltas = mutableListOf<ChatDelta>()
    val choice = chunk["choices"].at(0)
    val delta = choice["delta"]
    delta["content"].string()?.takeIf { it.isNotEmpty() }?.let { deltas += ChatDelta.Text(it) }
    delta["tool_calls"].asArray().forEachIndexed { position, call ->
      deltas += ChatDelta.ToolCallPart(
        index = call["index"].double()?.toInt() ?: position,
        id = call["id"].string(),
        name = call["function"]["name"].string(),
        argumentsFragment = call["function"]["arguments"].string(),
      )
    }
    (delta["reasoning_details"] as? JsonArray)?.let { state.reasoningDetails.addAll(it) }
    choice["finish_reason"].string()?.let { reason ->
      if (reason == "error") throw AiError.Server(200, choice["error"]["message"].string() ?: "errore del provider durante lo stream")
      state.finish = reason
    }
    usage(chunk["usage"])?.let { state.usage = it }
    (chunk["usage"] ?: chunk["x_groq"]["usage"])?.let { usage(it)?.let { u -> state.usage = u } }
    return deltas
  }

  /** Le parti grezze da conservare per OpenRouter: i reasoning_details raccolti lungo lo stream. */
  fun rawFromStream(state: StreamState): JsonObject? =
    if (state.reasoningDetails.isEmpty()) {
      null
    } else {
      buildJsonObject { put("reasoning_details", JsonArray(state.reasoningDetails)) }
    }
}
