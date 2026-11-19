@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.glass.field.trace.TodaySpring
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Un fotogramma ogni 16 ms di tempo virtuale: le molle vere del viaggio, senza un Recomposer. */
private class JourneyFrames(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
  override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
    delay(JourneyFrameMillis)
    return onFrame(scheduler.currentTime * 1_000_000L)
  }
}

private const val JourneyFrameMillis = 16L

/** Il banco: un viaggio su fotogrammi finti. */
private class JourneyBench(val test: TestScope) {
  private val job = SupervisorJob()
  val scope = CoroutineScope(test.coroutineContext.minusKey(Job) + job + JourneyFrames(test.testScheduler))

  fun frames(count: Int, read: () -> Float): List<Float> = List(count) {
    test.advanceTimeBy(JourneyFrameMillis)
    test.runCurrent()
    read()
  }

  fun close() = job.cancel()
}

/**
 * Il viaggio dei menu (3.0, fase 7) su un orologio finto: la molla d'apertura che oltrepassa e si
 * posa, la partenza dal tasto premuto che si scioglie, e soprattutto **l'inversione**: un back a meta'
 * apertura torna indietro dal valore di adesso, con lo slancio che aveva — non dal pannello pieno, e
 * non da fermo (un `Animatable` cancellato azzera la sua velocita': `retarget` la legge prima).
 */
class FluidMenuJourneyTest {

  private val rest = Rect(100f, 600f, 300f, 644f)
  private val pressed = Rect(90f, 590f, 310f, 654f)
  private val panel = Rect(40f, 300f, 292f, 500f)

  private fun anchor() = FluidMenuAnchor().apply {
    restBounds = rest
    pressedBounds = pressed
  }

  private fun opened(anchor: FluidMenuAnchor = anchor()): FluidMenuJourney = FluidMenuJourney().apply {
    prepare(anchor, Offset.Zero, panel, radius = 22f, inflation = FluidMenuInflation(0.5f, 0.6f))
    retarget(1f)
  }

  @Test
  fun ilDisegnoFraMollaEIntegratoreConservaTuttiIVerticiDopoIlCongedo() = runTest {
    val journey = FluidMenuJourney()
    journey.prepare(anchor(), Offset.Zero, panel, 22f, FluidMenuInflation.None, liquidMenu = true)
    journey.retarget(1f)
    val physics = FluidPhysicsState(FluidForm.circle(Offset.Zero, 1f))
    journey.install(physics)
    val opening = TodaySpring(FluidMotion.dropOpen(), 0f, 1f)
    fun step(t: Long) = physics.stepDrop(requireNotNull(physics.dropRestSlabNow()), t * 1_000_000L)
    for (ms in 0L..160L step 8) {
      journey.progress.snapTo(opening.value(ms * 1_000_000L))
      step(ms)
    }
    journey.retarget(0f)
    val closing = TodaySpring(androidx.compose.animation.core.spring<Float>(
      FluidMotion.DropJourneyDamping, FluidMotion.MenuLiquidCloseStiffness),
      journey.progress.value, 0f, opening.velocity(160_000_000L))
    for (ms in listOf(177L, 202L, 227L, 251L, 277L)) {
      val plan = physics.ensurePlan()
      assertTrue("anello in moto a $ms", plan.vertCount > 0)
      val previous = plan.verts.copyOf(plan.vertCount * 2)
      journey.progress.snapTo(closing.value((ms - 160) * 1_000_000L))
      val between = physics.ensurePlan()
      org.junit.Assert.assertArrayEquals("prima del passo fisico a $ms", previous,
        between.verts.copyOf(between.vertCount * 2), 0f)
      step(ms)
    }
  }

