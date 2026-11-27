package dev.antigravity.fluidengine.scan

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.saveable.SaverScope
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * R3 (review R3, Muse 3, 7 e 26c) · Lo scanner:
 * - una foto o una pagina pulita che il codificatore non scrive per intero (il disco pieno: `compress` torna falso) non
 *   passa per buona; una pagina pulita rotta sul disco non finisce nel PDF;
 * - la pulizia delle pagine ha un tetto di lavori insieme, e il ripristino non rifa' le pagine pulite gia' sul disco;
 * - `grantTo` prepara un invio che le altre app aprono (il tipo e `EXTRA_STREAM`).
 * Lo stato e' quello vero, con un filo suo al posto del principale, come in [ScanStateR1Test].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [FailingCompressBitmap::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScanStateR3Test {
  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
  private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "prova-principale") }
  private val main = executor.asCoroutineDispatcher()
  private val scope = CoroutineScope(SupervisorJob() + main)

  @After fun stop() {
    FailingCompressBitmap.failJpegQuality = -1
    FailingCompressBitmap.failures = 0
    executor.shutdown()
  }

  private fun state(session: String = "r3-" + UUID.randomUUID().toString().take(8)) =
    FluidScanState(context, scope, FluidScanOptions(), session)

  private fun dir(s: FluidScanState) = File(ScanFiles.root(context), s.session)

  private fun clean(s: FluidScanState, id: String) = File(dir(s), "pagina-$id.jpg")

  private fun <T> onMain(block: suspend CoroutineScope.() -> T): T = runBlocking(main, block)

  private fun sheet(w: Int, h: Int, color: Int): Bitmap =
    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

  // --- Muse 3: la codifica che fallisce ------------------------------------------------------------------------------

  @Test fun unaPaginaPulitaScrittaAMetaESegnataDaRifare() {
    val s = state()
    // La qualita' delle pagine pulite di serie (85): il disco si riempie mentre si scrive la prima.
    FailingCompressBitmap.failJpegQuality = FluidScanOptions().jpegQuality
    val page = onMain { s.add(sheet(1200, 1600, android.graphics.Color.WHITE), FluidScanQuad.Full)!! }
    onMain { withTimeout(20_000) { s.awaitProcessed() } }
    assertTrue("la codifica e' fallita davvero (l'ombra e' passata)", FailingCompressBitmap.failures >= 1)
    assertTrue("una pagina pulita scritta a meta' non e' segnata da rifare", onMain { s.failed(page.id) })
    assertFalse("il file a meta' resta sul disco come se fosse buono", clean(s, page.id).exists())
    // «Salva» con il disco ancora pieno: dice quale pagina, non scrive un PDF.
    val error = runCatching { onMain { withTimeout(20_000) { s.export() } } }.exceptionOrNull()
    assertTrue("export: $error", error is IOException)
    assertTrue(error!!.message.orEmpty().contains("1"))
    assertFalse(File(dir(s), "Scansione.pdf").exists())
    // Il disco torna libero: «Salva» rifa' la pagina e consegna.
    FailingCompressBitmap.failJpegQuality = -1
    val result = onMain { withTimeout(20_000) { s.export(title = "Scansione") } }
    assertEquals(1, result.pageCount)
    assertTrue(clean(s, page.id).length() > 1000)
  }

  @Test fun unoScattoScrittoAMetaNonEntraNellaFilaENonSostituisceLaFoto() {
    val s = state()
    FailingCompressBitmap.failJpegQuality = 92
    val error = runCatching { onMain { s.add(sheet(1200, 1600, android.graphics.Color.WHITE), FluidScanQuad.Full) } }.exceptionOrNull()
    assertTrue("lo scatto scritto a meta' e' entrato nella fila: $error, pagine ${onMain { s.pages.size }}", error is IOException)
    assertTrue(onMain { s.pages.isEmpty() })
    assertTrue("resta la foto a meta': ${dir(s).list()?.toList()}", dir(s).listFiles().orEmpty().none { it.name.startsWith("foto-") })
    // «Riprendi» con il disco pieno: la foto di prima resta quella della pagina.
    FailingCompressBitmap.failJpegQuality = -1
    val page = onMain { s.add(sheet(1200, 1600, android.graphics.Color.WHITE), FluidScanQuad.Full)!! }
    onMain { withTimeout(20_000) { s.awaitProcessed() } }
    val photo = page.photo.readBytes()
    FailingCompressBitmap.failJpegQuality = 92
    val again = runCatching { onMain { s.retake(page.id, sheet(1600, 1200, android.graphics.Color.LTGRAY), FluidScanQuad.Full) } }.exceptionOrNull()
    assertTrue("riprendi: $again", again is IOException)
    assertArrayEquals("la foto della pagina e' stata sostituita da uno scatto a meta'", photo, page.photo.readBytes())
    assertEquals(1200, onMain { s.pages.single().photoWidth })
  }

  @Test fun unaPaginaPulitaRottaSulDiscoNonFinisceNelPdf() {
    val s = state()
    onMain {
      s.add(sheet(1200, 1600, android.graphics.Color.WHITE), FluidScanQuad.Full)
      s.add(sheet(1200, 1600, android.graphics.Color.LTGRAY), FluidScanQuad.Full)
      withTimeout(20_000) { s.awaitProcessed() }
    }
    val second = onMain { s.pages[1] }
    // La pagina pulita della seconda si rompe sul disco (la cache toccata da fuori, una scrittura vecchia a meta').
    clean(s, second.id).writeBytes(ByteArray(12) { 3 })
    val error = runCatching { onMain { withTimeout(20_000) { s.export(title = "Rotta") } } }.exceptionOrNull()
    val pdf = File(dir(s), "Rotta.pdf")
    assertTrue("export con una pagina rotta: $error, PDF ${if (pdf.exists()) String(pdf.readBytes(), Charsets.ISO_8859_1).substringAfter("/Width ").take(6) else "assente"}",
      error is IOException)
    assertTrue(error!!.message.orEmpty().contains("2"))
    // La volta dopo la pagina si rifa' dalla foto e la consegna va, con misure vere.
    val result = onMain { withTimeout(20_000) { s.export(title = "Rotta") } }
    assertEquals(2, result.pageCount)
    val text = String(result.pdfFile!!.readBytes(), Charsets.ISO_8859_1)
    assertFalse("una pagina senza misure nel PDF", text.contains("/Width -1") || text.contains("/Width 0 "))
  }

  // --- Muse 7: il tetto della pulizia --------------------------------------------------------------------------------

  /** Quanti fili lavorano adesso a una pagina pulita (decodifica, raddrizzamento, filtro), letti dai loro stack. */
  private fun cleaningThreads(): Int = Thread.getAllStackTraces().count { (thread, stack) ->
    thread.name.startsWith("DefaultDispatcher") && stack.any {
      it.className.startsWith("dev.antigravity.fluidengine.scan.FluidScanImages") ||
        (it.className.startsWith("dev.antigravity.fluidengine.scan.FluidScanState") && (it.methodName == "render" || it.methodName == "decode"))
    }
  }

  @Test fun laPuliziaDiTutteLePagineNonParteTuttaInsieme() {
    val s = state()
    val count = 8
    onMain {
      repeat(count) { s.add(sheet(2400, 3200, android.graphics.Color.WHITE), FluidScanQuad.Full) }
      withTimeout(60_000) { s.awaitProcessed() }
    }
    val peak = AtomicInteger(0)
    val done = AtomicBoolean(false)
    val sampler = Thread {
      while (!done.get()) {
        peak.accumulateAndGet(cleaningThreads()) { a, b -> maxOf(a, b) }
        Thread.sleep(2)
      }
    }.apply { start() }
    // Il menu dei filtri sulla fotocamera: tutte le pagine si ripuliscono.
    onMain { s.setFilterForAll(FluidScanFilter.BlackAndWhite) }
    onMain { withTimeout(120_000) { s.awaitProcessed() } }
    done.set(true)
    sampler.join()
    println("R3 pulizia: $count pagine, al piu' ${peak.get()} insieme (processori ${Runtime.getRuntime().availableProcessors()})")
    assertTrue("nessuna pulizia vista: la prova non misura", peak.get() >= 1)
    assertTrue("${peak.get()} pagine pulite insieme, il tetto e' 2", peak.get() <= 2)
    assertTrue(onMain { s.pages.all { s.thumbnail(it.id) != null && !s.failed(it.id) } })
  }

  @Test fun ilRipristinoNonRifaLePaginePuliteGiaSulDisco() {
    val session = "r3-" + UUID.randomUUID().toString().take(8)
    val s = state(session)
    onMain {
      repeat(3) { s.add(sheet(1200, 1600, android.graphics.Color.WHITE), FluidScanQuad.Full) }
      withTimeout(20_000) { s.awaitProcessed() }
    }
    val ids = onMain { s.pages.map { it.id } }
    val old = 1_600_000_000_000L
    // La terza cambia filtro e l'attivita' si salva subito, con la sua pulizia ancora da fare: quella va rifatta.
    val rows = onMain {
      s.setFilter(ids[2], FluidScanFilter.Grayscale)
      with(scanStateSaver(context, scope, FluidScanOptions())) { SaverScope { true }.save(s) }!!
    }
    onMain { withTimeout(20_000) { s.awaitProcessed() } }
    for (id in ids) assertTrue(clean(s, id).setLastModified(old))
    // Il processo muore e l'attivita' si ricrea: lo stato torna dal Bundle, la cartella e' la stessa.
    val restored = onMain { scanStateSaver(context, scope, FluidScanOptions()).restore(rows)!! }
    onMain { withTimeout(20_000) { restored.awaitProcessed() } }
    assertEquals(ids, onMain { restored.pages.map { it.id } })
    for (id in ids) assertNotNull("la miniatura di $id", onMain { restored.thumbnail(id) })
    assertEquals("le pagine pulite gia' sul disco si sono rifatte", listOf(old, old), ids.take(2).map { clean(s, it).lastModified() })
    assertNotEquals("la pagina cambiata dopo l'ultima pulizia non si e' rifatta", old, clean(s, ids[2]).lastModified())
    assertEquals(FluidScanFilter.Grayscale, onMain { restored.pages[2].filter })
    assertFalse(onMain { restored.working })
  }

  // --- Muse 26c: grantTo ---------------------------------------------------------------------------------------------

  @Test fun grantToPreparaUnInvioCheLeAltreAppAprono() {
    val pdf = Uri.parse("content://prova.fluidscan/fluid_scan/s/Scansione.pdf")
    val one = Uri.parse("content://prova.fluidscan/fluid_scan/s/Scansione%20-%201.jpg")
    val two = Uri.parse("content://prova.fluidscan/fluid_scan/s/Scansione%20-%202.jpg")
    fun Intent.stream(): Uri? = getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    fun Intent.streams(): List<Uri>? = getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
    fun Intent.grants() = flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0
    fun Intent.clip() = (0 until (clipData?.itemCount ?: 0)).map { clipData!!.getItemAt(it).uri }

    // La ricetta della scheda: il PDF a un'altra app.
    val send = FluidScanResult(pdf, emptyList(), 2, "Scansione").grantTo(Intent(Intent.ACTION_SEND))
    assertEquals("il tipo dell'invio", "application/pdf", send.type)
    assertEquals("il file dell'invio (EXTRA_STREAM)", pdf, send.stream())
    assertTrue(send.grants())
    assertEquals(listOf(pdf), send.clip())
    // Le pagine in JPEG, tutte.
    val pages = FluidScanResult(null, listOf(one, two), 2, "Scansione").grantTo(Intent(Intent.ACTION_SEND_MULTIPLE))
    assertEquals("image/jpeg", pages.type)
    assertEquals(listOf(one, two), pages.streams())
    assertEquals(listOf(one, two), pages.clip())
    // Tutte e due: un invio singolo porta il PDF, uno multiplo tutto (tipi diversi: */*).
    val both = FluidScanResult(pdf, listOf(one), 1, "Scansione")
    assertEquals(pdf, both.grantTo(Intent(Intent.ACTION_SEND)).stream())
    assertEquals("application/pdf", both.grantTo(Intent(Intent.ACTION_SEND)).type)
    val all = both.grantTo(Intent(Intent.ACTION_SEND_MULTIPLE))
    assertEquals("*/*", all.type)
    assertEquals(listOf(pdf, one), all.streams())
    // Quello che l'app ha gia' messo resta suo.
    val own = FluidScanResult(pdf, emptyList(), 1, "Scansione")
      .grantTo(Intent(Intent.ACTION_SEND).setType("application/octet-stream").putExtra(Intent.EXTRA_STREAM, one))
    assertEquals("application/octet-stream", own.type)
    assertEquals(one, own.stream())
    // L'esito d'attivita' del contratto (nessuna azione): solo il permesso, come prima.
    val result = FluidScanResult(pdf, listOf(one), 1, "Scansione").grantTo(Intent())
    assertNull(result.type)
    assertNull(result.stream())
    assertTrue(result.grants())
    assertEquals(listOf(pdf, one), result.clip())
  }
}
