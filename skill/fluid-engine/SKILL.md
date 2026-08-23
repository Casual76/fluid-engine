---
name: fluid-engine
description: Istruzioni per lavorare con il Fluid Engine, le fondamenta condivise delle app Android di Antigravity (design system Fluid, tema, widget Glance, aggiornamento in-app, feature flag remoti). USARE SEMPRE quando il progetto contiene una cartella engine/ con ENGINE_VERSION, un file engine.properties, o import da dev.antigravity.fluidengine.*. Triggerare anche per richieste come "aggiorna l'engine", "integra l'engine in questa app", "aggiungi un componente all'engine", "aggiungi un feature flag", "nuova versione dell'engine", "il widget non segue il tema", "FluidTheme", "FluidScreen", "engine-ui", oppure quando si crea una nuova app Android che deve partire dalle stesse fondamenta.
---

# Fluid Engine

Le fondamenta condivise delle app Android di Antigravity, estratte da ClasseViva Expressive 7.x. Un
repo a sé, agganciato a un tag dentro ogni app che lo usa.

Questa skill dice **come lavorarci senza romperlo**. Prima di modificare qualsiasi cosa, leggi
`references/regole.md`: sono poche e sono tutte deliberate.

## Riconoscere la situazione

| segnale | dove sei |
|---|---|
| esiste `engine/ENGINE_VERSION` e `engine.properties` alla radice | in un'**app** che usa l'engine |
| esiste `ENGINE_VERSION` alla radice e le cartelle `engine-*` | **dentro l'engine** |
| c'e' `engine.properties` con `engine.mode=port` ma nessuna cartella dell'engine | in un'app che ha una **copia** del look: `references/integrazione.md`, sezione "Il porto" |
| nessuno dei due, ma serve partire da queste fondamenta | vai a `references/integrazione.md` |

La distinzione conta: **il codice dentro `engine/` non è codice dell'app.** Modificarlo lì e non
committarlo nel repo dell'engine crea una variante silenziosa che sparisce al primo aggiornamento.

## La mappa

Non entrano tutti in ogni app: `engine-install.ps1 -Modules engine-update` include solo quello che
serve, chiudendo da se' le dipendenze. Serve davvero — un'app senza il plugin Compose nel build root
non riesce nemmeno a *configurare* `engine-ui`, e un modulo che non configura ferma tutto il build.

```
engine-foundation   modelli, versioni, manifest remoto, flag — niente Compose, niente Android UI
engine-ui           design system Fluid: FluidTheme, FluidScreen, componenti, motion, tipografia
engine-storage      DataStore: EngineSettingsStore, cache del manifest
engine-net          EngineHttp: leggi un documento, scarica un file
engine-config       EngineRemoteConfig: feature flag, versione minima, kill switch
engine-update       EngineAppUpdater: aggiornamento in-app via PackageInstaller
engine-widget       palette e componenti Glance con lo stesso aspetto dell'app
```

Tutto sotto `dev.antigravity.fluidengine.*`.

## Le cinque regole che non si negoziano

1. **Nessun colore, dimensione di testo o raggio scritto a mano** in una schermata. Colori da
   `MaterialTheme.colorScheme`, testo da `MaterialTheme.typography` o `FluidTextStyles`, angoli da
   `FluidRadius` con `ContinuousCornerShape` (mai `RoundedCornerShape`), animazioni da `FluidMotion`.
2. **Le liste sono raggruppate.** `FluidListGroup` + `FluidListRow` + `FluidListDivider`. Il colore
   della categoria sta sulla piastrella dell'icona, mai sullo sfondo della riga.
3. **Una pagina in arrivo non sfuma mai.** Le transizioni di rotta sono opache e laterali. Se stai
   scrivendo un `fadeIn` fra due schermate, stai andando contro il vocabolario dell'app.
4. **`engine-foundation` non conosce Android UI, e nessun modulo dell'engine conosce il dominio
   dell'app.** Un componente che sa cos'è un voto o una materia non è un componente dell'engine.
5. **Il codice non si aggiorna da remoto.** Il manifest cambia comportamento (flag, versione minima,
   avvisi, kill switch) e a quale APK punta l'aggiornamento. Il resto richiede una build. Non
   proporre mai `DexClassLoader`, dex scaricati o simili: vedi `references/limiti.md`.

## Compiti frequenti

### Installare l'engine in un'app

