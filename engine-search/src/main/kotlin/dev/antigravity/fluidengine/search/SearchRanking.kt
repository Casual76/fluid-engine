package dev.antigravity.fluidengine.search

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ln

// La domanda diventa un piano (le varianti di ogni parola, con un peso), il piano diventa un'espressione FTS4 piatta
// (tutte le varianti in OR) e i risultati si ordinano qui, in Kotlin, con BM25F sui numeri di `matchinfo`. Il piano e
// l'ordine sono puri: si provano sulla JVM senza SQLite.

/** Una parola del vocabolario dell'indice: la forma piegata, la sua radice, come si scrive e in quanti documenti sta. */
internal class VocabularyEntry(val form: String, val stem: String, val display: String, val docs: Int)

/** Il vocabolario delle parole non riservate dell'indice: serve agli errori di battitura e alla parola corretta. */
internal interface SearchVocabulary {
  /** Vero se qualche documento ha la radice [stem]. */
  fun hasStem(stem: String): Boolean

  /** Vero se qualche parola comincia con [prefix]. */
  fun hasPrefix(prefix: String): Boolean

  /** Le parole a non piu' di [maxDistance] errori da [form], dalla piu' vicina e poi dalla piu' frequente. */
  fun near(form: String, maxDistance: Int, limit: Int): List<Pair<VocabularyEntry, Int>>

  companion object {
    val Empty: SearchVocabulary = object : SearchVocabulary {
      override fun hasStem(stem: String) = false
      override fun hasPrefix(prefix: String) = false
      override fun near(form: String, maxDistance: Int, limit: Int) = emptyList<Pair<VocabularyEntry, Int>>()
    }
  }
}

/** Come una variante riconosce una parola del testo, per evidenziarla: per radice o per inizio. */
internal sealed class TermMatch {
  class Stem(val stem: String) : TermMatch()
  class Prefix(val prefix: String) : TermMatch()
}

/** Una variante di una parola della domanda: l'espressione FTS ([phrase] nell'elenco del piano), il gruppo e il peso. */
internal class PlannedVariant(val phrase: Int, val group: Int, val weight: Float, val match: TermMatch)

/**
 * Il piano di una domanda.
 *
 * @property phrases le espressioni FTS4, una per frase di `matchinfo`, nell'ordine (si uniscono con « OR »).
 * @property groups quante parole ha la domanda (ogni parola e' un gruppo di varianti).
 * @property corrected la domanda con le parole corrette, se ce ne sono.
 */
internal class QueryPlan(
  val phrases: List<String>,
  val variants: List<PlannedVariant>,
  val groups: Int,
  val corrected: String?,
) {
  val empty: Boolean get() = phrases.isEmpty()

  /** L'espressione per `MATCH`. */
  val expression: String get() = phrases.joinToString(" OR ")

  companion object {
    val Empty = QueryPlan(emptyList(), emptyList(), 0, null)
  }
}

/** I pesi delle varianti (a occhio, 25/11/2026, sul corpus italiano delle prove: l'esatto prima, poi l'inizio, poi gli errori). */
internal object VariantWeights {
  const val Exact = 1f
  const val PrefixTyping = 0.9f
  const val Prefix = 0.75f
  const val Trimmed = 0.8f
  const val OneTypo = 0.6f
  const val TwoTypos = 0.42f
}

internal object SearchPlanner {
  /** Quante frasi al piu' in una domanda (oltre, le varianti meno pesanti si lasciano). */
  const val MaxPhrases: Int = 48

  /** Quante parole vicine si provano per una parola sbagliata. */
  const val MaxNear: Int = 4

