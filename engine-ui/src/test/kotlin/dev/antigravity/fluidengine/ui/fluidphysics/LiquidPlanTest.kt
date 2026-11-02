package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * La geometria del vetro liquido (3.0) nel piano di resa: l'ancora che si svuota nel corpo col
 * proprio raggio di fusione, il corpo deformato attorno al centro, e il patto col riposo — ai due
 * capi del viaggio e a corpo posato il piano e' quello di sempre, al bit.
 */
class LiquidPlanTest {

  // Il menu che si trasforma: il tasto sotto, il pannello sopra, staccati di 12 dp.
  private val button = FluidForm.Slab(Rect(90f, 296f, 190f, 340f), FluidCornerRadii.all(22f))
  private val panel = FluidForm.Slab(Rect(16f, 72f, 268f, 284f), FluidCornerRadii.all(26f))

  // Un'azione della barra: il pannello si apre SOPRA il tasto e lo contiene.
  private val barAction = FluidForm.Slab(Rect(200f, 40f, 244f, 84f), FluidCornerRadii.all(22f))
  private val barPanel = FluidForm.Slab(Rect(30f, 40f, 282f, 252f), FluidCornerRadii.all(26f))

  private fun liquidState(initial: FluidForm = button): FluidPhysicsState =
    FluidPhysicsState(initial).apply {
      liquidAllowed = true
      maskInShader = true
    }

  // --- Le funzioni pure ------------------------------------------------------------------------

  @Test
  fun theStrainWeightIsZeroOnTheAnchorAndFullFromAQuarterOn() {
    assertEquals(0f, liquidStrainWeight(0f), 0f)
    assertEquals(0f, liquidStrainWeight(-0.1f), 0f)
    assertEquals(1f, liquidStrainWeight(FluidLiquid.StrainAnchorSpan), 0f)
    assertEquals(1f, liquidStrainWeight(1.08f), 0f)
    var previous = 0f
    var p = 0f
    while (p <= 1f) {
      val w = liquidStrainWeight(p)
      assertTrue(w >= previous)
      previous = w
      p += 0.01f
    }
  }

  @Test
  fun theAnchorDrainsFromFullToNothingAndBlendsFromZero() {
    assertEquals(1f, liquidRootScale(0f), 0f)
    assertEquals(0f, liquidRootScale(FluidLiquid.RootDrainEnd), 0f)
    assertEquals(0f, liquidRootScale(1f), 0f)
    assertEquals(0f, liquidRootBlend(button.frame, 0f, 1f), 0f)
    // Il raggio di fusione e' una frazione del lato corto dell'ancora, a rampa finita.
    assertEquals(FluidLiquid.RootBlendFraction * 44f, liquidRootBlend(button.frame, 0.5f, 1f), 0.001f)
  }

  @Test
  fun theDrainingAnchorSlidesTowardTheBodyAndShrinksAroundItsCentre() {
    val half = liquidRootFrame(button.frame, panel.frame, 0.5f)
    assertEquals(50f, half.width, 0.001f)
    assertEquals(22f, half.height, 0.001f)
    // Il bordo che guarda il pannello e' quello alto (296): il centro (318) ci scivola per meta' della
    // frazione svuotata.
    val expectedY = 318f + (296f - 318f) * (0.5f * FluidLiquid.RootSlide)
    assertEquals(expectedY, half.center.y, 0.001f)
    assertEquals(button.frame, liquidRootFrame(button.frame, panel.frame, 1f))
  }

  @Test
  fun aBodyThatCoversItsAnchorHasNoSecondDrop() {
    assertTrue(liquidLeavesOrigin(button.frame, panel.frame))
    assertFalse(liquidLeavesOrigin(barAction.frame, barPanel.frame))
    // Un cerchio che diventa un quadrato attorno allo stesso centro (il Playground) non lascia niente.
    assertFalse(liquidLeavesOrigin(Rect(100f, 100f, 300f, 300f), Rect(110f, 110f, 290f, 290f)))
  }

  @Test
  fun theStrainKeepsTheCentreAndCapsEverySide() {
    val values = slabValues(panel)
    assertSame(values, strainSlab(values, 0f, 0f, 20f))
    val stretched = strainSlab(values, -0.1f, 0.1f, 1000f)
    assertEquals(panel.frame.center.x, stretched[0] + stretched[2] / 2f, 0.001f)
    assertEquals(panel.frame.center.y, stretched[1] + stretched[3] / 2f, 0.001f)
    assertEquals(panel.frame.height * 1.1f, stretched[3], 0.01f)
    assertEquals(panel.frame.width * 0.9f, stretched[2], 0.01f)
    // Il tetto per lato: 8 px al massimo, qualunque sia la deformazione.
    val capped = strainSlab(values, 0.14f, 0.14f, 8f)
    assertEquals(panel.frame.width + 16f, capped[2], 0.001f)
    assertEquals(panel.frame.height + 16f, capped[3], 0.001f)
    // I raggi seguono il fattore minore e non superano mai il mezzo lato.
    for (i in 4..7) assertTrue(stretched[i] <= minOf(stretched[2], stretched[3]) / 2f)
  }

