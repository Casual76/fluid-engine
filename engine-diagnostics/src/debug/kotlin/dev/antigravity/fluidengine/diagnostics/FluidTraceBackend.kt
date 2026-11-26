package dev.antigravity.fluidengine.diagnostics

import androidx.tracing.Trace

/**
 * Le tracce della build di **debug** (F8.24): AndroidX Tracing, che scrive in `atrace` e quindi in Perfetto. La release ha
 * lo stesso oggetto vuoto in `src/release`, con [Enabled] falso.
 */
@PublishedApi
internal object FluidTraceBackend {
  /** Vero: questa build scrive le tracce. */
  const val Enabled: Boolean = true

  fun isRecording(): Boolean = Trace.isEnabled()

  fun begin(name: String) = Trace.beginSection(name)

  fun end() = Trace.endSection()

  fun beginAsync(name: String, cookie: Int) = Trace.beginAsyncSection(name, cookie)

  fun endAsync(name: String, cookie: Int) = Trace.endAsyncSection(name, cookie)

  fun counter(name: String, value: Long) = Trace.setCounter(name, value.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt())
}
