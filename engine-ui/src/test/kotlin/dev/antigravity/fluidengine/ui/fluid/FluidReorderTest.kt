package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La matematica del riordino, senza dita: sopra chi si passa, dove sta l'elemento sotto il dito mentre la
 * lista cambia, quando la lista scorre da sola. E' la parte che `GridReorder` di FluidWeather provava da
 * sola, estesa ai due casi che nessuna app aveva: una lista che scorre sotto il dito e un elemento che si
 * muove di piu' posti in un colpo.
 */
class FluidReorderTest {

  /** Cinque righe da 100 px con 10 px di spaziatura, in una colonna larga 400. */
  private val column = (0 until 5).map { i ->
    FluidReorderBox(key = "k$i", index = i, left = 0f, top = i * 110f, width = 400f, height = 100f)
  }

  /** Una griglia 2x3 di tessere da 100 con 10 di spaziatura. */
  private val grid = (0 until 6).map { i ->
    FluidReorderBox(key = "g$i", index = i, left = (i % 2) * 110f, top = (i / 2) * 110f, width = 100f, height = 100f)
  }

  // --- targetBox ------------------------------------------------------------------------------------

  @Test
  fun `a centre inside the next row names it`() {
    assertEquals("k2", FluidReorder.targetBox(200f, 230f, "k1", column, FluidReorderAxis.Vertical)!!.key)
  }

  @Test
  fun `the dragged row itself is never the target`() {
    assertNull(FluidReorder.targetBox(200f, 150f, "k1", column, FluidReorderAxis.Vertical))
  }

  @Test
  fun `the spacing between two rows has nobody in it`() {
    // Fra la fine della riga 1 (210) e l'inizio della 2 (220): nessuno scambio.
    assertNull(FluidReorder.targetBox(200f, 215f, "k1", column, FluidReorderAxis.Vertical))
  }

  @Test
  fun `in a column only the vertical position counts`() {
    // Il dito puo' essere ovunque in orizzontale: la colonna non scambia di lato.
    assertEquals("k3", FluidReorder.targetBox(-500f, 340f, "k0", column, FluidReorderAxis.Vertical)!!.key)
    assertEquals("k3", FluidReorder.targetBox(900f, 340f, "k0", column, FluidReorderAxis.Vertical)!!.key)
  }

  @Test
  fun `in a row only the horizontal position counts`() {
    val row = (0 until 4).map { i ->
      FluidReorderBox("r$i", i, left = i * 60f, top = 0f, width = 50f, height = 80f)
    }
    assertEquals("r2", FluidReorder.targetBox(130f, -300f, "r0", row, FluidReorderAxis.Horizontal)!!.key)
  }

  @Test
  fun `in a grid both axes count`() {
    // Il centro sta nella tessera in basso a destra del primo blocco (colonna 1, riga 1).
    assertEquals("g3", FluidReorder.targetBox(160f, 160f, "g0", grid, FluidReorderAxis.Both)!!.key)
    // Sopra la stessa riga ma nello spazio fra due colonne: nessuno.
    assertNull(FluidReorder.targetBox(105f, 160f, "g0", grid, FluidReorderAxis.Both))
  }

  @Test
  fun `a fast drag over several rows names the one under the centre, not the next`() {
    assertEquals("k4", FluidReorder.targetBox(200f, 480f, "k0", column, FluidReorderAxis.Vertical)!!.key)
  }

  @Test
  fun `after a swap the same finger position no longer asks for a move back`() {
    // La riga trascinata era la 1 (top 110). Il dito la porta a top 170 (centro 220): l'unico a contenerlo e' k2.
    val before = FluidReorder.targetBox(220f, 220f, "k1", column, FluidReorderAxis.Vertical)!!
    assertEquals("k2", before.key)
    // Dopo lo scambio la riga e' nel posto 2 (top 220) e k2 in quello 1: la stessa sagoma sta ora nel suo riquadro.
    val swapped = listOf(
      column[0],
      column[2].copy(index = 1, top = 110f),
      column[1].copy(index = 2, top = 220f),
    ) + column.drop(3)
    assertNull(FluidReorder.targetBox(220f, 220f, "k1", swapped, FluidReorderAxis.Vertical))
  }

  // --- dragTranslation ------------------------------------------------------------------------------

  private val viewport = 400f to 600f

  @Test
  fun `the dragged row follows the finger from where it was lifted`() {
    // Sollevata a top 110, il dito scende di 40: sta a 150, cioe' 40 px sotto il suo posto.
    val t = FluidReorder.dragTranslation(column[1], 0f, 110f, 0f, 40f, viewport.first, viewport.second, FluidReorderAxis.Vertical)
    assertEquals(Offset(0f, 40f), t)
  }

  @Test
  fun `a swap that changes the slot does not move the row away from the finger`() {
    // Il dito ha portato la riga a top 170. Poi la lista la mette nel posto 2 (top 220): la traslazione cambia
    // segno, e la posizione che si vede e' la stessa.
    val visualBefore = column[1].top + FluidReorder.dragTranslation(column[1], 0f, 110f, 0f, 60f, 400f, 600f, FluidReorderAxis.Vertical).y
    val moved = column[1].copy(index = 2, top = 220f)
    val visualAfter = moved.top + FluidReorder.dragTranslation(moved, 0f, 110f, 0f, 60f, 400f, 600f, FluidReorderAxis.Vertical).y
    assertEquals(170f, visualBefore, 1e-3f)
    assertEquals(visualBefore, visualAfter, 1e-3f)
  }

