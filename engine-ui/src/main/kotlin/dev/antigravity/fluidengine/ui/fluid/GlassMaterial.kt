package dev.antigravity.fluidengine.ui.fluid

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Backdrop blur — the frosted material that lets bars float over live content instead of cutting it
 * off with an opaque strip.
 *
 * Compose has no `backdrop-filter`, so the effect is assembled by hand:
 *
 *  1. [glassBackdropSource] records the scrolling content into an offscreen [GraphicsLayer] instead
 *     of drawing it straight to the canvas, then draws that layer — so the content still appears,
 *     unchanged.
 *  2. The same pass re-records it into two more layers carrying [BlurEffect]s of different strength.
 *     Those are blurred snapshots of everything behind the bars.
 *  3. [glassSurface] draws the slice of the snapshot that sits underneath it, translated so the
 *     sampled pixels line up with what is really behind the bar, and tints the result.
 *
 * Two blurred copies rather than one, because material strength must travel without fading a blurry
 * text contour directly over a sharp one. The soft copy first replaces the sampled pixels, then the
 * heavy copy takes over as the bar materialises. That changes radius instead of manufacturing the
 * double contour that reads as noise while a list is moving.
 *
 * The snapshot trails the content by one frame: the source writes [GlassBackdropState.frameTick]
 * during its draw and the glass nodes read it during theirs. At 120 Hz that is 8 ms of lag on a
 * heavily blurred image — imperceptible, and the trade-off every backdrop implementation on Android
 * makes.
 *
 * `RenderEffect` needs API 31. Below that [GlassBackdropState.blurSupported] is false and the glass
 * degrades to a near-opaque tint, which still reads as a deliberate material rather than as a bug.
 */
@Stable
class GlassBackdropState internal constructor(
  internal val sourceLayer: GraphicsLayer,
  internal val heavyLayer: GraphicsLayer,
  internal val softLayer: GraphicsLayer,
  internal val blurRadius: Dp,
) {
  /** Bumped on every source draw so glass surfaces know the snapshot changed. */
  internal val frameTick = mutableIntStateOf(0)

  /**
   * Deliberately plain state. Reading [frameTick] inside the source draw would subscribe the source
   * to its own invalidation signal and keep all three full-screen layers redrawing forever.
   */
  private var publishedFrameTick = 0
  private var snapshotPublished = false

  internal var sourceOrigin by mutableStateOf(Offset.Zero)

  /** True once the source has recorded a frame; before that there is nothing to sample. */
  internal var hasSnapshot by mutableStateOf(false)

  /**
   * Frames of recording still owed to a surface that asked to sample this backdrop.
   *
   * Recording the body into three full-screen layers, two of them blurred, is the most expensive
   * thing a screen does per frame, and it was being paid unconditionally — including by the screen
   * a route transition is *leaving*, whose backdrop nothing is sampling any more. Recording only
   * while something actually reads the snapshot is what gives the transition back its frame budget.
   */
  private var recordingCredit = 0

  /**
   * The observable half of the same fact, and the reason the gate works at all.
   *
   * A pane of glass draws *after* the body it samples, so a request can only ever be honoured by a
   * later frame — and on a screen at rest there is no later frame unless something invalidates the
   * source. The source reads this during its draw, which subscribes it, so switching the glass on
   * schedules exactly the redraw needed to produce the first snapshot.
   */
  internal var isSampled by mutableStateOf(false)
    private set

  /** Called by a pane of glass that is about to draw, before it samples anything. */
  internal fun requestSample() {
    recordingCredit = SampleCreditFrames
    if (!isSampled) isSampled = true
  }

  /**
   * Whether this frame has to be recorded. Credit outlives a single frame so that a surface which
   * samples *after* the source has drawn still finds a snapshot no more than one frame stale.
   */
  internal fun consumeRecordingCredit(): Boolean {
    if (recordingCredit <= 0) {
      if (isSampled) isSampled = false
      return false
    }
    recordingCredit--
    return true
  }

  internal val blurSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

  /** Publishes one real source redraw without ever reading observable state from that draw pass. */
  internal fun publishSourceDraw() {
    if (!snapshotPublished) {
      snapshotPublished = true
      hasSnapshot = true
    }
    publishedFrameTick = nextGlassFrameTick(publishedFrameTick)
    frameTick.intValue = publishedFrameTick
  }
}

