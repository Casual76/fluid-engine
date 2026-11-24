package dev.antigravity.fluidengine.ui.fluid

import dev.antigravity.fluidengine.ui.fluid.icons.Add
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Notifications
import dev.antigravity.fluidengine.ui.fluid.icons.VolumeUp
import dev.antigravity.fluidengine.ui.fluid.icons.Wifi
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F8.5 · Gli effetti dei simboli, i conti: ogni effetto ha la sua durata e torna a riposo, le misure contro Apple (WWDC23
 * 10197, WWDC24 10188), gli effetti attivi che finiscono il ciclo, «Riduci movimento», gli strati delle icone per il
 * colore variabile e Draw On. Le prove nell'albero Compose vero sono nel `sample` (`F8p5FestaSimboliUiTest`).
 */
class F8p5SymbolEffectTest {
  private val frame = FluidSymbolFrame()

  private val all = listOf(
    FluidSymbolEffect.Bounce, FluidSymbolEffect.BounceDown, FluidSymbolEffect.Pulse, FluidSymbolEffect.Wiggle,
    FluidSymbolEffect.WiggleUp, FluidSymbolEffect.WiggleClockwise, FluidSymbolEffect.wiggleRotation(pivotY = 0.12f),
    FluidSymbolEffect.Rotate, FluidSymbolEffect.RotateCounterClockwise, FluidSymbolEffect.Breathe,
    FluidSymbolEffect.VariableColor, FluidSymbolEffect.VariableColorCumulative,
  )

  private fun at(effect: FluidSymbolEffect, ms: Float, reduced: Boolean = false): Boolean =
    fluidSymbolEffectFrame(effect, ms, indefinite = false, stopAt = Float.NaN, reduced = reduced, out = frame)

  @Test
  fun `ogni effetto ha la sua durata, si muove fino alla fine e poi e' a riposo`() {
    val expected = mapOf(
      FluidSymbolEffect.Bounce to 600.6f, FluidSymbolEffect.Pulse to 2000f, FluidSymbolEffect.Wiggle to 901f,
      FluidSymbolEffect.Rotate to 1902f, FluidSymbolEffect.Breathe to 3000f,
    )
    for ((effect, ms) in expected) assertEquals("$effect", ms, fluidSymbolEndMs(effect, false, Float.NaN), 1f)
    for (effect in all) {
      val end = fluidSymbolEndMs(effect, false, Float.NaN)
      assertTrue("$effect si muove a meta'", at(effect, end / 3f))
      assertFalse("$effect e' finito", at(effect, end))
      assertTrue("$effect a riposo dopo la fine", frame.atRest)
      // L'ultimo fotogramma prima della fine e' gia' quasi a riposo: niente scatto all'arrivo.
      at(effect, end - 4f)
      assertEquals("$effect scala", 1f, frame.scale, 0.01f)
      assertEquals("$effect spostamento", 0f, frame.translationX + frame.translationY, 0.002f)
      assertEquals("$effect rotazione", 0f, minOf(abs(frame.rotation), abs(abs(frame.rotation) - 360f)), 1.5f)
      assertTrue("$effect opacita' ${frame.alpha}", frame.alpha > 0.97f)
    }
  }

  @Test
  fun `il rimbalzo e' quello di SF Symbols 5, +25 per cento a 200 ms e -9 a 400`() {
    at(FluidSymbolEffect.Bounce, 200f)
    assertEquals(1.255f, frame.scale, 0.01f)
    at(FluidSymbolEffect.Bounce, 400f)
    assertEquals(0.905f, frame.scale, 0.01f)
    // Verso il basso: prima si stringe.
    at(FluidSymbolEffect.BounceDown, 200f)
    assertEquals(1f - 0.255f * 0.8f, frame.scale, 0.01f)
  }

  @Test
  fun `il dondolio arriva al 7 per cento della taglia, o a 10 gradi, e si smorza`() {
    at(FluidSymbolEffect.Wiggle, 140f)
    assertEquals(0.073f, frame.translationX, 0.003f)
    at(FluidSymbolEffect.Wiggle, 300f)
    assertEquals(-0.047f, frame.translationX, 0.004f)
    // Da destra a sinistra «in avanti» e' a sinistra.
    fluidSymbolEffectFrame(FluidSymbolEffect.Wiggle, 140f, false, Float.NaN, false, frame, rtl = true)
    assertEquals(-0.073f, frame.translationX, 0.003f)
    val bell = FluidSymbolEffect.wiggleRotation(pivotY = 0.12f)
    at(bell, 140f)
    assertEquals(10f, frame.rotation, 0.5f)
    assertEquals(0.12f, frame.pivotY, 0f)
  }

  @Test
  fun `la rotazione fa un giro in 1,9 s, svelta all'inizio e lunga in frenata`() {
    at(FluidSymbolEffect.Rotate, 500f)
    assertEquals(0.494f * 360f, frame.rotation, 3f)
    at(FluidSymbolEffect.Rotate, 1000f)
    assertEquals(0.836f * 360f, frame.rotation, 3f)
    at(FluidSymbolEffect.RotateCounterClockwise, 500f)
    assertEquals(-0.494f * 360f, frame.rotation, 3f)
  }

