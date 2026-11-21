@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
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

/** Fotogrammi a 60 Hz di tempo virtuale (0, 17, 33, 50, ... ms), come in [VociDentroLaFinestraTest]. */
private class RiaperturaSixtyHertz(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
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
 * S4k, voto S4j (b): quando «Altro» si riapre durante la chiusura, «Condividi» e alcuni glifi escono dal contorno del vetro per
 * ~100 ms (chiaro f0505–f0512, scuro f0561–f0571), mentre nelle aperture normali entrano schiacciate dal bordo.
 *
 * Sul Tab S9 (marcatori di s4i2, la riapertura a ~130 ms) la nascita normale ha la goccia da ~54 ms a ~550 ms, e il suo ritaglio;
 * la riapertura dopo un congedo da fermo no: il ritorno sul contorno spegne la goccia (`FluidMenuReturnSurface`), e la riapertura
 * lo lasciava acceso. Senza goccia `travelClipIn` toglie il ritaglio dal progresso 1, che la nascita raggiunge a ~108 ms quando il
 * bordo vicino e' ancora 24 px dentro il pannello: le voci si disegnavano a riposo, e ingrandite dalla lente, fuori dal vetro.
 *
 * Qui la geometria di quella ripresa, il viaggio vero con la goccia del banco, e la superficie che accende la goccia come
 * `FluidMenuReturnSurface`. A ogni fotogramma: se le voci non sono ritagliate sul vetro (ne' la goccia ne' il ritaglio del
 * viaggio), la loro sagoma (il pannello arrotondato, mappato dalla lente) deve stare tutta nel vetro.
 */
class RiaperturaVociNelVetroTest {
  private val d = 2.125f
  // «Altro» nella ripresa s4i2 del Tab S9: il tasto e il pannello che gli si posa sopra (`OverAnchor`), dai marcatori.
  private val button = Rect(1431f, 1116f, 1529f, 1214f)
  private val panel = Rect(1030f, 1116f, 1566f, 1566f)
  private val radius = 22f * d

  private class Frame(
    val t: Long,
    val sinceTap: Long?,
    val closing: Boolean,
    val progress: Float,
    val clock: Float,
    val focus: Float,
    val drop: Boolean,
    val clipped: Boolean,
    val glass: Rect,
    val items: Rect,
    val scale: Float,
    /** Pixel del contorno delle voci fuori dal vetro: zero se le voci sono ritagliate. */
    val out: Int,
  )

  /**
   * «Altro» che nasce, si posa, si congeda da fermo e, [tapMs] dopo il congedo, si riapre (null: solo la nascita). Con
   * [before] la regola di S4i: la riapertura lascia la goccia spenta, e il ritaglio finisce al progresso 1.
   */
  private fun TestScope.take(tapMs: Long?, before: Boolean): List<Frame> {
    val job = SupervisorJob()
    val base = testScheduler.currentTime
    val scope = CoroutineScope(coroutineContext.minusKey(Job) + job + RiaperturaSixtyHertz(testScheduler))
    val anchor = FluidMenuAnchor().apply {
      restBounds = button
      pressedBounds = button
    }
    val journey = FluidMenuJourney()
    fun prepare(fresh: Boolean) = journey.prepare(
      anchor, Offset.Zero, panel, radius, FluidMenuInflation.None, freshStart = fresh, liquidMenu = true, appleBirth = true,
    )
    prepare(fresh = true)
    val physics = FluidPhysicsState(FluidForm.circle(Offset(1f, 1f), 1f)).apply {
      density = d
      liquidAllowed = true
      maskInShader = true
      liquidBody = true
    }
    journey.install(physics)
    val lens = FluidMenuLens(journey, physics, panel)
    val driver = TodayLiquidDriver(physics, d)
    var run: Job? = null
    // Come l'host: il verso si decide prima, e l'effetto vecchio si cancella dopo (la velocita' si legge finche' e' viva).
    fun go(target: Float) {
      journey.retarget(target)
      run?.cancel()
      run = scope.launch { journey.run(reducedMotion = false) }
    }
    go(1f)
    runCurrent()
    val closeAt = 1_500L
    val end = if (tapMs == null) closeAt else closeAt + tapMs + 900L
    var closed = false
    var tapped = false
    val out = ArrayList<Frame>()
    var at = 0L
    var k = 0
    while (true) {
      k++
      val t = (k * 50L + 1L) / 3L
      if (t > end) break
      advanceTimeBy(t - at)
      runCurrent()
      at = t
      if (tapMs != null && !closed && t >= closeAt) {
        closed = true
        go(0f)
        runCurrent()
      }
      if (tapMs != null && closed && !tapped && t >= closeAt + tapMs) {
        tapped = true
        prepare(fresh = false)
        go(1f)
        runCurrent()
      }
      // La superficie: col ritorno sul contorno la goccia e' spenta (`FluidMenuReturnSurface`). Con la regola di prima restava
      // spenta anche nella riapertura.
      physics.liquidAllowed = if (before && closed) false else !journey.controlledReturn
      driver.frame((base + t) * 1_000_000L)
      val plan = physics.ensurePlan()
      // Il ritaglio del viaggio e la mappa della lente, come il pannello (`FluidMenuLayer`, `FluidMenuLens.contentMap`).
      val clipProgress = if (before) journey.progress.value else journey.clipProgress(physics, panel)
      val clip = physics.travelClipIn(panel, clipProgress)
      val visible = clip?.let { Rect(it.left, it.top, it.right, it.bottom).translate(panel.topLeft) } ?: panel
      val map = fluidMenuLensMap(
        panel, visible, journey.magnifiesInk, journey.progress.value, journey.focus.value, returning = journey.bornReturn,
      )
      if (!before) assertEquals("$t ms: la mappa della lente", lens.contentMap(), map)
      val half = Offset(panel.width / 2f, panel.height / 2f)
      val center = panel.center + map.translation
      val items = Rect(center - half * map.scale, center + half * map.scale)
      // Ritagliate: sulla sagoma del vetro (`drawInMenuSilhouette`), o dallo shader della lente (`clipInk`).
      val clipped = plan.dropRest != null || clip != null
      val outside = if (clipped) 0 else outsidePoints(items, radius * map.scale, plan.silhouetteRoundRect, plan.bounds)
      out += Frame(
        t = t,
        sinceTap = if (tapped) t - closeAt - tapMs!! else null,
        closing = journey.closing,
        progress = journey.progress.value,
        clock = journey.birthClock.value,
        focus = journey.focus.value,
        drop = plan.dropRest != null,
        clipped = clipped,
        glass = plan.bounds,
        items = items,
        scale = map.scale,
        out = outside,
      )
    }
    job.cancel()
    return out
  }

  /**
   * I pixel del contorno della sagoma delle voci (il pannello arrotondato [items], raggio [itemRadius], 1 px all'interno) fuori dal
   * vetro: [round] se e' un rettangolo arrotondato, altrimenti i suoi limiti [bounds]. Le due sagome sono convesse: le voci stanno
   * nel vetro se ci sta il loro contorno.
   */
  private fun outsidePoints(items: Rect, itemRadius: Float, round: RoundRect?, bounds: Rect): Int {
    val shape = items.deflate(1f)
    val r = (itemRadius - 1f).coerceIn(0f, minOf(shape.width, shape.height) / 2f)
    val glass = round ?: RoundRect(bounds, CornerRadius.Zero)
    var n = 0
    fun check(x: Float, y: Float) {
      if (!glass.contains(Offset(x, y))) n++
    }
    var x = shape.left + r
    while (x <= shape.right - r) {
      check(x, shape.top)
      check(x, shape.bottom)
      x += 1f
    }
    var y = shape.top + r
    while (y <= shape.bottom - r) {
      check(shape.left, y)
      check(shape.right, y)
      y += 1f
    }
    if (r > 0f) {
      val steps = kotlin.math.ceil(r * kotlin.math.PI / 2.0).toInt().coerceAtLeast(1)
      val corners = listOf(
        Offset(shape.left + r, shape.top + r) to kotlin.math.PI,
        Offset(shape.right - r, shape.top + r) to 1.5 * kotlin.math.PI,
        Offset(shape.right - r, shape.bottom - r) to 0.0,
        Offset(shape.left + r, shape.bottom - r) to 0.5 * kotlin.math.PI,
      )
      for ((c, from) in corners) {
        for (i in 0..steps) {
          val a = from + 0.5 * kotlin.math.PI * i / steps
          check(c.x + r * kotlin.math.cos(a).toFloat(), c.y + r * kotlin.math.sin(a).toFloat())
        }
      }
    }
    return n
  }

  private fun log(name: String, frames: List<Frame>) {
    for (f in frames) {
      if (f.sinceTap == null && f.closing) continue
      if (f.sinceTap == null && f.t > 900L) continue
      println(
        "TAVOLA $name t=${f.t} tocco=${f.sinceTap ?: "-"} p=${"%.3f".format(f.progress)} orologio=${"%.0f".format(f.clock)} " +
          "fuoco=${"%.2f".format(f.focus)} goccia=${f.drop} ritaglio=${f.clipped} vetro=${f.glass.short()} " +
          "voci=${f.items.short()} scala=${"%.3f".format(f.scale)} fuori=${f.out}",
      )
    }
  }

  private fun Rect.short(): String = "%.0f,%.0f,%.0f,%.0f".format(left, top, right, bottom)

  @Test fun theItemsStayInsideTheGlassWhenAltroReopensWhileClosing() = runTest {
    // La nascita normale: le voci sempre dentro.
    val birth = take(tapMs = null, before = false)
    log("nascita", birth)
    assertTrue("nascita: voci fuori dal vetro", birth.all { it.out == 0 })
    val caught = ArrayList<Long>()
    for (tap in listOf(110L, 150L, 220L)) {
      val now = take(tap, before = false)
      val then = take(tap, before = true)
      log("riapre-$tap", now.filter { it.sinceTap != null })
      log("prima-$tap", then.filter { it.sinceTap != null && it.out > 0 })
      val reopened = now.filter { it.sinceTap != null }
      // Si riapre davvero, e torna a riposo.
      assertTrue("$tap ms: non si riapre", reopened.isNotEmpty() && reopened.all { !it.closing })
      assertTrue("$tap ms: non torna a riposo", kotlin.math.abs(reopened.last().progress - 1f) < 0.01f)
      val worst = now.maxByOrNull { it.out }
      assertTrue("$tap ms: voci fuori dal vetro a ${worst?.t} ms (${worst?.out} punti)", now.all { it.out == 0 })
      // La riapertura riprende la nascita con la goccia, come una nascita (S4k).
      assertTrue("$tap ms: la goccia non riparte", reopened.any { it.drop })
      val first = then.firstOrNull { it.out > 0 }
      println(
        "TAVOLA riepilogo tocco=$tap prima: ${then.count { it.out > 0 }} fotogrammi fuori, il primo a ${first?.sinceTap} ms " +
          "dal tocco (${first?.out} punti); adesso ${now.count { it.out > 0 }}",
      )
      if (first != null) caught += tap
    }
    // Con la regola di S4i il difetto del voto S4j si vede: le voci fuori dal vetro.
    assertTrue("la regola di prima non mostra il difetto: $caught", caught.isNotEmpty())
  }

  @Test fun theBirthClipHoldsUntilTheGlassHoldsThePanel() {
    val p = Rect(100f, 100f, 300f, 400f)
    // Il vetro piu' basso di 24 px (il bordo vicino di «Altro» a ~108 ms): il ritaglio resta.
    assertEquals(FluidMenuBirthClipCeiling, fluidMenuBirthClipProgress(1.02f, Rect(98f, 124f, 302f, 410f), p), 0f)
    // Il vetro che contiene il pannello, anche col mezzo pixel del pannello posato all'intero: niente ritaglio.
    assertEquals(1.02f, fluidMenuBirthClipProgress(1.02f, Rect(100.5f, 100f, 300f, 399.5f), p), 0f)
    // Sotto 1 il progresso e' quello di sempre.
    assertEquals(0.6f, fluidMenuBirthClipProgress(0.6f, Rect(150f, 150f, 250f, 250f), p), 0f)
  }
}
