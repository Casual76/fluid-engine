package dev.antigravity.fluidengine.people.crypto

/**
 * Lettura e scrittura di campi in un array di byte, con la lunghezza davanti: il `Bytes.kt` di Codex
 * (`core/crypto`), portato così com'è perché i formati binari che si tengono (`CDXM`, il saluto della vicinanza)
 * devono restare uguali al byte. Interi in big-endian, stringhe in UTF-8 con la lunghezza da 16 bit davanti.
 */
internal class ByteWriter(initialCapacity: Int = 128) {
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

internal class ByteReader(private val source: ByteArray, private var offset: Int = 0) {

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

/** Un `u32` big-endian, come `u32()` del telefono finto del server. */
internal fun u32Bytes(value: Long): ByteArray = ByteWriter(4).u32(value).build()

/** Il `u32` big-endian che comincia in [offset]. */
internal fun readU32(bytes: ByteArray, offset: Int): Long = ByteReader(bytes, offset).u32()

/**
 * Base32 di Crockford, l'alfabeto del codice Pampa e delle schede da ricopiare: il `Base32.kt` di Codex. Niente I, L, O,
 * U: chi legge non si chiede mai «uno o elle?», e da byte casuali non nasce nessuna parola sgradevole. In lettura le
 * lettere che si confondono tornano cifre.
 *
 * **Non è una cifratura**: ci passano solo cose pubbliche.
 */
object Crockford32 {
  const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

  /** Da byte a testo, senza riempimento: l'ultimo carattere è completato con zeri. */
  fun encode(data: ByteArray): String {
    val out = StringBuilder((data.size * 8 + 4) / 5)
    var buffer = 0L
    var bits = 0
    for (byte in data) {
      buffer = (buffer shl 8) or (byte.toLong() and 0xFF)
      bits += 8
      while (bits >= 5) {
        bits -= 5
        out.append(ALPHABET[((buffer ushr bits) and 0x1F).toInt()])
      }
    }
    if (bits > 0) out.append(ALPHABET[((buffer shl (5 - bits)) and 0x1F).toInt()])
    return out.toString()
  }

  /** Da testo a byte, o `null` se non è base32: spazi, trattini e a capo non contano; I e L valgono 1, O vale 0, U vale V. */
  fun decode(text: String): ByteArray? {
    val out = ArrayList<Byte>(text.length * 5 / 8 + 1)
    var buffer = 0L
    var bits = 0
    for (raw in text) {
      if (raw.isWhitespace() || raw == '-' || raw == '_') continue
      val symbol = when (val character = raw.uppercaseChar()) {
        'I', 'L' -> '1'
        'O' -> '0'
        'U' -> 'V'
        else -> character
      }
      val value = ALPHABET.indexOf(symbol)
      if (value < 0) return null
      buffer = (buffer shl 5) or value.toLong()
      bits += 5
      if (bits >= 8) {
        bits -= 8
        out.add(((buffer ushr bits) and 0xFF).toByte())
      }
    }
    return out.toByteArray()
  }

  /** Lo stesso testo a gruppi, per chi lo deve ricopiare da uno schermo. */
  fun grouped(text: String, group: Int = 5, perLine: Int = 8): String =
    text.chunked(group).chunked(perLine).joinToString("\n") { it.joinToString(" ") }
}
