package dev.antigravity.fluidengine.foundation

import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F8.8 · la regola della ricorrenza, i suoi casi limite provati una volta sola, qui: il 31 del mese, il 29 febbraio,
 * «l'ultimo venerdì», il quinto lunedì, l'ultimo giorno feriale, le settimane alterne, l'ora legale, la fine per data e
 * per conteggio, la `RRULE` di RFC 5545 avanti e indietro, e la frase in italiano e in inglese.
 */
class FluidRecurrenceTest {
  private fun d(text: String): LocalDate = LocalDate.parse(text)
  private fun dates(vararg text: String): List<LocalDate> = text.map(::d)
  private val italian = Locale.ITALY
  private val english = Locale.US

  private fun weekly(vararg days: DayOfWeek, interval: Int = 1, end: FluidRecurrenceEnd = FluidRecurrenceEnd.Never) =
    FluidRecurrenceRule(FluidRecurrenceFrequency.Weekly, interval, days.map { FluidRecurrenceDay(it) }, end = end)

  // --- L'espansione -------------------------------------------------------------------------------------------------

  @Test fun theThirtyFirstSkipsShortMonthsAndTheLastDayDoesNot() {
    val monthly = FluidRecurrenceRule(FluidRecurrenceFrequency.Monthly)
    assertEquals(
      dates("2026-01-31", "2026-03-31", "2026-05-31", "2026-07-31", "2026-08-31", "2026-10-31", "2026-12-31"),
      monthly.occurrences(d("2026-01-31"), limit = 7),
    )
    val last = FluidRecurrenceRule(FluidRecurrenceFrequency.Monthly, daysOfMonth = listOf(-1))
    assertEquals(dates("2024-01-31", "2024-02-29", "2024-03-31", "2024-04-30"), last.occurrences(d("2024-01-31"), limit = 4))
    assertTrue(d("2025-02-28") in last.occurrences(d("2024-01-31"), limit = 14))
    // Il 30 di ogni febbraio non esiste: la regola finisce da sola, senza girare per sempre.
    val never = FluidRecurrenceRule(FluidRecurrenceFrequency.Monthly, daysOfMonth = listOf(30), monthsOfYear = listOf(Month.FEBRUARY))
    assertTrue(never.occurrences(d("2026-01-01"), limit = 1).isEmpty())
  }

  @Test fun theTwentyNinthOfFebruaryOnlyInLeapYears() {
    val yearly = FluidRecurrenceRule(FluidRecurrenceFrequency.Yearly)
    assertEquals(dates("2024-02-29", "2028-02-29", "2032-02-29"), yearly.occurrences(d("2024-02-29"), limit = 3))
    // Il 2100 non e' bisestile (divisibile per 100 e non per 400): dal 2096 si salta al 2104.
    assertEquals(dates("2096-02-29", "2104-02-29"), yearly.occurrences(d("2096-02-29"), limit = 2))
  }

