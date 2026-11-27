package dev.antigravity.fluidengine.ai.memory

import dev.antigravity.fluidengine.ai.net.string
import dev.antigravity.fluidengine.vault.VaultSection
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Dove la memoria tiene i suoi byte: un blocco solo, scritto tutto o niente. Il formato lo decide la memoria
 * ([AiMemoryBook]); il deposito sa solo leggere, scrivere e cancellare. Di serie e' la cassaforte ([vault]): il file e'
 * cifrato con la chiave della sezione (AES-256-GCM a pezzi, `VaultFile`), sta in `noBackupFilesDir` e non esce dal
 * telefono.
 */
interface AiMemoryStore {
  /** I byte salvati, o null se non c'e' ancora niente. */
  suspend fun read(): ByteArray?

  /** Scrive [bytes] al posto di quelli di prima, tutto o niente. */
  suspend fun write(bytes: ByteArray)

  /** Cancella tutto. */
  suspend fun delete()

  companion object {
    /** Il nome di serie del file della memoria nella sua sezione. */
    const val DefaultFile = "ai-memory.v1"

    /**
     * Il file [fileName] della sezione [section] della cassaforte: cifrato, fuori dal backup. Una sezione
     * `VaultAccess.Device()` (si apre da sola) e' quella giusta: la memoria serve anche a una domanda dalla sessione
     * dell'assistente; dal telefono bloccato non entra comunque nelle domande (lo decide PampAI).
     */
    fun vault(section: VaultSection, fileName: String = DefaultFile): AiMemoryStore = VaultMemoryStore(section, fileName)

    /** In memoria, che sparisce col processo: per le prove e la galleria. Non cifra niente. */
    fun inMemory(): AiMemoryStore = MemoryOnlyStore()
  }
}

private class VaultMemoryStore(section: VaultSection, fileName: String) : AiMemoryStore {
  private val file = section.file(fileName)
  override suspend fun read(): ByteArray? = if (file.exists) file.read() else null
  override suspend fun write(bytes: ByteArray) = file.write(bytes)
  override suspend fun delete() = file.delete()
}

private class MemoryOnlyStore : AiMemoryStore {
  @Volatile private var bytes: ByteArray? = null
  override suspend fun read(): ByteArray? = bytes?.copyOf()
  override suspend fun write(bytes: ByteArray) { this.bytes = bytes.copyOf() }
  override suspend fun delete() { bytes = null }
}

/** Un annulla possibile: cosa c'era prima dell'avviso [noticeId] (null: il ricordo non c'era). */
internal class UndoEntry(
  val noticeId: String,
  val memoryId: String,
  /** L'ora dell'ultimo cambio fatto dall'avviso: se il ricordo e' cambiato dopo, l'annulla non vale piu'. */
  val afterUpdatedAt: Long,
  val before: AiMemory?,
  /** L'app che l'ha proposto (solo lei puo' annullarlo col ponte), o null. */
  val app: String?,
  val atMillis: Long,
)

/** Un vettore del significato salvato: il testo da cui e' nato ([key]) e i byte a 8 bit con la scala. */
internal class StoredVector(val key: String, val bytes: ByteArray)

/** Una proposta che aspetta il si' dell'utente (la card «Vuoi che ricordi…?»): resta anche dopo un riavvio. */
internal class PendingEntry(
  val noticeId: String,
  val proposal: AiMemoryProposal,
  val source: AiMemorySource,
  /** Vero se veniva dall'estrattore: conta nel tetto dei ricordi automatici quando entra. */
  val automatic: Boolean,
  val atMillis: Long,
  /** Il fatto con la stessa chiave che prenderebbe il posto, per dirlo nella domanda. */
  val replacing: String?,
)

/** Tutto quello che la memoria salva, in un valore intero e immutabile. */
internal class MemoryState(
  val memories: List<AiMemory> = emptyList(),
  val blockedApps: Set<String> = emptySet(),
  val imported: Set<String> = emptySet(),
  val undo: List<UndoEntry> = emptyList(),
  val vectors: Map<String, StoredVector> = emptyMap(),
  val pending: List<PendingEntry> = emptyList(),
  val exclusions: List<AiMemoryExclusion> = emptyList(),
  /** Il salvataggio automatico, scelto dall'utente; null: non l'ha mai toccato (vale quello della configurazione). */
  val autoSave: Boolean? = null,
) {
  fun copy(
    memories: List<AiMemory> = this.memories,
    blockedApps: Set<String> = this.blockedApps,
    imported: Set<String> = this.imported,
    undo: List<UndoEntry> = this.undo,
    vectors: Map<String, StoredVector> = this.vectors,
    pending: List<PendingEntry> = this.pending,
    exclusions: List<AiMemoryExclusion> = this.exclusions,
    autoSave: Boolean? = this.autoSave,
  ) = MemoryState(memories, blockedApps, imported, undo, vectors, pending, exclusions, autoSave)
}

