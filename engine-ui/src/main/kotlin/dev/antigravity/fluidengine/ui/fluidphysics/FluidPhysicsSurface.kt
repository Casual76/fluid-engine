package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.FluidPressState
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassOptics
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassQuality
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.clampGlassUnit
import dev.antigravity.fluidengine.ui.fluid.glassResolutionScale
import dev.antigravity.fluidengine.ui.fluid.glassRimRecipe
import dev.antigravity.fluidengine.ui.fluid.glassShadowColor
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.glass.backdrop.BackdropEffectScope
import dev.antigravity.fluidengine.ui.glass.backdrop.drawBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.blur
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.colorControls
import dev.antigravity.fluidengine.ui.glass.backdrop.glassApiLevel
import dev.antigravity.fluidengine.ui.glass.backdrop.isRenderEffectSupported
import dev.antigravity.fluidengine.ui.glass.backdrop.isRuntimeShaderSupported
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidOnDrawBackdrop
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidPanels
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidProfile
import dev.antigravity.fluidengine.ui.glass.field.GlassWarmFamily
import dev.antigravity.fluidengine.ui.glass.field.GlassWarmUp
import dev.antigravity.fluidengine.ui.glass.field.glassSamplesLiquidPanel
import dev.antigravity.fluidengine.ui.glass.interaction.GlowRecipe
import dev.antigravity.fluidengine.ui.glass.interaction.glassButtonGlowRecipe

