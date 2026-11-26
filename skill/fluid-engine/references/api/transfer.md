# API · engine-transfer

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-transfer/transfer/FluidTransferRequest.kt`

- `data class FluidTransferRequest(val id: String, val direction: FluidTransferDirection, val url: String, val file: File, val title: String, val subtitle: String? = null, val expectedBytes: Long = -1L, val sha256: String? = null, val wifiOnly: Boolean = false, val chargingOnly: Boolean = false, val work: FluidWork = if (direction == FluidTransferDirection.Upload)…, val tag: String? = null, val mimeType: String? = null, val method: String = if (direction == FluidTransferDirection.Upload)…, val resumableUpload: Boolean = false)` — Un trasferimento da mettere in coda ([FluidTransfers.enqueue]). L'app dice cosa, dove e con quali vincoli; le credenziali non si salvano: le da' [FluidTransferAuthorizer] a ogni tentativo.
  - `companion object`
    - `fun download(url: String, target: File, title: String, id: String = UUID.randomUUID().toString(), subtitle: String? = null, expectedBytes: Long = -1L, sha256: String? = null, wifiOnly: Boolean = false, chargingOnly: Boolean = false, work: FluidWork = FluidWork.LargeDownload, tag: String? = null): FluidTransferRequest` — Uno scaricamento da [url] in [target] (scritto prima in `<target>.part`, rinominato alla fine).
    - `fun upload(url: String, source: File, title: String, id: String = UUID.randomUUID().toString(), subtitle: String? = null, mimeType: String? = null, method: String = "PUT", resumable: Boolean = false, wifiOnly: Boolean = false, chargingOnly: Boolean = false, work: FluidWork = FluidWork.LargeUpload, tag: String? = null): FluidTransferRequest` — Un caricamento di [source] verso [url]. Con [resumable] il server riprende a pezzi (`Content-Range`).
- `data class FluidTransferRecord(val request: FluidTransferRequest, val state: FluidTransferState = FluidTransferState.Queued, val bytesDone: Long = 0L, val bytesTotal: Long = request.expectedBytes, val validator: String? = null, val resumable: Boolean = true, val failure: FluidTransferFailure? = null, val waitingFor: List<FluidConditionReason> = emptyList(), val attempts: Int = 0, val forced: Boolean = false, val createdAtMillis: Long = 0L, val updatedAtMillis: Long = 0L, val finishedAtMillis: Long? = null, val uploadAcked: Long = 0L)` — Un trasferimento come sta nella coda salvata: la richiesta e dove e' arrivato. E' la forma su disco ([FluidTransferStore]); l'interfaccia legge [toItem].
  - `val id: String get()`
  - `val needs: FluidWorkNeeds get()` — I vincoli del trasferimento per la regola delle condizioni.
  - `fun toItem(bytesPerSecond: Long = 0L, remainingMillis: Long? = null): FluidTransferItem` — Il trasferimento come si mostra. [bytesPerSecond] e [remainingMillis] li sa solo chi lo sta facendo.

## `engine-transfer/transfer/FluidTransferStore.kt`

- `interface FluidTransferStore` — Dove sta la coda: un trasferimento per riga. Si legge una volta all'avvio ([load]) e si scrive a ogni cambio che conta (stato, e l'avanzamento ogni qualche secondo). Lo usa solo il dispatcher della coda, mai il thread principale.
  - `fun load(): List<FluidTransferRecord>`
  - `fun save(record: FluidTransferRecord)`
  - `fun delete(id: String)`
- `class InMemoryFluidTransferStore : FluidTransferStore` — La coda in memoria: per le prove e per le scene. Sopravvive a un `FluidTransfers` nuovo, non al processo.
- `class SqliteFluidTransferStore(context: Context, private val name: String = "fluid_transfers.db") : FluidTransferStore, Closeable` — La coda su disco, in SQLite della piattaforma (come il registro di `engine-sync`: niente Room e niente processore di annotazioni nell'app). Sta in `noBackupFilesDir`: una coda copiata su un telefono nuovo punterebbe a file che li' non ci sono.
  - `val file: File by lazy` — Il file del database. Si chiede la prima volta che serve, fuori dal thread principale: crea la cartella.

## `engine-transfer/transfer/FluidTransferWork.kt`

- `interface FluidTransferHost` — L'app che tiene la coda nella sua `Application`: il lavoro di WorkManager parte anche a app chiusa, in un processo nuovo, e chiede qui la coda. Serve a un'app con credenziali ([FluidTransferAuthorizer]) o una notifica sua; senza, il lavoro usa [FluidTransfers.open] con le opzioni di serie.
  - `val fluidTransfers: FluidTransfers`
- `class FluidTransferWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params)` — Il lavoro in sottofondo: fa girare la coda ([FluidTransfers.runQueue]) **col servizio in primo piano di tipo `dataSync`**, cosi' un trasferimento lungo continua con l'app chiusa e il sistema lo sa (la notifica e' quella di [FluidTransfers.notifications]). Un errore che passa chiede di riprovare (pause crescenti di WorkManager);
- `object FluidTransferWork` — La coda con WorkManager: un lavoro unico ([UniqueName]) coi vincoli del [FluidTransferPlan] (una rete connessa, o una che non costa; la carica; un'attesa), e le pause crescenti di WorkManager dopo un errore che passa.
  - `const val UniqueName: String` — Il nome del lavoro unico.
  - `fun schedule(context: Context, plan: FluidTransferPlan)` — Fa girare la coda secondo [plan]. Un piano [FluidTransferPlan.idle] toglie il giro rimasto in attesa.
  - `fun scheduler(context: Context): FluidTransferScheduler` — Lo [FluidTransferScheduler] che chiede i giri a WorkManager: e' quello di [FluidTransfers.open].
- `interface FluidTransferNotifications` — **Il punto d'aggancio della notifica** dei trasferimenti: la notifica del servizio in primo piano mentre la coda gira. Di serie [SimpleTransferNotifications], una notifica semplice con l'avanzamento. `engine-notify` (F8.19) mette la sua, dallo stesso modello ([FluidTransferOverview]): la pillola dentro l'app, la notifica viva fuori.
  - `fun foregroundInfo(context: Context, overview: FluidTransferOverview): ForegroundInfo` — La notifica del servizio in primo piano per [overview], col tipo `dataSync`.
  - `fun finished(context: Context, overview: FluidTransferOverview)` — Il giro della coda e' finito e non resta niente in corso ([overview] dice com'e' andata: completati, non riusciti, in attesa). La notifica del servizio se ne va col servizio; qui chi ha una notifica viva lascia l'esito (`engine-notify`: `FluidLiveUpdateSink.finish`). Di serie non fa niente.
- `open class SimpleTransferNotifications(private val phrases: TransferNotificationPhrases = TransferNotificationPhrases()) : FluidTransferNotifications` — La notifica semplice dei trasferimenti, con `androidx.core`: un canale silenzioso «Trasferimenti», il titolo di quello in corso, «12,4 MB di 48 MB · ancora 17 s» e la barra. Il tocco apre l'app. Niente stile personalizzato: e' la notifica di un servizio, non una notifica viva (quella e' di `engine-notify`).
  - `open fun build(context: Context, overview: FluidTransferOverview): Notification` — La notifica: si puo' cambiare in una sottoclasse.
  - `companion object`
    - `const val ChannelId: String` — Il canale delle notifiche dei trasferimenti.
    - `const val NotificationId: Int` — L'id della notifica del servizio in primo piano.
- `data class TransferNotificationPhrases(val title: String = "Trasferimenti", val channel: String = "Trasferimenti", val channelDescription: String = "Gli scaricamenti e i caricamenti in corso", val of: String = "di", val remaining: (String) -> String = { "ancora $it" }, val andOthers: (Int) -> String = { n -> if (n == 1) "e un altro" else "e altri $…, val conditions: FluidConditionPhrases = FluidConditionPhrases(), val pause: String = "Pausa", val cancel: String = "Annulla", val doneOne: (title: String, upload: Boolean) -> String = { title, upload -> if (upload) "$title caricato…, val doneMany: (Int) -> String = { "$it trasferimenti completati" }, val failedOne: (String) -> String = { "$it non riuscito" })` — Le parole della notifica semplice, di serie italiane.
  - `fun line(done: Long, total: Long, remainingMillis: Long?): String` — «12,4 MB di 48 MB · ancora 17 s».
  - `companion object`
    - `fun english(): TransferNotificationPhrases`

## `engine-transfer/transfer/FluidTransfers.kt`

- `data class FluidTransferConfig(val maxAutoRetries: Int = 3, val connectTimeoutMillis: Int = 15_000, val readTimeoutMillis: Int = 30_000, val userAgent: String = EngineHttp.DefaultUserAgent, val progressIntervalMillis: Long = 250L, val persistIntervalMillis: Long = 2_000L, val conditionsCheckMillis: Long = 1_000L, val bufferBytes: Int = 64 * 1024, val uploadChunkBytes: Int = 2 * 1024 * 1024, val keepFinished: Int = 50, val recheckMillis: Long = 15L * 60L * 1000L)` — Le regole di lavoro della coda. I tempi sono a occhio (25 novembre 2026) dove non e' scritto altro.
- `fun interface FluidTransferAuthorizer` — Le credenziali di un trasferimento, chieste all'app a ogni tentativo (non si salvano).
  - `suspend fun headers(request: FluidTransferRequest): Map<String, String>`
  - `companion object`
    - `val None: FluidTransferAuthorizer`
- `class FluidTransferPlan internal constructor(val idle: Boolean, val runNow: Boolean, val unmetered: Boolean, val charging: Boolean, val delayMillis: Long, val replace: Boolean)` — Quando deve girare la coda, nei termini di WorkManager: subito ([runNow]) o quando c'e' una rete che non costa ([unmetered]), quando il telefono e' in carica ([charging]), dopo [delayMillis]. [replace] dice se sostituire un giro gia' in attesa (i vincoli sono cambiati) o accodarsi a quello in corso. [idle]: niente da fare.
  - `fun later(millis: Long): FluidTransferPlan` — Lo stesso piano, non prima di [millis].
- `fun interface FluidTransferScheduler` — Chi fa girare la coda. Con WorkManager: [FluidTransferWork.scheduler]. Riceve anche il piano [FluidTransferPlan.idle] quando la coda resta senza niente da fare (e nessun giro sta girando): e' il momento di togliere il giro in attesa.
  - `fun schedule(plan: FluidTransferPlan)`
  - `companion object`
    - `val None: FluidTransferScheduler` — Nessuno: chi usa la coda chiama [FluidTransfers.runQueue] (le prove, le scene senza sottofondo).
- `class FluidTransferRunResult internal constructor(val started: Int, val completed: Int, val retryLater: Boolean, val waiting: Int)` — Cosa ha fatto un giro della coda ([FluidTransfers.runQueue]).
- `class FluidTransfers(private val store: FluidTransferStore, private val scheduler: FluidTransferScheduler = FluidTransferScheduler.None, private val conditions: () -> FluidConditions = { FluidDeviceConditions.current }, private val authorizer: FluidTransferAuthorizer = FluidTransferAuthorizer.None, val config: FluidTransferConfig = FluidTransferConfig(), private val wall: () -> Long = System::currentTimeMillis, private val ticks: () -> Long = { System.nanoTime() / 1_000_000L }, private val dispatcher: CoroutineDispatcher = Dispatchers.IO, conditionChanges: Flow<FluidConditions>? = FluidDeviceConditions.state) : FluidTransferControls` — **Il centro trasferimenti** (F8.20, `centro-trasferimenti`): una coda sola e persistita per gli scaricamenti e i caricamenti lunghi. Va avanti con l'app chiusa (WorkManager, col servizio in primo piano di tipo `dataSync`) e riprende dopo un riavvio; si mette in pausa e riprende davvero (`Range` e `If-Range`);
  - `val items: StateFlow<List<FluidTransferItem>>` — I trasferimenti, nell'ordine in cui sono entrati in coda.
  - `val overview: StateFlow<FluidTransferOverview>` — Tutti insieme: per la pillola e per la notifica.
  - `val isLoaded: StateFlow<Boolean>` — Vero quando la coda salvata e' stata letta.
  - `fun enqueue(request: FluidTransferRequest): String` — Mette in coda [request] e restituisce il suo id. Un id gia' in coda non si duplica: torna quello.
  - `fun remove(id: String)` — Toglie [id] dalla lista: annulla se e' in giro (e cancella il file a meta').
  - `fun item(id: String): FluidTransferItem?` — Il trasferimento [id] adesso, o null.
  - `suspend fun settle()` — Aspetta che i comandi gia' chiesti siano arrivati alla coda (per le prove e per chi deve leggere subito dopo).
  - `fun close()` — Ferma tutto (per le prove): i trasferimenti in corso si fermano dove sono. Se era la coda dell'app, il prossimo [open] ne apre una nuova (dalla stessa coda salvata) invece di restituire questa, che non lavora piu'.
  - `suspend fun runQueue(onProgress: suspend (FluidTransferOverview) -> Unit = {}): FluidTransferRunResult` — Fa girare la coda: uno alla volta, nell'ordine d'arrivo, ogni trasferimento che puo' partire; quelli che non possono restano in attesa col loro perche'. Torna quando non c'e' piu' niente da fare adesso. Un giro alla volta.
  - `fun plan(replace: Boolean = !runMutex.isLocked): FluidTransferPlan` — Il piano per il prossimo giro, dato lo stato della coda (lo chiama il lavoro di WorkManager alla fine del suo giro).
  - `val id: String,`
  - `val control: TransferControl,`
  - `val rate: TransferRate,`
  - `val onProgress: suspend (FluidTransferOverview) -> Unit,`
  - `companion object`
    - `fun open(context: Context, authorizer: FluidTransferAuthorizer = FluidTransferAuthorizer.None, config: FluidTransferConfig = FluidTransferConfig(), notifications: FluidTransferNotifications? = null): FluidTransfers` — La coda dell'app, una per processo: su disco ([SqliteFluidTransferStore]), con WorkManager ([FluidTransferWork.scheduler]) e le condizioni del telefono lette da `engine-net` ([EngineConditions.install]). La prima chiamata decide le opzioni; le altre restituiscono la stessa coda.
    - `fun from(context: Context): FluidTransfers` — La coda per il lavoro in sottofondo: quella dell'`Application` se e' un [FluidTransferHost], se no [open].
    - `fun partFile(request: FluidTransferRequest): File` — Il file a meta' dello scaricamento [request]: accanto al file, col nome del file e un'impronta del suo id (`notes.apk.3f2a9c41d07b.part`). Ogni trasferimento ha il suo: due richieste verso lo stesso file non si cuciono.

## `engine-transfer/transfer/LiveTransferNotifications.kt`

- `class LiveTransferNotifications(private val live: FluidLiveUpdateSink, private val foreground: (FluidLiveUpdate, Int) -> Notification, private val phrases: TransferNotificationPhrases = TransferNotificationPhrases(), private val open: String? = null, private val controls: (Context) -> FluidTransferControls = { FluidTransfers.from(it) }) : FluidTransferNotifications` — **La notifica viva dei trasferimenti** (F8.20b): il punto d'aggancio [FluidTransferNotifications] riempito con l'attività viva di `engine-notify` (F8.19). Il lavoro di WorkManager, che non ha interfaccia, mostra il trasferimento come `FluidLiveKind.Transfer`: chiesto dall'utente, quindi su Android 16 diventa un **Live Update** (il gettone nella barra di stato, la barra, la schermata di blocco), prima una notifica con…
  - `companion object`
    - `const val UpdateId: String` — L'id dell'attività viva dei trasferimenti: lo stesso della pillola (`FluidTransferActivityId` di engine-ui).
    - `const val NotificationId: Int` — L'id della notifica del servizio in primo piano, lo stesso della notifica semplice.
- `fun FluidTransferOverview.toLiveUpdate(controls: FluidTransferControls, phrases: TransferNotificationPhrases = TransferNotificationPhrases(), open: String? = null): FluidLiveUpdate` — La coda come attività viva: il trasferimento in corso (o «Pampa Notes 2.4 e altri 2»), «12,4 MB di 48 MB · ancora 17 s» o il perche' dell'attesa, l'avanzamento di tutto quello che e' in giro, «Pausa» e «Annulla» su quello in corso. Pura.
- `fun fluidTransferOutcome(overview: FluidTransferOverview, sinceMillis: Long, phrases: TransferNotificationPhrases = TransferNotificationPhrases()): FluidLiveOutcome` — L'esito di un giro della coda per l'attività viva: non riuscito se uno finito da [sinceMillis] non e' riuscito (col suo nome e il motivo), riuscito se almeno uno e' arrivato, in silenzio se non e' finito niente (aspetta o e' in pausa: lo dice la pagina). Pura.
