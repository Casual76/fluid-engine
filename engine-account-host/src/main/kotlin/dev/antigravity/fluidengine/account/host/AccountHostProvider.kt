package dev.antigravity.fluidengine.account.host

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import dev.antigravity.fluidengine.account.AccountJson
import dev.antigravity.fluidengine.account.AccountPeopleMethods
import dev.antigravity.fluidengine.account.AccountProtocol
import dev.antigravity.fluidengine.account.BackupInfo
import dev.antigravity.fluidengine.account.PampaAccountClient
import dev.antigravity.fluidengine.account.PlacesAccess
import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.host.custody.AppKeyPurpose
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Dove lo Store trova i backup di un'app sul server (pacchetto 5, `BackupScheduler`). `null` se non lo sa (offline). */
fun interface BackupDirectory {
  suspend fun available(packageName: String): List<BackupInfo>?
}

/**
 * Il protocollo del provider dello Store, metodo per metodo (`b\api.md` § 5.3), senza Android: chi chiama è già
 * stato riconosciuto dal sistema ([caller] viene dall'UID del Binder, mai dall'app). Ogni risposta porta `protocol` e
 * `capabilities`.
 *
 * Le regole:
 * - `negotiate`, `state` e `appearance` rispondono a ogni app con la firma dello Store, anche fuori dalla tabella, e
 *   `appearance` anche senza account;
 * - `token`, `appKey` e i luoghi solo a chi è nella tabella ([AppPolicy]) e solo per ciò che è suo (K3, M9);
 * - `google` risponde «non concesso» finché le concessioni non si accendono (scelta N6).
 */
