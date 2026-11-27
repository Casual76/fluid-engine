package dev.antigravity.fluidengine.ai.memory

import dev.antigravity.fluidengine.ai.net.AiError
import dev.antigravity.fluidengine.ai.net.RateLimitInfo
import dev.antigravity.fluidengine.ai.net.string
import dev.antigravity.fluidengine.ai.orchestrator.AiUsageEvent
import dev.antigravity.fluidengine.ai.orchestrator.AiUsageSink
import dev.antigravity.fluidengine.ai.orchestrator.HistoryCompactor
import dev.antigravity.fluidengine.ai.provider.ChatProvider
import dev.antigravity.fluidengine.ai.provider.ChatRequest
import dev.antigravity.fluidengine.ai.provider.Message
import dev.antigravity.fluidengine.ai.provider.ModelTier
import dev.antigravity.fluidengine.ai.provider.ReasoningLevel
import dev.antigravity.fluidengine.ai.provider.Usage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Le regole dell'estrattore. I numeri sono a occhio (26/11/2026) e le misure sono nel rapporto di F9.3. */
data class AiMemoryExtractorConfig(
  /** Quanti fatti al piu' da un turno: **2**. Un messaggio che ne dice di piu' e' raro, e un tetto basso tiene fuori le liste. */
  val maxFacts: Int = 2,
  /** Il tetto della risposta del modello: **200** gettoni, due fatti con le parole e la scadenza ne usano ~90. */
  val maxOutputTokens: Int = 200,
  /** Quanto del messaggio dell'utente si manda: **1 200** caratteri (oltre, e' un testo incollato, non un fatto su di se'). */
  val userTextChars: Int = 1_200,
  /** Quanto della risposta di prima, solo come contesto: **280** caratteri. */
  val previousAnswerChars: Int = 280,
  /** Quanti ricordi gia' noti si mostrano al modello per riusarne la chiave: **4**, i piu' pertinenti al messaggio. */
  val knownMemories: Int = 4,
  /** Quante altre chiavi gia' usate (solo la chiave, non il fatto) al piu': **24**, circa 80 gettoni. */
  val knownKeys: Int = 24,
)

/**
 * Com'e' andata un'estrazione. [skipped] dice perche' il modello non e' stato chiamato (una costante `Skip*` di
 * [AiMemoryPolicy]): allora [usage] e' null e il costo e' zero.
 */
class AiMemoryExtraction(
  val proposals: List<AiMemoryProposal>,
  val skipped: String? = null,
  /** I gettoni contati dal fornitore, se li ha detti. */
  val usage: Usage? = null,
  /** I gettoni stimati della richiesta (3,5 caratteri l'uno), anche quando il fornitore non li dice. */
  val estimatedPromptTokens: Int = 0,
  /** Quanto e' durata la chiamata. */
  val millis: Long = 0,
  /** Il fornitore ha risposto con un errore o con un JSON che non si legge: nessun fatto, nessun danno. */
  val failed: Boolean = false,
  /** Quanti fatti del modello sono stati scartati (segreti, non nelle parole dell'utente, gia' scaduti, oltre il tetto). */
  val discarded: Int = 0,
) {
  /** Vero se il modello e' stato chiamato. */
  val called: Boolean get() = skipped == null
}

