<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/moduli/diagnostics.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# engine-diagnostics

**La diagnostica dal campo** (4.0, F8.24, idee `crash-e-lentezze` e `tracce-dei-tempi`): l'app raccoglie da sola i
crash, i blocchi (ANR), le chiusure decise dal sistema e i fotogrammi lenti di ogni pagina, **col consenso chiesto una
volta**, senza contenuti né identità, in una coda sul telefono che parte solo col sì. È MetricKit di Apple e il consenso
«Condividi con gli sviluppatori di app». In più il **registro circolare** (`FluidLog`, che usa «Segnala un problema»
di [`engine-feedback`](feedback.md)) e le **tracce dei tempi** (`FluidTrace`, come OSSignposter), che esistono solo nelle
build di debug e dev. Pacchetto `dev.antigravity.fluidengine.diagnostics`. **Opt-in**; porta `engine-ui` e JankStats
(`androidx.metrics:metrics-performance`, Apache-2.0, `LICENSES/AndroidxMetrics.md`). Nessun permesso.

Catalogo completo delle firme: [`../api/diagnostics.md`](../api/diagnostics.md). Il servizio che riceve i rapporti è un
Worker di Cloudflare con D1, in `engine-diagnostics/worker/` (si prova solo in locale: vedi «Il Worker»).

**Il registro serve anche a F7**: i fotogrammi lenti per modello di telefono e per pagina dicono quali telefoni veri
scattano, e dove.

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| accendere la diagnostica nell'app | `FluidDiagnostics.install(this, FluidDiagnosticsConfig(appName = "Pampa Notes", endpoint = "https://…/v1/reports"))` nell'`Application` |
| chiedere il consenso una volta | `FluidDiagnosticsConsentPrompt(diagnostics)` dove l'app vuole (dopo il benvenuto); chiudere il foglio è il no |
| le righe nelle impostazioni «Privacy» | `FluidDiagnosticsSettingsGroup(diagnostics, onOpenData = { … })` |
| la pagina «Dati di diagnostica» (cosa si raccoglie, i rapporti sul telefono col JSON vero, invia, cancella) | `FluidDiagnosticsDataPage(diagnostics, onBack)` |
| contare i fotogrammi per pagina | `FluidDiagnosticsPage("impostazioni")` in cima a ogni pagina |
| richiedere il consenso perché cambia cosa si raccoglie | `diagnostics.askAgain()` |
| mandare adesso (col sì) | `diagnostics.send()` (sospesa); di serie parte da sola al massimo una volta al giorno |
| tenere tutto sul telefono, senza servizio | `endpoint = null`: la rete non si apre mai |
| scrivere un evento per «Segnala un problema» | `FluidLog.event("sync", "Sincronizzate 3 note")`, `.warn`, `.error` |
| togliere i dati personali da un testo | `FluidPrivacyScrubber.scrub(testo)` |
| segnare quanto dura un passaggio (solo debug e dev) | `FluidTrace.section(FluidTraceNames.Decode) { … }`, `FluidTrace.async(FluidTraceNames.OpenItem) { … }` |
| un intervallo che comincia qui e finisce altrove | `val i = FluidTrace.begin(nome)` e `i.end()` |
| i crediti | `fluidLicensesSection(licences = FluidEngineLicenses + FluidDiagnosticsLicenses)` |
| le prove di un'app | `FluidDiagnosticsTesting.create(context, config, transport, clock)` (mai in un'app) |
| **non così**: un `Thread.setDefaultUncaughtExceptionHandler` che non richiama quello di prima | l'app resta viva in uno stato rotto, o il crash sparisce: il gestore dell'engine passa sempre |
| **non così**: il messaggio di un'eccezione nel rapporto | può contenere quello che l'utente ha scritto: l'engine tiene solo i tipi e le righe del codice |

## Cosa si raccoglie, e cosa no

Le regole stanno nel tipo dei rapporti (`FluidDiagnosticsRecord`), non nella buona volontà di chi li riempie:

