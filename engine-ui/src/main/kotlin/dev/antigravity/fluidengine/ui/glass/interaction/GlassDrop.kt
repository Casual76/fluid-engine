/*
 * Copyright 2025 Kyant — Apache License, Version 2.0.
 *
 * The drop is the thumb of Kyant0's LiquidSlider and LiquidToggle, commit
 * b18eb0ff12c616546a68c72e7d0097f1ab286c87 (https://github.com/Kyant0/AndroidLiquidGlass), taken
 * out of the two controls that each carried a copy of it. The numbers are Kyant's and are not
 * changed here: blur 8 dp, lens 10/14 dp (slider) and 5/10 dp (toggle), Ambient highlight divided
 * by 1.5, inner shadow 4 dp, a fixed 4 dp shadow at 5%, the white face that the lens replaces, and
 * the squash `scaleX / (1 - v * 0.75)`, `scaleY * (1 - v * 0.25)` capped at 0.2.
 *
 * 3.0, fase 10: the face a drop wears when it stands in a pane and not on a coloured track
 * (`GlassDropFaces`), and the movement given as lambdas (`GlassDropMotion`), used to live in a second
 * file with a second modifier (`glassDropFace`). They are the same drop: one modifier, [glassDrop],
 * with the face and the motion as parameters. The thumbs keep white and their `GlassDragAnimation`
 * (the overload that takes a drag), and their output does not change by a bit.
 *
 * 4.0, F1B.3 (piano «iOS in Android»): dove il vetro è quello nuovo (`FluidGlassModel.Liquid`, da Android 13) il
 * corpo della goccia non è più la ricetta di Kyant qui sopra ma il materiale, con il profilo della goccia
 * (`glass/field/GlassDropProfile.kt`): la luce e l'ombra dei tasti, e sotto il dito una lente limpida. Geometria e
 * tempi non cambiano (la faccia, lo squash, la crescita); sotto Android 13 e con `FluidGlassModel.Classic` la
 * ricetta di Kyant resta quella di sempre, con i suoi numeri. Anche la goccia delle voci di menù passa da qui
 * (`FluidGlassMenuPanel.kt`): era una copia in linea.
 */
package dev.antigravity.fluidengine.ui.glass.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassFalloff
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassQuality
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModel
import dev.antigravity.fluidengine.ui.glass.backdrop.Backdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.BackdropEffectScope
import dev.antigravity.fluidengine.ui.glass.backdrop.drawBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.blur
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.lens
import dev.antigravity.fluidengine.ui.glass.backdrop.highlight.Highlight
import dev.antigravity.fluidengine.ui.glass.backdrop.shadow.InnerShadow
import dev.antigravity.fluidengine.ui.glass.backdrop.shadow.Shadow
import dev.antigravity.fluidengine.ui.glass.field.GlassDropOptics
import dev.antigravity.fluidengine.ui.glass.field.GlassDropRole
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidOnDrawBackdrop
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidPanels
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidSurface
import dev.antigravity.fluidengine.ui.glass.field.glassDropLightAmount
import dev.antigravity.fluidengine.ui.glass.field.glassDropProfile
import dev.antigravity.fluidengine.ui.glass.field.glassSamplesLiquidPanel
import dev.antigravity.fluidengine.ui.glass.field.glassShadow
import kotlin.math.abs

/**
 * I numeri della goccia, in un posto solo.
 *
 * "La goccia" e' cio' che si afferra e scorre: il pomello di uno slider e di un interruttore, la
 * pillola della tab bar, quella del segmentato, la goccia dei menu. A riposo e' piena; sotto il dito
 * diventa lente. I pomelli ne avevano ciascuno una copia, la pillola della barra un'altra scritta a
 * mano, e il segmentato nessuna: la ricetta e' una e sta in `docs/10-coerenza.md`, qui ci sono i
 * numeri che la dicono.
 */
internal object GlassDropRecipe {

