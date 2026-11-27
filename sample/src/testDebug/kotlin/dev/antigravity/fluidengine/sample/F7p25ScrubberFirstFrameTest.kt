package dev.antigravity.fluidengine.sample

import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileViewer
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidPageSource
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
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

/** Conta, per nome, le funzioni composte (i gruppi del compilatore Compose) mentre è acceso. */
@OptIn(InternalComposeTracingApi::class)
private class ScrubberTracer : CompositionTracer {
  val names = linkedMapOf<String, Int>()
  val files = linkedMapOf<String, Int>()

  /** Per le funzioni del vetro: il primo antenato scritto nell'engine fuori dal vetro (chi l'ha ricomposto). */
  val glassOwners = linkedMapOf<String, Int>()
  private val stack = ArrayList<String>()
  override fun isTraceInProgress(): Boolean = true
  override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
    val name = info.substringBefore(" (")
    names[name] = (names[name] ?: 0) + 1
    val file = info.substringAfter(" (", "").substringBefore(":")
    files[file] = (files[file] ?: 0) + 1
    if (file == "GlassMaterial.kt") {
      val owner = stack.lastOrNull { it.contains("FluidFastScroller.kt") || it.contains("FluidPagesPreset.kt") || it.contains("FluidViewer") }
      val key2 = owner ?: stack.lastOrNull() ?: "?"
      glassOwners[key2] = (glassOwners[key2] ?: 0) + 1
    }
    stack += info
  }

  override fun traceEventEnd() {
    if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
  }
}

/**
 * F7.25 · Il primo fotogramma della maniglia dello scorrimento rapido, col dito vero, sul PDF di 5000 pagine: che cosa
 * si compone quando la si prende e ai primi salti.
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class F7p25ScrubberFirstFrameTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val tracer = ScrubberTracer()
  private lateinit var view: View
  private lateinit var finger: MaterialFinger

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() {
    Composer.setTracer(null)
    FluidGlassDiagnostics.resetForTesting()
  }

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      Snapshot.sendApplyNotifications()
      left -= 16
    }
  }

  private class Giant : FluidPageSource {
    override val count = 5_000
    override suspend fun size(index: Int) = IntSize(595, 842)
    override suspend fun render(index: Int, target: IntSize) = androidx.compose.ui.graphics.ImageBitmap(target.width, target.height)
  }

  private fun strip(): Rect =
    compose.onNode(hasContentDescription("Scorri il documento"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

  /** Le funzioni composte nell'ultimo fotogramma, per file sorgente. */
  private val lastFiles = linkedMapOf<String, Int>()
  private var lastTotal = 0

  /** La bolla nell'albero unito, quello che vede TalkBack (e posata: si vede). */
  private fun bubbleShown(): Boolean =
    compose.onAllNodes(hasText("di 5000", substring = true)).fetchSemanticsNodes().any { it.layoutInfo.isPlaced }

  /** La bolla composta ma non posata: c'è nell'albero non unito, sotto il contenitore che la tiene pronta. */
  private fun bubbleWaiting(): Boolean =
    compose.onAllNodes(hasText("di 5000", substring = true), useUnmergedTree = true).fetchSemanticsNodes()
      .let { nodes -> nodes.isNotEmpty() && nodes.none { it.layoutInfo.isPlaced } }

  private fun frame(label: String, log: MutableList<String>) {
    advance(16)
    lastFiles.clear()
    lastFiles.putAll(tracer.files)
    lastTotal = tracer.names.values.sum()
    if (tracer.glassOwners.isNotEmpty()) log += "  vetro ricomposto in $label sotto: ${tracer.glassOwners}"
    tracer.glassOwners.clear()
    val top = tracer.names.entries.sortedByDescending { it.value }.take(14)
      .joinToString(", ") { "${it.key.substringAfterLast('.')}×${it.value}" }
    val byFile = tracer.files.entries.sortedByDescending { it.value }.take(10).joinToString(", ") { "${it.key}×${it.value}" }
    val scroller = tracer.names.entries.filter { it.key.contains("FluidFastScroller") }.joinToString(", ") { "${it.key.substringAfterLast('.')}×${it.value}" }
    log += "$label: ${tracer.names.values.sum()} funzioni · per file: $byFile\n    · $top\n    · maniglia: $scroller"
    tracer.names.clear()
    tracer.files.clear()
  }

  @Test
  fun `che cosa si compone quando si prende la maniglia`() {
    compose.setContent {
      view = LocalView.current
      FluidTheme(settings = remember { EngineSettings() }) {
        val document = remember { FluidViewerContent.Pages(Giant()) }
        FluidFileViewer(1, content = { document })
      }
    }
    var waited = 0
    while (waited < 6_000 &&
      compose.onAllNodes(hasContentDescription("Pagina 1 di 5000"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
    ) {
      advance(32)
      Thread.sleep(4)
      waited += 32
    }
    advance(500)
    finger = MaterialFinger(view)
    // La lista si muove un poco col dito: la maniglia compare.
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
    val bounds = strip()
    val thumb = Offset(bounds.right - 18f * density, bounds.top + 26f * density)
    val log = mutableListOf<String>()
    assertFalse("a maniglia libera la bolla non è nell'albero di TalkBack", bubbleShown())
    Composer.setTracer(tracer)
    tracer.names.clear()
    tracer.files.clear()
    finger.down(thumb)
    frame("giù", log)
    val down = LinkedHashMap(lastFiles)
    val downTotal = lastTotal
    var y = thumb.y
    repeat(6) { step ->
      y += 30f * density
      finger.move(Offset(thumb.x, y))
      frame("mossa ${step + 1}", log)
    }
    assertTrue("mentre si trascina la bolla c'è", bubbleShown())
    finger.up(Offset(thumb.x, y))
    frame("su", log)
    Composer.setTracer(null)
    println("F7.25 · la maniglia, fotogramma per fotogramma:\n" + log.joinToString("\n"))
    // Nel fotogramma in cui si prende la maniglia la bolla non si compone: c'era già. Né il suo vetro, né quello della
    // goccia della maniglia (prima lo scorrimento rapido si ricomponeva tutto, perché leggeva `scrubbing`).
    for (file in listOf("GlassMaterial.kt", "FluidGlassModel.kt", "GlassDrop.kt")) {
      assertEquals("$file nel fotogramma del tocco: $down", 0, down[file] ?: 0)
    }
    assertTrue("nel fotogramma del tocco $downTotal funzioni composte (prima 120; resta la transizione della bolla): $down", downTotal <= 64)
    advance(600)
    assertFalse("lasciata la maniglia, la bolla se ne va", bubbleShown())
    assertTrue("e torna ad aspettare, composta ma non posata", bubbleWaiting())
  }
}
