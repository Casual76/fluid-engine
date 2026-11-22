package dev.antigravity.fluidengine.viewer.data

import dev.antigravity.fluidengine.viewer.FluidViewerException
import dev.antigravity.fluidengine.viewer.FluidViewerFailure
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.util.Locale

/** Un capitolo della spina: il percorso nello ZIP, il titolo dall'indice (o `null`), i byte (per la percentuale). */
internal class DataEpubChapter(val path: String, val title: String?, val bytes: Long, val linear: Boolean)

/** Una voce dell'indice: il titolo, il capitolo a cui porta (o -1), quanto è rientrata. */
internal class DataEpubTocEntry(val title: String, val path: String?, val chapter: Int, val depth: Int)

/**
 * Il DRM di un EPUB: il [system] per le persone («Adobe ADEPT», «Apple FairPlay», «Readium LCP», «cifratura del
 * file»), quante risorse sono cifrate ([encrypted], 0 se il sistema non lo dice) e il [reason] in una frase.
 */
internal class DataEpubDrm(val system: String, val encrypted: Int, val reason: String)

/** Ciò che dice `META-INF` di un EPUB: il DRM, se c'è, e quanti caratteri sono solo offuscati (non è un DRM). */
internal class DataEpubProtection(val drm: DataEpubDrm?, val obfuscatedFonts: Int)

/** Un libro letto: i metadati dell'OPF, la spina, l'indice, la copertina e i conti per il pannello info. */
internal class DataEpubBook(
  val version: String,
  val opfPath: String,
  val title: String?,
  val creators: List<String>,
  val language: String?,
  val publisher: String?,
  val date: String?,
  val identifier: String?,
  val description: String?,
  val rights: String?,
  val subjects: List<String>,
  val chapters: List<DataEpubChapter>,
  val toc: List<DataEpubTocEntry>,
  val coverPath: String?,
  val images: Int,
  val stylesheets: Int,
  val fonts: Int,
  val obfuscatedFonts: Int,
  val fixedLayout: Boolean,
  val tocSource: String,
) {
  val totalBytes: Long = chapters.sumOf { it.bytes }.coerceAtLeast(1)

  /** La percentuale del libro all'inizio del capitolo [index], dai byte dei capitoli prima. */
  fun percentAt(index: Int): Int = (chapters.take(index).sumOf { it.bytes } * 100 / totalBytes).toInt().coerceIn(0, 100)

  /** Il capitolo di un percorso dello ZIP (senza frammento), o -1. */
  fun chapterOf(path: String): Int = chapters.indexOfFirst { it.path.equals(path, ignoreCase = true) }
}

/**
 * F6C.13 · Gli EPUB 2 e 3 (§1.3): lo ZIP, poi `META-INF/container.xml`, poi l'OPF (metadati, manifesto, spina), poi
 * l'indice (`nav.xhtml` di EPUB 3 o `toc.ncx` di EPUB 2). Un libro con DRM (`META-INF/encryption.xml` che cifra il
 * testo, `rights.xml` di Adobe, `sinf.xml` di Apple, `license.lcpl` di Readium) è **protetto**: la pagina lo dice, col
 * sistema e il motivo, e offre «Apri con…» ([DataEpubProtection], `DataProtectedPage`).
 * L'offuscamento dei caratteri di IDPF e di Adobe non è un DRM: il libro si apre, e il pannello lo dice.
 */
internal object DataEpub {
  private val FontObfuscation = setOf("http://www.idpf.org/2008/embedding", "http://ns.adobe.com/pdf/enc#RC")

  /** Il libro, se si può leggere: con un DRM è un'eccezione «protetto» (per chi vuole solo leggerlo, come la miniatura). */
  fun read(zip: DataZip): DataEpubBook {
    val protection = protection(zip)
    protection.drm?.let { throw FluidViewerException(FluidViewerFailure.Protected, it.reason) }
    return metadata(zip, protection.obfuscatedFonts)
  }

