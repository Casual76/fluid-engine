package dev.antigravity.fluidengine.sync

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException

/** Un documento, un registro o una coda che non si riesce a leggere: dati rovinati, o scritti da un engine piu' nuovo. */
class SyncFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * La codifica di un [SyncDocument] in byte: e' quello che il [SyncSealer] sigilla e che viaggia verso il relay.
 *
 * **Deterministica.** Lo stesso documento da' sempre gli stessi byte (campi in ordine di nome, versioni in conflitto in
 * ordine di stamp): due dispositivi che arrivano allo stesso stato producono lo stesso testo in chiaro, e un invio ripetuto
 * e' identico al primo. **Versionata:** il primo byte dice la versione del formato; un engine vecchio che trova un formato
 * piu' nuovo risponde [SyncFormatException] e non tocca l'elemento, invece di riscriverlo male.
 */
object SyncDocumentCodec {
  /** La versione del formato che questo engine scrive. */
  const val FormatVersion: Int = 1

  private const val Magic = 0x53 // 'S'

  /** I byte di [document]. */
  fun encode(document: SyncDocument): ByteArray = write { out -> writeDocument(out, document) }

  /** Il documento di [bytes]; [SyncFormatException] se i byte non sono un documento di questo formato. */
  fun decode(bytes: ByteArray): SyncDocument = read(bytes) { input -> readDocument(input) }

  internal fun writeDocument(out: DataOutputStream, document: SyncDocument) {
    out.writeByte(Magic)
    out.writeByte(FormatVersion)
    val deleted = document.deleted
    out.writeBoolean(deleted != null)
    if (deleted != null) {
      writeHlc(out, deleted)
      return
    }
    out.writeInt(document.fields.size)
    for ((name, field) in document.fields) {
      writeString(out, name)
      writeHlc(out, field.stamp)
      writeValue(out, field.value)
      val alternatives = field.alternatives.sortedBy { it.stamp }
      out.writeInt(alternatives.size)
      for (alt in alternatives) {
        writeHlc(out, alt.stamp)
        writeValue(out, alt.value)
      }
    }
  }

  internal fun readDocument(input: DataInputStream): SyncDocument {
    val magic = input.readUnsignedByte()
    if (magic != Magic) throw SyncFormatException("non e' un documento (primo byte $magic)")
    val version = input.readUnsignedByte()
    if (version != FormatVersion) throw SyncFormatException("formato $version: questo engine legge il $FormatVersion")
    if (input.readBoolean()) return SyncDocument.tombstone(readHlc(input))
    val count = input.readInt()
    if (count < 0 || count > MaxCount) throw SyncFormatException("campi: $count")
    val fields = LinkedHashMap<String, SyncField>(count)
    repeat(count) {
      val name = readString(input)
      val stamp = readHlc(input)
      val value = readValue(input)
      val alts = input.readInt()
      if (alts < 0 || alts > MaxCount) throw SyncFormatException("versioni: $alts")
      val list = List(alts) { SyncAlternative(readHlc(input), readValue(input)) }
      fields[name] = SyncField(value, stamp, list)
    }
    return SyncDocument(fields)
  }

  internal fun writeValue(out: DataOutputStream, value: SyncValue?) {
    when (value) {
      null -> out.writeByte(0)
      is SyncValue.Text -> { out.writeByte(1); writeString(out, value.text) }
      is SyncValue.LongText -> { out.writeByte(2); writeString(out, value.text) }
      is SyncValue.Integer -> { out.writeByte(3); out.writeLong(value.value) }
      is SyncValue.Real -> { out.writeByte(4); out.writeLong(value.value.toRawBits()) }
      is SyncValue.Bool -> { out.writeByte(5); out.writeBoolean(value.value) }
      is SyncValue.Bytes -> { out.writeByte(6); writeBytes(out, value.raw) }
    }
  }

  internal fun readValue(input: DataInputStream): SyncValue? = when (val tag = input.readUnsignedByte()) {
    0 -> null
    1 -> SyncValue.Text(readString(input))
    2 -> SyncValue.LongText(readString(input))
    3 -> SyncValue.Integer(input.readLong())
    4 -> SyncValue.Real(Double.fromBits(input.readLong()))
    5 -> SyncValue.Bool(input.readBoolean())
    6 -> SyncValue.Bytes(readBytes(input))
    else -> throw SyncFormatException("tipo di valore sconosciuto: $tag")
  }

  internal fun writeHlc(out: DataOutputStream, hlc: SyncHlc) {
    out.writeLong(hlc.millis)
    out.writeInt(hlc.counter)
    writeString(out, hlc.node)
  }

  internal fun readHlc(input: DataInputStream): SyncHlc {
    val millis = input.readLong()
    val counter = input.readInt()
    val node = readString(input)
    if (millis < 0 || counter < 0) throw SyncFormatException("stamp non valido")
    return SyncHlc(millis, counter, node)
  }

  internal fun writeString(out: DataOutputStream, text: String) = writeBytes(out, text.toByteArray(Charsets.UTF_8))

  internal fun readString(input: DataInputStream): String = String(readBytes(input), Charsets.UTF_8)

  internal fun writeBytes(out: DataOutputStream, bytes: ByteArray) {
    out.writeInt(bytes.size)
    out.write(bytes)
  }

  internal fun readBytes(input: DataInputStream): ByteArray {
    val size = input.readInt()
    if (size < 0 || size > MaxBytes) throw SyncFormatException("lunghezza non valida: $size")
    val bytes = ByteArray(size)
    input.readFully(bytes)
    return bytes
  }

  internal inline fun write(block: (DataOutputStream) -> Unit): ByteArray {
    val buffer = ByteArrayOutputStream(256)
    DataOutputStream(buffer).use(block)
    return buffer.toByteArray()
  }

  internal inline fun <T> read(bytes: ByteArray, block: (DataInputStream) -> T): T = try {
    DataInputStream(ByteArrayInputStream(bytes)).use(block)
  } catch (e: SyncFormatException) {
    throw e
  } catch (e: EOFException) {
    throw SyncFormatException("dati troncati", e)
  } catch (e: IllegalArgumentException) {
    throw SyncFormatException("dati non validi: ${e.message}", e)
  }

  /** Un tetto contro i dati rovinati: piu' campi o versioni di cosi' non ne scrive nessuno. */
  private const val MaxCount = 1_000_000

  /** 64 MB: oltre, la lunghezza letta e' un numero rovinato, non un testo. */
  private const val MaxBytes = 64 * 1024 * 1024
}
