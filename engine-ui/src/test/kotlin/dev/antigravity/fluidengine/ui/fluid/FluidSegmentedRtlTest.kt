package dev.antigravity.fluidengine.ui.fluid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La goccia del segmentato da destra a sinistra (3.0, fase 10).
 *
 * Il layout specchia da solo la posizione di partenza: in destra-sinistra il primo segmento sta a destra.
 * Ma la goccia si sposta con la traslazione di un layer e col dito, e nessuno dei due viene specchiato:
 * senza il segno la goccia del secondo segmento usciva dalla pista a destra, e trascinarla a destra
 * la portava verso segmenti che stavano a sinistra. Qui il conto, puro, come per `tabIndicatorTranslation`.
 */
class FluidSegmentedRtlTest {

  private val segment = 100f

  @Test
  fun `from left to right the drop moves right of where the layout put it`() {
    assertEquals(0f, segmentedDropTranslation(0f, segment, rtl = false), 0f)
    assertEquals(100f, segmentedDropTranslation(1f, segment, rtl = false), 0f)
    assertEquals(250f, segmentedDropTranslation(2.5f, segment, rtl = false), 0f)
  }

  @Test
  fun `from right to left the drop moves left of where the layout put it, which is the first segment on the right`() {
    assertEquals(0f, segmentedDropTranslation(0f, segment, rtl = true), 0f)
    assertEquals(-100f, segmentedDropTranslation(1f, segment, rtl = true), 0f)
    assertEquals(-250f, segmentedDropTranslation(2.5f, segment, rtl = true), 0f)
  }

  @Test
  fun `the two directions are exact mirrors, at every point of the travel`() {
    for (step in 0..40) {
      val value = step / 10f
      assertEquals(
        "valore $value",
        segmentedDropTranslation(value, segment, rtl = false),
        -segmentedDropTranslation(value, segment, rtl = true),
        0f,
      )
    }
  }

  @Test
  fun `a finger that moves toward the later segments moves the drop toward them, whichever way they lie`() {
    // In LTR i segmenti successivi stanno a destra; in RTL a sinistra.
    assertTrue(segmentedDragDelta(+60f, segment, rtl = false) > 0f)
    assertTrue(segmentedDragDelta(-60f, segment, rtl = true) > 0f)
    assertTrue(segmentedDragDelta(-60f, segment, rtl = false) < 0f)
    assertTrue(segmentedDragDelta(+60f, segment, rtl = true) < 0f)
  }

  @Test
  fun `a drag of one segment width is exactly one segment, in both directions`() {
    assertEquals(1f, segmentedDragDelta(segment, segment, rtl = false), 0f)
    assertEquals(1f, segmentedDragDelta(-segment, segment, rtl = true), 0f)
  }

  @Test
  fun `the drop sits under the finger after a drag, both ways`() {
    // Il dito si sposta di d px: la goccia, che parte dal primo segmento, deve spostarsi di d px nello
    // stesso verso sullo schermo. Lo schermo e' l'asse x del layer: +x a destra.
    for (rtl in listOf(false, true)) {
      val fingerMovedBy = if (rtl) -130f else 130f
      val valueAfter = segmentedDragDelta(fingerMovedBy, segment, rtl)
      val dropMovedBy = segmentedDropTranslation(valueAfter, segment, rtl)
      assertEquals("rtl=$rtl", fingerMovedBy, dropMovedBy, 0.0001f)
    }
  }

  @Test
  fun `the direction is plus or minus one and nothing else`() {
    assertEquals(1f, segmentedDirection(rtl = false), 0f)
    assertEquals(-1f, segmentedDirection(rtl = true), 0f)
  }

  // --- Il dito che parte da un altro segmento (3.0, "come iOS") ------------------------------------

  @Test
  fun `a finger in the middle of a segment puts the drop exactly on it, in both directions`() {
    // Pista di 3 segmenti da 100 con 4 px di margine: larga 308.
    assertEquals(0f, segmentedFingerValue(54f, 308f, 4f, segment, rtl = false), 0.001f)
    assertEquals(2f, segmentedFingerValue(254f, 308f, 4f, segment, rtl = false), 0.001f)
    // In RTL il primo segmento sta a destra: il dito a 254 e' sul secondo... da destra, cioe' il primo.
    assertEquals(0f, segmentedFingerValue(254f, 308f, 4f, segment, rtl = true), 0.001f)
    assertEquals(2f, segmentedFingerValue(54f, 308f, 4f, segment, rtl = true), 0.001f)
  }

  @Test
  fun `the border between two segments is half way between their values`() {
    assertEquals(0.5f, segmentedFingerValue(104f, 308f, 4f, segment, rtl = false), 0.001f)
  }

  @Test
  fun `a finger is on the drop within half a segment of its centre, and not beyond`() {
    assertTrue(segmentedTouchOnDrop(fingerValue = 1.4f, dropValue = 1f))
    assertTrue(segmentedTouchOnDrop(fingerValue = 0.5f, dropValue = 1f))
    assertTrue(!segmentedTouchOnDrop(fingerValue = 1.6f, dropValue = 1f))
    assertTrue(!segmentedTouchOnDrop(fingerValue = 0f, dropValue = 1f))
  }
}
