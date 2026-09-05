---
name: fluid-engine
description: Istruzioni per lavorare con il Fluid Engine, le fondamenta condivise delle app Android di Antigravity (design system Fluid, tema, vetro liquido e morphing Fluid-physics, widget Glance, aggiornamento in-app, feature flag remoti). USARE SEMPRE quando il progetto contiene una cartella engine/ con ENGINE_VERSION, un file engine.properties, o import da dev.antigravity.fluidengine.*. Triggerare anche per richieste come "aggiorna l'engine", "integra l'engine in questa app", "aggiungi un componente all'engine", "aggiungi un feature flag", "nuova versione dell'engine", "il widget non segue il tema", "FluidTheme", "FluidScreen", "engine-ui", "Fluid-physics", "il tasto che diventa un pop-up", "la card che si espande", "morphing", oppure quando si crea una nuova app Android che deve partire dalle stesse fondamenta.
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
engine-ai           l'assistente senza dominio: provider BYOK (Groq, Gemini, OpenRouter), chiavi
                    cifrate, SSE, failover, tipi dei tool, router e orchestratore a livelli, voce
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

### Far trasformare il vetro (Fluid-physics)

Il motore che trasforma qualsiasi silhouette di vetro in qualsiasi altra, con la rifrazione che
segue la forma. Dalla **1.9.7 i preset lo usano già**, quindi la domanda giusta è di solito
*nessuna*: un'app che aggiorna l'engine si ritrova la card che si espande trasformata — la riga
toccata diventa il pop-up e ci ritorna — senza cambiare una riga al call-site.

Serve chiamare qualcosa solo per il tasto che si apre nel proprio menù (`FluidMorphMenuButton` +
`FluidMorphMenuHost` alla radice) o per una forma inventata: `rememberFluidPhysicsState` e
`Modifier.fluidPhysicsSurface`. Vocabolario, tier e la questione della molla stanno in
`references/design-system.md`; le tre trappole del vetro in movimento — e non sono ovvie — in
`references/regole.md`.

Se stai animando una sagoma di vetro, **giudica dai fotogrammi**: la ricetta di cattura e misura è
in fondo a `references/regole.md`. "È giusto per costruzione" qui non è mai bastato.

### Dare un assistente IA a un'app

`engine-ai` porta tutto cio' che non sa cosa fa l'app: i tre provider a chiave dell'utente, il
Keystore, lo stream SSE, il failover, il catalogo dei modelli su tre livelli (router, chat,
profondo), il router dei gruppi di strumenti e l'orchestratore generico `AiOrchestrator<C>`.
L'app scrive **solo** il suo dominio: i gruppi (un enum che implementa `AiToolGroup`), i tool
(`AiTool<C>` col proprio contesto `C`), il prompt, le azioni e la UI. Un tool che sa cos'e' un
voto non entra mai nell'engine. Ricetta, contratto e trappole in `references/ai.md`.

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

Dentro il repo dell'engine, in `skill/fluid-engine/`. Le correzioni si fanno **sempre qui**: se
l'installazione è una copia, una modifica fatta altrove torna indietro al primo aggiornamento.

Come è installata, va verificato prima di toccarla — sono due mondi diversi:

```powershell
Get-Item "$env:USERPROFILE\.claude\skills\fluid-engine" -Force | Select-Object LinkType, Target
```

- **`LinkType: Junction`** (com'è sulla macchina di Alessio, verificato): l'installazione *è* il
  repo. Le modifiche sono già live, non c'è niente da reinstallare — e `Copy-Item` lì sopra
  fallisce sempre, perché copierebbe i file su se stessi.
- **`LinkType` vuoto**: è una copia vera, e va rifatta dopo ogni modifica:

```powershell
Copy-Item -Recurse -Force <engine>\skill\fluid-engine "$env:USERPROFILE\.claude\skills\"
```

## Il piano di adozione in corso

`ADOZIONE.md` alla radice dell'engine tiene l'ordine deciso da Alessio per portare **i componenti**
(non solo il tema) su tutte le app: ClasseViva Expressive, Pampa Store, KeyVoice,
universal_converter, Pampa widgets. Leggilo prima di iniziare una migrazione, e aggiornalo quando
una fase si chiude.

Ci sta dentro anche la distinzione che si sbaglia più spesso: **il tema non porta i componenti.**
`FluidTheme` cambia colori, tipografia, angoli e curve; il vetro, l'overscroll e le liste
raggruppate arrivano solo chiamando `FluidScreen`, `FluidListGroup` e `GlassMaterial`. Un'app che
ha adottato solo il tema si vede quasi identica a prima, e va detto così invece che "ha preso il
design system".

## Riferimenti

- `references/regole.md` — le regole del design system, per esteso, con il perché
- `references/design-system.md` — inventario dei componenti e dei token
- `references/integrazione.md` — mettere l'engine in un'app nuova o esistente
- `references/aggiornamenti.md` — versioni, canali, rilascio, rollback
- `references/config-remota.md` — manifest, flag, kill switch, compatibilità
- `references/widget.md` — il kit Glance
- `references/limiti.md` — cosa non si può fare, e cosa rispondere quando viene chiesto
- `references/ai.md` — l'assistente: cosa da' `engine-ai`, cosa resta all'app, come si scrive un tool
