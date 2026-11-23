package dev.antigravity.fluidengine.sample.dynamic

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidBarMinimizeBehavior
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSpec
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F6B.13, la correzione nel motore: **la lista sotto il campo cresciuto**. Una chat con il campo «Messaggio» in fondo
 * (F6B.7): a fine scorrimento l'ultima riga deve leggersi **sopra il vetro del campo**, con il campo di una riga e di
 * cinque, senza tastiera e con la tastiera su (un inserto `ime` mandato alla vista di Compose, come lo manda il sistema).
 * Prima di F6B.13 la lista aveva in fondo il margine di una riga della barra: sotto un campo di cinque righe le ultime
 * righe restavano coperte, e con la tastiera su il margine finiva sotto la tastiera.
 *
 * Il bordo alto del vetro è il bordo alto dell'editor meno il suo margine (8 dp, `FluidTextFieldVerticalPadding`): il
 * vetro del campo che cresce è alto quanto il contenuto, ancorato al bordo dalla parte della tastiera.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class FluidScreenGrownFieldListTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var root: View
  private lateinit var list: LazyListState

  private val rows = 40
  private val oneLine = "Ci vediamo alle otto"
  private val fiveLines = "Prima riga\nSeconda riga\nTerza riga\nQuarta riga\nQuinta riga"

  /** Il margine dell'editor dentro il vetro (`FluidTextFieldVerticalPadding`). */
  private val editorInset = 8.dp

  private fun chat(text: String, modifier: Modifier = Modifier) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }, systemBars = false) {
        // Movimento ridotto: la crescita e la quota della tastiera arrivano al loro posto senza molla.
        CompositionLocalProvider(LocalFluidMotionPolicy provides FluidMotionPolicy(0f)) {
          root = LocalView.current
          list = rememberLazyListState()
          FluidScreen(
            title = "Anna",
            modifier = modifier,
            listState = list,
            barMinimize = FluidBarMinimizeBehavior.Never,
            search = FluidSearchSpec.message(text, {}, onSend = {}, active = true, onActiveChange = {}),
          ) {
            items(rows) { index -> Text("Messaggio $index", Modifier.testTag("riga-$index")) }
          }
        }
      }
    }
    compose.waitForIdle()
  }

  private fun keyboard(height: Dp) {
    compose.runOnUiThread {
      val px = (height.value * root.resources.displayMetrics.density).toInt()
      val insets = WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, px))
        .setVisible(WindowInsetsCompat.Type.ime(), true)
        .build()
      ViewCompat.dispatchApplyWindowInsets(root, insets)
    }
    compose.waitForIdle()
  }

  /** L'ultima riga chiesta in cima: la lista si ferma alla fine, cioè dove il margine in fondo conta. */
  private fun scrollToEnd() {
    compose.onNode(hasScrollToIndexAction()).performScrollToIndex(rows - 1)
    compose.waitForIdle()
    compose.runOnIdle { assertTrue("la lista non è arrivata in fondo", !list.canScrollForward) }
  }

  /** A fine scorrimento: l'ultima riga sopra il vetro del campo, e non lontana (il margine di sempre, 28 dp, più poco). */
  private fun assertLastRowAboveTheField(case: String) {
    scrollToEnd()
    val last = compose.onNodeWithTag("riga-${rows - 1}").getUnclippedBoundsInRoot()
    val editor = compose.onNode(hasSetTextAction()).getUnclippedBoundsInRoot()
    val glassTop = editor.top - editorInset
    assertTrue("$case: l'ultima riga finisce a ${last.bottom}, il vetro del campo comincia a $glassTop",
      last.bottom <= glassTop + 0.5.dp)
    assertTrue("$case: fra l'ultima riga (${last.bottom}) e il vetro ($glassTop) c'è troppo spazio",
      glassTop - last.bottom <= 48.dp)
  }

  @Test fun oneLineWithoutKeyboard() {
    chat(oneLine)
    assertLastRowAboveTheField("una riga, senza tastiera")
  }

  @Test fun fiveLinesWithoutKeyboard() {
    chat(fiveLines)
    assertLastRowAboveTheField("cinque righe, senza tastiera")
  }

  @Test fun oneLineWithKeyboard() {
    chat(oneLine)
    keyboard(300.dp)
    assertLastRowAboveTheField("una riga, con la tastiera")
  }

  @Test fun fiveLinesWithKeyboard() {
    chat(fiveLines)
    keyboard(300.dp)
    assertLastRowAboveTheField("cinque righe, con la tastiera")
  }

  /**
   * La schermata dentro `imePadding()` (come chiede l'editor di testo ricco): sta già sopra la tastiera, e la lista non
   * conta la tastiera una seconda volta (l'ultima riga resta vicina al campo, non 300 dp sopra).
   */
  @Test fun fiveLinesWithKeyboardInsideImePadding() {
    chat(fiveLines, Modifier.imePadding())
    keyboard(300.dp)
    assertLastRowAboveTheField("cinque righe, con la tastiera, dentro imePadding")
  }
}

/** Un telefono piccolo, 360 dp. */
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class FluidScreenGrownFieldList360Test : FluidScreenGrownFieldListTest()

/** Un telefono grande, 412 dp. */
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class FluidScreenGrownFieldList412Test : FluidScreenGrownFieldListTest()
