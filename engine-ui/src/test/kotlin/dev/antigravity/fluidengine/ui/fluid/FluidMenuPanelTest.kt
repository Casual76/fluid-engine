package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluidphysics.solveMorphMenuPanel
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il pannello dei menu (3.0, fase 7) senza Compose: le misure delle righe, le celle con le sezioni,
 * il posto del pannello per i tre host e il tasto premuto da cui il viaggio parte. Sono l'aritmetica
 * che l'host usa PRIMA di comporre (l'apertura e' sincrona) e che il pannello usa per il hit-test:
 * se le due divergessero il dito sceglierebbe la riga sopra quella che vede.
 */
class FluidMenuPanelTest {

  // --- Le righe ---------------------------------------------------------------------------------

  @Test
  fun aRowIsFortyEightDpAtTheDefaultFontAndGrowsWithTheLine() {
    assertEquals(48.dp, fluidMenuRowHeight(24.dp))
    // Il testo grande non si taglia: la riga cresce con la linea, 24 dp d'aria sempre.
    assertEquals(64.dp, fluidMenuRowHeight(40.dp))
    // E sotto i 48 dp non scende mai, anche con una linea piccola.
    assertEquals(48.dp, fluidMenuRowHeight(14.dp))
  }

  @Test
  fun withoutSectionsTheCellsAreStackedAfterThePadding() {
    val layout = fluidMenuPanelLayout(listOf(false, false, false), rowHeightPx = 100f, paddingPx = 10f, sectionGapPx = 20f)

    assertArrayEquals(floatArrayOf(10f, 110f, 210f), layout.tops, 0f)
    assertEquals(320f, layout.height, 0f)
    assertEquals(0, layout.sectionBands.size)
  }

  @Test
  fun aSectionAddsABandBeforeTheItemThatOpensIt() {
    val layout = fluidMenuPanelLayout(listOf(true, false, true), rowHeightPx = 100f, paddingPx = 10f, sectionGapPx = 20f)

    // La prima voce non apre niente: non c'e' niente sopra di lei da separare.
    assertArrayEquals(floatArrayOf(10f, 110f, 230f), layout.tops, 0f)
    assertArrayEquals(floatArrayOf(210f), layout.sectionBands, 0f)
    assertEquals(340f, layout.height, 0f)
  }

  @Test
  fun theSectionBandAndThePaddingAreNotUnderTheFinger() {
    val layout = fluidMenuPanelLayout(listOf(false, false, true), rowHeightPx = 100f, paddingPx = 10f, sectionGapPx = 20f)
    val g = fluidMenuGeometryOf(layout, listOf(true, true, true), Rect(50f, 400f, 300f, 400f + layout.height))

    assertEquals(0, fluidMenuCellAt(g, Offset(100f, 415f)))
    assertEquals(1, fluidMenuCellAt(g, Offset(100f, 515f)))
    // La banda fra le sezioni (y 610..630) e il padding non sono voci: rilasciare li' lascia aperto.
    assertEquals(-1, fluidMenuCellAt(g, Offset(100f, 620f)))
    assertEquals(-1, fluidMenuCellAt(g, Offset(100f, 405f)))
    assertEquals(2, fluidMenuCellAt(g, Offset(100f, 640f)))
  }

  @Test
  fun theGeometryCellsSpanThePanelWidth() {
    val layout = fluidMenuPanelLayout(listOf(false, false), rowHeightPx = 48f, paddingPx = 6f, sectionGapPx = 8f)
    val panel = Rect(16f, 100f, 268f, 100f + layout.height)
    val g = fluidMenuGeometryOf(layout, listOf(true, false), panel)

    assertEquals(Rect(16f, 106f, 268f, 154f), g.cells[0])
    assertEquals(Rect(16f, 154f, 268f, 202f), g.cells[1])
    assertEquals(panel, g.panel)
    assertTrue(g.isEnabled(0))
    assertTrue(!g.isEnabled(1))
  }

  // --- Il posto del pannello ---------------------------------------------------------------------

  private val host = Size(400f, 800f)

  @Test
  fun theMorphMenuSitsAboveItsButtonWhenThereIsRoomAndBelowOtherwise() {
    val low = Rect(150f, 700f, 250f, 744f)
    val above = solveFluidMenuPanel(low, host, 252f, 200f, gapPx = 12f, marginPx = 16f, placement = FluidMenuPlacement.AboveOrBelow)
    assertEquals(700f - 12f - 200f, above.top, 0.001f)

    val high = Rect(150f, 40f, 250f, 84f)
    val below = solveFluidMenuPanel(high, host, 252f, 200f, gapPx = 12f, marginPx = 16f, placement = FluidMenuPlacement.AboveOrBelow)
    assertEquals(84f + 12f, below.top, 0.001f)
  }

