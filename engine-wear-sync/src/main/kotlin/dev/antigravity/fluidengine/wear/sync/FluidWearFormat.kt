package dev.antigravity.fluidengine.wear.sync

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException

/**
 * I byte che viaggiano fra il telefono e l'orologio: un involucro piccolo e versionato per lo stato, l'evento e la
 * ricevuta.
 *
 * Com'è fatto: le tre lettere `FWS`, un byte di versione ([Version]), un byte di genere, poi i campi uno dopo l'altro,
 * ciascuno con un'etichetta (un byte), la lunghezza (quattro byte) e il valore. I testi sono UTF-8, i numeri big-endian.
 *
 * Le due promesse del formato, che una versione futura deve mantenere:
 * - **un campo che chi legge non conosce si salta**: un'app più nuova può aggiungere campi e una più vecchia la legge
 *   ancora;
 * - **l'etichetta 1 di un evento è sempre il suo id**, in ogni versione. Così un telefono vecchio che riceve un evento di
 *   una versione che non sa leggere ([FluidWearDecoded.Unsupported]) può ancora rispondere «Non registrato» con l'id
 *   giusto, invece di lasciare il polso «In coda» per sempre.
 *
 * Una versione diversa da [Version] non si interpreta: si riporta come [FluidWearDecoded.Unsupported].
 */
object FluidWearFormat {
  /** La versione che questo engine scrive e legge. */
  const val Version: Int = 1

  private val Magic = byteArrayOf('F'.code.toByte(), 'W'.code.toByte(), 'S'.code.toByte())
  private const val KindStatus = 1
  private const val KindEvent = 2
  private const val KindReceipt = 3

  // Le etichette: una volta usate, non si cambiano piu'.
  private const val TagStatusKey = 1
  private const val TagStatusTitle = 2
  private const val TagStatusValue = 3
  private const val TagStatusDetail = 4
  private const val TagStatusProgress = 5
  private const val TagStatusUpdatedAt = 6
  private const val TagStatusStaleAfter = 7
  private const val TagStatusOpenUri = 8

  private const val TagEventId = 1
  private const val TagEventType = 2
  private const val TagEventPayload = 3
  private const val TagEventCreatedAt = 4
  private const val TagEventExpiresAt = 5
  private const val TagEventAttempt = 6

  private const val TagReceiptEventId = 1
  private const val TagReceiptOutcome = 2
  private const val TagReceiptAt = 3

  /** Lo stato in byte. */
  fun encode(status: FluidWearStatus): ByteArray = envelope(KindStatus) {
    text(TagStatusKey, status.key)
    text(TagStatusTitle, status.title)
    text(TagStatusValue, status.value)
    status.detail?.let { text(TagStatusDetail, it) }
    status.progress?.let { float(TagStatusProgress, it) }
    long(TagStatusUpdatedAt, status.updatedAtMillis)
    status.staleAfterMillis?.let { long(TagStatusStaleAfter, it) }
    status.openUri?.let { text(TagStatusOpenUri, it) }
  }

  /**
   * L'evento in byte. [attempt] è il numero della consegna: cambia i byte a ogni nuovo invio, così il Data Layer (che
   * non avvisa per un elemento rimesso identico) lo fa arrivare di nuovo al telefono. Chi legge lo ignora.
   */
  fun encode(event: FluidWearEvent, attempt: Int = 0): ByteArray = envelope(KindEvent) {
    text(TagEventId, event.id)
    text(TagEventType, event.type)
    text(TagEventPayload, event.payload)
    long(TagEventCreatedAt, event.createdAtMillis)
    event.expiresAtMillis?.let { long(TagEventExpiresAt, it) }
    if (attempt != 0) int(TagEventAttempt, attempt)
  }

  /** La ricevuta in byte. */
  fun encode(receipt: FluidWearReceipt): ByteArray = envelope(KindReceipt) {
    text(TagReceiptEventId, receipt.eventId)
    int(TagReceiptOutcome, outcomeCode(receipt.outcome))
    long(TagReceiptAt, receipt.atMillis)
  }

