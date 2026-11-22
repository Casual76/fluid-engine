package dev.antigravity.fluidengine.viewer

import android.content.ContextWrapper
import dev.antigravity.fluidengine.ui.fluid.FluidFileKind
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.viewer.prova.FluidProvaTestFamily
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * F6C.8 · Il registro dei moduli dei visori, punto per punto della lista d'accettazione (visori-2 §6):
 * (1) il modulo di prova si registra da solo; (2) l'ordine dei candidati; (3) il ripiego; (4) gli eseguibili;
 * (9) la compatibilità. La sorgente (5) sta in `FluidViewerSourceTest`, la build (8) in `FluidViewerFamiliesBuildTest`.
 */
class FluidViewerRegistryTest {

  /** Un contesto che non serve a niente: i tentativi di questi test non toccano Android. */
  private val context = object : ContextWrapper(null) {}

  private class TestFamily(
    override val id: String,
    override val types: List<FluidViewerFamilyType>,
    private val result: suspend (FluidViewerRequest) -> FluidViewerOpened? = { FluidViewerOpened(FluidViewerContent.Custom {}) },
  ) : FluidViewerFamily {
    val calls = AtomicInteger()
    val seen = mutableListOf<String>()

    override suspend fun open(request: FluidViewerRequest): FluidViewerOpened? {
      calls.incrementAndGet()
      seen += request.type.id
      return result(request)
    }
  }

  private fun type(
    id: String, extensions: Set<String> = emptySet(), mimes: Set<String> = emptySet(),
    signature: FluidViewerSignature? = null, priority: Int = 0, preset: FluidViewerPreset = FluidViewerPreset.Custom,
  ) = FluidViewerFamilyType(id, id.uppercase(), extensions, mimes, signature, priority = priority, preset = preset)

  private fun order(families: List<FluidViewerFamily>, mime: String?, name: String, header: ByteArray? = null, entries: Set<String>? = null) =
    FluidViewerResolver.candidates(families, mime, name, header, entries).map { (it.family?.id ?: "serie") + ":" + (it.familyType?.id ?: it.type.preset.toString()) }

  private fun temp(name: String, bytes: ByteArray): File {
    val dir = Files.createTempDirectory("f6c8").toFile()
    return File(dir, name).apply { writeBytes(bytes) }
  }

