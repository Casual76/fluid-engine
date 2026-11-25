<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/sync.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · engine-sync

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-sync/sync/FluidSync.kt`

- `data class SyncConfig(val pageSize: Int = 200, val pushBatchSize: Int = 100, val maxPushRounds: Int = 4, val tombstoneRetentionMillis: Long = 90L * 24 * 60 * 60 * 1000, val maxMergeEdits: Int = FluidTextDiff.DefaultMaxEdits)` — Le regole di lavoro della sincronizzazione. I valori di serie vengono dall'Account Pampa (`b/architettura.md` § 5.3).
- `fun interface SyncScheduler` — Chi fa partire un invio quando c'e' qualcosa da mandare. Con WorkManager: [FluidSyncWork.scheduler].
  - `fun requestSync()`
  - `companion object`
    - `val None: SyncScheduler` — Nessuno: l'app chiama [FluidSync.syncAll] quando vuole (le prove, le scene).
- `data class SyncOverview(val pending: Int = 0, val conflicts: Int = 0, val syncing: Boolean = false, val lastSuccessMillis: Long? = null, val failure: SyncFailure? = null, val failedSpaces: List<SyncSpaceId> = emptyList(), val spaces: Int = 0)` — Lo stato di tutti gli spazi insieme.
- `data class SyncPhrases(val pendingOffline: (Int) -> String = { n -> if (n == 1) "Una modifica in attesa: par…, val offline: String = "Sei offline: le modifiche restano qui.", val retryLater: String = "Il servizio chiede di aspettare: riprovo da so…, val rejected: String = "La sincronizzazione si è fermata.", val unreadable: String = "Un elemento arrivato non si apre: resta com'er…, val waitingForKey: String = "Le modifiche aspettano la chiave dell'account:…)` — Le frasi dello stato, nella lingua dell'app: di serie italiane, [english] per l'inglese.
  - `companion object`
    - `fun english()`
- `fun SyncOverview.toSyncStatus(phrases: SyncPhrases = SyncPhrases(), label: (SyncSpaceId) -> String = { it.value }): SyncStatus` — Lo stato da mostrare con i componenti che ci sono gia' (`FluidSyncIndicator`, `FluidSyncNotice`, `FluidSyncAction`): - in corso: [SyncState.SYNCING]; - senza rete: [SyncState.OFFLINE], con le modifiche in attesa nella frase; - un errore in tutti gli spazi: [SyncState.ERROR]; in alcuni: [SyncState.PARTIAL] con gli spazi in `failedSections`; - altrimenti [SyncState.IDLE], con l'ora dell'ultima riuscita.
- `class SyncReport(val spaces: List<SyncSpaceReport>)` — Cosa ha fatto [FluidSync.syncAll]: un resoconto per spazio.
  - `val failure: SyncFailure? get()` — Il primo errore, o null.
  - `val shouldRetry: Boolean` — Vero se conviene riprovare piu' tardi (rete o relay occupato); falso se e' andato tutto bene o se il relay ha rifiutato.
- `class FluidSync internal constructor(internal val store: SyncStore, private val relay: SyncRelay, internal val sealer: SyncSealer, private val nodeProvider: () -> String, private val wall: () -> Long, internal val dispatcher: CoroutineDispatcher, val config: SyncConfig, private val scheduler: SyncScheduler)` — Il motore della sincronizzazione di un dispositivo: apre gli spazi ([space]), li sincronizza ([syncAll]) e ne riassume lo stato ([overview], [status]).
  - `val clock: SyncClock by lazy` — L'orologio logico del dispositivo. Nasce alla prima scrittura o lettura, e alla prima apertura di uno spazio riprende l'ultimo stamp salvato nel registro: monotono anche fra un avvio e l'altro. Il nodo si legge solo allora, fuori dal thread principale.
  - `val overview: StateFlow<SyncOverview>` — Lo stato di tutti gli spazi aperti.
  - `val status: StateFlow<SyncStatus>` — Lo stato nella forma dei componenti dell'engine ([toSyncStatus] con le frasi italiane).
  - `val node: String get()` — Il nome di questo dispositivo nell'orologio logico: e' il [SyncVersion.node] delle sue revisioni.
  - `fun space(id: SyncSpaceId): SyncSpace` — Lo spazio [id], aperto la prima volta che si chiede.
  - `val openSpaces: List<SyncSpace> get()` — Gli spazi aperti.
  - `suspend fun syncAll(): SyncReport` — Sincronizza ogni spazio aperto e ogni spazio che ha qualcosa nel registro (dopo un avvio a freddo, il lavoro in sottofondo non sa quali spazi l'app aprira'). Uno spazio che fallisce non ferma gli altri.
  - `companion object`
    - `fun open(context: Context, relay: SyncRelay, sealer: SyncSealer, config: SyncConfig = SyncConfig()): FluidSync` — Il motore per un'app: il registro su disco in `noBackupFilesDir` ([SqliteSyncStore], fuori dal backup automatico: un registro copiato su un telefono nuovo si crederebbe un altro dispositivo), il nodo dell'installazione ([EngineInstallId]) e l'invio con WorkManager ([FluidSyncWork]).

## `engine-sync/sync/FluidSyncWork.kt`

- `interface FluidSyncHost` — L'app che sincronizza in sottofondo: di solito l'`Application`. Il lavoro di WorkManager parte anche a app chiusa, in un processo nuovo, e chiede qui il motore.
  - `val fluidSync: FluidSync`
- `class FluidSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params)` — Il lavoro in sottofondo: sincronizza tutti gli spazi ([FluidSync.syncAll]). Senza rete o con il relay occupato chiede di riprovare (WorkManager riparte quando torna la rete, con pause crescenti); un rifiuto del relay lo ferma. Se mentre lavorava sono arrivate modifiche nuove, fa un altro giro (al massimo [MaxRounds]).
- `object FluidSyncWork` — L'invio con WorkManager: dopo una modifica parte un lavoro unico ([UniqueName]) con la rete come vincolo e un piccolo ritardo ([DebounceMillis]), cosi' una raffica di modifiche (chi scrive una nota) diventa un invio solo. Un lavoro periodico ([schedulePeriodic]) fa da rete di sicurezza e porta le novita' degli altri dispositivi.
  - `const val UniqueName: String` — Il nome del lavoro unico dopo una modifica.
  - `const val PeriodicName: String` — Il nome del lavoro periodico.
  - `const val DebounceMillis: Long` — L'attesa prima dell'invio: due secondi, a occhio (24/11/2026). Abbastanza per raccogliere le parole di una frase, poco perche' l'altro dispositivo veda la modifica quasi subito.
  - `val constraints: Constraints` — I vincoli: una rete connessa.
  - `fun request(context: Context, delayMillis: Long = DebounceMillis)` — Chiede un invio. Se ce n'e' gia' uno in attesa, resta quello ([ExistingWorkPolicy.KEEP]).
  - `fun schedulePeriodic(context: Context, hours: Long = 6)` — Una sincronizzazione ogni [hours] ore quando c'e' rete (di serie sei).
  - `fun scheduler(context: Context): SyncScheduler` — Lo [SyncScheduler] che chiede un invio a WorkManager: e' quello di [FluidSync.open].

## `engine-sync/sync/MemorySyncRelay.kt`

- `class MemorySyncRelay(private val now: () -> Long = System::currentTimeMillis, val tombstoneRetentionMillis: Long = 90L * 24 * 60 * 60 * 1000, val idempotencyMillis: Long = 24L * 60 * 60 * 1000) : SyncRelay` — La destinazione in memoria: un relay completo dentro il processo, fedele al contratto del relay dell'Account Pampa (`b/api.md` § 4.10, `b/architettura.md` § 5.3): - una riga per elemento, con la versione che cresce di uno a ogni scrittura accettata; - una scrittura passa solo se la riga e' ancora alla `baseVersion` dell'invio, altrimenti torna come conflitto;
  - `fun purge(): Int` — Il lavoro di ogni notte: toglie le pietre tombali piu' vecchie di [tombstoneRetentionMillis]. Risponde quante. Dopo, un dispositivo con un cursore piu' vecchio dell'ultima tolta rilegge tutto.
  - `fun rows(space: SyncSpaceId): List<SyncRemoteRow>` — Le righe di [space] come le tiene il relay, in ordine: per le prove e per la scena che mostra cosa vede il relay.
  - `fun counts(space: SyncSpaceId): Pair<Int, Int>` — Quanti invii ha ricevuto [space] (ripetizioni comprese) e quante scritture ha applicato davvero.

## `engine-sync/sync/SqliteSyncStore.kt`

- `class SqliteSyncStore(context: Context, name: String = "fluid_sync.db") : SyncStore, Closeable` — Il registro su disco, in SQLite della piattaforma (niente Room: nessun processore di annotazioni per l'engine o per l'app). Tre tabelle: gli elementi (stato locale, base e versione), le voci del registro e i dati dell'engine (cursore, invio in volo, orologio). Ogni [transaction] e' una transazione di SQLite: una modifica e la sua voce, o una pagina e il cursore, arrivano su disco insieme o per niente.
  - `val file: File` — Il file del registro.

## `engine-sync/sync/SyncClock.kt`

- `class SyncHlc(val millis: Long, val counter: Int, val node: String) : Comparable<SyncHlc>` — Un istante dell'orologio logico ibrido (HLC: S. Kulkarni e altri, «Logical Physical Clocks and Consistent Snapshots in Globally Distributed Databases», 2014): i millisecondi dell'orologio corretto, un contatore per gli eventi nello stesso millisecondo e il nodo (il dispositivo) che l'ha preso.
  - `fun encode(): String` — La forma testuale, ordinabile come testo nello stesso ordine degli stamp: 12 cifre esadecimali di millisecondi (fino all'anno 10 889), 8 di contatore, poi il nodo. E' l'identificativo di una revisione nel foglio della scelta.
  - `companion object`
    - `val Zero: SyncHlc` — Lo stamp piu' piccolo: per le pietre tombali che il relay ha gia' cancellato e di cui non resta la data.
    - `fun parse(text: String): SyncHlc?` — Il contrario di [encode]; `null` se il testo non e' uno stamp.
    - `fun isValidNode(node: String): Boolean` — Un nodo valido: da 1 a 32 lettere, cifre o trattini bassi.
- `class SyncClock(val node: String, private val wall: () -> Long = System::currentTimeMillis, last: SyncHlc? = null)` — L'orologio logico ibrido di un dispositivo.
  - `var offsetMillis: Long` — La correzione dell'orologio del telefono letta dal relay, in millisecondi (positiva se il telefono e' indietro).
  - `val current: SyncHlc get()` — L'ultimo stamp dato o visto.
  - `fun wallMillis(): Long` — L'ora corretta, senza avanzare l'orologio logico.
  - `fun now(): SyncHlc` — Uno stamp nuovo per una scrittura: piu' grande di tutto quello che questo orologio ha dato o visto.
  - `fun observe(remote: SyncHlc)` — Registra uno stamp arrivato da un altro dispositivo: il prossimo [now] sara' piu' grande.

## `engine-sync/sync/SyncCodec.kt`

- `class SyncFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)` — Un documento, un registro o una coda che non si riesce a leggere: dati rovinati, o scritti da un engine piu' nuovo.
- `object SyncDocumentCodec` — La codifica di un [SyncDocument] in byte: e' quello che il [SyncSealer] sigilla e che viaggia verso il relay.
  - `const val FormatVersion: Int` — La versione del formato che questo engine scrive.
  - `fun encode(document: SyncDocument): ByteArray` — I byte di [document].
  - `fun decode(bytes: ByteArray): SyncDocument` — Il documento di [bytes]; [SyncFormatException] se i byte non sono un documento di questo formato.

## `engine-sync/sync/SyncDocument.kt`

- `sealed class SyncValue` — Il valore di un campo. Ogni tipo si unisce per conto suo: - [Text], [Integer], [Real], [Bool] e [Bytes] sono valori **da ultimo che vince**: fra due modifiche fatte senza vedersi resta quella con lo stamp piu' grande ([SyncHlc]); - [LongText] e' un testo **che si fonde**: due modifiche a passaggi diversi restano tutte e due, e due aggiunte nello stesso punto anche;
  - `data class Text(val text: String) : SyncValue()` — Un testo corto (un titolo, un nome): vince l'ultimo.
  - `data class LongText(val text: String) : SyncValue()` — Un testo lungo (il corpo di una nota): si fonde, e quando non si puo' chiede la scelta.
  - `data class Integer(val value: Long) : SyncValue()` — Un numero intero: vince l'ultimo.
  - `data class Real(val value: Double) : SyncValue()` — Un numero con la virgola: vince l'ultimo.
  - `data class Bool(val value: Boolean) : SyncValue()` — Un si'/no (una voce spuntata): vince l'ultimo.
  - `class Bytes(bytes: ByteArray) : SyncValue()` — Byte qualunque (un'immagine piccola, un valore gia' codificato dall'app): vince l'ultimo.
    - `val bytes: ByteArray get()` — Una copia dei byte.
- `data class SyncAlternative(val stamp: SyncHlc, val value: SyncValue?)` — Una versione di un campo rimasta indietro in un conflitto: il suo stamp (che e' l'identificativo della revisione) e il valore. Resta nel documento, e viaggia cifrata con lui, finche' qualcuno sceglie ([SyncSpace.resolve]).
  - `val revision: String get()` — L'identificativo della revisione: lo stamp in forma di testo.
- `data class SyncField(val value: SyncValue?, val stamp: SyncHlc, val alternatives: List<SyncAlternative> = emptyList())` — Un campo: il valore (nullo se cancellato), lo stamp della scrittura che l'ha messo e le versioni in conflitto ancora da scegliere ([alternatives], di solito nessuna).
  - `val revision: String get()` — L'identificativo della revisione del valore attuale.
  - `val inConflict: Boolean get()` — Vero se il campo aspetta una scelta fra versioni.
- `class SyncDocument(fields: Map<String, SyncField> = emptyMap(), val deleted: SyncHlc? = null)` — Un documento: i campi di un elemento (una nota, una voce di lista, un ricordo) e, se e' stato cancellato, lo stamp della cancellazione ([deleted]).
  - `val fields: Map<String, SyncField>` — I campi, in ordine di nome: due documenti uguali hanno lo stesso ordine e la stessa codifica.
  - `val isDeleted: Boolean get()` — Vero se il documento e' una pietra tombale.
  - `operator fun get(name: String): SyncValue?` — Il valore di [name], o null.
  - `fun text(name: String): String?` — Il testo di [name] se e' [SyncValue.Text] o [SyncValue.LongText], altrimenti null.
  - `fun integer(name: String): Long?` — Il numero intero di [name], o null.
  - `fun real(name: String): Double?` — Il numero con la virgola di [name], o null.
  - `fun bool(name: String): Boolean?` — Il si'/no di [name], o null.
  - `val conflictedFields: List<String> get()` — I nomi dei campi che aspettano una scelta.
  - `val latestStamp: SyncHlc` — Lo stamp piu' grande del documento: l'ultima scrittura che contiene.
  - `companion object`
    - `val Empty: SyncDocument` — Il documento vuoto: un elemento appena nato.
    - `fun tombstone(stamp: SyncHlc): SyncDocument` — La pietra tombale con lo stamp della cancellazione.

## `engine-sync/sync/SyncMerge.kt`

- `object SyncMerge` — L'unione di due versioni di un documento cambiate a partire da una stessa base: quella del dispositivo (le modifiche non ancora mandate) e quella del relay (le modifiche degli altri). E' una funzione pura, senza rete e senza registro.
  - `fun merge(base: SyncDocument?, local: SyncDocument, remote: SyncDocument, now: () -> SyncHlc, maxEdits: Int = FluidTextDiff.DefaultMaxEdits): SyncDocument` — L'unione di [local] e [remote] rispetto a [base] (null per un elemento nato senza base comune). [now] da' lo stamp di un testo fuso, che e' una scrittura nuova; [maxEdits] e' il tetto di lavoro del confronto ([FluidTextDiff]).

## `engine-sync/sync/SyncRelay.kt`

- `value class SyncSpaceId(val value: String)` — Lo spazio di sincronizzazione: i dati di un'app di una persona, oppure uno spazio condiviso fra piu' persone (una lista, un gruppo). Ogni spazio ha il suo registro, il suo cursore e le sue righe sul relay. L'identificativo e' scelto da chi chiama (l'account usa il pacchetto dell'app e la collezione, o l'id dello spazio condiviso) e non deve contenere nomi o titoli: sul relay sta in chiaro.
- `class SyncSealedPayload(bytes: ByteArray, val keyVersion: Int = 0)` — Il contenuto di un elemento come viaggia: byte che il relay non sa leggere, prodotti dal [SyncSealer] di chi chiama. [keyVersion] e' l'epoca della chiave che li ha sigillati (l'Account Pampa ruota la chiave dei dati a epoche); il relay la conserva e la restituisce, senza capirla.
  - `val bytes: ByteArray get()` — Una copia dei byte sigillati.
  - `val size: Int get()` — Quanti byte.
- `class SyncRemoteRow(val recordId: String, val version: Long, val deleted: Boolean, val payload: SyncSealedPayload?, val seq: Long)` — Una riga del relay: l'elemento, la sua versione sul relay, se e' cancellato, il contenuto sigillato e il numero d'ordine.
- `class SyncPushChange(val recordId: String, val baseVersion: Long, val deleted: Boolean, val payload: SyncSealedPayload?)` — Una modifica da mandare: l'elemento, la versione su cui si e' lavorato ([baseVersion], 0 per un elemento nuovo), il contenuto.
- `class SyncPushBatch(val idempotencyKey: String, val changes: List<SyncPushChange>)` — Un invio: le modifiche e la **chiave d'idempotenza**. Il relay ricorda la risposta per quella chiave (l'Account Pampa per 24 ore, `api.md` § 1): rimandare lo stesso invio dopo un errore di rete da' la stessa risposta e non scrive due volte. L'engine salva l'invio nel registro **prima** di mandarlo e lo rimanda identico finche' non ha la risposta.
- `interface SyncRelayTime` — L'ora del relay in una risposta: corregge l'orologio del telefono ([SyncClock.offsetMillis]). 0 se il relay non la dice.
  - `val serverTimeMillis: Long`
- `class SyncAccepted(val recordId: String, val version: Long, val seq: Long)` — Una modifica accettata: la sua versione nuova sul relay e il suo numero d'ordine.
- `class SyncPushResult(val accepted: List<SyncAccepted>, val conflicts: List<SyncRemoteRow>, override val serverTimeMillis: Long = 0) : SyncRelayTime` — La risposta a un invio. Ogni modifica e' accettata (se il relay aveva ancora la sua `baseVersion`) oppure torna in [conflicts] con la riga attuale: l'engine la fonde con la propria e rimanda.
- `sealed class SyncPullResult : SyncRelayTime` — La risposta a una lettura: le righe cambiate dopo il cursore, in ordine, e il cursore dopo l'ultima.
  - `class Rows(val rows: List<SyncRemoteRow>, val next: Long, val hasMore: Boolean, override val serverTimeMillis: Long = 0) : SyncPullResult()` — Una pagina di righe. [hasMore] se ce ne sono altre dopo [next].
  - `class CursorExpired(override val serverTimeMillis: Long = 0) : SyncPullResult()` — Il cursore e' troppo vecchio: il relay ha gia' cancellato pietre tombali piu' recenti (l'Account Pampa le tiene 90 giorni). Il dispositivo e' stato fermo troppo: rilegge tutto da capo, senza perdere le sue modifiche in attesa.
- `sealed class SyncFailure` — Perche' il relay non ha risposto: decide se riprovare e cosa dire nello stato.
  - `data object Offline : SyncFailure()` — Niente rete, o il relay irraggiungibile: si riprova quando torna la rete.
  - `data class RetryLater(val afterMillis: Long) : SyncFailure()` — Il relay ha detto di aspettare (troppe richieste, quota del giorno, manutenzione).
  - `data class Rejected(val code: String, val message: String? = null) : SyncFailure()` — Il relay ha rifiutato (accesso scaduto, app scollegata, spazio pieno): riprovare non basta. [code] e' il suo codice.
- `class SyncRelayException(val failure: SyncFailure, cause: Throwable? = null) :` — Il relay non ha risposto come doveva.
- `interface SyncRelay` — L'adattatore verso una destinazione: il **relay cifrato dell'Account Pampa** (F10.3, rotte `/v1/sync/…` di `api.md` § 4.10) e, un giorno, una cartella dell'utente (Drive, WebDAV, un NAS), che oggi non servono.
  - `suspend fun pull(space: SyncSpaceId, since: Long, limit: Int): SyncPullResult` — Le righe di [space] cambiate dopo [since] (0 = tutte), al massimo [limit].
  - `suspend fun push(space: SyncSpaceId, batch: SyncPushBatch): SyncPushResult` — Manda le modifiche di [batch] a [space]. Lo stesso [SyncPushBatch.idempotencyKey] da' la stessa risposta.
- `class SyncSealContext(val space: SyncSpaceId, val recordId: String, val version: Long)` — Dove e cosa si sigilla: lo spazio, l'elemento e la versione che avra' sul relay. Il [SyncSealer] lo lega ai byte come dati associati ([associatedData]): cosi' il relay non puo' spostare un contenuto su un altro elemento o in un altro spazio, ne' servirlo come una versione diversa, senza che l'apertura fallisca (`b/chiavi.md`, K4: «ogni dato cifrato porta con se' di chi e' e dove sta»).
  - `fun associatedData(): ByteArray` — I dati associati: `fluid-sync/1`, lo spazio, l'elemento e la versione, separati da uno zero.
- `class SyncSealException(message: String, cause: Throwable? = null) : Exception(message, cause)` — Un contenuto che non si apre: chiave sbagliata, byte toccati, oppure spostato su un altro elemento o un'altra versione.
- `interface SyncSealer` — **Il punto d'aggancio della cifratura.** L'engine non ha chiavi e non fa crittografia: prima di mandare un documento lo passa a [seal], e cio' che arriva dal relay lo apre con [open]. Il sigillo vero lo mette l'Account Pampa (F10.4: la chiave dell'app `K_app(p)` per i dati di un'app, la chiave dello spazio condiviso per le liste e i gruppi), con i dati associati di [SyncSealContext].
  - `suspend fun seal(context: SyncSealContext, plaintext: ByteArray): SyncSealedPayload` — Sigilla [plaintext] (la codifica di un documento) legandolo a [context].
  - `suspend fun open(context: SyncSealContext, payload: SyncSealedPayload): ByteArray` — Apre [payload] se e' stato sigillato per [context]; altrimenti [SyncSealException].

## `engine-sync/sync/SyncSpace.kt`

- `class SyncEditor internal constructor()` — Le modifiche di una scrittura ([SyncSpace.edit]): i campi da cambiare o la cancellazione. Tutto quello che si scrive in un blocco prende lo stesso stamp e finisce nel registro in una transazione sola.
  - `fun set(field: String, value: SyncValue?)` — [field] prende [value]; null lo cancella.
  - `fun text(field: String, text: String?)` — Un testo corto: vince l'ultimo.
  - `fun longText(field: String, text: String?)` — Un testo lungo: si fonde con le modifiche degli altri dispositivi.
  - `fun integer(field: String, value: Long?)`
  - `fun real(field: String, value: Double?)`
  - `fun bool(field: String, value: Boolean?)`
  - `fun delete()` — Cancella l'elemento. E' definitivo: vince su ogni modifica arrivata insieme.
- `class SyncVersion(val revision: String, val stamp: SyncHlc, val value: SyncValue?, val current: Boolean)` — Una versione di un campo in conflitto, come la mostra il foglio della scelta.
  - `val node: String get()` — Il dispositivo che l'ha scritta (il nodo del suo orologio): l'app sa dargli un nome («Tablet di Alessio»).
  - `val millis: Long get()` — Quando e' stata scritta, in millisecondi dell'orologio corretto.
  - `val text: String?` — Il testo, se il valore e' un testo.
- `class SyncConflict(val space: SyncSpaceId, val recordId: String, val field: String, val versions: List<SyncVersion>)` — Un campo che aspetta una scelta: le [versions] in ordine (prima quella attuale, poi le altre dalla piu' nuova). Le [revisions] vanno ripassate a [SyncSpace.resolve] come «quelle che ho visto»: se nel frattempo e' arrivata una revisione nuova, la scelta non vale e torna il confronto aggiornato.
  - `val revisions: List<String> get()`
- `sealed class SyncResolution` — Come e' andata una scelta ([SyncSpace.resolve]).
  - `data class Resolved(val copies: List<String>) : SyncResolution()` — Fatto. [copies] sono gli elementi nuovi nati da «tieni tutte e due» (vuota se se n'e' tenuta una sola).
  - `data class Stale(val current: SyncConflict?) : SyncResolution()` — Nel frattempo e' cambiato qualcosa: [current] e' il confronto di adesso (null se il conflitto non c'e' piu').
  - `data object NotFound : SyncResolution()` — L'elemento o il campo non esistono (cancellati).
- `data class SyncSpaceState(val records: Map<String, SyncDocument>, val conflicts: List<SyncConflict>, val pending: Int, val syncing: Boolean, val lastSuccessMillis: Long?, val failure: SyncFailure?, val loading: Boolean = false)` — Lo stato di uno spazio, come lo legge l'interfaccia: gli elementi vivi, i conflitti, le modifiche in attesa e come e' andata l'ultima sincronizzazione.
  - `companion object`
- `class SyncSpaceReport(val space: SyncSpaceId, val pushed: Int, val pulled: Int, val merged: Int, val conflicts: Int, val supersededByDelete: Int, val purged: Int, val fullResync: Boolean, val failure: SyncFailure?)` — Cosa ha fatto una sincronizzazione di uno spazio.
- `class SyncSpace internal constructor(val id: SyncSpaceId, private val engine: FluidSync)` — Uno spazio: i dati di un'app di una persona, oppure uno spazio condiviso. Si apre con [FluidSync.space].
  - `val state: StateFlow<SyncSpaceState>` — Lo stato dello spazio: cambia dopo ogni scrittura, ogni sincronizzazione e ogni scelta.
  - `suspend fun load()` — Legge il registro dal disco, se non l'ha gia' fatto. Le altre chiamate lo fanno da sole.
  - `suspend fun document(recordId: String): SyncDocument?` — Il documento di [recordId] com'e' adesso (anche una pietra tombale), o null.
  - `suspend fun edit(recordId: String, block: SyncEditor.() -> Unit): Boolean` — Scrive in [recordId] le modifiche di [block]. Risponde falso se non c'era niente da scrivere o se l'elemento e' stato cancellato (la cancellazione e' definitiva). Un elemento che non c'e' nasce.
  - `suspend fun create(block: SyncEditor.() -> Unit): String` — Crea un elemento nuovo con un identificativo casuale e ci scrive [block]. Risponde l'identificativo.
  - `suspend fun delete(recordId: String): Boolean` — Cancella [recordId]. Definitivo: vince su ogni modifica arrivata insieme da un altro dispositivo.
  - `suspend fun resolve(recordId: String, field: String, keep: List<String>, seen: Collection<String>): SyncResolution` — Sceglie in un conflitto. [keep] sono le revisioni da tenere: una sola («tieni questa»), oppure piu' d'una («tieni tutte e due»: la prima resta nell'elemento, ogni altra diventa un elemento nuovo, copia di questo con quella versione, come «Mantieni entrambi» di File).
  - `fun conflict(recordId: String, field: String): SyncConflict?` — Il conflitto di [recordId].[field] com'e' adesso, o null.
  - `suspend fun sync(): SyncSpaceReport` — Sincronizza adesso: vedi la descrizione della classe. Non lancia per gli errori di rete: li mette nel resoconto e nello stato.

## `engine-sync/sync/SyncStore.kt`

- `class SyncStoredRecord(val recordId: String, val local: SyncDocument, val base: SyncDocument?, val baseVersion: Long, val dirty: Boolean)` — Un elemento nel registro locale: - [local], lo stato che l'app vede, con le modifiche non ancora confermate; - [base], l'ultima versione confermata dal relay (la base comune dell'unione), nulla per un elemento mai mandato; - [baseVersion], la versione di [base] sul relay (0 se non c'e'); - [dirty], vero se [local] ha qualcosa che il relay non ha ancora.
- `sealed class SyncChange` — Una modifica nel registro: cosa e' cambiato in un elemento, con lo stamp della scrittura.
  - `data class Write(val field: String, val value: SyncValue?) : SyncChange()` — Il campo [field] ha preso [value] (null: cancellato).
  - `data object Delete : SyncChange()` — L'elemento e' stato cancellato.
  - `data class Choose(val field: String, val kept: List<String>) : SyncChange()` — Nel campo [field] in conflitto sono state tenute le revisioni [kept] (la prima resta, le altre diventano copie).
- `data class SyncJournalEntry(val seq: Long, val recordId: String, val stamp: SyncHlc, val change: SyncChange)` — Una voce del registro: il numero d'ordine nello spazio ([seq]), l'elemento, lo stamp e la modifica. Le voci restano finche' il relay non ha confermato l'elemento che le contiene: sono le «modifiche in attesa». Poi il registro si compatta e le toglie.
- `interface SyncStore` — Dove sta il registro. Ogni chiamata dell'engine passa da una [transaction]: o si scrive tutto o niente, cosi' una modifica e la sua voce nel registro, o una pagina di righe e il cursore, non si separano mai, nemmeno se il processo muore a meta'.
  - `fun <T> transaction(block: SyncStoreTransaction.() -> T): T` — Esegue [block] in modo atomico. Un'eccezione dentro [block] annulla tutto quello che [block] ha scritto.
- `interface SyncStoreTransaction` — Le operazioni dentro una transazione del [SyncStore].
  - `fun spaces(): List<String>` — Gli spazi che hanno qualcosa nel registro.
  - `fun record(space: String, recordId: String): SyncStoredRecord?`
  - `fun records(space: String): List<SyncStoredRecord>`
  - `fun putRecord(space: String, record: SyncStoredRecord)`
  - `fun removeRecord(space: String, recordId: String)`
  - `fun appendJournal(space: String, entry: SyncJournalEntry)`
  - `fun journal(space: String): List<SyncJournalEntry>` — Le voci del registro di [space], in ordine di [SyncJournalEntry.seq].
  - `fun journalSize(space: String): Int` — Quante voci ha il registro di [space].
  - `fun journalRecordCount(space: String): Int` — Quanti elementi diversi hanno voci nel registro di [space]: sono le «modifiche in attesa» che vede chi usa l'app. Una voce nuova con tre campi e' una modifica, non tre. Di serie conta da [journal]; i depositi possono farlo meglio.
  - `fun lastJournalSeq(space: String, recordId: String): Long` — Il numero d'ordine piu' alto fra le voci di [recordId], o 0.
  - `fun removeJournal(space: String, recordId: String, throughSeq: Long): Int` — Toglie le voci di [recordId] fino a [throughSeq] compreso; risponde quante ne ha tolte.
  - `fun meta(space: String, key: String): ByteArray?`
  - `fun putMeta(space: String, key: String, value: ByteArray?)` — Scrive (o, con null, toglie) un dato dell'engine per [space].
- `class InMemorySyncStore : SyncStore` — Il registro in memoria: per le prove, le scene e i dispositivi finti. Le transazioni lavorano su una copia e la tengono solo se finiscono, come un database. Lo stesso oggetto passato a due `FluidSync` uno dopo l'altro fa da «disco» fra due avvii del processo.

## `engine-sync/sync/testing/FakeSyncRelay.kt`

- `class FakeSyncRelay(val inner: SyncRelay = MemorySyncRelay()) : SyncRelay` — La destinazione finta per le prove: sta davanti a un relay vero ([MemorySyncRelay] di serie) e sa comportarsi come la rete di tutti i giorni. - [online] falso: ogni chiamata fallisce prima di partire ([SyncFailure.Offline]). - [dropRequests]: le prossime N chiamate si perdono all'andata (il relay non le vede).
  - `val pushes: List<SyncPushBatch> get()` — Ogni invio partito (anche quelli persi al ritorno), nell'ordine.
  - `val payloadBytes: List<ByteArray> get()` — Tutti i byte di contenuto passati da qui, all'andata e al ritorno.
