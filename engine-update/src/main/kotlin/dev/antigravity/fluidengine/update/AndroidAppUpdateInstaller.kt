package dev.antigravity.fluidengine.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dev.antigravity.fluidengine.foundation.AppUpdateInstallState
import dev.antigravity.fluidengine.foundation.AvailableAppUpdate
import dev.antigravity.fluidengine.net.EngineHttp
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withContext

/**
 * Installs an update through `PackageInstaller`.
 *
 * The three checks before the commit are not ceremony. An APK that is not this app, or not the
 * version the manifest advertised, is either a mistake in the release or someone else's file — and
 * the failure mode without them is an install prompt for an unrelated package, which is the worst
 * thing a self-updater can do.
 */
class AndroidAppUpdateInstaller(
  private val context: Context,
  private val http: EngineHttp = EngineHttp(),
) : AppUpdateInstaller {

  override fun install(update: AvailableAppUpdate): Flow<AppUpdateInstallState> = channelFlow {
    send(AppUpdateInstallState.Verifying("Preparazione aggiornamento..."))

    if (!context.packageManager.canRequestPackageInstalls()) {
      // Sending the user to the setting is the only way forward, and the screen does not report
      // back: the flow ends here and the next attempt finds the permission granted.
      runCatching {
        context.startActivity(
          Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
          ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
      }
      send(
        AppUpdateInstallState.Error(
          "Abilita l'installazione da questa app nelle impostazioni di Android e riprova.",
        ),
      )
      return@channelFlow
    }

    val apk = runCatching {
      val directory = File(context.cacheDir, "engine_updates").apply { mkdirs() }
      val target = File(directory, update.apkAsset.ifBlank { "update-${update.version}.apk" })
      http.download(
        url = update.downloadUrl,
        target = target,
        expectedBytes = update.sizeBytes,
      ) { progress, downloaded, total ->
        trySend(AppUpdateInstallState.Downloading(progress, downloaded, total))
      }
    }.getOrElse { error ->
      send(AppUpdateInstallState.Error(error.message ?: "Download aggiornamento non riuscito."))
      return@channelFlow
    }

    send(AppUpdateInstallState.Verifying("Verifica APK..."))
    val packageInfo = context.packageManager.readArchiveInfo(apk.absolutePath)
    when {
      packageInfo == null -> {
        send(AppUpdateInstallState.Error("Android non riesce a leggere l'APK scaricato."))
        return@channelFlow
      }
      packageInfo.packageName != context.packageName -> {
        send(AppUpdateInstallState.Error("L'APK scaricato non appartiene a questa app."))
        return@channelFlow
      }
      else -> {
        val apkVersion = packageInfo.versionName.orEmpty()
        if (apkVersion.isNotBlank() && apkVersion != update.version) {
          send(AppUpdateInstallState.Error("Versione APK inattesa: $apkVersion."))
          return@channelFlow
        }
      }
    }

    runCatching {
      commitSession(apk, packageInfo) { state -> send(state) }
    }.onFailure { error ->
      send(AppUpdateInstallState.Error(error.message ?: "Installazione non riuscita."))
    }
  }

  private suspend fun commitSession(
    file: File,
    packageInfo: PackageInfo,
    emit: suspend (AppUpdateInstallState) -> Unit,
  ) {
    val packageInstaller = context.packageManager.packageInstaller
    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
      setAppPackageName(packageInfo.packageName)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        setPackageSource(PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE)
      }
    }
    val sessionId = runCatching { packageInstaller.createSession(params) }.getOrElse { error ->
      throw IllegalStateException(
        error.message ?: "Impossibile avviare la sessione di installazione.",
        error,
      )
    }

    val events = AppUpdateInstallSessionRegistry.register(sessionId)
    try {
      emit(AppUpdateInstallState.Installing("Installazione aggiornamento..."))
      withContext(Dispatchers.IO) {
        packageInstaller.openSession(sessionId).use { session ->
          file.inputStream().use { input ->
            session.openWrite(file.name, 0, file.length()).use { output ->
              input.copyTo(output)
              session.fsync(output)
            }
          }
          val callback = Intent(context, AppUpdateInstallResultReceiver::class.java).apply {
            action = AppUpdateInstallResultReceiver.ACTION_INSTALL_STATUS
            putExtra(AppUpdateInstallResultReceiver.EXTRA_SESSION_ID, sessionId)
          }
          val pendingIntent = PendingIntent.getBroadcast(
            context,
            sessionId,
            callback,
            PendingIntent.FLAG_UPDATE_CURRENT or
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0,
          )
          session.commit(pendingIntent.intentSender)
        }
      }
    } catch (error: Exception) {
      runCatching { packageInstaller.abandonSession(sessionId) }
      AppUpdateInstallSessionRegistry.unregister(sessionId)
      throw error
    }

    var terminal: AppUpdateInstallState? = null
    events
      .takeWhile { event ->
        val keepGoing =
          event !is AppUpdateInstallState.Installed && event !is AppUpdateInstallState.Error
        if (!keepGoing) terminal = event
        keepGoing
      }
      .collect { emit(it) }
    terminal?.let { event ->
      emit(
        if (event is AppUpdateInstallState.Installed && event.filePath.isBlank()) {
          event.copy(filePath = file.absolutePath)
        } else {
          event
        },
      )
    }
  }
}

