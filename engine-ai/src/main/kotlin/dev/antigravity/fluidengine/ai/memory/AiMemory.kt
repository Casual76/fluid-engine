package dev.antigravity.fluidengine.ai.memory

// F9.3 · Una memoria che funziona (`ricordi-dell-assistente`). Qui stanno solo le parole comuni: cos'e' un ricordo, da
// dove viene, l'avviso «Ho ricordato…» e il formato con cui un'app propone un ricordo a PampAI. Le usano tre parti
// diverse, ed e' per questo che stanno in `engine-ai`, che tutte hanno gia':
// - PampAI, col motore della memoria (`engine-ai-memory`, opt-in: la cassaforte, la pertinenza, l'estrattore, la revoca);
// - l'interfaccia di `engine-ai-ui`, pacchetto `aiui.memory` («Ho ricordato…» con Annulla, «Cosa ricordo di te»);
// - le app che propongono un ricordo col ponte (ClasseViva, Pampa Mail, Pampa Notes), che non includono il motore.
// Nessuna di queste classi tocca il disco o la rete.

/**
 * Da dove viene un ricordo: la conversazione in cui l'utente l'ha detto, l'app Pampa che l'ha proposto, la pagina dove
 * l'ha scritto a mano, o la memoria di prima di PampAI. La pagina «Cosa ricordo di te» lo mostra sotto ogni ricordo, e la
 * revoca per app ([app]) lo usa per trovare «quelli nati da ClasseViva».
 *
 * [kind] e' una delle costanti `Kind*`: una stringa e non un enum, cosi' un tipo nuovo (per esempio la ricerca autonoma
 * del profilo di PampAI) si aggiunge senza allargare un enum pubblico.
 */
data class AiMemorySource(
  val kind: String,
  /** La conversazione di PampAI in cui e' stato detto (solo per [KindChat] e [KindImported]). */
  val conversationId: Long? = null,
  /**
   * Il pacchetto dell'app: per [KindApp] quella che l'ha proposto; per [KindChat] l'app da cui arrivava la domanda (la
   * chat «Dalle app» di PampAI, F9.1), altrimenti null.
   */
  val app: String? = null,
  /** Il nome dell'app da mostrare («ClasseViva»), detto dal sistema e non dall'app. */
  val appLabel: String? = null,
) {
  /** Vero se l'ha proposto un'altra app col ponte. */
  val fromApp: Boolean get() = kind == KindApp

  companion object {
    /** Detto dall'utente in chat: l'estrattore dopo il turno, o «ricordati che…». */
    const val KindChat = "chat"

    /** Proposto da un'altra app Pampa col ponte firmato (F9.1), passando da PampAI. */
    const val KindApp = "app"

    /** Scritto a mano dall'utente nella pagina «Cosa ricordo di te». */
    const val KindManual = "manual"

    /** Portato dalla memoria di prima di PampAI (la tabella `memories` di Room v7). */
    const val KindImported = "imported"

    /**
     * Chiesto dal modello della chat di PampAI con lo strumento della memoria (`ricorda`), anche con dati letti da
     * un'altra app ([app]): e' l'unica strada per i ricordi delle app che non possono proporre da sole (F9.3b).
     */
    const val KindTool = "tool"

    /**
     * Imparato dalla ricerca autorizzata di PampAI sui dati di un'app collegata ([app]): il profilo della ricerca di
     * oggi, fuso nella memoria unica (F9.3b). Porta le sue fonti in [AiMemory.evidence].
     */
    const val KindResearch = "research"

    /** Detto in chat, nella conversazione [conversationId]; [app] se la domanda arrivava da un'altra app. */
    fun chat(conversationId: Long?, app: String? = null, appLabel: String? = null): AiMemorySource =
      AiMemorySource(KindChat, conversationId = conversationId, app = app, appLabel = appLabel)

    /** Proposto dall'app [packageName] (il nome lo dice il sistema: [label]). */
    fun app(packageName: String, label: String): AiMemorySource = AiMemorySource(KindApp, app = packageName, appLabel = label)

    /** Scritto a mano. */
    val Manual: AiMemorySource = AiMemorySource(KindManual)

    /** Dalla memoria di prima, nata nella conversazione [conversationId] (se si sa). */
    fun imported(conversationId: Long?): AiMemorySource = AiMemorySource(KindImported, conversationId = conversationId)

    /** Chiesto dal modello della chat [conversationId] con lo strumento della memoria; [app] se i dati erano di un'app. */
    fun tool(conversationId: Long?, app: String? = null, appLabel: String? = null): AiMemorySource =
      AiMemorySource(KindTool, conversationId = conversationId, app = app, appLabel = appLabel)

    /** Imparato dalla ricerca autorizzata sui dati dell'app [app] (la fonte vera sta nelle prove del ricordo). */
    fun research(app: String?, appLabel: String?): AiMemorySource = AiMemorySource(KindResearch, app = app, appLabel = appLabel)
  }
}

