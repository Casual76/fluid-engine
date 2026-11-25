package dev.antigravity.fluidengine.people.crypto

import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import dev.antigravity.fluidengine.account.crypto.PampaCryptoException
import java.io.InputStream
import java.io.OutputStream

/**
 * Foto, voci e file cifrati **a blocchi**: il formato `CDXM` di Codex (`MediaCipher`), tenuto **così com'è**, al byte
 * (`d\server-e-chiavi.md` § 2). La memoria occupata è quella di un blocco da 64 KiB anche con una foto da 20 MB.
 *
 * ```
 * "CDXM" ‖ versione(1) ‖ dimensione del blocco(8) ‖ lunghezza originale(8) ‖ nonce di base(12)
 * poi, per ogni blocco: testo cifrato ‖ tag(16)
 * ```
 *
 * Ogni blocco ha nei dati associati tutta l'intestazione e il suo numero: un file rimescolato, troncato o allungato non
 * si apre. Il nonce di un blocco è quello di base con gli ultimi otto byte in XOR col numero del blocco. Nelle persone
 * la chiave di un file è casuale ([newKey]) e viaggia dentro la busta o il record che lo cita; [keyFor] è la chiave
 * dell'alpha (dalla chiave di una conversazione e dall'id del media), tenuta per le prove contro Codex.
 */
object Cdxm {
  private val MAGIC = "CDXM".toByteArray(Charsets.US_ASCII)
  private const val VERSION = 1
  const val CHUNK = 64 * 1024
  private const val HEADER_SIZE = 4 + 1 + 8 + 8 + PampaCrypto.NONCE_BYTES
  private const val MAX_CHUNK = 4 * 1024 * 1024

  class Malformed(message: String) : Exception(message)

  /** Una chiave nuova per un file. */
  fun newKey(): ByteArray = PampaCrypto.random(32)

  /** La chiave di un media dell'alpha: `HKDF(chiave della chat, "codex-media-v1:<id>")`. */
  fun keyFor(chatKey: ByteArray, mediaId: String): ByteArray = PampaCrypto.hkdf(chatKey, PampaCrypto.EMPTY, "codex-media-v1:$mediaId")

  /** Quanto occuperà un file cifrato: serve a dire «non ci sta» prima di cominciare (25 MB a volta). */
  fun encryptedSize(plainLength: Long): Long {
    val chunks = (plainLength + CHUNK - 1) / CHUNK
    return HEADER_SIZE + plainLength + chunks * PampaCrypto.TAG_BYTES
  }

  fun encrypt(key: ByteArray, input: InputStream, output: OutputStream, plainLength: Long) =
    encryptWith(key, input, output, plainLength, PampaCrypto.random(PampaCrypto.NONCE_BYTES))

  internal fun encryptWith(key: ByteArray, input: InputStream, output: OutputStream, plainLength: Long, nonceBase: ByteArray) {
    require(plainLength >= 0) { "lunghezza negativa" }
    val header = ByteWriter(HEADER_SIZE).bytes(MAGIC).u8(VERSION).i64(CHUNK.toLong()).i64(plainLength).bytes(nonceBase).build()
    output.write(header)
    val buffer = ByteArray(CHUNK)
    var index = 0L
    var written = 0L
    while (written < plainLength) {
      val wanted = minOf(CHUNK.toLong(), plainLength - written).toInt()
      val read = input.readFully(buffer, wanted)
      if (read < wanted) throw Malformed("il file è finito prima di quanto dichiarato")
      output.write(PampaCrypto.aesGcmSeal(key, nonceFor(nonceBase, index), buffer.copyOf(read), associated(header, index)))
      written += read
      index++
    }
    output.flush()
  }

  fun decrypt(key: ByteArray, input: InputStream, output: OutputStream) {
    val header = ByteArray(HEADER_SIZE)
    if (input.readFully(header, HEADER_SIZE) < HEADER_SIZE) throw Malformed("intestazione assente")
    val reader = ByteReader(header)
    try {
      reader.expectMagic(MAGIC)
      if (reader.u8() != VERSION) throw Malformed("versione sconosciuta")
    } catch (_: ByteReader.Malformed) {
      throw Malformed("non è un file CDXM")
    }
    val chunk = reader.i64().toInt()
    val plainLength = reader.i64()
    if (chunk !in 1..MAX_CHUNK || plainLength < 0) throw Malformed("intestazione fuori scala")
    val nonceBase = reader.bytes(PampaCrypto.NONCE_BYTES)
    val buffer = ByteArray(chunk + PampaCrypto.TAG_BYTES)
    var index = 0L
    var produced = 0L
    while (produced < plainLength) {
      val expected = minOf(chunk.toLong(), plainLength - produced).toInt() + PampaCrypto.TAG_BYTES
      val read = input.readFully(buffer, expected)
      if (read < expected) throw Malformed("il file è stato troncato")
      val plain = try {
        PampaCrypto.aesGcmOpen(key, nonceFor(nonceBase, index), buffer.copyOf(read), associated(header, index))
      } catch (_: PampaCryptoException) {
        throw Malformed("il file non si apre")
      }
      output.write(plain)
      produced += plain.size
      index++
    }
    output.flush()
  }

  private fun associated(header: ByteArray, index: Long): ByteArray = ByteWriter(header.size + 8).bytes(header).i64(index).build()

  private fun nonceFor(base: ByteArray, index: Long): ByteArray {
    val nonce = base.copyOf()
    for (byte in 0 until 8) {
      nonce[nonce.size - 1 - byte] = (nonce[nonce.size - 1 - byte].toLong() xor (index ushr (byte * 8))).toByte()
    }
    return nonce
  }

  private fun InputStream.readFully(into: ByteArray, count: Int): Int {
    var done = 0
    while (done < count) {
      val read = read(into, done, count - done)
      if (read < 0) return done
      done += read
    }
    return done
  }
}

/**
 * Le chiavi a epoche di un gruppo o di uno spazio condiviso (`GroupSecret` di Codex; `d\server-e-chiavi.md` § 11):
 * 32 byte casuali per epoca, consegnati a ogni membro con una CDXB. Quando qualcuno esce si apre un'epoca nuova, data
 * solo a chi resta: **chi esce tiene il passato e perde il futuro** (P6). Per questo si tengono tutte le epoche.
 */
class EpochKeyring private constructor(private val keys: java.util.TreeMap<Int, ByteArray>) {
  val current: Int get() = keys.lastKey()
  val currentKey: ByteArray get() = keys.getValue(current)
  val epochs: Set<Int> get() = keys.keys

  fun key(epoch: Int): ByteArray? = keys[epoch]

  /** Un'epoca nuova, con una chiave nuova. */
  fun rotate(): Int {
    val next = current + 1
    keys[next] = PampaCrypto.random(32)
    return next
  }

  /** Le chiavi arrivate in una busta: si aggiungono, non si sostituiscono quelle che si hanno già. */
  fun merge(other: Map<Int, ByteArray>) {
    for ((e, k) in other) keys.putIfAbsent(e, k.copyOf())
  }

  fun snapshot(): Map<Int, ByteArray> = keys.mapValues { it.value.copyOf() }

  companion object {
    fun create(): EpochKeyring = EpochKeyring(java.util.TreeMap(mapOf(1 to PampaCrypto.random(32))))
    fun of(keys: Map<Int, ByteArray>): EpochKeyring {
      require(keys.isNotEmpty()) { "un portachiavi senza epoche" }
      return EpochKeyring(java.util.TreeMap(keys.mapValues { it.value.copyOf() }))
    }
  }
}
