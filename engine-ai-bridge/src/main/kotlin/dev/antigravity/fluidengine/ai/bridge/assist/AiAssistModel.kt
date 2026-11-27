package dev.antigravity.fluidengine.ai.bridge.assist

// F9.1 · Il contratto inverso: un'app chiede a PampAI e riceve la risposta. Qui i tipi che attraversano il confine,
// visti dall'app (la richiesta, la pagina, il risultato) e dall'ospite (la chiamata verificata e l'esecutore).
// Il contratto per intero: `skill/fluid-engine/references/moduli/ai-bridge.md`, sezione «L'assistenza».

/**
 * Cosa l'app chiede a PampAI: il tipo ([kind]), il testo (la domanda, o il testo da riscrivere o riassumere) e, per
 * riscrivere e riassumere, lo stile. I valori sono testi e non un enum: un compito nuovo si aggiunge senza rompere chi
 * c'era, e un ospite che non lo conosce risponde `needs_update`.
 *
 * Gli stili sono quelli di Writing Tools di Apple Intelligence (iOS 18.1+, presenti in iOS 26: Correggi, Riscrivi,
 * Cordiale, Professionale, Breve; Riassunto, Punti chiave, Elenco, Tabella). Fiducia alta sull'elenco.
 */
data class AiAssistTask(val kind: String, val text: String, val style: String? = null) {
  companion object {
    /** Una domanda libera. Ambito [AiAssistPolicy.ScopeAnswer]. */
    const val KindAnswer = "answer"

    /** Riscrivere un testo dell'utente con uno stile `Style*`. Ambito [AiAssistPolicy.ScopeRewrite]. */
    const val KindRewrite = "rewrite"

    /** Riassumere un testo in una forma `Form*`. Ambito [AiAssistPolicy.ScopeSummarize]. */
    const val KindSummarize = "summarize"

    /** Correggi: ortografia e grammatica, senza cambiare il tono. */
    const val StyleProofread = "proofread"

    /** Riscrivi: lo stesso significato, parole nuove. */
    const val StyleRewrite = "rewrite"

    /** Cordiale. */
    const val StyleFriendly = "friendly"

    /** Professionale. */
    const val StyleProfessional = "professional"

    /** Breve. */
    const val StyleConcise = "concise"

    /** Un riassunto in un paragrafo. */
    const val FormSummary = "summary"

    /** I punti chiave, uno per riga. */
    const val FormKeyPoints = "key_points"

    /** Un elenco. */
    const val FormList = "list"

    /** Una tabella. */
    const val FormTable = "table"

    /** I compiti che questa versione conosce. */
    val Kinds: Set<String> = setOf(KindAnswer, KindRewrite, KindSummarize)

    /** Gli stili di [KindRewrite], nell'ordine di Writing Tools. */
    val RewriteStyles: List<String> = listOf(StyleProofread, StyleRewrite, StyleFriendly, StyleProfessional, StyleConcise)

    /** Le forme di [KindSummarize], nell'ordine di Writing Tools. */
    val SummaryForms: List<String> = listOf(FormSummary, FormKeyPoints, FormList, FormTable)

    fun answer(question: String): AiAssistTask = AiAssistTask(KindAnswer, question)

    fun rewrite(text: String, style: String = StyleRewrite): AiAssistTask = AiAssistTask(KindRewrite, text, style)

    fun summarize(text: String, form: String = FormSummary): AiAssistTask = AiAssistTask(KindSummarize, text, form)
  }
}

