package dev.antigravity.fluidengine.auth

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import androidx.credentials.CreateCredentialRequest
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CreatePasswordResponse
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PasswordCredential
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialInterruptedException
import androidx.credentials.exceptions.CreateCredentialNoCreateOptionException
import androidx.credentials.exceptions.CreateCredentialProviderConfigurationException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.CreateCredentialUnsupportedException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.domerrors.InvalidStateError
import androidx.credentials.exceptions.domerrors.NotAllowedError
import androidx.credentials.exceptions.domerrors.SecurityError
import androidx.credentials.exceptions.domerrors.TimeoutError
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import androidx.credentials.exceptions.publickeycredential.GetPublicKeyCredentialDomException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Gli errori di Credential Manager, ognuno con la sua frase pronta (B § 9.2). Robolectric dà l'Activity, la versione di
 * Android e il blocco schermo veri; al posto del gestore c'è uno finto che lancia le eccezioni vere di
 * `androidx.credentials` 1.6.0.
 */
@RunWith(RobolectricTestRunner::class)
class FluidPasskeysErrorsTest {

  private class FakeGateway : CredentialGateway {
    var createError: Throwable? = null
    var getError: Throwable? = null
    var createResponse: CreateCredentialResponse? = null
    var getResponse: GetCredentialResponse? = null
    val created = mutableListOf<CreateCredentialRequest>()
    val got = mutableListOf<GetCredentialRequest>()

    override suspend fun create(activity: Activity, request: CreateCredentialRequest): CreateCredentialResponse {
      created += request
      createError?.let { throw it }
      return createResponse ?: error("nessuna risposta preparata")
    }

    override suspend fun get(activity: Activity, request: GetCredentialRequest): GetCredentialResponse {
      got += request
      getError?.let { throw it }
      return getResponse ?: error("nessuna risposta preparata")
    }
  }

  private val gateway = FakeGateway()
  private val labels = FluidAuthLabels()
  private lateinit var activity: Activity

  private val vectors = javaClass.classLoader!!.getResourceAsStream("pampa-account/webauthn-vectors.json")!!
    .use { Json.parseToJsonElement(it.readBytes().toString(Charsets.UTF_8)).jsonObject }
  private val case0 = vectors["cases"]!!.jsonArray[0].jsonObject
  private val registrationOptions = case0["registrationOptions"].toString()
  private val authenticationOptions = case0["authenticationOptions"].toString()

  @Before
  fun setUp() {
    CredentialGateways.override = gateway
    activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    screenLock(true)
  }

  @After
  fun tearDown() {
    CredentialGateways.override = null
  }

  private fun screenLock(on: Boolean) {
    val keyguard = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    shadowOf(keyguard).setIsDeviceSecure(on)
  }

  private fun create(): AuthResult<FluidPasskeyResult> = runBlocking { FluidPasskeys.create(activity, registrationOptions) }

  private fun get(): AuthResult<FluidPasskeyResult> = runBlocking { FluidPasskeys.get(activity, authenticationOptions) }

  // --- i quattro casi del brief ---------------------------------------------------------------------------------

  @Test
  fun annullato() {
    gateway.createError = CreateCredentialCancellationException()
    assertEquals(AuthResult.Cancelled, create())
    assertEquals("Operazione annullata.", create().phrase())
    gateway.getError = GetCredentialCancellationException()
    assertEquals(AuthResult.Cancelled, get())
    assertEquals(labels.cancelled, get().phrase(labels))
    // In WebAuthn anche NotAllowedError vuol dire «annullato» (o tempo scaduto): niente frase d'errore.
    gateway.createError = CreatePublicKeyCredentialDomException(NotAllowedError(), "l'utente ha chiuso")
    assertEquals(AuthResult.Cancelled, create())
    gateway.getError = GetPublicKeyCredentialDomException(NotAllowedError())
    assertEquals(AuthResult.Cancelled, get())
  }

  @Test
  fun nessunaCredenziale() {
    gateway.getError = NoCredentialException()
    val result = get()
    assertEquals(AuthResult.NoCredential, result)
    assertEquals("Su questo telefono non trovo una passkey da usare qui.", result.phrase())
    // Per una password la frase cambia.
    val password = runBlocking { FluidPasswords.get(activity) }
    assertEquals(AuthResult.NoCredential, password)
    assertEquals(labels.noPassword, password.phrase(labels, password = true))
  }

