/*
 * engine-health (4.0, F8.26b): le letture di una card dai totali di Health Connect, senza rifare la scelta delle fonti.
 * Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.health

import androidx.compose.runtime.Immutable
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Un tratto senza nessun dato, da nessuna app: Health Connect non ha un tratto con dati li'. */
@Immutable
data class FluidHealthGap(val start: Instant, val end: Instant) {
  val duration: Duration get() = Duration.between(start, end)
  override fun toString(): String = "FluidHealthGap(***)"
}

/**
 * Quando un tratto senza dati e' un **buco** da dire: lungo almeno [minimum] e dentro la giornata sveglia, fra [from] e
 * [to] (la notte senza passi non e' un buco). Di serie tre ore fra le 7 e le 23 (scelta di Alessio, F8.26): piu' corto, un
 * pomeriggio alla scrivania (il telefono non scrive niente se non si cammina) diventa un avviso falso.
 */
@Immutable
data class FluidHealthGapRule(
  val minimum: Duration = Duration.ofHours(3),
  val from: LocalTime = LocalTime.of(7, 0),
  val to: LocalTime = LocalTime.of(23, 0),
)

/** La lettura di un tratto di una misura, come la mostra una card: mai uno zero inventato. */
sealed interface FluidHealthReading {
  /** Health Connect non ha niente nel tratto: «Nessun dato», non «0». */
  data object NoData : FluidHealthReading

  /**
   * Il totale di Health Connect, dalle app [sources] (quelle che dice l'aggregazione); [gaps] sono i buchi lunghi (allora
   * la card dice «niente dalle… alle…»).
   */
  @Immutable
  data class Value(val value: Double, val sources: List<FluidHealthSource>, val gaps: List<FluidHealthGap> = emptyList()) : FluidHealthReading {
    val partial: Boolean get() = gaps.isNotEmpty()
    override fun toString(): String = "Value(***)"
  }
}

/** Il battito di un tratto, dalle statistiche di Health Connect (`BPM_MIN`, `BPM_MAX`, `BPM_AVG`, `MEASUREMENTS_COUNT`). */
@Immutable
data class FluidHeartSummary(
  val min: Double,
  val max: Double,
  val average: Double,
  val count: Long?,
  /** L'ultimo campione, se l'app l'ha letto (`FluidHealthGateway.latest`). */
  val latest: FluidHealthPoint?,
  val sources: List<FluidHealthSource>,
) {
  override fun toString(): String = "FluidHeartSummary(***)"
}

/**
 * Le letture delle card dai totali di **Health Connect**.
 *
 * **Lo gestisce il provider, noi sommiamo** (scelta di Alessio del 26/11/2026). L'engine non ha un suo ordine delle
 * fonti e non somma a mano i record che si sovrappongono: i totali sono quelli delle aggregazioni di Health Connect
 * ([FluidHealthGateway.aggregate]), che per l'attivita' e il sonno (passi, distanze, allenamenti, sonno) tengono solo i
 * dati dell'app più in alto nell'ordine che **l'utente** sceglie nelle impostazioni di Health Connect, e per gli altri tipi
 * (acqua, peso, battito) mettono insieme i dati di tutte le app (developer.android.com, «Read aggregated data», «Aggregate
 * data affected by user-selected apps priorities», aggiornata il 10/11/2026). Qui si decide solo **come dirlo**: «Nessun
 * dato» quando Health Connect non ha niente, il valore con le app che l'hanno dato (`dataOrigins`), e i buchi lunghi,
 * presi dai tratti che Health Connect da' solo dove ci sono dati (la lista dei tratti è rada).
 */
object FluidHealthReadings {
  /** Il tratto del giorno [date] nel fuso [zone], da mezzanotte a mezzanotte; quello di oggi si ferma a [now]. */
  fun dayRange(date: LocalDate, zone: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now()): Pair<Instant, Instant> {
    val from = date.atStartOfDay(zone).toInstant()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant()
    return from to if (now.isBefore(end)) maxOf(now, from) else end
  }

