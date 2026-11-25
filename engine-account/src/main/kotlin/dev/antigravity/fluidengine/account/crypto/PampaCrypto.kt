package dev.antigravity.fluidengine.account.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter

/**
 * Le primitive dell'Account Pampa, tutte con Bouncy Castle (`b\chiavi.md` § 9; progetto, T9): SHA-256, HMAC-SHA256,
 * HKDF-SHA256 e AES-256-GCM. Sono le stesse di `core/crypto` di Codex, già provate sui telefoni veri, e le stesse
 * formule del telefono finto del server (`Pampa-account\test\fake\phone.ts`): le prove di questo modulo e di
 * `engine-account-host` lo verificano coi vettori degli RFC e con quelli usciti dal codice del server.
 *
 * Niente hash lenti: ogni segreto che entra qui è già casuale e lungo (32 byte di chiave, 128 bit del codice di
 * recupero, 32 byte del PRF), e HKDF basta (`b\chiavi.md` § 3.1).
 */
object PampaCrypto {
  const val KEY_BYTES = 32
  const val NONCE_BYTES = 12
  const val TAG_BYTES = 16
  private const val TAG_BITS = TAG_BYTES * 8

  /** Il sale vuoto di HKDF: per RFC 5869 § 2.2 vale come 32 zeri. */
  val EMPTY: ByteArray = ByteArray(0)

  private val random = SecureRandom()

  /** [size] byte casuali dal generatore del sistema. */
  fun random(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

  /** SHA-256 dei pezzi uno dopo l'altro. */
  fun sha256(vararg parts: ByteArray): ByteArray {
    val digest = SHA256Digest()
    for (part in parts) digest.update(part, 0, part.size)
    return ByteArray(digest.digestSize).also { digest.doFinal(it, 0) }
  }

  /** HMAC-SHA256 di [data] con [key] (RFC 2104). */
  fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val mac = HMac(SHA256Digest())
    mac.init(KeyParameter(key))
    mac.update(data, 0, data.size)
    return ByteArray(mac.macSize).also { mac.doFinal(it, 0) }
  }

  /**
   * HKDF-SHA256 (RFC 5869) con un `info` di testo, in UTF-8: è così che sono scritte tutte le etichette del contratto
   * (`PampaLabels`). Il sale vuoto vale come 32 zeri, come nel server (WebCrypto).
   */
  fun hkdf(ikm: ByteArray, salt: ByteArray = EMPTY, info: String, length: Int = KEY_BYTES): ByteArray =
    hkdfRaw(ikm, salt, info.toByteArray(Charsets.UTF_8), length)

  /** HKDF-SHA256 con `info` già in byte: serve ai vettori di RFC 5869. */
  fun hkdfRaw(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
    require(length in 1..255 * 32) { "lunghezza di HKDF fuori intervallo: $length" }
    val generator = HKDFBytesGenerator(SHA256Digest())
    generator.init(HKDFParameters(ikm, salt.takeIf { it.isNotEmpty() }, info))
    return ByteArray(length).also { generator.generateBytes(it, 0, length) }
  }

  /**
   * AES-256-GCM: [plaintext] cifrato con [key] e [nonce], legato ad [aad]. Il risultato è il testo cifrato col tag da
   * 16 byte in coda, come in WebCrypto. **Il nonce non si ripete mai con la stessa chiave**: chi chiama ne prende uno
   * casuale da 96 bit a ogni cifratura (`b\chiavi.md` § 9: il limite di circa 2³² cifrature per chiave è lontanissimo).
   */
  fun aesGcmSeal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
    requireSizes(key, nonce)
    val cipher = gcm(true, key, nonce, aad)
    val out = ByteArray(cipher.getOutputSize(plaintext.size))
    var n = cipher.processBytes(plaintext, 0, plaintext.size, out, 0)
    n += cipher.doFinal(out, n)
    return if (n == out.size) out else out.copyOf(n)
  }

  /**
   * Apre ciò che ha chiuso [aesGcmSeal]. Chiave sbagliata, testo toccato o dati associati diversi danno
   * [PampaCryptoException]: sopra questo livello la differenza non esiste, ed è giusto così.
   */
  fun aesGcmOpen(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
    requireSizes(key, nonce)
    if (ciphertext.size < TAG_BYTES) throw PampaCryptoException("testo cifrato più corto del tag")
    val cipher = gcm(false, key, nonce, aad)
    val out = ByteArray(cipher.getOutputSize(ciphertext.size))
    return try {
      var n = cipher.processBytes(ciphertext, 0, ciphertext.size, out, 0)
      n += cipher.doFinal(out, n)
      if (n == out.size) out else out.copyOf(n)
    } catch (e: InvalidCipherTextException) {
      throw PampaCryptoException("autenticazione fallita", e)
    }
  }

  /** Confronto a tempo costante: per i tag, i `check`, i `link` e i `mac`. */
  fun equal(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

  private fun gcm(forEncryption: Boolean, key: ByteArray, nonce: ByteArray, aad: ByteArray) =
    GCMBlockCipher.newInstance(AESEngine.newInstance()).apply {
      init(forEncryption, AEADParameters(KeyParameter(key), TAG_BITS, nonce, aad))
    }

  private fun requireSizes(key: ByteArray, nonce: ByteArray) {
    require(key.size == KEY_BYTES) { "la chiave deve essere di $KEY_BYTES byte, era ${key.size}" }
    require(nonce.size == NONCE_BYTES) { "il nonce deve essere di $NONCE_BYTES byte, era ${nonce.size}" }
  }
}

/** Una busta che non si apre: chiave sbagliata, dati toccati, o messa al posto di un'altra (dati associati diversi). */
class PampaCryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Base64url senza «=», il formato dei byte dentro i JSON del server (`b\api.md` § 1). La lettura accetta anche il
 * riempimento, e rifiuta tutto ciò che non è base64url.
 */
object PampaBase64 {
  private val encoder = Base64.getUrlEncoder().withoutPadding()
  private val decoder = Base64.getUrlDecoder()

  fun encode(bytes: ByteArray): String = encoder.encodeToString(bytes)

  /** I byte di [text]; [IllegalArgumentException] se non è base64url. */
  fun decode(text: String): ByteArray {
    require(text.all { it.isLetterOrDigit() && it.code < 128 || it == '-' || it == '_' || it == '=' }) { "non è base64url" }
    return decoder.decode(text.trimEnd('='))
  }
}
