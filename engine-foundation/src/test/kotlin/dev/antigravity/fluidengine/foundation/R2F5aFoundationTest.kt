package dev.antigravity.fluidengine.foundation

import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R2-F5a · le correzioni pure di `engine-foundation` della review R2 (`muse-f8-prime.md`): le ricorrenze con giorni del
 * mese e giorni della settimana insieme (e la loro frase), le password con il solo massimo e coi caratteri fuori dal
 * piano di base, i numeri incollati con separatori a caso o con le cifre arabe, l'intervallo che finisce all'ultimo
 * giorno possibile.
 */
class R2F5aFoundationTest {
  private val it = Locale.ITALY
  private val en = Locale.US
  private fun d(text: String) = LocalDate.parse(text)

  // --- 38. Le ricorrenze ------------------------------------------------------------------------------------------

  @Test fun `venerdi' 13, l'esempio di RFC 5545, e' l'intersezione dei due insiemi`() {
    // RFC 5545 § 3.3.10, nota 1: in una regola mensile BYDAY con BYMONTHDAY **limita**. Il revisore chiedeva l'unione:
    // qui resta l'intersezione, e la prova lo fissa con l'esempio della RFC (§ 3.8.5.3, «Every Friday the 13th»).
    val rule = FluidRecurrenceRule.parseRRule("FREQ=MONTHLY;BYDAY=FR;BYMONTHDAY=13")
    assertEquals(listOf(d("2026-02-13"), d("2026-03-13"), d("2026-11-13"), d("2027-08-13")), rule.occurrences(d("2026-01-01"), 4))
  }

  @Test fun `il numero del giorno della settimana conta anche quando limita i giorni del mese`() {
    // Il secondo venerdi', se cade fra l'1 e il 14: e' sempre il secondo venerdi', mai il primo.
    val second = FluidRecurrenceRule.parseRRule("FREQ=MONTHLY;BYDAY=2FR;BYMONTHDAY=1,2,3,4,5,6,7,8,9,10,11,12,13,14")
    assertEquals(listOf(d("2026-01-09"), d("2026-02-13"), d("2026-03-13")), second.occurrences(d("2026-01-01"), 3))
    // Il 15 che e' anche l'ultimo venerdi' del mese: non succede mai (l'ultimo venerdi' viene dal 22 in poi).
    val never = FluidRecurrenceRule.parseRRule("FREQ=MONTHLY;BYMONTHDAY=15;BYDAY=-1FR")
    assertEquals(emptyList<LocalDate>(), never.occurrences(d("2026-01-01"), 3))
  }

  @Test fun `la frase dice anche il giorno della settimana che limita i giorni del mese`() {
    val friday13 = FluidRecurrenceRule.parseRRule("FREQ=MONTHLY;BYDAY=FR;BYMONTHDAY=13")
    assertEquals("Ogni mese il 13, se è venerdì", FluidRecurrenceText.describe(friday13, d("2026-01-01"), it))
    assertEquals("Every month on the 13th, if it's a Friday", FluidRecurrenceText.describe(friday13, d("2026-01-01"), en))
    val lastFriday15 = FluidRecurrenceRule.parseRRule("FREQ=MONTHLY;BYMONTHDAY=15;BYDAY=-1FR")
    assertEquals("Ogni mese il 15, se è l'ultimo venerdì", FluidRecurrenceText.describe(lastFriday15, d("2026-01-01"), it))
  }

  @Test fun `una regola giornaliera con dei limiti non e' ogni giorno`() {
    val mondays = FluidRecurrenceRule(FluidRecurrenceFrequency.Daily, daysOfWeek = listOf(FluidRecurrenceDay(DayOfWeek.MONDAY)))
    assertEquals(d("2026-11-30"), mondays.occurrences(d("2026-11-24"), 1).single())
    assertEquals("Ogni lunedì", FluidRecurrenceText.describe(mondays, d("2026-11-24"), it))
    assertEquals("Every Monday", FluidRecurrenceText.describe(mondays, d("2026-11-24"), en))
    val weekdays = FluidRecurrenceRule.parseRRule("FREQ=DAILY;BYDAY=MO,TU,WE,TH,FR")
    assertEquals("Ogni giorno feriale", FluidRecurrenceText.describe(weekdays, d("2026-11-24"), it))
    val everyOtherMonday = mondays.copy(interval = 2)
    assertEquals("Ogni 2 giorni, solo il lunedì", FluidRecurrenceText.describe(everyOtherMonday, d("2026-11-24"), it))
    assertEquals("Every 2 days, only on Monday", FluidRecurrenceText.describe(everyOtherMonday, d("2026-11-24"), en))
    val january = FluidRecurrenceRule(FluidRecurrenceFrequency.Daily, monthsOfYear = listOf(Month.JANUARY))
    assertEquals("Ogni giorno (gennaio)", FluidRecurrenceText.describe(january, d("2026-11-24"), it))
    val fifteenth = FluidRecurrenceRule(FluidRecurrenceFrequency.Daily, daysOfMonth = listOf(15))
    assertEquals("Ogni giorno, solo il 15", FluidRecurrenceText.describe(fifteenth, d("2026-11-24"), it))
    // Senza limiti, come prima.
    assertEquals("Ogni giorno", FluidRecurrenceText.describe(FluidRecurrenceRule(FluidRecurrenceFrequency.Daily), d("2026-11-24"), it))
  }

