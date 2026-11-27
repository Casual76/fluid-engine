<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/componenti/condivisione.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# Condivisione: il foglio, la copia preparata, immagini, PDF e stampa

La condivisione della 4.0 (F8.10). Quattro pezzi, con una nota di Alessio che vale per tutti: **condividere i dati in
formati migliori del testo** (un voto o un impegno come immagine, senza fare lo screenshot).

- **Il foglio di condivisione** (`FluidShareSheet`): il foglio di vetro di iOS 26 (`ShareLink`,
  `UIActivityViewController`) con la consegna di Android. Pacchetto `dev.antigravity.fluidengine.ui.fluid.share`.
- **Prepara la copia** (`FluidShareCopyOptions`, `FluidPrepareCopySheet`): le «Opzioni ›» di Foto, come una pagina
  dentro il foglio o come foglio a se' prima. L'app fa e verifica la copia; l'engine mostra l'attesa e il riepilogo, e
  blocca l'invio se la copia non corrisponde. Stesso pacchetto.
- **Le uscite** (`FluidShareImageRenderer`, `FluidPdf`, `FluidPrint`): una vista Compose come immagine nei formati delle
  chat e delle storie, il PDF impaginato con testo vero, la stampa con l'anteprima di Android. Pacchetto
  `dev.antigravity.fluidengine.ui.fluid.output`.
- **Gli arrivi da altre app**: il verso opposto, nel modulo opt-in `engine-share-in` ([../moduli/share-in.md](../moduli/share-in.md)).

Il QR del foglio («Mostra come QR») lo calcola il modulo opt-in `engine-qr` ([../moduli/qr.md](../moduli/qr.md)); il
contratto e il disegno stanno qui. Catalogo delle firme: [../api/condivisione.md](../api/condivisione.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| il foglio di condivisione che nasce dal tasto Condividi della barra | `FluidShareSheet(item, onDismissRequest = { item = null }, source = rememberFluidToolbarTransitionSource("condividi"))` |
| condividere un file che l'app ha appena prodotto | `FluidShareItem.file(context, file, "image/png")`: l'Uri lo da' il FileProvider dell'engine (`FluidShareFiles`) |
| condividere un file qualsiasi dell'app (una foto in `filesDir`) | lo stesso `FluidShareItem.file(context, file, …)`: il foglio lo copia nella cartella dell'engine quando si apre, col tetto `FluidShareOptions(copyLimitBytes = …)` |
| mandare un file dell'app senza il foglio | `FluidShareFiles.stage(context, item)` (sospesa), poi `FluidShareIntents` con l'oggetto pronto |
| un file gia' esposto dall'app, un link, un testo, piu' foto | `FluidShareItem.file(uri, nome, tipo)`, `.link(url, titolo)`, `.text(testo)`, `.files(uris, "3 foto", "image/*")` |
| un'azione dell'app nel foglio («Aggiungi a Nota rapida») | `actions = listOf(FluidShareAction(id, titolo, icona) { item -> … })` |
| le app Pampa nella fila, anche da una build di prova | `trust = FluidShareTrust.certificates(setOf(impronta))` (di serie `SameSigner`) |
| «Mostra come QR» | il modulo `engine-qr` e `ProvideFluidQr { … }` attorno alla pagina |
| le azioni dell'app dentro il foglio di Android (Android 14+) | `chooserActions = listOf(FluidShareChooserAction(etichetta, icona, pendingIntent))` |
| solo l'intent, senza foglio | `FluidShareIntents.send(item)`, `.chooser(context, item)` |
| «Copia» di un codice o di una password, senza anteprima negli appunti | `item.copy(sensitive = true)`: il clip esce segnato delicato (Android 13+) |
| «Opzioni ›» nella testata del foglio, come Foto in iOS 26: una pagina dentro il foglio, e «Posizione inclusa» sotto il nome | `FluidShareSheet(…, copyOptions = FluidShareCopyOptions(prepare = { scelta -> copia }, options = …))` e `item.copy(detail = "Posizione inclusa")` |
| far scegliere cosa mandare prima di aprire il foglio | `FluidPrepareCopySheet(visible, …, prepare = { scelta -> copia }, options = …)`, poi il foglio con `onReady` |
| pagine dentro un altro foglio (un indietro in alto, il foglio fermo) | `FluidSheetPages` e `FluidSheetPageBar`: vedi [menu-modali.md](menu-modali.md) |
| le azioni dell'engine in lista come in iOS 17 e 18, invece della fila di tasti tondi | `options = FluidShareOptions(actionsAsList = true)` |
| togliere la posizione da un JPEG e verificarlo sul file | `FluidCopyTools.copyWithoutLocation(…)`, `FluidCopyTools.hasLocation(file)` |
| un voto, un impegno, un risultato **come immagine** | `rememberFluidShareImageRenderer().renderToFile(FluidShareImageFormat.Story, "Voto.png") { FluidShareImageFrame(signature = "ClasseViva") { … } }` |
| un pannello «di vetro» in un'immagine o su carta | `FluidPrintPanel { … }` (materiale piatto «stampa»), mai `fluidGlassPanel` |
| una pagina, una lista, una nota in **PDF** | `rememberFluidPdfWriter().writeToFile(fluidPdfDocument("Titolo") { … }, "Nota.pdf")` |
| il PDF lontano da una pagina (un lavoro in sottofondo) | `FluidPdf.writeToFile(context, documento, nome, stile)`, senza viste Compose |
| quante pagine, o le pagine disegnate per un'anteprima | `FluidPdf.layout(documento).pageCount`, `.draw(pagina, canvas)` |
| la **stampa** con l'anteprima di Android | `writer.print(documento)`; un'immagine `FluidPrint.printImage(context, uri)`; un PDF fatto `FluidPrint.printPdf(context, uri)` |
| ricevere da un'altra app | `engine-share-in` ([../moduli/share-in.md](../moduli/share-in.md)) |
| **non così**: `startActivity(Intent.createChooser(…))` da solo | niente anteprima, niente app Pampa, e i file di `SEND_MULTIPLE` partono senza permesso se manca il `ClipData` |
| **non così**: lo screenshot di una vista | prende la barra, il vetro e cio' che c'e' dietro; l'immagine dell'engine e' la vista sola, a 1080 px |

## Il foglio: `FluidShareSheet`

**Cos'e'.** Il foglio di condivisione di iOS 26 in un `FluidGlassSheet` a meta' e in cima ([menu-modali.md](menu-modali.md),
«Il foglio con i detent»). Con `source` nasce dal tasto Condividi e ci rientra: lo zoom dei fogli di F5B, col contenuto
dentro e sfocato all'inizio.

