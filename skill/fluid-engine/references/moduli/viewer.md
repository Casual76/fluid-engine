# engine-viewer

**I decodificatori dei visori di file e il registro delle famiglie**: il PDF col `PdfRenderer` della piattaforma, il testo col riconoscimento del charset, un tetto di lettura e i colori di sintassi, le GIF e le WebP animate con `ImageDecoder`, gli adattatori per il `Player` di Media3, il risolutore da MIME o estensione al preset e, dalla seconda ondata, il **registro** dei moduli di famiglia (Office, archivi, dati, grafica e font, 3D, AR: [`viewer-famiglie.md`](viewer-famiglie.md)). Pacchetto `dev.antigravity.fluidengine.viewer`, dalla 4.0 (F6C.4, registro F6C.8).

È un **modulo opt-in**, come `engine-richtext` e `engine-ai-ui`: si installa con `engine-install.ps1 -Modules …,engine-viewer`. Il telaio dei visori ([`FluidFileViewer`](../componenti/visori.md), la scheda dei sei preset) e i suoi preset stanno in `engine-ui` e **non decodificano niente**: ricevono un `FluidViewerContent` già pronto (un composable, una sessione, un sorgente di pagine o di righe). Questo modulo è il modo di serie di prepararlo da un file o da un `Uri`. Un'app che ha già i suoi decodificatori (Coil, un visore a tasselli, il suo player) passa i suoi contenuti e non lo include.

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
| aprire anche DOCX, ZIP, Markdown, SVG, font, modelli 3D | la dipendenza del modulo di famiglia, o `engine-viewer-all` ([`viewer-famiglie.md`](viewer-famiglie.md)): si registra da sola |
| sapere quali famiglie ci sono, toglierne o metterne una configurata | `FluidViewerRegistry.families`, `uninstall(id)`, `install(famiglia)` |
| aprire un file che non ha un `Uri` (la voce di un archivio, un allegato, un flusso) | `FluidViewerRegistry.open(context, FluidViewerSource.of(…))` |
| scrivere una famiglia mia | `FluidViewerFamily` in `META-INF/services` (sotto, e [`viewer-famiglie.md`](viewer-famiglie.md#scrivere-un-modulo-di-famiglia)) |
| tasselli da 200 MP, motion photo, un lettore con coda | **non questo modulo**: il composable dell'app come `FluidViewerContent.Image` o `Media` |
| riaprire un file dove l'utente era (pagina, zoom, riga, secondo) | `FluidViewerResumeProvider(rememberFluidViewerResume(files)) { FluidFileViewer(…) }` (sotto, «Riprendi dallo stesso punto») |
| portare le posizioni su un altro dispositivo | `FluidViewerResumeStore.get(context)`: `observe`, `export`, `import` |
| sfogliare cartelle e aprire i file da lì | [`files.md`](files.md) (`engine-files`) |

## Il risolutore

`FluidViewerType.of(mimeType, fileName, header = null)` decide il preset (`FluidViewerPreset`: `Image`, `Video`, `Audio`, `Pages`, `Text`, `Unknown`) senza aprire il file. Le regole, nell'ordine:

1. **L'eseguibile vince sempre.** `kind` è `FluidFileKind.from(mimeType, fileName)` senza ritocchi, quindi un eseguibile resta tale **da entrambe le fonti**: un `.apk` con MIME `image/png`, un `foto.png` con MIME `application/vnd.android.package-archive`, uno script `.sh` o `.ps1` (che pure è testo) vanno allo sconosciuto con l'avviso. Il visore non li legge e non li declassa; chi vuole leggerli lo chiede all'app.
2. **L'estensione**, l'ultima (`archivio.tar.gz` è `gz`), come in `FluidFileKind`. Un MIME in conflitto perde.
3. **Il MIME**, pulito: minuscolo e senza parametri (`Text/Plain; charset=UTF-8` vale `text/plain`). Un MIME **generico** (`application/octet-stream`, il jolly di tutti i tipi, `multipart/…`) o **malformato** (`png`, `image/`) vale come nessun MIME. Fra **video e audio decide il MIME**: un `.mp4` può essere solo audio.
4. **La firma dei primi byte** (`header`, se c'è e se nome e MIME tacciono): PDF, PNG, JPEG, GIF, WebP, BMP (con la misura dell'intestazione: «BM» da solo comincia troppi testi), HEIF e AVIF, MP4 e MOV, Matroska e WebM, WAV, AVI, MP3, FLAC, Ogg. Il testo non si riconosce dalla firma: lo decide la lettura vera.

Le immagini **senza un decodificatore della piattaforma** (SVG, TIFF, PSD, RAW) vanno allo sconosciuto, anche se `kind` è `Image`: niente librerie pesanti, e «Apri con…» c'è sempre. Così i documenti Office, gli archivi, gli ebook, i font e il 3D, **finché l'app non aggiunge il modulo della loro famiglia**: un tipo di famiglia batte questa tabella dei «noti ma senza decodificatore» (sotto). `FluidViewerPreset` **non è un `enum`** (regola 3.11): un preset nuovo sarà una costante in più, e chi confronta scrive un `else`.

## Il registro e i moduli di famiglia

`FluidViewerRegistry` (F6C.8) aggiunge i tipi dei moduli di famiglia al risolutore **senza toccarlo**: un modulo dichiara la sua `FluidViewerFamily` in `META-INF/services/dev.antigravity.fluidengine.viewer.FluidViewerFamily`, e il registro la trova con `ServiceLoader` la prima volta che risolve. Niente App Startup, niente codice nell'`Application`: chi aggiunge la dipendenza ha il tipo, chi non la aggiunge non ha niente. **Senza famiglie il visore fa esattamente quello che faceva in F6C.4**: stessi tentativi, stesse letture.

**Come si decide** (`FluidViewerType.of`, `FluidViewerFile.open`, `FluidViewerRegistry.open`):
1. **Un eseguibile resta sconosciuto**, sempre, prima di chiedere alle famiglie: non se ne legge un byte, nemmeno con un MIME da documento. Una famiglia non può nemmeno *dichiarare* un'estensione, un MIME o un `kind` da eseguibile: il tipo rifiuta di nascere.
2. **I candidati**: i tipi delle famiglie e il preset di serie, con la forza del riconoscimento. Una voce del pacchetto ZIP vale più dei primi byte (4 KiB), che valgono più dell'estensione, che vale più del MIME. Così un `.zip` che contiene `word/document.xml` è un Word, e un `.docx` rotto si scopre ZIP. Un preset di serie confermato dai primi byte vale quanto una firma.
3. **L'ordine**: per forza, poi per `priority`; a parità vince il preset di serie, poi l'ordine di `id`. Un tipo di famiglia batte la tabella «noti ma senza decodificatore». Un preset di serie che apre già il tipo resta davanti: l'HEIC di `graphics` ha priorità −10 e parte solo se `ImageDecoder` fallisce; per sostituire il preset di serie una famiglia dichiara una priorità positiva (i dati, 10: il CSV come tabella).
4. **Si prova in ordine**: un `open` che dà `null` o un'eccezione passa al candidato dopo, e la prima eccezione resta come motivo (un'eccezione qualunque vale «file rotto», un `OutOfMemoryError` «troppo grande»).
5. **Il ripiego** è lo sconosciuto col motivo e «Apri con…».

**La sorgente**, `FluidViewerSource`: un `Uri` (`of(context, uri, name, …)`), un file dell'app (`of(file, …)`) o un flusso (`of(name, cacheDir, …) { … }`). `open()` dà un flusso nuovo dall'inizio; `seekable()` un descrittore, e una sorgente che non si può spostare (una pipe) si copia **una volta** in cache (al più 512 MB, `FluidViewerOptions.sourceMaxCopyBytes`). La copia sparisce alla chiusura, anche dopo un annullamento o un errore a metà; il documento aperto possiede la sua sorgente e la chiude con sé.

**I file dentro i file.** Un archivio, una mail o un EPUB aprono le loro voci con `FluidViewerRegistry.open(request.context, FluidViewerSource.of(…), request.options, …)`: un PDF dentro uno ZIP usa il preset PDF, un DOCX allegato usa Office se c'è.

**Le API.**

```kotlin
object FluidViewerRegistry {
  val families: List<FluidViewerFamily>                     // ServiceLoader + installate − tolte
  fun install(vararg family: FluidViewerFamily)             // stessa id: sostituisce quella trovata
  fun uninstall(vararg id: String)                          // il tipo torna allo sconosciuto
  suspend fun open(context, source: FluidViewerSource, options = FluidViewerOptions(),
    openWith = null, share = null, media = null): FluidViewerDocument
  suspend fun thumbnail(context, source, sizePx, options = FluidViewerOptions()): ImageBitmap?   // solo le famiglie
}
interface FluidViewerFamily {
  val id: String; val types: List<FluidViewerFamilyType>
  suspend fun open(request: FluidViewerRequest): FluidViewerOpened?       // null o eccezione: «non questo»
  suspend fun thumbnail(request: FluidViewerRequest, sizePx: Int): ImageBitmap? = null
}
class FluidViewerOpened(content: FluidViewerContent, details: List<FluidViewerDetail> = emptyList(), onClose: () -> Unit = {})
data class FluidViewerDetail(val label: String, val value: String, val section: String? = null)
class FluidViewerLiveDetails(initial: List<FluidViewerDetail> = emptyList()) : List<FluidViewerDetail> {   // F7.20
  val current: List<FluidViewerDetail>                      // le righe di adesso, intere e immutabili
  fun replace(items: List<FluidViewerDetail>); fun add(detail: FluidViewerDetail)
  fun update(transform: (List<FluidViewerDetail>) -> List<FluidViewerDetail>)   // da qualunque filo, in fila
}
```

**Righe che arrivano dopo l'apertura** (il conto di un CSV in sottofondo, le pagine contate da Office): la famiglia dà una `FluidViewerLiveDetails` come `details` e la cambia da qualunque filo con `replace`, `add` o `update`. Ogni cambio mette una lista intera e immutabile al posto della vecchia; la lettura (`current`, e anche `for`, `filter`, `any`) prende un valore solo, sempre intero, e in composizione si iscrive ai cambi. Per una copia da tenere: `current`.

Campi nuovi, in coda e con un valore di serie (regola 3.11): `FluidViewerType(…, family, label, familyType)`, `FluidViewerOptions(…, sourceMaxCopyBytes)`, `FluidViewerDocument.details`; `FluidViewerPreset.Grid` e `Custom` sono costanti in più.

**La lista ammessa**, `engine-viewer/families.json`: per ogni famiglia il modulo, il pacchetto, le librerie che può portare con la licenza e il peso, e (F6C.15) la voce `apk` con il peso misurato per architettura. La leggono `checkViewerFamilies` (una libreria fuori lista ferma la build), `tools/engine-install.ps1` e la pagina «Pesi» della galleria. **R8** tiene le famiglie senza regole a mano (lo prova `:engine-viewer-prova-app`). I dettagli dei moduli, i pesi e le licenze: [`viewer-famiglie.md`](viewer-famiglie.md).

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
- **F8.18, la pagina per lo schermo.** Quando la chiede il preset delle pagine (`FluidPageDisplay` nel contesto della chiamata), la pagina resa passa nella memoria grafica (`Bitmap.Config.HARDWARE`) sul thread del PDF: il primo disegno non carica la texture dentro il fotogramma del RenderThread, e a 120 Hz non c'è lo scatto all'arrivo di ogni pagina. Ha la sua voce in cache; la stampa, la condivisione e chi chiama `render` senza quel contesto ricevono la bitmap normale, che si legge e si disegna in un `Canvas` software. La prenotazione della resa conta già le due copie (8 byte per pixel), e la copia software si ricicla subito. `FluidPdfPageSource.hardwareDisplay` lo spegne, solo per le prove di confronto sul dispositivo.
- **F8.18, le misure** tenute sono 256 (prima 64): un documento di migliaia di pagine si percorre avanti e indietro con la maniglia senza riaprire le pagine per misurarle.

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
- Un'animata va a `fluidAnimatedImage` del preset immagine: **parte solo sulla pagina ferma** (`currentFluidViewerPageActive()`) e si ferma quando la si lascia, come Gallery-2; col **movimento ridotto** (`LocalFluidMotionPolicy`) resta ferma e TalkBack ha l'azione «Riproduci animazione». Disegnata fuori dal visore con `FluidDecodedImage.Body`, parte quando entra in composizione.
- **Sotto Android 9** (API 28) non c'è `AnimatedImageDrawable`: `BitmapFactory` dà il primo fotogramma.
- **F8.18, i tasselli.** Una foto ferma ridotta (oltre il tetto di 12 MP) e letta da un indirizzo o da un file sa riaprire l'originale: dentro il visore, ingrandita oltre la sua risoluzione e ferma, riceve il tassello della parte in vista alla risoluzione dello schermo (`BitmapRegionDecoder`, la memoria dal registro del visore), sopra la foto ridotta. Non c'è niente da chiamare: lo fa `FluidDecodedImage.content`. Una foto girata dall'EXIF resta senza tasselli.
- `FluidImageHeader.read(bytes)` legge dai primi byte di una GIF o di una WebP misura, animazione, fotogrammi e giri senza decodificare i pixel: per un rapporto d'aspetto prima della decodifica, o per decidere di non animare una GIF enorme.

## Media3: `FluidMedia3Session`

**L'app passa il suo `Player`** e resta sua: lo crea, lo prepara, sceglie il file e lo rilascia. La sessione lo legge e gli passa play, pausa e salto.

- **Media3 è `compileOnly`.** Il modulo compila contro la **1.5.1** (la più vecchia dei consumer di riferimento: Fluidify e Gallery v1) e i suoi test JVM girano sulla **1.11.1** (Gallery-2 e Pampa notes): il codice compilato contro la prima gira sulla seconda. A runtime c'è **solo la versione dell'app**, mai una seconda; il compito `checkEngineUiHasNoDecoders` fallisce se Media3 entra nel runtime del modulo. Usa solo l'interfaccia `Player` di `media3-common`, uguale nelle due. Per la corsa al pavimento: `-Pviewer.media3Test=1.5.1`. Un'app **senza** Media3 può includere il modulo: le regole per R8 del modulo (`-dontwarn androidx.media3.**`) evitano l'errore sulle classi mancanti, e gli adattatori non si chiamano.
- `playing` segue **l'intenzione**, come il tasto di Media3: vero se il player deve suonare, anche mentre carica (`buffering`). `positionMillis` si aggiorna **a ogni fotogramma** mentre suona (lo scrubber scorre senza scatti), a riposo non costa niente. `durationMillis` è `null` finché la durata non è nota, e allora `canSeek` è falso; `seekTo` si limita alla durata. `play()` prepara un player fermo e riparte da capo se il file era finito (`ended`). I comandi che il player non offre non si chiamano.
- `FluidMedia3Video(session)` è la superficie, centrata col rapporto d'aspetto del video (anche con i pixel non quadrati). Di serie un `TextureView`, che il vetro dei comandi sopra il video **cattura**; `surfaceView = true` usa un `SurfaceView` (HDR, meno batteria, come Gallery-2) che sta in una finestra sua: il vetro sopra non lo vede.
- Tutto si chiama sul thread del player (di solito il principale, quello di Compose).

## Nella galleria «Visori» (F6C.5, rifatta in F6C.15)

La galleria di Fluid Glass usa il modulo come un'app vera, e si può leggere come esempio (`sample/.../ViewerGallery.kt`, `sample/.../viewer/`):

- i file stanno nella cartella dell'app (copiati una volta dalle risorse e dagli asset) e si aprono da un `file://` con `rememberFluidViewerFiles`; la galleria include `engine-viewer-all` (senza l'AR), quindi passano dal registro;
- **un `ExoPlayer` per file da suonare**, creato e rilasciato dalla galleria; `media` dà `fluidMedia3Content` solo al file di quel player. La superficie del video segue `currentFluidViewerPageActive()`: il video si ferma quando lo si sfoglia via, e al ritorno riparte solo se suonava e se l'interruttore «Riprendi al ritorno» è acceso (la scelta è dell'app, [`../componenti/visori.md`](../componenti/visori.md));
- «Apri con…» e «Condividi» passano al sistema l'`Uri` `android.resource://` della risorsa da cui viene il file, o un `content://` del suo provider in sola lettura per i file degli asset (un `file://` non si può dare a un'altra app senza un `FileProvider`);
- il pannello info legge `FluidViewerDocument`: il tipo deciso, il charset e le righe del testo, le pagine del PDF, il motivo di un ripiego; per un file di famiglia la funzione della famiglia;
- **«Senza moduli»** toglie le famiglie dal registro (`uninstall`) finché la scena è aperta: gli stessi file come li vede un'app con il solo `engine-viewer`.

