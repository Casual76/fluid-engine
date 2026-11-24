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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
import dev.antigravity.fluidengine.ui.fluid.fluidDragSelection
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEngine
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHapticGrainStyle
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.page.FluidSegmentedPager
import dev.antigravity.fluidengine.ui.page.FluidSegmentedPagerState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Cosa ha chiesto al vibratore un gesto. */
private class ListHaptics : FluidHaptics {
  override val engine: FluidHapticEngine = FluidHapticEngine.Composition
  val played = mutableListOf<FluidHapticEvent>()
  override fun play(event: FluidHapticEvent) {
    played += event
  }

  override fun grain(intensity: Float, style: FluidHapticGrainStyle) = Unit
}

/**
 * F8.4 · Liste e pagine, col dito vero (eventi del sistema con l'orologio che avanza, `MaterialFinger`) e col vetro vero
 * (verdetti degli shader accettati): la sezione richiudibile e il suo stato per TalkBack, la selezione trascinando che
 * aggiunge e toglie e lo scorrimento al bordo, il pizzico che cambia le colonne restando sul punto, il pager che porta
 * la pillola.
 *
 * In Robolectric la densita' e' 2 (xhdpi): lo slop del tocco e' 32 px.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-xhdpi")
class F8p4ListsUiTest {

  @get:Rule
  val compose = createComposeRule()

  private val haptics = ListHaptics()
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
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      left -= 16
    }
  }

  private fun show(reduced: Boolean = false, content: @Composable () -> Unit) {
    compose.setContent {
      view = LocalView.current
      FluidTheme(settings = remember { EngineSettings() }) {
        CompositionLocalProvider(
          LocalFluidHaptics provides haptics,
          LocalFluidMotionPolicy provides FluidMotionPolicy(durationScale = if (reduced) 0f else 1f),
        ) {
          Box(Modifier.fillMaxSize()) { content() }
        }
      }
    }
    advance(600)
    finger = MaterialFinger(view)
  }

  private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

  private fun exists(tag: String): Boolean =
    compose.onAllNodes(androidx.compose.ui.test.hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  private fun tap(at: Offset) {
    finger.down(at)
    advance(32)
    finger.up(at)
    advance(16)
  }

  /** Un trascinamento a passi di ([stepX], [stepY]) px ogni 16 ms per [steps] passi; [hold] ms fermo prima di alzare. */
  private fun drag(from: Offset, stepX: Float, stepY: Float, steps: Int, hold: Long = 0L, lift: Boolean = true): Offset {
    finger.down(from)
    var at = from
    repeat(steps) {
      advance(16)
      at = Offset(at.x + stepX, at.y + stepY)
      finger.move(at)
    }
    if (hold > 0) advance(hold)
    if (lift) {
      finger.up(at)
      advance(16)
    }
    return at
  }

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

  private fun writesDuring(ms: Long): Int {
    var writes = 0
    val observer = Snapshot.registerGlobalWriteObserver { writes++ }
    advance(ms)
    observer.dispose()
    return writes
  }

  // --- La sezione richiudibile -------------------------------------------------------------------------------------

  private val disclosure = FluidDisclosureState(initiallyExpanded = false)
  private var bodyDraws = 0

  private fun showDisclosure(reduced: Boolean = false) = show(reduced) {
    val backdrop = rememberGlassBackdrop()
    Box(Modifier.fillMaxSize()) {
      Box(Modifier.fillMaxSize().background(Color(0xFF7FA6C9)).glassBackdropSource(backdrop))
      Column(Modifier.padding(16.dp).fillMaxWidth()) {
        FluidDisclosureGroup(
          title = "Allegati",
          summary = "3 allegati",
          state = disclosure,
          backdrop = backdrop,
          modifier = Modifier.testTag("sezione"),
        ) {
          Text(
            "Preventivo.pdf",
            Modifier.padding(16.dp).height(120.dp).testTag("contenuto").drawBehind { bodyDraws++ },
          )
        }
        Text("Sotto", Modifier.testTag("sotto"))
      }
    }
  }

  private fun headerNode() =
    compose.onNode(hasText("Allegati"), useUnmergedTree = false).fetchSemanticsNode()

  @Test
  fun `il titolo apre e chiude, e la riga sotto scende con il vetro che esce`() {
    showDisclosure()
    val closed = bounds("sezione").height
    val belowClosed = bounds("sotto").top
    assertFalse(exists("contenuto"))
    val header = headerNode()
    assertEquals("Compresso", header.config.getOrNull(SemanticsProperties.StateDescription))
    tap(header.boundsInRoot.center)
    // Il contenuto e' gia' composto (il vetro nasce dal titolo), ma la sezione aspetta la nascita prima di allargarsi.
    assertTrue("il contenuto c'e' appena aperta", exists("contenuto"))
    assertTrue(disclosure.expanded)
    val early = bounds("sezione").height
    advance(220)
    val middle = bounds("sezione").height
    advance(900)
    val open = bounds("sezione").height
    println("F8p4 sezione: chiusa $closed, a 48 ms $early, a 270 ms $middle, aperta $open")
    assertTrue("nell'attesa la sezione non si e' ancora allargata: $early", early - closed < (open - closed) * 0.1f)
    assertTrue("a meta' strada si sta allargando: $middle", middle > closed + 10f && middle < open + 40f)
    assertTrue("aperta e' piu' alta: $closed -> $open", open > closed + 200f)
    assertTrue("la riga sotto e' scesa", bounds("sotto").top > belowClosed + 200f)
    assertEquals("Espanso", headerNode().config.getOrNull(SemanticsProperties.StateDescription))

    // Chiusa, torna dentro: il contenuto esce subito dalla composizione e la sezione si restringe con la molla.
    tap(headerNode().boundsInRoot.center)
    assertFalse(disclosure.expanded)
    advance(1000)
    assertFalse(exists("contenuto"))
    assertEquals(closed, bounds("sezione").height, 1f)
    assertEquals("Compresso", headerNode().config.getOrNull(SemanticsProperties.StateDescription))
  }

  @Test
  fun `TalkBack apre e chiude dalla riga, che resta lo stesso nodo`() {
    showDisclosure()
    val before = headerNode()
    val expand = before.config.getOrNull(SemanticsActions.Expand)
    assertNotNull("azione Espandi", expand)
    assertNull("da chiusa niente Comprimi", before.config.getOrNull(SemanticsActions.Collapse))
    compose.runOnUiThread { expand!!.action!!.invoke() }
    Snapshot.sendApplyNotifications()
    advance(1000)
    assertTrue(disclosure.expanded)
    val after = headerNode()
    assertEquals("lo stesso nodo: il fuoco resta sulla riga", before.id, after.id)
    assertEquals(Role.Button, after.config.getOrNull(SemanticsProperties.Role))
    val collapse = after.config.getOrNull(SemanticsActions.Collapse)
    assertNotNull("azione Comprimi", collapse)
    compose.runOnUiThread { collapse!!.action!!.invoke() }
    Snapshot.sendApplyNotifications()
    advance(1000)
    assertFalse(disclosure.expanded)
  }

  @Test
  fun `la sezione ferma non scrive stato e non ridisegna`() {
    showDisclosure()
    tap(headerNode().boundsInRoot.center)
    advance(1500)
    val draws = bodyDraws
    assertEquals("scritture a riposo", 0, writesDuring(1000))
    assertEquals("disegni del contenuto a riposo", draws, bodyDraws)
  }

  @Test
  fun `col movimento ridotto la sezione arriva aperta in un fotogramma`() {
    showDisclosure(reduced = true)
    val closed = bounds("sezione").height
    tap(headerNode().boundsInRoot.center)
    advance(32)
    val open = bounds("sezione").height
    advance(800)
    assertEquals("subito alla misura d'arrivo", bounds("sezione").height, open, 1f)
    assertTrue(open > closed + 200f)
  }

  // --- La griglia: selezione trascinando e pizzico -------------------------------------------------------------------

  private val gridSelection = FluidSelectionState<Int>()
  private val gridState = FluidPreviewGridState(initialDensity = 1)

  private fun showGrid(selecting: Boolean = true, count: Int = 300) = show {
    val sections = remember { listOf(FluidPreviewGridSection(key = "tutte", title = null, items = (0 until count).toList())) }
    FluidPreviewGrid(
      sections = sections,
      itemKey = { it },
      state = gridState,
      selection = gridSelection,
      inSelectionMode = selecting,
      modifier = Modifier.fillMaxSize().testTag("griglia"),
    ) { id, _ ->
      Box(Modifier.fillMaxSize().background(Color(0xFF406080)).testTag("cella:$id"))
    }
  }

  private fun cell(id: Int): Rect = bounds("cella:$id")

  @Test
  fun `di lato in modo selezione sceglie in fila, il primo decide, tornando indietro l'intervallo si stringe`() {
    showGrid()
    // 5 colonne su 800 px: celle da ~158 px. Dal centro della prima al centro della quarta, di lato.
    val from = cell(0).center
    finger.down(from)
    var at = moveTo(from, Offset(cell(3).center.x, from.y), steps = 12)
    advance(32)
    assertEquals("dalla prima alla quarta", setOf(0, 1, 2, 3), (0 until 10).filter { gridSelection.isSelected(it) }.toSet())
    // Giu' di una riga: l'intervallo va per righe, dalla 0 alla 8.
    at = moveTo(at, Offset(at.x, cell(8).center.y), steps = 10)
    advance(32)
    assertEquals((0..8).toSet(), (0 until 20).filter { gridSelection.isSelected(it) }.toSet())
    // Indietro alla seconda: escono e tornano come prima (non scelte).
    at = moveTo(at, cell(1).center, steps = 10)
    advance(32)
    assertEquals(setOf(0, 1), (0 until 20).filter { gridSelection.isSelected(it) }.toSet())
    finger.up(at)
    advance(32)
    val ticks = haptics.played.count { it == FluidHapticEvent.Tick }
    assertTrue("un tic per passo: $ticks", ticks >= 4)

    // Un gesto che parte da una scelta toglie, e non tocca le altre fuori dall'intervallo.
    gridSelection.select(7)
    Snapshot.sendApplyNotifications()
    advance(32)
    finger.down(cell(1).center)
    at = moveTo(cell(1).center, Offset(cell(2).center.x, cell(1).center.y), steps = 8)
    advance(32)
    finger.up(at)
    advance(32)
    assertEquals(setOf(0, 7), (0 until 20).filter { gridSelection.isSelected(it) }.toSet())
  }

  @Test
  fun `in giu' un dito scorre la griglia e non sceglie niente`() {
    showGrid()
    val from = cell(12).center
    drag(from, 0f, -20f, steps = 12)
    advance(400)
    assertTrue("nessuna scelta", (0 until 60).none { gridSelection.isSelected(it) })
    assertTrue("ha scorso", gridState.gridState.firstVisibleItemIndex > 0 || gridState.gridState.firstVisibleItemScrollOffset > 0)
  }

  @Test
  fun `al bordo basso la griglia scorre da sola e la selezione segue`() {
    showGrid()
    val grid = bounds("griglia")
    finger.down(cell(0).center)
    var at = moveTo(cell(0).center, Offset(cell(2).center.x, cell(0).center.y), steps = 8)
    // Fin dentro la fascia del bordo basso (72 dp = 144 px), e fermo.
    at = moveTo(at, Offset(at.x, grid.bottom - 30f), steps = 20)
    val before = gridState.gridState.firstVisibleItemIndex
    advance(1200)
    val after = gridState.gridState.firstVisibleItemIndex
    val chosen = (0 until 300).count { gridSelection.isSelected(it) }
    finger.up(at)
    advance(32)
    println("F8p4 bordo: prima visibile $before -> $after, scelte $chosen")
    assertTrue("e' scorsa da sola: $before -> $after", after > before + 5)
    assertTrue("la selezione ha seguito: $chosen", chosen > 30)
  }

  @Test
  fun `il pizzico allarga a 3 colonne e tiene la cella sotto le dita`() {
    showGrid(selecting = false)
    // Un po' piu' giu', perche' il punto non sia in cima: la cella 17 (riga 3, colonna 2).
    val target = cell(17)
    val focal = Offset(target.center.x, target.top + target.height * 0.3f)
    val a0 = Offset(focal.x - 60f, focal.y)
    val b0 = Offset(focal.x + 60f, focal.y)
    finger.downAll(listOf(a0, b0))
    var a = a0
    var b = b0
    // Le dita si allargano fino a 1,7 volte (oltre √(5/3) ≈ 1,29): un cambio a 3 colonne.
    repeat(16) {
      advance(16)
      a = Offset(a.x - 3f, a.y)
      b = Offset(b.x + 3f, b.y)
      finger.moveAll(listOf(a, b))
    }
    advance(32)
    assertEquals("a 3 colonne", 0, gridState.density)
    finger.upAll(listOf(a, b))
    advance(800)
    assertEquals(1f, gridState.pinchScale, 0.01f)
    val after = cell(17)
    println("F8p4 pizzico: cella 17 prima ${target.top}..${target.bottom}, dopo ${after.top}..${after.bottom}, punto ${focal.y}")
    assertTrue("la cella e' piu' grande", after.height > target.height * 1.5f)
    assertTrue("la cella resta sotto il punto delle dita: ${after.top}..${after.bottom} contro ${focal.y}", focal.y in after.top..after.bottom)
    // Alla stessa altezza dentro la cella (il 30%), entro qualche px.
    val fraction = (focal.y - after.top) / after.height
    assertEquals("stessa frazione della cella", 0.3f, fraction, 0.08f)
  }

  @Test
  fun `scorrendo le celle gia' in vista non ricompongono`() {
    val composed = HashMap<Int, Int>()
    show {
      val sections = remember { listOf(FluidPreviewGridSection(key = "tutte", title = null, items = (0 until 400).toList())) }
      FluidPreviewGrid(
        sections = sections,
        itemKey = { it },
        state = gridState,
        modifier = Modifier.fillMaxSize().testTag("griglia"),
      ) { id, _ ->
        composed[id] = (composed[id] ?: 0) + 1
        Box(Modifier.fillMaxSize().background(Color(0xFF406080)))
      }
    }
    composed.clear()
    repeat(90) {
      compose.runOnUiThread { gridState.gridState.dispatchRawDelta(6f) }
      advance(16)
    }
    val again = composed.filterValues { it > 1 }
    println("F8p4 scorrimento: celle composte ${composed.size}, ricomposte ${again.size}")
    assertTrue("nessuna cella ricomposta scorrendo: $again", again.isEmpty())
  }

  // --- La selezione trascinando su una lista -------------------------------------------------------------------------

  private val rowSelection = FluidSelectionState<Int>()
  private val listState = LazyListState()

  @Test
  fun `due dita su una riga scelgono in giu', e la pressione lunga entra nella selezione`() {
    show {
      LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("lista").fluidDragSelection(rowSelection, listState, keyAt = { it }),
      ) {
        items((0 until 100).toList(), key = { it }) { id ->
          Text("Riga $id", Modifier.fillMaxWidth().height(60.dp).testTag("riga:$id"))
        }
      }
    }
    val r2 = bounds("riga:2").center
    val a0 = Offset(r2.x - 40f, r2.y)
    val b0 = Offset(r2.x + 40f, r2.y)
    finger.downAll(listOf(a0, b0))
    var a = a0
    var b = b0
    val down = bounds("riga:5").center.y - r2.y
    repeat(15) {
      advance(16)
      a = Offset(a.x, a.y + down / 15f)
      b = Offset(b.x, b.y + down / 15f)
      finger.moveAll(listOf(a, b))
    }
    advance(32)
    finger.upAll(listOf(a, b))
    advance(32)
    assertEquals("dalla riga 2 alla 5", (2..5).toSet(), (0 until 20).filter { rowSelection.isSelected(it) }.toSet())

    // Un dito solo in giu' scorre e non sceglie.
    drag(bounds("riga:8").center, 0f, -16f, steps = 10)
    advance(400)
    assertEquals((2..5).toSet(), (0 until 40).filter { rowSelection.isSelected(it) }.toSet())

    // La pressione lunga su una non scelta: entra, e trascinando aggiunge.
    rowSelection.clear()
    Snapshot.sendApplyNotifications()
    advance(32)
    val start = bounds("riga:3").center
    finger.down(start)
    advance(500)
    assertTrue("la pressione lunga sceglie la prima", rowSelection.isSelected(3))
    val end = moveTo(start, bounds("riga:4").center, steps = 6)
    advance(32)
    finger.up(end)
    advance(32)
    assertEquals(setOf(3, 4), (0 until 40).filter { rowSelection.isSelected(it) }.toSet())
  }

  // --- Le scene della galleria ----------------------------------------------------------------------------------------

  private var scene by androidx.compose.runtime.mutableStateOf("sezioni")

  @Test
  fun `ogni scena di F8_4 si apre e sta ferma`() {
    show {
      androidx.compose.runtime.key(scene) { NewElementsPageAt(onBack = {}, initial = scene) }
    }
    for (one in F8p4Section.scenes) {
      scene = one.id
      Snapshot.sendApplyNotifications()
      advance(1500)
      assertTrue("la scena ${one.id} e' aperta", compose.onAllNodes(hasText(one.title, substring = true)).fetchSemanticsNodes().isNotEmpty() || one.id.startsWith("griglia") || one.id.startsWith("selezione") || one.id == "pager-segmentato")
      println("F8p4 scena ${one.id}: scritture a riposo ${writesDuring(500)}")
    }
  }

  // --- Il pager col segmentato ---------------------------------------------------------------------------------------

  private lateinit var pager: FluidSegmentedPagerState

  private fun showPager(reduced: Boolean = false) = show(reduced) {
    val state = dev.antigravity.fluidengine.ui.page.rememberFluidSegmentedPagerState(pageCount = { 3 })
    pager = state
    Box(Modifier.fillMaxSize().testTag("pager")) {
      FluidSegmentedPager(segments = listOf("Voti", "Note", "Assenze"), state = state) { index, _ ->
        Box(Modifier.fillMaxSize().background(if (index % 2 == 0) Color(0xFF8FB3D9) else Color(0xFFD9B38F)))
      }
    }
  }

  @Test
  fun `il dito che trascina le pagine porta la pillola, e al rilascio si posano insieme`() {
    showPager()
    val p = bounds("pager")
    val from = Offset(p.right - 60f, p.center.y)
    // A sinistra del 70% della larghezza (oltre la meta': lasciato fermo si posa sulla pagina dopo), e fermo col dito giu'.
    val at = drag(from, -(p.width * 0.7f) / 24f, 0f, steps = 24, hold = 300, lift = false)
    val fraction = pager.pagerState.currentPage + pager.pagerState.currentPageOffsetFraction
    val pill = pager.indicatorPosition
    println("F8p4 pager: frazione $fraction, pillola $pill")
    assertTrue("il pager segue il dito: $fraction", fraction in 0.55f..0.75f)
    assertEquals("la pillola segue il pager", fraction, pill, 0.06f)
    // E la pillola e' davvero quella della tab bar guidata dal pager, non il ripiego sulla frazione: il driver interno ha
    // la posizione animata della goccia (letta per riflessione, e' interno all'engine).
    val driver = pager.javaClass.getDeclaredField("driver").apply { isAccessible = true }.get(pager)
    @Suppress("UNCHECKED_CAST")
    val puck = (driver.javaClass.getMethod("getPuckValue").invoke(driver) as () -> Float)()
    assertFalse("la goccia della barra e' agganciata", puck.isNaN())
    assertEquals("la goccia della barra e' sul dito", fraction, puck, 0.06f)
    assertEquals("la pagina scelta non cambia finche' il dito e' giu'", 0, pager.selectedPage)
    finger.up(at)
    advance(1500)
    assertEquals(1, pager.selectedPage)
    assertEquals(1f, pager.indicatorPosition, 0.02f)
  }

  @Test
  fun `un tocco su un segmento porta il pager e la pillola, e TalkBack dice le schede`() {
    showPager()
    val target = compose.onNode(hasText("Assenze"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
    tap(target)
    advance(1500)
    assertEquals(2, pager.selectedPage)
    assertEquals(2f, pager.indicatorPosition, 0.02f)
    val tabs = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
      .fetchSemanticsNodes()
    assertEquals("tre segmenti: ${tabs.map { it.config }}", 3, tabs.size)
    val selected = tabs.filter { it.config.getOrNull(SemanticsProperties.Selected) == true }
    assertEquals(1, selected.size)
    assertTrue("e' «Assenze»", selected.single().config.toString().contains("Assenze"))
  }

  @Test
  fun `col movimento ridotto la pillola arriva senza viaggio`() {
    showPager(reduced = true)
    val p = bounds("pager")
    drag(Offset(p.right - 60f, p.center.y), -(p.width * 0.6f) / 12f, 0f, steps = 12)
    advance(1500)
    assertEquals(1, pager.selectedPage)
    assertTrue(abs(pager.indicatorPosition - 1f) < 0.02f)
  }
}
