package dev.antigravity.fluidengine.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.antigravity.fluidengine.foundation.AccentMode
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * One DataStore for everything the engine itself keeps.
 *
 * Separate from the app's own store on purpose: the engine's file can be deleted, migrated or
 * reset by the engine without touching a single one of the app's preferences, which is what makes
 * an engine update a safe thing to ship.
 */
internal val Context.fluidEngineStore: DataStore<Preferences> by preferencesDataStore(name = "fluid_engine")

/**
 * The appearance settings the design system reads.
 *
 * An app with its own settings screen usually already stores these; in that case skip this class
 * and map your own model to [EngineSettings] instead. It exists so a new app gets a working theme
 * picker without writing persistence for it first.
 *
 * **F10.8 · L'aspetto comune.** Con `engine-account` fra le dipendenze, [settings] mette sopra le scelte dell'app
 * quelle comuni di Pampa Store ([EngineSharedAppearance], trovata da sé): per ogni campo vince l'eccezione dell'app,
 * poi il tema comune, poi il valore dell'app. Così `FluidTheme` cambia quando l'utente cambia tema nello Store, senza
 * che l'app faccia niente. Senza Store, o senza `engine-account`, [settings] è quello di sempre.
 *
 * @param shared la sorgente dell'aspetto comune; di serie quella trovata ([EngineSharedAppearance.current]).
 */
