package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.glass.backdrop.BackdropEffectScope
import dev.antigravity.fluidengine.ui.glass.backdrop.drawPlainBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.glassApiLevel
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.RuntimeShaderEffect
import dev.antigravity.fluidengine.ui.glass.backdrop.isRuntimeShaderSupported
import dev.antigravity.fluidengine.ui.glass.backdrop.obtainEngineShader
import dev.antigravity.fluidengine.ui.glass.field.GlassEdgeBlurKernel
import dev.antigravity.fluidengine.ui.glass.field.GlassEdgeBlurSource
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldHealth
import dev.antigravity.fluidengine.ui.glass.field.GlassScrollEdgeCaptureScale
import dev.antigravity.fluidengine.ui.glass.field.GlassScrollEdgeProfile
import dev.antigravity.fluidengine.ui.glass.field.GlassScrollEdgeUniforms
import dev.antigravity.fluidengine.ui.glass.field.GlassWarmFamily
import dev.antigravity.fluidengine.ui.glass.field.GlassWarmUp

/**
 * Il lato dello schermo su cui sta un bordo dello scorrimento: in alto sotto la barra, in basso sopra la tab bar, la
 * capsula o la barra in basso. È il `Edge` di `scrollEdgeEffectStyle(_:for:)` di SwiftUI, ridotto ai due lati che
 * uno scorrimento verticale ha.
 */
enum class FluidScrollEdgeSide { Top, Bottom }

/**
 * Come il contenuto che scorre si comporta sotto i controlli di un bordo: `ScrollEdgeEffectStyle` di SwiftUI, più
 * `scrollEdgeEffectHidden` come valore.
 *
 * L'aspetto è quello di **iOS 26** per decisione di Alessio (2026-11-10, piano sezione 2): il contenuto si scioglie nel
 * colore del fondo sotto i tasti, con una sfocatura che cresce verso il bordo. La fascia uniforme a taglio netto di
 * iOS 27 non si fa.
 */
enum class FluidScrollEdgeStyle {
  /**
   * Il comportamento di serie: in alto il bordo morbido ([Soft]); in basso il velo senza sfocatura, come iOS 26 sul
   * telefono (R1 E1). Quale dei due in basso è una scelta di Alessio ancora aperta (tavola «in basso», F2.4).
   */
  Automatic,

  /** Il bordo morbido: sfocatura e velo che crescono verso il bordo, nessuna riga (R4 B7). */
  Soft,

  /**
   * Il bordo duro: una fascia del colore del fondo che nasconde il contenuto, con un passaggio in meno di 0,2 altezze
   * di tasto (R4 B7, variante dura). Per le pagine che hanno un'intestazione fissa sotto la barra, come le colonne di
   * una tabella.
   */
  Hard,

  /** Nessun effetto: il contenuto passa nitido sotto i controlli (`scrollEdgeEffectHidden`). */
  Hidden,
}

/** I valori di serie di [FluidScrollEdge]. */
object FluidScrollEdgeDefaults {

  /**
   * L'altezza dei tasti della barra, `H`: l'unità del profilo (R4 misura il bordo in altezze dei tasti). 44 dp, il
   * tasto tondo dell'engine (R6, [FluidMetrics.GlassButtonLens]). Una tab bar passa la sua altezza.
   */
  val ControlHeight: Dp = FluidMetrics.GlassButtonLens

  /**
   * Quanto la striscia sporge **oltre** il bordo dei controlli, verso il contenuto: dove sfocatura e velo finiscono.
   * Zero per [FluidScrollEdgeStyle.Hidden]. Il componente è alto `height + reach`: chi lo posa gli lascia lo spazio.
   */
  fun reach(
    edge: FluidScrollEdgeSide,
    style: FluidScrollEdgeStyle = FluidScrollEdgeStyle.Automatic,
    controlHeight: Dp = ControlHeight,
  ): Dp {
    val profile = fluidScrollEdgeProfile(edge, style) ?: return 0.dp
    return controlHeight * profile.atResolution(GlassScrollEdgeCaptureScale).reach
  }
}

/**
 * Vero se il bordo dello scorrimento si può disegnare qui: Android 13 o più (AGSL), il materiale `Liquid` chiesto
 * ([LocalFluidGlassModel]) e il compilatore del dispositivo che accetta il programma della striscia e quelli della
 * lastra.
 *
 * Falso, [FluidScrollEdge] non disegna **niente**: chi lo posa sceglie il ripiego, ed è la fascia di oggi
 * (`glassSurface(falloff = GlassFalloff.FadeDown)`), che resta intatta sotto Android 13 e sotto `Classic` (piano 2 e
 * 3.10). La lastra conta anche lei perché un rifiuto del corpo o della luce porta tutto il processo a `Classic`
 * (piano 3.11): i tasti tornerebbero quelli di oggi, e una striscia nuova sotto tasti di oggi è una combinazione che
 * nessuno ha scelto.
 */
