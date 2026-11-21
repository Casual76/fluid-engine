package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.saveable.SaverScope
import dev.antigravity.fluidengine.ui.fluid.icons.Add
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarBar
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarGroupChange
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarMetrics
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarRole
import dev.antigravity.fluidengine.ui.fluid.toolbar.buildFluidToolbar
import dev.antigravity.fluidengine.ui.fluid.toolbar.diffFluidToolbar
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidToolbarGlassTransition
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidToolbarModel
import dev.antigravity.fluidengine.ui.fluid.toolbar.resolveFluidToolbar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FluidUndoCountdownTest {
  @Test fun realClockControlsFillAnnouncementsAndDeadline() = runTest {
    val state = FluidUndoCountdownState(FluidNotificationCountdown(8_000L, 0L), { testScheduler.currentTime })
    val timer = launch { state.followDeadline() }
    runCurrent()
    assertEquals(8L, state.announcementSeconds())
    assertEquals(0f, state.fill(), 0f)
    advanceTimeBy(4_000)
    assertEquals(0.5f, state.fill(), 0f)
    assertEquals(4L, state.announcementSeconds())
    advanceTimeBy(3_999)
    assertTrue(state.isActive)
    advanceTimeBy(1)
    // Il click sul vecchio disegno viene respinto anche prima che il timer ricomponga.
    assertFalse(state.undo { error("annullamento fuori tempo") })
    runCurrent()
    assertFalse(state.isActive)
    assertEquals(1f, state.fill(), 0f)
    assertTrue(timer.isCompleted)
  }

  @Test fun undoIsAcceptedOnlyOnceIncludingReentrancyAndRestoration() {
    var now = 3_000L
    val timing = FluidNotificationCountdown(8_000L, 1_000L)
    val state = FluidUndoCountdownState(timing, { now })
    val saver = FluidUndoCountdownState.saver(timing, { now })
    val scope = SaverScope { true }
    fun restore(): FluidUndoCountdownState {
      val saved = with(saver) { scope.save(state) }
      return checkNotNull(saver.restore(checkNotNull(saved)))
    }

    now = 5_000L
    assertEquals(0.5f, restore().fill(), 0f)
    var calls = 0
    assertTrue(state.undo {
      calls++
      assertFalse(state.undo { calls++ })
    })
    assertFalse(state.undo { calls++ })
    assertFalse(restore().undo { calls++ })
    assertEquals(1, calls)
  }

  @Test fun primaryAndActionRetainIdentityAndResizeOnlyAcrossFaces() {
    var now = 500L
    val state = FluidUndoCountdownState(FluidNotificationCountdown(8_000L, 0L), { now })
    val undo = FluidUndoCountdown(state, onUndo = {})
    fun model(primary: Boolean, pending: FluidUndoCountdown?) =
      fluidToolbarModel(buildFluidToolbar(null) {
        if (primary) primary("mail", FluidIcons.Add, "Nuova mail", {}, undo = pending)
        else button("mail", FluidIcons.Add, "Nuova mail", {}, undo = pending)
      }, { 90f })

    for (primary in listOf(false, true)) {
      now = 500L
      val rest = model(primary, null)
      val active = model(primary, undo)
      val item = active.items.single()
      assertEquals(rest.items.single().id, item.id)
      assertEquals(90f, item.widthDp)
      assertNotEquals(rest.items.single().content, item.content)
      now = 4_200L
      assertEquals(active, model(primary, undo))
      now = 8_000L
      assertEquals(rest, model(primary, undo))
    }
  }

  @Test fun standaloneToolbarCounterUsesMaterialExit() {
    var now = 0L
    val state = FluidUndoCountdownState(FluidNotificationCountdown(8_000L, 0L), { now })
    val countdown = FluidUndoCountdown(state, {})
    fun resolve() = resolveFluidToolbar(
      fluidToolbarModel(buildFluidToolbar({}) {
        undoCountdown("only", countdown)
        button("regular", FluidIcons.Add, "Aggiungi", {})
      }, { 90f }),
      360f, metrics = FluidToolbarMetrics(48f, 8f, 12f, 44f), bar = FluidToolbarBar.Top,
    )
    val active = resolve()
    val counter = active.groups.single { group -> group.items.any { it.id == "only" } }
    assertTrue(active.elements.any { it.item.role == FluidToolbarRole.Back })
    assertEquals(FluidGlassTransition.MaterializeWhenAlone, fluidToolbarGlassTransition(counter))
    now = 8_000L
    val diff = diffFluidToolbar(active, resolve())
    assertEquals(FluidToolbarGroupChange.Dematerialize, diff.groups.single { counter in it.from }.change)
  }
}
