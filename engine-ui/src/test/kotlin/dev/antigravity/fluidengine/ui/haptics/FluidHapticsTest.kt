package dev.antigravity.fluidengine.ui.haptics

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakePort(
  override val hasVibrator: Boolean = true,
  private val primitives: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7, 8),
  override var systemHapticsEnabled: Boolean = true,
  override var powerSave: Boolean = false,
) : HapticPort {
  val compositions = mutableListOf<List<HapticPrimitiveStep>>()
  val platforms = mutableListOf<HapticFeedbackType>()
  val attentions = mutableListOf<Boolean>()

  override fun supportedPrimitives(ids: IntArray): Set<Int> = ids.filter { it in primitives }.toSet()

  /** Falso come un vibratore senza permesso: il motore deve ripiegare sulla costante. */
  var vibrateSucceeds: Boolean = true

  override fun vibrate(steps: List<HapticPrimitiveStep>, attention: Boolean): Boolean {
    if (!vibrateSucceeds) return false
    compositions += steps
    attentions += attention
    return true
  }

  override fun platform(type: HapticFeedbackType) {
    platforms += type
  }
}

class FluidHapticsTest {

  @Test
  fun `ogni evento ha una composizione, un ripiego e scale in 0-1`() {
    FluidHapticEvent.entries.forEach { event ->
      val steps = FluidHapticPatterns.composition(event)
      assertTrue("composizione vuota per $event", steps.isNotEmpty())
      steps.forEach { assertTrue("scala fuori range per $event", it.scale in 0f..1f) }
      FluidHapticPatterns.fallback(event)
    }
  }

  @Test
  fun `i controlli restano secchi e nessun pattern supera i 400 ms`() {
    FluidHapticEvent.entries.forEach { event ->
      val duration = FluidHapticPatterns.durationMillis(event)
      assertTrue("$event dura $duration ms", duration <= 400)
      if (event in FluidHapticPatterns.controlEvents) assertTrue("controllo lungo: $event ($duration ms)", duration <= 120)
    }
  }

  @Test
  fun `con tutte le primitive si compone, altrimenti si ripiega evento per evento`() {
    val full = FakePort()
    val haptics = FluidHapticsImpl(full, enabled = { true })
    assertEquals(FluidHapticEngine.Composition, haptics.engine)
    haptics.play(FluidHapticEvent.Confirm)
    assertEquals(1, full.compositions.size)
    assertTrue(full.platforms.isEmpty())

    // Senza THUD: Error e AlertAlarm ripiegano, Tap resta in composizione.
    val partial = FakePort(primitives = setOf(1, 4, 6, 7, 8))
    val mixed = FluidHapticsImpl(partial, enabled = { true })
    assertEquals(FluidHapticEngine.Platform, mixed.engine)
    mixed.play(FluidHapticEvent.Error)
    mixed.play(FluidHapticEvent.Tap)
    assertEquals(listOf(HapticFeedbackType.Reject), partial.platforms)
    assertEquals(1, partial.compositions.size)
  }

  @Test
  fun `l'interruttore dell'app zittisce tutto`() {
    val port = FakePort()
    var enabled = false
    val haptics = FluidHapticsImpl(port, enabled = { enabled })
    haptics.play(FluidHapticEvent.Tap)
    assertTrue(port.compositions.isEmpty() && port.platforms.isEmpty())
    enabled = true
    haptics.play(FluidHapticEvent.Tap)
    assertEquals(1, port.compositions.size)
  }

  @Test
  fun `l'impostazione tattile di sistema non zittisce piu' niente da sola`() {
    // Su One UI resta a zero mentre il telefono vibra: darle retta significava non vibrare mai.
    val port = FakePort(systemHapticsEnabled = false)
    val haptics = FluidHapticsImpl(port, enabled = { true })
    haptics.play(FluidHapticEvent.Tap)
    assertEquals(1, port.compositions.size)
  }

  @Test
  fun `se la composizione non parte resta la costante di piattaforma`() {
    val port = FakePort()
    port.vibrateSucceeds = false
    val haptics = FluidHapticsImpl(port, enabled = { true })
    haptics.play(FluidHapticEvent.Tap)
    assertTrue(port.compositions.isEmpty())
    assertEquals(listOf(HapticFeedbackType.VirtualKey), port.platforms)
  }

  @Test
  fun `in risparmio energetico restano i controlli e spariscono i tick continui`() {
    val port = FakePort(powerSave = true)
    val haptics = FluidHapticsImpl(port, enabled = { true })
    haptics.play(FluidHapticEvent.Tick)
    haptics.play(FluidHapticEvent.Tap)
    assertEquals(1, port.compositions.size)
    assertEquals(FluidHapticPatterns.composition(FluidHapticEvent.Tap), port.compositions.single())
  }

  @Test
  fun `i tick continui si diradano a 40 ms, i tap a distanza di un momento passano tutti`() {
    var now = 0L
    val port = FakePort()
    val haptics = FluidHapticsImpl(port, enabled = { true }, clock = { now })
    repeat(5) { haptics.play(FluidHapticEvent.Tick); now += 10 }
    assertEquals(2, port.compositions.size)
    // Tre tocchi veri, ognuno il suo momento: passano tutti.
    repeat(3) { haptics.play(FluidHapticEvent.Tap); now += FluidHapticPatterns.CoalesceWindowMillis }
    assertEquals(5, port.compositions.size)
  }

