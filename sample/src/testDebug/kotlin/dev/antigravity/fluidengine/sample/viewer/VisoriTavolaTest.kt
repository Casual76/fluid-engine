package dev.antigravity.fluidengine.sample.viewer

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performSemanticsAction
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.ViewerGallery
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * F6C.17 · La tavola delle tessere per Alessio: la galleria «Visori» nel formato del telefono (1080 × 2340, 420 dpi), in
 * chiaro e in scuro, schermata per schermata dall'alto fino a «Moduli e pesi». Usa solo ciò che c'era già in F6C.15,
 * così la stessa prova gira sul commit di partenza (il «prima») e su questo (il «dopo»). Le schermate vanno in
 * `build/visori-tavola/<tema>-<n>.png`.
 *
 * Costa tre minuti: gira solo con `VISORI_TAVOLA=1` nell'ambiente della build, e nel lotto risulta saltata.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class VisoriTavolaTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val output = File("build/visori-tavola").also { it.mkdirs() }

  @Test @Config(qualifiers = "w411dp-h891dp-420dpi") fun tavolaChiara() = capture("chiaro")

  @Test @Config(qualifiers = "w411dp-h891dp-night-420dpi") fun tavolaScura() = capture("scuro")

  private fun capture(theme: String) {
    assumeTrue("La tavola si cattura solo con VISORI_TAVOLA=1", System.getenv("VISORI_TAVOLA") == "1")
    compose.setContent { FluidTheme(settings = remember { EngineSettings() }) { ViewerGallery {} } }
    settle()
    var step = 0
    while (step < 14) {
      window("$theme-${"%02d".format(step)}")
      if (compose.onAllNodes(androidx.compose.ui.test.hasTestTag("visori-senza")).fetchSemanticsNodes().isNotEmpty()) break
      val height = compose.activity.window.decorView.height
      compose.onNode(hasScrollToNodeAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, height * 0.62f) }
      settle()
      step++
    }
    assertTrue("Almeno tre schermate", step >= 2)
  }

  /** Lascia arrivare le miniature (si leggono fuori dal filo principale) e le animazioni. */
  private fun settle() {
    repeat(6) {
      Thread.sleep(400)
      compose.waitForIdle()
    }
  }

  private fun window(name: String) {
    val window = compose.activity.window
    val view = window.decorView
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    var code = PixelCopy.ERROR_UNKNOWN
    compose.runOnIdle { PixelCopy.request(window, bitmap, { code = it }, Handler(Looper.getMainLooper())) }
    shadowOf(Looper.getMainLooper()).idle()
    assertTrue("PixelCopy $code", code == PixelCopy.SUCCESS)
    File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }
}
