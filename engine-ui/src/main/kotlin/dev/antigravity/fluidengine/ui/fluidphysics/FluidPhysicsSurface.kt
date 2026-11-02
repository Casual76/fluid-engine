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
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassOptics
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassQuality
import dev.antigravity.fluidengine.ui.fluid.clampGlassUnit
import dev.antigravity.fluidengine.ui.fluid.glassResolutionScale
import dev.antigravity.fluidengine.ui.fluid.glassRimRamp
import dev.antigravity.fluidengine.ui.fluid.glassRimRecipe
import dev.antigravity.fluidengine.ui.fluid.glassSizedOptics
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
  /**
   * Tinta di partenza opzionale: la pellicola scivola da questa a [tint] secondo [tintBlend]
   * (0 = tintFrom, 1 = tint), letto in fase di disegno. Esiste per le superfici che *diventano*
   * un'altra cosa — un tasto che si espande in un pannello non può cambiare pellicola in un
   * fotogramma solo: il colore è parte del viaggio.
   */
  tintFrom: GlassTint? = null,
  tintBlend: () -> Float = { 1f },
  sampleOnce: Boolean = false,
  /**
   * Come in `glassSurface` (2.8.0): se c'e', la superficie registra anche *se stessa* — materiale,
   * tinta, bordo — in quello stato, cosi' un'altra superficie la puo' rifrangere. Serve ai tasti
   * tondi dentro una capsula che si trasforma: senza, rifrangevano la pagina sotto la capsula, cioe'
   * un vetro che attraversa un altro vetro senza vederlo. Si abbina a `rememberCombinedGlassBackdrop`.
   * Al tier [FluidPhysicsTier.Lite] non si registra niente, come il ripiego di `glassSurface`. La
   * registrazione segue [intensity]; un alpha messo da fuori sul nodo no — e' la terza trappola del
   * vetro in movimento: chi campiona vedrebbe il materiale a forza piena.
   */
  exports: GlassBackdropState? = null,
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

  val effects: BackdropEffectScope.() -> Unit = remember(resolved, blurRadius, quality, state, resolvedTier, tint, tintFrom) {
    {
      val amount = clampGlassUnit(currentIntensity())
      if (amount > 0.001f) {
        val q = qualityFactor()
        if (resolved.vibrancy != 1f) {
          colorControls(saturation = 1f + (resolved.vibrancy - 1f) * amount)
        }
        val full = resolvedTier == FluidPhysicsTier.Full
        val plan = if (full) state.ensurePlan() else null
        val factor = scopeFactor(size.width, layoutWidth.floatValue)
        // La taglia che conta e' quella della SAGOMA, non quella di `size`: lo scope di un morph e'
        // il layer che lo ospita, grande quanto lo schermo, e il tasto che diventa pannello ha
        // sempre la stessa "taglia" di schermo intero. `bounds` e' in pixel di layout; col fattore
        // dello scope diventa pixel di scope, gli stessi di `density`, quindi il rapporto e' dp veri.
        // Cresce con la sagoma: sfocatura e lente seguono il viaggio con continuita', e il padding
        // no — a materiale e qualita' pieni vale zero a ogni taglia, perche' la sfocatura non
        // supera mai l'altezza che la copre (vedi [glassSizedOptics]). Senza la lente (Balanced) la
        // regola e' spenta: senza niente che copra il margine, un raggio che cresce con la sagoma
        // lo sposterebbe a ogni fotogramma del viaggio.
        val sized = glassSizedOptics(
          optics = resolved,
          baseBlurPx = blurRadius.toPx(),
          amount = amount,
          quality = q,
          minDimPx = plan?.let { it.bounds.minDimension * factor } ?: 0f,
          density = density,
          lensAvailable = full,
        )
        blur(sized.blurPx)

        if (plan != null) {
          // Come glassSurface: la lente scala con intensità e qualità. Durante un morph puro
          // entrambe valgono 1, quindi l'altezza — e con lei il padding — non si muove, e la
          // cattura non viene mai sporcata.
          val depth = amount * q
          val shaderTint = if (plan.tintInShader) {
            val overlay = if (tintFrom == null) {
              tint.overlay
            } else {
              androidx.compose.ui.graphics.lerp(
                tintFrom.overlay,
                tint.overlay,
                clampGlassUnit(tintBlend()),
              )
            }
            overlay.copy(alpha = overlay.alpha * amount)
          } else {
            Color.Transparent
          }
          if (plan.mode == PlanModePoly) {
            polyMorphLens(
              plan = plan,
              scopeFactor = factor,
              scratch = scratch,
              refractionHeightPx = sized.refractionHeightPx * depth,
              refractionAmountPx = sized.refractionAmountPx * depth,
              depthEffect = resolved.depthEffect,
              tintInShader = shaderTint,
            )
          } else {
            slabGroupMorphLens(
              plan = plan,
              scopeFactor = factor,
              scratch = scratch,
              refractionHeightPx = sized.refractionHeightPx * depth,
              refractionAmountPx = sized.refractionAmountPx * depth,
              depthEffect = resolved.depthEffect,
              tintInShader = shaderTint,
            )
          }
        }
      }
    }
  }

  val onDrawSurface: DrawScope.() -> Unit = remember(tint, tintFrom, state, resolvedTier) {
    {
      val amount = clampGlassUnit(currentIntensity())
      if (amount > 0.001f) {
        val plan = state.ensurePlan()
        val tintCarriedByShader = resolvedTier == FluidPhysicsTier.Full && plan.tintInShader
        if (!tintCarriedByShader) {
          val overlay = if (tintFrom == null) {
            tint.overlay
          } else {
            androidx.compose.ui.graphics.lerp(
              tintFrom.overlay,
              tint.overlay,
              clampGlassUnit(tintBlend()),
            )
          }
          drawPath(plan.silhouette, overlay.copy(alpha = overlay.alpha * amount))
        }
      }
    }
  }

  // Il bordo e' quello di `glassSurface`, non uno proprio: stessa ricetta per lato (chiaro e in `Plus`
  // sul lato scuro, scuro e in `SrcOver` sul chiaro: `glassRimRecipe`) e stesso profilo. Prima era un
  // gradiente bianco in entrambi i temi, quindi sul lato chiaro la stessa lastra aveva un bordo da
  // ferma e un altro in viaggio — o nessuno, bianco su una faccia chiara.
  val darkSide = GlassDefaults.isDarkSurface()
  val rim = remember(darkSide) { glassRimRecipe(darkSide) }
  val onDrawFront: DrawScope.() -> Unit = remember(resolved, quality, state, rim) {
    {
      val amount = clampGlassUnit(currentIntensity()) * qualityFactor()
      val strength = clampGlassUnit(resolved.highlightAlpha * amount)
      if (strength > 0.01f) {
        // Il peso del bordo e' quello di `glassSurface`: l'alfa dell'ottica per quello del colore
        // della ricetta (0,5 sul lato scuro, 0,28 sul chiaro). Il renderer li moltiplica nello
        // stesso modo: l'alfa del layer del bordo per l'alfa del colore dello stile.
        val alpha = strength * rim.color.alpha
        val plan = state.ensurePlan()
        val bounds = plan.bounds
        if (bounds.height > 0f) {
          // Il rim del renderer legge la luce dal gradiente del campo; qui lo approssima un
          // gradiente lungo la sagoma, col profilo dello shader (`glassRimRamp`): pieno sul bordo
          // che guarda in su e in giu', a zero sui fianchi, con la rampa che l'angolo gli da'. Su un
          // morph transiente le due letture sono indistinguibili, e questa segue la sagoma a ogni
          // fotogramma.
          val topRamp = plan.rimRampTop()
          val bottomRamp = plan.rimRampBottom()
          val lit = rim.color.copy(alpha = alpha)
          val dark = rim.color.copy(alpha = 0f)
          drawPath(
            path = plan.silhouette,
            brush = Brush.verticalGradient(
              0f to lit,
              topRamp to dark,
              (1f - bottomRamp) to dark,
              1f to lit,
              startY = bounds.top,
              endY = bounds.bottom,
            ),
            style = Stroke(width = resolved.highlightWidth.toPx()),
            blendMode = rim.blendMode,
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
      exportedBackdrop = exports?.layerBackdrop,
      onDrawSurface = onDrawSurface,
      onDrawFront = onDrawFront,
      backdropScale = glassResolutionScale(blurRadius.value * resolved.blurScale) *
        resolved.backdropResolution,
      sampleOnce = sampleOnce,
      backdropScaleFactor = qualityFactor,
    )
}

/**
 * Le rampe del bordo (in alto, in basso) della sagoma corrente, in frazione dell'altezza.
 *
 * Con un solo rettangolo arrotondato gli angoli sono quelli veri, sopra e sotto; per un gruppo o una
 * sagoma libera l'angolo non c'e' da leggere, e si assume la capsula (mezzo lato corto), che e' anche
 * il tetto oltre cui lo shader non allarga l'arco. Sempre almeno [MinRimRamp], cosi' le fermate del
 * gradiente restano crescenti anche per un angolo vivo. Due funzioni e non una coppia: si chiamano a
 * ogni fotogramma di un viaggio, e una coppia sono tre oggetti.
 */
private fun PhysicsRenderPlan.rimRampTop(): Float {
  val rounded = silhouetteRoundRect
  val radius = rounded?.let { maxOf(it.topLeftCornerRadius.x, it.topRightCornerRadius.x) }
    ?: (minOf(bounds.width, bounds.height) / 2f)
  return glassRimRamp(radius, bounds.width, bounds.height).coerceAtLeast(MinRimRamp)
}

private fun PhysicsRenderPlan.rimRampBottom(): Float {
  val rounded = silhouetteRoundRect
  val radius = rounded?.let { maxOf(it.bottomLeftCornerRadius.x, it.bottomRightCornerRadius.x) }
    ?: (minOf(bounds.width, bounds.height) / 2f)
  return glassRimRamp(radius, bounds.width, bounds.height).coerceAtLeast(MinRimRamp)
}

private const val MinRimRamp = 0.002f

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
      alpha = ((progress - 0.25f) / 0.5f).coerceIn(0f, 1f)
      // Il contenuto VIAGGIA con la superficie, non l'aspetta: e' posato sul frame d'arrivo, e
      // finche' il pannello e' altrove lo raggiunge — traslato sul centro corrente della
      // silhouette e scalato sulla taglia vera, non sull'orologio. Senza questo, il testo sta
      // fermo mentre il pannello gli cresce sotto: la "tendina tirata su un cartello gia'
      // scritto" del design rigettato, di nuovo. Con un gruppo in arrivo la traslazione si
      // spegne: due etichette trascinate verso il centro dell'unione andrebbero nel posto
      // sbagliato, e una fusione la raccontano gia' i pezzi.
      val targetFrame = state.form.frame
      val bounds = state.ensurePlan().bounds
      if (state.form !is FluidForm.Group && targetFrame.minDimension > 0f && bounds.minDimension > 0f) {
        translationX = bounds.center.x - targetFrame.center.x
        translationY = bounds.center.y - targetFrame.center.y
        val scale = (bounds.minDimension / targetFrame.minDimension).coerceIn(0.72f, 1.12f)
        scaleX = scale
        scaleY = scale
      } else {
        val scale = 0.92f + 0.08f * progress
        scaleX = scale
        scaleY = scale
      }
    }
  }
}
