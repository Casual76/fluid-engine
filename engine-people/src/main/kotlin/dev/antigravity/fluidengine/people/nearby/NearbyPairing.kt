package dev.antigravity.fluidengine.people.nearby

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import dev.antigravity.fluidengine.account.crypto.PampaCryptoException
import dev.antigravity.fluidengine.people.Contact
import dev.antigravity.fluidengine.people.ContactVia
import dev.antigravity.fluidengine.people.PeopleClient
import dev.antigravity.fluidengine.people.PeopleState
import dev.antigravity.fluidengine.people.Verification
import dev.antigravity.fluidengine.people.VerifyMethod
import dev.antigravity.fluidengine.people.crypto.NearbyHandshake
import dev.antigravity.fluidengine.people.crypto.PeoplePrints
import dev.antigravity.fluidengine.people.crypto.PersonCard
import dev.antigravity.fluidengine.people.crypto.X25519Key
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Il canale diretto fra due telefoni vicini, già aperto: Nearby Connections nello Store (F10.7, con Play services),
 * o il filo finto delle prove ([FakeNearbyLink]). Porta byte e basta: chi è l'altro lo dice il saluto firmato.
 */
interface NearbyLink {
  suspend fun send(bytes: ByteArray)
  suspend fun receive(): ByteArray
}

/** Il filo finto delle prove: due capi collegati da due code. [tap] vede (e può cambiare) quello che passa. */
class FakeNearbyLink private constructor(private val out: Channel<ByteArray>, private val inbox: Channel<ByteArray>, private val tap: ((ByteArray) -> ByteArray)?) : NearbyLink {
  override suspend fun send(bytes: ByteArray) = out.send(tap?.invoke(bytes) ?: bytes)
  override suspend fun receive(): ByteArray = inbox.receive()

  companion object {
    fun pair(tap: ((ByteArray) -> ByteArray)? = null): Pair<FakeNearbyLink, FakeNearbyLink> {
      val ab = Channel<ByteArray>(Channel.UNLIMITED)
      val ba = Channel<ByteArray>(Channel.UNLIMITED)
      return FakeNearbyLink(ab, ba, tap) to FakeNearbyLink(ba, ab, tap)
    }
  }
}

/** La vicinanza è stata rifiutata: un saluto non valido, una firma che non torna, o la runa non confermata. */
class NearbyRefused(message: String) : Exception(message)

/**
 * Aggiungersi **di persona** avvicinando i telefoni (`progetto.md` § 3.6, «Vicino»; `NearbyHandshake` di Codex): le
 * schede passano dirette, senza il server, e i due vedono la stessa runa. Dopo la conferma tutti e due sono
 * **verificati subito**, perché la chiave intera è arrivata senza intermediari (critica S5).
 *
 * Anche i gettoni di consegna e la chiave della conversazione passano dal canale diretto, cifrati con l'accordo fra le
 * due effimere: il server non vede nemmeno una richiesta. Dopo, ognuno registra nella sua casella l'impronta del
 * gettone che ha dato.
 *
 * ```kotlin
 * val session = NearbyPairing(people).handshake(link)   // saluto e risposte
 * show(session.rune)                                      // la stessa runa sui due schermi
 * val contact = session.confirm()                         // tutti e due confermano
 * ```
 */
class NearbyPairing(private val people: PeopleClient) {

  /** Saluto, sfida e risposta. Torna la sessione con la runa da mostrare; [NearbyRefused] se l'altro non è chi dice. */
  suspend fun handshake(link: NearbyLink): NearbySession {
    val me = people.identity()
    val eph = X25519Key.random()
    val challenge = NearbyHandshake.newChallenge()
    link.send(frame(HELLO, NearbyHandshake.hello(me.card, challenge, eph.publicKey)))
    val hello = try {
      NearbyHandshake.readHello(expect(link.receive(), HELLO))
    } catch (e: NearbyHandshake.Refused) {
      throw NearbyRefused(e.message ?: "saluto non valido")
    }
    if (hello.card.code == me.code) throw NearbyRefused("sei tu, da un altro dispositivo")
    link.send(frame(AUTH, NearbyHandshake.answer(hello.challenge, me.card, eph.publicKey, hello.ephemeral, me.work)))
    val auth = expect(link.receive(), AUTH)
    if (!NearbyHandshake.verify(challenge, hello.card, hello.ephemeral, eph.publicKey, auth)) throw NearbyRefused("la firma dell'altro telefono non torna")
    val rune = NearbyHandshake.rune(eph, hello.ephemeral, me.card, hello.card)
    return NearbySession(people, link, eph, hello.card, hello.ephemeral, rune)
  }

