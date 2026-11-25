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
import dev.antigravity.fluidengine.account.host.server.AccountServerClient
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
   */
  suspend fun startEncryption(recovery: RecoveryCode?, others: List<DoorSecret> = emptyList()): KeyCustody {
    val account = requireNotNull(accountId()) { "nessun account" }
    val doors = listOfNotNull(recovery?.let(DoorSecret::recovery)) + others
    val start = KeyCustody.start(account, doors)
    val token = session.storeToken()
    for (door in start.upload.doors) {
      if (door.kind == DoorKind.RECOVERY) {
        client.call("POST", "/v1/recovery", buildJsonObject {
          put("auth", recovery!!.auth)
          put("door", door.toJson())
        }, token)
      } else {
        client.call("PUT", "/v1/keys/doors/${door.doorId}", door.toJson(), token)
      }
    }
    client.call("POST", "/v1/keys/epochs", start.upload.epoch.toJson(), token)
    keyring.saveCustody(start.custody, grants())
    return ensureDeviceDoor(start.custody)
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
   */
  suspend fun createRecoveryCode(): RecoveryCode {
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
    client.call("POST", "/v1/recovery", body, session.storeToken())
    custody?.let { keyring.saveCustody(it, grants()) }
    return code
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
   */
  suspend fun openWithDoor(door: DoorSecret): KeyCustody {
    val view = keysView()
    val mine = runCatching { keyring.loadCustody() }.getOrNull()
    val custody = mine?.catchUp(view, door) ?: KeyCustody.openWithDoor(requireNotNull(accountId()), view, door)
    keyring.saveCustody(custody, grants())
    return ensureDeviceDoor(custody)
  }

  // --- Il passaggio fra telefoni (`b\chiavi.md` § 4.2) ------------------------------------------------------------

  /** Il telefono **nuovo**: chiede un numero al server, a corpo vuoto, e prepara il QR. */
  suspend fun beginTransfer(): TransferOffer {
    val r = client.call("POST", "/v1/keys/transfers", JsonObject(emptyMap()), session.storeToken())
    return TransferOffer.create((r["transferId"] as JsonPrimitive).content)
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

  /** Il telefono **nuovo** ritira, verifica, chiude nel suo Keystore e dimentica `n` e `s`. */
  suspend fun completeTransfer(offer: TransferOffer): KeyCustody {
    val r = client.call("GET", "/v1/keys/transfers/${offer.transferId}", token = session.storeToken())
    val seal = TransferSeal((r["sealed"] as JsonPrimitive).content, (r["mac"] as JsonPrimitive).content)
    try {
      val custody = KeyCustody.receiveTransfer(requireNotNull(accountId()), offer, seal, keysView())
      keyring.saveCustody(custody, grants())
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
    }
  }

  /** Al primo sblocco, o quando cambia la tabella delle app: ricava le chiavi di lavoro mancanti. Serve la madre. */
  fun refreshWorkKeys(): Boolean = try {
    keyring.loadCustody()?.let { keyring.refreshWorkKeys(it, grants()); true } ?: false
  } catch (_: CustodyLockedException) {
    false
  }
}