Dall'alto:

1. **L'anteprima.** La miniatura da 56 dp (pronta in `item.preview`, o preparata dal foglio dal primo Uri di
   un'immagine, a campioni, fuori dal thread principale), il nome, la riga sotto («Immagine PNG · 245 KB», «3 foto ·
   4,2 MB», il dominio di un link, piu' `item.detail`: «Posizione inclusa») e la «x» di vetro. Con `copyOptions` sotto il
   nome c'e' «Opzioni ›», come nel foglio di Foto di iOS 26: apre le opzioni della copia **dentro il foglio** (vedi
   sotto). **La miniatura che arriva entra sfocata** al posto dell'icona del tipo (`FluidGlassContentTransition`): la
   cambia l'app, non il dito. Anche la riga sotto il nome, quando torna una copia nuova dalle opzioni, entra sfocata.
   Senza immagine c'e' l'icona del tipo (la catena di un link, il documento di un PDF): sul tablet il 25 novembre la
   miniatura era vuota in ogni scena per due motivi: `FluidGlassContentTransition` non disegnava uno stato null (ora lo
   fa, quando la sua chiave non e' null), e la lettura delle misure di un'immagine (`inJustDecodeBounds`, che torna
   sempre null) faceva uscire la miniatura di ogni Uri. La prova coi pixel e' `F8p10ThumbnailPixelsTest` (l'icona di un
   link, una foto letta dal suo file, un'immagine pronta). Mentre una copia lavora, la miniatura si legge dal file al
   suo posto e non si rilegge dalla copia.
2. **La fila delle app.** Le app che ricevono questo tipo e sono **fidate** (`FluidShareTargets`, `FluidShareTrust`):
   icone da 60 dp col nome sotto, le ultime scelte da questo foglio in cima, poi per nome; in fondo «Altro». La fila
   scorre col dito ed e' **sempre nitida**. La propria app non c'e' mai.
3. **Le azioni dell'engine** — Copia, Salva in File, Mostra come QR, Stampa — come in iOS 26: una **fila di tasti tondi**
   sotto un filo (il disco da 60 dp col simbolo, il nome sotto). Ognuna si nasconde dove non ha senso: il QR solo per un
   testo o un link e solo con un codificatore; la stampa per un'immagine, un PDF, un testo, o un oggetto con
   `item.print`. Con `FluidShareOptions(actionsAsList = true)` stanno invece in lista, come in iOS 17 e 18.
4. **La lista**: le azioni dell'app (`actions`) e in fondo **«Altre app…»**, il foglio di Android
   (`Intent.createChooser`), che e' l'unico che vede tutte le app. Il nome a sinistra e il simbolo a destra, come iOS;
   righe da 52 dp.

**Le azioni.**
- **Copia** mette negli appunti il testo, o gli Uri (con il permesso concesso a chi incolla). La riga diventa «Copiato»
  con la spunta (sfocata: la cambia l'app), e il foglio se ne va dopo 450 ms. Un oggetto delicato
  (`FluidShareItem.sensitive = true`: un codice, una password, un dato di salute) va negli appunti **segnato delicato**
  (`ClipDescription.EXTRA_IS_SENSITIVE`, Android 13 e oltre; prima non c'e' niente da segnare): Android non ne mostra il
  testo nell'anteprima della copia ne' nei suggerimenti della tastiera. Vale per ogni strada di «Copia» del foglio (il
  dito, la tastiera, TalkBack: la stessa azione) e per la copia confermata nelle opzioni, anche dopo una rotazione (R2).
- **Salva in File**: il selettore di Android (`ACTION_CREATE_DOCUMENT`) per un oggetto solo, la cartella
  (`ACTION_OPEN_DOCUMENT_TREE`) per piu' file; la copia dei byte va su `Dispatchers.IO`. «Salvato» o «Non si è salvato».
- **Mostra come QR**: il corpo del foglio cambia (sfocato) nel codice grande, il testo sotto e «Fine». Il codice si
  calcola **alla pressione, su `Dispatchers.Default`**: aprire il foglio non codifica niente sul thread principale (R2).
  L'azione c'e' per un testo che entra in un QR versione 40 con correzione M: al massimo 1200 caratteri e 2331 byte.
- **Stampa**: l'anteprima di stampa di Android (`FluidPrint`), poi il foglio si chiude. Un `item.print` con viste
  Compose si stampa col PDF del foglio (`FluidPdfWriter`): prima il tasto c'era ma non faceva niente (R2).
- **Un'app della fila**: `SEND` diretto a quell'attivita' (`FluidShareIntents.sendTo`), e il foglio si chiude. Un'app
  che non parte (disinstallata nel frattempo, un permesso) fa sentire l'aptica d'errore e il foglio resta.
- **Un esito solo.** Dopo il primo esito (la spunta di «Copiato» o «Salvato» resta 450 ms) le altre azioni non
  rispondono: niente secondo esito, niente seconda chiusura (R2).

**Gli intent.** Tutti da `FluidShareIntents`: `FLAG_GRANT_READ_URI_PERMISSION` e un `ClipData` con **tutti** gli Uri (senza,
`SEND_MULTIPLE` concede la lettura del primo solo); `EXTRA_TITLE` per l'anteprima del foglio di Android;
`EXTRA_EXCLUDE_COMPONENTS` con le attivita' della propria app; da Android 14 `EXTRA_CHOOSER_CUSTOM_ACTIONS` con le azioni
dell'app (`FluidShareChooserAction`, un `PendingIntent` perche' il foglio di sistema vive in un altro processo).

**La fila e la fiducia.** Android non lascia elencare tutte le app: servirebbe `QUERY_ALL_PACKAGES`, che l'engine non
chiede. `engine-ui` dichiara nel manifest un `<queries>` per le sole app che ricevono `SEND` e `SEND_MULTIPLE`; fra quelle
il foglio tiene le app fidate, con la stessa verifica di `engine-ai-bridge`, la firma:
- `FluidShareTrust.SameSigner` (di serie): `checkSignatures` uguale a questa app. E' la famiglia Pampa per un'app firmata
  con la chiave di Pampa.
- `FluidShareTrust.certificates(impronte)`: l'impronta SHA-256 del certificato di firma (quella che il manifest dello
  store porta). Serve a una build di prova firmata con un'altra chiave, o per fidarsi di piu' chiavi.
