package dev.antigravity.fluidengine.diagnostics.testing

import android.content.Context
import dev.antigravity.fluidengine.diagnostics.FluidDiagnostics
import dev.antigravity.fluidengine.diagnostics.FluidDiagnosticsConfig
import dev.antigravity.fluidengine.diagnostics.FluidDiagnosticsTestHooks
import dev.antigravity.fluidengine.diagnostics.FluidDiagnosticsTransport
import java.io.File

/**
 * Per le prove delle app (e della galleria) con engine-diagnostics (F8.24): una diagnostica che non si installa nel
 * processo, col trasporto e l'orologio finti e il lavoro sul filo di chi chiama, e i crash e i fotogrammi messi a mano.
 * Non serve in un'app vera: lì c'è [FluidDiagnostics.install].
 */
object FluidDiagnosticsTesting {
  /**
   * Una diagnostica nuova sulla cartella [directory] (svuotata) e sulle `SharedPreferences` dell'app (svuotate): come il
   * primo avvio. Niente gestore dei crash, niente JankStats.
   */
  fun create(
    context: Context,
    config: FluidDiagnosticsConfig,
    transport: FluidDiagnosticsTransport = FluidDiagnosticsTransport { _, _ -> 200 },
    clock: () -> Long = System::currentTimeMillis,
    directory: File = File(context.cacheDir, "fluid-diagnostics-prova"),
  ): FluidDiagnostics = FluidDiagnosticsTestHooks.create(context, config, transport, clock, directory)

  /** Scrive nella coda il crash di [error], come farebbe il gestore dei crash. */
  fun recordCrash(diagnostics: FluidDiagnostics, error: Throwable, mainThread: Boolean = true) =
    FluidDiagnosticsTestHooks.recordCrash(diagnostics, error, mainThread)

  /** Conta un fotogramma della pagina [page], come JankStats. */
  fun frame(diagnostics: FluidDiagnostics, page: String, durationMillis: Long, overrunMillis: Long, isJank: Boolean) =
    FluidDiagnosticsTestHooks.frame(diagnostics, page, durationMillis, overrunMillis, isJank)

  /** Passa i fotogrammi contati alla coda, come quando l'app esce dallo schermo. */
  fun flushFrames(diagnostics: FluidDiagnostics) = FluidDiagnosticsTestHooks.flushFrames(diagnostics)

  /** Dimentica la diagnostica installata nel processo: la prova dopo parte come un processo nuovo. */
  fun reset() = FluidDiagnosticsTestHooks.reset()
}
