package dev.antigravity.fluidengine.ui.theme

import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.fluid.FluidAppearance
import org.junit.Assert.assertEquals
import org.junit.Test

/** F10.8 · L'accento dell'aspetto comune viaggia come colore `#rrggbb`: il tema lo porta su un preset suo. */
class FluidAccentHexTest {

  @Test
  fun `il nome resta il primo modo, maiuscole comprese`() {
    assertEquals("ember", fluidAccentPresetFor("Ember", fluidAccentPresets).name)
    assertEquals("fluid", fluidAccentPresetFor("sconosciuto", fluidAccentPresets).name)
  }

  @Test
  fun `un colore di un preset, in chiaro o in scuro, da' quel preset`() {
    assertEquals("ember", fluidAccentPresetFor("#FF9500", fluidAccentPresets).name)
    assertEquals("ember", fluidAccentPresetFor("#ff9f0a", fluidAccentPresets).name)
    assertEquals("ocean", fluidAccentPresetFor("#5856d6", fluidAccentPresets).name)
    assertEquals("jade", FluidAppearance.presetName("#34C759"))
  }

  @Test
  fun `un colore che nessuno ha da' il piu vicino, anche fra i preset di un'app`() {
    assertEquals("jade", fluidAccentPresetFor("#00ff00", fluidAccentPresets).name)
    assertEquals("ember", fluidAccentPresetFor("#ff8000", fluidAccentPresets).name)
    val own = listOf(AccentPreset("viola", "Viola", Color(0xFF8E44AD), Color(0xFFA569BD)), AccentPreset("rosso", "Rosso", Color(0xFFE74C3C), Color(0xFFEC7063)))
    assertEquals("rosso", fluidAccentPresetFor("#ff9500", own).name)
    // Un testo che sembra un colore ma non lo è: il primo, come per un nome sconosciuto.
    assertEquals("viola", fluidAccentPresetFor("#zzzzzz", own).name)
  }
}
