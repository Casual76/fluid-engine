package dev.antigravity.fluidengine.sample.novita

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.media.FluidMediaPicker
import dev.antigravity.fluidengine.media.FluidMediaRequest
import dev.antigravity.fluidengine.media.rememberFluidMediaPickerState
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
 * F8.14 · Le catture del selettore col vetro vero, in chiaro e in scuro, nel formato telefono (411 × 891 dp): per
 * guardarle prima del tablet e per le tavole accanto ad Apple. Le immagini vanno in `sample/build/f8p14-catture/`; la
 * prova controlla solo che la pagina sia disegnata (le foto di prova hanno colore).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class F8p14CapturesTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    compose.mainClock.autoAdvance = false
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

  private fun show(dark: Boolean, content: @Composable () -> Unit) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) }) {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            content()
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    advance(1_500)
  }

  private fun capture(name: String): Bitmap {
    advance(600)
    val window = compose.activity.window
    val view = window.decorView
    val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    var result = -1
    PixelCopy.request(window, image, { result = it }, Handler(Looper.getMainLooper()))
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals("PixelCopy", PixelCopy.SUCCESS, result)
    val dir = File("build/f8p14-catture").apply { mkdirs() }
    File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return image
  }

  private fun colorful(image: Bitmap): Int {
    val pixels = IntArray(image.width * image.height)
    image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
    return pixels.count { c ->
      val r = android.graphics.Color.red(c)
      val g = android.graphics.Color.green(c)
      val b = android.graphics.Color.blue(c)
      maxOf(r, g, b) - minOf(r, g, b) > 60
    }
  }

  private fun picker(dark: Boolean, name: String) {
    val sources = F8p14Sources(compose.activity)
    show(dark) {
      val state = rememberFluidMediaPickerState(sources.all, FluidMediaRequest.attachments(10))
      LaunchedEffect(state) { listOf("m004", "m000", "m009").forEach { state.selection.toggle("device/$it") } }
      FluidMediaPicker(state, onResult = {}, onCancel = {}, offerSystemPicker = false)
    }
    val image = capture(name)
    assertTrue("le foto di prova si vedono", colorful(image) > image.width * image.height / 20)
  }

  @Test fun ilSelettoreInChiaro() = picker(dark = false, name = "selettore-chiaro")

  @Test fun ilSelettoreInScuro() = picker(dark = true, name = "selettore-scuro")

  @Test fun laSchedaDelPiu() {
    show(dark = false) { NewElementsPageAt(onBack = {}, initial = "allegati-piu") }
    val plus = compose.onAllNodes(androidx.compose.ui.test.hasTestTag(F8p14Tags.Plus), useUnmergedTree = true)
      .fetchSemanticsNodes().first().boundsInRoot.center
    val view = compose.activity.window.decorView
    val finger = dev.antigravity.fluidengine.sample.material.MaterialFinger(view)
    finger.down(plus)
    advance(48)
    finger.up(plus)
    advance(1_200)
    val image = capture("piu-chiaro")
    assertTrue(colorful(image) > 0)
  }
}