/**
 * La pagina che l'utente ha davanti, scritta dall'app (`assistente-vede-la-pagina`): un titolo, un tipo facoltativo,
 * le righe visibili e la selezione. **Nient'altro**: il formato non ha posto per identificativi, indirizzi o dati
 * nascosti. Corta per costruzione: ogni campo ha un tetto e il totale non supera [MaxTotalChars] caratteri (circa 600
 * gettoni); ciò che sfora si taglia, prima la coda delle righe e poi la selezione, e [truncated] lo dice. I caratteri di
 * controllo si tolgono, gli spazi si ricompattano (nella selezione restano gli a capo).
 *
 * Parte solo dentro una domanda (`AiAssistRequest.page`), solo col sì dell'utente per quell'app, e il chip «Questa
 * pagina» di `engine-ai-ui` ne mostra [preview], lo stesso testo che passa.
 *
 * I tetti sono «a occhio» (26/11/2026): un titolo di una riga, le righe di una schermata di telefono, una selezione di
 * qualche paragrafo.
 */
class AiAssistPage(
  title: String,
  lines: List<String> = emptyList(),
  selection: String? = null,
  kind: String? = null,
) {
  val title: String
  val lines: List<String>
  val selection: String?
  val kind: String?

  /** Vero se qualcosa e' stato tagliato per stare nei tetti. */
  val truncated: Boolean

  init {
    var cut = false
    fun clip(value: String, max: Int): String = if (value.length <= max) value else { cut = true; value.take(max - 1).trimEnd() + "…" }
    this.title = clip(oneLine(title), MaxTitleChars)
    this.kind = kind?.let { clip(oneLine(it), MaxKindChars) }?.takeIf { it.isNotEmpty() }
    val cleanLines = lines.map { oneLine(it) }.filter { it.isNotEmpty() }
    if (cleanLines.size > MaxLines) cut = true
    val kept = ArrayList<String>()
    var total = this.title.length + (this.kind?.length ?: 0)
    for (line in cleanLines.take(MaxLines)) {
      val clipped = clip(line, MaxLineChars)
      if (total + clipped.length > MaxTotalChars) { cut = true; break }
      kept += clipped
      total += clipped.length
    }
    this.lines = java.util.Collections.unmodifiableList(kept)
    val room = (MaxTotalChars - total).coerceAtLeast(0)
    this.selection = selection?.let { multiLine(it) }?.takeIf { it.isNotEmpty() }?.let { sel ->
      val max = minOf(MaxSelectionChars, room)
      when {
        max <= 1 -> { cut = true; null }
        else -> clip(sel, max)
      }
    }
    this.truncated = cut
  }

  /** Quanti caratteri passano in tutto. */
  val length: Int get() = title.length + (kind?.length ?: 0) + lines.sumOf { it.length } + (selection?.length ?: 0)

  /**
   * Il testo che passa a PampAI, come lo legge una persona: il titolo, le righe e la selezione. Lo mostra il chip
   * «Questa pagina» quando si chiede cosa passa.
   */
  fun preview(): String = buildString {
    append(title)
    lines.forEach { append('\n').append("• ").append(it) }
    selection?.let { append("\n\n«").append(it).append('»') }
  }

  override fun equals(other: Any?): Boolean =
    other is AiAssistPage && other.title == title && other.lines == lines && other.selection == selection && other.kind == kind

  override fun hashCode(): Int = ((title.hashCode() * 31 + lines.hashCode()) * 31 + (selection?.hashCode() ?: 0)) * 31 + (kind?.hashCode() ?: 0)

  override fun toString(): String = "AiAssistPage(title=$title, lines=${lines.size}, selection=${selection?.length ?: 0}, truncated=$truncated)"

  companion object {
    const val MaxTitleChars = 120
    const val MaxKindChars = 40
    const val MaxLines = 8
    const val MaxLineChars = 160
    const val MaxSelectionChars = 2_000
    const val MaxTotalChars = 2_400

    private val Control = Regex("[\\p{Cc}\\p{Cf}&&[^\\n]]")
    private val Spaces = Regex("[ \\t\\u00A0]+")

    private fun oneLine(value: String): String =
      value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').replace(Control, "").replace(Spaces, " ").trim()

    private fun multiLine(value: String): String =
      value.replace("\r\n", "\n").replace('\r', '\n').replace('\t', ' ').replace(Control, "").replace(Spaces, " ")
        .replace(Regex("\\n{3,}"), "\n\n").trim()
  }
}

