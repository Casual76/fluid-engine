package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.DefaultGlassBlurRadius
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidPressTransform
import dev.antigravity.fluidengine.ui.fluid.GlassOptics
import dev.antigravity.fluidengine.ui.fluid.fluidPressedBounds
import dev.antigravity.fluidengine.ui.fluid.glassSizedOptics
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.fluidphysics.DropReachMarginPx
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsLensUniforms
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsMaterialSource
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsRenderPlan
import dev.antigravity.fluidengine.ui.fluidphysics.PlanModePoly
import dev.antigravity.fluidengine.ui.fluidphysics.RoundedRectPerimeter
import dev.antigravity.fluidengine.ui.fluidphysics.depthEffectUniform
import dev.antigravity.fluidengine.ui.fluidphysics.liquidMagnify
import dev.antigravity.fluidengine.ui.fluidphysics.liquidMaterial
import dev.antigravity.fluidengine.ui.fluidphysics.physicsLensUniforms
import dev.antigravity.fluidengine.ui.fluidphysics.sampleRestContour
import dev.antigravity.fluidengine.ui.theme.resolveFluidColorScheme
import kotlin.math.min

/*
 * Il vetro di OGGI visto dal banco: cio' che serve alle tracce «di oggi» (F0.5) e che l'engine non espone
 * come funzione pura.
 *
 * Ogni pezzo qui dice da dove viene. Quando e' una chiamata all'engine non c'e' niente da dire; quando e'
 * una ricostruzione (una formula che oggi vive dentro un `Modifier`, un effetto o un composable) il KDoc
 * nomina il punto del codice da cui e' copiata e la funzione che andrebbe estratta perche' la copia sparisca.
 */

// --- La tavolozza ------------------------------------------------------------------------------

/**
 * I colori del tema di serie (accento della casa, contrasto standard), fuori da una composizione.
 *
 * La tavolozza viene dall'engine (`resolveFluidColorScheme`, pura); le pellicole no: `glassFilm()`,
 * `controlTint()` e `modalTint()` sono `@Composable` in `GlassDefaults` (`fluid/GlassMaterial.kt:992,
 * 1139, 1212`) e qui sono **ricostruite** con le stesse costanti. Da estrarre:
 * `glassFilm(scheme, dark)` e le pellicole come funzioni pure della tavolozza.
 */
internal class TodayPalette(val dark: Boolean = false) {
  private val scheme = resolveFluidColorScheme(EngineSettings(), isDark = dark)

  val primary: Color = scheme.primary
  val onSurface: Color = scheme.onSurface

  /** Il colore delle voci distruttive di un menu (`FluidGlassMenuRow`: `colorScheme.error`). */
  val error: Color = scheme.error

  /** `GlassDefaults.glassFilm()`: il grigio del vetro con il 34% della tavolozza. */
  val film: Color = if (dark) {
    lerp(Color(0xFF4A4A4E), scheme.surfaceContainerHigh, 0.34f)
  } else {
    lerp(Color(0xFFFAFAFA), scheme.surfaceContainerLowest, 0.34f)
  }

  /** La pellicola di `GlassDefaults.controlTint()`: il 20% dell'accento, al 38% (34% sullo scuro). */
  val controlOverlay: Color = lerp(film, scheme.primary, 0.20f).copy(alpha = if (dark) 0.34f else 0.38f)

  /** La pellicola di `GlassDefaults.modalTint()` (che `panelTint()` restituisce): il film al 54% (50%). */
  val modalOverlay: Color = film.copy(alpha = if (dark) 0.50f else 0.54f)
}

// --- L'orologio --------------------------------------------------------------------------------

/**
 * Una molla di Compose valutata in forma chiusa: quello che `Animatable.animateTo` calcola a ogni
 * fotogramma, cioe' una `TargetBasedAnimation` con la stessa spec, letta al tempo di gioco. A tempo
 * finito (la molla entra nella soglia) vale il traguardo, come l'`Animatable` che si ferma.
 */
internal class TodaySpring(spec: AnimationSpec<Float>, val from: Float, val to: Float, velocity: Float = 0f) {
  private val animation = TargetBasedAnimation(spec, Float.VectorConverter, from, to, AnimationVector1D(velocity))