  @Test
  fun theReturnHoldsThePanelAndLeavesABubbleOnTheSourceColumn() {
    val source = FluidForm.Slab(Rect(42f, 510f, 86f, 554f), FluidCornerRadii.all(22f))
    val destination = FluidForm.Slab(panel, FluidCornerRadii.all(24f))
    fun at(millis: Int) = fluidMenuReturnSlab(source, destination, 1f - millis.toFloat() / FluidMotion.MenuReturnMillis)
    assertEquals(destination, at(0))
    assertEquals(panel.height * 0.03f, at(33).frame.top - panel.top, 0.5f)
    assertEquals(panel.height * 0.12f, at(67).frame.top - panel.top, 0.5f)
    for (ms in 0..200) assertTrue(at(ms).frame.width > 0 && at(ms).frame.height > 0)
    assertEquals(source.frame.center.x, at(150).frame.center.x, 0.001f)
    assertTrue(at(200).frame.height > source.frame.height)
    assertEquals(source, at(233))
  }

  @Test
  fun returnInkBlursBeforeTheSourceFaceComesBack() {
    val focus = TodaySpring(fluidMenuReturnFocusSpec(1f), 1f, 0f)
    val ink = TodaySpring(fluidMenuReturnInkSpec(1f), 1f, 0f)
    assertTrue(fluidMenuLensBlurDp(focus.value(33_000_000)) > 2f)
    assertTrue(ink.value(67_000_000) < 0.07f)
    assertEquals(0f, ink.value(90_000_000), 0.001f)
    for (ms in 0..233) {
      val p = 1f - ms / 233f
      assertEquals(0f, fluidMenuFaceAlpha(fluidMenuReturnFaceProgress(p)) * ink.value(ms * 1_000_000L), 0f)
    }
  }

  @Test
  fun openingOvershootGrowsTheFarEdgeAndLeavesTheNearEdgeInPlace() {
    val from = Rect(42f, 510f, 86f, 554f)
    val end = FluidForm.Slab(panel, FluidCornerRadii.all(24f))
    val peak = fluidMenuOpeningOvershoot(end, panel, from, FluidMotion.MenuOvershootPeakMillis.toFloat(), 0.1f).frame
    assertEquals(panel.bottom, peak.bottom, 0f)
    assertEquals(panel.height * 0.06f, panel.top - peak.top, 0.001f)
    assertEquals(panel.width * 0.034f, peak.width - panel.width, 0.001f)
    assertEquals(end, fluidMenuOpeningOvershoot(end, panel, from, 350f, 0.1f))
  }

  @Test
  fun theGroupGatherAndDestinationAreIndependentOfThePanelSize() {
    val trigger = Offset(rest.right - 22f, rest.center.y)
    val gather = fluidMenuJourneySeed(rest, trigger, panel.center, FluidMotion.MenuGatherEnd, grouped = true)
    assertEquals(rest.center, gather.center)
    assertEquals(rest.width * 0.60f, gather.width, 0.001f)
    val mid = fluidMenuJourneySeed(rest, trigger, panel.center, FluidMotion.MenuExpandStart, grouped = true)
    assertTrue(mid.center.y in panel.center.y..rest.center.y)
    assertEquals(0f, fluidMenuExpansionProgress(FluidMotion.MenuExpandStart), 0f)
    val destination = Offset(152f, 373f)
    val final = fluidMenuJourneySeed(rest, trigger, destination, FluidMotion.MenuTravelEnd, grouped = true)
    assertEquals(destination, final.center)
    assertEquals(final.height, final.width, 0.001f)
    assertEquals(1.04f, fluidMenuExpansionProgress(1.04f), 0f)
  }

  @Test
  fun theLensBeginsWithInkAndFocusesWithoutMovingTheRestingRows() {
    val clip = Rect(115f, 530f, 150f, 565f)
    val map = fluidMenuLensMap(panel, clip)
    assertTrue(fluidMenuLensAlpha(0f) > 0f && map.scale < 0.25f)
    assertEquals(clip.center - panel.center, map.translation)
    assertTrue(fluidMenuLensBlurDp(190f / 270f) < 0.5f)
    assertEquals(0f, fluidMenuLensBlurDp(1f), 0f)
    assertEquals(0f, fluidMenuLensStrength(1f), 0f)
    assertEquals(FluidMenuContentMap.Identity, fluidMenuLensMap(panel, panel))
    assertTrue(fluidMenuSpotAmount(0.35f) > 0f)
    assertEquals(0f, fluidMenuSpotAmount(0.8f), 0f)
  }