/**
 * Una domanda a PampAI.
 *
 * @param page la pagina, solo se l'utente ha lasciato acceso il chip «Questa pagina» (e PampAI ha il sì per l'app).
 * @param tools quali tool usa PampAI: [ToolsCaller] (di serie: i tool dell'app che chiama sono la base, le altre app
 *   restano raggiungibili ma non caricate, nota di Alessio su `connettori-mcp`), [ToolsCallerOnly], [ToolsNone].
 * @param timeoutMillis il tempo massimo; PampAI lo tiene fra 1 e 120 secondi.
 */
data class AiAssistRequest(
  val task: AiAssistTask,
  val page: AiAssistPage? = null,
  val tools: String = ToolsCaller,
  val language: String = "it",
  val timeoutMillis: Long = DefaultTimeoutMillis,
) {
  companion object {
    const val ToolsCaller = "caller"
    const val ToolsCallerOnly = "caller_only"
    const val ToolsNone = "none"

    /** 60 s: a occhio (26/11/2026), una risposta con due giri di tool. Il tetto dell'ospite e' 120 s. */
    const val DefaultTimeoutMillis = 60_000L
  }
}

/** La riga semplice di cosa fa PampAI («Controllo i voti») e l'app di cui usa i tool, per l'icona (F9.4). */
data class AiAssistStatusLine(val line: String, val app: String? = null)

/** La ricevuta di un'azione fatta durante la domanda: una riga, l'app, l'esito. */
data class AiAssistReceipt(val line: String, val app: String? = null, val outcome: String = OutcomeDone) {
  companion object {
    const val OutcomeDone = "done"

    /** L'effetto non e' certo (una risposta persa): da verificare nell'app, mai da ripetere da soli. */
    const val OutcomeUncertain = "uncertain"
    const val OutcomeFailed = "failed"
  }
}

/**
 * Un errore dell'assistenza: un **codice fisso**, mai il testo di un'eccezione o di un fornitore (che potrebbe portare
 * una chiave). Le parole le mette l'engine ([message]).
 *
 * @param retryAfterMillis per [LimitReached] e [Busy], quando si sa.
 * @param reason per [NotReady], la riga di PampAI («aggiungi una chiave»), corta e ripulita.
 * @param needs per [ConsentRequired] (F9.1b): quali sì mancano, [NeedsApp] (la prima volta che l'app usa PampAI) e/o
 *   [NeedsPage] (la pagina). L'app apre la domanda di PampAI con `AiAssistClient.consentIntent(page = NeedsPage in needs)`:
 *   un foglio solo, anche quando mancano tutti e due.
 */
