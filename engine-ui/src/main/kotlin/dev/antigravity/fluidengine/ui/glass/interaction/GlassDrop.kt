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
 *
 * 4.0, F6.3 (piano «iOS in Android»): la **salita nel vetro** dei pomelli di interruttore e cursore
 * ([GlassDropRise], [rememberGlassDropRiseMotion], [GlassDropStretch], [glassDropGlow]), misurata su `UISwitch` e
 * `UISlider` di iOS 26 a tempo vero (WWDC25, sessione 284). E' codice del Fluid Engine, non di Kyant: la goccia
 * resta la sua, cambia come cresce, come si stira e la luce colorata che le sta attorno.
 */
package dev.antigravity.fluidengine.ui.glass.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassFalloff
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassPreference
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassQuality
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.rememberGlassMaterialAccessibility
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
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

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
 * @param shadowFade quanta ombra scura la goccia perde a pressione piena (F6.3): uno per i pomelli che salgono nel
 *   vetro, sotto la cui lente Apple non ha un'ombra ma un alone colorato ([glassDropGlow]); zero, di serie, per le
 *   altre gocce, che restano com'erano al bit.
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
  shadowFade: Float = 0f,
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
    return glassDropLiquid(backdrop, motion, shape, layerBlock, onDrawSurface, shadowFade)
  }
  return glassDropKyant(backdrop, motion, lensSize, shape, layerBlock, onDrawSurface, shadowFade)
}