/**
 * Una prova di un ricordo: da quale fonte autorizzata e' venuto (F9.3b, il profilo della ricerca fuso nella memoria).
 * Revocare la fonte ([grant]) toglie i ricordi dedotti da lei; un dato tolto dall'app ([record]) toglie quelli che ne
 * dipendono.
 */
data class AiMemoryEvidence(
  /** L'identita' della prova (per PampAI: l'id di `aria_evidence`). */
  val id: String,
  /** L'autorizzazione con cui e' stata letta (per PampAI: l'id di `aria_grants`), o null. */
  val grant: String? = null,
  /** Il pacchetto dell'app che ha dato il dato. */
  val app: String? = null,
  /** Il dato dell'app da cui viene (la chiave del record), o null. */
  val record: String? = null,
  /** Quando il dato era vero, se si sa. */
  val observedAtMillis: Long? = null,
  /** Il tipo di dato dell'app («cv.grades», «fluidify.history_records»): un'esclusione puo' nominare lui. */
  val field: String? = null,
)

/**
 * Un'esclusione: cio' che l'utente non vuole che la memoria impari piu' (F9.3b; le esclusioni del profilo della ricerca
 * di oggi). Ogni campo non nullo deve combaciare: [key] e' l'argomento o il suo inizio («musica» esclude «musica.gusti»),
 * oppure il tipo di dato di una prova ([AiMemoryEvidence.field]: «cv.grades» esclude cio' che e' dedotto dai voti);
 * [app] l'app, [grant] e [record] la fonte autorizzata e il dato.
 */
data class AiMemoryExclusion(
  val key: String? = null,
  val app: String? = null,
  val grant: String? = null,
  val record: String? = null,
) {
  /** Vero se l'esclusione vale per un ricordo con [key], nato dall'app [app] con le prove [evidence]. */
  fun matches(key: String?, app: String?, evidence: List<AiMemoryEvidence> = emptyList()): Boolean {
    if (this.key == null && this.app == null && grant == null && record == null) return false
    if (this.key != null && !under(key) && evidence.none { under(it.field) }) return false
    if (this.app != null && app != this.app && evidence.none { it.app == this.app }) return false
    if (grant != null && evidence.none { it.grant == grant }) return false
    if (record != null && evidence.none { it.record == record }) return false
    return true
  }

  /** Vero se [name] e' [key] o comincia con lui seguito da un punto. */
  private fun under(name: String?): Boolean {
    val k = key ?: return false
    return name != null && (name == k || name.startsWith("$k."))
  }
}

/**
 * Un fatto che l'assistente ricorda dell'utente: «è in 4F al liceo Galilei», «è vegetariana», «la verifica di matematica
 * e' venerdi' 28». Un valore intero e immutabile: chi lo cambia ne fa una copia (regola F7.21).
 *
 * **Le contraddizioni.** Due ricordi con la stessa [key] parlano della stessa cosa («scuola.classe»): ne resta uno solo,
 * quello detto piu' di recente ([observedAtMillis]), e quello di prima resta scritto in [previousText] perche' la pagina e
 * l'avviso possano dirlo («prima: è in 4F»). Un ricordo senza chiave non si contraddice con nessuno: solo lo stesso testo
 * si riconosce come doppio.
 */
