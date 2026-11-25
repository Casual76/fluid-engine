package dev.antigravity.fluidengine.viewer.data

import androidx.compose.runtime.snapshots.Snapshot
import dev.antigravity.fluidengine.viewer.FluidViewerDetail
import dev.antigravity.fluidengine.viewer.FluidViewerLiveDetails
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Il conto delle righe di un CSV finisce in sottofondo e sostituisce i dati del pannello mentre altri li leggono: chi
 * legge trova sempre una lista intera, quella di prima o quella nuova, mai una a metà (F6C.15b, la corsa vista in
 * `DataFamilyTest`).
 *
 * F7.20: la prova di prima (un filo che scrive, uno che legge, 20 000 letture) passava da sola e cadeva sotto carico,
 * nei lotti completi, con una `ConcurrentModificationException`: la copia in un'istantanea di Compose non isola chi
 * legge da un filo che scrive (vedi [FluidViewerLiveDetails]). Ora la prova è più dura: due fili che sostituiscono e sei
 * che leggono per tre secondi in tre modi, come il pannello ([consistentCopy]), come la composizione (dentro
 * un'istantanea) e come un'app che scorre `document.details`. Senza la correzione cade a ogni giro.
 */
class DataDetailsReplaceTest {

  @Test
  fun `a replacement from other threads is never seen half done`() {
    val details = FluidViewerLiveDetails(DetailsRace.rows(0))
    val result = DetailsRace.run(
      write = { n -> details.replace(DetailsRace.rows(n)) },
      readers = listOf(
        { _ -> details.consistentCopy() },
        { _ -> details.consistentCopy() },
        { _ -> DetailsRace.inSnapshot { details.toList() } },
        { _ -> DetailsRace.inSnapshot { details.toList() } },
        { round -> DetailsRace.likeAnApp(details, round) },
        { round -> DetailsRace.likeAnApp(details, round + 1) },
      ),
    )
    println(result.summary)
    result.failure?.let { throw AssertionError(result.summary, it) }
  }
}

/**
 * La corsa di [DataDetailsReplaceTest]: [Writers] fili scrivono di continuo, ogni lettore legge e controlla finché non
 * passano [Millis] ms (e almeno [MinRounds] giri). Il primo problema ferma tutti.
 */
internal object DetailsRace {
  /** Quante righe ha la lista: quelle di un CSV vero, fatti in capsule e sezioni. */
  const val Rows = 12
  const val Writers = 2
  const val Millis = 3_000L
  const val MinRounds = 500

  class Result(val failure: Throwable?, val writes: Long, val reads: Long) {
    val summary get() = "scritture $writes, letture $reads" + (failure?.let { ", problema: $it" } ?: "")
  }

  /** Le righe della scrittura [n]: ogni valore porta [n], così una lista mescolata si vede. */
  fun rows(n: Int): List<FluidViewerDetail> = List(Rows) { i ->
    FluidViewerDetail("riga $i", "$i·$n", section = if (i < 4) null else if (i < 9) "Tabella" else "Lettura")
  }

  /** `null` se [list] è intera e viene da una scrittura sola, se no che cosa non va. */
  fun problem(list: List<FluidViewerDetail>): String? {
    if (list.size != Rows) return "lista di ${list.size} righe invece di $Rows: $list"
    val n = list[0].value.substringAfter('·')
    list.forEachIndexed { i, d ->
      if (d.label != "riga $i" || d.value != "$i·$n") return "riga $i fuori posto o di un'altra scrittura: $list"
    }
    return null
  }

  /** Come la composizione: la lettura dentro un'istantanea di sola lettura. */
  fun <T> inSnapshot(read: () -> T): T {
    val snapshot = Snapshot.takeSnapshot()
    try {
      return snapshot.enter(read)
    } finally {
      snapshot.dispose()
    }
  }

  /** Come un'app che scorre `document.details` fuori dalla composizione, in tre modi a turno. */
  fun likeAnApp(details: List<FluidViewerDetail>, round: Int): List<FluidViewerDetail> = when (round % 3) {
    0 -> buildList { for (d in details) add(d) }
    1 -> details.filter { it.label.isNotEmpty() }
    else -> details.toList()
  }

  fun run(
    write: (Int) -> Unit,
    readers: List<(round: Int) -> List<FluidViewerDetail>>,
    writers: Int = Writers,
    millis: Long = Millis,
  ): Result {
    val stop = AtomicBoolean(false)
    val failure = AtomicReference<Throwable?>(null)
    val next = AtomicInteger()
    val writes = AtomicLong()
    val reads = AtomicLong()
    val fail = { t: Throwable -> failure.compareAndSet(null, t); stop.set(true) }
    val writing = List(writers) { w ->
      thread(name = "conto-delle-righe-$w") {
        try {
          while (!stop.get()) {
            write(next.incrementAndGet())
            writes.incrementAndGet()
          }
        } catch (t: Throwable) {
          fail(t)
        }
      }
    }
    val deadline = System.nanoTime() + millis * 1_000_000
    val reading = readers.mapIndexed { r, read ->
      thread(name = "pannello-$r") {
        var round = 0
        try {
          while (!stop.get() && (System.nanoTime() < deadline || round < MinRounds)) {
            round++
            val list = read(round)
            reads.incrementAndGet()
            problem(list)?.let { throw AssertionError("lettore $r, giro $round: $it") }
          }
        } catch (t: Throwable) {
          fail(t)
        }
      }
    }
    reading.forEach { it.join() }
    stop.set(true)
    writing.forEach { it.join() }
    return Result(failure.get(), writes.get(), reads.get())
  }
}
