# Ricette

I compiti che tornano, passo per passo, con il *perché* di ogni passo: chi capisce perché c'è un passo sa
quando si può saltare e quando no. Per i dettagli di un componente, `componenti/`; per le firme, `api/`.

Prima di ogni ricetta: **dove sei?** Dentro `engine/` di un'app non stai lavorando sull'app (`SKILL.md`,
«Riconoscere la situazione»).

## Indice

1. [Una schermata nuova in un'app](#una-schermata-nuova-in-unapp)
2. [Un componente nuovo nell'engine](#un-componente-nuovo-nellengine)
3. [Un feature flag](#un-feature-flag)
4. [Un'app nuova sull'engine](#unapp-nuova-sullengine)
5. [Portare un'app esistente sul tema, poi sui componenti](#portare-unapp-esistente)
6. [Passare a `FluidNavHost` (la 3.0)](#passare-a-fluidnavhost)
7. [Tradurre le parole dell'engine](#tradurre-le-parole-dellengine)
8. [Un evento aptico o un token di movimento nuovo](#un-evento-aptico-o-un-token-di-movimento-nuovo)
9. [Un menu, un pop-up, un avviso](#un-menu-un-pop-up-un-avviso)
10. [Un widget che segue il tema](#un-widget-che-segue-il-tema)
11. [Un assistente IA, e i suoi tool](#un-assistente-ia-e-i-suoi-tool)
12. [Un modulo nuovo dell'engine](#un-modulo-nuovo-dellengine)
13. [Tagliare una release e aggiornare le app](#tagliare-una-release-e-aggiornare-le-app)
14. [Giudicare un'animazione di vetro](#giudicare-unanimazione-di-vetro)

---

## Una schermata nuova in un'app

1. **Parti da `FluidScreen`**, non da `Scaffold`: titolo grande dentro lo scroll che si ritira nella barra di
   vetro, bordo elastico, refresh. Una schermata a mano rifà male la stessa cosa e perde la barra unica
   dell'host (`componenti/schermata.md`).
2. Il contenuto è una `LazyListScope`: `FluidSectionHeader` + `FluidListGroup { FluidListRow … FluidListDivider … }`.
   Il colore della categoria sta sulla piastrella dell'icona, **mai** sullo sfondo della riga
   (`regole.md`, «Liste raggruppate»).
3. **Una lista dentro la lista della pagina non scorre.** Quella interna prende il gesto: il titolo non si ritira
   e il bordo elastico non si sente. Una griglia diventa *righe di due* dentro la stessa `LazyListScope`.
4. Colori da `MaterialTheme.colorScheme`, testo da `MaterialTheme.typography`/`FluidTextStyles`, angoli da
   `FluidRadius` con `ContinuousCornerShape`, animazioni da `FluidMotion`: niente valori a mano (`regole.md`).
5. Se la schermata sta in un `FluidNavHost`, **non disegna la sua barra**: la descrive (una `FluidScreen` la registra da sola) e la
   disegna l'host (`componenti/pagine.md`).
6. Verifica: `engine-doctor.ps1`, poi `:app:assembleDebug testDebugUnitTest`, poi **guardala sul dispositivo**
   (`strumenti-e-qa.md`): la UI si giudica in mano, non dal codice.

## Un componente nuovo nell'engine

Prima la domanda che decide tutto: **è generico?** Un componente che sa cos'è un voto, una materia, una
transazione non è dell'engine (`regole.md`, «L'engine non conosce il dominio»). Se è dell'app, resta nell'app.

1. **Cerca prima** in `componenti/` e `api/`: spesso c'è già, con un parametro che non conoscevi (`sampleOnce`,
   `leadingTile`, `subtitleMaxLines`…). Gran parte delle «aggiunte» della 3.0.1 era un parametro mancante.
2. File in `engine-ui/.../ui/fluid/` (o nella cartella della sua famiglia), nome col prefisso **`Fluid`**.
   `engine-foundation` non conosce Compose: un modello senza UI va lì.
3. **Il KDoc dice perché**, non solo cosa: perché quella molla, perché quel margine, cosa è stato scartato. È la
   fonte da cui ripartono le schede e da cui un altro Claude capirà il comportamento in un caso non descritto.
4. Regole del design system dentro il componente: angoli continui, `FluidMotion`, `fluidPressable` per ciò che si
   preme (con il `grip` giusto: `HoldsFinger` per un tasto, `YieldsToScroll` per ciò che sta in una fila che
   scorre), `FluidHaptics` per le vibrazioni, `LocalFluidMotionPolicy` per il movimento ridotto, **semantica per
   TalkBack** (ruolo, nome, stato), bersaglio ≥ 48 dp, parole da `FluidLabels` (mai italiano scritto dentro).
5. **Un test se ha logica** (misura, stato, contrasto, soglia): in `engine-ui/src/test`; se è interazione,
   un test Robolectric in `sample/src/testDebug`.
6. **Una demo nella galleria** (`sample/.../catalog/` o la scheda giusta): è lì che Alessio lo giudica. Una
   modifica al design system che nessuno ha guardato non è finita.
7. `python -I skill/fluid-engine/scripts/gen_api_catalog.py` (il catalogo cambia), poi aggiorna la **scheda** della
   famiglia in `componenti/` e controlla con `check_docs.py`. `sync_docs.py` porta le schede in `docs/`.
8. Release: ricetta «Tagliare una release». Una firma pubblica cambiata o tolta è **major** (`aggiornamenti.md`).

## Un feature flag

```kotlin
val NuovaAgenda = EngineFlag(key = "nuovaAgenda", default = false)   // default = come si comporta oggi
```

1. Dichiaralo dove serve, col default **che descrive la build di oggi**: senza rete l'app deve comportarsi
   come il giorno in cui è uscita.
2. Leggilo: `remoteConfig.flag(NuovaAgenda)` (un `Flow`), con `stateIn(..., NuovaAgenda.default)`.
3. Aggiungi la voce al manifest ospitato **solo** quando serve accenderlo; finché non c'è vale il default.
4. Un flag ha una scadenza: a funzione stabile togli il ramo vecchio **e** il flag.
5. I flag scelgono fra percorsi che la build **contiene già**; non aggiungono codice (`limiti.md`).

Dettagli, override per app, kill switch: `config-remota.md` e `moduli/config.md`.

## Un'app nuova sull'engine

1. **L'app può ospitarlo?** Cinque controlli (`integrazione.md`): che Compose usa (Multiplatform → no), se Compose
   c'è, AGP/compileSdk, `minSdk ≥ 26`, la cartella `engine/` è libera. Se uno fallisce, dillo e fermati.
2. Submodule, poi `engine-install.ps1 -AppRoot .` (con `-Modules` per i soli moduli che servono), poi aggancia il
   tag.
3. Dipendenza: `implementation project(':engine-ui')` (e gli altri solo se servono davvero).
4. Tema: `FluidTheme(settings = …, brand = AccentPreset(…)) { … }` alla radice.
   Subito dentro, **`FluidMotionPolicyProvider { … }`**: `FluidTheme` non lo installa (verificato nel codice 3.0.4), e senza
   di lui il movimento decorativo dell'engine ignora la scala di animazione di sistema a zero (`componenti/movimento.md`).
5. Collegamenti nella `Application` (flag, compatibilità, widget): `templates/EngineBootstrap.kt.txt`.
6. **Licenza.** Dalla 3.0 l'engine contiene codice GPL-3.0 (LibrePods): un'app che lo include si distribuisce
   sotto GPL-3.0, con i sorgenti a chi riceve l'APK, e la sua pagina «informazioni» chiama
   `fluidLicensesSection()`. Dillo prima di installare, non dopo (`regole.md`).
7. `engine-doctor.ps1`, poi la build, poi guardala.

## Portare un'app esistente

Tre profondità, e confonderle è il modo più rapido di promettere ciò che non arriva (`ADOZIONE.md`):

| profondità | cosa cambia | si vede? |
|---|---|---|
| **tema** (`FluidTheme`) | colori, tipografia, angoli, curve | poco |
| **componenti** (`FluidScreen`, `FluidListGroup`, vetro, overscroll) | com'è fatta ogni schermata | **sì** |
| **moduli senza UI** (`engine-update`, `engine-config`) | aggiornamento in-app, flag | no |

Il **tema** si adotta riscrivendo il *corpo* della funzione di tema che l'app ha già, senza toccare i punti di
chiamata (`integrazione.md`). I colori del marchio sono il `primary` del suo `lightColorScheme`/`darkColorScheme`.
Dopo il solo tema l'app si vede quasi identica: va detto così, non «ha preso il design system».

I **componenti** si portano per schermata. Trappola da cercare *prima*: la lista dentro la lista (ricetta
«schermata nuova», punto 3). Cerca i nomi pienamente qualificati che la riscrittura degli import non vede:
`grep -rn "core.designsystem\." --include=*.kt`.

## Passare a `FluidNavHost`

(3.0, **BREAKING**; ricetta completa in `ADOZIONE.md`, «Migrare alla 3.0»; comportamento in `componenti/pagine.md`.)

1. `NavHost` → `FluidNavHost`, `rememberNavController` → `rememberFluidNavController`, le rotte con
   `composable(...)` dell'engine. Le transizioni di `NavHost` e `FluidRouteMotionHost` (deprecato) **non** si
   portano: le pagine sono opache, da destra, con la pagina sotto che arretra di un terzo.
2. La barra è **una sola, ferma**: la pagina la descrive (`FluidScreen` la registra da sola), non se ne disegna una sua.
3. Il back è quello di iOS sulla posizione vera del dito; di serie parte dal **bordo** (20 dp,
   `FluidBackSwipe.EdgeOnly`); `Anywhere` solo per la pagina che lo vuole. Togli gli `EdgeOnly` messi per aggirare
   i gesti propri: dalla 3.0.3 sono il default.
4. Lo stato salvato nelle pagine (`rememberSaveable`) sopravvive alla ricreazione dell'Activity dalla 3.0.2.
5. Aggiungi `fluidLicensesSection()` alla pagina «informazioni» e la nota GPL-3.0 alla distribuzione.
6. Togli le toppe messe sulla 3.0.0 (`ADOZIONE.md`, punto 8: le correzioni della 3.0.1 le rendono superflue).

## Tradurre le parole dell'engine

L'italiano è il default. Per un'app in inglese, in radice: `FluidLabels.english()` (`LocalFluidLabels`),
`FluidUpdateStrings.english()`, e, con `engine-ai-ui`, `FluidAssistantStrings.english()`,
`FluidAiSettingsStrings.english()` (con `FluidProviderGuides.english`) e `FluidChatStrings.english()`.
Per un'altra lingua si costruisce l'oggetto delle stringhe campo per campo: **un test per riflessione** fa cadere
la build se un campo inglese resta uguale all'italiano, vuoto, o perde un segnaposto, quindi un campo aggiunto
domani senza la sua traduzione non passa. Le frasi di sincronizzazione stanno in `FluidLabels.sync`.
Dettagli: `componenti/impostazioni.md`.

## Un evento aptico o un token di movimento nuovo

- **Aptica:** un nuovo `FluidHapticEvent` va **in coda** al vocabolario (l'ordine è parte del contratto), con la
  sua composizione in `FluidHapticPatterns`; due eventi di controllo entro 70 ms sono lo stesso momento e il secondo
  non suona (`componenti/aptica.md`). Un evento «solo di piattaforma» non si fonde e non arma la rete dei 70 ms.
- **Movimento:** mai una molla o un `tween` scritti nel componente (regola di `regole.md`, «Un solo vocabolario di movimento»;
  in `engine-wear` la fa rispettare `FluidWearMotionTest`, e `FluidMotionTokensTest` blocca i valori dei token): un token in `FluidMotion` (e, se Material deve vederlo, in `FluidMotionScheme`), con il perché nel
  KDoc. Le animazioni a zero (`LocalFluidMotionPolicy`) devono dare un componente corretto, non rotto.

## Un menu, un pop-up, un avviso

Scegli per **chi lo apre** e **cosa deve diventare** (`componenti/menu-modali.md`):

| serve | uso |
|---|---|
| un tasto che si trasforma nel proprio menu | `FluidMorphMenuButton` + `FluidMorphMenuHost` alla radice |
| le azioni della barra | il menu delle azioni della barra (stessa famiglia liquida) |
| il menu contestuale di una riga | `fluidContextMenu` |
| un pop-up/foglio che nasce da un punto | `FluidGlassModalPortal` (+ `FluidGlassModalHost` alla radice) |
| un avviso, una conferma | `FluidAlert`, `FluidConfirmSheet`, `FluidPromptAlert` (una riga di testo) |
| una notifica contestuale | `FluidNotificationHost` + `FluidNotification` |

La regola 3.0: ogni menu si sceglie **col dito che l'ha aperto** (tieni, trascina, rilascia; un rilascio altrove lo
lascia aperto) e ha **un pannello, un viaggio, un materiale**. Non rifare un menu a mano. La trappola dei modali:
le lambda composable sono contenitori mutabili, quindi non catturare stato che cambia fuori dal portale
(`componenti/menu-modali.md`).

## Un widget che segue il tema

Un widget Glance si ridisegna quando cambiano i **dati**, non l'**aspetto**: serve il collegamento fra le
impostazioni e `updateAll` (nella `Application`: `templates/EngineBootstrap.kt.txt`, punto 3). Per i widget
`RemoteViews`: `engineWidgetIsDark`, `engineWidgetColorScheme`, `engineWidgetArgb`. `widget.md`, `moduli/widget.md`.

## Un assistente IA, e i suoi tool

`engine-ai` porta tutto ciò che non sa cosa fa l'app; l'app scrive **solo** il dominio: i gruppi (un enum che
implementa `AiToolGroup`), i tool (`AiTool<C>` col proprio contesto `C`), il prompt, le azioni e la UI. Un tool che
sa cos'è un voto non entra mai nell'engine. Con `engine-ai-ui` la UI arriva quasi pronta. Per scrivere un tool,
le trappole e il ponte fra app: `ai.md`, `moduli/ai-orchestrator.md`, `moduli/ai-ui.md`, `moduli/ai-bridge.md`.

## Un modulo nuovo dell'engine

Raro; farlo male costa agli altri. Un modulo nuovo si giustifica solo se serve a **app che non vogliono gli altri**
(è il motivo per cui `engine-update` non sta in `engine-ui`).

1. Cartella `engine-<nome>/` con `build.gradle` (copia quello di un modulo simile: stessi plugin, `namespace
   'dev.antigravity.fluidengine.<nome>'`, `minSdk engine.minSdk`, dipendenze `api project(...)` minime).
2. `include ':engine-<nome>'` in `settings.gradle` (percorsi piatti di proposito: un'app ospite include gli stessi
   nomi).
3. `tools/engine-install.ps1`: la mappa delle dipendenze (`$ModuleDeps`), l'elenco opt-in se è da chiedere per nome,
   e la lista che stampa. Senza, un'app non può chiederlo.
4. Se porta UI o Compose: dipende da `engine-ui`; se no, **non** deve (un modulo senza UI che dipende da
   `engine-ui` impedisce l'uso nelle app di sole View).
5. `README.md`, `docs/`, `mappa.md` e la scheda in `moduli/`; una voce in `gen_api_catalog.py` (`DIR_FAMILY`,
   `FAMILY_TITLES`) o il catalogo lo segnalerà come «non classificato».
6. `engine-release.ps1`, e `ADOZIONE.md` se cambia il piano.

## Tagliare una release e aggiornare le app

Dentro il repo dell'engine, albero pulito:

```powershell
powershell -ExecutionPolicy Bypass -File tools\engine-release.ps1 -Version 3.1.0 -Notes "ui: …" -Tag
```

Muove insieme `ENGINE_VERSION`, `EngineBuild.VERSION` (in `EngineVersion.kt`) e il tag `engine-<versione>`, e
inserisce la sezione nel `CHANGELOG.md` sotto `<!-- nuove versioni qui sopra -->`. Non toccarli a mano: divergono, e
`EngineBuild.VERSION` è quella su cui il manifest remoto giudica le build installate. Con più voci: `-Command` invece
di `-File`.

Cosa merita quale numero: **patch** correzioni; **minor** aggiunte compatibili; **major** qualcosa cambia o sparisce
(anche un rename: «è solo un nome» non esiste), con la voce `BREAKING:` e come si migra. Marca nel changelog ciò che
**Cambia a vista** o **Cambia al tatto** anche se compila: chi aggiorna deve saperlo.

Poi, per ogni app: `engine-update.ps1 -AppRoot . -Version X` (o `engine-update-all.ps1 … -WhatIf` prima), si legge
l'elenco **BREAKING**, si compila, si committa `engine` + `engine.properties` **insieme**. Il rollback è lo stesso
comando con il numero di prima: i tag non si spostano. Se l'app è un **porto** (`engine.mode=port`) lo script non
tocca niente: lo aggiorna una persona e solo allora alza `FLUID_PORT_OF`.

Dopo la release: `gen_api_catalog.py`, le schede toccate, `check_docs.py`, `sync_docs.py`.

## Giudicare un'animazione di vetro

«È giusto per costruzione» qui non è mai bastato: i difetti del vetro in movimento (lampi di un fotogramma,
fondale che scala, scrim che resta scuro) non si vedono ragionando. Ricetta in `componenti/fluid-physics.md` e in
fondo a `regole.md`: registra con `screenrecord`, estrai i fotogrammi con **`-fps_mode passthrough`** (a frame
rate variabile `fps=30` riordina e duplica), guarda **i primi 5** fotogrammi, e per i difetti di un fotogramma solo
la misura batte l'occhio (`signalstats,metadata=print`: la luminanza per frame, un lampo è un picco isolato).
Gli script sono in `tools/qa/` (`strumenti-e-qa.md`).
