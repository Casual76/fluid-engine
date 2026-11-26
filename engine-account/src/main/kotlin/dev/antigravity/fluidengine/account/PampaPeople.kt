package dev.antigravity.fluidengine.account

import android.content.Context
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Che cosa c'è nella fila delle persone: «I tuoi dispositivi» per primi (scelta Q1), poi persone e gruppi. */
enum class PeopleRowKind(val wire: String) { DEVICES("devices"), PERSON("person"), GROUP("group") }

/**
 * Una voce della fila delle persone (`people.row`, `b\api.md` § 5.3): nome, iniziali, il seme del minerale (l'avatar)
 * e un [id] **valido solo per l'app che chiama**. Niente chiavi, niente codice Pampa, niente casella (progetto, T10).
 */
data class PeopleRowEntry(
  val id: String,
  val kind: PeopleRowKind,
  val name: String,
  val initials: String,
  /** Il seme del minerale in base64url, per disegnare l'avatar; `null` per «I tuoi dispositivi». */
  val mineral: String?,
  /** Verificato di persona o con le quattro parole: solo a loro va «Avvisa quando arrivo» (§ 3.9). */
  val verified: Boolean,
)

/** Cosa si manda (`progetto.md` § 3.7): l'indirizzo di una pagina, una copia, o l'oggetto vivo che cambia per tutti. */
enum class ShareKind(val wire: String) { LINK("link"), COPY("copy"), LIVE("live") }

/** Una risposta attaccata a una cosa condivisa, quando non c'è Pampa Mail («Mamma: brava!»). */
data class SharedReply(val from: String, val text: String, val at: Long, val mine: Boolean)

/**
 * Una cosa arrivata per quest'app («Condivisi con te», `share.received`): già aperta dallo Store, con chi la manda ([from],
 * per il segno «Da X»), quando, e il contenuto che l'app mittente ha preparato ([payload], JSON dell'app).
 */
data class SharedItem(
  val id: String,
  val from: PeopleRowEntry,
  val kind: ShareKind,
  val title: String,
  val payload: String,
  val receivedAt: Long,
  val replies: List<SharedReply> = emptyList(),
  /** Una risposta a qualcosa che quest'app aveva mandato: [inReplyTo] è l'id che aveva dato `share.send`. */
  val inReplyTo: String? = null,
)

/** Com'è andato un invio. [Queued]: lo Store l'ha chiuso e firmato, e se la rete manca riprova da solo (M3). */
sealed interface ShareResult {
  data class Queued(val id: String) : ShareResult
  /** L'app non è nella tabella dello Store, o la persona non accetta da quest'app. */
  data object NotAllowed : ShareResult
  /** Lo Store non c'è, o è troppo vecchio per le persone: la fila non si mostra (P1). */
  data object NoPeople : ShareResult
  data object SignedOut : ShareResult
  data object UnknownPerson : ShareResult
}

/**
 * Le persone viste da un'app (`progetto.md` § 3.7; `b\api.md` § 5.3). Tutto passa da Pampa Store sul telefono, col
 * provider firmato: l'app non tocca mai la rubrica né una chiave, e non va mai in rete (R9).
 *
 * ```kotlin
 * val row = PampaPeople.row(context)                       // per FluidPeopleRow, nel foglio di condivisione
 * PampaPeople.send(context, row[1].id, ShareKind.COPY, "Il voto di storia", json)
 * PampaPeople.onReceived(context) { items -> notifica("${items[0].from.name} ti ha condiviso…") }
 * ```
 *
 * Senza Store, o con uno Store senza le persone, [row] è vuota e la fila non compare (P1).
 */
object PampaPeople {
  private val handlers = CopyOnWriteArrayList<suspend (List<SharedItem>) -> Unit>()
  private val delivery = PeopleDelivery(handlers)
  @Volatile private var installed = false

  private fun client(context: Context): PampaAccountClient = PampaAccount.client(context).also { c ->
    if (!installed) synchronized(this) {
      if (!installed) {
        val cache = DataStoreAccountCache.of(context.applicationContext)
        c.addChangeListener { what -> if (AccountProtocol.What.SHARE in what) delivery.deliver(c, cache) }
        installed = true
      }
    }
  }

  /** La fila: «I tuoi dispositivi» e fino a otto persone e gruppi recenti. Vuota senza Store o senza contatti. */
  suspend fun row(context: Context): List<PeopleRowEntry> = PeopleCalls.row(client(context))

  /** Manda a [to] (un id della fila). Lo Store cifra, firma e consegna; la coda in uscita riprova se manca la rete. */
  suspend fun send(context: Context, to: String, kind: ShareKind, title: String, payload: String): ShareResult =
    PeopleCalls.send(client(context), to, kind, title, payload)

