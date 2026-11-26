package dev.antigravity.fluidengine.people.crypto

import dev.antigravity.fluidengine.account.crypto.PampaCryptoException
import java.security.SecureRandom
import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.hpke.HPKE
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/**
 * Una chiave Ed25519: la radice della persona o la chiave di lavoro che firma (`b\chiavi.md` § 12). È
 * `IdentityKeys` di Codex, con Bouncy Castle e la sola API leggera. La privata non esce dal modulo: chi la custodisce la
 * chiude subito nella sua classe ([dev.antigravity.fluidengine.people.PersonKeys]).
 */
class Ed25519Key private constructor(private val private: Ed25519PrivateKeyParameters) {
  /** La pubblica, 32 byte. */
  val publicKey: ByteArray = private.generatePublicKey().encoded

  /** La firma di [message], 64 byte (RFC 8032, deterministica). */
  fun sign(message: ByteArray): ByteArray {
    val signer = Ed25519Signer()
    signer.init(true, private)
    signer.update(message, 0, message.size)
    return signer.generateSignature()
  }

  internal fun privateBytes(): ByteArray = private.encoded

  companion object {
    const val KEY_BYTES = 32
    const val SIGNATURE_BYTES = 64

    /** La chiave dei 32 byte [bytes] (il seme RFC 8032). */
    fun fromPrivate(bytes: ByteArray): Ed25519Key {
      require(bytes.size == KEY_BYTES) { "chiave Ed25519 non valida" }
      return Ed25519Key(Ed25519PrivateKeyParameters(bytes, 0))
    }

    fun random(): Ed25519Key = Ed25519Key(Ed25519PrivateKeyParameters(SecureRandom()))

    /** `false` invece di un'eccezione: una firma sbagliata è un caso normale, non un errore. */
    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
      if (publicKey.size != KEY_BYTES || signature.size != SIGNATURE_BYTES) return false
      return try {
        val signer = Ed25519Signer()
        signer.init(false, Ed25519PublicKeyParameters(publicKey, 0))
        signer.update(message, 0, message.size)
        signer.verifySignature(signature)
      } catch (_: IllegalArgumentException) {
        false
      }
    }
  }
}

/** Una chiave X25519: la chiave di lavoro che cifra (CDXB, HPKE) e da cui nasce la chiave di coppia. */
class X25519Key private constructor(private val private: X25519PrivateKeyParameters) {
  val publicKey: ByteArray = private.generatePublicKey().encoded

  /**
   * Il segreto in comune con [peerPublic] (RFC 7748). Non si usa mai così: passa sempre da HKDF, perché un accordo
   * Diffie-Hellman non ha la distribuzione di una chiave.
   */
  fun agree(peerPublic: ByteArray): ByteArray {
    require(peerPublic.size == 32) { "pubblica X25519 non valida" }
    val agreement = X25519Agreement()
    agreement.init(private)
    val shared = ByteArray(agreement.agreementSize)
    try {
      agreement.calculateAgreement(X25519PublicKeyParameters(peerPublic, 0), shared, 0)
    } catch (e: IllegalStateException) {
      // R2 · Una pubblica di ordine piccolo dà un segreto tutto zero, e Bouncy Castle lo rifiuta: è un rifiuto pulito.
      throw PeopleDetected("chiave di cifratura debole")
    }
    return shared
  }

  internal fun privateBytes(): ByteArray = private.encoded

  companion object {
    fun fromPrivate(bytes: ByteArray): X25519Key {
      require(bytes.size == 32) { "chiave X25519 non valida" }
      return X25519Key(X25519PrivateKeyParameters(bytes, 0))
    }

    fun random(): X25519Key = X25519Key(X25519PrivateKeyParameters(SecureRandom()))

    private val PROBE = X25519PrivateKeyParameters(ByteArray(32) { 0x5a }, 0)

    /**
     * R2 · La pubblica è di ordine piccolo (tutta zero, `1`, i punti d'ordine 8, e le loro scritture non canoniche)?
     * Con lei ogni accordo dà un segreto tutto zero, noto a chiunque. Si prova un accordo con una privata qualunque:
     * la privata «schiacciata» è multipla di 8, quindi il segreto è zero **solo** per quei punti.
     */
    internal fun isWeak(publicKey: ByteArray): Boolean {
      if (publicKey.size != 32) return true
      val agreement = X25519Agreement()
      agreement.init(PROBE)
      return try {
        agreement.calculateAgreement(X25519PublicKeyParameters(publicKey, 0), ByteArray(agreement.agreementSize), 0)
        false
      } catch (_: IllegalStateException) {
        true
      }
    }
  }
}

/**
 * HPKE «base» (RFC 9180) con DHKEM(X25519, HKDF-SHA256), HKDF-SHA256 e AES-256-GCM: la suite del contratto (`e2e.json`
 * → `hpke`), la stessa di `PampaHpke` della custodia. Qui ce n'è una copia piccola perché `engine-people` lo usa anche
 * Pampa Mail, che non porta `engine-account-host`; le prove aprono i sigilli fatti dall'HPKE del server, come
 * quelle di `PampaHpke`. Il sigillo è
 * `enc (32 byte) || testo cifrato con tag`, come nel telefono finto del server.
 */
internal object PeopleHpke {
  private const val ENC_BYTES = 32

  private fun suite() = HPKE(HPKE.mode_base, HPKE.kem_X25519_SHA256, HPKE.kdf_HKDF_SHA256, HPKE.aead_AES_GCM256)

  fun seal(recipient: ByteArray, info: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray {
    require(recipient.size == 32) { "la pubblica X25519 è di 32 byte" }
    val hpke = suite()
    val pk = runCatching { hpke.deserializePublicKey(recipient) }.getOrElse { throw PampaCryptoException("pubblica illeggibile", it) }
    // R2 · Verso una pubblica di ordine piccolo l'accordo fallisce (IllegalStateException): un rifiuto, non un guasto.
    val ctx = try {
      hpke.setupBaseS(pk, info)
    } catch (e: IllegalStateException) {
      throw PeopleDetected("chiave di cifratura debole")
    }
    return ctx.encapsulation + ctx.seal(aad, plaintext)
  }

  fun open(recipient: X25519Key, info: ByteArray, aad: ByteArray, sealed: ByteArray): ByteArray {
    if (sealed.size <= ENC_BYTES) throw PampaCryptoException("sigillo troppo corto")
    val hpke = suite()
    return try {
      val pair = hpke.deserializePrivateKey(recipient.privateBytes(), recipient.publicKey)
      hpke.setupBaseR(sealed.copyOfRange(0, ENC_BYTES), pair, info).open(aad, sealed.copyOfRange(ENC_BYTES, sealed.size))
    } catch (e: InvalidCipherTextException) {
      throw PampaCryptoException("sigillo non apribile", e)
    } catch (e: IllegalArgumentException) {
      throw PampaCryptoException("sigillo non apribile", e)
    } catch (e: IllegalStateException) {
      throw PampaCryptoException("sigillo non apribile", e)
    }
  }
}

/**
 * Il telefono ha scoperto qualcosa che non torna: una scheda col codice di un altro, una busta spostata, un record
 * vecchio rimandato, una chiave non firmata dalla radice. È il `Detected` del banco del server: il telefono non usa
 * la cosa e lo dice ([what] è la frase, senza segreti).
 */
class PeopleDetected(val what: String) : Exception(what)
