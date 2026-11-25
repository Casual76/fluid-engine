package dev.antigravity.fluidengine.auth.google

import android.accounts.Account
import android.app.Activity
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dev.antigravity.fluidengine.auth.AuthResult
import dev.antigravity.fluidengine.auth.FluidAuthLabels
import dev.antigravity.fluidengine.auth.phrase
import dev.antigravity.fluidengine.auth.valueOrNull
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.util.Base64

/** «Continua con Google» e le concessioni spente (scelta N6), senza Google: al posto del gestore uno finto. */
@RunWith(RobolectricTestRunner::class)
class FluidGoogleSignInTest {

  private class FakeGateway : GoogleGateway {
    var error: Throwable? = null
    var response: GetCredentialResponse? = null
    val requests = mutableListOf<GetCredentialRequest>()

    override suspend fun get(activity: Activity, request: GetCredentialRequest): GetCredentialResponse {
      requests += request
      error?.let { throw it }
      return response ?: error("nessuna risposta")
    }
  }

  private val gateway = FakeGateway()
  private val labels = FluidAuthLabels()
  private lateinit var activity: Activity

  // Un JWT finto, non firmato: al modulo non serve leggerlo, lo verifica il server.
  private val fakeJwt = listOf("""{"alg":"RS256"}""", """{"sub":"1234","email":"persona@example.com","nonce":"n0nce"}""", "firma")
    .joinToString(".") { Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray()) }

  @Before
  fun setUp() {
    GoogleGateways.override = gateway
    activity = Robolectric.buildActivity(Activity::class.java).setup().get()
  }

  @After
  fun tearDown() {
    GoogleGateways.override = null
    FluidGoogleAccess.isEnabled = false
  }

  @Test
  fun chiGiaEntratoVedeSoloISuoiAccount() {
    gateway.error = NoCredentialException()
    val result = runBlocking { FluidGoogleSignIn.idToken(activity, "web-client", "n0nce", returning = true) }
    // Nessun account già entrato: niente foglio, e lo Store mostra il tasto.
    assertEquals(AuthResult.NoCredential, result)
    val option = gateway.requests.single().credentialOptions.single() as GetGoogleIdOption
    assertTrue(option.filterByAuthorizedAccounts)
    assertTrue(option.autoSelectEnabled)
    assertEquals("n0nce", option.nonce)
    assertEquals("web-client", option.serverClientId)
  }

  @Test
  fun ilTastoMostraTuttiGliAccount() {
    gateway.response = GetCredentialResponse(
      GoogleIdTokenCredential.Builder().setId("persona@example.com").setIdToken(fakeJwt).setDisplayName("Persona").build(),
    )
    val result = runBlocking { FluidGoogleSignIn.idToken(activity, "web-client", "n0nce", returning = false) }
    val option = gateway.requests.single().credentialOptions.single() as GetSignInWithGoogleOption
    assertEquals("n0nce", option.nonce)
    assertEquals("web-client", option.serverClientId)
    val token = result.valueOrNull()!!
    assertEquals(fakeJwt, token.idToken())
    assertEquals("persona@example.com", token.email)
    assertEquals("Persona", token.displayName)
    // Né il token né l'email finiscono in un log.
    assertFalse(token.toString().contains(fakeJwt))
    assertFalse(token.toString().contains("persona@"))
  }

  @Test
  fun gliErroriHannoLaLoroFrase() {
    gateway.error = GetCredentialCancellationException()
    val cancelled = runBlocking { FluidGoogleSignIn.idToken(activity, "web-client", "n0nce", returning = false) }
    assertEquals(AuthResult.Cancelled, cancelled)
    assertEquals(labels.cancelled, cancelled.phrase())
    gateway.error = null
    gateway.response = GetCredentialResponse(PasswordCredential("x", "y"))
    val wrongType = runBlocking { FluidGoogleSignIn.idToken(activity, "web-client", "n0nce", returning = false) }
    assertEquals(labels.badRequest, wrongType.phrase())
    val noNonce = runBlocking { FluidGoogleSignIn.idToken(activity, "web-client", " ", returning = false) }
    assertEquals(labels.badRequest, noNonce.phrase())
    assertEquals(2, gateway.requests.size)
  }

  @Test
  fun leConcessioniSonoSpente() {
    // N6: scritte ma spente. Nessuna chiamata tocca Google.
    assertFalse(FluidGoogleAccess.isEnabled)
    val account = Account("persona@example.com", "com.google")
    runBlocking {
      assertEquals(GoogleAccess.Off, FluidGoogleAccess.authorize(activity, setOf("https://www.googleapis.com/auth/calendar.events"), account))
      assertEquals(AuthResult.Ok(Unit), FluidGoogleAccess.revokeAll(activity, account))
      FluidGoogleAccess.clearToken(activity, "token")
    }
    assertEquals(GoogleAccess.Off, FluidGoogleAccess.fromConsent(activity, null))
  }

  @Test
  fun accesoSenzaAmbitiEUnErrore() {
    FluidGoogleAccess.isEnabled = true
    val result = runBlocking { FluidGoogleAccess.authorize(activity, emptySet()) }
    assertEquals(GoogleAccess.Failed(labels.badRequest), result)
  }
}