  @Test
  fun theButtonGathersBeforeGrowingAndReachesThePanelWithRoundCorners() {
    fun at(p: Float) = fluidMenuButtonTravel(rest, panel, 22f, panel.center, p)
    assertEquals(rest, at(0f).frame)
    val gathered = at(FluidMotion.MenuButtonGatherEnd).frame
    assertEquals(rest.center, gathered.center)
    assertTrue(gathered.width < rest.width)
    val bubble = at(FluidMotion.MenuButtonBubble)
    assertTrue(bubble.frame.width > panel.width * 0.74f)
    assertTrue(bubble.frame.center.y < rest.center.y)
    assertEquals(bubble.frame.minDimension / 2f, bubble.cornerRadii.topLeft, 0.001f)
    assertEquals(panel, at(1f).frame)
    assertEquals(22f, at(1f).cornerRadii.topLeft, 0.001f)
  }

  @Test
  fun onlyTheButtonMagnifiesItsInkWhileTheLensIsActive() {
    val clip = Rect(80f, 410f, 190f, 540f)
    val button = fluidMenuLensMap(panel, clip, button = true, progress = 0.5f, focus = 0.3f)
    val group = fluidMenuLensMap(panel, clip, progress = 0.5f, focus = 0.3f)
    assertEquals(1.42f, button.scale, 0.001f)
    assertTrue(group.scale < 1f)
    assertEquals(group, fluidMenuLensMap(panel, clip, button = true, progress = 0.9f, focus = 0.7f))
    assertEquals(FluidMenuContentMap.Identity, fluidMenuLensMap(panel, panel, button = true, progress = 1f, focus = 1f))
  }

  @Test
  fun theInkResumesAtItsCurrentVisibilityWhenTheReturnIsInterrupted() = runTest {
    val bench = JourneyBench(this)
    val journey = FluidMenuJourney().apply {
      prepare(anchor(), Offset.Zero, panel, radius = 22f, inflation = FluidMenuInflation.None, liquidMenu = true)
      retarget(1f)
    }
    val opening = bench.scope.launch { journey.run(reducedMotion = false) }
    bench.frames(8) { journey.progress.value }
    journey.retarget(0f)
    opening.cancel()
    val returning = bench.scope.launch { journey.run(reducedMotion = false) }
    val halfway = bench.frames(5) { journey.contentVisibility.value }.last()
    assertTrue("l'inchiostro sta sparendo: $halfway", halfway > 0f && halfway < 1f)
    journey.retarget(1f)
    returning.cancel()
    bench.scope.launch { journey.run(reducedMotion = false) }
    assertEquals(halfway, journey.contentVisibility.value, 0f)
    val resumed = bench.frames(5) { journey.contentVisibility.value }
    assertTrue("l'inchiostro riprende da lì: $resumed", resumed.first() >= halfway && resumed.last() > halfway)
    bench.close()
  }

  @Test
  fun theOpeningOvershootsThePanelAndSettlesOnIt() = runTest {
    val bench = JourneyBench(this)
    val journey = opened()
    bench.scope.launch { journey.run(reducedMotion = false) }

    val values = bench.frames(90) { journey.progress.value }

    assertTrue("la molla del menu oltrepassa: ${values.max()}", values.max() > 1.03f)
    assertTrue("ma di quanto dice il token, non di piu': ${values.max()}", values.max() < 1f + FluidMotion.MenuPeakOvershoot + 0.03f)
    assertEquals(1f, values.last(), 0.002f)
    assertFalse(journey.closing)
    bench.close()
  }

