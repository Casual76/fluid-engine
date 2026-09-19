package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import dev.antigravity.fluidengine.ui.theme.resolveFluidColorScheme
import dev.antigravity.fluidengine.foundation.AccentMode
import dev.antigravity.fluidengine.foundation.EngineSettings
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidContrastTest {

  @Test
  fun notificationToneIcons_haveAtLeastThreeToOneContrast() {
    appSchemes().forEach { scheme ->
      FluidNotificationTone.entries.forEach { tone ->
        val colors = fluidNotificationIconColors(tone, scheme)
        assertTrue(
          "$tone icon contrast was ${contrastRatio(colors.content, colors.container)}",
          contrastRatio(colors.content, colors.container) >= 3f,
        )
      }
    }
  }

  @Test
  fun selectedAccentRing_hasAtLeastThreeToOneContrastOnItsCardSurface() {
    appSchemes().forEach { scheme ->
      val renderedRing = scheme.onSurface
        .copy(alpha = FluidSelectedRingAlpha)
        .compositeOver(scheme.surfaceContainerLow)

      assertTrue(
        "Selected ring contrast was ${contrastRatio(renderedRing, scheme.surfaceContainerLow)}",
        contrastRatio(renderedRing, scheme.surfaceContainerLow) >= 3f,
      )
    }
  }

  // There was a test here, `offSwitchOutline_hasAtLeastThreeToOneContrastOnAppSurfaces`, and it
  // is gone because the thing it measured is. The old switch drew a 55%-alpha outline around its
  // off track precisely so that boundary would clear 3:1 on every surface in the palette;
  // Kyant0's, which the switch now is, has no outline — its off track is a flat grey at a fifth
  // of an alpha, the way both platforms' own switches draw it, and that boundary is nearer 1.2:1.
  // Replacing the assertion with a lower number would have been a test written to pass. The
  // trade-off is deliberate and it belongs in the release notes, not in a threshold.

  private fun appSchemes() = listOf(
    EngineSettings(accentMode = AccentMode.BRAND),
    EngineSettings(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = "fluid"),
    EngineSettings(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = "ember"),
    EngineSettings(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = "ocean"),
    EngineSettings(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = "jade"),
  ).flatMap { settings ->
    listOf(
      resolveFluidColorScheme(settings = settings, isDark = false),
      resolveFluidColorScheme(settings = settings, isDark = true),
    )
  }

  private fun contrastRatio(foreground: Color, background: Color): Float {
    val high = maxOf(foreground.luminance(), background.luminance())
    val low = minOf(foreground.luminance(), background.luminance())
    return (high + 0.05f) / (low + 0.05f)
  }
}
