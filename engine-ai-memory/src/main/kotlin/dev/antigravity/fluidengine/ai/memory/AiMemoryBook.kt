package dev.antigravity.fluidengine.ai.memory

import android.content.Context
import dev.antigravity.fluidengine.search.FluidTextEmbedder
import dev.antigravity.fluidengine.vault.FluidVault
import dev.antigravity.fluidengine.vault.VaultAccess
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** I tetti della memoria. A occhio il 26/11/2026, tranne dove e' scritto. */
data class AiMemoryLimits(
  /**
   * Quanti ricordi al piu': **300**. Oltre, un ricordo nuovo non entra e l'esito lo dice («la memoria è piena»): meglio
   * chiedere all'utente di fare spazio che dimenticare da soli. La memoria di prima passa tutta, anche oltre.
   */
  val maxMemories: Int = 300,
  /** Quanti ricordi automatici (dall'estrattore) al giorno: **12**, domande comprese. Il rischio della scheda: «ricorda troppe cose». */
  val maxAutomaticPerDay: Int = 12,
  /** Quanti ricordi al giorno puo' proporre un'app: **5**, domande comprese. */
  val maxPerAppPerDay: Int = 5,
  /** Quanti giorni un ricordo scaduto resta visibile nella pagina prima di sparire: **7**. */
  val keepExpiredDays: Int = 7,
  /** Quanti avvisi recenti tiene [AiMemoryBook.notices]: **20**. */
  val notices: Int = 20,
  /** Quanti annulla restano possibili (anche dopo un riavvio): gli ultimi **50**. */
  val undo: Int = 50,
  /** Quante domande «Vuoi che ricordi…?» aspettano al piu': **30** (oltre, la piu' vecchia cade). */
  val maxPending: Int = 30,
  /** Per quanti giorni una domanda senza risposta resta nella pagina: **14**, poi cade (il fatto non entra). */
  val pendingDays: Int = 14,
)

/** Come si comporta la memoria. */
data class AiMemoryConfig(
  val limits: AiMemoryLimits = AiMemoryLimits(),
  /** Il tetto di gettoni del blocco nel prompt. */
  val prompt: AiMemoryPromptBudget = AiMemoryPromptBudget(),
  /**
   * Il modello del significato, facoltativo: quello scaricato col consenso (`FluidLiteRtEmbedder` di
   * `engine-search-semantic`), o quello prestato da un'altra app con `FluidSearchBridge.remoteEmbedder`. Con lui
   * «consigliami una cena» trova «è vegetariana» anche senza parole in comune.
   */
  val embedder: FluidTextEmbedder? = null,
  /** Il fuso delle date nel prompt e delle scadenze. */
  val zone: ZoneId = ZoneId.systemDefault(),
  /**
   * Il salvataggio automatico **di serie**, finche' l'utente non tocca l'interruttore: **spento** (scelta di Alessio del
   * 26/11/2026: «di default ti chiede se vuoi che venga ricordata una certa cosa»). La scelta dell'utente si salva con la
   * memoria ([AiMemoryBook.setAutoSave]).
   */
  val autoSave: Boolean = false,
  /** Le app che possono proporre ricordi da sole, e su quali argomenti. */
  val apps: AiMemoryAppPolicy = AiMemoryAppPolicy(),
  /**
   * Il modello del telefono (AICore, F9.5), se c'e': allarga le domande con le parole dell'argomento
   * ([AiMemoryQueryExpansion]). Null: la pertinenza va per parole (e per significato, con [embedder]).
   */
  val onDevice: AiMemoryOnDeviceModel? = null,
  /** Quanto si aspetta al piu' il modello del telefono per una domanda. */
  val onDeviceTimeoutMillis: Long = AiMemoryQueryExpansion.TimeoutMillis,
)

/** Com'e' andata una proposta: [result] e' una costante `Outcome*` di [AiMemoryProposalResult], o [NeedsConfirmation]. */
class AiMemoryOutcome(
  val result: String,
  /** Il ricordo com'e' adesso (nuovo, aggiornato, o quello piu' recente che resta). */
  val memory: AiMemory? = null,
  /** L'avviso da mostrare, se c'e' qualcosa da dire (per [AiMemoryProposalResult.OutcomePending] la domanda). */
  val notice: AiMemoryNotice? = null,
) {
  /** Vero se adesso la memoria lo ricorda (nuovo o aggiornato). */
  val remembered: Boolean get() = result == AiMemoryProposalResult.OutcomeRemembered || result == AiMemoryProposalResult.OutcomeUpdated

  companion object {
    /**
     * «Ricordati che…» in un turno che ha letto testo scritto da altri, o dati privati: decide l'utente con un tasto (in
     * PampAI la conferma di sicurezza che c'e' gia'), poi si richiama [AiMemoryBook.remember] con `confirmed = true`.
     */
    const val NeedsConfirmation = "needs_confirmation"
  }
}

/** Com'e' andato il dopo-turno: l'estrazione (e il suo costo) e le proposte. */
class AiMemoryAfterTurn(val extraction: AiMemoryExtraction, val outcomes: List<AiMemoryOutcome>) {
  /** Gli avvisi da mostrare sotto la risposta (di serie le domande «Vuoi che ricordi…?»). */
  val notices: List<AiMemoryNotice> get() = outcomes.mapNotNull { it.notice }
}

/** Una riga della memoria di prima di PampAI: la tabella `memories` di Room v7, colonna per colonna. */
data class AiMemoryLegacyRow(
  val id: Long,
  val text: String,
  val createdAtMillis: Long,
  val sourceConversationId: Long?,
  val pinned: Boolean,
)

/**
 * Un fatto del profilo della ricerca di PampAI (Room v7: `aria_profile` con le sue prove da `aria_dependencies` e
 * `aria_evidence`, e l'app della fonte da `aria_grants`), da fondere nella memoria unica (F9.3b, risposta 6 di Alessio).
 */
data class AiMemoryProfileRow(
  val id: String,
  /** Il campo del profilo («musica.artista_preferito»): diventa la chiave. */
  val field: String,
  val value: String,
  /** `USER_STATEMENT`, `INFERENCE` o un'osservazione: diventa l'origine. */
  val kind: String,
  /** `ACTIVE` passa; `SUPERSEDED`, `INVALID`, `FORGOTTEN` no (restano nella copia di Room). */
  val status: String,
  val limitations: String? = null,
  /** «provider/modello» per i fatti dedotti. */
  val derivedBy: String? = null,
  val evidence: List<AiMemoryEvidence> = emptyList(),
  val app: String? = null,
  val appLabel: String? = null,
  /** Quando era vero (il piu' recente delle prove), se si sa. */
  val observedAtMillis: Long? = null,
)

