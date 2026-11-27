package dev.antigravity.fluidengine.ai.memory

import dev.antigravity.fluidengine.ai.net.string
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Un fatto proposto alla memoria: dall'estrattore dopo un turno, da «ricordati che…», dalla pagina, o da un'altra app
 * Pampa col ponte (ClasseViva propone «è in 4F»). Una proposta non e' ancora un ricordo: decide la memoria di PampAI
 * (`AiMemoryBook` di `engine-ai-memory`), che la controlla, la confronta con quello che sa e ne fa un avviso.
 *
 * Il formato non ha posto per altro: niente identificativi, indirizzi o campi nascosti, solo il fatto e come trattarlo.
 * [sanitized] lo riporta nei limiti prima di ogni uso; [toJson] e [fromJson] sono il pezzo che passa nel ponte.
 */
data class AiMemoryProposal(
  /** Il fatto, una frase breve in terza persona: «è in 4F al liceo Galilei». */
  val text: String,
  /** L'argomento, per le contraddizioni: «scuola.classe». Lettere minuscole, cifre, `.`, `-`, `_`. */
  val key: String? = null,
  /** Le parole per cui serve, per trovarlo dalla domanda: «scuola», «compiti», «verifiche». */
  val topics: List<String> = emptyList(),
  /** Da quando non vale piu'; null se non scade. */
  val expiresAtMillis: Long? = null,
  /** Vale per ogni domanda: solo il nome e come vuole che gli si risponda. */
  val always: Boolean = false,
  /** Quando il fatto e' stato detto, se e' prima di adesso (un dato di un'app, la memoria di prima). */
  val observedAtMillis: Long? = null,
  /** Il valore strutturato («4F»), se c'e'. Passa anche nel ponte. */
  val value: String? = null,
  /** Come e' nato (le costanti `Origin*` di [AiMemory]). Non passa nel ponte: lo decide PampAI. */
  val origin: String = AiMemory.OriginStatement,
  /** Le fonti autorizzate (la ricerca di PampAI). Non passano nel ponte. */
  val evidence: List<AiMemoryEvidence> = emptyList(),
  /** Cosa non copre, per un fatto dedotto. Non passa nel ponte. */
  val limitations: String? = null,
  /** Chi l'ha dedotto, per un fatto dedotto. Non passa nel ponte. */
  val derivedBy: String? = null,
) {
  /**
   * La stessa proposta nei limiti: i caratteri di controllo tolti, gli spazi ripiegati, il testo tagliato a
   * [MaxTextChars] (a una parola), la chiave normalizzata ([normalizeKey]), al piu' [MaxTopics] parole di
   * [MaxTopicChars], il valore a [MaxValueChars]. Null se non resta un fatto.
   */
  fun sanitized(): AiMemoryProposal? {
    val clean = cleanText(text) ?: return null
    return copy(
      text = clean,
      key = normalizeKey(key),
      topics = normalizeTopics(topics),
      expiresAtMillis = expiresAtMillis?.takeIf { it > 0 },
      observedAtMillis = observedAtMillis?.takeIf { it > 0 },
      value = value?.let { cleanText(it) }?.take(MaxValueChars),
      limitations = limitations?.let { cleanText(it) },
      derivedBy = derivedBy?.trim()?.take(MaxKeyChars * 2)?.takeIf { it.isNotEmpty() },
    )
  }

  /**
   * Il JSON della proposta, come passa nel ponte: `{"text", "key"?, "topics"?, "expiresAt"?, "always"?, "observedAt"?,
   * "value"?}`. L'origine, le fonti, i limiti e chi l'ha dedotto non passano: un'app propone un dato suo, non una prova.
   */
  fun toJson(): String = toJsonObject().toString()

  internal fun toJsonObject(): JsonObject = buildJsonObject {
    put("text", text)
    key?.let { put("key", it) }
    if (topics.isNotEmpty()) put("topics", buildJsonArray { topics.forEach { add(JsonPrimitive(it)) } })
    expiresAtMillis?.let { put("expiresAt", it) }
    if (always) put("always", true)
    observedAtMillis?.let { put("observedAt", it) }
    value?.let { put("value", it) }
  }

  companion object {
    /**
     * Quanto e' lungo al piu' un fatto: 300 caratteri, come la memoria di oggi di PampAI (`MemoryRepository.MAX_CHARS`),
     * cosi' i ricordi di prima passano senza tagli. L'estrattore ne scrive di molto piu' corti (una riga).
     */
    const val MaxTextChars = 300

    /** Quanto e' lunga al piu' una chiave. */
    const val MaxKeyChars = 48

    /** Quante parole al piu' accompagnano un fatto. */
    const val MaxTopics = 6

    /** Quanto e' lunga al piu' una parola. */
    const val MaxTopicChars = 24

    /** Quanto e' lungo al piu' un valore strutturato. */
    const val MaxValueChars = 120

    private val Controls = Regex("[\\p{Cc}\\p{Cf}]")
    private val Spaces = Regex("\\s+")
    private val KeyChars = Regex("[^a-z0-9._-]")
    private val Wrapping = listOf('«' to '»', '“' to '”', '"' to '"')

    /**
     * Il testo di un fatto pulito (caratteri di controllo, spazi, le virgolette che lo chiudono tutto) e tagliato a
     * [MaxTextChars]; null se vuoto. Le virgolette dentro il fatto restano: «firma con «Giulia»».
     */
    fun cleanText(raw: String): String? {
      var flat = raw.replace(Controls, " ").replace(Spaces, " ").trim()
      for ((open, close) in Wrapping) {
        if (flat.length >= 2 && flat.first() == open && flat.last() == close) {
          val inner = flat.substring(1, flat.length - 1)
          if (open !in inner && close !in inner) flat = inner.trim()
        }
      }
      if (flat.isEmpty()) return null
      if (flat.length <= MaxTextChars) return flat
      val cut = flat.substring(0, MaxTextChars)
      val space = cut.lastIndexOf(' ')
      return (if (space >= MaxTextChars / 2) cut.substring(0, space) else cut).trimEnd(',', ';', ':', ' ')
    }

    /** La chiave in minuscolo, con gli spazi diventati punti e solo lettere, cifre, `.`, `-`, `_`; null se vuota. */
    fun normalizeKey(key: String?): String? {
      val k = key?.trim()?.lowercase()?.replace(Spaces, ".")?.replace(KeyChars, "")?.trim('.', '-', '_')?.take(MaxKeyChars)
      return k?.takeIf { it.isNotEmpty() }
    }

    /** Le parole di un fatto ripulite: in minuscolo, di lettere e cifre, da 2 a [MaxTopicChars], al piu' [MaxTopics]. */
    fun normalizeTopics(topics: List<String>): List<String> = topics.asSequence()
      .map { t -> t.trim().lowercase().filter { it.isLetterOrDigit() || it == ' ' || it == '-' }.trim() }
      .filter { it.length in 2..MaxTopicChars }
      .distinct()
      .take(MaxTopics)
      .toList()

    /** La proposta di [json] (il formato di [toJson]), o null se non e' una proposta. Non la ripulisce: [sanitized]. */
    fun fromJson(json: String): AiMemoryProposal? = runCatching { fromJsonElement(Json.parseToJsonElement(json)) }.getOrNull()

    /** Come [fromJson], da un elemento gia' letto (la proposta dentro una richiesta del ponte). */
    fun fromJsonElement(e: JsonElement?): AiMemoryProposal? {
      val obj = e as? JsonObject ?: return null
      val text = obj["text"].string() ?: return null
      return AiMemoryProposal(
        text = text,
        key = obj["key"].string(),
        topics = (obj["topics"] as? JsonArray)?.mapNotNull { it.string() }.orEmpty(),
        expiresAtMillis = (obj["expiresAt"] as? JsonPrimitive)?.longOrNull,
        always = (obj["always"] as? JsonPrimitive)?.booleanOrNull ?: false,
        observedAtMillis = (obj["observedAt"] as? JsonPrimitive)?.longOrNull,
        value = obj["value"].string(),
      )
    }
  }
}

