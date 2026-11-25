# API · engine-files

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-files/files/FluidFileBrowser.kt`

- `data class FluidFileBrowserStrings(val browse: String = "Sfoglia", val recents: String = "Recenti", val locations: String = "Posizioni", val addLocation: String = "Aggiungi una posizione…", val addLocationDetail: String = "Una cartella del telefono o di un'app, come Drive", val removeLocation: String = "Togli dalle posizioni", val locationsFootnote: String = "Le cartelle che aggiungi restano qui. Tieni pr…, val phone: String = "Sul telefono", val downloads: String = "Download", val media: String = "Foto e video", val folder: String = "Cartella", val more: String = "Altro", val select: String = "Seleziona", val icons: String = "Icone", val list: String = "Elenco", val name: String = "Nome", val kind: String = "Tipo", val date: String = "Data", val size: String = "Dimensione", val ascendingName: String = "Dalla A alla Z", val descendingName: String = "Dalla Z alla A", val newestFirst: String = "Prima i più recenti", val oldestFirst: String = "Prima i meno recenti", val largestFirst: String = "Prima i più grandi", val smallestFirst: String = "Prima i più piccoli", val searchIn: (String) -> String = { "Cerca in $it" }, val items: (Int) -> String = { if (it == 1) "1 elemento" else "$it elementi"…, val loading: String = "Caricamento", val failedTitle: String = "Non riesco a leggere la cartella", val failedDetail: String = "La posizione non risponde o il permesso non c'…, val retry: String = "Riprova", val emptyTitle: String = "Cartella vuota", val emptyDetail: String = "Qui non c'è ancora niente.", val noResults: (String) -> String = { "Nessun risultato per «$it»" }, val noResultsDetail: String = "Prova con un'altra parola.", val noRecentsTitle: String = "Ancora niente", val noRecentsDetail: String = "I file che apri compaiono qui, dal più recente.", val open: String = "Apri", val share: String = "Condividi", val scrubber: String = "Scorri la cartella", val infoFile: String = "File", val infoKind: String = "Tipo", val infoSize: String = "Dimensione", val infoModified: String = "Modificato", val infoWhere: String = "Posizione", val infoDetails: String = "Dettagli", val kindName: (FluidFileKind) -> String = { kind -> when (kind) { FluidFileKind.Image ->…)` — Le parole dello sfoglia-file (F8.18), in italiano di serie: un'app in un'altra lingua passa le sue. I numeri sono già formattati da chi chiama.
- `class FluidFileBrowserState internal constructor(private val scope: CoroutineScope, layout: FluidFileLayout = FluidFileLayout.Grid, sort: FluidFileSort = FluidFileSort())` — Lo stato dello sfoglia-file: come si mostrano le cartelle ([layout]) e in che ordine ([sort]), uguali per tutte come in File, e le cartelle aperte, tenute (al più [MaxFolders]) perché tornando indietro siano ancora lì, allo stesso punto. [layout] e [sort] sopravvivono alla ricreazione; le cartelle si rileggono.
  - `var layout: FluidFileLayout by mutableStateOf(layout)`
  - `var sort: FluidFileSort by mutableStateOf(sort)`
  - `fun sortBy(sort: FluidFileSort)` — Cambia l'ordine di tutte le cartelle aperte (i Recenti hanno il loro, dall'ultimo aperto, come in File).
  - `fun folder(source: FluidFileSource, folder: String?, name: String): FluidFileFolderState` — La cartella [folder] (null: la radice) di [source], aperta o ritrovata.
  - `companion object`
    - `const val MaxFolders: Int` — Le cartelle tenute aperte: 12, a occhio (25 novembre 2026).
- `@Composable fun rememberFluidFileBrowserState(layout: FluidFileLayout = FluidFileLayout.Grid, sort: FluidFileSort = FluidFileSort()): FluidFileBrowserState` — Uno stato dello sfoglia-file che sopravvive alla ricreazione (vista e ordine).
- `object FluidFileBrowserTags` — I tag delle prove (e delle riprese).
  - `const val Browse` — La scheda «Sfoglia»: le posizioni.
  - `const val Recents` — La scheda «Recenti»: i file aperti, dal più recente.
  - `const val Tabs` — Il segmentato «Recenti | Sfoglia» in cima alla prima pagina (F8.18b).
  - `const val AddLocation`
  - `const val Folder`
  - `fun location(id: String)`
  - `fun item(id: String)`
