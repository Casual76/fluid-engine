package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSize
import dev.antigravity.fluidengine.ui.fluid.FluidPressFeedback
import dev.antigravity.fluidengine.ui.fluid.FluidPressSprings
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.fluidPressTransform
import dev.antigravity.fluidengine.ui.glass.interaction.glassButtonGlowRecipe
import kotlin.math.ceil

/*
 * Un tasto tondo di vetro (`FluidGlassIconButton`, 44 dp, `FluidPressFeedback.ExpandRound`) premuto,
 * trascinato un poco verso il basso a sinistra e rilasciato: un costruttore solo e due scene.
 *
 *  - `oggi-tasto`: la scena del confronto con Apple (R4 B4), quella di F0.5, che non cambia.
 *  - `oggi-tasto-galleria`: **esattamente** il tocco che S1 ha registrato sul Tab S9 («Cerca» della pagina A di
 *    «Pagine»), per convalidare il banco (F0-a).
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
 * l'assemblaggio della lente di `glassSurface` (vedi `todayButtonElement`).
 *
 * **Fuori dalla traccia.** Il bagliore del dito (`GlassTouchHighlight` con la ricetta di LibrePods: velo
 * bianco 0,10 e punto 0,15 in `Plus`, largo il lato lungo): sul dispositivo e' meta' di cio' che si vede,
 * qui manca. Il suo shader e' una stringa dentro `GlassInteraction.kt`, che il banco non legge; i numeri di
 * ogni fotogramma sono in `_bagliore`. Mancano anche bordo e ombra.
 */

/**
 * Una scena del tocco: il tasto, la tela, la cadenza, e il dito (quando scende e' `t = 0`; da [dragStartMs] a
 * [dragEndMs] scivola di [dragDp] in modo uniforme; dal fotogramma [releaseFrame] e' su).
 */
internal class TapScene(
  val name: String,
  val fps: Int,
  val density: Float,
  val canvasWidthDp: Float,
  val canvasHeightDp: Float,
  /** La lente a riposo, in dp della tela. */
  val lensDp: Rect,
  val icon: String,
  val iconSizeDp: Float,
  val iconColor: (TodayPalette) -> Color,
  /** Il primo fotogramma senza dito: il ritorno parte li', dal valore del fotogramma prima. */
  val releaseFrame: Int,
  val dragStartMs: Double,
  val dragEndMs: Double,
  val dragDp: Offset,
  val preambleFrames: Int,
  val tailFrames: Int,
  val maxFrames: Int,
  val head: () -> Map<String, Any?>,
)

internal class TapBuilt(val trace: GlassTrace, val widthsDp: List<Float>)

internal fun buildTapTrace(scene: TapScene): TapBuilt {
  val d = scene.density
  val fps = scene.fps
  val palette = TodayPalette()
  val lensDp = scene.lensDp.width
  val rest = scene.lensDp.scaled(d)
  val origin = Offset.Zero
  val feedback = FluidPressFeedback.ExpandRound
  val glow = glassButtonGlowRecipe()

  fun fingerAt(tMs: Double): Offset {
    val f = ((tMs - scene.dragStartMs) / (scene.dragEndMs - scene.dragStartMs)).coerceIn(0.0, 1.0).toFloat()
    return Offset(scene.dragDp.x * d * f, scene.dragDp.y * d * f)
  }

  val releaseFrame = scene.releaseFrame
  val pressIn = TodaySpring(FluidPressSprings.ExpandIn, 0f, 1f)
  // Il ritorno parte dal valore e dalla velocita' dell'ultimo fotogramma della salita (Animatable li tiene),
  // e al primo fotogramma di gioco vale ancora quel valore.
  val beforeRelease = glassTraceTimeNanos(releaseFrame - 1, fps)
  val pressOut = TodaySpring(
    FluidPressSprings.ExpandOut,
    pressIn.value(beforeRelease),
    0f,
    pressIn.velocity(beforeRelease),
  )
  val leanBack = TodayOffsetSpring(FluidPressSprings.LeanReturn, fingerAt(glassTraceTimeMs(releaseFrame - 1, fps)), Offset.Zero)

  val frames = ArrayList<GlassTraceFrame>()
  val widths = ArrayList<Float>()
  var tail = 0
  for (k in -scene.preambleFrames until scene.maxFrames) {
    val t = glassTraceTimeMs(k, fps)
    val released = k >= releaseFrame
    val sinceRelease = glassTraceTimeNanos(k - releaseFrame, fps)
    val progress = when {
      k < 0 -> 0f
      released -> pressOut.value(sinceRelease)
      else -> pressIn.value(glassTraceTimeNanos(k, fps))
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
        icon = scene.icon,
        iconSizeDp = scene.iconSizeDp,
        color = scene.iconColor(palette),
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
      canvasWidthDp = scene.canvasWidthDp,
      canvasHeightDp = scene.canvasHeightDp,
      content = content,
      extra = extra,
    )
    frames += GlassTraceFrame(t, listOf(element))
    widths += rest.width * transform.scaleX / d
    val settled = released && pressOut.finished(sinceRelease) && leanBack.finished(sinceRelease)
    if (settled) tail++
    if (tail > scene.tailFrames) break
  }

  val trace = GlassTrace(
    name = scene.name,
    phase = "f0",
    density = d,
    theme = "light",
    widthDp = scene.canvasWidthDp,
    heightDp = scene.canvasHeightDp,
    frames = frames,
    fps = fps,
    head = scene.head(),
  )
  return TapBuilt(trace, widths)
}