/**
 * Il ricordo proposto da un'altra app, passando da PampAI (F9.3 sopra il contratto inverso di F9.1): i metodi e il
 * formato. Il trasporto, l'identita' di chi chiama e la tabella delle app sono quelli di F9.1 (`ContentResolver.call` sul
 * provider dell'assistenza di PampAI, la firma Pampa, il pacchetto detto dal sistema); qui c'e' solo cosa si scrive.
 *
 * | metodo | richiesta | risposta |
 * |---|---|---|
 * | [MethodPropose] | `{"memory":1, "proposal":{…}}` | un [AiMemoryProposalResult] (di serie `pending`: la card chiede) |
 * | [MethodAnswer] | `{"memory":1, "noticeId":"…", "accept":true}` | `remembered`/`updated`, `declined` o `not_found` |
 * | [MethodUndo] | `{"memory":1, "noticeId":"…"}` | `{"outcome":"undone"}` o `{"outcome":"not_found"}` |
 * | [MethodForgetMine] | `{"memory":1}` | `{"outcome":"forgotten", "count":N}` |
 *
 * **Di serie la memoria chiede** (scelta di Alessio del 26/11/2026): una proposta torna `pending`, l'app mostra «Vuoi
 * che PampAI ricordi…?» con Ricorda e No e manda la risposta con [MethodAnswer]; la stessa domanda resta anche nella
 * pagina di PampAI. Col salvataggio automatico acceso torna subito `remembered`, con Annulla.
 *
 * Un'app risponde, annulla e dimentica **solo quello che ha proposto lei**: le altre revoche (un ricordo, tutti, quelli
 * di un'app) le fa l'utente in PampAI. E **riceve solo il suo**: l'esito, l'avviso e il fatto che ha scritto, mai un
 * ricordo di PampAI (R3-S1).
 */