- **Un crash** (`FluidCrashInfo`): il tipo dell'eccezione e quelli delle cause, fino a 40 righe del codice
  (`pacchetto.Classe.metodo(File.kt:12)`), principale o in sottofondo, e una firma (SHA-256 dei primi otto passaggi
  del codice dell'app col tipo) per raggruppare i crash uguali. **Mai il messaggio**, mai il nome del filo.
- **Una chiusura decisa dal sistema** (`FluidExitInfo`, Android 11+, da `ApplicationExitInfo`): ANR, crash nativo,
  memoria finita, troppe risorse, avvio fallito, dipendenza morta, segnale, freezer; l'importanza (primo piano o
  sottofondo), la memoria in kB e, per un ANR, le righe del filo principale prese dalla traccia (senza i lucchetti).
  Restano fuori le uscite volute (l'utente che toglie l'app, un aggiornamento, un permesso tolto) e il crash Java, che
  il gestore ha già scritto. Il primo avvio fissa il punto di partenza: le chiusure di prima non sono di questa raccolta.
- **I fotogrammi di una pagina in un giorno** (`FluidPageFrames`): quanti, quanti lenti (JankStats: più del doppio di un
  fotogramma), i blocchi da 250 ms in su (la soglia dei «hang» dell'Organizer di Xcode), i fotogrammi bloccati da 700
  ms in su (Android vitals), il ritardo sommato oltre la scadenza (`hitchMillis`, da cui il rapporto di Apple in ms per
  secondo: sotto 5 bene, da 5 a 10 si vede, da 10 disturba; WWDC20 10077) e l'istogramma delle durate
  (`FluidFrameBuckets`).
- **Di ogni rapporto**: l'app, la versione e il codice, la versione dell'engine, debug o release, il produttore, il
  modello, la versione di Android, la frequenza dello schermo (`FluidDiagnosticsSource`). **Nessun identificativo** del
  telefono o dell'installazione: ogni rapporto ha un id a caso, solo per non contarlo due volte; l'ora è arrotondata
  all'ora intera, i fotogrammi sommati per giorno.
- **Il nome di una pagina** è un nome del codice: con spazi o lettere accentate diventa «pagina» (un titolo di nota
  passato per sbaglio non esce mai).

## Il consenso

`FluidDiagnosticsConsent`: `NotAsked` (di serie), `Granted`, `Denied`. Si ricorda nelle `SharedPreferences`
`fluid-diagnostics` con un nome fisso (`consenso-scelta`: `granted` o `denied`; senza, non ancora chiesta), mai col posto
dell'enum (R3): un riordino non fa diventare un no un sì. Il numero salvato prima di R3 (`consenso`: 1 il sì, 2 il no) si
legge una volta e si riscrive col nome; un valore che non si legge, nuovo o vecchio, è un no.

- **Prima della scelta** i rapporti si raccolgono sul telefono e non partono (come MetricKit, che raccoglie sul
  dispositivo e condivide solo col consenso).
- **Col sì** parte subito un invio, poi al massimo uno al giorno (`sendIntervalMillis`, come MetricKit che consegna una
  volta al giorno).
- **Col no** la coda si svuota, i fotogrammi contati si buttano e non si raccoglie più niente. Il gestore dei crash
  resta e passa ogni crash al sistema. Un invio già partito si ferma prima del gruppo dopo: il consenso si rilegge
  prima di ogni POST, e solo quello in volo arriva (R3). Se il processo muore prima di svuotare la coda, il no salvato
  la svuota al prossimo avvio, prima di ogni altro lavoro.
- **Il foglio** (`FluidDiagnosticsConsentPrompt`) si apre da sé finché la scelta non c'è: «Condividi con lo
  sviluppatore» è il sì; «Non condividere» **e chiudere il foglio** sono il no. Senza un sì esplicito non parte niente.
  Dice cosa parte (tre righe) e cosa non parte mai.

## La coda

`FluidDiagnosticsQueue`: un file di righe JSON in `noBackupFilesDir/fluid-diagnostics/` (fuori dal backup), con un
tetto di 200 rapporti e 512 KiB (`FluidDiagnosticsConfig.maxRecords`, `maxBytes`). Oltre il tetto si tolgono i più
vecchi, prima i fotogrammi e poi il resto. I fotogrammi della stessa pagina, giorno e versione stanno in una riga sola
che si somma: un mese di uso non riempie la coda. Il crash si scrive in modo sincrono e durevole (`fsync`) dal filo che
muore, poi passa al gestore di prima.

## Il gestore dei crash

Si mette al posto del gestore di serie e **lo richiama sempre** (in un `finally`, anche se la scrittura fallisce): è
quello di Android che chiude l'app e mostra «L'app si è arrestata». Una seconda `install` non ne mette un altro. Un
crash dentro la scrittura di un crash non si riscrive.

## I fotogrammi e F7

JankStats si attacca a ogni finestra mentre è visibile; quando l'ultima attività esce dallo schermo i conti passano alla
coda. Il conto di un fotogramma **non alloca niente** (array fissi per al massimo 32 pagine, le altre in «altre»; un
ciclo per indice sugli stati di JankStats), sotto un lucchetto perché JankStats chiama dal suo filo. Il segno della
pagina si scrive solo quando la pagina entra o esce. `FluidDiagnosticsPage` attacca JankStats anche a un'attività già
aperta quando la diagnostica si è accesa dopo.

## Le tracce dei tempi: solo debug e dev

`FluidTrace` scrive con AndroidX Tracing, e si guarda in Perfetto (`engine-diagnostics/tracce/fluid-glass.pbtx`, con la
riga di `adb` nel commento). **Nella release non c'è niente**: il modulo ha due `FluidTraceBackend`, quello di
`src/debug` con AndroidX Tracing e quello di `src/release` vuoto, con `Enabled` costante falsa. Le funzioni di
`FluidTrace` sono `inline` e chiedono prima `if (!FluidTraceBackend.Enabled)`: nella release il compilatore toglie il
ramo già quando compila l'app. Una build «dev» dell'app prende la versione di debug con `matchingFallbacks = ['debug']`;
la bench della galleria e ogni release prendono quella vuota.

- I nomi (`FluidTraceName`) sono del codice: minuscole, cifre, `.`, `_`, `:`, `-`, fino a 64. Un nome di file o un
  indirizzo (che possono contenere dati privati) non passa.
- I nomi comuni (`FluidTraceNames`): `FirstScreen`, `ReadFile`, `WriteFile`, `Network`, `Database`, `Decode`,
  `OpenItem`, `Sync`. L'app ne aggiunge con lo stesso stile (`gallery:apri-album`).
- `section` chiude anche se il blocco lancia; `async` anche se la coroutine viene annullata; `FluidTraceInterval.end`
  chiude una volta sola.

## Il Worker

`engine-diagnostics/worker/`: un Worker di Cloudflare con D1 (`src/index.js`, `migrations/`, `wrangler.toml`), come quelli
dell'Account Pampa. **Si usa solo in locale**: niente `wrangler login`, niente deploy, niente `--remote`.

- `POST /v1/reports` prende `{"schema":1,"reports":[…]}` (al massimo 100 rapporti e 128 KiB). **Valida ogni campo**: un
  campo sconosciuto, un testo troppo lungo, una riga del codice che non sembra una riga del codice, un'ora non intera
  rifiutano l'invio intero. Lo stesso rapporto due volte conta una volta (`INSERT OR IGNORE` sull'id).
