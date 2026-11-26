package dev.antigravity.fluidengine.account.host.custody

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.crypto.PampaCryptoException
import java.io.File
import java.util.TreeMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Quali chiavi di lavoro tenere pronte per un'app: lo dice la tabella delle app (`AppPolicy`). */
data class WorkKeyGrant(val packageName: String, val purposes: Set<AppKeyPurpose>)

/**
 * La custodia sul telefono, nelle due classi (`b\chiavi.md` § 3.3, K10):
 * - `custodia.bin`: la chiave dei dati di tutte le epoche e le porte conosciute, nella classe di
 *   [CustodyKeyKind.DATA_KEY] (madre, con la raccomandazione A di Q7);
 * - `lavoro.bin`: le chiavi di lavoro già ricavate (`K_app`, `K_bak` delle app ammesse, `K_com` dei luoghi, della
 *   rubrica e della persona), nella classe di [CustodyKeyKind.WORK_KEYS] (consegna).
 *
 * Le chiavi di lavoro si ricavano a telefono sbloccato ([saveCustody], [refreshWorkKeys]) e si leggono anche a
 * telefono bloccato ([workKeys]): così il sottofondo non apre mai la madre. I file stanno in una cartella fuori dal
 * backup di Android (`noBackupFilesDir`), che chi costruisce il portachiavi passa in [dir].
 */
