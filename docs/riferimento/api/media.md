<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/media.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · engine-media

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-media/media/FluidAttachmentPanel.kt`

- `object FluidAttachmentPanelDefaults` — I numeri del pannello degli allegati (F8.14, `pannello-allegati`).
  - `val Height: Dp` — L'altezza del pannello quando la tastiera non si e' ancora vista: 300 dp sopra la barra di sistema, quanto una tastiera di Android in verticale (Gboard, ~280–310 dp). Appena la tastiera si vede, il pannello prende la sua altezza (la quota della tastiera, come in Messaggi di iOS, dove il pannello sta al posto della tastiera).
  - `val RecentSize: Dp` — Il lato delle foto recenti della striscia: 84 dp, come il selettore compatto di iOS 17 (`.photosPickerStyle(.compact)`, una riga sola che scorre di lato: celle da ≈ 84 pt con passo 86, WWDC23, misurato il 25 novembre 2026; fiducia media). Il pannello «+» di Messaggi non ha una striscia di recenti: e' la nota della scheda `pannello-allegati`.
  - `val RecentSpacing: Dp` — Lo spazio fra le foto recenti: 2 dp, come le celle del selettore di iOS.
  - `const val RecentCount: Int` — Quante foto recenti: 30 (la striscia scorre; per le altre c'e' «Tutte le foto»).
  - `const val SpringDamping: Float` — La molla dell'arrivo e dell'uscita quando la tastiera non c'e': quella dei fogli (smorzamento 0,86, rigidezza 380), a occhio sul pannello di iOS che sale al posto della tastiera.
  - `const val SpringStiffness: Float`
- `class FluidAttachmentSource(val id: String, val label: String, val icon: ImageVector, val tone: FluidTone = FluidTone.Neutral, val enabled: Boolean = true, val onClick: () -> Unit)` — Una fonte del pannello: la fotocamera, le foto, i file, la posizione, o una dell'app (una nota, un documento dell'archivio). L'elenco e' aperto: l'app lo riempie nell'ordine che vuole. [tone] e' il colore della piastrella dell'icona (mai della riga).
  - `companion object`
    - `fun camera(onClick: () -> Unit, label: String = FluidMediaLabels().camera): FluidAttachmentSource` — La fotocamera: l'app apre la sua cattura (con un `FileProvider` suo per `ACTION_IMAGE_CAPTURE`).
    - `fun photos(onClick: () -> Unit, label: String = FluidMediaLabels().photos): FluidAttachmentSource` — Le foto: di solito il selettore intero ([FluidMediaPicker]) o il Photo Picker di Android.
    - `fun files(onClick: () -> Unit, label: String = FluidMediaLabels().files): FluidAttachmentSource` — I file: il selettore di documenti di Android (`OpenMultipleDocuments`) o il visore dei file dell'app.
    - `fun location(onClick: () -> Unit, label: String = FluidMediaLabels().location): FluidAttachmentSource` — La posizione: l'app la chiede (col permesso dal tasto, F8.11) e la allega come vuole.
    - `fun audio(onClick: () -> Unit, label: String = FluidMediaLabels().audio): FluidAttachmentSource` — Un messaggio vocale o un audio.
    - `fun other(id: String, label: String, onClick: () -> Unit, icon: ImageVector = FluidIcons.AttachFile, tone: FluidTone = FluidTone.Neutral)` — Una fonte di un'altra app o dell'app stessa, con l'icona degli allegati.
- `object FluidAttachmentPanelTags` — I tag delle prove e delle riprese del pannello.
  - `const val Panel: String`
  - `const val Recents: String`
  - `const val Sources: String`
  - `fun recent(key: String): String`
  - `fun source(id: String): String`
- `class FluidAttachmentPanelState internal constructor(initiallyOpen: Boolean)` — Lo stato del pannello degli allegati: aperto o chiuso, e l'altezza della tastiera vista per ultima (il pannello prende la sua). Si crea con [rememberFluidAttachmentPanelState]; il «+» del composer chiama [toggle].
  - `var isOpen: Boolean by mutableStateOf(initiallyOpen)` — Il pannello e' aperto (o si sta aprendo).
  - `fun open()` — Apre il pannello al posto della tastiera (che si chiude).
  - `fun close()` — Chiude il pannello.
  - `fun toggle()` — Apre o chiude.
- `@Composable fun rememberFluidAttachmentPanelState(initiallyOpen: Boolean = false): FluidAttachmentPanelState` — Lo stato di un pannello degli allegati, chiuso di serie. Resta alla rotazione.
- `@Composable fun FluidAttachmentPanelHost(state: FluidAttachmentPanelState, panel: @Composable () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit)` — Il posto del pannello degli allegati: [content] (la schermata col composer) sta sopra, il [panel] in fondo, **al posto della tastiera e alla sua altezza** (come il «+» di Messaggi di iOS e i pannelli di input al posto della tastiera).
- `@Composable fun FluidAttachmentPanel(recents: FluidMediaSource?, sources: List<FluidAttachmentSource>, attached: List<String>, onAttach: (FluidPickedMedia) -> Unit, onRemove: (key: String) -> Unit, modifier: Modifier = Modifier, request: FluidMediaRequest = FluidMediaRequest(), maxAttachments: Int = Int.MAX_VALUE, onAllPhotos: (() -> Unit)? = null, labels: FluidMediaLabels = LocalFluidMediaLabels.current)` — Il pannello degli allegati di vetro (F8.14, `pannello-allegati`), come il «+» di Messaggi di iOS 26: **le foto recenti in cima**, in una striscia da toccare, **poi le fonti** ([sources]: la fotocamera, le foto, i file, la posizione, quelle dell'app), un elenco aperto che l'app riempie. Sta in un [FluidAttachmentPanelHost].
- `object FluidAttachmentMenuDefaults` — I numeri della scheda degli allegati che nasce dal «+» ([FluidAttachmentMenu]), misurati su Messaggi di iOS 26.
  - `val Width: Dp` — La scheda: larga 320 dp e alta al piu' 456 dp, come la scheda di Messaggi di iOS 26 e 27 (≈ 320 × 456 pt, guida dell'iPhone e articolo 104969, misurate il 25 novembre 2026 a 1,135 e 1,458 px per punto; fiducia alta). Il raggio degli angoli (≈ 35 pt in iOS) e' quello del popover dell'engine.
  - `val MaxHeight: Dp`
  - `val RowHeight: Dp` — Il passo delle righe: 66 dp (66 pt in iOS 17–27, fiducia alta).
  - `val IconSize: Dp` — Il cerchio dell'icona: 38 dp (38–39 pt), col centro a 53 dp dal bordo sinistro della scheda (bordo a 34 dp).
  - `val IconStart: Dp`
  - `val LabelGap: Dp` — Il nome comincia a 100 dp dal bordo della scheda (≈ 27 pt dopo l'icona).
  - `val TopPadding: Dp` — Sopra la prima riga: il centro della prima icona sta a 56 dp dal bordo alto, quindi 23 dp di margine.
- `@Composable fun FluidAttachmentMenu(visible: Boolean, onDismissRequest: () -> Unit, source: FluidTransitionSource?, recents: FluidMediaSource?, sources: List<FluidAttachmentSource>, attached: List<String>, onAttach: (FluidPickedMedia) -> Unit, onRemove: (key: String) -> Unit, request: FluidMediaRequest = FluidMediaRequest(), maxAttachments: Int = Int.MAX_VALUE, onAllPhotos: (() -> Unit)? = null, labels: FluidMediaLabels = LocalFluidMediaLabels.current)` — Il pannello degli allegati come in **Messaggi di iOS 26** (F8.14): il «+» diventa una **scheda di vetro** che cresce dal tasto e ci rientra (il viaggio d'oro dei pannelli che nascono da un tasto), con le fonti in righe dalle icone tonde ([FluidAttachmentMenuDefaults]: 320 × 456 dp, righe da 66 dp, icone da 38 dp, le misure di iOS 26).

## `engine-media/media/FluidDeviceMediaSource.kt`

- `class FluidDeviceMediaSource(context: Context, private val kinds: Set<FluidMediaKind> = FluidMediaKind.PhotosAndVideos, override val label: String = "Su questo telefono") : FluidMediaSource` — Le foto e i video del telefono, dal MediaStore (F8.14). Coi tre regimi dei permessi delle foto di Android: - **tutto** (`READ_MEDIA_IMAGES` e `READ_MEDIA_VIDEO` da Android 13, `READ_EXTERNAL_STORAGE` prima): la libreria intera;
  - `companion object`
    - `const val SourceId: String` — L'id della sorgente del telefono.
    - `fun contentUri(kind: FluidMediaKind, id: Long): Uri` — L'indirizzo di un elemento del MediaStore.

## `engine-media/media/FluidFakeMediaSource.kt`

- `class FluidFakeMediaSource(override val id: String, override val label: String, entries: List<Entry>, private val albumList: List<Album> = emptyList(), override val capabilities: FluidMediaSourceCapabilities = FluidMediaSourceCapabilities(search = true, sem…, initialAccess: FluidMediaAccess = FluidMediaAccess.Full, private val thumbnails: ((FluidMediaItem, Int) -> Bitmap?)? = null, private val uriAuthority: String = "dev.antigravity.fluidengine.media.fake") : FluidMediaSource` — Una sorgente finta (F8.14), per le prove e per la galleria: un archivio in memoria con album, elementi solo nel backup e una **ricerca per significato finta** (parole contro etichette, e le stagioni contro la data), che si comporta come fara' MyCloud Gallery dietro il contratto ([FluidMediaSourceContract]).
  - `class Entry(val item: FluidMediaItem, val tags: Set<String> = emptySet(), val albums: Set<String> = emptySet(), val colors: Pair<Int, Int> = 0xFF5DBB8F.toInt() to 0xFF1F6E8C.toInt())` — Un elemento della sorgente finta: l'elemento, le etichette della ricerca per significato («mare», «anna», «cane»), gli album in cui sta e i due colori della sua miniatura.
  - `class Album(val id: String, val title: String, val role: FluidMediaAlbumRole = FluidMediaAlbumRole.User)` — Un album della sorgente finta: gli elementi sono quelli che lo nominano in [Entry.albums].
  - `val entries: List<Entry> get()` — Gli elementi, dal piu' recente (per farne una copia con altre regole, o per le prove).
  - `val declaredAlbums: List<Album> get()` — Gli album dichiarati (oltre a «Libreria» e «Solo nel backup», che la sorgente fa da se').
  - `var backupReachable: Boolean by mutableStateOf(true)` — Falso: il backup (il NAS) non risponde, e gli elementi solo nel backup non si consegnano.
  - `var latencyMillis: Long` — Un'attesa prima di ogni risposta, per vedere gli stati di caricamento.
  - `val deliveries: List<List<String>> get()` — Le consegne fatte, una lista di id per consegna, nell'ordine ricevuto (per le prove).
  - `val queries: List<FluidMediaQuery> get()` — Le domande ricevute (per le prove).
  - `var refreshCount: Int` — Quante volte e' stato riletto l'accesso.
  - `fun colorsOf(item: FluidMediaItem): Pair<Int, Int>?` — I due colori della miniatura di [item], per una griglia che la disegna da se'.
  - `fun lock(unlock: PendingIntent? = null)` — Chiude la sorgente come il blocco dell'app di MyCloud Gallery; [unlock] e' l'azione di sblocco.
  - `companion object`
    - `const val AlbumAll: String` — L'album di tutto.
    - `const val AlbumBackupOnly: String` — L'album di cio' che sta solo nel backup.
    - `fun sample(id: String = "mycloud", label: String = "MyCloud Gallery", count: Int = 60, newestMillis: Long = 1_764_000_000_000L): FluidFakeMediaSource` — Una libreria di prova come quella di MyCloud Gallery: [count] elementi dal 2024 a oggi (uno ogni circa nove giorni), un video ogni cinque, un terzo solo nel backup, con etichette (mare, montagna, Anna, cane, festa, tramonto, citta') e tre album. Sempre gli stessi, a ogni chiamata: le prove ci contano.

## `engine-media/media/FluidMediaLabels.kt`

- `data class FluidMediaLabels(val photosTab: String = "Foto", val albumsTab: String = "Album", val cancel: String = "Annulla", val back: String = "Indietro", val add: String = "Aggiungi", val search: String = "Cerca", val searchPlaceholder: String = "Cerca nelle foto", val semanticPlaceholder: String = "Cerca: «mare d'estate», «Anna al parco»", val searchIn: (sourceLabel: String) -> String = { "Cerca in $it" }, val noResults: String = "Nessun risultato", val noResultsHint: String = "Prova con altre parole.", val empty: String = "Nessuna foto", val emptyHint: String = "Qui non ci sono foto o video.", val selected: (photos: Int, videos: Int) -> String = { p, v -> val n = p + v when { v == 0 && p == 1…, val addCount: (Int) -> String = { "Aggiungi ($it)" }, val limitReached: (Int) -> String = { if (it == 1) "Puoi scegliere un elemento" els…, val video: String = "Video", val photo: String = "Foto", val backupOnly: String = "Solo nel backup", val favorite: String = "Preferito", val duration: (minutes: Long, seconds: Long) -> String = { m, s -> when { m == 0L -> if (s == 1L) "1 sec…, val preview: String = "Anteprima", val previewHint: String = "Tieni premuto per l'anteprima", val select: String = "Seleziona", val deselect: String = "Togli dalla scelta", val position: (index: Int, count: Int) -> String = { i, c -> "$i di $c" }, val limitedTitle: String = "Solo le foto che hai scelto", val limitedAction: String = "Gestisci", val notAllowedTitle: String = "Consenti l'accesso alle foto", val notAllowedDetail: String = "Per scegliere dalla libreria, consenti l'accesso alle foto e ai video. Puoi anche scegliere con il selettore di Android, senza permessi.", val allowAccess: String = "Consenti l'accesso", val openSettings: String = "Apri Impostazioni", val systemPicker: String = "Scegli con Android", val permissionReason: String = "Per mostrarti le tue foto e i tuoi video qui, e sceglierli senza uscire.", val lockedTitle: (sourceLabel: String) -> String = { "$it è bloccata" }, val lockedDetail: String = "Sbloccala per vedere le sue foto qui.", val unlock: String = "Sblocca", val unavailableTitle: (sourceLabel: String) -> String = { "$it non è disponibile" }, val unavailableDetail: String = "Riprova fra poco.", val retry: String = "Riprova", val preparing: (Int) -> String = { if (it == 1) "Preparo 1 elemento…" else "Prep…, val refusedTitle: String = "Non è stato possibile aggiungere", val refusedUnreachable: String = "Il backup non risponde: le foto che stanno sol…, val refusedTooMany: (Int) -> String = { "Al massimo $it elementi alla volta." }, val refusedNothing: String = "Gli elementi scelti non ci sono più.", val refusedLocation: String = "Non è stato possibile togliere la posizione da…, val refusedFailed: String = "Qualcosa non è andato. Riprova.", val skipped: (Int) -> String = { if (it == 1) "1 elemento non c'era più." else…, val ok: String = "OK", val panelTitle: String = "Allegati", val recents: String = "Recenti", val allPhotos: String = "Tutte le foto", val camera: String = "Fotocamera", val photos: String = "Foto", val files: String = "File", val location: String = "Posizione", val audio: String = "Audio", val attached: String = "Allegata", val attach: String = "Allega")` — Le parole del selettore e del pannello degli allegati (F8.14). Di serie in italiano; [english] e' la versione inglese completa. Un'app in un'altra lingua ne da' una sua con [LocalFluidMediaLabels].
  - `companion object`
    - `fun english(): FluidMediaLabels` — Le parole in inglese.
- `val LocalFluidMediaLabels` — Le parole del selettore e del pannello ([FluidMediaLabels]).

## `engine-media/media/FluidMediaModel.kt`

- `class FluidMediaKind private constructor(val id: String)` — Il tipo di un elemento: una foto o un video. Una classe e non un enum: un tipo nuovo (la foto dal vivo, il documento fotografato) si aggiunge senza rompere chi c'e'. Due tipi con lo stesso [id] sono uguali.
  - `companion object`
    - `val Photo: FluidMediaKind` — Una foto (anche animata: GIF, WebP).
    - `val Video: FluidMediaKind` — Un video, con la sua durata.
    - `val PhotosAndVideos: Set<FluidMediaKind>` — Foto e video: cio' che chiede un selettore di serie.
    - `fun of(id: String): FluidMediaKind` — Il tipo con l'[id] dato: [Photo] e [Video] sono quelli di serie, un altro id resta com'e'.
    - `fun from(mimeType: String?): FluidMediaKind` — Il tipo di un file dal suo MIME: video se `video/…`, foto altrimenti.
- `data class FluidMediaItem(val source: String, val id: String, val kind: FluidMediaKind = FluidMediaKind.Photo, val mimeType: String? = null, val width: Int? = null, val height: Int? = null, val durationMillis: Long? = null, val takenAtMillis: Long? = null, val sizeBytes: Long? = null, val name: String? = null, val backupOnly: Boolean = false, val favorite: Boolean = false, val description: String? = null)` — Un elemento di una sorgente: una foto o un video, come la griglia lo mostra e la sorgente lo consegna. Non porta immagini: la miniatura la chiede la griglia alla sorgente ([FluidMediaSource.thumbnail]).
  - `val key: String get()` — La chiave della scelta: la sorgente e l'id, in una stringa che un `Bundle` sa salvare.
  - `val aspectRatio: Float?` — Larghezza su altezza, se note.
- `fun fluidMediaKey(source: String, id: String): String` — La chiave di scelta di un elemento ([FluidMediaItem.key]).
- `class FluidMediaAlbumRole private constructor(val id: String)` — Il ruolo di un album, per l'icona e l'ordine: gli album «intelligenti» di Foto (Recenti, Preferiti, Video, Screenshot) e quelli di una sorgente col backup (Solo nel backup). Una classe con costanti: un ruolo nuovo non rompe nessuno.
  - `companion object`
    - `val All: FluidMediaAlbumRole` — Tutto, dal piu' recente («Recenti»).
    - `val Favorites: FluidMediaAlbumRole`
    - `val Videos: FluidMediaAlbumRole`
    - `val Screenshots: FluidMediaAlbumRole`
    - `val Camera: FluidMediaAlbumRole`
    - `val BackupOnly: FluidMediaAlbumRole` — Cio' che sta solo nel backup (MyCloud Gallery: «solo sul NAS»).
    - `val User: FluidMediaAlbumRole` — Un album fatto dalla persona o una cartella.
    - `fun of(id: String): FluidMediaAlbumRole`
- `data class FluidMediaAlbum(val source: String, val id: String, val title: String, val count: Int? = null, val cover: FluidMediaItem? = null, val role: FluidMediaAlbumRole = FluidMediaAlbumRole.User)` — Un album di una sorgente. [count] e' quanti elementi ha, se la sorgente lo sa senza contare a lungo; [cover] e' l'elemento della copertina (di solito il piu' recente).
- `data class FluidMediaQuery(val album: String? = null, val text: String? = null, val kinds: Set<FluidMediaKind> = FluidMediaKind.PhotosAndVideos, val localOnly: Boolean = false)` — Cosa si chiede a una sorgente: un album (null: tutto, dal piu' recente), un testo da cercare (null: nessuna ricerca) e i tipi. Con [text] una sorgente con la ricerca semantica ([FluidMediaSourceCapabilities.semanticSearch]) cerca per significato («mare d'estate», «Anna al parco»); le altre per nome o per cartella.
- `data class FluidMediaPage(val items: List<FluidMediaItem>, val next: String? = null, val total: Int? = null, val interpretation: List<String> = emptyList())` — Una pagina di risultati: gli elementi, nell'ordine della sorgente (il piu' recente prima, o il piu' pertinente per una ricerca), il segno della pagina dopo ([next], null alla fine) e, per una ricerca, come la sorgente l'ha capita ([interpretation]: «Mare», «Agosto, tutti gli anni»), che il selettore mostra sotto il campo.
- `data class FluidMediaSourceCapabilities(val search: Boolean = false, val semanticSearch: Boolean = false, val albums: Boolean = true, val backupOnly: Boolean = false, val maxDelivery: Int? = null)` — Cosa sa fare una sorgente.
- `sealed interface FluidMediaAccess` — L'accesso a una sorgente in questo momento. Il selettore lo legge come stato e mostra la cosa giusta: la griglia, la fascia «solo alcune foto», il tasto che chiede il permesso, lo sblocco, il motivo per cui non c'e'.
  - `data object Full : FluidMediaAccess` — Tutto.
  - `data object Limited : FluidMediaAccess` — Solo una parte: le foto «solo alcune» di Android 14 (`READ_MEDIA_VISUAL_USER_SELECTED`). La sorgente mostra quelle concesse; il selettore lo dice e offre di sceglierne altre.
  - `data class NotAllowed(val forever: Boolean = false) : FluidMediaAccess` — Nessun accesso: il permesso non c'e'. [forever] se Android non lo chiede piu' (si va nelle impostazioni).
  - `data class Locked(val unlock: PendingIntent? = null) : FluidMediaAccess` — La sorgente e' chiusa (il blocco dell'app di MyCloud Gallery): [unlock] apre la sua schermata di sblocco, poi il selettore rilegge l'accesso. Niente si elenca finche' e' chiusa.
  - `data class Unavailable(val reason: String) : FluidMediaAccess` — La sorgente non c'e' adesso: [reason] e' un codice ([FluidMediaSourceContract.UNAVAILABLE_NOT_READY], [FluidMediaSourceContract.UNAVAILABLE_STOPPED], [FluidMediaSourceContract.UNAVAILABLE_GONE], o uno della sorgente).
- `data class FluidMediaProgress(val done: Int, val total: Int, val bytesDone: Long = 0, val bytesTotal: Long = 0)` — A che punto e' una consegna lunga: elementi fatti su totale, e i byte di quello in corso (se noti).
  - `val fraction: Float?` — La frazione fatta, se si sa.
- `class FluidMediaRefusal private constructor(val code: String, val limit: Int? = null)` — Perche' una consegna non e' avvenuta. Una classe con costanti: un motivo nuovo non rompe nessuno.
  - `companion object`
    - `val Unreachable: FluidMediaRefusal` — Il backup non risponde (il NAS di casa spento o lontano): gli elementi solo nel backup non si possono avere ora.
    - `val NothingAvailable: FluidMediaRefusal` — Fra quelli scelti nessuno si puo' consegnare adesso (spariti, nascosti dopo che la griglia li mostrava).
    - `val LocationNotRemoved: FluidMediaRefusal` — La posizione non si e' potuta togliere (MyCloud con «Rimuovi la posizione» acceso): niente e' uscito.
    - `val Failed: FluidMediaRefusal` — Un errore della sorgente.
    - `fun tooMany(limit: Int): FluidMediaRefusal` — Troppi elementi insieme: al piu' [limit].
    - `fun of(code: String, limit: Int? = null): FluidMediaRefusal` — Il motivo col [code] dato (dal contratto fra app).
- `sealed interface FluidMediaDelivery` — L'esito di una consegna ([FluidMediaSource.deliver]).
  - `class Ready(val media: List<FluidPickedMedia>, val skipped: Int = 0) : FluidMediaDelivery` — Gli indirizzi, nell'ordine chiesto (l'ordine della scelta). [skipped]: quanti elementi scelti non c'erano piu' (spariti, nascosti dopo che la griglia li mostrava).
  - `class Refused(val reason: FluidMediaRefusal, val culprit: String? = null) : FluidMediaDelivery` — Niente consegnato: [reason] dice perche'; [culprit] e' l'id dell'elemento che l'ha fermata, se e' uno solo.
  - `class NeedsUnlock(val unlock: PendingIntent? = null) : FluidMediaDelivery` — La sorgente si e' chiusa nel frattempo: [unlock] la riapre, poi si riprova.
- `data class FluidPickedMedia(val uri: Uri, val info: FluidFileInfo, val kind: FluidMediaKind = FluidMediaKind.from(info.mimeType), val width: Int? = null, val height: Int? = null, val durationMillis: Long? = null, val source: String, val item: FluidMediaItem? = null)` — Cio' che l'app riceve: l'indirizzo da leggere (con il permesso di lettura) e le informazioni del file, come le usano i visori e i tasselli dei file ([FluidFileInfo]).
- `data class FluidMediaRequest(val kinds: Set<FluidMediaKind> = FluidMediaKind.PhotosAndVideos, val limit: Int = 1, val ordered: Boolean = limit > 1, val mimeTypes: List<String>? = null, val localOnly: Boolean = false)` — Cosa chiede l'app al selettore.
  - `fun query(album: String? = null, text: String? = null): FluidMediaQuery` — La domanda di serie per una sorgente, con l'album e il testo dati.
  - `fun accepts(item: FluidMediaItem): Boolean` — Vero se un elemento di tipo [mimeType] va bene per questa richiesta. Un tipo ignoto passa se i tipi non sono precisi.
  - `companion object`
    - `val SinglePhoto: FluidMediaRequest` — Una foto sola, come la foto di un profilo.
    - `fun attachments(limit: Int = 20): FluidMediaRequest` — Fino a [limit] foto e video, in ordine: gli allegati di una mail.

## `engine-media/media/FluidMediaPicker.kt`

- `object FluidMediaPickerDefaults` — I numeri del selettore (F8.14).
  - `val Columns: List<Int>` — Le colonne fra cui si pizzica: 3, 5 e 9, come Foto e la griglia di F8.4; il selettore parte da 3, come il selettore di foto di iOS su iPhone in verticale: 3 colonne con passo di 131–132 pt su 390–393 pt e 2 pt fra le celle in iOS 15, 16, 17 e 26 (fotogrammi delle sessioni WWDC21–WWDC25, misurati il 25 novembre 2026; fiducia alta). Il pizzico porta comunque a 5 e a 9.
  - `const val InitialDensity: Int` — La densita' di partenza, indice in [Columns]: 3 colonne.
  - `const val PrefetchDistance: Int` — Quanto manca alla fine della griglia quando si chiede la pagina dopo: 40 elementi.
  - `const val SearchDebounceMillis: Long` — L'attesa prima di cercare mentre si scrive: 300 ms, come la ricerca di Foto (a occhio, 25 novembre 2026).
  - `val BottomBarRoom: Dp` — Lo spazio sotto la griglia per la lente della ricerca in basso: 96 dp.
- `object FluidMediaPickerTags` — I tag delle prove e delle riprese.
  - `const val Grid: String`
  - `const val Tabs: String`
  - `const val SearchButton: String`
  - `const val SearchField: String`
  - `const val Confirm: String`
  - `const val Cancel: String`
  - `const val Albums: String`
  - `const val Limited: String`
  - `const val Access: String`
  - `const val Preview: String`
  - `const val Delivering: String`
  - `fun cell(key: String): String`
  - `fun album(source: String, id: String): String`
- `@Composable fun FluidMediaPicker(state: FluidMediaPickerState, onResult: (List<FluidPickedMedia>) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier, labels: FluidMediaLabels = LocalFluidMediaLabels.current, offerSystemPicker: Boolean = true)` — Il selettore di foto e video di vetro (F8.14, `scelta-foto-e-video`), come il selettore di foto di iOS (`PHPickerViewController`, iOS 17–26), con le sorgenti dell'engine ([FluidMediaSource]).
- `fun fluidMediaDurationText(millis: Long): String` — La durata di un video come Foto: «0:42», «12:05», «1:02:33».

## `engine-media/media/FluidMediaPickerState.kt`

- `class FluidMediaFeed internal constructor(val source: FluidMediaSource, val query: FluidMediaQuery, private val scope: CoroutineScope, private val pageSize: Int, private val onItems: (List<FluidMediaItem>) -> Unit)` — Gli elementi di una sorgente per una domanda, pagina dopo pagina (F8.14): la griglia chiede [loadMore] quando arriva vicino alla fine. [version] cresce a ogni pagina arrivata: chi disegna si ricompone solo allora.
  - `val items: List<FluidMediaItem> get()` — Gli elementi arrivati, nell'ordine della sorgente.
  - `var version: Int by mutableIntStateOf(0)` — Quante pagine sono arrivate (e quindi quante volte gli elementi sono cambiati).
  - `var loading: Boolean by mutableStateOf(false)` — Una pagina sta arrivando.
  - `var finished: Boolean by mutableStateOf(false)` — Non ci sono altre pagine.
  - `var failed: Boolean by mutableStateOf(false)` — L'ultima pagina chiesta non e' arrivata (R1): la sorgente non risponde (il NAS spento, una scadenza) o ha lanciato. Gli elementi gia' arrivati restano, la lista non si chiude: [retry] la richiede. Il selettore lo dice al posto di «Nessuna foto», o in fondo alla lista se l'errore arriva a meta'.
  - `var interpretation: List<String> by mutableStateOf(emptyList())` — Come la sorgente ha capito la ricerca («Mare», «Estate»).
  - `var total: Int? by mutableStateOf(null)` — Quanti elementi ha in tutto, se la sorgente lo sa.
  - `fun loadMore()` — Chiede la pagina dopo, se non sta gia' arrivando e se c'e'. Dopo un errore ([failed]) serve [retry].
  - `fun retry()` — Richiede la pagina che non era arrivata ([failed]), dal punto in cui ci si era fermati.
  - `fun reload()` — Ricomincia da capo (dopo uno sblocco, un permesso nuovo, un cambio della sorgente).
- `class FluidMediaPickerState internal constructor(val sources: List<FluidMediaSource>, val request: FluidMediaRequest, val selection: FluidSelectionState<String>, private val scope: CoroutineScope, private val pageSize: Int = DefaultPageSize)` — Lo stato di un [FluidMediaPicker] (F8.14): le sorgenti (la prima e' quella di partenza), la richiesta dell'app, la scelta in ordine ([selection], col tetto della richiesta), le pagine di risultati gia' arrivate e la consegna.
  - `val primary: FluidMediaSource get()` — La sorgente di partenza (la prima).
  - `fun source(id: String): FluidMediaSource?` — La sorgente con l'[id] dato.
  - `fun feed(source: FluidMediaSource, query: FluidMediaQuery): FluidMediaFeed` — Gli elementi di [source] per [query]: sempre lo stesso oggetto per la stessa coppia, cosi' tornando si ritrova.
  - `fun albumsOf(source: FluidMediaSource): List<FluidMediaAlbum>?` — Gli album di [source], se sono gia' arrivati; [loadAlbums] li chiede.
  - `fun loadAlbums(source: FluidMediaSource, force: Boolean = false)` — Chiede gli album di [source] (una volta; [force] di nuovo). Un errore non si tiene come «nessun album» (R3): resta da chiedere, e [albumsOf] resta null finché non arrivano.
  - `fun refreshAccess()` — Rilegge l'accesso di tutte le sorgenti e ricarica cio' che serve (dopo un permesso, uno sblocco).
  - `fun item(key: String): FluidMediaItem?` — L'elemento con la chiave data, se e' gia' passato da una pagina.
  - `val selectedItems: List<FluidMediaItem>` — Gli elementi scelti, nell'ordine della scelta.
  - `var delivering: Boolean by mutableStateOf(false)` — Una consegna e' in corso.
  - `var progress: FluidMediaProgress? by mutableStateOf(null)` — A che punto e' la consegna in corso.
  - `var notice: FluidMediaPickerNotice? by mutableStateOf(null)` — L'avviso dell'ultima consegna non riuscita, o dei saltati; null: niente da dire.
  - `var unlockNeeded: FluidMediaAccess.Locked? by mutableStateOf(null)` — La sorgente chiusa da sbloccare, se la consegna l'ha trovata chiusa.
  - `suspend fun deliver(items: List<FluidMediaItem> = selectedItems): List<FluidPickedMedia>?` — Consegna [items] (di serie la scelta), nell'ordine dato, chiedendo a ogni sorgente i suoi. Se una sorgente rifiuta, non esce niente: la scelta resta com'e' (senza l'elemento che ha fermato la consegna, se e' uno solo) e [notice] dice perche'. Null se non e' uscito niente.
  - `companion object`
    - `const val DefaultPageSize: Int` — Quanti elementi per pagina: 120, una decina di schermate di cinque colonne.
- `data class FluidMediaPickerNotice(val refusal: FluidMediaRefusal?, val skipped: Int)` — Cosa dire dopo una consegna: il rifiuto ([refusal], null se e' andata) e quanti elementi non c'erano piu'.
- `@Composable fun rememberFluidMediaPickerState(sources: List<FluidMediaSource>, request: FluidMediaRequest = FluidMediaRequest()): FluidMediaPickerState` — Lo stato di un [FluidMediaPicker] con queste [sources] (la prima e' quella di partenza) e questa [request]. Un cambio delle sorgenti o della richiesta fa uno stato nuovo; la scelta resta alla rotazione e alla morte del processo, con i suoi elementi (al piu' 200: oltre, quelli in piu' si dicono al ✓ come non piu' disponibili).

## `engine-media/media/FluidMediaSource.kt`

- `interface FluidMediaSource` — Una sorgente di foto e video per il selettore (F8.14, la nota di Alessio su `scelta-foto-e-video`: «la lettura direttamente da MyCloud Gallery, con la ricerca semantica e le cose solo sul backup, e in futuro un archivio con indicizzazione e ricerca»).
  - `val id: String` — L'id della sorgente, stabile («device», «dev.pampa.mycloudgallery»): entra nelle chiavi della scelta.
  - `val label: String` — Il nome per la persona («Su questo telefono», «MyCloud Gallery»).
  - `val capabilities: FluidMediaSourceCapabilities` — Cosa sa fare.
  - `val access: FluidMediaAccess` — L'accesso di adesso, come stato di Compose (chi implementa lo tiene in un `mutableStateOf`): il selettore lo legge e mostra la cosa giusta. [refresh] lo rilegge.
  - `suspend fun refresh()` — Rilegge l'accesso: dopo il dialogo del permesso, al ritorno dalle impostazioni o dallo sblocco.
  - `suspend fun albums(): List<FluidMediaAlbum>` — Gli album, nell'ordine in cui mostrarli (prima quelli intelligenti, poi gli altri). Vuoto senza accesso.
  - `suspend fun page(query: FluidMediaQuery, cursor: String?, limit: Int): FluidMediaPage` — Una pagina di elementi per [query], dalla pagina [cursor] (null: la prima), al piu' [limit]. Vuota senza accesso.
  - `suspend fun thumbnail(item: FluidMediaItem, sizePx: Int): Bitmap?` — La miniatura di [item], col lato lungo vicino a [sizePx] (mai molto piu' piccola: si ingrandirebbe sgranata), o null se non c'e'. La griglia la tiene in una cache sua ([FluidMediaThumbnails]); la sorgente non deve tenerne.
  - `suspend fun deliver(items: List<FluidMediaItem>, request: FluidMediaRequest, onProgress: (FluidMediaProgress) -> Unit = {}): FluidMediaDelivery` — Gli indirizzi di [items], nell'ordine dato (l'ordine della scelta), per la [request] dell'app. [onProgress] dice a che punto e' una consegna lunga (un video solo nel backup che arriva dal NAS).

## `engine-media/media/FluidMediaSourceContract.kt`

- `object FluidMediaSourceContract` — Il contratto fra app delle sorgenti di foto e video (F8.14), **versione 1**: come un'app (in F12 MyCloud Gallery) offre la sua Libreria, la sua ricerca e le foto che stanno solo nel suo backup al selettore delle altre app Pampa, senza che queste vedano altro.
  - `const val VERSION: Int` — La versione di questo contratto.
  - `const val ACTION_SOURCE: String` — L'azione dell'`intent-filter` del provider: e' cosi' che chi legge trova le sorgenti.
  - `const val METHOD_INFO: String`
  - `const val METHOD_ALBUMS: String`
  - `const val METHOD_PAGE: String`
  - `const val METHOD_DELIVER: String`
  - `const val PATH_THUMBNAIL: String` — Il percorso delle miniature: `content://<autorita'>/thumbnail?id=<id>&kind=<tipo>`.
  - `const val MAX_PAGE: Int` — Il tetto di una pagina: oltre, chi serve taglia (un `Bundle` di trecento elementi sta ben sotto il limite del binder).
  - `const val MAX_DELIVERY: Int` — Il tetto di una consegna sola.
  - `const val UNAVAILABLE_NOT_READY: String` — L'app che serve non e' pronta (si sta avviando, non e' configurata).
  - `const val UNAVAILABLE_STOPPED: String` — L'app che serve e' ferma (una versione ritirata, un'impostazione che la spegne).
  - `const val UNAVAILABLE_GONE: String` — Il provider non risponde o non c'e' piu' (l'app e' stata tolta).
  - `const val UNAVAILABLE_VERSION: String` — Il provider parla una versione del contratto che chi legge non conosce.
  - `fun thumbnailUri(authority: String, item: FluidMediaItem): Uri` — Il percorso della miniatura di [item] presso l'autorita' [authority].

## `engine-media/media/FluidMediaSourceProvider.kt`

- `abstract class FluidMediaSourceProvider : ContentProvider()` — Il lato di chi **serve** il contratto delle sorgenti ([FluidMediaSourceContract], F8.14): un `ContentProvider` che mette una [FluidMediaSource] dell'app a disposizione del selettore delle altre app Pampa. In F12 MyCloud Gallery lo estende con la sua Libreria (la vista di adesso, la ricerca semantica, i solo-NAS e la loro consegna) e lo dichiara nel manifest con l'azione [FluidMediaSourceContract.ACTION_SOURCE].

## `engine-media/media/FluidMediaThumbnails.kt`

- `object FluidMediaThumbnails` — Le miniature del selettore (F8.14): una cache in memoria **col tetto in byte** (un ottavo della memoria dell'app, al piu' 32 MB; le meno usate escono per prime) e al piu' [Parallelism] letture insieme. La chiave e' l'elemento e il lato arrotondato a [Step] px: due celle uguali fanno un lavoro solo, e tornando su una cella la miniatura e' gia' li' (non si anima di nuovo).
  - `const val Step: Int` — Il passo del lato: 64 px.
  - `const val Parallelism: Int` — Quante letture insieme: 4 (il MediaStore e i provider delle altre app non amano la folla).
  - `fun bucket(sizePx: Int): Int` — Il lato arrotondato in su al passo.
  - `fun cached(item: FluidMediaItem, sizePx: Int): ImageBitmap?` — La miniatura gia' in cache, senza lavorare; null se non c'e'.
  - `suspend fun load(source: FluidMediaSource, item: FluidMediaItem, sizePx: Int): ImageBitmap?` — La miniatura di [item] da [source]: dalla cache, o letta (fuori dal filo principale, lo fa la sorgente).
  - `fun evict(item: FluidMediaItem)` — Toglie le miniature di un elemento (cambiato o tolto).
  - `fun clear()` — Svuota la cache.
- `@Composable fun rememberFluidMediaThumbnail(source: FluidMediaSource, item: FluidMediaItem, sizePx: Int): FluidImagePhase` — La miniatura di [item] come fase di un'immagine che arriva (F8.1, [FluidImagePhase]): `Success` subito se e' gia' in cache (niente arrivo animato), altrimenti `Loading` e poi `Success` o `Failure`. La lettura si annulla se la cella esce.

## `engine-media/media/FluidRemoteMediaSource.kt`

- `class FluidMediaSourceTrust private constructor(internal val sameSignature: Boolean, internal val packages: Set<String>, internal val any: Boolean)` — Di chi si fida chi legge, fra le sorgenti delle altre app ([FluidMediaSources.discover]).
  - `companion object`
    - `val SameSignature: FluidMediaSourceTrust` — Le app firmate come questa (di serie).
    - `fun sameSignatureOr(vararg packages: String): FluidMediaSourceTrust` — Le app firmate come questa e, in piu', i pacchetti dati (un'app di prova firmata con un'altra chiave).
    - `val Any: FluidMediaSourceTrust` — Ogni app che espone il contratto. Solo per le prove: una sorgente sconosciuta vede cosa cerca la persona.
- `object FluidMediaSources` — Le sorgenti delle altre app ([FluidMediaSourceContract]).
  - `suspend fun discover(context: Context, trust: FluidMediaSourceTrust = FluidMediaSourceTrust.SameSignature, timeoutMillis: Long = 3_000L): List<FluidRemoteMediaSource>` — Le sorgenti che le altre app installate offrono (in F12 MyCloud Gallery), di cui ci si fida secondo [trust], gia' collegate (ognuna ha detto chi e' e cosa sa fare). Esclude questa stessa app: una sorgente sua l'app la usa direttamente. Fuori dal filo principale; una sorgente che non risponde entro [timeoutMillis] resta fuori.
  - `suspend fun connect(context: Context, authority: String): FluidRemoteMediaSource?` — La sorgente del provider con l'[authority] data, gia' collegata; null se non risponde o parla un'altra versione del contratto. Per chi conosce gia' l'autorita' (e per le prove).
- `class FluidRemoteMediaSource internal constructor(private val context: Context, val authority: String, override val id: String, override val label: String, override val capabilities: FluidMediaSourceCapabilities, initialAccess: FluidMediaAccess) : FluidMediaSource` — Una sorgente di un'altra app, letta attraverso il contratto ([FluidMediaSourceContract]): la si ottiene da [FluidMediaSources.discover] (o [FluidMediaSources.connect]) e si passa al selettore come le altre.

## `engine-media/media/FluidSystemPhotoPicker.kt`

- `object FluidSystemPhotoPicker` — Il **Photo Picker di Android** (F8.14), dove basta: la brief di F8.14 dice «usa il Photo Picker di Android dove basta, e la griglia dell'engine dove serve di piu'». Non chiede permessi (Android 13+, e con gli aggiornamenti di Play anche prima), mostra le foto del telefono e quelle del cloud del telefono, e restituisce indirizzi leggibili dall'app.
  - `const val SourceId: String` — L'id di sorgente dei [FluidPickedMedia] che vengono dal Photo Picker di Android.
  - `fun isAvailable(context: Context): Boolean` — Il Photo Picker di sistema c'e' su questo telefono (altrimenti androidx ripiega su un selettore di documenti).
  - `fun suffices(context: Context, sources: List<FluidMediaSource>): Boolean` — Vero se per [sources] il Photo Picker di Android basta: c'e' solo il telefono ([FluidDeviceMediaSource]) e l'app non ha dichiarato nel manifest i permessi delle foto (quindi la griglia del telefono non potrebbe mostrare niente).
  - `fun declaresMediaPermissions(context: Context): Boolean` — L'app dichiara nel manifest almeno un permesso delle foto.
  - `suspend fun describe(context: Context, uris: List<Uri>): List<FluidPickedMedia>` — Le informazioni dei file scelti (nome, tipo, peso), lette fuori dal filo principale.
- `class FluidSystemPhotoPickerLauncher internal constructor(private val start: () -> Unit)` — Il lanciatore del Photo Picker di Android per una richiesta ([rememberFluidSystemPhotoPicker]).
  - `fun launch()` — Apre il Photo Picker di Android.
- `@Composable fun rememberFluidSystemPhotoPicker(request: FluidMediaRequest, onResult: (List<FluidPickedMedia>) -> Unit): FluidSystemPhotoPickerLauncher` — Il Photo Picker di Android per [request] ([FluidSystemPhotoPicker]): uno o, con [FluidMediaRequest.limit] > 1, piu' elementi (al piu' il tetto, che Android rispetta). [onResult] riceve i file scelti, nell'ordine di Android; niente se la persona annulla.
