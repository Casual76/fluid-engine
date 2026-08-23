package dev.antigravity.fluidengine.foundation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineVersionTest {

  @Test
  fun `versions compare segment by segment, not as text`() {
    assertTrue(compareVersions("1.10.0", "1.9.0") > 0)
    assertTrue(compareVersions("2.0.0", "10.0.0") < 0)
    assertEquals(0, compareVersions("1.2", "1.2.0"))
  }

  @Test
  fun `a pre-release comes before the release it leads to`() {
    assertTrue(compareVersions("1.2.0-beta1", "1.2.0") < 0)
    assertTrue(compareVersions("1.2.0-beta2", "1.2.0-beta1") > 0)
  }

  @Test
  fun `the stable channel never offers a pre-release`() {
    assertTrue(isStableVersionNewer("1.3.0", "1.2.0"))
    assertFalse(isStableVersionNewer("1.3.0-beta1", "1.2.0"))
    assertFalse("the same version is not an update", isStableVersionNewer("1.2.0", "1.2.0"))
    assertFalse(isStableVersionNewer("1.1.0", "1.2.0"))
  }

  @Test
  fun `nonsense segments do not throw`() {
    assertEquals(0, compareVersions("", ""))
    assertTrue(compareVersions("1.0.0", "not-a-version") > 0)
  }
}
