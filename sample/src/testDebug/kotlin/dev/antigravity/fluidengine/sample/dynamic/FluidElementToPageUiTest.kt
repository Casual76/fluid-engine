package dev.antigravity.fluidengine.sample.dynamic

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.page.FluidPageHostState
import dev.antigravity.fluidengine.ui.page.FluidPageMove
import dev.antigravity.fluidengine.ui.page.rememberFluidPageHostState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.time.Duration
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * «Un elemento che diventa pagina» (F6B.5c) nelle scene della galleria, con le API pubbliche dell'engine: i quattro
 * preset si aprono e tornano, la pagina è una rotta vera (collegamento, back, ricreazione, morte del processo simulata
 * con lo stato salvato) e una sorgente sparita fa tornare la pagina col ripiego, senza salti.
 *
 * L'orologio è sempre a mano, a fotogrammi da 16 ms: le barre e i vetri della galleria possono chiedere fotogrammi anche
 * a riposo, e un'attesa dell'inattività con l'orologio automatico non finirebbe mai (come in `FluidBottomGalleryTest`).
 * Se il viaggio è lo zoom dalla sorgente o il ripiego lo dice lo stato interno dell'host, letto per riflessione
 * ([zoomActive]).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-mdpi")
class FluidElementToPageUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  @get:Rule val name = TestName()

  private lateinit var nav: NavHostController
  private lateinit var pages: FluidPageHostState
  private lateinit var photos: LazyListState

  private val content: @Composable () -> Unit = {
    FluidTheme(settings = remember { EngineSettings() }) {
      nav = rememberFluidNavController()
      pages = rememberFluidPageHostState()
      photos = rememberLazyListState()
      ElementToPagePage(navController = nav, state = pages, photos = photos)
    }
  }

  @Before fun start() {
    progress("inizio")
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = false)
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() {
    FluidGlassDiagnostics.resetForTesting()
    progress("fine")
  }

  /** Dove è arrivata la suite: se un test si ferma, il file dice quale (build/f6b5c-avanzamento.txt). */
  private fun progress(what: String) {
    runCatching {
      File("build/f6b5c-avanzamento.txt").appendText("${System.currentTimeMillis()} ${name.methodName} $what\n")
    }
  }

  // --- Aiuti ---------------------------------------------------------------------------------------------------------

  private fun advance(ms: Int) = repeat((ms + 15) / 16) {
    compose.mainClock.advanceTimeByFrame()
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
  }

  private val moving: Boolean get() = compose.runOnIdle { pages.isMoving }

  /**
   * La morte del processo simulata con lo stato salvato. Con l'orologio fermo lo smontaggio e la ricomposizione
   * cadrebbero nello stesso fotogramma e la composizione resterebbe quella di prima: qui, e solo qui, l'orologio va da sé.
   */
  private fun StateRestorationTester.die() {
    compose.mainClock.autoAdvance = true
    try {
      emulateSavedInstanceStateRestore()
    } finally {
      compose.mainClock.autoAdvance = false
    }
  }

  /** Fino al riposo dell'host, al più [maxMs]: mai un'attesa senza fine. */
  private fun settle(maxMs: Int = 4000) {
    advance(48)
    var waited = 0
    while (moving && waited < maxMs) {
      advance(16)
      waited += 16
    }
    assertFalse("l'host è ancora in movimento dopo $maxMs ms", moving)
    advance(48)
  }

  private fun show() {
    compose.setContent(content)
    advance(400)
  }

  private fun go(route: String) {
    compose.runOnIdle { nav.navigate(route) }
    settle()
  }

  private fun back() {
    compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
  }

  private val route: String? get() = compose.runOnIdle { nav.currentDestination?.route }

  private fun FluidPageHostState.internal(name: String): Any? =
    javaClass.methods.first { it.name.startsWith("get$name$") && it.parameterCount == 0 }.invoke(this)

  /** Il viaggio in corso è lo zoom dalla sorgente (non il ripiego): l'host ha trovato la sorgente. */
  private val FluidPageHostState.zoomActive: Boolean get() = internal("Zoom") != null

  /** Il viaggio porta il vetro che cresce (Cresce e i tasti della barra). */
  private val FluidPageHostState.growingActive: Boolean get() = internal("Growing") != null

  private fun atRoute(destination: String): (Any?) -> Boolean =
    { (it as? NavBackStackEntry)?.destination?.route == destination }

  private fun shot(): Bitmap {
    val window = compose.activity.window
    val bitmap = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
    compose.runOnIdle {
      PixelCopy.request(window, bitmap, { assertEquals(PixelCopy.SUCCESS, it) }, Handler(Looper.getMainLooper()))
    }
    shadowOf(Looper.getMainLooper()).idle()
    return bitmap
  }

  private data class Ink(val left: Int, val top: Int, val right: Int, val bottom: Int, val count: Int) {
    val width get() = right - left + 1
    val height get() = bottom - top + 1
  }

  private fun Bitmap.ink(color: Int): Ink {
    val data = IntArray(width * height)
    getPixels(data, 0, width, 0, 0, width, height)
    var left = width
    var right = -1
    var top = height
    var bottom = -1
    var n = 0
    for (i in data.indices) if (data[i] == color) {
      val x = i % width
      val y = i / width
      n++
      if (x < left) left = x
      if (x > right) right = x
      if (y < top) top = y
      if (y > bottom) bottom = y
    }
    return Ink(left, top, right, bottom, n)
  }

  /** Il push di un preset: il viaggio è lo zoom dalla sorgente, e la pagina arriva a riposo aperta. */
  private fun expectZoomOpen(destination: String, grow: Boolean) {
    advance(32)
    compose.runOnIdle {
      assertEquals(destination, nav.currentDestination?.route)
      assertEquals(FluidPageMove.Push, pages.kind)
      assertTrue("lo zoom ha trovato la sorgente", pages.zoomActive)
      assertEquals(grow, pages.growingActive)
    }
    settle()
    compose.runOnIdle { assertEquals(1f, pages.fractionToward(atRoute(destination)), 0f) }
  }

  /** Il ritorno: all'inizio è lo zoom verso la sorgente ([toSource]) o il ripiego; alla fine si è su [landing]. */
  private fun expectReturn(landing: String, toSource: Boolean) {
    advance(32)
    compose.runOnIdle {
      assertTrue(pages.isMoving)
      assertEquals("ritorno nella sorgente: $toSource", toSource, pages.zoomActive)
    }
    settle()
    assertEquals(landing, route)
  }

  // --- I quattro preset ----------------------------------------------------------------------------------------------

  @Test fun zoomDallaGrigliaAlVisoreEIndietro() {
    show()
    go(ElementToPageRoutes.PhotosGraph)
    compose.onNodeWithTag(ElementToPageTags.thumbnail(4)).performClick()
    expectZoomOpen(ElementToPageRoutes.Viewer, grow = false)
    assertEquals(4, compose.runOnIdle { nav.currentBackStackEntry?.arguments?.getInt(ElementToPageRoutes.ArgPhoto) })
    back()
    expectReturn(ElementToPageRoutes.Grid, toSource = true)
  }

  @Test fun saleDalMiniLettoreAlToccoEColDito() {
    show()
    go(ElementToPageRoutes.MusicGraph)
    compose.onNodeWithTag(ElementToPageTags.MiniPlayer).performClick()
    expectZoomOpen(ElementToPageRoutes.Player, grow = false)
    back()
    expectReturn(ElementToPageRoutes.Library, toSource = true)

    // Col dito: su dal mini lettore, giù dalla pagina.
    val hit = compose.onNodeWithTag(ElementToPageTags.MiniPlayer).fetchSemanticsNode().boundsInRoot.center
    val root = compose.onRoot()
    root.performTouchInput { down(hit); repeat(6) { moveBy(Offset(0f, -40f)) } }
    advance(64)
    compose.runOnIdle {
      assertTrue(pages.isInteractive)
      assertTrue(pages.zoomActive)
    }
    root.performTouchInput { repeat(8) { moveBy(Offset(0f, -40f)) }; up() }
    settle()
    assertEquals(ElementToPageRoutes.Player, route)
    root.performTouchInput { down(Offset(180f, 300f)); repeat(10) { moveBy(Offset(0f, 30f)) } }
    advance(64)
    compose.runOnIdle {
      assertTrue(pages.isInteractive)
      assertTrue("la pagina tirata giù rimpicciolisce verso il mini lettore", pages.zoomActive)
    }
    root.performTouchInput { repeat(6) { moveBy(Offset(0f, 30f)) }; up() }
    settle()
    assertEquals(ElementToPageRoutes.Library, route)
  }

  @Test fun cresceDallaPillolaELaSceltaAggiornaLaLocalita() {
    show()
    go(ElementToPageRoutes.WeatherGraph)
    compose.onNodeWithTag(ElementToPageTags.Pill).performClick()
    expectZoomOpen(ElementToPageRoutes.Places, grow = true)
    compose.onNodeWithText("Milano").performClick()
    expectReturn(ElementToPageRoutes.Weather, toSource = true)
    compose.onNodeWithTag(ElementToPageTags.Pill).assertTextContains("Milano")
  }

  @Test fun scriviDallaBarraDiventaIlCompositoreETornaNelTasto() {
    show()
    go(ElementToPageRoutes.MailGraph)
    compose.onNodeWithContentDescription("Scrivi").performClick()
    expectZoomOpen(ElementToPageRoutes.Compose, grow = true)
    compose.onNodeWithContentDescription("Invia").performClick()
    expectReturn(ElementToPageRoutes.Inbox, toSource = true)
    compose.onNodeWithContentDescription("Scrivi").assertExists()
  }

  // --- La rotta vera -------------------------------------------------------------------------------------------------

  @Test fun ilCollegamentoApreIlVisoreConLaGrigliaSottoEIlRitornoTrovaLaMiniatura() {
    // Come lo lancia il sistema: un compito nuovo, e la pila sintetica del grafo (indice → griglia → visore). Il
    // controller legge il collegamento dall'attività alla prima composizione; poi l'intent torna quello di partenza,
    // con cui `ActivityScenario` riconosce la sua attività alla chiusura.
    val launch = compose.activity.intent
    compose.activity.intent = Intent(Intent.ACTION_VIEW, Uri.parse(ElementToPageLinks.viewer(4)))
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    compose.setContent(content)
    advance(16)
    compose.activity.intent = launch
    compose.runOnIdle {
      val routes = nav.currentBackStack.value.mapNotNull { it.destination.route }
      assertEquals(listOf(ElementToPageRoutes.Index, ElementToPageRoutes.PhotosGraph, ElementToPageRoutes.Grid,
        ElementToPageRoutes.Viewer), routes)
      assertEquals(4, nav.currentBackStackEntry?.arguments?.getInt(ElementToPageRoutes.ArgPhoto))
      assertFalse("aperta da un collegamento la pagina è già aperta, senza viaggio", pages.isMoving)
      assertEquals(1f, pages.fractionToward(atRoute(ElementToPageRoutes.Viewer)), 0f)
    }
    advance(200)
    back()
    expectReturn(ElementToPageRoutes.Grid, toSource = true)
    assertTrue(shot().ink(elementToPagePhotoColor(4).toArgb()).count > 0)

    // Un collegamento con l'app aperta: la pagina arriva sopra quella di adesso, e senza la sua sorgente torna col ripiego.
    compose.runOnIdle { nav.navigate(Uri.parse(ElementToPageLinks.Player)) }
    settle()
    assertEquals(ElementToPageRoutes.Player, route)
    back()
    expectReturn(ElementToPageRoutes.Grid, toSource = false)
  }

  @Test fun ilBackDiSistemaChiudeLaPaginaPrimaDellaScena() {
    show()
    go(ElementToPageRoutes.MailGraph)
    compose.onNodeWithContentDescription("Scrivi").performClick()
    settle()
    assertEquals(ElementToPageRoutes.Compose, route)
    back()
    settle()
    assertEquals(ElementToPageRoutes.Inbox, route)
    back()
    settle()
    assertEquals(ElementToPageRoutes.Index, route)
  }

  @Test fun laRicreazioneTieneAperteLaPaginaELaPilaETorna() {
    show()
    go(ElementToPageRoutes.PhotosGraph)
    compose.onNodeWithTag(ElementToPageTags.thumbnail(1)).performClick()
    settle()
    val old = pages
    compose.activityRule.scenario.recreate()
    compose.mainClock.autoAdvance = false
    compose.setContent(content)
    advance(400)
    compose.runOnIdle {
      assertTrue(old !== pages)
      assertEquals(ElementToPageRoutes.Viewer, nav.currentDestination?.route)
      assertEquals(1, nav.currentBackStackEntry?.arguments?.getInt(ElementToPageRoutes.ArgPhoto))
      assertFalse(pages.isMoving)
    }
    back()
    expectReturn(ElementToPageRoutes.Grid, toSource = true)
  }

  @Test fun dopoLaMorteDelProcessoLaPaginaEApertaSenzaViaggioEIlRitornoCercaLaSorgente() {
    val restore = StateRestorationTester(compose)
    restore.setContent(content)
    advance(400)
    go(ElementToPageRoutes.MusicGraph)
    compose.onNodeWithTag(ElementToPageTags.MiniPlayer).performClick()
    settle()
    val oldNav = nav
    val oldPages = pages
    restore.die()
    advance(16)
    compose.runOnIdle {
      assertTrue("un controller e un host nuovi, come dopo la morte del processo", oldNav !== nav && oldPages !== pages)
      assertEquals(ElementToPageRoutes.Player, nav.currentDestination?.route)
      assertFalse("ripristinata, la pagina è aperta senza viaggio", pages.isMoving)
    }
    advance(400)
    back()
    expectReturn(ElementToPageRoutes.Library, toSource = true)

    // Il compositore ripristinato: il tasto della barra non è mai stato trovato in questo processo, e il ritorno lo cerca.
    go(ElementToPageRoutes.MailGraph)
    compose.onNodeWithContentDescription("Scrivi").performClick()
    settle()
    restore.die()
    advance(400)
    assertEquals(ElementToPageRoutes.Compose, route)
    back()
    settle()
    assertEquals(ElementToPageRoutes.Inbox, route)
    compose.onNodeWithContentDescription("Scrivi").assertExists()
  }

  // --- La sorgente sparita -------------------------------------------------------------------------------------------

  @Test fun unaMiniaturaUscitaDalloScorrimentoFaTornareIlVisoreColRipiegoSenzaSalti() {
    show()
    go(ElementToPageRoutes.PhotosGraph)
    compose.onNodeWithTag(ElementToPageTags.thumbnail(4)).performClick()
    settle()
    assertEquals(ElementToPageRoutes.Viewer, route)
    // Mentre il visore è aperto la griglia, coperta, scorre in fondo: la miniatura non è più composta.
    compose.runOnIdle { photos.requestScrollToItem(14) }
    advance(200)
    compose.runOnIdle { assertTrue(photos.firstVisibleItemIndex > 4) }
    back()
    advance(32)
    compose.runOnIdle {
      assertTrue(pages.isMoving)
      assertFalse("senza sorgente vale il ripiego", pages.zoomActive)
    }
    val progress = mutableListOf<Float>()
    var guard = 0
    while (guard++ < 200) {
      val (stillMoving, p) = compose.runOnIdle { pages.isMoving to pages.progress }
      if (!stillMoving) break
      progress += p
      advance(16)
    }
    assertTrue(progress.size > 5)
    for ((a, b) in progress.zipWithNext()) {
      assertTrue("il ritorno non torna indietro: $a → $b", b >= a - 0.0001f)
      assertTrue("nessun salto: $a → $b", abs(b - a) < 0.25f)
    }
    settle()
    assertEquals(ElementToPageRoutes.Grid, route)
  }

  // --- I pixel della griglia -----------------------------------------------------------------------------------------

  @Test fun laFotoViaggiaInteraFraLaMiniaturaEIlVisore() {
    show()
    go(ElementToPageRoutes.PhotosGraph)
    val color = elementToPagePhotoColor(4).toArgb()
    val start = shot().ink(color)
    assertTrue(start.count > 0)
    compose.onNodeWithTag(ElementToPageTags.thumbnail(4)).performClick()
    advance(32)
    var sampled = 0
    var guard = 0
    while (guard++ < 80) {
      val (stillMoving, p) = compose.runOnIdle { pages.isMoving to pages.progress }
      if (!stillMoving) break
      if (p in 0.3f..0.85f) {
        val art = shot().ink(color)
        // Una foto sola, piena, fra la miniatura e il visore: la miniatura non resta al suo posto (due macchie dello
        // stesso colore riempirebbero molto meno del loro riquadro comune). Gli angoli della sagoma ne tolgono poco.
        assertTrue("una sola foto a p=$p: $art", art.count >= 0.9f * art.width * art.height)
        assertTrue("cresce: $art contro $start", art.width > start.width && art.width < 360)
        sampled++
      }
      advance(16)
    }
    assertTrue("fotogrammi a metà viaggio: $sampled", sampled >= 2)
    settle()
    assertEquals(360, shot().ink(color).width)
  }
}
