package dev.antigravity.fluidengine.viewer.scene

import org.junit.Assert.assertNull
import org.junit.Test

class SceneViewerExtensionTest {
  @Test
  fun noProviderInStandaloneViewer() {
    assertNull(sceneViewerExtension)
  }
}