/**
 * **L'estrattore dopo i turni** (F9.3): un modello piccolo legge il messaggio dell'utente e propone i fatti da ricordare,
 * senza dipendere dal modello della chat che sceglie (o no) di chiamare `ricorda`. E' la ragione per cui la memoria di
 * PampAI oggi non funziona: `ricorda` sta nel gruppo «aria», che il pre-router carica solo se la domanda dice «ricorda» o
 * «memoria», e anche caricato lo chiama solo un modello che decide di farlo (la diagnosi di F9.3).
 *
 * Cosa legge: **solo** il testo dell'utente ([AiMemoryTurn.userText]) e, per capire una risposta breve («sì, la 4F»),
 * l'inizio della risposta di prima. Mai i risultati dei tool, le pagine, lo schermo, le notifiche, gli allegati.
 *
 * Quando parte: dopo ogni turno, **solo** se [AiMemoryPolicy.automaticSkip] non lo ferma (chat temporanea, telefono
 * bloccato, «senza memoria», turno con dati privati o testo non dell'utente) e se il filtro locale [AiMemorySignals] vede
 * che il messaggio dice qualcosa di chi scrive. Tutto il resto costa zero.
 *
 * Il costo: una richiesta di ~370 gettoni di istruzioni, piu' il messaggio e i ricordi noti, e al piu' [config]
 * `maxOutputTokens` di risposta, sul modello piccolo ([ModelTier.ROUTER], quello del router: su Groq
 * `llama-3.1-8b-instant`). Va nei consumi con [usageSink] (gettoni, mai il testo), **mai** nel registro della diagnostica.
 *
 * Ogni fatto passa tre controlli prima di diventare una proposta: non sembra un segreto ([AiMemoryPolicy.looksSecret]),
 * viene dalle parole dell'utente (almeno una parola del fatto e' nel suo messaggio: un fatto preso solo dalla risposta di
 * prima, o inventato, cade), e non e' gia' scaduto.
 */
