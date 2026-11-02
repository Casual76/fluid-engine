package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithText
import dev.antigravity.fluidengine.foundation.AccentMode
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.fluidAppearanceSection
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEngine
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * L'aptica come la da' il tema vero: **una sola istanza**, che legge l'interruttore a ogni evento
 * (`rememberAndroidFluidHaptics`), e con l'impostazione spenta non suona niente. Cambiare istanza a ogni
 * valore non sarebbe lo stesso modello: il tocco che accende l'aptica suona ancora quando l'interruttore
 * e' falso, ed e' per questo che serve il colpo di prova.
 */
private class AppearanceHaptics(
  private val enabled: () -> Boolean,
  private val sink: MutableList<FluidHapticEvent>,
) : FluidHaptics {
  override val engine: FluidHapticEngine = FluidHapticEngine.Composition
  override fun play(event: FluidHapticEvent) {
    if (enabled()) sink += event
  }
}

/**
 * La sezione "Aspetto" in una lista vera, con un `EngineSettings` che si aggiorna come in un'app.
 *
 * Lo schermo e' alto abbastanza da tenere tutta la sezione composta (una `LazyColumn` compone solo
 * quello che si vede, e qui interessa la sezione intera, non lo scorrimento).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h2400dp-xhdpi")
class FluidAppearanceSectionTest {

  @get:Rule
  val compose = createComposeRule()

  private val played = mutableListOf<FluidHapticEvent>()
  private var settings by mutableStateOf(EngineSettings())
  private var changes = 0
  private var glass by mutableStateOf(true)

  @Composable
  private fun Section(withGlass: Boolean = false) {
    // Il tema segue le impostazioni, come in un'app: serve a provare anche che l'aptica nuova arrivi.
    FluidTheme(settings = settings, systemBars = false) {
      val haptics = remember { AppearanceHaptics({ settings.hapticsEnabled }, played) }
      CompositionLocalProvider(LocalFluidHaptics provides haptics) {
        LazyColumn {
          fluidAppearanceSection(
            settings = settings,
            onSettingsChange = {
              changes++
              settings = it
            },
            glassEnabled = if (withGlass) glass else null,
            onGlassEnabledChange = if (withGlass) ({ glass = it }) else null,
            // Il pallino dei colori di sistema chiede il telefono: qui niente, si prova la sezione.
            dynamicAccent = false,
          )
        }
      }
    }
  }

  @Test
  fun `tema, accento e aptica partono dal valore delle impostazioni`() {
    compose.setContent { Section() }
    compose.onNodeWithText("Sistema").assertIsSelected()
    compose.onNodeWithText("Nero assoluto").assertIsDisplayed()
    // Il marchio e' l'accento di serie: il suo pallino e' acceso e il suo nome sta sotto "Accento".
    compose.onNodeWithContentDescription("Fluid").assertIsSelected()
    compose.onNodeWithText("Accento").assertIsDisplayed()
    compose.onNodeWithText("Aptica").assertIsDisplayed()
  }

  @Test
  fun `scegliere Scuro cambia il tema e solo il tema`() {
    compose.setContent { Section() }
    compose.onNodeWithText("Scuro").performClick()
    assertEquals(ThemeMode.DARK, settings.themeMode)
    assertFalse(settings.amoledEnabled)
    assertEquals(1, changes)
    compose.onNodeWithText("Scuro").assertIsSelected()
  }

  @Test
  fun `toccare la voce gia' scelta non scrive niente`() {
    compose.setContent { Section() }
    compose.onNodeWithText("Sistema").performClick()
    assertEquals("nessuna modifica, nessuna scrittura", 0, changes)
    assertEquals(EngineSettings(), settings)
  }

  @Test
  fun `il nero assoluto non risponde nel tema chiaro e risponde nello scuro`() {
    settings = EngineSettings(themeMode = ThemeMode.LIGHT)
    compose.setContent { Section() }
    compose.onNodeWithText("Nero assoluto").assertIsNotEnabled().performClick()
    assertEquals(0, changes)

    compose.onNodeWithText("Scuro").performClick()
    compose.onNodeWithText("Nero assoluto").performClick()
    assertTrue(settings.amoledEnabled)
    assertEquals(ThemeMode.DARK, settings.themeMode)
  }

  @Test
  fun `un tema AMOLED salvato si legge come Scuro con il nero acceso`() {
    settings = EngineSettings(themeMode = ThemeMode.AMOLED)
    compose.setContent { Section() }
    compose.onNodeWithText("Scuro").assertIsSelected()
    compose.onNodeWithText("Nero assoluto").assertIsOn()

    compose.onNodeWithText("Nero assoluto").performClick()
    assertEquals(ThemeMode.DARK, settings.themeMode)
    assertFalse(settings.amoledEnabled)
    compose.onNodeWithText("Nero assoluto").assertIsOff()
  }

  @Test
  fun `scegliere un preset scrive l'accento personalizzato`() {
    compose.setContent { Section() }
    // La fila porta il nome del marchio e dei preset come descrizione dei pallini.
    compose.onNodeWithContentDescription("Verde").performClick()
    assertEquals(AccentMode.CUSTOM_PRESET, settings.accentMode)
    assertEquals("jade", settings.customAccentName)
    compose.onNodeWithContentDescription("Verde").assertIsSelected()
    // E il nome del colore scelto sta nella riga sopra i pallini.
    assertTrue(compose.onAllNodesWithText("Verde").fetchSemanticsNodes().isNotEmpty())
  }

  @Test
  fun `la riga del vetro compare solo se l'app la porta, e scrive nel suo callback`() {
    compose.setContent { Section(withGlass = true) }
    compose.onNodeWithText("Vetro").assertIsDisplayed()
    compose.onNodeWithText("Vetro").assertIsOn()
    compose.onNodeWithText("Vetro").performClick()
    assertFalse(glass)
    assertEquals("il vetro non e' un campo di EngineSettings", 0, changes)
  }

  @Test
  fun `senza il vetro la riga non c'e'`() {
    compose.setContent { Section(withGlass = false) }
    assertTrue(compose.onAllNodesWithText("Vetro").fetchSemanticsNodes().isEmpty())
  }

  @Test
  fun `accendere l'aptica fa sentire subito un colpo, spegnerla no`() {
    settings = EngineSettings(hapticsEnabled = false)
    compose.setContent { Section() }
    compose.waitForIdle()
    played.clear()

    compose.onNode(hasText("Aptica")).performClick()
    compose.waitForIdle()
    assertTrue(settings.hapticsEnabled)
    assertTrue(
      "il colpo di prova arriva quando l'impostazione e' vera: $played",
      FluidHapticEvent.ToggleOn in played,
    )

    played.clear()
    compose.onNode(hasText("Aptica")).performClick()
    compose.waitForIdle()
    assertFalse(settings.hapticsEnabled)
    assertFalse(FluidHapticEvent.ToggleOn in played)
  }
}
