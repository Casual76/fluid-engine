package dev.antigravity.fluidengine.viewer

import android.net.Uri
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerPosition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * F8.18 · Riprendi dallo stesso punto: il formato della posizione, il negozio leggero (in memoria, scritto più tardi
 * tutto insieme), la revisione che fa offrire la posizione invece di applicarla, il tetto, il trasporto su un altro
 * dispositivo e la pulizia dei file che non ci sono più.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FluidViewerResumeStoreTest {
  private val context get() = RuntimeEnvironment.getApplication()
  private var clock = 1_000L

  private fun store(file: File = File(context.cacheDir, "resume-${System.nanoTime()}.tsv"), max: Int = 300) =
    FluidViewerResumeStore(file, maxEntries = max, saveDelayMillis = 50, clock = { clock })

  private fun pdf(name: String = "Manuale.pdf", size: Long? = 4096, modified: Long? = 77L) =
    FluidViewerFile(Uri.parse("content://prova/$name"), name, "application/pdf", size, modified)

  @Test
  fun `the position line survives a round trip and ignores unknown fields`() {
    val position = FluidViewerPosition(page = 41, offset = 0.25f, zoom = 1.5f, mediaMillis = 93_000)
    assertEquals("p=41;o=0.2500;z=1.500;m=93000", position.encode())
    assertEquals(position, FluidViewerPosition.decode(position.encode()))
    assertEquals(FluidViewerPosition(page = 3), FluidViewerPosition.decode("p=3;x=qualcosa"))
    assertNull(FluidViewerPosition.decode("niente"))
    assertNull(FluidViewerPosition.decode(""))
    assertTrue(FluidViewerPosition().isStart)
    assertFalse(FluidViewerPosition(page = 1).isStart)
    assertFalse(FluidViewerPosition(mediaMillis = 6_000).isStart)
  }

  @Test
  fun `the same revision applies and another revision is only offered`() {
    val store = store()
    val file = pdf()
    store.slot(file).save(FluidViewerPosition(page = 41, offset = 0.5f, zoom = 1.25f))
    val again = store.slot(file)
    assertEquals(FluidViewerPosition(page = 41, offset = 0.5f, zoom = 1.25f), again.position)
    assertNull(again.stale)
    // Lo stesso indirizzo con un altro peso: un'altra revisione. La posizione si offre e non si applica.
    val changed = store.slot(pdf(size = 5000))
    assertNull(changed.position)
    assertEquals(41, changed.stale?.page)
    changed.dismissStale()
    assertNull(changed.stale)
    // Senza peso né data la revisione non si sa: vale.
    assertEquals(41, store.slot(pdf(size = null, modified = null)).position?.page)
  }

  @Test
  fun `the start of a file is not kept`() {
    val store = store()
    val file = pdf()
    store.slot(file).save(FluidViewerPosition(page = 12))
    assertEquals(12, store.read(FluidViewerResumeStore.keyOf(file))?.position?.page)
    store.slot(file).save(FluidViewerPosition())
    assertNull(store.read(FluidViewerResumeStore.keyOf(file)))
  }

  @Test
  fun `positions are written later all together and read back by a new store`() {
    val file = File(context.cacheDir, "resume-persist.tsv").apply { delete() }
    val first = store(file)
    for (i in 1..20) first.slot(pdf("File $i.pdf")).save(FluidViewerPosition(page = i, offset = 0.1f))
    // In memoria subito; sul disco dopo l'attesa (qui la si forza con flush, come fa l'app che va in background).
    first.flush()
    assertTrue(file.length() > 0)
    val text = file.readText()
    assertTrue(text.startsWith("#fluid-viewer-resume v1"))
    val second = store(file)
    assertEquals(20, second.all.size)
    assertEquals(FluidViewerPosition(page = 7, offset = 0.1f), second.read(FluidViewerResumeStore.keyOf(pdf("File 7.pdf")))?.position)
  }

  @Test
  fun `the oldest positions leave first beyond the cap`() {
    val store = store(max = 5)
    for (i in 1..8) store.slot(pdf("F$i.pdf")).save(FluidViewerPosition(page = i))
    assertEquals(5, store.all.size)
    assertNull(store.read(FluidViewerResumeStore.keyOf(pdf("F1.pdf"))))
    assertEquals(8, store.read(FluidViewerResumeStore.keyOf(pdf("F8.pdf")))?.position?.page)
  }

  @Test
  fun `a line from another device enters only if newer`() {
    val here = store()
    val there = store()
    val file = pdf()
    clock = 5_000
    here.slot(file).save(FluidViewerPosition(page = 10))
    clock = 9_000
    there.slot(file).save(FluidViewerPosition(page = 99))
    val record = checkNotNull(there.export(FluidViewerResumeStore.keyOf(file)))
    assertTrue(here.import(record))
    assertEquals(99, here.read(FluidViewerResumeStore.keyOf(file))?.position?.page)
    // La riga vecchia non torna indietro.
    clock = 1_000
    val old = FluidViewerResumeStore.Entry(FluidViewerResumeStore.keyOf(file), file.uri.toString(), null,
      FluidViewerPosition(page = 1), 1_000).encode()
    assertFalse(here.import(old))
    assertEquals(99, here.read(FluidViewerResumeStore.keyOf(file))?.position?.page)
  }

  @Test
  fun `tabs and line breaks in an address do not break the line`() {
    val entry = FluidViewerResumeStore.Entry("chiave\tcon\ntab", "content://a/b\\c", "1:2",
      FluidViewerPosition(page = 2), 3)
    assertEquals(entry, FluidViewerResumeStore.Entry.decode(entry.encode()))
  }

  @Test
  fun `the app is told about every save`() {
    val store = store()
    val seen = mutableListOf<Int>()
    val watch = store.observe { seen += it.position.page }
    store.slot(pdf()).save(FluidViewerPosition(page = 3))
    store.slot(pdf()).save(FluidViewerPosition(page = 3))
    store.slot(pdf()).save(FluidViewerPosition(page = 4))
    watch.close()
    store.slot(pdf()).save(FluidViewerPosition(page = 5))
    assertEquals(listOf(3, 4), seen)
  }

  @Test
  fun `positions of files that are gone are removed and unknown ones stay`() = runBlocking {
    clock = FluidViewerResumeStore.PruneIntervalMillis + 1_000
    val store = store()
    val kept = File(context.filesDir, "tenuto.pdf").apply { writeText("x") }
    val gone = File(context.filesDir, "sparito.pdf").apply { writeText("x") }
    store.slot(FluidViewerFile(Uri.fromFile(kept), kept.name, null, 1, 1)).save(FluidViewerPosition(page = 1))
    store.slot(FluidViewerFile(Uri.fromFile(gone), gone.name, null, 1, 1)).save(FluidViewerPosition(page = 2))
    store.slot(FluidViewerFile(Uri.parse("https://esempio/remoto.pdf"), "remoto.pdf", null, 1, 1))
      .save(FluidViewerPosition(page = 3))
    gone.delete()
    assertTrue(store.pruneDue)
    assertEquals(1, store.prune(context))
    assertEquals(2, store.all.size)
    assertNull(store.read(Uri.fromFile(gone).toString()))
    assertFalse(store.pruneDue)
  }

  @Test
  fun `the default existence check answers for each scheme`() {
    val present = File(context.filesDir, "c'è.txt").apply { writeText("x") }
    assertEquals(true, resumeTargetExists(context, Uri.fromFile(present)))
    assertEquals(false, resumeTargetExists(context, Uri.fromFile(File(context.filesDir, "no.txt"))))
    // Un provider che non c'è (un'app disinstallata): il file non si riaprirà più.
    assertEquals(false, resumeTargetExists(context, Uri.parse("content://provider.che.non.esiste/doc/1")))
    assertEquals(true, resumeTargetExists(context, Uri.parse("android.resource://${context.packageName}/1")))
    assertNull(resumeTargetExists(context, Uri.parse("https://esempio/file.pdf")))
  }
}
