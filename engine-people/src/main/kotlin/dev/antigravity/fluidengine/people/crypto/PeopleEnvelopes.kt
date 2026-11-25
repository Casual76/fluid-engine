package dev.antigravity.fluidengine.people.crypto

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import dev.antigravity.fluidengine.account.crypto.PampaCryptoException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Chi riceve una CDXB: la sua casella, la sua chiave di cifratura e l'epoca, come stanno nella sua scheda. */
class CdxbRecipient(val mailbox: String, val enc: ByteArray, val workEpoch: Int) {
  companion object {
    fun of(card: PersonCard) = CdxbRecipient(card.mailbox, card.work.enc, card.work.epoch)
  }
}

/**
 * La busta per **una persona sola**: il formato `CDXB` di Codex (`InboxParcel`), rifatto con HPKE come dice il
 * contratto (`people.formulas.cdxb`, F10.5). Porta richieste, accettazioni, chiavi delle conversazioni, dei gruppi e
 * degli spazi.
 *
 * ```
 * "CDXB" ‖ u32(epoca di lavoro del mittente) ‖ u32(epoca di lavoro del destinatario) ‖ enc(32) ‖ testo cifrato ‖ firma(64)
 * ```
 *
 * - HPKE «base» verso la chiave di cifratura del destinatario, con `info = "pampa/v1/cdxb|<da>|<a>"` e i 12 byte di
 *   testa come dati associati: una busta spostata in un'altra casella, o con un altro mittente, non si apre.
 * - La firma Ed25519 della chiave di lavoro di chi manda su `"pampa/v1/cdxb-firma|<da>|<a>|" ‖ tutto il resto`.
 * - Dentro, il JSON del contenuto **più la scheda di chi manda** (`card`, `cardSig`): chi riceve controlla la firma
 *   anche da uno sconosciuto, e scopre un server che cambia il mittente.
 *
 * Il mittente lo mette il server (la casella di chi chiama), ma il telefono non gli crede: lo ricontrolla con la
 * scheda dentro la busta.
 */
object Cdxb {
  private val MAGIC = "CDXB".toByteArray(Charsets.US_ASCII)
  private const val HEAD = 12

  class Opened(val payload: JsonObject, val card: PersonCard, val senderEpoch: Int, val recipientEpoch: Int)

  /**
   * Chiude [payload] per [to]. [work] sono le chiavi di lavoro di chi manda, [myMailbox] la sua casella e [myCard] la
   * sua scheda (che viaggia dentro). Il risultato è già firmato: la coda in uscita lo può riprovare senza chiavi.
   */
  fun seal(work: WorkKeys, myMailbox: String, myCard: PersonCard, to: CdxbRecipient, payload: JsonObject): ByteArray {
    val head = MAGIC + u32Bytes(work.epoch.toLong()) + u32Bytes(to.workEpoch.toLong())
    val body = JsonObject(payload + mapOf("card" to JsonPrimitive(myCard.raw), "cardSig" to JsonPrimitive(myCard.sig)))
    val sealed = PeopleHpke.seal(to.enc, PeopleLabels.utf8(PeopleLabels.CDXB_INFO, "from" to myMailbox, "to" to to.mailbox), head, body.toString().toByteArray(Charsets.UTF_8))
    val signed = head + sealed
    return signed + work.sign.sign(PeopleLabels.utf8(PeopleLabels.CDXB_SIG, "from" to myMailbox, "to" to to.mailbox) + signed)
  }

  /** È una CDXB? Lo dicono i primi quattro byte. */
  fun isCdxb(data: ByteArray): Boolean = data.size > 4 && data.copyOf(4).contentEquals(MAGIC)

