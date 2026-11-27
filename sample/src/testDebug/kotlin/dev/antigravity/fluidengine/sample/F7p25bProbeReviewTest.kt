@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample

import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.glass.backdrop.Backdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.field.FluidGlassProbeDiagnostics
import dev.antigravity.fluidengine.ui.glass.field.GlassBackdropStats
import dev.antigravity.fluidengine.ui.glass.field.GlassProbeClient
import dev.antigravity.fluidengine.ui.glass.field.GlassProbeMaxSlots
import dev.antigravity.fluidengine.ui.glass.field.GlassProbeOutcome
import dev.antigravity.fluidengine.ui.glass.field.GlassProbeWindowReader
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
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

/**
 * F7.25b · I tre difetti della sonda trovati dalla review R2 (`artifacts/liquid-glass/review/R2/muse-vetro.md`), col
 * lettore vero della finestra:
 *
 * - **5**: una sonda che non ottiene il lettore (caselle piene, sfrattata da un vetro più importante, lettore che non
 *   nasce) si spegneva per tutta la vita del nodo; ora riprova al primo evento dopo un'attesa che cresce;
 * - **6**: il lettore che non nasce lasciava aperto il filo che aveva già avviato;
 * - **7**: dopo una lettura saltata, una sorgente che non avvisa (come un `CanvasBackdrop`) non veniva più riletta a
 *   cadenza, anche diventata pronta.
 *
 * Le sorgenti delle prove sono mute (nessun layer: la cadenza fissa) e, dove serve che una lettura non resti in volo
 * (in Robolectric l'immagine del lettore non arriva mai), non pronte: la lettura salta senza aspettare niente.
 * Le letture si contano dai risvegli della sonda (`Read` e `ReadRerecord`), che crescono a ogni lettura partita.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class F7p25bProbeReviewTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  /** Una sorgente che non avvisa quando cambia: niente layer, quindi la sonda la rilegge a cadenza fissa. */
  private class MuteBackdrop(var ready: Boolean) : Backdrop {
    override val isCoordinatesDependent: Boolean = false
    override fun isReadyToSample(): Boolean = ready
    override fun DrawScope.drawBackdrop(
      density: Density,
      coordinates: LayoutCoordinates?,
      layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
      drawRect(Color(0xFF7FA7D9))
    }
  }

  /** Chi occupa una casella senza leggere niente, più importante di ogni vetro vero. */
  private class Squatter : GlassProbeClient {
    override val thumbnailLayer: GraphicsLayer? = null
    override val priority: Float = Float.MAX_VALUE
    override fun onEvicted() = Unit
    override fun prepareThumbnail(): Boolean = false
    override fun onProbeResult(outcome: GlassProbeOutcome, stats: GlassBackdropStats?) = Unit
    override fun onReaderLost() = Unit
  }

  private var showProbe by mutableStateOf(true)
  private var nudge by mutableIntStateOf(0)
  private lateinit var view: View
  private var diagnosticsWere = false

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassProbeDiagnostics.resetForTesting()
    diagnosticsWere = FluidGlassDiagnostics.enabled
    FluidGlassDiagnostics.enabled = true
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() {
    GlassProbeWindowReader.constructionStepForTesting = null
    FluidGlassDiagnostics.enabled = diagnosticsWere
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassProbeDiagnostics.resetForTesting()
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

  private fun scene(backdrop: Backdrop) {
    compose.setContent {
      view = LocalView.current
      Box(Modifier.fillMaxSize().background(Color.White)) {
        if (showProbe) {
          Box(Modifier.size((80 + nudge).dp).then(FluidGlassProbeDiagnostics.probe(backdrop) {}))
        }
      }
    }
    advance(200)
  }

  /** Le letture partite (con o senza miniatura nuova), da [start]. */
  private fun reads(): Long = FluidGlassProbeDiagnostics.wakes().let { (it["Read"] ?: 0L) + (it["ReadRerecord"] ?: 0L) }

  private fun reader(): GlassProbeWindowReader {
    var got: GlassProbeWindowReader? = null
    compose.runOnUiThread { got = GlassProbeWindowReader.obtain(view) }
    return requireNotNull(got) { "il lettore della finestra non è nato" }
  }

  private fun probeThreads(): Int = Thread.getAllStackTraces().keys.count { it.isAlive && it.name == "FluidGlass.sonda" }

  @Test
  fun `5 - una sonda senza casella legge appena una casella si libera`() {
    showProbe = false
    scene(MuteBackdrop(ready = false))
    val reader: GlassProbeWindowReader = reader()
    val slots = mutableListOf<Int>()
    compose.runOnUiThread { repeat(GlassProbeMaxSlots) { slots += reader.register(Squatter()) } }
    showProbe = true
    advance(1_000)
    assertEquals("senza casella la sonda non legge", 0L, reads())
    assertTrue("la casella è stata rifiutata", FluidGlassProbeDiagnostics.totalSlotsRefused > 0)
    compose.runOnUiThread { reader.unregister(slots.first()) }
    // Un evento qualunque: il vetro cresce di due dp (una geometria nuova).
    nudge = 2
    advance(2_000)
    assertTrue("liberata una casella, la sonda torna a leggere (letture ${reads()})", reads() > 0)
  }

  @Test
  fun `5 - una sonda sfrattata torna a leggere quando c'è di nuovo posto`() {
    scene(MuteBackdrop(ready = false))
    advance(300)
    assertTrue("la sonda ha letto prima dello sfratto", reads() > 0)
    val reader: GlassProbeWindowReader = reader()
    val slots = mutableListOf<Int>()
    compose.runOnUiThread { repeat(GlassProbeMaxSlots) { slots += reader.register(Squatter()) } }
    advance(1_000)
    val evicted = reads()
    compose.runOnUiThread { reader.unregister(slots.first()) }
    nudge = 2
    advance(2_000)
    assertTrue("sfrattata, la sonda torna a leggere quando c'è posto (${reads()} contro $evicted)", reads() > evicted)
  }

  @Test
  fun `5 - una sonda il cui lettore non è nato riprova al prossimo evento`() {
    GlassProbeWindowReader.constructionStepForTesting = { step ->
      if (step == GlassProbeWindowReader.StepRenderer) throw IllegalStateException("renderer rifiutato per la prova")
    }
    scene(MuteBackdrop(ready = false))
    advance(500)
    assertEquals("senza lettore la sonda non legge", 0L, reads())
    GlassProbeWindowReader.constructionStepForTesting = null
    nudge = 2
    advance(2_000)
    assertTrue("il lettore ora nasce: la sonda legge (letture ${reads()})", reads() > 0)
  }

  @Test
  fun `6 - il lettore che non nasce non lascia aperto il suo filo`() {
    showProbe = false
    scene(MuteBackdrop(ready = true))
    val before = probeThreads()
    GlassProbeWindowReader.constructionStepForTesting = { step ->
      if (step == GlassProbeWindowReader.StepRenderer) throw IllegalStateException("renderer rifiutato per la prova")
    }
    var got: GlassProbeWindowReader? = null
    compose.runOnUiThread { got = GlassProbeWindowReader.obtain(view) }
    assertNull("il lettore non nasce", got)
    val deadline = System.currentTimeMillis() + 2_000
    while (probeThreads() > before && System.currentTimeMillis() < deadline) Thread.sleep(10)
    assertEquals("fili `FluidGlass.sonda` aperti dopo una costruzione fallita", before, probeThreads())
  }

  @Test
  fun `6 - anche se cade l'ultimo pezzo, il filo gia' partito si chiude`() {
    showProbe = false
    scene(MuteBackdrop(ready = true))
    val before = probeThreads()
    val lives = GlassProbeWindowReader.liveCount
    GlassProbeWindowReader.constructionStepForTesting = { step ->
      if (step == GlassProbeWindowReader.StepPing) throw IllegalStateException("ping rifiutato per la prova")
    }
    var got: GlassProbeWindowReader? = null
    compose.runOnUiThread { got = GlassProbeWindowReader.obtain(view) }
    assertNull("il lettore non nasce", got)
    assertEquals("nessun lettore vivo in più", lives, GlassProbeWindowReader.liveCount)
    val deadline = System.currentTimeMillis() + 2_000
    while (probeThreads() > before && System.currentTimeMillis() < deadline) Thread.sleep(10)
    assertEquals("il filo partito prima del ping si è chiuso", before, probeThreads())
    // E senza l'intoppo il lettore nasce, nella stessa finestra.
    GlassProbeWindowReader.constructionStepForTesting = null
    compose.runOnUiThread { got = GlassProbeWindowReader.obtain(view) }
    assertTrue("poi il lettore nasce", got != null)
    compose.runOnUiThread { got?.release() }
  }

  @Test
  fun `7 - dopo una lettura saltata la cadenza di una sorgente muta riparte da sola`() {
    val mute = MuteBackdrop(ready = false)
    scene(mute)
    advance(300)
    val skipped = reads()
    assertTrue("le prime letture saltano: la sorgente non è pronta", skipped > 0)
    val requests = FluidGlassProbeDiagnostics.totalRequests
    // La sorgente diventa pronta senza dirlo a nessuno: niente revisioni, niente si muove.
    mute.ready = true
    advance(1_500)
    println("F7.25b · difetto 7: letture saltate $skipped, poi ${reads()}; richieste al lettore ${FluidGlassProbeDiagnostics.totalRequests}")
    assertTrue("pronta la sorgente, entro la cadenza la sonda rilegge (${reads()} contro $skipped)", reads() > skipped)
    assertTrue("e la lettura arriva al lettore", FluidGlassProbeDiagnostics.totalRequests > requests)
  }
}
