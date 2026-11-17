package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.glass.field.*
import org.junit.Assert.*
import org.junit.Test

/** La tinta viene campionata dal viaggio vero, indipendentemente dalla distanza delle lastre. */
class FluidGlassTintJourneyTest {
  private val neutral = GlassTint(Color.Transparent, Color.White, Color.Transparent, GlassTintMode.Neutral)
  private val blue = fluidGlassTint(FluidGlass.Regular.tint(Color(0xFF007AFF)), neutral)
  private fun piece(key: String, x: Float, width: Float) = GlassPlanePiece(key, Rect(x, 40f, x + width, 84f),
    FluidCornerRadii.all(22f), id = key)
  private fun state(vararg pieces: GlassPlanePiece) = GlassPlaneState(pieces.toList(), 16f, Rect(0f, 0f, 400f, 140f))
  private val group = state(piece("group", 20f, 88f))
  private val split = state(piece("group", 20f, 44f), piece("primary", 76f, 44f))
  private fun tints(state: GlassPlaneState) = state.pieces.associate { it.key to if (it.key == "primary") blue else neutral }
  private fun start(from: GlassPlaneState, to: GlassPlaneState): Pair<GlassPlaneJourney, FluidGlassTintJourney> {
    val journey = GlassPlaneJourney(from, to)
    val tint = FluidGlassTintJourney()
    tint.rest(tints(from))
    tint.start(journey.frame(0.0), tints(to))
    return journey to tint
  }

  @Test fun aPrimaryIsNeutralWhileItStillCoincidesWithItsDonor() {
    val (journey, tint) = start(group, split)
    assertTrue(tint.frame(journey.frame(0.0)).values.all { it == neutral })
    assertEquals(blue, tint.frame(journey.frame(journey.durationMs)).entries.single { it.key.targetKey == "primary" }.value)
  }

  @Test fun aMergingPrimaryLosesItsTintInsideItsContentBlur() {
    val (journey, tint) = start(split, group)
    var mixed = 0
    var coincident = 0
    for (ms in 0..journey.durationMs.toInt() step 3) {
      val frame = journey.frame(ms.toDouble())
      val merging = frame.pieces.single { it.change == GlassPlaneChange.MergeIntoNeighbour }
      val colors = tint.frame(frame)
      val color = colors.getValue(merging.flightId)
      if (color != neutral && color != blue) {
        val role = glassPieceContentFor(GlassPieceArrival.MergesIntoNeighbour, GlassContentLook.Sharp, departed = true)
        assertTrue("t=$ms: tinta solo mentre il suo simbolo sfoca", glassPieceContentLook(role, merging.contentPhase).blur > 0.04f)
        mixed++
      }
      val host = frame.pieces.single { it.flightId.targetKey == "group" }
      if ((merging.bounds.center - host.bounds.center).getDistance() < 0.5f &&
        kotlin.math.abs(merging.bounds.width - host.bounds.width) < 1f) {
        assertEquals("t=$ms: nessuna mezza capsula tinta", colors[host.flightId], color)
        coincident++
      }
    }
    assertTrue(mixed > 3)
    assertTrue(coincident > 10)
  }

  @Test fun aBornPrimaryGainsItsTintWhileTheNewContentIsBlurred() {
    val (journey, tint) = start(group, split)
    var mixed = 0
    for (ms in 0..journey.durationMs.toInt() step 3) {
      val frame = journey.frame(ms.toDouble())
      val born = frame.pieces.single { it.flightId.targetKey == "primary" }
      val color = tint.frame(frame).getValue(born.flightId)
      if (color != neutral && color != blue) {
        assertTrue(glassPieceContentLook(GlassPieceContent(GlassPieceContentRole.Born), born.contentPhase).blur > 0.04f)
        mixed++
      }
    }
    assertTrue(mixed > 3)
  }

  @Test fun anInterruptedTintStartsAtTheColorAlreadyShown() {
    val (journey, tint) = start(split, group)
    val at = journey.frame(110.0)
    val before = tint.frame(at).entries.single { it.key.sourceKey == "primary" }.value
    assertNotEquals(neutral, before)
    assertNotEquals(blue, before)
    val next = journey.retarget(110.0, split)
    tint.start(next.frame(0.0), tints(split))
    assertEquals(before, tint.frame(next.frame(0.0)).entries.single { it.key.targetKey == "primary" }.value)
  }

  @Test fun aRelayHasOneTintForBothCoincidentFlights() {
    val from = state(piece("primary", 20f, 88f))
    val to = state(piece("other", 20f, 88f))
    val (journey, tint) = start(from, to)
    assertTrue(journey.frame(0.0).pieces.all { it.relay })
    for (ms in 0..journey.durationMs.toInt() step 4) {
      assertEquals("t=$ms", 1, tint.frame(journey.frame(ms.toDouble())).values.distinct().size)
    }
  }

  @Test fun mixingTheTintKeepsTheNeutralVeilAndExactEndpoints() {
    assertSame(neutral, fluidGlassMixTint(neutral, blue, 0f))
    assertSame(blue, fluidGlassMixTint(neutral, blue, 1f))
    val middle = fluidGlassMixTint(neutral, blue, 0.5f)
    assertEquals(GlassTintMode.Colored, glassTintLiquidMode(middle))
    assertEquals(0.5f, middle.overlay.alpha, 0.01f)
  }
}
