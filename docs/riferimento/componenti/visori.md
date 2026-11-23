<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/componenti/visori.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# Visori di file: FluidFileViewer, i preset «D · Mix» e il pannello info

Il visore a tutto schermo di un file, **che si adatta a ciò che mostra**: foto (anche animate), video, audio, documento a pagine, testo e codice, griglia (fogli e CSV), una pagina disegnata da un modulo (archivio, albero JSON, libro, font, 3D…) e il file che non sa mostrare. Il telaio e i preset stanno in `engine-ui` (`engine-ui/.../ui/fluid/viewer/`) e **non decodificano niente**; i moduli facoltativi aprono i file dal disco: `engine-viewer` per i tipi di serie e il registro, un modulo per famiglia per il resto ([`../moduli/viewer.md`](../moduli/viewer.md), [`../moduli/viewer-famiglie.md`](../moduli/viewer-famiglie.md)). Famiglia «Contenuto» ([`contenuto.md`](contenuto.md) ha l'indice; il visore di immagini di prima è in [`contenuto-testo-e-media.md`](contenuto-testo-e-media.md)). Firme complete: [`../api/contenuto.md`](../api/contenuto.md) (generato). Dalla 4.0 (fasi F6C e F6D).

**Modello mentale in una frase.** Il telaio mostra, il modulo apre, l'app possiede: il file, il player, i permessi, «Apri con…» e gli strumenti di modifica sono dell'app; il telaio dà la pagina, i gesti, la chrome, il pannello info e il posto per lo strumento.

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| aprire una miniatura a tutto schermo, sfogliare, chiudere tornando alla miniatura | `fluidFileViewerRoute(...)` nel `FluidNavHost` + `FluidFileViewer(count, content = …)`; la miniatura ha `Modifier.fluidViewerThumbnail(...)` |
| aprire file dal disco (PDF, testo, GIF, video) senza scrivere decodificatori | `rememberFluidViewerFiles(files, …)` di `engine-viewer`, e `content = files::content` |
| aprire anche DOCX, ZIP, Markdown, JSON, HEIC, SVG, font, modelli 3D | aggiungere il modulo della famiglia (o `engine-viewer-all`): si registra da solo e `rememberFluidViewerFiles` lo usa ([`../moduli/viewer-famiglie.md`](../moduli/viewer-famiglie.md)) |
| la miniatura di un file nella griglia (il contenuto in piccolo, come File di iOS) | `rememberFluidFileThumbnail(file, sizePx)` e `FluidFileThumbnailImage(it)`; fuori da Compose `FluidFileThumbnails.load(context, file, sizePx)` (cache, tempo massimo, tetto di memoria) |
| mostrare un contenuto che ho già (un painter di Coil, un mio player, un mio lettore) | `FluidViewerContent.Image`, `Media`, `Pages`, `Text`, `Grid`, `Custom`, `Unknown`: il telaio sceglie il preset |
| un foglio di calcolo o una tabella | `FluidViewerContent.Grid(source)` con un `FluidGridSource` (fogli, righe e colonne bloccate, celle a blocchi) |
| una pagina mia dentro il visore (un albero, una scheda, un provino) | `FluidViewerContent.Custom(background, find, actions) { … }`: la chrome, «Trova» e lo spazio fra le barre sono del telaio |
| un'immagine animata già decodificata | `fluidAnimatedImage(drawable, …)` (parte solo sulla pagina ferma) |
| fermare il mio video quando lo si sfoglia via | `currentFluidViewerPageActive()` dentro la superficie o il `renderContent` |
| un visore a tasselli (foto da 200 MP) dentro i gesti del telaio | il mio composable come `FluidViewerContent.Image`, e `currentFluidViewerZoom()` per scala e spostamento |
| le informazioni del file nel pannello di «D · Mix» | `FluidViewerInfoProvider(info = { i -> FluidViewerInfo(…) })` attorno al visore; per un file di famiglia `officeInfo()`, `fluidArchiveViewerInfo()`, `fluidDataViewerInfo(…)` |
| la durata dell'audio prima di Play | una sessione che implementa `FluidMediaDurationLoader` (lo fa `FluidMedia3Session`) |
| un mio strumento (ritaglio, testo nella foto) | `tools = { tool(id, icona, etichetta, actions = { … }) { i -> Editor(i) } }`: la barra in basso diventa la sua |
| comandi miei nella barra del visore | `toolbar = { i -> button(...) }` (la DSL della toolbar, [`toolbar.md`](toolbar.md)) |
| Rinomina, Sposta, Duplica, Elimina nel menu del nome in cima | `fileActions = { i -> files.fileActions(i, FluidViewerFileCommands(rename = …, delete = …)) }` (sotto, «Il titolo e il menu del file») |
| le azioni del mio tipo in «•••» (una famiglia, una pagina `Custom`) | `viewerTypeActions { listOf(FluidContextAction(…) { … }) }` nelle `actions` della pagina (sotto, «Le azioni del tipo in «•••»») |
| il visore fuori da una rotta (un overlay), con la chiusura col dito | l'overload con `onDismissRequest` |
| il visore di immagini di prima, con la sua firma | `FluidImageViewer` ([`contenuto-testo-e-media.md`](contenuto-testo-e-media.md)): oggi usa le stesse pagine del preset immagine |

## Le scelte di Alessio

Non si ridiscutono; il telaio le applica di serie.

**21 novembre 2026.**
- **Una rotta** è il modo di serie: il visore è una pagina vera, con indietro, collegamenti e ripristino dopo la morte del processo. La sovrapposizione resta per chi usa `FluidImageViewer`.
- **Le barre si ritirano solo al tocco**, anche durante un video: **nessun timer** (Gallery v1 le ritirava dopo 3 s, Fluidify dopo 3,5 s).
- **Il pannello info** si apre tirando su dal contenuto e col tasto «i».
- **Lo sfondo** è nero per foto e video, del tema per documenti e testo.
- **GIF e WebP animate le decodifica `engine-viewer`**, da un file o da un `Uri`; `engine-ui` resta senza decoder (lo controlla la build: F6C-d).

**22 novembre 2026 (la seconda ondata).**
- **Il visore è universale, a moduli.** Office, archivi, testi ricchi e dati, immagini speciali e font, 3D (e l'AR) sono famiglie in moduli facoltativi che si registrano presso `engine-viewer`: un'app che non ne aggiunge uno non ne paga il peso, e quel tipo resta sconosciuto con «Apri con…».
- **L'aspetto è «D · Mix»**: la grammatica di A (Quick Look di iOS 26: il contenuto da solo, la chrome in cerchi e capsule), i tocchi di C dove non c'è niente da leggere, e il pannello info con le informazioni ricche di B (`docs/liquid-glass/ricerca/visori-2.md` §5.1–5.2).
- **Ogni file che non è foto o video si apre dalla sua miniatura** e ci torna, anche col dito; la pagina sotto si scurisce (F6D).
- **Office «a pagine come il documento»**: i documenti impaginati come in Word, i fogli in griglia, le slide una per pagina; i caratteri con le misure di Word, solo latino.
- **3D con Filament**, archivi con libarchive; **l'AR con ARCore** in un modulo a parte, che chiede la fotocamera.

## Firma essenziale

```kotlin
fun NavGraphBuilder.fluidFileViewerRoute(
  route: String, arguments: List<NamedNavArgument> = emptyList(), deepLinks: List<NavDeepLink> = emptyList(),
  initial: (NavBackStackEntry) -> Int = { 0 },                         // il file chiesto dal collegamento
  thumbnail: (NavBackStackEntry, Int) -> FluidViewerThumbnail? = { _, _ -> null },  // dove nasce e torna il file i
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
  fileActions: @Composable (index: Int) -> FluidViewerFileActions? = { null },  // F6C.19: il menu del file
  renderContent: @Composable (FluidViewerContent, chromeVisible: Boolean) -> Unit = /* i preset */,
)

data class FluidViewerFileActions(                                     // F6C.19, engine-ui: una voce c'è solo se non è null
  val share: (() -> Unit)? = null, val openWith: (() -> Unit)? = null, val copy: (() -> Unit)? = null,
  val print: (() -> Unit)? = null, val rename: (() -> Unit)? = null, val move: (() -> Unit)? = null,
  val duplicate: (() -> Unit)? = null, val delete: (() -> Unit)? = null,
  val more: List<FluidViewerInfoAction> = emptyList(),                 // le voci dell'app senza un posto fisso
)

// engine-viewer: le voci che l'engine sa fare dall'indirizzo del file, più quelle dell'app legate al file della pagina
data class FluidViewerFileCommands(rename, move, duplicate, delete: ((FluidViewerFile) -> Unit)? = null,
  more: ((FluidViewerFile) -> List<FluidViewerInfoAction>)? = null, shareableUri: ((FluidViewerFile) -> Uri?)? = null)
@Composable fun FluidViewerFiles.fileActions(index: Int, commands: FluidViewerFileCommands = FluidViewerFileCommands()): FluidViewerFileActions?

@Composable fun FluidViewerInfoProvider(info: @Composable (Int) -> FluidViewerInfo?, content: @Composable () -> Unit)
```

I contratti (in `FluidViewerContent.kt`, senza librerie):

| forma | che cosa dà l'app o il modulo | preset |
|---|---|---|
| `FluidViewerContent.Image(aspectRatio) { … }` o `Image(painter, descrizione)` | il disegno e il rapporto d'aspetto | `FluidImagePreset` |
| `FluidViewerContent.Media(session, sampleVideo, videoSurface, artwork)` | un `FluidMediaSession` (stato, posizione, durata, play, pausa, salto); la superficie video **o** la copertina | `FluidMediaPreset` (video se c'è la superficie, audio se no) |
| `FluidViewerContent.Pages(source)` | un `FluidPageSource`: conteggio, misura, resa sospendibile di una pagina; dalla seconda ondata anche il foglio di ogni pagina (`sheet(i)` → `FluidPageSheet`: misura in punti, margini, colore della carta) e un conteggio che cresce mentre il documento si impagina (`countFinal`) | `FluidPagesPreset` |
| `FluidViewerContent.Text(source)` | un `FluidTextSource` (righe a pezzi, lingua); `FluidStyledTextSource` per i colori | `FluidTextPreset` |
| `FluidViewerContent.Grid(source)` | un `FluidGridSource`: i fogli (`FluidGridSheet`: righe, colonne, bloccate, larghezze, unioni `FluidGridRange`, colore della scheda) e le celle a blocchi (`FluidGridCell` con `FluidGridStyle` e `FluidGridBorders`) | `FluidGridPreset` |
| `FluidViewerContent.Custom(background, find, actions, firstFrameReady) { … }` | la pagina del modulo (`FluidViewerCustomScope`: `chromeVisible`, `finding`, `active`, `contentPadding`), il fondo (`FluidViewerBackground.Theme` o `Black`), «Trova» (`FluidViewerFind`), le azioni della barra | `FluidCustomPreset` |
| `FluidViewerContent.Unknown(info, openWith, share)` | `FluidFileInfo` (nome, MIME, dimensione, `FluidFileKind`) e le due azioni | `FluidUnknownPreset` |

`FluidViewerContent` è un'interfaccia aperta: un `renderContent` proprio può disegnare un'altra forma, e chiamare i preset di serie per le altre (sono pubblici). `Grid` e `Custom` sono di F6C.8: il contratto non era ancora uscito in una versione, quindi aggiungerle non ha rotto nessuno.

## Il telaio

- **La rotta.** `fluidFileViewerRoute` registra la destinazione con lo zoom dalla miniatura (la molla critica a 0,355 s di F5B.3) e la chiusura in giù. La voce ricorda la pagina ferma: dopo uno sfoglio la chiusura torna nella miniatura del file raggiunto, e dopo la ricreazione si riparte da lì. Un collegamento (`navDeepLink`) apre senza miniatura; `initial` lo risolve in un indice (anche da un ID di file). Se la miniatura è sparita (una griglia filtrata), l'uscita è quella senza sorgente. Una lista ancora vuota (i file arrivano dopo il collegamento) mostra «Nessun file» e tiene l'indice.
- **La chrome.** In alto indietro, il nome del file **senza estensione** con «⌄» (apre il menu del file, F6C.19), «2 di 5» sotto e il tasto «i»; in basso «•••» con le azioni del tipo, la capsula con l'azione principale del tipo e condividi, più gli strumenti e i comandi del preset («Trova», «A capo»). **Un tocco sul file** la ritira e un altro la riporta, con la grammatica della barra che si ritira di R5 V5: titolo e tasti si sfocano e svaniscono in 200 ms, tornano nitidi in 350 ms (`ViewerChromeMotion`, fiducia media, analogia). Con loro si nascondono e tornano le barre di sistema; all'uscita tornano come erano. Il contenuto non si sposta. TalkBack ha l'azione «Mostra controlli».
- **I gesti.** A scala 1 il dito di lato è del pager e quello in giù della chiusura; lo zoom (foto e video) arriva a 4×, il doppio tocco a 2,5×, e da ingrandito il dito sposta il file fino al bordo, poi passa al pager. La chiusura col dito è quella di F6B.5a: oltre 110 dp o con una spinta, verso la miniatura; un'inversione annulla. Il pizzico che stringe a scala minima chiude come in Gallery-2 (rilascio a scala ≤ 0,85, lancio ≥ 1,6 al secondo). Un preset che scorre (testo, pagine, griglia) o un comando (lo scrubber) tiene il suo dito. Una pagina `Custom` nera (un modello 3D) non è zoomata dal telaio e tiene i suoi gesti.
- **Gli strumenti.** Scelto uno strumento, il corpo passa al compositore dell'app e la barra in basso diventa la sua: i suoi `actions` e «Fine», nella stessa capsula (il meccanismo della sostituzione di F6B.1 e F6B.2). Indietro chiude prima lo strumento; l'`id` stabile lo riapre dopo la ricreazione. Uno strumento si può dichiarare solo per certe pagine: `tools` si legge in composizione, e può guardare la pagina ferma (`onPageChange`).
- **Lo sfondo** viene dal tipo: nero per `Image` e per i video, il `surface` del tema per il resto; una `Custom` lo dichiara. La chrome sul nero usa la variante scura della palette.

## I preset «D · Mix»

Con le informazioni di `FluidViewerInfoProvider`, le pagine di lettura usano le misure della tavola D (F6C.18).
I controlli di vetro sono da 44 dp con margine da 14 dp e la colonna di lettura arriva a 552 dp. In basso
«Altro» (le azioni del tipo, F6C.19), i comandi del tipo e «Condividi» hanno posti distinti. Registra «Condividi» nel
blocco `toolbar` come azione `BottomBar` con l'icona `FluidIcons.IosShare` o `FluidIcons.Share`.
Nel testo le due capsule di lettura e il tondo di condivisione sono al centro, come nella tavola, senza «Altro». Nell'archivio
la lente occupa il posto a destra, «Altro» tiene Ordina, Filtra e Codifica dei nomi, e la condivisione è nel menu del nome.

**La base è A**: il contenuto pulito come Quick Look, sul fondo del tema per documenti, testo, dati, griglia e slide (la carta dei documenti Office resta bianca anche in scuro). **Da C vengono i tocchi, solo dove non c'è niente da leggere:**

- **la tinta del file** su audio, eseguibile, sconosciuto e archivio, **mai** su documenti, testo, dati, griglia, slide. Il colore viene dalla copertina o dall'icona (il colore più frequente di un'immagine ridotta a 8 × 8), oppure dal tono di `FluidFileKind`. Il velo dell'audio e dello sconosciuto usa il 20% per il file e il 16% per i campi in basso (`ViewerMixProfile`). L'avviso e il pannello audio hanno una superficie piena per ogni tema, campionata dalla tavola D. Con «Riduci trasparenza» o col contrasto alto di sistema la tinta e l'alone spariscono;
- **l'alone** della copertina e dell'icona, un'ombra del loro colore;
- **i pannelli di vetro per i comandi**: il lettore audio, i fatti dell'eseguibile in capsule, le cartelle dell'archivio come tessere, l'avviso in vetro ambra;
- **i punti colorati**: il colore di ogni foglio nella capsula delle schede, il tipo di ogni valore nel JSON, la lingua del codice nel sottotitolo.

| tipo | cosa fa il preset (engine) | cosa resta all'app |
|---|---|---|
| **Immagine** | disegna il contenuto; zoom, pager, chiusura del telaio; un'animazione (`fluidAnimatedImage`) parte solo sulla pagina ferma, si ferma lasciandola, col movimento ridotto aspetta l'azione TalkBack «Riproduci animazione» | leggere il file (o `engine-viewer`), i metadati, i tasselli, la foto in movimento |
| **Video** | un solo gruppo di comandi di vetro limpido (`Clear`, M2) sopra il video: il Play al centro compare e sparisce **per lente** (R4 B3: 430 ms in entrata, 470 in uscita), lo scrubber (`FluidScrubber`) col tempo in cifre tabulari; i comandi seguono le barre, senza timer | il player e la sua versione, la superficie (con i suoi comandi spenti), l'autoplay, **cosa fare quando lo si sfoglia via** (sotto), codec, errori, il rilascio |
| **Audio** | copertina da 280 dp con alone, titolo e artista, onda di seek da 302 × 44 dp, tempo trascorso e rimanente, play da 68 dp, salti e capitolo corrente. La durata si chiede **prima di Play** con `FluidMediaDurationLoader`. Senza copertina si disegna quella generata dal tema | player e copertina, forma d'onda, capitoli, `audioFormat` e `audioOutput` in `FluidViewerInfo`. Con `FluidAudioSpeed` si può cambiare la velocità. Configura i salti con `FluidMediaPresetStrings`, 15 s nella galleria e 10 s di serie |
| **Documento a pagine** | le pagine in colonna a tutta larghezza (l'ultima finisce sopra la capsula in basso, anche con una tab bar nell'app), pizzico fino a 4× sulla colonna intera, una capsula «2 di 6» che apre le miniature, «Trova» in basso se l'app dà la ricerca (`searchPages` del pannello); anteprime leggere mentre si scorre (lato lungo 384 px), nitide da fermo (2048 px), una cache di sei bitmap | la sorgente delle pagine (o `FluidPdfPageSource` di `engine-viewer`, o Office) e la ricerca |
| **Testo e codice** | monospazio e numeri di riga, lettura a pezzi da 400 righe, il punto della lingua, «a capo» (di serie sì per il testo, no per il codice), «Trova» di F6B.6 nel file intero, sopra la tastiera e sotto la barra in alto; i colori dei ruoli col tema; «Riprova» e il file vuoto | le righe e la lingua (o `FluidTextDocument` di `engine-viewer`, che riconosce il charset e colora 19 lingue) |
| **Griglia** | celle a blocchi (64 × 32, ne tiene 48), righe e colonne bloccate, celle unite, schede con il punto del colore del foglio. Le schede stanno nella capsula centrale quando entrano. Con testi grandi o molte schede scorrono in una riga sopra la barra. A fine scorrimento l'ultima riga si legge intera, lo zoom va da ½ a 4× e il pager prende il gesto ai bordi | il libro di lavoro (Office) o la tabella (CSV dei dati) |
| **Pagina del modulo** (`Custom`) | la chrome, «Trova» se la pagina lo dichiara, lo spazio fra le barre (`contentPadding`), le azioni nella barra in basso; l'ultimo preset che registra la barra vince (una mail che apre un allegato la riprende quando si chiude) | tutto il resto: la pagina la disegna il modulo con i componenti pubblici dell'engine |
| **Sconosciuto ed eseguibile** | icona da 136 dp con alone, il nome con l'estensione vera, tipo, peso e data se nota. Fatti in capsule da 26 dp, «Apri con…» e «Condividi» in tasti da 46 dp. Il tasto pieno ha testo bianco in grassetto e il suo riempimento mantiene almeno 4,5:1. L'APK con icona usa lo stesso lettore delle miniature di F6C.17. L'avviso è crema in chiaro e ambra scura in scuro. «Mostra i primi byte» compare se l'app passa `firstBytes` | il permesso di leggere il file, eventualmente `modifiedAtMillis` in `FluidViewerFile` o `FluidFileInfo`, e le azioni per aprire e condividere |

Il contatore da 34 dp apre le miniature dei documenti. `officeInfo()` attiva «Trova» sui documenti Word,
ODT e RTF e fornisce il sottotitolo del formato. PPTX e ODP hanno «Presenta», che apre le slide a schermo
pieno. Il codice mette il punto della lingua e il numero di righe nel sottotitolo e offre «Aa», a capo e ricerca.
L'albero JSON ha righe da 33 dp e la capsula «Albero | Testo». L'archivio aggiunge la ricerca per percorso. Il
comando «Estrai tutto» usa il callback di destinazione configurato dall'app in `FluidArchiveFamily`. Le righe
visibili usano le anteprime di immagini e PDF per gli archivi di dimensione contenuta. Le altre voci hanno
un'icona colorata per tipo. Il peso compresso e quello estratto condividono l'unità quando è la stessa.

Le misure della colonna (fino a 552 dp), della copertina e dell'icona stanno in `ViewerMixProfile` (`FluidViewerInfo.kt`), scelte a occhio il 22 novembre 2026.

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

## Il titolo e il menu del file (F6C.19)

Come in File e in Quick Look di iOS 26 (il fermo `ql-pdf-menu.png` e la tavola «D · Mix»), il titolo del visore è **il
nome del file senza estensione con «⌄»** (`opm71 ⌄` per `opm71.pdf`; resta intero un nome come `.gitignore`), e apre
**il menu del file**. È il titolo selettore di F6B.11 ([`toolbar.md`](toolbar.md), `title(…, menu = { … })`): il piccolo
disco con la freccia accanto al nome, il menu che nasce dal disco e ci rientra, il bersaglio di almeno 48 dp che
comprende il disco, e per TalkBack un «elenco a discesa» col nome del file e l'azione «Mostra le azioni del file».
«2 di 5» (o la lingua del codice, il formato di un documento) resta sotto il nome. Vale in ogni preset e in ogni
pagina di famiglia, dentro una rotta e fuori; il nome viene da `FluidViewerInfoProvider` (senza, il titolo è «2 di 5»
e non apre niente).

Le voci, nei gruppi del menu del titolo di un documento in iOS (una banda fra un gruppo e l'altro):

| gruppo | voci | chi le fa |
|---|---|---|
| l'app | Duplica, Sposta, Rinomina, le sue in più (`more`, per esempio «Salva in Download») | l'app, **solo se le dà** |
| mandare altrove | Condividi, Apri con…, Copia (il file negli appunti), Stampa | l'engine: con `engine-viewer` le prime tre da `files.fileActions(i)`; Stampa il visore, per un documento a pagine (PDF, Word, slide), rendendo ogni pagina a 200 dpi nel dialogo di sistema |
| | Informazioni | il visore: apre il pannello info |
| | Elimina, in rosso | l'app, solo se la dà; **la conferma la chiede l'app** |

**«•••» tiene solo le azioni del tipo** (sotto): le azioni del file stanno solo nel titolo, e il visore toglie da «•••» le
voci che il titolo ha già. Il «•••» accanto al titolo del brano nel lettore audio tiene solo le voci dell'app che il
titolo non ha, e senza sparisce. Il pannello info tiene le sue azioni in fondo, come prima, e la scheda dello sconosciuto i
suoi «Apri con…» e «Condividi»: mai un vicolo cieco.

Un'app di prima, che dava le azioni solo a `FluidViewerInfo.actions`, le ritrova nel titolo: «Apri con…» e «Condividi»
valgono come quelle dell'engine, le altre entrano nel gruppo «mandare altrove», tranne quelle che la barra in basso ha
già («Estrai tutto»).

```kotlin
val files = rememberFluidViewerFiles(lista, openWith = ::apriCon, share = ::condividi)
val commands = FluidViewerFileCommands(
  rename = { file -> rinomina = file },                                 // un FluidAlert col campo del nome
  delete = { file -> elimina = file },                                  // un FluidConfirmationDialog
  // «Copia» (e Condividi e Apri con… se l'app non le dà) vuole un indirizzo che altre app leggono, non un file://
  shareableUri = { file -> FileProvider.getUriForFile(context, "$packageName.file", File(file.uri.path!!)) },
)
FluidViewerInfoProvider(info = { i -> files.document(i)?.let { Dettagli(it) } }) {
  FluidFileViewer(count = files.count, content = files::content, fileActions = { i -> files.fileActions(i, commands) })
}
```

Senza `engine-viewer` si dà `FluidViewerFileActions(share = …, rename = …)` direttamente. Le parole sono in
`FluidViewerStrings` (`copyFile`, `print`, `rename`, `move`, `duplicate`, `delete`, `fileMenu`). Un'azione che lancia
un'eccezione non chiude l'app: sotto la barra compare «Impossibile completare l'azione».

## Le azioni del tipo in «•••» (F6C.19)

Scelta di Alessio del 23 novembre: **«•••» c'è sempre** sulle pagine della tavola «D · Mix», in basso a sinistra, e tiene
**le azioni proprie del tipo** (come Cerca, Compilazione moduli e Annotazioni in Quick Look), mai quelle del menu del nome.
Solo azioni che il visore sa fare davvero:

| tipo | voci di «•••» | da dove (iOS 26) | chi le fa |
|---|---|---|---|
| documento a pagine, Word, ODT, RTF | Vai a pagina…, Miniature; Adatta alla larghezza se ingrandito | Anteprima | `FluidPagesPreset` |
| slide (PPTX, ODP) | Vai alla slide…, Miniature, Note del relatore (se ci sono); Adatta alla larghezza | Keynote | `FluidPagesPreset`; le note con `FluidPageNotes` dalla sorgente (il modulo Office) |
| audio | Velocità (le velocità del tasto «1×»), Timer di spegnimento (5, 15, 30 minuti, 1 ora, fine del brano) | Musica e Podcast | `FluidMediaPreset` (la velocità con `FluidAudioSpeed`) |
| foglio (XLSX, ODS, CSV) | Vai alla cella… («B12»); Dimensioni reali se ingrandito | Numbers | `FluidGridPreset` |
| albero (JSON, XML, plist) | Espandi tutto, Comprimi tutto | gli ispettori di dati | la famiglia dati |
| archivio | Ordina, Filtra, Codifica dei nomi | File | la famiglia archivi |
| una pagina senza azioni del tipo | Nascondi controlli (il tocco sul file) | — | il visore |

Il testo e il codice hanno nella tavola, al posto di «•••», le due capsule di lettura («Aa», a capo): restano come sono.
Le pagine delle famiglie che oggi non danno voci (Markdown, HTML, EPUB, posta, calendario, contatti, caratteri, 3D) hanno
«Nascondi controlli».

**Una famiglia o un'app** dà le sue voci con lo stesso meccanismo dei preset, nelle `actions` della pagina `Custom`:

```kotlin
FluidViewerContent.Custom(find = trova, actions = {
  dataModes(…)                                                        // le capsule della pagina, come prima
  viewerTypeActions {                                                 // «•••»: le azioni del tipo
    listOf(FluidContextAction("Espandi tutto") { espandi() }, FluidContextAction("Comprimi tutto") { comprimi() })
  }
}) { Pagina(this) }
```

Il menu si legge quando si apre (una voce con `selected`, un `subtitle` con lo stato, un sottomenu con `children`). Una
sorgente di pagine che conosce le note del relatore implementa `FluidPageNotes` (`notes(index)`, `hasNotes`). Il timer
di spegnimento vive con la pagina: lasciando il visore si spegne.

## Il pannello info

Il tasto «i» c'è in ogni pagina, e il dito che tira su dal contenuto fa lo stesso. Si chiude con ✕, tirando giù o col back.

- **Medium**, al 52% (i detent di F5B.2), sta sopra il contenuto **senza scurirlo** (`undimmedUpTo = Medium`): i documenti stanno fermi, la foto sale nello spazio libero come in Foto.
- **Large**, sotto la barra di stato con 6 dp di margine, mostra tutto, e la pagina sotto si scurisce.
- Da **840 dp** di larghezza è il pannello laterale, con lo stesso contenuto.
- **Com'è fatto**, uguale per ogni tipo: la testa (miniatura, nome, «tipo · dimensione», ✕); una riga di fatti in capsule che scorre di lato; le sezioni in gruppi (**una pressione lunga su una riga copia il valore**: `FluidViewerInfoRow.copyValue`, il valore originale quando quello mostrato è tradotto); in fondo le azioni («Apri con…», «Condividi», «Estrai tutto» per gli archivi). Titolo e sottotitolo stanno sul vetro con il suo inchiostro adattivo e seguono il velo del pannello mentre sale. La prima capsula usa l'accento con contrasto calibrato. TalkBack legge testa, fatti e sezioni in ordine.
- **I dati sono dell'app o del modulo**: `FluidViewerInfo(file, title, subtitle, thumbnail, facts, sections, actions, audioWaveform, location, reading, searchPages, chapters)`. Una famiglia dà le sue righe con `FluidViewerOpened.details` (`FluidViewerDetail(label, value, section)`), e ognuna ha la sua funzione per farne un `FluidViewerInfo`: `officeInfo()` (Office), `fluidArchiveViewerInfo()` (archivi, composable), `fluidDataViewerInfo(document)` (dati; va bene per ogni famiglia che mette i fatti senza sezione e il resto in sezioni, come grafica e 3D).
- **La mappa della foto è dell'app**: l'engine non ha mappe. `FluidViewerLocation(coordinates, map, openMap)` è uno spazio per il suo composable; senza, una riga con le coordinate e «Apri in Mappe».

| tipo | fatti | sezioni |
|---|---|---|
| audio | codec, bitrate, campionamento, canali | Brano (album, traccia, anno, genere), Capitoli, File, Lettura |
| eseguibile (APK) | APK, versione, firma, installata | App (ID, versione, Android minimo e di destinazione), Firma (certificato, SHA-256, validità), Permessi, Contenuto |
| Word e gli altri documenti Office | pagine, parole, formato | Documento (titolo, autore, date, applicazione), Impaginazione (foglio, margini, **caratteri sostituiti**, parti omesse) |
| foglio | fogli, formule | Fogli (col loro colore), Calcolo (i valori salvati, niente ricalcolo) |
| archivio | elementi, compressione, metodo, integrità | Contenuto, Sicurezza (protetti, percorsi, collegamenti), Archivio (formato, versione di libarchive) |
| foto | formato, misure, spazio colore | la data come titolo, Fotocamera, il luogo |
| PDF | pagine, versione | Documento, Pagine (i formati), Impronta |
| testo, codice e dati | lingua, codifica, fine riga, righe; per il JSON nodi e profondità | Testo, Lettura (codifica riconosciuta, troncato o no) |
| font, 3D | famiglia, stili, assi; vertici, triangoli, materiali | Nomi e Licenza (tabella `name`); Modello (misure, animazioni) |

## L'apertura dalla miniatura (F6C.10)

Ogni file si apre dalla sua miniatura (o dall'icona, se la miniatura manca) e la chiusura ci torna: col tasto, col back predittivo, col dito oltre 110 dp. Il ritorno parte da dove il dito ha lasciato.
- Foto e video viaggiano col contenuto condiviso; gli altri preset con una copia della sorgente (`FluidTransitionSource`, la transizione di `Expand`).
- La forma segue la molla di 0,355 s; **la pagina sotto si scurisce** col velo di F6D.1 (il profilo dello zoom dell'host, al massimo 18%); il fondo pieno del visore e la tinta di «D · Mix» arrivano nell'ultimo 12% della corsa; le barre entrano sfocate e sono nitide in 350 ms. L'icona che diventa pagina entra sfocata (R7). Il pannello info aperto durante la chiusura si chiude prima. Nel ritorno la tinta va a zero.
- **Niente scatti**: il documento tiene la miniatura fino alla prima pagina, al primo blocco di testo o di celle pronto da mostrare. Una superficie che dipinge in modo asincrono dichiara `firstFrameReady = { … }` in coda a `FluidViewerContent.Custom`: una funzione che legge stato osservabile e diventa vera alla prima pittura o a un errore che il contenuto può mostrare (la pagina web la prima pittura, il 3D un fotogramma con le risorse). Senza, vale il primo layout con contenuto.
- **L'app può preparare il primo fotogramma.** La galleria apre il file scelto e lo aspetta per al più 120 ms prima di navigare (`prepareVisoriFirstContent` del sample); oltre, naviga lo stesso e il documento arriva dopo.
- **La forma che cresce è la miniatura vera** (F6C.17): la transizione registra la tessera così com'è disegnata, quindi con le miniature di sotto la sagoma cresce mostrando le prime righe, l'albero, la tabella, l'icona d'archivio che c'erano nella tessera, e ci torna. La cache le dà alla prima composizione: tornando dal visore la tessera è già piena.

## Le miniature (F6C.17)

Un modulo che possiede già una sorgente può chiamare `FluidFileThumbnails.read(context, source, sizePx)`
per leggere la miniatura senza generare un'icona sostitutiva. La sorgente resta del chiamante.

**Ogni file mostra il suo contenuto in miniatura, come in File di iOS** (scelta di Alessio del 23 novembre 2026), e l'apertura parte da lì. Una chiamata, sempre un'immagine:

```kotlin
// In una griglia: la prima composizione legge la cache, poi la miniatura arriva fuori dal filo principale.
val sizePx = with(LocalDensity.current) { 180.dp.roundToPx() }      // il lato lungo della tessera
val thumbnail = rememberFluidFileThumbnail(FluidViewerFile(uri, name, mime, size), sizePx)
Box(Modifier.size(180.dp, 120.dp).fluidViewerThumbnail(FluidViewerThumbnail(id, ns), shape).clip(shape)
  .background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
  thumbnail?.let { FluidFileThumbnailImage(it, Modifier.fillMaxSize()) }   // Crop se è una figura, Fit se no
}

// Fuori da Compose (un worker, un widget): non lancia, al peggio dà l'icona del documento.
val t: FluidFileThumbnail = FluidFileThumbnails.load(context, file, sizePx = 480, look = FluidThumbnailLook.Dark)
```

| tipo | miniatura | chi la dà |
|---|---|---|
| testo | le prime righe su un foglio bianco, che vanno a capo | `engine-viewer` |
| codice (con una lingua riconosciuta) | le prime righe a spaziatura fissa coi colori di Xcode, senza a capo | `engine-viewer` |
| APK | l'icona dell'app, letta dal pacchetto, con l'alone del suo colore; il pacchetto non si apre | `engine-viewer` |
| foto, video, audio, PDF | la foto ridotta, un fotogramma, la copertina, la prima pagina (le app che hanno già le loro possono tenerle) | `engine-viewer` (solo `FluidFileThumbnails`) |
| Word, Excel, PowerPoint e gemelli | la miniatura salvata se dice qualcosa, altrimenti la prima pagina, slide o foglio (F6C.15b) | `engine-viewer-office` |
| Markdown, HTML | resi: titoli, elenchi, codice, tabelle; l'HTML senza rete né script (`Html.fromHtml`), senza immagini | `engine-viewer-data` |
| CSV, TSV | un pezzo di tabella su un foglio orizzontale: intestazione, griglia, numeri a destra | `engine-viewer-data` |
| JSON | un pezzo dell'albero coi punti colorati dei tipi, come la pagina | `engine-viewer-data` |
| XML, plist, NDJSON | il testo coi colori; un plist binario come albero | `engine-viewer-data` |
| EML | mittente con le iniziali, data, oggetto, l'inizio del corpo | `engine-viewer-data` |
| ICS, VCF | la tessera della data del primo evento; la foto del primo contatto o le sue iniziali (con «+N») | `engine-viewer-data` |
| EPUB | la copertina; senza, una copertina col titolo e l'autore | `engine-viewer-data` |
| font | «Aa» nel carattere, l'alfabeto e il nome della famiglia | `engine-viewer-graphics` |
| HEIC, AVIF, RAW, TIFF, PSD, SVG | l'immagine | `engine-viewer-graphics` |
| GLB, OBJ, STL | **il modello disegnato** a tre quarti con l'ombra a terra; disegnato dal modulo sulla CPU, non da Filament (ripiego dichiarato: niente texture); glTF con file esterni e USDZ hanno il simbolo del 3D | `engine-viewer-3d` |
| archivi | l'icona d'archivio nello stile della pagina D (la tessera con la tinta leggera, la cartella con la cerniera, l'estensione): mai una tessera grigia | `engine-viewer-archive` |
| uno sconosciuto, un file rotto, un tipo senza il suo modulo | l'icona del documento con l'angolo piegato e l'estensione, come File | `engine-viewer` |

**Come lavora `FluidFileThumbnails.load`.**
- L'ordine è quello del registro: la famiglia che apre il file (`FluidViewerFamily.thumbnail`), poi i tipi di serie, poi l'icona del documento. Un eseguibile non arriva mai a una famiglia: di un APK si legge solo l'icona.
- **Fuori dal filo principale**: legge e disegna su `Dispatchers.IO`, al più `Parallelism` (3) file insieme.
- **Tempo massimo** `DefaultTimeoutMillis` (2,5 s): oltre, torna subito con l'icona del documento e annulla il lavoro; il file si riprova dopo `RetryAfterTimeoutMillis` (30 s).
- **Cache** in memoria con un tetto in byte, `memoryLimitBytes` (un ottavo della memoria dell'app, al più 48 MB): le meno usate escono per prime. La chiave è il file (`Uri`, nome, MIME, dimensione), il lato arrotondato a 32 px, i colori (`FluidThumbnailLook`) e i moduli presenti; due richieste uguali fanno un lavoro solo. `cached(file, sizePx)` legge senza lavorare, `evict(file)` toglie un file che è cambiato, `clear()` svuota.
- `FluidFileThumbnail` dice come sta nella tessera: `fillsFrame` (foto, fotogramma, modello: `ContentScale.Crop`) o no (foglio e simbolo: `Fit`), e `isPlaceholder` quando l'immagine dice solo il tipo.
- **I colori**: la carta dei documenti è bianca nei due temi, come Quick Look. I simboli (archivio, audio senza copertina, 3D che non si legge) prendono l'accento, il terziario e la superficie dal `FluidThumbnailLook`: `rememberFluidThumbnailLook()` li legge dal `MaterialTheme`.
- `FluidViewerRegistry.thumbnail(context, source, sizePx)` resta per chi ha già una sorgente: dà solo il contenuto (famiglie, testo, codice, APK), senza cache, e `null` per le foto, i PDF e i tipi senza contenuto.

**Per chi scrive un modulo di famiglia.** `FluidViewerFamily.thumbnail(request, sizePx)` restituisce un `ImageBitmap`; gli attrezzi di `FluidThumbnailPainter` danno la grammatica comune: `page` (il foglio, con i margini e l'unità del testo), `codePage`, `linesPage`, `prosePage`, `symbol`, `kindSymbol`, `documentIcon`, `picture`, `syntaxColor`. Un'immagine fatta con loro porta con sé come sta nella tessera; i colori dell'app arrivano col contesto (`FluidThumbnailLook.current()`), senza cambiare la firma. Un `null` o un'eccezione passano al candidato dopo.

## Cosa fa l'engine, cosa i moduli, cosa l'app

| dell'engine (`engine-ui`) | dei moduli (facoltativi) | dell'app |
|---|---|---|
| la rotta, il pager, lo zoom, la chiusura, le barre al tocco, le barre di sistema | `engine-viewer`: il risolutore e il **registro** delle famiglie (`FluidViewerRegistry`), che non declassa mai un eseguibile | i file, la loro cartella, i permessi, l'`Uri` |
| il pannello info e il posto per lo strumento | `engine-viewer`: PDF, testo, GIF e WebP animate, l'adattatore di Media3 | il contenuto del pannello (o le funzioni delle famiglie), lo strumento e il suo compositore |
| i preset «D · Mix» e i loro comandi, la tinta, l'alone | le famiglie: Office, archivi, dati, grafica e font, 3D, AR, ognuna con le sue pagine `Custom`, `Grid` o `Pages` e il suo pannello | il player, quando suona, quando riprende |
| `currentFluidViewerPageActive()`, `currentFluidViewerZoom()` | le licenze delle loro librerie (`FluidOfficeLicenses`, `FluidArchiveLicenses`, `FluidGraphicsViewerLicenses`, `FluidSceneViewerLicenses`, `FluidArViewerLicenses`) | «Apri con…», «Condividi», «Estrai tutto», il dump esadecimale se lo vuole, la sezione delle licenze |
| il titolo col nome e «⌄», il menu del file, «Informazioni» e «Stampa» dei documenti a pagine | `engine-viewer`: «Condividi», «Apri con…» e «Copia» dall'indirizzo del file (`fileActions`) | Rinomina, Sposta, Duplica, Elimina e la sua conferma, le sue voci in più, l'indirizzo per le altre app |
| — | — | Media3 alla sua versione, la fotocamera per l'AR |

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
    FluidViewerInfoProvider(info = { i -> opened.document(i)?.let { Dettagli(it) } }) {
      FluidFileViewer(count = opened.count, content = opened::content, onBack = { nav.popBackStack() })
    }
  }
}
```

**Con le famiglie**: niente codice in più per aprire, una riga per il pannello.

```kotlin
// build.gradle: implementation project(':engine-viewer-all')   // oppure solo le famiglie che servono
FluidViewerInfoProvider(info = { i ->
  val document = opened.document(i) ?: return@FluidViewerInfoProvider null
  when (document.type.family) {
    "office" -> document.officeInfo()
    "archive" -> document.fluidArchiveViewerInfo()
    null -> Dettagli(document)                         // i tipi di serie: i metadati li legge l'app
    else -> fluidDataViewerInfo(document, azioni)      // dati, grafica, 3D
  }
}) { FluidFileViewer(count = opened.count, content = opened::content) }
```

**Senza decodificatore**: i contenuti dell'app, e il telaio sceglie il preset.

```kotlin
FluidFileViewer(count = items.size, content = { i ->
  when (val item = items[i]) {
    is Foto -> FluidViewerContent.Image(item.ratio) { AsyncImage(item.url, item.nome, Modifier.fillMaxSize()) }  // Coil
    is Clip -> FluidViewerContent.Media(item.sessione, sampleVideo = true, videoSurface = { Superficie(item) })
    is Nota -> FluidViewerContent.Text(item.righe)            // un FluidTextSource dell'app
    is Tabella -> FluidViewerContent.Grid(item.griglia)       // un FluidGridSource dell'app
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

## La galleria «Visori» (F6C.15)

In Fluid Glass, «Da provare › Visori» o la scheda Chrome. Il codice: `sample/.../ViewerGallery.kt` e `sample/.../viewer/`.

- **Un file per tipo di ogni famiglia**, ordinati per famiglia, ognuno aperto dalla sua miniatura e col pannello della sua famiglia:
  - **di serie** (`engine-viewer`): due foto, una WebP animata, un video e un audio con ExoPlayer, un PDF di sei pagine, un testo, un file Kotlin, un `.blend` sconosciuto e un `.apk`, con lo strumento «Ritaglia», l'interruttore «Riprendi al ritorno», il collegamento `fluidglass://visori/file/7` e il ripristino;
  - **Office**: DOCX, XLSX, PPTX, ODT, ODS, ODP, RTF; **archivi**: ZIP, 7z, RAR, TAR, TGZ, GZ; **dati**: Markdown, HTML, CSV, TSV, JSON, NDJSON, XML, plist, EPUB, EML, ICS, VCF; **immagini speciali e caratteri**: HEIC, AVIF, DNG, TIFF, PSD, SVG, TTF, TTF variabile, OTF, TTC, WOFF; **3D**: GLB, OBJ, STL. Sono i corpi di prova dei moduli (F6C.11–F6C.14), copiati byte per byte in `sample/src/main/assets/visori/`.
- **Le tessere mostrano le miniature vere** (F6C.17): le foto, il video, l'audio e il PDF la copertina dell'app; tutto il resto `rememberFluidFileThumbnail`. Niente più tessere grigie con la sigla.
- **«Senza moduli»**: gli stessi file come li apre un'app con il solo `engine-viewer`. I tipi che il modulo di serie conosce (il testo, il Markdown come codice, l'HEIC col decoder di Android) si aprono; gli altri sono sconosciuti con «Apri con…» e «Condividi», mai un vicolo cieco. Ogni riga ha la sua miniatura: testo e codice le loro prime righe, i tipi delle famiglie l'icona del documento con l'estensione. La scena toglie le famiglie dal registro (`FluidViewerRegistry.uninstall`) finché è aperta e le rimette uscendo.
- **«Pesi»**: il peso di ogni modulo per architettura, con le licenze, letto da `engine-viewer/families.json` (il build lo copia negli asset).
- **«Universal Converter»**, in sola lettura con `engine-viewer-all`: i tipi delle prove di quell'app (PNG, JPEG, GIF, SVG, Markdown, HTML, XML, JSON, testo UTF-16, DOCX, PDF, MP3, WAV, MP4, ZIP, OBJ, un `.wld`, il file vuoto, il file sparito), ognuno col suo preset al posto del dump esadecimale; il dump resta a richiesta per lo sconosciuto.
- La galleria include `engine-viewer-all` **senza `engine-viewer-ar`**: l'AR chiede la fotocamera, e Fluid Glass è un'app pubblicata che non la chiede (lo controlla `checkArViewerManifests`). Il tasto «AR» si prova nell'APK di prova del modulo.

## Trappole

- **Il player suona sotto un'altra pagina.** Il telaio non ferma niente da sé: senza `currentFluidViewerPageActive()` un video sfogliato via continua a suonare (e a costare). Vale anche per il ciclo di vita: la galleria pausa in `onPauseOrDispose`.
- **Un contenuto nuovo a ogni composizione.** `content(i)` si chiama spesso: un `FluidViewerContent` creato lì ogni volta fa ripartire il preset (il testo rilegge, le pagine rifanno la cache). Va ricordato per file, come fa `rememberFluidViewerFiles`.
- **La superficie con i suoi comandi.** Un `PlayerView` con il controller acceso dà due interfacce sullo stesso video (il difetto di Gallery v1): la superficie va con i comandi spenti, i comandi sono del preset.
- **`SurfaceView` sotto il vetro.** Sta in una finestra sua: i comandi di vetro non la vedono e mostrano il nero. Per il vetro che rifrange il video, `TextureView` (il default di `FluidMedia3Video`) e `sampleVideo = true`.
- **Un `.apk` col nome innocuo.** `FluidFileKind.from` non declassa mai un eseguibile, da qualunque fonte: la scheda ha l'avviso, e nessuna famiglia lo riceve. Non aggirarlo passando un `kind` a mano.
- **«Copia» che non c'è.** Un `file://` non esce dall'app: senza `shareableUri` in `FluidViewerFileCommands` (o un `Uri` `content://` già nel `FluidViewerFile`) il menu del file non ha «Copia», e «Condividi» e «Apri con…» ci sono solo se l'app le dà a `rememberFluidViewerFiles`.
- **Il menu del file senza nome.** Il titolo apre il menu solo se è il nome di un file: senza `FluidViewerInfoProvider` il titolo è «2 di 5» e le azioni tornano nel vecchio «Altro».
- **«Apri con…» con un `file://`.** Un'altra app non può leggerlo (`FileUriExposedException`): serve un `FileProvider` o un `Uri` che l'altra app sa aprire (la galleria ha un suo provider in sola lettura, `VisoriPackageProvider`). Se l'azione lancia un'eccezione, la scheda dice «Nessuna app può aprire questo file».
- **Il pannello vuoto per un file di famiglia.** Il telaio non sa leggere `FluidViewerDocument.details`: senza `officeInfo()`, `fluidArchiveViewerInfo()` o `fluidDataViewerInfo(…)` nel `FluidViewerInfoProvider` il pannello mostra solo nome e dimensione.
- **La tinta su un documento.** La tinta di C è solo per audio, eseguibile, sconosciuto e archivio: una pagina `Custom` di lettura non la mette.
- **Robolectric non ha i decodificatori.** `PdfRenderer` (pdfium) e `ImageDecoder` non decodificano sulla JVM, e le librerie native (libarchive, Filament) non si caricano: le prove della galleria passano il contenuto di quei file o una famiglia finta con i tipi veri; quelli veri si provano sul dispositivo (`connectedDebugAndroidTest` dei moduli).

## Cosa è verificato dai test

- `FluidViewerTraceTest` (engine-ui, JVM): il fondo per tipo, la soglia e l'inversione della chiusura, il pizzico di Gallery-2, i tempi delle barre, la miniatura giusta dopo uno sfoglio.
- `FluidFileViewerUiTest` (sample, Robolectric): pizzico, sfoglio e chiusura che non litigano, info dal gesto e dal tasto, lo strumento nella capsula della shell, il video che si ritira al tocco, il collegamento e la ricreazione, le liste vuote.
- `FluidViewerPresetsTest`, `FluidViewerPresetsUiTest`: immagine (zoom, animazione sulla pagina ferma), testo (pezzi, «Trova», a capo, colori), sconosciuto (eseguibile, nome tagliato, azioni che falliscono).
- `FluidMediaPagesPresetTest`, `FluidMediaPagesPresetUiTest`: video e audio (Play, seek, durata ignota, errori), pagine (cache, miniature, zoom accessibile, riprova).
- `FluidViewerFormsTest` (engine-viewer) e `FluidViewerFormsPresetTest` (engine-ui): `Pages` col foglio e il conteggio che cresce, `Grid` con fogli, unioni e blocchi, `Custom` con fondo, «Trova» e azioni; la pagina nera `Custom` non zoomata.
- `FluidViewerMixUiTest`, `FluidViewerReadingMatrixTest`, `FluidViewerInfoContrastUiTest` (sample): i preset di «D · Mix», la tinta solo dove va, il pannello a Medium, Large e laterale, il contrasto di titolo e capsule.
- `FluidDocumentJourneyUiTest` (sample) e `F6cDocumentViewerTraceTest` (engine-ui): l'apertura dalla miniatura per ogni tipo e il ritorno, a 360 e 412 dp.
- `VisoriGalleryTest` (sample): ogni tipo di serie si apre col suo preset; **ogni file di ogni famiglia si apre dalla sua miniatura col suo modulo**; il video si ferma quando lo si sfoglia via e riprende come sceglie l'app; lo strumento prende la barra; lo sconosciuto non è mai un vicolo cieco; **«Senza moduli» non è mai un vicolo cieco** e rimette le famiglie uscendo; **la pagina «Pesi» legge `families.json`**; la rotta col collegamento e il ripristino; **i tipi di Universal Converter col loro preset**.
- `FluidFileThumbnailsTest` (engine-viewer, Robolectric): testo e codice disegnati, l'icona del documento per lo sconosciuto e l'APK illeggibile, la cache (stessa istanza, richiesta doppia, altri colori, senza moduli), il tetto di memoria, il tempo massimo con un decodificatore che si blocca, il file sparito e la famiglia che cade.
- `VisoriMiniatureTest` (sample, Robolectric): **ogni file del corpo di prova ha una miniatura non vuota e non uniforme**, in chiaro e in scuro, e i tipi con un contenuto lo mostrano; «Senza moduli» testo e codice hanno la loro e gli altri l'icona; **l'apertura dalla miniatura di un JSON e di un archivio parte dall'immagine vera e ci torna** (i fotogrammi in `sample/build/visori-apertura`). `VisoriTavolaTest`: le schermate della galleria nel formato del telefono, per la tavola prima e dopo.
- `FluidViewerTypeActionsUiTest` (sample, Robolectric, 412 e 360 dp): «•••» su PDF, slide, audio, foglio, JSON e sulle pagine vere di XLSX e PPTX della galleria, con le voci giuste e nessuna in comune col nome; «Vai a pagina…» porta alla pagina, le note sono quelle della slide, la velocità cambia il player e il timer lo mette in pausa, «Vai alla cella…» porta la riga in vista, «Espandi tutto» e «Comprimi tutto» aprono e chiudono l'albero, una pagina senza azioni ha «Nascondi controlli»; in basso «•••», l'azione del tipo e condividi in fila, nello schermo.
- `FluidViewerFileMenuTest` (engine-ui, JVM) e `FluidViewerFileMenuUiTest` (sample, Robolectric, 412 e 360 dp): il nome senza estensione; il titolo come elenco a discesa col suo nome; le voci giuste per un PDF, un audio di un'app di prima, un archivio e uno sconosciuto; quelle dell'app solo se date; Informazioni che apre il pannello; nessuna voce doppia fra il titolo e «•••»; «2 di 5» sotto il nome e nessun bersaglio della barra sopra un altro, anche con un nome lungo.
- `F6cViewerTraceTest` e le tracce `tools/glassbench/traces/f6c/` (`visore-apre`, `visore-barre-tocco`, `visore-info-su`, `visore-chiude-giu`, `visore-chiude-pizzico`, `video-comandi` contro B3, `pdf-pagine`, `testo-trova`, e le aperture dalla miniatura di F6C.10), a 120 Hz: il movimento si giudica dai fotogrammi.

## Dove guardare

| cosa | file |
|---|---|
| la rotta, il pager, i gesti, l'apertura dalla miniatura | `engine-ui/.../ui/fluid/viewer/FluidFileViewer.kt` |
| la chrome, gli strumenti, le barre di sistema | `FluidViewerChrome.kt` |
| il titolo e il menu del file, la stampa | `FluidViewerFileMenu.kt`, `FluidViewerPrint.kt`; in `engine-viewer` `FluidViewerFileCommands.kt` |
| «•••» e le azioni del tipo | `FluidViewerTypeActions.kt` (`viewerTypeActions`, `FluidPageNotes`, «Vai a…»); le voci nei preset |
| i contratti | `FluidViewerContent.kt` |
| il pannello info e il profilo di «D · Mix» | `FluidViewerInfo.kt` |
| il pizzico che chiude | `FluidViewerPinch.kt` |
| i preset | `FluidImagePreset.kt`, `FluidMediaPreset.kt`, `FluidPagesPreset.kt`, `FluidTextPreset.kt`, `FluidGridPreset.kt`, `FluidCustomPreset.kt`, `FluidUnknownPreset.kt` |
| i decodificatori e il registro | `engine-viewer/` ([`../moduli/viewer.md`](../moduli/viewer.md)) |
| le miniature | `engine-viewer/.../FluidFileThumbnails.kt` (la funzione pubblica e la cache), `FluidThumbnailPainter.kt` (gli attrezzi), `FluidBuiltinThumbnails.kt` (testo, codice, APK, immagini, PDF); nelle famiglie `DataThumbnail.kt`, `FontThumbnail.kt`, `SceneThumbnail.kt`, `ArchiveThumbnail.kt`, `OfficeThumbnail.kt` |
| le famiglie | `engine-viewer-office/`, `-archive/`, `-data/`, `-graphics/`, `-3d/`, `-ar/`, `-all/` ([`../moduli/viewer-famiglie.md`](../moduli/viewer-famiglie.md)) |
| la galleria | `sample/.../ViewerGallery.kt`, `sample/.../viewer/` (`VisoriViewer.kt`, `VisoriFiles.kt`, `VisoriFamilies.kt`, `VisoriDetails.kt`, `VisoriPesi.kt`, `VisoriSenzaModuli.kt`) |

**Versione.** 4.0 (F6C): telaio F6C.1, preset F6C.2 e F6C.3, modulo F6C.4, prima galleria F6C.5; registro e forme `Grid` e `Custom` F6C.8, preset «D · Mix» e pannello info F6C.9, apertura dalla miniatura F6C.10, famiglie F6C.11–F6C.14 e F6C.16, galleria rifatta e schede F6C.15; le miniature vere F6C.17; lo scurimento F6D.1; il nome con «⌄» e il menu del file F6C.19.
