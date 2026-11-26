package dev.antigravity.fluidengine.people

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import dev.antigravity.fluidengine.people.client.EnvelopePriority
import dev.antigravity.fluidengine.people.client.OutEnvelope
import dev.antigravity.fluidengine.people.client.PeopleApi
import dev.antigravity.fluidengine.people.client.PeopleServerException
import dev.antigravity.fluidengine.people.client.PickedEnvelope
import dev.antigravity.fluidengine.people.crypto.Cdxb
import dev.antigravity.fluidengine.people.crypto.CdxbRecipient
import dev.antigravity.fluidengine.people.crypto.PairCode
import dev.antigravity.fluidengine.people.crypto.PeopleDetected
import dev.antigravity.fluidengine.people.crypto.PeoplePrints
import dev.antigravity.fluidengine.people.crypto.PersonCard
import dev.antigravity.fluidengine.people.crypto.PersonQr
import dev.antigravity.fluidengine.people.crypto.Pmx1
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Le regole delle persone che non sono numeri del server. Ognuna è una costante sola, facile da cambiare. */
object PeoplePolicy {
  /**
   * Dopo un rinnovo delle chiavi di un contatto, la verifica **a distanza** (quattro parole) è da rifare, come il
   * numero di sicurezza di Signal (brief di F10.6). La verifica di persona (QR, vicinanza) resta: ha visto la radice.
   * Con `false` resterebbe anche quella a distanza, perché le chiavi nuove sono firmate dalla radice già verificata.
   */
  const val RENEWAL_DROPS_WORDS_VERIFICATION = true

  /** Un QR personale con la prova giusta fa entrare subito, come il `pairing_auto` dell'alpha: l'altro era lì. */
  const val AUTO_ACCEPT_QR = true

  /** Il saluto di una richiesta: 280 caratteri al massimo (`progetto.md` § 3.6). */
  const val GREETING_MAX = 280

  /** Buste per chiamata verso una casella, e caselle per chiamata di gruppo (contratto `limits.people`). */
  const val PER_CALL = 45
  const val GROUP_PER_CALL = 100

  /** Il QR personale vale 24 ore e una volta (contratto `people.inviteQr`). */
  const val QR_TTL_MS = 86_400_000L
}

class PeopleConfig(
  /** La versione dello Store: una busta che chiede uno Store più nuovo arriva come «Aggiorna Pampa Store» (M2). */
  val storeVersion: String = OutEnvelope.MIN_STORE,
  val minStore: String = OutEnvelope.MIN_STORE,
  val renewalDropsWordsVerification: Boolean = PeoplePolicy.RENEWAL_DROPS_WORDS_VERIFICATION,
  val autoAcceptQr: Boolean = PeoplePolicy.AUTO_ACCEPT_QR,
)

/** Quando riprovare la coda in uscita: lo Store la collega a WorkManager (`PeopleWork`), le prove a niente. */
fun interface OutboxScheduler {
  fun requestFlush(delayMillis: Long)

  companion object {
    val None = OutboxScheduler { }
  }
}

/** Quello che il ritiro ha trovato, busta per busta. [n] è il numero della busta nella casella. */
sealed interface PeopleEvent {
  val n: Long

  data class Request(override val n: Long, val request: IncomingRequest) : PeopleEvent
  data class Accepted(override val n: Long, val code: String) : PeopleEvent
  data class Message(override val n: Long, val code: String, val thread: String, val payload: JsonObject, val messageId: String, val group: String? = null) : PeopleEvent
  data class ChatKey(override val n: Long, val code: String, val epoch: Int) : PeopleEvent
  data class WordsReady(override val n: Long, val code: String) : PeopleEvent
  /** Le parole con [code] sono cambiate: [byThem] se ha rinnovato lui, altrimenti ho rinnovato io. */
  data class WordsChanged(override val n: Long, val code: String, val byThem: Boolean) : PeopleEvent
  data class Join(override val n: Long, val group: String, val member: GroupMember, val card: PersonCard) : PeopleEvent
  data class GroupUpdated(override val n: Long, val group: String, val epoch: Int) : PeopleEvent
  data class RemovedFromGroup(override val n: Long, val group: String) : PeopleEvent
  data class SpaceKeys(override val n: Long, val space: String, val owner: String) : PeopleEvent
  data class FromMyDevices(override val n: Long, val payload: JsonObject) : PeopleEvent
  data class RubricaSynced(override val n: Long, val code: String) : PeopleEvent
  data class Detected(override val n: Long, val what: String) : PeopleEvent
  /** La busta chiede uno Store più nuovo (critica M2): la card «Aggiorna Pampa Store per vederlo». */
  data class TooNew(override val n: Long, val minStore: String) : PeopleEvent
}

/** Com'è andata la coda: quante buste sono partite, quante restano, e quelle che non arriveranno mai. */
data class FlushReport(val sent: Int, val kept: Int, val undeliverable: List<String>, val offline: Boolean)

/** Le parole di una coppia, come le mostra la conversazione. */
data class WordsView(val words: List<String>, val gem: ByteArray, val verified: Boolean, val notice: ContactNotice?)

/** Il QR di un gruppo, da proiettare in classe: 24 ore, 100 ingressi, un amministratore approva. */
data class GroupInvite(val group: String, val token: String, val admin: PersonCard)

/**
 * Le persone sul telefono (`d\server-e-chiavi.md` § 4–5 e § 10; `progetto.md` § 3.6–3.10). È lo Store che lo usa: tiene
 * la persona, la rubrica e la casella, e parla col server con [api].
 *
 * - **Aggiungersi** col codice ([lookup], [request]), col QR ([newQr], [requestFromQr]) o di persona (`NearbyPairing`);
 *   accettare ([accept]), ignorare, bloccare in silenzio ([block]), togliere ([remove]).
 * - **Verificare a distanza** con le quattro parole ([words], [markWordsVerified]), con l'impegno contro il compleanno.
 * - **Scrivere**: [sendMessage] (PMX1 nella conversazione), [sendToMyDevices] («I tuoi dispositivi»), i gruppi.
 * - **Ricevere** col ritiro ([pickup]), che il campanello o il canale aperto fanno partire ([onBell]).
 * - **La coda in uscita** (critica M3): ogni busta si chiude e si firma **quando la prepari**, e aspetta in
 *   [PeopleState.outbox] finché la rete torna ([flush]); riprovarla non chiede chiavi, quindi va anche in tasca.
 * - **Il rinnovo** ([rotateWork]) dopo un «perso o rubato» o i sei mesi.
 *
 * Tutto è chiuso in classe consegna (Q7 A), tranne la radice, che serve solo a [rotateWork] e al nome nuovo.
 */
