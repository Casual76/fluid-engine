package dev.antigravity.fluidengine.account.prova

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.antigravity.fluidengine.account.backup.BackupFile
import dev.antigravity.fluidengine.account.backup.BackupPackage
import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import dev.antigravity.fluidengine.account.crypto.PampaRecordCipher
import dev.antigravity.fluidengine.account.host.AccountSession
import dev.antigravity.fluidengine.account.host.AppPolicy
import dev.antigravity.fluidengine.account.host.backup.StoreBackupJob
import dev.antigravity.fluidengine.account.host.custody.AndroidKeystoreVault
import dev.antigravity.fluidengine.account.host.custody.KeystoreCustodyVault
import dev.antigravity.fluidengine.account.host.custody.AppKeyPurpose
import dev.antigravity.fluidengine.account.host.custody.CustodyKeyring
import dev.antigravity.fluidengine.account.host.custody.CustodyLockedException
import dev.antigravity.fluidengine.account.host.custody.DoorSecret
import dev.antigravity.fluidengine.account.host.custody.KeyClass
import dev.antigravity.fluidengine.account.host.custody.KeyCustody
import dev.antigravity.fluidengine.account.host.custody.RecoveryCode
import dev.antigravity.fluidengine.account.host.custody.WorkKeyGrant
import dev.antigravity.fluidengine.account.host.server.AccountHttp
import dev.antigravity.fluidengine.account.host.server.AccountServerClient
import dev.antigravity.fluidengine.account.host.server.AndroidKeystoreDeviceKey
import dev.antigravity.fluidengine.account.host.server.DeviceProof
import dev.antigravity.fluidengine.account.host.server.ServerResponse
import dev.antigravity.fluidengine.account.host.server.SessionStore
import dev.antigravity.fluidengine.account.host.server.TokenCache
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Il banco della custodia sul telefono vero (critica F2). Ogni passo scrive righe «PampaProva» in logcat e in
 * `files/prova.log`, con il risultato e i millisecondi. Niente rete: il server è finto, in memoria ([fakeServer]).
 */
object Banco {
  private const val TAG = "PampaProva"
  private const val ACCOUNT = "acc_provatablet000000000000"
  private const val PKG = "dev.pampa.pampai"

  private fun dir(c: Context) = File(c.noBackupFilesDir, "prova-custodia")
  private fun log(c: Context, line: String) {
    Log.i(TAG, line)
    runCatching { File(c.filesDir, "prova.log").appendText("${System.currentTimeMillis()} $line\n") }
  }

  private fun vault(c: Context, strongBox: KeystoreCustodyVault.StrongBoxUse = KeystoreCustodyVault.StrongBoxUse.MOTHER_ONLY, prefix: String = "prova.custodia") =
    AndroidKeystoreVault(c, KeystoreCustodyVault.Config(aliasPrefix = prefix, strongBox = strongBox))

  private fun keyring(c: Context) = CustodyKeyring(vault(c), File(dir(c), "chiavi"))
  private fun locked(c: Context) = c.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == true

  private inline fun <T> timed(c: Context, what: String, block: () -> T): T? {
    val t0 = SystemClock.elapsedRealtimeNanos()
    return try {
      block().also { log(c, "OK  $what in ${(SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0} ms") }
    } catch (e: Throwable) {
      log(c, "NO  $what: ${e.javaClass.simpleName} ${e.message} (${(SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0} ms)")
      null
    }
  }

  /** Il server finto: rinnovo, token dello Store, elenco e caricamento dei backup. */
  private val uploads = mutableMapOf<Long, ByteArray>()
  private val fakeServer = AccountHttp { _, request ->
    val body = when {
      request.path == "/v1/session/refresh" -> buildJsonObject { put("accessToken", "at-prova"); put("expiresIn", 900) }
      request.path.endsWith("/token") -> buildJsonObject { put("token", "app-prova"); put("expiresIn", 900) }
      request.path == "/v1/backups" && request.method == "GET" -> buildJsonObject {
        put("backups", buildJsonArray { for ((n, b) in uploads) add(buildJsonObject { put("package", AppPolicy.STORE); put("n", n); put("epoch", 1); put("size", b.size) }) })
      }
      request.path.startsWith("/v1/backups/") -> {
        uploads[request.path.substringAfterLast('/').toLong()] = request.body ?: ByteArray(0)
        buildJsonObject { put("ok", true) }
      }
      else -> buildJsonObject { put("error", "not_found") }
    }
    ServerResponse(if (body.containsKey("error")) 404 else 200, body.toString().toByteArray(), System.currentTimeMillis())
  }