**La prova con Universal Converter** apre dal telaio i tipi delle prove di quell'app, che il suo `PreviewLoader` legge come immagine, come testo o come dump esadecimale. Così si legge che cosa cambierebbe migrandola a `engine-viewer-all` (la migrazione è di Alessio):

| file | oggi in Universal Converter | con `engine-viewer-all` |
|---|---|---|
| `colors_50x50.png`, `foto.jpg` | immagine | immagine (`ImageDecoder`) |
| `animazione.gif` | il primo fotogramma, fermo | immagine animata, sulla pagina ferma |
| `tracce.svg` | il sorgente XML come testo | immagine speciale (`engine-viewer-graphics`, AndroidSVG), netta allo zoom |
| `markdown.md` | testo, senza colori né «Trova» | Markdown reso, con «Reso \| Sorgente» (`engine-viewer-data`) |
| `pagina.html`, `catalogo.xml`, `config.json` | il sorgente come testo | HTML chiuso e senza rete, XML e JSON ad albero (`engine-viewer-data`) |
| un testo UTF-16 col BOM | dump (vede i byte zero) | testo (il BOM decide il charset) |
| `relazione.docx` | dump esadecimale | documento a pagine (`engine-viewer-office`) |
| un `.pdf` | dump esadecimale | documento a pagine (`FluidPdfPageSource`) |
| un `.mp3`, un `.wav`, un `.mp4` | dump esadecimale | audio e video col `Player` dell'app |
| `progetto.zip` | dump esadecimale | l'elenco con le cartelle, e ogni voce col suo preset (`engine-viewer-archive`) |
| `piramide.obj` | il sorgente come testo | modello 3D con Filament (`engine-viewer-3d`) |
| un binario `.wld` | dump esadecimale | sconosciuto con «Apri con…»; il dump c'è ancora, a richiesta (`firstBytes` di `FluidUnknownPreset`) |
| un file vuoto | «Il file è vuoto.» | il preset testo dice «Il file è vuoto» |
| un file sparito | «Non riesco a leggere il file.» | sconosciuto: `failure` è `Unreadable`, la scheda lo dice |

