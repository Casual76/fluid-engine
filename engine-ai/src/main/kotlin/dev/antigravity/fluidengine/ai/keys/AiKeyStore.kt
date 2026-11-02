package dev.antigravity.fluidengine.ai.keys

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.antigravity.fluidengine.ai.provider.ProviderId
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Cosa si sa di una chiave senza leggerla: c'e', e quando e' stata verificata l'ultima volta. */
data class KeyState(val present: Boolean, val verifiedAtMillis: Long?) {
  val verified: Boolean get() = present && verifiedAtMillis != null
}

private val Context.aiKeysStore: DataStore<Preferences> by preferencesDataStore(
  name = "ai_keys",
  corruptionHandler = AiDataStores.corruptionHandler(),
)

/**
 * Le chiavi dei provider IA, cifrate col Keystore e in un file DataStore tutto loro (come
 * `provider_keys`: l'export dei dati puo' escluderlo e basta). La chiave in chiaro non esce mai
 * da un `Flow`: si legge con [key] nel momento in cui serve, e si dimentica.
 *
 * Se il Keystore non riesce piu' a decifrare (telefono ripristinato, chiave invalidata) la voce
 * si cancella e l'utente vede "reinserisci la chiave": meglio di un errore 401 senza spiegazione.
 *
 * Dalla 3.0.0 anche un **file corrotto** si butta (il costruttore a `Context` e [open] usano
 * [AiDataStores.corruptionHandler]): le chiavi vanno reinserite, ma l'app riparte invece di fallire
 * ogni lettura e ogni scrittura per sempre.
 */
class AiKeyStore(
  private val store: DataStore<Preferences>,
  private val cipher: SecretCipher,
) {

  constructor(context: Context, cipher: SecretCipher = KeystoreCipher()) : this(context.aiKeysStore, cipher)

  companion object {
    /**
     * Le chiavi in un file scelto dall'app (3.0.0), che deve finire in `.preferences_pb`: stesso
     * file di prima per chi l'aveva gia' ([AiDataStores.open]), con la corruzione gestita. Una volta
     * sola per file e per processo.
     */
    fun open(
      file: File,
      cipher: SecretCipher = KeystoreCipher(),
      scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    ): AiKeyStore = AiKeyStore(AiDataStores.open(file, scope), cipher)
  }

  val states: Flow<Map<ProviderId, KeyState>> = store.data.map { preferences ->
    ProviderId.entries.associateWith { provider ->
      KeyState(
        present = !preferences[keyOf(provider)].isNullOrBlank(),
        verifiedAtMillis = preferences[verifiedOf(provider)],
      )
    }
  }

  /** Vero se almeno un provider ha una chiave verificata: la condizione per accendere l'assistente. */
  val anyVerified: Flow<Boolean> = states.map { map -> map.values.any { it.verified } }

  suspend fun currentStates(): Map<ProviderId, KeyState> = states.first()

  /** La chiave in chiaro, adesso. Null se manca o se il Keystore non la legge piu' (e allora la toglie). */
  suspend fun key(provider: ProviderId): String? {
    val blob = store.data.first()[keyOf(provider)] ?: return null
    val plain = cipher.decrypt(blob)
    if (plain == null) {
      set(provider, null)
      return null
    }
    return plain.takeIf { it.isNotBlank() }
  }

  /** Salva (cifrata) o rimuove; una chiave nuova parte non verificata. */
  suspend fun set(provider: ProviderId, key: String?) {
    val trimmed = key?.trim()
    store.edit { preferences ->
      // `-=` e non `remove`: `remove` restituisce il valore tolto, e su una chiave di tipo
      // primitivo mai scritta prima Kotlin lo spacchetta da null. Salvare la PRIMA chiave di un
      // provider faceva cadere l'app proprio li' (visto sul telefono, 1.0.3).
      if (trimmed.isNullOrEmpty()) {
        preferences -= keyOf(provider)
        preferences -= verifiedOf(provider)
      } else {
        preferences[keyOf(provider)] = cipher.encrypt(trimmed)
        preferences -= verifiedOf(provider)
      }
    }
  }

  suspend fun markVerified(provider: ProviderId, atMillis: Long) {
    store.edit { preferences ->
      if (!preferences[keyOf(provider)].isNullOrBlank()) preferences[verifiedOf(provider)] = atMillis
    }
  }

  private fun keyOf(provider: ProviderId) = stringPreferencesKey("key_${provider.id}")
  private fun verifiedOf(provider: ProviderId) = longPreferencesKey("verified_${provider.id}")
}
