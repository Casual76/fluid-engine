package dev.antigravity.fluidengine.sample.novita

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.antigravity.fluidengine.editor.FluidAdjustment
import dev.antigravity.fluidengine.editor.FluidCropAspect
import dev.antigravity.fluidengine.editor.FluidEditorMode
import dev.antigravity.fluidengine.editor.FluidEditorSource
import dev.antigravity.fluidengine.editor.FluidImageEditor
import dev.antigravity.fluidengine.editor.FluidImageEditorState
import dev.antigravity.fluidengine.editor.rememberFluidImageEditorState
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.ui.fluid.FluidBeforeAfter
import dev.antigravity.fluidengine.ui.fluid.FluidBeforeAfterLabels
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.time.Duration
import androidx.compose.runtime.CompositionLocalProvider
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
 * F8.15 · Le catture dell'editor col vetro vero, in chiaro e in scuro, nel formato di un iPhone di 393 × 852 punti (a 3
 * pixel per punto, come le schermate di Foto della guida dell'iPhone ridotte alla stessa scala): per guardarle prima del
 * tablet e per le tavole accanto a Foto. Le immagini vanno in `sample/build/f8p15-catture/`; la prova controlla che la
 * foto sia disegnata e che le regolazioni la cambino.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class F8p15CapturesTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var state: FluidImageEditorState

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
      FluidTheme(settings = remember { EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, dynamicColorEnabled = false) }) {
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
    // Il vetro tinto di «Fine» aspetta la sua cattura dello sfondo: qualche fotogramma in piu'.
    advance(1_600)
    val window = compose.activity.window
    val view = window.decorView
    val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    var result = -1
    PixelCopy.request(window, image, { result = it }, Handler(Looper.getMainLooper()))
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals("PixelCopy", PixelCopy.SUCCESS, result)
    val dir = File("build/f8p15-catture").apply { mkdirs() }
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

  private fun photo(id: Int = R.drawable.viewer_flower): Bitmap = BitmapFactory.decodeResource(compose.activity.resources, id)

  private fun editor(dark: Boolean, id: Int = R.drawable.viewer_flower, setup: FluidImageEditorState.() -> Unit = {}) {
    val bitmap = photo(id)
    show(dark) {
      state = rememberFluidImageEditorState(FluidEditorSource.bitmap(bitmap))
      LaunchedEffect(state) {
        snapshotFlow { state.isReady }.collect { if (it) state.setup() }
      }
      FluidImageEditor(state, onCancel = {}, onDone = {})
    }
  }

  private fun brightness(image: Bitmap): Double {
    var sum = 0L
    val step = 7
    var n = 0
    for (y in image.height / 5 until image.height * 3 / 5 step step) for (x in 0 until image.width step step) {
      val c = image.getPixel(x, y)
      sum += android.graphics.Color.red(c) + android.graphics.Color.green(c) + android.graphics.Color.blue(c)
      n++
    }
    return sum.toDouble() / n
  }

  @Test fun regolaInChiaro() {
    editor(dark = false)
    val plain = capture("regola-chiaro-neutro")
    assertTrue(colorful(plain) > plain.width * plain.height / 30)
    compose.runOnIdle {
      state.setAdjustment(FluidAdjustment.Exposure, 35f)
      state.commit()
    }
    val brighter = capture("regola-chiaro")
    assertTrue("l'esposizione schiarisce: ${brightness(plain)} -> ${brightness(brighter)}", brightness(brighter) > brightness(plain) + 10)
  }

  @Test fun regolaInScuro() {
    editor(dark = true) {
      setAdjustment(FluidAdjustment.Exposure, 35f)
      commit()
    }
    assertTrue(colorful(capture("regola-scuro")) > 0)
  }

  private fun crop(dark: Boolean) {
    editor(dark, R.drawable.viewer_rocket) {
      mode = FluidEditorMode.Crop
      setStraighten(8f)
      commit()
    }
    assertTrue(colorful(capture(if (dark) "ritaglia-scuro" else "ritaglia-chiaro")) > 0)
  }

  @Test fun ritagliaInChiaro() = crop(false)

  @Test fun ritagliaInScuro() = crop(true)

  @Test fun leProporzioniEIlTondo() {
    editor(false, R.drawable.viewer_astronaut) {
      mode = FluidEditorMode.Crop
      aspectPanel = true
      setAspect(FluidCropAspect.Circle)
    }
    capture("proporzioni-tondo")
  }

  private fun split(dark: Boolean) {
    val before = photo(R.drawable.viewer_rocket)
    val bytes = java.io.ByteArrayOutputStream().also { before.compress(Bitmap.CompressFormat.JPEG, 12, it) }.toByteArray()
    val after = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    show(dark) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().aspectRatio(before.width.toFloat() / before.height)) {
          FluidBeforeAfter(
            before = { Image(before.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) },
            after = { Image(after.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) },
            labels = FluidBeforeAfterLabels(before = "Originale", after = "JPEG 12"),
          )
        }
      }
    }
    assertTrue(colorful(capture(if (dark) "tendina-scuro" else "tendina-chiaro")) > 0)
  }

  @Test fun laTendinaInChiaro() = split(false)

  @Test fun laTendinaInScuro() = split(true)
}
