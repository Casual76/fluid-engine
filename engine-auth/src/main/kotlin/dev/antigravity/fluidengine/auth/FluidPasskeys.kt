package dev.antigravity.fluidengine.auth

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Le passkey col foglio di sistema di Android (Credential Manager), senza rete: JSON WebAuthn in entrata, risposta
 * divisa fra server e telefono in uscita ([FluidPasskeyResult]). È la scheda `passkey` dell'Account Pampa (B § 9.2).
 *
 * ```kotlin
 * // 1. l'app chiede le opzioni al server (POST /v1/passkeys/register/options) e passa l'oggetto «options»
 * when (val r = FluidPasskeys.create(activity, optionsJson)) {
 *   is AuthResult.Ok -> server.verify(r.value.responseJson)      // senza il PRF
 *     .also { keepOnPhone(r.value.prfOutput()) }                  // il PRF resta qui
 *   else -> show(r.phrase())                                       // la frase pronta
 * }
 * ```
 *
 * **Il PRF lo scrive il modulo** ([FluidPrfInput], critica S9): qualunque `extensions.prf` arrivi dal server si toglie,
 * e al suo posto va [FluidPrfInput.PampaV1] (o quello passato). Alla creazione si chiede già il valore
 * (`evaluatePrfAtCreation`), perché alcuni gestori lo danno: chi non lo dà risponde solo `prf.enabled`, e lo Store fa
 * subito un accesso (`chiavi.md` § 3.2).
 *
 * **Prima di chiamare** si può sapere se il telefono non può ([unsupportedReason]): Android più vecchio di 9, nessun
 * blocco schermo. Le due chiamate lo controllano comunque, e rispondono [AuthResult.Unsupported] senza aprire niente.
 * Il gestore che manca si sa solo chiamando: arriva come [AuthResult.Why.NoProvider].
 *
 * **Android 9–13.** Lì le passkey passano da Google Play services: serve anche
 * `androidx.credentials:credentials-play-services-auth`, che porta `engine-auth-google`. Da Android 14 basta il sistema.
 */
object FluidPasskeys {
  /** Android 9: da qui Credential Manager gestisce le passkey ([passkeys](https://developer.android.com/identity/passkeys), fiducia alta). */
  const val MIN_SDK: Int = 28

  /**
   * Perché il telefono non può usare le passkey, o `null` se può provarci. Non sa se c'è un gestore: quello lo dice solo
   * Credential Manager, quando lo si chiama.
   */
  fun unsupportedReason(context: Context): AuthResult.Why? {
    if (Build.VERSION.SDK_INT < MIN_SDK) return AuthResult.Why.OldAndroid
    val keyguard = context.getSystemService(KeyguardManager::class.java)
    if (keyguard != null && !keyguard.isDeviceSecure) return AuthResult.Why.NoScreenLock
    return null
  }

  /** Android 9 o successivo e un blocco schermo. Il gestore si scopre chiamando. */
  fun isSupported(context: Context): Boolean = unsupportedReason(context) == null

  /**
   * Crea una passkey. [requestJson] è l'oggetto `options` di WebAuthn che manda il server (`challenge`, `rp`, `user`,
   * `pubKeyCredParams`…). [prf] è l'ingresso del PRF, scritto dal modulo; `null` non lo chiede.
   * [evaluatePrfAtCreation] chiede il valore già alla creazione; falso chiede solo se c'è (`prf: {}`).
   */
  suspend fun create(
    activity: Activity,
    requestJson: String,
    prf: FluidPrfInput? = FluidPrfInput.PampaV1,
    evaluatePrfAtCreation: Boolean = true,
    labels: FluidAuthLabels = FluidAuthLabels(),
  ): AuthResult<FluidPasskeyResult> {
    unsupportedReason(activity)?.let { return AuthResult.Unsupported(it) }
    // Anche Credential Manager rilegge il JSON (il nome dell'utente per il foglio) e lancia IllegalArgumentException:
    // FluidWebAuthnJsonException è dello stesso tipo, quindi un catch solo.
    val request = try {
      CreatePublicKeyCredentialRequest(FluidWebAuthnJson.creationRequest(requestJson, prf, evaluatePrfAtCreation))
    } catch (e: IllegalArgumentException) {
      return AuthResult.Failed(labels.badRequest, e)
    }
    val response = try {
      CredentialGateways.of(activity).create(activity, request)
    } catch (e: CancellationException) {
      throw e
    } catch (e: CreateCredentialException) {
      return FluidAuthErrors.fromCreate(e, labels)
    }
    val json = (response as? CreatePublicKeyCredentialResponse)?.registrationResponseJson
      ?: return AuthResult.Failed(labels.badRequest, null)
    return try {
      AuthResult.Ok(FluidWebAuthnJson.readCreation(json))
    } catch (e: FluidWebAuthnJsonException) {
      AuthResult.Failed(labels.badRequest, e)
    }
  }

  /**
   * Entra con una passkey. [requestJson] è l'oggetto `options` di accesso (`challenge`, `rpId`, `allowCredentials`…).
   * [prf] è l'ingresso del PRF, scritto dal modulo al posto di quello del server; `null` non lo chiede.
   * [onlyOnThisPhone] non propone passkey di altri dispositivi (il QR): senza una passkey qui risponde subito
   * [AuthResult.NoCredential] senza mostrare niente, ed è il modo di provare all'avvio. Con una passkey il foglio
   * chiede comunque l'impronta.
   */
  suspend fun get(
    activity: Activity,
    requestJson: String,
    prf: FluidPrfInput? = FluidPrfInput.PampaV1,
    onlyOnThisPhone: Boolean = false,
    labels: FluidAuthLabels = FluidAuthLabels(),
  ): AuthResult<FluidPasskeyResult> {
    unsupportedReason(activity)?.let { return AuthResult.Unsupported(it) }
    val getRequest = try {
      GetCredentialRequest.Builder()
        .addCredentialOption(GetPublicKeyCredentialOption(FluidWebAuthnJson.assertionRequest(requestJson, prf)))
        .setPreferImmediatelyAvailableCredentials(onlyOnThisPhone)
        .build()
    } catch (e: IllegalArgumentException) {
      return AuthResult.Failed(labels.badRequest, e)
    }
    val response = try {
      CredentialGateways.of(activity).get(activity, getRequest)
    } catch (e: CancellationException) {
      throw e
    } catch (e: GetCredentialException) {
      return FluidAuthErrors.fromGet(e, labels)
    }
    val json = (response.credential as? PublicKeyCredential)?.authenticationResponseJson
      ?: return AuthResult.Failed(labels.badRequest, null)
    return try {
      AuthResult.Ok(FluidWebAuthnJson.readAssertion(json))
    } catch (e: FluidWebAuthnJsonException) {
      AuthResult.Failed(labels.badRequest, e)
    }
  }
}
