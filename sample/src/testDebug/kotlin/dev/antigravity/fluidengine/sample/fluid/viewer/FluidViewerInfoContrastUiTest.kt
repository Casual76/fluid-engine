package dev.antigravity.fluidengine.sample.fluid.viewer

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.viewer.*
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h780dp-mdpi")
class FluidViewerInfoContrastUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val title = "Titolo WWW"
  private val fact = "Fatto MMM"

  @Test fun lightPhoto() = inspect(photo = true, dark = false)
  @Test fun darkPhoto() = inspect(photo = true, dark = true)
  @Test fun lightPdf() = inspect(photo = false, dark = false)
  @Test fun darkPdf() = inspect(photo = false, dark = true)

  private fun inspect(photo: Boolean, dark: Boolean) {
    val file = FluidFileInfo(if (photo) "lancio.jpg" else "quaderno.pdf")
    val info = FluidViewerInfo(file, title = title, facts = listOf(fact))
    compose.setContent {
      FluidTheme(EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
        val content = remember {
          if (photo) FluidViewerContent.Image(1.5f) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.DarkGray, Color.Black))))
          } else FluidViewerContent.Pages(object : FluidPageSource {
            override val count = 1
            override suspend fun size(index: Int) = IntSize(420, 595)
            override suspend fun render(index: Int, target: IntSize) =
              Bitmap.createBitmap(target.width, target.height, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.WHITE)
              }.asImageBitmap()
          })
        }
        FluidViewerInfoProvider({ info }) { FluidFileViewer(1, content = { content }) }
      }
    }
    compose.onNodeWithContentDescription("Informazioni").performClick()
    compose.waitForIdle()
    val sheet = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Informazioni"), useUnmergedTree = true)
    val medium = sheet.fetchSemanticsNode().boundsInRoot
    val trace = mutableListOf("frame,paneTop,titleRatio,factRatio,titleInkLuma,factInkLuma")
    val report = System.getenv("FLUID_VIEWER_CONTRAST")?.let { folder ->
      File(folder, "${if (photo) "foto" else "pdf"}-${if (dark) "scuro" else "chiaro"}.csv")
    }
    fun sample(frame: String): Pair<Sample, Sample> {
      val t = compose.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
      val f = compose.onNodeWithText(fact, useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
      val shot = pixels()
      try {
        val a = contrast(shot, t)
        val b = contrast(shot, f)
        val top = sheet.fetchSemanticsNode().boundsInRoot.top
        trace += "$frame,$top,${a.ratio},${b.ratio},${a.ink},${b.ink}"
        if (minOf(a.ratio, b.ratio) < 4.5f && report != null) {
          report.parentFile?.mkdirs()
          File(report.parentFile, "${report.nameWithoutExtension}-$frame.png").outputStream().use {
            shot.compress(Bitmap.CompressFormat.PNG, 100, it)
          }
        }
        assertTrue("photo=$photo dark=$dark frame=$frame title=$a ($t) fact=$b ($f)", minOf(a.ratio, b.ratio) >= 4.5f)
        return a to b
      } finally { shot.recycle() }
    }
    // The evidence run samples every frame. The normal suite also checks both settled detents and
    // the spring / delayed veil on each leg, without 396 PixelCopy requests in every sample build.
    fun travel(direction: String) {
      repeat(48) { frame ->
        compose.mainClock.advanceTimeByFrame()
        if (report != null || frame in setOf(3, 7, 11, 15, 20, 27, 35, 47)) sample("$direction-$frame")
      }
    }
    fun cycle() {
      val bounds = sheet.fetchSemanticsNode().boundsInRoot
      compose.onRoot().performTouchInput { click(Offset(bounds.center.x, bounds.top + 10f)) }
    }

    try {
      val initial = sample("Medium")
      if (photo) assertTrue("Foto a Medium: $initial", initial.first.lightInk && initial.second.lightInk)
      compose.mainClock.autoAdvance = false
      cycle()
      travel("up")
      assertTrue(sheet.fetchSemanticsNode().boundsInRoot.height > medium.height * 1.5f)
      val full = sample("Large")
      assertEquals("Titolo a Large", dark, full.first.lightInk)
      assertEquals("Capsula a Large", dark, full.second.lightInk)
      cycle()
      travel("down")
      assertEquals(medium.height, sheet.fetchSemanticsNode().boundsInRoot.height, 2f)
      sample("Medium-return")
    } finally {
      compose.mainClock.autoAdvance = true
      report?.parentFile?.mkdirs()
      report?.writeText(trace.joinToString("\n"))
    }
  }

  private data class Sample(val ratio: Float, val ink: Float, val lightInk: Boolean)

  /** Actual pixels inside the Text node, excluding padding, bordering glass and adjacent controls.
   * The median samples the background. The first/last percentile samples interiors of multiple glyphs. */
  private fun contrast(bitmap: Bitmap, bounds: Rect): Sample {
    val left = ceil(bounds.left).toInt().coerceAtLeast(0)
    val right = floor(bounds.right).toInt().coerceAtMost(bitmap.width)
    val top = ceil(bounds.top).toInt().coerceAtLeast(0)
    val bottom = floor(bounds.bottom).toInt().coerceAtMost(bitmap.height)
    require(right > left && bottom > top) { "Testo fuori dal frame: $bounds" }
    val pixels = IntArray((right - left) * (bottom - top))
    bitmap.getPixels(pixels, 0, right - left, left, top, right - left, bottom - top)
    val values = FloatArray(pixels.size) { Color(pixels[it]).luminance() }.apply { sort() }
    val bg = values[values.size / 2]
    val low = values[values.size / 100]
    val high = values[values.lastIndex - values.size / 100]
    val light = (high + .05f) / (bg + .05f)
    val shade = (bg + .05f) / (low + .05f)
    return if (light > shade) Sample(light, high, true) else Sample(shade, low, false)
  }

  private fun pixels(): Bitmap {
    val window = compose.activity.window
    val view = window.decorView
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    var result = PixelCopy.ERROR_UNKNOWN
    compose.runOnIdle {
      PixelCopy.request(window, android.graphics.Rect(0, 0, view.width, view.height), bitmap,
        { result = it }, Handler(Looper.getMainLooper()))
    }
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals(PixelCopy.SUCCESS, result)
    return bitmap
  }
}
