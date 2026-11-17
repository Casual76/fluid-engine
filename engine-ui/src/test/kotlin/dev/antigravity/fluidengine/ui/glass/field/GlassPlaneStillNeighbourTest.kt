package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * f3-rifiniture, il ponte della scena `testo`: un tondo **fermo** («Indietro») accanto a una capsula con testo
 * («Salva» ↔ «Salva e chiudi la raccolta», stesso `id`) che cambia larghezza col bordo lontano. La geometria è quella del
 * Tab S9 (densità 2,125), in pixel del contenitore, letta dal registro `FINE` della galleria: il tondo da 44 dp in un
 * bersaglio da 48, la capsula da 48 dp, 6 dp (13 px) fra i due vetri, lo spacing di serie 16 dp (34 px). Il posto del
 * palco è la finestra.
 *
 * Prima di f3-rifiniture il trascinamento di F4A.5b portava la capsula che si accorcia ~16 px verso il tondo, e il tondo
 * stava comunque nel suo campo (13 px sono sotto lo spacing): l'orologio del collo tirava fra i due un ponte per ~400 ms.
 */
class GlassPlaneStillNeighbourTest {
  private val spacing = 34f
  private val window = Rect(-40f, -650f, 1560f, 1910f)
  private val back = GlassPlanePiece("indietro", Rect(4f, 4f, 98f, 98f), FluidCornerRadii.all(47f), id = "indietro")
  private fun label(width: Float) =
    GlassPlanePiece("salva", Rect(111f, 0f, 111f + width, 102f), FluidCornerRadii.all(51f), id = "salva")
  private val short = label(180f)
  private val long = label(504f)
  private fun state(vararg pieces: GlassPlanePiece) = GlassPlaneState(
    pieces.toList(), spacing, Rect(0f, 0f, pieces.maxOf { it.bounds.right }, 102f), room = window,
  )
  private fun GlassPlaneFrame.of(key: Any) = pieces.single { it.flightId.targetKey == key }
  private fun GlassPlaneJourney.together(a: Any, b: Any) = fields.any { field ->
    field.flights.any { it.targetKey == a } && field.flights.any { it.targetKey == b }
  }

  /** Il varco più stretto fra il tondo e la capsula in tutto il viaggio, sulle geometrie del piano (senza rigonfiamento). */
  private fun GlassPlaneJourney.narrowestGap(): Float = (0..(durationMs.toInt() + 1)).minOf { ms ->
    val frame = frame(ms.toDouble())
    frame.of("salva").bounds.left - frame.of("indietro").bounds.right
  }

  @Test fun theStillRoundStaysOutOfTheFieldOfALabelThatShortens() {
    val journey = GlassPlaneJourney(state(back, long), state(back, short))
    // È un cambio di larghezza di F4A.5b: il suo orologio.
    assertEquals(FluidMotion.PlaneResizeMillis.toDouble(), journey.settleMs, 25.0)
    assertFalse("il tondo fermo non sta nel campo di «Salva»", journey.together("indietro", "salva"))
    assertTrue("indietro" in journey.stillKeys)
    // Il trascinamento non lo porta addosso al tondo: il varco resta quello di riposo (13 px).
    val narrowest = journey.narrowestGap()
    assertTrue("varco più stretto $narrowest px", narrowest >= 13f - FluidMotion.PlaneSettlePx)
    // Il resto della coreografia c'è ancora: il rigonfiamento suo, e la capsula arriva al bit.
    assertEquals(FluidMotion.PlaneResizeBreath, journey.frame(200.0).of("salva").swell, 0.03f)
    assertEquals(short.bounds, journey.frame(journey.durationMs + 0.01).of("salva").bounds)
  }