  /**
   * Apre una CDXB arrivata da [from] (quello che dice il server) nella casella [myMailbox], con le mie chiavi di lavoro
   * di ogni epoca ([mine]). [knownRoot] è la radice che conosco già per quella casella (un contatto, un membro di un
   * gruppo): deve restare quella. Ogni cosa che non torna è [PeopleDetected].
   */
  fun open(myMailbox: String, mine: Map<Int, WorkKeys>, from: String, data: ByteArray, knownRoot: ByteArray? = null): Opened {
    if (!isCdxb(data) || data.size <= HEAD + 32 + 64) throw PeopleDetected("busta sconosciuta")
    val senderEpoch = readU32(data, 4).toInt()
    val myEpoch = readU32(data, 8).toInt()
    val keys = mine[myEpoch] ?: throw PeopleDetected("busta per una chiave che non ho")
    val head = data.copyOf(HEAD)
    val signed = data.copyOf(data.size - 64)
    val sig = data.copyOfRange(data.size - 64, data.size)
    val plain = try {
      PeopleHpke.open(keys.enc, PeopleLabels.utf8(PeopleLabels.CDXB_INFO, "from" to from, "to" to myMailbox), head, signed.copyOfRange(HEAD, signed.size))
    } catch (_: PampaCryptoException) {
      throw PeopleDetected("busta che non si apre (spostata, cambiata o di un altro mittente)")
    }
    val payload = runCatching { Json.parseToJsonElement(plain.decodeToString()) as JsonObject }.getOrNull() ?: throw PeopleDetected("busta illeggibile")
    val cardRaw = (payload["card"] as? JsonPrimitive)?.content ?: throw PeopleDetected("busta senza la scheda di chi manda")
    val cardSig = (payload["cardSig"] as? JsonPrimitive)?.content ?: throw PeopleDetected("busta senza la scheda di chi manda")
    val card = PersonCard.verify(cardRaw, cardSig, expectMailbox = from, expectRoot = knownRoot)
    if (card.work.epoch != senderEpoch) throw PeopleDetected("epoca del mittente")
    if (!Ed25519Key.verify(card.work.sign, PeopleLabels.utf8(PeopleLabels.CDXB_SIG, "from" to from, "to" to myMailbox) + signed, sig)) {
      throw PeopleDetected("firma della busta")
    }
    return Opened(JsonObject(payload - "card" - "cardSig"), card, senderEpoch, myEpoch)
  }
}

/**
 * Il messaggio di una conversazione: il formato `CDX3` di Codex (`MessageEnvelope`), nella versione nuova `PMX1` del
 * contratto (`people.formulas.pmx1`), con le due caselle e il filo nei dati associati.
 *
 * ```
 * "PMX1" ‖ u32(epoca della conversazione) ‖ id del messaggio(16) ‖ nonce(12) ‖ AES-256-GCM(chiave del messaggio)
 * chiave del messaggio = HKDF(ikm = chiave della conversazione all'epoca, sale = id del messaggio, info = "pampa/v1/pmx1")
 * dati associati       = i 36 byte di testa ‖ utf8("|<da>|<a>|<filo>")
 * ```
 *
 * Una chiave per messaggio, come nell'alpha: due messaggi non condividono mai la coppia chiave-nonce. Una busta spostata
 * in un'altra conversazione, o attribuita a un altro mittente, non si apre.
 */
object Pmx1 {
  private val MAGIC = "PMX1".toByteArray(Charsets.US_ASCII)
  const val HEAD = 36

  fun isPmx1(data: ByteArray): Boolean = data.size > HEAD && data.copyOf(4).contentEquals(MAGIC)

  /** Chiude [plaintext] con [chatKey] all'[epoch], da [from] a [to] nel filo [thread]. */
  fun seal(chatKey: ByteArray, epoch: Int, from: String, to: String, thread: String, plaintext: ByteArray): ByteArray =
    sealWith(chatKey, epoch, from, to, thread, plaintext, PampaCrypto.random(16), PampaCrypto.random(PampaCrypto.NONCE_BYTES))

  internal fun sealWith(chatKey: ByteArray, epoch: Int, from: String, to: String, thread: String, plaintext: ByteArray, messageId: ByteArray, nonce: ByteArray): ByteArray {
    require(messageId.size == 16 && nonce.size == 12)
    val head = MAGIC + u32Bytes(epoch.toLong()) + messageId + nonce
    val key = PampaCrypto.hkdfRaw(chatKey, messageId, PeopleLabels.PMX1_KEY.toByteArray(Charsets.UTF_8), 32)
    return head + PampaCrypto.aesGcmSeal(key, nonce, plaintext, aad(head, from, to, thread))
  }

  /** L'epoca della conversazione con cui è chiusa, senza aprirla. */
  fun epochOf(data: ByteArray): Int = readU32(data, 4).toInt()

  /** L'id del messaggio, in base64url: serve a non prendere due volte lo stesso messaggio. */
  fun messageIdOf(data: ByteArray): String = PampaBase64.encode(data.copyOfRange(8, 24))

  /** Apre una PMX1 con la chiave dell'epoca giusta fra [chatKeys], o `null` se non è per questa conversazione. */
  fun open(chatKeys: Map<Int, ByteArray>, from: String, to: String, thread: String, data: ByteArray): ByteArray? {
    if (!isPmx1(data)) return null
    val chatKey = chatKeys[epochOf(data)] ?: return null
    val head = data.copyOf(HEAD)
    val key = PampaCrypto.hkdfRaw(chatKey, data.copyOfRange(8, 24), PeopleLabels.PMX1_KEY.toByteArray(Charsets.UTF_8), 32)
    return try {
      PampaCrypto.aesGcmOpen(key, data.copyOfRange(24, 36), data.copyOfRange(HEAD, data.size), aad(head, from, to, thread))
    } catch (_: PampaCryptoException) {
      null
    }
  }

