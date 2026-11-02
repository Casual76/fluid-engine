package dev.antigravity.fluidengine.ui.haptics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Un motore finto: ricorda cosa gli e' arrivato, nell'ordine. */
private class RecordingHaptics(override val engine: FluidHapticEngine = FluidHapticEngine.Composition) : FluidHaptics {
  val calls = mutableListOf<Any>()

  override fun play(event: FluidHapticEvent) {
    calls += event
  }

  override fun grain(intensity: Float, style: FluidHapticGrainStyle) {
    calls += Triple("grain", intensity, style)
  }
}

class FluidHapticTraceTest {

  /** Un orologio che si muove solo quando glielo dici. */
  private class Clock(var now: Long = 1_000L) : () -> Long {
    override fun invoke(): Long = now
    fun advance(millis: Long) {
      now += millis
    }
  }

  private val clock = Clock()
  private val trace = FluidHapticTrace(capacity = 16, clock = clock)
  private val real = RecordingHaptics()
  private val traced = trace.wrap(real)

  // --- Il decoratore non cambia niente --------------------------------------------------------------

  @Test
  fun `inoltra ogni chiamata al motore vero, tale e quale e nello stesso ordine`() {
    traced.play(FluidHapticEvent.PressDown)
    traced.grain(0.4f, FluidHapticGrainStyle.Fluid)
    traced.play(FluidHapticEvent.Open)
    traced.grain(1f, FluidHapticGrainStyle.LibrePods)

    assertEquals(
      listOf<Any>(
        FluidHapticEvent.PressDown,
        Triple("grain", 0.4f, FluidHapticGrainStyle.Fluid),
        FluidHapticEvent.Open,
        Triple("grain", 1f, FluidHapticGrainStyle.LibrePods),
      ),
      real.calls,
    )
  }

  @Test
  fun `il motore e' quello di sotto, anche se cambia`() {
    assertEquals(FluidHapticEngine.Composition, traced.engine)
    assertEquals(FluidHapticEngine.Off, trace.wrap(RecordingHaptics(FluidHapticEngine.Off)).engine)
  }

  @Test
  fun `registra gli eventi in ordine, con l'istante di ciascuno`() {
    traced.play(FluidHapticEvent.PressDown)
    clock.advance(60)
    traced.play(FluidHapticEvent.PressRelease)
    clock.advance(2)
    traced.play(FluidHapticEvent.PressClick)

    val entries = trace.entries
    assertEquals(
      listOf(FluidHapticEvent.PressDown, FluidHapticEvent.PressRelease, FluidHapticEvent.PressClick),
      entries.map { it.event },
    )
    assertEquals(listOf(1_000L, 1_060L, 1_062L), entries.map { it.atMillis })
  }

  @Test
  fun `un granello porta intensita' e stile`() {
    traced.grain(0.31f, FluidHapticGrainStyle.Fluid)

    val entry = trace.entries.single()
    assertTrue(entry.isGrain)
    assertNull(entry.event)
    assertEquals(0.31f, entry.grainIntensity, 0f)
    assertEquals(FluidHapticGrainStyle.Fluid, entry.grainStyle)
  }

  @Test
  fun `wrap e' idempotente per lo stesso registro, e non per un altro`() {
    assertSame(traced, trace.wrap(traced))
    val other = FluidHapticTrace(clock = clock)
    val rewrapped = other.wrap(traced)
    assertTrue(rewrapped is TracingFluidHaptics)
    assertSame(other, (rewrapped as TracingFluidHaptics).trace)
    assertSame(traced, rewrapped.delegate)
  }

  // --- La lista e' limitata e si puo' fermare -------------------------------------------------------

  @Test
  fun `tiene solo le ultime voci`() {
    repeat(40) {
      clock.advance(100)
      traced.play(FluidHapticEvent.Tap)
    }

    assertEquals(16, trace.entries.size)
    // Le piu' vecchie escono: restano le ultime sedici, la piu' nuova in fondo.
    assertEquals(1_000L + 100L * 40, trace.entries.last().atMillis)
    assertEquals(1_000L + 100L * 25, trace.entries.first().atMillis)
  }

  @Test
  fun `fermo non scrive ma il motore vero suona lo stesso`() {
    trace.recording = false
    traced.play(FluidHapticEvent.Open)
    assertTrue(trace.entries.isEmpty())
    assertEquals(listOf<Any>(FluidHapticEvent.Open), real.calls)

    trace.recording = true
    traced.play(FluidHapticEvent.Close)
    assertEquals(listOf<FluidHapticEvent?>(FluidHapticEvent.Close), trace.entries.map { it.event })
  }

  @Test
  fun `svuotare toglie le voci e basta`() {
    traced.play(FluidHapticEvent.Open)
    trace.clear()
    assertTrue(trace.entries.isEmpty())
    traced.play(FluidHapticEvent.Close)
    assertEquals(1, trace.entries.size)
  }

