package dev.antigravity.fluidengine.account.host.custody

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Il lato «chiavi» del server dell'account, in memoria, con le **stesse regole** del server vero
 * (`Pampa-account\src\routes\keys.ts`, `src\keys.ts`): l'epoca successiva e una sola, la prima solo con una porta (K2),
 * un sigillo per ogni porta e ogni epoca, il tag all'epoca di riferimento, la rotazione `lost` obbligatoria dopo un
 * furto coi tag nuovi, «Ricomincia» che riparte da una radice `reset`, il passaggio ritirato una volta sola.
 *
 * E sa fare il **server ostile** del banco (`b\chiavi.md` § 11, punto 2 e 10): [tamper] cambia quello che risponde
 * `GET /v1/keys` senza toccare il suo stato. Ogni corpo ricevuto passa da JSON (così si prova anche il formato) e
 * resta in [received] per il controllo «niente segreti».
 */
class FakeKeysServer(val accountId: String = "acc_bancoprova00000000000000") {
  private var keyEpoch = 0
  private val epochs = mutableListOf<EpochInfo>()
  private val doors = LinkedHashMap<String, DoorInfo>()
  private val transfers = HashMap<String, Transfer>()
  private var transferCounter = 0

  /** Un dispositivo è stato fatto uscire come «perso»: la prossima rotazione deve essere `lost`. */
  var lostPending = false

  /** Il server ostile: trasforma la risposta di `GET /v1/keys`. */
  var tamper: (KeysView) -> KeysView = { it }

  /** Tutti i corpi ricevuti, come testo, per il banco «niente segreti». */
  val received = mutableListOf<String>()

  class Refused(val reason: String) : Exception(reason)

  private class Transfer(var sealed: String? = null, var mac: String? = null, var taken: Boolean = false)

  private fun root() = epochs.lastOrNull { it.kind == EpochKind.FIRST || it.kind == EpochKind.RESET }?.epoch ?: 1
  private fun reference() = epochs.lastOrNull { it.kind != EpochKind.NORMAL }?.epoch ?: 1
  private fun chain() = epochs.filter { it.epoch >= root() }.map { it.epoch }

  /** La verità del server, senza il server ostile. */
  fun honestView(): KeysView = KeysView(keyEpoch, root(), reference(), epochs.filter { it.epoch >= root() }, doors.values.toList())

  /** `GET /v1/keys`, passato per JSON e per [tamper]. */
  fun view(): KeysView = KeysView.parse(Json.parseToJsonElement(tamper(honestView()).toJson().toString()) as JsonObject)

  private fun <T> roundTrip(json: JsonObject, parse: (JsonObject) -> T): T {
    val text = json.toString()
    received += text
    return parse(Json.parseToJsonElement(text) as JsonObject)
  }

  /** `PUT /v1/keys/doors/{doorId}` (e la porta dentro `POST /v1/recovery`). */
  fun putDoor(door: DoorInfo) {
    val d = roundTrip(door.toJson()) { parseDoor(it) }
    val required = if (keyEpoch == 0) listOf(1) else chain()
    if (d.seals.map { it.epoch }.sorted() != required) throw Refused("seals_mismatch")
    if (d.tagEpoch != (if (keyEpoch == 0) 1 else reference())) throw Refused("tag_epoch")
    doors[d.doorId] = d
  }

  /** `POST /v1/recovery` con la porta: `auth` e porta insieme. */
  fun postRecovery(auth: String, door: DoorInfo?) {
    received += """{"auth":"$auth"}"""
    if (door != null) putDoor(door)
  }

  /** `DELETE /v1/keys/doors/{doorId}`: mai l'ultima porta vera (codice o passkey) a cifratura partita. */
  fun deleteDoor(doorId: String) {
    val real = doors.values.filter { it.kind != DoorKind.DEVICE }
    if (keyEpoch > 0 && real.size == 1 && real.single().doorId == doorId) throw Refused("last_door")
    doors.remove(doorId)
  }

  /** Un dispositivo esce o è fatto uscire: la sua porta se ne va coi sigilli, nello stesso istante (F10.5, `porte-dispositivi.md`). */
  fun dropDevice(deviceId: String) {
    doors.remove(DoorSecret.deviceDoorId(deviceId))
  }