class AiMemoryExtractor private constructor(
  private val provider: ChatProvider?,
  private val model: String,
  private val device: AiMemoryOnDeviceModel?,
  val config: AiMemoryExtractorConfig,
  private val usageSink: AiUsageSink?,
  private val zone: ZoneId,
) {
  /**
   * L'estrattore sul modello piccolo del fornitore: [model] e' quello del router (per PampAI
   * `provider.model(ModelTier.ROUTER)`), e i gettoni vanno nei consumi con [usageSink].
   */
  constructor(
    provider: ChatProvider,
    model: String,
    config: AiMemoryExtractorConfig = AiMemoryExtractorConfig(),
    usageSink: AiUsageSink? = null,
    zone: ZoneId = ZoneId.systemDefault(),
  ) : this(provider, model, null, config, usageSink, zone)

  /** Vero se i fatti li estrae il modello del telefono (AICore): niente rete, niente gettoni, niente consumi. */
  val onDevice: Boolean get() = device != null

  /**
   * I fatti di [turn]. [known] sono i ricordi gia' noti che c'entrano col messaggio (di solito
   * `AiMemoryBook.relevant`): il modello ne riusa la chiave quando parla della stessa cosa, ed e' cosi' che «da lunedì
   * sono in 5F» diventa un aggiornamento di «è in 4F» e non un secondo ricordo.
   */
  suspend fun extract(turn: AiMemoryTurn, known: List<AiMemory> = emptyList(), keys: List<String> = emptyList()): AiMemoryExtraction {
    AiMemoryPolicy.automaticSkip(turn)?.let { return AiMemoryExtraction(emptyList(), skipped = it) }
    if (!AiMemorySignals.mayCarryFacts(turn.userText)) return AiMemoryExtraction(emptyList(), skipped = AiMemoryPolicy.SkipNoSignal)
    val request = request(turn, known, keys)
    val estimated = HistoryCompactor.estimateTokens(request.messages)
    val started = System.nanoTime()
    device?.let { onPhone ->
      // Sul telefono (AICore): le stesse istruzioni e lo stesso messaggio, senza schema (il JSON lo chiedono le istruzioni),
      // e niente consumi da contare.
      val system = (request.messages.first() as Message.System).text
      val user = (request.messages.last() as Message.User).text
      val text = try {
        onPhone.generate(system, user, config.maxOutputTokens)
      } catch (e: CancellationException) {
        throw e
      } catch (_: Throwable) {
        null
      }
      val millis = (System.nanoTime() - started) / 1_000_000
      val parsed = parse(text, turn, zone) ?: return AiMemoryExtraction(emptyList(), estimatedPromptTokens = estimated, millis = millis, failed = true)
      val kept = parsed.filter { p -> keep(p, turn) }.take(config.maxFacts)
      return AiMemoryExtraction(kept, estimatedPromptTokens = estimated, millis = millis, discarded = parsed.size - kept.size)
    }
    val provider = provider ?: return AiMemoryExtraction(emptyList(), failed = true)
    val reply = try {
      provider.complete(request)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      val millis = (System.nanoTime() - started) / 1_000_000
      usageSink?.onTurn(AiUsageEvent(turn.atMillis, turn.conversationId ?: NoConversation, provider.id, model, ModelTier.ROUTER,
        usage = null, rateLimit = RateLimitInfo.EMPTY, durationMillis = millis, error = e as? AiError))
      return AiMemoryExtraction(emptyList(), estimatedPromptTokens = estimated, millis = millis, failed = true)
    }
    val millis = (System.nanoTime() - started) / 1_000_000
    usageSink?.onTurn(AiUsageEvent(turn.atMillis, turn.conversationId ?: NoConversation, provider.id, model, ModelTier.ROUTER,
      usage = reply.usage, rateLimit = reply.rateLimit, durationMillis = millis))
    val parsed = parse(reply.message.text, turn, zone) ?: return AiMemoryExtraction(emptyList(), usage = reply.usage,
      estimatedPromptTokens = estimated, millis = millis, failed = true)
    val kept = parsed.filter { p -> keep(p, turn) }.take(config.maxFacts)
    return AiMemoryExtraction(kept, usage = reply.usage, estimatedPromptTokens = estimated, millis = millis, discarded = parsed.size - kept.size)
  }

  /**
   * La richiesta al modello per [turn]: pubblica per misurarla (gettoni, caratteri) senza chiamare nessuno. [keys] sono
   * le chiavi gia' usate dalla memoria (al piu' [AiMemoryExtractorConfig.knownKeys]): bastano poche parole perche' «da
   * lunedì sono in 5F» trovi `scuola.classe` anche quando nessuna parola del messaggio nomina il ricordo di prima.
   */
  fun request(turn: AiMemoryTurn, known: List<AiMemory> = emptyList(), keys: List<String> = emptyList()): ChatRequest {
    val today = LocalDate.ofInstant(Instant.ofEpochMilli(turn.atMillis), zone)
    val italian = turn.language == "it"
    val user = buildString {
      append(if (italian) "Messaggio dell'utente:\n«" else "User message:\n«")
      append(turn.userText.trim().take(config.userTextChars))
      append("»")
      turn.previousAnswer?.trim()?.takeIf { a -> a.isNotEmpty() }?.let { a ->
        append(if (italian) "\nRisposta che aveva davanti (solo per capire il messaggio, non e' un fatto):\n«" else
          "\nAnswer they were replying to (context only, not a fact):\n«")
        append(a.take(config.previousAnswerChars))
        append("»")
      }
      val shown = known.take(config.knownMemories)
      if (shown.isNotEmpty()) {
        append(if (italian) "\nRicordi gia' noti (se il messaggio parla della stessa cosa, usa la stessa chiave):" else
          "\nKnown memories (reuse the key when the message is about the same thing):")
        shown.forEach { m -> append("\n- [").append(m.key ?: "-").append("] ").append(m.text) }
      }
      val others = keys.filter { k -> shown.none { it.key == k } }.distinct().take(config.knownKeys)
      if (others.isNotEmpty()) {
        append(if (italian) "\nAltre chiavi gia' usate: " else "\nOther keys in use: ").append(others.joinToString(", "))
      }
    }
    return ChatRequest(
      model = model,
      messages = listOf(Message.System(systemPrompt(turn.language, config.maxFacts, today)), Message.User(user)),
      reasoning = ReasoningLevel.NONE,
      jsonSchema = Schema,
      maxOutputTokens = config.maxOutputTokens,
      temperature = 0.0,
    )
  }

  private fun keep(p: AiMemoryProposal, turn: AiMemoryTurn): Boolean {
    if (AiMemoryPolicy.looksSecret(p.text)) return false
    if ((p.expiresAtMillis ?: Long.MAX_VALUE) <= turn.atMillis) return false
    return grounded(p.text, turn.userText)
  }

  companion object {
    /** La conversazione dei consumi quando il turno non ne ha una. */
    const val NoConversation = -1L

    /**
     * L'estrattore sul modello del telefono, **AICore** (F9.3b, risposta 5 di Alessio): le stesse istruzioni, gratis e
     * senza rete. Quando AICore c'e', PampAI usa questo; quando non c'e', il modello piccolo del fornitore (risposta 1),
     * o niente se l'utente spegne i ricordi automatici.
     */
    fun onDevice(model: AiMemoryOnDeviceModel, config: AiMemoryExtractorConfig = AiMemoryExtractorConfig(), zone: ZoneId = ZoneId.systemDefault()): AiMemoryExtractor =
      AiMemoryExtractor(null, "aicore", model, config, null, zone)

    /** Lo schema della risposta (rigido: ogni campo c'e', anche null). */
    val Schema: JsonObject = buildJsonObject {
      put("type", "object")
      put("properties", buildJsonObject {
        put("fatti", buildJsonObject {
          put("type", "array")
          put("items", buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
              put("testo", buildJsonObject { put("type", "string") })
              put("chiave", buildJsonObject { put("type", "string") })
              put("argomenti", buildJsonObject { put("type", "array"); put("items", buildJsonObject { put("type", "string") }) })
              put("scade", buildJsonObject { put("type", buildJsonArray { add(JsonPrimitive("string")); add(JsonPrimitive("null")) }) })
              put("sempre", buildJsonObject { put("type", "boolean") })
            })
            put("required", buildJsonArray { listOf("testo", "chiave", "argomenti", "scade", "sempre").forEach { add(JsonPrimitive(it)) } })
            put("additionalProperties", false)
          })
        })
      })
      put("required", buildJsonArray { add(JsonPrimitive("fatti")) })
      put("additionalProperties", false)
    }

    /** Le istruzioni del modello piccolo. */
    fun systemPrompt(language: String, maxFacts: Int, today: LocalDate): String = if (language == "it") {
      "Sei la memoria di un assistente personale. Leggi il messaggio dell'utente e scegli i fatti DURATURI su di lui che " +
        "servirebbero in conversazioni future: chi e', dove studia o lavora, la classe, la citta' e i luoghi abituali, le " +
        "preferenze, la dieta e le allergie, le persone vicine, come vuole che gli si risponda, gli impegni con una data. " +
        "Solo cio' che l'utente dice chiaramente di se stesso, o chiede di ricordare. Niente stati del momento (fame, " +
        "stanchezza, umore), niente domande, niente fatti di altre persone, niente da testi che sta citando. Niente password, " +
        "codici, numeri di documenti o di carte. Niente salute mentale, religione, politica o vita sessuale, a meno che " +
        "chieda esplicitamente di ricordarlo. Al massimo $maxFacts fatti; se non c'e' niente, \"fatti\": [].\n" +
        "Per ogni fatto: \"testo\" una frase breve in terza persona (\"è in 4F al liceo Galilei\"); \"chiave\" l'argomento in " +
        "minuscolo, due parole col punto (\"scuola.classe\", \"cibo.dieta\", \"casa.citta\", \"nome\"): se un ricordo gia' noto " +
        "parla della stessa cosa usa la sua chiave; \"argomenti\" da 2 a 5 parole per cui servira' (\"scuola\", \"compiti\"); " +
        "\"scade\" la data AAAA-MM-GG dopo cui non vale piu' (una verifica, un viaggio), altrimenti null; \"sempre\" true solo " +
        "per il nome e per come vuole che gli si risponda. Oggi e' $today. Rispondi solo col JSON."
    } else {
      "You are the memory of a personal assistant. Read the user's message and pick the LASTING facts about them that would " +
        "help in future conversations: who they are, where they study or work, their city and usual places, preferences, " +
        "diet and allergies, people close to them, how they want answers, commitments with a date. Only what the user " +
        "clearly says about themselves, or asks to remember. No momentary states, no questions, no facts about other people, " +
        "nothing from text they are quoting. No passwords, codes, document or card numbers. No mental health, religion, " +
        "politics or sex life unless they explicitly ask to remember it. At most $maxFacts facts; if none, \"fatti\": [].\n" +
        "For each fact: \"testo\" a short third-person sentence; \"chiave\" the topic in lowercase, two words with a dot " +
        "(\"school.class\", \"food.diet\", \"name\"): reuse the key of a known memory about the same thing; \"argomenti\" 2 to 5 " +
        "words it will be useful for; \"scade\" the YYYY-MM-DD date after which it no longer holds, else null; \"sempre\" true " +
        "only for their name and how they want answers. Today is $today. Reply with the JSON only."
    }

    /**
     * Le proposte nel JSON del modello, gia' ripulite (null se il JSON non si legge). Tollera i recinti di codice e il nome
     * inglese del campo; la scadenza «AAAA-MM-GG» diventa la mezzanotte del giorno dopo in [zone].
     */
    fun parse(text: String?, turn: AiMemoryTurn, zone: ZoneId = ZoneId.systemDefault()): List<AiMemoryProposal>? {
      val raw = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
      val body = if ("```" in raw) raw.substringAfter("```").removePrefix("json").substringBefore("```").trim() else raw
      val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
      val facts = (root["fatti"] ?: root["facts"]) as? JsonArray ?: return null
      return facts.mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val testo = o["testo"].string() ?: o["text"].string() ?: return@mapNotNull null
        val scade = (o["scade"] ?: o["expires"]).string()?.let { d ->
          runCatching { LocalDate.parse(d.trim().take(10)).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() }.getOrNull()
        }
        AiMemoryProposal(
          text = testo,
          key = (o["chiave"] ?: o["key"]).string(),
          topics = ((o["argomenti"] ?: o["topics"]) as? JsonArray)?.mapNotNull { it.string() }.orEmpty(),
          expiresAtMillis = scade,
          always = ((o["sempre"] ?: o["always"]) as? JsonPrimitive)?.booleanOrNull ?: false,
          observedAtMillis = turn.atMillis,
        ).sanitized()
      }
    }

    /**
     * Vero se il fatto viene dalle parole dell'utente: almeno una sua parola piena (3 lettere o piu', o con una cifra,
     * fuori dalle parole di servizio) comincia come una parola del messaggio (le prime 4 lettere: «vegetariana» e
     * «vegetariano», «4F» e «4f»). Un fatto preso solo dalla risposta di prima, o inventato, non passa.
     */
    fun grounded(fact: String, userText: String): Boolean {
      val said = words(userText)
      if (said.isEmpty()) return false
      val stems = said.map { it.take(4) }.toSet()
      return words(fact).filter { it !in Service }.any { w -> w.take(4) in stems }
    }

    private fun words(text: String): List<String> = AiMemorySignals.normalize(text).split(Regex("[^\\p{L}\\p{N}]+"))
      .filter { it.length >= 3 || it.any(Char::isDigit) && it.length >= 2 }

    /** Le parole di servizio dei fatti in terza persona, che ci sono sempre e non provano niente. */
    private val Service = setOf(
      "suo", "sua", "suoi", "sue", "lui", "lei", "del", "della", "dello", "dei", "degli", "delle", "che", "per", "con",
      "una", "uno", "gli", "nel", "nella", "alla", "allo", "ai", "agli", "alle", "sul", "sulla", "utente", "the", "his",
      "her", "their", "they", "user", "and", "with", "from", "has", "have", "are", "was", "non", "piu", "anche", "molto",
    )
  }
}