  private fun session(c: Context, keyring: CustodyKeyring): AccountSession {
    val client = AccountServerClient("https://prova.invalid", "prova", fakeServer)
    return AccountSession(client, "https://prova.invalid", SessionStore(vault(c), File(dir(c), "sessione")), AndroidKeystoreDeviceKey(c, "prova.dispositivo"), TokenCache(), keyring, AppPolicy())
  }

  /** Passo 1, a telefono sbloccato: la custodia, una busta da aprire dopo, la sessione, la chiave del dispositivo. */
  fun prepara(c: Context) {
    log(c, "== prepara: Android ${Build.VERSION.SDK_INT}, ${Build.MODEL}, bloccato=${locked(c)}")
    dir(c).deleteRecursively()
    uploads.clear()
    vault(c).destroy()
    val ring = keyring(c)
    val custody = timed(c, "custodia nuova (CD_1 e la porta del codice)") { KeyCustody.start(ACCOUNT, listOf(DoorSecret.recovery(RecoveryCode.generate()))).custody } ?: return
    timed(c, "custodia chiusa nel Keystore e chiavi di lavoro ricavate") {
      ring.saveCustody(custody, listOf(WorkKeyGrant(PKG, setOf(AppKeyPurpose.APP)), WorkKeyGrant(AppPolicy.STORE, setOf(AppKeyPurpose.BACKUP))))
    }
    val set = custody.appKeys(PKG, AppKeyPurpose.APP)
    val field = PampaRecordCipher(ACCOUNT, PKG).seal(set.keys.getValue(set.current), set.current, "memories", "busta", 1, "busta aperta in tasca".toByteArray())
    File(dir(c), "busta.txt").writeText(field)
    val s = session(c, ring)
    timed(c, "sessione salvata (classe consegna)") {
      s.adopt(buildJsonObject {
        put("accountId", ACCOUNT); put("deviceId", "dev_prova"); put("accessToken", "at-0"); put("expiresIn", 1); put("refreshToken", PampaBase64.encode(PampaCrypto.random(32)))
      }, "Prova", AppPolicy.STORE)
    }
    timed(c, "chiave del dispositivo creata e prima firma") { AndroidKeystoreDeviceKey(c, "prova.dispositivo").sign("ciao".toByteArray()) }
    log(c, "== prepara fatto: ora blocca lo schermo e suona il campanello")
  }

  /** Lo stato dello schermo, solo letto: bloccato, blocco sicuro, schermo acceso. Non cambia niente. */
  private fun screen(c: Context): String {
    val kg = c.getSystemService(KeyguardManager::class.java)
    val pm = c.getSystemService(PowerManager::class.java)
    return "bloccato=${kg?.isDeviceLocked} bloccoSicuro=${kg?.isDeviceSecure} schermoAcceso=${pm?.isInteractive}"
  }

  /**
   * Il campanello fra [seconds] secondi, con WorkManager: se nel frattempo il tablet si blocca da sé (lo schermo si
   * spegne per il suo tempo), la prova gira a telefono bloccato senza che nessuno lo blocchi.
   */
  fun campanelloFra(c: Context, seconds: Long) {
    log(c, "== campanello fra $seconds s: ${screen(c)}")
    WorkManager.getInstance(c).enqueue(OneTimeWorkRequestBuilder<CampanelloWorker>().setInitialDelay(seconds, TimeUnit.SECONDS).build())
  }

