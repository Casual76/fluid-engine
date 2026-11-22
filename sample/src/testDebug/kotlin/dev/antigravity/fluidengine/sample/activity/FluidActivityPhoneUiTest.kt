package dev.antigravity.fluidengine.sample.activity

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivity
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivityHost
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivityHostState
import dev.antigravity.fluidengine.ui.fluid.icons.Archive
import dev.antigravity.fluidengine.ui.fluid.icons.FilterList
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarTitleMode
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.time.Duration
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * La pillola di attività sul telefono (F6B.10, terzo giro: il telefono è il bersaglio di Alessio, il tablet la sua
 * piattaforma di prova) e la card nel tema giusto.
 *
 * - A 360 e a 412 dp, con l'indietro, due tasti e «Altro»: la pillola sta nella barra senza toccare nessun altro tasto e
 *   dentro lo schermo; la card che apre sta tutta nello schermo.
 * - La card in chiaro e in scuro: la sua scritta ha l'inchiostro del tema (`onSurface`) e sta su un fondo del tema
 *   (chiaro in chiaro, scuro in scuro). Si legge sui pixel dello schermo (`PixelCopy`), come
 *   `FluidAdaptiveNavigationInkTest`; i PNG vanno in `build/reports/f6b-10/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h900dp-xhdpi")
class FluidActivityPhoneUiTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  /** xhdpi: 2 px per dp. */
  private val pxPerDp = 2f
  private var themeInk = Color.Unspecified
  private val out = File("build/reports/f6b-10")

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = false)
    out.mkdirs()
  }

  @After fun stop() {
    FluidGlassDiagnostics.resetForTesting()
  }

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      left -= 16
    }
  }

  private fun settle(ms: Long = 900) {
    Snapshot.sendApplyNotifications()
    compose.waitForIdle()
    advance(ms)
  }

  /** La posta col titolo in linea, l'indietro, «Cerca», «Filtri» e «Altro»: la barra piena di un telefono. */
  private fun screen(activities: FluidActivityHostState, mode: ThemeMode = ThemeMode.LIGHT) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings(themeMode = mode, dynamicColorEnabled = false) }) {
        themeInk = MaterialTheme.colorScheme.onSurface
        val modals = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides modals) {
          FluidActivityHost(activities) {
            Box(Modifier.fillMaxSize()) {
              FluidScreen(title = "Posta", onBack = {}, toolbar = {
                title(FluidToolbarTitleMode.Inline)
                button("cerca", FluidIcons.Search, "Cerca", onClick = {})
                button("filtri", FluidIcons.FilterList, "Filtri", onClick = {})
                overflow { button("archivia", FluidIcons.Archive, "Archivia tutte", onClick = {}) }
              }) {}
              FluidGlassModalHost(modals, backdrop = null)
            }
          }
        }
      }
    }
    settle()
  }

  private fun publish(activities: FluidActivityHostState) {
    activities.publish(FluidActivity.sync(title = "Calendario", progress = { 0.4f }, detail = "128 di 300 eventi"))
    settle()
  }

  private fun pill(): SemanticsNode = compose.onAllNodesWithContentDescription("Calendario").fetchSemanticsNodes()
    .minBy { it.boundsInRoot.top }

  private fun width(): Float = compose.onRoot().fetchSemanticsNode().boundsInRoot.width

  /** La pillola e gli altri tasti della barra non si toccano, e la pillola sta nello schermo. */
  private fun checkBar(name: String) {
    val pill = pill().boundsInRoot
    val screen = width()
    assertTrue("$name: la pillola dentro lo schermo ($pill su $screen)", pill.left >= 0f && pill.right <= screen)
    val others = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
      .map { it.boundsInRoot }
      // I tasti della riga della barra; non l'area del titolo, larga quanto la riga, che riporta in cima.
      .filter { it.top < pill.bottom && it.bottom > pill.top && it.width < screen / 2f && it != pill }
    assertTrue("$name: ci sono gli altri tasti della barra", others.size >= 3)
    for (other in others) {
      val apart = pill.right <= other.left + 0.5f || pill.left >= other.right - 0.5f
      assertTrue("$name: la pillola $pill tocca un tasto $other", apart)
    }
  }

  /** La card aperta dalla pillola sta tutta nello schermo. */
  private fun checkCard(name: String) {
    performOpen(pill())
    val card = compose.onAllNodesWithText("128 di 300 eventi", useUnmergedTree = true).fetchSemanticsNodes().single().boundsInRoot
    val screen = width()
    assertTrue("$name: la card nello schermo ($card su $screen)", card.left >= 0f && card.right <= screen)
  }

  private fun performOpen(pill: SemanticsNode) {
    val action = pill.config[SemanticsActions.OnClick].action
    compose.runOnUiThread { action?.invoke() }
    settle(1_200)
  }

  @Test fun `a 412 dp la pillola sta nella barra senza toccare i tasti, e la card nello schermo`() {
    val activities = FluidActivityHostState()
    screen(activities)
    publish(activities)
    checkBar("412")
    checkCard("412")
  }

  @Test @Config(qualifiers = "w360dp-h780dp-xhdpi")
  fun `a 360 dp la pillola sta nella barra senza toccare i tasti, e la card nello schermo`() {
    val activities = FluidActivityHostState()
    screen(activities)
    publish(activities)
    checkBar("360")
    checkCard("360")
  }

  @Test @Config(qualifiers = "w360dp-h780dp-xhdpi")
  fun `a 360 dp con due attivita il contatore non fa toccare la pillola agli altri tasti`() {
    val activities = FluidActivityHostState()
    screen(activities)
    publish(activities)
    // Della stessa importanza: la pillola resta la prima, col contatore «+1».
    activities.publish(FluidActivity.sync(id = "contatti", title = "Contatti", progress = { 0.1f }))
    settle()
    checkBar("360-due")
  }

  // --- La card nel tema ------------------------------------------------------------------------------------------------

  private fun shot(name: String): Bitmap {
    val decor = compose.activity.window.decorView
    val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
    compose.runOnIdle {
      PixelCopy.request(compose.activity.window, android.graphics.Rect(0, 0, decor.width, decor.height), bitmap, { result ->
        assertTrue(result == PixelCopy.SUCCESS)
      }, Handler(Looper.getMainLooper()))
    }
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, File(out, "$name.png").outputStream())
    return bitmap
  }

  private fun distance(a: Color, b: Color) = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue)) * 255

  /** Nel riquadro [box] (dp): il fondo (il colore più frequente) e l'inchiostro (il pixel più lontano dal fondo). */
  private fun Bitmap.inkAndGround(box: Rect): Pair<Color, Color> {
    val pixels = ArrayList<Int>()
    for (y in (box.top * pxPerDp).roundToInt() until (box.bottom * pxPerDp).roundToInt())
      for (x in (box.left * pxPerDp).roundToInt() until (box.right * pxPerDp).roundToInt()) pixels += getPixel(x, y)
    val ground = Color(pixels.groupingBy { it }.eachCount().maxBy { it.value }.key)
    return Color(pixels.maxBy { distance(Color(it), ground) }) to ground
  }

  private fun Color.hex() = "#%08X".format(toArgb())

  private fun checkCardTheme(mode: ThemeMode, name: String) {
    val activities = FluidActivityHostState()
    screen(activities, mode)
    publish(activities)
    performOpen(pill())
    // Il titolo della card: la scritta più in basso fra quelle che si chiamano come l'attività (la pillola è più su).
    val title = compose.onAllNodesWithText("Calendario", useUnmergedTree = true).fetchSemanticsNodes()
      .maxBy { it.boundsInRoot.top }.boundsInRoot
    val box = Rect(title.left / pxPerDp, title.top / pxPerDp, title.right / pxPerDp, title.bottom / pxPerDp)
    val (ink, ground) = shot(name).inkAndGround(box)
    val dark = mode == ThemeMode.DARK
    val gap = distance(ink, themeInk)
    assertTrue("$name: la scritta ha l'inchiostro del tema ${themeInk.hex()}, letto ${ink.hex()} (scarto ${gap.roundToInt()})",
      gap <= 60f)
    assertTrue("$name: il fondo della card è del tema (${ground.hex()}, luminanza ${ground.luminance()})",
      if (dark) ground.luminance() < 0.35f else ground.luminance() > 0.5f)
  }

  @Test fun `la card in scuro e scura, con la scritta chiara del tema`() {
    checkCardTheme(ThemeMode.DARK, "card-scuro")
  }

  @Test fun `la card in chiaro e chiara, con la scritta scura del tema`() {
    checkCardTheme(ThemeMode.LIGHT, "card-chiaro")
  }
}