class PeopleClient(
  val persona: PersonaStore,
  private val stateStore: PeopleStateStore,
  private val api: PeopleApi,
  val config: PeopleConfig = PeopleConfig(),
  private val clock: () -> Long = System::currentTimeMillis,
  private val scheduler: OutboxScheduler = OutboxScheduler.None,
) {
  private val mutex = Mutex()

  /** Lo stato di adesso (rubrica, richieste, posta). Per leggere; si cambia solo dai metodi del client. */
  val state: PeopleState get() = stateStore.load()

  fun identity(): PersonIdentity = requireNotNull(persona.identity()) { "la persona non c'è ancora: prima crea la persona" }

  fun contact(code: String): Contact? = state.contacts[code]

  /** Il contatto è verificato **adesso**? Di persona sì sempre; a distanza solo con le parole delle chiavi di oggi. */
  fun isVerified(code: String): Boolean {
    val c = state.contacts[code] ?: return false
    val v = c.verification ?: return false
    if (v.method != VerifyMethod.WORDS || !config.renewalDropsWordsVerification) return true
    return v.theirEpoch == c.card.work.epoch && v.myEpoch == identity().card.work.epoch
  }

  // --- La scheda ------------------------------------------------------------------------------------------------------

  /** Pubblica (o aggiorna) la scheda. La prima volta apre la casella. */
  suspend fun publish(findable: Boolean? = null) = api.putCard(identity().card, findable)

  /** La scheda di [code], controllata sul telefono: resta «da verificare» (critica S5). */
  suspend fun lookup(code: String, invite: String? = null): PersonCard = api.lookup(code, invite)

  // --- Richieste e accettazioni -----------------------------------------------------------------------------------

  /**
   * Manda una richiesta a [card]: la mia scheda, un saluto, il gettone per scrivermi, la chiave della conversazione e
   * l'**impegno** delle parole. Il contatto resta in attesa finché non accetta.
   */
  suspend fun request(card: PersonCard, greeting: String, via: ContactVia = ContactVia.CODE, invite: String? = null, qrProof: String? = null): Contact = mutex.withLock {
    require(greeting.codePointCount(0, greeting.length) <= PeoplePolicy.GREETING_MAX) { "il saluto va fino a ${PeoplePolicy.GREETING_MAX} caratteri" }
    val me = identity()
    require(card.code != me.code) { "non ci si aggiunge da soli" }
    val st = state
    // R2 · Un codice già in rubrica non si sostituisce: con un'altra radice è una scheda falsa (critica S5), con la
    // stessa è lo stesso contatto, che resta com'è (conversazione, gettoni e verifica compresi).
    st.contacts[card.code]?.let { old ->
      if (!old.card.root.contentEquals(card.root)) throw PeopleDetected("radice cambiata: il codice è di un contatto che ho già")
      val kept = old.copy(card = fresherCard(old.card, card))
      if (kept != old) {
        st.contacts[card.code] = kept
        stateStore.save(st)
      }
      return@withLock kept
    }
    val myToken = newDeliveryToken(st)
    val chatKey = PampaCrypto.random(32)
    val nonce = PampaCrypto.random(PairCode.NONCE_BYTES)
    val contact = Contact(
      card = card,
      theirToken = null,
      myToken = myToken,
      chatId = PampaBase64.encode(PampaCrypto.random(16)),
      chatKeys = mapOf(1 to chatKey),
      chatEpoch = 1,
      via = via,
      verification = if (via == ContactVia.QR) Verification(VerifyMethod.QR, me.card.work.epoch, card.work.epoch, clock()) else null,
      words = WordsExchange(committer = true, myEpoch = me.card.work.epoch, theirEpoch = card.work.epoch, myNonce = nonce),
      addedAt = clock(),
      lastActivity = clock(),
    )
    val payload = buildJsonObject {
      put("t", "richiesta")
      put("greeting", greeting)
      put("token", myToken)
      put("chatId", contact.chatId)
      put("chatKey", PampaBase64.encode(chatKey))
      put("wordsCommit", PampaBase64.encode(PairCode.commit(nonce, me.card.root, me.card.work.enc, card.work.enc)))
      invite?.let { put("invite", it) }
      qrProof?.let { put("qrProof", it) }
    }
    st.contacts[card.code] = contact
    enqueue(st, OutEnvelope(card.mailbox, seal(me, card, payload), invite = invite), "richiesta a ${card.code}")
    queueRubricaSync(st, me, contact)
    stateStore.save(st)
    flushLocked()
    contact
  }

  /** Il mio QR personale: la scheda intera, un invito da 24 ore e un uso, e un segreto che resta qui. */
  suspend fun newQr(): PersonQr = mutex.withLock {
    val me = identity()
    val token = PeoplePrints.newToken()
    api.addPrint("invite.qr", PeoplePrints.invite(PampaBase64.decode(token)))
    val qr = PersonQr.create(me.card, token)
    val st = state
    st.invites[token] = GivenInvite(token, "qr", qrSecret = qr.secret, expiresAt = clock() + PeoplePolicy.QR_TTL_MS)
    stateStore.save(st)
    qr
  }

  /**
   * Ho inquadrato il QR di qualcuno: la scheda è arrivata senza intermediari, quindi **verificata subito**. La richiesta
   * porta il suo invito e la prova di aver visto il QR, così anche lui sa che sono io (critica S5).
   */
  suspend fun requestFromQr(qr: PersonQr, greeting: String = ""): Contact =
    request(qr.card, greeting, ContactVia.QR, invite = qr.invite, qrProof = qr.proof(identity().card.raw))

  /** Accetta una richiesta: il mio gettone per lui, e il mio nonce delle parole. */
  suspend fun accept(req: IncomingRequest): Contact = mutex.withLock { acceptLocked(state, req) }

  private suspend fun acceptLocked(st: PeopleState, req: IncomingRequest): Contact {
    val me = identity()
    val old = st.contacts[req.card.code]
    if (old != null && !old.card.root.contentEquals(req.card.root)) throw PeopleDetected("radice cambiata: il codice è di un contatto che ho già")
    val myToken = newDeliveryToken(st)
    val mine = req.invite?.let { st.invites[it] }
    val qrOk = mine?.kind == "qr" && req.qrProof != null && mine.qrSecret != null && PersonQr(me.card, mine.token, mine.qrSecret).checkProof(req.card.raw, req.qrProof)
    val via = when {
      qrOk -> ContactVia.QR
      mine?.kind == "link" -> ContactVia.LINK
      mine?.kind == "group" -> ContactVia.GROUP
      else -> ContactVia.CODE
    }
    val nonce = responderNonce(me, req.wordsCommit, me.card.work.epoch, req.card.work.epoch)
    val contact = Contact(
      card = req.card,
      theirToken = req.token,
      myToken = myToken,
      chatId = req.chatId,
      chatKeys = mapOf(1 to req.chatKey),
      chatEpoch = 1,
      via = via,
      verification = if (qrOk) Verification(VerifyMethod.QR, me.card.work.epoch, req.card.work.epoch, clock()) else null,
      words = WordsExchange(committer = false, myEpoch = me.card.work.epoch, theirEpoch = req.card.work.epoch, myNonce = nonce, theirCommit = req.wordsCommit),
      // Accetto di nuovo chi c'era già (ha perso la sua rubrica): la conversazione riparte, il nome che gli ho dato resta.
      nickname = old?.nickname,
      addedAt = clock(),
      lastActivity = clock(),
    )
    st.contacts[req.card.code] = contact
    st.requests.remove(req.n)
    if (mine != null && mine.kind != "group") st.invites.remove(mine.token)
    val payload = buildJsonObject {
      put("t", "accetta")
      put("token", myToken)
      put("wordsNonce", PampaBase64.encode(nonce))
    }
    enqueue(st, OutEnvelope(req.card.mailbox, seal(me, req.card, payload), token = req.token), "accetto ${req.card.code}")
    queueRubricaSync(st, me, contact)
    stateStore.save(st)
    flushLocked()
    return contact
  }

  /** Ignora una richiesta: sparisce e basta, chi l'ha mandata non lo sa. */
  suspend fun ignore(req: IncomingRequest) = mutex.withLock {
    val st = state
    st.requests.remove(req.n)
    stateStore.save(st)
  }

  /**
   * Blocca: come togliere, più l'impronta della sua casella fra i bloccati. Lo scarto è silenzioso. Vale **subito** sul
   * telefono, anche senza rete (R2, regola R6): le sue buste si scartano qui, e le impronte partono con la coda.
   */
  suspend fun block(mailbox: String) = mutex.withLock {
    val st = state
    st.contacts.values.firstOrNull { it.card.mailbox == mailbox }?.let { removeLocked(st, it) }
    st.requests.entries.removeIf { it.value.card.mailbox == mailbox }
    st.pendingPrints += PrintOp(add = true, kind = "blocked", print = PeoplePrints.blocked(mailbox))
    st.blocked += mailbox
    stateStore.save(st)
    flushLocked()
  }

  suspend fun unblock(mailbox: String) = mutex.withLock {
    val st = state
    st.pendingPrints += PrintOp(add = false, kind = "blocked", print = PeoplePrints.blocked(mailbox))
    st.blocked -= mailbox
    stateStore.save(st)
    flushLocked()
  }

  /** Togli: la mia casella dimentica il gettone che gli avevo dato. Lui tiene quello che ha già. Vale subito (R2). */
  suspend fun remove(code: String) = mutex.withLock {
    val st = state
    st.contacts[code]?.let { removeLocked(st, it) }
    stateStore.save(st)
    flushLocked()
  }

  private fun removeLocked(st: PeopleState, c: Contact) {
    val now = clock()
    st.pendingPrints += PrintOp(add = false, kind = "delivery", print = PeoplePrints.delivery(PampaBase64.decode(c.myToken)))
    st.contacts.remove(c.code)
    tombstone(st, c.code, now)
    queueSelf(st, identity(), buildJsonObject { put("t", "rubrica-via"); put("code", c.code); put("at", now) }, EnvelopePriority.NORMAL)
  }

  /** La lapide di [code] tolto all'ora [at] (R2); le lapidi oltre i 90 giorni se ne vanno. */
  private fun tombstone(st: PeopleState, code: String, at: Long) {
    st.removedContacts[code] = maxOf(st.removedContacts[code] ?: 0L, at)
    val horizon = clock() - PeopleState.TOMBSTONE_DAYS * DAY_MS
    st.removedContacts.entries.removeIf { it.value < horizon }
  }

  /** [c] è un contatto tolto dopo che era stato aggiunto? Allora una sua copia vecchia non torna (R2). */
  private fun buried(st: PeopleState, c: Contact): Boolean {
    val removedAt = st.removedContacts[c.code] ?: return false
    return removedAt >= c.addedAt
  }

  // --- Le quattro parole ------------------------------------------------------------------------------------------

  /** Le parole con [code], se lo scambio con l'impegno è finito; altrimenti `null` («le parole arrivano fra poco»). */
  fun words(code: String): WordsView? {
    val c = state.contacts[code] ?: return null
    val w = c.words?.takeIf { it.ready } ?: return null
    val me = identity()
    val myWork = me.works[w.myEpoch] ?: return null
    if (w.theirEpoch != c.card.work.epoch) return null
    val key = PairCode.pairKey(myWork.enc, c.card.work.enc, me.card.root, c.card.root)
    val salt = if (w.committer) PairCode.salt(w.myNonce, w.theirNonce!!) else PairCode.salt(w.theirNonce!!, w.myNonce)
    return WordsView(PairCode.wordsText(key, salt), PairCode.gem(key, salt), isVerified(code), c.notice)
  }

  /** Le parole tornavano al telefono: [code] è verificato a distanza, per le chiavi di oggi. */
  suspend fun markWordsVerified(code: String) = mutex.withLock {
    val st = state
    val c = st.contacts[code] ?: return@withLock
    val w = checkNotNull(c.words?.takeIf { it.ready }) { "le parole non sono ancora pronte" }
    val updated = c.copy(verification = Verification(VerifyMethod.WORDS, w.myEpoch, w.theirEpoch, clock()), notice = null)
    st.contacts[code] = updated
    queueRubricaSync(st, identity(), updated)
    stateStore.save(st)
  }

  /** L'utente ha visto l'avviso delle parole cambiate. */
  suspend fun dismissNotice(code: String) = mutex.withLock {
    val st = state
    st.contacts[code]?.let { st.contacts[code] = it.copy(notice = null) }
    stateStore.save(st)
  }

  // --- Scrivere ---------------------------------------------------------------------------------------------------

  /** Un messaggio (o una cosa condivisa) nella conversazione con [code]: una PMX1, chiusa adesso, in coda. */
  suspend fun sendMessage(code: String, payload: JsonObject, priority: EnvelopePriority = EnvelopePriority.HIGH): String = mutex.withLock {
    val st = state
    val c = checkNotNull(st.contacts[code]?.takeIf { it.accepted }) { "$code non è (ancora) un contatto" }
    val me = identity()
    val data = Pmx1.seal(c.chatKeys.getValue(c.chatEpoch), c.chatEpoch, me.mailbox, c.card.mailbox, c.chatId, payload.toString().toByteArray())
    st.contacts[code] = c.copy(lastActivity = clock())
    st.seenMessages += Pmx1.messageIdOf(data)
    enqueue(st, OutEnvelope(c.card.mailbox, PampaBase64.encode(data), token = c.theirToken, priority = priority), "messaggio a $code")
    stateStore.save(st)
    flushLocked()
    Pmx1.messageIdOf(data)
  }

  /** «I tuoi dispositivi» (scelta Q1): una CDXB a me stesso, che apre ogni mio dispositivo. Il server non tiene cronologie. */
  suspend fun sendToMyDevices(payload: JsonObject) = mutex.withLock {
    val st = state
    queueSelf(st, identity(), JsonObject(payload + mapOf("t" to JsonPrimitive("dispositivi"))), EnvelopePriority.HIGH)
    stateStore.save(st)
    flushLocked()
  }

  // --- La coda in uscita ------------------------------------------------------------------------------------------

  /** Riprova le buste in coda. Senza rete restano tutte; un rifiuto definitivo (`gone`, `not_found`) le toglie. */
  suspend fun flush(): FlushReport = mutex.withLock { flushLocked() }

  private suspend fun flushLocked(): FlushReport {
    val st = state
    val now = clock()
    var sent = 0
    val undeliverable = ArrayList<String>()
    val due = st.outbox.filter { it.notBefore <= now }
    // Le buste di gruppo partono con le consegne a pezzi; le altre casella per casella, al massimo 45 per chiamata.
    try {
      // R2 · Prima le impronte della mia casella, nell'ordine: un gettone nuovo c'è già quando l'altro lo usa, e un
      // blocco fatto senza rete arriva al server. Se una non passa per ora, le buste aspettano con lei.
      while (st.pendingPrints.isNotEmpty()) {
        val op = st.pendingPrints.first()
        try {
          if (op.add) api.addPrint(op.kind, op.print) else api.removePrint(op.print)
        } catch (e: PeopleServerException) {
          if (e.retryable || e.status in 401..403) {
            stateStore.save(st)
            scheduler.requestFlush(e.retryAfterSeconds?.times(1000) ?: RETRY_BASE_MS)
            return FlushReport(0, st.outbox.size, emptyList(), offline = false)
          }
          // Un rifiuto definitivo (una richiesta sbagliata): riprovarla non cambia niente.
        }
        st.pendingPrints.removeAt(0)
        stateStore.save(st)
      }
      for (chunk in due.filter { it.group }.chunked(PeoplePolicy.GROUP_PER_CALL)) {
        val results = try {
          api.sendMany(chunk.map { it.envelope })
        } catch (e: PeopleServerException) {
          if (e.retryable) { postpone(st, chunk, e, now); continue }
          chunk.forEach { undeliverable += it.label }
          st.outbox.removeAll(chunk.toSet())
          continue
        }
        chunk.zip(results).forEach { (item, r) ->
          when (r) {
            "accepted" -> { st.outbox.remove(item); sent++ }
            "gone", "not_found" -> { st.outbox.remove(item); undeliverable += item.label }
            else -> postpone(st, listOf(item), null, now)
          }
        }
        // R2 · Salvato dopo ogni chiamata: un lavoro fermato a metà (WorkManager, una schermata chiusa) non rimanda doppioni.
        stateStore.save(st)
      }
      for ((to, items) in due.filter { !it.group }.groupBy { it.envelope.to }) {
        var rest = items
        while (rest.isNotEmpty()) {
          // Una richiesta (niente gettone, non a me stesso) viaggia da sola.
          val batch = if (isRequest(rest.first())) rest.take(1) else rest.takeWhile { !isRequest(it) }.take(PeoplePolicy.PER_CALL)
          try {
            api.send(to, batch.map { it.envelope })
            st.outbox.removeAll(batch.toSet())
            sent += batch.size
            stateStore.save(st)
          } catch (e: PeopleServerException) {
            val ok = (e.accepted ?: 0).coerceIn(0, batch.size)
            st.outbox.removeAll(batch.take(ok).toSet())
            sent += ok
            if (e.retryable) {
              postpone(st, rest.drop(ok), e, now)
              break
            }
            undeliverable += batch.drop(ok).map { it.label }
            st.outbox.removeAll(batch.drop(ok).toSet())
          }
          rest = rest.drop(batch.size)
        }
      }
    } catch (_: IOException) {
      stateStore.save(st)
      scheduler.requestFlush(RETRY_OFFLINE_MS)
      return FlushReport(sent, st.outbox.size, undeliverable, offline = true)
    }
    stateStore.save(st)
    st.outbox.minOfOrNull { it.notBefore }?.let { scheduler.requestFlush((it - now).coerceAtLeast(1_000)) }
    return FlushReport(sent, st.outbox.size, undeliverable, offline = false)
  }

  private fun isRequest(item: OutboxItem): Boolean =
    item.envelope.token == null && item.envelope.to != (persona.identity()?.mailbox ?: "") && !item.group

  private fun postpone(st: PeopleState, items: List<OutboxItem>, e: PeopleServerException?, now: Long) {
    for (item in items) {
      val i = st.outbox.indexOf(item)
      if (i < 0) continue
      val wait = e?.retryAfterSeconds?.times(1000) ?: (RETRY_BASE_MS shl item.attempts.coerceAtMost(6))
      st.outbox[i] = item.copy(attempts = item.attempts + 1, notBefore = now + wait)
    }
  }

  private fun enqueue(st: PeopleState, envelope: OutEnvelope, label: String, group: Boolean = false) {
    st.outbox += OutboxItem(PampaBase64.encode(PampaCrypto.random(9)), envelope, group, 0, 0, clock(), label)
  }

  // --- Ricevere ---------------------------------------------------------------------------------------------------

  /** Il campanello (o il canale aperto) ha suonato: ritira, apre, e riprova la coda. */
  suspend fun onBell(): List<PeopleEvent> = pickup()

  /**
   * Il ritiro di questo dispositivo: prende tutto dopo il suo punto, lo apre e lo controlla, poi conferma fin dove è
   * arrivato, così la casella può cancellare ciò che tutti i dispositivi hanno preso. Ogni cosa che non torna è un
   * [PeopleEvent.Detected]: il telefono non la usa.
   *
   * Le consegne per le app (le cose condivise, «I tuoi dispositivi», le richieste) restano anche in
   * [PeopleState.undispatched], salvate con il cursore: se il ritiro cade a metà, `PeopleDispatcher` le smista al giro
   * dopo (R1).
   */
  suspend fun pickup(): List<PeopleEvent> = mutex.withLock {
    val st = stateStore.reload()
    val events = ArrayList<PeopleEvent>()
    // R2 · Le PMX1 rimaste ad aspettare la chiave: un altro mio dispositivo (o una copia) può averla portata.
    retryWaiting(st).let { events += it; it.filterTo(st.undispatched) { e -> e.forApps } }
    var guard = 0
    while (guard++ < MAX_PAGES) {
      val page = api.pickup(st.cursor)
      for (e in page.envelopes) {
        val opened = openEnvelope(st, e) + retryWaiting(st)
        events += opened
        opened.filterTo(st.undispatched) { it.forApps }
      }
      st.cursor = maxOf(st.cursor, page.last)
      stateStore.save(st)
      if (!page.more) break
    }
    runCatching { api.pickup(st.cursor, limit = 1) }
    stateStore.save(st)
    // Le risposte nate dal ritiro (la rivelazione delle parole, i nonce dopo un rinnovo) partono subito.
    flushLocked()
    events
  }

  private suspend fun openEnvelope(st: PeopleState, e: PickedEnvelope): List<PeopleEvent> {
    if (newer(e.minStore, config.storeVersion)) return listOf(PeopleEvent.TooNew(e.n, e.minStore))
    val bytes = runCatching { PampaBase64.decode(e.data) }.getOrNull() ?: return listOf(PeopleEvent.Detected(e.n, "busta illeggibile"))
    return try {
      when {
        Pmx1.isPmx1(bytes) -> listOfNotNull(onPmx1(st, e, bytes))
        Cdxb.isCdxb(bytes) -> {
          val me = identity()
          val known = st.contacts.values.firstOrNull { it.card.mailbox == e.from }?.card?.root
            ?: st.groups.values.flatMap { it.members.values }.firstOrNull { it.mailbox == e.from }?.root?.let(PampaBase64::decode)
            ?: if (e.from == me.mailbox) me.card.root else null
          val opened = Cdxb.open(me.mailbox, me.works, e.from, bytes, known)
          onCdxb(st, e, opened)
        }
        else -> listOf(PeopleEvent.Detected(e.n, "busta sconosciuta"))
      }
    } catch (d: PeopleDetected) {
      listOf(PeopleEvent.Detected(e.n, d.what))
    } catch (c: CancellationException) {
      throw c
    } catch (l: PersonLockedException) {
      throw l
    } catch (io: IOException) {
      throw io
    } catch (s: PeopleServerException) {
      // Di passaggio (rete, tetti, accesso da rinnovare): la pagina si riprende al giro dopo, senza perdere niente.
      if (s.retryable || s.status in 401..403) throw s
      listOf(PeopleEvent.Detected(e.n, "busta che il server non lascia applicare (${s.code})"))
    } catch (x: Exception) {
      // R2 · Una busta con dentro un campo storto (un base64 rotto, un numero che non è un numero, un tipo sbagliato):
      // è di quella busta sola. Il cursore la passa e la casella non si ferma; chiunque può mandarne una.
      listOf(PeopleEvent.Detected(e.n, "busta malformata"))
    }
  }

  private fun onPmx1(st: PeopleState, e: PickedEnvelope, data: ByteArray): PeopleEvent? {
    openPmx1(st, e.n, e.from, data)?.let { return it.takeIf { m -> m !== ALREADY_SEEN } }
    // R2 · Di un contatto (o di un membro di un gruppo) ma di un'epoca che non ho ancora: la chiave è in ritardo. La busta
    // resta qui, cifrata, e si riapre quando arriva la chiave.
    val epoch = Pmx1.epochOf(data)
    val waits = st.contacts.values.any { it.card.mailbox == e.from && epoch > it.chatEpoch && epoch !in it.chatKeys } ||
      st.groups.values.any { g -> epoch > g.epoch && epoch !in g.keys && g.members.values.any { it.mailbox == e.from } }
    if (waits && st.waitingMessages.none { it.n == e.n && it.from == e.from }) {
      st.waitingMessages += WaitingMessage(e.n, e.from, PampaBase64.encode(data), clock())
      trimWaiting(st)
      return null
    }
    throw PeopleDetected("messaggio che non si apre (spostato, cambiato o di un'epoca che non ho)")
  }

  /** Apre una PMX1 con le chiavi di oggi: il messaggio, [ALREADY_SEEN] se l'avevo già, `null` se non si apre. */
  private fun openPmx1(st: PeopleState, n: Long, from: String, data: ByteArray): PeopleEvent.Message? {
    val me = identity()
    val id = Pmx1.messageIdOf(data)
    for (c in st.contacts.values.filter { it.card.mailbox == from }) {
      val plain = Pmx1.open(c.chatKeys, from, me.mailbox, c.chatId, data) ?: continue
      if (!st.seenMessages.add(id)) return ALREADY_SEEN
      st.contacts[c.code] = c.copy(lastActivity = clock())
      return PeopleEvent.Message(n, c.code, c.chatId, json(plain), id)
    }
    for (g in st.groups.values) {
      val m = g.members.values.firstOrNull { it.mailbox == from } ?: continue
      val plain = Pmx1.open(g.keys, from, me.mailbox, g.id, data) ?: continue
      if (!st.seenMessages.add(id)) return ALREADY_SEEN
      return PeopleEvent.Message(n, m.code, g.id, json(plain), id, group = g.id)
    }
    return null
  }

  /** Le PMX1 in attesa che ora si aprono (la chiave è arrivata): tornano coi loro numeri di busta. Le scadute vanno via. */
  private fun retryWaiting(st: PeopleState): List<PeopleEvent> {
    if (st.waitingMessages.isEmpty()) return emptyList()
    val out = ArrayList<PeopleEvent>()
    val iter = st.waitingMessages.iterator()
    while (iter.hasNext()) {
      val w = iter.next()
      val data = runCatching { PampaBase64.decode(w.data) }.getOrNull()
      if (data == null) { iter.remove(); continue }
      val opened = try {
        openPmx1(st, w.n, w.from, data)
      } catch (_: PeopleDetected) {
        iter.remove()
        out += PeopleEvent.Detected(w.n, "contenuto illeggibile")
        continue
      }
      when {
        opened === ALREADY_SEEN -> iter.remove()
        opened != null -> { iter.remove(); out += opened }
      }
    }
    trimWaiting(st)
    return out
  }

  /** I tetti delle PMX1 in attesa: 30 giorni, 100 buste, 1 MiB. Escono prima le più vecchie. */
  private fun trimWaiting(st: PeopleState) {
    val horizon = clock() - PeopleState.WAITING_DAYS * DAY_MS
    st.waitingMessages.removeAll { it.at < horizon }
    while (st.waitingMessages.size > PeopleState.WAITING_MAX || st.waitingMessages.sumOf { it.data.length } > PeopleState.WAITING_MAX_BYTES * 4 / 3) {
      st.waitingMessages.removeAt(0)
    }
  }

  private suspend fun onCdxb(st: PeopleState, e: PickedEnvelope, opened: Cdxb.Opened): List<PeopleEvent> {
    val me = identity()
    val p = opened.payload
    val card = opened.card
    val code = card.code
    if (e.from == me.mailbox) return onSelf(st, e, p)
    if (card.mailbox in st.blocked) return emptyList()
    // R2 · La scheda di un contatto non torna mai indietro: una busta vecchia e genuina, rimandata dal server dopo un
    // rinnovo, non rimette le chiavi di prima (che stanno sul telefono rubato). Si tiene la più nuova delle due.
    val known = st.contacts[code]?.card?.let { fresherCard(it, card) } ?: card
    return when (p.s("t")) {
      "richiesta" -> {
        val greeting = p.s("greeting").orEmpty()
        if (greeting.codePointCount(0, greeting.length) > PeoplePolicy.GREETING_MAX) throw PeopleDetected("richiesta troppo lunga")
        st.contacts[code]?.let { if (!it.card.root.contentEquals(card.root)) throw PeopleDetected("richiesta col codice di un contatto ma un'altra radice") }
        val req = IncomingRequest(
          n = e.n, card = card, greeting = greeting, token = p.req("token"), chatId = p.req("chatId"), chatKey = PampaBase64.decode(p.req("chatKey")),
          via = e.via, invite = p.s("invite"), wordsCommit = p.s("wordsCommit")?.let(PampaBase64::decode), qrProof = p.s("qrProof"), at = e.at,
        )
        val mine = req.invite?.let { st.invites[it] }
        if (config.autoAcceptQr && mine?.kind == "qr" && mine.expiresAt >= clock() && req.qrProof != null &&
          PersonQr(me.card, mine.token, mine.qrSecret!!).checkProof(card.raw, req.qrProof)
        ) {
          acceptLocked(st, req)
          return listOf(PeopleEvent.Accepted(e.n, code))
        }
        st.requests[e.n] = req
        listOf(PeopleEvent.Request(e.n, req))
      }
      "accetta" -> {
        val c = st.contacts[code] ?: throw PeopleDetected("accettazione di una richiesta che non ho fatto")
        var updated = c.copy(theirToken = p.req("token"), card = known, lastActivity = clock())
        val out = mutableListOf<PeopleEvent>(PeopleEvent.Accepted(e.n, code))
        val w = c.words
        val nonce = p.s("wordsNonce")?.let(PampaBase64::decode)
        if (w != null && w.committer && nonce != null) {
          updated = updated.copy(words = w.copy(theirNonce = nonce, revealed = true))
          sendReveal(st, me, updated, w)
          out += PeopleEvent.WordsReady(e.n, code)
        }
        st.contacts[code] = updated
        out
      }
      "parole" -> {
        val c = st.contacts[code] ?: throw PeopleDetected("parole da chi non è un contatto")
        val w = c.words ?: throw PeopleDetected("parole senza uno scambio aperto")
        val nonce = PampaBase64.decode(p.req("nonce"))
        val theirEpoch = p.int("myEpoch")
        val myEpoch = p.int("yourEpoch")
        if (w.committer || w.theirCommit == null || theirEpoch != w.theirEpoch || myEpoch != w.myEpoch) throw PeopleDetected("parole fuori dallo scambio")
        val theirEnc = card.work.takeIf { it.epoch == theirEpoch }?.enc ?: throw PeopleDetected("parole di un'altra epoca")
        val myEnc = me.works[myEpoch]?.enc?.publicKey ?: throw PeopleDetected("parole per una chiave che non ho")
        if (!PairCode.opens(w.theirCommit, nonce, card.root, theirEnc, myEnc)) throw PeopleDetected("l'impegno delle parole non torna")
        st.contacts[code] = c.copy(words = w.copy(theirNonce = nonce), card = known)
        listOf(PeopleEvent.WordsReady(e.n, code))
      }
      "chiave-chat" -> {
        val c = st.contacts[code] ?: throw PeopleDetected("chiave per una conversazione che non c'è")
        if (c.chatId != p.s("chatId")) throw PeopleDetected("chiave per una conversazione che non c'è")
        val epoch = p.int("epoch")
        val keys = c.chatKeys + (epoch to PampaBase64.decode(p.req("key")))
        val out = mutableListOf<PeopleEvent>(PeopleEvent.ChatKey(e.n, code, epoch))
        var updated = c.copy(chatKeys = keys, chatEpoch = maxOf(c.chatEpoch, epoch), card = known)
        val commit = p.s("wordsCommit")?.let(PampaBase64::decode)
        if (commit != null && card.work.epoch > c.card.work.epoch) {
          val nonce = responderNonce(me, commit, me.card.work.epoch, card.work.epoch)
          val w = WordsExchange(committer = false, myEpoch = me.card.work.epoch, theirEpoch = card.work.epoch, myNonce = nonce, theirCommit = commit)
          val dropped = c.verification?.method == VerifyMethod.WORDS && config.renewalDropsWordsVerification
          updated = updated.copy(words = w, notice = if (c.words?.ready == true || c.verification != null) ContactNotice.WORDS_CHANGED_THEIRS else updated.notice)
          val reply = buildJsonObject {
            put("t", "parole-nonce")
            put("chatId", c.chatId)
            put("nonce", PampaBase64.encode(nonce))
            put("myEpoch", w.myEpoch)
            put("yourEpoch", w.theirEpoch)
          }
          c.theirToken?.let { enqueue(st, OutEnvelope(card.mailbox, seal(me, card, reply), token = it, priority = EnvelopePriority.NORMAL), "parole a $code") }
          if (c.words?.ready == true || dropped) out += PeopleEvent.WordsChanged(e.n, code, byThem = true)
        }
        st.contacts[code] = updated
        out
      }
      "parole-nonce" -> {
        val c = st.contacts[code] ?: throw PeopleDetected("parole da chi non è un contatto")
        val w = c.words ?: throw PeopleDetected("parole senza uno scambio aperto")
        if (!w.committer || p.int("yourEpoch") != w.myEpoch || p.int("myEpoch") != w.theirEpoch) throw PeopleDetected("parole fuori dallo scambio")
        val updated = c.copy(words = w.copy(theirNonce = PampaBase64.decode(p.req("nonce")), revealed = true), card = known)
        sendReveal(st, me, updated, w)
        st.contacts[code] = updated
        listOf(PeopleEvent.WordsReady(e.n, code))
      }
      "ingresso" -> {
        val group = p.req("group")
        val g = st.groups[group]
        val inv = st.invites[p.s("invite")]
        if (g == null || g.admin != me.code || inv?.kind != "group" || inv.group != group) throw PeopleDetected("ingresso senza il mio QR del gruppo")
        val member = GroupMember(card.name, code, card.mailbox, card.rootB64, PampaBase64.encode(card.work.enc), PampaBase64.encode(card.work.sign), card.work.epoch, p.req("token"))
        listOf(PeopleEvent.Join(e.n, group, member, card))
      }
      "gruppo" -> {
        val group = p.req("group")
        val prev = st.groups[group]
        if (prev != null && prev.admin != code) throw PeopleDetected("stato del gruppo da chi non lo amministra")
        // R2 · Uno stato vecchio rimandato non rimette dentro chi era uscito, né i gettoni di prima.
        if (prev != null && p.int("epoch") < prev.epoch) throw PeopleDetected("stato del gruppo più vecchio di quello che ho")
        val members = (p["members"] as JsonArray).map { PeopleState.member(it.jsonObject) }.associateBy { it.code }
        val mine = members[me.code] ?: throw PeopleDetected("gruppo che non mi contiene")
        val keys = (prev?.keys ?: emptyMap()) + (p["keys"] as JsonObject).entries.associate { it.key.toInt() to PampaBase64.decode(it.value.jsonPrimitive.content) }
        // Le impronte in coda, come le buste (R2): il ritiro non chiama la rete a metà di una busta.
        if (prev != null && prev.myToken != mine.token) {
          st.pendingPrints += PrintOp(add = true, kind = "delivery", print = PeoplePrints.delivery(PampaBase64.decode(mine.token)))
          st.pendingPrints += PrintOp(add = false, kind = "delivery", print = PeoplePrints.delivery(PampaBase64.decode(prev.myToken)))
        } else if (prev == null && mine.token != st.pendingGroupTokens[group]) {
          st.pendingPrints += PrintOp(add = true, kind = "delivery", print = PeoplePrints.delivery(PampaBase64.decode(mine.token)))
        }
        st.pendingGroupTokens.remove(group)
        st.groups[group] = PeopleGroup(group, p.s("name") ?: prev?.name ?: "", code, p.int("epoch"), keys, members, mine.token)
        listOf(PeopleEvent.GroupUpdated(e.n, group, p.int("epoch")))
      }
      "fuori" -> {
        val group = p.req("group")
        if (st.groups[group]?.admin != code) throw PeopleDetected("uscita da chi non amministra il gruppo")
        listOf(PeopleEvent.RemovedFromGroup(e.n, group))
      }
      "spazio" -> {
        val id = p.req("id")
        val prev = st.spaces[id]
        if (prev != null && prev.s("owner") != code) throw PeopleDetected("chiavi di uno spazio da chi non ne è il proprietario")
        st.spaces[id] = dev.antigravity.fluidengine.people.spaces.SpaceMembership.mergeIncoming(prev, p, code)
        listOf(PeopleEvent.SpaceKeys(e.n, id, code))
      }
      else -> throw PeopleDetected("tipo di busta sconosciuto")
    }
  }

  /** Una busta da me a me: la rubrica di un altro mio dispositivo, o «I tuoi dispositivi». */
  private fun onSelf(st: PeopleState, e: PickedEnvelope, p: JsonObject): List<PeopleEvent> {
    if (p.s("origin") == st.deviceTag) return emptyList()
    return when (p.s("t")) {
      "rubrica" -> {
        val incoming = PeopleState.contact(p["contact"]!!.jsonObject)
        // R2 · Una rubrica partita prima che togliessi questo contatto (un dispositivo rimasto offline) non lo rimette.
        if (buried(st, incoming)) return emptyList()
        val mine = st.contacts[incoming.code]
        if (mine != null && !mine.card.root.contentEquals(incoming.card.root)) throw PeopleDetected("rubrica con un'altra radice per un contatto")
        if (mine == null || incoming.lastActivity >= mine.lastActivity) {
          st.contacts[incoming.code] = if (mine == null) incoming else incoming.copy(card = fresherCard(mine.card, incoming.card))
        }
        st.requests.entries.removeIf { it.value.card.code == incoming.code }
        listOf(PeopleEvent.RubricaSynced(e.n, incoming.code))
      }
      "rubrica-via" -> {
        val code = p.req("code")
        // Tolto all'ora dell'altro dispositivo: un contatto aggiunto qui dopo, di nuovo, resta (R2).
        val at = (p["at"] as? JsonPrimitive)?.longOrNull ?: clock()
        st.contacts[code]?.let { if (it.addedAt <= at) st.contacts.remove(code) }
        tombstone(st, code, at)
        listOf(PeopleEvent.RubricaSynced(e.n, code))
      }
      "dispositivi" -> listOf(PeopleEvent.FromMyDevices(e.n, JsonObject(p - "origin")))
      else -> throw PeopleDetected("busta a me stesso di un tipo sconosciuto")
    }
  }

  /**
   * Il nonce di chi risponde nello scambio delle parole. Non è casuale: nasce dalla chiave di lavoro che firma, che
   * hanno tutti i miei dispositivi, e dall'impegno ricevuto. Così due miei dispositivi che rispondono allo stesso
   * rinnovo mandano lo stesso nonce, e mostrano le stesse parole. Per chi sta in mezzo è come un nonce casuale: senza la
   * mia chiave privata non lo sa prevedere, e cambia con ogni impegno.
   */
  private fun responderNonce(me: PersonIdentity, theirCommit: ByteArray?, myEpoch: Int, theirEpoch: Int): ByteArray {
    val sign = me.works.getValue(myEpoch).sign.privateBytes()
    try {
      val key = PampaCrypto.hkdf(sign, PampaCrypto.EMPTY, RESPONDER_NONCE)
      val input = (theirCommit ?: ByteArray(0)) + "|$myEpoch|$theirEpoch".toByteArray()
      return PampaCrypto.hmacSha256(key, input).copyOf(PairCode.NONCE_BYTES)
    } finally {
      sign.fill(0)
    }
  }

  private fun sendReveal(st: PeopleState, me: PersonIdentity, c: Contact, w: WordsExchange) {
    val reveal = buildJsonObject {
      put("t", "parole")
      put("chatId", c.chatId)
      put("nonce", PampaBase64.encode(w.myNonce))
      put("myEpoch", w.myEpoch)
      put("yourEpoch", w.theirEpoch)
    }
    c.theirToken?.let { enqueue(st, OutEnvelope(c.card.mailbox, seal(me, c.card, reveal), token = it, priority = EnvelopePriority.NORMAL), "parole a ${c.code}") }
  }

  // --- Il rinnovo -------------------------------------------------------------------------------------------------

  /**
   * «Perso o rubato», o i sei mesi (`d\server-e-chiavi.md` § 3): chiavi di lavoro nuove firmate dalla stessa radice,
   * scheda nuova, e per ogni contatto una chiave nuova della conversazione con un impegno nuovo delle parole. Il
   * telefono rubato, già fatto uscire, non apre niente di ciò che arriva dopo. Serve la radice: a telefono sbloccato.
   */
  suspend fun rotateWork(): PersonIdentity = mutex.withLock {
    val me = persona.rotateWork()
    api.putCard(me.card)
    val st = state
    for (c in st.contacts.values.toList()) {
      if (!c.accepted) continue
      val epoch = c.chatEpoch + 1
      val key = PampaCrypto.random(32)
      val nonce = PampaCrypto.random(PairCode.NONCE_BYTES)
      val w = WordsExchange(committer = true, myEpoch = me.card.work.epoch, theirEpoch = c.card.work.epoch, myNonce = nonce)
      val updated = c.copy(
        chatKeys = c.chatKeys + (epoch to key),
        chatEpoch = epoch,
        words = w,
        notice = if (c.words?.ready == true || c.verification?.method == VerifyMethod.WORDS) ContactNotice.WORDS_CHANGED_MINE else c.notice,
      )
      val payload = buildJsonObject {
        put("t", "chiave-chat")
        put("chatId", c.chatId)
        put("epoch", epoch)
        put("key", PampaBase64.encode(key))
        put("wordsCommit", PampaBase64.encode(PairCode.commit(nonce, me.card.root, me.card.work.enc, c.card.work.enc)))
      }
      st.contacts[c.code] = updated
      enqueue(st, OutEnvelope(c.card.mailbox, seal(me, c.card, payload), token = c.theirToken, priority = EnvelopePriority.NORMAL), "chiavi nuove a ${c.code}")
    }
    stateStore.save(st)
    flushLocked()
    me
  }

  // --- I gruppi ---------------------------------------------------------------------------------------------------

  /** Un gruppo nuovo, di cui sono l'amministratore. Il nome viaggia solo cifrato, dentro le buste. */
  suspend fun createGroup(name: String): PeopleGroup = mutex.withLock {
    val me = identity()
    val st = state
    val myToken = newDeliveryToken(st)
    val id = PampaBase64.encode(PampaCrypto.random(16))
    val g = PeopleGroup(id, name, me.code, 1, mapOf(1 to PampaCrypto.random(32)), mapOf(me.code to selfMember(me, myToken)), myToken)
    st.groups[id] = g
    stateStore.save(st)
    g
  }

  /** Il QR del gruppo: 24 ore, 100 ingressi. Chi lo inquadra chiede di entrare, e io approvo. */
  suspend fun groupInvite(groupId: String): GroupInvite = mutex.withLock {
    val token = PeoplePrints.newToken()
    api.addPrint("invite.group", PeoplePrints.invite(PampaBase64.decode(token)))
    val st = state
    st.invites[token] = GivenInvite(token, "group", group = groupId, expiresAt = clock() + PeoplePolicy.QR_TTL_MS)
    stateStore.save(st)
    GroupInvite(groupId, token, identity().card)
  }

  /** Ho inquadrato il QR di un gruppo: chiedo di entrare all'amministratore, col mio gettone per il gruppo. */
  suspend fun joinGroup(invite: GroupInvite) = mutex.withLock {
    val me = identity()
    val st = state
    val token = newDeliveryToken(st)
    st.pendingGroupTokens[invite.group] = token
    val payload = buildJsonObject { put("t", "ingresso"); put("group", invite.group); put("token", token); put("invite", invite.token) }
    enqueue(st, OutEnvelope(invite.admin.mailbox, seal(me, invite.admin, payload), invite = invite.token, priority = EnvelopePriority.NORMAL), "ingresso nel gruppo")
    stateStore.save(st)
    flushLocked()
  }

  /** Approvo chi ha chiesto di entrare, e mando a tutti lo stato del gruppo: chiavi, membri, gettoni. */
  suspend fun approve(groupId: String, joins: List<PeopleEvent.Join>) = mutex.withLock {
    val st = state
    val g = st.groups.getValue(groupId)
    val updated = g.copy(members = g.members + joins.associate { it.member.code to it.member })
    st.groups[groupId] = updated
    broadcastGroup(st, updated, updated.members.values.toList(), emptyList())
    stateStore.save(st)
    flushLocked()
  }

  /** Un messaggio al gruppo: una PMX1 per ogni membro, ognuna con le due caselle nei dati associati. */
  suspend fun groupMessage(groupId: String, payload: JsonObject): String = mutex.withLock {
    val st = state
    val me = identity()
    val g = st.groups.getValue(groupId)
    var id = ""
    for (m in g.members.values) {
      if (m.code == me.code) continue
      val data = Pmx1.seal(g.keys.getValue(g.epoch), g.epoch, me.mailbox, m.mailbox, g.id, payload.toString().toByteArray())
      id = Pmx1.messageIdOf(data)
      enqueue(st, OutEnvelope(m.mailbox, PampaBase64.encode(data), token = m.token), "gruppo ${g.id}", group = true)
    }
    stateStore.save(st)
    flushLocked()
    id
  }

  /**
   * Tolgo dei membri (P6: chi esce tiene il passato e perde il futuro): epoca nuova, chiave nuova, gettoni nuovi per chi
   * resta. Lo stato nuovo parte coi gettoni **vecchi**, gli unici che le caselle accettano ancora; chi esce riceve «fuori».
   */
  suspend fun removeFromGroup(groupId: String, codes: List<String>) = mutex.withLock {
    val st = state
    val me = identity()
    val g = st.groups.getValue(groupId)
    val oldTokens = g.members.mapValues { it.value.token }
    val gone = codes.mapNotNull { g.members[it] }
    val epoch = g.epoch + 1
    val stay = (g.members - codes.toSet()).mapValues { (_, m) -> m.copy(token = PeoplePrints.newToken()) }
    val myToken = stay.getValue(me.code).token
    st.pendingPrints += PrintOp(add = true, kind = "delivery", print = PeoplePrints.delivery(PampaBase64.decode(myToken)))
    st.pendingPrints += PrintOp(add = false, kind = "delivery", print = PeoplePrints.delivery(PampaBase64.decode(g.myToken)))
    val updated = g.copy(epoch = epoch, keys = g.keys + (epoch to PampaCrypto.random(32)), members = stay, myToken = myToken)
    st.groups[groupId] = updated
    broadcastGroup(st, updated, stay.values.map { it.copy(token = oldTokens.getValue(it.code)) }, gone)
    stateStore.save(st)
    flushLocked()
  }

  private fun broadcastGroup(st: PeopleState, g: PeopleGroup, to: List<GroupMember>, gone: List<GroupMember>) {
    val me = identity()
    val state = buildJsonObject {
      put("t", "gruppo")
      put("group", g.id)
      put("name", g.name)
      put("epoch", g.epoch)
      putJsonObject("keys") { g.keys.forEach { (e, k) -> put(e.toString(), PampaBase64.encode(k)) } }
      putJsonArray("members") { g.members.values.forEach { add(PeopleState.memberJson(it)) } }
    }
    for (m in to) {
      if (m.code == me.code) continue
      enqueue(st, OutEnvelope(m.mailbox, seal(me, CdxbRecipient(m.mailbox, PampaBase64.decode(m.enc), m.workEpoch), state), token = m.token, priority = EnvelopePriority.NORMAL), "gruppo ${g.id}", group = true)
    }
    for (m in gone) {
      val out = buildJsonObject { put("t", "fuori"); put("group", g.id) }
      enqueue(st, OutEnvelope(m.mailbox, seal(me, CdxbRecipient(m.mailbox, PampaBase64.decode(m.enc), m.workEpoch), out), token = m.token, priority = EnvelopePriority.NORMAL), "fuori dal gruppo", group = true)
    }
  }

  private fun selfMember(me: PersonIdentity, token: String) = GroupMember(me.card.name, me.code, me.mailbox, me.card.rootB64, PampaBase64.encode(me.card.work.enc), PampaBase64.encode(me.card.work.sign), me.card.work.epoch, token)

  // --- La copia della rubrica sotto K_com(rubrica) ----------------------------------------------------------------

  /**
   * La rubrica chiusa per il server sotto `K_com(rubrica)` all'epoca [epoch] (`b\chiavi.md` § 12): i contatti coi
   * gettoni e le conversazioni, i bloccati, i gruppi e gli spazi. Un telefono nuovo, o un altro dispositivo dopo un
   * «perso o rubato», la riprende con [importRubrica] insieme alla persona. Dati associati
   * `accountId|com.pampa.store|rubrica|contatti|<versione>`.
   */
  fun exportRubrica(accountId: String, epoch: Int, kRubrica: ByteArray, version: Long): String {
    val all = state.toJson()
    val plain = JsonObject(all.filterKeys { it in RUBRICA_KEYS })
    return dev.antigravity.fluidengine.account.crypto.PampaEnvelope.seal(kRubrica, epoch, plain.toString().toByteArray(), rubricaAad(accountId, version)).toField()
  }

  /**
   * Riprende la rubrica dalla copia del server. Quello che c'è già sul telefono e non è nella copia resta.
   *
   * Contro i ritorni indietro (R2, minacce M12): una copia con una [version] più vecchia dell'ultima ripresa qui è
   * rifiutata ([dev.antigravity.fluidengine.account.crypto.PampaCryptoException]); un contatto tolto dopo che era
   * stato aggiunto (le lapidi, anche quelle della copia) non torna; e la scheda di un contatto non scende mai a
   * un'epoca di lavoro più vecchia di quella che ho.
   */
  suspend fun importRubrica(accountId: String, field: String, version: Long, keyFor: (Int) -> ByteArray?) = mutex.withLock {
    val envelope = dev.antigravity.fluidengine.account.crypto.PampaEnvelope.parseField(field)
    val key = keyFor(envelope.epoch) ?: throw dev.antigravity.fluidengine.account.crypto.PampaCryptoException("manca K_com(rubrica) dell'epoca ${envelope.epoch}")
    val copy = PeopleState.fromJson(Json.parseToJsonElement(envelope.open(key, rubricaAad(accountId, version)).decodeToString()).jsonObject)
    val st = state
    if (version < st.importedRubrica) {
      throw dev.antigravity.fluidengine.account.crypto.PampaCryptoException("copia della rubrica più vecchia di quella già ripresa ($version < ${st.importedRubrica})")
    }
    for ((code, at) in copy.removedContacts) {
      tombstone(st, code, at)
      st.contacts[code]?.let { if (it.addedAt <= at) st.contacts.remove(code) }
    }
    for ((k, c) in copy.contacts) {
      if (buried(st, c)) continue
      val mine = st.contacts[k]
      if (mine != null && !mine.card.root.contentEquals(c.card.root)) continue
      if ((mine?.lastActivity ?: -1) <= c.lastActivity) st.contacts[k] = if (mine == null) c else c.copy(card = fresherCard(mine.card, c.card))
    }
    st.blocked += copy.blocked
    for ((k, g) in copy.groups) st.groups.putIfAbsent(k, g)
    for ((k, sp) in copy.spaces) st.spaces.putIfAbsent(k, sp)
    st.importedRubrica = version
    stateStore.save(st)
  }

  private fun rubricaAad(accountId: String, version: Long) =
    dev.antigravity.fluidengine.account.crypto.PampaLabels.recordAad(accountId, PersonaStore.STORE_PACKAGE, "rubrica", "contatti", version)

  // --- Per gli spazi e il provider dello Store --------------------------------------------------------------------

  /** Una CDXB di [payload] per il contatto [code], in coda: per le chiavi degli spazi (`SharedSpaces`). */
  internal suspend fun sendCdxb(code: String, payload: JsonObject, priority: EnvelopePriority = EnvelopePriority.NORMAL) = mutex.withLock {
    val st = state
    val c = checkNotNull(st.contacts[code]?.takeIf { it.accepted }) { "$code non è un contatto" }
    enqueue(st, OutEnvelope(c.card.mailbox, seal(identity(), c.card, payload), token = c.theirToken, priority = priority), "busta a $code")
    stateStore.save(st)
    flushLocked()
  }

  internal suspend fun <T> edit(block: suspend (PeopleState) -> T): T = mutex.withLock {
    val st = state
    val r = block(st)
    stateStore.save(st)
    r
  }

  internal val peopleApi: PeopleApi get() = api

  /** La rubrica di [code] va agli altri miei dispositivi (dopo un'aggiunta di persona, per esempio). */
  internal suspend fun syncContact(code: String) = mutex.withLock {
    val st = state
    st.contacts[code]?.let { queueRubricaSync(st, identity(), it) }
    stateStore.save(st)
    flushLocked()
  }

  // --- Gli attrezzi -----------------------------------------------------------------------------------------------

  /** Un gettone nuovo da dare: la sua impronta va in coda (R2), e parte prima della busta che lo porta. */
  private fun newDeliveryToken(st: PeopleState): String {
    val token = PeoplePrints.newToken()
    st.pendingPrints += PrintOp(add = true, kind = "delivery", print = PeoplePrints.delivery(PampaBase64.decode(token)))
    return token
  }

  /** Per la vicinanza: il gettone nato lì, in coda come gli altri (R2). */
  internal fun queueDeliveryPrint(st: PeopleState, token: String) {
    st.pendingPrints += PrintOp(add = true, kind = "delivery", print = PeoplePrints.delivery(PampaBase64.decode(token)))
  }

  private fun seal(me: PersonIdentity, to: PersonCard, payload: JsonObject): String = seal(me, CdxbRecipient.of(to), payload)

  private fun seal(me: PersonIdentity, to: CdxbRecipient, payload: JsonObject): String =
    PampaBase64.encode(Cdxb.seal(me.work, me.mailbox, me.card, to, payload))

  /** La rubrica va anche agli altri miei dispositivi: una CDXB a me stesso, a priorità normale. */
  private fun queueRubricaSync(st: PeopleState, me: PersonIdentity, c: Contact) {
    st.rubricaVersion++
    queueSelf(st, me, buildJsonObject { put("t", "rubrica"); put("contact", PeopleState.contactJson(c)) }, EnvelopePriority.NORMAL)
  }

  private fun queueSelf(st: PeopleState, me: PersonIdentity, payload: JsonObject, priority: EnvelopePriority) {
    val body = JsonObject(payload + mapOf("origin" to JsonPrimitive(st.deviceTag)))
    enqueue(st, OutEnvelope(me.mailbox, seal(me, CdxbRecipient(me.mailbox, me.card.work.enc, me.card.work.epoch), body), priority = priority), "ai miei dispositivi")
  }

  private fun json(bytes: ByteArray): JsonObject = runCatching { Json.parseToJsonElement(bytes.decodeToString()) as JsonObject }.getOrNull() ?: throw PeopleDetected("contenuto illeggibile")

  companion object {
    private const val MAX_PAGES = 50
    private const val DAY_MS = 86_400_000L
    /** Le lapidi dei contatti tolti viaggiano anche nella copia (R2): un telefono che la riprende non li rimette. */
    private val RUBRICA_KEYS = setOf("v", "contacts", "blocked", "groups", "spaces", "removed")
    /** Il segno di una PMX1 già presa: non è un evento. */
    private val ALREADY_SEEN = PeopleEvent.Message(-1, "", "", JsonObject(emptyMap()), "")
    private const val RESPONDER_NONCE = "pampa/v1/parole-nonce"
    private const val RETRY_OFFLINE_MS = 60_000L
    private const val RETRY_BASE_MS = 5_000L

    /** [a] è una versione più nuova di [b]? `1.2.0` contro `1.10.0`, numero per numero. */
    fun newer(a: String, b: String): Boolean {
      val x = a.split('.').map { it.toIntOrNull() ?: 0 }
      val y = b.split('.').map { it.toIntOrNull() ?: 0 }
      for (i in 0 until maxOf(x.size, y.size)) {
        val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
        if (d != 0) return d > 0
      }
      return false
    }
  }
}

private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
private fun JsonObject.req(k: String): String = s(k) ?: throw PeopleDetected("busta senza «$k»")
private fun JsonObject.int(k: String): Int = (this[k] as? JsonPrimitive)?.intOrNull ?: throw PeopleDetected("busta senza «$k»")
