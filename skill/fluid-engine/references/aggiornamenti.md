# Versioni, rilascio, aggiornamento

## Il pin

Ogni app ha `engine.properties` alla radice del progetto Android:

```properties
engine.version=1.0.0
engine.channel=stable
engine.path=engine
engine.updatedAt=2026-08-23
```

È versionato nel repo dell'app. `git log engine.properties` racconta quale app aveva quale engine.

## Aggiornare una app

```powershell
powershell -ExecutionPolicy Bypass -File engine\tools\engine-update.ps1 -AppRoot . -Version 1.1.0
# oppure -Latest, che prende il tag più alto sul canale indicato in engine.properties
```

Lo script si rifiuta di partire se l'engine ha modifiche non committate, fa `fetch` + `checkout` del
tag, riscrive `engine.properties` e stampa il changelog fra le due versioni con le voci **BREAKING**
in evidenza. Non compila e non committa.

Dopo, sempre:

```bash
./gradlew.bat --no-daemon :app:assembleDebug testDebugUnitTest
git add engine engine.properties
git commit -m "engine: 1.0.0 -> 1.1.0"
```

Committare `engine` (il pin del submodule) e `engine.properties` **insieme**: separati, il repo dice
due cose diverse.

## Aggiornare tutte le app

```powershell
powershell -ExecutionPolicy Bypass -File tools\engine-update-all.ps1 -Root "C:\VibeCoded Projects" -Version 1.1.0 -WhatIf
```

`-WhatIf` prima, sempre. `-Build` compila ognuna e si ferma alla prima che fallisce, lasciando intatte
quelle non ancora toccate.

## Rollback

Lo stesso comando con la versione precedente. Funziona perché il pin è un tag e **i tag non si
spostano**: se una versione è sbagliata, se ne pubblica una nuova, non si sposta quella vecchia.

## Tagliare una versione dell'engine

Dentro il repo dell'engine, con l'albero pulito:

```powershell
powershell -ExecutionPolicy Bypass -File tools\engine-release.ps1 -Version 1.1.0 -Notes "Cosa è cambiato" -Tag
git push ; git push --tags
```

Con più voci di changelog serve `-Command` invece di `-File` (PowerShell non costruisce array
passando per `-File`):

```powershell
powershell -ExecutionPolicy Bypass -Command "& .\tools\engine-release.ps1 -Version 1.1.0 -Notes 'Voce uno','BREAKING: voce due' -Tag"
```

Lo script muove insieme `ENGINE_VERSION`, `EngineBuild.VERSION` e il tag. Non toccarli a mano.

### Cosa merita quale numero

- **patch** — correzioni, nessuna firma cambiata;
- **minor** — roba nuova, il codice esistente continua a compilare;
- **major** — qualcosa è cambiato o sparito; il changelog dice cosa e come si migra, con la voce
  marcata `BREAKING:`.

Un rename di un componente pubblico è **major**, anche se "è solo un nome": rompe le app che
aggiornano.

## Lavorare sull'engine da dentro un'app

Capita di accorgersi che manca un componente mentre si fa una schermata. `engine/` è un repo git
normale:

```bash
cd android/engine
git checkout -b feature/x
# modifichi; l'app ricompila subito, è un include sorgente
git commit -am "ui: aggiunge FluidX"
git push -u origin feature/x
```

Poi si taglia una versione e si aggancia l'app a quella. **Non lasciare modifiche non committate
dentro `engine/`**: al prossimo aggiornamento spariscono.

## Compatibilità a runtime

`EngineBuild.VERSION` è compilata nell'app. Il manifest remoto può alzare il pavimento:

```kotlin
when (remoteConfig.compatibility()) {
  EngineCompatibility.OK -> Unit
  EngineCompatibility.UPDATE_RECOMMENDED -> avvisoDiscreto()
  EngineCompatibility.UPDATE_REQUIRED -> schermataAggiorna()
}
```

È così che una modifica lato manifest raggiunge le build già installate: non cambia il loro codice,
ma può dire loro che il codice che hanno non è più quello giusto.
