package dev.antigravity.fluidengine.wear.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme as PhoneMaterialTheme
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.theme.FluidMotionScheme
import androidx.wear.compose.material3.MaterialTheme as WearMaterialTheme

/**
 * Lends [content] an accent of its own — usually the colour of the cover that is playing.
 *
 * Both themes take it, the phone's and Wear's, because both are read underneath: engine-ui's glass
 * and selection states look at the phone scheme, the Wear components and screens at Wear's. The
 * colour is lifted until it reads on a black watch face first ([liftForBlack]) — a cover's dominant
 * colour is as likely to be a near-black as anything — and eased from one song's to the next.
 *
 * Only the colours change underneath: the phone theme is rebuilt with the typography and shapes it
 * already had and with [FluidMotionScheme], so a Material control inside moves with the engine's
 * springs (3.0) instead of falling back to Material's defaults.
 *
 * @param seed the colour to start from; null keeps the theme's own accent.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FluidWearAccent(seed: Color?, content: @Composable () -> Unit) {
  val phone = PhoneMaterialTheme.colorScheme
  val wear = WearMaterialTheme.colorScheme
  val target = seed?.let(::liftForBlack) ?: wear.primary
  val accent by animateColorAsState(target, FluidMotion.color(AccentFadeMillis), label = "accent")
  val phoneScheme = remember(phone, accent) { phone.copy(primary = accent) }
  val wearScheme = remember(wear, accent) {
    wear.copy(primary = accent, primaryDim = lerp(accent, wear.background, DimFraction))
  }
  PhoneMaterialTheme(
    colorScheme = phoneScheme,
    motionScheme = FluidMotionScheme,
    shapes = PhoneMaterialTheme.shapes,
    typography = PhoneMaterialTheme.typography,
  ) {
    WearMaterialTheme(
      colorScheme = wearScheme,
      typography = WearMaterialTheme.typography,
      shapes = WearMaterialTheme.shapes,
      content = content,
    )
  }
}

/**
 * Brightens [color] until it stands out on black: a ring, a halo or a lit heart in a near-black
 * cover colour would simply not be there.
 */
fun liftForBlack(color: Color): Color {
  val luminance = color.luminance()
  if (luminance >= AccentFloor) return color
  val amount = ((AccentFloor - luminance) * LiftGain).coerceIn(0f, 1f)
  return Color(
    red = color.red + (1f - color.red) * amount,
    green = color.green + (1f - color.green) * amount,
    blue = color.blue + (1f - color.blue) * amount,
    alpha = 1f,
  )
}

/** Lowest luminance an accent may have on a black face. */
private const val AccentFloor = 0.22f
private const val LiftGain = 2.2f
private const val AccentFadeMillis = 600
