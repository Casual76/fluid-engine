package dev.antigravity.fluidengine.foundation

import kotlinx.coroutines.flow.Flow

/** Which release line an app follows. */
enum class UpdateChannel {
  STABLE,
  BETA,
}

/** A release the manifest is offering, already resolved to something downloadable. */
data class AvailableAppUpdate(
  val version: String,
  val changelog: String,
  val releaseTag: String,
  val apkAsset: String,
  val downloadUrl: String,
  val sizeBytes: Long,
  /** Hex SHA-256 the APK must have, or blank when the manifest did not say. Since 2.10.0. */
  val sha256: String = "",
)

/**
 * Where an install has got to.
 *
 * Modelled as states rather than a percentage because most of an install is not a percentage: the
 * long pauses are verification and the system's own confirmation dialog, and a progress bar frozen
 * at 100% is the single most common way a sideloaded update looks broken while working fine.
 */
sealed interface AppUpdateInstallState {
  data class Downloading(
    val progress: Float,
    val downloadedBytes: Long,
    val totalBytes: Long,
  ) : AppUpdateInstallState

  data class Verifying(val message: String) : AppUpdateInstallState

  data class Installing(val message: String) : AppUpdateInstallState

  /** Android is showing its own confirmation; nothing will move until the user answers it. */
  data class AwaitingUserAction(val message: String) : AppUpdateInstallState

  data class Installed(val filePath: String) : AppUpdateInstallState

  /**
   * The install stopped. [kind] says why when the reason changes what the screen should offer (3.0.1):
   * [AppUpdateErrorKind.InstallPermission] is not a failure to show, it is Android asking once for the
   * permission to install from this app — the screen says so and offers the settings, instead of a red
   * "update failed". A new reason is a new [AppUpdateErrorKind], never a new state: an app's exhaustive
   * `when` over these states keeps compiling.
   */
  data class Error(
    val message: String,
    val kind: AppUpdateErrorKind = AppUpdateErrorKind.Failed,
  ) : AppUpdateInstallState
}

/** Why an install stopped, when it matters to what comes next. Since 3.0.1. */
enum class AppUpdateErrorKind {
  /** Anything else: the message says what. */
  Failed,

  /** This app may not install packages yet; the next attempt, after the setting, goes through. */
  InstallPermission,
}

/**
 * In-app updates for apps distributed outside a store.
 *
 * [check] returns `null` when there is nothing newer — an absent update is an ordinary outcome, not
 * a failure, and folding the two together is how "check for updates" ends up showing an error
 * because everything is fine.
 */
interface AppUpdater {
  suspend fun check(
    currentVersionName: String,
    channel: UpdateChannel = UpdateChannel.STABLE,
    ignoredVersion: String = "",
  ): Result<AvailableAppUpdate?>

  fun install(update: AvailableAppUpdate): Flow<AppUpdateInstallState>
}
