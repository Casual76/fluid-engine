package dev.antigravity.fluidengine.ai.memory

import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * La memoria di prima di PampAI: la tabella `memories` del database Room di PampAI alla versione 7
 * (`core-assistant/schemas/…/PampaiDatabase/7.json`):
 *
 * ```sql
 * CREATE TABLE memories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, text TEXT NOT NULL,
 *   createdAtMillis INTEGER NOT NULL, sourceConversationId INTEGER, pinned INTEGER NOT NULL)
 * ```
 *
 * **La strada di serie** e' che PampAI legga le righe col suo `MemoryDao` (il database e' gia' aperto da Room, con le sue
 * migrazioni) e le passi a [AiMemoryBook.importLegacy] come [AiMemoryLegacyRow]: nessun secondo accesso al file. Questo
 * lettore serve dove Room non c'e' (le prove, uno strumento di recupero): apre il file **in sola lettura** e non scrive
 * niente, neanche il registro WAL.
 *
 * Il passaggio non cancella le righe di Room: PampAI le toglie con una migrazione successiva, dopo che il passaggio e'
 * andato bene (il piano per PampAI di F9.3).
 */
object AiMemoryLegacyImport {
  /** La versione dello schema di PampAI che questo lettore conosce. */
  const val RoomVersion = 7

  /** La tabella. */
  const val Table = "memories"

  /** Le colonne che servono, come le scrive Room. */
  val Columns: List<String> = listOf("id", "text", "createdAtMillis", "sourceConversationId", "pinned")

  /**
   * Le righe di [file] (il database di PampAI, `pampai.db`), in ordine di id. Vuoto se la tabella non c'e'. Lancia
   * un'eccezione se la tabella c'e' ma le colonne non sono quelle della versione 7 (uno schema che non si conosce non si
   * indovina), o se [file] non e' un database.
   */
  fun readPampaiRoom(file: File): List<AiMemoryLegacyRow> {
    val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
    return db.use { read(it) }
  }

  /** Le righe dal database [db] gia' aperto (in sola lettura va bene). */
  fun read(db: SQLiteDatabase): List<AiMemoryLegacyRow> {
    if (!columns(db, Table, Columns)) return emptyList()
    return db.rawQuery("SELECT id, text, createdAtMillis, sourceConversationId, pinned FROM $Table ORDER BY id", null).use { c ->
      buildList {
        while (c.moveToNext()) {
          add(
            AiMemoryLegacyRow(
              id = c.getLong(0),
              text = c.getString(1) ?: "",
              createdAtMillis = c.getLong(2),
              sourceConversationId = if (c.isNull(3)) null else c.getLong(3),
              pinned = c.getInt(4) != 0,
            ),
          )
        }
      }
    }
  }

  /** Le tabelle del profilo della ricerca (F9.3b), alla versione 7, e le colonne che servono di ognuna. */
  val ProfileColumns: Map<String, List<String>> = mapOf(
    "aria_profile" to listOf("id", "field", "value", "kind", "status", "generatedProvider", "generatedModel", "limitations"),
    "aria_dependencies" to listOf("itemId", "evidenceId"),
    "aria_evidence" to listOf("id", "grantId", "recordKey", "field", "observedAt", "syncedAt", "valid"),
    "aria_grants" to listOf("id", "app"),
    "aria_exclusions" to listOf("key", "field", "grantId", "recordKey", "unmapped"),
  )

  /** [readPampaiProfile] con un file: aperto in sola lettura, come [readPampaiRoom]. */
  fun readPampaiProfile(file: File, labelFor: (String) -> String? = { null }): AiMemoryProfileExport {
    val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
    return db.use { readProfile(it, labelFor) }
  }

