package dev.antigravity.fluidengine.foundation.live

import kotlinx.coroutines.flow.StateFlow

// F8.19 · `attivita-fuori-dall-app`: il modello puro di un'attività viva, la stessa dentro e fuori dall'app. Dentro è la
// pillola di attività (`FluidActivity` di engine-ui, F6B.10); fuori è una notifica viva, che su Android 16 diventa un Live
// Update promosso (`engine-notify`). Sta qui, nel pavimento comune, perché i due moduli non si conoscono: engine-ui
// traduce la pillola in [FluidLiveUpdate] e la passa a un [FluidLiveUpdateSink]; engine-notify è quel sink.
//
// Il riferimento Apple è ActivityKit (Live Activity e Dynamic Island): un'attività con un inizio e una fine, aggiornata
// solo quando c'è qualcosa di nuovo, che resta visibile per un po' dopo la fine («Displaying live data with Live
// Activities», fiducia alta).

/**
 * Il genere di un'attività viva: decide l'icona di serie, la categoria della notifica e se può diventare un Live Update.
 *
 * Non è un `enum` (regola 3.11 del piano): i generi nuovi si aggiungono senza rompere i `when` delle app. Due generi con lo
 * stesso [name] sono lo stesso. I nomi dei generi di serie sono quelli di `FluidActivityKind` della pillola.
 *
 * @property name il nome, l'identità.
 * @property promotable vero se un'attività di questo genere può chiedere la promozione a Live Update. Le linee guida di
 *   Android (developer.android.com, «Live Updates», fiducia alta) la vogliono solo per attività **avviate dall'utente, con
 *   un inizio e una fine, che si seguono adesso**: una sincronizzazione di sottofondo no.
 */
class FluidLiveKind private constructor(val name: String, val promotable: Boolean) {
  override fun equals(other: Any?): Boolean = other is FluidLiveKind && other.name == name
  override fun hashCode(): Int = name.hashCode()
  override fun toString(): String = "FluidLiveKind($name)"

  companion object {
    /** Una sincronizzazione di sottofondo: una notifica con l'avanzamento, mai un Live Update. */
    val Sync: FluidLiveKind = FluidLiveKind("sync", promotable = false)

    /** Un trasferimento chiesto dall'utente (uno scaricamento, un caricamento, una conversione). */
    val Transfer: FluidLiveKind = FluidLiveKind("transfer", promotable = true)

    /** Un timer avviato dall'utente. */
    val Timer: FluidLiveKind = FluidLiveKind("timer", promotable = true)

    /** L'assistente che lavora a una risposta lunga chiesta dall'utente. */
    val Assistant: FluidLiveKind = FluidLiveKind("assistant", promotable = true)

    /** Una registrazione in corso. */
    val Recording: FluidLiveKind = FluidLiveKind("recording", promotable = true)

    /** Un viaggio in corso (Fluid Transit): il caso tipico dei Live Update, con le tappe e le fermate. */
    val Navigation: FluidLiveKind = FluidLiveKind("navigation", promotable = true)

    /** Un genere su misura. */
    fun custom(name: String, promotable: Boolean = true): FluidLiveKind = FluidLiveKind(name, promotable)

    /** Il genere di serie col nome [name] (quello della pillola), o uno su misura promuovibile. */
    fun named(name: String): FluidLiveKind =
      listOf(Sync, Transfer, Timer, Assistant, Recording, Navigation).firstOrNull { it.name == name } ?: custom(name)
  }
}

/**
 * Il tempo di un'attività viva, sull'orologio che non salta col cambio d'ora (`SystemClock.elapsedRealtime()`), come
 * `FluidActivityTime` della pillola. Fuori dall'app diventa il cronometro della notifica, che il sistema fa scorrere da solo:
 * nessun aggiornamento al secondo.
 */
