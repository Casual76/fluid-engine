@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.glass.field.trace.TodayLiquidDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fotogrammi a 60 Hz di tempo virtuale (0, 17, 33, 50, ... ms). */
private class SixtyHertz(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
  override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
    val now = scheduler.currentTime
    var k = now * 3L / 50L
    while ((k * 50L + 1L) / 3L <= now) k++
    val next = (k * 50L + 1L) / 3L
    delay(next - now)
    return onFrame(next * 1_000_000L)
  }
}

/**
 * S4g-bis sul Tab S9 (b): nella nascita di «Altro» le icone di condivisione e della stella entrano parziali dal bordo destro
 * (chiaro f0010–f0012, scuro f0085–f0086), mentre il testo ai bordi e' continuo. Al PC era verde.
 *
 * La lente ingrandisce le voci fino a 1,42× e le porta verso il tasto, che sta vicino al bordo destro dello schermo: lo strato
 * delle voci esce dalla finestra. Sotto un `RenderEffect` Skia rende nello strato del filtro solo la parte dentro la finestra
 * (lo shader della lente non dichiara quanto lontano legge), e la lente, che porta le voci di fuori nel bordo del vetro, trova
 * le icone tagliate al bordo dello schermo. Al banco lo strato e' sempre intero. Il banco ne riproduce l'effetto con lo strato
 * limitato alla finestra (`tools/glassbench/test_menu_lens_window_input.py`); qui la geometria vera, la nascita di «Altro» nel
 * posto del Tab S9 (S4g, prova s4h), e lo spostamento che porta lo strato nella finestra ([fluidMenuInkOnScreen]).
 */
class VociDentroLaFinestraTest {
  private val d = 2.125f
  private val window = Rect(0f, 0f, 1600f, 2560f)
  // Il tasto e il pannello di «Altro» sul Tab S9 (S4g, prova s4h): a 60 px dal bordo destro.
  private val rest = Rect(1446f, 1846f, 1540f, 1940f)
  private val panel = Rect(1009f, 1957f, 1540f, 2373f)

  private class Frame(val t: Long, val ink: Rect, val glass: Rect, val scale: Float)

  /** La nascita vera, con la goccia del banco: a ogni fotogramma dove la lente disegna le voci e il vetro. */
  private fun TestScope.birth(rest: Rect, panel: Rect, group: Rect? = null): List<Frame> {
    val job = SupervisorJob()
    val base = testScheduler.currentTime
    val scope = CoroutineScope(coroutineContext.minusKey(Job) + job + SixtyHertz(testScheduler))
    val anchor = FluidMenuAnchor().apply {
      restBounds = rest
      pressedBounds = rest
      groupBounds = group
    }
    val journey = FluidMenuJourney().apply {
      prepare(anchor, Offset.Zero, panel, 22f * d, FluidMenuInflation.None, liquidMenu = true, appleBirth = true)
      retarget(1f)
    }
    val physics = FluidPhysicsState(FluidForm.circle(Offset(1f, 1f), 1f)).apply {
      density = d
      liquidAllowed = true
      maskInShader = true
      liquidBody = true
    }
    journey.install(physics)
    val lens = FluidMenuLens(journey, physics, panel)
    val driver = TodayLiquidDriver(physics, d)
    scope.launch { journey.run(reducedMotion = false) }
    runCurrent()
    val out = ArrayList<Frame>()
    var at = 0L
    for (k in 0..30) {
      val t = (k * 50L + 1L) / 3L
      advanceTimeBy(t - at)
      runCurrent()
      at = t
      driver.frame((base + t) * 1_000_000L)
      val map = lens.contentMap()
      // Il nodo delle voci e' grande quanto il pannello, scalato attorno al suo centro e traslato (`FluidGlassMenuPanel`).
      val half = Offset(panel.width / 2f, panel.height / 2f)
      val center = panel.center + map.translation
      out += Frame(t, Rect(center - half * map.scale, center + half * map.scale), physics.ensurePlan().bounds, map.scale)
    }
    job.cancel()
    return out
  }

  @Test fun theItemsLayerLeavesTheWindowNearTheRightEdgeAndTheShiftBringsItBack() = runTest {
    val frames = birth(rest, panel)
    var outside = 0
    var most = 0f
    for (f in frames) {
      val over = maxOf(f.ink.right - window.right, window.left - f.ink.left, 0f)
      if (over > 0.5f) outside++
      most = maxOf(most, over)
      val shift = fluidMenuInkOnScreen(f.ink, window)
      val moved = f.ink.translate(shift)
      println(
        "TAVOLA ltr t=${f.t} voci=${"%.0f..%.0f".format(f.ink.left, f.ink.right)} fuori=${"%.0f".format(over)} " +
          "vetro=${"%.0f..%.0f".format(f.glass.left, f.glass.right)} spostate=${"%.0f..%.0f".format(moved.left, moved.right)}",
      )
      // Spostato, lo strato sta tutto nella finestra: Skia lo rende intero.
      assertTrue("${f.t} ms: dentro la finestra", moved.left >= window.left - 0.5f && moved.right <= window.right + 0.5f)
      assertTrue("${f.t} ms: dentro la finestra", moved.top >= window.top - 0.5f && moved.bottom <= window.bottom + 0.5f)
      // E copre ancora il vetro che si vede: la lente esce sullo strato spostato, e lo shader lo riporta al suo posto.
      val seen = f.glass.intersect(f.ink).intersect(window)
      if (seen.width > 0f && seen.height > 0f) {
        assertTrue(
          "${f.t} ms: il vetro ${"%.0f..%.0f".format(seen.left, seen.right)} fuori dallo strato spostato",
          seen.left >= moved.left - 0.5f && seen.right <= moved.right + 0.5f && seen.top >= moved.top - 0.5f &&
            seen.bottom <= moved.bottom + 0.5f,
        )
      }
    }
    println("TAVOLA ltr fotogrammi con le voci fuori dalla finestra: $outside su ${frames.size}, al piu' ${"%.0f".format(most)} px")
    // Come sul Tab S9: nei primi fotogrammi le voci escono dal bordo destro.
    assertTrue("le voci escono dalla finestra: $outside fotogrammi", outside > 0)
  }