class EngineSettingsStore @JvmOverloads constructor(
  private val context: Context,
  private val shared: EngineSharedAppearance? = EngineSharedAppearance.current(),
) {

  /** Le impostazioni dell'app, senza l'aspetto comune: quelle che scrive [update]. */
  val ownSettings: Flow<EngineSettings> = context.fluidEngineStore.data.map { it.toSettings() }

  /** F10.8 · Le scelte comuni per quest'app (eccezione compresa), o `null` senza sorgente. */
  val sharedAppearance: Flow<EngineAppearanceLayer?> = shared?.layer(context) ?: flowOf(null)

  /** Quello che il tema deve dipingere: le impostazioni dell'app con sopra l'aspetto comune, se c'è. */
  val settings: Flow<EngineSettings> =
    if (shared == null) ownSettings else combine(ownSettings, sharedAppearance) { own, layer -> layer?.applyTo(own) ?: own }

  suspend fun current(): EngineSettings = settings.first()

  /**
   * F10.8 · La sezione Aspetto dell'app, quando c'è lo Store: «Segui Pampa Store» ([followShared] `true`) oppure i
   * valori di quest'app ([exception], solo i campi non nulli). Li scrive lo Store. Risponde `false` senza sorgente:
   * allora l'app scrive le sue impostazioni con [update], come sempre.
   */
  suspend fun setSharedException(followShared: Boolean, exception: EngineAppearanceLayer? = null): Boolean {
    val source = shared ?: return false
    source.setException(context, followShared, exception)
    return true
  }

  suspend fun update(transform: (EngineSettings) -> EngineSettings) {
    context.fluidEngineStore.edit { preferences ->
      val next = transform(preferences.toSettings())
      preferences[Keys.ThemeMode] = next.themeMode.name
      preferences[Keys.AccentMode] = next.accentMode.name
      preferences[Keys.CustomAccentName] = next.customAccentName
      preferences[Keys.DynamicColorEnabled] = next.dynamicColorEnabled
      preferences[Keys.AmoledEnabled] = next.amoledEnabled
      preferences[Keys.HapticsEnabled] = next.hapticsEnabled
      // F10.8: i campi nuovi si scrivono solo se diversi dal valore di serie, cosi' un file vecchio resta uguale.
      next.glassPreference.let { if (it == null) preferences.remove(Keys.GlassPreference) else preferences[Keys.GlassPreference] = it }
      if (next.textScale == 1f) preferences.remove(Keys.TextScale) else preferences[Keys.TextScale] = next.textScale
      if (!next.highContrast) preferences.remove(Keys.HighContrast) else preferences[Keys.HighContrast] = true
      if (!next.reducedMotion) preferences.remove(Keys.ReducedMotion) else preferences[Keys.ReducedMotion] = true
    }
  }

  suspend fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }

  suspend fun setAccentMode(mode: AccentMode) = update { it.copy(accentMode = mode) }

  suspend fun setCustomAccent(name: String) =
    update { it.copy(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = name) }

  suspend fun setDynamicColorEnabled(enabled: Boolean) =
    update { it.copy(dynamicColorEnabled = enabled) }

  suspend fun setAmoledEnabled(enabled: Boolean) = update { it.copy(amoledEnabled = enabled) }

  suspend fun setHapticsEnabled(enabled: Boolean) = update { it.copy(hapticsEnabled = enabled) }

  private fun Preferences.toSettings(): EngineSettings {
    val defaults = EngineSettings()
    return EngineSettings(
      // A stored value that no longer names anything — an enum entry removed by an engine update —
      // resolves to the default rather than throwing on a read the user cannot see or fix.
      themeMode = this[Keys.ThemeMode]?.let { name ->
        runCatching { ThemeMode.valueOf(name) }.getOrNull()
      } ?: defaults.themeMode,
      accentMode = this[Keys.AccentMode]?.let { name ->
        runCatching { AccentMode.valueOf(name) }.getOrNull()
      } ?: defaults.accentMode,
      customAccentName = this[Keys.CustomAccentName] ?: defaults.customAccentName,
      dynamicColorEnabled = this[Keys.DynamicColorEnabled] ?: defaults.dynamicColorEnabled,
      amoledEnabled = this[Keys.AmoledEnabled] ?: defaults.amoledEnabled,
      hapticsEnabled = this[Keys.HapticsEnabled] ?: defaults.hapticsEnabled,
      glassPreference = this[Keys.GlassPreference]?.takeIf { it.isFinite() }?.coerceIn(0f, 1f),
      textScale = this[Keys.TextScale]?.takeIf { it.isFinite() && it > 0f } ?: defaults.textScale,
      highContrast = this[Keys.HighContrast] ?: defaults.highContrast,
      reducedMotion = this[Keys.ReducedMotion] ?: defaults.reducedMotion,
    )
  }

  private object Keys {
    val ThemeMode = stringPreferencesKey("theme_mode")
    val AccentMode = stringPreferencesKey("accent_mode")
    val CustomAccentName = stringPreferencesKey("custom_accent_name")
    val DynamicColorEnabled = booleanPreferencesKey("dynamic_color_enabled")
    val AmoledEnabled = booleanPreferencesKey("amoled_enabled")
    val HapticsEnabled = booleanPreferencesKey("haptics_enabled")
    val GlassPreference = floatPreferencesKey("glass_preference")
    val TextScale = floatPreferencesKey("text_scale")
    val HighContrast = booleanPreferencesKey("high_contrast")
    val ReducedMotion = booleanPreferencesKey("reduced_motion")
  }
}

/** The last manifest that was read, kept so the app has an answer before the network has one. */
data class CachedManifest(
  val raw: String,
  val fetchedAtEpochMillis: Long,
)

/**
 * Holds the raw manifest text rather than a parsed object.
 *
 * The parse belongs to whichever engine version is running: a build that stored a decoded model
 * would have to migrate it after every schema change, while re-reading the text costs nothing and
 * can never disagree with the code doing the reading.
 */
class EngineConfigCache(private val context: Context) {

  val cached: Flow<CachedManifest?> = context.fluidEngineStore.data.map { preferences ->
    val raw = preferences[Keys.ManifestJson] ?: return@map null
    CachedManifest(
      raw = raw,
      fetchedAtEpochMillis = preferences[Keys.FetchedAt] ?: 0L,
    )
  }

  suspend fun store(raw: String, fetchedAtEpochMillis: Long) {
    context.fluidEngineStore.edit { preferences ->
      preferences[Keys.ManifestJson] = raw
      preferences[Keys.FetchedAt] = fetchedAtEpochMillis
    }
  }

  private object Keys {
    val ManifestJson = stringPreferencesKey("engine_manifest_json")
    val FetchedAt = longPreferencesKey("engine_manifest_fetched_at")
  }
}