@Composable
fun fluidScrollEdgeAvailable(): Boolean {
  val requested = LocalFluidGlassModel.current
  return remember(requested) {
    glassScrollEdgeAvailable(
      sdk = glassApiLevel(),
      requested = requested,
      shadersHealthy = { glassLiquidSlabShadersHealthy() && GlassFieldHealth.usable(GlassEdgeBlurSource) },
    )
  }
}

/** La regola di [fluidScrollEdgeAvailable], pura. */
internal fun glassScrollEdgeAvailable(
  sdk: Int,
  requested: FluidGlassModel,
  shadersHealthy: () -> Boolean,
): Boolean = requested == FluidGlassModel.Liquid && sdk >= FluidGlassLiquidMinSdk && shadersHealthy()

/** Il profilo di un lato e di uno stile; `null` = nessuna striscia. */
internal fun fluidScrollEdgeProfile(edge: FluidScrollEdgeSide, style: FluidScrollEdgeStyle): GlassScrollEdgeProfile? =
  when (style) {
    FluidScrollEdgeStyle.Hidden -> null
    FluidScrollEdgeStyle.Hard -> GlassScrollEdgeProfile.Hard
    FluidScrollEdgeStyle.Soft -> GlassScrollEdgeProfile.Soft
    FluidScrollEdgeStyle.Automatic -> when (edge) {
      FluidScrollEdgeSide.Top -> GlassScrollEdgeProfile.Soft
      FluidScrollEdgeSide.Bottom -> FluidScrollEdgeBottomAutomatic
    }
  }

/**
 * `Automatic` in basso: sfuma senza sfocare (iOS 26 sul telefono, R1 E1). La tavola «in basso» di F2.4 chiede ad
 * Alessio se tenerlo o se in basso non mettere niente (`null`): la scelta cambia questa riga sola.
 */
internal val FluidScrollEdgeBottomAutomatic: GlassScrollEdgeProfile? = GlassScrollEdgeProfile.FadeOnly

/**
 * Il bordo dello scorrimento: una **striscia** larga quanto lo schermo, sotto i controlli di una barra (o sopra quelli
 * in basso), in cui il contenuto che scorre si scioglie nel colore del fondo con una sfocatura che cresce verso il
 * bordo dello schermo. Prende il posto della fascia di vetro della barra (piano 3.10): nessuna lastra, nessuna sagoma,
 * nessuna lente, nessun filo. È `scrollEdgeEffectStyle` di SwiftUI.
 *
 * Il profilo si misura in altezze dei tasti ([controlHeight]) a partire dal bordo dei controlli: il componente è alto
 * [height] (dal bordo dello schermo al bordo dei controlli più lontano da lui: barra di stato più riga dei tasti) più
 * [FluidScrollEdgeDefaults.reach], e chi lo posa gli lascia quello spazio. In alto si allinea in cima, in basso in fondo.
 *
 * Disegna solo dove [fluidScrollEdgeAvailable] è vero; altrove non disegna niente e il ripiego è di chi lo posa.
 *
 * @param backdrop il contenuto che scorre, registrato con `glassBackdropSource`.
 * @param edge in alto o in basso.
 * @param height dal bordo dello schermo al bordo dei controlli verso il contenuto (es. barra di stato + 44 dp).
 * @param style vedi [FluidScrollEdgeStyle]; `Hidden` non disegna.
 * @param exports dove pubblicare il contenuto già dissolto, perché i tasti che ci stanno sopra rifrangano quello e non
 *   la pagina cruda (come faceva la fascia con `exports`); combinarlo con la pagina resta di chi posa i tasti
 *   (`rememberCombinedGlassBackdrop(backdrop, exports)`).
 * @param intensity da 0 (nessun effetto: il contenuto è tutto sotto i controlli o non c'è niente da coprire) a 1. Scala
 *   sfocatura e velo, non l'alfa di un'immagine sfocata: a metà strada non c'è un'immagine doppia. Letto al disegno.
 * @param controlHeight `H`, l'altezza dei tasti dei controlli: l'unità del profilo.
 * @param color il colore in cui il contenuto si scioglie: di serie il fondo del tema (`colorScheme.background`), che è
 *   il colore della pagina di `FluidScreen`.
 * @param darkContent da 0 a 1, quanto il contenuto sotto la striscia è scuro rispetto al fondo chiaro: lì la striscia
 *   scurisce invece di schiarire (R4 B7, fiducia bassa). Letto al disegno; di serie 0. Lo fornirà la sonda dello sfondo.
 */
