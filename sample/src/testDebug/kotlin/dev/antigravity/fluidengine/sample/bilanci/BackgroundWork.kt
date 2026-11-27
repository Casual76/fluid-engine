package dev.antigravity.fluidengine.sample.bilanci

/**
 * F7.32 · Il lavoro in sottofondo delle scene, sull'orologio della prova.
 *
 * Molte scene finiscono di arrivare con un lavoro su `Dispatchers.Default` o `Dispatchers.IO` (la sincronia simulata di
 * F8.21, il riconoscimento dei dati nel testo di F8.9): quel lavoro va in tempo vero, mentre l'orologio della prova corre
 * a passi di 16 ms. Su un PC scarico arrivava presto, su un PC carico qualche fotogramma (o qualche secondo) dopo, e le
 * righe dei bilanci cadevano «a volte»: catture in più nell'entrata (`sync-due-dispositivi`, 2 corse su 6 in F7.29), un
 * menu aperto nel moto (`dati-nel-testo`, 1 su 10).
 *
 * [await] tiene fermo l'orologio della prova finché i fili di `Default` e `IO` (uno scheduler solo, in kotlinx.coroutines)
 * non hanno più niente in corso né in coda: così un lavoro in sottofondo dura, per la prova, meno di un fotogramma, come
 * sul telefono, dove le stesse cose prendono pochi millisecondi. Quello che torna al filo principale arriva al
 * fotogramma dopo, a ogni corsa. Il banco lo fa per le scene che il loro frammento dichiara (`@sottofondo`). Il lavoro su
 * fili propri (un `HandlerThread`, un `Executor`) e le attese in tempo vero su quei fili (`delay` su `Default`, una
 * fotocamera finta a 30 fotogrammi al secondo) restano fuori: lì c'è ancora l'assestamento di [BalanceRuns].
 *
 * Lo stato si legge da `CoroutineScheduler.toString()` (kotlinx.coroutines 1.8: «Worker States {CPU = …, blocking = …}»,
 * le code, «blocking tasks = …»). Se un giorno il testo cambia, [available] è falso, la prova lo dice e il banco va come
 * prima: niente che cade in silenzio.
 */
internal object BackgroundWork {
  /**
   * Al più quanto si aspetta prima di un fotogramma: oltre, è un lavoro che non finisce (lo si conta e si va avanti).
   * Largo apposta: deve stare sopra il ritardo che [BalanceOrderTest] mette in sottofondo (1,5 s) e sopra un PC carico.
   */
  private const val CapNanos = 10_000_000_000L

  private val scheduler: Any? = runCatching {
    val owner = Class.forName("kotlinx.coroutines.scheduling.DefaultScheduler")
    val instance = owner.getField("INSTANCE").get(null)
    var type: Class<*>? = owner
    var field: java.lang.reflect.Field? = null
    while (type != null && field == null) {
      field = type.declaredFields.firstOrNull { it.name == "coroutineScheduler" }
      type = type.superclass
    }
    checkNotNull(field).apply { isAccessible = true }.get(instance)
  }.getOrNull()

  private val workers = Regex("""Worker States \{CPU = (\d+), blocking = (\d+)""")
  private val queues = Regex("""running workers queues = \[([^\]]*)]""")
  private val globalCpu = Regex("""global CPU queue size = (\d+)""")
  private val globalBlocking = Regex("""global blocking queue size = (\d+)""")
  private val blockingTasks = Regex("""blocking tasks = (\d+)""")

  /** Se lo stato dello scheduler si legge (vedi la classe). */
  val available: Boolean = scheduler != null && busyOrNull() != null

  /** Quante volte un'attesa è arrivata al tetto ([CapNanos]): un lavoro in sottofondo che non finisce. */
  var cappedWaits: Int = 0
    private set

