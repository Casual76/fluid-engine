<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/config.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · engine-config

_Generato sull'engine 3.1.1 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-config/config/EngineRemoteConfig.kt`

- `data class EngineConfigSource(val manifestUrl: String, val applicationId: String, val engineVersion: String = EngineBuild.VERSION, val refreshIntervalMillis: Long = DefaultRefreshIntervalMillis)` — Where an app reads its control file from, and how often.
  - `companion object`
    - `const val DefaultRefreshIntervalMillis: Long` — Six hours.
- `class EngineRemoteConfig(private val http: EngineHttp, private val cache: EngineConfigCache, private val source: EngineConfigSource, private val now: () -> Long = { System.currentTimeMillis() })` — The remote half of the engine: the part that can change a shipped build's behaviour.
  - `val config: Flow<EngineConfig>`
  - `fun flag(flag: EngineFlag): Flow<Boolean>` — One flag, as a flow, for a screen that wants to react the moment a refresh lands.
  - `suspend fun current(): EngineConfig`
  - `suspend fun compatibility(): EngineCompatibility`
  - `suspend fun refresh(): Result<EngineConfig>` — Fetches the manifest and caches it.
  - `suspend fun refreshIfStale(): Result<EngineConfig>?` — Refreshes only if the cached copy is older than [EngineConfigSource.refreshIntervalMillis].
