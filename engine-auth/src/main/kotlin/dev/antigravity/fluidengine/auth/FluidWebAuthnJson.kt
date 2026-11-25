package dev.antigravity.fluidengine.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** Un JSON WebAuthn che non si legge, o che manca di un campo obbligatorio. Non contiene mai i valori segreti. */
class FluidWebAuthnJsonException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/**
 * Le conversioni JSON di WebAuthn fra il server e Credential Manager, senza Android: si provano sulla JVM.
 *
 * Il server (Account Pampa, `@simplewebauthn/server`) manda le opzioni già nel formato JSON di WebAuthn, quello che
 * Credential Manager vuole in `CreatePublicKeyCredentialRequest` e `GetPublicKeyCredentialOption`
 * ([create passkeys](https://developer.android.com/identity/passkeys/create-passkeys), fiducia alta). Quindi la
 * conversione è piccola, ed è tutta sul PRF:
 * - **all'andata** ([creationRequest], [assertionRequest]) toglie qualunque `extensions.prf` arrivi dal server,
 *   compreso `evalByCredential`, e scrive il suo ([FluidPrfInput], critica S9). Il resto passa com'è, nello stesso
 *   ordine;
 * - **al ritorno** ([readCreation], [readAssertion]) legge flag, AAGUID e uscita del PRF, e toglie i risultati del PRF
 *   dalla risposta da spedire (`chiavi.md` § 3.2, K6). Resta `prf.enabled`, che al server serve.
 *
 * I test di questo modulo lo provano contro il contratto del server (`contract/v1`) e contro risposte che il server ha
 * verificato davvero (`webauthn-vectors.json`, fatti con `engine-auth/tools/webauthn-vectors.mjs`).
 */
object FluidWebAuthnJson {
  private val json = Json

  /**
   * Le opzioni di creazione per Credential Manager. [optionsJson] è l'oggetto `options` della risposta del server
   * (`challenge`, `rp`, `user`, `pubKeyCredParams`…). Con [prf] il modulo scrive `extensions.prf`: con
   * [evaluatePrfAtCreation] chiede già il valore (`eval.first`), altrimenti chiede solo se c'è (`prf: {}`). Senza
   * [prf] non chiede il PRF, ma toglie comunque quello del server.
   */
  fun creationRequest(optionsJson: String, prf: FluidPrfInput?, evaluatePrfAtCreation: Boolean = true): String {
    val options = parseObject(optionsJson, "le opzioni di creazione")
    string(options, "challenge")
    obj(options, "rp")
    string(obj(options, "user"), "id")
    if (options["pubKeyCredParams"] !is JsonArray) throw FluidWebAuthnJsonException("Mancano «pubKeyCredParams»")
    return encode(withPrf(options, prf?.let { prfRequest(it, evaluate = evaluatePrfAtCreation) }))
  }

  /** Le opzioni di accesso per Credential Manager, con l'estensione PRF del modulo al posto di quella del server. */
  fun assertionRequest(optionsJson: String, prf: FluidPrfInput?): String {
    val options = parseObject(optionsJson, "le opzioni di accesso")
    string(options, "challenge")
    return encode(withPrf(options, prf?.let { prfRequest(it, evaluate = true) }))
  }

