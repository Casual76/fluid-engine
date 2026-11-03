package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.navigation.NavHostController
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.page.FluidPageHostState
import dev.antigravity.fluidengine.ui.page.rememberFluidPageHostState
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
 * Il back a dito dentro l'app passa dai `BackHandler` (3.0).
 *
 * Il back di sistema passava gia' dal dispatcher: un `BackHandler` registrato dopo l'host lo
 * intercettava. Quello a dito no: `FluidPageHostState.commit` chiamava `onBack()`, cioe'
 * `navController.popBackStack()`, e un editor con modifiche da confermare veniva tolto da sotto il
 * dito lo stesso (Pampa notes se n'era difesa con `FluidBackSwipe.Off`).
 *
 * La scelta: **il dito segue la pagina come sempre, e se supera la soglia la sua conferma e' un
 * "indietro" mandato al dispatcher**, cioe' esattamente quello che il sistema manda a fine gesto. Il
 * gestore che risponde per primo — l'ultimo registrato e attivo — decide:
 *
 *  * se e' un `BackHandler` della pagina (o un modale, una ricerca aperta), la pagina **non esce**:
 *    torna sotto il dito con la sua molla e il gestore gira, a chiedere conferma o a togliere la
 *    pagina da se';
 *  * se non ce n'e', risponde l'host e la pagina esce come prima.
 *
 * Sotto la soglia nessuno viene chiamato: il gesto e' annullato, non confermato. Schermo 360 dp a
 * densita' 2: W = 720 px, soglia del 35% a 252 px. La pagina e' una `FluidScreen`, come in
 * `FluidPageHostTest`; i test vanno sull'host vero, con un `BackHandler` vero.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidPageBackHandlerTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var nav: NavHostController
  private lateinit var pages: FluidPageHostState

  /** Quante volte ha girato il gestore della pagina B. */
  private var handled = 0

  /** Quante volte ha girato il gestore predittivo della pagina P. */
  private var predictiveHandled = 0

  /** Quante volte ha girato un gestore registrato *prima* dell'host. */
  private var outerHandled = 0

  private var enabled by mutableStateOf(true)
  private var confirming by mutableStateOf(false)

  /** Cosa fa il gestore della pagina B quando gira. */
  private var onHandled: () -> Unit = {}

  private fun show(outerHandler: Boolean = false) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val controller = rememberFluidNavController()
        nav = controller
        val state = rememberFluidPageHostState()
        pages = state
        // Registrato prima dell'host: sta *sotto* di lui nella pila dei gestori.
        if (outerHandler) BackHandler { outerHandled++ }
        FluidNavHost(navController = controller, startDestination = "a", state = state) {
          composable("a") {
            FluidScreen(title = "Radice") {
              item(key = "open") { Text("Apri B") }
            }
          }
          composable("b") {
            BackHandler(enabled = enabled) {
              handled++
              onHandled()
            }
            FluidScreen(title = "Pagina B", onBack = { nav.popBackStack() }) {
              item(key = "mark") { Text("Marca B") }
              if (confirming) item(key = "confirm") { Text("Uscire senza salvare?") }
            }
          }
          composable("p") {
            PredictiveBackHandler(enabled = enabled) { progress ->
              progress.collect { }
              predictiveHandled++
            }
            FluidScreen(title = "Pagina P", onBack = { nav.popBackStack() }) {
              item(key = "mark") { Text("Marca P") }
            }
          }
        }
      }
    }
    compose.waitForIdle()
  }

  private fun open(route: String, mark: String) {
    compose.runOnIdle { nav.navigate(route) }
    compose.waitForIdle()
    compose.onNodeWithText(mark).assertExists()
  }

  private fun route(): String? = compose.runOnIdle { nav.currentBackStackEntry?.destination?.route }

  private fun leftPx(text: String): Float =
    with(compose.density) { compose.onNodeWithText(text).getUnclippedBoundsInRoot().left.toPx() }

  private fun isShown(text: String): Boolean = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

  /** Un trascinamento lento verso destra dal bordo (di serie l'unico punto da cui si tira): [px] in tutto, poi il dito si alza. */
  private fun drag(px: Float, lift: Boolean = true) {
    val steps = (px / 4f).toInt()
    compose.onRoot().performTouchInput {
      down(Offset(6f, 900f))
      repeat(steps) { moveBy(Offset(4f, 0f), delayMillis = 16L) }
      if (lift) up()
    }
    compose.waitForIdle()
  }

  // --- un BackHandler attivo ----------------------------------------------------------------------

  @Test
  fun `con un BackHandler attivo il dito oltre la soglia non toglie la pagina e chiama il gestore`() {
    show()
    open("b", "Marca B")
    val rest = leftPx("Marca B")

    // 0,40 W lento: senza gestore e' un back confermato (vedi `FluidPageHostTest`).
    drag(288f)

    assertEquals("il gestore ha girato una volta", 1, handled)
    assertEquals("la pagina e' ancora B", "b", route())
    assertTrue("la pagina B e' ancora in scena", isShown("Marca B"))
    assertEquals("la pagina e' tornata al suo posto", 0f, leftPx("Marca B") - rest, 0.5f)
  }

  @Test
  fun `la pagina torna sotto il dito con la molla invece di uscire e rientrare`() {
    show()
    open("b", "Marca B")
    val rest = leftPx("Marca B")
    compose.mainClock.autoAdvance = false
    compose.onRoot().performTouchInput {
      down(Offset(6f, 900f))
      repeat(72) { moveBy(Offset(4f, 0f), delayMillis = 16L) }
      up()
    }
    // Da qui la pagina non deve mai superare il punto dove l'ha lasciata il dito (288 px, piu' un
    // sorpasso della molla): l'uscita e il ritorno di un back confermato porterebbero a 720.
    var farthest = 0f
    repeat(120) {
      compose.mainClock.advanceTimeByFrame()
      if (isShown("Marca B")) farthest = maxOf(farthest, leftPx("Marca B") - rest)
    }
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()

    assertTrue("la pagina e' arrivata a $farthest px: e' uscita e rientrata", farthest < 400f)
    assertEquals(1, handled)
    assertEquals("b", route())
    assertEquals(0f, leftPx("Marca B") - rest, 0.5f)
  }

  @Test
  fun `sotto la soglia il gestore non gira, e' un gesto annullato`() {
    show()
    open("b", "Marca B")
    val rest = leftPx("Marca B")

    // 0,30 W lento: sotto il 35%, il rilascio annulla.
    drag(216f)

    assertEquals("un gesto annullato non e' un back", 0, handled)
    assertEquals("b", route())
    assertEquals(0f, leftPx("Marca B") - rest, 0.5f)
  }

  @Test
  fun `un BackHandler che toglie la pagina da se' la fa uscire e la pila si accorcia una volta sola`() {
    show()
    open("b", "Marca B")
    onHandled = { nav.popBackStack() }

    // "Salva e chiude": il gestore decide, e la pagina esce con la sua uscita.
    drag(288f)

    assertEquals(1, handled)
    assertEquals("la pagina e' stata tolta dal gestore", "a", route())
    assertTrue(!isShown("Marca B"))
    assertEquals(
      "una pila di due pagine ne perde una sola",
      listOf("a"),
      compose.runOnIdle { nav.currentBackStack.value.mapNotNull { it.destination.route } },
    )
  }

  @Test
  fun `un BackHandler che chiede conferma riporta la pagina a posto, e confermare la toglie`() {
    show()
    open("b", "Marca B")
    val rest = leftPx("Marca B")
    onHandled = { confirming = true }

    drag(288f)

    assertEquals(1, handled)
    assertEquals("b", route())
    assertTrue("la conferma si vede", isShown("Uscire senza salvare?"))
    assertEquals(0f, leftPx("Marca B") - rest, 0.5f)

    // L'utente conferma: la pagina esce da fuori, e il resto e' il pop di sempre.
    compose.runOnUiThread { nav.popBackStack() }
    compose.waitForIdle()
    assertEquals("a", route())
    assertTrue(!isShown("Marca B"))
  }

  @Test
  fun `un PredictiveBackHandler attivo risponde al dito come un BackHandler`() {
    show()
    open("p", "Marca P")
    val rest = leftPx("Marca P")

    drag(288f)

    assertEquals(1, predictiveHandled)
    assertEquals("p", route())
    assertEquals(0f, leftPx("Marca P") - rest, 0.5f)
  }

  // --- un BackHandler spento, o registrato prima dell'host --------------------------------------

  @Test
  fun `un BackHandler spento non cambia niente e il dito toglie la pagina come prima`() {
    show()
    enabled = false
    open("b", "Marca B")

    drag(288f)

    assertEquals("un gestore spento non e' interpellato", 0, handled)
    assertEquals("a", route())
    assertTrue(!isShown("Marca B"))
  }

  @Test
  fun `un gestore registrato prima dell'host sta sotto di lui e il dito toglie la pagina`() {
    show(outerHandler = true)
    open("b", "Marca B")
    enabled = false
    compose.waitForIdle()

    drag(288f)

    assertEquals("il gestore dell'esterno non decide cosa fa il back di una pagina interna", 0, outerHandled)
    assertEquals("a", route())
  }

  @Test
  fun `un gestore attivo passa anche una spinta corta e veloce`() {
    show()
    open("b", "Marca B")
    val rest = leftPx("Marca B")

    // 72 px a 2000 px/s: sotto il 35%, ma oltre la spinta (vedi `FluidPageHostTest`): e' una conferma.
    compose.onRoot().performTouchInput {
      down(Offset(6f, 900f))
      repeat(9) { moveBy(Offset(8f, 0f), delayMillis = 4L) }
      up()
    }
    compose.waitForIdle()

    assertEquals(1, handled)
    assertEquals("b", route())
    assertEquals(0f, leftPx("Marca B") - rest, 0.5f)
  }

  // --- il back di sistema e il tasto, che non sono cambiati ---------------------------------------

  @Test
  fun `il back di sistema con un BackHandler attivo chiama il gestore e non toglie la pagina`() {
    show()
    open("b", "Marca B")

    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.waitForIdle()

    assertEquals(1, handled)
    assertEquals("b", route())
  }

  @Test
  fun `il back di sistema senza gestori toglie la pagina`() {
    show()
    enabled = false
    open("b", "Marca B")

    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.waitForIdle()

    assertEquals(0, handled)
    assertEquals("a", route())
  }

  @Test
  fun `dopo un gestore che ha consumato il dito, un gesto senza gestore toglie la pagina`() {
    show()
    open("b", "Marca B")

    drag(288f)
    assertEquals(1, handled)
    assertEquals("b", route())

    // Nessuno stato rimasto in sospeso dal primo gesto: spento il gestore, il secondo toglie la pagina.
    enabled = false
    compose.waitForIdle()
    drag(288f)
    assertEquals("il gestore spento non e' interpellato", 1, handled)
    assertEquals("a", route())
    assertTrue(!isShown("Marca B"))
  }
}
