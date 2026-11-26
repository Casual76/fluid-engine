package dev.antigravity.fluidengine.ai.tools

import java.text.Normalizer
import kotlin.math.ln
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Uno strumento trovato dalla ricerca (4.0, F9.2): il tool, il suo punteggio e le parole della richiesta
 * che l'hanno trovato.
 */
data class ToolHit<C>(val tool: AiTool<C>, val score: Double, val matched: Set<String>)

/**
 * La ricerca dei tool (4.0, F9.2): trova fra **tutti** gli strumenti del catalogo quelli che servono a
 * una richiesta scritta in poche parole, senza chiamare nessun modello. E' quello che sta dietro al
 * tool built-in `cerca_strumenti` ([ToolRegistry.SEARCH_TOOLS]) e al precaricamento della prima domanda
 * ([dev.antigravity.fluidengine.ai.orchestrator.AiOrchestratorConfig.searchPreload]).
 *
 * Perche' esiste: in PampAI gli schemi delle altre app (registro, meteo, bus, convertitore, Fluidify)
 * sono 139 tool e ~23.000 gettoni (misura F9.2 col tokenizer di Qwen3). Caricarli di serie, o elencarli
 * tutti nella descrizione di `apri_categoria` e nel prompt del router, costa a ogni giro anche quando la
 * domanda e' "spiegami la fotosintesi". Con i gruppi differiti ([ToolRegistry.deferred]) il modello vede
 * solo gli strumenti dell'app da cui si parla e questo tool; gli altri arrivano quando servono.
 *
 * Com'e' fatta: BM25 su cinque campi pesati diversamente (nome, descrizione, parametri, riga del gruppo,
 * categoria e app), con le parole normalizzate all'italiano (minuscole, senza accenti, senza le parole
 * vuote) e una radice corta: due parole si incontrano se condividono le prime [STEM] lettere ("previsioni"
 * e "previsione"; "piovera'" e "pioggia" no: per quello ci sono le parole in piu' del gruppo). Le parole
 * in piu' di un gruppo (il vocabolario di un'app collegata: le materie, le fermate, le localita' salvate,
 * le parole del mestiere) contano come la sua riga. Il punteggio di un tool e' quello dei suoi campi piu'
 * meta' di quello del suo gruppo: "il meteo di domani" trova i tool del meteo anche se nessuna descrizione
 * dice "meteo". I pesi sono a occhio (26 novembre 2026), provati sul banco di F9.2 (`ToolSearchBenchTest`,
 * i numeri nel rapporto della sessione).
 *
 * Non ha stato che cambi: si costruisce una volta per catalogo ([ToolRegistry.search]) e si interroga da
 * piu' fili.
 */
