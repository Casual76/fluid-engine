package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSize
import dev.antigravity.fluidengine.ui.fluid.FluidPressFeedback
import dev.antigravity.fluidengine.ui.fluid.FluidPressSprings
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.fluidPressTransform
import dev.antigravity.fluidengine.ui.glass.interaction.glassButtonGlowRecipe

/**
 * `oggi-tasto`: un tasto tondo di vetro (`FluidGlassIconButton`, 44 dp, `FluidPressFeedback.ExpandRound`)
 * premuto, trascinato un poco verso il basso a sinistra e rilasciato dopo 567 ms: i tempi della clip di
 * Apple (R4 B4, pressione a `f6950`, rilascio a `f6967`).
 *
 * **Da dove vengono i numeri.** Dell'engine: le molle di salita e di ritorno (`FluidPressSprings.ExpandIn`,
 * `ExpandOut`, quelle di LibrePods) e quella della deriva (`LeanReturn`), la trasformazione del tasto per
 * pressione e dito (`fluidPressTransform`, scala, deriva col `tanh`, allungamento), il riquadro trasformato
 * (`fluidPressedBounds`), l'ottica del tasto (`GlassDefaults.buttonOptics`) e la regola della taglia
 * (`glassSizedOptics`).
 *
 * **Ricostruito.** La corsa di `FluidPressMotion` (la salita al contatto e il ritorno che parte dal valore e
 * dalla velocita' del fotogramma prima, senza aspettare l'arrivo: i pulsanti non hanno `waitsForArrival`),
 * la somma del dito di `FluidPressState` (a ogni passo `snapTo`, al rilascio la molla critica verso zero),
 * l'assemblaggio della lente di `glassSurface` (vedi `todayButtonElement`). Il percorso del dito e' una
 * scelta di scena: 16 dp in basso e 16 a sinistra fra 150 e 450 ms, come il dito di Apple che scivola.
 *
 * **Fuori dalla traccia.** Il bagliore del dito (`GlassTouchHighlight` con la ricetta di LibrePods: velo
 * bianco 0,10 e punto 0,15 in `Plus`, largo il lato lungo): sul dispositivo e' meta' di cio' che si vede,
 * qui manca. Il suo shader e' una stringa dentro `GlassInteraction.kt`, che il banco non legge; i numeri di
 * ogni fotogramma sono in `_bagliore`. Mancano anche bordo e ombra.
 */
internal object OggiTasto {
  const val Name = "oggi-tasto"

  private const val Density = 3f
  private const val CanvasWidthDp = 110f
  private const val CanvasHeightDp = 96f

  /** Il rilascio: 17 fotogrammi di Apple dopo la pressione (`f6967 - f6950`), cioe' il fotogramma 34 a 60 Hz. */
  private const val ReleaseFrame = 34
  private const val PreambleFrames = 2
  private const val TailFrames = 3
  private const val MaxFrames = ReleaseFrame + 60

  /** Il dito: fermo fino a 150 ms, poi 16 dp in basso a sinistra in 300 ms. */
  private const val DragStartMs = 150.0
  private const val DragEndMs = 450.0
  private val DragDp = Offset(-16f, 16f)

  private const val Icon = "fluid:ios_share|share"

