package dev.antigravity.fluidengine.foundation

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/*
 * La regola di una ricorrenza (F8.8, `scelta-ricorrenza`), pura e senza Android: un dato che un'app salva, e che
 * l'engine sa espandere in date e riassumere in una frase ([FluidRecurrenceText]). Il pannello «Ripeti» vive in
 * `engine-ui` (`FluidRecurrenceSheet`) e restituisce una di queste.
 *
 * Il riferimento Apple e' `EKRecurrenceRule` di EventKit (frequenza, intervallo, giorni della settimana con il numero
 * della settimana, giorni del mese, mesi, posizioni, fine per data o per conteggio), che a sua volta e' la `RRULE` di
 * RFC 5545: [toRRule] e [parseRRule] parlano quella sintassi, cosi' una regola passa da e per un calendario vero.
 *
 * **L'espansione lavora su date locali** (`LocalDate`), mai su millisecondi sommati: «ogni giorno alle 9» resta alle 9
 * anche il giorno in cui si passa all'ora legale (quel giorno dura 23 ore, e sommare 24 ore porterebbe alle 10). L'ora,
 * se serve, si aggiunge solo alla fine ([FluidRecurrenceRule.occurrences] con un `LocalDateTime` e una zona).
 */

/** Ogni quanto si ripete una regola: `EKRecurrenceFrequency` (`FREQ` di RFC 5545). */
enum class FluidRecurrenceFrequency { Daily, Weekly, Monthly, Yearly }

/**
 * Un giorno della settimana di una regola, con il suo numero: `EKRecurrenceDayOfWeek` (`BYDAY` di RFC 5545).
 *
 * @property ordinal 0 = ogni [dayOfWeek] del periodo; da 1 a 5 = il primo… il quinto; da −1 a −5 = l'ultimo, il
 *   penultimo… Il numero vale solo nelle regole mensili e annuali, come in RFC 5545.
 */
data class FluidRecurrenceDay(val dayOfWeek: DayOfWeek, val ordinal: Int = 0) {
  init {
    require(ordinal in -5..5) { "il numero del giorno va da -5 a 5: era $ordinal" }
  }

  companion object {
    /** L'ultimo [day] del periodo («l'ultimo venerdì»). */
    fun last(day: DayOfWeek): FluidRecurrenceDay = FluidRecurrenceDay(day, -1)
  }
}

/** Quando finisce una regola: `EKRecurrenceEnd` (`UNTIL` e `COUNT` di RFC 5545). */
sealed class FluidRecurrenceEnd {
  /** Non finisce mai. */
  data object Never : FluidRecurrenceEnd()

  /** L'ultima ricorrenza possibile e' il giorno [date], compreso (`UNTIL`). */
  data class OnDate(val date: LocalDate) : FluidRecurrenceEnd()

  /** Dopo [count] ricorrenze, contando la prima (`COUNT`). */
  data class AfterCount(val count: Int) : FluidRecurrenceEnd() {
    init {
      require(count >= 1) { "una regola finisce dopo almeno una volta: era $count" }
    }
  }
}

/**
 * La regola di una ricorrenza, come `EKRecurrenceRule` e la `RRULE` di RFC 5545.
 *
 * I campi vuoti prendono il giorno dall'**inizio** passato a [occurrences]: «ogni settimana» iniziata di martedi' cade il
 * martedi', «ogni mese» iniziata il 31 cade il 31, «ogni anno» iniziata il 29 febbraio cade il 29 febbraio.
 *
 * **I casi limite seguono RFC 5545**, come Calendario di Apple:
 * - un mese senza il giorno chiesto si salta: «ogni mese il 31» va a gennaio, marzo, maggio…; per l'ultimo giorno di ogni
 *   mese si usa `daysOfMonth = listOf(-1)`;
 * - «ogni anno il 29 febbraio» cade solo negli anni bisestili;
 * - «il quinto lunedi'» salta i mesi che ne hanno quattro;
 * - l'inizio conta solo se rispetta la regola (RFC 5545 vuole un inizio allineato; EventKit conta sempre l'evento
 *   stesso: qui si chiede alla regola, e l'evento resta affare dell'app).
 *
 * @property interval ogni quanti periodi (`INTERVAL`): 2 con [FluidRecurrenceFrequency.Weekly] e' «ogni due settimane».
 * @property daysOfWeek i giorni della settimana (`BYDAY`); nelle regole settimanali senza giorni vale quello dell'inizio.
 * @property daysOfMonth i giorni del mese (`BYMONTHDAY`), da 1 a 31, o da −1 (l'ultimo) a −31.
 * @property monthsOfYear i mesi (`BYMONTH`): nelle regole annuali scelgono i mesi, nelle altre li limitano.
 * @property setPositions le posizioni fra i giorni scelti in un periodo (`BYSETPOS`): con i giorni da lunedi' a venerdi'
 *   e `listOf(-1)`, «l'ultimo giorno feriale del mese».
 * @property end quando finisce.
 * @property weekStart il primo giorno della settimana per contare le settimane di [interval] (`WKST`).
 */
