package dev.antigravity.fluidengine.sample

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidSystemContrast
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.LocalFluidSystemContrast
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Il contrasto del sistema, dal telefono al vetro (3.0): `FluidTheme` lo legge da `UiModeManager`, lo
 * mette in `LocalFluidSystemContrast`, lo segue quando l'utente lo cambia con l'app aperta, e le
 * pellicole di `GlassDefaults` si fanno piu' dense.
 *
 * La mappa da livello a densita' e' pura e sta in `FluidSystemContrastTest` (engine-ui). Qui c'e'
 * quello che una funzione pura non puo' dire: che il valore arriva, e che il cambio lo ricompone.
 * `UiModeManager.getContrast()` esiste da Android 14: sotto, il tema vale sempre standard.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidSystemContrastObserveTest {

  @get:Rule
  val compose = createComposeRule()

  private fun uiMode(): UiModeManager =
    ApplicationProvider.getApplicationContext<Context>().getSystemService(UiModeManager::class.java)

  private fun requireApi34() = assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)

  @Test
  fun `il tema legge il contrasto che il sistema ha all'avvio`() {
    requireApi34()
    shadowOf(uiMode()).setContrast(1f)
    var seen = -1f
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) { seen = LocalFluidSystemContrast.current }
    }
    compose.waitForIdle()
    assertEquals(FluidSystemContrast.High, seen, 0f)
  }

  @Test
  fun `senza contrasto richiesto il tema vale standard`() {
    var seen = -1f
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) { seen = LocalFluidSystemContrast.current }
    }
    compose.waitForIdle()
    assertEquals(FluidSystemContrast.Standard, seen, 0f)
  }

  @Test
  fun `quando l'utente cambia il contrasto con l'app aperta il tema lo segue`() {
    requireApi34()
    shadowOf(uiMode()).setContrast(0f)
    var seen = -1f
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) { seen = LocalFluidSystemContrast.current }
    }
    compose.waitForIdle()
    assertEquals(FluidSystemContrast.Standard, seen, 0f)

    compose.runOnUiThread { shadowOf(uiMode()).setContrast(0.5f) }
    compose.waitForIdle()
    assertEquals("medio", FluidSystemContrast.Medium, seen, 0f)

    compose.runOnUiThread { shadowOf(uiMode()).setContrast(1f) }
    compose.waitForIdle()
    assertEquals("alto", FluidSystemContrast.High, seen, 0f)

    compose.runOnUiThread { shadowOf(uiMode()).setContrast(0f) }
    compose.waitForIdle()
    assertEquals("di nuovo standard", FluidSystemContrast.Standard, seen, 0f)
  }

  @Test
  fun `le pellicole di GlassDefaults si fanno piu' dense con il contrasto, tutte`() {
    // Il livello si forza dentro il tema, come fa la galleria: stesso percorso di quello del sistema.
    val alphas = mutableMapOf<String, MutableList<Float>>()
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        listOf(FluidSystemContrast.Standard, FluidSystemContrast.Medium, FluidSystemContrast.High).forEach { level ->
          CompositionLocalProvider(LocalFluidSystemContrast provides level) {
            fun record(name: String, alpha: Float) = alphas.getOrPut(name) { mutableListOf() }.add(alpha)
            record("barTint", GlassDefaults.barTint().overlay.alpha)
            record("floatingTint", GlassDefaults.floatingTint().overlay.alpha)
            record("controlTint", GlassDefaults.controlTint().overlay.alpha)
            record("contentTint", GlassDefaults.contentTint().overlay.alpha)
            record("modalTint", GlassDefaults.modalTint().overlay.alpha)
            record("panelTint", GlassDefaults.panelTint().overlay.alpha)
            record("stackedContentTint", GlassDefaults.stackedContentTint().overlay.alpha)
            record("barTintOnPhoto", GlassDefaults.barTintOnPhoto().overlay.alpha)
            record("floatingTintOnPhoto", GlassDefaults.floatingTintOnPhoto().overlay.alpha)
            record("tintedModalTint", GlassDefaults.tintedModalTint(androidx.compose.ui.graphics.Color(0xFF3366CC)).overlay.alpha)
          }
        }
      }
    }
    compose.waitForIdle()
    assertEquals(10, alphas.size)
    alphas.forEach { (name, values) ->
      assertEquals("$name: tre livelli", 3, values.size)
      assertTrue("$name: $values non sale con il contrasto", values[0] < values[1] && values[1] < values[2])
      assertTrue("$name: ad alto contrasto ${values[2]} e' un muro", values[2] < 1f)
    }
  }

  @Test
  fun `la tinta di un modale colorato sale una volta sola, non due`() {
    // `tintedModalTint` riparte dalla pellicola del modale: se il contrasto si applicasse a monte e a
    // valle il ripiego e il bordo salirebbero due volte.
    var standard: Float? = null
    var high: Float? = null
    var highFallback: Float? = null
    var standardFallback: Float? = null
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val colour = androidx.compose.ui.graphics.Color(0xFF3366CC)
        CompositionLocalProvider(LocalFluidSystemContrast provides FluidSystemContrast.Standard) {
          standard = GlassDefaults.tintedModalTint(colour).overlay.alpha
          standardFallback = GlassDefaults.tintedModalTint(colour).fallback.alpha
        }
        CompositionLocalProvider(LocalFluidSystemContrast provides FluidSystemContrast.High) {
          high = GlassDefaults.tintedModalTint(colour).overlay.alpha
          highFallback = GlassDefaults.tintedModalTint(colour).fallback.alpha
        }
      }
    }
    compose.waitForIdle()
    val a = standard!!
    val f = standardFallback!!
    // Un 255-esimo di opacita' per ogni passaggio dal colore: l'uguaglianza e' quella della formula.
    assertEquals(a + (1f - a) * FluidSystemContrast.veil(FluidSystemContrast.High), high!!, 0.01f)
    assertEquals(f + (1f - f) * FluidSystemContrast.veil(FluidSystemContrast.High), highFallback!!, 0.01f)
  }
}
