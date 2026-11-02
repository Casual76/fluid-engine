/*
 * Copyright 2025 Kyant — Apache License, Version 2.0.
 *
 * The drop is the thumb of Kyant0's LiquidSlider and LiquidToggle, commit
 * b18eb0ff12c616546a68c72e7d0097f1ab286c87 (https://github.com/Kyant0/AndroidLiquidGlass), taken
 * out of the two controls that each carried a copy of it. The numbers are Kyant's and are not
 * changed here: blur 8 dp, lens 10/14 dp (slider) and 5/10 dp (toggle), Ambient highlight divided
 * by 1.5, inner shadow 4 dp, a fixed 4 dp shadow at 5%, the white face that the lens replaces, and
 * the squash `scaleX / (1 - v * 0.75)`, `scaleY * (1 - v * 0.25)` capped at 0.2.
 */
package dev.antigravity.fluidengine.ui.glass.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.glass.backdrop.Backdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.BackdropEffectScope
import dev.antigravity.fluidengine.ui.glass.backdrop.drawBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.blur
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.lens
import dev.antigravity.fluidengine.ui.glass.backdrop.highlight.Highlight
import dev.antigravity.fluidengine.ui.glass.backdrop.shadow.InnerShadow
import dev.antigravity.fluidengine.ui.glass.backdrop.shadow.Shadow
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

/** Il valore di serie di `reducedMotion`: un'istanza sola, cosi' non cambia l'identita' del modificatore. */
internal val NeverReducedMotion: () -> Boolean = { false }

/**
 * La goccia: la superficie di vetro di un pomello, di Kyant, con la molla di [GlassDragAnimation].
 *
 *  - **a riposo piena** — la faccia e' bianca, la lente e' spenta, la sfocatura e' 8 dp;
 *  - **sotto il dito diventa lente**: la sfocatura scende a zero, la lente sale a [lensSize] con la
 *    dispersione, il bagliore `Ambient` e l'ombra interna arrivano a pieno, e la faccia bianca sfuma;
 *  - **cresce e si deforma** con le molle di [drag]: scala X e Y separate, e lo squash dalla
 *    velocita' ([glassDropSquash]).
 *
 * Nel `layerBlock` e non in un `graphicsLayer` antenato: il renderer inverte quella trasformazione
 * quando campiona, quindi la lente ha in mano la pista che c'e' davvero dietro. La traslazione
 * lungo la pista la mette chi chiama, in un `graphicsLayer` *prima* di questo modificatore.
 *
 * @param backdrop cio' che la lente piega: la pista, di solito, sopra la superficie in scope.
 * @param squashDivisor [GlassDropRecipe.SquashDivisor], o quello dell'interruttore.
 * @param squashSigned vedi [glassDropSquash].
 * @param reducedMotion letto nel `layerBlock`, a ogni fotogramma.
 */
@Composable
internal fun Modifier.glassDrop(
  backdrop: Backdrop,
  drag: GlassDragAnimation,
  lensSize: GlassDropLens,
  squashDivisor: Float = GlassDropRecipe.SquashDivisor,
  squashSigned: Boolean = false,
  reducedMotion: () -> Boolean = NeverReducedMotion,
  shape: Shape = FluidCapsuleShape,
): Modifier {
  val shapeBlock = remember(shape) { { shape } }

  val effects: BackdropEffectScope.() -> Unit = remember(drag, lensSize) {
    {
      val progress = drag.pressProgress
      blur(GlassDropRecipe.RestBlur.toPx() * (1f - progress))
      lens(lensSize.height.toPx() * progress, lensSize.amount.toPx() * progress, chromaticAberration = true)
    }
  }
  val highlight: () -> Highlight? = remember(drag) {
    {
      val progress = drag.pressProgress
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
  val innerShadow: () -> InnerShadow? = remember(drag) {
    {
      val progress = drag.pressProgress
      InnerShadow(radius = GlassDropRecipe.PressedInnerShadowRadius * progress, alpha = progress)
    }
  }
  // Letto ad ogni fotogramma, ma tenuto in uno stato: una lambda nuova a ogni ricomposizione del
  // chiamante cambierebbe l'identita' del `layerBlock`, e un modificatore che non e' uguale a se
  // stesso si invalida a ogni ricomposizione.
  val currentReducedMotion by rememberUpdatedState(reducedMotion)
  val layerBlock: GraphicsLayerScope.() -> Unit =
    remember(drag, squashDivisor, squashSigned) {
      {
        scaleX = drag.scaleX
        scaleY = drag.scaleY
        val squash = glassDropSquash(drag.velocity, squashDivisor, squashSigned, currentReducedMotion())
        scaleX /= squash.xDivisor
        scaleY *= squash.yFactor
      }
    }
  // Bianca a riposo e sparita sotto il dito: e' questo bianco che la lente sostituisce. Il bianco e'
  // voluto e non del tema: e' la superficie della lente, che resta una pillola chiara anche sul
  // tema scuro.
  val onDrawSurface: DrawScope.() -> Unit = remember(drag) {
    { drawRect(Color.White.copy(alpha = 1f - drag.pressProgress)) }
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
