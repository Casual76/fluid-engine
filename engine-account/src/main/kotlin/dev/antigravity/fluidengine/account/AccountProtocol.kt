package dev.antigravity.fluidengine.account

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/**
 * Il contratto sul telefono fra le app e Pampa Store (`b\api.md` § 5): un `ContentProvider` dello Store, chiamato con
 * `ContentResolver.call(method, null, extras)` e un JSON in `extras["json"]`, come il ponte dei tool. Ogni risposta
 * porta `protocol` e `capabilities`. Lo Store tiene il protocollo `1` per sempre: le novità arrivano come capacità.
 */
object AccountProtocol {
  const val PROTOCOL = 1

  /** Il meta-data sul `<provider>` dello Store: `dev.pampa.account.HOST = 1`. */
  const val META_HOST = "dev.pampa.account.HOST"

  /** L'autorità è `<applicationId dello Store>.account`. */
  const val AUTHORITY_SUFFIX = ".account"

  const val KEY_JSON = "json"

  /** Il broadcast esplicito dello Store a ogni app Pampa quando cambia qualcosa (§ 5.4). Dentro solo `what`. */
  const val ACTION_CHANGED = "dev.pampa.account.CHANGED"
  const val EXTRA_WHAT = "what"

  /** L'azione con cui un'app apre lo Store sulla pagina giusta (`open`). */
  const val ACTION_OPEN = "dev.pampa.account.OPEN"
  const val EXTRA_SECTION = "section"

  /** F10.8 · Nella richiesta dei luoghi ([AccountSections.PLACES_REQUEST]): l'app vuole anche aggiungerne. */
  const val EXTRA_PLACES_WRITE = "placesWrite"

  /** F10.8 · Il meta-data di un `<activity-alias>` dell'app: la famiglia d'icone che porta (`default`, `tinted`, …). */
  const val META_ICON_SET = "dev.pampa.account.ICON_SET"

  const val METHOD_NEGOTIATE = "negotiate"
  const val METHOD_STATE = "state"
  const val METHOD_TOKEN = "token"
  const val METHOD_GOOGLE = "google"
  const val METHOD_GRANTS = "grants"
  const val METHOD_APPEARANCE = "appearance"
  const val METHOD_SET_APPEARANCE = "setAppearance"
  const val METHOD_APP_KEY = "appKey"
  const val METHOD_PLACES_LIST = "places.list"
  const val METHOD_PLACES_PUT = "places.put"
  const val METHOD_PLACES_DELETE = "places.delete"
  const val METHOD_BACKUP_AVAILABLE = "backup.available"
  const val METHOD_OPEN = "open"

  /** Le capacità del protocollo 1 in F10.4. `people.*`, `share.*` e `devices.send` arrivano con F10.6. */
  val CAPABILITIES = listOf("state", "token", "google", "appearance", "appKey", "places", "backup", "open")

  /**
   * F10.6 · Le persone (`bpi.md` § 5.3): la fila delle persone, condividere, ricevere, rispondere e «I tuoi
   * dispositivi». Le annuncia solo uno Store che ha `engine-people`; uno Store più vecchio non le ha, e l'app non mostra
   * la fila (P1) invece di fallire.
   */
  const val METHOD_PEOPLE_ROW = "people.row"
  const val METHOD_SHARE_SEND = "share.send"
  const val METHOD_SHARE_RECEIVED = "share.received"
  const val METHOD_SHARE_REPLY = "share.reply"
  const val METHOD_SHARE_HIDE = "share.hide"
  const val METHOD_DEVICES_SEND = "devices.send"

  /** Le capacità che uno Store con `engine-people` aggiunge a [CAPABILITIES]. */
  val PEOPLE_CAPABILITIES = listOf("people", "share", "devices")

