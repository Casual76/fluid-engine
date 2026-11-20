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
uniform float clipInk;

half4 glassMenuContent(float2 xy) {
    float2 normal, center;
    float d = glassField(menuOrigin + xy * menuScale, normal, center);
    float edge = 1.0 - smoothstep(0.0, lensDepth, abs(d));
    float2 sampleAt = xy + normal * (edge * edge * lensPull);
    float coverage = clipInk > 0.5 ? clamp(0.5 - d, 0.0, 1.0) : 1.0;
    return content.eval(sampleAt) * half(coverage);
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

// La lente col tiro dei lati (S4f, NO 2): `lensOverflow` (sinistra, alto, destra, basso, nello spazio delle voci) e' quanto
// le voci escono dal vetro da quel lato, e il bordo le porta tutte dentro, schiacciate nella fascia della lente invece di
// tagliate. Due varianti a parte, usate solo quando le voci escono davvero: la lente di prima resta al bit, e con lei le tracce
// del banco che la usano. A tiro zero queste varianti disegnano come lei.
private const val GlassContentLensOverflowBlock = """
uniform float2 menuOrigin;
uniform float menuScale;
uniform float lensDepth;
uniform float lensPull;
uniform float4 lensOverflow;
uniform float clipInk;

half4 glassMenuContent(float2 xy) {
    float2 normal, center;
    float d = glassField(menuOrigin + xy * menuScale, normal, center);
    float edge = 1.0 - smoothstep(0.0, lensDepth, abs(d));
    float2 pull = float2(
        max(lensPull, normal.x < 0.0 ? lensOverflow.x : lensOverflow.z),
        max(lensPull, normal.y < 0.0 ? lensOverflow.y : lensOverflow.w));
    float2 sampleAt = xy + normal * (edge * edge * pull);
    float coverage = clipInk > 0.5 ? clamp(0.5 - d, 0.0, 1.0) : 1.0;
    return content.eval(sampleAt) * half(coverage);
}"""

@Language("AGSL")
internal const val GlassContentLensSlabOverflowShaderString = """
uniform shader content;
$GlassFieldKernel1
$GlassContentLensOverflowBlock
half4 main(float2 xy) { return glassMenuContent(xy); }
"""

@Language("AGSL")
internal const val GlassContentLensRingOverflowShaderString = """
uniform shader content;
$GlassFieldKernelRing
$GlassContentLensOverflowBlock
half4 main(float2 xy) { return glassMenuContent(xy); }
"""

/**
 * Le voci escono dal vetro di piu' di mezzo pixel da almeno un lato ([glassMenuLensOverflow]): allora la lente usa le varianti
 * col tiro dei lati.
 */
internal fun glassMenuLensPulls(overflow: FloatArray?): Boolean =
  overflow != null && overflow.size >= 4 && overflow.any { it > GlassMenuLensOverflowMin }

/** Sotto questa sporgenza (in px dello strato delle voci) la lente e' quella di prima. */
internal const val GlassMenuLensOverflowMin = 0.5f

/** L'effetto vive sul pannello. Il riquadro, la lente e il colore della pagina restano all'host. */
@RequiresApi(33)
internal class GlassContentLensEffect {
  private val shaderCache = RuntimeShaderCacheImpl()
  private val slab = menuShader(GlassShaderSources.MenuContentSlab)
  private val ring by lazy { menuShader(GlassShaderSources.MenuContentRing) }
  // Le varianti col tiro dei lati (S4f, NO 2), solo quando servono. Se il compilatore le rifiuta resta la lente di prima.
  private val slabOverflow by lazy { menuShader(GlassShaderSources.MenuContentSlabOverflow) }
  private val ringOverflow by lazy { menuShader(GlassShaderSources.MenuContentRingOverflow) }
  private val slabField = GlassFieldPlan(pieces = 1)
  private val ringField = GlassRingField()

  private fun menuShader(source: GlassShaderSource): RuntimeShader? =
    runCatching { shaderCache.obtainEngineShader(source.name, source) }.getOrNull()

  /**
   * Lo shader di questa sagoma ritaglia da se' le voci col `clipInk` (S4g, NO 1): allora il pannello non mette un ritaglio
   * sopra il loro strato. Falso dove [effect] non ha uno shader (piu' pezzi, nessuna sagoma) o il compilatore l'ha rifiutato.
   */
  fun clips(plan: PhysicsRenderPlan): Boolean = when {
    plan.mode == PlanModePoly && plan.vertCount > 2 -> ring != null
    plan.mode == PlanModeSlabs && plan.pieceCount == 1 -> slab != null
    else -> false
  }

  fun effect(
    plan: PhysicsRenderPlan,
    origin: Offset,
    scale: Float,
    density: Float,
    blurDp: Float,
    strength: Float,
    clipInk: Boolean = false,
    /** Quanto le voci escono dal vetro da ogni lato ([glassMenuLensOverflow]), o null: niente. */
    overflow: FloatArray? = null,
  ): androidx.compose.ui.graphics.RenderEffect? {
    val radius = blurDp * density
    val blur = if (radius >= 0.5f) {
      RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL)
    } else {
      null
    }
    val depth = glassMenuLensDepthPx(plan.bounds, density)
    val pulls = glassMenuLensPulls(overflow)
    val shader = when {
      strength <= 0f && !clipInk -> null
      plan.mode == PlanModePoly && plan.vertCount > 2 -> (if (pulls) ringOverflow ?: ring else ring)?.apply {
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
      plan.mode == PlanModeSlabs && plan.pieceCount == 1 -> (if (pulls) slabOverflow ?: slab else slab)?.apply {
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
    shader.setFloatUniform("clipInk", if (clipInk) 1f else 0f)
    shader.setFloatUniform("lensDepth", depth)
    shader.setFloatUniform("lensPull", glassMenuLensPullPx(depth, strength) / scale)
    // Le varianti col tiro dei lati, a ogni fotogramma (lo shader e' riusato).
    val o = overflow
    if (pulls && o != null && (shader === ringOverflow || shader === slabOverflow)) {
      shader.setFloatUniform("lensOverflow", o[0], o[1], o[2], o[3])
    }
    val lens = RuntimeShaderEffect(shader, "content").asAndroidRenderEffect()
    return (if (blur == null) lens else RenderEffect.createChainEffect(lens, blur)).asComposeRenderEffect()
  }
}

internal fun glassMenuLensDepthPx(bounds: Rect, density: Float): Float =
  (bounds.minDimension * 0.24f).coerceIn(2f * density, 38f * density)

internal fun glassMenuLensPullPx(depth: Float, strength: Float): Float = depth * 0.42f * strength

/**
 * Quanto lo strato delle voci (in [origin] nell'host, grande [size], scalato di [scale]) esce dal vetro [bounds] da ogni lato:
 * sinistra, alto, destra, basso, nelle unita' dello strato (lo spazio dello shader). Zero dove le voci stanno dentro.
 *
 * S4f sul Tab S9 (NO 2): nella nascita di «Altro» la lente ingrandisce le voci fino a 1,42× mentre la goccia e' ancora piu'
 * piccola del pannello, e il ritaglio della goccia tagliava le righe estreme («Condividi» ed «Elimina» a meta' a f0007, le
 * icone a destra a f0009). Con questo tiro il bordo delle voci cade sul bordo del vetro: le righe estreme entrano schiacciate
 * nella fascia della lente, come in B1 (a), dove «Recents» e «Tracking» arrivano compresse ai bordi (f9151–f9152).
 */
internal fun glassMenuLensOverflow(bounds: Rect, origin: Offset, size: Size, scale: Float): FloatArray {
  if (!(scale > 0f) || bounds.isEmpty || size.width <= 0f || size.height <= 0f) return FloatArray(4)
  val right = origin.x + size.width * scale
  val bottom = origin.y + size.height * scale
  return floatArrayOf(
    (bounds.left - origin.x).coerceAtLeast(0f) / scale,
    (bounds.top - origin.y).coerceAtLeast(0f) / scale,
    (right - bounds.right).coerceAtLeast(0f) / scale,
    (bottom - bounds.bottom).coerceAtLeast(0f) / scale,
  )
}

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
