package dev.antigravity.fluidengine.viewer.archive

import android.content.Context
import android.graphics.BitmapFactory
import dev.antigravity.fluidengine.ui.fluid.FluidFileKind
import dev.antigravity.fluidengine.ui.fluid.fluidFormatFileDate
import dev.antigravity.fluidengine.viewer.FluidFileThumbnail
import dev.antigravity.fluidengine.viewer.FluidFileThumbnails
import dev.antigravity.fluidengine.viewer.FluidPdfPageSource
import dev.antigravity.fluidengine.viewer.FluidThumbnailLook
import dev.antigravity.fluidengine.viewer.FluidViewerOptions
import dev.antigravity.fluidengine.viewer.FluidViewerSource
import java.io.File
import java.io.OutputStream
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CancellationException

internal data class ArchiveRowKey(
  val ordinal: Int,
  val sizePx: Int,
  val look: FluidThumbnailLook,
)

internal data class ArchiveRowPreview(
  val thumbnail: FluidFileThumbnail?,
  val pixels: Pair<Int, Int>? = null,
  val pages: Int? = null,
)

private const val MaxRowBytes = 8L * 1024 * 1024
private const val MaxArchiveForRowPreviews = 64L * 1024 * 1024

/** The row must stay cheap even when a reader has to scan preceding archive members. */
internal fun canPreviewArchiveRow(index: ArchiveIndex, item: ArchiveItem, kind: FluidFileKind): Boolean =
  item.kind == ArchiveKind.File && !item.encrypted && (kind == FluidFileKind.Image || kind == FluidFileKind.Pdf) &&
    (item.bytes ?: 0) in 1..MaxRowBytes &&
    (index.compressedSize ?: Long.MAX_VALUE) <= MaxArchiveForRowPreviews &&
    (index.expandedSize ?: Long.MAX_VALUE) <= MaxArchiveForRowPreviews

internal class ArchiveRowSink(private val target: OutputStream) : OutputStream() {
  private var written = 0L

  private fun count(bytes: Int) {
    written += bytes
    if (written > MaxRowBytes) throw ArchiveProblem.Limit.error()
  }

  override fun write(value: Int) {
    count(1)
    target.write(value)
  }

  override fun write(bytes: ByteArray, offset: Int, length: Int) {
    count(length)
    target.write(bytes, offset, length)
  }
}

internal suspend fun readArchiveRowPreview(context: Context, staged: File, item: ArchiveItem, kind: FluidFileKind,
  sizePx: Int, look: FluidThumbnailLook, options: FluidViewerOptions): ArchiveRowPreview {
  val thumbnail = FluidViewerSource.of(staged, name = item.name).use { source ->
    FluidFileThumbnails.read(context, source, sizePx, look, options)
  }
  val pixels = if (kind == FluidFileKind.Image) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(staged.path, bounds)
    if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth to bounds.outHeight else null
  } else null
  val pages = if (kind == FluidFileKind.Pdf) {
    try {
      FluidPdfPageSource.open(staged).use { it.count.takeIf { count -> count > 0 } }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      null
    } catch (_: LinkageError) {
      null
    }
  } else null
  return ArchiveRowPreview(thumbnail, pixels, pages)
}

internal fun archiveRowSubtitle(item: ArchiveItem, preview: ArchiveRowPreview?, locale: Locale, today: LocalDate): String {
  if (item.kind == ArchiveKind.Link) return "Collegamento"
  if (item.kind == ArchiveKind.Special) return "Voce speciale"
  if (item.encrypted) return "Protetto da password"
  val extension = item.name.substringAfterLast('.', "").uppercase(Locale.ROOT)
  val type = when (extension) {
    "MD", "MARKDOWN" -> "Markdown"
    "KTS" -> "Kotlin script"
    "KT" -> "Kotlin"
    "TXT" -> "File di testo"
    "PDF" -> "PDF"
    "JPG", "JPEG" -> "Immagine JPEG"
    else -> when (FluidFileKind.from(null, item.name)) {
      FluidFileKind.Image -> listOf("Immagine", extension).filter(String::isNotEmpty).joinToString(" ")
      FluidFileKind.Audio -> listOf("Audio", extension).filter(String::isNotEmpty).joinToString(" ")
      FluidFileKind.Video -> listOf("Video", extension).filter(String::isNotEmpty).joinToString(" ")
      FluidFileKind.Archive -> listOf("Archivio", extension).filter(String::isNotEmpty).joinToString(" ")
      else -> if (extension.isEmpty()) "File" else "File $extension"
    }
  }
  val detail = preview?.pixels?.let { (w, h) -> "$w × $h" }
    ?: preview?.pages?.let { pages ->
      val count = NumberFormat.getIntegerInstance(locale).format(pages)
      if (locale.language == "it") {
        if (pages == 1) "$count pagina" else "$count pagine"
      } else {
        if (pages == 1) "$count page" else "$count pages"
      }
    }
    ?: item.modifiedSeconds?.let { fluidFormatFileDate(Instant.ofEpochSecond(it), locale, today = today) }
  return listOfNotNull(type, detail).joinToString(" · ")
}
