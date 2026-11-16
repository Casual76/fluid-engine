package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneChange
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneMotion
import dev.antigravity.fluidengine.ui.glass.field.GlassPlanePiece
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneState
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Il conducente del contenitore (4.0, piano «iOS in Android» F3.2): chi porta il vetro, fotogramma per fotogramma.
 *
 * È la prova senza dispositivo del **passaggio di consegne** (F3-b) per la parte che si può provare: la geometria. Il
 * palco parte dalla geometria che il riposo mostrava (nessun pezzo salta al primo fotogramma), il riposo d'arrivo
 * riprende la geometria che il palco aveva all'ultimo fotogramma (entro mezzo pixel), e un cambio a metà viaggio riparte
 * da dove i vetri si trovano (F3-f). Che riposo e palco disegnino quella geometria allo stesso modo è la parità di F1B
 * (stessa ricetta, stesso corpo): resta da guardare in S3.
 */
class FluidGlassContainerDriverTest {

  private val canvas = Rect(0f, 0f, 600f, 200f)
  private val frameNanos = 16_666_667L

  private fun piece(key: String, x: Float, width: Float = 132f, id: Any? = key, transition: GlassPlaneTransition =
    GlassPlaneTransition.MatchedGeometry) =
    GlassPlanePiece(key, Rect(x, 60f, x + width, 192f), FluidCornerRadii.all(66f), id = id, transition = transition)

  private fun state(vararg pieces: GlassPlanePiece, spacing: Float = 48f) = GlassPlaneState(pieces.toList(), spacing, canvas)

  private fun assertRect(expected: Rect, actual: Rect, epsilon: Float = 0.001f, message: String = "") {
    assertEquals("$message left", expected.left, actual.left, epsilon)
    assertEquals("$message top", expected.top, actual.top, epsilon)
    assertEquals("$message right", expected.right, actual.right, epsilon)
    assertEquals("$message bottom", expected.bottom, actual.bottom, epsilon)
  }

  private val a = piece("a", 40f)
  private val b = piece("b", 196f)
  private val far = piece("far", 440f, transition = GlassPlaneTransition.Materialize)

  @Test
  fun `la prima misura e' il riposo, senza viaggio`() {
    val driver = FluidGlassPlaneDriver()
    assertEquals(FluidGlassPlaneDriver.Change.Rest, driver.step(0L, state(a), GlassPlaneMotion()))
    assertNull(driver.journey)
    assertEquals(1, driver.rest!!.pieces.size)
  }

  @Test
  fun `meno di mezzo pixel non e' un cambio, e un contenitore che si sposta non cambia niente`() {
    val driver = FluidGlassPlaneDriver()
    driver.step(0L, state(a), GlassPlaneMotion())
    val nudged = a.copy(bounds = a.bounds.translate(0.3f, -0.4f))
    assertEquals(FluidGlassPlaneDriver.Change.Nothing, driver.step(frameNanos, state(nudged), GlassPlaneMotion()))
    assertTrue(planesAlike(state(a), state(nudged)))
    assertFalse(planesAlike(state(a), state(a.copy(bounds = a.bounds.translate(1f, 0f)))))
    assertFalse(planesAlike(state(a), state(a.copy(id = "altro"))))
    // L'ordine del registro non è un cambio: chi è stato tolto e rimesso torna in fondo.
    assertTrue(planesAlike(state(a, b), state(b, a)))
    assertFalse(planesAlike(state(a, b), state(a, b.copy(key = "c"))))
  }

