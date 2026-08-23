package dev.antigravity.fluidengine.ui.fluid

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FluidNotificationHostStateTest {

  @Test
  fun queue_isFifoAndDeduplicatesVisibleOrPendingIds() = runTest {
    val state = FluidNotificationHostState()
    state.setHostActive(true)
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      state.runQueue(
        recommendedTimeout = { 60_000L },
        monotonicTimeMillis = { testScheduler.currentTime },
      )
    }
    val first = FluidNotification(id = "year-fallback", title = "Anno", message = "Primo")
    val duplicate = first.copy(message = "Non deve entrare")
    val second = FluidNotification(id = "sync-error", title = "Sync", message = "Secondo")

    val firstDelivery = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { state.show(first) }
    val duplicateDelivery = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { state.show(duplicate) }
    val secondDelivery = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { state.show(second) }
    runCurrent()

    assertEquals(first, state.currentNotification)
    assertFalse(firstDelivery.isCompleted)

    state.markPresented(first.id)
    runCurrent()
    assertEquals(FluidNotificationDelivery.Presented, firstDelivery.await())
    assertEquals(FluidNotificationDelivery.Presented, duplicateDelivery.await())

    state.dismissCurrent()
    runCurrent()
    assertNull(state.currentNotification)

    // Advancing time cannot replace the payload while AnimatedVisibility is still exiting.
    advanceTimeBy(5_000L)
    runCurrent()
    assertNull(state.currentNotification)
    assertEquals(first, state.presentation.notification)

    state.markExitCompleted(first.id)
    runCurrent()
    assertEquals(second, state.currentNotification)
    assertEquals(second, state.presentation.notification)
    state.markPresented(second.id)
    assertEquals(FluidNotificationDelivery.Presented, secondDelivery.await())
  }

  @Test
  fun timeout_clearsTheCurrentNotification() = runTest {
    val state = FluidNotificationHostState()
    state.setHostActive(true)
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      state.runQueue(
        recommendedTimeout = { 2_000L },
        monotonicTimeMillis = { testScheduler.currentTime },
      )
    }
    val delivery = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
      state.show(FluidNotification(id = "brief", title = "Titolo", message = "Messaggio"))
    }
    runCurrent()
    assertFalse(delivery.isCompleted)

    // Time spent before the host has a real layout must not silently consume a durable notice.
    advanceTimeBy(2_000L)
    runCurrent()
    assertEquals("brief", state.currentNotification?.id)
    assertFalse(delivery.isCompleted)

    state.markPresented("brief")
    runCurrent()
    assertEquals(FluidNotificationDelivery.Presented, delivery.await())

    advanceTimeBy(2_000L)
    runCurrent()
    assertNull(state.currentNotification)
  }

  @Test
  fun inactiveHost_neitherPresentsNorStartsTheTimeout() = runTest {
    val state = FluidNotificationHostState()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      state.runQueue(
        recommendedTimeout = { 2_000L },
        monotonicTimeMillis = { testScheduler.currentTime },
      )
    }
    val delivery = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
      state.show(FluidNotification(id = "covered", title = "Titolo", message = "Messaggio"))
    }
    runCurrent()

    state.markPresented("covered")
    advanceTimeBy(10_000L)
    runCurrent()
    assertFalse(delivery.isCompleted)
    assertEquals("covered", state.currentNotification?.id)

    state.setHostActive(true)
    state.markPresented("covered")
    runCurrent()
    assertEquals(FluidNotificationDelivery.Presented, delivery.await())
  }

  @Test
  fun timeoutBudget_pausesWhileTheHostIsInactive() = runTest {
    val state = FluidNotificationHostState()
    state.setHostActive(true)
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      state.runQueue(
        recommendedTimeout = { 2_000L },
        monotonicTimeMillis = { testScheduler.currentTime },
      )
    }
    val delivery = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
      state.show(FluidNotification(id = "paused", title = "Titolo", message = "Messaggio"))
    }
    runCurrent()
    state.markPresented("paused")
    runCurrent()
    assertEquals(FluidNotificationDelivery.Presented, delivery.await())

    advanceTimeBy(750L)
    runCurrent()
    state.setHostActive(false)
    runCurrent()

    advanceTimeBy(10_000L)
    runCurrent()
    assertEquals("paused", state.currentNotification?.id)

    state.setHostActive(true)
    runCurrent()
    advanceTimeBy(1_249L)
    runCurrent()
    assertEquals("paused", state.currentNotification?.id)

    advanceTimeBy(1L)
    runCurrent()
    assertNull(state.currentNotification)
  }

  @Test
  fun invalidPayload_isRejectedWithoutEnteringTheQueue() = runTest {
    val state = FluidNotificationHostState()

    assertEquals(
      FluidNotificationDelivery.Rejected,
      state.show(FluidNotification(id = "", title = "Titolo", message = "Messaggio")),
    )
    assertNull(state.currentNotification)
  }

  @Test
  fun hostRestart_keepsTheUnpresentedCurrentRequestAheadOfPendingItems() = runTest {
    val state = FluidNotificationHostState()
    state.setHostActive(true)
    val firstRunner = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      state.runQueue(
        recommendedTimeout = { 60_000L },
        monotonicTimeMillis = { testScheduler.currentTime },
      )
    }
    val first = FluidNotification(id = "first", title = "Titolo", message = "Primo")
    val second = FluidNotification(id = "second", title = "Titolo", message = "Secondo")
    val firstDelivery = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
      state.show(first)
    }
    val secondDelivery = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
      state.show(second)
    }
    runCurrent()
    assertEquals(first, state.currentNotification)

    firstRunner.cancel()
    runCurrent()
    assertFalse(firstDelivery.isCompleted)
    assertFalse(secondDelivery.isCompleted)

    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      state.runQueue(
        recommendedTimeout = { 60_000L },
        monotonicTimeMillis = { testScheduler.currentTime },
      )
    }
    runCurrent()
    assertEquals(first, state.currentNotification)
    state.markPresented(first.id)
    assertEquals(FluidNotificationDelivery.Presented, firstDelivery.await())

    state.dismissCurrent()
    runCurrent()
    state.markExitCompleted(first.id)
    runCurrent()
    assertEquals(second, state.currentNotification)
    state.markPresented(second.id)

    assertEquals(FluidNotificationDelivery.Presented, secondDelivery.await())
  }
}