  fun plan(
    text: String,
    vocabulary: SearchVocabulary,
    blind: BlindTerms?,
    typos: Boolean,
  ): QueryPlan {
    val tokens = SearchAnalyzer.tokens(text)
    if (tokens.isEmpty()) return QueryPlan.Empty
    // L'ultima parola si sta ancora scrivendo se il testo non finisce con uno spazio o un segno.
    val typing = text.isNotEmpty() && text.last().let { Character.isLetterOrDigit(it) || Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
    val terms = tokens.filterIndexed { i, t -> !t.stop || (i == tokens.lastIndex && typing) }
    if (terms.isEmpty()) return QueryPlan.Empty
    val phrases = LinkedHashMap<String, Int>()
    val variants = ArrayList<PlannedVariant>()
    var corrected = false
    val correctedWords = ArrayList<String>(terms.size)

    fun add(expr: String, group: Int, weight: Float, match: TermMatch) {
      val index = phrases.getOrPut(expr) { phrases.size }
      if (variants.none { it.phrase == index && it.group == group }) variants += PlannedVariant(index, group, weight, match)
    }

    terms.forEachIndexed { group, t ->
      val last = t === tokens.last() && typing
      val form = t.form
      // La parola intera, per radice: «riunioni» trova «riunione».
      if (!t.stop) add(t.stem, group, VariantWeights.Exact, TermMatch.Stem(t.stem))
      // L'inizio di parola, come Spotlight: ogni parola, e di piu' l'ultima, che si sta scrivendo.
      val prefixWeight = if (last) VariantWeights.PrefixTyping else VariantWeights.Prefix
      if (form.length >= 2 && (last || !t.stop)) add("$form*", group, prefixWeight, TermMatch.Prefix(form))
      // Chi scrive «verifich» sta scrivendo «verifiche», la cui radice e' «verific»: l'inizio scritto e' piu' lungo della
      // radice di una o due lettere tolte dalla radice leggera.
      if (last && form.length >= 5) {
        for (k in 1..2) {
          val cut = form.substring(0, form.length - k)
          val removed = form.substring(form.length - k)
          if (removed.all { it in "aeiouh" } && cut != t.stem) add(cut, group, VariantWeights.Trimmed, TermMatch.Stem(cut))
        }
      }
      // I campi riservati: le impronte della radice e, dalla terza lettera, dell'inizio.
      if (blind != null) {
        if (!t.stop) add(blind.term(t.stem), group, VariantWeights.Exact, TermMatch.Stem(t.stem))
        if (form.length >= BlindTerms.MinPrefix && (last || !t.stop)) {
          add(blind.prefix(form.take(BlindTerms.MaxPrefix)), group, prefixWeight, TermMatch.Prefix(form))
        }
      }
      // Gli errori di battitura: solo se la parola non c'e' (ne' intera ne' come inizio), cosi' «rossi» non diventa «rosso».
      var word = t.display
      if (typos && !t.stop) {
        val budget = SearchAnalyzer.typoBudget(form.length)
        val known = vocabulary.hasStem(t.stem) || ((last || form.length >= 2) && vocabulary.hasPrefix(form))
        if (budget > 0 && !known) {
          val near = vocabulary.near(form, budget, MaxNear)
          near.forEach { (entry, distance) ->
            val weight = if (distance <= 1) VariantWeights.OneTypo else VariantWeights.TwoTypos
            add(entry.stem, group, weight, TermMatch.Stem(entry.stem))
          }
          if (near.isNotEmpty()) {
            corrected = true
            word = near.first().first.display
          }
        }
      }
      correctedWords += word
    }
    // Troppe varianti (una domanda lunga piena di errori): si tengono le piu' pesanti.
    if (phrases.size > MaxPhrases) {
      val keep = variants.sortedByDescending { it.weight }.map { it.phrase }.distinct().take(MaxPhrases).toSet()
      val order = phrases.entries.filter { it.value in keep }.map { it.key }
      val remap = order.withIndex().associate { (i, expr) -> phrases.getValue(expr) to i }
      val kept = variants.filter { it.phrase in keep }.map { PlannedVariant(remap.getValue(it.phrase), it.group, it.weight, it.match) }
      return QueryPlan(order, kept, terms.size, correctedWords.joinToString(" ").takeIf { corrected })
    }
    return QueryPlan(phrases.keys.toList(), variants, terms.size, correctedWords.joinToString(" ").takeIf { corrected })
  }
}

/**
 * I numeri di `matchinfo(fts, 'pcnalx')` di una riga: frasi, colonne, righe, lunghezza media e di questa riga per colonna,
 * e per ogni frase e colonna le tre cifre (presenze qui, presenze in tutto l'indice, documenti con almeno una).
 */
internal class MatchInfo(private val ints: IntArray) {
  val phrases: Int get() = ints[0]
  val columns: Int get() = ints[1]
  val rows: Int get() = ints[2]
  fun average(column: Int): Int = ints[3 + column]
  fun length(column: Int): Int = ints[3 + columns + column]
  private fun x(phrase: Int, column: Int, k: Int): Int = ints[3 + 2 * columns + 3 * (phrase * columns + column) + k]
  fun hitsHere(phrase: Int, column: Int): Int = x(phrase, column, 0)
  fun docsWithHits(phrase: Int, column: Int): Int = x(phrase, column, 2)

