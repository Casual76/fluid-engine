<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/moduli/viewer.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# engine-viewer

**I decodificatori dei visori di file**: il PDF col `PdfRenderer` della piattaforma, il testo col riconoscimento del charset, un tetto di lettura e i colori di sintassi, le GIF e le WebP animate con `ImageDecoder`, gli adattatori per il `Player` di Media3 e il risolutore da MIME o estensione al preset. Pacchetto `dev.antigravity.fluidengine.viewer`, dalla 4.0 (F6C.4).

È un **modulo opt-in**, come `engine-richtext` e `engine-ai-ui`. Il telaio dei visori ([`FluidFileViewer`](../componenti/contenuto-testo-e-media.md)) e i suoi preset stanno in `engine-ui` e **non decodificano niente**: ricevono un `FluidViewerContent` già pronto (un composable, una sessione, un sorgente di pagine o di righe). Questo modulo è il modo di serie di prepararlo da un file o da un `Uri`. Un'app che ha già i suoi decodificatori (Coil, un visore a tasselli, il suo player) passa i suoi contenuti e non lo include.

Modello mentale: **il telaio mostra, il modulo apre, l'app possiede.** Il file, il player e i permessi restano dell'app; il modulo li legge e restituisce oggetti da chiudere.

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| sapere con che preset si mostra un file, senza leggerlo | `FluidViewerType.of(mimeType, nome)` → `preset`, `kind`, `language` |
| aprire una lista di file nel visore, con i ripieghi | `rememberFluidViewerFiles(files, openWith, share, media)` e `FluidFileViewer(count = files.count, content = files::content)` |
| aprire un file solo, fuori da Compose | `file.open(context)` → `FluidViewerDocument` (da chiudere) |
| un PDF | `FluidPdfPageSource.open(context, uri)` → `FluidViewerContent.Pages(source)`; `close()` quando la pagina esce |
| un file di testo o di codice | `fluidReadTextFile(context, uri, nome)` → `FluidTextDocument` (o `null` se è binario) → `FluidViewerContent.Text(doc)` |
| i colori di sintassi di righe che ho già | `FluidSyntax.highlight(righe, lingua)` o, a pezzi, `FluidSyntax.highlighter(lingua)` con gli stati |
| una GIF o una WebP animata | `FluidDecodedImage.decode(context, uri)` → `image.content(nome)` |
| un video o un audio col mio `Player` di Media3 | `rememberFluidMedia3Session(player)` → `fluidMedia3Content(session, video = true)` |
| capire perché un file è finito allo sconosciuto | `FluidViewerDocument.failure` (`FluidViewerException` con un `FluidViewerFailure`) |
| tasselli da 200 MP, motion photo, un lettore con coda | **non questo modulo**: il composable dell'app come `FluidViewerContent.Image` o `Media` |

## Il risolutore

`FluidViewerType.of(mimeType, fileName, header = null)` decide il preset (`FluidViewerPreset`: `Image`, `Video`, `Audio`, `Pages`, `Text`, `Unknown`) senza aprire il file. Le regole, nell'ordine:

1. **L'eseguibile vince sempre.** `kind` è `FluidFileKind.from(mimeType, fileName)` senza ritocchi, quindi un eseguibile resta tale **da entrambe le fonti**: un `.apk` con MIME `image/png`, un `foto.png` con MIME `application/vnd.android.package-archive`, uno script `.sh` o `.ps1` (che pure è testo) vanno allo sconosciuto con l'avviso. Il visore non li legge e non li declassa; chi vuole leggerli lo chiede all'app.
2. **L'estensione**, l'ultima (`archivio.tar.gz` è `gz`), come in `FluidFileKind`. Un MIME in conflitto perde.
3. **Il MIME**, pulito: minuscolo e senza parametri (`Text/Plain; charset=UTF-8` vale `text/plain`). Un MIME **generico** (`application/octet-stream`, il jolly di tutti i tipi, `multipart/…`) o **malformato** (`png`, `image/`) vale come nessun MIME. Fra **video e audio decide il MIME**: un `.mp4` può essere solo audio.
4. **La firma dei primi byte** (`header`, se c'è e se nome e MIME tacciono): PDF, PNG, JPEG, GIF, WebP, BMP (con la misura dell'intestazione: «BM» da solo comincia troppi testi), HEIF e AVIF, MP4 e MOV, Matroska e WebM, WAV, AVI, MP3, FLAC, Ogg. Il testo non si riconosce dalla firma: lo decide la lettura vera.

