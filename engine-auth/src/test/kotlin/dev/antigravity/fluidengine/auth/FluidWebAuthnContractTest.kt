package dev.antigravity.fluidengine.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

/**
 * Le conversioni JSON di WebAuthn contro il server dell'Account Pampa (F10.1).
 *
 * Due fonti, tutte e due copiate in `src/test/resources/pampa-account/` (il README lì dice da dove):
 * - `contract/v1/`, i JSON del contratto del server al commit `f4f23b2`: le forme di richieste e risposte;
 * - `webauthn-vectors.json`, fatto da `engine-auth/tools/webauthn-vectors.mjs` con le funzioni vere del server e il suo
 *   autenticatore finto: opzioni come le manda il server, risposte come le dà Credential Manager (col PRF dentro), e le
 *   stesse senza il PRF, **verificate dal server**; più il motivo per cui il server rifiuta quelle col PRF.
 *
 * Il punto: quello che il modulo spedisce è esattamente ciò che il server ha accettato, e il PRF non esce mai.
 */
class FluidWebAuthnContractTest {

  private val vectors = resourceJson("pampa-account/webauthn-vectors.json").jsonObject
  private val cases = vectors["cases"]!!.jsonArray.map { it.jsonObject }
  private val examples = resourceJson("pampa-account/contract/v1/examples.json").jsonObject
  private val pampaPrf = b64(MessageDigest.getInstance("SHA-256").digest("pampa/v1/prf".toByteArray()))

  @Test
  fun iVettoriSonoQuelliDelServer() {
    assertEquals("f4f23b2", vectors["source"]!!.jsonObject["commit"]!!.jsonPrimitive.content)
    assertEquals("pampa-apps.com", vectors["rpId"]!!.jsonPrimitive.content)
    assertEquals("android:apk-key-hash:glc4UAzpYFQp_7o8QBkij5kvLDfRtwoiMKfOLCVjfkM", origin)
    assertEquals(3, cases.size)
    for (c in cases) {
      // Il server rifiuta la risposta col PRF dentro, alla creazione e all'accesso: la prova che va tolto davvero.
      for (step in listOf("creation", "assertion")) {
        val rejected = c[step]!!.jsonObject["rawRejected"]
        assertTrue("${c.name}/$step: il server deve rifiutare il PRF", rejected is JsonPrimitive && rejected.isString)
      }
    }
  }

  @Test
  fun laCostanteDelPrfEQuellaDiChiavi() {
    assertEquals(pampaPrf, FluidPrfInput.PampaV1.base64Url)
    assertEquals("pampa/v1/prf", FluidPrfInput.PAMPA_V1_LABEL)
    assertEquals(32, FluidPrfInput.PampaV1.bytes().size)
    assertEquals(FluidPrfInput.PampaV1, FluidPrfInput.fromLabel("pampa/v1/prf"))
  }

  @Test
  fun laCreazioneTieneLeOpzioniDelServerEScriveIlSuoPrf() {
    for (c in cases) {
      val options = c["registrationOptions"]!!.jsonObject
      val out = parse(FluidWebAuthnJson.creationRequest(options.toString(), FluidPrfInput.PampaV1))
      // Tutto quello che il server ha scritto c'è, uguale e nello stesso ordine; in più solo extensions.prf.
      assertEquals(options.keys.toList(), out.keys.toList())
      for (key in options.keys - "extensions") assertEquals("${c.name}: $key", options[key], out[key])
      val extensions = out["extensions"]!!.jsonObject
      assertEquals(JsonPrimitive(true), extensions["credProps"])
      assertEquals(prfEval(pampaPrf), extensions["prf"])
    }
  }

  @Test
  fun allaCreazioneSiPuoChiedereSoloSeCeIlPrf() {
    val options = cases[0]["registrationOptions"]!!.jsonObject
    val out = parse(FluidWebAuthnJson.creationRequest(options.toString(), FluidPrfInput.PampaV1, evaluatePrfAtCreation = false))
    assertEquals(JsonObject(emptyMap()), out["extensions"]!!.jsonObject["prf"])
  }

