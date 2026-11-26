package dev.antigravity.fluidengine.diagnostics

import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** F8.24 · Il registro circolare e il filtro dei dati personali, sul JVM. */
class FluidLogAndScrubberTest {
  @After
  fun tearDown() {
    FluidLog.clear()
    FluidLog.clock = System::currentTimeMillis
  }

  @Test
  fun personalDataNeverLeaves() {
    val cases = mapOf(
      "Mandata a marta.rossi@example.com" to "Mandata a «[email]»",
      "Chiamo +39 333 123 4567 domani" to "Chiamo «[telefono]» domani",
      "IBAN IT60X0542811101000000123456 ok" to "IBAN «[iban]» ok",
      "carta 4111 1111 1111 1111 rifiutata" to "carta «[carta]» rifiutata",
      "cf RSSMRA85T10A562S" to "cf «[codice fiscale]»",
      "da 192.168.1.20 alle 12:30:45" to "da «[ip]» alle 12:30:45",
      "Authorization: Bearer abcDEF123._-xyz" to "Authorization: Bearer «[chiave]»",
      "token=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.c2lnbmF0dXJh" to "token=«[nascosto]»",
      "password: hunter2" to "password: «[nascosto]»",
      "GET https://api.classeviva.it/rest/v1/students/1234567/grades?x=1" to "GET https://api.classeviva.it/«…»",
      "aperto content://media/external/images/12" to "aperto «[file]»",
      "letto /storage/emulated/0/Download/pagella.pdf" to "letto «[file]»",
      "id 3f2b8c4e-1a2b-4c3d-9e8f-001122334455" to "id «[id]»",
      "chiave sk-0123456789abcdefABCDEF0123456789zz" to "chiave «[chiave]»",
    )
    for ((input, expected) in cases) assertEquals(input, expected, FluidPrivacyScrubber.scrub(input))
  }

  @Test
  fun ordinaryTextStaysReadable() {
    val keep = listOf(
      "Sincronizzate 3 note in 1200 ms",
      "Versione 2.0.0 (12), engine 3.1.2",
      "Il 26/11/2026 alle 14:03",
      "Pagina impostazioni aperta",
      "Timeout dopo 15 s, 2 tentativi",
    )
    for (text in keep) assertEquals(text, FluidPrivacyScrubber.scrub(text))
    // Un numero lungo che non è una carta (Luhn sbagliato) resta un numero di telefono, non una carta.
    assertFalse(FluidPrivacyScrubber.luhn("4111111111111112"))
    assertTrue(FluidPrivacyScrubber.luhn("4111111111111111"))
  }

  @Test
  fun theLogIsARingAndExportsScrubbed() {
    var t = 0L
    FluidLog.clock = { t }
    FluidLog.clear()
    repeat(FluidLog.Capacity + 25) { i ->
      t = i * 1000L
      FluidLog.event("prova", "evento $i")
    }
    val events = FluidLog.snapshot()
    assertEquals(FluidLog.Capacity, events.size)
    assertEquals("evento 25", events.first().message)
    assertEquals("evento ${FluidLog.Capacity + 24}", events.last().message)
    FluidLog.warn("posta", "Inviata a luca@example.com")
    val exported = FluidLog.export(limit = 2, zone = ZoneOffset.UTC)
    assertEquals(2, exported.size)
    assertTrue(exported.last(), exported.last().endsWith("W posta: Inviata a «[email]»"))
    // Il registro tiene il testo vero solo in memoria: è l'esportazione che lo pulisce.
    assertTrue(FluidLog.snapshot().last().message.contains("luca@example.com"))
  }

  @Test
  fun longMessagesAreCut() {
    FluidLog.clear()
    FluidLog.event("x".repeat(80), "y".repeat(1000))
    val event = FluidLog.snapshot().single()
    assertEquals(32, event.tag.length)
    assertEquals(FluidLog.MaxMessageLength, event.message.length)
  }
}
