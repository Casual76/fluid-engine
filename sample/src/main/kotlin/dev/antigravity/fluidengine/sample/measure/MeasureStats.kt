package dev.antigravity.fluidengine.sample.measure

import java.util.Locale

/**
 * I numeri di una serie di durate: quanti campioni, mediana, 90° e 99° percentile, massimo. In
 * nanosecondi, come arrivano da `FrameMetrics`.
 *
 * Puro e senza Android attorno: è la parte della pagina «Misure» di cui si può dire se è giusta senza
 * un dispositivo.
 */
internal class MeasureSummary(
  val count: Int,
  val p50: Long,
  val p90: Long,
  val p99: Long,
  val max: Long,
) {
  /** `p50=3.21 p90=4.10 p99=7.80 max=12.30`, in millisecondi. */
  fun millis(): String =
    "p50=${millis(p50)} p90=${millis(p90)} p99=${millis(p99)} max=${millis(max)}"

  companion object {
    val Empty = MeasureSummary(0, 0L, 0L, 0L, 0L)

    /** Riassume i primi [count] valori di [values]. Non tocca l'array: ne ordina una copia. */
    fun of(values: LongArray, count: Int = values.size): MeasureSummary {
      val n = count.coerceIn(0, values.size)
      if (n == 0) return Empty
      val sorted = values.copyOf(n)
      sorted.sort()
      return MeasureSummary(
        count = n,
        p50 = percentile(sorted, 0.50),
        p90 = percentile(sorted, 0.90),
        p99 = percentile(sorted, 0.99),
        max = sorted[n - 1],
      )
    }

    /**
     * Il percentile col metodo del rango più vicino: il più piccolo valore sotto cui sta almeno la
     * frazione [fraction] dei campioni. Niente interpolazione, perché un tempo di fotogramma che non è
     * mai esistito non è una misura. [sorted] è in ordine crescente.
     */
    fun percentile(sorted: LongArray, fraction: Double): Long {
      if (sorted.isEmpty()) return 0L
      val rank = Math.ceil(fraction.coerceIn(0.0, 1.0) * sorted.size).toInt()
      return sorted[(rank - 1).coerceIn(0, sorted.size - 1)]
    }

    /** Nanosecondi in millisecondi con due decimali e sempre col punto. */
    fun millis(nanos: Long): String = String.format(Locale.ROOT, "%.2f", nanos / 1_000_000.0)
  }
}

/**
 * I fotogrammi di una finestra di misura, una colonna per grandezza e una riga per fotogramma.
 *
 * - [total] è `TOTAL_DURATION`: dall'arrivo del vsync alla consegna del fotogramma;
 * - [ui] è il filo principale: ingresso, animazioni, misura e disegno;
 * - [render] è il RenderThread: sincronizzazione, comandi, scambio dei buffer. È qui che il vetro
 *   costa: la catena degli effetti gira mentre il RenderThread emette i comandi;
 * - [gpu] è `GPU_DURATION` (da Android 12; zero dove il dispositivo non lo dichiara);
 * - [deadline] è il bilancio del fotogramma (da Android 12), [vsync] l'istante a cui appartiene.
 */
internal class MeasureFrames(capacity: Int) {
  val vsync = LongArray(capacity)
  val total = LongArray(capacity)
  val ui = LongArray(capacity)
  val render = LongArray(capacity)
  val gpu = LongArray(capacity)
  val deadline = LongArray(capacity)

  var count = 0
    private set

  /** Fotogrammi che `FrameMetrics` non è riuscito a consegnare: se non è zero la finestra è monca. */
  var dropped = 0

  /** Vero se non c'era più posto: la finestra è più lunga della capacità, e i fotogrammi in più si perdono. */
  var overflowed = false
    private set

  fun add(vsyncNanos: Long, totalNanos: Long, uiNanos: Long, renderNanos: Long, gpuNanos: Long, deadlineNanos: Long) {
    if (count >= vsync.size) {
      overflowed = true
      return
    }
    vsync[count] = vsyncNanos
    total[count] = totalNanos
    ui[count] = uiNanos
    render[count] = renderNanos
    gpu[count] = gpuNanos
    deadline[count] = deadlineNanos
    count++
  }