class FluidLiveTime private constructor(
  /** L'istante di riferimento in millisecondi di `elapsedRealtime`, o i millisecondi fermi per [fixed]. */
  val anchorMillis: Long,
  private val mode: Int,
) {
  /** Vero se il tempo scende verso [anchorMillis] (un timer). */
  val countsDown: Boolean get() = mode == Until

  /** Vero se il tempo scorre da solo (non è fermo). */
  val running: Boolean get() = mode != Fixed

  /** I millisecondi da mostrare a [nowElapsedMillis], mai meno di zero. */
  fun shownMillis(nowElapsedMillis: Long): Long = when (mode) {
    Since -> nowElapsedMillis - anchorMillis
    Until -> anchorMillis - nowElapsedMillis
    else -> anchorMillis
  }.coerceAtLeast(0L)

  /**
   * L'istante sull'orologio di parete che corrisponde all'ancora, dati gli orologi di adesso: è il `when` del cronometro
   * della notifica. Per un tempo fermo, null.
   */
  fun wallAnchorMillis(nowElapsedMillis: Long, nowWallMillis: Long): Long? =
    if (!running) null else nowWallMillis + (anchorMillis - nowElapsedMillis)

  override fun equals(other: Any?): Boolean = other is FluidLiveTime && other.anchorMillis == anchorMillis && other.mode == mode
  override fun hashCode(): Int = 31 * anchorMillis.hashCode() + mode
  override fun toString(): String = "FluidLiveTime(${listOf("since", "until", "fixed")[mode]} $anchorMillis)"

  companion object {
    private const val Since = 0
    private const val Until = 1
    private const val Fixed = 2

    /** Il tempo trascorso da [startedAtElapsedMillis]: una registrazione. */
    fun since(startedAtElapsedMillis: Long): FluidLiveTime = FluidLiveTime(startedAtElapsedMillis, Since)

    /** Il tempo che resta fino a [endsAtElapsedMillis]: un timer, un arrivo. */
    fun until(endsAtElapsedMillis: Long): FluidLiveTime = FluidLiveTime(endsAtElapsedMillis, Until)

    /** Un tempo fermo di [millis]: in pausa. */
    fun fixed(millis: Long): FluidLiveTime = FluidLiveTime(millis.coerceAtLeast(0L), Fixed)
  }
}

/**
 * Una tappa della barra a tappe (il `Segment` di `Notification.ProgressStyle` di Android 16): un pezzo del lavoro lungo
 * [length] unità, con un colore facoltativo (ARGB). Le tappe di un viaggio sono i tratti: a piedi, il bus, a piedi.
 */
class FluidLiveStage(val length: Int, val color: Int? = null) {
  init {
    require(length > 0) { "una tappa e' lunga almeno 1: era $length" }
  }

  override fun equals(other: Any?): Boolean = other is FluidLiveStage && other.length == length && other.color == color
  override fun hashCode(): Int = 31 * length + (color ?: 0)
  override fun toString(): String = "FluidLiveStage($length)"
}

/** Un punto sulla barra a tappe (il `Point` di `ProgressStyle`): una fermata, a [position] unità dall'inizio. */
class FluidLivePoint(val position: Int, val color: Int? = null) {
  init {
    require(position >= 0) { "un punto sta da 0 in su: era $position" }
  }

  override fun equals(other: Any?): Boolean = other is FluidLivePoint && other.position == position && other.color == color
  override fun hashCode(): Int = 31 * position + (color ?: 0)
  override fun toString(): String = "FluidLivePoint($position)"
}

/**
 * Un comando dell'attività (pausa, annulla, termina): una scritta e cosa fa. Fuori dall'app è un tasto della notifica, che
 * chiama la stessa funzione finché il processo dell'app è vivo (il lavoro lungo, di solito, lo tiene vivo un servizio). Se
 * il processo è morto la notifica era vecchia: il tocco la toglie.
 *
 * @property key l'identità del comando fra un aggiornamento e l'altro (di serie la scritta).
 */