- `GET /v1/summary?app=…` è il cruscotto (col segreto `DASHBOARD_TOKEN`): i crash per firma, le chiusure per motivo, i
  fotogrammi per modello e pagina col rapporto dei ritardi di Apple, solo le build di release. `GET /cruscotto` è la
  pagina che lo legge.
- **Nessun indirizzo IP**: il codice non legge mai `CF-Connecting-IP` e i registri di Cloudflare sono spenti. Dopo 90
  giorni i rapporti si cancellano (il cron di ogni notte).
- La prova: `node engine-diagnostics/worker/test.mjs` (Node 22.5+, SQLite di Node dietro la stessa forma di D1). Dal vivo:
  `wrangler dev --local --port 8787` nella cartella del Worker, e sul telefono `adb reverse tcp:8787 tcp:8787` con
  l'indirizzo `http://127.0.0.1:8787/v1/reports` (la configurazione accetta l'HTTP solo verso il PC).

**I nomi veri dai mapping di R8 sono un passo del rilascio**: una release con R8 manda righe del codice offuscate
(`a.b.c(SourceFile:3)`). Al rilascio `engine-release.ps1` (o il CI) carica `mapping.txt` della build, con l'app e il
codice di versione come chiave, in un bucket R2 privato del Worker; il cruscotto ritraduce le righe con `retrace` di R8
(o un ritraduttore in JavaScript nel Worker) prima di mostrarle. Oggi non è scritto: il rapporto di F8.24 lo descrive.

## Trappole

- **`install` una volta sola, nell'`Application`**: le registrazioni non ripassano le attività già aperte (lo fa
  `FluidDiagnosticsPage` per la sua). Una seconda chiamata torna la prima, anche con un'altra configurazione.
- **L'invio vuole INTERNET**, che il modulo non dichiara: un'app con un indirizzo ce l'ha già. Solo HTTPS (o
  `127.0.0.1`, `localhost`, `10.0.2.2` per le prove).
