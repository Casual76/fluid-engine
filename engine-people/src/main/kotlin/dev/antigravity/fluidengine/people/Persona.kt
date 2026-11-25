package dev.antigravity.fluidengine.people

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import dev.antigravity.fluidengine.account.crypto.PampaCryptoException
import dev.antigravity.fluidengine.account.crypto.PampaEnvelope
import dev.antigravity.fluidengine.account.crypto.PampaLabels
import dev.antigravity.fluidengine.people.crypto.Ed25519Key
import dev.antigravity.fluidengine.people.crypto.PersonCard
import dev.antigravity.fluidengine.people.crypto.WorkKeys
import dev.antigravity.fluidengine.people.crypto.X25519Key
import java.io.File
import java.util.TreeMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Le due classi della custodia per la persona (`b\chiavi.md` § 3.3 e § 12; Q7, risposta A di Alessio):
 * - [ROOT], la radice Ed25519: classe **madre**, aperta solo a telefono sbloccato. Serve solo per le cose rare:
 *   firmare la scheda e le chiavi di lavoro nuove;
 * - [WORK], le chiavi di lavoro, la rubrica e la posta: classe **consegna**, aperta dal primo sblocco. Così il
 *   campanello apre le buste e la coda in uscita firma anche col telefono in tasca.
 */
enum class PersonKeyRole { ROOT, WORK }

/**
 * La cassaforte della persona. Lo Store la collega alla sua custodia (`CustodyVault` di `engine-account-host`), con
 * [PersonKeyRole.ROOT] nella classe di `CustodyKeyKind.PERSON_ROOT` e [PersonKeyRole.WORK] in quella di
 * `CustodyKeyKind.WORK_KEYS`: la classe di ogni chiave resta una costante sola, `KeyClassPolicy`. Pampa Mail non
 * la usa: non ha la persona.
 */
interface PersonVault {
  fun seal(role: PersonKeyRole, plaintext: ByteArray, aad: ByteArray): ByteArray

  /** Apre ciò che ha chiuso [seal]; [PersonLockedException] se la classe è chiusa (la madre a telefono bloccato). */
  fun open(role: PersonKeyRole, sealed: ByteArray, aad: ByteArray): ByteArray
}

/** La classe che serve è chiusa: la radice a telefono bloccato, o tutto prima del primo sblocco. Si riprova dopo. */
class PersonLockedException(message: String = "la custodia della persona è chiusa") : Exception(message)

/** Chi sono, per le persone: la scheda e le chiavi di lavoro di ogni epoca. Niente radice: si legge anche in tasca. */
class PersonIdentity(val card: PersonCard, val works: Map<Int, WorkKeys>) {
  val work: WorkKeys get() = works.getValue(card.work.epoch)
  val code: String get() = card.code
  val mailbox: String get() = card.mailbox
}

/**
 * La persona sul telefono (`d\server-e-chiavi.md` § 3; `b\chiavi.md` § 12). Nasce la prima volta che apri «Persone» o
 * condividi con qualcuno ([create], `progetto.md` § 3.5): prima di allora non esiste e non si pubblica niente (R3).
 *
 * Due file nella cartella privata, fuori dal backup di Android:
 * - `persona-radice.bin`, la radice, chiusa in [PersonKeyRole.ROOT];
 * - `persona-lavoro.bin`, nome, minerale, scheda e chiavi di lavoro di ogni epoca, chiusi in [PersonKeyRole.WORK].
 *
 * Le chiavi di lavoro cambiano solo con un «perso o rubato» e con la rotazione dei sei mesi ([rotateWork]): la radice
 * resta, quindi il codice e la casella restano, e i contatti non devono rifare la verifica di persona.
 */
class PersonaStore(private val dir: File, private val vault: PersonVault, private val clock: () -> Long = System::currentTimeMillis) {
  private val rootFile get() = File(dir, "persona-radice.bin")
  private val workFile get() = File(dir, "persona-lavoro.bin")

  @Volatile private var cached: PersonIdentity? = null

