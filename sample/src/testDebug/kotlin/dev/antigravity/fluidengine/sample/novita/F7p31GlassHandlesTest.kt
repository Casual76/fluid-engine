package dev.antigravity.fluidengine.sample.novita

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.ui.fluid.FluidRangeSlider
import dev.antigravity.fluidengine.ui.fluid.FluidRulerDial
import dev.antigravity.fluidengine.ui.fluid.FluidScrubber
import dev.antigravity.fluidengine.ui.fluid.FluidSettledSemanticsDiagnostics
import dev.antigravity.fluidengine.ui.fluid.FluidSlider
import dev.antigravity.fluidengine.ui.fluid.FluidSwitch
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
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
 * F7.31 · I pomelli di vetro si spostano nel disegno: con l'accessibilità accesa (come sul Tab S9 di Alessio, dove c'è
 * «Collegamento a Windows»), una maniglia di vetro che corre non fa più ricontrollare la pagina ogni 100 ms.
 *
 * Su «base R4» la maniglia si spostava con la traslazione del suo `graphicsLayer` e la goccia cresceva con la sua scala:
 * per Compose un cambio di layout (`updateLayerParameters` → `onLayoutChange`), e ogni 100 ms il delegato
 * dell'accessibilità ricontrollava la pagina e mandava eventi. Qui si contano gli eventi `TYPE_WINDOW_CONTENT_CHANGED`
 * (ogni ricontrollo che trova un nodo spostato ne manda): col dito giù, e i fotogrammi con eventi mentre il lettore suona.
 *
 * Prima di F7.31: lo scrubber in ascolto 30 fotogrammi con eventi in 3 s, il cursore, il cursore a due maniglie, lo
 * scrubber e l'interruttore sotto il dito decine di eventi. Dopo: zero col dito giù, e in ascolto solo la semantica
 * (una volta al secondo). Il righello di F7.24 non ha una maniglia di vetro: era già a zero, ed è la guardia.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F7p31GlassHandlesTest {

  @get:Rule
  val compose = createComposeRule()

  private lateinit var view: View
  private lateinit var finger: MaterialFinger

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    FluidSettledSemanticsDiagnostics.resetForTesting()
    // Un servizio di accessibilita' acceso, prima di setContent.
    val shadow = shadowOf(manager())
    shadow.setEnabled(true)
    shadow.setTouchExplorationEnabled(true)
    shadow.setEnabledAccessibilityServiceList(listOf(AccessibilityServiceInfo()))
    compose.mainClock.autoAdvance = false
  }

  @After
  fun stop() {
    FluidGlassDiagnostics.resetForTesting()
    FluidSettledSemanticsDiagnostics.resetForTesting()
  }

  private fun manager(): AccessibilityManager =
    ApplicationProvider.getApplicationContext<Context>().getSystemService(AccessibilityManager::class.java)

  private fun events(): Int =
    shadowOf(manager()).sentAccessibilityEvents.count { it.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED }

  private fun advance(ms: Long, each: () -> Unit = {}) {
    var left = ms
    while (left > 0) {
      each()
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      Snapshot.sendApplyNotifications()
      left -= 16
    }
  }

  private fun show(content: @Composable () -> Unit) {
    compose.setContent {
      view = LocalView.current
      FluidTheme(settings = remember { EngineSettings() }) {
        Box(Modifier.fillMaxSize()) { content() }
      }
    }
    advance(600)
    finger = MaterialFinger(view)
  }

  private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

  /**
   * Un trascinamento lento col dito vero, a passi da 16 ms: quanti eventi di accessibilita' col dito giu' (a due terzi
   * del gesto, prima del passo lento di un secondo della semantica), e in quanti fotogrammi.
   */
  private fun eventsUnderTheFinger(tag: String, from: Offset, step: Offset, steps: Int = 30): Pair<Int, Int> {
    val before = events()
    var last = before
    var frames = 0
    var during = -1
    var framesDuring = -1
    var at = from
    finger.down(at)
    advance(16)
    for (i in 1..steps) {
      at += step
      finger.move(at)
      advance(16)
      val now = events()
      if (now != last) frames++
      last = now
      if (i == steps * 2 / 3) {
        during = now - before
        framesDuring = frames
      }
    }
    finger.up(at)
    advance(1_200)
    println("F7p31 $tag: $during eventi in $framesDuring fotogrammi col dito giu' (${steps * 2 / 3 * 16} ms), ${events() - before} in tutto")
    return during to framesDuring
  }

  @Test
  fun `il cursore sotto il dito non fa ricontrollare la pagina`() {
    var value by mutableFloatStateOf(0.3f)
    show {
      FluidSlider(value = value, onValueChange = { value = it }, modifier = Modifier.padding(top = 200.dp).padding(horizontal = 20.dp).testTag("cursore"))
    }
    val b = bounds("cursore")
    val (during, _) = eventsUnderTheFinger("cursore", Offset(b.left + b.width * 0.3f, b.center.y), Offset(4f, 0f))
    assertTrue("il dito ha mosso il cursore: $value", value > 0.4f)
    assertEquals("nessun evento di accessibilita' col dito giu'", 0, during)
  }

  @Test
  fun `il cursore a due maniglie sotto il dito non fa ricontrollare la pagina`() {
    var range by mutableStateOf(0.2f..0.8f)
    show {
      FluidRangeSlider(value = range, onValueChange = { range = it }, modifier = Modifier.padding(top = 200.dp).padding(horizontal = 20.dp).testTag("intervallo"))
    }
    val b = bounds("intervallo")
    // Sulla maniglia di destra (all'80%, piu' o meno), verso sinistra.
    val (during, _) = eventsUnderTheFinger("intervallo", Offset(b.left + b.width * 0.78f, b.center.y), Offset(-4f, 0f))
    assertTrue("il dito ha mosso la maniglia: $range", range.endInclusive < 0.7f)
    assertEquals("nessun evento di accessibilita' col dito giu'", 0, during)
  }

  @Test
  fun `lo scrubber sotto il dito non fa ricontrollare la pagina`() {
    var position by mutableFloatStateOf(20_000f)
    show {
      FluidScrubber(
        position = { position },
        onSeek = { position = it },
        valueRange = 0f..60_000f,
        modifier = Modifier.padding(top = 200.dp).testTag("scrubber"),
      )
    }
    val b = bounds("scrubber")
    val (during, _) = eventsUnderTheFinger("scrubber", Offset(b.left + b.width * 0.34f, b.center.y), Offset(4f, 0f))
    assertTrue("il dito ha mosso lo scrubber: $position", position > 25_000f)
    assertEquals("nessun evento di accessibilita' col dito giu'", 0, during)
  }

  @Test
  fun `l'interruttore sotto il dito non fa ricontrollare la pagina`() {
    var checked by mutableStateOf(false)
    show {
      FluidSwitch(checked = checked, onCheckedChange = { checked = it }, modifier = Modifier.padding(top = 200.dp, start = 40.dp).testTag("interruttore"))
    }
    val b = bounds("interruttore")
    // Sul pomello (a sinistra, spento), mezzo pixel alla volta: oltre la presa ma non oltre la meta'.
    val (during, _) = eventsUnderTheFinger("interruttore", Offset(b.left + b.height / 2f, b.center.y), Offset(0.5f, 0f))
    assertEquals("nessun evento di accessibilita' col dito giu'", 0, during)
  }

  @Test
  fun `il righello di F7_24 resta senza ricontrolli`() {
    var degrees by mutableFloatStateOf(0f)
    show {
      FluidRulerDial(value = { degrees }, onValueChange = { degrees = it }, contentDescription = "Raddrizza", modifier = Modifier.padding(top = 200.dp).testTag("ghiera"))
    }
    val b = bounds("ghiera")
    val (during, _) = eventsUnderTheFinger("righello", Offset(b.center.x, b.center.y), Offset(-4f, 0f))
    assertTrue("il dito ha mosso il righello: $degrees", degrees > 1f)
    assertEquals("nessun evento di accessibilita' col dito giu'", 0, during)
  }

  /** Un lettore finto che suona: la posizione la porta avanti la prova, un fotogramma alla volta. */
  private class Playing {
    var positionMillis by mutableLongStateOf(5_000L)
  }

  @Test
  fun `lo scrubber mentre il lettore suona non fa ricontrollare la pagina ogni 100 ms`() {
    val session = Playing()
    show {
      FluidScrubber(
        position = { session.positionMillis.toFloat() },
        onSeek = { session.positionMillis = it.toLong() },
        valueRange = 0f..58_804f,
        modifier = Modifier.padding(top = 200.dp).testTag("scrubber"),
      )
    }
    advance(300)
    val before = events()
    val published = FluidSettledSemanticsDiagnostics.publishes
    var frames = 0
    var last = before
    advance(3_000) {
      session.positionMillis += 16
      val now = events()
      if (now != last) frames++
      last = now
    }
    val semantics = FluidSettledSemanticsDiagnostics.publishes - published
    println("F7p31 scrubber in ascolto: $frames fotogrammi con eventi (${events() - before} eventi), $semantics semantiche in 3 s")
    // La semantica si rifa' una volta al secondo (la scritta di TalkBack cambia al secondo): tre o quattro fotogrammi.
    // Prima di F7.31 ce n'erano una trentina, uno ogni 100 ms, per la maniglia che si spostava con la traslazione.
    assertTrue("fotogrammi con eventi in 3 s d'ascolto: $frames", frames <= 4)
  }
}