Le immagini **senza un decodificatore della piattaforma** (SVG, TIFF, PSD, RAW) vanno allo sconosciuto, anche se `kind` è `Image`: niente librerie pesanti, e «Apri con…» c'è sempre. Così i documenti Office, gli archivi, gli ebook, i font e il 3D. `FluidViewerPreset` **non è un `enum`** (regola 3.11): un preset nuovo sarà una costante in più, e chi confronta scrive un `else`.

## Aprire i file: `rememberFluidViewerFiles` e `open`

```kotlin
val files = rememberFluidViewerFiles(
  list,                                  // List<FluidViewerFile>(uri, nome, mime, dimensione)
  openWith = { file -> apriCon(file) },  // «Apri con…» dello sconosciuto
  share = { file -> condividi(file) },
  media = { _, type -> fluidMedia3Content(session, video = type.preset == FluidViewerPreset.Video) },
)
FluidFileViewer(count = files.count, content = files::content, info = { i -> Info(files.document(i)) })
```

- **Una finestra attorno alla pagina corrente**: `content(i)` apre il file la prima volta e chiude quelli oltre `window` pagine (di serie 1: la pagina e le due vicine). Un PDF lontano non tiene il suo renderer.
- **Mentre si apre, la pagina ha già il suo preset**: il testo e le pagine aspettano i dati dentro il loro sorgente (lo stesso oggetto resta quando arrivano: il preset non riparte), l'immagine è vuota sul nero finché non è decodificata.
- **I ripieghi**, in `open`: un eseguibile va subito allo sconosciuto (mai letto); un file senza tipo dal nome o dal MIME si riconosce dai primi 64 byte; un file che il suo decodificatore rifiuta prova il tipo della sua firma e poi il testo (un `.png` che in realtà è un registro si legge); se niente lo apre, lo sconosciuto, con il motivo in `FluidViewerDocument.failure`. **Mai un vicolo cieco.**
- **Video e audio** hanno bisogno del player dell'app: senza `media` vanno allo sconosciuto con «Apri con…».
- I tetti stanno in `FluidViewerOptions` (testo 4 MiB, immagini 12 MP, copia di un PDF da un flusso 512 MB, animazioni sì o no).

## PDF: `FluidPdfPageSource`

Il `PdfRenderer` della piattaforma; `androidx.pdf` è in alfa e non si usa.

- **Una pagina alla volta, su un thread solo.** `PdfRenderer` non è thread-safe e tiene aperta una pagina per volta (aprirne una seconda è un `IllegalStateException`). Ogni accesso passa da un esecutore a un thread (`FluidPdf`), in ordine; le chiamate del preset arrivano dal thread principale e aspettano il loro turno senza bloccarlo.
- **Le cache**: 4 pagine intere al più, e al più 64 MB; 32 **anteprime** a lato lungo 256 px (`preview(i)`, e ogni `render` con un lato lungo entro 256 px), per lo scorrimento e il foglio delle miniature. Una pagina uscita dalla cache non si ricicla (il preset potrebbe ancora disegnarla).
- `size(i)` è **in punti PDF** (1/72 di pollice), per il rapporto d'aspetto e per scegliere i pixel; `render(i, target)` dà una bitmap su **fondo bianco** (la carta: una pagina è trasparente dove non ha inchiostro), ridotta se supera 12 MP.
- **Il descrittore deve essere ricercabile.** Da un `Uri` che dà una pipe o un flusso, `open(context, uri)` copia il file nella cache dell'app (al più 512 MB, interrompibile) e cancella la copia alla chiusura.
- **Annullare** una chiamata in coda la toglie prima che apra la pagina; una resa già cominciata finisce (una pagina non si interrompe a metà), si chiude e va in cache. **`close()`** chiude il documento sul suo thread dopo la resa in corso, poi il descrittore e la copia; le chiamate dopo la chiusura sono annullate.
- **Gli errori** sono `FluidViewerException`: `Corrupt` (non è un PDF, o è rotto), `Protected` (password: il renderer della piattaforma non le chiede), `Unreadable`, `TooLarge`.

