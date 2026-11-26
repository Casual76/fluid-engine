package dev.antigravity.fluidengine.wear.sync

/**
 * Uno stato che il telefono pubblica e l'orologio mostra anche ad app chiusa: la prossima lezione, le mail non lette, il
 * timer in corso. L'orologio tiene l'ultimo per ogni [key] (un aggiornamento più vecchio di quello tenuto si scarta: i
 * messaggi del Data Layer non arrivano sempre in ordine) e lo mostra nelle Tile e nelle complicazioni di `engine-wear`.
 *
 * Come i widget di watchOS che leggono la timeline dell'app del telefono (WidgetKit, Smart Stack di watchOS 10): lo stato
 * è un dato, non un disegno. Il disegno lo fa l'orologio, col suo tema.
 *
 * @property key il nome stabile dello stato («lezione», «non-lette»): è anche l'indirizzo sul Data Layer.
 * @property title la riga piccola sopra (la materia, «Posta»).
 * @property value il valore grande, corto (un orario, un numero): sta nel disco della Tile e nel testo breve della
 * complicazione.
 * @property detail la riga sotto, facoltativa («Aula 3 · fra 12 min»).
 * @property progress l'avanzamento da 0 a 1 per l'anello sul bordo e per la complicazione a intervallo; `null` se lo
 * stato non ne ha uno.
 * @property updatedAtMillis quando il telefono l'ha scritto (orologio di sistema del telefono).
 * @property staleAfterMillis da quando non è più vero (la lezione è finita): l'orologio lo mostra spento con «Non
 * aggiornato». `null` se non scade.
 * @property openUri il link che il tocco apre sull'orologio (l'app dell'orologio lo risolve), facoltativo.
 */
data class FluidWearStatus(
  val key: String,
  val title: String,
  val value: String,
  val detail: String? = null,
  val progress: Float? = null,
  val updatedAtMillis: Long,
  val staleAfterMillis: Long? = null,
  val openUri: String? = null,
) {
  init {
    require(key.isNotBlank() && FluidWearPaths.isSafeSegment(key)) { "chiave non valida: «$key»" }
    require(progress == null || progress in 0f..1f) { "progress fuori da 0..1: $progress" }
  }

  /** Vero se a [nowMillis] lo stato non è più attuale ([staleAfterMillis] passato). */
  fun isStale(nowMillis: Long): Boolean = staleAfterMillis != null && nowMillis >= staleAfterMillis
}

/**
 * Un evento salvato al polso: un brano messo fra i preferiti, un giro finito, «segna come letto». Nasce sull'orologio,
 * resta nella sua coda ([FluidWearOutbox]) anche col telefono lontano e si dice «Registrato» solo quando il telefono
 * rimanda la [FluidWearReceipt].
 *
 * Il riferimento è `WCSession.transferUserInfo(_:)` di Watch Connectivity: il trasferimento si accoda e arriva quando i
 * due dispositivi si ritrovano (fiducia alta, documentazione Apple). La ricevuta dopo il salvataggio non c'è in Apple: è
 * dell'engine (scheda `wear-con-ricevuta`).
 *
 * @property id l'identità dell'evento, unica: il telefono la usa per riconoscere una consegna ripetuta.
 * @property type il significato, deciso dall'app («preferito», «giro»).
 * @property payload il contenuto, deciso dall'app (un JSON, un id): al massimo [MaxPayloadBytes] in UTF-8.
 * @property createdAtMillis quando il polso l'ha salvato (orologio di sistema dell'orologio).
 * @property expiresAtMillis da quando non vale più: un comando con un effetto subito («riproduci») deve scadere, se no
 * arriva un'ora dopo e fa partire la musica nel posto sbagliato. `null` se non scade.
 */
data class FluidWearEvent(
  val id: String,
  val type: String,
  val payload: String,
  val createdAtMillis: Long,
  val expiresAtMillis: Long? = null,
) {
  init {
    require(id.isNotBlank() && FluidWearPaths.isSafeSegment(id)) { "id non valido: «$id»" }
    require(type.isNotBlank()) { "il tipo dell'evento è vuoto" }
    require(payload.toByteArray(Charsets.UTF_8).size <= MaxPayloadBytes) {
      "il contenuto supera $MaxPayloadBytes byte: il Data Layer tiene al massimo 100 KB per elemento"
    }
  }

  /** Vero se a [nowMillis] l'evento è scaduto. */
  fun isExpired(nowMillis: Long): Boolean = expiresAtMillis != null && nowMillis >= expiresAtMillis

  companion object {
    /**
     * Il tetto del contenuto: 64 KiB. Il Data Layer tiene al massimo 100 KB per elemento (documentazione Android, «Sync
     * data items», letta il 26/11/2026); il resto lascia posto all'involucro. Per un file serve un `Asset`, fuori da qui.
     */
    const val MaxPayloadBytes: Int = 64 * 1024
  }
}

/** Come il telefono ha trattato un evento. */
enum class FluidWearReceiptOutcome {
  /** Salvato davvero dall'app del telefono: il polso dice «Registrato». */
  Registered,

  /** Arrivato dopo [FluidWearEvent.expiresAtMillis]: non applicato. Il polso dice «Scaduto». */
  Expired,

  /** Letto ma rifiutato (un tipo che l'app non conosce, un formato più nuovo): il polso dice «Non registrato». */
  Refused,
}

/**
 * La ricevuta che il telefono rimanda **dopo** aver salvato l'evento (non quando l'ha ricevuto: una trasmissione riuscita
 * può precedere il salvataggio, ed è il rischio scritto nella scheda).
 */
data class FluidWearReceipt(
  val eventId: String,
  val outcome: FluidWearReceiptOutcome,
  val atMillis: Long,
)

/** Lo stato di un evento visto dal polso. */
enum class FluidWearDeliveryState(
  /** La parola che il polso mostra, in italiano semplice. */
  val label: String,
) {
  /** Salvato al polso, la ricevuta non è ancora tornata (telefono lontano, o il telefono non l'ha ancora salvato). */
  Queued("In coda"),

  /** Il telefono l'ha salvato e l'ha detto. */
  Registered("Registrato"),

  /** Scaduto prima di arrivare (sul polso o sul telefono): non è stato applicato. */
  Expired("Scaduto"),

  /** Il telefono l'ha letto e rifiutato. */
  Refused("Non registrato"),
}

/**
 * Un evento nella coda del polso, con il suo stato.
 *
 * @property attempts quante volte è stato consegnato al trasporto.
 * @property lastSentAtMillis l'ultima consegna al trasporto, `null` se mai.
 * @property settledAtMillis quando lo stato è diventato definitivo (ricevuta o scadenza), `null` finché è in coda.
 * @property confirmedByPhone vero se lo stato viene da una ricevuta del telefono: allora non cambia più. Una scadenza
 * decisa al polso invece può ancora diventare «Registrato», se il telefono l'aveva salvato in tempo e la ricevuta
 * arriva dopo.
 */
data class FluidWearDelivery(
  val event: FluidWearEvent,
  val state: FluidWearDeliveryState,
  val attempts: Int = 0,
  val lastSentAtMillis: Long? = null,
  val settledAtMillis: Long? = null,
  val confirmedByPhone: Boolean = false,
)

/** Chi è questo lato del ponte: decide quali messaggi gli spettano. */
enum class FluidWearRole {
  /** Pubblica gli stati, riceve gli eventi, rimanda le ricevute. */
  Phone,

  /** Tiene gli stati, salva gli eventi, riceve le ricevute. */
  Watch,
}