  @Test
  fun `pulsazione e respiro hanno le opacita' e la scala misurate`() {
    at(FluidSymbolEffect.Pulse, 1000f)
    assertEquals(0.48f, frame.alpha, 0.01f)
    at(FluidSymbolEffect.Breathe, 1350f)
    assertEquals(1.23f, frame.scale, 0.005f)
    assertEquals(0.65f, frame.alpha, 0.005f)
    at(FluidSymbolEffect.Breathe, 2850f)
    assertTrue(frame.atRest)
  }

  @Test
  fun `il colore variabile accende le tacche ogni 334 ms, una per volta o a riempire`() {
    val step = FluidSymbolProfile.VariableStepMs
    val iterative = FluidSymbolEffect.VariableColor
    assertEquals(4 * step, fluidSymbolCycleMs(iterative, 3), 0.01f)
    assertEquals(7 * step, fluidSymbolCycleMs(FluidSymbolEffect.VariableColorCumulative, 3), 0.01f)
    // A 290 ms la prima tacca e' accesa, le altre spente al 24 %.
    assertEquals(1f, fluidSymbolVariableAlpha(iterative, 290f, 0, 3), 0.001f)
    assertEquals(0.24f, fluidSymbolVariableAlpha(iterative, 290f, 1, 3), 0.001f)
    // A 624 ms tocca alla seconda; la prima si e' spenta.
    assertEquals(1f, fluidSymbolVariableAlpha(iterative, step + 290f, 1, 3), 0.001f)
    assertEquals(0.24f, fluidSymbolVariableAlpha(iterative, step + 290f, 0, 3), 0.001f)
    // A riempire: a 1,2 s sono tutte accese, a 2,2 s tutte spente.
    val cumulative = FluidSymbolEffect.VariableColorCumulative
    for (layer in 0..2) {
      assertEquals(1f, fluidSymbolVariableAlpha(cumulative, 1200f, layer, 3), 0.001f)
      assertEquals(0.24f, fluidSymbolVariableAlpha(cumulative, 2200f, layer, 3), 0.001f)
    }
  }

  @Test
  fun `un effetto attivo non finisce, e fermato finisce il ciclo in corso`() {
    val pulse = FluidSymbolEffect.Pulse
    assertEquals(Float.POSITIVE_INFINITY, fluidSymbolEndMs(pulse, true, Float.NaN), 0f)
    assertTrue(fluidSymbolEffectFrame(pulse, 9_000f, true, Float.NaN, false, frame))
    // Fermato a 2,5 s: finisce a 4 s, fine del secondo ciclo.
    assertEquals(4000f, fluidSymbolEndMs(pulse, true, 2500f), 0.5f)
    assertFalse(fluidSymbolEffectFrame(pulse, 4000f, true, 2500f, false, frame))
    assertTrue(frame.atRest)
    // La rotazione continua: rampa di 0,8 s, poi 180 gradi al secondo.
    assertEquals(72f, fluidSymbolSpinDegrees(800f), 0.01f)
    assertEquals(252f, fluidSymbolSpinDegrees(1800f), 0.01f)
    // Fermata a 1,8 s (252 gradi) finisce il giro frenando: 108 gradi in 1,8 s, e chiude esattamente su 360.
    val spin = FluidSymbolEffect.Rotate
    val end = fluidSymbolEndMs(spin, true, 1800f)
    assertEquals(1800f + 1800f, end, 1f)
    fluidSymbolEffectFrame(spin, end - 1f, true, 1800f, false, frame)
    assertEquals(0f, minOf(abs(frame.rotation), abs(frame.rotation - 360f)), 0.5f)
    fluidSymbolEffectFrame(spin, 1801f, true, 1800f, false, frame)
    assertEquals(252.2f, frame.rotation, 0.3f)
  }

  @Test
  fun `velocita' e ripetizioni`() {
    assertEquals(2 * 600.6f, fluidSymbolEndMs(FluidSymbolEffect.Bounce.repeat(2), false, Float.NaN), 1f)
    assertEquals(300.3f, fluidSymbolEndMs(FluidSymbolEffect.Bounce.speed(2f), false, Float.NaN), 1f)
    at(FluidSymbolEffect.Bounce.speed(2f), 100f)
    assertEquals(1.255f, frame.scale, 0.01f)
    assertEquals(FluidSymbolEffect.Bounce.repeat(2), FluidSymbolEffect.Bounce.repeat(2))
  }