  @Test
  fun aBackAtAHundredMillisReversesFromWhereItIsWithItsVelocity() = runTest {
    val bench = JourneyBench(this)
    val journey = opened()
    val opening = bench.scope.launch { journey.run(reducedMotion = false) }
    bench.frames(6) { journey.progress.value }
    val atBack = journey.progress.value
    assertTrue("a 100 ms il menu e' a meta' strada: $atBack", atBack > 0.1f && atBack < 1f)

    // Quello che fa l'host: il verso cambia nel momento in cui si decide, poi l'effetto vecchio viene
    // cancellato e il nuovo corre.
    journey.retarget(0f)
    opening.cancel()
    bench.scope.launch { journey.run(reducedMotion = false) }
    val after = bench.frames(70) { journey.progress.value }

    assertTrue(journey.closing)
    // Riparte da dove si trova: nessun salto al pannello pieno, nessun salto all'ancora.
    assertTrue("riparte da $atBack, non da ${after.first()}", abs(after.first() - atBack) < 0.15f)
    // E con lo slancio dell'apertura: il primo fotogramma prosegue ancora un poco in su, invece di
    // tornare indietro da fermo.
    assertTrue("eredita la velocita': ${after.first()} dopo $atBack", after.first() >= atBack)
    // Poi rientra, oltrepassa l'ancora di un soffio — la goccia (3.0): il ritorno ha la sua molla, poco
    // sottosmorzata, e sotto lo zero la sagoma si sgonfia attorno al tasto — e si posa.
    assertTrue(after.all { it >= -0.05f })
    assertEquals(0f, after.last(), 0.002f)
    assertTrue(journey.settledClosed)
    bench.close()
  }

  @Test
  fun theStartIsThePressedButtonAndRelaxesToItsRestWhileTravelling() = runTest {
    val bench = JourneyBench(this)
    val journey = opened()
    assertEquals(pressed, journey.startFrame())

    bench.scope.launch { journey.run(reducedMotion = false) }
    bench.frames(60) { journey.progress.value }

    assertEquals(rest, journey.startFrame())
    bench.close()
  }

  @Test
  fun theStartIsInHostCoordinates() {
    val journey = FluidMenuJourney()
    journey.prepare(anchor(), hostOrigin = Offset(0f, 80f), panel = panel, radius = 22f, inflation = FluidMenuInflation.None)
    assertEquals(pressed.translate(0f, -80f), journey.startFrame())
  }

  @Test
  fun theWindowGoesFromThePressedButtonToThePanel() = runTest {
    val physics = FluidPhysicsState(FluidForm.circle(Offset(1f, 1f), 1f))
    val journey = opened()
    journey.install(physics)

    val start = physics.ensurePlan().bounds
    assertEquals(pressed.left, start.left, 0.01f)
    assertEquals(pressed.bottom, start.bottom, 0.01f)

    journey.progress.snapTo(1f)
    val end = physics.ensurePlan().bounds
    assertEquals(panel.left, end.left, 0.01f)
    assertEquals(panel.top, end.top, 0.01f)
    assertEquals(panel.right, end.right, 0.01f)
    assertEquals(panel.bottom, end.bottom, 0.01f)
  }

  @Test
  fun theOvershootIsAnInflationAroundThePanelCenter() = runTest {
    val physics = FluidPhysicsState(FluidForm.circle(Offset(1f, 1f), 1f))
    val journey = opened()
    journey.install(physics)

    journey.progress.snapTo(1.1f)
    val over = physics.ensurePlan().bounds
    // Gonfiato attorno al centro: il centro resta quello del pannello, i lati crescono tutti e quattro.
    assertEquals(panel.center.x, over.center.x, 0.01f)
    assertEquals(panel.center.y, over.center.y, 0.01f)
    assertTrue(over.width > panel.width && over.height > panel.height)
  }

  @Test
  fun aContextMenuGrowsFromItsSeed() = runTest {
    val physics = FluidPhysicsState(FluidForm.circle(Offset(1f, 1f), 1f))
    val seed = fluidMenuSeedFrame(panel, Offset(panel.center.x, panel.top), FluidMenuSeedScale)
    val journey = FluidMenuJourney()
    journey.prepare(null, Offset.Zero, panel, radius = 22f, inflation = FluidMenuInflation.None, seed = seed)
    journey.install(physics)

    assertEquals(seed, journey.startFrame())
    val start = physics.ensurePlan().bounds
    assertEquals(seed.width, start.width, 0.01f)
    assertEquals(seed.top, start.top, 0.01f)
  }

