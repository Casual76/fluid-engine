package dev.antigravity.fluidengine.search

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * L'aggiornamento a pezzi (F8.22): ogni pezzo entra col suo segno in una transazione; con l'app aperta si fa il lavoro
 * leggero e il resto aspetta la carica; un errore a meta' lascia dentro i pezzi gia' fatti e si riparte da li'.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FluidSearchIndexingTest {
  private val context: Context get() = ApplicationProvider.getApplicationContext()

  /** Una sorgente di [count] note, a pagine, col segno = quante ne ha gia' date; [failAt] fa fallire quella pagina. */
  private class Notes(private val all: List<FluidSearchDocument>, var failAt: Int? = null, var deleted: List<String> = emptyList()) : FluidSearchSource {
    override val id: String = "note"
    var calls = 0

    override suspend fun changes(cursor: String?, limit: Int): FluidSearchChanges {
      calls++
      val from = cursor?.toInt() ?: 0
      if (failAt != null && from >= failAt!!) error("il disco dell'app non risponde")
      val page = all.drop(from).take(limit)
      val next = from + page.size
      return FluidSearchChanges(upserts = page, deletes = if (next >= all.size) deleted else emptyList(), cursor = next.toString(), hasMore = next < all.size)
    }
  }

  @Test
  fun conLAppApertaSiFaIlLeggeroEIlRestoAspetta() = runBlocking {
    val index = FluidSearchIndex.inMemory(FluidSearchSamples.schema)
    val source = Notes(FluidSearchSamples.generated(1000))
    val indexer = FluidSearchIndexer(index, source, FluidSearchIndexPolicy(chunk = 100, foregroundDocs = 300))
    val light = indexer.update()
    assertEquals(300, light.indexed)
    assertTrue(light.hasMore)
    assertEquals(300, index.count())
    assertEquals("300", index.cursor("note"))
    // Il resto, «in carica»: dal segno, fino in fondo.
    val more = indexer.runHeavy(Long.MAX_VALUE) { false }
    assertFalse(more)
    assertEquals(1000, index.count())
    assertEquals(10, source.calls)
    index.close()
  }

  @Test
  fun unErroreAMetaLasciaIPezziFattiESiRiparteDaLi() = runBlocking {
    val index = FluidSearchIndex.inMemory(FluidSearchSamples.schema)
    val source = Notes(FluidSearchSamples.generated(500), failAt = 200)
    val indexer = FluidSearchIndexer(index, source, FluidSearchIndexPolicy(chunk = 100, foregroundDocs = 1000))
    val failed = indexer.update()
    assertTrue(failed.error != null)
    assertEquals(200, index.count())
    assertEquals("200", index.cursor("note"))
    source.failAt = null
    source.deleted = listOf("gen-0", "gen-1")
    val ok = indexer.update()
    assertEquals(null, ok.error)
    assertEquals(498, index.count())
    index.close()
  }

  @Test
  fun ilLavoroPesanteAspettaLaCarica() {
    WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
    FluidSearchWork.schedule(context, wifiOnly = true)
    val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(FluidSearchWork.UniqueName).get()
    assertEquals(1, infos.size)
    assertEquals(WorkInfo.State.ENQUEUED, infos.single().state)
    val constraints = infos.single().constraints
    assertTrue(constraints.requiresCharging())
    assertTrue(constraints.requiresBatteryNotLow())
    assertEquals(androidx.work.NetworkType.UNMETERED, constraints.requiredNetworkType)
    // Una seconda richiesta non ne fa due.
    FluidSearchWork.schedule(context)
    assertEquals(1, WorkManager.getInstance(context).getWorkInfosForUniqueWork(FluidSearchWork.UniqueName).get().size)
  }

  @Test
  fun leMisureDellaPrimaIndicizzazione() = runBlocking {
    // Sulla JVM i tempi non valgono per il telefono (quelli veri sono del Tab S9, nel rapporto): qui si guarda solo che
    // 5000 note entrino a pezzi e che una ricerca risponda.
    val index = FluidSearchIndex.inMemory(FluidSearchSamples.schema)
    val indexer = FluidSearchIndexer(index, Notes(FluidSearchSamples.generated(5000)), FluidSearchIndexPolicy(chunk = 250))
    val started = System.nanoTime()
    val done = indexer.update(heavy = true)
    val indexMs = (System.nanoTime() - started) / 1_000_000
    assertEquals(5000, done.indexed)
    val r = index.search("riunione genitori ")
    assertTrue(r.total > 0)
    println("F8.22 JVM: 5000 note in $indexMs ms; ricerca in ${r.tookMillis} ms, ${r.total} risultati")
    index.close()
  }
}