## Testo: `FluidTextDecoder` e `FluidTextDocument`

- **Il charset**, come un browser: il **BOM** (UTF-8, UTF-16 e UTF-32 nei due versi) vince su tutto; poi il `charset=` **dichiarato** nel MIME, se Java lo conosce; poi la **stima** sui primi 64 KB: UTF-16 o UTF-32 senza BOM dagli zeri alternati, UTF-8 se i byte lo sono senza errori (anche il solo ASCII), altrimenti **windows-1252**, il charset dei file di Windows scritti in italiano. `FluidTextCharset.certain` dice se il charset l'ha detto qualcuno o è stimato. (Universal Converter leggeva solo UTF-8 e mandava ogni UTF-16 nel dump esadecimale.)
- **Il binario**: un NUL dove il charset non lo prevede, o più di un carattere di controllo su dieci (tab, a capo, salto pagina ed escape ANSI sono testo), e `read` dà `null`: il file va allo sconosciuto.
- **Il tetto**, 4 MiB di serie (`FluidTextDocument.DefaultLimitBytes`), è **esatto**: un file di esattamente il tetto non è troncato; uno più lungo sì (`truncated`), e il taglio cade su un carattere intero, mai a metà di una sequenza UTF-8 o di una coppia surrogata.
- **L'input malformato** non ferma la lettura: un byte che il charset non sa leggere diventa U+FFFD e `malformed` lo dice (un U+FFFD scritto nel file non conta).
- **Le righe**: `\n`, `\r\n` o `\r`; un a capo in fondo non apre una riga vuota; un file vuoto ha zero righe. Una riga oltre 10 000 caratteri si spezza (`splitLines` conta i pezzi), così un JSON minimizzato su una riga sola non ferma il visore; dopo un pezzo i numeri di riga sono quelli dei pezzi.
- `FluidTextDocument` è un `FluidTextSource`: `readLines(start, count)` dà un pezzo, e un pezzo corto segna la fine.

### I colori di sintassi: `FluidSyntax`

Lessici scritti qui, una riga alla volta, senza librerie: Kotlin, Java, Groovy (anche `.gradle`), JavaScript, TypeScript, C, C++, C#, Swift, Go, Rust, Dart, Python, JSON, XML, HTML, Markdown, YAML e INI (anche `.properties`, TOML, `.env`). Riconoscono parole riservate, stringhe, numeri, commenti, tag, attributi, annotazioni e i segni del Markdown, e danno a ogni pezzo un `FluidSyntaxRole`. **Non sono parser**: ciò che non riconoscono resta del colore del testo, mai un errore (un test li prova su righe a caso in ogni lingua).

