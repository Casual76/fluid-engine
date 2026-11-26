package dev.antigravity.fluidengine.people.spaces

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import dev.antigravity.fluidengine.people.PeopleClient
import dev.antigravity.fluidengine.people.client.EnvelopePriority
import dev.antigravity.fluidengine.people.client.PeopleServerException
import dev.antigravity.fluidengine.people.crypto.PeopleDetected
import dev.antigravity.fluidengine.people.crypto.PeoplePrints
import dev.antigravity.fluidengine.people.crypto.PersonCard
import dev.antigravity.fluidengine.people.crypto.SpaceRecordCipher
import dev.antigravity.fluidengine.sync.FluidSync
import dev.antigravity.fluidengine.sync.SyncAccepted
import dev.antigravity.fluidengine.sync.SyncDocument
import dev.antigravity.fluidengine.sync.SyncEditor
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
import dev.antigravity.fluidengine.sync.SyncSpace
import dev.antigravity.fluidengine.sync.SyncSpaceId
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Chi sono io in uno spazio condiviso (`d\server-e-chiavi.md` § 11): la chiave di ogni epoca, il gettone di scrittura
 * (assente se posso solo vedere), il proprietario, le schede di chi può scrivere (per controllare le firme dei record)
 * e la versione più alta vista di ogni record (un record vecchio rimandato dal server viene scoperto).
 *
 * Il server non ha l'elenco dei membri: sa solo che un account ha scritto (per le quote).
 */
