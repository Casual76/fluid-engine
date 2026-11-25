package dev.pampa.codex.crypto

/**
 * Lettura e scrittura di campi in un array di byte, con la lunghezza davanti.
 *
 * Ogni formato di Codex (identita', vault, scheda contatto, busta) e' binario e con i campi in un
 * ordine fisso: un JSON costerebbe il doppio dentro un QR code e non avrebbe confini chiari da
 * firmare. Queste due classi sono l'unico posto dove quei confini vengono decisi.
 */
class ByteWriter(initialCapacity: Int = 128) {
  private var buffer = ByteArray(initialCapacity)
  private var size = 0

  fun bytes(value: ByteArray): ByteWriter = apply {
    ensure(value.size)
    value.copyInto(buffer, size)
    size += value.size
  }

  fun u8(value: Int): ByteWriter = apply {
    require(value in 0..255) { "u8 fuori intervallo: $value" }
    ensure(1)
    buffer[size++] = value.toByte()
  }

  fun u16(value: Int): ByteWriter = apply {
    require(value in 0..65535) { "u16 fuori intervallo: $value" }
    ensure(2)
    buffer[size++] = (value ushr 8).toByte()
    buffer[size++] = value.toByte()
  }

  fun u32(value: Long): ByteWriter = apply {
    require(value in 0..0xFFFFFFFFL) { "u32 fuori intervallo: $value" }
    ensure(4)
    for (shift in 24 downTo 0 step 8) buffer[size++] = (value ushr shift).toByte()
  }

  fun i64(value: Long): ByteWriter = apply {
    ensure(8)
    for (shift in 56 downTo 0 step 8) buffer[size++] = (value ushr shift).toByte()
  }

  /** Una stringa con la lunghezza in testa: al massimo 65535 byte in UTF-8. */
  fun string(value: String): ByteWriter = apply {
    val encoded = value.toByteArray(Charsets.UTF_8)
    u16(encoded.size)
    bytes(encoded)
  }

  fun blob(value: ByteArray): ByteWriter = apply {
    u16(value.size)
    bytes(value)
  }

  fun build(): ByteArray = buffer.copyOf(size)

  private fun ensure(extra: Int) {
    if (size + extra <= buffer.size) return
    var capacity = maxOf(buffer.size * 2, 16)
    while (capacity < size + extra) capacity *= 2
    buffer = buffer.copyOf(capacity)
  }
}

class ByteReader(private val source: ByteArray, private var offset: Int = 0) {

  class Malformed(message: String) : Exception(message)

  val position: Int get() = offset
  val remaining: Int get() = source.size - offset

  fun bytes(count: Int): ByteArray {
    require(count)
    return source.copyOfRange(offset, offset + count).also { offset += count }
  }

  fun u8(): Int {
    require(1)
    return source[offset++].toInt() and 0xFF
  }

  fun u16(): Int {
    require(2)
    return (u8() shl 8) or u8()
  }

  fun u32(): Long {
    require(4)
    var value = 0L
    repeat(4) { value = (value shl 8) or u8().toLong() }
    return value
  }

  fun i64(): Long {
    require(8)
    var value = 0L
    repeat(8) { value = (value shl 8) or u8().toLong() }
    return value
  }

  fun string(): String = String(bytes(u16()), Charsets.UTF_8)

  fun blob(): ByteArray = bytes(u16())

  fun rest(): ByteArray = source.copyOfRange(offset, source.size).also { offset = source.size }

  fun expectMagic(magic: ByteArray) {
    val actual = bytes(magic.size)
    if (!actual.contentEquals(magic)) throw Malformed("formato non riconosciuto")
  }

  private fun require(count: Int) {
    if (count < 0 || offset + count > source.size) throw Malformed("dati troncati")
  }
}
