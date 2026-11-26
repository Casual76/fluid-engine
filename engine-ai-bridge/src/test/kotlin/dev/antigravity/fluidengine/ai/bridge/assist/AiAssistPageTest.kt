package dev.antigravity.fluidengine.ai.bridge.assist

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F9.1 · La pagina che passa a PampAI: corta per costruzione, senza dati di troppo, uguale a quella che mostra il chip. */
class AiAssistPageTest {

  @Test fun `una pagina normale passa com'e'`() {
    val page = AiAssistPage("Matematica · verifica del 12 novembre", listOf("Voto 5½, peso 100%", "Media 6,8 (prima 7,1)"), kind = "voto")
    assertFalse(page.truncated)
    assertEquals("Matematica · verifica del 12 novembre\n• Voto 5½, peso 100%\n• Media 6,8 (prima 7,1)", page.preview())
  }

  @Test fun `una pagina lunga resta nei tetti, e lo dice`() {
    val page = AiAssistPage(
      title = "T".repeat(500),
      lines = (1..200).map { "riga $it " + "x".repeat(400) },
      selection = "s".repeat(10_000),
      kind = "k".repeat(100),
    )
    assertTrue(page.truncated)
    assertEquals(AiAssistPage.MaxTitleChars, page.title.length)
    assertEquals(AiAssistPage.MaxKindChars, page.kind!!.length)
    assertTrue(page.lines.size <= AiAssistPage.MaxLines)
    page.lines.forEach { assertTrue(it.length <= AiAssistPage.MaxLineChars) }
    assertTrue("in tutto al massimo ${AiAssistPage.MaxTotalChars}: ${page.length}", page.length <= AiAssistPage.MaxTotalChars)
    assertTrue(page.lines.first().startsWith("riga 1 "))
  }

  @Test fun `la selezione prende il posto che resta, e cade per prima se non ce n'e'`() {
    val full = AiAssistPage("Titolo", (1..8).map { "y".repeat(160) }, selection = "z".repeat(3_000))
    assertTrue(full.length <= AiAssistPage.MaxTotalChars)
    assertEquals(AiAssistPage.MaxTotalChars - 6 - 8 * 160, full.selection!!.length)
    val small = AiAssistPage("Titolo", listOf("riga"), selection = "a\n\n\n\nb\tc")
    assertEquals("a\n\nb c", small.selection)
    assertFalse(small.truncated)
  }

  @Test fun `i caratteri di controllo e gli spazi doppi spariscono, le righe vuote pure`() {
    val page = AiAssistPage("  Ciao\u0000​   mondo\n", listOf("", "   ", "a\u0007b", "uno\tdue"))
    assertEquals("Ciao mondo", page.title)
    assertEquals(listOf("ab", "uno due"), page.lines)
    assertNull(page.selection)
  }

  @Test fun `sul filo passano solo i quattro campi, e un'app che manda di piu' non ottiene di piu'`() {
    val json = AiAssistProtocol.pageJson(AiAssistPage("Voti", listOf("5½"), selection = "testo", kind = "voto"))
    assertEquals(setOf("title", "kind", "lines", "selection"), json.keys)
    // Un JSON scritto a mano, con campi in piu' e righe enormi: si rilegge dentro i tetti, senza i campi.
    val forged = buildJsonObject {
      put("title", "Voti")
      put("lines", buildJsonArray { repeat(50) { add(JsonPrimitive("r".repeat(1_000))) }; add(JsonPrimitive(42)) })
      put("email", "mario.rossi@example.com")
      put("studentId", "123456")
    }
    val page = AiAssistProtocol.pageOf(forged)!!
    assertTrue(page.truncated)
    assertTrue(page.length <= AiAssistPage.MaxTotalChars)
    val back = AiAssistProtocol.pageJson(page).toString()
    assertFalse(back.contains("mario.rossi"))
    assertFalse(back.contains("123456"))
    assertEquals("il numero (non un testo) si scarta, le righe restano otto", AiAssistPage.MaxLines, (AiAssistProtocol.pageJson(page)["lines"] as JsonArray).size)
  }

  @Test fun `la domanda fa il giro del filo`() {
    val request = AiAssistRequest(AiAssistTask.summarize("circolare", AiAssistTask.FormKeyPoints), page = AiAssistPage("Circolare 42"), tools = AiAssistRequest.ToolsNone, language = "en", timeoutMillis = 30_000)
    val back = AiAssistProtocol.askOf(AiAssistProtocol.askRequest(request))!!
    assertEquals(request.task, back.task)
    assertEquals(request.page, back.page)
    assertEquals(AiAssistRequest.ToolsNone, back.tools)
    assertEquals("en", back.language)
    assertEquals(30_000L, back.timeoutMillis)
    assertTrue(back.pageSent)
  }
}
