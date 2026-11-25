package dev.antigravity.fluidengine.people.testing

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import dev.antigravity.fluidengine.people.client.PeopleAuth
import dev.antigravity.fluidengine.people.client.PeopleHttp
import dev.antigravity.fluidengine.people.client.PeopleRequest
import dev.antigravity.fluidengine.people.client.PeopleResponse
import dev.antigravity.fluidengine.people.crypto.PeopleDetected
import dev.antigravity.fluidengine.people.crypto.PeoplePrints
import dev.antigravity.fluidengine.people.crypto.PersonCard
import dev.antigravity.fluidengine.people.crypto.PersonCode
import java.io.IOException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Il server delle persone finto del banco (`d\server-e-chiavi.md` § 13): le stesse regole del Worker vero di
 * `Pampa-account` (F10.5, `src\routes\people.ts`, `casella.ts`, `spazi.ts`, `src\persone\casella.ts`), in memoria e
 * dietro la stessa interfaccia del trasporto ([PeopleHttp]), così il client che lo usa è quello vero. Le prove HTTP lo
 * mettono anche dietro MockWebServer.
 *
 * Ha i tetti del contratto (20 codici, 10 richieste mandate e 20 ricevute, 2.000 buste al giorno, 64 KB a busta, 45 per
 * chiamata, 100 caselle per gruppo), le caselle coi gettoni e i bloccati, il ritiro per dispositivo, il campanello
 * finto con le sveglie raggruppate a 10 secondi, e le manopole del **server ostile** ([hostile]).
 *
 * Non va mai in un'app vera.
 */
class FakePeopleServer(var now: Long = 1_764_000_000_000) {

  /** Un dispositivo di un account: il suo token, il suo punto di ritiro, se ha il canale aperto, se è uscito. */
  inner class Device(val account: Account, val id: String) {
    val token: String = "tok_" + PampaBase64.encode(PampaCrypto.random(12))
    var revoked = false
    var channelOpen = false
    val http: PeopleHttp get() = this@FakePeopleServer.http
    val auth: PeopleAuth = PeopleAuth { token }
  }

  inner class Account(val id: String) {
    val devices = mutableListOf<Device>()
    fun addDevice(): Device = Device(this, "dev_${id}_${devices.size + 1}").also { devices += it; tokens[it.token] = it }
  }

  class Envelope(val n: Long, val kind: String, val from: String, val data: String, val size: Int, val via: String?, val minStore: String, val priority: String, val at: Long)

  class Invite(val kind: String, var usesLeft: Int, val expiresAt: Long)

  inner class Mailbox(val id: String, val owner: Account) {
    val envelopes = ArrayList<Envelope>()
    val prints = HashMap<String, String>()
    val invites = HashMap<String, Invite>()
    val cursors = HashMap<String, Long>()
    var next = 1L
    var closed = false
    val requestsToday = HashMap<Long, Int>()
  }

  class Card(val account: Account, var raw: String, var sig: String, val code: String, val mailbox: String, var at: Long, var findable: Boolean)

  class Space(val id: String, val kind: String, var epoch: Int, var writePrint: String, val owner: Account) {
    var seq = 0L
    val records = LinkedHashMap<String, SpaceRecord>()
  }

  class SpaceRecord(val recordId: String, var version: Long, var epoch: Int, var ciphertext: String, var deleted: Boolean, var seq: Long)

  /** Una sveglia del campanello finto: data-only e vuota, solo la priorità (`k = posta`). */
  data class Bell(val device: String, val priority: String, val at: Long)

  /** Le manopole del server ostile: ognuna, se c'è, cambia quello che il server rende. */
  class Hostile {
    /** Rende un'altra scheda (raw, sig) al posto di quella cercata. */
    var swapLookup: ((code: String) -> Pair<String, String>?)? = null
    /** Cambia una busta al ritiro: la sposta, ne cambia il mittente o i byte. */
    var tamperPickup: ((Envelope) -> Envelope)? = null
    /** Cambia le righe di uno spazio alla lettura (rimanda un record vecchio, lo rinomina, lo sposta). */
    var tamperSpace: ((spaceId: String, rows: List<SpaceRecord>) -> List<SpaceRecord>)? = null
  }

