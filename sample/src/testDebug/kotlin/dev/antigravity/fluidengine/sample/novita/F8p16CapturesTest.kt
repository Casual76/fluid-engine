package dev.antigravity.fluidengine.sample.novita

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ink.FluidInkEditor
import dev.antigravity.fluidengine.ink.FluidInkInput
import dev.antigravity.fluidengine.ink.FluidInkPointsBuilder
import dev.antigravity.fluidengine.ink.FluidInkSheet
import dev.antigravity.fluidengine.ink.FluidInkState
import dev.antigravity.fluidengine.ink.FluidInkStroke
import dev.antigravity.fluidengine.ink.FluidInkTool
import dev.antigravity.fluidengine.ink.ready
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.time.Duration
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
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
 * F8.16 · Le catture dell'editor col vetro vero, in chiaro e in scuro, nel formato telefono (411 × 891 dp): la foto
 * annotata e lo schizzo con le forme. Vanno in `sample/build/f8p16-catture/`. Sul computer i tratti li disegna il ripiego
 * (androidx.ink non ha la libreria nativa per Windows): l'aspetto vero della penna si guarda sul tablet.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class F8p16CapturesTest {
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
    val dir = File("build/f8p16-catture").apply { mkdirs() }
    File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return image
  }

  private fun count(image: Bitmap, test: (Int) -> Boolean): Int {
    val pixels = IntArray(image.width * image.height)
    image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
    return pixels.count(test)
  }

  private fun curve(state: FluidInkState, tool: FluidInkTool, color: Color, size: Float, cx: Float, cy: Float, w: Float, h: Float, stylus: Boolean): FluidInkStroke {
    val b = FluidInkPointsBuilder()
    for (i in 0..60) {
      val t = i / 60f
      b.add(cx - w / 2 + w * t, cy + h * sin(t * 2 * PI).toFloat(), i * 8f, if (stylus) 0.3f + 0.6f * sin(t * PI).toFloat() else -1f)
    }
    return FluidInkStroke(state.newStrokeId(), tool, color, size, b.build(), if (stylus) FluidInkInput.Stylus else FluidInkInput.Touch)
  }

  private fun circle(state: FluidInkState, cx: Float, cy: Float, r: Float, color: Color): FluidInkStroke {
    val b = FluidInkPointsBuilder()
    for (i in 0..72) b.add(cx + r * cos(i * 2 * PI / 72).toFloat(), cy + r * sin(i * 2 * PI / 72).toFloat(), i * 6f)
    return FluidInkStroke(state.newStrokeId(), FluidInkTool.Pen, color, 3f, b.build(), FluidInkInput.Synthetic)
  }

  private fun photo(dark: Boolean, name: String) {
    val bitmap = BitmapFactory.decodeResource(compose.activity.resources, if (dark) R.drawable.viewer_astronaut else R.drawable.viewer_flower)
    val state = FluidInkState()
    val sheet = FluidInkSheet.image(bitmap.asImageBitmap(), IntSize(bitmap.width, bitmap.height))
    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()
    state.add(curve(state, FluidInkTool.Pen, Color(0xFFFF3B30), w / 90f, w * 0.5f, h * 0.35f, w * 0.6f, h * 0.06f, stylus = true))
    state.add(curve(state, FluidInkTool.Highlighter, Color(0xFFFFCC00).copy(alpha = 0.5f), w / 25f, w * 0.5f, h * 0.55f, w * 0.7f, 0f, stylus = false))
    state.add(curve(state, FluidInkTool.Pencil, if (dark) Color.White else Color.Black, w / 160f, w * 0.5f, h * 0.7f, w * 0.5f, h * 0.04f, stylus = true))
    show(dark) { FluidInkEditor(state, sheet.ready(), onCancel = {}, onDone = {}, title = "Annota la foto") }
    val image = capture(name)
    // La foto, i tratti e la tavolozza si vedono: i pixel rossi della penna ci sono.
    assertTrue(count(image) { c -> android.graphics.Color.red(c) > 200 && android.graphics.Color.green(c) < 90 && android.graphics.Color.blue(c) < 90 } > 50)
  }

  @Test fun laFotoAnnotataInChiaro() = photo(dark = false, name = "foto-chiaro")

  @Test fun laFotoAnnotataInScuro() = photo(dark = true, name = "foto-scuro")

  private fun sketch(dark: Boolean, name: String) {
    val state = FluidInkState()
    val sheet = FluidInkSheet.blank(Size(390f, 700f))
    state.add(circle(state, 120f, 180f, 70f, Color(0xFF007AFF)))
    state.add(curve(state, FluidInkTool.Pen, Color.Black, 3.5f, 200f, 360f, 280f, 30f, stylus = true))
    state.add(curve(state, FluidInkTool.Highlighter, Color(0xFF34C759).copy(alpha = 0.5f), 18f, 200f, 450f, 260f, 0f, stylus = false))
    state.rulerVisible = true
    show(dark) { FluidInkEditor(state, sheet.ready(), onCancel = {}, onDone = {}, title = "Schizzo") }
    val image = capture(name)
    assertTrue(count(image) { c -> android.graphics.Color.blue(c) > 200 && android.graphics.Color.red(c) < 60 } > 50)
  }

  @Test fun loSchizzoInChiaro() = sketch(dark = false, name = "schizzo-chiaro")

  @Test fun loSchizzoInScuro() = sketch(dark = true, name = "schizzo-scuro")
}
