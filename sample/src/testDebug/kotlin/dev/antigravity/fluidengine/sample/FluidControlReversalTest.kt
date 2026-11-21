@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample

import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidSlider
import dev.antigravity.fluidengine.ui.fluid.FluidSwitch
import dev.antigravity.fluidengine.ui.glass.interaction.glassDropLayerProbe
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEngine
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import kotlin.math.abs
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
 * F6-d, l'inversione **nello stesso contatto**: interruttore e cursore portati a destra e poi indietro senza alzare il
 * dito. Sul tablet non si prova (agent-device traccia solo tratti dritti), quindi qui, fotogramma per fotogramma, con la
 * sonda del layer della goccia (`glassDropLayerProbe`: pressione e le due scale che il layer riceve).
 *
 * Cosa si chiede: mentre il dito c'è la lente resta lente (la pressione non scende, nemmeno dove il verso cambia); la
 * forma cambia senza scalini (lo stiramento passa per lo zero e cambia segno, non ci salta); al rilascio la lente torna
 * pomello in discesa continua. Tre profili: un'inversione umana (rallenta, si ferma un istante, riparte), una brusca, e
 * una con una pausa lunga a metà gesto, in cui le molle della goccia si fermano prima che il dito riparta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xhdpi")
class FluidControlReversalTest {

  @get:Rule val compose = createComposeRule()

  private data class Pose(val progress: Float, val scaleX: Float, val scaleY: Float) {
    val aspect: Float get() = scaleX / scaleY
  }

  private class Haptics : FluidHaptics {
    override val engine: FluidHapticEngine = FluidHapticEngine.Composition
    val played = mutableListOf<FluidHapticEvent>()
    override fun play(event: FluidHapticEvent) { played += event }
  }

  private var latest: Pose? = null
  private val haptics = Haptics()