  @Test
  fun aContextMenuIsBornFromADropHangingFromTheRowUnderTheFinger() {
    // Il vetro liquido (3.0): il menu contestuale esce dalla riga, da una goccia sotto il dito.
    val row = Rect(0f, 100f, 400f, 164f)
    val below = Rect(20f, 176f, 272f, 400f)
    val drop = fluidMenuDropSeed(row, below, touchX = 120f, diameterPx = 28f, overlapPx = 4f)
    assertEquals(28f, drop.width, 0.001f)
    assertEquals(28f, drop.height, 0.001f)
    assertEquals(120f, drop.center.x, 0.001f)
    // Appesa alla riga, ci entra di 4: esce DALLA riga, non le compare accanto.
    assertEquals(row.bottom - 4f, drop.top, 0.001f)

    // Un dito oltre il pannello: la goccia nasce sotto il dito (S4b, NO 7: a x = 130 compariva spostata verso il centro),
    // e resta dentro la riga da cui esce.
    val beyond = fluidMenuDropSeed(row, below, touchX = 330f, diameterPx = 28f, overlapPx = 4f)
    assertEquals(330f, beyond.center.x, 0.001f)
    val atTheEdge = fluidMenuDropSeed(row, below, touchX = 395f, diameterPx = 28f, overlapPx = 4f)
    assertEquals(row.right - 14f, atTheEdge.center.x, 0.001f)
    val left = fluidMenuDropSeed(row, below, touchX = 2f, diameterPx = 28f, overlapPx = 4f)
    assertEquals(row.left + 14f, left.center.x, 0.001f)

    // Un pannello sopra la riga: la goccia sta sul bordo alto, e senza dito sotto il centro della riga.
    val above = fluidMenuDropSeed(row, Rect(20f, 0f, 272f, 90f), touchX = null, diameterPx = 28f, overlapPx = 4f)
    assertEquals(row.top + 4f, above.bottom, 0.001f)
    assertEquals(200f, above.center.x, 0.001f)
  }

  @Test
  fun reducedMotionArrivesWithoutTravelling() = runTest {
    val journey = opened()
    journey.run(reducedMotion = true)
    assertEquals(1f, journey.progress.value, 0f)
    assertEquals(rest, journey.startFrame())
  }

  @Test
  fun reopeningWhileClosingKeepsTheStartItHad() {
    val a = anchor()
    val journey = FluidMenuJourney()
    journey.prepare(a, Offset.Zero, panel, 22f, FluidMenuInflation.None, freshStart = true)
    val relax = journey.anchorRelax
    journey.prepare(a, Offset.Zero, panel, 22f, FluidMenuInflation.None, freshStart = false)
    assertSame("una riapertura non riparte dal tasto premuto", relax, journey.anchorRelax)
    journey.prepare(a, Offset.Zero, panel, 22f, FluidMenuInflation.None, freshStart = true)
    assertNotSame(relax, journey.anchorRelax)
  }

  @Test
  fun aButtonOpenedWithoutPressStartsAtRest() {
    val a = FluidMenuAnchor().apply {
      restBounds = rest
      pressedBounds = rest
    }
    val journey = FluidMenuJourney()
    journey.prepare(a, Offset.Zero, panel, 22f, FluidMenuInflation.None)
    assertEquals(rest, journey.startFrame())
  }

  @Test
  fun retargetingToTheSameDirectionIsFree() {
    val journey = opened()
    assertEquals(1f, journey.target, 0f)
    journey.retarget(1f)
    assertEquals(1f, journey.target, 0f)
    journey.retarget(0f)
    assertTrue(journey.closing)
  }

  // --- La nascita di Apple (sessione contestuale, fase tasto) ---------------------------------------------------------

  private fun born(anchor: FluidMenuAnchor = anchor()): FluidMenuJourney = FluidMenuJourney().apply {
    prepare(anchor, Offset.Zero, panel, radius = 22f, inflation = FluidMenuInflation.None, liquidMenu = true, appleBirth = true)
    retarget(1f)
  }

