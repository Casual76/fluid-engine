package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import dev.antigravity.fluidengine.ui.fluid.FluidGlassBlurFloor
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassOptics
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassQuality
import dev.antigravity.fluidengine.ui.fluid.clampGlassUnit
import dev.antigravity.fluidengine.ui.fluid.glassResolutionScale
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.glass.backdrop.BackdropEffectScope
import dev.antigravity.fluidengine.ui.glass.backdrop.drawBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.blur
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.colorControls
import dev.antigravity.fluidengine.ui.glass.backdrop.isRenderEffectSupported
import dev.antigravity.fluidengine.ui.glass.backdrop.isRuntimeShaderSupported

/**
 * Un pannello di vetro la cui silhouette è quella di [state]: ferma quando lo stato riposa, viva
 * mentre viaggia — con la rifrazione che segue la forma, non un'istantanea di dov'era.
 *
 * Costruita su `drawBackdrop`, accanto a `glassSurface` e con la sua stessa grammatica (ruoli,
 * ottiche, tinte, qualità), ma con tre differenze deliberate che sono l'intero motivo per cui
 * esiste:
 *
 *  1. **Il clip del layer non insegue la sagoma.** Al tier [FluidPhysicsTier.Full] il layer resta
 *     ritagliato sul proprio rettangolo per tutta la vita della superficie, e la silhouette la
 *     scolpisce l'alpha dello shader — è ciò che rende un fotogramma di morph un aggiornamento di
 *     uniform invece di un re-clip.
 *  2. **Il bordo speculare è disegnato qui, non dal renderer.** L'anello del renderer vendored si
 *     ri-registra solo alla ricomposizione, quindi terrebbe in mano la fotografia della sagoma di
 *     partenza per tutto il viaggio. Un tratto di path con un gradiente direzionale segue invece
 *     la sagoma per costruzione, a un costo che non si misura.
 *  3. **Niente ombra esterna.** Le ombre del renderer hanno la stessa memoria fotografica del
 *     bordo, e un'ombra che resta indietro rispetto alla sagoma è il manufatto più visibile che un
 *     morph possa produrre. Il distacco lo raccontano la dislocazione al perimetro e il bordo — la
 *     stessa scelta di [GlassRole.Content], per la stessa ragione.
 *
 * Il contenuto appoggiato sopra non viene MAI stirato dalla sagoma: passa da
 * [fluidPhysicsContent], che gli dà il contratto 1.8.x — layout alla taglia d'arrivo, dissolvenza,
 * zoom uniforme. La lezione del design rigettato, incorporata nell'API.
 */