  @Test
  fun `a scroll under a still finger leaves the row where the finger is`() {
    // Il dito e' fermo (60 px sotto dove ha preso la riga), la lista e' scorsa di 30 px: il posto in lista della
    // riga e' salito di 30, e la traslazione compensa (+30) per tenerla alla stessa altezza.
    val scrolled = column[1].copy(top = 110f - 30f)
    val t = FluidReorder.dragTranslation(scrolled, 0f, 110f, 0f, 60f, 400f, 600f, FluidReorderAxis.Vertical)
    assertEquals(scrolled.top + t.y, 170f, 1e-3f)
  }

  @Test
  fun `a list locks the other axis, a grid does not`() {
    val inColumn = FluidReorder.dragTranslation(column[1], 0f, 110f, 80f, 40f, 400f, 600f, FluidReorderAxis.Vertical)
    assertEquals(0f, inColumn.x, 0f)
    val inGrid = FluidReorder.dragTranslation(grid[0], 0f, 0f, 60f, 40f, 400f, 600f, FluidReorderAxis.Both)
    assertEquals(Offset(60f, 40f), inGrid)
  }

  @Test
  fun `the row never leaves the viewport however far the finger goes`() {
    val past = FluidReorder.dragTranslation(column[1], 0f, 110f, 0f, 5_000f, 400f, 600f, FluidReorderAxis.Vertical)
    assertEquals(600f - 100f, column[1].top + past.y, 1e-3f)
    val above = FluidReorder.dragTranslation(column[1], 0f, 110f, 0f, -5_000f, 400f, 600f, FluidReorderAxis.Vertical)
    assertEquals(0f, column[1].top + above.y, 1e-3f)
  }

  @Test
  fun `a tile in a grid is held inside on both axes`() {
    val t = FluidReorder.dragTranslation(grid[0], 0f, 0f, -300f, 9_000f, 220f, 330f, FluidReorderAxis.Both)
    assertEquals(0f, grid[0].left + t.x, 1e-3f)
    assertEquals(330f - 100f, grid[0].top + t.y, 1e-3f)
  }

  // --- autoScrollDelta ------------------------------------------------------------------------------

  private fun scroll(start: Float, end: Float, seconds: Float = 0.016f) =
    FluidReorder.autoScrollDelta(start, end, 0f, 600f, zone = 100f, maxSpeedPx = 1_000f, seconds = seconds)

  @Test
  fun `away from the edges the list stays`() {
    assertEquals(0f, scroll(150f, 250f), 0f)
    assertEquals(0f, scroll(300f, 400f), 0f)
  }

  @Test
  fun `near the top it scrolls toward the start and near the bottom toward the end`() {
    assertTrue(scroll(40f, 140f) < 0f)
    assertTrue(scroll(460f, 560f) > 0f)
  }

  @Test
  fun `the speed grows with the square of the depth and is full at the edge`() {
    // Meta' della fascia = un quarto della velocita'; sul bordo, tutta.
    val full = scroll(0f, 100f, seconds = 1f)
    val half = scroll(50f, 150f, seconds = 1f)
    assertEquals(-1_000f, full, 1e-3f)
    assertEquals(-250f, half, 1e-3f)
  }

  @Test
  fun `a row that is past the edge scrolls at full speed, not faster`() {
    assertEquals(-1_000f, scroll(-30f, 70f, seconds = 1f), 1e-3f)
  }

  @Test
  fun `the distance scrolled is the speed times the time of the frame`() {
    assertEquals(scroll(0f, 100f, 0.5f), 2f * scroll(0f, 100f, 0.25f), 1e-3f)
  }

  @Test
  fun `no zone, no speed or no time means no scrolling`() {
    assertEquals(0f, FluidReorder.autoScrollDelta(0f, 100f, 0f, 600f, 0f, 1_000f, 1f), 0f)
    assertEquals(0f, FluidReorder.autoScrollDelta(0f, 100f, 0f, 600f, 100f, 0f, 1f), 0f)
    assertEquals(0f, FluidReorder.autoScrollDelta(0f, 100f, 0f, 600f, 100f, 1_000f, 0f), 0f)
  }

  // --- fluidMoved -----------------------------------------------------------------------------------

  @Test
  fun `moving down puts the item at the target and the others shift up`() {
    assertEquals(listOf("b", "c", "a", "d"), listOf("a", "b", "c", "d").fluidMoved(0, 2))
  }

  @Test
  fun `moving up puts the item at the target and the others shift down`() {
    assertEquals(listOf("a", "d", "b", "c"), listOf("a", "b", "c", "d").fluidMoved(3, 1))
  }

  @Test
  fun `an impossible move leaves the list as it is`() {
    val list = listOf("a", "b", "c")
    assertSame(list, list.fluidMoved(1, 1))
    assertSame(list, list.fluidMoved(-1, 2))
    assertSame(list, list.fluidMoved(0, 3))
  }

  @Test
  fun `the swap of two neighbours is the same in both directions`() {
    val list = listOf("a", "b", "c")
    assertEquals(list.fluidMoved(0, 1), list.fluidMoved(1, 0))
  }
}