  @Test fun `una regola settimanale limitata ad alcuni mesi lo dice`() {
    val rule = FluidRecurrenceRule(
      FluidRecurrenceFrequency.Weekly,
      daysOfWeek = listOf(FluidRecurrenceDay(DayOfWeek.MONDAY)),
      monthsOfYear = listOf(Month.JULY, Month.AUGUST),
    )
    assertEquals("Ogni lunedì (luglio e agosto)", FluidRecurrenceText.describe(rule, d("2026-11-24"), it))
    assertEquals("Every Monday (July and August)", FluidRecurrenceText.describe(rule, d("2026-11-24"), en))
  }

  // --- Minori: le password ----------------------------------------------------------------------------------------

  @Test fun `con il solo massimo la password proposta non lo supera`() {
    val rules = FluidPasswordRules.parse("maxlength: 6; required: digit")
    assertEquals(6, rules.maxLength)
    repeat(20) {
      val password = rules.generate()
      assertTrue("«$password» oltre 6", password.length <= 6)
      assertTrue("«$password» non rispetta le regole", rules.isSatisfiedBy(password))
    }
    // Con un massimo sopra il minimo di serie, il minimo resta 8.
    assertEquals(8, FluidPasswordRules.parse("maxlength: 20").minLength)
  }

  @Test fun `le lunghezze e le ripetizioni contano i caratteri, non le meta' di un emoji`() {
    assertFalse(FluidPasswordRules(minLength = 8).isSatisfiedBy("🔑🔑🔑🔑"))
    assertTrue(FluidPasswordRules(minLength = 4).isSatisfiedBy("🔑🔑🔑🔑"))
    assertFalse(FluidPasswordRules(minLength = 1, maxConsecutive = 2).isSatisfiedBy("a🔑🔑🔑b"))
    assertTrue(FluidPasswordRules(minLength = 1, maxLength = 4).isSatisfiedBy("🔑🔑🔑🔑"))
  }

  @Test fun `due regole uguali sono uguali, cosi' la password proposta non cambia a ogni ricomposizione`() {
    assertEquals(FluidPasswordRules.parse("minlength: 10; required: upper"), FluidPasswordRules.parse("minlength: 10; required: upper"))
    assertEquals(
      FluidPasswordRules.parse("minlength: 10; required: upper").hashCode(),
      FluidPasswordRules.parse("minlength: 10; required: upper").hashCode(),
    )
    assertFalse(FluidPasswordRules.parse("minlength: 10") == FluidPasswordRules.parse("minlength: 11"))
  }

  // --- Minori: i numeri incollati ---------------------------------------------------------------------------------

  @Test fun `un separatore ripetuto senza gruppi da tre non e' un numero`() {
    assertNull(FluidUnitFormat.parse("1.2.3", it))
    assertNull(FluidUnitFormat.parse("1.23.4", it))
    assertEquals(BigDecimal("1234567"), FluidUnitFormat.parse("1.234.567", it))
    assertEquals(BigDecimal("1234567.5"), FluidUnitFormat.parse("1.234.567,5", it))
    assertEquals(BigDecimal("1234.56"), FluidUnitFormat.parse("1,234.56", it))
    // I gruppi dell'India (3 + 2 + 2) si leggono ancora.
    assertEquals(BigDecimal("100000"), FluidUnitFormat.parse("1,00,000", Locale.forLanguageTag("en-IN")))
  }

  @Test fun `con le cifre arabe niente separatori arabi accanto alle cifre latine, e le cifre arabe si leggono`() {
    val egypt = Locale.forLanguageTag("ar-EG")
    val written = FluidUnitFormat.number(BigDecimal("1234.5"), egypt, maxFractionDigits = 1)
    assertTrue("«$written» mescola", written.all { it in '0'..'9' || it == '.' || it == ',' })
    assertEquals(BigDecimal("1234.5"), FluidUnitFormat.parse(written, egypt))
    assertEquals(BigDecimal("1234.5"), FluidUnitFormat.parse("١٬٢٣٤٫٥", egypt))
    assertEquals(BigDecimal("12"), FluidUnitFormat.parse("١٢", egypt))
  }

  // --- Minore: l'intervallo che finisce all'ultimo giorno -----------------------------------------------------------

  @Test fun `un intervallo che finisce all'ultimo giorno possibile si scorre fino in fondo`() {
    val range = FluidDateRange(LocalDate.MAX.minusDays(2), LocalDate.MAX)
    assertEquals(3, range.toList().size)
    assertEquals(1, FluidDateRange(LocalDate.MAX, LocalDate.MAX).toList().size)
  }
}
