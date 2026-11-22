package dev.antigravity.fluidengine.viewer.data

import android.os.ParcelFileDescriptor
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import dev.antigravity.fluidengine.ui.fluid.FluidWebResponse
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.viewer.FluidTextDecoder
import dev.antigravity.fluidengine.viewer.FluidTextDocument
import dev.antigravity.fluidengine.viewer.FluidViewerDetail
import dev.antigravity.fluidengine.viewer.FluidViewerException
import dev.antigravity.fluidengine.viewer.FluidViewerFailure
import dev.antigravity.fluidengine.viewer.FluidViewerOpened
import dev.antigravity.fluidengine.viewer.FluidViewerRequest
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Il formato di un albero di dati. */
internal enum class DataTreeFormat { Json, Ndjson, Xml, Plist }

/** I dati del pannello info, nell'ordine: i fatti in capsule (sezione `null`), poi le sezioni. */
internal class DataDetails {
  val list = ArrayList<FluidViewerDetail>()

  fun fact(label: String, value: String?) {
    if (!value.isNullOrBlank()) list += FluidViewerDetail(label, value, null)
  }

  fun row(section: String, label: String, value: String?) {
    if (!value.isNullOrBlank()) list += FluidViewerDetail(label, value.trim(), section)
  }

  /** La sezione «Lettura» di ogni tipo: il charset riconosciuto, gli a capo, i byte letti e se il file è stato tagliato. */
  fun reading(decoded: DataDecoded?, size: Long?, truncated: Boolean, extra: List<Pair<String, String>> = emptyList()) {
    if (decoded != null) {
      row(Reading, "Codifica", DataText.charsetLabel(decoded.charset))
      row(Reading, "Fine riga", decoded.newline)
    }
    size?.let { row(Reading, "Dimensione", DataText.size(it)) }
    extra.forEach { (l, v) -> row(Reading, l, v) }
    row(Reading, "Letto per intero", if (truncated) "no, il file è oltre il tetto" else "sì")
    row(Reading, "Lettore", "engine-viewer-data, senza rete")
  }

  companion object {
    const val Reading = "Lettura"
  }
}

/**
 * F6C.13 · L'apertura di ogni tipo della famiglia: legge la sorgente (sempre sul thread di I/O), prepara il modello
 * della pagina e i dati del pannello info, e consegna una pagina `Custom` (o la griglia per CSV e TSV). Ciò che resta
 * aperto (un descrittore, uno ZIP, un file in cache) si chiude con il documento.
 */
internal class DataOpen(private val request: FluidViewerRequest, private val settings: FluidDataSettings) {
  private val source = request.source
  private val name = source.name
  private val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
  private val strings = settings.strings

  private suspend fun readAll(limit: Int = DataProfile.MaxTextBytes): DataBytes = source.open().use {
    DataText.read(it, limit, source.sizeBytes)
  }

  private fun sourceDocument(bytes: DataBytes): FluidTextDocument? = FluidTextDecoder.decode(
    bytes.bytes, minOf(bytes.length, DataProfile.MaxSourceBytes), truncated = bytes.truncated || bytes.length > DataProfile.MaxSourceBytes,
    fileName = name, mimeType = source.mimeType, totalBytes = source.sizeBytes,
  )

  // --- Markdown ---------------------------------------------------------------------------------------------------

  suspend fun markdown(): FluidViewerOpened {
    val bytes = readAll()
    val decoded = DataText.decode(bytes)
    val text = decoded.text
    val model = DataMarkdownModel(name, text, DataMarkdown.chunks(text), sourceDocument(bytes))
    val stats = DataMarkdown.stats(text)
    val details = DataDetails().apply {
      fact("Formato", "Markdown")
      fact("Righe", "${DataText.count(decoded.lineCount)} righe")
      if (stats.headings > 0) fact("Titoli", if (stats.headings == 1) "1 titolo" else "${stats.headings} titoli")
      fact("Parole", "${DataText.count(stats.words)} parole")
      row("Documento", "Titolo", stats.title)
      row("Documento", "Titoli", stats.headings.toString())
      row("Documento", "Parole", DataText.count(stats.words))
      row("Documento", "Collegamenti", stats.links.toString())
      row("Documento", "Immagini", if (stats.images == 0) "0" else "${stats.images} (mostrate come testo alternativo)")
      row("Documento", "Tabelle", stats.tables.toString())
      row("Documento", "Blocchi di codice", stats.codeBlocks.toString())
      row("Documento", "Cose da fare", if (stats.tasks == 0) null else "${stats.tasksDone} fatte di ${stats.tasks}")
      reading(decoded, source.sizeBytes, bytes.truncated)
    }
    return FluidViewerOpened(FluidViewerContent.Custom(draw = { DataMarkdownPage(this, model, settings) }), details.list)
  }

