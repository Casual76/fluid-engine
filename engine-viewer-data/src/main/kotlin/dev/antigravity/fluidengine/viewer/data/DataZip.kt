package dev.antigravity.fluidengine.viewer.data

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.zip.CRC32
import java.util.zip.Inflater

/** Una voce di uno ZIP: dove comincia, come è compressa, quanto è grande, e il suo CRC-32 (R2; -1 se non si sa). */
internal class DataZipEntry(
  val name: String,
  val method: Int,
  val flags: Int,
  val compressedSize: Long,
  val size: Long,
  val localHeader: Long,
  val crc: Long = -1L,
) {
  val encrypted: Boolean get() = flags and 1 != 0
  val directory: Boolean get() = name.endsWith("/")
}

/**
 * F6C.13 · Un lettore ZIP minimo per gli EPUB, su un `FileChannel`: l'indice in fondo al file e una voce alla volta,
 * con l'`Inflater` della piattaforma. Esiste perché `java.util.zip.ZipFile` vuole un percorso, e un `Uri` dell'app ha
 * solo un descrittore; e perché qui valgono i tetti della famiglia:
 * - una voce più grande di [DataProfile.MaxEntryBytes], o compressa oltre [DataProfile.MaxCompressionRatio]:1, non si
 *   estrae (zip bomb);
 * - un nome con «..» o assoluto non esce mai: le voci si cercano per nome, non si scrivono su disco;
 * - una voce cifrata dice di esserlo, e non si legge;
 * - una voce tronca o rovinata (misura o CRC-32 diversi da quelli dell'indice) è un errore, non un capitolo a metà (R2).
 * Non è un archivio: gli archivi sono di `engine-viewer-archive` (libarchive).
 */
internal class DataZip(private val channel: FileChannel, private val onClose: () -> Unit = {}) : Closeable {
  val entries: Map<String, DataZipEntry>

  init {
    entries = readDirectory()
  }