  fun finished(playNanos: Long): Boolean = playNanos > 0 && animation.isFinishedFromNanos(playNanos)

  fun value(playNanos: Long): Float = when {
    playNanos <= 0L -> from
    animation.isFinishedFromNanos(playNanos) -> to
    else -> animation.getValueFromNanos(playNanos)
  }

  fun velocity(playNanos: Long): Float =
    if (playNanos > 0L && animation.isFinishedFromNanos(playNanos)) 0f else animation.getVelocityVectorFromNanos(playNanos.coerceAtLeast(0L)).value
}

/** Lo stesso per un `Offset` (la deriva del dito che torna a zero). */
internal class TodayOffsetSpring(spec: AnimationSpec<Offset>, val from: Offset, val to: Offset) {
  private val animation = TargetBasedAnimation(spec, Offset.VectorConverter, from, to, AnimationVector2D(0f, 0f))

  fun finished(playNanos: Long): Boolean = playNanos > 0 && animation.isFinishedFromNanos(playNanos)

  fun value(playNanos: Long): Offset = when {
    playNanos <= 0L -> from
    animation.isFinishedFromNanos(playNanos) -> to
    else -> animation.getValueFromNanos(playNanos)
  }
}

// --- La goccia ---------------------------------------------------------------------------------

/**
 * Il conducente della goccia di [state], fotogramma per fotogramma: **ricostruisce**
 * `FluidPhysicsState.runLiquid()` (`fp/FluidPhysicsState.kt:659`), che e' una coroutine con
 * `snapshotFlow` e `withFrameNanos` e non si puo' chiamare con un orologio finto. Le chiamate dentro sono
 * quelle dell'engine (`dropRestSlabNow`, `sampleRestContour`, `dropAnchorNow`, `LiquidDrop.step`).
 *
 * L'ordine dei tempi e' quello del dispositivo, per come lo leggo: la sagoma a riposo cambia al
 * fotogramma *k* (lo `snapshotFlow` se ne accorge dopo che le molle hanno scritto), il conducente riparte
 * e chiede il fotogramma *k*+1, dove la goccia prende il primo campione senza integrare (e' posata, quindi
 * la sagoma e' ancora la lastra), e dal *k*+2 integra. In un fotogramma le molle scrivono prima e la
 * goccia legge dopo. Il passo e' fisso (1/240 s) dentro `step`, quindi sul PC e' riproducibile; sul
 * dispositivo la cadenza e' quella vera (120 Hz sul Tab S9), e lo scarto lo dice solo la registrazione
 * di S1. Da estrarre: un `stepLiquidFrame(state, timeNanos)` che `runLiquid` chiami a ogni fotogramma.
 */
internal class TodayLiquidDriver(private val state: FluidPhysicsState, private val density: Float) {
  private var watched: FluidForm.Slab? = state.dropRestSlabNow()
  private var startNext = false
  private var running = false
  private val rest = FloatArray(FluidMotion.DropPoints * 2)
  private val perimeter = RoundedRectPerimeter()

  /** Quanti fotogrammi la goccia ha integrato davvero (per i controlli dei test). */
  var integratedFrames: Int = 0
    private set

  fun frame(timeNanos: Long) {
    if (startNext) {
      startNext = false
      state.liquid.forgetHistory()
      running = true
    }
    if (running) {
      val slab = state.dropRestSlabNow()
      if (slab == null || !state.liquidAllowed) {
        state.liquid.reset()
        running = false
      } else {
        sampleRestContour(slab, state.liquid.points, rest, perimeter)
        val anchor = state.dropAnchorNow()
        val wasQuiet = state.liquid.quiet
        running = state.liquid.step(
          timeNanos = timeNanos,
          restContour = rest,
          anchorX = anchor.x,
          anchorY = anchor.y,
          reachPx = FluidMotion.DropAnchorReachDp * density,
          density = density,
          edgeTension = state.dropEdgeTension,
        )
        if (!wasQuiet || !state.liquid.quiet) integratedFrames++
      }
      // Finita (o tolta): torna ad aspettare che la sagoma cambi da qui.
      if (!running) watched = state.dropRestSlabNow()
      return
    }
    val now = state.dropRestSlabNow()
    if (now != watched) {
      watched = now
      startNext = true
    }
  }
}

