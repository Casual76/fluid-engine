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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateMeasurement
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.glass.backdrop.BackdropEffectScope
import dev.antigravity.fluidengine.ui.glass.backdrop.BottomEdgeBackdropEffects
import dev.antigravity.fluidengine.ui.glass.backdrop.ScrollEdgeBackdropEffects
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.drawPlainBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.glassApiLevel
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.RuntimeShaderEffect
import dev.antigravity.fluidengine.ui.glass.backdrop.isRuntimeShaderSupported
import dev.antigravity.fluidengine.ui.glass.backdrop.obtainEngineShader
import dev.antigravity.fluidengine.ui.glass.field.GlassEdgeBlurKernel
import dev.antigravity.fluidengine.ui.glass.field.GlassEdgeBlurSource
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldHealth
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldLevers
import dev.antigravity.fluidengine.ui.glass.field.GlassScrollEdgeCaptureScale
import dev.antigravity.fluidengine.ui.glass.field.GlassScrollEdgeProfile
import dev.antigravity.fluidengine.ui.glass.field.GlassScrollEdgeSplit
import dev.antigravity.fluidengine.ui.glass.field.GlassScrollEdgeUniforms
import dev.antigravity.fluidengine.ui.glass.field.GlassWarmFamily
import dev.antigravity.fluidengine.ui.glass.field.GlassWarmUp
import kotlin.math.floor
import kotlin.math.max

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
   * telefono (R1 E1). In basso Alessio ha scelto il velo senza sfocatura ([FadeOnly]) sulla tavola «in basso» di F2.4.
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
   * L'altezza dei tasti della barra, `H`: l'unità del profilo (R4 misura il bordo in altezze dei tasti). 46 dp, il
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
 * Falso, [FluidScrollEdge] non disegna **niente**, ma tiene il suo posto nel layout: chi lo posa sceglie il ripiego,
 * ed è la fascia di oggi (`glassSurface(falloff = GlassFalloff.FadeDown)`), che resta intatta sotto Android 13 e sotto
 * `Classic` (piano 2 e 3.10). La lastra conta anche lei perché un rifiuto del corpo o della luce porta tutto il
 * processo a `Classic` (piano 3.11): i tasti tornerebbero quelli di oggi, e una striscia nuova sotto tasti di oggi è
 * una combinazione che nessuno ha scelto.
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
 * Quanto è alto [FluidScrollEdge]: [height] più la portata del profilo ([FluidScrollEdgeDefaults.reach]), la stessa
 * altezza del `Box` che la striscia disegna; `null` quando lo stile non ha striscia ([FluidScrollEdgeStyle.Hidden])
 * e il componente non occupa niente. Dove la striscia non si può disegnare il componente pubblico tiene questo posto,
 * vuoto (D-R10).
 */
