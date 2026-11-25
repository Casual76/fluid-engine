package dev.antigravity.fluidengine.people.store

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import dev.antigravity.fluidengine.account.AccountPeopleMethods
import dev.antigravity.fluidengine.account.AccountProtocol
import dev.antigravity.fluidengine.account.PampaAccountClient
import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import dev.antigravity.fluidengine.people.Contact
import dev.antigravity.fluidengine.people.InboxItem
import dev.antigravity.fluidengine.people.PeopleClient
import dev.antigravity.fluidengine.people.PeopleEvent
import dev.antigravity.fluidengine.people.PeopleGroup
import dev.antigravity.fluidengine.people.PeopleState
import dev.antigravity.fluidengine.people.client.EnvelopePriority
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Una notifica semplice dello Store, quando l'app giusta non c'è o non è un'app (`progetto.md` § 3.7 e § 3.9, T21). */
data class StoreNotice(val title: String, val text: String, val address: String? = null, val app: String? = null)

/**
 * Come lo Store avvisa le app (`b\api.md` § 5.4): un broadcast **esplicito** a quel pacchetto, con dentro solo `what`;
 * l'app non si fida del contenuto e rilegge dal provider firmato. Se l'app non c'è, lo Store mostra lui la notifica.
 */
interface AppNotifier {
  /** Avvisa [packageName]; `false` se l'app non è installata (allora c'è [show]). */
  fun notifyApp(packageName: String, what: String): Boolean
  fun show(notice: StoreNotice)
}

/** L'avviso vero: `sendBroadcast` con `setPackage`, e il canale «Persone» dello Store per le notifiche sue. */
class AndroidAppNotifier(private val context: Context) : AppNotifier {
  override fun notifyApp(packageName: String, what: String): Boolean {
    if (runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isFailure) return false
    context.sendBroadcast(
      // Anche a un'app mai aperta o fermata a mano: il broadcast esplicito la fa partire per mostrare la sua notifica.
      Intent(AccountProtocol.ACTION_CHANGED).setPackage(packageName).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        .putStringArrayListExtra(AccountProtocol.EXTRA_WHAT, arrayListOf(what)),
    )
    return true
  }

  override fun show(notice: StoreNotice) {
    val nm = context.getSystemService(NotificationManager::class.java) ?: return
    if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Persone", NotificationManager.IMPORTANCE_DEFAULT))
    val open = notice.address?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)) } ?: context.packageManager.getLaunchIntentForPackage(context.packageName)
    val pending = open?.let { android.app.PendingIntent.getActivity(context, notice.hashCode(), it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), android.app.PendingIntent.FLAG_IMMUTABLE) }
    val n = android.app.Notification.Builder(context, CHANNEL)
      .setSmallIcon(android.R.drawable.ic_dialog_email)
      .setContentTitle(notice.title)
      .setContentText(notice.text)
      .setAutoCancel(true)
      .apply { pending?.let(::setContentIntent) }
      .build()
    nm.notify(notice.hashCode(), n)
  }

  companion object {
    const val CHANNEL = "pampa-persone"
  }
}

/**
 * Quello che il ritiro ha portato, passato alle app (`d\server-e-chiavi.md` § 6, punto 3): una cosa condivisa va nella
 * posta in arrivo dell'app giusta, e l'app riceve l'avviso esplicito `CHANGED` con `share`; una risposta torna all'app
 * che aveva mandato; «I tuoi dispositivi» e le richieste diventano notifiche dello Store. Rispetta blocchi (già fatti
 * dal ritiro) e gli interruttori «Non mostrare più da X in questa app».
 */
class PeopleDispatcher(private val people: PeopleClient, private val notifier: AppNotifier, private val clock: () -> Long = System::currentTimeMillis) {

