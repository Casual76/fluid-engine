package dev.pampa.codex.crypto

import java.security.SecureRandom
import org.bouncycastle.crypto.digests.SHA256Digest

/**
 * Le primitive di base su cui il resto del modulo e' costruito. Tutto passa dall'API lightweight di
 * Bouncy Castle: Android porta un provider JCA con lo stesso nome ma vecchio di anni, e registrare il
 * nostro sopra il suo e' il modo classico di rompere le altre librerie del processo.
 */
object Digests {

  private val random = SecureRandom()

  /** Byte casuali da un generatore sicuro. */
  fun randomBytes(count: Int): ByteArray = ByteArray(count).also(random::nextBytes)

  /** SHA-256 del contenuto, senza salt e senza chiave: serve per impronte e identificatori. */
  fun sha256(data: ByteArray): ByteArray {
    val digest = SHA256Digest()
    digest.update(data, 0, data.size)
    return ByteArray(digest.digestSize).also { digest.doFinal(it, 0) }
  }

  /** Esadecimale minuscolo, per i test e i log (mai per i segreti). */
  fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
