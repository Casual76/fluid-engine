package dev.antigravity.fluidengine.people

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.people.client.OutEnvelope
import dev.antigravity.fluidengine.people.crypto.PersonCard
import java.io.File
import java.util.TreeMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Come una persona è entrata in rubrica (`progetto.md` § 3.6): di persona è verificata subito, a distanza no. */
enum class ContactVia(val wire: String) { CODE("code"), QR("qr"), LINK("link"), NEARBY("nearby"), GROUP("group") }

/** Come è stata verificata: di persona (QR, vicinanza: la chiave intera) o con le quattro parole al telefono. */
enum class VerifyMethod(val wire: String) { QR("qr"), NEARBY("nearby"), WORDS("words") }

/** La verifica, con le epoche di lavoro dei due quando è stata fatta. */
data class Verification(val method: VerifyMethod, val myEpoch: Int, val theirEpoch: Int, val at: Long)

/**
 * Lo scambio delle quattro parole con l'impegno (rapporto F10.6, il «compleanno»). Chi comincia ([committer]) tiene il
 * suo nonce e manda solo l'impegno; chi risponde manda il suo nonce; poi chi ha cominciato rivela il suo. Le parole si
 * possono mostrare solo quando ci sono i due nonce ([ready]). Vale per la coppia di epoche [myEpoch] e [theirEpoch]:
 * a un rinnovo ne comincia uno nuovo.
 */
data class WordsExchange(
  val committer: Boolean,
  val myEpoch: Int,
  val theirEpoch: Int,
  val myNonce: ByteArray,
  val theirCommit: ByteArray? = null,
  val theirNonce: ByteArray? = null,
  val revealed: Boolean = false,
) {
  val ready: Boolean get() = theirNonce != null && (!committer || revealed)
}

/** Quello che la conversazione deve dire, una volta, finché l'utente non l'ha visto. */
enum class ContactNotice(val wire: String) {
  /** «Le parole con X sono cambiate perché X ha rinnovato le chiavi»: la verifica a distanza è da rifare. */
  WORDS_CHANGED_THEIRS("words_changed_theirs"),
  /** «Le parole sono cambiate perché hai rinnovato le tue chiavi». */
  WORDS_CHANGED_MINE("words_changed_mine"),
}

/**
 * Un contatto della rubrica (`d\persone-e-sintonia.md` § 4.1): la sua scheda, i due gettoni di consegna, la
 * conversazione a epoche, come è entrato, se e come è verificato. La rubrica sta in classe consegna; la copia per gli
 * altri tuoi dispositivi viaggia in una CDXB a te stesso.
 */
data class Contact(
  val card: PersonCard,
  /** Il gettone che **lui** mi ha dato: lo uso per scrivergli. `null` finché non accetta. */
  val theirToken: String?,
  /** Il gettone che **io** gli ho dato: la mia casella ne tiene l'impronta. */
  val myToken: String,
  val chatId: String,
  val chatKeys: Map<Int, ByteArray>,
  val chatEpoch: Int,
  val via: ContactVia,
  val verification: Verification? = null,
  val words: WordsExchange? = null,
  val notice: ContactNotice? = null,
  val nickname: String? = null,
  val addedAt: Long = 0,
  val lastActivity: Long = 0,
) {
  val code: String get() = card.code
  val name: String get() = nickname ?: card.name
  val accepted: Boolean get() = theirToken != null
}

/** Una richiesta arrivata, da accettare, ignorare o bloccare. Porta solo la scheda e un saluto. */
data class IncomingRequest(
  val n: Long,
  val card: PersonCard,
  val greeting: String,
  val token: String,
  val chatId: String,
  val chatKey: ByteArray,
  /** Come è arrivata, per il server: `qr`, `link`, `group` (un invito), o `null` (col codice). */
  val via: String?,
  val invite: String?,
  val wordsCommit: ByteArray?,
  /** La prova di aver visto il mio QR: se torna, la persona è verificata anche per me. */
  val qrProof: String?,
  val at: Long,
)

/** Un membro di un gruppo come lo conosce l'amministratore: niente codice da mostrare agli altri (P3). */
data class GroupMember(val name: String, val code: String, val mailbox: String, val root: String, val enc: String, val sign: String, val workEpoch: Int, val token: String)

data class PeopleGroup(
  val id: String,
  val name: String,
  val admin: String,
  val epoch: Int,
  val keys: Map<Int, ByteArray>,
  val members: Map<String, GroupMember>,
  val myToken: String,
)

/** Un invito che ho dato io (QR, link, gruppo): solo io so che è mio. Il segreto del QR non va mai al server. */
data class GivenInvite(val token: String, val kind: String, val group: String? = null, val qrSecret: ByteArray? = null, val expiresAt: Long = 0)

