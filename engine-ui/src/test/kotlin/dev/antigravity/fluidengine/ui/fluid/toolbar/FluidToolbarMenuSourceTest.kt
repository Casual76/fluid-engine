package dev.antigravity.fluidengine.ui.fluid.toolbar

import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerState
import dev.antigravity.fluidengine.ui.fluid.FluidGlassPiece
import dev.antigravity.fluidengine.ui.fluid.FluidMenuAnchor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidToolbarMenuSourceTest {
  @Test fun `un menu singolo torna al pezzo gia registrato e con il contenuto a riposo`() {
    val glass = FluidToolbarGlassId(FluidToolbarGroupId(setOf("allega")))
    val source = FluidToolbarMenuSource()
    val plane = FluidGlassContainerState().apply { renders = true }
    val piece = FluidGlassPiece(plane)
    plane.attach(piece)
    piece.content.rest()
    val anchor = FluidMenuAnchor()
    anchor.groupLease = { consumed -> source.transfer(glass, anchor, consumed) }

    anchor.consume(true)
    assertSame(anchor, source.anchor(glass))
    assertFalse("il menu cede il disegno, il pezzo resta iscritto", source.modalOwns(glass))

    anchor.consume(false)
    assertNull(source.anchor(glass))
    assertTrue(piece.content.attached)
    assertFalse(piece.content.hidden)
    assertNull(piece.content.flight.value)
  }
}