  /** Cosa può cambiare (il contenuto di `what`). */
  object What {
    const val STATE = "state"
    const val APPEARANCE = "appearance"
    const val GRANTS = "grants"
    const val PLACES = "places"
    const val KEYS = "keys"
    const val WIPED = "wiped"
    /** F10.6 · È arrivato qualcosa per quest'app («Condivisi con te»): l'app rilegge `share.received`. */
    const val SHARE = "share"
    /** F10.6 · È cambiata la fila delle persone (un contatto nuovo, un nome, un blocco). */
    const val PEOPLE = "people"
  }

  /** Il JSON di una richiesta o di una risposta, o `null` se non è un oggetto JSON. */
  fun parse(text: String?): JsonObject? = text?.let { runCatching { Json.parseToJsonElement(it) as JsonObject }.getOrNull() }
}

/** Quello che vede un'app dell'account (`b\architettura.md` § 2.4 e § 9.4). */
sealed interface AccountState {
  /** Lo Store non c'è, oppure c'è un pacchetto con quel nome ma firmato da altri (M1). */
  data object NoStore : AccountState
  /** Lo Store non ha la capacità che serve: «Aggiorna Pampa Store per usare l'account». */
  data class StoreTooOld(val needed: String) : AccountState
  /** Lo Store c'è, nessuno è entrato: «Accedi con Pampa Store». */
  data object SignedOut : AccountState
  data class SignedIn(val profile: AccountProfile) : AccountState
}

/** Chi sei, come lo dice lo Store. Niente email, niente dati che il server non ha (R3). */
data class AccountProfile(
  val id: String,
  val displayName: String,
  val initials: String,
  /** C'è il codice di recupero (o una porta fuori dal telefono)? Serve alla riga dell'account per un avviso gentile. */
  val recovery: Boolean,
  /** La sessione è nata dal codice di recupero e non è ancora normale (S-rec). */
  val recoverySession: Boolean = false,
)

/** La risposta a `token` (`b\api.md` § 5.3). Il token vale per un'app, un servizio e 15 minuti, e sta solo in memoria. */
sealed interface TokenResult {
  data class Ok(val token: String, val expiresAtElapsed: Long) : TokenResult {
    override fun toString(): String = "Ok(…, $expiresAtElapsed)"
  }
  data object SignedOut : TokenResult
  data object NotAllowed : TokenResult
  data object Disconnected : TokenResult
  data object Offline : TokenResult
  data class Unavailable(val reason: String) : TokenResult
}

/** Le chiavi di un'app: per la sincronizzazione dei suoi dati ([APP]) o per il suo backup ([BACKUP]). */
enum class KeyPurpose(val wire: String) { APP("app"), BACKUP("backup") }

/** La risposta a `appKey`: le chiavi di quest'app per ogni epoca, mai quelle di un'altra (K3). */
sealed interface AppKeyResult {
  class Ok(val current: Int, val keys: Map<Int, ByteArray>) : AppKeyResult {
    /** La chiave con cui si scrive oggi. */
    val currentKey: ByteArray get() = keys.getValue(current)
    override fun toString(): String = "Ok(current=$current, epochs=${keys.keys})"
  }
  /** La cifratura non è partita: l'app lavora in locale (R1). */
  data object NoE2e : AppKeyResult
  /** Il telefono non è stato sbloccato dall'accensione, o serve la chiave madre: si riprova dopo lo sblocco. */
  data object Locked : AppKeyResult
  /** Lo Store deve aprire una porta: l'app propone di aprire lo Store. */
  data object NeedsDoor : AppKeyResult
  data object NotAllowed : AppKeyResult
  data object SignedOut : AppKeyResult
  data object NoStore : AppKeyResult
}

/** Un luogo comune (`b\chiavi.md` § 8): nome, tipo, coordinate e indirizzo scritto dall'utente. */
data class Place(
  val id: String,
  val name: String,
  val kind: String,
  val latitude: Double,
  val longitude: Double,
  val address: String? = null,
) {
  companion object {
    const val HOME = "home"
    const val SCHOOL = "school"
    const val WORK = "work"
    const val OTHER = "other"
  }
}