  // --- L'esito stimato: le due reti del motore ------------------------------------------------------

  @Test
  fun `due eventi di controllo nello stesso momento sono un colpo solo, il secondo e' fuso`() {
    traced.play(FluidHapticEvent.Tap)
    clock.advance(3)
    traced.play(FluidHapticEvent.Open)

    assertEquals(
      listOf(FluidHapticTraceOutcome.Played, FluidHapticTraceOutcome.Merged),
      trace.entries.map { it.outcome },
    )
  }

  @Test
  fun `a 70 ms dal precedente e' un momento nuovo e suona`() {
    traced.play(FluidHapticEvent.Tap)
    clock.advance(FluidHapticPatterns.CoalesceWindowMillis - 1)
    traced.play(FluidHapticEvent.Open)
    clock.advance(FluidHapticPatterns.CoalesceWindowMillis)
    traced.play(FluidHapticEvent.Close)

    assertEquals(
      listOf(FluidHapticTraceOutcome.Played, FluidHapticTraceOutcome.Merged, FluidHapticTraceOutcome.Played),
      trace.entries.map { it.outcome },
    )
  }

  @Test
  fun `un colpo fuso non rinnova la finestra, conta il momento di quello che e' suonato`() {
    traced.play(FluidHapticEvent.Tap) // suona a 0
    clock.advance(50)
    traced.play(FluidHapticEvent.Open) // fuso, a 50
    clock.advance(50)
    traced.play(FluidHapticEvent.Close) // a 100 dal suonato: nuovo momento

    assertEquals(
      listOf(FluidHapticTraceOutcome.Played, FluidHapticTraceOutcome.Merged, FluidHapticTraceOutcome.Played),
      trace.entries.map { it.outcome },
    )
  }

  @Test
  fun `i tre colpi di un tasto che cresce suonano sempre, anche nello stesso istante`() {
    traced.play(FluidHapticEvent.PressDown)
    traced.play(FluidHapticEvent.PressRelease)
    traced.play(FluidHapticEvent.PressClick)

    assertTrue(trace.entries.all { it.outcome == FluidHapticTraceOutcome.Played })
  }

  @Test
  fun `gli esiti non sono fra quelli che si fondono`() {
    traced.play(FluidHapticEvent.Tap)
    clock.advance(1)
    traced.play(FluidHapticEvent.Success)
    clock.advance(1)
    traced.play(FluidHapticEvent.Error)

    assertEquals(
      listOf(FluidHapticTraceOutcome.Played, FluidHapticTraceOutcome.Played, FluidHapticTraceOutcome.Played),
      trace.entries.map { it.outcome },
    )
  }

  @Test
  fun `un tick continuo piu' vicino di 40 ms al precedente e' scartato`() {
    traced.play(FluidHapticEvent.DragTick)
    clock.advance(FluidHapticsImpl.CONTINUOUS_MIN_GAP_MILLIS - 1)
    traced.play(FluidHapticEvent.DragTick)
    clock.advance(FluidHapticsImpl.CONTINUOUS_MIN_GAP_MILLIS)
    traced.play(FluidHapticEvent.DragTick)

    assertEquals(
      listOf(FluidHapticTraceOutcome.Played, FluidHapticTraceOutcome.Dropped, FluidHapticTraceOutcome.Played),
      trace.entries.map { it.outcome },
    )
  }

  @Test
  fun `la grana Fluid ha il pavimento di 8 ms, quella di LibrePods no`() {
    traced.grain(0.3f, FluidHapticGrainStyle.Fluid)
    clock.advance(FluidHapticsImpl.GRAIN_MIN_GAP_MILLIS - 1)
    traced.grain(0.3f, FluidHapticGrainStyle.Fluid)
    traced.grain(1f, FluidHapticGrainStyle.LibrePods)
    clock.advance(0)
    traced.grain(1f, FluidHapticGrainStyle.LibrePods)

    assertEquals(
      listOf(
        FluidHapticTraceOutcome.Played,
        FluidHapticTraceOutcome.Dropped,
        FluidHapticTraceOutcome.Played,
        FluidHapticTraceOutcome.Played,
      ),
      trace.entries.map { it.outcome },
    )
  }

  // --- Le righe -------------------------------------------------------------------------------------

  @Test
  fun `le righe sono le piu' nuove per prime, con i ritardi dalla riga prima`() {
    traced.play(FluidHapticEvent.PressDown)
    clock.advance(60)
    traced.play(FluidHapticEvent.PressRelease)
    clock.advance(2)
    traced.play(FluidHapticEvent.PressClick)

    val rows = trace.rows()
    assertEquals(listOf("PressClick", "PressRelease", "PressDown"), rows.map { it.label })
    assertEquals(listOf<Long?>(2L, 60L, null), rows.map { it.deltaMillis })
    // Tutti e tre sotto i 70 ms l'uno dall'altro (2 e 60): un momento solo, il primo lo apre.
    assertEquals(listOf(false, false, true), rows.map { it.startsMoment })
  }