  val hostile = Hostile()
  val accounts = mutableListOf<Account>()
  private val tokens = HashMap<String, Device>()
  val cards = HashMap<String, Card>()
  private val cardsByAccount = HashMap<String, Card>()
  val mailboxes = HashMap<String, Mailbox>()
  val spaces = HashMap<String, Space>()
  val bells = ArrayList<Bell>()
  private val lastBell = HashMap<String, Long>()
  private val lookups = HashMap<String, MutableSet<String>>()
  private val counters = HashMap<String, Int>()
  /** Tutte le richieste arrivate, per controllare che nessuna porti un segreto (critica S1, voce 9 del banco). */
  val requestLog = ArrayList<PeopleRequest>()

  /** Senza rete: ogni chiamata è [IOException], come un telefono in galleria. */
  @Volatile var offline = false

  fun newAccount(devices: Int = 1): Account = Account("acc_${accounts.size + 1}").also { a -> accounts += a; repeat(devices) { a.addDevice() } }

  /** «Fai uscire» un dispositivo: il suo token non vale più, e non trattiene più le buste. */
  fun revoke(device: Device) {
    device.revoked = true
    mailboxOf(device.account)?.let { cleanup(it) }
  }

  fun ringsFor(device: Device): List<Bell> = bells.filter { it.device == device.id }

  val http: PeopleHttp = PeopleHttp { request ->
    if (offline) throw IOException("rete assente")
    synchronized(this) {
      requestLog += request
      try {
        route(request)
      } catch (e: Fail) {
        PeopleResponse(e.status, buildJsonObject {
          put("error", e.code)
          put("message", e.message)
          e.reason?.let { put("reason", it) }
          e.retryAfter?.let { put("retryAfter", it) }
          e.accepted?.let { put("accepted", it) }
        })
      }
    }
  }

  private class Fail(val status: Int, val code: String, override val message: String, val reason: String? = null, val retryAfter: Long? = null, val accepted: Int? = null) : Exception(message)

  private fun day() = now / 86_400_000L
  private fun count(key: String, by: Int, max: Int, message: String, reason: String) {
    val k = "$key|${day()}"
    val n = counters[k] ?: 0
    if (n + by > max) throw Fail(429, "rate_limited", message, reason, retryAfter = 3600)
    counters[k] = n + by
  }

  private fun route(r: PeopleRequest): PeopleResponse {
    val device = r.token?.let { tokens[it] } ?: throw Fail(401, "unauthorized", "Accesso scaduto. Riprova.")
    if (device.revoked) throw Fail(401, "session_revoked", "Questo dispositivo è uscito dall'Account Pampa.")
    val path = r.path.substringBefore('?')
    val query = r.path.substringAfter('?', "").split('&').filter { it.contains('=') }.associate { it.substringBefore('=') to it.substringAfter('=') }
    val body = r.body ?: JsonObject(emptyMap())
    val parts = path.trim('/').split('/')
    return when {
      r.method == "PUT" && path == "/v1/people/card" -> ok(putCard(device, body))
      r.method == "PATCH" && path == "/v1/people/card" -> ok(patchCard(device, body))
      r.method == "POST" && path == "/v1/people/card/lookup" -> ok(lookup(device, body))
      r.method == "POST" && parts.size == 4 && parts[1] == "casella" && parts[3] == "buste" -> ok(deliverTo(device, parts[2], body))
      r.method == "POST" && path == "/v1/casella/consegne" -> ok(sendOut(device, body))
      r.method == "GET" && path == "/v1/casella/buste" -> ok(pickup(device, query["after"]?.toLongOrNull() ?: 0, query["limit"]?.toIntOrNull() ?: 100))
      r.method == "POST" && path == "/v1/casella/gettoni" -> ok(addPrint(device, body))
      r.method == "DELETE" && parts.size == 4 && parts[2] == "gettoni" -> ok(removePrint(device, parts[3]))
      r.method == "POST" && path == "/v1/spazi" -> ok(createSpace(device, body))
      r.method == "POST" && parts.size == 4 && parts[1] == "spazi" && parts[3] == "record" -> ok(writeRecord(device, parts[2], body))
      r.method == "GET" && parts.size == 3 && parts[1] == "spazi" -> ok(readSpace(parts[2], query["since"]?.toLongOrNull() ?: 0))
      r.method == "POST" && parts.size == 4 && parts[1] == "spazi" && parts[3] == "epoca" -> ok(rotateSpace(parts[2], body))
      r.method == "DELETE" && parts.size == 3 && parts[1] == "spazi" -> ok(deleteSpace(device, parts[2]))
      else -> throw Fail(404, "not_found", "Rotta sconosciuta: ${r.method} $path")
    }
  }

