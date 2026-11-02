package dev.antigravity.fluidengine.ai.keys

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * I file DataStore dell'assistente (3.0.0). Servono a una cosa: **un file corrotto si butta**.
 *
 * Senza un gestore della corruzione un file illeggibile vuol dire che ogni lettura e ogni scrittura
 * falliscono per sempre — e in silenzio, perche' i chiamanti hanno tutti un `runCatching` — con la
 * cancellazione dei dati dell'app come unico rimedio. Le app se lo erano cucito da sole: aprivano i
 * due file (`ai`, `ai_keys`) con il loro `ReplaceFileCorruptionHandler` e passavano il [DataStore] gia'
 * aperto agli store dell'engine, perche' quelli a `Context` non lo avevano. Adesso ce l'hanno: il
 * costruttore a `Context` di `AiSettingsStore` e di `AiKeyStore` usa [corruptionHandler], e chi
 * vuole un file suo ([AiSettingsStore.open], [AiKeyStore.open], [open]) lo ha uguale.
 *
 * Un file si apre **una volta sola per processo**: due `DataStore` sullo stesso file fanno cadere
 * l'app. Chi gia' apre `ai` e `ai_keys` da se' puo' continuare a farlo (il costruttore che prende il
 * [DataStore] non e' cambiato), ma non deve usare anche quelli a `Context`.
 */
object AiDataStores {

  /** Un file illeggibile riparte da vuoto: le preferenze tornano ai default, le chiavi vanno reinserite. */
  fun corruptionHandler(): ReplaceFileCorruptionHandler<Preferences> = ReplaceFileCorruptionHandler { emptyPreferences() }

  /**
   * Apre [file] (che deve finire in `.preferences_pb`) come [DataStore] di preferenze, col gestore
   * della corruzione. [scope] e' quello in cui gira l'I/O: di serie uno proprio, su `Dispatchers.IO`.
   */
  fun open(
    file: File,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
  ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    corruptionHandler = corruptionHandler(),
    scope = scope,
    produceFile = { file },
  )
}
