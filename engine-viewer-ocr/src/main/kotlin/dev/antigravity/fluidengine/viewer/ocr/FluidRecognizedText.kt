/*
 * engine-viewer-ocr (4.0, F8.17): il testo nelle immagini, come Testo attivo di iOS (Live Text, VisionKit
 * `ImageAnalysisInteraction`). Qui il modello del testo riconosciuto: blocchi, righe, parole, ognuna col suo
 * quadrilatero nell'immagine, in coordinate 0..1. Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.viewer.ocr

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Il quadrilatero di un pezzo di testo nell'immagine, normalizzato (0..1, origine in alto a sinistra), nell'ordine
 * della lettura: in alto a sinistra **del testo**, poi in senso orario. Una riga storta in una foto ha un quadrilatero
 * storto; il riconoscimento che dà solo rettangoli dà quadrilateri dritti.
 */
@Immutable
data class FluidTextQuad(
  val topLeft: Offset,
  val topRight: Offset,
  val bottomRight: Offset,
  val bottomLeft: Offset,
) {
  /** Il rettangolo che lo contiene. */
  val bounds: Rect get() = Rect(
    min(min(topLeft.x, topRight.x), min(bottomRight.x, bottomLeft.x)),
    min(min(topLeft.y, topRight.y), min(bottomRight.y, bottomLeft.y)),
    max(max(topLeft.x, topRight.x), max(bottomRight.x, bottomLeft.x)),
    max(max(topLeft.y, topRight.y), max(bottomRight.y, bottomLeft.y)),
  )

  /** Lo stesso quadrilatero girato di [quarterTurns] quarti in senso orario, come l'immagine che lo contiene. */
  fun rotated(quarterTurns: Int): FluidTextQuad {
    val turns = quarterTurns.mod(4)
    if (turns == 0) return this
    fun turn(p: Offset): Offset {
      var q = p
      repeat(turns) { q = Offset(1f - q.y, q.x) }
      return q
    }
    return FluidTextQuad(turn(topLeft), turn(topRight), turn(bottomRight), turn(bottomLeft))
  }

  /** Vero se il punto [p] (0..1) sta dentro, con [slop] di margine in coordinate 0..1. */
  fun contains(p: Offset, slop: Float = 0f): Boolean = distanceTo(p) <= slop

  /** Vero se è un rettangolo dritto (i lati sugli assi), come le righe di una schermata o di una pagina dritta. */
  val isUpright: Boolean get() =
    abs(topLeft.y - topRight.y) < Straight && abs(bottomLeft.y - bottomRight.y) < Straight &&
      abs(topLeft.x - bottomLeft.x) < Straight && abs(topRight.x - bottomRight.x) < Straight

  /**
   * La distanza del punto [p] dal quadrilatero (0 se ci sta dentro), in coordinate 0..1: col quadrilatero storto di una
   * riga in una foto storta conta la riga vera, non il rettangolo che la contiene (F8.17d).
   */
  fun distanceTo(p: Offset): Float {
    val corners = arrayOf(topLeft, topRight, bottomRight, bottomLeft)
    var inside = true
    var sign = 0f
    var nearest = Float.MAX_VALUE
    for (i in 0 until 4) {
      val a = corners[i]
      val b = corners[(i + 1) % 4]
      val cross = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
      if (cross != 0f) {
        if (sign == 0f) sign = cross else if (sign * cross < 0f) inside = false
      }
      nearest = min(nearest, segmentDistance(p, a, b))
    }
    // Un quadrilatero schiacciato (tutti i prodotti nulli) non ha un dentro: conta solo la distanza dai lati.
    return if (inside && sign != 0f) 0f else nearest
  }

  /**
   * Il pezzo di questa riga dall'inizio della parola [first] alla fine della parola [last] (due quadrilateri dentro la
   * riga), alto quanto la riga: è la selezione di una riga, dritta o storta come la riga.
   */
  fun between(first: FluidTextQuad, last: FluidTextQuad): FluidTextQuad {
    // La riga come segmento di mezzo, da sinistra a destra; i due capi sono le proiezioni dei lati delle parole.
    val a = (topLeft + bottomLeft) / 2f
    val b = (topRight + bottomRight) / 2f
    val d = b - a
    val length2 = d.x * d.x + d.y * d.y
    if (length2 <= 0f) return span(first, last)
    fun at(p: Offset): Float = (((p.x - a.x) * d.x + (p.y - a.y) * d.y) / length2).coerceIn(0f, 1f)
    val t0 = at((first.topLeft + first.bottomLeft) / 2f)
    val t1 = at((last.topRight + last.bottomRight) / 2f).coerceAtLeast(t0)
    fun top(t: Float) = topLeft + (topRight - topLeft) * t
    fun bottom(t: Float) = bottomLeft + (bottomRight - bottomLeft) * t
    return FluidTextQuad(top(t0), top(t1), bottom(t1), bottom(t0))
  }

  companion object {
    /** Un rettangolo dritto. */
    fun of(rect: Rect): FluidTextQuad = FluidTextQuad(rect.topLeft, rect.topRight, rect.bottomRight, rect.bottomLeft)

    /** L'unione di più quadrilateri della stessa riga: dal primo angolo sinistro all'ultimo destro. */
    fun span(first: FluidTextQuad, last: FluidTextQuad): FluidTextQuad =
      FluidTextQuad(first.topLeft, last.topRight, last.bottomRight, first.bottomLeft)

    /** Quanto un lato può scostarsi dall'asse per dirsi dritto: un decimillesimo dell'immagine. */
    private const val Straight = 1e-4f

    private fun segmentDistance(p: Offset, a: Offset, b: Offset): Float {
      val d = b - a
      val length2 = d.x * d.x + d.y * d.y
      val t = if (length2 > 0f) (((p.x - a.x) * d.x + (p.y - a.y) * d.y) / length2).coerceIn(0f, 1f) else 0f
      return (p - (a + d * t)).getDistance()
    }
  }
}