- `@Composable fun FluidFileBrowser(modifier: Modifier = Modifier, state: FluidFileBrowserState = rememberFluidFileBrowserState(), sources: List<FluidFileSource> = emptyList(), locations: FluidFileLocations? = rememberFluidFileLocations(), recents: FluidFileRecents? = rememberFluidFileRecents(), onBack: (() -> Unit)? = null, onPick: ((List<FluidFileItem>) -> Unit)? = null, selectionActions: (FluidToolbarScope.(List<FluidFileItem>) -> Unit)? = null, openWith: ((FluidViewerFile) -> Unit)? = null, share: ((List<FluidFileItem>) -> Unit)? = null, media: ((FluidViewerFile, FluidViewerType) -> FluidViewerContent?)? = null, strings: FluidFileBrowserStrings = FluidFileBrowserStrings(), addLocationHint: Uri? = FluidFileLocations.PhoneHint)` — **Lo sfoglia-file** (F8.18, `sfoglia-file`): la pagina di vetro per sfogliare, cercare e scegliere i file, come File di iOS 26 (`UIDocumentBrowserViewController`; per scegliere, `fileImporter` di SwiftUI). Una pagina intera con le sue rotte, da mettere dove l'app vuole (una scheda, una rotta, un foglio): tutto si muove con le pagine di `FluidNavHost`.

## `engine-files/files/FluidFileFolderState.kt`

- `value class FluidFileLayout private constructor(private val id: Int)` — Come si mostra una cartella: **Icone** (la griglia con le miniature vere) o **Elenco**, i due modi di File di iOS 26 («Colonne» è dell'iPad e non c'è). Non è un `enum` (regola 3.11).
  - `companion object`
    - `val Grid: FluidFileLayout`
    - `val List: FluidFileLayout`
- `class FluidFileFolderState internal constructor(val source: FluidFileSource, val folder: String?, val name: String, private val scope: CoroutineScope, initialSort: FluidFileSort, private val locale: Locale = Locale.getDefault())` — **Una cartella aperta**: gli elementi arrivati finora dalla sua [source], pagina dopo pagina, l'ordine, la ricerca, l'errore con «Riprova». Lo tiene [FluidFileBrowserState], così tornando indietro la cartella è ancora lì, allo stesso punto ([listState]).
  - `var sort: FluidFileSort by mutableStateOf(initialSort)`
  - `var query: String by mutableStateOf("")`
  - `var loading: Boolean by mutableStateOf(false)`
  - `var failure: Throwable? by mutableStateOf(null)`
  - `var complete: Boolean by mutableStateOf(false)`
  - `val listState: LazyListState` — Il punto della lista, che resta quando si torna alla cartella.
  - `val items: List<FluidFileItem> by derivedStateOf` — Gli elementi che si vedono: nell'ordine scelto e, se c'è una ricerca, solo quelli che rispondono.
  - `val files: List<FluidFileItem> by derivedStateOf` — Solo i file di [items], nello stesso ordine: le pagine del visore.
  - `val loadedCount: Int get()` — Quanti elementi sono arrivati dalla sorgente (prima della ricerca).
  - `fun start()` — Apre la cartella: la prima pagina, se non c'è ancora niente.
  - `fun loadMore()` — La pagina dopo, se c'è e se non se ne sta già leggendo una. Dopo un errore serve [retry].
  - `fun retry()` — Riprova dopo un errore, dal punto in cui ci si era fermati.
  - `fun refresh()` — Rilegge la cartella dall'inizio (la sorgente ha avvisato che è cambiata, o è cambiato l'ordine).
  - `fun sortBy(sort: FluidFileSort)` — Cambia l'ordine. Lo stesso ordine non fa niente.
  - `fun search(query: String)` — Cerca [query] nella cartella. Una sorgente che cerca da sé si rilegge con le parole; per le altre si legge il resto della cartella (per trovare anche ciò che non è ancora arrivato) e si filtra in memoria.
  - `fun nearEnd(index: Int)` — La lista è a [index] di [items]: se manca poco alla fine, chiede la pagina dopo.

## `engine-files/files/FluidFileLocations.kt`

- `class FluidFileLocations internal constructor(context: Context)` — F8.18 · **Le posizioni concesse**: le cartelle che l'utente ha scelto col selettore di sistema dello Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`), che restano fra un avvio e l'altro col permesso persistente.
  - `var sources: List<FluidFileSource> by mutableStateOf(emptyList())` — Le cartelle concesse, come sorgenti, in ordine di nome. Si aggiorna con [reload].
  - `var loading: Boolean by mutableStateOf(true)` — Vero finché la prima lettura non è finita.
  - `suspend fun reload()` — Rilegge i permessi persistenti e i nomi delle cartelle (fuori dal thread principale).
  - `suspend fun add(treeUri: Uri): FluidFileSource?` — Prende il permesso persistente di lettura sull'albero [treeUri] (il risultato del selettore) e lo aggiunge. Restituisce la sorgente, o null se il permesso non si può prendere (il selettore non l'ha concesso).
  - `suspend fun remove(source: FluidFileSource)` — Toglie la cartella: restituisce il permesso persistente. I file restano dove sono.
  - `fun owns(source: FluidFileSource): Boolean` — Vero se [source] è una cartella concessa (si può togliere).
  - `companion object`
    - `val PhoneHint: Uri` — Dove apre il selettore: la memoria interna del telefono (il documento `primary:`). È un suggerimento: l'utente può scegliere un'altra posizione o un'altra app dal menu del selettore.
    - `fun of(context: Context): FluidFileLocations` — Per le prove e per chi le crea fuori da Compose.
