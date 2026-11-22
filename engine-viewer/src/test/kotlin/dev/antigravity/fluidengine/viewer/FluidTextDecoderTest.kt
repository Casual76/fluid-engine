package dev.antigravity.fluidengine.viewer

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.Charset

/** Il charset, il tetto di lettura, le righe e il binario del decodificatore di testo. */
class FluidTextDecoderTest {

  private val italian = "Perché più già: «così» è città.\nSeconda riga, € 5.\n"

  private fun read(bytes: ByteArray, mime: String? = null, name: String = "nota.txt", limit: Int = FluidTextDocument.DefaultLimitBytes, maxLine: Int = FluidTextDocument.DefaultMaxLineChars) =
    FluidTextDecoder.read(ByteArrayInputStream(bytes), name, mime, bytes.size.toLong(), limit, maxLine)

  private fun bom(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

  // --- Charset ---------------------------------------------------------------------------------------------------

  @Test
  fun `every bom is recognised and skipped`() {
    val cases = listOf(
      Triple(bom(0xEF, 0xBB, 0xBF), Charsets.UTF_8, 3),
      Triple(bom(0xFF, 0xFE), Charsets.UTF_16LE, 2),
      Triple(bom(0xFE, 0xFF), Charsets.UTF_16BE, 2),
      Triple(bom(0xFF, 0xFE, 0, 0), Charset.forName("UTF-32LE"), 4),
      Triple(bom(0, 0, 0xFE, 0xFF), Charset.forName("UTF-32BE"), 4),
    )
    for ((mark, charset, length) in cases) {
      val document = read(mark + italian.toByteArray(charset))
      assertNotNull(charset.name(), document)
      document!!
      assertEquals(charset.name(), charset, document.charset.charset)
      assertEquals(charset.name(), length, document.charset.bomLength)
      assertTrue(charset.name(), document.charset.certain)
      assertEquals(charset.name(), listOf("Perché più già: «così» è città.", "Seconda riga, € 5."), document.lines)
      assertFalse(charset.name(), document.malformed)
    }
  }

  @Test
  fun `utf-16 without a bom is found from the zeros`() {
    for (charset in listOf(Charsets.UTF_16LE, Charsets.UTF_16BE)) {
      val document = read(italian.repeat(4).toByteArray(charset))!!
      assertEquals(charset, document.charset.charset)
      assertFalse(document.charset.certain)
      assertEquals("Perché più già: «così» è città.", document.lines.first())
    }
  }

  @Test
  fun `utf-32 without a bom is found from the zeros`() {
    val charset = Charset.forName("UTF-32LE")
    val document = read(italian.repeat(2).toByteArray(charset))!!
    assertEquals(charset, document.charset.charset)
  }

  @Test
  fun `valid utf-8 and plain ascii read as utf-8`() {
    assertEquals(Charsets.UTF_8, read(italian.toByteArray())!!.charset.charset)
    assertEquals(Charsets.UTF_8, read("solo ascii\n".toByteArray())!!.charset.charset)
  }

  @Test
  fun `legacy windows bytes fall back to windows-1252`() {
    val legacy = italian.toByteArray(Charset.forName("windows-1252"))
    val document = read(legacy)!!
    assertEquals(FluidTextDecoder.Fallback, document.charset.charset)
    assertEquals("Perché più già: «così» è città.", document.lines[0])
    assertEquals("Seconda riga, € 5.", document.lines[1])
    assertFalse(document.malformed)
  }

  @Test
  fun `a declared charset wins over the guess but not over the bom`() {
    val latin = "città".toByteArray(Charsets.ISO_8859_1)
    val declared = read(latin, mime = "text/plain; charset=ISO-8859-1")!!
    assertEquals(Charsets.ISO_8859_1, declared.charset.charset)
    assertTrue(declared.charset.certain)
    assertEquals(listOf("città"), declared.lines)
    val withBom = read(bom(0xEF, 0xBB, 0xBF) + "città".toByteArray(), mime = "text/plain; charset=ISO-8859-1")!!
    assertEquals(Charsets.UTF_8, withBom.charset.charset)
    assertEquals(listOf("città"), withBom.lines)
  }

  @Test
  fun `an unknown declared charset is ignored`() {
    val document = read("città".toByteArray(), mime = "text/plain; charset=x-inventato")!!
    assertEquals(Charsets.UTF_8, document.charset.charset)
    assertFalse(document.charset.certain)
  }

  @Test
  fun `a multibyte sequence cut by the sample end is not an error`() {
    val text = "à".repeat(FluidTextDecoder.SampleBytes) // 2 byte ciascuno: il campione taglia a metà di nessuno, poi
    val bytes = ("x" + text).toByteArray() // con «x» davanti il campione finisce a metà di una «à»
    assertEquals(Charsets.UTF_8, FluidTextDecoder.detect(bytes).charset)
  }

  // --- Malformato e binario --------------------------------------------------------------------------------------

  @Test
  fun `malformed bytes become replacement characters and are reported`() {
    val bytes = "ok ".toByteArray() + bom(0xC3, 0x28) + " fine".toByteArray()
    val document = read(bytes, mime = "text/plain; charset=utf-8")!!
    assertEquals(listOf("ok �( fine"), document.lines)
    assertTrue(document.malformed)
  }

  @Test
  fun `a replacement character written in the file is not an error`() {
    val document = read("segno � voluto".toByteArray())!!
    assertFalse(document.malformed)
  }

  @Test
  fun `binary bytes are not text`() {
    val png = bom(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 0x49, 0x48, 0x44, 0x52)
    assertNull(read(png + ByteArray(64) { (it * 7).toByte() }))
    assertNull(read("testo\u0000con un nul".toByteArray()))
    val controls = ByteArray(200) { if (it % 3 == 0) 0x01 else 0x41 }
    assertNull(read(controls))
  }

  @Test
  fun `tabs escapes and form feeds are text`() {
    val document = read("a\tb\u000C\u001B[1mgrassetto\u001B[0m\n".toByteArray())
    assertNotNull(document)
  }

  @Test
  fun `an empty file has no lines`() {
    val document = read(ByteArray(0))!!
    assertEquals(0, document.lineCount)
    assertFalse(document.truncated)
  }

  // --- Righe -----------------------------------------------------------------------------------------------------

  @Test
  fun `the three line endings and the final newline`() {
    assertEquals(listOf("a", "b", "c", "d"), read("a\nb\r\nc\rd".toByteArray())!!.lines)
    assertEquals(listOf("a", "b"), read("a\nb\n".toByteArray())!!.lines)
    assertEquals(listOf("a", "", "b"), read("a\n\nb".toByteArray())!!.lines)
    assertEquals(listOf("", ""), read("\n\n".toByteArray())!!.lines)
  }

  @Test
  fun `a very long line is split without breaking a surrogate pair`() {
    val emoji = "😀"
    val line = "x".repeat(9) + emoji + "y".repeat(5)
    val document = read(line.toByteArray(), maxLine = 10)!!
    assertEquals(2, document.lineCount)
    assertEquals("x".repeat(9), document.lines[0])
    assertEquals(emoji + "yyyyy", document.lines[1])
    assertEquals(1, document.splitLines)
    assertEquals(line, document.lines.joinToString(""))
  }

  // --- Il tetto --------------------------------------------------------------------------------------------------

  @Test
  fun `a file of exactly the limit is not truncated`() {
    val bytes = "x".repeat(1024).toByteArray()
    val document = read(bytes, limit = 1024)!!
    assertFalse(document.truncated)
    assertEquals(1024L, document.bytesRead)
  }

  @Test
  fun `a file over the limit is truncated on a whole character`() {
    val bytes = ("x".repeat(1023) + "è" + "fine").toByteArray() // «è» occupa i byte 1023 e 1024
    val document = read(bytes, limit = 1024)!!
    assertTrue(document.truncated)
    assertEquals(1023L, document.bytesRead)
    assertEquals("x".repeat(1023), document.lines.single())
    assertFalse(document.malformed)
  }

  @Test
  fun `the limit reads from a stream that never says its size`() {
    val endless = object : InputStream() {
      var served = 0L
      override fun read(): Int = if (served++ < 10_000_000) 'a'.code else -1
      override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = minOf(len.toLong(), 10_000_000 - served).toInt()
        if (n <= 0) return -1
        b.fill('a'.code.toByte(), off, off + n)
        served += n
        return n
      }
    }
    val document = FluidTextDecoder.read(endless, "registro.log", limitBytes = 64 * 1024)!!
    assertTrue(document.truncated)
    assertEquals(64L * 1024, document.bytesRead)
    assertNull(document.totalBytes)
  }