## Riprendi dallo stesso punto: `FluidViewerResumeStore` (F8.18)

Il negozio di serie delle posizioni dei visori ([`../componenti/visori.md`](../componenti/visori.md), «Riprendi dallo stesso punto»): uno per processo, `FluidViewerResumeStore.get(context)`.

- **Leggero.** Le posizioni stanno in memoria; il disco (un file di testo di poche decine di KB in `noBackupFilesDir`, fuori dal backup) si scrive una volta sola 1,5 s dopo l'ultima modifica e subito quando l'app va in background. Al più 300 posizioni; esce la meno usata. Una posizione all'inizio del file non si tiene.
- **Cancellato quando il file non c'è più.** Una volta al giorno, alla prima apertura, `prune` chiede a ogni indirizzo se esiste (`resumeTargetExists`: `file://` sul disco; `content://` al provider, e un provider sparito, un permesso perso o un documento cancellato valgono «non c'è»; uno schema che non si può chiedere resta). Un file che il visore non riesce più a leggere (`Unreadable`) perde la posizione subito; un'app che cancella un file chiama `forget` o `forgetUri`.
- **La revisione.** La chiave di serie è l'indirizzo (`keyOf`); la revisione è peso e data (`revisionOf`), se si sanno. Una posizione di un'altra revisione non si applica: il preset la offre. Un'app con un id stabile dei suoi file (un allegato, un documento del cloud) passa `key` e `revision` a `rememberFluidViewerResume`. Lo stesso nome con un indirizzo diverso è un altro file.
- **Un altro dispositivo.** `observe` avvisa a ogni salvataggio, `export` dà la riga di una posizione e `import` la prende (vince la più recente). Il trasporto è dell'app (l'Account Pampa, `engine-sync`).

