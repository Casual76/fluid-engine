package dev.antigravity.fluidengine.search

import java.text.Normalizer
import java.util.Locale

/**
 * Una parola del testo: la forma piegata ([form]: minuscole, senza accenti), la radice ([stem]) e dove sta nel testo
 * originale ([start] incluso, [end] escluso). [stop] e' una parola vuota (articoli, preposizioni) che non entra
 * nell'indice.
 */
internal class SearchToken(
  val form: String,
  val stem: String,
  val display: String,
  val start: Int,
  val end: Int,
  val stop: Boolean,
)

/**
 * L'italiano dell'indice: come una parola diventa un termine.
 *
 * - **Accenti**: «perché», «perche'» e «perche» sono la stessa parola (NFD e via i segni, come fa il normalizzatore di
 *   Core Spotlight con `CSSearchQuery` senza `c`, insensibile ai diacritici). Anche «ß» → «ss», «æ» → «ae».
 * - **Apostrofi**: «l'indirizzo» sono due parole, «l» e «indirizzo» (ICU tiene insieme le lettere ai due lati
 *   dell'apostrofo, UAX #29 WB6/WB7: per questo l'analisi e' nostra e non quella di AppSearch). Vale per «'», «’», «ʼ»,
 *   «`» e «´». La parte elisa («l», «dell», «un», «all») e' una parola vuota.
 * - **Plurali e generi**: la radice leggera di Savoy per l'italiano (J. Savoy, «Light stemming approaches for the French,
 *   Portuguese, German and Hungarian languages», 2006, e la sua regola per l'italiano usata in Lucene
 *   `ItalianLightStemmer`; fiducia alta sulla regola). Toglie la vocale finale, e «-ie», «-he», «-hi», «-ii», «-ia», «-io»:
 *   «riunione» e «riunioni» → «riunion», «amiche» e «amici» → «amic», «verifica» e «verifiche» → «verific». Lucene la
 *   applica da 6 lettere; qui da 4 (a occhio, 25/11/2026: con 6 «casa»/«case», «voto»/«voti» e «foto» non si univano, ed e'
 *   quello che si cerca di piu' in ClasseViva e Gallery; il prezzo e' «casa» = «caso», che l'ordine per pertinenza assorbe).
 *   Le parole con cifre non si toccano.
 * - **Parole vuote**: articoli, preposizioni semplici e articolate, «e», «o», «ma», «che»: non entrano nell'indice (pesano
 *   un terzo del testo italiano) e si tolgono dalla domanda, tranne l'ultima parola che si sta scrivendo («la» puo'
 *   diventare «lavoro»).
 */
internal object SearchAnalyzer {

  /** Le parole vuote, gia' piegate. Poche e sicure: articoli, preposizioni, congiunzioni brevi, e tre inglesi. */
  val stopwords: Set<String> = setOf(
    "il", "lo", "la", "i", "gli", "le", "l", "un", "uno", "una",
    "di", "a", "da", "in", "con", "su", "per", "tra", "fra",
    "e", "ed", "o", "od", "ma", "che",
    "del", "dello", "della", "dei", "degli", "delle", "dell",
    "al", "allo", "alla", "ai", "agli", "alle", "all",
    "dal", "dallo", "dalla", "dai", "dagli", "dalle", "dall",
    "nel", "nello", "nella", "nei", "negli", "nelle", "nell",
    "sul", "sullo", "sulla", "sui", "sugli", "sulle", "sull",
    "col", "coi", "d", "c", "s",
    "the", "of", "and",
  )

  /** Vero se [c] fa parte di una parola: lettere, cifre e i segni che combinano (un accento scritto a parte). */
  private fun isWordChar(c: Char): Boolean =
    Character.isLetterOrDigit(c) || Character.getType(c) == Character.NON_SPACING_MARK.toInt() ||
      Character.getType(c) == Character.COMBINING_SPACING_MARK.toInt()