  /** Sfocatura a riposo, che scende a zero con la pressione: `8 dp * (1 - p)`. */
  val RestBlur: Dp = 8.dp

  /** Il bagliore e' l'`Ambient` di serie, con larghezza e sfocatura divise per questo. */
  const val HighlightDivisor = 1.5f

  /** Raggio dell'ombra interna sotto il dito: `4 dp * p`, e alfa `p`. Solo premuta. */
  val PressedInnerShadowRadius: Dp = 4.dp

  /** L'ombra fissa, sempre accesa: 4 dp di raggio, nero al 5%. */
  val RestShadowRadius: Dp = 4.dp
  const val RestShadowAlpha = 0.05f

  /**
   * Il divisore della velocita' prima dello squash, per tutte le gocce tranne l'interruttore: la
   * velocita' della molla e' in "intervalli al secondo", e a 10 una corsa veloce fa il tetto.
   */
  const val SquashDivisor = 10f

  /**
   * Quello dell'interruttore, che conserva i numeri del port di Kyant (con segno, `/50`): la sua
   * corsa e' di venti dp e la velocita' in intervalli al secondo e' molto piu' alta di quella di uno
   * slider. E' il metro, e non si tocca.
   */
  const val SwitchSquashDivisor = 50f

  /** La goccia si allunga di `0,75` della velocita' lungo l'asse e si accorcia di `0,25` sull'altro. */
  const val SquashLengthen = 0.75f
  const val SquashShorten = 0.25f

  /** Nessuna deformazione supera il 20%: oltre, la pillola smette di essere la stessa cosa. */
  const val SquashCap = 0.2f
}

/** Altezza e spostamento della lente sotto il dito, entrambi pieni a pressione 1. */
internal data class GlassDropLens(val height: Dp, val amount: Dp) {
  companion object {
    /** Il pomello dello slider: 10 e 14 dp. */
    val Slider = GlassDropLens(10.dp, 14.dp)

    /** Quello dell'interruttore, piu' piccolo: 5 e 10 dp. */
    val Switch = GlassDropLens(5.dp, 10.dp)
  }
}

/**
 * Le due metà dello squash: `scaleX / xDivisor` e `scaleY * yFactor`.
 *
 * Restituiti come divisore e fattore, e non come due scale gia' calcolate, perche' e' cosi' che i
 * pomelli li applicavano (`scaleX /= ...`, `scaleY *= ...`): un'altra forma darebbe lo stesso numero a
 * meno di un'ultima cifra, e la goccia e' il metro. Un `value class` su un `Long`, e non una coppia:
 * si chiama a ogni fotogramma di un movimento, e non deve allocare.
 */
@JvmInline
internal value class GlassDropSquash private constructor(private val bits: Long) {

  constructor(xDivisor: Float, yFactor: Float) : this(
    (xDivisor.toRawBits().toLong() shl 32) or (yFactor.toRawBits().toLong() and 0xFFFFFFFFL),
  )

  val xDivisor: Float get() = Float.fromBits((bits shr 32).toInt())
  val yFactor: Float get() = Float.fromBits(bits.toInt())

  companion object {
    /** Nessuna deformazione: dividere e moltiplicare per uno non cambia un bit. */
    val None = GlassDropSquash(1f, 1f)
  }
}

/**
 * Lo squash della goccia dalla velocita' della sua molla, puro.
 *
 * @param velocity la velocita' della molla (`GlassDragAnimation.velocity`), in intervalli al secondo.
 * @param divisor [GlassDropRecipe.SquashDivisor], o [GlassDropRecipe.SwitchSquashDivisor] per
 *   l'interruttore.
 * @param signed vero conserva il segno della velocita': un verso allunga la goccia e l'opposto la
 *   accorcia, come nella sorgente di Kyant (l'interruttore, la pillola della tab bar). Falso ne
 *   prende il modulo: si allunga sempre, qualunque sia il verso (lo slider, e la ricetta di tutte le
 *   altre gocce).
 * @param reducedMotion con le animazioni ridotte la goccia non si deforma: [GlassDropSquash.None].
 */
