# La mappa dell'engine

Tutto quello che c'è nel repo, dove sta e da cosa dipende. Leggila quando devi decidere **in quale modulo
va una cosa**, **quali moduli installare in un'app**, o **dove andare a leggere il codice di un componente**.
I numeri sono dell'engine 3.0.4 (righe di Kotlin sotto `src/main`).

## I moduli, uno per uno

| modulo | pacchetto sotto `dev.antigravity.fluidengine` | file / righe | cosa porta | dipende da |
|---|---|---|---|---|
| `engine-foundation` | `foundation` | 6 / 495 | modelli, versioni, manifest remoto, flag, impostazioni — **niente Compose, niente Android UI** | (coroutines, serialization) |
| `engine-ui` | `ui` | 148 / 55 521 | il design system: tema, componenti, vetro, Fluid-physics, pagine, aptica, tutorial | foundation |
| `engine-storage` | `storage` | 1 / 128 | DataStore: impostazioni dell'engine e cache del manifest | foundation |
| `engine-net` | `net` | 1 / 141 | `EngineHttp`: leggi un documento, scarica un file | foundation |
| `engine-config` | `config` | 1 / 112 | `EngineRemoteConfig`: flag, versione minima, avvisi, kill switch | foundation, net, storage |
| `engine-update` | `update` | 2 / 489 | aggiornamento in-app con PackageInstaller | foundation, net |
| `engine-widget` | `widget` | 3 / 686 | palette e componenti Glance con lo stesso aspetto dell'app | foundation, **ui** |
| `engine-wear` | `wear` | 17 / 2 216 | il design system su Wear OS (opt-in) | foundation, **ui** |
| `engine-ai` | `ai` | 36 / 6 456 | assistente senza dominio: provider BYOK, chiavi, SSE, failover, orchestratore, voce | foundation |
| `engine-ai-ui` | `aiui` | 28 / 6 846 | l'interfaccia dell'assistente: overlay, chat, impostazioni, halo (opt-in) | **ui**, **ai** |
| `engine-ai-bridge` | `ai.bridge` | 4 / 912 | tool federati fra app (ContentProvider che verifica la firma di chi chiama) | ai |
| `engine-richtext` | `richtext` | 9 / 3 997 | l'editor di testo con formattazione (elenchi e citazioni annidati, link, barra sopra la tastiera) e i lettori/scrittori di HTML ristretto, Markdown e testo semplice — **opt-in**, dalla 3.1.0 (la scheda: `moduli/richtext.md`) | foundation, **ui** |

