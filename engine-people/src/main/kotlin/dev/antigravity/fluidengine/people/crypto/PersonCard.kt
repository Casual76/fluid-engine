package dev.antigravity.fluidengine.people.crypto

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Le chiavi di lavoro pubbliche di una persona a un'epoca (`b\chiavi.md` § 12): X25519 per cifrare ([enc]) ed Ed25519
 * per firmare ([sign]), con la firma della radice ([sig]) su `"pampa/v1/chiavi-di-lavoro|<epoch>|" ‖ enc ‖ sign`.
 */
class WorkPublic(val epoch: Int, val enc: ByteArray, val sign: ByteArray, val sig: ByteArray) {
  init {
    require(epoch >= 1) { "epoca di lavoro non valida" }
    require(enc.size == 32 && sign.size == 32 && sig.size == 64) { "chiavi di lavoro non valide" }
  }

  /** La radice [root] ha firmato queste chiavi? */
  fun signedBy(root: ByteArray): Boolean = Ed25519Key.verify(root, signatureInput(epoch, enc, sign), sig)

  override fun equals(other: Any?): Boolean =
    other is WorkPublic && other.epoch == epoch && other.enc.contentEquals(enc) && other.sign.contentEquals(sign) && other.sig.contentEquals(sig)

  override fun hashCode(): Int = (epoch * 31 + enc.contentHashCode()) * 31 + sign.contentHashCode()

  companion object {
    fun signatureInput(epoch: Int, enc: ByteArray, sign: ByteArray): ByteArray =
      PeopleLabels.utf8(PeopleLabels.WORK_KEYS, "epoch" to epoch) + enc + sign
  }
}

/**
 * Le chiavi di lavoro con le private: stanno nella classe «consegna» della custodia (Q7 A), così le buste si aprono e
 * si firmano anche col telefono in tasca. Cambiano solo con un «perso o rubato» o la rotazione dei sei mesi.
 */
class WorkKeys(val epoch: Int, val enc: X25519Key, val sign: Ed25519Key, val sig: ByteArray) {
  val public: WorkPublic get() = WorkPublic(epoch, enc.publicKey, sign.publicKey, sig)

  companion object {
    /** Chiavi nuove per l'epoca [epoch], firmate da [root]. Serve la radice: si fa a telefono sbloccato. */
    fun create(root: Ed25519Key, epoch: Int): WorkKeys {
      val enc = X25519Key.random()
      val sign = Ed25519Key.random()
      return WorkKeys(epoch, enc, sign, root.sign(WorkPublic.signatureInput(epoch, enc.publicKey, sign.publicKey)))
    }
  }
}

/**
 * La scheda pubblica di una persona (`d\server-e-chiavi.md` § 4; contratto `people.formulas.cardSig`), firmata dalla
 * radice: codice, nome, seme del minerale, radice, casella, chiavi di lavoro, ora. Niente email, telefono, età o app.
 *
 * Sono byte JSON firmati e mandati così come sono, in base64url ([raw]): niente canonicalizzazione. Il server li
 * controlla alla pubblicazione, e il telefono li ricontrolla **sempre** ([verify]), da qualunque parte arrivino: dal
 * server, da una busta, da un QR o dalla vicinanza.
 */