```kotlin
val store = FluidViewerResumeStore.get(context)
store.observe { entry -> sync.put("posizioni/${entry.key}", entry.encode()) }   // il trasporto è dell'app
sync.onRemote { riga -> store.import(riga) }
```

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
- **Una lista di Compose cambiata da un altro filo** (F7.20). Una `SnapshotStateList` scritta in sottofondo non si legge sicura da nessuna parte, neanche copiata in un'istantanea (`Snapshot.takeSnapshot`): Compose 1.10 rilegge l'id di un record mentre cerca il valore, e un record riciclato in quel momento fa leggere quello che l'altro filo sta riempiendo. Ne escono `ConcurrentModificationException`, liste vuote a metà e indici fuori misura. Le righe del pannello che cambiano dopo l'apertura stanno in una `FluidViewerLiveDetails`.

## Cosa è verificato dai test

Test JVM del modulo (`engine-viewer/src/test/`), senza Robolectric: un'app ospite li esegue senza scaricare niente.

- `FluidViewerTypeTest`: MIME nullo, generico, malformato, con parametri, in conflitto con l'estensione; video contro audio; l'eseguibile da entrambe le fonti e dalla firma; le immagini senza decodificatore; i nomi senza estensione; la firma dei byte.
- `FluidTextDecoderTest`: ogni BOM, UTF-16 e UTF-32 senza BOM, UTF-8, windows-1252, il charset dichiarato e quello sconosciuto, l'input malformato, il binario, il tetto esatto e il taglio su un carattere intero (anche UTF-16 con le coppie surrogate), un flusso senza fine, le righe, i pezzi, i colori a metà file.
- `FluidSyntaxTest`: ogni lingua sui suoi costrutti, gli stati fra le righe, il ripiego, e righe a caso in ogni lingua senza eccezioni né pezzi fuori posto.
- `FluidPageRenderQueueTest`: un thread solo e una pagina aperta per volta con chiamate concorrenti, le due cache e il tetto in byte, la riduzione oltre 12 MP, l'annullamento in coda e a metà resa, la chiusura dopo la resa in corso, le risorse rilasciate una volta sola anche dopo un'apertura fallita o annullata.
- `FluidMedia3SessionTest`: un `Player` finto di `media3-common` (ogni metodo non previsto fa fallire): preparare e suonare, l'intenzione durante il caricamento, la pausa, il salto limitato, il file finito, la sospensione, i comandi mancanti, l'errore, il rapporto d'aspetto, la versione di Media3 su cui gira.
- `FluidImageHeaderTest`: GIF e WebP costruite byte per byte, animate e ferme, troncate, con e senza giri.
- `EngineUiDecoderFreeTest`: F6C-d, sopra.
- `FluidViewerResumeStoreTest` (F8.18, Robolectric): la riga della posizione, la stessa revisione che si applica e l'altra che si offre, l'inizio che non si tiene, la scrittura più tardi e la lettura da un processo nuovo, il tetto, la riga di un altro dispositivo (vince la più recente), l'avviso a ogni salvataggio, la pulizia dei file spariti e la domanda all'indirizzo per ogni schema.
- `FluidPageDisplayTest` (F8.18): la pagina per lo schermo convertita sul thread del documento, con la sua voce in cache, e mai data alla stampa; le 256 misure tenute.
- `FluidImageDetailTest` (F8.18): quando serve il tassello di una foto enorme (solo oltre la risoluzione della foto ridotta), quale parte dell'originale (quella in vista, con lo zoom e lo spostamento del telaio), a che campionamento e dove si disegna.

