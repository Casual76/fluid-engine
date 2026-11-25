// GPL-3.0, con il permesso aggiuntivo della sezione 7 per le librerie di Google (Play services, googleid,
// credentials-play-services-auth): LICENSES/Permesso-librerie-Google.md.
package dev.antigravity.fluidengine.auth.google

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import dev.antigravity.fluidengine.auth.AuthResult
import dev.antigravity.fluidengine.auth.FluidAuthLabels
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

/**
 * L'esito di [FluidGoogleAccess.authorize].
 *
 * - [Token]: c'è il token d'accesso per gli ambiti concessi. Vale circa un'ora ([Token.expiresAtElapsed]) e sta solo in
 *   memoria. [toString] non lo stampa.
 * - [NeedsConsent]: Google deve chiedere il consenso. L'app lancia [NeedsConsent.intent] e passa il risultato a
 *   [FluidGoogleAccess.fromConsent].
 * - [NoPlayServices]: sul telefono non c'è Google Play services, o non è aggiornato.
 * - [Failed]: tutto il resto, con la frase pronta.
 * - [Off]: le concessioni sono spente ([FluidGoogleAccess.isEnabled]), e non si è toccato Google.
 */
sealed interface GoogleAccess {
  class Token(
    val accessToken: String,
    val grantedScopes: Set<String>,
    /** Quando scade, in `SystemClock.elapsedRealtime()`. */
    val expiresAtElapsed: Long,
  ) : GoogleAccess {
    override fun toString(): String = "GoogleAccess.Token(scopes=$grantedScopes, accessToken=nascosto)"
  }

  data class NeedsConsent(val intent: PendingIntent) : GoogleAccess

  data object NoPlayServices : GoogleAccess

  data class Failed(val message: String) : GoogleAccess

  data object Off : GoogleAccess
}

/**
 * Le concessioni Google (architettura § 4): un token d'accesso per un ambito, come il Calendario, chiesto **solo sul
 * telefono** con l'API di autorizzazione di Play services
 * ([authorization](https://developer.android.com/identity/authorization), fiducia alta). Il server non tiene niente di
 * Google (regola R5).
 *
 * **Scritto ma spento** (scelta N6 di Alessio): le concessioni si accendono al primo uso vero, e per ora non esiste
 * nessun client con ambiti da far verificare a Google. Finché [isEnabled] è falso, [authorize] risponde
 * [GoogleAccess.Off], [revokeAll] risponde `Ok` e [clearToken] non fa niente: nessuna di queste tocca Google. Accenderlo
 * è una riga dell'app, il giorno in cui un servizio lo chiede davvero (e il progetto Google ha la schermata del
 * consenso verificata per quell'ambito).
 *
 * Entrare e autorizzare restano due flussi separati, come vuole Android: l'accesso è [FluidGoogleSignIn].
 */
object FluidGoogleAccess {

  /** Spento di serie (N6). */
  @Volatile
  var isEnabled: Boolean = false

  /**
   * Quanto vale un token d'accesso di Google: un'ora. L'API non lo dice nel risultato; si conta un po' meno, 55 minuti,
   * così l'app lo rinnova prima che scada (a occhio, 25/11/2026, sulla durata di serie dei token OAuth di Google).
   */
  const val TOKEN_LIFETIME_MS: Long = 55 * 60 * 1000L

  /** Chiede un token per [scopes] (gli indirizzi degli ambiti, per esempio `https://www.googleapis.com/auth/calendar.events`). */
  suspend fun authorize(
    context: Context,
    scopes: Set<String>,
    account: Account? = null,
    labels: FluidAuthLabels = FluidAuthLabels(),
  ): GoogleAccess {
    if (!isEnabled) return GoogleAccess.Off
    if (scopes.isEmpty()) return GoogleAccess.Failed(labels.badRequest)
    if (!hasPlayServices(context)) return GoogleAccess.NoPlayServices
    val builder = AuthorizationRequest.builder().setRequestedScopes(scopes.map { Scope(it) })
    if (account != null) builder.setAccount(account)
    return try {
      result(Identity.getAuthorizationClient(context).authorize(builder.build()).await(), labels)
    } catch (e: CancellationException) {
      throw e
    } catch (e: ApiException) {
      GoogleAccess.Failed(labels.generic)
    }
  }

  /** Dopo il consenso: il risultato dell'intent di [GoogleAccess.NeedsConsent]. */
  fun fromConsent(context: Context, data: Intent?, labels: FluidAuthLabels = FluidAuthLabels()): GoogleAccess {
    if (!isEnabled) return GoogleAccess.Off
    if (data == null) return GoogleAccess.Failed(labels.cancelled)
    return try {
      result(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data), labels)
    } catch (e: ApiException) {
      GoogleAccess.Failed(labels.generic)
    }
  }

  /**
   * Toglie tutte le concessioni di questo progetto per [account] (all'eliminazione dell'account, architettura § 1.6).
   * [scopes] sono quelli concessi; vuoto vuol dire «quelli che Google conosce».
   */
  suspend fun revokeAll(
    context: Context,
    account: Account?,
    scopes: Set<String> = emptySet(),
    labels: FluidAuthLabels = FluidAuthLabels(),
  ): AuthResult<Unit> {
    if (!isEnabled) return AuthResult.Ok(Unit)
    if (account == null) return AuthResult.Failed(labels.badRequest, null)
    if (!hasPlayServices(context)) return AuthResult.Unsupported(AuthResult.Why.NoProvider)
    val builder = RevokeAccessRequest.builder().setAccount(account).setScopes(scopes.map { Scope(it) })
    return try {
      Identity.getAuthorizationClient(context).revokeAccess(builder.build()).await()
      AuthResult.Ok(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: ApiException) {
      AuthResult.Failed(labels.generic, e)
    }
  }

  /** Dimentica un token d'accesso (per esempio dopo un 401): la prossima [authorize] ne chiede uno nuovo. */
  suspend fun clearToken(context: Context, token: String) {
    if (!isEnabled || token.isEmpty() || !hasPlayServices(context)) return
    try {
      Identity.getAuthorizationClient(context).clearToken(ClearTokenRequest.builder().setToken(token).build()).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: ApiException) {
      // Niente da fare: il token scade comunque da solo.
    }
  }

  private fun result(result: AuthorizationResult, labels: FluidAuthLabels): GoogleAccess {
    if (result.hasResolution()) {
      return result.pendingIntent?.let { GoogleAccess.NeedsConsent(it) } ?: GoogleAccess.Failed(labels.generic)
    }
    val token = result.accessToken ?: return GoogleAccess.Failed(labels.generic)
    return GoogleAccess.Token(token, result.grantedScopes.toSet(), SystemClock.elapsedRealtime() + TOKEN_LIFETIME_MS)
  }

  private fun hasPlayServices(context: Context): Boolean =
    GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
}
