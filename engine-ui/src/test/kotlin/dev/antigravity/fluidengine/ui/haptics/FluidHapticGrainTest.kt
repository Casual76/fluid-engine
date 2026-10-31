package dev.antigravity.fluidengine.ui.haptics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidHapticGrainTest {

  private val config = FluidHapticGrainConfig.Default

  /** Un dito a velocita' costante: [stepDp] ogni [everyMillis], per [steps] eventi. Ritorna gli istanti e le intensita' dei granelli. */
  private fun FluidHapticGrain.drag(
    steps: Int,
    stepDp: Float,
    everyMillis: Long,
    fromMillis: Long = 0L,
    pxPerDp: Float = 2f,
  ): List<Pair<Long, Float>> {
    val grains = mutableListOf<Pair<Long, Float>>()
    repeat(steps) { i ->
      val at = fromMillis + everyMillis * i
      onMove(stepDp * pxPerDp, pxPerDp, at)?.let { grains += at to it }
    }
    return grains
  }

  @Test
  fun `a passo d'uomo non suona mai, per quanta strada faccia`() {
    val grain = FluidHapticGrain()
    // 300 dp/s per due secondi: seicento dp di strada, zero granelli. Era una tacca ogni 10 dp.
    assertTrue(grain.drag(steps = 250, stepDp = 2.4f, everyMillis = 8).isEmpty())
    assertTrue(grain.speedDpPerSec in 280f..320f)
  }

  @Test
  fun `veloce suona un treno, e mai due granelli piu' vicini del pavimento`() {
    val grain = FluidHapticGrain()
    // 1500 dp/s a 120 Hz.
    val grains = grain.drag(steps = 120, stepDp = 12.5f, everyMillis = 8)
    assertTrue("nessun granello a 1500 dp/s", grains.size > 20)
    grains.zipWithNext().forEach { (a, b) ->
      assertTrue("granelli a ${b.first - a.first} ms", b.first - a.first >= config.minGapMillis)
    }
    grains.forEach { (_, intensity) -> assertTrue(intensity in 0f..1f) }
  }

  @Test
  fun `piu' corre il dito, piu' forte la grana`() {
    val medium = FluidHapticGrain().drag(steps = 200, stepDp = 6.4f, everyMillis = 8) // 800 dp/s
    val fast = FluidHapticGrain().drag(steps = 200, stepDp = 20f, everyMillis = 8) // 2500 dp/s
    val mediumMean = medium.drop(5).map { it.second }.average()
    val fastMean = fast.drop(5).map { it.second }.average()
    assertTrue("media $mediumMean, veloce $fastMean", fastMean > mediumMean * 1.6)
    assertTrue(fastMean <= config.maxIntensity * (1 + config.intensityJitter) + 1e-3)
  }

  @Test
  fun `la soglia ha isteresi`() {
    val grain = FluidHapticGrain()
    // Le fasi sono contigue: una pausa vera fra l'una e l'altra azzererebbe la velocita'.
    // Sale a 1000 dp/s: parte.
    val up = grain.drag(steps = 40, stepDp = 8f, everyMillis = 8)
    assertTrue(up.isNotEmpty())
    // Scende a 450 dp/s, sotto la partenza ma sopra lo spegnimento: continua.
    val middle = grain.drag(steps = 60, stepDp = 3.6f, everyMillis = 8, fromMillis = 320)
    assertTrue("si e' spenta sopra la soglia di spegnimento", middle.size > 3)
    // Sotto i 380: si spegne e resta spenta.
    val down = grain.drag(steps = 60, stepDp = 2f, everyMillis = 8, fromMillis = 800)
    assertTrue(down.drop(10).isEmpty())
    // Risale a 450: non riparte, perche' 450 e' sotto la soglia di partenza.
    val again = grain.drag(steps = 60, stepDp = 3.6f, everyMillis = 8, fromMillis = 1_280)
    assertTrue(again.isEmpty())
  }

  @Test
  fun `i passi non sono regolari, ma lo stesso seme da' lo stesso treno`() {
    val a = FluidHapticGrain().apply { restart(seed = 42) }.drag(steps = 600, stepDp = 0.7f, everyMillis = 1)
    val b = FluidHapticGrain().apply { restart(seed = 42) }.drag(steps = 600, stepDp = 0.7f, everyMillis = 1)
    assertEquals(a, b)
    // 700 dp/s a 1000 Hz: il passo oscilla fra 8 e 15 eventi, sopra il pavimento di 9 ms.
    val gaps = a.zipWithNext { x, y -> y.first - x.first }.toSet()
    assertTrue("un treno regolare e' un ticchettio: $gaps", gaps.size > 2)
    assertTrue(a.map { it.second }.toSet().size > 2)
    val other = FluidHapticGrain().apply { restart(seed = 7) }.drag(steps = 600, stepDp = 0.7f, everyMillis = 1)
    assertTrue(a != other)
  }

  @Test
  fun `gli eventi dello stesso istante si sommano invece di dare velocita' infinita`() {
    val grain = FluidHapticGrain()
    grain.onMove(0f, 2f, 0L)
    // Un gruppo di tre eventi allo stesso istante, poi uno 16 ms dopo: 12 dp in 16 ms = 750 dp/s.
    grain.onMove(8f, 2f, 16L)
    grain.onMove(8f, 2f, 16L)
    grain.onMove(8f, 2f, 16L)
    assertTrue(grain.speedDpPerSec < 1000f)
    grain.onMove(0f, 2f, 32L)
    assertTrue(grain.speedDpPerSec.isFinite())
  }

  @Test
  fun `un gesto nuovo riparte da fermo`() {
    val grain = FluidHapticGrain()
    grain.drag(steps = 60, stepDp = 12f, everyMillis = 8)
    grain.restart()
    assertEquals(0f, grain.speedDpPerSec)
    // Il primo evento del gesto nuovo non suona mai: non ha un prima.
    assertEquals(null, grain.onMove(50f, 2f, 10_000L))
  }
}
