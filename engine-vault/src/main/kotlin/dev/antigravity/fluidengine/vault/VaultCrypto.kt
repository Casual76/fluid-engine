package dev.antigravity.fluidengine.vault

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.security.GeneralSecurityException
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/**
 * La crittografia in software della cassaforte: AES-256-GCM con una chiave in memoria, RSA-OAEP con la pubblica, HKDF
 * e PBKDF2. Solo le API di Java (`javax.crypto`), che su Android sono quelle di Conscrypt: nessuna libreria.
 *
 * Le scelte (fiducia alta, NIST SP 800-38D e RFC 5869, rilette a memoria il 24/11/2026):
 * - AES-256-GCM con nonce casuale da 96 bit e tag da 128: il limite di circa 2³² cifrature per chiave è lontanissimo,
 *   perché ogni sezione ha la sua chiave e ogni file la sua chiave derivata;
 * - RSA-OAEP con SHA-256 e MGF1 SHA-1: è l'unica combinazione che il Keystore di Android accetta prima di Android 14
 *   (`KeyProperties.DIGEST_SHA256` per OAEP, MGF1 sempre SHA-1);
 * - PBKDF2-HMAC-SHA256 per un segreto scritto dall'utente (il PIN dell'app come riserva), sempre chiuso anche da una
 *   chiave del Keystore: un PIN corto da solo non regge un attacco fuori dal telefono.
 */
internal object VaultCrypto {
  const val KeyBytes = 32
  const val NonceBytes = 12
  const val TagBits = 128
  const val TagBytes = 16
  private const val Gcm = "AES/GCM/NoPadding"
  const val RsaOaep = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"

  /** I parametri OAEP che il Keystore di Android accetta: SHA-256, MGF1 con SHA-1. */
  val OaepParams: OAEPParameterSpec = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)

  val random: SecureRandom by lazy { SecureRandom() }

  fun randomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

  /** AES-GCM con la chiave [key] in memoria: nonce(12) ‖ testo cifrato ‖ tag(16). */
  fun seal(key: ByteArray, plain: ByteArray, associated: ByteArray, nonce: ByteArray = randomBytes(NonceBytes)): ByteArray {
    val cipher = Cipher.getInstance(Gcm)
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TagBits, nonce))
    if (associated.isNotEmpty()) cipher.updateAAD(associated)
    val body = cipher.doFinal(plain)
    return nonce + body
  }

  /** L'inverso di [seal]. @throws VaultException.Corrupt se il dato non torna. */
  fun open(key: ByteArray, sealed: ByteArray, associated: ByteArray): ByteArray {
    if (sealed.size < NonceBytes + TagBytes) throw VaultException.Corrupt("dato troppo corto")
    return try {
      val cipher = Cipher.getInstance(Gcm)
      cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TagBits, sealed, 0, NonceBytes))
      if (associated.isNotEmpty()) cipher.updateAAD(associated)
      cipher.doFinal(sealed, NonceBytes, sealed.size - NonceBytes)
    } catch (e: AEADBadTagException) {
      throw VaultException.Corrupt(cause = e)
    } catch (e: GeneralSecurityException) {
      throw VaultException.Corrupt(cause = e)
    }
  }

  /** Chiude [plain] (una chiave, pochi byte) con la pubblica RSA [publicKey]: in software, senza chiedere niente. */
  fun rsaSeal(publicKey: PublicKey, plain: ByteArray): ByteArray {
    val cipher = Cipher.getInstance(RsaOaep)
    cipher.init(Cipher.ENCRYPT_MODE, publicKey, OaepParams)
    return cipher.doFinal(plain)
  }

  /** HKDF-SHA256 (RFC 5869): [length] byte da [ikm], con [salt] e [info]. */
  fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int = KeyBytes): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(if (salt.isEmpty()) ByteArray(32) else salt, "HmacSHA256"))
    val prk = mac.doFinal(ikm)
    mac.init(SecretKeySpec(prk, "HmacSHA256"))
    val out = ByteArrayOutputStream(length)
    var previous = ByteArray(0)
    var counter = 1
    while (out.size() < length) {
      mac.update(previous)
      mac.update(info)
      mac.update(counter.toByte())
      previous = mac.doFinal()
      out.write(previous, 0, minOf(previous.size, length - out.size()))
      counter++
    }
    prk.fill(0)
    return out.toByteArray()
  }

  /** PBKDF2-HMAC-SHA256 di [secret] (che non si tocca: lo azzera chi lo possiede). */
  fun pbkdf2(secret: CharArray, salt: ByteArray, iterations: Int): ByteArray {
    val spec = PBEKeySpec(secret, salt, iterations, KeyBytes * 8)
    return try {
      SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    } finally {
      spec.clearPassword()
    }
  }

  /** Confronto a tempo costante. */
  fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
    if (a.size != b.size) return false
    var diff = 0
    for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
    return diff == 0
  }
}

/** Scrive un record binario piccolo (la scheda di una sezione, i valori): campi con la loro lunghezza. */
internal class VaultRecordWriter {
  private val bytes = ByteArrayOutputStream()
  private val out = DataOutputStream(bytes)

  fun int(value: Int) = apply { out.writeInt(value) }

  fun bytes(value: ByteArray?) = apply {
    if (value == null) {
      out.writeInt(-1)
    } else {
      out.writeInt(value.size)
      out.write(value)
    }
  }

  fun text(value: String?) = bytes(value?.toByteArray(Charsets.UTF_8))

  fun toByteArray(): ByteArray = bytes.toByteArray()
}

/** Legge un record scritto da [VaultRecordWriter]. @throws VaultException.Corrupt se il record non torna. */
internal class VaultRecordReader(data: ByteArray) {
  private val input = DataInputStream(ByteArrayInputStream(data))
  private val limit = data.size

  fun int(): Int = guard { input.readInt() }

  fun bytes(): ByteArray? = guard {
    val size = input.readInt()
    when {
      size == -1 -> null
      size < 0 || size > limit -> throw VaultException.Corrupt("campo di lunghezza $size")
      else -> ByteArray(size).also { input.readFully(it) }
    }
  }

  fun text(): String? = bytes()?.toString(Charsets.UTF_8)

  fun atEnd(): Boolean = input.available() == 0

  private inline fun <T> guard(block: () -> T): T = try {
    block()
  } catch (e: EOFException) {
    throw VaultException.Corrupt("record troncato", e)
  }
}
