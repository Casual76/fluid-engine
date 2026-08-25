package dev.antigravity.fluidengine.ui.fluid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidGlassModalStateTest {

  @Test
  fun anEntryIsOnlyEverRegisteredOnce() {
    // The portal registers inside a `DisposableEffect`, and an effect can re-run for reasons that
    // have nothing to do with the modal. Registering twice would draw it twice, one pane over the
    // other, which on a translucent material is a visibly denser rectangle rather than a duplicate.
    val state = FluidGlassModalHostState()
    val entry = FluidGlassModalEntry()
    state.register(entry)
    state.register(entry)
    assertEquals(1, state.entries.size)
  }

  @Test
  fun disposingTakesTheModalWithIt() {
    // A feature that leaves the composition must not be able to leave a pop-up stranded above the
    // app: the host draws from this list and nothing else can dismiss it once the owner is gone.
    val state = FluidGlassModalHostState()
    val entry = FluidGlassModalEntry().also { it.visible = true }
    state.register(entry)
    assertTrue(state.isPresenting)
    state.unregister(entry)
    assertFalse(state.isPresenting)
    assertNull(state.topEntry())
  }

  @Test
  fun backClosesTheInnermost_meaningTheOneOpenedLast() {
    // Not the one *declared* last. Declaration order is composition order, which has no relationship
    // to what is on top: a context menu raised from inside an open modal is declared by a row deep
    // in the tree and is nonetheless the thing the back gesture is about.
    val state = FluidGlassModalHostState()
    val outer = FluidGlassModalEntry()
    val inner = FluidGlassModalEntry()
    state.register(inner)
    state.register(outer)

    var closed: String? = null
    outer.onDismissRequest = { closed = "outer" }
    inner.onDismissRequest = { closed = "inner" }

    outer.visible = true
    outer.openedAt = state.nextSequence()
    inner.visible = true
    inner.openedAt = state.nextSequence()

    assertSame(inner, state.topEntry())
    state.dismissTop()
    assertEquals("inner", closed)

    inner.visible = false
    assertSame(outer, state.topEntry())
    state.dismissTop()
    assertEquals("outer", closed)
  }

  @Test
  fun aRegisteredButClosedEntryIsNotPresenting() {
    // Every context menu in a list registers on composition and stays registered for the life of the
    // row. If mere registration counted as open, one list would report a dozen modals and the
    // accessibility obscuring would never come back off.
    val state = FluidGlassModalHostState()
    repeat(12) { state.register(FluidGlassModalEntry()) }
    assertFalse(state.isPresenting)
    assertNull(state.topEntry())
  }

  @Test
  fun sequenceNumbersOnlyEverGoUp() {
    val state = FluidGlassModalHostState()
    val first = state.nextSequence()
    val second = state.nextSequence()
    assertTrue(second > first)
  }
}