internal fun glassDropSquash(
  velocity: Float,
  divisor: Float,
  signed: Boolean,
  reducedMotion: Boolean,
): GlassDropSquash {
  if (reducedMotion) return GlassDropSquash.None
  val v = velocity / divisor
  val along = if (signed) v else abs(v)
  val floor = if (signed) -GlassDropRecipe.SquashCap else 0f
  return GlassDropSquash(
    xDivisor = 1f - (along * GlassDropRecipe.SquashLengthen).fastCoerceIn(floor, GlassDropRecipe.SquashCap),
    yFactor = 1f - (along * GlassDropRecipe.SquashShorten).fastCoerceIn(floor, GlassDropRecipe.SquashCap),
  )
}

/**
 * La faccia di una goccia che sta in una pista di vetro: il segmentato, il rail, la lente dell'indice.
 *
 * I pomelli di slider e interruttore hanno la faccia bianca e basta, "voluta e non del tema": stanno
 * su una pista colorata e il bianco e' la loro identita'. Una goccia dentro una **lastra** deve
 * invece stare con il tema, come la pillola di iOS: bianca sul chiaro, e sullo scuro un grigio
 * (`systemGray` di iOS, 0x636366) — un bianco pieno su una pista scura e' un faro, e l'etichetta
 * chiara che ci sta sopra non si leggerebbe.
 *
 * Il nero sopra il grigio, e il bianco sopra il bianco, non si incontrano mai: l'etichetta della
 * goccia e' `onSurface`, che sul chiaro e' scuro e sullo scuro e' chiaro.
 */
internal object GlassDropFaces {
  val Light: Color = Color.White
  val Dark: Color = Color(0xFF636366)

  /** La faccia a riposo, dalla stessa domanda che decide tutto il vetro: `GlassDefaults.isDarkSurface()`. */
  fun forSurface(dark: Boolean): Color = if (dark) Dark else Light
}

/**
 * Cio' che la goccia legge del suo movimento, come lambda: pressione, scala X e Y, velocita'.
 *
 * Una [GlassDragAnimation] le ha tutte e quattro ([GlassDragAnimation.dropMotion]); la lente
 * dell'indice di sezione no — e' nata sotto il dito e la sua "pressione" e' l'apertura del nastro —
 * quindi le passa da sola. Tenute lambda e non valori: si leggono nel `layerBlock` e negli effetti, a
 * ogni fotogramma, e leggerle in composizione ricomporrebbe il chiamante a ogni passo del dito.
 */
@Stable
internal class GlassDropMotion(
  val progress: () -> Float,
  val scaleX: () -> Float,
  val scaleY: () -> Float,
  val velocity: () -> Float,
)

/** Le molle di [GlassDragAnimation] lette come [GlassDropMotion]. Una volta per animazione. */
internal fun GlassDragAnimation.dropMotion(): GlassDropMotion = GlassDropMotion(
  progress = { pressProgress },
  scaleX = { scaleX },
  scaleY = { scaleY },
  velocity = { velocity },
)

/** Una sola [GlassDropMotion] per animazione: l'identita' stabile tiene fermi gli effetti fra una ricomposizione e l'altra. */
@Composable
internal fun rememberDropMotion(drag: GlassDragAnimation): GlassDropMotion =
  remember(drag) { drag.dropMotion() }

/**
 * L'alfa della faccia piena a una pressione: parte da quello della faccia e svanisce con la pressione.
 *
 * Col bianco opaco dei pomelli e' `1 * (1 - p)`, cioe' il `1f - pressProgress` di quando ogni pomello
 * aveva la sua copia, **bit per bit** (`GlassDropFaceTest`): e' il motivo per cui la goccia e' una e lo
 * slider e l'interruttore non si sono mossi. La pressione e' tenuta fra 0 e 1: fuori, `Color.copy`
 * rifiuta un alfa che non sia in quell'intervallo.
 */