  @Test
  @Config(sdk = [27])
  fun versioneVecchia() {
    assertEquals(AuthResult.Why.OldAndroid, FluidPasskeys.unsupportedReason(activity))
    assertFalse(FluidPasskeys.isSupported(activity))
    for (result in listOf(create(), get())) {
      assertEquals(AuthResult.Unsupported(AuthResult.Why.OldAndroid), result)
      assertEquals(labels.oldAndroid, result.phrase())
    }
    // Il foglio non si apre nemmeno.
    assertTrue(gateway.created.isEmpty() && gateway.got.isEmpty())
  }

  @Test
  @Config(sdk = [28])
  fun android9BastaPerLePasskey() {
    assertTrue(FluidPasskeys.isSupported(activity))
  }

  @Test
  fun nessunBloccoSchermo() {
    screenLock(false)
    assertEquals(AuthResult.Why.NoScreenLock, FluidPasskeys.unsupportedReason(activity))
    for (result in listOf(create(), get())) {
      assertEquals(AuthResult.Unsupported(AuthResult.Why.NoScreenLock), result)
      assertEquals(
        "Per usare una passkey serve un blocco schermo. Impostalo nelle impostazioni del telefono, poi riprova.",
        result.phrase(),
      )
    }
    assertTrue(gateway.created.isEmpty() && gateway.got.isEmpty())
  }

  // --- il resto della tabella -----------------------------------------------------------------------------------

  @Test
  fun nessunGestore() {
    val createErrors = listOf(
      CreateCredentialProviderConfigurationException(),
      CreateCredentialUnsupportedException(),
      CreateCredentialNoCreateOptionException(),
    )
    for (e in createErrors) {
      gateway.createError = e
      assertEquals(e.javaClass.simpleName, AuthResult.Unsupported(AuthResult.Why.NoProvider), create())
      assertEquals(labels.noProvider, create().phrase())
    }
    for (e in listOf(GetCredentialProviderConfigurationException(), GetCredentialUnsupportedException())) {
      gateway.getError = e
      assertEquals(e.javaClass.simpleName, AuthResult.Unsupported(AuthResult.Why.NoProvider), get())
    }
  }

  @Test
  fun ilDominioNonRiconosceLApp() {
    // SecurityError: di solito assetlinks.json manca o non ha l'impronta di quest'app.
    gateway.createError = CreatePublicKeyCredentialDomException(SecurityError())
    val created = create() as AuthResult.Failed
    assertEquals(labels.wrongDomain, created.message)
    assertFalse(created.retry)
    gateway.getError = GetPublicKeyCredentialDomException(SecurityError())
    assertEquals(labels.wrongDomain, get().phrase())
  }

  @Test
  fun laPasskeyCeGia() {
    gateway.createError = CreatePublicKeyCredentialDomException(InvalidStateError())
    assertEquals(labels.alreadyExists, create().phrase())
  }

  @Test
  fun interrottoSiRiprova() {
    gateway.createError = CreateCredentialInterruptedException()
    val created = create() as AuthResult.Failed
    assertEquals(labels.interrupted, created.message)
    assertTrue(created.retry)
    gateway.getError = GetCredentialInterruptedException()
    assertTrue((get() as AuthResult.Failed).retry)
    gateway.getError = GetPublicKeyCredentialDomException(TimeoutError())
    assertTrue((get() as AuthResult.Failed).retry)
  }

  @Test
  fun ilRestoEGenerico() {
    gateway.createError = CreateCredentialUnknownException("qualcosa")
    val created = create() as AuthResult.Failed
    assertEquals(labels.generic, created.message)
    assertTrue(created.cause is CreateCredentialUnknownException)
    // La causa non finisce nella frase.
    assertFalse(created.message.contains("qualcosa"))
    gateway.getError = GetCredentialUnknownException("altro")
    assertEquals(labels.generic, get().phrase())
  }

