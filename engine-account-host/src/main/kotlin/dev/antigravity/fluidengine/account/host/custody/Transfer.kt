package dev.antigravity.fluidengine.account.host.custody

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * Il passaggio fra telefoni, dal lato del telefono **nuovo** (`b\chiavi.md` § 4.2; critica S2): una coppia X25519
 * effimera `(n, N)` e un segreto `s` di 128 bit, che passano solo dallo schermo alla fotocamera. Al server va solo la
 * richiesta di un numero di passaggio, a corpo vuoto (`POST /v1/keys/transfers`): nessuna chiave.
 *
 * Il QR ([qr]) dice `{ "v": 1, "transferId", "N", "s" }`. Dopo il ritiro, o dopo 10 minuti, chi la tiene chiama
 * [forget].
 */
class TransferOffer private constructor(val transferId: String, private var pair: X25519KeyPair?, private var s: ByteArray?) {

  /** La pubblica `N`, 32 byte. */
  val publicKey: ByteArray = pair!!.publicKey

  /** Il testo del QR da mostrare: «Inquadra questo codice con un dispositivo dove sei già dentro». */
  val qr: String
    get() = TransferQr(transferId, publicKey, secretS()).encode()

  internal fun secretS(): ByteArray = (s ?: error("passaggio già dimenticato")).copyOf()
  internal fun keyPair(): X25519KeyPair = pair ?: error("passaggio già dimenticato")

  /** Cancella `n` e `s`: un passaggio vale una volta. */
  fun forget() {
    s?.fill(0)
    s = null
    pair = null
  }

  override fun toString(): String = "TransferOffer($transferId)"

  companion object {
    /** Il numero viene dal server; la chiave e il segreto nascono qui e non lo raggiungono mai. */
    fun create(transferId: String): TransferOffer = TransferOffer(transferId, X25519KeyPair.random(), PampaCrypto.random(16))
  }
}

/** Quello che il telefono **vecchio** legge dal QR. Con [parse] un testo che non è un QR di passaggio dà `null`. */
class TransferQr(val transferId: String, val n: ByteArray, val s: ByteArray) {
  fun encode(): String = buildJsonObject {
    put("v", 1)
    put("transferId", transferId)
    put("N", PampaBase64.encode(n))
    put("s", PampaBase64.encode(s))
  }.toString()

  override fun toString(): String = "TransferQr($transferId)"

  companion object {
    fun parse(text: String): TransferQr? {
      val o = runCatching { Json.parseToJsonElement(text) as JsonObject }.getOrNull() ?: return null
      if ((o["v"] as? JsonPrimitive)?.intOrNull != 1) return null
      val id = (o["transferId"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
      val n = (o["N"] as? JsonPrimitive)?.content?.let { runCatching { PampaBase64.decode(it) }.getOrNull() } ?: return null
      val s = (o["s"] as? JsonPrimitive)?.content?.let { runCatching { PampaBase64.decode(it) }.getOrNull() } ?: return null
      if (n.size != 32 || s.size != 16 || !id.matches(Regex("[A-Za-z0-9_-]{1,64}"))) return null
      return TransferQr(id, n, s)
    }
  }
}

/** Il sigillo del passaggio e il suo `mac` col segreto del QR, come li carica il vecchio e li ritira il nuovo. */
data class TransferSeal(val sealed: String, val mac: String)
