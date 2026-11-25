// GPL-3.0, con il permesso aggiuntivo della sezione 7 per le librerie di Google (Play services, googleid,
// credentials-play-services-auth): LICENSES/Permesso-librerie-Google.md.
package dev.antigravity.fluidengine.auth.google

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import dev.antigravity.fluidengine.auth.AuthResult
import dev.antigravity.fluidengine.auth.FluidAuthErrors
import dev.antigravity.fluidengine.auth.FluidAuthLabels
import kotlin.coroutines.cancellation.CancellationException

/**
 * L'ID token di Google, da spedire al server, e i dati dell'account da mostrare sul telefono.
 *
 * Il server ne legge solo il `sub` e non tiene l'email (regola R3): [email], [displayName] e [pictureUri] sono per lo
 * Store, che li mostra leggendoli dal telefono. [toString] non stampa il token.
 */
class FluidGoogleIdToken internal constructor(
  private val token: String,
  /** L'email dell'account Google scelto. Resta sul telefono. */
  val email: String,
  /** Il nome dell'account, se Google lo dà. */
  val displayName: String?,
  /** La foto dell'account, se c'è. */
  val pictureUri: String?,
) {
  /** Il token, da spedire al server (`POST /v1/google/login`). */
  fun idToken(): String = token

  override fun toString(): String = "FluidGoogleIdToken(email=nascosta, displayName=${displayName != null}, idToken=nascosto)"
}

/**
 * «Continua con Google» con Credential Manager (architettura § 1.3,
 * [Sign in with Google](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation), fiducia
 * alta). Chiede solo `openid`, `email` e `profile`: entrare e autorizzare sono due cose separate, e le concessioni stanno
 * in [FluidGoogleAccess].
 *
 * - [returning] vero: il foglio dal basso con i soli account già entrati (`setFilterByAuthorizedAccounts(true)`) e la
 *   scelta automatica se ce n'è uno. Se non ce n'è, risponde [AuthResult.NoCredential] senza mostrare niente: lo Store
 *   allora mostra il tasto.
 * - [returning] falso: il foglio del tasto «Continua con Google» (`GetSignInWithGoogleOption`), con tutti gli account.
 *
 * Il [nonce] lo dà il server (`POST /v1/google/nonce`) e torna dentro il token: il server controlla che sia lo stesso.
 * Il [webClientId] è il client **web** del progetto Google (non quello Android), che diventa `aud` del token. Nessun
 * client secret: il modulo non scambia mai codici con Google.
 */
object FluidGoogleSignIn {

  suspend fun idToken(
    activity: Activity,
    webClientId: String,
    nonce: String,
    returning: Boolean,
    labels: FluidAuthLabels = FluidAuthLabels(),
  ): AuthResult<FluidGoogleIdToken> {
    if (webClientId.isBlank() || nonce.isBlank()) return AuthResult.Failed(labels.badRequest, null)
    val option = if (returning) {
      GetGoogleIdOption.Builder()
        .setServerClientId(webClientId)
        .setFilterByAuthorizedAccounts(true)
        .setAutoSelectEnabled(true)
        .setNonce(nonce)
        .build()
    } else {
      GetSignInWithGoogleOption.Builder(webClientId).setNonce(nonce).build()
    }
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val response = try {
      GoogleGateways.of(activity).get(activity, request)
    } catch (e: CancellationException) {
      throw e
    } catch (e: GetCredentialException) {
      return FluidAuthErrors.fromGet(e, labels)
    }
    return read(response, labels)
  }

  internal fun read(response: GetCredentialResponse, labels: FluidAuthLabels): AuthResult<FluidGoogleIdToken> {
    val credential = response.credential
    if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
      return AuthResult.Failed(labels.badRequest, null)
    }
    return try {
      val google = GoogleIdTokenCredential.createFrom(credential.data)
      AuthResult.Ok(FluidGoogleIdToken(google.idToken, google.id, google.displayName, google.profilePictureUri?.toString()))
    } catch (e: GoogleIdTokenParsingException) {
      AuthResult.Failed(labels.badRequest, e)
    } catch (e: IllegalArgumentException) {
      AuthResult.Failed(labels.badRequest, e)
    }
  }
}

/** Come in engine-auth: il punto unico verso Credential Manager, sostituibile nei test. */
internal interface GoogleGateway {
  suspend fun get(activity: Activity, request: GetCredentialRequest): GetCredentialResponse
}

internal object GoogleGateways {
  @Volatile
  var override: GoogleGateway? = null

  fun of(context: Context): GoogleGateway = override ?: object : GoogleGateway {
    private val manager = CredentialManager.create(context)

    override suspend fun get(activity: Activity, request: GetCredentialRequest): GetCredentialResponse =
      manager.getCredential(activity, request)
  }
}
