package dev.antigravity.fluidengine.ui.fluid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** F8.4 · L'intervallo della selezione trascinando, senza dito: chi decide, come si allarga e come si stringe. */
class F8p4DragSelectRangeTest {

  private val chosen = HashSet<Int>()

  private fun range(anchor: Int) = FluidDragSelectRange(
    anchor = anchor,
    adding = anchor !in chosen,
    keyAt = { if (it in 0 until 100) it else null },
    isSelected = { it in chosen },
    setSelected = { key, on -> if (on) chosen += key else chosen -= key },
  )

  @Test
  fun `il primo elemento non scelto fa aggiungere, e l'intervallo si allarga e si stringe`() {
    chosen += 6
    val r = range(2)
    assertTrue(r.adding)
    assertEquals(setOf(2, 6), chosen)
    r.extendTo(8)
    assertEquals((2..8).toSet(), chosen)
    // Indietro: chi esce torna com'era prima del gesto (il 6 era scelto e resta scelto, il 7 no).
    r.extendTo(5)
    assertEquals(setOf(2, 3, 4, 5, 6), chosen)
    // Oltre l'ancora dall'altra parte: l'intervallo gira attorno all'ancora.
    r.extendTo(0)
    assertEquals(setOf(0, 1, 2, 6), chosen)
  }

  @Test
  fun `il primo elemento scelto fa togliere, e tornando indietro gli altri tornano scelti`() {
    chosen += (0..9).toSet()
    val r = range(3)
    assertTrue(!r.adding)
    r.extendTo(6)
    assertEquals(setOf(0, 1, 2, 7, 8, 9), chosen)
    r.extendTo(4)
    assertEquals(setOf(0, 1, 2, 5, 6, 7, 8, 9), chosen)
  }

  @Test
  fun `le voci senza chiave non si toccano e lo stesso elemento non cambia niente`() {
    val r = FluidDragSelectRange(
      anchor = 0,
      adding = true,
      keyAt = { if (it % 4 == 0) null else it },
      isSelected = { it in chosen },
      setSelected = { key, on -> if (on) chosen += key else chosen -= key },
    )
    assertEquals(0, r.extendTo(0))
    assertEquals(6, r.extendTo(8))
    assertEquals(setOf(1, 2, 3, 5, 6, 7), chosen)
  }
}