  /** Legge un messaggio. Non lancia mai: un messaggio rotto torna come [FluidWearDecoded.Unreadable]. */
  fun decode(bytes: ByteArray): FluidWearDecoded {
    if (bytes.size < Magic.size + 2) return FluidWearDecoded.Unreadable("troppo corto")
    for (i in Magic.indices) {
      if (bytes[i] != Magic[i]) return FluidWearDecoded.Unreadable("non è un messaggio FWS")
    }
    val version = bytes[Magic.size].toInt() and 0xFF
    val kind = bytes[Magic.size + 1].toInt() and 0xFF
    val fields = try {
      readFields(bytes, Magic.size + 2)
    } catch (e: IOException) {
      return FluidWearDecoded.Unreadable("campi rotti: ${e.message}")
    }
    if (version != Version) {
      // La promessa: l'etichetta 1 di un evento e' il suo id in ogni versione.
      val eventId = if (kind == KindEvent) fields[TagEventId]?.let { String(it, Charsets.UTF_8) } else null
      return FluidWearDecoded.Unsupported(version, eventId?.takeIf { FluidWearPaths.isSafeSegment(it) })
    }
    return try {
      when (kind) {
        KindStatus -> FluidWearDecoded.Status(
          FluidWearStatus(
            key = fields.text(TagStatusKey),
            title = fields.text(TagStatusTitle),
            value = fields.text(TagStatusValue),
            detail = fields.textOrNull(TagStatusDetail),
            progress = fields[TagStatusProgress]?.let { readFloat(it) },
            updatedAtMillis = fields.long(TagStatusUpdatedAt),
            staleAfterMillis = fields[TagStatusStaleAfter]?.let { readLong(it) },
            openUri = fields.textOrNull(TagStatusOpenUri),
          ),
        )
        KindEvent -> FluidWearDecoded.Event(
          FluidWearEvent(
            id = fields.text(TagEventId),
            type = fields.text(TagEventType),
            payload = fields.text(TagEventPayload),
            createdAtMillis = fields.long(TagEventCreatedAt),
            expiresAtMillis = fields[TagEventExpiresAt]?.let { readLong(it) },
          ),
          attempt = fields[TagEventAttempt]?.let { readInt(it) } ?: 0,
        )
        KindReceipt -> FluidWearDecoded.Receipt(
          FluidWearReceipt(
            eventId = fields.text(TagReceiptEventId),
            outcome = outcomeOf(fields[TagReceiptOutcome]?.let { readInt(it) } ?: -1),
            atMillis = fields.long(TagReceiptAt),
          ),
        )
        else -> FluidWearDecoded.Unreadable("genere sconosciuto: $kind")
      }
    } catch (e: IllegalArgumentException) {
      FluidWearDecoded.Unreadable(e.message ?: "campo mancante")
    } catch (e: IOException) {
      FluidWearDecoded.Unreadable(e.message ?: "campo rotto")
    }
  }

  private fun outcomeCode(outcome: FluidWearReceiptOutcome): Int = when (outcome) {
    FluidWearReceiptOutcome.Registered -> 1
    FluidWearReceiptOutcome.Expired -> 2
    FluidWearReceiptOutcome.Refused -> 3
  }

  // Un esito che questa versione non conosce non e' un «Registrato»: meglio dire «Non registrato» che mentire.
  private fun outcomeOf(code: Int): FluidWearReceiptOutcome = when (code) {
    1 -> FluidWearReceiptOutcome.Registered
    2 -> FluidWearReceiptOutcome.Expired
    else -> FluidWearReceiptOutcome.Refused
  }

  private class Writer(val out: DataOutputStream) {
    fun bytes(tag: Int, value: ByteArray) {
      out.writeByte(tag)
      out.writeInt(value.size)
      out.write(value)
    }

    fun text(tag: Int, value: String) = bytes(tag, value.toByteArray(Charsets.UTF_8))
    fun long(tag: Int, value: Long) = bytes(tag, ByteArrayOutputStream(8).also { DataOutputStream(it).writeLong(value) }.toByteArray())
    fun int(tag: Int, value: Int) = bytes(tag, ByteArrayOutputStream(4).also { DataOutputStream(it).writeInt(value) }.toByteArray())
    fun float(tag: Int, value: Float) = bytes(tag, ByteArrayOutputStream(4).also { DataOutputStream(it).writeFloat(value) }.toByteArray())
  }

