package dev.antigravity.fluidengine.wear.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.wear.compose.material3.ColorScheme as WearColorScheme
import androidx.wear.compose.material3.MaterialTheme as WearMaterialTheme
import androidx.wear.compose.material3.Shapes as WearShapes
import androidx.wear.compose.material3.Typography as WearTypography
import dev.antigravity.fluidengine.foundation.AccentMode
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidDisplayFontFamily
import dev.antigravity.fluidengine.ui.fluid.FluidFontFamily
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.theme.AccentPreset
import dev.antigravity.fluidengine.ui.theme.FluidDefaultBrand
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import androidx.compose.material3.ColorScheme as PhoneColorScheme
import androidx.compose.material3.MaterialTheme as PhoneMaterialTheme

/**
 * The settings a watch starts from.
 *
 * AMOLED and the brand accent, always: a watch face is black glass in a black bezel, and anything
 * lighter than black is a lit pixel the battery pays for on every frame. Dynamic colour is off for
 * the same reason it is off on the phone apps that ship a brand: the brand is the point.
 */
object FluidWearDefaults {
  val settings: EngineSettings = EngineSettings(
    themeMode = ThemeMode.AMOLED,
    accentMode = AccentMode.BRAND,
    dynamicColorEnabled = false,
    amoledEnabled = true,
    hapticsEnabled = true,
  )
}

/**
 * The Fluid design system on a watch.
 *
 * Two themes, nested on purpose, not by accident:
 *
 *  * outside, [FluidTheme] — the phone's. The glass reads its tints, its side (dark or light) and
 *    its haptics from there, and so do `fluidPressable` and every other engine-ui modifier. Reusing
 *    it unchanged is what makes a glass disc on the watch the *same* material as the phone's
 *    transport buttons, rather than a lookalike that drifts.
 *  * inside, Material 3 for Wear — a different library with a different type scale and colour
 *    roles, which every Wear component (TimeText, ScreenScaffold, lists, pickers) reads. Its
 *    scheme is mapped from the phone scheme above ([fluidWearColorScheme]), so there is one source
 *    of colour and two views of it.
 *
 * Screens read `androidx.wear.compose.material3.MaterialTheme`. The phone MaterialTheme is still in
 * scope, but only engine-ui should be looking at it.
 */
@Composable
fun FluidWearTheme(
  brand: AccentPreset = FluidDefaultBrand,
  settings: EngineSettings = FluidWearDefaults.settings,
  content: @Composable () -> Unit,
) {
  FluidTheme(settings = settings, brand = brand, systemBars = false) {
    val phone = PhoneMaterialTheme.colorScheme
    val colors = remember(phone) { fluidWearColorScheme(phone) }
    val typography = remember { fluidWearTypography() }
    val shapes = remember { fluidWearShapes() }
    WearMaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
  }
}

/**
 * The Wear colour roles, read off the phone's.
 *
 * The roles Wear has that the phone does not (`*Dim`) are the same colour a step towards the floor:
 * Wear uses them for pressed and disabled containers, which on the phone are carried by alpha.
 * The background is the phone's, which under [FluidWearDefaults.settings] is pure black.
 */
fun fluidWearColorScheme(phone: PhoneColorScheme): WearColorScheme {
  fun dim(color: Color) = lerp(color, phone.background, DimFraction)
  return WearColorScheme(
    primary = phone.primary,
    primaryDim = dim(phone.primary),
    primaryContainer = phone.primaryContainer,
    onPrimary = phone.onPrimary,
    onPrimaryContainer = phone.onPrimaryContainer,
    secondary = phone.secondary,
    secondaryDim = dim(phone.secondary),
    secondaryContainer = phone.secondaryContainer,
    onSecondary = phone.onSecondary,
    onSecondaryContainer = phone.onSecondaryContainer,
    tertiary = phone.tertiary,
    tertiaryDim = dim(phone.tertiary),
    tertiaryContainer = phone.tertiaryContainer,
    onTertiary = phone.onTertiary,
    onTertiaryContainer = phone.onTertiaryContainer,
    surfaceContainerLow = phone.surfaceContainerLow,
    surfaceContainer = phone.surfaceContainer,
    surfaceContainerHigh = phone.surfaceContainerHigh,
    onSurface = phone.onSurface,
    onSurfaceVariant = phone.onSurfaceVariant,
    outline = phone.outline,
    outlineVariant = phone.outlineVariant,
    background = phone.background,
    onBackground = phone.onBackground,
    error = phone.error,
    errorDim = dim(phone.error),
    errorContainer = phone.errorContainer,
    onError = phone.onError,
    onErrorContainer = phone.onErrorContainer,
  )
}

/**
 * Wear's type scale, set in Inter.
 *
 * The sizes stay Wear's: they were chosen for a screen a forearm away and a few centimetres
 * across, and the phone's iOS-derived scale would be wrong there in both directions. What changes
 * is the face — Inter Text, and Inter Display for the display and large title slots, the same
 * 20sp threshold the phone uses — and tabular figures on the numerals, which on a watch are
 * mostly clocks and counters that must not jitter as they change.
 */
fun fluidWearTypography(): WearTypography {
  val base = WearTypography(defaultFontFamily = FluidFontFamily)
  fun TextStyle.display() = copy(fontFamily = FluidDisplayFontFamily)
  fun TextStyle.tabular() = copy(fontFamily = FluidDisplayFontFamily, fontFeatureSettings = "tnum")
  return base.copy(
    displayLarge = base.displayLarge.display(),
    displayMedium = base.displayMedium.display(),
    displaySmall = base.displaySmall.display(),
    titleLarge = base.titleLarge.display(),
    numeralExtraLarge = base.numeralExtraLarge.tabular(),
    numeralLarge = base.numeralLarge.tabular(),
    numeralMedium = base.numeralMedium.tabular(),
    numeralSmall = base.numeralSmall.tabular(),
    numeralExtraSmall = base.numeralExtraSmall.tabular(),
  )
}

/**
 * Wear's shape slots, in continuous corners.
 *
 * Wear leans on larger radii than a phone (a card on a round screen reads better soft), so the
 * slots step one rung up the [FluidRadius] ladder from where a phone would put them. Every one is
 * a [ContinuousCornerShape], never a circular-arc rounded rectangle: the corner is the part of the
 * material the eye reads first.
 */
fun fluidWearShapes(): WearShapes = WearShapes(
  extraSmall = continuous(FluidRadius.Small),
  small = continuous(FluidRadius.Control),
  medium = continuous(FluidRadius.Card),
  large = continuous(FluidRadius.Group),
  extraLarge = continuous(FluidRadius.Sheet),
)

private fun continuous(radius: androidx.compose.ui.unit.Dp): CornerBasedShape = ContinuousCornerShape(radius)

/** How far a `*Dim` role steps towards the background. */
internal const val DimFraction = 0.28f
