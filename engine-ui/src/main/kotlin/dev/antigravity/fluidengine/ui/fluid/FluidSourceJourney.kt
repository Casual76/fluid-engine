package dev.antigravity.fluidengine.ui.fluid

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.glass.field.GlassContentLensEffect
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.coroutines.coroutineContext

/** Il ritaglio del contenuto sul corpo della finestra in viaggio: un rettangolo arrotondato posato. */
internal class FluidPopoverTravelClipShape(private val round: RoundRect) : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
    Outline.Rounded(round)
}

/** La forma si raccoglie sul posto e si apre dal centro d'arrivo, sullo stesso percorso del menu. */
internal fun fluidModalSourceSlab(
  start: FluidForm.Slab,
  end: FluidForm.Slab,
  progress: Float,
  returning: Boolean = false,
): FluidForm.Slab {
  if (returning) return fluidMenuReturnSlab(start, end, progress)
  val slab = fluidMenuButtonTravel(start.frame, end.frame, end.cornerRadii.topLeft, end.frame.center, progress)
  val t = (progress / FluidMotion.MenuButtonGatherEnd).coerceIn(0f, 1f)
  val radius = slab.cornerRadii.topLeft
  val limit = slab.frame.minDimension / 2f
  return slab.copy(cornerRadii = FluidCornerRadii(
    lerp(start.cornerRadii.topLeft, radius, t).coerceIn(0f, limit),
    lerp(start.cornerRadii.topRight, radius, t).coerceIn(0f, limit),
    lerp(start.cornerRadii.bottomRight, radius, t).coerceIn(0f, limit),
    lerp(start.cornerRadii.bottomLeft, radius, t).coerceIn(0f, limit),
  ))
}

internal enum class FluidModalSourceMotion { Popover, Confirmation, Alert }

// WWDC25 323, Delete: la goccia resta sul comando fino al quarto intervallo video da 33 ms.
private const val FluidModalGatherMillis = 133
private const val FluidModalDialogOpenMillis = 400
// WWDC25 284 e F5A.2: il ritorno entra nella posa a vista attorno a 233 ms, prima della fine della traiettoria.
private const val FluidModalReturnMillis = 250
private val FluidModalLateFocusEasing = Easing {
  val cubic = it * it * it
  cubic * cubic
}

internal fun fluidModalGeometrySpec(
  motion: FluidModalSourceMotion,
  opening: Boolean,
  from: Float,
): AnimationSpec<Float> = when {
  opening && from == 0f && motion == FluidModalSourceMotion.Popover -> FluidMotion.PopoverTravelOpen
  opening && from == 0f && motion != FluidModalSourceMotion.Popover -> keyframes {
    durationMillis = FluidModalDialogOpenMillis
    FluidMotion.MenuButtonGatherEnd at 67
    FluidMotion.MenuButtonGatherEnd at FluidModalGatherMillis
    FluidMotion.MenuButtonFirstRise at 172
    FluidMotion.MenuButtonBloom at 223
    FluidMotion.MenuButtonBubble at 270
    FluidMotion.MenuButtonPanel at 335
  }
  opening -> FluidMotion.dropOpen()
  // Una chiusura a viaggio interrotto conserva valore e slancio correnti.
  from != 1f -> spring(FluidMotion.DropJourneyDamping, FluidMotion.MenuLiquidCloseStiffness)
  else -> fluidMenuReturnSpec(FluidModalReturnMillis)
}

internal fun fluidModalFocusSpec(
  motion: FluidModalSourceMotion,
  opening: Boolean,
  from: Float,
  fresh: Boolean,
): AnimationSpec<Float> {
  if (!opening) return fluidMenuReturnFocusSpec(from)
  if (motion == FluidModalSourceMotion.Popover) return tween(
    durationMillis = (FluidMotion.ContextMenuFocusMillis * (1f - from)).toInt().coerceAtLeast(1),
    easing = FluidMotion.ContextMenuFocusEasing,
  )
  val freshOpen = opening && fresh
  val late = freshOpen
  val delay = if (late) FluidModalGatherMillis else 0
  val endAt = if (late) FluidModalDialogOpenMillis else FluidMotion.MenuContentFocusMillis
  return tween(
    durationMillis = ((endAt - delay) * abs((if (opening) 1f else 0f) - from)).roundToInt(),
    delayMillis = delay,
    easing = if (late) FluidModalLateFocusEasing else LinearEasing,
  )
}

internal fun fluidModalInkExitSpec(from: Float): AnimationSpec<Float> =
  tween((FluidMotion.MenuContentExitMillis * from).roundToInt(), easing = LinearEasing)

/** Nei dialoghi il glifo cede il posto prima della sosta della goccia. */
private fun FluidModalSourceMotion.faceEnd(): Float =
  if (this != FluidModalSourceMotion.Popover) FluidMotion.MenuButtonGatherEnd else FluidMotion.MenuFaceTo

