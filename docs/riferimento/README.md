<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/SKILL.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->


# Fluid Engine

Le fondamenta condivise delle app Android di Antigravity, estratte da ClasseViva Expressive. Un repo a sé,
agganciato a un tag dentro ogni app che lo usa. **Versione documentata: 3.1.0** (le schede della 3.1.0 sono scritte dal ramo di Pampa Mail; il catalogo API e il changelog seguono al rilascio).

Questa skill è la documentazione dell'engine **e** le regole per lavorarci senza romperlo. Non è un riassunto:
dietro c'è una scheda per ogni famiglia di componenti e per ogni modulo, e un catalogo di tutte le API pubbliche
generato dal codice. Il tuo lavoro è **trovare la pagina giusta e leggerla**, non ricordare a memoria.

**Prima di modificare qualsiasi cosa leggi `regole.md`**: sono poche e sono tutte deliberate.

## Come si naviga

```
SKILL.md                      <- sei qui: situazione, mappa, regole, indice
references/
  regole.md                   le regole del design system, col perché
  mappa.md                    moduli, dipendenze, cosa installare, dove sta il codice
  ricette.md                  i compiti frequenti, passo per passo
  risoluzione-problemi.md     sintomo -> causa -> rimedio
  strumenti-e-qa.md           script, build, collaudo sul dispositivo
  storia.md  glossario.md     come siamo arrivati qui; le parole dell'engine
  integrazione.md  aggiornamenti.md  config-remota.md  widget.md  limiti.md  ai.md
  design-system.md            inventario storico di engine-ui (le schede sotto lo sostituiscono, resta per il contesto)
  componenti/                 UNA SCHEDA PER FAMIGLIA di engine-ui (scopo, uso, comportamento, trappole)
  moduli/                     UNA SCHEDA PER MODULO senza UI o opt-in (foundation ... ai-ui, wear, richtext)
  api/                        catalogo GENERATO: ogni dichiarazione pubblica con la firma
scripts/                      gen_api_catalog.py, check_docs.py, sync_docs.py
```

Regola pratica: **la scheda spiega, il catalogo elenca, il sorgente decide.** Cerca il nome nel catalogo
(`grep -rn "FluidSlider" references/api/`), leggi la scheda della famiglia, e quando serve un numero o un
comportamento non scritto apri il file Kotlin indicato nella scheda («Dove guardare»): il KDoc spiega il perché.

## Riconoscere la situazione

| segnale | dove sei |
|---|---|
| esiste `engine/ENGINE_VERSION` e `engine.properties` alla radice | in un'**app** che usa l'engine |
| esiste `ENGINE_VERSION` alla radice e le cartelle `engine-*` | **dentro l'engine** |
| `engine.properties` con `engine.mode=port` ma nessuna cartella dell'engine | in un'app con una **copia** del look: `integrazione.md`, «Il porto» |
| nessuno dei due, ma serve partire da queste fondamenta | `integrazione.md`, poi `ricette.md` |

La distinzione conta: **il codice dentro `engine/` non è codice dell'app.** Modificarlo lì e non committarlo nel
repo dell'engine crea una variante silenziosa che sparisce al primo aggiornamento.

## La mappa

Non entrano tutti in ogni app: `engine-install.ps1 -Modules …` include solo quello che serve e chiude da sé le
dipendenze (un'app senza il plugin Compose non riesce nemmeno a *configurare* `engine-ui`, e un modulo che non
configura ferma tutto il build). Tutto sotto `dev.antigravity.fluidengine.*`. Dettaglio, dipendenze e toolchain:
`mappa.md`.

