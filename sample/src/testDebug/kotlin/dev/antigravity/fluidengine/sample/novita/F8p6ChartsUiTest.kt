package dev.antigravity.fluidengine.sample.novita

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.ui.fluid.FluidBarChart
import dev.antigravity.fluidengine.ui.fluid.FluidBarGroup
import dev.antigravity.fluidengine.ui.fluid.FluidChartDiagnostics
import dev.antigravity.fluidengine.ui.fluid.FluidChartSeries
import dev.antigravity.fluidengine.ui.fluid.FluidGauge
import dev.antigravity.fluidengine.ui.fluid.FluidGaugeStyle
import dev.antigravity.fluidengine.ui.fluid.FluidGaugeThreshold
import dev.antigravity.fluidengine.ui.fluid.FluidLineChart
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEngine
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHapticGrainStyle
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import java.util.Locale
import kotlin.math.abs
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

/** Cosa ha chiesto al vibratore un gesto. */
private class ChartHaptics : FluidHaptics {
  override val engine: FluidHapticEngine = FluidHapticEngine.Composition
  val played = mutableListOf<FluidHapticEvent>()
  override fun play(event: FluidHapticEvent) {
    played += event
  }

  override fun grain(intensity: Float, style: FluidHapticGrainStyle) = Unit
}

