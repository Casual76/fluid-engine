package dev.antigravity.fluidengine.sample.playground

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * F7.21 · I salvataggi delle forme del Playground: il thread principale cambia la lista di stato e chiede di salvarla,
 * il disco riceve sempre una lista intera, e l'ultima che scrive è l'ultima chiesta.
 *
 * Un filo fa da thread principale: una finestra di [ShapeSavesRace.Window] forme che scorre (aggiunge in fondo, toglie
 * in testa) e un salvataggio dopo ogni cambio. Chi scrive sul disco controlla ogni lista: forme consecutive, nessuna
 * lista a metà o mescolata, e il disco non torna mai indietro. Il primo problema ferma la prova. Col codice di prima
 * (la copia presa dentro `launch(Dispatchers.IO)`) la stessa corsa cade a ogni giro: i numeri sono nel rapporto di F7.21.
 */
class F7p21ShapeSavesRaceTest {

  @Test
  fun `the disk always gets a whole list, and the last one asked`() {
    val result = ShapeSavesRace.run { scope, write -> ShapeSaves(scope, write)::save }
    println(result.summary)
    result.failure?.let { throw AssertionError(result.summary, it) }
    assertTrue("la corsa è durata troppo poco: ${result.summary}", result.changes > 1_000 && result.written > 10)
    assertEquals("l'ultima lista scritta non è l'ultima chiesta: ${result.summary}", result.expected, result.last)
  }
}

/** La corsa di [F7p21ShapeSavesRaceTest], con il salvataggio da provare. */
internal object ShapeSavesRace {
  const val Window = 8
  const val Millis = 1_500L

  private val Triangle = listOf(Offset(0f, 0f), Offset(1f, 0f), Offset(0f, 1f))

  fun shape(n: Int) = SavedShape("Forma $n", 6f, Triangle)

  private fun number(shape: SavedShape) = shape.name.removePrefix("Forma ").toInt()

  /** `null` se [list] è una finestra intera di forme consecutive, se no che cosa non va. */
  fun problem(list: List<SavedShape>): String? {
    if (list.size != Window && list.size != Window + 1) return "lista di ${list.size} forme: ${list.map { it.name }}"
    val first = number(list[0])
    list.forEachIndexed { i, s -> if (number(s) != first + i) return "forme non consecutive: ${list.map { it.name }}" }
    return null
  }

  class Result(val failure: Throwable?, val changes: Long, val written: Long, val last: Int, val expected: Int) {
    val summary get() = "cambi $changes, scritture $written, ultima forma scritta $last di $expected" +
      (failure?.let { ", problema: $it" } ?: "")
  }

  /**
   * [saver] riceve lo scope dei salvataggi e la scrittura sul disco, e dà la funzione che il thread principale chiama
   * dopo ogni cambio.
   */
  fun run(
    millis: Long = Millis,
    saver: (CoroutineScope, (List<SavedShape>) -> Unit) -> (List<SavedShape>) -> Unit,
  ): Result {
    val failure = AtomicReference<Throwable?>(null)
    val job = SupervisorJob()
    val scope = CoroutineScope(job + CoroutineExceptionHandler { _, t -> failure.compareAndSet(null, t) })
    val written = AtomicLong()
    val last = AtomicInteger(-1)
    val write: (List<SavedShape>) -> Unit = { list ->
      try {
        problem(list)?.let { throw AssertionError(it) }
        val end = number(list.last())
        val before = last.getAndSet(end)
        if (end < before) throw AssertionError("il disco torna indietro: la forma $end dopo la $before")
        written.incrementAndGet()
      } catch (t: Throwable) {
        failure.compareAndSet(null, t)
      }
    }
    val save = saver(scope, write)
    val shapes = mutableStateListOf<SavedShape>().apply { for (n in 0 until Window) add(shape(n)) }
    var next = Window
    val changes = AtomicLong()
    thread(name = "principale") {
      val deadline = System.nanoTime() + millis * 1_000_000
      try {
        while (failure.get() == null && System.nanoTime() < deadline) {
          shapes.add(shape(next++))
          save(shapes)
          shapes.removeAt(0)
          save(shapes)
          changes.addAndGet(2)
        }
      } catch (t: Throwable) {
        failure.compareAndSet(null, t)
      }
    }.join()
    // L'ultima lista arriva al disco poco dopo: la si aspetta al più cinque secondi.
    val expected = next - 1
    val until = System.nanoTime() + 5_000_000_000L
    while (failure.get() == null && last.get() != expected && System.nanoTime() < until) Thread.sleep(1)
    job.cancel()
    return Result(failure.get(), changes.get(), written.get(), last.get(), expected)
  }
}
