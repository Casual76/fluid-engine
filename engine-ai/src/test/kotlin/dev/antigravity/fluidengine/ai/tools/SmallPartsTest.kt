package dev.antigravity.fluidengine.ai.tools

import dev.antigravity.fluidengine.ai.net.MultipartBody
import dev.antigravity.fluidengine.ai.speech.Transcriber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I piccoli buchi che le app avevano rattoppato (3.0.0): la riga "altre N righe omesse" che con un
 * resto di uno diceva "altre 1 righe", il boundary del multipart che portava il nome di FluidWeather,
 * e il numero di nomi che si danno a Whisper, che era venti per tutti.
 */
class SmallPartsTest {

  // ------------------------------------------------------------------------------------ ToolText

  private val line = "x".repeat(99)

  @Test
  fun `una riga sola che manca si dice al singolare`() {
    // Tre righe da 100 caratteri con un tetto di 250: ne entrano due.
    val out = ToolText.limit(List(3) { line }.joinToString("\n"), maxChars = 250)
    assertTrue(out, out.endsWith("… (un'altra riga omessa)"))
    assertFalse(out, out.contains("altre 1"))
    assertEquals(2, out.lines().count { it.startsWith("x") })
  }

  @Test
  fun `piu' righe che mancano si dicono al plurale col numero`() {
    val out = ToolText.limit(List(5) { line }.joinToString("\n"), maxChars = 250)
    assertTrue(out, out.endsWith("… (altre 3 righe omesse)"))
  }

  @Test
  fun `il testo che sta nel tetto non cambia, e nemmeno quello costruito col Builder`() {
    assertEquals("a\nb", ToolText.limit("a\nb"))
    assertEquals("riga: 5\nB", ToolText.build { line("riga", 5); line("B") })
    val built = ToolText.build(maxChars = 250) { repeat(4) { line(line) } }
    assertTrue(built, built.endsWith("… (altre 2 righe omesse)"))
  }

  @Test
  fun `la frase di cosa manca e' una funzione, per chi tronca per conto suo`() {
    assertEquals("un'altra riga omessa", ToolText.omitted(1))
    assertEquals("altre 2 righe omesse", ToolText.omitted(2))
    assertEquals("altre 40 righe omesse", ToolText.omitted(40))
  }

  // ------------------------------------------------------------------------------- MultipartBody

  @Test
  fun `il boundary di serie non porta il nome di nessuna app`() {
    val parts = MultipartBody.build(mapOf("model" to "whisper"), "file", "ask.wav", "audio/wav")
    val boundary = parts.contentType.substringAfter("boundary=")
    assertTrue(boundary, boundary.startsWith("----"))
    assertFalse(boundary, boundary.contains("FluidWeather"))
    assertTrue(boundary, boundary.contains("FluidEngine"))
    // Ed e' lo stesso nel corpo: ogni parte comincia con il suo, la chiusura lo ripete con "--".
    assertTrue(String(parts.prelude, Charsets.UTF_8).startsWith("--$boundary\r\n"))
    assertEquals("\r\n--$boundary--\r\n", String(parts.epilogue, Charsets.UTF_8))
  }

  // ----------------------------------------------------------------------------------- Transcriber

  private val names = (1..30).map { "Nome $it" }

  @Test
  fun `i nomi per Whisper sono venti di serie`() {
    val hint = Transcriber.hint("Domande sul meteo.", names)
    assertTrue(hint, hint.contains("Nome 20"))
    assertFalse(hint, hint.contains("Nome 21"))
    assertTrue(hint.startsWith("Domande sul meteo. Nome 1, Nome 2"))
    assertTrue(hint.endsWith("."))
  }

  @Test
  fun `il numero di nomi e' un parametro`() {
    val twelve = Transcriber.hint("Domande sul meteo.", names, maxWords = 12)
    assertTrue(twelve, twelve.contains("Nome 12"))
    assertFalse(twelve, twelve.contains("Nome 13"))
    val many = Transcriber.hint("Domande sul meteo.", names, maxWords = 25)
    assertTrue(many.contains("Nome 25") && !many.contains("Nome 26"))
    assertEquals("zero nomi: la sola frase", "Domande sul meteo.", Transcriber.hint("Domande sul meteo.", names, maxWords = 0))
    assertEquals("un numero negativo vale zero", "Domande sul meteo.", Transcriber.hint("Domande sul meteo.", names, maxWords = -3))
  }

  @Test
  fun `i nomi vuoti non contano, e senza nomi resta la frase`() {
    assertEquals("Frase.", Transcriber.hint("Frase.", emptyList()))
    assertEquals("Frase. Roma, Pisa.", Transcriber.hint("Frase.", listOf("", "Roma", "  ", "Pisa")))
    // I vuoti non occupano un posto dei venti.
    val padded = List(10) { "" } + names
    assertTrue(Transcriber.hint("Frase.", padded).contains("Nome 20"))
  }

  @Test
  fun `il suggerimento non supera il tetto di Whisper`() {
    val long = List(20) { "N".repeat(100) }
    assertEquals(Transcriber.HINT_MAX_CHARS, Transcriber.hint("Frase.", long).length)
    assertEquals(20, Transcriber.MAX_HINT_WORDS)
  }
}