  /** C'è una persona su questo telefono? Non apre niente. */
  val exists: Boolean get() = workFile.exists()

  /** La persona nuova: radice, chiavi di lavoro dell'epoca 1, scheda firmata. Serve la classe madre. */
  fun create(name: String, mineral: ByteArray = PampaCrypto.random(16)): PersonIdentity {
    check(!exists) { "la persona c'è già" }
    val root = Ed25519Key.random()
    val work = WorkKeys.create(root, 1)
    val card = PersonCard.sign(root, name, mineral, work.public, clock())
    saveRoot(root)
    return saveWork(card, mapOf(1 to work))
  }

  /** La persona di questo telefono; [PersonLockedException] prima del primo sblocco, `null` se non c'è. */
  fun identity(): PersonIdentity? {
    cached?.let { return it }
    if (!exists) return null
    val o = Json.parseToJsonElement(vault.open(PersonKeyRole.WORK, workFile.readBytes(), AAD_WORK).decodeToString()).jsonObject
    return decodeWork(o).also { cached = it }
  }

  /** La radice; [PersonLockedException] a telefono bloccato. Solo per firmare. */
  fun root(): Ed25519Key {
    check(rootFile.exists()) { "nessuna radice su questo telefono" }
    val bytes = vault.open(PersonKeyRole.ROOT, rootFile.readBytes(), AAD_ROOT)
    return Ed25519Key.fromPrivate(bytes).also { bytes.fill(0) }
  }

  /** Il nome nuovo (scelto nello Store): scheda nuova, stessa radice. */
  fun rename(name: String): PersonIdentity {
    val me = requireNotNull(identity())
    val card = PersonCard.sign(root(), name, me.card.mineral, me.work.public, maxOf(clock(), me.card.at + 1))
    return saveWork(card, me.works)
  }

  /**
   * «Perso o rubato», o i sei mesi: chiavi di lavoro nuove firmate dalla radice, all'epoca dopo. Le vecchie si tengono
   * per aprire le buste già arrivate; quelle che arrivano dopo sono per le nuove, che il telefono rubato non ha.
   */
  fun rotateWork(): PersonIdentity {
    val me = requireNotNull(identity())
    val root = root()
    val next = me.works.keys.max() + 1
    val work = WorkKeys.create(root, next)
    val card = PersonCard.sign(root, me.card.name, me.card.mineral, work.public, maxOf(clock(), me.card.at + 1))
    return saveWork(card, me.works + (next to work))
  }

  /** Uscita, uscita da remoto o eliminazione: via la persona da questo telefono. Il server la tiene sotto `K_com`. */
  fun wipe() {
    for (f in listOf(rootFile, workFile)) if (f.exists()) {
      runCatching { f.writeBytes(ByteArray(f.length().toInt())) }
      f.delete()
    }
    cached = null
  }

  // --- La copia sotto K_com(persona) --------------------------------------------------------------------------------

  /**
   * La persona chiusa per il server sotto `K_com(persona)` all'epoca [epoch] della chiave dei dati: così un telefono
   * nuovo, che apre la chiave dei dati con una porta, ritrova anche la persona (`b\chiavi.md` § 12: le private sono
   * protette dalle stesse porte). È la busta del contratto `{ v, e, alg, n, c }`, coi dati associati
   * `accountId|com.pampa.store|persona|chiavi|<versione>`.
   */
  fun exportSealed(accountId: String, epoch: Int, kPersona: ByteArray, version: Long): String {
    val me = requireNotNull(identity())
    val plain = buildJsonObject {
      put("v", 1)
      put("root", PampaBase64.encode(root().privateBytes()))
      put("state", encodeWork(me))
    }.toString().toByteArray()
    return PampaEnvelope.seal(kPersona, epoch, plain, aad(accountId, version)).toField()
  }

