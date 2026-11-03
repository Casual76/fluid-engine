package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.foundation.AccentMode
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.theme.AccentPreset
import dev.antigravity.fluidengine.ui.theme.fluidAccentPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La traduzione fra la pagina "Aspetto" e `EngineSettings`, senza disegnare niente: sono le regole
 * che le app sbagliavano da sole (il nero che si perde, `DYNAMIC` su un telefono che non ce l'ha).
 */
class FluidAppearanceTest {

  private val base = EngineSettings()

  @Test
  fun `le voci di tema sono tre, e AMOLED non e' una di loro`() {
    assertEquals(listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK), FluidAppearance.ThemeChoices)
    assertEquals(ThemeMode.DARK, FluidAppearance.themeChoice(base.copy(themeMode = ThemeMode.AMOLED)))
    assertEquals(ThemeMode.LIGHT, FluidAppearance.themeChoice(base.copy(themeMode = ThemeMode.LIGHT)))
  }

  @Test
  fun `il nero e' vero per il tema AMOLED come per l'interruttore`() {
    assertFalse(FluidAppearance.isPureBlack(base))
    assertTrue(FluidAppearance.isPureBlack(base.copy(amoledEnabled = true)))
    assertTrue(FluidAppearance.isPureBlack(base.copy(themeMode = ThemeMode.AMOLED)))
  }

  @Test
  fun `scegliere un tema non perde il nero di chi era in AMOLED`() {
    val amoled = base.copy(themeMode = ThemeMode.AMOLED)
    val light = FluidAppearance.withThemeMode(amoled, ThemeMode.LIGHT)
    assertEquals(ThemeMode.LIGHT, light.themeMode)
    assertTrue("il nero si e' piegato nell'interruttore", light.amoledEnabled)
    val dark = FluidAppearance.withThemeMode(amoled, ThemeMode.DARK)
    assertEquals(ThemeMode.DARK, dark.themeMode)
    assertTrue(dark.amoledEnabled)
  }

  @Test
  fun `scegliere un tema senza nero non lo accende`() {
    val next = FluidAppearance.withThemeMode(base, ThemeMode.DARK)
    assertEquals(ThemeMode.DARK, next.themeMode)
    assertFalse(next.amoledEnabled)
  }

  @Test
  fun `chiedere AMOLED vuol dire scuro e nero`() {
    val next = FluidAppearance.withThemeMode(base, ThemeMode.AMOLED)
    assertEquals(ThemeMode.DARK, next.themeMode)
    assertTrue(next.amoledEnabled)
  }

  @Test
  fun `spegnere il nero da AMOLED porta a scuro e non lascia un interruttore spento sopra il nero`() {
    val next = FluidAppearance.withPureBlack(base.copy(themeMode = ThemeMode.AMOLED), on = false)
    assertEquals(ThemeMode.DARK, next.themeMode)
    assertFalse(next.amoledEnabled)
    assertFalse(FluidAppearance.isPureBlack(next))
  }

  @Test
  fun `accendere il nero lascia il tema com'e'`() {
    val next = FluidAppearance.withPureBlack(base.copy(themeMode = ThemeMode.LIGHT), on = true)
    assertEquals(ThemeMode.LIGHT, next.themeMode)
    assertTrue(next.amoledEnabled)
  }

  @Test
  fun `il preset e' quello che il tema usera' davvero, a meno delle maiuscole, e se manca il primo`() {
    assertEquals("jade", FluidAppearance.presetName("JADE"))
    assertEquals("fluid", FluidAppearance.presetName("non-esiste"))
    assertEquals("fluid", FluidAppearance.presetName(""))
    val own = listOf(AccentPreset("rosa", "Rosa", Color.Red, Color.Red))
    assertEquals("rosa", FluidAppearance.presetName("jade", own))
  }

  @Test
  fun `con una lista di preset vuota si ricade su quella dell'engine, come fa il tema`() {
    assertEquals("jade", FluidAppearance.presetName("jade", emptyList()))
    assertEquals("fluid", FluidAppearance.presetName("sparito", emptyList()))
  }

  @Test
  fun `il marchio e' il pallino acceso quando l'accento e' BRAND`() {
    assertEquals(FluidAccentChoice.Brand, FluidAppearance.accentChoice(base, dynamicSupported = true))
  }

  @Test
  fun `DYNAMIC senza telefono che lo offra, o spento, e' il marchio`() {
    val dynamic = base.copy(accentMode = AccentMode.DYNAMIC)
    assertEquals(FluidAccentChoice.Dynamic, FluidAppearance.accentChoice(dynamic, dynamicSupported = true))
    assertEquals(FluidAccentChoice.Brand, FluidAppearance.accentChoice(dynamic, dynamicSupported = false))
    assertEquals(
      FluidAccentChoice.Brand,
      FluidAppearance.accentChoice(dynamic.copy(dynamicColorEnabled = false), dynamicSupported = true),
    )
  }

  @Test
  fun `un preset salvato con un nome che non c'e' piu' accende il primo, quello che il tema usa`() {
    val custom = base.copy(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = "sparito")
    assertEquals(FluidAccentChoice.Preset("fluid"), FluidAppearance.accentChoice(custom, dynamicSupported = true))
    val ember = custom.copy(customAccentName = "EMBER")
    assertEquals(FluidAccentChoice.Preset("ember"), FluidAppearance.accentChoice(ember, dynamicSupported = true))
  }

  @Test
  fun `scegliere i colori di sistema riaccende il colore dinamico`() {
    val off = base.copy(dynamicColorEnabled = false)
    val next = FluidAppearance.withAccent(off, FluidAccentChoice.Dynamic)
    assertEquals(AccentMode.DYNAMIC, next.accentMode)
    assertTrue(next.dynamicColorEnabled)
  }

  @Test
  fun `scegliere un preset o il marchio cambia solo l'accento`() {
    val preset = FluidAppearance.withAccent(base, FluidAccentChoice.Preset("ocean"))
    assertEquals(AccentMode.CUSTOM_PRESET, preset.accentMode)
    assertEquals("ocean", preset.customAccentName)
    assertEquals(base.themeMode, preset.themeMode)
    assertEquals(base.hapticsEnabled, preset.hapticsEnabled)
    val brand = FluidAppearance.withAccent(preset, FluidAccentChoice.Brand)
    assertEquals(AccentMode.BRAND, brand.accentMode)
    assertEquals("ocean", brand.customAccentName)
  }

  @Test
  fun `la scelta e la sua traduzione si rincorrono`() {
    val choices = listOf(FluidAccentChoice.Brand, FluidAccentChoice.Dynamic) +
      fluidAccentPresets.map { FluidAccentChoice.Preset(it.name) }
    choices.forEach { choice ->
      val settings = FluidAppearance.withAccent(base, choice)
      assertEquals(choice, FluidAppearance.accentChoice(settings, dynamicSupported = true))
    }
  }

  @Test
  fun `il nome del colore e' quello del marchio, dei colori di sistema o del preset`() {
    assertEquals("Classeviva", FluidAppearance.accentLabel(FluidAccentChoice.Brand, "Classeviva", "Sistema"))
    assertEquals("Sistema", FluidAppearance.accentLabel(FluidAccentChoice.Dynamic, "Classeviva", "Sistema"))
    assertEquals("Verde", FluidAppearance.accentLabel(FluidAccentChoice.Preset("jade"), "Classeviva", "Sistema"))
    assertEquals("x", FluidAppearance.accentLabel(FluidAccentChoice.Preset("x"), "Classeviva", "Sistema"))
  }

  @Test
  fun `la prova dell'aptica parte solo quando passa da spenta ad accesa`() {
    assertFalse("il primo valore letto non conta", FluidAppearance.hapticsPreview(previous = null, now = true))
    assertFalse(FluidAppearance.hapticsPreview(previous = null, now = false))
    assertTrue(FluidAppearance.hapticsPreview(previous = false, now = true))
    assertFalse(FluidAppearance.hapticsPreview(previous = true, now = false))
    assertFalse(FluidAppearance.hapticsPreview(previous = true, now = true))
  }

  @Test
  fun `le parole di tema non hanno una quarta voce per AMOLED`() {
    val strings = FluidAppearanceStrings()
    assertEquals(strings.themeDark, strings.themeLabel(ThemeMode.AMOLED))
    assertEquals(strings.themeSystem, strings.themeLabel(ThemeMode.SYSTEM))
    assertEquals(strings.themeLight, strings.themeLabel(ThemeMode.LIGHT))
  }

  @Test
  fun `il gruppo degli interruttori ha solo le righe che l'app vuole`() {
    assertEquals(
      listOf(FluidAppearanceSwitch.Glass, FluidAppearanceSwitch.Haptics),
      FluidAppearance.switchRows(glassRow = true, haptics = true),
    )
    assertEquals(listOf(FluidAppearanceSwitch.Haptics), FluidAppearance.switchRows(glassRow = false, haptics = true))
    assertEquals(listOf(FluidAppearanceSwitch.Glass), FluidAppearance.switchRows(glassRow = true, haptics = false))
    assertTrue("senza righe il gruppo non si disegna", FluidAppearance.switchRows(glassRow = false, haptics = false).isEmpty())
  }
}
