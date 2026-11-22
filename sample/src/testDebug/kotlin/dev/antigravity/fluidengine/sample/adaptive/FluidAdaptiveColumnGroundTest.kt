package dev.antigravity.fluidengine.sample.adaptive

import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.FluidAdaptiveNavigation
import dev.antigravity.fluidengine.ui.fluid.FluidPaneSizes
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
import dev.antigravity.fluidengine.ui.fluid.fluidBackgroundExtension
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Folder
import dev.antigravity.fluidengine.ui.fluid.icons.Inbox
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.time.Duration
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Assert.assertEquals
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
 * Lo sfondo dietro il rail, dietro la barra laterale e sotto l'ora (scelta di Alessio del 22 novembre 2026, sulle riprese
 * di F6B.12: nella posta sul tablet accanto al rail si vedeva la finestra, scura, e l'ora ci spariva sopra).
 * `FluidAdaptiveNavigation` mette da sé lo sfondo del tema nella colonna che toglie alla pagina; chi estende il suo fondale
 * con `fluidBackgroundExtension()` lo copre.
 *
 * La finestra qui è magenta: se la colonna fosse scoperta, si leggerebbe magenta. La barra di stato è finta, 24 dp, come in
 * [FluidAdaptiveStatusBarTest]. I punti letti stanno nella colonna, fuori dal vetro: nel margine a sinistra del rail sotto
 * l'ora e a metà altezza, e (con la barra laterale) fra il pannello e la pagina. I PNG vanno in
 * `build/reports/f6b-12/colonna/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h900dp-xhdpi")
class FluidAdaptiveColumnGroundTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  /** xhdpi: 2 px per dp. Non `density`, che dentro un'estensione di [Bitmap] è la densità della bitmap. */
  private val pxPerDp = 2f
  private val statusBarPx = 48
  private lateinit var root: View
  private var ground = Color.Unspecified
  private val window = Color(0xFFFF00FF)
  private val page = Color(0xFF2E7D32)
  private val out = File("build/reports/f6b-12/colonna")

  private val items = listOf(
    FluidTabItem("posta", "Posta", FluidIcons.Inbox),
    FluidTabItem("cartelle", "Cartelle", FluidIcons.Folder),
    FluidTabItem("cerca", "Cerca", FluidIcons.Search),
  )

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = false)
    out.mkdirs()
  }

  @After fun stop() {
    FluidGlassDiagnostics.resetForTesting()
  }

  private fun advance(ms: Int) {
    repeat((ms + 15) / 16) {
      compose.mainClock.advanceTimeByFrame()
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }
    compose.waitForIdle()
  }

  private fun show(mode: ThemeMode, extended: Boolean) {
    compose.runOnUiThread { compose.activity.window.setBackgroundDrawable(ColorDrawable(window.toArgb())) }
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings(themeMode = mode) }) {
        root = LocalView.current
        ground = MaterialTheme.colorScheme.background
        FluidAdaptiveNavigation(items, "posta", onSelect = {}) {
          // La pagina: senza estensione non dipinge niente (lo fa la scena, sopra la sua lista); con l'estensione, un
          // fondale verde che continua sotto il vetro, come una foto su iPad.
          if (extended) Box(Modifier.fillMaxSize().fluidBackgroundExtension().background(page))
          else Box(Modifier.fillMaxSize())
        }
      }
    }
    advance(400)
    compose.runOnUiThread {
      val bars = WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, statusBarPx, 0, 0))
        .setVisible(WindowInsetsCompat.Type.statusBars(), true)
        .build()
      ViewCompat.dispatchApplyWindowInsets(root, bars)
    }
    advance(400)
  }

  private fun shot(name: String): Bitmap {
    val decor = compose.activity.window.decorView
    val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
    compose.runOnIdle {
      PixelCopy.request(compose.activity.window, android.graphics.Rect(0, 0, decor.width, decor.height), bitmap, { result ->
        assertEquals(PixelCopy.SUCCESS, result)
      }, Handler(Looper.getMainLooper()))
    }
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, File(out, "$name.png").outputStream())
    return bitmap
  }

  /** Il colore a ([x], [y]) dp, come lo vede chi guarda. */
  private fun Bitmap.at(x: Float, y: Float): Color = Color(getPixel((x * pxPerDp).roundToInt(), (y * pxPerDp).roundToInt()))

  /** Vicino a [expected] canale per canale: il margine lascia passare l'ombra morbida del vetro, non un altro colore. */
  private fun assertNear(what: String, expected: Color, actual: Color) {
    val gap = maxOf(abs(expected.red - actual.red), abs(expected.green - actual.green), abs(expected.blue - actual.blue)) * 255
    assertTrue("$what: atteso ${expected.hex()}, letto ${actual.hex()} (scarto ${gap.roundToInt()})", gap <= 24f)
  }

  private fun Color.hex() = "#%08X".format(toArgb())

  /** Sotto l'ora e accanto al rail, nel margine a sinistra del vetro; e sul bordo destro della colonna. */
  private fun railPoints(bitmap: Bitmap) = listOf(
    "sotto l'ora" to bitmap.at(2f, 12f),
    "accanto al rail, a metà altezza" to bitmap.at(2f, 450f),
    "sul bordo della colonna, verso la pagina" to bitmap.at(FluidPaneSizes().rail.value - 2f, 12f),
  )

  @Test fun `in chiaro dietro il rail e sotto l'ora c'e' lo sfondo del tema`() {
    show(ThemeMode.LIGHT, extended = false)
    val bitmap = shot("rail-chiaro")
    assertTrue("il tema chiaro non è magenta", abs(ground.green - window.green) > .5f)
    for ((what, color) in railPoints(bitmap)) assertNear(what, ground, color)
  }

  @Test fun `in scuro dietro il rail e sotto l'ora c'e' lo sfondo del tema`() {
    show(ThemeMode.DARK, extended = false)
    val bitmap = shot("rail-scuro")
    assertTrue("il tema scuro è scuro", ground.red + ground.green + ground.blue < .6f)
    for ((what, color) in railPoints(bitmap)) assertNear(what, ground, color)
  }

  /** La barra laterale aperta: 280 dp di pannello fra due margini da 12; la pagina parte a 304. */
  private fun sidebar(mode: ThemeMode) {
    show(mode, extended = false)
    compose.onNodeWithContentDescription("Pannello laterale").performClick()
    advance(1500)
    val bitmap = shot("barra-laterale-${mode.name.lowercase()}")
    assertNear("$mode, sotto l'ora", ground, bitmap.at(2f, 12f))
    assertNear("$mode, accanto al pannello", ground, bitmap.at(2f, 450f))
    assertNear("$mode, fra il pannello e la pagina", ground, bitmap.at(298f, 450f))
  }

  @Test @Config(qualifiers = "w1400dp-h900dp-xhdpi")
  fun `in chiaro dietro la barra laterale aperta c'e' lo sfondo del tema`() = sidebar(ThemeMode.LIGHT)

  @Test @Config(qualifiers = "w1400dp-h900dp-xhdpi")
  fun `in scuro dietro la barra laterale aperta c'e' lo sfondo del tema`() = sidebar(ThemeMode.DARK)

  @Test fun `un fondale con fluidBackgroundExtension vince sullo sfondo di serie`() {
    show(ThemeMode.LIGHT, extended = true)
    val bitmap = shot("rail-estensione")
    for ((what, color) in railPoints(bitmap)) assertNear(what, page, color)
  }
}