  /** Ritrova la persona dalla copia del server (telefono nuovo, o un altro dispositivo dopo un «perso o rubato»). */
  fun importSealed(accountId: String, field: String, version: Long, keyFor: (Int) -> ByteArray?): PersonIdentity {
    val envelope = PampaEnvelope.parseField(field)
    val key = keyFor(envelope.epoch) ?: throw PampaCryptoException("manca K_com(persona) dell'epoca ${envelope.epoch}")
    val o = Json.parseToJsonElement(envelope.open(key, aad(accountId, version)).decodeToString()).jsonObject
    val root = Ed25519Key.fromPrivate(PampaBase64.decode(o["root"]!!.jsonPrimitive.content))
    val identity = decodeWork(o["state"]!!.jsonObject)
    if (!identity.card.root.contentEquals(root.publicKey)) throw PampaCryptoException("la copia ha una radice che non è della scheda")
    if (exists) {
      val mine = identity()
      if (mine != null && !mine.card.root.contentEquals(identity.card.root)) throw PampaCryptoException("la copia è di un'altra persona")
    }
    saveRoot(root)
    return saveWork(identity.card, identity.works)
  }

  private fun aad(accountId: String, version: Long) = PampaLabels.recordAad(accountId, STORE_PACKAGE, "persona", "chiavi", version)

  // --- I file ---------------------------------------------------------------------------------------------------------

  private fun saveRoot(root: Ed25519Key) {
    dir.mkdirs()
    val bytes = root.privateBytes()
    write(rootFile, vault.seal(PersonKeyRole.ROOT, bytes, AAD_ROOT))
    bytes.fill(0)
  }

  private fun saveWork(card: PersonCard, works: Map<Int, WorkKeys>): PersonIdentity {
    dir.mkdirs()
    val identity = PersonIdentity(card, TreeMap(works))
    write(workFile, vault.seal(PersonKeyRole.WORK, encodeWork(identity).toString().toByteArray(), AAD_WORK))
    cached = identity
    return identity
  }

  private fun encodeWork(p: PersonIdentity): JsonObject = buildJsonObject {
    put("v", 1)
    put("card", p.card.raw)
    put("sig", p.card.sig)
    putJsonArray("works") {
      for ((e, w) in p.works) addJsonObject {
        put("epoch", e)
        put("enc", PampaBase64.encode(w.enc.privateBytes()))
        put("sign", PampaBase64.encode(w.sign.privateBytes()))
        put("sig", PampaBase64.encode(w.sig))
      }
    }
  }

  private fun decodeWork(o: JsonObject): PersonIdentity {
    val card = PersonCard.verify(o["card"]!!.jsonPrimitive.content, o["sig"]!!.jsonPrimitive.content)
    val works = TreeMap<Int, WorkKeys>()
    for (w in (o["works"] as JsonArray).map { it.jsonObject }) {
      val e = w["epoch"]!!.jsonPrimitive.int
      val keys = WorkKeys(e, X25519Key.fromPrivate(PampaBase64.decode(w["enc"]!!.jsonPrimitive.content)), Ed25519Key.fromPrivate(PampaBase64.decode(w["sign"]!!.jsonPrimitive.content)), PampaBase64.decode(w["sig"]!!.jsonPrimitive.content))
      if (!keys.public.signedBy(card.root)) throw PampaCryptoException("chiavi di lavoro dell'epoca $e non firmate dalla radice")
      works[e] = keys
    }
    if (works[card.work.epoch]?.public != card.work) throw PampaCryptoException("la scheda non è delle chiavi di lavoro custodite")
    return PersonIdentity(card, works)
  }

  private fun write(target: File, bytes: ByteArray) {
    val tmp = File(target.parentFile, target.name + ".nuovo")
    tmp.writeBytes(bytes)
    if (!tmp.renameTo(target)) {
      target.delete()
      check(tmp.renameTo(target)) { "non riesco a scrivere ${target.name}" }
    }
  }

  companion object {
    /** Il pacchetto dello Store nei dati associati delle copie della persona e della rubrica. */
    const val STORE_PACKAGE = "com.pampa.store"
    private val AAD_ROOT = "pampa/v1/persona|radice".toByteArray()
    private val AAD_WORK = "pampa/v1/persona|lavoro".toByteArray()
  }
}
