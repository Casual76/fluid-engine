package dev.antigravity.fluidengine.wear.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.antigravity.fluidengine.ui.theme.FluidDefaultBrand
import dev.antigravity.fluidengine.ui.theme.fluidColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidWearColorSchemeTest {

  private val phone = fluidColorScheme(FluidWearDefaults.settings, isDark = true, brand = FluidDefaultBrand)
  private val wear = fluidWearColorScheme(phone)

  private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return maxOf(la, lb) / minOf(la, lb)
  }

  @Test
  fun backgroundIsBlackOnTheDefaultSettings() {
    assertEquals(Color.Black, wear.background)
  }

  @Test
  fun rolesComeFromThePhoneScheme() {
    assertEquals(phone.primary, wear.primary)
    assertEquals(phone.onSurface, wear.onSurface)
    assertEquals(phone.surfaceContainer, wear.surfaceContainer)
  }

  @Test
  fun textReadsOnTheBackground() {
    assertTrue(contrast(wear.onSurface, wear.background) >= 4.5f)
    assertTrue(contrast(wear.onBackground, wear.background) >= 4.5f)
    assertTrue(contrast(wear.onSurfaceVariant, wear.background) >= 4.5f)
  }

  @Test
  fun dimRolesSitBetweenTheRoleAndTheFloor() {
    assertTrue(wear.primaryDim.luminance() < wear.primary.luminance())
    assertTrue(wear.primaryDim.luminance() >= wear.background.luminance())
  }
}