class CustodyKeyring(
  private val vault: CustodyVault,
  private val dir: File,
  private val choice: KeyClassPolicy.Choice = KeyClassPolicy.CHOICE,
) {
  private val custodyFile get() = File(dir, "custodia.bin")
  private val workFile get() = File(dir, "lavoro.bin")

  private val dataClass get() = KeyClassPolicy.classOf(CustodyKeyKind.DATA_KEY, choice)
  private val workClass get() = KeyClassPolicy.classOf(CustodyKeyKind.WORK_KEYS, choice)

  /** C'è una custodia su questo telefono? Non apre niente. */
  val hasCustody: Boolean get() = custodyFile.exists()

  /** Chiude la custodia (classe madre) e ricava subito le chiavi di lavoro per [grants]. Va fatto a telefono sbloccato. */
  fun saveCustody(custody: KeyCustody, grants: List<WorkKeyGrant>) {
    dir.mkdirs()
    write(custodyFile, vault.seal(dataClass, encodeCustody(custody), aad("custodia")))
    refreshWorkKeys(custody, grants)
  }

  /** Apre la custodia; [CustodyLockedException] a telefono bloccato, [CustodyKeyLostException] se il Keystore l'ha persa. */
  fun loadCustody(): KeyCustody? {
    if (!custodyFile.exists()) return null
    return decodeCustody(vault.open(dataClass, custodyFile.readBytes(), aad("custodia")))
  }

  /**
   * Ricava e chiude le chiavi di lavoro delle app di [grants] e quelle comuni dello Store. Si rifà a ogni epoca nuova e
   * quando la tabella delle app cambia; un'app nuova chiede la sua chiave e trova `locked` finché non si ricavano.
   */
  fun refreshWorkKeys(custody: KeyCustody, grants: List<WorkKeyGrant>) {
    dir.mkdirs()
    val json = buildJsonObject {
      put("current", custody.current)
      putJsonArray("sets") {
        for (g in grants) for (p in g.purposes) addSet("app:${p.wire}:${g.packageName}", custody.appKeys(g.packageName, p))
        for (name in SHARED) addSet("shared:$name", custody.sharedKeys(name))
      }
    }
    write(workFile, vault.seal(workClass, json.toString().toByteArray(), aad("lavoro")))
  }

  /**
   * Le chiavi di lavoro di [packageName] per [purpose], o `null` se non sono mai state ricavate (l'app è nuova, o la
   * cifratura non è partita). Usa solo la classe delle chiavi di lavoro: con la raccomandazione A di Q7 funziona anche
   * a telefono bloccato.
   */
  fun workKeys(packageName: String, purpose: AppKeyPurpose): AppKeySet? = readSet("app:${purpose.wire}:$packageName")

  /** Le chiavi comuni (`places`, `rubrica`, `persona`) ricavate per lo Store. */
  fun sharedKeys(name: String): AppKeySet? = readSet("shared:$name")

  /** Uscita, uscita da remoto, «perso»: via i file e le chiavi del Keystore (`b\chiavi.md` § 4.4). */
  fun wipe() {
    for (f in listOf(custodyFile, workFile, pendingFile)) if (f.exists()) {
      runCatching { f.writeBytes(ByteArray(f.length().toInt())) }
      f.delete()
    }
    vault.destroy()
  }

  // --- R2 · Il lavoro rimasto a metà ----------------------------------------------------------------------------

  private val pendingFile get() = File(dir, "in-sospeso.bin")

  /**
   * Chiude [pending] in classe madre **prima** che le sue richieste partano: il codice di recupero nuovo, la prima
   * custodia e i corpi già pronti. Un'interruzione dopo che il server ha detto sì non perde più niente.
   */
  internal fun savePending(pending: CustodyPending) {
    dir.mkdirs()
    write(pendingFile, vault.seal(dataClass, encodePending(pending).toString().toByteArray(), aad("in-sospeso")))
  }

  /** Il lavoro rimasto a metà, o `null`. Un file che si apre ma non si legge è perso: `null`, e si riparte. */
  internal fun loadPending(): CustodyPending? {
    if (!pendingFile.exists()) return null
    val bytes = vault.open(dataClass, pendingFile.readBytes(), aad("in-sospeso"))
    return runCatching { decodePending(Json.parseToJsonElement(bytes.decodeToString()) as JsonObject) }.getOrNull()
  }

  internal fun clearPending() {
    if (pendingFile.exists()) {
      runCatching { pendingFile.writeBytes(ByteArray(pendingFile.length().toInt())) }
      pendingFile.delete()
    }
  }

  private fun encodePending(p: CustodyPending): JsonObject = buildJsonObject {
    put("v", 1)
    put("accountId", p.accountId)
    p.code?.let { put("code", PampaBase64.encode(it.secret())) }
    put("shown", p.shown)
    p.custody?.let { put("custody", custodyJson(it)) }
    putJsonArray("requests") {
      for (r in p.requests) addJsonObject {
        put("method", r.method)
        put("path", r.path)
        put("body", r.body)
        put("key", r.idempotencyKey)
      }
    }
  }

  private fun decodePending(o: JsonObject): CustodyPending {
    fun str(x: JsonObject, k: String) = (x[k] as JsonPrimitive).content
    return CustodyPending(
      accountId = str(o, "accountId"),
      code = (o["code"] as? JsonPrimitive)?.let { RecoveryCode.fromBytes(PampaBase64.decode(it.content)) },
      shown = (o["shown"] as JsonPrimitive).content.toBooleanStrict(),
      custody = (o["custody"] as? JsonObject)?.let(::custodyFrom),
      requests = (o["requests"] as JsonArray).map {
        val r = it as JsonObject
        PendingRequest(str(r, "method"), str(r, "path"), r["body"] as JsonObject, str(r, "key"))
      },
    )
  }

  /**
   * Un `lavoro.bin` che si apre ma ha una forma che non si legge (un engine di un'altra versione, un file rimesso a
   * mano) è [PampaCryptoException]: chi chiede le chiavi lo tratta come «non pronte», e si ricavano di nuovo.
   */
  private fun readSet(id: String): AppKeySet? {
    if (!workFile.exists()) return null
    val bytes = vault.open(workClass, workFile.readBytes(), aad("lavoro"))
    return try {
      val root = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
      val set = root["sets"]!!.jsonArray.map { it.jsonObject }.firstOrNull { it["id"]!!.jsonPrimitive.content == id } ?: return null
      val keys = TreeMap<Int, ByteArray>()
      for (k in set["keys"]!!.jsonArray) keys[k.jsonObject["epoch"]!!.jsonPrimitive.int] = PampaBase64.decode(k.jsonObject["key"]!!.jsonPrimitive.content)
      if (keys.isEmpty()) throw PampaCryptoException("chiavi di lavoro senza epoche")
      AppKeySet(set["current"]!!.jsonPrimitive.int, keys)
    } catch (e: PampaCryptoException) {
      throw e
    } catch (e: RuntimeException) {
      throw PampaCryptoException("chiavi di lavoro illeggibili", e)
    }
  }

  private fun kotlinx.serialization.json.JsonArrayBuilder.addSet(id: String, set: AppKeySet) = addJsonObject {
    put("id", id)
    put("current", set.current)
    putJsonArray("keys") {
      for ((e, k) in set.keys) addJsonObject {
        put("epoch", e)
        put("key", PampaBase64.encode(k))
      }
    }
  }

  private fun encodeCustody(c: KeyCustody): ByteArray = custodyJson(c).toString().toByteArray()

  private fun custodyJson(c: KeyCustody): JsonObject = buildJsonObject {
    put("v", 1)
    put("accountId", c.accountId)
    put("root", c.rootOfChain())
    putJsonArray("doors") { for (d in c.knownDoors) add(JsonPrimitive(d)) }
    // Il segreto della porta di questo dispositivo sta qui, in classe madre (`b\chiavi.md` § 3.3): mai altrove.
    c.deviceDoorId?.let { id -> putJsonObject("device") { put("doorId", id); put("secret", PampaBase64.encode(c.deviceSecret()!!)) } }
    putJsonArray("epochs") {
      for ((e, k) in c.copyEpochs()) addJsonObject {
        put("epoch", e)
        put("key", PampaBase64.encode(k))
      }
    }
  }

  private fun decodeCustody(bytes: ByteArray): KeyCustody {
    val o = runCatching { Json.parseToJsonElement(bytes.decodeToString()) as JsonObject }.getOrNull() ?: throw PampaCryptoException("custodia illeggibile")
    return custodyFrom(o)
  }

  /** R2 · Una forma inattesa è [PampaCryptoException] («custodia illeggibile»), mai un `NullPointerException`. */
  private fun custodyFrom(o: JsonObject): KeyCustody = try {
    val epochs = (o["epochs"] as JsonArray).associate { it.jsonObject["epoch"]!!.jsonPrimitive.int to PampaBase64.decode(it.jsonObject["key"]!!.jsonPrimitive.content) }
    if (epochs.isEmpty()) throw PampaCryptoException("custodia senza epoche")
    val doors = (o["doors"] as JsonArray).map { it.jsonPrimitive.content }.toSet()
    val device = (o["device"] as? JsonObject)?.let { DoorSecret.deviceFromStore(it["doorId"]!!.jsonPrimitive.content, PampaBase64.decode(it["secret"]!!.jsonPrimitive.content)) }
    KeyCustody.restore(o["accountId"]!!.jsonPrimitive.content, epochs, o["root"]!!.jsonPrimitive.int, doors, device)
  } catch (e: PampaCryptoException) {
    throw e
  } catch (e: RuntimeException) {
    throw PampaCryptoException("custodia illeggibile", e)
  }

  /** Scrive prima in un file accanto e poi lo sposta: un'interruzione a metà non lascia un file mezzo scritto. */
  private fun write(target: File, bytes: ByteArray) {
    val tmp = File(target.parentFile, target.name + ".nuovo")
    tmp.writeBytes(bytes)
    if (!tmp.renameTo(target)) {
      target.delete()
      check(tmp.renameTo(target)) { "non riesco a scrivere ${target.name}" }
    }
  }

  private fun aad(name: String) = "pampa/v1/custody|$name".toByteArray()

  companion object {
    /** Le chiavi comuni che lo Store tiene pronte (`b\chiavi.md` § 3). */
    val SHARED = listOf("places", "rubrica", "persona")
  }
}

