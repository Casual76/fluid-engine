package dev.antigravity.fluidengine.foundation

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/*
 * I conti del calendario che non dipendono da Android (F8.8): il primo giorno della settimana secondo la lingua e
 * l'intervallo di giorni «dal… al…» su date locali. Il calendario (`FluidCalendar`) e il foglio della data
 * (`FluidDateTimeSheet`) di `engine-ui` li usano tutti e due.
 */

/** I giorni della settimana come li conta una lingua. */
object FluidDates {
  /**
   * Il primo giorno della settimana per [locale], dai dati CLDR della piattaforma (`WeekFields.of`): lunedi' in
   * Italia e nel Regno Unito, domenica negli Stati Uniti e in Israele, sabato in Egitto. Su Android segue anche la scelta
   * dell'utente nelle impostazioni («Primo giorno della settimana», Android 14), che arriva come estensione `fw` della
   * lingua (`it-IT-u-fw-sun`).
   */
  fun firstDayOfWeek(locale: Locale): DayOfWeek = WeekFields.of(locale).firstDayOfWeek

  /** I sette giorni nell'ordine di [locale], dal primo. */
  fun weekdays(locale: Locale): List<DayOfWeek> {
    val first = firstDayOfWeek(locale)
    return List(7) { first.plus(it.toLong()) }
  }

  /** La settimana di [date] nell'ordine di [locale], come intervallo di sette giorni. */
  fun weekOf(date: LocalDate, locale: Locale): FluidDateRange {
    val start = date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek(locale)))
    return FluidDateRange(start, start.plusDays(6))
  }
}

/**
 * Un intervallo di giorni, dal [start] al [endInclusive] compresi (F8.8, `intervalli-e-giorni-sparsi`): la scelta «dal…
 * al…» del calendario. Vive su **date locali**: tre giorni sono tre giorni anche se in mezzo c'e' il cambio dell'ora, e
 * un giorno solo e' un intervallo con lo stesso inizio e la stessa fine.
 *
 * Gli istanti servono solo a chi deve interrogare un server o un archivio: [startInstant] e [endInstantExclusive]
 * danno l'intervallo semiaperto in una zona, dalla mezzanotte del primo giorno a quella del giorno dopo l'ultimo. Lì il
 * giorno del passaggio all'ora legale dura 23 ore e quello del ritorno 25 ([duration]).
 */
data class FluidDateRange(
  override val start: LocalDate,
  override val endInclusive: LocalDate,
) : ClosedRange<LocalDate>, Iterable<LocalDate> {
  init {
    require(!endInclusive.isBefore(start)) { "un intervallo finisce dopo l'inizio: $start > $endInclusive" }
  }

  /** Quanti giorni, contando il primo e l'ultimo. */
  val dayCount: Long get() = ChronoUnit.DAYS.between(start, endInclusive) + 1

  /** Vero se c'e' un giorno solo. */
  val isSingleDay: Boolean get() = start == endInclusive

  override fun iterator(): Iterator<LocalDate> =
    generateSequence(start) { d -> d.plusDays(1).takeIf { it <= endInclusive } }.iterator()

  /** La mezzanotte del primo giorno in [zone] (la prima ora valida, se quella notte manca la mezzanotte). */
  fun startInstant(zone: ZoneId): Instant = start.atStartOfDay(zone).toInstant()

  /** La mezzanotte del giorno dopo l'ultimo in [zone]: la fine esclusa. */
  fun endInstantExclusive(zone: ZoneId): Instant = endInclusive.plusDays(1).atStartOfDay(zone).toInstant()

  /** Quanto dura l'intervallo in [zone]: 24 ore per giorno, meno o piu' un'ora nei giorni del cambio. */
  fun duration(zone: ZoneId): Duration = Duration.between(startInstant(zone), endInstantExclusive(zone))

  companion object {
    /** L'intervallo fra [a] e [b] in qualunque ordine: il tocco «al» prima del «dal» non e' un errore. */
    fun between(a: LocalDate, b: LocalDate): FluidDateRange = if (b < a) FluidDateRange(b, a) else FluidDateRange(a, b)
  }
}
