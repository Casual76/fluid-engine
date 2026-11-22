package dev.antigravity.fluidengine.sample

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.page.FluidBackSwipe
import dev.antigravity.fluidengine.ui.page.FluidPageHost
import dev.antigravity.fluidengine.ui.page.FluidPageHostState
import dev.antigravity.fluidengine.ui.page.rememberFluidPageHostState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w480dp-h720dp-mdpi")
class F6dPageVeilPixelsTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test fun lightPanel() = panel(false)
  @Test fun darkMirroredPanel() = panel(true)

  @Test fun annulloPredittivoConIlGestoreDellaPagina() {
    lateinit var pages: FluidPageHostState
    var handles by mutableStateOf(true)
    var handled = 0
    var popped = 0
    compose.setContent {
      pages = rememberFluidPageHostState()
      FluidPageHost(listOf(0, 1), onBack = { popped++ }, state = pages) { page ->
        if (page == 1) BackHandler(enabled = handles) { handled++ }
        Box(Modifier.fillMaxSize().background(if (page == 0) Color.White else Color.Magenta))
      }
    }
    val dispatcher = compose.activity.onBackPressedDispatcher
    fun event(x: Float) = BackEventCompat(x, 300f, .15f, BackEventCompat.EDGE_LEFT)
    for (active in listOf(true, false)) {
      compose.runOnIdle { handles = active }
      compose.runOnIdle {
        dispatcher.dispatchOnBackStarted(event(1f))
        dispatcher.dispatchOnBackProgressed(event(240f))
      }
      if (!active) assertEquals(.5f, pages.progress, .01f)
      compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
      compose.waitForIdle()
      assertEquals(0, popped)
      assertEquals(0, handled)
      assertTrue(!pages.isMoving)
    }
    compose.runOnIdle { handles = true }
    compose.runOnIdle { dispatcher.onBackPressed() }
    assertEquals(1, handled)
    assertEquals(0, popped)
  }

  private fun panel(dark: Boolean) {
    val level = if (dark) 96 else 240
    val base = Color(level, level, level)
    var stack by mutableStateOf(listOf(0))
    lateinit var pages: FluidPageHostState
    compose.setContent {
      MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Box(Modifier.fillMaxSize().background(Color.Green)) {
          Box(Modifier.offset(40.dp, 60.dp).size(400.dp, 480.dp)) {
            CompositionLocalProvider(LocalLayoutDirection provides if (dark) LayoutDirection.Rtl else LayoutDirection.Ltr) {
              pages = rememberFluidPageHostState()
              FluidPageHost(stack, onBack = { stack = stack.dropLast(1) }, state = pages,
                backSwipe = { FluidBackSwipe.Anywhere }) { key ->
                Box(Modifier.fillMaxSize().background(if (key == 0) base else Color.Magenta)) {
                  if (key == 1) {
                    Box(Modifier.offset(y = 100.dp).fillMaxWidth().height(10.dp).background(Color.White))
                    Box(Modifier.offset(y = 110.dp).fillMaxWidth().height(10.dp).background(Color.Black))
                  }
                }
              }
            }
          }
        }
      }
    }
    fun capture(label: String): Bitmap {
      val window = compose.activity.window
      val image = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
      compose.runOnIdle {
        PixelCopy.request(window, image, { assertEquals(PixelCopy.SUCCESS, it) }, Handler(Looper.getMainLooper()))
      }
      System.getenv("GLASS_F6D_PIXELS")?.let { path ->
        val dir = File(path).apply { mkdirs() }
        File(dir, "${if (dark) "scuro-rtl" else "chiaro-ltr"}-$label.png").outputStream().use {
          image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
      }
      return image
    }
    val start = capture("riposo")
    compose.runOnIdle { stack = listOf(0, 1) }
    val front = capture("fronte")
    val whiteY = (100..280).first { front.getPixel(240, it) == android.graphics.Color.WHITE }
    val root = compose.onRoot()
    val sign = if (dark) -1 else 1
    root.performTouchInput {
      down(Offset(if (dark) 430f else 50f, 280f))
      repeat(5) { moveBy(Offset(sign * 20f, 0f)) }
    }
    val nearStart = if (dark) 408 else 72
    val nearEnd = if (dark) 80 else 400
    assertEquals(level, android.graphics.Color.red(start.getPixel(240, 300)))
    fun check(label: String, fraction: Float) {
      val held = capture(label)
      assertEquals(fraction, pages.progress, 0.015f)
      val far = android.graphics.Color.red(held.getPixel(nearStart, 300))
      assertEquals(level * (1f - .12f * (1f-fraction)), far.toFloat(), 1.5f)
      assertEquals(android.graphics.Color.MAGENTA, held.getPixel(nearEnd, 300))
      assertEquals(android.graphics.Color.WHITE, held.getPixel(nearEnd, whiteY+3))
      assertEquals(android.graphics.Color.BLACK, held.getPixel(nearEnd, whiteY+13))
      assertEquals(start.getPixel(25, 300), held.getPixel(25, 300))
      val edge = if (dark) 440-(400*fraction).toInt() else 40+(400*fraction).toInt()
      fun extra(d: Int): Float {
        val x = if (dark) edge+d-1 else edge-d
        return 1f - android.graphics.Color.red(held.getPixel(x, 300)).toFloat() / far
      }
      assertTrue("ombra a 4 px: ${extra(4)}", extra(4) in .008f.. .034f)
      assertTrue("ombra a 3% di W: ${extra(12)}", extra(12) <= .012f)
    }
    check("quarto", .25f)
    root.performTouchInput { repeat(5) { moveBy(Offset(sign*20f, 0f)) } }
    check("meta", .5f)
    root.performTouchInput { repeat(5) { moveBy(Offset(sign*20f, 0f)) } }
    check("tre-quarti", .75f)
    root.performTouchInput { repeat(5) { moveBy(Offset(-sign*20f, 0f)) } }
    check("inversione", .5f)
    root.performTouchInput { cancel() }
    val end = capture("annullo")
    assertTrue(stack == listOf(0, 1))
    assertEquals(android.graphics.Color.MAGENTA, end.getPixel(240, 300))
  }
}
