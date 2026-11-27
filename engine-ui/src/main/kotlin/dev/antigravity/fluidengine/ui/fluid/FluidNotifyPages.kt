package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.fluidengine.foundation.notify.FluidNotifyCenter
import dev.antigravity.fluidengine.foundation.notify.FluidNotifyCheck
import dev.antigravity.fluidengine.foundation.notify.FluidNotifyFix
import dev.antigravity.fluidengine.foundation.notify.FluidReminderDelivery
import dev.antigravity.fluidengine.foundation.notify.FluidReminderInfo
import dev.antigravity.fluidengine.ui.fluid.icons.Bedtime
import dev.antigravity.fluidengine.ui.fluid.icons.Bolt
import dev.antigravity.fluidengine.ui.fluid.icons.CheckCircle
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Notifications
import dev.antigravity.fluidengine.ui.fluid.icons.NotificationsOff
import dev.antigravity.fluidengine.ui.fluid.icons.Schedule
import dev.antigravity.fluidengine.ui.fluid.icons.Warning
import dev.antigravity.fluidengine.ui.theme.FluidInlineMessage
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTone
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale

// F8.19 · Le pagine delle notifiche (`notifiche-di-sistema`, `promemoria-affidabili`): le impostazioni delle notifiche
// dell'app, «Cosa succederà» e «Le notifiche non arrivano?». Leggono un [FluidNotifyCenter] (lo dà engine-notify,
// `FluidNotify.get(context).center`), quindi engine-ui non dipende da engine-notify. Il riferimento Apple sono le
// impostazioni delle notifiche di un'app in iOS (Consegna «Immediata» o «Riepilogo programmato», gli stili) e la pagina
// del Riepilogo programmato: righe di impostazioni, niente vetro (sono pagine, non elementi che galleggiano).
//
// Ciò che cambia da solo (lo stato riletto al ritorno dalle impostazioni, un promemoria che arriva mentre la pagina è
// aperta) cambia sfocato, come vuole la regola di F8 («ciò che muove l'app si sfoca»): il segno a destra delle righe
// passa da [FluidGlassContentTransition], il numero delle notifiche in attesa da [FluidNumericText]. Il dito non muove
// niente qui: le liste scorrono nitide.

