package dev.antigravity.fluidengine.wear.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The measures of a round watch screen.
 *
 * Wear sizes are dp like a phone's, but a watch is a 192–240 dp circle seen from a forearm away,
 * and the numbers that work there are their own: a primary control has to be a comfortable
 * thumb-press on a moving wrist (Wear's own floor is 48 dp), and anything near the edge is cut
 * by the bezel unless it follows the curve. Kept here, in the engine, so a screen never writes a
 * size by hand.
 */
object FluidWearDimens {
  /** The main action on a screen: play/pause. */
  val DiscLarge: Dp = 64.dp

  /** A secondary action beside it: previous, next. */
  val DiscMedium: Dp = 48.dp

  /** A small action on the bottom arc. Still a full touch target. */
  val DiscSmall: Dp = 40.dp

  /**
   * An action on the bottom arc of a player, sized for a thumb rather than to stay out of the way.
   * 40 dp turned out to be a miss on a real wrist. Since 2.11.0.
   */
  val DiscArc: Dp = 50.dp

  /** Space between the transport discs (previous, play, next). Since 2.11.0. */
  val TransportGap: Dp = 8.dp

  /** How far the arc's discs keep from the edge of the screen when they hug it. Since 2.11.0. */
  val ArcEdgeClearance: Dp = 6.dp

  /** The glyph in the main disc (play/pause). Since 2.11.0. */
  val IconLarge: Dp = 30.dp

  /** The glyph in a secondary or arc disc. Since 2.11.0. */
  val IconMedium: Dp = 24.dp

  /** A glyph beside text in a row or a capsule. Since 2.11.0. */
  val IconSmall: Dp = 18.dp

  /** The touch target every disc keeps whatever it draws. */
  val MinTouchTarget: Dp = 48.dp

  /** The progress ring's stroke. */
  val RingStroke: Dp = 4.dp

  /** How far the ring sits in from the edge of the screen. */
  val RingInset: Dp = 3.dp

  /** The stroke of a ring drawn on the very edge of the screen (`FluidEdgeGlowRing`). Since 2.11.0. */
  val EdgeRingStroke: Dp = 3.dp

  /** How far that ring's halo reaches in from the edge. Since 2.11.0. */
  val EdgeRingGlow: Dp = 18.dp

  /** Room the ring leaves on each side of a clock at twelve o'clock. Since 2.11.0. */
  val EdgeRingClockMargin: Dp = 6.dp

  /** Padding around the time in [dev.antigravity.fluidengine.wear.glass.FluidGlassTimePill]. Since 2.11.0. */
  val TimePillPaddingHorizontal: Dp = 10.dp

  /** See [TimePillPaddingHorizontal]. Since 2.11.0. */
  val TimePillPaddingVertical: Dp = 3.dp

  /** Where the clock pill sits below the top of the screen. Since 2.11.0. */
  val TimePillTop: Dp = 6.dp

  /** Horizontal padding a capsule keeps around its text. */
  val CapsulePaddingHorizontal: Dp = 14.dp

  /** Vertical padding a capsule keeps around its text. */
  val CapsulePaddingVertical: Dp = 6.dp

  /** A list row's cover or icon tile. */
  val RowArtwork: Dp = 40.dp

  /** Height of a pill button (Search, Library). */
  val PillHeight: Dp = 44.dp
}