internal fun glassDropFaceAlpha(face: Color, progress: Float): Float =
  face.alpha * (1f - progress.coerceIn(0f, 1f))

/** Il valore di serie di `reducedMotion`: un'istanza sola, cosi' non cambia l'identita' del modificatore. */
internal val NeverReducedMotion: () -> Boolean = { false }

/**
 * La goccia: la superficie di vetro di un pomello, di Kyant, col movimento dato come lambda.
 *
 *  - **a riposo piena** — la faccia e' [face] (bianca di serie), la lente e' spenta, la sfocatura e' 8 dp;
 *  - **sotto il dito diventa lente**: la sfocatura scende a zero, la lente sale a [lensSize] con la
 *    dispersione, il bagliore `Ambient` e l'ombra interna arrivano a pieno, e la faccia sfuma;
 *  - **cresce e si deforma** col movimento di [motion]: scala X e Y separate, e lo squash dalla
 *    velocita' ([glassDropSquash]).
 *
 * Questo e' il vetro di Kyant, che resta dove il materiale nuovo non c'e'. Dove c'e' (F1B.3) faccia,
 * crescita e squash sono gli stessi, ma sotto la faccia c'e' il materiale: a riposo la faccia porta il filo,
 * il riflesso e l'ombra dei tasti, e sotto il dito sfuma su una lente limpida che piega quanto la pressione
 * (`glass/field/GlassDropProfile.kt`). [lensSize] vale solo per il vetro di Kyant.
 *
 * Nel `layerBlock` e non in un `graphicsLayer` antenato: il renderer inverte quella trasformazione
 * quando campiona, quindi la lente ha in mano la pista che c'e' davvero dietro. La traslazione
 * lungo la pista la mette chi chiama, in un `graphicsLayer` *prima* di questo modificatore.
 *
 * E' la forma generale: la pressione puo' non venire da una molla di trascinamento (la lente
 * dell'indice di sezione ha la sua) e la faccia puo' non essere bianca (una goccia in una lastra sta
 * col tema: [GlassDropFaces]). Chi ha una [GlassDragAnimation] usa l'overload sotto.
 *
 * @param backdrop cio' che la lente piega: la pista, di solito, sopra la superficie in scope.
 * @param face la faccia piena a riposo; sfuma con la pressione. Il suo alfa di partenza e' il suo,
 *   cosi' una faccia traslucida non diventa piena per essere stata data. Bianco: quello dei pomelli.
 * @param squashDivisor [GlassDropRecipe.SquashDivisor], o quello dell'interruttore.
 * @param squashSigned vedi [glassDropSquash].
 * @param reducedMotion letto nel `layerBlock`, a ogni fotogramma.
 * @param squashVertical vero se la goccia corre in verticale (le voci di un menù): lo squash allunga l'asse Y e
 *   accorcia l'X. Di serie in orizzontale, come i pomelli.
 * @param place ciò che il chiamante mette nel `layerBlock` **prima** della scala e dello squash: traslazione, alfa.
 *   È il posto della goccia del menù (fra le celle, portato sulla sagoma del viaggio): nel `layerBlock` e non in un
 *   layer sopra, perché il renderer inverta anche quella quando campiona. Una scala scritta qui viene sostituita.
 */
