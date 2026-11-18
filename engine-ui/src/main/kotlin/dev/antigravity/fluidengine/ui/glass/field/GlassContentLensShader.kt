package dev.antigravity.fluidengine.ui.glass.field

import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import dev.antigravity.fluidengine.ui.fluid.FluidMenuJourney
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.fluidMenuSpotAmount
import dev.antigravity.fluidengine.ui.fluid.fluidMenuSpotCenter
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsRenderPlan
import dev.antigravity.fluidengine.ui.fluidphysics.PlanModePoly
import dev.antigravity.fluidengine.ui.fluidphysics.PlanModeSlabs
import dev.antigravity.fluidengine.ui.fluidphysics.physicsFieldPlan
import dev.antigravity.fluidengine.ui.fluidphysics.physicsRingField
import dev.antigravity.fluidengine.ui.fluidphysics.fluidPhysicsClip
import dev.antigravity.fluidengine.ui.glass.backdrop.RuntimeShader
import dev.antigravity.fluidengine.ui.glass.backdrop.RuntimeShaderCacheImpl
import dev.antigravity.fluidengine.ui.glass.backdrop.asComposeShader
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.RuntimeShaderEffect
import dev.antigravity.fluidengine.ui.glass.backdrop.obtainEngineShader
import org.intellij.lang.annotations.Language

// Si valuta il campo della finestra in pixel dell'host. Si torna poi nello spazio
// della texture delle voci. Una sola lettura, anche fuori dal bordo della lente.
private const val GlassContentLensBlock = """
uniform float2 menuOrigin;
uniform float menuScale;
uniform float lensDepth;
uniform float lensPull;

half4 glassMenuContent(float2 xy) {
    float2 normal, center;
    float d = glassField(menuOrigin + xy * menuScale, normal, center);
    float edge = 1.0 - smoothstep(0.0, lensDepth, abs(d));
    float2 sampleAt = xy + normal * (edge * edge * lensPull);
    return content.eval(sampleAt);
}"""

@Language("AGSL")
internal const val GlassContentLensSlabShaderString = """
uniform shader content;
$GlassFieldKernel1
$GlassContentLensBlock
half4 main(float2 xy) { return glassMenuContent(xy); }
"""

@Language("AGSL")
internal const val GlassContentLensRingShaderString = """
uniform shader content;
$GlassFieldKernelRing
$GlassContentLensBlock
half4 main(float2 xy) { return glassMenuContent(xy); }
"""

/** L'effetto vive sul pannello. Il riquadro, la lente e il colore della pagina restano all'host. */
@RequiresApi(33)
internal class GlassContentLensEffect {
  private val shaderCache = RuntimeShaderCacheImpl()
  private val slab = menuShader(GlassShaderSources.MenuContentSlab)
  private val ring by lazy { menuShader(GlassShaderSources.MenuContentRing) }
  private val slabField = GlassFieldPlan(pieces = 1)
  private val ringField = GlassRingField()

  private fun menuShader(source: GlassShaderSource): RuntimeShader? =
    runCatching { shaderCache.obtainEngineShader(source.name, source) }.getOrNull()

  fun effect(
    plan: PhysicsRenderPlan,
    origin: Offset,
    scale: Float,
    density: Float,
    blurDp: Float,
    strength: Float,
  ): androidx.compose.ui.graphics.RenderEffect? {
    val radius = blurDp * density
    val blur = if (radius >= 0.5f) {
      RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL)
    } else {
      null
    }
    val depth = glassMenuLensDepthPx(plan.bounds, density)
    val shader = when {
      strength <= 0f -> null
      plan.mode == PlanModePoly && plan.vertCount > 2 -> ring?.apply {
        val field = physicsRingField(plan, 1f, density, depth, depth, ringField)
        setFloatUniform("vertCount", field.count.toFloat())
        setFloatUniform("verts", field.verts)
        setFloatUniform("bbox", field.bbox)
        setFloatUniform("normalSmooth", field.normalSmooth)
        setFloatUniform("soften", field.soften)
        setFloatUniform("fieldCenter", field.centerX, field.centerY)
        setFloatUniform("restCenter", field.restCenterX, field.restCenterY)
        setFloatUniform("restHalfSize", field.restHalfWidth, field.restHalfHeight)
        setFloatUniform("restRadii", field.restRadii)
        setFloatUniform("restSmooth", field.restSmooth)
        setFloatUniform("restBlend", field.restBlend)
        setFloatUniform("dropReach", field.dropReach)
        setFloatUniform("ringBand", field.bandOuter, field.bandInner)
      }
      plan.mode == PlanModeSlabs && plan.pieceCount == 1 -> slab?.apply {
        val field = physicsFieldPlan(plan, 1f, slabField)
        setFloatUniform("pieceRect", field.rects)
        setFloatUniform("pieceRadii", field.radii)
        setFloatUniform("pieceSmooth", field.smooth)
      }
      else -> null
    }
    if (shader == null) return blur?.asComposeRenderEffect()
    shader.setFloatUniform("menuOrigin", origin.x, origin.y)
    shader.setFloatUniform("menuScale", scale)
    shader.setFloatUniform("lensDepth", depth)
    shader.setFloatUniform("lensPull", glassMenuLensPullPx(depth, strength) / scale)
    val lens = RuntimeShaderEffect(shader, "content").asAndroidRenderEffect()
    return (if (blur == null) lens else RenderEffect.createChainEffect(lens, blur)).asComposeRenderEffect()
  }
}

internal fun glassMenuLensDepthPx(bounds: Rect, density: Float): Float =
  (bounds.minDimension * 0.24f).coerceIn(2f * density, 38f * density)

internal fun glassMenuLensPullPx(depth: Float, strength: Float): Float = depth * 0.42f * strength

/** Lo stesso bagliore additivo della luce del vetro, sul suo contorno in questo fotogramma. */
@RequiresApi(33)
internal fun Modifier.glassMenuPointOfLight(journey: FluidMenuJourney, physics: FluidPhysicsState): Modifier =
  this
    .fluidPhysicsClip(physics) { !journey.closing && fluidMenuSpotAmount(journey.progress.value) > 0f }
    .drawWithCache {
      val shader = runCatching {
        RuntimeShaderCacheImpl().obtainEngineShader(GlassShaderSources.Light1.name, GlassShaderSources.Light1)
      }.getOrNull()
      val brush = shader?.let { ShaderBrush(it.asComposeShader()) }
      val light = GlassDrawInputs(pieces = 1)
      // Il ritaglio esterno è quello della goccia. Alla luce diamo una lastra che include il suo
      // riquadro, per applicare solo la campana additiva senza un secondo rilievo o un altro bordo.
      light.light.setSlab(size.width, size.height, GlassSlabGeometry())
      light.drawsGlowing = true
      light.lightMaterialize = 1f
      onDrawBehind {
        val amount = if (journey.closing) 0f else fluidMenuSpotAmount(journey.progress.value)
        if (amount > 0f && shader != null && brush != null) {
          val body = physics.ensurePlan().bounds
          if (!body.isEmpty) {
            val center = fluidMenuSpotCenter(body, journey.startFrame().center, journey.to.center)
            light.glowCenterX = center.x
            light.glowCenterY = center.y
            light.glowRadius = body.minDimension * FluidMotion.MenuSpotRadius
            light.glow = amount
            drawGlassLight(light, shader, brush, body.topLeft, Size(body.width, body.height))
          }
        }
      }
    }