// --- Il materiale di fluidPhysicsSurface -------------------------------------------------------

/** Pixel veri dell'antialias della maschera: `MaskAntialiasPx` (privata) di `fp/FluidPhysicsEffects.kt:24`. */
internal const val TodayMaskAntialiasPx = 1.25f

/** La media della normale dell'anello: `PolyNormalSmooth` (privata) di `fp/FluidPhysicsEffects.kt:30`, in dp. */
internal const val TodayPolyNormalSmoothDp = 4f

/**
 * Cio' che `fluidPhysicsSurface` (blocco `effects`, `fp/FluidPhysicsSurface.kt:171-245`) manda alla catena e
 * allo shader in un fotogramma. Le funzioni sono dell'engine (`PhysicsMaterialSource.at`, `liquidMaterial`,
 * `physicsLensUniforms`, `liquidMagnify`); il loro **assemblaggio** e' ricostruito, perche' vive dentro una
 * lambda di `BackdropEffectScope`. Da estrarre: `physicsSurfaceFrame(plan, material, lens, tint, ...)`,
 * pura, che `effects` chiami e che restituisca catena e uniform.
 */
internal class TodayPhysicsLook(
  /** La saturazione di `colorControls` (1 = nessun effetto in catena). */
  val saturation: Float,
  val lens: PhysicsLensUniforms,
  /** Il peso della frangia come arriva allo shader; > 0 sceglie la sorgente con la frangia. */
  val chroma: Float,
  val magnify: Float,
  /** La pellicola di questo istante (`blendedTint`, gia' per l'intensita'). */
  val tint: Color,
)

internal fun todayPhysicsLook(
  plan: PhysicsRenderPlan,
  material: PhysicsMaterialSource,
  opticsBlend: Float,
  motionLens: Float,
  tint: Color,
  density: Float,
  amount: Float = 1f,
  quality: Float = 1f,
): TodayPhysicsLook {
  val m = liquidMaterial(material.at(opticsBlend), motionLens)
  val saturation = if (m.optics.vibrancy != 1f) 1f + (m.optics.vibrancy - 1f) * amount else 1f
  val lens = physicsLensUniforms(
    material = m,
    baseBlurPx = DefaultGlassBlurRadius.value * density,
    amount = amount,
    quality = quality,
    minDimPx = plan.bounds.minDimension,
    density = density,
    lensAvailable = true,
    press = 0f,
  )
  return TodayPhysicsLook(
    saturation = saturation,
    lens = lens,
    chroma = todayDispersionUniform(lens.dispersion),
    magnify = liquidMagnify(motionLens),
    tint = tint.copy(alpha = tint.alpha * amount),
  )
}

/**
 * `dispersionUniform` (`fp/FluidPhysicsEffects.kt:54`) senza la sonda di salute, che sul JVM non esiste:
 * qui le sorgenti con la frangia si danno per sane, come lo sono sul Tab S9 dopo la correzione di `flat`.
 */
internal fun todayDispersionUniform(weight: Float): Float = if (!(weight > 0.001f)) 0f else weight.coerceAtMost(1f)

/**
 * L'elemento `shader` di una superficie di Fluid-physics in un fotogramma: il palco fermo grande quanto la
 * tela e il campo che si muove dentro (`pieces` o `ring`), con tutti gli uniform della sorgente.
 *
 * **Ricostruisce** `slabGroupMorphLens` e `polyMorphLens` (`fp/FluidPhysicsEffects.kt:78, 185`), che
 * scrivono gli uniform dentro un `runtimeShaderEffect`. Due scostamenti voluti, entrambi per stare dentro
 * cio' che il banco sa disegnare:
 *  - la pellicola sta **sempre** nello shader. L'engine la mette nello shader solo con piu' pezzi o con la
 *    goccia (`tintInShader`), altrimenti la disegna come path in `onDrawSurface`; dentro la sagoma e' la
 *    stessa formula (tinta sopra, SrcOver), cambia solo l'antialias del bordo. `_pellicola` dice quale dei
 *    due fa l'engine in quel fotogramma;
 *  - `normalSmooth` e' un quadrato di lunghezza (px²): si scrive `"16dp^2"` (4 dp al quadrato), cosi' il banco
 *    lo porta in pixel con la densita' al quadrato e la traccia vale a ogni densita' di rendering.
 *
 * Le coordinate del piano sono pixel dell'host; [origin] e' dove sta l'angolo della tela nell'host.
 */