  @Test fun inRtlThePushTowardsTheInkKeepsTheLayerInsideTheLeftEdge() = runTest {
    // Lo stesso menu specchiato: a 60 px dal bordo sinistro. Lo spostamento dell'inchiostro va sempre verso destra
    // (`fluidMenuLensMap`), e in S4g-bis in RTL le icone entravano intere.
    val mirror = { r: Rect -> Rect(window.right - r.right, r.top, window.right - r.left, r.bottom) }
    val frames = birth(mirror(rest), mirror(panel))
    val ltr = birth(rest, panel)
    val leftOut = frames.maxOf { maxOf(window.left - it.ink.left, 0f) }
    val rightOut = ltr.maxOf { maxOf(it.ink.right - window.right, 0f) }
    println("TAVOLA rtl al piu' ${"%.0f".format(leftOut)} px fuori a sinistra, contro ${"%.0f".format(rightOut)} a destra in LTR")
    assertTrue("in RTL meno fuori che in LTR: $leftOut contro $rightOut", leftOut < rightOut)
  }

  /**
   * S4g-ter: il limite dello strato della lente ([fluidMenuInkLimit]) tiene tutte le voci, spostate nella finestra, e resta
   * della misura del pannello nello spazio dello strato, dove Skia alloca l'intermedio del filtro: senza limite era la finestra
   * divisa per la scala (sul Tab S9 la prima apertura del gruppo a ~1 s). «Altro» vicino al bordo, e il gruppo, che nasce dalla
   * capsula con le voci rimpicciolite.
   */
  @Test fun theLensLimitHoldsEveryItemAndStaysPanelSized() = runTest {
    // Il gruppo della foto del Tab S9 (`GruppoRitornoIconeTest`): tre tasti da 44 dp, il menu sul terzo, il pannello sotto.
    val capsule = Rect(64f, 600f, 64f + 132f * d, 600f + 44f * d)
    val trigger = Rect(capsule.right - 44f * d, capsule.top, capsule.right, capsule.bottom)
    val groupPanel = Rect(64f, capsule.bottom + 8f * d, 64f + 250f * d, capsule.bottom + 8f * d + 196f * d)
    for ((name, frames, node) in listOf(
      Triple("altro", birth(rest, panel), panel),
      Triple("gruppo", birth(trigger, groupPanel, capsule), groupPanel),
    )) {
      var widest = 0f
      for (f in frames) {
        val halo = fluidMenuInkHaloPx(FluidMotion.MenuContentMaxBlurDp * d, f.scale)
        val shift = fluidMenuInkOnScreen(f.ink, window)
        val limit = fluidMenuInkLimit(f.ink, window, shift, halo)
        val moved = f.ink.translate(shift).intersect(window)
        // Tutte le voci, spostate, stanno nel limite: la lente le riceve intere.
        assertTrue(
          "$name ${f.t} ms: le voci $moved fuori dal limite $limit",
          moved.left >= limit.left && moved.top >= limit.top && moved.right <= limit.right && moved.bottom <= limit.bottom,
        )
        assertTrue("$name ${f.t} ms: il limite fuori dalla finestra", limit.left >= 0f && limit.right <= window.right + 0.01f)
        // Nello spazio dello strato: il pannello piu' il margine, mai la finestra divisa per la scala.
        val inLayer = Size(limit.width / f.scale, limit.height / f.scale)
        val margin = 2f * halo / f.scale + 1f
        widest = maxOf(widest, inLayer.width / node.width)
        assertTrue("$name ${f.t} ms, scala ${f.scale}: $inLayer nello strato", inLayer.width <= node.width + margin)
        assertTrue("$name ${f.t} ms, scala ${f.scale}: $inLayer nello strato", inLayer.height <= node.height + margin)
      }
      println(
        "TAVOLA limite $name scala minima ${"%.3f".format(frames.minOf { it.scale })}, " +
          "limite nello strato al piu' ${"%.2f".format(widest)} del pannello",
      )
    }
  }

  @Test fun theShiftIsTheLeastThatFits() {
    val w = Rect(0f, 0f, 100f, 200f)
    assertEquals(Offset.Zero, fluidMenuInkOnScreen(Rect(10f, 10f, 90f, 190f), w))
    assertEquals(Offset(-15f, 0f), fluidMenuInkOnScreen(Rect(40f, 10f, 115f, 190f), w))
    assertEquals(Offset(5f, 12f), fluidMenuInkOnScreen(Rect(-5f, -12f, 60f, 100f), w))
    // Piu' grande della finestra: al centro.
    assertEquals(Offset(-10f, 0f), fluidMenuInkOnScreen(Rect(0f, 10f, 120f, 190f), w))
  }
}
