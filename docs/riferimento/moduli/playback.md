<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/moduli/playback.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# engine-playback

**La riproduzione di sistema** (F8.13, idea `riproduzione-di-sistema`): tutto quello che serve perché un audio continui bene
fuori dall'app, scritto una volta. Un servizio di riproduzione (il `MediaSessionService` di Media3 con un ExoPlayer) con la
**sessione** che Android, l'orologio e l'auto capiscono; i **comandi nella notifica e sulla schermata di blocco**, con meno e
più 15 secondi; la **pausa quando la cuffia si stacca** o arriva una chiamata; la **velocità**; il **salto dei silenzi**; il
**timer di spegnimento**; la **coda ricordata** dopo un riavvio (e la ripresa dai tasti delle cuffie); e lo **stato per
Compose**, che è anche la `FluidMediaSession` del visore audio e dell'onda di engine-ui. Pacchetto
`dev.antigravity.fluidengine.playback`. **Opt-in**.

**Apple.** «In riproduzione» (`MPNowPlayingInfoCenter`, `MPRemoteCommandCenter`) e `AVAudioSession`; i salti di 15 secondi
e «Salta silenzi» di Memo Vocali (guida dell'iPhone, «Play it back», iOS 26).

**Chi lo usa.** Pampa Notes (le lezioni, i memo), Fluidify (la musica), Pampa Sounds, MyCloud Gallery (l'audio di un video a
schermo spento), PampAI (le risposte lette). L'app dà **cosa suonare** (`FluidPlaybackItem`: l'indirizzo, il titolo, il
sottotitolo, la copertina) e la versione di Media3.

Catalogo delle firme: [`../api/playback.md`](../api/playback.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| il controllo in una pagina | `val player = rememberFluidPlaybackController()` (null per un attimo, mentre si collega) |
| suonare una lezione, o una coda | `player.setItems(listOf(FluidPlaybackItem(id, uri, title, subtitle)), play = true)` |
| aprirla al punto giusto senza suonare | `player.setItems(items, startPositionMillis = …, play = false)` |
| play, pausa, salto, meno e più 15 s | `player.play()`, `pause()`, `seekTo(ms)`, `skipBy(-15_000L)` |
| la velocità | `player.changePlaybackSpeed(1.5f)`; si legge da `player.playbackSpeed` |
| salta i silenzi | `player.changeSkipSilence(true)`; si legge da `player.skipSilence` |
| il timer | `player.setSleepTimer(15)`, `player.sleepAtEndOfItem()`, `player.cancelSleepTimer()`; `player.sleepAtMillis` |
| l'onda o il visore audio sullo stesso stato | passare `player` dove si chiede una `FluidMediaSession` (`FluidAudioWaveform`, `FluidAudioTimeline`, `FluidViewerContent.Media`) |
| altri salti, la musica invece del parlato | `FluidPlayback.configure(FluidPlaybackConfig(speech = false, skipForwardMillis = 30_000L))` in `Application.onCreate` |
| cosa apre il tocco sulla notifica | `FluidPlaybackConfig(sessionActivity = { context -> … })` (di serie l'attività di avvio dell'app) |
| le licenze | `fluidLicensesSection(licences = FluidEngineLicenses + FluidPlaybackLicenses)` |

## Come funziona

**Il servizio** (`FluidPlaybackService`) è nel manifesto del modulo, esportato, col tipo `mediaPlayback` e i suoi permessi; il
ricevitore dei tasti delle cuffie (`MediaButtonReceiver` di Media3) anche. L'app non dichiara niente. Lo avvia il controllo
collegandosi (`FluidPlaybackController.connect`), e da lì Media3 lo porta in primo piano mentre suona, con la notifica
(canale «In riproduzione»).

**La sessione.** L'ExoPlayer ha gli attributi audio del parlato (o della musica) e **gestisce il fuoco audio**: una chiamata
o un'altra app che suona lo mettono in pausa; il parlato si ferma invece di abbassarsi. Con `setHandleAudioBecomingNoisy` la
cuffia che si stacca mette in pausa. I salti del lettore sono di 15 s (`FluidPlaybackConfig`), e la sessione mette ai lati di
Play i due tasti di Media3 (meno e più 15, coi posti `SLOT_BACK` e `SLOT_FORWARD`): li mostrano la notifica e la schermata di
blocco. Due comandi dell'engine passano dalla sessione: il **timer** (fra tot minuti, o a fine elemento con
`pauseAtEndOfMediaItems`) e il **salto dei silenzi** (lo `skipSilenceEnabled` di ExoPlayer: il file non si tocca). Il loro
stato sta negli **extra della sessione**, quindi lo vede ogni controllo collegato.

**L'indirizzo attraversa la sessione.** Media3 non porta l'indirizzo locale di un `MediaItem` da un controllo alla sessione:
`toMediaItem()` lo mette anche nei metadati della richiesta, e il servizio lo rimette al suo posto. Così la coda ricordata e
la ripresa non hanno bisogno di chiedere all'app «che cos'è questo id».

**La coda ricordata.** Gli elementi, quale si ascoltava e il punto si salvano (in un file di preferenze del modulo) a ogni
cambio della coda (anche da fermi: elementi aggiunti, tolti, spostati), a ogni cambio di elemento, a ogni salto e ogni 5
secondi mentre suona. Una coda **svuotata** dall'app si dimentica: al riavvio non torna quella di prima. Un elemento senza
indirizzo (messo da fuori con la sola `mediaId`) non si può riaprire e non si salva, e il punto di ripresa resta
sull'elemento che si ascoltava (R1, 26 novembre 2026). All'avvio del servizio la coda torna **ferma** al punto giusto
(`restoreOnStart`): riaprendo l'app la lezione c'è e non suona da sola. La ripresa di sistema (Play sulle cuffie con l'app
chiusa, `onPlaybackResumption`) riparte da lì.

