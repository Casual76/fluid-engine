package dev.antigravity.fluidengine.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.antigravity.fluidengine.foundation.FluidConditionPhrases
import dev.antigravity.fluidengine.foundation.FluidFormat
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferDirection
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferOverview
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferState
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * L'app che tiene la coda nella sua `Application`: il lavoro di WorkManager parte anche a app chiusa, in un processo
 * nuovo, e chiede qui la coda. Serve a un'app con credenziali ([FluidTransferAuthorizer]) o una notifica sua; senza, il
 * lavoro usa [FluidTransfers.open] con le opzioni di serie.
 */
interface FluidTransferHost {
  val fluidTransfers: FluidTransfers
}

/**
 * Il lavoro in sottofondo: fa girare la coda ([FluidTransfers.runQueue]) **col servizio in primo piano di tipo
 * `dataSync`**, cosi' un trasferimento lungo continua con l'app chiusa e il sistema lo sa (la notifica e' quella di
 * [FluidTransfers.notifications]). Un errore che passa chiede di riprovare (pause crescenti di WorkManager); alla fine
 * mette in attesa il giro dopo, coi vincoli giusti per chi aspetta (solo Wi-Fi, in carica).
 *
 * Il servizio in primo piano puo' non partire (da Android 12, se l'app e' in sottofondo quando il lavoro comincia): il
 * lavoro continua lo stesso, con i limiti di un lavoro normale, e riprende da dov'era se il sistema lo ferma.
 */
class FluidTransferWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val transfers = FluidTransfers.from(applicationContext)
    var lastForeground = Long.MIN_VALUE / 2
    val result = transfers.runQueue { overview ->
      val now = SystemClock.elapsedRealtime()
      if (now - lastForeground >= ForegroundIntervalMillis && overview.running != null) {
        lastForeground = now
        // Fuori dal primo piano permesso (Android 12+ in sottofondo) il sistema dice di no: si va avanti senza.
        runCatching { setForeground(transfers.notifications.foregroundInfo(applicationContext, overview)) }
      }
    }
    // L'esito prima del nuovo tentativo: chi e' finito in questo giro lo dice adesso (anche se un altro riprovera'), e
    // la notifica viva comincia il giro dopo da zero.
    val after = transfers.overview.value
    if (after.running == null) runCatching { transfers.notifications.finished(applicationContext, after) }
    if (result.retryLater) return Result.retry()
    // Il giro dopo, coi vincoli di chi aspetta: si accoda a questo (che sta finendo), non lo sostituisce.
    val plan = transfers.plan(replace = false).let { plan ->
      // Un giro che non ha fatto partire niente, con chi aspetta ancora: i vincoli di WorkManager erano soddisfatti ma le
      // condizioni dell'engine no (una simulazione, «Usa pochi dati», un telefono caldo). Si riguarda piu' tardi, non
      // subito: niente giri a vuoto. Con l'app viva, un cambio delle condizioni lo fa ripartire subito.
      if (result.started == 0 && result.waiting > 0 && !plan.runNow) plan.later(transfers.config.recheckMillis) else plan
    }
    if (!plan.idle) FluidTransferWork.schedule(applicationContext, plan)
    return Result.success()
  }

  override suspend fun getForegroundInfo(): ForegroundInfo {
    val transfers = FluidTransfers.from(applicationContext)
    return transfers.notifications.foregroundInfo(applicationContext, transfers.overview.value)
  }

  private companion object {
    /** La notifica si aggiorna al piu' una volta al secondo: piu' spesso il sistema la taglia comunque. */
    const val ForegroundIntervalMillis = 1_000L
  }
}

/**
 * La coda con WorkManager: un lavoro unico ([UniqueName]) coi vincoli del [FluidTransferPlan] (una rete connessa, o una
 * che non costa; la carica; un'attesa), e le pause crescenti di WorkManager dopo un errore che passa.
 */
object FluidTransferWork {
  /** Il nome del lavoro unico. */
  const val UniqueName: String = "fluid-transfers"

  /** Fa girare la coda secondo [plan]. Un piano [FluidTransferPlan.idle] toglie il giro rimasto in attesa. */
  fun schedule(context: Context, plan: FluidTransferPlan) {
    if (plan.idle) {
      WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UniqueName)
      return
    }
    val constraints = Constraints.Builder()
      .setRequiredNetworkType(if (plan.unmetered) NetworkType.UNMETERED else NetworkType.CONNECTED)
      .setRequiresCharging(plan.charging)
      .build()
    val builder = OneTimeWorkRequestBuilder<FluidTransferWorker>()
      .setConstraints(constraints)
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
    if (plan.delayMillis > 0L) builder.setInitialDelay(plan.delayMillis, TimeUnit.MILLISECONDS)
    // Subito e senza vincoli oltre la rete: un lavoro «accelerato» parte anche con l'app appena chiusa.
    if (plan.runNow && !plan.charging && plan.delayMillis == 0L) {
      builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
    }
    WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
      UniqueName,
      if (plan.replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE,
      builder.build(),
    )
  }

  /** Lo [FluidTransferScheduler] che chiede i giri a WorkManager: e' quello di [FluidTransfers.open]. */
  fun scheduler(context: Context): FluidTransferScheduler {
    val app = context.applicationContext
    return FluidTransferScheduler { plan -> schedule(app, plan) }
  }
}

/**
 * **Il punto d'aggancio della notifica** dei trasferimenti: la notifica del servizio in primo piano mentre la coda gira.
 * Di serie [SimpleTransferNotifications], una notifica semplice con l'avanzamento. `engine-notify` (F8.19) mette la sua,
 * dallo stesso modello ([FluidTransferOverview]): la pillola dentro l'app, la notifica viva fuori.
 *
 * ```
 * FluidTransfers.open(context, notifications = myNotifications)   // oppure: transfers.notifications = …
 * ```
 */