  private fun ok(o: JsonObject) = PeopleResponse(200, o)

  // --- Le schede -----------------------------------------------------------------------------------------------------

  private fun putCard(d: Device, b: JsonObject): JsonObject {
    val card = try {
      PersonCard.verify(b.s("card"), b.s("sig"))
    } catch (e: PeopleDetected) {
      throw Fail(400, "bad_request", "La scheda non è valida: ${e.what}", "card_invalid")
    }
    if (kotlin.math.abs(card.at - now) > 86_400_000L) throw Fail(400, "bad_request", "L'ora della scheda è sbagliata.", "card_clock")
    val existing = cardsByAccount[d.account.id]
    val findable = (b["findable"] as? JsonPrimitive)?.booleanOrNull
    if (existing != null && existing.code == card.code) {
      if (card.at <= existing.at) throw Fail(409, "conflict", "C'è già una scheda più nuova.", "card_older")
      existing.raw = card.raw; existing.sig = card.sig; existing.at = card.at
      findable?.let { existing.findable = it }
      return buildJsonObject { put("code", card.code); put("mailbox", card.mailbox); put("findable", existing.findable); put("created", false) }
    }
    cards[card.code]?.let { if (it.account != d.account) throw Fail(409, "conflict", "Questo codice è già di un'altra persona.", "code_taken") }
    existing?.let { cards.remove(it.code); mailboxes[it.mailbox]?.closed = true }
    val c = Card(d.account, card.raw, card.sig, card.code, card.mailbox, card.at, findable ?: true)
    cards[card.code] = c
    cardsByAccount[d.account.id] = c
    mailboxes.getOrPut(card.mailbox) { Mailbox(card.mailbox, d.account) }
    return buildJsonObject { put("code", card.code); put("mailbox", card.mailbox); put("findable", c.findable); put("created", true) }
  }

  private fun patchCard(d: Device, b: JsonObject): JsonObject {
    val c = cardsByAccount[d.account.id] ?: throw Fail(404, "not_found", "Non hai ancora una scheda.")
    c.findable = (b["findable"] as JsonPrimitive).booleanOrNull == true
    return buildJsonObject { put("findable", c.findable) }
  }

  private fun lookup(d: Device, b: JsonObject): JsonObject {
    val code = PersonCode.normalize(b.s("personCode")) ?: throw Fail(400, "bad_request", "Il codice non è valido.", "bad_code")
    val invite = (b["invite"] as? JsonPrimitive)?.content
    val row = cards[code]
    val own = row?.account == d.account
    val invited = row != null && invite != null && mailboxes[row.mailbox]?.invites?.get(PeoplePrints.invite(PampaBase64.decode(invite)))?.let { it.usesLeft > 0 && it.expiresAt > now } == true
    if (!own && !invited) {
      val seen = lookups.getOrPut("${d.account.id}|${day()}") { HashSet() }
      if (code !in seen && seen.size >= 20) throw Fail(429, "rate_limited", "Per oggi hai cercato abbastanza codici.", "code_lookups", 3600)
      seen += code
    }
    if (row == null || (!row.findable && !own && !invited)) throw Fail(404, "not_found", "Nessuna persona con questo codice.")
    hostile.swapLookup?.invoke(code)?.let { (raw, sig) -> return buildJsonObject { put("card", raw); put("sig", sig) } }
    return buildJsonObject { put("card", row.raw); put("sig", row.sig) }
  }

  // --- Le caselle ----------------------------------------------------------------------------------------------------

  private fun mailboxOf(a: Account): Mailbox? = cardsByAccount[a.id]?.let { mailboxes[it.mailbox] }

  private fun me(d: Device): Mailbox = mailboxOf(d.account) ?: throw Fail(409, "conflict", "Prima pubblica la tua scheda.", "no_card")

  private class Item(val to: String?, val token: String?, val invite: String?, val data: String, val size: Int, val minStore: String, val priority: String)

