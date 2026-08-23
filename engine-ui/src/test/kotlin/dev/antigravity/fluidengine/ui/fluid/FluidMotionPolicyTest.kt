package dev.antigravity.fluidengine.ui.fluid

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidMotionPolicyTest {

  @Test
  fun `zero animator scale disables decorative and elastic movement`() {
    val policy = FluidMotionPolicy(durationScale = 0f)

    assertTrue(policy.reducedMotion)
    assertFalse(policy.allowDecorativeMotion)
    assertFalse(policy.allowElasticity)
  }

  @Test
  fun `non zero animator scales preserve motion`() {
    listOf(0.5f, 1f, 2f).forEach { scale ->
      val policy = FluidMotionPolicy(durationScale = scale)
      assertFalse(policy.reducedMotion)
      assertTrue(policy.allowDecorativeMotion)
      assertTrue(policy.allowElasticity)
    }
  }
}