@Composable
fun FluidScrollEdge(
  backdrop: GlassBackdropState,
  edge: FluidScrollEdgeSide,
  height: Dp,
  modifier: Modifier = Modifier,
  style: FluidScrollEdgeStyle = FluidScrollEdgeStyle.Automatic,
  exports: GlassBackdropState? = null,
  intensity: () -> Float = FullScrollEdgeIntensity,
  controlHeight: Dp = FluidScrollEdgeDefaults.ControlHeight,
  color: Color = Color.Unspecified,
  darkContent: () -> Float = NoDarkContent,
) {
  if (!fluidScrollEdgeAvailable()) return
  val base = fluidScrollEdgeProfile(edge, style) ?: return
  // Il programma della striscia è il più caro a freddo (43–51 ms sul Tab S9): si scalda fuori schermo quando la prima
  // striscia entra in composizione, come fanno lastre e gruppi (`GlassWarmUp`), non al primo scorrimento.
  val view = LocalView.current
  remember(view) { GlassWarmUp.request(GlassWarmFamily.ScrollEdge, view) }
  val scale = GlassScrollEdgeCaptureScale
  val profile = remember(base, scale) { base.atResolution(scale) }
  val veil = if (color.isSpecified) color else MaterialTheme.colorScheme.background
  val currentIntensity by rememberUpdatedState(intensity)
  val currentDark by rememberUpdatedState(darkContent)
  val state = remember { GlassScrollEdgeState() }
  val top = edge == FluidScrollEdgeSide.Top
  val effects: BackdropEffectScope.() -> Unit = remember(state, profile, top, height, controlHeight, veil) {
    {
      glassScrollEdge(
        state = state,
        profile = profile,
        top = top,
        controls = height,
        unit = controlHeight,
        veil = veil,
        darkContent = currentDark(),
        amount = currentIntensity(),
      )
    }
  }
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(height + controlHeight * profile.reach)
      .drawPlainBackdrop(
        backdrop = backdrop.backdrop,
        shape = { RectangleShape },
        effects = effects,
        exportedBackdrop = exports?.layerBackdrop,
        backdropScale = scale,
      ),
  )
}

private val FullScrollEdgeIntensity: () -> Float = { 1f }
private val NoDarkContent: () -> Float = { 0f }

/**
 * Ciò che una striscia tiene fra un disegno e l'altro: gli uniform (riscritti, mai riallocati) e il nucleo della
 * sfocatura, che si rifà solo quando la sigma cambia (una densità nuova, un'altra altezza dei tasti).
 */
internal class GlassScrollEdgeState {
  val uniforms = GlassScrollEdgeUniforms()
  private var kernelSigma = Float.NaN
  private var kernel: GlassEdgeBlurKernel = GlassEdgeBlurKernel.Identity

  fun kernel(sigmaPx: Float): GlassEdgeBlurKernel {
    if (sigmaPx != kernelSigma) {
      kernel = GlassEdgeBlurKernel.forSigma(sigmaPx)
      kernelSigma = sigmaPx
    }
    return kernel
  }
}

/**
 * La catena della striscia: **due passate** dello stesso programma (orizzontale, poi verticale con velo e dissolvenza),
 * nessun margine (sopra il bordo dello schermo non c'è niente da campionare). Lo scope ha densità e taglia già alla
 * risoluzione della cattura, quindi ogni `toPx()` qui è in px della cattura.
 *
 * L'istanza dello shader è una per processo e le due passate la condividono: ognuna la riscrive per intero e la
 * fotografa subito nel suo effetto (`createRuntimeShaderEffect` copia gli uniform), come fa il corpo del vetro.
 */
internal fun BackdropEffectScope.glassScrollEdge(
  state: GlassScrollEdgeState,
  profile: GlassScrollEdgeProfile,
  top: Boolean,
  controls: Dp,
  unit: Dp,
  veil: Color,
  darkContent: Float,
  amount: Float,
) {
  if (!isRuntimeShaderSupported()) return
  padding = 0f
  val unitPx = unit.toPx()
  val kernel = state.kernel(GlassScrollEdgeUniforms.sigmaFor(profile, unitPx))
  state.uniforms.fill(
    profile = profile,
    top = top,
    widthPx = size.width,
    heightPx = size.height,
    controlsPx = controls.toPx(),
    unitPx = unitPx,
    veilColor = veil,
    darkContent = darkContent,
    amount = amount,
    kernel = kernel,
  )
  val shader = obtainEngineShader("GlassEdgeBlur", GlassEdgeBlurSource) ?: return
  state.uniforms.applyTo(shader, pass = 0)
  val horizontal = RuntimeShaderEffect(shader, "content").asAndroidRenderEffect()
  state.uniforms.applyTo(shader, pass = 1)
  val vertical = RuntimeShaderEffect(shader, "content").asAndroidRenderEffect()
  // `createChainEffect(esterno, interno)`: la verticale legge l'uscita dell'orizzontale.
  renderEffect = android.graphics.RenderEffect.createChainEffect(vertical, horizontal).asComposeRenderEffect()
}