/** Una parola riconosciuta: il testo, dove sta e la fiducia (0..1). */
@Immutable
data class FluidRecognizedWord(val text: String, val quad: FluidTextQuad, val confidence: Float)

/** Una riga: le parole nell'ordine della lettura. */
@Immutable
data class FluidRecognizedLine(val words: List<FluidRecognizedWord>, val quad: FluidTextQuad) {
  /** Il testo della riga, con uno spazio fra le parole. */
  val text: String = words.joinToString(" ") { it.text }
  val confidence: Float get() = if (words.isEmpty()) 0f else words.sumOf { it.confidence.toDouble() }.toFloat() / words.size
}

/** Un blocco (un paragrafo, una colonna, un cartello): le righe dall'alto in basso. */
@Immutable
data class FluidRecognizedBlock(val lines: List<FluidRecognizedLine>, val quad: FluidTextQuad) {
  val text: String get() = lines.joinToString("\n") { it.text }
}

/**
 * Il testo trovato in un'immagine larga [width] × [height] pixel. Le parole hanno un numero d'ordine ([words]) che è
 * l'ordine della lettura: blocco per blocco, riga per riga. La selezione e la copia lavorano su quell'ordine, come in
 * Testo attivo.
 */
@Immutable
class FluidRecognizedText(
  val blocks: List<FluidRecognizedBlock>,
  val width: Int,
  val height: Int,
  /** La lingua del riconoscimento (per esempio «ita»). */
  val language: String = "ita",
) {
  /** Le righe nell'ordine della lettura. */
  val lines: List<FluidRecognizedLine> = blocks.flatMap { it.lines }

  /** Le parole nell'ordine della lettura. */
  val words: List<FluidRecognizedWord> = lines.flatMap { it.words }

  /** Per ogni parola, la riga a cui appartiene. */
  internal val lineOfWord: IntArray = IntArray(words.size).also { out ->
    var w = 0
    lines.forEachIndexed { l, line -> repeat(line.words.size) { out[w++] = l } }
  }

  /** Per ogni riga, l'indice della sua prima parola. */
  internal val firstWordOfLine: IntArray = IntArray(lines.size).also { out ->
    var w = 0
    lines.forEachIndexed { l, line -> out[l] = w; w += line.words.size }
  }

  /** Vero se non c'è niente da leggere. */
  val isEmpty: Boolean get() = words.isEmpty()

  /** Tutto il testo: righe separate da a capo, blocchi da una riga vuota. */
  val text: String get() = blocks.joinToString("\n\n") { it.text }

  /** Il testo delle parole da [from] a [to] (incluse, nell'ordine della lettura), con gli a capo fra le righe. */
  fun text(from: Int, to: Int): String {
    if (words.isEmpty()) return ""
    val a = from.coerceIn(0, words.lastIndex)
    val b = to.coerceIn(0, words.lastIndex)
    val (start, end) = if (a <= b) a to b else b to a
    val out = StringBuilder()
    for (i in start..end) {
      if (i > start) out.append(if (lineOfWord[i] != lineOfWord[i - 1]) '\n' else ' ')
      out.append(words[i].text)
    }
    return out.toString()
  }

  /**
   * Dove compare [query] nelle parole: ogni risultato è la serie di parole che la contiene (anche a cavallo di più
   * parole della stessa riga). Senza distinguere maiuscole e accenti, come la ricerca di Anteprima.
   */
  fun search(query: String): List<FluidTextMatch> {
    val needle = fold(query.trim())
    if (needle.isEmpty() || words.isEmpty()) return emptyList()
    val out = ArrayList<FluidTextMatch>()
    lines.forEachIndexed { l, line ->
      val first = firstWordOfLine[l]
      // La riga come testo piegato, con la posizione d'inizio di ogni parola.
      val starts = IntArray(line.words.size)
      val folded = StringBuilder()
      line.words.forEachIndexed { i, w ->
        if (i > 0) folded.append(' ')
        starts[i] = folded.length
        folded.append(fold(w.text))
      }
      var at = folded.indexOf(needle)
      while (at >= 0) {
        val end = at + needle.length - 1
        val a = starts.indexOfLast { it <= at }.coerceAtLeast(0)
        val b = starts.indexOfLast { it <= end }.coerceAtLeast(a)
        out += FluidTextMatch(first + a, first + b)
        at = folded.indexOf(needle, at + 1)
      }
    }
    return out
  }

  companion object {
    /** Niente testo. */
    val Empty: FluidRecognizedText = FluidRecognizedText(emptyList(), 0, 0)

    /** Il testo in minuscolo e senza accenti, per cercare «perche» e trovare «Perché». */
    internal fun fold(text: String): String =
      Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
  }
}

/** Un risultato della ricerca: le parole da [first] a [last] (indici di [FluidRecognizedText.words]). */
@Immutable
data class FluidTextMatch(val first: Int, val last: Int)
