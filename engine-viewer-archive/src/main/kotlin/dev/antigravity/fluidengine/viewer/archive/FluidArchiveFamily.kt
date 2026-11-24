package dev.antigravity.fluidengine.viewer.archive

import androidx.compose.runtime.Composable
import androidx.core.content.FileProvider
import dev.antigravity.fluidengine.ui.fluid.FluidFileKind
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.fluidFormatFileSize
import dev.antigravity.fluidengine.ui.fluid.icons.Download
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.MoreHoriz
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfo
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfoAction
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfoRow
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfoSection
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.viewer.FluidViewerDetail
import dev.antigravity.fluidengine.viewer.FluidViewerDocument
import dev.antigravity.fluidengine.viewer.FluidViewerFamily
import dev.antigravity.fluidengine.viewer.FluidViewerFamilyType
import dev.antigravity.fluidengine.viewer.FluidViewerOpened
import dev.antigravity.fluidengine.viewer.FluidViewerRegistry
import dev.antigravity.fluidengine.viewer.FluidViewerRequest
import dev.antigravity.fluidengine.viewer.FluidViewerSignature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.zhanghai.android.libarchive.Archive
import java.io.OutputStream
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** A regular file that the archive can safely offer to the app's extraction destination. [path] is relative. */
data class FluidArchiveEntry(val path: String, val sizeBytes: Long?)

/** Results written so far by [FluidArchiveExtraction.writeFiles]. Totals exclude links and unsafe entries. */
data class FluidArchiveProgress(val completedFiles: Int, val totalFiles: Int, val writtenBytes: Long)

/**
 * The app chooses a destination and owns its I/O policy, collision handling and rollback. Use [writeFiles] once,
 * after choosing the destination, inside the extraction callback. Each output is closed after its file. An error
 * or cancellation can leave partial output in the app's destination. Empty folders are omitted.
 */
class FluidArchiveExtraction internal constructor(
  val files: List<FluidArchiveEntry>,
  private val copy: suspend (suspend (FluidArchiveEntry) -> OutputStream) -> Unit,
) {
  private val used = AtomicBoolean()
  private val active = AtomicBoolean(true)

  suspend fun writeFiles(openOutput: suspend (FluidArchiveEntry) -> OutputStream) {
    check(active.get() && used.compareAndSet(false, true)) { "Richiesta di estrazione già conclusa o utilizzata" }
    copy(openOutput)
  }

  internal fun finish() { active.set(false) }
}

/**
 * Optional archive viewer discovered automatically by [FluidViewerRegistry]. Install a configured instance with
 * `FluidViewerRegistry.install(FluidArchiveFamily(onExtractAll = …))` when the app offers «Estrai tutto».
 * The callback runs on the UI dispatcher, and [FluidArchiveExtraction.writeFiles] copies on the I/O dispatcher.
 */
