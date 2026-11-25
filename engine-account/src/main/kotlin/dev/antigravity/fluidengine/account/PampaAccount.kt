package dev.antigravity.fluidengine.account

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * L'Account Pampa visto da un'app (`b\architettura.md` § 9.4). Tutto passa da Pampa Store sul telefono: **mai la
 * rete** (R9). Senza Store, o con uno Store firmato da altri, lo stato è [AccountState.NoStore] e l'app va avanti come
 * oggi (R1); la riga «Account Pampa» di `engine-account-ui` lo dice da sé.
 *
 * ```kotlin
 * val state by PampaAccount.state(context).collectAsState()
 * when (val t = PampaAccount.token(context, "notes", setOf("notes.sync"))) { is TokenResult.Ok -> … }
 * val keys = PampaAccount.appKey(context, KeyPurpose.APP)   // per engine-sync
 * ```
 *
 * Dietro le quinte: trova lo Store e ne controlla la firma (M1), sceglie fra quello vero e quello di sviluppo (M9),
 * negozia, tiene una copia di stato e aspetto per partire offline, ascolta `CHANGED` e rilegge, e a `wiped` (uscita
 * da remoto) chiama i lavori registrati con [onWiped] dopo averlo confermato con lo Store.
 */
object PampaAccount {
  @Volatile private var config = AccountClientConfig()
  @Volatile private var client: PampaAccountClient? = null
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  /** La configurazione, prima del primo uso: i pacchetti dello Store (R8: chi ricompila mette i suoi). */
  fun configure(config: AccountClientConfig) {
    this.config = config
    client = null
  }

  /** Il cliente di questo processo; alla prima chiamata rimette la copia locale e rilegge dallo Store. */
  fun client(context: Context): PampaAccountClient {
    client?.let { return it }
    synchronized(this) {
      client?.let { return it }
      val app = context.applicationContext
      val cfg = config
      val dev = cfg.devBuild ?: StoreLocator.isDebuggable(app)
      val locator = StoreLocator(cfg, dev, StoreLocator.systemProbe(app))
      val created = PampaAccountClient(locator::locate, AccountTransport.system(app), DataStoreAccountCache.of(app))
      client = created
      scope.launch {
        created.restoreCache()
        created.refresh()
      }
      return created
    }
  }

  /** Lo stato dell'account; si aggiorna da sé agli avvisi dello Store. Chiama [refresh] quando l'app torna in primo piano. */
  fun state(context: Context): StateFlow<AccountState> = client(context).state

  /** Rilegge dallo Store (una chiamata locale, meno di un millisecondo di lavoro nello Store). */
  suspend fun refresh(context: Context): AccountState = client(context).refresh()

  suspend fun token(context: Context, audience: String, scopes: Set<String>): TokenResult = client(context).token(audience, scopes)

  suspend fun google(context: Context, service: String): GoogleTokenResult = client(context).google(service)

  fun grants(context: Context): Flow<Set<String>> = client(context).grants

  /** L'aspetto comune (funziona anche senza account, `b\architettura.md` § 2.5). Lo collega al tema F10.8. */
  fun appearance(context: Context): Flow<SharedAppearance> = client(context).appearance

  suspend fun setAppearanceException(context: Context, followGlobal: Boolean, override: Map<String, String>?) =
    client(context).setAppearanceException(followGlobal, override)

  suspend fun appKey(context: Context, purpose: KeyPurpose): AppKeyResult = client(context).appKey(purpose)

  /** I luoghi comuni, col permesso dell'utente (scelta B8). Si rilegge a ogni avviso `places`. */
  fun places(context: Context): Flow<PlacesResult> = flow { emit(client(context).places()) }

  suspend fun putPlace(context: Context, place: Place): PlacesResult = client(context).putPlace(place)

  suspend fun availableBackups(context: Context): List<BackupInfo> = client(context).availableBackups()

  /** L'intent che apre lo Store sulla pagina [section] (`AccountSections`), o `null` senza Store. */
  fun storeIntent(context: Context, section: String): Intent? = client(context).storeIntent(section)

  /** Un lavoro da fare quando lo Store conferma un'uscita da remoto: cancellare le copie sincronizzate e le chiavi. */
  fun onWiped(context: Context, handler: suspend () -> Unit) = client(context).onWiped(handler)

  internal fun onChanged(context: Context, what: List<String>, done: () -> Unit) {
    val c = client(context)
    scope.launch {
      try {
        c.onChanged(what)
      } finally {
        done()
      }
    }
  }
}

/**
 * L'avviso `dev.pampa.account.CHANGED` dello Store (`b\api.md` § 5.4). Esportato senza permesso di proposito: chiunque
 * può mandarlo, ma l'app non si fida del contenuto e rilegge dal provider. Un avviso falso costa una lettura in più.
 */
class PampaAccountChangedReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != AccountProtocol.ACTION_CHANGED) return
    val what = intent.getStringArrayListExtra(AccountProtocol.EXTRA_WHAT)?.toList()
      ?: intent.getStringExtra(AccountProtocol.EXTRA_WHAT)?.split(',')
      ?: emptyList()
    val pending = goAsync()
    PampaAccount.onChanged(context, what) { pending.finish() }
  }
}

/** La copia locale su DataStore, un file per processo (`pampa_account.preferences_pb`). */
internal class DataStoreAccountCache private constructor(private val store: DataStore<Preferences>) : AccountCache {
  override suspend fun read(key: String): String? = store.data.map { it[stringPreferencesKey(key)] }.first()

  override suspend fun write(key: String, value: String?) {
    store.edit { if (value == null) it.remove(stringPreferencesKey(key)) else it[stringPreferencesKey(key)] = value }
  }

  companion object {
    @Volatile private var instance: DataStoreAccountCache? = null

    fun of(context: Context): DataStoreAccountCache = instance ?: synchronized(this) {
      instance ?: DataStoreAccountCache(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("pampa_account") }).also { instance = it }
    }
  }
}
