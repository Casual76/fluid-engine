# Censimento delle icone usate dalle app

Data: 10/11/2026. Conteggio sullo snapshot delle 20:14–20:15. Nessun file delle app è stato modificato; scritti solo `censimento-app.txt` e questo file.

Il worktree dell'engine è in modifica da un'altra sessione: alle 20:03 `engine-ui` aveva 169 file `.kt`, alle 20:14 ne ha 170 (tre file `.kt` di `engine-ui` modificati nel frattempo: `ui/fluidphysics/FluidPhysicsTier.kt`, `ui/glass/backdrop/FluidGlassDiagnostics.kt`, `ui/glass/field/GlassFieldHealth.kt`). Le icone di `engine-ui` non sono cambiate: 55 usi e 39 nomi in entrambi gli snapshot.

## Come ho contato

- Letti tutti i `.kt` di ogni cartella, escluse `build`, `.gradle`, `.git`, `node_modules`. Tutte le 19 cartelle sorgenti esistevano (14 app, 4 moduli engine, `sample`).
- Escluse le **copie dell'engine**: cartelle `engine`, `fluid-engine` ed `engine-*` fuori da `src/`. Sono 5917 file `.kt` in totale, 453–462 per app, ognuna con 157 usi e 55 nomi, identiche tra loro.
- Escluso anche il checkout `classeviva-expressive\.claude\worktrees\…`: 212 file, 140 usi, 67 nomi.
- **Deviazione dalla regola letterale**: le cartelle di package chiamate `engine` dentro `src/` (33 file: MyCloud 29, Pampa-store-src 1, universal_converter 3) sono incluse, perché sono codice dell'app e non una copia dell'engine.
- Usi = occorrenze `Icons.<Stile>.<Nome>` (2090) + import di icone usate solo col nome, cioè nei file senza occorrenze qualificate, contati una volta (27). Totale 2117.
- Verifica incrociata con ripgrep sul codice incluso: 1881 usi nelle 14 app, 92 nei moduli engine, 117 in `sample`. Spot-check su `Warning`: 63, come nel `.txt`. Il `.txt` ha 361 righe, tutte nel formato richiesto, e la somma degli usi è 2117.

## Per cartella

| Cartella | .kt letti | Icone Material distinte | Usi | File con icone | Note |
|---|---:|---:|---:|---:|---|
| classeviva-expressive | 257 | 71 | 141 | 15 | esclusi copia engine (460 `.kt`) e checkout `.claude` (212) |
| Fluidify | 683 | 0 | 0 | 0 | nessuna icona Material: usa Phosphor, vedi sotto |
| Pampa notes | 591 | 50 | 90 | 19 | |
| Pampa mail | 4 | 0 | 0 | 0 | tutti in `docs/fase-1` |
| Pampa sounds | 50 | 10 | 13 | 4 | |
| Pampa widgets | 59 | 21 | 29 | 7 | |
| Pampa-store-src | 33 | 20 | 36 | 7 | incluso 1 file nel package `engine` |
| PampAI | 249 | 33 | 61 | 12 | 1 import wildcard (`rounded.*`), vedi dubbi |
| Fluid-transit | 288 | 29 | 71 | 15 | |
| FluidWeather | 620 | 42 | 83 | 18 | |
| MyCloud-Gallery-2 | 4509 | 230 | 1310 | 216 | inclusi 29 file nel package `engine`; 24 usi in `src/test` |
| universal_converter | 24 | 37 | 44 | 6 | esclusa la copia `fluid-engine/` (453 `.kt`); inclusi 3 file nel package `engine` |
| Codex | 181 | 23 | 26 | 11 | |
| KeyVoice | 34 | 3 | 3 | 1 | |
| engine: engine-ui | 170 | 39 | 55 | 17 | |
| engine: engine-ai-ui | 28 | 13 | 24 | 8 | |
| engine: engine-richtext | 10 | 12 | 13 | 1 | |
| engine: engine-update | 2 | 0 | 0 | 0 | |
| engine (4 moduli insieme) | 210 | 57 | 92 | 26 | conteggio dell'unione dei nomi |
| galleria (`sample`) | 36 | 42 | 118 | 23 | |

## Totali

- **361 icone Material distinte**, per 2117 usi.
- In **almeno due etichette** (14 app + engine + galleria): **135**. In almeno due **app reali** (senza engine e galleria): **107**.
- 226 compaiono in una sola etichetta, 19 solo in engine/galleria, 106 hanno un solo uso.
- Le più usate: Warning 63, Check 56, Delete 51, Close 48, Folder 43.

## Stile prevalente, app per app

