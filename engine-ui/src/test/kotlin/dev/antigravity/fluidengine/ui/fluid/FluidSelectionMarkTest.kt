package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.graphics.Color
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidSelectionMarkTest {

  @Test
  fun `the check on a light disc is dark`() {
    assertEquals(Color.Black.copy(alpha = 0.87f), fluidSelectionCheckColor(Color.White))
    assertEquals(Color.Black.copy(alpha = 0.87f), fluidSelectionCheckColor(Color(0xFFFFE082)))
  }

  @Test
  fun `the check on a dark disc is white`() {
    assertEquals(Color.White, fluidSelectionCheckColor(Color.Black))
    assertEquals(Color.White, fluidSelectionCheckColor(Color(0xFF1B5E20)))
  }

  @Test
  fun `the check sits inside the disc with the thickness of its stroke`() {
    val p = FluidSelectionCheckPoints
    assertEquals(6, p.size)
    for (i in p.indices step 2) {
      val distance = hypot(p[i] - 0.5f, p[i + 1] - 0.5f)
      // Il raggio del disco e' 0,5: il tratto, con la sua mezza spessore, non deve uscirne.
      assertTrue(
        "il punto $i esce dal disco",
        distance + FluidSelectionCheckStroke / 2f <= 0.5f,
      )
    }
  }

  @Test
  fun `the check goes down to the elbow and up to the end`() {
    val p = FluidSelectionCheckPoints
    // (x0,y0) -> (x1,y1) scende, (x1,y1) -> (x2,y2) sale: e' una spunta, non una virgola.
    assertTrue(p[3] > p[1])
    assertTrue(p[5] < p[3])
    assertTrue(p[0] < p[2] && p[2] < p[4])
  }

  @Test
  fun `the default description says the state`() {
    assertEquals("Selezionato", FluidSelectionMarkDefaults.description(true))
    assertEquals("Non selezionato", FluidSelectionMarkDefaults.description(false))
  }
}
