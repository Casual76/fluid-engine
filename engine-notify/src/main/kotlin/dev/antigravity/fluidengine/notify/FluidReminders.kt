package dev.antigravity.fluidengine.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import dev.antigravity.fluidengine.foundation.FluidRecurrenceRule
import dev.antigravity.fluidengine.foundation.FluidRecurrenceText
import dev.antigravity.fluidengine.foundation.notify.FluidReminderDelivery
import dev.antigravity.fluidengine.foundation.notify.FluidReminderInfo
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

// F8.19 · `promemoria-affidabili`: un pianificatore unico. `FluidReminders.schedule(id, quando, contenuto)` salva il
// promemoria, sceglie l'allarme giusto (esatto se c'è il permesso, se no una finestra di dieci minuti), lo rimette dopo
// riavvio, aggiornamento e cambio d'ora, e registra quando è arrivato davvero. Il riferimento Apple è
// `UNCalendarNotificationTrigger` (fiducia alta): un'ora del calendario, che resta l'ora locale anche se cambia il fuso.
//
// La scelta del modulo: i promemoria stanno **dentro engine-notify** e non in un `engine-reminders` a sé. Un promemoria è
// una notifica che parte più tardi: usa gli stessi canali, la stessa porta del ritmo, lo stesso permesso e la stessa
// pagina «Le notifiche non arrivano?»; e il riepilogo delle notifiche usa lo stesso allarme dei promemoria. Due moduli
// dovrebbero dipendere l'uno dall'altro in entrambe le direzioni.

/**
 * Quando suona un promemoria. Non è un `enum` né una classe `sealed` (regola 3.11).
 *
 * - [at]: un istante preciso. Se l'utente cambia fuso, suona allo stesso istante (un'altra ora sull'orologio).
 * - [local]: un'ora sull'orologio («domani alle 8:00»). Se l'utente cambia fuso, o arriva l'ora legale, resta alle 8:00
 *   dell'orologio, come `UNCalendarNotificationTrigger`. È la scelta giusta per quasi tutti i promemoria delle persone.
 */
class FluidReminderTime private constructor(internal val epochMillis: Long?, internal val wall: LocalDateTime?) {
  /** L'istante in cui cade, nella [zone] di adesso. */
  fun instantMillis(zone: ZoneId): Long = epochMillis ?: ZonedDateTime.of(wall!!, zone).toInstant().toEpochMilli()

  /** L'ora sull'orologio nella [zone]: per le ripetizioni, che contano i giorni sull'orologio. */
  internal fun wallIn(zone: ZoneId): LocalDateTime = wall ?: LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis!!), zone)

  override fun equals(other: Any?): Boolean = other is FluidReminderTime && other.epochMillis == epochMillis && other.wall == wall
  override fun hashCode(): Int = 31 * (epochMillis?.hashCode() ?: 0) + (wall?.hashCode() ?: 0)
  override fun toString(): String = epochMillis?.let { "FluidReminderTime.at($it)" } ?: "FluidReminderTime.local($wall)"

  companion object {
    /** Un istante, in millisecondi dall'epoca. */
    fun at(epochMillis: Long): FluidReminderTime = FluidReminderTime(epochMillis, null)

    /** Un istante. */
    fun at(instant: Instant): FluidReminderTime = at(instant.toEpochMilli())

    /** Un'ora sull'orologio, nel fuso del telefono quando arriva. */
    fun local(dateTime: LocalDateTime): FluidReminderTime = FluidReminderTime(null, dateTime)
  }
}

/**
 * Cosa mostra un promemoria.
 *
 * @param channel il canale (dichiarato dall'app), o null per quello dell'engine, «Promemoria» (che compare in alto).
 * @param open l'indirizzo da aprire al tocco, o l'app.
 * @param actions i tasti; il tocco arriva all'app come [FluidNotifyEvent.ReminderAction].
 * @param thread il filo, per raccogliere più promemoria («2 promemoria»).
 */
data class FluidReminderContent(
  val title: String,
  val text: String? = null,
  val channel: String? = null,
  val open: String? = null,
  val actions: List<FluidNotifyAction> = emptyList(),
  val thread: String? = null,
)

/**
 * L'appuntamento da armare per un promemoria: l'istante in programma ([scheduledMillis]), che può essere già passato (il
 * telefono era spento: si consegna subito, in ritardo). Null se il promemoria non suona più.
 *
 * - Senza ripetizione: l'ora del promemoria, finché non è stata consegnata ([lastScheduledMillis] nullo).
 * - Con la ripetizione ([repeat], sull'orologio della [zone]): la prima occorrenza dopo l'ultima consegnata; se ne sono
 *   passate più d'una mentre il telefono era spento, **solo la più recente** (come iOS, che non recupera le ripetizioni
 *   perse: un promemoria di ieri non serve).
 *
 * Pura.
 */
