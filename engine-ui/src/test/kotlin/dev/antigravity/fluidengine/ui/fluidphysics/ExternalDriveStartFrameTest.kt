package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La partenza viva di `driveExternally` (3.0): un menu parte dal tasto PREMUTO e, mentre il viaggio
 * corre, la pressione si scioglie e la partenza torna a riposo. Se il `from` fosse fisso, ogni
 * cambio del fotogramma di partenza sarebbe un salto della sagoma; con la partenza viva il lerp
 * fra il rettangolo di questo istante e il traguardo la assorbe.
 */
class ExternalDriveStartFrameTest {

  private val rest = FluidForm.Slab(Rect(100f, 600f, 300f, 644f), FluidCornerRadii.all(22f))
  private val panel = FluidForm.Slab(Rect(40f, 200f, 292f, 400f), FluidCornerRadii.all(18f))

  /** Il tasto sotto il dito: piu' grande di quello a riposo, attorno allo stesso centro. */
  private val pressed = Rect(90f, 590f, 310f, 654f)

  private fun PhysicsRenderPlan.centerX() = pieceRects[0]
  private fun PhysicsRenderPlan.centerY() = pieceRects[1]
  private fun PhysicsRenderPlan.halfWidth() = pieceRects[2]
  private fun PhysicsRenderPlan.halfHeight() = pieceRects[3]

  @Test
  fun withoutAStartFrameTheFromDecidesTheGeometryAsBefore() {
    val state = FluidPhysicsState(rest)
    state.driveExternally(rest, panel, progress = { 0f })

    val plan = state.ensurePlan()

    assertEquals(200f, plan.centerX(), 0.001f)
    assertEquals(622f, plan.centerY(), 0.001f)
    assertEquals(100f, plan.halfWidth(), 0.001f)
    assertEquals(22f, plan.halfHeight(), 0.001f)
  }

  @Test
  fun theLiveStartReplacesTheFromAndIsACapsule() {
    val state = FluidPhysicsState(rest)
    state.driveExternally(rest, panel, progress = { 0f }, startFrame = { pressed })

    val plan = state.ensurePlan()

    // A t=0 e' il rettangolo premuto, non quello a riposo...
    assertEquals(200f, plan.centerX(), 0.001f)
    assertEquals(622f, plan.centerY(), 0.001f)
    assertEquals(110f, plan.halfWidth(), 0.001f)
    assertEquals(32f, plan.halfHeight(), 0.001f)
    // ...e tondo: raggio mezzo lato corto (32), non i 22 del tasto a riposo.
    for (i in 0..3) assertEquals(32f, plan.pieceRadii[i], 0.001f)
  }

  @Test
  fun halfwayIsTheMidpointBetweenTheLiveStartAndThePanel() {
    val state = FluidPhysicsState(rest)
    state.driveExternally(rest, panel, progress = { 0.5f }, startFrame = { pressed })

    val plan = state.ensurePlan()

    // left 90 -> 40, right 310 -> 292, top 590 -> 200, bottom 654 -> 400.
    assertEquals((65f + 301f) / 2f, plan.centerX(), 0.001f)
    assertEquals((395f + 527f) / 2f, plan.centerY(), 0.001f)
    assertEquals((301f - 65f) / 2f, plan.halfWidth(), 0.001f)
    assertEquals((527f - 395f) / 2f, plan.halfHeight(), 0.001f)
    // Il raggio: 32 -> 18 a meta' e' 25.
    assertEquals(25f, plan.pieceRadii[0], 0.001f)
  }

  @Test
  fun theTravelEndsExactlyOnThePanelWhateverTheStart() {
    val state = FluidPhysicsState(rest)
    state.driveExternally(rest, panel, progress = { 1f }, startFrame = { pressed })

    val plan = state.ensurePlan()

    assertEquals(panel.frame.center.x, plan.centerX(), 0.001f)
    assertEquals(panel.frame.center.y, plan.centerY(), 0.001f)
    assertEquals(panel.frame.width / 2f, plan.halfWidth(), 0.001f)
    assertEquals(panel.frame.height / 2f, plan.halfHeight(), 0.001f)
    assertEquals(18f, plan.pieceRadii[0], 0.001f)
  }

