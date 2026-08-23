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
```

## Partire in cinque minuti

Da dentro il repo dell'app:

```powershell
git submodule add <url-del-repo-engine> engine
powershell -ExecutionPolicy Bypass -File engine/tools/engine-install.ps1 -AppRoot .
```

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

## Documentazione

| | |
|---|---|
| [01 · Integrazione](docs/01-integrazione.md) | mettere l'engine dentro un'app, nuova o esistente |
| [02 · Aggiornamento](docs/02-aggiornamento.md) | versioni, canali, aggiornare una app o tutte |
| [03 · Design system](docs/03-design-system.md) | cosa c'è in `engine-ui` e le regole per usarlo |
| [04 · Config remota](docs/04-config-remota.md) | manifest, flag, kill switch |
| [05 · Widget](docs/05-widget.md) | il kit Glance |
| [06 · Limiti](docs/06-limiti.md) | cosa si aggiorna da remoto e cosa no, senza giri di parole |

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

## Font

`engine-ui` include Inter (Rasmus Andersson) sotto SIL Open Font License 1.1 — vedi `LICENSES/Inter.md`. Il file di licenza va distribuito insieme all'app.