- **Rounded** è il 100% degli usi in tutte le 12 app con icone Material: classeviva-expressive, Pampa notes, Pampa sounds, Pampa widgets, Pampa-store-src, PampAI, Fluid-transit, FluidWeather, MyCloud-Gallery-2, universal_converter, Codex, KeyVoice.
- Fluidify e Pampa mail: nessuna icona Material.
- engine: Rounded 76 su 92 (83%), Outlined 14, Filled 2. Dettaglio: engine-ui 39/55 Rounded, 14 Outlined, 2 Filled; engine-ai-ui ed engine-richtext solo Rounded.
- galleria: Rounded 116 su 118, Outlined 2.
- Totale: Rounded 2099 (99,2%), Outlined 16, Filled 2, Default/Sharp/TwoTone 0. 29 icone compaiono anche in forma AutoMirrored.
- Nelle 14 app, nel codice incluso non c'è nessun `Default`, `Filled` o `Outlined`: quegli stili compaiono solo nel codice escluso (copie engine, `build`, checkout `.claude`).

## Icone proprie

- **Disegnate a mano** (`ImageVector.Builder`): nessuna nel codice incluso. Le dichiarazioni `: ImageVector` trovate (es. `icon`, `glyph`) sono parametri o proprietà senza builder.
- **Drawable vettoriali usati come icona** (`painterResource`/`vectorResource`: 6 chiamate in 5 file):
  - `ic_app_mark`: Fluidify, `app/src/main/res/drawable/ic_app_mark.xml`, usato in `app/src/main/java/dev/lelonio/square/ui/components/Wordmark.kt:238`. È il marchio dell'app, non un'icona d'interfaccia.
  - `ic_stat_gallery`: MyCloud-Gallery-2, `app/src/main/res/drawable/ic_stat_gallery.xml`, usato solo in `app/src/test/kotlin/dev/pampa/mycloudgallery/notify/MemoryNotificationScreenshotTest.kt:210`.
- **Drawable PNG usati come icona** (non vettoriali): `airpods_mode_adaptive`, `airpods_mode_noise_cancellation`, `airpods_mode_transparency`. Fluidify, `drawable-nodpi/*.png`, scelti da `modeIconRes` in `ui/airpods/AirPodsArtwork.kt:102-105` e usati in `AirPodsNoiseControl.kt:246` e `AirPodsControls.kt:1036`.
- Non contati: `R.mipmap.ic_launcher_foreground` (Fluidify, `Wordmark.kt:251`, icona del launcher); `painterResource(resource)` in `AirPodsBattery.kt:105` (immagini prodotto); `ImageProvider(R.drawable.ic_widget_refresh)` nel widget di classeviva.
- Fuori dall'elenco: `engine-wear\src\main\kotlin\dev\antigravity\fluidengine\wear\components\FluidWearRows.kt:113` contiene un `ImageVector.Builder`. Non l'ho contato, perché `engine-wear` non è nell'elenco.

## Icone di terze parti (fuori dal `.txt`)

- **Fluidify usa Phosphor** (`com.adamglin.phosphoricons`): 224 usi, 84 nomi distinti (Regular 170, Fill 54). Più usate: Fill.Play 10, Regular.Queue 8, Regular.Plus 8, Regular.Info 8, Regular.Check 6, Fill.Pause 6, Fill.Heart 6, Regular.Trash 5, Fill.ArrowCircleDown 5, Regular.Shuffle 5.
- Nelle altre app non ho trovato altre librerie di icone: ho cercato negli import icon, phosphor, lucide, feather, tabler, remix, heroicon, fontawesome, symbols.
- Nel `.txt` ci sono solo le icone Material, come da specifica. Se Phosphor serve al set, va contata a parte.

## Cosa non ho letto e dubbi sul conteggio

- Nessun file illeggibile: tutti letti come UTF-8.
- **Import con wildcard**: `PampAI … AssistantComposer.kt` (`rounded.*`) ed `engine-ui … FluidFileTile.kt` (`outlined.*`). Nel primo non ci sono chiamate `Icon(` né nomi di icone nudi noti; nel secondo tutte le icone sono qualificate (`Icons.Outlined.*`, `Icons.AutoMirrored.Outlined.*`). Non posso escludere icone nude che non ho mai visto.
- **Conteggio lessicale**. Gli import usati solo col nome contano come un uso ciascuno: se il nome è usato più volte nel file, sottostimo; se l'import è inutilizzato, sovrastimo. Un conteggio che cerca i riferimenti nudi dà +9 usi (2117 → 2126), differenza trascurabile, non adottata perché produce falsi positivi (`Grade`, `Science`, `Home`, parole del dominio).
- Nessuna occorrenza sembra dentro una stringa o un commento (controllo approssimativo sulle virgolette; commenti: 0). Nessun alias (`as`).
- Test: i 24 usi in `src/test` di MyCloud sono inclusi.
- `docs/`, `tools/`, `spikes/`, `artifacts/` sono inclusi per la regola letterale; non contengono usi di icone.
- I `R.drawable.*` fuori da `painterResource`/`vectorResource` non sono classificati: sono 218 riferimenti (Fluidify 96, classeviva 37, MyCloud 22, FluidWeather 19, KeyVoice 14, Pampa widgets 12, PampAI 7, Fluid-transit 4, Pampa notes 3, Codex 2, Pampa sounds 1, Pampa-store-src 1), e sono per lo più widget, notifiche o immagini.
- Lo snapshot dell'engine è in movimento (vedi in cima). Rifare il censimento se il worktree cambia.