/**
 * `oggi-tasto`: la scena del confronto con Apple (R4 B4), quella di F0.5. Il tasto e' un
 * `FluidGlassIconButton` da 44 dp al centro di una tela da 110 x 96 dp, densita' 3, 60 Hz; il rilascio e' il
 * fotogramma 34 (17 fotogrammi di Apple dopo la pressione, `f6967 - f6950`); il dito fermo fino a 150 ms, poi
 * 16 dp in basso a sinistra in 300 ms, come il dito di Apple che scivola.
 */
internal object OggiTasto {
  const val Name = "oggi-tasto"

  /** L'icona del tasto `Regular` della 3.1.2 (`FluidGlassSize.Regular.iconSize` prima di F1A.11a). */
  const val OggiIconSizeDp = 22f

  private const val CanvasWidthDp = 110f
  private const val CanvasHeightDp = 96f
  private const val ReleaseFrame = 34

  fun scene(): TapScene {
    val lens = FluidGlassSize.Regular.lens.value
    val left = (CanvasWidthDp - lens) / 2f
    val top = (CanvasHeightDp - lens) / 2f
    return TapScene(
      name = Name,
      fps = 60,
      density = 3f,
      canvasWidthDp = CanvasWidthDp,
      canvasHeightDp = CanvasHeightDp,
      lensDp = Rect(left, top, left + lens, top + lens),
      icon = "fluid:ios_share|share",
      // La taglia dell'icona di **allora**: la traccia è il tasto della 3.1.2, e il suo `iconSize` era 22 dp. Da F1A.11a
      // `FluidGlassSize.Regular.iconSize` è 26 (il set di icone nuovo): leggerla da lì cambierebbe una traccia «di
      // oggi» che deve restare la registrazione del vetro di prima (e `tools/glassbench/traces/` non deve muoversi).
      iconSizeDp = OggiIconSizeDp,
      iconColor = { it.onSurface },
      releaseFrame = ReleaseFrame,
      dragStartMs = 150.0,
      dragEndMs = 450.0,
      dragDp = Offset(-16f, 16f),
      preambleFrames = 2,
      tailFrames = 3,
      maxFrames = ReleaseFrame + 60,
      head = ::head,
    )
  }

