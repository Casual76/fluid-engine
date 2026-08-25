package dev.antigravity.fluidengine.ui.fluidphysics

import org.junit.Assert.assertEquals
import org.junit.Test

class FluidPhysicsTierTest {

  @Test
  fun theDeviceCeilingIsATruthTable() {
    assertEquals(
      FluidPhysicsTier.Full,
      resolvePhysicsTier(renderEffectSupported = true, runtimeShaderSupported = true, shadersHealthy = true, requested = null),
    )
    assertEquals(
      FluidPhysicsTier.Balanced,
      resolvePhysicsTier(renderEffectSupported = true, runtimeShaderSupported = false, shadersHealthy = false, requested = null),
    )
    // Uno shader rifiutato dal driver vale come non averlo: Balanced, non un crash.
    assertEquals(
      FluidPhysicsTier.Balanced,
      resolvePhysicsTier(renderEffectSupported = true, runtimeShaderSupported = true, shadersHealthy = false, requested = null),
    )
    assertEquals(
      FluidPhysicsTier.Lite,
      resolvePhysicsTier(renderEffectSupported = false, runtimeShaderSupported = false, shadersHealthy = false, requested = null),
    )
  }

  @Test
  fun aRequestCanOnlyStepDownNeverUp() {
    // Scendere è legittimo: il Playground lo usa per far vedere i livelli.
    assertEquals(
      FluidPhysicsTier.Balanced,
      resolvePhysicsTier(true, true, true, requested = FluidPhysicsTier.Balanced),
    )
    assertEquals(
      FluidPhysicsTier.Lite,
      resolvePhysicsTier(true, true, true, requested = FluidPhysicsTier.Lite),
    )
    // Salire no: Full su un dispositivo senza RuntimeShader è una richiesta di crash.
    assertEquals(
      FluidPhysicsTier.Balanced,
      resolvePhysicsTier(true, false, false, requested = FluidPhysicsTier.Full),
    )
    assertEquals(
      FluidPhysicsTier.Lite,
      resolvePhysicsTier(false, false, false, requested = FluidPhysicsTier.Full),
    )
  }
}