/** La risposta a `places.list`: i luoghi, oppure il permesso da chiedere allo Store. */
sealed interface PlacesResult {
  data class Ok(val places: List<Place>, val canWrite: Boolean) : PlacesResult
  /**
   * L'utente non ha dato il permesso: non gliel'hanno ancora chiesto, l'ha negato, o l'app non può averli (MyCloud
   * Gallery). Quale dei tre lo dice `PampaAccount.placesAccess`; la richiesta si apre con
   * `PampaAccount.placesRequestIntent`.
   */
  data object NotAllowed : PlacesResult
  data object NoStore : PlacesResult
}

/**
 * F10.8 · Il permesso dei luoghi per quest'app (scelta B8): ogni app lo chiede la prima volta, e l'utente decide app per
 * app. Si cambia dalla pagina dell'account nello Store.
 */
enum class PlacesAccess(val wire: String) {
  /** Mai chiesto: l'app può aprire la richiesta dello Store, una volta. */
  NOT_ASKED("not_asked"),
  /** Negato: l'app non richiede da sola; l'utente lo cambia dallo Store. */
  DENIED("denied"),
  READ("read"),
  READ_WRITE("read_write"),
  /** Quest'app non riceve mai i luoghi (MyCloud Gallery: la sua «casa» resta sua), o non è nella tabella. */
  NEVER("never"),
  NO_STORE("no_store"),
  ;

  companion object {
    fun of(wire: String?): PlacesAccess? = entries.firstOrNull { it.wire == wire }
  }
}

/** Un backup di quest'app sul server, per proporre il ripristino al primo avvio («Ripristinare i dati di ieri?»). */
data class BackupInfo(val n: Long, val createdAt: Long, val size: Long, val epoch: Int)

/** L'aspetto comune scelto nello Store, e l'eccezione di quest'app se c'è (`b\architettura.md` § 2.5). */
data class SharedAppearance(
  val version: Long,
  /** Le chiavi del contratto (`prefs.json`): `appearance.mode`, `appearance.accent`, `appearance.glass`, … */
  val global: Map<String, String>,
  val override: Map<String, String>?,
  val followGlobal: Boolean,
) {
  /** Il valore che vale per quest'app: prima l'eccezione (se l'app non segue il comune), poi il comune. */
  fun effective(key: String): String? = if (!followGlobal) override?.get(key) ?: global[key] else global[key]

  companion object {
    val EMPTY = SharedAppearance(0, emptyMap(), null, followGlobal = true)
  }
}

/** Le pagine dello Store che un'app può aprire (`open`). */
object AccountSections {
  const val ACCOUNT = "account"
  const val SIGN_IN = "signin"
  const val GOOGLE = "google"
  const val DEVICES = "devices"
  const val APPEARANCE = "appearance"
  const val PLACES = "places"
  /**
   * F10.8 · La richiesta dei luoghi di un'app («Fluid Weather vuole usare i tuoi luoghi. Consenti?»). Si apre **con
   * `startActivityForResult`** (`ActivityResultContracts.StartActivityForResult`): solo così lo Store sa dal sistema chi
   * chiede (`callingPackage`). Aperta in un altro modo, lo Store non chiede niente.
   */
  const val PLACES_REQUEST = "places.request"
}

/** I pezzi del protocollo scritti in JSON, uguali per lo Store e per le app. */
object AccountJson {
  /** Un luogo come viaggia nel provider: `{ id, name, kind, lat, lon, address? }`. */
  fun placeJson(p: Place): JsonObject = buildJsonObject {
    put("id", p.id)
    put("name", p.name)
    put("kind", p.kind)
    put("lat", p.latitude)
    put("lon", p.longitude)
    if (p.address != null) put("address", p.address)
  }

  fun place(o: JsonObject): Place = Place(
    id = (o["id"] as JsonPrimitive).content,
    name = (o["name"] as JsonPrimitive).content,
    kind = (o["kind"] as? JsonPrimitive)?.content ?: Place.OTHER,
    latitude = (o["lat"] as JsonPrimitive).doubleOrNull ?: 0.0,
    longitude = (o["lon"] as JsonPrimitive).doubleOrNull ?: 0.0,
    address = (o["address"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content,
  )
}