  // --- HTML -------------------------------------------------------------------------------------------------------

  suspend fun html(): FluidViewerOpened {
    val bytes = readAll()
    val declared = DataText.htmlCharset(bytes.bytes, bytes.length)
    val decoded = DataText.decode(bytes, declared)
    val html = decoded.text
    val resources = settings.resources
    val local: (String) -> FluidWebResponse? = { path ->
      if (resources == null) null else DataWeb.serve(path) { resources(source, path) }
    }
    val policy = DataWeb.Policy(local, remote = null)
    val remote = DataWeb.remoteReferences(html)
    val model = DataHtmlModel(name, html, policy, remote, settings.remoteImages, sourceDocument(bytes))
    val details = DataDetails().apply {
      fact("Formato", "HTML")
      DataWeb.title(html)?.let { fact("Titolo", it.take(40)) }
      fact("Rete", "nessuna rete")
      row("Documento", "Titolo", DataWeb.title(html))
      row("Documento", "Lingua", Regex("""<html[^>]*\blang\s*=\s*["']?([A-Za-z-]+)""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1))
      row("Documento", "Immagini", Regex("<img\\b", RegexOption.IGNORE_CASE).findAll(html).count().toString())
      row("Documento", "Collegamenti", Regex("<a\\b[^>]*href", RegexOption.IGNORE_CASE).findAll(html).count().toString())
      row("Documento", "Script", Regex("<script\\b", RegexOption.IGNORE_CASE).findAll(html).count().let { if (it == 0) "nessuno" else "$it, spenti" })
      row("Documento", "Colori", colorsLabel(DataWeb.colors(html)))
      row("Risorse", "Relative", if (resources == null) "bloccate: l'app non ha dato la cartella" else "dalla cartella del file")
      row("Risorse", "Remote", if (remote == 0) "nessuna" else "$remote, bloccate" + if (settings.remoteImages != null) " («Carica immagini»)" else "")
      row("Risorse", "Rete", "nessuna richiesta parte dalla pagina")
      reading(decoded, source.sizeBytes, bytes.truncated, listOfNotNull(declared?.let { "Charset dichiarato" to it }))
    }
    return FluidViewerOpened(FluidViewerContent.Custom(draw = { DataHtmlPage(this, model, settings) }), details.list)
  }

  // --- CSV e TSV --------------------------------------------------------------------------------------------------

  suspend fun csv(): FluidViewerOpened {
    val sample = source.open().use { DataText.read(it, 64 * 1024, source.sizeBytes) }
    val charset = FluidTextDecoder.detect(sample.bytes, sample.length, declared = source.mimeType, complete = !sample.truncated)
    val singleByteLines = charset.charset == Charsets.UTF_8 || charset.charset.newEncoder().maxBytesPerChar() <= 1f
    var temp: File? = null
    var pfd: ParcelFileDescriptor? = null
    val details = mutableStateListOf<FluidViewerDetail>()
    val sampleText = DataText.decodeWith(sample.bytes, charset.bomLength, sample.length - charset.bomLength, charset.charset)
    val usable = if (sample.truncated) sampleText.substringBeforeLast('\n', sampleText) else sampleText
    val dialect = DataCsv.sniff(usable, extension)
    val rows = DataCsv.records(usable, dialect, maxRecords = 200, dropLastPartial = sample.truncated)
    val header = DataCsv.hasHeader(rows)
    val widths = DataCsv.columnWidths(rows)
    val columns = rows.maxOfOrNull { it.size } ?: 1
    // Il conto finisce in sottofondo, anche prima che la pagina abbia i suoi dati: chi arriva per secondo non li raddoppia.
    val finish: (DataCsvGrid) -> Unit = { g -> synchronized(details) { details.replaceWith(csvDetails(g, charset).list) } }
    val grid = try {
      if (singleByteLines) {
        val descriptor = source.seekable()
        pfd = descriptor
        val channel = FileInputStream(descriptor.fileDescriptor).channel
        DataCsvGrid(name, channel, { source.open() }, charset.bomLength.toLong(), dialect, charset.charset, header, widths, columns,
          closeResources = { descriptor.close() }, onFinished = finish)
      } else {
        // UTF-16 e UTF-32: gli a capo sono due o quattro byte. Si trascrive una volta in UTF-8, in cache.
        val dir = File(request.context.cacheDir, "fluid-viewer-data").apply { mkdirs() }
        val file = File.createTempFile("csv-", ".txt", dir)
        temp = file
        source.open().use { input ->
          val reader = InputStreamReader(BufferedInputStream(input).also { it.skip(charset.bomLength.toLong()) }, charset.charset)
          file.writer(Charsets.UTF_8).use { reader.copyTo(it) }
        }
        val channel = RandomAccessFile(file, "r").channel
        DataCsvGrid(name, channel, { FileInputStream(file) }, 0L, dialect, Charsets.UTF_8, header, widths, columns,
          closeResources = { file.delete() }, onFinished = finish)
      }
    } catch (e: Exception) {
      pfd?.close()
      temp?.delete()
      throw e
    }
    synchronized(details) { if (details.isEmpty()) details.addAll(csvDetails(grid, charset).list) }
    return FluidViewerOpened(FluidViewerContent.Grid(grid), details, onClose = { grid.close() })
  }

  private fun csvDetails(grid: DataCsvGrid, charset: dev.antigravity.fluidengine.viewer.FluidTextCharset): DataDetails =
    DataDetails().apply {
      val done = grid.finished
      val rows = grid.rowCount
      val dataRows = if (grid.header) (rows - 1).coerceAtLeast(0) else rows
      fact("Formato", if (grid.dialect.delimiter == '\t') "TSV" else "CSV")
      fact("Righe", if (done) "${DataText.count(dataRows)} righe" else "righe in conteggio")
      fact("Colonne", "${grid.columnCount} colonne")
      fact("Separatore", grid.dialect.label)
      row("Tabella", "Righe di dati", if (done) DataText.count(dataRows) else "si contano mentre si legge")
      row("Tabella", "Colonne", grid.columnCount.toString())
      row("Tabella", "Intestazione", if (grid.header) "sì, la prima riga resta in alto" else "no")
      row("Tabella", "Separatore", grid.dialect.label)
      row("Tabella", "Virgolette", "doppie, RFC 4180")
      if (grid.truncated) row("Tabella", "Tetto", "mostrate le prime ${DataText.count(DataProfile.MaxCsvRows)} righe")
      grid.failure?.let { row("Tabella", "Lettura interrotta", it) }
      row(DataDetails.Reading, "Codifica", DataText.charsetLabel(charset))
      source.sizeBytes?.let { row(DataDetails.Reading, "Dimensione", DataText.size(it)) }
      row(DataDetails.Reading, "Lettura", "a flusso: in memoria solo l'inizio di ogni riga")
      row(DataDetails.Reading, "Lettore", "engine-viewer-data, senza rete")
    }

  // --- Alberi: JSON, NDJSON, XML, plist ---------------------------------------------------------------------------

  suspend fun tree(format: DataTreeFormat): FluidViewerOpened {
    var binary: DataBytes? = null
    var textCharset: String? = null
    val tree = when (format) {
      DataTreeFormat.Json, DataTreeFormat.Ndjson -> source.open().use { raw ->
        val input = BufferedInputStream(raw, 64 * 1024)
        input.mark(8192)
        val head = ByteArray(8192)
        val n = input.read(head).coerceAtLeast(0)
        input.reset()
        val charset = FluidTextDecoder.detect(head, n, declared = source.mimeType, complete = false)
        textCharset = DataText.charsetLabel(charset)
        input.skip(charset.bomLength.toLong())
        val reader = InputStreamReader(input, charset.charset)
        if (format == DataTreeFormat.Json) DataJsonParser.parse(reader) else DataJsonParser.parseLines(reader)
      }
      DataTreeFormat.Xml -> source.open().use { DataXml.parse(BufferedInputStream(it)) }
      DataTreeFormat.Plist -> {
        val bytes = readAll()
        if (DataPlist.isBinary(bytes.bytes, bytes.length)) {
          binary = bytes
          DataPlist.parseBinary(bytes.bytes, bytes.length)
        } else {
          DataXml.parse(ByteArrayInputStream(bytes.bytes, 0, bytes.length))
        }
      }
    }
    // Un file senza niente di leggibile e con un errore in testa non è di questa famiglia: lo sconosciuto lo dice.
    if (tree.root == null && tree.error != null && binary == null && tree.error.line <= 1 && tree.error.column <= 1 &&
      format != DataTreeFormat.Json && format != DataTreeFormat.Ndjson
    ) {
      throw FluidViewerException(FluidViewerFailure.Corrupt, "Il file non è ${tree.format}: ${tree.error.message}")
    }
    val textLoader: suspend () -> FluidTextDocument? = if (binary != null) {
      { DataTreeText.document(tree, name) }
    } else {
      {
        source.open().use { FluidTextDecoder.read(it, fileName = name, mimeType = source.mimeType, totalBytes = source.sizeBytes, limitBytes = DataProfile.MaxSourceBytes) }
      }
    }
    val model = DataTreeModel(name, tree, format, textLoader)
    val stats = tree.stats
    val details = DataDetails().apply {
      fact("Formato", tree.format)
      fact("Nodi", "${DataText.count(stats.nodes)} nodi")
      fact("Profondità", "profondità ${stats.maxDepth}")
      fact("Validità", if (tree.error == null) "valido" else "non valido")
      val root = tree.root
      row("Struttura", "Radice", when {
        root == null -> "nessuna"
        format == DataTreeFormat.Ndjson -> "${DataText.count(root.childCount)} righe"
        root.kind == DataKind.Element -> "<${root.name}> · ${root.summary()}"
        root.kind.container -> (if (root.kind == DataKind.Object) "oggetto con " else "elenco di ") + root.summary()
        else -> root.value
      })
      row("Struttura", "Nodi", DataText.count(stats.nodes))
      row("Struttura", "Profondità massima", stats.maxDepth.toString())
      when (format) {
        DataTreeFormat.Xml -> {
          row("Struttura", "Elementi", DataText.count(stats.count(DataKind.Element)))
          row("Struttura", "Attributi", DataText.count(stats.count(DataKind.Attribute)))
          row("Struttura", "Testi", DataText.count(stats.count(DataKind.Text)))
          row("Struttura", "Commenti", stats.comments.takeIf { it > 0 }?.toString())
          tree.prolog["version"]?.let { row("Dichiarazione", "Versione XML", it) }
          tree.prolog["encoding"]?.let { row("Dichiarazione", "Codifica", it) }
          tree.prolog["doctype"]?.let { row("Dichiarazione", "DOCTYPE", it.take(120)) }
          tree.prolog["namespaces"]?.let { row("Dichiarazione", "Namespace dichiarati", it) }
        }
        else -> {
          row("Struttura", "Oggetti", DataText.count(stats.count(DataKind.Object)))
          row("Struttura", "Elenchi", DataText.count(stats.count(DataKind.Array)))
          row("Struttura", "Stringhe", DataText.count(stats.count(DataKind.String)))
          row("Struttura", "Numeri", DataText.count(stats.count(DataKind.Number)))
          row("Struttura", "Vero o falso", DataText.count(stats.count(DataKind.Bool)))
          row("Struttura", "Null", stats.count(DataKind.Null).takeIf { it > 0 }?.let(DataText::count))
          row("Struttura", "Date", stats.count(DataKind.Date).takeIf { it > 0 }?.let(DataText::count))
          row("Struttura", "Dati binari", stats.count(DataKind.Data).takeIf { it > 0 }?.let(DataText::count))
          if (stats.comments > 0 || stats.trailingCommas > 0) {
            row("Struttura", "JSONC", "${stats.comments} commenti, ${stats.trailingCommas} virgole finali")
          }
          tree.prolog["invalidLines"]?.takeIf { it != "0" }?.let { row("Validità", "Righe non valide", it) }
        }
      }
      row("Validità", "Esito", tree.error?.let { "errore alla $it" } ?: "valido")
      if (tree.truncated) row("Validità", "Tetto", strings.truncated(DataProfile.MaxTreeNodes))
      reading(null, source.sizeBytes, tree.truncated, listOfNotNull((textCharset ?: tree.prolog["encoding"])?.let { "Codifica" to it }))
    }
    return FluidViewerOpened(FluidViewerContent.Custom(draw = { DataTreePage(this, model, settings) }), details.list)
  }

  // --- EPUB -------------------------------------------------------------------------------------------------------

  /** Lo ZIP della sorgente, su un descrittore ricercabile (un `Uri` che non lo è si copia in cache: F6C.8). */
  suspend fun zip(): DataZip {
    val pfd = source.seekable()
    return try {
      DataZip(FileInputStream(pfd.fileDescriptor).channel) { pfd.close() }
    } catch (e: Exception) {
      pfd.close()
      throw FluidViewerException(FluidViewerFailure.Corrupt, "L'EPUB non è uno ZIP leggibile", e)
    }
  }

  suspend fun epub(): FluidViewerOpened {
    val zip = zip()
    val book = try {
      val protection = DataEpub.protection(zip)
      val drm = protection.drm
      if (drm != null) return protectedEpub(zip, drm)
      DataEpub.metadata(zip, protection.obfuscatedFonts)
    } catch (e: Exception) {
      zip.close()
      throw e
    }
    val model = DataEpubModel(name, zip, book)
    val details = DataDetails().apply {
      fact("Formato", "EPUB ${book.version.substringBefore('.')}")
      fact("Capitoli", "${book.chapters.size} capitoli")
      book.language?.let { fact("Lingua", it) }
      if (book.images > 0) fact("Immagini", "${book.images} immagini")
      row("Libro", "Titolo", book.title)
      row("Libro", if (book.creators.size > 1) "Autori" else "Autore", book.creators.joinToString(", "))
      row("Libro", "Editore", book.publisher)
      row("Libro", "Data", book.date)
      row("Libro", "Lingua", book.language)
      row("Libro", "Identificativo", book.identifier)
      row("Libro", "Argomenti", book.subjects.joinToString(", "))
      row("Libro", "Diritti", book.rights)
      row("Libro", "Descrizione", book.description?.take(400))
      row("Contenuto", "Capitoli", book.chapters.size.toString())
      row("Contenuto", "Voci dell'indice", "${book.toc.size} (${book.tocSource})")
      row("Contenuto", "Immagini", book.images.toString())
      row("Contenuto", "Fogli di stile", book.stylesheets.toString())
      row("Contenuto", "Caratteri incorporati", book.fonts.toString())
      row("Contenuto", "Impaginazione", if (book.fixedLayout) "fissa: qui si legge a scorrimento" else "a scorrimento")
      row("Protezione", "DRM", "nessuno")
      row("Protezione", "Caratteri offuscati", if (book.obfuscatedFonts > 0) "${book.obfuscatedFonts} (offuscamento IDPF, non è un DRM)" else null)
      row("Protezione", "Rete", "nessuna: le risorse remote restano bloccate")
      row("Pacchetto", "OPF", book.opfPath)
      row("Pacchetto", "Versione EPUB", book.version)
      reading(null, source.sizeBytes, false)
    }
    return FluidViewerOpened(FluidViewerContent.Custom(draw = { DataEpubPage(this, model, settings) }), details.list, onClose = { zip.close() })
  }

  /**
   * Un EPUB con DRM: non lo sconosciuto generico, ma la pagina «Protetto da DRM» con il libro (titolo, autore,
   * copertina se non è cifrata), il sistema e il motivo, e «Apri con…» e «Condividi» dell'app. Come un archivio
   * cifrato, il file c'è e si riconosce; solo il testo non si legge qui.
   */
  private fun protectedEpub(zip: DataZip, drm: DataEpubDrm): FluidViewerOpened {
    val book = try {
      DataEpub.metadata(zip)
    } catch (_: Exception) {
      null
    }
    val cover = book?.coverPath?.let { path ->
      try {
        zip.read(path)?.let { FluidDataFamily.decodePicture(it, DataProfile.MaxPictureSide) }
      } catch (_: Exception) {
        null
      }
    }
    zip.close()
    val model = DataProtectedModel(
      title = book?.title ?: name.substringBeforeLast('.'),
      subtitle = listOfNotNull(book?.creators?.joinToString(", ")?.takeIf { it.isNotEmpty() }, "Libro EPUB",
        source.sizeBytes?.let { DataText.size(it) }).joinToString(" · "),
      reason = drm.reason,
      cover = cover,
      openWith = request.openWith,
      share = request.share,
    )
    val details = DataDetails().apply {
      fact("Formato", "EPUB")
      fact("Protezione", strings.protectedTitle)
      fact("Sistema", drm.system)
      row("Protezione", "DRM", drm.system)
      row("Protezione", "Risorse cifrate", drm.encrypted.takeIf { it > 0 }?.toString())
      row("Protezione", "Motivo", drm.reason)
      row("Protezione", "Cosa fare", "Apri il libro con l'app che l'ha comprato («Apri con…»)")
      book?.let { b ->
        row("Libro", "Titolo", b.title)
        row("Libro", if (b.creators.size > 1) "Autori" else "Autore", b.creators.joinToString(", "))
        row("Libro", "Editore", b.publisher)
        row("Libro", "Lingua", b.language)
        row("Libro", "Identificativo", b.identifier)
        row("Libro", "Capitoli", b.chapters.size.toString())
      }
      reading(null, source.sizeBytes, false)
    }
    return FluidViewerOpened(FluidViewerContent.Custom(draw = { DataProtectedPage(this, model, settings) }), details.list)
  }

  // --- EML --------------------------------------------------------------------------------------------------------

  suspend fun mail(): FluidViewerOpened {
    val bytes = readAll(DataProfile.MaxEntryBytes.toInt())
    val mail = DataMime.parse(bytes.bytes, bytes.length)
    if (mail.headers.list.isEmpty() && mail.html == null) throw FluidViewerException(FluidViewerFailure.Corrupt, "Non è un messaggio di posta")
    val model = DataMailModel(name, mail, request, settings)
    val zone = ZoneId.systemDefault()
    val details = DataDetails().apply {
      fact("Formato", "E-mail")
      fact("Corpo", if (mail.html != null) "HTML" else if (mail.text != null) "testo" else "senza corpo")
      fact("Allegati", if (mail.attachments.isEmpty()) "nessun allegato" else if (mail.attachments.size == 1) "1 allegato" else "${mail.attachments.size} allegati")
      mail.charsets.firstOrNull()?.let { fact("Charset", it.uppercase(Locale.ROOT)) }
      row("Messaggio", "Oggetto", mail.subject ?: strings.noSubject)
      row("Messaggio", strings.from, mail.from.joinToString(", "))
      row("Messaggio", strings.to, mail.to.joinToString(", "))
      row("Messaggio", strings.cc, mail.cc.joinToString(", "))
      row("Messaggio", "Rispondi a", mail.replyTo.joinToString(", "))
      row("Messaggio", strings.date, mail.date?.withZoneSameInstant(zone)?.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm", Locale.ITALIAN)) ?: mail.dateRaw)
      row("Messaggio", "Message-ID", mail.headers["Message-ID"])
      row("Messaggio", "Programma", mail.headers["X-Mailer"] ?: mail.headers["User-Agent"])
      mail.attachments.forEachIndexed { i, a ->
        row(strings.attachments, a.filename ?: "allegato ${i + 1}", "${DataText.size(a.body.size.toLong())} · ${a.type}")
      }
      row("Struttura", "Parti MIME", mail.parts.toString())
      row("Struttura", "Immagini in linea", mail.inline.size.toString())
      mail.html?.let { row("Struttura", "Colori del corpo", colorsLabel(DataWeb.colors(it))) }
      row("Struttura", "Charset", mail.charsets.joinToString(", ").ifEmpty { null })
      row("Struttura", "Codifiche", mail.encodings.joinToString(", ").ifEmpty { null })
      row("Struttura", "Rete", "nessuna: le immagini remote restano bloccate")
      reading(null, source.sizeBytes, bytes.truncated)
    }
    return FluidViewerOpened(FluidViewerContent.Custom(draw = { DataMailPage(this, model, settings) }), details.list, onClose = { model.close() })
  }

  // --- ICS --------------------------------------------------------------------------------------------------------

  suspend fun calendar(): FluidViewerOpened {
    val bytes = readAll()
    val decoded = DataText.decode(bytes)
    val calendar = DataIcs.parse(decoded.text)
    if (calendar.events.isEmpty() && calendar.version == null && calendar.product == null) {
      throw FluidViewerException(FluidViewerFailure.Corrupt, "Non è un calendario iCalendar")
    }
    val events = calendar.events.count { !it.todo }
    val todos = calendar.events.count { it.todo }
    val details = DataDetails().apply {
      fact("Formato", "iCalendar")
      fact("Eventi", if (events == 1) "1 evento" else "$events eventi")
      if (todos > 0) fact("Attività", if (todos == 1) "1 attività" else "$todos attività")
      calendar.events.firstNotNullOfOrNull { it.start?.zoneName }?.let { fact("Fuso", it) }
      row("Calendario", "Nome", calendar.name)
      row("Calendario", "Prodotto", calendar.product)
      row("Calendario", "Versione", calendar.version)
      row("Calendario", "Metodo", calendar.method)
      row("Calendario", "Eventi", events.toString())
      row("Calendario", "Attività", todos.toString())
      row("Calendario", "Diario", calendar.journals.takeIf { it > 0 }?.toString())
      row("Calendario", "Ripetuti", calendar.events.count { it.repeat != null }.toString())
      row("Calendario", "Giornata intera", calendar.events.count { it.start?.allDay == true }.toString())
      row("Fusi orari", "Dichiarati", calendar.zones.joinToString(", ").ifEmpty { "nessuno" })
      row("Fusi orari", "Del dispositivo", ZoneId.systemDefault().id)
      if (calendar.unknownZones.isNotEmpty()) row("Fusi orari", "Non riconosciuti", calendar.unknownZones.joinToString(", ") + " (ora mostrata com'è)")
      reading(decoded, source.sizeBytes, bytes.truncated)
    }
    val model = DataCalendarModel(name, calendar, request.openWith)
    return FluidViewerOpened(FluidViewerContent.Custom(draw = { DataCalendarPage(this, model, settings) }), details.list)
  }

  // --- VCF --------------------------------------------------------------------------------------------------------

  suspend fun contacts(): FluidViewerOpened {
    val bytes = readAll()
    val decoded = DataText.decode(bytes)
    val contacts = DataVcard.parse(decoded.text)
    if (contacts.isEmpty()) throw FluidViewerException(FluidViewerFailure.Corrupt, "Non ci sono contatti nel file")
    val versions = contacts.mapNotNull { it.version }.distinct()
    val details = DataDetails().apply {
      fact("Formato", "vCard ${versions.joinToString(", ").ifEmpty { "" }}".trim())
      fact("Contatti", if (contacts.size == 1) "1 contatto" else "${contacts.size} contatti")
      val photos = contacts.count { it.photo != null }
      if (photos > 0) fact("Foto", if (photos == 1) "1 foto" else "$photos foto")
      contacts.take(50).forEach { c -> row("Contatti", c.name, listOfNotNull(c.organization, c.phones.firstOrNull()?.value, c.emails.firstOrNull()?.value).joinToString(" · ").ifEmpty { "—" }) }
      if (contacts.size > 50) row("Contatti", "…", "altri ${contacts.size - 50}")
      row("Formato", "Versioni vCard", versions.joinToString(", ").ifEmpty { "non dichiarata" })
      row("Formato", "Con foto", photos.toString())
      row("Formato", "Foto remote non scaricate", contacts.count { it.photoRemote }.takeIf { it > 0 }?.toString())
      reading(decoded, source.sizeBytes, bytes.truncated)
    }
    val model = DataContactsModel(name, contacts, request.openWith)
    return FluidViewerOpened(FluidViewerContent.Custom(draw = { DataContactsPage(this, model, settings) }), details.list)
  }
}

/** I dati del pannello info che arrivano dopo l'apertura (il conto delle righe di un CSV): la lista è uno stato. */
internal fun SnapshotStateList<FluidViewerDetail>.replaceWith(items: List<FluidViewerDetail>) {
  clear()
  addAll(items)
}

/** Come si vede una pagina web nel tema scuro, per il pannello info. */
internal fun colorsLabel(colors: DataWebColors): String = when (colors) {
  DataWebColors.None -> "nessun colore suo: nel tema scuro prende quelli del tema"
  DataWebColors.Scheme -> "dichiara il suo schema chiaro e scuro"
  DataWebColors.Own -> "colori suoi, rispettati anche nel tema scuro"
}
