package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.glass.backdrop.Backdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.BackdropEffectScope
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.emptyBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.rememberCombinedBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.layerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.rememberLayerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.drawBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.blur
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.colorControls
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.lens
import dev.antigravity.fluidengine.ui.glass.backdrop.highlight.Highlight
import dev.antigravity.fluidengine.ui.glass.backdrop.highlight.HighlightStyle
import dev.antigravity.fluidengine.ui.glass.backdrop.isRenderEffectSupported
import dev.antigravity.fluidengine.ui.glass.backdrop.isRuntimeShaderSupported
import dev.antigravity.fluidengine.ui.glass.backdrop.shadow.InnerShadow
import dev.antigravity.fluidengine.ui.glass.backdrop.shadow.Shadow

/**
 * Fluid Glass — a real refracting material, not a translucent rectangle.
 *
 * The optics are done by the vendored `backdrop` renderer in
 * [dev.antigravity.fluidengine.ui.glass.backdrop] (Kyant0/AndroidLiquidGlass, Apache-2.0; see
 * `LICENSES/AndroidLiquidGlass.md`). This file is the engine's opinion about *how much* of it each
 * kind of surface gets, and nothing else: no shader of ours competes with it.
 *
 * What the material actually does, in the order the pixels see it:
 *
 *  1. **Capture.** [glassBackdropSource] records the screen's body into a `GraphicsLayer`. Every
 *     pane of glass on that screen samples the same recording, transformed into its own coordinates,
 *     so a bar and a button hold the *same* image rather than two independent screenshots.
 *  2. **Vibrancy.** Saturation is pushed past 1. Glass concentrates the colour it transmits, and
 *     without this step a pane over a photograph reads as grey plastic.
 *  3. **Blur.** The frosting. Kept lighter than the old implementation on purpose — the previous
 *     glass compensated for having no real refraction by nearly hiding what was behind it.
 *  4. **Lens.** A signed-distance field of the pane's own shape drives a displacement that grows
 *     towards the edge, so the backdrop *bends* into the perimeter the way a thick edge bends light.
 *     This is the step the old implementation only faked in a 2 dp rim, and its absence is why every
 *     control looked flat.
 *  5. **Tint, highlight, shadows.** A specular ring lit from one angle, an inner shadow that gives
 *     the pane thickness, and a drop shadow that lifts it off the page.
 *
 * Steps 2–4 need `RenderEffect` (API 31) and AGSL (API 33). Below those the material degrades on its
 * own: API 31–32 keeps blur and vibrancy and loses the lens, and below API 31 [glassSurface] paints
 * [GlassTint.fallback] — a defined, near-opaque material rather than a broken one.
 *
 * The API is the same one the engine has always exposed — [rememberGlassBackdrop],
 * [glassBackdropSource], [glassSurface] — so screens do not change. [GlassOptics] did change: its
 * vocabulary is now the physical one (how deep the lens reaches, how far it displaces) instead of
 * the rim-painting one, because there is no rim being painted any more.
 */
@Stable
class GlassBackdropState internal constructor(
  internal val backdrop: Backdrop,
  /**
   * The layer this state records into, when it has one.
   *
   * Null for a state assembled out of other states by [rememberCombinedGlassBackdrop]: that one has
   * nothing of its own to record, it only says which recordings to stack and in what order.
   */
  internal val layerBackdrop: LayerBackdrop?,
  /** Depth of the frosting, before each role's own multiplier. */
  val blurRadius: Dp,
) {

  /** False below API 31: no `RenderEffect`, so surfaces fall back to a solid material. */
  val supported: Boolean get() = isRenderEffectSupported()

  /** False below API 33: blur and vibrancy still work, the lens does not. */
  val refractionSupported: Boolean get() = isRuntimeShaderSupported()
}

/**
 * Reference depth of the frosting. **Two dp.**
 *
 * This number went 8 → 16 → 2, and the middle step is the mistake worth writing down. The reasoning
 * for widening it was sound in isolation — a wider kernel lets the tint come down, and it is the
 * tint that made the old glass look like painted plastic — but it answered the wrong question. At 16
 * dp a pane stops transmitting an image and starts transmitting that image's *average*, and a pane
 * holding an average is a fill. All the work the lens does at the edge is then bending a flat colour
 * into another flat colour, which is invisible by construction.
 *
 * Real Liquid Glass is nearly clear. What identifies it is the **displacement** at the perimeter and
 * the specular line on top, not the frosting — so the radius stays tiny and the lens carries the
 * material. Surfaces that genuinely have to hide something (a scrim, a full-page modal) ask for a
 * multiple of this through [GlassOptics.blurScale] instead of everything paying for it.
 *
 * Calibrated against Kyant's own catalogue and against Square (github.com/Lelonio/Square), which
 * ships the same renderer tuned by hand and lands on the same 2 dp.
 */
