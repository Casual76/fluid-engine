package dev.antigravity.fluidengine.ui.fluid

// La piattaforma finta delle prove JVM del contenitore web: un thread principale con un orologio che avanza
// solo quando la prova lo dice, un provider di profili e una vista con il suo renderer. Nessuna classe Android.

/** Il thread principale finto: una coda di azioni a tempo, eseguite in ordine quando l'orologio avanza. */
internal class FakeMainThread {
  private class Task(val at: Long, val order: Int, val owner: Any?, val action: () -> Unit)

  private val queue = ArrayList<Task>()
  private var order = 0
  var now = 0L
    private set
  /** Falso per simulare una chiamata arrivata da un altro thread. */
  var onMain = true

  val pending: Int get() = queue.size
  val nextAt: Long? get() = queue.minOfOrNull { it.at }

  fun require() = check(onMain) { "fuori dal thread principale" }

  fun schedule(delayMillis: Long, owner: Any? = null, action: () -> Unit) {
    queue += Task(now + delayMillis, order++, owner, action)
  }

  fun cancel(owner: Any) {
    queue.removeAll { it.owner === owner }
  }

  /** Porta l'orologio avanti di [millis], eseguendo nell'ordine tutto cio' che scade nel frattempo. */
  fun advanceBy(millis: Long) {
    val end = now + millis
    while (true) {
      val next = queue.filter { it.at <= end }.minWithOrNull(compareBy<Task> { it.at }.thenBy { it.order }) ?: break
      queue.remove(next)
      now = next.at
      next.action()
    }
    now = end
  }

  /** Esegue tutto cio' che e' in coda, fino a svuotarla. */
  fun runAll() {
    while (queue.isNotEmpty()) advanceBy(nextAt!! - now)
  }
}

/** Il provider finto dei profili. Le viste sono stringhe; [log] registra ogni chiamata che cambia qualcosa. */
internal class FakeProfileProvider(private val main: FakeMainThread) : ProfilePlatform<String> {
  var multi = true
  var canClear = true
  /** I profili che il provider conosce: quelli lasciati dal processo precedente piu' quelli creati qui. */
  val store = LinkedHashSet<String>()
  /** I profili caricati in questo processo: il provider rifiuta di eliminarli. */
  val loaded = HashSet<String>()
  /** Vista -> profilo, per ogni assegnazione riuscita. */
  val assigned = LinkedHashMap<String, String>()
  /** Le pulizie partite e non ancora risposte, nell'ordine. */
  val clearing = ArrayList<Pair<String, () -> Unit>>()
  val log = ArrayList<String>()
  /** Identificativi da dare ai prossimi profili nuovi, prima di passare a quelli numerati. */
  val forcedIds = ArrayDeque<String>()
  var assignFailure: RuntimeException? = null
  var clearFailure: RuntimeException? = null
  var deleteFailure: RuntimeException? = null
  var listings = 0
  private var ids = 0

  override fun requireMainThread() = main.require()
  override val multiProfile: Boolean get() = multi
  override val deleteBrowsingData: Boolean get() = canClear
  override fun newId(): String = forcedIds.removeFirstOrNull() ?: "n${++ids}"

  override fun existingNames(): Collection<String> {
    listings++
    return store.toList()
  }

  override fun assign(view: String, name: String) {
    assignFailure?.let { throw it }
    store += name
    loaded += name
    assigned[view] = name
    log += "assign $view $name"
  }

  override fun clearData(name: String, done: () -> Unit): Boolean {
    clearFailure?.let { throw it }
    if (name !in store) return false
    log += "clear $name"
    clearing += name to done
    return true
  }

  override fun delete(name: String) {
    log += "delete $name"
    deleteFailure?.let { throw it }
    check(name !in loaded) { "profilo caricato in questo processo" }
    store -= name
  }

  override fun schedule(delayMillis: Long, action: () -> Unit) = main.schedule(delayMillis, this, action)

  /** Il provider risponde alla pulizia piu' vecchia di [name]. */
  fun answerClear(name: String) {
    val index = clearing.indexOfFirst { it.first == name }
    check(index >= 0) { "nessuna pulizia in corso per $name" }
    clearing.removeAt(index).second()
  }

  fun deleted(): List<String> = log.filter { it.startsWith("delete ") }.map { it.removePrefix("delete ") }
}

/** La vista finta che si chiude, con il suo renderer. [terminate] risponde secondo [answers], poi [afterAnswers]. */
internal class FakeClosingView(private val main: FakeMainThread) : ClosingPlatform {
  /** Che cosa risponde `acquireRenderer`. */
  var rendererAvailable = true
  val answers = ArrayDeque<Boolean?>()
  var afterAnswers: Boolean? = false
  /** Eseguito dentro `terminate()`, prima di rispondere: per una morte riportata durante la chiamata. */
  var duringTerminate: (() -> Unit)? = null
  val log = ArrayList<String>()
  /** L'ora di ogni chiamata a `terminate()`. */
  val terminateTimes = ArrayList<Long>()
  var destroyedAt: Long? = null
    private set
  var destroys = 0
    private set

  override fun requireMainThread() = main.require()

  override fun acquireRenderer(): Boolean {
    log += "acquire"
    return rendererAvailable
  }

  override fun takeOver() {
    log += "takeOver"
  }

  override fun terminate(): Boolean? {
    log += "terminate"
    terminateTimes += main.now
    duringTerminate?.invoke()
    return if (answers.isNotEmpty()) answers.removeFirst() else afterAnswers
  }

  override fun destroyView() {
    log += "destroy"
    destroys++
    destroyedAt = main.now
  }

  override fun schedule(delayMillis: Long, action: () -> Unit) = main.schedule(delayMillis, this, action)
  override fun cancelScheduled() = main.cancel(this)
}

/** Registra che fine fa il profilo di una vista chiusa. */
internal class RecordingReturn : ProfileReturn {
  val log = ArrayList<String>()
  override fun recycle(name: String) { log += "recycle $name" }
  override fun retire(name: String) { log += "retire $name" }
}
