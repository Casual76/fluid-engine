# Fluid Engine

Le fondamenta condivise delle app Android di Antigravity: il design system completo (tema, tipografia, motion, componenti), la persistenza delle impostazioni, l'aggiornamento in-app fuori dallo store, la configurazione remota con i feature flag e il kit per i widget della home.

Non è una libreria generica: è **il** livello su cui è costruita ClasseViva Expressive dalla 7.0.0, estratto per essere riusato senza ricopiarlo a mano ogni volta.

```
engine-foundation   modelli, versioni, manifest remoto, flag      (nessuna dipendenza da Compose)
engine-ui           il design system Fluid: tema, tipografia, motion, componenti
engine-storage      DataStore per le impostazioni dell'engine e la cache del manifest
engine-net          le due chiamate HTTP che l'engine fa da solo (leggi documento, scarica file)
engine-config       il manifest remoto: feature flag, kill switch, versione minima
engine-update       aggiornamento in-app via PackageInstaller (modello Pampa Store)
engine-widget       palette e componenti Glance con lo stesso aspetto dell'app
engine-ai           l'assistente senza dominio: provider BYOK, chiavi, SSE, failover, orchestratore
```

## Partire in cinque minuti

Da dentro il repo dell'app:

```powershell
git submodule add <url-del-repo-engine> engine
powershell -ExecutionPolicy Bypass -File engine/tools/engine-install.ps1 -AppRoot .
```

Finche' l'engine e' una cartella locale e non un repo remoto, git rifiuta il trasporto `file` e
serve `git -c protocol.file.allow=always submodule add ...`; senza git c'e' `-Mode copy`.

Lo script aggiunge i moduli al `settings.gradle`, scrive `engine.properties` con la versione agganciata e stampa le righe da incollare nelle dipendenze. Poi:

```kotlin
FluidTheme(settings = engineSettings) {
  // tutta l'app
}
```

Il resto è in [`docs/01-integrazione.md`](docs/01-integrazione.md).

## Aggiornare

Una app per volta:

```powershell
powershell -ExecutionPolicy Bypass -File engine/tools/engine-update.ps1 -AppRoot . -Version 1.1.0
```

Tutte insieme:

```powershell
powershell -ExecutionPolicy Bypass -File tools/engine-update-all.ps1 -Root "C:\VibeCoded Projects" -Version 1.1.0
```

Ogni app resta agganciata alla propria versione: aggiornarne una non tocca le altre. Il modello completo — canali, compatibilità, rollback — è in [`docs/02-aggiornamento.md`](docs/02-aggiornamento.md).

## Cambiare comportamento senza ricompilare

Un solo file JSON ospitato (lo stesso del Pampa Store, in una sezione nuova) decide feature flag, versione minima dell'engine, avvisi e kill switch per **tutte** le app che lo leggono. Vedi [`docs/04-config-remota.md`](docs/04-config-remota.md), e soprattutto [`docs/06-limiti.md`](docs/06-limiti.md): il codice non si aggiorna da remoto, e il documento spiega esattamente dove passa il confine.

## La skill per gli agenti

`skill/fluid-engine/` e' la skill che insegna a un agente le regole del design system, l'inventario
dei componenti, come si integra e come si aggiorna. Sta dentro questo repo di proposito: cosi' non
puo' divergere dall'engine che descrive.

```powershell
Copy-Item -Recurse -Force skillluid-engine "$env:USERPROFILE\.claude\skills\"
```

## Documentazione

| | |
|---|---|
| [01 · Integrazione](docs/01-integrazione.md) | mettere l'engine dentro un'app, nuova o esistente |
| [02 · Aggiornamento](docs/02-aggiornamento.md) | versioni, canali, aggiornare una app o tutte |
| [03 · Design system](docs/03-design-system.md) | cosa c'è in `engine-ui` e le regole per usarlo |
| [04 · Config remota](docs/04-config-remota.md) | manifest, flag, kill switch |
| [05 · Widget](docs/05-widget.md) | il kit Glance |
| [06 · Limiti](docs/06-limiti.md) | cosa si aggiorna da remoto e cosa no, senza giri di parole |