val DefaultGlassBlurRadius: Dp = 2.dp

@Composable
fun rememberGlassBackdrop(blurRadius: Dp = DefaultGlassBlurRadius): GlassBackdropState {
  val layerBackdrop = rememberLayerBackdrop()
  return remember(layerBackdrop, blurRadius) {
    GlassBackdropState(layerBackdrop, layerBackdrop, blurRadius)
  }
}

/**
 * Stacks two backdrops into one, back to front.
 *
 * This is what lets glass sit on glass and still be honest. A control on a floating bar has to
 * refract the *bar* — its material, its tint, the icons printed on it — and not the page three
 * layers down; combining the page's recording with the bar's own gives the control exactly the image
 * a real lens resting on that bar would find.
 */
@Composable
fun rememberCombinedGlassBackdrop(
  behind: GlassBackdropState,
  inFront: GlassBackdropState,
  blurRadius: Dp = inFront.blurRadius,
): GlassBackdropState {
  val combined = rememberCombinedBackdrop(behind.backdrop, inFront.backdrop)
  return remember(combined, blurRadius) { GlassBackdropState(combined, null, blurRadius) }
}

/**
 * Marks the content that glass surfaces sample.
 *
 * Apply this to the scrolling body of a screen and never to the whole screen, otherwise the bars end
 * up sampling themselves. The body must also paint its own opaque background *inside* the recorded
 * region: a recording of text on transparency refracts into a smear that the sharp original still
 * shows through.
 */
fun Modifier.glassBackdropSource(state: GlassBackdropState): Modifier {
  // A combined state has no layer of its own; the states it was built from record theirs.
  val layer = state.layerBackdrop ?: return this
  return this.layerBackdrop(layer)
}

/**
 * The pane of glass the controls in this subtree should refract.
 *
 * Chrome provides it around its own action slot, so a `FluidBarAction` an app drops into a top bar
 * bends *that bar* without the app ever having to hold a backdrop or pass one down. Null means there
 * is no live material in scope — a plain page, a dialog in its own window — and controls then draw
 * their rim and their touch response without a backdrop to bend.
 */
val LocalGlassBackdrop = compositionLocalOf<GlassBackdropState?> { null }

/**
 * A backdrop with nothing in it.
 *
 * Not a failure case: a control over ordinary opaque content genuinely has nothing to refract, and
 * this lets it keep its rim, its highlight and its whole gesture response instead of needing a
 * second component to exist for that situation.
 */
@Composable
fun rememberEmptyGlassBackdrop(blurRadius: Dp = DefaultGlassBlurRadius): GlassBackdropState =
  remember(blurRadius) { GlassBackdropState(emptyBackdrop(), null, blurRadius) }

/** The backdrop a control should use unless its caller names a different one. */
@Composable
fun currentGlassBackdrop(): GlassBackdropState =
  LocalGlassBackdrop.current ?: rememberEmptyGlassBackdrop()

/**
 * The screen's *ambient* recording: the coloured wash and motif painted under the page, and nothing
 * else.
 *
 * This is the twin of [LocalGlassBackdrop], and the reason there have to be two is the only piece of
 * this architecture that cannot be skipped. [LocalGlassBackdrop] holds the screen's whole scrolling
 * body, which is right for chrome standing *over* it and catastrophic for a card standing *inside*
 * it: that card would sample a recording containing itself, and optical feedback is the one artefact
 * in this material that cannot be hidden.
 *
 * A content surface therefore refracts this one — painted and recorded *before* the list, so it can
 * never contain a single pixel of it — while bars, tab bars and modals go on refracting the body.
 *
 * Null means the screen has no ambient canvas. That is not a failure: [dev.antigravity.fluidengine
 * .ui.theme.FluidCard] and friends then draw exactly the opaque surface they always did, so asking
 * for glass on a screen that has nothing to show through it costs nothing and breaks nothing.
 */
val LocalFluidCanvasBackdrop = compositionLocalOf<GlassBackdropState?> { null }

/** The ambient recording in scope, or null when the screen has no canvas. */
@Composable
fun currentCanvasBackdrop(): GlassBackdropState? = LocalFluidCanvasBackdrop.current

/** Colour treatment of a glass surface. */
@Immutable
data class GlassTint(
  /** Painted over the refracted backdrop. Keep it low: the material is meant to transmit. */
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

  /**
   * A card or a grouped list standing *in* the page rather than over it.
   *
   * The one role that carries no drop shadow at all, and the reason is not restraint: content glass
   * is at the same depth as the text beside it. A shadow would say it floats, and the moment two
   * cards in a scrolling list both say that, the page reads as a stack of loose slabs. What it keeps
   * is a short, sharp bevel — an edge, not a dome — because at this size the edge is the whole tell.
   */
  Content,
}

