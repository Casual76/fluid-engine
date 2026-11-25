package dev.antigravity.fluidengine.account.crypto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * La busta di ogni dato cifrato dell'account (`b\chiavi.md` § 9; contratto `e2e.json`, `envelope`):
 *
 * ```
 * { "v": 1, "e": <epoca>, "alg": "A256GCM", "n": <nonce 12 byte, base64url>, "c": <testo cifrato con tag, base64url> }
 * ```
 *
 * Sul server viaggia come base64url dei byte UTF-8 di quel JSON, nel campo `ciphertext` di una riga. La busta dice con
 * che epoca è stata chiusa, così chi la apre sceglie la chiave giusta anche dopo tre rotazioni; i dati associati
 * (di chi è e dove sta, K4) non stanno nella busta: li ricostruisce chi la apre, e se il server l'ha spostata la
 * busta non si apre.
 */
class PampaEnvelope(val epoch: Int, val nonce: ByteArray, val ciphertext: ByteArray) {

  /** Il JSON della busta, con i campi nell'ordine del contratto. */
  fun toJson(): String = buildJsonObject {
    put("v", VERSION)
    put("e", epoch)
    put("alg", ALG)
    put("n", PampaBase64.encode(nonce))
    put("c", PampaBase64.encode(ciphertext))
  }.toString()

  /** Il valore del campo `ciphertext` sul server: base64url del JSON in UTF-8. */
  fun toField(): String = PampaBase64.encode(toJson().toByteArray(Charsets.UTF_8))

  companion object {
    const val VERSION = 1
    const val ALG = "A256GCM"

    /** Chiude [plaintext] con [key] all'epoca [epoch], legandolo ad [aad]. Il nonce è casuale, 96 bit. */
    fun seal(key: ByteArray, epoch: Int, plaintext: ByteArray, aad: ByteArray): PampaEnvelope {
      val nonce = PampaCrypto.random(PampaCrypto.NONCE_BYTES)
      return PampaEnvelope(epoch, nonce, PampaCrypto.aesGcmSeal(key, nonce, plaintext, aad))
    }

    /** Legge una busta dal campo `ciphertext` del server; [PampaCryptoException] se non è una busta di versione 1. */
    fun parseField(field: String): PampaEnvelope = parseJson(
      runCatching { PampaBase64.decode(field).toString(Charsets.UTF_8) }.getOrElse { throw PampaCryptoException("busta illeggibile", it) },
    )

    fun parseJson(json: String): PampaEnvelope {
      val obj = runCatching { Json.parseToJsonElement(json) as JsonObject }.getOrNull()
        ?: throw PampaCryptoException("busta illeggibile")
      val v = (obj["v"] as? JsonPrimitive)?.intOrNull
      val e = (obj["e"] as? JsonPrimitive)?.intOrNull
      val alg = (obj["alg"] as? JsonPrimitive)?.takeIf { it.isString }?.content
      val n = (obj["n"] as? JsonPrimitive)?.takeIf { it.isString }?.content
      val c = (obj["c"] as? JsonPrimitive)?.takeIf { it.isString }?.content
      if (v != VERSION || e == null || e < 1 || alg != ALG || n == null || c == null) throw PampaCryptoException("busta di un formato che non conosco")
      val nonce = runCatching { PampaBase64.decode(n) }.getOrElse { throw PampaCryptoException("nonce illeggibile", it) }
      if (nonce.size != PampaCrypto.NONCE_BYTES) throw PampaCryptoException("nonce di ${nonce.size} byte")
      return PampaEnvelope(e, nonce, runCatching { PampaBase64.decode(c) }.getOrElse { throw PampaCryptoException("testo cifrato illeggibile", it) })
    }
  }

  /** Apre la busta con [key] (la chiave dell'epoca [epoch]) e gli stessi dati associati della chiusura. */
  fun open(key: ByteArray, aad: ByteArray): ByteArray = PampaCrypto.aesGcmOpen(key, nonce, ciphertext, aad)
}

/**
 * Le righe sincronizzate di un'app: la busta coi dati associati del contratto (`accountId|pacchetto|raccolta|idRiga|
 * versione`). Le chiavi sono quelle che l'app riceve dallo Store con `PampaAccount.appKey` (una per epoca); qui non
 * si salvano mai.
 *
 * In più tiene **l'ultima versione vista** di ogni riga (M12): un server che rimette una versione vecchia viene
 * scoperto. La tiene in memoria; un'app che vuole ricordarla fra due avvii la salva con [lastSeen] e la rimette con
 * [remember].
 */
class PampaRecordCipher(private val accountId: String, private val packageName: String) {
  private val seen = HashMap<String, Long>()

  /** Chiude [plaintext] per la riga [recordId] della raccolta [collection], alla versione che avrà sul server. */
  fun seal(key: ByteArray, epoch: Int, collection: String, recordId: String, version: Long, plaintext: ByteArray): String =
    PampaEnvelope.seal(key, epoch, plaintext, PampaLabels.recordAad(accountId, packageName, collection, recordId, version)).toField()

  /**
   * Apre una riga come la dà il server. [keyFor] dà la chiave di un'epoca, o `null` se l'app non la ha (in quel caso
   * [PampaCryptoException]: l'app chiede le chiavi nuove allo Store e riprova). Una riga spostata, scambiata con
   * un'altra o tornata a una versione vecchia è [PampaCryptoException].
   */
  fun open(keyFor: (Int) -> ByteArray?, collection: String, recordId: String, version: Long, field: String): ByteArray {
    val id = "$collection|$recordId"
    val last = seen[id]
    if (last != null && version < last) throw PampaCryptoException("la riga $recordId è tornata alla versione $version dopo la $last")
    val envelope = PampaEnvelope.parseField(field)
    val key = keyFor(envelope.epoch) ?: throw PampaCryptoException("manca la chiave dell'epoca ${envelope.epoch}")
    val plain = try {
      envelope.open(key, PampaLabels.recordAad(accountId, packageName, collection, recordId, version))
    } catch (e: PampaCryptoException) {
      throw PampaCryptoException("la riga $recordId non è al suo posto (dati associati, K4)", e)
    }
    seen[id] = version
    return plain
  }

  /** L'ultima versione vista di ogni riga, `raccolta|idRiga` → versione. */
  fun lastSeen(): Map<String, Long> = HashMap(seen)

  /** Rimette le versioni viste salvate dall'app. */
  fun remember(versions: Map<String, Long>) {
    for ((k, v) in versions) if ((seen[k] ?: Long.MIN_VALUE) < v) seen[k] = v
  }
}