  @Test
  fun theButtonMenuIsBornOnApplesClockAndLeavesWithApplesInk() = runTest {
    val bench = JourneyBench(this)
    val journey = born()
    assertTrue(journey.born)
    bench.scope.launch { journey.run(reducedMotion = false) }
    // B1 (a): quasi pieno a ~100 ms, supera del 10% di corsa a 167 ms, fermo e 1 esatto alla fine (400 ms).
    val progress = bench.frames(30) { journey.progress.value }
    assertTrue("a ~96 ms: ${progress[6]}", progress[6] > 0.9f)
    assertTrue("il superamento: ${progress.max()}", progress.max() > 1.05f)
    assertEquals(1f, progress.last(), 0f)
    assertEquals(1f, journey.focus.value, 0f)
    assertFalse(journey.progress.isRunning)

    // Il congedo a menu fermo: i 233 ms del ritorno, l'inchiostro pieno fino a ~67 ms e via entro ~167.
    journey.retarget(0f)
    assertTrue(journey.controlledReturn)
    bench.scope.launch { journey.run(reducedMotion = false) }
    val ink = bench.frames(12) { journey.contentVisibility.value }
    assertTrue("a 48 ms: ${ink[3]}", ink[3] > 0.99f)
    // Le voci che se ne vanno restano piene finche' c'e' l'inchiostro (la messa a fuoco che torna non le spegne).
    assertTrue(journey.bornReturn)
    assertEquals(journey.contentVisibility.value, journey.lensInkAlpha(), 0f)
    assertTrue("a 128 ms: ${ink[8]}", ink[8] in 0.01f..0.9f)
    assertEquals(0f, ink[11], 0f)
    bench.frames(4) { journey.progress.value }
    assertEquals(0f, journey.progress.value, 0f)
    assertTrue(journey.settledClosed)
    bench.close()
  }

  @Test
  fun theGroupMenuIsBornFromTheCapsuleLikeThePaperclip() = runTest {
    // La graffetta: il tasto premuto a sinistra della capsula, sul margine del pannello.
    val capsule = Rect(40f, 600f, 172f, 644f)
    val trigger = Rect(40f, 600f, 84f, 644f)
    val a = FluidMenuAnchor().apply {
      restBounds = trigger
      pressedBounds = trigger
      groupBounds = capsule
    }
    val journey = born(a)
    val physics = FluidPhysicsState(FluidForm.circle(Offset.Zero, 1f))
    journey.install(physics)
    assertEquals(FluidMotion.DropAnchorReachDp, physics.dropAnchorReachDp, 0f)
    assertEquals(capsule, requireNotNull(physics.dropRestSlabNow()).frame)
    // Le icone del gruppo: sfocate e via prima di 67 ms. Le voci: niente prima di 67 ms.
    journey.birthClock.snapTo(55f)
    assertTrue(journey.faceProgress / FluidMotion.MenuGroupFaceProgressSpan > 0.66f)
    assertEquals(0f, journey.birthInk(), 0f)
    // A 67 ms la goccia sul tasto: il lato del tasto fermo, stretta come una goccia.
    journey.birthClock.snapTo(FluidGroupGatherMillis)
    val drop = requireNotNull(physics.dropRestSlabNow()).frame
    assertEquals(trigger.left, drop.left, 0.5f)
    assertTrue("larga ${drop.width}", drop.width < capsule.width * 0.6f)
    // A 167 ms l'uovo con la coda sul tasto.
    journey.birthClock.snapTo(166.8f)
    val warp = requireNotNull(physics.dropWarpNow())
    assertTrue(warp.tailTaper > 0.3f)
    assertEquals(trigger.center.x, warp.tailX, 0.5f)
  }

