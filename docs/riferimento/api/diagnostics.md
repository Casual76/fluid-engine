<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/diagnostics.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · engine-diagnostics

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-diagnostics/diagnostics/FluidDiagnostics.kt`

- `enum class FluidDiagnosticsConsent` — La scelta dell'utente sulla diagnostica: chiesta una volta, cambiabile nelle impostazioni.
- `class FluidDiagnosticsConfig(val appName: String, val endpoint: String? = null, val crashes: Boolean = true, val systemExits: Boolean = true, val frames: Boolean = true, val maxRecords: Int = FluidDiagnosticsQueue.DefaultMaxRecords, val maxBytes: Long = FluidDiagnosticsQueue.DefaultMaxBytes, val sendIntervalMillis: Long = 24L * 60 * 60 * 1000)` — Come si comporta la diagnostica di un'app (F8.24).
- `sealed class FluidDiagnosticsSendResult` — Com'è andato un invio.
  - `class Sent(val count: Int) : FluidDiagnosticsSendResult()` — Partiti [count] rapporti, tolti dalla coda.
  - `data object NothingToSend : FluidDiagnosticsSendResult()` — La coda era vuota.
  - `data object NotAllowed : FluidDiagnosticsSendResult()` — Senza il sì dell'utente, o senza indirizzo: niente è partito, e niente parte mai.
  - `class Failed(val status: Int) : FluidDiagnosticsSendResult()` — Il server ha risposto [status] (o non ha risposto: 0). I rapporti restano in coda per la prossima volta.
- `fun interface FluidDiagnosticsTransport` — Come parte un invio: un POST del [body] JSON a [url], che torna il codice HTTP (0 senza risposta). Di serie `HttpURLConnection` senza cookie e senza dati del telefono nelle intestazioni; le prove ne passano uno finto.
  - `fun post(url: String, body: ByteArray): Int`
  - `companion object`
    - `val Http: FluidDiagnosticsTransport` — Il trasporto di serie.
- `class FluidDiagnostics internal constructor(private val context: Context, val config: FluidDiagnosticsConfig, private val preferences: SharedPreferences, val queue: FluidDiagnosticsQueue, internal val source: FluidDiagnosticsSource, private val transport: FluidDiagnosticsTransport, private val clock: () -> Long, private val io: CoroutineDispatcher)` — La diagnostica dal campo di un'app (F8.24), come MetricKit: crash, blocchi, chiusure decise dal sistema e fotogrammi lenti per pagina, col consenso chiesto una volta ([FluidDiagnosticsConsentPrompt]), senza contenuti né identità, in coda sul telefono ([queue]) finché l'utente non dice di sì.
  - `val consent: StateFlow<FluidDiagnosticsConsent>` — La scelta dell'utente.
  - `val isCollecting: Boolean get()` — Vero se i rapporti si raccolgono (prima della scelta e col sì; col no mai).
  - `fun setConsent(granted: Boolean)` — La scelta dell'utente: [granted] vero per il sì, falso per il no. Col no la coda si svuota subito e non si raccoglie più niente; col sì parte un invio (se c'è un indirizzo e qualcosa in coda).
  - `fun askAgain()` — Torna a «non ancora chiesta»: il foglio del consenso si riapre. Per quando cambia cosa si raccoglie (una scelta data per una cosa non vale per un'altra). La coda resta com'è: col no di prima è già vuota.
  - `fun clear()` — Cancella i rapporti in coda e i fotogrammi non ancora passati alla coda («Cancella i dati»).
  - `fun pendingFrames(): List<FluidPageFrames>` — I fotogrammi di oggi non ancora passati alla coda, per pagina (la galleria li mostra dal vivo).
  - `suspend fun send(): FluidDiagnosticsSendResult` — Manda i rapporti in coda adesso, se l'utente ha detto di sì e c'è un indirizzo. A gruppi di [BatchSize]; il server li prende per [FluidDiagnosticsRecord.id], quindi un gruppo mandato due volte conta una volta sola.
  - `companion object`
    - `const val SchemaVersion: Int` — La versione del formato che parte verso il server.
    - `const val BatchSize: Int` — Quanti rapporti al massimo in un invio.
    - `const val BatchBytes: Int` — Quanti byte al massimo in un invio (il server ne accetta 128 KiB).
    - `const val PreferencesName: String` — Il file delle `SharedPreferences` della diagnostica.
    - `fun get(): FluidDiagnostics?` — La diagnostica installata in questo processo, o nulla.
    - `fun install(application: Application, config: FluidDiagnosticsConfig): FluidDiagnostics` — Accende la diagnostica nel processo, una volta: il gestore dei crash, i fotogrammi delle attività e, cinque secondi dopo, la lettura delle chiusure di sistema e l'invio (solo col sì). Una seconda chiamata torna la prima.

## `engine-diagnostics/diagnostics/FluidDiagnosticsLicenses.kt`

- `val FluidDiagnosticsLicenses: List<FluidLicense>` — Da aggiungere a `FluidEngineLicenses` in `fluidLicensesSection(licences = …)` quando l'app include engine-diagnostics.

## `engine-diagnostics/diagnostics/FluidDiagnosticsQueue.kt`

- `class FluidDiagnosticsQueue internal constructor(private val file: File, val maxRecords: Int = DefaultMaxRecords, val maxBytes: Long = DefaultMaxBytes)` — La coda dei rapporti sul telefono (F8.24): un file di righe JSON in `noBackupFilesDir` (fuori dal backup, come i rapporti di MetricKit restano sul dispositivo), con un tetto di righe e di byte. Resta qui finché l'utente non dice di sì e l'invio non riesce; con il no si svuota.
  - `val count: StateFlow<Int>` — Quanti rapporti aspettano (giusto dopo la prima lettura in sottofondo, o dopo [refreshCount]).
  - `fun refreshCount()` — Rilegge il file e aggiorna [count]. Da un filo in sottofondo.
  - `fun records(): List<FluidDiagnosticsRecord>` — Tutti i rapporti in coda, dal più vecchio. Legge il file: da un filo in sottofondo.
  - `fun append(record: FluidDiagnosticsRecord, durable: Boolean = false)` — Aggiunge [record] in fondo. [durable] chiede al disco di scriverlo davvero prima di tornare (il crash: il processo sta per finire). Poi applica il tetto.
  - `fun mergeFrames(source: FluidDiagnosticsSource, now: Long, frames: List<FluidPageFrames>)` — Somma [frames] alla riga della stessa pagina, giorno e versione, o ne apre una nuova con [source].
  - `fun remove(ids: Set<String>)` — Toglie i rapporti con questi [ids] (quelli che il server ha preso).
  - `fun clear()` — Svuota la coda (il no dell'utente, «Cancella i dati»).
  - `companion object`
    - `const val DefaultMaxRecords: Int` — Il tetto di righe di serie: 200 rapporti (a occhio, 26/11/2026: settimane di uso senza rete).
    - `const val DefaultMaxBytes: Long` — Il tetto di byte di serie: 512 KiB (a occhio, 26/11/2026; un crash pesa circa 3 kB, un giorno di pagina 0,5).

## `engine-diagnostics/diagnostics/FluidDiagnosticsRecord.kt`

- `enum class FluidDiagnosticsKind(val wire: String)` — Il tipo di un [FluidDiagnosticsRecord].
  - `companion object`
    - `fun fromWire(value: String): FluidDiagnosticsKind?`
- `class FluidDiagnosticsSource(val appId: String, val appVersion: String, val versionCode: Long, val engineVersion: String, val buildType: String, val manufacturer: String, val model: String, val sdk: Int, val refreshRateHz: Int)` — L'app e il telefono di un rapporto: niente che identifichi la persona. [buildType] è «debug» o «release» (le build dev contano come debug), così il cruscotto tiene da parte le prove.
- `class FluidCrashInfo(val type: String, val causes: List<String>, val frames: List<String>, val mainThread: Boolean, val signature: String)` — Un crash: il tipo dell'eccezione e quelli delle sue cause, le righe del codice (al massimo [FluidCrash.MaxFrames]), il filo (principale o no) e una firma per raggruppare i crash uguali. Mai il messaggio.
- `class FluidExitInfo(val reason: String, val importance: String, val pssKb: Long, val rssKb: Long, val frames: List<String>)` — Una chiusura decisa dal sistema ([reason]: «anr», «memoria», «crash-nativo», …), con l'importanza dell'app in quel momento («primo-piano» o «sottofondo»), la memoria usata in kB e, per un ANR, le righe del filo principale.
- `class FluidPageFrames(val page: String, val day: String, val frames: Long, val slow: Long, val hangs: Long, val frozen: Long, val hitchMillis: Long, val durationMillis: Long, val worstMillis: Long, val buckets: List<Long>)` — I fotogrammi di una pagina in un giorno ([day], `AAAA-MM-GG` in UTC). [slow] sono i fotogrammi lenti di JankStats (più del doppio del tempo di un fotogramma), [hangs] quelli da 250 ms in su (un blocco, come lo conta l'Organizer di Xcode), [frozen] quelli da 700 ms in su (i «fotogrammi bloccati» di Android vitals).
  - `val hitchRatio: Double get()` — Il rapporto dei ritardi di Apple, in millisecondi di ritardo per secondo: sotto 5 va bene, da 5 a 10 si vede, da 10 in su disturba (WWDC20 10077, «Eliminate animation hitches with XCTest»; fiducia alta).
- `object FluidFrameBuckets` — I confini dell'istogramma dei fotogrammi, in millisecondi: l'ultimo secchio prende tutto il resto.
  - `val UpperMillis: List<Double>` — Il limite alto di ogni secchio (escluso). 8,3 e 16,7 ms sono un fotogramma a 120 e a 60 Hz.
  - `val Count: Int` — Quanti secchi.
  - `fun indexOf(nanos: Long): Int` — Il secchio di un fotogramma di [nanos] nanosecondi.
- `class FluidDiagnosticsRecord internal constructor(val id: String, val kind: FluidDiagnosticsKind, val hourMillis: Long, val source: FluidDiagnosticsSource, val crash: FluidCrashInfo? = null, val exit: FluidExitInfo? = null, val frames: FluidPageFrames? = null)` — Un rapporto della diagnostica: uno di [crash], [exit] o [frames], secondo [kind]. [id] è casuale e serve solo al server per non contare due volte lo stesso invio; [hourMillis] è l'ora arrotondata in giù all'ora intera.
  - `fun toJson(): JsonObject` — Il rapporto in JSON, come parte verso il server e come si vede nella pagina «Dati di diagnostica».
  - `fun toJsonString(): String` — Lo stesso rapporto come testo JSON su una riga.
  - `companion object`
    - `fun hourOf(millis: Long): Long` — L'ora intera che contiene [millis].
    - `fun dayOf(millis: Long): String` — Il giorno `AAAA-MM-GG` in UTC che contiene [millis].
    - `fun fromJsonString(line: String): FluidDiagnosticsRecord?` — Un rapporto da una riga JSON di [toJsonString], o nulla se la riga è rotta.
- `object FluidCrash` — Le righe del codice di un'eccezione, senza i messaggi: la parte pura del gestore dei crash.
  - `const val MaxFrames: Int` — Quante righe del codice si tengono dell'eccezione in cima.
  - `const val MaxCauses: Int` — Quante cause si contano (i tipi, senza righe).
  - `fun frame(element: StackTraceElement): String` — Una riga del codice: `pacchetto.Classe.metodo(File.kt:12)`, senza il nome del modulo né del caricatore di classi.
  - `fun describe(error: Throwable, mainThread: Boolean): FluidCrashInfo` — Il crash di [error], sul filo principale o no. Il messaggio non entra mai.
  - `fun signature(type: String, frames: List<String>): String` — La firma di un crash: i primi otto passaggi del codice dell'app (senza quelli di Java, Kotlin e Android) col tipo. Due crash con la stessa firma sono lo stesso difetto, anche con messaggi diversi.

## `engine-diagnostics/diagnostics/FluidDiagnosticsUi.kt`

- `data class FluidDiagnosticsLabels(val consentTitle: (appName: String) -> String = { "Aiuta a migliorare $it" }, val consentMessage: (appName: String) -> String = { "Se $it si chiude da sola o va a scatti, può…, val consentSends: String = "Cosa parte", val sendsCrashes: String = "Quando l'app si chiude da sola o si blocca, e in che punto del codice", val sendsFrames: String = "Quali pagine vanno a scatti", val sendsDevice: String = "Il modello del telefono e le versioni di Andro…, val consentNever: String = "Cosa non parte mai", val neverDetail: String = "Quello che scrivi, le foto e i file, il tuo nome e il tuo account, la posizione, l'indirizzo di rete", val share: String = "Condividi con lo sviluppatore", val dontShare: String = "Non condividere", val settingTitle: String = "Condividi con lo sviluppatore", val settingSubtitle: String = "I crash e i rallentamenti, senza i tuoi dati", val dataTitle: String = "Dati di diagnostica", val dataCount: (Int) -> String = { n -> if (n == 1) "1 rapporto sul telefono" el…, val dataNone: String = "Nessun rapporto sul telefono", val settingFootnote: String = "I rapporti restano sul telefono finché non dic…, val whatHeader: String = "Cosa si raccoglie", val onPhoneHeader: String = "Sul telefono", val emptyTitle: String = "Niente da mandare", val emptyDetail: String = "Quando l'app si chiude da sola o va a scatti, il rapporto compare qui prima di partire.", val sendNow: String = "Invia ora", val sent: (Int) -> String = { n -> if (n == 1) "Partito 1 rapporto" else "P…, val sendFailed: String = "Non è partito: riprova più tardi", val nothingToSend: String = "Non c'è niente da mandare", val clear: String = "Cancella i dati", val clearConfirmTitle: String = "Cancellare i rapporti sul telefono?", val clearConfirm: String = "Cancella", val cancel: String = "Annulla", val close: String = "Chiudi", val kindCrash: String = "L'app si è chiusa", val kindExit: (reason: String) -> String = { reason -> when (reason) { "anr" -> "L'app si…, val kindFrames: (page: String) -> String = { "Fotogrammi di «$it»" }, val framesDetail: (frames: Long, slow: Long, hangs: Long) -> String = { frames, slow, hangs -> "$frames fotogrammi, $…, val notSharedFootnote: String = "Non condividi: niente si raccoglie e niente pa…)` — Le parole della diagnostica. In italiano di serie; [english] è il punto di partenza per un'app tradotta.
  - `companion object`
    - `fun english(): FluidDiagnosticsLabels` — Le stesse parole in inglese.
- `object FluidDiagnosticsTags` — I tag dei test della diagnostica.
  - `const val Consent`
  - `const val Share`
  - `const val DontShare`
  - `const val Toggle`
  - `const val DataRow`
  - `const val DataPage`
  - `const val SendNow`
  - `const val Clear`
  - `const val Empty`
  - `const val Json`
  - `fun record(id: String): String` — La riga del rapporto [id].
- `@Composable fun FluidDiagnosticsConsentPrompt(diagnostics: FluidDiagnostics, modifier: Modifier = Modifier, enabled: Boolean = true, labels: FluidDiagnosticsLabels = FluidDiagnosticsLabels())` — Il foglio del consenso (F8.24): si apre da sé finché la scelta non c'è, **una volta**. «Condividi con lo sviluppatore» è il sì; «Non condividere», e anche chiudere il foglio, è il no: senza un sì esplicito non parte niente (il GDPR chiede un consenso dato, non presunto). Si cambia idea in [FluidDiagnosticsSettingsGroup].
- `@Composable fun FluidDiagnosticsSettingsGroup(diagnostics: FluidDiagnostics, onOpenData: () -> Unit, modifier: Modifier = Modifier, labels: FluidDiagnosticsLabels = FluidDiagnosticsLabels())` — Le righe della diagnostica per una pagina delle impostazioni (di solito «Privacy»): l'interruttore «Condividi con lo sviluppatore» e «Dati di diagnostica», che apre [onOpenData] (la [FluidDiagnosticsDataPage]). Con la nota sotto.
- `@Composable fun FluidDiagnosticsDataPage(diagnostics: FluidDiagnostics, onBack: (() -> Unit)?, modifier: Modifier = Modifier, labels: FluidDiagnosticsLabels = FluidDiagnosticsLabels())` — La pagina «Dati di diagnostica» (F8.24): cosa si raccoglie, in parole semplici, e i rapporti che sono sul telefono adesso, ognuno col JSON che partirebbe (un tocco sulla riga). «Invia ora» col sì e un indirizzo; «Cancella i dati» con la conferma.

## `engine-diagnostics/diagnostics/FluidFrames.kt`

- `@Composable fun FluidDiagnosticsPage(name: String)` — Segna la pagina [name] per i fotogrammi della diagnostica (F8.24): finché questa chiamata è nella composizione, i fotogrammi della finestra contano per [name]. Si mette in cima a ogni pagina (`FluidDiagnosticsPage("impostazioni")`); quando due pagine sono insieme (il passaggio fra due pagine) vale l'ultima entrata.

## `engine-diagnostics/diagnostics/FluidLog.kt`

- `enum class FluidLogLevel(val mark: Char)` — Quanto è grave un evento del registro.
- `class FluidLogEvent(val timeMillis: Long, val level: FluidLogLevel, val tag: String, val message: String)` — Un evento del registro: l'ora, il livello, il nome del pezzo dell'app ([tag]) e la frase.
- `object FluidLog` — Il registro circolare dell'app: gli ultimi [Capacity] eventi in memoria. Si scrive da qualunque filo, costa una riga in un array (nessun disco, nessuna rete), e si legge solo per una segnalazione.
  - `const val Capacity: Int` — Quanti eventi si tengono (a occhio, 26/11/2026: qualche minuto di uso normale).
  - `const val MaxMessageLength: Int` — La lunghezza massima di una frase: il resto si taglia.
  - `fun event(tag: String, message: String)` — Un evento normale.
  - `fun warn(tag: String, message: String)` — Qualcosa che non va, ma l'app continua.
  - `fun error(tag: String, message: String)` — Un errore. Solo la frase: mai il messaggio di un'eccezione, che può contenere i dati.
  - `fun add(level: FluidLogLevel, tag: String, message: String)` — Aggiunge un evento di livello [level].
  - `fun snapshot(): List<FluidLogEvent>` — Gli eventi, dal più vecchio, **così come sono stati scritti**: solo per l'app, mai per uscire dal telefono.
  - `fun export(limit: Int = Capacity, zone: ZoneId = ZoneId.systemDefault()): List<String>` — Le ultime [limit] righe pronte per uscire: l'ora, il livello, il pezzo e la frase **coi dati personali tolti** ([FluidPrivacyScrubber.scrub]). È quello che una segnalazione mostra e allega.
  - `fun clear()` — Svuota il registro.
- `object FluidPrivacyScrubber` — Il filtro dei dati personali (F8.24): toglie da un testo quello che non deve uscire dal telefono, e lascia il resto leggibile. Mette al posto di ogni dato un segno fra «»: «[email]», «[telefono]», «[chiave]»…
  - `fun scrub(text: String): String` — [text] coi dati personali tolti.

## `engine-diagnostics/diagnostics/FluidTrace.kt`

- `class FluidTraceName(val value: String)` — Il nome di un intervallo: scelto dal codice, mai dai dati. Solo lettere minuscole, cifre, `.`, `_`, `:` e `-`, da 1 a 64 caratteri: un nome di file o un indirizzo (che possono contenere dati privati, rischio della scheda) non passa. Si dichiara una volta come costante: `val ApriAlbum = FluidTraceName("gallery:apri-album")`.
- `object FluidTraceNames` — I nomi comuni degli intervalli, uguali in tutte le app Pampa: in Perfetto si cercano allo stesso modo. Ogni app ne aggiunge di suoi con lo stesso stile (`app:cosa`).
  - `val FirstScreen: FluidTraceName` — Dal tocco dell'icona al primo schermo pronto da usare.
  - `val ReadFile: FluidTraceName` — Leggere un file dal disco (o da un Uri).
  - `val WriteFile: FluidTraceName` — Scrivere un file.
  - `val Network: FluidTraceName` — Una richiesta di rete, dalla partenza all'ultima riga letta.
  - `val Database: FluidTraceName` — Una lettura o scrittura del database.
  - `val Decode: FluidTraceName` — Decodificare un'immagine o un documento.
  - `val OpenItem: FluidTraceName` — Dal tocco su un elemento (un album, una nota) al suo contenuto pronto: attraversa I/O e fili.
  - `val Sync: FluidTraceName` — La sincronizzazione, dall'inizio alla fine.
- `class FluidTraceInterval internal constructor(private val name: FluidTraceName, private val cookie: Int)` — Un intervallo aperto con [FluidTrace.begin], da chiudere con [end]: per ciò che comincia in un posto e finisce in un altro (il tocco di un album e la prima miniatura disegnata). [end] chiude una volta sola; le altre non fanno niente.
  - `fun end()` — Chiude l'intervallo.
- `object FluidTrace` — Le tracce dei tempi dell'engine (F8.24): intervalli col nome che si vedono in Perfetto, **solo nelle build di debug e dev**. Nella release ogni chiamata è vuota (vedi la nota in cima al file) e il blocco gira com'è.
  - `val isAvailable: Boolean get()` — Vero se questa build scrive le tracce (debug e dev). Falso, sempre, nella release.
  - `val isRecording: Boolean get()` — Vero se in questo momento qualcuno registra (Perfetto o `atrace`): per non preparare niente quando nessuno guarda.
  - `inline fun <T> section(name: FluidTraceName, block: () -> T): T` — [block] in un intervallo [name] sullo stesso filo. Si chiude anche se [block] lancia.
  - `suspend inline fun <T> async(name: FluidTraceName, crossinline block: suspend () -> T): T` — [block] in un intervallo [name] che può cambiare filo (una sospensione, un `withContext(Dispatchers.IO)`): in Perfetto è una riga a sé. Si chiude anche se [block] lancia o viene annullato.
  - `fun begin(name: FluidTraceName): FluidTraceInterval` — Apre un intervallo [name] da chiudere altrove con [FluidTraceInterval.end].
  - `fun counter(name: FluidTraceName, value: Long)` — Un contatore con nome ([name]) che vale [value] da adesso: le miniature in coda, i byte letti.

## `engine-diagnostics/diagnostics/testing/FluidDiagnosticsTesting.kt`

- `object FluidDiagnosticsTesting` — Per le prove delle app (e della galleria) con engine-diagnostics (F8.24): una diagnostica che non si installa nel processo, col trasporto e l'orologio finti e il lavoro sul filo di chi chiama, e i crash e i fotogrammi messi a mano. Non serve in un'app vera: lì c'è [FluidDiagnostics.install].
  - `fun create(context: Context, config: FluidDiagnosticsConfig, transport: FluidDiagnosticsTransport = FluidDiagnosticsTransport { _, _ -> 200 }, clock: () -> Long = System::currentTimeMillis, directory: File = File(context.cacheDir, "fluid-diagnostics-prova…): FluidDiagnostics` — Una diagnostica nuova sulla cartella [directory] (svuotata) e sulle `SharedPreferences` dell'app (svuotate): come il primo avvio. Niente gestore dei crash, niente JankStats.
  - `fun recordCrash(diagnostics: FluidDiagnostics, error: Throwable, mainThread: Boolean = true)` — Scrive nella coda il crash di [error], come farebbe il gestore dei crash.
  - `fun frame(diagnostics: FluidDiagnostics, page: String, durationMillis: Long, overrunMillis: Long, isJank: Boolean)` — Conta un fotogramma della pagina [page], come JankStats.
  - `fun flushFrames(diagnostics: FluidDiagnostics)` — Passa i fotogrammi contati alla coda, come quando l'app esce dallo schermo.
  - `fun reset()` — Dimentica la diagnostica installata nel processo: la prova dopo parte come un processo nuovo.