- **La lingua** viene dal nome (`FluidSyntax.languageFor`), poi dal MIME (anche `+json`, `+xml`), poi dal contenuto (`guessLanguage`: un oggetto JSON, un documento XML o HTML, uno shebang Python). Una lingua sconosciuta dà `null`: il **ripiego** è il testo semplice.
- **Gli stati fra le righe**: un commento a blocco, una stringa su più righe (Kotlin, Swift, Python, i template di JavaScript), un blocco di codice nel Markdown continuano alla riga dopo. `FluidSyntaxHighlighter.line(testo, stato)` dà i pezzi e lo stato con cui comincia la riga successiva; `FluidTextDocument.syntaxSpans(start, count)` tiene gli stati già calcolati, così un pezzo a metà file costa solo le righe nuove.
- **I colori li sceglie il preset, non il modulo.** `FluidTextDocument.readStyledLines(start, count)` dà le righe con un'annotazione per pezzo: tag `fluid.syntax` (`FluidSyntaxAnnotation`), valore il ruolo in minuscolo (`keyword`, `type`, `string`, `number`, `comment`, `tag`, `attribute`, `heading`, `emphasis`, `link`, `code`). È la forma di `FluidStyledTextSource` del preset testo di engine-ui (F6C.2), che colora i ruoli col tema mentre disegna: il passaggio chiaro/scuro non lascia colori vecchi nelle righe già lette. Per righe proprie: `fluidSyntaxAnnotated(riga, pezzi)`.

## GIF e WebP animate: `FluidDecodedImage`

Scelta di Alessio del 21 novembre 2026: le decodifica questo modulo, **da un file o da un `Uri`**, e `engine-ui` resta senza decoder.

