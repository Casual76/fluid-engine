package dev.antigravity.fluidengine.ai.memory

import dev.antigravity.fluidengine.search.FluidSearchDocument
import dev.antigravity.fluidengine.search.FluidSearchField
import dev.antigravity.fluidengine.search.FluidSearchIndex
import dev.antigravity.fluidengine.search.FluidSearchQuery
import dev.antigravity.fluidengine.search.FluidSearchSchema
import dev.antigravity.fluidengine.search.FluidTextEmbedder
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Un ricordo trovato per una domanda: quanto c'entra ([score], piu' alto e' meglio) e se l'ha trovato il significato. */
data class AiMemoryMatch(val memory: AiMemory, val score: Float, val byMeaning: Boolean = false)

/**
 * La pertinenza: l'indice di `engine-search` (F8.22) sopra i ricordi, **solo in memoria**. Si rifa' dai ricordi a ogni
 * apertura (qualche centinaio di frasi brevi: millisecondi), cosi' sul disco non c'e' niente oltre al file cifrato della
 * cassaforte, nemmeno le radici delle parole che un indice FTS4 su file lascerebbe leggibili.
 *
 * L'indice di F8.22 vuole **tutte** le parole della domanda in un documento (e' una ricerca, «riunione budget»): una
 * domanda all'assistente («che compiti ho per domani?») invece nomina un ricordo con una parola sola, e le altre non ci
 * sono. Per questo ogni parola della domanda e' una ricerca a se' (con l'italiano dell'engine: accenti, apostrofi, plurali,
 * errori di battitura) e i punteggi BM25F si sommano: la pertinenza di una ricerca «in OR». Le parole del ricordo sono il
 * testo (peso 3), le parole per cui serve (`topics`, 2) e la chiave (1).
 *
 * Con un modello del significato ([FluidTextEmbedder], facoltativo) anche il vettore della domanda cerca, e i due elenchi
 * si uniscono per rango come in F8.22 (k = 10; le parole 1, il significato 0,8).
 */