class FluidArchiveFamily @JvmOverloads constructor(
  private val onExtractAll: (suspend (FluidArchiveExtraction) -> Unit)? = null,
) : FluidViewerFamily {
  override val id = "archive"

  override val types = listOf(
    type(ArchiveFormat.Zip, "Archivio ZIP", setOf("zip"), setOf("application/zip", "application/x-zip-compressed"),
      FluidViewerSignature.anyOf(bytes(0x50, 0x4b, 3, 4), bytes(0x50, 0x4b, 5, 6))),
    type(ArchiveFormat.SevenZip, "Archivio 7z", setOf("7z"), setOf("application/x-7z-compressed"),
      bytes(0x37, 0x7a, 0xbc, 0xaf, 0x27, 0x1c)),
    type(ArchiveFormat.Rar, "Archivio RAR", setOf("rar"), setOf("application/vnd.rar", "application/x-rar-compressed"),
      FluidViewerSignature.anyOf(bytes(0x52, 0x61, 0x72, 0x21, 0x1a, 7, 0), bytes(0x52, 0x61, 0x72, 0x21, 0x1a, 7, 1, 0))),
    type(ArchiveFormat.Tar, "Archivio TAR", setOf("tar"), setOf("application/x-tar"),
      FluidViewerSignature.ascii("ustar", offset = 257)),
    type(ArchiveFormat.Compressed, "Archivio compresso",
      setOf("gz", "tgz", "bz2", "tbz", "tbz2", "xz", "txz", "zst", "tzst"),
      setOf("application/gzip", "application/x-gzip", "application/x-bzip2", "application/x-xz", "application/zstd"),
      FluidViewerSignature.anyOf(bytes(0x1f, 0x8b, 8), bytes(0x42, 0x5a, 0x68), bytes(0xfd, 0x37, 0x7a, 0x58, 0x5a, 0),
        bytes(0x28, 0xb5, 0x2f, 0xfd))),
  )

  override suspend fun open(request: FluidViewerRequest): FluidViewerOpened? = withContext(Dispatchers.IO) {
    val format = ArchiveFormat.valueOf(request.type.id)
    if (format == ArchiveFormat.Zip && request.source.name.hasDedicatedViewerWithoutModule()) return@withContext null
    val reader = NativeArchive(request.source, format)
    val index = reader.index(ArchiveEncoding.Cp437)
    val session = ArchiveSession(request, format, reader, index, onExtractAll = onExtractAll)
    val content = archivePageContent(session)
    ArchivePages[content] = session
    FluidViewerOpened(content, session.details) {
      ArchivePages.remove(content)
      session.close()
    }
  }

  /** F6C.17 · L'icona d'archivio nello stile della pagina D, con l'estensione ([archiveThumbnail]). */
  override suspend fun thumbnail(request: FluidViewerRequest, sizePx: Int) =
    if (ArchiveFormat.valueOf(request.type.id) == ArchiveFormat.Zip && request.source.name.hasDedicatedViewerWithoutModule()) null
    else dev.antigravity.fluidengine.viewer.FluidViewerRaster.thumbnail(request.context, sizePx) { archiveThumbnail(request, it) }

  private fun type(format: ArchiveFormat, label: String, extensions: Set<String>, mimeTypes: Set<String>,
    signature: FluidViewerSignature) = FluidViewerFamilyType(format.name, label, extensions, mimeTypes, signature,
    kind = FluidFileKind.Archive)

  private fun bytes(vararg numbers: Int) = FluidViewerSignature.bytes(*numbers)
}

internal fun archivePageContent(session: ArchiveSession): FluidViewerContent.Custom {
  val find = ArchiveFind(session)
  return FluidViewerContent.Custom(find = find, actions = {
    action("fluid.archive.options", FluidIcons.MoreHoriz, "Altro", placement = FluidToolbarPlacement.BottomBar) {
      buildList {
        add(FluidContextAction(ArchiveMenu.Sort.title) { session.pageMenu = ArchiveMenu.Sort })
        add(FluidContextAction(ArchiveMenu.Filter.title) { session.pageMenu = ArchiveMenu.Filter })
        if (session.format == ArchiveFormat.Zip) add(FluidContextAction(ArchiveMenu.Encoding.title, enabled = !session.busy) {
          session.pageMenu = ArchiveMenu.Encoding
        })
        session.request.share?.let { add(FluidContextAction("Condividi", onClick = it)) }
      }
    }
    if (session.canExport) button("fluid.archive.extract", FluidIcons.Download, "Estrai tutto", session::requestExport,
      placement = FluidToolbarPlacement.BottomBar, enabled = !session.busy)
  }) { ArchivePage(session, this, find) }
}

private val ArchivePages = Collections.synchronizedMap(WeakHashMap<FluidViewerContent, ArchiveSession>())