/** Una busta già chiusa e firmata nella coda in uscita (critica M3): si riprova da sola, senza chiavi. */
data class OutboxItem(val id: String, val envelope: OutEnvelope, val group: Boolean, val attempts: Int, val notBefore: Long, val createdAt: Long, val label: String)

/**
 * Una cosa arrivata per un'app (`share.received`): già aperta dallo Store, con chi la manda e quando. [app] è il
 * pacchetto che la deve mostrare; [payload] è quello che l'app ha preparato (link, copia, oggetto vivo).
 */
data class InboxItem(
  val id: String,
  val app: String,
  val from: String,
  val kind: String,
  val title: String,
  val payload: JsonObject,
  val at: Long,
  val thread: String,
  val notified: Boolean = false,
  val hidden: Boolean = false,
  /** Le risposte rimaste attaccate alla cosa, senza Pampa Mail (`progetto.md` § 3.7). */
  val replies: List<JsonObject> = emptyList(),
  /** Una risposta a una cosa che avevo mandato io: l'id che le avevo dato. */
  val inReplyTo: String? = null,
)

/**
 * Tutto quello che lo Store sa delle persone, tranne la persona (che sta in [PersonaStore]): la rubrica, le richieste,
 * i bloccati, i gruppi, gli inviti, la coda in uscita, il punto di ritiro di questo dispositivo, le cose arrivate per
 * le app. Un file solo, chiuso in classe consegna.
 */
class PeopleState {
  val contacts = TreeMap<String, Contact>()
  val requests = TreeMap<Long, IncomingRequest>()
  val blocked = sortedSetOf<String>()
  val groups = TreeMap<String, PeopleGroup>()
  val pendingGroupTokens = TreeMap<String, String>()
  val invites = TreeMap<String, GivenInvite>()
  val outbox = ArrayList<OutboxItem>()
  val inbox = ArrayList<InboxItem>()
  /** Gli id dei messaggi già presi (PMX1): lo stesso messaggio non entra due volte. */
  val seenMessages = LinkedHashSet<String>()
  /** I dispositivi che non mostrano più «Da X» in un'app: `app|codice`. */
  val mutedInApps = sortedSetOf<String>()
  /** Gli spazi condivisi di cui ho la chiave (`SharedSpaces`), così come arrivano: id → stato. */
  val spaces = TreeMap<String, JsonObject>()
  var cursor: Long = 0
  var rubricaVersion: Long = 0
  /** Un nome casuale di questo dispositivo: le buste a me stesso che ho mandato io non le riapplico. */
  var deviceTag: String = PampaBase64.encode(dev.antigravity.fluidengine.account.crypto.PampaCrypto.random(9))
  /** Il sale degli id della fila delle persone: ogni app vede id suoi, che non valgono in un'altra app. */
  var appSalt: String = PampaBase64.encode(dev.antigravity.fluidengine.account.crypto.PampaCrypto.random(32))

  fun toJson(): JsonObject = buildJsonObject {
    put("v", 1)
    put("cursor", cursor)
    put("rubricaVersion", rubricaVersion)
    put("deviceTag", deviceTag)
    put("appSalt", appSalt)
    putJsonObject("spaces") { spaces.forEach { (k, v) -> put(k, v) } }
    putJsonArray("contacts") { contacts.values.forEach { add(contactJson(it)) } }
    putJsonArray("requests") { requests.values.forEach { add(requestJson(it)) } }
    putJsonArray("blocked") { blocked.forEach { add(JsonPrimitive(it)) } }
    putJsonArray("groups") { groups.values.forEach { add(groupJson(it)) } }
    putJsonObject("pendingGroupTokens") { pendingGroupTokens.forEach { (k, v) -> put(k, v) } }
    putJsonArray("invites") { invites.values.forEach { i -> add(buildJsonObject { put("token", i.token); put("kind", i.kind); i.group?.let { put("group", it) }; i.qrSecret?.let { put("secret", b(it)) }; put("expiresAt", i.expiresAt) }) } }
    putJsonArray("outbox") {
      outbox.forEach { o -> add(buildJsonObject { put("id", o.id); put("e", o.envelope.toJson(withTo = true)); put("group", o.group); put("attempts", o.attempts); put("notBefore", o.notBefore); put("createdAt", o.createdAt); put("label", o.label) }) }
    }
    putJsonArray("inbox") { inbox.forEach { add(inboxJson(it)) } }
    putJsonArray("seen") { seenMessages.toList().takeLast(SEEN_KEEP).forEach { add(JsonPrimitive(it)) } }
    putJsonArray("muted") { mutedInApps.forEach { add(JsonPrimitive(it)) } }
  }