/** Le parole delle pagine delle notifiche, di serie in italiano ([english] per l'inglese). */
@Immutable
data class FluidNotifyPageLabels(
  val settingsTitle: String = "Notifiche",
  val upcomingTitle: String = "Cosa succederà",
  val healthTitle: String = "Le notifiche non arrivano?",
  val off: String = "Le notifiche sono spente",
  val offDetail: String = "L'app non può avvisarti di niente finché non le accendi.",
  val turnOn: String = "Attiva le notifiche",
  val permissionReason: String = "Servono per avvisarti quando arriva qualcosa e per i promemoria che chiedi tu.",
  val channelsHeader: String = "Cosa arriva",
  val channelOff: String = "Spento in Android: tocca per accenderlo",
  /** La parola breve a destra di un canale spento. */
  val channelOffMark: String = "Spento",
  val urgent: String = "Sempre subito",
  val immediate: String = "Subito",
  val inDigest: String = "Nel riepilogo",
  val digestHeader: String = "Riepilogo programmato",
  val digestToggle: String = "Riepilogo programmato",
  val digestDetail: String = "Le notifiche non urgenti dei canali che scegli arrivano raccolte agli orari qui sotto. Quelle urgenti arrivano sempre subito.",
  val digestOffDetail: String = "Spento: tutto arriva subito.",
  val digestFirst: String = "Primo riepilogo",
  val digestSecond: String = "Secondo riepilogo",
  val digestWaiting: String = "In attesa del prossimo riepilogo",
  val digestChannels: String = "Nel riepilogo",
  val liveHeader: String = "Attività in corso",
  val liveRow: String = "Live Update",
  val liveOn: String = "Accesi: download, timer e viaggi stanno nella barra di stato",
  val liveOff: String = "Spenti: le attività in corso restano notifiche normali",
  val focusOn: String = "Non disturbare è attivo: le notifiche arrivano in silenzio, e l'app non lo scavalca mai.",
  val moreHeader: String = "Altro",
  val upcomingRow: String = "Cosa succederà",
  val upcomingRowDetail: String = "I promemoria in programma e quelli arrivati",
  val healthRow: String = "Le notifiche non arrivano?",
  val healthRowDetail: String = "Controlla cosa può fermarle",
  val scheduledHeader: String = "In programma",
  val nothingScheduled: String = "Niente in programma.",
  val deliveredHeader: String = "Arrivati",
  val nothingDelivered: String = "Ancora nessun promemoria arrivato.",
  val exact: String = "al minuto",
  val approximate: String = "entro 10 minuti",
  val remove: String = "Togli",
  val today: String = "oggi",
  val tomorrow: String = "domani",
  val yesterday: String = "ieri",
  val at: (day: String, time: String) -> String = { day, time -> "$day alle $time" },
  val onTime: String = "In orario",
  val late: (minutes: Long) -> String = { m -> if (m == 1L) "1 minuto di ritardo" else "$m minuti di ritardo" },
  val healthIntro: String = "Controllo le cose che possono fermare le notifiche e i promemoria. Tocca una riga per andare nel punto giusto delle impostazioni.",
  val allGood: String = "Tutto a posto",
  val toFix: String = "Da sistemare",
  val ok: String = "A posto",
  /** Un tocco che doveva aprire le impostazioni di Android, ma nessuna pagina si è aperta (R2; in coda, con un valore di serie). */
  val openFailed: String = "Non riesco ad aprire le impostazioni di Android da qui. Aprile tu: Impostazioni › App › questa app › Notifiche.",
  /** Il segno di un promemoria che all'ora giusta non si è potuto mostrare (R2; in coda, con un valore di serie). */
  val notShown: String = "Non mostrato",
) {
  companion object {
    /** Le parole inglesi. */
    fun english(): FluidNotifyPageLabels = FluidNotifyPageLabels(
      settingsTitle = "Notifications", upcomingTitle = "What's coming up", healthTitle = "Notifications not arriving?",
      off = "Notifications are off", offDetail = "The app can't tell you anything until you turn them on.",
      turnOn = "Turn on notifications",
      permissionReason = "They tell you when something arrives and deliver the reminders you ask for.",
      channelsHeader = "What arrives", channelOff = "Off in Android: tap to turn it on", channelOffMark = "Off",
      urgent = "Always immediate",
      immediate = "Immediately", inDigest = "In the summary", digestHeader = "Scheduled summary",
      digestToggle = "Scheduled summary",
      digestDetail = "Non-urgent notifications from the channels you pick arrive together at the times below. Urgent ones always arrive immediately.",
      digestOffDetail = "Off: everything arrives immediately.", digestFirst = "First summary", digestSecond = "Second summary",
      digestWaiting = "Waiting for the next summary", digestChannels = "In the summary", liveHeader = "Ongoing activities",
      liveRow = "Live Updates", liveOn = "On: downloads, timers and trips stay in the status bar",
      liveOff = "Off: ongoing activities stay ordinary notifications",
      focusOn = "Do Not Disturb is on: notifications arrive silently, and the app never overrides it.",
      moreHeader = "More", upcomingRow = "What's coming up", upcomingRowDetail = "Scheduled and delivered reminders",
      healthRow = "Notifications not arriving?", healthRowDetail = "Check what can stop them",
      scheduledHeader = "Scheduled", nothingScheduled = "Nothing scheduled.", deliveredHeader = "Delivered",
      nothingDelivered = "No reminder delivered yet.", exact = "on the minute", approximate = "within 10 minutes",
      remove = "Remove", today = "today", tomorrow = "tomorrow", yesterday = "yesterday",
      at = { day, time -> "$day at $time" }, onTime = "On time",
      late = { m -> if (m == 1L) "1 minute late" else "$m minutes late" },
      healthIntro = "I check what can stop notifications and reminders. Tap a row to go to the right place in settings.",
      allGood = "All good", toFix = "To fix", ok = "OK",
      openFailed = "I can't open Android settings from here. Open them yourself: Settings › Apps › this app › Notifications.",
      notShown = "Not shown",
    )
  }
}

/** I tag dei test delle pagine delle notifiche. */
object FluidNotifyPageTags {
  const val Settings: String = "fluid-notify:impostazioni"
  const val Upcoming: String = "fluid-notify:programma"
  const val Health: String = "fluid-notify:controlli"
  const val TurnOn: String = "fluid-notify:attiva"
  const val DigestToggle: String = "fluid-notify:riepilogo"

  fun channel(id: String): String = "fluid-notify:canale:$id"
  fun digestChannel(id: String): String = "fluid-notify:riepilogo:$id"
  fun reminder(id: String): String = "fluid-notify:promemoria:$id"
  fun delivery(index: Int): String = "fluid-notify:arrivato:$index"
  fun check(id: String): String = "fluid-notify:controllo:$id"
}

