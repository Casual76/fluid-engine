package dev.antigravity.fluidengine.ui.glass.backdrop

/**
 * F7.32c · L'orologio delle decisioni di cattura del vetro ([DrawBackdropNode]): quanto a lungo si tiene una cattura
 * mentre il vetro si piega (`HoldRefreshMillis`, 110 ms), lo spazio minimo fra due ricampionamenti
 * (`resampleIntervalMillis`) e da quanto la sorgente non c'è.
 *
 * Sul dispositivo è `System.currentTimeMillis()`, com'era: stesso orologio, stessi intervalli. Le prove di Robolectric lo
 * mettono sull'orologio della prova ([clockForTesting]): lì i fotogrammi avanzano di 16 ms simulati, ma ognuno dura in
 * tempo vero quanto il PC ci mette a disegnarlo, e con l'orologio di sistema le catture «ogni 110 ms» dipendevano da
 * quanto era carico il PC (le catture dell'entrata di `sync-due-dispositivi`, 11 su 9 a volte). Non è un'API per le app.
 */
internal object GlassCaptureClock {
  /** Per le prove: l'orologio da usare al posto di quello di sistema, in millisecondi; null (di serie) = di sistema. */
  @Volatile
  var clockForTesting: (() -> Long)? = null

  /** Adesso, in millisecondi. */
  fun nowMillis(): Long = clockForTesting?.invoke() ?: System.currentTimeMillis()
}