  /**
   * I metadati e la spina, anche di un libro protetto: l'OPF e l'indice non si cifrano mai (lo vieta OCF), quindi titolo,
   * autore e copertina si leggono lo stesso. I capitoli no.
   */
  fun metadata(zip: DataZip, obfuscated: Int = 0): DataEpubBook {
    val container = zip.read("META-INF/container.xml") ?: throw FluidViewerException(FluidViewerFailure.Corrupt, "L'EPUB non ha META-INF/container.xml")
    val opfPath = rootfile(container) ?: throw FluidViewerException(FluidViewerFailure.Corrupt, "L'EPUB non dice dov'è il pacchetto (OPF)")
    val opf = zip.read(opfPath) ?: throw FluidViewerException(FluidViewerFailure.Corrupt, "Manca il pacchetto dell'EPUB: $opfPath")
    return pack(zip, opfPath, opf, obfuscated)
  }

  /** Il percorso dell'OPF da `container.xml`. */
  fun rootfile(container: ByteArray): String? {
    val parser = DataXml.parser(relaxed = true)
    parser.setInput(ByteArrayInputStream(container), null)
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
      if (parser.eventType == XmlPullParser.START_TAG && parser.name.substringAfter(':') == "rootfile") {
        val path = parser.getAttributeValue(null, "full-path")
        val type = parser.getAttributeValue(null, "media-type")
        if (!path.isNullOrBlank() && (type == null || type == "application/oebps-package+xml")) return DataWeb.normalize(path)
      }
    }
    return null
  }

  /**
   * Il DRM di un EPUB, da `META-INF`: `rights.xml` è di Adobe ADEPT, `sinf.xml` di Apple FairPlay, `license.lcpl` di
   * Readium LCP; un `encryption.xml` che cifra il testo è un DRM anche senza nome. L'offuscamento dei caratteri di IDPF
   * e di Adobe non lo è: si conta e basta.
   */
  fun protection(zip: DataZip): DataEpubProtection {
    val (obfuscated, encrypted, algorithms) = encryption(zip)
    val drm = when {
      zip.entry("META-INF/rights.xml") != null -> DataEpubDrm("Adobe ADEPT", encrypted,
        "Il libro è cifrato con Adobe ADEPT: si legge solo con l'account e l'app che l'hanno comprato.")
      zip.entry("META-INF/sinf.xml") != null -> DataEpubDrm("Apple FairPlay", encrypted,
        "Il libro è cifrato con Apple FairPlay: si legge solo in Libri, con l'ID Apple che l'ha comprato.")
      zip.entry("META-INF/license.lcpl") != null -> DataEpubDrm("Readium LCP", encrypted,
        "Il libro è cifrato con Readium LCP: si legge in un'app che conosce la sua licenza e la sua frase segreta.")
      encrypted > 0 -> DataEpubDrm("cifratura del file", encrypted,
        "Il testo del libro è cifrato (${if (encrypted == 1) "1 risorsa" else "$encrypted risorse"}, ${algorithms.joinToString(", ") { it.substringAfterLast('#') }.ifEmpty { "algoritmo non dichiarato" }}): " +
          "serve l'app con la sua chiave.")
      else -> null
    }
    return DataEpubProtection(drm, obfuscated)
  }

  /** Da `encryption.xml`: i caratteri offuscati, le risorse cifrate e gli algoritmi di cifratura. */
  private fun encryption(zip: DataZip): Triple<Int, Int, Set<String>> {
    val encryption = zip.read("META-INF/encryption.xml") ?: return Triple(0, 0, emptySet())
    val parser = DataXml.parser(relaxed = true)
    parser.setInput(ByteArrayInputStream(encryption), null)
    var algorithm: String? = null
    var obfuscated = 0
    var encrypted = 0
    val algorithms = LinkedHashSet<String>()
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
      if (parser.eventType != XmlPullParser.START_TAG) continue
      when (parser.name.substringAfter(':')) {
        "EncryptedData" -> algorithm = null
        "EncryptionMethod" -> algorithm = parser.getAttributeValue(null, "Algorithm")
        "CipherReference" -> if (algorithm in FontObfuscation) obfuscated++ else {
          encrypted++
          algorithm?.let { algorithms += it }
        }
      }
    }
    return Triple(obfuscated, encrypted, algorithms)
  }

  private class Item(val id: String, val href: String, val mediaType: String, val properties: String)

  private fun pack(zip: DataZip, opfPath: String, opf: ByteArray, obfuscated: Int): DataEpubBook {
    val folder = DataWeb.folderOf(opfPath)
    val parser = DataXml.parser(relaxed = true)
    parser.setInput(ByteArrayInputStream(opf), null)
    var version = "2.0"
    val meta = HashMap<String, MutableList<String>>()
    val items = LinkedHashMap<String, Item>()
    val spine = ArrayList<Pair<String, Boolean>>()
    var ncxId: String? = null
    var coverId: String? = null
    var fixed = false
    var current: String? = null
    var metaProperty: String? = null
    val text = StringBuilder()
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
      when (parser.eventType) {
        XmlPullParser.START_TAG -> {
          val name = parser.name
          val local = name.substringAfter(':')
          when {
            local == "package" -> version = parser.getAttributeValue(null, "version") ?: version
            name.startsWith("dc:") -> {
              current = local
              text.setLength(0)
            }
            local == "meta" -> {
              val metaName = parser.getAttributeValue(null, "name")
              val content = parser.getAttributeValue(null, "content")
              if (metaName == "cover" && content != null) coverId = content
              metaProperty = parser.getAttributeValue(null, "property")
              text.setLength(0)
            }
            local == "item" -> {
              val id = parser.getAttributeValue(null, "id") ?: continue
              val href = parser.getAttributeValue(null, "href") ?: continue
              val path = DataWeb.resolve(folder, href) ?: continue
              items[id] = Item(id, path, parser.getAttributeValue(null, "media-type").orEmpty(), parser.getAttributeValue(null, "properties").orEmpty())
            }
            local == "spine" -> ncxId = parser.getAttributeValue(null, "toc")
            local == "itemref" -> {
              val idref = parser.getAttributeValue(null, "idref") ?: continue
              spine += idref to (parser.getAttributeValue(null, "linear") != "no")
            }
          }
        }
        XmlPullParser.TEXT -> text.append(parser.text)
        XmlPullParser.END_TAG -> {
          val local = parser.name.substringAfter(':')
          if (current != null && parser.name.startsWith("dc:")) {
            val value = text.toString().trim().replace(Regex("\\s+"), " ")
            if (value.isNotEmpty()) meta.getOrPut(current) { ArrayList() } += value
            current = null
          } else if (local == "meta") {
            if (metaProperty == "rendition:layout" && text.toString().trim() == "pre-paginated") fixed = true
            metaProperty = null
          }
        }
      }
    }
    val cover = items.values.firstOrNull { "cover-image" in it.properties.split(' ') }?.href
      ?: coverId?.let { items[it]?.href }
      ?: items.values.firstOrNull { it.mediaType.startsWith("image/") && it.href.substringAfterLast('/').lowercase(Locale.ROOT).startsWith("cover") }?.href
    // La spina: solo i documenti (XHTML, HTML, SVG); un capitolo che manca nello ZIP si salta.
    val documents = spine.mapNotNull { (id, linear) ->
      val item = items[id] ?: return@mapNotNull null
      val type = item.mediaType.lowercase(Locale.ROOT)
      if (type.isNotEmpty() && "html" !in type && "xml" !in type && "svg" !in type) return@mapNotNull null
      val entry = zip.entry(item.href) ?: return@mapNotNull null
      Triple(item.href, linear, entry.size)
    }
    if (documents.isEmpty()) throw FluidViewerException(FluidViewerFailure.Corrupt, "L'EPUB non ha capitoli da leggere")
    // L'indice: nav.xhtml di EPUB 3, altrimenti toc.ncx.
    val navItem = items.values.firstOrNull { "nav" in it.properties.split(' ') }
    val ncxItem = ncxId?.let { items[it] } ?: items.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }
    val paths = documents.map { it.first }
    var tocSource = "nessuno"
    val raw: List<Triple<String, String?, Int>> = try {
      when {
        navItem != null -> nav(zip.read(navItem.href), DataWeb.folderOf(navItem.href)).also { if (it.isNotEmpty()) tocSource = "nav.xhtml (EPUB 3)" }
        else -> emptyList()
      }.ifEmpty {
        if (ncxItem != null) ncx(zip.read(ncxItem.href), DataWeb.folderOf(ncxItem.href)).also { if (it.isNotEmpty()) tocSource = "toc.ncx (EPUB 2)" } else emptyList()
      }
    } catch (_: Exception) {
      emptyList()
    }
    val toc = raw.map { (title, path, depth) ->
      val target = path?.substringBefore('#')
      DataEpubTocEntry(title, target, target?.let { t -> paths.indexOfFirst { it.equals(t, ignoreCase = true) } } ?: -1, depth)
    }
    val chapters = documents.map { (path, linear, size) ->
      DataEpubChapter(path, toc.firstOrNull { it.path.equals(path, ignoreCase = true) }?.title, size, linear)
    }
    val all = items.values
    return DataEpubBook(
      version = version, opfPath = opfPath,
      title = meta["title"]?.firstOrNull(), creators = meta["creator"].orEmpty(), language = meta["language"]?.firstOrNull(),
      publisher = meta["publisher"]?.firstOrNull(), date = meta["date"]?.firstOrNull(), identifier = meta["identifier"]?.firstOrNull(),
      description = meta["description"]?.firstOrNull()?.let(DataWeb::plainText)?.trim(), rights = meta["rights"]?.firstOrNull(),
      subjects = meta["subject"].orEmpty(), chapters = chapters, toc = toc, coverPath = cover,
      images = all.count { it.mediaType.startsWith("image/") }, stylesheets = all.count { it.mediaType == "text/css" },
      fonts = all.count { it.mediaType.startsWith("font/") || "opentype" in it.mediaType || "truetype" in it.mediaType || "woff" in it.mediaType },
      obfuscatedFonts = obfuscated, fixedLayout = fixed, tocSource = tocSource,
    )
  }

  /** L'indice di EPUB 3: il `<nav epub:type="toc">`, con le voci annidate negli `<ol>`. */
  private fun nav(bytes: ByteArray?, folder: String): List<Triple<String, String?, Int>> {
    bytes ?: return emptyList()
    val parser = DataXml.parser(relaxed = true)
    parser.setInput(ByteArrayInputStream(bytes), null)
    val out = ArrayList<Triple<String, String?, Int>>()
    var inToc = false
    var navDepth = 0
    var olDepth = 0
    var href: String? = null
    var label: StringBuilder? = null
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
      when (parser.eventType) {
        XmlPullParser.START_TAG -> when (parser.name.substringAfter(':')) {
          "nav" -> {
            if (inToc) navDepth++
            else if (parser.getAttributeValue(null, "epub:type")?.split(' ')?.contains("toc") == true || parser.getAttributeValue(null, "role") == "doc-toc") {
              inToc = true
              navDepth = 1
            }
          }
          "ol" -> if (inToc) olDepth++
          "a", "span" -> if (inToc && label == null) {
            href = parser.getAttributeValue(null, "href")
            label = StringBuilder()
          }
        }
        XmlPullParser.TEXT -> label?.append(parser.text)
        XmlPullParser.END_TAG -> when (parser.name.substringAfter(':')) {
          "nav" -> if (inToc && --navDepth == 0) return out
          "ol" -> if (inToc) olDepth--
          "a", "span" -> if (inToc && label != null) {
            val title = label.toString().trim().replace(Regex("\\s+"), " ")
            if (title.isNotEmpty()) out += Triple(title, href?.let { h -> DataWeb.resolve(folder, h)?.let { it + h.substringAfter('#', "").let { f -> if (f.isEmpty()) "" else "#$f" } } }, (olDepth - 1).coerceAtLeast(0))
            label = null
            href = null
          }
        }
      }
    }
    return out
  }

  /** L'indice di EPUB 2: i `navPoint` annidati di `toc.ncx`. */
  private fun ncx(bytes: ByteArray?, folder: String): List<Triple<String, String?, Int>> {
    bytes ?: return emptyList()
    val parser = DataXml.parser(relaxed = true)
    parser.setInput(ByteArrayInputStream(bytes), null)
    val out = ArrayList<Triple<String, String?, Int>>()
    var depth = 0
    var inMap = false
    var inLabel = false
    val label = StringBuilder()
    var pendingTitle: String? = null
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
      when (parser.eventType) {
        XmlPullParser.START_TAG -> when (parser.name.substringAfter(':')) {
          "navMap" -> inMap = true
          "navPoint" -> if (inMap) {
            depth++
            pendingTitle = null
          }
          "navLabel" -> if (inMap) {
            inLabel = true
            label.setLength(0)
          }
          "content" -> if (inMap && pendingTitle != null) {
            val src = parser.getAttributeValue(null, "src")
            out += Triple(pendingTitle, src?.let { s -> DataWeb.resolve(folder, s) }, (depth - 1).coerceAtLeast(0))
            pendingTitle = null
          }
        }
        XmlPullParser.TEXT -> if (inLabel) label.append(parser.text)
        XmlPullParser.END_TAG -> when (parser.name.substringAfter(':')) {
          "navMap" -> inMap = false
          "navPoint" -> if (inMap) depth--
          "navLabel" -> if (inLabel) {
            inLabel = false
            pendingTitle = label.toString().trim().replace(Regex("\\s+"), " ").ifEmpty { null }
          }
        }
      }
    }
    return out
  }

  /**
   * Il documento HTML del capitolo [index], pronto per `FluidWebContent`: gli elementi XHTML chiusi da soli aperti, i
   * fogli di stile dentro il documento, gli indirizzi relativi assoluti dalla radice dello ZIP, e il foglio del lettore
   * ([css]) in testa.
   */
  fun chapterHtml(zip: DataZip, book: DataEpubBook, index: Int, css: String, colorScheme: String): String {
    val chapter = book.chapters[index]
    val bytes = zip.read(chapter.path) ?: throw FluidViewerException(FluidViewerFailure.Corrupt, "Manca il capitolo ${chapter.path}")
    val data = DataBytes(bytes, bytes.size, false)
    val text = DataText.decode(data, DataText.xmlCharset(bytes, bytes.size) ?: DataText.htmlCharset(bytes, bytes.size)).text
    val folder = DataWeb.folderOf(chapter.path)
    var html = DataWeb.expandSelfClosing(text.replace(Regex("""^\s*<\?xml[^>]*\?>"""), ""))
    html = DataWeb.inlineStylesheets(html, folder) { path ->
      zip.read(path, limit = 4L * 1024 * 1024)?.let { DataText.decode(DataBytes(it, it.size, false)).text }
    }
    html = DataWeb.rebase(html, folder)
    // Un capitolo SVG (una copertina) va dentro un documento HTML.
    if (chapter.path.endsWith(".svg", ignoreCase = true)) html = "<!DOCTYPE html><html><head></head><body>$html</body></html>"
    return DataWeb.inject(html, css, colorScheme, viewport = !Regex("""<meta[^>]+name\s*=\s*["']viewport""", RegexOption.IGNORE_CASE).containsMatchIn(html))
  }
}
