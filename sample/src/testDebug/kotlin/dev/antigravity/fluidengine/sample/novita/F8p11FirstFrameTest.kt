package dev.antigravity.fluidengine.sample.novita

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.sample.GalleryActivity
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F8.11 · Il primo fotogramma della galleria vera: la finestra ha il colore della pagina prima che Compose disegni (dal
 * tema `Theme.Fluid`, chiaro o scuro, o dal suggerimento dell'avvio di prima), e il primo fotogramma è già la pagina con
 * la sua struttura (la pagina «Da provare» con la sua barra), sul fondo giusto in chiaro e in scuro.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xhdpi")
class F8p11FirstFrameTest {
  @get:Rule val compose = createEmptyComposeRule()

  @Before fun forget() {
    ApplicationProvider.getApplicationContext<Context>()
      .getSharedPreferences("fluid_engine_first_frame", Context.MODE_PRIVATE).edit().clear().commit()
  }

  @Test fun chiaro() = firstFrame(dark = false)

  @Config(qualifiers = "w360dp-h780dp-night-xhdpi")
  @Test fun scuro() = firstFrame(dark = true)

  /** Senza un avvio di prima la finestra prende la pagina dal tema dell'Activity, già in `onCreate`. */
  @Test fun ilTemaPrimaDiComposeChiaro() = themeBeforeCompose(dark = false)

  @Config(qualifiers = "w360dp-h780dp-night-xhdpi")
  @Test fun ilTemaPrimaDiComposeScuro() = themeBeforeCompose(dark = true)

  private fun themeBeforeCompose(dark: Boolean) {
    val activity = Robolectric.buildActivity(GalleryActivity::class.java).create().get()
    assertEquals(if (dark) 0xFF0D0D0F.toInt() else 0xFFF2F2F7.toInt(), (activity.window.decorView.background as ColorDrawable).color)
  }

  /** Il fondo dell'avvio di prima arriva alla finestra già in `onCreate`, prima che Compose componga. */
  @Test fun ilSuggerimentoArrivaPrimaDiCompose() {
    ApplicationProvider.getApplicationContext<Context>()
      .getSharedPreferences("fluid_engine_first_frame", Context.MODE_PRIVATE).edit()
      .putInt("background.day", Color.BLACK).putBoolean("dark.day", true).commit()
    val activity = Robolectric.buildActivity(GalleryActivity::class.java).create().get()
    assertEquals(Color.BLACK, (activity.window.decorView.background as ColorDrawable).color)
  }

  private fun firstFrame(dark: Boolean) {
    compose.mainClock.autoAdvance = false
    ActivityScenario.launch(GalleryActivity::class.java).use { scenario ->
      compose.mainClock.advanceTimeByFrame()
      // La struttura della pagina c'è al primo fotogramma.
      compose.onNodeWithTag("to-try-page").assertExists()
      lateinit var image: Bitmap
      var windowColor = 0
      scenario.onActivity { activity ->
        windowColor = (activity.window.decorView.background as ColorDrawable).color
        image = Bitmap.createBitmap(activity.window.decorView.width, activity.window.decorView.height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(activity.window, image, { assertEquals(PixelCopy.SUCCESS, it) }, Handler(Looper.getMainLooper()))
      }
      compose.waitForIdle()
      System.getenv("GLASS_F8P11_PIXELS")?.let { path ->
        val dir = File(path).apply { mkdirs() }
        File(dir, "primo-fotogramma-${if (dark) "scuro" else "chiaro"}.png").outputStream().use {
          image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
      }
      // Dopo la prima composizione la finestra ha il fondo esatto della pagina (con la tinta dell'accento).
      val page = image.getPixel(image.width / 2, image.height * 3 / 4).let { if (Color.alpha(it) == 0) windowColor else it }
      val luminance = (Color.red(windowColor) * 0.2126f + Color.green(windowColor) * 0.7152f + Color.blue(windowColor) * 0.0722f) / 255f
      assertTrue("lato giusto: $luminance", if (dark) luminance < 0.1f else luminance > 0.9f)
      assertTrue("finestra e pagina dello stesso fondo: ${Integer.toHexString(windowColor)} / ${Integer.toHexString(page)}",
        close(windowColor, image.getPixel(2, image.height - 2)) || close(windowColor, page))
      // Il suggerimento per il prossimo avvio.
      val prefs = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("fluid_engine_first_frame", Context.MODE_PRIVATE)
      val side = if (dark) "night" else "day"
      assertEquals(windowColor, prefs.getInt("background.$side", 0))
      assertEquals(dark, prefs.getBoolean("dark.$side", !dark))
    }
  }

  private fun close(a: Int, b: Int): Boolean =
    kotlin.math.abs(Color.red(a) - Color.red(b)) <= 3 && kotlin.math.abs(Color.green(a) - Color.green(b)) <= 3 &&
      kotlin.math.abs(Color.blue(a) - Color.blue(b)) <= 3
}