/**
 * How thick the glass is, physically.
 *
 * Every distance is in `Dp` and describes the *edge*, because that is where glass does its work:
 * [refractionHeight] is how far in from the perimeter the bending is still felt, and
 * [refractionAmount] is how far the backdrop is dragged along the surface normal at the very edge.
 * A tall height with a small amount is a gentle dome; a short height with a large amount is a sharp
 * bevel. Apple's controls sit near height == amount.
 */
@Immutable
data class GlassOptics(
  /** Multiplies [GlassBackdropState.blurRadius]. Interactive glass is nearly clear. */
  val blurScale: Float,
  val refractionHeight: Dp,
  val refractionAmount: Dp,
  /**
   * Adds a centre-directed term to the surface normal, turning a bevelled edge into a whole dome.
   * Right for a small control the eye reads as a lens; wrong for a wide bar, where it would make the
   * middle of the screen swim.
   */
  val depthEffect: Boolean,
  /**
   * Splits the refracted sample into seven wavelengths. Only ever correct on a small, thick,
   * moving surface: on a large one it costs seven taps per pixel and reads as a colour fringe.
   */
  val dispersion: Boolean,
  /** Saturation multiplier applied to the transmitted image. 1 = off. */
  val vibrancy: Float,
  /** Specular ring drawn along the perimeter. */
  val highlightWidth: Dp,
  val highlightAlpha: Float,
  /** Degrees, clockwise from the positive x axis: where the light is coming from. */
  val highlightAngle: Float,
  /** Inner shadow: the pane's own thickness, seen from inside. */
  val innerShadowRadius: Dp,
  val innerShadowAlpha: Float,
  /** Drop shadow: how far off the page the pane sits. */
  val shadowRadius: Dp,
  val shadowAlpha: Float,
  /** Extra displacement while a finger is down, as a fraction of [refractionAmount]. */
  val pressedDepthBoost: Float,
)

internal fun clampGlassUnit(value: Float): Float =
  if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

private fun finiteNonNegative(value: Float): Float =
  if (value.isFinite()) value.coerceAtLeast(0f) else 0f

/**
 * Clamps a hand-written [GlassOptics] into the range the renderer can honour.
 *
 * A negative refraction height makes the lens shader sample outside its own input and a NaN alpha
 * poisons a whole `RenderEffect` chain, so malformed values are corrected rather than propagated.
 */
internal fun GlassOptics.sanitized(): GlassOptics = copy(
  // The ceiling is a multiple of [DefaultGlassBlurRadius], and it had to move when that did. At 4
  // it was a sane cap on a 16 dp reference — 64 dp of frosting, past which nothing gets any more
  // hidden and every pixel still costs. With the reference down at 2 dp the same 4 silently halved
  // the top bar, which is the one surface that genuinely needs a wide kernel: the cap was quietly
  // deciding a design question instead of catching a malformed value, which is its only job.
  blurScale = finiteNonNegative(blurScale).coerceAtMost(16f),
  refractionHeight = finiteNonNegative(refractionHeight.value).dp,
  refractionAmount = finiteNonNegative(refractionAmount.value).dp,
  vibrancy = if (vibrancy.isFinite()) vibrancy.coerceIn(0f, 3f) else 1f,
  highlightWidth = finiteNonNegative(highlightWidth.value).dp,
  highlightAlpha = clampGlassUnit(highlightAlpha),
  highlightAngle = if (highlightAngle.isFinite()) highlightAngle else 45f,
  innerShadowRadius = finiteNonNegative(innerShadowRadius.value).dp,
  innerShadowAlpha = clampGlassUnit(innerShadowAlpha),
  shadowRadius = finiteNonNegative(shadowRadius.value).dp,
  shadowAlpha = clampGlassUnit(shadowAlpha),
  pressedDepthBoost = if (pressedDepthBoost.isFinite()) {
    pressedDepthBoost.coerceIn(0f, 2f)
  } else {
    0f
  },
)

object GlassDefaults {