  suspend fun dispatch(events: List<PeopleEvent>): Set<String> {
    val apps = LinkedHashSet<String>()
    val notices = ArrayList<StoreNotice>()
    people.edit { st ->
      for (e in events) when (e) {
        is PeopleEvent.Message -> handleMessage(st, e, apps, notices)
        is PeopleEvent.FromMyDevices -> {
          val address = (e.payload["address"] as? JsonPrimitive)?.content
          val title = (e.payload["title"] as? JsonPrimitive)?.content?.takeIf { it.isNotEmpty() } ?: "Una pagina dal tuo dispositivo"
          notices += StoreNotice(title, address ?: "Tocca per aprire", address)
        }
        is PeopleEvent.Request -> notices += StoreNotice("${e.request.card.name} vuole aggiungerti", e.request.greeting.ifEmpty { "Apri Persone per accettare" })
        is PeopleEvent.TooNew -> notices += StoreNotice("Ti hanno mandato qualcosa", "Aggiorna Pampa Store per vederlo")
        else -> Unit
      }
    }
    for (app in apps) if (!notifier.notifyApp(app, AccountProtocol.What.SHARE)) {
      // L'app non c'è: la notifica la fa lo Store, con lo stesso testo (T21), e «Installa e apri» sta nella card.
      notices += StoreNotice("Hai ricevuto qualcosa", "Si apre con un'app che non hai ancora: aprila dallo Store", app = app)
    }
    for (n in notices) notifier.show(n)
    return apps
  }

