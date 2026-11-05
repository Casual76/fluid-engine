package dev.antigravity.fluidengine.sample

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.page.FluidPageHostState
import dev.antigravity.fluidengine.ui.page.rememberFluidPageHostState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Il fotogramma finale deve contenere l'icona anche quando il suo vetro passa al morph.
 * Contare i draw non basta: un layer invalidato puo' registrare l'icona senza mostrarla.
 * API 32 esercita la maschera RenderEffect senza gli shader AGSL non supportati da Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [32], qualifiers = "w360dp-h640dp-xhdpi")
class FluidPageBarIconDrawTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var nav: NavHostController
  private lateinit var pages: FluidPageHostState
  private val repaint = mutableIntStateOf(0)

  private fun show() {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        nav = rememberFluidNavController()
        pages = rememberFluidPageHostState()
        FluidNavHost(navController = nav, startDestination = "a", state = pages) {
          for (route in listOf("a", "b")) {
            composable(route) {
              FluidScreen(
                title = route,
                onBack = if (route == "b") ({ nav.popBackStack(); Unit }) else null,
                actions = {
                  FluidGlassIconButton(onClick = {}) {
                    Box(Modifier.size(24.dp).drawBehind {
                      // Invalida il disegno anche se il layer del tasto e' in cache.
                      repaint.intValue
                      drawRect(if (route == "a") Color.Cyan else Color.Magenta)
                    })
                  }
                },
              ) {}
            }
          }
        }
      }
    }
    compose.waitForIdle()
    compose.runOnIdle { nav.navigate("b") }
    compose.waitForIdle()
  }

  private fun assertIconVisible(route: String) {
    var visiblePixels = 0
    compose.runOnIdle { repaint.intValue++ }
    compose.mainClock.advanceTimeByFrame()
    compose.runOnUiThread {
      val view = compose.activity.window.decorView
      val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
      view.draw(Canvas(bitmap))
      val pixels = IntArray(bitmap.width * bitmap.height)
      bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
      visiblePixels = pixels.count {
        val red = android.graphics.Color.red(it)
        val green = android.graphics.Color.green(it)
        val blue = android.graphics.Color.blue(it)
        if (route == "a") green > red + 80 && blue > red + 80 else red > green + 80 && blue > green + 80
      }
      bitmap.recycle()
    }
    assertTrue("L'icona di $route deve essere visibile nel fotogramma: $visiblePixels pixel", visiblePixels > 50)
  }

  @Test
  fun `il morph spegne solo il vetro e conserva il disegno delle icone`() {
    show()
    assertIconVisible("b")
    compose.mainClock.autoAdvance = false
    compose.onRoot().performTouchInput {
      down(Offset(6f, 640f))
      repeat(8) { moveBy(Offset(16f, 0f), delayMillis = 100L) }
    }
    compose.mainClock.advanceTimeByFrame()
    assertTrue(compose.runOnIdle { pages.isMoving })
    assertIconVisible("b")

    // Il gesto torna indietro: anche dopo l'annullo il tasto deve continuare a disegnarsi.
    compose.onRoot().performTouchInput { up() }
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    assertIconVisible("b")
  }

  @Test
  fun `l'icona in arrivo compare durante il gesto e resta dopo la conferma`() {
    show()
    compose.mainClock.autoAdvance = false
    compose.onRoot().performTouchInput {
      down(Offset(6f, 640f))
      repeat(34) { moveBy(Offset(16f, 0f), delayMillis = 100L) }
    }
    compose.mainClock.advanceTimeByFrame()
    assertTrue(compose.runOnIdle { pages.isMoving })
    assertIconVisible("a")
    compose.onRoot().performTouchInput { up() }
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    assertIconVisible("a")
  }
}
