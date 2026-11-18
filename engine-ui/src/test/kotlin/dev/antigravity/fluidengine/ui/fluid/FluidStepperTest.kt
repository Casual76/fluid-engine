package dev.antigravity.fluidengine.ui.fluid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le funzioni pure dello stepper (F6.6): il passo dentro i capi, la meta' toccata, la ripetizione. Il tocco vero, col
 * vetro che cresce e si accende, si guarda sul dispositivo (`riprese.md` di F6.6).
 */
class FluidStepperTest {

  @Test
  fun `un passo in su e uno in giu', dentro i capi`() {
    assertEquals(4, fluidStepperNext(3, 1, 0..10, up = true))
    assertEquals(2, fluidStepperNext(3, 1, 0..10, up = false))
    assertEquals(10, fluidStepperNext(9, 5, 0..10, up = true))
    assertEquals(0, fluidStepperNext(2, 5, 0..10, up = false))
  }

  @Test
  fun `al capo la meta' non fa niente`() {
    assertFalse(fluidStepperCanStep(10, 1, 0..10, up = true))
    assertTrue(fluidStepperCanStep(10, 1, 0..10, up = false))
    assertFalse(fluidStepperCanStep(0, 1, 0..10, up = false))
  }

  @Test
  fun `senza capi il conto non gira`() {
    val all = Int.MIN_VALUE..Int.MAX_VALUE
    assertEquals(Int.MAX_VALUE, fluidStepperNext(Int.MAX_VALUE - 1, 5, all, up = true))
    assertEquals(Int.MIN_VALUE, fluidStepperNext(Int.MIN_VALUE + 1, 5, all, up = false))
    assertFalse(fluidStepperCanStep(Int.MAX_VALUE, 1, all, up = true))
  }

  @Test
  fun `un valore fuori dai capi ci torna al primo passo`() {
    assertEquals(10, fluidStepperNext(40, 1, 0..10, up = false))
    assertEquals(0, fluidStepperNext(-7, 1, 0..10, up = true))
  }

  @Test
  fun `la meta' sinistra e' il meno, e da destra a sinistra si scambiano`() {
    assertEquals(FluidStepperSide.Decrement, fluidStepperSide(10f, 188f, rtl = false))
    assertEquals(FluidStepperSide.Increment, fluidStepperSide(150f, 188f, rtl = false))
    assertEquals(FluidStepperSide.Increment, fluidStepperSide(10f, 188f, rtl = true))
    assertEquals(FluidStepperSide.Decrement, fluidStepperSide(150f, 188f, rtl = true))
  }

  @Test
  fun `tenuto premuto accelera, e mai sotto i 50 ms`() {
    assertEquals(150L, fluidStepperRepeatDelayMillis(0))
    assertEquals(100L, fluidStepperRepeatDelayMillis(5))
    assertEquals(50L, fluidStepperRepeatDelayMillis(20))
    var previous = Long.MAX_VALUE
    for (n in 0..100) {
      val d = fluidStepperRepeatDelayMillis(n)
      assertTrue(d <= previous)
      assertTrue(d >= 50L)
      previous = d
    }
  }

  @Test
  fun `la capsula ha le misure di UIStepper`() {
    assertEquals(94f, FluidStepperWidth.value, 0f)
    assertEquals(32f, FluidStepperHeight.value, 0f)
  }
}
