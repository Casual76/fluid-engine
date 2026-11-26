package dev.antigravity.fluidengine.sample

import android.os.Trace
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEngine
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHapticGrainStyle
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics

/**
 * F8.15d · L'aptica con le sue sezioni nella traccia di Perfetto: `FluidHaptics.play:<evento>` e `FluidHaptics.grain`
 * attorno a quello che fa l'aptica del tema (la vibrazione, il binder verso il servizio). Serve a vedere quanto tempo del
 * thread dell'interfaccia va all'aptica mentre il dito muove un righello. Solo nella build `bench`
 * ([BuildConfig.EXTRAS_WITHOUT_DEBUGGABLE]); in debug e nella galleria pubblicata non c'e'. Con la traccia spenta una
 * sezione costa un controllo di un flag: i nomi sono preparati una volta sola, niente stringhe per evento.
 */
internal class TracedFluidHaptics(private val inner: FluidHaptics) : FluidHaptics {
  /** Quante volte e' stata chiamata (per le prove al PC). */
  var plays: Int = 0
    private set

  override val engine: FluidHapticEngine
    get() = inner.engine

  override fun play(event: FluidHapticEvent) {
    plays++
    Trace.beginSection(PlayNames[event.ordinal])
    try {
      inner.play(event)
    } finally {
      Trace.endSection()
    }
  }

  override fun grain(intensity: Float, style: FluidHapticGrainStyle) {
    plays++
    Trace.beginSection("FluidHaptics.grain")
    try {
      inner.grain(intensity, style)
    } finally {
      Trace.endSection()
    }
  }

  private companion object {
    val PlayNames: Array<String> = FluidHapticEvent.entries.map { "FluidHaptics.play:${it.name}" }.toTypedArray()
  }
}

/** Sotto il tema della galleria: nella `bench` l'aptica passa da [TracedFluidHaptics], altrove resta com'e'. */
@Composable
internal fun BenchHapticsTrace(enabled: Boolean = BuildConfig.EXTRAS_WITHOUT_DEBUGGABLE, content: @Composable () -> Unit) {
  if (!enabled) {
    content()
    return
  }
  val inner = LocalFluidHaptics.current
  val traced = remember(inner) { TracedFluidHaptics(inner) }
  CompositionLocalProvider(LocalFluidHaptics provides traced, content = content)
}
