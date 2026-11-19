package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSizing
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPresentation
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSheet
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSize
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.LocalFluidButtonSizing
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassButtonStyle
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassSize
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Share
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Stili, larghezza e taglia d'ambiente dei tasti di vetro (F6.4) in un albero Compose vero, e il debito dei bersagli
 * della chiusura di F4A: ogni tasto, compresi quelli della barra di sempre, si tocca su almeno 44 dp (i 44 pt di
 * Apple), senza cambiare cio' che si vede. Il vetro resta della sua misura; il bersaglio e' il riquadro attorno.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidGlassButtonStylesUiTest {

  @get:Rule
  val compose = createComposeRule()

  @Composable
  private fun Themed(content: @Composable () -> Unit) {
    FluidTheme(settings = remember { EngineSettings() }, content = content)
  }

  private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().let {
    Rect(it.left.value, it.top.value, it.right.value, it.bottom.value)
  }

  /** Il quadrato da 44 dp attorno al centro del tasto: i quattro lati e i quattro angoli, mezzo dp dentro. */
  private fun ring44(center: Offset, wide: Boolean): List<Offset> {
    val r = 22f - 0.5f
    val corners = listOf(Offset(-r, -r), Offset(r, -r), Offset(-r, r), Offset(r, r))
    val sides = listOf(Offset(0f, -r), Offset(0f, r)) + if (wide) emptyList() else listOf(Offset(-r, 0f), Offset(r, 0f))
    return (sides + if (wide) emptyList() else corners).map { center + it }
  }

  @Test
  fun `ogni tasto si tocca su almeno 44 dp, quelli di sempre compresi, e TalkBack ne vede almeno 44`() {
    val clicks = mutableMapOf<String, Int>()
    fun hit(name: String): () -> Unit = { clicks[name] = (clicks[name] ?: 0) + 1 }
    val icons = FluidGlassSize.entries.map { "icon-${it.name}" } + "icon-32"
    compose.setContent {
      Themed {
        Column {
          FluidGlassSize.entries.forEach { size ->
            FluidGlassIconButton(onClick = hit("icon-${size.name}"), size = size, modifier = Modifier.testTag("icon-${size.name}")) {
              FluidIcon(FluidIcons.Share, "Condividi ${size.name}")
            }
          }
          FluidGlassIconButton(onClick = hit("icon-32"), size = 32.dp, modifier = Modifier.testTag("icon-32")) {}
          FluidGlassButton("Compatto", hit("label-Compact"), Modifier.testTag("label-Compact"), size = FluidGlassSize.Compact)
          FluidGlassButton("Normale", hit("label-Regular"), Modifier.testTag("label-Regular"))
          // Un tasto della barra di sempre (`FluidBarAction`, come l'indietro di un host senza barre dichiarate).
          FluidBarAction(icon = FluidIcons.Share, contentDescription = "Barra", onClick = hit("bar"), modifier = Modifier.testTag("bar"))
        }
      }
    }
    compose.waitForIdle()
    val targets = icons + listOf("label-Compact", "label-Regular", "bar")
    for (tag in targets) {
      val box = bounds(tag)
      val wide = tag.startsWith("label")
      val points = ring44(box.center, wide)
      points.forEach { point ->
        compose.onRoot().performTouchInput { click(Offset(point.x.dp.toPx(), point.y.dp.toPx())) }
        compose.waitForIdle()
      }
      assertEquals("$tag: ogni punto del quadrato da 44 dp clicca", points.size, clicks[tag] ?: 0)

      // TalkBack: il nodo con l'azione ha un bersaglio di almeno 44 dp per lato (Compose allarga quelli piu' piccoli).
      val clickable = if (wide) compose.onNodeWithTag(tag) else compose.onNodeWithTag(tag).onChildren().onFirst()
      val node = clickable.fetchSemanticsNode()
      assertTrue("$tag ha l'azione", node.config.contains(SemanticsActions.OnClick))
      val touch = node.touchBoundsInRoot
      val density = compose.density.density
      assertTrue("$tag: bersaglio ${touch.width / density} x ${touch.height / density} dp",
        touch.width / density >= 44f - 0.01f && touch.height / density >= 44f - 0.01f)
    }
  }

  @Test
  fun `la taglia d'ambiente vale per i tasti dentro, e l'icona ne discende`() {
    compose.setContent {
      Themed {
        Column {
          CompositionLocalProvider(LocalFluidGlassSize provides FluidGlassSize.Compact) {
            FluidGlassIconButton(onClick = {}, modifier = Modifier.testTag("ambiente")) {
              FluidIcon(FluidIcons.Share, null, Modifier.testTag("ambiente-icona"))
            }
            // Scritta sul tasto, la taglia vince sull'ambiente.
            FluidGlassIconButton(onClick = {}, size = FluidGlassSize.Large, modifier = Modifier.testTag("scritta")) {
              FluidIcon(FluidIcons.Share, null, Modifier.testTag("scritta-icona"))
            }
          }
          FluidGlassIconButton(onClick = {}, modifier = Modifier.testTag("serie")) {
            FluidIcon(FluidIcons.Share, null, Modifier.testTag("serie-icona"))
          }
        }
      }
    }
    compose.waitForIdle()
    compose.onNodeWithTag("ambiente").onChildren().onFirst().assertWidthIsEqualTo(40.dp)
    compose.onNodeWithTag("ambiente-icona", useUnmergedTree = true).assertWidthIsEqualTo(24.dp).assertHeightIsEqualTo(24.dp)
    compose.onNodeWithTag("scritta").onChildren().onFirst().assertWidthIsEqualTo(54.dp)
    compose.onNodeWithTag("scritta-icona", useUnmergedTree = true).assertWidthIsEqualTo(32.dp)
    compose.onNodeWithTag("serie").onChildren().onFirst().assertWidthIsEqualTo(44.dp)
    compose.onNodeWithTag("serie-icona", useUnmergedTree = true).assertWidthIsEqualTo(26.dp)
  }

  @Test
  fun `un foglio aperto da una barra Compact ha i tasti alla taglia, allo stile e alla larghezza di serie`() {
    var inSheet: Triple<FluidGlassSize, FluidGlassButtonStyle, FluidButtonSizing>? = null
    var inPopover: Triple<FluidGlassSize, FluidGlassButtonStyle, FluidButtonSizing>? = null
    compose.setContent {
      Themed {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(
          LocalFluidGlassModalHostState provides host,
          // La barra che apre il foglio: tasti compatti, limpidi e flessibili. Anche l'host sta qui dentro.
          LocalFluidGlassSize provides FluidGlassSize.Compact,
          LocalFluidGlassButtonStyle provides FluidGlassButtonStyle.Clear,
          LocalFluidButtonSizing provides FluidButtonSizing.Flexible,
        ) {
          Box(Modifier.fillMaxSize()) {
            FluidGlassIconButton(onClick = {}, modifier = Modifier.testTag("barra")) {}
            FluidGlassSheet(visible = true, onDismissRequest = {}) {
              inSheet = Triple(LocalFluidGlassSize.current, LocalFluidGlassButtonStyle.current, LocalFluidButtonSizing.current)
              FluidGlassIconButton(onClick = {}, modifier = Modifier.testTag("nel-foglio")) {
                FluidIcon(FluidIcons.Share, null, Modifier.testTag("nel-foglio-icona"))
              }
            }
            FluidGlassModalPortal(visible = true, onDismissRequest = {}, presentation = FluidGlassModalPresentation.Popover) {
              inPopover = Triple(LocalFluidGlassSize.current, LocalFluidGlassButtonStyle.current, LocalFluidButtonSizing.current)
            }
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    compose.waitForIdle()
    compose.onNodeWithTag("barra").onChildren().onFirst().assertWidthIsEqualTo(40.dp)
    val defaults = Triple(FluidGlassSize.Regular, FluidGlassButtonStyle.Glass, FluidButtonSizing.Fitted)
    assertEquals(defaults, inSheet)
    assertEquals(defaults, inPopover)
    compose.onNodeWithTag("nel-foglio").onChildren().onFirst().assertWidthIsEqualTo(44.dp)
    compose.onNodeWithTag("nel-foglio-icona", useUnmergedTree = true).assertWidthIsEqualTo(26.dp)
  }

  @Test
  fun `flessibile riempie la larghezza data, adattato resta quanto l'etichetta`() {
    compose.setContent {
      Themed {
        Column(Modifier.width(300.dp)) {
          FluidGlassButton("Invia", {}, Modifier.testTag("flessibile"), sizing = FluidButtonSizing.Flexible)
          FluidGlassButton("Invia", {}, Modifier.testTag("adattato"))
          CompositionLocalProvider(LocalFluidButtonSizing provides FluidButtonSizing.Flexible) {
            FluidGlassButton("Invia", {}, Modifier.testTag("ambiente"))
          }
        }
      }
    }
    compose.waitForIdle()
    compose.onNodeWithTag("flessibile").assertWidthIsEqualTo(300.dp)
    compose.onNodeWithTag("ambiente").assertWidthIsEqualTo(300.dp)
    val fitted: Dp = compose.onNodeWithTag("adattato").getUnclippedBoundsInRoot().let { it.right - it.left }
    assertTrue("adattato: $fitted", fitted < 200.dp)
  }

  @Test
  fun `il tasto in evidenza da' al contenuto l'inchiostro che sulla tinta si legge`() {
    var prominentInk = Color.Unspecified
    var glassInk = Color.Unspecified
    var accent = Color.Unspecified
    var themeInk = Color.Unspecified
    compose.setContent {
      Themed {
        accent = MaterialTheme.colorScheme.primary
        // L'inchiostro che il tasto trova intorno: fuori da un vetro che gira, il tasto di serie lo tiene.
        themeInk = LocalContentColor.current
        Column {
          FluidGlassIconButton(onClick = {}, style = FluidGlassButtonStyle.Prominent) { prominentInk = LocalContentColor.current }
          FluidGlassIconButton(onClick = {}) { glassInk = LocalContentColor.current }
        }
      }
    }
    compose.waitForIdle()
    fun ratio(a: Color, b: Color): Float {
      val hi = maxOf(a.luminance(), b.luminance())
      val lo = minOf(a.luminance(), b.luminance())
      return (hi + 0.05f) / (lo + 0.05f)
    }
    assertTrue("in evidenza sull'accento: ${ratio(prominentInk, accent)}", ratio(prominentInk, accent) >= 4.5f)
    assertEquals("il vetro di serie tiene l'inchiostro del tema", themeInk, glassInk)
  }
}
