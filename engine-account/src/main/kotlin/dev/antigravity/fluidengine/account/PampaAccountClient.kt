package dev.antigravity.fluidengine.account

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import dev.antigravity.fluidengine.account.crypto.PampaBase64
import java.util.TreeMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import dev.antigravity.fluidengine.storage.EngineAppearanceLayer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Il risultato di una chiamata al provider dello Store. */
sealed interface CallResult {
  data class Ok(val json: JsonObject) : CallResult
  /** Lo Store ha rifiutato chi chiama (firma diversa): `SecurityException`. */
  data object Denied : CallResult
  /** Il provider non c'è più (lo Store è stato tolto nel frattempo). */
  data object Missing : CallResult
  data class Failed(val message: String) : CallResult
}

/** La porta verso il provider dello Store: `ContentResolver.call`, sostituibile nelle prove. */
fun interface AccountTransport {
  fun call(authority: String, method: String, request: JsonObject): CallResult

  companion object {
    /** Il trasporto vero, come il cliente del ponte dei tool (`AiToolClient`). */
    fun system(context: Context): AccountTransport = AccountTransport { authority, method, request ->
      val client = runCatching { context.contentResolver.acquireUnstableContentProviderClient(authority) }.getOrNull()
        ?: return@AccountTransport CallResult.Missing
      try {
        val extras = Bundle().apply { putString(AccountProtocol.KEY_JSON, request.toString()) }
        val bundle = client.call(method, null, extras) ?: return@AccountTransport CallResult.Failed("nessuna risposta")
        AccountProtocol.parse(bundle.getString(AccountProtocol.KEY_JSON))?.let { CallResult.Ok(it) } ?: CallResult.Failed("risposta illeggibile")
      } catch (_: SecurityException) {
        CallResult.Denied
      } catch (e: Exception) {
        CallResult.Failed(e.message ?: e.javaClass.simpleName)
      } finally {
        client.close()
      }
    }
  }
}

/** La copia locale dello stato e dell'aspetto, per partire offline e senza aspettare lo Store (`b\architettura.md` § 9.4). */
interface AccountCache {
  suspend fun read(key: String): String?
  suspend fun write(key: String, value: String?)
}

/** La risposta a `google` (le concessioni Google, scelta N6: si accendono al primo uso vero, non in F10.4). */
sealed interface GoogleTokenResult {
  data class Ok(val accessToken: String, val expiresAtElapsed: Long) : GoogleTokenResult {
    override fun toString(): String = "Ok(…, $expiresAtElapsed)"
  }
  data object NotGranted : GoogleTokenResult
  data object NeedsConsent : GoogleTokenResult
  data object NoPlayServices : GoogleTokenResult
  data object Offline : GoogleTokenResult
  data object SignedOut : GoogleTokenResult
  data object NoStore : GoogleTokenResult
}

/**
 * Il cliente dell'account dentro un'app: trova lo Store ([StoreLocator]), negozia, e chiede. **Non usa mai la rete**
 * (R9): tutto passa dal provider dello Store sul telefono. Non si fida del contenuto dei broadcast: a ogni avviso
 * rilegge dal provider (`b\api.md` § 5.4).
 *
 * Di solito si usa da [PampaAccount]; questa classe c'è per le prove e per chi vuole due configurazioni.
 */
