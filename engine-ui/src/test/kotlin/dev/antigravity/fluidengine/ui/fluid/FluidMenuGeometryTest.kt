package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Dove sta il dito rispetto a un menu: il hit-test delle celle e il centro della goccia. Sono le
 * due funzioni che, sbagliate di un pixel, fanno scegliere la voce accanto — e nessun
 * fotogramma lo mostra.
 */
class FluidMenuGeometryTest {

  /**
   * Un pannello 200x168 con tre righe da 48 e 12 px di padding sopra e sotto, 16 px di rientro ai
   * fianchi: le celle sono solo le voci, il resto del pannello non e' sotto il dito.
   */
  private fun column(
    enabled: List<Boolean> = listOf(true, true, true),
    gap: Float = 0f,
  ): FluidMenuGeometry {
    val cells = enabled.indices.map { i ->
      val top = 12f + i * 48f
      Rect(16f, top, 184f, top + 48f - gap)
    }
    return FluidMenuGeometry(cells, enabled, panel = Rect(0f, 0f, 200f, 12f + enabled.size * 48f + 12f))
  }

  @Test
  fun aPointInsideACellFindsThatCell() {
    val g = column()
    assertEquals(0, fluidMenuCellAt(g, Offset(100f, 30f)))
    assertEquals(1, fluidMenuCellAt(g, Offset(100f, 80f)))
    assertEquals(2, fluidMenuCellAt(g, Offset(100f, 130f)))
  }

  @Test
  fun thePanelPaddingIsNotACell() {
    val g = column()
    // Sopra la prima voce e sotto l'ultima, ma dentro il pannello.
    assertEquals(-1, fluidMenuCellAt(g, Offset(100f, 5f)))
    assertEquals(-1, fluidMenuCellAt(g, Offset(100f, 12f + 3 * 48f + 6f)))
    // Il rientro laterale: dentro il pannello, fuori dalle celle.
    assertEquals(-1, fluidMenuCellAt(g, Offset(8f, 80f)))
    assertEquals(-1, fluidMenuCellAt(g, Offset(192f, 80f)))
  }

  @Test
  fun outsideThePanelIsNotACell() {
    val g = column()
    assertEquals(-1, fluidMenuCellAt(g, Offset(-30f, 80f)))
    assertEquals(-1, fluidMenuCellAt(g, Offset(100f, -4f)))
    assertEquals(-1, fluidMenuCellAt(g, Offset(100f, 900f)))
    assertEquals(-1, fluidMenuCellAt(g, Offset(900f, 80f)))
  }

  @Test
  fun aGapBetweenCellsIsASeparatorAndNotACell() {
    // Celle con mezzo pixel di separatore fra l'una e l'altra.
    val g = column(gap = 0.5f)
    assertEquals(0, fluidMenuCellAt(g, Offset(100f, 59.4f)))
    assertEquals(-1, fluidMenuCellAt(g, Offset(100f, 59.75f)))
    assertEquals(1, fluidMenuCellAt(g, Offset(100f, 60f)))
  }

  @Test
  fun theBoundaryOfTwoAdjacentCellsBelongsToTheLowerOneAndNeverToBoth() {
    // Righe a contatto, separatore disegnato proprio sul confine: il confine e' della riga sotto.
    val g = column()
    assertEquals(0, fluidMenuCellAt(g, Offset(100f, 59.999f)))
    assertEquals(1, fluidMenuCellAt(g, Offset(100f, 60f)))
    // L'ultimo bordo basso e' fuori: nessuna riga sotto.
    assertEquals(-1, fluidMenuCellAt(g, Offset(100f, 156f)))
  }

  @Test
  fun aDisabledCellIsStillACellButNotEnabled() {
    val g = column(enabled = listOf(true, false, true))
    assertEquals(1, fluidMenuCellAt(g, Offset(100f, 80f)))
    assertFalse(g.isEnabled(1))
    assertTrue(g.isEnabled(0))
    assertTrue(g.isEnabled(2))
    // Fuori dalle celle e indici impossibili non sono mai abilitati.
    assertFalse(g.isEnabled(-1))
    assertFalse(g.isEnabled(3))
  }

