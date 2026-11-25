package dev.antigravity.fluidengine.account.host.custody

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.crypto.PampaCryptoException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Cosa sta dentro il sigillo del passaggio fra telefoni (`b\chiavi.md` § 4.2): tutte le epoche della chiave dei dati,
 * `{ "epochs": [ { "epoch": 1, "key": "<32 byte base64url>" }, … ] }`, come lo scrive il telefono finto del server
 * (`sealTransfer`). Il JSON non esce mai dal sigillo.
 */
internal object TransferPayload {

  fun encode(epochs: Map<Int, ByteArray>): ByteArray = buildJsonObject {
    putJsonArray("epochs") {
      for ((epoch, key) in epochs.toSortedMap()) addJsonObject {
        put("epoch", epoch)
        put("key", PampaBase64.encode(key))
      }
    }
  }.toString().toByteArray(Charsets.UTF_8)

  /** Le epoche dentro il sigillo; [PampaCryptoException] se non è il formato giusto o una chiave non è da 32 byte. */
  fun decode(bytes: ByteArray): Map<Int, ByteArray> {
    val root = runCatching { Json.parseToJsonElement(bytes.decodeToString()) as JsonObject }.getOrNull()
      ?: throw PampaCryptoException("passaggio illeggibile")
    val list = root["epochs"] as? JsonArray ?: throw PampaCryptoException("passaggio senza epoche")
    val out = sortedMapOf<Int, ByteArray>()
    for (item in list) {
      val obj = item as? JsonObject ?: throw PampaCryptoException("epoca illeggibile")
      val epoch = (obj["epoch"] as? JsonPrimitive)?.intOrNull ?: throw PampaCryptoException("epoca senza numero")
      val key = (obj["key"] as? JsonPrimitive)?.content?.let { runCatching { PampaBase64.decode(it) }.getOrNull() }
      if (key == null || key.size != 32 || epoch < 1 || out.containsKey(epoch)) throw PampaCryptoException("epoca $epoch non valida")
      out[epoch] = key
    }
    if (out.isEmpty()) throw PampaCryptoException("passaggio senza epoche")
    return out
  }
}
