package dev.antigravity.fluidengine.viewer.data

import android.content.Context
import android.net.Uri
import dev.antigravity.fluidengine.ui.fluid.FluidWebRequest
import dev.antigravity.fluidengine.ui.fluid.FluidWebResponse
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.viewer.FluidViewerFailure
import dev.antigravity.fluidengine.viewer.FluidViewerPreset
import dev.antigravity.fluidengine.viewer.FluidViewerRegistry
import dev.antigravity.fluidengine.viewer.FluidViewerSource
import dev.antigravity.fluidengine.viewer.FluidViewerType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream

/**
 * F6C.13 · La famiglia «data» con le API di Android (XmlPullParser, Uri, ParcelFileDescriptor), su Robolectric: XML e
 * plist, EPUB, le regole della rete chiusa, e il giro intero dal registro alla pagina.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DataFamilyTest {
  private lateinit var context: Context
  private lateinit var dir: File

  @Before fun setUp() {
    context = RuntimeEnvironment.getApplication()
    dir = File(context.cacheDir, "prove-data").apply { mkdirs() }
  }

  @After fun tearDown() {
    dir.deleteRecursively()
  }

  private fun file(name: String): File {
    val out = File(dir, name.substringAfterLast('/'))
    requireNotNull(javaClass.classLoader!!.getResourceAsStream("data/$name")) { "manca data/$name" }.use { input ->
      out.outputStream().use { input.copyTo(it) }
    }
    return out
  }

  private fun bytes(name: String) = requireNotNull(javaClass.classLoader!!.getResourceAsStream("data/$name")).use { it.readBytes() }

  // --- Il registro --------------------------------------------------------------------------------------------------

  @Test fun `the family is found by ServiceLoader and owns its types`() {
    val family = FluidViewerRegistry.families.firstOrNull { it.id == FluidDataFamily.Id }
    assertNotNull("la famiglia «data» si registra da sola (META-INF/services)", family)
    fun type(name: String, mime: String? = null): FluidViewerType = FluidViewerType.of(mime, name)
    for ((name, id) in listOf(
      "nota.md" to "markdown", "pagina.html" to "html", "spese.csv" to "csv", "misure.tsv" to "tsv", "config.json" to "json",
      "impostazioni.jsonc" to "json", "eventi.ndjson" to "ndjson", "catalogo.xml" to "xml", "Info.plist" to "plist",
      "libro3.epub" to "epub", "messaggio.eml" to "eml", "riunioni.ics" to "ics", "contatti.vcf" to "vcf",
    )) {
      val t = type(name)
      assertEquals(name, "data", t.family)
      assertEquals(name, id, t.familyType)
    }
    assertEquals(FluidViewerPreset.Grid, type("spese.csv").preset)
    assertEquals(FluidViewerPreset.Custom, type("nota.md").preset)
    assertEquals("ics", type("invito", "text/calendar").familyType)
    // Un eseguibile non arriva mai alla famiglia, anche con un MIME da dati.
    assertNull(type("script.sh", "application/json").family)
    assertNull(type("pacchetto.apk", "text/csv").family)
  }

  @Test fun `every test file opens through the registry with its page and its info`() = runBlocking {
    val expected = mapOf(
      "nota.md" to FluidViewerContent.Custom::class, "pagina.html" to FluidViewerContent.Custom::class,
      "spese.csv" to FluidViewerContent.Grid::class, "misure.tsv" to FluidViewerContent.Grid::class,
      "config.json" to FluidViewerContent.Custom::class, "impostazioni.jsonc" to FluidViewerContent.Custom::class,
      "eventi.ndjson" to FluidViewerContent.Custom::class, "rotto.json" to FluidViewerContent.Custom::class,
      "catalogo.xml" to FluidViewerContent.Custom::class, "Info.plist" to FluidViewerContent.Custom::class,
      "binario.plist" to FluidViewerContent.Custom::class, "libro3.epub" to FluidViewerContent.Custom::class,
      "libro2.epub" to FluidViewerContent.Custom::class, "messaggio.eml" to FluidViewerContent.Custom::class,
      "charset.eml" to FluidViewerContent.Custom::class, "riunioni.ics" to FluidViewerContent.Custom::class,
      "contatti.vcf" to FluidViewerContent.Custom::class,
    )
    for ((name, kind) in expected) {
      val document = FluidViewerRegistry.open(context, FluidViewerSource.of(file(name)))
      try {
        assertNull("$name: ${document.failure}", document.failure)
        assertEquals(name, "data", document.type.family)
        assertTrue("$name: ${document.content::class}", kind.isInstance(document.content))
        assertTrue("$name: dati del pannello", document.details.consistentCopy().any { it.section == null })
        assertTrue("$name: sezioni del pannello", document.details.consistentCopy().any { it.section != null })
        val info = fluidDataViewerInfo(document)
        assertTrue(name, info.facts.isNotEmpty())
        assertTrue(name, info.sections.isNotEmpty())
      } finally {
        document.close()
      }
    }
  }

  @Test fun `a protected epub says Protetto da DRM with the system, the reason and the book`() = runBlocking {
    val document = FluidViewerRegistry.open(context, FluidViewerSource.of(file("protetto.epub")))
    try {
      // Non lo sconosciuto generico: la pagina del libro protetto, con i dati del pannello.
      assertTrue(document.content is FluidViewerContent.Custom)
      assertNull(document.failure)
      val facts = document.details.consistentCopy().filter { it.section == null }.map { it.value }
      assertTrue(facts.toString(), "Protetto da DRM" in facts)
      assertTrue(facts.toString(), "cifratura del file" in facts)
      val protection = document.details.consistentCopy().filter { it.section == "Protezione" }.associate { it.label to it.value }
      assertEquals("1", protection["Risorse cifrate"])
      assertTrue(protection["Motivo"], protection["Motivo"]!!.contains("aes128-cbc"))
      assertEquals("Quaderno di campo", document.details.consistentCopy().first { it.section == "Libro" && it.label == "Titolo" }.value)
    } finally {
      document.close()
    }
    // Chi vuole solo leggere il libro (la miniatura) riceve ancora l'eccezione «protetto».
    zip("protetto.epub").use { zip ->
      val drm = DataEpub.protection(zip).drm!!
      assertEquals("cifratura del file", drm.system)
      val error = runCatching { DataEpub.read(zip) }.exceptionOrNull() as dev.antigravity.fluidengine.viewer.FluidViewerException
      assertEquals(FluidViewerFailure.Protected, error.failure)
    }
    zip("libro3.epub").use { zip ->
      val protection = DataEpub.protection(zip)
      assertNull(protection.drm)
      assertEquals(1, protection.obfuscatedFonts)
    }
  }

  @Test fun `a page without colors takes the dark theme, one with its own colors keeps them`() {
    val surface = androidx.compose.ui.graphics.Color(0xFF0D0D0F)
    val ink = androidx.compose.ui.graphics.Color(0xFFF2F2F7)
    val accent = androidx.compose.ui.graphics.Color(0xFF2ED8A0)
    val plain = String(bytes("pagina.html"), Charsets.UTF_8)
    val own = String(bytes("colori.html"), Charsets.UTF_8)
    val mail = DataMime.parse(bytes("messaggio.eml")).html!!
    assertEquals(DataWebColors.None, DataWeb.colors(plain))
    assertEquals(DataWebColors.None, DataWeb.colors(mail))
    assertEquals(DataWebColors.Own, DataWeb.colors(own))
    assertEquals(DataWebColors.Own, DataWeb.colors("<table><tr><td bgcolor=\"#ffffff\">x</td></tr></table>"))
    assertEquals(DataWebColors.Own, DataWeb.colors("<p style=\"color: #333\">x</p>"))
    assertEquals(DataWebColors.Own, DataWeb.colors("<font color=\"red\">x</font>"))
    assertEquals(DataWebColors.None, DataWeb.colors("<p style=\"border-color: red; background-color: transparent\">x</p>"))
    assertEquals(DataWebColors.Scheme, DataWeb.colors("<meta name=\"color-scheme\" content=\"light dark\"><body style=\"color:#000\">"))
    assertEquals(DataWebColors.Scheme, DataWeb.colors("<style>@media (prefers-color-scheme: dark) { body { background: #000 } }</style>"))
    // Nel tema scuro: la pagina senza colori prende il fondo e l'inchiostro del tema, e chiede lo scurimento.
    val (dark, darkening) = DataWeb.forTheme(plain, dark = true, surface = surface, ink = ink, accent = accent)
    assertTrue(darkening)
    assertTrue(dark, dark.contains("<meta name=\"color-scheme\" content=\"dark\">"))
    assertTrue(dark, dark.contains("background: #0D0D0F") && dark.contains("color: #F2F2F7"))
    // Quella con i suoi colori resta com'è, senza scurimento; nel tema chiaro tutte restano come sono.
    assertEquals(own to false, DataWeb.forTheme(own, dark = true, surface = surface, ink = ink, accent = accent))
    assertEquals(plain to false, DataWeb.forTheme(plain, dark = false, surface = surface, ink = ink, accent = accent))
    val scheme = "<meta name=\"color-scheme\" content=\"light dark\">"
    assertEquals(scheme to true, DataWeb.forTheme(scheme, dark = true, surface = surface, ink = ink, accent = accent))
  }

  @Test fun `the book is set like a book`() {
    val css = DataWeb.readerCss(androidx.compose.ui.graphics.Color.White, androidx.compose.ui.graphics.Color.Black,
      androidx.compose.ui.graphics.Color.Gray, androidx.compose.ui.graphics.Color.Blue, dark = false, marginPx = 20)
    assertTrue(css, css.contains("max-width: ${DataProfile.BookColumn}px !important; margin: 0 auto !important"))
    assertTrue(css, css.contains("font-size: ${DataProfile.BookFontSize}px !important; line-height: ${DataProfile.BookLineHeight} !important"))
    assertTrue(css, css.contains("padding: 12px 20px 72px 20px"))
    assertTrue(css, css.contains("h1 { font-size: 1.9em !important"))
    assertTrue(css, css.contains("img { display: block; margin: 1.1em auto !important"))
    assertTrue(DataProfile.BookFontSize in 18..20)
    // Il capitolo porta il foglio e la viewport, e un collegamento al capitolo dopo porta al capitolo dopo.
    zip("libro3.epub").use { zip ->
      val book = DataEpub.read(zip)
      val html = DataEpub.chapterHtml(zip, book, 1, css, "light")
      assertTrue(html.contains("max-width: ${DataProfile.BookColumn}px"))
      val link = Regex("""href="(/OEBPS/Text/cap3\.xhtml#fine)"""").find(html)!!.groupValues[1]
      val target = DataWeb.target(Uri.parse("https://abc.invalid$link")) as DataWebTarget.Local
      assertEquals(2, book.chapterOf(target.path))
    }
  }

  @Test fun `the csv details are a state that gets the final count`() = runBlocking {
    val document = FluidViewerRegistry.open(context, FluidViewerSource.of(file("spese.csv")))
    try {
      val grid = (document.content as FluidViewerContent.Grid).source as DataCsvGrid
      grid.awaitIndex()
      assertEquals(6, grid.rowCount)
      assertTrue(grid.header)
      val facts = document.details.consistentCopy().filter { it.section == null }.map { it.value }
      assertTrue(facts.toString(), "5 righe" in facts)
      assertTrue(facts.toString(), "; (punto e virgola)" in facts)
      val cells = grid.cells(0, 0..5, 0..3)
      assertEquals("Caffè e pranzo", cells.first { it.row == 2 && it.column == 0 }.text)
      assertTrue(cells.first { it.row == 3 && it.column == 2 }.numeric)
    } finally {
      document.close()
    }
  }

  // --- XML e plist --------------------------------------------------------------------------------------------------

  @Test fun `xml tree with attributes, inline text, cdata, comments and entities`() {
    val tree = DataXml.parse(ByteArrayInputStream(bytes("catalogo.xml")))
    assertNull(tree.error)
    val root = tree.root!!
    assertEquals("catalogo", root.name)
    val names = root.children!!.map { it.kind to it.name }
    assertTrue(names.contains(DataKind.Attribute to "xmlns:dc"))
    assertTrue(names.contains(DataKind.Element to "dc:title"))
    assertEquals("Visori della seconda ondata", root.children!!.first { it.name == "dc:title" }.value)
    val modulo = root.children!!.first { it.name == "modulo" }
    assertEquals("data", modulo.children!!.first { it.kind == DataKind.Attribute && it.name == "id" }.value)
    assertEquals("Il <testo> dentro CDATA resta com'è", modulo.children!!.first { it.name == "nota" }.value)
    assertEquals("Caffè & latte", root.children!!.first { it.name == "testo" }.value)
    assertTrue(root.children!!.any { it.kind == DataKind.Comment })
    assertEquals("1", tree.prolog["namespaces"])
  }

  @Test fun `an invalid xml says line and column`() {
    val tree = DataXml.parse(ByteArrayInputStream(bytes("rotto.xml")))
    val error = tree.error!!
    assertEquals(4, error.line)
    assertTrue(error.toString(), error.column > 1)
    assertEquals("radice", tree.root!!.name)
    assertFalse(tree.root!!.complete)
  }

  @Test fun `plist xml and binary give the same typed tree`() {
    val xml = DataXml.parse(ByteArrayInputStream(bytes("Info.plist")))
    val binaryBytes = bytes("binario.plist")
    assertTrue(DataPlist.isBinary(binaryBytes, binaryBytes.size))
    val binary = DataPlist.parseBinary(binaryBytes, binaryBytes.size)
    assertNull(binary.error)
    assertEquals("Property list XML", xml.format)
    assertEquals("Property list binaria", binary.format)
    for (tree in listOf(xml, binary)) {
      val root = tree.root!!
      assertEquals(DataKind.Object, root.kind)
      val byName = root.children!!.associateBy { it.name }
      assertEquals("Fluid Glass", byName["CFBundleName"]!!.value)
      assertEquals(DataKind.Bool, byName["LSRequiresIPhoneOS"]!!.kind)
      assertEquals(DataKind.Array, byName["UIDeviceFamily"]!!.kind)
      assertEquals(DataKind.Number, byName["Prezzo"]!!.kind)
      assertEquals(DataKind.Date, byName["Creato"]!!.kind)
      assertEquals(DataKind.Data, byName["Icona"]!!.kind)
      assertTrue(byName["Icona"]!!.value!!.startsWith("10 byte"))
      assertEquals(DataKind.Object, byName["Impostazioni"]!!.kind)
    }
    assertNotNull(DataTreeText.document(binary, "binario.plist"))
  }

  // --- EPUB ---------------------------------------------------------------------------------------------------------

  private fun zip(name: String) = DataZip(FileInputStream(file(name)).channel)

  @Test fun `epub 3 with nav, cover, images and obfuscated fonts`() {
    zip("libro3.epub").use { zip ->
      val book = DataEpub.read(zip)
      assertEquals("3.0", book.version)
      assertEquals("Quaderno di campo", book.title)
      assertEquals(listOf("Alessio"), book.creators)
      assertEquals("it", book.language)
      assertEquals(3, book.chapters.size)
      assertEquals(listOf("Il vento", "Le misure", "La fine"), book.chapters.map { it.title })
      assertEquals(4, book.toc.size)
      assertEquals(1, book.toc[2].depth)
      assertEquals(1, book.toc[2].chapter)
      assertEquals("OEBPS/Images/cover.png", book.coverPath)
      assertEquals(1, book.obfuscatedFonts)
      assertEquals("nav.xhtml (EPUB 3)", book.tocSource)
      assertEquals(0, book.percentAt(0))
      assertTrue(book.percentAt(2) in 1..99)
      val html = DataEpub.chapterHtml(zip, book, 0, "body{}", "light")
      // Gli indirizzi relativi diventano assoluti dalla radice dello ZIP; il foglio di stile entra nel documento.
      assertTrue(html, html.contains("src=\"/OEBPS/Images/duna.png\""))
      assertTrue(html, html.contains("font-family: serif"))
      assertTrue(html, html.contains("url(\"/OEBPS/Images/duna.png\")"))
      assertFalse(html, html.contains("<link"))
      assertTrue(html, html.contains("<title></title>"))
      assertTrue(html, html.contains("<a id=\"inizio\"></a>"))
      assertTrue(html, html.contains("name=\"viewport\""))
      val chapter2 = DataEpub.chapterHtml(zip, book, 1, "", "light")
      assertTrue(chapter2, chapter2.contains("href=\"/OEBPS/Text/cap3.xhtml#fine\""))
      assertEquals(2, book.chapterOf("OEBPS/Text/cap3.xhtml"))
      // Le risorse si servono dallo ZIP; le remote no.
      val model = DataEpubModel("libro3.epub", zip, book)
      assertNotNull(model.serve("OEBPS/Images/duna.png"))
      assertNull(model.serve("OEBPS/Images/non-c-e.png"))
      val remote = model.policy.handle(FluidWebRequest(Uri.parse("https://example.com/remota.png"), "GET", false))
      assertEquals(FluidWebResponse.Block, remote)
      assertEquals(1, model.policy.blocked.get())
    }
  }

  @Test fun `epub 2 with toc ncx and the cover from the meta`() {
    zip("libro2.epub").use { zip ->
      val book = DataEpub.read(zip)
      assertEquals("2.0", book.version)
      assertEquals("toc.ncx (EPUB 2)", book.tocSource)
      assertEquals(4, book.toc.size)
      assertEquals(listOf(0, 0, 1, 0), book.toc.map { it.depth })
      assertEquals("OEBPS/Images/cover.png", book.coverPath)
      assertEquals(0, book.obfuscatedFonts)
    }
  }

  // --- La rete chiusa -----------------------------------------------------------------------------------------------

  @Test fun `the web policy serves the file and blocks everything remote`() {
    val served = ArrayList<String>()
    val policy = DataWeb.Policy(local = { path -> served += path; FluidWebResponse.Serve("image/png") { ByteArrayInputStream(ByteArray(1)) } })
    fun ask(url: String, main: Boolean = false) = policy.handle(FluidWebRequest(Uri.parse(url), "GET", main))
    assertTrue(ask("https://1234-abcd.invalid/immagini/duna.png") is FluidWebResponse.Serve)
    assertTrue(ask("https://1234-abcd.invalid/a/../../../etc/passwd") is FluidWebResponse.Serve)
    assertEquals(listOf("immagini/duna.png", "etc/passwd"), served)
    // Il percorso esce al più fino alla radice concessa: «etc/passwd» è dentro la cartella dell'app, non il sistema.
    assertEquals(FluidWebResponse.Block, ask("https://1234-abcd.invalid/document"))
    for (url in listOf("https://example.com/x.png", "http://127.0.0.1:8080/x.png", "file:///sdcard/x.png",
      "content://media/external/images/1", "ftp://example.com/x", "//example.com/x.png", "https://example.invalid.com/x.png")) {
      assertEquals(url, FluidWebResponse.Block, ask(url))
    }
    assertEquals(FluidWebResponse.Block, ask("https://1234-abcd.invalid/immagini/duna.png", main = true))
    assertEquals(7, policy.blocked.get())
    // Con il permesso dell'app, il remoto passa dal SUO client; la pagina non apre connessioni.
    var asked: Uri? = null
    policy.remote = { asked = it; FluidWebResponse.Block }
    ask("https://example.com/y.png")
    assertEquals("https://example.com/y.png", asked.toString())
  }

  @Test fun `mail cid parts are served from the message, remote images are not`() = runBlocking {
    val mail = DataMime.parse(bytes("messaggio.eml"))
    val request = FluidWebRequest(Uri.parse("cid:logo@fluid"), "GET", false)
    val policy = DataWeb.Policy(local = null, part = { id -> mail.part(id)?.let { DataWeb.serveBytes(it.type, it.body) } })
    val response = policy.handle(request)
    assertTrue(response is FluidWebResponse.Serve)
    assertEquals("image/png", (response as FluidWebResponse.Serve).mimeType)
    assertEquals(FluidWebResponse.Block, policy.handle(FluidWebRequest(Uri.parse("https://tracker.example.com/pixel.gif"), "GET", false)))
    assertEquals(1, DataWeb.remoteReferences(mail.html!!))
  }

  @Test fun `relative urls, css and self closing tags`() {
    assertEquals("OEBPS/Images/a.png", DataWeb.resolve("OEBPS/Text/", "../Images/a.png"))
    assertNull(DataWeb.resolve("OEBPS/Text/", "https://example.com/a.png"))
    assertNull(DataWeb.resolve("OEBPS/Text/", "#nota"))
    assertNull(DataWeb.resolve("OEBPS/Text/", "mailto:a@b.it"))
    assertEquals("<p class=\"x\"></p><br/><img src=\"a\"/>", DataWeb.expandSelfClosing("<p class=\"x\"/><br/><img src=\"a\"/>"))
    assertEquals("url(\"/OEBPS/Fonts/f.otf\")", DataWeb.rebaseCss("url(../Fonts/f.otf)", "OEBPS/Styles/"))
    assertTrue(DataWeb.fixedWidth("<table width=\"600\">"))
    assertFalse(DataWeb.fixedWidth("<table width=\"100%\">"))
  }

  @Test fun `the module never reaches the network by itself`() {
    // Nessuna API di rete nei sorgenti del modulo, e nessun permesso INTERNET nel manifesto.
    val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "src/main/kotlin").isDirectory || File(it, "engine-viewer-data").isDirectory }
    val module = if (File(root, "src/main/kotlin").isDirectory) root else File(root, "engine-viewer-data")
    val forbidden = listOf("java.net.URL", "HttpURLConnection", "java.net.Socket", "okhttp3", "openConnection", "URLConnection",
      "DatagramSocket", "InetAddress", "android.net.http", "WebView(")
    val sources = File(module, "src/main").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    assertTrue(sources.size > 10)
    for (source in sources) {
      val text = source.readText()
      for (word in forbidden) assertFalse("${source.name} usa $word", text.contains(word))
    }
    val manifest = File(module, "src/main/AndroidManifest.xml").readText()
    assertFalse(manifest.contains("android.permission.INTERNET"))
    assertFalse(manifest.contains("uses-permission"))
  }
}
