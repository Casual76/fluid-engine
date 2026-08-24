package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidGlassControlsTest {

  @Test
  fun iconButton_keepsA48DpHitTargetAroundThe44DpLens() {
    assertTrue(FluidGlassIconTouchTargetSize >= 48.dp)
    assertEquals(44.dp, FluidGlassIconLensSize)
    assertTrue(FluidGlassIconTouchTargetSize > FluidGlassIconLensSize)
  }
}