  private inline fun envelope(kind: Int, body: Writer.() -> Unit): ByteArray {
    val buffer = ByteArrayOutputStream(128)
    val out = DataOutputStream(buffer)
    out.write(Magic)
    out.writeByte(Version)
    out.writeByte(kind)
    Writer(out).body()
    out.flush()
    return buffer.toByteArray()
  }

  private fun readFields(bytes: ByteArray, offset: Int): Map<Int, ByteArray> {
    val input = DataInputStream(ByteArrayInputStream(bytes, offset, bytes.size - offset))
    val fields = HashMap<Int, ByteArray>()
    while (true) {
      val tag = input.read()
      if (tag < 0) break
      val length = try {
        input.readInt()
      } catch (e: EOFException) {
        throw IOException("lunghezza mancante dopo l'etichetta $tag")
      }
      if (length < 0 || length > input.available()) throw IOException("lunghezza $length fuori misura")
      val value = ByteArray(length)
      input.readFully(value)
      // Un'etichetta ripetuta: vale l'ultima, come in protobuf.
      fields[tag] = value
    }
    return fields
  }

  private fun Map<Int, ByteArray>.text(tag: Int): String =
    String(requireNotNull(this[tag]) { "campo $tag mancante" }, Charsets.UTF_8)

  private fun Map<Int, ByteArray>.textOrNull(tag: Int): String? = this[tag]?.let { String(it, Charsets.UTF_8) }

  private fun Map<Int, ByteArray>.long(tag: Int): Long = readLong(requireNotNull(this[tag]) { "campo $tag mancante" })

  private fun readLong(value: ByteArray): Long {
    if (value.size != 8) throw IOException("un numero lungo vuole 8 byte, ne ha ${value.size}")
    return DataInputStream(ByteArrayInputStream(value)).readLong()
  }

  private fun readInt(value: ByteArray): Int {
    if (value.size != 4) throw IOException("un numero vuole 4 byte, ne ha ${value.size}")
    return DataInputStream(ByteArrayInputStream(value)).readInt()
  }

  private fun readFloat(value: ByteArray): Float {
    if (value.size != 4) throw IOException("un decimale vuole 4 byte, ne ha ${value.size}")
    return DataInputStream(ByteArrayInputStream(value)).readFloat()
  }
}

/** Un messaggio letto. */
sealed interface FluidWearDecoded {
  /** Uno stato pubblicato dal telefono. */
  data class Status(val status: FluidWearStatus) : FluidWearDecoded

  /** Un evento del polso, con il numero della consegna ([FluidWearFormat.encode]). */
  data class Event(val event: FluidWearEvent, val attempt: Int = 0) : FluidWearDecoded

  /** Una ricevuta del telefono. */
  data class Receipt(val receipt: FluidWearReceipt) : FluidWearDecoded

  /**
   * Un messaggio FWS di un'altra versione: non si interpreta. Se è un evento, [eventId] è il suo id (la promessa del
   * formato), così il telefono può rispondere «Non registrato».
   */
  data class Unsupported(val version: Int, val eventId: String?) : FluidWearDecoded

  /** Non è un messaggio FWS, o è rotto. */
  data class Unreadable(val reason: String) : FluidWearDecoded
}

/**
 * Gli indirizzi sul Data Layer, tutti sotto [Root]. Un'app li filtra nel manifest del suo `WearableListenerService`
 * (`<data android:scheme="wear" android:host="*" android:pathPrefix="/fluid-wear" />`).
 */
object FluidWearPaths {
  const val Root: String = "/fluid-wear"
  const val StatusPrefix: String = "$Root/status/"
  const val EventPrefix: String = "$Root/event/"
  const val ReceiptPrefix: String = "$Root/receipt/"

  fun status(key: String): String = StatusPrefix + key
  fun event(id: String): String = EventPrefix + id
  fun receipt(eventId: String): String = ReceiptPrefix + eventId

  /** Un pezzo d'indirizzo sicuro: lettere, cifre, `-`, `_`, `.`, fino a 128 caratteri. */
  fun isSafeSegment(value: String): Boolean =
    value.length in 1..128 && value.all { it.isLetterOrDigit() && it.code < 128 || it == '-' || it == '_' || it == '.' }
}
