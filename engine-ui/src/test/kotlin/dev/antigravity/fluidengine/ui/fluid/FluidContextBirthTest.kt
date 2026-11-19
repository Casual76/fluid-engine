@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidDropWarp
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Un fotogramma ogni 8 ms di tempo virtuale (120 Hz, come il Tab S9): la nascita su un orologio finto. */
private class BirthFrames(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
  override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
    delay(BirthFrameMillis)
    return onFrame(scheduler.currentTime * 1_000_000L)
  }
}

private const val BirthFrameMillis = 8L

/**
 * La nascita del menu contestuale ([fluidContextBirthSlab], [FluidContextBirth]) contro B1 (b) di Apple: WWDC25 219 a
 * 7:15, il tasto graffetta che diventa menu sulla foto, letto a mano sui fotogrammi del 4K (`riferimento-apple.md` della
 * sessione «contestuale»). I numeri di Apple qui sotto sono **le misure**, riscritte a parte apposta: se il profilo
 * dell'engine cambia, il test lo confronta con i fotogrammi e non con se stesso.
 */
class FluidContextBirthTest {

  // Il caso della galleria: la riga sollevata a tutta larghezza, il pannello di 252 dp sotto di lei a 12 dp, a 3 px/dp.
  private val card = Rect(48f, 1236f, 1185f, 1428f)
  private val panel = Rect(237f, 1464f, 993f, 2100f)
  private val radius = 78f
  private val seed = fluidMenuDropSeed(row = card, panel = panel, touchX = 615f, diameterPx = 84f, overlapPx = 12f)

  /** Le letture di Apple, in frazioni del menu a riposo: ms, bordo vicino, bordo lontano, larghezza. */
  private val apple = listOf(
    floatArrayOf(100.1f, 0.039f, 0.385f, 0.322f),
    floatArrayOf(133.5f, 0.069f, 0.542f, 0.469f),
    floatArrayOf(166.8f, 0.100f, 0.683f, 0.610f),
    floatArrayOf(200.2f, 0.128f, 0.794f, 0.737f),
    floatArrayOf(233.6f, 0.153f, 0.878f, 0.838f),
    floatArrayOf(266.9f, 0.139f, 0.938f, 0.909f),
    floatArrayOf(300.3f, 0.121f, 0.975f, 0.964f),
    floatArrayOf(333.7f, 0.093f, 1.003f, 1.004f),
    floatArrayOf(367.0f, 0.072f, 1.028f, 1.026f),
    floatArrayOf(400.4f, 0.053f, 1.028f, 1.030f),
    floatArrayOf(467.1f, 0.024f, 1.031f, 1.032f),
    floatArrayOf(533.9f, 0.006f, 1.019f, 1.030f),
    floatArrayOf(600.6f, 0.000f, 1.010f, 1.024f),
    floatArrayOf(767.4f, 0.000f, 1.000f, 1.004f),
  )

  /** La sagoma di Apple (senza l'anticipo della goccia): e' lei che si confronta coi fotogrammi. */
  private fun at(ms: Float, s: Rect = seed, p: Rect = panel) = requireNotNull(fluidContextBirthSlab(s, p, radius, ms, leadForDrop = false))

  @Test
  fun aMeasuredCurvePassesThroughItsPointsAndInventsNoBounceBetweenMonotonePoints() {
    val curve = FluidMeasuredCurve(floatArrayOf(0f, 10f, 20f, 30f, 40f), floatArrayOf(0f, 0.2f, 0.9f, 1f, 1f))
    assertEquals(0.2f, curve.at(10f), 1e-6f)
    assertEquals(0.9f, curve.at(20f), 1e-6f)
    var last = curve.at(0f)
    for (i in 1..400) {
      val v = curve.at(i / 10f)
      assertTrue("la curva scende a ${i / 10f} ms", v >= last - 1e-6f)
      assertTrue("la curva supera l'1 dove i punti non lo fanno", v <= 1f + 1e-6f)
      last = v
    }
    assertEquals(0f, curve.at(-5f), 0f)
    assertEquals(1f, curve.at(99f), 0f)
  }