Il nome del pacchetto da solo non basta mai: un'app qualunque puo' chiamarsi come una di Pampa.

**I file.** `FluidShareFiles` e' la cartella delle uscite (`cacheDir/fluid-share/<una cartella per uscita>/<nome>`) con il
suo FileProvider (`FluidShareFileProvider`, autorita' `<applicationId>.fluid.share`, non esportato): non litiga con il
`FileProvider` dell'app. `FluidShareFiles.clean(context)` toglie le uscite piu' vecchie di un giorno. **La pulizia e'
dell'app**: l'engine non la chiama mai da solo (non sa se un'uscita vecchia serve ancora), quindi l'app la chiama, fuori
dal thread principale, per esempio all'avvio insieme a `FluidShareInFiles.clean` di `engine-share-in`. Senza, le copie
(anche le foto con la posizione) restano nella cache finche' Android non la svuota.

**Un file qualsiasi dell'app** (come `ShareLink`, che accetta qualunque file leggibile). `FluidShareItem.file(context,
file, …)` non lancia mai: un file sotto la cartella dell'engine (`FluidShareFiles.isShareable`, percorsi canonici
confrontati cartella per cartella) ha subito il suo Uri; uno fuori (una foto in `filesDir`) resta in
`FluidShareItem.localFiles` (`needsCopy`) e il foglio lo copia nella cartella dell'engine **quando si apre**
(`FluidShareFiles.stage`): fuori dal thread principale, annullabile (la copia a meta' si cancella), il nome resta.
- Mentre copia, la miniatura (letta dal file al suo posto) ha l'attesa sopra e il corpo dice «Preparo il file…»: dopo
  150 ms, cosi' una foto piccola non fa lampeggiare niente. Il cambio entra sfocato, lo fa l'app.
- Il tetto della copia e' `FluidShareOptions.copyLimitBytes` (di serie `FluidShareFiles.DefaultCopyLimitBytes`, 512 MB);
  la copia lascia anche 16 MB liberi sul disco. **I byte si contano copiando** (R2): un file che cresce mentre si copia
  (un video ancora in registrazione) si ferma al tetto («troppo grande», col peso vero) o allo spazio libero («non c'e'
  abbastanza spazio») invece di superarli; il disco pieno a meta' e' «spazio», non «non si legge»; il peso sotto il nome
  e' quello della copia.
- Un'uscita gia' dentro la cartella dell'engine ma **cancellata** (Android ha svuotato la cache) resta da controllare come
  un file da copiare: il foglio dice «Il file non c'e' piu'» invece di mandare un Uri morto (R2).
- Un file che non c'e', che non si legge (una cartella, un errore a meta' copia), oltre il tetto o senza spazio non
  parte: al posto delle azioni c'e' il **perche'** con parole sue (`FluidShareFileProblem`, le frasi in
  `FluidShareLabels`: il peso e il tetto quando contano) e «Chiudi». Nessun tocco lancia un'eccezione.
- Senza il foglio: `FluidShareFiles.stage(context, item)` torna `FluidShareStaged.Ready` (solo Uri `content://`) o
  `FluidShareStaged.Failed` (il motivo), poi `FluidShareIntents`.
- Le prove: sul JVM di Windows il `FileProvider` vero non trova le sue radici, quindi le prove danno gli Uri con
  `FluidShareFiles.overrideUrisForTesting` (mai in un'app). Il caso vero si guarda sul dispositivo.

**Le persone.** La fila delle persone recenti di iOS non c'e': Android non le da' a un'app. Le scorciatoie di condivisione
delle app che ricevono (le «persone» e i quaderni) compaiono nel foglio di Android, «Altre app…».

**Trappole.**
- Il foglio e' un portale: lo disegna l'host dei modali alla radice. `LocalFluidQrEncoder` lo legge il foglio dove e'
  dichiarato, quindi `ProvideFluidQr` attorno alla sola pagina basta.
- Un `item` nuovo mentre il foglio e' aperto ne cambia il contenuto; `null` lo chiude.
- Un Uri `file://` non parte mai: Android lo vieta fra app (`startActivity` lancerebbe `FileUriExposedException`). Per un
  `File` dell'app c'e' `FluidShareItem.file(context, file, …)`, che lo copia nella cartella dell'engine se serve. Se un
  `file://` finisce lo stesso fra gli `uris` (`FluidShareItem.file(uri, …)`), l'oggetto ha `needsCopy` e il foglio lo
  copia come un file dell'app: fuori parte un `content://` (R2). Chi usa `FluidShareIntents` senza il foglio chiama
  prima `FluidShareFiles.stage`.
- **Il foglio ricorda la copia confermata** (R2). La scelta delle opzioni e la copia verificata si salvano dove il foglio
  e' dichiarato, non nel suo contenuto (che l'host dei modali compone alla radice e che alla rotazione ripartirebbe da
  zero): dopo una rotazione, o col processo ripristinato e l'app che ripassa lo stesso oggetto (riconosciuto da titolo,
  tipo, testo, Uri e file), il foglio riparte dalla copia, **mai dall'originale**. Un'apertura nuova o un altro oggetto
  ripartono da capo. La copia si salva senza l'anteprima (si rilegge dal file) e senza `print`; una col testo oltre i
  20 000 caratteri non sta nello stato salvato, e al ritorno il foglio la rifa' dalla scelta, con l'attesa e la verifica.

