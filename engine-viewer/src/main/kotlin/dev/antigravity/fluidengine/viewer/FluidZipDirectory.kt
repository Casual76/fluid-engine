package dev.antigravity.fluidengine.viewer

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * I nomi delle voci di uno ZIP, letti dall'indice in fondo al file (la «central directory»), senza decomprimere
 * niente: servono alle firme `FluidViewerSignature.zipEntry` (un DOCX è uno ZIP con `word/document.xml`). Regge lo
 * ZIP64. Un indice rotto, assente o oltre i tetti dà `null`: la firma non si riconosce e decidono estensione e MIME.
 */
internal object FluidZipDirectory {
  /** I tetti: 8 MiB di indice e 65.536 voci bastano a ogni documento; oltre, un archivio enorme. A occhio, 22 novembre 2026. */
  const val MaxDirectoryBytes: Long = 8L * 1024 * 1024
  const val MaxEntries: Int = 65_536

  private const val EndSignature = 0x06054b50
  private const val Zip64LocatorSignature = 0x07064b50
  private const val Zip64EndSignature = 0x06064b50
  private const val EntrySignature = 0x02014b50
  private const val EndLength = 22
  private const val MaxCommentLength = 0xFFFF

  /** Vero se i primi byte sono quelli di uno ZIP (una voce, un archivio vuoto, uno ZIP a più parti). */
  fun looksLikeZip(header: ByteArray?): Boolean {
    if (header == null || header.size < 4 || header[0] != 'P'.code.toByte() || header[1] != 'K'.code.toByte()) return false
    val a = header[2].toInt()
    val b = header[3].toInt()
    return a == 3 && b == 4 || a == 5 && b == 6 || a == 7 && b == 8
  }

  fun names(channel: FileChannel, reserve: (Long) -> Boolean = { true }): Set<String>? = try {
    read(channel, reserve)
  } catch (cancelled: kotlinx.coroutines.CancellationException) {
    throw cancelled
  } catch (_: Exception) {
    null
  }

  private fun read(channel: FileChannel, reserve: (Long) -> Boolean): Set<String>? {
    val size = channel.size()
    if (size < EndLength) return null
    val tailLength = minOf(size, (EndLength + MaxCommentLength).toLong()).toInt()
    val tail = readAt(channel, size - tailLength, tailLength) ?: return null
    var end = -1
    for (i in tailLength - EndLength downTo 0) {
      if (tail.getInt(i) == EndSignature) {
        end = i
        break
      }
    }
    if (end < 0) return null
    var entries = (tail.getShort(end + 10).toInt() and 0xFFFF).toLong()
    var directorySize = tail.getInt(end + 12).toLong() and 0xFFFFFFFFL
    var directoryOffset = tail.getInt(end + 16).toLong() and 0xFFFFFFFFL
    if (entries == 0xFFFFL || directorySize == 0xFFFFFFFFL || directoryOffset == 0xFFFFFFFFL) {
      // ZIP64: il localizzatore sta 20 byte prima della fine, e punta al record vero.
      val locatorAt = size - tailLength + end - 20
      if (locatorAt < 0) return null
      val locator = readAt(channel, locatorAt, 20) ?: return null
      if (locator.getInt(0) != Zip64LocatorSignature) return null
      val recordAt = locator.getLong(8)
      val record = readAt(channel, recordAt, 56) ?: return null
      if (record.getInt(0) != Zip64EndSignature) return null
      entries = record.getLong(32)
      directorySize = record.getLong(40)
      directoryOffset = record.getLong(48)
    }
    if (entries < 0 || entries > MaxEntries || directorySize < 0 || directorySize > MaxDirectoryBytes) return null
    if (directoryOffset < 0 || directoryOffset + directorySize > size) return null
    // Directory originale, nomi decodificati e nodi del set restano vivi insieme.
    if (!reserve(directorySize * 4 + entries * 96)) return null
    val directory = readAt(channel, directoryOffset, directorySize.toInt()) ?: return null
    val names = LinkedHashSet<String>()
    var at = 0
    while (at + 46 <= directory.limit() && names.size < entries) {
      if (directory.getInt(at) != EntrySignature) return null
      val flags = directory.getShort(at + 8).toInt() and 0xFFFF
      val nameLength = directory.getShort(at + 28).toInt() and 0xFFFF
      val extraLength = directory.getShort(at + 30).toInt() and 0xFFFF
      val commentLength = directory.getShort(at + 32).toInt() and 0xFFFF
      if (at + 46 + nameLength > directory.limit()) return null
      val raw = ByteArray(nameLength)
      directory.position(at + 46)
      directory.get(raw)
      // Il bit 11 dice UTF-8; senza, CP437. I nomi che le firme cercano sono ASCII, uguali nelle due letture.
      names += String(raw, if (flags and 0x800 != 0) Charsets.UTF_8 else Charsets.ISO_8859_1).replace('\\', '/')
      at += 46 + nameLength + extraLength + commentLength
    }
    return names
  }

  private fun readAt(channel: FileChannel, position: Long, length: Int): ByteBuffer? {
    if (position < 0 || length < 0) return null
    val buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN)
    var at = position
    while (buffer.hasRemaining()) {
      val n = channel.read(buffer, at)
      if (n < 0) return null
      at += n
    }
    buffer.flip()
    return buffer
  }
}
