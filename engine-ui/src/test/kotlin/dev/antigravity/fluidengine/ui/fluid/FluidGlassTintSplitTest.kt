package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.emptyBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Ordine delle pellicole rispetto alle lastre del campo. */
class FluidGlassTintSplitTest {

  private val backdrop = GlassBackdropState(emptyBackdrop(), null, 2.5.dp)
  private val neutral = GlassTint(Color(0x61FFFFFF), Color(0xE6F2F2F7), Color.Transparent, GlassTintMode.Neutral)
  private val accent = GlassTint(Color(0xFF0A84FF), Color(0xFF0A84FF), Color.Transparent, GlassTintMode.Colored)

  private fun look(tint: GlassTint) = fluidGlassPanelLook(
    backdrop, tint, FluidCapsuleShape, GlassRole.Interactive, GlassDefaults.buttonOptics(), null, null, 0L,
  )

  @Test
  fun `una tinta sola non divide niente`() {
    assertNull(fluidGlassTintSplit(listOf(look(neutral), look(neutral), look(neutral))))
    assertNull("un pezzo solo", fluidGlassTintSplit(listOf(look(accent))))
  }

  @Test
  fun `le lastre conservano la propria pellicola anche quando una tinta si ripete`() {
    val split = fluidGlassTintSplit(listOf(look(neutral), look(accent), look(neutral)))!!
    assertEquals(listOf(neutral, accent), split.looks.map { it.tint })
    assertEquals(listOf(0, 1, 0), split.slabTints.toList())
  }

}
