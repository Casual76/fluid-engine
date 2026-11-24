package dev.antigravity.fluidengine.sample.novita

import android.graphics.Bitmap
import android.graphics.RenderNode
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.time.Duration
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
 * F8.2b · i fermi della ruota per la tavola contro iOS 27, presi in Robolectric col vetro vero (verdetti degli shader
 * accettati) e PixelCopy, come `F6b3GlyphFramesTest`.
 *
 * Lo schermo e' quello dell'iPhone delle schermate di Apple: 393 dp di larghezza, a 420 dpi (2,625 px a dp, la scala di
 * `tavole.py`). La scena e' quella vera della galleria, «Il timer di Orologio» (`ruota-timer`), in chiaro e in scuro:
 * a riposo, e a meta' di un lancio dei minuti (le voci restano nitide: le muove il dito).
 *
 * I PNG vanno in `sample/build/reports/f8-2b/fermi/`: la finestra intera e il ritaglio della ruota.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-420dpi")
class F8p2bWheelFramesTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private val out = File("build/reports/f8-2b/fermi")
  private val view get() = compose.activity.window.decorView

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    compose.mainClock.autoAdvance = false
  }

  @After
  fun stop() {
    FluidGlassDiagnostics.resetForTesting()
  }

  private fun frame() {
    compose.mainClock.advanceTimeBy(16)
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    Snapshot.sendApplyNotifications()
  }

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      frame()
      left -= 16
    }
  }

  private val node = RenderNode("F8.2b")

  /** Disegna la finestra in un nodo, come F6B.3b: cosi' le sorgenti di vetro si registrano prima della copia. */
  private fun draw() = compose.runOnUiThread {
    node.setPosition(0, 0, view.width, view.height)
    val canvas = node.beginRecording()
    try {
      view.draw(canvas)
    } finally {
      node.endRecording()
    }
  }

  private fun window(): Bitmap {
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    compose.runOnIdle {
      PixelCopy.request(compose.activity.window, android.graphics.Rect(0, 0, view.width, view.height),
        bitmap, { assertEquals(PixelCopy.SUCCESS, it) }, Handler(Looper.getMainLooper()))
    }
    return bitmap
  }

  private fun save(image: Bitmap, crop: Rect, name: String) {
    out.mkdirs()
    File(out, "$name-finestra.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val left = crop.left.roundToInt().coerceIn(0, image.width - 1)
    val top = crop.top.roundToInt().coerceIn(0, image.height - 1)
    val width = crop.width.roundToInt().coerceAtMost(image.width - left)
    val height = crop.height.roundToInt().coerceAtMost(image.height - top)
    val cut = Bitmap.createBitmap(image, left, top, width, height)
    File(out, "$name.png").outputStream().use { cut.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  private fun shoot(dark: Boolean) {
    val theme = if (dark) "scuro" else "chiaro"
    compose.setContent {
      FluidTheme(
        settings = remember { EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, dynamicColorEnabled = false) },
      ) {
        NewElementsPageAt(onBack = {}, initial = "ruota-timer")
      }
    }
    advance(1500)
    val density = view.resources.displayMetrics.density
    assertEquals("420 dpi", 2.625f, density, 0.001f)
    val wheel = compose.onNodeWithTag(F8p2Tags.Wheel, useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
    // La ruota e' larga quanto lo schermo dell'iPhone, e le colonne stanno dentro i 45 dp della fascia di iOS 26.
    val hours = compose.onNode(hasContentDescription("Ore"), useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
    val seconds = compose.onNode(hasContentDescription("Secondi"), useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
    println("F8p2b $theme: ruota ${wheel.width / density} dp, ore da ${(hours.left - wheel.left) / density} dp, secondi fino a ${(wheel.right - seconds.right) / density} dp dal bordo")
    assertTrue("la ruota piena larghezza (${wheel.width / density} dp)", wheel.width / density > 340f)
    assertEquals("le ore cominciano dove comincia la fascia", 45f, (hours.left - wheel.left) / density, 1f)
    assertEquals("i secondi finiscono dove finisce la fascia", 45f, (wheel.right - seconds.right) / density, 1f)

    draw(); frame(); draw()
    val rest = window()
    save(rest, wheel, "ruota-timer-$theme-riposo")
    rest.recycle()

    // A meta' di un lancio dei minuti: le voci corrono e restano nitide.
    val finger = MaterialFinger(view)
    val x = wheel.center.x
    var y = wheel.center.y + 120f
    finger.down(Offset(x, y))
    repeat(6) {
      advance(16)
      y -= 60f
      finger.move(Offset(x, y))
    }
    finger.up(Offset(x, y))
    advance(160)
    draw(); frame(); draw()
    val flight = window()
    save(flight, wheel, "ruota-timer-$theme-lancio")
    flight.recycle()
    advance(6000)
  }

  @Test
  fun `la ruota del timer in scuro, ferma e lanciata`() = shoot(dark = true)

  @Test
  fun `la ruota del timer in chiaro, ferma e lanciata`() = shoot(dark = false)
}
