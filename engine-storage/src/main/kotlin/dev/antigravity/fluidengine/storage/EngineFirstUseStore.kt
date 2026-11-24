package dev.antigravity.fluidengine.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.antigravity.fluidengine.foundation.FluidFirstUseMemory
import kotlinx.coroutines.flow.first

/**
 * La memoria della prima volta (F8.11) nel file dell'engine: quale versione ha già mostrato il benvenuto o le novità, quali
 * permessi sono già stati chiesti e quali spiegazioni date.
 *
 * Sta nello stesso `DataStore` delle impostazioni dell'engine (`fluid_engine`), con le chiavi sotto `first_use.`: è
 * l'engine che le scrive e le legge, e un'app che cancella le sue preferenze non rivede per questo il benvenuto. Android
 * non dice se un permesso è «mai chiesto» o «negato per sempre»: lo dice questa memoria. Si passa a `engine-ui` con
 * `LocalFluidFirstUseMemory` alla radice dell'app.
 */
class EngineFirstUseStore internal constructor(private val store: DataStore<Preferences>) : FluidFirstUseMemory {

  constructor(context: Context) : this(context.applicationContext.fluidEngineStore)

  override suspend fun read(key: String): String? = store.data.first()[keyOf(key)]

  override suspend fun write(key: String, value: String?) {
    store.edit { preferences ->
      if (value == null) preferences.remove(keyOf(key)) else preferences[keyOf(key)] = value
    }
  }

  private fun keyOf(key: String) = stringPreferencesKey("first_use.$key")
}
