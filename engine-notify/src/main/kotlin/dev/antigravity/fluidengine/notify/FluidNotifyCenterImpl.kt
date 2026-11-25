package dev.antigravity.fluidengine.notify

import android.app.ActivityManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dev.antigravity.fluidengine.foundation.notify.FluidNotifyCenter
import dev.antigravity.fluidengine.foundation.notify.FluidNotifyChannelInfo
import dev.antigravity.fluidengine.foundation.notify.FluidNotifyCheck
import dev.antigravity.fluidengine.foundation.notify.FluidNotifyDigestInfo
import dev.antigravity.fluidengine.foundation.notify.FluidNotifyFix
import dev.antigravity.fluidengine.foundation.notify.FluidNotifyHealth
import dev.antigravity.fluidengine.foundation.notify.FluidNotifyLiveState
import dev.antigravity.fluidengine.foundation.notify.FluidNotifySettings
import dev.antigravity.fluidengine.foundation.notify.FluidReminderDelivery
import dev.antigravity.fluidengine.foundation.notify.FluidReminderInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// F8.19 · Il centro per le pagine: legge da Android lo stato delle notifiche, dei canali, degli allarmi e della batteria,
// e manda l'utente nel punto giusto delle impostazioni. La pagina «Le notifiche non arrivano?» si accorge della
// sospensione di One UI dal secchio di standby dell'app (`UsageStatsManager.getAppStandbyBucket`: «raro» o «limitato»)
// e dalle restrizioni in sottofondo (`ActivityManager.isBackgroundRestricted`): sono i due segni pubblici che le «App in
// sospensione» e «in sospensione profonda» di One UI lasciano (a occhio dai documenti di Android e di Samsung, da
// guardare sul tablet; fiducia media).

/** Ciò che i controlli leggono da Android: separato, così la decisione è pura e si prova senza telefono. */
internal data class FluidNotifyHealthInputs(
  val sdk: Int,
  val notificationsAllowed: Boolean,
  val channels: List<Pair<FluidNotifyChannel, Boolean>>,
  val exactAllowed: Boolean,
  val hasReminders: Boolean,
  val standbyBucket: Int?,
  val backgroundRestricted: Boolean,
  val samsung: Boolean,
  val focusActive: Boolean,
  val live: FluidNotifyLiveState,
)

/** I secchi di standby di Android (`UsageStatsManager.STANDBY_BUCKET_*`): raro 40, limitato 45. */
internal const val StandbyBucketRare: Int = 40

