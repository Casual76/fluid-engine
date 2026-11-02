package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.util.fastCoerceAtLeast
import dev.antigravity.fluidengine.ui.glass.backdrop.BackdropEffectScope
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.runtimeShaderEffect
import dev.antigravity.fluidengine.ui.glass.backdrop.isRuntimeShaderSupported

/**
 * Gli array di lavoro per gli uniform, uno per superficie: il piano è in pixel di layout e lo
 * shader vuole pixel di scope, e la conversione non deve allocare a ogni fotogramma.
 */
internal class PhysicsUniformScratch {
  val rects = FloatArray(PhysicsMaxPieces * 4)
  val radii = FloatArray(PhysicsMaxPieces * 4)
  val verts = FloatArray(PhysicsMaxVertices * 2)
}

/** Larghezza dell'antialias della maschera, in pixel di scope. */
private const val MaskAntialiasPx = 1.25f

/**
 * La profondità della lente come PESO (3.0): 0 è il bordo piatto, 1 la lente con profondità.
 *
 * Lo shader prendeva già un `float` (`grad + depthEffect * normalize(coord)`), era solo il Kotlin a
 * passare 0 o 1. Con un peso la profondità si sfuma lungo un viaggio fra due materiali — il menu che
 * parte da un tasto con profondità e arriva a un pannello senza — invece di accendersi di colpo a
 * metà strada. Chi passa un Boolean ottiene 0f / 1f, cioè esattamente quello di prima.
 */
internal fun Boolean.asDepthWeight(): Float = if (this) 1f else 0f

/**
 * Il valore che arriva all'uniform: tenuto fra 0 e 1 perché oltre non sarebbe più una miscela fra
 * la normale del bordo e quella radiale, ma una spinta che lo shader non ha mai avuto il compito di
 * reggere (un peso che sfora per un'interpolazione con overshoot non deve rovesciare la rifrazione).
 */
internal fun depthEffectUniform(weight: Float): Float = weight.coerceIn(0f, 1f)

/**
 * Il peso della frangia che arriva allo shader: fra 0 e 1, e zero sotto un millesimo (la sorgente
 * senza frangia costa un settimo dei campioni nella banda della lente) o se il driver ha rifiutato
 * la variante con la frangia — la lente resta, la frangia no.
 */
internal fun dispersionUniform(weight: Float): Float {
  if (!(weight > 0.001f)) return 0f
  if (!FluidPhysicsShaderHealth.dispersionHealthy()) return 0f
  return weight.coerceAtMost(1f)
}

/**
 * La lente di Fluid-physics per pezzi rettangolari (uno o molti, fusi con lo smin).
 *
 * Stessa disciplina di `lens()` (Lens.kt): salta sotto API 33 lasciando in piedi sfocatura e
 * vividezza già in catena, e sconta dal padding l'altezza di rifrazione — con un **padding che non
 * cambia mai durante il morph**, così la cattura non viene mai sporcata a metà viaggio (vedi il
 * contratto in DrawBackdropModifier).
 *
 * Blur e altezza, dalla regola della taglia (`glassSizedOptics`), crescono con la sagoma mentre
 * viaggia, quindi non sono piu' costanti: a restare ferma e' la loro *differenza*. La sfocatura non
 * supera mai l'altezza che la copre, e il padding (`max(0, sfocatura - altezza)`) vale zero a ogni
 * taglia. Chi chiama questa funzione con numeri propri deve rispettare lo stesso patto.
 *
 * A differenza della lente di serie, qui lo shader porta anche la **maschera** della silhouette
 * (il clip del layer è un rettangolo fisso) e, quando i pezzi sono più d'uno, la **tinta**: il
 * ponte dello smin è materiale che nessun path descrive, e una pellicola disegnata come path lo
 * lascerebbe nudo.
 */
