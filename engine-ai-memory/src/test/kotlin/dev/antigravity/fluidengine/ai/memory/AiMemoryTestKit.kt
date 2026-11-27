package dev.antigravity.fluidengine.ai.memory

import dev.antigravity.fluidengine.ai.net.RateLimitInfo
import dev.antigravity.fluidengine.ai.provider.ChatDelta
import dev.antigravity.fluidengine.ai.provider.ChatProvider
import dev.antigravity.fluidengine.ai.provider.ChatRequest
import dev.antigravity.fluidengine.ai.provider.ChatTurn
import dev.antigravity.fluidengine.ai.provider.FinishReason
import dev.antigravity.fluidengine.ai.provider.Message
import dev.antigravity.fluidengine.ai.provider.ModelCatalogue
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.ai.provider.TranscribeOptions
import dev.antigravity.fluidengine.ai.provider.Transcript
import dev.antigravity.fluidengine.ai.provider.Usage
import dev.antigravity.fluidengine.ai.orchestrator.HistoryCompactor
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Il fuso e l'ora delle prove: il 26 novembre 2026 alle 10, a Roma. */
internal object MemoryTestTime {
  val Zone: ZoneId = ZoneId.of("Europe/Rome")
  val Now: Long = LocalDateTime.of(2026, 11, 26, 10, 0).atZone(Zone).toInstant().toEpochMilli()
  const val Hour = 3_600_000L
  const val Day = 86_400_000L
}

/** Un orologio che si sposta a mano. */
internal class TestClock(var now: Long = MemoryTestTime.Now) : () -> Long {
  override fun invoke(): Long = now
  fun advance(ms: Long) { now += ms }
}

/**
 * Il modello finto dell'estrattore: **nessuna chiamata vera**. Legge il messaggio dell'utente dalla richiesta e risponde
 * col JSON che [reply] sceglie, contando le chiamate e tenendo le richieste per guardarci dentro. L'uso dei gettoni e'
 * quello stimato dall'orchestratore, come lo direbbe un fornitore.
 */
internal class FakeMemoryModel(private val reply: (userMessage: String) -> String) : ChatProvider {
  override val id: ProviderId = ProviderId.GROQ
  val requests = ArrayList<ChatRequest>()
  var failWith: Throwable? = null

  override suspend fun complete(request: ChatRequest): ChatTurn {
    requests += request
    failWith?.let { throw it }
    val user = (request.messages.last() as Message.User).text
    val text = reply(user)
    val prompt = HistoryCompactor.estimateTokens(request.messages)
    val completion = HistoryCompactor.estimateTokens(text)
    return ChatTurn(Message.Assistant(text), FinishReason.STOP, Usage(prompt, completion, prompt + completion), RateLimitInfo.EMPTY)
  }

  override fun stream(request: ChatRequest): Flow<ChatDelta> = emptyFlow()
  override suspend fun listModels(): ModelCatalogue = ModelCatalogue(emptyList(), emptyList())
  override suspend fun transcribe(audio: File, mime: String, options: TranscribeOptions): Transcript = error("non serve")

  companion object {
    /** Il JSON di un fatto. */
    fun fact(testo: String, chiave: String, argomenti: List<String>, scade: String? = null, sempre: Boolean = false): String =
      "{\"testo\":\"$testo\",\"chiave\":\"$chiave\",\"argomenti\":[${argomenti.joinToString(",") { "\"$it\"" }}]," +
        "\"scade\":${scade?.let { "\"$it\"" } ?: "null"},\"sempre\":$sempre}"

    fun facts(vararg f: String): String = "{\"fatti\":[${f.joinToString(",")}]}"

    /**
     * Un modello «che capisce» le frasi delle prove: riconosce le frasi su di se' del corpus e risponde coi fatti giusti;
     * per tutto il resto, niente. E' quello che farebbe un modello piccolo sulle stesse frasi.
     */
    fun scripted(): FakeMemoryModel = FakeMemoryModel { user ->
      val said = user.substringAfter("«").substringBefore("»").lowercase()
      when {
        "4f" in said && "galilei" in said -> facts(fact("è in 4F al liceo Galilei", "scuola.classe", listOf("scuola", "classe", "compiti", "verifiche")))
        "5f" in said -> facts(fact("è in 5F al liceo Galilei", "scuola.classe", listOf("scuola", "classe", "compiti")))
        "vegetarian" in said -> facts(fact("è vegetariana", "cibo.dieta", listOf("cibo", "cena", "ricette", "ristorante")))
        "mi chiamo" in said -> facts(fact("si chiama Giulia", "nome", listOf("nome"), sempre = true))
        "firenze" in said -> facts(fact("abita a Firenze", "casa.citta", listOf("casa", "città", "meteo")))
        "verifica" in said && "venerd" in said -> facts(fact("ha la verifica di matematica venerdì 27 novembre", "scuola.verifica.matematica",
          listOf("verifica", "matematica", "studio"), scade = "2026-11-27"))
        "password" in said -> facts(fact("la password del wifi è girasole42", "casa.wifi", listOf("wifi")))
        // Un modello che sbaglia: un fatto che nel messaggio non c'e'.
        "inventa" in said -> facts(fact("è in 3B al liceo Volta", "scuola.classe", listOf("scuola")))
        else -> facts()
      }
    }
  }
}

/** Un turno di chat normale, alle [at]. */
internal fun turn(text: String, at: Long = MemoryTestTime.Now, conversation: Long = 1L, previous: String? = null) =
  AiMemoryTurn(conversationId = conversation, userText = text, previousAnswer = previous, atMillis = at)
