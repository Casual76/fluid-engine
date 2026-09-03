package dev.antigravity.fluidengine.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.antigravity.fluidengine.foundation.AccentMode
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * One DataStore for everything the engine itself keeps.
 *
 * Separate from the app's own store on purpose: the engine's file can be deleted, migrated or
 * reset by the engine without touching a single one of the app's preferences, which is what makes
 * an engine update a safe thing to ship.
 */
private val Context.fluidEngineStore: DataStore<Preferences> by preferencesDataStore(name = "fluid_engine")

/**
 * The appearance settings the design system reads.
 *
 * An app with its own settings screen usually already stores these; in that case skip this class
 * and map your own model to [EngineSettings] instead. It exists so a new app gets a working theme
 * picker without writing persistence for it first.
 */
class EngineSettingsStore(private val context: Context) {

  val settings: Flow<EngineSettings> = context.fluidEngineStore.data.map { it.toSettings() }

  suspend fun current(): EngineSettings = settings.first()

  suspend fun update(transform: (EngineSettings) -> EngineSettings) {
    context.fluidEngineStore.edit { preferences ->
      val next = transform(preferences.toSettings())
      preferences[Keys.ThemeMode] = next.themeMode.name
      preferences[Keys.AccentMode] = next.accentMode.name
      preferences[Keys.CustomAccentName] = next.customAccentName
      preferences[Keys.DynamicColorEnabled] = next.dynamicColorEnabled
      preferences[Keys.AmoledEnabled] = next.amoledEnabled
      preferences[Keys.HapticsEnabled] = next.hapticsEnabled
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
    )
  }

  private object Keys {
    val ThemeMode = stringPreferencesKey("theme_mode")
    val AccentMode = stringPreferencesKey("accent_mode")
    val CustomAccentName = stringPreferencesKey("custom_accent_name")
    val DynamicColorEnabled = booleanPreferencesKey("dynamic_color_enabled")
    val AmoledEnabled = booleanPreferencesKey("amoled_enabled")
    val HapticsEnabled = booleanPreferencesKey("haptics_enabled")
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