  internal companion object {
    const val HELLO = 1
    const val AUTH = 2
    const val CONFIRM = 3
    const val MATERIAL = 4

    fun frame(type: Int, body: ByteArray): ByteArray = byteArrayOf(type.toByte()) + body

    fun expect(bytes: ByteArray, type: Int): ByteArray {
      if (bytes.isEmpty() || bytes[0].toInt() != type) throw NearbyRefused("messaggio fuori ordine")
      return bytes.copyOfRange(1, bytes.size)
    }
  }
}

/**
 * La vicinanza dopo il saluto: [peer] è la scheda dell'altro, [rune] la runa da confrontare. [confirm] quando l'utente
 * ha visto la stessa runa sui due schermi; [cancel] se non tornava.
 */
class NearbySession internal constructor(
  private val people: PeopleClient,
  private val link: NearbyLink,
  private val eph: X25519Key,
  val peer: PersonCard,
  private val peerEphemeral: ByteArray,
  val rune: String,
) {
  /**
   * Confermo: lo dico all'altro e aspetto il suo sì; poi ci scambiamo, cifrati sul canale, i gettoni di consegna. La
   * conversazione nasce dall'accordo delle effimere, uguale dai due lati e mai trasmessa.
   */
  suspend fun confirm(): Contact {
    val me = people.identity()
    link.send(NearbyPairing.frame(NearbyPairing.CONFIRM, byteArrayOf(1)))
    val answer = NearbyPairing.expect(link.receive(), NearbyPairing.CONFIRM)
    if (answer.singleOrNull() != 1.toByte()) throw NearbyRefused("l'altro non ha confermato la runa")
    val dh = eph.agree(peerEphemeral)
    val (lo, hi) = if (me.card.raw < peer.raw) me.card to peer else peer to me.card
    val salt = PampaCrypto.sha256(lo.raw.toByteArray(), hi.raw.toByteArray())
    val chatId = PampaBase64.encode(PampaCrypto.hkdf(dh, salt, "pampa/v1/vicino-chat", 16))
    val chatKey = PampaCrypto.hkdf(dh, salt, "pampa/v1/vicino-chiave")
    val materialKey = PampaCrypto.hkdf(dh, salt, "pampa/v1/vicino-materiale")
    dh.fill(0)
    val myToken = PeoplePrints.newToken()
    val nonce = PampaCrypto.random(12)
    val mine = buildJsonObject { put("token", myToken) }.toString().toByteArray()
    link.send(NearbyPairing.frame(NearbyPairing.MATERIAL, nonce + PampaCrypto.aesGcmSeal(materialKey, nonce, mine, aad(me.card.code))))
    val theirs = NearbyPairing.expect(link.receive(), NearbyPairing.MATERIAL)
    val opened = try {
      PampaCrypto.aesGcmOpen(materialKey, theirs.copyOf(12), theirs.copyOfRange(12, theirs.size), aad(peer.code))
    } catch (e: PampaCryptoException) {
      throw NearbyRefused("i gettoni dell'altro non si aprono")
    }
    val theirToken = ((Json.parseToJsonElement(opened.decodeToString()) as JsonObject)["token"] as JsonPrimitive).content
    // R2 · Un codice che ho già, con un'altra radice, non sostituisce il contatto vero.
    val old = people.contact(peer.code)
    if (old != null && !old.card.root.contentEquals(peer.root)) throw NearbyRefused("il codice è di un contatto che hai già, con un'altra chiave")
    val contact = Contact(
      card = peer,
      theirToken = theirToken,
      myToken = myToken,
      chatId = chatId,
      chatKeys = mapOf(1 to chatKey),
      chatEpoch = 1,
      via = ContactVia.NEARBY,
      verification = Verification(VerifyMethod.NEARBY, me.card.work.epoch, peer.work.epoch, System.currentTimeMillis()),
      // Di nuovo vicini con chi c'era già: la conversazione nuova è quella che ha anche lui, il nome dato resta.
      nickname = old?.nickname,
      addedAt = System.currentTimeMillis(),
      lastActivity = System.currentTimeMillis(),
    )
    // L'impronta del mio gettone nella mia casella, in coda (R2): la vicinanza va anche senza rete, e parte con la coda.
    people.edit { st: PeopleState ->
      people.queueDeliveryPrint(st, myToken)
      st.contacts[peer.code] = contact
    }
    people.syncContact(peer.code)
    return contact
  }

  /** La runa non era la stessa: si chiude senza aggiungere niente. */
  suspend fun cancel() {
    runCatching { link.send(NearbyPairing.frame(NearbyPairing.CONFIRM, byteArrayOf(0))) }
  }

  private fun aad(code: String) = "pampa/v1/vicino|$code".toByteArray()
}