/** Use in `FluidViewerInfoProvider` to show the archive facts, sections, and available actions. */
@Composable
fun FluidViewerDocument.fluidArchiveViewerInfo(): FluidViewerInfo? {
  val session = ArchivePages[content] ?: return null
  val details = session.details
  return FluidViewerInfo(
    file = file.info.copy(kind = FluidFileKind.Archive),
    subtitle = listOfNotNull(session.index.format, session.index.compressedSize?.let(::fluidFormatFileSize)).joinToString(" · "),
    facts = details.filter { it.section == null }.map { if (it.label == "Voci") "${it.value} voci" else it.value },
    sections = details.filter { it.section != null }.groupBy { it.section.orEmpty() }.map { (title, rows) ->
      FluidViewerInfoSection(title, rows.map { FluidViewerInfoRow(it.label, it.value) })
    },
    actions = buildList {
      add(FluidViewerInfoAction("Apri con…", session::requestOpenOriginal))
      session.request.share?.let { add(FluidViewerInfoAction("Condividi", it)) }
      if (session.canExport) add(FluidViewerInfoAction("Estrai tutto", session::requestExport))
    },
  )
}

/** A dedicated, non-exported FileProvider grants read access only to previews explicitly sent out of the viewer. */
class ArchivePreviewProvider : FileProvider()

internal fun ArchiveSession.archiveDetails(): List<FluidViewerDetail> {
  val info = index
  val protection = when (info.protection) {
    ArchiveProtection.None -> "Nessuna rilevata"
    ArchiveProtection.Entries -> if (unlocked) "Password inserita" else "Voci cifrate"
    ArchiveProtection.SevenZipHeaders -> "Intestazioni e contenuto protetti"
    ArchiveProtection.Rar -> "RAR protetto"
  }
  val integrity = info.problem?.label ?: when {
    info.protection == ArchiveProtection.Rar || info.protection == ArchiveProtection.SevenZipHeaders -> "Da verificare"
    else -> "Indice leggibile"
  }
  return buildList {
    add(FluidViewerDetail("Voci", info.items.size.toString()))
    info.compression?.let { add(FluidViewerDetail("Compressione", it)) }
    add(FluidViewerDetail("Integrità", integrity))
    add(FluidViewerDetail("Cartelle", info.tree.folderCount.toString(), "Contenuto"))
    add(FluidViewerDetail("File", info.files.size.toString(), "Contenuto"))
    add(FluidViewerDetail("Dimensione estratta", info.expandedSize?.let(::fluidFormatFileSize) ?: "Ignorata", "Contenuto"))
    info.files.maxByOrNull { it.bytes ?: 0 }?.let { add(FluidViewerDetail("File più grande", it.name, "Contenuto")) }
    add(FluidViewerDetail("Cifratura", protection, "Sicurezza"))
    add(FluidViewerDetail("Percorsi ignorati", info.hiddenPaths.toString(), "Sicurezza"))
    add(FluidViewerDetail("Collegamenti", info.items.count { it.kind == ArchiveKind.Link }.toString(), "Sicurezza"))
    add(FluidViewerDetail("Formato", info.format, "Archivio"))
    add(FluidViewerDetail("Dimensione compressa", info.compressedSize?.let(::fluidFormatFileSize) ?: "Ignorata", "Archivio"))
    if (format == ArchiveFormat.Zip) add(FluidViewerDetail("Nomi senza Unicode", encoding.label, "Archivio"))
    add(FluidViewerDetail("Decoder", "libarchive-android 1.1.7 · ${Archive.versionString().toString(Charsets.UTF_8)}", "Lettura"))
    if (info.sevenZipFallback && SevenZipAes.available) {
      add(FluidViewerDetail("7z cifrati", "Apache Commons Compress · XZ", "Lettura"))
    }
  }
}

private fun String.hasDedicatedViewerWithoutModule(): Boolean {
  val family = when (substringAfterLast('.', "").lowercase(Locale.ROOT)) {
    "docx", "docm", "xlsx", "xlsm", "pptx", "pptm", "odt", "ods", "odp", "pages", "numbers", "key" -> "office"
    "epub" -> "data"
    else -> return false
  }
  return FluidViewerRegistry.families.none { it.id == family }
}