  /** Le parole di [text], con le posizioni nel testo originale. */
  fun tokens(text: String): List<SearchToken> {
    val out = ArrayList<SearchToken>(text.length / 6 + 1)
    var i = 0
    val n = text.length
    while (i < n) {
      while (i < n && !isWordChar(text[i])) i++
      if (i >= n) break
      val start = i
      while (i < n && isWordChar(text[i])) i++
      val raw = text.substring(start, i)
      val form = fold(raw)
      if (form.isEmpty()) continue
      out += SearchToken(
        form = form,
        stem = stem(form),
        display = raw.lowercase(Locale.ITALIAN),
        start = start,
        end = i,
        stop = form in stopwords,
      )
    }
    return out
  }

  /** Una parola piegata: minuscole, senza accenti, con le poche lettere che si scrivono in due. */
  fun fold(word: String): String {
    var ascii = true
    for (c in word) if (c.code >= 0x80) { ascii = false; break }
    if (ascii) return word.lowercase(Locale.ROOT)
    val decomposed = Normalizer.normalize(word, Normalizer.Form.NFD)
    val sb = StringBuilder(decomposed.length)
    for (c in decomposed) {
      val type = Character.getType(c)
      if (type == Character.NON_SPACING_MARK.toInt() || type == Character.COMBINING_SPACING_MARK.toInt() ||
        type == Character.ENCLOSING_MARK.toInt()
      ) continue
      when (c) {
        'ß' -> sb.append("ss")
        'æ', 'Æ' -> sb.append("ae")
        'œ', 'Œ' -> sb.append("oe")
        'ø', 'Ø' -> sb.append('o')
        'đ', 'Đ' -> sb.append('d')
        'ł', 'Ł' -> sb.append('l')
        'ı' -> sb.append('i')
        else -> sb.append(c)
      }
    }
    return sb.toString().lowercase(Locale.ROOT)
  }

  /** Il testo intero piegato e ricomposto con uno spazio fra le parole (per confrontare frasi). */
  fun foldText(text: String): String = tokens(text).joinToString(" ") { it.form }

  /** La radice leggera (sopra): vocale finale, «-ie», «-he», «-hi», «-ii», «-ia», «-io». */
  fun stem(form: String): String {
    val n = form.length
    if (n < 4) return form
    for (c in form) if (!c.isLetter()) return form
    val last = form[n - 1]
    val prev = form[n - 2]
    return when (last) {
      'e' -> if (n >= 5 && (prev == 'i' || prev == 'h')) form.substring(0, n - 2) else form.substring(0, n - 1)
      'i' -> if (n >= 5 && (prev == 'h' || prev == 'i')) form.substring(0, n - 2) else form.substring(0, n - 1)
      'a', 'o' -> if (n >= 5 && prev == 'i') form.substring(0, n - 2) else form.substring(0, n - 1)
      else -> form
    }
  }

  /**
   * La distanza di Damerau-Levenshtein (con lo scambio di due lettere vicine, «riunoine») fra [a] e [b], o un numero
   * maggiore di [max] appena si sa che la supera.
   */
  fun distance(a: String, b: String, max: Int): Int {
    val la = a.length
    val lb = b.length
    if (kotlin.math.abs(la - lb) > max) return max + 1
    if (a == b) return 0
    var prevPrev = IntArray(lb + 1)
    var prev = IntArray(lb + 1) { it }
    var cur = IntArray(lb + 1)
    for (i in 1..la) {
      cur[0] = i
      var rowMin = cur[0]
      val ca = a[i - 1]
      for (j in 1..lb) {
        val cb = b[j - 1]
        val cost = if (ca == cb) 0 else 1
        var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
        if (i > 1 && j > 1 && ca == b[j - 2] && a[i - 2] == cb) v = minOf(v, prevPrev[j - 2] + 1)
        cur[j] = v
        if (v < rowMin) rowMin = v
      }
      if (rowMin > max) return max + 1
      val t = prevPrev
      prevPrev = prev
      prev = cur
      cur = t
    }
    return prev[lb]
  }

  /** Quanti errori si tollerano in una parola di [length] lettere: nessuno sotto 4, uno fino a 6, due da 7. */
  fun typoBudget(length: Int): Int = when {
    length < 4 -> 0
    length < 7 -> 1
    else -> 2
  }
}