internal fun fluidModalFaceAlpha(motion: FluidModalSourceMotion, p: Float): Float =
  fluidMenuFaceAlpha(p * FluidMotion.MenuFaceTo / motion.faceEnd())

/** Il glifo rientra sul tasto quando tutti i lati della goccia sono arrivati alla sorgente. */
internal fun fluidModalReturnFaceAlpha(progress: Float, current: Rect, source: Rect): Float {
  if (current.isEmpty || source.isEmpty) return 0f
  val tolerance = source.minDimension * 0.10f
  val edgeGap = maxOf(
    abs(current.left - source.left), abs(current.top - source.top),
    abs(current.right - source.right), abs(current.bottom - source.bottom),
  )
  val arrival = (1f - edgeGap / tolerance).coerceIn(0f, 1f)
  return arrival * fluidMenuFaceAlpha(fluidMenuReturnFaceProgress(progress))
}

internal fun fluidModalContentAlpha(motion: FluidModalSourceMotion, p: Float, focus: Float, visibility: Float): Float {
  // Le finestre si toccano al punto in cui il glifo è già spento. Poi risponde la lente del menu.
  val handoff = ((p - motion.faceEnd()) / FluidMotion.MenuButtonGatherEnd).coerceIn(0f, 1f)
  return handoff * fluidMenuLensAlpha(focus) * visibility
}

internal fun fluidModalContentMap(
  physics: FluidPhysicsState,
  pane: Rect,
  progress: Float,
  focus: Float,
): FluidMenuContentMap {
  val clip = fluidModalTravelClip(physics, pane, progress, focus)
  val visible = clip?.let { Rect(it.left, it.top, it.right, it.bottom).translate(pane.topLeft) } ?: pane
  return fluidMenuLensMap(pane, visible, button = true, progress = progress, focus = focus)
}

/** La lente usa il contorno in movimento fino alla messa a fuoco. */
internal fun fluidModalTravelClip(physics: FluidPhysicsState, pane: Rect, progress: Float, focus: Float): RoundRect? =
  physics.travelClipIn(pane, if (focus < 1f) progress.coerceAtMost(0.999f) else progress)

@Composable
internal fun rememberFluidModalLens(active: Boolean): GlassContentLensEffect? =
  if (active && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) remember { GlassContentLensEffect() } else null

internal fun GraphicsLayerScope.fluidModalLensEffect(
  lens: GlassContentLensEffect?,
  physics: FluidPhysicsState,
  pane: Rect,
  map: FluidMenuContentMap,
  focus: Float,
): RenderEffect? {
  val blur = fluidMenuLensBlurDp(focus)
  val strength = fluidMenuLensStrength(focus)
  if (lens != null && (blur > 0f || strength > 0f)) {
    val padding = Offset(size.width, size.height) * ((1f - map.scale) / 2f)
    return lens.effect(physics.ensurePlan(), pane.topLeft + map.translation + padding, map.scale, density, blur, strength)
  }
  val radius = blur * density
  return if (radius >= 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    BlurEffect(radius, radius, TileMode.Decal)
  } else null
}