internal fun todayPhysicsElement(
  name: String,
  plan: PhysicsRenderPlan,
  look: TodayPhysicsLook,
  origin: Offset,
  density: Float,
  canvasWidthDp: Float,
  canvasHeightDp: Float,
  content: List<Map<String, Any?>>,
): Map<String, Any?> {
  fun dpX(px: Float) = (px - origin.x) / density
  fun dpY(px: Float) = (px - origin.y) / density
  val poly = plan.mode == PlanModePoly
  val dispersion = look.chroma > 0f
  val source = when {
    poly && dispersion -> "kotlin:PolyMorphDispersionShaderString"
    poly -> "kotlin:PolyMorphRefractionShaderString"
    dispersion -> "kotlin:SlabGroupDispersionShaderString"
    else -> "kotlin:SlabGroupRefractionShaderString"
  }
  val bounds = plan.bounds
  val uniforms = LinkedHashMap<String, Any?>()
  uniforms["offset"] = "\$offset"
  val element = LinkedHashMap<String, Any?>()
  element["kind"] = "shader"
  element["name"] = name
  element["source"] = source
  element["x"] = 0
  element["y"] = 0
  element["w"] = canvasWidthDp
  element["h"] = canvasHeightDp
  element["inputs"] = mapOf("content" to todayInput(look.saturation, look.lens.blurPx / density))
  if (poly) {
    val count = plan.vertCount
    element["ring"] = List(count) { i -> listOf(round3(dpX(plan.verts[i * 2])), round3(dpY(plan.verts[i * 2 + 1]))) }
    uniforms["vertCount"] = "\$vertCount"
    uniforms["verts"] = "\$verts"
    // Il riquadro dell'engine, non quello del banco (`$bbox` e' il minimo e il massimo dell'anello): e'
    // allargato dell'erosione, dell'antialias e di due pixel.
    val margin = plan.soften + TodayMaskAntialiasPx + 2f
    uniforms["bbox"] = listOf(
      glassDp(dpX(bounds.left - margin)), glassDp(dpY(bounds.top - margin)),
      glassDp(dpX(bounds.right + margin)), glassDp(dpY(bounds.bottom + margin)),
    )
    uniforms["normalSmooth"] = glassDpPower((TodayPolyNormalSmoothDp * TodayPolyNormalSmoothDp).toDouble(), 2)
    val rest = plan.dropRest
    if (rest != null) {
      val f = rest.frame
      val r = rest.cornerRadii
      uniforms["restCenter"] = listOf(glassDp(dpX(f.center.x)), glassDp(dpY(f.center.y)))
      uniforms["restHalfSize"] = listOf(glassDp(f.width / 2f / density), glassDp(f.height / 2f / density))
      uniforms["restRadii"] = listOf(r.topLeft, r.topRight, r.bottomRight, r.bottomLeft).map { glassDp(it / density) }
      uniforms["dropReach"] = glassDp((plan.dropReach + DropReachMarginPx) / density)
    } else {
      uniforms["restCenter"] = listOf(0, 0)
      uniforms["restHalfSize"] = listOf(0, 0)
      uniforms["restRadii"] = listOf(0, 0, 0, 0)
      uniforms["dropReach"] = -1
    }
    uniforms["soften"] = glassDp(plan.soften / density)
  } else {
    val count = plan.pieceCount
    element["pieces"] = List(count) { i ->
      val b = i * 4
      val cx = plan.pieceRects[b]
      val cy = plan.pieceRects[b + 1]
      val hw = plan.pieceRects[b + 2]
      val hh = plan.pieceRects[b + 3]
      linkedMapOf(
        "x" to round3(dpX(cx - hw)),
        "y" to round3(dpY(cy - hh)),
        "w" to round3(2f * hw / density),
        "h" to round3(2f * hh / density),
        "radius" to List(4) { j -> round3(plan.pieceRadii[b + j] / density) },
        "blend" to round3(plan.pieceBlend[i] / density),
      )
    }
    uniforms["pieceCount"] = "\$pieceCount"
    uniforms["pieceRect"] = "\$pieceRect"
    uniforms["pieceRadii"] = "\$pieceRadii"
    uniforms["pieceBlend"] = "\$pieceBlend"
  }
  uniforms["refractionHeight"] = glassDp(look.lens.refractionHeightPx.coerceAtLeast(0.0001f) / density)
  uniforms["refractionAmount"] = glassDp(-look.lens.refractionAmountPx / density)
  uniforms["depthEffect"] = depthEffectUniform(look.lens.depthEffect)
  uniforms["magnify"] = look.magnify
  uniforms["fieldCenter"] = listOf(glassDp(dpX(bounds.center.x)), glassDp(dpY(bounds.center.y)))
  if (dispersion) {
    uniforms["fieldHalfSize"] = listOf(glassDp(bounds.width / 2f / density), glassDp(bounds.height / 2f / density))
    uniforms["chromaticAberration"] = look.chroma
  }
  uniforms["aa"] = TodayMaskAntialiasPx
  uniforms["tintColor"] = glassColor(look.tint)
  element["uniforms"] = uniforms
  element["_pellicola"] = if (plan.tintInShader) "shader" else "path (qui nello shader)"
  if (content.isNotEmpty()) element["content"] = content
  return element
}

