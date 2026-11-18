package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidChromeController
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidScrollToTopBus
import dev.antigravity.fluidengine.ui.fluid.FluidScrollEdge
import dev.antigravity.fluidengine.ui.fluid.FluidScrollEdgeSide
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.ProvideFluidChrome
import dev.antigravity.fluidengine.ui.fluid.fluidChromePages
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidChromeController
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.Backdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.rememberLayerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.drawPlainBackdrop
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FluidChromeBackdropInstrumentedTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test fun theExternalReaderSeesThePageButKeepsItsOwnLayerEmpty() {
    val constrained = mutableStateOf(true)
    lateinit var external: LayerBackdrop
    compose.setContent {
      external = rememberLayerBackdrop()
      val body = remember { movableContentOf {
        Box(Modifier.fillMaxSize().background(if (constrained.value) Color.Red else Color.Green))
      } }
      Box(Modifier.size(96.dp)) {
        Box(Modifier.fillMaxSize().fluidChromePages()) {
          if (constrained.value) {
            BoxWithConstraints { if (maxWidth > 0.dp) body() }
          } else {
            body()
          }
        }
        // Il nero dietro alla cattura impedisce al rosso della pagina sottostante di passare per trasparenza.
        Box(Modifier.size(40.dp).testTag("lettore").drawPlainBackdrop(
          external, { RectangleShape }, {}, onDrawBehind = { drawRect(Color.Black) },
        ))
      }
    }
    compose.waitUntil(10_000) { external.isReadyToSample() }
    fun center(): Color {
      val pixels = compose.onNodeWithTag("lettore").captureToImage().toPixelMap()
      return pixels[pixels.width / 2, pixels.height / 2]
    }
    val before = center()
    assertTrue("la riserva disegna il rosso: $before", before.red > 0.9f && before.green < 0.1f)
    compose.runOnIdle {
      assertEquals("il lettore non è la destinazione della pagina", IntSize.Zero, external.graphicsLayer.size)
      constrained.value = false
    }
    compose.waitForIdle()
    val after = center()
    assertTrue("il corpo riposizionato disegna il verde: $after", after.green > 0.9f && after.red < 0.1f)
    compose.runOnIdle { assertEquals(IntSize.Zero, external.graphicsLayer.size) }
  }

  @Test fun onlyMountedBottomContributionsArePublished() {
    val inset = mutableStateOf(0.dp)
    lateinit var controller: FluidChromeController
    val field = GlassBackdropState::class.java.getDeclaredField("backdrop").apply { isAccessible = true }
    fun layers(): List<Any> = controller.activeBackdrop.value?.let { (field.get(it) as Backdrop).layerSources }.orEmpty()
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        controller = rememberFluidChromeController()
        ProvideFluidChrome(controller, inset.value, remember { FluidScrollToTopBus() }) {
          FluidScreen(title = "Fondale") {}
        }
      }
    }
    compose.waitForIdle()
    compose.runOnIdle {
      assertEquals("pagina senza bordo", 1, layers().size)
      inset.value = 78.dp
    }
    compose.waitUntil(10_000) { layers().size == 2 }
    compose.runOnIdle { inset.value = 0.dp }
    compose.waitUntil(10_000) { layers().size == 1 }
  }

  @Test fun topEdgeOnAUniformSourceIsVisibleOnlyWhenScrolled() {
    val scrolled = mutableStateOf(false)
    val background = Color(0xfff0f1f5)
    compose.setContent {
      val source = rememberGlassBackdrop()
      Box(Modifier.size(240.dp, 160.dp).testTag("striscia")) {
        Box(Modifier.fillMaxSize().glassBackdropSource(source).background(background))
        FluidScrollEdge(source, FluidScrollEdgeSide.Top, 68.dp, color = Color.White,
          intensity = { if (scrolled.value) 1f else 0f })
      }
    }
    val box = compose.onNodeWithTag("striscia")
    val before = box.captureToImage().toPixelMap()
    compose.runOnIdle { scrolled.value = true }
    compose.waitForIdle()
    val after = box.captureToImage().toPixelMap()
    val x = before.width / 4
    assertEquals(background.toArgb(), before[x, 5].toArgb())
    assertTrue("il velo è acceso", after[x, 5].red > before[x, 5].red)
    // Campioni della riga segnalata in A6, in coordinate locali della sola striscia.
    val y = (before.height * 113f / 160f).toInt()
    for (row in y - 2..y + 2) {
      android.util.Log.i("FluidEdgeTest", "y=$row prima=%08x dopo=%08x".format(
        before[x, row].toArgb(), after[x, row].toArgb()))
    }
  }
}
