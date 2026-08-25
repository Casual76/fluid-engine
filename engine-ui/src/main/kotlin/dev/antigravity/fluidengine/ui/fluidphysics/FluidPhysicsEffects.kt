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
 * La lente di Fluid-physics per pezzi rettangolari (uno o molti, fusi con lo smin).
 *
 * Stessa disciplina di `lens()` (Lens.kt): salta sotto API 33 lasciando in piedi sfocatura e
 * vividezza già in catena, e sconta dal padding l'altezza di rifrazione — con blur e altezza
 * **costanti durante il morph**, così il padding non cambia mai a metà viaggio e la cattura non
 * viene mai sporcata (vedi il contratto in DrawBackdropModifier).
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
  tintInShader: Color,
) {
  if (!isRuntimeShaderSupported()) return

  val height = refractionHeightPx.fastCoerceAtLeast(0.0001f)
  if (padding > 0f) {
    padding = (padding - height).fastCoerceAtLeast(0f)
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
  runtimeShaderEffect("FluidPhysicsSlabGroup", SlabGroupRefractionShaderString, "content") {
    setFloatUniform("offset", -currentPadding, -currentPadding)
    setFloatUniform("pieceCount", count.toFloat())
    setFloatUniform("pieceRect", scratch.rects)
    setFloatUniform("pieceRadii", scratch.radii)
    setFloatUniform("blendRadius", plan.blendRadius * scopeFactor)
    setFloatUniform("refractionHeight", height)
    setFloatUniform("refractionAmount", -refractionAmountPx)
    setFloatUniform("aa", MaskAntialiasPx)
    setColorUniform("tintColor", tintInShader)
  }
}

/** La lente di Fluid-physics per una sagoma libera: stesso contratto di quella sopra. */
internal fun BackdropEffectScope.polyMorphLens(
  plan: PhysicsRenderPlan,
  scopeFactor: Float,
  scratch: PhysicsUniformScratch,
  refractionHeightPx: Float,
  refractionAmountPx: Float,
  tintInShader: Color,
) {
  if (!isRuntimeShaderSupported()) return

  val height = refractionHeightPx.fastCoerceAtLeast(0.0001f)
  if (padding > 0f) {
    padding = (padding - height).fastCoerceAtLeast(0f)
  }

  val count = plan.vertCount.coerceIn(3, PhysicsMaxVertices)
  for (i in 0 until count * 2) {
    scratch.verts[i] = plan.verts[i] * scopeFactor
  }
  for (i in count * 2 until scratch.verts.size) {
    scratch.verts[i] = 0f
  }

  val currentPadding = padding
  runtimeShaderEffect("FluidPhysicsPoly", PolyMorphRefractionShaderString, "content") {
    setFloatUniform("offset", -currentPadding, -currentPadding)
    setFloatUniform("vertCount", count.toFloat())
    setFloatUniform("verts", scratch.verts)
    setFloatUniform("soften", plan.soften * scopeFactor)
    setFloatUniform("refractionHeight", height)
    setFloatUniform("refractionAmount", -refractionAmountPx)
    setFloatUniform("aa", MaskAntialiasPx)
    setColorUniform("tintColor", tintInShader)
  }
}
