package dev.antigravity.fluidengine.ui.fluid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class FluidSearchSpecTest {
  @Test fun `dichiarare una ricerca non avvia callback ed e strutturale`() {
    var calls = 0
    val edit: (String) -> Unit = { calls++ }
    val submit: () -> Unit = { calls++ }
    val spec = FluidSearchSpec("query", edit, onSearch = submit)
    assertEquals(spec, FluidSearchSpec("query", edit, onSearch = submit))
    assertEquals(0, calls)
    assertEquals(FluidSearchPlacement.Automatic, spec.placement)
    assertEquals(FluidSearchBehavior.Automatic, spec.behavior)
    assertFalse(spec.active)
    assertNull(spec.suggestions)
    assertNull(spec.scopes)
  }

  @Test fun `inserimento generico e ricerca condividono contratto e scelta tastiera`() {
    val input = FluidSearchSpec("messaggio", {}, purpose = FluidSearchPurpose.Input,
      placement = FluidSearchPlacement.TopBar, expansion = FluidSearchExpansion.AboveKeyboard)
    assertEquals(FluidSearchPurpose.Input, input.purpose)
    assertEquals(FluidSearchExpansion.AboveKeyboard, input.expansion)
    assertEquals(FluidSearchExpansion.StayAtTop, input.copy(expansion = FluidSearchExpansion.StayAtTop).expansion)
  }
}