- `FluidDecodedImage.decode(context, uri)` (o `decode(file)`) usa `ImageDecoder`: una GIF o una WebP animata (e una HEIF animata dove la piattaforma la anima) diventa un `AnimatedImageDrawable`, una ferma una bitmap. Tetto di 12 MP: le foto da 50 o 200 MP si riducono (per quelle i tasselli sono dell'app). Un file troncato mostra ciò che c'è.
- `image.content(nome)` è un `FluidViewerContent.Image` qualunque, col rapporto d'aspetto: il preset immagine non ha un tipo nuovo da conoscere, e il telaio dà zoom, sfoglio e chiusura come a una foto.
- L'animazione **parte quando l'immagine entra in composizione e si ferma quando esce** (lo sfoglio toglie la pagina), e col **movimento ridotto** (`LocalFluidMotionPolicy`) resta ferma sul primo fotogramma.
- **Sotto Android 9** (API 28) non c'è `AnimatedImageDrawable`: `BitmapFactory` dà il primo fotogramma.
- `FluidImageHeader.read(bytes)` legge dai primi byte di una GIF o di una WebP misura, animazione, fotogrammi e giri senza decodificare i pixel: per un rapporto d'aspetto prima della decodifica, o per decidere di non animare una GIF enorme.

## Media3: `FluidMedia3Session`

**L'app passa il suo `Player`** e resta sua: lo crea, lo prepara, sceglie il file e lo rilascia. La sessione lo legge e gli passa play, pausa e salto.

- **Media3 è `compileOnly`.** Il modulo compila contro la **1.5.1** (la più vecchia dei consumer di riferimento: Fluidify e Gallery v1) e i suoi test JVM girano sulla **1.11.1** (Gallery-2 e Pampa notes): il codice compilato contro la prima gira sulla seconda. A runtime c'è **solo la versione dell'app**, mai una seconda; il compito `checkEngineUiHasNoDecoders` fallisce se Media3 entra nel runtime del modulo. Usa solo l'interfaccia `Player` di `media3-common`, uguale nelle due. Per la corsa al pavimento: `-Pviewer.media3Test=1.5.1`. Un'app **senza** Media3 può includere il modulo: le regole per R8 del modulo (`-dontwarn androidx.media3.**`) evitano l'errore sulle classi mancanti, e gli adattatori non si chiamano.
- `playing` segue **l'intenzione**, come il tasto di Media3: vero se il player deve suonare, anche mentre carica (`buffering`). `positionMillis` si aggiorna **a ogni fotogramma** mentre suona (lo scrubber scorre senza scatti), a riposo non costa niente. `durationMillis` è `null` finché la durata non è nota, e allora `canSeek` è falso; `seekTo` si limita alla durata. `play()` prepara un player fermo e riparte da capo se il file era finito (`ended`). I comandi che il player non offre non si chiamano.
- `FluidMedia3Video(session)` è la superficie, centrata col rapporto d'aspetto del video (anche con i pixel non quadrati). Di serie un `TextureView`, che il vetro dei comandi sopra il video **cattura**; `surfaceView = true` usa un `SurfaceView` (HDR, meno batteria, come Gallery-2) che sta in una finestra sua: il vetro sopra non lo vede.
- Tutto si chiama sul thread del player (di solito il principale, quello di Compose).

## Cosa è dell'app, cosa del modulo

| dell'app | del modulo |
|---|---|
| il file, il permesso di leggerlo, l'`Uri` | aprirlo, riconoscerne il tipo, leggerlo con un tetto |
| il `Player`, la sua versione di Media3, la sorgente, l'autoplay, il rilascio | leggerlo come `FluidMediaSession`, la superficie |
| «Apri con…», «Condividi», l'esame dei primi byte | metterli nello sconosciuto |
| tasselli, motion photo, strumenti di modifica | — (si passano come contenuto `Image` o come strumento del telaio) |
| chiudere ciò che apre fuori da `rememberFluidViewerFiles` | chiudere ciò che apre `rememberFluidViewerFiles` |

## Il controllo di build (F6C-d)

`engine-ui` **non dipende da questo modulo e non arriva a nessuna libreria di decodifica**. Lo controllano due prove che girano a ogni `:engine-viewer:testDebugUnitTest`:

- il compito Gradle `checkEngineUiHasNoDecoders` (in `engine-viewer/build.gradle`) legge il grafo risolto del modulo, **nel suo contesto**, e segue tutto ciò che si raggiunge da `:engine-ui`: si ferma su un gruppo di decodifica (Media3, ExoPlayer, `androidx.pdf`, Coil, Glide, Fresco, PdfBox, i rilevatori di charset, ICU…) o su `engine-viewer` stesso, e se Media3 entra nel runtime di `engine-viewer`;
- il test `EngineUiDecoderFreeTest` legge `engine-ui/build.gradle` (anche le dipendenze `compileOnly`, che il grafo non vede) e i sorgenti di `engine-ui` (nessuna API di decodifica della piattaforma: `PdfRenderer`, `ImageDecoder`, `AnimatedImageDrawable`, `BitmapFactory`, `MediaPlayer`, `MediaCodec`…).

## Trappole

- **Chiudere i PDF.** `FluidPdfPageSource` e `FluidViewerDocument` tengono un descrittore e un thread: aperti a mano vanno chiusi (`use`, `DisposableEffect`). `rememberFluidViewerFiles` lo fa da sé.
- **Un `Uri` senza permesso persistente** smette di aprirsi dopo la ricreazione dell'attività: è un errore `Unreadable`, e il file va allo sconosciuto. Il permesso è dell'app (`takePersistableUriPermission`).
- **Il testo troncato** si dice: `truncated` vero va mostrato in fondo («letti 4 MB di 120»), altrimenti chi legge un registro crede di averlo tutto.
- **Una GIF enorme** si anima a 12 MP per fotogramma: se serve meno, `FluidViewerOptions(imageMaxPixels = …)` o `animateImages = false`.
- **Il `SurfaceView`** non sta sotto il vetro: con `surfaceView = true` i comandi di vetro mostrano il nero.

## Cosa è verificato dai test

Test JVM del modulo (`engine-viewer/src/test/`), senza Robolectric: un'app ospite li esegue senza scaricare niente.

- `FluidViewerTypeTest`: MIME nullo, generico, malformato, con parametri, in conflitto con l'estensione; video contro audio; l'eseguibile da entrambe le fonti e dalla firma; le immagini senza decodificatore; i nomi senza estensione; la firma dei byte.
- `FluidTextDecoderTest`: ogni BOM, UTF-16 e UTF-32 senza BOM, UTF-8, windows-1252, il charset dichiarato e quello sconosciuto, l'input malformato, il binario, il tetto esatto e il taglio su un carattere intero (anche UTF-16 con le coppie surrogate), un flusso senza fine, le righe, i pezzi, i colori a metà file.
- `FluidSyntaxTest`: ogni lingua sui suoi costrutti, gli stati fra le righe, il ripiego, e righe a caso in ogni lingua senza eccezioni né pezzi fuori posto.
- `FluidPageRenderQueueTest`: un thread solo e una pagina aperta per volta con chiamate concorrenti, le due cache e il tetto in byte, la riduzione oltre 12 MP, l'annullamento in coda e a metà resa, la chiusura dopo la resa in corso, le risorse rilasciate una volta sola anche dopo un'apertura fallita o annullata.
- `FluidMedia3SessionTest`: un `Player` finto di `media3-common` (ogni metodo non previsto fa fallire): preparare e suonare, l'intenzione durante il caricamento, la pausa, il salto limitato, il file finito, la sospensione, i comandi mancanti, l'errore, il rapporto d'aspetto, la versione di Media3 su cui gira.
- `FluidImageHeaderTest`: GIF e WebP costruite byte per byte, animate e ferme, troncate, con e senza giri.
- `EngineUiDecoderFreeTest`: F6C-d, sopra.

Sul dispositivo, `FluidViewerDeviceTest` (`src/androidTest`, `:engine-viewer:connectedDebugAndroidTest`): il `PdfRenderer` vero su un PDF di 300 pagine, rotto, troncato, protetto da password e da un `Uri` che dà una pipe (`PipeProvider`); `ImageDecoder` e `AnimatedImageDrawable` su GIF e WebP scritte da Pillow; il testo UTF-16 da una pipe; i ripieghi di `open` (una GIF chiamata `.txt`, un JSON chiamato `.png`, un PDF senza nome, un `.apk`, un binario, un file sparito); un `ExoPlayer` vero dietro `FluidMedia3Session` e un file video rotto. Compilano anche contro Media3 1.5.1; la superficie video si giudica nella galleria «Visori».

## Dove guardare

| cosa | file |
|---|---|
| il risolutore | `engine-viewer/src/main/kotlin/dev/antigravity/fluidengine/viewer/FluidViewerType.kt` |
| aprire i file, la finestra, i ripieghi | `FluidViewerFiles.kt` |
| il PDF: il sorgente e la coda a un thread | `FluidPdfPageSource.kt`, `FluidPageRenderQueue.kt` |
| il testo e il charset | `FluidTextDecoder.kt` |
| i colori di sintassi | `FluidSyntax.kt`, `FluidSyntaxAnnotations.kt` |
| GIF e WebP | `FluidDecodedImage.kt`, `FluidImageHeader.kt` |
| Media3 | `FluidMedia3.kt` |
| gli errori | `FluidViewerException.kt` |
| i contratti che il modulo riempie | `engine-ui/…/ui/fluid/viewer/FluidViewerContent.kt` |
| il controllo di build | `engine-viewer/build.gradle` (`checkEngineUiHasNoDecoders`), `EngineUiDecoderFreeTest.kt` |

## Dipendenze e configurazione minima

```groovy
// settings.gradle dell'app: accanto agli altri moduli dell'engine
include ':engine-viewer'
project(':engine-viewer').projectDir = file('engine/engine-viewer')

// build.gradle dell'app
implementation project(':engine-viewer')
// solo se l'app usa gli adattatori video e audio, alla sua versione:
implementation "androidx.media3:media3-exoplayer:1.11.1"
```

Porta `engine-ui` (e con lui `engine-foundation`). Nessun permesso: i file arrivano dall'app. `minSdk` 26; le animazioni da 28.
