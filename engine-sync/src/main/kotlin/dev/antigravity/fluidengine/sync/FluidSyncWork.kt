package dev.antigravity.fluidengine.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * L'app che sincronizza in sottofondo: di solito l'`Application`. Il lavoro di WorkManager parte anche a app chiusa, in un
 * processo nuovo, e chiede qui il motore.
 */
interface FluidSyncHost {
  val fluidSync: FluidSync
}

/**
 * Il lavoro in sottofondo: sincronizza tutti gli spazi ([FluidSync.syncAll]). Senza rete o con il relay occupato chiede di
 * riprovare (WorkManager riparte quando torna la rete, con pause crescenti); un rifiuto del relay lo ferma. Se mentre
 * lavorava sono arrivate modifiche nuove, fa un altro giro (al massimo [MaxRounds]).
 */
class FluidSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val sync = (applicationContext as? FluidSyncHost)?.fluidSync ?: return Result.failure()
    repeat(MaxRounds) {
      val report = sync.syncAll()
      if (report.shouldRetry) return Result.retry()
      if (report.failure != null) return Result.failure()
      if (sync.overview.value.pending == 0) return Result.success()
    }
    return Result.success()
  }

  private companion object {
    const val MaxRounds = 3
  }
}

/**
 * L'invio con WorkManager: dopo una modifica parte un lavoro unico ([UniqueName]) con la rete come vincolo e un piccolo
 * ritardo ([DebounceMillis]), cosi' una raffica di modifiche (chi scrive una nota) diventa un invio solo. Un lavoro
 * periodico ([schedulePeriodic]) fa da rete di sicurezza e porta le novita' degli altri dispositivi.
 */
object FluidSyncWork {
  /** Il nome del lavoro unico dopo una modifica. */
  const val UniqueName: String = "fluid-sync"

  /** Il nome del lavoro periodico. */
  const val PeriodicName: String = "fluid-sync-periodic"

  /**
   * L'attesa prima dell'invio: due secondi, a occhio (24/11/2026). Abbastanza per raccogliere le parole di una frase,
   * poco perche' l'altro dispositivo veda la modifica quasi subito.
   */
  const val DebounceMillis: Long = 2_000

  /** I vincoli: una rete connessa. */
  val constraints: Constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

  /** Chiede un invio. Se ce n'e' gia' uno in attesa, resta quello ([ExistingWorkPolicy.KEEP]). */
  fun request(context: Context, delayMillis: Long = DebounceMillis) {
    val work = OneTimeWorkRequestBuilder<FluidSyncWorker>()
      .setConstraints(constraints)
      .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
      .build()
    WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(UniqueName, ExistingWorkPolicy.KEEP, work)
  }

  /** Una sincronizzazione ogni [hours] ore quando c'e' rete (di serie sei). */
  fun schedulePeriodic(context: Context, hours: Long = 6) {
    val work = PeriodicWorkRequestBuilder<FluidSyncWorker>(hours, TimeUnit.HOURS)
      .setConstraints(constraints)
      .build()
    WorkManager.getInstance(context.applicationContext)
      .enqueueUniquePeriodicWork(PeriodicName, ExistingPeriodicWorkPolicy.KEEP, work)
  }

  /** Lo [SyncScheduler] che chiede un invio a WorkManager: e' quello di [FluidSync.open]. */
  fun scheduler(context: Context): SyncScheduler {
    val app = context.applicationContext
    return SyncScheduler { request(app) }
  }
}
