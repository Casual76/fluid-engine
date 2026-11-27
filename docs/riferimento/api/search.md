<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/search.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · engine-search

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-search/search/FluidPampaSearch.kt`

- `class FluidPampaSearch(private val calls: BridgeCalls, private val discover: suspend () -> List<FluidPampaApp>, private val embedder: FluidTextEmbedder? = null, val timeoutMillis: Long = DefaultTimeoutMillis, val language: String = "it")` — «Cerca in Pampa» (F8.22, `ricerca-in-tutte-le-app`): la domanda va insieme a tutte le app Pampa del telefono che la sanno ricevere (il tool [FluidSearchBridge.ToolName] sul ponte firmato), e i risultati tornano **per app**, come i gruppi di Spotlight. Con un [embedder] il vettore della domanda si calcola **una volta** e va a tutte le app, che confrontano i loro (stesso spazio: MyCloud Gallery compresa).
  - `suspend fun apps(): List<FluidPampaApp>` — Le app di adesso (tenute un minuto: la scoperta chiede il catalogo a ogni app).
  - `suspend fun refresh()` — Dimentica le app trovate: la prossima domanda le cerca di nuovo (un'app installata adesso).
  - `fun search(query: String, kinds: Set<String> = emptySet(), limit: Int = DefaultLimit): Flow<FluidPampaResults>` — Cerca [query] in tutte le app. Il primo valore ha tutte le app in attesa; poi un valore nuovo a ogni app che risponde, fino all'ultima. I gruppi restano nell'ordine delle app: niente salti mentre si legge.
  - `companion object`
    - `const val DefaultTimeoutMillis: Long` — Quanto si aspetta un'app: 2,5 s (a occhio, 25/11/2026: un'app chiusa si avvia in 0,4–1,2 s sul Tab S9, poi cerca in ms).
    - `const val DefaultLimit: Int` — Quanti risultati per app: come Spotlight, che ne mostra pochi per app e «Mostra altro».
    - `fun create(context: Context, embedder: FluidTextEmbedder? = null, language: String = "it"): FluidPampaSearch` — «Cerca in Pampa» con le app del telefono trovate col ponte, nella lingua [language] di chi cerca.
    - `suspend fun discoverApps(client: AiToolClient): List<FluidPampaApp>` — Le app del telefono che rispondono a «Cerca in Pampa»: quelle col ponte (`AiToolClient.discover`) che hanno il tool della ricerca nel catalogo. Il catalogo stabilisce anche il protocollo 2 (i risultati tipizzati). Un'app con un'altra firma non risponde e resta fuori. In ordine di nome.
    - `fun open(context: Context, hit: FluidPampaHit): Boolean` — Apre il risultato nella sua app (il suo indirizzo, solo in quell'app). Falso se l'app non sa aprirlo.
    - `fun launch(context: Context, app: FluidPampaApp): Boolean` — Apre l'app (per sbloccarla, o quando un risultato non ha indirizzo).
- `data class FluidPampaSearchLabels(val prompt: String = "Cerca in Pampa", val searchedIn: String = "Cercato sul telefono, in %d app", val searching: String = "Cerco in %d app…", val locked: String = "%s è bloccata", val unlock: String = "Aprila per vedere i risultati", val noAnswer: String = "Non hanno risposto: %s", val none: String = "Nessun risultato per «%s» nelle app Pampa", val byMeaning: String = "per significato")` — Le parole di «Cerca in Pampa». **In italiano di serie.**
- `class FluidPampaSearchState internal constructor()` — Lo stato di «Cerca in Pampa» in una pagina: il testo, il campo aperto e i risultati per app.
  - `var query: String by mutableStateOf("")`
  - `var active: Boolean by mutableStateOf(false)`
  - `var results: FluidPampaResults by mutableStateOf(FluidPampaResults.Empty)` — I risultati di adesso, per app.
  - `fun spec(labels: FluidPampaSearchLabels = FluidPampaSearchLabels(), placement: FluidSearchPlacement = FluidSearchPlacement.Automatic): FluidSearchSpec` — Il campo «Cerca in Pampa».
- `@Composable fun rememberFluidPampaSearchState(search: FluidPampaSearch?): FluidPampaSearchState` — Lo stato di «Cerca in Pampa» con [search] (null: ancora da creare).
- `fun LazyListScope.fluidPampaResults(state: FluidPampaSearchState, onOpen: (FluidPampaHit) -> Unit, onOpenApp: (FluidPampaApp) -> Unit = {}, appIcon: (FluidPampaApp) -> (@Composable () -> Unit)? = { null }, labels: FluidPampaSearchLabels = FluidPampaSearchLabels())` — I risultati di «Cerca in Pampa» in una pagina: sopra «Cercato sul telefono, in 5 app» (o «Cerco in 2 app…»), poi un gruppo per app con l'intestazione «Pampa Mail · 2» e una riga per risultato (titolo e pezzo con le parole trovate, il tipo e «per significato» sotto). Un'app bloccata ha la sua riga che la apre. I gruppi arrivano quando arrivano le risposte, sfocati (lo fa l'app, non il dito);

## `engine-search/search/FluidSearchBridge.kt`

- `class FluidSearchExposure(val kinds: Set<String>? = emptySet(), val kindLabels: Map<String, String> = emptyMap(), val snippets: Boolean = true, val locked: () -> Boolean = { false }, val maxHits: Int = 20, val embedder: FluidTextEmbedder? = null)` — Cosa un'app mostra di se' alle altre app Pampa quando cercano.
- `object FluidSearchBridge` — I tool della ricerca fra app, per il registry dell'app (quello che il suo `AiToolHostProvider` espone).
  - `const val ActionSearch: String` — L'azione dell'intent-filter che chi risponde mette sul suo provider del ponte: rende l'app visibile a chi cerca (Android 11+, i `<queries>` del manifest del modulo). Il provider resta quello di `AiToolHostProvider`.
  - `const val ToolName: String` — Il nome del tool della ricerca: e' cosi' che «Cerca in Pampa» lo riconosce in ogni app.
  - `const val EmbedToolName: String` — Il nome del tool dei vettori: l'app che tiene il modello lo presta alle altre ([remoteEmbedder]).
  - `const val MaxEmbedTexts: Int` — Quanti testi al piu' per una chiamata a [EmbedToolName] (768 float a testo: 32 stanno ben sotto il mega di Binder). Il tool lo impone (R3): una chiamata con piu' testi risponde con un errore, senza calcolarne nessuno.
  - `val Group: AiToolGroup` — Il gruppo dei due tool, se l'app non ne ha uno suo.
  - `fun <C> searchTool(index: FluidSearchIndex, exposure: FluidSearchExposure = FluidSearchExposure(), group: AiToolGroup = Group): AiTool<C>` — Il tool della ricerca nell'[index], per gli assistenti e per «Cerca in Pampa». Legge e basta (niente conferma). I campi riservati non si cercano e non escono mai ([FluidSearchQuery.includeSensitive] falso). Mostra solo i tipi elencati in [FluidSearchExposure.kinds]: con l'esposizione di serie non trova niente (R3).
  - `fun <C> embedTool(embedder: FluidTextEmbedder, group: AiToolGroup = Group): AiTool<C>` — Il tool dei vettori: l'app che ha il modello del significato lo presta alle altre app firmate come lei.
  - `suspend fun remoteEmbedder(calls: BridgeCalls, authority: String): FluidTextEmbedder?` — Il modello del significato di un'altra app ([authority], col suo [EmbedToolName]): un'app senza il modello (157 MB) chiede i vettori a quella che lo tiene. Restituisce null se l'app non risponde o non lo presta.
- `data class FluidPampaApp(val packageName: String, val authority: String, val label: String)` — Un'app Pampa che risponde a «Cerca in Pampa»: dove sta il suo ospite del ponte e come si chiama.
- `data class FluidPampaHit(val app: FluidPampaApp, val id: String, val kind: String, val kindLabel: String?, val title: FluidSearchSnippet, val snippet: FluidSearchSnippet?, val timestamp: Long?, val link: String?, val byMeaning: Boolean, val byWords: Boolean)` — Un risultato di un'altra app.
- `data class FluidPampaGroup(val app: FluidPampaApp, val hits: List<FluidPampaHit> = emptyList(), val total: Int = 0, val pending: Boolean = true, val locked: Boolean = false, val error: String? = null)` — I risultati di un'app.
- `data class FluidPampaResults(val query: String, val groups: List<FluidPampaGroup>, val meaning: Boolean = false, val tookMillis: Long = 0)` — «Cerca in Pampa» adesso: un gruppo per app, nell'ordine delle app.
  - `val pending: Int get()` — Quante app devono ancora rispondere.
  - `val shown: List<FluidPampaGroup> get()` — I gruppi da mostrare: quelli con dei risultati, quelli bloccati e quelli che non hanno risposto.
  - `companion object`
    - `val Empty: FluidPampaResults`

## `engine-search/search/FluidSearchCipher.kt`

- `interface FluidSearchCipher` — Come l'indice chiude i suoi dati sul disco (F8.22, «i dati sensibili nell'indice»). Con un cifrario:
  - `suspend fun seal(plain: ByteArray, associated: ByteArray): ByteArray` — Chiude [plain], legandolo ad [associated].
  - `suspend fun open(sealed: ByteArray, associated: ByteArray): ByteArray` — Riapre quello che [seal] ha chiuso con gli stessi [associated]; lancia un'eccezione se non torna.
  - `fun isLost(error: Throwable): Boolean` — Vero se [error] dice che la chiave non apre piu' niente (un telefono nuovo, il Keystore azzerato, un dato di un'altra chiave): allora l'indice si butta e si rifa'. Falso per una sezione solo chiusa (all'impronta), che va aperta e non svuotata.
  - `companion object`
    - `fun vault(section: VaultSection): FluidSearchCipher` — La cassaforte dell'engine: una sezione di `engine-vault` (di solito `VaultAccess.Device()`, che si apre da sola; una sezione all'impronta vuole di essere aperta prima di ogni ricerca).

## `engine-search/search/FluidSearchIndex.kt`

- `class FluidSearchIndex private constructor(val schema: FluidSearchSchema, private val db: SQLiteDatabase, private val file: File?, private val cipher: FluidSearchCipher?, val name: String) : Closeable` — L'indice di ricerca sul telefono (F8.22, `indice-ricerca`), come Core Spotlight per i dati di un'app: documenti con campi e pesi ([FluidSearchSchema]), la ricerca che tollera gli errori di battitura e trova gli inizi di parola, l'italiano (accenti, apostrofi, plurali), l'ordine per pertinenza e i pezzi di testo con le parole evidenziate.
  - `val generation: StateFlow<Long>` — Cambia a ogni scrittura: chi mostra dei risultati la osserva e rifa' la domanda (una nota cambiata mentre si cerca cambia la lista).
  - `var rebuilt: Boolean` — Vero se all'apertura l'indice di prima e' stato buttato: lo schema era cambiato ([FluidSearchSchema.version] o i campi), il formato era vecchio, o la chiave della cassaforte non c'era piu'. L'app allora rimanda tutto (i segni delle sorgenti sono ripartiti da capo da soli).
  - `class Stats(val documents: Int, val words: Int, val vectors: Int, val bytes: Long)` — Le misure dell'indice adesso.
  - `suspend fun put(documents: List<FluidSearchDocument>)` — Mette (o sostituisce) i documenti, tutti insieme.
  - `suspend fun put(document: FluidSearchDocument): Unit` — Mette (o sostituisce) un documento.
  - `suspend fun delete(ids: Collection<String>)` — Toglie i documenti con questi id (quelli che non ci sono si ignorano).
  - `suspend fun deleteAll(): Unit` — Svuota l'indice (lo schema e la chiave restano). I segni delle sorgenti ripartono da capo.
  - `suspend fun apply(sourceId: String, changes: FluidSearchChanges): Unit` — Applica un pezzo di modifiche di una sorgente e ne salva il segno, **nella stessa transazione** (F8.22, «l'aggiornamento a pezzi»): o tutto o niente. [sourceId] distingue le sorgenti dello stesso indice.
  - `suspend fun cursor(sourceId: String): String?` — Il segno da cui riprende la sorgente [sourceId] (null: da capo).
  - `suspend fun search(text: String, limit: Int = 50): FluidSearchResults` — Cerca [text] (scorciatoia di [search] con una [FluidSearchQuery]).
  - `suspend fun search(query: FluidSearchQuery): FluidSearchResults` — Cerca: per parole e, con [FluidSearchQuery.meaning], per significato.
  - `suspend fun completions(prefix: String, limit: Int = 5): List<String>` — Le parole dell'indice che cominciano con [prefix], dalla piu' frequente: per i suggerimenti mentre si scrive.
  - `suspend fun count(): Int` — Quanti documenti ci sono.
  - `suspend fun stats(): Stats` — Le misure dell'indice: documenti, parole del vocabolario, vettori e byte sul disco.
  - `suspend fun recents(): List<String>` — Le domande recenti, dalla piu' nuova (al piu' [MaxRecents]). Stanno nell'indice: si cancellano con lui.
  - `suspend fun addRecent(text: String)` — Ricorda [text] fra le recenti (in cima; una gia' presente sale).
  - `suspend fun removeRecent(text: String): Unit` — Toglie [text] dalle recenti.
  - `suspend fun clearRecents(): Unit` — Dimentica tutte le recenti.
  - `class PendingMeaning(val id: String, val key: String, val passages: List<String>)` — Un documento che aspetta i suoi vettori: i pezzi del testo da dare al modello ([passages]) e la chiave del testo ([key]), da ripassare a [putMeaning] (se nel frattempo il documento cambia, i vettori vecchi non entrano).
  - `suspend fun pendingMeaning(limit: Int, passageChars: Int = PassageChars, maxPassages: Int = MaxPassages): List<PendingMeaning>` — Fino a [limit] documenti senza vettori, o coi vettori di un testo vecchio.
  - `suspend fun meaningSpace(): String?` — Lo spazio dei vettori che l'indice conserva («id/dimensioni» di [FluidEmbeddingSpace]), o null se non ne ha.
  - `suspend fun usesMeaningSpace(space: FluidEmbeddingSpace): Boolean` — Vero se l'indice conserva vettori dello spazio [space]: solo allora una domanda per significato ha senso.
  - `suspend fun useMeaningSpace(space: FluidEmbeddingSpace): Unit` — Usa lo spazio [space] per i vettori: se l'indice ne conservava di un altro modello, li butta e li rifara' (vettori di spazi diversi non si confrontano).
  - `suspend fun pendingMeaningCount(): Int` — Quanti documenti aspettano i vettori.
  - `suspend fun putMeaning(pending: PendingMeaning, vectors: List<FloatArray>): Boolean` — I vettori di un documento, uno per pezzo di [PendingMeaning.passages], gia' normalizzati (norma 1). Si salvano a 8 bit con la scala (un errore sul coseno sotto 0,002 col modello di serie, misurato il 25/11/2026). Vero se sono entrati; falso se il documento nel frattempo e' cambiato o sparito.
  - `suspend fun destroy(): Unit` — Chiude l'indice e cancella il suo file: «l'indice si cancella insieme ai dati».
  - `companion object`
    - `const val MeaningMinZ: Float` — Da quanto sopra la media un pezzo e' «simile per significato»: 1,75 deviazioni (a occhio, 25/11/2026, dalle dieci domande del corpus con EmbeddingGemma 2 270M: i documenti giusti stanno fra 1,8 e 3,8, gli altri sotto 1,7).
    - `const val MaxMeaningHits: Int` — Quanti risultati al piu' arrivano dal significato.
    - `const val MaxRecents: Int` — Le domande recenti che si tengono (come la ricerca di Mail e Note di iOS, che ne mostra poche).
    - `const val PassageChars: Int` — Quanto e' lungo un pezzo di testo per il modello: 700 caratteri, circa 200 parti di parola (il modello ne legge 256).
    - `const val MaxPassages: Int` — Quanti pezzi al piu' per documento (oltre, il resto del testo si cerca solo per parole).
    - `suspend fun open(context: Context, schema: FluidSearchSchema, cipher: FluidSearchCipher? = null, name: String = schema.name): FluidSearchIndex` — Apre (o crea) l'indice [name] dell'app, con lo [schema] dato. Se lo schema e' cambiato l'indice si rifa' vuoto e [rebuilt] e' vero. Con [cipher] il testo conservato si chiude con la cassaforte.
    - `suspend fun inMemory(schema: FluidSearchSchema, cipher: FluidSearchCipher? = null): FluidSearchIndex` — Un indice in memoria, che sparisce chiudendolo: per le prove e le pagine di prova.
    - `fun fileOf(context: Context, name: String): File` — Il file dell'indice [name] dell'app (anche se non e' aperto): per misurarlo o cancellarlo.

## `engine-search/search/FluidSearchIndexing.kt`

- `interface FluidSearchSource` — Da dove arrivano i documenti di un indice: l'app li elenca a pezzi, da un segno in poi (il suo `updatedAt`, un numero di versione, un indice di pagina). L'engine non sa cosa siano.
  - `val id: String` — Il nome della sorgente: un indice puo' averne piu' d'una (note e trascrizioni).
  - `suspend fun changes(cursor: String?, limit: Int): FluidSearchChanges` — Le modifiche dopo [cursor] (null: dall'inizio), al piu' [limit] fra nuove, cambiate e tolte.
- `data class FluidSearchIndexPolicy(val chunk: Int = 100, val foregroundDocs: Int = 300, val heavyNeedsCharging: Boolean = true, val heavyNeedsWifi: Boolean = false)` — Le regole dell'aggiornamento.
  - `val heavyNeeds: FluidWorkNeeds get()` — I vincoli del lavoro pesante, nel linguaggio delle condizioni dell'engine.
- `data class FluidSearchProgress(val indexed: Int = 0, val running: Boolean = false, val hasMore: Boolean = false, val tookMillis: Long = 0, val error: String? = null)` — A che punto e' un aggiornamento: un valore intero, da mettere com'e' in uno stato.
- `interface FluidSearchJob` — Un lavoro dell'indice che il lavoro in sottofondo sa far girare: le parole ([FluidSearchIndexer]) o il significato.
  - `suspend fun runHeavy(deadlineMillis: Long, stop: () -> Boolean): Boolean` — Un giro pesante, fino a [deadlineMillis] (orologio di `SystemClock.elapsedRealtime`) o a [stop]. Vero se resta lavoro.
- `class FluidSearchIndexer(val index: FluidSearchIndex, val source: FluidSearchSource, val policy: FluidSearchIndexPolicy = FluidSearchIndexPolicy()) : FluidSearchJob` — L'aggiornamento di un indice da una sorgente, a pezzi ([FluidSearchIndexPolicy.chunk]): ogni pezzo entra col suo segno in una transazione sola, cosi' un'app chiusa a meta' riparte dal pezzo dopo.
  - `val progress: StateFlow<FluidSearchProgress>` — L'ultimo giro.
  - `suspend fun update(heavy: Boolean = false): FluidSearchProgress` — Un giro leggero (con [heavy] falso: fino a [FluidSearchIndexPolicy.foregroundDocs]) o completo. Restituisce come e' finito; [FluidSearchProgress.hasMore] vero vuol dire che il resto aspetta il lavoro in carica.
- `interface FluidTextEmbedder` — Il modello del significato (F8.22, `ricerca-in-tutte-le-app`): trasforma un testo in un vettore, cosi' «la mail del dentista» trova «Studio Odontoiatrico Rossi». L'implementazione di serie e' `FluidLiteRtEmbedder` di `engine-search-semantic` (EmbeddingGemma 2 con LiteRT-LM, tutto sul telefono); un'app senza il modello puo' chiedere i vettori all'app che lo ha (`FluidSearchBridge.remoteEmbedder`).
  - `val space: FluidEmbeddingSpace` — Lo spazio dei vettori: vettori di spazi diversi non si confrontano.
  - `suspend fun embed(texts: List<String>): List<FloatArray>` — I vettori (norma 1) dei [texts], nello stesso ordine. Lavora fuori dal filo principale.
  - `suspend fun release()` — Libera il modello e la sua memoria; il prossimo [embed] lo ricarica.
- `data class FluidEmbeddingSpace(val id: String, val dimensions: Int)` — Uno spazio di vettori: il modello e la sua ricetta. [id] e' quello di MyCloud Gallery per EmbeddingGemma 2 («embeddinggemma2-float-v1»): stesse domande, stessi vettori, e la ricerca in tutte le app puo' mandare a Gallery lo stesso vettore. [dimensions] e' la lunghezza piena (768); i vettori Matrjoska si possono tagliare (256, 128).
  - `companion object`
    - `val EmbeddingGemma2: FluidEmbeddingSpace` — EmbeddingGemma 2 (270M solo testo, o 440M testo e immagini: stesso spazio), 768 dimensioni, senza prefissi.
- `class FluidSemanticIndexer(val index: FluidSearchIndex, val embedder: FluidTextEmbedder, private val batch: Int = 8) : FluidSearchJob` — Il significato dei documenti di un indice: chiede al modello i vettori di chi non li ha (o li ha di un testo vecchio), a pezzi. E' sempre lavoro pesante: il modello costa decine di millisecondi per pezzo di testo (misure nella scheda del modulo), e trentamila documenti si fanno solo in carica ([FluidSearchWork]).
  - `val progress: StateFlow<FluidSearchProgress>` — L'ultimo giro.
  - `suspend fun update(maxDocs: Int = Int.MAX_VALUE): FluidSearchProgress` — Fa i vettori che mancano, fino a [maxDocs] documenti. Restituisce come e' finito.
- `interface FluidSearchHost` — L'app che tiene gli indici nella sua `Application`: il lavoro in carica parte anche ad app chiusa, in un processo nuovo, e chiede qui cosa far girare.
  - `suspend fun fluidSearchJobs(): List<FluidSearchJob>` — I lavori pesanti degli indici (le parole, poi il significato), nell'ordine in cui farli.
- `class FluidSearchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params)` — Il lavoro pesante in sottofondo (F8.22): WorkManager, solo **in carica** e con la batteria non scarica (e solo in Wi-Fi se lo chiede la politica), un giro per volta fino a [FluidSearchWork.RunMillis]; se resta lavoro se ne mette in coda un altro.
- `object FluidSearchWork` — Il lavoro pesante degli indici dell'app, con WorkManager.
  - `const val UniqueName: String` — Il nome del lavoro: uno per app.
  - `const val RunMillis: Long` — Quanto dura al piu' un giro: 8 minuti, sotto i 10 che WorkManager concede a un lavoro (fiducia alta: documentazione di `ListenableWorker`).
  - `val HeavyIndexing: FluidWork` — Il genere di lavoro, per le condizioni dell'engine: niente rete, si ferma col risparmio energetico e col caldo.
  - `fun schedule(context: Context, wifiOnly: Boolean = false, append: Boolean = false)` — Mette in coda il lavoro pesante: parte quando il telefono e' in carica (e con [wifiOnly] su una rete che non costa). Un lavoro gia' in coda resta quello ([append] lo accoda a quello in corso).
  - `fun heavyAllowedNow(context: Context, policy: FluidSearchIndexPolicy = FluidSearchIndexPolicy()): Boolean` — Vero se adesso si puo' fare il lavoro pesante **in primo piano** (l'app aperta e il telefono gia' in carica): la corrente, e le condizioni dell'engine se qualcuno le legge (risparmio energetico, caldo, Wi-Fi).

## `engine-search/search/FluidSearchModel.kt`

- `class FluidSearchField(val name: String, val weight: Float = 1f, val stored: Boolean = true, val sensitive: Boolean = false, val semantic: Boolean = !sensitive)` — Un campo dei documenti di un indice: il titolo, il corpo, il mittente, il nome del file.
- `class FluidSearchSchema(val name: String, val version: Int, val fields: List<FluidSearchField>, val title: String? = fields.firstOrNull()?.name, val snippets: List<String> = fields.filter { it.stored && it.name != title }…)` — Lo schema di un indice: i campi, quale e' il titolo e da quali campi nascono i pezzi evidenziati.
  - `fun field(name: String): FluidSearchField?` — Il campo [name], o null.
  - `val hasSensitive: Boolean get()` — Vero se un campo e' riservato: l'indice allora vuole la cassaforte.
  - `companion object`
    - `const val MaxFields: Int` — Quante colonne puo' avere l'indice (FTS4 ne regge molte di piu'; un indice dell'app ne ha da 2 a 6).
    - `fun titleAndBody(name: String, version: Int = 1): FluidSearchSchema` — Lo schema piu' comune: titolo (peso 3) e testo.
- `data class FluidSearchDocument(val id: String, val fields: Map<String, String>, val kind: String = "", val timestamp: Long? = null, val boost: Float = 1f, val link: String? = null)` — Un documento dell'indice: una nota, un messaggio, una comunicazione, una foto con la sua descrizione. Sostituisce quello con lo stesso [id] (come `CSSearchableItem.uniqueIdentifier`).
  - `operator fun get(name: String): String` — Il testo del campo [name], vuoto se manca.
- `data class FluidSearchQuery(val text: String, val kinds: Set<String> = emptySet(), val limit: Int = 50, val typos: Boolean = true, val meaning: FloatArray? = null, val includeSensitive: Boolean = true)` — Una domanda all'indice.
  - `companion object`
    - `const val MaxLimit: Int` — Il tetto di [limit].
- `data class FluidSearchSnippet(val text: String, val highlights: List<IntRange> = emptyList())` — Un pezzo di testo con le parole trovate: [highlights] sono gli intervalli (inclusivi) dentro [text] da mettere in evidenza. Un pezzo tagliato comincia o finisce con «…».
  - `val highlighted: List<String> get()` — Le parole evidenziate, nell'ordine.
- `data class FluidSearchHit(val id: String, val kind: String, val score: Float, val title: FluidSearchSnippet?, val snippet: FluidSearchSnippet?, val fields: Map<String, String>, val timestamp: Long?, val link: String?, val byWords: Boolean = true, val byMeaning: Boolean = false, val similarity: Float? = null)` — Un risultato.
- `data class FluidSearchResults(val query: String, val hits: List<FluidSearchHit>, val total: Int, val corrected: String? = null, val tookMillis: Long = 0, val meaning: Boolean = false)` — La risposta a una domanda.
  - `val byKind: List<Pair<String, List<FluidSearchHit>>>` — I risultati divisi per tipo, nell'ordine in cui compare il primo di ogni tipo.
  - `companion object`
    - `val Empty: FluidSearchResults` — Nessuna domanda: niente risultati.
- `data class FluidSearchChanges(val upserts: List<FluidSearchDocument> = emptyList(), val deletes: List<String> = emptyList(), val cursor: String? = null, val hasMore: Boolean = false)` — Un pezzo di modifiche da una sorgente ([FluidSearchSource]): i documenti nuovi o cambiati, quelli tolti, e il segno da cui ripartire. L'indice li applica tutti insieme, col segno, in una transazione sola: se il telefono si spegne a meta', si riparte dal pezzo prima.

## `engine-search/search/FluidSearchSamples.kt`

- `class FluidSearchSample(val app: String, val document: FluidSearchDocument)` — Un documento di prova e l'app finta a cui appartiene (le prove e la galleria lo mettono nell'indice di quell'app).
- `object FluidSearchSamples` — I dati di prova della ricerca (F8.22), tutti inventati: note, mail, comunicazioni della scuola, foto con la loro descrizione e conversazioni con l'assistente, in italiano, come li avrebbero Pampa Notes, Pampa Mail, ClasseViva, MyCloud Gallery e PampAI. Servono alle prove (errori di battitura, accenti, plurali, pertinenza, significato) e alle pagine di prova; un'app non li usa.
  - `val apps: Map<String, String>` — Le app finte, con il loro nome.
  - `val kinds: Map<String, String>` — I tipi, con il nome del gruppo nei risultati.
  - `val schema: FluidSearchSchema` — Lo schema di tutti gli indici di prova: titolo (peso 3), testo, persone (peso 2).
  - `const val Now: Long` — Il 25 novembre 2026 alle 9, il «adesso» dei dati di prova.
  - `val all: List<FluidSearchSample>` — I 32 documenti di prova.
  - `fun of(app: String): List<FluidSearchDocument>` — I documenti dell'app [app].
  - `val documents: List<FluidSearchDocument> get()` — Tutti i documenti, in un indice solo.
  - `val meaningQueries: List<Pair<String, String>>` — Le dieci domande per significato delle prove, con il documento che deve arrivare fra i primi tre. I vettori del modello vero (EmbeddingGemma 2 270M) per documenti e domande stanno nelle risorse delle prove.
  - `fun generated(count: Int, seed: Int = 22): List<FluidSearchDocument>` — [count] note inventate per le misure (la prima indicizzazione, il tempo di una ricerca): titoli e testi di poche righe, con parole che si ripetono come in un quaderno vero. Sempre le stesse per lo stesso [seed].

## `engine-search/search/FluidSearchUi.kt`

- `class FluidSearchKindStyle(val label: String, val icon: ImageVector? = null, val tone: FluidTone = FluidTone.Neutral)` — Il nome e il simbolo di un tipo di documento, per il suo gruppo e per il suo gettone.
- `data class FluidSearchLabels(val results: String = "%d risultati", val oneResult: String = "1 risultato", val noResults: String = "Nessun risultato per «%s»", val corrected: String = "Risultati per %1\$s · cercato anche «%2\$s»", val byMeaning: String = "per significato", val kinds: String = "Tipo", val searching: String = "Cerco…", val failed: String = "Impossibile cercare adesso")` — Le parole della ricerca. **In italiano di serie**: un'app tradotta le passa a [fluidSearchResults].
- `object FluidSearchSemantics` — Le proprieta' di semantica della ricerca, per le prove e le tracce (TalkBack non le legge).
  - `val Arriving: SemanticsPropertyKey<Boolean>` — Vero sulla riga (o sul gruppo) che arriva sfocato perche' l'app l'ha aggiunto da sola; con «Riduci movimento» no.
- `object FluidSearchDefaults` — I numeri della ricerca nel campo.
  - `const val DebounceMillis: Long` — Quanto si aspetta dopo l'ultima lettera prima di cercare per parole: 60 ms (a occhio, 25/11/2026: fra due tasti di chi scrive svelto passano 100–200 ms; Spotlight aggiorna a ogni tasto, e 60 ms non si vedono ma evitano le ricerche di parole a meta' quando si scrive in fretta).
  - `const val MeaningDelayMillis: Long` — Quanto si aspetta prima di chiedere il significato: 300 ms, come la ricerca del selettore di foto (`FluidMediaPickerDefaults.SearchDebounceMillis`). Il modello costa 50–90 ms per domanda: si chiede solo quando la persona si ferma.
  - `const val MeaningMinChars: Int` — Da quante lettere la domanda passa anche dal significato.
  - `const val KindTokenPrefix: String` — Il prefisso degli id dei gettoni dei tipi: «kind:nota».
  - `const val MaxCompletions: Int` — Quante parole che completano si suggeriscono sopra i risultati.
  - `const val ArrivalMillis: Int` — Quanto dura l'arrivo di un risultato che l'app aggiunge da sola (il significato, un'app che risponde dopo): 300 ms e una sfocatura che si scioglie, come lo scambio del contenuto del vetro (R7, `GlassContentDefaults.DurationMs`, fiducia alta sul tempo). Con «Riduci movimento» arriva fermo.
  - `const val ArrivalBlurDp: Float` — La sfocatura di partenza dell'arrivo, in dp.
  - `fun kindToken(kind: String, label: String): FluidToken` — Il gettone del tipo [kind].
- `class FluidSearchState internal constructor(internal val index: FluidSearchIndex?, private val scope: CoroutineScope, private val limit: Int, private val typos: Boolean)` — Lo stato di una ricerca nell'indice: il testo e i gettoni (dell'app, come in [FluidSearchSpec]), e i risultati. Si crea con [rememberFluidSearchState]; [spec] da' il campo gia' agganciato, [fluidSearchResults] i risultati.
  - `var query: String by mutableStateOf("")` — Il testo del campo.
  - `var tokens: List<FluidToken> by mutableStateOf(emptyList())` — I gettoni del campo (i tipi: [FluidSearchDefaults.kindToken]).
  - `var active: Boolean by mutableStateOf(false)` — Il campo e' aperto.
  - `var results: FluidSearchResults by mutableStateOf(FluidSearchResults.Empty)` — I risultati dell'ultima domanda.
  - `var searching: Boolean by mutableStateOf(false)` — Vero mentre una domanda e' in corso (il significato compreso).
  - `var failure: Throwable? by mutableStateOf(null)` — L'errore dell'indice nell'ultima domanda (una cassaforte chiusa, un file illeggibile, un indice chiuso), o null. Con un errore [results] e' vuoto per la domanda di adesso e la pagina dice [FluidSearchLabels.failed]; la domanda dopo riprova (R3: prima l'errore sembrava «Nessun risultato», o restavano i risultati della domanda di prima).
  - `var recents: List<String> by mutableStateOf(emptyList())` — Le domande recenti dell'indice.
  - `var completions: List<String> by mutableStateOf(emptyList())` — Le parole dell'indice che completano quella che si sta scrivendo.
  - `val kinds: Set<String>` — I tipi scelti coi gettoni.
  - `fun saveRecent()` — Ricorda la domanda di adesso fra le recenti (l'invio del campo, o un risultato aperto).
  - `fun removeRecent(text: String)` — Toglie [text] dalle recenti.
  - `fun complete(word: String)` — Mette [word] al posto della parola che si sta scrivendo (un suggerimento toccato).
  - `fun spec(prompt: String? = null, kinds: Map<String, FluidSearchKindStyle> = emptyMap(), placement: FluidSearchPlacement = FluidSearchPlacement.Automatic, labels: FluidSearchLabels = FluidSearchLabels()): FluidSearchSpec` — Il campo agganciato a questo stato: la ricerca di serie ([FluidSearchSpec.search]) col testo, i gettoni dei tipi ([kinds]: la fila delle pillole, che scelte diventano gettoni), e i suggerimenti sopra i risultati (le recenti a campo vuoto, con la ✕; le parole che completano, scrivendo). L'invio ricorda la domanda.
- `fun fluidSearchKindFilters(kinds: Map<String, FluidSearchKindStyle>, labels: FluidSearchLabels = FluidSearchLabels()): FluidFilterSet?` — La fila dei tipi per il campo: una pillola per tipo, che scelta diventa gettone.
- `@Composable fun rememberFluidSearchState(index: FluidSearchIndex?, embedder: FluidTextEmbedder? = null, limit: Int = 50, typos: Boolean = true): FluidSearchState` — Lo stato della ricerca nell'[index] (null: un indice che si sta ancora aprendo). Con [embedder] la domanda passa anche dal significato quando la persona si ferma ([FluidSearchDefaults.MeaningDelayMillis]) e l'indice ha i vettori di quel modello; i risultati per significato arrivano dopo quelli per parole, sfocati.
- `fun FluidSearchSnippet.toAnnotatedString(highlight: SpanStyle): AnnotatedString` — [FluidSearchSnippet] col suo testo e le parole trovate in [highlight].
- `@Composable fun fluidSearchHighlight(): SpanStyle` — Lo stile delle parole trovate: il peso semibold e il colore pieno del testo, sul grigio del pezzo (come la ricerca di Mail di iOS, che mette in nero grassetto le parole trovate nell'anteprima). Non solo il colore: si legge anche senza.
- `fun LazyListScope.fluidSearchResults(state: FluidSearchState, kinds: Map<String, FluidSearchKindStyle> = emptyMap(), onOpen: (FluidSearchHit) -> Unit, meta: (FluidSearchHit) -> String? = { null }, labels: FluidSearchLabels = FluidSearchLabels())` — I risultati di [state] in una pagina (il contenuto di `FluidScreen`), raggruppati per tipo come Mail e Note di iOS: l'intestazione «Note · 3», poi un gruppo con una riga per risultato, il titolo e il pezzo di testo con le parole trovate in evidenza. Sopra, la parola corretta quando c'e' («Risultati per riunione · cercato anche «riunone»»).
- `@Composable fun FluidSearchResultRow(state: FluidSearchState, hit: FluidSearchHit, style: FluidSearchKindStyle?, onOpen: (FluidSearchHit) -> Unit, meta: String? = null, labels: FluidSearchLabels = FluidSearchLabels())` — Una riga dei risultati: si puo' usare anche fuori da [fluidSearchResults] (un elenco dell'app).
- `@Composable fun Modifier.fluidSearchArrival(key: Any, arrives: Boolean): Modifier` — L'arrivo di un contenuto che l'app aggiunge da sola (F8, «il blur va solo sui cambi guidati dall'app»): l'opacita' da 0 e una sfocatura che si scioglie in [FluidSearchDefaults.ArrivalMillis]. Si anima solo la prima volta ([arrives]); con «Riduci movimento» arriva fermo. Il valore si legge nel layer: nessuna ricomposizione per fotogramma.
