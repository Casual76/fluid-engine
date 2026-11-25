<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/storage.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · engine-storage

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-storage/storage/EngineFirstUseStore.kt`

- `class EngineFirstUseStore internal constructor(private val store: DataStore<Preferences>) : FluidFirstUseMemory` — La memoria della prima volta (F8.11) nel file dell'engine: quale versione ha già mostrato il benvenuto o le novità, quali permessi sono già stati chiesti e quali spiegazioni date.

## `engine-storage/storage/EngineInstallId.kt`

- `object EngineInstallId` — L'identita' di questa installazione dell'app (F8.21): sedici caratteri casuali, nati al primo uso e mai cambiati finche' l'app resta installata. La sincronizzazione (`engine-sync`) la usa come nome del dispositivo nel suo orologio logico.
  - `fun get(context: Context): String` — L'identita' di questa installazione. Legge un file piccolo: meglio chiamarla fuori dal thread principale.
  - `fun readOrCreate(file: File): String` — L'identita' scritta in [file], creata se manca o se il contenuto non e' valido. La scrittura passa da un file temporaneo rinominato, cosi' un processo che muore a meta' non lascia un'identita' troncata.
  - `fun isValid(id: String): Boolean` — Vero se [id] e' un'identita' come le scrive [readOrCreate].

## `engine-storage/storage/EngineSharedAppearance.kt`

- `data class EngineAppearanceLayer(val themeMode: ThemeMode? = null, val accent: String? = null, val glassPreference: Float? = null, val textScale: Float? = null, val highContrast: Boolean? = null, val reducedMotion: Boolean? = null, val iconSet: String? = null, val followShared: Boolean = true)` — F10.8 · Le scelte d'aspetto che arrivano **da fuori dall'app** (l'aspetto comune di Pampa Store), già risolte per quest'app: per ogni campo prima la sua eccezione, se l'utente l'ha scelta, poi il tema comune. Un campo `null` vuol dire «nessuna scelta comune»: vale quello dell'app ([applyTo]).
  - `fun applyTo(own: EngineSettings): EngineSettings` — Le impostazioni dell'app ([own]) con sopra le scelte comuni. Uno scuro comune su un'app che aveva il nero vero (`ThemeMode.AMOLED`) resta nero: il nero si piega in `amoledEnabled`, come fa la sezione Aspetto dell'engine.
  - `val isEmpty: Boolean` — Nessuna scelta: [applyTo] restituisce le impostazioni dell'app così come sono.
  - `companion object`
    - `val EMPTY`
    - `const val ACCENT_APP` — L'accento comune «il colore di ogni app»: ogni app torna al suo marchio.
    - `const val ACCENT_SYSTEM` — L'accento comune «i colori del sistema» (Android 12+; sotto, il marchio dell'app).
- `interface EngineSharedAppearance` — F10.8 · Una sorgente dell'aspetto comune. La dà `engine-account` (l'aspetto di Pampa Store, che funziona anche senza account): basta averlo fra le dipendenze, e [EngineSettingsStore] la trova da sé con `ServiceLoader`, come i moduli dei visori. Niente codice nell'`Application`. Senza `engine-account`, o senza Store, l'app ha il suo tema di sempre (R1).
  - `fun layer(context: Context): Flow<EngineAppearanceLayer?>` — Le scelte comuni per quest'app, già con la sua eccezione sopra; `null` quando la sorgente non c'è (lo Store manca, o è firmato da altri): allora vale solo l'app. Il primo valore arriva presto (dalla copia locale), poi a ogni cambio.
  - `suspend fun setException(context: Context, followShared: Boolean, exception: EngineAppearanceLayer?)` — L'eccezione di quest'app: [followShared] `true` torna al tema comune; `false` tiene i campi non nulli di [exception] (gli altri seguono il comune). Scrive la sorgente, non l'app (sul telefono scrive solo lo Store).
  - `companion object`
    - `fun current(): EngineSharedAppearance?` — La sorgente di questo processo: quella installata a mano ([install]), o quella trovata, o `null`.
    - `fun install(source: EngineSharedAppearance?)` — Mette a mano la sorgente (le prove, o un'app che vuole la sua). `null` toglie l'aspetto comune; [reset] torna a quella trovata.
    - `fun reset()` — Torna alla sorgente trovata con `ServiceLoader`.

## `engine-storage/storage/EngineStore.kt`

- `class EngineSettingsStore @JvmOverloads constructor(private val context: Context, private val shared: EngineSharedAppearance? = EngineSharedAppearance.current())` — The appearance settings the design system reads.
  - `val ownSettings: Flow<EngineSettings>` — Le impostazioni dell'app, senza l'aspetto comune: quelle che scrive [update].
  - `val sharedAppearance: Flow<EngineAppearanceLayer?>` — F10.8 · Le scelte comuni per quest'app (eccezione compresa), o `null` senza sorgente.
  - `val settings: Flow<EngineSettings>` — Quello che il tema deve dipingere: le impostazioni dell'app con sopra l'aspetto comune, se c'è.
  - `suspend fun current(): EngineSettings`
  - `suspend fun setSharedException(followShared: Boolean, exception: EngineAppearanceLayer? = null): Boolean` — F10.8 · La sezione Aspetto dell'app, quando c'è lo Store: «Segui Pampa Store» ([followShared] `true`) oppure i valori di quest'app ([exception], solo i campi non nulli). Li scrive lo Store. Risponde `false` senza sorgente: allora l'app scrive le sue impostazioni con [update], come sempre.
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