  @Test fun theStillRoundStaysOutOfTheFieldOfALabelThatGrows() {
    val journey = GlassPlaneJourney(state(back, short), state(back, long))
    assertFalse(journey.together("indietro", "salva"))
    assertTrue("indietro" in journey.stillKeys)
    // Chi si allarga è trascinato verso il bordo che si muove (lontano dal tondo); il passaggio dall'altra parte, che lo
    // riportava verso il tondo, si ferma al varco di riposo.
    val narrowest = journey.narrowestGap()
    assertTrue("varco più stretto $narrowest px", narrowest >= 13f - FluidMotion.PlaneSettlePx)
    val drag = (0..900).maxOf { journey.frame(it.toDouble()).of("salva").bounds.left - short.bounds.left }
    assertTrue("trascinato via dal tondo di $drag px", drag > 5f)
    assertEquals(long.bounds, journey.frame(journey.durationMs + 0.01).of("salva").bounds)
  }

  @Test fun aLabelThatChangesByAFewLettersDoesNotBridgeTheStillRound() {
    // Sotto metà altezza non è un cambio di larghezza (nessun trascinamento): col solo campo comune il ponte c'era lo stesso.
    val journey = GlassPlaneJourney(state(back, label(180f)), state(back, label(220f)))
    assertEquals(FluidMotion.PlaneContextMillis.toDouble(), journey.settleMs, 0.5)
    assertFalse(journey.together("indietro", "salva"))
  }

  @Test fun aNeighbourThatComesCloserStillJoinsTheField() {
    // La regola nuova vale solo a distanza costante: una capsula che allunga il bordo verso il tondo gli va incontro, e il
    // campo li fonde come prima (il collo di F3.3 fra due vetri che si toccano in viaggio).
    val toward = GlassPlanePiece("salva", Rect(105f, 0f, 285f, 102f), FluidCornerRadii.all(51f), id = "salva")
    val journey = GlassPlaneJourney(state(back, short), state(back, toward))
    assertTrue(journey.together("indietro", "salva"))
  }

  @Test fun theDragIsUnchangedWhereThereIsRoomAndStopsShortOfTheSpacingOtherwise() {
    val h = 102f
    fun round(gap: Float) = GlassPlanePiece("tondo", Rect(111f - gap - 94f, 4f, 111f - gap, 98f), FluidCornerRadii.all(47f), id = "tondo")
    fun dragOf(journey: GlassPlaneJourney) = (0..900).maxOf { short.bounds.left - journey.frame(it.toDouble()).of("salva").bounds.left }
    val alone = GlassPlaneJourney(state(long), state(short))
    val free = dragOf(alone)
    assertEquals(FluidMotion.PlaneResizeDrag * h, free, 0.5f)
    // Un vicino lontano: il trascinamento è quello di F4A.5b, al bit.
    val far = GlassPlaneJourney(state(round(spacing + 40f), long), state(round(spacing + 40f), short))
    assertEquals(free, dragOf(far), 0f)
    assertFalse(far.together("tondo", "salva"))
    // Un vicino oltre lo spacing ma più vicino del trascinamento: il vetro si ferma mezzo pixel prima del suo campo.
    val gap = spacing + 6f
    val near = GlassPlaneJourney(state(round(gap), long), state(round(gap), short))
    assertEquals(gap - spacing - FluidMotion.PlaneSettlePx, dragOf(near), 0.05f)
    assertFalse("il vicino resta fuori dal campo", near.together("tondo", "salva"))
    assertTrue("tondo" in near.stillKeys)
  }

  @Test fun aStillNeighbourThatIsUnitedOrAlreadyInTheFieldDoesNotStopTheDrag() {
    // Un vicino unito alla capsula (lo stesso vetro per l'app) è nel suo campo comunque: il trascinamento non lo guarda.
    val united = back.copy(unionId = "u")
    val journey = GlassPlaneJourney(state(united, long.copy(unionId = "u")), state(united, short.copy(unionId = "u")))
    val drag = (0..900).maxOf { short.bounds.left - journey.frame(it.toDouble()).of("salva").bounds.left }
    assertEquals(FluidMotion.PlaneResizeDrag * 102f, drag, 0.5f)
  }
}
