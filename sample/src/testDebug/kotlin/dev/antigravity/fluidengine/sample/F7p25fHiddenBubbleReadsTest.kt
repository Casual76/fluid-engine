@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample

import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidFastScroller
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.glassSurface
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerBackdrop
import dev.antigravity.fluidengine.ui.glass.field.FluidGlassProbeDiagnostics
import dev.antigravity.fluidengine.ui.glass.field.GlassProbeNode
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
 * F7.25f (dopo F7.25e, review R3 difetto 1) · Il vetro della bolla nascosta dello scorrimento rapido, composto e non
 * posato (`ScrubberBubbleHost`, F7.25), non deve contare fra i lettori della pagina. Prima di F7.25f il suo nodo di
 * disegno (`DrawBackdropNode`) si iscriveva appena attaccato (`registerReads` in `onAttach`): dove la bolla è l'unico
 * vetro, la pagina (`rememberGlassBackdrop`, su richiesta) restava registrata in un layer anche a maniglia libera. La
 * stessa regola della sonda di F7.25e: si iscrive quando il vetro è posato.
 *
 * La scena: una pagina col suo fondale e una lista di 500 righe, sopra lo scorrimento rapido col fondale della pagina,
 * e nessun altro vetro. I vetri della scena sono due: la bolla e la goccia della maniglia (sempre posata, trasparente
 * quando lo scorrimento rapido è nascosto).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class F7p25fHiddenBubbleReadsTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private var page: GlassBackdropState? = null
  private lateinit var view: View
  private var diagnosticsWere = false

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassProbeDiagnostics.resetForTesting()
    diagnosticsWere = FluidGlassDiagnostics.enabled
    FluidGlassDiagnostics.enabled = true
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    compose.mainClock.autoAdvance = false
  }

  @After
  fun stop() {
    FluidGlassDiagnostics.enabled = diagnosticsWere
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassProbeDiagnostics.resetForTesting()
  }

  private val pageLayer: LayerBackdrop get() = checkNotNull(checkNotNull(page).layerBackdrop)

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      Snapshot.sendApplyNotifications()
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

  /**
   * Un nodo che può contare fra i lettori della pagina: il disegno del vetro (`DrawBackdropNode`) o la sua sonda
   * (`GlassProbeNode`, che si iscrive ai layer su richiesta che campiona). Se è posato, e di quanti layer è lettore.
   */
  private data class GlassNode(val kind: String, val placed: Boolean, val reads: Int)

  /** Questi nodi nell'albero. `DrawBackdropNode` è privato al suo file: per nome; i due elenchi con la riflessione. */
  private fun glassNodes(): List<GlassNode> {
    val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode().layoutInfo as LayoutNode
    val out = ArrayList<GlassNode>()
    fun visit(layout: LayoutNode) {
      var node: Modifier.Node? = layout.nodes.head
      while (node != null) {
        if (node.isAttached && node.javaClass.name == DrawBackdropNodeClass) {
          val field = node.javaClass.getDeclaredField("registeredReads").apply { isAccessible = true }
          out += GlassNode("vetro", layout.isPlaced, (field.get(node) as List<*>).size)
        }
        if (node.isAttached && node is GlassProbeNode) {
          val field = GlassProbeNode::class.java.getDeclaredField("sampledLayers").apply { isAccessible = true }
          out += GlassNode("sonda", layout.isPlaced, (field.get(node) as Set<*>).size)
        }
        node = node.child
      }
      for (child in layout.children) visit(child)
    }
    visit(root)
    return out
  }

  private fun report(label: String): List<GlassNode> {
    val nodes = glassNodes()
    println(
      "F7.25f · $label: ${nodes.map { "${it.kind} " + (if (it.placed) "posato" else "NON posato") + " (lettore di ${it.reads})" }}, " +
        "lettori della pagina ${pageLayer.readerCount}, la pagina si registra ${pageLayer.wantsRecording}",
    )
    return nodes
  }

  private fun assertOnlyPlacedRead(label: String, nodes: List<GlassNode>) {
    assertTrue("$label: un vetro non posato (la bolla) c'è: $nodes", nodes.any { it.kind == "vetro" && !it.placed })
    assertEquals("$label: i vetri non posati non leggono la pagina: $nodes", 0, nodes.filter { !it.placed }.sumOf { it.reads })
    assertEquals(
      "$label: i lettori della pagina sono i vetri posati che la campionano: $nodes",
      nodes.filter { it.placed }.sumOf { it.reads },
      pageLayer.readerCount,
    )
  }

  @Test
  fun `a maniglia libera il vetro della bolla nascosta non legge la pagina`() {
    showScene()
    assertOnlyPlacedRead("dopo l'apertura", report("dopo l'apertura"))
    // La lista si muove (lo scorrimento rapido compare) e si ferma (sparisce): la bolla resta nascosta.
    val finger = MaterialFinger(view)
    val density = view.resources.displayMetrics.density
    val middle = Offset(view.width / 2f, view.height / 2f)
    finger.down(middle)
    var at = middle
    repeat(8) {
      advance(16)
      at = Offset(at.x, at.y - 12f * density)
      finger.move(at)
    }
    finger.up(at)
    advance(3_000)
    assertOnlyPlacedRead("dopo uno scorrimento", report("dopo uno scorrimento"))
  }

  @Test
  fun `con la maniglia presa la bolla legge la pagina e quando sparisce smette`() {
    showScene()
    val before = pageLayer.readerCount
    val finger = MaterialFinger(view)
    val density = view.resources.displayMetrics.density
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
    var y = thumb.y
    repeat(20) {
      y += 3f * density
      finger.move(Offset(thumb.x, y))
      advance(16)
    }
    val shown = report("maniglia presa")
    assertTrue("con la maniglia presa i vetri e le sonde sono tutti posati: $shown", shown.all { it.placed })
    assertTrue("la bolla che si vede legge la pagina: $shown", pageLayer.readerCount > before)
    assertEquals("i lettori della pagina sono i vetri e le sonde che la campionano: $shown", shown.sumOf { it.reads }, pageLayer.readerCount)
    finger.up(Offset(thumb.x, y))
    advance(1_500)
    assertOnlyPlacedRead("dopo il rilascio", report("dopo il rilascio"))
  }

  @Test
  fun `un vetro composto e non posato, solo sulla pagina, non la tiene registrata`() {
    // La regola senza lo scorrimento rapido (dove la goccia della maniglia, posata, legge comunque la pagina): l'unico
    // vetro della scena è misurato e non posato, come la bolla nascosta.
    compose.setContent {
      view = LocalView.current
      FluidTheme(settings = remember { EngineSettings() }) {
        val backdrop = rememberGlassBackdrop()
        page = backdrop
        val tint = GlassDefaults.panelTint()
        Box(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).drawBehind { drawRect(Color.Gray) })
          Layout(content = { Box(Modifier.size(120.dp, 48.dp).glassSurface(state = backdrop, tint = tint, shape = FluidCapsuleShape)) }) {
            measurables, constraints ->
            for (measurable in measurables) measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(0, 0) {}
          }
        }
      }
    }
    advance(1_000)
    val nodes = report("solo un vetro non posato")
    assertTrue("il vetro non posato c'è: $nodes", nodes.any { it.kind == "vetro" && !it.placed })
    assertEquals("lettori della pagina: $nodes", 0, pageLayer.readerCount)
    assertTrue("la pagina non resta registrata per un vetro che non si vede", !pageLayer.wantsRecording)
  }

  private companion object {
    const val DrawBackdropNodeClass = "dev.antigravity.fluidengine.ui.glass.backdrop.DrawBackdropNode"
  }
}
