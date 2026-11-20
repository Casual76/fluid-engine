@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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

/** Fotogrammi a 120 Hz di tempo virtuale (in ms interi: 8, 17, 25, ...). */
private class GroupFrames(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
  override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
    val now = scheduler.currentTime
    val next = frameAfter(now)
    delay(next - now)
    return onFrame(next * 1_000_000L)
  }

  companion object {
    /** Il primo fotogramma dopo [ms]: i fotogrammi stanno a `ceil(k · 25 / 3)` ms. */
    fun frameAfter(ms: Long): Long {
      var k = ms * 3L / 25L
      while ((k * 25L + 2L) / 3L <= ms) k++
      return (k * 25L + 2L) / 3L
    }
  }
}

/**
 * S4f sul Tab S9 (NO 3): al ritorno del gruppo, in scuro a f0236, la stella e' gia' leggibile, il glifo a sinistra a meta' e
 * quello a destra ridotto al bordo; la capsula intera arriva nei fotogrammi dopo. Nell'annullo a meta' nascita (f0193–f0195) i
 * simboli della capsula stanno sopra le righe del menu. Le tre icone devono tornare insieme, con la capsula.
 *
 * Il ritorno vero ([FluidMenuJourney.run], la goccia del banco come `runLiquid`) da menu fermo e da una nascita interrotta a
 * ~150 ms: a ogni fotogramma la sagoma disegnata e la fase delle icone ([FluidMenuAnchor.groupProgress], spente da
 * [FluidMotion.MenuGroupFaceProgressSpan]). Quando le icone si vedono, il vetro deve contenerle tutte e tre: prima (la sola
 * regola del progresso) e dopo (con la copertura della capsula, [fluidMenuGroupFaceCover]).
 */
class GruppoRitornoIconeTest {
  private val d = 2.125f
  // Il gruppo della foto: tre tasti da 44 dp, il menu sul terzo, e il pannello sotto.
  private val capsule = Rect(64f, 600f, 64f + 132f * d, 600f + 44f * d)
  private val trigger = Rect(capsule.right - 44f * d, capsule.top, capsule.right, capsule.bottom)
  private val panel = Rect(64f, capsule.bottom + 8f * d, 64f + 250f * d, capsule.bottom + 8f * d + 196f * d)

  /** Le icone: 24 dp al centro di ogni tasto, quindi 10 dp dentro la capsula a sinistra, a destra, sopra e sotto. */
  private val icons = Rect(capsule.left + 10f * d, capsule.top + 10f * d, capsule.right - 10f * d, capsule.bottom - 10f * d)

  private class Frame(
    val t: Long,
    val drawn: Rect,
    val progress: Float,
    val phase: Float,
    val phaseBefore: Float,
    /** L'inchiostro del congedo ([FluidMenuJourney.contentVisibility]) e le voci come le disegna l'host (`lensInkAlpha`). */
    val visibility: Float,
    val ink: Float,
  )