object AiMemoryProtocol {
  /** La versione del formato dei ricordi. */
  const val Version = 1

  const val MethodPropose = "memory_propose"
  const val MethodAnswer = "memory_answer"
  const val MethodUndo = "memory_undo"
  const val MethodForgetMine = "memory_forget_mine"

  /** I quattro metodi, per chi smista le chiamate. */
  val Methods: Set<String> = setOf(MethodPropose, MethodAnswer, MethodUndo, MethodForgetMine)

  /** La richiesta di [MethodAnswer]: [accept] vero per Ricorda, falso per No. */
  fun answerRequest(noticeId: String, accept: Boolean): String =
    buildJsonObject { put("memory", Version); put("noticeId", noticeId); put("accept", accept) }.toString()

  /** La richiesta di [MethodPropose]. */
  fun proposeRequest(proposal: AiMemoryProposal): String =
    buildJsonObject { put("memory", Version); put("proposal", proposal.toJsonObject()) }.toString()

  /** La richiesta di [MethodUndo]. */
  fun undoRequest(noticeId: String): String = buildJsonObject { put("memory", Version); put("noticeId", noticeId) }.toString()

  /** La richiesta di [MethodForgetMine]. */
  fun forgetMineRequest(): String = buildJsonObject { put("memory", Version) }.toString()
}

/**
 * Com'e' andata una proposta (o un annulla, o una revoca) arrivata col ponte. [outcome] e' una delle costanti `Outcome*`:
 * un'app che riceve un esito che non conosce lo tratta come [OutcomeRefused].
 */