class PampaAccountClient(
  private val locate: () -> StoreHost?,
  private val transport: AccountTransport,
  private val cache: AccountCache? = null,
  private val clientVersion: String = "engine-account/4.0.0",
  private val elapsed: () -> Long = { SystemClock.elapsedRealtime() },
) {
  private val _state = MutableStateFlow<AccountState>(AccountState.NoStore)
  private val _appearance = MutableStateFlow(SharedAppearance.EMPTY)
  private val _grants = MutableStateFlow<Set<String>>(emptySet())
  /** F10.8 · Vero dopo la copia locale o la prima lettura: prima, l'aspetto comune non si sa ancora. */
  private val _ready = MutableStateFlow(false)
  /** F10.8 · Cresce a ogni avviso che può cambiare i luoghi (`places`, `state`, `wiped`): [placesFlow] rilegge. */
  private val placesVersion = MutableStateFlow(0L)
  private val wipeHandlers = CopyOnWriteArrayList<suspend () -> Unit>()
  /** F10.6 · Chi ascolta gli avvisi `CHANGED` oltre allo stato (le persone: `share`, `people`). */
  private val changeListeners = CopyOnWriteArrayList<suspend (List<String>) -> Unit>()

  /** F10.6 · Un lavoro da fare a ogni avviso `CHANGED`, con i suoi `what` (lo usa [PampaPeople] per `share`). */
  fun addChangeListener(listener: suspend (List<String>) -> Unit) {
    changeListeners += listener
  }

  @Volatile private var wipedHandled: Long = 0
  @Volatile private var host: StoreHost? = null
  @Volatile private var capabilities: Set<String> = emptySet()

  val state: StateFlow<AccountState> = _state.asStateFlow()
  val appearance: StateFlow<SharedAppearance> = _appearance.asStateFlow()
  val grants: StateFlow<Set<String>> = _grants.asStateFlow()

  /**
   * F10.8 · Le scelte comuni per quest'app, eccezione compresa ([PampaAppearance.layerOf]); `null` senza Store (R1).
   * Il primo valore arriva dopo la copia locale ([restoreCache]) o la prima lettura, così il tema non lampeggia su
   * quello dell'app prima di passare al comune.
   */
  val sharedLayer: Flow<EngineAppearanceLayer?> = combine(_ready, _state, _appearance) { ready, s, a -> Triple(ready, s, a) }
    .filter { it.first }
    .map { (_, s, a) -> if (s is AccountState.NoStore) null else PampaAppearance.layerOf(a) }
    .distinctUntilChanged()

  /** F10.8 · I luoghi, riletti a ogni avviso dello Store che li può cambiare (un permesso dato o tolto, un luogo nuovo). */
  val placesFlow: Flow<PlacesResult> = placesVersion.map { places() }.distinctUntilChanged()

  /** Lo Store trovato all'ultima lettura, per aprirlo ([storeIntent]). */
  val store: StoreHost? get() = host

  /** Rimette la copia locale (all'avvio, prima che lo Store risponda). */
  suspend fun restoreCache() {
    val c = cache ?: return
    c.read(CACHE_STATE)?.let(AccountProtocol::parse)?.let { _state.value = stateFrom(it) }
    c.read(CACHE_APPEARANCE)?.let(AccountProtocol::parse)?.let { _appearance.value = appearanceFrom(it) }
    _ready.value = true
  }

  /**
   * Rilegge tutto dallo Store: lo trova, controlla la firma, negozia, legge stato e aspetto. Lo chiama [PampaAccount]
   * all'avvio, quando l'app torna in primo piano, e a ogni avviso `CHANGED`.
   */
  suspend fun refresh(): AccountState = withContext(Dispatchers.IO) {
    val found = locate()
    host = found
    if (found == null) {
      capabilities = emptySet()
      return@withContext publish(AccountState.NoStore)
    }
    val negotiated = call(AccountProtocol.METHOD_NEGOTIATE, buildJsonObject {
      put("protocol", AccountProtocol.PROTOCOL)
      put("client", clientVersion)
    }) ?: return@withContext publish(AccountState.NoStore)
    capabilities = (negotiated["capabilities"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }?.toSet().orEmpty()
    if ("state" !in capabilities) return@withContext publish(AccountState.StoreTooOld("state"))
    if ("appearance" in capabilities) call(AccountProtocol.METHOD_APPEARANCE)?.let { a ->
      _appearance.value = appearanceFrom(a)
      cache?.write(CACHE_APPEARANCE, a.toString())
    }
    val s = call(AccountProtocol.METHOD_STATE) ?: return@withContext _state.value.also { _ready.value = true }
    cache?.write(CACHE_STATE, s.toString())
    publish(stateFrom(s))
  }

  private fun publish(state: AccountState): AccountState {
    _state.value = state
    _ready.value = true
    return state
  }

  /** Un avviso `CHANGED` dello Store: non ci si fida del contenuto, si rilegge. `wiped` si conferma con lo Store. */
  suspend fun onChanged(what: List<String>) {
    refresh()
    if (AccountProtocol.What.GRANTS in what) refreshGrants()
    if (AccountProtocol.What.WIPED in what) confirmWiped()
    if (what.isEmpty() || what.any { it == AccountProtocol.What.PLACES || it == AccountProtocol.What.STATE || it == AccountProtocol.What.WIPED }) {
      placesVersion.value = placesVersion.value + 1
    }
    for (l in changeListeners) runCatching { l(what) }
  }

  /** Le capacità che lo Store ha annunciato all'ultima lettura. */
  val storeCapabilities: Set<String> get() = capabilities

  /**
   * F10.6 · Una chiamata al provider dello Store per i metodi delle persone (`PampaPeople`): `null` senza Store o se lo
   * Store non risponde. Come ogni chiamata, passa dal controllo della firma nei due sensi.
   */
  internal suspend fun callPeople(method: String, request: JsonObject): JsonObject? = withContext(Dispatchers.IO) {
    if (host == null && refresh() is AccountState.NoStore) return@withContext null
    call(method, request)
  }

  /**
   * `wiped` arriva dopo un'uscita **da remoto** (`b\chiavi.md` § 4.4): l'app cancella le copie sincronizzate e le sue
   * chiavi. Un broadcast falso potrebbe chiederlo, quindi si cancella solo se lo Store conferma: non c'è più
   * nessuno dentro e c'è un'uscita da remoto più nuova dell'ultima già gestita.
   */
  private suspend fun confirmWiped() {
    val s = call(AccountProtocol.METHOD_STATE) ?: return
    val signedIn = (s["signedIn"] as? JsonPrimitive)?.booleanOrNull == true
    val at = (s["remoteExitAt"] as? JsonPrimitive)?.longOrNull ?: return
    val handled = maxOf(wipedHandled, cache?.read(CACHE_WIPED)?.toLongOrNull() ?: 0L)
    if (signedIn || at <= handled) return
    wipedHandled = at
    cache?.write(CACHE_WIPED, at.toString())
    for (h in wipeHandlers) runCatching { h() }
  }

  /** Un lavoro da fare quando lo Store conferma l'uscita da remoto: di solito cancellare le copie di `engine-sync`. */
  fun onWiped(handler: suspend () -> Unit) {
    wipeHandlers += handler
  }

  /** Un token per il servizio di quest'app (`audience`) con gli ambiti dati. Sta solo in memoria. */
  suspend fun token(audience: String, scopes: Set<String>): TokenResult = withContext(Dispatchers.IO) {
    if (host == null && refresh() is AccountState.NoStore) return@withContext TokenResult.Unavailable("no_store")
    val r = call(AccountProtocol.METHOD_TOKEN, buildJsonObject {
      put("audience", audience)
      putJsonArray("scopes") { scopes.sorted().forEach { add(JsonPrimitive(it)) } }
    }) ?: return@withContext TokenResult.Unavailable("no_store")
    if ((r["ok"] as? JsonPrimitive)?.booleanOrNull == true) {
      val expires = (r["expiresIn"] as? JsonPrimitive)?.longOrNull ?: 0
      return@withContext TokenResult.Ok((r["token"] as JsonPrimitive).content, elapsed() + expires * 1000)
    }
    when ((r["reason"] as? JsonPrimitive)?.content) {
      "signed_out" -> TokenResult.SignedOut
      "not_allowed" -> TokenResult.NotAllowed
      "disconnected" -> TokenResult.Disconnected
      "offline" -> TokenResult.Offline
      else -> TokenResult.Unavailable((r["reason"] as? JsonPrimitive)?.content ?: "unavailable")
    }
  }

  /** Le chiavi di quest'app per [purpose], una per epoca (per `engine-sync` e per il backup). Mai di un'altra app. */
  suspend fun appKey(purpose: KeyPurpose): AppKeyResult = withContext(Dispatchers.IO) {
    if (host == null && refresh() is AccountState.NoStore) return@withContext AppKeyResult.NoStore
    if ("appKey" !in capabilities) return@withContext AppKeyResult.NoStore
    val r = call(AccountProtocol.METHOD_APP_KEY, buildJsonObject { put("purpose", purpose.wire) }) ?: return@withContext AppKeyResult.NoStore
    if ((r["ok"] as? JsonPrimitive)?.booleanOrNull == true) {
      val keys = TreeMap<Int, ByteArray>()
      for (k in (r["keys"] as JsonArray)) {
        val o = k as JsonObject
        keys[(o["epoch"] as JsonPrimitive).intOrNull!!] = PampaBase64.decode((o["key"] as JsonPrimitive).content)
      }
      return@withContext AppKeyResult.Ok((r["current"] as JsonPrimitive).intOrNull!!, keys)
    }
    when ((r["reason"] as? JsonPrimitive)?.content) {
      "no_e2e" -> AppKeyResult.NoE2e
      "locked" -> AppKeyResult.Locked
      "needs_door" -> AppKeyResult.NeedsDoor
      "signed_out" -> AppKeyResult.SignedOut
      else -> AppKeyResult.NotAllowed
    }
  }

  /** I luoghi comuni, se l'utente ha dato il permesso a quest'app. Funziona anche senza account. */
  suspend fun places(): PlacesResult = withContext(Dispatchers.IO) {
    if (host == null && refresh() is AccountState.NoStore) return@withContext PlacesResult.NoStore
    val r = call(AccountProtocol.METHOD_PLACES_LIST) ?: return@withContext PlacesResult.NoStore
    if ((r["ok"] as? JsonPrimitive)?.booleanOrNull != true) return@withContext PlacesResult.NotAllowed
    PlacesResult.Ok((r["places"] as JsonArray).map { AccountJson.place(it as JsonObject) }, (r["canWrite"] as? JsonPrimitive)?.booleanOrNull == true)
  }

  /**
   * F10.8 · Il permesso dei luoghi di quest'app (scelta B8): [PlacesAccess.NOT_ASKED] vuol dire che l'app può aprire la
   * richiesta dello Store ([placesRequestIntent]); dopo un «no» è [PlacesAccess.DENIED] e l'app non richiede da sola.
   * Uno Store di F10.4, che non dice il motivo, risponde [PlacesAccess.NOT_ASKED] finché non dà i luoghi.
   */
  suspend fun placesAccess(): PlacesAccess = withContext(Dispatchers.IO) {
    if (host == null && refresh() is AccountState.NoStore) return@withContext PlacesAccess.NO_STORE
    val r = call(AccountProtocol.METHOD_PLACES_LIST) ?: return@withContext PlacesAccess.NO_STORE
    if ((r["ok"] as? JsonPrimitive)?.booleanOrNull == true) {
      return@withContext if ((r["canWrite"] as? JsonPrimitive)?.booleanOrNull == true) PlacesAccess.READ_WRITE else PlacesAccess.READ
    }
    PlacesAccess.of((r["access"] as? JsonPrimitive)?.content) ?: PlacesAccess.NOT_ASKED
  }

  /**
   * F10.8 · L'intent della richiesta dei luoghi, da lanciare **con `startActivityForResult`** (lo Store legge dal sistema
   * chi chiede). [write]: l'app vuole anche aggiungere luoghi. `null` senza Store. Alla risposta l'app rilegge
   * [placesAccess]: lo Store non mette niente nel risultato, così nessuno deve fidarsi di un risultato.
   */
  fun placesRequestIntent(write: Boolean = false): Intent? = storeIntent(AccountSections.PLACES_REQUEST)?.apply {
    putExtra(AccountProtocol.EXTRA_PLACES_WRITE, write)
    // Il risultato torna a chi chiama: con NEW_TASK si perderebbe, e con lui il pacchetto di chi chiede.
    flags = flags and Intent.FLAG_ACTIVITY_NEW_TASK.inv()
  }

  suspend fun putPlace(place: Place): PlacesResult = withContext(Dispatchers.IO) {
    val r = call(AccountProtocol.METHOD_PLACES_PUT, buildJsonObject { put("place", AccountJson.placeJson(place)) }) ?: return@withContext PlacesResult.NoStore
    if ((r["ok"] as? JsonPrimitive)?.booleanOrNull != true) PlacesResult.NotAllowed else places()
  }

  suspend fun deletePlace(id: String): PlacesResult = withContext(Dispatchers.IO) {
    val r = call(AccountProtocol.METHOD_PLACES_DELETE, buildJsonObject { put("id", id) }) ?: return@withContext PlacesResult.NoStore
    if ((r["ok"] as? JsonPrimitive)?.booleanOrNull != true) PlacesResult.NotAllowed else places()
  }

  /** L'eccezione di quest'app all'aspetto comune, dalla sua sezione Aspetto. Scrive solo lo Store. */
  suspend fun setAppearanceException(followGlobal: Boolean, override: Map<String, String>?) = withContext(Dispatchers.IO) {
    call(AccountProtocol.METHOD_SET_APPEARANCE, buildJsonObject {
      put("followGlobal", followGlobal)
      if (override != null) putJsonObject("override") { for ((k, v) in override) put(k, v) } else put("override", JsonNull)
    })?.let { _appearance.value = appearanceFrom(it) }
  }

  /** I backup di quest'app, per proporre il ripristino al primo avvio. Lista vuota se non ce ne sono o se non si sa. */
  suspend fun availableBackups(): List<BackupInfo> = withContext(Dispatchers.IO) {
    if (host == null && refresh() is AccountState.NoStore) return@withContext emptyList()
    val r = call(AccountProtocol.METHOD_BACKUP_AVAILABLE) ?: return@withContext emptyList()
    (r["items"] as? JsonArray)?.map {
      val o = it as JsonObject
      BackupInfo(o.long("n"), o.long("createdAt"), o.long("size"), (o["epoch"] as? JsonPrimitive)?.intOrNull ?: 0)
    }.orEmpty()
  }

  /** Un token Google per [service], col consenso dato nello Store (N6: in F10.4 lo Store risponde sempre «non concesso»). */
  suspend fun google(service: String): GoogleTokenResult = withContext(Dispatchers.IO) {
    if (host == null && refresh() is AccountState.NoStore) return@withContext GoogleTokenResult.NoStore
    val r = call(AccountProtocol.METHOD_GOOGLE, buildJsonObject { put("service", service) }) ?: return@withContext GoogleTokenResult.NoStore
    if ((r["ok"] as? JsonPrimitive)?.booleanOrNull == true) {
      return@withContext GoogleTokenResult.Ok((r["accessToken"] as JsonPrimitive).content, elapsed() + ((r["expiresIn"] as? JsonPrimitive)?.longOrNull ?: 0) * 1000)
    }
    when ((r["reason"] as? JsonPrimitive)?.content) {
      "needs_consent" -> GoogleTokenResult.NeedsConsent
      "no_play_services" -> GoogleTokenResult.NoPlayServices
      "offline" -> GoogleTokenResult.Offline
      "signed_out" -> GoogleTokenResult.SignedOut
      else -> GoogleTokenResult.NotGranted
    }
  }

  /** Le concessioni Google di quest'app. */
  suspend fun refreshGrants(): Set<String> = withContext(Dispatchers.IO) {
    val r = call(AccountProtocol.METHOD_GRANTS) ?: return@withContext emptySet()
    val set = (r["grants"] as? JsonArray)?.mapNotNull { g -> (g as? JsonObject)?.takeIf { (it["state"] as? JsonPrimitive)?.content == "granted" }?.let { (it["service"] as JsonPrimitive).content } }?.toSet().orEmpty()
    _grants.value = set
    set
  }

  /** L'intent che apre lo Store su [section]; `null` senza Store. L'app lo lancia con `startActivity`. */
  fun storeIntent(section: String): Intent? {
    val h = host ?: return null
    return Intent(AccountProtocol.ACTION_OPEN).setPackage(h.packageName).putExtra(AccountProtocol.EXTRA_SECTION, section)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  }

  private fun call(method: String, request: JsonObject = JsonObject(emptyMap())): JsonObject? {
    val h = host ?: return null
    return when (val r = transport.call(h.authority, method, request)) {
      is CallResult.Ok -> r.json
      // Uno Store che ci rifiuta ha un'altra firma: per noi non c'è (M1).
      CallResult.Denied, CallResult.Missing -> {
        host = null
        _state.value = AccountState.NoStore
        null
      }
      is CallResult.Failed -> null
    }
  }

  companion object {
    private const val CACHE_STATE = "state"
    private const val CACHE_APPEARANCE = "appearance"
    private const val CACHE_WIPED = "wipedHandledAt"

    internal fun stateFrom(s: JsonObject): AccountState {
      if ((s["signedIn"] as? JsonPrimitive)?.booleanOrNull != true) return AccountState.SignedOut
      fun str(k: String) = (s[k] as? JsonPrimitive)?.content
      val name = str("displayName") ?: ""
      return AccountState.SignedIn(
        AccountProfile(
          id = str("accountId") ?: "",
          displayName = name,
          initials = str("initials") ?: initialsOf(name),
          recovery = (s["recovery"] as? JsonPrimitive)?.booleanOrNull == true,
          recoverySession = str("sessionKind") == "recovery",
        ),
      )
    }

    internal fun appearanceFrom(a: JsonObject): SharedAppearance = SharedAppearance(
      version = (a["version"] as? JsonPrimitive)?.longOrNull ?: 0,
      global = (a["global"] as? JsonObject)?.flat().orEmpty(),
      override = (a["override"] as? JsonObject)?.flat(),
      followGlobal = (a["followGlobal"] as? JsonPrimitive)?.booleanOrNull ?: true,
    )

    private fun JsonObject.flat(): Map<String, String> = mapNotNull { (k, v) -> (v as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.let { k to it } }.toMap()

    /** Le iniziali di un nome: «Alessio Pampaloni» → «AP», «alessio» → «A». */
    fun initialsOf(name: String): String = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
      .joinToString("") { it.substring(0, it.offsetByCodePoints(0, 1)).uppercase() }

    private fun JsonObject.long(k: String) = (this[k] as? JsonPrimitive)?.longOrNull ?: 0L
  }
}