/** Rilegge il centro alla ripresa: al ritorno dalle impostazioni di Android la pagina dice lo stato nuovo. */
@Composable
private fun RefreshOnResume(center: FluidNotifyCenter) {
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  DisposableEffect(lifecycle, center) {
    center.refresh()
    val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) center.refresh() }
    lifecycle.addObserver(observer)
    onDispose { lifecycle.removeObserver(observer) }
  }
}

/**
 * Chi apre le impostazioni di Android dalle pagine: ricorda se l'ultimo tocco non ha aperto niente
 * (`FluidNotifyCenter.open` falso), perché la pagina lo dica invece di restare muta (R2).
 */
@Stable
private class SettingsOpener(private val center: FluidNotifyCenter) {
  var failed by mutableStateOf(false)
    private set

  fun open(fix: FluidNotifyFix) {
    failed = !center.open(fix)
  }
}

@Composable
private fun rememberSettingsOpener(center: FluidNotifyCenter): SettingsOpener = remember(center) { SettingsOpener(center) }

/** Il messaggio del tocco che non ha aperto le impostazioni, in cima alla pagina. */
private fun LazyListScope.openFailedItem(opener: SettingsOpener, labels: FluidNotifyPageLabels) {
  if (opener.failed) {
    item(key = "open-failed") { FluidInlineMessage(message = labels.openFailed, tone = FluidTone.Warning) }
  }
}

/** Il segno a destra di una riga: cambia sfocato quando lo cambia l'app (R7). */
@Composable
private fun StatusMark(ok: Boolean, text: String) {
  FluidGlassContentTransition(targetState = ok to text, key = { it }) { (good, label) ->
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      FluidIcon(
        if (good) FluidIcons.CheckCircle else FluidIcons.Warning,
        contentDescription = null,
        tint = if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        size = 18.dp,
      )
      // Una parola o due: una scritta lunga a destra stringe il titolo della riga (visto sul tablet il 25/11/2026). La
      // spiegazione sta nel sottotitolo.
      if (label.isNotEmpty()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
      }
    }
  }
}

/**
 * La pagina delle impostazioni delle notifiche dell'app (F8.19): l'interruttore generale (il permesso, chiesto dal tasto
 * con [rememberFluidPermission]), i canali con parole comuni, il riepilogo programmato (**spento di serie**), i Live
 * Update di Android 16 e i rimandi a «Cosa succederà» e «Le notifiche non arrivano?».
 *
 * ```kotlin
 * FluidNotificationSettingsPage(center = FluidNotify.get(context).center, onBack = nav::pop,
 *   onOpenUpcoming = { nav.push(Upcoming) }, onOpenHealth = { nav.push(Health) })
 * ```
 *
 * I canali non si accendono e spengono qui: lo decide Android, e la riga porta nella pagina del canale. Qui si sceglie
 * solo se un canale arriva subito o nel riepilogo, come la «Consegna» di iOS.
 */
