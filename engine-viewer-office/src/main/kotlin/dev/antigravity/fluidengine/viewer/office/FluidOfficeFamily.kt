package dev.antigravity.fluidengine.viewer.office

import androidx.compose.ui.graphics.ImageBitmap
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.viewer.*
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.*

/**
 * Optional offline Office readers. ServiceLoader discovers this class when the application includes
 * the module.
 */
class FluidOfficeFamily : FluidViewerFamily {
  override val id = "office"
  override val types =
    listOf(
      type(
        "docx",
        "Documento Word",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "word/document.xml",
      ),
      type("odt", "Documento OpenDocument", "application/vnd.oasis.opendocument.text"),
      type("rtf", "Documento RTF", "application/rtf"),
      type(
        "xlsx",
        "Cartella Excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "xl/workbook.xml",
      ),
      type("ods", "Foglio OpenDocument", "application/vnd.oasis.opendocument.spreadsheet"),
      type(
        "pptx",
        "Presentazione PowerPoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "ppt/presentation.xml",
      ),
      type("odp", "Presentazione OpenDocument", "application/vnd.oasis.opendocument.presentation"),
    )

  private fun type(ext: String, title: String, mime: String, part: String? = null) =
    FluidViewerFamilyType(
      ext,
      title,
      setOf(ext),
      if (ext == "rtf") setOf(mime, "text/rtf") else setOf(mime),
      signature =
        when {
          part != null -> FluidViewerSignature.zipEntry(part)
          ext == "rtf" -> FluidViewerSignature.ascii("{\\rtf")
          else -> FluidViewerSignature.ascii(mime, 38)
        },
      priority = 5,
      preset =
        if (ext == "ods" || ext == "xlsx") FluidViewerPreset.Grid else FluidViewerPreset.Pages,
    )

