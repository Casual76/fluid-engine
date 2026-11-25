package dev.antigravity.fluidengine.ui.fluid.viewer

import androidx.annotation.RestrictTo
import androidx.compose.runtime.RememberObserver
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryBudget
import java.io.Closeable
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Trasferisce al lettore Compose il credito della bitmap consegnata, anche attraverso una sorgente delegata. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class FluidPageRead(val optional: Boolean = false) : AbstractCoroutineContextElement(Key), Closeable, RememberObserver {
  companion object Key : CoroutineContext.Key<FluidPageRead>
  private var owner: FluidMemoryBudget.Resident? = null
  private var readers: java.util.concurrent.atomic.AtomicInteger? = null
  private var closed = false
  val managed get() = synchronized(this) { owner != null }

  /** Prende in carico il credito. Una consegna tardiva a un lettore uscito lo restituisce subito. */
  @Synchronized fun take(credit: FluidMemoryBudget.Resident, readerCount: java.util.concurrent.atomic.AtomicInteger? = null) {
    if (closed) credit.close() else {
      owner?.close()
      readers?.decrementAndGet()
      readers = readerCount.takeUnless { optional }
      readers?.incrementAndGet()
      owner = credit
    }
  }

  /** Aggiunge un lettore prima di rimuovere il proprietario della cache. */
  fun retainInto(reader: FluidPageRead) {
    val copy = synchronized(this) { owner?.retain()?.let { it to readers } } ?: return
    reader.take(copy.first, copy.second)
  }

  override fun onRemembered() = Unit
  override fun onForgotten() = close()
  override fun onAbandoned() = close()

  @Synchronized override fun close() {
    closed = true
    owner?.close()
    owner = null
    readers?.decrementAndGet()
    readers = null
  }
}

/**
 * F8.18 · La resa chiesta dal preset delle pagine **per lo schermo**, mai per la stampa o per un'immagine da
 * condividere. Una sorgente che lo sa (il PDF di engine-viewer) può consegnare una bitmap già nella memoria grafica
 * (`Bitmap.Config.HARDWARE`), caricata fuori dal RenderThread: così la prima volta che la pagina si disegna non c'è il
 * caricamento della texture dentro il fotogramma, che a 120 Hz è uno scatto. Una bitmap così non si legge e non si
 * disegna in un `Canvas` software: per questo vale solo dentro questo contesto.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object FluidPageDisplay : CoroutineContext.Element, CoroutineContext.Key<FluidPageDisplay> {
  override val key: CoroutineContext.Key<*> get() = this
}