## Prepara la copia: `FluidPrepareCopySheet`

**Cos'e'.** Il foglio «Opzioni» di Foto su iPhone (la posizione di una foto), prima del foglio di condivisione. In cima il
formato (`formats`, un segmentato se piu' d'uno) e i dati da includere (`options`, interruttori). Sotto, l'esito.

**Chi fa cosa.** L'app prepara la copia **vera** (`prepare`, sospesa, fuori dal thread principale) e la verifica sul file
uscito (`FluidCopyCheck`: «Posizione — tolta», «Peso — 1,2 MB»). L'engine:
- rifa' la copia a ogni cambio di scelta, dopo 180 ms (due interruttori di fila fanno una copia sola), annullando quella
  prima. **Una copia annullata non diventa mai quella pronta** (R2): anche se `prepare` non guarda l'annullamento (un
  callback in `suspendCoroutine`, una copia bloccante) e finisce dopo che la persona ha tolto la posizione, la sua copia
  si butta; la conferma prende solo la copia della scelta di adesso;
- mostra l'attesa, poi il riepilogo delle verifiche (sfocati: li cambia l'app; gli interruttori e il segmentato li muove
  il dito e restano nitidi);
- **blocca l'invio** se la copia non corrisponde alle opzioni (`fluidCopyProblems`): una verifica fallita, o un dato
  lasciato a casa **senza una verifica passata** che lo dica. «Una spunta da sola non toglie un dato dal file»: senza la
  prova la copia non parte. «Condividi» resta spento e c'e' «Rifai la copia».

