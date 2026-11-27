package dev.antigravity.fluidengine.foundation.text

/*
 * F8.21 · Il confronto di due testi a pezzi, con un tetto di lavoro.
 *
 * Serve a due moduli che non si conoscono: `engine-sync` lo usa per fondere un testo lungo cambiato su due dispositivi
 * (l'unione a tre vie), `engine-revisions` per mostrare i passaggi diversi nel foglio della scelta tra versioni. Sta qui,
 * in foundation, perche' e' una funzione pura e perche' Pampa Notes, che ha la sua sincronizzazione, deve poter usare il
 * foglio senza portarsi dietro `engine-sync`.
 */

/**
 * Il confronto di due liste di pezzi di testo con l'algoritmo di Myers (E. W. Myers, «An O(ND) Difference Algorithm and Its
 * Variations», Algorithmica 1986): trova la sottosequenza comune piu' lunga facendo al massimo `maxEdits` modifiche.
 *
 * **Il tetto di lavoro.** Il costo cresce col numero di modifiche D (tempo O((N+M)·D), memoria O(D²) per ripercorrere la
 * strada): due testi quasi uguali costano poco anche se lunghi, due testi diversissimi costano molto anche se corti. Oltre
 * [DefaultMaxEdits] modifiche [diff] si ferma e risponde `null`: chi chiama ripiega (la sincronizzazione chiede la scelta,
 * il foglio mostra due estratti). Prima del conto si tolgono l'inizio e la fine comuni, che nei casi veri sono quasi tutto.
 */
object FluidTextDiff {
  /**
   * Il tetto di serie: mille pezzi tolti o aggiunti. Nel caso peggiore il conto di Myers sono circa 4 MB di lavoro
   * temporaneo e qualche decina di millisecondi su un telefono di fascia bassa; per questo chi lo usa lo chiama fuori dal
   * thread principale. A occhio, 24/11/2026: due versioni di una nota con piu' di mille parole diverse non si fondono
   * comunque in modo utile.
   *
   * Il tetto limita il conto, non i pezzi (R2): [words] e [lines] fanno un pezzo per parola o per riga di tutto il testo,
   * e [diff] tiene per ogni pezzo un numero e la sua corrispondenza (una mappa dei pezzi diversi e quattro interi per
   * pezzo). Per due testi di qualche megabyte sono decine di megabyte, che chi chiama deve poter dare, o tagliare prima.
   */
  const val DefaultMaxEdits: Int = 1_000

  /**
   * I pezzi per parola: ogni parola (lettere e cifre), ogni corsa di spazi, ogni a capo e ogni segno di punteggiatura e'
   * un pezzo. Messi in fila ridanno il testo esatto.
   */
  fun words(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val out = ArrayList<String>(text.length / 4 + 1)
    var start = 0
    var kind = kindAt(text, 0)
    var i = Character.charCount(text.codePointAt(0))
    while (i < text.length) {
      val cp = text.codePointAt(i)
      val next = kindOf(cp)
      // Le parole e gli spazi fanno corsa; a capo e punteggiatura sono un pezzo per carattere.
      if (next != kind || kind == KindBreak || kind == KindMark) {
        out += text.substring(start, i)
        start = i
        kind = next
      }
      i += Character.charCount(cp)
    }
    out += text.substring(start)
    return out
  }

  /** I pezzi per riga: ogni riga con il suo a capo (l'ultima anche senza). Messi in fila ridanno il testo esatto. */
  fun lines(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val out = ArrayList<String>()
    var start = 0
    while (start < text.length) {
      val end = text.indexOf('\n', start)
      if (end < 0) {
        out += text.substring(start)
        break
      }
      out += text.substring(start, end + 1)
      start = end + 1
    }
    return out
  }

  /**
   * Allinea [a] e [b]: per ogni pezzo dice a quale pezzo dell'altra lista corrisponde, se corrisponde. Risponde `null`
   * se servono piu' di [maxEdits] pezzi tolti o aggiunti.
   */
  fun diff(a: List<String>, b: List<String>, maxEdits: Int = DefaultMaxEdits): FluidDiff? {
    require(maxEdits >= 0) { "il tetto non puo' essere negativo: $maxEdits" }
    val ids = HashMap<String, Int>(a.size + b.size)
    val ai = IntArray(a.size) { ids.getOrPut(a[it]) { ids.size } }
    val bi = IntArray(b.size) { ids.getOrPut(b[it]) { ids.size } }
    val n = ai.size
    val m = bi.size
    val aToB = IntArray(n) { -1 }
    val bToA = IntArray(m) { -1 }
    var prefix = 0
    while (prefix < n && prefix < m && ai[prefix] == bi[prefix]) {
      aToB[prefix] = prefix
      bToA[prefix] = prefix
      prefix++
    }
    var suffix = 0
    while (suffix < n - prefix && suffix < m - prefix && ai[n - 1 - suffix] == bi[m - 1 - suffix]) {
      aToB[n - 1 - suffix] = m - 1 - suffix
      bToA[m - 1 - suffix] = n - 1 - suffix
      suffix++
    }
    val ok = myers(ai, prefix, n - suffix, bi, prefix, m - suffix, maxEdits, aToB, bToA)
    return if (ok) FluidDiff(aToB, bToA) else null
  }