  /**
   * Chrome.
   *
   * The lens reaches a long way in but displaces gently: a bar is a wide, shallow sheet, and a
   * strong displacement across that width would make the whole top of the screen wobble while a list
   * moves under it. No drop shadow — the bar is flush with the screen, not floating over it.
   */
  private val BarOptics = GlassOptics(
    // The one surface that keeps a heavy kernel, and it earns it: a top bar is the only pane with
    // *sharp text scrolling underneath it*, arriving from a page it does not control. Everything
    // else in this file stands over something soft — a wash, a bar's own fill, a control's track —
    // and can afford to be nearly clear. This cannot, and going clear here was a mistake that made
    // the top of every screen look like it was leaking.
    //
    // Ten dp, not sixteen. Sixteen did stop the leak, and it also stopped the bar being glass: past
    // roughly five times the base radius the page underneath is averaged to a single colour, and a
    // pane with a flat colour behind it has nothing left to refract. What separates the bar from
    // the page is then the tint, which a painted rectangle would have done for nothing.
    blurScale = 5f,
    refractionHeight = 20.dp,
    refractionAmount = 16.dp,
    depthEffect = false,
    dispersion = false,
    vibrancy = 1.6f,
    highlightWidth = 0.8.dp,
    highlightAlpha = 0.5f,
    highlightAngle = 90f,
    innerShadowRadius = 0.dp,
    innerShadowAlpha = 0f,
    shadowRadius = 0.dp,
    shadowAlpha = 0f,
    pressedDepthBoost = 0f,
  )

  /**
   * A floating capsule: navigation that content passes beneath.
   *
   * This is the surface people look at longest, so it carries the full treatment — a visible bevel,
   * a lit rim, thickness on the inside and a shadow that separates it from whatever scrolls under.
   */
  private val FloatingOptics = GlassOptics(
    // Light, but not nothing: a floating capsule travels over arbitrary content, and its labels are
    // eight pixels tall. Enough to stop a saturated photograph from eating the word "Impostazioni",
    // and far short of the radius that would turn the bar back into a fill.
    blurScale = 1.8f,
    // 19 and 29 dp: the numbers Kyant's own Apple-matched capsule uses, and the ones Square
    // converges on independently. The amount running *past* the height is what makes the edge read
    // as a thick bevel rather than a soft dome — the reverse, which this had, reads as plastic.
    refractionHeight = 19.dp,
    refractionAmount = 29.dp,
    depthEffect = false,
    dispersion = false,
    vibrancy = 1.6f,
    highlightWidth = 0.8.dp,
    highlightAlpha = 0.6f,
    highlightAngle = 90f,
    innerShadowRadius = 8.dp,
    innerShadowAlpha = 0.25f,
    shadowRadius = 20.dp,
    shadowAlpha = 0.7f,
    pressedDepthBoost = 0.35f,
  )

  /**
   * A control.
   *
   * Barely frosted on purpose. A button this size cannot afford to hide what is behind it and still
   * look like glass: it is the *bend* at its edge that identifies the material, so the blur comes
   * almost all the way down and the lens comes all the way up. Dispersion is on — this is the one
   * place small enough for seven samples per pixel to be worth what it costs.
   */
  private val InteractiveOptics = GlassOptics(
    // A control is a lens, and a lens is nearly clear — but a *label* on a lens still has to be
    // read against whatever the lens happens to be standing over. This is the least frosting that
    // survives a button parked over a photograph. Surfaces that carry no text at all — the tab bar's
    // indicator, a switch thumb — override it back to zero at their call sites.
    blurScale = 1.4f,
    refractionHeight = 10.dp,
    // Held to well under the radius of the smallest control the system has. A 44 dp lens displacing
    // 24 dp drags in whatever happens to be a centimetre away and reads as a coloured halo rather
    // than as an edge.
    refractionAmount = 14.dp,
    // A dome, on the one kind of surface small enough for the eye to read the whole thing as a
    // single lens. Wrong for anything wider — see the note on [GlassOptics.depthEffect] — and wrong
    // even here below about 40 dp, which is why a switch thumb turns it back off.
    depthEffect = true,
    dispersion = true,
    vibrancy = 1.6f,
    highlightWidth = 0.8.dp,
    highlightAlpha = 0.55f,
    highlightAngle = 90f,
    innerShadowRadius = 6.dp,
    innerShadowAlpha = 0.3f,
    shadowRadius = 12.dp,
    shadowAlpha = 0.5f,
    pressedDepthBoost = 0.6f,
  )

  /**
   * A sheet or an alert.
   *
   * Deep frosting, because a sheet has to win an argument with a whole page of content behind it,
   * and a wide bevel that makes its top edge read as a thick slab rather than a cut.
   */
  private val ModalOptics = GlassOptics(
    // Heavy, because the things wearing this preset — an in-app notification, an alert — arrive
    // over a page that is still fully sharp and are the only thing separating themselves from it.
    // A modal that comes with a scrim does not need it and says so: the pop-up's own optics turn
    // this down, because a second heavy blur on top of a blurred scrim is what turns a pane into a
    // grey rectangle with no material left in it.
    // Which is why it is three and a half and not six: at six the page behind an alert is a colour
    // rather than a picture, and the alert stops looking like it is *in front of* anything at all.
    blurScale = 3.5f,
    refractionHeight = 20.dp,
    refractionAmount = 28.dp,
    depthEffect = false,
    dispersion = false,
    vibrancy = 1.6f,
    highlightWidth = 0.8.dp,
    highlightAlpha = 0.6f,
    highlightAngle = 90f,
    innerShadowRadius = 12.dp,
    innerShadowAlpha = 0.2f,
    shadowRadius = 32.dp,
    shadowAlpha = 0.8f,
    pressedDepthBoost = 0f,
  )

