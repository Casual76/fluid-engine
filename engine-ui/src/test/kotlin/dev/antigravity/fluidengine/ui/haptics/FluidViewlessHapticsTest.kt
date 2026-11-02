package dev.antigravity.fluidengine.ui.haptics

import android.os.VibrationEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Un vibratore senza `View`: il ripiego che [fluidHapticsFor] usa dove le primitive mancano. La porta
 * finta fa da `AndroidHapticPort` senza Compose: `platform(type)` passa dalla stessa mappa
 * ([predefinedHapticFor]) e annota l'effetto di fabbrica che il telefono suonerebbe.
 */
private class ViewlessPort(
  override val hasVibrator: Boolean = true,
  private val primitives: Set<Int> = emptySet(),
  override var powerSave: Boolean = false,
) : HapticPort {
  val compositions = mutableListOf<List<HapticPrimitiveStep>>()
  val effects = mutableListOf<PredefinedHaptic>()
  override val systemHapticsEnabled: Boolean = true

  override fun supportedPrimitives(ids: IntArray): Set<Int> = ids.filter { it in primitives }.toSet()

  override fun vibrate(steps: List<HapticPrimitiveStep>, attention: Boolean): Boolean {
    compositions += steps
    return true
  }

  override fun platform(type: HapticFeedbackType) {
    effects += predefinedHapticFor(type)
  }
}

class FluidViewlessHapticsTest {

  @Test
  fun theEffectIdsAreTheOnesAndroidUses() {
    // Copiati a mano perche' la mappa gira sul computer: se Android li cambiasse, o un refuso li
    // sposta, questo e' il punto in cui si vede.
    assertEquals(VibrationEffect.EFFECT_TICK, PredefinedHaptic.Tick.effectId)
    assertEquals(VibrationEffect.EFFECT_CLICK, PredefinedHaptic.Click.effectId)
    assertEquals(VibrationEffect.EFFECT_DOUBLE_CLICK, PredefinedHaptic.DoubleClick.effectId)
    assertEquals(VibrationEffect.EFFECT_HEAVY_CLICK, PredefinedHaptic.HeavyClick.effectId)
  }

  @Test
  fun everyFallbackTheEngineUsesHasAnEffect() {
    // Il ripiego di ogni evento passa dalla mappa e ne esce uno dei quattro effetti, mai un'eccezione.
    FluidHapticEvent.entries.forEach { event ->
      val effect = predefinedHapticFor(FluidHapticPatterns.fallback(event))
      assertTrue("$event", effect in PredefinedHaptic.entries)
    }
  }

  @Test
  fun theRelativeStrengthOfTheVocabularySurvives() {
    fun effect(event: FluidHapticEvent) = predefinedHapticFor(FluidHapticPatterns.fallback(event))
    // Un tocco e' piu' leggero di un'apertura, un no sono due colpi, un'allerta pesa.
    assertEquals(PredefinedHaptic.Tick, effect(FluidHapticEvent.Tap))
    assertEquals(PredefinedHaptic.Click, effect(FluidHapticEvent.Open))
    assertEquals(PredefinedHaptic.Click, effect(FluidHapticEvent.Success))
    assertEquals(PredefinedHaptic.DoubleClick, effect(FluidHapticEvent.Reject))
    assertEquals(PredefinedHaptic.DoubleClick, effect(FluidHapticEvent.Error))
    assertEquals(PredefinedHaptic.HeavyClick, effect(FluidHapticEvent.AlertAlarm))
    // I tick a raffica restano i piu' leggeri.
    assertEquals(PredefinedHaptic.Tick, effect(FluidHapticEvent.DragTick))
    assertEquals(PredefinedHaptic.Tick, effect(FluidHapticEvent.Tick))
    assertEquals(PredefinedHaptic.Tick, effect(FluidHapticEvent.ToggleOff))
    assertEquals(PredefinedHaptic.Click, effect(FluidHapticEvent.ToggleOn))
  }

