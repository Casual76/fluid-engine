package dev.antigravity.fluidengine.foundation

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.util.Locale

/**
 * La frase che riassume una [FluidRecurrenceRule] (F8.8): «Ogni lun e gio fino al 20 dicembre», «Every month on the last
 * Friday, 10 times». La scrive il pannello «Ripeti» sopra le prossime date, come la riga di Calendario di Apple sotto la
 * ripetizione personalizzata («L'evento si ripeterà ogni settimana il lunedì e il giovedì»), ma piu' corta: sta in una
 * riga.
 *
 * Due lingue: l'italiano per `it`, l'inglese per tutte le altre. Le parole sono scritte qui e non chieste alla
 * piattaforma, perche' la frase deve essere la stessa sul telefono e nelle prove (i nomi brevi dei giorni di Android e
 * della JVM non coincidono in tutte le lingue).
 *
 * I casi che la frase conosce per nome: i giorni feriali («Ogni giorno feriale»), il fine settimana, l'ultimo giorno del
 * mese, «il secondo lunedì», «l'ultima domenica» (con l'accordo), l'ultimo giorno feriale. Il resto si dice per parti.
 */
object FluidRecurrenceText {

  /**
   * La frase di [rule] cominciata il giorno [start] (serve ai campi vuoti, che prendono il giorno dall'inizio, e all'anno
   * della fine, scritto solo se e' un altro). [locale] sceglie la lingua.
   */
  fun describe(rule: FluidRecurrenceRule, start: LocalDate, locale: Locale): String {
    val words = if (locale.language == "it") Italian else English
    return words.rule(rule, start) + words.end(rule.end, start)
  }

