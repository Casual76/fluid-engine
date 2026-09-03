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
  fun `i tick continui si diradano a 40 ms, i tap no`() {
    var now = 0L
    val port = FakePort()
    val haptics = FluidHapticsImpl(port, enabled = { true }, clock = { now })
    repeat(5) { haptics.play(FluidHapticEvent.Tick); now += 10 }
    assertEquals(2, port.compositions.size)
    repeat(3) { haptics.play(FluidHapticEvent.Tap) }
    assertEquals(5, port.compositions.size)
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
