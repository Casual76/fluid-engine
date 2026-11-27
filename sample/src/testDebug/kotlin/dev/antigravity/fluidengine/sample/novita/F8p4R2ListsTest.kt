package dev.antigravity.fluidengine.sample.novita

import android.os.Looper
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.ui.fluid.FluidDisclosureGroup
import dev.antigravity.fluidengine.ui.fluid.FluidDisclosureState
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidPreviewGrid
import dev.antigravity.fluidengine.ui.fluid.FluidPreviewGridSection
import dev.antigravity.fluidengine.ui.fluid.FluidPreviewGridState
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEngine
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHapticGrainStyle
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.page.FluidSegmentedPager
import dev.antigravity.fluidengine.ui.page.FluidSegmentedPagerState
import dev.antigravity.fluidengine.ui.page.rememberFluidSegmentedPagerState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private class QuietHaptics : FluidHaptics {
  override val engine: FluidHapticEngine = FluidHapticEngine.Composition
  override fun play(event: FluidHapticEvent) = Unit
  override fun grain(intensity: Float, style: FluidHapticGrainStyle) = Unit
}

/**
 * R2 (F8.4) · liste e griglie nei confini che le prove di F8.4 non attraversavano: la stessa foto in due gruppi, una
 * densità salvata che l'app non ha più, la sezione aperta per TalkBack, il gesto interrotto dal sistema mentre la griglia
 * scorre da sola, e il pager a segmenti senza segmenti o che si accorcia.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-xhdpi")
class F8p4R2ListsTest {

  @get:Rule
  val compose = createComposeRule()

  private lateinit var view: View
  private lateinit var finger: MaterialFinger

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    compose.mainClock.autoAdvance = false
  }

  @After
  fun stop() {
    FluidGlassDiagnostics.resetForTesting()
  }

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      Snapshot.sendApplyNotifications()
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      left -= 16
    }
  }

  private fun show(content: @Composable () -> Unit) {
    compose.setContent {
      view = LocalView.current
      FluidTheme(settings = remember { EngineSettings() }) {
        CompositionLocalProvider(
          LocalFluidHaptics provides remember { QuietHaptics() },
          LocalFluidMotionPolicy provides FluidMotionPolicy(durationScale = 1f),
        ) {
          Box(Modifier.fillMaxSize()) { content() }
        }
      }
    }
    advance(600)
    finger = MaterialFinger(view)
  }

  private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

  private fun count(tag: String): Int =
    compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

  private fun moveTo(from: Offset, to: Offset, steps: Int): Offset {
    var at = from
    val dx = (to.x - from.x) / steps
    val dy = (to.y - from.y) / steps
    repeat(steps) {
      advance(16)
      at = Offset(at.x + dx, at.y + dy)
      finger.move(at)
    }
    return at
  }

  // --- #44 · la stessa chiave in due gruppi ---------------------------------------------------------------------------

  @Test
  fun `la stessa foto in due gruppi non fa cadere la griglia e compare in tutti e due`() {
    val selection = FluidSelectionState<Int>()
    show {
      val sections = remember {
        listOf(
          FluidPreviewGridSection(key = "ieri", title = "Ieri", items = listOf(1, 2, 3)),
          FluidPreviewGridSection(key = "preferite", title = "Preferite", items = listOf(3, 4)),
        )
      }
      FluidPreviewGrid(sections = sections, itemKey = { it }, selection = selection, modifier = Modifier.fillMaxSize()) { id, _ ->
        Box(Modifier.fillMaxSize().background(Color(0xFF406080)).testTag("cella:$id"))
      }
    }
    assertEquals(2, count("cella:3"))
    assertEquals(1, count("cella:4"))
  }

  // --- Il minore della densità ---------------------------------------------------------------------------------------

  @Test
  fun `una densita' salvata fuori dalle colonne di oggi si corregge anche nello stato`() {
    // Il valore che torna da un ripristino dopo un aggiornamento dell'app con meno densità.
    val state = FluidPreviewGridState(initialDensity = 7)
    show {
      val sections = remember { listOf(FluidPreviewGridSection(key = "tutte", title = null, items = (0 until 40).toList())) }
      FluidPreviewGrid(sections = sections, itemKey = { it }, state = state, modifier = Modifier.fillMaxSize()) { id, _ ->
        Box(Modifier.fillMaxSize().testTag("cella:$id"))
      }
    }
    assertEquals(2, state.density)
  }

  // --- Il minore della sezione richiudibile ----------------------------------------------------------------------------

  private fun headerTexts(): List<String> =
    compose.onNode(hasText("Allegati") and hasClickAction(), useUnmergedTree = false).fetchSemanticsNode()
      .config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

  @Test
  fun `a sezione aperta TalkBack non legge il riepilogo nel titolo`() {
    val disclosure = FluidDisclosureState(initiallyExpanded = false)
    show {
      Column(Modifier.padding(16.dp).fillMaxWidth()) {
        FluidDisclosureGroup(title = "Allegati", summary = "3 allegati", state = disclosure, backdrop = null) {
          Text("Preventivo.pdf", Modifier.padding(16.dp).height(80.dp))
        }
      }
    }
    assertTrue("chiusa: il riepilogo dice cosa c'e' dentro", "3 allegati" in headerTexts())
    disclosure.expanded = true
    advance(800)
    assertFalse("aperta: il contenuto lo dice gia'", "3 allegati" in headerTexts())
    disclosure.expanded = false
    advance(800)
    assertTrue("richiusa torna", "3 allegati" in headerTexts())
  }

  // --- Il sospetto 10: il gesto interrotto dal sistema mentre la griglia scorre da sola ----------------------------------

  @Test
  fun `un gesto interrotto dal sistema ferma lo scorrimento automatico`() {
    val selection = FluidSelectionState<Int>()
    val state = FluidPreviewGridState(initialDensity = 1)
    show {
      val sections = remember { listOf(FluidPreviewGridSection(key = "tutte", title = null, items = (0 until 300).toList())) }
      FluidPreviewGrid(
        sections = sections, itemKey = { it }, state = state, selection = selection, inSelectionMode = true,
        modifier = Modifier.fillMaxSize().testTag("griglia"),
      ) { id, _ -> Box(Modifier.fillMaxSize().testTag("cella:$id")) }
    }
    val grid = bounds("griglia")
    val start = bounds("cella:0").center
    finger.down(start)
    var at = moveTo(start, Offset(bounds("cella:2").center.x, start.y), steps = 8)
    at = moveTo(at, Offset(at.x, grid.bottom - 30f), steps = 20)
    advance(400)
    val moving = state.gridState.firstVisibleItemIndex
    assertTrue("scorre da sola: $moving", moving > 0)
    finger.cancel(at)
    advance(48)
    val stopped = state.gridState.firstVisibleItemIndex to state.gridState.firstVisibleItemScrollOffset
    advance(1000)
    assertEquals("ferma dopo l'interruzione", stopped, state.gridState.firstVisibleItemIndex to state.gridState.firstVisibleItemScrollOffset)
  }

  /** Il sospetto 10, la convivenza: in modo selezione un pizzico di lato resta un pizzico, non una selezione in fila. */
  @Test
  fun `in modo selezione il pizzico di lato cambia le colonne e non sceglie niente`() {
    val selection = FluidSelectionState<Int>()
    val state = FluidPreviewGridState(initialDensity = 1)
    show {
      val sections = remember { listOf(FluidPreviewGridSection(key = "tutte", title = null, items = (0 until 300).toList())) }
      FluidPreviewGrid(
        sections = sections, itemKey = { it }, state = state, selection = selection, inSelectionMode = true,
        modifier = Modifier.fillMaxSize().testTag("griglia"),
      ) { id, _ -> Box(Modifier.fillMaxSize().testTag("cella:$id")) }
    }
    val target = bounds("cella:17")
    val focal = Offset(target.center.x, target.top + target.height * 0.3f)
    var a = Offset(focal.x - 60f, focal.y)
    var b = Offset(focal.x + 60f, focal.y)
    finger.downAll(listOf(a, b))
    repeat(16) {
      advance(16)
      a = Offset(a.x - 3f, a.y)
      b = Offset(b.x + 3f, b.y)
      finger.moveAll(listOf(a, b))
    }
    advance(32)
    finger.upAll(listOf(a, b))
    advance(800)
    assertEquals("a 3 colonne", 0, state.density)
    assertEquals("niente scelto", 0, (0 until 300).count { selection.isSelected(it) })
  }

  // --- Il minore e il sospetto 16 del pager a segmenti ---------------------------------------------------------------------

  @Test
  fun `il pager senza segmenti non cade e non mostra niente`() {
    show {
      FluidSegmentedPager(segments = emptyList(), modifier = Modifier.fillMaxSize()) { index, _ ->
        Text("Pagina $index", Modifier.testTag("pagina:$index"))
      }
    }
    assertEquals(0, count("pagina:0"))
  }

  @Test
  fun `il pager che si accorcia sull'ultima pagina torna sull'ultima che c'e'`() {
    var segments by mutableStateOf(listOf("Tutte", "Album", "Persone"))
    lateinit var state: FluidSegmentedPagerState
    show {
      state = rememberFluidSegmentedPagerState(initialPage = 2, pageCount = { segments.size })
      FluidSegmentedPager(segments = segments, state = state, modifier = Modifier.fillMaxSize()) { index, _ ->
        Text("Pagina $index", Modifier.testTag("pagina:$index"))
      }
    }
    assertEquals(2, state.selectedPage)
    segments = listOf("Tutte", "Album")
    advance(800)
    assertEquals(1, state.pagerState.currentPage)
    assertEquals(1, count("pagina:1"))
  }
}
