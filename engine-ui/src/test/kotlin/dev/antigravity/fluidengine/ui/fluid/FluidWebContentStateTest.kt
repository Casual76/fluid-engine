package dev.antigravity.fluidengine.ui.fluid

import org.junit.Assert.*
import org.junit.Test

class FluidWebContentStateTest {
  @Test fun emptyLoadingPaintErrorAndRelease() {
    val state = FluidWebContentState()
    val owner = Any()
    assertEquals(FluidWebPhase.Empty, state.phase)
    assertNull(state.printAdapter("test"))
    state.attach(owner, true)
    assertEquals(FluidWebPhase.Loading, state.phase)
    state.ready(owner)
    assertEquals(FluidWebPhase.Ready, state.phase)
    state.fail(owner, FluidWebFailure.RendererGone)
    state.ready(owner) // Una callback di pittura tardiva non cancella l'errore.
    assertEquals(FluidWebPhase.Error, state.phase)
    assertEquals(FluidWebFailure.RendererGone, state.failure)
    assertNull(state.printAdapter("test"))
    state.detach(owner)
    assertEquals(FluidWebPhase.Empty, state.phase)
    assertNull(state.failure)
  }

  @Test fun callbacksFromDisposedRendererCannotChangeNewDocument() {
    val state = FluidWebContentState()
    val old = Any()
    val current = Any()
    state.attach(old, true)
    state.detach(old)
    state.attach(current, true)
    state.fail(old, FluidWebFailure.RendererGone)
    state.ready(old)
    state.detach(old)
    assertEquals(FluidWebPhase.Loading, state.phase)
    state.ready(current)
    assertEquals(FluidWebPhase.Ready, state.phase)
  }

  @Test fun retryCreatesNewRevisionAndNewOwnerResetsMeasurements() {
    val state = FluidWebContentState()
    val owner = Any()
    state.attach(owner, true)
    state.contentHeightPx = 100_000
    state.scrollsInternally = true
    state.fail(owner, FluidWebFailure.RendererGone)
    state.retry()
    assertEquals(1, state.revision)
    state.detach(owner)
    state.attach(Any(), true)
    assertEquals(0, state.contentHeightPx)
    assertFalse(state.scrollsInternally)
    assertNull(state.failure)
  }

  @Test(expected = IllegalStateException::class)
  fun stateCannotOwnTwoViews() {
    FluidWebContentState().apply { attach(Any(), true); attach(Any(), true) }
  }

  @Test fun blankContentDoesNotEnterLoading() {
    FluidWebContentState().apply { attach(Any(), false); assertEquals(FluidWebPhase.Empty, phase) }
  }
}