internal fun fluidScrollEdgeExtent(
  edge: FluidScrollEdgeSide,
  style: FluidScrollEdgeStyle,
  height: Dp,
  controlHeight: Dp,
): Dp? {
  if (fluidScrollEdgeProfile(edge, style) == null) return null
  return height + FluidScrollEdgeDefaults.reach(edge, style, controlHeight)
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
 * Disegna solo dove [fluidScrollEdgeAvailable] è vero; altrove non disegna niente e il ripiego è di chi lo posa, ma
 * **tiene il posto** (D-R10): un `Box` vuoto alto lo stesso `height + reach`, così una colonna che lo impila ha lo
 * stesso layout sotto `Liquid` e sotto `Classic` o Android 12. Con [FluidScrollEdgeStyle.Hidden] non occupa niente,
 * in tutti e due i casi.
 *
 * @param backdrop il contenuto che scorre, registrato con `glassBackdropSource`.
 * @param edge in alto o in basso.
 * @param height dal bordo dello schermo al bordo dei controlli verso il contenuto (es. barra di stato + 46 dp).
 * @param style vedi [FluidScrollEdgeStyle]; `Hidden` non disegna.
 * @param exports dove pubblicare il contenuto già dissolto, perché i tasti che ci stanno sopra rifrangano quello e non
 *   la pagina cruda (come faceva la fascia con `exports`); combinarlo con la pagina resta di chi posa i tasti
 *   (`rememberCombinedGlassBackdrop(backdrop, exports)`).
 * @param intensity da 0 (nessun effetto: il contenuto è tutto sotto i controlli o non c'è niente da coprire) a 1. Scala
 *   sfocatura e velo, non l'alfa di un'immagine sfocata: a metà strada non c'è un'immagine doppia. Letto al disegno.
 *   A zero la striscia non disegna niente, **cattura compresa**, come la fascia che sostituisce (F2.5,
 *   [glassScrollEdgeDraws]).
 * @param controlHeight `H`, l'altezza dei tasti dei controlli: l'unità del profilo.
 * @param color il colore in cui il contenuto si scioglie: di serie il fondo del tema (`colorScheme.background`), che è
 *   il colore della pagina di `FluidScreen`.
 * @param darkContent da 0 a 1, quanto il contenuto sotto la striscia è scuro rispetto al fondo chiaro: lì la striscia
 *   scurisce invece di schiarire (R4 B7, fiducia bassa). Letto al disegno; di serie 0. Nelle barre dell'engine lo dà
 *   la sonda dello sfondo (F2.5, `rememberFluidScrollEdgeShade`).
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
  if (!fluidScrollEdgeAvailable()) {
    // D-R10: dove la striscia non si disegna tiene lo stesso posto, vuota; il ripiego lo disegna chi la posa. Solo il
    // componente pubblico: la barra ancorata dell'host e `FluidScreen` chiamano la striscia solo dove si disegna.
    val extent = fluidScrollEdgeExtent(edge, style, height, controlHeight) ?: return
    Box(modifier = modifier.fillMaxWidth().height(extent))
    return
  }
  FluidScrollEdgeStrip(
    backdrop = backdrop,
    edge = edge,
    height = height,
    modifier = modifier,
    style = style,
    exports = exports,
    intensity = intensity,
    controlHeight = controlHeight,
    color = color,
    darkContent = darkContent,
    split = NoSplit,
  )
}

/**
 * [FluidScrollEdge] con il taglio fra due pagine ([split], letto al disegno; null = nessun taglio): la usa la barra
 * ancorata dell'host delle pagine, dove ogni pagina si porta la sua striscia mentre si muove.
 */
@Composable
internal fun FluidScrollEdgeStrip(
  backdrop: GlassBackdropState,
  edge: FluidScrollEdgeSide,
  height: Dp,
  modifier: Modifier,
  style: FluidScrollEdgeStyle,
  exports: GlassBackdropState?,
  intensity: () -> Float,
  controlHeight: Dp,
  color: Color,
  darkContent: () -> Float,
  split: () -> GlassScrollEdgeSplit?,
  // G1: il colore del velo letto in disegno (la barra in alto sul tema scuro, che vela verso il fondo chiaro quando
  // gira); null = [color] o il fondo del tema, come prima.
  veil: (() -> Color)? = null,
) {
  if (!fluidScrollEdgeAvailable()) return
  val base = fluidScrollEdgeProfile(edge, style) ?: return
  // Il programma della striscia è il più caro a freddo (43–51 ms sul Tab S9): si scalda fuori schermo quando la prima
  // striscia entra in composizione, come fanno lastre e gruppi (`GlassWarmUp`), non al primo scorrimento.
  val view = LocalView.current
  remember(view) { GlassWarmUp.request(GlassWarmFamily.ScrollEdge, view) }
  // F7.4, leva 9: la metà risoluzione cambia la resa ed è spenta di serie (`GlassFieldLevers.edgeHalf`, scelta di
  // Alessio). L'altezza del componente resta quella della risoluzione piena: chi lo posa non vede differenze.
  val scale = if (GlassFieldLevers.edgeHalf) GlassScrollEdgeHalfScale else GlassScrollEdgeCaptureScale
  val profile = remember(base, scale) { base.atResolution(scale) }
  val laidOut = remember(base) { base.atResolution(GlassScrollEdgeCaptureScale) }
  val baseVeil = if (color.isSpecified) color else MaterialTheme.colorScheme.background
  val currentVeil by rememberUpdatedState(veil)
  val currentIntensity by rememberUpdatedState(intensity)
  val currentDark by rememberUpdatedState(darkContent)
  val currentSplit by rememberUpdatedState(split)
  val currentHeight by rememberUpdatedState(height)
  val currentUnit by rememberUpdatedState(controlHeight)
  val state = remember { GlassScrollEdgeState() }
  val top = edge == FluidScrollEdgeSide.Top
  val hold = GlassFieldLevers.edgeHold
  // Con la tenuta (F7.4) le due altezze si leggono dentro gli effetti, osservate: la tab bar che si piega cambia gli
  // uniform e basta, senza un elemento nuovo a ogni fotogramma (che segnerebbe da registrare anche il materiale
  // esportato, e sveglierebbe la sonda di chi lo legge). Senza, le chiavi di prima.
  val heightKey = if (hold) null else height
  val unitKey = if (hold) null else controlHeight
  val effects: BackdropEffectScope.() -> Unit = remember(state, profile, top, heightKey, unitKey, baseVeil) {
    {
      glassScrollEdge(
        state = state,
        profile = profile,
        top = top,
        controls = currentHeight,
        unit = currentUnit,
        veil = currentVeil?.invoke() ?: baseVeil,
        darkContent = currentDark(),
        amount = currentIntensity(),
        split = currentSplit(),
        lean = GlassFieldLevers.edgeLean,
      )
    }
  }
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(height + controlHeight * laidOut.reach)
      // Il cancello (F2.5): a intensità zero il nodo della striscia non si disegna, e quindi non cattura e non fa
      // girare le due passate. Prima di un nodo che posa con un layer: saltarne il disegno salta anche il layer.
      .drawWithContent { if (glassScrollEdgeDraws(currentIntensity())) drawContent() }
      // Nella diagnostica la striscia si conta a parte, non come vetro di prima (dal seguito di F2.5).
      .then(ScrollEdgeCensusElement)
      // F7.4, tenuta: la cattura tiene la sua taglia mentre l'altezza si anima.
      .then(if (hold) ScrollEdgeHoldElement(top) else Modifier)
      .drawPlainBackdrop(
        backdrop = backdrop.backdrop,
        shape = { RectangleShape },
        effects = if (!top && exports != null && scale == 1f && profile.blurShare == 0f) {
          remember(effects) {
            BottomEdgeBackdropEffects(top = {
              val u = state.uniforms
              // Oltre la fine della dissolvenza il filtro è trasparente. Due pixel coprono il bordo campionato.
              floor(u.edgeLine[0] - u.edgeLine[2] * u.fadeRamp[1]) - 2f
            }, effects = effects, fromEdgeProfile = true)
          }
        } else remember(effects) { ScrollEdgeBackdropEffects(effects) },
        exportedBackdrop = exports?.layerBackdrop,
        backdropScale = scale,
      ),
  )
}

