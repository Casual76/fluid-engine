package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.geometry.Offset
import dev.antigravity.fluidengine.ui.fluid.FluidMenuJourney
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.fluidMenuLensStrength
import dev.antigravity.fluidengine.ui.fluid.fluidMenuSpotCenter
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsRenderPlan
import dev.antigravity.fluidengine.ui.fluidphysics.PlanModePoly
import dev.antigravity.fluidengine.ui.fluidphysics.physicsRingField
import dev.antigravity.fluidengine.ui.glass.field.GlassRingField
import dev.antigravity.fluidengine.ui.glass.field.glassMenuLensDepthPx
import dev.antigravity.fluidengine.ui.glass.field.glassMenuLensPullPx
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun menuTraceContentLens(
  plan: PhysicsRenderPlan,
  origin: Offset,
  density: Float,
  focus: Float,
  clipInk: Boolean = false,
): Map<String, Any?>? {
  val strength = fluidMenuLensStrength(focus)
  if (strength <= 0f && !clipInk) return null
  val d = density
  val depth = glassMenuLensDepthPx(plan.bounds, d)
  fun dp(x: Float) = glassDp(x / d)
  fun coords(x: Float, y: Float) = listOf(dp(x), dp(y))
  // Le voci della traccia sono già posate nella tela. Per questo la scala del campo è 1.
  val u = linkedMapOf<String, Any?>(
    "offset" to listOf(0, 0), "flex" to listOf(1, 0, 0, 1), "flexCenter" to listOf(0, 0),
    "menuOrigin" to coords(origin.x, origin.y), "menuScale" to 1,
    "lensDepth" to dp(depth), "lensPull" to dp(glassMenuLensPullPx(depth, strength)),
  )
  if (clipInk) u["clipInk"] = 1
  val kernel = if (plan.mode == PlanModePoly) {
    val f = physicsRingField(plan, 1f, d, depth, depth, GlassRingField())
    u["vertCount"] = f.count
    u["verts"] = f.verts.map(::dp)
    u["bbox"] = f.bbox.map(::dp)
    u["normalSmooth"] = f.normalSmooth
    u["soften"] = dp(f.soften)
    u["fieldCenter"] = coords(f.centerX, f.centerY)
    u["restCenter"] = coords(f.restCenterX, f.restCenterY)
    u["restHalfSize"] = coords(f.restHalfWidth, f.restHalfHeight)
    u["restRadii"] = f.restRadii.map(::dp)
    u["restSmooth"] = f.restSmooth
    u["restBlend"] = f.restBlend
    u["dropReach"] = dp(f.dropReach)
    u["ringBand"] = coords(f.bandOuter, f.bandInner)
    "GlassContentLensRingShaderString"
  } else {
    u["pieceCount"] = 1
    u["pieceRect"] = plan.pieceRects.take(4).map(::dp)
    u["pieceRadii"] = plan.fieldRadii.take(4).map(::dp)
    u["pieceBlend"] = listOf(0)
    u["pieceSmooth"] = plan.fieldSmooth.take(1)
    "GlassContentLensSlabShaderString"
  }
  return linkedMapOf("source" to "kotlin:$kernel", "uniforms" to u)
}

@Suppress("UNCHECKED_CAST")
internal fun menuTraceSpot(
  element: Map<String, Any?>,
  plan: PhysicsRenderPlan,
  journey: FluidMenuJourney,
  origin: Offset,
  density: Float,
  amount: Float,
) {
  if (amount <= 0f) return
  val body = element["body"] as? Map<String, Any?> ?: return
  val u = body["uniforms"] as? MutableMap<String, Any?> ?: return
  val center = fluidMenuSpotCenter(plan.bounds, journey.startFrame().center, journey.to.center) - origin
  u["glow"] = amount
  u["glowCenter"] = listOf(glassDp(center.x / density), glassDp(center.y / density))
  u["glowRadius"] = glassDp(plan.bounds.minDimension * FluidMotion.MenuSpotRadius / density)
  u["glowFill"] = 0f
}

class F5a2MenuExportTest {
  @Test
  fun openingClosingAndInterruptionUseTheSameFieldAsTheMenu() {
    val variants = linkedMapOf(
      "scelta-tasto" to MenuTraceVariant(pointOfLight = true),
      "scelta-gruppo" to MenuTraceVariant(grouped = true, pointOfLight = true),
      "scelta-chiusura-tasto" to MenuTraceVariant(closingAtMs = 700, alignToClose = true),
      "scelta-chiusura-gruppo" to MenuTraceVariant(grouped = true, closingAtMs = 700, alignToClose = true),
      "scelta-interruzione" to MenuTraceVariant(closingAtMs = 117, pointOfLight = true),
    )
    for ((name, variant) in variants) {
      val built = buildMenuTrace(
        OggiMenuApertura.scene(density = 1.5f), dress = TraceDress.Liquid, name = name,
        phase = "f5a-2", unified = true, variant = variant,
      )
      val trace = built.trace
      assertTrue("$name: goccia", built.ringFrames > 0)
      assertTrue("$name: la simulazione ha integrato", built.integratedFrames > 0)
      assertTrue("$name: piani e uniform serializzabili", trace.encode().isNotEmpty())
      if (variant.threePhase && !variant.alignToClose) {
        val first = trace.frames.first { it.tMs >= 0.0 }.elements.first { it["name"] == "menu" }
        val content = first["content"] as List<*>
        assertTrue("$name: inchiostro e lente al primo fotogramma", content.filterIsInstance<Map<*, *>>()
          .any { it["name"] == "voci" && it["effect"] != null && (it["alpha"] as Float) > 0f })
      }
      GlassTraceOutput.writeIfRequested(trace)
    }
  }
}