internal class AiMemoryIndex private constructor(
  private val index: FluidSearchIndex,
  private val embedder: FluidTextEmbedder?,
) {
  /** Rifa' l'indice con [memories]; i vettori gia' calcolati arrivano da [vectors] e quelli nuovi tornano indietro. */
  suspend fun rebuild(memories: List<AiMemory>, vectors: Map<String, StoredVector>): Map<String, StoredVector> {
    index.deleteAll()
    if (memories.isNotEmpty()) index.put(memories.map(::document))
    return meaning(vectors)
  }

  /** Mette (o sostituisce) [memories]; torna i vettori nuovi da aggiungere a quelli salvati. */
  suspend fun put(memories: List<AiMemory>, vectors: Map<String, StoredVector>): Map<String, StoredVector> {
    if (memories.isEmpty()) return emptyMap()
    index.put(memories.map(::document))
    return meaning(vectors)
  }

  suspend fun delete(ids: Collection<String>) {
    if (ids.isNotEmpty()) index.delete(ids)
  }

  /**
   * I ricordi che c'entrano con [question], dal piu' pertinente: id e punteggio. Tiene quelli a [MinRelative] del
   * migliore o piu' (un ricordo nominato da una parola debole accanto a uno nominato da tre non entra), al piu' [limit].
   */
  suspend fun search(question: String, limit: Int, extra: List<String> = emptyList()): List<Triple<String, Float, Boolean>> {
    val lexical = HashMap<String, Float>()
    val direct = words(question)
    // Le parole del modello del telefono (AICore, la domanda allargata) contano un po' meno di quelle scritte.
    val expanded = extra.flatMap { words(it) }.filter { it !in direct }.distinct().take(MaxWords)
    for ((word, weight) in direct.map { it to 1f } + expanded.map { it to ExpandedWeight }) {
      // Lo spazio in fondo: la parola e' finita (l'indice non la tratta come un inizio che si sta scrivendo, e le parole
      // vuote, «che», «per», cadono).
      val hits = index.search(FluidSearchQuery("$word ", limit = FluidSearchQuery.MaxLimit, typos = word.length >= TypoMinLength)).hits
      for (h in hits) lexical[h.id] = (lexical[h.id] ?: 0f) + h.score * weight
    }
    val byWords = lexical.entries.sortedByDescending { it.value }
    val top = byWords.firstOrNull()?.value ?: 0f
    val kept = byWords.filter { it.value >= top * MinRelative }.map { it.key }
    val semantic = embedder?.let { e ->
      if (question.isBlank()) emptyList() else {
        val vector = e.embed(listOf(question)).first()
        index.search(FluidSearchQuery("", limit = limit, meaning = vector)).hits.filter { it.byMeaning }.map { it.id }
      }
    }.orEmpty()
    if (semantic.isEmpty()) return kept.take(limit).map { Triple(it, lexical.getValue(it), false) }
    // L'unione per rango (reciprocal rank fusion), con gli stessi numeri dell'indice di F8.22.
    val fused = LinkedHashMap<String, Float>()
    kept.forEachIndexed { rank, id -> fused[id] = 1f / (RrfK + rank + 1) }
    val meaningIds = HashSet<String>()
    semantic.forEachIndexed { rank, id ->
      meaningIds += id
      fused[id] = (fused[id] ?: 0f) + MeaningWeight / (RrfK + rank + 1)
    }
    return fused.entries.sortedByDescending { it.value }.take(limit).map { (id, s) -> Triple(id, s, id in meaningIds && id !in lexical) }
  }

  /** I vettori che mancano: quelli salvati per lo stesso testo si riusano, gli altri li calcola il modello. */
  private suspend fun meaning(saved: Map<String, StoredVector>): Map<String, StoredVector> {
    val e = embedder ?: return emptyMap()
    val fresh = HashMap<String, StoredVector>()
    // Un ricordo cambiato a meta' torna fra quelli che aspettano col testo nuovo: la guardia evita un giro senza fine.
    var rounds = 0
    while (rounds++ < MaxMeaningRounds) {
      val pending = index.pendingMeaning(limit = Batch)
      if (pending.isEmpty()) break
      val reuse = pending.filter { p -> saved[p.id]?.key == p.key && p.passages.size == 1 }
      reuse.forEach { p -> index.putMeaning(p, listOf(dequantize(saved.getValue(p.id).bytes))) }
      val compute = pending - reuse.toSet()
      if (compute.isEmpty()) continue
      val vectors = e.embed(compute.flatMap { it.passages })
      var at = 0
      for (p in compute) {
        val mine = vectors.subList(at, at + p.passages.size)
        at += p.passages.size
        if (index.putMeaning(p, mine)) fresh[p.id] = StoredVector(p.key, quantize(mine.first()))
      }
      if (reuse.isEmpty() && compute.size < Batch) break
    }
    return fresh
  }

  fun close() = index.close()

  companion object {
    /** Lo schema: il testo (peso 3), le parole per cui serve (2), la chiave in parole (1). */
    val Schema = FluidSearchSchema(
      name = "ai-memory",
      version = 1,
      fields = listOf(FluidSearchField("text", weight = 3f), FluidSearchField("topics", weight = 2f), FluidSearchField("key", weight = 1f)),
      title = "text",
    )

    /**
     * Sotto quale frazione del migliore un ricordo non c'entra abbastanza: 0,25 (a occhio, 26/11/2026, sul corpus di
     * `AiMemoryRelevanceTest`: i ricordi giusti stanno sopra 0,4 del migliore, quelli nominati da una parola di passaggio
     * sotto 0,2).
     */
    const val MinRelative: Float = 0.25f

    /**
     * Da quante lettere una parola della domanda si corregge (gli errori di battitura dell'indice di F8.22): **6**. A occhio
     * il 26/11/2026, sulle prove: una parola corta corretta diventa un'altra parola comune («cosa» diventa «casa» e porta
     * «abita a Firenze» in una domanda sulla cena), una lunga sbagliata resta riconoscibile («sorela», «verifca»).
     */
    const val TypoMinLength: Int = 6

    /** Quanto conta una parola della domanda allargata dal modello del telefono: **0,7** di una scritta (a occhio, 26/11/2026). */
    const val ExpandedWeight: Float = 0.7f

    /** Le parole della domanda che cercano, al piu': una domanda lunga non diventa trenta ricerche. */
    const val MaxWords: Int = 16

    private const val RrfK = 10f
    private const val MeaningWeight = 0.8f
    private const val Batch = 8
    private const val MaxMeaningRounds = 200

    suspend fun create(embedder: FluidTextEmbedder?): AiMemoryIndex {
      val index = FluidSearchIndex.inMemory(Schema)
      embedder?.let { index.useMeaningSpace(it.space) }
      return AiMemoryIndex(index, embedder)
    }

    /** Le parole di [question] da cercare: lettere e cifre, almeno 3 (2 con una cifra: «4F», «23»), senza doppioni. */
    internal fun words(question: String): List<String> =
      question.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length >= 3 || it.length == 2 && it.any(Char::isDigit) }
        .distinct().take(MaxWords)

    internal fun document(m: AiMemory) = FluidSearchDocument(
      id = m.id,
      fields = mapOf(
        "text" to m.text,
        "topics" to m.topics.joinToString(" "),
        "key" to (m.key?.replace(Regex("[._-]+"), " ") ?: ""),
      ),
      kind = "ricordo",
      timestamp = m.observedAtMillis,
    )

    /** [v] (norma 1) a 8 bit: la scala in float, poi un byte per componente (il formato dell'indice di F8.22). */
    internal fun quantize(v: FloatArray): ByteArray {
      val max = v.maxOfOrNull { abs(it) }?.takeIf { it > 0f } ?: 1f
      val scale = max / 127f
      val out = ByteBuffer.allocate(4 + v.size)
      out.putFloat(scale)
      v.forEach { out.put((it / scale).roundToInt().coerceIn(-127, 127).toByte()) }
      return out.array()
    }

    internal fun dequantize(bytes: ByteArray): FloatArray {
      val buf = ByteBuffer.wrap(bytes)
      val scale = buf.float
      val v = FloatArray(bytes.size - 4) { bytes[4 + it] * scale }
      val n = sqrt(v.fold(0.0) { a, x -> a + x * x }).toFloat().takeIf { it > 0f } ?: 1f
      return FloatArray(v.size) { v[it] / n }
    }
  }
}
