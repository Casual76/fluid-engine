package dev.antigravity.fluidengine.sample.novita

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import dev.antigravity.fluidengine.sharein.FluidShareInWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F8.10 · la finestra trasparente degli arrivi non ha la fascia bianca sotto il foglio (il tablet, 25 novembre: una fascia
 * a 251 su tutta la larghezza, cioè il velo di contrasto della barra di navigazione chiara, bianco al 90%, sopra il
 * foglio a 215).
 *
 * La causa: [FluidShareInWindow.install] spegneva il velo prima che la finestra costruisse la sua decorazione, e
 * `setContent` la costruiva rileggendo dal tema `enforceNavigationBarContrast`, vero di serie. Ora la decorazione si
 * costruisce prima, e il tema degli arrivi lo dice anche lui.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class F8p10ShareInWindowTest {

  @Test fun theArrivalsWindowKeepsTheNavigationBarClearAfterItsContentIsSet() {
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Ricordati il libro di storia")
    val activity = Robolectric.buildActivity(ShareInDemoActivity::class.java, intent).setup().get()
    val window = activity.window
    assertFalse("il velo di contrasto sotto la barra di navigazione", window.isNavigationBarContrastEnforced)
    assertFalse("il velo di contrasto sotto la barra di stato", window.isStatusBarContrastEnforced)
    assertEquals("la barra di navigazione trasparente", Color.TRANSPARENT, window.navigationBarColor)
  }

  @Test fun installBeforeSetContentHoldsOnAnAppTheme() {
    // Un'Activity col tema dell'app (che il velo lo chiede, come quello di serie) e non con quello degli arrivi.
    val activity = Robolectric.buildActivity(AppThemedArrivals::class.java).setup().get()
    assertFalse("il velo di contrasto torna con setContent", activity.window.isNavigationBarContrastEnforced)
  }
}

/** Un'attivita' degli arrivi col tema dell'app: [FluidShareInWindow.install] prima di `setContent`, come chiede il KDoc. */
class AppThemedArrivals : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    setTheme(android.R.style.Theme_Material_Light_NoActionBar)
    super.onCreate(savedInstanceState)
    FluidShareInWindow.install(this)
    setContent { Box {} }
  }
}
