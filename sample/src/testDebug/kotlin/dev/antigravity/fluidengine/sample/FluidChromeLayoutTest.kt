package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidScrollToTopBus
import dev.antigravity.fluidengine.ui.fluid.ProvideFluidChrome
import dev.antigravity.fluidengine.ui.fluid.fluidChromePages
import dev.antigravity.fluidengine.ui.fluid.rememberFluidChromeController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * G1, rilievi C-R1, B-R8, D-R6: `ProvideFluidChrome` è trasparente al layout. In F2.3c avvolgeva il contenuto in un
 * `Box` (lo scrittore di riserva): in una `Column` i figli si sovrapponevano e il `weight` non arrivava, in un `Box` un
 * `matchParentSize` misurava zero. Ora è solo un fornitore, e lo scrittore è `Modifier.fluidChromePages()`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidChromeLayoutTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun inAColumnThePageTakesTheWeightAndTheBarStaysBelowIt() {
    var page = IntSize.Zero
    var pageTop = Float.NaN
    var bar = IntSize.Zero
    var barTop = Float.NaN
    compose.setContent {
      val controller = rememberFluidChromeController()
      val bus = remember { FluidScrollToTopBus() }
      Column(Modifier.size(300.dp, 400.dp)) {
        val column: ColumnScope = this
        ProvideFluidChrome(controller = controller, bottomInset = 0.dp, scrollToTop = bus) {
          // Pagina(Modifier.weight(1f)); Barra()
          Box(
            with(column) { Modifier.weight(1f) }
              .fillMaxWidth()
              .onGloballyPositioned { page = it.size; pageTop = it.positionInRoot().y },
          )
          Box(
            Modifier.fillMaxWidth().height(50.dp)
              .onGloballyPositioned { bar = it.size; barTop = it.positionInRoot().y },
          )
        }
      }
    }
    compose.waitForIdle()
    // xhdpi: 1 dp = 2 px.
    assertEquals("la barra è alta 50 dp", 100, bar.height)
    assertEquals("la pagina prende il resto della colonna", 800 - 100, page.height)
    assertEquals("la pagina in cima", 0f, pageTop, 0.5f)
    assertEquals("la barra sotto la pagina, non sopra", 700f, barTop, 0.5f)
  }

  @Test
  fun inABoxMatchParentSizeReachesTheParent() {
    var page = IntSize.Zero
    compose.setContent {
      val controller = rememberFluidChromeController()
      val bus = remember { FluidScrollToTopBus() }
      Box {
        val box: BoxScope = this
        // Il Box prende la misura da un altro figlio; la pagina la eredita con matchParentSize.
        Spacer(Modifier.size(200.dp, 120.dp))
        ProvideFluidChrome(controller = controller, bottomInset = 0.dp, scrollToTop = bus) {
          // Pagina(Modifier.matchParentSize())
          Box(with(box) { Modifier.matchParentSize() }.onGloballyPositioned { page = it.size })
        }
      }
    }
    compose.waitForIdle()
    assertEquals("non zero: la misura del genitore", IntSize(400, 240), page)
  }

  @Test
  fun thePagesWriterDoesNotChangeTheLayoutEither() {
    var plain = IntSize.Zero
    var written = IntSize.Zero
    var child = IntSize.Zero
    compose.setContent {
      val controller = rememberFluidChromeController()
      val bus = remember { FluidScrollToTopBus() }
      Column {
        Box(Modifier.width(180.dp).onGloballyPositioned { plain = it.size }) { Spacer(Modifier.size(180.dp, 90.dp)) }
        ProvideFluidChrome(controller = controller, bottomInset = 0.dp, scrollToTop = bus) {
          Box(Modifier.width(180.dp).fluidChromePages().onGloballyPositioned { written = it.size }) {
            Spacer(Modifier.size(180.dp, 90.dp).onGloballyPositioned { child = it.size })
          }
        }
      }
    }
    compose.waitForIdle()
    assertEquals(plain, written)
    assertEquals(IntSize(360, 180), child)
  }
}
