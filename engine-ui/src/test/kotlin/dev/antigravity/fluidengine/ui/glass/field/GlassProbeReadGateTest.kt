package dev.antigravity.fluidengine.ui.glass.field

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F7.25 · La regola dei fotogrammi saltati della sonda (`GlassProbeTiming.kt`): col RenderThread libero si legge
 * subito; occupato si salta senza nessuna attesa per [GlassProbeFreeSkipFrames] fotogrammi, poi si aspetta il ping per
 * poco una volta sola (F7.25c), e al tetto si legge comunque. Il tetto è un tempo, [GlassProbeCeilingNanos] = 250 ms
 * (F7.25d, la scelta di Alessio del 26 novembre 2026): 30 fotogrammi a 120 Hz, 15 a 60 Hz. Mai la lettura «come
 * prima», che col RenderThread indietro fermava il thread principale 4,5–5,7 ms.
 */
class GlassProbeReadGateTest {

  /** Un fotogramma a [hz]: il tempo del fotogramma [index] (da 1) dall'inizio dell'attesa, in nanosecondi. */
  private fun frameAt(index: Int, hz: Int): Long = index * 1_000_000_000L / hz

  private fun verdicts(gate: GlassProbeReadGate, busyFrames: Int, hz: Int = 120, startNanos: Long = 0L): List<GlassProbeGateVerdict> {
    gate.begin(startNanos)
    return List(busyFrames) { index ->
      gate.onFrame(startNanos + frameAt(index + 1, hz))
      gate.verdict(free = false)
    }
  }

  /** Quanti fotogrammi a [hz] fanno il tetto: il primo il cui tempo arriva a [GlassProbeCeilingNanos]. */
  private fun ceilingFrame(hz: Int): Int = (1..10_000).first { frameAt(it, hz) >= GlassProbeCeilingNanos }

  private fun expected(frames: Int, hz: Int): List<GlassProbeGateVerdict> = List(frames) { index ->
    val frame = index + 1
    when {
      frame <= GlassProbeFreeSkipFrames -> GlassProbeGateVerdict.Skip
      frame == GlassProbeFreeSkipFrames + 1 -> GlassProbeGateVerdict.WaitBriefly
      frame < ceilingFrame(hz) -> GlassProbeGateVerdict.Skip
      else -> GlassProbeGateVerdict.ReadAnyway
    }
  }

  @Test
  fun `col RenderThread libero si legge al primo fotogramma, e a qualunque fotogramma`() {
    val gate = GlassProbeReadGate()
    gate.begin(0L)
    gate.onFrame(frameAt(1, 120))
    assertEquals(GlassProbeGateVerdict.ReadNow, gate.verdict(free = true))
    repeat(40) { gate.onFrame(frameAt(it + 2, 120)) }
    assertEquals(GlassProbeGateVerdict.ReadNow, gate.verdict(free = true))
  }

  @Test
  fun `il tetto e' 250 ms, 30 fotogrammi a 120 Hz`() {
    // F7.25c: un'attesa breve sola per lettura (sul tablet quella ripetuta scadeva 5 volte su 6). F7.25d: il tetto è
    // un tempo; prima era 15 fotogrammi, 125 ms a 120 Hz.
    assertEquals(30, ceilingFrame(120))
    val got = verdicts(GlassProbeReadGate(), 33, hz = 120)
    assertEquals(expected(33, 120), got)
    assertEquals("prima lettura al tetto", 30, got.indexOf(GlassProbeGateVerdict.ReadAnyway) + 1)
    assertEquals("attese brevi per lettura", 1, got.count { it == GlassProbeGateVerdict.WaitBriefly })
  }

  @Test
  fun `il tetto e' 250 ms anche a 60 Hz, cioe' 15 fotogrammi`() {
    assertEquals(15, ceilingFrame(60))
    val got = verdicts(GlassProbeReadGate(), 18, hz = 60)
    assertEquals(expected(18, 60), got)
    assertEquals("prima lettura al tetto", 15, got.indexOf(GlassProbeGateVerdict.ReadAnyway) + 1)
  }

  @Test
  fun `il tetto conta il tempo anche se si saltano fotogrammi`() {
    // Un fotogramma lungo (il principale fermo 200 ms) conta per il suo tempo, non per uno.
    val gate = GlassProbeReadGate()
    gate.begin(1_000_000_000L)
    gate.onFrame(1_000_000_000L + 8_333_333L)
    assertEquals(GlassProbeGateVerdict.Skip, gate.verdict(free = false))
    gate.onFrame(1_000_000_000L + 251_000_000L)
    assertEquals(GlassProbeGateVerdict.ReadAnyway, gate.verdict(free = false))
    assertEquals(2, gate.frames)
    assertEquals(251_000_000L, gate.waitedNanos)
  }

  @Test
  fun `ogni lettura nuova ha di nuovo la sua attesa breve e il suo tempo`() {
    val gate = GlassProbeReadGate()
    val first = verdicts(gate, 30, startNanos = 5_000_000_000L)
    val second = verdicts(gate, 30, startNanos = 9_000_000_000L)
    assertEquals(1, first.count { it == GlassProbeGateVerdict.WaitBriefly })
    assertEquals(first, second)
  }

  @Test
  fun `col RenderThread libero dopo l'attesa breve si legge subito`() {
    val gate = GlassProbeReadGate()
    verdicts(gate, GlassProbeFreeSkipFrames + 3)
    gate.onFrame(frameAt(GlassProbeFreeSkipFrames + 4, 120))
    assertEquals(GlassProbeGateVerdict.ReadNow, gate.verdict(free = true))
  }

  @Test
  fun `nei fotogrammi liberi il principale non aspetta mai`() {
    val got = verdicts(GlassProbeReadGate(), GlassProbeFreeSkipFrames)
    assertTrue("$got", got.all { it == GlassProbeGateVerdict.Skip })
  }

  @Test
  fun `una lettura nuova riparte da zero`() {
    val gate = GlassProbeReadGate()
    verdicts(gate, 30)
    gate.begin(10_000_000_000L)
    assertEquals(0, gate.frames)
    assertEquals(0L, gate.waitedNanos)
    gate.onFrame(10_000_000_000L + frameAt(1, 120))
    assertEquals(GlassProbeGateVerdict.Skip, gate.verdict(free = false))
  }

  @Test
  fun `il tetto e' la scelta di Alessio, 250 ms`() {
    // 26 novembre 2026, domanda del rapporto di F7.25, risposta «B»: meno letture al tetto nello scorrimento pesante,
    // e il vetro che può girare fino a un quarto di secondo dopo il contenuto.
    assertEquals(250_000_000L, GlassProbeCeilingNanos)
    assertTrue("attesa breve ${GlassProbeBriefWaitNanos} ns", GlassProbeBriefWaitNanos <= 1_000_000L)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `i fotogrammi liberi non sono meno di zero`() {
    GlassProbeReadGate(freeFrames = -1)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `il tetto e' piu' di zero`() {
    GlassProbeReadGate(ceilingNanos = 0L)
  }
}
