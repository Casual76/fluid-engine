package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavHostController
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
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

private const val TitleA = "Pagine"
private const val TitleB = "Pagina B"

/**
 * Le pagine della 3.0 sotto un dito finto, dentro un FluidNavHost vero.
 *
 * La promessa da controllare e' una: **la pagina sta sotto il dito**. Dal bordo, da un punto
 * qualunque, col gesto di sistema anche quando il suo `progress` fa quello che vuole. Poi le regole
 * del rilascio, la velocita' che passa dal dito alla molla, il tasto indietro.
 *
 * Schermo 360 dp a densita' 2: W = 720 px, zona del bordo 40 px, spinta 800 px/s. La soglia di
 * movimento e' quella di Robolectric (32 px), letta dal test. La posizione della pagina e' quella del suo titolo, meno quella che ha a riposo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidPageHostTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var nav: NavHostController
  private lateinit var pages: FluidPageHostState
  private var touchSlop = 0f

  private val width = 720f

  private fun show(reducedMotion: Boolean = false, rtl: Boolean = false) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        CompositionLocalProvider(
          LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
          LocalFluidMotionPolicy provides FluidMotionPolicy(durationScale = if (reducedMotion) 0f else 1f),
        ) {
          touchSlop = LocalViewConfiguration.current.touchSlop
          val controller = rememberFluidNavController()
          nav = controller
          val state = rememberFluidPageHostState()
          pages = state
          PagesDemo(navController = controller, state = state)
        }
      }
    }
    compose.waitForIdle()
  }

  private fun openB() {
    compose.runOnIdle { nav.navigate(PagesRouteB) }
    compose.waitForIdle()
    compose.onNodeWithText(TitleB).assertExists()
  }

  private fun route(): String? = compose.runOnIdle { nav.currentBackStackEntry?.destination?.route }

  private fun leftPx(text: String): Float =
    with(compose.density) { compose.onNodeWithText(text).getUnclippedBoundsInRoot().left.toPx() }

  private fun isShown(text: String): Boolean =
    compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

  /** Un trascinamento a passi regolari, un fotogramma per passo; torna (dito − contatto). */
  private fun drag(from: Offset, steps: Int, stepPx: Float, stepMillis: Long = 16L, lift: Boolean = true) {
    compose.onRoot().performTouchInput {
      down(from)
      repeat(steps) { moveBy(Offset(stepPx, 0f), delayMillis = stepMillis) }
      if (lift) up()
    }
  }

  // --- 1. Dal bordo, 1:1 ------------------------------------------------------------------------

  @Test
  fun `dal bordo la pagina sta sotto il dito a ogni fotogramma`() {
    show()
    openB()
    val rest = leftPx(TitleB)
    compose.mainClock.autoAdvance = false

    val downX = 6f
    compose.onRoot().performTouchInput { down(Offset(downX, 640f)) }
    compose.mainClock.advanceTimeByFrame()
    var finger = downX
    var grabbedAt = -1
    repeat(30) { frame ->
      compose.onRoot().performTouchInput { moveBy(Offset(12f, 0f)) }
      finger += 12f
      compose.mainClock.advanceTimeByFrame()
      val page = leftPx(TitleB) - rest
      if (grabbedAt < 0 && page > 0.5f) grabbedAt = frame
      if (grabbedAt >= 0) {
        assertEquals("fotogramma $frame: la pagina e' dove e' il dito", finger - downX, page, 1f)
      }
    }
    // Presa nel fotogramma in cui il dito supera la soglia, non uno dopo.
    val expected = (0 until 30).first { (it + 1) * 12f > touchSlop }
    assertEquals(expected, grabbedAt)
    compose.onRoot().performTouchInput { up() }
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    // 360 px lenti: oltre il 35%, si torna alla radice.
    assertEquals(PagesRouteA, route())
  }

  @Test
  fun `da un punto qualunque la pagina sta sotto il dito`() {
    show()
    openB()
    val rest = leftPx(TitleB)
    compose.mainClock.autoAdvance = false
    val downX = 300f
    compose.onRoot().performTouchInput { down(Offset(downX, 900f)) }
    compose.mainClock.advanceTimeByFrame()
    var finger = downX
    var grabbed = false
    repeat(20) { frame ->
      compose.onRoot().performTouchInput { moveBy(Offset(10f, 0f)) }
      finger += 10f
      compose.mainClock.advanceTimeByFrame()
      val page = leftPx(TitleB) - rest
      if (page > 0.5f) grabbed = true
      if (grabbed) assertEquals("fotogramma $frame", finger - downX, page, 1f)
    }
    assertTrue(grabbed)
    compose.onRoot().performTouchInput { up() }
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
  }

  // --- 2. Il back di sistema segue touchX, non progress ----------------------------------------

  @Test
  fun `il back di sistema segue il dito anche con un progress storto`() {
    show()
    openB()
    val rest = leftPx(TitleB)
    val input = DirectNavigationEventInput()
    compose.runOnUiThread { compose.activity.navigationEventDispatcher.addInput(input) }
    compose.mainClock.autoAdvance = false

    var time = 1_000L
    fun event(x: Float, progress: Float) = NavigationEvent(
      swipeEdge = NavigationEvent.EDGE_LEFT,
      progress = progress,
      touchX = x,
      touchY = 640f,
      frameTimeMillis = time.also { time += 16L },
    )
    compose.runOnUiThread { input.backStarted(event(12f, 0f)) }
    compose.mainClock.advanceTimeByFrame()
    // L'host tocca il bordo sinistro: l'ancora e' il bordo, la pagina e' al dito dal primo evento.
    assertEquals(12f, leftPx(TitleB) - rest, 1f)

    // Un progress volutamente non lineare: corre, si ferma, torna indietro, salta.
    val path = listOf(40f to 0.02f, 90f to 0.40f, 150f to 0.41f, 200f to 0.35f, 260f to 0.95f, 300f to 0.96f)
    for ((x, progress) in path) {
      compose.runOnUiThread { input.backProgressed(event(x, progress)) }
      compose.mainClock.advanceTimeByFrame()
      assertEquals("touchX $x, progress $progress", x, leftPx(TitleB) - rest, 1f)
    }

    compose.runOnUiThread { input.backCancelled() }
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    assertEquals(0f, leftPx(TitleB) - rest, 0.5f)
    assertEquals(PagesRouteB, route())

    // Poi il back senza gesto (3 tasti, Android 13): la pagina esce con la molla e la pila si accorcia.
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.waitForIdle()
    assertEquals(PagesRouteA, route())
    assertTrue(!isShown(TitleB))
  }

  @Test
  fun `il back di sistema confermato porta fuori la pagina`() {
    show()
    openB()
    val input = DirectNavigationEventInput()
    compose.runOnUiThread { compose.activity.navigationEventDispatcher.addInput(input) }
    compose.runOnUiThread {
      input.backStarted(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT, progress = 0f, touchX = 10f, touchY = 600f, frameTimeMillis = 100L))
      input.backProgressed(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT, progress = 0.1f, touchX = 60f, touchY = 600f, frameTimeMillis = 116L))
      // Il sistema decide: conferma anche se la pagina e' a 60 px.
      input.backCompleted()
    }
    compose.waitForIdle()
    assertEquals(PagesRouteA, route())
    assertTrue(!isShown(TitleB))
  }

  // --- 3. Le regole del rilascio ---------------------------------------------------------------

  @Test
  fun `un rilascio lento sotto il 35 percento resta`() {
    show()
    openB()
    val rest = leftPx(TitleB)
    // 0,30 W = 216 px a 250 px/s.
    drag(Offset(300f, 900f), steps = 54, stepPx = 4f)
    compose.waitForIdle()
    assertEquals(PagesRouteB, route())
    assertEquals(0f, leftPx(TitleB) - rest, 0.5f)
  }

  @Test
  fun `un rilascio lento oltre il 35 percento torna indietro`() {
    show()
    openB()
    // 0,40 W = 288 px a 250 px/s.
    drag(Offset(300f, 900f), steps = 72, stepPx = 4f)
    compose.waitForIdle()
    assertEquals(PagesRouteA, route())
    assertTrue(!isShown(TitleB))
  }

  @Test
  fun `una spinta corta torna indietro`() {
    show()
    openB()
    // 0,10 W = 72 px a 2000 px/s, molto oltre gli 800 (presa a 40 px, oltre la soglia).
    drag(Offset(300f, 900f), steps = 9, stepPx = 8f, stepMillis = 4L)
    compose.waitForIdle()
    assertEquals(PagesRouteA, route())
  }

  @Test
  fun `oltre meta' ma spinta indietro resta`() {
    show()
    openB()
    val rest = leftPx(TitleB)
    compose.onRoot().performTouchInput {
      down(Offset(200f, 900f))
      repeat(36) { moveBy(Offset(10f, 0f), delayMillis = 16L) }
      repeat(3) { moveBy(Offset(-24f, 0f), delayMillis = 8L) }
      up()
    }
    compose.waitForIdle()
    assertEquals(PagesRouteB, route())
    assertEquals(0f, leftPx(TitleB) - rest, 0.5f)
  }

  // --- 4. La velocita' del dito passa alla molla ------------------------------------------------

  @Test
  fun `la molla parte con la velocita' del dito`() {
    show()
    openB()
    val rest = leftPx(TitleB)
    compose.mainClock.autoAdvance = false
    // 12 px ogni 16 ms: 750 px/s, sotto la spinta (800): conferma per distanza a 0,5 W. Un blocco
    // solo, cosi' i tempi degli eventi sono quelli del dito e non quelli dei fotogrammi del test.
    compose.onRoot().performTouchInput {
      down(Offset(150f, 900f))
      repeat(30) { moveBy(Offset(12f, 0f), delayMillis = 16L) }
      up()
    }
    compose.mainClock.advanceTimeByFrame()
    val velocity = compose.runOnIdle { pages.velocity }
    assertEquals("velocita' della pagina subito dopo il rilascio", 750f, velocity, 750f * 0.2f)

    // Primo fotogramma della molla: la pagina e' dove l'ha lasciata il dito.
    val atRelease = leftPx(TitleB) - rest
    assertEquals(360f, atRelease, 1f)
    // E continua verso fuori, almeno come andava il dito (12 px a fotogramma).
    compose.mainClock.advanceTimeByFrame()
    val next = leftPx(TitleB) - rest
    assertTrue("passo dopo il rilascio ${next - atRelease} px", next - atRelease >= 12f * 0.8f)
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    assertEquals(PagesRouteA, route())
  }

  // --- 5. Il tasto indietro ---------------------------------------------------------------------

  @Test
  fun `il tasto indietro porta fuori la pagina con una corsa monotona`() {
    show()
    openB()
    val rest = leftPx(TitleB)
    compose.mainClock.autoAdvance = false
    compose.onNodeWithText("Indietro").performClick()
    var last = 0f
    var frames = 0
    var moved = false
    while (isShown(TitleB) && frames < 120) {
      compose.mainClock.advanceTimeByFrame()
      frames++
      if (!isShown(TitleB)) break
      val page = leftPx(TitleB) - rest
      assertTrue("fotogramma $frames: $page dopo $last", page >= last - 0.5f)
      if (page > 0.5f) moved = true
      last = page
    }
    assertTrue("la pagina si e' mossa prima di sparire", moved)
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    assertEquals(PagesRouteA, route())
  }

  @Test
  fun `un salto di piu' pagine porta fuori la cima e basta`() {
    show()
    openB()
    compose.runOnIdle { nav.navigate(PagesRouteC) }
    compose.waitForIdle()
    assertTrue(isShown("Pagina C"))
    compose.mainClock.autoAdvance = false
    compose.runOnUiThread { nav.popBackStack(PagesRouteA, inclusive = false) }
    // Durante il salto si vedono solo la cima che esce e la radice: la pagina di mezzo no.
    repeat(4) {
      compose.mainClock.advanceTimeByFrame()
      assertTrue("fotogramma $it: la pagina di mezzo non c'e'", !isShown(TitleB))
    }
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    assertEquals(PagesRouteA, route())
    assertTrue(!isShown("Pagina C"))
    assertTrue(isShown(TitleA))
  }

  // --- RTL e animazioni ridotte ----------------------------------------------------------------

  @Test
  fun `in RTL la pagina esce a sinistra, sempre sotto il dito`() {
    show(rtl = true)
    openB()
    val rest = leftPx(TitleB)
    compose.mainClock.autoAdvance = false
    val downX = width - 6f
    compose.onRoot().performTouchInput { down(Offset(downX, 640f)) }
    compose.mainClock.advanceTimeByFrame()
    var finger = downX
    var grabbed = false
    // 24 passi da 12: 288 px, oltre il 35% (252), quindi alla fine si torna alla radice.
    repeat(24) { frame ->
      compose.onRoot().performTouchInput { moveBy(Offset(-12f, 0f)) }
      finger -= 12f
      compose.mainClock.advanceTimeByFrame()
      val page = leftPx(TitleB) - rest
      if (page < -0.5f) grabbed = true
      if (grabbed) assertEquals("fotogramma $frame", finger - downX, page, 1f)
    }
    assertTrue(grabbed)
    compose.onRoot().performTouchInput { up() }
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    assertEquals(PagesRouteA, route())
  }

  @Test
  fun `con le animazioni ridotte il push salta e il dito segue lo stesso`() {
    show(reducedMotion = true)
    compose.mainClock.autoAdvance = false
    compose.runOnUiThread { nav.navigate(PagesRouteB) }
    // Un fotogramma per la composizione che vede la pila nuova, uno per disegnarla a riposo.
    compose.mainClock.advanceTimeByFrame()
    compose.mainClock.advanceTimeByFrame()
    assertTrue(isShown(TitleB))
    val rest = leftPx(TitleB)
    assertTrue(!isShown(TitleA))

    val downX = 6f
    compose.onRoot().performTouchInput { down(Offset(downX, 640f)) }
    compose.mainClock.advanceTimeByFrame()
    var finger = downX
    var grabbed = false
    repeat(10) { frame ->
      compose.onRoot().performTouchInput { moveBy(Offset(12f, 0f)) }
      finger += 12f
      compose.mainClock.advanceTimeByFrame()
      val page = leftPx(TitleB) - rest
      if (page > 0.5f) grabbed = true
      if (grabbed) assertEquals("fotogramma $frame", finger - downX, page, 1f)
    }
    assertTrue(grabbed)
    compose.onRoot().performTouchInput { up() }
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    // 108 px lenti, sotto il 35%: torna, senza animazione.
    assertEquals(PagesRouteB, route())
  }
}
