package dev.antigravity.fluidengine.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.UUID
import java.util.concurrent.Executors

/**
 * R1 · Lo stato della scansione nei casi che la review ha trovato: un «Riprendi» annullato a metà, «Butta» con la
 * schermata già chiusa o con una pagina che si sta scrivendo, una pagina che non si legge, una pagina tolta mentre si
 * pulisce. Lo stato è quello vero, con un filo suo al posto del principale (le scritture di stato stanno tutte lì).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScanStateR1Test {
  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
  private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "prova-principale") }
  private val main = executor.asCoroutineDispatcher()

  @After fun stop() {
    executor.shutdown()
  }

  private fun state(scope: CoroutineScope = CoroutineScope(SupervisorJob() + main)) =
    FluidScanState(context, scope, FluidScanOptions(), "r1-" + UUID.randomUUID().toString().take(8))

  /** La cartella della sessione, senza crearla (il getter `folder` la crea). */
  private fun dir(s: FluidScanState) = File(ScanFiles.root(context), s.session)

  private fun <T> onMain(block: suspend CoroutineScope.() -> T): T = runBlocking(main, block)

  private fun sheet(w: Int, h: Int, color: Int): Bitmap =
    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

  private fun size(file: File): Pair<Int, Int> {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, o)
    return o.outWidth to o.outHeight
  }

  private fun waitUntil(ms: Long, condition: () -> Boolean): Boolean {
    val end = System.currentTimeMillis() + ms
    while (System.currentTimeMillis() < end) {
      if (condition()) return true
      Thread.sleep(10)
    }
    return condition()
  }

  @Test fun riprendiAnnullatoLasciaInsiemeLaFotoELaPaginaDiPrima() {
    val escaped = Collections.synchronizedList(ArrayList<Throwable>())
    val s = state()
    // Due fogli di misure diverse: dalla foto sul disco si capisce quale dei due c'è.
    val before = sheet(2600, 2000, android.graphics.Color.WHITE)
    val after = sheet(2000, 2600, android.graphics.Color.LTGRAY)
    val page = onMain { s.add(before, FluidScanQuad.Full)!! }
    onMain { s.awaitProcessed() }
    // «Indietro» mentre si scrive lo scatto nuovo: si annulla il lavoro dopo tempi diversi, per cadere anche a metà.
    for (wait in listOf(0L, 1, 2, 4, 8, 12, 20, 30, 45, 70, 100, 150)) {
      val job = CoroutineScope(main + CoroutineExceptionHandler { _, e -> escaped += e }).launch { s.retake(page.id, after, FluidScanQuad.Full) }
      Thread.sleep(wait)
      job.cancel()
      runBlocking { job.join() }
      onMain { s.awaitProcessed() }
      val now = onMain { s.pages.single() }
      assertEquals("annullato dopo $wait ms: la foto sul disco è quella che la pagina dice", now.photoWidth to now.photoHeight, size(now.photo))
      // Se il «Riprendi» è arrivato in fondo, si torna alla foto di prima per il giro dopo.
      if (now.photoWidth != before.width) {
        onMain { s.retake(page.id, before, FluidScanQuad.Full); s.awaitProcessed() }
      }
    }
    assertTrue("errori usciti dai «Riprendi»: $escaped", escaped.isEmpty())
    // Nessuno scatto a metà lasciato nella cartella: solo la foto e la pagina pulita.
    val files = dir(s).listFiles().orEmpty().map { it.name }.sorted()
    assertEquals(listOf("foto-${page.id}.jpg", "pagina-${page.id}.jpg"), files)
  }

  @Test fun buttaAncheSeLaSchermataSeNeEGiaAndata() {
    val screen = SupervisorJob()
    val s = state(CoroutineScope(screen + main))
    onMain {
      s.add(sheet(1200, 1600, android.graphics.Color.WHITE), FluidScanQuad.Full)
      s.awaitProcessed()
    }
    assertTrue(dir(s).exists())
    // La schermata è uscita (lo scope della composizione è annullato), poi l'app butta la scansione.
    screen.cancel()
    onMain { s.clear() }
    waitUntil(3000) { !dir(s).exists() }
    assertFalse("le foto buttate sono ancora nella cache: ${dir(s).listFiles()?.map { it.name }}", dir(s).exists())
  }

  @Test fun buttaMentreUnaPaginaSiScriveNonLasciaNiente() {
    for (wait in listOf(0L, 5, 15, 30, 60, 100, 160, 250)) {
      val s = state()
      onMain { s.add(sheet(2400, 3200, android.graphics.Color.WHITE), FluidScanQuad.Full) }
      // Un secondo scatto ancora in corso, e la pulizia della prima pagina che lavora.
      val adding = CoroutineScope(main + CoroutineExceptionHandler { _, _ -> }).launch { s.add(sheet(2400, 3200, android.graphics.Color.LTGRAY), FluidScanQuad.Full) }
      Thread.sleep(wait)
      onMain { s.clear() }
      runBlocking { adding.join() }
      // Il tempo perché tutto quello che era in volo finisca.
      waitUntil(4000) { !dir(s).exists() }
      Thread.sleep(300)
      assertFalse("butta dopo $wait ms: restano ${dir(s).listFiles()?.map { it.name }}", dir(s).exists())
      assertTrue("butta dopo $wait ms: la fila è vuota", onMain { s.pages.isEmpty() })
    }
  }

  @Test fun unaPaginaCheNonSiLeggeNonFaCadereNonBloccaESalvaLoDice() {
    val escaped = Collections.synchronizedList(ArrayList<Throwable>())
    val s = state(CoroutineScope(SupervisorJob() + main + CoroutineExceptionHandler { _, e -> escaped += e }))
    val first = onMain { s.add(sheet(1200, 1600, android.graphics.Color.WHITE), FluidScanQuad.Full)!! }
    val second = onMain { s.add(sheet(1200, 1600, android.graphics.Color.LTGRAY), FluidScanQuad.Full)!! }
    onMain { s.awaitProcessed() }
    // La foto della seconda pagina si rompe sul disco (un JPEG a metà), poi si cambia il suo filtro: la pagina pulita
    // si rifà dalla foto, che non si legge.
    second.photo.writeBytes(ByteArray(96) { 7 })
    onMain { s.setFilter(second.id, FluidScanFilter.BlackAndWhite) }
    onMain { withTimeout(10_000) { s.awaitProcessed() } }
    assertTrue("l'errore della pulizia è uscito dal lavoro: $escaped", escaped.isEmpty())
    assertFalse("working resta acceso", onMain { s.working })
    // «Salva»: un errore che dice quale pagina manca, non una consegna con una pagina in meno.
    val error = runCatching { onMain { withTimeout(20_000) { s.export() } } }.exceptionOrNull()
    assertTrue("export: $error", error is IOException)
    assertTrue("export dice la pagina: ${error?.message}", error!!.message.orEmpty().contains("2"))
    assertTrue(escaped.isEmpty())
    assertFalse(onMain { s.working })
    // La pagina rotta si toglie: la consegna va, con la pagina buona.
    onMain { s.remove(second.id) }
    val result = onMain { withTimeout(20_000) { s.export() } }
    assertEquals(1, result.pageCount)
    assertTrue(first.id.isNotEmpty())
  }

  @Test fun togliereUnaPaginaMentreSiPulisceSpegneWorking() {
    val s = state()
    val page = onMain { s.add(sheet(2400, 3200, android.graphics.Color.WHITE), FluidScanQuad.Full)!! }
    assertTrue("la pulizia è in corso", onMain { s.working })
    onMain { s.remove(page.id) }
    waitUntil(4000) { !onMain { s.working } }
    assertFalse("working resta vero con la fila vuota", onMain { s.working })
  }
}
