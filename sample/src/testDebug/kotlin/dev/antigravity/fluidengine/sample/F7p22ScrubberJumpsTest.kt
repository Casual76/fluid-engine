package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidFastScroller
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileViewer
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidPageSource
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.util.Collections
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F7.22 · Lo scorrimento rapido per TalkBack: **ogni** richiesta di salto porta la lista, non solo la prima dopo un
 * movimento (il difetto visto da F8.18), anche quando la lista sta ancora correndo da un lancio. E il PDF di migliaia
 * di pagine: salti avanti e indietro col cursore, in fila, ciascuno dove deve.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class F7p22ScrubberJumpsTest {

  @get:Rule
  val compose = createComposeRule()

  private val cursor = SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Scorrimento rapido"))

  private fun showList(count: Int, onState: (LazyListState) -> Unit) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val state = rememberLazyListState()
        onState(state)
        Box(Modifier.fillMaxSize()) {
          LazyColumn(state = state, modifier = Modifier.fillMaxSize().testTag("lista")) {
            items(count) { Text("Riga $it", Modifier.fillMaxWidth().height(48.dp)) }
          }
          FluidFastScroller(state = state, label = { "L${it / 10}" })
        }
      }
    }
    compose.waitForIdle()
  }

  private fun jump(fraction: Float) {
    compose.onNode(cursor, useUnmergedTree = true).performSemanticsAction(SemanticsActions.SetProgress) { it(fraction) }
    compose.waitForIdle()
  }

  private fun near(state: LazyListState, fraction: Float, count: Int) {
    val max = count - state.layoutInfo.visibleItemsInfo.size
    val expected = (fraction * max).toInt()
    val first = state.firstVisibleItemIndex
    assertTrue("al ${(fraction * 100).toInt()}% la lista doveva stare vicino a $expected, è a $first", abs(first - expected) <= 3)
  }

  @Test
  fun `ogni salto chiesto da TalkBack porta la lista, uno dopo l'altro`() {
    var state: LazyListState? = null
    showList(1_000) { state = it }
    for (fraction in listOf(0.5f, 0.2f, 0.9f, 0.9f, 0.05f, 0.6f)) {
      jump(fraction)
      near(state!!, fraction, 1_000)
    }
  }

  @Test
  fun `dopo uno scorrimento col dito i salti in fila funzionano ancora`() {
    var state: LazyListState? = null
    showList(1_000) { state = it }
    jump(0.3f)
    near(state!!, 0.3f, 1_000)
    compose.onNodeWithTag("lista").performTouchInput { swipeUp() }
    compose.waitForIdle()
    for (fraction in listOf(0.7f, 0.1f, 0.4f)) {
      jump(fraction)
      near(state!!, fraction, 1_000)
    }
  }

  @Test
  fun `un salto mentre la lista corre da un lancio la ferma dove va il cursore`() {
    var state: LazyListState? = null
    showList(1_000) { state = it }
    compose.mainClock.autoAdvance = false
    compose.onNodeWithTag("lista").performTouchInput { swipeUp(durationMillis = 80) }
    // Il lancio è partito e corre ancora: un fotogramma, non di più.
    compose.mainClock.advanceTimeByFrame()
    assertTrue("il lancio doveva essere in corso", state!!.isScrollInProgress)
    compose.onNode(cursor, useUnmergedTree = true).performSemanticsAction(SemanticsActions.SetProgress) { it(0.75f) }
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    near(state!!, 0.75f, 1_000)
    // E il salto dopo funziona ancora: il giro delle richieste non è morto con il lancio.
    jump(0.25f)
    near(state!!, 0.25f, 1_000)
  }

  /**
   * TalkBack regola un cursore col gesto su e giù (o i tasti del volume): `ACTION_SCROLL_FORWARD` e `BACKWARD`, che
   * Compose traduce in `SetProgress(attuale ± 5 %)` dal valore **che il nodo dice**. Se il nodo restasse indietro, il
   * secondo passo chiederebbe lo stesso punto del primo e la lista non si muoverebbe più (il difetto di F8.18).
   */
  @Test
  fun `i passi di TalkBack avanti e indietro muovono la lista ogni volta`() {
    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    val manager = context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
    org.robolectric.Shadows.shadowOf(manager).setEnabled(true)
    org.robolectric.Shadows.shadowOf(manager).setTouchExplorationEnabled(true)
    var state: LazyListState? = null
    var view: android.view.View? = null
    compose.setContent {
      view = androidx.compose.ui.platform.LocalView.current
      FluidTheme(settings = remember { EngineSettings() }) {
        val list = rememberLazyListState()
        state = list
        Box(Modifier.fillMaxSize()) {
          LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("lista")) {
            items(1_000) { Text("Riga $it", Modifier.fillMaxWidth().height(48.dp)) }
          }
          FluidFastScroller(state = list, label = { "L${it / 10}" })
        }
      }
    }
    compose.waitForIdle()
    fun step(action: Int) {
      val id = compose.onNode(cursor, useUnmergedTree = true).fetchSemanticsNode().id
      compose.runOnIdle { assertTrue(view!!.accessibilityNodeProvider.performAction(id, action, null)) }
      compose.waitForIdle()
    }
    val positions = mutableListOf(state!!.firstVisibleItemIndex)
    repeat(4) {
      step(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
      positions += state!!.firstVisibleItemIndex
    }
    // Il dito muove la lista, poi due passi indietro.
    compose.onNodeWithTag("lista").performTouchInput { swipeUp() }
    compose.waitForIdle()
    positions += state!!.firstVisibleItemIndex
    repeat(2) {
      step(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
      positions += state!!.firstVisibleItemIndex
    }
    val forward = positions.subList(0, 5).zipWithNext()
    assertTrue("ogni passo avanti muove la lista: $positions", forward.all { (a, b) -> b > a })
    val backward = positions.subList(5, 8).zipWithNext()
    assertTrue("ogni passo indietro muove la lista: $positions", backward.all { (a, b) -> b < a })
  }

  /** 5000 pagine A4: il PDF gigante di F8.18, senza `PdfRenderer` (le bitmap sono vuote). */
  private class Giant : FluidPageSource {
    val rendered: MutableList<Int> = Collections.synchronizedList(mutableListOf())
    override val count = 5_000
    override suspend fun size(index: Int) = IntSize(595, 842)
    override suspend fun render(index: Int, target: IntSize): ImageBitmap {
      rendered += index
      return ImageBitmap(target.width, target.height)
    }
  }

  private fun currentPage(): Int = compose.onNodeWithContentDescription("Miniature delle pagine").fetchSemanticsNode()
    .config.getOrNull(SemanticsProperties.StateDescription).orEmpty()
    .removePrefix("Pagina ").substringBefore(" di").toInt()

  private fun scrub(fraction: Float) {
    compose.onNode(hasContentDescription("Scorri il documento"), useUnmergedTree = true)
      .performSemanticsAction(SemanticsActions.SetProgress) { it(fraction) }
    compose.waitForIdle()
  }

  @Test
  fun `il PDF di 5000 pagine salta avanti e indietro col cursore, ogni volta`() {
    val giant = Giant()
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val document = remember { FluidViewerContent.Pages(giant) }
        FluidFileViewer(1, content = { document })
      }
    }
    compose.waitUntil(20_000) {
      compose.onAllNodesWithContentDescription("Pagina 1 di 5000").fetchSemanticsNodes().isNotEmpty()
    }
    // Il contatore per TalkBack segue la lista a ogni salto, non solo al primo: con la descrizione letta dentro il blocco
    // `semantics` restava a «Pagina 3999» mentre la lista era già a pagina 1000 (F7.22, 26 novembre 2026).
    for (fraction in listOf(0.8f, 0.2f)) {
      scrub(fraction)
      compose.waitUntil(10_000) { abs(currentPage() - fraction * 5_000) < 60 }
    }
    // La lista si muove in un altro modo, poi due salti in fila: tutti e due arrivano.
    compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 100%"))
      .performScrollToIndex(2_499)
    compose.waitUntil(10_000) { abs(currentPage() - 2_500) < 5 }
    for (fraction in listOf(0.6f, 0.1f, 0.95f)) {
      scrub(fraction)
      compose.waitUntil(10_000) { abs(currentPage() - fraction * 5_000) < 60 }
    }
    // Si rendono solo le pagine su cui ci si è fermati: mai quelle attraversate.
    val far = giant.rendered.toSet().filter { page ->
      listOf(1, 4_000, 1_000, 2_500, 3_000, 500, 4_750).none { abs(it - page) < 12 }
    }
    assertTrue("pagine rese lontano dalle soste: $far", far.isEmpty())
  }
}
