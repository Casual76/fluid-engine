package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import dev.antigravity.fluidengine.ui.glass.field.glassMenuLensOverflow
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le regole pure delle correzioni dopo S4f: la scelta col dito fermo (NO 4) e il tiro della lente (NO 2). */
class FluidMenuS4fRulesTest {
  private val panel = Rect(100f, 100f, 400f, 300f)
  private val geometry = FluidMenuGeometry(
    panel = panel,
    cells = List(4) { Rect(panel.left, panel.top + it * 50f, panel.right, panel.top + (it + 1) * 50f) },
    enabled = listOf(true, true, false, true),
  )
  private val slop = 18f

  @Test
  fun theLitItemHoldsWhileTheMenuMovesUnderAStillFinger() {
    val at = Offset(200f, 120f)
    // Il menu si muove (la mappa non e' l'identita'): un dito fermo, o che trema, tiene la voce accesa.
    assertTrue(fluidMenuHoverHolds(hovered = 0, hoverAt = at, root = at, touchSlop = slop, moving = true))
    assertTrue(fluidMenuHoverHolds(0, at, at + Offset(6f, -5f), slop, moving = true))
    // Il dito che si sposta davvero, il menu fermo, o nessuna voce accesa: si guarda di nuovo sotto il dito.
    assertFalse(fluidMenuHoverHolds(0, at, at + Offset(0f, 40f), slop, moving = true))
    assertFalse(fluidMenuHoverHolds(0, at, at, slop, moving = false))
    assertFalse(fluidMenuHoverHolds(-1, at, at, slop, moving = true))
  }

  @Test
  fun theReleasePicksTheLitItemThenWhatIsSeenThenWhatIsArriving() {
    val at = Offset(200f, 120f)
    // 1. La voce accesa, col dito ancora li': anche se ora sotto il dito si vedrebbe un'altra voce (il corpo e' cresciuto).
    assertEquals(0, fluidMenuReleaseCell(geometry, hovered = 0, hoverAt = at, root = at, atRest = Offset(200f, 170f), touchSlop = slop))
    // 2. Il dito si e' spostato: la voce che si vede sotto di lui.
    assertEquals(1, fluidMenuReleaseCell(geometry, 0, at, Offset(200f, 175f), Offset(200f, 170f), slop))
    // 3. Sotto il dito non si vede niente (il menu e' ancora piccolo): la voce della posa che arriva li'.
    assertEquals(3, fluidMenuReleaseCell(geometry, -1, null, Offset(200f, 280f), Offset(200f, 420f), slop))
    // A menu fermo (la mappa e' l'identita') fuori dal pannello non si sceglie niente.
    assertEquals(-1, fluidMenuReleaseCell(geometry, -1, null, Offset(200f, 420f), Offset(200f, 420f), slop))
    // Una voce accesa ma spenta nel frattempo non si sceglie: si guarda sotto il dito.
    assertEquals(1, fluidMenuReleaseCell(geometry, 2, Offset(200f, 160f), Offset(200f, 160f), Offset(200f, 160f), slop))
  }

  @Test
  fun theLensPullsInWhatTheGlassDoesNotHold() {
    // Lo strato delle voci a (90, 80), 300 x 200 scalato di 1,4: esce dal vetro (120..380 x 110..300) da ogni lato.
    val o = glassMenuLensOverflow(Rect(120f, 110f, 380f, 300f), Offset(90f, 80f), Size(300f, 200f), 1.4f)
    assertArrayEquals(floatArrayOf(30f / 1.4f, 30f / 1.4f, (510f - 380f) / 1.4f, (360f - 300f) / 1.4f), o, 1e-3f)
    // Dentro il vetro: niente tiro, la lente di prima.
    assertArrayEquals(FloatArray(4), glassMenuLensOverflow(Rect(0f, 0f, 1000f, 1000f), Offset(90f, 80f), Size(300f, 200f), 1.4f), 0f)
  }
}