  @Test
  fun `il palco parte dalla geometria del riposo, e chi nasce parte sovrapposto al vicino`() {
    val driver = FluidGlassPlaneDriver()
    driver.step(0L, state(a), GlassPlaneMotion())
    assertEquals(FluidGlassPlaneDriver.Change.Started, driver.step(frameNanos, state(a, b), GlassPlaneMotion()))
    val frame = driver.frame!!
    val matched = frame.pieces.single { it.flightId.targetKey == "a" }
    val born = frame.pieces.single { it.flightId.targetKey == "b" }
    assertEquals(GlassPlaneChange.Matched, matched.change)
    assertEquals(GlassPlaneChange.BornFromNeighbour, born.change)
    assertEquals("a", born.bornFrom)
    // Il primo fotogramma del palco è il riposo di prima: «a» è dov'era, «b» coincide con «a».
    assertRect(a.bounds, matched.bounds, message = "a al primo fotogramma")
    assertRect(a.bounds, born.bounds, message = "b al primo fotogramma")
    assertEquals(0f, driver.progress, 0f)
    // Il riposo è già l'arrivo, montato e spento finché il palco porta il vetro.
    assertEquals(setOf("a", "b"), driver.rest!!.pieces.map { it.key }.toSet())
    assertEquals(listOf("a"), driver.origin!!.pieces.map { it.key })
  }

  @Test
  fun `il riposo d'arrivo riprende la geometria dell'ultimo fotogramma del palco`() {
    val driver = FluidGlassPlaneDriver()
    driver.step(0L, state(a), GlassPlaneMotion())
    driver.step(frameNanos, state(a, b), GlassPlaneMotion())
    var now = frameNanos
    var last = driver.frame!!
    var frames = 0
    while (true) {
      now += frameNanos
      val change = driver.step(now, state(a, b), GlassPlaneMotion())
      if (change == FluidGlassPlaneDriver.Change.Finished) break
      assertEquals(FluidGlassPlaneDriver.Change.Moved, change)
      assertTrue("il progresso dichiarato non arriva all'uno in viaggio", driver.progress < 1f)
      last = driver.frame!!
      frames++
      assertTrue("il viaggio deve finire", frames < 200)
    }
    // Contesto di 300 ms a 60 Hz: una ventina di fotogrammi.
    assertTrue("fotogrammi $frames", frames in 12..24)
    assertNull(driver.journey)
    assertEquals(1f, driver.progress, 0f)
    for (target in listOf(a, b)) {
      val flight = last.pieces.single { it.flightId.targetKey == target.key }
      assertRect(target.bounds, flight.bounds, FluidGlassPlaneTolerancePx, "${target.key} all'ultimo fotogramma")
    }
  }

  @Test
  fun `un cambio a meta' viaggio riparte da dove i vetri si trovano`() {
    val driver = FluidGlassPlaneDriver()
    driver.step(0L, state(a), GlassPlaneMotion())
    driver.step(frameNanos, state(a, b), GlassPlaneMotion())
    var now = frameNanos
    repeat(6) {
      now += frameNanos
      driver.step(now, state(a, b), GlassPlaneMotion())
    }
    val before = driver.frame!!.pieces.single { it.flightId.targetKey == "b" }.bounds
    val revision = driver.journeyRevision
    now += frameNanos
    // «b» se ne va prima di essere arrivato: il viaggio riparte dalla geometria di adesso.
    assertNotNull(driver.journey)
    assertEquals(FluidGlassPlaneDriver.Change.Retargeted, driver.step(now, state(a), GlassPlaneMotion()))
    assertEquals(revision + 1, driver.journeyRevision)
    val restart = driver.frame!!.pieces.single { it.flightId.sourceKey == "b" }
    // Fra l'ultimo fotogramma mostrato e la ripartenza passa un fotogramma di moto, non un salto.
    val step = maxOf(
      abs(restart.bounds.left - before.left), abs(restart.bounds.right - before.right),
      abs(restart.bounds.top - before.top), abs(restart.bounds.bottom - before.bottom),
    )
    assertTrue("salto alla ripartenza di $step px", step < 40f)
    assertTrue(restart.change == GlassPlaneChange.MergeIntoNeighbour || restart.change == GlassPlaneChange.Dematerialize)
  }