- `@Composable fun rememberFluidFileLocations(): FluidFileLocations` — Le posizioni concesse, lette alla prima composizione.
- `@Composable fun rememberFluidAddLocation(locations: FluidFileLocations, hint: Uri? = FluidFileLocations.PhoneHint, onAdded: (FluidFileSource) -> Unit = {}): () -> Unit` — «Aggiungi una posizione…»: la funzione apre il selettore di sistema delle cartelle (sul telefono, di serie [FluidFileLocations.PhoneHint]); la cartella scelta entra fra le posizioni col permesso persistente e arriva a [onAdded]. Se l'utente chiude il selettore non succede niente.

## `engine-files/files/FluidFileRecents.kt`

- `class FluidFileRecents @VisibleForTesting internal constructor(private val file: File, val maxEntries: Int = DefaultMaxEntries, private val clock: () -> Long = System::currentTimeMillis)` — F8.18 · **Recenti**: i file aperti di recente dallo sfoglia-file, il più recente in cima, come la scheda «Recenti» di File. Al più [maxEntries] (di serie 60); un file riaperto torna in cima, una sola volta.
  - `data class Entry(val sourceId: String, val item: FluidFileItem, val openedAtMillis: Long)` — Un file aperto: la sorgente da cui viene, l'elemento e quando è stato aperto.
  - `val entries: List<Entry> get()` — I recenti, dal più recente. Stato di Compose: chi lo legge si ricompone quando cambia.
  - `fun record(sourceId: String, item: FluidFileItem)` — Mette [item] di [sourceId] in cima ai recenti. Una cartella o un file senza indirizzo non entrano.
  - `fun forget(uri: Uri)` — Toglie il file a [uri] dai recenti (cancellato, o non più leggibile).
  - `fun clear()` — Svuota i recenti.
  - `suspend fun prune(context: Context, exists: suspend (Uri) -> Boolean? = { uri -> withContext(Dispatchers.IO) { resumeTa…): Int` — Toglie i recenti i cui file non ci sono più: [exists] risponde vero, falso o null (non si sa: il recente resta). Di serie si chiede all'indirizzo. Restituisce quanti sono usciti.
  - `val source: FluidFileSource` — La sorgente della pagina «Recenti»: i file in ordine d'apertura («Data») o nell'ordine scelto.
  - `fun sourceOf(item: FluidFileItem): String?` — Da quale sorgente viene il recente [item] (per aprirlo con le stesse regole).
  - `companion object`
    - `const val DefaultMaxEntries: Int` — I recenti tenuti: 60, a occhio (25 novembre 2026).
    - `const val SourceId: String` — L'id della sorgente virtuale dei recenti.
    - `fun get(context: Context): FluidFileRecents` — I recenti del processo, nel `noBackupFilesDir` dell'app.
- `@Composable fun rememberFluidFileRecents(): FluidFileRecents` — I recenti del processo ([FluidFileRecents.get]).

## `engine-files/files/FluidFileSource.kt`