  private fun parse(b: JsonObject, batch: Boolean, self: Boolean): List<Item> {
    val list = (b["envelopes"] as? JsonArray) ?: throw Fail(400, "bad_request", "«envelopes» manca.")
    val max = if (batch) 100 else 45
    if (list.isEmpty() || list.size > max) throw Fail(400, "bad_request", "«envelopes» deve avere da 1 a $max buste.")
    val items = list.map { it.jsonObject }.map { e ->
      val data = e.s("data")
      val size = runCatching { PampaBase64.decode(data).size }.getOrElse { throw Fail(400, "bad_request", "busta non base64url") }
      if (size > 65_536) throw Fail(413, "too_large", "Una busta va fino a 64 KB.")
      val token = (e["token"] as? JsonPrimitive)?.content
      val invite = (e["invite"] as? JsonPrimitive)?.content
      if (token != null && invite != null) throw Fail(400, "bad_request", "Un invito va solo con una richiesta, senza gettone.")
      if (token == null && !self && size > 4096) throw Fail(413, "too_large", "Una richiesta porta solo la scheda e un saluto.")
      val priority = e.s("priority").takeIf { it == "high" || it == "normal" } ?: throw Fail(400, "bad_request", "«priority» deve essere high o normal.")
      if (batch && token == null) throw Fail(400, "bad_request", "Ai gruppi si scrive solo coi gettoni di consegna.")
      Item(if (batch) e.s("to") else null, token, invite, data, size, e.s("minStore"), priority)
    }
    if (!batch && !self && items.any { it.token == null } && items.size != 1) throw Fail(400, "bad_request", "Una richiesta di contatto viaggia da sola.")
    return items
  }

  private fun deliverTo(d: Device, to: String, b: JsonObject): JsonObject {
    val mine = me(d)
    val self = to == mine.id
    val items = parse(b, batch = false, self = self)
    if (items.first().token == null && !self) count("${d.account.id}|requests", 1, 10, "Per oggi hai mandato abbastanza richieste.", "requests")
    count("${d.account.id}|envelopes", items.size, 2000, "Per oggi hai mandato troppe buste.", "envelopes")
    val results = deliver(mine.id, to, items)
    val failure = results.indexOfFirst { it != "ok" && it != "dropped" }
    if (failure >= 0) throw deliveryError(results[failure], failure)
    return buildJsonObject { put("accepted", results.size) }
  }

  private fun sendOut(d: Device, b: JsonObject): JsonObject {
    val mine = me(d)
    val items = parse(b, batch = true, self = false)
    if (items.any { it.to == mine.id }) throw Fail(400, "bad_request", "Per i tuoi dispositivi usa la tua casella direttamente.")
    count("${d.account.id}|envelopes", items.size, 2000, "Per oggi hai mandato troppe buste.", "envelopes")
    // Il server consegna a pezzi da 45: qui tutti subito, ma il risultato è uguale.
    val results = items.map { deliver(mine.id, it.to!!, listOf(it)).first() }
    return buildJsonObject {
      put("accepted", items.size)
      put("delivered", results.count { it == "ok" || it == "dropped" })
      put("queued", 0)
      putJsonArray("results") { results.forEach { add(JsonPrimitive(publicStatus(it))) } }
    }
  }

  private fun publicStatus(s: String) = when (s) { "ok", "dropped" -> "accepted"; "full" -> "mailbox_full"; else -> s }

  private fun deliveryError(s: String, accepted: Int): Fail = when (s) {
    "gone" -> Fail(410, "gone", "Questa persona ha lasciato Pampa.", accepted = accepted)
    "full" -> Fail(507, "mailbox_full", "La casella è piena.", accepted = accepted, retryAfter = 600)
    "rate_limited" -> Fail(429, "rate_limited", "Questa persona ha ricevuto troppe richieste oggi.", "requests_received", 3600, accepted)
    else -> Fail(404, "not_found", "Nessuna casella con questo id.", accepted = accepted)
  }

