package dev.pampa.codex.crypto

import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.modes.GCMModeCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter

/**
 * AES-256-GCM: l'unica cifratura simmetrica di Codex.
 *
 * GCM autentica: se il testo cifrato, il nonce o i dati associati sono stati toccati, la
 * decifratura fallisce invece di restituire spazzatura. Da qui viene anche il modo in cui l'app
 * capisce che una parola d'ordine e' sbagliata: non e' un confronto, e' un tag che non torna.
 *
 * **Il nonce non si ripete mai con la stessa chiave.** Con GCM riusarlo non e' una debolezza
 * teorica: due messaggi con lo stesso nonce rivelano lo XOR dei testi in chiaro e la chiave di
 * autenticazione. Per questo la busta di un messaggio deriva chiave e nonce dall'id del messaggio
 * (unico per costruzione) e i vault ne generano uno casuale a ogni riscrittura.
 */
object Aead {

  const val KEY_SIZE = 32
  const val NONCE_SIZE = 12
  const val TAG_SIZE = 16
  private const val TAG_BITS = TAG_SIZE * 8

  class DecryptionFailed(message: String, cause: Throwable? = null) : Exception(message, cause)

  fun encrypt(
    key: ByteArray,
    nonce: ByteArray,
    plaintext: ByteArray,
    associatedData: ByteArray? = null,
  ): ByteArray {
    requireSizes(key, nonce)
    val cipher = newCipher(forEncryption = true, key = key, nonce = nonce, associatedData = associatedData)
    val output = ByteArray(cipher.getOutputSize(plaintext.size))
    var written = cipher.processBytes(plaintext, 0, plaintext.size, output, 0)
    written += cipher.doFinal(output, written)
    return if (written == output.size) output else output.copyOf(written)
  }

  fun decrypt(
    key: ByteArray,
    nonce: ByteArray,
    ciphertext: ByteArray,
    associatedData: ByteArray? = null,
  ): ByteArray {
    requireSizes(key, nonce)
    if (ciphertext.size < TAG_SIZE) throw DecryptionFailed("testo cifrato piu' corto del tag")
    val cipher = newCipher(forEncryption = false, key = key, nonce = nonce, associatedData = associatedData)
    val output = ByteArray(cipher.getOutputSize(ciphertext.size))
    return try {
      var written = cipher.processBytes(ciphertext, 0, ciphertext.size, output, 0)
      written += cipher.doFinal(output, written)
      if (written == output.size) output else output.copyOf(written)
    } catch (error: InvalidCipherTextException) {
      // Il messaggio non dice mai *perche'*: sopra questo livello la distinzione fra "chiave
      // sbagliata" e "dati manomessi" non esiste, ed e' giusto cosi'.
      throw DecryptionFailed("autenticazione fallita", error)
    }
  }

  /** Un nonce casuale. Per i dati a riposo che vengono riscritti; mai per i messaggi. */
  fun randomNonce(): ByteArray = Digests.randomBytes(NONCE_SIZE)

  private fun newCipher(
    forEncryption: Boolean,
    key: ByteArray,
    nonce: ByteArray,
    associatedData: ByteArray?,
  ): GCMModeCipher = GCMBlockCipher.newInstance(AESEngine.newInstance()).apply {
    init(forEncryption, AEADParameters(KeyParameter(key), TAG_BITS, nonce, associatedData))
  }

  private fun requireSizes(key: ByteArray, nonce: ByteArray) {
    require(key.size == KEY_SIZE) { "la chiave deve essere di $KEY_SIZE byte, era ${key.size}" }
    require(nonce.size == NONCE_SIZE) { "il nonce deve essere di $NONCE_SIZE byte, era ${nonce.size}" }
  }
}