## Fluid-physics e l'app Fluid Glass

Dalla 1.9.0 l'engine ha **Fluid-physics** (`engine-ui`, package `ui/fluidphysics`): qualsiasi
forma di vetro diventa qualsiasi altra — rettangolo↔cerchio, sagome disegnate a mano, gruppi di
pezzi che si fondono con ponti liquidi — con la rifrazione che segue la sagoma mentre viaggia.
API: `FluidForm` + `rememberFluidPhysicsState` + `Modifier.fluidPhysicsSurface`; dettagli in
`docs/03-design-system.md`.

Il modulo `sample/` è il suo banco di prova e, dalla 1.0.0, un'app vera del Pampa Store:
**Fluid Glass** (`dev.antigravity.fluidglass`, manifest in `manifest.json`). La scheda Playground
è l'editor del motore: preset, disegno a mano libera, molle e livelli di qualità dal vivo, e il
lato pratico — due tasti che diventano un menù che diventa un pop-up. Chi aveva installato la
vecchia galleria firmata debug deve disinstallarla una volta: la firma è cambiata.

## Sviluppare l'engine

L'engine si apre e si compila da solo:

```bash
./gradlew.bat --no-daemon build
```

Serve un `local.properties` con `sdk.dir` (Android Studio lo scrive da solo aprendo la cartella):

```properties
sdk.dir=C\:\\Android\\Sdk
```

(In un file `.properties` il backslash e i due punti vanno scritti cosi'.)

`settings.gradle` e `build.gradle` alla radice servono **solo** a questo. Quando l'engine è dentro un'app, è il build dell'app a comandare e quei due file non vengono letti.

## Versione

Vedi `ENGINE_VERSION` e `CHANGELOG.md`. La stessa stringa è compilata in `EngineBuild.VERSION`: è quella che il manifest remoto confronta per decidere se una build è troppo vecchia. `tools/engine-doctor.ps1` verifica che le due non divergano.

## Crediti e licenze di terze parti

`engine-ui` distribuisce dentro l'APK due opere di altri, e le rispettive licenze chiedono che
l'avviso viaggi con la distribuzione. `FluidEngineLicenses` e `fluidLicensesSection()` mettono
l'elenco nella schermata "informazioni" di ogni app senza doverlo riscrivere cinque volte.

| | | |
|---|---|---|
| [AndroidLiquidGlass (`backdrop`)](https://github.com/Kyant0/AndroidLiquidGlass) | Kyant, Copyright 2025 | Apache-2.0 — [`LICENSES/AndroidLiquidGlass.md`](LICENSES/AndroidLiquidGlass.md) |
| [Inter](https://github.com/rsms/inter) | Rasmus Andersson | SIL OFL 1.1 — [`LICENSES/Inter.md`](LICENSES/Inter.md) |

**Il vetro è di Kyant.** Tutta la rifrazione del Fluid Glass — cattura dello sfondo, lente, bordo
speculare, spessore — è la sua libreria `backdrop`, copiata come sorgente in
`engine-ui/.../ui/glass/backdrop/`. Il perché della copia, e l'elenco completo delle modifiche, sono
nel file di licenza.

Che quella fosse la strada giusta l'abbiamo capito guardando
**[Square](https://github.com/Lelonio/Square)** di [@Lelonio](https://github.com/Lelonio): un client
musicale Android costruito interamente su quel vetro, e la prima implementazione convincente di
Liquid Glass su Android che avessimo visto. Da Square non abbiamo preso codice — è GPL-3.0, e il suo
vetro è la stessa libreria Apache-2.0 che abbiamo preso direttamente da monte — ma il debito è reale
ed è giusto scriverlo.
