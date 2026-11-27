package dev.antigravity.fluidengine.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import dev.antigravity.fluidengine.vault.VaultAuthMethod
import dev.antigravity.fluidengine.vault.VaultAuthRequest
import dev.antigravity.fluidengine.vault.VaultAuthResult
import dev.antigravity.fluidengine.vault.VaultAuthenticator
import dev.antigravity.fluidengine.vault.VaultUnavailability
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.suspendCancellableCoroutine

/** Che cosa sa fare il telefono per riconoscere la persona. */
data class FluidAuthCapability(
  /** Un'impronta o un volto **forti** (classe 3): aprono le chiavi della cassaforte. */
  val strongBiometrics: Boolean,
  /** Un'impronta o un volto qualsiasi (anche il volto «debole» di molti Samsung): bastano per il blocco dell'app. */
  val anyBiometrics: Boolean,
  /** Un blocco schermo sicuro (PIN, sequenza, password). */
  val deviceCredential: Boolean,
)

/**
 * Che cosa sa fare il telefono, letto dal sistema: `BiometricManager` da Android 10, l'impronta di `FingerprintManager`
 * su Android 9, niente prima (su Android 8 il dialogo dell'impronta non esiste: restano il codice del telefono e il PIN
 * dell'app).
 */
object FluidBiometrics {
  /**
   * Le capacità di adesso (cambiano se la persona registra un'impronta). Senza il permesso `USE_BIOMETRIC` nel manifesto
   * (lo dichiara questo modulo; un'app che lo togliesse) il sistema lancia una `SecurityException`: qui vuol dire
   * «niente impronta», mai un'app che si chiude.
   */
  fun capability(context: Context): FluidAuthCapability {
    val keyguard = context.getSystemService(KeyguardManager::class.java)
    val secure = keyguard?.isDeviceSecure == true
    return try {
      biometricCapability(context, secure)
    } catch (e: SecurityException) {
      FluidAuthCapability(strongBiometrics = false, anyBiometrics = false, deviceCredential = secure)
    }
  }

  private fun biometricCapability(context: Context, secure: Boolean): FluidAuthCapability {
    return when {
      Build.VERSION.SDK_INT >= 30 -> {
        val manager = context.getSystemService(BiometricManager::class.java)
        FluidAuthCapability(
          strongBiometrics = manager?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS,
          anyBiometrics = manager?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS,
          deviceCredential = secure,
        )
      }
      Build.VERSION.SDK_INT == 29 -> {
        val manager = context.getSystemService(BiometricManager::class.java)
        @Suppress("DEPRECATION")
        val ok = manager?.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
        FluidAuthCapability(strongBiometrics = ok, anyBiometrics = ok, deviceCredential = secure)
      }
      Build.VERSION.SDK_INT == 28 -> {
        val fingerprint = fingerprintEnrolled(context)
        FluidAuthCapability(strongBiometrics = fingerprint, anyBiometrics = fingerprint, deviceCredential = secure)
      }
      else -> FluidAuthCapability(strongBiometrics = false, anyBiometrics = false, deviceCredential = secure)
    }
  }

  @Suppress("DEPRECATION")
  private fun fingerprintEnrolled(context: Context): Boolean {
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) return false
    val manager = context.getSystemService(android.hardware.fingerprint.FingerprintManager::class.java) ?: return false
    return try {
      manager.isHardwareDetected && manager.hasEnrolledFingerprints()
    } catch (e: SecurityException) {
      false
    }
  }
}

/**
 * Il dialogo di sistema per l'impronta, il volto e il codice del telefono, per la cassaforte ([VaultAuthenticator]) e
 * per il blocco dell'app ([authenticateUser]). Il dialogo resta di sistema: non è vetro e non si cambia (scheda
 * dell'idea, «rischi»).
 *
 * **Perché non `androidx.biometric`.** La sua strada per una `ComponentActivity` (`registerForAuthenticationResult`) è
 * nella 1.4.0, ancora candidata (1.4.0-rc01 del 18/11/2026, letta su developer.android.com il 24/11/2026) e compilata
 * contro Android 16 QPR2 (`minCompileSdk` 36.1, più dell'engine); la 1.1.0 stabile vuole una `FragmentActivity`. Il
 * `BiometricPrompt` del sistema (Android 9+) lavora con qualunque attività: lo usiamo direttamente, con il codice del
 * telefono dentro il dialogo da Android 11, `setDeviceCredentialAllowed` su Android 10 e la conferma del `KeyguardManager`
 * prima. Quando la 1.4.0 sarà stabile e l'engine compilerà contro la 36.1 si potrà passare a lei senza cambiare l'API.
 *
 * Mentre il dialogo copre l'app, il blocco dell'app non scatta al ritorno ([FluidAppLock.allowLeaving]).
 */
