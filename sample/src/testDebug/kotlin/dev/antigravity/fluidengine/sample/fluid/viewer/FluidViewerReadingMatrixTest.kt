package dev.antigravity.fluidengine.sample.fluid.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import org.robolectric.Shadows.shadowOf
import java.io.File
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import dev.antigravity.fluidengine.ui.fluid.LocalFluidReduceTransparency
import dev.antigravity.fluidengine.ui.fluid.LocalFluidSystemContrast
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.ui.fluid.viewer.*
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.viewer.FluidTextDecoder
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class FluidViewerReadingMatrixTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test fun phone() = verify("telefono")
  @Config(sdk = [35], qualifiers = "w1000dp-h800dp-mdpi")
  @Test fun tablet() = verify("tablet")

  private fun verify(device: String) {
    val resources = compose.activity.resources
    fun text(resource: Int, name: String) = resources.openRawResource(resource).use {
      FluidViewerContent.Text(requireNotNull(FluidTextDecoder.read(it, name)))
    }
    val utf16 = text(R.raw.viewer_uc_lettera, "lettera.txt")
    val code = text(R.raw.viewer_codice, "Visore.kt")
    val cover = BitmapFactory.decodeResource(resources, R.drawable.viewer_flower).asImageBitmap()
    val session = object : FluidMediaSession {
      override val playing = false
      override val positionMillis = 0L
      override val durationMillis = 12_000L
      override val canSeek = true
      override fun play() {}
      override fun pause() {}
      override fun seekTo(positionMillis: Long) {}
    }
    fun pages(pageCount: Int) = FluidViewerContent.Pages(object : FluidPageSource {
      override val count = pageCount
      override suspend fun size(index: Int) = if (index % 3 == 2) IntSize(595, 420) else IntSize(420, 595)
      override suspend fun render(index: Int, target: IntSize) =
        Bitmap.createBitmap(target.width, target.height, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }.asImageBitmap()
    })
    val audio = FluidViewerContent.Media(session)
    val files = listOf(audio, audio, utf16, code, pages(1), pages(6))
    var current by mutableIntStateOf(0)
    var scale by mutableFloatStateOf(1f)
    var dark by mutableStateOf(false)
    compose.setContent {
      val density = LocalDensity.current.density
      CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
        FluidTheme(EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
          key(current) {
            val names = listOf("Con copertina.m4a", "Senza tag.m4a", "lettera.txt", "Visore.kt", "Singolo.pdf", "Misto.pdf")
            val details = FluidViewerInfo(FluidFileInfo(names[current]), thumbnail = cover.takeIf { current == 0 })
            FluidViewerInfoProvider({ details }) {
              FluidFileViewer(1, content = { files[current] }, modifier = Modifier.fillMaxSize())
            }
          }
        }
      }
    }
    for (night in listOf(false, true)) for (font in listOf(1f, 2f)) {
      for (page in files.indices) {
        compose.runOnIdle { dark = night; scale = font; current = page }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Informazioni").assertIsDisplayed()
        when (page) {
          0, 1 -> compose.onNodeWithContentDescription("Riproduci").performScrollTo().assertIsDisplayed()
          2, 3 -> {
            compose.onNodeWithContentDescription("Trova").assertIsDisplayed()
            compose.onNodeWithContentDescription("A capo").assertIsDisplayed()
          }
          else -> {
            val count = if (page == 4) 1 else 6
            compose.onNodeWithContentDescription("Miniature delle pagine").assertIsDisplayed()
              .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                "Pagina 1 di $count"))
          }
        }
        val out = System.getenv("FLUID_VIEWER_MATRIX")
        if (out != null) capture(File(out, "$device/${if (night) "scuro" else "chiaro"}/$font/$page.png"))
      }
    }
  }

  @Test fun highContrastAndReducedTransparencyRemoveTheFileWash() {
    var reduced by mutableStateOf(false)
    var contrast by mutableFloatStateOf(0f)
    val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
    val info = FluidViewerInfo(FluidFileInfo("colore.bin"), thumbnail = bitmap.asImageBitmap())
    compose.setContent {
      FluidTheme(EngineSettings(themeMode = ThemeMode.LIGHT)) {
        CompositionLocalProvider(LocalFluidReduceTransparency provides reduced, LocalFluidSystemContrast provides contrast) {
          FluidViewerInfoProvider({ info }) { FluidFileViewer(1, content = { FluidViewerContent.Unknown(info.file) }) }
        }
      }
    }
    fun colour() = pixels { it.getPixel(3, it.height / 3) }
    fun redness(pixel: Int) = android.graphics.Color.red(pixel) - android.graphics.Color.green(pixel)
    compose.waitUntil(10_000) { redness(colour()) > 5 }
    val tinted = colour()
    compose.runOnIdle { reduced = true }
    val solid = colour()
    compose.runOnIdle { reduced = false; contrast = 1f }
    val high = colour()
    assertTrue("Tinta $tinted, riduzione $solid", redness(tinted) > redness(solid))
    assertEquals(redness(solid), redness(high))
  }

  private fun capture(file: File) = pixels { bitmap ->
    file.parentFile?.mkdirs()
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    Unit
  }

  private fun <T> pixels(read: (Bitmap) -> T): T {
    val window = compose.activity.window
    val view = window.decorView
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    try {
      var result = PixelCopy.ERROR_UNKNOWN
      compose.runOnIdle {
        PixelCopy.request(window, android.graphics.Rect(0, 0, view.width, view.height), bitmap,
          { result = it }, Handler(Looper.getMainLooper()))
      }
      shadowOf(Looper.getMainLooper()).idle()
      assertEquals(PixelCopy.SUCCESS, result)
      return read(bitmap)
    } finally { bitmap.recycle() }
  }
}