**Chi si collega** (R1, 26 novembre 2026). Il servizio è esportato perché lo trovino il sistema, l'orologio e l'auto, ma
non risponde a tutti allo stesso modo:

| chi | cosa può fare |
|---|---|
| l'app stessa (il suo uid: il controllo, la notifica di Media3) | tutto: la coda, i comandi dell'engine (timer, salto dei silenzi) |
| il sistema e chi può comandare i media: la tendina e la schermata di blocco, il Bluetooth, chi ascolta le notifiche (l'app dell'orologio), Android Auto | vede il titolo e comanda l'ascolto (Play, i salti, la velocità); non cambia la coda e non manda i comandi dell'engine |
| un'altra app | non si collega: niente titoli dei memo, niente comandi |

Media3 1.5.1 crede al nome del pacchetto che un controllo dice di avere, e da quel nome decide «fidato» (gli ascoltatori
delle notifiche): il servizio controlla che il nome sia dell'uid che chiama. Resta aperto a tutti, come in ogni app
multimediale, il ricevitore dei tasti delle cuffie: un'altra app può «premere» Play o Pausa come una cuffia, ma non legge
niente.

**Lo stato per Compose.** `FluidPlaybackController` legge il `MediaController` in stati di Compose: `playing` (l'intenzione,
come il tasto di Media3), `buffering`, `positionMillis` (a ogni fotogramma mentre suona, fermo non costa), `durationMillis`,
`item`, `index`, `queueSize`, e gli extra. È una `FluidMediaSession` con `FluidAudioSpeed`.

**Prima che il lettore sia preparato** (la coda ricordata, riaperta ferma) Media3 non offre il salto nell'elemento: il
controllo salta con l'indice (`seekTo(index, ms)`), così il tocco sull'ora di un paragrafo funziona anche lì, **anche
quando la durata non si sa** (R3: gli elementi della coda senza `durationMillis`): il salto non si tiene dentro una durata
che non c'è, e il lettore non si prepara da solo; `canSeek` resta falso finché la durata non arriva (una barra senza
durata non si disegna), ma `seekTo` funziona. Un elemento
finito si ricorda dall'inizio, e Play da in fondo riparte da capo (trovati sul Tab S9 il 25 novembre 2026).

## Trappole

- **Media3 è dell'app**, come in `engine-viewer`: il modulo compila contro la 1.5.1 (`compileOnly`) e i test girano sulla
  1.11.1. L'app aggiunge `media3-session` e `media3-exoplayer` alla sua versione, almeno la 1.5.1; senza, il servizio non
  parte. `engine-install.ps1` lo ricorda.
- **Android Auto** mostra solo le app passate dalla sua revisione (o col modo sviluppatore): la sessione è quella giusta, ma
  l'app nell'auto va chiesta a Google quando servirà.
- **Il controllo si chiude da solo** con la composizione (`rememberFluidPlaybackController`); la riproduzione no, continua
  nel servizio. Fuori da Compose, `connect` e `close` a mano.
- **Un altro `MediaSessionService` nell'app** (Fluidify ne ha uno suo) convive male con questo: si sceglie uno dei due.
- In Robolectric il controllo non si collega al servizio: le prove dell'interfaccia usano una `FluidMediaSession` finta.
- **Un controllo di un'altra app dell'ecosistema** (un widget di Pampa Notes in un'altra app, per esempio) non si collega:
  il servizio è dell'app che lo porta. Per comandarlo da fuori passa dall'app (un `PendingIntent`, un ponte).

## Cosa è verificato dai test

`engine-playback/src/test/.../FluidPlaybackServiceTest.kt` (Robolectric, Media3 1.11.1, l'ExoPlayer vero senza
decodificatori): i salti di 15 s e i due tasti ai lati di Play; i comandi dell'engine offerti all'app stessa; un'app
estranea che non si collega, anche col nome di un'app fidata preso in prestito, e il sistema che vede il titolo e comanda
l'ascolto ma non cambia la coda (R1); il salto dei silenzi di ExoPlayer, pubblicato negli extra; il timer che mette in pausa
dopo il suo minuto, e quello a fine elemento; gli elementi che arrivano da un controllo ritrovano l'indirizzo; un elemento
che fa il giro in `MediaItem` resta uguale; la coda ricordata torna ferma e la ripresa la propone; la coda cambiata da ferma
si ricorda, quella svuotata si dimentica, e un elemento senza indirizzo non sposta il punto di ripresa (R1); la
configurazione dell'app (niente ripresa, salti da 30 s); lo stato del controllo sul lettore; la coda ricordata senza
durate che si salta lo stesso, senza suonare né prepararsi (R3). Sul tablet: la notifica, la schermata di blocco, i salti, il timer.

## Dove guardare

- `engine-playback/src/main/kotlin/dev/antigravity/fluidengine/playback/FluidPlaybackService.kt` — il servizio, la sessione,
  il timer, la coda ricordata.
- `FluidPlaybackController.kt` — il controllo e lo stato per Compose.
- `FluidPlayback.kt` — gli elementi, la configurazione, i comandi, le licenze.
- `engine-playback/src/main/AndroidManifest.xml` — il servizio e il ricevitore.

## Dipendenze e configurazione minima

```groovy
implementation project(':engine-playback')                       // porta engine-ui
implementation "androidx.media3:media3-session:1.11.1"          // la versione dell'app, almeno la 1.5.1
implementation "androidx.media3:media3-exoplayer:1.11.1"
```

Licenze: Media3, Apache-2.0 (`FluidPlaybackLicenses`).
