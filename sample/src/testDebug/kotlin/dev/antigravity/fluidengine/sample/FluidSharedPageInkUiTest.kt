package dev.antigravity.fluidengine.sample

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidPaneLayout
import dev.antigravity.fluidengine.ui.fluid.FluidPaneRole
import dev.antigravity.fluidengine.ui.fluid.FluidPaneScaffold
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.page.FluidGrowingPagePill
import dev.antigravity.fluidengine.ui.page.fluidGrowingPageChrome
import dev.antigravity.fluidengine.ui.page.growingGlass
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.fluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.page.FluidPageDismiss
import dev.antigravity.fluidengine.ui.page.FluidPageHost
import dev.antigravity.fluidengine.ui.page.FluidPageHostState
import dev.antigravity.fluidengine.ui.page.FluidPageTransition
import dev.antigravity.fluidengine.ui.page.fluidOpenGesture
import dev.antigravity.fluidengine.ui.page.fluidSharedElement
import dev.antigravity.fluidengine.ui.page.rememberFluidPageHostState
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Misura nelle copie della finestra ogni pixel della copertina e delle due facce. Le coordinate
 * assolute includono l'offset del pannello e le decorazioni della finestra. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h720dp-mdpi")
class FluidSharedPageInkUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private fun shot(name: String? = null): Bitmap {
    val window = compose.activity.window
    val bitmap = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
    compose.runOnIdle {
      PixelCopy.request(window, bitmap, { assertEquals(PixelCopy.SUCCESS, it) }, Handler(Looper.getMainLooper()))
    }
    val traceDir = System.getenv("GLASS_F6B5B_DIR")
    if (name != null && !traceDir.isNullOrBlank()) {
      val folder = java.io.File(java.io.File(traceDir).parentFile, "pixelcopy")
      folder.mkdirs()
      java.io.File(folder, name + ".png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    return bitmap
  }

  private data class Ink(val x: Float, val y: Float, val w: Int, val h: Int, val count: Int)
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
      left = minOf(left, x)
      top = minOf(top, y)
      right = maxOf(right, x)
      bottom = maxOf(bottom, y)
    }
    return Ink(left.toFloat(), top.toFloat(), right - left + 1, bottom - top + 1, n)
  }

  @Test fun cresceArrivaAlBordoDelDettaglioConIlFondoOpacoSoloAlRilascio() {
    lateinit var pages: FluidPageHostState
    val pane = FluidPaneLayout(listOf(FluidPaneRole.Side, FluidPaneRole.Detail), false, 0.dp, 80.dp, 0.dp, 320.dp, true)
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = false)
    try {
      compose.setContent {
        FluidPaneScaffold(pane, list = {}, side = { Box(Modifier.fillMaxSize().background(Color.Green)) }, detail = {
          var stack by remember { mutableStateOf(listOf("lista")) }
          pages = rememberFluidPageHostState()
          val ns = rememberFluidGlassNamespace()
          val source = remember(ns) { FluidTransitionSource("pillola", ns).growingGlass() }
          FluidPageHost(stack, { stack = stack.dropLast(1) }, state = pages, pageColor = Color.White,
            transition = { FluidPageTransition.Expand }, zoomSource = { source }, dismiss = { FluidPageDismiss.Down }) { page ->
            if (page == "lista") {
              val backdrop = rememberGlassBackdrop()
              Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).background(Color.Cyan))
                FluidGrowingPagePill("pillola", ns, backdrop, Modifier.align(Alignment.BottomCenter).size(190.dp, 60.dp)
                  .fluidOpenGesture { stack = stack + "pagina" }) {
                  Box(Modifier.align(Alignment.Center).size(50.dp, 8.dp).background(Color.Blue))
                }
              }
            } else Box(Modifier.fillMaxSize().background(Color.Magenta)) {
              Box(Modifier.align(Alignment.Center).size(50.dp, 20.dp).fluidGrowingPageChrome().background(Color.Red))
            }
          }
        })
      }
      val root = compose.onRoot()
      val start = shot("pannello-riposo")
      val side = start.ink(android.graphics.Color.GREEN)
      val hit = start.ink(android.graphics.Color.BLUE)
      val top = side.y
      // Coordinate della radice Compose, a partire dalla finestra PixelCopy.
      root.performTouchInput {
        down(Offset(hit.x + hit.w / 2, hit.y - top + hit.h / 2))
        repeat(12) { moveBy(Offset(0f, -28f)) }
      }
      compose.waitForIdle()
      assertTrue(pages.isInteractive)
      assertEquals(0, shot().ink(android.graphics.Color.MAGENTA).count)
      root.performTouchInput { repeat(10) { moveBy(Offset(0f, -24f)) } }
      compose.waitForIdle()
      val held = shot("pannello-in-volo")
      assertTrue(pages.fractionToward { it == "pagina" } > 0.72f)
      assertEquals(0, held.ink(android.graphics.Color.MAGENTA).count)
      assertEquals(side, held.ink(android.graphics.Color.GREEN))
      root.performTouchInput { up() }
      compose.waitForIdle()
      val end = shot("pannello-posato")
      assertEquals(side, end.ink(android.graphics.Color.GREEN))
      assertEquals(side.x + side.w, end.ink(android.graphics.Color.MAGENTA).x, 1f)
      assertEquals(320, end.ink(android.graphics.Color.MAGENTA).w)
      assertTrue(end.ink(android.graphics.Color.RED).count > 0)
    } finally {
      FluidGlassDiagnostics.resetForTesting()
    }
  }

  @Test fun ilDitoPortaUnaSolaCopertinaTraIRiquadriVeriNelPannello() {
    lateinit var pages: FluidPageHostState
    compose.setContent {
      var stack by remember { mutableStateOf(listOf("lista")) }
      pages = rememberFluidPageHostState()
      val ns = rememberFluidGlassNamespace()
      val origin = remember(ns) { FluidTransitionSource("card", ns) }
      Box(Modifier.fillMaxSize().background(Color.LightGray)) {
        FluidPageHost(stack, onBack = { stack = stack.dropLast(1) }, state = pages,
          modifier = Modifier.offset(37.dp, 20.dp).size(280.dp, 420.dp), pageColor = Color.White,
          transition = { FluidPageTransition.Expand }, zoomSource = { origin }, dismiss = { FluidPageDismiss.Down }) { page ->
          if (page == "lista") {
            Box(Modifier.offset(40.dp, 315.dp).size(180.dp, 50.dp)
              .fluidTransitionSource("card", ns).fluidOpenGesture { stack = stack + "pagina" }.background(Color.Yellow)) {
              Box(Modifier.offset(8.dp, 8.dp).size(28.dp).fluidSharedElement("album", ns).background(Color.Magenta))
              Box(Modifier.offset(55.dp, 18.dp).size(65.dp, 12.dp).background(Color.Blue))
            }
          } else {
            Box(Modifier.offset(34.dp, 42.dp).size(160.dp).fluidSharedElement("album", ns).background(Color.Magenta))
            Box(Modifier.offset(30.dp, 280.dp).size(130.dp, 24.dp).background(Color.Red))
          }
        }
      }
    }
    val magenta = android.graphics.Color.MAGENTA
    val start = shot("copertina-riposo").ink(magenta)
    assertEquals(28, start.w)
    val root = compose.onRoot()
    root.performTouchInput { down(Offset(100f, 360f)); repeat(5) { moveBy(Offset(0f, -25f)) } }
    compose.waitForIdle()
    var sample = 0
    fun checkFrame(): Pair<Float, Ink> {
      val frame = shot("copertina-dito-" + ++sample)
      val p = pages.fractionToward { it == "pagina" }
      val art = frame.ink(magenta)
      val expectedSize = 28 + (160 - 28) * p
      assertTrue("p=" + p + ", atteso " + expectedSize + ", trovato " + art, abs(expectedSize - art.w) <= 3)
      assertTrue("una copertina piena " + art, art.count >= (art.w - 2) * (art.h - 2))
      assertTrue("la chrome si vede solo per ultima", p > 0.7f || frame.ink(android.graphics.Color.RED).count == 0)
      assertTrue("la sorgente è uscita", p < 0.3f || frame.ink(android.graphics.Color.BLUE).count == 0)
      return p to art
    }
    val samples = mutableListOf(checkFrame())
    root.performTouchInput { repeat(3) { moveBy(Offset(0f, -28f)) } }
    samples += checkFrame()
    root.performTouchInput { repeat(3) { moveBy(Offset(0f, 22f)) } }
    samples += checkFrame()
    assertTrue(samples[0].first < samples[1].first && samples[2].first < samples[1].first)
    root.performTouchInput { repeat(5) { moveBy(Offset(0f, -32f)) }; up() }
    compose.waitForIdle()
    val end = shot("copertina-posata").ink(magenta)
    assertEquals(160, end.w)
    for ((p, art) in samples) {
      assertEquals(start.x + (end.x - start.x) * p, art.x, 3f)
      assertEquals(start.y + (end.y - start.y) * p, art.y, 3f)
    }
  }
}
