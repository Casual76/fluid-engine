package dev.antigravity.fluidengine.search

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * L'indice di ricerca sul telefono (F8.22, `indice-ricerca`), come Core Spotlight per i dati di un'app: documenti con
 * campi e pesi ([FluidSearchSchema]), la ricerca che tollera gli errori di battitura e trova gli inizi di parola,
 * l'italiano (accenti, apostrofi, plurali), l'ordine per pertinenza e i pezzi di testo con le parole evidenziate.
 *
 * **Dove sta.** Un file SQLite privato dell'app, in `noBackupFilesDir/fluid-search/<nome>.db` (fuori dai backup: e' una
 * copia dei dati, si rifa'). Le parole stanno in una tabella **FTS4**, che c'e' nel SQLite di ogni Android (FTS5 no: non
 * e' garantito); l'analisi della lingua, l'ordine (BM25F) e i pezzi evidenziati sono dell'engine. La scelta contro
 * AppSearch, con le misure, sta nella scheda del modulo (`moduli/search.md`).
 *
 * **I dati sensibili.** Con un [FluidSearchCipher] (la cassaforte di `engine-vault`) il testo conservato, le domande
 * recenti e i vettori si chiudono sul disco, e i campi [FluidSearchField.sensitive] entrano come impronte con chiave.
 *
 * **I fili.** Ogni chiamata e' `suspend` e lavora su `Dispatchers.IO`, una alla volta. I risultati sono valori immutabili:
 * si mettono interi in uno stato di Compose dal filo principale (regola F7.21).
 *
 * **Il significato.** [search] con [FluidSearchQuery.meaning] cerca anche fra i vettori che l'indice conserva
 * ([pendingMeaning] e [putMeaning], che `FluidSemanticIndexer` riempie a pezzi, in carica).
 */