/**
 * R2 · Un lavoro della custodia che parte verso il server e non deve perdersi a metà (`CustodyService`): il codice di
 * recupero nuovo, finché l'utente non l'ha visto ([shown]); la prima custodia, finché il server non l'ha presa tutta
 * ([custody]); e le richieste già pronte, ognuna con la sua chiave d'idempotenza ([requests]), da rimandare **uguali**.
 * Sta in `in-sospeso.bin`, in classe madre come la custodia.
 */
internal class CustodyPending(
  val accountId: String,
  val code: RecoveryCode?,
  val shown: Boolean,
  val custody: KeyCustody?,
  val requests: List<PendingRequest>,
) {
  /** Resta qualcosa da mandare o da tenere in custodia. */
  val unfinished: Boolean get() = custody != null || requests.isNotEmpty()

  fun sent(): CustodyPending = CustodyPending(accountId, code, shown, null, emptyList())

  fun seen(): CustodyPending = CustodyPending(accountId, code, true, custody, requests)

  /** Non resta niente: né un codice da mostrare né un invio da finire. */
  val empty: Boolean get() = !unfinished && (code == null || shown)

  override fun toString(): String = "CustodyPending(code=${code != null}, shown=$shown, custody=${custody != null}, requests=${requests.size})"
}

/** Una richiesta già pronta: metodo, percorso, corpo (solo dati pubblici: `auth`, pubbliche, sali, sigilli) e chiave d'idempotenza. */
internal class PendingRequest(val method: String, val path: String, val body: JsonObject, val idempotencyKey: String)
