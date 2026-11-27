package dev.antigravity.fluidengine.files

import android.net.Uri
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * F8.18 · Una cartella lenta, a pagine e che può fallire (la scheda dell'idea): le pagine che arrivano, l'errore con le
 * righe già arrivate e «Riprova» dal punto giusto, l'ordine e la ricerca in memoria, l'ordine chiesto alla sorgente.
 * E i recenti: in cima il più recente, una volta sola, salvati e ripuliti dai file spariti.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FluidFileFolderStateTest {
  private val context get() = RuntimeEnvironment.getApplication()

  /** Una sorgente lenta di [count] file, che sbaglia la pagina [failAt] la prima volta. */
  private class SlowSource(
    private val count: Int,
    override val sorts: Boolean = false,
    var failAt: Int? = null,
  ) : FluidFileSource {
    override val id = "lenta"
    override val title = "Lenta"
    val queries = mutableListOf<FluidFileQuery>()
    val names = List(count) { i -> if (i == 7) "La città vecchia.pdf" else "File %04d.txt".format(i) }

    override suspend fun list(query: FluidFileQuery): FluidFilePage {
      queries += query
      delay(300)
      val start = query.cursor?.toInt() ?: 0
      if (failAt == start) {
        failAt = null
        throw IOException("La rete non risponde")
      }
      val all = names.mapIndexed { i, name ->
        FluidFileItem("f$i", name, sizeBytes = i.toLong(), modifiedAtMillis = 1000L * i, uri = Uri.parse("content://p/$i"))
      }
      val ordered = if (sorts) all.sortedWith(query.sort.comparator(Locale.ITALIAN)) else all
      return ordered.page(query)
    }
  }

  private fun TestScope.folder(source: FluidFileSource) =
    FluidFileFolderState(source, null, "Prova", this, FluidFileSort(), Locale.ITALIAN)

  @Test
  fun `pages arrive one at a time and the last one completes the folder`() = runTest(StandardTestDispatcher()) {
    val source = SlowSource(450)
    val folder = folder(source)
    folder.start()
    assertTrue(folder.loading)
    assertTrue(folder.items.isEmpty())
    advanceUntilIdle()
    assertEquals(200, folder.items.size)
    assertFalse(folder.complete)
    folder.nearEnd(100)
    advanceUntilIdle()
    assertEquals(200, folder.items.size)
    folder.nearEnd(170)
    advanceUntilIdle()
    assertEquals(400, folder.items.size)
    folder.loadMore()
    advanceUntilIdle()
    assertEquals(450, folder.items.size)
    assertTrue(folder.complete)
    assertEquals(listOf(null, "200", "400"), source.queries.map { it.cursor })
    // Solo i file, per il visore.
    assertEquals(450, folder.files.size)
  }

  @Test
  fun `a failure keeps what arrived and retry continues from there`() = runTest(StandardTestDispatcher()) {
    val source = SlowSource(450, failAt = 200)
    val folder = folder(source)
    folder.start()
    advanceUntilIdle()
    folder.loadMore()
    advanceUntilIdle()
    assertNotNull(folder.failure)
    assertEquals(200, folder.items.size)
    // Dopo un errore la lista non chiede da sé: serve «Riprova».
    folder.loadMore()
    advanceUntilIdle()
    assertEquals(200, folder.items.size)
    folder.retry()
    advanceUntilIdle()
    assertNull(folder.failure)
    assertEquals(400, folder.items.size)
    assertEquals(listOf(null, "200", "200"), source.queries.map { it.cursor })
  }

  @Test
  fun `search reads the whole folder and ignores case and accents`() = runTest(StandardTestDispatcher()) {
    val source = SlowSource(450)
    val folder = folder(source)
    folder.start()
    advanceUntilIdle()
    folder.search("CITTA")
    advanceUntilIdle()
    assertTrue(folder.complete)
    assertEquals(listOf("La città vecchia.pdf"), folder.items.map { it.name })
    folder.search("")
    assertEquals(450, folder.items.size)
  }

  @Test
  fun `a source that does not sort is sorted in memory, one that sorts is asked again`() = runTest(StandardTestDispatcher()) {
    val plain = SlowSource(30)
    val folder = folder(plain)
    folder.start()
    advanceUntilIdle()
    folder.sortBy(FluidFileSort.of(FluidFileSortKey.Size))
    assertEquals("File 0029.txt", folder.items.first().name)
    assertEquals(1, plain.queries.size)

    val sorting = SlowSource(30, sorts = true)
    val asked = folder(sorting)
    asked.start()
    advanceUntilIdle()
    asked.sortBy(FluidFileSort.of(FluidFileSortKey.Date))
    assertTrue(asked.items.isEmpty())
    advanceUntilIdle()
    assertEquals("File 0029.txt", asked.items.first().name)
    assertEquals(FluidFileSortKey.Date, sorting.queries.last().sort.key)
    assertEquals(2, sorting.queries.size)
  }

  @Test
  fun `recents keep the newest first, once, and survive a new process`() {
    val file = File(context.cacheDir, "recenti-${System.nanoTime()}.tsv")
    var now = 100L
    val recents = FluidFileRecents(file, maxEntries = 3, clock = { now })
    fun item(i: Int) = FluidFileItem("f$i", "File $i.pdf", mimeType = "application/pdf", sizeBytes = i.toLong(),
      uri = Uri.parse("content://p/$i"))
    recents.record("drive", item(1)); now++
    recents.record("drive", item(2)); now++
    recents.record("app", item(1)); now++
    recents.record("drive", FluidFileItem("c", "Cartella", isFolder = true))
    assertEquals(listOf("File 1.pdf", "File 2.pdf"), recents.entries.map { it.item.name })
    assertEquals("app", recents.sourceOf(item(1)))
    for (i in 3..6) { recents.record("drive", item(i)); now++ }
    assertEquals(listOf("File 6.pdf", "File 5.pdf", "File 4.pdf"), recents.entries.map { it.item.name })
    // Scritti in sottofondo: un processo nuovo li ritrova.
    recents.awaitWrites()
    val again = FluidFileRecents(file, maxEntries = 3)
    assertEquals(listOf("File 6.pdf", "File 5.pdf", "File 4.pdf"), again.entries.map { it.item.name })
    assertEquals(Uri.parse("content://p/6"), again.entries.first().item.uri)
  }

  @Test
  fun `recents of files that are gone leave, and the page lists them by opening`() = runTest {
    val recents = FluidFileRecents(File(context.cacheDir, "recenti-pulizia-${System.nanoTime()}.tsv"))
    fun item(i: Int) = FluidFileItem("f$i", "File $i.pdf", uri = Uri.parse("content://p/$i"), modifiedAtMillis = 10L - i)
    for (i in 1..4) recents.record("drive", item(i))
    assertEquals(2, recents.prune(context) { uri -> if (uri.lastPathSegment == "2" || uri.lastPathSegment == "3") false else null })
    assertEquals(listOf("File 4.pdf", "File 1.pdf"), recents.entries.map { it.item.name })
    val page = recents.source.list(FluidFileQuery(null, FluidFileSort.of(FluidFileSortKey.Date)))
    assertEquals(listOf("File 4.pdf", "File 1.pdf"), page.items.map { it.name })
    val byName = recents.source.list(FluidFileQuery(null, FluidFileSort()))
    assertEquals(listOf("File 1.pdf", "File 4.pdf"), byName.items.map { it.name })
    recents.forget(Uri.parse("content://p/4"))
    assertEquals(listOf("File 1.pdf"), recents.entries.map { it.item.name })
  }

  @Test
  fun `after a search the folder goes back to reading by pages`() = runTest(StandardTestDispatcher()) {
    val source = SlowSource(1000, sorts = true)
    val folder = folder(source)
    folder.start()
    advanceUntilIdle()
    assertEquals(200, folder.loadedCount)
    // Una sorgente che ordina ma non cerca: per cercare si legge tutta la cartella.
    folder.search("CITTA")
    advanceUntilIdle()
    assertTrue(folder.complete)
    assertEquals(1000, folder.loadedCount)
    folder.search("")
    // Un cambio d'ordine rilegge da capo: senza ricerca bastano le prime pagine, non tutta la cartella.
    val asked = source.queries.size
    folder.sortBy(FluidFileSort.of(FluidFileSortKey.Date))
    advanceUntilIdle()
    assertEquals("una pagina sola dopo la ricerca: ${source.queries.size - asked} richieste", 200, folder.loadedCount)
    assertEquals(1, source.queries.size - asked)
    assertFalse(folder.complete)
  }
}