/** Le righe di «Le notifiche non arrivano?», dalla più grave. Pura. */
internal fun fluidNotifyHealth(inputs: FluidNotifyHealthInputs, labels: FluidNotifyLabels): FluidNotifyHealth {
  val checks = ArrayList<FluidNotifyCheck>()
  checks += FluidNotifyCheck(
    id = "permission", title = labels.checkPermission,
    detail = if (inputs.notificationsAllowed) labels.checkPermissionOk else labels.checkPermissionOff,
    ok = inputs.notificationsAllowed,
    fix = FluidNotifyFix.AppNotifications.takeUnless { inputs.notificationsAllowed },
    fixLabel = labels.fixOpen.takeUnless { inputs.notificationsAllowed },
  )
  val suspended = (inputs.standbyBucket ?: 0) >= StandbyBucketRare || inputs.backgroundRestricted
  checks += FluidNotifyCheck(
    id = "suspended", title = labels.checkSuspended,
    detail = when {
      !suspended -> labels.checkSuspendedOk
      inputs.samsung -> labels.checkSuspendedSamsung
      else -> labels.checkSuspendedOther
    },
    ok = !suspended,
    fix = FluidNotifyFix.Battery.takeIf { suspended },
    fixLabel = labels.fixBattery.takeIf { suspended },
  )
  if (inputs.notificationsAllowed) {
    inputs.channels.filter { (channel, _) -> channel.id != FluidNotifyChannel.DigestId }.forEach { (channel, enabled) ->
      if (!enabled) {
        checks += FluidNotifyCheck(
          id = "channel:${channel.id}", title = channel.name, detail = labels.checkChannelOff, ok = false,
          fix = FluidNotifyFix.channel(channel.id), fixLabel = labels.fixOpen,
        )
      }
    }
  }
  if (inputs.sdk >= 31 && inputs.hasReminders) {
    checks += FluidNotifyCheck(
      id = "exact", title = labels.checkExact,
      detail = if (inputs.exactAllowed) labels.checkExactOk else labels.checkExactOff,
      ok = inputs.exactAllowed,
      fix = FluidNotifyFix.ExactAlarms.takeUnless { inputs.exactAllowed },
      fixLabel = labels.fixAllow.takeUnless { inputs.exactAllowed },
    )
  }
  checks += FluidNotifyCheck(
    id = "focus", title = labels.checkFocus,
    detail = if (inputs.focusActive) labels.checkFocusOn else labels.checkFocusOk,
    ok = !inputs.focusActive,
    fix = FluidNotifyFix.Focus.takeIf { inputs.focusActive },
    fixLabel = labels.fixOpen.takeIf { inputs.focusActive },
  )
  if (inputs.live.supported) {
    checks += FluidNotifyCheck(
      id = "live", title = labels.checkLive,
      detail = if (inputs.live.allowed) labels.checkLiveOk else labels.checkLiveOff,
      ok = inputs.live.allowed,
      fix = FluidNotifyFix.LiveUpdates.takeUnless { inputs.live.allowed },
      fixLabel = labels.fixOpen.takeUnless { inputs.live.allowed },
    )
  }
  // Prima ciò che non va, nell'ordine di sopra; poi ciò che va.
  return FluidNotifyHealth(checks = checks.filter { !it.ok } + checks.filter { it.ok }, suspended = suspended)
}

/** Il centro delle notifiche di [notify], per le pagine di engine-ui. */
internal class FluidNotifyCenterImpl(private val notify: FluidNotify) : FluidNotifyCenter {
  private val settingsState = MutableStateFlow(FluidNotifySettings())
  private val remindersState = MutableStateFlow<List<FluidReminderInfo>>(emptyList())
  private val deliveriesState = MutableStateFlow<List<FluidReminderDelivery>>(emptyList())
  private val healthState = MutableStateFlow(FluidNotifyHealth())

  override val settings: StateFlow<FluidNotifySettings> = settingsState.asStateFlow()
  override val reminders: StateFlow<List<FluidReminderInfo>> = remindersState.asStateFlow()
  override val deliveries: StateFlow<List<FluidReminderDelivery>> = deliveriesState.asStateFlow()
  override val health: StateFlow<FluidNotifyHealth> = healthState.asStateFlow()

  override fun refresh() = refreshAll()

  fun refreshAll() {
    refreshSettings()
    refreshReminders()
  }

  fun refreshSettings() {
    val memory = notify.store.read()
    val allowed = notify.notificationsAllowed()
    settingsState.value = FluidNotifySettings(
      notificationsAllowed = allowed,
      channels = notify.config.channels.map { channel ->
        FluidNotifyChannelInfo(
          id = channel.id, name = channel.name, description = channel.description,
          enabled = notify.channelAllowed(channel.id), urgent = !channel.digestible,
          inDigest = channel.digestible && channel.id in memory.digestChannels, group = channel.group?.name,
        )
      },
      digest = FluidNotifyDigestInfo(memory.digestEnabled, memory.digestTimes, memory.digestQueue.size),
      live = liveState(),
      focusActive = focusActive(),
    )
    healthState.value = fluidNotifyHealth(inputs(), notify.config.labels)
  }

  fun refreshReminders() {
    remindersState.value = notify.reminders.upcoming()
    deliveriesState.value = notify.reminders.deliveries()
    healthState.value = fluidNotifyHealth(inputs(), notify.config.labels)
  }

