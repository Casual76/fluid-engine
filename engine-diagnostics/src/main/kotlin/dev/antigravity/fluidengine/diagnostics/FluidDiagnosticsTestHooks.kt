package dev.antigravity.fluidengine.diagnostics

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers

/** Le porte delle prove, per [dev.antigravity.fluidengine.diagnostics.testing.FluidDiagnosticsTesting]. */
internal object FluidDiagnosticsTestHooks {
  fun create(
    context: Context,
    config: FluidDiagnosticsConfig,
    transport: FluidDiagnosticsTransport,
    clock: () -> Long,
    directory: File,
  ): FluidDiagnostics {
    directory.deleteRecursively()
    context.applicationContext.getSharedPreferences(FluidDiagnostics.PreferencesName, Context.MODE_PRIVATE).edit().clear().commit()
    return FluidDiagnostics.create(context, config, transport, clock, Dispatchers.Unconfined, directory)
  }

  fun recordCrash(diagnostics: FluidDiagnostics, error: Throwable, mainThread: Boolean) = diagnostics.recordCrash(error, mainThread)

  fun frame(diagnostics: FluidDiagnostics, page: String, durationMillis: Long, overrunMillis: Long, isJank: Boolean) =
    diagnostics.frameAggregator.onFrame(fluidSafePageName(page), durationMillis * 1_000_000, overrunMillis * 1_000_000, isJank)

  fun flushFrames(diagnostics: FluidDiagnostics) = diagnostics.flushFrames()

  fun reset() = FluidDiagnostics.resetForTesting()
}
