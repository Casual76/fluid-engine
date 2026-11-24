package dev.antigravity.fluidengine.ui.fluid

import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidDetectedTextRulesTest {
  private val today = LocalDate.of(2025, 11, 27)
  private val all = FluidDetectedTextKind.entries.toSet()

  @Test fun italianSchoolMailAndNotes() {
    val cases = listOf(
      "Il laboratorio si sposta a venerdì prossimo. Portare il quaderno." to "venerdì prossimo",
      "Il versamento va fatto il 28/11 entro la chiusura della segreteria." to "il 28/11",
      "Si ricorda l’uscita venerdì 28. Portare il pranzo al sacco." to "venerdì 28",
      "Passo a prendere le chiavi domani alle 8." to "domani alle 8",
      "La prossima assemblea è lunedì 1 dicembre in aula magna." to "lunedì 1 dicembre",
    )
    for ((source, expected) in cases) {
      val value = detectTextRules(source, Locale.ITALY, today, all).single()
      assertEquals(expected, value.text)
      assertEquals(expected, source.substring(value.start, value.end))
      assertEquals(FluidDetectedTextKind.Date, value.kind)
      assertEquals(if (expected.startsWith("lunedì")) today.plusDays(4) else today.plusDays(1), value.date)
      assertEquals(if (expected.startsWith("domani")) LocalTime.of(8, 0) else null, value.time)
    }
  }

  @Test fun englishSchoolMailAndNotes() {
    for (source in listOf("Pick up the parcel next Friday.", "Field trip Friday 28.", "Foundation meeting on 11/28.")) {
      assertEquals(today.plusDays(1), detectTextRules(source, Locale.US, today, all).single().date)
    }
    for ((source, region) in listOf(
      "The workshop starts on 28/11." to Locale.UK,
      "The workshop starts on 11/28." to Locale.US,
    )) {
      assertEquals(LocalDate.of(2025, 11, 28), detectTextRules(source, region, today, all).single().date)
    }
    val us = detectTextRules("Doors open tomorrow at 8:15 PM.", Locale.US, today, all).single()
    assertEquals(today.plusDays(1), us.date)
    assertEquals(LocalTime.of(20, 15), us.time)
    val uk = detectTextRules("The performance is Monday, 1 December.", Locale.UK, today, all).single()
    assertEquals(LocalDate.of(2025, 12, 1), uk.date)
    assertEquals(LocalTime.MIDNIGHT, detectTextRules("Alarm at 12 am.", Locale.UK, today, all).single().time)
  }

  @Test fun calendarRejectsImpossibleDaysAndResolvesYearBoundaries() {
    assertTrue(detectTextRules("Avviso il 31/02/2026 e appuntamento venerdì 29 novembre.", Locale.ITALY, today, all).isEmpty())
    val newYear = LocalDate.of(2025, 12, 31)
    assertEquals(LocalDate.of(2026, 1, 1), detectTextRules("Ci vediamo il 01/01.", Locale.ITALY, newYear, all).single().date)
    assertEquals(today.plusDays(8), detectTextRules("venerdì prossimo", Locale.ITALY, today.plusDays(1), all).single().date)
    assertEquals(LocalDate.of(2028, 2, 29), detectTextRules("Prova il 29/02/2028.", Locale.ITALY, today, all).single().date)
  }

  @Test fun gradesAmountsCodesAndYearsAreNotPhones() {
    for (source in listOf(
      "Voti 7,5 e 8.25. Totale € 123 456 789.",
      "Circolare 381204, pratica 0201234567, anno 2026.",
      "Codice 1234567890123456789012345 e importo 123.456.789,00 €.",
      "Voto 7/10, titolo del capitolo 3/4, proporzione 2:150.",
      "Scuola 2025–2026. Il totale è 1.23456789.",
      "Reference A01234567890B and invoice $ 123456789.",
      "Pratica 123 456 789. Indirizzo IP 192.168.178.80.",
    )) {
      assertTrue(source, detectTextRules(source, Locale.ITALY, today, all).isEmpty())
    }
  }

  @Test fun sixKindsHaveOriginalTextOffsetsAndNormalizedDestinations() {
    val source = "🙂 Il 28/11 alle 8:15. Poi alle 12:30 in via Roma 12. " +
      "Tel. +39 02 5550 1234; scrivi ad anna@example.org. Leggi (https://example.org/2026-11-28)."
    val values = detectTextRules(source, Locale.ITALY, today, all)
    assertEquals(listOf(
      FluidDetectedTextKind.Date, FluidDetectedTextKind.Time, FluidDetectedTextKind.Address,
      FluidDetectedTextKind.Phone, FluidDetectedTextKind.Email, FluidDetectedTextKind.Link,
    ), values.map { it.kind })
    assertEquals(listOf(
      "Il 28/11 alle 8:15", "alle 12:30", "via Roma 12", "+39 02 5550 1234",
      "anna@example.org", "https://example.org/2026-11-28",
    ), values.map { it.text })
    assertEquals("+390255501234", values[3].value)
    values.forEach { assertEquals(it.text, source.substring(it.start, it.end)) }
    assertTrue(detectTextRules(source, Locale.ITALY, today, setOf(FluidDetectedTextKind.Link)).all {
      it.kind == FluidDetectedTextKind.Link
    })
  }

  @Test fun timeAndDateCanBeEnabledIndependently() {
    val text = "La consegna è domani alle 8."
    val withDate = detectTextRules(text, Locale.ITALY, today, setOf(FluidDetectedTextKind.Date)).single()
    assertEquals("domani", withDate.text)
    assertEquals(null, withDate.time)
    val withTime = detectTextRules(text, Locale.ITALY, today, setOf(FluidDetectedTextKind.Time)).single()
    assertEquals("alle 8", withTime.text)
    assertEquals(today.plusDays(1), withTime.date)
    assertEquals(LocalTime.of(8, 0), withTime.time)
  }

  @Test fun addressPhoneAndUrlSupportEnglishAndOccasionalPunctuation() {
    val text = "Meet at 221B Baker Street, then 22 King Street. Call (202) 555-0142 or go to www.example.org."
    val detected = detectTextRules(text, Locale.UK, today, all)
    assertEquals(listOf("221B Baker Street", "22 King Street", "(202) 555-0142", "www.example.org"), detected.map { it.text })
    assertEquals("2025550142", detected[2].value)
    assertEquals("https://www.example.org", detected[3].value)
    assertEquals("via Roma, 12", detectTextRules("In via Roma, 12.", Locale.ITALY, today, all).single().text)
  }

  @Test fun excerptsFromPublicNotices() {
    // Circolare 164 IISS Rosa Luxemburg, 27 novembre 2025.
    val italian = detectTextRules("la classe 2A Sas uscirà alle ore 11.15", Locale.ITALY, today, all).single()
    assertEquals(LocalTime.of(11, 15), italian.time)
    // Flamborough Parish Council, calendario pubblico 2025.
    val british = detectTextRules("Monday 1 December 2025 at 7.30pm.", Locale.UK, today, all).single()
    assertEquals(LocalDate.of(2025, 12, 1), british.date)
    assertEquals(LocalTime.of(19, 30), british.time)
  }

  @Test fun longTranscriptKeepsOrderedOffsets() {
    val repeated = "La docente chiude il laboratorio domani alle 8; voto 7,5 e pratica 12345678. "
    val transcript = repeated.repeat(20_000 / repeated.length) + "Ultimo: via Roma 12."
    val values = detectTextRules(transcript, Locale.ITALY, today, all)
    assertEquals(transcript.length / repeated.length + 1, values.size)
    assertEquals("via Roma 12", values.last().text)
    assertTrue(values.zipWithNext().all { (first, second) -> first.end <= second.start })
  }
}
