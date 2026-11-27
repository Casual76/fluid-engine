package dev.antigravity.fluidengine.revisions

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** F8.21 · I passaggi diversi, il ripiego su due estratti, l'ora delle versioni e la scelta. */
class FluidVersionsTest {
  private val zone = ZoneId.of("Europe/Rome")

  private fun marked(excerpt: FluidVersionExcerpt): List<String> = excerpt.changed.map { excerpt.text.substring(it) }

  @Test fun `i passaggi diversi sono segnati in tutte e due le versioni`() {
    val mine = FluidVersion("a", "Questo telefono", 0, "Ritrovo alle 8 davanti a scuola.", current = true)
    val other = FluidVersion("b", "Tablet", 0, "Ritrovo alle 7:45 in stazione.")
    val excerpts = fluidVersionExcerpts(listOf(mine, other))
    assertEquals(listOf("a", "b"), excerpts.keys.toList())
    val a = excerpts.getValue("a")
    val b = excerpts.getValue("b")
    assertTrue(a.compared && b.compared)
    assertEquals("Ritrovo alle 8 davanti a scuola.", a.text)
    assertTrue("8" in marked(a).joinToString(" "))
    assertTrue("scuola" in marked(a).joinToString(" "))
    assertTrue("7:45" in marked(b).joinToString(""))
    assertTrue("stazione" in marked(b).joinToString(" "))
    assertFalse("le parole uguali non si segnano", marked(b).any { it.contains("Ritrovo") })
    // Tratti separati solo da spazi sono un passaggio solo, non tre macchie (visto sul tablet).
    assertEquals(listOf("7:45 in stazione"), marked(b))
  }

  @Test fun `un testo lungo si taglia attorno al primo passaggio diverso`() {
    val start = (1..80).joinToString(" ") { "parola$it" }
    val one = FluidVersion("a", "A", 0, "$start fine uno. E poi altro testo che continua a lungo dopo.", current = true)
    val two = FluidVersion("b", "B", 0, "$start fine due. E poi altro testo che continua a lungo dopo.")
    val b = fluidVersionExcerpts(listOf(one, two), maxChars = 120).getValue("b")
    assertTrue(b.clippedStart)
    assertTrue(b.text.length <= 120)
    assertEquals(listOf("due"), marked(b))
  }

  @Test fun `oltre il tetto restano due estratti senza segni`() {
    val one = FluidVersion("a", "A", 0, (1..300).joinToString(" ") { "a$it" }, current = true)
    val two = FluidVersion("b", "B", 0, (1..300).joinToString(" ") { "b$it" })
    val excerpts = fluidVersionExcerpts(listOf(one, two), maxEdits = 50)
    for (e in excerpts.values) {
      assertFalse(e.compared)
      assertTrue(e.changed.isEmpty())
      assertFalse(e.clippedStart)
    }
  }

  @Test fun `l'ora - oggi, ieri, un altro giorno, un altro anno`() {
    val today = LocalDate.of(2026, 11, 24)
    fun at(y: Int, m: Int, d: Int, h: Int, min: Int) = ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()
    val it = Locale.ITALY
    assertEquals("Oggi, 14:32", fluidVersionTime(at(2026, 11, 24, 14, 32), zone, it, today))
    assertEquals("Ieri, 9:05", fluidVersionTime(at(2026, 11, 23, 9, 5), zone, it, today))
    assertEquals("21 nov, 18:05", fluidVersionTime(at(2026, 11, 21, 18, 5), zone, it, today))
    assertEquals("3 mar 2025, 8:00", fluidVersionTime(at(2025, 3, 3, 8, 0), zone, it, today))
    assertEquals("Yesterday, 9:05", fluidVersionTime(at(2026, 11, 23, 9, 5), zone, Locale.UK, today, FluidVersionLabels.english()))
  }

  @Test fun `la scelta - una, tutte`() {
    assertFalse(FluidVersionDecision(listOf("a"), listOf("a", "b")).keepsAll)
    assertTrue(FluidVersionDecision(listOf("a", "b"), listOf("a", "b")).keepsAll)
  }

  /**
   * R2, il minore di F8.21: tre versioni, una oltre il tetto. La versione attuale non è più confrontata con tutte: è
   * l'inizio del testo senza segni, come dice [FluidVersionExcerpt.compared]; quella confrontata tiene i suoi.
   */
  @Test fun `tre versioni e una oltre il tetto - l'attuale e' l'inizio senza segni`() {
    val start = (1..80).joinToString(" ") { "parola$it" }
    val mine = FluidVersion("a", "A", 0, "$start fine uno.", current = true)
    val near = FluidVersion("b", "B", 0, "$start fine due.")
    val far = FluidVersion("c", "C", 0, (1..80).joinToString(" ") { "altro$it" })
    val excerpts = fluidVersionExcerpts(listOf(mine, near, far), maxChars = 120, maxEdits = 10)
    val a = excerpts.getValue("a")
    assertFalse(a.compared)
    assertTrue("l'attuale non porta i segni delle altre: ${a.changed}", a.changed.isEmpty())
    assertTrue("e' l'inizio del testo", a.text.startsWith("parola1 ") && !a.clippedStart)
    assertTrue(excerpts.getValue("b").compared)
    assertTrue(excerpts.getValue("b").changed.isNotEmpty())
    assertFalse(excerpts.getValue("c").compared)
  }

  /**
   * R2 #47: due testi enormi con molti passaggi diversi. La traduzione dei tratti in caratteri era quadratica (per ogni
   * tratto ripartiva dall'inizio del testo): con 800 000 pezzi e 2 000 tratti erano più di un miliardo e mezzo di passi.
   * Ora gli scarti si contano una volta sola: la stessa preparazione sta sotto il secondo anche su un PC carico.
   */
  @Test fun `due testi enormi con molti tratti diversi si preparano in tempo lineare`() {
    val words = 400_000
    val changes = 2_000
    val step = words / changes
    val base = StringBuilder()
    val other = StringBuilder()
    for (i in 0 until words) {
      if (i > 0) { base.append(' '); other.append(' ') }
      base.append("w").append(i)
      other.append(if (i % step == step / 2) "x$i" else "w$i")
    }
    val versions = listOf(
      FluidVersion("a", "A", 0, base.toString(), current = true),
      FluidVersion("b", "B", 0, other.toString()),
    )
    // Il primo giro scalda il JIT, il secondo si misura.
    fluidVersionExcerpts(versions, maxEdits = 2 * changes + 10)
    val started = System.nanoTime()
    val excerpts = fluidVersionExcerpts(versions, maxEdits = 2 * changes + 10)
    val ms = (System.nanoTime() - started) / 1_000_000
    println("R2 #47: $words parole, $changes tratti, $ms ms")
    assertTrue(excerpts.getValue("b").compared)
    assertEquals("x${step / 2}", excerpts.getValue("b").let { it.text.substring(it.changed.first()) })
    assertTrue("preparazione in $ms ms", ms < 1_200)
  }
}