@Composable
fun FluidNotificationSettingsPage(
  center: FluidNotifyCenter,
  onBack: (() -> Unit)?,
  modifier: Modifier = Modifier,
  onOpenUpcoming: (() -> Unit)? = null,
  onOpenHealth: (() -> Unit)? = null,
  labels: FluidNotifyPageLabels = FluidNotifyPageLabels(),
) {
  RefreshOnResume(center)
  val settings by center.settings.collectAsStateWithLifecycle()
  val permission = rememberFluidPermission(FluidPermission.Notifications, reason = labels.permissionReason) {
    center.refresh()
  }
  // Le ore intere dalle 5 alle 23, più gli orari salvati come sono (8:30, le 3 di notte): la scelta non li arrotonda
  // e non li perde (R2; prima un 8:30 dato con `center.setDigest` diventava 8:00 al primo tocco).
  val times = remember(settings.digest.times) {
    ((5..23).map { it * 60 } + settings.digest.times).distinct().sorted()
      .map { minutes -> FluidPickerOption(minutes, "%d:%02d".format(minutes / 60, minutes % 60)) }
  }
  val opener = rememberSettingsOpener(center)
  FluidScreen(title = labels.settingsTitle, onBack = onBack, modifier = modifier.testTag(FluidNotifyPageTags.Settings)) {
    openFailedItem(opener, labels)
    if (!settings.notificationsAllowed) {
      item(key = "off") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          FluidInlineMessage(message = labels.offDetail, title = labels.off, tone = FluidTone.Warning)
          FluidButton(
            text = labels.turnOn,
            onClick = { permission.request { center.refresh() } },
            modifier = Modifier.fluidPermissionSource(permission).testTag(FluidNotifyPageTags.TurnOn),
            fillWidth = true,
          )
        }
      }
    }
    if (settings.channels.isNotEmpty()) {
      item(key = "channels") {
        Column {
          FluidSectionHeader(labels.channelsHeader)
          FluidListGroup {
            settings.channels.forEachIndexed { index, channel ->
              if (index > 0) FluidListDivider()
              val delivery = when {
                !channel.enabled -> labels.channelOffMark
                channel.urgent -> labels.urgent
                settings.digest.enabled && channel.inDigest -> labels.inDigest
                else -> labels.immediate
              }
              FluidListRow(
                title = channel.name,
                subtitle = if (channel.enabled) channel.description else channel.description + "\n" + labels.channelOff,
                modifier = Modifier.testTag(FluidNotifyPageTags.channel(channel.id)),
                leading = {
                  FluidIcon(if (channel.enabled) FluidIcons.Notifications else FluidIcons.NotificationsOff, contentDescription = null)
                },
                tone = if (channel.enabled) FluidTone.Primary else FluidTone.Neutral,
                badge = { StatusMark(channel.enabled, delivery) },
                onClick = { opener.open(FluidNotifyFix.channel(channel.id)) },
              )
            }
          }
        }
      }
    }
    item(key = "digest") {
      Column {
        FluidSectionHeader(labels.digestHeader)
        FluidListGroup {
          FluidToggleRow(
            title = labels.digestToggle,
            subtitle = if (settings.digest.enabled) labels.digestDetail else labels.digestOffDetail,
            checked = settings.digest.enabled,
            onCheckedChange = { on -> center.setDigest(on, settings.digest.times) },
            modifier = Modifier.testTag(FluidNotifyPageTags.DigestToggle),
            leading = { FluidIcon(FluidIcons.Schedule, contentDescription = null) },
            tone = FluidTone.Info,
          )
          if (settings.digest.enabled) {
            val saved = settings.digest.times
            val first = saved.getOrNull(0) ?: 8 * 60
            val second = saved.getOrNull(1) ?: 18 * 60
            // Si cambia un orario solo: gli altri (anche un terzo, dato con `center.setDigest`) restano com'erano.
            val chosen = { index: Int, minutes: Int ->
              center.setDigest(true, (if (saved.size >= 2) saved else listOf(first, second)).toMutableList().also { it[index] = minutes })
            }
            FluidListDivider()
            FluidPickerRow(
              title = labels.digestFirst, selectedKey = first, options = times,
              onSelected = { chosen(0, it) },
            )
            FluidListDivider()
            FluidPickerRow(
              title = labels.digestSecond, selectedKey = second, options = times,
              onSelected = { chosen(1, it) },
            )
            if (settings.digest.waiting > 0) {
              FluidListDivider()
              FluidListRow(
                title = labels.digestWaiting,
                subtitle = "",
                badge = { FluidNumericText(value = settings.digest.waiting, style = MaterialTheme.typography.bodyLarge) },
                disclosure = false,
              )
            }
          }
        }
        if (settings.digest.enabled) {
          val digestible = settings.channels.filter { !it.urgent }
          if (digestible.isNotEmpty()) {
            FluidSectionHeader(labels.digestChannels)
            FluidListGroup {
              digestible.forEachIndexed { index, channel ->
                if (index > 0) FluidListDivider()
                FluidToggleRow(
                  title = channel.name,
                  subtitle = if (channel.inDigest) labels.inDigest else labels.immediate,
                  checked = channel.inDigest,
                  onCheckedChange = { center.setChannelInDigest(channel.id, it) },
                  modifier = Modifier.testTag(FluidNotifyPageTags.digestChannel(channel.id)),
                )
              }
            }
          }
        }
      }
    }
    if (settings.live.supported) {
      item(key = "live") {
        Column {
          FluidSectionHeader(labels.liveHeader)
          FluidListGroup {
            FluidListRow(
              title = labels.liveRow,
              subtitle = if (settings.live.allowed) labels.liveOn else labels.liveOff,
              leading = { FluidIcon(FluidIcons.Bolt, contentDescription = null) },
              tone = FluidTone.Primary,
              badge = { StatusMark(settings.live.allowed, if (settings.live.allowed) labels.ok else labels.toFix) },
              onClick = { opener.open(FluidNotifyFix.LiveUpdates) },
            )
          }
        }
      }
    }
    if (settings.focusActive) {
      item(key = "focus") {
        FluidInlineMessage(
          message = labels.focusOn, tone = FluidTone.Info,
          leading = { FluidIcon(FluidIcons.Bedtime, contentDescription = null) },
          onClick = { opener.open(FluidNotifyFix.Focus) },
        )
      }
    }
    if (onOpenUpcoming != null || onOpenHealth != null) {
      item(key = "more") {
        Column {
          FluidSectionHeader(labels.moreHeader)
          FluidListGroup {
            onOpenUpcoming?.let {
              FluidListRow(title = labels.upcomingRow, subtitle = labels.upcomingRowDetail, onClick = it)
            }
            if (onOpenUpcoming != null && onOpenHealth != null) FluidListDivider()
            onOpenHealth?.let {
              FluidListRow(title = labels.healthRow, subtitle = labels.healthRowDetail, onClick = it)
            }
          }
        }
      }
    }
  }
}