  @Test
  fun unaRichiestaStortaNonApreIlFoglio() {
    val result = runBlocking { FluidPasskeys.create(activity, """{"rp":{}}""") }
    assertEquals(labels.badRequest, result.phrase())
    assertTrue(result is AuthResult.Failed && result.cause is FluidWebAuthnJsonException)
    assertTrue(gateway.created.isEmpty())
  }

  @Test
  fun leFrasiInInglese() {
    val english = FluidAuthLabels.english()
    gateway.getError = NoCredentialException()
    assertEquals("There's no passkey on this phone to use here.", get().phrase(english))
    screenLock(false)
    assertEquals(english.noScreenLock, get().phrase(english))
    // Il blocco schermo si controlla prima del JSON: lo si riaccende per arrivare alla richiesta storta.
    screenLock(true)
    val failed = runBlocking { FluidPasskeys.create(activity, "{}", labels = english) }
    assertEquals(english.badRequest, failed.phrase())
  }

  // --- quando va ----------------------------------------------------------------------------------------------

  @Test
  fun laCreazioneMandaIlPrfDelModuloETornaPulita() {
    val raw = case0["creation"]!!.jsonObject["credentialManager"].toString()
    gateway.createResponse = CreatePublicKeyCredentialResponse(raw)
    // Il server prova a mettere un suo PRF: non arriva al gestore.
    val hostile = Json.parseToJsonElement(registrationOptions).jsonObject.let { o ->
      JsonObject(o + ("extensions" to Json.parseToJsonElement("""{"prf":{"eval":{"first":"AAAA"}}}""")))
    }
    val result = runBlocking { FluidPasskeys.create(activity, hostile.toString()) }
    val sent = Json.parseToJsonElement((gateway.created.single() as CreatePublicKeyCredentialRequest).requestJson).jsonObject
    assertEquals(
      FluidPrfInput.PampaV1.base64Url,
      sent["extensions"]!!.jsonObject["prf"]!!.jsonObject["eval"]!!.jsonObject["first"].toString().trim('"'),
    )
    val ok = (result as AuthResult.Ok).value
    assertFalse(FluidWebAuthnJson.hasPrfOutput(ok.responseJson))
    assertTrue(ok.hasPrfOutput)
    assertTrue(ok.authenticatorData.backedUp)
    assertEquals(null, result.phrase())
  }

  @Test
  fun lAccessoMandaIlPrfDelModuloETornaPulito() {
    val raw = case0["assertion"]!!.jsonObject["credentialManager"].toString()
    gateway.getResponse = GetCredentialResponse(PublicKeyCredential(raw))
    val result = runBlocking { FluidPasskeys.get(activity, authenticationOptions, onlyOnThisPhone = true) }
    val request = gateway.got.single()
    assertTrue(request.preferImmediatelyAvailableCredentials)
    val option = request.credentialOptions.single() as GetPublicKeyCredentialOption
    assertTrue(option.requestJson.contains(FluidPrfInput.PampaV1.base64Url))
    val ok = result.valueOrNull()!!
    assertFalse(FluidWebAuthnJson.hasPrfOutput(ok.responseJson))
    assertEquals(32, ok.prfOutput()!!.size)
  }

  @Test
  fun ilCodiceDiRecuperoNelGestore() {
    gateway.createResponse = CreatePasswordResponse()
    val saved = runBlocking { FluidPasswords.save(activity, "Codice di recupero Pampa", "ABCDE-FGHIJ") }
    assertEquals(AuthResult.Ok(Unit), saved)
    val request = gateway.created.single() as CreatePasswordRequest
    assertEquals("Codice di recupero Pampa", request.id)
    gateway.getResponse = GetCredentialResponse(PasswordCredential("Codice di recupero Pampa", "ABCDE-FGHIJ"))
    val read = runBlocking { FluidPasswords.get(activity, setOf("Codice di recupero Pampa")) }.valueOrNull()!!
    assertEquals("ABCDE-FGHIJ", read.secret())
    assertFalse(read.toString().contains("ABCDE"))
    gateway.createError = CreateCredentialCancellationException()
    assertEquals(AuthResult.Cancelled, runBlocking { FluidPasswords.save(activity, "x", "y") })
    assertEquals(labels.badRequest, runBlocking { FluidPasswords.save(activity, " ", "y") }.phrase())
  }
}