  /** `POST /v1/keys/epochs`. */
  fun postEpoch(upload: EpochUpload) {
    val u = roundTrip(upload.toJson()) { parseEpoch(it) }
    if (u.epoch != keyEpoch + 1) throw Refused("epoch_changed")
    if (keyEpoch == 0) {
      if (u.kind != EpochKind.FIRST || u.link != null) throw Refused("bad_request")
      // K2: le porte dei dispositivi non contano.
      if (doors.values.none { it.kind != DoorKind.DEVICE }) throw Refused("no_door")
    } else {
      if (u.kind != EpochKind.NORMAL && u.kind != EpochKind.LOST) throw Refused("bad_request")
      if (lostPending && u.kind != EpochKind.LOST) throw Refused("lost_rotation_required")
      if (doors.keys.any { id -> u.seals.none { it.first == id } }) throw Refused("doors")
      if (u.kind == EpochKind.LOST && doors.keys.any { id -> u.tags.none { it.first == id } }) throw Refused("doors")
    }
    epochs += EpochInfo(u.epoch, u.kind, u.link)
    for ((id, sealed) in u.seals) {
      val d = doors[id] ?: throw Refused("not_found")
      doors[id] = d.copy(seals = d.seals.filter { it.epoch != u.epoch } + SealInfo(u.epoch, sealed))
    }
    for ((id, tag) in u.tags) doors[id] = doors.getValue(id).copy(tag = tag, tagEpoch = u.epoch)
    if (u.kind == EpochKind.LOST) lostPending = false
    keyEpoch = u.epoch
  }

  /** `POST /v1/keys/reset`, «Ricomincia»: via epoche e porte vecchie, dentro la catena nuova. */
  fun reset(upload: ResetUpload) {
    val json = upload.toJson()
    received += json.toString()
    if ((json["confirm"] as kotlinx.serialization.json.JsonPrimitive).content != "ricomincia") throw Refused("confirm")
    if (upload.epoch != keyEpoch + 1) throw Refused("epoch_changed")
    epochs.clear()
    doors.clear()
    epochs += EpochInfo(upload.epoch, EpochKind.RESET, null)
    keyEpoch = upload.epoch
    for (d in upload.doors) {
      if (d.seals.map { it.epoch } != listOf(upload.epoch) || d.tagEpoch != upload.epoch) throw Refused("seals_mismatch")
      doors[d.doorId] = d
    }
  }

  /** `POST /v1/keys/transfers`: corpo vuoto, niente chiavi. */
  fun openTransfer(): String {
    received += "{}"
    val id = "xfr_${++transferCounter}"
    transfers[id] = Transfer()
    return id
  }

  /** `POST /v1/keys/transfers/{id}/seal`. */
  fun sealTransfer(id: String, seal: TransferSeal) {
    received += """{"sealed":"${seal.sealed}","mac":"${seal.mac}"}"""
    val t = transfers[id] ?: throw Refused("not_found")
    t.sealed = seal.sealed
    t.mac = seal.mac
  }

  /** `GET /v1/keys/transfers/{id}`: una volta sola. */
  fun takeTransfer(id: String): TransferSeal {
    val t = transfers[id] ?: throw Refused("not_found")
    if (t.taken || t.sealed == null) throw Refused("not_ready")
    t.taken = true
    return TransferSeal(t.sealed!!, t.mac!!).also { t.sealed = null }
  }

  /** Il server ostile cambia lo stato vero (non solo la risposta): per le prove dove il danno resta. */
  fun corruptDoor(doorId: String, change: (DoorInfo) -> DoorInfo) {
    doors[doorId] = change(doors.getValue(doorId))
  }

  private fun parseDoor(o: JsonObject): DoorInfo =
    KeysView.parse(Json.parseToJsonElement("""{"epoch":0,"root":1,"reference":1,"epochs":[],"doors":[$o]}""") as JsonObject).doors.single()

  private fun parseEpoch(o: JsonObject): EpochUpload {
    fun list(name: String, field: String) = (o[name] as? kotlinx.serialization.json.JsonArray)?.map {
      val x = it as JsonObject
      (x["doorId"] as kotlinx.serialization.json.JsonPrimitive).content to (x[field] as kotlinx.serialization.json.JsonPrimitive).content
    } ?: emptyList()
    val link = (o["link"] as? kotlinx.serialization.json.JsonPrimitive)?.content
    link?.let { require(PampaBase64.decode(it).size == 32) }
    return EpochUpload(
      (o["epoch"] as kotlinx.serialization.json.JsonPrimitive).content.toInt(),
      EpochKind.of((o["kind"] as kotlinx.serialization.json.JsonPrimitive).content)!!,
      link,
      list("seals", "sealed"),
      list("tags", "tag"),
    )
  }
}