  @Test
  fun theBirthStartsExactlyOnTheSeedAndEndsExactlyOnThePanel() {
    val first = at(0f)
    assertEquals(seed.left, first.frame.left, 0.01f)
    assertEquals(seed.top, first.frame.top, 0.01f)
    assertEquals(seed.right, first.frame.right, 0.01f)
    assertEquals(seed.bottom, first.frame.bottom, 0.01f)
    for (r in listOf(first.cornerRadii.topLeft, first.cornerRadii.topRight, first.cornerRadii.bottomRight, first.cornerRadii.bottomLeft)) {
      assertEquals(seed.minDimension / 2f, r, 0.01f)
    }
    assertNull(fluidContextBirthSlab(seed, panel, radius, FluidContextBirthProfile.EndMillis))
    val last = at(FluidContextBirthProfile.EndMillis - 0.01f)
    assertEquals(panel.left, last.frame.left, 1f)
    assertEquals(panel.top, last.frame.top, 1f)
    assertEquals(panel.right, last.frame.right, 1f)
    assertEquals(panel.bottom, last.frame.bottom, 1f)
    assertEquals(FluidCornerRadii.all(radius), last.cornerRadii)
    assertEquals(0f, fluidContextBirthProgress(0f), 0f)
    assertEquals(1f, fluidContextBirthProgress(FluidContextBirthProfile.EndMillis), 0f)
  }

  /**
   * La larghezza della nascita: fino a 300 ms mai piu' larga della frazione di Apple (la goccia ha le proporzioni di quella
   * di Apple, [FluidContextBirthProfile.Aspect]), alta a 100–133 ms; da 300 ms la frazione di Apple.
   */
  private fun assertWidth(where: String, ms: Float, appleWidth: Float, f: Rect, p: Rect) {
    if (ms >= FluidContextBirthProfile.AspectTo) {
      assertEquals("larghezza $where a $ms ms", appleWidth, f.width / p.width, 0.002f)
    } else {
      assertTrue("larghezza $where a $ms ms: ${f.width / p.width}", f.width / p.width <= appleWidth + 0.002f)
      if (ms <= 133.5f) assertTrue("goccia alta $where a $ms ms: ${f.width} x ${f.height}", f.width < f.height)
    }
  }

  @Test
  fun fromAHundredMillisecondsTheShapeIsApplesFrameByFrame() {
    for ((ms, near, far, width) in apple.map { it.toList() }) {
      val f = at(ms).frame
      assertEquals("bordo vicino a $ms ms", near, (f.top - panel.top) / panel.height, 0.002f)
      assertEquals("bordo lontano a $ms ms", far, (f.bottom - panel.top) / panel.height, 0.002f)
      assertWidth("", ms, width, f, panel)
    }
    // Le proporzioni: a 100 ms il fagiolo di Apple e' largo 0,64 della sua altezza, e cosi' il nostro.
    val bean = at(100.1f).frame
    assertEquals(0.64f, bean.width / bean.height, 0.01f)
  }

  @Test
  fun theBodyLeavesTheCardThenComesBackAndOvershootsOnceOnTheFarSide() {
    var liftPeak = 0f
    var liftAt = 0f
    var farPeak = 0f
    var farAt = 0f
    var widthPeak = 0f
    var widthAt = 0f
    for (ms in 100..860) {
      val f = at(ms.toFloat()).frame
      val lift = (f.top - panel.top) / panel.height
      val far = (f.bottom - panel.bottom) / panel.height
      val width = f.width / panel.width - 1f
      if (lift > liftPeak) { liftPeak = lift; liftAt = ms.toFloat() }
      if (far > farPeak) { farPeak = far; farAt = ms.toFloat() }
      if (width > widthPeak) { widthPeak = width; widthAt = ms.toFloat() }
      // Mai un secondo superamento sotto il riposo: Apple non ne ha (R4 B1, «nessuna seconda oscillazione»).
      if (ms > 550) assertTrue("il bordo lontano rientra sotto il riposo a $ms ms", far >= -0.002f)
      if (ms > 600) assertTrue("il bordo vicino passa oltre il riposo a $ms ms", lift >= -0.002f)
    }
    // Il bordo vicino si stacca dalla card del 15% dell'altezza a ~234 ms (la goccia che «sale» di Apple) e ci torna.
    assertEquals(0.153f, liftPeak, 0.005f)
    assertTrue("picco del distacco a $liftAt ms", liftAt in 220f..250f)
    assertEquals(0f, (at(620f).frame.top - panel.top) / panel.height, 0.002f)
    // Un solo superamento: 3% dell'altezza sul lato lontano fra 367 e 467 ms, 3% della larghezza fra 400 e 534 ms.
    assertEquals(0.031f, farPeak, 0.003f)
    assertTrue("picco del bordo lontano a $farAt ms", farAt in 360f..480f)
    assertEquals(0.032f, widthPeak, 0.003f)
    assertTrue("picco della larghezza a $widthAt ms", widthAt in 390f..540f)
  }

