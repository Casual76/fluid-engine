package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import org.junit.Assert.*
import org.junit.Test

/** R7, riga 21: una lente per chi arriva, lo stesso vetro per chi resta. */
class GlassPlaneLensIdentityTest {
  private val canvas = Rect(0f, 0f, 400f, 120f)
  private fun piece(key: String, id: Any? = key) = GlassPlanePiece(key, Rect(16f, 32f, 60f, 76f),
    FluidCornerRadii.all(22f), id = id, transition = GlassPlaneTransition.MaterializeWhenAlone)
  private fun state(vararg pieces: GlassPlanePiece) = GlassPlaneState(pieces.toList(), 16f, canvas)

  @Test fun `un'uscita nuova non nasce dal tasto accanto e non vi confluisce al ritorno`() {
    val other = piece("modifica").copy(transition = GlassPlaneTransition.MatchedGeometry)
    val back = piece("indietro").copy(bounds = Rect(72f, 32f, 116f, 76f))
    val entering = GlassPlaneJourney(state(other), state(other, back)).frame(100.0).pieces
    assertEquals(GlassPlaneChange.Materialize, entering.single { it.piece.id == "indietro" }.change)
    assertNull(entering.single { it.piece.id == "indietro" }.bornFrom)
    val exiting = GlassPlaneJourney(state(other, back), state(other)).frame(100.0).pieces
    assertEquals(GlassPlaneChange.Dematerialize, exiting.single { it.piece.id == "indietro" }.change)
    assertNull(exiting.single { it.piece.id == "indietro" }.mergesInto)
  }

  @Test fun `stesso id anche con nodo nuovo resta accoppiato e fermo al pixel`() {
    val old = piece("vecchio", "back")
    val next = piece("nuovo", "back")
    for (time in listOf(0.0, 33.0, 150.0, 500.0)) {
      val frame = GlassPlaneJourney(state(old), state(next)).frame(time)
      val flying = frame.pieces.single()
      assertEquals(GlassPlaneChange.Matched, flying.change)
      assertEquals(GlassPlaneMatch.Id, flying.matchedBy)
      assertEquals(old.bounds, flying.bounds)
      assertEquals(1f, flying.materialize, 0f)
      assertTrue(flying.still)
    }
  }

  @Test fun `senza id non ruba il ruolo o il simbolo e non passa fra contenitori`() {
    val old = piece("old", null).copy(role = "back", symbol = "arrow")
    val next = piece("next", null).copy(role = "back", symbol = "arrow")
    assertEquals(setOf(GlassPlaneChange.Materialize, GlassPlaneChange.Dematerialize),
      GlassPlaneJourney(state(old), state(next)).frame(100.0).pieces.map { it.change }.toSet())
    assertEquals(2, GlassPlaneJourney(state(piece("old", "back")),
      state(piece("next", "back").copy(containerId = "altro"))).frame(100.0).pieces.size)
  }

  @Test fun `un retarget durante la lente conserva il contorno e la lente gia raggiunti`() {
    val back = piece("back")
    val first = GlassPlaneJourney(state(), state(back))
    val before = first.frame(150.0).pieces.single()
    val after = first.retarget(150.0, state(back.copy(bounds = back.bounds.translate(12f, 0f))))
      .frame(0.0).pieces.single()
    assertEquals(before.bounds, after.bounds)
    assertEquals(before.materialize, after.materialize, 0.00001f)
    assertEquals(before.velocity, after.velocity)
  }

  @Test fun `Materialize esplicito continua a morire e rinascere anche con lo stesso id`() {
    val old = piece("old", "back").copy(transition = GlassPlaneTransition.Materialize)
    val next = old.copy(key = "next")
    assertEquals(setOf(GlassPlaneChange.Materialize, GlassPlaneChange.Dematerialize),
      GlassPlaneJourney(state(old), state(next)).frame(100.0).pieces.map { it.change }.toSet())
  }
}
