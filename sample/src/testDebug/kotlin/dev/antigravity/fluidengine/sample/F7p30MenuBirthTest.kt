@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.bilanci.BalanceHarness
import dev.antigravity.fluidengine.sample.bilanci.CountingBitmap
import dev.antigravity.fluidengine.sample.bilanci.CountingRenderEffect
import dev.antigravity.fluidengine.sample.bilanci.CountingRenderNode
import dev.antigravity.fluidengine.sample.bilanci.CountingRuntimeShader
import dev.antigravity.fluidengine.sample.bilanci.GlassFactoryCounters
import dev.antigravity.fluidengine.sample.measure.MeasureScenario
import dev.antigravity.fluidengine.sample.measure.ScenarioActions
import dev.antigravity.fluidengine.sample.measure.ScenarioScene
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidScrollToTopBus
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.ProvideFluidChrome
import dev.antigravity.fluidengine.ui.fluid.fluidChromePages
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidChromeController
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.After
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

/** F7.30: le funzioni composte (i gruppi del compilatore Compose) mentre è acceso, per nome e per file, coi tempi. */
@OptIn(InternalComposeTracingApi::class)
private class BirthTracer : CompositionTracer {
  class Entry(var count: Int = 0, var inclusive: Long = 0, var self: Long = 0)

  val byName = HashMap<String, Entry>()
  val byFile = HashMap<String, Entry>()
  private val stack = ArrayList<Triple<String, Long, LongArray>>()
  override fun isTraceInProgress(): Boolean = true

  override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
    stack += Triple(info, System.nanoTime(), LongArray(1))
  }

  override fun traceEventEnd() {
    if (stack.isEmpty()) return
    val (info, start, children) = stack.removeAt(stack.size - 1)
    val total = System.nanoTime() - start
    stack.lastOrNull()?.third?.let { it[0] += total }
    val name = info.substringBefore(" (")
    val file = info.substringAfter(" (", "").substringBefore(":")
    byName.getOrPut(name) { Entry() }.let { it.count++; it.inclusive += total; it.self += total - children[0] }
    byFile.getOrPut(file) { Entry() }.let { it.count++; it.self += total - children[0] }
  }

  val composed: Int get() = byName.values.sumOf { it.count }

  fun clear() {
    byName.clear()
    byFile.clear()
  }
}