  /** Il cammino piu' corto nel mezzo, [aFrom]..[aTo) contro [bFrom]..[bTo). Falso se servono piu' di [maxEdits] passi. */
  private fun myers(
    a: IntArray, aFrom: Int, aTo: Int,
    b: IntArray, bFrom: Int, bTo: Int,
    maxEdits: Int,
    aToB: IntArray, bToA: IntArray,
  ): Boolean {
    val n = aTo - aFrom
    val m = bTo - bFrom
    if (n == 0 || m == 0) return n + m <= maxEdits
    val max = minOf(n + m, maxEdits)
    val offset = max + 1
    val v = IntArray(2 * max + 3)
    // Lo stato prima di ogni passo d, per k in -d..d (indice k + d): serve a ripercorrere la strada all'indietro.
    val trace = ArrayList<IntArray>()
    for (d in 0..max) {
      trace += IntArray(2 * d + 1) { v[it - d + offset] }
      var k = -d
      while (k <= d) {
        var x = if (k == -d || (k != d && v[k - 1 + offset] < v[k + 1 + offset])) v[k + 1 + offset] else v[k - 1 + offset] + 1
        var y = x - k
        while (x < n && y < m && a[aFrom + x] == b[bFrom + y]) {
          x++
          y++
        }
        v[k + offset] = x
        if (x >= n && y >= m) {
          backtrack(trace, d, n, m, aFrom, bFrom, aToB, bToA)
          return true
        }
        k += 2
      }
    }
    return false
  }

  private fun backtrack(
    trace: List<IntArray>, last: Int, n: Int, m: Int,
    aFrom: Int, bFrom: Int, aToB: IntArray, bToA: IntArray,
  ) {
    var x = n
    var y = m
    for (d in last downTo 0) {
      val snapshot = trace[d]
      fun at(k: Int): Int = snapshot[k + d]
      val k = x - y
      val prevK = if (k == -d || (k != d && at(k - 1) < at(k + 1))) k + 1 else k - 1
      val prevX = if (d == 0) 0 else at(prevK)
      val prevY = if (d == 0) 0 else prevX - prevK
      while (x > prevX && y > prevY) {
        aToB[aFrom + x - 1] = bFrom + y - 1
        bToA[bFrom + y - 1] = aFrom + x - 1
        x--
        y--
      }
      if (d > 0) {
        x = prevX
        y = prevY
      }
    }
  }

  private const val KindWord = 0
  private const val KindSpace = 1
  private const val KindBreak = 2
  private const val KindMark = 3

  private fun kindAt(text: String, index: Int): Int = kindOf(text.codePointAt(index))

  private fun kindOf(cp: Int): Int = when {
    cp == '\n'.code -> KindBreak
    Character.isLetterOrDigit(cp) || Character.getType(cp) == Character.NON_SPACING_MARK.toInt() -> KindWord
    Character.isWhitespace(cp) -> KindSpace
    else -> KindMark
  }
}

/**
 * Il risultato di [FluidTextDiff.diff]: per ogni pezzo di `a` il pezzo di `b` che gli corrisponde (o -1), e viceversa.
 * Le corrispondenze vanno sempre avanti: se il pezzo `i` corrisponde a `j`, ogni pezzo dopo `i` corrisponde a un pezzo
 * dopo `j`.
 */
class FluidDiff internal constructor(private val aToB: IntArray, private val bToA: IntArray) {
  /** Quanti pezzi ha `a`. */
  val sizeA: Int get() = aToB.size

  /** Quanti pezzi ha `b`. */
  val sizeB: Int get() = bToA.size

  /** Il pezzo di `b` che corrisponde al pezzo [indexA] di `a`, o -1 se in `b` non c'e'. */
  fun matchInB(indexA: Int): Int = aToB[indexA]

  /** Il pezzo di `a` che corrisponde al pezzo [indexB] di `b`, o -1 se in `a` non c'e'. */
  fun matchInA(indexB: Int): Int = bToA[indexB]

  /** Quanti pezzi sono stati tolti da `a` o aggiunti in `b`. */
  val edits: Int by lazy { aToB.count { it < 0 } + bToA.count { it < 0 } }

  /** I tratti diversi, in ordine: fra due tratti tutto e' uguale. */
  val hunks: List<FluidDiffHunk> by lazy {
    val out = ArrayList<FluidDiffHunk>()
    var i = 0
    var j = 0
    while (i < aToB.size || j < bToA.size) {
      if (i < aToB.size && j < bToA.size && aToB[i] == j) {
        i++
        j++
        continue
      }
      val si = i
      val sj = j
      while (i < aToB.size && aToB[i] < 0) i++
      while (j < bToA.size && bToA[j] < 0) j++
      out += FluidDiffHunk(si, i, sj, j)
    }
    out
  }
}

/**
 * Un tratto diverso: i pezzi [aStart]..[aEnd) di `a` (tolti) sono diventati [bStart]..[bEnd) di `b` (aggiunti). Uno dei due
 * intervalli puo' essere vuoto: un'aggiunta pura o una cancellazione pura.
 */
data class FluidDiffHunk(val aStart: Int, val aEnd: Int, val bStart: Int, val bEnd: Int)