internal fun BackdropEffectScope.slabGroupMorphLens(
  plan: PhysicsRenderPlan,
  scopeFactor: Float,
  scratch: PhysicsUniformScratch,
  refractionHeightPx: Float,
  refractionAmountPx: Float,
  depthEffect: Float,
  tintInShader: Color,
  /**
   * Il peso della frangia cromatica, 0..1 (3.0): quella di `glassSurface` per le ottiche che la
   * chiedono. A zero — o se il driver ha rifiutato la variante — si usa la sorgente di sempre.
   */
  chromaticAberration: Float = 0f,
) {
  if (!isRuntimeShaderSupported()) return

  val height = refractionHeightPx.fastCoerceAtLeast(0.0001f)
  if (padding > 0f) {
    padding = physicsLensPadding(padding, height)
  }

  val count = plan.pieceCount.coerceIn(1, PhysicsMaxPieces)
  for (i in 0 until count * 4) {
    scratch.rects[i] = plan.pieceRects[i] * scopeFactor
    scratch.radii[i] = plan.pieceRadii[i] * scopeFactor
  }
  // Gli slot oltre pieceCount non vengono letti dal loop, ma l'array uniform va riempito per
  // intero: un residuo del fotogramma prima non deve poter diventare un pezzo fantasma.
  for (i in count * 4 until scratch.rects.size) {
    scratch.rects[i] = 0f
    scratch.radii[i] = 0f
  }

  val currentPadding = padding
  val chroma = dispersionUniform(chromaticAberration)
  if (chroma > 0f) {
    runtimeShaderEffect("FluidPhysicsSlabGroupDispersion", SlabGroupDispersionShaderString, "content") {
      setFloatUniform("offset", -currentPadding, -currentPadding)
      setFloatUniform("pieceCount", count.toFloat())
      setFloatUniform("pieceRect", scratch.rects)
      setFloatUniform("pieceRadii", scratch.radii)
      setFloatUniform("blendRadius", plan.blendRadius * scopeFactor)
      setFloatUniform("refractionHeight", height)
      setFloatUniform("refractionAmount", -refractionAmountPx)
      setFloatUniform("depthEffect", depthEffectUniform(depthEffect))
      setFloatUniform(
        "fieldCenter",
        plan.bounds.center.x * scopeFactor,
        plan.bounds.center.y * scopeFactor,
      )
      setFloatUniform(
        "fieldHalfSize",
        plan.bounds.width / 2f * scopeFactor,
        plan.bounds.height / 2f * scopeFactor,
      )
      setFloatUniform("chromaticAberration", chroma)
      setFloatUniform("aa", MaskAntialiasPx)
      setColorUniform("tintColor", tintInShader)
    }
    return
  }
  runtimeShaderEffect("FluidPhysicsSlabGroup", SlabGroupRefractionShaderString, "content") {
    setFloatUniform("offset", -currentPadding, -currentPadding)
    setFloatUniform("pieceCount", count.toFloat())
    setFloatUniform("pieceRect", scratch.rects)
    setFloatUniform("pieceRadii", scratch.radii)
    setFloatUniform("blendRadius", plan.blendRadius * scopeFactor)
    setFloatUniform("refractionHeight", height)
    setFloatUniform("refractionAmount", -refractionAmountPx)
    setFloatUniform("depthEffect", depthEffectUniform(depthEffect))
    setFloatUniform(
      "fieldCenter",
      plan.bounds.center.x * scopeFactor,
      plan.bounds.center.y * scopeFactor,
    )
    setFloatUniform("aa", MaskAntialiasPx)
    setColorUniform("tintColor", tintInShader)
  }
}

/**
 * La stessa lente con la profondità come interruttore: lo era prima della 3.0 e i chiamanti che
 * hanno ancora un `GlassOptics.depthEffect` booleano restano com'erano (0f / 1f, nessun cambio di
 * resa). Il peso fra 0 e 1 è l'overload sopra.
 */