class FluidSystemAuthenticator(
  private val activity: Activity,
  private val labels: FluidSecurityLabels = FluidSecurityLabels(),
) : VaultAuthenticator {

  override suspend fun authenticate(request: VaultAuthRequest): VaultAuthResult = when (request.method) {
    VaultAuthMethod.Biometric -> biometric(request)
    VaultAuthMethod.DeviceCredential -> deviceCredential(request.title, request.subtitle)
  }

  /**
   * Il dialogo per il blocco dell'app: l'impronta o il volto, anche «deboli», e il codice del telefono, insieme quando il
   * sistema lo permette. [fallback] è il tasto in basso quando il codice del telefono non sta nel dialogo («Usa il PIN
   * dell'app»): toccarlo dà [VaultAuthResult.Fallback].
   */
  suspend fun authenticateUser(title: String, subtitle: String? = null, fallback: String? = null): VaultAuthResult {
    val capability = FluidBiometrics.capability(activity)
    return when {
      Build.VERSION.SDK_INT >= 30 && (capability.anyBiometrics || capability.deviceCredential) -> prompt(
        title, subtitle, negative = null,
        authenticators = (if (capability.anyBiometrics) BiometricManager.Authenticators.BIOMETRIC_WEAK else 0) or
          (if (capability.deviceCredential) BiometricManager.Authenticators.DEVICE_CREDENTIAL else 0),
        credentialInPrompt = capability.deviceCredential,
        negativeIsFallback = false,
      )
      Build.VERSION.SDK_INT == 29 && capability.anyBiometrics -> prompt(
        title, subtitle, negative = null, authenticators = 0,
        credentialInPrompt = capability.deviceCredential, negativeIsFallback = false,
      )
      Build.VERSION.SDK_INT >= 28 && capability.anyBiometrics -> prompt(
        title, subtitle, negative = fallback ?: labels.cancel, authenticators = 0,
        credentialInPrompt = false, negativeIsFallback = fallback != null,
      )
      capability.deviceCredential -> deviceCredential(title, subtitle)
      else -> VaultAuthResult.Unavailable(VaultUnavailability.NoScreenLock)
    }
  }

  private suspend fun biometric(request: VaultAuthRequest): VaultAuthResult {
    if (Build.VERSION.SDK_INT < 28) return VaultAuthResult.Unavailable(VaultUnavailability.NoBiometrics)
    if (!FluidBiometrics.capability(activity).strongBiometrics) return VaultAuthResult.Unavailable(VaultUnavailability.NoBiometrics)
    return prompt(
      title = request.title,
      subtitle = request.subtitle,
      negative = request.fallback ?: labels.cancel,
      authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG,
      credentialInPrompt = false,
      negativeIsFallback = request.fallback != null,
      crypto = request.cipher,
      // R2 · Il sensore bloccato da troppe impronte sbagliate (anche per sempre, finché non si scrive il codice) per una
      // sezione con la riserva vale come il tasto della riserva: la sezione passa al codice o al PIN dell'app.
      lockoutIsFallback = request.fallback != null,
    )
  }

  private suspend fun deviceCredential(title: String, subtitle: String?): VaultAuthResult {
    if (!FluidBiometrics.capability(activity).deviceCredential) return VaultAuthResult.Unavailable(VaultUnavailability.NoScreenLock)
    if (Build.VERSION.SDK_INT >= 30) {
      return prompt(
        title, subtitle, negative = null, authenticators = BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        credentialInPrompt = true, negativeIsFallback = false,
      )
    }
    return keyguardConfirm(title, subtitle)
  }

  /** Il `BiometricPrompt` del sistema. [authenticators] conta solo da Android 11. */
  private suspend fun prompt(
    title: String,
    subtitle: String?,
    negative: String?,
    authenticators: Int,
    credentialInPrompt: Boolean,
    negativeIsFallback: Boolean,
    crypto: javax.crypto.Cipher? = null,
    lockoutIsFallback: Boolean = false,
  ): VaultAuthResult {
    if (Build.VERSION.SDK_INT < 28) return VaultAuthResult.Unavailable(VaultUnavailability.NoBiometrics)
    FluidAppLock.allowLeavingForSystemUi(2.minutes)
    try {
      return suspendCancellableCoroutine { continuation ->
        val executor = activity.mainExecutor
        val builder = BiometricPrompt.Builder(activity).setTitle(title)
        if (!subtitle.isNullOrBlank()) builder.setSubtitle(subtitle)
        if (Build.VERSION.SDK_INT >= 30) {
          builder.setAllowedAuthenticators(authenticators)
        } else if (Build.VERSION.SDK_INT == 29 && credentialInPrompt) {
          @Suppress("DEPRECATION")
          builder.setDeviceCredentialAllowed(true)
        }
        if (Build.VERSION.SDK_INT >= 29) builder.setConfirmationRequired(false)
        if (!credentialInPrompt) {
          builder.setNegativeButton(negative ?: labels.cancel, executor) { _, _ ->
            if (continuation.isActive) continuation.resume(if (negativeIsFallback) VaultAuthResult.Fallback else VaultAuthResult.Cancelled)
          }
        }
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        val callback = object : BiometricPrompt.AuthenticationCallback() {
          override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            if (continuation.isActive) continuation.resume(VaultAuthResult.Success)
          }

          override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            if (continuation.isActive) continuation.resume(errorResult(errorCode, errString, lockoutIsFallback))
          }
        }
        try {
          if (crypto != null) {
            builder.build().authenticate(BiometricPrompt.CryptoObject(crypto), signal, executor, callback)
          } else {
            builder.build().authenticate(signal, executor, callback)
          }
        } catch (e: RuntimeException) {
          if (continuation.isActive) continuation.resume(VaultAuthResult.Failed(e.message ?: labels.authenticationFailed))
        }
      }
    } finally {
      FluidAppLock.cancelLeavingForSystemUi()
    }
  }

  private fun errorResult(code: Int, message: CharSequence, lockoutIsFallback: Boolean): VaultAuthResult = when (code) {
    BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED, BiometricPrompt.BIOMETRIC_ERROR_CANCELED -> VaultAuthResult.Cancelled
    BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT, BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT ->
      if (lockoutIsFallback) VaultAuthResult.Fallback else VaultAuthResult.Failed(message.toString().ifBlank { labels.authenticationFailed })
    BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS, BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT,
    BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE -> VaultAuthResult.Unavailable(VaultUnavailability.NoBiometrics)
    BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL -> VaultAuthResult.Unavailable(VaultUnavailability.NoScreenLock)
    else -> VaultAuthResult.Failed(message.toString().ifBlank { labels.authenticationFailed })
  }

  /** La conferma del codice del telefono del `KeyguardManager` (Android 8–10): una schermata a sé. */
  @Suppress("DEPRECATION")
  private suspend fun keyguardConfirm(title: String, subtitle: String?): VaultAuthResult {
    val keyguard = activity.getSystemService(KeyguardManager::class.java)
    val intent = keyguard?.createConfirmDeviceCredentialIntent(title, subtitle)
      ?: return VaultAuthResult.Unavailable(VaultUnavailability.NoScreenLock)
    val component = activity as? ComponentActivity ?: return VaultAuthResult.Unavailable(VaultUnavailability.NoScreenLock)
    FluidAppLock.allowLeavingForSystemUi(5.minutes)
    return suspendCancellableCoroutine { continuation ->
      val key = "fluid-security-keyguard-${UUID.randomUUID()}"
      lateinit var launcher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>
      launcher = component.activityResultRegistry.register(key, ActivityResultContracts.StartActivityForResult()) { result ->
        launcher.unregister()
        if (continuation.isActive) {
          continuation.resume(if (result.resultCode == Activity.RESULT_OK) VaultAuthResult.Success else VaultAuthResult.Cancelled)
        }
      }
      continuation.invokeOnCancellation { launcher.unregister() }
      launcher.launch(intent)
    }
  }
}