data class AiMemoryProposalResult(
  val outcome: String,
  /** L'avviso di PampAI, da passare a [AiMemoryProtocol.MethodUndo] per annullare. */
  val noticeId: String? = null,
  /**
   * Il fatto che l'app ha proposto, come l'ha ripulito PampAI (puo' essere tagliato). Mai un ricordo che l'app non ha
   * scritto lei: per [OutcomeKept] il fatto che resta non torna (R3-S1).
   */
  val text: String? = null,
  /**
   * Il fatto di prima. **Il ponte non lo manda piu'** (R3-S1): il ricordo sostituito o quello che resta sono dell'utente, e
   * li mostra solo la pagina di PampAI. Resta nel formato per chi lo legge.
   */
  val previousText: String? = null,
  /** Per [OutcomeForgotten]: quanti. */
  val count: Int? = null,
) {
  /** Vero se adesso PampAI lo ricorda (nuovo o aggiornato). */
  val remembered: Boolean get() = outcome == OutcomeRemembered || outcome == OutcomeUpdated

  /**
   * L'avviso da mostrare nell'app che ha proposto (la card o la notifica di `engine-ai-ui`), o null se non c'e' niente
   * da dire. [source] e' l'app stessa. Per [OutcomeKept] niente, come per [OutcomeDuplicate]: il fatto non e' entrato, e
   * quello che resta lo vede l'utente in PampAI (R3-S1).
   */
  fun notice(source: AiMemorySource, atMillis: Long): AiMemoryNotice? {
    val kind = when (outcome) {
      OutcomeRemembered -> AiMemoryNotice.KindRemembered
      OutcomeUpdated -> AiMemoryNotice.KindUpdated
      OutcomePending -> AiMemoryNotice.KindAsk
      else -> return null
    }
    return AiMemoryNotice(noticeId ?: return null, kind, memoryId = "", text = text.orEmpty(), previousText = previousText,
      source = source, atMillis = atMillis)
  }

  fun toJson(): String = buildJsonObject {
    put("outcome", outcome)
    noticeId?.let { put("noticeId", it) }
    text?.let { put("text", it) }
    previousText?.let { put("previousText", it) }
    count?.let { put("count", it) }
  }.toString()

  companion object {
    /** Ricordato: e' nuovo. */
    const val OutcomeRemembered = "remembered"

    /** Ricordato al posto di un fatto con la stessa chiave. */
    const val OutcomeUpdated = "updated"

    /** Non entrato: PampAI sa gia' un fatto piu' recente con la stessa chiave, che resta. */
    const val OutcomeKept = "kept"

    /** Lo sapeva gia', uguale: niente di nuovo, nessun avviso. */
    const val OutcomeDuplicate = "duplicate"

    /** Non si ricorda: sembra un segreto (una password, un codice), o non e' un fatto. */
    const val OutcomeRefused = "refused"

    /** L'app non puo' proporre ricordi, non di questo argomento, o l'utente gliel'ha tolto. */
    const val OutcomeNotAllowed = "not_allowed"

    /** Troppe proposte oggi da quest'app, o la memoria e' piena. */
    const val OutcomeLimitReached = "limit_reached"

    /** La richiesta non si legge. */
    const val OutcomeInvalid = "invalid"

    /** Annullato. */
    const val OutcomeUndone = "undone"

    /** Niente da annullare: l'avviso non c'e', e' di un'altra app, o il ricordo e' cambiato nel frattempo. */
    const val OutcomeNotFound = "not_found"

    /** Dimenticati ([count]). */
    const val OutcomeForgotten = "forgotten"

    /**
     * In attesa del si' dell'utente (il modo di serie): la card chiede «Vuoi che ricordi…?», e la risposta arriva con
     * [AiMemoryProtocol.MethodAnswer] o dalla pagina di PampAI. [noticeId] e' la domanda.
     */
    const val OutcomePending = "pending"

    /** L'utente ha detto No a una domanda: il fatto non entra. */
    const val OutcomeDeclined = "declined"

    /** L'utente ha chiesto di non imparare piu' questo argomento (o da questa fonte): non entra. */
    const val OutcomeExcluded = "excluded"

    /** L'esito di [json], o un [OutcomeInvalid] se non si legge. */
    fun fromJson(json: String): AiMemoryProposalResult = runCatching {
      val o = Json.parseToJsonElement(json) as JsonObject
      AiMemoryProposalResult(
        outcome = o["outcome"].string() ?: OutcomeInvalid,
        noticeId = o["noticeId"].string(),
        text = o["text"].string(),
        previousText = o["previousText"].string(),
        count = (o["count"] as? JsonPrimitive)?.content?.toIntOrNull(),
      )
    }.getOrElse { AiMemoryProposalResult(OutcomeInvalid) }
  }
}