  @Test
  fun `utf-16 truncation keeps whole units and surrogate pairs`() {
    val text = "ab😀cd"
    val bytes = text.toByteArray(Charsets.UTF_16LE)
    // Tagliato dopo il primo mezzo dell'emoji (6 byte): resta «ab».
    assertEquals(4, FluidTextDecoder.wholeCharEnd(bytes, 0, 6, Charsets.UTF_16LE))
    // Tagliato a un byte dispari: si perde il mezzo carattere.
    assertEquals(2, FluidTextDecoder.wholeCharEnd(bytes, 0, 3, Charsets.UTF_16LE))
    assertEquals(8, FluidTextDecoder.wholeCharEnd(bytes, 0, 8, Charsets.UTF_16LE))
  }

  @Test
  fun `utf-8 truncation never cuts a sequence`() {
    val bytes = "a€".toByteArray() // € = E2 82 AC
    assertEquals(1, FluidTextDecoder.wholeCharEnd(bytes, 0, 2, Charsets.UTF_8))
    assertEquals(1, FluidTextDecoder.wholeCharEnd(bytes, 0, 3, Charsets.UTF_8))
    assertEquals(4, FluidTextDecoder.wholeCharEnd(bytes, 0, 4, Charsets.UTF_8))
  }

  // --- Il sorgente a pezzi ---------------------------------------------------------------------------------------

