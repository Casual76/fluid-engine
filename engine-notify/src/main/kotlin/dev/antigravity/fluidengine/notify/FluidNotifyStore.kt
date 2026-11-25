package dev.antigravity.fluidengine.notify

import dev.antigravity.fluidengine.foundation.FluidRecurrenceRule
import dev.antigravity.fluidengine.foundation.notify.FluidReminderDelivery
import java.io.File
import java.time.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

// F8.19 · La memoria di engine-notify: i promemoria in programma, il registro di quelli arrivati, il riepilogo (le
// scelte dell'utente e le notifiche che aspettano). Un file JSON in `noBackupFilesDir`: sopravvive a riavvio e
// aggiornamento dell'app; non va nel backup, perché un promemoria rimesso su un telefono nuovo suonerebbe due volte (lì
// lo rimette l'app, coi suoi dati). Scritto per intero a ogni cambio, in un file nuovo e poi rinominato: un processo che
// muore a metà lascia il file di prima.

/** Un promemoria come sta nella memoria. [lastScheduledMillis] è l'ultimo appuntamento già consegnato. */
internal data class FluidStoredReminder(
  val id: String,
  val time: FluidReminderTime,
  val content: FluidReminderContent,
  val repeat: FluidRecurrenceRule?,
  val exact: Boolean,
  val alarm: Boolean,
  val lastScheduledMillis: Long? = null,
  /** Il prossimo appuntamento armato, per la pagina «Cosa succederà» (null: finito). */
  val nextMillis: Long? = null,
  /** Vero se l'appuntamento armato è esatto (l'allarme esatto era concesso). */
  val armedExact: Boolean = false,
)

/** Tutto ciò che engine-notify ricorda. */
internal data class FluidNotifyMemory(
  val reminders: List<FluidStoredReminder> = emptyList(),
  val deliveries: List<FluidReminderDelivery> = emptyList(),
  val digestEnabled: Boolean = false,
  val digestTimes: List<Int> = listOf(8 * 60, 18 * 60),
  val digestChannels: Set<String> = emptySet(),
  val digestQueue: List<FluidNotifyMessage> = emptyList(),
)

/** Il file della memoria, con accesso serializzato. */
internal class FluidNotifyStore(private val file: File?) {
  private var cached: FluidNotifyMemory? = null

  @Synchronized
  fun read(): FluidNotifyMemory {
    cached?.let { return it }
    val memory = file?.takeIf { it.exists() }?.let { runCatching { FluidNotifyCodec.decode(it.readText()) }.getOrNull() }
      ?: FluidNotifyMemory()
    cached = memory
    return memory
  }

  @Synchronized
  fun update(change: (FluidNotifyMemory) -> FluidNotifyMemory): FluidNotifyMemory {
    val next = change(read())
    cached = next
    val target = file ?: return next
    runCatching {
      target.parentFile?.mkdirs()
      val temp = File(target.parentFile, target.name + ".tmp")
      temp.writeText(FluidNotifyCodec.encode(next))
      if (!temp.renameTo(target)) {
        target.delete()
        temp.renameTo(target)
      }
    }
    return next
  }

  /** Dimentica la copia in memoria (le prove simulano un processo nuovo). */
  @Synchronized
  fun forget() {
    cached = null
  }
}

/** Il formato del file: JSON scritto a mano (niente plugin di serializzazione nell'engine), con una versione. */
internal object FluidNotifyCodec {
  const val Version: Int = 1

  private val json = Json { ignoreUnknownKeys = true }

  fun encode(memory: FluidNotifyMemory): String = buildJsonObject {
    put("v", Version)
    put("reminders", buildJsonArray { memory.reminders.forEach { add(reminder(it)) } })
    put("deliveries", buildJsonArray { memory.deliveries.forEach { add(delivery(it)) } })
    put("digestEnabled", memory.digestEnabled)
    put("digestTimes", buildJsonArray { memory.digestTimes.forEach { add(JsonPrimitive(it)) } })
    put("digestChannels", buildJsonArray { memory.digestChannels.sorted().forEach { add(JsonPrimitive(it)) } })
    put("digestQueue", buildJsonArray { memory.digestQueue.forEach { add(message(it)) } })
  }.toString()

  fun decode(text: String): FluidNotifyMemory {
    val root = json.parseToJsonElement(text).jsonObject
    return FluidNotifyMemory(
      reminders = root.array("reminders").mapNotNull { runCatching { reminder(it.jsonObject) }.getOrNull() },
      deliveries = root.array("deliveries").mapNotNull { runCatching { delivery(it.jsonObject) }.getOrNull() },
      digestEnabled = root.bool("digestEnabled") ?: false,
      digestTimes = root.array("digestTimes").mapNotNull { it.jsonPrimitive.contentOrNull?.toIntOrNull() }
        .ifEmpty { listOf(8 * 60, 18 * 60) },
      digestChannels = root.array("digestChannels").mapNotNull { it.jsonPrimitive.contentOrNull }.toSet(),
      digestQueue = root.array("digestQueue").mapNotNull { runCatching { message(it.jsonObject) }.getOrNull() },
    )
  }