  @Test
  fun thePlanIsCachedOnTimeAndRectangle() {
    var t = 0.4f
    var start = pressed
    val state = FluidPhysicsState(rest)
    state.driveExternally(rest, panel, progress = { t }, startFrame = { start })

    val first = state.ensurePlan().shape
    val again = state.ensurePlan().shape
    // Ne' t ne' la partenza sono cambiati: la stessa istantanea, nessun lavoro.
    assertSame(first, again)

    // La partenza si muove a progresso fermo (la pressione si scioglie): piano nuovo.
    val centerBefore = state.ensurePlan().centerY()
    start = rest.frame
    val afterRelax = state.ensurePlan()
    assertNotSame(first, afterRelax.shape)
    // Fra il rettangolo premuto e quello a riposo cambia l'altezza: il centro no, ma le meta' si'.
    assertEquals(centerBefore, afterRelax.centerY(), 0.001f)
    val relaxedShape = afterRelax.shape
    assertSame(relaxedShape, state.ensurePlan().shape)

    // Il progresso si muove con la partenza ferma: piano nuovo.
    t = 0.45f
    assertNotSame(relaxedShape, state.ensurePlan().shape)
  }

  @Test
  fun aRelaxingStartMovesTheSilhouetteSmoothly() {
    // Lo scopo: nessun salto. Mentre la partenza passa da premuta a riposo, a progresso fisso la
    // sagoma cambia di una frazione (1 - t) della differenza, non dell'intera.
    var start = pressed
    val state = FluidPhysicsState(rest)
    state.driveExternally(rest, panel, progress = { 0.5f }, startFrame = { start })
    val before = state.ensurePlan().halfWidth()

    start = rest.frame
    val after = state.ensurePlan().halfWidth()

    // La larghezza di partenza scende di 20 px (da 220 a 200): a meta' viaggio la larghezza della
    // sagoma ne sente 10, e la mezza larghezza 5.
    assertEquals(before - 5f, after, 0.001f)
  }

  @Test
  fun callingAgainWithTheSameEndpointsSwapsTheStartInPlace() {
    val state = FluidPhysicsState(rest)
    val progress = { 0f }
    state.driveExternally(rest, panel, progress, startFrame = { pressed })
    val first = state.ensurePlan().halfHeight()
    assertEquals(32f, first, 0.001f)

    // Stessi estremi, lambda nuova (cattura un altro rettangolo): si vede subito, senza reinstallare.
    val taller = Rect(90f, 580f, 310f, 664f)
    state.driveExternally(rest, panel, progress, startFrame = { taller })

    assertEquals(42f, state.ensurePlan().halfHeight(), 0.001f)
  }

  @Test
  fun droppingTheStartFrameGoesBackToTheFrom() {
    val state = FluidPhysicsState(rest)
    state.driveExternally(rest, panel, progress = { 0f }, startFrame = { pressed })
    assertEquals(32f, state.ensurePlan().halfHeight(), 0.001f)

    // La presenza della partenza cambia la corsa: senza, torna a decidere il `from`.
    state.driveExternally(rest, panel, progress = { 0f })

    assertEquals(22f, state.ensurePlan().halfHeight(), 0.001f)
  }

  @Test
  fun pastTheTargetWithInflationTheStartDoesNotMatter() {
    // Oltre l'1 la sagoma e' il traguardo GONFIATO attorno al suo centro: la partenza non c'entra
    // piu', quindi due partenze diverse danno lo stesso piano.
    fun inflatedHalfWidth(start: Rect): Float {
      val state = FluidPhysicsState(rest)
      state.driveExternally(
        rest, panel, progress = { 1.1f },
        overshootInflationX = 0.5f, overshootInflationY = 0.3f,
        startFrame = { start },
      )
      return state.ensurePlan().halfWidth()
    }

    val fromPressed = inflatedHalfWidth(pressed)
    val fromRest = inflatedHalfWidth(rest.frame)

    assertEquals(fromRest, fromPressed, 0f)
    // 126 (mezza larghezza del pannello) * (1 + 0.1 * 0.5)
    assertEquals(126f * 1.05f, fromPressed, 0.001f)
  }

  @Test
  fun aDegenerateStartStaysFiniteAndInsideTheLimits() {
    // Una partenza di un punto (il fotogramma e' misurato prima del layout) non deve dare NaN.
    val state = FluidPhysicsState(rest)
    state.driveExternally(rest, panel, progress = { 0f }, startFrame = { Rect(200f, 622f, 200f, 622f) })

    val plan = state.ensurePlan()

    assertTrue(plan.halfWidth().isFinite() && plan.halfHeight().isFinite())
    assertTrue(plan.halfWidth() >= MinSlabSidePx / 2f)
  }
}