  /** Legge la risposta di Credential Manager a una creazione (`registrationResponseJson`). */
  fun readCreation(responseJson: String): FluidPasskeyResult {
    val root = parseObject(responseJson, "la risposta della creazione")
    val id = credentialShape(root)
    val response = obj(root, "response")
    val authData = (response["authenticatorData"] as? JsonPrimitive)?.contentOrNull
      ?.let { FluidAuthenticatorData.parse(Base64Url.decode(it)) }
      ?: FluidAuthenticatorData.fromAttestationObject(Base64Url.decode(string(response, "attestationObject")))
    val stripped = stripPrf(root)
    val transports = (response["transports"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    return FluidPasskeyResult(
      credentialId = id,
      responseJson = encode(stripped.clean),
      authenticatorData = authData,
      prfEnabled = stripped.enabled,
      prfFirst = stripped.first,
      transports = transports,
      userHandle = null,
      origin = origin(response),
    )
  }

  /** Legge la risposta di Credential Manager a un accesso (`authenticationResponseJson`). */
  fun readAssertion(responseJson: String): FluidPasskeyResult {
    val root = parseObject(responseJson, "la risposta dell'accesso")
    val id = credentialShape(root)
    val response = obj(root, "response")
    val authData = FluidAuthenticatorData.parse(Base64Url.decode(string(response, "authenticatorData")))
    string(response, "signature")
    val stripped = stripPrf(root)
    return FluidPasskeyResult(
      credentialId = id,
      responseJson = encode(stripped.clean),
      authenticatorData = authData,
      prfEnabled = stripped.enabled,
      prfFirst = stripped.first,
      transports = emptyList(),
      userHandle = (response["userHandle"] as? JsonPrimitive)?.contentOrNull,
      origin = origin(response),
    )
  }

  /**
   * Vero se un JSON contiene ancora un'uscita del PRF (`prf.results`, o il vecchio `hmacGetSecret`) in un punto
   * qualunque. Per un banco che guarda ogni richiesta in uscita (`chiavi.md` § 11, punto 9).
   */
  fun hasPrfOutput(anyJson: String): Boolean {
    val root = try {
      json.parseToJsonElement(anyJson)
    } catch (e: Exception) {
      return false
    }
    return containsPrfOutput(root, 0)
  }

  // ---------------------------------------------------------------------------------------------------------------

  private class Stripped(val clean: JsonObject, val enabled: Boolean?, val first: ByteArray?)

  private fun prfRequest(input: FluidPrfInput, evaluate: Boolean): JsonObject =
    if (evaluate) {
      JsonObject(mapOf("eval" to JsonObject(mapOf("first" to JsonPrimitive(input.base64Url)))))
    } else {
      JsonObject(emptyMap())
    }

  private fun withPrf(options: JsonObject, prf: JsonObject?): JsonObject {
    val current = options["extensions"]
    if (current != null && current !is JsonObject) throw FluidWebAuthnJsonException("«extensions» non è un oggetto")
    val extensions = LinkedHashMap((current as JsonObject?).orEmpty())
    extensions.remove("prf")
    if (prf != null) extensions["prf"] = prf
    val out = LinkedHashMap<String, JsonElement>(options)
    if (extensions.isEmpty()) out.remove("extensions") else out["extensions"] = JsonObject(extensions)
    return JsonObject(out)
  }

  /** Toglie `prf.results` (e `prf` se resta vuoto, e il vecchio `hmacGetSecret`); tiene `prf.enabled`. */
  private fun stripPrf(root: JsonObject): Stripped {
    val ext = root["clientExtensionResults"] as? JsonObject ?: return Stripped(root, null, null)
    val prf = ext["prf"] as? JsonObject
    val enabled = (prf?.get("enabled") as? JsonPrimitive)?.booleanOrNull
    val first = ((prf?.get("results") as? JsonObject)?.get("first") as? JsonPrimitive)?.contentOrNull
      ?.let { Base64Url.decode(it) }
    val cleanExt = LinkedHashMap<String, JsonElement>(ext)
    cleanExt.remove("hmacGetSecret")
    if (prf != null) {
      val cleanPrf = LinkedHashMap<String, JsonElement>(prf)
      cleanPrf.remove("results")
      if (cleanPrf.isEmpty()) cleanExt.remove("prf") else cleanExt["prf"] = JsonObject(cleanPrf)
    }
    val out = LinkedHashMap<String, JsonElement>(root)
    out["clientExtensionResults"] = JsonObject(cleanExt)
    return Stripped(JsonObject(out), enabled, first)
  }

  private fun containsPrfOutput(element: JsonElement, depth: Int): Boolean {
    if (depth > 16) return false
    return when (element) {
      is JsonObject -> element.any { (key, value) ->
        (key == "hmacGetSecret") ||
          (key == "prf" && value is JsonObject && "results" in value) ||
          containsPrfOutput(value, depth + 1)
      }
      is JsonArray -> element.any { containsPrfOutput(it, depth + 1) }
      else -> false
    }
  }

  private fun credentialShape(root: JsonObject): String {
    val id = string(root, "id")
    Base64Url.decode(id)
    val type = (root["type"] as? JsonPrimitive)?.contentOrNull
    if (type != "public-key") throw FluidWebAuthnJsonException("«type» non è public-key")
    string(obj(root, "response"), "clientDataJSON")
    return id
  }

  private fun origin(response: JsonObject): String? = try {
    val clientData = Base64Url.decode(string(response, "clientDataJSON")).toString(Charsets.UTF_8)
    ((json.parseToJsonElement(clientData) as? JsonObject)?.get("origin") as? JsonPrimitive)?.contentOrNull
  } catch (e: Exception) {
    null
  }

  private fun parseObject(text: String, what: String): JsonObject {
    val element = try {
      json.parseToJsonElement(text)
    } catch (e: Exception) {
      throw FluidWebAuthnJsonException("Non è JSON: $what", e)
    }
    return element as? JsonObject ?: throw FluidWebAuthnJsonException("Non è un oggetto JSON: $what")
  }

  private fun obj(parent: JsonObject, key: String): JsonObject =
    parent[key] as? JsonObject ?: throw FluidWebAuthnJsonException("Manca «$key»")

  private fun string(parent: JsonObject, key: String): String {
    val value = (parent[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    if (value.isNullOrEmpty()) throw FluidWebAuthnJsonException("Manca «$key»")
    return value
  }

  private fun encode(element: JsonElement): String = json.encodeToString(JsonElement.serializer(), element)
}
