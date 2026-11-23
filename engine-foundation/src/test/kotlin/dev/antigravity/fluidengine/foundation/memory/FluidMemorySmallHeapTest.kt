package dev.antigravity.fluidengine.foundation.memory

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

class FluidMemorySmallHeapTest {
  @Test fun realBuffersCancelAndFallBackInAChildVm() {
    // Il worker Gradle può usare un classloader isolato; passa solo test, foundation e stdlib.
    val classpath = listOf(FluidMemoryHeapProbe::class.java, FluidMemoryBudget::class.java, Unit::class.java)
      .map { File(it.protectionDomain.codeSource.location.toURI()).absolutePath }
      .distinct().joinToString(File.pathSeparator)
    val java = File(System.getProperty("java.home"), "bin/java")
    val child = ProcessBuilder(java.path, "-Xmx64m", "-cp", classpath, FluidMemoryHeapProbe::class.java.name)
      .redirectErrorStream(true).start()
    try {
      assertTrue("Timeout JVM figlia", child.waitFor(60, TimeUnit.SECONDS))
      val output = child.inputStream.bufferedReader().readText()
      println(output)
      assertEquals(output, 0, child.exitValue())
      assertTrue(output, output.contains("F7.9 heapBytes="))
    } finally {
      child.destroyForcibly()
    }
  }
}

/** Eseguibile nel solo classpath dei test. Tocca i buffer e li rilegge mentre hanno ancora un proprietario. */
internal object FluidMemoryHeapProbe {
  @JvmStatic fun main(args: Array<String>) {
    val heap = Runtime.getRuntime().maxMemory()
    check(heap in 1..64L * 1024 * 1024)
    val kib = 1024
    val pageSize = 512 * kib
    val smallSize = 256 * kib
    val limits = FluidMemoryLimits.Protected.forHeap(heap).copy(viewerBytes = 1024L * kib)
    val budget = FluidMemoryBudget(limits)
    val viewer = FluidMemoryCategory.VIEWER
    var allocated = 0L
    var cancellations = 0
    var fallbacks = 0
    fun allocate(size: Int, fill: Byte): ByteArray {
      val bytes = ByteArray(size)
      bytes.fill(fill)
      allocated += bytes.size
      return bytes
    }

    repeat(128) { index ->
      val mark = index.toByte()
      budget.tryReserve(viewer, pageSize.toLong())!!.use { pending ->
        val visible = allocate(pageSize, mark)
        pending.confirm(visible.size.toLong())!!.use {
          check(budget.tryReserve(viewer, 768L * kib) == null)
          try {
            budget.tryReserve(viewer, smallSize.toLong())!!.use { work ->
              val small = allocate(smallSize, mark)
              if (index % 2 == 0) throw CancellationException()
              work.confirm(small.size.toLong())!!.use {
                check(small.last() == mark)
                fallbacks++
              }
            }
          } catch (_: CancellationException) {
            cancellations++
          }
          check(visible.last() == mark)
          check(budget.usage().residentBytes == pageSize.toLong())
        }
      }
      check(budget.usage().usedBytes == 0L)
    }

    val observed = budget.usage()
    check(observed.peakBytes == (pageSize + smallSize).toLong())
    check(observed.refusals == 128L && fallbacks == 64 && cancellations == 64)
    check(allocated == 128L * (pageSize + smallSize))
    println("F7.9 heapBytes=$heap allocatedPayloadBytes=$allocated peakAccountedBytes=${observed.peakBytes}" +
      " refusals=${observed.refusals} cancellations=$cancellations fallbacks=$fallbacks finalBytes=${observed.usedBytes}")
  }
}
