# Quando qualcosa non va

Sintomo → causa quasi sempre → cosa fare. Raccolto da quello che ha già fatto perdere tempo (changelog, KDoc,
migrazioni delle app). Se il sintomo non c'è, `strumenti-e-qa.md` dice come guardarlo da vicino.

Il primo passo non cambia: **l'app è sull'ultima patch?** Metà dei sintomi qui sotto sono stati corretti da una
patch (3.0.1–3.0.4) e un'app li ha con un semplice aggiornamento dell'engine, senza toccare niente.

## Build e integrazione

| sintomo | causa | cosa fare |
|---|---|---|
| `fatal: transport 'file' not allowed` | engine ancora cartella locale (CVE-2022-39253) | `git -c protocol.file.allow=always submodule add …` |
| `SDK location not found` | repo appena clonato, manca `local.properties` | scrivi `sdk.dir=…` |
| l'AGP non conosce l'API 36 | AGP più vecchio dell'engine | `engine.compileSdk=35` in `gradle.properties` dell'app |
| `Plugin [id: '…compose'] was not found` | il build root dell'app non dichiara il plugin che i moduli applicano senza versione | dichiara `com.android.library`, `org.jetbrains.kotlin.android`, `org.jetbrains.kotlin.plugin.compose` |
| `Project with path ':engine-ui' could not be found` | manca il blocco nel `settings.gradle` | rilancia `engine-install.ps1` |
| `Could not get unknown property 'engine'` | un modulo è stato spostato: `versions.gradle` si applica da `${projectDir}/..` | rimettilo al suo posto |
| classi Compose duplicate | l'app è Compose Multiplatform, o forza un'altra versione di Compose | `engine-ui` non entra in KMP: «Il porto» in `integrazione.md` |
| il build non configura nemmeno `engine-ui` | app senza plugin Compose nel root (di sole View) | `-Modules engine-update` (solo i moduli senza UI) |
| la build si pianta e non finisce (Windows) | lock del file watcher | `--no-watch-fs` sempre; `--no-daemon` con build in parallelo |
| il changelog dell'app dice una versione, il codice un'altra | pin e `engine/` divergono | `engine-doctor.ps1`; si committano `engine` e `engine.properties` insieme |
| `engine-doctor` dice «agganciato a beta-…» ma il pin è giusto | più tag sullo stesso commit | corretto in 3.0.2 (guarda solo i tag `engine-*`): aggiorna |
| le mie modifiche dentro `engine/` sono sparite | `engine-update` le ha sovrascritte (erano non committate nel repo dell'engine) | `engine/` è un repo a sé: modifica, committa **lì**, taglia una versione |
| la release non si installa sopra l'app vera | firmata con la chiave di debug (`local.properties` senza `pampa.*`) | firma con `pampa.jks`; una build debug non si installa sopra quelle vere |

## Comportamento e tatto

| sintomo | causa | cosa fare |
|---|---|---|
| la pagina corre davanti al dito o salta nel back | un back basato sul `progress` del gesto di sistema (corre fino a 2,5 volte il dito) | `FluidNavHost`: segue la posizione vera del dito (`touchX`) |
| il back predittivo scatta col dito fermo o che torna indietro | l'host deduceva l'annullo da un evento solo | corretto in 3.0.4: aggiorna |
| una pagina si tira da un punto qualunque | `Anywhere` | di serie dalla 3.0.3 parte dal bordo (20 dp, `FluidBackSwipe.EdgeOnly`); `Anywhere` solo se chiesto |
| lo stato di una pagina (testo, passo di un onboarding) si azzera a rotazione o cambio di tema | l'host teneva la pagina sotto la chiave `hash` della voce | corretto in 3.0.2: la chiave è l'`id` della voce |
| la pagina sotto un pulsante non scorre mentre lo tengo | **voluto** (3.0): un dito che scende su un pulsante è del pulsante fino al sollevamento | si parte da fuori dai pulsanti; `FluidChip`/`FluidColorDot` cedono il dito alla fila |
| un chip in una fila che scorre non clicca appena la fila parte | **voluto**: `YieldsToScroll`, come iOS | — |
| l'interruttore di una riga non si trascina | era decorativo (`onCheckedChange = null`) | corretto in 3.0.4 |
| due vibrazioni («toc-toc») all'apertura di un menu | due eventi di controllo entro 70 ms | la rete dei 70 ms della 3.0; un tap su un pulsante fa **tre** colpi apposta (eccezione dei tasti) |
| il menu si apre lento | andata della goccia ~0,42 s | 3.0.4: ~0,28 s |
| il titolo grande non si ritira / il bordo non è elastico | una lista dentro la lista della pagina: quella interna prende il gesto | righe di due dentro la stessa `LazyListScope` |
| la barra flottante non si ripiega, o si ripiega senza scorrimento | `locked`, o contenuto che non scorre davvero | 3.0.1: si ripiega solo se il contenuto scorre e si riapre a ogni cambio di pagina; togli `settle()`/`expand()` a mano |
| il pop-up finisce sotto la tastiera o la barra dei gesti | | 3.0.1 (tastiera) e 3.0.3 (inset della barra di navigazione): aggiorna |
| testo nero su nero in tema scuro dentro un pop-up/foglio/menu | l'host non dava il colore del contenuto | 3.0.1 |
| i menu tagliano la voce più lunga | | 3.0.1: si allargano fino a 340 dp |
| un campo di testo su più righe si comporta come una riga sola | `singleLine` ha default vero e ignora `minLines` | 2.5.2: `minLines > 1` è su più righe |
| con testo grande il pulsante taglia l'etichetta | altezza fissa | 3.0.4: l'altezza è un minimo |
| con le animazioni di sistema a zero l'engine anima lo stesso (burst, toast, pressione del vetro) | `LocalFluidMotionPolicy` vale 1 di serie e nessuno installa `FluidMotionPolicyProvider` (nemmeno `FluidTheme`) | avvolgi la radice dell'app in `FluidMotionPolicyProvider` (`componenti/movimento.md`) |
| la tastiera fisica non mostra il fuoco | `indication = null` | 2.7.0: `fluidPressable` si fa avanti del 3% |

## Vetro e aspetto

| sintomo | causa | cosa fare |
|---|---|---|
| il vetro è «invisibile» o piatto | rifrange una pagina grigia (nessun fondale) | dai alla pagina un fondale che valga la pena guardare (`FluidAmbient*`); il vetro non è la cura del grigio |
| un lampo di un fotogramma all'apertura di un pannello | un nodo disegna col default nel primo/ultimo fotogramma | cancello `drawWithContent { if (amount > 0.004f) drawContent() }` (trappola 2, `regole.md`) |
| il testo *dietro* il vetro si muove in un rimbalzo | hai scalato il layer della superficie di vetro | l'oltrepasso va nella geometria (`overshootInflation`), mai in `graphicsLayer { scaleX }` (trappola 1) |
| il composito sotto uno scrim resta scuro mentre lo scrim sfuma | `exports` dello scrim registrano il materiale a forza piena | campiona la pagina cruda e dipingi la tua quota di scurimento (trappola 3) |
| un tasto o una card di vetro dentro un pannello di vetro è più nitida del pannello | campiona la pagina cruda invece del materiale del pannello | dalla 2.13.0 lo trova da solo (il nodo di vetro più vicino fra gli antenati); un pannello disegnato con **un altro motore** non è un nodo dell'engine: `Modifier.glassMaterialSource` + `Modifier.glassContainer` (`componenti/vetro.md`) |
| leggibilità scarsa sul vetro | si è alzata la tinta | la leggibilità si compra col raggio di sfocatura, non con l'opacità |
| un pannello grande sembra un pannello piccolo ingrandito | | 2.12.0: regola della taglia (`glassSizeGain`) |
| l'app è lenta / RenderThread saturo | sorgenti di vetro a schermo intero sempre registrate | registra solo ciò che si vede; `sampleOnce`; `artifacts/perf/` |
| sul telefono vecchio il vetro è piatto | `RenderEffect` esiste dall'API 31 e `RuntimeShader` (la lente, gli shader di Fluid-physics) dall'API 33: sotto c'è un ripiego | previsto (`isRenderEffectSupported`, `isRuntimeShaderSupported`): `componenti/vetro.md` |
| nessun accento risulta selezionato dopo la migrazione | i preset sono salvati per **nome**: l'engine chiama «fluid» ciò che l'app chiamava «expressive» | tieni la lista dei preset dell'app coi nomi storici |

## Moduli senza UI

| sintomo | causa | cosa fare |
|---|---|---|
| un flag non cambia | la cache vale sei ore; o manca la voce nel manifest (vale il default); o l'override per app non c'è | `refreshIfStale()`; vedi `config-remota.md` |
| senza rete l'app cambia comportamento | un flag con default che non è «la build di oggi» | il default descrive sempre la build com'è uscita |
| il widget non segue il tema | il widget si ridisegna sui **dati**, non sull'aspetto | collega le impostazioni a `updateAll` (`widget.md`) |
| `engine-update` non installa | permesso d'installare da fonti sconosciute mancante | `AppUpdateInstallState.NeedsPermission` (3.0.1): la UI lo gestisce; **`REQUEST_INSTALL_PACKAGES` rende l'app non pubblicabile su Play** |
| l'assistente non vede i tool di un'altra app | il ponte richiede la **stessa firma** | `moduli/ai-bridge.md`; dalla 3.0 il permesso `AI_TOOLS` non esiste più |
| un 400 dell'assistente sembra «errore del servizio» | prima della 2.9.0 | `AiError.BadRequest.reason` (`BadReason`) |
| limite di richieste che consiglia un interruttore inesistente | | `FluidAssistantStrings(failoverSwitch = false)` |

## Licenza e distribuzione

Dalla **3.0** l'engine contiene codice di LibrePods (**GPL-3.0**): un'app che lo distribuisce è sotto GPL-3.0, i sorgenti
vanno a chi riceve l'APK, e la sua pagina «informazioni» chiama `fluidLicensesSection()`. Non è un dettaglio da
scoprire a rilascio fatto: `ADOZIONE.md`, «Migrare alla 3.0».

## Se nessuna riga corrisponde

1. `grep` nel CHANGELOG (`api/versioni.md` è il digest, `CHANGELOG.md` il testo intero): spesso la voce c'è.
2. Il KDoc del componente dice *perché* è fatto così: leggilo prima di «correggere».
3. Il test che lo copre (`mappa.md`, «Test») dice cosa è garantito.
4. Guardalo in mano (`strumenti-e-qa.md`); i fotogrammi, non il ragionamento, decidono per il vetro in movimento.
