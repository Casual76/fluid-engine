package dev.antigravity.fluidengine.viewer.archive

import dev.antigravity.fluidengine.ui.page.FluidPageMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveBackdropTest {
  @Test fun washUsesTheLateOpeningWindowAndDisappearsForBothBackPaths() {
    assertEquals(1f, archiveBackdropAlpha(null, 0f), 0f)
    assertEquals(1f, archiveBackdropAlpha(FluidPageMove.None, 0f), 0f)
    assertEquals(0f, archiveBackdropAlpha(FluidPageMove.Push, .88f), 0f)
    assertTrue(archiveBackdropAlpha(FluidPageMove.Push, .94f) in .49f.. .51f)
    assertEquals(1f, archiveBackdropAlpha(FluidPageMove.Push, 1f), 0f)
    for (close in listOf(FluidPageMove.Back, FluidPageMove.Pop)) {
      for (position in listOf(0f, .25f, .5f, .75f, 1f)) {
        assertEquals(0f, archiveBackdropAlpha(close, position), 0f)
      }
    }
  }
}
