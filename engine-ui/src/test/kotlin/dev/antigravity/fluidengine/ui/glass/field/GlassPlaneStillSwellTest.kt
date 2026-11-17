package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import org.junit.Assert.*
import org.junit.Test

/** f3-rif2: misura le lastre disegnate, compreso il rigonfiamento che la geometria del viaggio non contiene. */
class GlassPlaneStillSwellTest {
  private val back = GlassPlanePiece("back", Rect(4f, 4f, 98f, 98f), FluidCornerRadii.all(47f), id = "back")
  private fun label(width: Float, left: Float = 111f) = GlassPlanePiece("label", Rect(left, 0f, left + width, 102f),
    FluidCornerRadii.all(51f), id = "label")
  private fun state(vararg pieces: GlassPlanePiece) = GlassPlaneState(pieces.toList(), 34f, Rect(0f, 0f, 900f, 160f))
  private val swell = FluidGlassSwell(GlassLiquidProfile.Default.journey, 1f, 20f, 2.125f)

  private fun checkStill(journey: GlassPlaneJourney, together: Boolean) {
    assertEquals(together, journey.fields.any { field -> field.flights.size == 2 })
    for (ms in 0..journey.durationMs.toInt() step 4) {
      val frame = journey.frame(ms.toDouble())
      val piece = frame.pieces.single { it.flightId.targetKey == "back" }
      assertEquals("t=$ms: quota comune", 0f, piece.swellShare, 0f)
      assertEquals(0f, piece.swell, 0f)
      val shape = fluidGlassStagePlane(frame, listOf(piece.flightId), Offset.Zero, { ContinuousCornerShape.IosSmoothing },
        34f, 1f, swell, 2.125f, 200f)
      assertEquals("t=$ms: il tondo disegnato resta della sua misura", back.bounds, shape.slabs.single().frame)
      assertEquals(back.radii, shape.slabs.single().cornerRadii)
    }
  }

  @Test fun theTextSceneDoesNotInflateTheStillBack() = checkStill(
    GlassPlaneJourney(state(back, label(504f)), state(back, label(180f))), together = false)

  @Test fun aStillNeighbourInsideTheMovingFieldDoesNotInflate() = checkStill(
    GlassPlaneJourney(state(back, label(180f)), state(back, label(180f, 105f))), together = true)

  @Test fun aStillPieceStaysStillAcrossAnInterruption() {
    val journey = GlassPlaneJourney(state(back, label(504f)), state(back, label(180f)))
    val next = journey.retarget(190.0, state(back, label(504f)), commonSwell = 0.9f)
    assertTrue("back" in next.stillKeys)
    checkStill(next, together = false)
  }

  @Test fun aMovingPieceStillReceivesTheCommonSwell() {
    val journey = GlassPlaneJourney(state(label(180f)), state(label(180f, 180f)))
    assertEquals(1f, journey.frame(100.0).pieces.single().swellShare, 0f)
  }

  @Test fun aPieceChangingContentStillBreathesWithTheResize() {
    val journey = GlassPlaneJourney(state(back.copy(contentKey = "a"), label(504f)),
      state(back.copy(contentKey = "b"), label(180f)))
    val piece = journey.frame(200.0).pieces.single { it.flightId.targetKey == "back" }
    assertEquals(FluidMotion.PlaneContentBreath, piece.swell, 0.04f)
    assertTrue(swell.growPx(piece.bounds.minDimension, piece.swell, piece.swellShare) > 0f)
  }
}