data class AiMemory(
  /** L'identita', stabile per tutta la vita del ricordo (anche quando lo si corregge). */
  val id: String,
  /** Il fatto, una frase breve in terza persona, al piu' [AiMemoryProposal.MaxTextChars] caratteri. */
  val text: String,
  /** L'argomento, per riconoscere le contraddizioni («scuola.classe», «cibo.dieta»); null se non si sa. */
  val key: String? = null,
  /** Le parole per cui serve («scuola», «compiti»): aiutano la ricerca per pertinenza a trovarlo dalla domanda. */
  val topics: List<String> = emptyList(),
  val source: AiMemorySource,
  /** Quando e' entrato nella memoria. */
  val createdAtMillis: Long,
  /**
   * L'ultima volta che e' cambiato il fatto (il testo, da un aggiornamento o da una correzione). Fissarlo o cambiarne la
   * scadenza non lo tocca: Annulla resta possibile.
   */
  val updatedAtMillis: Long = createdAtMillis,
  /** Quando il fatto e' stato detto: decide chi vince fra due ricordi che si contraddicono. */
  val observedAtMillis: Long = createdAtMillis,
  /** Da quando non vale piu' (la verifica di venerdi'); null: non scade. Scaduto, non entra piu' nelle domande. */
  val expiresAtMillis: Long? = null,
  /** Fissato dall'utente: entra in ogni domanda, dentro il tetto. */
  val pinned: Boolean = false,
  /** Vale per ogni domanda (il nome, come vuole che gli si risponda): entra anche quando la domanda non lo nomina. */
  val always: Boolean = false,
  /** Il testo del ricordo che questo ha sostituito (stessa [key]), per dirlo. */
  val previousText: String? = null,
  /** Quando era stato detto [previousText]. */
  val previousSinceMillis: Long? = null,
  /** Vero se l'utente l'ha corretto a mano. */
  val edited: Boolean = false,
  /** Il valore strutturato, quando c'e' («4F» per «è in 4F al liceo Galilei»): quello che il profilo chiamava `value`. */
  val value: String? = null,
  /**
   * Come e' nato: [OriginStatement] (detto o confermato dall'utente, o un dato di un'app), [OriginObservation] (letto da
   * una fonte autorizzata) o [OriginInference] (dedotto da un modello dalle fonti). Un fatto detto dall'utente non lo
   * sostituisce mai uno letto o dedotto, nemmeno se piu' recente.
   */
  val origin: String = OriginStatement,
  /** Le fonti autorizzate da cui viene (vuoto per quelli detti in chat). */
  val evidence: List<AiMemoryEvidence> = emptyList(),
  /** Cosa il fatto non copre («solo gli ultimi 30 giorni di ascolti»), per i fatti dedotti. */
  val limitations: String? = null,
  /** Chi l'ha dedotto («groq/llama-3.1-8b-instant»), per i fatti dedotti. */
  val derivedBy: String? = null,
) {
  /** Vero se a [nowMillis] e' gia' scaduto. */
  fun expired(nowMillis: Long): Boolean = expiresAtMillis != null && nowMillis >= expiresAtMillis

  companion object {
    /** Detto o confermato dall'utente (anche scritto a mano), o un dato proposto da un'app. */
    const val OriginStatement = "statement"

    /** Letto da una fonte autorizzata, cosi' com'e' (la ricerca del profilo). */
    const val OriginObservation = "observation"

    /** Dedotto da un modello dalle fonti (la ricerca del profilo): ha [limitations] e [derivedBy]. */
    const val OriginInference = "inference"
  }
}

/**
 * L'avviso che accompagna ogni ricordo nuovo, perche' **niente si ricordi senza che l'utente lo veda**: di serie la card
 * «Vuoi che ricordi…?» con Ricorda e No ([KindAsk], scelta di Alessio del 26/11/2026), o, col salvataggio automatico
 * acceso, «Ho ricordato…» con Annulla; sotto la risposta o come notifica di vetro, di `engine-ai-ui`.
 *
 * [kind] e' una delle costanti `Kind*`. Un avviso porta tutto quello che serve per annullare ([memoryId], [previousText]):
 * l'app puo' salvarlo accanto al messaggio e mostrarlo anche dopo un riavvio.
 */
data class AiMemoryNotice(
  val id: String,
  val kind: String,
  val memoryId: String,
  /** Il fatto ricordato, come sta nella memoria. */
  val text: String,
  /** Per [KindUpdated]: il fatto di prima, che questo ha sostituito. Per [KindKept]: quello che non e' entrato. */
  val previousText: String? = null,
  val source: AiMemorySource,
  val atMillis: Long,
  /** Vero dopo Annulla: la card dice «Non lo ricordo più». */
  val undone: Boolean = false,
  /**
   * Vero se il ricordo e' cambiato dopo l'avviso (un aggiornamento, una correzione): l'annulla non vale piu' e la card
   * non lo offre; il ricordo si corregge dalla pagina.
   */
  val stale: Boolean = false,
  /** Per [KindAsk]: l'utente ha detto No (la card dice «Non lo ricordo»). */
  val declined: Boolean = false,
) {
  /** Vero se la card deve offrire Annulla. */
  val canUndo: Boolean get() = !undone && !stale && (kind == KindRemembered || kind == KindUpdated)

  /** Vero se la card deve chiedere «Vuoi che ricordi…?» con Ricorda e No. */
  val asking: Boolean get() = kind == KindAsk && !declined && !stale

  companion object {
    /** Un fatto nuovo: «Ho ricordato: è in 4F». */
    const val KindRemembered = "remembered"

    /** Un fatto che ne sostituisce uno con la stessa chiave: «Ho aggiornato: è in 5F (prima: è in 4F)». */
    const val KindUpdated = "updated"

    /**
     * Un fatto arrivato dopo ma detto prima di quello che c'e' (un'app che propone un dato vecchio): vince il piu'
     * recente, che resta, e l'avviso lo dice («Tengo: è in 5F, è più recente»). Niente da annullare.
     */
    const val KindKept = "kept"

    /**
     * Un fatto proposto che aspetta il si' dell'utente: «Vuoi che ricordi: è in 4F?» con Ricorda e No. E' il modo di
     * serie per i ricordi che l'utente non ha chiesto lui (l'estrattore, il modello, un'app, la ricerca): col
     * salvataggio automatico acceso diventano subito [KindRemembered]. Se l'utente dice si', l'avviso diventa
     * [KindRemembered] (o [KindUpdated]) con Annulla.
     */
    const val KindAsk = "ask"
  }
}