  /**
   * Quanti fotogrammi hanno sforato il bilancio. Dove il sistema dichiara il bilancio del singolo
   * fotogramma (`DEADLINE`) vale quello; altrimenti [fallbackBudgetNanos], il periodo dello schermo.
   */
  fun overBudget(fallbackBudgetNanos: Long): Int {
    var over = 0
    for (index in 0 until count) {
      val budget = if (deadline[index] > 0L) deadline[index] else fallbackBudgetNanos
      if (budget > 0L && total[index] > budget) over++
    }
    return over
  }

  /** Il massimo di [column] fra i fotogrammi il cui vsync cade in `[fromNanos, toNanos]`, o -1. */
  fun maxBetween(column: LongArray, fromNanos: Long, toNanos: Long): Long {
    var best = -1L
    for (index in 0 until count) {
      if (vsync[index] in fromNanos..toNanos && column[index] > best) best = column[index]
    }
    return best
  }

  /** La mediana di [column] fra i fotogrammi il cui vsync cade in `[fromNanos, toNanos]`, o -1. */
  fun medianBetween(column: LongArray, fromNanos: Long, toNanos: Long): Long {
    val picked = LongArray(count)
    var n = 0
    for (index in 0 until count) {
      if (vsync[index] in fromNanos..toNanos) picked[n++] = column[index]
    }
    return if (n == 0) -1L else MeasureSummary.of(picked, n).p50
  }
}

/**
 * La riga di logcat di una finestra: tutto su una riga, `chiave=valore`, così si filtra con `grep` e si
 * incolla in una tabella senza interpretare niente.
 */
internal fun measureWindowLine(
  key: String,
  run: Int,
  runs: Int,
  frames: MeasureFrames,
  refreshHz: Float,
  extra: String = "",
): String {
  val budgetNanos = if (refreshHz > 0f) (1_000_000_000.0 / refreshHz).toLong() else 0L
  val over = frames.overBudget(budgetNanos)
  val share = if (frames.count > 0) 100.0 * over / frames.count else 0.0
  val builder = StringBuilder()
  builder.append("RIS ").append(key)
  builder.append(" run=").append(run).append('/').append(runs)
  builder.append(" frames=").append(frames.count)
  builder.append(" hz=").append(String.format(Locale.ROOT, "%.0f", refreshHz))
  builder.append(" budget_ms=").append(MeasureSummary.millis(budgetNanos))
  builder.append(" | total_ms ").append(MeasureSummary.of(frames.total, frames.count).millis())
  builder.append(" | ui_ms ").append(MeasureSummary.of(frames.ui, frames.count).millis())
  builder.append(" | render_ms ").append(MeasureSummary.of(frames.render, frames.count).millis())
  builder.append(" | gpu_ms ").append(MeasureSummary.of(frames.gpu, frames.count).millis())
  builder.append(" | over=").append(over)
  builder.append(" over_pct=").append(String.format(Locale.ROOT, "%.2f", share))
  builder.append(" dropped=").append(frames.dropped)
  if (frames.overflowed) builder.append(" OVERFLOW")
  if (extra.isNotEmpty()) builder.append(" | ").append(extra)
  return builder.toString()
}

/**
 * La riga di chiusura: la dispersione fra le ripetizioni. Per ogni grandezza, il minimo, la mediana e
 * il massimo della **mediana** e del **90° percentile** delle finestre. Se minimo e massimo sono
 * lontani la scena non è ripetibile e il numero non decide niente.
 */
internal fun measureSpreadLine(key: String, windows: List<MeasureFrames>): String {
  fun spread(pick: (MeasureFrames) -> Long): String {
    val values = LongArray(windows.size) { pick(windows[it]) }
    values.sort()
    if (values.isEmpty()) return "-"
    val median = MeasureSummary.percentile(values, 0.5)
    return "${MeasureSummary.millis(values.first())}/${MeasureSummary.millis(median)}/${MeasureSummary.millis(values.last())}"
  }
  return "SPREAD $key runs=${windows.size} (min/med/max)" +
    " total_p50=" + spread { MeasureSummary.of(it.total, it.count).p50 } +
    " total_p90=" + spread { MeasureSummary.of(it.total, it.count).p90 } +
    " render_p50=" + spread { MeasureSummary.of(it.render, it.count).p50 } +
    " render_p90=" + spread { MeasureSummary.of(it.render, it.count).p90 } +
    " gpu_p50=" + spread { MeasureSummary.of(it.gpu, it.count).p50 }
}