@Composable
fun Modifier.fluidPhysicsSurface(
  state: FluidPhysicsState,
  backdrop: GlassBackdropState,
  tint: GlassTint,
  role: GlassRole = GlassRole.Floating,
  optics: GlassOptics = GlassDefaults.optics(role),
  /** null = il tetto del dispositivo. Una richiesta può solo scendere di livello, mai salire. */
  tier: FluidPhysicsTier? = null,
  intensity: () -> Float = { 1f },
  sampleOnce: Boolean = false,
): Modifier {
  val resolved = remember(optics) { optics.sanitized() }
  val resolvedTier = remember(tier) {
    resolvePhysicsTier(
      renderEffectSupported = isRenderEffectSupported(),
      runtimeShaderSupported = isRuntimeShaderSupported(),
      shadersHealthy = FluidPhysicsShaderHealth.healthy(),
      requested = tier,
    )
  }
  state.maskInShader = resolvedTier == FluidPhysicsTier.Full

  if (resolvedTier == FluidPhysicsTier.Lite) {
    return this.physicsFallback(state, tint, intensity)
  }

  val currentIntensity by rememberUpdatedState(intensity)
  val quality = LocalFluidGlassQuality.current
  val qualityFactor: () -> Float = remember(quality) { { quality?.level ?: 1f } }
  val blurRadius = backdrop.blurRadius
  val scratch = remember(state) { PhysicsUniformScratch() }
  // Lo scope degli effetti può lavorare a risoluzione ridotta: ogni pixel di geometria va
  // riportato nel suo spazio, e il fattore è (larghezza dello scope / larghezza di layout).
  val layoutWidth = remember { mutableFloatStateOf(0f) }
  val shapeBlock: () -> Shape = remember(state) { { state.ensurePlan().shape } }

  val effects: BackdropEffectScope.() -> Unit = remember(resolved, blurRadius, quality, state, resolvedTier, tint) {
    {
      val amount = clampGlassUnit(currentIntensity())
      if (amount > 0.001f) {
        val q = qualityFactor()
        if (resolved.vibrancy != 1f) {
          colorControls(saturation = 1f + (resolved.vibrancy - 1f) * amount)
        }
        val blurQuality = FluidGlassBlurFloor + (1f - FluidGlassBlurFloor) * q
        blur(blurRadius.toPx() * resolved.blurScale * amount * blurQuality)

        if (resolvedTier == FluidPhysicsTier.Full) {
          val plan = state.ensurePlan()
          val factor = scopeFactor(size.width, layoutWidth.floatValue)
          // Come glassSurface: la lente scala con intensità e qualità. Durante un morph puro
          // entrambe valgono 1, quindi l'altezza — e con lei il padding — non si muove, e la
          // cattura non viene mai sporcata.
          val depth = amount * q
          val shaderTint = if (plan.tintInShader) {
            tint.overlay.copy(alpha = tint.overlay.alpha * amount)
          } else {
            Color.Transparent
          }
          if (plan.mode == PlanModePoly) {
            polyMorphLens(
              plan = plan,
              scopeFactor = factor,
              scratch = scratch,
              refractionHeightPx = resolved.refractionHeight.toPx() * depth,
              refractionAmountPx = resolved.refractionAmount.toPx() * depth,
              depthEffect = resolved.depthEffect,
              tintInShader = shaderTint,
            )
          } else {
            slabGroupMorphLens(
              plan = plan,
              scopeFactor = factor,
              scratch = scratch,
              refractionHeightPx = resolved.refractionHeight.toPx() * depth,
              refractionAmountPx = resolved.refractionAmount.toPx() * depth,
              depthEffect = resolved.depthEffect,
              tintInShader = shaderTint,
            )
          }
        }
      }
    }
  }

  val onDrawSurface: DrawScope.() -> Unit = remember(tint, state, resolvedTier) {
    {
      val amount = clampGlassUnit(currentIntensity())
      if (amount > 0.001f) {
        val plan = state.ensurePlan()
        val tintCarriedByShader = resolvedTier == FluidPhysicsTier.Full && plan.tintInShader
        if (!tintCarriedByShader) {
          drawPath(plan.silhouette, tint.overlay.copy(alpha = tint.overlay.alpha * amount))
        }
      }
    }
  }

  val onDrawFront: DrawScope.() -> Unit = remember(resolved, quality, state) {
    {
      val amount = clampGlassUnit(currentIntensity()) * qualityFactor()
      val alpha = clampGlassUnit(resolved.highlightAlpha * amount)
      if (alpha > 0.01f) {
        val plan = state.ensurePlan()
        val bounds = plan.bounds
        if (bounds.height > 0f) {
          // Il rim del renderer legge la luce dal gradiente del campo; qui lo approssima un
          // gradiente lungo la sagoma — pieno sul bordo che guarda la luce, quasi niente sui
          // fianchi, un riflesso più povero sotto. Su un morph transiente le due letture sono
          // indistinguibili, e questa segue la sagoma a ogni fotogramma.
          drawPath(
            path = plan.silhouette,
            brush = Brush.verticalGradient(
              0f to Color.White.copy(alpha = alpha),
              0.45f to Color.White.copy(alpha = alpha * 0.15f),
              1f to Color.White.copy(alpha = alpha * 0.5f),
              startY = bounds.top,
              endY = bounds.bottom,
            ),
            style = Stroke(width = resolved.highlightWidth.toPx()),
          )
        }
      }
    }
  }

  return this
    .onSizeChanged { layoutWidth.floatValue = it.width.toFloat() }
    .drawBackdrop(
      backdrop = backdrop.backdrop,
      shape = shapeBlock,
      effects = effects,
      highlight = null,
      shadow = null,
      innerShadow = null,
      onDrawSurface = onDrawSurface,
      onDrawFront = onDrawFront,
      backdropScale = glassResolutionScale(blurRadius.value * resolved.blurScale) *
        resolved.backdropResolution,
      sampleOnce = sampleOnce,
      backdropScaleFactor = qualityFactor,
    )
}

/** Il ripiego sotto API 31: la tinta piena, ritagliata sul path che si trasforma. */
private fun Modifier.physicsFallback(
  state: FluidPhysicsState,
  tint: GlassTint,
  intensity: () -> Float,
): Modifier = this.drawWithContent {
  val amount = clampGlassUnit(intensity())
  if (amount > 0.001f) {
    val plan = state.ensurePlan()
    drawPath(plan.silhouette, tint.fallback.copy(alpha = tint.fallback.alpha * amount))
  }
  drawContent()
}

/** Da che lato del viaggio sta un contenuto. */
enum class FluidPhysicsContentRole { Outgoing, Incoming }

/**
 * Il contratto del contenuto durante un morph, impacchettato: il contenuto è misurato alla taglia
 * del proprio estremo e non viene mai stirato — solo dissolvenza e uno zoom uniforme di qualche
 * punto percentuale. È la lezione pagata dal design rigettato del 1.8.x ("una tendina tirata su un
 * cartello già scritto"): la sagoma si trasforma, la scritta mai.
 *
 * [FluidPhysicsContentRole.Outgoing] se ne va nella prima parte del viaggio;
 * [FluidPhysicsContentRole.Incoming] arriva nella seconda, così i due testi non sono mai leggibili
 * insieme.
 */
fun Modifier.fluidPhysicsContent(
  state: FluidPhysicsState,
  contentRole: FluidPhysicsContentRole = FluidPhysicsContentRole.Incoming,
): Modifier = graphicsLayer {
  val progress = state.progress.coerceIn(0f, 1f)
  when (contentRole) {
    FluidPhysicsContentRole.Outgoing -> {
      alpha = 1f - (progress / 0.35f).coerceIn(0f, 1f)
      val scale = 1f - 0.06f * progress
      scaleX = scale
      scaleY = scale
    }

    FluidPhysicsContentRole.Incoming -> {
      alpha = ((progress - 0.4f) / 0.45f).coerceIn(0f, 1f)
      val scale = 0.92f + 0.08f * progress.coerceIn(0f, 1f)
      scaleX = scale
      scaleY = scale
    }
  }
}
