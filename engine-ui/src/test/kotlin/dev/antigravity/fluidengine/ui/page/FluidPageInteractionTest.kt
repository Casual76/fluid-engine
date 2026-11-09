package dev.antigravity.fluidengine.ui.page

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FluidPageInteractionTest {
  @Test fun `i comandi seguono la pila richiesta anche prima della fine del movimento`() = runTest {
    val state = FluidPageHostState()
    state.scope = backgroundScope
    state.width = 1000f
    state.onStack(listOf("root"))
    state.onStack(listOf("root", "detail"))
    assertTrue(state.isMoving)
    assertEquals("detail", state.interactionPage)
    state.onStack(listOf("root"))
    assertTrue(state.isMoving)
    assertEquals("root", state.interactionPage)
    state.onStack(emptyList())
    assertNull(state.interactionPage)
  }
}