data class FluidRecurrenceRule(
  val frequency: FluidRecurrenceFrequency,
  val interval: Int = 1,
  val daysOfWeek: List<FluidRecurrenceDay> = emptyList(),
  val daysOfMonth: List<Int> = emptyList(),
  val monthsOfYear: List<Month> = emptyList(),
  val setPositions: List<Int> = emptyList(),
  val end: FluidRecurrenceEnd = FluidRecurrenceEnd.Never,
  val weekStart: DayOfWeek = DayOfWeek.MONDAY,
) {
  init {
    require(interval >= 1) { "l'intervallo di una regola e' almeno 1: era $interval" }
    require(daysOfMonth.all { it in 1..31 || it in -31..-1 }) { "i giorni del mese vanno da 1 a 31 o da -31 a -1" }
    require(setPositions.all { it in 1..366 || it in -366..-1 }) { "le posizioni vanno da 1 a 366 o da -366 a -1" }
    require(
      frequency == FluidRecurrenceFrequency.Monthly || frequency == FluidRecurrenceFrequency.Yearly ||
        daysOfWeek.all { it.ordinal == 0 },
    ) { "il numero di un giorno della settimana vale solo nelle regole mensili e annuali" }
  }

  /**
   * Le ricorrenze dal giorno [start] in poi, in ordine: una sequenza pigra, infinita se la regola non finisce. Una regola
   * che non cade mai («ogni mese il 30» solo a febbraio) finisce da sola dopo 400 anni di periodi vuoti, un ciclo intero
   * del calendario gregoriano.
   */
  fun occurrences(start: LocalDate): Sequence<LocalDate> = sequence {
    var emitted = 0
    var period = 0L
    var lastHit = start
    val until = (end as? FluidRecurrenceEnd.OnDate)?.date
    val count = (end as? FluidRecurrenceEnd.AfterCount)?.count
    while (true) {
      val begin = periodBegin(start, period) ?: return@sequence
      if (until != null && begin > until) return@sequence
      for (date in candidates(begin, start)) {
        if (date < start) continue
        if (until != null && date > until) return@sequence
        yield(date)
        lastHit = date
        emitted++
        if (count != null && emitted >= count) return@sequence
      }
      if (ChronoUnit.YEARS.between(lastHit, begin) >= EmptyYearsLimit * interval) return@sequence
      period++
    }
  }

  /** Le prime [limit] ricorrenze dal giorno [from] in poi (di serie l'inizio), contate dall'inizio [start]. */
  fun occurrences(start: LocalDate, limit: Int, from: LocalDate = start): List<LocalDate> {
    require(limit >= 0) { "il limite e' almeno 0: era $limit" }
    return occurrences(start).dropWhile { it < from }.take(limit).toList()
  }

  /** Le ricorrenze che cadono fra [first] e [last], compresi. */
  fun occurrencesBetween(start: LocalDate, first: LocalDate, last: LocalDate): List<LocalDate> =
    occurrences(start).dropWhile { it < first }.takeWhile { it <= last }.toList()

  /**
   * Le ricorrenze con un'ora: la regola si espande in date ([occurrences]) e solo dopo ogni data prende l'ora di
   * [start] nella [zone]. Nell'ora che manca (l'ora legale che comincia) l'ora va avanti della lunghezza del salto;
   * nell'ora che si ripete vale la prima, come vuole RFC 5545 (§ 3.3.5) e come fa `ZonedDateTime.of`.
   */
  fun occurrences(start: LocalDateTime, zone: ZoneId): Sequence<ZonedDateTime> =
    occurrences(start.toLocalDate()).map { ZonedDateTime.of(it, start.toLocalTime(), zone) }

  /** La regola nella sintassi di RFC 5545, senza `RRULE:` davanti: `FREQ=WEEKLY;BYDAY=MO,TH;UNTIL=20261220`. */
  fun toRRule(): String = buildString {
    append("FREQ=").append(
      when (frequency) {
        FluidRecurrenceFrequency.Daily -> "DAILY"
        FluidRecurrenceFrequency.Weekly -> "WEEKLY"
        FluidRecurrenceFrequency.Monthly -> "MONTHLY"
        FluidRecurrenceFrequency.Yearly -> "YEARLY"
      },
    )
    if (interval != 1) append(";INTERVAL=").append(interval)
    if (daysOfWeek.isNotEmpty()) {
      append(";BYDAY=").append(daysOfWeek.joinToString(",") { (if (it.ordinal != 0) it.ordinal.toString() else "") + it.dayOfWeek.code() })
    }
    if (daysOfMonth.isNotEmpty()) append(";BYMONTHDAY=").append(daysOfMonth.joinToString(","))
    if (monthsOfYear.isNotEmpty()) append(";BYMONTH=").append(monthsOfYear.joinToString(",") { it.value.toString() })
    if (setPositions.isNotEmpty()) append(";BYSETPOS=").append(setPositions.joinToString(","))
    when (val e = end) {
      FluidRecurrenceEnd.Never -> Unit
      is FluidRecurrenceEnd.OnDate -> append(";UNTIL=").append(e.date.toString().replace("-", ""))
      is FluidRecurrenceEnd.AfterCount -> append(";COUNT=").append(e.count)
    }
    if (weekStart != DayOfWeek.MONDAY) append(";WKST=").append(weekStart.code())
  }

  /** Il primo giorno del periodo numero [index] (0 = quello dell'inizio), o nulla oltre l'anno 9999. */
  private fun periodBegin(start: LocalDate, index: Long): LocalDate? {
    val step = index * interval
    val begin = when (frequency) {
      FluidRecurrenceFrequency.Daily -> start.plusDays(step)
      FluidRecurrenceFrequency.Weekly -> start.with(TemporalAdjusters.previousOrSame(weekStart)).plusWeeks(step)
      FluidRecurrenceFrequency.Monthly -> YearMonth.from(start).plusMonths(step).atDay(1)
      FluidRecurrenceFrequency.Yearly -> start.withDayOfYear(1).plusYears(step)
    }
    return begin.takeIf { it.year <= 9999 }
  }

  /** I giorni della regola nel periodo che comincia a [begin], in ordine e senza doppioni, dopo le posizioni. */
  private fun candidates(begin: LocalDate, start: LocalDate): List<LocalDate> {
    val days: List<LocalDate> = when (frequency) {
      FluidRecurrenceFrequency.Daily -> listOf(begin).filter { limitByMonth(it) && limitByMonthDay(it) && limitByWeekday(it) }
      FluidRecurrenceFrequency.Weekly -> {
        val wanted = if (daysOfWeek.isEmpty()) setOf(start.dayOfWeek) else daysOfWeek.map { it.dayOfWeek }.toSet()
        (0L until 7L).map(begin::plusDays).filter { it.dayOfWeek in wanted && limitByMonth(it) }
      }
      FluidRecurrenceFrequency.Monthly -> {
        val month = YearMonth.from(begin)
        if (monthsOfYear.isNotEmpty() && month.month !in monthsOfYear) emptyList() else inMonth(month, start, null)
      }
      FluidRecurrenceFrequency.Yearly -> inYear(begin.year, start)
    }
    val sorted = days.distinct().sorted()
    if (setPositions.isEmpty()) return sorted
    return setPositions.mapNotNull { p ->
      val i = if (p > 0) p - 1 else sorted.size + p
      sorted.getOrNull(i)
    }.distinct().sorted()
  }

  /**
   * I giorni della regola nel mese [month]. Con giorni del mese e giorni della settimana insieme, i secondi **limitano**
   * i primi (RFC 5545 § 3.3.10, nota 1: «Limit if BYMONTHDAY is present»; l'esempio della RFC e' «ogni venerdi' 13»), e
   * il numero di un giorno della settimana conta anche qui (R2): `BYMONTHDAY=15;BYDAY=-1FR` vuole il 15 che e' anche
   * l'ultimo venerdi' del periodo. Il periodo del numero e' il mese, o l'anno ([yearScope]) in una regola annuale
   * senza mesi.
   */
  private fun inMonth(month: YearMonth, start: LocalDate, yearScope: Int?): List<LocalDate> {
    val byMonthDay = daysOfMonth.mapNotNull { monthDay(month, it) }
    return when {
      byMonthDay.isNotEmpty() || daysOfMonth.isNotEmpty() ->
        if (daysOfWeek.isEmpty()) byMonthDay else byMonthDay.filter { d -> daysOfWeek.any { it.matches(d, yearScope) } }
      daysOfWeek.isNotEmpty() -> daysOfWeek.flatMap { weekdaysIn(month.atDay(1), month.atEndOfMonth(), it) }
      else -> listOfNotNull(monthDay(month, start.dayOfMonth))
    }
  }

  /** Vero se [date] e' questo giorno della settimana, col suo numero nel mese di [date] (o nell'anno [yearScope]). */
  private fun FluidRecurrenceDay.matches(date: LocalDate, yearScope: Int?): Boolean {
    if (date.dayOfWeek != dayOfWeek) return false
    if (ordinal == 0) return true
    val (first, last) = if (yearScope != null) LocalDate.of(yearScope, 1, 1) to LocalDate.of(yearScope, 12, 31)
    else date.withDayOfMonth(1) to date.withDayOfMonth(date.lengthOfMonth())
    return date in weekdaysIn(first, last, this)
  }

  private fun inYear(year: Int, start: LocalDate): List<LocalDate> {
    if (monthsOfYear.isNotEmpty()) {
      return monthsOfYear.flatMap { m ->
        val month = YearMonth.of(year, m)
        if (daysOfMonth.isEmpty() && daysOfWeek.isEmpty()) listOfNotNull(monthDay(month, start.dayOfMonth)) else inMonth(month, start, null)
      }
    }
    return when {
      daysOfMonth.isNotEmpty() -> (1..12).flatMap { inMonth(YearMonth.of(year, it), start, yearScope = year) }
      daysOfWeek.isNotEmpty() -> daysOfWeek.flatMap { weekdaysIn(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), it) }
      // Il giorno dell'inizio, se quell'anno c'e' (il 29 febbraio solo negli anni bisestili).
      else -> listOfNotNull(monthDay(YearMonth.of(year, start.month), start.dayOfMonth))
    }
  }

  private fun limitByMonth(date: LocalDate): Boolean = monthsOfYear.isEmpty() || date.month in monthsOfYear

  private fun limitByMonthDay(date: LocalDate): Boolean =
    daysOfMonth.isEmpty() || daysOfMonth.any { monthDay(YearMonth.from(date), it) == date }

  private fun limitByWeekday(date: LocalDate): Boolean = daysOfWeek.isEmpty() || daysOfWeek.any { it.dayOfWeek == date.dayOfWeek }

  companion object {
    /** Dopo quanti anni di periodi vuoti una regola si considera senza ricorrenze: un ciclo gregoriano. */
    private const val EmptyYearsLimit = 400L

    /**
     * Legge una regola di RFC 5545 (`FREQ=MONTHLY;BYDAY=-1FR`, anche con `RRULE:` davanti). Le parti che una regola di
     * calendario non usa (`BYHOUR`, `BYWEEKNO`, `BYYEARDAY`…) fanno fallire la lettura invece di essere ignorate: una
     * regola letta a meta' darebbe date sbagliate senza dirlo. `UNTIL` con un'ora vale per la sua data.
     */
    fun parseRRule(text: String): FluidRecurrenceRule {
      val parts = text.trim().removePrefix("RRULE:").split(';').filter { it.isNotBlank() }.associate {
        val (k, v) = it.split('=', limit = 2).let { p -> require(p.size == 2) { "parte senza valore: $it" }; p[0] to p[1] }
        k.trim().uppercase() to v.trim()
      }
      val known = setOf("FREQ", "INTERVAL", "BYDAY", "BYMONTHDAY", "BYMONTH", "BYSETPOS", "UNTIL", "COUNT", "WKST")
      val unknown = parts.keys - known
      require(unknown.isEmpty()) { "parti non sostenute: ${unknown.joinToString()}" }
      require(!(parts.containsKey("UNTIL") && parts.containsKey("COUNT"))) { "UNTIL e COUNT insieme" }
      val frequency = when (parts["FREQ"]?.uppercase()) {
        "DAILY" -> FluidRecurrenceFrequency.Daily
        "WEEKLY" -> FluidRecurrenceFrequency.Weekly
        "MONTHLY" -> FluidRecurrenceFrequency.Monthly
        "YEARLY" -> FluidRecurrenceFrequency.Yearly
        else -> throw IllegalArgumentException("FREQ mancante o non sostenuta: ${parts["FREQ"]}")
      }
      fun ints(key: String) = parts[key]?.split(',')?.map { it.trim().toInt() }.orEmpty()
      val days = parts["BYDAY"]?.split(',')?.map { token ->
        val t = token.trim().uppercase()
        val code = t.takeLast(2)
        val n = t.dropLast(2).let { if (it.isEmpty() || it == "+") 0 else it.removePrefix("+").toInt() }
        FluidRecurrenceDay(dayFromCode(code), n)
      }.orEmpty()
      val end = when {
        parts["UNTIL"] != null -> {
          val raw = parts.getValue("UNTIL").substringBefore('T')
          FluidRecurrenceEnd.OnDate(LocalDate.of(raw.substring(0, 4).toInt(), raw.substring(4, 6).toInt(), raw.substring(6, 8).toInt()))
        }
        parts["COUNT"] != null -> FluidRecurrenceEnd.AfterCount(parts.getValue("COUNT").toInt())
        else -> FluidRecurrenceEnd.Never
      }
      return FluidRecurrenceRule(
        frequency = frequency,
        interval = parts["INTERVAL"]?.toInt() ?: 1,
        daysOfWeek = days,
        daysOfMonth = ints("BYMONTHDAY"),
        monthsOfYear = ints("BYMONTH").map { Month.of(it) },
        setPositions = ints("BYSETPOS"),
        end = end,
        weekStart = parts["WKST"]?.let { dayFromCode(it.uppercase()) } ?: DayOfWeek.MONDAY,
      )
    }

    /** Il giorno [day] del mese [month] (negativo: dalla fine), o nulla se quel mese non lo ha. */
    internal fun monthDay(month: YearMonth, day: Int): LocalDate? {
      val length = month.lengthOfMonth()
      val d = if (day > 0) day else length + 1 + day
      return if (d in 1..length) month.atDay(d) else null
    }

    /** I giorni [day] fra [first] e [last]: tutti, o il numero `ordinal` (dalla fine se negativo). */
    private fun weekdaysIn(first: LocalDate, last: LocalDate, day: FluidRecurrenceDay): List<LocalDate> {
      val all = generateSequence(first.with(TemporalAdjusters.nextOrSame(day.dayOfWeek))) { it.plusWeeks(1) }
        .takeWhile { it <= last }.toList()
      return when {
        day.ordinal == 0 -> all
        day.ordinal > 0 -> listOfNotNull(all.getOrNull(day.ordinal - 1))
        else -> listOfNotNull(all.getOrNull(all.size + day.ordinal))
      }
    }

    private fun dayFromCode(code: String): DayOfWeek = when (code) {
      "MO" -> DayOfWeek.MONDAY
      "TU" -> DayOfWeek.TUESDAY
      "WE" -> DayOfWeek.WEDNESDAY
      "TH" -> DayOfWeek.THURSDAY
      "FR" -> DayOfWeek.FRIDAY
      "SA" -> DayOfWeek.SATURDAY
      "SU" -> DayOfWeek.SUNDAY
      else -> throw IllegalArgumentException("giorno sconosciuto: $code")
    }
  }
}

private fun DayOfWeek.code(): String = when (this) {
  DayOfWeek.MONDAY -> "MO"
  DayOfWeek.TUESDAY -> "TU"
  DayOfWeek.WEDNESDAY -> "WE"
  DayOfWeek.THURSDAY -> "TH"
  DayOfWeek.FRIDAY -> "FR"
  DayOfWeek.SATURDAY -> "SA"
  DayOfWeek.SUNDAY -> "SU"
}