  @Test
  fun ilPrfDelServerNonPassaMai() {
    // Critica S9: un server ostile manda un ingresso suo e un evalByCredential. Il modulo li butta e scrive il suo.
    val hostile = """{"eval":{"first":"AAAA","second":"BBBB"},"evalByCredential":{"K2l3J0Ek_kUuwXDlw89gYg":{"first":"CCCC"}}}"""
    for (c in cases) {
      val reg = withExtension(c["registrationOptions"]!!.jsonObject, hostile)
      val regOut = parse(FluidWebAuthnJson.creationRequest(reg.toString(), FluidPrfInput.PampaV1))
      assertEquals(prfEval(pampaPrf), regOut["extensions"]!!.jsonObject["prf"])
      val auth = withExtension(c["authenticationOptions"]!!.jsonObject, hostile)
      val authOut = parse(FluidWebAuthnJson.assertionRequest(auth.toString(), FluidPrfInput.PampaV1))
      assertEquals(prfEval(pampaPrf), authOut["extensions"]!!.jsonObject["prf"])
      assertFalse(authOut.toString().contains("evalByCredential"))
      assertFalse(authOut.toString().contains("CCCC"))
      // Senza PRF del modulo, quello del server si toglie lo stesso.
      val none = parse(FluidWebAuthnJson.assertionRequest(auth.toString(), prf = null))
      assertNull(none["extensions"])
    }
  }

  @Test
  fun lAccessoTieneLeOpzioniDelServer() {
    for (c in cases) {
      val options = c["authenticationOptions"]!!.jsonObject
      val out = parse(FluidWebAuthnJson.assertionRequest(options.toString(), FluidPrfInput.PampaV1))
      for (key in options.keys) assertEquals("${c.name}: $key", options[key], out[key])
      assertEquals(options.keys.toList() + "extensions", out.keys.toList())
      assertEquals(prfEval(pampaPrf), out["extensions"]!!.jsonObject["prf"])
      assertEquals("pampa-apps.com", out["rpId"]!!.jsonPrimitive.content)
      assertEquals("required", out["userVerification"]!!.jsonPrimitive.content)
    }
  }

  @Test
  fun laRispostaDellaCreazioneDiventaQuellaCheIlServerHaVerificato() {
    for (c in cases) {
      val creation = c["creation"]!!.jsonObject
      val result = FluidWebAuthnJson.readCreation(creation["credentialManager"]!!.toString())
      // Uguale, campo per campo, alla risposta che il server ha accettato.
      assertEquals(c.name, creation["forServer"], parse(result.responseJson))
      assertTrue(FluidWebAuthnJson.hasPrfOutput(creation["credentialManager"]!!.toString()))
      assertFalse(FluidWebAuthnJson.hasPrfOutput(result.responseJson))
      // Il PRF resta sul telefono, ed è quello del gestore.
      assertArrayEquals(unb64(creation["prfFirst"]!!.jsonPrimitive.content), result.prfOutput())
      assertEquals(true, result.prfEnabled)
      // I flag e l'AAGUID come li ha letti il server.
      val server = creation["server"]!!.jsonObject
      val data = result.authenticatorData
      assertTrue(data.userPresent)
      assertTrue(data.userVerified)
      assertTrue(data.hasAttestedCredential)
      assertEquals(server["backupEligible"]!!.jsonPrimitive.boolean, data.backupEligible)
      assertEquals(server["backedUp"]!!.jsonPrimitive.boolean, data.backedUp)
      assertEquals(server["aaguid"]!!.jsonPrimitive.content, data.aaguid)
      assertEquals(server["credentialId"]!!.jsonPrimitive.content, result.credentialId)
      assertEquals(result.credentialId, data.credentialId)
      assertEquals(0L, data.signCount)
      assertTrue(data.isFor("pampa-apps.com"))
      assertFalse(data.isFor("prova.pampa-apps.com"))
      assertEquals(origin, result.origin)
      assertEquals(listOf("internal", "hybrid"), result.transports)
      // E nessun segreto nel testo che stampa.
      assertFalse(result.toString().contains(creation["prfFirst"]!!.jsonPrimitive.content))
    }
  }