  @Test
  fun `i granelli consecutivi dello stesso stile sono una riga sola`() {
    traced.play(FluidHapticEvent.PressDown)
    clock.advance(200)
    repeat(14) {
      clock.advance(10)
      traced.grain(0.2f + it * 0.02f, FluidHapticGrainStyle.Fluid)
    }
    clock.advance(300)
    traced.play(FluidHapticEvent.PressRelease)

    val rows = trace.rows()
    assertEquals(listOf("PressRelease", "grana Fluid", "PressDown"), rows.map { it.label })
    val grain = rows[1]
    assertEquals(14, grain.count)
    assertEquals("grana Fluid ×14", grain.text)
    // Dal PressDown al primo granello: 200 + 10 ms. Dall'ultimo granello al rilascio: 300 ms.
    assertEquals(210L, grain.deltaMillis)
    assertEquals(300L, rows[0].deltaMillis)
    assertTrue(grain.detail!!.startsWith("14 in 130 ms"))
    assertTrue(grain.detail!!.contains("forza 20-46%"))
  }

  @Test
  fun `una pausa lunga spezza la fila, e uno stile diverso pure`() {
    traced.grain(1f, FluidHapticGrainStyle.LibrePods)
    clock.advance(10)
    traced.grain(1f, FluidHapticGrainStyle.LibrePods)
    clock.advance(FluidHapticTraceGrainRunGapMillis + 1)
    traced.grain(1f, FluidHapticGrainStyle.LibrePods)
    clock.advance(10)
    traced.grain(0.3f, FluidHapticGrainStyle.Fluid)

    val rows = trace.rows()
    assertEquals(listOf("grana Fluid", "grana LibrePods", "grana LibrePods"), rows.map { it.label })
    assertEquals(listOf(1, 1, 2), rows.map { it.count })
    // LibrePods non ha una forza da dire: niente "forza" nella nota.
    assertFalse(rows[2].detail!!.contains("forza"))
  }

  @Test
  fun `una fila dove qualcuno e' scartato suona comunque, e dice quanti`() {
    traced.grain(0.3f, FluidHapticGrainStyle.Fluid)
    clock.advance(1)
    traced.grain(0.3f, FluidHapticGrainStyle.Fluid)

    val row = trace.rows().single()
    assertEquals(2, row.count)
    assertEquals(FluidHapticTraceOutcome.Played, row.outcome)
    assertTrue(row.detail!!.contains("1 scartati"))
  }

  @Test
  fun `una riga di soli granelli scartati e' una riga scartata`() {
    traced.grain(0.3f, FluidHapticGrainStyle.Fluid) // suona
    clock.advance(1)
    traced.grain(1f, FluidHapticGrainStyle.LibrePods) // altra fila
    clock.advance(1)
    traced.grain(0.3f, FluidHapticGrainStyle.Fluid) // a 2 ms dall'ultimo suonato: scartato

    val rows = trace.rows()
    assertEquals(listOf("grana Fluid", "grana LibrePods", "grana Fluid"), rows.map { it.label })
    assertEquals(
      listOf(FluidHapticTraceOutcome.Dropped, FluidHapticTraceOutcome.Played, FluidHapticTraceOutcome.Played),
      rows.map { it.outcome },
    )
  }

  @Test
  fun `un evento fuso lo dice nella nota`() {
    traced.play(FluidHapticEvent.Tap)
    traced.play(FluidHapticEvent.Open)

    val rows = trace.rows()
    assertEquals(FluidHapticTraceOutcome.Merged, rows[0].outcome)
    assertTrue(rows[0].detail!!.startsWith("fuso"))
    assertNull(rows[1].detail)
  }

  @Test
  fun `maxRows taglia le piu' vecchie, ma i ritardi restano veri`() {
    traced.play(FluidHapticEvent.Open)
    clock.advance(500)
    traced.play(FluidHapticEvent.Close)
    clock.advance(500)
    traced.play(FluidHapticEvent.Tap)

    val rows = trace.rows(maxRows = 2)
    assertEquals(listOf("Tap", "Close"), rows.map { it.label })
    // La riga piu' vecchia che si vede dista comunque 500 ms da quella che e' uscita.
    assertEquals(500L, rows[1].deltaMillis)
  }

  @Test
  fun `senza voci non ci sono righe`() {
    assertTrue(trace.rows().isEmpty())
    assertTrue(fluidHapticTraceRows(emptyList(), 0).isEmpty())
  }
}
