# I moduli di famiglia dei visori: engine-viewer-office, -archive, -data, -graphics, -3d, -ar, -ocr, -all

**Il visore universale, a pezzi.** Ogni famiglia di tipi è un modulo facoltativo che si registra da sola presso il registro di [`engine-viewer`](viewer.md): basta la dipendenza Gradle, niente codice nell'`Application`. Un'app che non aggiunge un modulo **non ne paga il peso**, e quel tipo resta sconosciuto con «Apri con…». `engine-viewer-all` li raccoglie tutti, per chi (come Universal Converter) vuole aprire ogni file. Il telaio e i preset che li mostrano sono in [`../componenti/visori.md`](../componenti/visori.md). Dalla 4.0 (seconda ondata di F6C, 22 novembre 2026).

Modello mentale: **il telaio mostra, il registro sceglie, la famiglia apre, l'app possiede.** Una famiglia riceve una `FluidViewerSource` e restituisce un `FluidViewerOpened`: il contenuto (`Pages`, `Grid`, `Image`, `Custom`…), i dettagli per il pannello info e la chiusura.

## Scegliere in fretta

| l'app deve aprire… | modulo | pacchetto Kotlin |
|---|---|---|
| DOCX, ODT, RTF a pagine; XLSX, ODS in griglia; PPTX, ODP a slide | `engine-viewer-office` | `dev.antigravity.fluidengine.viewer.office` |
| ZIP, 7z, RAR 4 e 5, TAR (anche `.tgz`, `.tar.xz`, `.tar.zst`), `.gz`, `.bz2`, `.xz` | `engine-viewer-archive` | `…viewer.archive` |
| Markdown, HTML, CSV, TSV, JSON, NDJSON, XML, plist, EPUB, EML, ICS, VCF | `engine-viewer-data` | `…viewer.data` |
| HEIC e AVIF (anche sotto Android 9 e 12), RAW, TIFF, PSD, SVG; TTF, OTF, TTC, WOFF | `engine-viewer-graphics` | `…viewer.graphics` |
| GLB, glTF, OBJ, STL con la luce e l'ombra, con Filament | `engine-viewer-3d` | `…viewer.scene` |
| il modello 3D posato nella stanza con ARCore | `engine-viewer-ar` (porta `engine-viewer-3d`) | `…viewer.ar` |
| leggere il testo di foto, schermate e PDF scansiti («Leggi testo»: selezione, copia, ricerca) | `engine-viewer-ocr` (un'estensione, non un tipo) | `…viewer.ocr` |
| tutto quello che c'è sopra | `engine-viewer-all` | — (niente codice) |

## Come si installa

Con lo script, una famiglia o tutte (le dipendenze si chiudono da sole: ogni famiglia porta `engine-viewer`, `engine-ui` e `engine-foundation`; l'AR porta anche il 3D):

```powershell
powershell -ExecutionPolicy Bypass -File engine\tools\engine-install.ps1 -AppRoot . -Modules engine-ui,engine-viewer-office
powershell -ExecutionPolicy Bypass -File engine\tools\engine-install.ps1 -AppRoot . -Modules engine-ui,engine-viewer-all
```

Lo script legge `engine-viewer/families.json` (la stessa lista che controlla la build) e stampa ogni famiglia con il peso, le licenze e la riga da incollare. A mano:

```groovy
// settings.gradle dell'app, accanto agli altri moduli dell'engine
include ':engine-viewer', ':engine-viewer-office'
project(':engine-viewer').projectDir = file('engine/engine-viewer')
project(':engine-viewer-office').projectDir = file('engine/engine-viewer-office')

// build.gradle dell'app
implementation project(':engine-viewer-office')     // oppure project(':engine-viewer-all')
```

Un `engine-viewer-all` senza una famiglia (la galleria lo usa senza l'AR, che chiede la fotocamera):

```groovy
implementation(project(':engine-viewer-all')) { exclude module: 'engine-viewer-ar' }
```

**Niente da configurare per aprire.** `rememberFluidViewerFiles` e `FluidViewerFile.open` passano dal registro appena c'è una famiglia. Restano dell'app: il pannello info (sotto), le licenze, e le impostazioni delle famiglie che ne hanno (gli archivi per «Estrai tutto», i dati per le risorse di un HTML). Una famiglia con impostazioni si installa a mano, e sostituisce quella trovata:

```kotlin
FluidViewerRegistry.install(FluidArchiveFamily(onExtractAll = { extraction -> salvaInCartella(extraction) }))
FluidViewerRegistry.install(FluidDataFamily(FluidDataSettings(resources = { source, path -> cartellaDelFile(source, path) })))
```

**Il pannello info**: il telaio non sa leggere `FluidViewerDocument.details`, lo traduce l'app con la funzione della famiglia.

```kotlin
FluidViewerInfoProvider(info = { i ->
  val document = files.document(i) ?: return@FluidViewerInfoProvider null
  when (document.type.family) {
    "office" -> document.officeInfo()
    "archive" -> document.fluidArchiveViewerInfo()      // composable; null finché l'elenco non è letto
    null -> InfoDiSerie(document)                         // foto, PDF, testo: i metadati li legge l'app
    else -> fluidDataViewerInfo(document, azioni)         // dati, grafica, 3D: fatti senza sezione, poi le sezioni
  }
}) { FluidFileViewer(count = files.count, content = files::content) }
```

**Le licenze**: ogni modulo con librerie o caratteri di terzi espone la sua lista, che si aggiunge a quella dell'engine nella pagina «Licenze e crediti» dell'app.

```kotlin
fluidLicensesSection(licences = FluidEngineLicenses + FluidOfficeLicenses + FluidArchiveLicenses +
  FluidGraphicsViewerLicenses + FluidSceneViewerLicenses + FluidArViewerLicenses)
```

## I pesi

Misurati da F6C.15 nella bench della galleria (R8 come la release, firmata di debug), il 23 novembre 2026: per ogni modulo il **DEX** del suo pacchetto e delle sue librerie (con `apkanalyzer dex packages` e la mappa di R8), gli **asset** e le **librerie native** di ogni architettura, dalle voci dell'APK. I numeri stanno nella voce `apk` di ogni famiglia in `engine-viewer/families.json`, e la pagina «Pesi» della galleria li legge da lì. La frase `weight` di ogni famiglia resta quella del suo pacchetto, con il suo metodo.

| modulo | arm64-v8a | armeabi-v7a | x86 | x86_64 | di cui | licenze |
|---|---:|---:|---:|---:|---|---|
| `engine-viewer-office` | 837,8 KB | 837,8 KB | 837,8 KB | 837,8 KB | DEX 103,3 KB; i caratteri Carlito, Caladea, Liberation (solo latino) 734,5 KB nell'APK (1,29 MB grezzi); nessuna libreria nativa | caratteri OFL-1.1 |
| `engine-viewer-archive` | 2,4 MB | 1,9 MB | 2,7 MB | 2,7 MB | `libarchive-jni.so` 2.406.200 B (arm64), DEX 118,7 KB con il ripiego dei 7z cifrati, le licenze 20,1 KB | libarchive BSD-2-Clause, il collegamento Android Apache-2.0; dentro zlib, bzip2, xz (0BSD), lz4, zstd, Mbed TLS; Commons Compress, IO, Codec, Lang Apache-2.0, XZ for Java 0BSD |
| `engine-viewer-data` | 101,0 KB | 101,0 KB | 101,0 KB | 101,0 KB | solo DEX: nessuna libreria, uguale per ogni architettura | nessuna libreria di terzi |
| `engine-viewer-graphics` | 186,5 KB | 186,5 KB | 186,5 KB | 186,5 KB | solo DEX, con AndroidSVG ed ExifInterface | Apache-2.0 |
| `engine-viewer-3d` | 6,4 MB | 4,9 MB | 7,1 MB | 6,9 MB | le librerie native di Filament (`libfilament-jni.so`, `libgltfio-jni.so`, `libfilament-utils-jni.so`) 6.645.392 B (arm64), DEX 46,6 KB, l'ambiente IBL e il piano d'ombra 52,4 KB | Filament Apache-2.0; ambiente e ombra originali, licenza dell'engine |
| `engine-viewer-ar` | 317,0 KB | 263,4 KB | 327,2 KB | 317,0 KB | misurato da F6C.16 nel suo APK di prova, senza R8: DEX 143,3 KB (il modulo e ARCore), il materiale della fotocamera 8,9 KB, il JNI di ARCore; Filament è contato nel 3D | ARCore: condizioni aggiuntive di Google; materiale originale |
| `engine-viewer-ocr` | 16,1 MB | 1,6 MB | 1,6 MB | 16,6 MB | misurato da F8.17b nella bench con R8 (bytedeco 1.5.14): le librerie native di Tesseract, Leptonica e JavaCPP 14.468.480 B (arm64; bytedeco non dà le librerie a 32 bit, dove «Leggi testo» non compare), il modello `ita.traineddata` 1,28 MB nella voce compressa (2,7 MB grezzo), DEX 320,9 KB (i collegamenti tenuti per JavaCPP) | Tesseract, JavaCPP Apache-2.0; Leptonica BSD-2-Clause e le sue librerie d'immagine; modello Apache-2.0 |

Per confronto, `engine-viewer` da solo (il registro, PDF, testo, GIF, Media3) porta 65,5 KB di DEX. Il DEX conta le classi del pacchetto del modulo e delle sue librerie: il codice che R8 fonde in classi di altri pacchetti (alcune lambda) resta fuori, quindi è una stima per difetto. Lo script della misura è `artifacts/liquid-glass/sessioni/f6c-15/strumenti/misura-pesi.py` del worktree dei visori; i numeri sono nella voce `apk` di `families.json`.

Le attese della ricerca, per arm64 (`visori-2.md`), contro la misura: archivi 2,35 MB (2,4), 3D circa 7 MB (6,4), Office circa 1,5 MB con i caratteri latini (0,84), gli altri sotto 0,5 MB (dati 0,10, grafica 0,18).

## La build lo controlla (F6C-h)

- **`checkViewerFamilies`**, prima dei test di `engine-viewer` e di `engine-viewer-all`, legge i classpath risolti: `engine-ui` non arriva a nessuna famiglia né a una libreria di decodifica; `engine-viewer` non arriva a nessuna famiglia; ogni famiglia porta solo le librerie della sua **lista ammessa** in `families.json`, con licenza e peso. Una libreria nuova fuori lista ferma la build. Prima di guardare il grafo vero, il controllo si prova da solo su sette grafi finti.
- **R8 tiene le famiglie** senza regole a mano: con le ottimizzazioni riscrive `ServiceLoader.load(…)` in una lista con la famiglia; con `-dontoptimize` tiene `META-INF/services` rinominato; senza il modulo non resta nessuna classe. Lo prova l'app di prova `:engine-viewer-prova-app` (`checkViewerFamilyApks`, tre sapori: il modulo di prova costa 123 byte).
- **`checkArViewerManifests`**: `CAMERA` e `com.google.ar.core=optional` stanno solo nell'APK con `engine-viewer-ar`, mai in quello del 3D da solo né nella galleria.
- Le `.so` di libarchive, Filament, ARCore e (da F8.17b, con bytedeco 1.5.14) Tesseract, Leptonica e JavaCPP sono
  allineate a 16 KB: segmenti `LOAD` a 16384 e voci allineate nello zip. Si controlla un'APK con
  `python -I tools/allineamento-16kb.py <apk>`; bytedeco 1.5.11 e 1.5.12 erano a 4 KB (Android 16 lo segnala a ogni avvio
  di un'app di debug, Google Play non le accetta per le app per Android 15 e oltre).

## engine-viewer-office

**Cosa apre.** DOCX, ODT e RTF con un modello comune (paragrafi, tabelle, figure, sezioni) e un impaginatore Android: pagine come il documento, con il conteggio che cresce mentre impagina (`Pages` con `countFinal`). XLSX e ODS nella `Grid`, con le schede colorate, le righe e colonne bloccate, le larghezze di SpreadsheetML. PPTX e ODP a slide, una per pagina, nel formato della presentazione. La miniatura per la griglia dell'app (`thumbnail`) è quella salvata nel documento solo se dice qualcosa: se manca, è rotta, minuscola (meno della metà della misura chiesta) o quasi tutta di un colore, come le miniature bianche che Word e PowerPoint salvano spesso, il modulo disegna la prima pagina, la prima slide o l'inizio del primo foglio con il suo impaginatore, entro 2 s, come Quick Look (F6C.15b). La griglia dell'app non mostra mai un riquadro bianco. Un documento cifrato o troncato va allo sconosciuto col motivo.

**I caratteri con le misure di Word** (scelta di Alessio del 22 novembre): Carlito al posto di Calibri, Caladea di Cambria, Liberation Sans, Serif e Mono di Arial, Times New Roman e Courier New, solo latino (1,25 MB). Per i documenti in quei caratteri lo stesso numero di pagine e gli stessi a capo del PDF di Word, sul corpo di prova. Aptos, i caratteri senza gemello e le scritture non latine (greco, cirillico, arabo, CJK) vanno in sostituzione, con l'a capo approssimato, e il pannello lo dice («caratteri sostituiti»).

**API.** `FluidOfficeFamily`; `FluidViewerDocument.officeInfo()` per il pannello (autore, date nella lingua e nel fuso del dispositivo, applicazione, parole, misure della pagina, caratteri e sostituzioni, fogli e formule, slide, note, oggetti omessi); `FluidOfficeLicenses`.

**Limiti.** Le corrispondenze dei caratteri sono misurate solo sul corpo di prova. Intestazioni, piè e note hanno un corpo semplificato; i blocchi ancorati stanno al paragrafo d'ancoraggio; campi complessi, caselle di testo, più colonne e tabelle annidate vanno ancora coperti; i collegamenti nel testo non si attivano. I fogli mostrano i risultati di formula salvati (niente ricalcolo; per ODS il testo di presentazione), con tetti alle parti e alle celle; un formato numero oltre 255 caratteri (il tetto di Excel) non si applica e il numero si mostra com'è, e i formati si preparano una volta per foglio (R2). Grafici, SmartArt e alcuni oggetti diventano un segnaposto col titolo. Prestazioni sul Tab S9: la prima pagina del documento di 300 pagine in 431 ms, il foglio di 100.000 celle in 65 ms, al più +128 MiB di memoria.

## engine-viewer-archive

**Cosa apre.** Con libarchive (`me.zhanghai.android.libarchive` 1.1.7, libarchive 3.8.8): ZIP (anche AES), 7z, RAR 4 e 5, TAR e i compressi. La pagina di «D · Mix»: la tinta leggera, le cartelle come tessere di vetro, i file in un gruppo pieno, il percorso delle cartelle annidate, l'ordinamento e i filtri. **Un file dentro si apre col suo preset** attraverso lo stesso registro (un PDF a pagine, un'immagine, un DOCX se c'è Office, altrimenti lo sconosciuto con «Apri con…»), e Indietro torna nella cartella, alla stessa posizione. I 7z cifrati passano da Commons Compress e XZ (il ripiego si toglie con `-PfluidArchiveSevenZipAes=false`); un RAR cifrato dice «Archivio protetto» con «Apri con…»; un archivio danneggiato tiene le voci lette, con l'avviso.

**API.** `FluidArchiveFamily(onExtractAll)`: «Estrai tutto» c'è solo se l'app passa la callback, che riceve una `FluidArchiveExtraction` (le voci `FluidArchiveEntry`, `writeFiles` con l'avanzamento `FluidArchiveProgress`; solo i file regolari con percorso relativo). `fluidArchiveViewerInfo()` per il pannello (con la versione di libarchive); `FluidArchiveLicenses`. Un `FileProvider` del modulo, non esportato, concede la sola lettura delle anteprime mandate fuori su richiesta.

**Sicurezza e limiti.** Niente percorsi assoluti né con `..` (zip slip); i collegamenti simbolici e gli hard link si vedono ma restano inattivi; anteprima al più 128 MiB per voce, lettura complessiva al più 512 MiB e 10.000 voci, rapporto al più 200:1 sopra 1 MiB, controllato durante la copia (zip bomb); la cache usa nomi casuali. Uno ZIP senza nomi Unicode parte in CP437, e il selettore offre Shift-JIS e UTF-8. Il ripiego 7z decodifica al più 64 MiB, con AES al più 2^24 iterazioni; un 7z cifrato che non si legge nemmeno senza password (tronco, intestazione rovinata) dice «Archivio danneggiato» invece di richiedere la password, mentre un errore dentro i dati cifrati resta «password sbagliata», come in 7-Zip (R2).

**La dipendenza nativa.** L'AAR Maven dichiara `minCompileSdk=37`, mentre l'engine compila con SDK 36: il modulo verifica l'impronta dell'AAR (`prepareLibarchive`), include i binari JNI intatti e controlla i riferimenti contro `android.jar` 36 (`checkLibarchiveAndroidApi36`). Va tolto quando l'engine compilerà con SDK 37.2.

## engine-viewer-data

**Cosa apre**, tutto con letture proprie, **senza nessuna libreria**:
- **Markdown** con `FluidMarkdown` in una lista pigra, e «Reso | Sorgente»; le immagini restano testo alternativo.
- **HTML** in `FluidWebContent`, con JavaScript spento e **nessuna rete**: le risorse relative vengono dalla cartella che l'app concede (`FluidDataSettings.resources`), le remote sono bloccate e contate («Carica immagini» c'è solo se l'app dà il client). In tema scuro una pagina senza colori suoi prende quelli del tema; una con i suoi colori li tiene.
- **CSV e TSV** nella `Grid`, letti a flusso (100.000 righe): il separatore si riconosce (`,` `;` tab `|`), le virgolette seguono RFC 4180, l'intestazione resta ferma.
- **JSON, JSONC, NDJSON, XML e plist** (anche binario) come albero, con un punto colorato per tipo, «Mostra altri 100», «Albero | Testo»; oltre 150.000 nodi la lettura si ferma; un errore mostra riga e colonna con «Mostra nel testo».
- **EPUB** 2 e 3 a capitoli, con l'indice in un foglio e «Capitolo 3 di 12 · 24%»; con DRM la pagina «Protetto da DRM».
- **EML**: la scheda delle intestazioni, il corpo e gli allegati, che si aprono col registro dentro la pagina («‹ Torna al messaggio»).
- **ICS** e **VCF** come schede: l'evento con la tessera della data, i fusi, la giornata intera e le ripetizioni dette in italiano; i contatti con la foto. «Aggiungi al calendario» e «Aggiungi ai contatti» sono l'«Apri con…» dell'app.

**API.** `FluidDataFamily(settings)` con `FluidDataSettings(resources, remoteImages, openLink, openFile, strings)` e `FluidDataStrings`; `fluidDataViewerInfo(document, actions, thumbnail)`, che va bene per ogni famiglia che mette i fatti senza sezione e il resto in sezioni.

**La regola «nessuna rete».** Il modulo non chiede `INTERNET` e non ha API di rete: tutto passa dal contenitore chiuso di `FluidWebContent`. Sul Tab S9 la prova con un server finto ha contato zero richieste.

**`Custom` dentro `Custom`.** Il telaio ha una barra sola per pagina e vince l'ultimo preset che la registra: un allegato aperto dentro una mail prende la barra, e la mail la riprende quando lo si chiude. Se la barra del telaio diventasse una pila, il giro non servirebbe più.

**Limiti.** Niente «Trova» nell'albero; gli EPUB a impaginazione fissa scorrono. I tetti dei file costruiti apposta (R2): una cella di CSV mostra al più 1.000 caratteri, un blocco di righe si legge al più 4 MiB alla volta e di una riga al più 1 MiB (oltre, l'ultima cella letta finisce con «…»); un CSV in UTF-16 o UTF-32 si trascrive in cache al più `FluidViewerOptions.sourceMaxCopyBytes`, e la trascrizione si ferma se l'apertura è annullata; JSON, XML e plist oltre 512 livelli sono un errore con la riga, e l'albero letto fin lì resta; una voce di EPUB tronca o col CRC sbagliato è un errore, non un capitolo a metà.

## engine-viewer-graphics

**Cosa apre.** HEIC e HEIF col decoder di Android 9, AVIF da Android 12 (sotto, il ripiego della famiglia: il loro tipo ha priorità −10, quindi parte solo se il decoder della piattaforma fallisce). RAW (DNG, CR2, CR3, NEF, ARW) con il JPEG della fotocamera e l'EXIF, e il pannello dice «sviluppo della fotocamera»; per il DNG si prova prima il decoder Android. TIFF di più pagine nel visore a pagine; PSD e PSB dal composito o dalla miniatura JPEG; SVG con AndroidSVG, netto a ogni zoom (anche SVGZ). I caratteri TTF, OTF, TTC e WOFF1 nel **provino**: l'esemplare grande, i fatti in capsule di vetro, la frase a 18, 28 e 42 sp, gli assi variabili come cursori, e nel pannello la tabella `name` (autore, versione, licenza), le tabelle e gli alfabeti. WOFF2 e i codec mancanti danno il motivo e «Apri con…».

**API.** `FluidGraphicsFamily`, `FluidGraphicsViewerLicenses`; il pannello con `fluidDataViewerInfo`.

**Limiti.** TIFF al più 512 pagine e 12 MP per pagina (16 MP al massimo; strisce o tasselli, 8 e 16 bit, grezzo, PackBits, LZW, Deflate). PSD composito grezzo o RLE (il CMYK con una conversione semplice). SVG al più 8 MiB anche decompresso; font al più 32 MiB.

## engine-viewer-3d

**Cosa apre.** GLB e glTF 2.0 con Filament 1.71.5 (gltfio), con i materiali PBR; STL (binario e ASCII) e OBJ (col MTL) diventano un GLB in memoria. Il modello sta in una `TextureView` sul fondo del tema, con l'ambiente IBL e l'ombra a terra; il vetro della chrome lo campiona (sfocato, mai nero). Un dito ruota, due zoomano e spostano, il doppio tocco e «Inquadra» rimettono la vista; le animazioni partono col tasto «Riproduci». Il pannello dice vertici, triangoli, materiali, texture, animazioni e misure. Alla chiusura `Engine` e risorse si liberano. Su un dispositivo dove Filament non si apre, la pagina lo dice con «Apri con…».

**API.** `FluidSceneFamily`, `FluidSceneViewerLicenses`; per un'app che vuole il modello nel suo renderer, `openFluidSceneModel(source)` → `FluidSceneModel` (`stats`: `FluidSceneStats`; `load(engine)` → `FluidLoadedScene`, da chiudere prima dell'engine sullo stesso thread). I file a fianco di un glTF arrivano da una `FluidSceneSource` (`openRelative(path)`); da una sorgente semplice un glTF con file esterni non si apre (la galleria mostra un GLB). `FluidSceneViewerExtension` è l'aggancio per l'AR.

**Limiti.** GLB e glTF al più 1.000.000 di triangoli, 3.000.000 di vertici e 96 MiB fra file e risorse; STL e OBJ al più 250.000 triangoli. Le texture si misurano dalle intestazioni prima di Filament (R2): al più 8192 pixel per lato e 64 Mi di pixel in tutto (256 MiB decodificati), in PNG, JPEG, KTX2 o WebP; un altro formato non si apre. Percorsi esterni, URL remoti e Draco danno un ripiego esplicito. USDZ resta sconosciuto.

## engine-viewer-ar

**Cosa fa.** Il tasto «AR» compare nella barra del 3D solo dove ARCore c'è (se mancano i servizi, offre Google Play); la fotocamera si chiede **dopo il tocco** e una spiegazione. Il modello si posa con un tocco su un piano orizzontale, o con «Posa al centro», a 1,0× con la base sul piano (glTF e GLB in metri, OBJ e STL un metro per unità); il pizzico va da 0,1× a 10×, due dita lo girano attorno alla verticale, il trascinamento segue il piano. Riusa l'ambiente e l'ombra del 3D; il renderer 3D si sospende mentre l'AR è in primo piano, e Back o «Torna al modello» rilasciano tutto. Non registra immagini né audio; Cloud Anchors e Geospatial sono spenti. Serve Android 8.1.

**API.** `FluidArSceneExtension` (si scopre da sola con `ServiceLoader` dal 3D), `FluidArViewerLicenses`.

**Da sapere.** Il modulo dichiara `CAMERA` (con la fotocamera facoltativa) e `com.google.ar.core=optional` nel manifest: un'app che lo include chiede la fotocamera agli utenti che toccano «AR», e la sua scheda nello store lo dice. Per questo la galleria Fluid Glass non lo include.

## engine-viewer-ocr

**Cosa fa.** «Leggi testo» nei visori, come **Testo attivo** di iOS (Live Text, VisionKit `ImageAnalysisInteraction`, in
Foto, Anteprima e Quick Look): le foto e le schermate (`FluidViewerContent.Image`) e i documenti a pagine (i PDF
scansiti) ricevono da soli il riconoscimento del testo, **sul telefono e mai in rete**. Non apre un tipo nuovo: è
un'**estensione dei visori** (`FluidViewerExtension` di [`engine-viewer`](viewer.md)), che si scopre con `ServiceLoader`
come le famiglie. Dei documenti a pagine tocca solo i **PDF**: le pagine di Office hanno già il loro testo e capacità
loro (le note del relatore) che un involucro nasconderebbe. Basta la dipendenza (o `engine-viewer-all`): `rememberFluidViewerFiles` la usa. Dalla 4.0 (F8.17).

**Come si comporta.**
- La pagina **ferma da 450 ms** si legge in sottofondo (una lettura alla volta nel processo, tutta fuori dal filo
  principale; le librerie native di Tesseract si caricano nel suo filo alla prima lettura; i risultati restano in
  memoria, un file riaperto è già letto). Se c'è testo compare **il tasto di Testo attivo** in basso a destra, sopra la fila
  dei comandi, nello spazio dello schermo (`FluidViewerPageAccessory` di `engine-ui`): compare sfocato e si fa nitido
  (arriva da solo, R7), si ritira con le barre al tocco.
- **Il tasto** accende la modalità: la foto si vela di nero al 45% e **le righe restano chiare** con un contorno bianco
  leggero; accanto compaiono «Testo» (l'elenco delle righe) e «Copia tutto», e il primo numero, mail o link diventa
  un'azione rapida nell'angolo in basso a sinistra (come VisionKit). La riga sta sempre nella larghezza dello schermo
  (F8.17b): il tasto c'è sempre intero; «Copia tutto» resta solo se all'azione rapida rimangono 160 dp, se no si usa
  quello del foglio «Testo»; l'azione rapida lunga finisce coi puntini e sotto 80 dp non compare. I comandi sono vetro
  sulla pagina del visore, che sfocano. Per TalkBack ogni riga diventa un elemento
  nel suo posto, nell'ordine della lettura, con l'azione «Copia la riga»; il tasto è un interruttore («attivo»/«spento»).
- **Tieni premuto su una parola** per selezionarla, anche con la modalità spenta (come in Foto); poi il dito allarga la
  selezione. Con la modalità accesa basta un tocco. **Le maniglie** di iOS (la sbarra col pallino) spostano i due capi; la
  selezione segue le righe (un pezzo di riga per riga toccata, storto come la riga in una foto storta). Sopra (o sotto) compare **il menu** di vetro con «Copia»,
  «Seleziona tutto» e «Condividi». Un tocco fuori la toglie.
- **Lo zoom, le pagine e le rotazioni**: il disegno sta **dentro** il riquadro dell'immagine che lo zoom del visore
  ingrandisce, quindi le righe seguono la foto da sole (pizzico, doppio tocco, spostamento, schermo girato); le maniglie
  e il menu restano della stessa misura a ogni zoom (la scala si legge nel disegno). Nei PDF il disegno sta sopra ogni
  pagina del preset (`FluidPageOverlays`), che cresce col pizzico. I riquadri sono in coordinate 0..1 dell'immagine
  dritta (EXIF applicato dallo stesso decodificatore del visore); `FluidTextQuad.rotated` li gira di quarti di giro.
- **«Scansiona, poi leggi»** (F8.17d): in una **foto storta di un foglio** (girato di più di 1,5° o in prospettiva, più
  di un quinto della foto, bordi trovati con fiducia almeno 0,8) si legge **la pagina raddrizzata** coi bordi e la
  prospettiva dello scanner (`engine-scan-core`), e ogni riga torna sulla foto con la stessa prospettiva: un
  quadrilatero storto come il foglio. Per chi guarda non cambia niente: il velo, la selezione, le maniglie e i tocchi
  seguono le righe vere (si tocca il quadrilatero della parola, non il rettangolo che la contiene). Sulla foto di prova
  della lettera Tesseract passa dal 41% di caratteri sbagliati (righe spezzate e mescolate) a pochi punti. Le schermate,
  le pagine già dritte e le foto senza foglio si leggono intere come prima. Solo le foto: le pagine dei PDF scansiti
  sono già dritte. `FluidLiveText.straightenSheets = false` lo spegne.
- **«Testo»** apre il foglio «Testo trovato»: il campo per **cercare nelle parole trovate** (con «3/12» e le frecce;
  senza maiuscole né accenti), i risultati in giallo sulla foto, e l'elenco delle righe con **numeri, indirizzi, mail e
  link toccabili** (`FluidDetectedText`); toccare una riga la seleziona sulla foto. Nei PDF scansiti c'è anche «Trova» del
  preset delle pagine (`FluidPageTextSearch`), che legge fino a 40 pagine.
- Il dito resta nitido (F8): niente sfocature su selezione e maniglie. Col movimento ridotto il velo e il tasto
  compaiono subito.

**Il riconoscimento.** `FluidTextRecognizer` è il contratto; di serie `FluidTesseractRecognizer`: **Tesseract 5.5**
(Apache-2.0; 5.5.3 coi collegamenti JavaCPP 1.5.14 di bytedeco, pronti per le pagine da 16 KB), da Maven Central, e il
modello LSTM «fast» dell'italiano
(`ita.traineddata`, Apache-2.0, 2,7 MB negli asset; riconosce anche l'inglese e le lingue con l'alfabeto latino). Prima
della lettura l'immagine passa in grigio con **la luce della carta pareggiata** (la stessa idea del filtro «Documento»
dello scanner) e il testo piccolo si ingrandisce fino a 2400 px sul lato lungo. Le librerie native ci sono per
**arm64-v8a e x86_64**: su un telefono a 32 bit il tasto non compare. Un'app può dare un altro riconoscitore con
`FluidLiveText.recognizer` (per esempio ML Kit, se lo sceglie: la domanda è nel rapporto di F8.17).

**API.** `FluidLiveTextExtension` (trovata da sola); `FluidLiveText` (`recognizer`, `strings`, `enabled`,
`straightenSheets`, `lastError` per i log, la memoria dei risultati; `read(context, key, findSheet = true) { foto }` legge
una foto passando dal foglio); per un visore dell'app: `FluidLiveTextState`, `FluidLiveTextOverlay(state)` dentro il riquadro dell'immagine e
`FluidLiveTextControls(state)` in un posto fermo dello schermo; il modello `FluidRecognizedText` (blocchi, righe, parole,
`FluidTextQuad` con `isUpright`, `distanceTo`, `between`; `search`, `text(from, to)`); `FluidOcrLicenses`. Il modulo
porta `engine-scan-core` (i bordi e la prospettiva dello scanner, senza fotocamera né manifest: `requires` in
`families.json`).

```kotlin
// Un visore mio (non FluidFileViewer): il disegno nel riquadro che il mio zoom ingrandisce, i comandi fermi.
val state = remember { FluidLiveTextState(aspect) }
LaunchedEffect(uri) { state.show(FluidLiveText.read(context, uri.toString()) { decodifica(uri) }) }
Box(Modifier.aspectRatio(aspect).graphicsLayer { scaleX = zoom; scaleY = zoom }) {
  Image(foto, null, Modifier.fillMaxSize())
  FluidLiveTextOverlay(state)
}
FluidLiveTextControls(state, Modifier.align(Alignment.BottomEnd))
```

**Da sapere.** Un'app che lo include aggiunge al suo `build.gradle` (le intestazioni C e l'eseguibile dentro i jar di
bytedeco non servono):

```groovy
android { packaging { resources { excludes += ['lib/*/include/**', 'lib/*/tesseract', 'META-INF/native-image/**'] } } }
```

**Limiti.** La scrittura a mano si legge male (Tesseract è fatto per lo stampato); i riquadri di Tesseract sono dritti
(una riga storta in una foto ha il rettangolo che la contiene); la ricerca nei PDF scansiti legge al più 40 pagine; niente
«Traduci» né «Cerca sul web» (fuori dal telefono). I tempi e la qualità misurati sono nel rapporto di F8.17.

## engine-viewer-all

Niente codice: `api` verso `engine-viewer` e una riga per famiglia, che si accende quando il modulo è nel build (nel `settings.gradle` dell'engine o nel blocco che scrive `engine-install.ps1`). `checkViewerFamilies` controlla che ogni famiglia ammessa che è nel build stia qui, e che il modulo di prova no. Il peso è la somma dei pesi.

## Il registro, in breve

L'ordine dei candidati, il ripiego e la sorgente sono nella scheda di [`engine-viewer`](viewer.md#il-registro-e-i-moduli-di-famiglia). In una riga: un eseguibile non arriva mai a una famiglia; la firma vale più dell'estensione, che vale più del MIME, poi la priorità; un `open` che dà `null` o un'eccezione passa al candidato dopo; il ripiego è lo sconosciuto col motivo.

## Scrivere un modulo di famiglia

1. Un modulo Android che dipende con `api` da `engine-viewer`.
2. Una classe che implementa `FluidViewerFamily` (`id`, `types`, `open`, se serve `thumbnail`), dichiarata in `src/main/resources/META-INF/services/dev.antigravity.fluidengine.viewer.FluidViewerFamily`.
3. I tipi con `FluidViewerFamilyType(id, label, extensions, mimeTypes, signature, kind, priority, preset)`; la firma con `FluidViewerSignature.bytes`, `ascii`, `zipEntry` («cartella/» vale per tutto sotto) o `anyOf`. Un tipo con un'estensione, un MIME o un `kind` da eseguibile non nasce.
4. Per i file dentro i file: `FluidViewerRegistry.open(request.context, FluidViewerSource.of(…), request.options, …)`.
5. La voce in `families.json`, con le librerie ammesse, la licenza e il peso, e la riga in `engine-viewer-all`.

L'esempio piccolo è `engine-viewer/prova/famiglia` (il tipo `.prova`).

## Nella galleria «Visori»

La galleria di Fluid Glass include `engine-viewer-all` senza l'AR e mostra **un file per tipo di ogni famiglia** (i corpi di prova dei moduli, in `sample/src/main/assets/visori/`), la scena **«Senza moduli»** (gli stessi file con il solo `engine-viewer`), la pagina **«Pesi»** (da `families.json`) e la prova con **Universal Converter** ([`../componenti/visori.md`](../componenti/visori.md#la-galleria-visori-f6c15)).

## Cosa resta da provare in mano

Raccolto dai rapporti dei pacchetti (F6C.11–F6C.16), per la sessione sul tablet (F6C.6):
- **TalkBack ascoltato davvero** su tutte le famiglie: le pagine Office e la griglia (una riga alla volta), l'elenco dell'archivio, l'albero JSON (una riga detta una volta, «aperto» e «chiuso»), le schede ICS e VCF, il cursore «Weight» del provino, i tasti del 3D, l'AR;
- Office: la fedeltà della pagina durante il pizzico, giudicata a occhio;
- archivi: il DOCX dentro lo ZIP con Office presente, e il movimento dalla miniatura nella galleria completa;
- dati: «‹ Torna al messaggio» sopra un PDF allegato, il pannello laterale da 840 dp con un CSV e un JSON, il passaggio di capitolo di un EPUB col dito vero, `:engine-viewer-data:connectedDebugAndroidTest` intero;
- 3D: le texture KTX2 (la prova ha usato KTX1), la sfocatura dei tasti quando il modello li copre;
- AR: la resa della fotocamera (l'anteprima era quasi nera), la posa su un piano vero, l'ombra, i gesti, la scala del testo 2,0, la riprova con l'APK finale.

## Dove guardare

| cosa | file |
|---|---|
| la lista ammessa, i pesi e le licenze | `engine-viewer/families.json` |
| il controllo di build | `engine-viewer/viewer-families.gradle` (`checkViewerFamilies`), `engine-viewer-ar/build.gradle` (`checkArViewerManifests`) |
| Office | `engine-viewer-office/src/main/kotlin/.../office/` (`FluidOfficeFamily.kt`, `OfficeViewerInfo.kt`), i caratteri in `src/main/assets/office` |
| archivi | `engine-viewer-archive/src/main/kotlin/.../archive/` (`FluidArchiveFamily.kt`, `NativeArchive.kt`, `ArchivePage.kt`) |
| dati | `engine-viewer-data/src/main/kotlin/.../data/` (`FluidDataFamily.kt`, `FluidDataInfo.kt`) |
| grafica e font | `engine-viewer-graphics/src/main/kotlin/.../graphics/` (`FluidGraphicsFamily.kt`) |
| 3D | `engine-viewer-3d/src/main/kotlin/.../scene/` (`FluidSceneFamily.kt`, `FluidSceneModel.kt`, `SceneTexture.kt`) |
| AR | `engine-viewer-ar/src/main/kotlin/.../ar/` (`FluidArSceneExtension.kt`) |
| i corpi di prova | `src/test/resources` e `src/androidTest/assets` di ogni modulo |
| la galleria | `sample/.../ViewerGallery.kt`, `sample/.../viewer/VisoriFamilies.kt`, `VisoriPesi.kt`, `VisoriSenzaModuli.kt` |