  @Test
  fun theOldMorphSolverIsTheAboveOrBelowCase() {
    val anchor = Rect(150f, 700f, 250f, 744f)
    val old = solveMorphMenuPanel(anchor, Rect(0f, 0f, 400f, 800f), 3, 48f, 252f, 6f, 12f, 16f)
    val new = solveFluidMenuPanel(anchor, host, 252f, 3 * 48f + 12f, 12f, 16f, FluidMenuPlacement.AboveOrBelow)
    assertEquals(old, new)
  }

  @Test
  fun anExpandMenuCoversItsButtonSoItOpensUnderTheFinger() {
    val bar = Rect(330f, 40f, 374f, 84f)
    val down = solveFluidMenuPanel(bar, host, 252f, 200f, 12f, 16f, FluidMenuPlacement.OverAnchor)
    // Il bordo da cui il pannello si allontana resta fermo: quello alto del tasto.
    assertEquals(40f, down.top, 0.001f)
    assertTrue("il pannello copre il tasto", down.contains(bar.center))

    val bottom = Rect(330f, 740f, 374f, 784f)
    val up = solveFluidMenuPanel(bottom, host, 252f, 200f, 12f, 16f, FluidMenuPlacement.OverAnchor)
    assertEquals(784f, up.bottom, 0.001f)
    assertTrue(up.contains(bottom.center))
  }

  @Test
  fun aContextMenuSitsBesideItsRowAndNeverOnIt() {
    val row = Rect(0f, 200f, 400f, 272f)
    val below = solveFluidMenuPanel(row, host, 252f, 200f, 12f, 16f, FluidMenuPlacement.BesideAnchor)
    assertEquals(272f + 12f, below.top, 0.001f)

    val lowRow = Rect(0f, 640f, 400f, 712f)
    val above = solveFluidMenuPanel(lowRow, host, 252f, 200f, 12f, 16f, FluidMenuPlacement.BesideAnchor)
    assertEquals(640f - 12f, above.bottom, 0.001f)
  }

  @Test
  fun thePanelStaysInsideTheMarginsAndNeverWiderThanTheRoomBetweenThem() {
    val nearEdge = Rect(0f, 400f, 30f, 444f)
    val p = solveFluidMenuPanel(nearEdge, host, 252f, 200f, 12f, 16f, FluidMenuPlacement.BesideAnchor)
    assertEquals(16f, p.left, 0.001f)

    val narrow = Size(200f, 800f)
    val q = solveFluidMenuPanel(Rect(80f, 400f, 120f, 444f), narrow, 252f, 200f, 12f, 16f, FluidMenuPlacement.AboveOrBelow)
    assertEquals(168f, q.width, 0.001f)
    assertEquals(16f, q.left, 0.001f)
  }

  // --- Il seme del menu contestuale ---------------------------------------------------------

  @Test
  fun theSeedIsThePanelScaledAroundThePivot() {
    val panel = Rect(100f, 300f, 352f, 500f)
    val pivot = Offset(200f, 300f)
    val seed = fluidMenuSeedFrame(panel, pivot, 0.5f)

    assertEquals(Rect(150f, 300f, 276f, 400f), seed)
  }

  @Test
  fun thePivotIsThePointOfThePanelNearestTheRow() {
    val row = Rect(0f, 200f, 400f, 272f)
    val below = Rect(74f, 284f, 326f, 484f)
    assertEquals(Offset(200f, 284f), fluidMenuPivot(below, row))

    val above = Rect(74f, -12f, 326f, 188f)
    assertEquals(Offset(200f, 188f), fluidMenuPivot(above, row))

    // Una riga stretta a sinistra: il perno si ferma sul bordo del pannello.
    assertEquals(74f, fluidMenuPivot(below, Rect(0f, 200f, 40f, 272f)).x, 0.001f)
  }

  // --- Il tasto premuto -----------------------------------------------------------------------

  @Test
  fun aButtonAtRestIsItsOwnRectangle() {
    val rest = Rect(100f, 600f, 300f, 644f)
    assertEquals(rest, fluidPressedBounds(rest, FluidPressTransform.Identity))
  }

  @Test
  fun thePressedButtonGrowsAroundItsCenterAndFollowsTheLean() {
    val rest = Rect(100f, 600f, 300f, 644f)
    val pressed = fluidPressedBounds(rest, FluidPressTransform(scaleX = 1.1f, scaleY = 1.2f, translationX = 6f, translationY = -4f))

    assertEquals(206f, pressed.center.x, 0.001f)
    assertEquals(618f, pressed.center.y, 0.001f)
    assertEquals(220f, pressed.width, 0.001f)
    assertEquals(52.8f, pressed.height, 0.001f)
  }

  @Test
  fun aDegenerateButtonIsLeftAlone() {
    val empty = Rect(10f, 10f, 10f, 10f)
    assertEquals(empty, fluidPressedBounds(empty, FluidPressTransform(1.5f, 1.5f, 3f, 3f)))
  }
}