/** «oggi alle 20:00», «domani alle 8:00», «3 dic alle 9:00». Con il formatore di Java (quello di Android sul telefono). */
internal fun fluidNotifyWhen(millis: Long, nowMillis: Long, zone: ZoneId, locale: Locale, labels: FluidNotifyPageLabels): String {
  val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
  val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
  val time = DateFormat.getTimeInstance(DateFormat.SHORT, locale).apply { timeZone = java.util.TimeZone.getTimeZone(zone) }
    .format(Date(millis))
  val dayText = when (day) {
    today -> labels.today
    today.plusDays(1) -> labels.tomorrow
    today.minusDays(1) -> labels.yesterday
    else -> DateFormat.getDateInstance(DateFormat.MEDIUM, locale).apply { timeZone = java.util.TimeZone.getTimeZone(zone) }
      .format(Date(millis))
  }
  return labels.at(dayText, time)
}

/** Il ritardo di un promemoria arrivato, in parole. */
internal fun fluidNotifyDelay(delivery: FluidReminderDelivery, labels: FluidNotifyPageLabels): String {
  val minutes = delivery.delayMillis / 60_000L
  return if (minutes < 1) labels.onTime else labels.late(minutes)
}

/**
 * «Cosa succederà» (F8.19, `promemoria-affidabili`): i promemoria in programma, dal più vicino, con l'ora e se arriverà
 * al minuto; sotto, quelli arrivati, con il ritardo vero e il perché. Un promemoria si toglie col menu della riga (e con
 * l'azione di TalkBack «Togli»).
 */