data class AiAssistError(
  val code: String,
  val retryAfterMillis: Long? = null,
  val reason: String? = null,
  val needs: Set<String> = emptySet(),
) {
  /** Le parole per l'utente, in italiano o in inglese. */
  fun message(language: String = "it"): String = AiAssistTexts.message(this, language)

  /**
   * Se ha senso offrire «Riprova» con la stessa domanda (F9.1d). Sì quando la stessa domanda puo' andare dopo: [Timeout],
   * [Interrupted], [Failed] (e ogni codice sconosciuto), [Busy] e [LimitReached] (dopo [retryAfterMillis]),
   * [ConsentRequired] (riprovare riapre il foglio del sì). No quando non cambierebbe niente: [NotAuthorized] (PampAI o
   * l'utente hanno detto di no, e il tasto si spegne: [AiAssistEntry]), [NotInstalled], [NeedsUpdate] e [NotReady] (serve
   * prima l'azione del tasto spento), [TooLarge] (la stessa domanda sarebbe ancora troppo lunga) e [Cancelled] (l'ha
   * fermata l'utente).
   */
  val retryable: Boolean
    get() = when (code) {
      NotAuthorized, NotInstalled, NeedsUpdate, NotReady, TooLarge, Cancelled -> false
      else -> true
    }

  companion object {
    const val NotInstalled = "not_installed"
    const val NeedsUpdate = "needs_update"
    const val NotAuthorized = "not_authorized"
    const val ConsentRequired = "consent_required"
    const val NotReady = "not_ready"
    const val LimitReached = "limit_reached"
    const val Busy = "busy"
    const val TooLarge = "too_large"
    const val Cancelled = "cancelled"
    const val Timeout = "timeout"
    const val Interrupted = "interrupted"
    const val Failed = "failed"

    /** I codici che questa versione conosce; un codice sconosciuto si legge come [Failed]. */
    val Codes: Set<String> = setOf(NotInstalled, NeedsUpdate, NotAuthorized, ConsentRequired, NotReady, LimitReached, Busy, TooLarge, Cancelled, Timeout, Interrupted, Failed)

    /** Manca il sì per usare PampAI da quest'app (la prima volta, F9.1b). */
    const val NeedsApp = "app"

    /** Manca il sì per la pagina. */
    const val NeedsPage = "page"
  }
}

/**
 * Lo stato di una domanda, **intero** e immutabile: il testo arrivato finora, la riga di stato, le ricevute, se e'
 * finita e come. `AiAssistClient.ask` ne emette uno a ogni novita'; in Compose basta mostrare l'ultimo. L'ultimo
 * elemento del flusso ha [done] vero o un [error].
 */
data class AiAssistProgress(
  val text: String = "",
  val status: AiAssistStatusLine? = null,
  val receipts: List<AiAssistReceipt> = emptyList(),
  val done: Boolean = false,
  val error: AiAssistError? = null,
) {
  val finished: Boolean get() = done || error != null

  /**
   * Se offrire «Riprova» per questa domanda: come [AiAssistError.retryable], ma **non dopo una scrittura** (R3-S1). Una
   * ricevuta fatta o da verificare vuol dire che un tool ha gia' scritto qualcosa (un evento, un promemoria): rifare la
   * stessa domanda lo rifarebbe. Allora decide l'utente, guardando le ricevute.
   */
  val retryable: Boolean
    get() = error?.retryable == true && receipts.none { it.outcome != AiAssistReceipt.OutcomeFailed }
}

/**
 * Una scelta dell'utente nel registro di PampAI: il sì per usare PampAI da un'app (la prima volta, F9.1b) e il sì per la
 * pagina. «No» si ricorda: PampAI non chiede piu', e si cambia da «App che usano PampAI».
 */
enum class AiAssistConsentState { NotAsked, Allowed, Denied }

/**
 * Cosa l'app puo' fare con PampAI adesso (`AiAssistClient.check`): se e' pronta, quali compiti puo' chiedere, se puo'
 * usare i suoi tool e mandare la pagina, il sì per la pagina e (F9.1b) il sì per usare PampAI. Con [error] il tasto
 * «Chiedi a PampAI» si vede spento ([AiAssistEntry.of]). Un'app che l'utente ha rifiutato o bloccato riceve solo
 * `not_authorized`, senza nient'altro.
 */
data class AiAssistStatus(
  val ready: Boolean,
  val tasks: Set<String> = emptySet(),
  val tools: Boolean = false,
  val page: Boolean = false,
  val pageConsent: AiAssistConsentState = AiAssistConsentState.NotAsked,
  val error: AiAssistError? = null,
  val hostPackage: String? = null,
  /** Il sì per usare PampAI da quest'app: [AiAssistConsentState.NotAsked] vuol dire che la prima domanda aprira' il foglio. */
  val appConsent: AiAssistConsentState = AiAssistConsentState.NotAsked,
)

