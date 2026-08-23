package dev.antigravity.fluidengine.update

import dev.antigravity.fluidengine.foundation.AppRelease
import dev.antigravity.fluidengine.foundation.AppUpdateInstallState
import dev.antigravity.fluidengine.foundation.AppUpdater
import dev.antigravity.fluidengine.foundation.AvailableAppUpdate
import dev.antigravity.fluidengine.foundation.EngineManifest
import dev.antigravity.fluidengine.foundation.EngineManifestParser
import dev.antigravity.fluidengine.foundation.UpdateChannel
import dev.antigravity.fluidengine.foundation.compareVersions
import dev.antigravity.fluidengine.foundation.isStableVersionNewer
import dev.antigravity.fluidengine.net.EngineHttp
import kotlinx.coroutines.flow.Flow

/**
 * Where releases are published.
 *
 * [manifestUrl] can be the same file the remote config uses — the two live in different sections of
 * the same document precisely so one URL is enough.
 */
data class UpdateSource(
  val manifestUrl: String,
  val applicationId: String,
  val downloadUrlTemplate: String = GithubReleaseTemplate,
) {
  companion object {
    /**
     * `{owner}`, `{repo}`, `{tag}` and `{asset}` are filled in from the manifest.
     *
     * A template rather than a hard-coded host so an app published somewhere other than GitHub
     * releases needs a string, not a fork of the updater.
     */
    const val GithubReleaseTemplate: String =
      "https://github.com/{owner}/{repo}/releases/download/{tag}/{asset}"
  }
}

/**
 * In-app updates for an app distributed outside a store.
 *
 * The check and the install are separate objects because they fail for unrelated reasons and are
 * usually shown in unrelated places: a check runs quietly on a cold start and can be ignored, an
 * install owns the screen until it finishes.
 */
class EngineAppUpdater(
  private val http: EngineHttp,
  private val source: UpdateSource,
  private val installer: AppUpdateInstaller,
) : AppUpdater {

  override suspend fun check(
    currentVersionName: String,
    channel: UpdateChannel,
    ignoredVersion: String,
  ): Result<AvailableAppUpdate?> = runCatching {
    val manifest = EngineManifestParser.parse(http.readText(source.manifestUrl))
    val release = manifest.release(channel) ?: return@runCatching null
    val update = manifest.toAvailableUpdate(release, source) ?: return@runCatching null
    when {
      update.version == ignoredVersion -> null
      channel == UpdateChannel.STABLE && !isStableVersionNewer(update.version, currentVersionName) -> null
      channel == UpdateChannel.BETA && compareVersions(update.version, currentVersionName) <= 0 -> null
      else -> update
    }
  }

  override fun install(update: AvailableAppUpdate): Flow<AppUpdateInstallState> =
    installer.install(update)

  private fun EngineManifest.release(channel: UpdateChannel): AppRelease? = when (channel) {
    UpdateChannel.STABLE -> app?.stable
    // An app on the beta channel that finds no beta published falls back to stable rather than
    // reporting "no updates" forever after a beta line is retired.
    UpdateChannel.BETA -> app?.beta ?: app?.stable
  }

  private fun EngineManifest.toAvailableUpdate(
    release: AppRelease,
    source: UpdateSource,
  ): AvailableAppUpdate? {
    val repository = app?.repository ?: return null
    val asset = release.apkAsset.ifBlank { "${source.applicationId}-${release.version}.apk" }
    val url = source.downloadUrlTemplate
      .replace("{owner}", repository.owner)
      .replace("{repo}", repository.name)
      .replace("{tag}", release.releaseTag)
      .replace("{asset}", asset)
    return AvailableAppUpdate(
      version = release.version,
      changelog = release.changelog,
      releaseTag = release.releaseTag,
      apkAsset = asset,
      downloadUrl = url,
      sizeBytes = release.sizeBytes,
    )
  }
}

/** Downloads and installs an APK. Separated so the check can be tested without a device. */
interface AppUpdateInstaller {
  fun install(update: AvailableAppUpdate): Flow<AppUpdateInstallState>
}
