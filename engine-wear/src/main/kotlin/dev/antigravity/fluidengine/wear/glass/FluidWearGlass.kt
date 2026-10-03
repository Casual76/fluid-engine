package dev.antigravity.fluidengine.wear.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import dev.antigravity.fluidengine.ui.fluid.GlassOptics
import dev.antigravity.fluidengine.ui.fluid.GlassTint

/**
 * The one glass a watch is made of.
 *
 * On the phone every pane picks the role that fits its job, and the roles look different on
 * purpose: a bar is quieter than a control, a floating pill darker than a button. On a watch they
 * all stand together on the same 200 dp cover, a centimetre apart, and that difference stops
 * reading as hierarchy and starts reading as two materials — a black capsule over the title and
 * grey buttons under it. So a watch screen uses this instead, everywhere: the title, the transport
 * discs, the arc, the clock, the volume, the notices.
 *
 * It is a smoked pane, not a grey film: a watch screen is almost always a cover, and the glass has
 * to hold white text against whatever that cover is doing. A sixth of the accent goes into the
 * smoke so the panes belong to the app (and, when the app takes its accent from the artwork, to
 * the song).
 */
object FluidWearGlass {

  /**
   * The lens every pane shares.
   *
   * Between the phone's floating capsule and its controls: frosted enough to read a label over a
   * photograph, a short bevel that still bends the cover at the edge. No dispersion — seven samples
   * per pixel is a cost the W-series GPUs feel on every recapture, and on a 50 dp disc it reads as
   * a colour fringe more than as glass.
   */
  val Optics: GlassOptics = GlassOptics(
    blurScale = 1.6f,
    refractionHeight = 12.dp,
    refractionAmount = 16.dp,
    depthEffect = true,
    dispersion = false,
    vibrancy = 1.5f,
    highlightWidth = 0.8.dp,
    highlightAlpha = 0.6f,
    highlightAngle = 90f,
    innerShadowRadius = 0.dp,
    innerShadowAlpha = 0f,
    shadowRadius = 14.dp,
    shadowAlpha = 0.55f,
    pressedDepthBoost = 0.5f,
  )

  /**
   * [Optics] for a pane wider than it is tall — the title capsule, the clock.
   *
   * The same numbers without the dome: a dome is right on a disc the eye reads as one lens, and on
   * a capsule it makes the middle swim under the text it carries.
   */
  val WideOptics: GlassOptics = Optics.copy(depthEffect = false)

  /**
   * The smoke every pane is filled with.
   *
   * @param accent the colour a sixth of the smoke is taken from; the theme's primary by default.
   */
  @Composable
  fun tint(accent: Color = MaterialTheme.colorScheme.primary): GlassTint = remember(accent) {
    GlassTint(
      overlay = lerp(Color.Black, accent, AccentShare).copy(alpha = SmokeAlpha),
      fallback = lerp(FallbackBase, accent, AccentShare).copy(alpha = FallbackAlpha),
      hairline = Color.White.copy(alpha = HairlineAlpha),
    )
  }

  /**
   * The same glass on a black page — a list, a menu — where there is no picture to smoke.
   *
   * Over black the smoke is black on black and the pane disappears; what makes glass visible there
   * is a lit film, the phone's grey, with the same sixth of the accent and the same rim, so a pill
   * on a list and a disc on the player still read as one material seen against two backgrounds.
   */
  @Composable
  fun pageTint(accent: Color = MaterialTheme.colorScheme.primary): GlassTint = remember(accent) {
    GlassTint(
      overlay = lerp(PageFilm, accent, AccentShare).copy(alpha = PageFilmAlpha),
      fallback = lerp(FallbackBase, accent, AccentShare).copy(alpha = FallbackAlpha),
      hairline = Color.White.copy(alpha = HairlineAlpha),
    )
  }

  /**
   * A notice that can land over anything — a cover, a list, a sheet — and has to be read the
   * moment it does. The page film made nearly opaque: the one pane that should not show through.
   */
  @Composable
  fun noticeTint(accent: Color = MaterialTheme.colorScheme.primary): GlassTint = remember(accent) {
    GlassTint(
      overlay = lerp(FallbackBase, accent, AccentShare).copy(alpha = NoticeAlpha),
      fallback = lerp(FallbackBase, accent, AccentShare).copy(alpha = FallbackAlpha),
      hairline = Color.White.copy(alpha = HairlineAlpha),
    )
  }

  /** Enough to hold white text over a bright cover, little enough to leave it a cover. */
  private const val SmokeAlpha = 0.42f
  private const val AccentShare = 0.16f
  private const val HairlineAlpha = 0.18f
  private const val FallbackAlpha = 0.95f
  private val FallbackBase = Color(0xFF141416)
  private val PageFilm = Color(0xFF4A4A4E)
  private const val PageFilmAlpha = 0.36f
  private const val NoticeAlpha = 0.88f
}
