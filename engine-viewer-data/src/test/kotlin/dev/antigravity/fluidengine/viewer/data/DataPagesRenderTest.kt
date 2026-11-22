package dev.antigravity.fluidengine.viewer.data

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileViewer
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfoProvider
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.viewer.FluidViewerRegistry
import dev.antigravity.fluidengine.viewer.FluidViewerSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/**
 * F6C.13 · Ogni pagina della famiglia dentro il visore vero di engine-ui, in chiaro e in scuro, su Robolectric con la
 * grafica nativa: la composizione non cade, e i fermi restano in `build/reports/f6c-13/` per guardarli prima del tablet.
 * La WebView su Robolectric non c'è: le pagine web mostrano il loro ripiego.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h820dp-xhdpi")
class DataPagesRenderTest {
  private val out = File("build/reports/f6c-13").apply { mkdirs() }

  private fun file(name: String): File {
    val context = RuntimeEnvironment.getApplication()
    val dir = File(context.cacheDir, "pagine").apply { mkdirs() }
    val target = File(dir, name)
    requireNotNull(javaClass.classLoader!!.getResourceAsStream("data/$name")).use { input -> target.outputStream().use { input.copyTo(it) } }
    return target
  }

  private fun render(name: String, dark: Boolean) {
    val context = RuntimeEnvironment.getApplication()
    val document = runBlocking { FluidViewerRegistry.open(context, FluidViewerSource.of(file(name))) }
    val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    val activity = controller.get()
    try {
      activity.setContent {
        FluidTheme(settings = EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, dynamicColorEnabled = false)) {
          FluidViewerInfoProvider({ fluidDataViewerInfo(document) }) {
            FluidFileViewer(count = 1, content = { document.content }, onBack = {})
          }
        }
      }
      repeat(8) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)) }
      // Il CSV si conta in sottofondo: un attimo per il conto e per le celle.
      Thread.sleep(300)
      repeat(4) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)) }
      val view = activity.window.decorView
      val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
      PixelCopy.request(activity.window, android.graphics.Rect(0, 0, view.width, view.height), bitmap, { }, Handler(Looper.getMainLooper()))
      shadowOf(Looper.getMainLooper()).idle()
      File(out, "${name.replace('.', '-')}-${if (dark) "scuro" else "chiaro"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    } finally {
      controller.pause().stop().destroy()
      document.close()
    }
  }

  private val files = listOf(
    "nota.md", "pagina.html", "spese.csv", "config.json", "rotto.json", "eventi.ndjson", "catalogo.xml", "binario.plist",
    "libro3.epub", "protetto.epub", "colori.html", "messaggio.eml", "riunioni.ics", "contatti.vcf",
  )

  @Test fun everyPageLight() {
    for (name in files) render(name, dark = false)
    assertTrue(out.listFiles().orEmpty().size >= files.size)
  }

  @Test fun everyPageDark() {
    for (name in files) render(name, dark = true)
    assertTrue(out.listFiles().orEmpty().isNotEmpty())
  }
}