internal fun BackdropEffectScope.slabGroupMorphLens(
  plan: PhysicsRenderPlan,
  scopeFactor: Float,
  scratch: PhysicsUniformScratch,
  refractionHeightPx: Float,
  refractionAmountPx: Float,
  depthEffect: Boolean,
  tintInShader: Color,
) {
  slabGroupMorphLens(
    plan, scopeFactor, scratch, refractionHeightPx, refractionAmountPx, depthEffect.asDepthWeight(), tintInShader,
  )
}

/** La lente di Fluid-physics per una sagoma libera: stesso contratto di quella sopra. */
internal fun BackdropEffectScope.polyMorphLens(
  plan: PhysicsRenderPlan,
  scopeFactor: Float,
  scratch: PhysicsUniformScratch,
  refractionHeightPx: Float,
  refractionAmountPx: Float,
  depthEffect: Float,
  tintInShader: Color,
  /** Il peso della frangia cromatica: vedi [slabGroupMorphLens]. */
  chromaticAberration: Float = 0f,
) {
  if (!isRuntimeShaderSupported()) return

  val height = refractionHeightPx.fastCoerceAtLeast(0.0001f)
  if (padding > 0f) {
    padding = physicsLensPadding(padding, height)
  }

  val count = plan.vertCount.coerceIn(3, PhysicsMaxVertices)
  for (i in 0 until count * 2) {
    scratch.verts[i] = plan.verts[i] * scopeFactor
  }
  for (i in count * 2 until scratch.verts.size) {
    scratch.verts[i] = 0f
  }

  val currentPadding = padding
  val chroma = dispersionUniform(chromaticAberration)
  if (chroma > 0f) {
    runtimeShaderEffect("FluidPhysicsPolyDispersion", PolyMorphDispersionShaderString, "content") {
      setFloatUniform("offset", -currentPadding, -currentPadding)
      setFloatUniform("vertCount", count.toFloat())
      setFloatUniform("verts", scratch.verts)
      setFloatUniform("soften", plan.soften * scopeFactor)
      setFloatUniform("refractionHeight", height)
      setFloatUniform("refractionAmount", -refractionAmountPx)
      setFloatUniform("depthEffect", depthEffectUniform(depthEffect))
      setFloatUniform(
        "fieldCenter",
        plan.bounds.center.x * scopeFactor,
        plan.bounds.center.y * scopeFactor,
      )
      setFloatUniform(
        "fieldHalfSize",
        plan.bounds.width / 2f * scopeFactor,
        plan.bounds.height / 2f * scopeFactor,
      )
      setFloatUniform("chromaticAberration", chroma)
      setFloatUniform("aa", MaskAntialiasPx)
      setColorUniform("tintColor", tintInShader)
    }
    return
  }
  runtimeShaderEffect("FluidPhysicsPoly", PolyMorphRefractionShaderString, "content") {
    setFloatUniform("offset", -currentPadding, -currentPadding)
    setFloatUniform("vertCount", count.toFloat())
    setFloatUniform("verts", scratch.verts)
    setFloatUniform("soften", plan.soften * scopeFactor)
    setFloatUniform("refractionHeight", height)
    setFloatUniform("refractionAmount", -refractionAmountPx)
    setFloatUniform("depthEffect", depthEffectUniform(depthEffect))
    setFloatUniform(
      "fieldCenter",
      plan.bounds.center.x * scopeFactor,
      plan.bounds.center.y * scopeFactor,
    )
    setFloatUniform("aa", MaskAntialiasPx)
    setColorUniform("tintColor", tintInShader)
  }
}

/** La lente per sagoma libera con la profondità come interruttore: vedi l'overload a peso sopra. */
internal fun BackdropEffectScope.polyMorphLens(
  plan: PhysicsRenderPlan,
  scopeFactor: Float,
  scratch: PhysicsUniformScratch,
  refractionHeightPx: Float,
  refractionAmountPx: Float,
  depthEffect: Boolean,
  tintInShader: Color,
) {
  polyMorphLens(
    plan, scopeFactor, scratch, refractionHeightPx, refractionAmountPx, depthEffect.asDepthWeight(), tintInShader,
  )
}
