package dev.antigravity.fluidengine.foundation.memory

import org.junit.Assert.*
import org.junit.Test

class FluidMemoryLimitsTest {
  @Test fun profilesFitTheReferenceHeapAndNeverRoundPastTheTotal() {
    val mib = 1024L * 1024
    val profiles = listOf(
      FluidMemoryLimits.Full to listOf(48, 8, 24, 48),
      FluidMemoryLimits.Reduced to listOf(24, 4, 12, 24),
      FluidMemoryLimits.Protected to listOf(12, 2, 4, 14),
    )
    for ((row, quotas) in profiles) {
      assertEquals(quotas.map { it * mib }, FluidMemoryCategory.entries.map { row[it] })
      assertEquals(quotas.sum() * mib, row.processBytes)
      assertEquals(row, row.forHeap(Long.MAX_VALUE))
      for (heap in 0L..1024L) {
        val scaled = row.forHeap(heap)
        assertEquals(heap / 4, scaled.processBytes)
        assertEquals(scaled.processBytes, FluidMemoryCategory.entries.sumOf { scaled[it] })
        for (category in FluidMemoryCategory.entries) {
          val ideal = scaled.processBytes.toDouble() * row[category] / row.processBytes
          assertTrue(kotlin.math.abs(scaled[category] - ideal) < 1)
        }
      }
    }
  }

  @Test fun externalLimitsAndOverflowSizedWeightsAreHandledExactly() {
    assertEquals(0L, FluidMemoryLimits(80, 0, 0, 0, 0).forHeap(120).processBytes)
    val wide = FluidMemoryLimits(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE)
    assertEquals(FluidMemoryLimits(4, 1, 1, 1, 1), wide.forHeap(16))
    val small = FluidMemoryLimits(7, 1, 2, 3, 4).forHeap(128)
    assertEquals(7L, small.processBytes)
    assertEquals(7L, FluidMemoryCategory.entries.sumOf { small[it] })
    assertThrows(IllegalArgumentException::class.java) { small.copy(viewerBytes = -1) }
    assertThrows(IllegalArgumentException::class.java) { small.forHeap(-1) }
  }

  @Test fun strideFormatCopiesAndAllOverflowBoundaries() {
    assertEquals(32L, fluidSurfaceBytes(2, 2, 8))
    assertEquals(384L, fluidSurfaceBytes(3, 8, 4, copies = 3, rowStrideBytes = 16))
    assertEquals(Long.MAX_VALUE, fluidSurfaceBytes(Long.MAX_VALUE, 1, 1))
    assertThrows(IllegalArgumentException::class.java) { fluidSurfaceBytes(0, 1, 1) }
    assertThrows(IllegalArgumentException::class.java) { fluidSurfaceBytes(1, -1, 1) }
    assertThrows(IllegalArgumentException::class.java) { fluidSurfaceBytes(1, 1, 0) }
    assertThrows(IllegalArgumentException::class.java) { fluidSurfaceBytes(1, 1, 1, copies = 0) }
    assertThrows(IllegalArgumentException::class.java) { fluidSurfaceBytes(3, 8, 4, rowStrideBytes = 11) }
    assertThrows(ArithmeticException::class.java) { fluidSurfaceBytes(Long.MAX_VALUE, 1, 8) }
    assertThrows(ArithmeticException::class.java) { fluidSurfaceBytes(1, 2, 1, rowStrideBytes = Long.MAX_VALUE) }
    assertThrows(ArithmeticException::class.java) { fluidSurfaceBytes(Long.MAX_VALUE, 1, 1, copies = 2) }
  }
}
