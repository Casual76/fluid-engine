# API · engine-map

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-map/map/FluidGeo.kt`

- `data class FluidGeoPoint(val latitude: Double, val longitude: Double)` — Un punto sulla Terra, in gradi WGS84 (quelli del GPS, di OpenStreetMap e di `CLLocationCoordinate2D`).
  - `val wrappedLongitude: Double get()` — La longitudine riportata fra -180 (incluso) e 180 (escluso).
- `data class FluidGeoBounds(val south: Double, val west: Double, val north: Double, val east: Double)` — Un riquadro sulla Terra, da [south] a [north] e da [west] a [east] (in gradi). Non attraversa l'antimeridiano: un riquadro che lo attraversa si da' come due.
  - `val center: FluidGeoPoint get()`
  - `operator fun contains(point: FluidGeoPoint): Boolean`
  - `fun including(point: FluidGeoPoint): FluidGeoBounds` — Lo stesso riquadro allargato fino a contenere [point].
  - `companion object`
    - `fun of(points: Iterable<FluidGeoPoint>): FluidGeoBounds?` — Il riquadro piu' piccolo che contiene tutti i [points]; null se non ce n'e' nessuno.
- `data class FluidMapCamera(val center: FluidGeoPoint, val zoom: Double, val bearing: Double = 0.0)` — Dove guarda la mappa: il [center], lo [zoom] (0 la Terra intera in 512 dp, ogni passo il doppio, come MapLibre e `MKMapCamera` con la sua distanza) e la [bearing], i gradi in senso orario da nord del lato alto dello schermo (0: nord in alto; `MapCamera.heading` di MapKit).
  - `val normalizedBearing: Double get()` — La direzione riportata fra 0 (incluso) e 360 (escluso).
  - `val isRotated: Boolean get()` — Vero se il nord non e' in alto (oltre mezzo grado: sotto, la bussola non si mostra, come in Mappe).
- `object FluidMapProjection` — La proiezione di Mercatore della mappa (EPSG:3857), la stessa di MapLibre, MapKit e OpenStreetMap: a zoom `z` la Terra e' un quadrato di `512 · 2^z` dp ([TileSize]: le tessere vettoriali di MapLibre sono da 512). Le coordinate del «mondo» qui sono **normalizzate**: da 0 a 1 in orizzontale (da -180° a 180°) e in verticale (da 85,05° nord a 85,05° sud); i dp sono `normalizzato · 512 · 2^z`.
  - `const val TileSize: Double` — Il lato di una tessera a zoom intero, in dp: 512, come MapLibre (`util::tileSize_D`). Fiducia alta.
  - `const val MaxLatitude: Double` — La latitudine dove finisce il quadrato di Mercatore: `atan(sinh(π))`, in gradi.
  - `const val NorthTolerance: Double` — Sotto questa differenza (in gradi) la mappa e' «a nord»: la bussola non c'e'. A occhio, 26/11/2026.
  - `fun worldSize(zoom: Double): Double` — Il lato del mondo a [zoom], in dp.
  - `fun x(longitude: Double): Double` — La x normalizzata (0..1) di una longitudine.
  - `fun y(latitude: Double): Double` — La y normalizzata (0..1, 0 in cima) di una latitudine, ferma ai bordi del quadrato.
  - `fun longitude(x: Double): Double` — La longitudine di una x normalizzata (anche fuori da 0..1: si riporta).
  - `fun latitude(y: Double): Double` — La latitudine di una y normalizzata (ferma ai bordi del quadrato).
  - `fun bearingDistance(a: Double, b: Double): Double` — La distanza piu' breve fra due direzioni, in gradi (0..180).
  - `fun fit(bounds: FluidGeoBounds, widthDp: Double, heightDp: Double, paddingDp: Double = 48.0, maxZoom: Double = 16.0): FluidMapCamera` — Lo zoom e il centro che mostrano tutto [bounds] in una finestra di [widthDp] × [heightDp], lasciando [paddingDp] su ogni lato, senza superare [maxZoom] (un punto solo non diventa una tessera a zoom 22). Nord in alto.
- `class FluidMapProjector(val camera: FluidMapCamera, val widthPx: Float, val heightPx: Float, val density: Float)` — La proiezione di una [camera] su una finestra di [widthPx] × [heightPx] pixel con [density] pixel per dp: dal punto allo schermo e ritorno, e i movimenti del dito (spostare, ingrandire attorno a un punto, ruotare). Pura e senza allocazioni oltre al risultato: si usa nel disegno e nella misura a ogni fotogramma.
  - `val worldPx: Double get()` — Il lato del mondo in pixel.
  - `fun toScreen(point: FluidGeoPoint): Offset` — Dove cade [point] sullo schermo, in pixel dall'angolo in alto a sinistra.
  - `fun toScreenNormalized(x: Double, y: Double): Offset` — Dove cade il punto normalizzato ([x], [y]) sullo schermo; la x prende il giro di mondo piu' vicino al centro.
  - `fun toGeo(screen: Offset): FluidGeoPoint` — Il punto della Terra sotto il pixel [screen].
  - `fun normalizedAt(screen: Offset): Pair<Double, Double>` — Le coordinate normalizzate sotto il pixel [screen] (la x non riportata fra 0 e 1).
  - `fun panned(deltaPx: Offset): FluidMapCamera` — La camera dopo che il dito ha trascinato la mappa di [deltaPx]: il punto sotto il dito resta sotto il dito.
  - `fun transformed(focus: Offset, zoomDelta: Double, rotationDegrees: Double, minZoom: Double, maxZoom: Double): FluidMapCamera` — La camera dopo uno zoom di [zoomDelta] passi e una rotazione di [rotationDegrees] attorno al pixel [focus] (il punto fra le due dita, o il doppio tocco): il punto sotto [focus] resta lì.
  - `fun screenRadiusNormalized(): Double` — Il raggio, in coordinate normalizzate, del cerchio che contiene tutto lo schermo (con qualunque rotazione).
- `object FluidGeoFormat` — Le coordinate scritte: come le mostra Mappe («43,77310° N, 11,25560° E», cinque decimali: circa un metro) e come si copiano («43.77310, 11.25560», col punto: il formato che ogni app di mappe e ogni campo di ricerca capisce).
  - `const val Decimals: Int` — Le cifre dopo la virgola: cinque, circa 1,1 m all'equatore. Fiducia alta (la scheda di Mappe di iOS 26).
  - `fun display(point: FluidGeoPoint, locale: Locale = Locale.getDefault()): String` — Il punto da leggere, nella lingua del telefono: «43,77310° N, 11,25560° E».
  - `fun copy(point: FluidGeoPoint): String` — Il punto da copiare, sempre col punto decimale: «43.77310, 11.25560».
  - `fun geoUri(point: FluidGeoPoint, label: String? = null): String` — L'indirizzo `geo:` del punto, per «Indicazioni» e per le altre app di mappe (RFC 5870, con l'etichetta).
  - `fun openStreetMapUrl(point: FluidGeoPoint, zoom: Int = 17): String` — Il collegamento a OpenStreetMap del punto, per «Condividi»: si apre in ogni browser.

## `engine-map/map/FluidMap.kt`

- `object FluidMapTags` — I tag delle prove.
  - `const val Map: String`
  - `const val Controls: String`
  - `const val Location: String`
  - `const val Compass: String`
  - `const val Layers: String`
  - `const val Attribution: String`
  - `const val UserDot: String`
  - `const val Postcard: String`
  - `fun marker(id: String): String`
  - `fun style(id: String): String`
- `enum class FluidMapControlsPlacement` — Dove stanno i comandi di vetro.
- `data class FluidMapControls(val location: Boolean = true, val compass: Boolean = true, val layers: Boolean = true, val placement: FluidMapControlsPlacement = FluidMapControlsPlacement.AboveSheet)` — Quali comandi di vetro mostra la mappa, e dove. Tutti di serie, sopra il foglio, come Mappe di iOS 26.
  - `companion object`
    - `val All: FluidMapControls`
    - `val None: FluidMapControls`
- `@Composable fun FluidMap(state: FluidMapState, modifier: Modifier = Modifier, markers: List<FluidMapMarker> = emptyList(), selectedMarkerId: String? = null, onMarkerClick: (FluidMapMarker) -> Unit = {}, onClusterClick: ((FluidMapCluster) -> Unit)? = null, onMapClick: ((FluidGeoPoint) -> Unit)? = null, onLocationRequest: (() -> Unit)? = null, styles: List<FluidMapStyle> = FluidMapStyles.OpenFreeMap, engine: FluidMapEngine = rememberFluidMapEngine(), controls: FluidMapControls = FluidMapControls.All, contentPadding: PaddingValues = PaddingValues(0.dp), labels: FluidMapLabels = FluidMapLabels(), backdrop: GlassBackdropState = rememberGlassBackdrop(), markerContent: (@Composable (cluster: FluidMapCluster, selected: Boolean) -> Unit)? = null)` — La mappa: `Map` di MapKit per SwiftUI con i comandi di Mappe di iOS 26.
- `object FluidMapControlsDefaults` — I numeri dei comandi e dei segni sopra la mappa.
  - `val EdgeMargin` — Il margine dei comandi dal bordo destro: 16 dp. Mappe di iOS 26: 16 pt nella guida dell'iPhone (1,11 px/pt) e nelle immagini di Newsroom (1,746 px/pt), misurate il 26/11/2026 (`sessioni/f8-26/riferimenti/indice.md`). Fiducia alta.
  - `val SheetGap` — Lo spazio fra la capsula e il bordo alto del foglio: 10 dp (Newsroom 10 pt, la guida 12,5–13,5). Fiducia media.
  - `val TopMargin` — Il margine dall'alto quando i comandi stanno in alto (`TopEnd`, la mappa di «Luoghi» in Foto). A occhio, 26/11/2026.
  - `val Button` — Il lato di un tasto della capsula e della bussola: 48 dp (Mappe di iOS 26: 47,5–49 pt; la capsula 48 × 96). Fiducia alta.
  - `val Gap` — Lo spazio fra la bussola e la capsula: 10 dp (Mappe di iOS 26: 10,3 pt fra gli elementi della colonna). Fiducia alta.
  - `val UserDot` — Il pallino della posizione (18 dp) e il bordo bianco (3 dp), come il punto blu di Mappe. A occhio, 26/11/2026.
  - `val UserRing`

## `engine-map/map/FluidMapClusters.kt`

- `data class FluidMapMarker(val id: String, val point: FluidGeoPoint, val title: String, val subtitle: String? = null, val tint: Color = Color.Unspecified, val glyph: ImageVector? = null, val count: Int = 1)` — Un segnaposto: `Marker` di MapKit per SwiftUI (iOS 17), il pallino colorato col simbolo bianco e il nome sotto.
- `class FluidMapCluster internal constructor(val id: String, val point: FluidGeoPoint, val markers: List<FluidMapMarker>, val level: Int, internal val x: Double, internal val y: Double, internal val parentId: String?)` — Un grappolo a un livello di zoom: uno o piu' segnaposto vicini sullo schermo, mostrati come uno solo (il cerchio col numero di MapKit, `MKClusterAnnotation`). Un grappolo di un solo segnaposto e' il segnaposto stesso ([isSingle]).
  - `val count: Int` — Quante cose contiene: la somma dei [FluidMapMarker.count].
  - `val isSingle: Boolean get()` — Un segnaposto solo.
  - `val first: FluidMapMarker get()` — Il primo segnaposto (per un grappolo di uno, il segnaposto).
  - `val bounds: FluidGeoBounds get()` — Il riquadro dei suoi segnaposto.
- `class FluidMapClusterIndex private constructor(val minZoom: Int, val maxZoom: Int, val radiusDp: Double, private val levels: Array<Level>, val size: Int)` — I grappoli di un elenco di segnaposto a ogni livello di zoom, calcolati una volta (l'algoritmo di Supercluster di Mapbox, ISC: dal livello piu' alto in giu', ogni punto raccoglie i vicini entro [radiusDp] sullo schermo e il grappolo va nel loro baricentro pesato). Poi chiedere i grappoli di un riquadro costa poco: si leggono dalle celle della griglia di quel livello.
  - `fun clusters(level: Int): List<FluidMapCluster>` — Tutti i grappoli del livello [level].
  - `fun cluster(level: Int, id: String): FluidMapCluster?` — Il grappolo [id] al livello [level], se c'e'.
  - `fun around(level: Int, x: Double, y: Double, radius: Double): List<FluidMapCluster>` — I grappoli del livello [level] dentro il cerchio di centro ([x], [y]) e raggio [radius], in coordinate normalizzate (la x anche fuori da 0..1). Nell'ordine del livello, che e' stabile.
  - `fun ancestor(id: String, from: Int, to: Int): FluidMapCluster?` — L'antenato di [id] (un grappolo del livello [from]) al livello [to] (piu' basso), cioe' il grappolo che lo contiene quando si allontana; lo stesso grappolo se [to] >= [from].
  - `fun expansionZoom(cluster: FluidMapCluster): Int` — Lo zoom a cui il grappolo si divide: il primo livello sopra il suo in cui il suo primo segnaposto non sta piu' con tutti gli altri. E' dove porta il tocco su un grappolo (come `MKMapView` che mostra i membri). [maxZoom] se non si divide mai (segnaposto nello stesso punto).
  - `companion object`
    - `const val DefaultRadiusDp: Double` — Il raggio di raccolta di serie: 48 dp, poco piu' del pallino di un segnaposto (30 dp) col suo margine, perche' due pallini non si coprano mai. A occhio, 26/11/2026 (Supercluster usa 40 px su tessere da 256, cioe' 80 su 512: con i segnaposto di MapKit, piu' piccoli, il grappolo arrivava troppo presto).
    - `val Empty: FluidMapClusterIndex` — Un indice vuoto.
    - `fun build(markers: List<FluidMapMarker>, radiusDp: Double = DefaultRadiusDp, minZoom: Int = 0, maxZoom: Int = 18): FluidMapClusterIndex` — Costruisce i grappoli di [markers] dal livello [maxZoom] (sopra, ogni segnaposto e' solo) a [minZoom]. Pura, deterministica (lo stesso elenco da' gli stessi grappoli e gli stessi id) e senza Android: da chiamare fuori dal thread principale per elenchi lunghi (diecimila segnaposto: decine di millisecondi).

## `engine-map/map/FluidMapEngine.kt`

- `data class FluidMapCredit(val text: String, val url: String)` — Un credito della mappa: la riga da leggere e il collegamento che la spiega.
- `data class FluidMapAttribution(val short: String, val credits: List<FluidMapCredit>)` — L'attribuzione di una mappa: la scritta corta sempre visibile nell'angolo ([short]) e i crediti interi che compaiono toccandola ([credits]). La mappa la disegna sempre, anche sulla cartolina, e non si spegne:
- `data class FluidMapStyle(val id: String, val label: String, val uri: String, val darkUri: String? = null, val attribution: FluidMapAttribution = FluidMapStyles.OpenFreeMapAttribution, val maxZoom: Double = 19.0, val swatch: Color = Color(0xFFE9E4DA), val darkSwatch: Color = Color(0xFF1D2330))` — Uno stile della mappa: l'indirizzo dello stile di MapLibre ([uri]) e quello per il tema scuro ([darkUri], null se lo stile non ne ha uno: allora resta chiaro anche di notte), il nome nel pannello dei livelli e i due colori del suo campione. Lo stile dice anche fin dove arriva lo zoom ([maxZoom]) e chi va citato ([attribution]).
  - `fun uriFor(dark: Boolean): String` — L'indirizzo per il tema: [darkUri] di notte, se c'e'.
- `object FluidMapStyles` — Gli stili di serie: le mappe di OpenStreetMap servite da **OpenFreeMap** (tiles.openfreemap.org), gratis, senza chiave, senza account, senza limiti di visite (openfreemap.org, letto il 26/11/2026). Le regole del servizio, che l'app accetta usandolo:
  - `val OpenFreeMapAttribution: FluidMapAttribution` — I crediti delle mappe di OpenFreeMap, come li scrive il servizio (TileJSON di tiles.openfreemap.org): la scritta corta «OpenFreeMap © OpenMapTiles © OpenStreetMap» porta i tre nomi che i termini d'uso e l'ODbL chiedono in vista.
  - `val Explore: FluidMapStyle` — «Esplora»: lo stile Liberty, coi parchi, l'acqua e gli edifici; di notte Dark. Il piu' vicino a Mappe.
  - `val Essential: FluidMapStyle` — «Essenziale»: Positron, grigio chiaro, per i segnaposto che devono spiccare; di notte Dark.
  - `val Vivid: FluidMapStyle` — «Vivace»: Bright, coi colori pieni delle strade; di notte Fiord, il blu notte.
  - `val OpenFreeMap: List<FluidMapStyle>` — I tre stili di OpenFreeMap, nell'ordine del pannello dei livelli.
- `data class FluidMapSnapshotRequest(val camera: FluidMapCamera, val widthPx: Int, val heightPx: Int, val density: Float, val style: FluidMapStyle, val dark: Boolean)` — Che cosa chiede la cartolina al motore: un'immagine ferma di [camera], di [widthPx] × [heightPx] pixel.
- `interface FluidMapCameraDriver` — Chi muove la camera della mappa vera: il motore lo da' a [FluidMapState.attach] quando la sua superficie e' pronta, e da li' lo stato gli passa i comandi (la posizione, la bussola, il tocco su un grappolo).
  - `fun jump(camera: FluidMapCamera)` — Porta la camera a [camera] subito, senza animazione.
  - `suspend fun fly(camera: FluidMapCamera, durationMillis: Int)` — Porta la camera a [camera] in [durationMillis] (una curva morbida del motore). Torna quando arriva o si ferma.
- `interface FluidMapEngine` — Il motore che disegna la mappa: la superficie viva (che segue il dito e dice la camera allo stato) e l'immagine ferma della cartolina. Ce ne sono due:
  - `val glassSamplesMap: Boolean` — Vero se il vetro sopra la mappa la rifrange: la superficie si disegna dentro Compose (una `TextureView`, un `Canvas`). Falso per una `SurfaceView`, che sta in una finestra sua: il vetro dei comandi allora si vede chiaro, senza sfocare la mappa sotto.
  - `@Composable fun Surface(state: FluidMapState, style: FluidMapStyle, dark: Boolean, modifier: Modifier)` — La mappa viva. Legge la camera da [state] alla prima composizione, poi la dice lei a ogni fotogramma con [FluidMapState.report] e prende i comandi con [FluidMapState.attach]. Il dito la muove (trascinare, pizzicare, ruotare con due dita, doppio tocco): il motore non la sfoca mai, neanche nel lancio («dito nitido»).
  - `suspend fun snapshot(request: FluidMapSnapshotRequest): ImageBitmap?` — L'immagine ferma per [request], o null se non si puo' fare (niente rete, stile che non arriva).
- `val LocalFluidMapEngine: ProvidableCompositionLocal<FluidMapEngine?>` — Il motore della mappa di serie per questo sottoalbero. Null (di serie): [rememberFluidMapEngine] fa MapLibre. Le prove e la galleria senza rete forniscono qui il disegno ([FluidSketchMapEngine]).
- `@Composable fun rememberFluidMapEngine(): FluidMapEngine` — Il motore di [LocalFluidMapEngine], o la mappa vera di MapLibre ([FluidMapLibreEngine], in `TextureView`).
- `data class FluidMapLabels(val map: String = "Mappa", val location: String = "Posizione", val locationFollowing: String = "Posizione, la mappa ti segue", val compass: (degrees: Int) -> String = { "Bussola, $it gradi. Tocca per tornare al nor…, val layers: String = "Livelli della mappa", val layersTitle: String = "Mappa", val credits: String = "Crediti della mappa", val zoomIn: String = "Ingrandisci", val zoomOut: String = "Riduci", val showAll: String = "Mostra tutti i luoghi", val cluster: (count: Int) -> String = { if (it == 1) "1 luogo" else "$it luoghi" }, val clusterAction: String = "Avvicina", val selected: String = "Scelto", val userLocation: String = "La tua posizione", val directions: String = "Indicazioni", val share: String = "Condividi", val copyCoordinates: String = "Copia coordinate", val copied: String = "Coordinate copiate", val openMap: String = "Apri la mappa", val postcard: (place: String) -> String = { "Mappa di $it" }, val loading: String = "Carico la mappa", val unavailable: String = "Mappa non disponibile", val visiblePlaces: (count: Int) -> String = { if (it == 1) "Mappa, un luogo in vista" else…, val copyShort: String = "Copia")` — Le parole della mappa, per TalkBack e per gli scritti. Italiano di serie; [English] per le app in inglese.
  - `companion object`
    - `val English: FluidMapLabels` — Le stesse parole in inglese.

## `engine-map/map/FluidMapLibreEngine.kt`

- `enum class FluidMapRenderMode` — Come si disegna la mappa vera.
- `class FluidMapLibreEngine(context: Context, val renderMode: FluidMapRenderMode = FluidMapRenderMode.Texture, val maximumFps: Int = screenMaximumFps(context)) : FluidMapEngine` — La mappa vera, con MapLibre Native e gli stili di [FluidMapStyles] (OpenFreeMap di serie).
  - `companion object`
    - `fun ensureStarted(context: Context)` — Avvia MapLibre una volta per processo (dal thread principale). Niente chiave: OpenFreeMap non ne chiede.
    - `fun screenMaximumFps(context: Context): Int` — Il ritmo piu' alto dello schermo, in fotogrammi al secondo (120 sul Tab S9, 60 su un telefono semplice): il limite di serie di MapLibre in [FluidMapRenderMode.Texture]. Il piu' alto e non quello di adesso, perche' uno schermo adattivo sta a 60 da fermo e sale sotto il dito. 60 se lo schermo non lo dice.

## `engine-map/map/FluidMapLocation.kt`

- `class FluidSystemLocation(context: Context)` — La posizione del telefono con `LocationManager`, senza Play Services: il fornitore «fused» di Android 12 quando c'e', altrimenti la rete e il GPS (il migliore acceso). Senza Play Services la posizione e' meno precisa al chiuso: l'alone dell'incertezza lo dice.
  - `fun hasPermission(): Boolean` — Vero se l'app ha almeno la posizione approssimata.
  - `fun last(): FluidMapUserLocation?` — L'ultima posizione conosciuta, se c'e' e se l'app ha il permesso.
  - `fun updates(intervalMillis: Long = 2_000): Flow<FluidMapUserLocation>` — Le posizioni, una ogni [intervalMillis] al piu', finche' qualcuno ascolta. Niente senza permesso o senza fornitore. Sul thread principale (il `Looper` principale), come vuole lo stato della mappa.
- `@Composable fun FluidSystemLocationEffect(state: FluidMapState, enabled: Boolean, intervalMillis: Long = 2_000)` — Tiene [state] sulla posizione vera mentre [enabled] (di solito: il permesso c'e' e la mappa si vede). Fuori dal permesso non fa niente.
- `val FluidMapLicenses: List<FluidLicense>` — Le licenze di `engine-map`, da aggiungere alla pagina «Licenze e crediti»: MapLibre Native, i dati di OpenStreetMap, il servizio di OpenFreeMap e lo stile di OpenMapTiles.

## `engine-map/map/FluidMapMarkers.kt`

- `object FluidMapPinDefaults` — Le misure del segnaposto: il pallino di `Marker` di MapKit (iOS 17 e 26), misurato a occhio sulle schermate di Mappe il 26/11/2026 (fiducia media): 30 dp col simbolo da 16, la punta di 7 dp, 46 dp quando e' scelto; il nome sotto, in 11,5 sp seminero, su due righe al piu'.
  - `val Balloon`
  - `val SelectedBalloon`
  - `val Tail`
  - `val Glyph`
  - `val Half` — Meta' dell'altezza del segnaposto: la punta sta al centro, sopra il pallino, sotto il nome.
  - `val LabelWidth`
  - `val ClusterMin` — Il grappolo: un cerchio da 34 a 56 dp, che cresce col logaritmo del numero.
  - `val ClusterMax`

## `engine-map/map/FluidMapPostcard.kt`

- `object FluidMapPostcardDefaults` — I numeri della cartolina.
  - `const val Zoom: Double` — Lo zoom di serie: un quartiere, come la mappa del pannello info di Foto. A occhio, 26/11/2026.
  - `val ViewerHeight: Dp` — L'altezza nel pannello info dei visori: 160 dp, la mappa di «Informazioni» di Foto in iOS 26. A occhio, 26/11/2026.
- `@Composable fun FluidMapPostcard(point: FluidGeoPoint, modifier: Modifier = Modifier, title: String? = null, zoom: Double = FluidMapPostcardDefaults.Zoom, style: FluidMapStyle = FluidMapStyles.Explore, engine: FluidMapEngine = rememberFluidMapEngine(), shape: Shape = ContinuousCornerShape(FluidRadius.Card), markerTint: Color = Color.Unspecified, labels: FluidMapLabels = FluidMapLabels(), onClick: (() -> Unit)? = null)` — La cartolina: una mappa **piccola e ferma** di [point] (`MKMapSnapshotter`, la mappa del pannello info di Foto), col segnaposto al centro e l'attribuzione nell'angolo. Si mette in una riga o nel pannello info di un visore ([fluidViewerLocation]); toccata chiama [onClick] (di solito apre la mappa intera: con `fluidZoomSource` sulla cartolina la mappa nasce da lei).
- `fun fluidViewerLocation(point: FluidGeoPoint, title: String? = null, zoom: Double = FluidMapPostcardDefaults.Zoom, style: FluidMapStyle = FluidMapStyles.Explore, labels: FluidMapLabels = FluidMapLabels(), onOpen: (() -> Unit)? = null): FluidViewerLocation` — Il luogo di un file per il pannello info dei visori ([FluidViewerLocation]): le coordinate come le scrive Mappe, la [FluidMapPostcard] alta [FluidMapPostcardDefaults.ViewerHeight] al posto della mappa che prima disegnava l'app, e «Apri la mappa» ([onOpen]) nelle azioni del pannello e sul tocco della cartolina.
- `object FluidMapSnapshots` — La cache delle cartoline: in memoria (un sedicesimo della memoria dell'app, al piu' 16 MiB: una decina di cartoline grandi) e su disco, nella cache dell'app (al piu' [DiskEntries] file, i piu' vecchi vanno via). La chiave e' lo stile, il tema, il punto a cinque decimali, lo zoom e la misura: la stessa cartolina in un altro elenco non si rifa'.
  - `const val DiskEntries: Int` — Quante cartoline restano su disco.
  - `fun cached(request: FluidMapSnapshotRequest): ImageBitmap?` — La cartolina di [request] se e' gia' in memoria.
  - `suspend fun get(engine: FluidMapEngine, request: FluidMapSnapshotRequest, context: Context? = null): ImageBitmap?` — La cartolina di [request]: dalla memoria, dal disco o dal [engine] (che la fa), e poi tenuta. Null se il motore non la puo' fare (niente rete). Lavora fuori dal thread principale, tranne il motore, che sceglie lui.
  - `fun clearMemory()` — Svuota la memoria (il disco resta: lo pulisce il sistema con la cache dell'app).

## `engine-map/map/FluidMapSheet.kt`

- `object FluidMapSheetDefaults` — I numeri del foglio sopra la mappa.
  - `val Peek: Dp` — L'altezza del foglio basso, col campo di ricerca. Mappe di iOS 26: una capsula alta 66–67 pt a 22–23 pt dal fondo, cioe' circa 89 pt dal suo bordo alto al fondo dello schermo (la guida dell'iPhone, 1,11 px/pt, 26/11/2026; fiducia media, un'immagine sola). Qui 96 dp: il foglio dell'engine porta dentro la maniglia e lo spazio della barra dei gesti.
  - `val Detents: Set<FluidSheetDetent>` — Le tre altezze di Mappe: basso, a meta' (la mappa resta viva fin qui) e in cima.
  - `const val CopiedMillis: Long` — Quanto tempo resta «Coordinate copiate» al posto delle coordinate. A occhio, 26/11/2026.
  - `val ActionGap: Dp` — Lo spazio fra i tasti della scheda del luogo: 8 dp.
- `@Composable fun FluidMapSheet(mapState: FluidMapState, detents: Set<FluidSheetDetent> = FluidMapSheetDefaults.Detents, selectedDetent: FluidSheetDetent? = null, onSelectedDetentChange: (FluidSheetDetent) -> Unit = {}, paneTitle: String? = null, content: @Composable ColumnScope.() -> Unit)` — Il foglio sopra la mappa, come quello di Mappe in iOS 26: sempre aperto, con tre altezze ([FluidMapSheetDefaults.Detents]: basso, a meta', in cima), di vetro finche' non e' in cima, e **la mappa resta viva** fino a meta' (si trascina, si tocca: `undimmedUpTo = Medium`). Trascinato giu' sotto il detent piu' basso torna su: questo foglio non si chiude.
- `@Composable fun FluidMapGlassHost(backdrop: GlassBackdropState, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit)` — L'host dei modali **sopra la mappa**: il foglio ([FluidMapSheet]) e i pannelli dei comandi (livelli, crediti) si disegnano qui e rifrangono [backdrop], la registrazione della mappa ([FluidMap] con lo stesso `backdrop`), invece della pagina sotto. Un'app che ha gia' il suo host alla radice lo mette attorno alla pagina della mappa.
- `data class FluidMapPlace(val title: String, val point: FluidGeoPoint, val subtitle: String? = null, val detail: String? = null)` — Un luogo per la scheda: il nome, il punto, una riga sotto (l'indirizzo, «214 foto») e un dettaglio facoltativo.
- `object FluidMapPlaceTags` — I tag delle prove della scheda.
  - `const val Card: String`
  - `const val Directions: String`
  - `const val Share: String`
  - `const val Copy: String`
  - `const val Coordinates: String`
- `@Composable fun FluidMapPlaceCard(place: FluidMapPlace, modifier: Modifier = Modifier, labels: FluidMapLabels = FluidMapLabels(), onDirections: (() -> Unit)? = null, onShare: (() -> Unit)? = null, onCopy: (() -> Unit)? = null)` — La scheda del luogo, da mettere nel [FluidMapSheet]: il nome, la riga sotto, i tre tasti di Mappe e le coordinate.
- `object FluidMapActions` — Le azioni di serie della scheda del luogo, anche da usare a mano.
  - `fun directions(context: Context, place: FluidMapPlace): Boolean` — Apre l'app di mappe del telefono sul punto (`geo:` con il nome). Falso se non ce n'e' nessuna.
  - `fun share(context: Context, place: FluidMapPlace, labels: FluidMapLabels = FluidMapLabels())` — Il nome e il collegamento a OpenStreetMap del luogo, col foglio di condivisione di Android.
  - `fun copyCoordinates(context: Context, place: FluidMapPlace)` — Copia le coordinate («43.77310, 11.25560») negli appunti.

## `engine-map/map/FluidMapState.kt`

- `data class FluidMapUserLocation(val point: FluidGeoPoint, val accuracyMeters: Float? = null, val headingDegrees: Float? = null)` — La posizione di chi usa la mappa, come la da' l'app: il punto, l'incertezza in metri e la direzione (se si sa).
- `object FluidMapMotion` — I numeri del movimento della mappa. Apple non pubblica quelli di Mappe: sono **a occhio** (26/11/2026), guardando Mappe di iOS 26 sul tasto della posizione e sul tocco di un grappolo, e vicini a quelli che usa MyCloud Gallery 2 con MapLibre (450 ms).
  - `const val FlyMillis: Int` — Il volo della camera verso un punto (la posizione, un luogo scelto, un grappolo). A occhio, 26/11/2026.
  - `const val NorthMillis: Int` — Il ritorno al nord dalla bussola. A occhio, 26/11/2026.
  - `const val DoubleTapMillis: Int` — Il doppio tocco che avvicina di un passo. A occhio, 26/11/2026.
  - `const val ClusterMillis: Int` — I segnaposto che escono da un grappolo che si divide (e ci rientrano). A occhio, 26/11/2026.
  - `const val UserZoom: Double` — Lo zoom a cui porta il tasto della posizione, se la mappa e' piu' lontana: la scala di un quartiere.
- `enum class FluidMapTracking` — Se la mappa segue la posizione: [Off] (di serie, e appena il dito la sposta) o [Follow] (il tasto acceso).
- `class FluidMapState(camera: FluidMapCamera, styleId: String? = null)` — Lo stato di una [FluidMap]: dove guarda ([camera]), la posizione ([userLocation], data dall'app), se la segue ([tracking]), lo stile scelto ([styleId]) e i comandi della camera ([animateTo], [moveTo], [zoomBy], [resetNorth], [show], [centerOnUser]).
  - `var camera: FluidMapCamera by mutableStateOf(camera)` — Dove guarda la mappa, adesso.
  - `var userLocation: FluidMapUserLocation? by mutableStateOf(null)` — La posizione di chi guarda, se l'app la conosce (e ha il permesso). Null: il tasto della posizione la chiede.
  - `var tracking: FluidMapTracking by mutableStateOf(FluidMapTracking.Off)` — Se la mappa segue la posizione. Il dito che la sposta la spegne, come in Mappe.
  - `var styleId: String? by mutableStateOf(styleId)` — L'id dello stile scelto nel pannello dei livelli; null il primo.
  - `var isGestureActive: Boolean by mutableStateOf(false)` — Vero mentre il dito muove la mappa (anche nel lancio che parte dal dito).
  - `var minZoom: Double` — Lo zoom piu' lontano e il piu' vicino che il dito e i comandi raggiungono.
  - `var maxZoom: Double`
  - `var bottomObscured: Dp by mutableStateOf(0.dp)` — Quanto del fondo della mappa copre il foglio sopra (il suo detent di riposo): l'attribuzione sta sopra, e i comandi centrano i punti nella parte che si vede. Lo scrive [FluidMapSheet]; un'app con un suo pannello lo scrive lei.
  - `var sizePx: IntSize by mutableStateOf(IntSize.Zero)` — La misura della superficie in pixel e la densita', scritte dalla mappa quando cambiano.
  - `fun attach(driver: FluidMapCameraDriver)` — Il motore collega chi muove la camera (quando la superficie e' pronta).
  - `fun detach(driver: FluidMapCameraDriver)` — Il motore stacca [driver] (quando la superficie se ne va).
  - `fun report(camera: FluidMapCamera, byGesture: Boolean)` — Il motore dice dove guarda la mappa. [byGesture] vero se l'ha mossa il dito: allora la mappa smette di seguire la posizione.
  - `fun reportGesture(active: Boolean)` — Il motore dice quando il dito comincia e finisce di muovere la mappa.
  - `fun reportTap(point: FluidGeoPoint)` — Il motore dice che il dito ha toccato la mappa in [point], senza muoverla.
  - `fun projector(): FluidMapProjector?` — La proiezione della camera di adesso sulla superficie; null prima della prima misura.
  - `fun moveTo(target: FluidMapCamera)` — Porta la camera a [target] subito.
  - `suspend fun animateTo(target: FluidMapCamera, durationMillis: Int = FluidMapMotion.FlyMillis)` — Porta la camera a [target] con un volo di [durationMillis] (col movimento ridotto, un salto). Si ferma se il dito riprende la mappa.
  - `suspend fun zoomBy(steps: Double)` — Avvicina (o allontana, con [steps] negativo) di [steps] passi attorno al centro.
  - `suspend fun resetNorth()` — Riporta il nord in alto (il tocco sulla bussola).
  - `suspend fun show(points: Collection<FluidGeoPoint>, paddingDp: Double = 56.0, maxZoom: Double = 16.0)` — Mostra tutti i [points] (con [paddingDp] attorno, sopra il foglio), nord in alto. Niente se l'elenco e' vuoto.
  - `suspend fun centerOnUser(): Boolean` — Porta la mappa sulla posizione e la fa seguire ([FluidMapTracking.Follow]); vero se la posizione c'e'. Se la mappa e' piu' lontana di [FluidMapMotion.UserZoom] si avvicina fin li', se e' piu' vicina resta allo zoom che ha.
  - `companion object`
    - `val Saver: Saver<FluidMapState, Any>` — Salva camera e stile (la posizione no: la ridice l'app).
- `@Composable fun rememberFluidMapState(initial: FluidMapCamera, styleId: String? = null): FluidMapState` — Lo stato di una mappa, ricordato anche dopo la ricreazione dell'attivita'.

## `engine-map/map/FluidSketchMapEngine.kt`

- `data class FluidSketchPalette(val land: Color, val water: Color, val park: Color, val road: Color, val minorRoad: Color)` — I colori del disegno, chiaro e scuro: quelli di Liberty e Dark di OpenFreeMap, campionati dallo stile il 26/11/2026, perche' passando dal disegno alla mappa vera il colore non salti.
  - `companion object`
    - `val Light`
    - `val Dark`
- `class FluidSketchMapEngine(private val palette: (dark: Boolean) -> FluidSketchPalette = { if (it) FluidSketchPalette.Dark else FluidSke…) : FluidMapEngine` — La mappa disegnata dall'engine: terra, acqua, parchi e strade **inventati** (una griglia con le celle scelte da un numero fisso), ma fermi sul luogo, cosi' che trascinare, pizzicare e ruotare si vedano e si misurino. Niente rete e niente librerie native: e' il motore delle prove sul computer (MapLibre non si disegna in Robolectric), della galleria senza rete e dell'anteprima di una mappa.
  - `companion object`
    - `const val FlingFriction: Float` — L'attrito del lancio: quello di `exponentialDecay` a 1,5, che si ferma in circa mezzo secondo come Mappe. A occhio, 26/11/2026.
    - `const val MinFlingVelocity: Float` — Sotto questa velocita' (px/s) il dito si ferma senza lancio.

## `engine-map/map/icons/FluidIconsFilled.kt`

- `val FluidIcons.Filled.NearMe: ImageVector` — `near_me`, pieno. Nel set per: la posizione: vuota spenta, piena quando la mappa ti segue (la freccia di Mappe).

## `engine-map/map/icons/FluidIconsOutline.kt`

- `val FluidIcons.Directions: ImageVector` — `directions`, al tratto. Nel set per: «Indicazioni», la freccia che gira. Si gira da sola nelle lingue da destra a sinistra.
- `val FluidIcons.NearMe: ImageVector` — `near_me`, al tratto. Nel set per: la posizione: vuota spenta, piena quando la mappa ti segue (la freccia di Mappe).