/**
 * Receives what `PackageInstaller` has to say about a session.
 *
 * Declared in the engine's own manifest, so a host app gets it by depending on the module rather
 * than by remembering to copy a `<receiver>` element.
 */
class AppUpdateInstallResultReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val sessionId = intent.getIntExtra(
      EXTRA_SESSION_ID,
      intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1),
    )
    if (sessionId == -1) return

    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
    val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
    when (status) {
      PackageInstaller.STATUS_PENDING_USER_ACTION -> {
        intent.parcelableIntent(Intent.EXTRA_INTENT)?.let { confirmation ->
          confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          runCatching { context.startActivity(confirmation) }
        }
        AppUpdateInstallSessionRegistry.tryEmit(
          sessionId,
          AppUpdateInstallState.AwaitingUserAction("Conferma l'installazione sul dispositivo."),
        )
      }
      PackageInstaller.STATUS_SUCCESS -> AppUpdateInstallSessionRegistry.tryEmit(
        sessionId,
        AppUpdateInstallState.Installed(""),
      )
      else -> AppUpdateInstallSessionRegistry.tryEmit(
        sessionId,
        AppUpdateInstallState.Error(status.toInstallFailureMessage(statusMessage)),
      )
    }
  }

  companion object {
    const val ACTION_INSTALL_STATUS = "dev.antigravity.fluidengine.action.UPDATE_INSTALL_STATUS"
    const val EXTRA_SESSION_ID = "dev.antigravity.fluidengine.extra.UPDATE_SESSION_ID"
  }
}

/**
 * Carries session events from the broadcast receiver back to the flow that is waiting for them.
 *
 * A receiver is a separate object with no reference to the collector, and the system may deliver
 * the first event before the flow has started collecting — hence the replay buffer.
 */
object AppUpdateInstallSessionRegistry {
  private val sessions = ConcurrentHashMap<Int, MutableSharedFlow<AppUpdateInstallState>>()

  fun register(sessionId: Int): SharedFlow<AppUpdateInstallState> {
    val flow = MutableSharedFlow<AppUpdateInstallState>(replay = 8, extraBufferCapacity = 8)
    sessions[sessionId] = flow
    return flow.asSharedFlow()
  }

  fun unregister(sessionId: Int) {
    sessions.remove(sessionId)
  }

  fun tryEmit(sessionId: Int, event: AppUpdateInstallState) {
    sessions[sessionId]?.tryEmit(event)
    if (event is AppUpdateInstallState.Installed || event is AppUpdateInstallState.Error) {
      unregister(sessionId)
    }
  }
}

@Suppress("DEPRECATION")
private fun PackageManager.readArchiveInfo(path: String): PackageInfo? =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(0))
  } else {
    getPackageArchiveInfo(path, 0)
  }

@Suppress("DEPRECATION")
private fun Intent.parcelableIntent(key: String): Intent? =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    getParcelableExtra(key, Intent::class.java)
  } else {
    getParcelableExtra(key) as? Intent
  }

private fun Int.toInstallFailureMessage(systemMessage: String): String = when (this) {
  PackageInstaller.STATUS_FAILURE_ABORTED -> "Installazione annullata."
  PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android ha bloccato l'installazione dell'APK."
  PackageInstaller.STATUS_FAILURE_CONFLICT -> "Conflitto di firma o versione con l'app installata."
  PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "L'APK non e' compatibile con questo dispositivo."
  PackageInstaller.STATUS_FAILURE_INVALID -> "Android considera l'APK non valido o non installabile."
  PackageInstaller.STATUS_FAILURE_STORAGE -> "Spazio insufficiente per completare l'installazione."
  else -> systemMessage.ifBlank { "Installazione non riuscita." }
}