/** La catena a monte di un ingresso: saturazione, sfocatura con `Clamp`, come la mette `drawBackdrop`. */
internal fun todayInput(saturation: Float, blurDp: Float): Map<String, Any?> =
  linkedMapOf("saturation" to saturation, "blurDp" to blurDp, "edge" to "clamp")

// --- Il tasto di glassSurface ------------------------------------------------------------------

/**
 * Un tasto di vetro di oggi (`glassControlSurface` → `glassSurface`, `fluid/GlassMaterial.kt:1391-1437`) in
 * un fotogramma, come elemento `shader` con **un** pezzo.
 *
 * Sul dispositivo il tasto e' la lente di Kyant (`bd/internal/Shaders.kt`) nel layer del nodo, e la
 * pressione scala e sposta quel layer (`layerBlock`, `fluidPressTransform`). Qui e' il gemello di
 * Fluid-physics di quella lente (`SlabGroup*ShaderString`: «lo stesso campo, la stessa banda, la stessa
 * cupola, la stessa frangia», `fp/FluidPhysicsShaders.kt`) su un pezzo grande quanto il layer trasformato
 * (`fluidPressedBounds`, dell'engine). I numeri della lente sono quelli di `glassSurface`, ricostruiti:
 * `glassSizedOptics` sul lato del layer **non** scalato, lo spostamento per `1 + pressione * pressedDepthBoost`,
 * e poi tutto (sfocatura, altezza, spostamento) per la scala del layer, perche' il layer scalato li porta
 * con se'. Due approssimazioni: una scala non uniforme (l'allungamento) e' resa con la media dei due assi, e
 * il disco stirato e' una capsula invece di un'ellisse. Restano fuori, come per ogni traccia di oggi, il
 * bordo, l'ombra e il bagliore del dito (`_bagliore` ne porta i numeri). Da estrarre: un `glassSurfaceFrame`
 * puro che `effects` di `glassSurface` chiami.
 */
