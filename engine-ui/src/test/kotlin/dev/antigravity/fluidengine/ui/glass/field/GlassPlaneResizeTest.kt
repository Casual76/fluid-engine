package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * F4A.5b: il cambio di larghezza (un gruppo della barra che perde o guadagna un tasto, col bordo del margine fermo) e il
 * respiro di un vetro che cambia contenuto. Le misure sono in `artifacts/liquid-glass/sessioni/f4a-5b/misure.md`.
 */
class GlassPlaneResizeTest {
  private val h = 44f
  private val canvas = Rect(0f, 0f, 420f, 140f)
  private fun capsule(key: String, left: Float, width: Float, content: Any? = null) =
    GlassPlanePiece(key, Rect(left, 40f, left + width, 40f + h), FluidCornerRadii.all(h / 2f), id = key, contentKey = content)
  private fun state(vararg pieces: GlassPlanePiece, room: Rect = canvas) =
    GlassPlaneState(pieces.toList(), 12f, canvas, room = room)
  private fun GlassPlaneFrame.of(key: Any) = pieces.single { it.flightId.targetKey == key }

  /** Tre icone diventano il «+»: la capsula da 3 H a 1 H si chiude da sinistra, il bordo destro (il margine) resta. */
  private val three = capsule("gruppo", 252f, 3f * h)
  private val one = capsule("gruppo", 252f + 2f * h, h)

  @Test fun aGroupThatLosesButtonsTravelsOnTheResizeClockAfterAShortWait() {
    val journey = GlassPlaneJourney(state(three), state(one))
    assertEquals("il riposo a vista di un cambio di larghezza", FluidMotion.PlaneResizeMillis.toDouble(), journey.settleMs, 25.0)
    // L'attesa: nei primi 24 ms il bordo non si muove (Apple: 2% a 33 ms).
    assertEquals(three.bounds.left, journey.frame(FluidMotion.PlaneResizeWaitMillis - 1.0).of("gruppo").bounds.left, 0.001f)
    fun progress(ms: Double): Float {
      val drag = journey.frame(ms).of("gruppo").bounds.right - three.bounds.right
      // Il bordo che si chiude, senza il trascinamento (che sposta tutto il vetro).
      return (journey.frame(ms).of("gruppo").bounds.left - drag - three.bounds.left) / (one.bounds.left - three.bounds.left)
    }
    // Contro le due clip a tempo vero (tolto il trascinamento): 21, 61, 86, 97% a 100, 200, 300, 400 ms.
    assertEquals(0.21f, progress(100.0), 0.05f)
    assertEquals(0.61f, progress(200.0), 0.05f)
    assertEquals(0.86f, progress(300.0), 0.04f)
    assertEquals(0.97f, progress(400.0), 0.03f)
    // Il rigonfiamento suo, coi tempi di Apple (+13,2 / 13,8% a 200 ms, −3% a ~470): non il comune.
    val swollen = journey.frame(200.0).of("gruppo")
    assertEquals(FluidMotion.PlaneResizeBreath, swollen.swell, 0.03f)
    assertEquals(0f, swollen.swellShare, 0f)
    assertTrue("sotto la sua misura a ~470 ms", journey.frame(470.0).of("gruppo").swell < -0.1f)
    // All'arrivo, al bit.
    assertEquals(one.bounds, journey.frame(journey.durationMs + 0.01).of("gruppo").bounds)
    assertTrue(journey.frame(journey.durationMs + 0.01).finished)
  }

  @Test fun theWholeGlassIsDraggedTowardTheClosingEdgeAndComesBack() {
    val journey = GlassPlaneJourney(state(three), state(one))
    fun right(ms: Double) = journey.frame(ms).of("gruppo").bounds.right - one.bounds.right
    val peak = (0..900).maxBy { right(it.toDouble()) }
    val drag = right(peak.toDouble())
    // Apple: il «+» va a destra di 0,16 H a ~300 ms; qui il bordo fermo, con lo stesso trascinamento.
    assertEquals(FluidMotion.PlaneResizeDrag * h, drag, 0.5f)
    assertTrue("colmo a $peak ms", peak in 270..330)
    // Comincia quando il bordo che si chiude ha fatto un pezzo di strada (Apple: ~100–130 ms).
    assertEquals(0f, right(80.0), 0.001f)
    // Poi passa dall'altra parte, poco, e si ferma.
    val under = (400..900).minOf { right(it.toDouble()) }
    assertTrue("dall'altra parte di $under px", under < -0.5f && under > -0.35f * drag)
    assertTrue("il viaggio dura quanto il trascinamento: ${journey.durationMs}", journey.durationMs >= 0.7 * 1000.0)
  }

  @Test fun aGroupThatGainsAButtonDragsItsAnchoredEdgeInwardFromTheStart() {
    // 16:02 (R4 B2 (c)): il gruppo di sinistra da 2 a 3 icone, bordo sinistro al margine; il bordo fermo è tirato verso
    // l'interno (il «rientro» di R4) dal primo istante, al colmo a ~300 ms, e torna.
    val two = capsule("gruppo", 16f, 2f * h)
    val three = capsule("gruppo", 16f, 3f * h)
    val journey = GlassPlaneJourney(state(two), state(three))
    fun left(ms: Double) = journey.frame(ms).of("gruppo").bounds.left - 16f
    assertTrue("già tirato a 100 ms: ${left(100.0)}", left(100.0) > 0.5f)
    val peak = (0..900).maxBy { left(it.toDouble()) }
    assertTrue("colmo a $peak ms", peak in 270..330)
    // Il gruppo cresce di 1 H, il centro fa mezza H: il trascinamento è il 30% di quella strada, sotto il tetto di 0,155 H.
    assertEquals(FluidMotion.PlaneResizeDragShare * h / 2f, left(peak.toDouble()), 0.5f)
    assertEquals(three.bounds, journey.frame(journey.durationMs + 0.01).of("gruppo").bounds)
  }