/** Com'e' andato il passaggio della memoria di prima (o del profilo). */
data class AiMemoryImport(
  /** Quanti ricordi sono entrati adesso. */
  val imported: Int,
  /** Quanti erano gia' passati (il passaggio si puo' rifare: non duplica). */
  val alreadyImported: Int,
  /** Quanti erano doppi di un ricordo che c'e' gia' (lo stesso testo, o la stessa chiave con un fatto detto dall'utente). */
  val duplicates: Int,
  /** Quanti erano vuoti. */
  val empty: Int,
  /** Quanti somigliano a un segreto: entrano (li aveva chiesti l'utente) ma non vanno mai nel prompt; la pagina li segnala. */
  val secretLike: Int,
  /** Per il profilo: quanti non erano attivi (superati, invalidati, dimenticati) e restano solo nella copia di Room. */
  val inactive: Int = 0,
  /** Per il profilo: quante esclusioni sono passate. */
  val exclusions: Int = 0,
  /** Per il profilo: quanti fatti erano esclusi (un'esclusione passata adesso o gia' nella memoria) e non sono entrati. */
  val excluded: Int = 0,
)

/**
 * **La memoria di PampAI** (F9.3, F9.3b): i fatti che l'assistente ricorda dell'utente, con la fonte, la data e la
 * scadenza, cifrati nella cassaforte, e la ricerca per pertinenza che decide quali entrano in una domanda. E' **la memoria
 * unica**: ci stanno i ricordi della chat, quelli proposti dalle app, quelli scritti a mano, la memoria di prima e il
 * profilo della ricerca (valori strutturati, fonti autorizzate, fatti dedotti coi loro limiti, esclusioni).
 *
 * - **Niente si ricorda senza che l'utente lo veda.** Di serie la memoria **chiede**: un fatto che l'utente non ha chiesto
 *   lui (l'estrattore, il modello, un'app, la ricerca) diventa una domanda «Vuoi che ricordi…?» ([pending], avviso
 *   [AiMemoryNotice.KindAsk]) e entra solo con [answer]. Col salvataggio automatico ([setAutoSave]) entra subito, con
 *   «Ho ricordato…» e Annulla ([undo]). «Ricordati che…» detto dall'utente, e quello scritto a mano, entrano sempre
 *   subito.
 * - **Le contraddizioni**: due ricordi con la stessa chiave non stanno insieme. Un fatto detto dall'utente vince su uno
 *   letto o dedotto; fra due dello stesso tipo vince quello detto piu' di recente (anche se arriva dopo), e l'avviso dice
 *   cosa c'era prima.
 * - **Correggibile**: [correct], [setPinned], [setExpiry]; **la revoca**: [forget] (anche «e non imparare piu'»),
 *   [forgetAll], [forgetFromApp], [forgetFromGrant] per una fonte autorizzata revocata; [exclude] e [include];
 *   [setAppBlocked] per un'app che non deve piu' proporre.
 * - **La pertinenza**: [relevant] e [prompt], col tetto di gettoni ([AiMemoryPromptBudget]).
 * - **L'estrattore**: [afterTurn] alla fine di ogni turno.
 *
 * Tutte le scritture passano da un lucchetto e vanno sul disco **prima** di cambiare cio' che si vede: se il disco
 * rifiuta, l'eccezione arriva a chi chiama e la memoria resta com'era. I flussi sono valori interi e immutabili (regola
 * F7.21), da raccogliere con `collectAsState`.
 */