  @Test
  fun theDropIsAskedForAppleALittleLaterThanItShows() {
    // La sagoma chiesta alla goccia e' quella di Apple qualche ms dopo (l'anticipo misurato sul banco), con l'oltrepasso
    // ridotto della parte che la goccia aggiunge; ai due capi e' la stessa.
    assertEquals(at(0f).frame, requireNotNull(fluidContextBirthSlab(seed, panel, radius, 0f)).frame)
    assertNull(fluidContextBirthSlab(seed, panel, radius, FluidContextBirthProfile.EndMillis))
    val asked = requireNotNull(fluidContextBirthSlab(seed, panel, radius, 200.2f)).frame
    val later = at(200.2f + FluidContextBirthProfile.FarLead.at(200.2f)).frame
    assertEquals(later.bottom, asked.bottom, 0.5f)
    assertTrue("chiesta piu' avanti di Apple a 200 ms", asked.bottom > at(200.2f).frame.bottom + 10f)
    var peak = 0f
    for (ms in 300..700) peak = maxOf(peak, (requireNotNull(fluidContextBirthSlab(seed, panel, radius, ms.toFloat())).frame.bottom - panel.bottom) / panel.height)
    assertEquals(0.031f * FluidContextBirthProfile.OvershootShare, peak, 0.003f)
  }

  @Test
  fun aSeedOnTheLeftEdgeGrowsToTheRightLikeApplesPaperclip() {
    val left = fluidMenuDropSeed(row = card, panel = panel, touchX = 0f, diameterPx = 84f, overlapPx = 12f)
    assertEquals(panel.left, left.left, 0.01f)
    for (ms in listOf(133.5f, 233.6f, 333.7f, 467.1f)) {
      val f = at(ms, left).frame
      assertEquals("il lato della sorgente resta fermo a $ms ms", panel.left, f.left, 1f)
    }
    val centred = at(233.6f).frame
    assertTrue("un seme al centro cresce dai due lati", centred.left > panel.left + 20f && centred.right < panel.right - 20f)
  }

  @Test
  fun aPanelAboveTheCardIsTheSameBirthMirrored() {
    val above = Rect(panel.left, card.top - 12f - panel.height, panel.right, card.top - 12f)
    val upSeed = fluidMenuDropSeed(row = card, panel = above, touchX = 615f, diameterPx = 84f, overlapPx = 12f)
    for ((ms, near, far, width) in apple.map { it.toList() }) {
      val f = at(ms, upSeed, above).frame
      assertEquals("bordo vicino a $ms ms", near, (above.bottom - f.bottom) / above.height, 0.002f)
      assertEquals("bordo lontano a $ms ms", far, (above.bottom - f.top) / above.height, 0.002f)
      assertWidth("sopra", ms, width, f, above)
    }
    val tail = at(200f, upSeed, above).cornerRadii
    assertTrue("la coda tonda e' verso la card, sotto", tail.bottomLeft >= tail.topLeft)
  }

  @Test
  fun theCornersAreTheDropUntilTheBodyFormsThenThePanels() {
    for (ms in 0..860) {
      val slab = at(ms.toFloat())
      val cap = slab.frame.minDimension / 2f + 0.001f
      val r = slab.cornerRadii
      assertTrue("raggi oltre mezzo lato a $ms ms", r.topLeft <= cap && r.topRight <= cap && r.bottomLeft <= cap && r.bottomRight <= cap)
      assertTrue(slab.frame.width > 0f && slab.frame.height > 0f)
    }
    // In viaggio gli angoli stanno al 78% di mezzo lato: ogni lato ha un tratto dritto, e il ritaglio delle voci lo segue.
    val bean = at(100f)
    assertEquals(bean.frame.minDimension / 2f * FluidContextBirthProfile.RoundnessInTravel, bean.cornerRadii.bottomLeft, 0.01f)
    assertEquals(bean.frame.minDimension / 2f * FluidContextBirthProfile.RoundnessInTravel, bean.cornerRadii.topLeft, 0.01f)
    val egg = at(300f).cornerRadii
    assertTrue("a 300 ms la coda verso la card e' ancora piu' tonda del lato lontano", egg.topLeft > egg.bottomLeft)
    assertEquals(FluidCornerRadii.all(radius), at(440f).cornerRadii)
  }