  @Test
  fun anEmptyMenuHasNoCells() {
    val g = FluidMenuGeometry(emptyList(), emptyList(), Rect(0f, 0f, 200f, 100f))
    assertEquals(-1, fluidMenuCellAt(g, Offset(100f, 50f)))
  }

  @Test
  fun cellsAndEnabledFlagsMustMatchInNumber() {
    try {
      FluidMenuGeometry(listOf(Rect(0f, 0f, 10f, 10f)), emptyList(), Rect(0f, 0f, 10f, 10f))
      fail("una cella senza flag deve essere rifiutata")
    } catch (expected: IllegalArgumentException) {
      // ok
    }
  }

  // --- Il centro della goccia --------------------------------------------------------

  @Test
  fun theDropCenterAtAWholeValueIsTheCenterOfThatCell() {
    val g = column()
    assertEquals(Offset(100f, 36f), fluidMenuDropCenter(g, 0f))
    assertEquals(Offset(100f, 84f), fluidMenuDropCenter(g, 1f))
    assertEquals(Offset(100f, 132f), fluidMenuDropCenter(g, 2f))
  }

  @Test
  fun aFractionalValueSitsBetweenTwoAdjacentCells() {
    val g = column()
    val quarter = fluidMenuDropCenter(g, 0.25f)
    assertEquals(100f, quarter.x, 0.001f)
    assertEquals(36f + 0.25f * 48f, quarter.y, 0.001f)

    val between = fluidMenuDropCenter(g, 1.5f)
    assertEquals(84f + 24f, between.y, 0.001f)

    // Non salta fra le celle: avanza in modo monotono con il valore.
    var previous = fluidMenuDropCenter(g, 0f).y
    var v = 0.05f
    while (v <= 2f) {
      val y = fluidMenuDropCenter(g, v).y
      assertTrue("la goccia e' tornata indietro a $v", y >= previous - 0.0001f)
      previous = y
      v += 0.05f
    }
  }

  @Test
  fun aTwoCellHorizontalGeometryMovesTheDropSideways() {
    // L'avviso con due pulsanti affiancati: celle una accanto all'altra, la goccia scivola in x.
    val left = Rect(20f, 120f, 130f, 168f)
    val right = Rect(130f, 120f, 240f, 168f)
    val g = FluidMenuGeometry(listOf(left, right), listOf(true, true), Rect(0f, 0f, 260f, 188f))

    assertEquals(left.center, fluidMenuDropCenter(g, 0f))
    assertEquals(right.center, fluidMenuDropCenter(g, 1f))

    val mid = fluidMenuDropCenter(g, 0.5f)
    assertEquals((left.center.x + right.center.x) / 2f, mid.x, 0.001f)
    assertEquals(144f, mid.y, 0.001f)

    val third = fluidMenuDropCenter(g, 1f / 3f)
    assertEquals(left.center.x + (right.center.x - left.center.x) / 3f, third.x, 0.01f)
  }

  @Test
  fun aValueOutsideTheRangeStopsAtTheFirstOrLastCell() {
    val g = column()
    assertEquals(fluidMenuDropCenter(g, 0f), fluidMenuDropCenter(g, -0.7f))
    assertEquals(fluidMenuDropCenter(g, 2f), fluidMenuDropCenter(g, 2.6f))
    // Un NaN (una molla non ancora inizializzata) non sporca il layer.
    assertEquals(fluidMenuDropCenter(g, 0f), fluidMenuDropCenter(g, Float.NaN))
  }

