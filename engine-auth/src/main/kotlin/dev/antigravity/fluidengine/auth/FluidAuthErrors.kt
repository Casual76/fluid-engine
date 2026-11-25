package dev.antigravity.fluidengine.auth

import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialInterruptedException
import androidx.credentials.exceptions.CreateCredentialNoCreateOptionException
import androidx.credentials.exceptions.CreateCredentialProviderConfigurationException
import androidx.credentials.exceptions.CreateCredentialUnsupportedException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.domerrors.AbortError
import androidx.credentials.exceptions.domerrors.DomError
import androidx.credentials.exceptions.domerrors.InvalidStateError
import androidx.credentials.exceptions.domerrors.NotAllowedError
import androidx.credentials.exceptions.domerrors.NotSupportedError
import androidx.credentials.exceptions.domerrors.SecurityError
import androidx.credentials.exceptions.domerrors.TimeoutError
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import androidx.credentials.exceptions.publickeycredential.GetPublicKeyCredentialDomException

/**
 * Le eccezioni di Credential Manager tradotte in [AuthResult] (B § 9.2). Una tabella sola per la creazione e una per
 * l'accesso, così le frasi sono le stesse in ogni chiamata, anche in `engine-auth-google` e in un'app che chiama
 * Credential Manager da sé.
 *
 * | Eccezione | Esito |
 * |---|---|
 * | `…CancellationException`, DOM `NotAllowedError`, `AbortError` | [AuthResult.Cancelled] |
 * | `NoCredentialException` | [AuthResult.NoCredential] |
 * | `…ProviderConfigurationException`, `…UnsupportedException`, `CreateCredentialNoCreateOptionException`, DOM `NotSupportedError` | [AuthResult.Unsupported] con [AuthResult.Why.NoProvider] |
 * | `…InterruptedException`, DOM `TimeoutError` | [AuthResult.Failed] con [FluidAuthLabels.interrupted], da riprovare |
 * | DOM `SecurityError` | [AuthResult.Failed] con [FluidAuthLabels.wrongDomain] (di solito `assetlinks.json`) |
 * | DOM `InvalidStateError` alla creazione | [AuthResult.Failed] con [FluidAuthLabels.alreadyExists] |
 * | tutto il resto | [AuthResult.Failed] con [FluidAuthLabels.generic] |
 *
 * `NotAllowedError` è anche «tempo scaduto» e «l'utente non ha confermato»: WebAuthn lo tiene ambiguo di proposito
 * ([WebAuthn § 5.1.3](https://www.w3.org/TR/webauthn-3/#sctn-createCredential), fiducia alta), e un browser non mostra
 * niente. Qui vale lo stesso.
 */
object FluidAuthErrors {

  /** L'esito di un'eccezione della creazione (passkey o password). */
  fun fromCreate(e: CreateCredentialException, labels: FluidAuthLabels): AuthResult<Nothing> = when (e) {
    is CreateCredentialCancellationException -> AuthResult.Cancelled
    is CreateCredentialInterruptedException -> AuthResult.Failed(labels.interrupted, e, retry = true)
    is CreateCredentialProviderConfigurationException,
    is CreateCredentialUnsupportedException,
    is CreateCredentialNoCreateOptionException -> AuthResult.Unsupported(AuthResult.Why.NoProvider)
    is CreatePublicKeyCredentialDomException -> fromDom(e.domError, e, labels, creating = true)
    else -> AuthResult.Failed(labels.generic, e)
  }

  /** L'esito di un'eccezione dell'accesso (passkey, password, o l'accesso con Google di `engine-auth-google`). */
  fun fromGet(e: GetCredentialException, labels: FluidAuthLabels): AuthResult<Nothing> = when (e) {
    is GetCredentialCancellationException -> AuthResult.Cancelled
    is NoCredentialException -> AuthResult.NoCredential
    is GetCredentialInterruptedException -> AuthResult.Failed(labels.interrupted, e, retry = true)
    is GetCredentialProviderConfigurationException,
    is GetCredentialUnsupportedException -> AuthResult.Unsupported(AuthResult.Why.NoProvider)
    is GetPublicKeyCredentialDomException -> fromDom(e.domError, e, labels, creating = false)
    else -> AuthResult.Failed(labels.generic, e)
  }

  private fun fromDom(error: DomError, e: Throwable, labels: FluidAuthLabels, creating: Boolean): AuthResult<Nothing> =
    when (error) {
      is NotAllowedError, is AbortError -> AuthResult.Cancelled
      is NotSupportedError -> AuthResult.Unsupported(AuthResult.Why.NoProvider)
      is TimeoutError -> AuthResult.Failed(labels.interrupted, e, retry = true)
      is SecurityError -> AuthResult.Failed(labels.wrongDomain, e)
      is InvalidStateError -> AuthResult.Failed(if (creating) labels.alreadyExists else labels.generic, e)
      else -> AuthResult.Failed(labels.generic, e)
    }
}