class FluidLiveAction(
  val label: String,
  val onClick: () -> Unit,
  val destructive: Boolean = false,
  val key: String = label,
) {
  override fun equals(other: Any?): Boolean =
    other is FluidLiveAction && other.label == label && other.destructive == destructive && other.key == key
  override fun hashCode(): Int = 31 * (31 * label.hashCode() + destructive.hashCode()) + key.hashCode()
  override fun toString(): String = "FluidLiveAction($label)"
}

/**
 * Chi ha avviato l'attività: le linee guida dei Live Update di Android li vogliono **solo per attività avviate
 * dall'utente** («Don't allow activities triggered by other parties to generate Live Updates»), e un evento seguito in
 * sottofondo vuole un tasto per smettere di seguirlo («Unpin»). Non è un `enum` (regola 3.11).
 */
class FluidLiveOrigin private constructor(internal val code: Int) {
  /** Vero se l'origine permette la promozione a Live Update. */
  val promotable: Boolean get() = code != OthersCode

  /** Vero se l'attività è di qualcun altro e l'utente ha scelto di seguirla: serve il tasto per smettere. */
  val followed: Boolean get() = code == FollowedCode

  override fun equals(other: Any?): Boolean = other is FluidLiveOrigin && other.code == code
  override fun hashCode(): Int = code
  override fun toString(): String = "FluidLiveOrigin(${listOf("user", "followed", "others")[code]})"

  companion object {
    private const val UserCode = 0
    private const val FollowedCode = 1
    private const val OthersCode = 2

    /** Avviata dall'utente su questo telefono (di serie). */
    val User: FluidLiveOrigin = FluidLiveOrigin(UserCode)

    /**
     * Di un'altra persona, che l'utente ha **scelto di seguire** su questo telefono (il viaggio di Alessio che la mamma
     * segue con «Avvisa quando arrivo»): può diventare Live Update, e l'engine aggiunge il tasto «Smetti di seguire».
     */
    val Followed: FluidLiveOrigin = FluidLiveOrigin(FollowedCode)

    /** Avviata da altri senza una scelta dell'utente: una notifica con l'avanzamento, mai un Live Update. */
    val Others: FluidLiveOrigin = FluidLiveOrigin(OthersCode)
  }
}

/**
 * Un'attività viva: la stessa descrizione per la pillola dentro l'app e per la notifica fuori. L'[id] è l'identità:
 * pubblicare di nuovo lo stesso `id` aggiorna.
 *
 * @param kind il genere: l'icona, la categoria, se può diventare Live Update.
 * @param title la frase breve («Converto il video»): il titolo della notifica, obbligatorio per un Live Update.
 * @param text la riga sotto il titolo («3 di 10 file», «Arriva alle 18:42»).
 * @param progress l'avanzamento da 0 a 1, o null. Senza avanzamento e senza [time] la barra gira (lavoro senza misura);
 *   senza avanzamento ma con un tempo, niente barra e il cronometro.
 * @param stages le tappe della barra (Android 16; prima, una barra sola). Le lunghezze sono in unità qualsiasi.
 * @param points i punti sulla barra (le fermate), nelle stesse unità delle tappe.
 * @param time il tempo da mostrare (il cronometro della notifica, il gettone della barra di stato).
 * @param chip il testo breve del gettone nella barra di stato di Android 16 (`setShortCriticalText`): sotto i 7 caratteri
 *   si vede intero (developer.android.com, «Live Updates», fiducia alta). Di serie la percentuale, o niente.
 * @param paused in pausa: la barra si ferma, il tempo è fermo.
 * @param actions i comandi (al massimo tre: Android ne mostra tre).
 * @param open l'indirizzo da aprire al tocco (un indirizzo di pagina di F8.12); di serie l'app.
 * @param promote vero o falso per forzare la scelta; null la lascia al genere e all'origine.
 * @param origin chi l'ha avviata.
 */