internal fun fluidReminderDue(
  time: FluidReminderTime,
  repeat: FluidRecurrenceRule?,
  zone: ZoneId,
  lastScheduledMillis: Long?,
  nowMillis: Long,
): Long? {
  if (repeat == null) return if (lastScheduledMillis == null) time.instantMillis(zone) else null
  val start = time.wallIn(zone)
  var latestPast: Long? = null
  var steps = 0
  for (occurrence in repeat.occurrences(start, zone)) {
    if (++steps > MaxOccurrenceSteps) break
    val millis = occurrence.toInstant().toEpochMilli()
    if (lastScheduledMillis != null && millis <= lastScheduledMillis) continue
    if (millis <= nowMillis) {
      latestPast = millis
      continue
    }
    return latestPast ?: millis
  }
  return latestPast
}

/** Quante occorrenze si scorrono al massimo per trovare la prossima: più di cent'anni di una regola giornaliera. */
private const val MaxOccurrenceSteps = 40_000

/** Perché un promemoria è arrivato tardi, o null se è arrivato in orario. Pura. */
internal fun fluidReminderLateReason(
  scheduledMillis: Long,
  deliveredMillis: Long,
  afterRestore: Boolean,
  exact: Boolean,
  labels: FluidNotifyLabels,
): String? {
  val delay = deliveredMillis - scheduledMillis
  if (delay <= FluidNotifyProfile.LateThresholdMs) return null
  return when {
    afterRestore -> labels.lateDeviceOff
    !exact -> labels.lateInexact
    else -> labels.lateSystem
  }
}

/**
 * I promemoria dell'app: [schedule], [cancel], [upcoming], e il registro di quelli arrivati ([deliveries]). Si prende con
 * [FluidReminders.get] (o `FluidNotify.get(context).reminders`).
 *
 * ```kotlin
 * FluidReminders.get(context).schedule(
 *   id = "verifica-storia",
 *   at = FluidReminderTime.local(LocalDateTime.of(2026, 12, 3, 20, 0)),
 *   content = FluidReminderContent("Domani: verifica di storia", "Capitoli 4 e 5"),
 * )
 * ```
 *
 * L'allarme: [AlarmManager.setExactAndAllowWhileIdle] se l'allarme esatto è concesso (sempre prima di Android 12; da
 * Android 13 l'app deve dichiarare `SCHEDULE_EXACT_ALARM` e l'utente deve concederlo, «Sveglie e promemoria»), altrimenti
 * [AlarmManager.setWindow] con dieci minuti di finestra; con `alarm = true` [AlarmManager.setAlarmClock], la sveglia che il
 * sistema non sposta mai e che compare nella barra di stato (developer.android.com, «Schedule alarms», fiducia alta).
 */
class FluidReminders internal constructor(private val notify: FluidNotify) {
  private val context: Context get() = notify.context
  private val store: FluidNotifyStore get() = notify.store

  /**
   * Mette in programma il promemoria [id] a [at] con [content]. Lo stesso `id` sostituisce quello di prima.
   *
   * @param repeat la ripetizione (la regola delle date di F8.8, sull'orologio), o null.
   * @param exact vero per chiedere l'allarme esatto, se concesso; falso per un promemoria che può arrivare entro dieci
   *   minuti (consuma meno batteria).
   * @param alarm vero per una sveglia (`setAlarmClock`): suona sempre all'ora giusta e mostra la sveglia nella barra.
   */
  fun schedule(
    id: String,
    at: FluidReminderTime,
    content: FluidReminderContent,
    repeat: FluidRecurrenceRule? = null,
    exact: Boolean = true,
    alarm: Boolean = false,
  ) {
    require(id.isNotBlank()) { "un promemoria ha un id" }
    require(content.title.isNotBlank()) { "un promemoria ha un titolo" }
    // Una ripetizione conta i giorni sull'orologio: un istante diventa la sua ora locale di adesso.
    val time = if (repeat != null && at.wall == null) FluidReminderTime.local(at.wallIn(notify.zone())) else at
    val reminder = FluidStoredReminder(id, time, content, repeat, exact, alarm)
    store.update { memory -> memory.copy(reminders = memory.reminders.filterNot { it.id == id } + reminder) }
    arm(id, afterRestore = false)
    notify.remindersChanged()
  }

  /** Toglie il promemoria [id] dal programma (e la sua notifica, se è a schermo). */
  fun cancel(id: String) {
    alarmManager().cancel(pendingIntent(id))
    store.update { memory -> memory.copy(reminders = memory.reminders.filterNot { it.id == id }) }
    notify.cancelReminderNotification(id)
    notify.remindersChanged()
  }

  /** I promemoria in programma, dal più vicino. */
  fun upcoming(): List<FluidReminderInfo> = store.read().reminders
    .filter { it.nextMillis != null }
    .sortedBy { it.nextMillis }
    .map { r ->
      FluidReminderInfo(
        id = r.id, title = r.content.title, text = r.content.text, atMillis = r.nextMillis!!, exact = r.armedExact,
        repeats = r.repeat?.let { rule ->
          runCatching { FluidRecurrenceText.describe(rule, r.time.wallIn(notify.zone()).toLocalDate(), Locale.getDefault()) }
            .getOrNull()
        },
      )
    }

