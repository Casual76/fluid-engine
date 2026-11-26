package dev.antigravity.fluidengine.account.host

import dev.antigravity.fluidengine.account.host.custody.AppKeyPurpose
import dev.antigravity.fluidengine.account.host.custody.AppKeySet
import dev.antigravity.fluidengine.account.host.custody.CustodyKeyLostException
import dev.antigravity.fluidengine.account.host.custody.CustodyKeyring
import dev.antigravity.fluidengine.account.host.custody.CustodyLockedException
import dev.antigravity.fluidengine.account.host.custody.DoorKind
import dev.antigravity.fluidengine.account.host.custody.DoorSecret
import dev.antigravity.fluidengine.account.host.custody.EpochKind
import dev.antigravity.fluidengine.account.host.custody.KeyCustody
import dev.antigravity.fluidengine.account.host.custody.KeysView
import dev.antigravity.fluidengine.account.host.custody.RecoveryCode
import dev.antigravity.fluidengine.account.host.custody.TransferOffer
import dev.antigravity.fluidengine.account.host.custody.TransferQr
import dev.antigravity.fluidengine.account.host.custody.TransferSeal
import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import dev.antigravity.fluidengine.account.crypto.PampaCryptoException
import dev.antigravity.fluidengine.account.host.custody.CustodyPending
import dev.antigravity.fluidengine.account.host.custody.CustodyTamperedException
import dev.antigravity.fluidengine.account.host.custody.PendingRequest
import dev.antigravity.fluidengine.account.host.server.AccountHeaders
import dev.antigravity.fluidengine.account.host.server.AccountServerClient
import dev.antigravity.fluidengine.account.host.server.AccountServerException
import dev.antigravity.fluidengine.account.host.server.SessionKind
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** La risposta a `appKey` per un'app (`b\api.md` § 5.3). */
sealed interface AppKeyOutcome {
  data class Ok(val keys: AppKeySet) : AppKeyOutcome
  /** La cifratura non è partita (nessuna cosa cifrata ancora, Q11): l'app lavora in locale. */
  data object NoE2e : AppKeyOutcome
  /** Le chiavi di lavoro non sono pronte perché il telefono non è stato sbloccato dall'accensione, o serve la madre. */
  data object Locked : AppKeyOutcome
  /** Il Keystore ha perso la custodia, o l'app è nuova: lo Store deve aprire una porta (a telefono sbloccato). */
  data object NeedsDoor : AppKeyOutcome
  /** L'app non è nella tabella per questo scopo (K3). */
  data object NotAllowed : AppKeyOutcome
}

/** Il passaggio col QR non ha ancora il sigillo del telefono vecchio: si riprova fra poco ([CustodyService.pollTransfer]). */
class TransferPendingException(val transferId: String) : IllegalStateException("il passaggio $transferId aspetta ancora il sigillo")

/**
 * Il passaggio col QR non darà più un sigillo: rifiutato dal telefono vecchio (`refused`) o già ritirato (`taken`).
 * L'offerta è dimenticata; per riprovare ne serve una nuova ([CustodyService.beginTransfer]).
 */
class TransferEndedException(val transferId: String, val state: String) : IllegalStateException("il passaggio $transferId è finito ($state)")

/**
 * La custodia al lavoro con il server (`b\chiavi.md` § 4): prende lo stato (`GET /v1/keys`), lo fa verificare a
 * [KeyCustody], spedisce quello che lei prepara, e chiude il risultato in [CustodyKeyring]. Le operazioni che toccano
 * la chiave madre si fanno a telefono sbloccato, e le azioni delicate vogliono una prova recente (S+R, `b\api.md`
 * § 2): se il server risponde `reauth_required`, lo Store chiede l'impronta e riprova.
 */