class PersonCard private constructor(
  val code: String,
  val name: String,
  /** Il seme del minerale, l'avatar che nasce da un seme come nell'alpha: 16 byte. */
  val mineral: ByteArray,
  val root: ByteArray,
  val mailbox: String,
  val work: WorkPublic,
  val at: Long,
  /** I byte firmati, in base64url, così come sono arrivati. */
  val raw: String,
  /** La firma della radice, in base64url. */
  val sig: String,
) {
  /** La radice in base64url, come sta nella scheda. */
  val rootB64: String get() = PampaBase64.encode(root)

  /** `{ "card": raw, "sig": sig }`, come la vuole `PUT /v1/people/card` e come viaggia nelle buste. */
  fun toJson(): JsonObject = buildJsonObject {
    put("card", raw)
    put("sig", sig)
  }

  override fun equals(other: Any?): Boolean = other is PersonCard && other.raw == raw && other.sig == sig
  override fun hashCode(): Int = raw.hashCode()
  override fun toString(): String = "PersonCard($code, «$name», lavoro ${work.epoch})"

  companion object {
    const val MAX_NAME = 40
    const val MAX_BYTES = 2048

    /** Scrive e firma la scheda di chi ha [root]. */
    fun sign(root: Ed25519Key, name: String, mineral: ByteArray, work: WorkPublic, at: Long): PersonCard {
      require(validName(name)) { "nome non valido per la scheda" }
      require(mineral.size == 16) { "il seme del minerale è di 16 byte" }
      require(work.signedBy(root.publicKey)) { "le chiavi di lavoro non sono di questa radice" }
      val json = buildJsonObject {
        put("v", 1)
        put("code", PersonCode.of(root.publicKey))
        put("name", name)
        put("mineral", PampaBase64.encode(mineral))
        put("root", PampaBase64.encode(root.publicKey))
        put("mailbox", PersonCode.mailboxOf(root.publicKey))
        putJsonObject("work") {
          put("epoch", work.epoch)
          put("enc", PampaBase64.encode(work.enc))
          put("sign", PampaBase64.encode(work.sign))
          put("sig", PampaBase64.encode(work.sig))
        }
        put("at", at)
      }
      val bytes = json.toString().toByteArray(Charsets.UTF_8)
      val raw = PampaBase64.encode(bytes)
      val sig = PampaBase64.encode(root.sign(PeopleLabels.CARD.toByteArray(Charsets.UTF_8) + bytes))
      return verify(raw, sig)
    }

    /**
     * Legge e controlla una scheda: forma, codice e casella ricavati dalla radice, firma della scheda e delle chiavi di
     * lavoro. Con [expectCode], [expectMailbox] o [expectRoot] controlla anche che sia **quella** persona: un server
     * che rende la scheda di un altro, o che cambia la radice di un contatto, viene scoperto. Ogni problema è
     * [PeopleDetected].
     */
    fun verify(raw: String, sig: String, expectCode: String? = null, expectMailbox: String? = null, expectRoot: ByteArray? = null): PersonCard {
      val bytes = runCatching { PampaBase64.decode(raw) }.getOrNull() ?: throw PeopleDetected("scheda illeggibile")
      if (bytes.size > MAX_BYTES) throw PeopleDetected("scheda troppo grande")
      val o = runCatching { Json.parseToJsonElement(bytes.decodeToString()) as JsonObject }.getOrNull() ?: throw PeopleDetected("scheda illeggibile")
      fun str(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw PeopleDetected("scheda senza «$k»")
      fun b64(v: String, n: Int) = runCatching { PampaBase64.decode(v) }.getOrNull()?.takeIf { it.size == n } ?: throw PeopleDetected("scheda con un campo non valido")
      if ((o["v"] as? JsonPrimitive)?.intOrNull != 1) throw PeopleDetected("scheda di un'altra versione")
      val name = str("name")
      if (!validName(name)) throw PeopleDetected("scheda con un nome non valido")
      val root = b64(str("root"), 32)
      val code = str("code")
      val mailbox = str("mailbox")
      if (code != PersonCode.of(root)) throw PeopleDetected("codice che non viene dalla radice")
      if (mailbox != PersonCode.mailboxOf(root)) throw PeopleDetected("casella che non viene dalla radice")
      if (expectCode != null && code != expectCode) throw PeopleDetected("scheda con un altro codice")
      if (expectMailbox != null && mailbox != expectMailbox) throw PeopleDetected("scheda di un'altra casella")
      if (expectRoot != null && !root.contentEquals(expectRoot)) throw PeopleDetected("radice cambiata")
      val sigBytes = runCatching { PampaBase64.decode(sig) }.getOrNull() ?: throw PeopleDetected("firma della scheda")
      if (!Ed25519Key.verify(root, PeopleLabels.CARD.toByteArray(Charsets.UTF_8) + bytes, sigBytes)) throw PeopleDetected("firma della scheda")
      val w = o["work"] as? JsonObject ?: throw PeopleDetected("scheda senza chiavi di lavoro")
      val epoch = (w["epoch"] as? JsonPrimitive)?.intOrNull?.takeIf { it >= 1 } ?: throw PeopleDetected("epoca di lavoro non valida")
      fun wstr(k: String) = (w[k] as? JsonPrimitive)?.content ?: throw PeopleDetected("chiavi di lavoro incomplete")
      val work = WorkPublic(epoch, b64(wstr("enc"), 32), b64(wstr("sign"), 32), b64(wstr("sig"), 64))
      if (!work.signedBy(root)) throw PeopleDetected("chiavi di lavoro non firmate dalla radice")
      val at = (o["at"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 } ?: throw PeopleDetected("scheda senza ora")
      return PersonCard(code, name, b64(str("mineral"), 16), root, mailbox, work, at, raw, sig)
    }

    /** La scheda di un JSON `{ card, sig }`. */
    fun fromJson(o: JsonObject, expectMailbox: String? = null, expectRoot: ByteArray? = null): PersonCard =
      verify(o["card"]?.jsonPrimitive?.content ?: throw PeopleDetected("scheda assente"), o["sig"]?.jsonPrimitive?.content ?: throw PeopleDetected("firma assente"), expectMailbox = expectMailbox, expectRoot = expectRoot)

    /** Le regole del nome del server: da 1 a 40 caratteri, niente caratteri di controllo. */
    fun validName(name: String): Boolean =
      name.trim().isNotEmpty() && name.length <= MAX_NAME && name.none { it.code in 0..0x1f || it.code in 0x7f..0x9f || it == ' ' || it == ' ' }
  }
}