**Dentro il foglio: `FluidShareCopyOptions`.** Come in Foto di iOS 26, le opzioni sono una pagina del foglio di
condivisione: `FluidShareSheet(…, copyOptions = FluidShareCopyOptions(prepare, formats, options))`. «Opzioni ›» la spinge
da destra ([`FluidSheetPages`](menu-modali.md), la pagina sotto arretra di un terzo e sfuma), con indietro in alto a
sinistra e la spunta in alto a destra, accesa solo con la copia pronta; «Rifai la copia» sta nell'esito. Confermata, si
torna alla condivisione con la **copia nuova al posto dell'oggetto** (anteprima, riga sotto il nome, azioni): il foglio
non si chiude e non si riapre. Indietro (il tasto, il back di sistema, il dito dal bordo sinistro) torna senza cambiare
niente. Riaperta, la pagina riparte dall'ultima scelta confermata e non rifa' la copia finche' la scelta e' quella.
L'oggetto passato al foglio dev'essere la copia della scelta di partenza (le `options` con `included`, il primo formato).
**Se la scelta di partenza lascia a casa qualcosa** (un'opzione con `included = false`), il foglio non si fida
dell'oggetto: all'apertura prepara e verifica lui la copia di partenza, con «Preparo il file…» al posto delle azioni, e
manda quella; se non corrisponde dice «La copia non corrisponde alle opzioni: non parte.» con «Chiudi», e «Opzioni ›»
resta per rifarla (R2). Con tutte le opzioni incluse di serie (come Foto) il foglio manda l'oggetto com'e'.

**A se': `FluidPrepareCopySheet`.** Lo stesso contenuto in un foglio suo, per chi chiede prima di aprire la
condivisione: `onReady` riceve l'oggetto da condividere, e di solito l'app chiude le opzioni e apre `FluidShareSheet` con
lui. La scelta si salva col foglio: dopo una rotazione gli interruttori restano come li ha lasciati il dito (la posizione
tolta resta tolta) e la copia si rifa' per quella scelta (R2).

`FluidCopyTools` porta gli attrezzi delle foto: `copyWithoutLocation` (copia il JPEG e svuota i campi GPS dell'EXIF) e
`hasLocation` (legge il file), con `android.media.ExifInterface`. Una ricompressione perde tutto l'EXIF: chi riduce una
foto rimette i dati che l'utente ha chiesto di tenere. Una foto modificata con `engine-editor` tiene di serie la
posizione, come Foto (R3): è qui, con «Posizione» nelle opzioni, che la persona la toglie, e il foglio lo verifica sul file
([`../moduli/editor.md`](../moduli/editor.md)).

## L'immagine da condividere: `FluidShareImageRenderer`

**Cos'e'.** `ImageRenderer` di SwiftUI: una vista Compose resa in una bitmap fuori dallo schermo. Si ottiene con
`rememberFluidShareImageRenderer()` nella pagina che condivide; la vista eredita il tema, la lingua e le parole della
pagina.

- **I formati** (`FluidShareImageFormat`): `Square` 1080 × 1080, `Portrait` 4:5 1080 × 1350 (la piu' alta che un post
  mostra intera), `Story` 9:16 1080 × 1920. Il contenuto si compone a 360 dp di larghezza: un dp e' 3 pixel, e il testo si
  legge come sul telefono. Un formato proprio: `FluidShareImageFormat(nome, w, h, widthDp)`.
- **La cornice** (`FluidShareImageFrame`): il fondo dell'accento (sfumato, con due chiarori fermi), il pannello «stampa»
  al centro col contenuto, la firma in fondo («ClasseViva»). Come le card che Fitness o Musica fanno per le storie.
- **Il materiale «stampa»** (`FluidPrintPanel`, `FluidPrintColors`): fuori dallo schermo il vetro non ha uno sfondo da
  piegare. Il pannello porta i tre segni del vetro fermi: il film del modale appena tinto, la luce del bordo in alto a
  sinistra, il riflesso morbido in cima, e un'ombra di maschera. Nessuna sfocatura, nessuna lente. In chiaro e' chiaro,
  in scuro e' scuro: segue il tema della pagina. Dentro un'uscita `LocalFluidPrinting` e' vero.
- **Il disegno** e' software, sul thread principale, una volta per immagine (pochi millisecondi): il testo e le forme
  sono veri. Il PNG si comprime su `Dispatchers.IO` (`renderToFile`). La tela si prenota nel registro della memoria
  (`FluidMemoryBudget`, categoria `VIEWER`) prima di esistere: se non c'e' posto, `FluidOutputTooLargeException`. Un PNG
  che non si scrive per intero (il disco pieno) si cancella e `renderToFile` lancia `IOException`: mai un file tronco
  pronto da condividere (R2).

**Trappole.** Il contenuto deve essere pronto alla prima composizione: un'immagine che arriva dalla rete dopo non fa in
tempo (si passa gia' decodificata). Niente `fluidGlassPanel`, `Modifier.blur` o ombre di elevazione dentro un'uscita: su
una tela software non ci sono.

## Il PDF: `FluidPdf`, `FluidPdfWriter`, `FluidPdfLayout`

**Cos'e'.** «Salva PDF» e «Stampa» di una pagina Compose qualunque, non solo del contenuto web (prima stampava solo
`FluidWebContent`). Un documento (`fluidPdfDocument`) e' una fila di pezzi: titolo, sezione, paragrafo, riga
«etichetta — valore», filo, spazio, e una **vista Compose** dell'app (un grafico, una card col suo stile).

- **Testo vero.** Il `Canvas` di `PdfDocument` scrive il testo come testo, coi caratteri dentro il file: nel PDF si
  seleziona e si cerca. Anche le viste Compose scrivono testo e vettori (un dp vale 0,75 punti: un corpo da 16 sp esce a
  12 pt).
- **La pagina.** A4 di serie (Letter per chi lo vuole), margini di 48 pt, l'intestazione ripetuta in cima con un filo
  nell'accento, «Pagina 2 di 5» in fondo (la frase la da' `pageLabel`). La carta e' sempre chiara; l'accento si scurisce
  fino al contrasto 4,5:1 sul bianco.
- **Nessuna riga tagliata.** Un paragrafo si spezza solo fra le righe, con almeno due righe per parte quando ne ha quattro
  o piu'; un titolo non resta solo in fondo; una riga «etichetta — valore» e una vista Compose non si spezzano (una vista
  piu' alta di una pagina si riduce; una riga piu' alta di una pagina, un'etichetta lunghissima, si spezza fra le sue
  righe come un paragrafo e ogni pagina ne mostra solo la sua fetta: R2, prima sbordava oltre il fondo e si ripeteva in
  cima alla pagina dopo). Nel taglio la riga dopo non finisce nel file ritagliata via: nel testo che si seleziona non
  c'e' niente di invisibile.
- **A pezzi, nella memoria.** Si impagina un paragrafo alla volta; `PdfDocument` tiene ogni pagina finche' il file non e'
  scritto, e ogni pagina si prenota prima nel registro (`FluidMemoryBudget`, `VIEWER`: 24 KB per una pagina di testo, 96
  KB in piu' per ogni vista Compose). Un documento che non ci sta si ferma con `FluidOutputTooLargeException` invece di
  finire la memoria.
- **I thread.** Senza viste Compose tutto va su `Dispatchers.Default` e `IO`; con le viste Compose l'impaginazione e il
  disegno stanno sul thread principale, la scrittura su `IO`.
- **Le pagine senza file**: `FluidPdf.layout(documento)` (o `writer.layout`) da' il numero di pagine e disegna ogni pagina
  su una tela qualunque: un'anteprima, una miniatura. E' la stessa impaginazione del file e della stampa. **Impagina
  subito, sul thread di chi chiama**: per un documento lungo `withContext(Dispatchers.Default) { FluidPdf.layout(documento) }`
  (`writer.layout`, con le viste Compose, resta sul principale).

## La stampa: `FluidPrint`

L'anteprima di Android (`PrintManager`), come `UIPrintInteractionController`: il foglio, la stampante e «Salva come PDF».
Un documento si reimpagina sul foglio scelto (A4, Letter…), con gli stessi margini e lo stesso stile. `printImage` mette
un'immagine intera al centro della pagina (da un Uri si legge a non piu' di 3508 px di lato, un A4 a 300 dpi);
`printPdf` passa un PDF fatto alla stampante com'e'.

**I thread** (R2). Android chiama l'adattatore di stampa sul thread principale, ma lascia finire il lavoro altrove e
rispondere da li': l'impaginazione e la scrittura di un documento senza viste Compose, la lettura dell'immagine e la
copia del PDF vanno su `Dispatchers.Default`, rispondono una volta sola, e l'annullamento dello spooler (o la chiusura
dell'anteprima) le ferma. Un documento con viste Compose resta sul principale, dove le viste si compongono.
**Le pagine scelte** nell'anteprima («2–3»): l'adattatore scrive tutte le pagine e lo dice (`PageRange.ALL_PAGES`), e
lo spooler di Android tiene quelle scelte da un documento che le contiene. Da verificare su un servizio di stampa vero.

## Token, costanti, Defaults

| nome | valore | fonte |
|---|---|---|
| miniatura del foglio | 56 dp | a occhio dalle schermate del foglio di iOS 26, 24 novembre 2026 |
| icona di un'app nella fila | 60 dp, nome sotto in `labelSmall`, due righe | le icone del foglio di iOS |
| il QR nel foglio | al massimo 280 dp, quadrato, si stringe a meta' altezza: il testo e «Fine» restano sempre dentro il foglio | a occhio |
| tasto tondo d'azione | disco da 60 dp, simbolo da 26 dp, nome sotto in `labelSmall`, due righe | la fila di iOS 26 (guida dell'iPhone, «Condividi foto e video»), a occhio |
| riga d'azione | 52 dp, nome a sinistra, simbolo da 22 dp a destra | il foglio di iOS 26; sopra i 44 dp delle regole |
| app nella fila | al massimo 8 (`FluidShareTargets.MaxTargets`) | a occhio |
| spunta di «Copiato» / «Salvato» | 450 ms, poi il foglio esce | a occhio, 24 novembre 2026 |
| QR | correzione M, zona vuota 4 moduli, al massimo 1200 caratteri e 2331 byte in UTF-8 | ISO/IEC 18004 (2331: versione 40, M, modo byte); a occhio per la lunghezza |
| testo di una copia nello stato salvato | al massimo 20 000 caratteri (`FluidShareSavedTextChars`); oltre, la copia si rifa' dalla scelta | a occhio: 40 KB nel `Bundle` |
| attesa prima della copia | 180 ms | a occhio |
| tetto della copia di un file dell'app | 512 MB (`FluidShareFiles.DefaultCopyLimitBytes`), piu' 16 MB lasciati liberi | a occhio, 24 novembre 2026: un video di qualche minuto in 4K ci sta |
| attesa della copia, prima di farsi vedere | 150 ms | a occhio: una foto si copia prima |
| pagina delle opzioni nel foglio | molla delle pagine (0,34 s, critica), quella sotto arretra di W/3 e sfuma col quadrato della corsa; tasti della barra da 44 dp | `FluidPageMotion`; la sfumatura a occhio, 24 novembre 2026; i 44 dp dalla guida di iOS 26 |
| formati delle immagini | 1080 × 1080, 1080 × 1350, 1080 × 1920 a 360 dp | i formati delle chat e delle storie |
| pagina | A4 595 × 842 pt, margini 48 pt, corpo 11 pt, titolo 22, sezione 14, interlinea 1,25 | a occhio, sulle misure di una lettera stampata |
| un dp in un PDF | 0,75 pt | a occhio (un corpo da 16 sp a 12 pt) |