class CustodyService(
  private val client: AccountServerClient,
  private val session: AccountSession,
  private val keyring: CustodyKeyring,
  private val policy: AppPolicy,
  private val accountId: () -> String?,
  private val installed: () -> Collection<String>? = { null },
  /** L'id di questo dispositivo (`dev_…`), per la sua porta. Senza, la custodia lavora senza porta del dispositivo. */
  private val deviceId: () -> String? = { null },
) {
  suspend fun keysView(): KeysView = KeysView.parse(client.call("GET", "/v1/keys", token = session.storeToken()))

  private fun grants() = policy.workKeyGrants(installed())

  /**
   * La prima cifratura (`b\chiavi.md` § 4.1): la chiave nasce qui, le porte date diventano le prime. Con il codice
   * di recupero la porta viaggia con `auth` nello stesso batch (`POST /v1/recovery`), le altre con `PUT`.
   *
   * R2 · La chiave appena nata, il codice e le richieste già pronte si chiudono in classe madre **prima** di partire
   * ([pendingRecoveryCode]): se la rete cade o lo Store muore a metà, la chiamata dopo riprende lo stesso lavoro, con
   * gli stessi corpi e le stesse chiavi d'idempotenza, invece di far nascere una seconda chiave. Se il lavoro a metà è
   * di un altro codice e il server ne ha già una parte, [IllegalStateException]: lo Store riparte da
   * [pendingRecoveryCode], che è il codice che il server ha. Fino alla fine `hasCustody` è falso, e niente di
   * cifrato parte (K2).
   */
  suspend fun startEncryption(recovery: RecoveryCode?, others: List<DoorSecret> = emptyList()): KeyCustody {
    val account = requireNotNull(accountId()) { "nessun account" }
    val half = pending(account)?.takeIf { it.custody != null }
    if (half != null) {
      if (half.code == recovery) return finishStart(half)
      if (serverHasAny(half, keysView())) {
        throw IllegalStateException("c'è una prima cifratura rimasta a metà con un altro codice: riprendila col codice di pendingRecoveryCode()")
      }
      // Il server non ne ha niente (per esempio ha chiesto prima l'impronta): si riparte da capo con quello di adesso.
      keyring.clearPending()
    }
    val doors = listOfNotNull(recovery?.let(DoorSecret::recovery)) + others
    val start = KeyCustody.start(account, doors)
    val requests = start.upload.doors.map { door ->
      if (door.kind == DoorKind.RECOVERY) {
        request("POST", "/v1/recovery", buildJsonObject {
          put("auth", recovery!!.auth)
          put("door", door.toJson())
        })
      } else {
        request("PUT", "/v1/keys/doors/${door.doorId}", door.toJson())
      }
    } + request("POST", "/v1/keys/epochs", start.upload.epoch.toJson())
    val pending = CustodyPending(account, recovery, shown = false, custody = start.custody, requests = requests)
    keyring.savePending(pending)
    return finishStart(pending)
  }

  /**
   * R2 · Finisce una prima cifratura rimasta a metà: se il server è già partito con **questa** chiave (catena e
   * sigilli d'autenticità tornano con lei) la si tiene; se non è partito, si rimandano le stesse richieste.
   */
  private suspend fun finishStart(pending: CustodyPending): KeyCustody {
    val custody = requireNotNull(pending.custody)
    val view = keysView()
    if (view.started) {
      // La custodia preparata deve essere quella del server: stessa catena, sigilli d'autenticità fatti con lei.
      // Un'epoca più avanti ([CustodyBehindException]) o un'altra chiave: si apre una porta, e il lavoro a metà se ne va.
      custody.verify(view)
    } else {
      send(pending.requests)
    }
    keyring.saveCustody(custody, grants())
    settle(pending.sent())
    return ensureDeviceDoor(custody)
  }

  /** Il server ha già una delle porte preparate da [pending] (stessa pubblica e stesso sale)? */
  private fun serverHasAny(pending: CustodyPending, view: KeysView): Boolean = view.started || pending.requests.any { r ->
    val door = (r.body["door"] as? JsonObject) ?: r.body.takeIf { r.method == "PUT" } ?: return@any false
    val id = (door["doorId"] as? JsonPrimitive)?.content ?: return@any false
    val there = view.door(id) ?: return@any false
    there.pub == (door["pub"] as? JsonPrimitive)?.content && there.salt == (door["salt"] as? JsonPrimitive)?.content
  }

  private fun request(method: String, path: String, body: JsonObject) =
    PendingRequest(method, path, body, PampaBase64.encode(PampaCrypto.random(18)))

  /** Le richieste già pronte, uguali, ognuna con la sua chiave d'idempotenza: ripetute danno la stessa risposta. */
  private suspend fun send(requests: List<PendingRequest>) {
    if (requests.isEmpty()) return
    val token = session.storeToken()
    for (r in requests) client.call(r.method, r.path, r.body, token, mapOf(AccountHeaders.IDEMPOTENCY_KEY to r.idempotencyKey))
  }

  private fun pending(account: String): CustodyPending? = keyring.loadPending()?.takeIf { it.accountId == account }

  /** Salva quello che resta del lavoro, o lo toglie se non resta niente. */
  private fun settle(pending: CustodyPending) {
    if (pending.empty) keyring.clearPending() else keyring.savePending(pending)
  }

  /**
   * R2 · Il codice di recupero nuovo che lo Store deve ancora mostrare, o che il server non ha ancora preso tutto:
   * c'è dal momento in cui [createRecoveryCode] o [startEncryption] lo preparano, **prima** della rete, finché l'utente
   * non dice di averlo scritto ([recoveryCodeSeen]) e l'invio non è finito. Dopo un riavvio o una rete caduta lo Store
   * lo rimostra da qui; dopo un'attesa di sicurezza (`waiting`) è il codice che varrà. `null` se non ce n'è.
   */
  fun pendingRecoveryCode(): RecoveryCode? = accountId()?.let(::pending)?.code

  /** R2 · L'utente ha scritto il codice: non si mostra più. Se l'invio non è finito resta, per rimandarlo uguale. */
  fun recoveryCodeSeen() {
    val p = accountId()?.let(::pending) ?: return
    settle(p.seen())
  }

  /**
   * La porta di questo dispositivo (`device:<deviceId>`), se non c'è ancora: così le epoche nuove create da un altro
   * dispositivo arrivano in silenzio ([catchUp]). Si fa appena la custodia ha la chiave, e una volta per i dispositivi
   * già dentro. Non conta per K2.
   */
  suspend fun ensureDeviceDoor(custody: KeyCustody? = keyring.loadCustody()): KeyCustody {
    val mine = requireNotNull(custody) { "la cifratura non è partita" }
    val id = deviceId() ?: return mine
    val view = keysView()
    if (mine.deviceDoorId == DoorSecret.deviceDoorId(id) && view.door(mine.deviceDoorId!!) != null) return mine
    val pending = mine.createDeviceDoor(id, view)
    client.call("PUT", "/v1/keys/doors/${pending.upload.doorId}", pending.upload.toJson(), session.storeToken())
    keyring.saveCustody(pending.custody, grants())
    return pending.custody
  }

  /**
   * Un altro dispositivo ha creato un'epoca nuova: la si apre con la porta di questo dispositivo, senza chiedere niente.
   * Se la porta manca, [dev.antigravity.fluidengine.account.host.custody.CustodyBehindException]: serve una porta vera
   * ([openWithDoor]) o il passaggio. Serve la madre: si fa a telefono sbloccato.
   */
  suspend fun catchUp(): KeyCustody {
    val mine = requireNotNull(keyring.loadCustody()) { "la cifratura non è partita" }
    val view = keysView()
    if (view.epoch <= mine.current) return mine.also { it.verify(view) }
    val caught = mine.catchUp(view)
    keyring.saveCustody(caught, grants())
    return caught
  }

  /**
   * Un codice di recupero nuovo, generato qui (critica S1). Se la cifratura è partita la sua porta va con `auth`
   * nello stesso batch: un codice che non apre i dati non può esistere. Il codice si mostra una volta e basta.
   *
   * R2 · Il codice e il corpo si chiudono in classe madre **prima** di partire. Se la rete cade dopo che il server ha
   * già preso il codice, o lo Store muore prima di mostrarlo, il codice si ritrova in [pendingRecoveryCode], e la
   * chiamata dopo **rimanda lo stesso** (stessa chiave d'idempotenza) invece di farne un altro. Con l'attesa di
   * sicurezza (`waiting`) l'eccezione esce come prima, e il codice che varrà fra 24 ore è in [pendingRecoveryCode].
   */
  suspend fun createRecoveryCode(): RecoveryCode {
    val account = requireNotNull(accountId()) { "nessun account" }
    pending(account)?.takeIf { it.unfinished }?.let { half ->
      if (half.custody != null) {
        // Una prima cifratura a metà: si finisce lei. Se portava un codice, è quello che il server ha.
        finishStart(half)
        half.code?.let { return it }
      } else {
        val code = requireNotNull(half.code)
        try {
          sendRecovery(half, keyring.loadCustody())
          return code
        } catch (e: AccountServerException) {
          // Il corpo preparato non vale più (un'epoca nuova nel frattempo, la cifratura partita): se ne fa un altro,
          // con un codice nuovo che si mostra lui. Gli altri errori (rete, impronta da rifare) lasciano lo stesso.
          if (e.code != "conflict" || e.reason !in STALE_REASONS) throw e
          keyring.clearPending()
        }
      }
    }
    val code = RecoveryCode.generate()
    val custody = keyring.loadCustody()
    val body = if (custody == null) {
      buildJsonObject { put("auth", code.auth) }
    } else {
      val door = custody.buildDoor(DoorSecret.recovery(code), keysView())
      buildJsonObject {
        put("auth", code.auth)
        put("door", door.toJson())
      }
    }
    val pending = CustodyPending(account, code, shown = false, custody = null, requests = listOf(request("POST", "/v1/recovery", body)))
    keyring.savePending(pending)
    sendRecovery(pending, custody)
    return code
  }

  /** Manda il codice preparato; col sì del server (o con l'attesa di sicurezza) l'invio è finito, il codice resta da mostrare. */
  private suspend fun sendRecovery(pending: CustodyPending, custody: KeyCustody?) {
    try {
      send(pending.requests)
    } catch (e: AccountServerException) {
      // Il server l'ha preso in carico: fra 24 ore varrà questo codice. Rimandarlo darebbe «c'è già un'azione in attesa».
      if (e.isWaiting) settle(pending.sent())
      throw e
    }
    if (custody != null && pending.requests.any { it.body["door"] != null }) {
      custody.rememberDoor(DoorSecret.RECOVERY_DOOR)
      keyring.saveCustody(custody, grants())
    }
    settle(pending.sent())
  }

  /** Una passkey col PRF diventa una porta: il telefono, che ha la chiave, la sigilla per tutte le epoche. */
  suspend fun addPrfDoor(credentialId: String, prfOutput: ByteArray, synced: Boolean) {
    val custody = requireNotNull(keyring.loadCustody()) { "la cifratura non è partita" }
    val door = custody.buildDoor(DoorSecret.prf(credentialId, prfOutput, synced), keysView())
    client.call("PUT", "/v1/keys/doors/${door.doorId}", door.toJson(), session.storeToken())
    keyring.saveCustody(custody, grants())
  }

  /** Un'epoca nuova (`b\chiavi.md` § 6): dopo un «perso o rubato» [kind] è [EpochKind.LOST] e i tag si rifanno. */
  suspend fun rotate(kind: EpochKind = EpochKind.NORMAL): KeyCustody {
    val custody = requireNotNull(keyring.loadCustody()) { "la cifratura non è partita" }
    val pending = custody.rotate(keysView(), kind)
    client.call("POST", "/v1/keys/epochs", pending.upload.toJson(), session.storeToken())
    keyring.saveCustody(pending.custody, grants())
    return pending.custody
  }

  /**
   * Apre la chiave dei dati da una porta: il telefono nuovo, il Keystore che l'ha persa (§ 11 punto 7), o il telefono
   * rimasto indietro di un'epoca. Verifica tutto (K8, K9) prima di chiudere il risultato.
   *
   * F10.7 · Con una **sessione di recupero** (entrata col codice) la porta di questo dispositivo non si scrive: il server
   * la accetta solo da una sessione normale (`not_this_device`, `b\api.md` § 2). Si scrive dopo, quando una passkey
   * nuova o Google rendono normale la sessione: [AccountSession.promote], poi [ensureDeviceDoor].
   */
  suspend fun openWithDoor(door: DoorSecret): KeyCustody {
    val view = keysView()
    val mine = runCatching { keyring.loadCustody() }.getOrNull()
    val custody = mine?.catchUp(view, door) ?: KeyCustody.openWithDoor(requireNotNull(accountId()), view, door)
    keyring.saveCustody(custody, grants())
    dropHalfStart()
    if (recoverySession()) return custody
    return ensureDeviceDoor(custody)
  }

  /** La sessione di questo telefono è nata dal codice di recupero e non è ancora normale. */
  private fun recoverySession(): Boolean = (session.state.value as? HostSession.SignedIn)?.kind == SessionKind.RECOVERY

  /** R2 · La custodia è arrivata per un'altra strada (una porta, il passaggio): una prima cifratura a metà non serve più. */
  private fun dropHalfStart() {
    val p = runCatching { keyring.loadPending() }.getOrNull() ?: return
    if (p.custody != null) keyring.clearPending()
  }

  // --- Il passaggio fra telefoni (`b\chiavi.md` § 4.2) ------------------------------------------------------------

  /** Il telefono **nuovo**: chiede un numero al server, a corpo vuoto, e prepara il QR. */
  suspend fun beginTransfer(): TransferOffer {
    val r = client.call("POST", "/v1/keys/transfers", JsonObject(emptyMap()), session.storeToken())
    val id = (r["transferId"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw CustodyTamperedException("risposta del passaggio illeggibile")
    return TransferOffer.create(id)
  }

  /** Il telefono **vecchio** legge di che dispositivo è il passaggio, per la schermata «Passare i tuoi dati a…?». */
  suspend fun transferInfo(qr: TransferQr): JsonObject = client.call("GET", "/v1/keys/transfers/${qr.transferId}/info", token = session.storeToken())

  /** Il telefono **vecchio**, dopo l'impronta: sigilla e carica. */
  suspend fun confirmTransfer(qr: TransferQr) {
    val custody = requireNotNull(keyring.loadCustody()) { "la cifratura non è partita" }
    custody.verify(keysView())
    val seal = custody.sealTransfer(qr)
    client.call("POST", "/v1/keys/transfers/${qr.transferId}/seal", buildJsonObject {
      put("sealed", seal.sealed)
      put("mac", seal.mac)
    }, session.storeToken())
  }

  /**
   * Il telefono **nuovo** guarda se il vecchio ha già sigillato. Finché il server non ha il sigillo (`waiting`) risponde
   * `null` e l'offerta resta com'è: lo Store riprova ogni pochi secondi, mentre il QR è sullo schermo. Col sigillo fa
   * come [completeTransfer]. Se il passaggio è finito senza sigillo (rifiutato, o già ritirato) dimentica l'offerta e
   * lancia [TransferEndedException].
   */
  suspend fun pollTransfer(offer: TransferOffer): KeyCustody? {
    val r = client.call("GET", "/v1/keys/transfers/${offer.transferId}", token = session.storeToken())
    val sealed = (r["sealed"] as? JsonPrimitive)?.content
    val mac = (r["mac"] as? JsonPrimitive)?.content
    if (sealed == null || mac == null) {
      val state = (r["state"] as? JsonPrimitive)?.content ?: "waiting"
      if (state == "waiting" || state == "sealed") return null
      offer.forget()
      throw TransferEndedException(offer.transferId, state)
    }
    return receiveTransfer(offer, TransferSeal(sealed, mac))
  }

  /**
   * Il telefono **nuovo** ritira, verifica, chiude nel suo Keystore e dimentica `n` e `s`. Se il sigillo non c'è
   * ancora, [TransferPendingException] e l'offerta resta buona (prima di F10.7 qui cadeva un `NullPointerException`).
   */
  suspend fun completeTransfer(offer: TransferOffer): KeyCustody = pollTransfer(offer) ?: throw TransferPendingException(offer.transferId)

  private suspend fun receiveTransfer(offer: TransferOffer, seal: TransferSeal): KeyCustody {
    try {
      val custody = KeyCustody.receiveTransfer(requireNotNull(accountId()), offer, seal, keysView())
      keyring.saveCustody(custody, grants())
      dropHalfStart()
      return ensureDeviceDoor(custody)
    } finally {
      offer.forget()
    }
  }

  // --- Le chiavi delle app (`appKey`) -----------------------------------------------------------------------------

  /**
   * Le chiavi di [packageName] per [purpose], solo sue (K3). Legge solo le chiavi di lavoro, mai la madre: con la
   * raccomandazione A di Q7 risponde anche a telefono bloccato.
   */
  fun appKey(packageName: String, purpose: AppKeyPurpose): AppKeyOutcome {
    val entry = policy.entryFor(packageName) ?: return AppKeyOutcome.NotAllowed
    val scope = if (purpose == AppKeyPurpose.APP) "sync.data" else "sync.backup"
    if (scope !in entry.audiences["sync"].orEmpty()) return AppKeyOutcome.NotAllowed
    if (!keyring.hasCustody) return AppKeyOutcome.NoE2e
    return try {
      keyring.workKeys(packageName, purpose)?.let { AppKeyOutcome.Ok(it) } ?: AppKeyOutcome.Locked
    } catch (_: CustodyLockedException) {
      AppKeyOutcome.Locked
    } catch (_: CustodyKeyLostException) {
      AppKeyOutcome.NeedsDoor
    } catch (_: PampaCryptoException) {
      // R2 · Le chiavi di lavoro si aprono ma non si leggono: come non ancora ricavate, si rifanno dalla madre.
      AppKeyOutcome.Locked
    }
  }

  /** Al primo sblocco, o quando cambia la tabella delle app: ricava le chiavi di lavoro mancanti. Serve la madre. */
  fun refreshWorkKeys(): Boolean = try {
    keyring.loadCustody()?.let { keyring.refreshWorkKeys(it, grants()); true } ?: false
  } catch (_: CustodyLockedException) {
    false
  }

  private companion object {
    /** I motivi di `conflict` che vogliono dire «il corpo preparato è vecchio» (`errors.json`, rotte delle porte). */
    val STALE_REASONS = setOf("seals_mismatch", "tag_epoch", "door_required", "epoch_changed")
  }
}