  /**
   * La notte che finisce il giorno [date]: dalle 18 del giorno prima alle 18 di [date] (a occhio, 26/11/2026: la sessione
   * del timer della notte di Sounds e quella dell'orologio ci stanno dentro intere).
   */
  fun nightRange(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Pair<Instant, Instant> =
    date.minusDays(1).atTime(18, 0).atZone(zone).toInstant() to date.atTime(18, 0).atZone(zone).toInstant()

  /**
   * La lettura di [aggregate]: «Nessun dato» se Health Connect non ha niente, altrimenti il suo totale con le app che
   * l'hanno dato. I [buckets] (i tratti con dati di [FluidHealthGateway.aggregateByDuration]) servono solo a dire i buchi.
   */
  fun reading(
    aggregate: FluidHealthAggregate,
    buckets: List<FluidHealthAggregate> = emptyList(),
    zone: ZoneId = ZoneId.systemDefault(),
    gapRule: FluidHealthGapRule? = FluidHealthGapRule(),
  ): FluidHealthReading {
    val value = aggregate.total ?: aggregate.average ?: return FluidHealthReading.NoData
    val gaps = if (gapRule == null) emptyList() else gaps(buckets, aggregate.start, aggregate.end, zone, gapRule)
    return FluidHealthReading.Value(value, aggregate.sources, gaps)
  }

  /**
   * I buchi lunghi fra i tratti con dati [buckets] (la lista rada di Health Connect), e dall'ultimo dato fino a [to]; solo
   * nella giornata sveglia, e mai prima del primo dato (la mattina prima di uscire non e' un buco).
   */
  fun gaps(buckets: List<FluidHealthAggregate>, from: Instant, to: Instant, zone: ZoneId, rule: FluidHealthGapRule): List<FluidHealthGap> {
    val covered = buckets.filter { it.hasData && it.end.isAfter(from) && it.start.isBefore(to) }
      .map { maxOf(it.start, from) to minOf(it.end, to) }
      .sortedBy { it.first }
    if (covered.isEmpty()) return emptyList()
    val merged = covered.fold(ArrayList<Pair<Instant, Instant>>()) { acc, span ->
      val last = acc.lastOrNull()
      if (last != null && !span.first.isAfter(last.second)) acc[acc.size - 1] = last.first to maxOf(last.second, span.second)
      else acc.add(span)
      acc
    }
    val holes = merged.zipWithNext { a, b -> a.second to b.first } + listOf(merged.last().second to to)
    val out = ArrayList<FluidHealthGap>()
    for ((a, b) in holes) {
      val day = a.atZone(zone).toLocalDate()
      val s = maxOf(a, day.atTime(rule.from).atZone(zone).toInstant())
      val e = minOf(b, day.atTime(rule.to).atZone(zone).toInstant())
      if (e.isAfter(s) && Duration.between(s, e) >= rule.minimum) out.add(FluidHealthGap(s, e))
    }
    return out
  }

  /** Il battito dalle statistiche di Health Connect; null senza dati (niente «0 bpm»). */
  fun heart(aggregate: FluidHealthAggregate, latest: FluidHealthPoint? = null): FluidHeartSummary? {
    val min = aggregate.min ?: return null
    val max = aggregate.max ?: return null
    val average = aggregate.average ?: return null
    return FluidHeartSummary(min, max, average, aggregate.count, latest, aggregate.sources)
  }

  /** L'ultima misura di un elenco (il peso letto da me): la più recente, mai una media inventata. Null senza misure. */
  fun latest(points: List<FluidHealthPoint>): FluidHealthPoint? =
    points.maxWithOrNull(compareBy<FluidHealthPoint> { it.time }.thenBy { it.lastModified ?: Instant.MIN })
}

/**
 * Il tratto dei buchi di serie: un quarto d'ora. I buchi si dicono al quarto d'ora («Niente dalle 14:15»): un giorno sono
 * 96 tratti, una richiesta sola a Health Connect. A occhio, 26/11/2026.
 */
val FluidHealthGapBucket: Duration = Duration.ofMinutes(15)

/**
 * La lettura del giorno [date] di una misura che si somma (passi, acqua, minuti di allenamento): il totale di Health
 * Connect, le app che l'hanno dato e i buchi lunghi. Oggi si ferma a [now].
 */
suspend fun FluidHealthGateway.day(
  metric: FluidHealthMetric,
  date: LocalDate,
  zone: ZoneId = ZoneId.systemDefault(),
  now: Instant = Instant.now(),
  gapRule: FluidHealthGapRule? = FluidHealthGapRule(),
): FluidHealthReading {
  val (from, to) = FluidHealthReadings.dayRange(date, zone, now)
  if (!to.isAfter(from)) return FluidHealthReading.NoData
  val total = aggregate(metric, from, to)
  if (!total.hasData) return FluidHealthReading.NoData
  val buckets = if (gapRule == null) emptyList() else aggregateByDuration(metric, from, to, FluidHealthGapBucket)
  return FluidHealthReadings.reading(total, buckets, zone, gapRule)
}

/** Le letture di [days] giorni da [first] (la settimana di una card), coi totali giorno per giorno di Health Connect. */
suspend fun FluidHealthGateway.week(
  metric: FluidHealthMetric,
  first: LocalDate,
  days: Int = 7,
  zone: ZoneId = ZoneId.systemDefault(),
): List<FluidHealthReading> = aggregateByDays(metric, first, days, zone).map { FluidHealthReadings.reading(it, gapRule = null) }

/**
 * La notte che finisce il giorno [date]: i minuti di sonno di Health Connect (`SLEEP_DURATION_TOTAL`), che tiene una sola
 * notte quando due app (Sounds e l'orologio) la scrivono, con l'ordine dell'utente.
 */
suspend fun FluidHealthGateway.night(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): FluidHealthReading {
  val (from, to) = FluidHealthReadings.nightRange(date, zone)
  return FluidHealthReadings.reading(aggregate(FluidHealthMetric.Sleep, from, to), gapRule = null)
}