  private fun aad(head: ByteArray, from: String, to: String, thread: String) =
    head + PeopleLabels.utf8(PeopleLabels.PMX1_AAD, "from" to from, "to" to to, "thread" to thread)
}

/**
 * Un record di uno spazio condiviso (contratto `people.formulas.spaceRecord`): `nonce(12) ‖ AES-256-GCM`, con la chiave
 * dello spazio all'epoca e i dati associati `"pampa/v1/spazio|<spazio>|<epoca>|<record>|<versione>"`. Dentro c'è la
 * firma di chi scrive, così «aggiunto da Marco» non si falsifica e il server non sa chi ha scritto cosa:
 * `{ "d": dati, "w": codice di chi scrive, "e": sua epoca di lavoro, "s": Ed25519(sign, "pampa/v1/spazio-firma|…|" ‖ d) }`.
 *
 * Nota per il contratto: `e2e.json` dice che `w` è «la radice di chi scrive», il telefono finto del server ci mette il
 * codice. Qui si segue il telefono finto (il codice), ed è scritto nel rapporto di F10.6.
 */
object SpaceRecordCipher {
  class Opened(val data: String, val writer: String, val writerEpoch: Int, val signature: ByteArray)

  fun seal(key: ByteArray, spaceId: String, epoch: Int, recordId: String, version: Long, data: String, writerCode: String, writer: WorkKeys): ByteArray =
    sealWith(key, spaceId, epoch, recordId, version, data, writerCode, writer, PampaCrypto.random(PampaCrypto.NONCE_BYTES))

  internal fun sealWith(key: ByteArray, spaceId: String, epoch: Int, recordId: String, version: Long, data: String, writerCode: String, writer: WorkKeys, nonce: ByteArray): ByteArray {
    val sig = writer.sign.sign(signatureInput(spaceId, epoch, recordId, version, data))
    val plain = kotlinx.serialization.json.buildJsonObject {
      put("d", JsonPrimitive(data))
      put("w", JsonPrimitive(writerCode))
      put("e", JsonPrimitive(writer.epoch))
      put("s", JsonPrimitive(PampaBase64.encode(sig)))
    }.toString().toByteArray(Charsets.UTF_8)
    return nonce + PampaCrypto.aesGcmSeal(key, nonce, plain, aad(spaceId, epoch, recordId, version))
  }

  /** Apre un record; [PeopleDetected] se è stato spostato, rinominato, rinumerato o cambiato. Non controlla la firma. */
  fun open(key: ByteArray, spaceId: String, epoch: Int, recordId: String, version: Long, ciphertext: ByteArray): Opened {
    if (ciphertext.size <= PampaCrypto.NONCE_BYTES + PampaCrypto.TAG_BYTES) throw PeopleDetected("record troppo corto")
    val plain = try {
      PampaCrypto.aesGcmOpen(key, ciphertext.copyOf(12), ciphertext.copyOfRange(12, ciphertext.size), aad(spaceId, epoch, recordId, version))
    } catch (_: PampaCryptoException) {
      throw PeopleDetected("record che non si apre (spostato, rinominato o cambiato)")
    }
    val o = runCatching { Json.parseToJsonElement(plain.decodeToString()) as JsonObject }.getOrNull() ?: throw PeopleDetected("record illeggibile")
    fun s(k: String) = (o[k] as? JsonPrimitive)?.content ?: throw PeopleDetected("record incompleto")
    return Opened(s("d"), s("w"), s("e").toIntOrNull() ?: throw PeopleDetected("record incompleto"), PampaBase64.decode(s("s")))
  }

  /** La firma di [opened] è della chiave di lavoro [signPub]? */
  fun signedBy(opened: Opened, signPub: ByteArray, spaceId: String, epoch: Int, recordId: String, version: Long): Boolean =
    Ed25519Key.verify(signPub, signatureInput(spaceId, epoch, recordId, version, opened.data), opened.signature)

  fun aad(spaceId: String, epoch: Int, recordId: String, version: Long): ByteArray =
    PeopleLabels.utf8(PeopleLabels.SPACE_AAD, "spaceId" to spaceId, "epoch" to epoch, "recordId" to recordId, "version" to version)

  private fun signatureInput(spaceId: String, epoch: Int, recordId: String, version: Long, data: String): ByteArray =
    PeopleLabels.utf8(PeopleLabels.SPACE_SIG, "spaceId" to spaceId, "epoch" to epoch, "recordId" to recordId, "version" to version) + data.toByteArray(Charsets.UTF_8)
}