  /**
   * A card or a grouped list, in the page.
   *
   * The frosting is heavier than a bar's because what is behind this is a coloured wash and the text
   * printed on top has to win against it — legibility here is bought with radius, as everywhere else
   * in this file. The lens is the opposite: short reach, modest displacement, no dome. A dome on a
   * surface that is 300 dp wide and full of text makes the middle of the paragraph swim.
   *
   * Nothing outside the pane at all: no drop shadow, and only enough inner shadow to give the edge a
   * thickness. See [GlassRole.Content].
   */
  private val ContentOptics = GlassOptics(
    // What is behind a content card is an ambient wash — already soft, with nothing sharp in it to
    // hide. Frosting it again buys nothing and costs the only thing worth having: the wash's own
    // gradient bending into the card's perimeter.
    // So: barely.
    blurScale = 0.8f,
    refractionHeight = 18.dp,
    refractionAmount = 22.dp,
    depthEffect = false,
    dispersion = false,
    vibrancy = 1.6f,
    highlightWidth = 0.8.dp,
    highlightAlpha = 0.5f,
    highlightAngle = 90f,
    innerShadowRadius = 6.dp,
    innerShadowAlpha = 0.16f,
    shadowRadius = 0.dp,
    shadowAlpha = 0f,
    pressedDepthBoost = 0f,
  )

  /** Stable, allocation-free presets. Custom callers can use `copy` and are sanitized at use. */
  fun optics(role: GlassRole): GlassOptics = when (role) {
    GlassRole.Bar -> BarOptics
    GlassRole.Floating -> FloatingOptics
    GlassRole.Interactive -> InteractiveOptics
    GlassRole.Modal -> ModalOptics
    GlassRole.Content -> ContentOptics
  }

  /**
   * The colour every pane of glass is made of.
   *
   * **Not `MaterialTheme.surface`.** That is the mistake this replaces, and it is the one that made
   * the navigation pill disappear: matching the film to the page's own background means a bar over
   * an AMOLED-black page is a black bar, and a black bar over black content is not a translucent
   * material, it is nothing. Apple's Liquid Glass is a *bright, reflective* material — in dark mode
   * it reads as a distinctly lighter frosted panel, never as a darker rectangle.
   *
   * So the film starts from a fixed pair of glass greys, near-white on a light page and a mid grey
   * on a dark one, and only then takes a third of the palette's own container colour so it still
   * belongs to the app's accent and to Material You. A third is enough to be felt and not enough to
   * let an AMOLED theme drag it back down to the background it is supposed to stand out from.
   *
   * The greys are Square's (github.com/Lelonio/Square), which arrived at them by hand against the
   * same renderer.
   */
  @Composable
  internal fun glassFilm(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (isDarkSurface()) {
      androidx.compose.ui.graphics.lerp(DarkGlassGrey, scheme.surfaceContainerHigh, PaletteShare)
    } else {
      androidx.compose.ui.graphics.lerp(LightGlassGrey, scheme.surfaceContainerLowest, PaletteShare)
    }
  }

  private val LightGlassGrey = Color(0xFFFAFAFA)
  private val DarkGlassGrey = Color(0xFF4A4A4E)

  /** How much of the app's own palette the film is allowed to take. See [glassFilm]. */
  private const val PaletteShare = 0.34f