  @Test
  fun theClockMatchesApplesTimesAndCanBeResumedHalfway() {
    // Il bordo lontano di Apple: a meta' corsa fra 133 e 167 ms, al 90% fra 234 e 300 ms, al pannello a 334 ms.
    val half = (0..400).first { fluidContextBirthProgress(it.toFloat()) >= 0.5f }
    assertTrue("a meta' a $half ms", half in 134..167)
    val ninety = (0..400).first { fluidContextBirthProgress(it.toFloat()) >= 0.9f }
    assertTrue("al 90% a $ninety ms", ninety in 234..300)
    var last = 0f
    for (ms in 1..333) {
      val p = fluidContextBirthProgress(ms.toFloat())
      assertTrue("il progresso scende a $ms ms", p >= last)
      last = p
      // Nei primi ~40 ms il progresso e' fermo a zero (la capsula di Apple si stringe sul posto): li' non si inverte.
      if (ms >= 60) assertEquals("ripresa a $ms ms", ms.toFloat(), fluidContextBirthMillisAt(p), 0.6f)
    }
    // La goccia esce dalla card in 50 ms (a 67 ms Apple e' gia' una goccia piena).
    assertEquals(0f, fluidContextBirthPresence(0f), 0f)
    assertEquals(1f, fluidContextBirthPresence(50f), 0f)
    // Le voci: sfocate quanto Apple a 234 ms (ancora morbide), nitide a 334. La sfocatura cala dritta nel tempo.
    fun blur(ms: Float) = fluidMenuLensBlurDp(FluidContextFocusEasing.transform((ms / FluidContextFocusMillis).coerceIn(0f, 1f)))
    assertEquals(FluidMotion.MenuContentMaxBlurDp / 2f, blur(FluidContextFocusMillis / 2f), 0.01f)
    assertTrue(blur(234f) > 1.2f)
    assertEquals(0f, blur(334f), 0.001f)
  }

  @Test
  fun theLayerRunsTheBirthOnItsOwnClockAndClosesWithTheMenusReturn() = runTest {
    val job = SupervisorJob()
    val scope = CoroutineScope(coroutineContext.minusKey(Job) + job + BirthFrames(testScheduler))
    val journey = FluidMenuJourney().apply {
      // `this@FluidContextBirthTest`: dentro `apply` il nome `seed` sarebbe quello del viaggio, ancora nullo.
      prepare(null, Offset.Zero, panel, radius, FluidMenuInflation.None, seed = this@FluidContextBirthTest.seed, liquidMenu = true)
      retarget(1f)
    }
    val birth = FluidContextBirth().apply {
      this.radius = this@FluidContextBirthTest.radius
      prepare(fresh = true)
    }
    // Il primo fotogramma, prima di qualunque coroutine: il seme, invisibile.
    assertEquals(seed, requireNotNull(birth.travel(journey)).frame)
    assertEquals(0f, birth.presence(journey), 0f)
    scope.launch { birth.open(journey) }
    fun step(ms: Long) {
      advanceTimeBy(ms)
      runCurrent()
    }
    step(200)
    assertEquals(fluidContextBirthProgress(birth.millis()), journey.progress.value, 0.002f)
    assertEquals(requireNotNull(fluidContextBirthSlab(seed, panel, radius, birth.millis())).frame, requireNotNull(birth.travel(journey)).frame)
    // La card non aspetta il menu: con la sua molla e' gia' oltre il 95%.
    assertTrue("card a ${birth.card.value}", birth.card.value > 0.95f)
    step(900)
    assertEquals(1f, journey.progress.value, 0f)
    assertNull(birth.travel(journey))
    assertEquals(1f, journey.focus.value, 0f)

    // Il congedo a menu fermo: il ritorno del menu dal tasto (233 ms, il contorno), la card con lui.
    journey.retarget(0f)
    assertTrue(journey.controlledReturn)
    scope.launch { birth.close(journey, reducedMotion = false) }
    // L'inchiostro di B1 (a), scelto da Alessio: le voci si sfocano subito ma restano piene fino a ~67 ms...
    step(50)
    assertEquals(1f, journey.contentVisibility.value, 0f)
    assertTrue("fuoco a ${journey.focus.value}", journey.focus.value < 0.5f)
    // ...e a 120 ms se ne stanno andando, entro 167 ms non ci sono piu'.
    step(70)
    assertTrue("inchiostro a ${journey.contentVisibility.value}", journey.contentVisibility.value in 0.2f..0.7f)
    val returning = requireNotNull(birth.travel(journey))
    val source = FluidForm.Slab(seed, FluidCornerRadii.all(seed.minDimension / 2f))
    assertEquals(fluidMenuReturnSlab(source, FluidForm.Slab(panel, FluidCornerRadii.all(radius)), journey.progress.value), returning)
    assertEquals(journey.progress.value, birth.card.value, 0.03f)
    step(400)
    assertTrue(journey.settledClosed)
    assertEquals(0f, birth.card.value, 0.001f)
    assertEquals(0f, journey.contentVisibility.value, 0f)
    job.cancel()
  }

