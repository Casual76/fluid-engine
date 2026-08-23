package dev.antigravity.fluidengine.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.antigravity.fluidengine.foundation.AccentMode
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeColorSchemeTest {

  @Test
  fun dynamicMode_preservesEverySystemAccentPair() {
    val system = lightColorScheme(
      primary = Color(0xFF102030),
      onPrimary = Color(0xFFF0F1F2),
      primaryContainer = Color(0xFF203040),
      onPrimaryContainer = Color(0xFFE0E1E2),
      secondary = Color(0xFF304050),
      onSecondary = Color(0xFFD0D1D2),
      secondaryContainer = Color(0xFF405060),
      onSecondaryContainer = Color(0xFFC0C1C2),
      tertiary = Color(0xFF506070),
      onTertiary = Color(0xFFB0B1B2),
      tertiaryContainer = Color(0xFF607080),
      onTertiaryContainer = Color(0xFFA0A1A2),
      surface = Color(0xFFFF00FF),
    )

    val resolved = resolveFluidColorScheme(
      settings = EngineSettings(accentMode = AccentMode.DYNAMIC, dynamicColorEnabled = true),
      isDark = false,
      dynamicScheme = system,
    )

    assertEquals(system.primary, resolved.primary)
    assertEquals(system.onPrimary, resolved.onPrimary)
    assertEquals(system.primaryContainer, resolved.primaryContainer)
    assertEquals(system.onPrimaryContainer, resolved.onPrimaryContainer)
    assertEquals(system.secondary, resolved.secondary)
    assertEquals(system.onSecondary, resolved.onSecondary)
    assertEquals(system.secondaryContainer, resolved.secondaryContainer)
    assertEquals(system.onSecondaryContainer, resolved.onSecondaryContainer)
    assertEquals(system.tertiary, resolved.tertiary)
    assertEquals(system.onTertiary, resolved.onTertiary)
    assertEquals(system.tertiaryContainer, resolved.tertiaryContainer)
    assertEquals(system.onTertiaryContainer, resolved.onTertiaryContainer)
    assertNotEquals(system.surface, resolved.surface)
  }

  @Test
  fun fixedPalettes_tintContainerSurfacesAndKeepReadableContentPairs() {
    val brand = resolveFluidColorScheme(
      settings = EngineSettings(accentMode = AccentMode.BRAND),
      isDark = false,
    )
    val orange = resolveFluidColorScheme(
      settings = EngineSettings(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = "ember"),
      isDark = false,
    )

    assertNotEquals(brand.surfaceContainer, orange.surfaceContainer)
    assertNotEquals(brand.surfaceContainerHigh, orange.surfaceContainerHigh)
    assertNotEquals(brand.primary, brand.secondary)
    assertNotEquals(brand.primary, brand.tertiary)
    assertNotEquals(brand.secondary, brand.tertiary)
    listOf(
      brand.onPrimary to brand.primary,
      brand.onPrimaryContainer to brand.primaryContainer,
      brand.onSecondaryContainer to brand.secondaryContainer,
      brand.onTertiaryContainer to brand.tertiaryContainer,
      orange.onPrimary to orange.primary,
      orange.onPrimaryContainer to orange.primaryContainer,
      orange.onSecondaryContainer to orange.secondaryContainer,
      orange.onTertiaryContainer to orange.tertiaryContainer,
    ).forEach { (foreground, background) ->
      assertTrue(
        "Expected at least 4.5:1, got ${contrastRatio(foreground, background)}",
        contrastRatio(foreground, background) >= 4.5f,
      )
    }
  }

  @Test
  fun disabledDynamicMode_fallsBackToBrandInsteadOfUsingSystemRoles() {
    val system = lightColorScheme(primary = Color.Magenta)
    val resolved = resolveFluidColorScheme(
      settings = EngineSettings(accentMode = AccentMode.DYNAMIC, dynamicColorEnabled = false),
      isDark = false,
      dynamicScheme = system,
    )

    assertEquals(fluidBrandAccent(isDark = false), resolved.primary)
    assertNotEquals(system.primary, resolved.primary)
  }

  @Test
  fun amoledMode_keepsTheBackgroundTrulyBlack() {
    val resolved = resolveFluidColorScheme(
      settings = EngineSettings(themeMode = ThemeMode.AMOLED, accentMode = AccentMode.BRAND),
      isDark = true,
    )

    assertEquals(Color.Black, resolved.background)
  }

  private fun contrastRatio(foreground: Color, background: Color): Float {
    val high = maxOf(foreground.luminance(), background.luminance())
    val low = minOf(foreground.luminance(), background.luminance())
    return (high + 0.05f) / (low + 0.05f)
  }
}