  override fun setChannelInDigest(channelId: String, inDigest: Boolean) {
    notify.store.update { memory ->
      memory.copy(digestChannels = if (inDigest) memory.digestChannels + channelId else memory.digestChannels - channelId)
    }
    notify.armDigest()
    refreshSettings()
  }

  override fun setDigest(enabled: Boolean, times: List<Int>) {
    val clean = times.map { it.coerceIn(0, 24 * 60 - 1) }.distinct().sorted().ifEmpty { listOf(8 * 60, 18 * 60) }
    val queue = notify.store.read().digestQueue
    notify.store.update { it.copy(digestEnabled = enabled, digestTimes = clean, digestQueue = if (enabled) it.digestQueue else emptyList()) }
    // Spegnere il riepilogo consegna subito ciò che aspettava: niente resta nascosto.
    if (!enabled && queue.isNotEmpty()) {
      notify.store.update { it.copy(digestQueue = queue) }
      notify.deliverDigest()
    }
    notify.armDigest()
    refreshSettings()
  }

  override fun cancelReminder(id: String) {
    notify.reminders.cancel(id)
  }

  override fun open(fix: FluidNotifyFix): Boolean {
    val context = notify.context
    val pkg = context.packageName
    val intents = buildList {
      when (fix) {
        FluidNotifyFix.ExactAlarms -> if (notify.sdk >= 31) {
          add(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", pkg, null)))
        }
        FluidNotifyFix.LiveUpdates -> add(Intent(ActionPromotionSettings).putExtra(Settings.EXTRA_APP_PACKAGE, pkg))
        FluidNotifyFix.Focus -> add(Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS))
        FluidNotifyFix.Battery -> Unit
        FluidNotifyFix.AppNotifications -> Unit
        else -> fix.channelId?.let { id ->
          add(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
              .putExtra(Settings.EXTRA_APP_PACKAGE, pkg).putExtra(Settings.EXTRA_CHANNEL_ID, id),
          )
        }
      }
      if (fix != FluidNotifyFix.Battery) add(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg))
      // La pagina dell'app ha sempre «Batteria» (One UI: «Senza restrizioni»): c'è su ogni telefono.
      add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)))
    }
    for (intent in intents) {
      val opened = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
      }.getOrDefault(false)
      if (opened) return true
    }
    return false
  }

  private fun liveState(): FluidNotifyLiveState = when {
    notify.sdk < 36 -> FluidNotifyLiveState.NotSupported
    notify.liveUpdates.canPromote() -> FluidNotifyLiveState.Allowed
    else -> FluidNotifyLiveState.Off
  }

  private fun focusActive(): Boolean = runCatching {
    val filter = notify.notificationManager().currentInterruptionFilter
    filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
  }.getOrDefault(false)

  private fun inputs(): FluidNotifyHealthInputs {
    val context = notify.context
    val bucket = if (Build.VERSION.SDK_INT >= 28) {
      runCatching { context.getSystemService(UsageStatsManager::class.java).appStandbyBucket }.getOrNull()
    } else {
      null
    }
    val restricted = Build.VERSION.SDK_INT >= 28 &&
      runCatching { context.getSystemService(ActivityManager::class.java).isBackgroundRestricted }.getOrDefault(false)
    return FluidNotifyHealthInputs(
      sdk = notify.sdk,
      notificationsAllowed = notify.notificationsAllowed(),
      channels = (notify.config.channels + listOfNotNull(notify.channel(FluidNotifyChannel.RemindersId)))
        .map { it to notify.channelAllowed(it.id) },
      exactAllowed = notify.reminders.canScheduleExact(),
      hasReminders = notify.store.read().reminders.isNotEmpty(),
      standbyBucket = bucket,
      backgroundRestricted = restricted,
      samsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true),
      focusActive = focusActive(),
      live = liveState(),
    )
  }

  private companion object {
    /** `Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS` (API 36): la pagina dei Live Update dell'app. */
    const val ActionPromotionSettings = "android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS"
  }
}
