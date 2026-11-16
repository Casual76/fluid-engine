package dev.antigravity.fluidengine.aiui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import dev.antigravity.fluidengine.aiui.overlay.FluidAssistantInk
import dev.antigravity.fluidengine.aiui.overlay.fluidAssistantInk
import dev.antigravity.fluidengine.aiui.overlay.fluidAssistantOwnsContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I colori di serie dei pezzi dell'assistente e chi di loro si fa un contenitore (4.0, F3.2b): un'app che non passa
 * niente riceve l'inchiostro del lato, una che passa un colore lo tiene; un pezzo gira da solo solo sulla pagina.
 */
class FluidAssistantInkTest {

  /** Due inchiostri qualunque: quello del lato del tema e quello del lato girato. Il conto non sa quale sia quale. */
  private val themeInk = Color(0xFF1C1B1F)
  private val flippedInk = Color(0xFFF2F2F5)

  @Test
  fun `il valore di serie vuol dire l'inchiostro del lato`() {
    assertFalse("il valore di serie non è un colore scritto", FluidAssistantInk.Default.isSpecified)
    assertEquals(themeInk, fluidAssistantInk(FluidAssistantInk.Default, themeInk))
    // Lo stesso tasto, quando il contenitore gira: il colore segue lo schema letto dentro, non quello della firma.
    assertEquals(flippedInk, fluidAssistantInk(FluidAssistantInk.Default, flippedInk))
  }

  @Test
  fun `un colore dell'app resta il suo su tutti e due i lati`() {
    val accent = Color(0xFF0A84FF)
    val error = Color(0xFFFF453A)
    assertEquals(accent, fluidAssistantInk(accent, themeInk))
    assertEquals(accent, fluidAssistantInk(accent, flippedInk))
    assertEquals(error, fluidAssistantInk(error, flippedInk))
    // Anche un colore trasparente scritto è un colore dell'app.
    val clear = Color.Transparent
    assertEquals(clear, fluidAssistantInk(clear, flippedInk))
  }

  @Test
  fun `un pezzo piccolo si fa un contenitore solo da solo sulla pagina`() {
    assertTrue("da solo sulla pagina gira", fluidAssistantOwnsContainer(onPanel = false, onAdaptiveGlass = false))
    assertFalse("su un pannello che non gira tiene il suo lato", fluidAssistantOwnsContainer(onPanel = true, onAdaptiveGlass = false))
    assertFalse(
      "dentro un contenitore o un vetro adattivo il lato è già deciso",
      fluidAssistantOwnsContainer(onPanel = false, onAdaptiveGlass = true),
    )
    assertFalse(fluidAssistantOwnsContainer(onPanel = true, onAdaptiveGlass = true))
  }
}