  @Test fun theDragNeverLeavesTheStageRoom() {
    // Un gruppo a 2 px dal bordo del posto del palco: il trascinamento verso fuori si ferma lì.
    val room = Rect(0f, 0f, one.bounds.right + 2f, 140f)
    val journey = GlassPlaneJourney(state(three, room = room), state(one, room = room))
    val rightmost = (0..900).maxOf { journey.frame(it.toDouble()).of("gruppo").bounds.right }
    assertTrue("arriva a $rightmost", rightmost <= room.right + 0.001f)
  }

  @Test fun aGlassThatSlidesWhileResizingOrBarelyResizesKeepsTheContextClock() {
    // Si sposta mentre cambia misura: non è un gruppo ancorato al margine.
    val slide = GlassPlaneJourney(state(capsule("a", 20f, h)), state(capsule("a", 80f, 100f)))
    assertEquals(FluidMotion.PlaneContextMillis.toDouble(), slide.settleMs, 0.5)
    // Cambia di un terzo della sua altezza: una scritta che cambia di qualche lettera.
    val small = GlassPlaneJourney(state(capsule("a", 100f, 88f)), state(capsule("a", 100f, 88f + h / 3f)))
    assertEquals(FluidMotion.PlaneContextMillis.toDouble(), small.settleMs, 0.5)
    // Con una spec dell'app il tempo è quello della spec.
    val custom = GlassPlaneJourney(state(three), state(one),
      GlassPlaneMotion(spec = androidx.compose.animation.core.tween(200)))
    assertEquals(200.0, custom.settleMs, 1.0)
  }

  @Test fun everyoneTravellingWithAResizeSharesItsClock() {
    // Un tondo che si sposta nello stesso viaggio di un gruppo che si stringe: lo stesso orologio, la stessa attesa.
    val round = capsule("tondo", 16f, h)
    val moved = capsule("tondo", 40f, h)
    val journey = GlassPlaneJourney(state(round, three), state(moved, one))
    assertEquals(FluidMotion.PlaneResizeMillis.toDouble(), journey.settleMs, 25.0)
    assertEquals(round.bounds, journey.frame(FluidMotion.PlaneResizeWaitMillis - 1.0).of("tondo").bounds)
  }

  @Test fun anInterruptedResizeRestartsWhereItIsWithoutWaiting() {
    val first = GlassPlaneJourney(state(three), state(one))
    val at = 150.0
    val live = first.frame(at).of("gruppo")
    val back = first.retarget(at, state(three))
    val restart = back.frame(0.0).of("gruppo")
    assertEquals(live.bounds, restart.bounds)
    assertEquals(live.velocity, restart.velocity)
    // Nessuna attesa: al primo passo il vetro ha già cambiato verso o rallentato, non è rimasto fermo.
    val step = back.frame(8.0).of("gruppo").bounds
    assertTrue("si muove subito: ${restart.bounds} -> $step", abs(step.left - restart.bounds.left) > 0.01f)
    assertEquals(three.bounds, back.frame(back.durationMs + 0.01).of("gruppo").bounds)
  }

  @Test fun aRoundThatChangesContentBreathesOnlyAlongAChangeOfTheBar() {
    // Il tondo di sinistra della barra (elenco → lente) nello stesso viaggio della capsula che si chiude.
    val list = capsule("tondo", 16f, h, content = "elenco")
    val lens = capsule("tondo", 16f, h, content = "lente")
    val journey = GlassPlaneJourney(state(list, three), state(lens, one))
    val round = journey.frame(200.0).of("tondo")
    assertFalse("non è fermo: entra nel palco", round.still)
    assertTrue("tondo" !in journey.stillKeys)
    // Apple: +16,7% a ~200 ms. Il respiro è suo (`swellShare` zero), in unità del rigonfiamento del carattere.
    assertEquals(FluidMotion.PlaneContentBreath, round.swell, 0.05f)
    assertEquals(0f, round.swellShare, 0f)
    // Non si sposta e non cambia misura: respira soltanto.
    for (ms in 0..900 step 10) assertEquals(lens.bounds, journey.frame(ms.toDouble()).of("tondo").bounds)
    assertTrue("sotto la sua misura a ~490 ms", journey.frame(490.0).of("tondo").swell < -0.1f)
    assertEquals(0f, journey.frame(journey.durationMs + 0.01).of("tondo").swell, 0f)
    // Lo stesso contenuto, o un contenuto non dichiarato: fermo, com'era.
    val same = GlassPlaneJourney(state(list, three), state(list, one))
    assertTrue(same.frame(200.0).of("tondo").still)
    val undeclared = GlassPlaneJourney(state(capsule("tondo", 16f, h), three), state(capsule("tondo", 16f, h), one))
    assertTrue(undeclared.frame(200.0).of("tondo").still)
  }
}