/** La risoluzione della cattura della striscia con la leva `GlassFieldLevers.edgeHalf` (F7.4): metà. */
internal const val GlassScrollEdgeHalfScale: Float = 0.5f

/**
 * Quanto la cattura della striscia tiene la taglia più grande dopo che l'altezza ha smesso di cambiare (F7.4): la piega
 * della tab bar ([FluidMotion.GooeyMillis]) cambia l'altezza a ogni fotogramma; dopo questa pausa la cattura torna alla
 * taglia esatta, con una registrazione sola.
 */
internal const val GlassScrollEdgeHoldSettleMillis: Long = 250L

/**
 * Di quanto la cattura cresce in più quando l'altezza sale (F7.4): una piega all'indietro della tab bar (52 → 62 dp, più la
 * portata del profilo in `H`, circa 24 dp in tutto) sta dentro una crescita sola invece di una per fotogramma.
 */
internal val GlassScrollEdgeHoldSlack: Dp = 24.dp

/**
 * La **tenuta** della cattura della striscia (F7.4, leva 9 del piano), pura: quanto è alto il nodo che cattura, dato quanto
 * è alto il bordo adesso. Gli istanti li dà chi chiama.
 *
 * Il caso. Il bordo in basso segue la tab bar che si piega mentre si scorre (62 → 52 dp e ritorno, `fluidTabBarEdgeInset`):
 * a ogni fotogramma della piega cambiano l'altezza del bordo e la sua unità `H`, quindi la taglia della cattura. Una taglia
 * nuova è una cattura da registrare di nuovo e, sul RenderThread, un layer offscreen da riallocare (con le texture di
 * appoggio delle passate), proprio mentre la pagina scorre. Qui il nodo che cattura tiene la taglia più grande finché
 * l'altezza cambia, e torna a quella esatta [GlassScrollEdgeHoldSettleMillis] dopo l'ultimo cambio; gli uniform (bordo dei
 * tasti, `H`) seguono il bordo vero a ogni fotogramma.
 *
 * Perché non si vede: la parte in più sta oltre la portata del profilo, dove la striscia esce trasparente (la dissolvenza
 * dell'alfa finisce a `fadeTo`, che è la portata); e il nodo è allineato al bordo dello schermo, dove sono contati gli
 * uniform. Il componente pubblico resta alto quanto prima: cresce solo il nodo dentro, verso il contenuto.
 *
 * @param slackPx di quanto cresce in più quando l'altezza sale ([GlassScrollEdgeHoldSlack] in px).
 */
