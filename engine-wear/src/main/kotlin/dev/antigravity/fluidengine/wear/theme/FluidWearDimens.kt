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

  /** The touch target every disc keeps whatever it draws. */
  val MinTouchTarget: Dp = 48.dp

  /** The progress ring's stroke. */
  val RingStroke: Dp = 4.dp

  /** How far the ring sits in from the edge of the screen. */
  val RingInset: Dp = 3.dp

  /** Horizontal padding a capsule keeps around its text. */
  val CapsulePaddingHorizontal: Dp = 14.dp

  /** Vertical padding a capsule keeps around its text. */
  val CapsulePaddingVertical: Dp = 6.dp

  /** A list row's cover or icon tile. */
  val RowArtwork: Dp = 40.dp

  /** Height of a pill button (Search, Library). */
  val PillHeight: Dp = 44.dp
}