  @Test
  fun laRispostaDellAccessoDiventaQuellaCheIlServerHaVerificato() {
    for (c in cases) {
      val assertion = c["assertion"]!!.jsonObject
      val result = FluidWebAuthnJson.readAssertion(assertion["credentialManager"]!!.toString())
      assertEquals(c.name, assertion["forServer"], parse(result.responseJson))
      assertFalse(FluidWebAuthnJson.hasPrfOutput(result.responseJson))
      assertArrayEquals(unb64(assertion["prfFirst"]!!.jsonPrimitive.content), result.prfOutput())
      assertNull(result.prfEnabled)
      assertEquals(assertion["server"]!!.jsonObject["backedUp"]!!.jsonPrimitive.boolean, result.authenticatorData.backedUp)
      assertEquals(c["synced"]!!.jsonPrimitive.boolean, result.authenticatorData.backupEligible)
      assertNull(result.authenticatorData.aaguid)
      assertEquals(c["registrationOptions"]!!.jsonObject["user"]!!.jsonObject["id"], JsonPrimitive(result.userHandle))
      assertEquals(c["creation"]!!.jsonObject["server"]!!.jsonObject["credentialId"]!!.jsonPrimitive.content, result.credentialId)
      assertTrue(result.authenticatorData.isFor("pampa-apps.com"))
    }
  }

  @Test
  fun dueAccessiConLoStessoPrfDannoLoStessoValore() {
    // Il gestore dà lo stesso valore a ogni accesso: il modulo non lo altera, e lo restituisce uguale.
    val raw = cases[0]["assertion"]!!.jsonObject["credentialManager"]!!.toString()
    val first = FluidWebAuthnJson.readAssertion(raw).prfOutput()
    val second = FluidWebAuthnJson.readAssertion(raw).prfOutput()
    assertArrayEquals(first, second)
    assertEquals(32, first!!.size)
  }

  @Test
  fun laRispostaPulitaHaLaFormaDelContratto() {
    // examples.json: cosa manda uno Store. Ogni campo dell'esempio c'è, dello stesso tipo JSON.
    val registerExample = examples["POST /v1/passkeys/register/verify"]!!.jsonObject["request"]!!.jsonObject["credential"]!!.jsonObject
    val loginExample = examples["POST /v1/passkeys/login/verify"]!!.jsonObject["request"]!!.jsonObject["credential"]!!.jsonObject
    for (c in cases) {
      val created = parse(FluidWebAuthnJson.readCreation(c["creation"]!!.jsonObject["credentialManager"]!!.toString()).responseJson)
      assertShape("${c.name} creazione", registerExample, created)
      assertEquals(true, created["clientExtensionResults"]!!.jsonObject["prf"]!!.jsonObject["enabled"]!!.jsonPrimitive.booleanOrNull)
      val asserted = parse(FluidWebAuthnJson.readAssertion(c["assertion"]!!.jsonObject["credentialManager"]!!.toString()).responseJson)
      assertShape("${c.name} accesso", loginExample, asserted)
      // Le chiavi che il server rifiuta in qualunque richiesta (src/guards.ts, FORBIDDEN_KEYS, al commit f4f23b2).
      for (json in listOf(created, asserted)) assertNoForbiddenKeys(json)
    }
  }

  @Test
  fun leOpzioniDiEsempioDelContrattoSiLeggono() {
    val options = examples["POST /v1/passkeys/register/options"]!!.jsonObject["response"]!!.jsonObject["options"]!!.jsonObject
    val out = parse(FluidWebAuthnJson.creationRequest(options.toString(), FluidPrfInput.PampaV1))
    assertEquals(options["rp"], out["rp"])
    assertEquals(options["user"], out["user"])
    assertEquals(-8, out["pubKeyCredParams"]!!.jsonArray[0].jsonObject["alg"]!!.jsonPrimitive.int)
  }