  /** Vero se qualcosa gira o aspetta su `Default` o `IO`; null se lo stato non si legge. */
  private fun busyOrNull(): Boolean? {
    val text = scheduler?.toString() ?: return null
    val state = workers.find(text) ?: return null
    val cpu = state.groupValues[1].toInt()
    val blocking = state.groupValues[2].toInt()
    val local = queues.find(text)?.groupValues?.get(1) ?: return null
    val localBusy = local.split(',').any { entry -> entry.trim().takeWhile(Char::isDigit).toIntOrNull()?.let { it > 0 } == true }
    val cpuQueue = globalCpu.find(text)?.groupValues?.get(1)?.toInt() ?: return null
    val blockingQueue = globalBlocking.find(text)?.groupValues?.get(1)?.toInt() ?: return null
    val pendingBlocking = blockingTasks.find(text)?.groupValues?.get(1)?.toInt() ?: return null
    return cpu > 0 || blocking > 0 || localBusy || cpuQueue > 0 || blockingQueue > 0 || pendingBlocking > 0 || ownThreadsBusy()
  }

  /**
   * F7.32c · I fili propri dell'engine, che lo scheduler di kotlinx.coroutines non vede: il riconoscimento del testo
   * (Tesseract, `fluid-ocr`), gli indici di ricerca (`fluid-search-<nome>`), il modello dei vettori (`fluid-embedder`). Sono
   * esecutori a filo singolo: fermi aspettano il prossimo compito (`WAITING`), al lavoro sono `RUNNABLE` (o `BLOCKED`).
   */
  private fun isOwnThread(name: String): Boolean =
    name.startsWith("fluid-ocr") || name.startsWith("fluid-search-") || name.startsWith("fluid-embedder")

  private fun ownThreadsBusy(): Boolean {
    var group = Thread.currentThread().threadGroup
    while (group.parent != null) group = group.parent
    val threads = arrayOfNulls<Thread>(group.activeCount() + 16)
    val count = group.enumerate(threads, true)
    for (i in 0 until count) {
      val thread = threads[i] ?: continue
      if (!isOwnThread(thread.name)) continue
      val state = thread.state
      if (state == Thread.State.RUNNABLE || state == Thread.State.BLOCKED) return true
    }
    return false
  }

  /**
   * Per [BalanceOrderTest]: il PC carico, a comando e senza consumare CPU. Ogni filo di `Default` e di `IO` resta occupato
   * per [millis] da un'attesa che dorme, così il lavoro in sottofondo che la scena chiede adesso parte solo dopo.
   */
  fun occupy(millis: Long) {
    // Un'occupazione alla volta: senza la barriera le scene si susseguono più in fretta delle attese, e le attese si
    // accumulavano in coda, rallentando anche le prove dopo nella stessa JVM.
    val now = System.nanoTime()
    if (now < occupiedUntil) return
    occupiedUntil = now + millis * 1_000_000L
    val cores = Runtime.getRuntime().availableProcessors()
    val sleep = Runnable { Thread.sleep(millis) }
    val default = kotlinx.coroutines.Dispatchers.Default
    val io = kotlinx.coroutines.Dispatchers.IO
    repeat(cores) { default.dispatch(kotlin.coroutines.EmptyCoroutineContext, sleep) }
    repeat(IoThreads) { io.dispatch(kotlin.coroutines.EmptyCoroutineContext, sleep) }
  }

  @Volatile
  private var occupiedUntil = 0L

  /** I fili di `Dispatchers.IO` di serie (`kotlinx.coroutines.io.parallelism`). */
  private const val IoThreads = 64

  /** Aspetta che `Default` e `IO` siano fermi (al più [CapNanos]). Dal filo della prova, prima di un fotogramma. */
  fun await() {
    if (!available) return
    val end = System.nanoTime() + CapNanos
    // Fermi due volte di fila, a un millisecondo: un compito appena consegnato a un filo proprio lo sveglia un attimo dopo.
    var quiet = 0
    while (quiet < 2) {
      if (System.nanoTime() > end) {
        cappedWaits++
        return
      }
      quiet = if (busyOrNull() == true) 0 else quiet + 1
      if (quiet < 2) Thread.sleep(1)
    }
  }
}
