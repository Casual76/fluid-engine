package dev.antigravity.fluidengine.ai.orchestrator

import dev.antigravity.fluidengine.ai.provider.ContentPart
import dev.antigravity.fluidengine.ai.provider.Message
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.ai.tools.AiToolCategory
import dev.antigravity.fluidengine.ai.tools.AiToolGroup

/**
 * Uno scambio finito, per la card: la domanda, la risposta, i chip. [attachments] sono le parti
 * (immagini, documenti) che l'utente aveva messo nella domanda: il compattatore le ripropone solo
 * per l'ultimo scambio, cosi' "e a destra cosa c'e'?" trova ancora lo screenshot.
 */
data class Exchange(
  val question: String,
  val answer: String,
  val chips: List<AnswerChip>,
  val provider: ProviderId,
  val atMillis: Long,
  val attachments: List<ContentPart> = emptyList(),
)

/**
 * La conversazione in memoria: gli scambi conclusi, il traffico tool dell'ultima domanda (per i
 * "e domani?"), i gruppi gia' aperti, il provider su cui si e' finiti. L'engine la tiene solo in
 * memoria; un'app che vuole conservarla la ricostruisce da cio' che ha salvato ([exchanges] basta,
 * il traffico tool si perde e va bene cosi'; con un catalogo gerarchico vale la pena salvare anche
 * gli id di [loadedGroups], e ridarglieli con `registry.group(id)`).
 */
class Conversation(val id: Long, val startedAtMillis: Long) {
  val exchanges = mutableListOf<Exchange>()

  /** `Assistant(toolCalls)` + `ToolResult` dell'ultima domanda, da riproporre una volta sola. */
  var lastToolRound: List<Message> = emptyList()

  /** I gruppi dell'ultima domanda: il ripiego del router quando non capisce quella dopo. */
  var lastGroups: Set<AiToolGroup> = emptySet()

  /**
   * I gruppi aperti in questa conversazione (1.26.0), dal meno al piu' recente uso. Restano aperti
   * finche' la conversazione vive: quello che il modello ha chiesto una volta non lo richiede a ogni
   * domanda. L'orchestratore ne toglie dalla testa solo quando i loro tool superano il tetto
   * (`AiOrchestratorConfig.maxLoadedTools`).
   */
  val loadedGroups: LinkedHashSet<AiToolGroup> = linkedSetOf()

  /** Le categorie aperte: quelle dei gruppi aperti, piu' quelle aperte senza sottocategoria. */
  val loadedCategories: LinkedHashSet<AiToolCategory> = linkedSetOf()

  var lastActivityMillis: Long = startedAtMillis
  var provider: ProviderId? = null

  /** Segna questi gruppi come usati adesso: vanno in coda, e sono gli ultimi a cadere. */
  fun touch(groups: Collection<AiToolGroup>) {
    groups.forEach { group ->
      loadedGroups.remove(group)
      loadedGroups.add(group)
    }
  }

  fun isExpired(nowMillis: Long, ttlMillis: Long = TTL_MILLIS): Boolean = nowMillis - lastActivityMillis > ttlMillis

  companion object {
    const val TTL_MILLIS: Long = 10 * 60_000L
  }
}

/**
 * Cosa del passato si manda al modello: le coppie domanda/risposta (solo testo, le vecchie
 * accorciate), il traffico tool dell'ultima domanda al suo posto, gli allegati dell'ultima
 * domanda, e una guardia sui token (prima cadono gli allegati, poi il traffico tool, poi le coppie
 * piu' vecchie, mai l'ultima). Un `Assistant` con tool call cade sempre insieme ai suoi risultati:
 * i provider rispondono 400 a un risultato orfano.
 */
object HistoryCompactor {

  const val MAX_PAIRS = 8
  const val OLD_ANSWER_CHARS = 600
  const val CHARS_PER_TOKEN = 3.5

  fun compact(
    conversation: Conversation,
    budgetTokens: Int,
    includeToolRound: Boolean = true,
    includeAttachments: Boolean = true,
  ): List<Message> {
    val exchanges = conversation.exchanges.takeLast(MAX_PAIRS)
    if (exchanges.isEmpty()) return emptyList()
    fun build(pairs: List<Exchange>, withToolRound: Boolean, withAttachments: Boolean): List<Message> {
      val out = mutableListOf<Message>()
      pairs.forEachIndexed { index, exchange ->
        val last = index == pairs.lastIndex
        out += if (last && withAttachments && exchange.attachments.isNotEmpty()) {
          Message.User(listOf(ContentPart.Text(exchange.question)) + exchange.attachments)
        } else {
          Message.User(exchange.question)
        }
        if (last && withToolRound) out += conversation.lastToolRound
        out += Message.Assistant(text = if (last) exchange.answer else exchange.answer.take(OLD_ANSWER_CHARS))
      }
      return out
    }
    var pairs = exchanges
    var withToolRound = includeToolRound && conversation.lastToolRound.isNotEmpty()
    var withAttachments = includeAttachments && exchanges.last().attachments.isNotEmpty()
    while (true) {
      val messages = build(pairs, withToolRound, withAttachments)
      if (estimateTokens(messages) <= budgetTokens || (pairs.size == 1 && !withToolRound && !withAttachments)) return messages
      when {
        withAttachments -> withAttachments = false
        withToolRound -> withToolRound = false
        else -> pairs = pairs.drop(1)
      }
    }
  }

  fun estimateTokens(messages: List<Message>): Int = (messages.sumOf { chars(it) } / CHARS_PER_TOKEN).toInt() + messages.size * 4

  fun estimateTokens(text: String): Int = (text.length / CHARS_PER_TOKEN).toInt()

  private fun chars(message: Message): Int = when (message) {
    is Message.System -> message.text.length
    // Un'immagine o un documento pesano quanto un migliaio di token: una stima, non un conto.
    is Message.User -> message.text.length + message.parts.count { !it.isText } * 4_000
    is Message.Assistant -> (message.text?.length ?: 0) + message.toolCalls.sumOf { it.name.length + it.arguments.toString().length + 12 }
    is Message.ToolResult -> message.content.length + message.name.length + 12
  }
}