  fun build(): Built {
    val d = Density
    val palette = TodayPalette()
    val lensDp = FluidGlassSize.Regular.lens.value
    val left = (CanvasWidthDp - lensDp) / 2f
    val top = (CanvasHeightDp - lensDp) / 2f
    val rest = Rect(left * d, top * d, (left + lensDp) * d, (top + lensDp) * d)
    val origin = Offset.Zero
    val feedback = FluidPressFeedback.ExpandRound
    val glow = glassButtonGlowRecipe()

    fun fingerAt(tMs: Double): Offset {
      val f = ((tMs - DragStartMs) / (DragEndMs - DragStartMs)).coerceIn(0.0, 1.0).toFloat()
      return Offset(DragDp.x * d * f, DragDp.y * d * f)
    }

    val pressIn = TodaySpring(FluidPressSprings.ExpandIn, 0f, 1f)
    // Il ritorno parte dal valore e dalla velocita' dell'ultimo fotogramma della salita (Animatable li tiene),
    // e al primo fotogramma di gioco vale ancora quel valore.
    val beforeRelease = glassTraceTimeNanos(ReleaseFrame - 1)
    val pressOut = TodaySpring(
      FluidPressSprings.ExpandOut,
      pressIn.value(beforeRelease),
      0f,
      pressIn.velocity(beforeRelease),
    )
    val leanBack = TodayOffsetSpring(FluidPressSprings.LeanReturn, fingerAt(glassTraceTimeMs(ReleaseFrame - 1)), Offset.Zero)

    val frames = ArrayList<GlassTraceFrame>()
    val widths = ArrayList<Float>()
    var tail = 0
    for (k in -PreambleFrames until MaxFrames) {
      val t = glassTraceTimeMs(k)
      val released = k >= ReleaseFrame
      val sinceRelease = glassTraceTimeNanos(k - ReleaseFrame)
      val progress = when {
        k < 0 -> 0f
        released -> pressOut.value(sinceRelease)
        else -> pressIn.value(glassTraceTimeNanos(k))
      }
      val lean = when {
        k < 0 -> Offset.Zero
        released -> leanBack.value(sinceRelease)
        else -> fingerAt(t)
      }
      val transform = fluidPressTransform(
        feedback = feedback,
        progress = progress,
        lean = lean,
        width = rest.width,
        height = rest.height,
        dp = d,
        reducedMotion = false,
      )
      val content = listOf(
        todayIconLayer(
          name = "icona",
          box = rest.translate(transform.translationX, transform.translationY),
          icon = Icon,
          iconSizeDp = FluidGlassSize.Regular.iconSize.value,
          color = palette.onSurface,
          origin = origin,
          density = d,
          scaleX = transform.scaleX,
          scaleY = transform.scaleY,
        ),
      )
      // Il bagliore com'e' sul dispositivo (`GlassTouchHighlight`): pressione tenuta fra 0 e 1, centro dove il
      // dito e' sceso (il centro del tasto) piu' la strada fatta, nel layer non scalato. Non si rende.
      val g = progress.coerceIn(0f, 1f)
      val extra = mapOf(
        "_bagliore" to linkedMapOf(
          "velo" to glow.veil * g,
          "punto" to glow.spot * g,
          "raggioDp" to lensDp,
          "core" to glow.core,
          "x" to round3((rest.width / 2f + lean.x).coerceIn(0f, rest.width) / d),
          "y" to round3((rest.height / 2f + lean.y).coerceIn(0f, rest.height) / d),
        ),
      )
      val element = todayButtonElement(
        name = "tasto",
        rest = rest,
        transform = transform,
        pressed = progress,
        optics = GlassDefaults.buttonOptics(),
        tint = palette.controlOverlay,
        origin = origin,
        density = d,
        canvasWidthDp = CanvasWidthDp,
        canvasHeightDp = CanvasHeightDp,
        content = content,
        extra = extra,
      )
      frames += GlassTraceFrame(t, listOf(element))
      widths += rest.width * transform.scaleX / d
      val settled = released && pressOut.finished(sinceRelease) && leanBack.finished(sinceRelease)
      if (settled) tail++
      if (tail > TailFrames) break
    }

    val trace = GlassTrace(
      name = Name,
      phase = "f0",
      density = d,
      theme = "light",
      widthDp = CanvasWidthDp,
      heightDp = CanvasHeightDp,
      frames = frames,
      head = head(),
    )
    return Built(trace, widths)
  }

  class Built(val trace: GlassTrace, val widthsDp: List<Float>)

  private fun head(): Map<String, Any?> = linkedMapOf(
    "_" to "Engine di OGGI (F0.5): un tasto tondo di vetro premuto, trascinato e rilasciato coi tempi di R4 B4. " +
      "Numeri dalle funzioni dell'engine, corsa e lente ricostruite nel test (OggiTasto.kt). " +
      "Senza il bagliore del dito (numeri in _bagliore), bordo e ombra.",
    "palette" to "engine",
    "background" to linkedMapOf("kind" to "gradient4", "tl" to "#99989B", "tr" to "#6A6E74", "bl" to "#DDDCDF", "br" to "#595A5F"),
    "apple" to linkedMapOf(
      "video" to "wwdc2025-219_hd",
      "t0Frame" to 6950,
      "crop" to listOf(378, 17, 1135, 990),
      "collage" to "artifacts/liquid-glass/riferimenti/collage/B4_tocco_tasto_f6940-7018_ogni2.jpg",
      "note" to "R4 B4, classe C con il dito: pressione a f6950, rilascio a f6967. Il tasto a riposo e' largo 454 px = 44 dp.",
    ),
    "events" to listOf(
      linkedMapOf("t" to round3(glassTraceTimeMs(ReleaseFrame).toFloat()), "kind" to "mark", "label" to "rilascio (f6967 di Apple)"),
    ),
    "probes" to listOf(
      linkedMapOf("name" to "larghezza", "kind" to "value", "path" to "tasto.pieces[0].w", "label" to "il vetro, dai numeri (dp)", "unit" to "dp"),
      linkedMapOf(
        "name" to "larghezza-pixel",
        "kind" to "width",
        "line" to "h:${CanvasHeightDp / 2f}:0:$CanvasWidthDp",
        "mode" to "light",
        "label" to "il vetro, dai pixel",
      ),
      linkedMapOf("name" to "luce", "kind" to "luma", "rect" to listOf(45, 38, 20, 20), "label" to "il centro del tasto"),
    ),
  )
}