data class FluidLiveUpdate(
  val id: String,
  val kind: FluidLiveKind,
  val title: String,
  val text: String? = null,
  val progress: Float? = null,
  val stages: List<FluidLiveStage> = emptyList(),
  val points: List<FluidLivePoint> = emptyList(),
  val time: FluidLiveTime? = null,
  val chip: String? = null,
  val paused: Boolean = false,
  val actions: List<FluidLiveAction> = emptyList(),
  val open: String? = null,
  val promote: Boolean? = null,
  val origin: FluidLiveOrigin = FluidLiveOrigin.User,
) {
  init {
    require(title.isNotBlank()) { "un'attivita' viva ha un titolo: Android non promuove una notifica senza" }
  }

  /** L'avanzamento come percentuale intera (0–100), o null. */
  val percent: Int? get() = progress?.let { (it.coerceIn(0f, 1f) * 100f).toInt() }

  /** Vero se la barra gira (nessuna misura e nessun tempo). */
  val indeterminate: Boolean get() = progress == null && time == null && stages.isEmpty()

  /** Vero se l'attività chiede di diventare Live Update: la scelta esplicita, o genere e origine che lo permettono. */
  val wantsPromotion: Boolean get() = promote ?: (kind.promotable && origin.promotable)
}

/**
 * Come finisce un'attività viva. Non è un `enum` (regola 3.11).
 */
class FluidLiveOutcome private constructor(internal val code: Int, val message: String?) {
  /** Riuscita. */
  val succeeded: Boolean get() = code == SuccessCode

  /** Non riuscita. */
  val failed: Boolean get() = code == FailureCode

  /** Finita senza esito da mostrare (annullata, o l'esito lo dice qualcun altro). */
  val silent: Boolean get() = code == SilentCode

  override fun equals(other: Any?): Boolean = other is FluidLiveOutcome && other.code == code && other.message == message
  override fun hashCode(): Int = 31 * code + (message?.hashCode() ?: 0)
  override fun toString(): String = "FluidLiveOutcome(${listOf("success", "failure", "silent")[code]}, $message)"

  companion object {
    private const val SuccessCode = 0
    private const val FailureCode = 1
    private const val SilentCode = 2

    /** Riuscita, con un messaggio facoltativo («Video pronto»). */
    fun succeeded(message: String? = null): FluidLiveOutcome = FluidLiveOutcome(SuccessCode, message)

    /** Non riuscita, con il perché («Rete assente»). */
    fun failed(message: String? = null): FluidLiveOutcome = FluidLiveOutcome(FailureCode, message)

    /** Finita senza esito: la notifica se ne va. */
    val Silent: FluidLiveOutcome = FluidLiveOutcome(SilentCode, null)
  }
}

/**
 * Dove va un'attività viva fuori dall'app: lo implementa `engine-notify` (`FluidLiveUpdates`). La pillola di engine-ui ci
 * specchia le sue attività (`FluidActivityHostState.mirrorOutside`), un servizio ci pubblica direttamente.
 */
interface FluidLiveUpdateSink {
  /** Pubblica [update], o lo aggiorna se c'è già un'attività con lo stesso `id`. */
  fun publish(update: FluidLiveUpdate)

  /** Finisce l'attività [id] con [outcome]. */
  fun finish(id: String, outcome: FluidLiveOutcome)

  /** Toglie l'attività [id] subito, senza esito. */
  fun remove(id: String)

  /**
   * Vero quando le attività si vedono fuori: per uno specchio della pillola, quando l'app non è in primo piano. Chi
   * specchia rilegge l'avanzamento solo allora (dentro lo disegna la pillola).
   */
  val outside: StateFlow<Boolean>

  /** Ogni quanto ha senso rileggere un avanzamento che cambia di continuo, in millisecondi. */
  val sampleMillis: Long get() = 1_000L
}
