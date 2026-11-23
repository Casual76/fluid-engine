package dev.antigravity.fluidengine.viewer.archive

import java.io.IOException
import java.util.Locale

internal const val ArchiveEntryLimit = 128L * 1024 * 1024
internal const val ArchiveTotalLimit = 512L * 1024 * 1024
internal const val ArchiveMaxEntries = 10_000
private const val ExpansionRatio = 200L
private const val SmallArchiveAllowance = 1024L * 1024

internal class ArchiveBudget(compressedSize: Long?) {
  val totalLimit = compressedSize?.coerceIn(0, ArchiveTotalLimit / ExpansionRatio + 1)
    ?.times(ExpansionRatio)?.coerceIn(SmallArchiveAllowance, ArchiveTotalLimit) ?: ArchiveTotalLimit
  private var expanded = 0L

  fun addEntry(size: Long) {
    if (size < 0 || size > ArchiveEntryLimit || size > totalLimit - expanded) throw ArchiveProblem.Limit.error()
    expanded += size
  }

  fun checkRead(entryBytes: Long) {
    if (entryBytes < 0 || entryBytes > ArchiveEntryLimit || entryBytes > totalLimit - expanded) {
      throw ArchiveProblem.Limit.error()
    }
  }
}

internal enum class ArchiveProblem(val label: String) {
  Broken("Archivio danneggiato o incompleto"),
  Limit("Superato il limite di estrazione"),
  Locked("Archivio protetto"),
  WrongPassword("Password sbagliata"),
  Changed("L’archivio è cambiato. Riaprilo per continuare."),
  Unsafe("Questa voce non può essere estratta in sicurezza"),
  Unreadable("Non è stato possibile leggere il file"),
  NoApp("Nessuna app disponibile per questo file");

  fun error(cause: Throwable? = null) = ArchiveReadFailure(this, cause)
}

internal class ArchiveReadFailure(val problem: ArchiveProblem, cause: Throwable? = null) : IOException(problem.label, cause)

internal enum class ArchiveKind { File, Folder, Link, Special }
internal enum class ArchiveEncoding(val label: String, val charsetName: String) {
  Cp437("CP437", "IBM437"), ShiftJis("Shift-JIS", "Shift_JIS"), Utf8("UTF-8", "UTF-8"),
}
internal enum class ArchiveProtection { None, Entries, SevenZipHeaders, Rar }

internal data class ArchiveItem(
  val ordinal: Int,
  val path: String,
  val kind: ArchiveKind,
  val bytes: Long?,
  val modifiedSeconds: Long?,
  val encrypted: Boolean = false,
) {
  val name: String get() = path.substringAfterLast('/')
  val parent: String get() = path.substringBeforeLast('/', "")
}

internal data class ArchiveIndex(
  val format: String,
  val compression: String?,
  val compressedSize: Long?,
  val items: List<ArchiveItem>,
  val protection: ArchiveProtection,
  val problem: ArchiveProblem?,
  val hiddenPaths: Int = 0,
  val streamedZip: Boolean = false,
  val sevenZipFallback: Boolean = false,
) {
  val files = items.filter { it.kind == ArchiveKind.File }
  val expandedSize: Long? = files.takeIf { it.all { file -> file.bytes != null } }?.fold(0L) { sum, file ->
    sum + minOf(file.bytes ?: 0, Long.MAX_VALUE - sum)
  }
  val tree = ArchiveTree(items)
}

/** An index of direct children. Real directory records can arrive after their children in an archive. */
internal class ArchiveTree(items: List<ArchiveItem>) {
  private val folders = linkedMapOf<String, ArchiveItem>()
  private val children: Map<String, List<ArchiveItem>>
  val folderCount: Int get() = folders.size
  val extractable: List<ArchiveItem>
  private val folderBytes = mutableMapOf<String, Long>()
  private val foldersWithUnknownSize = mutableSetOf<String>()

  init {
    for (item in items) {
      if (item.kind == ArchiveKind.Folder) folders[item.path] = item
      var parent = item.parent
      while (parent.isNotEmpty() && parent !in folders) {
        folders[parent] = ArchiveItem(-1, parent, ArchiveKind.Folder, null, null)
        parent = folders.getValue(parent).parent
      }
    }
    val leaves = items.filter { it.kind != ArchiveKind.Folder }
    children = (folders.values + leaves).groupBy { it.parent }
    for (file in leaves) {
      if (file.kind != ArchiveKind.File) continue
      val bytes = file.bytes
      var parent = file.parent
      while (parent.isNotEmpty()) {
        if (bytes == null) foldersWithUnknownSize += parent
        else {
          val total = folderBytes[parent] ?: 0L
          folderBytes[parent] = total + bytes.coerceIn(0L, Long.MAX_VALUE - total)
        }
        parent = parent.substringBeforeLast('/', "")
      }
    }
    val ambiguousPaths = leaves.groupingBy { it.path.lowercase(Locale.ROOT) }.eachCount()
    val folderPaths = folders.keys.mapTo(HashSet()) { it.lowercase(Locale.ROOT) }
    extractable = leaves.filter {
      val path = it.path.lowercase(Locale.ROOT)
      it.kind == ArchiveKind.File && path !in folderPaths && ambiguousPaths[path] == 1
    }
  }

  fun children(path: String): List<ArchiveItem> = children[path].orEmpty()
  fun bytesIn(path: String): Long? = folderBytes[path]?.takeUnless { path in foldersWithUnknownSize }
}

/** Paths stay relative and portable, even when a caller uses a Windows-backed document provider. */
internal fun archiveSafePath(name: String): String? {
  if (name.length > 1024 || name.startsWith('/') || name.startsWith('\\')) return null
  if (name.any { it < ' ' || it == '\u007f' || it == ':' || it == '\ufffd' }) return null
  val parts = name.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
  if (parts.isEmpty() || parts.size > 32 || parts.any { it == ".." || it.endsWith('.') || it.endsWith(' ') }) return null
  return parts.joinToString("/")
}