  companion object {
    fun of(blob: ByteArray): MatchInfo {
      val buffer = ByteBuffer.wrap(blob).order(ByteOrder.nativeOrder())
      return MatchInfo(IntArray(blob.size / 4) { buffer.getInt(it * 4) })
    }

    /** Per le prove: i numeri gia' fatti. */
    fun of(ints: IntArray): MatchInfo = MatchInfo(ints)
  }
}

/**
 * L'ordine per pertinenza: BM25F (Robertson e Zaragoza, «The Probabilistic Relevance Framework: BM25 and Beyond», 2009),
 * col peso di ogni campo dentro la frequenza; k1 = 1,2 e b = 0,75, i valori di serie di Lucene ed Elasticsearch (fiducia
 * alta come valori d'uso comune). Il punteggio di una parola della domanda e' quello della sua variante migliore per
 * peso (non la somma: «riunion» e «riun*» trovano le stesse parole).
 */
internal object SearchScoring {
  const val K1 = 1.2f
  const val B = 0.75f

  /** Il punteggio di una riga e quante parole della domanda vi stanno. */
  class Scored(val score: Float, val matchedGroups: Int)

  /**
   * @param averages la lunghezza media di ogni colonna fra i documenti che la hanno; null: quella di FTS4 (che conta
   *   anche le righe vuote).
   */
  fun score(plan: QueryPlan, info: MatchInfo, fieldWeights: FloatArray, averages: FloatArray? = null): Scored {
    val groupScores = FloatArray(plan.groups)
    val n = info.rows.toFloat()
    val columns = info.columns
    for (variant in plan.variants) {
      val p = variant.phrase
      if (p >= info.phrases) continue
      var df = 0
      var tf = 0f
      for (c in 0 until columns) {
        val here = info.hitsHere(p, c)
        df = maxOf(df, info.docsWithHits(p, c))
        if (here == 0) continue
        val avg = averages?.getOrNull(c) ?: maxOf(1, info.average(c)).toFloat()
        val norm = (1f - B) + B * (info.length(c) / avg)
        tf += fieldWeights.getOrElse(c) { 1f } * here / norm
      }
      if (tf <= 0f) continue
      val idf = ln(1.0 + (n - df + 0.5) / (df + 0.5)).toFloat().coerceAtLeast(0.01f)
      val s = variant.weight * idf * tf * (K1 + 1f) / (K1 + tf)
      if (s > groupScores[variant.group]) groupScores[variant.group] = s
    }
    var total = 0f
    var matched = 0
    for (s in groupScores) {
      if (s > 0f) {
        matched++
        total += s
      }
    }
    return Scored(total, matched)
  }
}

/** I pezzi di testo con le parole evidenziate. */
internal object SearchSnippets {
  /** Quanto e' lungo un pezzo di serie: due righe di una riga d'elenco a 393 pt (Mail di iOS ne mostra due). */
  const val DefaultChars: Int = 120

  /** Quanta parte del pezzo sta prima della prima parola trovata (un terzo, a occhio: si legge il contesto). */
  private const val LeadFraction = 0.3f

  private class Mark(val start: Int, val end: Int, val weight: Float)