class SpaceMembership(
  val id: String,
  val kind: String,
  val epoch: Int,
  val keys: Map<Int, ByteArray>,
  val writeToken: String?,
  val owner: String,
  val writers: Map<String, PersonCard>,
  val seen: Map<String, Long>,
) {
  val canWrite: Boolean get() = writeToken != null

  fun toJson(): JsonObject = buildJsonObject {
    put("id", id)
    put("kind", kind)
    put("epoch", epoch)
    putJsonObject("keys") { keys.forEach { (e, k) -> put(e.toString(), PampaBase64.encode(k)) } }
    if (writeToken != null) put("writeToken", writeToken) else put("writeToken", JsonNull)
    put("owner", owner)
    putJsonArray("members") { writers.values.forEach { add(it.toJson()) } }
    putJsonObject("seen") { seen.forEach { (k, v) -> put(k, v) } }
  }

  companion object {
    fun fromJson(o: JsonObject): SpaceMembership = SpaceMembership(
      id = (o["id"] as JsonPrimitive).content,
      kind = (o["kind"] as? JsonPrimitive)?.content ?: "",
      epoch = (o["epoch"] as JsonPrimitive).intOrNull ?: 1,
      keys = (o["keys"] as JsonObject).entries.associate { it.key.toInt() to PampaBase64.decode((it.value as JsonPrimitive).content) },
      writeToken = (o["writeToken"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content,
      owner = (o["owner"] as JsonPrimitive).content,
      writers = ((o["members"] as? JsonArray) ?: JsonArray(emptyList())).map { PersonCard.fromJson(it.jsonObject) }.associateBy { it.code },
      seen = (o["seen"] as? JsonObject)?.entries?.associate { it.key to ((it.value as JsonPrimitive).longOrNull ?: 0) }.orEmpty(),
    )

    /**
     * Le chiavi arrivate dal proprietario in una CDXB (`t = "spazio"`), unite a quello che avevo: le epoche si
     * aggiungono e non si tolgono (chi è rimasto legge il passato), la versione vista resta la più alta.
     *
     * Contro i ritorni indietro (R2): gettone di scrittura e scriventi vengono dall'epoca **più alta**. Uno stato di
     * un'epoca vecchia, rimandato dal server, porta solo le chiavi (che già avevo): non rimette il gettone di prima né
     * chi era stato tolto. A pari epoca gli scriventi si uniscono (togliere qualcuno cambia sempre epoca), e di ognuno
     * resta la scheda più nuova.
     */
    fun mergeIncoming(prev: JsonObject?, payload: JsonObject, owner: String): JsonObject {
      val old = prev?.let(::fromJson)
      val incoming = fromJson(JsonObject(payload + mapOf("owner" to JsonPrimitive(owner))))
      val writeToken: String?
      val writers: Map<String, PersonCard>
      when {
        old == null || incoming.epoch > old.epoch -> {
          writeToken = incoming.writeToken
          writers = incoming.writers.mapValues { (code, card) -> old?.writers?.get(code)?.let { fresher(it, card) } ?: card }
        }
        incoming.epoch == old.epoch -> {
          writeToken = incoming.writeToken ?: old.writeToken
          writers = (old.writers.keys + incoming.writers.keys).associateWith { code ->
            val a = old.writers[code]
            val b = incoming.writers[code]
            if (a != null && b != null) fresher(a, b) else a ?: b!!
          }
        }
        else -> {
          writeToken = old.writeToken
          writers = old.writers.mapValues { (code, card) -> incoming.writers[code]?.let { fresher(card, it) } ?: card }
        }
      }
      return SpaceMembership(
        id = incoming.id,
        kind = incoming.kind,
        epoch = maxOf(incoming.epoch, old?.epoch ?: 0),
        keys = (old?.keys ?: emptyMap()) + incoming.keys,
        writeToken = writeToken,
        owner = owner,
        writers = writers,
        seen = old?.seen ?: emptyMap(),
      ).toJson()
    }

    /** La scheda più nuova di due della stessa persona; con un'altra radice resta quella che c'era. */
    private fun fresher(kept: PersonCard, incoming: PersonCard): PersonCard =
      if (!kept.root.contentEquals(incoming.root)) kept
      else if (incoming.work.epoch > kept.work.epoch || (incoming.work.epoch == kept.work.epoch && incoming.at > kept.at)) incoming else kept
  }
}

/**
 * Come un oggetto vivo diventa i campi di `engine-sync` (F8.21): per un campo vince l'ultimo, ogni voce di una lista ha
 * il suo id, e una voce tolta vince su una modifica arrivata insieme («spunta e tolta insieme: vince la tolta»,
 * `progetto.md` § 3.10). L'app dice solo come scrivere e leggere il suo oggetto.
 */
interface SharedSpaceCodec<T> {
  fun write(value: T, editor: SyncEditor)
  fun read(document: SyncDocument): T?
}

/**
 * Gli spazi condivisi della persona (`d\server-e-chiavi.md` § 11; `progetto.md` § 3.10): una lista, un invito, un
 * viaggio da seguire. Ogni spazio ha una chiave casuale a epoche, sigillata per ogni membro con una CDXB, e un gettone di
 * scrittura dato solo a chi modifica; il server ne tiene l'impronta, non l'elenco dei membri.
 *
 * Sopra c'è `engine-sync`: [relay] parla con le rotte `/v1/spazi/…` e [sealer] chiude ogni record con la chiave
 * dell'epoca e la firma di chi scrive (contratto `people.formulas.spaceRecord`). Così conflitti, coda e stato sono
 * quelli di ogni altra sincronizzazione dell'engine.
 */
class SharedSpaces(private val people: PeopleClient) {

  fun membership(id: String): SpaceMembership? = people.state.spaces[id]?.let(SpaceMembership::fromJson)

  /** Uno spazio nuovo di cui sono il proprietario: id e chiave nati qui, l'impronta del gettone al server. */
  suspend fun create(kind: String): SpaceMembership {
    val me = people.identity()
    val id = PampaBase64.encode(PampaCrypto.random(16))
    val writeToken = PeoplePrints.newToken()
    people.peopleApi.createSpace(id, kind, PeoplePrints.write(PampaBase64.decode(writeToken)))
    val m = SpaceMembership(id, kind, 1, mapOf(1 to PampaCrypto.random(32)), writeToken, me.code, mapOf(me.code to me.card), emptyMap())
    save(m)
    return m
  }

  /**
   * Do lo spazio a dei contatti: la chiave di ogni epoca e, a chi modifica, il gettone di scrittura; a chi in
   * [readOnly] niente gettone. Viaggia in una CDXB per ognuno, a priorità normale.
   */
  suspend fun share(id: String, codes: List<String>, readOnly: Set<String> = emptySet()) {
    val m = checkNotNull(membership(id)) { "spazio sconosciuto" }
    check(m.owner == people.identity().code) { "solo il proprietario invita" }
    val writers = m.writers + codes.filter { it !in readOnly }.associateWith { checkNotNull(people.contact(it)) { "$it non è un contatto" }.card }
    val updated = SpaceMembership(m.id, m.kind, m.epoch, m.keys, m.writeToken, m.owner, writers, m.seen)
    save(updated)
    for (code in codes) people.sendCdxb(code, payload(updated, if (code in readOnly) null else m.writeToken), EnvelopePriority.NORMAL)
  }

  /**
   * Tolgo qualcuno (P6): epoca nuova, chiave nuova, gettone nuovo, consegnati solo a chi resta. Chi esce tiene ciò che
   * ha già, e non legge né scrive più niente di nuovo.
   */
  suspend fun removeMembers(id: String, codes: Collection<String>, keep: List<String>) {
    val m = checkNotNull(membership(id))
    check(m.owner == people.identity().code) { "solo il proprietario toglie" }
    val newToken = PeoplePrints.newToken()
    val epoch = m.epoch + 1
    people.peopleApi.rotateSpace(id, m.writeToken!!, epoch, PeoplePrints.write(PampaBase64.decode(newToken)))
    val updated = SpaceMembership(m.id, m.kind, epoch, m.keys + (epoch to PampaCrypto.random(32)), newToken, m.owner, m.writers - codes.toSet(), m.seen)
    save(updated)
    for (code in keep) people.sendCdxb(code, payload(updated, if (code in updated.writers) newToken else null), EnvelopePriority.NORMAL)
  }

  /** Cancella lo spazio: solo il proprietario. */
  suspend fun delete(id: String) {
    people.peopleApi.deleteSpace(id)
    people.edit { it.spaces.remove(id) }
  }

  private fun payload(m: SpaceMembership, writeToken: String?): JsonObject = buildJsonObject {
    put("t", "spazio")
    put("id", m.id)
    put("kind", m.kind)
    put("epoch", m.epoch)
    putJsonObject("keys") { m.keys.forEach { (e, k) -> put(e.toString(), PampaBase64.encode(k)) } }
    if (writeToken != null) put("writeToken", writeToken) else put("writeToken", JsonNull)
    putJsonArray("members") { m.writers.values.forEach { add(it.toJson()) } }
  }

  private suspend fun save(m: SpaceMembership) = people.edit { it.spaces[m.id] = m.toJson() }

  /** Lo spazio [id] come oggetto vivo di tipo [T], dentro il motore [sync] costruito con [relay] e [sealer]. */
  fun <T> open(id: String, sync: FluidSync, codec: SharedSpaceCodec<T>): SharedSpace<T> = SharedSpace(id, this, sync.space(SyncSpaceId(id)), codec)

  /** L'adattatore di `engine-sync` verso `/v1/spazi/…`. */
  val relay: SyncRelay = object : SyncRelay {
    override suspend fun pull(space: SyncSpaceId, since: Long, limit: Int): SyncPullResult = guard {
      val page = people.peopleApi.readSpace(space.value, since)
      SyncPullResult.Rows(
        page.records.map { r ->
          SyncRemoteRow(r.recordId, r.version, r.deleted, r.ciphertext.takeIf { it.isNotEmpty() }?.let { SyncSealedPayload(PampaBase64.decode(it), r.epoch) }, r.seq)
        },
        page.last,
        page.more,
      )
    }

    override suspend fun push(space: SyncSpaceId, batch: SyncPushBatch): SyncPushResult = guard {
      val m = membership(space.value) ?: throw SyncRelayException(SyncFailure.Rejected("unknown_space"))
      val token = m.writeToken ?: throw SyncRelayException(SyncFailure.Rejected("read_only", "Puoi solo vedere questo spazio."))
      val accepted = ArrayList<SyncAccepted>()
      val conflicts = ArrayList<SyncRemoteRow>()
      for (c in batch.changes) {
        val payload = c.payload ?: throw SyncRelayException(SyncFailure.Rejected("bad_request", "record senza contenuto"))
        try {
          val (version, seq) = people.peopleApi.writeRecord(space.value, token, c.recordId, c.baseVersion, payload.keyVersion, PampaBase64.encode(payload.bytes), c.deleted)
          accepted += SyncAccepted(c.recordId, version, seq)
        } catch (e: PeopleServerException) {
          if (e.code != "conflict" || e.reason != "version") throw e
          val current = people.peopleApi.readSpace(space.value, 0).records.firstOrNull { it.recordId == c.recordId } ?: throw e
          conflicts += SyncRemoteRow(current.recordId, current.version, current.deleted, SyncSealedPayload(PampaBase64.decode(current.ciphertext), current.epoch), current.seq)
        }
      }
      SyncPushResult(accepted, conflicts)
    }
  }

  /** Il sigillo dei record: chiave dell'epoca, firma di chi scrive dentro, versione mai più vecchia di quella vista. */
  val sealer: SyncSealer = object : SyncSealer {
    override suspend fun seal(context: SyncSealContext, plaintext: ByteArray): SyncSealedPayload {
      val m = membership(context.space.value) ?: throw SyncSealException("spazio sconosciuto")
      val me = people.identity()
      val ct = SpaceRecordCipher.seal(m.keys.getValue(m.epoch), m.id, m.epoch, context.recordId, context.version, PampaBase64.encode(plaintext), me.code, me.work)
      return SyncSealedPayload(ct, m.epoch)
    }

    override suspend fun open(context: SyncSealContext, payload: SyncSealedPayload): ByteArray {
      val m = membership(context.space.value) ?: throw SyncSealException("spazio sconosciuto")
      val key = m.keys[payload.keyVersion] ?: throw SyncSealException("record di un'epoca che non ho")
      val opened = try {
        SpaceRecordCipher.open(key, m.id, payload.keyVersion, context.recordId, context.version, payload.bytes)
      } catch (d: PeopleDetected) {
        throw SyncSealException(d.what)
      } catch (e: Exception) {
        // R2 · Qualunque altro guasto di un record è un rifiuto pulito di quel record, mai un errore che risale.
        throw SyncSealException("record illeggibile", e)
      }
      val me = people.identity()
      val signPub = if (opened.writer == me.code) me.works[opened.writerEpoch]?.sign?.publicKey else writerKey(m, opened)
      if (signPub == null) throw SyncSealException("record di chi non può scrivere")
      if (!SpaceRecordCipher.signedBy(opened, signPub, m.id, payload.keyVersion, context.recordId, context.version)) throw SyncSealException("firma del record")
      if (context.version < (m.seen[context.recordId] ?: 0)) throw SyncSealException("record vecchio rimandato")
      if (context.version > (m.seen[context.recordId] ?: 0)) people.edit { st ->
        st.spaces[m.id]?.let { j -> val now = SpaceMembership.fromJson(j); st.spaces[m.id] = SpaceMembership(now.id, now.kind, now.epoch, now.keys, now.writeToken, now.owner, now.writers, now.seen + (context.recordId to context.version)).toJson() }
      }
      return runCatching { PampaBase64.decode(opened.data) }.getOrElse { throw SyncSealException("record illeggibile", it) }
    }
  }

  /**
   * La chiave di firma di chi ha scritto [opened] (R2): la sua scheda fra gli scriventi, se è della stessa epoca;
   * altrimenti le chiavi di lavoro che il record porta con sé ([SpaceRecordCipher.Opened.writerKeys]), se le ha firmate
   * la **stessa radice** che il proprietario ha messo fra gli scriventi. Così chi rinnova le chiavi (dopo un furto, o i
   * sei mesi) resta leggibile per tutti, prima e dopo che il proprietario rimandi lo spazio; chi è stato tolto no.
   */
  private fun writerKey(m: SpaceMembership, opened: SpaceRecordCipher.Opened): ByteArray? {
    val card = m.writers[opened.writer] ?: return null
    if (card.work.epoch == opened.writerEpoch) return card.work.sign
    val carried = opened.writerKeys ?: return null
    if (carried.epoch != opened.writerEpoch || !carried.signedBy(card.root)) return null
    return carried.sign
  }

  private suspend fun <R> guard(block: suspend () -> R): R = try {
    block()
  } catch (e: IOException) {
    throw SyncRelayException(SyncFailure.Offline, e)
  } catch (e: PeopleServerException) {
    throw SyncRelayException(if (e.retryable) SyncFailure.RetryLater((e.retryAfterSeconds ?: 30) * 1000) else SyncFailure.Rejected(e.reason ?: e.code, e.message), e)
  }
}

/**
 * Uno spazio condiviso come oggetto vivo ([T] per ogni record), sopra uno `SyncSpace` di `engine-sync`: [items] cambia
 * da sé, [put], [add] e [remove] scrivono subito sul telefono e partono con la sincronizzazione.
 */
class SharedSpace<T> internal constructor(val id: String, private val spaces: SharedSpaces, val sync: SyncSpace, private val codec: SharedSpaceCodec<T>) {
  /** Gli elementi vivi, per id. */
  val items: Flow<Map<String, T>> = sync.state.map { s -> s.records.mapNotNull { (k, v) -> codec.read(v)?.let { k to it } }.toMap() }

  /** Posso modificare? Senza gettone di scrittura lo spazio si vede soltanto. */
  val canWrite: Boolean get() = spaces.membership(id)?.canWrite == true

  /**
   * Scrive [value] in [recordId]. Senza gettone di scrittura ([canWrite] falso) è [IllegalStateException]: una modifica
   * locale che il relay rifiuterebbe per sempre resterebbe in coda e fermerebbe anche la lettura dello spazio.
   */
  suspend fun put(recordId: String, value: T): Boolean = writable { sync.edit(recordId) { codec.write(value, this) } }
  suspend fun add(value: T): String = writable { sync.create { codec.write(value, this) } }
  suspend fun remove(recordId: String): Boolean = writable { sync.delete(recordId) }

  private inline fun <R> writable(block: () -> R): R {
    check(canWrite) { "puoi solo vedere questo spazio" }
    return block()
  }
  suspend fun sync() = sync.sync()
  suspend fun snapshot(): Map<String, T> = sync.state.value.records.mapNotNull { (k, v) -> codec.read(v)?.let { k to it } }.toMap()
}

/** Una voce di una lista condivisa (spesa, materiale per la gita): il testo, la spunta, a chi è assegnata («@mamma»). */
data class SharedListItem(val text: String, val done: Boolean = false, val assignee: String? = null) {
  object Codec : SharedSpaceCodec<SharedListItem> {
    override fun write(value: SharedListItem, editor: SyncEditor) {
      editor.text("text", value.text)
      editor.bool("done", value.done)
      editor.text("assignee", value.assignee)
    }

    override fun read(document: SyncDocument): SharedListItem? =
      document.text("text")?.let { SharedListItem(it, document.bool("done") == true, document.text("assignee")) }
  }
}