  private fun deliver(from: String, to: String, items: List<Item>): List<String> {
    val box = mailboxes[to] ?: return items.map { "not_found" }
    if (box.closed) return items.map { "gone" }
    val blocked = box.prints[PeoplePrints.blocked(from)] == "blocked"
    val own = from == box.id
    val out = ArrayList<String>()
    var stop: String? = null
    var high = false
    for (item in items) {
      if (stop != null) { out += stop; continue }
      if (blocked) { out += "dropped"; continue }
      var kind: String
      var via: String? = null
      if (own) {
        kind = "envelope"; via = "self"
      } else if (item.token != null) {
        if (box.prints[PeoplePrints.delivery(PampaBase64.decode(item.token))] != "delivery") { out += "dropped"; continue }
        kind = "envelope"
      } else {
        kind = "request"
        val ip = item.invite?.let { PeoplePrints.invite(PampaBase64.decode(it)) }
        val invite = ip?.let { box.invites[it] }?.takeIf { it.usesLeft > 0 && it.expiresAt > now }
        if (invite != null) {
          via = invite.kind
          invite.usesLeft--
        } else {
          val n = box.requestsToday[day()] ?: 0
          if (n >= 20) { stop = "rate_limited"; out += stop; continue }
          box.requestsToday[day()] = n + 1
        }
      }
      if (box.envelopes.size + 1 > 5000 || box.envelopes.sumOf { it.size } + item.size > 52_428_800) { stop = "full"; out += stop; continue }
      box.envelopes += Envelope(box.next++, kind, from, item.data, item.size, via, item.minStore, item.priority, now)
      if (item.priority == "high") high = true
      out += "ok"
    }
    if (out.any { it == "ok" }) ring(box, if (high) "high" else "normal")
    return out
  }

  /** Il campanello: suona sui dispositivi senza canale aperto, una volta per finestra di 10 secondi. */
  private fun ring(box: Mailbox, priority: String) {
    for (dev in box.owner.devices) {
      if (dev.revoked || dev.channelOpen) continue
      val last = lastBell[dev.id]
      if (last != null && now - last < 10_000) {
        // Nella finestra: una sveglia sola, con la priorità più alta.
        val i = bells.indexOfLast { it.device == dev.id }
        if (i >= 0 && priority == "high" && bells[i].priority == "normal") bells[i] = bells[i].copy(priority = "high")
        continue
      }
      lastBell[dev.id] = now
      bells += Bell(dev.id, priority, now)
    }
  }

  private fun pickup(d: Device, after: Long, limit: Int): JsonObject {
    val box = me(d)
    box.cursors[d.id] = maxOf(box.cursors[d.id] ?: 0, after)
    cleanup(box)
    val rows = box.envelopes.filter { it.n > after }.sortedBy { it.n }
    val page = rows.take(limit).map { hostile.tamperPickup?.invoke(it) ?: it }
    return buildJsonObject {
      putJsonArray("envelopes") {
        for (e in page) addJsonObject {
          put("n", e.n); put("kind", e.kind); put("from", e.from); put("size", e.size); put("data", e.data)
          if (e.via != null) put("via", e.via) else put("via", JsonNull)
          put("minStore", e.minStore); put("priority", e.priority); put("at", e.at)
        }
      }
      put("more", rows.size > limit)
      put("last", page.lastOrNull()?.n ?: after)
    }
  }

  /** Via le buste che tutti i dispositivi attivi hanno preso, e quelle più vecchie di 30 giorni. */
  private fun cleanup(box: Mailbox) {
    val active = box.owner.devices.filter { !it.revoked }
    for (dev in box.owner.devices.filter { it.revoked }) box.cursors.remove(dev.id)
    if (active.isNotEmpty()) {
      val low = active.minOf { box.cursors[it.id] ?: 0 }
      box.envelopes.removeAll { it.n <= low }
    }
    box.envelopes.removeAll { it.at <= now - 30L * 86_400_000L }
  }

  private fun addPrint(d: Device, b: JsonObject): JsonObject {
    val kind = b.s("kind")
    val print = b.s("print")
    val box = me(d)
    when (kind) {
      "delivery", "blocked" -> {
        if (box.prints.size >= 5000) throw Fail(409, "conflict", "Troppe impronte in questa casella.", "too_many_prints")
        box.prints[print] = kind
      }
      "invite.qr" -> box.invites[print] = Invite("qr", 1, now + 86_400_000L)
      "invite.link" -> box.invites[print] = Invite("link", 1, now + 7 * 86_400_000L)
      "invite.group" -> box.invites[print] = Invite("group", 100, now + 86_400_000L)
      else -> throw Fail(400, "bad_request", "tipo di gettone sconosciuto")
    }
    return buildJsonObject { put("kind", kind); put("print", print) }
  }

  private fun removePrint(d: Device, print: String): JsonObject {
    val box = me(d)
    if (box.prints.remove(print) == null && box.invites.remove(print) == null) throw Fail(404, "not_found", "Questa impronta non c'è.")
    return buildJsonObject { put("removed", print) }
  }

