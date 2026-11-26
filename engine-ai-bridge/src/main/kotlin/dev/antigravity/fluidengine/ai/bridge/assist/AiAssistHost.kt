package dev.antigravity.fluidengine.ai.bridge.assist

import dev.antigravity.fluidengine.ai.bridge.ReadyState
import java.util.ArrayDeque
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject

/**
 * I numeri dell'ospite dell'assistenza. Quelli di serie sono «a occhio» (26/11/2026), coi motivi nel contratto di F9.1
 * (§ 6 e § 8): stanno lontani dal buffer di Binder (1 MB per processo, meta' per le transazioni a senso unico) e dal
 * contesto dei tre fornitori di PampAI.
 *
 * @param maxInputChars il testo di un compito: 24 000 caratteri, ~48 KB in un `Parcel`, ~6 000 gettoni.
 * @param maxOutputChars la risposta: oltre, l'ospite ferma l'esecutore e chiude con quello che ha.
 * @param maxPerApp le domande in corso di un'app; [maxTotal] di tutte.
 * @param startsPerMinute le domande che un'app puo' fare in un minuto: un'app che gira a vuoto non svuota la quota.
 * @param minTimeoutMillis / [maxTimeoutMillis] il tempo di una domanda, come lo tiene l'ospite.
 * @param flushMillis ogni quanto, al massimo, l'ospite manda i pezzi: 50 ms, un invio ogni tre fotogrammi a 60 Hz.
 * @param keepMillis quanto una domanda finita resta leggibile con `assist_poll`.
 */
data class AiAssistLimits(
  val maxInputChars: Int = 24_000,
  val maxOutputChars: Int = 32_000,
  val maxPerApp: Int = 2,
  val maxTotal: Int = 4,
  val startsPerMinute: Int = 30,
  val minTimeoutMillis: Long = 1_000L,
  val maxTimeoutMillis: Long = 120_000L,
  val flushMillis: Long = 50L,
  val keepMillis: Long = 60_000L,
)

/** Dove l'ospite manda gli invii di una domanda (il canale di ritorno dell'app). Falso: l'app non c'e' piu'. */
internal fun interface AiAssistListener {
  fun deliver(batch: String): Boolean

  /** La domanda e' finita e l'ultimo invio e' partito: il canale si puo' lasciare. */
  fun closed() {}
}

/**
 * L'ospite dell'assistenza, **senza Android**: il pezzo che PampAI tiene per tutta la vita del processo (nel suo grafo)
 * e che `AiAssistHostProvider` usa per ogni chiamata. Riceve chiamanti gia' verificati dal sistema ([AiAssistCaller]) e
 * decide, nell'ordine:
 *
 * 1. l'app e' nella tabella ([policy]) e l'utente non l'ha rifiutata o bloccata ([consents]): altrimenti
 *    `not_authorized`. **La prima volta** (F9.1b) la domanda riceve `consent_required` con `needs = [app]`, e
 *    all'esecutore non arriva niente finche' l'utente non dice sì nel foglio di PampAI;
 * 2. il compito e' conosciuto (`needs_update`) e concesso (`not_authorized`), il testo sta nei tetti (`too_large`);
 * 3. la pagina: solo con l'ambito `page` e col sì dell'utente per quell'app, altrimenti `consent_required` (con
 *    `needs = [page]`, o `[app, page]` la prima volta: un foglio solo) e **all'esecutore non arriva niente**;
 * 4. PampAI e' pronta ([ready]: `not_ready`), non ci sono troppe domande insieme (`busy`) ne' troppe al minuto
 *    (`limit_reached`).
 *
 * Poi fa partire l'[executor] e ne raccoglie i pezzi: testo a parole intere e ripulito dalle chiavi
 * ([AiAssistRedaction]), un invio ogni [AiAssistLimits.flushMillis] al massimo. Ferma l'esecutore quando l'app annulla,
 * quando l'app muore, quando scade il tempo, quando l'utente blocca l'app o le toglie il sì per la pagina; in tutti
 * questi casi l'app riceve subito la fine, anche se l'esecutore tarda a fermarsi. Gli errori dell'esecutore escono come
 * codici: un messaggio di eccezione non esce mai.
 *
 * Le impostazioni di PampAI («App che usano PampAI») passano da qui ([setAppConsent], [setPageConsent], [forget]), cosi'
 * una revoca ferma anche le domande in corso.
 *
 * **La chat «Dalle app»** (F9.1b, F9.1c): a ogni domanda finita (anche fermata o fallita, non quelle rifiutate prima di
 * partire) l'ospite consegna a [history] un [AiAssistHistoryEntry], col testo di partenza e la risposta interi e senza le
 * righe della pagina. Le voci restano finche' l'utente non le toglie.
 *
 * @param wallClock l'ora delle voci della cronologia (millisecondi dell'epoca).
 */