@Composable
internal fun Modifier.glassDrop(
  backdrop: Backdrop,
  motion: GlassDropMotion,
  lensSize: GlassDropLens,
  face: Color = GlassDropFaces.Light,
  squashDivisor: Float = GlassDropRecipe.SquashDivisor,
  squashSigned: Boolean = false,
  reducedMotion: () -> Boolean = NeverReducedMotion,
  shape: Shape = FluidCapsuleShape,
  squashVertical: Boolean = false,
  place: (GraphicsLayerScope.() -> Unit)? = null,
): Modifier {
  // Letto ad ogni fotogramma, ma tenuto in uno stato: una lambda nuova a ogni ricomposizione del
  // chiamante cambierebbe l'identita' del `layerBlock`, e un modificatore che non e' uguale a se
  // stesso si invalida a ogni ricomposizione.
  val currentReducedMotion by rememberUpdatedState(reducedMotion)
  val layerBlock: GraphicsLayerScope.() -> Unit =
    remember(motion, squashDivisor, squashSigned, squashVertical, place) {
      {
        place?.invoke(this)
        scaleX = motion.scaleX()
        scaleY = motion.scaleY()
        val squash = glassDropSquash(motion.velocity(), squashDivisor, squashSigned, currentReducedMotion())
        if (squashVertical) {
          // Lungo il moto si allunga, di traverso si accorcia: in un menu il moto e' verticale.
          scaleY /= squash.xDivisor
          scaleX *= squash.yFactor
        } else {
          scaleX /= squash.xDivisor
          scaleY *= squash.yFactor
        }
      }
    }
  // Piena a riposo e sparita sotto il dito: e' questa faccia che la lente sostituisce. Per i pomelli e'
  // il bianco, voluto e non del tema (una pillola chiara anche sul tema scuro); per una goccia in una
  // lastra e' quella del tema. Col bianco opaco il conto e' quello di prima, bit per bit: `1 * (1 - p)`,
  // con la pressione tenuta fra 0 e 1 (fuori, il colore l'avrebbe tenuta comunque).
  val onDrawSurface: DrawScope.() -> Unit = remember(motion, face) {
    { drawRect(face.copy(alpha = glassDropFaceAlpha(face, motion.progress()))) }
  }

  // Quale vetro: il materiale nuovo dove puo' esistere (Android 13, sorgenti sane, nessun antenato che chieda il
  // vetro di prima, una forma che il campo sa rendere), altrimenti la ricetta di Kyant, intera.
  val model = rememberFluidGlassModel(shape, GlassFalloff.Uniform, GlassDropOptics)
  if (model == FluidGlassModel.Liquid) {
    return glassDropLiquid(backdrop, motion, shape, layerBlock, onDrawSurface)
  }
  return glassDropKyant(backdrop, motion, lensSize, shape, layerBlock, onDrawSurface)
}

/**
 * La goccia sul materiale nuovo (F1B.3): corpo, luce e ombra di `GlassLiquidSurface` con il profilo della goccia
 * (`glassDropProfile`) e l'ottica dei tasti (`GlassDropOptics`); la pressione e' la profondita' ottica, cioe' quanto la
 * lente piega. La faccia ([onDrawSurface]) sta fra il corpo e la luce: a riposo copre il corpo e porta sopra di se' il
 * filo e il riflesso dei tasti; sotto il dito sfuma, e resta la lente limpida.
 *
 * L'ombra e' quella della ricetta, posata dentro il `layerBlock` (cresce con la goccia) e fuori dal ritaglio; la
 * regola del vetro annidato la riduce quando la goccia sta in una lastra (il nodo lo sa: `GlassNestingScope`).
 */
