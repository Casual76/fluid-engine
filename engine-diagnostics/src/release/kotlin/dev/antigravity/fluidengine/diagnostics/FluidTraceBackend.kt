package dev.antigravity.fluidengine.diagnostics

/**
 * Le tracce della build di **release** (F8.24): niente. [Enabled] è una costante falsa, quindi le funzioni `inline` di
 * [FluidTrace] perdono il ramo delle tracce già quando l'app si compila, e qui non c'è nessuna chiamata a Trace (questo
 * sorgente non importa neanche AndroidX Tracing). Lo prova FluidTraceReleaseTest.
 */
@PublishedApi
internal object FluidTraceBackend {
  /** Falso: la release non scrive tracce. */
  const val Enabled: Boolean = false

  @Suppress("FunctionOnlyReturningConstant")
  fun isRecording(): Boolean = false

  @Suppress("UNUSED_PARAMETER")
  fun begin(name: String) = Unit

  fun end() = Unit

  @Suppress("UNUSED_PARAMETER")
  fun beginAsync(name: String, cookie: Int) = Unit

  @Suppress("UNUSED_PARAMETER")
  fun endAsync(name: String, cookie: Int) = Unit

  @Suppress("UNUSED_PARAMETER")
  fun counter(name: String, value: Long) = Unit
}