  /** I promemoria arrivati, dal più recente. */
  fun deliveries(): List<FluidReminderDelivery> = store.read().deliveries

  /** Vero se l'allarme esatto è concesso (sempre prima di Android 12). */
  fun canScheduleExact(): Boolean =
    Build.VERSION.SDK_INT < 31 || runCatching { alarmManager().canScheduleExactAlarms() }.getOrDefault(false)

  /**
   * Rimette tutti gli allarmi: dopo un riavvio, un aggiornamento dell'app, un cambio d'ora o di fuso, un cambio del
   * permesso degli allarmi esatti (lo fa il ricevitore dell'engine), e all'avvio dell'app. Un promemoria scaduto nel
   * frattempo arriva subito, segnato in ritardo.
   */
  fun restore(afterBoot: Boolean = false) {
    store.read().reminders.map { it.id }.forEach { arm(it, afterRestore = afterBoot) }
    notify.remindersChanged()
  }

  /** Arma il promemoria [id], o lo consegna subito se è già scaduto. */
  internal fun arm(id: String, afterRestore: Boolean) {
    val reminder = store.read().reminders.firstOrNull { it.id == id } ?: return
    val now = notify.wallNow()
    val due = fluidReminderDue(reminder.time, reminder.repeat, notify.zone(), reminder.lastScheduledMillis, now)
    if (due == null) {
      // Finito: esce dal programma.
      alarmManager().cancel(pendingIntent(id))
      store.update { memory -> memory.copy(reminders = memory.reminders.filterNot { it.id == id }) }
      return
    }
    if (due <= now) {
      deliver(id, due, afterRestore)
      return
    }
    val exact = reminder.exact && canScheduleExact()
    val pi = pendingIntent(id)
    val am = alarmManager()
    when {
      reminder.alarm && exact -> am.setAlarmClock(AlarmManager.AlarmClockInfo(due, notify.openIntent(null, "reminder:$id")), pi)
      exact -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pi)
      else -> am.setWindow(AlarmManager.RTC_WAKEUP, due, FluidNotifyProfile.InexactWindowMs, pi)
    }
    store.update { memory ->
      memory.copy(reminders = memory.reminders.map { if (it.id == id) it.copy(nextMillis = due, armedExact = exact) else it })
    }
  }

  /** L'allarme di [id] è suonato (o era già scaduto): mostra il promemoria, lo registra, arma il prossimo. */
  internal fun deliver(id: String, scheduledHint: Long?, afterRestore: Boolean) {
    val reminder = store.read().reminders.firstOrNull { it.id == id } ?: return
    val now = notify.wallNow()
    val scheduled = scheduledHint ?: reminder.nextMillis
      ?: fluidReminderDue(reminder.time, reminder.repeat, notify.zone(), reminder.lastScheduledMillis, now) ?: return
    if (reminder.lastScheduledMillis != null && scheduled <= reminder.lastScheduledMillis) return // già consegnato
    val reason = fluidReminderLateReason(scheduled, now, afterRestore, reminder.armedExact, notify.config.labels)
    notify.showReminder(reminder, scheduled)
    val delivery = FluidReminderDelivery(id, reminder.content.title, scheduled, now, reason)
    store.update { memory ->
      memory.copy(
        reminders = memory.reminders.map { if (it.id == id) it.copy(lastScheduledMillis = scheduled, nextMillis = null) else it },
        deliveries = (listOf(delivery) + memory.deliveries).take(FluidNotifyProfile.DeliveryLogSize),
      )
    }
    notify.dispatch(FluidNotifyEvent.ReminderDelivered(id, scheduled, now))
    arm(id, afterRestore = false)
    notify.remindersChanged()
  }

  private fun alarmManager(): AlarmManager = context.getSystemService(AlarmManager::class.java)

  /** L'allarme del promemoria [id]: uno per promemoria, distinti dall'indirizzo (l'`Intent` col dato diverso). */
  internal fun pendingIntent(id: String): PendingIntent = PendingIntent.getBroadcast(
    context, 0,
    Intent(FluidNotifyReceiver.ActionReminder).setClass(context, FluidNotifyReceiver::class.java)
      .setData(Uri.parse("fluid-notify://reminder/" + Uri.encode(id)))
      .putExtra(FluidNotifyReceiver.ExtraId, id),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
  )

  companion object {
    /** I promemoria dell'app. */
    fun get(context: Context): FluidReminders = FluidNotify.get(context).reminders
  }
}

/**
 * Accende il ricevitore del ripristino (riavvio, aggiornamento, cambio d'ora) solo se serve: c'è un promemoria, o un
 * riepilogo acceso. Così un'app senza promemoria non si sveglia a ogni avvio del telefono (la raccomandazione di
 * developer.android.com, «Schedule alarms»: «enable the receiver only when the user sets an alarm»).
 */
internal fun fluidSetRestoreReceiver(context: Context, enabled: Boolean) {
  val component = ComponentName(context, FluidNotifyRestoreReceiver::class.java)
  val pm = context.packageManager
  val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
  runCatching {
    if (pm.getComponentEnabledSetting(component) != state) {
      pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
    }
  }
}
