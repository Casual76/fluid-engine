<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/foundation.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · engine-foundation

_Generato sull'engine 3.0.7 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-foundation/foundation/AppUpdate.kt`

- `enum class UpdateChannel` — Which release line an app follows.
- `data class AvailableAppUpdate(val version: String, val changelog: String, val releaseTag: String, val apkAsset: String, val downloadUrl: String, val sizeBytes: Long, val sha256: String = "")` — A release the manifest is offering, already resolved to something downloadable.
- `sealed interface AppUpdateInstallState` — Where an install has got to.
  - `data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : AppUpdateInstallState`
  - `data class Verifying(val message: String) : AppUpdateInstallState`
  - `data class Installing(val message: String) : AppUpdateInstallState`
  - `data class AwaitingUserAction(val message: String) : AppUpdateInstallState` — Android is showing its own confirmation; nothing will move until the user answers it.
  - `data class Installed(val filePath: String) : AppUpdateInstallState`
  - `data class Error(val message: String, val kind: AppUpdateErrorKind = AppUpdateErrorKind.Failed) : AppUpdateInstallState` — The install stopped. [kind] says why when the reason changes what the screen should offer (3.0.1): [AppUpdateErrorKind.InstallPermission] is not a failure to show, it is Android asking once for the permission to install from this app — the screen says so and offers the settings, instead of a red "update failed".
- `enum class AppUpdateErrorKind` — Why an install stopped, when it matters to what comes next. Since 3.0.1.
- `interface AppUpdater` — In-app updates for apps distributed outside a store.
  - `suspend fun check(currentVersionName: String, channel: UpdateChannel = UpdateChannel.STABLE, ignoredVersion: String = ""): Result<AvailableAppUpdate?>`
  - `fun install(update: AvailableAppUpdate): Flow<AppUpdateInstallState>`

## `engine-foundation/foundation/EngineConfig.kt`

- `data class EngineFlag(val key: String, val default: Boolean = false)` — A flag, declared where it is used.
- `data class EngineConfig(val schema: Int = EngineManifestSchema, val minimumVersion: String? = null, val recommendedVersion: String? = null, val notice: String? = null, val flags: Map<String, Boolean> = emptyMap(), val killSwitch: EngineKillSwitch = EngineKillSwitch(), val fetchedAtEpochMillis: Long? = null)` — The manifest as it applies to *this* app, at this version, right now.
  - `fun isEnabled(flag: EngineFlag): Boolean`
  - `fun isEnabled(key: String, default: Boolean = false): Boolean`
  - `fun compatibility(engineVersion: String = EngineBuild.VERSION): EngineCompatibility` — Whether this build's engine is behind what the manifest asks for.
  - `val isFallback: Boolean get()` — True before the first successful fetch: every value here is the built-in default.
  - `companion object`
    - `val Fallback` — What the app runs on until a manifest has been read — deliberately, an empty one.
- `fun resolveEngineConfig(manifest: EngineManifest, applicationId: String, fetchedAtEpochMillis: Long?): EngineConfig` — Folds the shared section and this app's override into one answer.

## `engine-foundation/foundation/EngineManifest.kt`

- `data class EngineManifest(val app: AppReleases? = null, val engine: EngineSection? = null)` — The one file that every app built on the engine reads.
- `data class AppReleases(val id: String = "", val packageName: String = "", val repository: ReleaseRepository? = null, val stable: AppRelease? = null, val beta: AppRelease? = null)`
- `data class ReleaseRepository(val owner: String, val name: String)`
- `data class AppRelease(val version: String, val changelog: String = "", val releaseTag: String = "", val apkAsset: String = "", val sizeBytes: Long = 0L, val releaseDate: String = "", val sha256: String = "")`
- `data class EngineSection(val schema: Int = EngineManifestSchema, val minimumVersion: String? = null, val recommendedVersion: String? = null, val notice: String? = null, val flags: Map<String, Boolean> = emptyMap(), val killSwitch: EngineKillSwitch = EngineKillSwitch(), val overrides: Map<String, EngineOverride> = emptyMap())` — The remotely controlled half of the manifest.
- `data class EngineOverride(val minimumVersion: String? = null, val recommendedVersion: String? = null, val notice: String? = null, val flags: Map<String, Boolean> = emptyMap(), val killSwitch: EngineKillSwitch? = null)`
- `data class EngineKillSwitch(val enabled: Boolean = false, val message: String? = null)` — The last resort: a remote instruction to stop.
- `const val EngineManifestSchema: Int` — Bumped only when the meaning of an existing field changes; new optional fields do not count.
- `object EngineManifestParser` — Reads a manifest without a serialization plugin.
  - `fun parse(raw: String): EngineManifest`

## `engine-foundation/foundation/EngineSettings.kt`

- `enum class ThemeMode` — How the app decides between its light and dark palettes.
- `enum class AccentMode` — Where the accent colour comes from.
- `data class EngineSettings(val themeMode: ThemeMode = ThemeMode.SYSTEM, val accentMode: AccentMode = AccentMode.BRAND, val customAccentName: String = "fluid", val dynamicColorEnabled: Boolean = true, val amoledEnabled: Boolean = false, val hapticsEnabled: Boolean = true)` — Everything the engine needs to know to paint an app.

## `engine-foundation/foundation/EngineVersion.kt`

- `object EngineBuild` — Which engine this app was built against.
  - `const val VERSION: String`
- `fun compareVersions(left: String, right: String): Int` — Orders two versions the way a release train does: numerically segment by segment, with a pre-release suffix sorting *before* the release it leads to, so `1.2.0-beta1` < `1.2.0`.
- `fun isStableVersionNewer(candidate: String, current: String): Boolean` — Whether [candidate] is a stable release newer than [current].
- `enum class EngineCompatibility` — How far behind the engine this app carries is.
- `fun engineCompatibility(engineVersion: String = EngineBuild.VERSION, minimumVersion: String?, recommendedVersion: String?): EngineCompatibility`

## `engine-foundation/foundation/SyncStatus.kt`

- `enum class SyncState` — What the last attempt to refresh the app's data did.
- `data class SyncStatus(val state: SyncState = SyncState.IDLE, val lastSuccessfulSyncEpochMillis: Long? = null, val message: String? = null, val failedSections: List<String> = emptyList(), val notice: String? = null)` — The result of the app's last refresh, in the shape the design system's status components read.
