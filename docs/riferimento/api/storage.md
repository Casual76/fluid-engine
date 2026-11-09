<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/storage.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · engine-storage

_Generato sull'engine 3.0.6 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-storage/storage/EngineStore.kt`

- `class EngineSettingsStore(private val context: Context)` — The appearance settings the design system reads.
  - `val settings: Flow<EngineSettings>`
  - `suspend fun current(): EngineSettings`
  - `suspend fun update(transform: (EngineSettings) -> EngineSettings)`
  - `suspend fun setThemeMode(mode: ThemeMode)`
  - `suspend fun setAccentMode(mode: AccentMode)`
  - `suspend fun setCustomAccent(name: String)`
  - `suspend fun setDynamicColorEnabled(enabled: Boolean)`
  - `suspend fun setAmoledEnabled(enabled: Boolean)`
  - `suspend fun setHapticsEnabled(enabled: Boolean)`
- `data class CachedManifest(val raw: String, val fetchedAtEpochMillis: Long)` — The last manifest that was read, kept so the app has an answer before the network has one.
- `class EngineConfigCache(private val context: Context)` — Holds the raw manifest text rather than a parsed object.
  - `val cached: Flow<CachedManifest?>`
  - `suspend fun store(raw: String, fetchedAtEpochMillis: Long)`
