# 02 · Aggiornamento

Questo è il documento che risponde alla domanda "aggiorno l'engine una volta e lo prendono tutte le app?".

La risposta onesta è: **sì per il comportamento, no per il codice** — ma per il codice l'aggiornamento è un comando per app, non un lavoro per app. I due piani sono separati apposta.

| | come si aggiorna | quando arriva |
|---|---|---|
| Codice dell'engine (componenti, tema, logica) | `engine-update.ps1` cambia il tag agganciato, poi si ricompila e si pubblica l'app | alla prossima release dell'app |
| Comportamento (flag, versione minima, avvisi, kill switch) | si modifica un file JSON ospitato | al prossimo controllo dell'app, senza ricompilare niente |

Il perché del "no" sta in [`06-limiti.md`](06-limiti.md). Qui c'è il "come".

## Il pin

Ogni app ha un `engine.properties` alla radice del progetto Android:

```properties
engine.version=1.0.0
engine.channel=stable
engine.path=engine
engine.updatedAt=2026-08-23
```

È l'unica cosa che dice quale engine usa quell'app. È versionata nel repo dell'app, quindi la storia di quale app aveva quale engine è ricostruibile con `git log`.

## Aggiornare una app

```powershell
powershell -ExecutionPolicy Bypass -File engine/tools/engine-update.ps1 -AppRoot . -Version 1.1.0
```

Lo script:

1. si rifiuta di partire se l'engine ha modifiche locali non committate (altrimenti le perderesti);
2. fa `git fetch` e `git checkout engine-1.1.0` dentro la cartella dell'engine;
3. riscrive `engine.properties`;
4. stampa le voci del `CHANGELOG.md` fra la versione vecchia e la nuova, comprese quelle marcate **BREAKING**.

Non compila e non committa: quello lo decidi tu, dopo aver letto cosa è cambiato.

```powershell
./gradlew.bat --no-daemon :app:assembleDebug testDebugUnitTest
git add engine engine.properties
git commit -m "engine: 1.0.0 -> 1.1.0"
```

## Aggiornare tutte le app insieme

```powershell
powershell -ExecutionPolicy Bypass -File engine/tools/engine-update-all.ps1 -Root "C:\VibeCoded Projects" -Version 1.1.0
```

Cerca ogni progetto sotto `-Root` che abbia un `engine.properties`, e aggiorna quelli. Con `-WhatIf` dice soltanto cosa farebbe; con `-Build` compila ognuno dopo l'aggiornamento e si ferma alla prima app che non compila, lasciando le altre come stavano.

Questo è il "toggle" per il codice: un comando, tutte le app, e ognuna resta comunque libera di restare indietro perché il pin è suo.

## Canali

`engine.channel` vale `stable` o `beta`, e serve a `engine-update.ps1 -Latest`: con `stable` prende il tag `engine-*` più alto senza suffisso, con `beta` accetta anche `engine-1.2.0-beta1`. Il canale dell'engine non ha niente a che vedere con il canale di release dell'app: un'app stable può essere costruita su un engine beta mentre lo si prova.

## Versioni e compatibilità

Le versioni dell'engine sono semantiche e la regola è quella ovvia:

- **patch** (1.0.0 → 1.0.1): correzioni, niente cambia nelle firme;
- **minor** (1.0.0 → 1.1.0): roba nuova, il codice esistente continua a compilare;
- **major** (1.0.0 → 2.0.0): qualcosa è cambiato o sparito, il `CHANGELOG` dice cosa e come si migra.

La versione è scritta in tre posti che devono restare d'accordo: il file `ENGINE_VERSION`, la costante `EngineBuild.VERSION` e il tag git. `engine-release.ps1` li muove insieme, `engine-doctor.ps1` verifica che non abbiano divorziato.

`EngineBuild.VERSION` è compilata dentro l'app, ed è quella che il manifest remoto confronta:

```kotlin
when (config.compatibility()) {
  EngineCompatibility.OK -> Unit
  EngineCompatibility.UPDATE_RECOMMENDED -> mostraAvvisoDiscreto()
  EngineCompatibility.UPDATE_REQUIRED -> mostraSchermataAggiornaApp()
}
```

Così una modifica al manifest raggiunge davvero tutte le app installate: non cambia il loro codice, ma può dire loro che il codice che hanno non è più quello giusto — e questo è l'unico modo stabile e conforme di ritirare una build vecchia dal campo.

## Tornare indietro

```powershell
powershell -ExecutionPolicy Bypass -File engine/tools/engine-update.ps1 -AppRoot . -Version 1.0.0
```

Il rollback è identico all'aggiornamento, perché il pin è un tag e i tag non si muovono. Per questo non va mai spostato un tag già pubblicato: si pubblica una patch nuova.

## Modificare l'engine mentre lavori su un'app

Capita: stai facendo una schermata e ti accorgi che manca un componente. La cartella `engine/` è un repo git normale, quindi:

```powershell
cd android/engine
git checkout -b feature/nuovo-componente
# modifichi, l'app ricompila subito perché è un include sorgente
git commit -am "ui: aggiunge FluidSomething"
git push -u origin feature/nuovo-componente
```

Poi si taglia una versione dell'engine e si aggancia l'app a quella. Quello che **non** va fatto è lasciare modifiche non committate dentro `engine/`: `engine-doctor.ps1` le segnala proprio perché sono il modo tipico in cui una copia condivisa smette di essere condivisa senza che nessuno se ne accorga.