  @Test
  fun everyTypeThisComposeKnowsHasAnEffect() {
    // `values()` e' l'elenco di questa versione di Compose: se ne arrivasse uno nuovo passerebbe dal
    // ramo di riserva (un click) invece di esplodere, e qui si vede quali sono.
    val types = HapticFeedbackType.values()
    assertTrue(types.isNotEmpty())
    types.forEach { type ->
      assertTrue("$type", predefinedHapticFor(type) in PredefinedHaptic.entries)
    }
    // Un tasto di tastiera e' il colpo piu' leggero: una tastiera ne suona uno a ogni lettera.
    assertEquals(PredefinedHaptic.Tick, predefinedHapticFor(HapticFeedbackType.KeyboardTap))
  }

  @Test
  fun everyLegacyWaveformStartsAtZeroAndHasPositivePulses() {
    PredefinedHaptic.entries.forEach { effect ->
      val timings = legacyWaveformFor(effect)
      assertEquals("$effect parte subito", 0L, timings.first())
      // Coppie [attesa, acceso]: un numero pari di campi, e un impulso a zero o negativo non vibra.
      assertEquals("$effect: campi a coppie", 0, timings.size % 2)
      timings.forEachIndexed { index, ms ->
        if (index % 2 == 1) assertTrue("$effect impulso $index = $ms ms", ms > 0)
      }
    }
    // Un doppio click ha due impulsi, gli altri uno.
    assertEquals(2, legacyWaveformFor(PredefinedHaptic.DoubleClick).size / 2)
    assertEquals(1, legacyWaveformFor(PredefinedHaptic.Click).size / 2)
  }

  @Test
  fun withoutPrimitivesTheEngineFallsBackToFactoryEffects() {
    val port = ViewlessPort()
    val haptics = FluidHapticsImpl(port, enabled = { true })
    assertEquals(FluidHapticEngine.Platform, haptics.engine)
    haptics.play(FluidHapticEvent.Tap)
    assertEquals(listOf(PredefinedHaptic.Tick), port.effects)
    assertTrue(port.compositions.isEmpty())
  }

  @Test
  fun withPrimitivesTheSameEngineComposesAndTheFactoryEffectIsNotUsed() {
    val port = ViewlessPort(primitives = HapticPrimitive.all.toSet())
    val haptics = FluidHapticsImpl(port, enabled = { true })
    assertEquals(FluidHapticEngine.Composition, haptics.engine)
    haptics.play(FluidHapticEvent.Confirm)
    assertEquals(1, port.compositions.size)
    assertTrue(port.effects.isEmpty())
  }

  @Test
  fun theAppSwitchSilencesTheViewlessPathToo() {
    val port = ViewlessPort()
    var on = false
    val haptics = FluidHapticsImpl(port, enabled = { on })
    haptics.play(FluidHapticEvent.Error)
    assertTrue(port.effects.isEmpty())
    on = true
    haptics.play(FluidHapticEvent.Error)
    assertEquals(listOf(PredefinedHaptic.DoubleClick), port.effects)
  }

  @Test
  fun twoControlEventsInTheSameMomentAreOneEffect() {
    // La rete dei 70 ms vale anche qui: e' il motivo per cui un codice a viste usa il motore
    // invece di chiamare il vibratore a mano da due parti.
    var now = 1_000L
    val port = ViewlessPort()
    val haptics = FluidHapticsImpl(port, enabled = { true }, clock = { now })
    haptics.play(FluidHapticEvent.Open)
    now += 20
    haptics.play(FluidHapticEvent.Tap)
    assertEquals(1, port.effects.size)
    now += 200
    haptics.play(FluidHapticEvent.Tap)
    assertEquals(2, port.effects.size)
  }

  @Test
  fun noVibratorMeansNothingAtAll() {
    val port = ViewlessPort(hasVibrator = false)
    val haptics = FluidHapticsImpl(port, enabled = { true })
    assertEquals(FluidHapticEngine.Off, haptics.engine)
    FluidHapticEvent.entries.forEach { haptics.play(it) }
    assertTrue(port.effects.isEmpty() && port.compositions.isEmpty())
  }

  @Test
  fun theLibrePodsGrainFallsOnTheLightestFactoryEffect() {
    val port = ViewlessPort()
    val haptics = FluidHapticsImpl(port, enabled = { true })
    haptics.grain(0.5f, FluidHapticGrainStyle.LibrePods)
    assertEquals(listOf(PredefinedHaptic.Tick), port.effects)
  }
}
