package dev.antigravity.fluidengine.ui.fluidphysics

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La guardia sulla trappola della densità: lo scope degli effetti può lavorare a risoluzione
 * ridotta, e ogni pixel di geometria che entra negli uniform deve passare per il fattore
 * scopeSize/layoutSize — o la lente scivola rispetto alla sagoma proprio quando la superficie
 * prova a risparmiare.
 */
class UniformScaleMappingTest {

  @Test
  fun geometryLandsInScopeSpaceUnderAHalvedCapture() {
    // backdropResolution 0.5: uno scope largo la metà del layout.
    val factor = scopeFactor(scopeWidth = 540f, layoutWidth = 1080f)
    assertEquals(0.5f, factor, 0.0001f)
    assertEquals(100f, toScopeSpace(200f, factor), 0.0001f)
    assertEquals(12f, toScopeSpace(24f, factor), 0.0001f)
  }

  @Test
  fun aFullResolutionScopeIsTheIdentity() {
    assertEquals(1f, scopeFactor(1080f, 1080f), 0.0001f)
    assertEquals(37.5f, toScopeSpace(37.5f, 1f), 0f)
  }

  @Test
  fun degenerateSizesFallBackToTheIdentityInsteadOfPoisoningTheUniforms() {
    assertEquals(1f, scopeFactor(540f, 0f), 0f)
    assertEquals(1f, scopeFactor(Float.NaN, 1080f), 0f)
    assertEquals(1f, scopeFactor(0f, 1080f), 0f)
  }
}