  companion object {
    private const val SEEN_KEEP = 5000

    fun fromJson(o: JsonObject): PeopleState = PeopleState().apply {
      cursor = o.long("cursor")
      rubricaVersion = o.long("rubricaVersion")
      o.opt("deviceTag")?.let { deviceTag = it }
      o.opt("appSalt")?.let { appSalt = it }
      (o["spaces"] as? JsonObject)?.forEach { (k, v) -> spaces[k] = v.jsonObject }
      o.arr("contacts").forEach { val c = contact(it.jsonObject); contacts[c.code] = c }
      o.arr("requests").forEach { val r = request(it.jsonObject); requests[r.n] = r }
      o.arr("blocked").forEach { blocked += (it as JsonPrimitive).content }
      o.arr("groups").forEach { val g = group(it.jsonObject); groups[g.id] = g }
      (o["pendingGroupTokens"] as? JsonObject)?.forEach { (k, v) -> pendingGroupTokens[k] = (v as JsonPrimitive).content }
      o.arr("invites").forEach { val i = it.jsonObject; invites[i.str("token")] = GivenInvite(i.str("token"), i.str("kind"), i.opt("group"), i.opt("secret")?.let(::d), i.long("expiresAt")) }
      o.arr("outbox").forEach {
        val i = it.jsonObject
        outbox += OutboxItem(i.str("id"), OutEnvelope.fromJson(i["e"]!!.jsonObject), i.bool("group"), i.long("attempts").toInt(), i.long("notBefore"), i.long("createdAt"), i.opt("label") ?: "")
      }
      o.arr("inbox").forEach { inbox += inboxItem(it.jsonObject) }
      o.arr("seen").forEach { seenMessages += (it as JsonPrimitive).content }
      o.arr("muted").forEach { mutedInApps += (it as JsonPrimitive).content }
    }

    fun contactJson(c: Contact): JsonObject = buildJsonObject {
      put("card", c.card.raw)
      put("sig", c.card.sig)
      c.theirToken?.let { put("theirToken", it) }
      put("myToken", c.myToken)
      put("chatId", c.chatId)
      putJsonObject("chat") { c.chatKeys.forEach { (e, k) -> put(e.toString(), b(k)) } }
      put("chatEpoch", c.chatEpoch)
      put("via", c.via.wire)
      c.verification?.let { v -> putJsonObject("verified") { put("method", v.method.wire); put("myEpoch", v.myEpoch); put("theirEpoch", v.theirEpoch); put("at", v.at) } }
      c.words?.let { w ->
        putJsonObject("words") {
          put("committer", w.committer); put("myEpoch", w.myEpoch); put("theirEpoch", w.theirEpoch); put("myNonce", b(w.myNonce))
          w.theirCommit?.let { put("theirCommit", b(it)) }; w.theirNonce?.let { put("theirNonce", b(it)) }; put("revealed", w.revealed)
        }
      }
      c.notice?.let { put("notice", it.wire) }
      c.nickname?.let { put("nickname", it) }
      put("addedAt", c.addedAt)
      put("lastActivity", c.lastActivity)
    }

    fun contact(o: JsonObject): Contact = Contact(
      card = PersonCard.verify(o.str("card"), o.str("sig")),
      theirToken = o.opt("theirToken"),
      myToken = o.str("myToken"),
      chatId = o.str("chatId"),
      chatKeys = (o["chat"] as JsonObject).entries.associate { it.key.toInt() to d((it.value as JsonPrimitive).content) },
      chatEpoch = o.long("chatEpoch").toInt(),
      via = ContactVia.entries.first { it.wire == o.str("via") },
      verification = (o["verified"] as? JsonObject)?.let { v -> Verification(VerifyMethod.entries.first { it.wire == v.str("method") }, v.long("myEpoch").toInt(), v.long("theirEpoch").toInt(), v.long("at")) },
      words = (o["words"] as? JsonObject)?.let { w -> WordsExchange(w.bool("committer"), w.long("myEpoch").toInt(), w.long("theirEpoch").toInt(), d(w.str("myNonce")), w.opt("theirCommit")?.let(::d), w.opt("theirNonce")?.let(::d), w.bool("revealed")) },
      notice = o.opt("notice")?.let { n -> ContactNotice.entries.firstOrNull { it.wire == n } },
      nickname = o.opt("nickname"),
      addedAt = o.long("addedAt"),
      lastActivity = o.long("lastActivity"),
    )

    private fun requestJson(r: IncomingRequest): JsonObject = buildJsonObject {
      put("n", r.n); put("card", r.card.raw); put("sig", r.card.sig); put("greeting", r.greeting); put("token", r.token); put("chatId", r.chatId); put("chatKey", b(r.chatKey))
      r.via?.let { put("via", it) }; r.invite?.let { put("invite", it) }; r.wordsCommit?.let { put("wordsCommit", b(it)) }; r.qrProof?.let { put("qrProof", it) }; put("at", r.at)
    }

    private fun request(o: JsonObject) = IncomingRequest(o.long("n"), PersonCard.verify(o.str("card"), o.str("sig")), o.str("greeting"), o.str("token"), o.str("chatId"), d(o.str("chatKey")), o.opt("via"), o.opt("invite"), o.opt("wordsCommit")?.let(::d), o.opt("qrProof"), o.long("at"))

    fun memberJson(m: GroupMember): JsonObject = buildJsonObject {
      put("name", m.name); put("code", m.code); put("mailbox", m.mailbox); put("root", m.root); put("enc", m.enc); put("sign", m.sign); put("workEpoch", m.workEpoch); put("token", m.token)
    }

    fun member(o: JsonObject) = GroupMember(o.str("name"), o.str("code"), o.str("mailbox"), o.str("root"), o.str("enc"), o.str("sign"), o.long("workEpoch").toInt(), o.str("token"))

    private fun groupJson(g: PeopleGroup): JsonObject = buildJsonObject {
      put("id", g.id); put("name", g.name); put("admin", g.admin); put("epoch", g.epoch); put("myToken", g.myToken)
      putJsonObject("keys") { g.keys.forEach { (e, k) -> put(e.toString(), b(k)) } }
      putJsonArray("members") { g.members.values.forEach { add(memberJson(it)) } }
    }

    private fun group(o: JsonObject) = PeopleGroup(
      o.str("id"), o.str("name"), o.str("admin"), o.long("epoch").toInt(),
      (o["keys"] as JsonObject).entries.associate { it.key.toInt() to d((it.value as JsonPrimitive).content) },
      o.arr("members").map { member(it.jsonObject) }.associateBy { it.code }, o.str("myToken"),
    )

    private fun inboxJson(i: InboxItem): JsonObject = buildJsonObject {
      put("id", i.id); put("app", i.app); put("from", i.from); put("kind", i.kind); put("title", i.title); put("payload", i.payload); put("at", i.at); put("thread", i.thread)
      put("notified", i.notified); put("hidden", i.hidden); putJsonArray("replies") { i.replies.forEach { add(it) } }
      i.inReplyTo?.let { put("inReplyTo", it) }
    }

    private fun inboxItem(o: JsonObject) = InboxItem(
      o.str("id"), o.str("app"), o.str("from"), o.str("kind"), o.str("title"), o["payload"]!!.jsonObject, o.long("at"), o.str("thread"),
      o.bool("notified"), o.bool("hidden"), o.arr("replies").map { it.jsonObject }, o.opt("inReplyTo"),
    )

    internal fun b(bytes: ByteArray) = PampaBase64.encode(bytes)
    internal fun d(text: String) = PampaBase64.decode(text)
  }
}