  // --- Gli spazi -----------------------------------------------------------------------------------------------------

  private fun createSpace(d: Device, b: JsonObject): JsonObject {
    val id = b.s("spaceId")
    if (spaces.containsKey(id)) throw Fail(409, "conflict", "Lo spazio c'è già.")
    if (spaces.values.count { it.owner == d.account } >= 200) throw Fail(507, "quota_exceeded", "Troppi spazi.")
    spaces[id] = Space(id, b.s("kind"), 1, b.s("writePrint"), d.account)
    return buildJsonObject { put("spaceId", id); put("kind", b.s("kind")); put("epoch", 1) }
  }

  private fun writeRecord(d: Device, id: String, b: JsonObject): JsonObject {
    val sp = spaces[id] ?: throw Fail(404, "not_found", "Nessuno spazio con questo id.")
    val print = PeoplePrints.write(PampaBase64.decode(b.s("writeToken")))
    if (print != sp.writePrint) throw Fail(403, "forbidden", "Non puoi più scrivere in questo spazio.", "write_token")
    val epoch = (b["epoch"] as JsonPrimitive).intOrNull!!
    if (epoch != sp.epoch) throw Fail(409, "conflict", "L'epoca dello spazio è cambiata.", "epoch")
    val recordId = b.s("recordId")
    val base = (b["baseVersion"] as JsonPrimitive).longOrNull!!
    val current = sp.records[recordId]?.version ?: 0
    if (base != current) throw Fail(409, "conflict", "Il record è cambiato nel frattempo.", "version")
    val ct = b.s("ciphertext")
    if (PampaBase64.decode(ct).size > 65_536) throw Fail(413, "too_large", "Un record va fino a 64 KB.")
    sp.seq++
    val rec = sp.records.getOrPut(recordId) { SpaceRecord(recordId, 0, epoch, ct, false, 0) }
    rec.version = base + 1; rec.epoch = epoch; rec.ciphertext = ct; rec.deleted = (b["deleted"] as? JsonPrimitive)?.booleanOrNull == true; rec.seq = sp.seq
    return buildJsonObject { put("recordId", recordId); put("version", rec.version); put("seq", rec.seq) }
  }

  private fun readSpace(id: String, since: Long): JsonObject {
    val sp = spaces[id] ?: throw Fail(404, "not_found", "Nessuno spazio con questo id.")
    var rows = sp.records.values.filter { it.seq > since }.sortedBy { it.seq }
    hostile.tamperSpace?.let { rows = it(id, rows) }
    return buildJsonObject {
      put("spaceId", id)
      put("kind", sp.kind)
      put("epoch", sp.epoch)
      putJsonArray("records") {
        for (r in rows) addJsonObject { put("recordId", r.recordId); put("version", r.version); put("epoch", r.epoch); put("ciphertext", r.ciphertext); put("deleted", r.deleted); put("seq", r.seq) }
      }
      put("last", rows.lastOrNull()?.seq ?: since)
      put("more", false)
    }
  }

  private fun rotateSpace(id: String, b: JsonObject): JsonObject {
    val sp = spaces[id] ?: throw Fail(404, "not_found", "Nessuno spazio con questo id.")
    if (PeoplePrints.write(PampaBase64.decode(b.s("writeToken"))) != sp.writePrint) throw Fail(403, "forbidden", "Il gettone di scrittura non è quello di adesso.", "write_token")
    val epoch = (b["epoch"] as JsonPrimitive).intOrNull!!
    if (epoch != sp.epoch + 1) throw Fail(409, "conflict", "Epoca non valida.", "epoch")
    sp.epoch = epoch
    sp.writePrint = b.s("writePrint")
    return buildJsonObject { put("spaceId", id); put("epoch", epoch) }
  }

  private fun deleteSpace(d: Device, id: String): JsonObject {
    val sp = spaces[id] ?: throw Fail(404, "not_found", "Nessuno spazio con questo id.")
    if (sp.owner != d.account) throw Fail(403, "forbidden", "Solo il proprietario cancella lo spazio.")
    spaces.remove(id)
    return buildJsonObject { put("spaceId", id); put("deleted", true) }
  }

  private fun JsonObject.s(k: String): String = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content ?: throw Fail(400, "bad_request", "Manca «$k».")
}
