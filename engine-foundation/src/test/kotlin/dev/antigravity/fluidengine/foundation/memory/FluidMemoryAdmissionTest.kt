package dev.antigravity.fluidengine.foundation.memory

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FluidMemoryAdmissionTest {
  private val mib = 1024L * 1024

  @Test fun twoWorkersCannotSpendTheSameGlassHeadroom() {
    race(FluidMemoryLimits.Protected, FluidMemoryCategory.GLASS, FluidMemoryCategory.GLASS)
  }

  @Test fun twoCategoriesCannotSpendTheSameProcessHeadroom() {
    race(FluidMemoryLimits.Protected.copy(processBytes = 12 * mib), FluidMemoryCategory.GLASS, FluidMemoryCategory.VIEWER)
  }

  private fun race(limits: FluidMemoryLimits, first: FluidMemoryCategory, second: FluidMemoryCategory) {
    val budget = FluidMemoryBudget(limits)
    val gate = CyclicBarrier(2)
    val workers = Executors.newFixedThreadPool(2)
    try {
      val futures = listOf(first, second).map { category ->
        workers.submit<FluidMemoryBudget.Reservation?> {
          gate.await(5, TimeUnit.SECONDS)
          budget.tryReserve(category, 8 * mib)
        }
      }
      // Entrambi hanno ricevuto la risposta prima che il vincitore restituisca la quota.
      val grants = futures.mapNotNull { it.get(10, TimeUnit.SECONDS) }
      try {
        assertEquals("concessioni su 12 MiB per due richieste da 8 MiB", 1, grants.size)
        assertEquals(8 * mib, budget.usage().peakBytes)
        assertEquals(1L, budget.usage().refusals)
      } finally {
        grants.forEach { it.close() }
      }
      assertEquals(0L, budget.usage().usedBytes)
    } finally {
      workers.shutdownNow()
    }
  }
}
