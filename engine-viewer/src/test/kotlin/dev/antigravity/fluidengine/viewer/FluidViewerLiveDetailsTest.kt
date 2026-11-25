package dev.antigravity.fluidengine.viewer

import androidx.compose.runtime.snapshots.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * F7.20 · La lista viva del pannello info: ogni cambio è una lista intera e immutabile, i cambi da fili diversi si
 * mettono in fila, e chi osserva gli stati di Compose lo sa subito. La corsa fra chi scrive e chi legge sta in
 * `DataDetailsReplaceTest` (engine-viewer-data), sulla strada vera del CSV.
 */
class FluidViewerLiveDetailsTest {

  private fun row(n: Int) = FluidViewerDetail("riga $n", "$n", "Prova")

  @Test
  fun `changes replace the whole list`() {
    val details = FluidViewerLiveDetails(listOf(row(1)))
    assertEquals(listOf(row(1)), details)
    details.add(row(2))
    assertEquals(listOf(row(1), row(2)), details.current)
    details.replace(listOf(row(3)))
    assertEquals(listOf(row(3)), details.toList())
    details.update { if (it.isEmpty()) listOf(row(9)) else it }
    assertEquals("«riempi se è vuota» non tocca una lista piena", listOf(row(3)), details.current)
    details.replace(emptyList())
    assertTrue(details.isEmpty())
  }

  @Test
  fun `a list given out never changes afterwards`() {
    val source = arrayListOf(row(1), row(2))
    val details = FluidViewerLiveDetails(source)
    source += row(3)
    val before = details.current
    assertEquals("la lista dell'app è copiata", 2, before.size)
    details.add(row(4))
    assertEquals("la copia data prima resta com'era", listOf(row(1), row(2)), before)
    val failed = runCatching { @Suppress("UNCHECKED_CAST") (before as MutableList<FluidViewerDetail>).add(row(5)) }
    assertTrue("nessuno la cambia da fuori", failed.isFailure)
  }

  @Test
  fun `the same rows again write nothing`() {
    val details = FluidViewerLiveDetails(listOf(row(1)))
    val before = details.current
    details.replace(listOf(row(1)))
    assertSame(before, details.current)
  }

  @Test
  fun `adds from many threads are never lost`() {
    val details = FluidViewerLiveDetails()
    val start = CountDownLatch(1)
    val threads = List(4) { t ->
      thread(name = "impaginazione-$t") {
        start.await()
        repeat(500) { details.add(row(t * 1000 + it)) }
      }
    }
    start.countDown()
    threads.forEach { it.join() }
    assertEquals(2000, details.size)
    assertEquals(2000, details.current.map { it.label }.toSet().size)
  }

  @Test
  fun `a change from a background thread is announced to the observers`() {
    val details = FluidViewerLiveDetails(listOf(row(1)))
    val announced = CountDownLatch(1)
    val handle = Snapshot.registerApplyObserver { set, _ -> if (set.isNotEmpty()) announced.countDown() }
    try {
      thread(name = "conto") { details.replace(listOf(row(2))) }.join()
      assertTrue("l'osservatore non è stato chiamato", announced.await(5, TimeUnit.SECONDS))
      assertEquals(listOf(row(2)), details.current)
    } finally {
      handle.dispose()
    }
  }
}