  /** Il campanello finto, a schermo bloccato: sottofondo con le chiavi di consegna, e la madre che deve restare chiusa. */
  fun campanello(c: Context) {
    log(c, "== campanello: ${screen(c)}")
    val ring = keyring(c)
    timed(c, "busta aperta con le chiavi di lavoro (consegna)") {
      val keys = ring.workKeys(PKG, AppKeyPurpose.APP) ?: error("chiavi di lavoro assenti")
      val text = PampaRecordCipher(ACCOUNT, PKG).open({ keys.keys[it] }, "memories", "busta", 1, File(dir(c), "busta.txt").readText()).decodeToString()
      check(text == "busta aperta in tasca") { "testo diverso" }
    }
    timed(c, "rinnovo: sessione letta e prova del dispositivo firmata") {
      val stored = SessionStore(vault(c), File(dir(c), "sessione")).load() ?: error("sessione assente")
      DeviceProof.create(AndroidKeystoreDeviceKey(c, "prova.dispositivo"), "POST", "https://prova.invalid/v1/session/refresh", stored.accountId, System.currentTimeMillis())
    }
    val mother = runCatching { ring.loadCustody() }
    when (val e = mother.exceptionOrNull()) {
      null -> log(c, if (locked(c)) "NO  la madre si è aperta a telefono bloccato" else "OK  la madre si apre (telefono sbloccato)")
      is CustodyLockedException -> log(c, "OK  la madre resta chiusa a telefono bloccato (${e.keyClass})")
      else -> log(c, "NO  la madre: ${e.javaClass.simpleName} ${e.message}")
    }
    WorkManager.getInstance(c).enqueue(OneTimeWorkRequestBuilder<BackupProvaWorker>().build())
    log(c, "== campanello: lavoro del backup messo in coda")
  }

  /** Il lavoro del backup in sottofondo (WorkManager), col server finto. */
  suspend fun backup(c: Context) {
    log(c, "== backup in sottofondo: bloccato=${locked(c)}")
    val ring = keyring(c)
    val s = session(c, ring)
    s.restore()
    val job = StoreBackupJob(s, AccountServerClient("https://prova.invalid", "prova", fakeServer), ring, { ACCOUNT }, {
      BackupPackage(AppPolicy.STORE, "prova", 1, System.currentTimeMillis(), listOf(BackupFile("apps.json", "[\"$PKG\"]".toByteArray())))
    }, File(dir(c), "stato"))
    val t0 = SystemClock.elapsedRealtimeNanos()
    val outcome = job.run()
    log(c, "${if (outcome is StoreBackupJob.Outcome.Uploaded) "OK " else "NO "} backup: $outcome in ${(SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0} ms")
  }

  /** La velocità di StrongBox contro il TEE: creazione, 20 chiusure e 20 aperture per classe, 20 firme del dispositivo. */
  fun velocita(c: Context) {
    log(c, "== velocità: StrongBox dichiarato=${c.packageManager.hasSystemFeature("android.hardware.strongbox_keystore")}")
    for (mode in listOf(KeystoreCustodyVault.StrongBoxUse.NONE, KeystoreCustodyVault.StrongBoxUse.ALL)) {
      val v = vault(c, mode, "prova.velocita.${mode.name.lowercase()}")
      v.destroy()
      for (k in KeyClass.entries) {
        val data = PampaCrypto.random(512)
        val first = timed(c, "$mode $k: chiave nuova e prima chiusura") { v.seal(k, data, ByteArray(0)) } ?: continue
        timed(c, "$mode $k: 20 chiusure") { repeat(20) { v.seal(k, data, ByteArray(0)) } }
        timed(c, "$mode $k: 20 aperture") { repeat(20) { v.open(k, first, ByteArray(0)) } }
        log(c, "    $mode $k in StrongBox=${v.strongBoxBacked[k]}")
      }
      v.destroy()
    }
    for (sb in listOf(false, true)) {
      val key = AndroidKeystoreDeviceKey(c, "prova.velocita.dispositivo.$sb", strongBox = sb)
      key.destroy()
      timed(c, "chiave del dispositivo StrongBox=$sb: creazione e prima firma") { key.sign(ByteArray(32)) }
      timed(c, "chiave del dispositivo StrongBox=$sb: 20 firme") { repeat(20) { key.sign(ByteArray(32)) } }
      log(c, "    dispositivo in StrongBox=${key.strongBoxBacked}")
      key.destroy()
    }
    log(c, "== velocità fatto")
  }

  /** Lo stato, per controllare a voce che cosa c'è. */
  fun stato(c: Context) {
    log(c, "== stato: ${screen(c)}, Android ${Build.VERSION.SDK_INT}, custodia=${keyring(c).hasCustody}, backup caricati=${uploads.keys}")
  }
}

/** Il campanello rimandato di [Banco.campanelloFra]. */
class CampanelloWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    Banco.campanello(applicationContext)
    return Result.success()
  }
}

/** Il lavoro del backup in sottofondo. */
class BackupProvaWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    Banco.backup(applicationContext)
    return Result.success()
  }
}