private fun JsonObject.str(k: String): String = (this[k] as JsonPrimitive).content
private fun JsonObject.opt(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
private fun JsonObject.long(k: String): Long = (this[k] as? JsonPrimitive)?.longOrNull ?: (this[k] as? JsonPrimitive)?.intOrNull?.toLong() ?: 0L
private fun JsonObject.bool(k: String): Boolean = (this[k] as? JsonPrimitive)?.booleanOrNull == true
private fun JsonObject.arr(k: String): List<JsonElement> = (this[k] as? JsonArray) ?: emptyList()

/** Il file dello stato delle persone, chiuso in classe consegna ([PersonKeyRole.WORK]). */
class PeopleStateStore(private val file: File, private val vault: PersonVault) {
  @Volatile private var cached: PeopleState? = null

  @Synchronized
  fun load(): PeopleState {
    cached?.let { return it }
    val state = if (file.exists()) PeopleState.fromJson(Json.parseToJsonElement(vault.open(PersonKeyRole.WORK, file.readBytes(), AAD).decodeToString()).jsonObject) else PeopleState()
    cached = state
    return state
  }

  @Synchronized
  fun save(state: PeopleState) {
    file.parentFile?.mkdirs()
    val tmp = File(file.parentFile, file.name + ".nuovo")
    tmp.writeBytes(vault.seal(PersonKeyRole.WORK, state.toJson().toString().toByteArray(), AAD))
    if (!tmp.renameTo(file)) {
      file.delete()
      check(tmp.renameTo(file)) { "non riesco a scrivere ${file.name}" }
    }
    cached = state
  }

  /** Rilegge dal disco: un altro processo (il campanello in sottofondo) può averlo cambiato. */
  @Synchronized
  fun reload(): PeopleState {
    cached = null
    return load()
  }

  @Synchronized
  fun wipe() {
    if (file.exists()) {
      runCatching { file.writeBytes(ByteArray(file.length().toInt())) }
      file.delete()
    }
    cached = null
  }

  private companion object {
    val AAD = "pampa/v1/persone|stato".toByteArray()
  }
}