/**
 * Come si mostra il tasto «Chiedi a PampAI» (F9.1b, scelta di Alessio del 26/11/2026: **spento, non nascosto**, quando
 * PampAI non c'e'). [enabled] dice se si puo' toccare; [note] e' la riga sotto («PampAI non c'è»); [action] il tasto
 * accanto: [ActionInstall] («Installa PampAI», `AiAssistClient.installIntent()`), [ActionUpdate] («Aggiorna PampAI»,
 * lo stesso Store), [ActionOpen] («Apri PampAI», per aggiungere una chiave), o null.
 *
 * Un'app **non autorizzata** (fuori tabella, rifiutata o bloccata dall'utente) ha il tasto spento con «PampAI non ha
 * autorizzato quest'app» e **nessuna azione**: non le si propone di installare una PampAI che c'e', ne' le si dice
 * altro di lei.
 */
data class AiAssistEntry(val enabled: Boolean, val note: String? = null, val action: String? = null, val actionLabel: String? = null) {
  companion object {
    const val ActionInstall = "install"
    const val ActionUpdate = "update"
    const val ActionOpen = "open"

    /** Il tasto per [status] nella lingua [language]; null (non ancora controllato) e' spento, senza parole. */
    fun of(status: AiAssistStatus?, language: String = "it"): AiAssistEntry {
      status ?: return AiAssistEntry(enabled = false)
      val error = status.error ?: return if (status.ready) AiAssistEntry(enabled = true) else AiAssistEntry(false, AiAssistTexts.message(AiAssistError(AiAssistError.NotReady), language))
      val note = error.message(language)
      return when (error.code) {
        AiAssistError.NotInstalled -> AiAssistEntry(false, note, ActionInstall, AiAssistTexts.action(ActionInstall, language))
        AiAssistError.NeedsUpdate -> AiAssistEntry(false, note, ActionUpdate, AiAssistTexts.action(ActionUpdate, language))
        AiAssistError.NotReady -> AiAssistEntry(false, note, ActionOpen, AiAssistTexts.action(ActionOpen, language))
        else -> AiAssistEntry(false, note)
      }
    }
  }
}

/**
 * Chi chiama, come lo vede l'ospite: **verificato dal sistema** (uid di Binder, firma, un pacchetto solo), mai scritto
 * dall'app. [toolsAuthority] e' l'ospite dei tool dell'app chiamante, trovato da PampAI nel suo pacchetto (il provider
 * col meta-data `TOOL_HOST`), null se l'app non ne ha.
 */
data class AiAssistCaller(val packageName: String, val label: String, val toolsAuthority: String? = null)

/**
 * Una domanda accettata, come arriva all'esecutore di PampAI: chi chiama, il compito, la pagina **solo se** l'utente ha
 * detto sì per quell'app e l'app l'ha mandata, quali tool, la lingua, la scadenza (in millisecondi dell'orologio
 * dell'ospite).
 */
class AiAssistCall(
  val requestId: String,
  val caller: AiAssistCaller,
  val task: AiAssistTask,
  val page: AiAssistPage?,
  val tools: String,
  val language: String,
  val timeoutMillis: Long,
)

/**
 * Dove l'esecutore scrive cosa sta facendo. Non sospende: l'ospite raccoglie e manda all'app ogni 50 ms, a parole
 * intere e ripulito ([AiAssistRedaction]). Si puo' chiamare da qualunque filo.
 */
interface AiAssistSink {
  /** La riga semplice («Controllo i voti»), con l'app di cui si usano i tool. */
  fun status(line: String, app: String? = null)

  /** Un pezzo di testo della risposta, da aggiungere a quello di prima. */
  fun text(delta: String)

  /** La ricevuta di un'azione. */
  fun receipt(receipt: AiAssistReceipt)
}