/** L'impronta del PIN dell'app: PBKDF2-HMAC-SHA256 con un sale, mai il PIN. */
internal object FluidPinHash {
  private val random = SecureRandom()

  fun salt(): ByteArray = ByteArray(16).also { random.nextBytes(it) }

  fun hash(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray {
    val spec = PBEKeySpec(pin, salt, iterations, 256)
    return try {
      SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    } finally {
      spec.clearPassword()
    }
  }

  fun encode(salt: ByteArray, iterations: Int, hash: ByteArray): ByteArray =
    java.nio.ByteBuffer.allocate(4 + 4 + salt.size + hash.size).putInt(iterations).putInt(salt.size).put(salt).put(hash).array()

  fun verify(pin: CharArray, stored: ByteArray): Boolean {
    val buffer = java.nio.ByteBuffer.wrap(stored)
    if (stored.size < 8) return false
    val iterations = buffer.int
    val saltSize = buffer.int
    if (saltSize !in 1..64 || stored.size < 8 + saltSize + 1) return false
    val salt = ByteArray(saltSize).also { buffer.get(it) }
    val expected = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val actual = hash(pin, salt, iterations)
    var diff = expected.size xor actual.size
    for (i in 0 until minOf(expected.size, actual.size)) diff = diff or (expected[i].toInt() xor actual[i].toInt())
    actual.fill(0)
    return diff == 0
  }
}