interface FluidTransferNotifications {
  /** La notifica del servizio in primo piano per [overview], col tipo `dataSync`. */
  fun foregroundInfo(context: Context, overview: FluidTransferOverview): ForegroundInfo

  /**
   * Il giro della coda e' finito e non resta niente in corso ([overview] dice com'e' andata: completati, non riusciti, in
   * attesa). La notifica del servizio se ne va col servizio; qui chi ha una notifica viva lascia l'esito (`engine-notify`:
   * `FluidLiveUpdateSink.finish`). Di serie non fa niente.
   */
  fun finished(context: Context, overview: FluidTransferOverview) = Unit
}

/**
 * La notifica semplice dei trasferimenti, con `androidx.core`: un canale silenzioso «Trasferimenti», il titolo di quello in
 * corso, «12,4 MB di 48 MB · ancora 17 s» e la barra. Il tocco apre l'app. Niente stile personalizzato: e' la notifica di
 * un servizio, non una notifica viva (quella e' di `engine-notify`).
 *
 * @param phrases le parole, di serie italiane ([TransferNotificationPhrases.english] per l'inglese).
 */
open class SimpleTransferNotifications(
  private val phrases: TransferNotificationPhrases = TransferNotificationPhrases(),
) : FluidTransferNotifications {
  override fun foregroundInfo(context: Context, overview: FluidTransferOverview): ForegroundInfo {
    val notification = build(context, overview)
    return if (Build.VERSION.SDK_INT >= 29) {
      ForegroundInfo(NotificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
      ForegroundInfo(NotificationId, notification)
    }
  }

  /** La notifica: si puo' cambiare in una sottoclasse. */
  open fun build(context: Context, overview: FluidTransferOverview): Notification {
    ensureChannel(context)
    val item = overview.running ?: overview.active.firstOrNull()
    val upload = item?.direction == FluidTransferDirection.Upload
    val count = overview.active.size
    val title = when {
      item == null -> phrases.title
      count > 1 -> "${item.title} ${phrases.andOthers(count - 1)}"
      else -> item.title
    }
    val line = when {
      item == null -> null
      item.state == FluidTransferState.Waiting -> phrases.conditions.waitingFor(item.waitingFor)
      else -> phrases.line(item.bytesDone, item.bytesTotal, item.remainingMillis)
    }
    val progress = overview.progress
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
      PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
    return NotificationCompat.Builder(context, ChannelId)
      .setSmallIcon(if (upload) android.R.drawable.stat_sys_upload else android.R.drawable.stat_sys_download)
      .setContentTitle(title)
      .setContentText(line)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setProgress(100, ((progress ?: 0f) * 100f).toInt(), progress == null)
      .apply { launch?.let(::setContentIntent) }
      .build()
  }

  private fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT < 26) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(ChannelId) != null) return
    manager.createNotificationChannel(
      NotificationChannel(ChannelId, phrases.channel, NotificationManager.IMPORTANCE_LOW).apply {
        description = phrases.channelDescription
        setShowBadge(false)
      },
    )
  }

  companion object {
    /** Il canale delle notifiche dei trasferimenti. */
    const val ChannelId: String = "fluid-transfers"

    /** L'id della notifica del servizio in primo piano. */
    const val NotificationId: Int = 0x00F8_2000
  }
}

/** Le parole della notifica semplice, di serie italiane. */
data class TransferNotificationPhrases(
  val title: String = "Trasferimenti",
  val channel: String = "Trasferimenti",
  val channelDescription: String = "Gli scaricamenti e i caricamenti in corso",
  val of: String = "di",
  val remaining: (String) -> String = { "ancora $it" },
  val andOthers: (Int) -> String = { n -> if (n == 1) "e un altro" else "e altri $n" },
  val conditions: FluidConditionPhrases = FluidConditionPhrases(),
  /** F8.20b · I tasti e gli esiti della notifica viva ([LiveTransferNotifications]). */
  val pause: String = "Pausa",
  val cancel: String = "Annulla",
  val doneOne: (title: String, upload: Boolean) -> String = { title, upload -> if (upload) "$title caricato" else "$title scaricato" },
  val doneMany: (Int) -> String = { "$it trasferimenti completati" },
  val failedOne: (String) -> String = { "$it non riuscito" },
) {
  /** «12,4 MB di 48 MB · ancora 17 s». */
  fun line(done: Long, total: Long, remainingMillis: Long?): String {
    val amount = if (total > 0L) "${FluidFormat.bytes(done)} $of ${FluidFormat.bytes(total)}" else FluidFormat.bytes(done)
    val left = remainingMillis?.let { remaining(FluidFormat.duration(Duration.ofMillis(it), maxUnits = 1)) }
    return if (left != null) "$amount · $left" else amount
  }

  companion object {
    fun english(): TransferNotificationPhrases = TransferNotificationPhrases(
      title = "Transfers",
      channel = "Transfers",
      channelDescription = "Downloads and uploads in progress",
      of = "of",
      remaining = { "$it left" },
      andOthers = { n -> if (n == 1) "and 1 more" else "and $n more" },
      conditions = FluidConditionPhrases.english(),
      pause = "Pause",
      cancel = "Cancel",
      doneOne = { title, upload -> if (upload) "$title uploaded" else "$title downloaded" },
      doneMany = { "$it transfers done" },
      failedOne = { "$it failed" },
    )
  }
}