internal fun nextGlassFrameTick(current: Int): Int = if (current == Int.MAX_VALUE) 0 else current + 1

/**
 * How many frames one sample request keeps the source recording.
 *
 * Two, not one: a pane of glass draws after the body it samples, so a request made this frame can
 * only be honoured by the next one, and a single frame of credit would leave the material flickering
 * between a fresh and a frozen snapshot every time it is on.
 */
private const val SampleCreditFrames = 2

/** Restrained blur; 8 dp is still about 22 physical pixels on the 120 Hz QA phone. */
val DefaultGlassBlurRadius: Dp = 8.dp

/** The light end of the ramp. It is intentionally subtle when material first crosses the dead zone. */
private val SoftBlurFraction = 0.20f

@Composable
fun rememberGlassBackdrop(blurRadius: Dp = DefaultGlassBlurRadius): GlassBackdropState {
  val sourceLayer = rememberGraphicsLayer()
  val heavyLayer = rememberGraphicsLayer()
  val softLayer = rememberGraphicsLayer()
  return remember(sourceLayer, heavyLayer, softLayer, blurRadius) {
    GlassBackdropState(sourceLayer, heavyLayer, softLayer, blurRadius)
  }
}

/**
 * Marks the content that glass surfaces sample.
 *
 * Apply this to the scrolling body of a screen and never to the whole screen, otherwise the bars end
 * up sampling themselves. The body must also paint its own opaque background *inside* the recorded
 * region: a snapshot of text on transparency blurs into a faint smear that the sharp original still
 * shows through.
 */
fun Modifier.glassBackdropSource(state: GlassBackdropState): Modifier = this
  .onGloballyPositioned { state.sourceOrigin = it.positionInRoot() }
  .drawWithCache {
    if (state.blurSupported) {
      val heavy = state.blurRadius.toPx()
      val soft = heavy * SoftBlurFraction
      state.heavyLayer.renderEffect = BlurEffect(heavy, heavy, TileMode.Clamp)
      state.softLayer.renderEffect = BlurEffect(soft, soft, TileMode.Clamp)
      // Keep only a whisper of the saturation lift used by system materials. A stronger filter made
      // fast-moving text behind the top bar turn into dark, noisy-looking colour bands.
      val saturate = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.04f) })
      state.heavyLayer.colorFilter = saturate
      state.softLayer.colorFilter = saturate
    }
    onDrawWithContent {
      // Subscribes this draw node to the demand flag, so a pane of glass switching on invalidates
      // the source even when the content itself is perfectly still.
      state.isSampled
      if (!state.consumeRecordingCredit()) {
        // Nothing is sampling this backdrop, so there is nothing to snapshot for. Draw the content
        // straight to the canvas and skip three full-screen layer recordings and two blurs.
        drawContent()
        return@onDrawWithContent
      }
      state.sourceLayer.record { this@onDrawWithContent.drawContent() }
      drawLayer(state.sourceLayer)
      if (state.blurSupported) {
        state.heavyLayer.record { drawLayer(state.sourceLayer) }
        state.softLayer.record { drawLayer(state.sourceLayer) }
      }
      state.publishSourceDraw()
    }
  }

/** Colour treatment of a glass surface. */
@Immutable
data class GlassTint(
  /** Painted over the blurred backdrop when blur is available. */
  val overlay: Color,
  /** Used instead of the backdrop on devices without `RenderEffect`. */
  val fallback: Color,
  /** Hairline drawn on the edge that meets the content. */
  val hairline: Color,
)

object GlassDefaults {

