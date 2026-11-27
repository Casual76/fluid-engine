package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.glass.field.GlassAdaptiveDarkBelow
import dev.antigravity.fluidengine.ui.glass.field.GlassAdaptiveLightAbove
import dev.antigravity.fluidengine.ui.glass.field.GlassProbeStatsSide
import dev.antigravity.fluidengine.ui.glass.field.glassProbeStatsOfRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F7.33c · I lati sotto i grappoli di un gruppo e il gruppo **a cavallo** di un confine (D7, [fluidGlassGroupSides]): le
 * celle della miniatura sotto ogni grappolo, le soglie del filtro con la sua isteresi, la media solo sotto i grappoli.
 */
class FluidGlassGroupSidesTest {
  private val side = GlassProbeStatsSide
  /** La parte letta: 160 × 16 px, dieci pixel per colonna di celle. */
  private val area = Rect(0f, 0f, 160f, 16f)
  private val left = Rect(0f, 0f, 40f, 16f)
  private val middle = Rect(60f, 0f, 100f, 16f)
  private val right = Rect(120f, 0f, 160f, 16f)

  /** Le celle con la luma che [luma] dà a ogni colonna. */
  private fun cells(luma: (Int) -> Float) = FloatArray(side * side) { luma(it % side) }

  private fun sides(cells: FloatArray, clusters: List<Rect>, previous: List<Boolean?> = emptyList()) =
    fluidGlassGroupSides(cells, side, area, clusters, previous, GlassAdaptiveDarkBelow, GlassAdaptiveLightAbove)

  @Test fun uniformBackgroundIsNotAStraddle() {
    val read = sides(cells { 0.9f }, listOf(left, middle, right))
    assertEquals(listOf(false, false, false), read.sides)
    assertFalse(read.straddles)
    assertEquals(0.9f, read.luminance, 1e-4f)
  }

  @Test fun paperOnTheLeftAndTableOnTheRightIsAStraddle() {
    val read = sides(cells { if (it < 8) 1f else 0f }, listOf(left, right))
    assertEquals(listOf(false, true), read.sides)
    assertTrue(read.straddles)
  }

  @Test fun theMeanCountsOnlyTheCellsUnderTheClusters() {
    // Il bianco sotto i due grappoli, il nero nel vuoto fra loro: il gruppo sta sul chiaro, senza cavallo.
    val read = sides(cells { if (it < 4 || it >= 12) 1f else 0f }, listOf(left, right))
    assertEquals(listOf(false, false), read.sides)
    assertFalse(read.straddles)
    assertEquals(1f, read.luminance, 1e-4f)
  }

  @Test fun aClusterBetweenTheThresholdsKeepsItsSide() {
    // Il grappolo di mezzo a metà fra le due soglie: tiene il lato che aveva, e il cavallo non nasce né finisce lì.
    val mid = (GlassAdaptiveDarkBelow + GlassAdaptiveLightAbove) / 2
    val luma = cells { if (it < 4) 1f else if (it < 12) mid else 0f }
    val wasDark = sides(luma, listOf(left, middle, right), previous = listOf(false, true, true))
    assertEquals(listOf(false, true, true), wasDark.sides)
    assertTrue(wasDark.straddles)
    val wasLight = sides(luma, listOf(left, middle, right), previous = listOf(false, false, true))
    assertEquals(listOf(false, false, true), wasLight.sides)
    val unknown = sides(cells { mid }, listOf(left, right))
    assertEquals(listOf(null, null), unknown.sides)
    assertFalse("senza un lato deciso non c'è cavallo", unknown.straddles)
  }

  @Test fun theFakeReaderFillsTheCellsOfTheRegion() {
    // Il lettore finto delle prove legge cella per cella: la carta fino a x = 100 della finestra (80 della parte letta), poi il tavolo.
    val where = Rect(20f, 300f, 180f, 316f)
    val stats = glassProbeStatsOfRegion(where, { r -> if (r.center.x < 100f) Color.White else Color.Black })
    assertNotNull(stats.cells)
    val cells = stats.cells!!.values
    assertEquals(side * side, cells.size)
    assertEquals(1f, cells[0], 0.02f)
    assertEquals(0f, cells[side - 1], 0.02f)
    val read = fluidGlassGroupSides(
      cells, side, Rect(0f, 0f, where.width, where.height), listOf(left, right), emptyList(),
      GlassAdaptiveDarkBelow, GlassAdaptiveLightAbove,
    )
    assertTrue(read.straddles)
    assertEquals(0.5f, stats.luminance, 0.05f)
  }
}
