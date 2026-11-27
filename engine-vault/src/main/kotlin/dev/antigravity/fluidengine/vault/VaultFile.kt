package dev.antigravity.fluidengine.vault

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Un file cifrato di una sezione: un documento, una foto, un referto. Si scrive e si legge **a pezzi** da 64 KiB, come
 * lo «streaming AEAD» di Tink (AES-GCM-HKDF a segmenti, lo schema STREAM di Hoang, Reyhanitabar, Rogaway e Vizár,
 * 2015): un file grande non entra mai tutto in memoria, e un pezzo tolto, spostato o aggiunto si vede.
 *
 * Il formato: un'intestazione in chiaro (`"FVS1"`, la grandezza dei pezzi, un sale di 16 byte, un prefisso di 7) e poi
 * i pezzi, ognuno AES-256-GCM con la chiave del file (HKDF dalla chiave della sezione, col sale e col nome del file) e
 * il nonce `prefisso ‖ numero del pezzo ‖ ultimo?`. L'intestazione è nei dati associati di ogni pezzo; il nome del file
 * nella chiave, così un file rinominato a mano non si apre.
 *
 * La scrittura va in un file accanto, sul disco, e poi lo sostituisce con un solo scambio: un'app che muore a metà, o
 * un telefono che si spegne, lascia il file di prima.
 */
class VaultFile internal constructor(
  private val section: VaultSection,
  /** Il nome del file nella sezione. */
  val name: String,
) {
  init {
    require(name.isNotEmpty() && name.length <= 128 && name.all { it.isLetterOrDigit() || it in "._-" } && !name.startsWith(".")) {
      "nome di file non valido: «$name»"
    }
  }

  private val mutex = Mutex()

  /** Dove sta il file cifrato sul disco (fuori dal backup). */
  val file: File get() = File(File(section.dir, "files"), "$name.fvs")

  /** Vero se il file c'è. */
  val exists: Boolean get() = file.exists()

  /** Scrive [bytes] nel file, al posto di quello che c'era. */
  suspend fun write(bytes: ByteArray) = write { it.write(bytes) }

  /**
   * Scrive il file a pezzi: [block] riceve un `OutputStream` che cifra mentre scrive. Il file nuovo prende il posto del
   * vecchio solo quando [block] finisce senza errori, dopo essere stato messo sul disco: anche dopo uno spegnimento brusco
   * resta il file di prima o quello nuovo intero. Due scritture dello stesso file vanno in fila.
   */
  suspend fun write(block: (OutputStream) -> Unit) {
    // R2 · In fila: due scritture insieme si pestavano sullo stesso file accanto (contenuti mescolati, o lo scambio che
    // non trovava più il file perché l'altra l'aveva già tolto).
    mutex.withLock {
      section.withKey { key ->
        val target = file
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, ".${target.name}.tmp")
        try {
          temp.outputStream().use { stream ->
            val buffered = stream.buffered()
            SegmentedOutput(buffered, key, name.toByteArray(Charsets.UTF_8)).use(block)
            buffered.flush()
            // R2 · Sul disco prima dello scambio, come `atomicWrite` della scheda e dei valori.
            stream.fd.sync()
          }
          VaultSection.moveIntoPlace(temp, target)
        } finally {
          temp.delete()
        }
      }
    }
  }

  /** Copia [input] nel file, cifrandolo a pezzi (per importare un file grande senza tenerlo in memoria). */
  suspend fun importFrom(input: InputStream) = write { out -> input.copyTo(out) }

  /** Tutto il file, decifrato. Per un file grande, [read] con il blocco. */
  suspend fun read(): ByteArray = read { input -> input.readBytes() }

  /**
   * Legge il file a pezzi: [block] riceve un `InputStream` che decifra mentre legge, e che vale solo dentro il blocco.
   * Un pezzo toccato o mancante fa lanciare [VaultCorruptStreamException] alla lettura.
   *
   * @throws java.io.FileNotFoundException se il file non c'è.
   */
  suspend fun <T> read(block: (InputStream) -> T): T = section.withKey { key ->
    file.inputStream().buffered().use { raw ->
      SegmentedInput(raw, key, name.toByteArray(Charsets.UTF_8)).use(block)
    }
  }

  /** Toglie il file. */
  suspend fun delete() {
    file.delete()
  }

  internal companion object {
    const val SegmentSize = 64 * 1024
    const val SaltBytes = 16
    const val PrefixBytes = 7
    val Magic = byteArrayOf(0x46, 0x56, 0x53, 0x31) // "FVS1"
    const val HeaderBytes = 4 + 4 + SaltBytes + PrefixBytes
    val Info = "fluid-vault/v1/file/".toByteArray(Charsets.UTF_8)

    fun fileKey(sectionKey: ByteArray, salt: ByteArray, fileName: ByteArray): ByteArray =
      VaultCrypto.hkdf(sectionKey, salt, Info + fileName)

    fun nonce(prefix: ByteArray, index: Int, last: Boolean): ByteArray =
      ByteBuffer.allocate(VaultCrypto.NonceBytes).put(prefix).putInt(index).put(if (last) 1 else 0).array()
  }
}

