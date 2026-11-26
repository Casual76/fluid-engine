package dev.antigravity.fluidengine.diagnostics

import android.app.Activity
import android.widget.FrameLayout
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.FrameDataApi31
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import androidx.metrics.performance.StateInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** F8.24 · I fotogrammi lenti per pagina: il conto, i blocchi, le pagine segnate, il nome che non porta dati. */
@RunWith(RobolectricTestRunner::class)
class FluidFramesTest {
  private val ms = 1_000_000L

  @Test
  fun framesCountPerPageWithAppleAndAndroidThresholds() {
    val aggregator = FluidFrameAggregator()
    // 120 Hz: un fotogramma vale 8,3 ms.
    repeat(100) { aggregator.onFrame("note", 7 * ms, -1 * ms, isJank = false) }
    aggregator.onFrame("note", 20 * ms, 12 * ms, isJank = true)
    aggregator.onFrame("note", 260 * ms, 252 * ms, isJank = true) // un blocco (250 ms)
    aggregator.onFrame("note", 800 * ms, 792 * ms, isJank = true) // un fotogramma bloccato (700 ms)
    aggregator.onFrame("impostazioni", 8 * ms, 0, isJank = false)
    val pages = aggregator.snapshot("2026-11-26", reset = true).associateBy { it.page }
    val note = pages.getValue("note")
    assertEquals(103L, note.frames)
    assertEquals(3L, note.slow)
    assertEquals(2L, note.hangs)
    assertEquals(1L, note.frozen)
    assertEquals(800L, note.worstMillis)
    assertEquals(12L + 252 + 792, note.hitchMillis)
    assertEquals(103L, note.buckets.sum())
    assertEquals(100L, note.buckets[FluidFrameBuckets.indexOf(7 * ms)])
    assertEquals(1L, pages.getValue("impostazioni").frames)
    assertTrue(aggregator.snapshot("2026-11-26", reset = false).isEmpty())
  }

  @Test
  fun tooManyPagesGoToOthers() {
    val aggregator = FluidFrameAggregator(maxPages = 4)
    for (i in 0 until 10) aggregator.onFrame("pagina-$i", 8 * ms, 0, isJank = false)
    val pages = aggregator.snapshot("2026-11-26", reset = false)
    assertEquals(listOf("pagina-0", "pagina-1", "pagina-2", "altre"), pages.map { it.page })
    assertEquals(7L, pages.last().frames)
  }

  @Test
  fun theListenerReadsThePageAndTheOverrun() {
    val aggregator = FluidFrameAggregator()
    val listener = FrameListener(aggregator, expectedNanos = 8_333_333)
    val states = listOf(StateInfo("altro", "x"), StateInfo(FluidPageStateKey, "galleria"))
    listener.onFrame(FrameDataApi31(0, 9 * ms, 18 * ms, 21 * ms, 10 * ms, true, states))
    listener.onFrame(FrameData(0, 30 * ms, true, emptyList()))
    val pages = aggregator.snapshot("2026-11-26", reset = false).associateBy { it.page }
    assertEquals(10L, pages.getValue("galleria").hitchMillis)
    assertEquals(18L, pages.getValue("galleria").worstMillis)
    // Senza pagina segnata conta per «app», e il ritardo è oltre gli 8,3 ms del fotogramma.
    assertEquals(21L, pages.getValue(FluidUnnamedPage).hitchMillis)
  }

  @Test
  fun aPageNameCannotCarryData() {
    assertEquals("impostazioni", fluidSafePageName("impostazioni"))
    assertEquals("note/dettaglio", fluidSafePageName("note/dettaglio"))
    assertEquals("pagina", fluidSafePageName("Diario di Marta"))
    assertEquals("pagina", fluidSafePageName("perché"))
    assertEquals("pagina", fluidSafePageName("x".repeat(49)))
  }

  @Test
  fun markedPagesStackAndReachJankStats() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    val content = FrameLayout(activity)
    activity.setContentView(content)
    // JankStats crea lo stato della finestra; le pagine segnate prima ci entrano quando arriva.
    val first = FluidPageStates.push(content, "elenco")
    JankStats.createAndTrack(activity.window) { }
    FluidPageStates.apply(content.rootView)
    val holder = PerformanceMetricsState.getHolderForHierarchy(content)
    assertTrue(holder.state != null)
    val second = FluidPageStates.push(content, "dettaglio")
    assertEquals("dettaglio", FluidPageStates.top(content))
    FluidPageStates.pop(content, second)
    assertEquals("elenco", FluidPageStates.top(content))
    FluidPageStates.pop(content, first)
    assertNull(FluidPageStates.top(content))
  }
}