  @Test
  fun `riduci movimento toglie scala, spostamento e rotazione e tiene le opacita'`() {
    assertFalse(at(FluidSymbolEffect.Bounce, 200f, reduced = true))
    assertTrue(frame.atRest)
    assertFalse(at(FluidSymbolEffect.Wiggle, 140f, reduced = true))
    assertFalse(at(FluidSymbolEffect.Rotate, 500f, reduced = true))
    assertTrue(at(FluidSymbolEffect.Breathe, 1350f, reduced = true))
    assertEquals(1f, frame.scale, 0f)
    assertEquals(0.65f, frame.alpha, 0.005f)
    assertTrue(at(FluidSymbolEffect.Pulse, 1000f, reduced = true))
    assertEquals(0.48f, frame.alpha, 0.01f)
  }

  @Test
  fun `gli strati delle icone, per il colore variabile e Draw On`() {
    val check = SymbolGeometryBuilder.of(FluidIcons.Check)
    assertEquals(1, check.layers.size)
    assertEquals(SymbolDrawKind.Stroke, check.layers[0].kind)
    val wifi = SymbolGeometryBuilder.of(FluidIcons.Wifi)
    assertEquals(3, wifi.layers.size)
    assertTrue(wifi.layers.all { it.kind == SymbolDrawKind.Stroke })
    // Dal punto all'arco esterno, come le tacche di Apple.
    val order = fluidSymbolVariableLayers(wifi)
    val areas = order.map { abs(wifi.layers[it].outer.signedArea) }
    assertEquals(areas.sorted(), areas)
    assertTrue(wifi.layers[order[0]].outer.maxY > wifi.layers[order[2]].outer.maxY)
    val volume = SymbolGeometryBuilder.of(FluidIcons.VolumeUp)
    assertEquals(2, fluidSymbolVariableLayers(volume).size)
    assertTrue("il corpo dell'altoparlante resta acceso", volume.layers.any { it.kind == SymbolDrawKind.Sweep })
    // La campanella al tratto ha un buco: si disegna girando.
    assertTrue(SymbolGeometryBuilder.of(FluidIcons.Notifications).layers.any { it.kind == SymbolDrawKind.Sweep })
    // La croce del «+» e' un pezzo solo: compare, non si finge di disegnarla.
    val add = SymbolGeometryBuilder.of(FluidIcons.Add)
    assertTrue(add.layers.all { it.kind == SymbolDrawKind.Appear })
    assertFalse(add.drawable)
    // Una volta sola per icona.
    assertTrue(SymbolGeometryBuilder.of(FluidIcons.Check) === check)
  }

  @Test
  fun `il tratto della spunta si percorre da un capo all'altro senza allocare`() {
    val stroke = SymbolGeometryBuilder.of(FluidIcons.Check).layers[0].stroke!!
    val xs = FloatArray(symbolStrokeWindowCapacity())
    val ys = FloatArray(symbolStrokeWindowCapacity())
    assertEquals(0, symbolStrokeWindow(stroke, 0f, 0f, xs, ys))
    val half = symbolStrokeWindow(stroke, 0f, 0.5f, xs, ys)
    val full = symbolStrokeWindow(stroke, 0f, 1f, xs, ys)
    assertTrue(half in 4 until full)
    // Il capo di partenza e' quello che si legge prima: il braccio corto, a sinistra.
    assertTrue(stroke.ax[0] < stroke.ax[stroke.samples])
    // La larghezza del tratto e' quella del peso 500 (circa 2,3 su 24).
    assertEquals(2.3f, stroke.width, 0.5f)
    val counter = F8p5Allocations()
    fun run(times: Int) {
      for (i in 0 until times) symbolStrokeWindow(stroke, 0f, (i % 100) / 100f, xs, ys)
    }
    run(200)
    val a0 = counter.read(); run(1_000); val a1 = counter.read(); run(2_000); val a2 = counter.read()
    assertTrue("alloca per fotogramma: ${a1 - a0} e ${a2 - a1} byte", (a2 - a1) - (a1 - a0) < 512)
  }

  @Test
  fun `il fotogramma di un effetto non alloca`() {
    val counter = F8p5Allocations()
    fun run(times: Int) {
      for (i in 0 until times) {
        // Con l'indice: un `for (e in lista)` allocherebbe l'iteratore, e la prova misurerebbe il test.
        for (k in 0 until all.size) fluidSymbolEffectFrame(all[k], (i % 60) * 16.6f, false, Float.NaN, false, frame)
        fluidSymbolVariableAlpha(FluidSymbolEffect.VariableColor, (i % 80) * 16.6f, 1, 3)
      }
    }
    run(200)
    val a0 = counter.read(); run(500); val a1 = counter.read(); run(1_000); val a2 = counter.read()
    assertTrue("alloca per fotogramma: ${a1 - a0} e ${a2 - a1} byte", (a2 - a1) - (a1 - a0) < 512)
  }
}

/** I byte allocati da questo filo (HotSpot), per riflessione: la classpath dei test di Android non ha `java.lang.management`. */
internal class F8p5Allocations {
  private val bean = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
  private val method = Class.forName("com.sun.management.ThreadMXBean")
    .getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
  private val args = arrayOf<Any>(Thread.currentThread().id)

  fun read(): Long = method.invoke(bean, *args) as Long
}