  @Test
  fun theMotionLensIsTheDeclaredOpticsAtRestAndNeverOpensTheCapturePadding() {
    val optics = GlassDefaults.panelOptics(compact = true)
    assertSame(optics, liquidLensOptics(optics, 0f))
    val material = optics.asPhysicsMaterial()
    assertSame(material, liquidMaterial(material, 0f))
    assertEquals(0f, liquidMagnify(0f), 0f)
    var w = 0f
    while (w <= 1f) {
      val m = liquidMaterial(material, w)
      // Piu' lente, meno latte: la sfocatura non supera mai l'altezza che la copre.
      assertTrue(m.optics.blurScale <= optics.blurScale)
      assertTrue(m.optics.refractionHeight >= optics.refractionHeight)
      val uniforms = physicsLensUniforms(
        material = m,
        baseBlurPx = 2.5f * 2.625f,
        amount = 1f,
        quality = 1f,
        minDimPx = 200f * 2.625f,
        density = 2.625f,
        lensAvailable = true,
      )
      assertEquals(0f, physicsCapturePaddingPx(uniforms, vibrancyApplied = true), 0f)
      w += 0.05f
    }
    assertTrue(liquidMaterial(material, 1f).optics.refractionHeight > 10.dp)
  }

  // --- Il piano --------------------------------------------------------------------------------

  @Test
  fun withoutASurfaceThatAsksForItThePlanIsTheOneOfBefore() {
    // Uno stato costruito a mano non ha il liquido: un pezzo solo a meta' viaggio.
    val state = FluidPhysicsState(button)
    state.maskInShader = true
    state.driveExternally(button, panel, progress = { 0.4f }, startFrame = null, liquidAnchor = true)
    assertEquals(1, state.ensurePlan().pieceCount)
  }

  @Test
  fun leavingTheAnchorTheBodyAndTheDrainingAnchorAreOneField() {
    var t = 0.4f
    val state = liquidState()
    state.driveExternally(button, panel, progress = { t }, startFrame = null, liquidAnchor = true)
    val plan = state.ensurePlan()
    assertEquals(2, plan.pieceCount)
    // Il pezzo zero e' il corpo: il lerp del viaggio.
    val body = lerpSlab(button, panel, 0.4f)
    assertEquals(body[0] + body[2] / 2f, plan.pieceRects[0], 0.001f)
    assertEquals(body[1] + body[3] / 2f, plan.pieceRects[1], 0.001f)
    // Il pezzo uno e' l'ancora che si svuota, fusa col suo raggio.
    assertTrue(plan.pieceBlend[1] > 0f)
    assertEquals(0f, plan.pieceBlend[0], 0f)
    // Il riquadro e la sagoma "a rettangolo arrotondato" sono del corpo: lente, bordo e ombra parlano di lui.
    assertEquals(body[0], plan.bounds.left, 0.001f)
    assertEquals(body[1] + body[3], plan.bounds.bottom, 0.001f)
    assertNotNull(plan.silhouetteRoundRect)
    // Con piu' di un pezzo la tinta sta nello shader: il collo e' materiale che nessun path descrive.
    assertTrue(plan.tintInShader)

    t = 0.9f
    assertEquals(1, state.ensurePlan().pieceCount)
  }

  @Test
  fun atTheTwoEndsThereIsNoSecondDrop() {
    var t = 0f
    val state = liquidState()
    state.driveExternally(button, panel, progress = { t }, startFrame = null, liquidAnchor = true)
    val start = state.ensurePlan()
    assertEquals(1, start.pieceCount)
    assertEquals(button.frame.center.y, start.pieceRects[1], 0.001f)

    t = 1f
    val end = state.ensurePlan()
    assertEquals(1, end.pieceCount)
    assertEquals(panel.frame.center.y, end.pieceRects[1], 0.001f)
    assertNotNull(state.restFormNow())
  }

  @Test
  fun aPanelThatCoversItsAnchorNeverGrowsASecondDrop() {
    var t = 0f
    val state = liquidState(barAction)
    state.driveExternally(barAction, barPanel, progress = { t }, startFrame = null, liquidAnchor = true)
    while (t <= 1f) {
      assertEquals(1, state.ensurePlan().pieceCount)
      t += 0.05f
    }
  }

  @Test
  fun aJourneyThatDoesNotAskForTheAnchorKeepsOnePiece() {
    val state = liquidState()
    state.driveExternally(button, panel, progress = { 0.4f }, startFrame = null, liquidAnchor = false)
    assertEquals(1, state.ensurePlan().pieceCount)
  }

  @Test
  fun withoutTheShaderTheAnchorDoesNotDrain() {
    // Senza lo smooth minimum due rettangoli si unirebbero a spigolo vivo: niente seconda goccia.
    val state = liquidState()
    state.maskInShader = false
    state.driveExternally(button, panel, progress = { 0.4f }, startFrame = null, liquidAnchor = true)
    assertEquals(1, state.ensurePlan().pieceCount)
  }