class AiAssistHost(
  private val executor: AiAssistExecutor,
  val consents: AiAssistConsentStore,
  val policy: AiAssistPolicy = AiAssistPolicy(),
  val limits: AiAssistLimits = AiAssistLimits(),
  private val ready: () -> ReadyState = { ReadyState(true) },
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
  private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
  private val history: AiAssistHistory? = null,
  private val wallClock: () -> Long = { System.currentTimeMillis() },
) {
  private val lock = Any()
  private val sessions = LinkedHashMap<String, Session>()
  private val starts = HashMap<String, ArrayDeque<Long>>()

  // ── per PampAI (le impostazioni) ──────────────────────────────────────────────────────────────────────────

  /**
   * Il sì per usare PampAI da un'app (F9.1b). Il «no» ([AiAssistConsentState.Denied]) e' anche il blocco: da subito
   * `not_authorized`, e le sue domande in corso si fermano.
   */
  fun setAppConsent(packageName: String, consent: AiAssistConsentState) {
    consents.setAppConsent(packageName, consent)
    if (consent == AiAssistConsentState.Denied) stopAll(packageName, AiAssistError(AiAssistError.NotAuthorized)) { true }
  }

  /** Blocca (il «no» di [setAppConsent]) o sblocca (il sì) un'app. */
  fun setBlocked(packageName: String, blocked: Boolean) =
    setAppConsent(packageName, if (blocked) AiAssistConsentState.Denied else AiAssistConsentState.Allowed)

  /** Il sì per la pagina di un'app. Toglierlo ferma le sue domande in corso che hanno una pagina. */
  fun setPageConsent(packageName: String, consent: AiAssistConsentState) {
    consents.setPageConsent(packageName, consent)
    if (consent != AiAssistConsentState.Allowed) stopAll(packageName, AiAssistError(AiAssistError.ConsentRequired)) { it.hasPage }
  }

  /** Dimentica un'app: tutte e due le scelte tornano «da chiedere»; le sue domande in corso si fermano. */
  fun forget(packageName: String) {
    consents.forget(packageName)
    stopAll(packageName, AiAssistError(AiAssistError.ConsentRequired, needs = setOf(AiAssistError.NeedsApp))) { true }
  }

  /** Le domande in corso di [packageName]. */
  fun inFlight(packageName: String): Int = synchronized(lock) { sessions.values.count { it.caller.packageName == packageName && !it.finished } }

  /** Le domande che l'ospite tiene ancora: in corso, o finite da meno di `keepMillis` (per `assist_poll`). Per le prove. */
  internal fun retained(): Int = synchronized(lock) { sessions.size }

  /** Ferma tutte le domande in corso (PampAI chiude, l'utente spegne l'assistenza). */
  fun cancelAll() {
    val all = synchronized(lock) { sessions.values.filter { !it.finished } }
    all.forEach { stop(it, AiAssistError(AiAssistError.Cancelled)) }
  }

  // ── per il provider ───────────────────────────────────────────────────────────────────────────────────────

  internal fun negotiate(request: JsonObject?): JsonObject = AiAssistProtocol.negotiate(request)

  internal fun scope(caller: AiAssistCaller): JsonObject {
    val entry = policy.entryFor(caller.packageName)
    // Un'app rifiutata o bloccata riceve solo questo: niente compiti, niente stato di PampAI.
    if (entry == null || blocked(caller.packageName)) return AiAssistProtocol.errorResponse(AiAssistError(AiAssistError.NotAuthorized))
    val state = ready()
    val tasks = AiAssistTask.Kinds.filterTo(HashSet()) { kind -> AiAssistPolicy.scopeFor(kind)?.let { it in entry.scopes } == true }
    return AiAssistProtocol.scopeResponse(
      tasks = tasks,
      tools = AiAssistPolicy.ScopeTools in entry.scopes,
      page = AiAssistPolicy.ScopePage in entry.scopes,
      consent = consents.pageConsent(caller.packageName),
      ready = state.ready,
      reason = state.reason?.let { cleanReason(it) },
      app = consents.appConsent(caller.packageName),
    )
  }

  /** Una domanda: o `{requestId}` (e il lavoro parte), o `{error}`. */
  internal fun ask(caller: AiAssistCaller, request: JsonObject?, listener: AiAssistListener?): AskOutcome {
    prune()
    refusal(caller, request)?.let { return AskOutcome(AiAssistProtocol.errorResponse(it), null) }
    val ask = AiAssistProtocol.askOf(request)!!
    val entry = policy.entryFor(caller.packageName)!!
    val writing = ask.task.kind != AiAssistTask.KindAnswer
    val tools = when {
      writing || AiAssistPolicy.ScopeTools !in entry.scopes -> AiAssistRequest.ToolsNone
      else -> ask.tools
    }
    val timeout = ask.timeoutMillis.coerceIn(limits.minTimeoutMillis, limits.maxTimeoutMillis)
    val id = UUID.randomUUID().toString()
    val session = synchronized(lock) {
      // Il conto si rifa' sotto il lucchetto: due domande insieme non passano entrambe dall'ultimo posto.
      admission(caller.packageName)?.let { return AskOutcome(AiAssistProtocol.errorResponse(it), null) }
      starts.getOrPut(caller.packageName) { ArrayDeque() }.addLast(clock())
      Session(id, caller, ask.page != null, listener).also {
        it.task = ask.task
        it.pageTitle = ask.page?.title
        sessions[id] = it
      }
    }
    val call = AiAssistCall(id, caller, ask.task, ask.page, tools, ask.language, timeout)
    session.watchdog = scope.launch {
      delay(timeout)
      stop(session, AiAssistError(AiAssistError.Timeout))
    }
    session.job = scope.launch {
      val sink = SessionSink(session)
      val error: AiAssistError? = try {
        executor.execute(call, sink)
        null
      } catch (e: CancellationException) {
        if (session.overflow) null else AiAssistError(AiAssistError.Cancelled)
      } catch (e: AiAssistException) {
        val code = e.error.code.takeIf { it in AiAssistError.Codes } ?: AiAssistError.Failed
        AiAssistError(code, e.error.retryAfterMillis, e.error.reason?.let { cleanReason(it) })
      } catch (e: Throwable) {
        AiAssistError(AiAssistError.Failed)
      }
      finish(session, error)
    }
    // Chiusa prima che il lavoro avesse il suo Job (il tempo minimo e' scaduto subito): si ferma anche lui.
    if (synchronized(lock) { session.finished }) session.job?.cancel()
    return AskOutcome(AiAssistProtocol.askResponse(id), session)
  }

  /** Gli eventi dopo `after`, per chi non ha il canale o ha perso un invio. */
  internal fun poll(caller: AiAssistCaller, request: JsonObject?): JsonObject {
    prune()
    val id = AiAssistProtocol.requestIdOf(request) ?: return AiAssistProtocol.errorResponse(AiAssistError(AiAssistError.Failed))
    val after = AiAssistProtocol.afterOf(request)
    return synchronized(lock) {
      val s = sessions[id]?.takeIf { it.caller.packageName == caller.packageName }
        ?: return AiAssistProtocol.errorResponse(AiAssistError(AiAssistError.Interrupted))
      AiAssistProtocol.pollResponse(s.events.filter { it.seq > after }, s.finished)
    }
  }

  /** L'app annulla una sua domanda (solo sua: quella di un'altra app non si tocca). */
  internal fun cancel(caller: AiAssistCaller, request: JsonObject?): JsonObject {
    val id = AiAssistProtocol.requestIdOf(request)
    val s = synchronized(lock) { id?.let { sessions[it] }?.takeIf { it.caller.packageName == caller.packageName } }
    return AiAssistProtocol.cancelResponse(s?.let { stop(it, AiAssistError(AiAssistError.Cancelled)) } ?: false)
  }

  /** Il canale di ritorno e' morto: l'app non c'e' piu', il lavoro si ferma. */
  internal fun callerGone(requestId: String) {
    val s = synchronized(lock) { sessions[requestId] } ?: return
    synchronized(lock) { s.listener = null }
    stop(s, AiAssistError(AiAssistError.Cancelled))
  }

  internal class AskOutcome(val response: JsonObject, internal val session: Session?) {
    /** L'id della domanda accettata. */
    val requestId: String? get() = session?.id
  }

  // ── dentro ────────────────────────────────────────────────────────────────────────────────────────────────

  private fun refusal(caller: AiAssistCaller, request: JsonObject?): AiAssistError? {
    val entry = policy.entryFor(caller.packageName) ?: return AiAssistError(AiAssistError.NotAuthorized)
    if (blocked(caller.packageName)) return AiAssistError(AiAssistError.NotAuthorized)
    val ask = AiAssistProtocol.askOf(request) ?: return AiAssistError(AiAssistError.Failed)
    val scope = AiAssistPolicy.scopeFor(ask.task.kind) ?: return AiAssistError(AiAssistError.NeedsUpdate)
    if (scope !in entry.scopes) return AiAssistError(AiAssistError.NotAuthorized)
    val style = ask.task.style
    when (ask.task.kind) {
      AiAssistTask.KindRewrite -> if (style != null && style !in AiAssistTask.RewriteStyles) return AiAssistError(AiAssistError.NeedsUpdate)
      AiAssistTask.KindSummarize -> if (style != null && style !in AiAssistTask.SummaryForms) return AiAssistError(AiAssistError.NeedsUpdate)
    }
    if (ask.tools !in setOf(AiAssistRequest.ToolsCaller, AiAssistRequest.ToolsCallerOnly, AiAssistRequest.ToolsNone)) return AiAssistError(AiAssistError.NeedsUpdate)
    if (ask.task.text.isBlank()) return AiAssistError(AiAssistError.Failed)
    if (ask.task.text.length > limits.maxInputChars) return AiAssistError(AiAssistError.TooLarge)
    if (ask.pageSent) {
      if (AiAssistPolicy.ScopePage !in entry.scopes) return AiAssistError(AiAssistError.NotAuthorized)
      if (ask.page == null) return AiAssistError(AiAssistError.Failed)
    }
    // I sì che mancano, insieme: la prima volta con la pagina e' un foglio solo.
    val needs = LinkedHashSet<String>()
    if (consents.appConsent(caller.packageName) != AiAssistConsentState.Allowed) needs += AiAssistError.NeedsApp
    if (ask.pageSent && consents.pageConsent(caller.packageName) != AiAssistConsentState.Allowed) needs += AiAssistError.NeedsPage
    if (needs.isNotEmpty()) return AiAssistError(AiAssistError.ConsentRequired, needs = needs)
    val state = ready()
    if (!state.ready) return AiAssistError(AiAssistError.NotReady, reason = state.reason?.let { cleanReason(it) })
    return synchronized(lock) { admission(caller.packageName) }
  }

  /** L'utente ha detto no a quest'app (o l'ha bloccata). */
  private fun blocked(packageName: String): Boolean = consents.appConsent(packageName) == AiAssistConsentState.Denied

  /** Quante insieme e quante al minuto. Sotto [lock]. */
  private fun admission(packageName: String): AiAssistError? {
    val running = sessions.values.filter { !it.finished }
    if (running.size >= limits.maxTotal || running.count { it.caller.packageName == packageName } >= limits.maxPerApp) {
      return AiAssistError(AiAssistError.Busy, retryAfterMillis = 2_000L)
    }
    val now = clock()
    val window = starts[packageName]
    if (window != null) {
      while (window.isNotEmpty() && now - window.peekFirst() >= 60_000L) window.pollFirst()
      if (window.size >= limits.startsPerMinute) return AiAssistError(AiAssistError.LimitReached, retryAfterMillis = 60_000L - (now - window.peekFirst()))
    }
    return null
  }

  private fun prune() {
    val now = clock()
    synchronized(lock) {
      sessions.values.removeAll { it.finished && now - it.finishedAt > limits.keepMillis }
    }
  }

  private fun stopAll(packageName: String, error: AiAssistError, which: (Session) -> Boolean) {
    val list = synchronized(lock) { sessions.values.filter { it.caller.packageName == packageName && !it.finished && which(it) } }
    list.forEach { stop(it, error) }
  }

  /** Chiude [s] con [error] subito, poi ferma l'esecutore. Vero se era ancora aperta. */
  private fun stop(s: Session, error: AiAssistError): Boolean {
    val closed = finish(s, error)
    s.job?.cancel()
    return closed
  }

  /** La fine: il testo trattenuto esce, poi `done` (col testo intero mandato) o `failed`. Una volta sola. */
  private fun finish(s: Session, error: AiAssistError?): Boolean {
    val entry: AiAssistHistoryEntry?
    synchronized(lock) {
      if (s.finished) return false
      val rest = s.stream.drain()
      if (rest.isNotEmpty()) appendText(s, rest)
      s.events += if (error == null) {
        AiAssistWireEvent(++s.seq, AiAssistProtocol.TYPE_DONE, text = s.sent.toString())
      } else {
        AiAssistWireEvent(++s.seq, AiAssistProtocol.TYPE_FAILED, code = error.code, retryAfterMillis = error.retryAfterMillis, reason = error.reason, needs = error.needs)
      }
      s.finished = true
      s.finishedAt = clock()
      entry = if (history != null) historyEntry(s, error) else null
    }
    s.watchdog?.cancel()
    flush(s, urgent = true)
    entry?.let { e -> runCatching { history?.record(e) } }
    return true
  }

  /**
   * Sotto [lock]: la voce della chat «Dalle app» (F9.1c). La domanda, o il testo da riscrivere o riassumere, **intero**;
   * della pagina **solo il titolo**; la risposta intera, com'e' arrivata all'app; le ricevute.
   */
  private fun historyEntry(s: Session, error: AiAssistError?): AiAssistHistoryEntry {
    val task = s.task ?: AiAssistTask(AiAssistTask.KindAnswer, "")
    val text = task.text.trim()
    val question = text
    val receipts = s.events.filter { it.type == AiAssistProtocol.TYPE_RECEIPT && it.line != null }
      .map { AiAssistReceipt(it.line!!, it.app, it.outcome ?: AiAssistReceipt.OutcomeDone) }
    return AiAssistHistoryEntry(
      requestId = s.id,
      app = s.caller.packageName,
      appName = s.caller.label,
      atMillis = wallClock(),
      kind = task.kind,
      style = task.style,
      question = AiAssistRedaction.scrub(question),
      inputChars = text.length,
      pageTitle = s.pageTitle,
      answer = s.sent.toString(),
      receipts = receipts,
      outcome = error?.code ?: AiAssistHistoryEntry.OutcomeDone,
    )
  }

  /** Sotto [lock]: un pezzo di testo gia' ripulito, unito all'ultimo evento di testo se non e' ancora partito. */
  private fun appendText(s: Session, text: String) {
    s.sent.append(text)
    val last = s.events.lastOrNull()
    if (last != null && last.type == AiAssistProtocol.TYPE_TEXT && last.seq > s.delivered) {
      s.events[s.events.size - 1] = last.copy(delta = (last.delta ?: "") + text)
    } else {
      s.events += AiAssistWireEvent(++s.seq, AiAssistProtocol.TYPE_TEXT, delta = text)
    }
  }

  /** Un invio alla volta per domanda, al massimo ogni [AiAssistLimits.flushMillis]; la fine parte subito. */
  private fun flush(s: Session, urgent: Boolean) {
    synchronized(lock) {
      if (urgent) { s.urgent = true; s.wake.trySend(Unit) }
      if (s.flushing) return
      s.flushing = true
    }
    scope.launch {
      while (true) {
        val wait = synchronized(lock) { if (s.urgent) 0L else (s.lastFlush + limits.flushMillis - clock()).coerceAtLeast(0L) }
        if (wait > 0) {
          // Si aspetta il proprio turno, ma la fine sveglia subito.
          withTimeoutOrNull(wait) { s.wake.receive() }
          continue
        }
        val batch: List<AiAssistWireEvent>
        val listener: AiAssistListener?
        synchronized(lock) {
          batch = s.events.filter { it.seq > s.delivered }
          if (batch.isNotEmpty()) s.delivered = batch.last().seq
          s.lastFlush = clock()
          s.urgent = false
          listener = s.listener
        }
        if (batch.isNotEmpty() && listener != null) {
          val delivered = runCatching { listener.deliver(AiAssistProtocol.batch(s.id, batch)) }.getOrDefault(false)
          if (!delivered) {
            synchronized(lock) { s.listener = null }
            stop(s, AiAssistError(AiAssistError.Cancelled))
          }
        }
        var closing: AiAssistListener? = null
        val idle = synchronized(lock) {
          if ((s.events.lastOrNull()?.seq ?: 0L) > s.delivered) return@synchronized false
          s.flushing = false
          // Finita e tutta mandata: il canale e il gettone si lasciano.
          if (s.finished) { closing = s.listener; s.listener = null; s.token = null }
          true
        }
        if (idle) { closing?.let { runCatching { it.closed() } }; return@launch }
      }
    }
  }

  private fun cleanReason(reason: String): String = AiAssistRedaction.scrub(reason.replace('\n', ' ').trim()).take(AiAssistProtocol.MaxReasonChars)

  /** Quello che l'esecutore scrive, filtrato: niente dopo la fine, testo a parole intere, il tetto della risposta. */
  private inner class SessionSink(private val s: Session) : AiAssistSink {
    override fun status(line: String, app: String?) {
      val clean = AiAssistRedaction.scrub(line.replace('\n', ' ').trim()).take(MaxLineChars)
      if (clean.isEmpty()) return
      synchronized(lock) {
        if (s.finished) return
        s.events += AiAssistWireEvent(++s.seq, AiAssistProtocol.TYPE_STATUS, line = clean, app = app?.takeIf { PackageName.matches(it) })
      }
      flush(s, urgent = false)
    }

    override fun text(delta: String) {
      if (delta.isEmpty()) return
      var overflow = false
      synchronized(lock) {
        if (s.finished) return
        val room = limits.maxOutputChars - s.sent.length - s.stream.pending
        val piece = if (delta.length > room) { overflow = true; delta.take(room.coerceAtLeast(0)) } else delta
        val ready = s.stream.push(piece)
        if (ready.isNotEmpty()) appendText(s, ready)
        if (overflow) s.overflow = true
      }
      if (overflow) {
        finish(s, null)
        s.job?.cancel()
      } else {
        flush(s, urgent = false)
      }
    }

    override fun receipt(receipt: AiAssistReceipt) {
      val clean = AiAssistRedaction.scrub(receipt.line.replace('\n', ' ').trim()).take(MaxLineChars)
      if (clean.isEmpty()) return
      val outcome = receipt.outcome.takeIf { it in Outcomes } ?: AiAssistReceipt.OutcomeUncertain
      synchronized(lock) {
        if (s.finished) return
        s.events += AiAssistWireEvent(++s.seq, AiAssistProtocol.TYPE_RECEIPT, line = clean, app = receipt.app?.takeIf { PackageName.matches(it) }, outcome = outcome)
      }
      flush(s, urgent = false)
    }
  }

  internal class Session(val id: String, val caller: AiAssistCaller, val hasPage: Boolean, @Volatile var listener: AiAssistListener?) {
    val events = ArrayList<AiAssistWireEvent>()
    val stream = AiAssistRedaction.Stream()
    val sent = StringBuilder()
    var seq = 0L
    var delivered = 0L
    var finished = false
    var finishedAt = 0L
    var overflow = false
    var flushing = false
    var urgent = false
    var lastFlush = Long.MIN_VALUE / 2
    val wake = Channel<Unit>(Channel.CONFLATED)
    @Volatile var job: Job? = null
    @Volatile var watchdog: Job? = null

    /** Per la cronologia: il compito e il titolo della pagina (solo il titolo). */
    var task: AiAssistTask? = null
    var pageTitle: String? = null

    /** Il gettone che il provider da' all'app (un `Binder`): vive quanto la domanda. */
    @Volatile var token: Any? = null
  }

  private companion object {
    const val MaxLineChars = 160
    val PackageName = Regex("[A-Za-z0-9_.]{1,128}")
    val Outcomes = setOf(AiAssistReceipt.OutcomeDone, AiAssistReceipt.OutcomeUncertain, AiAssistReceipt.OutcomeFailed)
  }
}