  private val Weekdays = listOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
  )
  private val Weekend = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

  /** I giorni nell'ordine della settimana della regola (dal suo `weekStart`). */
  private fun ordered(days: Collection<DayOfWeek>, weekStart: DayOfWeek): List<DayOfWeek> =
    days.distinct().sortedBy { Math.floorMod(it.value - weekStart.value, 7) }

  private abstract class Words {
    abstract fun rule(rule: FluidRecurrenceRule, start: LocalDate): String
    abstract fun end(end: FluidRecurrenceEnd, start: LocalDate): String
  }

  private object Italian : Words() {
    private val short = mapOf(
      DayOfWeek.MONDAY to "lun", DayOfWeek.TUESDAY to "mar", DayOfWeek.WEDNESDAY to "mer", DayOfWeek.THURSDAY to "gio",
      DayOfWeek.FRIDAY to "ven", DayOfWeek.SATURDAY to "sab", DayOfWeek.SUNDAY to "dom",
    )
    private val full = mapOf(
      DayOfWeek.MONDAY to "lunedì", DayOfWeek.TUESDAY to "martedì", DayOfWeek.WEDNESDAY to "mercoledì",
      DayOfWeek.THURSDAY to "giovedì", DayOfWeek.FRIDAY to "venerdì", DayOfWeek.SATURDAY to "sabato",
      DayOfWeek.SUNDAY to "domenica",
    )
    private val months = listOf(
      "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto", "settembre", "ottobre",
      "novembre", "dicembre",
    )

    private fun month(m: Month) = months[m.value - 1]

    /** «lun e gio», «lun, mer e ven». */
    private fun join(items: List<String>): String = when (items.size) {
      0 -> ""
      1 -> items[0]
      else -> items.dropLast(1).joinToString(", ") + " e " + items.last()
    }

    /** «il 1°», «l'8», «l'11», «il 15»; dalla fine «l'ultimo giorno», «il penultimo giorno». */
    private fun monthDay(day: Int): String = when {
      day == 1 -> "il 1°"
      day == 8 || day == 11 -> "l'$day"
      day > 0 -> "il $day"
      day == -1 -> "l'ultimo giorno"
      day == -2 -> "il penultimo giorno"
      else -> "il ${-day}° giorno dalla fine"
    }

    /** «il secondo lunedì», «l'ultimo venerdì», «la prima domenica», «l'ultima domenica». */
    private fun ordinalDay(ordinal: Int, noun: String, feminine: Boolean): String {
      val word = when (ordinal) {
        1 -> "primo"; 2 -> "secondo"; 3 -> "terzo"; 4 -> "quarto"; 5 -> "quinto"
        -1 -> "ultimo"; -2 -> "penultimo"; -3 -> "terzultimo"; -4 -> "quartultimo"
        else -> "quintultimo"
      }.let { if (feminine) it.dropLast(1) + "a" else it }
      val article = when {
        word.first() in "aeiouAEIOU" -> "l'"
        feminine -> "la "
        else -> "il "
      }
      return "$article$word $noun"
    }

    private fun weekdayWithOrdinal(day: FluidRecurrenceDay): String =
      if (day.ordinal == 0) "ogni ${full.getValue(day.dayOfWeek)}"
      else ordinalDay(day.ordinal, full.getValue(day.dayOfWeek), feminine = day.dayOfWeek == DayOfWeek.SUNDAY)

    /** «lun e gio», o il nome intero se il giorno e' uno solo. */
    private fun weekdays(days: List<DayOfWeek>): String =
      if (days.size == 1) full.getValue(days[0]) else join(days.map { short.getValue(it) })

    private fun every(n: Int, one: String, many: String): String = if (n == 1) "Ogni $one" else "Ogni $n $many"

    /** Quello che si sceglie dentro un mese: «il 15», «l'ultimo venerdì», «l'ultimo giorno feriale». */
    private fun inMonth(rule: FluidRecurrenceRule, start: LocalDate): String {
      val days = rule.daysOfWeek
      if (rule.setPositions.size == 1 && days.all { it.ordinal == 0 }) {
        val set = days.map { it.dayOfWeek }.toSet()
        val noun = when (set) {
          Weekdays.toSet() -> "giorno feriale"
          Weekend.toSet() -> "giorno del fine settimana"
          DayOfWeek.entries.toSet() -> "giorno"
          else -> null
        }
        if (noun != null) return ordinalDay(rule.setPositions[0], noun, feminine = false)
      }
      return when {
        rule.daysOfMonth.isNotEmpty() -> join(rule.daysOfMonth.sortedWith(monthDayOrder).map(::monthDay))
        days.isNotEmpty() -> join(days.map(::weekdayWithOrdinal))
        else -> monthDay(start.dayOfMonth)
      }
    }

    override fun rule(rule: FluidRecurrenceRule, start: LocalDate): String {
      val n = rule.interval
      return when (rule.frequency) {
        FluidRecurrenceFrequency.Daily -> every(n, "giorno", "giorni")
        FluidRecurrenceFrequency.Weekly -> {
          val days = ordered(rule.daysOfWeek.map { it.dayOfWeek }.ifEmpty { listOf(start.dayOfWeek) }, rule.weekStart)
          when {
            days.size == 7 -> if (n == 1) "Ogni giorno" else "Ogni $n settimane, tutti i giorni"
            days == ordered(Weekdays, rule.weekStart) -> if (n == 1) "Ogni giorno feriale" else "Ogni $n settimane, nei giorni feriali"
            days == ordered(Weekend, rule.weekStart) -> if (n == 1) "Ogni fine settimana" else "Ogni $n settimane, nel fine settimana"
            n == 1 -> "Ogni ${weekdays(days)}"
            days.size == 1 -> "Ogni $n settimane, ${if (days[0] == DayOfWeek.SUNDAY) "la" else "il"} ${weekdays(days)}"
            else -> "Ogni $n settimane, ${weekdays(days)}"
          }
        }
        FluidRecurrenceFrequency.Monthly -> {
          val head = every(n, "mese", "mesi")
          val limit = if (rule.monthsOfYear.isNotEmpty()) " (${join(rule.monthsOfYear.sorted().map(::month))})" else ""
          "$head ${inMonth(rule, start)}$limit"
        }
        FluidRecurrenceFrequency.Yearly -> {
          val head = every(n, "anno", "anni")
          val months = rule.monthsOfYear.sorted()
          when {
            rule.daysOfMonth.isEmpty() && rule.daysOfWeek.isEmpty() -> {
              val ms = months.ifEmpty { listOf(start.month) }
              "$head ${join(ms.map { "${monthDay(start.dayOfMonth)} ${month(it)}" })}"
            }
            months.isNotEmpty() -> "$head ${inMonth(rule, start)} di ${join(months.map(::month))}"
            rule.daysOfMonth.isNotEmpty() -> "$head ${inMonth(rule, start)} di ogni mese"
            else -> "$head ${inMonth(rule, start)} dell'anno"
          }
        }
      }
    }

    override fun end(end: FluidRecurrenceEnd, start: LocalDate): String = when (end) {
      FluidRecurrenceEnd.Never -> ""
      is FluidRecurrenceEnd.AfterCount -> if (end.count == 1) ", 1 volta" else ", ${end.count} volte"
      is FluidRecurrenceEnd.OnDate -> {
        val d = end.date
        // «fino al 20», «fino all'8» e «all'11», «fino al 1°».
        val prep = when (d.dayOfMonth) {
          1 -> "al 1°"
          8, 11 -> "all'${d.dayOfMonth}"
          else -> "al ${d.dayOfMonth}"
        }
        val year = if (d.year != start.year) " ${d.year}" else ""
        " fino $prep ${month(d.month)}$year"
      }
    }
  }

  private object English : Words() {
    private val short = mapOf(
      DayOfWeek.MONDAY to "Mon", DayOfWeek.TUESDAY to "Tue", DayOfWeek.WEDNESDAY to "Wed", DayOfWeek.THURSDAY to "Thu",
      DayOfWeek.FRIDAY to "Fri", DayOfWeek.SATURDAY to "Sat", DayOfWeek.SUNDAY to "Sun",
    )
    private val full = mapOf(
      DayOfWeek.MONDAY to "Monday", DayOfWeek.TUESDAY to "Tuesday", DayOfWeek.WEDNESDAY to "Wednesday",
      DayOfWeek.THURSDAY to "Thursday", DayOfWeek.FRIDAY to "Friday", DayOfWeek.SATURDAY to "Saturday",
      DayOfWeek.SUNDAY to "Sunday",
    )
    private val months = listOf(
      "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November",
      "December",
    )

    private fun month(m: Month) = months[m.value - 1]

    /** «Mon and Thu», «Mon, Wed, and Fri»: la virgola di serie, come la guida di stile di Apple. */
    private fun join(items: List<String>): String = when (items.size) {
      0 -> ""
      1 -> items[0]
      2 -> "${items[0]} and ${items[1]}"
      else -> items.dropLast(1).joinToString(", ") + ", and " + items.last()
    }

    private fun suffix(n: Int): String = when {
      n % 100 in 11..13 -> "th"
      n % 10 == 1 -> "st"
      n % 10 == 2 -> "nd"
      n % 10 == 3 -> "rd"
      else -> "th"
    }

    private fun monthDay(day: Int): String = when {
      day > 0 -> "the $day${suffix(day)}"
      day == -1 -> "the last day"
      day == -2 -> "the second-to-last day"
      else -> "the ${-day}${suffix(-day)}-to-last day"
    }

    private fun ordinalDay(ordinal: Int, noun: String): String {
      val word = when (ordinal) {
        1 -> "first"; 2 -> "second"; 3 -> "third"; 4 -> "fourth"; 5 -> "fifth"
        -1 -> "last"; -2 -> "second-to-last"; -3 -> "third-to-last"
        else -> "${-ordinal}${suffix(-ordinal)}-to-last"
      }
      return "the $word $noun"
    }

    private fun weekdayWithOrdinal(day: FluidRecurrenceDay): String =
      if (day.ordinal == 0) "every ${full.getValue(day.dayOfWeek)}" else ordinalDay(day.ordinal, full.getValue(day.dayOfWeek))

    private fun weekdays(days: List<DayOfWeek>): String =
      if (days.size == 1) full.getValue(days[0]) else join(days.map { short.getValue(it) })

    private fun every(n: Int, one: String, many: String): String = if (n == 1) "Every $one" else "Every $n $many"

    private fun inMonth(rule: FluidRecurrenceRule, start: LocalDate): String {
      val days = rule.daysOfWeek
      if (rule.setPositions.size == 1 && days.all { it.ordinal == 0 }) {
        val set = days.map { it.dayOfWeek }.toSet()
        val noun = when (set) {
          Weekdays.toSet() -> "weekday"
          Weekend.toSet() -> "weekend day"
          DayOfWeek.entries.toSet() -> "day"
          else -> null
        }
        if (noun != null) return ordinalDay(rule.setPositions[0], noun)
      }
      return when {
        rule.daysOfMonth.isNotEmpty() -> join(rule.daysOfMonth.sortedWith(monthDayOrder).map(::monthDay))
        days.isNotEmpty() -> join(days.map(::weekdayWithOrdinal))
        else -> monthDay(start.dayOfMonth)
      }
    }

    override fun rule(rule: FluidRecurrenceRule, start: LocalDate): String {
      val n = rule.interval
      return when (rule.frequency) {
        FluidRecurrenceFrequency.Daily -> every(n, "day", "days")
        FluidRecurrenceFrequency.Weekly -> {
          val days = ordered(rule.daysOfWeek.map { it.dayOfWeek }.ifEmpty { listOf(start.dayOfWeek) }, rule.weekStart)
          when {
            days.size == 7 -> if (n == 1) "Every day" else "Every $n weeks, every day"
            days == ordered(Weekdays, rule.weekStart) -> if (n == 1) "Every weekday" else "Every $n weeks on weekdays"
            days == ordered(Weekend, rule.weekStart) -> if (n == 1) "Every weekend" else "Every $n weeks on weekends"
            n == 1 -> "Every ${weekdays(days)}"
            else -> "Every $n weeks on ${weekdays(days)}"
          }
        }
        FluidRecurrenceFrequency.Monthly -> {
          val limit = if (rule.monthsOfYear.isNotEmpty()) " (${join(rule.monthsOfYear.sorted().map(::month))})" else ""
          "${every(n, "month", "months")} on ${inMonth(rule, start)}$limit"
        }
        FluidRecurrenceFrequency.Yearly -> {
          val head = every(n, "year", "years")
          val months = rule.monthsOfYear.sorted()
          when {
            rule.daysOfMonth.isEmpty() && rule.daysOfWeek.isEmpty() -> {
              val ms = months.ifEmpty { listOf(start.month) }
              "$head on ${join(ms.map { "${month(it)} ${start.dayOfMonth}" })}"
            }
            months.isNotEmpty() -> "$head on ${inMonth(rule, start)} of ${join(months.map(::month))}"
            rule.daysOfMonth.isNotEmpty() -> "$head on ${inMonth(rule, start)} of every month"
            else -> "$head on ${inMonth(rule, start)} of the year"
          }
        }
      }
    }

    override fun end(end: FluidRecurrenceEnd, start: LocalDate): String = when (end) {
      FluidRecurrenceEnd.Never -> ""
      is FluidRecurrenceEnd.AfterCount -> if (end.count == 1) ", once" else ", ${end.count} times"
      is FluidRecurrenceEnd.OnDate -> {
        val d = end.date
        val year = if (d.year != start.year) ", ${d.year}" else ""
        " until ${month(d.month)} ${d.dayOfMonth}$year"
      }
    }
  }

  /** I giorni del mese in ordine di calendario: prima quelli dall'inizio, poi quelli dalla fine (l'ultimo per ultimo). */
  private val monthDayOrder = Comparator<Int> { a, b ->
    when {
      a > 0 && b > 0 -> a.compareTo(b)
      a < 0 && b < 0 -> a.compareTo(b)
      a > 0 -> -1
      else -> 1
    }
  }
}
