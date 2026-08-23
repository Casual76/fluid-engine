package dev.antigravity.fluidengine.widget

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.unit.ColorProvider
import dev.antigravity.fluidengine.foundation.AccentMode
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.theme.AccentPreset
import dev.antigravity.fluidengine.ui.theme.FluidDefaultBrand
import dev.antigravity.fluidengine.ui.theme.fluidColorScheme

/**
 * A background paired with the content colour that stays readable on it.
 *
 * Pairs rather than loose colours because a widget has no theme to fall back on: once a tone is
 * split, nothing downstream can tell which content colour belonged to which container.
 */
data class EngineWidgetTone(
  val container: ColorProvider,
  val content: ColorProvider,
)

/**
 * Every colour a widget draws, resolved for both launcher themes in advance.
 *
 * Glance hands its content to the home screen as RemoteViews, so nothing can be decided at draw
 * time: each role is a day/night pair the launcher chooses between. Translucent values are
 * composited here against the surface they will sit on, because a widget's parent is the wallpaper
 * rather than the app's background — an alpha meant to soften a tile over a card would otherwise
 * let the wallpaper through it.
 */
data class EngineWidgetPalette(
  val background: ColorProvider,
  val card: ColorProvider,
  val hairline: ColorProvider,
  val onSurface: ColorProvider,
  val onSurfaceVariant: ColorProvider,
  val accent: ColorProvider,
  val accentContainer: ColorProvider,
  val onAccentContainer: ColorProvider,
  val attention: ColorProvider,
  val primaryTone: EngineWidgetTone,
  val secondaryTone: EngineWidgetTone,
  val warningTone: EngineWidgetTone,
  val dangerTone: EngineWidgetTone,
  val successTone: EngineWidgetTone,
  val neutralTone: EngineWidgetTone,
)

/**
 * Builds a widget palette from the app's own appearance settings.
 *
 * This is the whole point of the module: a widget that resolves its colours independently drifts
 * from the app the first time the accent changes, and a home screen showing last month's palette is
 * the most visible way an app looks unmaintained. Pass the same [EngineSettings] the theme gets.
 */
fun engineWidgetPalette(
  context: Context,
  settings: EngineSettings,
  brand: AccentPreset = FluidDefaultBrand,
): EngineWidgetPalette {
  val light = widgetScheme(context, settings, brand, isDark = false)
  val dark = widgetScheme(context, settings, brand, isDark = true)

  fun pair(select: ColorScheme.() -> Color): ColorProvider =
    DayNightColorProvider(day = light.select(), night = dark.select())

  fun tone(container: ColorScheme.() -> Color, content: ColorScheme.() -> Color) = EngineWidgetTone(
    container = pair(container),
    content = pair(content),
  )

  fun tintedTone(content: ColorScheme.() -> Color) = EngineWidgetTone(
    container = DayNightColorProvider(
      day = light.content().copy(alpha = TileAlpha).compositeOver(light.surfaceContainerLow),
      night = dark.content().copy(alpha = TileAlpha).compositeOver(dark.surfaceContainerLow),
    ),
    content = pair(content),
  )

  fun fixedTone(lightContent: Color, darkContent: Color) = EngineWidgetTone(
    container = DayNightColorProvider(
      day = lightContent.copy(alpha = TileAlpha).compositeOver(light.surfaceContainerLow),
      night = darkContent.copy(alpha = TileAlpha).compositeOver(dark.surfaceContainerLow),
    ),
    content = DayNightColorProvider(day = lightContent, night = darkContent),
  )

  return EngineWidgetPalette(
    background = pair { background },
    card = pair { surfaceContainerLow },
    hairline = DayNightColorProvider(
      day = light.onSurface.copy(alpha = HairlineAlpha).compositeOver(light.surfaceContainerLow),
      night = dark.onSurface.copy(alpha = HairlineAlpha).compositeOver(dark.surfaceContainerLow),
    ),
    onSurface = pair { onSurface },
    onSurfaceVariant = pair { onSurfaceVariant },
    accent = pair { primary },
    accentContainer = pair { primaryContainer },
    onAccentContainer = pair { onPrimaryContainer },
    attention = pair { error },
    primaryTone = tone({ primaryContainer }, { onPrimaryContainer }),
    secondaryTone = tone({ secondaryContainer }, { onSecondaryContainer }),
    warningTone = fixedTone(WarningContentLight, WarningContentDark),
    dangerTone = tintedTone { error },
    successTone = fixedTone(SuccessContentLight, SuccessContentDark),
    neutralTone = tone({ surfaceContainerHigh }, { onSurfaceVariant }),
  )
}

/**
 * The scheme for one launcher theme.
 *
 * A theme pinned in the app wins over the launcher's: with the app forced to Dark, its widget stays
 * dark on a light home screen — which is what every other surface of that app does, and what makes
 * the widget read as part of it.
 */
private fun widgetScheme(
  context: Context,
  settings: EngineSettings,
  brand: AccentPreset,
  isDark: Boolean,
): ColorScheme {
  val resolvedDark = when (settings.themeMode) {
    ThemeMode.SYSTEM -> isDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK,
    ThemeMode.AMOLED,
    -> true
  }
  val dynamic = if (
    settings.dynamicColorEnabled &&
    settings.accentMode == AccentMode.DYNAMIC &&
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  ) {
    if (resolvedDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
  } else {
    null
  }
  return fluidColorScheme(
    settings = settings,
    isDark = resolvedDark,
    brand = brand,
    dynamicScheme = dynamic,
  )
}

/** The same values the design system uses for an icon tile and a row separator. */
private const val TileAlpha = 0.16f
private const val HairlineAlpha = 0.10f

// Warning and success have no Material role, so the design system carries fixed pairs for them.
private val WarningContentLight = Color(0xFFE65100)
private val WarningContentDark = Color(0xFFFFB74D)
private val SuccessContentLight = Color(0xFF1B5E20)
private val SuccessContentDark = Color(0xFF81C784)
