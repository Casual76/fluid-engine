package dev.antigravity.fluidengine.ui.fluid

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I minori del testo riconosciuto della review R2 (muse-f8-prime): le frasi normali che sembravano
 * indirizzi, e i link con lettere accentate o col trattino basso nell'host.
 */
class FluidDetectedTextR2Test {
  private val today = LocalDate.of(2025, 11, 27)
  private val all = FluidDetectedTextKind.entries.toSet()

  private fun addresses(text: String): List<String> =
    detectTextRules(text, Locale.ITALY, today, all).filter { it.kind == FluidDetectedTextKind.Address }.map { it.text }

  private fun links(text: String): List<String> =
    detectTextRules(text, Locale.ITALY, today, all).filter { it.kind == FluidDetectedTextKind.Link }.map { it.value }

  @Test fun ordinarySentencesAreNotStreetAddresses() {
    for (text in listOf(
      "Mandato via posta il 12.",
      "Te lo giro via mail entro il 3.",
      "Ho fatto un corso di inglese 10 anni fa.",
      "Nel corso del 2025 cambia tutto.",
      "Un tavolo largo di 3 metri.",
    )) {
      assertEquals(text, emptyList<String>(), addresses(text))
    }
  }

  @Test fun realStreetsAreStillFound() {
    val cases = mapOf(
      "Ci vediamo in via Roma 12." to "via Roma 12",
      "Abito in Via della Repubblica 3." to "Via della Repubblica 3",
      "Corso Vittorio Emanuele II 5, secondo piano." to "Corso Vittorio Emanuele II 5",
      "Scrivo male: via roma, 12." to "via roma, 12",
      "Indirizzo: via roma n. 12." to "via roma n. 12",
      "Interno: via roma 12/A." to "via roma 12/A",
      "Ritrovo in piazza duomo 1." to "piazza duomo 1",
      "Largo Augusto 7 alle 9." to "Largo Augusto 7",
    )
    for ((text, expected) in cases) assertEquals(text, listOf(expected), addresses(text))
  }

  @Test fun linksWithAccentsOrUnderscoresAreFound() {
    assertEquals(listOf("https://esempio.it/città"), links("Leggi https://esempio.it/città oggi"))
    assertEquals(listOf("https://my_host.example.com/x"), links("Prova https://my_host.example.com/x subito"))
    assertEquals(listOf("https://città.it/menu"), links("Vedi https://città.it/menu."))
    assertEquals(listOf("https://www.città.it"), links("Vedi www.città.it oggi"))
    assertEquals(listOf("https://esempio.it/a|b"), links("Apri https://esempio.it/a|b ora"))
    assertEquals("https://esempio.it:8080/x", normalizedWebLink("https://esempio.it:8080/x"))
  }

  @Test fun theHostIsStillRequiredAndCredentialsStillRejected() {
    assertNull(normalizedWebLink("https://localhost/x"))
    assertNull(normalizedWebLink("https:///x"))
    assertNull(normalizedWebLink("https://utente:segreto@esempio.it/"))
    assertNull(normalizedWebLink("https://utente@my_host.example.com/"))
    assertNull(normalizedWebLink("https://.esempio.it/"))
    assertNull(normalizedWebLink("https://esempio..it/"))
    assertNull(normalizedWebLink("ftp://esempio.it/"))
    assertNull(normalizedWebLink("javascript://esempio.it/%0Aalert(1)"))
    assertTrue(links("niente link qui").isEmpty())
  }
}
