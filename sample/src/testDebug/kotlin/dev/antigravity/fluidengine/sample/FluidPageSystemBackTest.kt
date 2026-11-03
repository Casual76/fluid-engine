package dev.antigravity.fluidengine.sample

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.navigation.NavHostController
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Il back predittivo di sistema tiene la pagina sotto il dito anche col dito fermo (3.0.4).
 *
 * Alessio: «perfetto, ma a volte — col dito fermo, e qualche volta mentre lo muovo — l'animazione
 * scatta, come indecisa su dove sia il dito». Gli eventi qui sono quelli che un'app riceve davvero:
 * `touchX` e' l'ultimo punto del dito, e il `progress` e' passato dalla molla che il sistema fa girare
 * nell'app (`BackProgressAnimator`, rigidita' 1500, senza rimbalzo) a ogni fotogramma, anche quando il
 * dito non manda niente. Dopo un passo indietro del dito quella molla continua a scendere per qualche
 * fotogramma a dito fermo: e' lo stesso disegno di un annullo, e l'host lo leggeva come un annullo. E
 * quando il dito torna verso il bordo il sistema porta il progresso a zero col dito ancora giu'
 * (misurato sull'emulatore, API 37): stesso disegno, e la pagina tornava a casa sotto il dito.
 *
 * Il metro: fra un evento e l'altro la pagina si sposta quanto il dito (due pixel di tolleranza), e
 * mentre il dito e' giu' e fermo non si muove da sola. Schermo 360 dp a densita' 2.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidPageSystemBackTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var nav: NavHostController

  private fun showChild() {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val controller = rememberFluidNavController()
        nav = controller
        FluidNavHost(navController = controller, startDestination = "a") {
          composable("a") { FluidScreen(title = "Radice") { item(key = "a") { Text("Marca A") } } }
          composable("b") {
            FluidScreen(title = "Pagina B", onBack = { nav.popBackStack() }) { item(key = "b") { Text("Marca B") } }
          }
        }
      }
    }
    compose.waitForIdle()
    compose.runOnIdle { nav.navigate("b") }
    compose.waitForIdle()
    compose.onNodeWithText("Marca B").assertExists()
  }

  private fun route(): String? = compose.runOnIdle { nav.currentBackStackEntry?.destination?.route }

  private fun pageLeft(): Float =
    with(compose.density) { compose.onNodeWithText("Marca B").getUnclippedBoundsInRoot().left.toPx() }

  /** La molla che il sistema fa girare nell'app sul `progress`: criticamente smorzata, k = 1500. */
  private class SystemProgress(private val startX: Float, private val maxDistance: Float) {
    var value = 0f
    private var velocity = 0f
    fun step(touchX: Float, seconds: Float = 0.016f): Float {
      val target = ((touchX - startX) / maxDistance).coerceIn(0f, 1f)
      val k = 1500f
      val c = 2f * sqrt(k)
      var t = 0f
      val dt = 0.001f
      while (t < seconds) {
        val a = -k * (value - target) - c * velocity
        velocity += a * dt
        value += velocity * dt
        t += dt
      }
      value = value.coerceIn(0f, 1f)
      return value
    }
  }

  private val dispatcher get() = compose.activity.onBackPressedDispatcher

  private fun event(touchX: Float, progress: Float) =
    BackEventCompat(touchX, 900f, progress, BackEventCompat.EDGE_LEFT)

  private fun frame() = compose.mainClock.advanceTimeBy(16L)

  /** Manda [xs] (un `touchX` per fotogramma) e controlla a ogni fotogramma che la pagina si sposti quanto il dito. */
  private fun followAndCheck(xs: List<Float>, spring: SystemProgress, startLeft: Float, startX: Float) {
    var previousLeft = startLeft
    var previousX = startX
    xs.forEachIndexed { index, x ->
      compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(event(x, spring.step(x))) }
      frame()
      val left = pageLeft()
      val pageStep = left - previousLeft
      val fingerStep = x - previousX
      assertTrue(
        "fotogramma $index (dito a $x): il dito si e' mosso di $fingerStep px, la pagina di $pageStep px",
        abs(pageStep - fingerStep) <= 2f,
      )
      previousLeft = left
      previousX = x
    }
  }

  @Test
  fun `col dito fermo dopo un passo indietro la pagina resta sotto il dito`() {
    showChild()
    compose.mainClock.autoAdvance = false
    val spring = SystemProgress(startX = 4f, maxDistance = 600f)

    compose.runOnUiThread { dispatcher.dispatchOnBackStarted(event(4f, 0f)) }
    frame()
    // Il primo evento mette la pagina sotto il dito; da qui ogni passo e' misurato.
    followAndCheck((1..25).map { 4f + it * 8f }, spring, pageLeft(), 4f)

    // Un passo indietro di 12 px in tre fotogrammi, poi il dito fermo: la molla del sistema scende
    // ancora per qualche fotogramma col dito immobile.
    val back = listOf(200f, 196f, 192f)
    val still = List(30) { 192f }
    // Il tremolio di un pollice appoggiato: un pixel avanti e indietro.
    val jitter = List(20) { if (it % 2 == 0) 193f else 192f }
    // Di nuovo avanti, poi indietro piano (meno di mezzo pixel per fotogramma), poi fermo.
    val forward = (1..10).map { 192f + it * 6f }
    val slowBack = (1..40).map { 252f - it * 0.4f }
    val stillAgain = List(20) { 236f }
    followAndCheck(back + still + jitter + forward + slowBack + stillAgain, spring, pageLeft(), 204f)

    // Il dito e' giu' e fermo: per un terzo di secondo senza eventi la pagina non si muove da sola.
    val held = pageLeft()
    repeat(20) { frame() }
    assertEquals(held, pageLeft(), 0.5f)

    compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    assertEquals("b", route())
  }

  @Test
  fun `il dito che torna verso il bordo porta la pagina con se' anche quando il sistema azzera il progresso`() {
    // Misurato sull'emulatore (API 37): fuori fino a 600, poi un passo indietro a 500. Il sistema decide
    // che il back non partira' e porta il suo progresso da 0,53 a 0 in sei fotogrammi, col `touchX`
    // fermo a 500 e il dito ancora giu'; poi resta a 0 mentre il dito va a 400, 300, 200.
    showChild()
    compose.mainClock.autoAdvance = false
    val spring = SystemProgress(startX = 4f, maxDistance = 1100f)
    compose.runOnUiThread { dispatcher.dispatchOnBackStarted(event(4f, 0f)) }
    frame()
    followAndCheck((1..30).map { 4f + it * 20f }, spring, pageLeft(), 4f)
    val reversal = SystemProgress(startX = 604f, maxDistance = 1100f).also { it.value = spring.value }
    val xs = List(10) { 504f } + List(6) { 404f } + List(6) { 304f } + List(6) { 204f }
    var previousLeft = pageLeft()
    var previousX = 604f
    xs.forEachIndexed { index, x ->
      compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(event(x, reversal.step(604f))) }
      frame()
      val left = pageLeft()
      assertTrue(
        "fotogramma $index (dito a $x): il dito si e' mosso di ${x - previousX} px, la pagina di ${left - previousLeft} px",
        abs((left - previousLeft) - (x - previousX)) <= 2f,
      )
      previousLeft = left
      previousX = x
    }
    // Si alza li': il sistema annulla, e solo ora la pagina torna a casa.
    compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    assertEquals("b", route())
  }

  @Test
  fun `un annullo vero si riconosce ancora prima che il sistema lo dica`() {
    showChild()
    compose.mainClock.autoAdvance = false
    val spring = SystemProgress(startX = 4f, maxDistance = 600f)
    compose.runOnUiThread { dispatcher.dispatchOnBackStarted(event(4f, 0f)) }
    frame()
    followAndCheck((1..25).map { 4f + it * 8f }, spring, pageLeft(), 4f)
    repeat(10) {
      compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(event(204f, spring.step(204f))) }
      frame()
    }
    val lifted = pageLeft()

    // Il dito si alza sotto la soglia: il sistema porta il suo progresso a zero con la sua molla, e
    // il `touchX` resta l'ultimo punto del dito. Dopo un decimo di secondo la pagina e' gia' partita.
    repeat(6) {
      compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(event(204f, spring.step(4f))) }
      frame()
    }
    assertTrue("la pagina doveva essere partita: ${pageLeft()} contro $lifted", pageLeft() < lifted - 8f)
    compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    assertEquals("b", route())
  }

  @Test
  fun `il back di sistema che prende un gesto gia' partito nell'app continua da dove e' la pagina`() {
    showChild()
    compose.mainClock.autoAdvance = false

    // Navigazione a gesti: il dito parte nel bordo, l'app lo vede e il suo riconoscitore parte...
    compose.onRoot().performTouchInput {
      down(Offset(30f, 900f))
      repeat(6) { moveBy(Offset(6f, 0f), delayMillis = 16L) }
    }
    frame()
    val inApp = pageLeft()
    // ...poi il sistema si prende il dito (l'app riceve un annullo) e manda il suo inizio.
    compose.onRoot().performTouchInput { cancel() }
    val spring = SystemProgress(startX = 30f, maxDistance = 600f)
    compose.runOnUiThread { dispatcher.dispatchOnBackStarted(event(66f, spring.step(66f))) }
    frame()
    assertTrue("al passaggio la pagina e' saltata da $inApp a ${pageLeft()}", abs(pageLeft() - inApp) <= 2f)
    followAndCheck((1..10).map { 66f + it * 6f }, spring, pageLeft(), 66f)
    compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    assertEquals("b", route())
  }
}
