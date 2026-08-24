package dev.antigravity.fluidengine.ui.fluid

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Outline
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.math.roundToInt

/**
 * Backdrop blur — the frosted material that lets bars float over live content instead of cutting it
 * off with an opaque strip.
 *
 * Compose has no `backdrop-filter`, so the effect is assembled by hand:
 *
 *  1. [glassBackdropSource] records the scrolling content into an offscreen [GraphicsLayer] instead
 *     of drawing it straight to the canvas, then draws that layer — so the content still appears,
 *     unchanged.
 *  2. For each active pane, the same pass re-records only the pane-sized slice into two layers
 *     carrying [BlurEffect]s of different strength. Those are the blurred pixels behind that pane.
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
  internal val blurRadius: Dp,
) {
  /** Bumped on every source draw so glass surfaces know the snapshot changed. */
  internal val frameTick = mutableIntStateOf(0)

  /**
   * Deliberately plain state. Reading [frameTick] inside the source draw would subscribe the source
   * to its own invalidation signal and keep all active backdrop layers redrawing forever.
   */
  private var publishedFrameTick = 0

  internal var sourceOrigin by mutableStateOf(Offset.Zero)

  private val surfaces = LinkedHashSet<GlassSurfaceAnchor>()
  private val requestedSurfaces = ArrayList<GlassSurfaceAnchor>(2)

  /**
   * Observable wake-up signal for the source.
   *
   * A boolean cannot distinguish a newly visible pane from one that is already being sampled. The
   * generation advances only when an individual pane has no recording credit left, so every new
   * pane gets a first snapshot while an already active pane cannot invalidate the source forever.
   */
  internal val requestGeneration = mutableIntStateOf(0)
  private var publishedRequestGeneration = 0

  internal fun registerSurface(surface: GlassSurfaceAnchor) {
    surfaces += surface
  }

  internal fun unregisterSurface(surface: GlassSurfaceAnchor) {
    surfaces -= surface
  }

  /** Called by a pane of glass that is about to draw, before it samples anything. */
  internal fun requestSample(surface: GlassSurfaceAnchor) {
    if (surface.requestRecording()) {
      publishedRequestGeneration = nextGlassFrameTick(publishedRequestGeneration)
      requestGeneration.intValue = publishedRequestGeneration
    }
  }

  /**
   * Whether this frame has to be recorded. Credit outlives a single frame so that a surface which
   * samples *after* the source has drawn still finds a snapshot no more than one frame stale.
   */
  internal fun consumeRequestedSurfaces(): List<GlassSurfaceAnchor> {
    requestedSurfaces.clear()
    surfaces.forEach { surface ->
      if (surface.consumeRecordingCredit()) requestedSurfaces += surface
    }
    return requestedSurfaces
  }

  internal val blurSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

  /** Publishes one real source redraw without ever reading observable state from that draw pass. */
  internal fun publishSourceDraw(surfaces: List<GlassSurfaceAnchor>) {
    surfaces.forEach(GlassSurfaceAnchor::publishSnapshot)
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

/** Pure credit gate behind one glass pane's recording demand. */
internal class GlassRecordingDemand(
  private val creditFrames: Int = SampleCreditFrames,
) {
  init {
    require(creditFrames > 0)
  }

  private var recordingCredit = 0

  /** Returns true only when the source must be explicitly woken for this pane. */
  fun request(): Boolean {
    val needsSourceWakeUp = recordingCredit == 0
    recordingCredit = creditFrames
    return needsSourceWakeUp
  }

  fun consume(surfaceReady: Boolean): Boolean {
    if (recordingCredit <= 0) return false
    recordingCredit--
    return surfaceReady
  }
}

/** Restrained blur; 8 dp is still about 22 physical pixels on the 120 Hz QA phone. */
val DefaultGlassBlurRadius: Dp = 8.dp

/** The light end of the ramp. It is intentionally subtle when material first crosses the dead zone. */
private val SoftBlurFraction = 0.20f

/** Four Gaussian radii keep the cropped-edge contribution below one 8-bit channel step. */
private const val BlurCropPaddingMultiplier = 4f

/** At this point the remaining alpha difference is below one 8-bit channel step. */
private const val SteadyGlassThreshold = 0.999f

@Composable
fun rememberGlassBackdrop(blurRadius: Dp = DefaultGlassBlurRadius): GlassBackdropState {
  val sourceLayer = rememberGraphicsLayer()
  return remember(sourceLayer, blurRadius) {
    GlassBackdropState(sourceLayer, blurRadius)
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
    onDrawWithContent {
      // Subscribes this draw node to the demand flag, so a pane of glass switching on invalidates
      // the source even when the content itself is perfectly still.
      state.requestGeneration.intValue
      val requestedSurfaces = state.consumeRequestedSurfaces()
      if (requestedSurfaces.isEmpty()) {
        // Nothing is sampling this backdrop, so there is nothing to snapshot for. Draw the content
        // straight to the canvas and skip the source recording and every cropped blur pass.
        drawContent()
        return@onDrawWithContent
      }
      state.sourceLayer.record { this@onDrawWithContent.drawContent() }
      drawLayer(state.sourceLayer)
      if (state.blurSupported) {
        requestedSurfaces.forEach { surface ->
          recordCroppedGlassLayers(
            sourceLayer = state.sourceLayer,
            sourceOrigin = state.sourceOrigin,
            surface = surface,
          )
        }
      }
      state.publishSourceDraw(requestedSurfaces)
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

/** The optical job a pane performs; colour remains the separate concern of [GlassTint]. */
enum class GlassRole {
  /** Edge-to-edge chrome whose material should stay quiet behind navigation and status controls. */
  Bar,

  /** A detached navigation capsule or rail with a clearly visible silhouette. */
  Floating,

  /** A control that yields under a finger and therefore carries the strongest lens response. */
  Interactive,

  /** A sheet or alert surface: deeper than a bar, calmer than a control. */
  Modal,
}

/**
 * Optical treatment layered over the existing blur and tint.
 *
 * Values describe the material at full [glassSurface] optical depth. The modifier clamps malformed
 * custom values, and press feedback is a draw-time multiplier: it never allocates another blurred
 * layer or re-records a different backdrop.
 */
@Immutable
data class GlassOptics(
  val refractionStrength: Dp,
  val rimWidth: Dp,
  val outerRimAlpha: Float,
  val innerRimAlpha: Float,
  val innerShadowAlpha: Float,
  val specularAlpha: Float,
  val magnification: Float,
  val pressedDepthBoost: Float,
  /** Unit-like vector pointing toward the light; malformed vectors fall back to top-left. */
  val lightDirection: Offset = Offset(-0.64f, -0.77f),
)

/** Rendering tier selected without touching classes that do not exist on an older Android release. */
internal enum class GlassRenderCapability {
  /** Tint and static optical strokes only. */
  StaticRim,

  /** Real backdrop blur plus a magnified raw sample in the rim. */
  MagnifiedRim,

  /** Android 13 AGSL displacement over the raw rim sample. */
  RuntimeRefraction,
}

internal fun glassRenderCapability(
  apiLevel: Int,
  hardwareAccelerated: Boolean,
): GlassRenderCapability = when {
  !hardwareAccelerated || apiLevel < Build.VERSION_CODES.S -> GlassRenderCapability.StaticRim
  apiLevel < Build.VERSION_CODES.TIRAMISU -> GlassRenderCapability.MagnifiedRim
  else -> GlassRenderCapability.RuntimeRefraction
}

/**
 * Returns the exact circular radius understood by the rounded-box AGSL, or null for shapes whose
 * outline the shader cannot faithfully describe.
 */
internal fun runtimeGlassCornerRadiusOrNull(outline: Outline): Float? =
  (outline as? Outline.Rounded)?.roundRect?.let(::runtimeGlassCornerRadiusForRoundRectOrNull)

internal fun runtimeGlassCornerRadiusForRoundRectOrNull(roundRect: RoundRect): Float? {
  val corners = arrayOf(
    roundRect.topLeftCornerRadius,
    roundRect.topRightCornerRadius,
    roundRect.bottomRightCornerRadius,
    roundRect.bottomLeftCornerRadius,
  )
  val radius = corners.first().x
  if (!radius.isFinite() || radius < 0f) return null
  return radius.takeIf {
    corners.all { corner ->
      corner.x.isFinite() && corner.y.isFinite() &&
        abs(corner.x - radius) <= GlassRadiusTolerancePx &&
        abs(corner.y - radius) <= GlassRadiusTolerancePx
    }
  }
}

internal fun shouldCreateGlassRuntimeRefraction(
  requestedCapability: GlassRenderCapability,
  perimeterOptics: Boolean,
  hasRefraction: Boolean,
  runtimeCornerRadius: Float?,
): Boolean = requestedCapability == GlassRenderCapability.RuntimeRefraction &&
  perimeterOptics &&
  hasRefraction &&
  runtimeCornerRadius != null

internal fun resolveGlassRenderCapability(
  requestedCapability: GlassRenderCapability,
  runtimeEligible: Boolean,
  runtimeAvailable: Boolean,
): GlassRenderCapability = if (
  requestedCapability == GlassRenderCapability.RuntimeRefraction &&
  (!runtimeEligible || !runtimeAvailable)
) {
  GlassRenderCapability.MagnifiedRim
} else {
  requestedCapability
}

private const val GlassRadiusTolerancePx = 0.01f

internal fun clampGlassUnit(value: Float): Float =
  if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

private fun finiteNonNegative(value: Float): Float =
  if (value.isFinite()) value.coerceAtLeast(0f) else 0f

internal fun GlassOptics.sanitized(): GlassOptics {
  val requestedDirectionLength = sqrt(
    lightDirection.x * lightDirection.x + lightDirection.y * lightDirection.y,
  )
  val directionSource = if (
    requestedDirectionLength.isFinite() && requestedDirectionLength > 0.001f
  ) {
    lightDirection
  } else {
    Offset(-0.64f, -0.77f)
  }
  val directionLength = sqrt(
    directionSource.x * directionSource.x + directionSource.y * directionSource.y,
  )
  val direction = Offset(
    directionSource.x / directionLength,
    directionSource.y / directionLength,
  )
  return copy(
    refractionStrength = finiteNonNegative(refractionStrength.value).dp,
    rimWidth = finiteNonNegative(rimWidth.value).dp,
    outerRimAlpha = clampGlassUnit(outerRimAlpha),
    innerRimAlpha = clampGlassUnit(innerRimAlpha),
    innerShadowAlpha = clampGlassUnit(innerShadowAlpha),
    specularAlpha = clampGlassUnit(specularAlpha),
    magnification = if (magnification.isFinite()) magnification.coerceIn(0f, 0.08f) else 0f,
    pressedDepthBoost = if (pressedDepthBoost.isFinite()) {
      pressedDepthBoost.coerceIn(0f, 1f)
    } else {
      0f
    },
    lightDirection = direction,
  )
}

internal fun calculateGlassSamplePadding(
  blurRadiusPx: Float,
  refractionStrengthPx: Float,
  rimWidthPx: Float,
  pressedDepthBoost: Float,
): Float {
  val blurReach = finiteNonNegative(blurRadiusPx) * BlurCropPaddingMultiplier
  val refractionReach = finiteNonNegative(refractionStrengthPx) *
    (1f + finiteNonNegative(pressedDepthBoost).coerceAtMost(1f)) +
    finiteNonNegative(rimWidthPx) * 2f
  return maxOf(blurReach, refractionReach)
}

object GlassDefaults {

  private val BarOptics = GlassOptics(
    refractionStrength = 1.25.dp,
    rimWidth = 0.75.dp,
    outerRimAlpha = 0.20f,
    innerRimAlpha = 0.10f,
    innerShadowAlpha = 0.08f,
    specularAlpha = 0.12f,
    magnification = 0.004f,
    pressedDepthBoost = 0.10f,
  )

  private val FloatingOptics = GlassOptics(
    refractionStrength = 4.4.dp,
    rimWidth = 1.65.dp,
    outerRimAlpha = 0.74f,
    innerRimAlpha = 0.34f,
    innerShadowAlpha = 0.32f,
    specularAlpha = 0.50f,
    magnification = 0.028f,
    pressedDepthBoost = 0.26f,
  )

  private val InteractiveOptics = GlassOptics(
    refractionStrength = 5.2.dp,
    rimWidth = 1.75.dp,
    outerRimAlpha = 0.80f,
    innerRimAlpha = 0.40f,
    innerShadowAlpha = 0.36f,
    specularAlpha = 0.58f,
    magnification = 0.036f,
    pressedDepthBoost = 0.38f,
  )

  private val ModalOptics = GlassOptics(
    refractionStrength = 3.4.dp,
    rimWidth = 1.45.dp,
    outerRimAlpha = 0.60f,
    innerRimAlpha = 0.28f,
    innerShadowAlpha = 0.30f,
    specularAlpha = 0.40f,
    magnification = 0.020f,
    pressedDepthBoost = 0.15f,
  )

  /** Stable, allocation-free presets. Custom callers can use `copy` and are sanitized at use. */
  fun optics(role: GlassRole): GlassOptics = when (role) {
    GlassRole.Bar -> BarOptics
    GlassRole.Floating -> FloatingOptics
    GlassRole.Interactive -> InteractiveOptics
    GlassRole.Modal -> ModalOptics
  }

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
      // Navigation floats over arbitrary content. Keep the interior contrast-safe and let the raw,
      // displaced rim carry the refraction; lowering this base makes fixed icon colours disappear
      // whenever the pixels underneath have the opposite luminance.
      overlay = if (dark) surface.copy(alpha = 0.78f) else surface.copy(alpha = 0.86f),
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
class GlassSurfaceAnchor internal constructor(
  internal val heavyLayer: GraphicsLayer,
  internal val softLayer: GraphicsLayer,
  /** One unblurred crop, used either by AGSL or by the API 31 magnified-rim fallback. */
  internal val opticalLayer: GraphicsLayer,
) {
  internal var originInRoot by mutableStateOf(Offset.Zero)
  internal var surfaceSize by mutableStateOf(IntSize.Zero)
  internal var hasSnapshot by mutableStateOf(false)

  internal var sampleCrop: GlassSampleCrop? = null
  internal var blurPaddingPx: Float = 0f
  internal var capability: GlassRenderCapability = GlassRenderCapability.StaticRim
  internal var requiresOpticalSample: Boolean = false
  private val recordingDemand = GlassRecordingDemand()

  internal fun requestRecording(): Boolean = recordingDemand.request()

  internal fun consumeRecordingCredit(): Boolean = recordingDemand.consume(
    surfaceReady = surfaceSize.width > 0 && surfaceSize.height > 0,
  )

  internal fun publishSnapshot() {
    if (sampleCrop != null && !hasSnapshot) hasSnapshot = true
  }
}

@Composable
fun rememberGlassSurfaceAnchor(): GlassSurfaceAnchor {
  val heavyLayer = rememberGraphicsLayer()
  val softLayer = rememberGraphicsLayer()
  val opticalLayer = rememberGraphicsLayer()
  return remember(heavyLayer, softLayer, opticalLayer) {
    GlassSurfaceAnchor(heavyLayer, softLayer, opticalLayer)
  }
}

@Immutable
internal data class GlassSampleCrop(
  val left: Int,
  val top: Int,
  val width: Int,
  val height: Int,
  val offsetInSurface: Offset,
)

/**
 * Restricts a pane's blur input to the pixels its kernel can actually reach.
 *
 * The old implementation blurred the full screen separately for the top bar and the floating tab
 * bar. At 1080 x 2340 that made two small panes pay for millions of invisible pixels every frame.
 * Four radii of padding make the discarded Gaussian tail smaller than one 8-bit channel step, so
 * the cropped input is visually equivalent while avoiding millions of invisible blurred pixels.
 */
internal fun calculateGlassSampleCrop(
  sourceSize: IntSize,
  sourceOrigin: Offset,
  surfaceOrigin: Offset,
  surfaceSize: IntSize,
  blurPaddingPx: Float,
): GlassSampleCrop? {
  if (sourceSize.width <= 0 || sourceSize.height <= 0) return null
  if (surfaceSize.width <= 0 || surfaceSize.height <= 0) return null

  val surfaceLeft = surfaceOrigin.x - sourceOrigin.x
  val surfaceTop = surfaceOrigin.y - sourceOrigin.y
  val padding = blurPaddingPx.coerceAtLeast(0f)
  val left = floor(surfaceLeft - padding).toInt().coerceIn(0, sourceSize.width)
  val top = floor(surfaceTop - padding).toInt().coerceIn(0, sourceSize.height)
  val right = ceil(surfaceLeft + surfaceSize.width + padding).toInt()
    .coerceIn(0, sourceSize.width)
  val bottom = ceil(surfaceTop + surfaceSize.height + padding).toInt()
    .coerceIn(0, sourceSize.height)
  if (right <= left || bottom <= top) return null

  return GlassSampleCrop(
    left = left,
    top = top,
    width = right - left,
    height = bottom - top,
    offsetInSurface = Offset(
      x = left - surfaceLeft,
      y = top - surfaceTop,
    ),
  )
}

private fun DrawScope.recordCroppedGlassLayers(
  sourceLayer: GraphicsLayer,
  sourceOrigin: Offset,
  surface: GlassSurfaceAnchor,
) {
  val crop = calculateGlassSampleCrop(
    sourceSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
    sourceOrigin = sourceOrigin,
    surfaceOrigin = surface.originInRoot,
    surfaceSize = surface.surfaceSize,
    blurPaddingPx = surface.blurPaddingPx,
  ) ?: run {
    surface.sampleCrop = null
    return
  }
  val cropSize = IntSize(crop.width, crop.height)
  surface.softLayer.record(size = cropSize) {
    translate(-crop.left.toFloat(), -crop.top.toFloat()) { drawLayer(sourceLayer) }
  }
  surface.heavyLayer.record(size = cropSize) {
    translate(-crop.left.toFloat(), -crop.top.toFloat()) { drawLayer(sourceLayer) }
  }
  if (surface.requiresOpticalSample) {
    surface.opticalLayer.record(size = cropSize) {
      translate(-crop.left.toFloat(), -crop.top.toFloat()) { drawLayer(sourceLayer) }
    }
  }
  surface.sampleCrop = crop
}

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
  role: GlassRole = if (falloff == GlassFalloff.FadeDown) GlassRole.Bar else GlassRole.Floating,
  optics: GlassOptics = GlassDefaults.optics(role),
  /** Independent optical weight. Dynamic reads happen in draw, so a press does not recompose. */
  opticalDepth: () -> Float = { 1f },
  /** 0 = resting, 1 = fully pressed. Uses the same raw/refraction layer at every value. */
  pressed: () -> Float = { 0f },
): Modifier {
  val anchor = rememberGlassSurfaceAnchor()
  val resolvedOptics = remember(optics) { optics.sanitized() }
  val hardwareAccelerated = LocalView.current.isHardwareAccelerated
  val requestedCapability = glassRenderCapability(
    apiLevel = Build.VERSION.SDK_INT,
    hardwareAccelerated = hardwareAccelerated,
  )
  val runtimeRefraction = remember(requestedCapability) {
    lazy(LazyThreadSafetyMode.NONE) {
      if (requestedCapability == GlassRenderCapability.RuntimeRefraction) {
        createGlassRuntimeRefractionOrNull()
      } else {
        null
      }
    }
  }
  val perimeterOptics = falloff == GlassFalloff.Uniform && resolvedOptics.rimWidth.value > 0f

  DisposableEffect(state, anchor) {
    state.registerSurface(anchor)
    onDispose { state.unregisterSurface(anchor) }
  }
  return this
    .graphicsLayer {
      this.shape = shape
      this.clip = true
    }
    .onGloballyPositioned {
      anchor.originInRoot = it.positionInRoot()
      anchor.surfaceSize = it.size
    }
    .drawWithCache {
      val outline = if (perimeterOptics) {
        shape.createOutline(size, layoutDirection, this)
      } else {
        null
      }
      val runtimeCornerRadius = outline?.let(::runtimeGlassCornerRadiusOrNull)
      val runtimeEligible = shouldCreateGlassRuntimeRefraction(
        requestedCapability = requestedCapability,
        perimeterOptics = perimeterOptics,
        hasRefraction = resolvedOptics.refractionStrength.value > 0f,
        runtimeCornerRadius = runtimeCornerRadius,
      )
      val resolvedRuntimeRefraction = if (runtimeEligible) runtimeRefraction.value else null
      val capability = resolveGlassRenderCapability(
        requestedCapability = requestedCapability,
        runtimeEligible = runtimeEligible,
        runtimeAvailable = resolvedRuntimeRefraction != null,
      )
      val samplesBackdrop = capability != GlassRenderCapability.StaticRim
      anchor.capability = capability
      anchor.requiresOpticalSample = perimeterOptics &&
        samplesBackdrop &&
        resolvedOptics.refractionStrength.value > 0f
      if (samplesBackdrop) {
        val heavy = state.blurRadius.toPx()
        val soft = heavy * SoftBlurFraction
        val refractionPx = resolvedOptics.refractionStrength.toPx()
        val rimWidthPx = resolvedOptics.rimWidth.toPx()
        anchor.blurPaddingPx = calculateGlassSamplePadding(
          blurRadiusPx = heavy,
          refractionStrengthPx = refractionPx,
          rimWidthPx = rimWidthPx,
          pressedDepthBoost = resolvedOptics.pressedDepthBoost,
        )
        anchor.heavyLayer.renderEffect = BlurEffect(heavy, heavy, TileMode.Clamp)
        anchor.softLayer.renderEffect = BlurEffect(soft, soft, TileMode.Clamp)
        anchor.opticalLayer.renderEffect = if (
          capability == GlassRenderCapability.RuntimeRefraction
        ) {
          resolvedRuntimeRefraction?.renderEffect
        } else {
          null
        }
        // Keep only a whisper of the saturation lift used by system materials. A stronger filter
        // made fast-moving text behind the top bar turn into dark, noisy-looking colour bands.
        val saturate = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.04f) })
        anchor.heavyLayer.colorFilter = saturate
        anchor.softLayer.colorFilter = saturate
      } else {
        anchor.opticalLayer.renderEffect = null
      }
      val hairlinePx = maxOf(1f, 0.5f.dp.toPx())
      val opticalCache = if (outline != null) {
        buildGlassOpticalDrawCache(
          outline = outline,
          runtimeCornerRadiusPx = runtimeCornerRadius,
          optics = resolvedOptics,
        )
      } else {
        null
      }
      val steadyFadeCache = if (falloff == GlassFalloff.FadeDown) {
        GlassFadeDrawCache(
          bounds = Rect(0f, 0f, size.width, size.height),
          layerPaint = Paint(),
          softMask = Brush.verticalGradient(
            colorStops = progressiveMaskStops(coverage = 1f, holdFraction = 0.72f),
            startY = 0f,
            endY = size.height,
          ),
          heavyMask = Brush.verticalGradient(
            colorStops = progressiveMaskStops(coverage = 0.86f, holdFraction = 0.60f),
            startY = 0f,
            endY = size.height,
          ),
          overlay = Brush.verticalGradient(
            colorStops = arrayOf(
              0f to tint.overlay,
              0.55f to tint.overlay,
              1f to tint.overlay.copy(alpha = 0f),
            ),
            startY = 0f,
            endY = size.height,
          ),
        )
      } else {
        null
      }
      onDrawBehind {
        val amount = clampGlassUnit(intensity())
        if (amount <= 0.001f) return@onDrawBehind
        // Claimed before sampling, so the source keeps recording for as long as this pane is on.
        if (samplesBackdrop) state.requestSample(anchor)

        val crop = anchor.sampleCrop
        if (samplesBackdrop && anchor.hasSnapshot && crop != null) {
          // Reading the tick keeps this node invalidated in step with the source's redraws.
          state.frameTick.intValue
          val dx = crop.offsetInSurface.x
          val dy = crop.offsetInSurface.y
          when (falloff) {
            GlassFalloff.Uniform -> {
              // Always replace the sharp pixels with the lightly softened snapshot once material is
              // present, then travel from that soft radius to the full radius. Fading a blurred copy
              // directly over sharp text creates two readable contours — the noisy trail this glass
              // implementation is specifically meant to avoid during fast scrolling.
              translate(dx, dy) { drawLayer(anchor.softLayer) }
              anchor.heavyLayer.alpha = amount
              translate(dx, dy) { drawLayer(anchor.heavyLayer) }
              anchor.heavyLayer.alpha = 1f
              drawRect(tint.overlay.copy(alpha = tint.overlay.alpha * amount))
            }
            GlassFalloff.FadeDown -> drawFadingGlass(
              surface = anchor,
              dx = dx,
              dy = dy,
              tint = tint,
              amount = amount,
              steadyState = steadyFadeCache,
            )
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

        if (opticalCache != null) {
          val depth = clampGlassUnit(opticalDepth())
          val press = clampGlassUnit(pressed())
          val opticalAmount = amount * depth
          if (opticalAmount > 0.001f) {
            if (
              samplesBackdrop &&
              anchor.requiresOpticalSample &&
              anchor.hasSnapshot &&
              crop != null
            ) {
              drawRefractedGlassRim(
                surface = anchor,
                crop = crop,
                capability = capability,
                runtimeRefraction = resolvedRuntimeRefraction,
                cache = opticalCache,
                amount = opticalAmount,
                pressed = press,
                optics = resolvedOptics,
              )
            }
            drawGlassOpticalRims(
              cache = opticalCache,
              optics = resolvedOptics,
              amount = opticalAmount,
              pressed = press,
            )
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

private class GlassOpticalDrawCache(
  val bounds: Rect,
  val outline: Outline,
  val centre: Offset,
  val runtimeCornerRadiusPx: Float?,
  val refractionStrengthPx: Float,
  val rimWidthPx: Float,
  val refractionMask: Stroke,
  val innerRimStroke: Stroke,
  val outerRimStroke: Stroke,
  val specularStroke: Stroke,
  val innerRimBrush: Brush,
  val outerRimBrush: Brush,
  val specularBrush: Brush,
  val layerPaint: Paint,
)

private fun androidx.compose.ui.draw.CacheDrawScope.buildGlassOpticalDrawCache(
  outline: Outline,
  runtimeCornerRadiusPx: Float?,
  optics: GlassOptics,
): GlassOpticalDrawCache {
  val rim = maxOf(1f, optics.rimWidth.toPx())
  val refraction = optics.refractionStrength.toPx()
  val centre = Offset(size.width * 0.5f, size.height * 0.5f)
  val directionSpan = maxOf(size.width, size.height) * 0.72f
  val lightStart = centre + optics.lightDirection * directionSpan
  val lightEnd = centre - optics.lightDirection * directionSpan
  val innerRimBrush = Brush.linearGradient(
    colorStops = arrayOf(
      0f to Color.White.copy(alpha = optics.innerRimAlpha),
      0.42f to Color.White.copy(alpha = optics.innerRimAlpha * 0.34f),
      0.60f to Color.Transparent,
      1f to Color.Black.copy(alpha = optics.innerShadowAlpha),
    ),
    start = lightStart,
    end = lightEnd,
  )
  val outerRimBrush = Brush.linearGradient(
    colorStops = arrayOf(
      0f to Color.White.copy(alpha = optics.outerRimAlpha),
      0.38f to Color.White.copy(alpha = optics.outerRimAlpha * 0.46f),
      0.64f to Color.Transparent,
      1f to Color.Black.copy(alpha = optics.innerShadowAlpha * 0.82f),
    ),
    start = lightStart,
    end = lightEnd,
  )
  val specularBrush = Brush.linearGradient(
    colorStops = arrayOf(
      0f to Color.White.copy(alpha = optics.specularAlpha),
      0.30f to Color.White.copy(alpha = optics.specularAlpha * 0.52f),
      0.58f to Color.Transparent,
      1f to Color.Transparent,
    ),
    start = lightStart,
    end = lightEnd,
  )
  val opticalBand = maxOf(rim * 3f, refraction * 2f + rim)
  return GlassOpticalDrawCache(
    bounds = Rect(0f, 0f, size.width, size.height),
    outline = outline,
    centre = centre,
    runtimeCornerRadiusPx = runtimeCornerRadiusPx,
    refractionStrengthPx = refraction,
    rimWidthPx = rim,
    // Strokes are centred on the outline; clipping keeps their inner half, hence the factor two.
    refractionMask = Stroke(width = opticalBand * 2f),
    innerRimStroke = Stroke(width = rim * 3.2f),
    outerRimStroke = Stroke(width = maxOf(1f, rim * 1.35f)),
    specularStroke = Stroke(width = maxOf(1f, rim * 0.72f)),
    innerRimBrush = innerRimBrush,
    outerRimBrush = outerRimBrush,
    specularBrush = specularBrush,
    layerPaint = Paint(),
  )
}

private fun DrawScope.drawRefractedGlassRim(
  surface: GlassSurfaceAnchor,
  crop: GlassSampleCrop,
  capability: GlassRenderCapability,
  runtimeRefraction: GlassRuntimeRefraction?,
  cache: GlassOpticalDrawCache,
  amount: Float,
  pressed: Float,
  optics: GlassOptics,
) {
  val pressMultiplier = 1f + pressed * optics.pressedDepthBoost
  val displacement = cache.refractionStrengthPx * amount * pressMultiplier
  val magnification = optics.magnification * amount * pressMultiplier
  val dx = crop.offsetInSurface.x
  val dy = crop.offsetInSurface.y

  val runtimeCornerRadius = cache.runtimeCornerRadiusPx
  if (
    capability == GlassRenderCapability.RuntimeRefraction &&
    runtimeRefraction != null &&
    runtimeCornerRadius != null
  ) {
    runtimeRefraction.update(
      inputWidth = crop.width.toFloat(),
      inputHeight = crop.height.toFloat(),
      surfaceLeft = -dx,
      surfaceTop = -dy,
      surfaceWidth = size.width,
      surfaceHeight = size.height,
      cornerRadius = runtimeCornerRadius,
      displacement = displacement,
      rimWidth = cache.rimWidthPx,
      magnification = magnification,
    )
  }

  drawContext.canvas.saveLayer(cache.bounds, cache.layerPaint)
  when (capability) {
    GlassRenderCapability.RuntimeRefraction -> translate(dx, dy) {
      drawLayer(surface.opticalLayer)
    }
    GlassRenderCapability.MagnifiedRim -> scale(
      scaleX = 1f + magnification,
      scaleY = 1f + magnification,
      pivot = cache.centre,
    ) {
      translate(dx, dy) { drawLayer(surface.opticalLayer) }
    }
    GlassRenderCapability.StaticRim -> Unit
  }
  drawOutline(
    outline = cache.outline,
    color = Color.White,
    alpha = amount,
    style = cache.refractionMask,
    blendMode = BlendMode.DstIn,
  )
  drawContext.canvas.restore()
}

private fun DrawScope.drawGlassOpticalRims(
  cache: GlassOpticalDrawCache,
  optics: GlassOptics,
  amount: Float,
  pressed: Float,
) {
  val pressGlow = 1f + pressed * optics.pressedDepthBoost * 0.35f
  drawOutline(
    outline = cache.outline,
    brush = cache.innerRimBrush,
    alpha = (amount * pressGlow).coerceAtMost(1f),
    style = cache.innerRimStroke,
  )
  drawOutline(
    outline = cache.outline,
    brush = cache.outerRimBrush,
    alpha = amount,
    style = cache.outerRimStroke,
  )
  drawOutline(
    outline = cache.outline,
    brush = cache.specularBrush,
    alpha = (amount * pressGlow).coerceAtMost(1f),
    style = cache.specularStroke,
  )
}

/**
 * Draws the ramp: heavy blur at the top, light blur in the middle, clear content at the bottom.
 *
 * Each band is drawn into its own offscreen buffer and then cut back with a vertical gradient in
 * `DstIn`, which is the only way to apply a soft mask to something already rasterised. The bands
 * overlap, so the heavy layer is still at full strength where the light one begins and the eye never
 * finds the seam between them.
 */
private class GlassFadeDrawCache(
  val bounds: Rect,
  val layerPaint: Paint,
  val softMask: Brush,
  val heavyMask: Brush,
  val overlay: Brush,
)

private fun DrawScope.drawFadingGlass(
  surface: GlassSurfaceAnchor,
  dx: Float,
  dy: Float,
  tint: GlassTint,
  amount: Float,
  steadyState: GlassFadeDrawCache?,
) {
  if (amount >= SteadyGlassThreshold && steadyState != null) {
    maskedLayer(
      layer = surface.softLayer,
      dx = dx,
      dy = dy,
      amount = 1f,
      mask = steadyState.softMask,
      bounds = steadyState.bounds,
      paint = steadyState.layerPaint,
    )
    maskedLayer(
      layer = surface.heavyLayer,
      dx = dx,
      dy = dy,
      amount = 1f,
      mask = steadyState.heavyMask,
      bounds = steadyState.bounds,
      paint = steadyState.layerPaint,
    )
    drawRect(brush = steadyState.overlay)
    return
  }

  // Never fade a blurred glyph over its sharp copy: that creates two readable contours. The soft
  // snapshot stays opaque where present, while its *coverage* grows down from the status bar. This
  // is a genuinely progressive reveal instead of the previous binary soft layer that appeared at
  // full height on the first non-zero scroll frame.
  val coverage = calculateProgressiveGlassCoverage(amount)
  if (coverage <= 0.001f) return
  maskedLayer(
    layer = surface.softLayer,
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
      layer = surface.heavyLayer,
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
  val paint = Paint()
  val mask = Brush.verticalGradient(colorStops = stops, startY = 0f, endY = size.height)
  maskedLayer(layer, dx, dy, amount, mask, bounds, paint)
}

private fun DrawScope.maskedLayer(
  layer: GraphicsLayer,
  dx: Float,
  dy: Float,
  amount: Float,
  mask: Brush,
  bounds: Rect,
  paint: Paint,
) {
  drawContext.canvas.saveLayer(bounds, paint)
  layer.alpha = amount
  translate(dx, dy) { drawLayer(layer) }
  layer.alpha = 1f
  drawRect(
    brush = mask,
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
