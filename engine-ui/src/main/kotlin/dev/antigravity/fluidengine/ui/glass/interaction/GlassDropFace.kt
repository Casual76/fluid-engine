/*
 * Copyright 2025 Kyant — Apache License, Version 2.0.
 *
 * Same drop as [glassDrop] (Kyant0's LiquidSlider thumb, commit b18eb0ff12c616546a68c72e7d0097f1ab286c87):
 * the numbers are `GlassDropRecipe`'s and are not repeated here. What this file adds is the two
 * things the thumbs never needed, a face that is not always white and a pressure that does not have
 * to come from a `GlassDragAnimation`.
 */
package dev.antigravity.fluidengine.ui.glass.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.glass.backdrop.Backdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.BackdropEffectScope
import dev.antigravity.fluidengine.ui.glass.backdrop.drawBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.blur
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.lens
import dev.antigravity.fluidengine.ui.glass.backdrop.highlight.Highlight
import dev.antigravity.fluidengine.ui.glass.backdrop.shadow.InnerShadow
import dev.antigravity.fluidengine.ui.glass.backdrop.shadow.Shadow

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
 * La goccia: [glassDrop] con la faccia scelta da chi chiama e il movimento dato come lambda.
 *
 * Stessa ricetta, stessi numeri (`GlassDropRecipe`), stesso posto per la scala (il `layerBlock` di
 * `drawBackdrop`, mai un `graphicsLayer` sopra): la differenza e' la faccia, che a riposo e' [face]
 * al posto del bianco fisso, e il fatto che la pressione possa non essere quella di una molla di
 * trascinamento. [glassDrop] resta com'e' per i pomelli, che il bianco non lo cambiano mai.
 *
 * @param backdrop cio' che la lente piega: la pista, e le etichette tinte che vi stanno sopra.
 * @param face la faccia piena a riposo ([GlassDropFaces.forSurface]); sfuma con la pressione.
 */
@Composable
internal fun Modifier.glassDropFace(
  backdrop: Backdrop,
  motion: GlassDropMotion,
  lensSize: GlassDropLens,
  face: Color,
  squashDivisor: Float = GlassDropRecipe.SquashDivisor,
  squashSigned: Boolean = false,
  reducedMotion: () -> Boolean = NeverReducedMotion,
  shape: Shape = FluidCapsuleShape,
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
  val currentReducedMotion by rememberUpdatedState(reducedMotion)
  val layerBlock: GraphicsLayerScope.() -> Unit =
    remember(motion, squashDivisor, squashSigned) {
      {
        scaleX = motion.scaleX()
        scaleY = motion.scaleY()
        val squash = glassDropSquash(motion.velocity(), squashDivisor, squashSigned, currentReducedMotion())
        scaleX /= squash.xDivisor
        scaleY *= squash.yFactor
      }
    }
  // La faccia che la lente sostituisce: piena a riposo, sparita sotto il dito. Il suo alfa di partenza
  // e' quello di [face], cosi' una faccia traslucida non diventa piena per essere stata data.
  val onDrawSurface: DrawScope.() -> Unit = remember(motion, face) {
    { drawRect(face.copy(alpha = face.alpha * (1f - motion.progress().coerceIn(0f, 1f)))) }
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

/** [glassDropFace] su una [GlassDragAnimation]: il caso del segmentato e del rail. */
@Composable
internal fun Modifier.glassDropFace(
  backdrop: Backdrop,
  drag: GlassDragAnimation,
  lensSize: GlassDropLens,
  face: Color,
  squashDivisor: Float = GlassDropRecipe.SquashDivisor,
  squashSigned: Boolean = false,
  reducedMotion: () -> Boolean = NeverReducedMotion,
  shape: Shape = FluidCapsuleShape,
): Modifier = glassDropFace(
  backdrop = backdrop,
  motion = rememberDropMotion(drag),
  lensSize = lensSize,
  face = face,
  squashDivisor = squashDivisor,
  squashSigned = squashSigned,
  reducedMotion = reducedMotion,
  shape = shape,
)
