package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSize
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `FluidGlassIconButton(size: Dp)` (3.0): un tasto tondo di vetro di una misura che le tre taglie non
 * dicono — i 62 e i 76 dp dei dischi del trasporto di Fluidify, i 52 dell'hero, i 54 di un menu.
 *
 * Le regole sono quelle di sempre: il vetro e' di `size` dp, il riquadro che il tasto occupa e che il
 * dito colpisce non e' mai sotto i 48 dp. La crescita sotto il dito (il tetto di 24 dp in tutto) e'
 * pura e sta in `FluidPressTransformTest`; qui si guarda quello che il layout dice.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidGlassFreeSizeTest {

  @get:Rule
  val compose = createComposeRule()

  @Composable
  private fun Themed(content: @Composable () -> Unit) {
    FluidTheme(settings = remember { EngineSettings() }, content = content)
  }

  private val sizes = listOf(32.dp, 40.dp, 48.dp, 52.dp, 54.dp, 62.dp, 76.dp)

  @Test
  fun `il vetro e' di size dp e il riquadro non e' mai sotto i 48`() {
    compose.setContent {
      Themed {
        Column {
          sizes.forEach { size ->
            FluidGlassIconButton(onClick = {}, size = size, modifier = Modifier.testTag("icon-${size.value.toInt()}")) {}
          }
        }
      }
    }
    compose.waitForIdle()
    sizes.forEach { size ->
      val box = maxOf(size, 48.dp)
      val tag = "icon-${size.value.toInt()}"
      compose.onNodeWithTag(tag).assertWidthIsEqualTo(box).assertHeightIsEqualTo(box)
      // Il click sta sul vetro, dentro il riquadro: e' il figlio che ha l'azione, ed e' di `size`.
      compose.onNodeWithTag(tag).onChildren().onFirst()
        .assertHasClickAction()
        .assertWidthIsEqualTo(size)
        .assertHeightIsEqualTo(size)
    }
  }

  @Test
  fun `un tocco nel margine attorno a un vetro da 32 dp clicca lo stesso`() {
    var clicks = 0
    compose.setContent {
      Themed {
        FluidGlassIconButton(onClick = { clicks++ }, size = 32.dp, modifier = Modifier.testTag("small")) {}
      }
    }
    compose.waitForIdle()
    // Il riquadro e' da 48: l'angolo sta a 8 dp dal vetro per lato.
    compose.onNodeWithTag("small").performTouchInput { click(Offset(3f, 3f)) }
    compose.waitForIdle()
    assertEquals("il margine fuori dal vetro non clicca", 1, clicks)
  }

  @Test
  fun `una taglia dell'enum e la stessa misura in dp danno lo stesso tasto`() {
    compose.setContent {
      Themed {
        Column {
          FluidGlassSize.entries.forEach { size ->
            FluidGlassIconButton(onClick = {}, size = size, modifier = Modifier.testTag("enum-${size.name}")) {}
            FluidGlassIconButton(onClick = {}, size = size.lens, modifier = Modifier.testTag("dp-${size.name}")) {}
          }
        }
      }
    }
    compose.waitForIdle()
    FluidGlassSize.entries.forEach { size ->
      compose.onNodeWithTag("enum-${size.name}").assertWidthIsEqualTo(size.touchTarget).assertHeightIsEqualTo(size.touchTarget)
      compose.onNodeWithTag("dp-${size.name}").assertWidthIsEqualTo(size.touchTarget).assertHeightIsEqualTo(size.touchTarget)
      compose.onNodeWithTag("dp-${size.name}").onChildren().onFirst().assertWidthIsEqualTo(size.lens)
    }
  }

  @Test
  fun `senza size vale la taglia Regular, come prima`() {
    compose.setContent {
      Themed {
        FluidGlassIconButton(onClick = {}, modifier = Modifier.testTag("default")) {}
      }
    }
    compose.waitForIdle()
    val regular: Dp = FluidGlassSize.Regular.touchTarget
    compose.onNodeWithTag("default").assertWidthIsEqualTo(regular).assertHeightIsEqualTo(regular)
    compose.onNodeWithTag("default").onChildren().onFirst().assertWidthIsEqualTo(FluidGlassSize.Regular.lens)
  }
}