internal fun todayButtonElement(
  name: String,
  rest: Rect,
  transform: FluidPressTransform,
  pressed: Float,
  optics: GlassOptics,
  tint: Color,
  origin: Offset,
  density: Float,
  canvasWidthDp: Float,
  canvasHeightDp: Float,
  content: List<Map<String, Any?>>,
  extra: Map<String, Any?> = emptyMap(),
): Map<String, Any?> {
  val resolved = optics.sanitized()
  val sized = glassSizedOptics(
    optics = resolved,
    baseBlurPx = DefaultGlassBlurRadius.value * density,
    amount = 1f,
    quality = 1f,
    minDimPx = rest.minDimension,
    density = density,
    lensAvailable = true,
  )
  val layerScale = (transform.scaleX + transform.scaleY) / 2f
  val press = pressed.coerceIn(0f, 1f)
  val heightPx = sized.refractionHeightPx * layerScale
  val amountPx = sized.refractionAmountPx * (1f + press * resolved.pressedDepthBoost) * layerScale
  val blurPx = sized.blurPx * layerScale
  val saturation = if (resolved.vibrancy != 1f) 1f + (resolved.vibrancy - 1f) else 1f
  val dispersion = resolved.dispersion
  val shown = fluidPressedBounds(rest, transform)
  fun dpX(px: Float) = (px - origin.x) / density
  fun dpY(px: Float) = (px - origin.y) / density
  val radius = round3(min(shown.width, shown.height) / 2f / density)
  val element = LinkedHashMap<String, Any?>()
  element["kind"] = "shader"
  element["name"] = name
  element["source"] = if (dispersion) "kotlin:SlabGroupDispersionShaderString" else "kotlin:SlabGroupRefractionShaderString"
  element["x"] = 0
  element["y"] = 0
  element["w"] = canvasWidthDp
  element["h"] = canvasHeightDp
  element["inputs"] = mapOf("content" to todayInput(saturation, blurPx / density))
  element["pieces"] = listOf(
    linkedMapOf(
      "x" to round3(dpX(shown.left)),
      "y" to round3(dpY(shown.top)),
      "w" to round3(shown.width / density),
      "h" to round3(shown.height / density),
      "radius" to listOf(radius, radius, radius, radius),
      "blend" to 0,
    ),
  )
  val uniforms = LinkedHashMap<String, Any?>()
  uniforms["offset"] = "\$offset"
  uniforms["pieceCount"] = "\$pieceCount"
  uniforms["pieceRect"] = "\$pieceRect"
  uniforms["pieceRadii"] = "\$pieceRadii"
  uniforms["pieceBlend"] = "\$pieceBlend"
  uniforms["refractionHeight"] = glassDp(heightPx.coerceAtLeast(0.0001f) / density)
  uniforms["refractionAmount"] = glassDp(-amountPx / density)
  uniforms["depthEffect"] = if (resolved.depthEffect) 1 else 0
  uniforms["magnify"] = 0
  uniforms["fieldCenter"] = listOf(glassDp(dpX(shown.center.x)), glassDp(dpY(shown.center.y)))
  if (dispersion) {
    uniforms["fieldHalfSize"] = listOf(glassDp(shown.width / 2f / density), glassDp(shown.height / 2f / density))
    uniforms["chromaticAberration"] = 1
  }
  uniforms["aa"] = TodayMaskAntialiasPx
  uniforms["tintColor"] = glassColor(tint)
  element["uniforms"] = uniforms
  element["_pellicola"] = "path (qui nello shader)"
  element.putAll(extra)
  if (content.isNotEmpty()) element["content"] = content
  return element
}

// --- Il contenuto ------------------------------------------------------------------------------

/**
 * Uno strato con un'icona sola, centrata nel suo riquadro [box] (pixel dell'host), con la scala e
 * l'opacita' del `graphicsLayer` che la porta.
 */
internal fun todayIconLayer(
  name: String,
  box: Rect,
  icon: String,
  iconSizeDp: Float,
  color: Color,
  origin: Offset,
  density: Float,
  alpha: Float = 1f,
  scaleX: Float = 1f,
  scaleY: Float = scaleX,
): Map<String, Any?> = linkedMapOf(
  "name" to name,
  "x" to round3((box.left - origin.x) / density),
  "y" to round3((box.top - origin.y) / density),
  "w" to round3(box.width / density),
  "h" to round3(box.height / density),
  "icon" to icon,
  "iconSizeDp" to iconSizeDp,
  "color" to glassColor(color),
  "alpha" to alpha.coerceIn(0f, 1f),
  "scale" to if (scaleX == scaleY) scaleX else listOf(scaleX, scaleY),
)

/** Tre decimali: i dp di una traccia (TRACE.md). */
internal fun round3(value: Float): Double = Math.round(value.toDouble() * 1000.0) / 1000.0