class ToolSearch<C>(
  private val tools: List<AiTool<C>>,
  /** Parole in piu' per gruppo: entrano nel campo del gruppo. */
  extraTerms: Map<AiToolGroup, List<String>> = emptyMap(),
) {

  private class Doc(val fields: Array<List<String>>, val length: Double)

  private val docs: List<Doc> = tools.map { tool ->
    val params = buildList {
      (tool.parameters["properties"] as? JsonObject)?.forEach { (name, schema) ->
        add(name)
        ((schema as? JsonObject)?.get("description") as? JsonPrimitive)?.content?.let { add(it) }
      }
    }
    val category = tool.group.resolvedCategory
    val fields = arrayOf(
      terms(tool.name.replace('_', ' ')),
      terms(tool.description),
      terms(params.joinToString(" ")),
      terms(tool.group.id.replace('_', ' ') + " " + tool.group.hint + " " + (tool.group.parent?.hint ?: "") + " " + extraTerms[tool.group].orEmpty().joinToString(" ")),
      terms((category?.id?.replace('_', ' ') ?: "") + " " + (category?.label ?: "") + " " + (category?.hint ?: "")),
    )
    Doc(fields, fields.indices.sumOf { fields[it].size * WEIGHTS[it] })
  }

  private val groupDocs: Map<AiToolGroup, List<String>> = tools.map { it.group }.distinct().associateWith { group ->
    val category = group.resolvedCategory
    terms(
      group.id.replace('_', ' ') + " " + group.hint + " " + (category?.label ?: "") + " " + (category?.hint ?: "") + " " +
        extraTerms[group].orEmpty().joinToString(" "),
    )
  }

  private val documentFrequency = HashMap<String, Int>().also { df ->
    docs.forEach { doc -> doc.fields.flatMap { it }.toSet().forEach { df[it] = (df[it] ?: 0) + 1 } }
  }
  private val groupFrequency = HashMap<String, Int>().also { df ->
    groupDocs.values.forEach { words -> words.toSet().forEach { df[it] = (df[it] ?: 0) + 1 } }
  }
  private val averageLength: Double = docs.map { it.length }.average().takeIf { !it.isNaN() && it > 0 } ?: 1.0
  private val groupAverage: Double = groupDocs.values.map { it.size.toDouble() }.average().takeIf { !it.isNaN() && it > 0 } ?: 1.0

  /**
   * I tool che rispondono a [query], dal piu' adatto, al massimo [limit], esclusi quelli di [exclude]
   * (di solito quelli che il modello ha gia'). Un tool entra solo se una parola della richiesta lo trova
   * nei suoi campi (nome, descrizione, parametri, riga e parole del gruppo): la sola categoria non basta,
   * se no "il radar" porterebbe dentro tutti gli strumenti dell'app del meteo. E resta fuori chi ha meno
   * di [minRelative] del punteggio del primo: ogni tool in piu' sono ~150 gettoni a ogni giro dopo.
   * Vuoto se niente corrisponde.
   */
  fun find(query: String, limit: Int = DEFAULT_LIMIT, exclude: Set<String> = emptySet(), minRelative: Double = MIN_RELATIVE): List<ToolHit<C>> {
    val words = terms(query).distinct()
    if (words.isEmpty() || tools.isEmpty() || limit <= 0) return emptyList()
    // L'idf di ogni parola si calcola una volta per richiesta: con la radice corta una parola conta piu' termini.
    val toolIdf = words.associateWith { idf(it, documentFrequency, docs.size) }
    val groupIdf = words.associateWith { idf(it, groupFrequency, groupDocs.size) }
    val groupScores = groupDocs.mapValues { (_, doc) -> score(words, doc, groupIdf, doc.size.toDouble(), groupAverage) }
    val hits = ArrayList<Pair<Int, ToolHit<C>>>()
    tools.forEachIndexed { index, tool ->
      if (tool.name in exclude) return@forEachIndexed
      val doc = docs[index]
      val matched = words.filter { w -> (0 until CATEGORY_FIELD).any { f -> doc.fields[f].any { matches(w, it) } } }.toSet()
      if (matched.isEmpty()) return@forEachIndexed
      var total = 0.0
      doc.fields.forEachIndexed { field, list -> total += WEIGHTS[field] * score(words, list, toolIdf, doc.length, averageLength) }
      total += GROUP_SHARE * (groupScores[tool.group] ?: 0.0)
      hits += index to ToolHit(tool, total, matched)
    }
    val ranked = hits.sortedWith(compareByDescending<Pair<Int, ToolHit<C>>> { it.second.score }.thenBy { it.first }).map { it.second }
    val top = ranked.firstOrNull()?.score ?: return emptyList()
    return ranked.filter { it.score >= top * minRelative }.take(limit)
  }

  private fun idf(word: String, df: Map<String, Int>, count: Int): Double {
    val n = df.entries.sumOf { (term, f) -> if (matches(word, term)) f else 0 }.coerceAtMost(count)
    return ln(1.0 + (count - n + 0.5) / (n + 0.5))
  }

  /** BM25 di [words] su un campo, con l'idf gia' calcolato. */
  private fun score(words: List<String>, field: List<String>, idf: Map<String, Double>, length: Double, average: Double): Double {
    var sum = 0.0
    for (word in words) {
      var tf = 0.0
      for (term in field) if (term == word) tf += 1.0 else if (matches(word, term)) tf += STEM_WEIGHT
      if (tf == 0.0) continue
      sum += (idf[word] ?: 0.0) * (tf * (K1 + 1)) / (tf + K1 * (1 - B + B * length / average))
    }
    return sum
  }

  companion object {
    const val DEFAULT_LIMIT = 5

    /** La parte del punteggio del primo sotto cui un risultato non entra. A occhio, 26/11/2026, provato sul banco. */
    const val MIN_RELATIVE = 0.35

    /** L'indice del campo della categoria: conta nel punteggio, ma da solo non trova un tool. */
    private const val CATEGORY_FIELD = 4

    /** Le lettere che due parole devono avere in comune all'inizio per incontrarsi (la radice corta). */
    const val STEM = 5

    private const val K1 = 1.2
    private const val B = 0.6
    private const val GROUP_SHARE = 0.5

    /** Quanto vale una parola trovata solo per la radice ("evento" per "eventi"), rispetto a quella uguale. */
    private const val STEM_WEIGHT = 0.7

    /** I pesi dei campi: nome, descrizione, parametri, riga del gruppo, categoria e app. A occhio, 26/11/2026. */
    private val WEIGHTS = doubleArrayOf(2.0, 1.0, 0.4, 1.2, 0.8)

    private val STOP = setOf(
      "il", "lo", "la", "gli", "le", "un", "uno", "una", "di", "del", "dello", "della", "dei", "degli", "delle",
      "al", "allo", "alla", "ai", "agli", "alle", "da", "dal", "dalla", "dai", "dalle", "in", "nel", "nello", "nella",
      "nei", "negli", "nelle", "con", "su", "sul", "sulla", "sui", "per", "tra", "fra", "ed", "od", "che", "chi",
      "mi", "ti", "ci", "vi", "si", "ne", "non", "piu", "come", "cosa", "quale", "quali", "quanto", "quanti", "quando", "dove",
      "perche", "questo", "questa", "questi", "queste", "quello", "quella", "mio", "mia", "miei", "mie", "tuo", "tua", "suo",
      "sua", "ho", "hai", "ha", "abbiamo", "hanno", "sono", "sei", "era", "essere", "fare", "fa", "puoi", "posso", "voglio",
      "vorrei", "dimmi", "dammi", "me", "te", "se", "anche", "ma", "oppure", "gia", "solo", "tutto", "tutti", "tutte",
      "es", "eg", "the", "of", "to", "and", "or", "for", "vuoto", "facoltativo", "po", "un'", "c'e",
      // I verbi della domanda, che non dicono quale strumento: "spiegami la fotosintesi" non e' il meteo che spiega.
      "spiegami", "spiega", "spiegare", "fammi", "vedere", "dire", "devo", "sto", "ci", "puo", "potresti", "vuoi", "grazie",
    )

    /** Le parole di un testo per la ricerca: minuscole, senza accenti e apostrofi, senza parole vuote. */
    fun terms(text: String): List<String> {
      val plain = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
      return plain.split(Regex("[^a-z0-9]+")).filter { it.length >= 2 && it !in STOP }
    }

    /** Due parole si incontrano se sono uguali o se hanno in comune le prime [STEM] lettere. */
    fun matches(a: String, b: String): Boolean {
      if (a == b) return true
      if (a.length < STEM || b.length < STEM) return false
      return a.regionMatches(0, b, 0, STEM)
    }
  }
}
