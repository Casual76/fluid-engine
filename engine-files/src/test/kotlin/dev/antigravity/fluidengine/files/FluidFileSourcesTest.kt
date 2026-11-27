package dev.antigravity.fluidengine.files

import android.content.Intent
import android.provider.DocumentsContract
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * F8.18 · Le sorgenti di serie dello sfoglia-file: la cartella dell'app sul disco e una cartella concessa con lo Storage
 * Access Framework (un provider finto, interrogato con gli stessi indirizzi dell'albero di Android), l'ordine di File e
 * le pagine; le posizioni concesse col permesso persistente, che si prende e si restituisce.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FluidFileSourcesTest {
  private val context get() = RuntimeEnvironment.getApplication()

  @Before
  fun start() {
    FakeTreeProvider.reset()
    Robolectric.setupContentProvider(FakeTreeProvider::class.java, FakeTreeProvider.Authority)
  }

  @After
  fun stop() = FakeTreeProvider.reset()

  private fun folder(): File = File(context.filesDir, "prova-${System.nanoTime()}").apply {
    mkdirs()
    File(this, "Zeta.pdf").writeBytes(ByteArray(300))
    File(this, "alfa.txt").writeBytes(ByteArray(10))
    File(this, "Éclair.md").writeBytes(ByteArray(2000))
    File(this, ".nascosto").writeText("x")
    File(this, "Bozze").apply { mkdirs(); File(this, "una.txt").writeText("1"); File(this, "due.txt").writeText("2") }
    File(this, "Archivio").mkdirs()
    File(this, "Zeta.pdf").setLastModified(3_000_000)
    File(this, "alfa.txt").setLastModified(1_000_000)
    File(this, "Éclair.md").setLastModified(2_000_000)
  }

  @Test
  fun `the sort of Files puts folders first and follows the language`() {
    val items = listOf(
      FluidFileItem("1", "zeta.txt", sizeBytes = 5, modifiedAtMillis = 30),
      FluidFileItem("2", "Éclair.txt", sizeBytes = 50, modifiedAtMillis = 10),
      FluidFileItem("3", "Cartella", isFolder = true, childCount = 2),
      FluidFileItem("4", "alfa.pdf", sizeBytes = 500, modifiedAtMillis = 20),
    )
    val byName = items.sortedWith(FluidFileSort().comparator(Locale.ITALIAN)).map { it.name }
    assertEquals(listOf("Cartella", "alfa.pdf", "Éclair.txt", "zeta.txt"), byName)
    // Data e dimensione partono dal più recente e dal più grande, come File.
    val byDate = FluidFileSort.of(FluidFileSortKey.Date)
    assertTrue(byDate.descending)
    assertEquals(listOf("Cartella", "zeta.txt", "alfa.pdf", "Éclair.txt"),
      items.sortedWith(byDate.comparator(Locale.ITALIAN)).map { it.name })
    assertEquals(listOf("Cartella", "alfa.pdf", "Éclair.txt", "zeta.txt"),
      items.sortedWith(FluidFileSort.of(FluidFileSortKey.Size).comparator(Locale.ITALIAN)).map { it.name })
    assertEquals(listOf("Cartella", "zeta.txt", "Éclair.txt", "alfa.pdf"),
      items.sortedWith(FluidFileSort().reversed().comparator(Locale.ITALIAN)).map { it.name })
    // Per tipo: l'estensione, poi il nome.
    assertEquals(listOf("Cartella", "alfa.pdf", "Éclair.txt", "zeta.txt"),
      items.sortedWith(FluidFileSort(FluidFileSortKey.Kind).comparator(Locale.ITALIAN)).map { it.name })
  }

  @Test
  fun `search ignores case and accents`() {
    val words = fluidFileSearchWords("CITTA  vecchia", Locale.ITALIAN)
    assertEquals(listOf("citta", "vecchia"), words)
    assertTrue(fluidFileMatches(FluidFileItem("1", "La città vecchia.pdf"), words, Locale.ITALIAN))
    assertFalse(fluidFileMatches(FluidFileItem("2", "La città nuova.pdf"), words, Locale.ITALIAN))
  }

  @Test
  fun `the app folder lists its files in pages and stays inside its root`() = runBlocking {
    val root = folder()
    val source = FluidFileSources.appFolder(root, "app", "In Fluid Glass")
    assertTrue(source.sorts)
    val page = source.list(FluidFileQuery(null, FluidFileSort(), limit = 3))
    assertEquals(listOf("Archivio", "Bozze", "alfa.txt"), page.items.map { it.name })
    assertEquals("3", page.next)
    val rest = source.list(FluidFileQuery(null, FluidFileSort(), cursor = page.next, limit = 3))
    assertEquals(listOf("Éclair.md", "Zeta.pdf"), rest.items.map { it.name })
    assertNull(rest.next)
    val bozze = page.items[1]
    assertTrue(bozze.isFolder)
    assertEquals(2, bozze.childCount)
    assertNull(bozze.uri)
    val pdf = rest.items[1]
    assertEquals(300L, pdf.sizeBytes)
    assertEquals(3_000_000L, pdf.modifiedAtMillis)
    assertEquals("file", pdf.uri?.scheme)
    assertNotNull(pdf.viewerFile())
    // Dentro una cartella, e il percorso relativo come id.
    val inside = source.list(FluidFileQuery(bozze.id))
    assertEquals(listOf("Bozze/due.txt", "Bozze/una.txt"), inside.items.map { it.id })
    // Per data: il più recente prima, le cartelle sempre in cima.
    val byDate = source.list(FluidFileQuery(null, FluidFileSort.of(FluidFileSortKey.Date)))
    assertEquals(listOf("Zeta.pdf", "Éclair.md", "alfa.txt"), byDate.items.filter { !it.isFolder }.map { it.name })
    try {
      source.list(FluidFileQuery("../.."))
      fail("una cartella fuori dalla radice non si legge")
    } catch (_: IOException) {
    }
  }

  @Test
  fun `a granted tree is read from its provider in pages`() = runBlocking {
    FakeTreeProvider.names["radice"] = "Documenti"
    FakeTreeProvider.children["radice"] = buildList {
      add(FakeTreeProvider.Doc("sotto", "Sottocartella", DocumentsContract.Document.MIME_TYPE_DIR))
      for (i in 1..450) add(FakeTreeProvider.Doc("f$i", "File %03d.txt".format(i), "text/plain", i.toLong(), i * 1000L))
      add(FakeTreeProvider.Doc("h", ".nascosto", "text/plain"))
    }
    val tree = FakeTreeProvider.tree("radice")
    val source = FluidFileSources.documentTree(context, tree, "Documenti", "Drive")
    val first = source.list(FluidFileQuery(null))
    assertEquals(200, first.items.size)
    assertTrue(first.items.first().isFolder)
    assertEquals("Sottocartella", first.items.first().name)
    assertEquals("File 001.txt", first.items[1].name)
    val file = first.items[1]
    assertEquals(DocumentsContract.buildDocumentUriUsingTree(tree, "f1"), file.uri)
    assertEquals(1L, file.sizeBytes)
    assertEquals("text/plain", file.mimeType)
    // Le pagine dopo non rileggono il provider: la cartella è letta una volta.
    val queries = FakeTreeProvider.queries
    val second = source.list(FluidFileQuery(null, cursor = first.next))
    val third = source.list(FluidFileQuery(null, cursor = second.next))
    assertEquals(queries, FakeTreeProvider.queries)
    assertEquals(450, first.items.size + second.items.size + third.items.size - 1)
    assertNull(third.next)
    // Il permesso tolto: un errore che il browser mostra con «Riprova».
    FakeTreeProvider.denied = true
    try {
      source.list(FluidFileQuery(null))
      fail("senza permesso la cartella non si legge")
    } catch (_: IOException) {
    }
  }

  @Test
  fun `a granted location keeps its persistent permission until it is removed`() = runBlocking {
    FakeTreeProvider.names["primary:Documents"] = "Documents"
    val locations = FluidFileLocations.of(context)
    locations.reload()
    assertTrue(locations.sources.isEmpty())
    val tree = FakeTreeProvider.tree("primary:Documents")
    val added = locations.add(tree)
    assertNotNull(added)
    assertEquals("Documents", added!!.title)
    assertTrue(locations.owns(added))
    assertTrue(context.contentResolver.persistedUriPermissions.any {
      it.uri == tree && it.isReadPermission
    })
    // Una nuova lettura (un altro avvio) la ritrova.
    val again = FluidFileLocations.of(context).apply { reload() }
    assertEquals(listOf("Documents"), again.sources.map { it.title })
    locations.remove(added)
    assertTrue(locations.sources.isEmpty())
    assertTrue(context.contentResolver.persistedUriPermissions.none { it.uri == tree })
  }

  @Test
  fun `removing a location the export also uses hides it and keeps the permission`() = runBlocking {
    FakeTreeProvider.names["primary:Export"] = "Export"
    val tree = FakeTreeProvider.tree("primary:Export")
    // Un'altra funzione dell'app (l'export) ha preso lettura e scrittura sulla stessa cartella.
    val both = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    context.contentResolver.takePersistableUriPermission(tree, both)
    val locations = FluidFileLocations.of(context)
    locations.reload()
    val shown = locations.sources.single()
    assertEquals("Export", shown.title)
    locations.remove(shown)
    assertTrue("l'export tiene lettura e scrittura", context.contentResolver.persistedUriPermissions.any {
      it.uri == tree && it.isReadPermission && it.isWritePermission
    })
    assertTrue("lo sfoglia-file non la mostra piu'", locations.sources.isEmpty())
    assertTrue("nemmeno al prossimo avvio", FluidFileLocations.of(context).apply { reload() }.sources.isEmpty())
    // Riscelta dal selettore, torna fra le posizioni.
    assertNotNull(locations.add(tree))
    assertEquals(listOf("Export"), locations.sources.map { it.title })
  }

  @Test
  fun `removing a location the app already had before keeps its permission`() = runBlocking {
    FakeTreeProvider.names["primary:Import"] = "Import"
    val tree = FakeTreeProvider.tree("primary:Import")
    // L'app la teneva gia' in sola lettura (un'importazione), prima che lo sfoglia-file la aggiungesse.
    context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val locations = FluidFileLocations.of(context)
    val added = locations.add(tree)!!
    locations.remove(added)
    assertTrue("l'importazione tiene la lettura", context.contentResolver.persistedUriPermissions.any {
      it.uri == tree && it.isReadPermission
    })
    assertTrue(locations.sources.isEmpty())
  }

  @Test
  fun `the pages of the app folder come from one reading`() = runBlocking {
    val root = File(context.filesDir, "pagine-${System.nanoTime()}").apply { mkdirs() }
    for (i in 1..450) File(root, "File %03d.txt".format(Locale.ROOT, i)).writeText("$i")
    val source = FluidFileSources.appFolder(root, "app", "In Fluid Glass")
    val first = source.list(FluidFileQuery(null))
    assertEquals(200, first.items.size)
    // Un file nuovo arriva mentre si scorre: le pagine dopo restano quelle della prima lettura (niente doppioni).
    File(root, "File 000.txt").writeText("0")
    val second = source.list(FluidFileQuery(null, cursor = first.next))
    val third = source.list(FluidFileQuery(null, cursor = second.next))
    val all = (first.items + second.items + third.items).map { it.name }
    assertEquals("nessun doppione fra le pagine", all.size, all.toSet().size)
    assertEquals(450, all.size)
    // Una lettura da capo (senza segno) vede il file nuovo.
    assertEquals("File 000.txt", source.list(FluidFileQuery(null)).items.first().name)
  }
}
