package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlabParamLerpTest {

  private val circle = FluidForm.circle(center = androidx.compose.ui.geometry.Offset(100f, 100f), radius = 50f)
  private val square = FluidForm.Slab(
    frame = Rect(300f, 300f, 500f, 500f),
    cornerRadii = FluidCornerRadii.all(12f),
  )

  @Test
  fun theJourneyIsExactAtBothEnds() {
    val atStart = lerpSlab(circle, square, 0f)
    assertEquals(circle.frame.left, atStart[0], 0f)
    assertEquals(circle.frame.top, atStart[1], 0f)
    assertEquals(circle.frame.width, atStart[2], 0f)
    assertEquals(50f, atStart[4], 0f)

    val atEnd = lerpSlab(circle, square, 1f)
    assertEquals(square.frame.left, atEnd[0], 0f)
    assertEquals(square.frame.width, atEnd[2], 0f)
    assertEquals(12f, atEnd[4], 0f)
  }

  @Test
  fun radiiNeverOutgrowHalfTheShortSideMidJourney() {
    // Un cerchio piccolo verso un rettangolo basso e largo: a metà strada il raggio interpolato
    // supererebbe la mezza altezza, e il campo di distanza disegnerebbe un vetro impossibile.
    val small = FluidForm.circle(androidx.compose.ui.geometry.Offset(0f, 0f), radius = 80f)
    val flat = FluidForm.Slab(Rect(0f, 0f, 400f, 60f), FluidCornerRadii.all(0f))
    for (step in 0..10) {
      val t = step / 10f
      val v = lerpSlab(small, flat, t)
      val half = minOf(v[2], v[3]) / 2f
      for (r in 4..7) {
        assertTrue("raggio oltre il mezzo lato a t=$t", v[r] <= half + 0.001f)
        assertTrue("raggio negativo a t=$t", v[r] >= 0f)
      }
    }
  }

  @Test
  fun overshootStretchesTheFrameButNeverBreaksThePhysics() {
    // Le molle della casa oltrepassano l'arrivo: la geometria deve estrapolare senza degenerare.
    val v = lerpSlab(circle, square, 1.08f)
    assertTrue(v[2] >= MinSlabSidePx)
    assertTrue(v[3] >= MinSlabSidePx)
    val half = minOf(v[2], v[3]) / 2f
    for (r in 4..7) assertTrue(v[r] in 0f..half + 0.001f)
  }

  @Test
  fun aCapsuleEndpointArrivesAsATrueCapsule() {
    val capsule = FluidFormPresets.capsule(Rect(0f, 0f, 300f, 100f))
    val v = lerpSlab(circle, capsule, 1f)
    // Raggio == mezza altezza: la capsula è una capsula, non una squircle accidentale.
    assertEquals(50f, v[4], 0.001f)
    assertEquals(50f, v[7], 0.001f)
  }

  @Test
  fun aShrinkingJourneyNeverCollapsesToNothing() {
    val point = FluidForm.Slab(Rect(100f, 100f, 100.5f, 100.5f), FluidCornerRadii.Zero)
    val v = lerpSlab(circle, point, 1f)
    assertTrue(v[2] >= MinSlabSidePx)
    assertTrue(v[3] >= MinSlabSidePx)
  }
}
