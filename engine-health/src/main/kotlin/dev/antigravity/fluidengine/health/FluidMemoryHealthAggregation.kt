/*
 * engine-health (4.0, F8.26b): le aggregazioni del Health Connect finto in memoria, fatte come le fa Health Connect, per
 * le prove e la galleria. Non sono la logica dell'engine: i totali veri li calcola il provider. Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.health

import java.time.Duration
import java.time.Instant

/**
 * Come Health Connect calcola le aggregazioni, per il finto in memoria ([FluidMemoryHealthGateway]):
 *
 * - **attività e sonno** (passi, allenamenti, sonno): dove due app scrivono gli stessi momenti tiene solo quella più in alto
 *   nell'ordine che l'utente sceglie nelle impostazioni di Health Connect ([rank]); un tratto a cavallo dei bordi conta
 *   in proporzione al tempo;
 * - **gli altri tipi** (acqua, peso, battito): mette insieme i dati di tutte le app.
 *
 * Dalla pagina «Read aggregated data» di developer.android.com (aggiornata il 10/11/2026), sezione «Aggregate data
 * affected by user-selected apps priorities». Le app che hanno dato il totale sono le `dataOrigins`.
 */
internal object FluidMemoryHealthAggregation {
  /** I tipi di cui Health Connect toglie i doppioni con l'ordine dell'utente. */
  val Deduplicated: Set<FluidHealthMetric> = setOf(FluidHealthMetric.Steps, FluidHealthMetric.Exercise, FluidHealthMetric.Sleep)

  fun aggregate(
    metric: FluidHealthMetric,
    samples: List<FluidHealthSample>,
    from: Instant,
    to: Instant,
    rank: (FluidHealthSource) -> Int,
  ): FluidHealthAggregate {
    if (!to.isAfter(from)) return FluidHealthAggregate.empty(metric, from, to)
    val mine = samples.filter { it.metric == metric }
    return when (metric) {
      FluidHealthMetric.Steps -> spans(metric, mine.filterIsInstance<FluidHealthInterval>().map { Span(it.start, it.end, it.value, it.source) }, from, to, rank, dedupe = true)
      FluidHealthMetric.Hydration -> spans(metric, mine.filterIsInstance<FluidHealthInterval>().map { Span(it.start, it.end, it.value, it.source) }, from, to, rank, dedupe = false)
      FluidHealthMetric.Sleep, FluidHealthMetric.Exercise ->
        // Il valore di una sessione e' la sua durata in minuti: in proporzione al tempo e' il tempo dentro il tratto.
        spans(metric, mine.filterIsInstance<FluidHealthSession>().map { Span(it.start, it.end, it.duration.toMillis() / 60_000.0, it.source) }, from, to, rank, dedupe = true)
      FluidHealthMetric.HeartRate, FluidHealthMetric.Weight -> {
        val points = mine.filterIsInstance<FluidHealthPoint>().filter { !it.time.isBefore(from) && it.time.isBefore(to) }
        if (points.isEmpty()) FluidHealthAggregate.empty(metric, from, to)
        else FluidHealthAggregate(
          metric, from, to,
          min = points.minOf { it.value }, max = points.maxOf { it.value }, average = points.sumOf { it.value } / points.size,
          count = if (metric == FluidHealthMetric.HeartRate) points.size.toLong() else null,
          sources = points.map { it.source }.distinctBy { it.packageName }.sortedBy { it.packageName },
        )
      }
      else -> FluidHealthAggregate.empty(metric, from, to)
    }
  }

  private class Span(val start: Instant, val end: Instant, val value: Double, val source: FluidHealthSource)

  /**
   * La somma degli intervalli dentro [from, to): con [dedupe] ogni tratto elementare va alla fonte con il [rank] più basso
   * che lo copre (attività e sonno), senza, contano tutti (gli altri tipi). Un intervallo di durata zero conta intero.
   */
  private fun spans(metric: FluidHealthMetric, all: List<Span>, from: Instant, to: Instant, rank: (FluidHealthSource) -> Int, dedupe: Boolean): FluidHealthAggregate {
    val instants = all.filter { it.start == it.end && !it.start.isBefore(from) && it.start.isBefore(to) }
    val spans = all.filter { it.start != it.end && it.start.isBefore(to) && it.end.isAfter(from) }.sortedBy { it.start }
    if (instants.isEmpty() && spans.isEmpty()) return FluidHealthAggregate.empty(metric, from, to)
    val sources = LinkedHashMap<String, FluidHealthSource>()
    var total = 0.0
    for (p in instants) {
      total += p.value
      sources[p.source.packageName] = p.source
    }
    if (!dedupe) {
      for (s in spans) {
        val a = maxOf(s.start, from)
        val b = minOf(s.end, to)
        total += s.value * Duration.between(a, b).toNanos().toDouble() / Duration.between(s.start, s.end).toNanos().toDouble()
        sources[s.source.packageName] = s.source
      }
    } else {
      val edges = sortedSetOf<Instant>()
      for (s in spans) {
        edges.add(maxOf(s.start, from))
        edges.add(minOf(s.end, to))
      }
      val list = edges.toList()
      for (i in 0 until list.size - 1) {
        val a = list[i]
        val b = list[i + 1]
        var best: Span? = null
        for (s in spans) {
          if (s.start.isAfter(a)) break
          if (s.end >= b && (best == null || rank(s.source) < rank(best.source))) best = s
        }
        val w = best ?: continue
        total += w.value * Duration.between(a, b).toNanos().toDouble() / Duration.between(w.start, w.end).toNanos().toDouble()
        sources[w.source.packageName] = w.source
      }
    }
    return FluidHealthAggregate(metric, from, to, total = total, sources = sources.values.sortedBy { it.packageName })
  }
}