@Stable
internal class FluidSourceJourney(
  private val source: FluidTransitionSource,
  val initial: FluidTransitionSourceGeometry,
  val motion: FluidModalSourceMotion,
) {
  private var lease by mutableStateOf<FluidTransitionSource?>(null)
  val clock = Animatable(0f, 0.001f)
  val focus = Animatable(0f)
  private val contentVisibility = Animatable(1f)
  var target = 0f
    private set
  private var velocity = 0f
  private var returningOnContour by mutableStateOf(false)
  val controlledReturn: Boolean get() = returningOnContour
  private val faceAtSource: Boolean get() = target == 0f || returningOnContour
  private var latest by mutableStateOf(initial)
  var rootOrigin by mutableStateOf(Offset.Zero)
  val returned: Boolean get() = target == 0f && !clock.isRunning && clock.value <= 0.001f
  val bounds: Rect get() = latest.bounds.translate(-rootOrigin.x, -rootOrigin.y)

  fun retarget(visible: Boolean) {
    val next = if (visible) 1f else 0f
    if (target == next) return
    velocity = clock.velocity
    if (!visible) {
      if (!clock.isRunning && clock.value == 1f) returningOnContour = true
      source.resolve(includeCoveredPage = true)?.let { latest = it }
    } else if (clock.value == 0f) {
      returningOnContour = false
    }
    target = next
  }

  suspend fun run(reduced: Boolean) {
    if (motion == FluidModalSourceMotion.Popover && !reduced) {
      withContext(fluidSteadyFrames(coroutineContext)) { animate(reduced = false) }
    } else animate(reduced)
  }

  private suspend fun animate(reduced: Boolean) {
    val end = target
    if (reduced) {
      clock.snapTo(end)
      focus.snapTo(end)
      contentVisibility.snapTo(end)
      return
    }
    val opening = end == 1f
    val fresh = clock.value == 0f && focus.value == 0f
    coroutineScope {
      launch { focus.animateTo(end, fluidModalFocusSpec(motion, opening, focus.value, fresh)) }
      launch {
        val spec = if (opening) fluidModalInkExitSpec(1f - contentVisibility.value)
          else fluidMenuReturnInkSpec(contentVisibility.value)
        contentVisibility.animateTo(end, spec)
      }
      clock.animateTo(end, fluidModalGeometrySpec(motion, opening, clock.value), initialVelocity = velocity)
    }
  }

  val hasLease: Boolean get() = lease != null

  fun takeOver(acquired: FluidTransitionSource?) {
    lease = acquired
  }

  fun release() {
    lease?.releaseFromModal()
    lease = null
  }

  fun drive(physics: FluidPhysicsState, pane: Rect, radius: Float, width: Float, density: Density,
    direction: LayoutDirection, progress: () -> Float = { clock.value }) {
    val frame = bounds
    val start = FluidForm.Slab(frame, modalSourceCorners(latest.shape, frame.size, density, direction))
    val end = FluidForm.Slab(pane, FluidCornerRadii.all(radius))
    val inflation = with(density) {
      fluidMenuInflation(pane.width, pane.height, width, FluidMenuInflationEdgeMin.toPx(),
        FluidMenuInflationReferenceHeight.toPx(), FluidPopoverWindowPopGain, FluidPopoverPeakOvershoot)
    }
    physics.driveExternally(
      from = start,
      to = end,
      progress = progress,
      overshootInflationX = inflation.x,
      overshootInflationY = inflation.y,
      startFrame = null,
      travelSlab = { progress().takeIf { it < 1f }?.let { fluidModalSourceSlab(start, end, it, returningOnContour) } },
    )
  }

  fun contentAlpha(progress: Float): Float = fluidModalContentAlpha(motion, progress, focus.value, contentVisibility.value)

  fun bodyAlpha(progress: Float, physics: FluidPhysicsState): Float =
    if (lease?.hasSeparateModalBody == false && lease?.snapshot != null) 1f - sourceFaceAlpha(progress, physics) else 1f

  private fun sourceFaceAlpha(progress: Float, physics: FluidPhysicsState): Float =
    if (faceAtSource) fluidModalReturnFaceAlpha(progress, physics.contentBoundsNow(), bounds)
    else fluidModalFaceAlpha(motion, progress)

  @Composable
  fun SourceFace(physics: FluidPhysicsState, progress: () -> Float) {
    val face = rememberGraphicsLayer()
    Box(Modifier.fillMaxSize().drawWithContent {
      val snapshot = lease?.snapshot ?: return@drawWithContent
      val alpha = sourceFaceAlpha(progress(), physics)
      if (alpha <= 0f || snapshot.size.isEmpty()) return@drawWithContent
      val current = if (faceAtSource) bounds else physics.contentBoundsNow()
      if (current.isEmpty) return@drawWithContent
      val scale = minOf(current.width / snapshot.size.width, current.height / snapshot.size.height)
      val pivot = Offset(snapshot.size.width / 2f, snapshot.size.height / 2f)
      // Il layer appartiene alla sorgente. Può essere già stato rilasciato se la riga è sparita.
      runCatching {
        face.record {
          translate(current.center.x - pivot.x, current.center.y - pivot.y) {
            scale(scale, pivot) { drawLayer(snapshot.layer) }
          }
        }
        face.alpha = alpha
        drawLayer(face)
      }
    })
  }
}

private fun modalSourceCorners(shape: Shape, size: Size, density: Density, direction: LayoutDirection): FluidCornerRadii {
  if (shape is CornerBasedShape) {
    val limit = size.minDimension / 2f
    val tl = shape.topStart.toPx(size, density).coerceIn(0f, limit)
    val tr = shape.topEnd.toPx(size, density).coerceIn(0f, limit)
    val br = shape.bottomEnd.toPx(size, density).coerceIn(0f, limit)
    val bl = shape.bottomStart.toPx(size, density).coerceIn(0f, limit)
    return if (direction == LayoutDirection.Ltr) FluidCornerRadii(tl, tr, br, bl) else FluidCornerRadii(tr, tl, bl, br)
  }
  val round = (shape.createOutline(size, direction, density) as? Outline.Rounded)?.roundRect
    ?: return FluidCornerRadii.all(0f)
  return FluidCornerRadii(round.topLeftCornerRadius.x, round.topRightCornerRadius.x,
    round.bottomRightCornerRadius.x, round.bottomLeftCornerRadius.x)
}

/** Quanta parte dell'oltrepasso gonfia la sagoma. Taratura F5B.1 su WWDC25 284 e 323, fiducia media. */
internal const val FluidPopoverWindowPopGain = 0.6f

/** Picco atteso di (progress − 1) per il tetto laterale. Taratura F5B.1 su WWDC25 284 e 323, fiducia media. */
internal const val FluidPopoverPeakOvershoot = 0.15f