  /** Le cose arrivate per quest'app dopo [since], già aperte. Rispetta blocchi e interruttori. */
  suspend fun received(context: Context, since: Long = 0): List<SharedItem> = PeopleCalls.received(client(context), since)

  /** «Rispondi» (`FluidQuickReply`): va nella conversazione in Pampa Mail, o resta attaccata alla cosa. */
  suspend fun reply(context: Context, item: String, text: String): ShareResult = PeopleCalls.reply(client(context), item, text)

  /** «Togli dai condivisi» ([muteSender] = falso) o «Non mostrare più da X in questa app» (vero). */
  suspend fun hide(context: Context, item: String, muteSender: Boolean = false): Boolean = PeopleCalls.hide(client(context), item, muteSender)

  /** «I tuoi dispositivi» (scelta Q1): manda l'indirizzo F8.12 di una pagina agli altri tuoi dispositivi. */
  suspend fun sendToMyDevices(context: Context, address: String, title: String = ""): ShareResult = PeopleCalls.devices(client(context), address, title)

  /**
   * Chiamato quando lo Store dice che è arrivato qualcosa (`CHANGED` con `share`): qui l'app mostra **la sua** notifica
   * («Alessio ti ha condiviso una lista»). Si registra all'avvio dell'app (`Application.onCreate`): l'avviso esplicito
   * dello Store fa partire il processo anche con l'app chiusa.
   *
   * Ogni cosa arriva **una volta** (R2): il punto fin dove l'app ha già ricevuto sta nella copia locale del cliente,
   * quindi un'app uccisa e fatta ripartire dall'avviso non riceve di nuovo la posta di sempre; due cose arrivate nello
   * stesso millisecondo arrivano tutte e due; e se [handler] cade, la cosa si riconsegna all'avviso dopo.
   */
  fun onReceived(context: Context, handler: suspend (List<SharedItem>) -> Unit) {
    client(context)
    handlers += handler
  }
}

/**
 * R2 · La consegna di «È arrivato qualcosa» alle app ([PampaPeople.onReceived]). Il punto di consegna è la coppia
 * ultima ora + id già consegnati a quell'ora, salvato in [AccountCache] **dopo** che i gestori hanno finito: prima stava
 * solo in memoria (a ogni riavvio tornava tutto) e confrontava solo l'ora (la seconda cosa dello stesso millisecondo
 * si perdeva), e si segnava prima dei gestori (un gestore caduto la perdeva).
 */
internal class PeopleDelivery(private val handlers: List<suspend (List<SharedItem>) -> Unit>) {
  private val mutex = Mutex()
  @Volatile private var memory: Mark? = null

  /** Fin dove si è consegnato: l'ora più alta, e gli id già consegnati a quell'ora. */
  internal data class Mark(val at: Long, val ids: Set<String>)

  suspend fun deliver(c: PampaAccountClient, cache: AccountCache?): Unit = mutex.withLock {
    val mark = cache?.read(CACHE_DELIVERED)?.let(::parse) ?: memory ?: Mark(0, emptySet())
    // Lo Store dà le cose con l'ora **dopo** `since`: si chiede un millisecondo prima, per quelle della stessa ora.
    val items = PeopleCalls.received(c, mark.at - 1).filter { it.receivedAt > mark.at || (it.receivedAt == mark.at && it.id !in mark.ids) }
    if (items.isEmpty()) return@withLock
    var failed = false
    for (h in handlers) {
      try {
        h(items)
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (_: Exception) {
        failed = true
      }
    }
    // Un gestore caduto: niente segno, all'avviso dopo la cosa si riconsegna (meglio due volte che mai).
    if (failed) return@withLock
    val top = maxOf(mark.at, items.maxOf { it.receivedAt })
    val ids = (if (top == mark.at) mark.ids else emptySet()) + items.filter { it.receivedAt == top }.map { it.id }
    val next = Mark(top, ids)
    memory = next
    cache?.write(CACHE_DELIVERED, encode(next))
    Unit
  }

  private fun encode(m: Mark): String = buildJsonObject {
    put("at", m.at)
    put("ids", JsonArray(m.ids.map(::JsonPrimitive)))
  }.toString()

  private fun parse(text: String): Mark? = runCatching {
    val o = kotlinx.serialization.json.Json.parseToJsonElement(text).jsonObject
    Mark((o["at"] as JsonPrimitive).longOrNull ?: 0, (o["ids"] as? JsonArray)?.map { (it as JsonPrimitive).content }?.toSet().orEmpty())
  }.getOrNull()

  companion object {
    /** La voce della copia locale del cliente col punto di consegna delle persone. */
    const val CACHE_DELIVERED = "peopleDelivered"
  }
}

/** Le chiamate delle persone sul cliente dell'account, senza `Context`: le usa [PampaPeople] e le prove. */
object PeopleCalls {
  private fun has(c: PampaAccountClient) = "people" in c.storeCapabilities

