# engine-scan

**Lo scanner di documenti** (4.0, F8.17, `scanner-documenti`): la scansione di Note e File di iOS
(`VNDocumentCameraViewController` di VisionKit) con il vetro dell'engine. La fotocamera trova i bordi del foglio dal vivo,
scatta da sola quando il foglio è fermo, raddrizza e pulisce la pagina; le pagine si mettono in fila, si riordinano col
dito, si ritagliano con la lente, e diventano un PDF o delle immagini consegnate **per URI**. Pacchetto
`dev.antigravity.fluidengine.scan`.

È un **modulo opt-in** su `engine-ui` con **una sola libreria**, CameraX (Apache-2.0). I bordi (le rette di Hough sui
bordi di Canny e la macchia chiara di Otsu, con lo stesso voto), la prospettiva, i filtri e il PDF sono **codice
dell'engine**: niente OpenCV (decine di MB), niente ML Kit. **Il modulo non dichiara il permesso `CAMERA`**: lo dichiara
l'app che usa la fotocamera vera (sotto, «La fotocamera e il permesso»).

**Il nucleo senza fotocamera** (F8.17d) è un modulo a parte, **`engine-scan-core`**: i bordi del foglio in una foto, la
geometria (gli angoli, la prospettiva, le proporzioni vere), il raddrizzamento e i filtri, nello stesso pacchetto
`dev.antigravity.fluidengine.scan`. Non ha librerie, manifest né permessi, e **non si chiede da solo**: arriva con
`engine-scan` (che lo porta con `api`, quindi le classi si usano come prima) e con `engine-viewer-ocr`, che lo usa per
«scansiona, poi leggi» (sotto, «La prospettiva fuori dallo scanner»).

**L'engine** fa la fotocamera di vetro, il riconoscimento e l'inseguimento del foglio, lo scatto automatico, il
raddrizzamento, i filtri, la fila delle pagine, il ritaglio e la consegna. **L'app** dice cosa vuole (PDF o immagini, il
filtro, lo scatto automatico), dove mettere il risultato e cosa farne, e dichiara la fotocamera.