## Cosa è verificato dai test

In `sample/src/testDebug/…/novita/`, Robolectric con la grafica vera:
- `F8p10ShareUiTest`: gli intent di uno e di tanti file col permesso di lettura e il `ClipData` completo; il foglio di
  Android senza la propria app; «Altre app…» col file; la fila delle app con un'app finta fidata (e senza la galleria,
  che pure riceve immagini), il tocco che la manda e i recenti; la fiducia per impronta che scarta le altre chiavi;
  Copia negli appunti e l'uscita dopo la spunta; le azioni secondo l'oggetto (link col QR, tre foto senza QR ne'
  stampa); TalkBack (la «x», la miniatura, i tasti, le altezze); la regola di «Prepara la copia» e il foglio col dito,
  che rifa' la copia, mostra il riepilogo e blocca l'invio di una copia sbagliata; «Opzioni ›» come pagina dentro il
  foglio: arriva da destra (a due fotogrammi e' ancora a destra; col movimento ridotto e' gia' al suo posto), la pagina
  sotto non si vede, la conferma torna con la copia nuova senza chiudere il foglio, la pagina riaperta riparte dalla
  scelta confermata senza rifare la copia, e indietro col tasto, col back di sistema (al secondo back si chiude il
  foglio) e col dito dal bordo sinistro. Un file dell'app fuori dalla cartella dell'engine: copiato e mandato da «Altre
  app…» (la copia nella cartella, gli stessi byte, il permesso di lettura); un file che non c'e', una cartella e un file
  oltre il tetto danno il messaggio al posto delle azioni, senza crash, e «Chiudi» chiude. Dopo la review R2
  (`StateRestorationTester` per la rotazione e il processo ripristinato): la copia senza posizione resta dopo la
  rotazione e parte lei, non l'originale; il foglio delle opzioni a se' tiene la posizione tolta; una copia vecchia che
  finisce in ritardo non si conferma; con la posizione spenta di serie parte la copia verificata, e una copia di partenza
  che tiene la posizione non parte; un `file://` esce come `content://`; dopo «Copia» un secondo tocco non fa niente; il
  QR si codifica solo alla pressione e fuori dal thread principale; un oggetto delicato va negli appunti segnato
  delicato (col dito e con l'azione di TalkBack e della tastiera), uno qualsiasi no, e la copia delicata confermata
  nelle opzioni resta delicata dopo la rotazione.
- `F8p10PrintThreadTest`: l'impaginazione e la scrittura di un documento, e la lettura di un'immagine, rispondono da un
  altro thread e una volta sola; un'impaginazione annullata risponde «annullata». Le callback dell'adattatore si
  estendono dal pacchetto `android.print` (`F8p10PrintCallbacks`): i loro costruttori sono del pacchetto.
- `F8p10StagingTest`: `FluidShareItem.file` non lancia mai (fuori dalla cartella diventa un file da copiare; dentro ha
  subito l'Uri; senza il provider finto ripiega sulla copia); `FluidShareFiles.stage` copia e da' Uri `content://`,
  tiene l'ordine di piu' file, e un file mancante o oltre il tetto torna col motivo; un'uscita cancellata dentro la
  cartella dice «non c'e' piu'».
- In `engine-ui`, al JVM, `FluidShareStagingTest`: dentro o fuori la radice cartella per cartella (anche
  `fluid-share2/` e `..`), la copia vera (nome e byte), un file gia' dentro non si copia e non conta per il tetto, file
  mancante, cartella, oltre il tetto e senza spazio (niente copiato), le frasi dei messaggi; un file che cresce mentre si
  copia si ferma al tetto e allo spazio libero, col peso vero; la fiducia per impronta e' un valore (uguale se uguali).
- `F8p10OutputTest`: l'immagine nei tre formati coi pixel veri (fondo colorato, pannello chiaro, pieno e piatto, il
  testo), lo scuro; il PDF di molte pagine con l'intestazione e il numero su ognuna, ogni riga intera dentro i margini,
  ogni paragrafo una volta sola e nessuna bitmap; la pagella della galleria col grafico Compose che scrive testo vero;
  una riga piu' alta di una pagina resta nei margini e ogni sua parola compare una volta sola.
- `engine-qr`: il QR si rilegge con ZXing (link, testo con gli accenti), un testo troppo lungo non da' un codice.
Il PDF vero (con `PdfDocument`, che Robolectric non ha) si rilegge sul dispositivo nella scena «La pagella in PDF»: le
pagine con `PdfRenderer` e, da Android 15, il testo della prima pagina.

## Dove guardare

- `engine-ui/…/ui/fluid/share/`: `FluidShareItem.kt`, `FluidShareIntents.kt`, `FluidShareTargets.kt`, `FluidShareFiles.kt`,
  `FluidShareSheet.kt`, `FluidPrepareCopy.kt`, `FluidQr.kt`.
- `engine-ui/…/ui/fluid/output/`: `FluidOffscreen.kt` (la vista fuori dallo schermo), `FluidPrintPanel.kt`,
  `FluidShareImage.kt`, `FluidPdfDocument.kt`, `FluidPdf.kt`, `FluidPrint.kt`.
- Il manifest di `engine-ui`: il `<queries>` e il provider delle uscite.
- La galleria: «Elementi nuovi», F8.10 (`sample/…/novita/F8p10Scenes.kt`).