@Composable
private fun Modifier.glassDropLiquid(
  backdrop: Backdrop,
  motion: GlassDropMotion,
  shape: Shape,
  layerBlock: GraphicsLayerScope.() -> Unit,
  onDrawSurface: DrawScope.() -> Unit,
): Modifier {
  val darkSide = GlassDefaults.isDarkSurface()
  val quality = LocalFluidGlassQuality.current
  val screenDensity = LocalDensity.current.density
  val currentBackdrop by rememberUpdatedState(backdrop)
  val surface = remember(motion, shape, darkSide, screenDensity, quality) {
    GlassLiquidSurface(
      role = GlassDropRole,
      optics = GlassDropOptics,
      shape = shape,
      darkSide = darkSide,
      screenDensity = screenDensity,
      intensity = { 1f },
      // La lente cresce con la pressione, come cresceva quella di Kyant: a riposo piatta, sotto il dito piena.
      opticalDepth = { motion.progress() },
      pressed = { 0f },
      quality = { quality?.level ?: 1f },
      film = { Color.Unspecified },
      tint = { Color.Unspecified },
      touch = { null },
      profile = glassDropProfile(),
      onPanel = { glassSamplesLiquidPanel(currentBackdrop, GlassLiquidPanels::isPublished) },
    )
  }
  val shadow = remember(surface) {
    if (surface.castsShadow) Modifier.glassShadow(surface.shadowOutline, surface.shadow) else Modifier
  }
  // La luce dei tasti (filo e riflesso) viene con la lente: in proporzione alla pressione, e a riposo niente, sul
  // pomello bianco un contorno grigio che Apple non ha (vedi `glassDropLightAmount`). A riposo e a pressione piena si
  // disegna diretta, com'era; in mezzo, per i pochi fotogrammi in cui la faccia sfuma, ogni suo tratto con l'alfa
  // moltiplicato per la pressione ([GlassDropFadedLight]). Non un layer con l'alfa: il riflesso è una somma (`Plus`), e
  // in un layer si sommerebbe al layer vuoto invece che alla goccia; tornando al disegno diretto a pressione piena la
  // luce scatterebbe. La pressione si legge qui, in disegno: un suo cambio ridisegna la luce.
  val fadedLight = remember { GlassDropFadedLight() }
  val onDrawLight: DrawScope.() -> Unit = remember(surface, motion) {
    {
      val amount = glassDropLightAmount(motion.progress())
      when {
        amount <= GlassDropLightEpsilon -> Unit
        amount >= 1f - GlassDropLightEpsilon -> surface.onDrawLight(this)
        else -> fadedLight.draw(this, amount) { surface.onDrawLight(this) }
      }
    }
  }
  return this.drawBackdrop(
    backdrop = backdrop,
    shape = surface.clipShapeBlock,
    effects = surface.effects,
    highlight = null,
    shadow = null,
    innerShadow = null,
    layerBlock = layerBlock,
    // La cattura apre sempre con un'operazione, perche' il corpo esista anche sopra un fondo vuoto.
    onDrawBackdrop = GlassLiquidOnDrawBackdrop,
    onDrawSurface = onDrawSurface,
    // Con un `layerBlock` la cattura resta a risoluzione piena, come in ogni `glassSurface` `Liquid`.
    backdropScale = 1f,
    onDrawLight = onDrawLight,
    underlay = shadow,
  )
}

/** Sotto questa frazione la luce della goccia non si disegna, sopra il suo complemento si disegna diretta. */
private const val GlassDropLightEpsilon = 0.002f

/**
 * La luce della goccia più debole (giro di correzione di F1B.3): lo scope del nodo, con l'alfa di ogni `drawRect`
 * moltiplicato per la quantità di luce. La luce del vetro nuovo (`drawGlassLight`) è fatta di soli `drawRect` (il
 * riflesso in `Plus`, il filo sopra), quindi ogni tratto resta quello che è, con la sua miscela, solo più debole: la
 * stessa cosa che il banco fa con l'`alpha` dei suoi elementi `shader`. Se un giorno la luce disegnasse altro che
 * rettangoli, quel tratto passerebbe a pieno: va aggiunto qui.
 *
 * Tiene l'ultimo scope ricevuto, che il nodo riusa da un fotogramma all'altro: nessuna allocazione in disegno, se non
 * quando lo scope cambia.
 */
internal class GlassDropFadedLight {
  private var faded: Faded? = null

  /** Disegna [block] su [base] con l'alfa di ogni rettangolo per [amount]. */
  fun draw(base: DrawScope, amount: Float, block: DrawScope.() -> Unit) {
    val scope = faded?.takeIf { it.base === base } ?: Faded(base).also { faded = it }
    scope.amount = amount
    scope.block()
  }

