package dev.antigravity.fluidengine.viewer.archive

import dev.antigravity.fluidengine.viewer.FluidViewerSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import me.zhanghai.android.libarchive.ArchiveException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.Locale

internal const val ArchiveBufferSize = 64 * 1024

internal enum class ArchiveFormat(val label: String) {
  Zip("ZIP"), SevenZip("7z"), Rar("RAR"), Tar("TAR"), Compressed("File compresso");
}

internal class NativeArchive(
  private val source: FluidViewerSource,
  private val hint: ArchiveFormat,
) {
  suspend fun index(encoding: ArchiveEncoding): ArchiveIndex {
    val index = scan(encoding, streamedZip = false)
    // A missing ZIP central directory can still leave a readable prefix of local records. Those records
    // lack the Unix attributes in the central directory (including symlinks), so recovery is list-only.
    if (hint == ArchiveFormat.Zip && index.problem == ArchiveProblem.Broken) {
      val recovered = scan(encoding, streamedZip = true)
      if (recovered.items.size > index.items.size) return recovered.copy(problem = ArchiveProblem.Broken)
    }
    return index
  }

  private suspend fun scan(encoding: ArchiveEncoding, streamedZip: Boolean): ArchiveIndex {
    val items = mutableListOf<ArchiveItem>()
    val zipMethods = linkedSetOf<String>()
    var result = ArchiveIndex(hint.label, null, source.sizeBytes, emptyList(), ArchiveProtection.None, null,
      streamedZip = streamedZip)
    var hidden = 0
    try {
      read(encoding, streamedZip) { reader ->
        result = result.copy(compressedSize = reader.compressedSize)
        val budget = ArchiveBudget(reader.compressedSize)
        for (ordinal in 0..ArchiveMaxEntries) {
          currentCoroutineContext().ensureActive()
          val entry = reader.next() ?: break
          if (ordinal == ArchiveMaxEntries) throw ArchiveProblem.Limit.error()
          if (hint == ArchiveFormat.Zip && entry.kind == ArchiveKind.File) reader.zipMethod?.let(zipMethods::add)
          result = result.copy(format = reader.format, compression = reader.compression,
            protection = when {
              reader.rar && (entry.encrypted || reader.encrypted) -> ArchiveProtection.Rar
              entry.encrypted || reader.encrypted -> ArchiveProtection.Entries
              else -> result.protection
            })
          val visible = entry.path != null
          var extractedSize = entry.bytes
          if (visible) items += entry.item(ordinal) else if (!entry.directoryRoot) hidden++
          if (entry.kind == ArchiveKind.File && entry.bytes != null) budget.checkRead(entry.bytes)
          if (result.protection == ArchiveProtection.Rar) break
          if (reader.checkWhileListing && !entry.encrypted && entry.kind == ArchiveKind.File) {
            extractedSize = reader.copyData(budget, entry.bytes, null)
          } else {
            // Solid 7z and seekable ZIP can skip known data without writing any of it.
            reader.skip()
            if (entry.kind == ArchiveKind.File && entry.bytes != null) budget.addEntry(entry.bytes)
          }
          if (visible && entry.bytes == null) items[items.lastIndex] = entry.item(ordinal, extractedSize)
        }
        result = result.copy(format = reader.format, compression = reader.compression)
      }
    } catch (e: ArchiveException) {
      val problem = e.problem()
      result = when {
        hint == ArchiveFormat.Rar && problem == ArchiveProblem.Locked -> result.copy(protection = ArchiveProtection.Rar)
        hint == ArchiveFormat.SevenZip && problem == ArchiveProblem.Locked -> result.copy(
          protection = if (items.isEmpty()) ArchiveProtection.SevenZipHeaders else ArchiveProtection.Entries,
          sevenZipFallback = true,
        )
        else -> result.copy(problem = problem)
      }
    } catch (e: ArchiveReadFailure) {
      result = result.copy(problem = e.problem)
    }
    if (hint == ArchiveFormat.SevenZip && result.protection == ArchiveProtection.Entries) {
      result = result.copy(sevenZipFallback = true)
    }
    return result.copy(items = items, hiddenPaths = hidden,
      compression = zipMethods.takeIf { it.isNotEmpty() }?.joinToString(" / ") ?: result.compression)
  }

  suspend fun extract(
    items: List<ArchiveItem>,
    encoding: ArchiveEncoding,
    password: CharArray?,
    write: suspend (ArchiveItem, suspend (OutputStream) -> Unit) -> Unit,
  ) {
    if (items.isEmpty()) return
    var currentEncrypted = false
    try {
      read(encoding, false, password) { reader ->
        val budget = ArchiveBudget(reader.compressedSize)
        val wanted = items.sortedBy { it.ordinal }.iterator()
        var item = wanted.next()
        for (ordinal in 0..items.maxOf { it.ordinal }) {
          currentCoroutineContext().ensureActive()
          currentEncrypted = false
          val entry = reader.next() ?: throw ArchiveProblem.Changed.error()
          currentEncrypted = entry.encrypted
          if (entry.kind == ArchiveKind.File && entry.bytes != null) budget.checkRead(entry.bytes)
          if (ordinal == item.ordinal) {
            if (entry.kind != ArchiveKind.File || entry.path != item.path || entry.bytes?.let { it != item.bytes } == true) {
              throw ArchiveProblem.Changed.error()
            }
            if (entry.encrypted && password == null) throw ArchiveProblem.Locked.error()
            write(item) { sink -> reader.copyData(budget, entry.bytes, sink) }
            if (!wanted.hasNext()) break
            item = wanted.next()
          } else if (reader.checkWhileListing && !entry.encrypted && entry.kind == ArchiveKind.File) {
            reader.copyData(budget, entry.bytes, null)
          } else {
            reader.skip()
            if (entry.kind == ArchiveKind.File && entry.bytes != null) budget.addEntry(entry.bytes)
          }
        }
      }
    } catch (e: ArchiveException) {
      val problem = e.problem()
      throw if (currentEncrypted && password != null && problem == ArchiveProblem.Locked) {
        ArchiveProblem.WrongPassword.error(e)
      } else problem.error(e)
    }
  }

  private suspend fun <T> read(
    encoding: ArchiveEncoding,
    streamedZip: Boolean,
    password: CharArray? = null,
    block: suspend (Reader) -> T,
  ): T = source.seekable().use { descriptor ->
    val archive = Archive.readNew()
    try {
      val size = descriptor.statSize.takeIf { it >= 0 } ?: source.sizeBytes
      setup(archive, streamedZip, password)
      Archive.readOpenFd(archive, descriptor.fd, ArchiveBufferSize.toLong())
      block(Reader(archive, size, encoding, streamedZip))
    } finally {
      runCatching { Archive.readFree(archive) }
    }
  }

  private fun setup(archive: Long, streamedZip: Boolean, password: CharArray?) {
    when (hint) {
      ArchiveFormat.Zip -> {
        if (streamedZip) Archive.readSupportFormatZipStreamable(archive) else Archive.readSupportFormatZipSeekable(archive)
        // Ask libarchive to preserve non-Unicode names as bytes. The JVM selects their ZIP charset.
        Archive.readSetFormatOption(archive, "zip".encodeToByteArray(), "compat-2x".encodeToByteArray(), "1".encodeToByteArray())
      }
      ArchiveFormat.SevenZip -> Archive.readSupportFormat7zip(archive)
      ArchiveFormat.Rar -> {
        Archive.readSupportFormatRar(archive)
        Archive.readSupportFormatRar5(archive)
      }
      ArchiveFormat.Tar, ArchiveFormat.Compressed -> {
        Archive.readSupportFilterGzip(archive)
        Archive.readSupportFilterBzip2(archive)
        Archive.readSupportFilterXz(archive)
        Archive.readSupportFilterZstd(archive)
        Archive.readSupportFormatTar(archive)
        Archive.readSupportFormatEmpty(archive)
        if (hint == ArchiveFormat.Compressed && !source.name.isTarName()) Archive.readSupportFormatRaw(archive)
      }
    }
    if (password != null) {
      val encoded = Charsets.UTF_8.encode(java.nio.CharBuffer.wrap(password))
      val bytes = ByteArray(encoded.remaining()).also { encoded.get(it) }
      try {
        Archive.readAddPassphrase(archive, bytes)
      } finally {
        bytes.fill(0)
        if (encoded.hasArray()) encoded.array().fill(0)
      }
    }
  }

  private inner class Reader(
    private val archive: Long,
    val compressedSize: Long?,
    private val encoding: ArchiveEncoding,
    private val streamedZip: Boolean,
  ) {
    private val buffer = ByteBuffer.allocate(ArchiveBufferSize)
    private val formatCode: Int get() = Archive.format(archive) and Archive.FORMAT_BASE_MASK
    val rar get() = formatCode == Archive.FORMAT_RAR || formatCode == Archive.FORMAT_RAR_V5
    val encrypted get() = Archive.readHasEncryptedEntries(archive) > 0
    val zipMethod: String? get() = Archive.formatName(archive)?.toString(Charsets.UTF_8)
      ?.substringAfter('(', "")?.substringBeforeLast(')')?.takeIf { it.isNotBlank() }
    val checkWhileListing get() = streamedZip || formatCode == Archive.FORMAT_RAW || formatCode == Archive.FORMAT_TAR
    val format: String get() = when (formatCode) {
      Archive.FORMAT_ZIP -> "ZIP"
      Archive.FORMAT_7ZIP -> "7z"
      Archive.FORMAT_RAR -> "RAR 4"
      Archive.FORMAT_RAR_V5 -> "RAR 5"
      Archive.FORMAT_TAR -> "TAR"
      Archive.FORMAT_RAW -> compression ?: hint.label
      else -> hint.label
    }
    val compression: String? get() = when (Archive.filterCode(archive, 0)) {
      Archive.FILTER_GZIP -> "gzip"
      Archive.FILTER_BZIP2 -> "bzip2"
      Archive.FILTER_XZ -> "xz"
      Archive.FILTER_ZSTD -> "Zstandard"
      else -> when (formatCode) {
        Archive.FORMAT_ZIP -> "Per voce"
        Archive.FORMAT_7ZIP, Archive.FORMAT_RAR, Archive.FORMAT_RAR_V5 -> "Interna al formato"
        Archive.FORMAT_TAR -> "Nessuna"
        else -> null
      }
    }

    fun next(): NativeEntry? {
      val entry = Archive.readNextHeader(archive)
      if (entry == 0L) return null
      val raw = formatCode == Archive.FORMAT_RAW
      if (raw && Archive.filterCode(archive, 0) == Archive.FILTER_NONE) throw ArchiveProblem.Broken.error()
      val kind = when {
        ArchiveEntry.hardlink(entry) != null || ArchiveEntry.symlink(entry) != null -> ArchiveKind.Link
        else -> when (ArchiveEntry.filetype(entry)) {
          ArchiveEntry.AE_IFREG -> ArchiveKind.File
          ArchiveEntry.AE_IFDIR -> ArchiveKind.Folder
          ArchiveEntry.AE_IFLNK -> ArchiveKind.Link
          else -> ArchiveKind.Special
        }
      }
      val name = if (raw) source.name.substringAfterLast('/').substringBeforeLast('.').ifBlank { "contenuto" }
      else {
        val original = ArchiveEntry.pathname(entry)
        val utf8 = original?.toString(Charsets.UTF_8) ?: ArchiveEntry.pathnameUtf8(entry).orEmpty()
        // The Android binary does not have iconv. Decode legacy ZIP names with the platform's charset tables.
        if (original != null && formatCode == Archive.FORMAT_ZIP && '\ufffd' in utf8) {
          original.toString(Charset.forName(encoding.charsetName))
        } else utf8
      }
      return NativeEntry(archiveSafePath(name), kind, ArchiveEntry.size(entry).takeIf { ArchiveEntry.sizeIsSet(entry) && it >= 0 },
        ArchiveEntry.mtime(entry).takeIf { ArchiveEntry.mtimeIsSet(entry) }, ArchiveEntry.isEncrypted(entry),
        kind == ArchiveKind.Folder && name.trimEnd('/') in setOf("", "."))
    }

    fun skip() = Archive.readDataSkip(archive)

    suspend fun copyData(budget: ArchiveBudget, expected: Long?, output: OutputStream?): Long {
      var count = 0L
      while (true) {
        currentCoroutineContext().ensureActive()
        buffer.clear()
        Archive.readData(archive, buffer)
        val length = buffer.position()
        if (length == 0) break
        count += length
        budget.checkRead(count)
        if (expected != null && count > expected) throw ArchiveProblem.Broken.error()
        output?.write(buffer.array(), 0, length)
      }
      if (expected != null && expected != count) throw ArchiveProblem.Broken.error()
      budget.addEntry(count)
      return count
    }
  }
}

private data class NativeEntry(
  val path: String?,
  val kind: ArchiveKind,
  val bytes: Long?,
  val modified: Long?,
  val encrypted: Boolean,
  val directoryRoot: Boolean,
) {
  fun item(ordinal: Int, size: Long? = bytes) = ArchiveItem(ordinal, checkNotNull(path), kind, size, modified, encrypted)
}

private fun String.isTarName(): Boolean = lowercase(Locale.ROOT).let {
  it.endsWith(".tgz") || it.endsWith(".tbz") || it.endsWith(".tbz2") || it.endsWith(".txz") ||
    it.endsWith(".tzst") || ".tar." in it
}

private fun ArchiveException.problem(): ArchiveProblem {
  val reason = message.orEmpty().lowercase(Locale.ROOT)
  return when {
    "passphrase" in reason || "password" in reason || "encrypt" in reason || "crypto codec" in reason -> ArchiveProblem.Locked
    else -> ArchiveProblem.Broken
  }
}