Il registro (F6C.8): `FluidViewerRegistryTest` (il modulo di prova trovato con `META-INF/services`, firma > estensione > MIME > priorità, lo ZIP che è un Word, l'HEIC a −10, il ripiego al candidato dopo, l'eseguibile che non arriva mai a una famiglia, i campi in coda), `FluidViewerSourceTest` (la copia una volta, annullata, oltre il tetto, l'indice ZIP), `FluidViewerFormsTest`, `FluidViewerFamiliesBuildTest` (i sette grafi finti di `checkViewerFamilies`); `checkViewerFamilyApks` sugli APK R8 dell'app di prova.

Le righe vive del pannello (F7.20): `FluidViewerLiveDetailsTest` (ogni cambio è una lista intera, la copia data prima non cambia, 2000 aggiunte da quattro fili senza perderne una, l'osservatore di Compose avvisato da un filo in sottofondo); la corsa vera sta in `DataDetailsReplaceTest` di `engine-viewer-data` (due fili che sostituiscono, sei che leggono per tre secondi come il pannello, come la composizione e come un'app).

Nella galleria, `VisoriGalleryTest` (sample, Robolectric) apre col modulo i file veri del testo, del codice, dello sconosciuto e dell'eseguibile, **ogni file di ogni famiglia** col suo modulo, la scena «Senza moduli», la pagina «Pesi» e i tipi di Universal Converter; il PDF e le immagini li passa già pronti, perché sulla JVM `PdfRenderer` e `ImageDecoder` non decodificano, e al posto di libarchive (nativa) mette una famiglia finta con i tipi veri.

Sul dispositivo, `FluidViewerDeviceTest` (`src/androidTest`, `:engine-viewer:connectedDebugAndroidTest`): il `PdfRenderer` vero su un PDF di 300 pagine, rotto, troncato, protetto da password e da un `Uri` che dà una pipe (`PipeProvider`); `ImageDecoder` e `AnimatedImageDrawable` su GIF e WebP scritte da Pillow; il testo UTF-16 da una pipe; i ripieghi di `open` (una GIF chiamata `.txt`, un JSON chiamato `.png`, un PDF senza nome, un `.apk`, un binario, un file sparito); un `ExoPlayer` vero dietro `FluidMedia3Session` e un file video rotto. Compilano anche contro Media3 1.5.1; la superficie video si giudica nella galleria «Visori».

## Dove guardare

| cosa | file |
|---|---|
| il risolutore | `engine-viewer/src/main/kotlin/dev/antigravity/fluidengine/viewer/FluidViewerType.kt` |
| il registro, le famiglie, la sorgente | `FluidViewerRegistry.kt`, `FluidViewerFamily.kt`, `FluidViewerSource.kt`, `FluidZipDirectory.kt` |
| la lista ammessa e il suo controllo | `engine-viewer/families.json`, `engine-viewer/viewer-families.gradle` |
| aprire i file, la finestra, i ripieghi | `FluidViewerFiles.kt` |
| il PDF: il sorgente e la coda a un thread | `FluidPdfPageSource.kt`, `FluidPageRenderQueue.kt` |
| il testo e il charset | `FluidTextDecoder.kt` |
| i colori di sintassi | `FluidSyntax.kt`, `FluidSyntaxAnnotations.kt` |
| GIF e WebP | `FluidDecodedImage.kt`, `FluidImageHeader.kt` |
| Media3 | `FluidMedia3.kt` |
| gli errori | `FluidViewerException.kt` |
| i contratti che il modulo riempie | `engine-ui/…/ui/fluid/viewer/FluidViewerContent.kt` ([`../componenti/visori.md`](../componenti/visori.md)) |
| il modulo usato da un'app | `sample/.../ViewerGallery.kt`, `sample/.../viewer/VisoriViewer.kt`, `VisoriFiles.kt` |
| il controllo di build | `engine-viewer/build.gradle` (`checkEngineUiHasNoDecoders`), `EngineUiDecoderFreeTest.kt` |

## Dipendenze e configurazione minima

Con lo script: `engine-install.ps1 -AppRoot . -Modules engine-ui,engine-viewer` (le dipendenze si chiudono da sole; lo script ricorda anche la riga di Media3, che il modulo non porta). A mano:

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

## Memoria di miniature e pagine

Il processo condivide le quote `THUMBNAILS` e `VIEWER` con gli altri moduli Fluid. Il lato richiesto è un massimo. Quando una resa non entra, il visore prova a liberare le voci ricostruibili e riduce il lato. La pagina già mostrata conserva il suo credito durante la nuova resa. Il conteggio delle pagine e la posizione nel documento restano disponibili anche quando escono le immagini.

Per una tessera visibile usare `rememberFluidFileThumbnail`. Per scaldare la cache fuori schermo usare `FluidFileThumbnails.prefetch(context, file, sizePx, look)`. Per una pagina vicina usare `FluidPdfPageSource.prefetch(index)`. Le due preparazioni sono sospendibili e seguono il permesso di prefetch del processo. Il PDF ha un solo render simultaneo per processo. Le miniature hanno da uno a tre posti. La vecchia chiamata `load` continua a consegnare la miniatura. Se il processo ha esaurito anche il margine per l'icona, può segnalare `FluidViewerException(TooLarge)`. Il Composable lascia libera la tessera.

Il lettore Compose rilascia la propria quota all'uscita. Per le API che espongono direttamente una bitmap o un drawable senza un metodo di chiusura il credito segue la raggiungibilità dell'oggetto Android. Una collezione che conserva ogni immagine conserva anche quella memoria. Nei processi secondari che aprono esclusivamente file o descrittori e non inizializzano AndroidX Startup, chiamare prima `FluidGlassResources.get(context)`.