/** Il formato del file: JSON a mano, come il resto di engine-ai (ogni campo mancante e' una decisione). */
internal object MemoryCodec {
  const val Version = 1

  fun encode(state: MemoryState): ByteArray = buildJsonObject {
    put("v", Version)
    put("memories", buildJsonArray { state.memories.forEach { add(memory(it)) } })
    put("blocked", buildJsonArray { state.blockedApps.sorted().forEach { add(JsonPrimitive(it)) } })
    put("imported", buildJsonArray { state.imported.sorted().forEach { add(JsonPrimitive(it)) } })
    put("undo", buildJsonArray {
      state.undo.forEach { u ->
        add(buildJsonObject {
          put("notice", u.noticeId)
          put("memory", u.memoryId)
          put("after", u.afterUpdatedAt)
          put("before", u.before?.let { memory(it) } ?: JsonNull)
          u.app?.let { put("app", it) }
          put("at", u.atMillis)
        })
      }
    })
    put("vectors", buildJsonObject {
      state.vectors.forEach { (id, v) -> put(id, buildJsonObject { put("key", v.key); put("b", Base64.getEncoder().encodeToString(v.bytes)) }) }
    })
    put("pending", buildJsonArray {
      state.pending.forEach { p ->
        add(buildJsonObject {
          put("notice", p.noticeId)
          put("proposal", proposal(p.proposal))
          put("source", source(p.source))
          if (p.automatic) put("automatic", true)
          put("at", p.atMillis)
          p.replacing?.let { put("replacing", it) }
        })
      }
    })
    put("exclusions", buildJsonArray {
      state.exclusions.forEach { x ->
        add(buildJsonObject {
          x.key?.let { put("key", it) }
          x.app?.let { put("app", it) }
          x.grant?.let { put("grant", it) }
          x.record?.let { put("record", it) }
        })
      }
    })
    state.autoSave?.let { put("autoSave", it) }
  }.toString().toByteArray(Charsets.UTF_8)

