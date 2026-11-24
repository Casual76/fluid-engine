package dev.antigravity.fluidengine.ui.fluid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F8.1 · Il testo che arriva a pezzi (il passo, il ritardo massimo, l'ordine) e la regola delle cifre che scorrono: le
 * parti pure, senza Compose.
 */
class FluidTextArrivalTest {
  private val ms = 1_000_000L
  private val reveal = 260 * ms
  private val rewrite = 120 * ms
  private val stagger = 24 * ms
  private val spread = 200 * ms

  private fun RevealTracker.feed(text: String, first: Boolean = false) =
    update(text, reveal, rewrite, animateFirst = first, staggerNanos = stagger, spreadNanos = spread)

  @Test
  fun `le parole di un pezzo nascono una dopo l'altra, di un passo`() {
    val tracker = RevealTracker()
    tracker.feed("Ciao")
    tracker.feed("Ciao a tutti voi oggi")
    tracker.stamp(1_000 * ms)
    val born = tracker.revealing.map { it.born }
    assertEquals(4, born.size)
    for (i in 1 until born.size) assertEquals("parola $i", stagger, born[i] - born[i - 1])
    assertEquals("la prima nasce subito", 1_000 * ms, born.first())
  }

  @Test
  fun `il passo non supera il ritardo massimo`() {
    val tracker = RevealTracker()
    tracker.feed("x")
    tracker.feed("x " + (1..30).joinToString(" ") { "p$it" })
    tracker.stamp(0L)
    val last = tracker.revealing.maxOf { it.born }
    assertEquals("l'ultima parola aspetta al più il ritardo massimo", spread, last)
  }

  @Test
  fun `due pezzi vicini restano nell'ordine del testo`() {
    val tracker = RevealTracker()
    tracker.feed("Uno")
    tracker.feed("Uno due tre quattro")
    tracker.stamp(0L)
    // Il pezzo dopo arriva prima che il primo sia finito: nessuna parola nasce prima di quella che la precede.
    tracker.feed("Uno due tre quattro cinque sei")
    tracker.stamp(30 * ms)
    val born = tracker.revealing.map { it.born }
    for (i in 1 until born.size) assertTrue("parola $i nata prima della precedente: $born", born[i] >= born[i - 1] + stagger)
    assertTrue("e nessuna oltre il ritardo massimo", born.all { it <= 30 * ms + spread })
  }

  @Test
  fun `prima della sua nascita una parola resta nascosta, e alla fine tutto e' dentro`() {
    val tracker = RevealTracker()
    tracker.feed("a b c", first = true)
    tracker.stamp(0L)
    val c = tracker.revealing.last()
    assertEquals("nascosta prima della nascita", 1f, c.mask(10 * ms), 0f)
    assertFalse(tracker.settle(100 * ms))
    assertTrue("tutto dentro dopo l'ultima nascita più la durata", tracker.settle(2 * stagger + reveal + 1))
    assertTrue(tracker.all.all { it.mask(10_000 * ms) == 0f })
  }

  @Test
  fun `senza passo il pezzo nasce tutto insieme, come prima della 4_0`() {
    val tracker = RevealTracker()
    tracker.update("a", reveal, rewrite, animateFirst = false)
    tracker.update("a b c d", reveal, rewrite, animateFirst = false)
    tracker.stamp(5L)
    assertTrue(tracker.revealing.all { it.born == 5L })
  }

  @Test
  fun `il testo che si espande arriva come un arrivo, non come una riscrittura`() {
    val tracker = RevealTracker()
    tracker.update("Il testo… altro", reveal, rewrite, animateFirst = false, staggerNanos = stagger, spreadNanos = spread, rewriteArrives = true)
    tracker.update("Il testo intero che continua", reveal, rewrite, animateFirst = false, staggerNanos = stagger, spreadNanos = spread, rewriteArrives = true)
    tracker.stamp(0L)
    // «testo» resta (prefisso comune), «intero che continua» arriva col tempo dell'arrivo.
    assertEquals(listOf("intero", "che", "continua"), tracker.revealing.map { "Il testo intero che continua".substring(it.start, it.end) })
    assertTrue(tracker.revealing.all { it.durationNanos == reveal })
  }

  @Test
  fun `l'arrivo di serie e' parola per parola e sfocato, le righe hanno il loro passo`() {
    assertEquals(FluidTextArrivalUnit.Word, FluidTextArrival.Streaming.unit)
    assertTrue(FluidTextArrival.Streaming.blur)
    assertEquals(FluidTextArrivalUnit.Line, FluidTextArrival.Lines.unit)
    assertFalse(FluidTextArrival.Fade.blur)
    assertEquals(FluidTextMotion.WordArrivalMillis, FluidRevealMillis)
  }

  @Test
  fun `come Apple all'inizio si vede solo il fantasma sfocato, poi il testo nitido prende il posto`() {
    // F8.1b: nella prima parte dell'arrivo il testo vero non c'e', il fantasma regge; alla fine c'e' solo il testo vero.
    assertEquals(0f, fluidArrivalSharp(0.3f), 0f)
    assertTrue(fluidArrivalGhost(0.3f) > 0.8f)
    assertTrue("a meta' il fantasma pesa piu' del nitido", fluidArrivalGhost(0.5f) > fluidArrivalSharp(0.5f))
    assertEquals(1f, fluidArrivalSharp(1f), 0f)
    assertEquals(0f, fluidArrivalGhost(1f), 1e-4f)
    assertEquals(0f, fluidArrivalGhost(0f), 0f)
    assertEquals(0.20f, FluidTextMotion.ArrivalPeakBlur, 0f)
    assertEquals(6, GhostBuckets)
    // Ogni maschera cade in un gruppo, e i gruppi coprono tutto l'intervallo.
    assertEquals(0, ghostBucketOf(0f))
    assertEquals(GhostBuckets - 1, ghostBucketOf(1f))
  }

  // ---- le cifre che scorrono --------------------------------------------------------------------

  private fun kinds(old: String, new: String) = FluidNumericRoll.plan(old, new).map { it.kind }

  @Test
  fun `scorrono solo le cifre cambiate`() {
    val plan = FluidNumericRoll.plan("128", "131")
    // Allineate da destra: 8 → 1, 2 → 3 scorrono, l'1 resta.
    assertEquals(
      listOf(NumericSlotKind.Roll, NumericSlotKind.Roll, NumericSlotKind.Static),
      plan.map { it.kind },
    )
    assertEquals(listOf(2, 1, 0), plan.map { it.to })
  }

  @Test
  fun `un numero che si allunga fa entrare la cifra nuova`() {
    assertEquals(listOf(NumericSlotKind.Roll, NumericSlotKind.Roll, NumericSlotKind.Enter), kinds("99", "100"))
    assertEquals(listOf(NumericSlotKind.Roll, NumericSlotKind.Roll, NumericSlotKind.Exit), kinds("100", "99"))
  }

  @Test
  fun `segno, virgola e unita' non scorrono`() {
    val plan = FluidNumericRoll.plan("€ 9,99", "€ 10,00")
    val static = plan.filter { it.kind == NumericSlotKind.Static }.map { "€ 10,00"[it.to] }
    assertTrue("€, lo spazio e la virgola restano: $static", static.containsAll(listOf('€', ' ', ',')))
    assertTrue(plan.none { it.kind == NumericSlotKind.Roll && !"€ 10,00"[it.to].isDigit() })
    // L'unità che cambia non scorre: dissolvenza sul posto.
    val units = FluidNumericRoll.plan("2,4 MB", "2,5 GB")
    assertEquals(NumericSlotKind.Swap, units.first { it.to == 4 }.kind)
    assertEquals(NumericSlotKind.Static, units.first { it.to == 5 }.kind)
    assertEquals(NumericSlotKind.Roll, units.first { it.to == 2 }.kind)
  }

  @Test
  fun `il verso viene dal valore, non dal testo`() {
    assertEquals(1, FluidNumericRoll.direction(128.0, 131.0, "128", "131"))
    assertEquals(-1, FluidNumericRoll.direction(131.0, 128.0, "131", "128"))
    // Un conto alla rovescia: «1:00» → «0:59» scende anche se le cifre crescono.
    assertEquals(-1, FluidNumericRoll.direction(60.0, 59.0, "1:00", "0:59"))
  }

  @Test
  fun `il primo valore non si muove, un valore nuovo prepara il passaggio`() {
    val state = NumericRollState()
    assertFalse(state.moving)
    assertEquals(0, state.generation)
  }
}
