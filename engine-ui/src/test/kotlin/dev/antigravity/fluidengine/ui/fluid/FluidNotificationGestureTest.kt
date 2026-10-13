package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le decisioni del dito sulla card, provate dove si possono provare senza un dito: a numeri, in dp e
 * in dp/s, e con un orologio finto per il tempo di lettura. Il gesto vero, dentro un albero Compose,
 * lo prova `FluidNotificationDragTest` nel sample.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FluidNotificationGestureTest {

  // --- Il rilascio ---------------------------------------------------------------------------

  @Test
  fun release_pastTheExpandDistanceOpensTheCard() {
    assertEquals(FluidNotificationRelease.Expand, fluidNotificationRelease(72f, 0f))
    assertEquals(FluidNotificationRelease.Expand, fluidNotificationRelease(130f, 200f))
    assertEquals(FluidNotificationRelease.SpringBack, fluidNotificationRelease(71.9f, 0f))
  }

  @Test
  fun release_aDownwardFlingOpensEvenFromAShortPull() {
    assertEquals(FluidNotificationRelease.Expand, fluidNotificationRelease(15f, 1_000f))
    assertEquals(FluidNotificationRelease.Expand, fluidNotificationRelease(8f, 2_400f))
    assertEquals(FluidNotificationRelease.SpringBack, fluidNotificationRelease(15f, 999f))
  }

  @Test
  fun release_pastTheDismissDistanceThrowsTheCardAway() {
    assertEquals(FluidNotificationRelease.Dismiss, fluidNotificationRelease(-40f, 0f))
    assertEquals(FluidNotificationRelease.Dismiss, fluidNotificationRelease(-90f, -200f))
    assertEquals(FluidNotificationRelease.SpringBack, fluidNotificationRelease(-39.9f, 0f))
  }

  @Test
  fun release_anUpwardFlingThrowsAwayEvenFromAShortPush() {
    assertEquals(FluidNotificationRelease.Dismiss, fluidNotificationRelease(-10f, -1_000f))
    assertEquals(FluidNotificationRelease.Dismiss, fluidNotificationRelease(0f, -3_000f))
    assertEquals(FluidNotificationRelease.SpringBack, fluidNotificationRelease(-10f, -999f))
  }

  @Test
  fun release_aQuietReleaseInTheMiddleSpringsBack() {
    assertEquals(FluidNotificationRelease.SpringBack, fluidNotificationRelease(0f, 0f))
    assertEquals(FluidNotificationRelease.SpringBack, fluidNotificationRelease(30f, 300f))
    assertEquals(FluidNotificationRelease.SpringBack, fluidNotificationRelease(-25f, -300f))
  }

  @Test
  fun release_changingYourMindAfterPassingAThresholdCancels() {
    // Tirata fino a farla aprire e poi ripresa di scatto verso l'alto: non e' un "butta via".
    assertEquals(FluidNotificationRelease.SpringBack, fluidNotificationRelease(90f, -1_500f))
    // E all'inverso: spinta oltre la soglia di scarto e poi ritirata giu' di scatto non apre niente.
    assertEquals(FluidNotificationRelease.SpringBack, fluidNotificationRelease(-60f, 1_500f))
  }

  @Test
  fun release_anOpenCardDoesNotOpenAgainButStillGoesAway() {
    assertEquals(FluidNotificationRelease.SpringBack, fluidNotificationRelease(100f, 0f, expanded = true))
    assertEquals(FluidNotificationRelease.SpringBack, fluidNotificationRelease(5f, 2_000f, expanded = true))
    assertEquals(FluidNotificationRelease.Dismiss, fluidNotificationRelease(-45f, 0f, expanded = true))
    assertEquals(FluidNotificationRelease.Dismiss, fluidNotificationRelease(0f, -1_200f, expanded = true))
  }

  // --- Dove sta la card ----------------------------------------------------------------------

  @Test
  fun pullDown_isResistedAndUpIsOneToOne() {
    val dimension = NotificationPullDimensionDp * 3f
    assertEquals(-120f, fluidNotificationOffsetPx(-120f, dimension), 0f)
    assertEquals(0f, fluidNotificationOffsetPx(0f, dimension), 0f)

    var previous = 0f
    for (raw in listOf(10f, 40f, 90f, 216f, 400f, 1_000f)) {
      val visual = fluidNotificationOffsetPx(raw, dimension)
      assertTrue("a $raw la card sta a $visual: non resiste", visual < raw)
      assertTrue("la curva non cresce a $raw", visual > previous)
      assertTrue("oltre l'asintoto a $raw", visual < dimension)
      previous = visual
    }
  }

  @Test
  fun pullDown_atTheThresholdTheCardHasMovedAboutAThirdOfTheFinger() {
    val density = 3f
    val visual = fluidNotificationOffsetPx(
      NotificationExpandDistanceDp * density,
      NotificationPullDimensionDp * density,
    ) / density
    assertTrue("la card ha fatto $visual dp: troppo poco per vedere il gesto", visual > 25f)
    assertTrue("la card ha fatto $visual dp: non resiste abbastanza", visual < 40f)
  }

  @Test
  fun grabbingACardInFlightResumesWhereTheFingerWouldBe() {
    val dimension = NotificationPullDimensionDp * 2f
    for (raw in listOf(-80f, -1f, 0f, 12f, 150f, 600f)) {
      val visual = fluidNotificationOffsetPx(raw, dimension)
      assertEquals(raw, fluidNotificationRawPx(visual, dimension), 0.01f * (1f + kotlin.math.abs(raw)))
    }
  }

  @Test
  fun follow_isTheSlopeOfTheCurve() {
    val dimension = NotificationPullDimensionDp * 3f
    for (raw in listOf(0f, 30f, 200f, 500f)) {
      val h = 0.01f
      val numeric = (fluidNotificationOffsetPx(raw + h, dimension) - fluidNotificationOffsetPx(raw, dimension)) / h
      assertEquals(numeric, fluidNotificationFollow(raw, dimension), 0.005f)
    }
    assertEquals(1f, fluidNotificationFollow(-30f, dimension), 0f)
    assertEquals(OverscrollResistance, fluidNotificationFollow(0f, dimension), 0.001f)
  }

  @Test
  fun stretch_growsWithThePullStaysSmallAndVanishesWithReducedMotion() {
    val density = 3f
    assertEquals(0f, fluidNotificationStretchPx(0f, density, reducedMotion = false), 0f)
    assertEquals(0f, fluidNotificationStretchPx(-60f, density, reducedMotion = false), 0f)
    assertEquals(0f, fluidNotificationStretchPx(100f, density, reducedMotion = true), 0f)
    assertEquals(
      30f * NotificationStretchFraction,
      fluidNotificationStretchPx(30f, density, reducedMotion = false),
      0.001f,
    )
    // Il tetto: una card tirata a fondo non diventa un'altra card.
    assertEquals(
      NotificationStretchMaxDp * density,
      fluidNotificationStretchPx(10_000f, density, reducedMotion = false),
      0.001f,
    )
  }

  // --- Il tick sulla soglia ------------------------------------------------------------------

  @Test
  fun armed_crossesOnceAndDoesNotChatterOnTheThreshold() {
    var armed = false
    var ticks = 0
    // Un dito che sale oltre la soglia e vi trema sopra e sotto di un paio di dp.
    for (travel in listOf(60f, 71f, 72f, 73f, 71f, 70f, 73f, 72f, 71f, 74f)) {
      val next = fluidNotificationArmed(armed, travel)
      if (next != armed) ticks++
      armed = next
    }
    assertEquals("un solo tick per attraversamento", 1, ticks)
    assertTrue(armed)

    // Scende davvero sotto: si disarma, e un nuovo attraversamento e' un altro tick.
    assertFalse(fluidNotificationArmed(true, 60f))
    assertTrue(fluidNotificationArmed(false, 72f))
    assertFalse(fluidNotificationArmed(false, 71.9f))
  }

  // --- Il tempo di lettura -------------------------------------------------------------------

  @Test
  fun resume_neverLeavesLessThanOneAndAHalfSeconds() {
    assertEquals(1_500L, fluidNotificationResumeMillis(0L))
    assertEquals(1_500L, fluidNotificationResumeMillis(200L))
    assertEquals(1_500L, fluidNotificationResumeMillis(1_500L))
    assertEquals(4_800L, fluidNotificationResumeMillis(4_800L))
    assertEquals(1_500L, fluidNotificationResumeMillis(-30L))
  }

  private fun kotlinx.coroutines.test.TestScope.start(
    timeoutMillis: Long,
    id: String = "n",
    standard: Boolean = false,
  ): FluidNotificationHostState {
    val state = FluidNotificationHostState()
    state.setHostActive(true)
    val dispatcher = if (standard) StandardTestDispatcher(testScheduler) else UnconfinedTestDispatcher(testScheduler)
    backgroundScope.launch(dispatcher) {
      state.runQueue(
        recommendedTimeout = { timeoutMillis },
        monotonicTimeMillis = { testScheduler.currentTime },
      )
    }
    backgroundScope.async(dispatcher) {
      state.show(FluidNotification(id = id, title = "Titolo", message = "Messaggio"))
    }
    runCurrent()
    state.markPresented(id)
    runCurrent()
    return state
  }

  @Test
  fun aFingerOnTheCard_stopsTheCountdown() = runTest {
    val state = start(timeoutMillis = 2_000L)

    advanceTimeBy(500L)
    runCurrent()
    state.setTouched("n", true)
    runCurrent()

    advanceTimeBy(60_000L)
    runCurrent()
    assertEquals("la card e' sparita sotto il dito", "n", state.currentNotification?.id)
  }

  @Test
  fun letGo_resumesWithWhatWasLeftButNeverLessThanTheFloor() = runTest {
    val state = start(timeoutMillis = 2_000L)

    // Duecento millisecondi alla fine: il dito arriva all'ultimo istante.
    advanceTimeBy(1_800L)
    runCurrent()
    state.setTouched("n", true)
    runCurrent()
    advanceTimeBy(5_000L)
    runCurrent()
    state.setTouched("n", false)
    runCurrent()

    advanceTimeBy(1_499L)
    runCurrent()
    assertEquals("la card e' sparita sotto il dito che la molla", "n", state.currentNotification?.id)
    advanceTimeBy(1L)
    runCurrent()
    assertNull(state.currentNotification)
  }

  @Test
  fun letGo_keepsWhatWasLeftWhenItIsMoreThanTheFloor() = runTest {
    val state = start(timeoutMillis = 6_000L)

    advanceTimeBy(1_000L)
    runCurrent()
    state.setTouched("n", true)
    runCurrent()
    advanceTimeBy(3_000L)
    runCurrent()
    state.setTouched("n", false)
    runCurrent()

    advanceTimeBy(4_999L)
    runCurrent()
    assertEquals("n", state.currentNotification?.id)
    advanceTimeBy(1L)
    runCurrent()
    assertNull(state.currentNotification)
  }

  @Test
  fun aTouchShorterThanATurnOfTheTimerStillGetsItsFloor() = runTest {
    // Con un dispatcher che non riprende subito, giu' e su arrivano prima che il timer li veda: il
    // minimo concesso al rilascio non puo' dipendere dall'averli visti uno per uno.
    val state = start(timeoutMillis = 2_000L, standard = true)

    advanceTimeBy(1_800L)
    state.setTouched("n", true)
    state.setTouched("n", false)
    runCurrent()

    advanceTimeBy(1_499L)
    runCurrent()
    assertEquals("n", state.currentNotification?.id)
    advanceTimeBy(1L)
    runCurrent()
    assertNull(state.currentNotification)
  }

  @Test
  fun anOpenCard_neverTimesOutButStillGoesWhenDismissed() = runTest {
    val state = start(timeoutMillis = 2_000L)

    advanceTimeBy(500L)
    runCurrent()
    state.setExpanded("n", true)
    runCurrent()
    advanceTimeBy(120_000L)
    runCurrent()
    assertEquals("una card aperta e' scaduta", "n", state.currentNotification?.id)

    // Toccarla e mollarla non rimette in moto un conto che non c'e'.
    state.setTouched("n", true)
    runCurrent()
    state.setTouched("n", false)
    runCurrent()
    advanceTimeBy(120_000L)
    runCurrent()
    assertEquals("n", state.currentNotification?.id)

    state.dismissCurrent()
    runCurrent()
    assertNull(state.currentNotification)
  }

  @Test
  fun aDismissalArrivesEvenWhileAFingerIsDown() = runTest {
    val state = start(timeoutMillis = 6_000L)

    state.setTouched("n", true)
    runCurrent()
    state.dismissCurrent()
    runCurrent()
    assertNull(state.currentNotification)
  }

  @Test
  fun aFingerOnAnotherNotification_isNotThisOnesBusiness() = runTest {
    val state = start(timeoutMillis = 2_000L)

    state.setTouched("altra", true)
    state.setExpanded("altra", true)
    runCurrent()
    advanceTimeBy(2_000L)
    runCurrent()
    assertNull(state.currentNotification)
  }

  @Test
  fun theNextNotificationStartsFreeAndClosed() = runTest {
    val state = FluidNotificationHostState()
    state.setHostActive(true)
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      state.runQueue(
        recommendedTimeout = { 2_000L },
        monotonicTimeMillis = { testScheduler.currentTime },
      )
    }
    backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
      state.show(FluidNotification(id = "uno", title = "Titolo", message = "Primo"))
    }
    backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
      state.show(FluidNotification(id = "due", title = "Titolo", message = "Secondo"))
    }
    runCurrent()
    state.markPresented("uno")
    runCurrent()

    // La prima viene aperta e poi chiusa con il dito ancora sopra.
    state.setExpanded("uno", true)
    state.setTouched("uno", true)
    runCurrent()
    state.dismissCurrent()
    runCurrent()
    state.markExitCompleted("uno")
    runCurrent()
    assertEquals("due", state.currentNotification?.id)

    // La seconda non eredita ne' il dito ne' l'apertura: scade nel suo tempo.
    state.markPresented("due")
    runCurrent()
    advanceTimeBy(2_000L)
    runCurrent()
    assertNull(state.currentNotification)
  }

  // --- La molla del ritorno ------------------------------------------------------------------

  /**
   * Il picco di oltrepasso, in dp, di una card che torna a riposo da [startDp] con [velocityDpPerSecond]
   * (della card, non del dito): quanto va oltre lo zero dalla parte opposta a quella da cui veniva.
   */
  private fun overshootDp(startDp: Float, velocityDpPerSecond: Float): Float {
    val animation = TargetBasedAnimation(
      animationSpec = notificationSpringBack(),
      typeConverter = Float.VectorConverter,
      initialValue = startDp,
      targetValue = 0f,
      initialVelocity = velocityDpPerSecond,
    )
    var worst = 0f
    val end = animation.durationNanos
    var t = 0L
    while (t <= end) {
      val value = animation.getValueFromNanos(t)
      // Oltre il riposo dalla parte opposta alla partenza.
      val beyond = if (startDp >= 0f) -value else value
      if (beyond > worst) worst = beyond
      t += 1_000_000L
    }
    return worst
  }

  @Test
  fun springBack_neverVisiblyOvershootsTheRest() {
    var worstDp = 0f
    var worstCase = ""
    for (travel in listOf(-39f, -25f, -10f, 5f, 20f, 40f, 71f)) {
      for (velocity in listOf(-999f, -600f, -200f, 0f, 200f, 600f, 999f)) {
        if (fluidNotificationRelease(travel, velocity) != FluidNotificationRelease.SpringBack) continue
        // La card segue il dito scontata dalla resistenza in giu': la sua velocita' e' minore.
        val dimension = NotificationPullDimensionDp
        val cardStart = fluidNotificationOffsetPx(travel, dimension)
        val cardVelocity = fluidNotificationReturnVelocity(
          cardStart,
          velocity * fluidNotificationFollow(travel, dimension),
        )
        val overshoot = overshootDp(cardStart, cardVelocity)
        if (overshoot > worstDp) {
          worstDp = overshoot
          worstCase = "da $travel dp a $velocity dp/s"
        }
      }
    }
    assertTrue("oltrepasso di $worstDp dp ($worstCase): la card rimbalza", worstDp < 0.75f)
  }

  @Test
  fun springBack_afterAnOpeningNeverVisiblyOvershootsEither() {
    var worstDp = 0f
    for (travel in listOf(72f, 90f, 140f, 260f)) {
      for (velocity in listOf(-900f, 0f, 500f, 1_500f, 3_000f)) {
        if (fluidNotificationRelease(travel, velocity) != FluidNotificationRelease.Expand) continue
        val density = 1f
        val raw = travel * density
        val dimension = NotificationPullDimensionDp * density
        val cardStart = fluidNotificationOffsetPx(raw, dimension)
        val cardVelocity = fluidNotificationReturnVelocity(
          cardStart,
          velocity * fluidNotificationFollow(raw, dimension),
        )
        worstDp = maxOf(worstDp, overshootDp(cardStart, cardVelocity))
      }
    }
    assertTrue("oltrepasso di $worstDp dp dopo l'apertura", worstDp < 0.75f)
  }
}
