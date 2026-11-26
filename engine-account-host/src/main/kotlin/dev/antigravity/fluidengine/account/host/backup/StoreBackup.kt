package dev.antigravity.fluidengine.account.host.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.antigravity.fluidengine.account.BackupInfo
import dev.antigravity.fluidengine.account.backup.BackupFile
import dev.antigravity.fluidengine.account.backup.BackupPackage
import dev.antigravity.fluidengine.account.backup.PampaBackupFormat
import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.host.AccountSession
import dev.antigravity.fluidengine.account.host.AppPolicy
import dev.antigravity.fluidengine.account.host.BackupDirectory
import dev.antigravity.fluidengine.account.host.TokenOutcome
import dev.antigravity.fluidengine.account.host.custody.AppKeyPurpose
import dev.antigravity.fluidengine.account.host.custody.CustodyKeyLostException
import dev.antigravity.fluidengine.account.host.custody.CustodyKeyring
import dev.antigravity.fluidengine.account.host.custody.CustodyLockedException
import dev.antigravity.fluidengine.account.host.server.AccountHeaders
import dev.antigravity.fluidengine.account.host.server.AccountServerClient
import dev.antigravity.fluidengine.account.host.server.AccountServerException
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Il backup dello Store (`b\chiavi.md` § 7; architettura § 5.8): l'elenco delle app Pampa installate e le preferenze
 * dello Store, così un telefono nuovo propone «Avevi 7 app Pampa: installarle?». Cifrato con `K_bak(com.pampa.store)`.
 *
 * Gira in sottofondo, al massimo una volta al giorno, **solo se qualcosa è cambiato** (l'impronta del contenuto), con
 * la rete. Usa solo le chiavi di lavoro: con la raccomandazione A di Q7 funziona col telefono in tasca; con la B o la C
 * trova le chiavi chiuse e riprova dopo lo sblocco.
 */
class StoreBackupJob(
  private val session: AccountSession,
  private val client: AccountServerClient,
  private val keyring: CustodyKeyring,
  private val accountId: () -> String?,
  private val content: suspend () -> BackupPackage,
  private val stateDir: File,
  private val storePackage: String = AppPolicy.STORE,
) {
  sealed interface Outcome {
    data class Uploaded(val n: Long, val bytes: Int) : Outcome
    data object Unchanged : Outcome
    data object NoE2e : Outcome
    data object Locked : Outcome
    data object SignedOut : Outcome
    data object Offline : Outcome
    data class Failed(val message: String) : Outcome
  }

  private val fingerprintFile get() = File(stateDir, "backup-impronta")

  suspend fun run(): Outcome {
    val account = accountId() ?: return Outcome.SignedOut
    if (!keyring.hasCustody) return Outcome.NoE2e
    val keys = try {
      keyring.workKeys(storePackage, AppKeyPurpose.BACKUP) ?: return Outcome.Locked
    } catch (_: CustodyLockedException) {
      return Outcome.Locked
    } catch (_: CustodyKeyLostException) {
      return Outcome.Locked
    }
    val pkg = content()
    // R2 · L'impronta vale per **un** conto e **un'**epoca: sullo stesso telefono un altro account (lo stesso contenuto,
    // le stesse app) non ha ancora nessun backup, e dopo un «perso o rubato» il backup si richiude con la chiave nuova.
    val fingerprint = "$account|${keys.current}|" + PampaBase64.encode(PampaBackupFormat.fingerprint(pkg))
    if (fingerprintFile.exists() && runCatching { fingerprintFile.readText() }.getOrNull() == fingerprint) return Outcome.Unchanged
    val token = when (val t = session.appToken(storePackage, "sync", setOf("sync.backup"))) {
      is TokenOutcome.Ok -> t.token
      TokenOutcome.Offline -> return Outcome.Offline
      TokenOutcome.SignedOut -> return Outcome.SignedOut
      else -> return Outcome.Failed(t.toString())
    }
    return try {
      val existing = list(client, token).filter { it.first == storePackage }.maxOfOrNull { it.second.n } ?: 0L
      val n = existing + 1
      val blob = PampaBackupFormat.seal(pkg, keys.keys.getValue(keys.current), keys.current, account, n)
      client.upload("PUT", "/v1/backups/$n", blob, token, mapOf(AccountHeaders.BACKUP_EPOCH to keys.current.toString()))
      stateDir.mkdirs()
      fingerprintFile.writeText(fingerprint)
      Outcome.Uploaded(n, blob.size)
    } catch (_: IOException) {
      Outcome.Offline
    } catch (e: AccountServerException) {
      if (e.status >= 500) Outcome.Offline else Outcome.Failed(e.code)
    }
  }

  companion object {
    /** `GET /v1/backups`: lo Store vede l'elenco di tutte le app dell'account, senza il contenuto. */
    suspend fun list(client: AccountServerClient, token: String): List<Pair<String, BackupInfo>> {
      val r = client.call("GET", "/v1/backups", token = token)
      // R2 · Una voce di forma strana si salta, non fa cadere il lavoro.
      return (r["backups"] as? JsonArray)?.mapNotNull {
        val o = it as? JsonObject ?: return@mapNotNull null
        val pkg = (o["package"] as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: return@mapNotNull null
        pkg to BackupInfo(
          n = (o["n"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null,
          createdAt = (o["createdAt"] as? JsonPrimitive)?.longOrNull ?: 0,
          size = (o["size"] as? JsonPrimitive)?.longOrNull ?: 0,
          epoch = (o["epoch"] as? JsonPrimitive)?.intOrNull ?: 0,
        )
      }.orEmpty()
    }

    /**
     * Il contenuto del backup dello Store: le app Pampa installate (quelle della tabella, dal `PackageManager`) e le
     * preferenze dello Store. Mai token, mai credenziali.
     */
    fun storeContent(context: Context, policy: AppPolicy, storeVersion: String, preferences: Map<String, String>, now: Long = System.currentTimeMillis()): BackupPackage {
      val pm = context.packageManager
      val installed = policy.apps.values.filter { e ->
        e.packageName != context.packageName && runCatching { pm.getPackageInfo(e.packageName, 0) }.isSuccess
      }.sortedBy { it.packageName }
      val apps = buildJsonArray { for (e in installed) addJsonObject { put("package", e.packageName); put("name", e.name) } }
      val prefs = kotlinx.serialization.json.buildJsonObject { for ((k, v) in preferences.toSortedMap()) put(k, v) }
      return BackupPackage(context.packageName, storeVersion, schema = 1, createdAt = now, files = listOf(
        BackupFile("apps.json", apps.toString().toByteArray()),
        BackupFile("preferenze.json", prefs.toString().toByteArray()),
      ))
    }
  }
}

/** L'elenco dei backup di un'app, per `backup.available` (`b\api.md` § 5.3): lo chiede lo Store col suo token. */
class ServerBackupDirectory(private val session: AccountSession, private val client: AccountServerClient) : BackupDirectory {
  override suspend fun available(packageName: String): List<BackupInfo>? {
    val token = (session.appToken(AppPolicy.STORE, "sync", setOf("sync.backup")) as? TokenOutcome.Ok)?.token ?: return null
    return runCatching { StoreBackupJob.list(client, token) }.getOrNull()?.filter { it.first == packageName }?.map { it.second }?.sortedByDescending { it.n }
  }
}

/** Lo Store dà il suo lavoro di backup a WorkManager attraverso la sua `Application`. */
interface AccountHostOwner {
  /** Il lavoro del backup già collegato, o `null` se l'account non c'è. */
  fun storeBackupJob(): StoreBackupJob?
}

/** Il lavoro in sottofondo di WorkManager: chiama [StoreBackupJob] e riprova più tardi se il telefono è chiuso o offline. */
class StoreBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val job = (applicationContext as? AccountHostOwner)?.storeBackupJob() ?: return Result.success()
    return when (job.run()) {
      is StoreBackupJob.Outcome.Uploaded, StoreBackupJob.Outcome.Unchanged, StoreBackupJob.Outcome.NoE2e, StoreBackupJob.Outcome.SignedOut -> Result.success()
      StoreBackupJob.Outcome.Locked, StoreBackupJob.Outcome.Offline, is StoreBackupJob.Outcome.Failed -> Result.retry()
    }
  }
}

/** Il calendario del backup dello Store: una volta al giorno, con la rete, un lavoro solo. */
object BackupScheduler {
  const val WORK_NAME = "pampa-account-backup"

  fun schedule(context: Context) {
    val request = PeriodicWorkRequestBuilder<StoreBackupWorker>(1, TimeUnit.DAYS)
      .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
      .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
  }

  /** All'uscita dall'account. */
  fun cancel(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
  }
}