  /** Le parole di [text] che corrispondono al piano, con la parte da evidenziare. */
  private fun marks(text: String, plan: QueryPlan): List<Mark> {
    if (plan.variants.isEmpty()) return emptyList()
    val out = ArrayList<Mark>()
    for (t in SearchAnalyzer.tokens(text)) {
      if (t.stop && plan.variants.none { it.match is TermMatch.Prefix }) continue
      var best = 0f
      var length = t.end - t.start
      for (v in plan.variants) {
        when (val m = v.match) {
          is TermMatch.Stem -> if (!t.stop && t.stem == m.stem && v.weight > best) {
            best = v.weight
            length = t.end - t.start
          }
          is TermMatch.Prefix -> if (t.form.startsWith(m.prefix) && v.weight > best) {
            best = v.weight
            // Si evidenzia la parte scritta («riun» in «riunione»), se la parola piegata e' lunga come l'originale.
            length = if (t.form.length == t.end - t.start && t.form != m.prefix) m.prefix.length else t.end - t.start
          }
        }
      }
      if (best > 0f) out += Mark(t.start, t.start + length, best)
    }
    return out
  }

  /** Il titolo intero, con le parole trovate. */
  fun whole(text: String, plan: QueryPlan): FluidSearchSnippet = build(text, 0, text.length, marks(text, plan))

  /** Il pezzo migliore di [text], lungo al piu' [maxChars], o null se il testo e' vuoto. */
  fun best(text: String, plan: QueryPlan, maxChars: Int = DefaultChars): FluidSearchSnippet? {
    if (text.isBlank()) return null
    val marks = marks(text, plan)
    if (text.length <= maxChars) return build(text, 0, text.length, marks)
    if (marks.isEmpty()) return cut(text, 0, maxChars, marks)
    // La finestra con piu' peso: si prova a partire poco prima di ogni parola trovata.
    var bestStart = 0
    var bestWeight = -1f
    val lead = (maxChars * LeadFraction).toInt()
    for (m in marks) {
      val start = wordStart(text, (m.start - lead).coerceAtLeast(0), m.start)
      val end = start + maxChars
      var w = 0f
      for (o in marks) if (o.start >= start && o.end <= end) w += o.weight
      if (w > bestWeight) {
        bestWeight = w
        bestStart = start
      }
    }
    return cut(text, bestStart, maxChars, marks)
  }

  /** Dove comincia la finestra: all'inizio di una frase se e' vicino, se no all'inizio di una parola. */
  private fun wordStart(text: String, from: Int, mark: Int): Int {
    // Una frase che comincia fra [from] e la parola trovata: si parte da li'.
    for (i in mark - 1 downTo maxOf(from, 1)) {
      if (text[i - 1] == '.' || text[i - 1] == '\n' || text[i - 1] == '!' || text[i - 1] == '?') {
        var s = i
        while (s < mark && text[s].isWhitespace()) s++
        return s
      }
    }
    if (from == 0) return 0
    var s = from
    while (s < mark && !text[s - 1].isWhitespace()) s++
    return s
  }

  private fun cut(text: String, start: Int, maxChars: Int, marks: List<Mark>): FluidSearchSnippet {
    var end = (start + maxChars).coerceAtMost(text.length)
    if (end < text.length) {
      var e = end
      while (e > start && !text[e - 1].isWhitespace() && !text[e].isWhitespace()) e--
      if (e > start + maxChars / 2) end = e
    }
    return build(text, start, end, marks)
  }

  /** Il pezzo [start]..[end] di [text]: spazi e a capo diventano uno spazio, i tagli diventano «…». */
  private fun build(text: String, start: Int, end: Int, marks: List<Mark>): FluidSearchSnippet {
    val sb = StringBuilder(end - start + 2)
    val map = IntArray(end - start + 1)
    var s = start
    while (s < end && text[s].isWhitespace()) s++
    var e = end
    while (e > s && text[e - 1].isWhitespace()) e--
    if (s > 0) sb.append('…')
    var lastSpace = false
    for (i in s until e) {
      val c = text[i]
      map[i - start] = sb.length
      if (c.isWhitespace()) {
        if (!lastSpace) sb.append(' ')
        lastSpace = true
      } else {
        sb.append(c)
        lastSpace = false
      }
    }
    map[e - start] = sb.length
    if (e < text.length && text.substring(e).isNotBlank()) sb.append('…')
    val ranges = ArrayList<IntRange>()
    for (m in marks) {
      if (m.start < s || m.end > e || m.end <= m.start) continue
      val a = map[m.start - start]
      val b = map[m.end - start] - 1
      if (b >= a) ranges += a..b
    }
    return FluidSearchSnippet(sb.toString(), ranges)
  }
}
