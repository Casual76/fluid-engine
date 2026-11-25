package dev.antigravity.fluidengine.sample.novita

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performSemanticsAction
import dev.antigravity.fluidengine.files.FluidFileBrowser
import dev.antigravity.fluidengine.files.FluidFileBrowserTags
import dev.antigravity.fluidengine.files.FluidFileLayout
import dev.antigravity.fluidengine.files.FluidFileRecents
import dev.antigravity.fluidengine.files.FluidFileSources
import dev.antigravity.fluidengine.files.rememberFluidFileBrowserState
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.time.Duration
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
 * F8.18 · Lo sfoglia-file nel formato del telefono (412 × 892 dp), in chiaro e in scuro, col vetro vero: Sfoglia, la
 * cartella in griglia con le miniature vere e in elenco. Le schermate vanno in `sample/build/f8-18-sfoglia` (per la
 * tavola accanto ad Apple); la prova controlla che il contenuto ci sia e che il tema scuro sia scuro.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
class F8p18BrowserPixelsTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private lateinit var root: File

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    compose.mainClock.autoAdvance = false
    root = File(compose.activity.filesDir, "tavola-${System.nanoTime()}").apply {
      mkdirs()
      File(this, "Documenti").mkdirs()
      File(this, "Immagini").mkdirs()
      File(this, "Note del corso").apply { mkdirs(); for (i in 1..12) File(this, "Nota $i.txt").writeText("Nota $i") }
      File(this, "Appunti di fisica.txt").writeText("Il moto rettilineo uniforme\n\nVelocità costante, accelerazione zero.\n" +
        "La legge oraria è s = s0 + v t.\n")
      File(this, "Elenco della spesa.md").writeText("# Spesa\n\n- pane\n- latte\n- mele\n")
      File(this, "Visore.kt").writeText("fun main() {\n  val pagine = 3000\n  println(\"Pagina 1 di \$pagine\")\n}\n")
      File(this, "Bilancio.csv").writeText("mese,entrate,uscite\ngennaio,1200,900\nfebbraio,1100,950\n")
      for ((i, f) in listFiles()!!.withIndex()) f.setLastModified(1_764_000_000_000L - i * 86_400_000L)
    }
    FluidFileRecents.get(compose.activity).clear()
  }

  @After fun stop() = FluidGlassDiagnostics.resetForTesting()

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      Snapshot.sendApplyNotifications()
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      left -= 16
    }
  }

  private fun waitFor(what: String, condition: () -> Boolean) {
    repeat(500) {
      if (condition()) return
      advance(32)
      Thread.sleep(4)
    }
    throw AssertionError("non è successo: $what")
  }

  private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  private fun capture(name: String): Bitmap {
    val window = compose.activity.window
    val image = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
    var result = -1
    compose.runOnIdle { PixelCopy.request(window, image, { result = it }, Handler(Looper.getMainLooper())) }
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals("PixelCopy", PixelCopy.SUCCESS, result)
    val dir = File("build/f8-18-sfoglia").apply { mkdirs() }
    File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return image
  }

  private fun luminance(image: Bitmap): Float {
    val p = image.getPixel(image.width / 2, image.height - 40)
    return 0.2126f * AndroidColor.red(p) + 0.7152f * AndroidColor.green(p) + 0.0722f * AndroidColor.blue(p)
  }

  private fun shoot(dark: Boolean) {
    val mode = if (dark) "scuro" else "chiaro"
    var layoutState: dev.antigravity.fluidengine.files.FluidFileBrowserState? = null
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
        dynamicColorEnabled = false) }) {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            val state = rememberFluidFileBrowserState().also { layoutState = it }
            val source = remember { FluidFileSources.appFolder(root, "app", "In Fluid Glass", "La cartella dell'app") }
            FluidFileBrowser(state = state, sources = listOf(source), locations = null)
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    advance(600)
    waitFor("Sfoglia") { has(FluidFileBrowserTags.location("app")) }
    advance(400)
    val browse = capture("sfoglia-$mode")
    if (dark) assertTrue(luminance(browse) < 80f) else assertTrue(luminance(browse) > 180f)
    compose.onNode(hasTestTag(FluidFileBrowserTags.location("app")), useUnmergedTree = true)
      .performSemanticsAction(SemanticsActions.OnClick)
    waitFor("la cartella") { has(FluidFileBrowserTags.item("Appunti di fisica.txt")) }
    advance(1500)
    capture("cartella-griglia-$mode")
    compose.onNode(hasContentDescription("Altro"), useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
    advance(600)
    capture("cartella-menu-$mode")
    compose.onNode(hasText("Elenco")).performSemanticsAction(SemanticsActions.OnClick)
    advance(800)
    assertEquals(FluidFileLayout.List, layoutState?.layout)
    capture("cartella-elenco-$mode")
  }

  @Test fun chiaro() = shoot(dark = false)

  @Test fun scuro() = shoot(dark = true)
}
