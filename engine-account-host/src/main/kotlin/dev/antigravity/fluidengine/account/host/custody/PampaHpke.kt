package dev.antigravity.fluidengine.account.host.custody

import dev.antigravity.fluidengine.account.crypto.PampaCryptoException
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.hpke.HPKE
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters

/**
 * Una coppia X25519. La privata sono 32 byte qualunque: X25519 li «schiaccia» da sé (RFC 7748 § 5), così un seme di
 * HKDF diventa direttamente una privata, come nel telefono finto del server (`x25519FromSeed`).
 */
class X25519KeyPair private constructor(private val private: X25519PrivateKeyParameters) {
  /** La pubblica, 32 byte: va sul server (porte) o nel QR (passaggio). */
  val publicKey: ByteArray = private.generatePublicKey().encoded

  internal fun privateBytes(): ByteArray = private.encoded

  internal fun asHpke(hpke: HPKE): AsymmetricCipherKeyPair = hpke.deserializePrivateKey(private.encoded, publicKey)

  companion object {
    /** La coppia che nasce da [seed] (32 byte). */
    fun fromSeed(seed: ByteArray): X25519KeyPair {
      require(seed.size == 32) { "il seme X25519 è di 32 byte, era ${seed.size}" }
      return X25519KeyPair(X25519PrivateKeyParameters(seed, 0))
    }

    /** Una coppia nuova e casuale (l'effimera del passaggio fra telefoni). */
    fun random(): X25519KeyPair = X25519KeyPair(X25519PrivateKeyParameters(java.security.SecureRandom()))
  }
}

/**
 * HPKE «base» (RFC 9180) con DHKEM(X25519, HKDF-SHA256), HKDF-SHA256 e AES-256-GCM: la suite del contratto
 * (`e2e.json`, `hpke`: kem 0x0020, kdf 0x0001, aead 0x0002), quella di Bouncy Castle. Serve alle porte della chiave dei
 * dati e al passaggio fra telefoni (`b\chiavi.md` § 3 e § 4.2). La modalità «auth» non serve più (critica S10).
 *
 * Il sigillo è `enc (32 byte) || testo cifrato con tag`, come lo scrive e lo legge il telefono finto del server. Le
 * prove passano i vettori della suite usciti dal gruppo dell'RFC (T9), e aprono i sigilli fatti dal codice del server.
 */
object PampaHpke {
  const val ENC_BYTES = 32

  private fun suite(aead: Short = HPKE.aead_AES_GCM256) = HPKE(HPKE.mode_base, HPKE.kem_X25519_SHA256, HPKE.kdf_HKDF_SHA256, aead)

  /** Sigilla [plaintext] per la pubblica [recipient], con [info] e [aad]. Ogni sigillo ha un'effimera nuova. */
  fun seal(recipient: ByteArray, info: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray =
    sealWith(suite(), recipient, info, aad, plaintext, ephemeral = null)

  /** Apre un sigillo per [recipient]; [PampaCryptoException] se non è per questa chiave o è stato toccato. */
  fun open(recipient: X25519KeyPair, info: ByteArray, aad: ByteArray, sealed: ByteArray): ByteArray =
    openWith(suite(), recipient, info, aad, sealed)

  internal fun sealWith(hpke: HPKE, recipient: ByteArray, info: ByteArray, aad: ByteArray, plaintext: ByteArray, ephemeral: X25519KeyPair?): ByteArray {
    require(recipient.size == 32) { "la pubblica X25519 è di 32 byte, era ${recipient.size}" }
    val pk = runCatching { hpke.deserializePublicKey(recipient) }.getOrElse { throw PampaCryptoException("pubblica illeggibile", it) }
    val ctx = if (ephemeral == null) hpke.setupBaseS(pk, info) else hpke.setupBaseS(pk, info, ephemeral.asHpke(hpke))
    val ct = ctx.seal(aad, plaintext)
    return ctx.encapsulation + ct
  }

  internal fun openWith(hpke: HPKE, recipient: X25519KeyPair, info: ByteArray, aad: ByteArray, sealed: ByteArray): ByteArray {
    if (sealed.size <= ENC_BYTES) throw PampaCryptoException("sigillo troppo corto")
    val enc = sealed.copyOfRange(0, ENC_BYTES)
    return try {
      hpke.setupBaseR(enc, recipient.asHpke(hpke), info).open(aad, sealed.copyOfRange(ENC_BYTES, sealed.size))
    } catch (e: InvalidCipherTextException) {
      throw PampaCryptoException("sigillo non apribile", e)
    } catch (e: IllegalArgumentException) {
      throw PampaCryptoException("sigillo non apribile", e)
    } catch (e: IllegalStateException) {
      throw PampaCryptoException("sigillo non apribile", e)
    }
  }

  /** Per i vettori dell'RFC: la suite con AES-128-GCM (appendice A.1) o AES-256-GCM, e un contesto deterministico. */
  internal fun suiteFor(aeadId: Int): HPKE = suite(aeadId.toShort())
}
