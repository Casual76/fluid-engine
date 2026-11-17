package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.glass.field.GlassPlanePiece
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneState
import org.junit.Assert.*
import org.junit.Test

/** I numeri letti dal banco devono essere quelli del campo del dispositivo, anche lontano dalla capsula. */
class GlassPlaneTraceSmoothingTest {
  @Suppress("UNCHECKED_CAST")
  private fun uniforms(side: Float, smoothing: Float): Map<String, Any?> {
    val piece = GlassPlanePiece("panel", Rect(30f, 30f, 30f + side, 30f + side), FluidCornerRadii.all(20f), id = "panel")
    val state = GlassPlaneState(listOf(piece), 16f, Rect(0f, 0f, side + 60f, side + 60f))
    val trace = F38aTraces.build(F38aTraces.Scene("angolo", "parita della forma", side + 60f, side + 60f,
      false, state, emptyList(), emptyMap(), 0, density = 1f, firstFrame = 0, smoothing = smoothing))
    assertEquals(smoothing, (trace.head["_smoothing"] as Number).toFloat(), 0f)
    return (trace.frames.single().elements.single()["body"] as Map<String, Any?>)["uniforms"] as Map<String, Any?>
  }

  @Test fun aLargeContinuousCornerExportsItsRealSmoothing() {
    val continuous = uniforms(300f, ContinuousCornerShape.IosSmoothing)
    assertEquals(0.6f, ((continuous["pieceSmooth"] as List<*>).first() as Number).toFloat(), 0f)
    assertEquals(0f, ((uniforms(300f, 0f)["pieceSmooth"] as List<*>).first() as Number).toFloat(), 0f)
  }

  @Test fun aSmallContinuousCornerExportsTheEquivalentRadiusOfTheDevice() {
    fun radius(smoothing: Float): Float {
      val radii = (uniforms(100f, smoothing)["pieceRadii"] as List<*>).first() as List<*>
      return radii.first().toString().removeSuffix("dp").toFloat()
    }
    assertEquals(20f, radius(0f), 0.001f)
    assertEquals(16.4f, radius(ContinuousCornerShape.IosSmoothing), 0.001f)
  }
}
