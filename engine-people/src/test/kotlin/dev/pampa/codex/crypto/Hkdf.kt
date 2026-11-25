package dev.pampa.codex.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters

/**
 * HKDF-SHA256 (RFC 5869): da un segreto lungo a tutte le chiavi che ne discendono.
 *
 * In Codex non si usa mai una chiave "cruda" per due scopi diversi: dalla chiave di chat si deriva
 * la chiave del messaggio, il seme del sigillo, l'emblema. Il campo `info` e' quello che li tiene
 * separati, quindi va scritto per esteso e mai riutilizzato con significati diversi.
 */
object Hkdf {

  fun derive(
    ikm: ByteArray,
    info: String,
    length: Int,
    salt: ByteArray? = null,
  ): ByteArray {
    require(length in 1..8160) { "lunghezza fuori intervallo: $length" }
    val generator = HKDFBytesGenerator(SHA256Digest())
    generator.init(HKDFParameters(ikm, salt, info.toByteArray(Charsets.UTF_8)))
    return ByteArray(length).also { generator.generateBytes(it, 0, length) }
  }

  /** La stessa derivazione con `info` gia' in byte: serve ai vettori di prova di RFC 5869. */
  fun deriveRaw(ikm: ByteArray, info: ByteArray, length: Int, salt: ByteArray?): ByteArray {
    val generator = HKDFBytesGenerator(SHA256Digest())
    generator.init(HKDFParameters(ikm, salt, info))
    return ByteArray(length).also { generator.generateBytes(it, 0, length) }
  }

  /** Un `Long` deterministico da un materiale di chiave: e' cosi' che nasce il seme di un sigillo. */
  fun deriveSeed(ikm: ByteArray, info: String): Long {
    val bytes = derive(ikm, info, 8)
    var seed = 0L
    for (byte in bytes) seed = (seed shl 8) or (byte.toLong() and 0xFF)
    return seed
  }
}
