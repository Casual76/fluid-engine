package dev.antigravity.fluidengine.account.backup

import java.io.ByteArrayOutputStream

/**
 * Il poco di CBOR (RFC 8949) che serve al pacchetto di backup: mappe con chiavi di testo, interi, testo, byte e
 * liste. **Deterministico** (§ 4.2.1): lunghezze nella forma più corta, chiavi delle mappe in ordine; stessi dati,
 * stessi byte, così «è cambiato qualcosa?» si decide con un hash. Niente libreria per così poco.
 */
internal object PampaCbor {

  fun encode(value: Any?): ByteArray = ByteArrayOutputStream().also { write(it, value) }.toByteArray()

  fun decode(bytes: ByteArray): Any? {
    val r = Reader(bytes)
    val v = r.read()
    require(r.pos == bytes.size) { "CBOR con byte in più" }
    return v
  }

  private fun head(out: ByteArrayOutputStream, major: Int, n: Long) {
    val m = major shl 5
    when {
      n < 24 -> out.write(m or n.toInt())
      n < 0x100 -> { out.write(m or 24); out.write(n.toInt()) }
      n < 0x10000 -> { out.write(m or 25); for (s in 8 downTo 0 step 8) out.write((n shr s).toInt() and 0xff) }
      n < 0x100000000L -> { out.write(m or 26); for (s in 24 downTo 0 step 8) out.write((n shr s).toInt() and 0xff) }
      else -> { out.write(m or 27); for (s in 56 downTo 0 step 8) out.write((n shr s).toInt() and 0xff) }
    }
  }

  private fun write(out: ByteArrayOutputStream, v: Any?) {
    when (v) {
      null -> out.write(0xf6)
      is Boolean -> out.write(if (v) 0xf5 else 0xf4)
      is Int -> write(out, v.toLong())
      is Long -> if (v >= 0) head(out, 0, v) else head(out, 1, -1 - v)
      is String -> v.toByteArray(Charsets.UTF_8).let { head(out, 3, it.size.toLong()); out.write(it) }
      is ByteArray -> { head(out, 2, v.size.toLong()); out.write(v) }
      is List<*> -> { head(out, 4, v.size.toLong()); v.forEach { write(out, it) } }
      is Map<*, *> -> {
        // Ordine deterministico: per lunghezza della chiave codificata, poi byte per byte (RFC 8949 § 4.2.1).
        val entries = v.entries.map { (k, value) -> encode(k as String) to value }
          .sortedWith { a, b -> compareBytes(a.first, b.first) }
        head(out, 5, entries.size.toLong())
        for ((k, value) in entries) { out.write(k); write(out, value) }
      }
      else -> throw IllegalArgumentException("tipo non supportato in CBOR: ${v.javaClass.simpleName}")
    }
  }

  private fun compareBytes(a: ByteArray, b: ByteArray): Int {
    if (a.size != b.size) return a.size - b.size
    for (i in a.indices) {
      val d = (a[i].toInt() and 0xff) - (b[i].toInt() and 0xff)
      if (d != 0) return d
    }
    return 0
  }

  private class Reader(val b: ByteArray) {
    var pos = 0

    private fun byte(): Int {
      require(pos < b.size) { "CBOR troncato" }
      return b[pos++].toInt() and 0xff
    }

    private fun length(info: Int): Long = when {
      info < 24 -> info.toLong()
      info == 24 -> byte().toLong()
      info == 25 -> (byte().toLong() shl 8) or byte().toLong()
      info == 26 -> (0 until 4).fold(0L) { acc, _ -> (acc shl 8) or byte().toLong() }
      info == 27 -> (0 until 8).fold(0L) { acc, _ -> (acc shl 8) or byte().toLong() }
      else -> throw IllegalArgumentException("CBOR: lunghezza indefinita non ammessa")
    }

    private fun bytes(n: Long): ByteArray {
      require(n in 0..(b.size - pos).toLong()) { "CBOR troncato" }
      return b.copyOfRange(pos, pos + n.toInt()).also { pos += n.toInt() }
    }

    fun read(): Any? {
      val initial = byte()
      val major = initial shr 5
      val info = initial and 31
      return when (major) {
        0 -> length(info)
        1 -> -1 - length(info)
        2 -> bytes(length(info))
        3 -> bytes(length(info)).toString(Charsets.UTF_8)
        4 -> (0 until length(info)).map { read() }
        5 -> (0 until length(info)).associate { (read() as? String ?: throw IllegalArgumentException("CBOR: chiave non testo")) to read() }
        7 -> when (info) {
          20 -> false
          21 -> true
          22 -> null
          else -> throw IllegalArgumentException("CBOR: valore semplice non ammesso")
        }
        else -> throw IllegalArgumentException("CBOR: tipo $major non ammesso")
      }
    }
  }
}