  override suspend fun open(request: FluidViewerRequest): FluidViewerOpened =
    withContext(Dispatchers.IO) {
      val file = File.createTempFile("fluid-office-", ".tmp", request.context.cacheDir)
      val worker = CoroutineScope(SupervisorJob() + Dispatchers.Default)
      try {
        copyOfficeInput(request, file)
        val info = OfficeInfo()
        val fonts = OfficeFonts(request.context)
        var sections: List<Section>? = null
        var slides: List<OfficePage>? = null
        var grid: OfficeGrid? = null
        val ext = request.type.id
        if (ext == "rtf") {
          if (file.length() > 8_000_000) tooLarge("RTF oltre 8 MB")
          sections = readRtf(file.readBytes(), info)
        } else {
          val magic = file.inputStream().use { input -> ByteArray(8).also { input.read(it) } }
          if (
            magic.contentEquals(
              byteArrayOf(
                0xd0.toByte(),
                0xcf.toByte(),
                0x11,
                0xe0.toByte(),
                0xa1.toByte(),
                0xb1.toByte(),
                0x1a,
                0xe1.toByte(),
              )
            )
          ) {
            val marker = "EncryptedPackage".toByteArray(Charsets.UTF_16LE)
            val encrypted =
              file.inputStream().buffered().use { input ->
                var matched = 0
                while (matched < marker.size) {
                  val next = input.read()
                  if (next < 0) break
                  matched =
                    if (next == marker[matched].toInt()) matched + 1
                    else if (next == marker[0].toInt()) 1 else 0
                }
                matched == marker.size
              }
            if (encrypted) protectedFile() else damaged("Formato Office binario inatteso")
          }
          ZipFile(file).use { zip ->
            if (zip.size() > 8192) tooLarge("Oltre 8192 parti nel documento")
            val pkg = OfficePackage(zip)
            if (
              ext.startsWith("od") &&
                pkg.xml("META-INF/manifest.xml")?.first("encryption-data") != null
            )
              protectedFile()
            readMetadata(pkg, info)
            when (ext) {
              "docx" -> sections = WordReader(pkg, info).read()
              "xlsx" ->
                grid =
                  ExcelReader(pkg, info, request.context.resources.configuration.locales[0], fonts).read()
              "pptx" -> slides = PowerPointReader(pkg, info, fonts).read()
              "odt" -> sections = OdfReader(pkg, info).document()
              "ods" -> grid = OdfReader(pkg, info).grid()
              "odp" -> slides = OdfReader(pkg, info).slides(fonts)
              else -> damaged("Tipo Office sconosciuto")
            }
          }
        }
        grid?.let {
          worker.cancel()
          return@withContext FluidViewerOpened(FluidViewerContent.Grid(it), info.finish())
        }
        val pages = OfficePages(worker)
        val laidOut = sections
        val fixed = slides
        if (fixed != null) info.add("Slide", fixed.size, "Contenuto")
        // F6C.19: le note del relatore lette con le slide («Slide 3» nella sezione delle note) vanno anche alla sorgente.
        if (fixed != null) pages.speakerNotes = info.details.filter { it.section == SpeakerNotesSection }
          .mapNotNull { detail -> detail.label.removePrefix("Slide ").trim().toIntOrNull()?.let { it - 1 to detail.value } }
          .toMap()
        val paper =
          laidOut?.firstOrNull()?.paper ?: fixed?.firstOrNull()?.paper ?: damaged("Nessuna pagina")
        info.add(
          "Foglio",
          "%.1f × %.1f pt".format(java.util.Locale.ROOT, paper.width, paper.height),
          "Impaginazione",
        )
        // Le righe che arrivano dall'impaginazione, in sottofondo: ogni aggiunta è una lista intera al posto dell'altra
        // (F7.20), così il pannello che la legge non la trova mai a metà.
        val details = FluidViewerLiveDetails(info.finish())
        pages.start(
          onPartial = { error ->
            details.add(
              FluidViewerDetail(
                "Impaginazione interrotta",
                error.message ?: "Errore di lettura",
                "Lettura",
              )
            )
          }
        ) {
          if (laidOut != null) paginate(laidOut, it, fonts)
          else fixed.orEmpty().forEach { page -> it.append(page) }
          details.add(FluidViewerDetail("Pagine", it.count.toString(), "Contenuto"))
        }
        FluidViewerOpened(FluidViewerContent.Pages(pages), details, pages::close)
      } catch (t: Throwable) {
        worker.cancel()
        throw t
      } finally {
        file.delete()
      }
    }

  /**
   * The saved thumbnail when it says something, otherwise the first page, slide or sheet drawn by the module
   * ([officeThumbnail]): never a white tile. `null` (the app's icon) only when neither is possible in time.
   */
  override suspend fun thumbnail(request: FluidViewerRequest, sizePx: Int): ImageBitmap? =
    dev.antigravity.fluidengine.viewer.FluidViewerRaster.thumbnail(request.context, sizePx) { officeThumbnail(request, it) }
}

private fun readMetadata(pkg: OfficePackage, info: OfficeInfo) {
  val core = pkg.xml("docProps/core.xml") ?: pkg.xml("meta.xml")
  val app = pkg.xml("docProps/app.xml")
  for ((key, label) in
    listOf(
      "title" to "Titolo",
      "creator" to "Autore",
      "initial-creator" to "Primo autore",
      "created" to "Creato",
      "creation-date" to "Creato",
      "modified" to "Modificato",
      "date" to "Modificato",
      "revision" to "Revisione",
      "generator" to "Applicazione",
    )) {
    info.add(label, core?.first(key)?.text(), "Documento")
  }
  info.add("Applicazione", app?.child("Application")?.text(), "Documento")
  val words =
    app?.child("Words")?.text()?.toIntOrNull()
      ?: core?.first("document-statistic")?.get("word-count")?.toIntOrNull()
  if (words != null && words > 0) info.add("Parole", words, "Contenuto")
}