- **Il foglio del consenso è un portale**: serve un `FluidGlassModalHost` alla radice.
- **`FluidLog` è in memoria**: un crash lo perde, ed è voluto (niente dati personali sul disco). Si scrive cosa è
  successo, non i dati: un voto, un nome o il testo di una nota il filtro non li riconosce.
- **Gli IPv6** il filtro li toglie per intero e in forma compressa (`2001:db8::1`, `::1`, `fe80::1%wlan0`,
  `::ffff:192.0.2.10`), e fra parentesi quadre al posto del dominio (`http://[2001:db8::1]:8080/…` diventa
  `http://«[ip]»/«…»`; R3). Un `::` da solo e `Note::class` restano. Anche una parola di sole cifre esadecimali attorno
  a `::` (`dead::beef`) è un IPv6 valido e si toglie.

## Prove

- `engine-diagnostics/src/test` (Robolectric): `FluidDiagnosticsTest` (niente parte prima del sì, senza indirizzo mai,
  il no che svuota, la scelta dopo il riavvio, l'invio fallito che resta, una volta al giorno, i due tetti della coda e
  i fotogrammi che vanno per primi, i fotogrammi sommati per pagina e giorno, il crash senza messaggi, nomi di filo né
  id, la stessa firma con un messaggio diverso, il crash passato sempre al gestore di sistema anche se la scrittura
  cade, una sola `install`, le chiusure di sistema lette una volta con le righe dell'ANR, i gruppi sotto i limiti del
  server, solo HTTPS), `FluidFramesTest` (le soglie di Apple e di Android, «altre», l'ascoltatore che legge pagina e
  ritardo, il nome che non porta dati, le pagine segnate in pila), `FluidLogAndScrubberTest` (14 dati personali tolti,
  il testo normale che resta, il registro circolare). R3 (review `sol-ai`): `FluidLogR3Test` (gli IPv6 compressi, con
  la zona, con un IPv4 in coda e fra parentesi quadre in un indirizzo web, anche nel registro esportato; le ore e
  `Note::class` che restano), `FluidDiagnosticsR3Test` (il no detto mentre parte il primo gruppo ferma gli altri; il no
  salvato senza pulizia svuota la coda all'avvio), `FluidDiagnosticsConsentR3Test` (la scelta col nome fisso, il numero
  di prima migrato, un valore che non si legge che conta come no).
- `src/testDebug`: `FluidTraceDebugTest` (le sezioni, la chiusura quando il blocco lancia, gli intervalli fra i fili,
  i nomi). `src/testRelease`: `FluidTraceReleaseTest` (**nessuna** chiamata a Trace, e la classe della release non
  nomina AndroidX Tracing). Gira con `:engine-diagnostics:testReleaseUnitTest`.
- `sample/src/testDebug/.../novita/F8p24DiagnosticsUiTest.kt`: il foglio del consenso (una volta, il sì che manda, il
  no che svuota, «Chiedi di nuovo»), la riga delle impostazioni, la pagina dei dati col JSON vero e «Cancella».
- `F8p24DataPageR3Test` (galleria, R3): la pagina dei dati che sta leggendo la coda non dice «Niente da mandare» (lo
  dice solo quando sa che è vuota), e i rapporti sono una riga della lista l'uno, composti quando si vedono (prima,
  fino a 200, tutti in un elemento solo).
- Il Worker: `node engine-diagnostics/worker/test.mjs`.

## Dove guardare

`engine-diagnostics/src/main/kotlin/dev/antigravity/fluidengine/diagnostics/`: `FluidDiagnostics.kt` (il consenso,
l'installazione, l'invio), `FluidDiagnosticsRecord.kt` (cosa c'è in un rapporto, `FluidCrash`), `FluidDiagnosticsQueue.kt`,
`FluidCrashHandler.kt`, `FluidExitReasons.kt`, `FluidFrames.kt` (JankStats e `FluidDiagnosticsPage`),
`FluidDiagnosticsUi.kt` (il foglio, le righe, la pagina), `FluidLog.kt` (il registro e `FluidPrivacyScrubber`),
`FluidTrace.kt` (e i due `FluidTraceBackend` in `src/debug` e `src/release`), `testing/FluidDiagnosticsTesting.kt`.

Installazione: `engine-install.ps1 -AppRoot . -Modules engine-diagnostics` (opt-in; porta `engine-ui` e
`engine-foundation`). Nel `build.gradle` dell'app: `implementation project(':engine-diagnostics')`.