/**
 * Un pannello di vetro la cui silhouette è quella di [state]: ferma quando lo stato riposa, viva
 * mentre viaggia — con la rifrazione che segue la forma, non un'istantanea di dov'era.
 *
 * **Dalla 4.0 il vetro è uno solo.** Al livello [FluidPhysicsTier.Full], dove il materiale nuovo può esistere
 * (Android 13, le sue sorgenti accettate, `LocalFluidGlassModel` non a `Classic`: vedi `resolvePhysicsGlass`), la
 * superficie si disegna con **lo stesso corpo e la stessa ricetta di `glassSurface`** (`ui/glass/field`): la
 * sagoma in viaggio, i pezzi di un gruppo, la goccia e le sagome libere sono lo stesso campo di una lastra ferma,
 * con il velo, il filo e il riflesso del profilo. In viaggio filo e riflesso li compone il corpo e seguono la
 * sagoma pixel per pixel, ponti compresi; a riposo una lastra ha i due disegni della luce di ogni altra lastra.
 * Il collo liquido fra due pezzi esiste **solo in viaggio**: a riposo due pezzi distinti sono separati del tutto.
 * Dove il materiale nuovo non può esistere vale, immutato, ciò che segue.
 *
 * Costruita su `drawBackdrop`, accanto a `glassSurface` e con la sua stessa grammatica (ruoli,
 * ottiche, tinte, qualità). **A riposo è una lastra** (3.0): una forma ferma si disegna con i numeri
 * di `glassSurface` per lo stesso ruolo, la stessa ottica e la stessa tinta — la stessa funzione per
 * sfocatura, lente, frangia e pressione ([physicsLensUniforms]), il bordo e l'ombra registrati con
 * la ricetta del renderer ([PhysicsRimRenderer], [PhysicsShadowLayers]). In viaggio gli stessi numeri
 * seguono la sagoma, quindi alla partenza e all'arrivo non c'è nessuno scalino. Quello che resta
 * diverso, e perché, è nel passaggio "Il pannello in movimento" di `docs/10-coerenza.md`.
 *
 * Tre cose la separano da `glassSurface`, e sono il motivo per cui esiste:
 *
 *  1. **Il clip del layer non insegue la sagoma.** Al tier [FluidPhysicsTier.Full] il layer resta
 *     ritagliato sul proprio rettangolo per tutta la vita della superficie, e la silhouette la
 *     scolpisce l'alpha dello shader — è ciò che rende un fotogramma di morph un aggiornamento di
 *     uniform invece di un re-clip. Lo shader della lente è quello di Kyant (lo stesso campo, la
 *     stessa banda, la stessa cupola, la stessa frangia) con in più la maschera.
 *  2. **Bordo e ombra li disegna lei, non il renderer.** Gli anelli del renderer vendored si
 *     ri-registrano solo alla ricomposizione e solo sulla taglia del nodo, quindi terrebbero in mano
 *     la fotografia della sagoma di partenza per tutto il viaggio. Qui a riposo si registrano una
 *     volta come li registra il renderer, e in viaggio seguono la sagoma senza mai rifare una
 *     maschera sfocata.
 *  3. **Il nodo può essere più grande della sagoma** (il palco del Playground, lo schermo intero
 *     del modale): tutta la geometria è nella forma, mai nella taglia del nodo.
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
  /**
   * Ottica di partenza opzionale (3.0): il materiale scivola da questa a [optics] secondo
   * [opticsBlend] (0 = opticsFrom, 1 = optics), letto in fase di disegno — sfocatura, lente, cupola,
   * frangia, vividezza, bordo e ombra, tutti insieme ([lerpGlassOptics]). È il gemello di
   * [tintFrom]: un tasto che diventa un menu cambia vetro lungo il viaggio, non a metà strada.
   *
   * Il patto del padding regge per costruzione: la sfocatura non supera mai l'altezza della lente
   * che la copre, a nessun punto della miscela fra due ottiche di serie, quindi il margine della
   * cattura resta zero e fermo per tutto il viaggio. Chi passa ottiche scritte a mano deve tenerle
   * così ai due capi: in mezzo, la miscela lineare non lo rompe.
   */
  opticsFrom: GlassOptics? = null,
  opticsBlend: () -> Float = { 1f },
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
  /**
   * La pressione di chi tocca questa superficie (3.0), guidata da un `fluidPressable(pressState = )`
   * posato sulla sagoma: la lente si approfondisce come quella di un tasto di vetro
   * (`1 + pressione * pressedDepthBoost` sullo spostamento) e sotto il dito si accende il bagliore
   * dei tasti di vetro (`glassButtonGlowRecipe`: velo 0,10, punto 0,15 largo il lato lungo),
   * ritagliato sulla sagoma. Il dito si legge nelle coordinate dell'elemento premuto, che si assume
   * posato sul riquadro della sagoma. Niente crescita: scalare il layer di un vetro è la prima
   * trappola, e una superficie che si trasforma cede, non si gonfia sotto il dito. Null: nessuna
   * risposta, come prima della 3.0.
   */
  pressState: FluidPressState? = null,
): Modifier {
  val resolved = remember(optics) { optics.sanitized() }
  val resolvedFrom = remember(opticsFrom) { opticsFrom?.sanitized() }
  val material = remember(resolved, resolvedFrom) { PhysicsMaterialSource(resolvedFrom, resolved) }
  // Il livello e, a `Full`, quale dei due materiali (4.0): il materiale nuovo dove può esistere, altrimenti il
  // vetro di prima com'era. Una tabella di verità pura: `resolvePhysicsGlass`.
  val requestedModel = LocalFluidGlassModel.current
  val glass = remember(tier, requestedModel) {
    resolvePhysicsGlass(
      sdk = glassApiLevel(),
      renderEffectSupported = isRenderEffectSupported(),
      runtimeShaderSupported = isRuntimeShaderSupported(),
      classicHealthy = FluidPhysicsShaderHealth::healthy,
      liquidHealthy = ::physicsLiquidShadersHealthy,
      requestedTier = tier,
      requestedModel = requestedModel,
    )
  }
  val resolvedTier = glass.tier
  state.maskInShader = resolvedTier == FluidPhysicsTier.Full
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  state.density = LocalDensity.current.density

  // Il vetro liquido (3.0): il corpo elastico si alimenta da qui, fotogramma per fotogramma finche' la
  // sagoma si muove o la gelatina dell'arrivo non si e' posata, e a riposo non chiede niente. Con le
  // animazioni ridotte (o [LocalFluidPhysicsLiquid] spento) non parte: niente oscillazioni, niente
  // collo, niente lente in moto.
  val liquidOn = !reducedMotion && LocalFluidPhysicsLiquid.current
  state.liquidAllowed = liquidOn
  state.dropEdgeTension = LocalFluidPhysicsDropTuning.current.edgeTension
  LaunchedEffect(state, liquidOn) {
    if (liquidOn) state.runLiquid()
  }

  if (resolvedTier == FluidPhysicsTier.Lite) {
    return this.physicsFallback(state, tint, intensity)
  }

  val currentIntensity by rememberUpdatedState(intensity)
  val currentTintBlend by rememberUpdatedState(tintBlend)
  val currentOpticsBlend by rememberUpdatedState(opticsBlend)
  val quality = LocalFluidGlassQuality.current
  val qualityFactor: () -> Float = remember(quality) { { quality?.level ?: 1f } }
  val blurRadius = backdrop.blurRadius
  // Lo scope degli effetti può lavorare a risoluzione ridotta: ogni pixel di geometria va
  // riportato nel suo spazio, e il fattore è (larghezza dello scope / larghezza di layout).
  val layoutWidth = remember { mutableFloatStateOf(0f) }
  val shapeBlock: () -> Shape = remember(state) { { state.ensurePlan().shape } }
  val darkSide = GlassDefaults.isDarkSurface()

  // L'ombra del ruolo, disegnata da qui (3.0) e FUORI dal layer del vetro: sta prima di
  // `drawBackdrop` nella catena, quindi non la ritaglia il rettangolo del nodo e la sagoma la copre
  // con il proprio fondale. Colore del lato del tema, raggio dell'ottica dichiarata, peso del
  // materiale di adesso: a riposo e' quella di `glassSurface` di prima. Se il chiamante ne disegna gia' una
  // (`fluidPhysicsShadow`, il modale), questa tace. La stessa per i due materiali, finche' F1B.2 non la
  // porta sull'ombra unica.
  val shadowColor = glassShadowColor(darkSide)
  val shadowRadius = if (resolved.shadowRadius.value > 0f) resolved.shadowRadius else resolvedFrom?.shadowRadius ?: 0.dp
  val shadowFirst = rememberGraphicsLayer()
  val shadowSecond = rememberGraphicsLayer()
  val shadowLayers = remember(shadowFirst, shadowSecond) { PhysicsShadowLayers(shadowFirst, shadowSecond) }
  val shadow = remember(state, material, shadowLayers, shadowColor, shadowRadius, qualityFactor) {
    Modifier.drawBehind {
      if (state.externalShadows > 0) return@drawBehind
      val amount = clampGlassUnit(currentIntensity()) * qualityFactor()
      if (amount <= 0.001f) return@drawBehind
      val m = material.at(currentOpticsBlend())
      shadowLayers.draw(
        scope = this,
        state = state,
        radiusPx = shadowRadius.toPx(),
        color = shadowColor,
        alpha = m.optics.shadowAlpha * amount,
      )
    }
  }

  if (glass.liquid) {
    // Il materiale nuovo (4.0, F1B.1): lo stesso corpo e la stessa ricetta di `glassSurface`, sulla sagoma del
    // motore. Niente pellicola dipinta, niente bordo e niente bagliore a parte: sono nel corpo e nella luce.
    val screenDensity = LocalDensity.current.density
    val view = LocalView.current
    // La famiglia dei gruppi e della goccia si scalda quando una superficie che viaggia entra in scena.
    remember(view) { GlassWarmUp.request(GlassWarmFamily.Group, view) }
    val published = exports?.layerBackdrop
    if (published != null) {
      // Come una lastra `Liquid`: chi campiona questo materiale da un nodo fratello sta su questo pannello.
      DisposableEffect(published) {
        GlassLiquidPanels.publish(published)
        onDispose { GlassLiquidPanels.withdraw(published) }
      }
    }
    val currentBackdrop by rememberUpdatedState(backdrop.backdrop)
    val surface = remember(state, role, material, darkSide, screenDensity, quality, tint, tintFrom, pressState, reducedMotion) {
      PhysicsLiquidSurface(
        state = state,
        role = role,
        material = material,
        darkSide = darkSide,
        screenDensity = screenDensity,
        tint = tint,
        tintFrom = tintFrom,
        pressState = pressState,
        glowWeight = if (reducedMotion) ReducedMotionGlow else 1f,
        profile = GlassLiquidProfile.Default,
        intensity = { currentIntensity() },
        tintBlend = { currentTintBlend() },
        opticsBlend = { currentOpticsBlend() },
        quality = { quality?.level ?: 1f },
        layoutWidth = { layoutWidth.floatValue },
        onPanel = { glassSamplesLiquidPanel(currentBackdrop, GlassLiquidPanels::isPublished) },
      )
    }
    return this
      .onSizeChanged { layoutWidth.floatValue = it.width.toFloat() }
      .then(shadow)
      .drawBackdrop(
        backdrop = backdrop.backdrop,
        shape = shapeBlock,
        effects = surface.effects,
        highlight = null,
        shadow = null,
        innerShadow = null,
        exportedBackdrop = published,
        // La cattura apre sempre con un'operazione, perche' il corpo esista anche su un fondo che non ha niente
        // da dare: vedi `GlassLiquidOnDrawBackdrop`.
        onDrawBackdrop = GlassLiquidOnDrawBackdrop,
        // A risoluzione piena: filo e riflesso li compone il corpo, nella cattura, e devono essere quelli che
        // la luce di una `glassSurface` disegna sul nodo. A risoluzione ridotta un filo di un pixel e mezzo
        // uscirebbe piu' molle della lastra a cui la superficie passa il posto, e tornerebbe netto li': uno scatto.
        backdropScale = PhysicsLiquidCaptureScale,
        sampleOnce = sampleOnce,
        backdropScaleFactor = qualityFactor,
        onDrawLight = surface.onDrawLight,
      )
  }

  val scratch = remember(state) { PhysicsUniformScratch() }

  val effects: BackdropEffectScope.() -> Unit =
    remember(material, blurRadius, quality, state, resolvedTier, tint, tintFrom, pressState) {
      {
        val amount = clampGlassUnit(currentIntensity())
        if (amount > 0.001f) {
          val q = qualityFactor()
          val full = resolvedTier == FluidPhysicsTier.Full
          // La lente in moto (3.0): mentre la sagoma viaggia il vetro si fa goccia — piu' rifrazione
          // al bordo, meno latte, un filo d'ingrandimento — e a riposo il peso e' zero esatto, quindi
          // il materiale e' quello dichiarato, la stessa istanza. Solo dove la lente c'e'.
          val motionLens = if (full) state.liquid.lens else 0f
          val m = liquidMaterial(material.at(currentOpticsBlend()), motionLens)
          if (m.optics.vibrancy != 1f) {
            colorControls(saturation = 1f + (m.optics.vibrancy - 1f) * amount)
          }
          val plan = if (full) state.ensurePlan() else null
          val factor = scopeFactor(size.width, layoutWidth.floatValue)
          // La taglia che conta e' quella della SAGOMA, non quella di `size`: lo scope di un morph e'
          // il layer che lo ospita, grande quanto lo schermo, e il tasto che diventa pannello ha
          // sempre la stessa "taglia" di schermo intero. `bounds` e' in pixel di layout; col fattore
          // dello scope diventa pixel di scope, gli stessi di `density`, quindi il rapporto e' dp veri.
          // Senza la lente (Balanced) la regola e' spenta: senza niente che copra il margine, un
          // raggio che cresce con la sagoma lo sposterebbe a ogni fotogramma del viaggio.
          //
          // I numeri sono quelli di `glassSurface`, dalla stessa funzione: sfocatura, altezza,
          // spostamento (con la pressione), cupola e frangia. A riposo la lastra e' quella; in
          // viaggio gli stessi numeri seguono la sagoma, e all'arrivo non c'e' scalino.
          val lens = physicsLensUniforms(
            material = m,
            baseBlurPx = blurRadius.toPx(),
            amount = amount,
            quality = q,
            minDimPx = plan?.let { it.bounds.minDimension * factor } ?: 0f,
            density = density,
            lensAvailable = full,
            press = pressState?.progress ?: 0f,
          )
          blur(lens.blurPx)

          if (plan != null) {
            val shaderTint = if (plan.tintInShader) {
              val overlay = blendedTint(tint, tintFrom, currentTintBlend())
              overlay.copy(alpha = overlay.alpha * amount)
            } else {
              Color.Transparent
            }
            if (plan.mode == PlanModePoly) {
              polyMorphLens(
                plan = plan,
                scopeFactor = factor,
                scratch = scratch,
                refractionHeightPx = lens.refractionHeightPx,
                refractionAmountPx = lens.refractionAmountPx,
                depthEffect = lens.depthEffect,
                tintInShader = shaderTint,
                chromaticAberration = lens.dispersion,
                magnify = liquidMagnify(motionLens),
              )
            } else {
              slabGroupMorphLens(
                plan = plan,
                scopeFactor = factor,
                scratch = scratch,
                refractionHeightPx = lens.refractionHeightPx,
                refractionAmountPx = lens.refractionAmountPx,
                depthEffect = lens.depthEffect,
                tintInShader = shaderTint,
                chromaticAberration = lens.dispersion,
                magnify = liquidMagnify(motionLens),
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
          val overlay = blendedTint(tint, tintFrom, currentTintBlend())
          drawPath(plan.silhouette, overlay.copy(alpha = overlay.alpha * amount))
        }
      }
    }
  }

  // Il bordo e' quello di `glassSurface`, non uno proprio: stessa ricetta per lato (chiaro e in `Plus`
  // sul lato scuro, scuro e in `SrcOver` sul chiaro: `glassRimRecipe`), stessa banda dentro la sagoma,
  // stesso profilo. A riposo e' registrato come lo registra il renderer; in viaggio e' la stessa banda
  // senza la sfocatura di mezza larghezza (vedi [PhysicsRimRenderer]).
  val rim = remember(darkSide) { glassRimRecipe(darkSide) }
  val rimLayer = rememberGraphicsLayer()
  val rimRenderer = remember(rimLayer) { PhysicsRimRenderer(rimLayer) }
  val onDrawFront: DrawScope.() -> Unit = remember(material, quality, state, rim, rimRenderer) {
    {
      // Il peso del bordo e' quello di `glassSurface`: l'alfa dell'ottica per intensita' e qualita',
      // e il renderer ci moltiplica sopra l'alfa del colore della ricetta (0,5 sul lato scuro, 0,28
      // sul chiaro).
      val amount = clampGlassUnit(currentIntensity()) * qualityFactor()
      val m = material.at(currentOpticsBlend())
      if (m.optics.highlightAlpha > 0f && amount > 0.001f) {
        rimRenderer.draw(
          scope = this,
          plan = state.ensurePlan(),
          restSlab = state.restSlabNow(),
          optics = m.optics,
          strength = m.optics.highlightAlpha * amount,
          rim = rim,
        )
      }
    }
  }

  // Il bagliore del dito, dentro il layer del vetro e sotto il contenuto, come quello di
  // `glassControlSurface`; fuori dal materiale pubblicato (`exports`), come il suo.
  val glowRecipe = remember { glassButtonGlowRecipe() }
  val glow = remember(state, pressState, reducedMotion, glowRecipe) {
    if (pressState == null) {
      Modifier
    } else {
      Modifier.drawWithContent {
        val amount = clampGlassUnit(currentIntensity())
        if (amount > 0.001f) {
          drawPhysicsGlow(
            plan = state.ensurePlan(),
            press = pressState,
            weight = amount * (if (reducedMotion) ReducedMotionGlow else 1f),
            recipe = glowRecipe,
          )
        }
        drawContent()
      }
    }
  }

  // La cattura si tara sul piu' nitido dei due capi: un tasto quasi trasparente letto alla
  // risoluzione di un pannello smerigliato mostrerebbe i pixel che la sfocatura del pannello copre.
  val backdropScale = maxOf(
    glassResolutionScale(blurRadius.value * resolved.blurScale) * resolved.backdropResolution,
    resolvedFrom?.let { glassResolutionScale(blurRadius.value * it.blurScale) * it.backdropResolution } ?: 0f,
  )

  return this
    .onSizeChanged { layoutWidth.floatValue = it.width.toFloat() }
    .then(shadow)
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
      backdropScale = backdropScale,
      sampleOnce = sampleOnce,
      backdropScaleFactor = qualityFactor,
    )
    .then(glow)
}

/** La pellicola di adesso: [tint], o la miscela da [tintFrom] secondo [blend]. */
private fun blendedTint(tint: GlassTint, tintFrom: GlassTint?, blend: Float): Color =
  if (tintFrom == null) {
    tint.overlay
  } else {
    androidx.compose.ui.graphics.lerp(tintFrom.overlay, tint.overlay, clampGlassUnit(blend))
  }

/** Il peso del bagliore con le animazioni ridotte: quello di `glassControlSurface`. */
private const val ReducedMotionGlow = 0.4f

/**
 * La frazione di risoluzione a cui cattura una superficie fisica disegnata dal materiale nuovo (4.0): piena.
 *
 * Una lastra ferma di `glassSurface` cattura a risoluzione ridotta (0,73 con la smerigliatura di serie) perché la
 * sua luce si disegna sul nodo, a risoluzione piena, e il suo contorno lo taglia il ritaglio hardware. Una
 * superficie fisica, ferma o in viaggio, ha la luce **nel corpo** e il contorno ritagliato **dal corpo** (che
 * emula anche l'antialias del ritaglio del nodo: `nodeClip`, in pixel della cattura): a risoluzione ridotta il
 * filo e l'antialias del contorno sarebbero quelli della cattura ingrandita, cioè diversi da quelli della
 * `glassSurface` a cui la superficie passa il posto, e il passaggio si vedrebbe. A risoluzione piena l'interno
 * coincide entro due livelli su 255 (banco, scene `f1b/riposo-contro-viaggio-*`). I tasti di vetro, che hanno
 * un `layerBlock`, catturano già così. Costo: 1,9 volte i pixel della catena rispetto a 0,73, da misurare in S2
 * (voce del rapporto di F1B.1).
 */
internal const val PhysicsLiquidCaptureScale: Float = 1f

/**
 * Le fermate del punto luce del bagliore: la campana dello shader di `GlassTouchHighlight`
 * (`smoothstep(raggio, raggio * core, distanza)`), campionata in [GlowStops] tratti di un gradiente
 * radiale. Piena fino a `core` del raggio, poi scende col profilo della smoothstep a zero sul bordo.
 * Un gradiente e non lo shader: funziona anche sotto API 33, dove il bagliore di un tasto ripiega
 * su una lavata piatta.
 */
internal fun physicsGlowStops(recipe: GlowRecipe, glow: Float): Array<Pair<Float, Color>> {
  val core = recipe.core.coerceIn(0f, 0.999f)
  return Array(GlowStops + 1) { i ->
    val f = i / GlowStops.toFloat()
    val intensity = if (f <= core) {
      1f
    } else {
      val t = ((1f - f) / (1f - core)).coerceIn(0f, 1f)
      t * t * (3f - 2f * t)
    }
    f to recipe.tone.copy(alpha = (recipe.spot * glow * intensity).coerceIn(0f, 1f))
  }
}

private const val GlowStops = 8

/**
 * Il bagliore sotto il dito, sulla sagoma di adesso: il velo su tutta la faccia e il punto che segue
 * il dito, entrambi ritagliati dal path della silhouette (un `drawPath` con un pennello non esce dal
 * proprio path), nel modo di comporre della ricetta.
 */
private fun DrawScope.drawPhysicsGlow(
  plan: PhysicsRenderPlan,
  press: FluidPressState,
  weight: Float,
  recipe: GlowRecipe,
) {
  val glow = press.progress.coerceIn(0f, 1f) * weight.coerceIn(0f, 1f)
  if (glow <= 0f) return
  val bounds = plan.bounds
  if (bounds.minDimension <= 0f) return
  drawPath(
    path = plan.silhouette,
    color = recipe.tone.copy(alpha = (recipe.veil * glow).coerceIn(0f, 1f)),
    blendMode = recipe.blend,
  )
  val finger = press.glow
  val center = Offset(
    (bounds.left + finger.x).coerceIn(bounds.left, bounds.right),
    (bounds.top + finger.y).coerceIn(bounds.top, bounds.bottom),
  )
  val radius = if (recipe.radiusFromLongSide) bounds.maxDimension else bounds.minDimension * recipe.radiusScale
  if (radius <= 0f) return
  drawPath(
    path = plan.silhouette,
    brush = Brush.radialGradient(
      colorStops = physicsGlowStops(recipe, glow),
      center = center,
      radius = radius,
    ),
    blendMode = recipe.blend,
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
      alpha = ((progress - 0.25f) / 0.5f).coerceIn(0f, 1f)
      // Il contenuto VIAGGIA con la superficie, non l'aspetta: e' posato sul frame d'arrivo, e
      // finche' il pannello e' altrove lo raggiunge — traslato sul centro corrente della
      // silhouette e scalato sulla taglia vera, non sull'orologio. Senza questo, il testo sta
      // fermo mentre il pannello gli cresce sotto: la "tendina tirata su un cartello gia'
      // scritto" del design rigettato, di nuovo. Con un gruppo in arrivo la traslazione si
      // spegne: due etichette trascinate verso il centro dell'unione andrebbero nel posto
      // sbagliato, e una fusione la raccontano gia' i pezzi.
      val targetFrame = state.form.frame
      // La sagoma del viaggio, non quella del corpo che oscilla: il testo non trema con la gelatina.
      val bounds = state.contentBoundsNow()
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