  private fun zip(vararg entries: String): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    ZipOutputStream(out).use { z ->
      for (entry in entries) {
        z.putNextEntry(ZipEntry(entry))
        z.write("contenuto di $entry".toByteArray())
        z.closeEntry()
      }
    }
    return out.toByteArray()
  }

  /** Una sorgente in memoria che conta le aperture e la chiusura. */
  private class CountingSource(override val name: String, override val mimeType: String?, private val bytes: ByteArray) : FluidViewerSource {
    val opens = AtomicInteger()
    @Volatile var closed = false
    override suspend fun open(): InputStream {
      opens.incrementAndGet()
      return ByteArrayInputStream(bytes)
    }
    override suspend fun seekable(): android.os.ParcelFileDescriptor = throw UnsupportedOperationException("non serve qui")
    override fun close() {
      closed = true
    }
  }

  private val office = TestFamily("office", listOf(
    type("docx", setOf("docx", "docm"), setOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
      FluidViewerSignature.zipEntry("word/document.xml"), preset = FluidViewerPreset.Pages),
  ))
  private val archive = TestFamily("archive", listOf(
    type("zip", setOf("zip"), setOf("application/zip"), FluidViewerSignature.bytes(0x50, 0x4B, 0x03, 0x04)),
  ))
  private val heic = byteArrayOf(0, 0, 0, 0x18) + "ftypheic".toByteArray()
  private fun graphics(open: suspend (FluidViewerRequest) -> FluidViewerOpened? = { FluidViewerOpened(FluidViewerContent.Custom {}) }) =
    TestFamily("graphics", listOf(
      type("heic", setOf("heic", "heif"), setOf("image/heic"), FluidViewerSignature.ascii("ftypheic", offset = 4), priority = -10,
        preset = FluidViewerPreset.Image),
      type("svg", setOf("svg", "svgz"), setOf("image/svg+xml")),
    ), open)

  // (1) Il modulo di prova.

  @Test
  fun `the test module in src test declares prova through META-INF services and the resolver finds it`() {
    val prova = FluidViewerRegistry.families.filterIsInstance<FluidProvaTestFamily>()
    assertEquals("trovato con ServiceLoader, una volta", 1, prova.size)
    val type = FluidViewerType.of(null, "nota.prova")
    assertEquals("prova", type.family)
    assertEquals("prova", type.familyType)
    assertEquals("File di prova", type.label)
    assertEquals(FluidViewerPreset.Custom, type.preset)
    // `FluidFileKind.from` non sa niente di «.prova» (Other): vale quello della famiglia.
    assertEquals(FluidFileKind.Other, FluidFileKind.from(null, "nota.prova"))
    assertEquals(FluidFileKind.Text, type.kind)
    // Dal MIME senza estensione, e dalla firma senza nome né MIME.
    assertEquals("prova", FluidViewerType.of("application/x-fluid-prova", "senza-nome").family)
    assertEquals("prova", FluidViewerType.of(null, "scaricato", "FLUIDPROVA\nuno".toByteArray()).family)
    // Gli altri tipi non cambiano: il modulo aggiunge, non toglie.
    assertNull(FluidViewerType.of(null, "foto.png").family)
    assertEquals(FluidViewerPreset.Unknown, FluidViewerType.of(null, "tesi.docx").preset)
  }

  @Test
  fun `the whole round of the prova module, from the file to the page and back to closed`() = runBlocking {
    val file = temp("nota.prova", "FLUIDPROVA\nuno\ndue\ntre".toByteArray())
    val opened = FluidProvaTestFamily.opened.get()
    val closed = FluidProvaTestFamily.closed.get()
    val opening = fluidOpenWithRegistry(context, FluidViewerSource.of(file, deleteOnClose = true), FluidViewerRegistry.families,
      FluidViewerOptions(), null, null, null)
    assertTrue(opening.content is FluidViewerContent.Custom)
    assertEquals("prova", opening.type.family)
    assertNull(opening.failure)
    assertEquals(listOf(FluidViewerDetail("Righe", "3")), opening.details)
    assertEquals(opened + 1, FluidProvaTestFamily.opened.get())
    assertTrue("il documento tiene la sorgente", file.exists())
    requireNotNull(opening.resource).close()
    assertEquals(closed + 1, FluidProvaTestFamily.closed.get())
    assertFalse("la sorgente si chiude con il documento", file.exists())
  }

  @Test
  fun `a prova file that is not one goes to unknown and leaves nothing open`() = runBlocking {
    val file = temp("finto.prova", "un testo qualunque".toByteArray())
    val opening = fluidOpenWithRegistry(context, FluidViewerSource.of(file, deleteOnClose = true), FluidViewerRegistry.families,
      FluidViewerOptions(), null, null, null)
    assertTrue(opening.content is FluidViewerContent.Unknown)
    assertNull("«non questo» non è un errore", opening.failure)
    assertEquals("prova", opening.type.family)
    assertFalse("la sorgente si chiude subito", file.exists())
  }

  @Test
  fun `engine-viewer does not know the prova module`() {
    val main = File(System.getProperty("user.dir") ?: ".").resolve("src/main").canonicalFile
    val sources = main.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    assertTrue("sorgenti letti in $main", sources.size > 10)
    val pattern = Regex("""FluidProva|viewer\.prova|"prova"|\.prova\b""")
    val found = sources.flatMap { f -> f.readLines().withIndex().filter { pattern.containsMatchIn(it.value) }.map { "${f.name}:${it.index + 1}" } }
    assertEquals("il risolutore non nomina il modulo di prova", emptyList<String>(), found)
  }

  @Test
  fun `install and uninstall change the families without touching the discovered ones`() {
    val docx = TestFamily("test-office", listOf(type("docx", setOf("docx"))))
    try {
      FluidViewerRegistry.install(docx)
      assertTrue(FluidViewerRegistry.families.any { it === docx })
      assertEquals("test-office", FluidViewerType.of(null, "tesi.docx").family)
      assertTrue(FluidViewerRegistry.families.any { it is FluidProvaTestFamily })
      // Una famiglia installata con l'id di una trovata la sostituisce.
      val other = TestFamily("test-office", listOf(type("docx2", setOf("docx"))))
      FluidViewerRegistry.install(other)
      assertEquals(1, FluidViewerRegistry.families.count { it.id == "test-office" })
      assertSame(other, FluidViewerRegistry.families.single { it.id == "test-office" })
    } finally {
      FluidViewerRegistry.uninstall("test-office")
    }
    assertTrue(FluidViewerRegistry.families.none { it.id == "test-office" })
    assertEquals(FluidViewerPreset.Unknown, FluidViewerType.of(null, "tesi.docx").preset)
    assertTrue(FluidViewerRegistry.families.any { it is FluidProvaTestFamily })
  }

  // (2) L'ordine: firma, poi estensione, poi MIME, poi priorità.

  @Test
  fun `a signature beats an extension, which beats a mime`() {
    val byName = TestFamily("a", listOf(type("dat-nome", setOf("dat"))))
    val bySignature = TestFamily("b", listOf(type("dat-firma", signature = FluidViewerSignature.ascii("MAGI"))))
    val byMime = TestFamily("c", listOf(type("dat-mime", mimes = setOf("application/x-dat"))))
    val families = listOf(byMime, byName, bySignature)
    assertEquals(listOf("b:dat-firma", "a:dat-nome", "c:dat-mime"),
      order(families, "application/x-dat", "x.dat", "MAGIA".toByteArray()))
    // Senza i byte la firma tace, e l'estensione torna davanti.
    assertEquals(listOf("a:dat-nome", "c:dat-mime"), order(families, "application/x-dat", "x.dat"))
    // Un MIME con parametri e maiuscole vale come quello pulito; `tipo/*` vale per il gruppo. A parità col testo di
    // serie (anche lui dal MIME) vince il preset di serie.
    val wild = TestFamily("d", listOf(type("tutto-testo", mimes = setOf("text/*"))))
    assertEquals(listOf("serie:Text", "d:tutto-testo"), order(listOf(wild), "Text/X-Strano; charset=UTF-8", "senza-nome"))
    assertEquals(listOf("d:tutto-testo"), order(listOf(wild), "text/rtf", "senza-nome"))
  }

  @Test
  fun `the priority breaks ties, and the built-in preset wins a full tie`() {
    val low = TestFamily("a", listOf(type("csv-basso", setOf("csv"), priority = 1)))
    val high = TestFamily("b", listOf(type("csv-alto", setOf("csv"), priority = 5)))
    assertEquals(listOf("b:csv-alto", "a:csv-basso", "serie:Text"), order(listOf(low, high), null, "dati.csv"))
    // A parità di forza e priorità 0 il preset di serie resta davanti: il CSV della famiglia dati, che vuole la
    // tabella, dichiara una priorità positiva.
    val tie = TestFamily("data", listOf(type("csv", setOf("csv"))))
    assertEquals(listOf("serie:Text", "data:csv"), order(listOf(tie), null, "dati.csv"))
    val table = TestFamily("data", listOf(type("csv", setOf("csv"), priority = 10, preset = FluidViewerPreset.Grid)))
    assertEquals(listOf("data:csv", "serie:Text"), order(listOf(table), null, "dati.csv"))
    assertEquals(FluidViewerPreset.Grid, FluidViewerResolver.candidates(listOf(table), null, "dati.csv", null, null).first().type.preset)
  }

  @Test
  fun `a zip with word document xml is a Word document, and a broken docx is found to be a zip`() = runBlocking {
    val families = listOf(office, archive)
    val word = temp("lettera.zip", zip("[Content_Types].xml", "word/document.xml", "word/styles.xml"))
    val probe = FluidViewerProbe.read(FluidViewerSource.of(word), families)
    assertEquals(setOf("[Content_Types].xml", "word/document.xml", "word/styles.xml"), probe.entries)
    assertEquals("la voce del pacchetto vale più dei primi byte e dell'estensione",
      listOf("office:docx", "archive:zip"), order(families, null, "lettera.zip", probe.header, probe.entries))
    val broken = temp("rotto.docx", zip("solo.txt"))
    val brokenProbe = FluidViewerProbe.read(FluidViewerSource.of(broken), families)
    assertEquals("i primi byte dello ZIP valgono più dell'estensione .docx",
      listOf("archive:zip", "office:docx"), order(families, null, "rotto.docx", brokenProbe.header, brokenProbe.entries))
    // Un file che non è uno ZIP non fa leggere nessun indice.
    val text = temp("note.docx", "non sono uno zip".toByteArray())
    assertNull(FluidViewerProbe.read(FluidViewerSource.of(text), families).entries)
  }

  @Test
  fun `a family type beats the table of known types without a decoder`() {
    assertEquals(FluidViewerPreset.Unknown, FluidViewerType.builtin(null, "tesi.docx").type.preset)
    assertEquals(listOf("office:docx"), order(listOf(office), null, "tesi.docx"))
    assertEquals(listOf("graphics:svg"), order(listOf(graphics()), "image/svg+xml", "logo"))
    // Senza famiglie il tipo resta sconosciuto, con «Apri con…».
    assertEquals(emptyList<String>(), order(emptyList(), null, "tesi.docx"))
  }

  @Test
  fun `the heic of graphics at minus ten runs only if ImageDecoder fails`() = runBlocking {
    val first = graphics()
    // Anche quando la famiglia lo riconosce dai byte, il preset di serie confermato dalla firma resta davanti.
    assertEquals(listOf("serie:Image", "graphics:heic"), order(listOf(first), null, "foto.heic", heic))
    assertEquals(listOf("serie:Image", "graphics:heic"), order(listOf(first), "image/heic", "IMG_0001", heic))
    // ImageDecoder la legge (Android 9 e oltre): la famiglia non parte.
    val candidates = FluidViewerResolver.candidates(listOf(first), null, "foto.heic", heic, null)
    val decoded = FluidViewerAttempt(candidates.first().type, FluidViewerContent.Image(1f) {}, emptyList(), null)
    val ok = fluidOpenCandidates(candidates, candidates.first().type, "foto.heic", FluidViewerFailures(),
      builtin = { decoded }, family = { c -> requireNotNull(c.family).open(FluidViewerRequest(context, CountingSource("foto.heic", null, heic), requireNotNull(c.familyType), FluidViewerOptions(), null, null, null)) })
    assertSame(decoded, ok.attempt)
    assertEquals(0, first.calls.get())
    // ImageDecoder non la legge (sotto Android 9): parte la famiglia, e il tipo lo dice.
    val second = graphics()
    val failures = FluidViewerFailures()
    val fallback = fluidOpenCandidates(FluidViewerResolver.candidates(listOf(second), null, "foto.heic", heic, null),
      FluidViewerType.builtin(null, "foto.heic").type, "foto.heic", failures,
      builtin = {
        failures.record(FluidViewerException(FluidViewerFailure.Unsupported, "HEIF sotto Android 9"))
        null
      },
      family = { c -> requireNotNull(c.family).open(FluidViewerRequest(context, CountingSource("foto.heic", null, heic), requireNotNull(c.familyType), FluidViewerOptions(), null, null, null)) })
    assertNotNull(fallback.attempt)
    assertEquals(1, second.calls.get())
    assertEquals("graphics", fallback.type.family)
    assertEquals(listOf("heic"), second.seen)
  }

  // (3) Il ripiego.

  @Test
  fun `a null or an exception passes to the next candidate, and the first exception stays as the reason`() = runBlocking {
    val calls = mutableListOf<String>()
    val nothing = TestFamily("a", listOf(type("x1", setOf("x"), priority = 4))) { calls += "a"; null }
    val locked = TestFamily("b", listOf(type("x2", setOf("x"), priority = 3))) {
      calls += "b"; throw FluidViewerException(FluidViewerFailure.Protected, "protetto")
    }
    val broken = TestFamily("c", listOf(type("x3", setOf("x"), priority = 2))) { calls += "c"; error("parser rotto") }
    val good = TestFamily("d", listOf(type("x4", setOf("x"), priority = 1))) { calls += "d"; FluidViewerOpened(FluidViewerContent.Custom {}) }
    val failures = FluidViewerFailures()
    val families = listOf(good, broken, locked, nothing)
    val outcome = fluidOpenCandidates(FluidViewerResolver.candidates(families, null, "f.x", null, null),
      FluidViewerType.builtin(null, "f.x").type, "f.x", failures, builtin = { fail("nessun preset di serie per .x"); null },
      family = { c -> requireNotNull(c.family).open(FluidViewerRequest(context, CountingSource("f.x", null, ByteArray(0)), requireNotNull(c.familyType), FluidViewerOptions(), null, null, null)) })
    assertEquals(listOf("a", "b", "c", "d"), calls)
    assertEquals("d", outcome.type.family)
    assertNotNull(outcome.attempt)
    assertEquals("il primo motivo resta", FluidViewerFailure.Protected, failures.first?.failure)
  }

  @Test
  fun `without a candidate that opens, the unknown keeps the reason, and without candidates it is truly unknown`() = runBlocking {
    val locked = TestFamily("b", listOf(type("x", setOf("x")))) { throw FluidViewerException(FluidViewerFailure.Protected, "protetto") }
    val failures = FluidViewerFailures()
    val outcome = fluidOpenCandidates(FluidViewerResolver.candidates(listOf(locked), null, "f.x", null, null),
      FluidViewerType.builtin(null, "f.x").type, "f.x", failures, builtin = { null },
      family = { c -> requireNotNull(c.family).open(FluidViewerRequest(context, CountingSource("f.x", null, ByteArray(0)), requireNotNull(c.familyType), FluidViewerOptions(), null, null, null)) })
    assertNull(outcome.attempt)
    assertEquals(FluidViewerFailure.Protected, outcome.failure?.failure)
    assertEquals("b", outcome.type.family)
    val none = fluidOpenCandidates(emptyList(), FluidViewerType.builtin(null, "f.x").type, "f.x", FluidViewerFailures(),
      builtin = { null }, family = { null })
    assertNull(none.attempt)
    assertNull(none.failure)
    assertEquals(FluidViewerPreset.Unknown, none.type.preset)
  }

  @Test
  fun `a family that breaks counts as a corrupt file, one out of memory as too large`() = runBlocking {
    for ((thrown, expected) in listOf<Pair<Throwable, FluidViewerFailure>>(
      IllegalStateException("rotto") to FluidViewerFailure.Corrupt,
      java.io.IOException("letto male") to FluidViewerFailure.Corrupt,
      OutOfMemoryError("troppo") to FluidViewerFailure.TooLarge,
    )) {
      val family = TestFamily("a", listOf(type("x", setOf("x")))) { throw thrown }
      val failures = FluidViewerFailures()
      val outcome = fluidOpenCandidates(FluidViewerResolver.candidates(listOf(family), null, "f.x", null, null),
        FluidViewerType.builtin(null, "f.x").type, "f.x", failures, builtin = { null },
        family = { c -> requireNotNull(c.family).open(FluidViewerRequest(context, CountingSource("f.x", null, ByteArray(0)), requireNotNull(c.familyType), FluidViewerOptions(), null, null, null)) })
      assertNull(outcome.attempt)
      assertEquals(thrown.toString(), expected, outcome.failure?.failure)
      assertSame(thrown, outcome.failure?.cause)
    }
  }

  @Test
  fun `the source is closed when every candidate fails and when the opening is cancelled`() = runBlocking {
    val failing = TestFamily("a", listOf(type("x", setOf("x")))) { throw FluidViewerException(FluidViewerFailure.Corrupt, "no") }
    val source = CountingSource("f.x", null, "abc".toByteArray())
    val opening = fluidOpenWithRegistry(context, source, listOf(failing), FluidViewerOptions(), null, null, null)
    assertTrue(opening.content is FluidViewerContent.Unknown)
    assertEquals(FluidViewerFailure.Corrupt, opening.failure?.failure)
    assertTrue(source.closed)

    val entered = CompletableDeferred<Unit>()
    val stuck = TestFamily("b", listOf(type("x", setOf("x")))) { entered.complete(Unit); awaitCancellation() }
    val second = CountingSource("f.x", null, "abc".toByteArray())
    val job = launch(Dispatchers.Default) { fluidOpenWithRegistry(context, second, listOf(stuck), FluidViewerOptions(), null, null, null) }
    withTimeout(5_000) { entered.await() }
    assertFalse(second.closed)
    job.cancelAndJoin()
    assertTrue("annullata, la sorgente si chiude", second.closed)
  }

  // (4) Un eseguibile non arriva mai a una famiglia.

  @Test
  fun `an executable never reaches a family, not even with a document mime`() = runBlocking {
    val docxMime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    val everything = TestFamily("tutto", listOf(type("doc", setOf("docx", "sh2", "txt"), setOf(docxMime, "text/*", "image/*"),
      FluidViewerSignature.bytes(0x4D, 0x5A))))
    for ((mime, name) in listOf(docxMime to "setup.exe", "application/vnd.android.package-archive" to "relazione.docx",
      docxMime to "Relazione.DOCX.apk", "image/png" to "script.sh", null to "app.jar")) {
      assertEquals(name, emptyList<String>(), order(listOf(everything), mime, name, byteArrayOf(0x4D, 0x5A)))
      val source = CountingSource(name, mime, byteArrayOf(0x4D, 0x5A, 0, 0))
      val opening = fluidOpenWithRegistry(context, source, listOf(everything), FluidViewerOptions(), null, null, null)
      assertTrue(name, opening.content is FluidViewerContent.Unknown)
      assertTrue(name, opening.type.executable)
      assertNull(name, opening.type.family)
      assertEquals("$name: mai letto", 0, source.opens.get())
      assertTrue(name, source.closed)
    }
    assertEquals("la famiglia non è mai stata chiamata", 0, everything.calls.get())
    try {
      FluidViewerRegistry.install(everything)
      val type = FluidViewerType.of(docxMime, "setup.exe")
      assertTrue(type.executable)
      assertEquals(FluidViewerPreset.Unknown, type.preset)
      assertNull(type.family)
    } finally {
      FluidViewerRegistry.uninstall("tutto")
    }
  }

  @Test
  fun `a family cannot even declare an executable`() {
    for (build in listOf<() -> FluidViewerFamilyType>(
      { FluidViewerFamilyType("apk", "App", extensions = setOf("apk")) },
      { FluidViewerFamilyType("exe", "Programma", extensions = setOf(".EXE")) },
      { FluidViewerFamilyType("apk", "App", mimeTypes = setOf("application/vnd.android.package-archive")) },
      { FluidViewerFamilyType("sh", "Script", mimeTypes = setOf("text/x-shellscript")) },
      { FluidViewerFamilyType("bin", "Binario", kind = FluidFileKind.Executable) },
    )) {
      try {
        build()
        fail("un eseguibile dichiarato da una famiglia")
      } catch (_: IllegalArgumentException) {
      }
    }
  }

  // (9) Compatibilità: campi in coda con un valore di serie, nessun enum nuovo.

  @Test
  fun `the new fields are at the end with a default, and the old calls still mean the same`() {
    val old = FluidViewerType(FluidViewerPreset.Text, FluidFileKind.Text, "text/plain", null)
    assertNull(old.family)
    assertNull(old.label)
    assertNull(old.familyType)
    assertEquals(old, old.copy(preset = FluidViewerPreset.Text))
    val options = FluidViewerOptions(1, 2, 3L, false, 4L)
    assertEquals(FluidViewerSource.DefaultMaxCopyBytes, options.sourceMaxCopyBytes)
    assertEquals(listOf("Grid", "Custom"), listOf(FluidViewerPreset.Grid, FluidViewerPreset.Custom).map { it.toString() })
    assertEquals(8, setOf(FluidViewerPreset.Image, FluidViewerPreset.Video, FluidViewerPreset.Audio, FluidViewerPreset.Pages,
      FluidViewerPreset.Text, FluidViewerPreset.Unknown, FluidViewerPreset.Grid, FluidViewerPreset.Custom).size)
    val type = FluidViewerFamilyType("docx", "Documento Word")
    assertEquals(FluidFileKind.Document, type.kind)
    assertEquals(0, type.priority)
    assertEquals(FluidViewerPreset.Custom, type.preset)
  }

  @Test
  fun `two equal signatures are equal and say what they match`() {
    assertEquals(FluidViewerSignature.ascii("%PDF"), FluidViewerSignature.bytes(0x25, 0x50, 0x44, 0x46))
    assertEquals(FluidViewerSignature.zipEntry("word/document.xml"), FluidViewerSignature.zipEntry("/word/document.xml"))
    assertFalse(FluidViewerSignature.ascii("%PDF") == FluidViewerSignature.ascii("%PDF", offset = 1))
    val tiff = FluidViewerSignature.anyOf(FluidViewerSignature.ascii("II*"), FluidViewerSignature.ascii("MM\u0000*"))
    assertEquals(FluidViewerStrength.Bytes, tiff.strength("MM\u0000*rest".toByteArray(), null))
    assertEquals(0, tiff.strength("PK".toByteArray(), null))
    val folder = FluidViewerSignature.zipEntry("ppt/slides/")
    assertEquals(FluidViewerStrength.Entry, folder.strength(null, setOf("ppt/slides/slide1.xml")))
    assertEquals(0, folder.strength(null, setOf("ppt/presentation.xml")))
    try {
      FluidViewerSignature.bytes(0x41, offset = FluidViewerSignature.HeaderBytes)
      fail("oltre la testa letta")
    } catch (_: IllegalArgumentException) {
    }
  }
}