  /**
   * S4b sul Tab S9 (NO 4): al ritorno del gruppo la capsula vuota per 2-3 fotogrammi prima delle icone. Le icone ora le
   * disegna l'host sopra il vetro mentre torna ([FluidMenuAnchor.groupFacesAbove]), spente fino all'ultimo tratto e piene
   * alla consegna; all'andata restano della barra, sotto il vetro che nasce.
   */
  @Test
  fun theGroupIconsComeBackWithTheCapsuleAboveTheGlass() = runTest {
    val capsule = Rect(40f, 600f, 172f, 644f)
    val trigger = Rect(40f, 600f, 84f, 644f)
    val a = FluidMenuAnchor().apply {
      restBounds = trigger
      pressedBounds = trigger
      groupBounds = capsule
    }
    val journey = born(a)
    assertFalse("all'andata le icone sono della barra", a.groupFacesAbove())
    journey.progress.snapTo(1f)
    journey.retarget(0f)
    assertTrue("al ritorno le disegna l'host sopra il vetro", a.groupFacesAbove())
    val span = FluidMotion.MenuGroupFaceProgressSpan
    for (p in listOf(1f, 0.8f, 0.5f, FluidMenuGroupFaceReturnShare + 0.01f)) {
      journey.progress.snapTo(p)
      assertTrue("spente a $p mentre il vetro torna", a.groupProgress() >= span)
    }
    journey.progress.snapTo(FluidMenuGroupFaceReturnShare / 2f)
    assertTrue("a meta' dell'ultimo tratto si vedono", a.groupProgress() in 0.3f..0.7f)
    journey.progress.snapTo(0f)
    assertEquals("piene alla consegna", 0f, a.groupProgress(), 0f)
    // La consegna scioglie tutto: la barra riprende icone e capsula.
    a.consume(false)
    assertFalse(a.groupFacesAbove())
    assertNull(a.groupFaces)
    assertEquals(0f, a.groupProgress(), 0f)
  }

  @Test
  fun aBornMenuClosedHalfwayGoesBackFromTheShapeItHad() = runTest {
    val journey = born()
    val physics = FluidPhysicsState(FluidForm.circle(Offset.Zero, 1f))
    journey.install(physics)
    // La goccia del tasto singolo e' piu' tesa (B1 a); senza la nascita torna quella di serie.
    assertEquals(FluidButtonBirthProfile.DropReachDp, physics.dropAnchorReachDp, 0f)
    journey.birthClock.snapTo(80f)
    journey.progress.snapTo(fluidButtonBirthProgress(80f))
    assertEquals(fluidButtonBirthSlab(journey.startFrame(), panel, 22f, 80f), physics.dropRestSlabNow())
    journey.retarget(0f)
    assertFalse(journey.controlledReturn)
    // Il ritorno parte dalla sagoma che si vede (quella di Apple a 80 ms), non da quella chiesta in anticipo alla goccia.
    assertEquals(fluidButtonBirthSlab(journey.startFrame(), panel, 22f, 80f, leadForDrop = false), physics.dropRestSlabNow())
  }

  @Test
  fun theInkLeavingABornButtonMenuGrowsInTheLensLikeB1a() {
    // B1 (a): a 33 ms le voci sono alla loro misura, a ~100 ms ingrandite (1,35×), fino a 1,42× a ~117 ms.
    val clip = Rect(80f, 410f, 190f, 540f)
    fun at(ms: Float) = fluidMenuLensMap(panel, clip, button = true, progress = 1f - ms / 233f, focus = 0.1f, returning = true)
    val fitted = fluidMenuLensMap(panel, clip, progress = 0.5f, focus = 1f).scale
    assertEquals(fitted, at(33f).scale, 0.01f)
    assertTrue(at(67f).scale > fitted && at(67f).scale < 1.42f)
    assertEquals(1.42f, at(117f).scale, 0.01f)
    // Senza la nascita il ritorno non ingrandisce: le voci seguono la goccia che si stringe.
    assertEquals(fitted, fluidMenuLensMap(panel, clip, button = false, progress = 0.5f, focus = 0.1f, returning = true).scale, 0f)
  }

  @Test
  fun withReducedMotionABornMenuIsThePanelAtOnce() = runTest {
    val journey = born()
    val physics = FluidPhysicsState(FluidForm.circle(Offset.Zero, 1f))
    journey.install(physics)
    journey.run(reducedMotion = true)
    assertEquals(panel, requireNotNull(physics.dropRestSlabNow()).frame)
  }

  @Test
  fun withoutTheBirthTheLiquidMenuTravelsAsBefore() {
    val journey = FluidMenuJourney().apply {
      prepare(anchor(), Offset.Zero, panel, radius = 22f, inflation = FluidMenuInflation.None, liquidMenu = true)
      retarget(1f)
    }
    assertFalse(journey.born)
    assertEquals(1f, journey.birthInk(), 0f)
  }
}
