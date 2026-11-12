# Strumenti, build e collaudo

Cosa lanciare, in che ordine, e come si giudica che una cosa è davvero finita. L'engine ha pochi strumenti e
tutti hanno una ragione: questa pagina dice quale.

## Gli script (`tools/*.ps1`)

Tutti PowerShell, da lanciare con `powershell -ExecutionPolicy Bypass -File <script> …`. Dentro un'app stanno in
`engine\tools\`.

| script | dove si lancia | cosa fa | note |
|---|---|---|---|
| `engine-install.ps1 -AppRoot .` | nell'app | inserisce il blocco nel `settings.gradle`, scrive `engine.properties`, stampa le dipendenze da incollare | idempotente; `-Modules a,b` (le dipendenze si chiudono da sole; **i moduli opt-in — `engine-wear`, `engine-ai-ui`, `engine-richtext` — entrano solo se chiesti per nome**); `-EnginePath` se `engine/` è occupata; `-Mode copy -Source <cartella>` senza git; `-Channel stable|beta`. **Opt-in** per nome: `engine-wear`, `engine-ai-ui` |
| `engine-update.ps1 -AppRoot . -Version X` (o `-Latest`) | nell'app | aggancia il tag, riscrive `engine.properties`, stampa il changelog con le voci **BREAKING** | rifiuta di partire se `engine/` ha modifiche non committate (`-AllowDirty` le perde); non compila e non committa; esce 0 / 1; è un **porto**? non tocca niente e lo dice |
| `engine-update-all.ps1 -Root "…" -Version X` | nell'engine | lo stesso su ogni progetto con un `engine.properties` sotto `-Root` | **`-WhatIf` prima, sempre**; `-Build` compila ognuna e si ferma alla prima che fallisce; elenca i porti senza toccarli |
| `engine-doctor.ps1 -AppRoot .` | nell'app | controlla che `engine.properties`, `ENGINE_VERSION`, `EngineBuild.VERSION`, il tag agganciato e il `settings.gradle` dicano la stessa cosa, e che `engine/` non abbia modifiche locali | **da eseguire prima di dire «ho finito»**: rileva la variante silenziosa. Per un porto confronta `FLUID_PORT_OF` |
| `engine-release.ps1 -Version X -Notes "…" -Tag` | nel repo dell'engine | muove insieme `ENGINE_VERSION`, `EngineBuild.VERSION`, il `CHANGELOG.md` e il tag `engine-X` | albero pulito; più voci → `-Command` (con `-File` PowerShell non costruisce array); non modificare mai i tre numeri a mano |

Trappole di PowerShell 5.1 già pagate dagli script (stanno nel codice, vale ricordarle se ne scrivi uno): `Get-Content -Raw`
decodifica in ANSI, non UTF-8 (un file accentato riscritto si corrompe); `Set-Content -Encoding utf8` scrive un BOM (e
un BOM in testa a un `settings.gradle` è una modifica gratuita); git scrive su stderr anche quando va bene, e con
`$ErrorActionPreference = "Stop"` diventa un errore terminante; un array non passa da `-File`.

## Gli script della skill (`skill/fluid-engine/scripts/`)

Python 3, da lanciare **con `-I`** (isolato) dalla radice dell'engine.

| script | a che serve |
|---|---|
| `gen_api_catalog.py` | rigenera `references/api/` (catalogo delle API pubbliche e digest del changelog) dai sorgenti Kotlin. `--check` non scrive e esce 1 se il catalogo è vecchio. **Va rilanciato a ogni release**: è ciò che impedisce alla documentazione di datarsi di nuovo. Un file nuovo di `ui/fluid/` o un modulo nuovo va prima **mappato** nelle tabelle in testa allo script (`UI_FLUID_FILES`, `DIR_FAMILY`, `FAMILY_TITLES`): se no finisce in «Non classificato» e lo script lo segnala |
| `check_docs.py [file…]` | fallisce se la documentazione nomina un simbolo che non esiste nei sorgenti o ha un link relativo rotto. Una riga con `nocheck` non si controlla |
| `sync_docs.py` | porta le schede della skill in `docs/riferimento/` (copie con un'intestazione che dice di non modificarle): la skill è la fonte, `docs/` il modo di leggerla senza la skill |

## Compilare e provare

Dalla radice dell'engine (l'engine si apre da solo con la galleria **Fluid Glass** in `sample/`):

```bash
./gradlew.bat --no-daemon --no-watch-fs :engine-ui:testDebugUnitTest :engine-richtext:testDebugUnitTest
./gradlew.bat --no-daemon --no-watch-fs :sample:testDebugUnitTest
./gradlew.bat --no-daemon --no-watch-fs :sample:assembleDebug
```

**Su Windows:** `--no-watch-fs` sempre (le build si bloccano sui lock del file watcher); `--no-daemon` quando ne
girano più in parallelo. Serve `local.properties` con `sdk.dir`. Dentro un'app, `:app:assembleDebug testDebugUnitTest`
(l'engine si ricompila con lei: è un include sorgente).

I test d'interfaccia stanno in `sample/src/testDebug` (Robolectric: gesti, pagine, menu, semantica, contrasto) e
non girano da soli con `:engine-ui:test`. Una galleria che compila non prova niente sull'aspetto.

**Le prove strumentate** (`sample/src/androidTest`, `:sample:connectedDebugAndroidTest`) servono dove Robolectric non basta: una `WebView` vera (`FluidWebContentInstrumentedTest`: rete e script chiusi, link, renderer che muore, altezza, **il vetro che contiene davvero i pixel della WebView**; dalla 3.1.1 `FluidWebContentNetworkCensusTest` e `FluidWebContentSealInstrumentedTest`: un socket in ascolto che conta le connessioni native, il contatore DNS di sistema e il DOM che Chromium costruisce davvero). Girano **solo su emulatore o dispositivo**, non nella suite JVM, e richiedono un dispositivo acceso. Dalla 3.1.1 c'è anche `FluidWebContentRendererLifecycleTest`: 60 documenti aperti e chiusi in un solo processo, contando i processi renderer dell'app (`dumpsys activity services` incrociato con `ps`), che devono tornare al numero di partenza. Fino a K1 le prove che aprono decine di documenti andavano divise in più lanci (`am instrument … -e webChunk i/n`), perché ogni contenitore lasciava un processo renderer vivo fino alla fine del processo di prova; ora il contenitore lo termina alla chiusura e le prove girano intere. `FluidWebContentProfileReuseTest` guarda che cosa passa fra due documenti che usano, uno dopo l'altro, lo stesso profilo. Le lancia tutte `tools/qa/web-network-tests.sh` (installa i due APK di debug, 14 lanci, un processo nuovo per lancio, raccoglie esiti e log, esce 1 se un lancio fallisce). Su API 37 serve Espresso 3.7.0 esplicito (la versione transitiva chiamava un'API rimossa).

## La firma delle app

Le app dell'ecosistema si firmano tutte con la stessa chiave (`pampa.jks`, indicata da `local.properties`:
`pampa.storeFile`, `pampa.storePassword`, `pampa.keyAlias`, `pampa.keyPassword`; **le password non stanno mai nel
repo**; su Windows il percorso si scrive con i due punti protetti, `pampa.storeFile=C\:/cartella/pampa.jks`, altrimenti il lint
segnala `PropertyEscape` su `local.properties`). Senza chiave la release ripiega sulla firma di debug e lo dice: si compila ovunque, ma quella build non è
pubblicabile e **non si installa sopra una installazione vera** (firma diversa → Android rifiuta). Per pubblicare
sullo store: la skill `pampa-store-publish-direct`.

## Il collaudo che conta: guardarla in mano

L'engine è un livello visivo e tattile; i suoi difetti veri non stanno nel compilatore.

- **Una UI si giudica in mano.** Costruire, installare e **guardare** prima di dire che è fatto: uno screenshot di un
  difetto di tatto o di moto non lo prova. Lo dice il progetto: la galleria esiste per questo.
- **Le animazioni si giudicano dai fotogrammi.** Registra (`screenrecord`), estrai con `-fps_mode passthrough`
  (a frame rate variabile `fps=30` riordina e duplica i fotogrammi, e una cronologia sbagliata porta a diagnosi sbagliate),
  guarda **i primi 5** fotogrammi (è lì che stanno i lampi di default e gli scalini), e per i difetti di un fotogramma
  solo la misura batte l'occhio (`signalstats,metadata=print`, luminanza per frame: un lampo è un picco isolato in una
  serie che dovrebbe essere monotona). «Giusto per costruzione» qui non è mai bastato.
- **I contenitori non rimbalzano**; la forma che si trasforma può rimbalzare solo dentro la sua geometria
  (`componenti/fluid-physics.md`).
- Gli strumenti sono in `tools/qa/`: `qa-common.sh` (`qa_record`, `qa_finish` che scarica il video e ne estrae frame e
  luminanza, `qa_bounds` per trovare le coordinate di un nodo, `qa_shot`, `qa_dump`) e `baseline.sh` (la baseline delle
  animazioni su emulatore). Li usano `source tools/qa/qa-common.sh` e `ANDROID_SERIAL` (di serie `emulator-5554`:
  non tocca i dispositivi fisici). `adb` e `ffmpeg` non sono nel PATH: i percorsi sono nel file.
- **Screenshot da dispositivo fisico:** `screencap` + `pull`; il formato telefono su un tablet si ottiene con
  `wm size` (e si ripristina con `wm size reset`).
- Per la **prestazione** (RenderThread, cache GPU) le sorgenti di vetro a schermo intero sempre registrate sono il
  costo da cercare: `componenti/vetro.md`. I rilievi precedenti sono in `artifacts/perf/` (`README.md`).
- **Le pagine strumento della galleria** (solo nella build di debug gli extra; si aprono anche dal catalogo di Chrome),
  ognuna col suo `LEGGIMI.md` in `sample/src/main/kotlin/dev/antigravity/fluidengine/sample/<cartella>/`:
  «Misure» (`--es gallery.page measure`, scene fisse per i tempi di fotogramma), «Materiale» (`material`: il vetro
  nuovo accanto a quello di prima, scena per scena; logcat `FluidMateriale`), «Icone» (`icons`), «Robustezza»
  (`robustness`, con `robustness.case` e `robustness.auto`: la serie fissa della trasformazione universale) e
  «Parità» (`parity`, con `parity.scene piatto|testo|foto`, `parity.theme`, `parity.breathe on`, `parity.model`:
  la stessa forma da `glassSurface`, Fluid-physics ferma, vetro dipinto, pillola e goccia, fianco a fianco; logcat
  `FluidParita`). Le manopole del profilo del vetro si danno con `--es gallery.tune "chiave=valore,…"` (fra cui quelle
  del viaggio: `travel`, `travelclear`, `travelbend`, `travelneck`, `travelclip`; elenco in `componenti/vetro.md`,
  «La diagnostica»), il livello di API simulato con `--es gallery.api 32`.
- **Il banco del vetro** (`tools/glassbench/`): rende sul PC gli shader veri dalle scene scritte dalla ricetta e
  confronta con le immagini di riferimento (`glassbench.py compare --golden classic|liquid`); il rivelatore di scatti
  `glassjerk.py` legge i fotogrammi del tablet e del banco (`tools/glassbench/SCATTI.md`).

## Prima di dire «ho finito»

1. `engine-doctor.ps1 -AppRoot .` (in un'app).
2. La build e i test (`:app:assembleDebug testDebugUnitTest`).
3. Se hai toccato l'engine dentro un'app: o l'hai committato **nel repo dell'engine**, o lo stai per perdere.
4. Hai **guardato** la cosa sul dispositivo? Per un cambio al tatto o a vista, un'installazione e un giro vero.
5. Se hai toccato il codice pubblico dell'engine: `gen_api_catalog.py`, la scheda, `check_docs.py`.