  fun build(): TapBuilt = buildTapTrace(scene())

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

/**
 * `oggi-tasto-galleria`: il tocco che S1 ha registrato (`S1/video/adesso-t-tasto.mp4`): «Cerca», l'azione
 * della barra della pagina A di «Pagine» (`sample/…/PagesDemo.kt`), con `input swipe 2496 111 2462 145 567`
 * (`S1/strumenti/s1lib.sh`), Tab S9 in orizzontale, 120 Hz, densita' 2,125.
 *
 * **Dai sorgenti e dalla taglia del tablet.** «Cerca» e' una `FluidBarAction` senza menu, cioe' un
 * `FluidGlassIconButton` (`ExpandRound`) con l'icona `Search` da 22 dp nell'accento; e' l'unica azione, quindi
 * la sua lente sta fra W - 52 e W - 8 dp (bersaglio da 48 dp, 6 dp dal bordo), con W = 2560 / 2,125 =
 * 1204,7 dp. Il dito: `input swipe` scende a (2496, 111) px, scivola in modo uniforme di (-34, +34) px =
 * (-16, +16) dp per tutti i 567 ms e si alza alla fine; la deriva parte dal primo spostamento (la pressione
 * dell'engine non ha soglia). Il ritorno parte al primo fotogramma dopo il rilascio: 567 ms cadono fra il
 * fotogramma 68 (566,7 ms) e il 69 (575 ms).
 *
 * **Letto dai fotogrammi, dichiarato.** La quota della riga dei controlli: il centro della lente e' a 110–111 px
 * (110 nel video, 111 nel dump della UI di S1), quindi la barra di stato e' alta 64 px = 30,12 dp e la lente sta
 * fra 30,12 e 74,12 dp. Il colore della pagina, #EFF0F5. La tela e' 238 x 204 px dello schermo (x da 2322 a
 * 2560, y da 0 a 204): ci stanno anche le icone della barra di stato, che il banco non disegna.
 */
internal object OggiTastoGalleria {
  const val Name = "oggi-tasto-galleria"

  const val Density = 2.125f
  const val Fps = 120

  /** La tela nello schermo: x da 2322 px, larga 238 px (112 dp), alta 204 px (96 dp). */
  const val CanvasLeftPx = 2322
  const val CanvasWidthDp = 112f
  const val CanvasHeightDp = 96f

  const val StatusBarDp = 64f / Density
  const val ScreenWidthDp = 2560f / Density

  /** Il dito si alza a 567 ms: il primo fotogramma senza dito e' il 69 (575 ms). */
  const val ReleaseMs = 567.0
  val ReleaseFrame: Int = ceil(ReleaseMs * Fps / 1000.0).toInt()

  fun scene(): TapScene {
    val left = ScreenWidthDp - 52f - CanvasLeftPx / Density
    return TapScene(
      name = Name,
      fps = Fps,
      density = Density,
      canvasWidthDp = CanvasWidthDp,
      canvasHeightDp = CanvasHeightDp,
      lensDp = Rect(left, StatusBarDp, left + 44f, StatusBarDp + 44f),
      icon = "fluid:search|circle",
      iconSizeDp = 22f,
      iconColor = { it.primary },
      releaseFrame = ReleaseFrame,
      dragStartMs = 0.0,
      dragEndMs = ReleaseMs,
      dragDp = Offset(-34f / Density, 34f / Density),
      preambleFrames = 2,
      tailFrames = 6,
      maxFrames = ReleaseFrame + 120,
      head = ::head,
    )
  }

  fun build(): TapBuilt = buildTapTrace(scene())

  private fun head(): Map<String, Any?> = linkedMapOf(
    "_" to "Engine di OGGI, la scena registrata in S1 (F0-a): «Cerca» della pagina A di «Pagine» sul Tab S9, " +
      "input swipe di 567 ms con (-16, +16) dp, 120 Hz, densita' 2,125. Geometria dai sorgenti; quota della riga " +
      "e colore della pagina letti dai fotogrammi (OggiTasto.kt). Senza bagliore (numeri in _bagliore), bordo, " +
      "ombra e barra di stato.",
    "palette" to "engine",
    "background" to linkedMapOf("kind" to "color", "color" to "#EFF0F5"),
    "events" to listOf(
      linkedMapOf("t" to round3(ReleaseMs.toFloat()), "kind" to "mark", "label" to "il dito si alza (input swipe, 567 ms)"),
    ),
    "probes" to listOf(
      linkedMapOf("name" to "larghezza", "kind" to "value", "path" to "tasto.pieces[0].w", "label" to "il vetro, dai numeri (dp)", "unit" to "dp"),
    ),
  )
}