class AccountProtocolHandler(
  private val session: AccountSession,
  private val custody: CustodyService,
  private val policy: AppPolicy,
  private val places: PlacesStore,
  private val appearance: AppearanceStore,
  private val backups: BackupDirectory,
  private val storeVersion: String,
  /** C'è il codice di recupero, o un'altra porta fuori dal telefono? Lo sa lo Store (`GET /v1/account`). */
  private val hasRecovery: () -> Boolean = { false },
  /**
   * F10.6 · I metodi delle persone (`people.row`, `share.*`, `devices.send`), dati da `engine-people`. Senza, lo Store
   * non li annuncia e le app non mostrano la fila (P1). Rispondono solo alle app della tabella.
   */
  private val people: AccountPeopleMethods? = null,
) {
  suspend fun handle(caller: String, method: String, request: JsonObject?): JsonObject {
    val body: JsonObject = when (method) {
      AccountProtocol.METHOD_NEGOTIATE -> buildJsonObject { put("store", storeVersion) }
      AccountProtocol.METHOD_STATE -> state(caller)
      AccountProtocol.METHOD_TOKEN -> token(caller, request)
      AccountProtocol.METHOD_GOOGLE -> buildJsonObject { put("ok", false); put("reason", if (session.state.value is HostSession.SignedOut) "signed_out" else "not_granted") }
      AccountProtocol.METHOD_GRANTS -> buildJsonObject { putJsonArray("grants") {} }
      AccountProtocol.METHOD_APPEARANCE -> appearance.responseFor(caller)
      AccountProtocol.METHOD_SET_APPEARANCE -> setAppearance(caller, request)
      AccountProtocol.METHOD_APP_KEY -> appKey(caller, request)
      AccountProtocol.METHOD_PLACES_LIST -> placesList(caller)
      AccountProtocol.METHOD_PLACES_PUT -> placesWrite(caller) { places.put(caller, AccountJson.place(request!!["place"] as JsonObject)) }
      AccountProtocol.METHOD_PLACES_DELETE -> placesWrite(caller) { places.delete(caller, (request!!["id"] as JsonPrimitive).content) }
      AccountProtocol.METHOD_BACKUP_AVAILABLE -> backupAvailable(caller)
      AccountProtocol.METHOD_OPEN -> buildJsonObject { put("ok", true) }
      in PEOPLE_METHODS -> peopleCall(caller, method, request)
      else -> buildJsonObject { put("error", "metodo sconosciuto: $method") }
    }
    val capabilities = if (people != null) AccountProtocol.CAPABILITIES + AccountProtocol.PEOPLE_CAPABILITIES else AccountProtocol.CAPABILITIES
    return JsonObject(body + mapOf(
      "protocol" to JsonPrimitive(AccountProtocol.PROTOCOL),
      "capabilities" to JsonArray(capabilities.map(::JsonPrimitive)),
    ))
  }

  private fun state(caller: String): JsonObject = when (val s = session.state.value) {
    is HostSession.SignedOut -> buildJsonObject {
      put("signedIn", false)
      if (session.lastRemoteExit > 0) put("remoteExitAt", session.lastRemoteExit)
    }
    is HostSession.SignedIn -> buildJsonObject {
      put("signedIn", true)
      put("accountId", s.accountId)
      put("displayName", s.displayName)
      put("initials", PampaAccountClient.initialsOf(s.displayName))
      put("sessionKind", s.kind.wire)
      put("recovery", hasRecovery())
      put("appConnected", policy.entryFor(caller) != null)
    }
  }

  private suspend fun token(caller: String, request: JsonObject?): JsonObject {
    val audience = (request?.get("audience") as? JsonPrimitive)?.content ?: return fail("not_allowed")
    val scopes = (request["scopes"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }?.toSet().orEmpty()
    return when (val t = session.appToken(caller, audience, scopes)) {
      is TokenOutcome.Ok -> buildJsonObject { put("ok", true); put("token", t.token); put("expiresIn", t.expiresInSeconds) }
      TokenOutcome.SignedOut -> fail("signed_out")
      TokenOutcome.NotAllowed -> fail("not_allowed")
      TokenOutcome.Disconnected -> fail("disconnected")
      TokenOutcome.Offline -> fail("offline")
      is TokenOutcome.Unavailable -> fail("unavailable")
    }
  }

  private fun appKey(caller: String, request: JsonObject?): JsonObject {
    if (session.state.value is HostSession.SignedOut) return fail("signed_out")
    val purpose = when ((request?.get("purpose") as? JsonPrimitive)?.content) {
      "app" -> AppKeyPurpose.APP
      "backup" -> AppKeyPurpose.BACKUP
      else -> return fail("not_allowed")
    }
    return when (val r = custody.appKey(caller, purpose)) {
      is AppKeyOutcome.Ok -> buildJsonObject {
        put("ok", true)
        put("current", r.keys.current)
        putJsonArray("keys") {
          for ((e, k) in r.keys.keys) addJsonObject { put("epoch", e); put("key", PampaBase64.encode(k)) }
        }
      }
      AppKeyOutcome.NoE2e -> fail("no_e2e")
      AppKeyOutcome.Locked -> fail("locked")
      AppKeyOutcome.NeedsDoor -> fail("needs_door")
      AppKeyOutcome.NotAllowed -> fail("not_allowed")
    }
  }

  private fun setAppearance(caller: String, request: JsonObject?): JsonObject {
    val follow = (request?.get("followGlobal") as? JsonPrimitive)?.booleanOrNull ?: true
    val override = (request?.get("override") as? JsonObject)?.mapValues { (it.value as JsonPrimitive).content }
    appearance.setException(caller, follow, override)
    return appearance.responseFor(caller)
  }

  /**
   * I luoghi, se l'app ha il permesso. Altrimenti `not_allowed` con il perché (F10.8, scelta B8): `not_asked` (l'app
   * può aprire la richiesta dello Store, una volta), `denied` (l'utente ha detto no) o `never` (MyCloud Gallery, o
   * un'app fuori dalla tabella).
   */
  private suspend fun placesList(caller: String): JsonObject {
    val access = runCatching { places.accessFor(caller) }.getOrDefault(PlacesAccess.NEVER)
    val list = if (access == PlacesAccess.READ || access == PlacesAccess.READ_WRITE) runCatching { places.all() }.getOrNull() else null
    if (list == null) return buildJsonObject { put("ok", false); put("reason", "not_allowed"); put("access", access.wire) }
    return buildJsonObject {
      put("ok", true)
      put("canWrite", access == PlacesAccess.READ_WRITE)
      put("access", access.wire)
      putJsonArray("places") { for (p in list) add(AccountJson.placeJson(p)) }
    }
  }

  private suspend fun placesWrite(caller: String, write: suspend () -> Boolean): JsonObject =
    if (runCatching { write() }.getOrDefault(false)) buildJsonObject { put("ok", true) } else fail("not_allowed")

  private suspend fun backupAvailable(caller: String): JsonObject {
    if (policy.entryFor(caller)?.audiences?.get("sync")?.contains("sync.backup") != true) return buildJsonObject { putJsonArray("items") {} }
    val items = runCatching { backups.available(caller) }.getOrNull().orEmpty()
    return buildJsonObject {
      putJsonArray("items") {
        for (b in items) addJsonObject { put("n", b.n); put("createdAt", b.createdAt); put("size", b.size); put("epoch", b.epoch) }
      }
    }
  }

  /** Le persone: solo alle app della tabella (K3), e solo con qualcuno dentro. */
  private suspend fun peopleCall(caller: String, method: String, request: JsonObject?): JsonObject {
    val handler = people ?: return fail("no_people")
    if (session.state.value is HostSession.SignedOut) return fail("signed_out")
    if (policy.entryFor(caller) == null) return fail("not_allowed")
    return handler.handle(caller, method, request)
  }

  private fun fail(reason: String) = buildJsonObject { put("ok", false); put("reason", reason) }

  private companion object {
    val PEOPLE_METHODS = setOf(
      AccountProtocol.METHOD_PEOPLE_ROW,
      AccountProtocol.METHOD_SHARE_SEND,
      AccountProtocol.METHOD_SHARE_RECEIVED,
      AccountProtocol.METHOD_SHARE_REPLY,
      AccountProtocol.METHOD_SHARE_HIDE,
      AccountProtocol.METHOD_DEVICES_SEND,
    )
  }
}

/**
 * Il provider firmato dello Store (`b\api.md` § 5.1 e § 5.2): autorità `<applicationId>.account`, meta-data
 * `dev.pampa.account.HOST = 1`, `exported="true"` **senza** `android:permission` (il permesso `signature` ha già dato
 * problemi al ponte dei tool). Il controllo è a ogni chiamata, ed è l'unico cancello:
 *
 * 1. la firma di chi chiama deve essere quella dello Store (`checkSignatures(callingUid, myUid)`), altrimenti
 *    `SecurityException`, che l'app legge come «Store assente» (M1);
 * 2. il pacchetto viene da `getPackagesForUid`, cioè dal sistema: un'app non può dire di essere un'altra. Un UID con
 *    più pacchetti (UID condiviso) non è ammesso;
 * 3. poi la tabella delle app, nel [AccountProtocolHandler].
 *
 * Lo Store ne fa una sottoclasse che dà il gestore ([handler]).
 */
abstract class AccountHostProvider : ContentProvider() {

  /** Il gestore dello Store, già collegato a sessione, custodia, luoghi e aspetto. */
  abstract fun handler(): AccountProtocolHandler

  /** Quanto può durare una chiamata (un token può aspettare la rete). */
  open val callTimeoutMillis: Long = 20_000L

  final override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
    val uid = Binder.getCallingUid()
    if (compareSignatures(uid, Process.myUid()) != PackageManager.SIGNATURE_MATCH) {
      throw SecurityException("chiamante con un'altra firma: lo Store risponde solo alle app firmate come lui")
    }
    val packages = packagesForUid(uid)
    val caller = packages?.singleOrNull() ?: throw SecurityException("pacchetto del chiamante non univoco")
    val request = AccountProtocol.parse(extras?.getString(AccountProtocol.KEY_JSON))
    val response = try {
      runBlocking(Dispatchers.IO) { withTimeoutOrNull(callTimeoutMillis) { handler().handle(caller, method, request) } }
        ?: buildJsonObject { put("ok", false); put("reason", "unavailable") }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      buildJsonObject { put("ok", false); put("reason", "unavailable"); put("error", e.javaClass.simpleName) }
    }
    return Bundle().apply { putString(AccountProtocol.KEY_JSON, Json.encodeToString(JsonObject.serializer(), response)) }
  }

  /** Il confronto delle firme come lo fa il `PackageManager`; a parte perché Robolectric risponde sempre «uguali». */
  protected open fun compareSignatures(callingUid: Int, ownUid: Int): Int =
    context?.packageManager?.checkSignatures(callingUid, ownUid) ?: PackageManager.SIGNATURE_UNKNOWN_PACKAGE

  /** I pacchetti dell'UID che chiama, dal sistema. */
  protected open fun packagesForUid(uid: Int): Array<String>? = context?.packageManager?.getPackagesForUid(uid)

  final override fun onCreate(): Boolean = true
  final override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
  final override fun getType(uri: Uri): String? = null
  final override fun insert(uri: Uri, values: ContentValues?): Uri? = null
  final override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
  final override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