  suspend fun row(c: PampaAccountClient): List<PeopleRowEntry> {
    val r = c.callPeople(AccountProtocol.METHOD_PEOPLE_ROW, JsonObject(emptyMap()))?.takeIf { has(c) } ?: return emptyList()
    if ((r["ok"] as? JsonPrimitive)?.booleanOrNull == false) return emptyList()
    return (r["row"] as? JsonArray)?.map { entry(it.jsonObject) }.orEmpty()
  }

  suspend fun send(c: PampaAccountClient, to: String, kind: ShareKind, title: String, payload: String): ShareResult {
    val body = buildJsonObject {
      put("to", to)
      put("kind", kind.wire)
      put("title", title)
      put("payload", payload)
    }
    return result(c.callPeople(AccountProtocol.METHOD_SHARE_SEND, body)?.takeIf { has(c) })
  }

  suspend fun received(c: PampaAccountClient, since: Long): List<SharedItem> {
    val r = c.callPeople(AccountProtocol.METHOD_SHARE_RECEIVED, buildJsonObject { put("since", since) })?.takeIf { has(c) } ?: return emptyList()
    return (r["items"] as? JsonArray)?.map { item(it.jsonObject) }.orEmpty()
  }

  suspend fun reply(c: PampaAccountClient, item: String, text: String): ShareResult =
    result(c.callPeople(AccountProtocol.METHOD_SHARE_REPLY, buildJsonObject { put("item", item); put("text", text) })?.takeIf { has(c) })

  suspend fun hide(c: PampaAccountClient, item: String, muteSender: Boolean): Boolean =
    (c.callPeople(AccountProtocol.METHOD_SHARE_HIDE, buildJsonObject { put("item", item); put("mute", muteSender) })?.get("ok") as? JsonPrimitive)?.booleanOrNull == true

  suspend fun devices(c: PampaAccountClient, address: String, title: String): ShareResult =
    result(c.callPeople(AccountProtocol.METHOD_DEVICES_SEND, buildJsonObject { put("address", address); put("title", title) })?.takeIf { has(c) })

  private fun result(r: JsonObject?): ShareResult {
    r ?: return ShareResult.NoPeople
    if ((r["ok"] as? JsonPrimitive)?.booleanOrNull == true) return ShareResult.Queued((r["id"] as? JsonPrimitive)?.content.orEmpty())
    return when ((r["reason"] as? JsonPrimitive)?.content) {
      "signed_out" -> ShareResult.SignedOut
      "unknown_person" -> ShareResult.UnknownPerson
      "no_people" -> ShareResult.NoPeople
      else -> ShareResult.NotAllowed
    }
  }

  fun entry(o: JsonObject) = PeopleRowEntry(
    id = o.s("id")!!,
    kind = PeopleRowKind.entries.firstOrNull { it.wire == o.s("kind") } ?: PeopleRowKind.PERSON,
    name = o.s("name").orEmpty(),
    initials = o.s("initials") ?: PampaAccountClient.initialsOf(o.s("name").orEmpty()),
    mineral = o.s("mineral"),
    verified = (o["verified"] as? JsonPrimitive)?.booleanOrNull == true,
  )

  fun item(o: JsonObject) = SharedItem(
    id = o.s("id")!!,
    from = entry(o["from"]!!.jsonObject),
    kind = ShareKind.entries.firstOrNull { it.wire == o.s("kind") } ?: ShareKind.COPY,
    title = o.s("title").orEmpty(),
    payload = o.s("payload") ?: "{}",
    receivedAt = (o["at"] as? JsonPrimitive)?.longOrNull ?: 0,
    replies = (o["replies"] as? JsonArray)?.map { r -> r.jsonObject.let { SharedReply(it.s("from").orEmpty(), it.s("text").orEmpty(), (it["at"] as? JsonPrimitive)?.longOrNull ?: 0, (it["mine"] as? JsonPrimitive)?.booleanOrNull == true) } }.orEmpty(),
    inReplyTo = o.s("inReplyTo"),
  )

  private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
}

/**
 * Il gestore dei metodi delle persone dentro lo Store: lo dà `engine-people` (`PeopleProviderMethods`) al provider di
 * `engine-account-host`. Sta qui, nel protocollo comune, così i due moduli non dipendono l'uno dall'altro. [caller] è il
 * pacchetto di chi chiama, preso dal sistema e già controllato (firma, tabella delle app).
 */
fun interface AccountPeopleMethods {
  suspend fun handle(caller: String, method: String, request: JsonObject?): JsonObject
}
