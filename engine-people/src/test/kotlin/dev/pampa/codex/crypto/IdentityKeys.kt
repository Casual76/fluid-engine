package dev.pampa.codex.crypto

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/**
 * Le due chiavi asimmetriche di un utente Codex.
 *
 * Sono separate per un motivo preciso: **X25519 stabilisce un segreto in comune, Ed25519 dice chi
 * ha scritto una cosa**. Usare la stessa coppia per entrambi e' possibile ma fragile, e qui non
 * c'e' nessun vantaggio a farlo: sono 32 byte in piu' in un file che ne ha gia' 96.
 */
object IdentityKeys {

  const val KEY_SIZE = 32
  const val SIGNATURE_SIZE = 64

  fun randomX25519Private(): ByteArray = Digests.randomBytes(KEY_SIZE)

  fun randomEd25519Private(): ByteArray = Digests.randomBytes(KEY_SIZE)

  fun x25519PublicKey(privateKey: ByteArray): ByteArray {
    require(privateKey.size == KEY_SIZE) { "chiave X25519 non valida" }
    return X25519PrivateKeyParameters(privateKey, 0).generatePublicKey().encoded
  }

  fun ed25519PublicKey(privateKey: ByteArray): ByteArray {
    require(privateKey.size == KEY_SIZE) { "chiave Ed25519 non valida" }
    return Ed25519PrivateKeyParameters(privateKey, 0).generatePublicKey().encoded
  }

  /**
   * Il segreto in comune fra due utenti: la meta' privata di uno, la meta' pubblica dell'altro.
   *
   * Non si usa mai direttamente come chiave. Passa sempre da [Hkdf], perche' il risultato di un
   * accordo Diffie-Hellman non ha la distribuzione uniforme che una chiave deve avere.
   */
  fun agree(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray {
    require(privateKey.size == KEY_SIZE && peerPublicKey.size == KEY_SIZE) { "chiavi non valide" }
    val agreement = X25519Agreement()
    agreement.init(X25519PrivateKeyParameters(privateKey, 0))
    val shared = ByteArray(agreement.agreementSize)
    agreement.calculateAgreement(X25519PublicKeyParameters(peerPublicKey, 0), shared, 0)
    return shared
  }

  fun sign(privateKey: ByteArray, message: ByteArray): ByteArray {
    require(privateKey.size == KEY_SIZE) { "chiave Ed25519 non valida" }
    val signer = Ed25519Signer()
    signer.init(true, Ed25519PrivateKeyParameters(privateKey, 0))
    signer.update(message, 0, message.size)
    return signer.generateSignature()
  }

  /** `false` invece di un'eccezione: una firma sbagliata e' un caso normale, non un errore. */
  fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
    if (publicKey.size != KEY_SIZE || signature.size != SIGNATURE_SIZE) return false
    return try {
      val signer = Ed25519Signer()
      signer.init(false, Ed25519PublicKeyParameters(publicKey, 0))
      signer.update(message, 0, message.size)
      signer.verifySignature(signature)
    } catch (error: IllegalArgumentException) {
      false
    }
  }
}