| modulo | cosa porta | dove si legge |
|---|---|---|
| `engine-foundation` | modelli, versioni, manifest remoto, flag, impostazioni — niente Compose | `moduli/foundation.md` |
| `engine-ui` | il design system Fluid: tema, componenti, vetro, Fluid-physics, pagine, aptica, tutorial | `componenti/` |
| `engine-storage` | DataStore: impostazioni, cache del manifest | `moduli/storage.md` |
| `engine-net` | `EngineHttp`: leggi un documento, scarica un file | `moduli/net.md` |
| `engine-config` | `EngineRemoteConfig`: flag, versione minima, avvisi, kill switch | `moduli/config.md` |
| `engine-update` | aggiornamento in-app via PackageInstaller | `moduli/update.md` |
| `engine-widget` | palette e componenti Glance con lo stesso aspetto | `moduli/widget.md` |
| `engine-wear` | il design system su Wear OS (**opt-in**: solo nel modulo dell'orologio) | `moduli/wear.md` |
| `engine-ai` | l'assistente senza dominio: provider BYOK, chiavi, SSE, failover, router e orchestratore, voce | `moduli/ai-provider.md`, `ai-orchestrator.md`, `ai-chiavi-rete-voce.md` |
| `engine-ai-ui` | l'interfaccia dell'assistente: overlay, chat, impostazioni, halo (**opt-in**) | `moduli/ai-ui*.md` |
| `engine-ai-bridge` | tool federati fra app (ContentProvider che verifica la firma di chi chiama) | `moduli/ai-bridge.md` |
| `engine-richtext` | l'editor di testo con formattazione (elenchi e citazioni annidati, link, barra sopra la tastiera), HTML ristretto, Markdown, testo (**opt-in**, dalla 3.1.0) | `moduli/richtext.md` |

## Che componente uso per…

La tabella «Scegliere in fretta» sta in testa a ogni scheda; questo è l'indirizzamento.

| ho bisogno di… | scheda |
|---|---|
| una schermata, titolo grande che si ritira, refresh, schermi larghi, indice laterale, scroll veloce | `componenti/schermata.md` |
| barra delle schede a pillola (con segni sulle zone), rail, capsula di azioni (anche accese/spente), campo di ricerca | `componenti/barre.md` |
| righe, liste raggruppate, card, hero, skeleton, Markdown, visore di immagini, riordino | `componenti/contenuto.md` (indice) → `contenuto-righe-liste.md`, `contenuto-testo-e-media.md` |
| una lista **lunga** che cambia mentre la si legge, righe con gesto di lato, «3 nuove in alto», stato vuoto con azione | `componenti/contenuto-righe-liste.md`, «Liste lunghe, una riga per elemento» |
| l'avatar di una persona, i colori stabili, il badge «3 non lette», la selezione multipla (anche «Seleziona tutto» paginato), un allegato | `componenti/contenuto-testo-e-media.md`, «Identità e selezione», «File» |
| mostrare un HTML ricevuto (mail, newsletter) in una vista chiusa | `componenti/contenuto-web.md` |
| una ciambella, barre o un andamento con legenda | `componenti/grafici.md` |
| scrivere testo formattato (elenchi, link, citazioni, HTML/Markdown) | `moduli/richtext.md` (opt-in) |
| tasti, segmentato, chip, campo di testo, campo a gettoni (destinatari, etichette), avatar come azione della barra | `componenti/controlli.md` |
| interruttore, cursore, scrubber (i controlli a gesto) | `componenti/controlli-gesto.md` |
| pop-up, foglio, avviso, conferma, portale, pannello laterale (`FluidDrawer`), scelta di data e ora | `componenti/menu-modali.md` |
| menu che si trasforma, azioni della barra, menu contestuale | `componenti/menu-liquidi.md` |
| notifiche contestuali (la card che si tocca; azioni in linea, sostituzione per gruppo, esito) | `componenti/notifiche.md` |
| pressione, gesto, molle, movimento ridotto | `componenti/movimento.md` |
| vibrazioni | `componenti/aptica.md` |
| il materiale di vetro, i ruoli, il costo | `componenti/vetro.md`; la libreria backdrop di Kyant0: `vetro-backdrop.md` |
| una forma che si trasforma in un'altra (la card che si espande, il tasto che diventa menu) | `componenti/fluid-physics.md`; il motore dentro: `fluid-physics-motore.md` |
| più pagine, migrare da `NavHost` | `componenti/pagine.md` |
| back col dito, `BackHandler`, l'ordine del back (menu → pannello → selezione → pagina), barra unica e ancorata | `componenti/pagine-back-e-barra.md` |
| colori, tipografia, angoli, tema, accento, la palette d'identità | `componenti/tema.md` |
| sezione aspetto, aggiornamento in-app, parole traducibili, licenze | `componenti/impostazioni.md` |
| suggerimenti al primo uso | `componenti/tutorial.md` |
| un orologio | `moduli/wear.md` |
| un assistente IA | `ai.md`, `moduli/ai-provider.md`, `ai-orchestrator.md`, `ai-chiavi-rete-voce.md`, e l'interfaccia in `moduli/ai-ui.md` (+ `ai-ui-overlay`, `-chat`, `-impostazioni`, `-stringhe`) |

## Le cinque regole che non si negoziano

Il perché di ciascuna, i numeri e le eccezioni sono in `regole.md`.

1. **Nessun colore, dimensione di testo o raggio scritto a mano** in una schermata. Colori da
   `MaterialTheme.colorScheme`, testo da `MaterialTheme.typography` o `FluidTextStyles`, angoli da `FluidRadius` con
   `ContinuousCornerShape` (mai `RoundedCornerShape`), animazioni da `FluidMotion`.
2. **Le liste sono raggruppate.** `FluidListGroup` + `FluidListRow` + `FluidListDivider`. Il colore della categoria
   sta sulla piastrella dell'icona, mai sullo sfondo della riga.
3. **Una pagina in arrivo non sfuma mai.** Le pagine si muovono con `FluidNavHost` (al posto di `NavHost`): opache,
   da destra, con la pagina sotto che arretra di un terzo; il back è quello di iOS sulla **posizione vera del dito**
   (`touchX`, mai il `progress` del gesto di sistema) e parte dal bordo. Un `fadeIn` fra due schermate, o le
   transizioni di un `NavHost` o di un `FluidRouteMotionHost` (deprecato), vanno contro il vocabolario dell'app.
   Dentro un host la barra è **una sola, ferma**: la pagina la descrive, non se ne disegna una sua.
   **Dalla 3.0 l'engine include codice GPL-3.0 di LibrePods** (pulsanti, pagine, aptica; `LICENSES/LibrePods.md`):
   un'app che lo aggiorna è un'opera derivata, si distribuisce sotto GPL-3.0 con i sorgenti a chi riceve l'APK, e
   la sua pagina «informazioni» chiama `fluidLicensesSection()`. Dillo **prima** di migrare.
4. **`engine-foundation` non conosce Android UI, e nessun modulo dell'engine conosce il dominio dell'app.** Un
   componente che sa cos'è un voto o una materia non è un componente dell'engine.
5. **Il codice non si aggiorna da remoto.** Il manifest cambia comportamento (flag, versione minima, avvisi, kill
   switch) e a quale APK punta l'aggiornamento; il resto richiede una build. Mai `DexClassLoader` o dex scaricati:
   `limiti.md`.

Sempre vero e facile da dimenticare: **il tema non porta i componenti.** `FluidTheme` cambia colori, tipografia,
angoli e curve; il vetro, l'overscroll e le liste raggruppate arrivano solo chiamando `FluidScreen`,
`FluidListGroup` e il materiale. Un'app con solo il tema si vede quasi identica a prima: dillo così.

## Compiti frequenti

Ogni compito ha la sua ricetta con i passi e il perché in `ricette.md`. Qui, il filo.

- **Installare l'engine in un'app** — prima verifica che ci entri (cinque controlli in `integrazione.md`: Compose
  androidx o Multiplatform, Compose c'è, AGP/compileSdk, `minSdk ≥ 26`, cartella `engine/` libera). Un'app in
  Compose Multiplatform non può ospitare `engine-ui`: dillo e fermati. Poi submodule, `engine-install.ps1`, dipendenza,
  tema riscrivendo il corpo della funzione di tema esistente.
- **Aggiungere una schermata** — `FluidScreen(title, subtitle) { item { FluidSectionHeader(...) } item { FluidListGroup { FluidListRow(...) } } }`; scheda `componenti/schermata.md` e `contenuto.md`.
- **Aggiungere un componente all'engine** — solo se è **generico**; in `engine-ui`, prefisso `Fluid`, KDoc che dice
  *perché*, un test se ha logica, una demo nella galleria; poi release e catalogo (`ricette.md`).
- **Far trasformare il vetro** — dalla 1.9.7 i preset usano già Fluid-physics: un'app che aggiorna ritrova la card
  che si espande trasformata senza cambiare una riga. Si chiama qualcosa solo per il tasto che si apre nel proprio
  menu (`FluidMorphMenuButton` + `FluidMorphMenuHost`) o per una forma inventata (`rememberFluidPhysicsState`,
  `Modifier.fluidPhysicsSurface`). Un menu si sceglie col dito che l'ha aperto; ha un pannello, un viaggio, un
  materiale (`componenti/menu-modali.md`). **Giudica dai fotogrammi**, mai «giusto per costruzione».
- **Dare un assistente a un'app** — `engine-ai` porta tutto ciò che non sa cosa fa l'app; l'app scrive solo gruppi,
  tool (`AiTool<C>`), prompt, azioni e UI (`ai.md`, `moduli/`).
- **Aggiungere un feature flag** — `EngineFlag(key, default)` con il default = come si comporta oggi; `remoteConfig.flag(...)`;
  la voce nel manifest solo quando serve (`config-remota.md`).
- **Aggiornare l'engine di un'app** — `engine-update.ps1 -AppRoot . -Version X`; se l'app è un porto lo script non
  tocca niente; poi compila, leggi le voci **BREAKING**, committa `engine` + `engine.properties` insieme.
- **Tagliare una versione** — `engine-release.ps1 -Version X -Notes "…" -Tag`: muove insieme `ENGINE_VERSION`,
  `EngineBuild.VERSION` e il tag. Mai a mano. Poi rigenera il catalogo (`scripts/gen_api_catalog.py`).
- **«Il widget non segue il tema»** — manca il collegamento fra impostazioni e `updateAll` (`widget.md`).
- **Qualcosa non va** — `risoluzione-problemi.md`, e prima: l'app è sull'ultima patch (3.0.1–3.0.4 correggono
  metà dei sintomi)?

## Il movimento ridotto non è automatico

`LocalFluidMotionPolicy` vale scala 1 di serie e `FluidTheme` **non** installa `FluidMotionPolicyProvider`
(verificato nel codice 3.0.4): finché la radice dell'app non lo avvolge, il movimento decorativo dell'engine ignora la
scala di animazione di sistema a zero. Mettilo in radice in un'app nuova (`componenti/movimento.md`).

## Prima di dire che hai finito

```powershell
powershell -ExecutionPolicy Bypass -File engine\tools\engine-doctor.ps1 -AppRoot .
```

```bash
./gradlew.bat --no-daemon --no-watch-fs :app:assembleDebug testDebugUnitTest
```

Poi **guardala sul dispositivo**: la UI si giudica in mano, e le animazioni dai fotogrammi (`strumenti-e-qa.md`).
Se hai toccato l'engine dentro un'app: o l'hai committato nel repo dell'engine, o lo stai per perdere. Se hai
toccato il codice pubblico dell'engine: `gen_api_catalog.py`, la scheda, `check_docs.py`, `sync_docs.py`.

## Mantenere questa skill

Vive dentro il repo dell'engine, in `skill/fluid-engine/`. Le correzioni si fanno **sempre qui**.

- **Catalogo API** (`api/`): *generato*, mai a mano. `python -I skill/fluid-engine/scripts/gen_api_catalog.py`
  dalla radice dell'engine; `--check` esce 1 se è vecchio. Rilancialo a ogni release.
- **Schede** (`componenti/`, `moduli/`): scritte a mano dal codice. `check_docs.py` fa fallire un nome che non esiste
  o un link rotto: lancialo dopo ogni modifica. Una scheda dice *perché* e *dove si rompe*; le firme stanno nel catalogo.
- **`docs/riferimento/`**: copia generata di tutta la skill, leggibile senza di lei. `sync_docs.py` la riscrive,
  `--check` verifica l'allineamento. Non si modifica a mano.
- **Installazione:** verifica com'è installata (`Get-Item "$env:USERPROFILE\.claude\skills\fluid-engine" -Force | Select-Object LinkType, Target`).
  `LinkType: Junction` (com'è sulla macchina di Alessio): l'installazione *è* il repo, le modifiche sono già live e
  `Copy-Item` lì sopra fallisce (copierebbe i file su se stessi). `LinkType` vuoto: è una copia, da rifare dopo ogni
  modifica con `Copy-Item -Recurse -Force <engine>\skill\fluid-engine "$env:USERPROFILE\.claude\skills\"`.

## Il piano di adozione

`ADOZIONE.md` alla radice dell'engine tiene l'ordine deciso da Alessio per portare **i componenti** (non solo il
tema) su tutte le app: leggilo prima di una migrazione e aggiornalo quando una fase si chiude. Stato e tappe:
`storia.md`.

## Indice dei riferimenti

- `regole.md` — le regole del design system, per esteso, con il perché
- `mappa.md` — moduli, dipendenze, toolchain, cosa installare
- `ricette.md` — i compiti frequenti, passo per passo
- `risoluzione-problemi.md` — sintomo → causa → rimedio
- `strumenti-e-qa.md` — script, build, firma, collaudo sul dispositivo
- `storia.md`, `glossario.md` — le tappe; le parole
- `integrazione.md` — mettere l'engine in un'app nuova o esistente
- `aggiornamenti.md` — versioni, canali, rilascio, rollback
- `config-remota.md` — manifest, flag, kill switch, compatibilità
- `widget.md` — il kit Glance
- `limiti.md` — cosa non si può fare, e cosa rispondere quando viene chiesto
- `ai.md` — l'assistente: cosa dà `engine-ai`, cosa resta all'app, come si scrive un tool
- `design-system.md` — l'inventario storico di `engine-ui` (token, regola del vetro, pagine…), che le schede in `componenti/` ora dettagliano
- `componenti/*.md`, `moduli/*.md` — le schede (indici nelle tabelle sopra); nuove nella 3.1.0: `componenti/contenuto-web.md`, `componenti/grafici.md`, `moduli/richtext.md`
- `api/*.md` — il catalogo generato; `api/versioni.md` — una riga per versione dal changelog
