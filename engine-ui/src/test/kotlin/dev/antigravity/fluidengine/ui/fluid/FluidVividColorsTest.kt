package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.antigravity.fluidengine.foundation.AccentMode
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.theme.AccentPoles
import dev.antigravity.fluidengine.ui.theme.AccentPreset
import dev.antigravity.fluidengine.ui.theme.resolveFluidColorScheme
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidVividColorsTest {

  @Test
  fun from_picksContentReadableOnBothEndsOfSaturatedGradients() {
    // The saturated families a vivid card is actually asked to carry. White fails on several of
    // these (2.2:1 on iOS orange), which is exactly why the content is computed, not assumed.
    val gradients = listOf(
      Color(0xFFFF3B30) to Color(0xFFFF2D55),
      Color(0xFFFF9500) to Color(0xFFFF6B00),
      Color(0xFF34C759) to Color(0xFF00C7BE),
      Color(0xFF00A86B) to Color(0xFF00C7BE),
      Color(0xFFFF2D55) to Color(0xFFAF52DE),
    )
    // Full blue and violet accents sit in the luminance band where no single content colour
    // reaches 4.5 on both ends — white's worst on blue-to-indigo is ~3.95, and that IS the best
    // pick. The threshold sits between the right choice and the wrong one (dark content: ~3.34),
    // so it still fails on a bad pick without demanding the physically impossible.
    val midLuminance = listOf(
      Color(0xFF007AFF) to Color(0xFF5856D6),
      Color(0xFF9966CC) to Color(0xFF5856D6),
    )

    gradients.forEach { (start, end) -> assertWorstRatioAtLeast(start, end, 4.5f) }
    midLuminance.forEach { (start, end) -> assertWorstRatioAtLeast(start, end, 3.5f) }
  }

  private fun assertWorstRatioAtLeast(start: Color, end: Color, minimum: Float) {
    val colors = FluidVividColors.from(start, end)
    val worst = minOf(
      contrastRatio(colors.content, colors.start),
      contrastRatio(colors.content, colors.end),
    )
    assertTrue(
      "content unreadable on $start -> $end: worst ratio $worst",
      worst >= minimum,
    )
  }

  @Test
  fun bandColors_giveSevenTonesSevenDistinctStarts() {
    // Only schemes whose accent stays clear of the derivation anchors can promise a full ring:
    // a pole-less preset sitting *on* an anchor (ember on the orange pole, ocean on the indigo
    // one) collapses tertiary or secondary into primary by construction. That degeneracy predates
    // the band; poles exist precisely to fix it for the accents that care.
    val fullRing = setOf("brand light", "brand dark", "brand amoled", "violet light", "violet dark", "fluid light", "fluid dark", "jade light", "jade dark")
    schemes().filter { it.first in fullRing }.forEach { (name, scheme) ->
      val starts = ringTones().map { fluidHeroBandColors(scheme, it, urgent = false).start }
      assertTrue(
        "expected 7 distinct band starts in $name, got ${starts.distinct().size}",
        starts.distinct().size == ringTones().size,
      )
    }
  }

  @Test
  fun bandColors_keepContentReadableOnBothEndsInEveryScheme() {
    // 4.0 rather than 4.5: the band's type is large and bold, and some ring mixes land in the
    // luminance band where neither near-black nor near-white reaches 4.5 against a full accent.
    // Anything below 4.0 is a wrong content pick, not an unlucky mix.
    schemes().forEach { (name, scheme) ->
      ringTones().forEach { tone ->
        listOf(false, true).forEach { urgent ->
          val colors = fluidHeroBandColors(scheme, tone, urgent)
          val worst = minOf(
            contrastRatio(colors.content, colors.start),
            contrastRatio(colors.content, colors.end),
          )
          assertTrue(
            "band content unreadable for $tone (urgent=$urgent) in $name: worst $worst",
            worst >= 4.0f,
          )
        }
      }
    }
  }

  private fun ringTones() = FluidHeroTone.entries

  private fun schemes() = buildList {
    val violet = AccentPreset(
      name = "amethyst",
      label = "Ametista",
      light = Color(0xFF9966CC),
      dark = Color(0xFFB88CE8),
      poles = AccentPoles(
        secondaryLight = Color(0xFF007AFF),
        secondaryDark = Color(0xFF0A84FF),
        tertiaryLight = Color(0xFFFF2D55),
        tertiaryDark = Color(0xFFFF375F),
        secondaryBlend = 0.45f,
        tertiaryBlend = 0.40f,
      ),
    )
    add(
      "brand light" to resolveFluidColorScheme(
        settings = EngineSettings(accentMode = AccentMode.BRAND),
        isDark = false,
      ),
    )
    add(
      "brand dark" to resolveFluidColorScheme(
        settings = EngineSettings(accentMode = AccentMode.BRAND),
        isDark = true,
      ),
    )
    add(
      "brand amoled" to resolveFluidColorScheme(
        settings = EngineSettings(themeMode = ThemeMode.AMOLED, accentMode = AccentMode.BRAND),
        isDark = true,
      ),
    )
    add(
      "violet light" to resolveFluidColorScheme(
        settings = EngineSettings(accentMode = AccentMode.BRAND),
        isDark = false,
        brand = violet,
      ),
    )
    add(
      "violet dark" to resolveFluidColorScheme(
        settings = EngineSettings(accentMode = AccentMode.BRAND),
        isDark = true,
        brand = violet,
      ),
    )
    dev.antigravity.fluidengine.ui.theme.fluidAccentPresets.forEach { preset ->
      add(
        "${preset.name} light" to resolveFluidColorScheme(
          settings = EngineSettings(
            accentMode = AccentMode.CUSTOM_PRESET,
            customAccentName = preset.name,
          ),
          isDark = false,
        ),
      )
      add(
        "${preset.name} dark" to resolveFluidColorScheme(
          settings = EngineSettings(
            accentMode = AccentMode.CUSTOM_PRESET,
            customAccentName = preset.name,
          ),
          isDark = true,
        ),
      )
    }
  }

  private fun contrastRatio(foreground: Color, background: Color): Float {
    val high = maxOf(foreground.luminance(), background.luminance())
    val low = minOf(foreground.luminance(), background.luminance())
    return (high + 0.05f) / (low + 0.05f)
  }
}
