package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.currentGlassBackdrop
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuButton
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuHost
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuState
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidMorphMenuState
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.page.FluidPageHost
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
 * Il back col menu che si trasforma aperto: **prima il menu, poi la sottopagina, poi la pagina.**
 *
 * Sul dispatcher di navigationevent risponde il gestore abilitato registrato per ultimo, e
 * "registrato" vuol dire entrato in composizione: `enabled` non sposta un gestore nella pila. L'host
 * del menu sta alla radice ed e' composto all'avvio. Un `FluidPageHost` annidato (le sottopagine delle
 * impostazioni) o il `BackHandler` di una pagina si compongono dopo, quando la pagina si apre. Con il
 * gestore del menu sempre composto (`BackHandler(enabled = isOpen)`, fino alla 3.0.5) quelli stavano
 * sopra di lui: il back toglieva la sottopagina, o chiedeva all'editor se uscire, col menu ancora
 * aperto. Ora il gestore del menu entra in composizione con l'apertura, come quello dei modali.
 *
 * L'albero e' quello documentato: le pagine in un `FluidNavHost`, l'host del menu alla radice dopo di
 * loro. Il back e' quello dei 3 tasti (o di Android 13): un "indietro" completato al dispatcher
 * dell'Activity. Lo stato si legge subito dopo il back, prima di ogni fotogramma, per sapere quale
 * gestore ha risposto (il tasto che esce con la sua pagina chiuderebbe il menu da se' qualche
 * fotogramma dopo, e nasconderebbe l'ordine sbagliato); poi a riposo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidMorphMenuBackOrderTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var nav: NavHostController
  private lateinit var morph: FluidMorphMenuState

  /** La sottopagina dell'host annidato nella pagina "impostazioni". */
  private var detailOpen by mutableStateOf(false)

  /** Quante volte ha chiesto l'editor della pagina "bozza". */
  private var draftAsked = 0

  /** Le scelte fatte dal menu: un back non ne fa mai. */
  private var picks = 0

  /** Lo stato subito dopo un back, prima di ogni fotogramma. */
  private data class AfterBack(val menuOpen: Boolean, val detailOpen: Boolean, val draftAsked: Int)

  private fun show() {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val controller = rememberFluidNavController()
        nav = controller
        morph = rememberFluidMorphMenuState()
        val backdrop = currentGlassBackdrop()
        Box(Modifier.fillMaxSize()) {
          FluidNavHost(navController = controller, startDestination = "casa") {
            composable("casa") { Text("Casa") }
            composable("semplice") {
              Box(Modifier.fillMaxSize()) {
                Text("Semplice")
                MenuButton(backdrop, "menu-semplice")
              }
            }
            composable("impostazioni") {
              val stack = if (detailOpen) listOf("elenco", "dettaglio") else listOf("elenco")
              FluidPageHost(stack = stack, onBack = { detailOpen = false }) { key ->
                Box(Modifier.fillMaxSize()) {
                  if (key == "elenco") {
                    Text("Elenco")
                  } else {
                    Text("Dettaglio")
                    MenuButton(backdrop, "menu-dettaglio")
                  }
                }
              }
            }
            composable("bozza") {
              // L'editor con modifiche da perdere (la pagina "Bozza" del sample): chiede prima di uscire.
              BackHandler { draftAsked++ }
              Box(Modifier.fillMaxSize()) {
                Text("Bozza")
                MenuButton(backdrop, "menu-bozza")
              }
            }
          }
          // Alla radice, dopo le pagine: come in GalleryActivity e nelle app.
          FluidMorphMenuHost(state = morph, backdrop = backdrop)
        }
      }
    }
    compose.waitForIdle()
  }

  @Composable
  private fun BoxScope.MenuButton(backdrop: GlassBackdropState, tag: String) {
    FluidMorphMenuButton(
      state = morph,
      actions = { listOf(FluidContextAction(label = "Uno", onClick = { picks++ })) },
      onClick = {},
      modifier = Modifier.align(Alignment.Center).testTag(tag),
      text = "Menu",
      backdrop = backdrop,
      opensOnTap = true,
    )
  }

  private fun open(route: String, mark: String) {
    compose.runOnIdle { nav.navigate(route) }
    compose.waitForIdle()
    compose.onNodeWithText(mark).assertExists()
  }

  private fun openMenu(tag: String) {
    compose.onNodeWithTag(tag).performClick()
    compose.waitForIdle()
    assertTrue("il menu non si e' aperto", morph.isOpen)
    compose.onNodeWithText("Uno").assertExists()
  }

  private fun back(): AfterBack {
    val after = compose.runOnUiThread {
      compose.activity.onBackPressedDispatcher.onBackPressed()
      AfterBack(menuOpen = morph.isOpen, detailOpen = detailOpen, draftAsked = draftAsked)
    }
    compose.waitForIdle()
    return after
  }

  private fun route(): String? = compose.runOnIdle { nav.currentBackStackEntry?.destination?.route }

  // --- i casi che con la 3.0.5 cadevano ---------------------------------------------------------

  @Test
  fun `il back chiude prima il menu, poi la sottopagina dell'host annidato, poi la pagina`() {
    show()
    open("impostazioni", "Elenco")
    compose.runOnIdle { detailOpen = true }
    compose.waitForIdle()
    compose.onNodeWithText("Dettaglio").assertExists()
    openMenu("menu-dettaglio")

    val first = back()
    assertFalse("il primo back e' del menu, non dell'host annidato: $first", first.menuOpen)
    assertTrue("il primo back lascia la sottopagina: $first", first.detailOpen)
    assertFalse("il ritorno e' finito", morph.isOnScreen)
    assertTrue(detailOpen)
    assertEquals("impostazioni", route())

    // Riaperto, il menu torna davanti: il gestore si registra di nuovo a ogni apertura.
    openMenu("menu-dettaglio")
    val again = back()
    assertFalse("riaperto, il back e' ancora del menu: $again", again.menuOpen)
    assertTrue(again.detailOpen)

    val second = back()
    assertFalse("il secondo back chiude la sottopagina: $second", second.detailOpen)
    assertEquals("impostazioni", route())

    back()
    assertEquals("casa", route())
    assertEquals("un back non sceglie niente", 0, picks)
  }

  @Test
  fun `il back chiude il menu prima di chiedere all'editor, e l'editor chiede al back dopo`() {
    show()
    open("bozza", "Bozza")
    openMenu("menu-bozza")

    val first = back()
    assertFalse("il primo back e' del menu: $first", first.menuOpen)
    assertEquals("l'editor non ha ancora chiesto: $first", 0, first.draftAsked)

    val second = back()
    assertEquals("il back dopo e' dell'editor: $second", 1, second.draftAsked)
    assertEquals("e l'editor decide, la pagina resta", "bozza", route())
    assertEquals(0, picks)
  }

  // --- il controllo: questo andava gia' ----------------------------------------------------------

  /**
   * Una pagina del `FluidNavHost` senza gestori suoi: l'host delle pagine e' composto all'avvio, prima
   * di quello del menu, quindi stava gia' sotto. Passa con e senza la correzione: dice che il guasto
   * era l'ordine di registrazione, non il congedo del menu.
   */
  @Test
  fun `su una pagina senza host annidato il back chiude il menu e poi la pagina`() {
    show()
    open("semplice", "Semplice")
    openMenu("menu-semplice")

    val first = back()
    assertFalse("il primo back e' del menu: $first", first.menuOpen)
    assertEquals("semplice", route())

    back()
    assertEquals("casa", route())
    assertEquals(0, picks)
  }
}
