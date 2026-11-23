package dev.antigravity.fluidengine.foundation.memory

import dev.antigravity.fluidengine.foundation.memory.FluidMemoryCategory.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FluidMemoryBudgetTest {
  private val limits = FluidMemoryLimits(100, 40, 20, 30, 50)
  private val budget = FluidMemoryBudget(limits)

  @Test fun eachCategoryOwnsItsQuota() {
    for (category in FluidMemoryCategory.entries) {
      val grant = budget.tryReserve(category, limits[category])!!
      assertNull(budget.tryReserve(category, 1))
      assertEquals(limits[category], budget.usage(category).peakBytes)
      assertEquals(1L, budget.usage(category).refusals)
      grant.close()
    }
    assertEquals(4L, budget.usage().refusals)
    assertEquals(0L, budget.usage().usedBytes)
  }

  @Test fun categoriesCompeteForProcessHeadroomAndCanReacquireIt() {
    budget.updateLimits(limits.copy(processBytes = 60))
    val first = budget.tryReserve(GLASS, 40)!!
    assertNull(budget.tryReserve(VIEWER, 30))
    val second = budget.tryReserve(THUMBNAILS, 20)!!
    first.close()
    val page = budget.tryReserve(VIEWER, 40)!!
    assertEquals(60L, budget.usage().peakBytes)
    second.close()
    page.close()
    assertEquals(0L, budget.usage().usedBytes)
  }

  @Test fun cacheAndScreenShareOneChargeAndCannotResurrectAClosedOwner() {
    val work = budget.tryReserve(VIEWER, 32)!!
    val cache = work.confirm()!!
    assertEquals(0L, budget.usage().inFlightBytes)
    val screen = cache.retain()
    work.close()
    cache.close()
    cache.close()
    assertThrows(IllegalStateException::class.java) { cache.retain() }
    assertThrows(IllegalStateException::class.java) { work.confirm() }
    assertEquals(32L, budget.usage().residentBytes)
    assertEquals(32L, budget.usage().peakBytes)
    screen.close()
    screen.close()
    assertEquals(0L, budget.usage(VIEWER).usedBytes)
  }

  @Test fun trimReportsTheMinimumRecoveryWithoutDiscardingLiveBytes() {
    val cache = budget.tryReserve(OTHER_CACHE, 20)!!.confirm()!!
    val thumb = budget.tryReserve(THUMBNAILS, 30)!!
    assertEquals(30L, budget.updateLimits(limits.copy(otherCacheBytes = 5, thumbnailBytes = 15)))
    assertEquals(15L, budget.usage(THUMBNAILS).excessBytes)
    assertEquals(35L, budget.updateLimits(limits.copy(processBytes = 15, otherCacheBytes = 5, thumbnailBytes = 15)))
    assertEquals(20L, budget.usage().residentBytes)
    assertNull(budget.tryReserve(VIEWER, 1))
    // Il lavoro ammesso prima del trim può finire con lo stesso costo. La pubblicazione resta del chiamante.
    val live = thumb.confirm()!!
    assertEquals(50L, budget.usage().residentBytes)
    live.close()
    cache.close()
    budget.tryReserve(VIEWER, 15)!!.close()
    assertEquals(0L, budget.usage().usedBytes)
  }

  @Test fun measuredCostReturnsOrAcquiresOnlyTheDifference() {
    val first = budget.tryReserve(GLASS, 30)!!.confirm(8)!!
    val second = budget.tryReserve(GLASS, 20)!!.confirm(32)!!
    assertEquals(40L, budget.usage().residentBytes)
    assertEquals(40L, budget.usage().peakBytes)
    assertEquals(0L, budget.usage().inFlightBytes)
    first.close()
    second.close()
    assertEquals(0L, budget.usage().usedBytes)
  }

  @Test fun unexpectedOversizeStaysChargedUntilItIsDiscarded() {
    val work = budget.tryReserve(VIEWER, 40)!!
    assertNull(work.confirm(105))
    assertEquals(105L, budget.usage().inFlightBytes)
    assertEquals(0L, budget.usage().residentBytes)
    assertEquals(55L, budget.usage(VIEWER).excessBytes)
    assertEquals(5L, budget.usage().excessBytes)
    assertNull(budget.tryReserve(THUMBNAILS, 1))
    assertThrows(IllegalStateException::class.java) { work.confirm() }
    work.close()
    work.close()
    assertEquals(105L, budget.usage().peakBytes)
    assertEquals(0L, budget.usage().usedBytes)
    budget.tryReserve(VIEWER, 50)!!.close()
  }

  @Test fun invalidAndUnrepresentableMeasurementsPreserveCredit() {
    assertThrows(IllegalArgumentException::class.java) { budget.tryReserve(GLASS, -1) }
    assertThrows(IllegalArgumentException::class.java) { budget.tryReserve(GLASS, 0) }
    val work = budget.tryReserve(VIEWER, 2)!!
    budget.tryReserve(GLASS, 1)!!.use {
      assertThrows(IllegalArgumentException::class.java) { work.confirm(Long.MAX_VALUE) }
      assertThrows(IllegalArgumentException::class.java) { work.confirm(0) }
      assertEquals(3L, budget.usage().usedBytes)
    }
    work.confirm(1)!!.close()
    assertEquals(0L, budget.usage().usedBytes)
  }

  @Test fun earlyReturnExceptionAndCancellationReleaseInTheProducer() {
    fun abandon(): Int = budget.tryReserve(THUMBNAILS, 12)!!.use { return 17 }
    assertEquals(17, abandon())
    for (failure in listOf(IllegalArgumentException("decode"), CancellationException("cancel"))) {
      val caught = assertThrows(RuntimeException::class.java) {
        budget.tryReserve(THUMBNAILS, 30)!!.use { throw failure }
      }
      assertSame(failure, caught)
      assertEquals(0L, budget.usage().usedBytes)
    }
  }

  @Test fun cancelledNativeWorkKeepsCreditAndCannotPublishAfterTrim() {
    val entered = CountDownLatch(1)
    val finish = CountDownLatch(1)
    val decoder = Executors.newSingleThreadExecutor()
    val publicationLock = Any()
    var generation = 0
    var cached: FluidMemoryBudget.Resident? = null
    try {
      val requested = synchronized(publicationLock) { generation }
      val task = CompletableFuture.runAsync({
        budget.tryReserve(VIEWER, 50)!!.use { work ->
          entered.countDown()
          check(finish.await(10, TimeUnit.SECONDS))
          synchronized(publicationLock) {
            if (generation == requested) cached = work.confirm()
          }
        }
      }, decoder)
      val waiter = task.thenRun { }
      assertTrue(entered.await(10, TimeUnit.SECONDS))
      assertTrue(waiter.cancel(false))
      synchronized(publicationLock) {
        generation++
        budget.updateLimits(limits.copy(viewerBytes = 8))
      }
      assertEquals(50L, budget.usage(VIEWER).inFlightBytes)
      assertNull(budget.tryReserve(VIEWER, 8))
      finish.countDown()
      task.get(10, TimeUnit.SECONDS)
      assertNull(cached)
      assertEquals(0L, budget.usage().usedBytes)
      budget.tryReserve(VIEWER, 8)!!.close()
    } finally {
      finish.countDown()
      decoder.shutdownNow()
    }
  }

  @Test fun twoProtectedTexturesFallBackAfterEviction() {
    val memory = FluidMemoryBudget(FluidMemoryLimits.Protected.forHeap(128L * 1024 * 1024))
    val one = fluidSurfaceBytes(1080, 2340, 4)
    val pair = fluidSurfaceBytes(1080, 2340, 4, copies = 2)
    val smaller = fluidSurfaceBytes(540, 1170, 4, copies = 2)
    assertEquals(20_217_600L, pair)
    assertEquals(5_054_400L, smaller)
    assertNull(memory.tryReserve(GLASS, pair))
    val old = memory.tryReserve(GLASS, one)!!.confirm()!!
    assertNull(memory.tryReserve(GLASS, smaller))
    old.close()
    memory.tryReserve(GLASS, smaller)!!.use {
      assertEquals(smaller, memory.usage(GLASS).inFlightBytes)
    }
    assertEquals(one, memory.usage().peakBytes)
    assertEquals(0L, memory.usage().usedBytes)
  }
}