  /**
   * Bars.
   *
   * Denser than it looks, and it has to be: with the frosting down at 2 dp the film is now doing all
   * of the separating on its own. The old value tried the opposite trade — a very light film over a
   * very wide blur — and produced a bar that hid its background completely while looking like
   * nothing in particular.
   */
  @Composable
  fun barTint(): GlassTint {
    val dark = isDarkSurface()
    return GlassTint(
      overlay = glassFilm().copy(alpha = if (dark) 0.40f else 0.44f),
      fallback = MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.95f else 0.96f),
      hairline = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.16f else 0.10f),
    )
  }

  /** Floating navigation: a little denser, because it travels over arbitrary content. */
  @Composable
  fun floatingTint(): GlassTint {
    val dark = isDarkSurface()
    return GlassTint(
      overlay = glassFilm().copy(alpha = if (dark) 0.48f else 0.52f),
      fallback = MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.96f else 0.97f),
      hairline = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.18f else 0.08f),
    )
  }

  /**
   * Controls.
   *
   * Almost nothing. A control is a lens sitting on a surface that is itself already glass; a second
   * opaque wash here is what made the old buttons read as grey pills stuck onto a bar.
   */
  @Composable
  fun controlTint(): GlassTint {
    val dark = isDarkSurface()
    val scheme = MaterialTheme.colorScheme
    // Tinted with the app's own accent, and that is not decoration. A control's film is the only
    // thing standing between its label and whatever it is floating over; a white one at 18% was
    // effectively no film at all, and the buttons came out completely transparent with unreadable
    // text on them. Taking a fifth of the accent gives the pane a colour of its own to hold the
    // label against, and ties every control in the app to the one accent the palette has.
    val film = androidx.compose.ui.graphics.lerp(glassFilm(), scheme.primary, AccentShare)
    return GlassTint(
      overlay = film.copy(alpha = if (dark) 0.34f else 0.38f),
      fallback = if (dark) {
        scheme.surface.copy(alpha = 0.88f)
      } else {
        scheme.surface.copy(alpha = 0.90f)
      },
      hairline = Color.Transparent,
    )
  }

  /** How much of the accent a control's film takes. Enough to be felt, not enough to be a fill. */
  private const val AccentShare = 0.20f

  /**
   * The capsule that marks a selection inside a bar.
   *
   * Barely there, because it is not what carries the selection: the tab underneath it is already
   * showing through in the accent colour, and a strong fill here would only cover that up. It exists
   * to give the selected tab an edge to sit inside.
   */
  @Composable
  fun selectionTint(): GlassTint {
    val dark = isDarkSurface()
    return GlassTint(
      overlay = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
      fallback = if (dark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.10f),
      hairline = Color.Transparent,
    )
  }

  /**
   * Cards and grouped lists.
   *
   * The one place the film is allowed to lean back towards the app's own surface rather than towards
   * the glass grey, because this is the only surface that holds paragraphs: what it stands over is a
   * coloured wash, and a bright film plus a colour wash plus body text is one layer too many for the
   * contrast to survive. The rule that goes with it is that the text itself never joins in — labels,
   * icons and badges over content glass stay fully opaque.
   */
  @Composable
  fun contentTint(): GlassTint {
    val dark = isDarkSurface()
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    return GlassTint(
      overlay = androidx.compose.ui.graphics.lerp(surface, glassFilm(), 0.4f)
        .copy(alpha = if (dark) 0.52f else 0.56f),
      // The opaque material a card falls back to is exactly the one it used before it could be
      // glass, so an API 30 device sees the page it has always seen rather than a degraded copy.
      fallback = surface,
      hairline = Color.Transparent,
    )
  }

  /** Sheets and alerts. */
  @Composable
  fun modalTint(): GlassTint {
    val dark = isDarkSurface()
    return GlassTint(
      overlay = glassFilm().copy(alpha = if (dark) 0.50f else 0.54f),
      fallback = MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.97f else 0.98f),
      hairline = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.14f else 0.08f),
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
  internal fun isDarkSurface(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f
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
   * there is a line across the screen with sharp content on one side and refracted content on the
   * other. That line is the single most artificial-looking part of a translucent bar; ramping the
   * whole material out — glass, tint and rim together — removes it entirely.
   */
  FadeDown,
}

