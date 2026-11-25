package dev.antigravity.fluidengine.sample.novita

import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.StrongBoxUnavailableException
import android.security.keystore.UserNotAuthenticatedException
import java.io.InputStream
import java.io.OutputStream
import java.security.AlgorithmParameters
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.Key
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyPairGeneratorSpi
import java.security.KeyStoreException
import java.security.KeyStoreSpi
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.security.spec.AlgorithmParameterSpec
import java.util.Collections
import java.util.Date
import java.util.Enumeration
import javax.crypto.Cipher
import javax.crypto.CipherSpi
import javax.crypto.IllegalBlockSizeException
import javax.crypto.KeyGeneratorSpi
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Un fornitore «AndroidKeyStore» finto per Robolectric, che sul computer non ha il Keystore. Fa girare il codice **vero**
 * che parla col Keystore ([dev.antigravity.fluidengine.vault.AndroidVaultKeystore] e il `KeystoreCipher` di `engine-ai`
 * della 3.x, copiato nei test com'era) e si comporta come il Keystore di Android dove conta:
 * - legge il `KeyGenParameterSpec` vero (StrongBox, autenticazione, invalidazione all'impronta nuova);
 * - le chiavi non escono: `getEncoded()` è nullo, e il `Cipher` le accetta solo da questo fornitore;
 * - una chiave a finestra rifiuta l'init senza un'autenticazione recente, una all'impronta il `doFinal` senza
 *   [approveNext] (il dialogo di sistema riuscito);
 * - [enrollBiometric] e [removeScreenLock] invalidano come Android (`KeyPermanentlyInvalidatedException`).
 */
internal object FakeAndroidKeyStore {
  class Entry(val spec: KeyGenParameterSpec, val secret: SecretKeySpec?, val pair: KeyPair?) {
    var invalid = false
  }

  val entries = LinkedHashMap<String, Entry>()
  var strongBoxAvailable = false
  var deviceSecure = true
  var biometricsEnrolled = true
  private var lastAuthAt: Long? = null
  private var approvals = 0

  /** Mette il fornitore finto al primo posto, al posto di quello vero se Robolectric ne avesse uno. */
  fun install() {
    reset()
    Security.removeProvider("AndroidKeyStore")
    Security.insertProviderAt(FakeProvider(), 1)
  }

  fun uninstall() {
    Security.removeProvider("AndroidKeyStore")
    reset()
  }

  fun reset() {
    entries.clear()
    strongBoxAvailable = false
    deviceSecure = true
    biometricsEnrolled = true
    lastAuthAt = null
    approvals = 0
  }

  /** Un'autenticazione dell'utente, adesso (apre le chiavi a finestra). */
  fun authenticate() {
    lastAuthAt = SystemClock.elapsedRealtime()
  }

  /** Il prossimo `doFinal` di una chiave all'impronta passa: è il dialogo di sistema riuscito col `CryptoObject`. */
  fun approveNext() {
    approvals++
    authenticate()
  }

  fun enrollBiometric() {
    entries.values.filter { it.spec.isUserAuthenticationRequired && it.spec.isInvalidatedByBiometricEnrollment && perUse(it.spec) }
      .forEach { it.invalid = true }
  }

  fun removeScreenLock() {
    deviceSecure = false
    biometricsEnrolled = false
    entries.values.filter { it.spec.isUserAuthenticationRequired }.forEach { it.invalid = true }
  }

  @Suppress("DEPRECATION")
  internal fun perUse(spec: KeyGenParameterSpec): Boolean = spec.userAuthenticationValidityDurationSeconds <= 0

  @Suppress("DEPRECATION")
  internal fun recentEnough(spec: KeyGenParameterSpec): Boolean {
    val at = lastAuthAt ?: return false
    return SystemClock.elapsedRealtime() - at <= spec.userAuthenticationValidityDurationSeconds * 1_000L
  }

  internal fun consumeApproval(): Boolean = if (approvals > 0) {
    approvals--
    true
  } else {
    false
  }

  internal fun checkCreatable(spec: KeyGenParameterSpec) {
    if (spec.isStrongBoxBacked && !strongBoxAvailable) throw StrongBoxUnavailableException()
    if (spec.isUserAuthenticationRequired && !deviceSecure) {
      throw IllegalStateException("Secure lock screen must be enabled to create keys requiring user authentication")
    }
    if (spec.isUserAuthenticationRequired && perUse(spec) && !biometricsEnrolled) {
      throw IllegalStateException("At least one biometric must be enrolled to create keys requiring user authentication for every use")
    }
  }
}

private class FakeProvider : Provider("AndroidKeyStore", 1.0, "Keystore finto dei test di F8.23") {
  init {
    put("KeyStore.AndroidKeyStore", FakeKeyStoreSpi::class.java.name)
    put("KeyGenerator.AES", FakeAesKeyGeneratorSpi::class.java.name)
    put("KeyPairGenerator.RSA", FakeRsaKeyPairGeneratorSpi::class.java.name)
    put("Cipher.AES/GCM/NoPadding", FakeAesGcmSpi::class.java.name)
    put("Cipher.RSA/ECB/OAEPWithSHA-256AndMGF1Padding", FakeRsaOaepSpi::class.java.name)
  }
}

/** Una chiave del Keystore finto: si vede il nome, mai i byte. */
internal class FakeSecretKey(val alias: String) : SecretKey {
  override fun getAlgorithm() = "AES"
  override fun getFormat(): String? = null
  override fun getEncoded(): ByteArray? = null
}

internal class FakePrivateKey(val alias: String) : PrivateKey {
  override fun getAlgorithm() = "RSA"
  override fun getFormat(): String? = null
  override fun getEncoded(): ByteArray? = null
}

private class FakeCertificate(private val key: PublicKey) : Certificate("X.509") {
  override fun getEncoded(): ByteArray = key.encoded
  override fun verify(key: PublicKey?) = Unit
  override fun verify(key: PublicKey?, sigProvider: String?) = Unit
  override fun toString() = "certificato finto"
  override fun getPublicKey(): PublicKey = key
}

class FakeKeyStoreSpi : KeyStoreSpi() {
  private val entries get() = FakeAndroidKeyStore.entries

  override fun engineGetKey(alias: String, password: CharArray?): Key? {
    val entry = entries[alias] ?: return null
    return if (entry.secret != null) FakeSecretKey(alias) else FakePrivateKey(alias)
  }

  override fun engineGetCertificateChain(alias: String): Array<Certificate>? = engineGetCertificate(alias)?.let { arrayOf(it) }
  override fun engineGetCertificate(alias: String): Certificate? = entries[alias]?.pair?.public?.let { FakeCertificate(it) }
  override fun engineGetCreationDate(alias: String): Date = Date()
  override fun engineSetKeyEntry(alias: String, key: Key, password: CharArray?, chain: Array<out Certificate>?) =
    throw KeyStoreException("non nel Keystore finto")
  override fun engineSetKeyEntry(alias: String, key: ByteArray, chain: Array<out Certificate>?) = throw KeyStoreException("non nel Keystore finto")
  override fun engineSetCertificateEntry(alias: String, cert: Certificate) = throw KeyStoreException("non nel Keystore finto")
  override fun engineDeleteEntry(alias: String) {
    entries.remove(alias)
  }
  override fun engineAliases(): Enumeration<String> = Collections.enumeration(entries.keys.toList())
  override fun engineContainsAlias(alias: String) = alias in entries
  override fun engineSize() = entries.size
  override fun engineIsKeyEntry(alias: String) = alias in entries
  override fun engineIsCertificateEntry(alias: String) = false
  override fun engineGetCertificateAlias(cert: Certificate): String? = null
  override fun engineStore(stream: OutputStream?, password: CharArray?) = Unit
  override fun engineLoad(stream: InputStream?, password: CharArray?) = Unit
}

class FakeAesKeyGeneratorSpi : KeyGeneratorSpi() {
  private var spec: KeyGenParameterSpec? = null
  override fun engineInit(random: SecureRandom?) = throw IllegalStateException("serve un KeyGenParameterSpec")
  override fun engineInit(params: AlgorithmParameterSpec?, random: SecureRandom?) {
    spec = params as? KeyGenParameterSpec ?: throw InvalidAlgorithmParameterException("serve un KeyGenParameterSpec")
  }
  override fun engineInit(keysize: Int, random: SecureRandom?) = throw IllegalStateException("serve un KeyGenParameterSpec")
  override fun engineGenerateKey(): SecretKey {
    val s = spec ?: throw IllegalStateException("non inizializzato")
    FakeAndroidKeyStore.checkCreatable(s)
    val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
    FakeAndroidKeyStore.entries[s.keystoreAlias] = FakeAndroidKeyStore.Entry(s, SecretKeySpec(raw, "AES"), null)
    return FakeSecretKey(s.keystoreAlias)
  }
}

class FakeRsaKeyPairGeneratorSpi : KeyPairGeneratorSpi() {
  private var spec: KeyGenParameterSpec? = null
  override fun initialize(keysize: Int, random: SecureRandom?) = throw IllegalStateException("serve un KeyGenParameterSpec")
  override fun initialize(params: AlgorithmParameterSpec?, random: SecureRandom?) {
    spec = params as? KeyGenParameterSpec ?: throw InvalidAlgorithmParameterException("serve un KeyGenParameterSpec")
  }
  override fun generateKeyPair(): KeyPair {
    val s = spec ?: throw IllegalStateException("non inizializzato")
    FakeAndroidKeyStore.checkCreatable(s)
    val real = KeyPairGenerator.getInstance("RSA", "SunRsaSign").apply { initialize(2048) }.generateKeyPair()
    FakeAndroidKeyStore.entries[s.keystoreAlias] = FakeAndroidKeyStore.Entry(s, null, real)
    return KeyPair(real.public, FakePrivateKey(s.keystoreAlias))
  }
}

/** Il `Cipher` del Keystore finto: lavora solo con le sue chiavi, e chiede l'autenticazione dove la chiede Android. */
abstract class FakeKeystoreCipherSpi(private val transformation: String) : CipherSpi() {
  private var inner: Cipher? = null
  private var entry: FakeAndroidKeyStore.Entry? = null

  override fun engineSetMode(mode: String?) = Unit
  override fun engineSetPadding(padding: String?) = Unit
  override fun engineGetBlockSize(): Int = inner?.blockSize ?: 16
  override fun engineGetOutputSize(inputLen: Int): Int = inner?.getOutputSize(inputLen) ?: (inputLen + 256)
  override fun engineGetIV(): ByteArray? = inner?.iv
  override fun engineGetParameters(): AlgorithmParameters? = inner?.parameters

  override fun engineInit(opmode: Int, key: Key?, random: SecureRandom?) = init(opmode, key, null)
  override fun engineInit(opmode: Int, key: Key?, params: AlgorithmParameterSpec?, random: SecureRandom?) = init(opmode, key, params)
  override fun engineInit(opmode: Int, key: Key?, params: AlgorithmParameters?, random: SecureRandom?) =
    init(opmode, key, params?.let { p ->
      if (transformation.startsWith("AES")) p.getParameterSpec(GCMParameterSpec::class.java) else p.getParameterSpec(OAEPParameterSpec::class.java)
    })

  private fun init(opmode: Int, key: Key?, params: AlgorithmParameterSpec?) {
    val alias = when (key) {
      is FakeSecretKey -> key.alias
      is FakePrivateKey -> key.alias
      else -> throw InvalidKeyException("non è una chiave del Keystore finto")
    }
    val e = FakeAndroidKeyStore.entries[alias] ?: throw InvalidKeyException("chiave sparita")
    if (e.invalid) throw KeyPermanentlyInvalidatedException()
    if (e.spec.isUserAuthenticationRequired && !FakeAndroidKeyStore.perUse(e.spec) && !FakeAndroidKeyStore.recentEnough(e.spec)) {
      throw UserNotAuthenticatedException()
    }
    val cipher = Cipher.getInstance(transformation, "SunJCE")
    if (e.secret != null) {
      if (opmode == Cipher.ENCRYPT_MODE) {
        // Come il Keystore con setRandomizedEncryptionRequired: il nonce lo sceglie lui.
        if (params != null) throw InvalidAlgorithmParameterException("Caller-provided IV not permitted")
        cipher.init(opmode, e.secret, GCMParameterSpec(128, ByteArray(12).also { SecureRandom().nextBytes(it) }))
      } else {
        cipher.init(opmode, e.secret, params)
      }
    } else {
      cipher.init(opmode, e.pair!!.private, params)
    }
    inner = cipher
    entry = e
  }

  override fun engineUpdateAAD(src: ByteArray, offset: Int, len: Int) {
    inner!!.updateAAD(src, offset, len)
  }

  override fun engineUpdate(input: ByteArray, inputOffset: Int, inputLen: Int): ByteArray? = inner!!.update(input, inputOffset, inputLen)
  override fun engineUpdate(input: ByteArray, inputOffset: Int, inputLen: Int, output: ByteArray, outputOffset: Int): Int =
    inner!!.update(input, inputOffset, inputLen, output, outputOffset)

  override fun engineDoFinal(input: ByteArray?, inputOffset: Int, inputLen: Int): ByteArray {
    guard()
    return inner!!.doFinal(input ?: ByteArray(0), inputOffset, inputLen)
  }

  override fun engineDoFinal(input: ByteArray?, inputOffset: Int, inputLen: Int, output: ByteArray, outputOffset: Int): Int {
    guard()
    return inner!!.doFinal(input ?: ByteArray(0), inputOffset, inputLen, output, outputOffset)
  }

  private fun guard() {
    val e = entry ?: throw IllegalStateException("non inizializzato")
    if (e.invalid) throw IllegalBlockSizeException().apply { initCause(KeyPermanentlyInvalidatedException()) }
    if (e.spec.isUserAuthenticationRequired && FakeAndroidKeyStore.perUse(e.spec) && !FakeAndroidKeyStore.consumeApproval()) {
      throw IllegalBlockSizeException().apply { initCause(KeyStoreException("Key user not authenticated")) }
    }
  }
}

class FakeAesGcmSpi : FakeKeystoreCipherSpi("AES/GCM/NoPadding")

class FakeRsaOaepSpi : FakeKeystoreCipherSpi("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
