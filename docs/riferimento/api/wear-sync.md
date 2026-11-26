<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/wear-sync.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · engine-wear-sync

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-wear-sync/wear/sync/FluidWearDataLayer.kt`

- `class FluidWearDataLayer(private val dataClient: DataClient, val sync: FluidWearSync)` — Il ponte sopra il Data Layer di Wear OS (`DataClient` di Play services), con il client dell'app.
  - `suspend fun publish(status: FluidWearStatus)` — Telefono: pubblica [status]. L'orologio lo tiene e lo mostra anche ad app chiusa.
  - `suspend fun withdraw(key: String)` — Telefono: toglie lo stato [key] (non è più vero e non c'è niente da mostrare).
  - `suspend fun flush(resendAll: Boolean = false): Int` — Polso: consegna al Data Layer gli eventi in coda che lo aspettano ([FluidWearOutbox.due]). Torna quanti. Un `putDataItem` che non riesce lascia l'evento in coda: si riprova al prossimo [flush].
  - `suspend fun sendReceipt(receipt: FluidWearReceipt)` — Telefono: rimanda la ricevuta [receipt].
  - `suspend fun pullStatuses(): Int` — Polso: legge gli stati già sul Data Layer (quelli arrivati prima che l'app fosse installata o mentre il servizio non girava) e li tiene. Torna quanti sono cambiati.
  - `suspend fun forget(eventId: String)` — Toglie dal Data Layer l'evento [eventId] e la sua ricevuta, quando l'evento è chiuso: gli elementi non scadono da soli, e senza pulizia resterebbero per sempre su tutti e due i dispositivi.
  - `fun handle(events: Iterable<DataEvent>): List<FluidWearRouted>` — Smista gli eventi di `onDataChanged`. Va chiamato dentro il callback: il buffer vale solo lì.
  - `companion object`
    - `fun route(sync: FluidWearSync, events: Iterable<DataEvent>): List<FluidWearRouted>` — [handle] senza il client: smista [events] nel ponte [sync].
    - `const val BytesKey: String` — Il campo della `DataMap` coi byte del messaggio.
    - `fun requestFor(path: String, bytes: ByteArray): PutDataRequest` — La richiesta urgente per [path] coi [bytes].
    - `fun bytesOf(item: DataItem): ByteArray?` — I byte del messaggio dentro [item], o `null` se non è un elemento del ponte.
    - `fun wildcard(path: String): Uri` — L'indirizzo [path] su tutti i dispositivi (nessun nodo nell'indirizzo).
- `abstract class FluidWearListenerService : WearableListenerService()` — Il servizio del Data Layer, pronto: smista gli elementi del ponte anche ad app chiusa (Wear OS e Android avviano il servizio quando arriva qualcosa). L'app lo estende, dice il suo [role] e lo dichiara nel manifest:

## `engine-wear-sync/wear/sync/FluidWearFormat.kt`

- `object FluidWearFormat` — I byte che viaggiano fra il telefono e l'orologio: un involucro piccolo e versionato per lo stato, l'evento e la ricevuta.
  - `const val Version: Int` — La versione che questo engine scrive e legge.
  - `fun encode(status: FluidWearStatus): ByteArray` — Lo stato in byte.
  - `fun encode(event: FluidWearEvent, attempt: Int = 0): ByteArray` — L'evento in byte. [attempt] è il numero della consegna: cambia i byte a ogni nuovo invio, così il Data Layer (che non avvisa per un elemento rimesso identico) lo fa arrivare di nuovo al telefono. Chi legge lo ignora.
  - `fun encode(receipt: FluidWearReceipt): ByteArray` — La ricevuta in byte.
  - `fun decode(bytes: ByteArray): FluidWearDecoded` — Legge un messaggio. Non lancia mai: un messaggio rotto torna come [FluidWearDecoded.Unreadable].
- `sealed interface FluidWearDecoded` — Un messaggio letto.
  - `data class Status(val status: FluidWearStatus) : FluidWearDecoded` — Uno stato pubblicato dal telefono.
  - `data class Event(val event: FluidWearEvent, val attempt: Int = 0) : FluidWearDecoded` — Un evento del polso, con il numero della consegna ([FluidWearFormat.encode]).
  - `data class Receipt(val receipt: FluidWearReceipt) : FluidWearDecoded` — Una ricevuta del telefono.
  - `data class Unsupported(val version: Int, val eventId: String?) : FluidWearDecoded` — Un messaggio FWS di un'altra versione: non si interpreta. Se è un evento, [eventId] è il suo id (la promessa del formato), così il telefono può rispondere «Non registrato».
  - `data class Unreadable(val reason: String) : FluidWearDecoded` — Non è un messaggio FWS, o è rotto.
- `object FluidWearPaths` — Gli indirizzi sul Data Layer, tutti sotto [Root]. Un'app li filtra nel manifest del suo `WearableListenerService` (`<data android:scheme="wear" android:host="*" android:pathPrefix="/fluid-wear" />`).
  - `const val Root: String`
  - `const val StatusPrefix: String`
  - `const val EventPrefix: String`
  - `const val ReceiptPrefix: String`
  - `fun status(key: String): String`
  - `fun event(id: String): String`
  - `fun receipt(eventId: String): String`
  - `fun isSafeSegment(value: String): Boolean` — Un pezzo d'indirizzo sicuro: lettere, cifre, `-`, `_`, `.`, fino a 128 caratteri.

## `engine-wear-sync/wear/sync/FluidWearInbox.kt`

- `class FluidWearInbox(private val store: FluidWearStore, private val clock: () -> Long = System::currentTimeMillis, val retainMillis: Long = DefaultRetainMillis, val maxRemembered: Int = DefaultMaxRemembered)` — Il registro del telefono: ricorda quali eventi del polso ha già trattato e con quale esito, così una consegna ripetuta riceve **la stessa ricevuta** e non si applica due volte.
  - `fun receive(event: FluidWearEvent): FluidWearInboxResult` — Il primo passo: che cosa fare di [event].
  - `fun confirm(eventId: String, outcome: FluidWearReceiptOutcome = FluidWearReceiptOutcome.Registered): FluidWearReceipt` — Il terzo passo: l'app ha trattato [eventId] con l'esito [outcome] (di serie «Registrato»: salvato). Registra su disco e torna la ricevuta da rimandare. Chiamato due volte, torna la ricevuta della prima.
  - `fun receiptFor(eventId: String): FluidWearReceipt?` — La ricevuta già data per [eventId], se c'è.
  - `suspend fun handle(event: FluidWearEvent, save: suspend (FluidWearEvent) -> Boolean): FluidWearReceipt` — I tre passi in fila. [save] salva l'evento e dice se l'ha accettato (`false`: un tipo che l'app non conosce, e la ricevuta dice «Non registrato»). Se [save] lancia, non c'è ricevuta: il polso riconsegnerà.
  - `fun refuseUnsupported(eventId: String): FluidWearReceipt` — Un evento di una versione del formato che questo telefono non sa leggere ([FluidWearDecoded.Unsupported]): si risponde «Non registrato», così il polso smette di aspettare e l'app può dire di aggiornare l'app del telefono.
  - `companion object`
    - `const val DefaultRetainMillis: Long` — Una settimana. A occhio, 26/11/2026: più a lungo di qualunque coda del polso ([FluidWearOutbox.DefaultKeepSettledMillis]).
    - `const val DefaultMaxRemembered: Int` — 2000 eventi: qualche decina di byte l'uno.
- `sealed interface FluidWearInboxResult` — Il primo passo del registro ([FluidWearInbox.receive]).
  - `data class New(val event: FluidWearEvent) : FluidWearInboxResult` — Un evento mai visto: l'app lo salva e poi chiama [FluidWearInbox.confirm].
  - `data class Repeated(val receipt: FluidWearReceipt) : FluidWearInboxResult` — Un evento già trattato: si rimanda [receipt], senza applicarlo di nuovo.
  - `data class Expired(val receipt: FluidWearReceipt) : FluidWearInboxResult` — Arrivato dopo la scadenza: non si applica, si rimanda [receipt] («Scaduto»). Già registrato.

## `engine-wear-sync/wear/sync/FluidWearMessages.kt`

- `data class FluidWearStatus(val key: String, val title: String, val value: String, val detail: String? = null, val progress: Float? = null, val updatedAtMillis: Long, val staleAfterMillis: Long? = null, val openUri: String? = null)` — Uno stato che il telefono pubblica e l'orologio mostra anche ad app chiusa: la prossima lezione, le mail non lette, il timer in corso. L'orologio tiene l'ultimo per ogni [key] (un aggiornamento più vecchio di quello tenuto si scarta: i messaggi del Data Layer non arrivano sempre in ordine) e lo mostra nelle Tile e nelle complicazioni di `engine-wear`.
  - `fun isStale(nowMillis: Long): Boolean` — Vero se a [nowMillis] lo stato non è più attuale ([staleAfterMillis] passato).
- `data class FluidWearEvent(val id: String, val type: String, val payload: String, val createdAtMillis: Long, val expiresAtMillis: Long? = null)` — Un evento salvato al polso: un brano messo fra i preferiti, un giro finito, «segna come letto». Nasce sull'orologio, resta nella sua coda ([FluidWearOutbox]) anche col telefono lontano e si dice «Registrato» solo quando il telefono rimanda la [FluidWearReceipt].
  - `fun isExpired(nowMillis: Long): Boolean` — Vero se a [nowMillis] l'evento è scaduto.
  - `companion object`
    - `const val MaxPayloadBytes: Int` — Il tetto del contenuto: 64 KiB. Il Data Layer tiene al massimo 100 KB per elemento (documentazione Android, «Sync data items», letta il 26/11/2026); il resto lascia posto all'involucro. Per un file serve un `Asset`, fuori da qui.
- `enum class FluidWearReceiptOutcome` — Come il telefono ha trattato un evento.
- `data class FluidWearReceipt(val eventId: String, val outcome: FluidWearReceiptOutcome, val atMillis: Long)` — La ricevuta che il telefono rimanda **dopo** aver salvato l'evento (non quando l'ha ricevuto: una trasmissione riuscita può precedere il salvataggio, ed è il rischio scritto nella scheda).
- `enum class FluidWearDeliveryState(val label: String)` — Lo stato di un evento visto dal polso.
- `data class FluidWearDelivery(val event: FluidWearEvent, val state: FluidWearDeliveryState, val attempts: Int = 0, val lastSentAtMillis: Long? = null, val settledAtMillis: Long? = null, val confirmedByPhone: Boolean = false)` — Un evento nella coda del polso, con il suo stato.
- `enum class FluidWearRole` — Chi è questo lato del ponte: decide quali messaggi gli spettano.

## `engine-wear-sync/wear/sync/FluidWearOutbox.kt`

- `class FluidWearOutbox(private val store: FluidWearStore, private val clock: () -> Long = System::currentTimeMillis, private val newId: () -> String = { UUID.randomUUID().toString() }, val resendAfterMillis: Long = DefaultResendAfterMillis, val keepSettledMillis: Long = DefaultKeepSettledMillis)` — La coda del polso: gli eventi salvati sull'orologio, ciascuno «In coda» finché il telefono non rimanda la ricevuta.
  - `val deliveries: StateFlow<List<FluidWearDelivery>>` — Gli eventi in coda e quelli chiusi da poco, nell'ordine in cui sono stati salvati.
  - `fun enqueue(type: String, payload: String = "", ttlMillis: Long? = null): FluidWearEvent` — Salva un evento al polso e lo mette in coda. [ttlMillis] è la sua vita: oltre, l'evento scade e non si applica più (un comando con un effetto subito vuole una vita corta, per esempio 30 s; un preferito nessuna).
  - `fun due(resendAll: Boolean = false): List<FluidWearOutgoing>` — Gli eventi da consegnare al trasporto adesso. Prima chiude «Scaduto» quelli oltre la scadenza. Con [resendAll] vero (il telefono è appena tornato vicino) riconsegna tutti quelli in coda, senza aspettare [resendAfterMillis].
  - `fun markSent(eventId: String)` — Il trasporto ha preso in carico l'evento [eventId] (per il Data Layer: `putDataItem` riuscito).
  - `fun onReceipt(receipt: FluidWearReceipt): FluidWearDelivery?` — La ricevuta del telefono. Torna l'evento chiuso, o `null` se la ricevuta non cambia niente (un evento che non c'è, o già chiuso da un'altra ricevuta).
  - `fun expire(): List<FluidWearDelivery>` — Chiude «Scaduto» gli eventi in coda oltre la scadenza e torna quelli appena chiusi.
  - `fun get(eventId: String): FluidWearDelivery?` — L'evento [eventId], se è ancora in elenco.
  - `fun prune()` — Toglie gli eventi chiusi più vecchi di [keepSettledMillis].
  - `companion object`
    - `const val DefaultResendAfterMillis: Long` — 15 minuti: abbastanza per non insistere a ogni risveglio, abbastanza poco per un telefono che ha perso l'evento. A occhio, 26/11/2026.
    - `const val DefaultKeepSettledMillis: Long` — Un giorno: l'evento chiuso resta visibile al polso fino a domani. A occhio, 26/11/2026.
    - `const val MaxDeliveries: Int` — Il tetto dell'elenco.
- `class FluidWearOutgoing(val event: FluidWearEvent, val attempt: Int)` — Un evento da consegnare al trasporto, col numero della consegna ([FluidWearFormat.encode]).
  - `fun bytes(): ByteArray` — I byte da mettere sul Data Layer.
  - `val path: String get()` — L'indirizzo sul Data Layer.

## `engine-wear-sync/wear/sync/FluidWearStatusStore.kt`

- `class FluidWearStatusStore(private val store: FluidWearStore)` — Gli stati pubblicati dal telefono, tenuti sull'orologio: l'ultimo per chiave, su disco.
  - `val statuses: StateFlow<Map<String, FluidWearStatus>>` — Gli stati tenuti, per chiave.
  - `fun get(key: String): FluidWearStatus?` — Lo stato [key], o `null`.
  - `fun put(status: FluidWearStatus): Boolean` — Tiene [status] se è più nuovo di quello che c'è. Torna vero se ha cambiato qualcosa.
  - `fun remove(key: String): Boolean` — Dimentica lo stato [key] (il telefono l'ha tolto). Torna vero se c'era.

## `engine-wear-sync/wear/sync/FluidWearStore.kt`

- `interface FluidWearStore` — Dove il ponte tiene la coda, il registro e gli stati: pochi file piccoli, riscritti interi.
  - `fun read(name: String): ByteArray?` — I byte del file [name], o `null` se non c'è.
  - `fun write(name: String, bytes: ByteArray)` — Sostituisce il file [name] con [bytes], tutto o niente.
- `class FluidWearFileStore(private val directory: File) : FluidWearStore` — L'archivio su disco: un file per nome in [directory], scritto in un file accanto e poi spostato sopra quello vecchio con uno spostamento atomico (lo stesso schema di `AtomicFile` di Android), dopo `fsync`.
- `class FluidWearMemoryStore : FluidWearStore` — L'archivio in memoria: per le prove e per la galleria. Una copia dei byte entra e una esce.

## `engine-wear-sync/wear/sync/FluidWearSync.kt`

- `class FluidWearSync(val role: FluidWearRole, private val store: FluidWearStore, private val clock: () -> Long = System::currentTimeMillis, private val newId: () -> String = { UUID.randomUUID().toString() })` — Il ponte fra il telefono e l'orologio, un lato alla volta: la coda del polso ([outbox]), il registro del telefono ([inbox]) e gli stati tenuti dall'orologio ([statuses]), più lo smistamento dei messaggi che arrivano ([route]).
  - `val outbox: FluidWearOutbox by lazy` — La coda del polso (lato orologio).
  - `val inbox: FluidWearInbox by lazy` — Il registro degli eventi trattati (lato telefono).
  - `val statuses: FluidWearStatusStore by lazy` — Gli stati tenuti (lato orologio).
  - `fun now(): Long` — L'ora di questo lato, quella con cui il ponte decide le scadenze.
  - `fun route(path: String, bytes: ByteArray): FluidWearRouted` — Smista un elemento cambiato all'indirizzo [path] coi suoi [bytes]. Non lancia.
  - `fun routeDeleted(path: String): FluidWearRouted` — Smista un elemento tolto all'indirizzo [path]: per l'orologio, il telefono ha tolto uno stato.
  - `companion object`
    - `fun get(context: Context, role: FluidWearRole): FluidWearSync` — Il ponte del processo, con l'archivio su disco in `noBackupFilesDir/fluid-wear-sync` (fuori dal backup). Lo stesso oggetto per il servizio del Data Layer, la Tile, la complicazione e l'app: una coda sola, un registro solo.
    - `const val DirectoryName: String` — La cartella dell'archivio, dentro `noBackupFilesDir`.
- `sealed interface FluidWearRouted` — Che cosa ha fatto [FluidWearSync.route] con un messaggio.
  - `data class StatusChanged(val status: FluidWearStatus) : FluidWearRouted` — Orologio: uno stato nuovo, già tenuto. Si chiede alle Tile e alle complicazioni di ridisegnarsi.
  - `data class StatusRemoved(val key: String) : FluidWearRouted` — Orologio: il telefono ha tolto lo stato [key].
  - `data class EventArrived(val event: FluidWearEvent) : FluidWearRouted` — Telefono: un evento nuovo. L'app lo salva, poi `inbox.confirm(event.id)` e rimanda la ricevuta.
  - `data class ReceiptToSend(val receipt: FluidWearReceipt, val repeated: Boolean) : FluidWearRouted` — Telefono: una ricevuta da rimandare subito (consegna ripetuta, evento scaduto, formato più nuovo).
  - `data class DeliverySettled(val delivery: FluidWearDelivery) : FluidWearRouted` — Orologio: un evento chiuso dalla ricevuta del telefono.
  - `data class Ignored(val reason: String) : FluidWearRouted` — Niente da fare, con il perché (per il log).