  /** Gli eventi del dito come li vede chi sta sopra al controllo: tempo, x in pixel, giù o su. Per il rapporto. */
  private val events = mutableListOf<String>()
  private val watch = Modifier.pointerInput(Unit) {
    awaitPointerEventScope {
      while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        event.changes.forEach { events += "${it.uptimeMillis}:${"%.1f".format(it.position.x)}:${if (it.pressed) "g" else "s"}" }
      }
    }
  }

  @Before fun probe() { glassDropLayerProbe = { p, x, y -> latest = Pose(p, x, y) } }
  @After fun unprobe() { glassDropLayerProbe = null }

  private fun tick() {
    compose.mainClock.advanceTimeByFrame()
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    Snapshot.sendApplyNotifications()
  }

  private fun frame(): Pose {
    tick()
    return checkNotNull(latest) { "la goccia non ha ancora posato il suo layer" }
  }

  /** dp per fotogramma: rallenta, si ferma un fotogramma, riparte all'indietro. */
  private val human = listOf(1f, 2f, 2.5f, 2.5f, 2.5f, 2f, 1.5f, 1f, 0.5f, 0f, -0.5f, -1f, -1.5f, -2f, -2.5f, -2.5f, -2.5f, -2f, -1f, -0.5f)
  private val abrupt = List(8) { 2f } + List(8) { -2f }
  private val paused = List(7) { 2f } + List(50) { 0f } + List(7) { -2f }

  /** Il gesto [steps] (dp per fotogramma, in orizzontale) sul nodo [tag] dal punto [start], poi il rilascio. */
  private fun drag(tag: String, start: (Float, Float) -> Offset, steps: List<Float>): Pair<List<Pose>, List<Pose>> {
    repeat(10) { tick() }
    compose.onNodeWithTag(tag).performTouchInput { down(start(width.toFloat(), centerY)) }
    val held = mutableListOf<Pose>()
    repeat(20) { held += frame() }
    for (dp in steps) {
      if (dp != 0f) compose.onNodeWithTag(tag).performTouchInput { moveBy(Offset(dp.dp.toPx(), 0f)) }
      held += frame()
    }
    compose.onNodeWithTag(tag).performTouchInput { up() }
    val released = List(40) { frame() }
    return held to released
  }

  private fun assertSmooth(label: String, held: List<Pose>, released: List<Pose>, maxAspectStep: Float) {
    val formed = held.indexOfFirst { it.progress >= 0.98f }
    assertTrue("$label: la lente non si è formata: ${held.map { it.progress }}", formed >= 0)
    val lens = held.drop(formed)
    assertTrue(
      "$label: col dito giù la lente è tornata verso il pomello: ${lens.map { it.progress }}",
      lens.all { it.progress >= 0.95f },
    )
    for (i in 1 until lens.size) {
      val before = lens[i - 1].aspect
      val now = lens[i].aspect
      assertTrue(
        "$label: la forma salta di ${now - before} al fotogramma $i: ${lens.map { it.aspect }}",
        abs(now - before) <= maxAspectStep,
      )
      // Lo stiramento non si azzera di colpo: torna a riposo passando per valori piccoli.
      assertTrue(
        "$label: stiramento azzerato di colpo al fotogramma $i ($before → $now)",
        !(abs(now - 1f) < 1e-4f && abs(before - 1f) > 0.01f),
      )
    }
    val stretched = lens.map { it.aspect - 1f }
    assertTrue("$label: la lente non si è stirata in avanti: $stretched", stretched.any { it > 0.005f })
    assertTrue("$label: e non si è stirata all'indietro: $stretched", stretched.any { it < -0.005f })
    val back = listOf(lens.last()) + released
    for (i in 1 until back.size) {
      assertTrue(
        "$label: al rilascio la pressione risale al fotogramma $i: ${back.map { it.progress }}",
        back[i].progress <= back[i - 1].progress + 1e-4f,
      )
    }
    assertEquals("$label: di nuovo pomello", 0f, released.last().progress, 0.01f)
    assertEquals("$label: pomello senza stiramento", 1f, released.last().aspect, 0.01f)
  }

  private fun showSwitch(changes: MutableList<Boolean>) {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        CompositionLocalProvider(LocalFluidHaptics provides haptics) {
          var checked by remember { mutableStateOf(false) }
          Box(Modifier.padding(40.dp).then(watch)) {
            FluidSwitch(checked = checked, onCheckedChange = { checked = it; changes += it }, modifier = Modifier.testTag("switch"))
          }
        }
      }
    }
  }

  private fun showSlider(values: MutableList<Float>) {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        CompositionLocalProvider(LocalFluidHaptics provides haptics) {
          var value by remember { mutableFloatStateOf(0.5f) }
          Box(Modifier.padding(40.dp).width(280.dp)) {
            FluidSlider(value = value, onValueChange = { value = it; values += it }, modifier = Modifier.testTag("slider"))
          }
        }
      }
    }
  }

  private fun switchReversal(label: String, steps: List<Float>) {
    val changes = mutableListOf<Boolean>()
    showSwitch(changes)
    // Il pomello spento sta a sinistra: il suo centro è a 22 dp dall'inizio della pista.
    val (held, released) = drag("switch", { _, y -> Offset(22.dp.value * 2f, y) }, steps)
    assertSmooth("interruttore, $label", held, released, maxAspectStep = 0.25f)
    println("F6-d interruttore, $label: aptica ${haptics.played}, cambi $changes, eventi ${events.joinToString(" ")}")
    assertEquals(
      "interruttore, $label: sotto il dito si accende oltre la metà e si spegne tornando",
      listOf(FluidHapticEvent.ToggleOn, FluidHapticEvent.ToggleOff),
      haptics.played.take(2),
    )
    // Il rilascio: come `FluidTouchTest`, nessun colpo in più e lo stato di prima. Solo sul profilo brusco, l'unico che si
    // allontana dal contatto di almeno 32 px: lo slop del tocco di Robolectric è 16 dp (32 px a xhdpi, il doppio degli 8 dp
    // di un dispositivo), e sotto lo slop l'interruttore legge il gesto come un tocco e al rilascio commuta. Sul tablet
    // passare la metà chiede almeno 11,6 dp, più dello slop vero.
    if (steps === abrupt) {
      assertEquals("interruttore, $label: al rilascio nessun colpo in più", 2, haptics.played.size)
      assertEquals("interruttore, $label: tornato da dove era partito, lo stato non cambia", emptyList<Boolean>(), changes)
    }
  }

  private fun sliderReversal(label: String, steps: List<Float>) {
    val values = mutableListOf<Float>()
    showSlider(values)
    val (held, released) = drag("slider", { w, y -> Offset(w / 2f, y) }, steps)
    assertSmooth("cursore, $label", held, released, maxAspectStep = 0.45f)
    val peak = values.max()
    assertTrue("cursore, $label: il valore doveva salire e tornare: $values", peak > 0.53f && values.last() < peak - 0.03f)
  }

  @Test fun `interruttore, inversione umana nello stesso contatto`() = switchReversal("umana", human)
  @Test fun `interruttore, inversione brusca nello stesso contatto`() = switchReversal("brusca", abrupt)
  @Test fun `interruttore, inversione dopo una pausa a meta' gesto`() = switchReversal("con pausa", paused)
  @Test fun `cursore, inversione umana nello stesso contatto`() = sliderReversal("umana", human)
  @Test fun `cursore, inversione brusca nello stesso contatto`() = sliderReversal("brusca", abrupt)
  @Test fun `cursore, inversione dopo una pausa a meta' gesto`() = sliderReversal("con pausa", paused)
}
