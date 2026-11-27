@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample

import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.ui.fluid.FluidFastScroller
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerBackdrop
import dev.antigravity.fluidengine.ui.glass.field.FluidGlassProbeDiagnostics
import dev.antigravity.fluidengine.ui.glass.field.GlassProbeFence
import dev.antigravity.fluidengine.ui.glass.field.GlassProbeNode
import dev.antigravity.fluidengine.ui.glass.field.GlassProbeWindowReader
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F7.25e (review R3, difetto 1) · La bolla dello scorrimento rapido, a maniglia libera, è composta ma non posata
 * (`ScrubberBubbleHost`, F7.25): il suo vetro ha una sonda che non può leggere, perché non ha una posizione. Prima di
 * F7.25e la sonda si iscriveva alla pagina appena attaccata e a ogni ridisegno della pagina chiedeva una lettura: il
 * lettore della finestra nasceva per lei, il ping andava al RenderThread, col RenderThread indietro c'erano l'attesa
 * breve e il tetto, e la lettura finiva sempre a vuoto (niente miniatura senza posizione).
 *
 * La scena: una pagina col suo fondale e una lista di 500 righe, sopra lo scorrimento rapido col fondale della pagina,
 * e nessun altro vetro. La pagina «cambia» con la sua revisione, una a fotogramma ([LayerBackdrop.bumpRevision], quello
 * che la pagina fa quando si registra di nuovo): così il ritmo è dell'orologio della prova, non di Robolectric.
 *
 * - **Bolla nascosta** (RenderThread libero e indietro): nessuna lettura, nessun lettore della finestra, nessun ping,
 *   nessuna attesa breve, nessun tentativo.
 * - **Maniglia presa**: la bolla si vede e la sua sonda legge come prima; quando la bolla sparisce torna a tacere.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class F7p25eHiddenBubbleProbeTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private var renderThreadFree = true
  private val fences = mutableListOf<FakeFence>()
  private var page: GlassBackdropState? = null
  private lateinit var view: View
  private var changing = false
  private var diagnosticsWere = false

  /** Il ping finto del RenderThread: torna subito (libero) o mai (indietro). */
  private inner class FakeFence(private val onReturn: (Int) -> Unit) : GlassProbeFence {
    private val main = Handler(Looper.getMainLooper())
    override var returned: Int = 0
    override val healthy: Boolean = true
    var pings = 0
    var awaits = 0

    override fun ping(seq: Int) {
      pings++
      if (renderThreadFree) {
        returned = seq
        main.post { onReturn(seq) }
      }
    }

    override fun await(seq: Int, timeoutNanos: Long): Boolean {
      awaits++
      return false
    }

    override fun release() = Unit
  }

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassProbeDiagnostics.resetForTesting()
    diagnosticsWere = FluidGlassDiagnostics.enabled
    FluidGlassDiagnostics.enabled = true
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    GlassProbeWindowReader.fenceForTesting = { onReturn -> FakeFence(onReturn).also { fences += it } }
    compose.mainClock.autoAdvance = false
  }

  @After
  fun stop() {
    GlassProbeWindowReader.fenceForTesting = null
    FluidGlassDiagnostics.enabled = diagnosticsWere
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassProbeDiagnostics.resetForTesting()
  }

  private val pageLayer: LayerBackdrop get() = checkNotNull(checkNotNull(page).layerBackdrop)

  /** Fotogrammi da 16 ms dell'orologio della prova; con [changing] la pagina ha una revisione a fotogramma. */
  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      Snapshot.sendApplyNotifications()
      if (changing) pageLayer.bumpRevision()
      left -= 16
    }
  }

  private fun showScene() {
    compose.setContent {
      view = LocalView.current
      FluidTheme(settings = remember { EngineSettings() }) {
        val backdrop = rememberGlassBackdrop()
        page = backdrop
        val list = rememberLazyListState()
        Box(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize().glassBackdropSource(backdrop)) {
            LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
              items(500) { Text("Riga $it", Modifier.height(48.dp)) }
            }
          }
          FluidFastScroller(list, label = { "Bolla $it" }, backdrop = backdrop)
        }
      }
    }
    advance(1_000)
  }

  private class Window(
    val reads: Long,
    val pageRevisions: Long,
    val revisions: Long,
    val requests: Long,
    val pings: Int,
    val awaits: Int,
    val paths: Map<String, Long>,
  )

  /** Che cosa fanno le sonde in [ms] di pagina che cambia. */
  private fun measure(ms: Long): Window {
    fences.forEach { it.pings = 0; it.awaits = 0 }
    val wakes = FluidGlassProbeDiagnostics.wakes()
    val requests = FluidGlassProbeDiagnostics.totalRequests
    val paths = FluidGlassProbeDiagnostics.timedReads()
    val pageRevision = pageLayer.revision
    changing = true
    advance(ms)
    changing = false
    val after = FluidGlassProbeDiagnostics.wakes()
    fun delta(key: String) = (after[key] ?: 0L) - (wakes[key] ?: 0L)
    val nowPaths = FluidGlassProbeDiagnostics.timedReads()
    return Window(
      reads = delta("Read") + delta("ReadRerecord"),
      pageRevisions = pageLayer.revision - pageRevision,
      revisions = delta("Revision"),
      requests = FluidGlassProbeDiagnostics.totalRequests - requests,
      pings = fences.sumOf { it.pings },
      awaits = fences.sumOf { it.awaits },
      paths = nowPaths.mapValues { (key, value) -> value - (paths[key] ?: 0L) }.filterValues { it > 0L },
    )
  }

  private fun report(label: String, w: Window, seconds: Double) {
    println(
      "F7.25e · $label: in ${seconds}s di pagina che cambia (una revisione a fotogramma) letture partite ${w.reads} " +
        "(${"%.1f".format(w.reads / seconds)} al secondo), revisioni della pagina ${w.pageRevisions}, viste dalle sonde ${w.revisions}, " +
        "richieste al lettore ${w.requests}, ping ${w.pings}, attese brevi ${w.awaits}, letture per strada ${w.paths}; " +
        "sonde vive ${FluidGlassProbeDiagnostics.liveProbes}, lettori della finestra ${GlassProbeWindowReader.liveCount}, " +
        "lettori della pagina ${pageLayer.readerCount}",
    )
  }

  /**
   * Le sonde attaccate dell'albero, e se il loro nodo è posato. Nella scena l'unico vetro con una sonda è la bolla: a
   * maniglia libera `[false]`, una sonda non posata. (L'etichetta della bolla non serve: prima della prima presa è vuota.)
   */
  private fun probesPlaced(): List<Boolean> {
    val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode().layoutInfo as LayoutNode
    val out = ArrayList<Boolean>()
    fun visit(layout: LayoutNode) {
      var node: Modifier.Node? = layout.nodes.head
      while (node != null) {
        if (node is GlassProbeNode && node.isAttached) out += layout.isPlaced
        node = node.child
      }
      for (child in layout.children) visit(child)
    }
    visit(root)
    return out
  }

  private fun hiddenBubble(label: String) {
    val readersBefore = GlassProbeWindowReader.liveCount
    showScene()
    assertEquals("a maniglia libera una sonda sola, quella della bolla, non posata", listOf(false), probesPlaced())
    val w = measure(3_000)
    report(label, w, 3.0)
    assertTrue("la pagina è cambiata a ogni fotogramma: ${w.pageRevisions} revisioni", w.pageRevisions >= 180L)
    assertEquals("letture della bolla nascosta (tentativi a vuoto)", 0L, w.reads)
    assertEquals("letture per strada della bolla nascosta", emptyMap<String, Long>(), w.paths)
    assertEquals("ping al RenderThread per la bolla nascosta", 0, w.pings)
    assertEquals("attese brevi del principale per la bolla nascosta", 0, w.awaits)
    assertEquals("lettori della finestra nati per la bolla nascosta", 0, GlassProbeWindowReader.liveCount - readersBefore)
  }

  @Test
  fun `la bolla nascosta non legge mentre la pagina cambia, RenderThread libero`() {
    renderThreadFree = true
    hiddenBubble("bolla nascosta, RenderThread libero")
  }

  @Test
  fun `la bolla nascosta non legge mentre la pagina cambia, RenderThread indietro`() {
    renderThreadFree = false
    hiddenBubble("bolla nascosta, RenderThread indietro")
  }

  @Test
  fun `con la maniglia presa la bolla legge come prima e quando sparisce torna a tacere`() {
    renderThreadFree = true
    showScene()
    val finger = MaterialFinger(view)
    val density = view.resources.displayMetrics.density
    // La lista si muove un poco col dito: la maniglia compare.
    val middle = Offset(view.width / 2f, view.height / 2f)
    finger.down(middle)
    var at = middle
    repeat(8) {
      advance(16)
      at = Offset(at.x, at.y - 12f * density)
      finger.move(at)
    }
    finger.up(at)
    advance(400)
    val strip = compose.onNode(hasContentDescription("Scorrimento rapido"), useUnmergedTree = true)
      .fetchSemanticsNode().boundsInRoot
    val thumb = Offset(strip.right - 18f * density, strip.top + 26f * density)
    finger.down(thumb)
    advance(48)
    // La maniglia presa e trascinata piano: la bolla si vede (posata) e la pagina sotto cambia.
    var y = thumb.y
    fences.forEach { it.pings = 0; it.awaits = 0 }
    val wakes = FluidGlassProbeDiagnostics.wakes()
    changing = true
    repeat(60) {
      y += 2f * density
      finger.move(Offset(thumb.x, y))
      advance(16)
    }
    changing = false
    val shownReads = (FluidGlassProbeDiagnostics.wakes()["Read"] ?: 0L) + (FluidGlassProbeDiagnostics.wakes()["ReadRerecord"] ?: 0L) -
      (wakes["Read"] ?: 0L) - (wakes["ReadRerecord"] ?: 0L)
    val bubbleShown = compose.onAllNodes(hasText("Bolla", substring = true)).fetchSemanticsNodes().any { it.layoutInfo.isPlaced }
    println(
      "F7.25e · maniglia presa: bolla posata $bubbleShown, letture in ~1 s $shownReads, " +
        "lettori della finestra ${GlassProbeWindowReader.liveCount}, letture per strada ${FluidGlassProbeDiagnostics.timedReads()}",
    )
    assertTrue("mentre si trascina la bolla si vede", bubbleShown)
    assertTrue("la bolla che si vede legge come prima: $shownReads letture", shownReads > 0)
    assertTrue("il lettore della finestra c'è", GlassProbeWindowReader.liveCount > 0)
    finger.up(Offset(thumb.x, y))
    // La bolla esce (la sua animazione) e torna nel contenitore che la tiene pronta, non posata.
    advance(1_500)
    assertEquals("dopo il rilascio la bolla torna composta e non posata", listOf(false), probesPlaced())
    val w = measure(2_000)
    report("dopo il rilascio", w, 2.0)
    assertEquals("letture della bolla tornata nascosta", 0L, w.reads)
    assertEquals("attese brevi della bolla tornata nascosta", 0, w.awaits)
  }
}