  private fun warpAt(ms: Float, s: Rect = seed, p: Rect = panel): FluidDropWarp? {
    val slab = requireNotNull(fluidContextBirthSlab(s, p, radius, ms, leadForDrop = false))
    return fluidContextBirthWarp(s, p, slab, ms)
  }

  @Test
  fun theContourIsApplesDropBeanAndEggThenTheSlab() {
    // 67 ms: la goccia sulla sorgente, piu' stretta in avanti che sulla card, e tutta lente.
    val drop = requireNotNull(warpAt(66.7f))
    assertTrue("punta ${drop.scaleAt(0.97f)}, coda ${drop.scaleAt(0.05f)}", drop.scaleAt(0.97f) < drop.scaleAt(0.05f) - 0.2f)
    assertEquals(1f, drop.lens, 0.001f)
    assertTrue("ingrandimento ${drop.magnify}", drop.magnify > 0.2f)
    // 100 ms: il fagiolo, con la vita.
    val bean = requireNotNull(warpAt(100.1f))
    assertTrue("vita ${bean.scaleAt(0.5f)}, lobo ${bean.scaleAt(0.15f)}", bean.scaleAt(0.5f) < bean.scaleAt(0.15f) - 0.02f)
    // 167 ms: l'uovo con la coda verso la card (la coda e' dal lato del seme, la testa dal lato lontano).
    val egg = requireNotNull(warpAt(166.8f))
    assertTrue("coda ${egg.scaleAt(0.02f)}", egg.scaleAt(0.02f) < 0.55f)
    assertTrue("testa ${egg.scaleAt(0.8f)}", egg.scaleAt(0.8f) > 0.97f)
    assertTrue(egg.tailY < egg.headY)
    // 267 ms: il corpo a botte, i capi piu' stretti del mezzo.
    val barrel = requireNotNull(warpAt(266.9f))
    assertTrue(barrel.scaleAt(0.5f) > barrel.scaleAt(0.02f))
    // Da 400 ms niente: a menu aperto e fermo il contorno e' la lastra.
    assertNull(warpAt(400.5f))
    assertNull(warpAt(800f))
    // Un pannello sopra la card: la coda resta dal lato della card, cioe' in basso.
    val above = Rect(panel.left, card.top - 12f - panel.height, panel.right, card.top - 12f)
    val upSeed = fluidMenuDropSeed(row = card, panel = above, touchX = 615f, diameterPx = 84f, overlapPx = 12f)
    val upEgg = requireNotNull(warpAt(166.8f, upSeed, above))
    assertTrue(upEgg.tailY > upEgg.headY)
  }

  @Test
  fun theDropLensGoesBackToTheProfileWithoutAJumpByTwoHundredMilliseconds() {
    var last = requireNotNull(warpAt(0f))
    var ms = 4f
    while (ms <= 400f) {
      val now = warpAt(ms) ?: break
      assertTrue("ingrandimento a $ms ms", abs(now.magnify - last.magnify) < 0.04f)
      assertTrue("limpidezza a $ms ms", abs(now.clarity - last.clarity) < 0.1f)
      assertTrue("lente a $ms ms", abs(now.lens - last.lens) < 0.16f)
      last = now
      ms += 4f
    }
    val back = requireNotNull(warpAt(233.6f))
    assertEquals(0f, back.magnify, 0.001f)
    assertEquals(0f, back.clarity, 0.001f)
    assertEquals(0f, back.lens, 0.001f)
  }

