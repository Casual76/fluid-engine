# engine-map

**La mappa e i luoghi** (4.0, F8.26, `mappa-e-luoghi`): `Map` di MapKit per SwiftUI con i comandi di Mappe di iOS 26.
La mappa con i **comandi di vetro** (livelli, posizione, bussola), i **segnaposto anche a grappolo**, il **foglio a più
altezze** sopra la mappa con la **scheda del luogo** (Indicazioni, Condividi, Copia coordinate), e la **cartolina**: una
mappa piccola e ferma per una riga o per il pannello info di un visore (`FluidViewerLocation`), che toccata si apre.
Pacchetto `dev.antigravity.fluidengine.map`.

È un **modulo opt-in** su `engine-ui` con **una sola libreria**, MapLibre Native per Android (BSD-2-Clause), e le mappe
di **OpenStreetMap** (dati ODbL, con l'attribuzione obbligatoria, che la mappa disegna sempre) servite di serie da
**OpenFreeMap**: niente chiave, niente account. Niente Google Maps, che non è libero. La proiezione, i grappoli, i comandi,
il foglio e la cartolina sono **codice dell'engine**; la mappa vera è una superficie dietro un'interfaccia
(`FluidMapEngine`), e nelle prove sul computer la disegna il **disegno** dell'engine (`FluidSketchMapEngine`).

**L'engine** fa la mappa, i comandi, i segnaposto e i grappoli, il foglio, la scheda, la cartolina, l'attribuzione e la
posizione di sistema. **L'app** passa i punti, sceglie il fornitore delle mappe (o tiene OpenFreeMap), decide se mostrare
la posizione e chiede il permesso.

Catalogo delle firme: [`../api/map.md`](../api/map.md) (generato). Il foglio sotto è `FluidGlassSheet`
([`../componenti/menu-modali.md`](../componenti/menu-modali.md)); il pannello info dei visori è in
[`../componenti/visori.md`](../componenti/visori.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| una mappa coi punti (i «Luoghi» di MyCloud Gallery, le risposte di PampAI con dei posti) | `val state = rememberFluidMapState(FluidMapCamera(punto, 13.0))`, `FluidMap(state, markers = luoghi, onMarkerClick = { … })` |
| il foglio sopra la mappa, con la scheda del luogo scelto | `val backdrop = rememberGlassBackdrop()`, `FluidMapGlassHost(backdrop) { FluidMap(state, backdrop = backdrop, …); FluidMapSheet(state) { FluidMapPlaceCard(luogo) } }` |
| la mappa piccola e ferma in una riga (dove è stata presa una nota, il luogo di un invito) | `FluidMapPostcard(punto, Modifier.height(150.dp), title = "Piazza della Signoria", onClick = { apriMappa() })` |
| il luogo di una foto nel pannello info del visore | `FluidViewerInfo(…, location = fluidViewerLocation(punto, "Boboli") { apriMappa() })` |
| la posizione vera (col permesso dato dall'app) | `FluidSystemLocationEffect(state, enabled = permessoDato)`; `onLocationRequest = { chiediIlPermesso() }` |
| una posizione mia (simulata, o dal mio GPS) | `state.userLocation = FluidMapUserLocation(punto, accuracyMeters = 30f)` |
| muovere la camera | `state.animateTo(camera)`, `state.show(punti)`, `state.zoomBy(1.0)`, `state.resetNorth()`, `state.centerOnUser()` |
| un segnaposto disegnato da me (una miniatura della foto) | `FluidMap(…, markerContent = { cluster, scelto -> Miniatura(cluster) })` |
| un mio server di mappe (PMTiles sul cloud, un'istanza mia di OpenFreeMap) | `FluidMap(…, styles = listOf(FluidMapStyle("mio", "Mappa", uri = "https://…/style.json", attribution = …)))` |
| la mappa senza rete, nelle prove o in un'anteprima | `CompositionLocalProvider(LocalFluidMapEngine provides FluidSketchMapEngine()) { … }` |
| le coordinate scritte come Mappe, e da copiare | `FluidGeoFormat.display(punto)` («43,77310° N, 11,25560° E»), `FluidGeoFormat.copy(punto)` («43.77310, 11.25560») |
| le licenze | `FluidMapLicenses` (MapLibre, OpenStreetMap, OpenFreeMap, OpenMapTiles) nella pagina «Licenze e crediti» |

## La mappa: `FluidMap`

- **I comandi di vetro**, come Mappe di iOS 26: **in basso a destra, appoggiati sopra il foglio** (16 dp dal bordo, 10 sopra
  il foglio, e lo seguono quando si posa a un'altra altezza; col foglio in cima spariscono davvero, anche per il dito e
  per TalkBack: R3, prima erano solo trasparenti e restavano toccabili), una capsula 48 × 96 col tasto
  dei **livelli** (il pannello che nasce dal tasto, con gli stili e il loro campione) e quello della **posizione** (la
  freccia: vuota, piena quando la mappa segue), e sopra la **bussola**, che compare solo col nord fuori posto (oltre mezzo
  grado), gira con la mappa e toccata riporta il nord su per la strada breve. Le misure vengono dalla guida dell'iPhone e
  da Newsroom (`artifacts/liquid-glass/sessioni/f8-26/riferimenti/`): tasti da 47,5–49 pt, margine 16 pt, 10 pt fra gli
  elementi. Con `FluidMapControls(placement = FluidMapControlsPlacement.TopEnd)` stanno in alto a destra, come la mappa di
  «Luoghi» in Foto (e Mappe fino a iOS 18).
- **La posizione** la dà l'app (`state.userLocation`): il punto col bordo bianco, l'alone dell'incertezza e il cono della
  direzione. Senza posizione il tasto chiama `onLocationRequest` (l'app chiede il permesso: `FluidPermissionButton` di
  F8.11 sul tasto giusto); senza `onLocationRequest` il tasto non c'è. Toccato, vola sulla posizione (allo zoom di un
  quartiere, 15, se la mappa è più lontana) e la **segue**; il dito che sposta la mappa smette di seguirla.
- **I segnaposto** (`FluidMapMarker`) sono il pallino di `Marker` (30 dp col simbolo, 46 quando scelto) col nome sotto, col
  contorno del colore della pagina perché si legga su ogni mappa. Il punto è la punta.
- **I grappoli** (`FluidMapClusterIndex`, l'algoritmo di Supercluster): i segnaposto entro 48 dp sullo schermo diventano
  un cerchio col numero (da 34 a 56 dp col logaritmo del numero), che toccato vola fino allo zoom in cui si divide. Si
  calcolano una volta per elenco (fuori dal thread principale: diecimila segnaposto in qualche decina di millisecondi) per
  tutti i livelli da 0 a 18; gli id sono stabili (lo stesso insieme, lo stesso id), e un grappolo che non cambia non si
  anima. **Il viaggio d'oro**: quando lo zoom passa di livello i segnaposto **escono dal grappolo da cui nascono** e
  allontanando **ci rientrano**, in 280 ms (a occhio).
- **L'attribuzione** è sempre in vista in basso a sinistra, sopra il foglio, su un vetro piccolo («OpenFreeMap
  © OpenMapTiles © OpenStreetMap»: i tre nomi che chiedono i termini di OpenFreeMap e l'ODbL), e toccata mostra i
  crediti coi collegamenti (ODbL: openstreetmap.org/copyright a un tocco). Su un telefono stretto va a capo fra un nome e
  l'altro (il «©» resta attaccato al suo nome) e non passa mai sotto i comandi; se nemmeno in due righe ci sta (il
  carattere di sistema grande: a 2× su un telefono da 360 dp ne vorrebbe tre, e la terza era «© OpenStreetMap») resta la
  forma compatta, «© OpenStreetMap», come sulla cartolina (R3). TalkBack la legge **una volta**, come un
  tasto: «Crediti della mappa: OpenFreeMap © OpenMapTiles © OpenStreetMap» (F8.26c; prima la scritta del vetro si
  aggiungeva e i crediti si sentivano due volte).
- **Il luogo scelto si vede** (F8.26d), come in Mappe: con `selectedMarkerId` la mappa porta il segnaposto in vista da
  sé. Se il foglio lo copre, se sta sotto le barre o fuori dallo schermo lo centra nella parte che si vede (sopra il
  foglio, all'altezza a cui il foglio si posa); se sta dentro un grappolo avvicina fin dove è da solo; se si vede già la
  mappa non si muove. L'app sceglie e basta (non serve un `animateTo` suo).
- **I segnaposto stanno sopra il vetro dei crediti** (F8.26d): un grappolo che passa accanto ai crediti non finisce
  sotto il vetro. I comandi restano sopra tutto.
- **Il tasto della posizione** per TalkBack è «Posizione», anche prima che l'app la sappia (il tocco la chiede), e
  «Posizione, la mappa ti segue» quando la segue. Fino alla F8.26b prima del tocco diceva «Posizione non disponibile».
- **Il dito.** La mappa vera la muove MapLibre; il disegno ha gli stessi gesti: un dito trascina e lancia, due dita
  pizzicano e ruotano attorno al punto fra le dita, il doppio tocco avvicina di un passo. Il punto toccato **resta sotto
  il dito**. Niente inclinazione.

**Prestazioni.** La camera cambia a ogni fotogramma e si legge solo nella misura e nel disegno: la mappa che scorre **non
ricompone niente**. I segnaposto si ricompongono solo quando cambia il livello o la mappa esce dalla finestra dei
grappoli (un raggio di schermo e mezzo attorno a una cella del livello); la bussola solo quando compare o sparisce; il
numero letto da TalkBack solo quando cambia. A camera ferma niente ridisegna.

**Il vetro.** I comandi, il foglio e i pannelli rifrangono la mappa solo se il motore la disegna dentro Compose
(`FluidMapEngine.glassSamplesMap`): il disegno sempre, MapLibre con `FluidMapRenderMode.Texture` (una `TextureView`).

**`TextureView` per tutti, col limite dei fotogrammi di serie.** In una `TextureView` MapLibre non aspetta lo schermo:
sul Tab S9 col dito che trascina disegnava **252 fotogrammi al secondo** su uno schermo da 120. Di serie
`FluidMapLibreEngine` gli mette il limite del ritmo più alto dello schermo (`maximumFps =
FluidMapLibreEngine.screenMaximumFps(context)`, `MapView.setMaximumFps`; `maximumFps = 0` lo toglie). Misurato sul Tab S9
(26/11/2026, tre corse alternate per modo e una traccia): col limite **112 fotogrammi al secondo**, regolari (uno ogni
8,2 ms), il filo di MapLibre al **52%** di un core invece del 76%, e gli stessi fotogrammi in ritardo (2 su 636; in
`gfxinfo` 0,30–0,44% in tutti e due i modi). **Per i telefoni deboli** il modo leggero è questo stesso con un vetro
semplice sopra (la sfocatura con la tinta, senza rifrazione: la scala del vetro), non la `SurfaceView`. Il resto del
costo di `Texture` è nel RenderThread dell'app (49% contro 27% della `SurfaceView`): la tessera copiata nella finestra e
il vetro che la rifrange.

**`Surface` solo a mano.** Con `FluidMapRenderMode.Surface` (una `SurfaceView`, in una finestra sua) il vetro non vede
la mappa: sopra si vede chiaro, con la tinta e il filo ma senza sfocatura. **Nessuna fascia la sceglie.** Fino alla
F8.26b era **nera**: `FluidMap` la teneva dentro la registrazione del vetro (`glassBackdropSource`, che quando il vetro
la campiona si compone in un layer fuori schermo), e il buco che la `SurfaceView` apre nella finestra restava chiuso lì
dentro. Dalla F8.26c una mappa che il vetro non vede sta **fuori** dalla sorgente, sotto, e sul Tab S9 si vede. Le
misure dei tre modi sono nel rapporto di F8.26 (`artifacts/liquid-glass/sessioni/f8-26/rapporto.md`).

Il foglio e i pannelli si disegnano nell'host dei modali: perché
rifrangano la mappa e non la pagina sotto, l'host va messo **sopra la mappa** con la stessa registrazione
(`FluidMapGlassHost(backdrop)` e `FluidMap(…, backdrop = backdrop)`).

**Dito nitido, app sfocata.** Niente di ciò che muove il dito si sfoca: né la mappa né i segnaposto, nemmeno nel lancio.
Si sfoca solo ciò che arriva da sé: la cartolina che entra la prima volta.

**TalkBack.** La mappa dice quanti luoghi ha in vista («Mappa, 5 luoghi in vista») e ha le azioni «Ingrandisci»,
«Riduci» e «Mostra tutti i luoghi»; ogni segnaposto è un tasto col nome e la riga sotto, «Scelto» quando lo è; un
grappolo dice «12 luoghi» con l'azione «Avvicina»; la bussola dice i gradi. **Riduci movimento**: i voli della camera
diventano salti, i grappoli cambiano senza viaggio, la bussola compare senza crescere. Le parole sono in `FluidMapLabels`
(italiano di serie, `FluidMapLabels.English`).

## Lo stato: `FluidMapState`

`rememberFluidMapState(FluidMapCamera(centro, zoom, bearing))` (salvato nella ricreazione: camera e stile).
`FluidMapCamera` ha lo **zoom di MapLibre** (0 la Terra in 512 dp, ogni passo il doppio) e la direzione in gradi da nord.

| | |
|---|---|
| `camera` | dove guarda adesso; **cambia a ogni fotogramma**: leggerla in disegno o misura, o con un `derivedStateOf` |
| `userLocation`, `tracking` | la posizione data dall'app; se la mappa la segue (`FluidMapTracking.Off`/`Follow`) |
| `styleId` | lo stile scelto nei livelli |
| `bottomObscured` | quanto copre il foglio (lo scrive `FluidMapSheet`): l'attribuzione sta sopra, i voli centrano sopra |
| `animateTo`, `moveTo`, `zoomBy`, `resetNorth`, `show(punti)`, `centerOnUser()` | i comandi della camera (450 ms, 350 per il nord, 300 per il doppio tocco: a occhio) |
| `projector()` | la proiezione di adesso (`FluidMapProjector`: dallo schermo alla Terra e ritorno) |
| `attach`, `report`, `reportGesture`, `reportTap` | per chi scrive un motore |

## Il foglio e la scheda del luogo

`FluidMapSheet(state) { … }` è `FluidGlassSheet` con le tre altezze di Mappe: **96 dp** (il foglio ridotto col campo di
ricerca), **a metà** e **in cima**; di vetro finché non è in cima, e **la mappa resta viva fino a metà**
(`undimmedUpTo = Medium`). Trascinato sotto il detent più basso **torna su**: questo foglio non si chiude (dal 4.0 il
foglio di vetro dell'engine torna al detent più basso quando l'app lo tiene aperto). Dice alla mappa quanto la copre.

`FluidMapPlaceCard(FluidMapPlace(nome, punto, sotto))`: il nome, la riga sotto, **Indicazioni** (pieno: apre l'app di
mappe del telefono con `geo:`), **Condividi** (il nome e il collegamento a OpenStreetMap, col foglio di Android) e **Copia**
(per TalkBack solo «Copia coordinate»: «43.77310, 11.25560» negli appunti, e «Coordinate copiate» al posto delle coordinate per 1,6 s, detto da
TalkBack). Ogni tasto si può sostituire. **Le parole restano intere**: se un'etichetta non sta accanto alla sua icona (i
tre tasti sul telefono), in tutti e tre l'icona sale e l'etichetta le sta sotto, su una riga, come in Mappe
(`FluidButtonLabelPlacement.Below`); sul tablet restano accanto. Fino alla F8.26b il telefono leggeva «Indicaz|ioni».

## La cartolina: `FluidMapPostcard`

Una mappa **piccola e ferma** (`MKMapSnapshotter`, la mappa del pannello info di Foto): il segnaposto al centro,
l'attribuzione nell'angolo, la forma della card. **Ferma vuol dire che non costa**: l'immagine si fa una volta (MapLibre
`MapSnapshotter`, alla densità dello schermo), fuori dal thread principale, e si tiene in memoria (al più 16 MiB) e su
disco (240 cartoline nella cache dell'app, `FluidMapSnapshots`). La prima volta entra sfocata e si mette a fuoco
(`FluidAsyncImage`); dalla cache c'è già. Senza rete resta il colore dello stile col segnaposto. TalkBack la legge «Mappa
di Piazza della Signoria» con l'azione «Apri la mappa».

**I crediti della cartolina** (F8.26d) stanno nell'angolo su un fondo chiaro o scuro, così si leggono anche sopra i nomi
della mappa, e **su una riga che non va a capo**. Se la scritta corta non ci sta resta la forma compatta
(`FluidMapAttribution.compact`, di serie «© OpenStreetMap»: il credito che l'ODbL chiede in vista), che scende fino a
7 sp; il resto è **dietro un tocco** sui crediti, che apre il popover coi collegamenti, come «Legale» nelle mappe di
Apple. La cartolina più stretta che tiene i crediti è `FluidMapPostcardDefaults.MinWidth` (80 dp), **a ogni grandezza
del carattere di sistema**: col carattere grande la scritta cresce dove c'è posto e nella cartolina stretta scende fino
ai 7 dp veri di 1× (R3: a 1,3× i 7 sp erano 9 dp e la scritta si tagliava). Le prove lo tengono fermo a 1, 1,3 e 2. Per TalkBack i crediti sono un tasto a parte, «Crediti della mappa». Fino alla F8.26c la scritta andava a capo in
tre pezzi e la cartolina di una riga ne mostrava due, senza «© OpenStreetMap».

`fluidViewerLocation(punto, nome, onOpen)` dà il `FluidViewerLocation` del pannello info dei visori: le coordinate come
Mappe, la cartolina alta 160 dp al posto della mappa che prima disegnava l'app, e «Apri la mappa».

## La posizione e il permesso

Il modulo **non dichiara** il permesso della posizione: lo dichiara l'app che la mostra.

```xml
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<!-- solo se serve il punto esatto: -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

`FluidSystemLocation` legge `LocationManager` senza Play Services (il «fused» di Android 12, poi GPS e rete):
`FluidSystemLocationEffect(state, enabled)` tiene `state.userLocation` aggiornata mentre la mappa si vede. Senza
permesso non dà niente e non va in errore.

**Una posizione vecchia non entra come attuale** (R3): `updates()` comincia dall'ultima posizione conosciuta solo se ha
al più 30 secondi (`LastKnownMaxAgeMillis`, il confine con cui Android dice «attuale» una posizione in memoria), contati
sull'orologio che non torna indietro; una di ieri non diventa il punto blu e la mappa che segue non ci vola. Ogni
posizione porta il suo istante, `FluidMapUserLocation.timeMillis` (null se l'app non lo sa); `last()` dà l'ultima
conosciuta com'è, anche vecchia, e l'istante lo dice.

## Le mappe di OpenFreeMap

Gli stili di serie (`FluidMapStyles.OpenFreeMap`) sono **Esplora** (Liberty, di notte Dark), **Essenziale** (Positron,
di notte Dark) e **Vivace** (Bright, di notte Fiord), da `https://tiles.openfreemap.org/styles/…`. I termini del servizio
(openfreemap.org, letti il 26/11/2026): gratis, senza chiave, senza account, senza limiti di visite, anche per uso
commerciale; l'attribuzione «OpenFreeMap © OpenMapTiles Data from OpenStreetMap»; i download per l'uso senza rete al più
di 3 000 tessere e 50 MB l'uno, con al più 4 richieste insieme e uno User-Agent che dice l'app; il servizio è «così com'è»,
passa da Cloudflare e può cambiare o fermarsi. Un'app che non vuole dipenderne passa i suoi stili (la sua istanza, un file
PMTiles sul suo cloud).

## Cosa costa

MapLibre 13.7.1 (`android-sdk-opengl`, la stessa di MyCloud Gallery 2 e FluidWeather) porta `libmaplibre.so`: **11,0 MB**
in arm64-v8a, 8,1 MB in armeabi-v7a, 11,2 MB in x86 e 11,4 MB in x86_64 (non compressa, dall'AAR); un'app per telefono
che tiene solo arm64-v8a (`abiFilters`) paga 11 MB. A runtime porta OkHttp 4.12.0, Timber, i gesti, geojson e turf di
MapLibre. Il DEX misurato nella bench della galleria è nel rapporto di F8.26.

## Trappole

- *Sintomo*: il foglio e i pannelli rifrangono la pagina sotto, non la mappa. → Manca `FluidMapGlassHost(backdrop)` con la
  stessa `backdrop` di `FluidMap`: l'host della radice registra la pagina, non la mappa.
- *Sintomo*: i comandi di vetro neri o vuoti. → La mappa in una `SurfaceView` (`FluidMapRenderMode.Surface`): il vetro
  campiona solo una `TextureView` o Compose.
- *Sintomo*: nella cartolina manca «© OpenStreetMap». → La cartolina è più stretta di `FluidMapPostcardDefaults.MinWidth`
  (80 dp), o uno stile dell'app ha una `FluidMapAttribution` senza `compact`: la forma compatta deve dire almeno il
  credito che la licenza dei dati chiede.
- *Sintomo*: la mappa nera, con i segnaposto e i comandi sopra. → Una `SurfaceView` dentro un layer fuori schermo (la
  registrazione del vetro, un `graphicsLayer` con `CompositingStrategy.Offscreen` o con l'alfa): il buco nella finestra
  non si apre. `FluidMap` la tiene fuori dalla sua sorgente (F8.26c); fuori da `FluidMap` non va messa dentro un layer.
- *Sintomo*: la lista dei segnaposto si ricompone a ogni fotogramma. → Una lettura di `state.camera` nella composizione:
  va nel disegno, o in un `derivedStateOf` che cambia di rado.
- *Sintomo*: la mappa è bianca nelle prove. → MapLibre non si disegna in Robolectric: le prove usano
  `LocalFluidMapEngine provides FluidSketchMapEngine()`.
- *Sintomo*: il tasto della posizione non c'è. → Senza `userLocation` e senza `onLocationRequest` non serve a niente, e
  non si mostra.
- *Sintomo*: l'attribuzione e i comandi coperti dal foglio. → Un foglio dell'app che non dice `state.bottomObscured`: lo
  scrive solo `FluidMapSheet`.

## Come si prova

- `FluidMapCoreTest` (JVM): la proiezione contro i valori di MapLibre, lo schermo e la Terra andata e ritorno anche
  ruotati, il dito che trascina e il pizzico che tiene fermo il punto fra le dita, `show` che mette tutto dentro, la
  bussola per la strada breve, i grappoli che non perdono né raddoppiano niente a nessun livello, deterministici in
  qualunque ordine, che si dividono dove promettono, oltre l'antimeridiano, diecimila in poco tempo, le coordinate scritte
  e da copiare, l'attribuzione, la chiave della cartolina.
- `FluidMapUiTest` (Robolectric, sul disegno, col dito finto): trascinare (i segnaposto seguono al pixel), pizzicare, il
  doppio tocco; la posizione chiesta, raggiunta e seguita finché il dito la sposta; la bussola; i livelli dal pannello; i
  grappoli che si aprono; la scelta di un segnaposto con TalkBack; l'attribuzione sopra il foglio coi crediti; le azioni
  di TalkBack; la cartolina e il luogo del visore; il movimento ridotto.
- `F8p26PlacesHealthUiTest` (galleria, Robolectric): il foglio che torna giù invece di chiudersi, la scheda con «Copia
  coordinate», la posizione simulata, la cartolina che apre la mappa.
- La galleria: «Elementi nuovi › Luoghi e salute» (`mappa`, `mappa-disegno`, `cartolina`, `cartolina-visore`), con
  `--es novita.map texture|surface|disegno` per le misure.

## Dove guardare

| cosa | file |
|---|---|
| la mappa, i segnaposto in posizione, il punto della posizione | `engine-map/.../map/FluidMap.kt` |
| i comandi di vetro, la bussola, i livelli, l'attribuzione | `FluidMapControls.kt` |
| il pallino e il grappolo | `FluidMapMarkers.kt` |
| i grappoli | `FluidMapClusters.kt` |
| punti, camera, proiezione, coordinate | `FluidGeo.kt` |
| lo stato e i voli | `FluidMapState.kt` |
| gli stili, l'attribuzione, il motore, le parole | `FluidMapEngine.kt` |
| MapLibre (superficie, ciclo di vita, cartolina) | `FluidMapLibreEngine.kt` |
| il disegno e i suoi gesti | `FluidSketchMapEngine.kt` |
| il foglio, l'host dei modali, la scheda del luogo | `FluidMapSheet.kt` |
| la cartolina, la cache, il luogo del visore | `FluidMapPostcard.kt` |
| la posizione di sistema, le licenze | `FluidMapLocation.kt` |
| le icone (freccia, indicazioni) | `engine-map/icons/icone-map.txt` → `.../map/icons/` |