  /**
   * Bars.
   *
   * The tint raises contrast for controls and suppresses the one-frame trail inherent in a manually
   * sampled Android backdrop. Enough of the softened colour remains to read as material, while text
   * moving behind the bar no longer becomes a high-contrast grey smear.
   */
  @Composable
  fun barTint(): GlassTint {
    val dark = isDarkSurface()
    val surface = MaterialTheme.colorScheme.surface
    return GlassTint(
      overlay = if (dark) surface.copy(alpha = 0.58f) else surface.copy(alpha = 0.60f),
      fallback = if (dark) surface.copy(alpha = 0.95f) else surface.copy(alpha = 0.96f),
      hairline = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.16f else 0.10f),
    )
  }

  /** Floating controls: heavier, because they sit over arbitrary content and carry their own edge. */
  @Composable
  fun floatingTint(): GlassTint {
    val dark = isDarkSurface()
    val surface = MaterialTheme.colorScheme.surface
    return GlassTint(
      overlay = if (dark) surface.copy(alpha = 0.52f) else surface.copy(alpha = 0.56f),
      fallback = if (dark) surface.copy(alpha = 0.96f) else surface.copy(alpha = 0.97f),
      hairline = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.18f else 0.08f),
    )
  }

  /**
   * Whether the app is painting a dark surface.
   *
   * Read from the palette rather than from `isSystemInDarkTheme()`: the app carries its own theme
   * setting, so with the system light and the app forced to AMOLED the bars would otherwise mix a
   * white tint into a black backdrop.
   */
  @Composable
  private fun isDarkSurface(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f
}

/** Which edge, if any, carries the hairline that separates glass from content. */
enum class GlassEdge { None, Bottom, Top }

/** How the material ends. */
enum class GlassFalloff {
  /** The same everywhere, with a defined edge. For a floating control with its own silhouette. */
  Uniform,

  /**
   * Strongest at the top, ramping to nothing at the bottom.
   *
   * For a bar pinned under the status bar. A uniform bar has to end somewhere, and wherever it ends
   * there is a line across the screen with sharp content on one side and blurred content on the
   * other. That line is the single most artificial-looking part of a translucent bar; ramping the
   * blur out removes it entirely.
   */
  FadeDown,
}

