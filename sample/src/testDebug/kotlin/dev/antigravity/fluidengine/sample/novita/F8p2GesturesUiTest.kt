package dev.antigravity.fluidengine.sample.novita

import android.os.Looper
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidRangeSlider
import dev.antigravity.fluidengine.ui.fluid.FluidRulerDial
import dev.antigravity.fluidengine.ui.fluid.FluidWheelPicker
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEngine
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHapticGrainStyle
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import kotlin.math.asin
import kotlin.math.roundToInt
import kotlin.math.sin
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

/** Cosa ha chiesto al vibratore un gesto. */
private class WheelHaptics : FluidHaptics {
  override val engine: FluidHapticEngine = FluidHapticEngine.Composition
  val played = mutableListOf<FluidHapticEvent>()
  override fun play(event: FluidHapticEvent) {
    played += event
  }

  override fun grain(intensity: Float, style: FluidHapticGrainStyle) = Unit
}

/**
 * F8.2 · Gesti e ruote, col dito vero (eventi del sistema, con l'orologio che avanza: `idleFor` al passo di
 * `advanceTimeBy`, come `MaterialFinger` vuole) e col vetro vero (verdetti degli shader accettati: la fascia della ruota
 * e le maniglie sono vetro dell'engine).
 *
 * In Robolectric la densita' e' 2 (xhdpi): una voce della ruota e' 64 px, una tacca della ghiera 18 px, e lo slop del
 * tocco e' 32 px (16 dp, il doppio di un telefono): la ruota e la ghiera si prendono il dito a meta', 16 px.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F8p2GesturesUiTest {

  @get:Rule
  val compose = createComposeRule()

  private val haptics = WheelHaptics()
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

  /** Un trascinamento a passi di ([stepX], [stepY]) px ogni 16 ms per [steps] passi; [hold] ms fermo prima di alzare. */
  private fun drag(from: Offset, stepX: Float, stepY: Float, steps: Int, hold: Long = 0L) {
    finger.down(from)
    var at = from
    repeat(steps) {
      advance(16)
      at = Offset(at.x + stepX, at.y + stepY)
      finger.move(at)
    }
    // Senza [hold] il dito si alza nell'istante dell'ultimo movimento: un lancio. Con [hold] resta fermo prima.
    if (hold > 0) advance(hold)
    finger.up(at)
    advance(16)
  }

  private fun tap(at: Offset) {
    finger.down(at)
    advance(32)
    finger.up(at)
    advance(16)
  }

  private fun node(description: String): SemanticsNode =
    compose.onNode(hasContentDescription(description), useUnmergedTree = true).fetchSemanticsNode()

  private fun SemanticsNode.custom(label: String) {
    val action = config[SemanticsActions.CustomActions].first { it.label == label }
    compose.runOnUiThread { action.action() }
    Snapshot.sendApplyNotifications()
    advance(32)
  }

  private fun SemanticsNode.setProgress(value: Float) {
    val action = config[SemanticsActions.SetProgress].action
    compose.runOnUiThread { action?.invoke(value) }
    Snapshot.sendApplyNotifications()
    advance(32)
  }

  /** Le scritture di stato in [ms] (per «a riposo niente si muove»). */
  private fun writesDuring(ms: Long): Int {
    var writes = 0
    val observer = Snapshot.registerGlobalWriteObserver { writes++ }
    advance(ms)
    observer.dispose()
    return writes
  }

  // --- La ruota ----------------------------------------------------------------------------------------------------

  private var minute by mutableIntStateOf(10)

  private fun showMinutes(reduced: Boolean = false, cyclic: Boolean = false) = show(reduced) {
    FluidWheelPicker(
      count = 60,
      selected = minute,
      onSelectedChange = { minute = it },
      label = { it.toString().padStart(2, '0') },
      cyclic = cyclic,
      contentDescription = "Minuti",
      modifier = Modifier.testTag("ruota"),
    )
  }

  @Test
  fun `trascinata di tre voci e lasciata ferma, la ruota si posa sulla terza voce con un tic per voce`() {
    showMinutes()
    val b = bounds("ruota")
    // Mezzo slop (16 px) per prendersi il dito, poi tre voci da 64 px: 208 px a passi di 8.
    drag(Offset(b.center.x, b.center.y + 60f), 0f, -8f, steps = 26, hold = 160)
    advance(900)
    assertEquals(13, minute)
    val ticks = haptics.played.count { it == FluidHapticEvent.Tick || it == FluidHapticEvent.DragTick }
    assertEquals("un tic per voce passata: ${haptics.played}", 3, ticks)
  }

  @Test
  fun `lanciata, la ruota continua oltre il dito e si ferma esattamente su una voce`() {
    showMinutes()
    val b = bounds("ruota")
    drag(Offset(b.center.x, b.center.y + 120f), 0f, -40f, steps = 6)
    // Il valore arriva solo quando la ruota si ferma (come `didSelectRow`): a meta' corsa e' ancora quello di prima.
    advance(300)
    assertEquals("durante l'inerzia il valore non cambia", 10, minute)
    advance(6000)
    // Il dito ha portato la ruota a 13,1; l'inerzia la porta oltre di mezzo secondo della velocita' che il tracciatore di
    // Compose stima (in Robolectric meno di quella del dito finto: qui ~4 voci). Si ferma su una voce intera.
    println("F8p2 lancio: ferma su $minute")
    assertTrue("si e' fermata su $minute", minute >= 16)
    // Un tic per voce passata, col dito e durante l'inerzia (la rete dei 55 ms ne puo' saltare qualcuno a corsa veloce).
    assertTrue("tic durante il gesto e l'inerzia: ${haptics.played.size}", haptics.played.size >= 3)
    // E una volta ferma, niente si muove.
    assertEquals("scritture a riposo", 0, writesDuring(1000))
  }

  @Test
  fun `un dito su una ruota che gira la ferma, e la posa sulla voce piu' vicina`() {
    showMinutes()
    val b = bounds("ruota")
    drag(Offset(b.center.x, b.center.y + 120f), 0f, -40f, steps = 6)
    advance(5000)
    val free = minute - 10
    minute = 10
    Snapshot.sendApplyNotifications()
    advance(1500)
    assertEquals(10, minute)
    drag(Offset(b.center.x, b.center.y + 120f), 0f, -40f, steps = 6)
    advance(150)
    tap(Offset(b.center.x, b.center.y))
    advance(1200)
    val stopped = minute - 10
    assertTrue("fermata a +$stopped, libera a +$free", stopped in 3 until free)
  }

  @Test
  fun `un tocco su una voce sotto la fascia la porta al centro`() {
    showMinutes()
    val b = bounds("ruota")
    val radius = b.height * 0.39f
    val dy = radius * sin(2f * 64f / radius)
    tap(Offset(b.center.x, b.center.y + dy))
    advance(800)
    assertEquals(12, minute)
    tap(Offset(b.center.x, b.center.y - radius * sin(64f / radius)))
    advance(800)
    assertEquals(11, minute)
  }

  @Test
  fun `col movimento ridotto niente inerzia lunga, ma lo scatto resta`() {
    showMinutes(reduced = true)
    val b = bounds("ruota")
    drag(Offset(b.center.x, b.center.y + 120f), 0f, -40f, steps = 6)
    advance(400)
    // Il dito ha portato la ruota a ~13,1: al piu' una voce oltre.
    assertTrue("si e' fermata su $minute", minute in 13..14)
    assertEquals("scritture a riposo", 0, writesDuring(600))
  }

  @Test
  fun `TalkBack legge la voce e la regola con aumenta, diminuisci e imposta, e una ruota che gira torna alla prima`() {
    showMinutes(cyclic = true)
    val column = node("Minuti")
    assertEquals("10", column.config[SemanticsProperties.StateDescription])
    val info = column.config[SemanticsProperties.ProgressBarRangeInfo]
    assertEquals(10f, info.current, 0f)
    assertEquals(59f, info.range.endInclusive, 0f)
    column.custom("Aumenta")
    assertEquals(11, minute)
    node("Minuti").custom("Diminuisci")
    assertEquals(10, minute)
    node("Minuti").setProgress(59f)
    assertEquals(59, minute)
    node("Minuti").custom("Aumenta")
    assertEquals("dopo l'ultima la prima", 0, minute)
    advance(800)
    assertEquals("00", node("Minuti").config[SemanticsProperties.StateDescription])
  }

  @Test
  fun `dentro una lista che scorre il gesto verticale e' della ruota, e la lista resta ferma`() {
    val list = LazyListState()
    show {
      LazyColumn(Modifier.fillMaxSize(), state = list) {
        item { Box(Modifier.fillMaxWidth().height(120.dp)) }
        item {
          FluidWheelPicker(
            count = 60,
            selected = minute,
            onSelectedChange = { minute = it },
            label = { it.toString() },
            contentDescription = "Minuti",
            modifier = Modifier.testTag("ruota"),
          )
        }
        items(40) { Text("Riga $it", Modifier.height(48.dp)) }
      }
    }
    val b = bounds("ruota")
    drag(Offset(b.center.x, b.center.y + 60f), 0f, -8f, steps = 26, hold = 160)
    advance(900)
    assertEquals(0, list.firstVisibleItemIndex)
    assertEquals(0, list.firstVisibleItemScrollOffset)
    assertEquals(13, minute)
  }

  // --- La ghiera ---------------------------------------------------------------------------------------------------

  private var degrees by mutableFloatStateOf(0f)
  private var dialFinished = 0

  private fun showDial(reduced: Boolean = false) = show(reduced) {
    FluidRulerDial(
      value = degrees,
      onValueChange = { degrees = it },
      onValueChangeFinished = { dialFinished++ },
      contentDescription = "Raddrizza",
      modifier = Modifier.testTag("ghiera"),
    )
  }

  @Test
  fun `trascinata di dieci tacche la ghiera segue il dito e si ferma sulla principale, con il tic`() {
    showDial()
    val b = bounds("ghiera")
    // Verso sinistra: i valori crescono. Mezzo slop (16 px), poi dieci tacche da 18 px.
    drag(Offset(b.center.x, b.center.y), -4f, 0f, steps = 49, hold = 160)
    advance(800)
    assertEquals(10f, degrees, 1e-4f)
    assertTrue("le tacche suonano: ${haptics.played}", haptics.played.count { it == FluidHapticEvent.Tick || it == FluidHapticEvent.DragTick } >= 3)
    assertTrue("alla fine del gesto", dialFinished >= 1)
  }

  @Test
  fun `lanciata, la ghiera continua con l'inerzia e si ferma su una tacca intera`() {
    showDial()
    val b = bounds("ghiera")
    drag(Offset(b.center.x + 200f, b.center.y), -36f, 0f, steps = 6)
    advance(5000)
    assertTrue("oltre il dito: $degrees", degrees > 12f)
    assertEquals("una tacca intera", degrees.roundToInt().toFloat(), degrees, 1e-4f)
    assertEquals("scritture a riposo", 0, writesDuring(800))
  }

  @Test
  fun `il doppio tocco torna allo zero con un colpo deciso`() {
    degrees = 17f
    showDial()
    val b = bounds("ghiera")
    tap(Offset(b.center.x, b.center.y))
    advance(100)
    tap(Offset(b.center.x, b.center.y))
    advance(900)
    assertEquals(0f, degrees, 1e-4f)
    assertTrue(haptics.played.contains(FluidHapticEvent.Threshold))
  }

  @Test
  fun `TalkBack regola la ghiera di un passo`() {
    showDial()
    node("Raddrizza").custom("Aumenta")
    assertEquals(1f, degrees, 1e-4f)
    node("Raddrizza").custom("Diminuisci")
    node("Raddrizza").custom("Diminuisci")
    assertEquals(-1f, degrees, 1e-4f)
    assertEquals("−1", node("Raddrizza").config.getOrNull(SemanticsProperties.StateDescription))
  }

  // --- Le due maniglie ---------------------------------------------------------------------------------------------

  private var from by mutableFloatStateOf(60f)
  private var to by mutableFloatStateOf(270f)

  private fun showRange() = show {
    FluidRangeSlider(
      value = from..to,
      onValueChange = { from = it.start; to = it.endInclusive },
      valueRange = 0f..600f,
      minDistance = 30f,
      modifier = Modifier.testTag("intervallo"),
    )
  }

  /** Il centro della maniglia a [value], in px nella radice: margine di 20 px e pista di `larghezza − 40`. */
  private fun thumbX(b: Rect, value: Float): Float = b.left + 20f + (b.width - 40f) * value / 600f

  @Test
  fun `la maniglia del minimo non scavalca quella del massimo e si ferma alla distanza minima, con un colpo`() {
    showRange()
    val b = bounds("intervallo")
    drag(Offset(thumbX(b, from), b.center.y), 10f, 0f, steps = 40)
    advance(600)
    assertEquals(240f, from, 0.5f)
    assertEquals(270f, to, 0f)
    assertTrue("il colpo all'arrivo: ${haptics.played}", haptics.played.contains(FluidHapticEvent.Threshold))
  }

  @Test
  fun `due maniglie sovrapposte si separano dal verso del primo movimento`() {
    from = 300f
    to = 300f
    show {
      FluidRangeSlider(
        value = from..to,
        onValueChange = { from = it.start; to = it.endInclusive },
        valueRange = 0f..600f,
        modifier = Modifier.testTag("intervallo"),
      )
    }
    val b = bounds("intervallo")
    drag(Offset(thumbX(b, 300f), b.center.y), -8f, 0f, steps = 12)
    advance(600)
    assertTrue("il minimo e' sceso: $from", from < 300f)
    assertEquals(300f, to, 0f)
    drag(Offset(thumbX(b, 300f), b.center.y), 8f, 0f, steps = 12)
    advance(600)
    assertTrue("il massimo e' salito: $to", to > 300f)
  }

  @Test
  fun `TalkBack ha due nodi, ognuno regolabile da solo nello spazio che l'altro gli lascia`() {
    showRange()
    val min = node("Minimo")
    val max = node("Massimo")
    assertEquals(60f, min.config[SemanticsProperties.ProgressBarRangeInfo].current, 0f)
    assertEquals(240f, min.config[SemanticsProperties.ProgressBarRangeInfo].range.endInclusive, 0f)
    assertEquals(90f, max.config[SemanticsProperties.ProgressBarRangeInfo].range.start, 0f)
    max.setProgress(500f)
    assertEquals(500f, to, 0f)
    assertEquals(60f, from, 0f)
    node("Minimo").custom("Aumenta")
    assertEquals(90f, from, 0f)
    node("Minimo").setProgress(590f)
    assertEquals("non oltre il massimo meno la distanza", 470f, from, 0f)
  }

  // --- Le scene ----------------------------------------------------------------------------------------------------

  @Test
  fun `ogni scena di F8_2 si apre e a riposo non scrive niente`() {
    var scene by mutableStateOf<String?>(null)
    show {
      androidx.compose.runtime.key(scene) { NewElementsPageAt(onBack = {}, initial = scene) }
    }
    val quiet = ArrayList<String>()
    for (s in F8p2Section.scenes) {
      scene = s.id
      Snapshot.sendApplyNotifications()
      advance(2000)
      compose.onNodeWithTag(F8p2Tags.Background, useUnmergedTree = true).assertExists()
      val writes = writesDuring(1000)
      println("F8p2 scena ${s.id}: $writes scritture a riposo")
      if (writes > 0) quiet += "${s.id} ($writes)"
    }
    assertTrue("scene che non stanno ferme: $quiet", quiet.isEmpty())
  }
}
