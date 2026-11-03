package dev.antigravity.fluidengine.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.theme.FluidDefaultBrand
import dev.antigravity.fluidengine.ui.theme.fluidColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I colori di un widget come interi ARGB, per i widget `RemoteViews` (3.0.1), e la regola chiaro/scuro.
 */
class EngineWidgetArgbTest {

  @Test
  fun `il tema dell'app vince su quello del launcher, SYSTEM lo segue`() {
    assertTrue(engineWidgetIsDark(EngineSettings(themeMode = ThemeMode.SYSTEM), launcherIsDark = true))
    assertFalse(engineWidgetIsDark(EngineSettings(themeMode = ThemeMode.SYSTEM), launcherIsDark = false))
    assertFalse(engineWidgetIsDark(EngineSettings(themeMode = ThemeMode.LIGHT), launcherIsDark = true))
    assertTrue(engineWidgetIsDark(EngineSettings(themeMode = ThemeMode.DARK), launcherIsDark = false))
    assertTrue(engineWidgetIsDark(EngineSettings(themeMode = ThemeMode.AMOLED), launcherIsDark = false))
  }

  @Test
  fun `gli interi sono i ruoli dello schema, e i traslucidi sono gia' composti sulla card`() {
    val settings = EngineSettings()
    val light = fluidColorScheme(settings = settings, isDark = false, brand = FluidDefaultBrand, dynamicScheme = null)
    val argb = engineWidgetArgbOf(light, isDark = false)

    assertEquals(light.onSurface.toArgb(), argb.onSurface)
    assertEquals(light.primary.toArgb(), argb.accent)
    assertEquals(light.surfaceContainerLow.toArgb(), argb.card)
    // Il velo del separatore e' composto: un widget sta sullo sfondo, e un alfa farebbe passare lo sfondo.
    assertEquals(0xFF, argb.hairline ushr 24)
    assertNotEquals(argb.card, argb.hairline)
    assertEquals(Color(0xFFE65100).toArgb(), argb.warning)
  }

  @Test
  fun `il set scuro ha i suoi valori fissi`() {
    val dark = fluidColorScheme(settings = EngineSettings(), isDark = true, brand = FluidDefaultBrand, dynamicScheme = null)
    val argb = engineWidgetArgbOf(dark, isDark = true)
    assertTrue(argb.isDark)
    assertEquals(Color(0xFFFFB74D).toArgb(), argb.warning)
    assertEquals(Color(0xFF81C784).toArgb(), argb.success)
    assertEquals(dark.background.toArgb(), argb.background)
  }
}