/**
 * L'esecutore che PampAI fornisce: fa la domanda al modello scelto dall'utente, con le chiavi che stanno solo li', e
 * scrive nel [AiAssistSink]. Finisce normalmente (risposta completa), lancia [AiAssistException] per un errore con il
 * suo codice, o viene cancellato (l'app ha annullato, e' morta, o e' scaduto il tempo): deve smettere di lavorare e di
 * spendere. Qualunque altra eccezione diventa [AiAssistError.Failed], **senza il suo messaggio**.
 */
fun interface AiAssistExecutor {
  suspend fun execute(call: AiAssistCall, sink: AiAssistSink)
}

/** Un errore con il suo codice, dall'esecutore (un 429 diventa [AiAssistError.LimitReached]). */
class AiAssistException(val error: AiAssistError) : Exception(error.code)

/**
 * Una domanda di un'app come la ricorda PampAI nella chat «Dalle app» (F9.1b e F9.1c, scelte di Alessio del 26/11/2026).
 * Si salva **tutto quello che serve a rileggere**: l'app, quando, il compito con lo stile, la domanda **intera** (per
 * riscrivere e riassumere, **il testo di partenza intero**), il titolo della pagina, la risposta **intera**, le
 * ricevute e l'esito. **Non si salvano** le righe e la selezione della pagina, le righe di stato, i dati dei tool e
 * niente del fornitore o del modello (i consumi stanno a parte). Tutto il testo passa dalla pulizia delle chiavi.
 *
 * Le voci **restano finche' l'utente non le toglie**, una per una o tutte, come le altre chat di PampAI: l'engine non ne
 * cancella nessuna da solo.
 *
 * @param question la domanda, o il testo da riscrivere o riassumere, com'era (al massimo il tetto dei compiti,
 *   `AiAssistLimits.maxInputChars`).
 * @param inputChars i caratteri del testo di partenza.
 * @param answer la risposta come l'ha ricevuta l'app (al massimo `AiAssistLimits.maxOutputChars`).
 * @param outcome `done`, o il codice dell'errore (anche `cancelled`).
 */
data class AiAssistHistoryEntry(
  val requestId: String,
  val app: String,
  val appName: String,
  val atMillis: Long,
  val kind: String,
  val style: String?,
  val question: String,
  val inputChars: Int,
  val pageTitle: String?,
  val answer: String,
  val receipts: List<AiAssistReceipt>,
  val outcome: String,
) {
  companion object {
    const val OutcomeDone = "done"
  }
}

/**
 * Dove l'ospite consegna le domande finite per la chat «Dalle app». PampAI la scrive nel suo database; l'ospite la
 * chiama una volta per domanda, a domanda finita (anche fermata o fallita), mai per una domanda rifiutata prima di
 * partire. Non deve bloccare: si chiama dal filo che chiude la domanda.
 */
fun interface AiAssistHistory {
  fun record(entry: AiAssistHistoryEntry)
}

/**
 * La cronologia in memoria, per le prove e per un PampAI finto: dura quanto il processo. Come quella vera non toglie
 * niente da sola; [capacity] e' solo un tetto di memoria per chi lo vuole (di serie nessuno).
 */
class InMemoryAiAssistHistory(private val capacity: Int = Int.MAX_VALUE) : AiAssistHistory {
  private val list = ArrayList<AiAssistHistoryEntry>()

  override fun record(entry: AiAssistHistoryEntry) {
    synchronized(list) {
      list.add(0, entry)
      while (list.size > capacity) list.removeAt(list.size - 1)
    }
  }

  /** Toglie una domanda (la scelta dell'utente, una per una). Vero se c'era. */
  fun remove(requestId: String): Boolean = synchronized(list) { list.removeAll { it.requestId == requestId } }

  /** Le domande, la piu' nuova per prima (una copia). */
  fun entries(): List<AiAssistHistoryEntry> = synchronized(list) { java.util.Collections.unmodifiableList(ArrayList(list)) }

  /** Toglie le domande di un'app, o tutte. */
  fun forget(app: String? = null) {
    synchronized(list) { if (app == null) list.clear() else list.removeAll { it.app == app } }
  }
}