/** La forza dell'ombra scura a una pressione: `1 − fade · p`, tenuta fra 0 e 1. */
internal fun glassDropShadowAmount(fade: Float, progress: Float): Float =
  (1f - fade * progress.fastCoerceIn(0f, 1f)).fastCoerceIn(0f, 1f)

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
  shadowFade: Float,
): Modifier {
  val darkSide = GlassDefaults.isDarkSurface()
  val quality = LocalFluidGlassQuality.current
  val accessibility = rememberGlassMaterialAccessibility()
  // Il cursore dell'utente (giro di correzione di F2), come in ogni lastra ferma: del corpo limpido muove il tono al
  // posto del velo. Entra fra le chiavi perché cambia anche il raggio, cioè il margine della cattura, come in
  // `glassSurface`. A metà corsa non tocca niente.
  val preference = LocalFluidGlassPreference.current
  val screenDensity = LocalDensity.current.density
  val currentBackdrop by rememberUpdatedState(backdrop)
  val surface = remember(motion, shape, darkSide, screenDensity, quality, preference) {
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
      preference = preference,
      accessibility = { accessibility.value },
    )
  }
  val shadow = remember(surface, shadowFade) {
    when {
      !surface.castsShadow -> Modifier
      shadowFade <= 0f -> Modifier.glassShadow(surface.shadowOutline, surface.shadow)
      // Solo l'alfa: la maschera registrata resta buona (`GlassShadowSpec.scaled`), e la pressione letta in disegno
      // ridisegna l'ombra e basta.
      else -> Modifier.glassShadow(surface.shadowOutline) {
        surface.shadow()?.scaled(glassDropShadowAmount(shadowFade, motion.progress()))
      }
    }
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
  shadowFade: Float,
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
  val shadow: () -> Shadow? = remember(motion, shadowFade) {
    {
      val amount = if (shadowFade <= 0f) 1f else glassDropShadowAmount(shadowFade, motion.progress())
      Shadow(
        radius = GlassDropRecipe.RestShadowRadius,
        color = Color.Black.copy(alpha = GlassDropRecipe.RestShadowAlpha * amount),
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

// ---------------------------------------------------------------------------------------------
// F6.3 · La salita nel vetro dei pomelli
// ---------------------------------------------------------------------------------------------

/**
 * I numeri della **salita nel vetro** dei pomelli di interruttore e cursore (F6.3), in un posto solo.
 *
 * Misurati su `UISwitch` e `UISlider` di iOS 26 registrati a schermo, a tempo vero (WWDC25, sessione 284, 29,97 fps;
 * misure e fogli in `artifacts/liquid-glass/sessioni/f6-3/apple/`), e sul tocco dell'interruttore della sessione 219
 * (R4 B5, media):
 *  - **pressione**: il bianco sfuma e la lente cresce insieme, a ×1,48 (interruttore) e ×1,56 (cursore) del pomello, in
 *    130–170 ms e **senza superamento**; al rilascio torna pomello in 70–150 ms, e il bianco rientra entro ~200 ms;
 *  - **tenuta**: la lente si deforma con l'**accelerazione**, con il segno e ad area quasi costante: accelerando nel verso
 *    del valore si allunga e si abbassa (fino a +16% e −19%), frenando si stringe e si alza; ferma torna in ~250 ms;
 *  - **tocco sull'interruttore** (219): la lente si forma sul posto (~100 ms), poi corre (~200 ms), poi si richiude.
 *
 * La crescita dei pomelli prima di F6.3 era la molla della scala della goccia di Kyant (0,6 / 250 e 0,7 / 250 di
 * `FluidMotion`): arrivava con un superamento del 9,5%, il gonfiarsi e sgonfiarsi che Apple non ha. Ora segue la
 * pressione (la molla critica `FluidMotion.DropPress`, che sulla curva misurata cade fra i due controlli di Apple).
 */
internal object GlassDropRise {

  /** Quanto cresce la lente sotto il dito: ×1,5 (Apple 1,48 l'interruttore, 1,56 il cursore; R4 B5 ≈1,5–1,6). */
  const val Grown = 1.5f

  /** Con le animazioni ridotte la crescita si dimezza, come quella dei tasti (`FluidPress`). */
  const val ReducedGrown = 1.25f

  /**
   * Lo stiramento per dp/s di velocità che il passa-alto lascia passare (vedi [GlassDropStretch]). Tarato sul cursore
   * di Apple (284, f1523–f1633): con il moto misurato come dito, la larghezza prevista sta a 0,05 (radice dello scarto
   * quadratico medio) da quella misurata, in tre tratti (partenza, corsa e frenata, arresto).
   */
  const val StretchGain = 0.0008f

  /** La pulsazione della velocità «lenta» che il passa-alto sottrae: 1 / 70 ms. È quanto dura uno stiramento. */
  const val StretchFollowOmega = 14.3f

  /** Il tetto dello stiramento: Apple arriva a +16% in larghezza su un lancio vero del cursore. */
  const val StretchCap = 0.18f

  /**
   * Il tetto dello stiramento del pomello dell'interruttore: la sua corsa è di 20 dp, e la lente di Apple trascinata
   * (284, f727–f772) cambia altezza di +8% partendo e di +12% fermandosi in fondo, non di più. Col tetto del cursore
   * la lente partiva quasi tonda.
   */
  const val SwitchStretchCap = 0.1f

  /**
   * La rigidezza della molla critica con cui il colore della pista dell'interruttore segue il pomello: 400 (ω = 20,
   * ~100 ms di ritardo). Apple (284) comincia a spegnere il verde appena il pomello parte e finisce 170–330 ms dopo,
   * con un ritardo di 70–130 ms sulla posizione.
   */
  const val TrackTintStiffness = 400f

  /** Sotto questo lo stiramento è zero, e chi lo guida smette di chiedere fotogrammi. */
  const val StretchRest = 0.002f

  /**
   * Il passo massimo dell'integrazione della velocità lenta: un fotogramma lungo (30 Hz, un salto del renderer) si
   * spezza in passi di 4 ms, così la stessa corsa dà la stessa deformazione a 60 e a 120 Hz.
   */
  const val StretchMaxStepSeconds = 0.004f

  /**
   * Tocco sull'interruttore: quanto la lente si forma sul posto prima di correre. Apple (219, f7756–f7760) la mostra
   * formata in ~100–117 ms e solo allora la fa partire; a 100 ms la pressione critica è all'82%.
   */
  const val TapTravelDelayMillis = 100L

  /**
   * Tocco sull'interruttore: quanto la lente resta formata dall'altra parte prima di richiudersi. Apple (219) la
   * lascia arrivare (f7764) e la richiude da f7766: due fotogrammi, ~60 ms, **a occhio** su un render.
   */
  const val TapDwellMillis = 60L
}

/** La taglia della lente a una pressione: da uno a [grown], senza superamento (la pressione è tenuta in 0..1). */
internal fun glassDropRiseScale(progress: Float, grown: Float): Float =
  1f + (grown - 1f) * progress.fastCoerceIn(0f, 1f)

/**
 * Lo stiramento della lente dal suo moto, puro: il passa-alto della velocità lungo la pista.
 *
 * La velocità del fotogramma ([step]: la posizione della goccia in dp, nel verso del valore) meno una velocità **lenta**
 * che la insegue con una molla critica ([GlassDropRise.StretchFollowOmega]): a velocità costante la differenza va a
 * zero, e resta solo quando la goccia accelera o frena. Moltiplicata per [GlassDropRise.StretchGain] e tenuta entro
 * [GlassDropRise.StretchCap], è lo stiramento con il suo segno: positivo accelerando nel verso del valore (la lente si
 * allunga), negativo frenando o partendo all'indietro (si stringe e si alza). È ciò che fa la lente di Apple: lo
 * stesso verso di trascinamento la allunga mentre parte e la stringe mentre frena (vedi [GlassDropRise]).
 *
 * Prima di F6.3 lo squash dei pomelli veniva dalla velocità (il modulo per il cursore, il segno per l'interruttore,
 * [glassDropSquash]): a velocità costante restava, e sulla lente di Apple a velocità costante non c'è. La forma resta
 * quella di [glassDropSquash] per chi non sale nel vetro (la pillola della tab bar, il segmentato, il rail).
 *
 * Non alloca: si chiama a ogni fotogramma di un trascinamento.
 */
internal class GlassDropStretch(
  private val gain: Float = GlassDropRise.StretchGain,
  private val omega: Float = GlassDropRise.StretchFollowOmega,
  private val cap: Float = GlassDropRise.StretchCap,
) {
  private var position = Float.NaN
  private var slow = 0f
  private var slowSpeed = 0f

  /** Lo stiramento dell'ultimo passo. */
  var value: Float = 0f
    private set

  /** La velocità della goccia nell'ultimo passo, in dp/s nel verso del valore. */
  var velocity: Float = 0f
    private set

  /** Riparte da fermo a [positionDp]. */
  fun reset(positionDp: Float) {
    position = positionDp
    slow = 0f
    slowSpeed = 0f
    value = 0f
    velocity = 0f
  }

  /**
   * Un fotogramma: la goccia è a [positionDp] dopo [dtSeconds]. Il primo passo dopo [reset] (o un passo nullo) non ha
   * una velocità e non cambia niente.
   */
  fun step(positionDp: Float, dtSeconds: Float): Float {
    if (position.isNaN() || dtSeconds <= 0f) {
      position = positionDp
      return value
    }
    velocity = (positionDp - position) / dtSeconds
    position = positionDp
    val steps = ceil(dtSeconds / GlassDropRise.StretchMaxStepSeconds).toInt().coerceIn(1, 64)
    val h = dtSeconds / steps
    repeat(steps) {
      val acceleration = omega * omega * (velocity - slow) - 2f * omega * slowSpeed
      slowSpeed += acceleration * h
      slow += slowSpeed * h
    }
    value = (gain * (velocity - slow)).fastCoerceIn(-cap, cap)
    return value
  }

  /** Vero quando non c'è più niente da far vedere: lo stiramento è a zero e la velocità lenta anche. */
  val isSettled: Boolean
    get() = abs(value) < GlassDropRise.StretchRest && abs(slow) * gain < GlassDropRise.StretchRest &&
      abs(slowSpeed) * gain < GlassDropRise.StretchRest * omega
}

/**
 * Lo stiramento di una lente che corre **senza il dito** (un tocco sull'interruttore, un valore che arriva da fuori):
 * solo in avanti, nel verso del moto, mentre accelera; frenando non si stringe. Così fa il pomello toccato nella 219
 * (f7761–f7763: la lente che attraversa la pista è più lunga, all'arrivo torna la sua); col dito, invece, lo stiramento
 * ha il suo segno (284). Pura.
 */
internal fun glassDropLeadingStretch(stretch: Float, velocity: Float): Float = when {
  velocity > 0f -> max(stretch, 0f)
  velocity < 0f -> max(-stretch, 0f)
  else -> 0f
}

/**
 * Le due scale della lente: la crescita [glassDropRiseScale] e lo stiramento [stretch] **ad area costante**
 * (`x · (1 + e)`, `y / (1 + e)`), con lo stiramento pesato dalla pressione: un pomello bianco che si muove senza
 * il dito (un valore che arriva da fuori) non si deforma. Restituite come [GlassDropSquash], senza allocare:
 * `xDivisor` è la scala X e `yFactor` la scala Y.
 */
internal fun glassDropRiseScales(progress: Float, grown: Float, stretch: Float): GlassDropSquash {
  val s = glassDropRiseScale(progress, grown)
  val e = stretch * progress.fastCoerceIn(0f, 1f)
  return GlassDropSquash(s * (1f + e), s / (1f + e))
}

/** La crescita di serie o quella dimezzata delle animazioni ridotte. */
internal fun riseGrown(reducedMotion: Boolean): Float =
  if (reducedMotion) GlassDropRise.ReducedGrown else GlassDropRise.Grown

/**
 * Il moto della goccia che **sale nel vetro** (F6.3): la pressione di [drag], la taglia di [glassDropRiseScales] e lo
 * stiramento di [GlassDropStretch] sulla posizione della goccia lungo la pista, in dp ([travelDp]: la corsa intera,
 * positiva nel verso del valore; da destra a sinistra il controllo si specchia e lo stiramento con lui).
 *
 * La velocità della [GlassDropMotion] è zero: lo squash di [glassDrop] (quello di Kyant, dalla velocità) per questi
 * pomelli non c'è più, la forma è tutta qui. Le molle della scala di [drag] non servono: chi usa questo moto le tiene
 * a uno (`pressedScale = 1`).
 *
 * Lo stiramento si calcola fotogramma per fotogramma solo mentre [drag] si muove, e finché non è tornato a zero: a
 * riposo questo effetto non chiede fotogrammi.
 *
 * [followsFinger] dice se la goccia la sta portando il dito: allora lo stiramento ha il suo segno; se no (il pomello che
 * corre dopo un tocco, un valore da fuori) la lente si allunga soltanto nel verso del moto ([glassDropLeadingStretch]).
 */
@Composable
internal fun rememberGlassDropRiseMotion(
  drag: GlassDragAnimation,
  travelDp: () -> Float,
  reducedMotion: () -> Boolean,
  followsFinger: () -> Boolean = { true },
  stretchCap: Float = GlassDropRise.StretchCap,
): GlassDropMotion {
  val currentTravel by rememberUpdatedState(travelDp)
  val currentReduced by rememberUpdatedState(reducedMotion)
  val currentFinger by rememberUpdatedState(followsFinger)
  val stretch = remember(drag) { mutableFloatStateOf(0f) }
  val model = remember(drag, stretchCap) { GlassDropStretch(cap = stretchCap) }
  LaunchedEffect(drag, model) {
    snapshotFlow { drag.isAnimating }.collectLatest { animating ->
      if (!animating && model.isSettled) {
        stretch.floatValue = 0f
        return@collectLatest
      }
      var last = -1L
      model.reset(drag.progress * currentTravel())
      while (true) {
        val now = withFrameNanos { it }
        val dt = if (last < 0L) 0f else (now - last) / 1e9f
        last = now
        val e = if (currentReduced()) {
          model.reset(drag.progress * currentTravel())
          0f
        } else {
          val raw = model.step(drag.progress * currentTravel(), dt)
          if (currentFinger()) raw else glassDropLeadingStretch(raw, model.velocity)
        }
        stretch.floatValue = e
        if (!drag.isAnimating && model.isSettled) {
          stretch.floatValue = 0f
          break
        }
      }
    }
  }
  return remember(drag) {
    GlassDropMotion(
      progress = { drag.pressProgress },
      scaleX = { glassDropRiseScales(drag.pressProgress, riseGrown(currentReduced()), stretch.floatValue).xDivisor },
      scaleY = { glassDropRiseScales(drag.pressProgress, riseGrown(currentReduced()), stretch.floatValue).yFactor },
      velocity = { 0f },
    )
  }
}

/**
 * L'alone colorato della lente tenuta (F6.3): sotto e attorno alla lente di Apple non c'è un'ombra scura ma una luce
 * del colore di ciò che la lente ha sotto, più forte in basso (284: sotto la lente dell'interruttore acceso il fondo
 * prende il verde, −19 livelli di rosso su 243, fino a ~0,4 altezze di lente; attorno a quella del cursore l'azzurro,
 * anche sopra). Un'ellisse con il centro sotto quello della lente, piena fino a [Core] del suo raggio e poi a zero:
 * la lente, che le sta sopra, ne lascia vedere solo l'anello.
 *
 * Le grandezze sono in multipli dell'**altezza** della lente in quel fotogramma (scala compresa), così l'alone cresce
 * con lei e si rende uguale sul banco (`tools/glassbench/traces/f6-3/alone-goccia.agsl`).
 */
internal object GlassDropGlow {
  /** L'alfa piena, a pressione uno: un decimo del colore (284: 0,10 sul cursore, 0,08–0,10 sull'interruttore). */
  const val Alpha = 0.10f

  /** Di quanto il centro sta sotto quello della lente. */
  const val CenterDrop = 0.16f

  /** Il raggio orizzontale: mezza lente più questo. */
  const val SpreadX = 0.25f

  /** Il raggio verticale. */
  const val RadiusY = 0.78f

  /** Fin dove l'alone è pieno, in frazione del raggio. */
  const val Core = 0.6f
}

/**
 * La geometria dell'alone di una lente larga [lensW] e alta [lensH] (taglia del fotogramma) alla pressione
 * [progress]: centro rispetto a quello della lente, i due raggi, l'alfa. Pura; stessa unità della lente.
 */
internal data class GlassDropGlowShape(val centerDy: Float, val radiusX: Float, val radiusY: Float, val alpha: Float)

internal fun glassDropGlow(lensW: Float, lensH: Float, progress: Float): GlassDropGlowShape = GlassDropGlowShape(
  centerDy = lensH * GlassDropGlow.CenterDrop,
  radiusX = lensW / 2f + lensH * GlassDropGlow.SpreadX,
  radiusY = lensH * GlassDropGlow.RadiusY,
  alpha = GlassDropGlow.Alpha * progress.fastCoerceIn(0f, 1f),
)

/**
 * Disegna l'alone [glassDropGlow] dietro la goccia: va **prima** di `glassDrop` nella catena, dopo la traslazione del
 * pomello, sullo stesso nodo (la sua taglia è quella del pomello a riposo; la lente cresce attorno al centro con le
 * scale di [motion]). [color] è letto in disegno: l'accento, o il colore della pista che cambia.
 */
internal fun Modifier.glassDropGlow(motion: GlassDropMotion, color: () -> Color): Modifier = drawBehind {
  val p = motion.progress()
  if (p <= GlassDropRise.StretchRest) return@drawBehind
  val lensW = size.width * motion.scaleX()
  val lensH = size.height * motion.scaleY()
  val glow = glassDropGlow(lensW, lensH, p)
  if (glow.alpha <= 0f || glow.radiusX <= 0f || glow.radiusY <= 0f) return@drawBehind
  val c = color()
  val full = c.copy(alpha = glow.alpha)
  val clear = c.copy(alpha = 0f)
  val cx = size.width / 2f
  val cy = size.height / 2f + glow.centerDy
  // Un gradiente radiale in un cerchio di raggio `radiusY`, stirato in orizzontale: l'ellisse.
  scale(scaleX = glow.radiusX / glow.radiusY, scaleY = 1f, pivot = Offset(cx, cy)) {
    drawCircle(
      brush = Brush.radialGradient(
        0f to full,
        GlassDropGlow.Core to full,
        1f to clear,
        center = Offset(cx, cy),
        radius = glow.radiusY,
      ),
      radius = glow.radiusY,
      center = Offset(cx, cy),
    )
  }
}
