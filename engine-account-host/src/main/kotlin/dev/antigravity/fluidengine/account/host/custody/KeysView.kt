package dev.antigravity.fluidengine.account.host.custody

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Il tipo di un'epoca (contratto `e2e.json`, `epochKinds`). Il tipo entra nella catena (F10.3). */
enum class EpochKind(val wire: String) {
  FIRST("first"), NORMAL("normal"), LOST("lost"), RESET("reset");

  companion object {
    fun of(wire: String?): EpochKind? = entries.firstOrNull { it.wire == wire }
  }
}

/**
 * Il tipo di una porta (contratto `e2e.json`, `doorKinds`): il codice di recupero, una passkey col PRF, o un dispositivo
 * già dentro ([DEVICE], `device:<deviceId>`: riceve le epoche nuove in silenzio, e non conta per K2).
 */
enum class DoorKind(val wire: String) {
  RECOVERY("recovery"), PRF("prf"), DEVICE("device");

  companion object {
    fun of(wire: String?): DoorKind? = entries.firstOrNull { it.wire == wire }
  }
}

/** Un'epoca come la racconta il server: il numero, il tipo, il collegamento alla precedente (`null` per la radice). */
data class EpochInfo(val epoch: Int, val kind: EpochKind, val link: String?)

/** Il sigillo HPKE della chiave dei dati di un'epoca per una porta, in base64url (`enc || testo cifrato`). */
data class SealInfo(val epoch: Int, val sealed: String)

/**
 * Una porta come sta sul server: la pubblica, il sale, il sigillo d'autenticità (K8) con la sua epoca di riferimento,
 * il controllo della radice (K9) e un sigillo per epoca. Tutto pubblico: nessun segreto della porta sta qui.
 */
data class DoorInfo(
  val doorId: String,
  val kind: DoorKind,
  val pub: String,
  val salt: String,
  val tag: String,
  val tagEpoch: Int,
  val check: String,
  val seals: List<SealInfo>,
) {
  /** Il corpo di `PUT /v1/keys/doors/{doorId}` e della porta dentro `POST /v1/recovery` (`b\api.md` § 4.5 e § 4.11). */
  fun toJson(): JsonObject = buildJsonObject {
    put("doorId", doorId)
    put("kind", kind.wire)
    put("pub", pub)
    put("salt", salt)
    put("tag", tag)
    put("tagEpoch", tagEpoch)
    put("check", check)
    putJsonArray("seals") {
      for (s in seals) addJsonObject {
        put("epoch", s.epoch)
        put("sealed", s.sealed)
      }
    }
  }
}

/**
 * Lo stato delle chiavi come lo dà `GET /v1/keys`: l'epoca attiva, la radice della catena, l'epoca di riferimento dei
 * sigilli d'autenticità, le epoche e le porte. **Il telefono non se ne fida**: lo verifica con [KeyCustody] (K8, K9),
 * e i numeri che contano (radice, riferimento) li ricalcola dalla catena.
 */
data class KeysView(
  val epoch: Int,
  val root: Int,
  val reference: Int,
  val epochs: List<EpochInfo>,
  val doors: List<DoorInfo>,
) {
  /** La cifratura è partita? Prima della prima epoca il server risponde con `epoch = 0`. */
  val started: Boolean get() = epoch > 0

  fun door(doorId: String): DoorInfo? = doors.firstOrNull { it.doorId == doorId }

  fun toJson(): JsonObject = buildJsonObject {
    put("epoch", epoch)
    put("root", root)
    put("reference", reference)
    putJsonArray("epochs") {
      for (e in epochs) addJsonObject {
        put("epoch", e.epoch)
        put("kind", e.kind.wire)
        put("link", e.link?.let(::JsonPrimitive) ?: JsonNull)
      }
    }
    putJsonArray("doors") { for (d in doors) add(d.toJson()) }
  }

  companion object {
    /** Legge la risposta del server; un campo che manca o è del tipo sbagliato è [CustodyTamperedException]. */
    fun parse(json: JsonObject): KeysView {
      fun bad(what: String): Nothing = throw CustodyTamperedException("risposta delle chiavi illeggibile: $what")
      fun JsonObject.int(k: String) = (this[k] as? JsonPrimitive)?.intOrNull ?: bad(k)
      fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: bad(k)
      val epochs = (json["epochs"] as? JsonArray ?: bad("epochs")).map { raw ->
        val o = raw as? JsonObject ?: bad("epoca")
        val link = o["link"].let { if (it == null || it is JsonNull) null else (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: bad("link") }
        EpochInfo(o.int("epoch"), EpochKind.of(o.str("kind")) ?: bad("kind"), link)
      }
      val doors = (json["doors"] as? JsonArray ?: bad("doors")).map { raw ->
        val o = raw as? JsonObject ?: bad("porta")
        DoorInfo(
          doorId = o.str("doorId"),
          kind = DoorKind.of(o.str("kind")) ?: bad("kind della porta"),
          pub = o.str("pub"),
          salt = o.str("salt"),
          tag = o.str("tag"),
          tagEpoch = o.int("tagEpoch"),
          check = o.str("check"),
          seals = (o["seals"] as? JsonArray ?: bad("seals")).map { s ->
            val so = s as? JsonObject ?: bad("sigillo")
            SealInfo(so.int("epoch"), so.str("sealed"))
          },
        )
      }
      return KeysView(json.int("epoch"), json.int("root"), json.int("reference"), epochs, doors)
    }
  }
}

/** Il corpo di `POST /v1/keys/epochs`: un'epoca nuova, il suo collegamento, un sigillo per ogni porta, e i tag nuovi dopo un furto. */
data class EpochUpload(
  val epoch: Int,
  val kind: EpochKind,
  val link: String?,
  val seals: List<Pair<String, String>>,
  val tags: List<Pair<String, String>> = emptyList(),
) {
  fun toJson(): JsonObject = buildJsonObject {
    put("epoch", epoch)
    put("kind", kind.wire)
    if (link != null) put("link", link)
    if (seals.isNotEmpty()) putJsonArray("seals") {
      for ((doorId, sealed) in seals) addJsonObject {
        put("doorId", doorId)
        put("sealed", sealed)
      }
    }
    if (tags.isNotEmpty()) putJsonArray("tags") {
      for ((doorId, tag) in tags) addJsonObject {
        put("doorId", doorId)
        put("tag", tag)
      }
    }
  }
}

/** Il corpo di `POST /v1/keys/reset`, «Ricomincia» (`b\chiavi.md` § 5): la conferma scritta, l'epoca nuova, le porte nuove. */
data class ResetUpload(val epoch: Int, val doors: List<DoorInfo>) {
  fun toJson(): JsonObject = buildJsonObject {
    put("confirm", "ricomincia")
    put("epoch", epoch)
    putJsonArray("doors") { for (d in doors) add(d.toJson()) }
  }
}
