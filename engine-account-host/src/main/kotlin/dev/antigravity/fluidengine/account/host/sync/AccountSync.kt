package dev.antigravity.fluidengine.account.host.sync

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.crypto.PampaCryptoException
import dev.antigravity.fluidengine.account.crypto.PampaEnvelope
import dev.antigravity.fluidengine.account.crypto.PampaLabels
import dev.antigravity.fluidengine.account.host.AccountSession
import dev.antigravity.fluidengine.account.host.AppearanceStore
import dev.antigravity.fluidengine.account.host.HostSession
import dev.antigravity.fluidengine.account.host.PlacesStore
import dev.antigravity.fluidengine.account.host.PrefRow
import dev.antigravity.fluidengine.account.host.TokenOutcome
import dev.antigravity.fluidengine.account.host.custody.AppKeySet
import dev.antigravity.fluidengine.account.host.custody.CustodyKeyLostException
import dev.antigravity.fluidengine.account.host.custody.CustodyKeyring
import dev.antigravity.fluidengine.account.host.custody.CustodyLockedException
import dev.antigravity.fluidengine.account.host.server.AccountHeaders
import dev.antigravity.fluidengine.account.host.server.AccountServerClient
import dev.antigravity.fluidengine.account.host.server.AccountServerException
import dev.antigravity.fluidengine.sync.SyncAccepted
import dev.antigravity.fluidengine.sync.SyncFailure
import dev.antigravity.fluidengine.sync.SyncPullResult
import dev.antigravity.fluidengine.sync.SyncPushBatch
import dev.antigravity.fluidengine.sync.SyncPushResult
import dev.antigravity.fluidengine.sync.SyncRelay
import dev.antigravity.fluidengine.sync.SyncRelayException
import dev.antigravity.fluidengine.sync.SyncRemoteRow
import dev.antigravity.fluidengine.sync.SyncSealContext
import dev.antigravity.fluidengine.sync.SyncSealException
import dev.antigravity.fluidengine.sync.SyncSealedPayload
import dev.antigravity.fluidengine.sync.SyncSealer
import dev.antigravity.fluidengine.sync.SyncSpaceId
import dev.antigravity.fluidengine.sync.SyncSpaceReport
import java.io.IOException
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * F10.8 · Il relay di `engine-sync` sopra le righe cifrate del server (`b\api.md` § 4.10; F10.3, `src\routes\sync.ts`):
 * `GET` e `POST /v1/sync/{raccolta}` col token di un'app. Il server vede solo byte sigillati, versioni e numeri
 * d'ordine. Lo usa lo Store per i luoghi (`places`, ambito `sync.places`); è lo stesso contratto che useranno le app per
 * i loro dati in F10.9.
 *
 * - Una riga va col suo `baseVersion` e la sua epoca (`keyVersion`): una cancellazione senza contenuto porta l'epoca di
 *   oggi ([currentEpoch]).
 * - L'invio porta la chiave d'idempotenza di `engine-sync`: rimandato dopo un errore di rete dà la stessa risposta.
 * - Le righe grandi (`blob`) non servono ai luoghi e non si leggono: una riga così è un errore.
 * - Senza rete è [SyncFailure.Offline]; `429` è [SyncFailure.RetryLater]; il resto è [SyncFailure.Rejected] col codice.
 */
