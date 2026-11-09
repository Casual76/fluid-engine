package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.foundation.AccentMode
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.theme.resolveFluidColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Il testo del badge e il suo contrasto, su ogni tema e anche sopra la pellicola del vetro. */
class FluidBadgeTest {

  private val lightFilm = Color(0xFFFAFAFA)
  private val darkFilm = Color(0xFF4A4A4E)

  private data class Case(val name: String, val scheme: ColorScheme, val film: Color)

  private fun cases(): List<Case> =
    listOf("fluid", "ember", "ocean", "jade").flatMap { name ->
      listOf(false, true).map { dark ->
        Case(
          "$name/dark=$dark",
          resolveFluidColorScheme(
            settings = EngineSettings(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = name),
            isDark = dark,
          ),
          if (dark) darkFilm else lightFilm,
        )
      }
    }

  // --- Testo -------------------------------------------------------------------------------------------

  @Test
  fun `numbers up to the cap are written as they are`() {
    assertEquals("1", fluidBadgeText(1))
    assertEquals("12", fluidBadgeText(12))
    assertEquals("99", fluidBadgeText(99))
  }

  @Test
  fun `past the cap it says cap plus - 100 is 99+ and so is a million`() {
    assertEquals("99+", fluidBadgeText(100))
    assertEquals("99+", fluidBadgeText(1_000_000))
    assertEquals("99+", fluidBadgeText(Int.MAX_VALUE))
  }

  @Test
  fun `a custom cap is respected`() {
    assertEquals("9", fluidBadgeText(9, max = 9))
    assertEquals("9+", fluidBadgeText(10, max = 9))
    assertEquals("999+", fluidBadgeText(5_000, max = 999))
  }

  @Test
  fun `a cap under one is brought to one`() {
    assertEquals("1", fluidBadgeText(1, max = 0))
    assertEquals("1+", fluidBadgeText(2, max = -5))
  }

  @Test
  fun `zero and negative numbers write nothing - a badge at zero is not drawn`() {
    assertEquals("", fluidBadgeText(0))
    assertEquals("", fluidBadgeText(-1))
    assertEquals("", fluidBadgeText(Int.MIN_VALUE))
  }

  // --- Contrasto -----------------------------------------------------------------------------------

  private val stress = listOf(
    Color(0xFFFFF176), Color(0xFFBDBDBD), Color(0xFF8BC34A), Color(0xFF00BE64), Color(0xFF4CAF50),
    Color(0xFF42A5F5), Color(0xFF9575CD), Color(0xFFEF5350), Color(0xFF808080), Color(0xFF1F2A44),
    Color(0xFF101010), Color.White, Color.Black,
  )

  @Test
  fun `the fill stands out at 3 to 1 from every surface and the glass film, the digit reads at 4,5`() {
    cases().forEach { case ->
      val behinds = listOf(case.scheme.surface, case.scheme.surfaceContainerLow, case.scheme.surfaceContainerHigh, case.film)
      (stress + case.scheme.primary + FluidIdentityColors.palette.flatMap { listOf(it.light, it.dark) }).forEach { color ->
        val result = fluidBadgeColors(color, case.scheme, case.film)
        behinds.forEach { behind ->
          val ratio = fluidContrastRatio(result.fill, behind)
          assertTrue("${case.name}: $color -> ${result.fill} su $behind: $ratio:1", ratio >= FluidFillMinContrast - 0.02f)
        }
        val ink = fluidContrastRatio(result.ink, result.fill)
        assertTrue("${case.name}: $color -> cifra ${result.ink} su ${result.fill}: $ink:1", ink >= FluidInkMinContrast - 0.02f)
      }
    }
  }

  @Test
  fun `the theme accent reads as it is on every preset where it already did - the default does not drift`() {
    // Se tutti gli accenti di serie venissero spostati, il badge di un'app che non ha scelto un colore
    // non somiglierebbe piu' all'accento: il contratto e' «del minimo che serve».
    var untouched = 0
    cases().forEach { case ->
      val result = fluidBadgeColors(case.scheme.primary, case.scheme, case.film)
      if (result.fill == case.scheme.primary) untouched++
    }
    assertTrue("nessun accento di serie era gia' leggibile: $untouched", untouched > 0)
  }

  @Test
  fun `a transparent colour is measured opaque`() {
    val case = cases().first()
    val opaque = fluidBadgeColors(Color(0xFF1565C0), case.scheme, case.film)
    val translucent = fluidBadgeColors(Color(0xFF1565C0).copy(alpha = 0.2f), case.scheme, case.film)
    assertEquals(opaque, translucent)
  }
}