class FluidSearchIndex private constructor(
  val schema: FluidSearchSchema,
  private val db: SQLiteDatabase,
  private val file: File?,
  private val cipher: FluidSearchCipher?,
  /** Il nome dell'indice (quello del file). */
  val name: String,
) : Closeable {

  private val mutex = Mutex()
  // Un filo solo per l'indice: le transazioni di SQLite su Android sono del filo che le apre, e una scrittura che
  // sospende (la cassaforte che cifra) deve riprendere sullo stesso filo, o la transazione resta a meta' e si blocca.
  private val thread = Executors.newSingleThreadExecutor { r -> Thread(r, "fluid-search-$name").apply { isDaemon = true } }
  private val dispatcher = thread.asCoroutineDispatcher()
  private val weights = FloatArray(schema.fields.size) { schema.fields[it].weight }
  private var blind: BlindTerms? = null
  private var vocabularyCache: MemoryVocabulary? = null
  private var vectorCache: VectorMatrix? = null
  // Le lunghezze delle colonne per BM25F: le parole e i documenti con quella colonna piena. La media di FTS4 (`matchinfo`
  // 'a') conta anche le righe vuote, e un campo che c'e' in pochi documenti (il mittente) sembrerebbe lunghissimo.
  private val colTokens = LongArray(schema.fields.size)
  private val colDocs = LongArray(schema.fields.size)
  private val _generation = MutableStateFlow(0L)
  private var closed = false

  /**
   * Cambia a ogni scrittura: chi mostra dei risultati la osserva e rifa' la domanda (una nota cambiata mentre si cerca
   * cambia la lista).
   */
  val generation: StateFlow<Long> = _generation.asStateFlow()

  /**
   * Vero se all'apertura l'indice di prima e' stato buttato: lo schema era cambiato ([FluidSearchSchema.version] o i
   * campi), il formato era vecchio, o la chiave della cassaforte non c'era piu'. L'app allora rimanda tutto (i segni delle
   * sorgenti sono ripartiti da capo da soli).
   */
  var rebuilt: Boolean = false
    private set

  /** Le misure dell'indice adesso. */
  class Stats(val documents: Int, val words: Int, val vectors: Int, val bytes: Long)

  private fun check() = check(!closed) { "FluidSearchIndex($name) e' chiuso" }

  private suspend fun <T> locked(block: suspend () -> T): T = withContext(dispatcher) { mutex.withLock { check(); block() } }

  private suspend fun prepare() {
    db.execSQL("CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value BLOB)")
    val fingerprint = "$Format|${schema.fingerprint}"
    val current = metaText("schema")
    if (current != fingerprint) {
      if (current != null) rebuilt = true
      dropAll()
      createTables()
      setMetaText("schema", fingerprint)
    }
    loadColumnStats()
    if (cipher != null) {
      blind = try {
        val sealed = meta("blind")
        val key = if (sealed == null) {
          ByteArray(32).also { SecureRandom().nextBytes(it) }.also { setMeta("blind", cipher.seal(it, associated("blind"))) }
        } else {
          cipher.open(sealed, associated("blind"))
        }
        BlindTerms(key)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // La chiave della cassaforte non apre piu' l'indice (telefono nuovo, Keystore azzerato): si riparte vuoti. Una
        // sezione solo chiusa (all'impronta) invece non si svuota: l'errore arriva all'app, che la apre e riprova.
        if (!cipher.isLost(e)) {
          close()
          throw e
        }
        rebuilt = true
        dropAll()
        createTables()
        setMetaText("schema", fingerprint)
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        setMeta("blind", cipher.seal(key, associated("blind")))
        BlindTerms(key)
      }
    }
  }

  private fun createTables() {
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS docs(rid INTEGER PRIMARY KEY, id TEXT NOT NULL UNIQUE, kind TEXT NOT NULL, ts INTEGER, " +
        "boost REAL NOT NULL, link TEXT, stored BLOB, forms TEXT, lens TEXT, semkey TEXT, veckey TEXT)",
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS docs_kind ON docs(kind)")
    val columns = schema.fields.indices.joinToString(", ") { "f$it" }
    // Gli inizi di 2, 3 e 4 lettere hanno un indice loro: «ri», «riu», «riun» non scorrono tutto il vocabolario.
    db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS fts USING fts4($columns, prefix=\"2,3,4\")")
    db.execSQL("CREATE TABLE IF NOT EXISTS forms(form TEXT PRIMARY KEY, stem TEXT NOT NULL, display TEXT NOT NULL, n INTEGER NOT NULL)")
    db.execSQL("CREATE TABLE IF NOT EXISTS vectors(rid INTEGER NOT NULL, chunk INTEGER NOT NULL, dims INTEGER NOT NULL, data BLOB NOT NULL, PRIMARY KEY(rid, chunk))")
  }

  private fun dropAll() {
    db.execSQL("DROP TABLE IF EXISTS docs")
    db.execSQL("DROP TABLE IF EXISTS fts")
    db.execSQL("DROP TABLE IF EXISTS forms")
    db.execSQL("DROP TABLE IF EXISTS vectors")
    db.execSQL("DELETE FROM meta")
    vocabularyCache = null
    vectorCache = null
    colTokens.fill(0)
    colDocs.fill(0)
  }

  private fun loadColumnStats() {
    colTokens.fill(0)
    colDocs.fill(0)
    metaText("colstats")?.split(';')?.forEachIndexed { i, pair ->
      if (i >= colTokens.size) return@forEachIndexed
      val (t, d) = pair.split(',').let { (it.getOrNull(0)?.toLongOrNull() ?: 0L) to (it.getOrNull(1)?.toLongOrNull() ?: 0L) }
      colTokens[i] = t
      colDocs[i] = d
    }
  }

  private fun saveColumnStats() =
    setMetaText("colstats", colTokens.indices.joinToString(";") { "${colTokens[it]},${colDocs[it]}" })

  private fun addLengths(lens: String?, sign: Int) {
    if (lens.isNullOrEmpty()) return
    lens.split(',').forEachIndexed { i, v ->
      val n = v.toLongOrNull() ?: return@forEachIndexed
      if (i >= colTokens.size || n == 0L) return@forEachIndexed
      colTokens[i] += sign * n
      colDocs[i] += sign.toLong()
    }
  }

  /** La lunghezza media di ogni colonna fra i documenti che la hanno piena (almeno 1). */
  private fun averages(): FloatArray = FloatArray(colTokens.size) { i ->
    if (colDocs[i] <= 0) 1f else (colTokens[i].toFloat() / colDocs[i]).coerceAtLeast(1f)
  }

  private fun associated(what: String): ByteArray = "fs1|$name|$what".toByteArray(Charsets.UTF_8)

  private fun meta(key: String): ByteArray? =
    db.rawQuery("SELECT value FROM meta WHERE key = ?", arrayOf(key)).use { if (it.moveToFirst()) it.getBlob(0) else null }

  private fun setMeta(key: String, value: ByteArray?) {
    if (value == null) {
      db.delete("meta", "key = ?", arrayOf(key))
    } else {
      db.insertWithOnConflict("meta", null, ContentValues().apply { put("key", key); put("value", value) }, SQLiteDatabase.CONFLICT_REPLACE)
    }
  }

  private fun metaText(key: String): String? = meta(key)?.toString(Charsets.UTF_8)
  private fun setMetaText(key: String, value: String?) = setMeta(key, value?.toByteArray(Charsets.UTF_8))

  // --- Le scritture ---------------------------------------------------------------------------------------------

  /** Mette (o sostituisce) i documenti, tutti insieme. */
  suspend fun put(documents: List<FluidSearchDocument>) {
    if (documents.isEmpty()) return
    locked { write { documents.forEach { putOne(it) } } }
  }

  /** Mette (o sostituisce) un documento. */
  suspend fun put(document: FluidSearchDocument): Unit = put(listOf(document))

  /** Toglie i documenti con questi id (quelli che non ci sono si ignorano). */
  suspend fun delete(ids: Collection<String>) {
    if (ids.isEmpty()) return
    locked { write { ids.forEach { deleteOne(it) } } }
  }

  /** Svuota l'indice (lo schema e la chiave restano). I segni delle sorgenti ripartono da capo. */
  suspend fun deleteAll(): Unit = locked {
    write {
      db.execSQL("DELETE FROM docs")
      db.execSQL("DELETE FROM fts")
      db.execSQL("DELETE FROM forms")
      db.execSQL("DELETE FROM vectors")
      db.execSQL("DELETE FROM meta WHERE key LIKE 'cursor:%'")
      colTokens.fill(0)
      colDocs.fill(0)
    }
  }

  /**
   * Applica un pezzo di modifiche di una sorgente e ne salva il segno, **nella stessa transazione** (F8.22,
   * «l'aggiornamento a pezzi»): o tutto o niente. [sourceId] distingue le sorgenti dello stesso indice.
   */
  suspend fun apply(sourceId: String, changes: FluidSearchChanges): Unit = locked {
    write {
      changes.deletes.forEach { deleteOne(it) }
      changes.upserts.forEach { putOne(it) }
      setMetaText("cursor:$sourceId", changes.cursor)
    }
  }

  /** Il segno da cui riprende la sorgente [sourceId] (null: da capo). */
  suspend fun cursor(sourceId: String): String? = locked { metaText("cursor:$sourceId") }

  private suspend fun write(block: suspend () -> Unit) {
    db.beginTransaction()
    try {
      block()
      db.execSQL("DELETE FROM forms WHERE n <= 0")
      saveColumnStats()
      db.setTransactionSuccessful()
    } catch (e: Throwable) {
      // La transazione torna indietro: anche le lunghezze in memoria tornano quelle salvate.
      db.endTransaction()
      loadColumnStats()
      throw e
    }
    db.endTransaction()
    vocabularyCache = null
    vectorCache = null
    _generation.value = _generation.value + 1
  }

  private suspend fun putOne(doc: FluidSearchDocument) {
    val unknown = doc.fields.keys.filter { schema.field(it) == null }
    require(unknown.isEmpty()) { "FluidSearchIndex($name): campi che lo schema non ha: $unknown" }
    val existing = db.rawQuery("SELECT rid, forms, semkey, lens FROM docs WHERE id = ?", arrayOf(doc.id)).use {
      if (it.moveToFirst()) Triple(it.getLong(0), it.getString(1), it.getString(2)) to it.getString(3) else null
    }
    val columns = arrayOfNulls<String>(schema.fields.size)
    val forms = LinkedHashMap<String, SearchToken>()
    schema.fields.forEachIndexed { i, field ->
      val text = doc[field.name]
      if (text.isEmpty()) {
        columns[i] = ""
        return@forEachIndexed
      }
      val tokens = SearchAnalyzer.tokens(text).filter { !it.stop }
      columns[i] = if (field.sensitive) {
        val keys = blind ?: error("FluidSearchIndex($name): il campo riservato '${field.name}' vuole un FluidSearchCipher")
        buildString {
          for (t in tokens) {
            if (isNotEmpty()) append(' ')
            append(keys.term(t.stem))
            val top = minOf(t.form.length - 1, BlindTerms.MaxPrefix)
            for (k in BlindTerms.MinPrefix..top) append(' ').append(keys.prefix(t.form.substring(0, k)))
          }
        }
      } else {
        for (t in tokens) forms.putIfAbsent(t.form, t)
        tokens.joinToString(" ") { it.stem }
      }
    }
    val lens = columns.joinToString(",") { c -> if (c.isNullOrEmpty()) "0" else (c.count { it == ' ' } + 1).toString() }
    val stored = serialize(schema.fields.filter { it.stored }.associate { it.name to doc[it.name] }.filterValues { it.isNotEmpty() })
    val semanticText = semanticText(doc.fields)
    val semkey = semanticText?.let { digest(it) }
    val sealed = cipher?.seal(stored, associated("doc|${doc.id}")) ?: stored
    val values = ContentValues().apply {
      put("id", doc.id)
      put("kind", doc.kind)
      if (doc.timestamp != null) put("ts", doc.timestamp) else putNull("ts")
      put("boost", doc.boost.toDouble())
      put("link", doc.link)
      put("stored", sealed)
      put("forms", forms.keys.joinToString(" "))
      put("lens", lens)
      put("semkey", semkey)
    }
    val rid: Long
    if (existing != null) {
      val (old, oldLens) = existing
      rid = old.first
      decrementForms(old.second)
      addLengths(oldLens, -1)
      db.delete("fts", "docid = ?", arrayOf(rid.toString()))
      if (old.third != semkey) {
        db.delete("vectors", "rid = ?", arrayOf(rid.toString()))
        values.putNull("veckey")
      }
      db.update("docs", values, "rid = ?", arrayOf(rid.toString()))
    } else {
      rid = db.insertOrThrow("docs", null, values)
    }
    val fts = ContentValues().apply {
      put("docid", rid)
      columns.forEachIndexed { i, c -> put("f$i", c) }
    }
    db.insertOrThrow("fts", null, fts)
    addLengths(lens, +1)
    for (t in forms.values) incrementForm(t)
  }

  private fun deleteOne(id: String) {
    val row = db.rawQuery("SELECT rid, forms, lens FROM docs WHERE id = ?", arrayOf(id)).use {
      if (it.moveToFirst()) Triple(it.getLong(0), it.getString(1), it.getString(2)) else null
    } ?: return
    decrementForms(row.second)
    addLengths(row.third, -1)
    db.delete("fts", "docid = ?", arrayOf(row.first.toString()))
    db.delete("vectors", "rid = ?", arrayOf(row.first.toString()))
    db.delete("docs", "rid = ?", arrayOf(row.first.toString()))
  }

  private fun incrementForm(t: SearchToken) {
    val updated = db.compileStatement("UPDATE forms SET n = n + 1 WHERE form = ?").use {
      it.bindString(1, t.form)
      it.executeUpdateDelete()
    }
    if (updated == 0) {
      db.insertOrThrow("forms", null, ContentValues().apply {
        put("form", t.form)
        put("stem", t.stem)
        put("display", t.display)
        put("n", 1)
      })
    }
  }

  private fun decrementForms(forms: String?) {
    if (forms.isNullOrEmpty()) return
    db.compileStatement("UPDATE forms SET n = n - 1 WHERE form = ?").use { st ->
      for (f in forms.split(' ')) {
        if (f.isEmpty()) continue
        st.clearBindings()
        st.bindString(1, f)
        st.executeUpdateDelete()
      }
    }
  }

  // --- La ricerca -----------------------------------------------------------------------------------------------

  /** Cerca [text] (scorciatoia di [search] con una [FluidSearchQuery]). */
  suspend fun search(text: String, limit: Int = 50): FluidSearchResults = search(FluidSearchQuery(text, limit = limit))

  /** Cerca: per parole e, con [FluidSearchQuery.meaning], per significato. */
  suspend fun search(query: FluidSearchQuery): FluidSearchResults = locked {
    val started = SystemClock.elapsedRealtime()
    val plan = SearchPlanner.plan(query.text, vocabulary(), blind.takeIf { query.includeSensitive }, query.typos)
    val lexical = if (plan.empty) emptyList() else lexical(plan, query.kinds)
    val semantic = query.meaning?.let { meaning(it, query.kinds) }.orEmpty()
    val merged = merge(lexical, semantic)
    val top = merged.take(query.limit)
    val hits = top.mapNotNull { hit(it, plan, query.includeSensitive) }
    FluidSearchResults(
      query = query.text,
      hits = hits,
      total = merged.size,
      corrected = plan.corrected?.takeIf { lexical.isNotEmpty() },
      tookMillis = SystemClock.elapsedRealtime() - started,
      meaning = query.meaning != null,
    )
  }

  private class Candidate(
    val rid: Long,
    val id: String,
    val kind: String,
    val ts: Long?,
    val link: String?,
    var score: Float,
    val lexical: Boolean,
    var semantic: Boolean = false,
    var similarity: Float? = null,
  )

  private fun lexical(plan: QueryPlan, kinds: Set<String>): List<Candidate> {
    val args = ArrayList<String>()
    args += plan.expression
    val kindFilter = if (kinds.isEmpty()) "" else {
      args += kinds
      " AND d.kind IN (${kinds.joinToString(",") { "?" }})"
    }
    val rows = ArrayList<Pair<Candidate, Int>>()
    val averages = averages()
    db.rawQuery(
      "SELECT d.rid, d.id, d.kind, d.ts, d.boost, d.link, matchinfo(fts, 'pcnalx') FROM fts JOIN docs d ON d.rid = fts.docid " +
        "WHERE fts MATCH ?$kindFilter",
      args.toTypedArray(),
    ).use { c ->
      while (c.moveToNext()) {
        val scored = SearchScoring.score(plan, MatchInfo.of(c.getBlob(6)), weights, averages)
        if (scored.matchedGroups == 0) continue
        val candidate = Candidate(
          rid = c.getLong(0),
          id = c.getString(1),
          kind = c.getString(2),
          ts = if (c.isNull(3)) null else c.getLong(3),
          link = c.getString(5),
          score = scored.score * c.getFloat(4),
          lexical = true,
        )
        rows += candidate to scored.matchedGroups
      }
    }
    // Tutte le parole della domanda; se nessun documento le ha tutte, quelli a cui ne manca una, piu' in basso.
    val all = rows.filter { it.second >= plan.groups }.map { it.first }
    val result = if (all.isNotEmpty() || plan.groups < 2) all else {
      rows.filter { it.second >= plan.groups - 1 }.map { it.first.also { c -> c.score *= RelaxedFactor } }
    }
    return result.sortedWith(compareByDescending<Candidate> { it.score }.thenByDescending { it.ts ?: Long.MIN_VALUE })
  }

  private fun merge(lexical: List<Candidate>, semantic: List<SemanticHit>): List<Candidate> {
    if (semantic.isEmpty()) return lexical
    // L'unione per rango (reciprocal rank fusion, Cormack e altri, SIGIR 2009) con k = 10: un'altra scala di punteggi
    // non si somma, i ranghi si'. Le parole pesano 1, il significato 0,8 (a occhio, 25/11/2026: chi scrive una parola
    // esatta la vuole in cima).
    val byRid = LinkedHashMap<Long, Candidate>()
    lexical.forEachIndexed { rank, c ->
      c.score = 1f / (RrfK + rank + 1)
      byRid[c.rid] = c
    }
    semantic.forEachIndexed { rank, s ->
      val add = MeaningWeight / (RrfK + rank + 1)
      val c = byRid[s.rid] ?: docCandidate(s.rid)?.also { byRid[s.rid] = it } ?: return@forEachIndexed
      c.score += add
      c.semantic = true
      c.similarity = s.cosine
    }
    return byRid.values.sortedWith(compareByDescending<Candidate> { it.score }.thenByDescending { it.lexical }.thenByDescending { it.ts ?: Long.MIN_VALUE })
  }

  private fun docCandidate(rid: Long): Candidate? =
    db.rawQuery("SELECT id, kind, ts, link FROM docs WHERE rid = ?", arrayOf(rid.toString())).use { c ->
      if (!c.moveToFirst()) null else Candidate(
        rid = rid,
        id = c.getString(0),
        kind = c.getString(1),
        ts = if (c.isNull(2)) null else c.getLong(2),
        link = c.getString(3),
        score = 0f,
        lexical = false,
      )
    }

  private suspend fun storedOf(rid: Long, id: String): Map<String, String> {
    val blob = db.rawQuery("SELECT stored FROM docs WHERE rid = ?", arrayOf(rid.toString())).use {
      if (it.moveToFirst()) it.getBlob(0) else null
    } ?: return emptyMap()
    val plain = cipher?.open(blob, associated("doc|$id")) ?: blob
    return deserialize(plain)
  }

  private suspend fun hit(c: Candidate, plan: QueryPlan, sensitive: Boolean): FluidSearchHit? {
    val all = storedOf(c.rid, c.id)
    val fields = if (sensitive) all else all.filterKeys { schema.field(it)?.sensitive != true }
    val titleText = schema.title?.let { fields[it] }
    val title = titleText?.let { SearchSnippets.whole(it, plan) }
    // Il pezzo dal campo con piu' parole trovate; se le parole stanno solo nel titolo, l'inizio del primo campo.
    var snippet: FluidSearchSnippet? = null
    for (field in schema.snippets) {
      val text = fields[field] ?: continue
      val s = SearchSnippets.best(text, plan) ?: continue
      if (snippet == null || s.highlights.size > snippet.highlights.size) snippet = s
    }
    return FluidSearchHit(
      id = c.id,
      kind = c.kind,
      score = c.score,
      title = title,
      snippet = snippet,
      fields = fields,
      timestamp = c.ts,
      link = c.link,
      byWords = c.lexical,
      byMeaning = c.semantic,
      similarity = c.similarity,
    )
  }

  /** Le parole dell'indice che cominciano con [prefix], dalla piu' frequente: per i suggerimenti mentre si scrive. */
  suspend fun completions(prefix: String, limit: Int = 5): List<String> = locked {
    val form = SearchAnalyzer.fold(prefix.trim())
    if (form.length < 2) return@locked emptyList()
    db.rawQuery(
      "SELECT display FROM forms WHERE form >= ? AND form < ? ORDER BY n DESC, form LIMIT ?",
      arrayOf(form, form + '￿', limit.toString()),
    ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
  }

  /** Quanti documenti ci sono. */
  suspend fun count(): Int = locked { countDocs() }

  private fun countDocs(): Int = db.rawQuery("SELECT COUNT(*) FROM docs", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

  /** Le misure dell'indice: documenti, parole del vocabolario, vettori e byte sul disco. */
  suspend fun stats(): Stats = locked {
    val words = db.rawQuery("SELECT COUNT(*) FROM forms", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    val vectors = db.rawQuery("SELECT COUNT(*) FROM vectors", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    val bytes = file?.let { f -> listOf(f, File(f.path + "-wal"), File(f.path + "-journal")).sumOf { if (it.exists()) it.length() else 0L } } ?: 0L
    Stats(countDocs(), words, vectors, bytes)
  }

  // --- Le domande recenti ---------------------------------------------------------------------------------------

  /** Le domande recenti, dalla piu' nuova (al piu' [MaxRecents]). Stanno nell'indice: si cancellano con lui. */
  suspend fun recents(): List<String> = locked { readRecents() }

  /** Ricorda [text] fra le recenti (in cima; una gia' presente sale). */
  suspend fun addRecent(text: String) {
    val clean = text.trim().replace(Regex("\\s+"), " ")
    if (clean.isEmpty()) return
    locked { writeRecents(listOf(clean) + readRecents().filter { !it.equals(clean, ignoreCase = true) }) }
  }

  /** Toglie [text] dalle recenti. */
  suspend fun removeRecent(text: String): Unit = locked { writeRecents(readRecents().filter { it != text }) }

  /** Dimentica tutte le recenti. */
  suspend fun clearRecents(): Unit = locked { writeRecents(emptyList()) }

  private suspend fun readRecents(): List<String> {
    val blob = meta("recents") ?: return emptyList()
    val plain = try {
      cipher?.open(blob, associated("recents")) ?: blob
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      return emptyList()
    }
    return plain.toString(Charsets.UTF_8).split('\n').filter { it.isNotEmpty() }
  }

  private suspend fun writeRecents(list: List<String>) {
    val plain = list.take(MaxRecents).joinToString("\n").toByteArray(Charsets.UTF_8)
    setMeta("recents", if (list.isEmpty()) null else cipher?.seal(plain, associated("recents")) ?: plain)
    _generation.value = _generation.value + 1
  }

  // --- Il significato -------------------------------------------------------------------------------------------

  /**
   * Un documento che aspetta i suoi vettori: i pezzi del testo da dare al modello ([passages]) e la chiave del testo
   * ([key]), da ripassare a [putMeaning] (se nel frattempo il documento cambia, i vettori vecchi non entrano).
   */
  class PendingMeaning(val id: String, val key: String, val passages: List<String>)

  /** Fino a [limit] documenti senza vettori, o coi vettori di un testo vecchio. */
  suspend fun pendingMeaning(limit: Int, passageChars: Int = PassageChars, maxPassages: Int = MaxPassages): List<PendingMeaning> = locked {
    val rows = db.rawQuery(
      "SELECT rid, id, semkey FROM docs WHERE semkey IS NOT NULL AND (veckey IS NULL OR veckey != semkey) LIMIT ?",
      arrayOf(limit.toString()),
    ).use { c -> buildList { while (c.moveToNext()) add(Triple(c.getLong(0), c.getString(1), c.getString(2))) } }
    rows.mapNotNull { (rid, id, key) ->
      val text = semanticParts(storedOf(rid, id)) ?: return@mapNotNull null
      PendingMeaning(id, key, passages(text.first, text.second, passageChars, maxPassages))
    }
  }

  /** Lo spazio dei vettori che l'indice conserva («id/dimensioni» di [FluidEmbeddingSpace]), o null se non ne ha. */
  suspend fun meaningSpace(): String? = locked { metaText("space") }

  /** Vero se l'indice conserva vettori dello spazio [space]: solo allora una domanda per significato ha senso. */
  suspend fun usesMeaningSpace(space: FluidEmbeddingSpace): Boolean = meaningSpace() == "${space.id}/${space.dimensions}"

  /**
   * Usa lo spazio [space] per i vettori: se l'indice ne conservava di un altro modello, li butta e li rifara' (vettori di
   * spazi diversi non si confrontano).
   */
  suspend fun useMeaningSpace(space: FluidEmbeddingSpace): Unit = locked {
    val id = "${space.id}/${space.dimensions}"
    if (metaText("space") != id) {
      db.beginTransaction()
      try {
        db.execSQL("DELETE FROM vectors")
        db.execSQL("UPDATE docs SET veckey = NULL")
        setMetaText("space", id)
        db.setTransactionSuccessful()
      } finally {
        db.endTransaction()
      }
      vectorCache = null
    }
  }

  /** Quanti documenti aspettano i vettori. */
  suspend fun pendingMeaningCount(): Int = locked {
    db.rawQuery("SELECT COUNT(*) FROM docs WHERE semkey IS NOT NULL AND (veckey IS NULL OR veckey != semkey)", null)
      .use { if (it.moveToFirst()) it.getInt(0) else 0 }
  }

  /**
   * I vettori di un documento, uno per pezzo di [PendingMeaning.passages], gia' normalizzati (norma 1). Si salvano a 8 bit
   * con la scala (un errore sul coseno sotto 0,002 col modello di serie, misurato il 25/11/2026). Vero se sono entrati;
   * falso se il documento nel frattempo e' cambiato o sparito.
   */
  suspend fun putMeaning(pending: PendingMeaning, vectors: List<FloatArray>): Boolean = locked {
    val rid = db.rawQuery("SELECT rid FROM docs WHERE id = ? AND semkey = ?", arrayOf(pending.id, pending.key))
      .use { if (it.moveToFirst()) it.getLong(0) else null } ?: return@locked false
    db.beginTransaction()
    try {
      db.delete("vectors", "rid = ?", arrayOf(rid.toString()))
      for ((chunk, v) in vectors.withIndex()) {
        val data = VectorMatrix.quantize(v)
        val stored = cipher?.seal(data, associated("vec|${pending.id}|$chunk")) ?: data
        db.insertOrThrow("vectors", null, ContentValues().apply {
          put("rid", rid)
          put("chunk", chunk)
          put("dims", v.size)
          put("data", stored)
        })
      }
      db.update("docs", ContentValues().apply { put("veckey", pending.key) }, "rid = ?", arrayOf(rid.toString()))
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
    vectorCache = null
    _generation.value = _generation.value + 1
    true
  }

  private class SemanticHit(val rid: Long, val cosine: Float)

  private suspend fun meaning(query: FloatArray, kinds: Set<String>): List<SemanticHit> {
    val matrix = vectorCache ?: loadVectors().also { vectorCache = it }
    return matrix.search(query, kinds, MeaningMinZ, MaxMeaningHits).map { SemanticHit(it.first, it.second) }
  }

  private suspend fun loadVectors(): VectorMatrix {
    val rows = ArrayList<VectorMatrix.Row>()
    db.rawQuery("SELECT v.rid, v.chunk, v.data, d.kind, d.id FROM vectors v JOIN docs d ON d.rid = v.rid", null).use { c ->
      while (c.moveToNext()) {
        val rid = c.getLong(0)
        val chunk = c.getInt(1)
        val blob = c.getBlob(2)
        val id = c.getString(4)
        val data = try {
          cipher?.open(blob, associated("vec|$id|$chunk")) ?: blob
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          continue
        }
        rows += VectorMatrix.Row(rid, c.getString(3), data)
      }
    }
    return VectorMatrix(rows)
  }

  private fun semanticParts(fields: Map<String, String>): Pair<String?, String>? {
    val semantic = schema.fields.filter { it.semantic && it.stored && !it.sensitive }
    if (semantic.isEmpty()) return null
    val title = schema.title?.takeIf { t -> semantic.any { it.name == t } }?.let { fields[it] }?.takeIf { it.isNotBlank() }
    val body = semantic.filter { it.name != schema.title }.mapNotNull { fields[it.name]?.takeIf { s -> s.isNotBlank() } }.joinToString("\n")
    if (title == null && body.isBlank()) return null
    return title to body
  }

  private fun semanticText(fields: Map<String, String>): String? = semanticParts(fields)?.let { (t, b) -> listOfNotNull(t, b).joinToString("\n") }

  override fun close() {
    if (closed) return
    closed = true
    db.close()
    thread.shutdown()
  }

  /** Chiude l'indice e cancella il suo file: «l'indice si cancella insieme ai dati». */
  suspend fun destroy(): Unit = withContext(Dispatchers.IO) {
    mutex.withLock {
      close()
      file?.let { f -> listOf(f, File(f.path + "-wal"), File(f.path + "-shm"), File(f.path + "-journal")).forEach { it.delete() } }
    }
    Unit
  }

  private suspend fun prepareOnThread() = withContext(dispatcher) { prepare() }

  companion object {
    /** Il formato dei file: cambia se cambiano le tabelle o l'analisi (allora l'indice si rifa'). */
    private const val Format = "fluid-search-1"

    /** Quanto pesa un documento a cui manca una parola della domanda (quando nessuno le ha tutte). */
    private const val RelaxedFactor = 0.5f

    private const val RrfK = 10f
    private const val MeaningWeight = 0.8f

    /**
     * Da quanto sopra la media un pezzo e' «simile per significato»: 1,75 deviazioni (a occhio, 25/11/2026, dalle dieci
     * domande del corpus con EmbeddingGemma 2 270M: i documenti giusti stanno fra 1,8 e 3,8, gli altri sotto 1,7).
     */
    const val MeaningMinZ: Float = 1.75f

    /** Quanti risultati al piu' arrivano dal significato. */
    const val MaxMeaningHits: Int = 20

    /** Le domande recenti che si tengono (come la ricerca di Mail e Note di iOS, che ne mostra poche). */
    const val MaxRecents: Int = 10

    /** Quanto e' lungo un pezzo di testo per il modello: 700 caratteri, circa 200 parti di parola (il modello ne legge 256). */
    const val PassageChars: Int = 700

    /** Quanti pezzi al piu' per documento (oltre, il resto del testo si cerca solo per parole). */
    const val MaxPassages: Int = 6

    /**
     * Apre (o crea) l'indice [name] dell'app, con lo [schema] dato. Se lo schema e' cambiato l'indice si rifa' vuoto e
     * [rebuilt] e' vero. Con [cipher] il testo conservato si chiude con la cassaforte.
     */
    suspend fun open(
      context: Context,
      schema: FluidSearchSchema,
      cipher: FluidSearchCipher? = null,
      name: String = schema.name,
    ): FluidSearchIndex = withContext(Dispatchers.IO) {
      require(cipher != null || !schema.hasSensitive) { "FluidSearchIndex($name): i campi riservati vogliono un FluidSearchCipher" }
      val dir = File(context.noBackupFilesDir, "fluid-search").apply { mkdirs() }
      val file = File(dir, "$name.db")
      val db = SQLiteDatabase.openOrCreateDatabase(file, null)
      FluidSearchIndex(schema, db, file, cipher, name).also { it.prepareOnThread() }
    }

    /** Un indice in memoria, che sparisce chiudendolo: per le prove e le pagine di prova. */
    suspend fun inMemory(schema: FluidSearchSchema, cipher: FluidSearchCipher? = null): FluidSearchIndex = withContext(Dispatchers.IO) {
      require(cipher != null || !schema.hasSensitive) { "FluidSearchIndex(${schema.name}): i campi riservati vogliono un FluidSearchCipher" }
      FluidSearchIndex(schema, SQLiteDatabase.create(null), null, cipher, schema.name).also { it.prepareOnThread() }
    }

    /** Il file dell'indice [name] dell'app (anche se non e' aperto): per misurarlo o cancellarlo. */
    fun fileOf(context: Context, name: String): File = File(File(context.noBackupFilesDir, "fluid-search"), "$name.db")

    internal fun digest(text: String): String {
      val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
      return bytes.take(12).joinToString("") { "%02x".format(it) }
    }

    /** I pezzi per il modello: il titolo davanti a ogni pezzo del corpo, tagliato alle frasi. */
    internal fun passages(title: String?, body: String, chars: Int, max: Int): List<String> {
      val head = title?.let { "$it. " }.orEmpty()
      if (body.isBlank()) return listOfNotNull(title)
      val room = (chars - head.length).coerceAtLeast(chars / 2)
      val out = ArrayList<String>()
      var rest = body.trim()
      while (rest.isNotEmpty() && out.size < max) {
        if (rest.length <= room) {
          out += head + rest
          break
        }
        var cut = rest.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'), room)
        if (cut < room / 2) cut = rest.lastIndexOf(' ', room).takeIf { it > room / 2 } ?: room
        out += head + rest.substring(0, cut + 1).trim()
        rest = rest.substring(cut + 1).trim()
      }
      return out
    }

    internal fun serialize(fields: Map<String, String>): ByteArray {
      val bytes = ByteArrayOutputStream()
      DataOutputStream(bytes).use { out ->
        out.writeInt(fields.size)
        for ((k, v) in fields) {
          writeText(out, k)
          writeText(out, v)
        }
      }
      return bytes.toByteArray()
    }

    private fun writeText(out: DataOutputStream, text: String) {
      val b = text.toByteArray(Charsets.UTF_8)
      out.writeInt(b.size)
      out.write(b)
    }

    internal fun deserialize(bytes: ByteArray): Map<String, String> {
      if (bytes.isEmpty()) return emptyMap()
      val input = DataInputStream(ByteArrayInputStream(bytes))
      val n = input.readInt()
      val out = LinkedHashMap<String, String>(n)
      repeat(n) {
        val k = readText(input)
        out[k] = readText(input)
      }
      return out
    }

    private fun readText(input: DataInputStream): String {
      val size = input.readInt()
      val b = ByteArray(size)
      input.readFully(b)
      return b.toString(Charsets.UTF_8)
    }
  }

  // --- Il vocabolario in memoria ----------------------------------------------------------------------------------

  private fun vocabulary(): SearchVocabulary = vocabularyCache ?: loadVocabulary().also { vocabularyCache = it }

  private fun loadVocabulary(): MemoryVocabulary {
    val entries = ArrayList<VocabularyEntry>()
    db.rawQuery("SELECT form, stem, display, n FROM forms ORDER BY form", null).use { c ->
      while (c.moveToNext()) entries += VocabularyEntry(c.getString(0), c.getString(1), c.getString(2), c.getInt(3))
    }
    return MemoryVocabulary(entries)
  }
}

/** Il vocabolario in memoria: le forme in ordine (per gli inizi), le radici (per le parole intere), e per lunghezza. */
internal class MemoryVocabulary(private val sorted: List<VocabularyEntry>) : SearchVocabulary {
  private val stems: Set<String> = sorted.mapTo(HashSet(sorted.size)) { it.stem }
  private val byLength: Map<Int, List<VocabularyEntry>> = sorted.groupBy { it.form.length }

  override fun hasStem(stem: String): Boolean = stem in stems

  override fun hasPrefix(prefix: String): Boolean {
    var lo = 0
    var hi = sorted.size
    while (lo < hi) {
      val mid = (lo + hi) ushr 1
      if (sorted[mid].form < prefix) lo = mid + 1 else hi = mid
    }
    return lo < sorted.size && sorted[lo].form.startsWith(prefix)
  }

  override fun near(form: String, maxDistance: Int, limit: Int): List<Pair<VocabularyEntry, Int>> {
    val found = ArrayList<Pair<VocabularyEntry, Int>>()
    for (len in (form.length - maxDistance)..(form.length + maxDistance)) {
      val bucket = byLength[len] ?: continue
      for (e in bucket) {
        val d = SearchAnalyzer.distance(form, e.form, maxDistance)
        if (d in 1..maxDistance) found += e to d
      }
    }
    return found.sortedWith(compareBy<Pair<VocabularyEntry, Int>> { it.second }.thenByDescending { it.first.docs }).distinctBy { it.first.stem }.take(limit)
  }
}

/**
 * I vettori dell'indice in memoria, a 8 bit con la scala: una matrice piatta, la somiglianza col coseno, e i pezzi
 * «sopra la media» per scarti tipo ([search]).
 */
internal class VectorMatrix(rows: List<Row>) {
  class Row(val rid: Long, val kind: String, val data: ByteArray)

  private val rids = LongArray(rows.size) { rows[it].rid }
  private val kinds = Array(rows.size) { rows[it].kind }
  private val scales = FloatArray(rows.size)
  private val dims = IntArray(rows.size)
  private val values: Array<ByteArray> = Array(rows.size) { i ->
    val buffer = java.nio.ByteBuffer.wrap(rows[i].data)
    scales[i] = buffer.float
    dims[i] = rows[i].data.size - 4
    rows[i].data.copyOfRange(4, rows[i].data.size)
  }

  val size: Int get() = rids.size

  /** I documenti piu' simili a [query]: (rid, coseno), solo quelli a [minZ] scarti tipo sopra la media, al piu' [limit]. */
  fun search(query: FloatArray, kinds: Set<String>, minZ: Float, limit: Int): List<Pair<Long, Float>> {
    if (size == 0) return emptyList()
    val cos = FloatArray(size)
    var sum = 0.0
    var sumSq = 0.0
    val normalized = HashMap<Int, FloatArray>()
    for (i in 0 until size) {
      val d = dims[i]
      val q = normalized.getOrPut(d) { normalize(query, d) }
      val row = values[i]
      var dot = 0f
      for (j in 0 until minOf(d, q.size)) dot += row[j] * q[j]
      val c = dot * scales[i]
      cos[i] = c
      sum += c
      sumSq += c * c
    }
    val mean = sum / size
    val std = kotlin.math.sqrt((sumSq / size - mean * mean).coerceAtLeast(1e-9))
    // Il migliore di ogni documento (i suoi pezzi), fra i tipi chiesti.
    val best = HashMap<Long, Float>()
    for (i in 0 until size) {
      if (kinds.isNotEmpty() && this.kinds[i] !in kinds) continue
      val prev = best[rids[i]]
      if (prev == null || cos[i] > prev) best[rids[i]] = cos[i]
    }
    val strong = best.filterValues { (it - mean) / std >= minZ }
    val top = strong.values.maxOrNull() ?: return emptyList()
    // Accanto a un documento molto simile restano quelli quasi uguali a lui (le due mail dello stesso studio in un
    // indice piccolo, dove gli scarti tipo ballano), anche se sono appena sotto la soglia.
    return best.entries.filter { it.key in strong || it.value >= top - NearTop }
      .sortedByDescending { it.value }.take(limit).map { it.key to it.value }
  }

  companion object {
    /**
     * Quanto sotto il piu' simile un documento resta lo stesso: 0,03 di coseno (a occhio, 25/11/2026: le due mail dello
     * studio dentistico stanno a 0,025 l'una dall'altra, il primo documento diverso a 0,05).
     */
    const val NearTop: Float = 0.03f

    /** [v] (norma 1) a 8 bit: la scala in float, poi un byte per componente. */
    fun quantize(v: FloatArray): ByteArray {
      var max = 0f
      for (x in v) max = maxOf(max, kotlin.math.abs(x))
      val scale = if (max == 0f) 1f else max / 127f
      val buffer = java.nio.ByteBuffer.allocate(4 + v.size)
      buffer.putFloat(scale)
      for (x in v) buffer.put(Math.round(x / scale).coerceIn(-127, 127).toByte())
      return buffer.array()
    }

    /** I primi [dims] valori di [v], di nuovo a norma 1 (i vettori Matrjoska si tagliano cosi'). */
    fun normalize(v: FloatArray, dims: Int): FloatArray {
      val n = minOf(dims, v.size)
      var s = 0.0
      for (i in 0 until n) s += v[i] * v[i]
      val inv = if (s == 0.0) 0f else (1.0 / kotlin.math.sqrt(s)).toFloat()
      return FloatArray(n) { v[it] * inv }
    }
  }
}