  @Test
  fun `lines are served in chunks and a short chunk marks the end`() = runBlocking {
    val document = read((1..250).joinToString("\n") { "riga $it" }.toByteArray())!!
    assertEquals(250, document.lineCount)
    assertEquals((1..100).map { "riga $it" }, document.readLines(0, 100))
    assertEquals((201..250).map { "riga $it" }, document.readLines(200, 100))
    assertEquals(emptyList<String>(), document.readLines(250, 100))
    assertEquals(emptyList<String>(), document.readLines(10, 0))
  }

  @Test
  fun `the language comes from the name then from the content`() {
    assertEquals(FluidSyntax.Kotlin, read("fun main() {}".toByteArray(), name = "Main.kt")!!.language)
    assertEquals(FluidSyntax.Json, read("{\"a\": 1}".toByteArray(), name = "risposta")!!.language)
    assertEquals(FluidSyntax.Xml, read("<?xml version=\"1.0\"?><a/>".toByteArray(), name = "dati")!!.language)
    assertNull(read("solo testo".toByteArray(), name = "nota.txt")!!.language)
  }

  @Test
  fun `syntax spans read in the middle of a file match a full read`() {
    val lines = buildList {
      repeat(40) { add("val x$it = \"testo\" // commento") }
      add("/* un commento")
      repeat(30) { add(" che continua $it") }
      add("*/ fun dopo() = 42")
      repeat(40) { add("class Tipo$it") }
    }
    val document = read(lines.joinToString("\n").toByteArray(), name = "Lungo.kt")!!
    val full = FluidSyntax.highlight(document.lines, FluidSyntax.Kotlin)
    // Prima un pezzo a metà del commento a blocco, poi l'inizio: lo stato deve essere quello giusto.
    assertEquals(full.subList(50, 80), document.syntaxSpans(50, 30))
    assertEquals(full.subList(0, 50), document.syntaxSpans(0, 50))
    assertEquals(full.subList(100, 112), document.syntaxSpans(100, 50))
    assertTrue(document.syntaxSpans(50, 1).single().single().role == FluidSyntaxRole.Comment)
  }
}
