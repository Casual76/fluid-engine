# engine-transfer

**Il centro trasferimenti** (F8.20, idea `centro-trasferimenti`): una coda sola e **persistita** per gli scaricamenti e i
caricamenti lunghi. Va avanti con l'app chiusa (WorkManager col servizio in primo piano di tipo `dataSync`) e riprende dopo
un riavvio; si mette **in pausa e riprende davvero** (`Range` e `If-Range` per gli scaricamenti, pezzi con `Content-Range`
per i caricamenti); rispetta le **condizioni** (solo Wi-Fi, solo in carica, e la regola di `FluidConditions`: chi non può
partire aspetta, dice il perché e offre «Fallo comunque»); mostra la **velocità e il tempo che manca**; un errore ha il suo
**motivo** e si riprova. Pacchetto `dev.antigravity.fluidengine.transfer`. **Opt-in**, senza interfaccia: la pagina
«Trasferimenti» e la pillola stanno in `engine-ui` ([elementi-dinamici](../componenti/elementi-dinamici.md)) e leggono il
modello di [foundation](foundation.md) (`FluidTransferItem`).

**Apple.** Le sessioni in sottofondo di `URLSession` e gli elenchi di download di Safari e dell'App Store.

**Il primo cliente** è Pampa Store (gli APK delle app, con pausa e ripresa veri); poi MyCloud Gallery (foto e video in
sottofondo), Fluidify (brani per l'ascolto offline), Universal Converter e Pampa Mail (file e allegati grandi). L'app dice
**cosa** scaricare, **dove** metterlo e **le credenziali** per farlo; il resto è dell'engine.

Catalogo delle firme: [`../api/transfer.md`](../api/transfer.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| la coda in un'app (su disco, WorkManager, condizioni del telefono) | `FluidTransfers.open(context, authorizer = …)` una volta (di solito nell'`Application`) |
| scaricare un file grande, anche solo col Wi-Fi | `transfers.enqueue(FluidTransferRequest.download(url, File(…), title, expectedBytes = …, sha256 = …, wifiOnly = true))` |
| caricare un file grande che riprende a pezzi | `FluidTransferRequest.upload(url, file, title, resumable = true)` (il server parla il protocollo delle sessioni riprendibili di Cloud Storage/Drive) |
| le credenziali (non si salvano) | `FluidTransferAuthorizer { request -> mapOf("Authorization" to token()) }` |
| la pagina «Trasferimenti» | `FluidTransfersPage(transfers.items.collectAsState().value, controls = transfers)` (engine-ui) |
| i trasferimenti nella pillola | `FluidTransferActivity(attivita, transfers.overview…, transfers)` (engine-ui) |
| pausa, riprendi, riprova, «Fallo comunque», annulla | `transfers.pause(id)`, `resume`, `retry`, `force`, `cancel`, `remove`, `clearFinished` (tornano subito) |
| fuori dall'app la notifica viva di `engine-notify` (un Live Update su Android 16) | `val live = FluidNotify.get(this).liveUpdates` e `FluidTransfers.open(this, notifications = LiveTransferNotifications(live, live::foreground))` |
| una notifica mia | `FluidTransfers.open(context, notifications = …)` o `transfers.notifications = …` (`FluidTransferNotifications`) |
| la coda nella mia `Application`, per il lavoro che riparte a app chiusa | `class App : Application(), FluidTransferHost { override val fluidTransfers by lazy { FluidTransfers.open(this, …) } }` |
| provarla senza telefono | `FluidTransfers(InMemoryFluidTransferStore(), conditions = { … }, conditionChanges = null)` e `runQueue()` contro un server di prova |

## Come funziona

**La coda.** Ogni trasferimento è un `FluidTransferRecord` (la richiesta e dove è arrivato) salvato in SQLite della
piattaforma (`SqliteFluidTransferStore`, in `noBackupFilesDir`; niente Room). Si legge al primo uso, fuori dal thread
principale; un trasferimento trovato «in corso» in una coda appena letta era stato interrotto (il processo è morto) e riparte.
Lo stato si scrive subito; l'avanzamento ogni 2 s (`persistIntervalMillis`), mai a ogni pezzo.

**Il giro.** `runQueue()` prende, uno alla volta e nell'ordine d'arrivo, i trasferimenti in coda o in attesa; per ognuno chiede
il permesso alle condizioni (`conditions().allows(request.work, needs, force)`); chi non può partire diventa `Waiting` col suo
perché. Durante un trasferimento il permesso si richiede ogni secondo: il Wi-Fi che se ne va lo ferma in `Waiting` (e
riprende da dov'era quando torna). Lo fa girare `FluidTransferWorker` (WorkManager), che alla fine mette il giro dopo coi
vincoli di chi aspetta (`FluidTransferPlan`: una rete che non costa per `Metered`, `NotWifi`, `Roaming`, `DataSaver`; la carica
per `NotCharging`; un'attesa di 15 minuti per ciò che WorkManager non sa esprimere: risparmio energetico, calore, «Usa pochi
dati»). Con l'app viva un cambio delle condizioni rimette in coda subito chi può partire. Quando la coda resta senza niente
da fare (chi aspettava è stato annullato o tolto) il piano vuoto (`idle`) toglie anche il giro rimandato da WorkManager:
nessun lavoro resta in coda a girare a vuoto.

**Lo scaricamento.** Scrive in un file a metà suo, accanto al file (`FluidTransfers.partFile(request)`:
`notes.apk.3f2a9c41d07b.part`, col nome del file e un'impronta dell'id; due richieste verso lo stesso file non si cuciono), e
rinomina alla fine (un file mezzo scritto col nome giusto è indistinguibile da uno completo). Alla ripresa chiede
`Range: bytes=N-` con `If-Range` (l'`ETag` forte o il `Last-Modified` del primo scaricamento): `206` col `Content-Range` giusto,
lo stesso validatore e lo stesso totale → continua; `206` con un altro validatore o un altro totale (un server che ignora
`If-Range`) → ricomincia da capo; `200` → il server non riprende (o il file è cambiato): ricomincia da capo, e la riga lo dice
(«Ricomincia da capo dopo una pausa»); `416` con il file già intero → finito. `Accept-Encoding: identity` sempre (con gzip la
lunghezza e i `Range` sarebbero del contenuto compresso). Alla fine: il peso atteso e l'impronta SHA-256, se l'app li dà. Una
pausa o un annullamento arrivati con l'ultima lettura valgono: il parziale non diventa il file.

**I rinvii** si seguono a mano, fino a cinque, anche quelli che cambiano protocollo (un host di release che passa al suo CDN),
relativi compresi; il `308` è un rinvio come gli altri, tranne per i pezzi dei caricamenti (`Content-Range`), dove è la conferma.
**Le credenziali dell'authorizer (tutte le sue intestazioni) vanno solo allo stesso server**: un rinvio verso un'altra origine
(schema, host o porta: «release → CDN con l'indirizzo firmato») le lascia indietro e non tornano nei salti dopo, come fanno i
browser e OkHttp. Un rinvio da https a http, o verso un protocollo che non è http, non si segue: è un errore del server, senza
nuovi tentativi.

**Il caricamento.** Di serie un invio solo (`PUT` o `POST`): dopo una pausa ricomincia. Con `resumable = true` a pezzi da 2 MiB
(multipli di 256 KiB) con `Content-Range: bytes a-b/N`; ogni pezzo riceve `308` con il `Range` confermato; dopo una pausa o un
riavvio si chiede al server a che punto è (un `PUT` vuoto con `Content-Range: bytes *` e il totale). Un server che non tiene i
pezzi (tre `308` senza progresso) è un errore.

**La pausa vera.** `pause(id)` chiude la connessione dal thread di chi chiama: una lettura bloccata su un server lento si
ferma subito, il file a metà resta, e `resume(id)` riparte con `Range`. Lo stesso quando il sistema ferma il lavoro
(l'annullamento chiude la connessione): il trasferimento torna in coda da dov'era.

**La velocità e il tempo che manca.** Una media che dimentica piano (costante di tempo 3 s, a occhio), misurata ogni 200 ms;
il tempo che manca compare dopo 2 s di misure. All'interfaccia arrivano quattro volte al secondo (`progressIntervalMillis`);
la pagina li mostra una volta al secondo.

**Gli errori.** `FluidTransferFailure`: la rete (`network`, si riprova), il server (`server(code)`: 5xx, 429 e 408 si riprovano),
il file che non c'è (`notFound`), l'accesso negato (`unauthorized`), il disco (`storage`: anche **il disco pieno a metà**, che non
è la rete e non si riprova da solo; il file a metà resta e «Riprova» riprende da lì), il file diverso da quello atteso
(`integrity`). Un trasferimento non riuscito ha l'ora di fine (`finishedAtMillis`), come uno completato o annullato: l'esito del
giro lo conta. Chi si riprova da solo lo fa fino a `maxAutoRetries` (3) con le pause crescenti di WorkManager (30 s
esponenziali), restando «In coda · Nuovo tentativo fra poco (2 di 3)»; poi `Failed` con «Riprova». Una caduta di rete
mentre le condizioni dicono «senza rete» non è un errore: è un'attesa.

**Il servizio in primo piano.** `FluidTransferWorker` chiama `setForeground` con la notifica di
`FluidTransfers.notifications` e il tipo `FOREGROUND_SERVICE_TYPE_DATA_SYNC`; il manifesto del modulo dichiara
`SystemForegroundService` con `foregroundServiceType="dataSync"` e i permessi `FOREGROUND_SERVICE` e
`FOREGROUND_SERVICE_DATA_SYNC` (normali: non si chiedono all'utente). Da Android 12, se il lavoro parte con l'app in
sottofondo, il sistema può rifiutare il primo piano: il lavoro continua lo stesso coi limiti di un lavoro normale. Da
Android 15 `dataSync` ha al più sei ore al giorno: un trasferimento fermato dal sistema riprende da dov'era.

**La notifica: il punto d'aggancio con `engine-notify` (F8.19).** `FluidTransferNotifications.foregroundInfo(context,
overview)` (la notifica del servizio) e `finished(context, overview)` (il giro è finito: chi ha una notifica viva lascia
l'esito; di serie niente): di serie `SimpleTransferNotifications` (androidx.core, canale silenzioso «Trasferimenti», il titolo, «12,4 MB di
48 MB · ancora 17 s», la barra, il tocco che apre l'app; niente stile personalizzato).

**La notifica viva (F8.20b): `LiveTransferNotifications`.** Il punto d'aggancio riempito con l'attività viva di
`engine-notify`, senza dipendere da `engine-notify`: prende il pubblicatore dal modello puro di `engine-foundation`
(`FluidLiveUpdateSink`) e la notifica del servizio da chi la costruisce (`FluidLiveUpdates.foreground`).

```kotlin
class App : Application(), FluidTransferHost {
  override val fluidTransfers by lazy {
    val live = FluidNotify.get(this).liveUpdates                          // engine-notify
    FluidTransfers.open(this, notifications = LiveTransferNotifications(live, live::foreground))
  }
}
```

- L'attività è `FluidLiveKind.Transfer` con l'id `"transfers"` (lo stesso della pillola): chiesta dall'utente, quindi su
  Android 16 diventa un **Live Update** (il gettone nella barra di stato, la barra, la schermata di blocco); prima una
  notifica con l'avanzamento. La traduzione è pura: `FluidTransferOverview.toLiveUpdate(controls)` (il trasferimento in corso
  o «Pampa Notes 2.4 e altri 2», «12,4 MB di 48 MB · ancora 17 s» o il perché dell'attesa, l'avanzamento di tutto, «Pausa» e
  «Annulla» come tasti della notifica).
- Alla fine del giro l'esito resta (`fluidTransferOutcome`): «Pampa Notes 2.4 scaricato», «3 trasferimenti completati», o
  «… non riuscito»; in silenzio se non è finito niente (chi aspetta o è in pausa lo dice la pagina). Conta solo chi è finito
  in quel giro.
- **La pillola dei trasferimenti non si specchia fuori** (`mirrorOutside`): fuori la mostra già il lavoro, e le notifiche
  sarebbero due. La sincronizzazione di `engine-sync` non passa di qui (se si mostra fuori è `FluidLiveKind.Sync`, che non
  diventa Live Update).

Il permesso delle notifiche (Android 13+) non lo dichiara questo modulo: lo chiede l'app (o `engine-notify`); senza, il
servizio gira lo stesso e il sistema lo mostra fra le app attive.

## Trappole

- **`FluidTransfers.open` è una per processo**: la prima chiamata decide credenziali, configurazione e notifiche. Un'app con
  credenziali o una notifica sua implementa `FluidTransferHost` nell'`Application`, così il lavoro che riparte a app chiusa
  trova la coda giusta.
- **WorkManager deve potersi avviare** anche in un processo nuovo: con l'avvio automatico di WorkManager tolto dal
  manifesto (la galleria lo fa), l'`Application` implementa `Configuration.Provider`.
- **Un server che non accetta `Range` ricomincia da capo** a ogni ripresa: è corretto (mai cucire due versioni), ma un APK
  da 100 MB su una rete che cade spesso non finisce mai. Pampa Store usa GitHub Releases, che li accetta.
- **Il testo in chiaro** (`http://`) vale solo dove la configurazione di rete dell'app lo permette (la galleria: solo il
  loopback). Un rinvio da https a http non si segue mai.
- **Una credenziale per un CDN** non passa per i rinvii: se anche il CDN la vuole, l'indirizzo dev'essere suo (firmato) o la
  richiesta va fatta direttamente al CDN.
- **Una coda salvata che non scrive** (SQLite pieno o rotto) non ferma la coda: il trasferimento si vede e va avanti, e
  l'errore va nel registro di Android (`FluidTransfers`); si perderebbe solo se il processo muore.
- **Le condizioni** vengono di serie da `FluidDeviceConditions`: `open` chiama `EngineConditions.install`. Una simulazione
  attiva (la galleria) ferma anche la coda: è voluto.

## Cosa è verificato dai test

- `FluidTransfersTest` (31, JVM contro MockWebServer): lo scaricamento intero con velocità, tempo che manca, impronta e
  `identity`; **pausa e ripresa** con `Range` e `If-Range` giusti e il file a metà della misura giusta; un server **senza
  `Range`** che ricomincia, giusto; **un file cambiato** sul server che non si cuce a metà; **il processo che muore a metà** e
  una coda nuova che riprende con `Range`; un errore che passa riprovato da solo e poi «Riprova»; un 404 che non si riprova;
  un file diverso da quello atteso; la rete a consumo che fa aspettare, il piano coi vincoli, **«Fallo comunque»**; solo
  Wi-Fi e senza rete (che non si forza); **il Wi-Fi che se ne va a metà** (attesa e ripresa da lì); annulla che toglie il
  file a metà; l'avanzamento che non si scrive a ogni pezzo; **il caricamento a pezzi** con pausa, domanda al server e
  ripresa; la media della velocità; le intestazioni; la coda vuota che toglie il giro rimandato. R2, contro server scorretti:
  **le credenziali che non seguono un rinvio verso un altro host** (un CDN su `127.0.0.1` dietro un server su `localhost`) e
  restano su un rinvio allo stesso server; **un rinvio da https a http rifiutato** (un server https finto col certificato
  fatto al volo da `keytool`, `LocalTls`) senza toccare il server in chiaro; il `308` seguito; **il disco pieno a metà**
  (`storage`, nessun nuovo tentativo, «Riprova» riprende dal parziale); un server che ignora `If-Range`, uno senza validatori
  che cambia misura; due trasferimenti sullo stesso file; l'ora di fine e l'esito di un non riuscito; una coda salvata che non
  scrive; «Annulla» con l'ultima lettura; il caricamento a pezzi che, prima che il server abbia il primo pezzo, ha già
  l'avanzamento su disco (dopo un riavvio chiede a che punto è); dove porta un rinvio, a chi vanno le credenziali, il file a
  metà per id.
- `FluidTransferAndroidTest` (5, Robolectric): la coda su SQLite che si rilegge uguale (anche un lavoro su misura), il piano
  che diventa i vincoli di WorkManager, il piano vuoto che cancella il lavoro unico, la notifica del servizio di tipo
  `dataSync` col testo e l'avanzamento, **la notifica viva** (genere `Transfer`, Live Update, i tasti che arrivano alla coda,
  l'esito del solo giro).
- `FluidTransferLifecycleTest` (3, Robolectric, R2): il lavoro di WorkManager che lascia l'esito anche quando un altro
  trasferimento riproverà; `open` dopo `close` che dà una coda nuova e viva; la notifica del servizio privata sulla schermata di
  blocco (`VISIBILITY_PRIVATE`: con «nascondi i contenuti sensibili» il nome del file non si vede).

## Dove guardare

- `engine-transfer/src/main/kotlin/dev/antigravity/fluidengine/transfer/FluidTransfers.kt` — la coda, i comandi, il giro,
  scaricare (`download`), caricare (`uploadWhole`, `uploadInChunks`), lo stato (`Session`), il piano.
- `…/FluidTransferRequest.kt` — la richiesta e il record. `…/FluidTransferStore.kt` — la coda in memoria e su SQLite.
- `…/FluidTransferWork.kt` — il lavoro, la pianificazione, il punto d'aggancio della notifica e la notifica semplice.
- `…/LiveTransferNotifications.kt` — la notifica viva di `engine-notify`, la traduzione in `FluidLiveUpdate` e l'esito.
- `…/TransferIo.kt` — la velocità (`TransferRate`), l'HTTP coi rinvii a mano, le intestazioni dei pezzi.
- `engine-transfer/src/main/AndroidManifest.xml` — il servizio `dataSync` e i suoi permessi.

## Dipendenze e configurazione minima

**Dipendenze del modulo.** `api project(':engine-net')` (e da lì foundation), `kotlinx-coroutines-android`, `api
work-runtime-ktx` (chi implementa `FluidTransferNotifications` vede `ForegroundInfo`), `androidx.core` per la notifica.
L'HTTP è quello della piattaforma.

**Aggancio.** `tools/engine-install.ps1 -AppRoot . -Modules …,engine-transfer` (opt-in; porta `engine-net` e
`engine-foundation`). Nel `build.gradle`:

```groovy
implementation project(':engine-transfer')
```

**Configurazione minima.**

```kotlin
class App : Application(), FluidTransferHost {
  override val fluidTransfers: FluidTransfers by lazy {
    FluidTransfers.open(this, authorizer = { mapOf("Authorization" to "Bearer ${account.token()}") })
  }
}
```
