package dev.antigravity.fluidengine.viewer.data

import androidx.compose.runtime.mutableStateListOf
import dev.antigravity.fluidengine.viewer.FluidViewerDetail
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Il conto delle righe di un CSV finisce in sottofondo e sostituisce i dati del pannello mentre altri li leggono: la
 * sostituzione è un passo solo, e chi legge una copia ferma non trova mai la lista vuota a metà (F6C.15b, la corsa vista
 * in `DataFamilyTest`).
 */
class DataDetailsReplaceTest {

  @Test
  fun `a replacement from another thread is never seen half done`() {
    val details = mutableStateListOf(FluidViewerDetail("Righe", "circa 5"), FluidViewerDetail("Separatore", ";"))
    val stop = AtomicBoolean(false)
    val writer = thread(name = "conto-delle-righe") {
      var n = 0
      while (!stop.get()) {
        n++
        details.replaceWith(listOf(FluidViewerDetail("Righe", "$n righe"), FluidViewerDetail("Separatore", ";")))
      }
    }
    try {
      repeat(20_000) {
        val copy = details.consistentCopy()
        assertTrue("la lista letta a metà: $copy", copy.size == 2 && copy[0].label == "Righe" && copy[1].label == "Separatore")
      }
    } finally {
      stop.set(true)
      writer.join()
    }
  }
}