@Composable
fun FluidUpcomingRemindersPage(
  center: FluidNotifyCenter,
  onBack: (() -> Unit)?,
  modifier: Modifier = Modifier,
  labels: FluidNotifyPageLabels = FluidNotifyPageLabels(),
  zone: ZoneId = ZoneId.systemDefault(),
  locale: Locale = Locale.getDefault(),
  now: () -> Long = System::currentTimeMillis,
) {
  RefreshOnResume(center)
  val reminders by center.reminders.collectAsStateWithLifecycle()
  val deliveries by center.deliveries.collectAsStateWithLifecycle()
  FluidScreen(title = labels.upcomingTitle, onBack = onBack, modifier = modifier.testTag(FluidNotifyPageTags.Upcoming)) {
    item(key = "scheduled") {
      Column {
        FluidSectionHeader(labels.scheduledHeader)
        if (reminders.isEmpty()) {
          FluidSectionFootnote(labels.nothingScheduled)
        } else {
          FluidListGroup {
            reminders.forEachIndexed { index, reminder ->
              if (index > 0) FluidListDivider()
              ReminderRow(reminder, center, labels, zone, locale, now())
            }
          }
        }
      }
    }
    item(key = "delivered") {
      Column {
        FluidSectionHeader(labels.deliveredHeader)
        if (deliveries.isEmpty()) {
          FluidSectionFootnote(labels.nothingDelivered)
        } else {
          FluidListGroup {
            deliveries.forEachIndexed { index, delivery ->
              if (index > 0) FluidListDivider()
              val late = delivery.delayMillis >= 60_000L
              // Un promemoria che all'ora giusta non si è potuto mostrare è perso: si dice così, non «in orario» (R2).
              val good = delivery.shown && !late
              FluidListRow(
                title = delivery.title,
                subtitle = listOfNotNull(
                  fluidNotifyWhen(delivery.deliveredMillis, now(), zone, locale, labels),
                  delivery.reason,
                ).joinToString(" · "),
                modifier = Modifier.testTag(FluidNotifyPageTags.delivery(index)),
                badge = { StatusMark(good, if (delivery.shown) fluidNotifyDelay(delivery, labels) else labels.notShown) },
                disclosure = false,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ReminderRow(
  reminder: FluidReminderInfo,
  center: FluidNotifyCenter,
  labels: FluidNotifyPageLabels,
  zone: ZoneId,
  locale: Locale,
  nowMillis: Long,
) {
  val whenText = fluidNotifyWhen(reminder.atMillis, nowMillis, zone, locale, labels)
  val precision = if (reminder.exact) labels.exact else labels.approximate
  FluidListRow(
    title = reminder.title,
    subtitle = listOfNotNull(whenText, reminder.repeats, reminder.text).joinToString(" · "),
    modifier = Modifier.testTag(FluidNotifyPageTags.reminder(reminder.id)),
    leading = { FluidIcon(FluidIcons.Schedule, contentDescription = null) },
    tone = FluidTone.Info,
    badge = { StatusMark(reminder.exact, precision) },
    disclosure = false,
    contextActions = {
      listOf(FluidContextAction(labels.remove, destructive = true, onClick = { center.cancelReminder(reminder.id) }))
    },
    customActions = listOf(
      CustomAccessibilityAction(labels.remove) {
        center.cancelReminder(reminder.id)
        true
      },
    ),
  )
}

/**
 * «Le notifiche non arrivano?» (F8.19): i controlli che possono fermare notifiche e promemoria, prima quelli da
 * sistemare: le notifiche spente, l'app messa a dormire da One UI («App in sospensione») o da Android, un canale spento,
 * il permesso degli allarmi esatti, Non disturbare, i Live Update. Ogni riga porta nel punto giusto delle impostazioni;
 * quella delle notifiche spente, da Android 13, chiede il permesso dal tasto.
 */
@Composable
fun FluidNotificationHealthPage(
  center: FluidNotifyCenter,
  onBack: (() -> Unit)?,
  modifier: Modifier = Modifier,
  labels: FluidNotifyPageLabels = FluidNotifyPageLabels(),
) {
  RefreshOnResume(center)
  val health by center.health.collectAsStateWithLifecycle()
  val permission = rememberFluidPermission(FluidPermission.Notifications, reason = labels.permissionReason) {
    center.refresh()
  }
  val opener = rememberSettingsOpener(center)
  FluidScreen(title = labels.healthTitle, onBack = onBack, modifier = modifier.testTag(FluidNotifyPageTags.Health)) {
    openFailedItem(opener, labels)
    item(key = "intro") { FluidSectionFootnote(labels.healthIntro, modifier = Modifier.padding(bottom = 4.dp)) }
    item(key = "checks") {
      Column {
        FluidSectionHeader(if (health.allGood) labels.allGood else labels.toFix)
        FluidListGroup {
          health.checks.forEachIndexed { index, check ->
            if (index > 0) FluidListDivider()
            HealthRow(check, labels, onFix = {
              when {
                check.fix == FluidNotifyFix.AppNotifications -> permission.request { center.refresh() }
                check.fix != null -> opener.open(check.fix!!)
              }
            }, modifier = if (check.fix == FluidNotifyFix.AppNotifications) Modifier.fluidPermissionSource(permission) else Modifier)
          }
        }
      }
    }
  }
}

@Composable
private fun HealthRow(check: FluidNotifyCheck, labels: FluidNotifyPageLabels, onFix: () -> Unit, modifier: Modifier) {
  // Da sistemare: solo il segno e la freccia; il perché e cosa fare stanno nel sottotitolo.
  val mark = if (check.ok) labels.ok else ""
  FluidListRow(
    title = check.title,
    subtitle = if (check.ok || check.fixLabel == null) check.detail else check.detail + "\n" + check.fixLabel,
    modifier = modifier.testTag(FluidNotifyPageTags.check(check.id)),
    tone = if (check.ok) FluidTone.Success else FluidTone.Warning,
    leading = { FluidIcon(if (check.ok) FluidIcons.CheckCircle else FluidIcons.Warning, contentDescription = null) },
    badge = { StatusMark(check.ok, mark) },
    disclosure = check.fix != null,
    onClick = if (check.fix != null) onFix else null,
  )
}
