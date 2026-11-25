package dev.antigravity.fluidengine.foundation.transfer

import dev.antigravity.fluidengine.foundation.FluidConditionReason

// F8.20 · Centro trasferimenti (`centro-trasferimenti`): il modello che la coda (`engine-transfer`) produce e che la
// pagina «Trasferimenti» e la pillola (`engine-ui`) disegnano. Sta qui, in foundation, per la stessa ragione di
// `SyncStatus`: l'interfaccia non deve tirarsi dentro il modulo che parla di rete e di WorkManager, e un'app che fa i
// trasferimenti a modo suo puo' usare la stessa pagina.

/** Il verso di un trasferimento. Non e' un `enum` (regola 3.11). */
class FluidTransferDirection private constructor(val name: String) {
  override fun equals(other: Any?): Boolean = other is FluidTransferDirection && other.name == name
  override fun hashCode(): Int = name.hashCode()
  override fun toString(): String = "FluidTransferDirection($name)"

  companion object {
    val Download: FluidTransferDirection = FluidTransferDirection("download")
    val Upload: FluidTransferDirection = FluidTransferDirection("upload")

    /** Il verso col nome [name] (quello salvato), di serie [Download]. */
    fun named(name: String): FluidTransferDirection = if (name == Upload.name) Upload else Download
  }
}

/**
 * Dove sta un trasferimento. Non e' un `enum` (regola 3.11): uno stato nuovo non rompe i `when` delle app.
 *
 * - [Queued]: in coda, partira' appena tocca a lui;
 * - [Running]: sta trasferendo;
 * - [Waiting]: aspetta le condizioni (il Wi-Fi, la carica, la rete): il perche' e' in [FluidTransferItem.waitingFor];
 * - [Paused]: l'ha fermato l'utente, riprende da dov'era;
 * - [Failed]: non e' riuscito, il motivo e' in [FluidTransferItem.failure]; si riprova;
 * - [Completed], [Cancelled]: finito.
 */
class FluidTransferState private constructor(val name: String) {
  /** In coda, in corso o in attesa: non e' fermo per scelta e non e' finito. */
  val active: Boolean get() = this == Queued || this == Running || this == Waiting

  /** Finito: riuscito o annullato. */
  val finished: Boolean get() = this == Completed || this == Cancelled

  override fun equals(other: Any?): Boolean = other is FluidTransferState && other.name == name
  override fun hashCode(): Int = name.hashCode()
  override fun toString(): String = "FluidTransferState($name)"

  companion object {
    val Queued: FluidTransferState = FluidTransferState("queued")
    val Running: FluidTransferState = FluidTransferState("running")
    val Waiting: FluidTransferState = FluidTransferState("waiting")
    val Paused: FluidTransferState = FluidTransferState("paused")
    val Failed: FluidTransferState = FluidTransferState("failed")
    val Completed: FluidTransferState = FluidTransferState("completed")
    val Cancelled: FluidTransferState = FluidTransferState("cancelled")

    /** Tutti gli stati. */
    val all: List<FluidTransferState> by lazy { listOf(Queued, Running, Waiting, Paused, Failed, Completed, Cancelled) }

    /** Lo stato col nome [name] (quello salvato), di serie [Queued]. */
    fun named(name: String): FluidTransferState = all.firstOrNull { it.name == name } ?: Queued
  }
}

/**
 * Perche' un trasferimento non e' riuscito. [retryable] dice se ha senso riprovare da soli (la rete caduta, un server
 * occupato) o se serve qualcuno (il file non c'e' piu', l'accesso e' negato, il disco e' pieno). Il tasto «Riprova»
 * c'e' sempre.
 *
 * Non e' un `enum` (regola 3.11): [kind] e' il genere, [code] il codice HTTP quando c'e', [message] il testo tecnico.
 */
class FluidTransferFailure private constructor(
  val kind: String,
  val retryable: Boolean,
  val code: Int? = null,
  val message: String? = null,
) {
  override fun equals(other: Any?): Boolean =
    other is FluidTransferFailure && other.kind == kind && other.code == code && other.message == message
  override fun hashCode(): Int = (kind.hashCode() * 31 + (code ?: 0)) * 31 + (message?.hashCode() ?: 0)
  override fun toString(): String = "FluidTransferFailure($kind${code?.let { ", $it" } ?: ""}${message?.let { ", $it" } ?: ""})"

  /** Lo stesso genere con un altro testo tecnico. */
  fun withMessage(message: String?): FluidTransferFailure = FluidTransferFailure(kind, retryable, code, message)

  companion object {
    /** La connessione si e' interrotta o non si e' aperta. */
    fun network(message: String? = null): FluidTransferFailure = FluidTransferFailure(Network, true, null, message)

    /** Il server ha risposto con un errore suo ([code]): 5xx e 429 si riprovano da soli, gli altri no. */
    fun server(code: Int, message: String? = null): FluidTransferFailure =
      FluidTransferFailure(Server, code >= 500 || code == 429 || code == 408, code, message)

    /** Il file non c'e' (404, 410). */
    fun notFound(code: Int = 404): FluidTransferFailure = FluidTransferFailure(NotFound, false, code, null)

    /** L'accesso e' negato (401, 403): servono credenziali nuove. */
    fun unauthorized(code: Int = 401): FluidTransferFailure = FluidTransferFailure(Unauthorized, false, code, null)

    /** Non c'e' spazio, o il file non si scrive o non si legge. */
    fun storage(message: String? = null): FluidTransferFailure = FluidTransferFailure(Storage, false, null, message)

    /** Il file arrivato non e' quello atteso (dimensione o impronta diverse). */
    fun integrity(message: String? = null): FluidTransferFailure = FluidTransferFailure(Integrity, true, null, message)

    /** Altro. */
    fun unknown(message: String? = null): FluidTransferFailure = FluidTransferFailure(Unknown, true, null, message)

    /** Rilegge un motivo salvato. */
    fun restore(kind: String, retryable: Boolean, code: Int?, message: String?): FluidTransferFailure =
      FluidTransferFailure(kind, retryable, code, message)

    const val Network: String = "network"
    const val Server: String = "server"
    const val NotFound: String = "not-found"
    const val Unauthorized: String = "unauthorized"
    const val Storage: String = "storage"
    const val Integrity: String = "integrity"
    const val Unknown: String = "unknown"
  }
}