(I numeri di file e righe sono quelli dell'engine 3.0.4; `engine-richtext` è contato sulla 3.1.0.) `engine-ui` e' circa tre quarti di tutte le righe; gli altri sono sottili di proposito. Un modulo senza UI non puo'
dipendere da `engine-ui`: e' il motivo per cui un'app di sole View puo' prendere `engine-update` senza Compose.

```
engine-foundation  <-  engine-ui  <-  engine-widget
        ^                  ^      <-  engine-wear
        |                  +------<-  engine-richtext           (opt-in)
        |                  +------<-  engine-ai-ui  ->  engine-ai
        +-- engine-storage --+                              ^
        +-- engine-net ------+--> engine-config             +-- engine-ai-bridge
        |        ^
        |        +-- engine-update
        +-- engine-ai (foundation, DataStore, serialization, coroutines-android)
```

(freccia = «dipende da»: `engine-config` prende foundation, net e storage; `engine-update` foundation e net;
`engine-ai-ui` prende `engine-ui` e `engine-ai`; `engine-ai-bridge` prende `engine-ai`; `engine-richtext` prende `engine-foundation` e `engine-ui`.)

Le dipendenze `api` di `engine-ui` esportano all'app: Compose BOM (ui, foundation, runtime, animation), Material 3,
`material-icons-extended`, core-ktx, lifecycle (runtime/viewmodel compose), activity-compose, navigation-compose e
`engine-foundation`. `engine-ui` tiene `graphics-shapes` come `implementation`, e **dalla 3.1.0 anche `androidx.webkit`** (`implementation`, per `FluidWebContent`): un'app che compila `engine-ui` dai sorgenti ottiene `androidx.webkit` nel build anche se non usa mai il contenitore web.

## Quali moduli mettere in un'app

| l'app… | moduli |
|---|---|
| è in Compose e vuole il look | `engine-ui` (porta `engine-foundation`) |
| vuole anche aggiornarsi da sola | + `engine-update` (dichiara `REQUEST_INSTALL_PACKAGES`: non pubblicabile su Play senza motivazione) |
| vuole flag e kill switch | + `engine-config` (porta `net` e `storage`) |
| ha widget Glance | + `engine-widget` |
| ha un assistente | + `engine-ai` (+ `engine-ai-ui` se vuoi la sua interfaccia) |
| espone o chiama tool di altre app | + `engine-ai-bridge` |
| scrive testo formattato (corpo di una mail, note con elenchi, firme) | + `engine-richtext` (**opt-in**: solo se chiesto per nome, `-Modules …,engine-richtext`; porta `engine-ui` e `engine-foundation`) |
| è un orologio (modulo Wear OS) | `engine-wear` **solo nel modulo dell'orologio** |
| è di sole View e XML | solo `engine-update` / `engine-net` / `engine-config` (`-Modules`), mai `engine-ui` |
| è Compose **Multiplatform** | niente `engine-ui` (artefatti diversi): i moduli senza UI in `androidMain`, il look si **porta** (`integrazione.md`, «Il porto») |

`engine-install.ps1 -Modules a,b` chiude da sé le dipendenze. Perché conta: `engine-ui` applica il plugin Compose,
e un'app che non lo dichiara nel build root non riesce nemmeno a **configurare** il modulo, quindi ferma tutto il build.

## Toolchain (da `versions.gradle`)

AGP 8.13.2 · Kotlin 2.2.20 · Compose BOM 2026.03.00 · Material 3 **1.5.0-alpha16** (`MotionScheme` non è nella
linea stabile) · lifecycle 2.10.0 · activity-compose 1.13.0 · navigation 2.9.8 · DataStore 1.1.1 · Glance 1.1.1 ·
Wear Compose 1.6.2 · androidx.webkit 1.14.0 (dalla 3.1.0) · coroutines 1.8.1 · serialization 1.6.3 · Java 17. `compileSdk` 36, `minSdk` 26 (alzabile dall'app
con `engine.minSdk`, mai abbassabile; `engine.compileSdk=35` nel `gradle.properties` dell'app per un AGP vecchio).
Le versioni stanno in `versions.gradle` come mappa semplice, non come catalogo: l'engine sta dentro app che ne hanno già uno.

## `engine-ui` per cartella

Tutto sotto `engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/`. La colonna «scheda» è la pagina di
`references/componenti/` che li descrive; «API» è il catalogo generato in `references/api/`.

| cartella | cosa | scheda | API |
|---|---|---|---|
| `fluid/` (89 file nella 3.1.0, 73 nella 3.0.4) | quasi tutti i componenti: schermata, barre, righe, controlli, menu, notifiche, pressione, movimento, materiale; dalla 3.1.0 liste lunghe, avatar e selezione, pannello laterale, selettore data e ora, campo a gettoni, contenuto web, grafici, file | `schermata`, `barre`, `contenuto` (+ `contenuto-web`, `grafici`), `controlli`, `menu-modali`, `movimento`, `impostazioni` | stesse (+ `contenuto-web`, `grafici`) |
| `theme/` | `FluidTheme`, `FluidComponents` (i componenti Material ridefiniti), vecchi `Motion`/`RouteMotion` | `tema` | `tema` |
| `glass/` (+ `backdrop/` e sotto) | la libreria di Kyant0 copiata come sorgente: lente, backdrop, effetti, highlight, ombre | `vetro` | `vetro` |
| `fluidphysics/` (17 file) | il motore che trasforma una sagoma di vetro in un'altra | `fluid-physics` | `fluid-physics` |
| `page/` + `navigation/` | `FluidNavHost`, host delle pagine, back, barra di pagina | `pagine` | `pagine` |
| `haptics/` | vocabolario degli eventi aptici, grana, pattern | `aptica` | `aptica` |
| `tutorial/` | suggerimenti al primo uso, coach mark | `tutorial` | `tutorial` |

I file più grandi, da conoscere perché è lì che stanno le cose difficili: `FluidFloatingTabBar.kt` (3 181 righe, la
barra a pillola che si ripiega), `FluidGlassModal.kt` (2 411, pop-up e fogli), `GlassMaterial.kt` (1 938, il
materiale), `FluidScreen.kt` (1 803), `FluidComponents.kt` (1 794), `FluidPhysicsState.kt` (1 237),
`FluidNotification.kt` (1 213), `DrawBackdropModifier.kt` (1 211), `FluidScrubber.kt` (1 203).

## Il resto del repo

| cosa | dove | a che serve |
|---|---|---|
| gli script | `tools/` | `engine-install`, `engine-update`, `engine-update-all`, `engine-doctor`, `engine-release` (vedi `strumenti-e-qa.md`) |
| la galleria | `sample/` | **Fluid Glass**, app vera del Pampa Store (`dev.antigravity.fluidglass`): ogni cambiamento al design system si *guarda* qui prima di arrivare nelle app. È anche la raccolta dei test d'interfaccia (Robolectric) |
| i modelli | `templates/` | `EngineBootstrap.kt.txt` (collegamenti nella `Application`), `EngineModule.kt.txt`, `engine-manifest.json`, `LICENSE-GPL-3.0.txt` |
| il manifest dello store | `manifest.json` | le app pubblicate dall'engine (Fluid Glass) + sezione `engine` |
| le licenze | `LICENSES/` | `AndroidLiquidGlass.md` (Kyant0, Apache-2.0), `Inter.md`, `LibrePods.md` (**GPL-3.0**) |
| la documentazione lunga | `docs/` | 10 capitoli numerati + `riferimenti-3.0.md` (l'analisi di LibrePods) |
| lo stato del lavoro | `artifacts/` | `3.0-decisioni.md`, `3.0-stato.md`, e le cartelle dei collaudi (beta, baseline, perf) |
| il changelog | `CHANGELOG.md` | una sezione per versione; le voci **BREAKING** e «Cambia a vista/al tatto» |
| il piano d'adozione | `ADOZIONE.md` | in che ordine e a che profondità ogni app sta sull'engine; «Migrare alla 3.0» |
| la skill | `skill/fluid-engine/` | questa documentazione (la fonte; l'installazione è una giunzione qui) |

## Test

Nella 3.1.0: 179 file di test sotto `engine-*/src/test` (113 in `engine-ui`, 33 in `engine-ai`, 8 in `engine-wear`, 7 in
`engine-ai-ui`, 7 in `engine-richtext`, gli altri 2-5) più 47 in `sample/src/testDebug` (interfaccia con Robolectric: gesti, pagine, menu,
semantica) e le prove **strumentate** in `sample/src/androidTest` (una WebView vera: `FluidWebContentInstrumentedTest` e, dalla 3.1.1, `FluidWebContentNetworkCensusTest` e `FluidWebContentSealInstrumentedTest`, che girano solo su emulatore o dispositivo). Quando una scheda dice «verificato da» un test, quel test è lì. Un comportamento di cui nessun test parla
va **guardato in mano** prima di fidarsene (`strumenti-e-qa.md`).
