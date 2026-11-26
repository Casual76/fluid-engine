# La mappa dell'engine

Tutto quello che c'è nel repo, dove sta e da cosa dipende. Leggila quando devi decidere **in quale modulo
va una cosa**, **quali moduli installare in un'app**, o **dove andare a leggere il codice di un componente**.
I numeri sono dell'engine 3.0.4 (righe di Kotlin sotto `src/main`).

## I moduli, uno per uno

| modulo | pacchetto sotto `dev.antigravity.fluidengine` | file / righe | cosa porta | dipende da |
|---|---|---|---|---|
| `engine-foundation` | `foundation` | 6 / 495 | modelli, versioni, manifest remoto, flag, impostazioni — **niente Compose, niente Android UI** | (coroutines, serialization) |
| `engine-ui` | `ui` | 148 / 55 521 | il design system: tema, componenti, vetro, Fluid-physics, pagine, aptica, tutorial | foundation |
| `engine-storage` | `storage` | 1 / 128 | DataStore: impostazioni dell'engine e cache del manifest | foundation |
| `engine-net` | `net` | 4 / 760 | `EngineHttp`: leggi un documento, scarica un file; l'osservatore della rete (`EngineNetwork`, che non lampeggia: Wi-Fi senza internet, VPN, micro-cadute), le condizioni del telefono (`EngineConditions`: rete a consumo e roaming, risparmio dati ed energetico, calore, corrente) e le reti finte per le prove (`testing.FakeNetworks`) — F8.20, `moduli/net.md` | foundation |
| `engine-config` | `config` | 1 / 112 | `EngineRemoteConfig`: flag, versione minima, avvisi, kill switch | foundation, net, storage |
| `engine-update` | `update` | 2 / 489 | aggiornamento in-app con PackageInstaller | foundation, net |
| `engine-widget` | `widget` | 3 / 686 | palette e componenti Glance con lo stesso aspetto dell'app | foundation, **ui** |
| `engine-wear` | `wear` | 17 / 2 216 | il design system su Wear OS (opt-in) | foundation, **ui** |
| `engine-ai` | `ai` | 36 / 6 456 | assistente senza dominio: provider BYOK, chiavi, SSE, failover, orchestratore, voce | foundation, **vault** (dalla 4.0) |
| `engine-vault` | `vault` | 11 / 2 508 | la cassaforte: valori, file e DataStore cifrati col Keystore (StrongBox quando c'è), sezioni «solo telefono» o all'impronta che si richiudono da sole, la chiave diretta di `engine-ai` — **opt-in**, dalla 4.0 (F8.23, la scheda: `moduli/vault.md`) | foundation |
| `engine-security` | `security` | 7 / 1 720 | il blocco con l'impronta: l'app che si blocca al rientro, la schermata di vetro, il PIN dell'app, il contenuto protetto, l'anteprima oscurata nelle recenti, la riga delle impostazioni — **opt-in**, dalla 4.0 (F8.23, `moduli/security.md`) | **ui**, **vault** |
| `engine-ai-ui` | `aiui` | 28 / 6 846 | l'interfaccia dell'assistente: overlay, chat, impostazioni, halo (opt-in) | **ui**, **ai** |
| `engine-ai-bridge` | `ai.bridge` | 4 / 912 | tool federati fra app (ContentProvider che verifica la firma di chi chiama) | ai |
| `engine-richtext` | `richtext` | 9 / 3 997 | l'editor di testo con formattazione (elenchi e citazioni annidati, link, barra sopra la tastiera) e i lettori/scrittori di HTML ristretto, Markdown e testo semplice — **opt-in**, dalla 3.1.0 (la scheda: `moduli/richtext.md`) | foundation, **ui** |
| `engine-qr` | `qr` | 1 / 63 | il QR di «Mostra come QR» nel foglio di condivisione, con ZXing core (Apache-2.0); il contratto e il disegno stanno in `engine-ui` — **opt-in**, dalla 4.0 (F8.10, `moduli/qr.md`) | **ui** (zxing core) |
| `engine-share-in` | `sharein` | 3 / 916 | gli arrivi da altre app: `SEND`, `SEND_MULTIPLE`, `PROCESS_TEXT`, `VIEW` letti in un modo solo, la copia subito con tetto e avanzamento, il foglio sopra l'app di partenza, le scorciatoie di condivisione — **opt-in**, dalla 4.0 (F8.10, `moduli/share-in.md`) | **ui** |
| `engine-viewer` | `viewer` | 15 / 4 120 | i decodificatori dei visori di file: PDF (`PdfRenderer`), testo con charset e colori di sintassi, GIF e WebP animate, l'adattatore del `Player` di Media3 (`compileOnly`: la versione è dell'app), il risolutore dal MIME al preset e il **registro** dei moduli di famiglia — **opt-in**, dalla 4.0 (le schede: `moduli/viewer.md`, e il telaio in `componenti/visori.md`) | foundation, **ui** |
| `engine-viewer-office` | `viewer.office` | 11 / 3 211 | visori, famiglia Office: DOCX, ODT, RTF a pagine, XLSX e ODS in griglia, PPTX e ODP a slide, i caratteri con le misure di Word — **opt-in** (`moduli/viewer-famiglie.md`) | **viewer** |
| `engine-viewer-archive` | `viewer.archive` | 6 / 1 322 | visori, archivi con libarchive: ZIP, 7z, RAR, TAR e i compressi, ogni voce col suo preset — **opt-in** | **viewer** |
| `engine-viewer-data` | `viewer.data` | 25 / 6 174 | visori, testi ricchi e dati: Markdown, HTML, CSV, JSON, XML, plist, EPUB, EML, ICS, VCF, senza librerie — **opt-in** | **viewer** |
| `engine-viewer-graphics` | `viewer.graphics` | 10 / 1 615 | visori, immagini speciali (HEIC, AVIF, RAW, TIFF, PSD, SVG) e il provino dei caratteri — **opt-in** | **viewer** |
| `engine-viewer-3d` | `viewer.scene` | 6 / 1 406 | visori, modelli GLB, glTF, OBJ, STL con Filament — **opt-in** | **viewer** |
| `engine-viewer-ar` | `viewer.ar` | 7 / 1 002 | visori, il modello posato nella stanza con ARCore (chiede la fotocamera) — **opt-in** | **viewer-3d** |
| `engine-sync` | `sync` | 12 / 2 517 | la sincronizzazione: registro locale (SQLite della piattaforma), orologio logico ibrido, invio una volta sola con chiave d'idempotenza, unione per campo e dei testi lunghi, pietre tombali e compattazione, ripresa dopo mesi, l'adattatore del relay e il sigillo di chi chiama, WorkManager — **opt-in**, dalla 4.0 (F8.21, `moduli/sync.md`) | storage, foundation |
| `engine-account` | `account` | 9 / 1 300 | l'Account Pampa visto da un'app: lo Store trovato e verificato nei due sensi, il protocollo del provider, token, chiavi dell'app, la busta delle righe e il formato del backup, l'aspetto e i luoghi; mai rete; Bouncy Castle — **opt-in**, dalla 4.0 (F10.4, `moduli/account.md`) | storage, foundation |
| `engine-account-host` | `account.host` | 22 / 2 890 | il motore dell'account in Pampa Store: provider firmato, sessione e chiave del dispositivo, token, custodia delle chiavi (HPKE, porte, epoche, passaggio col QR, due classi), codice di recupero, luoghi, aspetto, backup dello Store con WorkManager — **opt-in**, solo lo Store (F10.4, `moduli/account-host.md`) | **account**, **auth**, **vault** |
| `engine-account-ui` | `account.ui` | 2 / 430 | la riga «Account Pampa» e l'avatar, e i componenti delle persone (fila con «I tuoi dispositivi», «Condivisi con te», «Da X», «Rispondi», le parole cambiate) — **opt-in** (F10.4 e F10.6, `moduli/account-ui.md`) | **account**, **ui** |
| `engine-people` | `people` | 20 / 4 300 | le persone dell'account: crittografia di Codex (PMX1, CDXB, CDXM, scheda, codice, quattro parole con l'impegno, runa), persona in radice e chiavi di lavoro, `PeopleClient` con la coda in uscita e il canale aperto, gruppi, spazi condivisi sopra `engine-sync`, i metodi delle persone del provider dello Store, il server finto del banco — **opt-in**, Store e Pampa Mail (F10.6, `moduli/persone.md`) | **account**, **sync**, storage |
| `engine-push-fcm` | `push` | 1 / 120 | il campanello di Pampa Store con Firebase Messaging, spento fino all'accesso; le notifiche dentro i messaggi non passano — **opt-in**, solo lo Store (F10.6, `moduli/push-fcm.md`) | foundation |
| `engine-revisions` | `revisions` | 2 / 576 | il foglio di vetro della scelta tra versioni in conflitto, i passaggi diversi con il tetto di lavoro — **opt-in**, dalla 4.0 (F8.21, `moduli/revisions.md`) | foundation, **ui** |
| `engine-notify` | `notify` | 13 / 2 652 | le notifiche vere di Android: canali, notifiche raccolte per filo, risposte rapide, riepilogo programmato, la porta del ritmo, i Live Update di Android 16 (e la notifica con l'avanzamento prima), i promemoria con `AlarmManager` e il loro ripristino, i controlli di «Le notifiche non arrivano?» — **opt-in**, dalla 4.0 (F8.19, `moduli/notify.md`) | foundation |
| `engine-media` | `media` | 13 / 4 250 | foto e allegati: il selettore di foto e video di vetro (la griglia di F8.4 con la scelta in ordine, album, ricerca, anteprima che nasce dalla miniatura, i tre regimi dei permessi), il contratto delle sorgenti (il telefono, le altre app col provider firmato: MyCloud Gallery in F12, una sorgente finta), il Photo Picker di Android dove basta, il pannello degli allegati (la scheda del «+» e il pannello al posto della tastiera) — **opt-in**, dalla 4.0 (F8.14, `moduli/media.md`); niente permessi dichiarati | **ui** |
| `engine-audio` | `audio` | 4 / 1 200 | la cattura del registratore: il microfono con le interruzioni (il fuoco audio, il microfono zittito), la registrazione con pausa e ripresa in un file solo (AAC in M4A con `MediaCodec`, o WAV), l'onda dal vivo, l'avviso del suono che taglia, il taglio dei silenzi, l'onda di un file salvata accanto — **opt-in**, dalla 4.0 (F8.13, `moduli/audio.md`; il tasto rosso, il pannello e l'onda in `engine-ui`) | foundation |
| `engine-playback` | `playback` | 3 / 800 | la riproduzione di sistema con Media3 (dell'app, `compileOnly`): il servizio con la sessione, la notifica e la schermata di blocco con meno e più 15 s, la pausa per la cuffia e le chiamate, la velocità, il salto dei silenzi, il timer, la coda ricordata, il controllo per Compose che è una `FluidMediaSession` — **opt-in**, dalla 4.0 (F8.13, `moduli/playback.md`) | ui, foundation |
| `engine-transcript` | `transcript` | 2 / 600 | l'ascolto dal paragrafo: il contratto dei tempi (paragrafi, frasi, parole) e del salto, i paragrafi dai segmenti di un riconoscitore, la vista che segue la voce senza saltare e «Torna alla voce» — **opt-in**, dalla 4.0 (F8.13, `moduli/transcript.md`) | ui, foundation |
| `engine-ink` | `ink` | 14 / 4 300 | il disegno e le annotazioni come Markup di iOS 26: il foglio col tratto a bassa latenza di androidx.ink (la penna dello stilo che prende il posto del dito, il dito che scorre, pressione e inclinazione, il palmo), la tavolozza di vetro (penna, evidenziatore, matita, gomma a oggetti e a pixel, lazo, righello, colori e spessori, che si sposta e si riduce), le forme tenendo fermo, annulla e ripristina, il salvataggio dei tratti, la foto e il PDF annotati in un file nuovo — **opt-in**, dalla 4.0 (F8.16, `moduli/ink.md`); androidx.ink 1.0 (Apache-2.0), nessun permesso | **ui** |
| `engine-editor` | `editor` | 16 / 3 700 | la modifica delle immagini: l'editor delle foto come «Modifica» di Foto in iOS 26 (ritaglio con le proporzioni e la cornice che resta nella foto, rotazione di 90° e libera col righello e due dita, specchio, le regolazioni con la ruota e i valori, «Auto», annulla e ripristina, l'originale tenendo premuto), la ricetta che lascia intatto l'originale, il calcolo dei pixel uguale sulla CPU e nello shader AGSL dell'anteprima, il salvataggio a strisce a piena risoluzione con EXIF e profilo colore, lo strumento «Modifica» del visore dei file — **opt-in**, dalla 4.0 (F8.15, `moduli/editor.md`); il confronto a tendina (`FluidBeforeAfter`) sta in `engine-ui` | **ui** |
| `engine-transfer` | `transfer` | 5 / 1 700 | il centro trasferimenti: coda persistita (SQLite della piattaforma) di scaricamenti e caricamenti lunghi, pausa e ripresa con `Range`/`If-Range` e a pezzi con `Content-Range`, solo Wi-Fi e in carica e la regola delle condizioni, velocità e tempo che manca, errori con motivo e nuovi tentativi, WorkManager col servizio in primo piano `dataSync` e il punto d'aggancio della notifica — **opt-in**, dalla 4.0 (F8.20, `moduli/transfer.md`; la pagina e la pillola in `engine-ui`) | net, foundation |
| `engine-files` | `files` | 6 / 1 760 | lo sfoglia-file di vetro come File di iOS 26: le schede Recenti e Sfoglia (le posizioni: le sorgenti dell'app e le cartelle concesse con lo Storage Access Framework, anche delle app con un `DocumentsProvider`), le cartelle in griglia con le miniature vere o in elenco, l'ordine, la ricerca, la selezione e la scelta (`onPick`), l'apertura nei visori dalla miniatura con la ripresa dallo stesso punto; il contratto delle sorgenti a pagine — **opt-in**, dalla 4.0 (F8.18, `moduli/files.md`) | **viewer** (e da lì ui, foundation) |
| `engine-files` | `files` | 6 / 1 760 | lo sfoglia-file di vetro come File di iOS 26: Sfoglia con le posizioni (le sorgenti dell'app e le cartelle concesse con lo Storage Access Framework, anche delle app con un `DocumentsProvider`) e Recenti, le cartelle in griglia con le miniature vere o in elenco, l'ordine, la ricerca, la selezione e la scelta (`onPick`), l'apertura nei visori dalla miniatura con la ripresa dallo stesso punto; il contratto delle sorgenti a pagine — **opt-in**, dalla 4.0 (F8.18, `moduli/files.md`) | **viewer** (e da lì ui, foundation) |
| `engine-search` | `search` | 9 / 2 900 | la ricerca nei dati dell'app come Core Spotlight: l'indice in SQLite FTS4 del telefono (niente librerie native), l'italiano (accenti, apostrofi, plurali), gli errori di battitura, gli inizi di parola, l'ordine BM25F coi pesi dei campi, i pezzi con le parole evidenziate, la cassaforte per i dati sensibili (impronte con chiave), l'aggiornamento a pezzi e il lavoro pesante in carica (WorkManager), l'aggancio al campo (`FluidSearchSpec`: gettoni, suggerimenti, recenti), i vettori del significato, «Cerca in Pampa» col ponte firmato — **opt-in**, dalla 4.0 (F8.22, `moduli/search.md`) | **ui**, **ai-bridge**, **vault** |
| `engine-search-semantic` | `search.semantic` | 2 / 230 | il modello del significato: EmbeddingGemma 2 Text 270M con LiteRT-LM (`liblitertlm_jni.so` 22 MB in arm64-v8a), lo stesso di MyCloud Gallery 2; il modello (157 MiB) lo porta l'app — **opt-in** (F8.22, `moduli/search.md`) | **search** (+ LiteRT-LM) |
| `engine-viewer-all` | — | 0 / 0 | il visore universale: `api` verso `engine-viewer` e tutte le famiglie — **opt-in** | tutte le famiglie |
| `engine-auth` | `auth` | 11 / 938 | le passkey e il gestore delle password con Credential Manager, senza rete: l'estensione PRF la scrive il modulo, la risposta torna senza il PRF, gli errori come frasi pronte (`AuthResult`, `FluidAuthLabels`) — **opt-in**, dalla 4.0 (la scheda: `moduli/auth.md`) | foundation (+ `androidx.credentials`) |
| `engine-auth-google` | `auth.google` | 2 / 277 | «Continua con Google» (`FluidGoogleSignIn`) e le concessioni Google (`FluidGoogleAccess`, **spente**): l'unico modulo con librerie Google non libere, col permesso della GPL-3.0 § 7 — **opt-in**, dalla 4.0 | **auth** (+ Play services, `googleid`) |

(I numeri di file e righe sono quelli dell'engine 3.0.4; `engine-richtext` è contato sulla 3.1.0, `engine-viewer` e i moduli di famiglia sulla 4.0 di F6C, seconda ondata, `engine-auth` ed `engine-auth-google` sulla 4.0 di F10.2.) `engine-ui` e' circa tre quarti di tutte le righe; gli altri sono sottili di proposito. Un modulo senza UI non puo'
dipendere da `engine-ui`: e' il motivo per cui un'app di sole View puo' prendere `engine-update` senza Compose.

```
engine-foundation  <-  engine-ui  <-  engine-widget
        ^                  ^      <-  engine-wear
        |                  +------<-  engine-richtext           (opt-in)
        |                  +------<-  engine-viewer             (opt-in; Media3 compileOnly)
        |                                  ^  <- engine-viewer-office, -archive, -data, -graphics, -3d  (opt-in, ognuno)
        |                                  |       ^  <- engine-viewer-ar (prende -3d)
        |                                  +----- engine-viewer-all (api verso tutti)
        |                                  +----- engine-files      (opt-in, F8.18: lo sfoglia-file)
        |                  +------<-  engine-ai-ui  ->  engine-ai
        +-- engine-storage --+                              ^
        +-- engine-net ------+--> engine-config             +-- engine-ai-bridge
        |        ^
        |        +-- engine-update
        +-- engine-ai (foundation, DataStore, serialization, coroutines-android)  ->  engine-vault
        +-- engine-vault (DataStore, il Keystore)   <-  engine-security  ->  engine-ui     (opt-in, 4.0)
        +-- engine-auth (androidx.credentials)  <-  engine-auth-google (Play services)   (opt-in, tutti e due)
        +-- engine-storage <- engine-sync  (opt-in; WorkManager, coroutines-android; niente Compose)
        +-- engine-net <- engine-transfer   (opt-in; WorkManager, androidx.core, servizio dataSync; niente Compose)
engine-ui <- engine-revisions              (opt-in; il foglio della scelta; non conosce engine-sync)
engine-ui <- engine-media                  (opt-in; MediaStore, il Photo Picker di androidx.activity; nessuna libreria nuova)
engine-ui <- engine-ink                    (opt-in; androidx.ink 1.0 e la sua libreria nativa, input-motionprediction)
engine-ui + engine-ai-bridge + engine-vault <- engine-search <- engine-search-semantic   (opt-in; FTS4 del telefono e WorkManager; LiteRT-LM solo nel secondo)
engine-ui <- engine-editor                 (opt-in; Bitmap, Canvas, BitmapRegionDecoder, ExifInterface, AGSL da Android 13; nessuna libreria nuova)
engine-foundation <- engine-notify         (opt-in; androidx.core; niente Compose: pagine e ponte con la pillola in engine-ui)
engine-foundation <- engine-audio          (opt-in; nessuna libreria; niente Compose: il registratore e l'onda in engine-ui)
engine-ui <- engine-playback               (opt-in; Media3 compileOnly: session ed exoplayer li porta l'app)
engine-ui <- engine-transcript             (opt-in; nessuna libreria)
engine-storage <- engine-account <- engine-account-host   (opt-in; Bouncy Castle; l'host solo nello Store, con engine-auth, engine-vault e WorkManager)
engine-ui + engine-account <- engine-account-ui           (opt-in; nessuna chiave)
engine-account + engine-sync <- engine-people             (opt-in; Bouncy Castle; Store e Pampa Mail)
engine-push-fcm                                           (opt-in; Firebase Messaging, solo lo Store; permesso GPL-3.0 § 7)
```

(freccia = «dipende da»: `engine-config` prende foundation, net e storage; `engine-update` foundation e net;
`engine-ai-ui` prende `engine-ui` e `engine-ai`; `engine-ai-bridge` prende `engine-ai`; `engine-richtext` e `engine-viewer` prendono `engine-foundation` e `engine-ui`. `engine-ui` non dipende mai da `engine-viewer` né da una libreria di decodifica: lo controlla la build, F6C-d. Ogni modulo di famiglia prende `engine-viewer` con `api` e porta solo le librerie della sua lista ammessa, `engine-viewer/families.json`; `engine-viewer` non arriva mai a una famiglia: lo controlla `checkViewerFamilies`, F6C-h.)

Le dipendenze `api` di `engine-ui` esportano all'app: Compose BOM (ui, foundation, runtime, animation), Material 3,
`material-icons-extended`, core-ktx, lifecycle (runtime/viewmodel compose), activity-compose, navigation-compose e
`engine-foundation`. `engine-ui` tiene `graphics-shapes` come `implementation`, e **dalla 3.1.0 anche `androidx.webkit`** (`implementation`, per `FluidWebContent`): un'app che compila `engine-ui` dai sorgenti ottiene `androidx.webkit` nel build anche se non usa mai il contenitore web.

## Quali moduli mettere in un'app

| l'app… | moduli |
|---|---|
| è in Compose e vuole il look | `engine-ui` (porta `engine-foundation`) |
| vuole anche aggiornarsi da sola | + `engine-update` (dichiara `REQUEST_INSTALL_PACKAGES`: non pubblicabile su Play senza motivazione) |
| vuole flag e kill switch | + `engine-config` (porta `net` e `storage`) |
| ha widget Glance | + `engine-widget` |
| ha un assistente | + `engine-ai` (+ `engine-ai-ui` se vuoi la sua interfaccia) |
| espone o chiama tool di altre app | + `engine-ai-bridge` |
| custodisce password, token, chiavi, documenti cifrati | + `engine-vault` (**opt-in**; entra comunque con `engine-ai`) |
| si blocca con l'impronta, o ha una cartella o un album protetti | + `engine-security` (**opt-in**; porta `engine-ui` e `engine-vault`) |
| fa scegliere foto e video dentro l'app (allegati, foto del profilo), legge da MyCloud Gallery, ha il «+» degli allegati | + `engine-media` (**opt-in**; i permessi delle foto li dichiara l'app solo se usa la griglia del telefono) |
| fa disegnare o annotare (uno schizzo, una firma, scrivere su una foto o su un PDF) | + `engine-ink` (**opt-in**; porta androidx.ink e la sua libreria nativa, circa 1,3 MB per ABI) |
| fa modificare le foto (ritaglio, raddrizza, luce e colore, la foto tonda dell'account), anche dal visore dei file | + `engine-editor` (**opt-in**; nessun permesso, nessuna libreria; il confronto a tendina da solo è già in `engine-ui`) |
| riceve da altre app (Condividi → l'app, il menu del testo selezionato) | + `engine-share-in` (**opt-in**; l'attivita' che riceve e i filtri li dichiara l'app) |
| vuole «Mostra come QR» nel foglio di condivisione | + `engine-qr` (**opt-in**; porta ZXing core). Il foglio, le immagini da condividere, il PDF e la stampa sono gia' in `engine-ui` |
| scrive testo formattato (corpo di una mail, note con elenchi, firme) | + `engine-richtext` (**opt-in**: solo se chiesto per nome, `-Modules …,engine-richtext`; porta `engine-ui` e `engine-foundation`) |
| apre file dal disco nel visore (PDF, testo e codice, GIF animate, video e audio) | + `engine-viewer` (**opt-in**: `-Modules …,engine-viewer`; porta `engine-ui` e `engine-foundation`; per video e audio l'app aggiunge il suo `media3-exoplayer`). Il telaio dei visori è già in `engine-ui`: un'app che ha i suoi decodificatori non lo installa |
| sincronizza i dati fra i dispositivi della persona, o uno spazio condiviso (liste, gruppi), anche senza rete | + `engine-sync` (**opt-in**: `-Modules …,engine-sync`; porta `engine-storage` e WorkManager; la cifratura la mette l'Account Pampa col suo sigillo) |
| fa scegliere fra due versioni di un elemento in conflitto | + `engine-revisions` (**opt-in**; porta `engine-ui`; non richiede `engine-sync`) |
| avvisa fuori dall'app (notifiche vere, un lavoro lungo che continua come Live Update, promemoria) | + `engine-notify` (**opt-in**: `-Modules …,engine-notify`; porta solo `engine-foundation` e `androidx.core`; le pagine sono già in `engine-ui`) |
| usa l'Account Pampa (riga nelle impostazioni, token per il suo servizio, chiavi, backup) | + `engine-account-ui` (**opt-in**; porta `engine-account` ed `engine-ui`), o solo `engine-account` senza interfaccia |
| e' Pampa Store, il custode dell'account | + `engine-account-host` (**opt-in**; porta `engine-account`, `engine-auth`, `engine-vault` e WorkManager), + `engine-people` e `engine-push-fcm` per le persone e il campanello |
| e' Pampa Mail, la faccia della rubrica | + `engine-people` (**opt-in**; le conversazioni con le chiavi che lo Store le da') |
| condivide con le persone, «Condivisi con te», «Da X» | + `engine-account-ui` (`FluidPeopleRow`, `FluidSharedWithYou`, `FluidAttribution`, `FluidQuickReply`) e `PampaPeople` di `engine-account` |
| registra memo o lezioni col microfono (pausa e ripresa, l'onda dal vivo) | + `engine-audio` (**opt-in**: `-Modules …,engine-audio`; porta solo `engine-foundation`; il tasto rosso, il pannello e l'onda sono già in `engine-ui`; il servizio in primo piano `microphone` per lo schermo spento è dell'app) |
| suona audio che continua fuori dall'app (notifica, schermata di blocco, cuffie, timer) | + `engine-playback` (**opt-in**; porta `engine-ui`; l'app aggiunge `media3-session` e `media3-exoplayer` alla sua versione, almeno la 1.5.1) |
| mostra la trascrizione di un audio, toccabile per parola e per paragrafo | + `engine-transcript` (**opt-in**; porta `engine-ui`; il lettore di solito è `engine-playback`) |
| scarica o carica file grandi che devono finire anche con l'app chiusa (APK, mappe, brani, foto e video) | + `engine-transfer` (**opt-in**: `-Modules …,engine-transfer`; porta `engine-net` e WorkManager e dichiara il servizio in primo piano `dataSync`; la pagina «Trasferimenti» e la pillola sono già in `engine-ui`) |
| cerca nei suoi dati con un indice (note, mail, comunicazioni, foto), o risponde a «Cerca in Pampa» | + `engine-search` (**opt-in**: `-Modules …,engine-search`; porta `engine-ui`, `engine-ai-bridge`, `engine-vault` e WorkManager; nessuna libreria nativa) |
| cerca anche per significato («la mail del dentista») col modello sul telefono | + `engine-search-semantic` (**opt-in**; porta LiteRT-LM, 22 MB di libreria nativa in arm64-v8a; il modello di 157 MiB lo porta l'app o un'altra app Pampa) |
| fa sfogliare cartelle e scegliere file (i file dell'app, una cartella del telefono, Drive), con i recenti e l'apertura nei visori | + `engine-files` (**opt-in**: `-Modules …,engine-files`; porta `engine-viewer`; nessun permesso: le cartelle arrivano dal selettore di sistema) |
| apre anche documenti Office, archivi, Markdown e dati, immagini speciali e font, modelli 3D | + il modulo di ogni famiglia che serve (`-Modules …,engine-viewer-office`), o `engine-viewer-all` per il visore universale. Chi non ne aggiunge uno non ne paga il peso (la tabella dei pesi: `moduli/viewer-famiglie.md`); `engine-viewer-ar` chiede la fotocamera |
| fa entrare con una passkey o salva un codice nel gestore delle password (oggi solo Pampa Store) | + `engine-auth` (**opt-in**: `-Modules …,engine-auth`; porta `engine-foundation` e `androidx.credentials`, niente Compose). Da Android 9 a 13 serve anche `credentials-play-services-auth`, che porta `engine-auth-google` |
| fa entrare con Google, o chiederà un token Google (solo Pampa Store) | + `engine-auth-google` (**opt-in**; porta `engine-auth` e le librerie Google non libere, col permesso di `LICENSES/Permesso-librerie-Google.md`) |
| è un orologio (modulo Wear OS) | `engine-wear` **solo nel modulo dell'orologio** |
| è di sole View e XML | solo `engine-update` / `engine-net` / `engine-config` (`-Modules`), mai `engine-ui` |
| è Compose **Multiplatform** | niente `engine-ui` (artefatti diversi): i moduli senza UI in `androidMain`, il look si **porta** (`integrazione.md`, «Il porto») |

`engine-install.ps1 -Modules a,b` chiude da sé le dipendenze. Perché conta: `engine-ui` applica il plugin Compose,
e un'app che non lo dichiara nel build root non riesce nemmeno a **configurare** il modulo, quindi ferma tutto il build.

## Toolchain (da `versions.gradle`)

AGP 8.13.2 · Kotlin 2.2.20 · Compose BOM 2026.03.00 · Material 3 **1.5.0-alpha16** (`MotionScheme` non è nella
linea stabile) · lifecycle 2.10.0 · activity-compose 1.13.0 · navigation 2.9.8 · DataStore 1.1.1 · Glance 1.1.1 ·
Wear Compose 1.6.2 · androidx.webkit 1.14.0 (dalla 3.1.0) · coroutines 1.8.1 · serialization 1.6.3 · Java 17. `compileSdk` 36, `minSdk` 26 (alzabile dall'app
con `engine.minSdk`, mai abbassabile; `engine.compileSdk=35` nel `gradle.properties` dell'app per un AGP vecchio).
Le versioni stanno in `versions.gradle` come mappa semplice, non come catalogo: l'engine sta dentro app che ne hanno già uno.

## `engine-ui` per cartella

Tutto sotto `engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/`. La colonna «scheda» è la pagina di
`references/componenti/` che li descrive; «API» è il catalogo generato in `references/api/`.

| cartella | cosa | scheda | API |
|---|---|---|---|
| `fluid/` (89 file nella 3.1.0, 73 nella 3.0.4) | quasi tutti i componenti: schermata, barre, righe, controlli, menu, notifiche, pressione, movimento, materiale; dalla 3.1.0 liste lunghe, avatar e selezione, pannello laterale, selettore data e ora, campo a gettoni, contenuto web, grafici, file | `schermata`, `barre`, `contenuto` (+ `contenuto-web`, `grafici`), `controlli`, `menu-modali`, `movimento`, `impostazioni` | stesse (+ `contenuto-web`, `grafici`) |
| `theme/` | `FluidTheme`, `FluidComponents` (i componenti Material ridefiniti), vecchi `Motion`/`RouteMotion` | `tema` | `tema` |
| `glass/` (+ `backdrop/` e sotto) | la libreria di Kyant0 copiata come sorgente: lente, backdrop, effetti, highlight, ombre | `vetro` | `vetro` |
| `fluidphysics/` (17 file) | il motore che trasforma una sagoma di vetro in un'altra | `fluid-physics` | `fluid-physics` |
| `page/` + `navigation/` | `FluidNavHost`, host delle pagine, back, barra di pagina | `pagine` | `pagine` |
| gli elementi dinamici (F6B): `fluid/toolbar/FluidSelectionToolbar.kt`, `fluid/action/`, `fluid/FluidUndoCountdown.kt`, `fluid/FluidNotificationSource.kt`, `page/FluidZoomPresets.kt`; e, dalla seconda parte, `fluid/FluidSearch.kt` (i preset del campo e `FluidComposerSlots`), `fluid/FluidFindInText.kt`, `fluid/toolbar/FluidToolbarSearch.kt` (il campo che cresce, la lista che gli lascia il posto), `fluid/FluidFilterChips.kt`, `fluid/toolbar/FluidKeyboardBar.kt`, `fluid/activity/FluidActivity.kt`, `fluid/FluidSplitButton.kt`, `fluid/toolbar/FluidToolbarTitleSelector.kt`, `aiui/chat/FluidComposerInField.kt` | ciò che è dell'interfaccia e si trasforma: la selezione con la tab bar che diventa le azioni, i tasti con esito, il contatore «Annulla», l'avviso dal tasto, l'elemento che diventa pagina; il campo coi preset e «trova», il composer dentro il campo, i filtri a pillole, la barra sopra la tastiera, la pillola di attività, il tasto diviso, il titolo selettore; le scene in `sample/…/dynamic/` | `elementi-dinamici` (+ `toolbar`, `barre`, `notifiche`, `pagine`, `ai-ui-chat`, `richtext`) | `barre`, `controlli`, `menu-modali`, `pagine`, `ai-ui`, `richtext` |
| `haptics/` | vocabolario degli eventi aptici, grana, pattern | `aptica` | `aptica` |
| `tutorial/` | suggerimenti al primo uso, coach mark | `tutorial` | `tutorial` |

I file più grandi, da conoscere perché è lì che stanno le cose difficili: `FluidFloatingTabBar.kt` (3 181 righe, la
barra a pillola che si ripiega), `GlassMaterial.kt` (1 938, il materiale), `FluidScreen.kt` (1 803),
`FluidComponents.kt` (1 794), `FluidPhysicsState.kt` (1 237), `FluidNotification.kt` (1 213),
`DrawBackdropModifier.kt` (1 211), `FluidScrubber.kt` (1 203). Pop-up e fogli non sono più un solo file (erano
`FluidGlassModal.kt`, 2 411 righe): da F5A.0 sono sette, da 110 a 705 righe, elencati nella scheda dei modali.

## Il resto del repo

| cosa | dove | a che serve |
|---|---|---|
| gli script | `tools/` | `engine-install`, `engine-update`, `engine-update-all`, `engine-doctor`, `engine-release` (vedi `strumenti-e-qa.md`) |
| la galleria | `sample/` | **Fluid Glass**, app vera del Pampa Store (`dev.antigravity.fluidglass`): ogni cambiamento al design system si *guarda* qui prima di arrivare nelle app. È anche la raccolta dei test d'interfaccia (Robolectric) |
| i modelli | `templates/` | `EngineBootstrap.kt.txt` (collegamenti nella `Application`), `EngineModule.kt.txt`, `engine-manifest.json`, `LICENSE-GPL-3.0.txt` |
| il manifest dello store | `manifest.json` | le app pubblicate dall'engine (Fluid Glass) + sezione `engine` |
| le licenze | `LICENSES/` | `AndroidLiquidGlass.md` (Kyant0, Apache-2.0), `Haze.md` (Chris Banes, Apache-2.0: il bordo dello scorrimento), `MaterialSymbols.md` (Google, Apache-2.0), `Inter.md`, `LibrePods.md` (**GPL-3.0**), `Permesso-librerie-Google.md` (il permesso della GPL-3.0 § 7 per Play services e Firebase, dalla 4.0) |
| la documentazione lunga | `docs/` | 10 capitoli numerati + `riferimenti-3.0.md` (l'analisi di LibrePods) |
| lo stato del lavoro | `artifacts/` | `3.0-decisioni.md`, `3.0-stato.md`, e le cartelle dei collaudi (beta, baseline, perf) |
| il changelog | `CHANGELOG.md` | una sezione per versione; le voci **BREAKING** e «Cambia a vista/al tatto» |
| il piano d'adozione | `ADOZIONE.md` | in che ordine e a che profondità ogni app sta sull'engine; «Migrare alla 3.0» |
| la skill | `skill/fluid-engine/` | questa documentazione (la fonte; l'installazione è una giunzione qui) |

## Test

Nella 3.1.0: 179 file di test sotto `engine-*/src/test` (113 in `engine-ui`, 33 in `engine-ai`, 8 in `engine-wear`, 7 in
`engine-ai-ui`, 7 in `engine-richtext`, gli altri 2-5) più 47 in `sample/src/testDebug` (interfaccia con Robolectric: gesti, pagine, menu,
semantica) e le prove **strumentate** in `sample/src/androidTest` (una WebView vera: `FluidWebContentInstrumentedTest` e, dalla 3.1.1, `FluidWebContentNetworkCensusTest`, `FluidWebContentSealInstrumentedTest`, `FluidWebContentRendererLifecycleTest` e `FluidWebContentProfileReuseTest`, che girano solo su emulatore o dispositivo). Quando una scheda dice «verificato da» un test, quel test è lì. Un comportamento di cui nessun test parla
va **guardato in mano** prima di fidarsene (`strumenti-e-qa.md`).