  internal class Faded(val base: DrawScope) : DrawScope by base {
    var amount: Float = 1f

    override fun drawRect(
      brush: Brush,
      topLeft: Offset,
      size: Size,
      alpha: Float,
      style: DrawStyle,
      colorFilter: ColorFilter?,
      blendMode: BlendMode,
    ) = base.drawRect(brush, topLeft, size, alpha * amount, style, colorFilter, blendMode)

    override fun drawRect(
      color: Color,
      topLeft: Offset,
      size: Size,
      alpha: Float,
      style: DrawStyle,
      colorFilter: ColorFilter?,
      blendMode: BlendMode,
    ) = base.drawRect(color, topLeft, size, alpha * amount, style, colorFilter, blendMode)
  }
}

/**
 * La goccia di Kyant: la ricetta di sempre, dove il materiale nuovo non c'e' (sotto Android 13, `Classic`). I numeri
 * sono quelli di [GlassDropRecipe] e [lensSize], e non cambiano.
 */
@Composable
private fun Modifier.glassDropKyant(
  backdrop: Backdrop,
  motion: GlassDropMotion,
  lensSize: GlassDropLens,
  shape: Shape,
  layerBlock: GraphicsLayerScope.() -> Unit,
  onDrawSurface: DrawScope.() -> Unit,
): Modifier {
  val shapeBlock = remember(shape) { { shape } }

  val effects: BackdropEffectScope.() -> Unit = remember(motion, lensSize) {
    {
      val progress = motion.progress()
      blur(GlassDropRecipe.RestBlur.toPx() * (1f - progress))
      lens(lensSize.height.toPx() * progress, lensSize.amount.toPx() * progress, chromaticAberration = true)
    }
  }
  val highlight: () -> Highlight? = remember(motion) {
    {
      val progress = motion.progress()
      Highlight.Ambient.copy(
        width = Highlight.Ambient.width / GlassDropRecipe.HighlightDivisor,
        blurRadius = Highlight.Ambient.blurRadius / GlassDropRecipe.HighlightDivisor,
        alpha = progress,
      )
    }
  }
  val shadow: () -> Shadow? = remember {
    {
      Shadow(
        radius = GlassDropRecipe.RestShadowRadius,
        color = Color.Black.copy(alpha = GlassDropRecipe.RestShadowAlpha),
      )
    }
  }
  val innerShadow: () -> InnerShadow? = remember(motion) {
    {
      val progress = motion.progress()
      InnerShadow(radius = GlassDropRecipe.PressedInnerShadowRadius * progress, alpha = progress)
    }
  }

  return this.drawBackdrop(
    backdrop = backdrop,
    shape = shapeBlock,
    effects = effects,
    highlight = highlight,
    shadow = shadow,
    innerShadow = innerShadow,
    layerBlock = layerBlock,
    onDrawSurface = onDrawSurface,
  )
}

/**
 * [glassDrop] su una [GlassDragAnimation]: il caso dei pomelli (slider, interruttore), del segmentato
 * e del rail. Il movimento e' quello delle sue molle ([rememberDropMotion]). La goccia del menù, che ha
 * anche un posto e un asse suoi, chiama la forma generale.
 */
@Composable
internal fun Modifier.glassDrop(
  backdrop: Backdrop,
  drag: GlassDragAnimation,
  lensSize: GlassDropLens,
  face: Color = GlassDropFaces.Light,
  squashDivisor: Float = GlassDropRecipe.SquashDivisor,
  squashSigned: Boolean = false,
  reducedMotion: () -> Boolean = NeverReducedMotion,
  shape: Shape = FluidCapsuleShape,
): Modifier = glassDrop(
  backdrop = backdrop,
  motion = rememberDropMotion(drag),
  lensSize = lensSize,
  face = face,
  squashDivisor = squashDivisor,
  squashSigned = squashSigned,
  reducedMotion = reducedMotion,
  shape = shape,
)