  private fun TestScope.ritorno(dismissMs: Long): List<Frame> {
    val job = SupervisorJob()
    val scope = CoroutineScope(coroutineContext.minusKey(Job) + job + GroupFrames(testScheduler))
    val anchor = FluidMenuAnchor().apply {
      restBounds = trigger
      pressedBounds = trigger
      groupBounds = capsule
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
    // Come l'host (`FluidMenuLayer`): la copertura della capsula dalla sagoma disegnata.
    journey.groupCover = { fluidMenuGroupFaceCover(physics.ensurePlan().bounds, capsule) }
    val driver = TodayLiquidDriver(physics, d)
    val open = scope.launch { journey.run(reducedMotion = false) }
    runCurrent()
    val out = ArrayList<Frame>()
    var at = 0L
    var closed = false
    var t = GroupFrames.frameAfter(0L)
    while (t < dismissMs + 900L) {
      advanceTimeBy(t - at)
      runCurrent()
      at = t
      if (!closed && t >= dismissMs) {
        // Il congedo nell'ordine dell'host: il verso cambia nella composizione, poi riparte l'effetto col ritorno.
        closed = true
        journey.retarget(0f)
        open.cancel()
        scope.launch { journey.run(reducedMotion = false) }
        runCurrent()
      }
      driver.frame(t * 1_000_000L)
      if (closed) {
        out += Frame(
          t, physics.ensurePlan().bounds, journey.progress.value, anchor.groupProgress(), fluidMenuGroupFaceReturn(journey.progress.value),
          journey.contentVisibility.value, journey.lensInkAlpha(),
        )
      }
      t = GroupFrames.frameAfter(t)
    }
    job.cancel()
    return out
  }

  private fun holds(drawn: Rect) =
    drawn.left <= icons.left + 1f && drawn.right >= icons.right - 1f && drawn.top <= icons.top + 1f && drawn.bottom >= icons.bottom - 1f

  private fun check(name: String, frames: List<Frame>) {
    val span = FluidMotion.MenuGroupFaceProgressSpan
    var before = 0
    var after = 0
    for (f in frames) {
      val cover = fluidMenuGroupFaceCover(f.drawn, capsule)
      println(
        "TAVOLA $name t=${f.t} p=${"%.3f".format(f.progress)} vetro=${"%.0f..%.0f x %.0f..%.0f".format(f.drawn.left, f.drawn.right, f.drawn.top, f.drawn.bottom)} " +
          "copertura=${"%.2f".format(cover)} icone-prima=${"%.2f".format(f.phaseBefore)} icone-dopo=${"%.2f".format(f.phase)}",
      )
      if (f.phaseBefore < span && !holds(f.drawn)) before++
      if (f.phase < span && !holds(f.drawn)) after++
    }
    println("TAVOLA $name fotogrammi con le icone fuori dal vetro: prima $before, dopo $after")
    assertEquals("$name: icone che si vedono fuori dal vetro", 0, after)
    assertTrue("$name: le icone tornano", frames.any { it.phase < span * 0.5f })
    // Mai la capsula vuota: appena il vetro tiene le icone, e le righe del menu se ne sono andate, si accendono; al piu' un
    // fotogramma a 120 Hz. Con la prima regola (la capsula a 0,8 di intersezione su unione) era 1 da fermo e 9 nell'annullo.
    val empty = frames.count { holds(it.drawn) && it.progress <= 0.02f && it.phase >= span }
    println("TAVOLA $name fotogrammi col vetro che tiene le icone e le icone spente: $empty")
    assertTrue("$name: capsula vuota per $empty fotogrammi", empty <= 1)
    assertEquals("$name: piene alla consegna", 0f, frames.last().phase, 0.02f)
    // S4g (NO 3): mai la pillola vuota piu' stretta delle icone vicino alla capsula (scuro f0131, chiaro f0058), da cui la
    // capsula larga coi glifi arrivava in un passo. Col tondo del ritorno erano i fotogrammi in cui la bolla si allargava.
    val narrow = frames.count {
      it.phase >= span && it.drawn.height <= capsule.height * 1.6f && it.drawn.width < icons.width && it.progress < 0.5f
    }
    val step = frames.zipWithNext().maxOf { (a, b) -> a.phase - b.phase }
    println("TAVOLA $name pillola vuota piu' stretta delle icone: $narrow fotogrammi; passo piu' grande delle icone ${"%.2f".format(step)}")
    assertEquals("$name: pillola vuota stretta", 0, narrow)
    // S4g-bis (d): in chiaro a f0361 e a f0391–f0392 voci sfocate dietro le tre icone piene. Le icone si vedono con la curva
    // d'uscita della barra; con la regola di S4g (il quadrato dell'inchiostro uscito) erano gia' quasi piene sopra le voci.
    var latch = 1f
    var overlapBefore = 0
    var overlapAfter = 0
    for (f in frames) {
      val cover = fluidMenuGroupFaceCover(f.drawn, capsule)
      val gone = 1f - f.visibility
      latch = minOf(latch, fluidMenuGroupFaceReturn(f.progress, cover * gone * gone))
      val iconsBefore = iconAlpha(latch)
      val iconsAfter = iconAlpha(f.phase)
      if (iconsBefore > 0.01f && f.visibility > 0.01f) overlapBefore++
      if (iconsAfter > 0.01f && f.ink > 0.01f) overlapAfter++
      println(
        "TAVOLA $name t=${f.t} icone ${"%.2f".format(iconsAfter)} voci ${"%.2f".format(f.ink)} " +
          "(S4g: icone ${"%.2f".format(iconsBefore)} voci ${"%.2f".format(f.visibility)})",
      )
    }
    println("TAVOLA $name fotogrammi con icone e voci insieme: S4g $overlapBefore, ora $overlapAfter")
    assertEquals("$name: icone e voci insieme", 0, overlapAfter)
  }

  /** L'opacita' delle icone della barra a questa fase (`FluidToolbar`: la curva d'uscita, con la sfocatura). */
  private fun iconAlpha(phase: Float): Float =
    if (phase >= FluidMotion.MenuGroupFaceProgressSpan) 0f else
      GlassContentCurves.alpha(GlassContentRole.Exit, phase / FluidMotion.MenuGroupFaceProgressSpan, blurAvailable = true)

  @Test fun theIconsComeBackTogetherWithTheCapsuleFromARestingMenu() = runTest { check("fermo", ritorno(dismissMs = 1_200L)) }

  @Test fun theIconsStayOffTheEggWhenTheBirthIsCancelled() = runTest {
    // L'annullo di S4f (f0192–f0195): il congedo a ~150 ms dalla nascita, con l'uovo e le righe.
    check("annullo", ritorno(dismissMs = 150L))
  }

  @Test fun theCoverIsTheCapsuleOnlyWhenTheGlassIsTheCapsule() {
    assertEquals(1f, fluidMenuGroupFaceCover(capsule, capsule), 1e-4f)
    // Il pannello copre tutta la capsula ma non e' lei; la bolla tonda sul tasto e' piu' stretta.
    assertTrue(fluidMenuGroupFaceCover(panel.copy(top = capsule.top), capsule) < 0.5f)
    val bubble = Rect(trigger.center.x - 32f * d, trigger.center.y - 32f * d, trigger.center.x + 32f * d, trigger.center.y + 32f * d)
    assertTrue(fluidMenuGroupFaceCover(bubble, capsule) < 0.5f)
    // Il pannello che si stringe sopra la capsula le contiene, ma e' molto piu' grande: spente.
    assertEquals(0f, fluidMenuGroupFaceCover(Rect(capsule.left - 40f, capsule.top - 300f, capsule.right + 40f, capsule.bottom), capsule), 0f)
    // Il vetro che le ha appena raggiunte: si accendono; con 5 dp attorno sono piene.
    assertEquals(0f, fluidMenuGroupFaceCover(icons, capsule), 1e-4f)
    val wrapped = Rect(icons.left - 5f * d, icons.top - 5f * d, icons.right + 5f * d, icons.bottom + 5f * d)
    assertEquals(1f, fluidMenuGroupFaceCover(wrapped, capsule), 0.01f)
    // Spente finche' il vetro non le tiene, piene quando le avvolge, anche a progresso zero.
    assertTrue(fluidMenuGroupFaceReturn(0f, 0f) >= FluidMotion.MenuGroupFaceProgressSpan)
    assertEquals(0f, fluidMenuGroupFaceReturn(0f, 1f), 0f)
    // Senza l'host conta il solo progresso, come prima.
    assertEquals(0.5f, fluidMenuGroupFaceReturn(FluidMenuGroupFaceReturnShare / 2f), 1e-4f)
  }
}