/** Tracks where a single pane of glass sits, so it can sample the matching slice of the snapshot. */
@Stable
class GlassSurfaceAnchor internal constructor() {
  internal var originInRoot by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberGlassSurfaceAnchor(): GlassSurfaceAnchor = remember { GlassSurfaceAnchor() }

/**
 * Turns a composable into a pane of glass over [state]'s content.
 *
 * @param intensity 0 = glass absent, 1 = full material. Animate this instead of the bar's own alpha
 *   so that the controls sitting on the glass stay fully opaque and legible throughout.
 */
@Composable
fun Modifier.glassSurface(
  state: GlassBackdropState,
  tint: GlassTint,
  shape: Shape = RectangleShape,
  edge: GlassEdge = GlassEdge.None,
  falloff: GlassFalloff = GlassFalloff.Uniform,
  intensity: () -> Float = { 1f },
): Modifier {
  val anchor = rememberGlassSurfaceAnchor()
  return this
    .graphicsLayer {
      this.shape = shape
      this.clip = true
    }
    .onGloballyPositioned { anchor.originInRoot = it.positionInRoot() }
    .drawWithCache {
      val hairlinePx = maxOf(1f, 0.5f.dp.toPx())
      onDrawBehind {
        val amount = intensity().coerceIn(0f, 1f)
        if (amount <= 0.001f) return@onDrawBehind
        // Claimed before sampling, so the source keeps recording for as long as this pane is on.
        state.requestSample()

        if (state.blurSupported && state.hasSnapshot) {
          // Reading the tick keeps this node invalidated in step with the source's redraws.
          state.frameTick.intValue
          val dx = state.sourceOrigin.x - anchor.originInRoot.x
          val dy = state.sourceOrigin.y - anchor.originInRoot.y
          when (falloff) {
            GlassFalloff.Uniform -> {
              // Always replace the sharp pixels with the lightly softened snapshot once material is
              // present, then travel from that soft radius to the full radius. Fading a blurred copy
              // directly over sharp text creates two readable contours — the noisy trail this glass
              // implementation is specifically meant to avoid during fast scrolling.
              translate(dx, dy) { drawLayer(state.softLayer) }
              state.heavyLayer.alpha = amount
              translate(dx, dy) { drawLayer(state.heavyLayer) }
              state.heavyLayer.alpha = 1f
              drawRect(tint.overlay.copy(alpha = tint.overlay.alpha * amount))
            }
            GlassFalloff.FadeDown -> drawFadingGlass(state, dx, dy, tint, amount)
          }
        } else {
          val fallbackBrush = when (falloff) {
            GlassFalloff.Uniform -> null
            GlassFalloff.FadeDown -> verticalFade(tint.fallback.copy(alpha = tint.fallback.alpha * amount))
          }
          if (fallbackBrush == null) {
            drawRect(tint.fallback.copy(alpha = tint.fallback.alpha * amount))
          } else {
            drawRect(brush = fallbackBrush)
          }
        }

        val hairline = tint.hairline.copy(alpha = tint.hairline.alpha * amount)
        when (edge) {
          GlassEdge.None -> Unit
          GlassEdge.Bottom -> drawRect(
            color = hairline,
            topLeft = Offset(0f, size.height - hairlinePx),
            size = Size(size.width, hairlinePx),
          )
          GlassEdge.Top -> drawRect(
            color = hairline,
            topLeft = Offset.Zero,
            size = Size(size.width, hairlinePx),
          )
        }
      }
    }
}

/**
 * Draws the ramp: heavy blur at the top, light blur in the middle, clear content at the bottom.
 *
 * Each band is drawn into its own offscreen buffer and then cut back with a vertical gradient in
 * `DstIn`, which is the only way to apply a soft mask to something already rasterised. The bands
 * overlap, so the heavy layer is still at full strength where the light one begins and the eye never
 * finds the seam between them.
 */
private fun DrawScope.drawFadingGlass(
  state: GlassBackdropState,
  dx: Float,
  dy: Float,
  tint: GlassTint,
  amount: Float,
) {
  // Never fade a blurred glyph over its sharp copy: that creates two readable contours. The soft
  // snapshot stays opaque where present, while its *coverage* grows down from the status bar. This
  // is a genuinely progressive reveal instead of the previous binary soft layer that appeared at
  // full height on the first non-zero scroll frame.
  val coverage = calculateProgressiveGlassCoverage(amount)
  if (coverage <= 0.001f) return
  maskedLayer(
    layer = state.softLayer,
    dx = dx,
    dy = dy,
    amount = 1f,
    stops = progressiveMaskStops(coverage, holdFraction = 0.72f),
  )

  // The heavier radius joins only after the soft material has established an opaque replacement.
  // It may therefore change alpha without exposing sharp text underneath it.
  val heavyAmount = smoothStep(((amount - 0.42f) / 0.58f).coerceIn(0f, 1f))
  if (heavyAmount > 0.001f) {
    maskedLayer(
      layer = state.heavyLayer,
      dx = dx,
      dy = dy,
      amount = heavyAmount,
      stops = progressiveMaskStops(coverage * 0.86f, holdFraction = 0.60f),
    )
  }
  drawRect(
    brush = progressiveVerticalFade(
      color = tint.overlay.copy(alpha = tint.overlay.alpha * amount),
      coverage = coverage,
    ),
  )
}

internal fun calculateProgressiveGlassCoverage(amount: Float): Float = smoothStep(amount)

private fun progressiveMaskStops(
  coverage: Float,
  holdFraction: Float,
): Array<Pair<Float, Color>> {
  val end = coverage.coerceIn(0.001f, 1f)
  val hold = end * holdFraction.coerceIn(0f, 1f)
  return arrayOf(
    0f to Color.White,
    hold to Color.White,
    end to Color.Transparent,
    1f to Color.Transparent,
  )
}

private fun DrawScope.progressiveVerticalFade(color: Color, coverage: Float): Brush {
  val end = coverage.coerceIn(0.001f, 1f)
  return Brush.verticalGradient(
    colorStops = arrayOf(
      0f to color,
      (end * 0.55f) to color,
      end to color.copy(alpha = 0f),
      1f to color.copy(alpha = 0f),
    ),
    startY = 0f,
    endY = size.height,
  )
}

private fun DrawScope.maskedLayer(
  layer: GraphicsLayer,
  dx: Float,
  dy: Float,
  amount: Float,
  stops: Array<Pair<Float, Color>>,
) {
  val bounds = Rect(0f, 0f, size.width, size.height)
  drawContext.canvas.saveLayer(bounds, Paint())
  layer.alpha = amount
  translate(dx, dy) { drawLayer(layer) }
  layer.alpha = 1f
  drawRect(
    brush = Brush.verticalGradient(colorStops = stops, startY = 0f, endY = size.height),
    blendMode = BlendMode.DstIn,
  )
  drawContext.canvas.restore()
}

private fun DrawScope.verticalFade(color: Color): Brush = Brush.verticalGradient(
  colorStops = arrayOf(
    0f to color,
    0.55f to color,
    1f to color.copy(alpha = 0f),
  ),
  startY = 0f,
  endY = size.height,
)
