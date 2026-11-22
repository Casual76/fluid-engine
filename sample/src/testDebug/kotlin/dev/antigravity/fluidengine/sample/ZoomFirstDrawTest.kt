package dev.antigravity.fluidengine.sample

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.fluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.page.FluidPageHost
import dev.antigravity.fluidengine.ui.page.FluidPageTransition
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h600dp-mdpi")
class ZoomFirstDrawTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test fun laCardRestaVisibileFincheIlFrontePuoDisegnareLaFoto() {
    var stack by mutableStateOf(listOf(0))
    var reuseCardLayer by mutableStateOf(false)
    var detailDrawn = false
    compose.setContent {
      val ns = rememberFluidGlassNamespace()
      val source = remember(ns) { FluidTransitionSource("card", ns) }
      FluidPageHost(stack, onBack = { stack = listOf(0) }, pageColor = Color.Gray,
        transition = { if (it == 1) FluidPageTransition.Expand else FluidPageTransition.Slide },
        zoomSource = { if (it == 1) source else null }) { key ->
        if (key == 0) {
          val cached = rememberGraphicsLayer()
          Box(Modifier.offset(20.dp, 150.dp).size(100.dp, 90.dp).drawWithContent {
            // Il layer della riga continua a presentare l'ultima incisione sullo schermo.
            // La nuova foto non è disponibile al fronte finché il nodo non viene ridisegnato.
            if (!reuseCardLayer) cached.record { this@drawWithContent.drawContent() }
            drawLayer(cached)
          }.fluidTransitionSource("card", ns, RectangleShape).background(Color.Green))
        } else {
          Box(Modifier.fillMaxSize().drawWithContent {
            drawContent()
            detailDrawn = true
          }.background(Color.Blue))
        }
      }
    }
    val window = compose.activity.window
    fun center(): Int {
      val decor = window.decorView
      val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
      compose.runOnIdle {
        PixelCopy.request(window, bitmap, { assertEquals(PixelCopy.SUCCESS, it) }, Handler(Looper.getMainLooper()))
      }
      return bitmap.getPixel(70, 200)
    }

    assertEquals(android.graphics.Color.GREEN, center())
    compose.mainClock.autoAdvance = false
    compose.runOnIdle { reuseCardLayer = true; stack = listOf(0, 1) }
    repeat(14) { frame ->
      compose.mainClock.advanceTimeByFrame()
      val pixel = center()
      val red = android.graphics.Color.red(pixel)
      val ink = maxOf(android.graphics.Color.green(pixel), android.graphics.Color.blue(pixel))
      org.junit.Assert.assertTrue("fotogramma $frame, centro della card = %08x".format(pixel), ink > red + 3)
    }
    compose.runOnIdle { reuseCardLayer = false }
    compose.mainClock.advanceTimeBy(1_200)
    compose.mainClock.autoAdvance = true
    assertEquals(android.graphics.Color.BLUE, center())
    org.junit.Assert.assertTrue(detailDrawn)
    compose.runOnIdle { stack = listOf(0) }
    assertEquals("la card torna dopo il pop", android.graphics.Color.GREEN, center())
  }
}
