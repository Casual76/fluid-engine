package dev.antigravity.fluidengine.auth

import android.app.Activity
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import kotlin.coroutines.cancellation.CancellationException

/** Una password (o un codice) letta dal gestore. [toString] non stampa il segreto. */
class FluidSavedPassword internal constructor(
  /** Il nome con cui è salvata, per esempio «Codice di recupero Pampa». */
  val id: String,
  private val value: String,
) {
  /** Il segreto. Resta sul telefono. */
  fun secret(): String = value

  override fun toString(): String = "FluidSavedPassword(id=$id, secret=nascosto)"
}

/**
 * Un segreto nel gestore delle password del telefono (Gestore password di Google, Samsung Pass…), con lo stesso foglio
 * di sistema delle passkey.
 *
 * Per l'Account Pampa è il **codice di recupero** (architettura § 1.5): lo Store lo propone come una «password»
 * chiamata «Codice di recupero Pampa». Meglio anche su carta: se il gestore è lo stesso della passkey, perderlo fa
 * perdere tutti e due. Il codice lo genera il telefono, e al server non arriva mai (critica S1).
 */
object FluidPasswords {

  /** Salva [secret] col nome [id]. Il gestore mostra il suo foglio e chiede conferma. */
  suspend fun save(
    activity: Activity,
    id: String,
    secret: String,
    labels: FluidAuthLabels = FluidAuthLabels(),
  ): AuthResult<Unit> {
    if (id.isBlank() || secret.isEmpty()) return AuthResult.Failed(labels.badRequest, null)
    val request = try {
      CreatePasswordRequest(id, secret)
    } catch (e: IllegalArgumentException) {
      return AuthResult.Failed(labels.badRequest, e)
    }
    return try {
      CredentialGateways.of(activity).create(activity, request)
      AuthResult.Ok(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: CreateCredentialException) {
      FluidAuthErrors.fromCreate(e, labels)
    }
  }

  /**
   * Rilegge un segreto salvato. Con [ids] il gestore propone solo quelli con quei nomi. Senza niente da proporre
   * risponde [AuthResult.NoCredential]: la frase è `phrase(password = true)`.
   */
  suspend fun get(
    activity: Activity,
    ids: Set<String> = emptySet(),
    labels: FluidAuthLabels = FluidAuthLabels(),
  ): AuthResult<FluidSavedPassword> {
    val request = GetCredentialRequest.Builder().addCredentialOption(GetPasswordOption(ids)).build()
    val response = try {
      CredentialGateways.of(activity).get(activity, request)
    } catch (e: CancellationException) {
      throw e
    } catch (e: GetCredentialException) {
      return FluidAuthErrors.fromGet(e, labels)
    }
    val credential = response.credential as? PasswordCredential ?: return AuthResult.Failed(labels.badRequest, null)
    return AuthResult.Ok(FluidSavedPassword(credential.id, credential.password))
  }
}
