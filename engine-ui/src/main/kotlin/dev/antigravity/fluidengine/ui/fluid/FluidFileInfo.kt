package dev.antigravity.fluidengine.ui.fluid

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

/** Categoria visiva generica; non certifica la sicurezza o il contenuto del file. */
enum class FluidFileKind {
  Image, Pdf, Document, Spreadsheet, Presentation, Archive, Audio, Video, Text, Executable, Other;

  companion object {
    /** L'ultima estensione prevale; un MIME eseguibile non viene mai declassato da un nome innocuo. */
    fun from(mimeType: String?, fileName: String): FluidFileKind {
      val extension = fluidFileExtension(fileName).removePrefix(".").lowercase(Locale.ROOT)
      val byExtension = when (extension) {
        "apk", "apks", "xapk", "aab", "exe", "msi", "com", "bat", "cmd", "ps1", "sh", "jar", "deb", "rpm", "dmg", "pkg", "appimage", "scr", "vbs", "lnk", "desktop", "app", "ipa", "appx", "msix", "msixbundle" -> Executable
        "jpg", "jpeg", "png", "gif", "webp", "heic", "avif", "svg", "bmp", "tiff" -> Image
        "pdf" -> Pdf
        "doc", "docx", "odt", "rtf" -> Document
        "xls", "xlsx", "ods", "csv" -> Spreadsheet
        "ppt", "pptx", "odp" -> Presentation
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz" -> Archive
        "mp3", "wav", "ogg", "flac", "aac", "m4a", "opus" -> Audio
        "mp4", "mkv", "webm", "mov", "avi", "m4v" -> Video
        "txt", "md", "json", "xml", "html", "css", "js", "ts", "kt", "java", "py", "c", "cpp", "log", "yaml", "yml" -> Text
        else -> Other
      }
      val mime = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT).orEmpty()
      val byMime = when {
        mime in setOf("application/vnd.android.package-archive", "application/x-msdownload", "application/x-msdos-program", "application/x-dosexec", "application/x-executable", "application/x-sh", "application/x-shellscript", "text/x-shellscript", "application/x-powershell", "application/x-bat", "application/java-archive", "application/x-msi", "application/vnd.microsoft.portable-executable", "application/x-debian-package", "application/x-rpm", "application/x-apple-diskimage") -> Executable
        mime == "application/pdf" -> Pdf
        mime.startsWith("image/") -> Image
        mime.startsWith("audio/") -> Audio
        mime.startsWith("video/") -> Video
        "spreadsheet" in mime || "excel" in mime || mime == "text/csv" -> Spreadsheet
        "presentation" in mime || "powerpoint" in mime -> Presentation
        "wordprocessing" in mime || mime in setOf("application/msword", "application/rtf", "application/vnd.oasis.opendocument.text") -> Document
        mime in setOf("application/zip", "application/x-7z-compressed", "application/x-rar-compressed", "application/gzip", "application/x-tar", "application/vnd.rar") -> Archive
        mime.startsWith("text/") || mime in setOf("application/json", "application/xml") -> Text
        else -> Other
      }
      return if (byExtension == Executable || byMime == Executable) Executable
      else if (byExtension != Other) byExtension else byMime
    }
  }
}

/** Dimensione decimale (1000 byte = 1 KB), una cifra decimale al massimo; ignoto va rappresentato con null dal chiamante. */
fun fluidFormatFileSize(bytes: Long, locale: Locale = Locale.getDefault()): String {
  require(bytes >= 0) { "La dimensione non puo essere negativa" }
  val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB", "EB")
  var unit = if (bytes < 1000) 0 else (ln(bytes.toDouble()) / ln(1000.0)).toInt().coerceAtMost(units.lastIndex)
  // Non mostrare 1000 KB quando l'arrotondamento raggiunge il confine successivo.
  if (unit < units.lastIndex && bytes / 1000.0.pow(unit) >= 999.95) unit++
  val number = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = if (unit == 0) 0 else 1; isGroupingUsed = false }
  return "${number.format(bytes / 1000.0.pow(unit))} ${units[unit]}"
}

internal fun fluidFileExtension(name: String): String {
  val leaf = name.substringAfterLast('/').substringAfterLast('\\')
  val dot = leaf.lastIndexOf('.')
  return if (dot > 0 && dot < leaf.lastIndex) leaf.substring(dot) else ""
}

// Code point boundaries avoid producing half an emoji. The suffix is never sacrificed to a budget.
internal fun fluidTruncateFileName(name: String, maxCharacters: Int): String {
  val points = name.codePoints().toArray()
  if (points.size <= maxCharacters.coerceAtLeast(0)) return name
  val extension = fluidFileExtension(name)
  val suffixCount = extension.codePointCount(0, extension.length)
  val keep = (maxCharacters.coerceAtLeast(0) - suffixCount - 1).coerceAtLeast(0)
  return String(points, 0, keep.coerceAtMost(points.size - suffixCount)) + "…" + extension
}
