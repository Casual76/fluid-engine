package dev.antigravity.fluidengine.sample.account

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import dev.antigravity.fluidengine.account.PampaAppearance
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidAppearanceStrings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassPreference
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassPreference
import dev.antigravity.fluidengine.ui.fluid.fluidAppearanceSection
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F10.8c · Il cursore del vetro è uno solo, da «trasparente» a «di serie», lo stesso dello Store (scelta di Alessio del
 * 25/11/2026). La riga vera di `fluidAppearanceSection`, col tema vero: un'app che aveva salvato un valore nella metà
 * colorata di prima trova il cursore in cima, «Di serie», e la riga non riscrive il valore da sola; quando l'utente lo
 * muove, il valore nuovo arriva all'app.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w420dp-h800dp-xhdpi")
class F10p8cGlassCursorTest {
  @get:Rule val compose = createComposeRule()

  private val strings = FluidAppearanceStrings()
  private val slider = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo) and
    hasContentDescription(strings.glassPreference)

  private fun state(): Pair<ClosedFloatingPointRange<Float>, Float> {
    val config = compose.onNode(slider, useUnmergedTree = true).fetchSemanticsNode().config
    val info = config[SemanticsProperties.ProgressBarRangeInfo]
    return info.range to info.current
  }

  private fun label(): String? =
    compose.onNode(slider, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

  @Test
  fun `un valore salvato nella meta colorata di prima mette il cursore in cima, di serie, e non si riscrive`() {
    var saved by mutableStateOf(0.8f)
    val emitted = ArrayList<Float>()
    compose.setContent {
      FluidTheme(EngineSettings(), systemBars = false) {
        CompositionLocalProvider(LocalFluidGlassPreference provides saved) {
          LazyColumn(Modifier.fillMaxSize()) {
            fluidAppearanceSection(
              settings = EngineSettings(),
              onSettingsChange = {},
              title = null,
              pureBlack = false,
              accent = false,
              haptics = false,
              glassPreference = saved,
              onGlassPreferenceChange = { emitted += it; saved = it },
            )
          }
        }
      }
    }
    compose.waitForIdle()
    // Un cursore solo, da trasparente a di serie; il valore salvato si legge di serie.
    val (range, current) = state()
    assertEquals(FluidGlassPreference.Transparent, range.start, 0f)
    assertEquals(FluidGlassPreference.Default, range.endInclusive, 0f)
    assertEquals(FluidGlassPreference.Default, current, 0f)
    assertEquals(strings.glassPreferenceDefault, label())
    assertTrue("la riga non riscrive il valore salvato: $emitted", emitted.isEmpty())
    assertEquals(0.8f, saved, 0f)

    // L'utente lo porta a metà corsa: arriva il valore vero del cursore, e la riga lo dice in percentuale.
    compose.onNode(slider, useUnmergedTree = true).performSemanticsAction(SemanticsActions.SetProgress) { it(0.25f) }
    compose.waitForIdle()
    assertEquals(listOf(0.25f), emitted)
    assertEquals(0.25f, state().second, 0f)
    assertEquals("50%", label())

    // Fino in cima: il valore di serie esatto, cioè il vetro di serie al bit.
    compose.onNode(slider, useUnmergedTree = true).performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
    compose.waitForIdle()
    assertEquals(FluidGlassPreference.Default, emitted.last(), 0f)
    assertEquals(strings.glassPreferenceDefault, label())
  }

  /**
   * La chiarezza dello Store (`appearance.glass`, da 0 a 1) e il cursore dell'engine sono lo stesso cursore:
   * `PampaAppearance` non porta `engine-ui`, quindi i due conti si confrontano qui, dove ci sono tutti e due.
   */
  @Test
  fun `la chiarezza dello Store e il cursore dell'engine sono lo stesso numero`() {
    assertEquals(FluidGlassPreference.Default, PampaAppearance.GLASS_DEFAULT, 0f)
    for (step in 0..100) {
      val clarity = step / 100f
      val text = "%.2f".format(java.util.Locale.ROOT, clarity)
      assertEquals(text, FluidGlassPreference.fromClarity(clarity), PampaAppearance.glassPreferenceOf(text)!!, 1e-6f)
    }
    for (preference in listOf(0f, 0.1f, 0.25f, 0.4f, 0.5f, 0.7f, 1f, Float.NaN)) {
      assertEquals("$preference", FluidGlassPreference.clarityOf(preference), PampaAppearance.clarityOf(preference), 0f)
    }
  }
}