  private fun readFully(position: Long, length: Int): ByteBuffer {
    val buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN)
    var at = position
    while (buffer.hasRemaining()) {
      val n = channel.read(buffer, at)
      if (n <= 0) throw IOException("ZIP troncato")
      at += n
    }
    buffer.flip()
    return buffer
  }

  private fun readDirectory(): Map<String, DataZipEntry> {
    val size = channel.size()
    if (size < 22) throw IOException("non è uno ZIP")
    val tail = minOf(size, 22L + 65_535L).toInt()
    val end = readFully(size - tail, tail)
    var eocd = -1
    for (i in tail - 22 downTo 0) {
      if (end.getInt(i) == 0x06054b50) {
        eocd = i
        break
      }
    }
    if (eocd < 0) throw IOException("ZIP senza indice")
    var count = end.getShort(eocd + 10).toInt() and 0xFFFF
    var dirSize = end.getInt(eocd + 12).toLong() and 0xFFFFFFFFL
    var dirOffset = end.getInt(eocd + 16).toLong() and 0xFFFFFFFFL
    // ZIP64: il localizzatore sta subito prima del record di fine.
    if ((dirOffset == 0xFFFFFFFFL || count == 0xFFFF) && eocd >= 20 && end.getInt(eocd - 20) == 0x07064b50) {
      val z64 = end.getLong(eocd - 20 + 8)
      val rec = readFully(z64, 56)
      if (rec.getInt(0) == 0x06064b50) {
        count = rec.getLong(32).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        dirSize = rec.getLong(40)
        dirOffset = rec.getLong(48)
      }
    }
    if (dirOffset + dirSize > size || dirSize > 64L * 1024 * 1024 || count > 200_000) throw IOException("indice dello ZIP non valido")
    val dir = readFully(dirOffset, dirSize.toInt())
    val out = LinkedHashMap<String, DataZipEntry>()
    var p = 0
    repeat(count) {
      if (p + 46 > dir.limit() || dir.getInt(p) != 0x02014b50) return out
      val flags = dir.getShort(p + 8).toInt() and 0xFFFF
      val method = dir.getShort(p + 10).toInt() and 0xFFFF
      val crc = dir.getInt(p + 16).toLong() and 0xFFFFFFFFL
      var compressed = dir.getInt(p + 20).toLong() and 0xFFFFFFFFL
      var uncompressed = dir.getInt(p + 24).toLong() and 0xFFFFFFFFL
      val nameLength = dir.getShort(p + 28).toInt() and 0xFFFF
      val extraLength = dir.getShort(p + 30).toInt() and 0xFFFF
      val commentLength = dir.getShort(p + 32).toInt() and 0xFFFF
      var local = dir.getInt(p + 42).toLong() and 0xFFFFFFFFL
      if (p + 46 + nameLength + extraLength > dir.limit()) return out
      val nameBytes = ByteArray(nameLength).also { dir.position(p + 46); dir.get(it) }
      val name = if (flags and 0x800 != 0) String(nameBytes, Charsets.UTF_8) else decodeName(nameBytes)
      // Campo extra ZIP64 per le misure e lo scostamento.
      var e = p + 46 + nameLength
      val extraEnd = e + extraLength
      while (e + 4 <= extraEnd) {
        val id = dir.getShort(e).toInt() and 0xFFFF
        val len = dir.getShort(e + 2).toInt() and 0xFFFF
        if (id == 1) {
          var q = e + 4
          if (uncompressed == 0xFFFFFFFFL && q + 8 <= e + 4 + len) { uncompressed = dir.getLong(q); q += 8 }
          if (compressed == 0xFFFFFFFFL && q + 8 <= e + 4 + len) { compressed = dir.getLong(q); q += 8 }
          if (local == 0xFFFFFFFFL && q + 8 <= e + 4 + len) { local = dir.getLong(q) }
        }
        e += 4 + len
      }
      out.putIfAbsent(name.removePrefix("/"), DataZipEntry(name.removePrefix("/"), method, flags, compressed, uncompressed, local, crc))
      p += 46 + nameLength + extraLength + commentLength
    }
    return out
  }

  private fun decodeName(bytes: ByteArray): String {
    val utf8 = String(bytes, Charsets.UTF_8)
    if (Replacement !in utf8) return utf8
    return try {
      String(bytes, charset("IBM437"))
    } catch (_: Exception) {
      String(bytes, Charsets.ISO_8859_1)
    }
  }

  /** La voce [name] (senza «/» in testa), cercata anche ignorando le maiuscole; `null` se non c'è. */
  fun entry(name: String): DataZipEntry? {
    val clean = name.removePrefix("/")
    return entries[clean] ?: entries.values.firstOrNull { it.name.equals(clean, ignoreCase = true) }
  }

  /** I byte della voce [name], o `null` se non c'è. Una voce cifrata, troppo grande o sospetta è un'eccezione. */
  fun read(name: String, limit: Long = DataProfile.MaxEntryBytes): ByteArray? {
    val entry = entry(name) ?: return null
    if (entry.directory) return null
    if (entry.encrypted) throw IOException("la voce $name è cifrata")
    if (entry.size > limit) throw IOException("la voce $name è troppo grande")
    if (entry.compressedSize > 0 && entry.size / entry.compressedSize > DataProfile.MaxCompressionRatio && entry.size > 1_000_000) {
      throw IOException("la voce $name è compressa in modo sospetto")
    }
    val header = readFully(entry.localHeader, 30)
    if (header.getInt(0) != 0x04034b50) throw IOException("voce $name non valida")
    val nameLength = header.getShort(26).toInt() and 0xFFFF
    val extraLength = header.getShort(28).toInt() and 0xFFFF
    val dataStart = entry.localHeader + 30 + nameLength + extraLength
    if (entry.compressedSize > Int.MAX_VALUE) throw IOException("voce $name troppo grande")
    val raw = readFully(dataStart, entry.compressedSize.toInt())
    val bytes = when (entry.method) {
      0 -> ByteArray(raw.remaining()).also { raw.get(it) }
      8 -> {
        val inflater = Inflater(true)
        try {
          val input = ByteArray(raw.remaining()).also { raw.get(it) }
          inflater.setInput(input)
          val out = ByteArrayOutputStream(entry.size.coerceIn(0, 1L shl 20).toInt())
          val chunk = ByteArray(64 * 1024)
          while (!inflater.finished()) {
            val n = inflater.inflate(chunk)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(chunk, 0, n)
            if (out.size() > limit) throw IOException("la voce $name è troppo grande")
          }
          out.toByteArray()
        } finally {
          inflater.end()
        }
      }
      else -> throw IOException("metodo di compressione ${entry.method} non supportato")
    }
    // R2: la misura e il CRC-32 dell'indice, come fa la famiglia Office: una voce tronca o rovinata è un errore come le
    // altre voci illeggibili, non un capitolo a metà o un'immagine sbagliata.
    if (bytes.size.toLong() != entry.size) throw IOException("la voce $name è incompleta")
    if (entry.crc >= 0 && CRC32().apply { update(bytes) }.value != entry.crc) throw IOException("la voce $name è danneggiata")
    return bytes
  }

  private companion object {
    /** U+FFFD: il segno che il decodificatore mette dove i byte non sono UTF-8. */
    val Replacement: Char = Char(0xFFFD)
  }

  override fun close() {
    try {
      channel.close()
    } catch (_: Exception) {
    }
    onClose()
  }
}