  @Test
  fun `senza animazione, o con la scala di sistema a zero, il vetro va al posto nuovo`() {
    val none = FluidGlassPlaneDriver()
    none.step(0L, state(a), null)
    assertEquals(FluidGlassPlaneDriver.Change.Rest, none.step(frameNanos, state(a, b), null))
    assertNull(none.journey)
    val zero = FluidGlassPlaneDriver()
    zero.step(0L, state(a), GlassPlaneMotion())
    assertEquals(FluidGlassPlaneDriver.Change.Rest, zero.step(frameNanos, state(a, b), GlassPlaneMotion(), 0f))
  }

  @Test
  fun `la scala delle animazioni di sistema allunga il viaggio`() {
    fun framesAt(scale: Float): Int {
      val driver = FluidGlassPlaneDriver()
      driver.step(0L, state(a), GlassPlaneMotion())
      driver.step(frameNanos, state(a, b), GlassPlaneMotion(), scale)
      var now = frameNanos
      var frames = 0
      while (driver.step(now + frameNanos, state(a, b), GlassPlaneMotion(), scale) != FluidGlassPlaneDriver.Change.Finished) {
        now += frameNanos
        frames++
      }
      return frames
    }
    val normal = framesAt(1f)
    val slow = framesAt(2f)
    assertTrue("$normal contro $slow", slow in normal * 2 - 2..normal * 2 + 2)
  }

  @Test
  fun `una spec dell'app porta il suo tempo`() {
    val driver = FluidGlassPlaneDriver()
    val motion = GlassPlaneMotion(spec = tween(100))
    driver.step(0L, state(a), motion)
    driver.step(frameNanos, state(a, b), motion)
    assertEquals(100.0, driver.journey!!.durationMs, 1.0)
  }

  @Test
  fun `un campo di sole comparse porta la rifrazione nell'intensita', uno misto toglie solo chi e' appena nato`() {
    val driver = FluidGlassPlaneDriver()
    driver.step(0L, state(a), GlassPlaneMotion())
    driver.step(frameNanos, state(a, far), GlassPlaneMotion())
    driver.step(frameNanos * 8, state(a, far), GlassPlaneMotion())
    val frame = driver.frame!!
    val appearing = frame.pieces.single { it.flightId.targetKey == "far" }
    assertEquals(GlassPlaneChange.Materialize, appearing.change)
    val alone = fluidGlassFieldSlabs(frame, listOf(appearing.flightId), Offset(400f, 20f)) { 0.6f }
    assertEquals(appearing.materialize, alone.intensity, 0f)
    assertTrue(alone.intensity > 0f && alone.intensity < 1f)
    assertEquals(1, alone.slabs.size)
    assertRect(appearing.bounds.translate(-400f, -20f), alone.slabs.single().frame, message = "nelle coordinate del nodo")
    assertEquals(0.6f, alone.slabs.single().smoothing, 0f)

    val start = FluidGlassPlaneDriver()
    start.step(0L, state(a), GlassPlaneMotion())
    start.step(frameNanos, state(a, far), GlassPlaneMotion())
    val t0 = start.frame!!
    val mixed = fluidGlassFieldSlabs(t0, t0.pieces.map { it.flightId }, Offset.Zero) { 0f }
    assertEquals(1f, mixed.intensity, 0f)
    assertEquals("al primo fotogramma chi compare non c'è ancora", 1, mixed.slabs.size)
  }

  @Test
  fun `la forma di Fluid-physics, una lastra da sola, un gruppo fino a otto`() {
    val slab = FluidForm.Slab(Rect(0f, 0f, 10f, 10f))
    assertNull(fluidGlassFormOf(emptyList(), 4f))
    assertSame(slab, fluidGlassFormOf(listOf(slab), 4f))
    val group = fluidGlassFormOf(List(10) { slab }, 4f) as FluidForm.Group
    assertEquals(FluidForm.Group.MaxPieces, group.pieces.size)
    assertEquals(4f, group.blendRadius, 0f)
  }
}