  private fun handleMessage(st: PeopleState, e: PeopleEvent.Message, apps: MutableSet<String>, notices: MutableList<StoreNotice>) {
    val p = e.payload
    fun s(k: String) = (p[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
    val sender = st.contacts[e.code]?.name ?: st.groups[e.group]?.members?.get(e.code)?.name ?: "Qualcuno"
    when (s("t")) {
      "condivisione" -> {
        val app = s("app") ?: return
        val id = s("id") ?: e.messageId
        if (st.inbox.any { it.id == id && it.from == e.code }) return
        st.inbox += InboxItem(id, app, e.code, s("kind") ?: "copy", s("title").orEmpty(), wrap(s("payload")), clock(), e.thread)
        if ("$app|${e.code}" !in st.mutedInApps) apps += app
      }
      "risposta" -> {
        val app = s("app") ?: return
        val item = s("item") ?: return
        st.inbox += InboxItem(e.messageId, app, e.code, "reply", "", wrap(s("text")?.let { buildJsonObject { put("text", it) }.toString() }), clock(), e.thread, inReplyTo = item)
        apps += app
      }
      "messaggio" -> notices += StoreNotice(sender, s("text") ?: "Ti ha scritto", app = "dev.pampa.mail")
      else -> notices += StoreNotice(sender, "Ti ha mandato qualcosa: aggiorna Pampa Store per vederlo")
    }
  }

  private fun wrap(payload: String?): JsonObject = buildJsonObject { put("json", payload ?: "{}") }
}

/**
 * Il campanello (`d\server-e-chiavi.md` § 6): una notifica FCM **vuota**, data-only con il solo `k`, sveglia lo Store.
 * Con `k = posta` lo Store ritira, apre e passa le cose alle app. Niente nome, niente tipo, niente anteprima: tutto
 * quello che si vede lo scrive il telefono dopo aver aperto le buste.
 */
class PeopleBell(private val people: PeopleClient, private val dispatcher: PeopleDispatcher) {
  /** [data] è il contenuto del messaggio FCM (o del campanello finto). Torna le app avvisate. */
  suspend fun ring(data: Map<String, String>): Set<String> {
    if (data["k"] != null && data["k"] != "posta") return emptySet()
    return dispatcher.dispatch(people.onBell())
  }
}

/**
 * I metodi delle persone del provider dello Store (`b\api.md` § 5.3), per `AccountProtocolHandler`: la fila, mandare,
 * ricevere, rispondere, nascondere e «I tuoi dispositivi». Ogni app vede **id suoi** (un HMAC col sale dello Store e il
 * suo pacchetto): un id di Notes non vale in Transit, e nessuna app vede codici, caselle o chiavi.
 */
class PeopleProviderMethods(
  private val people: PeopleClient,
  /** Ci sono altri miei dispositivi? Lo sa lo Store (`GET /v1/devices`); senza, «I tuoi dispositivi» non compare. */
  private val hasOtherDevices: () -> Boolean = { true },
  private val deviceName: () -> String = { "Telefono" },
) : AccountPeopleMethods {

  override suspend fun handle(caller: String, method: String, request: JsonObject?): JsonObject {
    if (!people.persona.exists) return fail("no_people")
    val st = people.state
    return when (method) {
      AccountProtocol.METHOD_PEOPLE_ROW -> row(caller, st)
      AccountProtocol.METHOD_SHARE_SEND -> send(caller, st, request ?: JsonObject(emptyMap()))
      AccountProtocol.METHOD_SHARE_RECEIVED -> received(caller, st, (request?.get("since") as? JsonPrimitive)?.longOrNull ?: 0)
      AccountProtocol.METHOD_SHARE_REPLY -> reply(caller, st, request ?: JsonObject(emptyMap()))
      AccountProtocol.METHOD_SHARE_HIDE -> hide(caller, request ?: JsonObject(emptyMap()))
      AccountProtocol.METHOD_DEVICES_SEND -> {
        val address = (request?.get("address") as? JsonPrimitive)?.content ?: return fail("bad_request")
        val title = (request["title"] as? JsonPrimitive)?.content.orEmpty()
        people.sendToMyDevices(buildJsonObject { put("address", address); put("title", title); put("app", caller); put("from", deviceName()) })
        ok(PampaBase64.encode(PampaCrypto.random(9)))
      }
      else -> fail("unknown_method")
    }
  }

  private fun appId(st: PeopleState, caller: String, what: String): String =
    PampaBase64.encode(PampaCrypto.hmacSha256(PampaBase64.decode(st.appSalt), "$caller|$what".toByteArray()).copyOf(12))

  private fun row(caller: String, st: PeopleState): JsonObject = buildJsonObject {
    put("ok", true)
    putJsonArray("row") {
      if (hasOtherDevices()) addJsonObject {
        put("id", appId(st, caller, "devices")); put("kind", "devices"); put("name", "I tuoi dispositivi"); put("initials", ""); put("verified", true)
      }
      val people = st.contacts.values.filter { it.accepted && it.card.mailbox !in st.blocked }.map { it.lastActivity to entry(st, caller, it) }
      val groups = st.groups.values.map { 0L to groupEntry(st, caller, it) }
      for ((_, e) in (people + groups).sortedByDescending { it.first }.take(ROW_MAX)) add(e)
    }
  }

  private fun entry(st: PeopleState, caller: String, c: Contact): JsonObject = buildJsonObject {
    put("id", appId(st, caller, "p:${c.code}"))
    put("kind", "person")
    put("name", c.name)
    put("initials", PampaAccountClient.initialsOf(c.name))
    put("mineral", PampaBase64.encode(c.card.mineral))
    put("verified", this@PeopleProviderMethods.people.isVerified(c.code))
  }

  private fun groupEntry(st: PeopleState, caller: String, g: PeopleGroup): JsonObject = buildJsonObject {
    put("id", appId(st, caller, "g:${g.id}"))
    put("kind", "group")
    put("name", g.name)
    put("initials", PampaAccountClient.initialsOf(g.name))
    put("verified", false)
  }

  private suspend fun send(caller: String, st: PeopleState, r: JsonObject): JsonObject {
    val to = (r["to"] as? JsonPrimitive)?.content ?: return fail("bad_request")
    val id = PampaBase64.encode(PampaCrypto.random(12))
    val payload = buildJsonObject {
      put("t", "condivisione")
      put("id", id)
      put("app", caller)
      put("kind", (r["kind"] as? JsonPrimitive)?.content ?: "copy")
      put("title", (r["title"] as? JsonPrimitive)?.content.orEmpty())
      put("payload", (r["payload"] as? JsonPrimitive)?.content ?: "{}")
    }
    if (to == appId(st, caller, "devices")) {
      people.sendToMyDevices(JsonObject(payload + mapOf("from" to JsonPrimitive(deviceName()))))
      return ok(id)
    }
    st.contacts.values.firstOrNull { it.accepted && appId(st, caller, "p:${it.code}") == to }?.let {
      people.sendMessage(it.code, payload, EnvelopePriority.HIGH)
      return ok(id)
    }
    st.groups.values.firstOrNull { appId(st, caller, "g:${it.id}") == to }?.let {
      people.groupMessage(it.id, payload)
      return ok(id)
    }
    return fail("unknown_person")
  }

  private fun received(caller: String, st: PeopleState, since: Long): JsonObject = buildJsonObject {
    put("ok", true)
    putJsonArray("items") {
      for (i in st.inbox) {
        if (i.app != caller || i.hidden || i.at <= since || "$caller|${i.from}" in st.mutedInApps) continue
        val c = st.contacts[i.from]
        addJsonObject {
          put("id", i.id)
          putJsonObject("from") {
            put("id", appId(st, caller, "p:${i.from}"))
            put("kind", "person")
            put("name", c?.name ?: "Qualcuno")
            put("initials", PampaAccountClient.initialsOf(c?.name ?: "?"))
            c?.let { put("mineral", PampaBase64.encode(it.card.mineral)) }
            put("verified", c != null && people.isVerified(i.from))
          }
          put("kind", i.kind)
          put("title", i.title)
          put("payload", (i.payload["json"] as? JsonPrimitive)?.content ?: "{}")
          put("at", i.at)
          i.inReplyTo?.let { put("inReplyTo", it) }
          putJsonArray("replies") { i.replies.forEach { add(it) } }
        }
      }
    }
  }

  private suspend fun reply(caller: String, st: PeopleState, r: JsonObject): JsonObject {
    val itemId = (r["item"] as? JsonPrimitive)?.content ?: return fail("bad_request")
    val text = (r["text"] as? JsonPrimitive)?.content?.take(REPLY_MAX) ?: return fail("bad_request")
    val item = st.inbox.firstOrNull { it.id == itemId && it.app == caller } ?: return fail("not_allowed")
    val payload = buildJsonObject { put("t", "risposta"); put("item", item.id); put("app", caller); put("text", text) }
    val messageId = people.sendMessage(item.from, payload, EnvelopePriority.HIGH)
    people.edit { s ->
      val i = s.inbox.indexOfFirst { it.id == itemId && it.app == caller }
      if (i >= 0) s.inbox[i] = s.inbox[i].copy(replies = s.inbox[i].replies + buildJsonObject { put("from", "Tu"); put("text", text); put("at", System.currentTimeMillis()); put("mine", true) })
    }
    return ok(messageId)
  }

  private suspend fun hide(caller: String, r: JsonObject): JsonObject {
    val itemId = (r["item"] as? JsonPrimitive)?.content ?: return fail("bad_request")
    val mute = (r["mute"] as? JsonPrimitive)?.content == "true"
    var found = false
    people.edit { s ->
      val i = s.inbox.indexOfFirst { it.id == itemId && it.app == caller }
      if (i >= 0) {
        found = true
        if (mute) s.mutedInApps += "$caller|${s.inbox[i].from}"
        s.inbox[i] = s.inbox[i].copy(hidden = true)
      }
    }
    return buildJsonObject { put("ok", found) }
  }

  private fun ok(id: String) = buildJsonObject { put("ok", true); put("id", id) }
  private fun fail(reason: String) = buildJsonObject { put("ok", false); put("reason", reason) }

  companion object {
    /** La fila: fino a otto persone e gruppi recenti, più «Altri…» (`progetto.md` § 3.7). */
    const val ROW_MAX = 8
    const val REPLY_MAX = 1000
  }
}