class AiMemoryBook private constructor(
  private val store: AiMemoryStore,
  private val index: AiMemoryIndex,
  val config: AiMemoryConfig,
  private val clock: () -> Long,
  initial: MemoryState,
) {
  private val mutex = Mutex()
  @Volatile private var state: MemoryState = initial

  private val _memories = MutableStateFlow(display(initial.memories))

  /** Tutti i ricordi, i fissati prima e poi dal piu' recente: la pagina «Cosa ricordo di te». */
  val memories: StateFlow<List<AiMemory>> = _memories.asStateFlow()

  private val _notices = MutableStateFlow<List<AiMemoryNotice>>(emptyList())

  /** Gli avvisi recenti, dal piu' nuovo: le card sotto le risposte. */
  val notices: StateFlow<List<AiMemoryNotice>> = _notices.asStateFlow()

  private val _pending = MutableStateFlow(pendingNotices(initial))

  /** Le domande «Vuoi che ricordi…?» che aspettano una risposta, dalla piu' nuova: la sezione «Da confermare». */
  val pending: StateFlow<List<AiMemoryNotice>> = _pending.asStateFlow()

  private val _blocked = MutableStateFlow(initial.blockedApps)

  /** Le app a cui l'utente ha tolto il permesso di proporre ricordi. */
  val blockedApps: StateFlow<Set<String>> = _blocked.asStateFlow()

  private val _exclusions = MutableStateFlow(initial.exclusions)

  /** Cio' che l'utente non vuole che la memoria impari piu'. */
  val exclusions: StateFlow<List<AiMemoryExclusion>> = _exclusions.asStateFlow()

  private val _autoSave = MutableStateFlow(initial.autoSave ?: config.autoSave)

  /**
   * Il salvataggio automatico: vero, i fatti entrano subito con Annulla; falso (di serie), la memoria chiede. Uno solo
   * per tutto: la chat, le app, lo strumento del modello e i fatti imparati dalla ricerca ([AiMemorySource.KindResearch]:
   * spento chiedono uno per uno, acceso entrano da soli; risposte 2C e 4A di Alessio alle domande di F9.3b).
   */
  val autoSave: StateFlow<Boolean> = _autoSave.asStateFlow()

  /**
   * Propone [proposal], nato da [source].
   *
   * Con [turn] (un «ricordati che…» o il tool del modello in chat) valgono le regole di [AiMemoryPolicy.explicit]: nella
   * chat temporanea o dal telefono bloccato non si ricorda; se il turno ha letto testo di altri l'esito e'
   * [AiMemoryOutcome.NeedsConfirmation] finche' [confirmed] non e' vero. Entra subito se l'ha chiesto l'utente ([turn]
   * con «ricordati», [confirmed], o scritto a mano) o se il salvataggio automatico e' acceso; se no l'esito e'
   * [AiMemoryProposalResult.OutcomePending] e l'avviso e' la domanda. [automatic] e' vero per i fatti dell'estrattore:
   * contano nel loro tetto del giorno.
   */
  suspend fun remember(
    proposal: AiMemoryProposal,
    source: AiMemorySource,
    turn: AiMemoryTurn? = null,
    confirmed: Boolean = false,
    automatic: Boolean = false,
  ): AiMemoryOutcome = mutex.withLock { rememberLocked(proposal, source, turn, confirmed, automatic) }

  private suspend fun rememberLocked(
    proposal: AiMemoryProposal,
    source: AiMemorySource,
    turn: AiMemoryTurn?,
    confirmed: Boolean,
    automatic: Boolean,
  ): AiMemoryOutcome {
    val now = clock()
    var userAsked = confirmed || source.kind == AiMemorySource.KindManual || source.kind == AiMemorySource.KindImported
    if (turn != null) {
      when (AiMemoryPolicy.explicit(turn)) {
        AiMemoryPolicy.VerdictRefused -> return AiMemoryOutcome(AiMemoryProposalResult.OutcomeNotAllowed)
        AiMemoryPolicy.VerdictAskFirst -> if (!confirmed) return AiMemoryOutcome(AiMemoryOutcome.NeedsConfirmation)
      }
      if (AiMemorySignals.asksToRemember(turn.userText)) userAsked = true
    }
    val p = proposal.sanitized() ?: return AiMemoryOutcome(AiMemoryProposalResult.OutcomeInvalid)
    if (AiMemoryPolicy.looksSecret(p.text)) return AiMemoryOutcome(AiMemoryProposalResult.OutcomeRefused)
    if ((p.expiresAtMillis ?: Long.MAX_VALUE) <= now) return AiMemoryOutcome(AiMemoryProposalResult.OutcomeRefused)
    if (source.fromApp && source.app in state.blockedApps) return AiMemoryOutcome(AiMemoryProposalResult.OutcomeNotAllowed)
    // L'utente ha chiesto di non imparare piu' questo: vale per tutto cio' che non ha chiesto lui adesso.
    if (!userAsked && state.exclusions.any { it.matches(p.key, source.app, p.evidence) }) {
      return AiMemoryOutcome(AiMemoryProposalResult.OutcomeExcluded)
    }
    val ask = !userAsked && !_autoSave.value
    return storeLocked(p, source, now, automatic, ask, noticeId = newId())
  }

  /**
   * Il cuore: lo stesso fatto, la contraddizione, i tetti, poi il ricordo nuovo o la domanda ([ask]). [noticeId] e'
   * l'avviso da usare (una risposta Ricorda tiene l'id della domanda, cosi' la card cambia al suo posto).
   */
  private suspend fun storeLocked(
    p: AiMemoryProposal,
    source: AiMemorySource,
    now: Long,
    automatic: Boolean,
    ask: Boolean,
    noticeId: String,
  ): AiMemoryOutcome {
    val current = state
    val observed = (p.observedAtMillis ?: now).coerceAtMost(now)

    // Lo stesso fatto detto di nuovo: resta uno, con la data piu' recente e le parole di tutti e due. Nessun avviso.
    current.memories.firstOrNull { sameText(it.text, p.text) }?.let { same ->
      // Un'app che propone il testo di un ricordo non suo (dell'utente, di un'altra fonte) non lo cambia: ne' la scadenza,
      // ne' la chiave, ne' le parole o le prove. Sa solo che era un doppione (D2 di Alessio, R3-S1b): se no, indovinando il
      // testo, potrebbe farlo scadere.
      if (source.fromApp && !(same.source.fromApp && same.source.app == source.app)) {
        return AiMemoryOutcome(AiMemoryProposalResult.OutcomeDuplicate, same)
      }
      val refreshed = same.copy(
        observedAtMillis = maxOf(same.observedAtMillis, observed),
        topics = (same.topics + p.topics).distinct().take(AiMemoryProposal.MaxTopics),
        key = same.key ?: p.key,
        expiresAtMillis = p.expiresAtMillis ?: same.expiresAtMillis,
        evidence = (same.evidence + p.evidence).distinctBy { it.id },
      )
      commit(current.copy(memories = current.memories.replace(refreshed)), changed = listOf(refreshed))
      return AiMemoryOutcome(AiMemoryProposalResult.OutcomeDuplicate, refreshed)
    }
    // La stessa domanda gia' in attesa: niente di nuovo.
    if (ask && current.pending.any { sameText(it.proposal.text, p.text) }) return AiMemoryOutcome(AiMemoryProposalResult.OutcomeDuplicate)

    // La stessa chiave: una contraddizione. Un fatto detto dall'utente vince su uno letto o dedotto; fra due dello stesso
    // tipo vince il piu' recente. E lo si dice.
    val rival = p.key?.let { k -> current.memories.firstOrNull { it.key == k } }
    if (rival != null) {
      val keepRival = when {
        rival.origin == AiMemory.OriginStatement && p.origin != AiMemory.OriginStatement -> true
        p.origin == AiMemory.OriginStatement && rival.origin != AiMemory.OriginStatement -> false
        else -> observed < rival.observedAtMillis
      }
      if (keepRival) {
        val notice = AiMemoryNotice(noticeId, AiMemoryNotice.KindKept, rival.id, rival.text, previousText = p.text, source = source, atMillis = now)
        publish(notice)
        return AiMemoryOutcome(AiMemoryProposalResult.OutcomeKept, rival, notice)
      }
    }

    // I tetti: della memoria, dei ricordi automatici del giorno, di un'app (le domande in attesa contano).
    val limits = config.limits
    if (rival == null && current.memories.size >= limits.maxMemories) return AiMemoryOutcome(AiMemoryProposalResult.OutcomeLimitReached)
    if (rival == null && automatic && todayCount(current, now) { it.kind == AiMemorySource.KindChat } >= limits.maxAutomaticPerDay) {
      return AiMemoryOutcome(AiMemoryProposalResult.OutcomeLimitReached)
    }
    if (rival == null && source.fromApp && todayCount(current, now) { it.kind == AiMemorySource.KindApp && it.app == source.app } >= limits.maxPerAppPerDay) {
      return AiMemoryOutcome(AiMemoryProposalResult.OutcomeLimitReached)
    }

    if (ask) {
      val entry = PendingEntry(noticeId, p, source, automatic, now, replacing = rival?.text)
      val kept = (listOf(entry) + current.pending).take(limits.maxPending)
      commit(current.copy(pending = kept))
      val notice = askNotice(entry)
      publish(notice)
      return AiMemoryOutcome(AiMemoryProposalResult.OutcomePending, rival, notice)
    }

    if (rival != null) {
      val replaced = rival.copy(
        text = p.text,
        topics = p.topics.ifEmpty { rival.topics },
        source = source,
        updatedAtMillis = now,
        observedAtMillis = observed,
        expiresAtMillis = p.expiresAtMillis,
        always = p.always,
        previousText = rival.text,
        previousSinceMillis = rival.observedAtMillis,
        edited = false,
        value = p.value,
        origin = p.origin,
        evidence = p.evidence,
        limitations = p.limitations,
        derivedBy = p.derivedBy,
      )
      val notice = AiMemoryNotice(noticeId, AiMemoryNotice.KindUpdated, rival.id, replaced.text, previousText = rival.text, source = source, atMillis = now)
      val undo = UndoEntry(notice.id, rival.id, now, before = rival, app = source.app.takeIf { source.fromApp }, atMillis = now)
      commit(current.copy(memories = current.memories.replace(replaced), undo = listOf(undo) + current.undo), changed = listOf(replaced))
      markStale(rival.id, except = noticeId)
      publish(notice)
      return AiMemoryOutcome(AiMemoryProposalResult.OutcomeUpdated, replaced, notice)
    }

    val memory = AiMemory(
      id = newId(),
      text = p.text,
      key = p.key,
      topics = p.topics,
      source = source,
      createdAtMillis = now,
      updatedAtMillis = now,
      observedAtMillis = observed,
      expiresAtMillis = p.expiresAtMillis,
      always = p.always,
      value = p.value,
      origin = p.origin,
      evidence = p.evidence,
      limitations = p.limitations,
      derivedBy = p.derivedBy,
    )
    val notice = AiMemoryNotice(noticeId, AiMemoryNotice.KindRemembered, memory.id, memory.text, source = source, atMillis = now)
    val undo = UndoEntry(notice.id, memory.id, now, before = null, app = source.app.takeIf { source.fromApp }, atMillis = now)
    commit(current.copy(memories = current.memories + memory, undo = listOf(undo) + current.undo), changed = listOf(memory))
    publish(notice)
    return AiMemoryOutcome(AiMemoryProposalResult.OutcomeRemembered, memory, notice)
  }

  /**
   * La risposta a una domanda «Vuoi che ricordi…?»: [accept] vero (Ricorda) e il fatto entra, con «Ho ricordato…» e
   * Annulla al posto della domanda; falso (No) e non entra, e con [exclude] la memoria non chiede piu' di quell'argomento.
   * Con [app] risponde solo se la domanda e' nata da quell'app (il ponte). [AiMemoryProposalResult.OutcomeNotFound] se la
   * domanda non c'e' (gia' risposta, scaduta, di un'altra app).
   */
  suspend fun answer(noticeId: String, accept: Boolean, exclude: Boolean = false, app: String? = null): AiMemoryOutcome = mutex.withLock {
    val current = state
    val entry = current.pending.firstOrNull { it.noticeId == noticeId } ?: return@withLock AiMemoryOutcome(AiMemoryProposalResult.OutcomeNotFound)
    if (app != null && entry.source.app != app) return@withLock AiMemoryOutcome(AiMemoryProposalResult.OutcomeNotFound)
    val rest = current.pending.filter { it !== entry }
    if (!accept) {
      val key = entry.proposal.key
      val exclusions = if (exclude && key != null) (current.exclusions + AiMemoryExclusion(key = key)).distinct() else current.exclusions
      commit(current.copy(pending = rest, exclusions = exclusions))
      val declined = askNotice(entry).copy(declined = true)
      replaceNotice(declined)
      return@withLock AiMemoryOutcome(AiMemoryProposalResult.OutcomeDeclined, notice = declined)
    }
    // Ricorda vale con le regole di adesso, non con quelle di quando e' nata la domanda (R3-S1): un'app bloccata, un
    // argomento escluso dopo, un fatto gia' scaduto chiudono la domanda senza che il fatto entri.
    val now = clock()
    val refused = when {
      entry.source.fromApp && entry.source.app in current.blockedApps -> AiMemoryProposalResult.OutcomeNotAllowed
      current.exclusions.any { it.matches(entry.proposal.key, entry.source.app, entry.proposal.evidence) } -> AiMemoryProposalResult.OutcomeExcluded
      (entry.proposal.expiresAtMillis ?: Long.MAX_VALUE) <= now -> AiMemoryProposalResult.OutcomeRefused
      else -> null
    }
    commit(current.copy(pending = rest))
    if (refused != null) {
      closeAsks(setOf(noticeId))
      return@withLock AiMemoryOutcome(refused)
    }
    val outcome = storeLocked(entry.proposal, entry.source, now, entry.automatic, ask = false, noticeId = noticeId)
    if (outcome.notice == null) {
      // Lo sapeva gia' (un doppione): la domanda si chiude come risposta, senza un ricordo nuovo da annullare.
      replaceNotice(askNotice(entry).copy(stale = true))
    }
    outcome
  }

  /**
   * Il dopo-turno: le regole ([AiMemoryPolicy.automaticSkip]), il filtro locale, le app (una domanda arrivata da un'app
   * che non puo' proporre ricordi non lascia ricordi automatici: risposta 3 di Alessio), il tetto del giorno, poi
   * l'estrattore coi ricordi gia' noti che c'entrano, e le sue proposte. Se qualcosa ferma prima del modello, il costo e'
   * zero. Un «ricordati che…» nel messaggio entra subito; il resto, di serie, chiede.
   */
  suspend fun afterTurn(turn: AiMemoryTurn, extractor: AiMemoryExtractor): AiMemoryAfterTurn {
    val skip = AiMemoryPolicy.automaticSkip(turn)
      ?: turn.app?.let { if (config.apps.entryFor(it) == null) AiMemoryPolicy.SkipApp else null }
      ?: if (!AiMemorySignals.mayCarryFacts(turn.userText)) AiMemoryPolicy.SkipNoSignal else null
    if (skip != null) return AiMemoryAfterTurn(AiMemoryExtraction(emptyList(), skipped = skip), emptyList())
    if (todayCount(state, clock()) { it.kind == AiMemorySource.KindChat } >= config.limits.maxAutomaticPerDay) {
      return AiMemoryAfterTurn(AiMemoryExtraction(emptyList(), skipped = AiMemoryPolicy.SkipDailyCap), emptyList())
    }
    val known = relevant(turn.userText, extractor.config.knownMemories, turn.language).map { it.memory }
    val keys = state.memories.sortedByDescending { it.observedAtMillis }.mapNotNull { it.key }.distinct()
    val extraction = extractor.extract(turn, known, keys)
    val asked = AiMemorySignals.asksToRemember(turn.userText) && AiMemoryPolicy.explicit(turn) == AiMemoryPolicy.VerdictAllowed
    val outcomes = extraction.proposals.map { remember(it, turn.source, confirmed = asked, automatic = true) }
    return AiMemoryAfterTurn(extraction, outcomes)
  }

  /**
   * Annulla l'avviso [noticeId]: un ricordo nuovo sparisce, uno aggiornato torna com'era. Vale finche' il ricordo non e'
   * cambiato di nuovo (una correzione, un altro aggiornamento): allora torna falso. Con [app] annulla solo se l'avviso e'
   * nato da quell'app (il ponte: un'app annulla solo le sue proposte).
   */
  suspend fun undo(noticeId: String, app: String? = null): Boolean = mutex.withLock {
    val current = state
    val entry = current.undo.firstOrNull { it.noticeId == noticeId } ?: return@withLock false
    if (app != null && entry.app != app) return@withLock false
    val memory = current.memories.firstOrNull { it.id == entry.memoryId }
    val rest = current.undo.filter { it !== entry }
    if (memory == null || memory.updatedAtMillis != entry.afterUpdatedAt) {
      commit(current.copy(undo = rest))
      return@withLock false
    }
    val before = entry.before
    // La versione di prima torna solo se vale ancora (R3-S1): non scaduta, e non da un argomento o una fonte che l'utente
    // ha escluso dopo (le fonti revocate tolgono l'annulla da se': [forgetWhere]).
    if (before != null && (before.expired(clock()) ||
        (before.origin != AiMemory.OriginStatement && current.exclusions.any { it.matches(before.key, before.source.app, before.evidence) }))) {
      commit(current.copy(undo = rest))
      return@withLock false
    }
    if (before == null) {
      commit(current.copy(memories = current.memories.filter { it.id != memory.id }, undo = rest, vectors = current.vectors - memory.id), deleted = listOf(memory.id))
    } else {
      commit(current.copy(memories = current.memories.replace(before), undo = rest), changed = listOf(before))
    }
    _notices.value = _notices.value.map { if (it.id == noticeId) it.copy(undone = true) else it }
    true
  }

  /** Toglie l'avviso [noticeId] dagli avvisi recenti (la card chiusa); il ricordo (o la domanda) resta. */
  fun dismissNotice(noticeId: String) {
    _notices.value = _notices.value.filter { it.id != noticeId }
  }

  /** Accende o spegne il salvataggio automatico (si salva con la memoria). Le domande gia' in attesa restano domande. */
  suspend fun setAutoSave(on: Boolean) = mutex.withLock {
    if (state.autoSave != on) commit(state.copy(autoSave = on))
  }

  /** Corregge il testo del ricordo [id] (dalla pagina): resta la fonte, cambia la data di modifica. Null se non c'e'. */
  suspend fun correct(id: String, text: String): AiMemory? = mutex.withLock {
    val clean = AiMemoryProposal(text).sanitized()?.text ?: return@withLock null
    if (AiMemoryPolicy.looksSecret(clean)) return@withLock null
    val m = state.memories.firstOrNull { it.id == id } ?: return@withLock null
    if (m.text == clean) return@withLock m
    // Corretto dall'utente: e' un fatto detto da lui, che un fatto dedotto non sostituira'.
    val fixed = m.copy(text = clean, edited = true, updatedAtMillis = clock(), origin = AiMemory.OriginStatement)
    commit(state.copy(memories = state.memories.replace(fixed)), changed = listOf(fixed))
    markStale(id)
    fixed
  }

  /** Fissa (sempre nel prompt, dentro il tetto) o sfissa il ricordo [id]. */
  suspend fun setPinned(id: String, pinned: Boolean): Boolean = update(id) { it.copy(pinned = pinned) }

  /** Cambia la scadenza del ricordo [id] (null: non scade). Come [setPinned], non cambia il fatto: Annulla resta possibile. */
  suspend fun setExpiry(id: String, expiresAtMillis: Long?): Boolean = update(id) { it.copy(expiresAtMillis = expiresAtMillis) }

  private suspend fun update(id: String, change: (AiMemory) -> AiMemory): Boolean = mutex.withLock {
    val m = state.memories.firstOrNull { it.id == id } ?: return@withLock false
    val next = change(m)
    commit(state.copy(memories = state.memories.replace(next)), changed = listOf(next))
    true
  }

  /**
   * Dimentica il ricordo [id]. Le card che lo nominano diventano «Non lo ricordo più». Con [exclude] la memoria non
   * impara piu' quell'argomento (la sua chiave) se non lo chiede l'utente.
   */
  suspend fun forget(id: String, exclude: Boolean = false): Boolean {
    val key = state.memories.firstOrNull { it.id == id }?.key
    val gone = forgetWhere { it.id == id } > 0
    if (gone && exclude && key != null) this.exclude(AiMemoryExclusion(key = key))
    return gone
  }

  /** Dimentica tutto (i ricordi, le domande, gli annulla, i vettori). Restano le app bloccate, le esclusioni e l'interruttore. */
  suspend fun forgetAll(): Int = forgetWhere(asks = { true }) { true }

  /**
   * Dimentica i ricordi nati dall'app [app]: quelli che ha proposto lei, quelli detti nella chat «Dalle app» partita da
   * lei, quelli chiesti dal modello coi suoi dati e quelli imparati dalla ricerca sui suoi dati ([onlyProposed] vero: solo
   * i primi, la revoca chiesta dall'app stessa col ponte). Per non riceverne altri: [setAppBlocked].
   */
  suspend fun forgetFromApp(app: String, onlyProposed: Boolean = false): Int = forgetWhere(
    asks = { p -> (p.source.app == app || p.proposal.evidence.any { it.app == app }) && (!onlyProposed || p.source.fromApp) },
  ) { m -> (m.source.app == app || m.evidence.any { it.app == app }) && (!onlyProposed || m.source.fromApp) }

  /**
   * Dimentica cio' che e' stato letto o dedotto dalla fonte autorizzata [grant] (e, con [record], solo da quel dato): la
   * revoca di una fonte della ricerca. I fatti detti dall'utente restano, anche se una prova veniva da li'. Con loro se ne
   * vanno le domande ancora aperte che vengono da li' e le versioni di prima che Annulla rimetterebbe (R3-S1).
   */
  suspend fun forgetFromGrant(grant: String, record: String? = null): Int {
    fun from(evidence: List<AiMemoryEvidence>) = evidence.any { it.grant == grant && (record == null || it.record == record) }
    return forgetWhere(asks = { from(it.proposal.evidence) }) { m -> m.origin != AiMemory.OriginStatement && from(m.evidence) }
  }

  /**
   * Toglie, in una scrittura sola, i ricordi [which], le versioni di prima che Annulla rimetterebbe e che [which] toglie
   * anche loro, e le domande ancora aperte [asks] (R3-S1: una revoca vale anche per cio' che aspetta un si' o un
   * Annulla). Torna quanti ricordi.
   */
  private suspend fun forgetWhere(asks: (PendingEntry) -> Boolean = { false }, which: (AiMemory) -> Boolean): Int = mutex.withLock {
    val current = state
    val gone = current.memories.filter(which)
    val ids = gone.map { it.id }.toSet()
    val undo = current.undo.filter { u -> u.memoryId !in ids && u.before.let { it == null || !which(it) } }
    val closed = current.pending.filter(asks)
    if (gone.isEmpty() && undo.size == current.undo.size && closed.isEmpty()) return@withLock 0
    commit(
      current.copy(
        memories = current.memories.filter { it.id !in ids },
        undo = undo,
        vectors = current.vectors - ids,
        pending = current.pending - closed.toSet(),
      ),
      deleted = ids,
    )
    _notices.value = _notices.value.map { if (it.memoryId in ids) it.copy(undone = true) else it }
    closeAsks(closed.map { it.noticeId }.toSet())
    gone.size
  }

  /**
   * Non imparare piu' [exclusion] (un argomento, un'app, una fonte, un dato), se non lo chiede l'utente. Come
   * l'esclusione del profilo di oggi, toglie anche cio' che la memoria aveva **imparato** da li' (osservato o dedotto); le
   * cose dette dall'utente restano, finche' non le dimentica lui. Torna quanti ricordi ha tolto.
   */
  suspend fun exclude(exclusion: AiMemoryExclusion): Int {
    mutex.withLock {
      if (exclusion !in state.exclusions) commit(state.copy(exclusions = state.exclusions + exclusion))
    }
    // Le domande aperte su quell'argomento si chiudono: non le ha chieste l'utente (come in [remember]).
    return forgetWhere(asks = { exclusion.matches(it.proposal.key, it.source.app, it.proposal.evidence) }) { m ->
      m.origin != AiMemory.OriginStatement && exclusion.matches(m.key, m.source.app, m.evidence)
    }
  }

  /** Toglie l'esclusione [exclusion]: la memoria puo' di nuovo imparare quell'argomento. */
  suspend fun include(exclusion: AiMemoryExclusion) = mutex.withLock {
    if (exclusion in state.exclusions) commit(state.copy(exclusions = state.exclusions - exclusion))
  }

  /**
   * Toglie (o ridà) all'app [app] il permesso di proporre ricordi. I suoi ricordi restano ([forgetFromApp]); le sue
   * proposte ancora da confermare no (R3-S1): senza permesso non entrano neanche con Ricorda.
   */
  suspend fun setAppBlocked(app: String, blocked: Boolean) = mutex.withLock {
    val next = if (blocked) state.blockedApps + app else state.blockedApps - app
    val closed = if (blocked) state.pending.filter { it.source.fromApp && it.source.app == app } else emptyList()
    if (next != state.blockedApps || closed.isNotEmpty()) commit(state.copy(blockedApps = next, pending = state.pending - closed.toSet()))
    closeAsks(closed.map { it.noticeId }.toSet())
  }

  /**
   * I ricordi che c'entrano con [question], dal piu' pertinente, senza gli scaduti. Col modello del telefono
   * ([AiMemoryConfig.onDevice]) la domanda si allarga con le parole del suo argomento, entro il suo tempo massimo.
   */
  suspend fun relevant(question: String, limit: Int = 8, language: String = "it"): List<AiMemoryMatch> {
    val extra = config.onDevice?.let { AiMemoryQueryExpansion.expand(it, question, language, config.onDeviceTimeoutMillis) }.orEmpty()
    val hits = index.search(question, limit * 2, extra)
    // Dopo l'attesa (il modello del telefono, l'indice) valgono i ricordi e l'ora di adesso: uno tolto o scaduto nel
    // frattempo non torna (R3-S1).
    val now = clock()
    val byId = state.memories.associateBy { it.id }
    return hits.mapNotNull { (id, score, meaning) -> byId[id]?.takeIf { !it.expired(now) }?.let { AiMemoryMatch(it, score, meaning) } }
      .take(limit)
  }

  /**
   * Il blocco dei ricordi per il prompt della domanda [question]: i fissati, quelli «per ogni domanda», poi i
   * pertinenti, finche' stanno in [budget]. Vuoto se niente c'entra: la domanda non paga gettoni per la memoria.
   * Gli scaduti e quelli che sembrano un segreto non entrano mai; le domande in attesa nemmeno.
   */
  suspend fun prompt(question: String, budget: AiMemoryPromptBudget = config.prompt, language: String = "it"): AiMemoryPrompt {
    val found = relevant(question, budget.maxItems, language).map { it.memory.id }
    // Tutto si legge dopo l'attesa, senza piu' sospendere fino al blocco: un ricordo tolto, sfissato o scaduto mentre il
    // modello del telefono pensava non entra (R3-S1).
    val now = clock()
    val live = state.memories.filter { !it.expired(now) && !AiMemoryPolicy.looksSecret(it.text) }
    val pinned = live.filter { it.pinned }.sortedByDescending { it.observedAtMillis }
    val always = live.filter { it.always && !it.pinned }.sortedByDescending { it.observedAtMillis }.take(budget.maxAlways)
    val taken = (pinned + always).map { it.id }.toHashSet()
    val liveById = live.associateBy { it.id }
    val pertinent = found.mapNotNull { liveById[it] }.filter { it.id !in taken }
    return AiMemoryPrompt.build(pinned + always + pertinent, budget, language, config.zone)
  }

  /**
   * La manutenzione: toglie i ricordi scaduti da piu' di [AiMemoryLimits.keepExpiredDays] giorni e le domande senza
   * risposta da piu' di [AiMemoryLimits.pendingDays]. Torna quanti ricordi.
   */
  suspend fun maintain(): Int {
    val now = clock()
    val cutoff = now - config.limits.keepExpiredDays * DayMillis
    val n = forgetWhere { m -> (m.expiresAtMillis ?: Long.MAX_VALUE) < cutoff }
    mutex.withLock {
      val fresh = state.pending.filter { it.atMillis >= now - config.limits.pendingDays * DayMillis }
      if (fresh.size != state.pending.size) {
        val gone = (state.pending - fresh.toSet()).map { it.noticeId }.toSet()
        commit(state.copy(pending = fresh))
        closeAsks(gone)
      }
    }
    return n
  }

  /**
   * Porta dentro la memoria di prima (Room v7 di PampAI, [AiMemoryLegacyImport]) **senza perdere niente**: ogni riga
   * entra con il suo testo intero, la sua data, la conversazione e la fissatura. Si puo' rifare: le righe gia' passate
   * (per id) non si duplicano, e uno stesso testo che c'e' gia' resta uno. I tetti della memoria non valgono qui, e non
   * si chiede niente: l'utente li aveva gia' chiesti.
   */
  suspend fun importLegacy(rows: List<AiMemoryLegacyRow>): AiMemoryImport = mutex.withLock {
    val current = state
    var already = 0
    var duplicates = 0
    var empty = 0
    var secret = 0
    val added = ArrayList<AiMemory>()
    val marks = HashSet(current.imported)
    for (row in rows) {
      val mark = LegacyMark + row.id
      if (mark in marks) { already++; continue }
      val text = AiMemoryProposal.cleanText(row.text)
      if (text == null) { empty++; marks += mark; continue }
      if ((current.memories + added).any { sameText(it.text, text) }) { duplicates++; marks += mark; continue }
      if (AiMemoryPolicy.looksSecret(text)) secret++
      added += AiMemory(
        id = "legacy-${row.id}",
        text = text,
        source = AiMemorySource.imported(row.sourceConversationId),
        createdAtMillis = row.createdAtMillis,
        updatedAtMillis = row.createdAtMillis,
        observedAtMillis = row.createdAtMillis,
        pinned = row.pinned,
      )
      marks += mark
    }
    if (added.isNotEmpty() || marks.size != current.imported.size) {
      commit(current.copy(memories = current.memories + added, imported = marks), changed = added)
    }
    AiMemoryImport(added.size, already, duplicates, empty, secret)
  }

  /**
   * Fonde nella memoria il **profilo della ricerca** di PampAI (F9.3b, risposta 6 di Alessio: «deve essere una cosa
   * unica»): ogni fatto attivo diventa un ricordo con la chiave del campo, il valore, l'origine (detto, letto o
   * dedotto), le prove con le fonti autorizzate, i limiti e chi l'ha dedotto; le esclusioni diventano esclusioni. Il
   * testo e' «[label] del campo: valore» (PampAI puo' passare le sue parole per i campi). Si puo' rifare senza doppioni. Se
   * c'e' gia' un ricordo con la stessa chiave, valgono le regole delle contraddizioni (un fatto detto dall'utente resta).
   */
  suspend fun importProfile(
    rows: List<AiMemoryProfileRow>,
    exclusions: List<AiMemoryExclusion> = emptyList(),
    label: (String) -> String = ::profileLabel,
  ): AiMemoryImport = mutex.withLock {
    val current = state
    var already = 0
    var duplicates = 0
    var empty = 0
    var inactive = 0
    var excluded = 0
    val allExclusions = current.exclusions + exclusions.filter { it !in current.exclusions }
    val marks = HashSet(current.imported)
    val memories = current.memories.toMutableList()
    val changed = ArrayList<AiMemory>()
    val now = clock()
    for (row in rows) {
      val mark = ProfileMark + row.id
      if (mark in marks) { already++; continue }
      if (row.status != "ACTIVE") { inactive++; marks += mark; continue }
      val value = AiMemoryProposal.cleanText(row.value)
      if (value == null) { empty++; marks += mark; continue }
      val text = AiMemoryProposal.cleanText("${label(row.field)}: $value") ?: value
      val key = AiMemoryProposal.normalizeKey(row.field)
      val origin = when (row.kind) {
        "USER_STATEMENT" -> AiMemory.OriginStatement
        "INFERENCE" -> AiMemory.OriginInference
        else -> AiMemory.OriginObservation
      }
      val observed = row.observedAtMillis ?: row.evidence.mapNotNull { it.observedAtMillis }.maxOrNull() ?: now
      if (origin != AiMemory.OriginStatement && allExclusions.any { it.matches(key, row.app, row.evidence) }) {
        excluded++; marks += mark; continue
      }
      val rival = key?.let { k -> memories.firstOrNull { it.key == k } }
      if (memories.any { sameText(it.text, text) } || (rival != null && rival.origin == AiMemory.OriginStatement && origin != AiMemory.OriginStatement)) {
        duplicates++; marks += mark; continue
      }
      val memory = AiMemory(
        id = "profile-${row.id}",
        text = text,
        key = key,
        topics = AiMemoryProposal.normalizeTopics(row.field.split('.', '_', '-')),
        source = AiMemorySource.research(row.app, row.appLabel),
        createdAtMillis = now,
        updatedAtMillis = now,
        observedAtMillis = observed,
        value = value,
        origin = origin,
        evidence = row.evidence,
        limitations = row.limitations?.let { AiMemoryProposal.cleanText(it) },
        derivedBy = row.derivedBy,
        previousText = rival?.text,
        previousSinceMillis = rival?.observedAtMillis,
      )
      if (rival != null) memories.removeAll { it.id == rival.id }
      memories += memory
      changed += memory
      marks += mark
    }
    val newExclusions = exclusions.filter { it !in current.exclusions }
    if (changed.isNotEmpty() || marks.size != current.imported.size || newExclusions.isNotEmpty()) {
      val removed = current.memories.map { it.id }.toSet() - memories.map { it.id }.toSet()
      commit(current.copy(memories = memories, imported = marks, exclusions = current.exclusions + newExclusions), changed = changed, deleted = removed)
    }
    AiMemoryImport(changed.size, already, duplicates, empty, secretLike = 0, inactive = inactive, exclusions = newExclusions.size, excluded = excluded)
  }

  /** Chiude l'indice in memoria. La memoria sul disco resta. */
  fun close() = index.close()

  /** Scrive [next] sul disco, poi aggiorna l'indice e cio' che si vede. Chiamato col lucchetto preso. */
  private suspend fun commit(next: MemoryState, changed: List<AiMemory> = emptyList(), deleted: Collection<String> = emptyList()) {
    val trimmed = next.copy(undo = next.undo.take(config.limits.undo))
    index.delete(deleted)
    val fresh = index.put(changed, trimmed.vectors)
    val final = if (fresh.isEmpty()) trimmed else trimmed.copy(vectors = trimmed.vectors + fresh)
    try {
      store.write(MemoryCodec.encode(final))
    } catch (e: Throwable) {
      // Il disco ha rifiutato: l'indice torna quello dei ricordi di prima, e chi chiama riceve l'errore.
      runCatching { index.rebuild(state.memories, state.vectors) }
      throw e
    }
    state = final
    _memories.value = display(final.memories)
    _blocked.value = final.blockedApps
    _pending.value = pendingNotices(final)
    _exclusions.value = final.exclusions
    _autoSave.value = final.autoSave ?: config.autoSave
  }

  /** Le domande [noticeIds] tolte senza risposta (dimenticate, scadute): le loro card non offrono piu' Ricorda e No. */
  private fun closeAsks(noticeIds: Set<String>) {
    if (noticeIds.isEmpty()) return
    _notices.value = _notices.value.map { if (it.id in noticeIds && it.kind == AiMemoryNotice.KindAsk) it.copy(stale = true) else it }
  }

  /** Le card di prima del ricordo [memoryId] non offrono piu' Annulla: il fatto e' cambiato dopo di loro. */
  private fun markStale(memoryId: String, except: String? = null) {
    _notices.value = _notices.value.map { if (it.memoryId == memoryId && !it.undone && it.id != except) it.copy(stale = true) else it }
  }

  private fun publish(notice: AiMemoryNotice) {
    if (_notices.value.any { it.id == notice.id }) replaceNotice(notice)
    else _notices.value = (listOf(notice) + _notices.value).take(config.limits.notices)
  }

  /** Mette [notice] al posto dell'avviso con lo stesso id (la domanda che diventa «Ho ricordato…»), o in cima. */
  private fun replaceNotice(notice: AiMemoryNotice) {
    val list = _notices.value
    _notices.value = if (list.any { it.id == notice.id }) list.map { if (it.id == notice.id) notice else it }
    else (listOf(notice) + list).take(config.limits.notices)
  }

  private fun todayCount(state: MemoryState, now: Long, which: (AiMemorySource) -> Boolean): Int =
    state.memories.count { it.createdAtMillis >= now - DayMillis && which(it.source) } +
      state.pending.count { it.atMillis >= now - DayMillis && which(it.source) }

  companion object {
    /** Il nome della sezione della cassaforte di serie. */
    const val VaultSectionName = "ai-memory"

    private const val DayMillis = 86_400_000L
    private const val LegacyMark = "room7:"
    private const val ProfileMark = "aria:"

    /**
     * La memoria dell'app [context], nella sezione [VaultSectionName] della sua cassaforte (`VaultAccess.Device()`:
     * cifrata col Keystore, si apre da sola, fuori dal backup). Va aperta fuori dal thread principale.
     */
    suspend fun open(context: Context, config: AiMemoryConfig = AiMemoryConfig()): AiMemoryBook =
      open(AiMemoryStore.vault(FluidVault.get(context).section(VaultSectionName, VaultAccess.Device())), config)

    /**
     * La memoria nel deposito [store] (per le prove e la galleria: [AiMemoryStore.inMemory], o una cassaforte di prova).
     * Un file che non si legge (una versione futura, byte rovinati) fa lanciare l'eccezione: la memoria non si azzera da
     * sola. [clock] e' l'ora del muro, in millisecondi.
     */
    suspend fun open(store: AiMemoryStore, config: AiMemoryConfig = AiMemoryConfig(), clock: () -> Long = System::currentTimeMillis): AiMemoryBook {
      val bytes = store.read()
      val initial = if (bytes == null) MemoryState() else MemoryCodec.decode(bytes)
      val index = AiMemoryIndex.create(config.embedder)
      val fresh = index.rebuild(initial.memories, initial.vectors)
      val state = if (fresh.isEmpty()) initial else initial.copy(vectors = initial.vectors + fresh)
      if (fresh.isNotEmpty()) store.write(MemoryCodec.encode(state))
      return AiMemoryBook(store, index, config, clock, state)
    }

    /** Le parole di un campo del profilo: «musica.artista_preferito» → «musica artista preferito». */
    fun profileLabel(field: String): String = field.replace(Regex("[._-]+"), " ").trim()

    private fun newId(): String = UUID.randomUUID().toString()

    private fun askNotice(p: PendingEntry) = AiMemoryNotice(p.noticeId, AiMemoryNotice.KindAsk, memoryId = "", text = p.proposal.text,
      previousText = p.replacing, source = p.source, atMillis = p.atMillis)

    private fun pendingNotices(state: MemoryState): List<AiMemoryNotice> = state.pending.sortedByDescending { it.atMillis }.map(::askNotice)

    /** I fissati prima, poi dal piu' recente. */
    private fun display(memories: List<AiMemory>): List<AiMemory> =
      memories.sortedWith(compareByDescending<AiMemory> { it.pinned }.thenByDescending { it.observedAtMillis }.thenBy { it.id })

    /** Lo stesso fatto, a meno di maiuscole, accenti, punteggiatura e spazi. */
    internal fun sameText(a: String, b: String): Boolean = key(a) == key(b)

    private fun key(text: String): String =
      AiMemorySignals.normalize(text).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun List<AiMemory>.replace(m: AiMemory): List<AiMemory> = map { if (it.id == m.id) m else it }
  }
}
