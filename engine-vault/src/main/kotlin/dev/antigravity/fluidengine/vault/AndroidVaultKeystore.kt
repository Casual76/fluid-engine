package dev.antigravity.fluidengine.vault

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import java.security.InvalidKeyException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.PrivateKey
import java.security.ProviderException
import java.security.PublicKey
import java.security.UnrecoverableKeyException
import java.security.spec.X509EncodedKeySpec
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Il Keystore di Android per la cassaforte: le chiavi nascono nel chip separato (StrongBox) quando c'è, altrimenti
 * nell'ambiente protetto del processore, e non escono mai.
 *
 * - **StrongBox.** Si chiede sempre quando [VaultKeySpec.strongBox] è vero, e se il telefono non lo ha la chiave nasce
 *   nel TEE (`StrongBoxUnavailableException`, la strada che la documentazione indica). Anche un errore del chip con
 *   quei parametri ripiega sul TEE: una chiave un po' meno al riparo è meglio di nessuna chiave.
 * - **Le invalidazioni.** `KeyPermanentlyInvalidatedException` diventa [VaultException.Invalidated], col motivo letto
 *   dal blocco schermo: se non c'è più, è [VaultInvalidation.ScreenLockRemoved], altrimenti
 *   [VaultInvalidation.BiometricsChanged].
 * - **Le autenticazioni.** `UserNotAuthenticatedException`, o un `KeyStoreException` «utente non autenticato» dentro
 *   `doFinal`, diventano [VaultException.NeedsAuthentication].
 *
 * [context] serve solo a leggere il blocco schermo (per il motivo di un'invalidazione e per [isDeviceSecure]); senza,
 * il motivo è sempre «impronte cambiate» e il telefono si considera protetto.
 */
class AndroidVaultKeystore(context: Context? = null) : VaultKeystore {
  private val keyguard: KeyguardManager? = context?.applicationContext?.getSystemService(KeyguardManager::class.java)

  private val store: KeyStore by lazy { KeyStore.getInstance(Provider).apply { load(null) } }

  override val isDeviceSecure: Boolean
    get() = keyguard?.isDeviceSecure ?: true

  override fun contains(alias: String): Boolean = keystore { store.containsAlias(alias) }

  override fun delete(alias: String) {
    keystore { if (store.containsAlias(alias)) store.deleteEntry(alias) }
  }

  override fun createSecretKey(alias: String, spec: VaultKeySpec): VaultKeyInfo = create(alias, spec) { strongBox ->
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, Provider)
    generator.init(
      builder(alias, spec, strongBox)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .setRandomizedEncryptionRequired(true)
        .build(),
    )
    generator.generateKey()
  }

  override fun createKeyPair(alias: String, spec: VaultKeySpec): VaultKeyInfo = create(alias, spec) { strongBox ->
    val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, Provider)
    generator.initialize(
      // Solo DECRYPT: la cifratura si fa in software con la pubblica, che non chiede niente.
      KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_DECRYPT)
        .applyCommon(spec, strongBox)
        .setKeySize(2048)
        .setDigests(KeyProperties.DIGEST_SHA256)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
        .build(),
    )
    generator.generateKeyPair()
  }

  override fun publicKey(alias: String): PublicKey? = keystore {
    val certificate = store.getCertificate(alias) ?: return@keystore null
    // Una copia in software: la pubblica del Keystore lavora solo col suo fornitore, e cifrare con lei non serve.
    KeyFactory.getInstance(certificate.publicKey.algorithm).generatePublic(X509EncodedKeySpec(certificate.publicKey.encoded))
  }

  override fun aesEncrypt(alias: String): VaultCipherOp {
    val key = secretKey(alias)
    val cipher = Cipher.getInstance(Gcm)
    initGuarded { cipher.init(Cipher.ENCRYPT_MODE, key) }
    return AndroidOp(cipher, cipher.iv ?: ByteArray(0))
  }

  override fun aesDecrypt(alias: String, iv: ByteArray): VaultCipherOp {
    val key = secretKey(alias)
    val cipher = Cipher.getInstance(Gcm)
    initGuarded { cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(VaultCrypto.TagBits, iv)) }
    return AndroidOp(cipher, iv)
  }

  override fun rsaDecrypt(alias: String): VaultCipherOp {
    val key = keystore { store.getKey(alias, null) as? PrivateKey } ?: throw VaultException.Invalidated(VaultInvalidation.KeyLost)
    val cipher = Cipher.getInstance(VaultCrypto.RsaOaep)
    initGuarded { cipher.init(Cipher.DECRYPT_MODE, key, VaultCrypto.OaepParams) }
    return AndroidOp(cipher, ByteArray(0))
  }

  // --- dentro -----------------------------------------------------------------------------------------------------

  private fun secretKey(alias: String): SecretKey =
    keystore { store.getKey(alias, null) as? SecretKey } ?: throw VaultException.Invalidated(VaultInvalidation.KeyLost)

  private fun create(alias: String, spec: VaultKeySpec, generate: (strongBox: Boolean) -> Unit): VaultKeyInfo {
    val wantStrongBox = spec.strongBox && Build.VERSION.SDK_INT >= 28
    if (wantStrongBox) {
      try {
        generate(true)
        return VaultKeyInfo(alias, spec.auth, strongBox = true)
      } catch (e: Exception) {
        if (!isStrongBoxRefusal(e)) throw translateCreation(e, spec)
        delete(alias)
      }
    }
    try {
      generate(false)
    } catch (e: Exception) {
      throw translateCreation(e, spec)
    }
    return VaultKeyInfo(alias, spec.auth, strongBox = if (Build.VERSION.SDK_INT >= 28) false else null)
  }

  // StrongBoxUnavailableException e' un ProviderException: lo stesso ripiego copre il chip assente e il chip che rifiuta
  // quei parametri.
  private fun isStrongBoxRefusal(e: Throwable): Boolean = e is ProviderException

  private fun translateCreation(e: Exception, spec: VaultKeySpec): VaultException = when {
    e is VaultException -> e
    spec.auth == VaultKeyAuth.None -> VaultException.Unavailable(VaultUnavailability.KeystoreError, e)
    // «Secure lock screen must be enabled» / «At least one biometric must be enrolled»: IllegalStateException
    // dentro InvalidAlgorithmParameterException, o diretta, secondo la versione.
    e.causes().any { it is IllegalStateException && it.message.orEmpty().contains("biometric", ignoreCase = true) } ->
      VaultException.Unavailable(VaultUnavailability.NoBiometrics, e)
    e.causes().any { it is IllegalStateException } || !isDeviceSecure ->
      VaultException.Unavailable(VaultUnavailability.NoScreenLock, e)
    else -> VaultException.Unavailable(VaultUnavailability.KeystoreError, e)
  }

  private fun builder(alias: String, spec: VaultKeySpec, strongBox: Boolean): KeyGenParameterSpec.Builder =
    KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).applyCommon(spec, strongBox)

  @Suppress("DEPRECATION")
  private fun KeyGenParameterSpec.Builder.applyCommon(spec: VaultKeySpec, strongBox: Boolean): KeyGenParameterSpec.Builder {
    if (strongBox && Build.VERSION.SDK_INT >= 28) setIsStrongBoxBacked(true)
    if (spec.unlockedDeviceRequired && Build.VERSION.SDK_INT >= 28) setUnlockedDeviceRequired(true)
    when (val auth = spec.auth) {
      VaultKeyAuth.None -> Unit
      VaultKeyAuth.BiometricEachUse -> {
        setUserAuthenticationRequired(true)
        setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= 30) {
          setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
          setUserAuthenticationValidityDurationSeconds(-1)
        }
      }
      is VaultKeyAuth.Recent -> {
        setUserAuthenticationRequired(true)
        // Una finestra di tempo: un'impronta nuova non la invalida (la documentazione di
        // setInvalidatedByBiometricEnrollment), ma lo diciamo lo stesso.
        setInvalidatedByBiometricEnrollment(false)
        if (Build.VERSION.SDK_INT >= 30) {
          setUserAuthenticationParameters(auth.seconds, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
        } else {
          setUserAuthenticationValidityDurationSeconds(auth.seconds)
        }
      }
    }
    return this
  }

  private inline fun initGuarded(block: () -> Unit) {
    try {
      block()
    } catch (e: KeyPermanentlyInvalidatedException) {
      throw VaultException.Invalidated(invalidationReason(), e)
    } catch (e: UserNotAuthenticatedException) {
      throw VaultException.NeedsAuthentication(e)
    } catch (e: UnrecoverableKeyException) {
      throw VaultException.Invalidated(VaultInvalidation.KeyLost, e)
    } catch (e: InvalidKeyException) {
      if (needsAuthentication(e)) throw VaultException.NeedsAuthentication(e)
      throw VaultException.Invalidated(invalidationReason(), e)
    }
  }

  private fun invalidationReason(): VaultInvalidation =
    if (!isDeviceSecure) VaultInvalidation.ScreenLockRemoved else VaultInvalidation.BiometricsChanged

  private inner class AndroidOp(override val cipher: Cipher, override val iv: ByteArray) : VaultCipherOp {
    override fun associate(data: ByteArray) {
      if (data.isNotEmpty()) cipher.updateAAD(data)
    }

    override fun finish(input: ByteArray): ByteArray = try {
      cipher.doFinal(input)
    } catch (e: AEADBadTagException) {
      throw VaultException.Corrupt(cause = e)
    } catch (e: KeyPermanentlyInvalidatedException) {
      throw VaultException.Invalidated(invalidationReason(), e)
    } catch (e: IllegalBlockSizeException) {
      if (needsAuthentication(e)) throw VaultException.NeedsAuthentication(e)
      throw VaultException.Corrupt(cause = e)
    } catch (e: BadPaddingException) {
      throw VaultException.Corrupt(cause = e)
    } catch (e: IllegalStateException) {
      // Un Cipher dato a BiometricPrompt e poi annullato: non si può più usare.
      throw VaultException.NeedsAuthentication(e)
    }
  }

  private inline fun <T> keystore(block: () -> T): T = try {
    block()
  } catch (e: KeyStoreException) {
    throw VaultException.Unavailable(VaultUnavailability.KeystoreError, e)
  } catch (e: UnrecoverableKeyException) {
    throw VaultException.Invalidated(VaultInvalidation.KeyLost, e)
  }

  companion object {
    private const val Provider = "AndroidKeyStore"
    private const val Gcm = "AES/GCM/NoPadding"

    /** Vero se fra le cause c'è il «chiave non autenticata» del Keystore (Android 6–16). */
    internal fun needsAuthentication(e: Throwable): Boolean = e.causes().any { cause ->
      cause is UserNotAuthenticatedException ||
        (Build.VERSION.SDK_INT >= 33 && cause is android.security.KeyStoreException &&
          cause.numericErrorCode == android.security.KeyStoreException.ERROR_USER_AUTHENTICATION_REQUIRED) ||
        cause.message.orEmpty().contains("not authenticated", ignoreCase = true)
    }

    private fun Throwable.causes(): Sequence<Throwable> = generateSequence(this) { it.cause.takeIf { c -> c !== it } }
  }
}
