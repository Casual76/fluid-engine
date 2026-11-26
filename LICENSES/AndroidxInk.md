# Jetpack Ink (androidx.ink)

Il modulo facoltativo `engine-ink` dipende, **come libreria binaria** (non come sorgente), da Jetpack Ink e dalla
predizione del tratto di Jetpack.

- Progetto: **Jetpack Ink**, di The Android Open Source Project
- Repository: https://android.googlesource.com/platform/frameworks/support/+/androidx-main/ink/
- Artefatti (Google Maven), fissati in `versions.gradle` (`engine.ink`, `engine.motionPrediction`):
  - `androidx.ink:ink-authoring:1.0.0`, `ink-brush`, `ink-geometry`, `ink-rendering`, `ink-strokes` (con
    `ink-nativeloader`, che porta la libreria nativa `libink.so` per le ABI di Android);
  - `androidx.input:input-motionprediction:1.0.1`.
- Licenza: **Apache License 2.0** (letta il 25/11/2026 nel `LICENSE.txt` dentro gli artefatti 1.0.0:
  `META-INF/androidx/ink/ink-nativeloader/LICENSE.txt`).

Apache 2.0 e' compatibile con la GPL-3.0 dell'engine (la Free Software Foundation la elenca fra le licenze compatibili
con la GPL versione 3): non serve un permesso aggiuntivo come per le librerie Google non libere.

## Cosa usiamo

| Dove sta da noi | Cosa usa |
|---|---|
| `engine-ink/src/main/kotlin/dev/antigravity/fluidengine/ink/FluidInkCanvas.kt` | `InProgressStrokesView` (il tratto in corso nel front buffer, a bassa latenza), `MotionEventPredictor` |
| `engine-ink/src/main/kotlin/dev/antigravity/fluidengine/ink/FluidInkRender.kt` | `StockBrushes.pressurePen` e `highlighter`, una famiglia di pennelli per la matita, `Stroke`, `CanvasStrokeRenderer` |

Il modello dei tratti, la gomma, il lazo, le forme e il salvataggio sono dell'engine (Kotlin puro): non c'e' codice di
androidx.ink copiato nell'engine. L'avviso viaggia con l'app nell'elenco dei crediti: un'app che include `engine-ink`
aggiunge `FluidInkLicenses` a `fluidLicensesSection(licences = …)`. Il testo della licenza e' in
https://www.apache.org/licenses/LICENSE-2.0.