  private fun reminder(r: FluidStoredReminder): JsonObject = buildJsonObject {
    put("id", r.id)
    r.time.epochMillis?.let { put("at", it) }
    r.time.wall?.let { put("wall", it.toString()) }
    put("content", content(r.content))
    r.repeat?.let { put("repeat", it.toRRule()) }
    put("exact", r.exact)
    put("alarm", r.alarm)
    r.lastScheduledMillis?.let { put("last", it) }
    r.nextMillis?.let { put("next", it) }
    put("armedExact", r.armedExact)
  }

  private fun reminder(o: JsonObject): FluidStoredReminder {
    val at = o.long("at")
    val wall = o.string("wall")?.let(LocalDateTime::parse)
    val time = when {
      wall != null -> FluidReminderTime.local(wall)
      at != null -> FluidReminderTime.at(at)
      else -> error("promemoria senza ora")
    }
    return FluidStoredReminder(
      id = o.string("id") ?: error("promemoria senza id"),
      time = time,
      content = content(o["content"]!!.jsonObject),
      repeat = o.string("repeat")?.let { FluidRecurrenceRule.parseRRule(it) },
      exact = o.bool("exact") ?: true,
      alarm = o.bool("alarm") ?: false,
      lastScheduledMillis = o.long("last"),
      nextMillis = o.long("next"),
      armedExact = o.bool("armedExact") ?: false,
    )
  }

  private fun content(c: FluidReminderContent): JsonObject = buildJsonObject {
    put("title", c.title)
    c.text?.let { put("text", it) }
    c.channel?.let { put("channel", it) }
    c.open?.let { put("open", it) }
    c.thread?.let { put("thread", it) }
    put("actions", actions(c.actions))
  }

  private fun content(o: JsonObject): FluidReminderContent = FluidReminderContent(
    title = o.string("title") ?: "",
    text = o.string("text"),
    channel = o.string("channel"),
    open = o.string("open"),
    actions = actions(o.array("actions")),
    thread = o.string("thread"),
  )

  private fun actions(list: List<FluidNotifyAction>): JsonArray = buildJsonArray {
    list.forEach { a ->
      add(buildJsonObject { put("label", a.label); put("key", a.key); put("opensApp", a.opensApp) })
    }
  }

  private fun actions(array: List<JsonElement>): List<FluidNotifyAction> = array.mapNotNull { element ->
    val o = element.jsonObject
    val label = o.string("label") ?: return@mapNotNull null
    val key = o.string("key") ?: return@mapNotNull null
    FluidNotifyAction(label, key, o.bool("opensApp") ?: false)
  }

  private fun delivery(d: FluidReminderDelivery): JsonObject = buildJsonObject {
    put("id", d.id)
    put("title", d.title)
    put("scheduled", d.scheduledMillis)
    put("delivered", d.deliveredMillis)
    d.reason?.let { put("reason", it) }
  }

  private fun delivery(o: JsonObject): FluidReminderDelivery = FluidReminderDelivery(
    id = o.string("id") ?: error("senza id"),
    title = o.string("title") ?: "",
    scheduledMillis = o.long("scheduled") ?: 0L,
    deliveredMillis = o.long("delivered") ?: 0L,
    reason = o.string("reason"),
  )

  private fun message(m: FluidNotifyMessage): JsonObject = buildJsonObject {
    put("id", m.id)
    put("channel", m.channel)
    put("title", m.title)
    m.text?.let { put("text", it) }
    m.thread?.let { put("thread", it) }
    m.open?.let { put("open", it) }
    put("actions", actions(m.actions))
    m.reply?.let { r ->
      put("reply", buildJsonObject {
        put("label", r.label); put("hint", r.hint)
        put("choices", buildJsonArray { r.choices.forEach { add(JsonPrimitive(it)) } })
      })
    }
    m.person?.let { put("person", it) }
    put("time", m.timeMillis)
    m.urgent?.let { put("urgent", it) }
    put("sensitive", m.sensitive)
  }

  private fun message(o: JsonObject): FluidNotifyMessage = FluidNotifyMessage(
    id = o.string("id") ?: error("senza id"),
    channel = o.string("channel") ?: error("senza canale"),
    title = o.string("title") ?: error("senza titolo"),
    text = o.string("text"),
    thread = o.string("thread"),
    open = o.string("open"),
    actions = actions(o.array("actions")),
    reply = (o["reply"] as? JsonObject)?.let { r ->
      FluidNotifyReply(
        label = r.string("label") ?: "Rispondi",
        hint = r.string("hint") ?: "",
        choices = r.array("choices").mapNotNull { it.jsonPrimitive.contentOrNull },
      )
    },
    person = o.string("person"),
    timeMillis = o.long("time") ?: 0L,
    urgent = o.bool("urgent"),
    sensitive = o.bool("sensitive") ?: false,
  )

  private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
  private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
  private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
  private fun JsonObject.array(key: String): List<JsonElement> = (this[key] as? JsonArray)?.jsonArray ?: emptyList()
}