/**
 * Turns a composable into a pane of glass over [state]'s content.
 *
 * @param intensity 0 = glass absent, 1 = full material. Animate this instead of the bar's own alpha
 *   so that the controls sitting on the glass stay fully opaque and legible throughout.
 * @param opticalDepth independent weight on the lens, the rim and the shadows, leaving the blur and
 *   the tint alone. A control uses it to melt its own edge away while it is at rest.
 * @param pressed 0 = resting, 1 = fully pressed. Deepens the lens without re-recording anything.
 * @param exports when set, this pane also publishes *itself* — material, tint and all — into that
 *   state, so another pane can refract it. That is how a control on a bar bends the bar rather than
 *   the page underneath it. Pair it with [rememberCombinedGlassBackdrop].
 * @param layerBlock transforms applied to the whole pane. The renderer inverts them when it samples,
 *   so a bar that scales or slides keeps holding the image the world actually has behind it.
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
  opticalDepth: () -> Float = { 1f },
  pressed: () -> Float = { 0f },
  exports: GlassBackdropState? = null,
  layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
): Modifier {
  val resolved = remember(optics) { optics.sanitized() }

  // Below API 31 there is no RenderEffect at all: no blur, no lens, no vibrancy. Sampling the
  // backdrop there would hand a *sharp* copy of the page to the bar, which is worse than not
  // sampling it. A defined solid material is the honest degradation.
  if (!isRenderEffectSupported()) {
    return this.glassFallback(shape, tint, edge, falloff, intensity)
  }

  val currentIntensity by rememberUpdatedState(intensity)
  val currentDepth by rememberUpdatedState(opticalDepth)
  val currentPressed by rememberUpdatedState(pressed)
  val shapeBlock = remember(shape) { { shape } }
  val blurRadius = state.blurRadius

  val effects: BackdropEffectScope.() -> Unit = remember(resolved, blurRadius) {
    {
      val amount = clampGlassUnit(currentIntensity())
      if (amount > 0.001f) {
        val depth = clampGlassUnit(currentDepth()) * amount
        val press = clampGlassUnit(currentPressed())

        if (resolved.vibrancy != 1f) {
          // Ramped with the material rather than switched on: a bar whose colours saturate a frame
          // before it has any blur announces itself as an effect.
          colorControls(saturation = 1f + (resolved.vibrancy - 1f) * amount)
        }
        blur(blurRadius.toPx() * resolved.blurScale * amount)
        lens(
          refractionHeight = resolved.refractionHeight.toPx() * depth,
          refractionAmount = resolved.refractionAmount.toPx() * depth *
            (1f + press * resolved.pressedDepthBoost),
          depthEffect = resolved.depthEffect,
          chromaticAberration = resolved.dispersion,
        )
      }
    }
  }

  val highlight: () -> Highlight? = remember(resolved) {
    val style = HighlightStyle.Default(
      color = Color.White.copy(alpha = 0.5f),
      angle = resolved.highlightAngle,
      falloff = 1f,
    );
    {
      val amount = clampGlassUnit(currentIntensity()) * clampGlassUnit(currentDepth())
      if (resolved.highlightAlpha <= 0f || amount <= 0.001f) {
        null
      } else {
        Highlight(
          width = resolved.highlightWidth,
          blurRadius = resolved.highlightWidth / 2f,
          alpha = resolved.highlightAlpha * amount,
          style = style,
        )
      }
    }
  }

  val shadow: () -> Shadow? = remember(resolved) {
    {
      val amount = clampGlassUnit(currentIntensity()) * clampGlassUnit(currentDepth())
      if (resolved.shadowAlpha <= 0f || amount <= 0.001f) {
        null
      } else {
        Shadow(
          radius = resolved.shadowRadius,
          color = Color.Black.copy(alpha = 0.1f),
          alpha = resolved.shadowAlpha * amount,
        )
      }
    }
  }

  val innerShadow: () -> InnerShadow? = remember(resolved) {
    {
      val amount = clampGlassUnit(currentIntensity()) * clampGlassUnit(currentDepth())
      if (resolved.innerShadowAlpha <= 0f || amount <= 0.001f) {
        null
      } else {
        InnerShadow(
          radius = resolved.innerShadowRadius,
          color = Color.Black.copy(alpha = 0.15f),
          alpha = resolved.innerShadowAlpha * amount,
        )
      }
    }
  }

  val onDrawSurface: DrawScopeBlock = remember(tint, edge, falloff) {
    {
      val amount = clampGlassUnit(currentIntensity())
      if (amount > 0.001f) {
        drawGlassTint(tint, edge, falloff, amount)
      }
    }
  }

  return this
    .glassMask(falloff, resolved.shadowRadius, currentIntensity)
    .drawBackdrop(
      backdrop = state.backdrop,
      shape = shapeBlock,
      effects = effects,
      highlight = highlight,
      shadow = shadow,
      innerShadow = innerShadow,
      layerBlock = layerBlock,
      exportedBackdrop = exports?.layerBackdrop,
      onDrawSurface = onDrawSurface,
      backdropScale = if (layerBlock != null) {
        1f
      } else {
        glassResolutionScale(blurRadius.value * resolved.blurScale)
      },
    )
}

/**
 * What fraction of its own resolution a pane processes its backdrop at.
 *
 * The `RenderEffect` chain is charged per pixel and it is the most expensive thing the design system
 * does, so a full-screen scrim is worth six times a small one. What pays for the shortcut is the
 * blur itself — a wide kernel is exactly what hides the upscale — which is why the fraction is a
 * function of the radius and nothing else. At zero blur nothing is hidden and the pane stays at full
 * resolution, because a clear lens is *all* edge and a soft edge is the one thing it cannot have.
 *
 * The floor is deliberately conservative. Pushing it lower is tempting and self-defeating: the
 * effect parameters are pre-multiplied by this, so far enough down a modest blur collapses to under
 * a pixel and the upscale has nothing left smoothing it, which reads as pixelation rather than as
 * frost.
 */
