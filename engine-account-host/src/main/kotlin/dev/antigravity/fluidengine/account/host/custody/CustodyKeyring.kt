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
    for (f in listOf(custodyFile, workFile)) if (f.exists()) {
      runCatching { f.writeBytes(ByteArray(f.length().toInt())) }
      f.delete()
    }
    vault.destroy()
  }

  private fun readSet(id: String): AppKeySet? {
    if (!workFile.exists()) return null
    val root = Json.parseToJsonElement(vault.open(workClass, workFile.readBytes(), aad("lavoro")).decodeToString()).jsonObject
    val set = root["sets"]!!.jsonArray.map { it.jsonObject }.firstOrNull { it["id"]!!.jsonPrimitive.content == id } ?: return null
    val keys = TreeMap<Int, ByteArray>()
    for (k in set["keys"]!!.jsonArray) keys[k.jsonObject["epoch"]!!.jsonPrimitive.int] = PampaBase64.decode(k.jsonObject["key"]!!.jsonPrimitive.content)
    return AppKeySet(set["current"]!!.jsonPrimitive.int, keys)
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

  private fun encodeCustody(c: KeyCustody): ByteArray = buildJsonObject {
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
  }.toString().toByteArray()

  private fun decodeCustody(bytes: ByteArray): KeyCustody {
    val o = runCatching { Json.parseToJsonElement(bytes.decodeToString()) as JsonObject }.getOrNull() ?: throw PampaCryptoException("custodia illeggibile")
    val epochs = (o["epochs"] as JsonArray).associate { it.jsonObject["epoch"]!!.jsonPrimitive.int to PampaBase64.decode(it.jsonObject["key"]!!.jsonPrimitive.content) }
    val doors = (o["doors"] as JsonArray).map { it.jsonPrimitive.content }.toSet()
    val device = (o["device"] as? JsonObject)?.let { DoorSecret.deviceFromStore(it["doorId"]!!.jsonPrimitive.content, PampaBase64.decode(it["secret"]!!.jsonPrimitive.content)) }
    return KeyCustody.restore(o["accountId"]!!.jsonPrimitive.content, epochs, o["root"]!!.jsonPrimitive.int, doors, device)
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