internal class GlassScrollEdgeHold(
  private val slackPx: Int,
  private val settleMillis: Long = GlassScrollEdgeHoldSettleMillis,
) {
  /** L'altezza del nodo che cattura adesso, in px; -1 prima della prima misura. */
  var heldPx: Int = -1
    private set

  private var lastActualPx = -1
  private var changedAt = Long.MIN_VALUE

  /** L'altezza del nodo che cattura per un bordo alto [actualPx] a [nowMillis]. */
  fun extent(nowMillis: Long, actualPx: Int): Int {
    if (heldPx < 0) {
      heldPx = actualPx
      lastActualPx = actualPx
      return heldPx
    }
    if (actualPx != lastActualPx) {
      lastActualPx = actualPx
      changedAt = nowMillis
    }
    if (actualPx > heldPx) {
      heldPx = actualPx + slackPx
    } else if (heldPx != actualPx && changedAt != Long.MIN_VALUE && nowMillis - changedAt >= settleMillis) {
      heldPx = actualPx
    }
    return heldPx
  }

  /** Quando la tenuta si scioglie se l'altezza non cambia più, o null se il nodo ha già la taglia esatta. */
  fun settleAtMillis(): Long? =
    if (heldPx >= 0 && heldPx != lastActualPx && changedAt != Long.MIN_VALUE) changedAt + settleMillis else null
}

private class ScrollEdgeHoldElement(val top: Boolean) : ModifierNodeElement<ScrollEdgeHoldNode>() {
  override fun create(): ScrollEdgeHoldNode = ScrollEdgeHoldNode(top)

  override fun update(node: ScrollEdgeHoldNode) {
    node.top = top
  }

  override fun hashCode(): Int = if (top) 0x5ced01 else 0x5ced02

  override fun equals(other: Any?): Boolean = other is ScrollEdgeHoldElement && other.top == top
}

/**
 * Il nodo della tenuta: misura chi gli sta dentro (il nodo che cattura) all'altezza di [GlassScrollEdgeHold] e lo posa
 * allineato al bordo dello schermo, in cima in alto e in fondo in basso; lui resta alto quanto il componente.
 */
private class ScrollEdgeHoldNode(var top: Boolean) : Modifier.Node(), LayoutModifierNode {
  private var hold: GlassScrollEdgeHold? = null
  private val handler = android.os.Handler(android.os.Looper.getMainLooper())
  private val settle = Runnable { if (isAttached) invalidateMeasurement() }