  fun decode(bytes: ByteArray): MemoryState {
    val root = Json.parseToJsonElement(String(bytes, Charsets.UTF_8)) as JsonObject
    return MemoryState(
      memories = (root["memories"] as? JsonArray).orEmpty().mapNotNull { memoryOf(it) },
      blockedApps = (root["blocked"] as? JsonArray).orEmpty().mapNotNull { it.string() }.toSet(),
      imported = (root["imported"] as? JsonArray).orEmpty().mapNotNull { it.string() }.toSet(),
      undo = (root["undo"] as? JsonArray).orEmpty().mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        UndoEntry(
          noticeId = o["notice"].string() ?: return@mapNotNull null,
          memoryId = o["memory"].string() ?: return@mapNotNull null,
          afterUpdatedAt = o["after"].long() ?: return@mapNotNull null,
          before = memoryOf(o["before"]),
          app = o["app"].string(),
          atMillis = o["at"].long() ?: 0L,
        )
      },
      vectors = (root["vectors"] as? JsonObject).orEmpty().mapNotNull { (id, e) ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val key = o["key"].string() ?: return@mapNotNull null
        val b = o["b"].string()?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() } ?: return@mapNotNull null
        id to StoredVector(key, b)
      }.toMap(),
      pending = (root["pending"] as? JsonArray).orEmpty().mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        PendingEntry(
          noticeId = o["notice"].string() ?: return@mapNotNull null,
          proposal = proposalOf(o["proposal"]) ?: return@mapNotNull null,
          source = sourceOf(o["source"]),
          automatic = o["automatic"].bool(),
          atMillis = o["at"].long() ?: 0L,
          replacing = o["replacing"].string(),
        )
      },
      exclusions = (root["exclusions"] as? JsonArray).orEmpty().mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        AiMemoryExclusion(o["key"].string(), o["app"].string(), o["grant"].string(), o["record"].string())
      },
      autoSave = (root["autoSave"] as? JsonPrimitive)?.booleanOrNull,
    )
  }

  private fun source(s: AiMemorySource): JsonObject = buildJsonObject {
    put("kind", s.kind)
    s.conversationId?.let { put("conversation", it) }
    s.app?.let { put("app", it) }
    s.appLabel?.let { put("appLabel", it) }
  }

  private fun sourceOf(e: JsonElement?): AiMemorySource {
    val src = e as? JsonObject
    return AiMemorySource(
      kind = src?.get("kind").string() ?: AiMemorySource.KindChat,
      conversationId = src?.get("conversation").long(),
      app = src?.get("app").string(),
      appLabel = src?.get("appLabel").string(),
    )
  }

  private fun evidence(list: List<AiMemoryEvidence>) = buildJsonArray {
    list.forEach { v ->
      add(buildJsonObject {
        put("id", v.id)
        v.grant?.let { put("grant", it) }
        v.app?.let { put("app", it) }
        v.record?.let { put("record", it) }
        v.observedAtMillis?.let { put("observed", it) }
        v.field?.let { put("field", it) }
      })
    }
  }

  private fun evidenceOf(e: JsonElement?): List<AiMemoryEvidence> = (e as? JsonArray).orEmpty().mapNotNull { x ->
    val o = x as? JsonObject ?: return@mapNotNull null
    AiMemoryEvidence(o["id"].string() ?: return@mapNotNull null, o["grant"].string(), o["app"].string(), o["record"].string(), o["observed"].long(), o["field"].string())
  }

  /** Una proposta intera, anche coi campi che nel ponte non passano (l'origine, le fonti). */
  private fun proposal(p: AiMemoryProposal): JsonObject = buildJsonObject {
    (Json.parseToJsonElement(p.toJson()) as JsonObject).forEach { (k, v) -> put(k, v) }
    if (p.origin != AiMemory.OriginStatement) put("origin", p.origin)
    if (p.evidence.isNotEmpty()) put("evidence", evidence(p.evidence))
    p.limitations?.let { put("limitations", it) }
    p.derivedBy?.let { put("derivedBy", it) }
  }

  private fun proposalOf(e: JsonElement?): AiMemoryProposal? {
    val o = e as? JsonObject ?: return null
    val base = AiMemoryProposal.fromJsonElement(o) ?: return null
    return base.copy(
      origin = o["origin"].string() ?: AiMemory.OriginStatement,
      evidence = evidenceOf(o["evidence"]),
      limitations = o["limitations"].string(),
      derivedBy = o["derivedBy"].string(),
    )
  }

  fun memory(m: AiMemory): JsonObject = buildJsonObject {
    put("id", m.id)
    put("text", m.text)
    m.key?.let { put("key", it) }
    if (m.topics.isNotEmpty()) put("topics", buildJsonArray { m.topics.forEach { add(JsonPrimitive(it)) } })
    put("source", buildJsonObject {
      put("kind", m.source.kind)
      m.source.conversationId?.let { put("conversation", it) }
      m.source.app?.let { put("app", it) }
      m.source.appLabel?.let { put("appLabel", it) }
    })
    put("created", m.createdAtMillis)
    put("updated", m.updatedAtMillis)
    put("observed", m.observedAtMillis)
    m.expiresAtMillis?.let { put("expires", it) }
    if (m.pinned) put("pinned", true)
    if (m.always) put("always", true)
    m.previousText?.let { put("previous", it) }
    m.previousSinceMillis?.let { put("previousSince", it) }
    if (m.edited) put("edited", true)
    m.value?.let { put("value", it) }
    if (m.origin != AiMemory.OriginStatement) put("origin", m.origin)
    if (m.evidence.isNotEmpty()) put("evidence", evidence(m.evidence))
    m.limitations?.let { put("limitations", it) }
    m.derivedBy?.let { put("derivedBy", it) }
  }

  fun memoryOf(e: JsonElement?): AiMemory? {
    val o = e as? JsonObject ?: return null
    val created = o["created"].long() ?: return null
    return AiMemory(
      id = o["id"].string() ?: return null,
      text = o["text"].string() ?: return null,
      key = o["key"].string(),
      topics = (o["topics"] as? JsonArray).orEmpty().mapNotNull { it.string() },
      source = sourceOf(o["source"]),
      createdAtMillis = created,
      updatedAtMillis = o["updated"].long() ?: created,
      observedAtMillis = o["observed"].long() ?: created,
      expiresAtMillis = o["expires"].long(),
      pinned = o["pinned"].bool(),
      always = o["always"].bool(),
      previousText = o["previous"].string(),
      previousSinceMillis = o["previousSince"].long(),
      edited = o["edited"].bool(),
      value = o["value"].string(),
      origin = o["origin"].string() ?: AiMemory.OriginStatement,
      evidence = evidenceOf(o["evidence"]),
      limitations = o["limitations"].string(),
      derivedBy = o["derivedBy"].string(),
    )
  }

  private fun JsonElement?.long(): Long? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.longOrNull
  private fun JsonElement?.bool(): Boolean = (this as? JsonPrimitive)?.booleanOrNull ?: false
}