internal fun glassResolutionScale(blurRadiusDp: Float): Float {
  if (!blurRadiusDp.isFinite() || blurRadiusDp <= 0f) return 1f
  val t = (blurRadiusDp / GlassFullQualityBlurDp).coerceIn(0f, 1f)
  return 1f - t * (1f - GlassMinResolutionScale)
}

/** Lowest fraction a heavily frosted pane may drop to. */
internal const val GlassMinResolutionScale = 0.4f

/** Radius at or above which that floor is safe, in dp. */
internal const val GlassFullQualityBlurDp = 10f

private typealias DrawScopeBlock = DrawScope.() -> Unit

private fun DrawScope.drawGlassTint(
  tint: GlassTint,
  edge: GlassEdge,
  falloff: GlassFalloff,
  amount: Float,
) {
  val overlay = tint.overlay.copy(alpha = tint.overlay.alpha * amount)
  when (falloff) {
    GlassFalloff.Uniform -> drawRect(overlay)
    // The tint runs out earlier than the glass does, so the bottom of a bar is refraction with no
    // colour left in it — which is what stops the ramp from ending on a visible grey lip.
    GlassFalloff.FadeDown -> drawRect(
      brush = Brush.verticalGradient(
        colorStops = arrayOf(
          0f to overlay,
          0.55f to overlay,
          1f to overlay.copy(alpha = 0f),
        ),
        startY = 0f,
        endY = size.height,
      ),
    )
  }

  if (tint.hairline.alpha <= 0f || edge == GlassEdge.None) return
  val hairlinePx = maxOf(1f, 0.5f.dp.toPx())
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

/**
 * Fades the finished material — glass, rim, shadows, everything — instead of any one of its parts.
 *
 * It has to sit *outside* the renderer, because a mask applied inside the recorded layer would be
 * blurred along with the image and the ramp would grow its own soft lip. Wrapping the node in a
 * single `saveLayer` and cutting the result with `DstIn` is the only way to apply a gradient to
 * something already rasterised.
 *
 * When there is nothing to fade the modifier disappears entirely, so an ordinary pane at full
 * strength never pays for an offscreen buffer.
 */
private fun Modifier.glassMask(
  falloff: GlassFalloff,
  shadowRadius: Dp,
  intensity: () -> Float,
): Modifier {
  if (falloff == GlassFalloff.Uniform) {
    return this.drawWithCache {
      val paint = Paint()
      // Inflated so the drop shadow, which lives outside the pane, is not cut off by its own layer.
      val padding = shadowRadius.toPx() * 2f
      val bounds = Rect(-padding, -padding, size.width + padding, size.height + padding)
      onDrawWithContent {
        val amount = clampGlassUnit(intensity())
        when {
          amount <= 0.001f -> Unit
          amount >= 0.999f -> drawContent()
          else -> {
            paint.alpha = amount
            drawContext.canvas.saveLayer(bounds, paint)
            drawContent()
            drawContext.canvas.restore()
          }
        }
      }
    }
  }
  return this.drawWithCache {
    val paint = Paint()
    val bounds = Rect(0f, 0f, size.width, size.height)
    onDrawWithContent {
      val amount = clampGlassUnit(intensity())
      if (amount <= 0.001f) return@onDrawWithContent
      paint.alpha = amount
      drawContext.canvas.saveLayer(bounds, paint)
      drawContent()
      drawRect(
        brush = Brush.verticalGradient(
          colorStops = fadeDownStops(amount),
          startY = 0f,
          endY = size.height,
        ),
        blendMode = BlendMode.DstIn,
      )
      drawContext.canvas.restore()
    }
  }
}

/**
 * The ramp's coverage grows with the material rather than appearing at full height.
 *
 * A bar that reaches its final extent on the first non-zero scroll frame and only then gains opacity
 * reads as a rectangle switching on. Growing downward reads as the material arriving.
 */
internal fun fadeDownStops(amount: Float): Array<Pair<Float, Color>> {
  val coverage = smoothStep(clampGlassUnit(amount)).coerceIn(0.001f, 1f)
  val hold = coverage * 0.62f
  return arrayOf(
    0f to Color.White,
    hold to Color.White,
    coverage to Color.Transparent,
    1f to Color.Transparent,
  )
}

/** Below API 31 the material is a defined solid, deliberately not an approximation of glass. */
private fun Modifier.glassFallback(
  shape: Shape,
  tint: GlassTint,
  edge: GlassEdge,
  falloff: GlassFalloff,
  intensity: () -> Float,
): Modifier = this
  .clip(shape)
  .drawWithContent {
    val amount = clampGlassUnit(intensity())
    if (amount > 0.001f) {
      drawGlassTint(
        tint = tint.copy(overlay = tint.fallback),
        edge = edge,
        falloff = falloff,
        amount = amount,
      )
    }
    drawContent()
  }
