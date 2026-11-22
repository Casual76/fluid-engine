package dev.antigravity.fluidengine.sample.adaptive

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.FluidAdaptiveNavigation
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
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
 * L'inchiostro delle voci di `FluidAdaptiveNavigation` (scelta di Alessio del 22 novembre 2026, sulle riprese di F6B.12):
 * la tab bar in alto e la barra laterale prendono da sole il colore del contenuto dal tema, anche senza una `Surface`
 * dell'app sopra la navigazione; prima, in scuro, le voci uscivano nere sul vetro scuro. Una `Surface` o un
 * `LocalContentColor` dell'app continuano a valere come prima. Il rail prende l'inchiostro del tema da sempre, e non
 * cambia.
 *
 * Si legge l'inchiostro di «Cartelle», la voce non scelta (niente lente né velo sopra): nel riquadro della scritta, il
 * pixel più lontano dal colore che ci sta più spesso. I PNG vanno in `build/reports/f6b-12/voci/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1400dp-h900dp-xhdpi")
class FluidAdaptiveNavigationInkTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  /** xhdpi: 2 px per dp. */
  private val pxPerDp = 2f
  private var theme = Color.Unspecified
  /** Un colore del contenuto che nessun tema dà: se le voci lo prendono, vale quello dell'app. */
  private val appInk = Color(0xFFD81B60)
  private val out = File("build/reports/f6b-12/voci")

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

  private enum class Around { Nothing, Surface, ContentColor }

  private fun show(mode: ThemeMode, around: Around, sidebar: Boolean) {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings(themeMode = mode) }) {
        theme = MaterialTheme.colorScheme.onSurface
        val navigation: @Composable () -> Unit = {
          FluidAdaptiveNavigation(items, "posta", onSelect = {}) { Box(Modifier.fillMaxSize()) }
        }
        when (around) {
          Around.Nothing -> navigation()
          Around.Surface -> Surface(color = MaterialTheme.colorScheme.background, contentColor = appInk) { navigation() }
          Around.ContentColor -> CompositionLocalProvider(LocalContentColor provides appInk) { navigation() }
        }
      }
    }
    advance(400)
    if (sidebar) {
      compose.onNodeWithContentDescription("Pannello laterale").performClick()
      advance(1500)
    }
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

  /** L'inchiostro nel riquadro [box] (dp): il pixel più lontano dal colore che ci sta più spesso, il fondo. */
  private fun Bitmap.ink(box: Rect): Color {
    val pixels = ArrayList<Int>()
    for (y in (box.top * pxPerDp).roundToInt() until (box.bottom * pxPerDp).roundToInt())
      for (x in (box.left * pxPerDp).roundToInt() until (box.right * pxPerDp).roundToInt()) pixels += getPixel(x, y)
    val ground = Color(pixels.groupingBy { it }.eachCount().maxBy { it.value }.key)
    return Color(pixels.maxBy { distance(Color(it), ground) })
  }

  private fun distance(a: Color, b: Color) =
    maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue)) * 255

  private fun Color.hex() = "#%08X".format(toArgb())

  /** La scritta «Cartelle»; nel rail ce n'è anche una copia invisibile per la lente, nello stesso posto. */
  private fun label(): Rect {
    val bounds = compose.onAllNodesWithText("Cartelle", useUnmergedTree = true).onFirst().getBoundsInRoot()
    return Rect(bounds.left.value, bounds.top.value, bounds.right.value, bounds.bottom.value)
  }

  /** La tolleranza lascia passare il bordo sfumato dei caratteri, non un altro colore: nero e #F2F2F5 distano 242. */
  private fun check(name: String, expected: Color) {
    val ink = shot(name).ink(label())
    val gap = distance(ink, expected)
    assertTrue("$name: atteso ${expected.hex()}, letto ${ink.hex()} (scarto ${gap.roundToInt()})", gap <= 48f)
  }

  @Test fun `barra laterale in scuro senza Surface ha l'inchiostro del tema`() {
    show(ThemeMode.DARK, Around.Nothing, sidebar = true)
    check("barra-laterale-scuro", theme)
  }

  @Test fun `barra laterale in chiaro senza Surface ha l'inchiostro del tema`() {
    show(ThemeMode.LIGHT, Around.Nothing, sidebar = true)
    check("barra-laterale-chiaro", theme)
  }

  @Test fun `barra laterale in scuro con una Surface vale la Surface`() {
    show(ThemeMode.DARK, Around.Surface, sidebar = true)
    check("barra-laterale-scuro-surface", appInk)
  }

  @Test fun `barra laterale in chiaro con una Surface vale la Surface`() {
    show(ThemeMode.LIGHT, Around.Surface, sidebar = true)
    check("barra-laterale-chiaro-surface", appInk)
  }

  @Test fun `tab bar in alto in scuro senza Surface ha l'inchiostro del tema`() {
    show(ThemeMode.DARK, Around.Nothing, sidebar = false)
    check("tab-bar-alto-scuro", theme)
  }

  @Test fun `tab bar in alto in chiaro con un LocalContentColor dell'app vale il suo`() {
    show(ThemeMode.LIGHT, Around.ContentColor, sidebar = false)
    check("tab-bar-alto-chiaro-contenuto", appInk)
  }

  @Test @Config(qualifiers = "w800dp-h900dp-xhdpi")
  fun `il rail in scuro con una Surface ha l'inchiostro del tema, come prima`() {
    show(ThemeMode.DARK, Around.Surface, sidebar = false)
    // Il rail non ha mai seguito la Surface dell'app (fluidChromeInk dalla 3.x): resta così.
    check("rail-scuro-surface", theme)
  }

  @Test @Config(qualifiers = "w800dp-h900dp-xhdpi")
  fun `il rail in chiaro senza Surface ha l'inchiostro del tema`() {
    show(ThemeMode.LIGHT, Around.Nothing, sidebar = false)
    check("rail-chiaro", theme)
  }
}