/**
 * F7.30 · **La nascita del menu**, al PC: che cosa costa il fotogramma in cui il menu del gruppo in basso nasce
 * (`menu-gruppo-barra`, il palco della galleria, formato telefono). Sul Tab S9 (F7.27) quel fotogramma lavorava 27–34 ms
 * sul filo principale a ogni apertura, anche a caldo: 13–16 ms per comporre il menu e 14–18 ms per registrarne il primo
 * disegno, perché il contenuto del menu nasceva con l'apertura e moriva con l'uscita.
 *
 * Non si misurano millisecondi (il PC è carico): si conta il lavoro, con le stesse misure dei bilanci (F7.28). Per ogni
 * apertura (cinque: la prima del processo e quattro a caldo), nel fotogramma della nascita: le funzioni composte (per file,
 * dal tracciatore del compilatore), i nodi di layout nati, i layer, le bitmap e i filtri nati (le ombre che contano le
 * fabbriche); e nella pressione che la precede, quanto si compone lì. Sul codice di F7.29 la nascita a caldo componeva
 * 467 funzioni e faceva nascere 21 nodi e 25 layer, ogni volta.
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
  sdk = [35],
  qualifiers = "w411dp-h891dp-420dpi",
  application = GlassBenchDevice::class,
  shadows = [
    GlassBenchHeap::class, CountingRenderNode::class, CountingBitmap::class, CountingRenderEffect::class,
    CountingRuntimeShader::class,
  ],
)
class F7p30MenuBirthTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val harness = BalanceHarness(compose)
  private lateinit var restore: AutoCloseable
  private val tracer = BirthTracer()

  @Before fun start() {
    compose.mainClock.autoAdvance = false
    restore = harness.install(frameLog = false)
  }

  @After fun stop() {
    Composer.setTracer(null)
    restore.close()
  }

  private fun countNodes(node: LayoutNode?): Int = if (node == null) 0 else 1 + node.children.sumOf { countNodes(it) }

  /** Un fotogramma misurato: quello che si è composto, quanti nodi e fabbriche sono nati. */
  private class Frame(
    val index: Int, val composed: Int, val nodesBorn: Int, val factories: GlassFactoryCounters.Factories, val report: String,
    val millis: String = "", val functions: String = "",
  )

  private class Opening(val cycle: Int, val frames: List<Frame>) {
    /** La nascita: il fotogramma che compone di più nei primi venti dell'apertura. */
    val birth: Frame get() = frames.take(20).maxBy { it.composed }
  }

  private fun measure(): List<Opening> {
    val actions = ScenarioActions()
    var scope: CoroutineScope? = null
    harness.show {
      FluidTheme(settings = remember { EngineSettings() }) {
        scope = rememberCoroutineScope()
        val chrome = rememberFluidChromeController()
        val host = rememberFluidGlassModalHostState()
        val backdrop = rememberGlassBackdrop()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          ProvideFluidChrome(chrome, 0.dp, remember { FluidScrollToTopBus() }) {
            Box(Modifier.fillMaxSize()) {
              Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).background(Color(0xFF7FA7D9)))
              Box(Modifier.fillMaxSize().fluidChromePages()) {
                Box(Modifier.align(Alignment.Center)) { ScenarioScene(MeasureScenario.MenuGroupBar, actions, 0.dp) }
              }
              FluidGlassModalHost(host, chrome.activeBackdrop.value ?: backdrop)
            }
          }
        }
      }
    }
    repeat(60) { harness.frame() }
    check(actions.ready) { "scena non pronta" }
    val looper = shadowOf(Looper.getMainLooper())
    val openings = ArrayList<Opening>()
    Composer.setTracer(tracer)
    try {
      for (cycle in 1..5) {
        for (phase in listOf("apre", "chiude")) {
          val action = checkNotNull(actions.action(phase))
          harness.runOnUi { checkNotNull(scope).launch { action() } }
          val frames = ArrayList<Frame>()
          for (f in 0 until 50) {
            tracer.clear()
            val nodesBefore = countNodes(harness.root())
            val fabBefore = GlassFactoryCounters.snapshot()
            val t0 = System.nanoTime()
            compose.mainClock.advanceTimeByFrame()
            Snapshot.sendApplyNotifications()
            val t1 = System.nanoTime()
            looper.idleFor(Duration.ofMillis(BalanceHarness.FrameMillis))
            Snapshot.sendApplyNotifications()
            val t2 = System.nanoTime()
            harness.draw()
            val t3 = System.nanoTime()
            val fab = GlassFactoryCounters.snapshot() - fabBefore
            val files = tracer.byFile.entries.sortedByDescending { it.value.count }.take(12)
              .joinToString(", ") { "${it.key}×${it.value.count}" }
            val functions = tracer.byName.entries.sortedByDescending { it.value.self }.take(30)
              .joinToString(FunctionSeparator) { "%.2f self %.2f incl ×%d %s".format(it.value.self / 1e6, it.value.inclusive / 1e6, it.value.count, it.key) }
            val millis = "frame %.2f + %.2f, disegno %.2f ms".format((t1 - t0) / 1e6, (t2 - t1) / 1e6, (t3 - t2) / 1e6)
            frames += Frame(f, tracer.composed, countNodes(harness.root()) - nodesBefore, fab, files, millis, functions)
          }
          if (phase == "apre") openings += Opening(cycle, frames)
          val shown = frames.filter { it.composed > 0 || it.nodesBorn != 0 || it.factories.heavy > 0 }.take(12)
          println("F730 ciclo=$cycle $phase\n  " + shown.joinToString("\n  ") {
            "f=${it.index} ${it.millis} funzioni=${it.composed} nodi nati=${it.nodesBorn} fab=${it.factories} · ${it.report}"
          })
          // Fra un tratto e l'altro il menu finisce di uscire (col menu tenuto pronto: si parcheggia).
          repeat(30) { harness.frame() }
        }
      }
    } finally {
      Composer.setTracer(null)
    }
    assertNull("gesto", actions.failure)
    return openings
  }

  private fun report(label: String, openings: List<Opening>): String {
    val summary = openings.joinToString(LineSeparator) { o ->
      val b = o.birth
      "apertura ${o.cycle}: nascita f=${b.index}, ${b.millis}, ${b.composed} funzioni, ${b.nodesBorn} nodi nati, ${b.factories} · ${b.report}"
    }
    println("F730 NASCITA $label$LineSeparator$summary")
    println("F730 FUNZIONI $label, la nascita a caldo (apertura 4)$FunctionSeparator${openings[3].birth.functions}")
    return summary
  }

  /** La strada di F7.29 (il menu nasce e muore con ogni apertura), nella stessa JVM: stampa soltanto, per il confronto. */
  @Test fun `la strada di prima, per il confronto`() {
    val was = dev.antigravity.fluidengine.ui.fluid.FluidMenuReadiness.enabled
    dev.antigravity.fluidengine.ui.fluid.FluidMenuReadiness.enabled = false
    try {
      report("strada di prima", measure())
    } finally {
      dev.antigravity.fluidengine.ui.fluid.FluidMenuReadiness.enabled = was
    }
  }

  @Test fun `la nascita del menu del gruppo, prima apertura e a caldo`() {
    val openings = measure()
    val summary = report("menu tenuto pronto", openings)
    val warm = openings.drop(1)
    // A caldo il corpo del menu c'è già (parcheggiato dall'apertura di prima): nella nascita non nascono i suoi nodi, né
    // le bitmap delle icone. Nascono il vetro (finestra, velo, goccia, i due materiali e lo strato della lente, che
    // parcheggiati non tengono layer vivi) e quello che la barra fa per cedere il gruppo, quindi i layer nati restano quasi
    // quelli di prima. Le funzioni composte non scendono (il corpo si riesegue quasi tutto, con le voci nuove
    // dell'apertura): layer e funzioni non sono un tetto qui, li dice il rapporto.
    for (o in warm) {
      val b = o.birth
      assertTrue("apertura ${o.cycle}, a caldo: ${b.nodesBorn} nodi nati nella nascita (sul codice di F7.29 21)\n  $summary",
        b.nodesBorn <= WarmBirthNodes)
      assertTrue("apertura ${o.cycle}, a caldo: ${b.factories.bitmaps} bitmap nate nella nascita (sul codice di F7.29 4)\n  $summary",
        b.factories.bitmaps <= WarmBirthBitmaps)
    }
  }

  private companion object {
    const val LineSeparator = "\n  "
    const val FunctionSeparator = "\n      "

    /**
     * I tetti della nascita a caldo, dalla misura di F7.30 (1 nodo, la goccia che rinasce; 0 bitmap): il codice di F7.29 li
     * superava tutti e due (21 nodi, 4 bitmap).
     */
    const val WarmBirthNodes = 2
    const val WarmBirthBitmaps = 0L
  }
}
