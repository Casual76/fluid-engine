# F6C.7 · Visori, seconda ondata: ricerca e proposte

Ricerca del 22 novembre 2026 per la seconda ondata di F6C (piano, «F6C, seconda ondata»). Ci sono sette parti:
- §1, come si legge ogni famiglia;
- §2, cosa fa Apple tipo per tipo;
- §3, l'apertura dalla miniatura;
- §4, i moduli e il registro;
- §5, le tre direzioni d'aspetto;
- §6, la lista d'accettazione da F6C.8 a F6C.15;
- §7, le quattro domande per Alessio.

Il materiale (tavole, clip, fermi) è in [artifacts/liquid-glass/ricerca/visori-2/](../../../artifacts/liquid-glass/ricerca/visori-2/LEGGIMI.md).

## 1. In breve: come si legge ogni famiglia

| Famiglia, modulo | Lettura scelta | Libreria | Licenza | Peso nell'APK, per arm64-v8a | Android minimo |
|---|---|---|---|---|---|
| **Base**, `engine-viewer` (c'è già) | PdfRenderer, ImageDecoder, testo, Media3 `compileOnly`, risolutore; diventa il **registro** | nessuna nuova | — | invariato (lo misura F6C.15) | 8.0, API 26 (il minSdk dell'engine) |
| **Office**, `engine-viewer-office` | **propria**: ZIP (`java.util.zip`) + `XmlPullParser` della piattaforma per DOCX, XLSX, PPTX, ODT, ODS, ODP; un lettore RTF proprio; un impaginatore proprio su `StaticLayout` | nessuna (facoltativi i caratteri metrici, §1.1) | — (caratteri: OFL / Apache-2.0) | stima 0,2–0,4 MB di codice dopo R8; con i caratteri metrici +3–6 MB (stima) | API 26 |
| **Archivi**, `engine-viewer-archive` | **libarchive** per ZIP, 7z, RAR 4 e 5, TAR con gz, bz2, xz e zst; elenco, cartelle, una voce estratta in cache e aperta dal risolutore | `me.zhanghai.android.libarchive:library` 1.1.7 (settembre 2026) | Apache-2.0 per il collegamento; libarchive BSD-2; zlib, bzip2, xz, lz4, zstd, mbedTLS: zlib, BSD, 0BSD, Apache-2.0 | 2,35 MB non compressi (1,08 MB scaricati) | API 21 per la libreria, quindi 26 |
| **Testi ricchi e dati**, `engine-viewer-data` | **propria**, sopra engine-ui: `FluidMarkdown`, `FluidWebContent` (WebView chiusa) per HTML, EPUB ed EML, `JsonReader` e `XmlPullParser` della piattaforma, lettori propri CSV, EML, ICS, VCF | nessuna | — | stima 0,1–0,25 MB | API 26 |
| **Immagini speciali e font**, `engine-viewer-graphics` | piattaforma per HEIC/HEIF (API 28), AVIF (API 31), DNG; **propria** per l'anteprima incorporata nei RAW, il TIFF di base, il PSD composito, le tabelle dei font (`Typeface.Builder` per disegnarli); **libreria** solo per SVG | AndroidSVG 1.4 (`com.caverock:androidsvg-aar`), più `androidx.exifinterface` 1.4.2 | Apache-2.0 tutte e due | AndroidSVG 0,21 MB di classi, exifinterface 0,07 MB; meno dopo R8 | API 26; HEIC da API 28, AVIF da API 31, sotto «Apri con…» |
| **3D**, `engine-viewer-3d` | **proposta: renderer GLES 3 proprio** per STL, OBJ e GLB statici; alternativa: Filament con gltfio (domanda ad Alessio) | nessuna; oppure Filament 1.71.5 | — ; Filament Apache-2.0 | proprio ≈0,1 MB (stima); Filament 6,5 MB non compressi (3,1 MB scaricati) | API 26 (GLES 3.0 c'è da API 18) |
| **Tutti**, `engine-viewer-all` | solo dipendenze `api` verso i cinque moduli | — | — | la somma | API 26 |

Come leggere i pesi:
- sono misurati sugli AAR e JAR di Maven Central del 22 novembre 2026: la `.so` per arm64-v8a, la sua compressione deflate e il `classes.jar`;
- AGP, con minSdk 23 o più, mette le librerie native **non compresse** nell'APK, così l'app installata paga il numero «non compressi»;
- dal Play Store si scarica circa il numero «scaricati», e una sola architettura;
- le «stime» del codice proprio sono a occhio: le misura F6C.15, come vuole il piano.

**Cosa resta fuori e perché.**
- **Apache POI**: 13 MB di JAR (poi, poi-ooxml, poi-ooxml-lite, xmlbeans 5.x), e in parte richiede `java.awt`.
- **junrar**: la licenza UnRAR non è OSI e vieta di usare il codice per un compressore RAR. Non vieta l'uso, ma non è «Apache, MIT, BSD o simili». È l'alternativa di §1.2.
- **libheif** e i lettori LGPL o GPL (7-Zip-JBinding): esclusi.
- **Readium**: per gli EPUB basta la WebView chiusa di engine-ui.

### 1.1 Office «a pagine come il documento»

Scelta di Alessio del 22 novembre: i fogli uno sotto l'altro, zoomabili come un PDF; le slide una per pagina; i fogli di
calcolo una griglia. Tutto passa dal contratto `Pages` di oggi, cioè `FluidPageSource` con conteggio, misura e resa di una
pagina. Fa eccezione la griglia, che è una forma nuova (§4.3).

**Un solo modello per tre lettori.** DOCX, ODT e RTF diventano lo stesso documento interno:
- **i blocchi**: paragrafo con i pezzi di testo, tabella, immagine, interruzione di pagina, cambio di sezione;
- **gli stili già risolti**:
  - DOCX: `styles.xml` con `basedOn` e `docDefaults`, `numbering.xml`, il tema per i caratteri `minor`/`major`;
  - ODT: `styles.xml` con gli stili automatici di `content.xml`;
  - RTF: `\stylesheet`, `\fonttbl`, `\colortbl`.

Un impaginatore solo trasforma il modello in pagine.

**Il foglio.** La misura e i margini vengono dal documento:
- DOCX: `w:sectPr/w:pgSz` e `w:pgMar`, in ventesimi di punto;
- ODT: `style:page-layout-properties`, con `fo:page-width` e `fo:margin-*`;
- RTF: `\paperw`, `\paperh`, `\margl` e gli altri margini.

A4 e Letter si riconoscono da soli: la pagina è sempre quella del file, in punti a 72 dpi. Ogni sezione può cambiare
foglio e orientamento.

**Cosa si impagina nella prima versione:**
- **titoli e stili di paragrafo**: carattere, corpo, peso, corsivo, sottolineato, barrato, colore, evidenziato, apice e
  pedice; allineamento compreso il giustificato; rientri sinistro, destro, prima riga e sporgente; spazio prima e dopo;
  interlinea automatica, esatta e «almeno»; «mantieni con il successivo» e «mantieni insieme» approssimati;
- **elenchi**: puntati e numerati su più livelli, con `numFmt` decimale, lettere, romani, e testo del livello come «%1.%2.»;
- **tabelle**: griglia dalle colonne (`tblGrid`), celle unite in orizzontale (`gridSpan`) e in verticale (`vMerge`),
  bordi, sfondo delle celle, margini interni, righe che si spezzano fra due pagine, righe d'intestazione ripetute;
- **immagini**: in linea, misurate dall'`extent` in EMU; quelle ancorate vanno nel punto del paragrafo d'ancora, come
  blocco;
- **intestazioni e piè di pagina**: prima pagina, pari e dispari, con i campi PAGE e NUMPAGES;
- **note a piè di pagina** in fondo alla pagina, **note di chiusura** in fondo al documento;
- **collegamenti**: aprono il browser tramite l'app;
- **interruzioni**: di pagina, di colonna (come pagina), di sezione con pagina nuova.

**Cosa si approssima:**
- i caratteri che il telefono non ha (§ «La domanda sui caratteri», sotto);
- le forme con il testo attorno (diventano blocchi);
- più colonne per sezione: una colonna sola nella prima versione, con l'avviso nel pannello info;
- vedove e orfane;
- la sillabazione (spenta);
- i campi che non sono PAGE o NUMPAGES: si mostra l'ultimo risultato salvato nel file.

**Cosa resta fuori nella prima versione** (il pannello info dice che c'è):
- revisioni e commenti: si mostra il testo finale;
- grafici di Word, SmartArt, equazioni: per le equazioni il testo lineare, per gli altri un riquadro con il titolo;
- caselle di testo ruotate, WordArt;
- caratteri incorporati;
- `.doc`, `.xls` e `.ppt` binari: «Apri con…».

**Il foglio di calcolo (XLSX, ODS) è una griglia.** Vengono dal file:
- i fogli e il loro ordine;
- larghezze delle colonne e altezze delle righe;
- righe e colonne bloccate (`pane`), celle unite, righe e colonne nascoste;
- stili di cella: carattere, colore, riempimento, bordi, allineamento, a capo;
- **il valore salvato** delle formule (`<v>`): niente ricalcolo;
- il formato dei numeri: un formattatore proprio per i codici comuni (`0`, `0.00`, `#,##0`, `%`, valute, date in
  `d/m/yyyy` con l'epoca 1900 o 1904, `@`);
- le immagini ancorate alle celle.

I grafici diventano un riquadro con il titolo. La seconda versione può disegnarli dai valori salvati (`c:numCache`). Un
foglio enorme si legge a flusso, con un tetto di celle e l'avviso «mostrate le prime N righe».

**Le slide (PPTX, ODP)** sono pagine della misura della presentazione (`p:sldSz`, di solito 16:9). Ogni slide si
disegna in quest'ordine:
1. lo sfondo, dalla slide, poi dal layout, poi dal master;
2. le forme, con le geometrie preimpostate comuni (rettangolo, arrotondato, ellisse, triangolo, frecce, linee); le
   altre diventano il loro riquadro;
3. riempimenti pieni, sfumati e d'immagine, e i contorni;
4. il testo, con autofit, punti elenco, livelli dagli stili del master e colori del tema;
5. immagini ritagliate, gruppi con le loro trasformazioni, tabelle.

Gli SmartArt usano il disegno già pronto che PowerPoint salva (`ppt/diagrams/drawing*.xml`). Un grafico diventa un
riquadro con il titolo, un video l'immagine d'anteprima. Le note del relatore vanno sotto la slide o nel pannello.
Animazioni e transizioni restano fuori.

**Ripieghi:**
- se una slide non si disegna, si usa `docProps/thumbnail.jpeg` per la prima;
- gli ODF hanno sempre `Thumbnails/thumbnail.png`, che serve anche da miniatura per l'apertura (§3);
- **Pages, Numbers e Keynote** (pacchetti ZIP) contengono `preview.jpg`: il modulo lo mostra come «anteprima salvata
  dall'app, solo la prima pagina», poi «Apri con…».

**La domanda sui caratteri.** Word impagina con caratteri che Android non ha: Calibri, Cambria, Times New Roman, Arial.
Le scelte sono due:
- **caratteri di sistema**: si sostituiscono con Roboto e Noto Serif. Costano zero byte, ma a capo e pagine si spostano
  un poco;
- **caratteri con le stesse misure**: Carlito per Calibri, Caladea per Cambria, Liberation Sans, Serif e Mono per
  Arial, Times e Courier. Le licenze sono OFL e Apache-2.0. A capo e interruzioni di pagina diventano quelli di Word,
  ma il modulo cresce di 3–6 MB, in base a quanti stili e lingue si tengono.

È una domanda per Alessio (D4). Il pannello info dice sempre quali caratteri sono stati sostituiti: lo mostra la tavola
B («Calibri → Inter»).

### 1.2 Archivi: libarchive, oppure Java

- **Proposta (D3, prima opzione): `libarchive-android`.** Una sola libreria legge le quattro famiglie di Alessio e altre ancora (ISO,
  CPIO, CAB). Si chiamano le funzioni C di `archive.h` dal collegamento Java. Fa anche:
  - ZIP cifrati, ZipCrypto e AES (con mbedTLS);
  - 7z cifrati;
  - i nomi in codifiche vecchie.

  Non apre i **RAR cifrati**: libarchive non li supporta, e il modulo li mostra come protetti, con «Apri con…». Pesa
  2,35 MB non compressi per arm64.
- **Alternativa (D3, seconda opzione): Java.**
  - ZIP con `java.util.zip.ZipFile`, che vuole un file vero: un `content://` si copia prima in cache;
  - TAR con un lettore proprio di circa 200 righe, gz con la piattaforma;
  - 7z, bz2 e xz con commons-compress 1.28.0 e xz 1.10. Commons-compress porta con sé commons-io, commons-lang3 e
    commons-codec: circa 2,7 MB di JAR, molto meno dopo R8;
  - RAR **solo come elenco**, con un lettore proprio delle intestazioni RAR 4 e 5; oppure junrar 8.1.2, che dalla
    8.0 di luglio 2026 estrae anche i RAR 5, ma con la licenza UnRAR.

  Niente codice nativo, ma più pezzi e un RAR a metà.

**Regole di sicurezza, in tutte e due:**
- nessun percorso esce dalla cartella di cache (niente «zip slip»);
- tetti sulla dimensione estratta e sul rapporto di compressione (niente «zip bomb»);
- i collegamenti simbolici restano voci, senza seguirli;
- una voce più grande del tetto non si estrae: si offre «Apri con…» sull'archivio.

Un file dentro si apre dal risolutore come un file qualunque (§4.2): un PDF dentro uno ZIP si apre col preset del PDF.

### 1.3 Testi ricchi e dati: quasi tutto c'è già in engine-ui

- **Markdown**: `FluidMarkdown` sa titoli, tabelle, codice, cose da fare, citazioni. Le immagini restano il loro testo
  alternativo, l'HTML dentro il Markdown resta testo. Una capsula «Reso | Sorgente» passa al preset testo con i colori.
- **HTML**: `FluidWebContent` con JavaScript spento e rete chiusa. Le risorse relative (CSS e immagini nella stessa
  cartella) le serve `onRequest`, solo se l'app ha l'accesso alla cartella. Le immagini remote sono bloccate, con
  «Carica immagini» se l'app lo permette.
- **CSV e TSV**: un lettore proprio RFC 4180. Riconosce da solo il separatore (`,` `;` tab `|`) e l'intestazione; la
  codifica la decide il `FluidTextDecoder` di oggi; i numeri si allineano a destra. Va nella stessa **griglia** del
  foglio di calcolo.
- **JSON**: `android.util.JsonReader` a flusso, in modo tollerante per i commenti di JSONC. Ne nasce un albero pigro con
  un tetto di nodi; gli elenchi lunghi si mostrano a pagine («mostra altri 100»); NDJSON diventa un elenco di righe.
  **XML** con `XmlPullParser`, e i plist XML con i tipi.
- **EPUB**: ZIP più `META-INF/container.xml`, poi l'OPF (metadati, manifesto, spina), poi l'indice (`nav.xhtml` o
  `toc.ncx`).
  - I capitoli si leggono in `FluidWebContent`, con `onRequest` che serve immagini, CSS e caratteri dallo ZIP, e un
    foglio di stile del lettore (tema, corpo, margini).
  - Nella prima versione è uno scorrimento per capitolo.
  - Gli EPUB con DRM (`encryption.xml`) sono «protetti», con «Apri con…»; quelli a impaginazione fissa vanno alla
    seconda versione.
- **EML**: un lettore MIME proprio:
  - intestazioni RFC 5322 e parole codificate RFC 2047;
  - `multipart/alternative`, `mixed` e `related`;
  - quoted-printable e base64, i charset.

  Si vede una scheda con le intestazioni, poi il corpo HTML in `FluidWebContent` (le immagini `cid:` servite dalle
  parti), poi gli allegati, che si aprono col risolutore come le voci di un archivio.
- **ICS**: un lettore RFC 5545 proprio, con le righe piegate, VEVENT, VTODO, i fusi con `java.time` e la regola di
  ripetizione detta in italiano per i casi comuni. «Aggiungi al calendario» è un intento dell'app.
- **VCF**: vCard 2.1, 3.0 e 4.0, con quoted-printable e foto. «Aggiungi ai contatti» è dell'app.

### 1.4 Immagini speciali e font

- **HEIC e AVIF** con `ImageDecoder`: HEIF da Android 9 (API 28), AVIF da Android 12 (API 31). Sotto quelle versioni
  vanno allo sconosciuto, che dice «serve Android 9» e offre «Apri con…».
- **RAW**:
  1. prima `ImageDecoder`, perché il DNG lo decodifica la piattaforma (da verificare sul Tab S9 in F6C.14);
  2. poi un lettore proprio che cerca l'**anteprima JPEG incorporata**, quasi sempre a piena risoluzione: le IFD TIFF di
     CR2, NEF, ARW, ORF, RW2 e PEF, il box `PRVW` del CR3, l'offset del RAF;
  3. EXIF con `androidx.exifinterface`.

  Quick Look sviluppa il RAW. Qui si mostra lo sviluppo della fotocamera, e il pannello lo dice.
- **TIFF**: un lettore di base proprio:
  - strisce e tasselli;
  - nessuna compressione, PackBits, LZW, Deflate;
  - 8 e 16 bit, RGB, grigi e tavolozza.

  Un TIFF di più pagine usa il contratto `Pages`. CCITT G3 e G4 (i fax delle scansioni) e il JPEG dentro il TIFF vanno
  alla seconda versione.
- **PSD e PSB**: il composito alla fine del file, RLE o grezzo, con un lettore proprio di circa 300 righe. Se manca
  («massima compatibilità» spenta) si usa la miniatura JPEG delle risorse (ID 1036). Il pannello mostra misure, livelli,
  modo colore e bit.
- **SVG e SVGZ**: AndroidSVG disegna un `Picture` vettoriale, nitido a ogni zoom. L'ultima versione è del 2019: se
  serve, si può copiare nel modulo, perché la licenza Apache lo permette.
- **Font** (TTF, OTF, TTC; WOFF decompresso con l'`Inflater` della piattaforma). Si disegnano con
  `Typeface.Builder(File)` (API 26), e l'indice delle collezioni e gli assi variabili sono pure API 26.
  - I dati vengono da un lettore proprio delle tabelle `name`, `OS/2`, `head`, `maxp`, `cmap` e `fvar`: famiglia,
    stile, versione, autore, licenza, numero di glifi, alfabeti coperti, assi.
  - WOFF2 va alla seconda versione: serve Brotli (`org.brotli:dec`, MIT, 96 KB) più la ricostruzione di `glyf`.

### 1.5 3D: un renderer proprio basta?

- **Filament** (Google, Apache-2.0) con gltfio legge i GLB con i materiali PBR veri e la luce da ambiente: il risultato
  più vicino ad AR Quick Look. Pesa per arm64, non compressi:
  - `libfilament-jni` 2,9 MB;
  - `libgltfio-jni` 3,0 MB;
  - `libfilament-utils-jni` 0,5 MB;

  in tutto 6,5 MB (3,1 MB scaricati). Le varianti «lite» non si pubblicano più dal 2023 (ultime: gltfio-android-lite
  1.23.1).
- **Un renderer GLES 3 proprio** fa:
  - STL binario e ASCII, OBJ con MTL, GLB statico (mesh, nodi, colore base e texture PNG o JPEG decodificate dalla
    piattaforma);
  - una luce fissa con un ambiente in armoniche sferiche precalcolate;
  - un'ombra morbida a terra, come il piano di Quick Look;
  - orbita, pizzico e doppio tocco.

  Sono circa 1.500 righe, senza librerie: ≈0,1 MB (stima). Non fa la PBR completa (metallo e rugosità solo
  approssimati), le animazioni, la pelle, Draco e KTX2.

Va disegnato su una `TextureView`, così il vetro lo campiona; una `SurfaceView` sotto il vetro sarebbe nera. È la stessa
regola del `sampleVideo` di oggi.

**Proposta**: il renderer proprio nella prima versione, in `engine-viewer-3d`, così si può sostituire con Filament
senza toccare `graphics`. È la domanda D2.

## 2. Apple, pagina per pagina

**Fonti.**
- La lista dei tipi di `QLPreviewController`, documentata: iWork, Office, RTF, PDF, immagini, testo che è
  `public.text`, CSV, USDZ «con la vista da sola e in AR»; e «la lista può cambiare fra le versioni». Pagina:
  [QLPreviewController](https://developer.apple.com/documentation/quicklook/qlpreviewcontroller).
- Due riprese pubbliche di iOS 26 su iPhone, a tempo vero (§3):
  - [MacMost](https://www.youtube.com/watch?v=KzcjEnAFFYY), 30 fps;
  - [jargonfreehelp](https://www.youtube.com/watch?v=RfNIua472i4), 50 fps, registrazione dello schermo.
- Le guide dell'iPhone per iOS 26: [File](https://support.apple.com/guide/iphone/find-and-view-files-and-folders-iphe4bff8827/26/ios/26),
  [Anteprima](https://support.apple.com/guide/iphone/open-and-view-pdfs-and-images-iph7a0cdad0c/26/ios/26),
  [ZIP](https://support.apple.com/102532).
- La ricerca F6C.0.

**Fiducia.**
- **alta**: documentato, oppure visto in un fermo;
- **media**: visto in iOS 26 su un solo caso;
- **bassa**: da versioni precedenti o da descrizioni di terzi. Va ripreso su un iPhone vero prima di copiarlo.

**La grammatica comune di Quick Look in iOS 26** (alta, dai fermi [ql-pdf-pagina.png](../../../artifacts/liquid-glass/ricerca/visori-2/apple/ql-pdf-pagina.png), [ql-pdf-menu.png](../../../artifacts/liquid-glass/ricerca/visori-2/apple/ql-pdf-menu.png), [ql-immagine-barre.jpg](../../../artifacts/liquid-glass/ricerca/visori-2/apple/ql-immagine-barre.jpg)):
- **in alto**: un cerchio di vetro a sinistra (l'indice del documento, ☰); al centro il **nome senza estensione con
  «⌄»**, che apre il menu del file; un cerchio «✕» a destra. Non c'è un contatore nel titolo;
- **sotto la barra**, solo nei documenti a pagine: una pillola di vetro «▯ 1 of 1». Il simbolo della barra laterale apre
  le miniature;
- **in basso**: «•••» a sinistra, che si trasforma nel suo menu (Cerca, Compilazione moduli, Annotazioni);
  un'**azione principale in capsula** al centro («↗ Preview», cioè apri nell'app); condividi a destra. Sulle immagini
  in basso ci sono l'annotazione (a sinistra) e una capsula con ricerca visiva e condividi (a destra);
- **materiali**: tutta la chrome è vetro, cerchi da 44 punti e capsule. Il contenuto non è mai dentro un vetro;
- **il fondo**:
  - per i documenti è bianco, anche durante l'apertura;
  - le **immagini si aprono a barre ritirate su fondo nero**; un tocco riporta le barre e il fondo diventa bianco;
  - lo stesso per i RAW ([ql-raw-cr3.jpg](../../../artifacts/liquid-glass/ricerca/visori-2/apple/ql-raw-cr3.jpg)):
    mentre decodifica, la pagina resta bianca con le barre.

Poi i tipi. Per ognuno ci sono cinque voci: impaginazione, testo, materiali, azioni, riempimento.

### 2.1 Audio (bassa)
- **Impaginazione**: un lettore al centro del fondo, con la copertina se c'è, altrimenti il simbolo della nota grande;
  sotto, la barra del tempo e play.
- **Testo**: il nome del file nella barra; titolo e artista solo se il file ha i tag.
- **Materiali**: la chrome di sempre; i comandi del lettore sono quelli di AVKit.
- **Azioni**: play, pausa, scorrimento, condividi.
- **Riempimento**: la copertina o il simbolo grande. Non abbiamo un fermo di iOS 26 e va ripreso.

Il riferimento più ricco resta il lettore di Musica (F6B.0 §7): copertina grande, titolo in grassetto, barra del tempo,
tre tasti grandi, uscita audio. È quello che seguono le tavole.

### 2.2 Documento a pagine (PDF) (alta)
- **Impaginazione**: pagine in colonna a tutta larghezza, con poco margine, sul fondo bianco. In Anteprima toccare il
  numero di pagine apre le miniature, e allargare due dita su una pagina le nasconde (guida iOS 26).
- **Testo**: nome con «⌄»; «1 of 1» nella pillola.
- **Materiali**: le pagine sono opache, senza bordo visibile sul bianco; la chrome è vetro.
- **Azioni**: ☰ indice (se c'è); «•••» con Cerca (parola intera, maiuscole), Compilazione moduli e Annotazioni; Apri
  in Anteprima; condividi; Vai alla pagina (in Anteprima).
- **Riempimento**: le pagine riempiono sempre. Una pagina sola sta in alto, con il bianco sotto
  ([ql-pdf-pagina.png](../../../artifacts/liquid-glass/ricerca/visori-2/apple/ql-pdf-pagina.png)).

### 2.3 Testo e codice (media per il tipo, bassa per l'aspetto)
- **Impaginazione**: il testo semplice e il codice (`public.text`) occupano tutta la pagina, con margini e a capo
  automatico, senza numeri di riga né colori. JSON, Markdown e codice sono testo semplice.
- **Testo**: un carattere solo, piccolo; niente gerarchia.
- **Materiali**: fondo bianco, chrome di vetro.
- **Azioni**: Cerca dal «•••», condividi, «Apri con» dal menu del nome.
- **Riempimento**: niente. Un file corto lascia la pagina vuota sotto.

Qui il visore Fluid può già fare di più di Apple: numeri di riga, colori e «Trova» ci sono da F6C.2.

### 2.4 Sconosciuto ed eseguibile (media)
- **Impaginazione**: icona grande del tipo al centro, sotto il nome e la riga «Tipo · dimensione».
- **Testo**: nome in grassetto, tipo e dimensione in grigio.
- **Materiali**: fondo del tema, chrome di vetro.
- **Azioni**: condividi; «Apri con» dal menu del nome. iOS non ha un avviso per gli eseguibili, perché non li installa.
- **Riempimento**: solo l'icona; il resto è vuoto.

Il visore Fluid deve fare di più, per scelta del piano: «mai un vicolo cieco», l'avviso, i dati del pacchetto.

### 2.5 Office e iWork (media per il tipo, bassa per l'aspetto)

Quick Look legge Word, Excel, PowerPoint, Pages, Numbers e Keynote (documentato).

| | Impaginazione | Testo | Materiali | Azioni | Riempimento |
|---|---|---|---|---|---|
| **Word, Pages** | fogli bianchi uno sotto l'altro con uno spazio fra loro, zoomabili come il PDF | gli stili del documento; i caratteri mancanti sostituiti | carta opaca su fondo grigio chiaro; chrome di vetro | Cerca, condividi, «Apri con», pillola delle pagine | i fogli |
| **Excel, Numbers** | un foglio alla volta in griglia, con le schede dei fogli (in basso in Quick Look); Numbers mostra le sue tabelle e i grafici | intestazioni e celle come nel file | griglia opaca; schede in una barra | scorrere i fogli, Cerca, condividi | la griglia; i fogli piccoli lasciano bianco attorno |
| **PowerPoint, Keynote** | le slide in colonna, una dopo l'altra, a tutta larghezza | quello della slide | slide opache su fondo neutro | condividi, «Apri con», a volte la riproduzione | le slide |

La scelta di Alessio, «pagine come il documento», è proprio questa grammatica.

### 2.6 Archivio (alta)
- **File** non mostra l'interno: toccare uno ZIP **crea accanto una cartella** con il contenuto, che si apre come le
  altre ([guida Apple](https://support.apple.com/102532)). La guida parla solo di ZIP; per 7z, RAR e TAR non dice niente.
- **Quick Look** non ha un'anteprima degli archivi: mostrerebbe la scheda dello sconosciuto (media).
- **Riempimento**: la lista di File, con miniature, nomi, data e dimensione.

Le tavole riprendono la lista di File (A), oppure la arricchiscono con filtri, percorso e compressione (B), o con le
cartelle come tessere (C).

### 2.7 Markdown, HTML, CSV, JSON, EPUB, EML, ICS, VCF

| Tipo | Cosa fa iOS 26 | Fiducia |
|---|---|---|
| Markdown | testo semplice (è `public.plain-text`), non reso | media |
| HTML | reso da WebKit, senza script | media |
| CSV | una tabella (è nella lista documentata) | alta per il tipo, bassa per l'aspetto |
| JSON | testo semplice | media |
| EPUB | non è nella lista: File propone Libri; Quick Look mostra la scheda dello sconosciuto | bassa |
| EML | Mail lo apre come un messaggio: intestazioni, corpo, allegati | bassa |
| ICS | una scheda dell'evento con «Aggiungi al calendario» (anteprima di Calendario, non Quick Look) | bassa |
| VCF | una scheda del contatto con «Crea nuovo contatto» | media |

Dove Apple mostra solo il testo (Markdown, JSON), il piano chiede di più: Markdown reso, JSON ad albero, CSV a tabella.

### 2.8 HEIC, RAW, SVG, PSD
- **HEIC**: un'immagine come le altre (alta).
- **RAW**: si sfoglia in Quick Look, con lo sviluppo di Apple. Nella ripresa i CR3 di una Canon si vedono dopo un breve
  bianco ([ql-raw-cr3.jpg](../../../artifacts/liquid-glass/ricerca/visori-2/apple/ql-raw-cr3.jpg); media).
- **SVG**: disegnato da WebKit (bassa).
- **PSD**: il composito, con ImageIO (bassa).
- Per tutti: fondo nero a barre ritirate, bianco con le barre, come le foto.

### 2.9 Font (bassa)
Su iOS i font si installano da un'app ([guida](https://support.apple.com/guide/iphone/install-and-manage-fonts-iphb2517689c/26/ios/26)).
Sul Mac Quick Look mostra l'alfabeto in grande; su iPhone non abbiamo un fermo. Le tavole non disegnano i font: la pagina
proposta per F6C.14 è in §5.2.

### 2.10 USDZ e modelli 3D (alta per USDZ)
- **USDZ** si apre in AR Quick Look, con le due viste dette dalla documentazione:
  - **Oggetto**: il modello su un fondo chiaro, con il **piano e l'ombra a terra messi da Quick Look** (WWDC23 10274);
  - **AR**: nella stanza.
- Un selettore «AR | Oggetto» sta in alto, con chiudi e condividi. Un dito ruota, due dita scalano e spostano.
- GLB, OBJ e STL non sono nella lista: su iOS restano sconosciuti (media).

## 3. L'apertura dalla miniatura

Due riprese pubbliche di iOS 26 su iPhone, a tempo vero. I narratori parlano sopra, e niente nelle trascrizioni
dichiara un rallentamento.
- **PDF da File** ([MacMost](https://www.youtube.com/watch?v=KzcjEnAFFYY), 4:30, 30 fps,
  [strip](../../../artifacts/liquid-glass/ricerca/visori-2/apple/ql-pdf-apre-strip.jpg),
  [clip](../../../artifacts/liquid-glass/ricerca/visori-2/apple/ql-pdf-apre-chiude.mp4),
  [4×](../../../artifacts/liquid-glass/ricerca/visori-2/apple/ql-pdf-apre-chiude-lento.mp4)).
- **Foto HEIC da File** ([jargonfreehelp](https://www.youtube.com/watch?v=RfNIua472i4), 1:37, 50 fps,
  [strip](../../../artifacts/liquid-glass/ricerca/visori-2/apple/ql-foto-apre-strip.jpg),
  [clip](../../../artifacts/liquid-glass/ricerca/visori-2/apple/ql-foto-apre-chiude.mp4)).

| Fase | PDF (fondo bianco) | Foto (fondo nero) | Fiducia |
|---|---|---|---|
| la sorgente si svuota | la riga resta, la sua icona sparisce al primo fotogramma | la miniatura della riga sparisce | media |
| **la forma che cresce** | la pagina compare già al 60–70% della larghezza finale, mezza trasparente, vicino alla riga; in ~130 ms è quasi a posto e opaca; poi si assesta di qualche punto verso l'alto fino a ~270 ms | l'immagine compare grande e trasparente, cresce e diventa opaca in ~200 ms | media sulla durata, bassa sulla curva (passo di 33 ms, possibili fotogrammi doppi) |
| **quando arriva il fondo** | insieme alla forma: il bianco comincia a coprire la lista nel primo fotogramma di movimento, e il contrasto del testo sotto scende al 76% a +67 ms e al 21% a +133 ms | insieme alla forma: il nero sale dal primo fotogramma di movimento, all'80% a +100 ms (metà strada), stabile a ~+240 ms | media; [letture](../../../artifacts/liquid-glass/ricerca/visori-2/apple/misure-fondo.txt) con lo zero 60 ms (PDF) e 40 ms (foto) prima dello svuotamento; le curve fini sono di F6D.0 |
| **la pagina sotto** | non si scurisce: la copre il fondo bianco che sale (in chiaro) | si scurisce fino al nero | media |
| **le barre** | la barra di File **si trasforma sul posto** in quella di Quick Look (‹ → ☰, «Examples» → «opm71», ••• → ✕, in dissolvenza incrociata), la barra in basso sfuma nella capsula di Quick Look; tutto nei primi ~100 ms | ritirate (fondo nero) | media |
| **chiusura con ✕** | il contenuto sparisce al primo fotogramma; il bianco cala, la barra torna quella di File e una pagina piccola e trasparente rientra nella riga; ~200 ms ([strip](../../../artifacts/liquid-glass/ricerca/visori-2/apple/ql-pdf-chiude-strip.jpg)) | l'immagine si rimpicciolisce verso la riga mentre la lista torna chiara; ~300 ms ([strip](../../../artifacts/liquid-glass/ricerca/visori-2/apple/ql-foto-chiude-strip.jpg)) | media |
| **chiusura col dito** | non ripresa in queste fonti; è documentata per Foto (F6C.0) | — | — |

Le durate sono misurate dal primo fotogramma in cui la sorgente si svuota (≈ il tocco) all'ultimo che cambia. Gli
attrezzi di `riferimenti/tools/` leggono solo i video WWDC di `riferimenti/video/`. Per queste due riprese ho usato due
script della sessione che fanno lo stesso lavoro:
- `striscia.py`, i fogli di fotogrammi, come `sheet.py`;
- `misura.py`, la luminanza in un rettangolo, come `probe.py`.

Il metodo è nel [LEGGIMI](../../../artifacts/liquid-glass/ricerca/visori-2/LEGGIMI.md). La forma della curva e i valori
fini del velo **non** li misuro io: li fa F6D.0.

**Cosa ne viene per F6C.10.**
- **Il ritmo.** Quick Look apre in **~0,25 s**, più in fretta della molla di 0,355 s. Il fondo arriva **insieme** alla
  forma, non alla fine.
- **La scelta di Alessio del 22 novembre** dice: «la miniatura cresce fino alla pagina, il fondo arriva verso la fine,
  la pagina sotto si scurisce (F6D)». Non si ridiscute.
- **Come stanno insieme le due cose.** Lo scurimento della pagina sotto (F6D) parte con la forma e la segue. Il fondo
  pieno del visore arriva nell'ultimo tratto. Così nessun fotogramma mostra due pagine leggibili una dentro l'altra (R7).
  La [strip della proposta](../../../artifacts/liquid-glass/ricerca/visori-2/tavole/apertura-pdf-chiaro.png) è fatta
  così.
- **La forma.** La miniatura di un documento è la sua prima pagina, quindi la forma che cresce **è già la pagina**:
  niente cambio d'identità.
  - Se la miniatura è un'icona (un tipo senza anteprima), l'icona entra sfocata nella pagina (R7), come fa Quick Look,
    che parte con la pagina già grande e trasparente.
  - Gli angoli passano dal raggio della miniatura a quello della pagina.
- **Le barre** del visore entrano sfocate e diventano nitide in 300–400 ms (R5 V5).
- **Il gesto delle barre di Quick Look**, che si trasformano sul posto, è il viaggio della barra di F6B.1 (sostituzione):
  F6C.10 può usarlo quando la pagina d'origine ha la stessa barra in alto.

![Apertura di un PDF dalla miniatura](../../../artifacts/liquid-glass/ricerca/visori-2/tavole/apertura-pdf-chiaro.png)

La strip della proposta: in alto l'apertura col tocco, otto fotogrammi da 0 a 450 ms, con la percentuale della forma,
del velo e del fondo; sotto la chiusura col dito, che segue il dito, supera i 110 dp e torna nella miniatura con la
stessa molla, partendo da dove il dito ha lasciato. C'è anche la [versione scura](../../../artifacts/liquid-glass/ricerca/visori-2/tavole/apertura-pdf-scuro.png).

## 4. I moduli e il registro

### 4.1 I nomi definitivi

Restano quelli proposti nel piano: sono chiari, e ognuno è una famiglia di Alessio.

| Modulo | Pacchetto Kotlin | Contiene | Dipende da |
|---|---|---|---|
| `engine-viewer` | `dev.antigravity.fluidengine.viewer` | quello di oggi più il **registro** (`FluidViewerRegistry`, `FluidViewerFamily`, `FluidViewerSource`) | `engine-ui`; Media3 `compileOnly` |
| `engine-viewer-office` | `…viewer.office` | DOCX, ODT, RTF a pagine; XLSX, ODS in griglia; PPTX, ODP a slide; anteprima di Pages, Numbers e Keynote | `engine-viewer` |
| `engine-viewer-archive` | `…viewer.archive` | ZIP, 7z, RAR, TAR (anche .tgz, .tar.bz2, .tar.xz, .tar.zst), .gz, .bz2, .xz da soli | `engine-viewer`, libarchive-android |
| `engine-viewer-data` | `…viewer.data` | Markdown, HTML, CSV, TSV, JSON, NDJSON, XML, plist, EPUB, EML, ICS, VCF | `engine-viewer` |
| `engine-viewer-graphics` | `…viewer.graphics` | HEIC e AVIF (con il ripiego per le versioni vecchie), RAW, TIFF, PSD, SVG; TTF, OTF, TTC, WOFF | `engine-viewer`, AndroidSVG, exifinterface |
| `engine-viewer-3d` | `…viewer.scene` | STL, OBJ, GLB, glTF; USDZ resta sconosciuto | `engine-viewer` (più Filament, se D2 lo sceglie) |
| `engine-viewer-all` | — | niente codice: `api` verso i cinque moduli | i cinque |

Il 3D sta in un modulo suo anche con il renderer proprio: costa pochissimo, e la scelta Filament / proprio resta
reversibile senza toccare `graphics`.

### 4.2 Il registro: un modulo aggiunge i suoi tipi senza toccare il risolutore

```kotlin
package dev.antigravity.fluidengine.viewer

/** Una famiglia di tipi in un modulo facoltativo. Si registra da sola: basta la dipendenza Gradle. */
interface FluidViewerFamily {
  /** «office», «archive», «data», «graphics», «3d»: compare nel pannello info. */
  val id: String
  /** I tipi che sa aprire, ognuno con le sue regole di riconoscimento. */
  val types: List<FluidViewerFamilyType>
  /** Apre il file. `null`, oppure una FluidViewerException, vuol dire «non questo»: il registro prova il prossimo. */
  suspend fun open(source: FluidViewerSource, type: FluidViewerFamilyType, options: FluidViewerOptions): FluidViewerOpened?
  /** La miniatura per la griglia dell'app e per l'apertura (§3). `null` = l'icona del tipo. */
  suspend fun thumbnail(source: FluidViewerSource, type: FluidViewerFamilyType, sizePx: Int): ImageBitmap? = null
}

data class FluidViewerFamilyType(
  val id: String,                                  // "docx"
  val label: String,                               // "Documento Word"
  val extensions: Set<String> = emptySet(),        // "docx", "docm"
  val mimeTypes: Set<String> = emptySet(),
  val signature: FluidViewerSignature? = null,     // primi byte, o una voce del pacchetto ZIP ("word/document.xml")
  val kind: FluidFileKind = FluidFileKind.Document,// icona e avviso, come oggi
  val priority: Int = 0,                           // a parità di riconoscimento vince la più alta
)

/** Il file da aprire: un Uri, una voce di archivio, un allegato. Sempre con un modo di leggerlo dall'inizio e,
 *  se serve (ZIP, PdfRenderer), una copia ricercabile in cache. */
interface FluidViewerSource {
  val name: String; val mimeType: String?; val sizeBytes: Long?
  suspend fun open(): InputStream
  suspend fun seekable(): ParcelFileDescriptor   // copia in cache se l'Uri non è ricercabile
}

class FluidViewerOpened(
  val content: FluidViewerContent,                  // Pages, Grid, Text, Image, Custom…
  val details: List<FluidViewerDetail> = emptyList(), // per il pannello info: «Pagine 9», «Calibri → Inter»…
  val close: () -> Unit = {},
)

object FluidViewerRegistry {
  /** Le famiglie trovate con ServiceLoader (META-INF/services), più quelle installate a mano. */
  val families: List<FluidViewerFamily>
  fun install(vararg family: FluidViewerFamily)      // per i test e per chi vuole scegliere a mano
}
```

**Come si trova un modulo.** Ogni modulo dichiara la sua famiglia in
`META-INF/services/dev.antigravity.fluidengine.viewer.FluidViewerFamily`, e il registro la carica la prima volta che
risolve, con `ServiceLoader.load(FluidViewerFamily::class.java, FluidViewerFamily::class.java.classLoader)`.
- R8 riconosce proprio questa forma: istanzia direttamente le classi e le tiene, senza regole a mano. Va verificato con
  una build `release` in F6C.8.
- Niente App Startup, niente codice nell'`Application` dell'app: chi aggiunge la dipendenza ha il tipo, chi non la
  aggiunge non ha niente.

**Come si decide** (`FluidViewerType.of`, esteso; `FluidViewerFile.open`, esteso):
1. **Un eseguibile resta sconosciuto**, sempre, prima di chiedere alle famiglie: la regola di F6C.4 non cambia.
2. **Si raccolgono i candidati**: i tipi delle famiglie e i preset di serie, con la forza del riconoscimento. La firma
   dei primi byte o della voce ZIP vale più dell'estensione, che vale più del MIME. Così un `.zip` che contiene
   `word/document.xml` è un Word, e un `.docx` rotto si scopre ZIP.
3. **Si ordinano** per forza, poi per `priority`.
   - Un tipo di famiglia **batte la tabella «noti ma senza decodificatore»** di oggi (docx, zip, svg… → sconosciuto).
   - I preset di serie che aprono già un tipo (HEIC con `ImageDecoder`) restano davanti, con priorità 0; la famiglia
     `graphics` dichiara il suo HEIC con priorità −10, così parte solo se la piattaforma fallisce, cioè sotto Android 9.
4. **Si prova in ordine.** Se uno `open` dà `null` o una `FluidViewerException`, si passa al prossimo; la prima
   eccezione si tiene come motivo.
5. **Il ripiego** è lo sconosciuto con il motivo e «Apri con…», come oggi. Il tipo che nessun modulo conosce resta
   sconosciuto: «chi non aggiunge un modulo non ne paga il peso».

**Annidato.** L'archivio, l'EML e l'EPUB aprono le loro voci con `FluidViewerRegistry` (una `FluidViewerSource` sulla
voce), quindi un PDF dentro uno ZIP usa il preset PDF, un DOCX allegato a una mail usa Office se c'è.

**I campi nuovi delle classi pubbliche** vanno in coda con un valore di serie (regole 3.11): per esempio
`FluidViewerType(…, family: String? = null, label: String? = null)`. `FluidViewerPreset` è una classe valore:
`Grid` e `Custom` sono costanti in più, non un enum che si rompe.

### 4.3 Le due forme di contenuto nuove in engine-ui

Il telaio oggi sa disegnare `Image`, `Media`, `Pages`, `Text` e `Unknown`; un'altra classe che implementa
`FluidViewerContent` resterebbe **vuota** (`ViewerBody` è un `when` senza `else`). Servono due forme, senza
decodificatori:
- **`Grid(source: FluidGridSource)`**: fogli, dimensioni, righe e colonne bloccate, larghezze, celle a blocchi
  (testo, allineamento, stile, unioni). Serve a Office (XLSX, ODS) e a dati (CSV, TSV): una griglia sola, disegnata come
  nelle tavole.
- **`Custom(background, find, actions, draw)`**: il modulo disegna la sua pagina con i componenti pubblici dell'engine
  (`FluidListGroup`, vetro, chip). La pagina dichiara:
  - lo sfondo: del tema o nero, per la scelta del 21 novembre;
  - se ha «Trova»;
  - le azioni della barra, per esempio «Estrai tutto», «Albero | Testo», «Presenta».

  Archivio, albero JSON, EPUB, EML, ICS, VCF, font e 3D usano questa.

**Perimetro.** Queste due forme toccano `ui/fluid/viewer/FluidViewerContent.kt`, un preset nuovo
`FluidGridPreset.kt` e una riga di `ViewerBody` in `FluidFileViewer.kt`. Nessuno dei pacchetti della seconda ondata ha
questi file nel perimetro: F6C.8 ha `engine-viewer/**`, F6C.9 ha i preset e la chrome, F6C.10 ha
`FluidFileViewer.kt`. **Propongo di darli a F6C.8**, con la riga di `ViewerBody` da unire con F6C.10. Lo decide
l'orchestratore.

### 4.4 La build lo controlla (F6C-h)

- **`checkViewerFamilies`**, nel lotto di `engine-viewer`, legge i classpath risolti e controlla tre cose:
  - `engine-ui` non arriva a nessuna famiglia né a una libreria di decodifica (F6C-d di oggi, esteso);
  - `engine-viewer` non arriva a nessuna famiglia;
  - ogni famiglia porta solo le librerie della sua **lista ammessa**, scritta con licenza e peso. Una libreria nuova
    fuori lista ferma la build.
- **Il `sample` ha una variante senza famiglie**, solo `engine-viewer`: un test verifica che un DOCX vada allo
  sconosciuto con «Apri con…», e che nel dex non ci siano classi `…viewer.office`.
- `engine-install.ps1` elenca i moduli con il peso e la riga da incollare, e propone `engine-viewer-all` per il visore
  universale.

## 5. Le direzioni d'aspetto

Tre direzioni, ognuna sulle **stesse dieci pagine**:
- audio;
- documento a pagine;
- testo e codice;
- sconosciuto ed eseguibile;
- pannello info;
- pagina Word;
- foglio;
- slide;
- archivio;
- JSON.

Ogni direzione è in chiaro e in scuro. Lo stile segue le schede della skill:
- Inter Text e Display;
- la rampa 34, 28, 22, 20, 17, 15, 13 e 11;
- i raggi 10, 12, 18, 22 e 38 e le capsule;
- le superfici F2F2F7/FFFFFF e 0D0D0F/1B1B1E;
- l'accento del marchio 1F9E6E/2ED8A0;
- il vetro solo sulla chrome e sui pannelli, mai su una riga.

Le tavole sono HTML reso con Edge; il vetro è un `backdrop-filter`, un'illustrazione e non la ricetta dell'engine.

**Lo sfondo delle tavole e le scelte.** In tutte e tre le pagine di documento e di testo hanno il fondo del tema (scelta
del 21 novembre), e la carta dei documenti Office resta bianca anche in scuro, come in Quick Look. Il visore resta una
rotta con la chrome che si ritira al tocco (F6C.1).

### A · Fedele
**Le pagine di Quick Look di iOS 26 con il vetro dell'engine: il contenuto da solo, la chrome in cerchi e capsule.
Quando il contenuto è piccolo lo riempiono la copertina o l'icona grande; i dettagli stanno nel pannello info.**

- [Tavola in chiaro](../../../artifacts/liquid-glass/ricerca/visori-2/tavole/a-fedele-chiaro.png)
- [Tavola in scuro](../../../artifacts/liquid-glass/ricerca/visori-2/tavole/a-fedele-scuro.png)
- [Le due insieme](../../../artifacts/liquid-glass/ricerca/visori-2/tavole/a-fedele.png)

![A · Fedele, chiaro](../../../artifacts/liquid-glass/ricerca/visori-2/tavole/a-fedele-chiaro.png)

### B · Ispettore
**Ogni pagina è anche una scheda tecnica: una riga di fatti sotto la barra, e attorno al contenuto le sezioni dello
strumento (capitoli, struttura, firma, filtri, la cella o il nodo scelto). Densa, da lavoro.**

- [Tavola in chiaro](../../../artifacts/liquid-glass/ricerca/visori-2/tavole/b-ispettore-chiaro.png)
- [Tavola in scuro](../../../artifacts/liquid-glass/ricerca/visori-2/tavole/b-ispettore-scuro.png)
- [Le due insieme](../../../artifacts/liquid-glass/ricerca/visori-2/tavole/b-ispettore.png)

![B · Ispettore, chiaro](../../../artifacts/liquid-glass/ricerca/visori-2/tavole/b-ispettore-chiaro.png)

### C · Atmosfera
**Il tipo del file dà il colore: un velo tenue del suo colore attorno, pannelli di vetro grandi, striscia di miniature
in vetro. Dove si legge (pagine, codice, griglia) la superficie resta piena e pulita.**

Attenzione: il velo colora anche il fondo attorno alla pagina dei documenti. La superficie su cui si legge resta del
tema, ma è un'interpretazione della scelta «sfondo del tema per documenti e testo»; se Alessio sceglie C, va confermata.

- [Tavola in chiaro](../../../artifacts/liquid-glass/ricerca/visori-2/tavole/c-atmosfera-chiaro.png)
- [Tavola in scuro](../../../artifacts/liquid-glass/ricerca/visori-2/tavole/c-atmosfera-scuro.png)
- [Le due insieme](../../../artifacts/liquid-glass/ricerca/visori-2/tavole/c-atmosfera.png)

![C · Atmosfera, chiaro](../../../artifacts/liquid-glass/ricerca/visori-2/tavole/c-atmosfera-chiaro.png)

### Il confronto
Le tre direzioni sulle stesse cinque pagine (audio, eseguibile, Word, foglio, archivio):
[in chiaro](../../../artifacts/liquid-glass/ricerca/visori-2/tavole/confronto-chiaro.png) ·
[in scuro](../../../artifacts/liquid-glass/ricerca/visori-2/tavole/confronto-scuro.png).

![Confronto](../../../artifacts/liquid-glass/ricerca/visori-2/tavole/confronto-chiaro.png)

### 5.1 Cosa riempie lo spazio, pagina per pagina

Contro il «troppo spoglio» delle riprese di F6C.5:

| Pagina | Oggi (F6C.5) | A | B | C |
|---|---|---|---|---|
| Audio | onda grigia e Play in mezzo al vuoto; durata «—» prima di Play | copertina 280 dp (o generata dall'onda), titolo e artista, tempo con il formato, ±15 s, velocità, capitoli | onda grande con i capitoli e il tempo con i decimi, capitoli e tag sotto | la copertina colora la pagina; lettore in un pannello di vetro; capitolo corrente |
| Documento | pagina e «1/6» | colonna a tutta larghezza, pillola «2 di 6» che apre le miniature | riga dei fatti (pagine, formato, versione, ricercabile), colonna di miniature accanto | pagina che galleggia sull'alone, striscia di miniature in vetro |
| Testo | monospazio piccolo sul bianco | righe numerate e colori, carattere e a capo in basso | codifica, fine riga e righe in vista; «Trova» con le occorrenze sulla minimappa | codice su una superficie piena dentro l'alone della lingua |
| Sconosciuto ed eseguibile | icona piccola, una riga, due tasti | icona vera dell'app, nome, versione e pacchetto, avviso, tasti | carta d'identità: ID, versione, Android, firma, permessi, byte iniziali | icona grande con alone, avviso in vetro ambra |
| Pannello info | elenco di coppie su vetro scuro | foglio a metà con la miniatura e le righe, come Anteprima | foglio grande a schede, con impronta e tempi di lettura | foglio con la copertina del file in testa |

Il ripiego per un audio senza copertina: in A la copertina si genera dall'onda e dal colore dell'accento, in C l'alone
usa l'accento. Per un eseguibile che non è un APK (`.exe`, `.sh`) resta l'icona del tipo, senza la sezione «App».

La durata prima di Play (uno dei due difetti di F6C.9) va letta all'apertura dai metadati, con
`MediaMetadataRetriever` o con il player preparato senza partire.

### 5.2 Le pagine dei tipi nuovi che le tavole non mostrano

Seguono la direzione scelta:
- **Markdown** reso con «Reso | Sorgente»;
- **HTML** chiuso, con «Carica immagini»;
- **CSV** come il foglio, senza schede;
- **EPUB** con l'indice in un foglio e «Capitolo 3 di 12 · 24%»;
- **EML** con la scheda delle intestazioni, il corpo e gli allegati come righe dell'archivio;
- **ICS** e **VCF** come schede (evento con la tessera della data, contatto con la foto) e l'azione dell'app;
- **font** con l'alfabeto e un testo di prova in grande, i pesi e gli assi variabili come cursori, i dati della tabella
  `name` nel pannello;
- **3D** con il modello su fondo del tema e ombra a terra, un dito che ruota, «Inquadra» e il pannello con vertici,
  triangoli e misure.

## 6. Lista d'accettazione per pacchetto

Per i viaggi d'oro (l'apertura dalla miniatura di F6C.10, il passaggio da un preset all'altro, la barra che si
sostituisce) vale il protocollo F1B:
- prima e dopo;
- tre ripetizioni a 120 Hz;
- chiaro e scuro, fondo semplice e carico;
- interruzioni al 25, 50 e 75%;
- back annullato e confermato.

Nessun fotogramma deve mostrare la miniatura e la pagina leggibili insieme. Il velo e la sua curva si confrontano con
F6D.0, non con i numeri di §3.

| Pacchetto | Accanto ad Apple / al riferimento | Condizioni di accettazione |
|---|---|---|
| **F6C.7** | §2 tipo per tipo con fonti e fiducia; §3 con due riprese di iOS 26. | Tavole A, B, C in chiaro e scuro, strip dell'apertura, tabella delle famiglie con licenza, peso e versione minima, registro e nomi dei moduli, questa lista. |
| **F6C.8 · Registro** | §4.2; F6C.4 per la precedenza degli eseguibili. | (1) Un modulo di prova in `src/test` dichiara un tipo `.prova` via `META-INF/services` e il risolutore lo trova senza toccare `engine-viewer`. (2) Ordine: firma > estensione > MIME, poi priorità; `.zip` con `word/document.xml` → Office; HEIC con priorità −10 parte solo se `ImageDecoder` fallisce. (3) Un `open` che dà `null` o un'eccezione passa al prossimo; senza candidati, lo sconosciuto col motivo. (4) Un eseguibile non arriva mai a una famiglia, anche con un MIME da documento. (5) `FluidViewerSource` per Uri non ricercabili copia in cache e chiude tutto anche dopo l'annullamento. (6) R8 in `release` tiene le famiglie (prova sull'APK). (7) `Grid` e `Custom` in engine-ui, se assegnate (§4.3), con il controllo «nessun decodificatore». (8) `checkViewerFamilies` e la lista ammessa; `engine-viewer-all`; `engine-install.ps1` con i pesi. (9) Compatibilità: campi in coda, test di oggi verdi. |
| **F6C.9 · Preset rifatti** | La direzione scelta (§5) e Quick Look §2.1–2.4 per la grammatica. | Le cinque pagine (audio, documento, testo, sconosciuto ed eseguibile, pannello info) messe accanto alla tavola scelta, in chiaro e scuro, a 1,0 e 2,0 di scala del testo, su telefono e sul Tab S9. Gli stati da provare per ciascuna sono questi. Audio: con copertina, senza copertina, senza tag, durata ignota; **la durata compare prima di Play**. Documento: 1 pagina, 6 pagine, pagine miste. Testo: corto (riempimento), lungo, UTF-16. Eseguibile: APK con icona, APK senza icona, `.exe`. Info: tutte le sezioni. «Trova» con la tastiera: **l'occorrenza resta sotto la barra in alto e sopra la tastiera**, nel banco e sul tablet. TalkBack legge le sezioni nuove nell'ordine giusto. |
| **F6C.10 · Apertura dalla miniatura** | §3 (Quick Look ≈0,25 s, fondo insieme alla forma), la strip della proposta, F6D per il velo. | Per ogni preset non foto/video (pagine, testo, sconosciuto, Office, archivio, dati, font, 3D) l'apertura parte dalla miniatura dell'app, o dall'icona se la miniatura manca, e la chiusura ci torna: col tasto, col back predittivo, col dito oltre 110 dp. Il ritorno parte da dove il dito ha lasciato. Traccia a 120 Hz per PDF, DOCX, ZIP, JSON: forma con la molla di 0,355 s, velo che segue la forma (curva di F6D), fondo nell'ultimo tratto, barre che entrano sfocate e sono nitide in 300–400 ms. Icona → pagina entra sfocata (R7). Rivelatore di scatti pulito; nessun fotogramma con due pagine leggibili. Sorgente sparita: dissolvenza. |
| **F6C.11 · Office** | Quick Look §2.5; il documento aperto in Word, LibreOffice o Pages come verità. | Un corpus di prova di almeno 12 file (A4 e Letter, verticale e orizzontale; titoli, elenchi su 3 livelli, tabelle con unioni e righe spezzate, immagini, intestazioni e piè diversi in prima pagina, note, RTF con tabella e immagine, XLSX con date, valute, formule, righe bloccate, 3 fogli, ODS, PPTX con master e layout, SmartArt e note, ODP). Per ogni file: le pagine accanto al PDF esportato dall'app d'origine, con **lo stesso numero di pagine** (±1 con i caratteri di sistema), titoli e tabelle riconoscibili, a capo confrontato riga per riga su due pagine. XLSX: valori e formati uguali alla cella; schede dei fogli. PPTX: slide accanto all'esportazione. Il pannello info elenca caratteri sostituiti e parti omesse. File grandi (300 pagine, 100.000 celle): prima pagina in meno di 1 s sul Tab S9, memoria sotto un tetto dichiarato. Danneggiato o protetto: lo sconosciuto con il motivo. Peso del modulo misurato. |
| **F6C.12 · Archivi** | File §2.6 (la cartella accanto); la lista di File per la composizione. | ZIP, 7z, RAR 4, RAR 5, TAR, .tgz, .tar.xz, .gz da solo: elenco, cartelle annidate, percorso, ordinamento, filtri della direzione scelta. Una voce si apre col suo preset (PDF, immagine, DOCX se c'è Office) e il back torna nella cartella. Cifrati: ZIP AES e 7z chiedono la password (giusta e sbagliata); RAR cifrato dice «protetto» con «Apri con…». Danneggiato: l'elenco fin dove si legge e l'avviso. Sicurezza: zip slip, zip bomb, collegamenti simbolici, nomi in CP437 e Shift-JIS: prove dedicate. «Estrai tutto» è dell'app (callback), con l'avanzamento. Peso misurato per architettura. |
| **F6C.13 · Testi ricchi e dati** | §2.7; Quick Look per CSV; le app di riferimento (Libri, Mail, Calendario, Contatti) per le schede. | Markdown reso e sorgente; HTML con risorse relative e remote bloccate; CSV con `;` e virgolette, intestazione riconosciuta, 100.000 righe a flusso; JSON annidato, enorme (tetto e «mostra altri»), JSONC, NDJSON, non valido (riga e colonna dell'errore); XML e plist. EPUB 2 e 3, indice, immagini, DRM → protetto. EML con HTML, `cid:`, allegati che si aprono, charset vari. ICS con fusi, giornata intera, ripetizioni. VCF con più contatti e foto. Nessuna rete parte mai (test con il contenitore chiuso). |
| **F6C.14 · Immagini speciali, font, 3D** | §2.8–2.10; AR Quick Look (piano, ombra, gesti). | HEIC e AVIF sul Tab S9 e il ripiego sotto API 28 e 31 (Robolectric). RAW: DNG, CR2, CR3, NEF, ARW con l'anteprima incorporata e l'EXIF; il pannello dice «sviluppo della fotocamera». TIFF di più pagine con contatore; PSD composito e il ripiego alla miniatura; SVG nitido a 4×, SVGZ. Font TTF, OTF, TTC, variabile (assi), WOFF; WOFF2 → sconosciuto con il motivo. 3D: STL binario e ASCII, OBJ+MTL, GLB con texture; orbita, pizzico, doppio tocco; ombra a terra; vetro sopra (TextureView). Un modello da 1 milione di triangoli a 60 fps o il tetto dichiarato. Pesi misurati dei due moduli. |
| **F6C.15 · Galleria rifatta** | Le scene di §2 e §3 accanto alle riprese Fluid; la tavola scelta. | La galleria «Visori» da F6C.5 con ogni famiglia: un file per tipo dei corpi di prova di F6C.11–14, tutti aperti dalla loro miniatura. Una scena «Senza moduli» (solo `engine-viewer`) mostra gli stessi file come sconosciuti con «Apri con…». Una pagina «Pesi» elenca il peso misurato di ogni modulo per architettura. Tracce nuove: `visore-apre-pdf`, `visore-apre-docx`, `visore-apre-zip`, `visore-chiude-dito-docx`, `office-pagine`, `archivio-annidato`, con collage e fonte. Universal Converter in sola lettura con `engine-viewer-all`: i tipi delle sue prove (DOCX, ZIP, MD, JSON, SVG…) ognuno col suo preset. Schede `componenti/visori.md` e `moduli/viewer*.md` con i moduli, i pesi e le licenze (anche in `fluidLicensesSection`). |

### Decisioni da portare nel piano
- I nomi dei moduli restano quelli del piano (§4.1). Il 3D sta in un modulo suo anche con il renderer proprio.
- Il registro scopre le famiglie con `ServiceLoader`: niente App Startup.
- `Grid` e `Custom` entrano in engine-ui: serve dire chi le scrive (§4.3; proposta F6C.8).
- Il «fondo verso la fine» della scelta del 22 novembre convive con Quick Look, dove il fondo arriva subito: il velo
  della pagina sotto parte con la forma, il fondo pieno del visore arriva alla fine (§3).
- I due difetti di F6C.5 hanno una prova nella lista di F6C.9: la durata prima di Play, e l'occorrenza di «Trova» fra la
  barra e la tastiera.

## 7. Le domande per Alessio

**D1 · Che aspetto hanno le pagine dei visori?** (le tre tavole e il confronto, §5)
- **A · Fedele**: come Quick Look di iOS 26, con il contenuto da solo e i dettagli nel pannello info; la più vicina ad
  Apple e la più quieta.
- **B · Ispettore** (proposta): ogni pagina è anche una scheda tecnica, con una riga di fatti, struttura, capitoli,
  firma, filtri; la più «strumento profondo».
- **C · Atmosfera**: il colore del file attorno e pannelli di vetro grandi; la più espressiva. Colora il fondo attorno
  ai documenti, e la scelta del 21 novembre va riconfermata.

**D2 · Con che motore si vedono i modelli 3D?** (§1.5)
- **Renderer proprio** (proposta): GLES 3 per STL, OBJ e GLB statici, luce fissa e ombra a terra; ≈0,1 MB, ma niente
  PBR piena né animazioni.
- **Filament**: i GLB con materiali e luce come AR Quick Look; 6,5 MB non compressi per arm64 (3,1 MB scaricati).

**D3 · Come si leggono gli archivi?** (§1.2)
- **libarchive** (proposta): ZIP, 7z, RAR 4 e 5, TAR con una libreria sola, licenze BSD e Apache; 2,35 MB non compressi
  per arm64; i RAR cifrati restano «protetti».
- **Solo Java**: ZIP e TAR propri, 7z con commons-compress; RAR solo come elenco (oppure junrar, con la licenza UnRAR);
  niente codice nativo, meno formati completi.

**D4 · Con quali caratteri si impagina Office?** (§1.1)
- **Caratteri di sistema** (proposta per la prima versione): zero byte; a capo e pagine un po' diversi da Word, detto
  nel pannello.
- **Caratteri con le stesse misure** (Carlito, Caladea, Liberation): pagine e a capo come in Word; +3–6 MB al modulo
  Office.