  @Test
  fun `due eventi di controllo nello stesso momento suonano una volta sola`() {
    var now = 1_000L
    val port = FakePort()
    val haptics = FluidHapticsImpl(port, enabled = { true }, clock = { now })
    // Il tocco sul tasto e il pannello che si apre un fotogramma dopo: era il "toc-toc" dei menu.
    haptics.play(FluidHapticEvent.Tap)
    now += 16
    haptics.play(FluidHapticEvent.Open)
    now += 16
    haptics.play(FluidHapticEvent.GestureStart)
    assertEquals(listOf(FluidHapticPatterns.composition(FluidHapticEvent.Tap)), port.compositions)
    // Passata la finestra, il momento dopo suona.
    now += FluidHapticPatterns.CoalesceWindowMillis
    haptics.play(FluidHapticEvent.Open)
    assertEquals(2, port.compositions.size)
  }

  @Test
  fun `esiti e allerte non si scartano mai e i tick non armano la rete`() {
    var now = 1_000L
    val port = FakePort()
    val haptics = FluidHapticsImpl(port, enabled = { true }, clock = { now })
    haptics.play(FluidHapticEvent.Tap)
    haptics.play(FluidHapticEvent.Success)
    haptics.play(FluidHapticEvent.AlertAlarm)
    assertEquals(3, port.compositions.size)
    now += 200
    haptics.play(FluidHapticEvent.Tick)
    haptics.play(FluidHapticEvent.Tap)
    assertEquals(5, port.compositions.size)
  }

  @Test
  fun `la rete dei 70 ms e' una regola pura`() {
    assertTrue(fluidHapticCoalesces(previousAt = 100, now = 100))
    assertTrue(fluidHapticCoalesces(previousAt = 100, now = 169))
    assertTrue(!fluidHapticCoalesces(previousAt = 100, now = 170))
    assertTrue(!fluidHapticCoalesces(previousAt = Long.MIN_VALUE / 2, now = 0))
    assertTrue(FluidHapticEvent.Success !in FluidHapticPatterns.coalescible)
    assertTrue(FluidHapticEvent.Tick !in FluidHapticPatterns.coalescible)
  }

  @Test
  fun `Open e' un colpo solo`() {
    val open = FluidHapticPatterns.composition(FluidHapticEvent.Open).single()
    assertEquals(HapticPrimitive.CLICK, open.id)
  }

  @Test
  fun `il granello e' un TICK dosato, col suo pavimento, muto in risparmio energetico`() {
    var now = 1_000L
    val port = FakePort()
    val haptics = FluidHapticsImpl(port, enabled = { true }, clock = { now })
    haptics.grain(0.3f)
    assertEquals(listOf(HapticPrimitiveStep(HapticPrimitive.TICK, 0.3f)), port.compositions.single())
    // Piu' vicino del pavimento: niente.
    now += FluidHapticsImpl.GRAIN_MIN_GAP_MILLIS - 1
    haptics.grain(0.3f)
    assertEquals(1, port.compositions.size)
    now += 1
    haptics.grain(2f)
    assertEquals(1f, port.compositions.last().single().scale)
    // La grana non arma la rete dei controlli: un Tap subito dopo suona.
    haptics.play(FluidHapticEvent.Tap)
    assertEquals(3, port.compositions.size)

    val saving = FakePort(powerSave = true)
    FluidHapticsImpl(saving, enabled = { true }).grain(0.4f)
    assertTrue(saving.compositions.isEmpty() && saving.platforms.isEmpty())

    val noPrimitives = FakePort(primitives = emptySet())
    FluidHapticsImpl(noPrimitives, enabled = { true }).grain(0.4f)
    assertEquals(listOf(HapticFeedbackType.SegmentFrequentTick), noPrimitives.platforms)

    val off = FakePort()
    FluidHapticsImpl(off, enabled = { false }).grain(0.4f)
    assertTrue(off.compositions.isEmpty())
  }

  @Test
  fun `le tacche di trascinamento sono tick continui, secchi e corti`() {
    assertTrue(FluidHapticEvent.DragTick.continuous)
    assertTrue(FluidHapticEvent.DragTick in FluidHapticPatterns.controlEvents)
    // Il piu' leggero dei tick: piu' leggero di quello che si sente da solo.
    val drag = FluidHapticPatterns.composition(FluidHapticEvent.DragTick).single()
    val tick = FluidHapticPatterns.composition(FluidHapticEvent.Tick).single()
    assertEquals(HapticPrimitive.TICK, drag.id)
    assertTrue(drag.scale < tick.scale)
    assertTrue(FluidHapticPatterns.durationMillis(FluidHapticEvent.DragTick) <= 20)

    var now = 0L
    val port = FakePort()
    val haptics = FluidHapticsImpl(port, enabled = { true }, clock = { now })
    repeat(10) { haptics.play(FluidHapticEvent.DragTick); now += 16 }
    // 10 richieste in 160 ms: il motore ne lascia passare una ogni 40 ms.
    assertEquals(4, port.compositions.size)

    val saving = FakePort(powerSave = true)
    FluidHapticsImpl(saving, enabled = { true }).play(FluidHapticEvent.DragTick)
    assertTrue(saving.compositions.isEmpty() && saving.platforms.isEmpty())
  }

  @Test
  fun `le allerte vibrano con l'attenzione delle notifiche, i controlli col tocco`() {
    val port = FakePort()
    val haptics = FluidHapticsImpl(port, enabled = { true })
    haptics.play(FluidHapticEvent.AlertAlarm)
    haptics.play(FluidHapticEvent.Open)
    assertEquals(listOf(true, false), port.attentions)
  }

  @Test
  fun `senza vibratore il motore e' spento e nulla parte`() {
    val port = FakePort(hasVibrator = false)
    val haptics = FluidHapticsImpl(port, enabled = { true })
    assertEquals(FluidHapticEngine.Off, haptics.engine)
    haptics.play(FluidHapticEvent.Success)
    assertTrue(port.compositions.isEmpty() && port.platforms.isEmpty())
  }
}
