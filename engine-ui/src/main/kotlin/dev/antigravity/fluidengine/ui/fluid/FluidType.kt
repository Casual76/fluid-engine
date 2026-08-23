package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import dev.antigravity.fluidengine.ui.R

/**
 * The app's voice.
 *
 * Roboto is the single strongest tell that something is an Android app, and no amount of layout work
 * survives it. Inter is used instead: it shares San Francisco's proportions closely enough that the
 * same optical rules apply, and — unlike a literal SF clone — it ships under the OFL.
 *
 * Two things make Inter read like SF rather than like "a nice grotesk":
 *
 *  * **Optical sizing.** Apple ships two cuts and switches at 20pt. Inter does the same through the
 *    `InterDisplay` family, which is drawn with tighter spacing and smaller apertures for headline
 *    use. Anything 20sp and up here uses Display; body text uses the Text cut.
 *  * **Tracking.** SF applies a size-dependent tracking curve — noticeably negative at title sizes,
 *    neutral around 13pt, positive below that. Inter set at a flat 0 looks loose in headlines and
 *    cramped in captions. The [tracking] curve below reproduces the shape of Apple's table.
 */
private val InterText = FontFamily(
  Font(R.font.inter_regular, FontWeight.Normal),
  Font(R.font.inter_medium, FontWeight.Medium),
  Font(R.font.inter_semibold, FontWeight.SemiBold),
  Font(R.font.inter_bold, FontWeight.Bold),
)

private val InterDisplay = FontFamily(
  Font(R.font.inter_display_semibold, FontWeight.SemiBold),
  Font(R.font.inter_display_bold, FontWeight.Bold),
)

/** The text cut, for anything a person reads in a paragraph or a row. */
val FluidFontFamily: FontFamily = InterText

/** The display cut, for titles at 20sp and above. */
val FluidDisplayFontFamily: FontFamily = InterDisplay

/**
 * Tracking, in `sp`, for a given size.
 *
 * Apple publishes this as a table rather than a formula; these are the anchor points, linearly
 * interpolated between. The values are per-size, not per-em, which is why they are applied as `sp`.
 */
private fun tracking(sizeSp: Float): TextUnit {
  val anchors = listOf(
    11f to 0.30f,
    13f to 0.00f,
    15f to -0.15f,
    17f to -0.25f,
    20f to -0.35f,
    24f to -0.45f,
    28f to -0.55f,
    34f to -0.70f,
  )
  if (sizeSp <= anchors.first().first) return anchors.first().second.sp
  if (sizeSp >= anchors.last().first) return anchors.last().second.sp
  for (i in 0 until anchors.lastIndex) {
    val (lowSize, lowTrack) = anchors[i]
    val (highSize, highTrack) = anchors[i + 1]
    if (sizeSp in lowSize..highSize) {
      val t = (sizeSp - lowSize) / (highSize - lowSize)
      return (lowTrack + (highTrack - lowTrack) * t).sp
    }
  }
  return 0f.sp
}

/**
 * Compose centres the first line inside its line box by default only when asked to.
 *
 * Without this, a 17sp line inside a 22sp line height sits with all 5sp of leading above it, so a
 * title in a fixed-height row reads as visually low. UIKit distributes leading evenly; so does this.
 */
private val FluidLineHeightStyle = LineHeightStyle(
  alignment = LineHeightStyle.Alignment.Center,
  trim = LineHeightStyle.Trim.None,
)

@Suppress("DEPRECATION")
private fun fluidStyle(
  sizeSp: Float,
  lineHeightSp: Float,
  weight: FontWeight,
  display: Boolean = sizeSp >= 20f,
  letterSpacing: TextUnit = tracking(sizeSp),
): TextStyle = TextStyle(
  fontFamily = if (display) InterDisplay else InterText,
  fontSize = sizeSp.sp,
  lineHeight = lineHeightSp.sp,
  fontWeight = weight,
  letterSpacing = letterSpacing,
  lineHeightStyle = FluidLineHeightStyle,
  platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/**
 * The iOS type scale, mapped onto Material's slots.
 *
 * Material's slot names stay because they are what every call site already asks for; what they
 * resolve to is the Apple ramp — 34 / 28 / 22 / 20 / 17 / 15 / 13 / 11 — rather than Material's
 * 57 / 45 / 36 / 32 …, which is far too large for a phone and is the other half of why the app
 * looked like a Material sample.
 */
fun fluidTypography(): Typography = Typography(
  // Large titles. 34sp Bold is the iOS navigation-bar large title.
  displayLarge = fluidStyle(34f, 41f, FontWeight.Bold),
  displayMedium = fluidStyle(34f, 41f, FontWeight.Bold),
  displaySmall = fluidStyle(30f, 36f, FontWeight.Bold),

  headlineLarge = fluidStyle(28f, 34f, FontWeight.Bold),
  headlineMedium = fluidStyle(26f, 32f, FontWeight.Bold),
  headlineSmall = fluidStyle(22f, 28f, FontWeight.Bold),

  // Titles. 17sp Semibold is the workhorse: every list row's primary line.
  titleLarge = fluidStyle(20f, 25f, FontWeight.SemiBold),
  titleMedium = fluidStyle(17f, 22f, FontWeight.SemiBold),
  titleSmall = fluidStyle(15f, 20f, FontWeight.SemiBold),

  // Body. 15sp Regular is the secondary line under a title.
  bodyLarge = fluidStyle(17f, 23f, FontWeight.Normal),
  bodyMedium = fluidStyle(15f, 21f, FontWeight.Normal),
  bodySmall = fluidStyle(13f, 18f, FontWeight.Normal),

  // Labels. Small sizes get positive tracking so they stay legible.
  labelLarge = fluidStyle(15f, 20f, FontWeight.SemiBold),
  labelMedium = fluidStyle(13f, 17f, FontWeight.Medium),
  labelSmall = fluidStyle(11f, 14f, FontWeight.SemiBold, letterSpacing = 0.5.sp),
)

/**
 * Extra styles that have no Material slot.
 *
 * [uppercaseCaption] is the grouped-list section header: uppercase needs far more tracking than the
 * curve gives, because the shapes no longer have ascenders and descenders to separate them.
 * [numeric] switches on tabular figures so a column of grades, dates or counts stays in line
 * instead of jittering as the digits change.
 */
object FluidTextStyles {

  val uppercaseCaption: TextStyle = fluidStyle(12f, 16f, FontWeight.SemiBold, letterSpacing = 0.6.sp)

  val numeric: TextStyle = fluidStyle(17f, 22f, FontWeight.SemiBold).copy(fontFeatureSettings = "tnum")

  val largeNumeric: TextStyle = fluidStyle(34f, 38f, FontWeight.Bold).copy(fontFeatureSettings = "tnum")
}