  /**
   * **Il profilo della ricerca di oggi** (`aria_profile` con le sue prove, le autorizzazioni e le esclusioni) come righe
   * per [AiMemoryBook.importProfile]: la risposta 6 di Alessio, una memoria sola. Come per i ricordi, la strada di serie e'
   * che PampAI legga le righe coi suoi DAO; questo lettore e' per le prove e il recupero.
   *
   * - ogni voce porta le sue prove (`aria_dependencies` → `aria_evidence`, solo quelle valide), con l'app presa dalla sua
   *   autorizzazione (`aria_grants.app`): revocare l'autorizzazione toglie i fatti dedotti da lei
   *   ([AiMemoryBook.forgetFromGrant]);
   * - le esclusioni passano con l'argomento (`field`), l'autorizzazione, il dato e l'app;
   * - un'esclusione **da chiarire** (`unmapped`, che oggi sospende tutto l'apprendimento) non si traduce: e' contata in
   *   [AiMemoryProfileExport.unmapped] e PampAI non fa il passaggio finche' l'utente non l'ha chiarita (il piano).
   *
   * [labelFor] da' il nome di un'app dal pacchetto (PampAI lo chiede al sistema). Vuoto se le tabelle non ci sono; lancia
   * un'eccezione se ci sono ma con colonne diverse.
   */
  fun readProfile(db: SQLiteDatabase, labelFor: (String) -> String? = { null }): AiMemoryProfileExport {
    val present = ProfileColumns.mapValues { (table, cols) -> columns(db, table, cols) }
    if (present["aria_profile"] != true) return AiMemoryProfileExport(emptyList(), emptyList(), unmapped = 0)
    val grants = if (present["aria_grants"] == true) {
      db.rawQuery("SELECT id, app FROM aria_grants", null).use { c -> buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) } }
    } else emptyMap()
    val evidence: Map<String, AiMemoryEvidence> = if (present["aria_evidence"] == true) {
      db.rawQuery("SELECT id, grantId, recordKey, field, observedAt, syncedAt FROM aria_evidence WHERE valid != 0", null).use { c ->
        buildMap {
          while (c.moveToNext()) {
            val id = c.getString(0)
            val grant = c.getString(1)
            val observed = if (!c.isNull(4)) c.getLong(4) else if (!c.isNull(5)) c.getLong(5) else null
            put(id, AiMemoryEvidence(id, grant, grants[grant], c.getString(2), observed, AiMemoryProposal.normalizeKey(c.getString(3))))
          }
        }
      }
    } else emptyMap()
    val links: Map<String, List<String>> = if (present["aria_dependencies"] == true) {
      db.rawQuery("SELECT itemId, evidenceId FROM aria_dependencies ORDER BY itemId, evidenceId", null).use { c ->
        val out = LinkedHashMap<String, MutableList<String>>()
        while (c.moveToNext()) out.getOrPut(c.getString(0)) { ArrayList() } += c.getString(1)
        out
      }
    } else emptyMap()
    val rows = db.rawQuery(
      "SELECT id, field, value, kind, status, generatedProvider, generatedModel, limitations FROM aria_profile ORDER BY id", null,
    ).use { c ->
      buildList {
        while (c.moveToNext()) {
          val id = c.getString(0)
          val proofs = links[id].orEmpty().mapNotNull { evidence[it] }
          val apps = proofs.mapNotNull { it.app }.distinct()
          val app = apps.singleOrNull()
          val provider = c.getString(5)
          val model = c.getString(6)
          add(
            AiMemoryProfileRow(
              id = id,
              field = c.getString(1),
              value = c.getString(2) ?: "",
              kind = c.getString(3),
              status = c.getString(4),
              limitations = c.getString(7),
              derivedBy = if (provider != null && model != null) "$provider/$model" else model ?: provider,
              evidence = proofs,
              app = app,
              appLabel = app?.let(labelFor),
              observedAtMillis = proofs.mapNotNull { it.observedAtMillis }.maxOrNull(),
            ),
          )
        }
      }
    }
    var unmapped = 0
    val exclusions = if (present["aria_exclusions"] == true) {
      db.rawQuery("SELECT key, field, grantId, recordKey, unmapped FROM aria_exclusions ORDER BY key", null).use { c ->
        buildList {
          while (c.moveToNext()) {
            if (c.getInt(4) != 0) { unmapped++; continue }
            val grant = c.getString(2)
            val exclusion = AiMemoryExclusion(
              key = AiMemoryProposal.normalizeKey(c.getString(1)),
              app = grant?.let { grants[it] },
              grant = grant,
              record = c.getString(3),
            )
            if (exclusion.key != null || exclusion.grant != null || exclusion.record != null) add(exclusion) else unmapped++
          }
        }
      }
    } else emptyList()
    return AiMemoryProfileExport(rows, exclusions, unmapped)
  }

  /** Vero se [table] c'e'; se c'e' ma senza una delle [needed], un'eccezione: uno schema che non si conosce non si indovina. */
  private fun columns(db: SQLiteDatabase, table: String, needed: List<String>): Boolean {
    val exists = db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)).use { it.moveToFirst() }
    if (!exists) return false
    val present = db.rawQuery("PRAGMA table_info($table)", null).use { c ->
      val name = c.getColumnIndexOrThrow("name")
      buildSet { while (c.moveToNext()) add(c.getString(name)) }
    }
    val missing = needed.filter { it !in present }
    require(missing.isEmpty()) { "la tabella $table non e' quella di Room v$RoomVersion: mancano ${missing.joinToString()}" }
    return true
  }
}

/**
 * Il profilo della ricerca letto da PampAI: le voci, le esclusioni da passare e quante esclusioni sono da chiarire
 * ([unmapped]: con una sola, il passaggio aspetta).
 */
data class AiMemoryProfileExport(
  val rows: List<AiMemoryProfileRow>,
  val exclusions: List<AiMemoryExclusion>,
  val unmapped: Int,
)
