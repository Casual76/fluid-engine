# Jetpack Metrics (JankStats) e AndroidX Tracing

Il modulo facoltativo `engine-diagnostics` (F8.24) dipende, **come librerie binarie** (non come sorgente), da due
librerie di Jetpack.

- **JankStats** (`androidx.metrics:metrics-performance:1.0.0`), di The Android Open Source Project: i fotogrammi lenti per
  pagina, sopra `FrameMetrics` di Android.
  - Repository: https://android.googlesource.com/platform/frameworks/support/+/androidx-main/metrics/
- **AndroidX Tracing** (`androidx.tracing:tracing:1.2.0`), di The Android Open Source Project: gli intervalli delle tracce
  dei tempi, **solo nella build di debug** del modulo (`debugImplementation`). La stessa versione arriva comunque in ogni
  app con `androidx.core`.
  - Repository: https://android.googlesource.com/platform/frameworks/support/+/androidx-main/tracing/
- Versioni fissate in `versions.gradle` (`engine.metricsPerformance`, `engine.tracing`).
- Licenza di tutte e due: **Apache License 2.0** (dichiarata nel POM degli artefatti su Google Maven, letto il
  26/11/2026).

Apache 2.0 e' compatibile con la GPL-3.0 dell'engine (la Free Software Foundation la elenca fra le licenze compatibili
con la GPL versione 3): non serve un permesso aggiuntivo.

## Cosa usiamo

| Dove sta da noi | Cosa usa |
|---|---|
| `engine-diagnostics/src/main/kotlin/dev/antigravity/fluidengine/diagnostics/FluidFrames.kt` | `JankStats.createAndTrack`, `FrameData`, `FrameDataApi24`, `FrameDataApi31`, `PerformanceMetricsState` |
| `engine-diagnostics/src/debug/kotlin/dev/antigravity/fluidengine/diagnostics/FluidTraceBackend.kt` | `androidx.tracing.Trace` (sezioni, sezioni asincrone, contatori) |

La somma per pagina, la coda, il consenso, i rapporti e l'invio sono dell'engine (Kotlin): non c'e' codice delle due
librerie copiato nell'engine. L'avviso viaggia con l'app nell'elenco dei crediti: un'app che include
`engine-diagnostics` aggiunge `FluidDiagnosticsLicenses` a `fluidLicensesSection(licences = …)`. Il testo della licenza
e' in https://www.apache.org/licenses/LICENSE-2.0.