  @Test
  fun aSingleCellAndAnEmptyMenuHaveASafeCenter() {
    val one = FluidMenuGeometry(listOf(Rect(0f, 0f, 100f, 48f)), listOf(true), Rect(0f, 0f, 100f, 48f))
    assertEquals(Offset(50f, 24f), fluidMenuDropCenter(one, 0f))
    assertEquals(Offset(50f, 24f), fluidMenuDropCenter(one, 3f))

    val none = FluidMenuGeometry(emptyList(), emptyList(), Rect(0f, 0f, 100f, 80f))
    assertEquals(Offset(50f, 40f), fluidMenuDropCenter(none, 0f))
  }

  // --- Il rilascio e le sue regole ------------------------------------------------------

  @Test
  fun releaseOnAnEnabledItemPicksItForThePanelFinger() {
    assertEquals(
      FluidMenuRelease.Pick(2),
      fluidMenuRelease(FluidMenuOwner.Panel, armed = false, index = 2, enabled = true),
    )
  }

  @Test
  fun theHandedOffFingerPicksOnlyOnceItHasMoved() {
    assertEquals(
      FluidMenuRelease.StayOpen,
      fluidMenuRelease(FluidMenuOwner.HandOff, armed = false, index = 1, enabled = true),
    )
    assertEquals(
      FluidMenuRelease.Pick(1),
      fluidMenuRelease(FluidMenuOwner.HandOff, armed = true, index = 1, enabled = true),
    )
  }

  @Test
  fun anywhereElseTheMenuStaysOpen() {
    for (owner in FluidMenuOwner.entries) {
      for (armed in listOf(false, true)) {
        // Fuori da ogni voce (padding, separatore, fuori pannello).
        assertEquals(
          "owner=$owner armed=$armed",
          FluidMenuRelease.StayOpen,
          fluidMenuRelease(owner, armed, index = -1, enabled = false),
        )
        // Su una voce disabilitata.
        assertEquals(
          "owner=$owner armed=$armed",
          FluidMenuRelease.StayOpen,
          fluidMenuRelease(owner, armed, index = 1, enabled = false),
        )
      }
    }
  }

  @Test
  fun withoutAnOwnerThereIsNoFingerToPick() {
    assertEquals(
      FluidMenuRelease.StayOpen,
      fluidMenuRelease(FluidMenuOwner.None, armed = true, index = 0, enabled = true),
    )
  }

  @Test
  fun theHandOffArmsOnlyBeyondTheSlop() {
    val origin = Offset(100f, 100f)
    assertFalse(fluidMenuHandOffArmed(origin, origin, touchSlop = 10f))
    assertFalse(fluidMenuHandOffArmed(origin, Offset(106f, 108f), touchSlop = 10f)) // distanza 10: esattamente lo slop
    assertTrue(fluidMenuHandOffArmed(origin, Offset(107f, 108f), touchSlop = 10f))
    assertTrue(fluidMenuHandOffArmed(origin, Offset(100f, 130f), touchSlop = 10f))
  }

  @Test
  fun aHoverTicksOnlyWhenMovingToAnotherEnabledItem() {
    // Da una voce a un'altra abilitata: tick.
    assertTrue(fluidMenuHoverTicks(previous = 0, next = 1, onDown = false, enabled = true))
    // Entrare da fuori su una voce abilitata: tick (e' un'altra voce rispetto a "nessuna").
    assertTrue(fluidMenuHoverTicks(previous = -1, next = 2, onDown = false, enabled = true))
    // Restare sulla stessa: niente.
    assertFalse(fluidMenuHoverTicks(previous = 1, next = 1, onDown = false, enabled = true))
    // Uscire da tutte: niente.
    assertFalse(fluidMenuHoverTicks(previous = 1, next = -1, onDown = false, enabled = false))
    // Una voce disabilitata: niente.
    assertFalse(fluidMenuHoverTicks(previous = 0, next = 1, onDown = false, enabled = false))
    // Il down di un tocco nel pannello: mai, nemmeno su una voce abilitata.
    assertFalse(fluidMenuHoverTicks(previous = -1, next = 1, onDown = true, enabled = true))
  }
}
