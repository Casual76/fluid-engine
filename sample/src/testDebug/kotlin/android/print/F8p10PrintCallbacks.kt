package android.print

import java.util.concurrent.CountDownLatch

/**
 * R2 · Le risposte di un adattatore di stampa, per le prove: i costruttori delle callback di `PrintDocumentAdapter` sono
 * del pacchetto `android.print`, quindi le prove le estendono da qui. Ricordano su che thread arriva la risposta e quante
 * volte (una sola: lo spooler non ne aspetta due).
 */
class F8p10LayoutCallback : PrintDocumentAdapter.LayoutResultCallback() {
  val done = CountDownLatch(1)
  @Volatile var thread: Thread? = null
  @Volatile var info: PrintDocumentInfo? = null
  @Volatile var answers = 0
  @Volatile var failure: CharSequence? = null
  @Volatile var cancelled = false

  private fun answer() {
    thread = Thread.currentThread()
    answers++
    done.countDown()
  }

  override fun onLayoutFinished(info: PrintDocumentInfo, changed: Boolean) {
    this.info = info
    answer()
  }

  override fun onLayoutFailed(error: CharSequence?) {
    failure = error ?: "?"
    answer()
  }

  override fun onLayoutCancelled() {
    cancelled = true
    answer()
  }
}

/** Come [F8p10LayoutCallback], per la scrittura. */
class F8p10WriteCallback : PrintDocumentAdapter.WriteResultCallback() {
  val done = CountDownLatch(1)
  @Volatile var thread: Thread? = null
  @Volatile var answers = 0
  @Volatile var pages: Array<out PageRange>? = null
  @Volatile var failure: CharSequence? = null
  @Volatile var cancelled = false

  private fun answer() {
    thread = Thread.currentThread()
    answers++
    done.countDown()
  }

  override fun onWriteFinished(pages: Array<out PageRange>) {
    this.pages = pages
    answer()
  }

  override fun onWriteFailed(error: CharSequence?) {
    failure = error ?: "?"
    answer()
  }

  override fun onWriteCancelled() {
    cancelled = true
    answer()
  }
}
