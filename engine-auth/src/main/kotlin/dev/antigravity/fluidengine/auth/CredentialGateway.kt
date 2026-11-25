package dev.antigravity.fluidengine.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CreateCredentialRequest
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse

/**
 * Il punto unico in cui il modulo chiama Credential Manager. Esiste per le prove: Robolectric non ha un gestore di
 * passkey, e i test mettono al suo posto uno che lancia le eccezioni vere di `androidx.credentials`
 * ([CredentialGateways.override]). In un'app è sempre [CredentialManager].
 */
internal interface CredentialGateway {
  suspend fun create(activity: Activity, request: CreateCredentialRequest): CreateCredentialResponse

  suspend fun get(activity: Activity, request: GetCredentialRequest): GetCredentialResponse
}

internal object CredentialGateways {
  /** Solo per i test. */
  @Volatile
  var override: CredentialGateway? = null

  fun of(context: Context): CredentialGateway = override ?: RealGateway(CredentialManager.create(context))

  private class RealGateway(private val manager: CredentialManager) : CredentialGateway {
    override suspend fun create(activity: Activity, request: CreateCredentialRequest): CreateCredentialResponse =
      manager.createCredential(activity, request)

    override suspend fun get(activity: Activity, request: GetCredentialRequest): GetCredentialResponse =
      manager.getCredential(activity, request)
  }
}