class AccountSyncRelay(
  private val client: AccountServerClient,
  private val token: suspend () -> String,
  private val collection: String,
  private val currentEpoch: () -> Int,
) : SyncRelay {

  override suspend fun pull(space: SyncSpaceId, since: Long, limit: Int): SyncPullResult = guard {
    val r = client.call("GET", "/v1/sync/$collection?since=$since&limit=$limit", token = token())
    val rows = (r["records"] as? JsonArray).orEmpty().map { rowOf(it as JsonObject) }
    val next = (r["next"] as? JsonPrimitive)?.longOrNull
    SyncPullResult.Rows(rows, next ?: rows.maxOfOrNull { it.seq } ?: since, hasMore = next != null, serverTimeMillis = client.serverNow())
  }

  override suspend fun push(space: SyncSpaceId, batch: SyncPushBatch): SyncPushResult = guard {
    val body = buildJsonObject {
      putJsonArray("changes") {
        for (c in batch.changes) addJsonObject {
          put("recordId", c.recordId)
          put("baseVersion", c.baseVersion)
          put("deleted", c.deleted)
          put("keyVersion", c.payload?.keyVersion ?: currentEpoch())
          if (!c.deleted) c.payload?.let { put("ciphertext", PampaBase64.encode(it.bytes)) }
        }
      }
    }
    val r = client.call("POST", "/v1/sync/$collection", body, token(), mapOf(AccountHeaders.IDEMPOTENCY_KEY to batch.idempotencyKey))
    val seq = (r["seq"] as? JsonPrimitive)?.longOrNull ?: 0L
    fun accepted(key: String) = (r[key] as? JsonArray).orEmpty().map {
      val o = it as JsonObject
      SyncAccepted((o["recordId"] as JsonPrimitive).content, (o["version"] as JsonPrimitive).longOrNull ?: 0L, seq)
    }
    SyncPushResult(accepted("accepted") + accepted("applied"), (r["conflicts"] as? JsonArray).orEmpty().map { rowOf(it as JsonObject) }, client.serverNow())
  }

  private fun rowOf(o: JsonObject): SyncRemoteRow {
    if (o["blob"] != null && o["blob"] !is JsonNull) throw SyncRelayException(SyncFailure.Rejected("blob", "riga grande in una raccolta che non ne usa"))
    val keyVersion = (o["keyVersion"] as? JsonPrimitive)?.intOrNull ?: 0
    val payload = (o["ciphertext"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.let { SyncSealedPayload(PampaBase64.decode(it), keyVersion) }
    return SyncRemoteRow(
      recordId = (o["recordId"] as JsonPrimitive).content,
      version = (o["version"] as JsonPrimitive).longOrNull ?: 0L,
      deleted = (o["deleted"] as? JsonPrimitive)?.booleanOrNull == true,
      payload = payload,
      seq = (o["seq"] as? JsonPrimitive)?.longOrNull ?: 0L,
    )
  }

  private inline fun <T> guard(block: () -> T): T = try {
    block()
  } catch (e: SyncRelayException) {
    throw e
  } catch (e: IOException) {
    throw SyncRelayException(SyncFailure.Offline, e)
  } catch (e: AccountServerException) {
    throw SyncRelayException(
      when {
        e.status == 429 -> SyncFailure.RetryLater((e.retryAfterSeconds ?: 60) * 1000)
        e.code == "unavailable" || e.status >= 500 -> SyncFailure.Offline
        else -> SyncFailure.Rejected(e.reason ?: e.code, e.message)
      },
      e,
    )
  }
}

/**
 * F10.8 · Il sigillo dei luoghi fra i dispositivi: AES-256-GCM con `K_com(places)` dell'epoca, la busta `{v,e,alg,n,c}`
 * e i dati associati del contratto (`accountId|pacchetto dello Store|places|idRiga|versione`, `e2e.json`
 * `recordAad`). Il server non può spostare una riga su un'altra, né servirla come una versione diversa (K4).
 */
class PlacesSealer(
  private val accountId: String,
  private val storePackage: String,
  private val keys: () -> AppKeySet?,
) : SyncSealer {
  private fun aad(context: SyncSealContext) =
    PampaLabels.recordAad(accountId, storePackage, PlacesStore.COLLECTION, context.recordId, context.version)

  override suspend fun seal(context: SyncSealContext, plaintext: ByteArray): SyncSealedPayload {
    val set = keys() ?: throw SyncSealException("le chiavi dei luoghi non ci sono")
    val key = set.keys[set.current] ?: throw SyncSealException("manca la chiave dell'epoca ${set.current}")
    val envelope = PampaEnvelope.seal(key, set.current, plaintext, aad(context))
    return SyncSealedPayload(envelope.toJson().toByteArray(Charsets.UTF_8), set.current)
  }

  override suspend fun open(context: SyncSealContext, payload: SyncSealedPayload): ByteArray = try {
    val envelope = PampaEnvelope.parseJson(payload.bytes.decodeToString())
    val key = keys()?.keys?.get(envelope.epoch) ?: throw SyncSealException("manca la chiave dell'epoca ${envelope.epoch}")
    envelope.open(key, aad(context))
  } catch (e: PampaCryptoException) {
    throw SyncSealException("la riga ${context.recordId} non si apre o non è al suo posto (K4)", e)
  }
}

/** Com'è andata una sincronizzazione dello Store (luoghi o preferenze). */
sealed interface StoreSyncResult {
  /** [changed]: qualcosa che le app vedono è cambiato (e lo Store le ha avvisate). */
  data class Ok(val changed: Boolean, val pushed: Int, val pulled: Int, val rejected: List<String> = emptyList()) : StoreSyncResult
  data object SignedOut : StoreSyncResult
  /** La cifratura non è partita, o le chiavi dei luoghi non sono state ancora ricavate: i luoghi restano sul telefono. */
  data object NoE2e : StoreSyncResult
  /** Il telefono non è stato sbloccato dall'accensione: si riprova dopo. */
  data object Locked : StoreSyncResult
  data object Offline : StoreSyncResult
  data class Failed(val failure: SyncFailure) : StoreSyncResult
}

/**
 * F10.8 · I luoghi comuni fra i dispositivi (`b\chiavi.md` § 8): lo spazio `places` di [PlacesStore] sopra
 * [AccountSyncRelay], cifrato con `K_com(places)` ([PlacesSealer]). Il token è quello dello Store per `sync` con
 * l'ambito `sync.places`, che il server dà solo allo Store (F10.3). Due modifiche dello stesso campo: vince la più
 * recente, con l'orologio logico di `engine-sync`. Due dispositivi che creano lo stesso luogo senza vedersi: resta la
 * riga scritta per ultima, l'altra si cancella.
 *
 * Quando farla lo decide lo Store (F10.7): dopo un cambio, all'apertura e al rinnovo, col telefono sbloccato almeno una
 * volta dall'accensione. Senza account i luoghi restano comuni sul telefono e basta.
 */
class PlacesSync(
  private val places: PlacesStore,
  private val client: AccountServerClient,
  private val session: AccountSession,
  private val keyring: CustodyKeyring,
  private val storePackage: String,
) {
  private fun keys(): AppKeySet? = keyring.sharedKeys(PampaLabels.SHARED_PLACES)

  suspend fun sync(): StoreSyncResult {
    val s = session.state.value as? HostSession.SignedIn ?: return StoreSyncResult.SignedOut
    val keys = try {
      keys() ?: return StoreSyncResult.NoE2e
    } catch (_: CustodyLockedException) {
      return StoreSyncResult.Locked
    } catch (_: CustodyKeyLostException) {
      return StoreSyncResult.NoE2e
    }
    val token: suspend () -> String = {
      when (val t = session.appToken(storePackage, "sync", setOf("sync.places"))) {
        is TokenOutcome.Ok -> t.token
        TokenOutcome.Offline -> throw SyncRelayException(SyncFailure.Offline)
        else -> throw SyncRelayException(SyncFailure.Rejected("token", t.toString()))
      }
    }
    places.attach(
      AccountSyncRelay(client, token, PlacesStore.COLLECTION) { runCatching { keys() }.getOrNull()?.current ?: keys.current },
      PlacesSealer(s.accountId, storePackage) { runCatching { keys() }.getOrNull() },
    )
    val before = places.all()
    var report: SyncSpaceReport = places.space.sync()
    if (report.failure == null) {
      val duplicates = places.duplicates()
      if (duplicates.isNotEmpty()) {
        for (d in duplicates) places.space.delete(d)
        report = places.space.sync()
      }
    }
    val after = places.all()
    val changed = before != after
    if (changed) places.changedFromSync()
    return when (val f = report.failure) {
      null -> StoreSyncResult.Ok(changed, report.pushed, report.pulled)
      SyncFailure.Offline -> StoreSyncResult.Offline
      else -> StoreSyncResult.Failed(f)
    }
  }
}

/**
 * F10.8 · Tema e preferenze fra i dispositivi (`b\architettura.md` § 5.2; `b\api.md` § 4.9; F10.3,
 * `src\routes\choices.ts`): legge le novità (`GET /v1/prefs?since=`) e le fonde campo per campo con l'orologio logico
 * ([AppearanceStore.merge]), poi manda le scelte fatte qui (`POST /v1/prefs`), solo delle chiavi che il server annuncia
 * (critica M1). Funziona col token dello Store: le preferenze non sono cifrate (il server le valida).
 *
 * Una riga rifiutata per l'orologio (`bad_hlc`, il telefono troppo avanti) si rimanda dopo, con l'ora corretta; le
 * altre rifiutate (`bad_value`, `unknown_key`, …) restano solo sul telefono.
 */
class AppearanceSync(
  private val client: AccountServerClient,
  private val session: AccountSession,
  private val store: AppearanceStore,
) {
  suspend fun sync(): StoreSyncResult {
    if (session.state.value !is HostSession.SignedIn) return StoreSyncResult.SignedOut
    return try {
      val token = session.storeToken()
      val r = client.call("GET", "/v1/prefs?since=${store.serverSeq()}", token = token)
      store.syncClockTo(client.serverNow())
      (r["accepted"] as? JsonArray)?.mapNotNull { ((it as? JsonObject)?.get("key") as? JsonPrimitive)?.content }?.toSet()?.let(store::setAccepted)
      val rows = (r["prefs"] as? JsonArray).orEmpty().mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        PrefRow((o["scope"] as JsonPrimitive).content, (o["key"] as JsonPrimitive).content, o["value"] ?: JsonNull, (o["hlc"] as JsonPrimitive).content)
      }
      val changed = store.merge(rows)
      (r["seq"] as? JsonPrimitive)?.longOrNull?.let(store::setServerSeq)
      var pushed = 0
      val rejected = ArrayList<String>()
      // Al massimo due giri: il secondo solo per le righe rifiutate per l'orologio, rinate all'ora del server.
      for (round in 0 until 2) {
        val pending = store.pending()
        if (pending.isEmpty()) break
        val (sent, again) = push(token, pending, rejected, lastRound = round == 1)
        pushed += sent
        if (again.isEmpty()) break
        store.restamp(again, client.serverNow())
      }
      StoreSyncResult.Ok(changed, pushed, rows.size, rejected)
    } catch (_: IOException) {
      StoreSyncResult.Offline
    } catch (e: AccountServerException) {
      if (e.code == "unavailable" || e.status >= 500) StoreSyncResult.Offline else StoreSyncResult.Failed(SyncFailure.Rejected(e.code, e.message))
    }
  }

  /**
   * Manda [pending]; risponde quante sono passate e quelle rifiutate per l'orologio (`bad_hlc`), da rifare. Le altre
   * rifiutate (`bad_value`, `unknown_key`, …) restano solo sul telefono e finiscono in [rejected].
   */
  private suspend fun push(token: String, pending: List<PrefRow>, rejected: MutableList<String>, lastRound: Boolean): Pair<Int, List<PrefRow>> {
    val body = buildJsonObject {
      putJsonArray("changes") {
        for (p in pending) addJsonObject {
          put("scope", p.scope)
          put("key", p.key)
          put("value", p.value)
          put("hlc", p.hlc)
        }
      }
    }
    val resp = client.call("POST", "/v1/prefs", body, token, mapOf(AccountHeaders.IDEMPOTENCY_KEY to UUID.randomUUID().toString()))
    val again = HashSet<Int>()
    var refused = 0
    for (x in (resp["rejected"] as? JsonArray).orEmpty()) {
      val o = x as? JsonObject ?: continue
      val index = (o["index"] as? JsonPrimitive)?.intOrNull ?: continue
      val reason = (o["reason"] as? JsonPrimitive)?.content ?: "rejected"
      refused++
      if (reason == "bad_hlc" && !lastRound) again += index else rejected += "${pending.getOrNull(index)?.key}: $reason"
    }
    // Non si aggiorna il numero d'ordine con quello del POST: fra la lettura e l'invio un altro dispositivo può aver
    // scritto, e la prossima lettura deve vederlo. Le proprie righe tornano indietro e non cambiano niente. Una riga
    // `bad_hlc` anche al secondo giro resta da mandare: la prossima sincronizzazione riprova.
    store.markSent(pending.filterIndexed { i, p -> i !in again && !(lastRound && rejected.any { it.startsWith("${p.key}: bad_hlc") }) })
    return (pending.size - refused) to again.map { pending[it] }
  }
}