/** Un file cifrato che non torna: un pezzo toccato, tolto o aggiunto, o la chiave di un'altra sezione. */
class VaultCorruptStreamException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Cifra a pezzi su [out]. L'ultimo pezzo si scrive alla chiusura, col segno «ultimo». */
private class SegmentedOutput(
  private val out: OutputStream,
  sectionKey: ByteArray,
  fileName: ByteArray,
  private val segmentSize: Int = VaultFile.SegmentSize,
) : OutputStream() {
  private val prefix = VaultCrypto.randomBytes(VaultFile.PrefixBytes)
  private val header: ByteArray
  private val key: ByteArray
  private val buffer = ByteArray(segmentSize)
  private var filled = 0
  private var index = 0
  private var closed = false
  private val cipher = Cipher.getInstance("AES/GCM/NoPadding")

  init {
    val salt = VaultCrypto.randomBytes(VaultFile.SaltBytes)
    header = ByteBuffer.allocate(VaultFile.HeaderBytes).put(VaultFile.Magic).putInt(segmentSize).put(salt).put(prefix).array()
    key = VaultFile.fileKey(sectionKey, salt, fileName)
    out.write(header)
  }

  override fun write(b: Int) {
    write(byteArrayOf(b.toByte()), 0, 1)
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    check(!closed) { "file già chiuso" }
    var offset = off
    var remaining = len
    while (remaining > 0) {
      // Un pezzo pieno si scrive solo quando arriva altro: così l'ultimo, anche se pieno, porta il segno giusto.
      if (filled == segmentSize) flushSegment(last = false)
      val n = minOf(remaining, segmentSize - filled)
      System.arraycopy(b, offset, buffer, filled, n)
      filled += n
      offset += n
      remaining -= n
    }
  }

  private fun flushSegment(last: Boolean) {
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(VaultCrypto.TagBits, VaultFile.nonce(prefix, index, last)))
    cipher.updateAAD(header)
    out.write(cipher.doFinal(buffer, 0, filled))
    index++
    filled = 0
  }

  override fun flush() = out.flush()

  override fun close() {
    if (closed) return
    flushSegment(last = true)
    closed = true
    key.fill(0)
    buffer.fill(0)
    out.flush()
  }
}

/** Decifra a pezzi da [input]: un pezzo è l'ultimo se dopo di lui il file finisce. */
private class SegmentedInput(
  private val input: InputStream,
  sectionKey: ByteArray,
  fileName: ByteArray,
) : InputStream() {
  private val header = ByteArray(VaultFile.HeaderBytes)
  private val prefix: ByteArray
  private val segmentSize: Int
  private val key: ByteArray
  private val cipher = Cipher.getInstance("AES/GCM/NoPadding")
  private var plain = ByteArray(0)
  private var position = 0
  private var index = 0
  private var finished = false
  private var pending = -1 // un byte già letto del pezzo dopo

  init {
    if (readFully(header, 0, header.size) != header.size) throw VaultCorruptStreamException("intestazione troncata")
    val buffer = ByteBuffer.wrap(header)
    val magic = ByteArray(4).also { buffer.get(it) }
    if (!magic.contentEquals(VaultFile.Magic)) throw VaultCorruptStreamException("non è un file della cassaforte")
    segmentSize = buffer.int
    if (segmentSize !in 16..(16 * 1024 * 1024)) throw VaultCorruptStreamException("pezzi di $segmentSize byte")
    val salt = ByteArray(VaultFile.SaltBytes).also { buffer.get(it) }
    prefix = ByteArray(VaultFile.PrefixBytes).also { buffer.get(it) }
    key = VaultFile.fileKey(sectionKey, salt, fileName)
  }

  override fun read(): Int {
    val one = ByteArray(1)
    return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 0xFF
  }

  override fun read(b: ByteArray, off: Int, len: Int): Int {
    if (len == 0) return 0
    while (position == plain.size) {
      if (finished) return -1
      nextSegment()
    }
    val n = minOf(len, plain.size - position)
    System.arraycopy(plain, position, b, off, n)
    position += n
    return n
  }

  private fun nextSegment() {
    val stored = ByteArray(segmentSize + VaultCrypto.TagBytes)
    var filled = 0
    if (pending >= 0) {
      stored[0] = pending.toByte()
      filled = 1
      pending = -1
    }
    filled += readFully(stored, filled, stored.size - filled)
    val after = input.read()
    val last = after == -1
    if (!last) pending = after
    if (!last && filled != stored.size) throw VaultCorruptStreamException("pezzo $index troncato")
    if (filled < VaultCrypto.TagBytes) throw VaultCorruptStreamException("file troncato")
    plain.fill(0)
    plain = try {
      cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(VaultCrypto.TagBits, VaultFile.nonce(prefix, index, last)))
      cipher.updateAAD(header)
      cipher.doFinal(stored, 0, filled)
    } catch (e: AEADBadTagException) {
      throw VaultCorruptStreamException("pezzo $index non valido", VaultException.Corrupt(cause = e))
    } catch (e: java.security.GeneralSecurityException) {
      throw VaultCorruptStreamException("pezzo $index non valido", VaultException.Corrupt(cause = e))
    }
    position = 0
    index++
    if (last) finished = true
  }

  private fun readFully(target: ByteArray, offset: Int, length: Int): Int {
    var total = 0
    while (total < length) {
      val n = input.read(target, offset + total, length - total)
      if (n < 0) break
      total += n
    }
    return total
  }

  override fun close() {
    key.fill(0)
    plain.fill(0)
    input.close()
  }
}

/** Per i test: cifra [plain] come farebbe [VaultFile] e restituisce i byte del file. */
internal fun vaultFileBytesForTests(sectionKey: ByteArray, fileName: String, plain: ByteArray, segmentSize: Int): ByteArray {
  val out = ByteArrayOutputStream()
  SegmentedOutput(out, sectionKey, fileName.toByteArray(Charsets.UTF_8), segmentSize).use { it.write(plain) }
  return out.toByteArray()
}

/** Per i test: decifra i byte di un file di [VaultFile]. */
internal fun vaultFileReadForTests(sectionKey: ByteArray, fileName: String, stored: ByteArray): ByteArray =
  SegmentedInput(stored.inputStream(), sectionKey, fileName.toByteArray(Charsets.UTF_8)).use { it.readBytes() }