/**
 * F8.6 · I grafici da toccare e il quadrante, col dito vero (eventi del sistema, con l'orologio che avanza come vuole
 * `MaterialFinger`) e col vetro vero (verdetti degli shader accettati: la capsula e' vetro dell'engine).
 *
 * Il punto sotto il dito si trova dai nodi di TalkBack: ogni punto ha un nodo grande quanto la sua colonna, lo stesso
 * rettangolo del tocco. In Robolectric la densita' e' 2 e lo slop e' 32 px.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F8p6ChartsUiTest {

  @get:Rule
  val compose = createComposeRule()

  private val haptics = ChartHaptics()
  private lateinit var view: View
  private lateinit var finger: MaterialFinger
  private val scrubs = mutableListOf<IntRange?>()
  private val taps = mutableListOf<Int?>()

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    FluidChartDiagnostics.resetForTesting()
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
      // Le scritture fatte dal test (fuori da un fotogramma) arrivano alla composizione: come F6b3GlyphFramesTest.
      Snapshot.sendApplyNotifications()
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
    // L'ingresso dei grafici finisce.
    advance(900)
    finger = MaterialFinger(view)
  }

  private val grades = listOf(5.5f, 6f, 6.5f, 5f, 6f, 7f, 7.5f, 6.5f)
  private val dates = listOf("12 set", "26 set", "10 ott", "24 ott", "7 nov", "21 nov", "5 dic", "19 dic")

  @Composable
  private fun Grades(modifier: Modifier = Modifier) {
    val series = remember { listOf(FluidChartSeries("Matematica")) }
    val points = remember { listOf<List<Float?>>(grades) }
    FluidLineChart(
      series = series,
      points = points,
      xLabels = dates,
      startAtZero = false,
      valueText = ::fmt,
      onScrub = { scrubs += it },
      onSelectedChange = { taps += it },
      modifier = modifier.padding(16.dp).testTag("grafico"),
    )
  }

  private fun showGrades(reduced: Boolean = false) = show(reduced) { Column(Modifier.padding(top = 80.dp)) { Grades() } }

  private fun fmt(v: Float): String = String.format(Locale.ITALY, if (v % 1f == 0f) "%.0f" else "%.1f", v)

  /** Il nodo di TalkBack del punto [index]: il suo rettangolo e' la colonna del tocco. */
  private fun point(index: Int): SemanticsNode =
    compose.onNode(hasContentDescription("${dates[index]}: ${fmt(grades[index])}"), useUnmergedTree = true).fetchSemanticsNode()

  private fun centre(index: Int): Offset = point(index).boundsInRoot.center

  /** Il testo [text] dentro la capsula (i suoi bordi), o null se la capsula non lo scrive. */
  private fun capsule(text: String): Rect? = compose
    .onAllNodes(hasText(text) and hasAnyAncestor(hasTestTag(FluidChartDiagnostics.CapsuleTag)), useUnmergedTree = true)
    .fetchSemanticsNodes().firstOrNull()?.boundsInRoot

  /** La capsula di vetro, o null. */
  private fun capsuleBounds(): Rect? = compose
    .onAllNodes(hasTestTag(FluidChartDiagnostics.CapsuleTag), useUnmergedTree = true)
    .fetchSemanticsNodes().firstOrNull()?.boundsInRoot

  private fun chart(): Rect = compose.onNodeWithTag("grafico", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

  /** Le scritture di stato in [ms] (per «a riposo niente si muove»). */
  private fun writesDuring(ms: Long): Int {
    var writes = 0
    val observer = Snapshot.registerGlobalWriteObserver { writes++ }
    advance(ms)
    observer.dispose()
    return writes
  }

  /** I pixel della finestra, col disegno vero (PixelCopy: il disegno software non sa i RuntimeShader del vetro). */
  private fun snapshot(): Bitmap {
    val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().first()
    val window = activity.window
    val image = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
    var result = -1
    compose.runOnUiThread {
      PixelCopy.request(window, image, { result = it }, Handler(Looper.getMainLooper()))
    }
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals("PixelCopy", PixelCopy.SUCCESS, result)
    return image
  }

  // --- Lo scrubber -------------------------------------------------------------------------------------------------

  @Test
  fun `il dito di lato prende lo scrubber sul punto giusto, una tacca per punto, e il rilascio lo toglie`() {
    showGrades()
    val y = centre(0).y
    finger.down(Offset(centre(1).x, y))
    advance(16)
    // Oltre lo slop di lato: lo scrubber e' preso, sul punto sotto il dito.
    var x = centre(1).x
    repeat(3) {
      x += 14f
      finger.move(Offset(x, y))
      advance(16)
    }
    assertEquals(1..1, scrubs.last())
    advance(80)
    // Un punto ogni 80 ms (oltre la rete dei 55 ms): una tacca ciascuno.
    for (i in 2..5) {
      finger.move(Offset(centre(i).x, y))
      advance(80)
      assertEquals(i..i, scrubs.last())
    }
    assertEquals(listOf(1..1, 2..2, 3..3, 4..4, 5..5), scrubs.filterNotNull())
    assertEquals(5, haptics.played.count { it == FluidHapticEvent.Tick })
    // La capsula: il valore e la data del punto, dentro il grafico.
    assertTrue("la capsula scrive il valore", capsule("7") != null)
    assertTrue("e la data", capsule("21 nov") != null)
    val shown = capsuleBounds()
    val c = chart()
    assertTrue("dentro il grafico: $shown in $c", shown!!.left >= c.left - 1f && shown.right <= c.right + 1f)
    // Il tocco non e' partito: lo scrubber non sceglie la colonna.
    assertTrue(taps.isEmpty())
    finger.up(Offset(centre(5).x, y))
    advance(16)
    assertNull("al rilascio l'app sa che e' finito", scrubs.last())
    advance(500)
    assertNull("e la capsula e' tornata nel punto", capsule("21 nov"))
  }

  @Test
  fun `la capsula resta dentro il grafico anche sul primo e sull'ultimo punto, e disegna la linea`() {
    showGrades()
    val y = centre(0).y
    val before = snapshot()
    finger.down(centre(0))
    advance(400) // «tieni premuto»
    assertEquals(0..0, scrubs.last())
    advance(200)
    val c = chart()
    assertTrue(capsule("12 set") != null)
    val first = capsuleBounds()!!
    assertTrue("a sinistra non esce: $first in $c", first.left >= c.left - 1f)
    val during = snapshot()
    // La linea verticale: la colonna di pixel del punto cambia per buona parte dell'altezza dell'area.
    val column = centre(0).x.toInt()
    val plot = point(0).boundsInRoot
    var changed = 0
    for (py in plot.top.toInt() until plot.bottom.toInt()) {
      if (before.getPixel(column, py) != during.getPixel(column, py)) changed++
    }
    assertTrue("la linea c'e': $changed pixel su ${plot.height.toInt()}", changed > plot.height * 0.5f)
    finger.move(Offset(centre(7).x, y))
    advance(300)
    assertTrue(capsule("19 dic") != null)
    val last = capsuleBounds()!!
    assertTrue("a destra non esce: $last in $c", last.right <= c.right + 1f)
    finger.up(Offset(centre(7).x, y))
    advance(400)
  }

  @Test
  fun `con due dita la differenza e l'intervallo, e le dita che si muovono insieme`() {
    showGrades()
    val a = centre(1)
    val b = centre(6)
    finger.downAll(listOf(a, b))
    advance(32)
    assertEquals(1..6, scrubs.last())
    advance(250)
    // Da 6 a 7,5: +1,5, cioe' +25%.
    assertTrue("la differenza", capsule("+1,5 (+25%)") != null)
    assertTrue("l'intervallo", capsule("26 set – 5 dic") != null)
    // Le dita si muovono insieme: l'intervallo segue.
    finger.moveAll(listOf(centre(2), centre(6)))
    advance(80)
    assertEquals(2..6, scrubs.last())
    finger.upAll(listOf(centre(2), centre(6)))
    advance(16)
    assertNull(scrubs.last())
  }

  @Test
  fun `il dito in verticale fa scorrere la pagina e lo scrubber non parte, il tocco sceglie ancora la colonna`() {
    val list = LazyListState()
    show {
      LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
        item { Box(Modifier.fillMaxWidth().height(120.dp)) }
        item { Grades() }
        items(8) { Box(Modifier.fillMaxWidth().height(200.dp)) }
      }
    }
    val from = centre(3)
    finger.down(from)
    var at = from
    repeat(10) {
      advance(16)
      at = Offset(at.x, at.y - 30f)
      finger.move(at)
    }
    // Fermo prima di alzare: niente lancio, la lista resta dove il dito l'ha portata (il grafico ancora a vista).
    advance(300)
    finger.up(at)
    advance(500)
    assertTrue("la pagina e' scorsa", list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 100)
    assertTrue("lo scrubber no", scrubs.isEmpty())
    // Un tocco breve sceglie la colonna, come prima.
    val tap = centre(4)
    finger.down(tap)
    advance(48)
    finger.up(tap)
    advance(100)
    assertEquals(listOf<Int?>(4), taps)
    assertTrue(scrubs.isEmpty())
  }

  @Test
  fun `lo scrubber non ricompone il grafico, la capsula una volta per punto, e a riposo nessuno scrive`() {
    showGrades()
    val y = centre(0).y
    FluidChartDiagnostics.resetForTesting()
    finger.down(Offset(centre(1).x, y))
    advance(16)
    var x = centre(1).x
    repeat(3) {
      x += 14f
      finger.move(Offset(x, y))
      advance(16)
    }
    // Venti movimenti, sei punti: il piano non si ricompone mai.
    for (i in 1..20) {
      finger.move(Offset(centre(1).x + (centre(6).x - centre(1).x) * i / 20f, y))
      advance(16)
    }
    assertEquals("il piano non si ricompone", 0, FluidChartDiagnostics.plotCompositions)
    assertTrue(
      "la capsula una volta per punto, non per movimento: ${FluidChartDiagnostics.capsuleCompositions}",
      FluidChartDiagnostics.capsuleCompositions <= 8,
    )
    finger.up(Offset(centre(6).x, y))
    advance(600)
    assertEquals("il piano non si ricompone nemmeno al rilascio", 0, FluidChartDiagnostics.plotCompositions)
    assertEquals("a riposo nessuno scrive", 0, writesDuring(2_000))
  }

  @Test
  fun `con Riduci movimento la capsula compare e va via senza viaggio`() {
    showGrades(reduced = true)
    finger.down(centre(2))
    advance(400)
    assertEquals(2..2, scrubs.last())
    advance(32)
    assertTrue(capsule("10 ott") != null)
    finger.up(centre(2))
    advance(48)
    assertNull("sparita subito", capsule("10 ott"))
    assertEquals(0, writesDuring(1_000))
  }

  @Test
  fun `le barre hanno lo stesso scrubber, coi GB e la differenza fra due mesi`() {
    show {
      Column(Modifier.padding(top = 80.dp)) {
        FluidBarChart(
          groups = remember { listOf(FluidBarGroup("Gen", listOf(18f)), FluidBarGroup("Feb", listOf(20f)), FluidBarGroup("Mar", listOf(27f))) },
          series = remember { listOf(FluidChartSeries("Spazio")) },
          valueText = { "${it.toInt()} GB" },
          onScrub = { scrubs += it },
          modifier = Modifier.padding(16.dp).testTag("grafico"),
        )
      }
    }
    val jan = compose.onNode(hasContentDescription("Gen: 18 GB"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
    val mar = compose.onNode(hasContentDescription("Mar: 27 GB"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
    finger.downAll(listOf(jan, mar))
    advance(300)
    assertEquals(0..2, scrubs.last())
    assertTrue(capsule("+9 GB (+50%)") != null)
    finger.upAll(listOf(jan, mar))
    advance(400)
  }

  // --- Accessibilita' ----------------------------------------------------------------------------------------------

  @Test
  fun `TalkBack legge i valori punto per punto`() {
    showGrades()
    for (i in grades.indices) {
      val node = point(i)
      assertTrue("il punto $i ha la sua colonna", node.boundsInRoot.width > 0f)
    }
    val rects = grades.indices.map { point(it).boundsInRoot }
    for (i in 1 until rects.size) assertTrue("da sinistra a destra", rects[i].left >= rects[i - 1].right - 1f)
  }

  // --- Il quadrante ------------------------------------------------------------------------------------------------

  @Test
  fun `il quadrante dice il valore, l'intervallo e la soglia, e la soglia superata`() {
    val value = mutableFloatStateOf(18f)
    show {
      FluidGauge(
        value = value.floatValue,
        range = 0f..100f,
        label = "Assenze",
        valueText = { "${it.toInt()} %" },
        thresholds = listOf(FluidGaugeThreshold(25f, "Limite")),
        modifier = Modifier.testTag("quadrante"),
      )
    }
    val node = compose.onNodeWithTag("quadrante", useUnmergedTree = true).fetchSemanticsNode()
    assertEquals("Assenze", node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull())
    assertEquals("18 %, da 0 % a 100 %. Limite 25 %.", node.config.getOrNull(SemanticsProperties.StateDescription))
    assertEquals(18f, node.config[SemanticsProperties.ProgressBarRangeInfo].current, 0.01f)
    value.floatValue = 27f
    advance(700)
    val after = compose.onNodeWithTag("quadrante", useUnmergedTree = true).fetchSemanticsNode()
    assertEquals("27 %, da 0 % a 100 %. oltre: Limite 25 %.", after.config.getOrNull(SemanticsProperties.StateDescription))
    assertEquals("fermo, nessuno scrive", 0, writesDuring(1_500))
  }

  @Test
  fun `col valore in una lambda il quadrante non ricompone chi lo chiama`() {
    val db = mutableFloatStateOf(60f)
    var callerCompositions = 0
    show {
      SideEffect { callerCompositions++ }
      Box(Modifier.width(220.dp)) {
        FluidGauge(value = { db.floatValue }, range = 30f..110f, style = FluidGaugeStyle.Arc, label = "Livello", valueText = { "${it.toInt()} dB" })
      }
    }
    val before = callerCompositions
    repeat(8) { i ->
      db.floatValue = 60f + i * 3f
      advance(125)
    }
    assertEquals("otto valori al secondo: chi chiama non si ricompone", before, callerCompositions)
    // Il numero e la frase seguono il valore (il nodo unico del quadrante li ha tutti e due).
    val node = compose.onAllNodes(hasContentDescription("Livello"), useUnmergedTree = true).fetchSemanticsNodes().first()
    assertTrue(node.config.getOrNull(SemanticsProperties.StateDescription).orEmpty().startsWith("81 dB"))
    assertTrue(abs(db.floatValue - 81f) < 0.01f)
  }

  @Test
  fun `l'anello e la barra hanno il loro nodo col valore`() {
    show {
      Column {
        FluidGauge(value = 6.9f, range = 0f..10f, style = FluidGaugeStyle.Ring, label = "Media", valueText = { String.format(Locale.ITALY, "%.1f", it) }, modifier = Modifier.testTag("anello"))
        FluidGauge(value = 41.5f, range = 0f..64f, style = FluidGaugeStyle.Capacity, label = "Spazio", valueText = { "${it.toInt()} GB" }, maxLabel = "64 GB", modifier = Modifier.testTag("barra"))
      }
    }
    val ring = compose.onNodeWithTag("anello", useUnmergedTree = true).fetchSemanticsNode()
    assertEquals("6,9, da 0,0 a 10,0.", ring.config.getOrNull(SemanticsProperties.StateDescription))
    val bar = compose.onNodeWithTag("barra", useUnmergedTree = true).fetchSemanticsNode()
    assertEquals("41 GB, da 0 GB a 64 GB.", bar.config.getOrNull(SemanticsProperties.StateDescription))
    assertTrue("l'anello sta in una riga", ring.boundsInRoot.height <= 44f * 2f)
  }
}