  @Test
  fun unJsonStortoEUnErroreChiaro() {
    val bad = listOf(
      "non è json",
      "[]",
      """{"rp":{},"user":{"id":"AA"},"pubKeyCredParams":[]}""",
      """{"challenge":"AA","rp":{},"user":{},"pubKeyCredParams":[]}""",
      """{"challenge":"AA","rp":{},"user":{"id":"AA"}}""",
      """{"challenge":"AA","rp":{},"user":{"id":"AA"},"pubKeyCredParams":[],"extensions":"prf"}""",
    )
    for (json in bad) {
      try {
        FluidWebAuthnJson.creationRequest(json, FluidPrfInput.PampaV1)
        fail("doveva rifiutare: $json")
      } catch (e: FluidWebAuthnJsonException) {
        assertNotNull(e.message)
      }
    }
    val goodResponse = cases[0]["assertion"]!!.jsonObject["credentialManager"]!!.jsonObject
    val badResponses = listOf(
      JsonObject(goodResponse - "id"),
      JsonObject(goodResponse + ("type" to JsonPrimitive("password"))),
      JsonObject(goodResponse + ("response" to JsonObject(goodResponse["response"]!!.jsonObject - "authenticatorData"))),
      JsonObject(goodResponse + ("response" to JsonObject(goodResponse["response"]!!.jsonObject - "signature"))),
      JsonObject(goodResponse + ("response" to JsonObject(goodResponse["response"]!!.jsonObject + ("authenticatorData" to JsonPrimitive("AAAA"))))),
    )
    for (json in badResponses) {
      try {
        FluidWebAuthnJson.readAssertion(json.toString())
        fail("doveva rifiutare: $json")
      } catch (e: FluidWebAuthnJsonException) {
        assertNotNull(e.message)
      }
    }
  }

  // --------------------------------------------------------------------------------------------------------------

  private val origin: String get() = vectors["origin"]!!.jsonPrimitive.content

  private val JsonObject.name: String get() = this["name"]!!.jsonPrimitive.content

  private fun prfEval(first: String): JsonObject =
    JsonObject(mapOf("eval" to JsonObject(mapOf("first" to JsonPrimitive(first)))))

  private fun withExtension(options: JsonObject, prf: String): JsonObject {
    val extensions = (options["extensions"] as? JsonObject).orEmpty() + ("prf" to Json.parseToJsonElement(prf))
    return JsonObject(options + ("extensions" to JsonObject(extensions)))
  }

  /** Come `test/shape.ts` del server: ogni campo dell'esempio c'è, dello stesso tipo; `null` vuol dire «può mancare». */
  private fun assertShape(where: String, example: JsonElement, actual: JsonElement?) {
    if (example is JsonNull) return
    assertNotNull("$where: manca", actual)
    when (example) {
      is JsonObject -> {
        assertTrue("$where: non è un oggetto", actual is JsonObject)
        for ((key, value) in example) assertShape("$where.$key", value, (actual as JsonObject)[key])
      }
      is JsonArray -> assertTrue("$where: non è un array", actual is JsonArray)
      is JsonPrimitive -> {
        assertTrue("$where: non è un valore semplice", actual is JsonPrimitive)
        actual as JsonPrimitive
        when {
          example.isString -> assertTrue("$where: non è una stringa", actual.isString)
          example.booleanOrNull != null -> assertNotNull("$where: non è un booleano", actual.booleanOrNull)
          else -> assertFalse("$where: non è un numero", actual.isString)
        }
      }
    }
  }

  private fun assertNoForbiddenKeys(element: JsonElement) {
    val forbidden = setOf("code", "recoverycode", "recovery_code", "seed", "doorseed", "door_seed", "prfoutput")
    when (element) {
      is JsonObject -> element.forEach { (key, value) ->
        assertFalse("chiave vietata: $key", key.lowercase() in forbidden)
        assertNoForbiddenKeys(value)
      }
      is JsonArray -> element.forEach { assertNoForbiddenKeys(it) }
      else -> Unit
    }
  }

  private fun parse(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

  private fun b64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

  private fun unb64(text: String): ByteArray = Base64.getUrlDecoder().decode(text)

  private fun resourceJson(path: String): JsonElement {
    val stream = javaClass.classLoader!!.getResourceAsStream(path) ?: error("Manca la risorsa $path")
    return Json.parseToJsonElement(stream.use { it.readBytes().toString(Charsets.UTF_8) })
  }
}
