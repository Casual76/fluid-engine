package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneMotion
import dev.antigravity.fluidengine.ui.glass.field.GlassPlanePiece
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F4A.5b: ciò che un vetro dichiara di portare (`fluidGlassContentKey`). Un cambio **solo di contenuto** non è un viaggio
 * (due tasti che cambiano solo icona restano vetri fermi, F4A.3), ma il riposo lo sa: il vetro respira se cambia contenuto
 * nello stesso viaggio di un altro (la barra che cambia contesto), mai per un cambio vecchio.
 */
class FluidGlassContentKeyTest {
  private val canvas = Rect(0f, 0f, 420f, 140f)
  private val frameNanos = 16_666_667L
  private fun piece(key: String, left: Float, width: Float = 44f, content: Any? = null) =
    GlassPlanePiece(key, Rect(left, 40f, left + width, 84f), FluidCornerRadii.all(22f), id = key, contentKey = content)
  private fun state(vararg pieces: GlassPlanePiece) = GlassPlaneState(pieces.toList(), 12f, canvas)

  private val list = piece("tondo", 16f, content = "elenco")
  private val lens = piece("tondo", 16f, content = "lente")
  private val three = piece("gruppo", 252f, width = 132f)
  private val one = piece("gruppo", 340f)

  @Test fun `l'attributo arriva al pezzo e non conta per l'uguaglianza della geometria`() {
    val attributes = fluidGlassAttributesOf(Modifier.fluidGlassContentKey("lente"))
    assertEquals("lente", attributes.contentKey)
    assertNull(fluidGlassAttributesOf(Modifier.fluidGlassContentKey(null)).contentKey)
    assertTrue(planesAlike(state(list, three), state(lens, three)))
    assertTrue(samePieces(state(list, three), state(lens, three)))
  }

  @Test fun `un cambio solo di contenuto non parte, ma il riposo lo sa`() {
    val driver = FluidGlassPlaneDriver()
    driver.step(0L, state(list, three), GlassPlaneMotion())
    assertEquals(FluidGlassPlaneDriver.Change.Nothing, driver.step(frameNanos, state(lens, three), GlassPlaneMotion()))
    assertNull("nessun viaggio: il vetro non si muove", driver.journey)
    assertEquals("lente", driver.rest!!.pieces.first { it.key == "tondo" }.contentKey)
    // Il cambio di contesto dopo: il tondo non ha cambiato contenuto in questo viaggio, e resta fermo.
    assertEquals(FluidGlassPlaneDriver.Change.Started, driver.step(2 * frameNanos, state(lens, one), GlassPlaneMotion()))
    assertTrue(driver.journey!!.frame(200.0).pieces.single { it.flightId.targetKey == "tondo" }.still)
  }

  @Test fun `nello stesso cambio di contesto il tondo che cambia contenuto respira`() {
    val driver = FluidGlassPlaneDriver()
    driver.step(0L, state(list, three), GlassPlaneMotion())
    assertEquals(FluidGlassPlaneDriver.Change.Started, driver.step(frameNanos, state(lens, one), GlassPlaneMotion()))
    val round = driver.journey!!.frame(200.0).pieces.single { it.flightId.targetKey == "tondo" }
    assertFalse(round.still)
    assertEquals(FluidMotion.PlaneContentBreath, round.swell, 0.05f)
  }

  @Test fun `a viaggio avviato un cambio solo di contenuto non lo fa ripartire, e l'arrivo lo sa`() {
    val driver = FluidGlassPlaneDriver()
    driver.step(0L, state(list, three), GlassPlaneMotion())
    driver.step(frameNanos, state(list, one), GlassPlaneMotion())
    val journey = driver.journey
    assertEquals(FluidGlassPlaneDriver.Change.Moved, driver.step(2 * frameNanos, state(lens, one), GlassPlaneMotion()))
    assertSame(journey, driver.journey)
    assertEquals("lente", driver.rest!!.pieces.first { it.key == "tondo" }.contentKey)
  }

  @Test fun `senza un cambio di contenuto il riposo resta lo stesso oggetto`() {
    val rest = state(list, three)
    assertSame(rest, planeWithContentOf(rest, state(list, three)))
    assertSame(rest, planeWithContentOf(rest, state(list)))
  }
}
