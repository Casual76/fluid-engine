package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FluidNotificationSourceTest {
  private fun TestScope.host() = FluidNotificationHostState().also { state ->
    state.setHostActive(true)
    backgroundScope.launch { state.runQueue({ it.durationMillis }, { testScheduler.currentTime }) }
  }

  @Test fun landsBelowTheTopSourceAndAboveTheBottomSource() {
    val room = Size(800f, 600f)
    val card = Size(400f, 120f)
    val top = fluidNotificationSourceFrame(Rect(680f, 12f, 728f, 60f), card, room, 12f, 10f)
    val bottom = fluidNotificationSourceFrame(Rect(40f, 538f, 88f, 586f), card, room, 12f, 10f)
    assertEquals(Rect(388f, 70f, 788f, 190f), top)
    assertEquals(Rect(12f, 408f, 412f, 528f), bottom)
  }

  @Test fun keepsTheCardInTheViewportInCompactSpace() {
    val frame = fluidNotificationSourceFrame(
      Rect(40f, 8f, 88f, 56f), Size(96f, 99f), Size(100f, 100f), 12f, 10f,
    )
    assertEquals(Rect(2f, 1f, 98f, 100f), frame)
  }

  @Test fun undoPresetExecutesOneActionForAllObservers() = runTest {
    val state = host()
    var undos = 0
    val notice = FluidNotification.undo("In corso", 4, { undos++ }, id = "invia")
    val result = backgroundScope.async { state.showForResult(notice) }
    val duplicate = backgroundScope.async { state.showForResult(notice) }
    runCurrent()
    state.markPresented(notice.id)
    repeat(2) { state.performAction(notice.id, notice.inlineActions.single()) }
    runCurrent()
    assertEquals(1, undos)
    assertEquals(FluidNotificationResult.ActionPerformed("Annulla"), result.await())
    assertEquals(result.await(), duplicate.await())
  }

  @Test fun elapsedRingAndApplicationExpiryDoNotResetTheReadingBudget() = runTest {
    val state = host()
    val window = FluidNotificationCountdown(durationMillis = 2_000, startedAtElapsedRealtimeMillis = 100L)
    assertEquals(1f, window.fractionAt(0), 0f)
    assertEquals(0.5f, window.fractionAt(1_100), 0f)
    assertEquals(0f, window.fractionAt(2_100), 0f)
    assertEquals(0f, window.fractionAt(10_000), 0f)
    var undos = 0
    val undo = FluidNotificationAction("Annulla", { undos++ }, window)
    val notice = FluidNotification("invia", "In corso", "Fra poco", durationMillis = 3_500, inlineActions = listOf(undo))
    val result = backgroundScope.async { state.showForResult(notice) }
    runCurrent()
    state.markPresented(notice.id)
    advanceTimeBy(2_100)
    assertTrue(state.updateCurrent(notice.copy(title = "Inviata", inlineActions = emptyList(), durationMillis = 20_000)))
    state.performAction(notice.id, undo)
    assertEquals(0, undos)
    assertFalse(result.isCompleted)
    advanceTimeBy(1_400)
    runCurrent()
    assertEquals(FluidNotificationResult.TimedOut, result.await())
  }

  @Test fun bulkReplacementKeepsOneUndoForTheCombinedOperation() = runTest {
    val state = host()
    val restored = mutableListOf<Int>()
    val old = FluidNotification.undo("Eliminata una voce", 4, { restored += 1 }, group = "elimina", id = "uno")
    val new = FluidNotification.undo("Eliminate due voci", 4, { restored += listOf(1, 2) }, group = "elimina", id = "due")
    val oldResult = backgroundScope.async { state.showForResult(old) }
    runCurrent()
    state.markPresented(old.id)
    val newResult = backgroundScope.async { state.showForResult(new) }
    runCurrent()
    assertEquals(FluidNotificationResult.Replaced, oldResult.await())
    assertEquals(new.id, state.currentNotification?.id)
    assertEquals(1, state.currentNotification?.inlineActions?.size)
    state.performAction(old.id, old.inlineActions.single())
    assertTrue(restored.isEmpty())
    repeat(2) { state.performAction(new.id, new.inlineActions.single()) }
    runCurrent()
    assertEquals(listOf(1, 2), restored)
    assertEquals(FluidNotificationResult.ActionPerformed("Annulla"), newResult.await())
  }
}