- `data class FluidFileItem(val id: String, val name: String, val isFolder: Boolean = false, val mimeType: String? = null, val sizeBytes: Long? = null, val modifiedAtMillis: Long? = null, val uri: Uri? = null, val childCount: Int? = null)` — Un file o una cartella di una [FluidFileSource]. [id] è stabile dentro la sorgente (l'id del documento, il percorso relativo): con l'id della sorgente identifica l'elemento nei recenti, nelle chiavi delle liste e nel viaggio d'oro. [uri] è l'indirizzo da cui il visore legge il file (null per una cartella di una sorgente virtuale); una cartella dice, se lo sa, quanti elementi contiene ([childCount]).
  - `val kind: FluidFileKind get()` — La famiglia del file, per l'icona e per TalkBack.
  - `fun viewerFile(): FluidViewerFile?` — Il file per i visori dell'engine; null per una cartella o un file senza indirizzo.
- `value class FluidFileSortKey private constructor(private val id: Int)` — Per che cosa si ordina una cartella. Non è un `enum` di proposito (regola 3.11): un ordine nuovo sarà una costante in più. Le quattro sono quelle del menu di File di iOS 26 («Nome», «Tipo», «Data», «Dimensione»; «Tag» non c'è).
  - `companion object`
    - `val Name: FluidFileSortKey`
    - `val Date: FluidFileSortKey`
    - `val Size: FluidFileSortKey`
    - `val Kind: FluidFileSortKey`
    - `val all: List<FluidFileSortKey>` — Nell'ordine del menu di File: nome, tipo, data, dimensione.
- `data class FluidFileSort(val key: FluidFileSortKey = FluidFileSortKey.Name, val descending: Boolean = false)` — L'ordine di una cartella: la chiave e il verso. Come File, la data e la dimensione partono **dalla più recente e dalla più grande** ([of]); un secondo tocco sulla voce scelta inverte il verso. Le cartelle stanno sempre prima dei file;
  - `fun reversed(): FluidFileSort` — Lo stesso ordine col verso opposto.
  - `fun comparator(locale: Locale = Locale.getDefault()): Comparator<FluidFileItem>` — Il confronto: le cartelle prima, poi la chiave nel verso scelto, poi il nome.
  - `companion object`
    - `fun of(key: FluidFileSortKey): FluidFileSort` — L'ordine di serie di una chiave, come File: dalla A alla Z, dal più recente, dal più grande.
- `data class FluidFileQuery(val folder: String?, val sort: FluidFileSort = FluidFileSort(), val cursor: String? = null, val limit: Int = DefaultLimit, val search: String? = null)` — Una richiesta a una sorgente: la [folder] (null è la radice), l'ordine, il segno dove riprendere ([cursor], quello dato dalla pagina prima; null all'inizio), quanti elementi al più ([limit]) e, per una sorgente che cerca da sé ([FluidFileSource.searches]), le parole da cercare ([search]) in tutta la cartella e sotto.
  - `companion object`
    - `const val DefaultLimit: Int` — Gli elementi per pagina: 200, a occhio (25 novembre 2026). Una cartella di migliaia di file arriva a pezzi.
- `data class FluidFilePage(val items: List<FluidFileItem>, val next: String? = null)` — Una pagina di una cartella: gli elementi e il segno per la prossima ([next]); null se è l'ultima.
- `interface FluidFileSource` — **Il contratto di una sorgente di file** (la scheda dell'idea: «paginata, lenta, che può fallire»). Le due di serie sono la cartella dell'app ([FluidFileSources.appFolder]) e una cartella concessa dall'utente con lo Storage Access Framework ([FluidFileSources.documentTree], anche di un'app che ha un `DocumentsProvider`: Drive, MyCloud Gallery).
  - `val id: String` — L'id stabile della sorgente: entra nei recenti, nelle rotte e nelle chiavi. Senza «/».
  - `val title: String` — Il nome nella lista delle posizioni e il titolo della radice: «Su questo telefono», «Drive».
  - `val subtitle: String? get()` — La seconda riga nella lista delle posizioni: l'app che la dà, lo spazio.
  - `val icon: ImageVector? get()` — L'icona nella lista delle posizioni; null: la cartella.
  - `val sorts: Boolean get()`
  - `val searches: Boolean get()`
  - `suspend fun list(query: FluidFileQuery): FluidFilePage`
  - `fun changes(folder: String?): Flow<Unit>`

## `engine-files/files/FluidFileSources.kt`

- `object FluidFileSources` — Le due sorgenti di serie dello sfoglia-file (F8.18).
  - `fun appFolder(root: File, id: String, title: String, subtitle: String? = null, icon: ImageVector? = null): FluidFileSource` — La cartella [root] dell'app (per esempio `filesDir/Documenti`, o i risultati di una conversione): una sorgente sul disco, senza permessi. I file nascosti (col punto davanti) non si mostrano; una cartella dice quanti elementi contiene. L'[id] dev'essere stabile fra un avvio e l'altro.
  - `fun documentTree(context: Context, treeUri: Uri, title: String, subtitle: String? = null, icon: ImageVector? = null, id: String = treeSourceId(treeUri)): FluidFileSource` — Una cartella concessa dall'utente con lo Storage Access Framework ([treeUri], da `ACTION_OPEN_DOCUMENT_TREE`: il telefono, una scheda, o un'app con un `DocumentsProvider`). Serve il permesso, persistente se la cartella deve restare fra un avvio e l'altro ([FluidFileLocations] lo prende da sé). La cartella si legge con una richiesta sola (il provider la dà intera) e si consegna a pagine;
  - `fun treeSourceId(treeUri: Uri): String` — L'id di serie di una cartella concessa: stabile, senza «/», dall'indirizzo dell'albero.
