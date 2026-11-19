package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.emptyBackdrop
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * La registrazione di una schermata nella chrome quando cambia solo il suo fondale (S4c, il difetto latente indicato dopo la
 * ripresa 25): la schermata e' la stessa, e resta davanti e al suo posto nell'elezione. Fino a S4c `FluidScreen` la
 * registrava di nuovo (togliendola e rimettendola): il davanti se ne andava con la vecchia registrazione e lo rimetteva solo
 * l'effetto del davanti, che non ripartiva; e la registrazione nuova passava in fondo all'ordine.
 */
class FluidChromeRegistrationTest {

  private fun backdrop() = GlassBackdropState(emptyBackdrop(), null, 2.5.dp)

  private fun controller() = FluidChromeController(bottomBarVelocityThresholdPx = 0f, densityPx = 1f)

  @Test
  fun `il fondale nuovo di una schermata davanti resta davanti anche quando entra un'altra pagina`() {
    val chrome = controller()
    val page = backdrop()
    chrome.registerBackdrop("pagina", page)
    chrome.setBackdropFront("pagina", true)
    assertSame(page, chrome.activeBackdrop.value)

    // La striscia in basso arriva: il fondale della chrome della pagina cambia.
    val withStrip = backdrop()
    chrome.updateBackdrop("pagina", withStrip)
    assertSame(withStrip, chrome.activeBackdrop.value)

    // Una pagina che entra in scena (una transizione, una sottopagina che si prepara) e non e' ancora davanti.
    chrome.registerBackdrop("entra", backdrop())
    assertSame("la chrome rifrange ancora la pagina davanti", withStrip, chrome.activeBackdrop.value)
  }

  @Test
  fun `la registrazione di prima perdeva il davanti`() {
    // Quello che faceva `FluidScreen` fino a S4c al cambio del fondale: togliere e rimettere la registrazione.
    val chrome = controller()
    chrome.registerBackdrop("pagina", backdrop())
    chrome.setBackdropFront("pagina", true)
    val withStrip = backdrop()
    chrome.unregisterBackdrop("pagina")
    chrome.registerBackdrop("pagina", withStrip)
    val entering = backdrop()
    chrome.registerBackdrop("entra", entering)
    // Nessuno e' piu' davanti: vince l'ultima registrata, la pagina che entra.
    assertSame(entering, chrome.activeBackdrop.value)
  }

  @Test
  fun `il fondale nuovo resta al suo posto nell'ordine`() {
    val chrome = controller()
    val under = backdrop()
    val over = backdrop()
    chrome.registerBackdrop("sotto", under)
    chrome.registerBackdrop("sopra", over)
    chrome.setBackdropFront("sotto", true)
    chrome.setBackdropFront("sopra", true)
    assertSame(over, chrome.activeBackdrop.value)
    // La pagina sotto cambia fondale: non passa sopra a quella che le sta sopra.
    chrome.updateBackdrop("sotto", backdrop())
    assertSame(over, chrome.activeBackdrop.value)
    // Un fondale per una schermata mai registrata non registra niente.
    chrome.updateBackdrop("mai", backdrop())
    assertSame(over, chrome.activeBackdrop.value)
  }
}