  @Test fun theLastFridayTheFifthMondayAndTheLastWeekday() {
    val lastFriday = FluidRecurrenceRule(FluidRecurrenceFrequency.Monthly, daysOfWeek = listOf(FluidRecurrenceDay.last(FRIDAY)))
    assertEquals(
      dates("2026-11-27", "2026-12-25", "2027-01-29", "2027-02-26"),
      lastFriday.occurrences(d("2026-11-24"), limit = 4),
    )
    val fifthMonday = FluidRecurrenceRule(FluidRecurrenceFrequency.Monthly, daysOfWeek = listOf(FluidRecurrenceDay(MONDAY, 5)))
    // Novembre 2026 ha cinque lunedi' (il 30), dicembre e gennaio quattro, marzo 2027 cinque (il 29).
    assertEquals(dates("2026-11-30", "2027-03-29"), fifthMonday.occurrences(d("2026-11-01"), limit = 2))
    val lastWeekday = FluidRecurrenceRule(
      FluidRecurrenceFrequency.Monthly,
      daysOfWeek = listOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY).map { FluidRecurrenceDay(it) },
      setPositions = listOf(-1),
    )
    assertEquals(dates("2026-11-30", "2026-12-31", "2027-01-29", "2027-02-26"), lastWeekday.occurrences(d("2026-11-01"), limit = 4))
    // Il quarto giovedi' di novembre, ogni anno.
    val thanksgiving = FluidRecurrenceRule(
      FluidRecurrenceFrequency.Yearly, daysOfWeek = listOf(FluidRecurrenceDay(THURSDAY, 4)), monthsOfYear = listOf(Month.NOVEMBER),
    )
    assertEquals(dates("2026-11-26", "2027-11-25", "2028-11-23"), thanksgiving.occurrences(d("2026-01-01"), limit = 3))
  }

  @Test fun weeksCountFromTheWeekStartAndTheStartCountsOnlyIfItMatches() {
    // Martedi' 24 novembre: il primo lunedi' e' quello dopo, e il martedi' non c'e'.
    assertEquals(
      dates("2026-11-26", "2026-11-30", "2026-12-03", "2026-12-07", "2026-12-10", "2026-12-14", "2026-12-17"),
      weekly(MONDAY, THURSDAY, end = FluidRecurrenceEnd.OnDate(d("2026-12-20"))).occurrences(d("2026-11-24")).toList(),
    )
    // Ogni due settimane: la settimana del 23 novembre, poi quella del 7 dicembre.
    assertEquals(
      dates("2026-11-26", "2026-12-07", "2026-12-10", "2026-12-21"),
      weekly(MONDAY, THURSDAY, interval = 2).occurrences(d("2026-11-24"), limit = 4),
    )
    // Senza giorni vale quello dell'inizio.
    assertEquals(dates("2026-11-24", "2026-12-01"), FluidRecurrenceRule(FluidRecurrenceFrequency.Weekly).occurrences(d("2026-11-24"), limit = 2))
    // Con la settimana che comincia di domenica, la domenica 29 apre una settimana nuova.
    val sundayWeeks = weekly(SATURDAY, SUNDAY, interval = 2).copy(weekStart = SUNDAY)
    assertEquals(dates("2026-11-28", "2026-12-06", "2026-12-12"), sundayWeeks.occurrences(d("2026-11-24"), limit = 3))
  }

  @Test fun theEndByCountAndByDateAndFrom() {
    val threeTimes = FluidRecurrenceRule(FluidRecurrenceFrequency.Daily, interval = 2, end = FluidRecurrenceEnd.AfterCount(3))
    assertEquals(dates("2026-12-30", "2027-01-01", "2027-01-03"), threeTimes.occurrences(d("2026-12-30")).toList())
    // Il conteggio parte dall'inizio, anche se si chiede da piu' avanti.
    assertEquals(dates("2027-01-03"), threeTimes.occurrences(d("2026-12-30"), limit = 5, from = d("2027-01-02")))
    val untilLastDay = FluidRecurrenceRule(FluidRecurrenceFrequency.Daily, end = FluidRecurrenceEnd.OnDate(d("2026-11-26")))
    assertEquals(dates("2026-11-24", "2026-11-25", "2026-11-26"), untilLastDay.occurrences(d("2026-11-24")).toList())
    assertEquals(
      dates("2026-12-03", "2026-12-07"),
      weekly(MONDAY, THURSDAY).occurrencesBetween(d("2026-11-24"), d("2026-12-01"), d("2026-12-07")),
    )
  }

  @Test fun daylightSavingKeepsTheLocalTime() {
    val rome = ZoneId.of("Europe/Rome")
    val daily = FluidRecurrenceRule(FluidRecurrenceFrequency.Daily)
    val nine = daily.occurrences(LocalDateTime.parse("2026-03-28T09:00"), rome).take(3).toList()
    nine.forEach { assertEquals(LocalTime.of(9, 0), it.toLocalTime()) }
    // Il 29 marzo 2026 dura 23 ore: fra le 9 del 28 e le 9 del 29 ne passano 23, non 24.
    assertEquals(Duration.ofHours(23), Duration.between(nine[0].toInstant(), nine[1].toInstant()))
    assertEquals(Duration.ofHours(24), Duration.between(nine[1].toInstant(), nine[2].toInstant()))
    // Le 2:30 che mancano diventano le 3:30 (RFC 5545 § 3.3.5); quelle che si ripetono valgono la prima volta.
    val gap = daily.occurrences(LocalDateTime.parse("2026-03-29T02:30"), rome).first()
    assertEquals(LocalTime.of(3, 30), gap.toLocalTime())
    val overlap = daily.occurrences(LocalDateTime.parse("2026-10-25T02:30"), rome).first()
    assertEquals(ZoneOffset.ofHours(2), overlap.offset)
    // Ogni mese il 31 alle 9 a New York, attraverso il cambio di marzo: sempre le 9.
    val ny = ZoneId.of("America/New_York")
    FluidRecurrenceRule(FluidRecurrenceFrequency.Monthly).occurrences(LocalDateTime.parse("2026-01-31T09:00"), ny).take(4)
      .forEach { assertEquals(LocalTime.of(9, 0), it.toLocalTime()) }
  }

  @Test fun rruleRoundTrip() {
    val rules = listOf(
      weekly(MONDAY, THURSDAY, end = FluidRecurrenceEnd.OnDate(d("2026-12-20"))) to "FREQ=WEEKLY;BYDAY=MO,TH;UNTIL=20261220",
      FluidRecurrenceRule(FluidRecurrenceFrequency.Monthly, daysOfWeek = listOf(FluidRecurrenceDay.last(FRIDAY)), end = FluidRecurrenceEnd.AfterCount(10))
        to "FREQ=MONTHLY;BYDAY=-1FR;COUNT=10",
      FluidRecurrenceRule(FluidRecurrenceFrequency.Daily, interval = 3) to "FREQ=DAILY;INTERVAL=3",
      FluidRecurrenceRule(FluidRecurrenceFrequency.Yearly, daysOfWeek = listOf(FluidRecurrenceDay(THURSDAY, 4)), monthsOfYear = listOf(Month.NOVEMBER))
        to "FREQ=YEARLY;BYDAY=4TH;BYMONTH=11",
      FluidRecurrenceRule(FluidRecurrenceFrequency.Monthly, daysOfMonth = listOf(1, -1), setPositions = listOf(1), weekStart = SUNDAY)
        to "FREQ=MONTHLY;BYMONTHDAY=1,-1;BYSETPOS=1;WKST=SU",
    )
    rules.forEach { (rule, text) ->
      assertEquals(text, rule.toRRule())
      assertEquals(rule, FluidRecurrenceRule.parseRRule(text))
      assertEquals(rule, FluidRecurrenceRule.parseRRule("RRULE:$text"))
    }
    assertEquals(
      FluidRecurrenceEnd.OnDate(d("2026-12-20")),
      FluidRecurrenceRule.parseRRule("FREQ=DAILY;UNTIL=20261220T225959Z").end,
    )
    assertThrows(IllegalArgumentException::class.java) { FluidRecurrenceRule.parseRRule("FREQ=DAILY;BYHOUR=9") }
    assertThrows(IllegalArgumentException::class.java) { FluidRecurrenceRule.parseRRule("FREQ=DAILY;COUNT=2;UNTIL=20261220") }
  }

  @Test fun invalidRulesAreRejected() {
    assertThrows(IllegalArgumentException::class.java) { FluidRecurrenceRule(FluidRecurrenceFrequency.Daily, interval = 0) }
    assertThrows(IllegalArgumentException::class.java) { FluidRecurrenceRule(FluidRecurrenceFrequency.Monthly, daysOfMonth = listOf(32)) }
    assertThrows(IllegalArgumentException::class.java) { weekly().copy(daysOfWeek = listOf(FluidRecurrenceDay(MONDAY, 1))) }
    assertThrows(IllegalArgumentException::class.java) { FluidRecurrenceEnd.AfterCount(0) }
    assertThrows(IllegalArgumentException::class.java) { FluidRecurrenceDay(MONDAY, 6) }
  }

  // --- La frase -----------------------------------------------------------------------------------------------------

  private fun it(rule: FluidRecurrenceRule, start: String = "2026-11-24") = FluidRecurrenceText.describe(rule, d(start), italian)
  private fun en(rule: FluidRecurrenceRule, start: String = "2026-11-24") = FluidRecurrenceText.describe(rule, d(start), english)

  @Test fun sentenceInItalianAndEnglish() {
    val monThu = weekly(MONDAY, THURSDAY, end = FluidRecurrenceEnd.OnDate(d("2026-12-20")))
    assertEquals("Ogni lun e gio fino al 20 dicembre", it(monThu))
    assertEquals("Every Mon and Thu until December 20", en(monThu))

    val lastFriday = FluidRecurrenceRule(FluidRecurrenceFrequency.Monthly, daysOfWeek = listOf(FluidRecurrenceDay.last(FRIDAY)), end = FluidRecurrenceEnd.AfterCount(10))
    assertEquals("Ogni mese l'ultimo venerdì, 10 volte", it(lastFriday))
    assertEquals("Every month on the last Friday, 10 times", en(lastFriday))

    val daily = FluidRecurrenceRule(FluidRecurrenceFrequency.Daily)
    assertEquals("Ogni giorno", it(daily))
    assertEquals("Every day", en(daily))
    assertEquals("Ogni 3 giorni, 1 volta", it(daily.copy(interval = 3, end = FluidRecurrenceEnd.AfterCount(1))))
    assertEquals("Every 3 days, once", en(daily.copy(interval = 3, end = FluidRecurrenceEnd.AfterCount(1))))

    val monthly = FluidRecurrenceRule(FluidRecurrenceFrequency.Monthly)
    assertEquals("Ogni mese il 31", it(monthly, "2026-01-31"))
    assertEquals("Every month on the 31st", en(monthly, "2026-01-31"))
    assertEquals("Ogni 2 mesi il 1°", it(monthly.copy(interval = 2), "2026-01-01"))
    assertEquals("Every month on the 1st and the 15th", en(monthly.copy(daysOfMonth = listOf(15, 1))))
    assertEquals("Ogni mese l'8 e l'ultimo giorno", it(monthly.copy(daysOfMonth = listOf(-1, 8))))

    val yearly = FluidRecurrenceRule(FluidRecurrenceFrequency.Yearly)
    assertEquals("Ogni anno il 29 febbraio", it(yearly, "2024-02-29"))
    assertEquals("Every year on February 29", en(yearly, "2024-02-29"))
  }

  @Test fun sentenceNamesTheUsualCases() {
    val weekdays = weekly(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)
    assertEquals("Ogni giorno feriale", it(weekdays))
    assertEquals("Every weekday", en(weekdays))
    assertEquals("Ogni fine settimana", it(weekly(SUNDAY, SATURDAY)))
    assertEquals("Ogni martedì", it(FluidRecurrenceRule(FluidRecurrenceFrequency.Weekly)))
    assertEquals("Every Tuesday", en(FluidRecurrenceRule(FluidRecurrenceFrequency.Weekly)))
    assertEquals("Ogni 2 settimane, lun e gio", it(weekly(THURSDAY, MONDAY, interval = 2)))
    assertEquals("Every 2 weeks on Mon and Thu", en(weekly(THURSDAY, MONDAY, interval = 2)))
    assertEquals("Ogni 2 settimane, la domenica", it(weekly(SUNDAY, interval = 2)))
    assertEquals("Every Mon, Wed, and Fri", en(weekly(FRIDAY, MONDAY, WEDNESDAY)))
    assertEquals("Ogni lun, mer e ven", it(weekly(FRIDAY, MONDAY, WEDNESDAY)))

    val monthly = FluidRecurrenceRule(FluidRecurrenceFrequency.Monthly)
    assertEquals("Ogni mese l'ultima domenica", it(monthly.copy(daysOfWeek = listOf(FluidRecurrenceDay.last(SUNDAY)))))
    assertEquals("Ogni mese la prima domenica", it(monthly.copy(daysOfWeek = listOf(FluidRecurrenceDay(SUNDAY, 1)))))
    assertEquals("Ogni mese il secondo lunedì", it(monthly.copy(daysOfWeek = listOf(FluidRecurrenceDay(MONDAY, 2)))))
    val lastWeekday = monthly.copy(daysOfWeek = listOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY).map { FluidRecurrenceDay(it) }, setPositions = listOf(-1))
    assertEquals("Ogni mese l'ultimo giorno feriale", it(lastWeekday))
    assertEquals("Every month on the last weekday", en(lastWeekday))

    val thanksgiving = FluidRecurrenceRule(FluidRecurrenceFrequency.Yearly, daysOfWeek = listOf(FluidRecurrenceDay(THURSDAY, 4)), monthsOfYear = listOf(Month.NOVEMBER))
    assertEquals("Ogni anno il quarto giovedì di novembre", it(thanksgiving))
    assertEquals("Every year on the fourth Thursday of November", en(thanksgiving))
  }

  @Test fun sentenceEndsWithTheRightPrepositionAndYear() {
    val daily = FluidRecurrenceRule(FluidRecurrenceFrequency.Daily)
    assertEquals("Ogni giorno fino all'8 dicembre", it(daily.copy(end = FluidRecurrenceEnd.OnDate(d("2026-12-08")))))
    assertEquals("Ogni giorno fino all'11 dicembre", it(daily.copy(end = FluidRecurrenceEnd.OnDate(d("2026-12-11")))))
    assertEquals("Ogni giorno fino al 1° gennaio 2027", it(daily.copy(end = FluidRecurrenceEnd.OnDate(d("2027-01-01")))))
    assertEquals("Every day until January 1, 2027", en(daily.copy(end = FluidRecurrenceEnd.OnDate(d("2027-01-01")))))
    // Le altre lingue hanno la frase inglese.
    assertEquals("Every day", FluidRecurrenceText.describe(daily, d("2026-11-24"), Locale.GERMANY))
  }

  // --- Le date ------------------------------------------------------------------------------------------------------

  @Test fun firstDayOfWeekFollowsTheLanguage() {
    assertEquals(MONDAY, FluidDates.firstDayOfWeek(Locale.ITALY))
    assertEquals(SUNDAY, FluidDates.firstDayOfWeek(Locale.US))
    assertEquals(MONDAY, FluidDates.firstDayOfWeek(Locale.UK))
    assertEquals(SUNDAY, FluidDates.firstDayOfWeek(Locale.forLanguageTag("he-IL")))
    assertEquals(SATURDAY, FluidDates.firstDayOfWeek(Locale.forLanguageTag("fa-IR")))
    assertEquals(listOf(MONDAY, SUNDAY), FluidDates.weekdays(Locale.ITALY).let { listOf(it.first(), it.last()) })
    assertEquals(listOf(SUNDAY, SATURDAY), FluidDates.weekdays(Locale.US).let { listOf(it.first(), it.last()) })
    assertEquals(FluidDateRange(d("2026-11-23"), d("2026-11-29")), FluidDates.weekOf(d("2026-11-25"), Locale.ITALY))
    assertEquals(FluidDateRange(d("2026-11-22"), d("2026-11-28")), FluidDates.weekOf(d("2026-11-25"), Locale.US))
  }

  @Test fun aRangeAcrossDaylightSavingIsLocalDays() {
    val rome = ZoneId.of("Europe/Rome")
    val spring = FluidDateRange(d("2026-03-28"), d("2026-03-30"))
    assertEquals(3L, spring.dayCount)
    assertEquals(Duration.ofHours(71), spring.duration(rome))
    assertEquals(dates("2026-03-28", "2026-03-29", "2026-03-30"), spring.toList())
    val autumn = FluidDateRange(d("2026-10-24"), d("2026-10-26"))
    assertEquals(3L, autumn.dayCount)
    assertEquals(Duration.ofHours(73), autumn.duration(rome))
    assertEquals(LocalDate.parse("2026-10-27").atStartOfDay(rome).toInstant(), autumn.endInstantExclusive(rome))
    assertEquals(spring, FluidDateRange.between(d("2026-03-30"), d("2026-03-28")))
    assertTrue(d("2026-03-29") in spring)
    assertFalse(d("2026-03-31") in spring)
    assertTrue(FluidDateRange(d("2026-03-29"), d("2026-03-29")).isSingleDay)
    assertThrows(IllegalArgumentException::class.java) { FluidDateRange(d("2026-03-30"), d("2026-03-28")) }
  }
}
