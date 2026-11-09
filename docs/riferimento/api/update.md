<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/update.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · engine-update

_Generato sull'engine 3.1.0 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-update/update/AndroidAppUpdateInstaller.kt`

- `class AndroidAppUpdateInstaller(private val context: Context, private val http: EngineHttp = EngineHttp()) : AppUpdateInstaller` — Installs an update through `PackageInstaller`.
  - `suspend fun download(update: AvailableAppUpdate, onProgress: (AppUpdateInstallState.Downloading) -> Unit = {}): File` — Downloads [update]'s APK into this app's cache and returns the file, without installing it.
  - `fun installFile(file: File, expectedVersionName: String, expectedPackageName: String = context.packageName, sha256: String = ""): Flow<AppUpdateInstallState>` — Verifies an APK already on disk and installs it.
- `class AppUpdateInstallResultReceiver : BroadcastReceiver()` — Receives what `PackageInstaller` has to say about a session.
  - `companion object`
    - `const val ACTION_INSTALL_STATUS`
    - `const val EXTRA_SESSION_ID`
- `object AppUpdateInstallSessionRegistry` — Carries session events from the broadcast receiver back to the flow that is waiting for them.
  - `fun register(sessionId: Int): SharedFlow<AppUpdateInstallState>`
  - `fun unregister(sessionId: Int)`
  - `fun tryEmit(sessionId: Int, event: AppUpdateInstallState)`

## `engine-update/update/EngineAppUpdater.kt`

- `data class UpdateSource(val manifestUrl: String, val applicationId: String, val downloadUrlTemplate: String = GithubReleaseTemplate)` — Where releases are published.
  - `companion object`
    - `const val GithubReleaseTemplate: String` — `{owner}`, `{repo}`, `{tag}` and `{asset}` are filled in from the manifest.
- `class EngineAppUpdater(private val http: EngineHttp, private val source: UpdateSource, private val installer: AppUpdateInstaller) : AppUpdater` — In-app updates for an app distributed outside a store.
- `interface AppUpdateInstaller` — Downloads and installs an APK. Separated so the check can be tested without a device.
  - `fun install(update: AvailableAppUpdate): Flow<AppUpdateInstallState>`