**Prima verifica che ci entri**, con i cinque controlli in `references/integrazione.md`: che Compose
usa (androidx o Multiplatform), se Compose c'e' proprio, AGP e compileSdk, minSdk, e se la cartella
`engine/` e' libera. Un'app in Compose Multiplatform non puo' ospitare `engine-ui`, e forzarla non
porta da nessuna parte: dillo e fermati.

Poi submodule, `engine-install.ps1`, dipendenza, tema. Il tema si adotta **riscrivendo il corpo**
della funzione di tema che l'app ha gia', senza toccare i punti di chiamata — ricetta completa in
`references/integrazione.md`.

### Aggiungere una schermata a un'app

```kotlin
FluidScreen(title = "Titolo", subtitle = "Sottotitolo") {
  item { FluidSectionHeader(title = "Sezione") }
  item {
    FluidListGroup {
      FluidListRow(title = "…", subtitle = "…", onClick = { })
      FluidListDivider()
      FluidListRow(title = "…", subtitle = "…", onClick = { })
    }
  }
}
```

Il resto dell'inventario dei componenti è in `references/design-system.md`.

### Aggiungere un componente all'engine

Solo se è **generico**. Va in `engine-ui`, con il prefisso `Fluid`, con un KDoc che dice *perché* è
fatto così e non solo cos'è, e con un test se ha logica (contrasto, misura, stato). Poi si taglia
una versione dell'engine e si aggancia l'app: vedi `references/aggiornamenti.md`.

Se è specifico dell'app — conosce il dominio, i suoi dati, le sue schermate — resta nell'app.

### Aggiungere un feature flag

```kotlin
val NuovaAgenda = EngineFlag(key = "nuovaAgenda", default = false)   // default = come si comporta oggi
```

Poi `remoteConfig.flag(NuovaAgenda)` e la voce nel manifest ospitato. Il default è la cosa
importante: senza rete, l'app deve comportarsi esattamente come il giorno in cui è uscita. Dettagli
in `references/config-remota.md`.

### Aggiornare l'engine di un'app

```powershell
powershell -ExecutionPolicy Bypass -File engine\tools\engine-update.ps1 -AppRoot . -Version 1.1.0
```

Se l'app e' un porto (`engine.mode=port`), lo script **non tocca niente** e lo dice: un porto lo
aggiorna una persona, riportando le modifiche, e poi alza `FLUID_PORT_OF`. Alzare quel numero senza
aver riportato niente e' l'unico modo di rendere il meccanismo inutile.

Poi compila, leggi le voci **BREAKING** che lo script stampa, e committa `engine` + `engine.properties`
insieme. Non committare mai solo uno dei due.

### Tagliare una versione dell'engine

```powershell
powershell -ExecutionPolicy Bypass -File tools\engine-release.ps1 -Version 1.1.0 -Notes "…" -Tag
```

Muove insieme `ENGINE_VERSION`, `EngineBuild.VERSION` e il tag git. Non modificarli a mano: divergono,
e `EngineBuild.VERSION` è quella su cui il manifest remoto giudica le build installate.

### "Il widget non segue il tema"

Quasi sempre manca il collegamento fra le impostazioni e `updateAll`: il widget si ridisegna quando
cambiano i **dati**, non quando cambia l'**aspetto**. Vedi `references/widget.md`.

## Prima di dire che hai finito

```powershell
powershell -ExecutionPolicy Bypass -File engine\tools\engine-doctor.ps1 -AppRoot .
```

```bash
./gradlew.bat --no-daemon :app:assembleDebug testDebugUnitTest
```

E se hai toccato l'engine dentro un'app: o l'hai committato nel repo dell'engine, o lo stai per
perdere. `engine-doctor` te lo dice, ma solo se lo esegui.

## Dove vive questa skill

Dentro il repo dell'engine, in `skill/fluid-engine/`. E' installata copiandola in
`~/.claude/skills/fluid-engine`, quindi **la copia installata e' una copia**: le correzioni vanno
fatte nel repo dell'engine e poi reinstallate, altrimenti al primo aggiornamento tornano indietro.

```powershell
Copy-Item -Recurse -Force <engine>\skillluid-engine "$env:USERPROFILE\.claude\skills\"
```

## Riferimenti

- `references/regole.md` — le regole del design system, per esteso, con il perché
- `references/design-system.md` — inventario dei componenti e dei token
- `references/integrazione.md` — mettere l'engine in un'app nuova o esistente
- `references/aggiornamenti.md` — versioni, canali, rilascio, rollback
- `references/config-remota.md` — manifest, flag, kill switch, compatibilità
- `references/widget.md` — il kit Glance
- `references/limiti.md` — cosa non si può fare, e cosa rispondere quando viene chiesto
