package dev.antigravity.fluidengine.ui.fluidphysics

import org.junit.Assert.assertEquals
import org.junit.Test

class FluidPhysicsTierTest {

  @Test
  fun theMenuPanelPrefersAboveAndStaysInsideTheHost() {
    val host = androidx.compose.ui.geometry.Rect(0f, 0f, 1080f, 2200f)
    // Un tasto in basso: il menù cresce verso l'alto, dentro i margini.
    val anchorLow = androidx.compose.ui.geometry.Rect(700f, 2000f, 940f, 2110f)
    val above = solveMorphMenuPanel(anchorLow, host, 3, 130f, 700f, 22f, 32f, 44f)
    assertEquals(2000f - 32f - (3 * 130f + 44f), above.top, 0.001f)
    org.junit.Assert.assertTrue(above.left >= 44f && above.right <= 1080f - 44f)
    // Il centro del pannello insegue quello del tasto finché i margini lo lasciano fare.
    assertEquals((1080f - 44f - 700f), above.left, 0.001f)

    // Un tasto in alto senza spazio sopra: il menù scende sotto.
    val anchorHigh = androidx.compose.ui.geometry.Rect(100f, 60f, 340f, 170f)
    val below = solveMorphMenuPanel(anchorHigh, host, 3, 130f, 700f, 22f, 32f, 44f)
    assertEquals(170f + 32f, below.top, 0.001f)
  }

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