  override fun onDetach() {
    handler.removeCallbacks(settle)
    hold = null
  }

  override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
    val actual = constraints.maxHeight
    if (!constraints.hasBoundedHeight || actual <= 0) {
      val placeable = measurable.measure(constraints)
      return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val current = hold ?: GlassScrollEdgeHold(GlassScrollEdgeHoldSlack.roundToPx()).also { hold = it }
    val now = android.os.SystemClock.uptimeMillis()
    val held = current.extent(now, actual)
    // La taglia esatta torna da sola, a piega finita: un messaggio solo, rifatto a ogni misura che cambia l'altezza.
    handler.removeCallbacks(settle)
    current.settleAtMillis()?.let { at -> handler.postAtTime(settle, at) }
    val placeable = measurable.measure(
      if (constraints.hasBoundedWidth) {
        Constraints.fixed(constraints.maxWidth, held)
      } else {
        Constraints(minWidth = constraints.minWidth, minHeight = held, maxHeight = held)
      },
    )
    val width = placeable.width
    return layout(width, actual) { placeable.place(0, if (top) 0 else actual - held) }
  }
}

/**
 * Conta la striscia in `FluidGlassDiagnostics.liveScrollEdges` finché il suo nodo è attaccato, con la regola dei nodi
 * del vetro: se la diagnostica è accesa quando si attacca. Così `liveSurfaces` non la conta come `Classic`.
 */
private object ScrollEdgeCensusElement : ModifierNodeElement<ScrollEdgeCensusNode>() {
  override fun create(): ScrollEdgeCensusNode = ScrollEdgeCensusNode()

  override fun update(node: ScrollEdgeCensusNode) = Unit

  override fun hashCode(): Int = 0x5ced6e

  override fun equals(other: Any?): Boolean = other === this
}

private class ScrollEdgeCensusNode : Modifier.Node() {
  private var counted = false

  override fun onAttach() {
    if (FluidGlassDiagnostics.enabled) {
      counted = true
      FluidGlassDiagnostics.countScrollEdge(+1)
    }
  }

  override fun onDetach() {
    if (counted) {
      counted = false
      FluidGlassDiagnostics.countScrollEdge(-1)
    }
  }
}

private val FullScrollEdgeIntensity: () -> Float = { 1f }
private val NoSplit: () -> GlassScrollEdgeSplit? = { null }
private val NoDarkContent: () -> Float = { 0f }

/** Sotto questa intensità la striscia non c'è: la stessa soglia della lastra (`glassPresence`). */
internal const val GlassScrollEdgeOffBelow: Float = 0.001f

/**
 * Se la striscia si disegna a intensità [amount] (F2.5): sotto [GlassScrollEdgeOffBelow] no, e allora non cattura e
 * non fa girare le due passate, come la fascia che sostituisce (F2-f). Una pagina che si apre in cima non paga niente
 * finché il contenuto non passa sotto i controlli.
 *
 * Spegnerla così non lascia ai tasti una striscia vecchia: l'export ([FluidScrollEdge] `exports`) tiene la cattura
 * **per riferimento**, e l'effetto di quella cattura il nodo lo rifà appena l'intensità cambia, fuori dal disegno
 * (`DrawBackdropNode.updateEffects`, osservato). Quando il cancello si chiude l'effetto è già quello a zero, cioè
 * trasparente.
 */