Catalogo delle firme: [`../api/scan.md`](../api/scan.md) (generato). Il testo nelle pagine scansite («Leggi testo») è
`engine-viewer-ocr` ([`viewer-famiglie.md`](viewer-famiglie.md)); il visore che apre il PDF è in
[`../componenti/visori.md`](../componenti/visori.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| scansionare con una riga, come lo scanner di ML Kit (Universal Converter, Pampa Mail) | `rememberLauncherForActivityResult(FluidScanDocumentContract()) { r -> r?.pdf }` e `.launch(FluidScanRequest(output = FluidScanOutput.Pdf))` |
| lo scanner dentro una mia pagina (Pampa Notes, col mio tema e la mia navigazione) | `val state = rememberFluidScanState(FluidScanOptions(…))`, `val camera = rememberFluidCameraXScanCamera()`, `FluidDocumentScanner(state, camera, onDone = { r -> … }, onCancel = { … })` |
| le pagine come immagini (una nota con le foto) | `FluidScanOutput.Images` (o `PdfAndImages`) |
| il filtro di serie, lo scatto solo col tasto | `FluidScanOptions(filter = FluidScanFilter.BlackAndWhite, autoCapture = false)` |
| provare lo scanner senza fotocamera (prove, galleria, un telefono senza) | `FluidStillScanCamera(sheets = listOf({ foto1 }, { foto2 }), table = { tavolo })` |
| trovare il foglio in una foto già fatta | `FluidScanEdges.detect(bitmap)` → `FluidScanDetection(quad, confidence, aspect)` |
| raddrizzare e pulire una foto da me | `FluidScanImages.straighten(photo, quad)` e `FluidScanImages.filter(page, FluidScanFilter.Document)` |
| riportare sulla foto un punto della pagina raddrizzata (le righe lette, un riquadro) | `quad.inset(FluidScanImages.EdgeInset).pageMap()!!.photoPoint(u, v)` |
| passare il risultato a un'altra app | `result.grantTo(Intent(Intent.ACTION_SEND))`: gli URI col permesso di lettura |
| le licenze | `FluidScanLicenses` (CameraX) nella pagina «Licenze e crediti» |

## Lo scanner: `FluidDocumentScanner`

Una **pagina intera**, nera come la fotocamera di iOS, con tre schermate.

**La fotocamera.** L'anteprima riempie lo schermo (tagliando i bordi, come la fotocamera di iOS) ed è la sorgente del
vetro dei comandi. In alto «Annulla», la torcia (se c'è), il filtro (un menu che si trasforma, con i quattro filtri) e
«Auto» / «Manuale»; in basso la pila delle pagine fatte (l'ultima miniatura col conto), lo **scatto** da 72 dp (il
cerchio bianco nell'anello, come la fotocamera di iOS) e «Salva (3)». Al centro la **scritta che guida**, una capsula di
vetro che cambia per sostituzione sfocata (R7): «Inquadra un documento», «Avvicinati», «Tieni fermo», «Scansione…».

- **Il foglio trovato** si colora con l'accento al 28% e il contorno pieno, più deciso mentre si avvicina lo scatto. Si
  disegna nel layer: il riconoscimento a 15 fotogrammi al secondo **non ricompone niente**.
- **Lo scatto automatico** («Auto», di serie): il foglio fermo per **900 ms** (tremito sotto l'1,5% della diagonale fra
  due fotogrammi) scatta da solo, **una volta per foglio**: il prossimo scatto aspetta che il foglio sparisca per 350 ms
  o si sposti di un quinto, come VisionKit che non fotografa due volte la stessa pagina. Le misure sono in
  `FluidScanTrackerDefaults`, a occhio sulle riprese di Note di iOS 18 e 26 (fiducia media).
- **Lo scatto**: un lampo bianco di 180 ms, i bordi **ricercati sulla foto vera** (valgono se distano da quelli dal vivo
  meno dell'8% della diagonale), e la pagina raddrizzata **che vola** dal foglio alla miniatura (il viaggio d'oro di
  F1B: nasce dal foglio, finisce nella pila). Col movimento ridotto niente lampo e niente volo.
- **La fotocamera che non c'è**: senza il permesso la pagina dice «Per scansionare serve la fotocamera» con «Consenti la
  fotocamera» (il permesso chiesto dal tasto, F8.11); senza fotocamera «La fotocamera non è disponibile».
- **Gli avvisi** (R1): uno scatto non riuscito («Scatto non riuscito, riprova») e la fila piena («Hai raggiunto il
  massimo di 100 pagine»: lo scatto non parte) prendono per 2,5 s il posto della scritta che guida, e TalkBack li dice.
  Dopo uno scatto TalkBack dice «Pagina 3 scansionata» col numero della pagina, anche dopo «Riprendi».

**La revisione** (tocco sulla pila, o `openReview = true` con pagine già fatte): le pagine una per volta, «2 di 5» in una
capsula, «Salva»; in basso **la fila delle miniature** e la capsula dei comandi.

- **La fila**: tocca una miniatura per andarci; **tienila premuta e trascinala** per spostarla (il riordino di
  `engine-ui`, `fluidReorderable`, con «Sposta prima» e «Sposta dopo» per TalkBack). La pagina che si guarda resta quella
  anche se cambia posto.
- **Ritaglia** apre il ritaglio; **Ruota** gira la pagina di un quarto; **Filtro** apre le quattro anteprime della pagina
  (Documento, Scala di grigi, Bianco e nero, Foto, come VisionKit); **Riprendi** torna alla fotocamera e rifà quella
  pagina allo stesso posto; **Elimina** la toglie (l'ultima torna alla fotocamera).
- Lo scatto di **Riprendi** prende il posto della foto solo quando la pagina nuova è pronta: tornando indietro mentre si
  scrive resta la pagina di prima, foto compresa (R1). Una pagina che non si è potuta pulire (una foto rotta, il disco
  pieno) dice «Pagina non leggibile»: Riprendi la rifà, Elimina la toglie.
- Niente sfocature su pagine e fila: le muove il dito (F8).

**Il ritaglio**: la foto intera, il foglio con un velo scuro fuori, **i quattro angoli da trascinare** (cerchi da 22 dp
in bersagli da 48) e **la lente** da 110 dp a 2,5× sopra il dito, con la croce nel punto. Un angolo che renderebbe il
foglio non convesso si ferma. «Tutta la foto» e «Rileva i bordi» rifanno il foglio; TalkBack sposta un angolo dell'1% con
quattro azioni.

```kotlin
@Composable fun FluidDocumentScanner(
  state: FluidScanState, camera: FluidScanCamera,
  onDone: (FluidScanResult) -> Unit, onCancel: () -> Unit,
  modifier: Modifier = Modifier, strings: FluidScanStrings = FluidScanStrings(), openReview: Boolean = false,
)
```

«Annulla» con pagine già fatte chiede «Eliminare le 3 scansioni?» («Elimina» in rosso, «Continua a scansionare»).
L'attività del contratto (`FluidScanActivity`) disegna fino al bordo e spegne il velo di contrasto della barra a tre
tasti, anche dopo una rotazione (R1, la regola delle app dell'engine).

## La consegna: `FluidScanResult`

«Salva» scrive le pagine pulite e la consegna nella cache dell'app (`cache/fluid-scan/<sessione>/`) e la passa a
`onDone`:

```kotlin
data class FluidScanResult(val pdf: Uri?, val images: List<Uri>, val pageCount: Int, val title: String,
  val pdfFile: File? = null, val imageFiles: List<File> = emptyList())
```

- Gli **URI sono `content://` del FileProvider del modulo** (`${applicationId}.fluidscan`, non esportato), in sola
  lettura: l'app li apre col `ContentResolver`, li copia dove vuole, o li passa a un'altra app con `grantTo(intent)`.
  `pdfFile` e `imageFiles` sono i file per chi resta dentro l'app.
- **Il PDF** è scritto dall'engine: PDF 1.4, una pagina per foglio, il JPEG della pagina dentro com'è (`DCTDecode`). Il
  `PdfDocument` di Android ricomprimerebbe senza perdita: una pagina peserebbe megabyte. Il formato della pagina è A4 o
  Letter se le proporzioni ci stanno entro il 3%, altrimenti la larghezza di un A4 e l'altezza della pagina, come Note.
  PdfRenderer (i visori), Anteprima e Acrobat lo leggono.
- Il titolo di serie è «Scansione 25 nov 2026, 14.32» nella lingua del telefono (`FluidScanOptions.title` lo cambia).
- I file restano finché l'app chiama `state.clear()` o Android svuota la cache: chi li vuole tenere li copia.
  `clear()` li cancella in sottofondo anche se la schermata si è già chiusa, dopo le pagine che si stavano scrivendo (R1).
- **«Salva» che non riesce** (R1): una pagina che non si legge o il documento che non si scrive (il disco pieno) non
  chiudono lo scanner: un avviso dice quali pagine rifare. `state.export()` rifà una volta le pagine fallite e lancia
  `IOException`, col numero delle pagine nel messaggio; la scansione resta com'è.

**Universal Converter** apre il PDF (o le immagini) con l'URI che riceve, come oggi da «Apri con…». **Pampa Notes** mette
le immagini (`FluidScanOutput.Images`) nella nota, o il PDF come allegato.

## La fotocamera e il permesso

`FluidScanCamera` è il contratto: l'anteprima, i fotogrammi d'analisi (in toni di grigio, col verso del sensore) e lo
scatto. Due di serie:

- **`rememberFluidCameraXScanCamera()`**: CameraX 1.6, la fotocamera posteriore, **4:3** per anteprima, analisi
  (640 × 480, l'ultimo fotogramma) e scatto (al più 8 MP, qualità massima). L'anteprima è una `TextureView`
  (`PreviewView` in modo `COMPATIBLE`): il vetro dei comandi la campiona. Stacca **solo le sue sorgenti**
  (`unbind(preview, analysis, still)`, R1): il provider di CameraX è uno per processo, e l'anteprima dell'app ospite
  resta. Se l'app tiene già la fotocamera e le sorgenti insieme non ci stanno, `problem` dice «non disponibile».
- **`FluidStillScanCamera(sheets, table)`**: foto di fogli già scattate, una per volta, con la mano che trema un poco e
  si ferma, e il tavolo vuoto fra un foglio e l'altro. Per le prove, la galleria (che non chiede la fotocamera) e un
  telefono senza fotocamera.

L'app che usa la fotocamera vera scrive nel suo manifest:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera.any" android:required="false" />
```

Lo scanner chiede il permesso col tasto, quando serve. Il modulo non lo dichiara: la galleria Fluid Glass include
`engine-scan` con la fotocamera finta e resta senza `CAMERA` (lo controlla `checkArViewerManifests`).

## Bordi, filtri, misure

- **I bordi** (`FluidScanEdges`): il fotogramma ridotto a 320 px sul lato lungo, i livelli stirati fra il 2° e il 98°
  percentile (la poca luce), una sfocatura gaussiana, Sobel e Canny con soglie dai percentili; poi due strade che si
  votano allo stesso modo: **le rette** (Hough pesato col gradiente, i 24 picchi, le coppie di lati opposti entro 35°) e
  **la macchia chiara** (Otsu, la componente più grande, il quadrilatero di area massima nel suo inviluppo). Il voto è la
  parte dei lati che poggia su un bordo vero (ogni lato almeno al 30%), con un premio al foglio più grande; i lati si
  rifiniscono coi minimi quadrati. Un buffer per riconoscitore: niente allocazioni per fotogramma.
- **Le proporzioni vere** (`FluidScanQuad.estimatedAspect`): il metodo di Zhang e He (2007) stima la focale dai quattro
  angoli e da lì il rapporto; se non regge, la media dei lati opposti.
- **I filtri** (`FluidScanFilter`): «Documento» (di serie) divide ogni pixel per **la luce della carta** stimata zona per
  zona (il 90° percentile di una griglia di 40 × 40 celle, allargato e sfumato): la carta diventa bianca anche
  all'ombra, l'inchiostro tiene il colore; «Scala di grigi» uguale in grigio; «Bianco e nero» con la soglia di Bradley sui
  dintorni; «Foto» la foto raddrizzata e basta.
- **Le misure sulle foto di prova** (`ScanCoreTest`, foto generate da `tools/scan-samples/genera.py`: luce buona e
  cattiva, prospettiva, rotazione, ombra della mano, basso contrasto, scontrino, tavolo vuoto): nel rapporto di F8.17.
- **Il bordo** (F8.17d): prima di raddrizzare il foglio rientra dello 0,5% della pagina su ogni lato
  (`FluidScanImages.EdgeInset`, misurato sulla pagina dritta, `FluidScanQuad.inset`). I bordi trovati sbagliano al più
  dello 0,27% della diagonale e lasciavano un filo di tavolo che il filtro «Documento» faceva nero e tratteggiato (visto
  sul Tab S9): sulle foto di prova la striscia più scura del bordo passa da 1–74 a 236–255 su 255. Un A4 perde un
  millimetro per lato; `straighten(…, edgeInset = 0f)` raddrizza gli angoli esatti.
- **Le etichette** dei comandi tondi (Flash, Filtri, Automatico) hanno un'ombra corta (nera al 55%, 1 dp in basso,
  sfumata di 4 dp), perché si leggano anche sopra un foglio chiaro che riempie l'inquadratura (F8.17d).
- **La pagina pulita** ha al più 2400 px sul lato lungo (circa 205 dpi su un A4) e si scrive in JPEG a qualità 85: una
  pagina A4 pesa 300–600 KB. In memoria stanno solo le miniature (480 px).

## La prospettiva fuori dallo scanner (F8.17d)

`FluidScanQuad.pageMap()` dà la prospettiva di un foglio (`FluidScanPageMap`): `photoPoint(u, v)` porta un punto della
pagina raddrizzata (0..1) nella foto (0..1). È il raddrizzamento al contrario, e serve a riportare sulla foto quello che
si trova sulla pagina: «Leggi testo» legge la pagina raddrizzata di una foto storta e riporta ogni riga sulla foto,
storta come il foglio. Va presa sul foglio **rientrato** come lo raddrizza `straighten`:

```kotlin
val sheet = FluidScanEdges.detect(photo)?.quad ?: return
val page = FluidScanImages.straighten(photo, sheet)               // rientra di EdgeInset
val map = sheet.inset(FluidScanImages.EdgeInset).pageMap()!!      // la stessa pagina
val onPhoto = map.photoPoint(0.5f, 0.5f)                          // il centro della pagina sulla foto
```

## Cosa costa

Misurato da F8.17 nella bench della galleria con R8: **935,7 KB di DEX** (il modulo, il nucleo e CameraX core, camera2,
lifecycle e view), uguali per ogni architettura; nessuna libreria nativa e nessun asset. CameraX 1.6 chiede **compileSdk 36 e AGP
8.9.1** all'app ospite e porta **kotlinx-coroutines 1.9.0**.

## Trappole

- *Sintomo*: «Per scansionare serve la fotocamera» anche dopo «Consenti». → Il manifest dell'app non ha `CAMERA`: Android
  rifiuta senza chiedere.
- *Sintomo*: gli URI della consegna non si aprono in un'altra app. → Vanno passati con `grantTo(intent)` (o col contratto,
  che lo fa da sé): il FileProvider non è esportato.
- *Sintomo*: la fotocamera nera sotto i comandi di vetro. → Un'anteprima mia in una `SurfaceView`: il vetro campiona solo
  una `TextureView` o Compose.
- *Sintomo*: i file della scansione spariscono. → Stanno nella cache: chi li tiene li copia in `onDone`.

## Come si prova

- `ScanCoreTest` di `engine-scan-core` (Robolectric, grafica nativa): i bordi sulle foto di prova contro gli angoli veri
  (entro il 2% della diagonale con la luce buona, il 3,5% con quella cattiva), nessun foglio sul tavolo vuoto, il
  fotogramma girato dal sensore, le proporzioni vere di un A4 in prospettiva, l'ordine degli angoli, il raddrizzamento, i
  filtri che tolgono l'ombra, la prospettiva della pagina che torna sulla foto (gli angoli e l'incrocio delle
  diagonali), il bordo della pagina senza il filo del tavolo.
- `ScanOutputTest` di `engine-scan`: il PDF con la tabella xref giusta, lo scatto automatico una volta per foglio e mai
  con la mano che trema.
- `ScanStateR1Test`, `ScanScannerR1UiTest` e `ScanActivityR1Test` (R1): «Riprendi» annullato a metà, «Butta» con la
  schermata chiusa o una pagina che si scrive, la pagina che non si legge, `working` dopo una pagina tolta, gli avvisi
  sulla fotocamera e l'annuncio dopo «Riprendi», «Salva» con una pagina rotta, il velo della barra, le sorgenti di CameraX.
- `FluidDocumentScannerUiTest` (Robolectric, Compose): due fogli scattati da soli dalla fotocamera finta, la fila
  riordinata **col dito** (tieni premuto e trascina), un angolo ritagliato, il filtro e la rotazione, il PDF consegnato
  per URI; in manuale niente scatti da soli, col movimento ridotto, e le etichette di TalkBack.
- L'app di prova **«Pampa · prova scanner»** (`engine-scan/prova`, `:engine-scan-prova`): la fotocamera vera col
  contratto, il risultato nel visore con «Leggi testo», e la misura di Tesseract e ML Kit sul telefono.
- La galleria: «Elementi nuovi › Documenti».

## Dove guardare

| cosa | file |
|---|---|
| lo scanner, le parole, i tag | `engine-scan/.../scan/FluidDocumentScanner.kt` |
| la fotocamera di vetro, il volo della pagina | `ScanCameraScreen.kt` |
| la revisione e la fila | `ScanReviewScreen.kt`; il ritaglio con la lente: `ScanCropScreen.kt` |
| i bordi (in `engine-scan-core`) | `FluidScanEdges.kt`; la geometria, la prospettiva e le proporzioni: `FluidScanGeometry.kt` |
| l'inseguimento e lo scatto automatico | `FluidScanTracker.kt` |
| raddrizzamento, bordo e filtri (in `engine-scan-core`) | `FluidScanImages.kt` |
| le pagine sul disco, la consegna | `FluidScanState.kt`, `FluidScanOutput.kt` (il PDF) |
| la fotocamera | `FluidScanCamera.kt` (contratto e finta), `FluidCameraXScanCamera.kt` |
| il contratto con l'attività | `FluidScanActivity.kt` |
| le foto di prova | `tools/scan-samples/genera.py` → `engine-scan-core/src/test/resources/scan/` (le usano anche le prove di `engine-scan`) |