  @Test
  fun theWarpOnlyPullsTowardTheAxisAndNeverLeavesTheRestShape() {
    for (ms in listOf(66.7f, 100.1f, 133.5f, 166.8f, 200.2f, 266.9f, 333.7f)) {
      val slab = requireNotNull(fluidContextBirthSlab(seed, panel, radius, ms, leadForDrop = false))
      val warp = requireNotNull(fluidContextBirthWarp(seed, panel, slab, ms))
      val out = FloatArray(2)
      val f = slab.frame
      for (k in 0..200) {
        // I lati del rettangolo a riposo, a passi.
        val x = f.left + f.width * (k % 41) / 40f
        for (y in listOf(f.top, f.bottom)) {
          warp.warpInto(x, y, out, 0)
          assertTrue("$ms ms: ($x,$y) -> ${out[0]}", out[0] >= f.left - 0.01f && out[0] <= f.right + 0.01f)
          // L'asse e' verticale (dito al centro): la deformazione e' solo di traverso.
          assertEquals(y, out[1], 0.5f)
        }
        val y = f.top + f.height * (k % 41) / 40f
        for (xs in listOf(f.left, f.right)) {
          warp.warpInto(xs, y, out, 0)
          assertTrue(out[0] >= f.left - 0.01f && out[0] <= f.right + 0.01f)
          assertTrue("$ms ms: verso l'asse", abs(out[0] - f.center.x) <= abs(xs - f.center.x) + 0.01f)
        }
      }
    }
  }

  @Test
  fun theInkOfTheCloseHoldsLikeApplesThenLeaves() {
    val ink = TargetBasedAnimation(fluidContextExitInkSpec(1f), Float.VectorConverter, 1f, 0f)
    fun at(ms: Long) = ink.getValueFromNanos(ms * 1_000_000L)
    assertEquals(1f, at(0), 0f)
    assertEquals(1f, at(66), 0f)
    assertTrue("a 117 ms ${at(117)}", at(117) in 0.3f..0.7f)
    assertTrue("a 150 ms ${at(150)}", at(150) < 0.1f)
    assertEquals(0f, at(167), 0f)
    // Mai uno scatto all'insu': scende e basta.
    var last = 1f
    for (ms in 0L..167L) {
      assertTrue("a $ms ms", at(ms) <= last + 1e-6f)
      last = at(ms)
    }
  }

  @Test
  fun aBackHalfwayReturnsFromTheShapeItHadNotFromThePanel() = runTest {
    val job = SupervisorJob()
    val scope = CoroutineScope(coroutineContext.minusKey(Job) + job + BirthFrames(testScheduler))
    val journey = FluidMenuJourney().apply {
      // `this@FluidContextBirthTest`: dentro `apply` il nome `seed` sarebbe quello del viaggio, ancora nullo.
      prepare(null, Offset.Zero, panel, radius, FluidMenuInflation.None, seed = this@FluidContextBirthTest.seed, liquidMenu = true)
      retarget(1f)
    }
    val birth = FluidContextBirth().apply {
      this.radius = this@FluidContextBirthTest.radius
      prepare(fresh = true)
    }
    val opening = scope.launch { birth.open(journey) }
    advanceTimeBy(136)
    runCurrent()
    val before = requireNotNull(birth.travel(journey))
    journey.retarget(0f)
    opening.cancel()
    scope.launch { birth.close(journey, reducedMotion = false) }
    runCurrent()
    val after = requireNotNull(birth.travel(journey))
    // Nessun salto: la chiusura parte dalla sagoma che c'era, entro mezzo fotogramma di moto.
    assertTrue("salto di ${abs(after.frame.bottom - before.frame.bottom)} px", abs(after.frame.bottom - before.frame.bottom) < 24f)
    assertTrue("salto di ${abs(after.frame.width - before.frame.width)} px", abs(after.frame.width - before.frame.width) < 24f)
    advanceTimeBy(600)
    runCurrent()
    assertTrue(journey.settledClosed)
    job.cancel()
  }
}
