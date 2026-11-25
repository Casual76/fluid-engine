# API · engine-storage

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-storage/storage/EngineFirstUseStore.kt`

- `class EngineFirstUseStore internal constructor(private val store: DataStore<Preferences>) : FluidFirstUseMemory` — La memoria della prima volta (F8.11) nel file dell'engine: quale versione ha già mostrato il benvenuto o le novità, quali permessi sono già stati chiesti e quali spiegazioni date.

## `engine-storage/storage/EngineInstallId.kt`

- `object EngineInstallId` — L'identita' di questa installazione dell'app (F8.21): sedici caratteri casuali, nati al primo uso e mai cambiati finche' l'app resta installata. La sincronizzazione (`engine-sync`) la usa come nome del dispositivo nel suo orologio logico.
  - `fun get(context: Context): String` — L'identita' di questa installazione. Legge un file piccolo: meglio chiamarla fuori dal thread principale.
  - `fun readOrCreate(file: File): String` — L'identita' scritta in [file], creata se manca o se il contenuto non e' valido. La scrittura passa da un file temporaneo rinominato, cosi' un processo che muore a meta' non lascia un'identita' troncata.
  - `fun isValid(id: String): Boolean` — Vero se [id] e' un'identita' come le scrive [readOrCreate].

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
