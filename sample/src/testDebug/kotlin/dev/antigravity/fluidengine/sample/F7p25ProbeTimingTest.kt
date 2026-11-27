@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample

import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.field.FluidGlassProbeDiagnostics
import dev.antigravity.fluidengine.ui.glass.field.GlassProbeFence
import dev.antigravity.fluidengine.ui.glass.field.GlassProbeFreeSkipFrames
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
import org.robolectric.shadows.ShadowChoreographer

/**
 * F7.25 · La sonda del vetro col RenderThread indietro: il lettore vero della finestra, con un ping finto al posto di
 * quello del RenderThread (in Robolectric il RenderThread non c'è). Una pagina che cambia a ogni fotogramma sotto un
 * tasto di vetro: la sonda chiede una lettura ogni 120 ms.
 *
 * - **RenderThread libero** (il ping torna subito): si legge fra due fotogrammi o al pre-disegno, al più un fotogramma
 *   dopo la richiesta.
 * - **RenderThread indietro** (il ping non torna mai): per [GlassProbeFreeSkipFrames] fotogrammi nessuna attesa, poi
 *   un'attesa breve sola (F7.25c), e si legge solo al tetto, dopo 250 ms (F7.25d, la scelta di Alessio del 26 novembre
 *   2026); mai «come prima», cioè mai subito dopo il disegno della finestra, dove la lettura aspettava tutto il
 *   fotogramma nuovo del RenderThread.
 * - **Il ping torna durante l'attesa breve**: si legge al primo fotogramma dopo quelli liberi.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class F7p25ProbeTimingTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private enum class RenderThread { Free, Behind, BackDuringTheWait }

  private var renderThread = RenderThread.Free
  private val fences = mutableListOf<FakeFence>()
  private var diagnosticsWere = false

  /** Il ping finto: torna subito, mai, o solo quando il principale lo aspetta. */
  private inner class FakeFence(private val onReturn: (Int) -> Unit) : GlassProbeFence {
    private val main = Handler(Looper.getMainLooper())
    override var returned: Int = 0
    override val healthy: Boolean = true
    var pings = 0
    var awaits = 0

    override fun ping(seq: Int) {
      pings++
      if (renderThread == RenderThread.Free) {
        returned = seq
        main.post { onReturn(seq) }
      }
    }

    override fun await(seq: Int, timeoutNanos: Long): Boolean {
      awaits++
      if (renderThread != RenderThread.BackDuringTheWait) return false
      returned = seq
      return true
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

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      left -= 16
    }
  }

  private fun showChangingPage() {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val page = rememberGlassBackdrop()
        val light by rememberInfiniteTransition(label = "pagina").animateFloat(
          initialValue = 0f,
          targetValue = 1f,
          animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
          label = "luce",
        )
        Box(Modifier.fillMaxSize()) {
          Box(
            Modifier.fillMaxSize().glassBackdropSource(page)
              .drawBehind { drawRect(lerp(Color(0xFF101418), Color(0xFFF2F4F7), light)) },
          )
          Column(Modifier.padding(24.dp)) {
            FluidGlassIconButton(onClick = {}, backdrop = page) { Box(Modifier.size(22.dp).background(Color.Black)) }
          }
        }
      }
    }
  }

  private fun report(label: String): Map<String, Long> {
    val paths = FluidGlassProbeDiagnostics.timedReads()
    val frames = FluidGlassProbeDiagnostics.timedFrames()
    println(
      "F7.25 · $label: letture per strada $paths; fotogrammi aspettati ${frames.toList()}; richieste al lettore " +
        "${FluidGlassProbeDiagnostics.totalRequests}; ping ${fences.sumOf { it.pings }}, attese brevi ${fences.sumOf { it.awaits }}",
    )
    return paths
  }

  @Test
  fun `col RenderThread libero si legge al piu' un fotogramma dopo la richiesta`() {
    renderThread = RenderThread.Free
    showChangingPage()
    advance(4_000)
    val paths = report("libero")
    assertTrue("nessuna lettura: $paths", paths.values.sum() > 0)
    assertTrue("strade: $paths", paths.keys.all { it == "Glass.sonda libera" || it == "Glass.sonda pre-disegno" })
    val frames = FluidGlassProbeDiagnostics.timedFrames()
    assertEquals("letture oltre un fotogramma: ${frames.toList()}", 0L, frames.drop(2).sum())
    assertEquals("attese brevi col RenderThread libero", 0, fences.sumOf { it.awaits })
  }

  @Test
  fun `col RenderThread indietro la lettura salta e legge solo al tetto, mai come prima`() {
    renderThread = RenderThread.Behind
    showChangingPage()
    advance(6_000)
    val paths = report("indietro")
    val ceiling = paths["Glass.sonda al tetto"] ?: 0L
    assertTrue("nessuna lettura al tetto: $paths", ceiling > 0)
    assertEquals("strade: $paths", setOf("Glass.sonda al tetto"), paths.keys)
    val frames = FluidGlassProbeDiagnostics.timedFrames()
    // F7.25d: il tetto è un tempo, 250 ms (scelta di Alessio del 26 novembre 2026, «B»), non 15 fotogrammi. In
    // Robolectric un fotogramma di Choreographer dura `ShadowChoreographer.getFrameDelay()` (15 ms di serie). La lettura
    // comincia ad aspettare fra due fotogrammi, quindi arriva al tetto dopo ceil(250 / fotogramma) fotogrammi o uno in
    // più (17 o 18 con 15 ms; col tetto di prima, 15). Prima del tetto nessuna lettura.
    val frameMillis = ShadowChoreographer.getFrameDelay().toMillis()
    val first = ((250L + frameMillis - 1) / frameMillis).toInt()
    assertEquals("letture prima dei 250 ms: ${frames.toList()}", 0L, frames.take(first).sum())
    assertEquals("ogni lettura al tetto dopo $first–${first + 1} fotogrammi: ${frames.toList()}", ceiling,
      frames[first] + frames[first + 1])
    // F7.25c: un'attesa breve sola per lettura, dopo i fotogrammi liberi (più una per la lettura che aspetta ancora alla
    // fine). Prima era una a ogni fotogramma fino al tetto: sul tablet scadevano 5 volte su 6.
    assertTrue(
      "attese brevi ${fences.sumOf { it.awaits }} per $ceiling letture",
      fences.sumOf { it.awaits }.toLong() in ceiling..(ceiling + 1),
    )
    // Nessuna richiesta al lettore prima del tetto: le syncAndDraw sono al più le letture al tetto.
    assertTrue("richieste ${FluidGlassProbeDiagnostics.totalRequests} oltre le letture $ceiling",
      FluidGlassProbeDiagnostics.totalRequests <= ceiling)
  }

  @Test
  fun `se il ping torna durante l'attesa breve si legge subito dopo i fotogrammi liberi`() {
    renderThread = RenderThread.BackDuringTheWait
    showChangingPage()
    advance(4_000)
    val paths = report("torna nell'attesa")
    val awaited = paths["Glass.sonda attesa"] ?: 0L
    assertTrue("nessuna lettura dopo l'attesa breve: $paths", awaited > 0)
    assertEquals("strade: $paths", setOf("Glass.sonda attesa"), paths.keys)
    val frames = FluidGlassProbeDiagnostics.timedFrames()
    assertEquals("letture al fotogramma ${GlassProbeFreeSkipFrames + 1}: ${frames.toList()}", awaited,
      frames[GlassProbeFreeSkipFrames + 1])
  }
}