  @Test
  fun aMorphFromASlabOutOfItselfDrainsTheOriginToo() = runTest {
    val state = liquidState()
    launch(Frames(testScheduler)) {
      state.morphTo(panel, androidx.compose.animation.core.tween(320, easing = androidx.compose.animation.core.LinearEasing))
    }
    advanceTimeBy(100L)
    runCurrent()
    assertTrue(state.isMorphing)
    assertEquals(2, state.ensurePlan().pieceCount)
    advanceTimeBy(400L)
    runCurrent()
    assertFalse(state.isMorphing)
    assertEquals(1, state.ensurePlan().pieceCount)
  }

  @Test
  fun theContentLandsOnTheTravelNotOnTheJelly() {
    var t = 0.5f
    val state = liquidState()
    state.driveExternally(button, panel, progress = { t }, startFrame = null, liquidAnchor = true)
    val body = lerpSlab(button, panel, 0.5f)
    val content = state.contentBoundsNow()
    assertEquals(body[0], content.left, 0.001f)
    assertEquals(body[1] + body[3], content.bottom, 0.001f)
    // Il ritaglio del contenuto e' il corpo, nelle coordinate del pannello; a riposo non c'e'.
    val clip = state.travelClipIn(panel.frame, t)
    assertNotNull(clip)
    assertEquals(body[0] - panel.frame.left, clip!!.left, 0.001f)
    t = 1f
    assertNull(state.travelClipIn(panel.frame, 1f))
  }

  @Test
  fun theBodyFrameIsTheTravelWithoutTheStrain() {
    var t = 0.25f
    val state = liquidState()
    state.driveExternally(button, panel, progress = { t }, startFrame = null, liquidAnchor = true)
    val frame = state.bodyFrameNow()!!
    val body = lerpSlab(button, panel, 0.25f)
    assertEquals(body[0], frame.left, 0.001f)
    assertEquals(body[1], frame.top, 0.001f)
    t = 1f
    assertEquals(panel.frame, state.bodyFrameNow())
    // Un gruppo non ha un corpo solo.
    val group = FluidPhysicsState(FluidForm.Group(listOf(button, barAction)))
    assertNull(group.bodyFrameNow())
  }

  // --- Il conducente ---------------------------------------------------------------------------

  /** Un fotogramma ogni 16 ms di tempo virtuale, contando quanti ne vengono chiesti. */
  private class Frames(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
    var requested = 0
    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
      requested++
      delay(16L)
      return onFrame(scheduler.currentTime * 1_000_000L)
    }
  }

  @Test
  fun theDriverDeformsTheJourneySettlesToExactlyZeroAndThenAsksForNothing() = runTest {
    val state = liquidState()
    state.density = 2.625f
    val clock = Frames(testScheduler)
    val progress = mutableFloatStateOf(0f)
    state.driveExternally(button, panel, progress = { progress.floatValue }, startFrame = null, liquidAnchor = true)
    val driver = launch(clock) { state.runLiquid() }
    runCurrent()
    // A riposo il conducente aspetta la sagoma: nessun fotogramma chiesto (D9).
    advanceTimeBy(500L)
    runCurrent()
    assertEquals(0, clock.requested)

    // Il viaggio del menu: la molla di serie, un valore ogni 16 ms.
    var maxStretch = 0f
    var maxSquash = 0f
    val mover = launch {
      for (i in 1..50) {
        progress.floatValue = if (i == 50) 1f else springAt(i * 0.016f)
        Snapshot.sendApplyNotifications()
        delay(16L)
        maxStretch = maxOf(maxStretch, state.liquid.strainY)
        maxSquash = minOf(maxSquash, state.liquid.strainY)
      }
    }
    advanceTimeBy(900L)
    runCurrent()
    assertTrue("il viaggio non ha stirato il corpo: $maxStretch", maxStretch > 0.03f)
    assertTrue("l'arrivo non ha schiacciato il corpo: $maxSquash", maxSquash < -0.01f)

    // Arrivato: la gelatina si posa a zero esatto e il conducente smette di chiedere fotogrammi.
    advanceTimeBy(2000L)
    runCurrent()
    assertEquals(0f, state.liquid.strainX, 0f)
    assertEquals(0f, state.liquid.strainY, 0f)
    assertEquals(0f, state.liquid.lens, 0f)
    assertNotNull(state.restFormNow())
    val settled = clock.requested
    advanceTimeBy(1000L)
    runCurrent()
    assertEquals(settled, clock.requested)
    mover.cancel()
    driver.cancel()
  }

  private fun springAt(t: Float): Float {
    val zeta = 0.62
    val omega = 2 * Math.PI / 0.36
    val wd = omega * kotlin.math.sqrt(1 - zeta * zeta)
    val e = kotlin.math.exp(-zeta * omega * t)
    return (1 - e * (kotlin.math.cos(wd * t) + zeta / kotlin.math.sqrt(1 - zeta * zeta) * kotlin.math.sin(wd * t))).toFloat()
  }
}
