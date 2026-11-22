# Visori di file: FluidFileViewer e i sei preset

Il visore a tutto schermo di un file, **che si adatta a ciò che mostra**: foto (anche animate), video, audio, documento a pagine, testo e codice, e il file che non sa mostrare. Il telaio e i preset stanno in `engine-ui` (`engine-ui/.../ui/fluid/viewer/`) e **non decodificano niente**; il modulo facoltativo `engine-viewer` apre i file dal disco ([`../moduli/viewer.md`](../moduli/viewer.md)). Famiglia «Contenuto» ([`contenuto.md`](contenuto.md) ha l'indice; il visore di immagini di prima è in [`contenuto-testo-e-media.md`](contenuto-testo-e-media.md)). Firme complete: [`../api/contenuto.md`](../api/contenuto.md) (generato). Dalla 4.0 (fase F6C).

**Modello mentale in una frase.** Il telaio mostra, il modulo apre, l'app possiede: il file, il player, i permessi, «Apri con…» e gli strumenti di modifica sono dell'app; il telaio dà la pagina, i gesti, la chrome, il pannello info e il posto per lo strumento.

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| aprire una miniatura a tutto schermo, sfogliare, chiudere tornando alla miniatura | `fluidFileViewerRoute(...)` nel `FluidNavHost` + `FluidFileViewer(count, content = …)`; la miniatura ha `Modifier.fluidViewerThumbnail(...)` |
| aprire file dal disco (PDF, testo, GIF, video) senza scrivere decodificatori | `rememberFluidViewerFiles(files, …)` di `engine-viewer`, e `content = files::content` |
| mostrare un contenuto che ho già (un painter di Coil, un mio player, un mio lettore) | `FluidViewerContent.Image`, `Media`, `Pages`, `Text`, `Unknown`: il telaio sceglie il preset |
| un'immagine animata già decodificata | `fluidAnimatedImage(drawable, …)` (parte solo sulla pagina ferma) |
| fermare il mio video quando lo si sfoglia via | `currentFluidViewerPageActive()` dentro la superficie o il `renderContent` |
| un visore a tasselli (foto da 200 MP) dentro i gesti del telaio | il mio composable come `FluidViewerContent.Image`, e `currentFluidViewerZoom()` per scala e spostamento |
| le informazioni del file (nome, misure, EXIF) | `info = { i -> … }`: il gesto in su e il tasto «i» aprono lo stesso foglio |
| un mio strumento (ritaglio, testo nella foto) | `tools = { tool(id, icona, etichetta, actions = { … }) { i -> Editor(i) } }`: la barra in basso diventa la sua |
| comandi miei nella barra del visore | `toolbar = { i -> button(...) }` (la DSL della toolbar, [`toolbar.md`](toolbar.md)) |
| il visore fuori da una rotta (un overlay), con la chiusura col dito | l'overload con `onDismissRequest` |
| il visore di immagini di prima, con la sua firma | `FluidImageViewer` ([`contenuto-testo-e-media.md`](contenuto-testo-e-media.md)): oggi usa le stesse pagine del preset immagine |

## Le scelte di Alessio (21 novembre 2026)

Non si ridiscutono; il telaio le applica di serie.

- **Una rotta** è il modo di serie: il visore è una pagina vera, con indietro, collegamenti e ripristino dopo la morte del processo. La sovrapposizione resta per chi usa `FluidImageViewer`.
- **Le barre si ritirano solo al tocco**, anche durante un video: **nessun timer** (Gallery v1 le ritirava dopo 3 s, Fluidify dopo 3,5 s).
- **Il pannello info** si apre tirando su dal contenuto e col tasto «i».
- **Lo sfondo** è nero per foto e video, del tema per documenti e testo.
- **I tipi della prima versione** sono tutti e sei (immagine anche animata, video, audio, documento a pagine, testo e codice, sconosciuto). Fuori i documenti Office, gli archivi, gli ebook e il 3D: per loro il visore offre «Apri con…».
- **GIF e WebP animate le decodifica `engine-viewer`**, da un file o da un `Uri`; `engine-ui` resta senza decoder (lo controlla la build: F6C-d).

## Firma essenziale

```kotlin
fun NavGraphBuilder.fluidFileViewerRoute(
  route: String, arguments: List<NamedNavArgument> = emptyList(), deepLinks: List<NavDeepLink> = emptyList(),
  initial: (NavBackStackEntry) -> Int = { 0 },                         // il file chiesto dal collegamento
  thumbnail: (NavBackStackEntry, Int) -> FluidViewerThumbnail? = { _, _ -> null },  // dove torna il file i
  content: @Composable (NavBackStackEntry) -> Unit,
)

@Composable fun FluidFileViewer(
  count: Int,
  initial: Int = /* l'indice della rotta */,
  content: (index: Int) -> FluidViewerContent,
  toolbar: FluidToolbarScope.(index: Int) -> Unit = {},
  tools: FluidViewerToolsScope.() -> Unit = {},
  info: (@Composable (index: Int) -> Unit)? = null,
  onBack: () -> Unit = /* il back dell'host */,
  modifier: Modifier = Modifier,
  onPageChange: (Int) -> Unit = {},                                    // la pagina ferma
  strings: FluidViewerStrings = FluidViewerStrings(),                  // «2 di 5», Informazioni, Fine…
  renderContent: @Composable (FluidViewerContent, chromeVisible: Boolean) -> Unit = /* i sei preset */,
)
```

I contratti (in `FluidViewerContent.kt`, senza librerie):

| forma | che cosa dà l'app | preset |
|---|---|---|
| `FluidViewerContent.Image(aspectRatio) { … }` o `Image(painter, descrizione)` | il disegno e il rapporto d'aspetto | `FluidImagePreset` |
| `FluidViewerContent.Media(session, sampleVideo, videoSurface, artwork)` | un `FluidMediaSession` (stato, posizione, durata, play, pausa, salto); la superficie video **o** la copertina | `FluidMediaPreset` (video se c'è la superficie, audio se no) |
| `FluidViewerContent.Pages(source)` | un `FluidPageSource`: conteggio, misura, resa sospendibile di una pagina | `FluidPagesPreset` |
| `FluidViewerContent.Text(source)` | un `FluidTextSource` (righe a pezzi, lingua); `FluidStyledTextSource` per i colori | `FluidTextPreset` |
| `FluidViewerContent.Unknown(info, openWith, share)` | `FluidFileInfo` (nome, MIME, dimensione, `FluidFileKind`) e le due azioni | `FluidUnknownPreset` |

`FluidViewerContent` è un'interfaccia aperta: un `renderContent` proprio può disegnare un'altra forma, e chiamare i preset di serie per le altre (sono pubblici).

## Il telaio

- **La rotta.** `fluidFileViewerRoute` registra la destinazione con lo zoom dalla miniatura (la molla critica a 0,355 s di F5B.3) e la chiusura in giù. La voce ricorda la pagina ferma: dopo uno sfoglio la chiusura torna nella miniatura del file raggiunto, e dopo la ricreazione si riparte da lì. Un collegamento (`navDeepLink`) apre senza miniatura; `initial` lo risolve in un indice (anche da un ID di file). Se la miniatura è sparita (una griglia filtrata), l'uscita è quella senza sorgente. Una lista ancora vuota (i file arrivano dopo il collegamento) mostra «Nessun file» e tiene l'indice.
- **La chrome.** In alto «2 di 5», indietro (o Chiudi) e le azioni della `toolbar`; in basso la capsula con «i», gli strumenti e i comandi del preset («Trova», «A capo»). **Un tocco sul file** la ritira e un altro la riporta, con la grammatica della barra che si ritira di R5 V5: titolo e tasti si sfocano e svaniscono in 200 ms, tornano nitidi in 350 ms (`ViewerChromeMotion`, fiducia media, analogia). Con loro si nascondono e tornano le barre di sistema; all'uscita tornano come erano. Il contenuto non si sposta. TalkBack ha l'azione «Mostra controlli».
- **I gesti.** A scala 1 il dito di lato è del pager e quello in giù della chiusura; lo zoom (foto e video) arriva a 4×, il doppio tocco a 2,5×, e da ingrandito il dito sposta il file fino al bordo, poi passa al pager. La chiusura col dito è quella di F6B.5a: oltre 110 dp o con una spinta, verso la miniatura; un'inversione annulla. Il pizzico che stringe a scala minima chiude come in Gallery-2 (rilascio a scala ≤ 0,85, lancio ≥ 1,6 al secondo). Un preset che scorre (testo, pagine) o un comando (lo scrubber) tiene il suo dito.
- **Il pannello info.** Il foglio di vetro di F5B.2 a Medium (52%) e Large, senza velo fino a Medium: il file resta visibile sopra. Da 840 dp di larghezza effettiva è un pannello laterale (come Gallery-2). Indietro chiude prima il pannello.
- **Gli strumenti.** Scelto uno strumento, il corpo passa al compositore dell'app e la barra in basso diventa la sua: i suoi `actions` e «Fine», nella stessa capsula (il meccanismo della sostituzione di F6B.1 e F6B.2). Indietro chiude prima lo strumento; l'`id` stabile lo riapre dopo la ricreazione. Uno strumento si può dichiarare solo per certe pagine: `tools` si legge in composizione, e può guardare la pagina ferma (`onPageChange`).
- **Lo sfondo** viene dal tipo: nero per `Image` e per i video, il `surface` del tema per il resto; la chrome sul nero usa la variante scura della palette.

## I sei preset

| tipo | cosa fa il preset (engine) | cosa resta all'app |
|---|---|---|
| **Immagine** | disegna il contenuto; zoom, pager, chiusura del telaio; un'animazione (`fluidAnimatedImage`) parte solo sulla pagina ferma, si ferma lasciandola, col movimento ridotto aspetta l'azione TalkBack «Riproduci animazione» | leggere il file (o `engine-viewer`), i metadati, i tasselli, la foto in movimento |
| **Video** | un solo gruppo di comandi di vetro limpido (`Clear`, M2) sopra il video: il Play al centro compare e sparisce **per lente** (R4 B3: 430 ms in entrata, 470 in uscita), lo scrubber (`FluidScrubber`) col tempo in cifre tabulari; i comandi seguono le barre, senza timer | il player e la sua versione, la superficie (con i suoi comandi spenti), l'autoplay, **cosa fare quando lo si sfoglia via** (sotto), codec, errori, il rilascio |
| **Audio** | copertina o forma d'onda dell'app al centro, Play, ±10 s se la sessione può saltare, lo scrubber | il player, la copertina o la forma d'onda, la coda, la velocità |
| **Documento a pagine** | le pagine in colonna, pizzico fino a 4× sulla colonna intera, «3/12» che segue lo scorrimento, il foglio delle miniature che porta alla pagina; anteprime leggere mentre si scorre (lato lungo 384 px), nitide da fermo (2048 px), una cache di sei bitmap | la sorgente delle pagine (o `FluidPdfPageSource` di `engine-viewer`) |
| **Testo e codice** | monospazio e numeri di riga, lettura a pezzi da 400 righe, «a capo» (di serie sì per il testo, no per il codice), «Trova» di F6B.6 nel file intero, i colori dei ruoli col tema, «Riprova» e il file vuoto | le righe e la lingua (o `FluidTextDocument` di `engine-viewer`, che riconosce il charset e colora 19 lingue) |
| **Sconosciuto** | una scheda, **mai un vicolo cieco**: l'icona del tipo, il nome con l'estensione vera (`….apk` resta in vista), tipo e dimensione, «Apri con…» e «Condividi», l'avviso se è un eseguibile; il dump esadecimale del primo KB solo se l'app passa i byte (`firstBytes`) | le due azioni (un `ACTION_VIEW`, un `ACTION_SEND`), il permesso di leggere il file |

### Il video che si sfoglia via

Il telaio non comanda il player: dice al contenuto se la sua pagina è quella ferma. Dentro la superficie video (o nella copertina, o in un `renderContent`) `currentFluidViewerPageActive()` è vero sulla pagina ferma e falso sulle vicine e quando la si lascia. Fermarsi è la regola di Gallery-2; **riprendere al ritorno è una scelta dell'app**. La galleria «Visori» lo fa così, insieme al ciclo di vita dell'attività:

```kotlin
@Composable
fun FollowPage(player: Player, resume: Boolean, memory: Memory) {
  val active = currentFluidViewerPageActive()
  LifecycleResumeEffect(active) {
    if (active && (memory.first || resume && memory.wasPlaying)) player.play()
    memory.first = false
    onPauseOrDispose { if (active) { memory.wasPlaying = player.playWhenReady; player.pause() } }
  }
}
// FluidViewerContent.Media(session, sampleVideo = true, videoSurface = { FollowPage(...); FluidMedia3Video(session) })
```

## Cosa fa l'engine, cosa l'app

| dell'engine (`engine-ui`) | del modulo (`engine-viewer`, facoltativo) | dell'app |
|---|---|---|
| la rotta, il pager, lo zoom, la chiusura, le barre al tocco, le barre di sistema | il risolutore dal MIME e dall'estensione al preset (`FluidViewerType`), che non declassa mai un eseguibile | i file, la loro cartella, i permessi, l'`Uri` |
| il pannello info e il posto per lo strumento | il PDF (`FluidPdfPageSource`, `PdfRenderer` su un thread solo) | il contenuto del pannello, lo strumento e il suo compositore |
| i sei preset e i loro comandi | il testo (`FluidTextDocument`: charset, tetto, colori di sintassi) | il player, quando suona, quando riprende |
| `currentFluidViewerPageActive()`, `currentFluidViewerZoom()` | GIF e WebP animate (`FluidDecodedImage`) | «Apri con…», «Condividi», il dump esadecimale se lo vuole |
| — | l'adattatore di Media3 (`FluidMedia3Session`, `FluidMedia3Video`; Media3 `compileOnly`) | Media3 alla sua versione |

## Esempi

**Con il decodificatore** (`engine-viewer`): una lista di file del disco, il video e l'audio col player dell'app.

```kotlin
val ns = rememberFluidGlassNamespace()
FluidNavHost(nav, "album") {
  composable("album") {
    Box(Modifier.fluidViewerThumbnail(FluidViewerThumbnail(file.id, ns), shape).clickable { nav.navigate("file/$i") })
  }
  fluidFileViewerRoute("file/{index}", listOf(navArgument("index") { type = NavType.IntType }),
    deepLinks = listOf(navDeepLink { uriPattern = "app://file/{index}" }),
    initial = { it.arguments!!.getInt("index") }, thumbnail = { _, i -> FluidViewerThumbnail(files[i].id, ns) }) {
    // Il player è dell'app: lo crea, gli dà il file, lo rilascia. Uno per file da suonare (la galleria: VisoriMedia).
    val player = remember { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(clip.uri)) } }
    DisposableEffect(player) { onDispose { player.release() } }
    val session = rememberFluidMedia3Session(player)
    val opened = rememberFluidViewerFiles(
      files.map { FluidViewerFile(it.uri, it.name, it.mime, it.size) },
      openWith = { apriCon(it) }, share = { condividi(it) },
      media = { file, type -> if (file.uri == clip.uri) fluidMedia3Content(session, video = type.preset == FluidViewerPreset.Video) else null },
    )
    FluidFileViewer(count = opened.count, content = opened::content, onBack = { nav.popBackStack() },
      info = { i -> Dettagli(opened.document(i)) })
  }
}
```

**Senza decodificatore**: i contenuti dell'app, e il telaio sceglie il preset.

```kotlin
FluidFileViewer(count = items.size, content = { i ->
  when (val item = items[i]) {
    is Foto -> FluidViewerContent.Image(item.ratio) { AsyncImage(item.url, item.nome, Modifier.fillMaxSize()) }  // Coil
    is Clip -> FluidViewerContent.Media(item.sessione, sampleVideo = true, videoSurface = { Superficie(item) })
    is Nota -> FluidViewerContent.Text(item.righe)            // un FluidTextSource dell'app
    else -> FluidViewerContent.Unknown(FluidFileInfo(item.nome, item.mime, item.byte), openWith = { apri(item) })
  }
})
```

**Uno strumento** che prende la barra in basso (la galleria ha «Ritaglia», finto):

```kotlin
FluidFileViewer(count, content = …, tools = {
  if (isPhoto(current)) tool("ritaglia", FluidIcons.Edit, "Ritaglia", actions = { _ ->
    button("ruota", FluidIcons.Refresh, "Ruota", { crop.rotate() })
    button("formato", FluidIcons.GridView, "Formato ${crop.format}", { crop.next() })
  }) { i -> CropEditor(files[i], crop) }    // «Fine» lo aggiunge il telaio
}, onPageChange = { current = it })
```

## La galleria «Visori»

In Fluid Glass, «Da provare › Visori» o la scheda Chrome: i dieci file della prima versione (due foto, una WebP animata decodificata da `engine-viewer`, un video e un audio con ExoPlayer, un PDF di sei pagine, un testo, un file Kotlin, un `.blend` sconosciuto e un `.apk`), lo strumento «Ritaglia», il pannello info che dice chi ha aperto il file, l'interruttore «Riprendi al ritorno» per il video, il collegamento `fluidglass://visori/file/7` e il ripristino. **«Universal Converter»** è la prova in sola lettura: i tipi delle prove di quell'app che oggi finiscono nel dump esadecimale (MP4, MP3, PDF, UTF-16, DOCX, un `.wld`, il file vuoto, il file sparito) aperti dal telaio e dal risolutore; lì lo sconosciuto offre anche «Mostra i primi byte». Il codice: `sample/.../ViewerGallery.kt` e `sample/.../viewer/`.

## Trappole

- **Il player suona sotto un'altra pagina.** Il telaio non ferma niente da sé: senza `currentFluidViewerPageActive()` un video sfogliato via continua a suonare (e a costare). Vale anche per il ciclo di vita: la galleria pausa in `onPauseOrDispose`.
- **Un contenuto nuovo a ogni composizione.** `content(i)` si chiama spesso: un `FluidViewerContent` creato lì ogni volta fa ripartire il preset (il testo rilegge, le pagine rifanno la cache). Va ricordato per file, come fa `rememberFluidViewerFiles`.
- **La superficie con i suoi comandi.** Un `PlayerView` con il controller acceso dà due interfacce sullo stesso video (il difetto di Gallery v1): la superficie va con i comandi spenti, i comandi sono del preset.
- **`SurfaceView` sotto il vetro.** Sta in una finestra sua: i comandi di vetro non la vedono e mostrano il nero. Per il vetro che rifrange il video, `TextureView` (il default di `FluidMedia3Video`) e `sampleVideo = true`.
- **Un `.apk` col nome innocuo.** `FluidFileKind.from` non declassa mai un eseguibile, da qualunque fonte: la scheda ha l'avviso. Non aggirarlo passando un `kind` a mano.
- **«Apri con…» con un `file://`.** Un'altra app non può leggerlo (`FileUriExposedException`): serve un `FileProvider` o un `Uri` che l'altra app sa aprire. Se l'azione lancia un'eccezione, la scheda dice «Nessuna app può aprire questo file».
- **Robolectric non ha i decodificatori.** `PdfRenderer` (pdfium) e `ImageDecoder` non decodificano i file della galleria sulla JVM: le prove della galleria passano il contenuto di quei file; quelli veri si provano sul dispositivo (`:engine-viewer:connectedDebugAndroidTest`).

## Cosa è verificato dai test

- `FluidViewerTraceTest` (engine-ui, JVM): il fondo per tipo, la soglia e l'inversione della chiusura, il pizzico di Gallery-2, i tempi delle barre, la miniatura giusta dopo uno sfoglio.
- `FluidFileViewerUiTest` (sample, Robolectric): pizzico, sfoglio e chiusura che non litigano, info dal gesto e dal tasto, lo strumento nella capsula della shell, il video che si ritira al tocco, il collegamento e la ricreazione, le liste vuote.
- `FluidViewerPresetsTest`, `FluidViewerPresetsUiTest`: immagine (zoom, animazione sulla pagina ferma), testo (pezzi, «Trova», a capo, colori), sconosciuto (eseguibile, nome tagliato, azioni che falliscono).
- `FluidMediaPagesPresetTest`, `FluidMediaPagesPresetUiTest`: video e audio (Play, seek, durata ignota, errori), pagine (cache, miniature, zoom accessibile, riprova).
- `VisoriGalleryTest` (sample): nella galleria ogni tipo si apre col suo preset, il video si ferma quando lo si sfoglia via e riprende come sceglie l'app, lo strumento prende la barra, lo sconosciuto non è mai un vicolo cieco, la rotta col collegamento e il ripristino, i tipi di Universal Converter.
- `F6cViewerTraceTest` e le tracce `tools/glassbench/traces/f6c/` (`visore-apre`, `visore-barre-tocco`, `visore-info-su`, `visore-chiude-giu`, `visore-chiude-pizzico`, `video-comandi` contro B3, `pdf-pagine`, `testo-trova`), a 120 Hz: il movimento si giudica dai fotogrammi.

## Dove guardare

| cosa | file |
|---|---|
| la rotta, il pager, i gesti, il pannello | `engine-ui/.../ui/fluid/viewer/FluidFileViewer.kt` |
| la chrome, gli strumenti, le barre di sistema | `FluidViewerChrome.kt` |
| i contratti | `FluidViewerContent.kt` |
| il pizzico che chiude | `FluidViewerPinch.kt` |
| i preset | `FluidImagePreset.kt`, `FluidMediaPreset.kt`, `FluidPagesPreset.kt`, `FluidTextPreset.kt`, `FluidUnknownPreset.kt` |
| i decodificatori | `engine-viewer/` ([`../moduli/viewer.md`](../moduli/viewer.md)) |
| la galleria | `sample/.../ViewerGallery.kt`, `sample/.../viewer/VisoriViewer.kt`, `VisoriFiles.kt` |

**Versione.** 4.0 (F6C): telaio F6C.1, preset F6C.2 e F6C.3, modulo F6C.4, galleria e schede F6C.5.
