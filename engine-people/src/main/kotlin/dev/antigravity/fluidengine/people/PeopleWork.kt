package dev.antigravity.fluidengine.people

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Lo Store che tiene le persone: di solito la sua `Application`. Il lavoro di WorkManager parte anche a Store chiuso, in
 * un processo nuovo, e chiede qui il client (`null` se la persona non c'è ancora o nessuno è entrato).
 */
interface PeopleWorkHost {
  fun peopleClient(): PeopleClient?
}

/**
 * La coda in uscita in sottofondo (critica M3): manda le buste già chiuse e firmate. Senza rete chiede di riprovare
 * (WorkManager riparte quando la rete torna, con pause crescenti); quello che resta per un tetto o una casella piena lo
 * riprende il lavoro successivo, che la coda stessa chiede all'ora giusta. Non apre mai la radice: col telefono in tasca
 * va come a telefono sbloccato (Q7 A).
 */
class PeopleOutboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val client = (applicationContext as? PeopleWorkHost)?.peopleClient() ?: return Result.success()
    val report = try {
      client.flush()
    } catch (_: PersonLockedException) {
      // Prima del primo sblocco la posta non si apre: si riprova dopo.
      return Result.retry()
    }
    return if (report.offline) Result.retry() else Result.success()
  }
}

/** L'[OutboxScheduler] con WorkManager: un lavoro unico ([UNIQUE]) con la rete come vincolo. */
object PeopleWork {
  const val UNIQUE = "pampa-persone-coda"

  val constraints: Constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

  /** Chiede di riprovare la coda fra [delayMillis]; l'ultima richiesta sostituisce quella in attesa. */
  fun request(context: Context, delayMillis: Long) {
    val work = OneTimeWorkRequestBuilder<PeopleOutboxWorker>()
      .setConstraints(constraints)
      .setInitialDelay(delayMillis.coerceAtLeast(0), TimeUnit.MILLISECONDS)
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
      .build()
    WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.REPLACE, work)
  }

  /** Lo scheduler da passare a [PeopleClient]. */
  fun scheduler(context: Context): OutboxScheduler {
    val app = context.applicationContext
    return OutboxScheduler { delay -> request(app, delay) }
  }
}