internal fun glassScrollEdgeDraws(amount: Float): Boolean = amount > GlassScrollEdgeOffBelow

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
  split: GlassScrollEdgeSplit? = null,
  lean: Boolean = false,
) {
  if (!isRuntimeShaderSupported()) return
  // Per le sessioni sul dispositivo (F6B.15): il colore in cui il bordo scioglie il contenuto, lo scurimento e l'intensità.
  dev.antigravity.fluidengine.ui.glass.backdrop.GlassFrameDiagnostics.event("scrollEdge", state) {
    "top=$top veil=#${Integer.toHexString(veil.toArgb())} dark=$darkContent amount=$amount size=${size.width}x${size.height}"
  }
  padding = 0f
  // F7.4: senza niente da mostrare (intensità zero, da una parte e dall'altra del taglio) la striscia esce trasparente
  // comunque; qui lo fa un filtro di colore invece di due passate da 41 letture. Conta per il materiale esportato, che i
  // tasti della barra continuano a campionare anche quando il cancello non disegna il nodo.
  val quality = this as? GlassRenderQuality
  val singleAxis = quality?.limitDecorations == true
  val passes = if (lean || singleAxis) glassScrollEdgePasses(amount, split, profile.blurShare, singleAxis) else 2
  if (passes == 0) {
    renderEffect = scrollEdgeSilentEffect ?: android.graphics.RenderEffect.createColorFilterEffect(
      android.graphics.PorterDuffColorFilter(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.SRC),
    ).asComposeRenderEffect().also { scrollEdgeSilentEffect = it }
    return
  }
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
    split = split,
  )
  val shader = obtainEngineShader("GlassEdgeBlur", GlassEdgeBlurSource) ?: return
  if (passes == 1) {
    // Durante il moto a costo ridotto la verticale conserva sfocatura, velo e dissolvenza.
    // F7.4: un profilo che non sfoca (il bordo in basso, `FadeOnly`) fa la sola seconda passata. La prima, con
    // `blurShare` zero, rende ogni pixel com'era (legge il centro e basta): toglierla non cambia un bit, e risparmia una
    // superficie offscreen larga quanto lo schermo a ogni fotogramma.
    state.uniforms.applyTo(shader, pass = 1)
    renderEffect = RuntimeShaderEffect(shader, "content").asAndroidRenderEffect().asComposeRenderEffect()
    return
  }
  state.uniforms.applyTo(shader, pass = 0)
  val horizontal = RuntimeShaderEffect(shader, "content").asAndroidRenderEffect()
  state.uniforms.applyTo(shader, pass = 1)
  val vertical = RuntimeShaderEffect(shader, "content").asAndroidRenderEffect()
  // `createChainEffect(esterno, interno)`: la verticale legge l'uscita dell'orizzontale.
  renderEffect = android.graphics.RenderEffect.createChainEffect(vertical, horizontal).asComposeRenderEffect()
}

/**
 * Quante passate fa la striscia (F7.4, leva 9 del piano: «la striscia solo quando il contenuto è sotto»), pura:
 *
 * - **0**: nessuna intensità, da tutte e due le parti del taglio fra pagine se c'è (la stessa regola con cui gli uniform
 *   scelgono fra `amount` e il taglio): lo shader darebbe trasparente ovunque (moltiplica l'uscita per `4·a` limitato a
 *   uno), e lo dà un filtro di colore che non legge niente intorno;
 * - **1**: il profilo non sfoca ([blurShare] zero): la sola passata del velo, che con `blurShare` zero legge il centro;
 * - **2**: sfocatura orizzontale, poi verticale con velo e dissolvenza, come sempre.
 *
 * Zero è «esattamente zero»: sotto la soglia del cancello ([GlassScrollEdgeOffBelow]) e sopra lo zero la striscia esportata
 * porta ancora un velo dello 0,4% al più, e un cambio al bit non si fa per un risparmio che lì non c'è.
 */
internal fun glassScrollEdgePasses(
  amount: Float, split: GlassScrollEdgeSplit?, blurShare: Float, singleAxis: Boolean = false,
): Int {
  val strongest = if (split != null && split.to >= split.from) max(split.outside, split.inside) else amount
  if (!(strongest.coerceIn(0f, 1f) > 0f)) return 0
  return if (blurShare > 0f && !singleAxis) 2 else 1
}

/**
 * Il bordo senza niente da mostrare (F7.4): ogni pixel trasparente, come la catena a intensità zero, ma con un filtro di
 * colore al posto delle due passate. Uno per processo (un `RenderEffect` è immutabile), fatto al primo uso dal filo che
 * disegna, dentro [glassScrollEdge] che ha già chiesto il livello di API.
 */
private var scrollEdgeSilentEffect: androidx.compose.ui.graphics.RenderEffect? = null