/**
 * Un trasferimento come si mostra: la riga della pagina «Trasferimenti», la pillola, la notifica.
 *
 * @param id l'identita', stabile per tutta la vita del trasferimento.
 * @param title il nome («Pampa Notes 2.4.apk»).
 * @param subtitle una riga dell'app, facoltativa («Aggiornamento di Pampa Notes»).
 * @param bytesTotal il peso, o -1 se il server non lo dice.
 * @param bytesPerSecond la velocita' media recente, o 0 se non si sa ancora.
 * @param remainingMillis il tempo che manca, o null se non si sa (peso ignoto, velocita' non ancora stabile).
 * @param waitingFor perche' aspetta (in [FluidTransferState.Waiting]): il primo e' il principale.
 * @param canForce «Fallo comunque» lo farebbe partire (aspetta solo per motivi che si possono forzare).
 * @param resumable il server riprende da dove si era fermato (accetta `Range`); falso dopo un server che ha ricominciato
 *   da capo.
 * @param attempts i tentativi fatti dopo un errore.
 * @param wifiOnly, chargingOnly i vincoli scelti per questo trasferimento.
 */
data class FluidTransferItem(
  val id: String,
  val title: String,
  val direction: FluidTransferDirection,
  val state: FluidTransferState,
  val bytesDone: Long = 0L,
  val bytesTotal: Long = -1L,
  val bytesPerSecond: Long = 0L,
  val remainingMillis: Long? = null,
  val subtitle: String? = null,
  val failure: FluidTransferFailure? = null,
  val waitingFor: List<FluidConditionReason> = emptyList(),
  val canForce: Boolean = false,
  val resumable: Boolean = true,
  val attempts: Int = 0,
  val wifiOnly: Boolean = false,
  val chargingOnly: Boolean = false,
  val createdAtMillis: Long = 0L,
  val finishedAtMillis: Long? = null,
) {
  /** L'avanzamento da 0 a 1, o null se il peso non si sa. */
  val progress: Float?
    get() = if (bytesTotal > 0L) (bytesDone.toDouble() / bytesTotal.toDouble()).toFloat().coerceIn(0f, 1f)
    else if (state == FluidTransferState.Completed) 1f else null
}

/**
 * Tutti i trasferimenti insieme, per la pillola e la notifica: quanti ce ne sono in giro, l'avanzamento complessivo, la
 * velocita' e il tempo che manca.
 */
data class FluidTransferOverview(val items: List<FluidTransferItem> = emptyList()) {
  /** In coda, in corso o in attesa. */
  val active: List<FluidTransferItem> get() = items.filter { it.state.active }

  /** Quello che sta trasferendo adesso, o null. */
  val running: FluidTransferItem? get() = items.firstOrNull { it.state == FluidTransferState.Running }

  /** Quelli in attesa delle condizioni. */
  val waiting: List<FluidTransferItem> get() = items.filter { it.state == FluidTransferState.Waiting }

  /** Quelli non riusciti. */
  val failed: List<FluidTransferItem> get() = items.filter { it.state == FluidTransferState.Failed }

  /** Quelli fermati dall'utente. */
  val paused: List<FluidTransferItem> get() = items.filter { it.state == FluidTransferState.Paused }

  /**
   * L'avanzamento di tutti quelli in giro (attivi e in pausa), pesato sui byte, o null se qualcuno non ha un peso.
   */
  val progress: Float?
    get() {
      val shown = items.filter { it.state.active || it.state == FluidTransferState.Paused }
      if (shown.isEmpty() || shown.any { it.bytesTotal <= 0L }) return null
      val total = shown.sumOf { it.bytesTotal }
      return if (total <= 0L) null else (shown.sumOf { it.bytesDone }.toDouble() / total).toFloat().coerceIn(0f, 1f)
    }

  /** La velocita' di quello in corso, o 0. */
  val bytesPerSecond: Long get() = running?.bytesPerSecond ?: 0L

  /** Il tempo che manca a tutti quelli in giro alla velocita' di adesso, o null. */
  val remainingMillis: Long?
    get() {
      val speed = bytesPerSecond.takeIf { it > 0L } ?: return null
      val shown = items.filter { it.state.active }
      if (shown.any { it.bytesTotal <= 0L }) return null
      val left = shown.sumOf { (it.bytesTotal - it.bytesDone).coerceAtLeast(0L) }
      return left * 1000L / speed
    }
}

/**
 * I comandi di un trasferimento, quelli della pagina e della pillola. `FluidTransfers` di `engine-transfer` li
 * implementa; un'app con una coda sua li implementa per usare la stessa pagina.
 */
interface FluidTransferControls {
  /** Ferma il trasferimento: riprende da dov'era. */
  fun pause(id: String)

  /** Riprende un trasferimento fermato. */
  fun resume(id: String)

  /** Riprova un trasferimento non riuscito. */
  fun retry(id: String)

  /** «Fallo comunque»: parte anche se le condizioni direbbero di aspettare (non senza rete). */
  fun force(id: String)

  /** Annulla e toglie il file a meta'. */
  fun cancel(id: String)

  /** Toglie dalla lista quelli finiti. */
  fun clearFinished()
}
