package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidAmbientTest {

  private val light = lightColorScheme()
  private val dark = darkColorScheme()

  @Test
  fun intensityZero_isExactlyTodaysFlatBackground() {
    // The promise `intensity` makes to every screen that has not adopted a canvas yet: at 0 this is
    // not "a very faint wash", it is the background, to the bit. A wash that is merely close would
    // put a seam between an adopted screen and an unadopted one at the exact moment they cross-fade.
    for (scheme in listOf(light, dark)) {
      for (tone in FluidHeroTone.entries) {
        val colors = fluidAmbientColors(
          scheme,
          FluidAmbient(tone = tone, motif = FluidHeroMotif.Glow, intensity = 0f),
        )
        assertEquals(scheme.background, colors.base)
        assertEquals(scheme.background, colors.wash)
        assertEquals(scheme.background, colors.glow)
        assertEquals(0f, colors.motifAlpha, 0f)
      }
    }
  }

  @Test
  fun everyToneOfTheRingLandsOnItsOwnColour() {
    // Seven destinations that all look the same is the failure this ring exists to prevent, and it
    // is the property that quietly breaks the moment someone adds an eighth entry to the enum and
    // maps it onto a container that is already taken.
    for (scheme in listOf(light, dark)) {
      val seen = FluidHeroTone.entries.map { tone ->
        fluidAmbientColors(scheme, FluidAmbient(tone = tone, motif = FluidHeroMotif.Glow)).base
      }
      assertEquals(FluidHeroTone.entries.size, seen.toSet().size)
    }
  }

  @Test
  fun urgentOverridesTheToneEverywhere() {
    // A screen with something urgent to say says it the same way wherever it is in the ring.
    val urgent = FluidHeroTone.entries.map { tone ->
      fluidAmbientColors(
        light,
        FluidAmbient(tone = tone, motif = FluidHeroMotif.Glow, urgent = true),
      ).base
    }
    assertEquals(1, urgent.toSet().size)
    assertNotEquals(
      urgent.first(),
      fluidAmbientColors(light, FluidAmbient(FluidHeroTone.Primary, FluidHeroMotif.Glow)).base,
    )
  }

  @Test
  fun theWashNeverCrossesTheBackgroundsOwnPolarity() {
    // The one thing that would actually break a page: a light theme whose canvas comes out dark, or
    // the reverse. `onBackground` is chosen for the background and never renegotiated, so a wash
    // that crossed the middle would take the body text with it.
    for ((scheme, expectLight) in listOf(light to true, dark to false)) {
      for (tone in FluidHeroTone.entries) {
        val colors = fluidAmbientColors(scheme, FluidAmbient(tone, FluidHeroMotif.Glow))
        for (colour in listOf(colors.base, colors.wash, colors.glow)) {
          assertEquals(
            "tone=$tone colour=$colour",
            expectLight,
            colour.relativeLuminance() > 0.5f,
          )
        }
      }
    }
  }

  @Test
  fun intensityScalesTheMotifWithTheColour() {
    // Half a wash under a full-strength motif reads as a decoration someone forgot to remove.
    val half = fluidAmbientColors(
      light,
      FluidAmbient(FluidHeroTone.Primary, FluidHeroMotif.Bars, intensity = 0.5f),
    )
    val full = fluidAmbientColors(light, FluidAmbient(FluidHeroTone.Primary, FluidHeroMotif.Bars))
    assertTrue(half.motifAlpha < full.motifAlpha)
    assertTrue(half.motifAlpha > 0f)
  }

  private fun Color.relativeLuminance(): Float =
    0.299f * red + 0.587f * green + 0.114f * blue
}
