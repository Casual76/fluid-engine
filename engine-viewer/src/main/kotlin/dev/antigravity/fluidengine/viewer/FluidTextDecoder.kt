package dev.antigravity.fluidengine.viewer

import androidx.compose.runtime.Immutable
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.nio.charset.IllegalCharsetNameException
import java.nio.charset.UnsupportedCharsetException

/**
 * Il charset con cui leggere un testo, e da dove viene.
 *
 * [bomLength] sono i byte del BOM all'inizio, da saltare (0 se non c'è). [certain] è vero se il charset l'ha detto il
 * file (il BOM) o chi lo manda (il `charset=` del MIME), falso se è stimato dai byte.
 */
@Immutable
data class FluidTextCharset(val charset: Charset, val bomLength: Int, val certain: Boolean)

/**
 * Il riconoscimento del charset e la lettura di un testo con un tetto, in Kotlin puro (si prova sulla JVM).
 *
 * L'ordine, come per un browser: **il BOM** (UTF-8, UTF-16 e UTF-32 nei due versi) vince su tutto; poi il charset
 * **dichiarato** nel MIME, se Java lo conosce; poi la **stima**: UTF-16 o UTF-32 senza BOM dagli zeri alternati (un
 * testo latino in UTF-16LE ha uno zero ogni due byte), UTF-8 se i byte lo sono senza errori (anche il solo ASCII),
 * altrimenti windows-1252, il charset dei file di Windows scritti in italiano. Universal Converter leggeva solo UTF-8
 * e mandava ogni UTF-16 nel dump esadecimale: qui non succede.
 */
object FluidTextDecoder {

  /** Quanti byte guarda la stima: abbastanza per un sorgente intero, pochi per non leggere un file due volte. */
  const val SampleBytes: Int = 64 * 1024

  private val Utf8: Charset = Charsets.UTF_8
  private val Utf16Le: Charset = Charsets.UTF_16LE
  private val Utf16Be: Charset = Charsets.UTF_16BE
  private val Utf32Le: Charset by lazy { Charset.forName("UTF-32LE") }
  private val Utf32Be: Charset by lazy { Charset.forName("UTF-32BE") }

  /** Il ripiego per i byte che non sono UTF valido: windows-1252, o Latin-1 dove la piattaforma non lo avesse. */
  val Fallback: Charset by lazy { supported("windows-1252") ?: Charsets.ISO_8859_1 }

  /**
   * Il charset dei primi [length] byte di [sample]. [declared] è il `charset=` del MIME (anche il MIME intero:
   * `text/plain; charset=ISO-8859-1`). [complete] dice se [sample] è il file intero: se non lo è, una sequenza
   * UTF-8 tagliata in fondo non conta come errore.
   */
  fun detect(sample: ByteArray, length: Int = sample.size, declared: String? = null, complete: Boolean = true): FluidTextCharset {
    bom(sample, length)?.let { return it }
    declaredCharset(declared)?.let { return FluidTextCharset(it, 0, certain = true) }
    val n = minOf(length, sample.size, SampleBytes)
    wideWithoutBom(sample, n)?.let { return FluidTextCharset(it, 0, certain = false) }
    // Una sequenza tagliata in fondo è un errore solo se quei byte sono davvero la fine del file.
    return FluidTextCharset(if (isUtf8(sample, n, !complete || n < length)) Utf8 else Fallback, 0, certain = false)
  }

  /**
   * Vero se i byte non sono un testo nel [charset] dato: un NUL dove il charset non lo prevede (in UTF-8 e nei
   * charset a un byte), o più di un carattere di controllo su dieci (esclusi tab, a capo, ritorno, salto pagina ed
   * escape). È la prova che separa un testo da un binario prima di mostrarlo.
   */
  fun looksBinary(sample: ByteArray, length: Int, charset: FluidTextCharset): Boolean {
    val n = minOf(length, sample.size, SampleBytes)
    if (n <= charset.bomLength) return false
    val wide = charset.charset == Utf16Le || charset.charset == Utf16Be || charset.charset.name().startsWith("UTF-32")
    if (!wide) {
      for (i in charset.bomLength until n) if (sample[i].toInt() == 0) return true
    }
    val text = decodeSample(sample, charset.bomLength, n, charset.charset)
    if (text.isEmpty()) return false
    val control = text.count { c ->
      c.code < 0x20 && c != '\t' && c != '\n' && c != '\r' && c != '\u000C' && c != '\u001B' || c.code == 0x7F
    }
    return control * 10 > text.length
  }

  private fun decodeSample(bytes: ByteArray, from: Int, to: Int, charset: Charset): String {
    val decoder = charset.newDecoder()
      .onMalformedInput(CodingErrorAction.REPLACE)
      .onUnmappableCharacter(CodingErrorAction.REPLACE)
    val out = CharBuffer.allocate((to - from) + 4)
    decoder.decode(ByteBuffer.wrap(bytes, from, to - from), out, false)
    out.flip()
    return out.toString()
  }

  private fun bom(b: ByteArray, length: Int): FluidTextCharset? {
    fun has(vararg bytes: Int) = length >= bytes.size && bytes.indices.all { b[it].toInt() and 0xFF == bytes[it] }
    return when {
      has(0xEF, 0xBB, 0xBF) -> FluidTextCharset(Utf8, 3, certain = true)
      has(0x00, 0x00, 0xFE, 0xFF) -> FluidTextCharset(Utf32Be, 4, certain = true)
      // FF FE 00 00 è UTF-32LE prima che UTF-16LE seguito da un NUL: un testo non comincia con un NUL.
      has(0xFF, 0xFE, 0x00, 0x00) -> FluidTextCharset(Utf32Le, 4, certain = true)
      has(0xFE, 0xFF) -> FluidTextCharset(Utf16Be, 2, certain = true)
      has(0xFF, 0xFE) -> FluidTextCharset(Utf16Le, 2, certain = true)
      else -> null
    }
  }

  private fun declaredCharset(declared: String?): Charset? {
    if (declared.isNullOrBlank()) return null
    val name = if ('=' in declared || ';' in declared) {
      declared.split(';').map { it.trim() }.firstOrNull { it.startsWith("charset=", ignoreCase = true) }
        ?.substringAfter('=')?.trim()?.trim('"', '\'') ?: return null
    } else {
      declared.trim()
    }
    return supported(name)
  }

  private fun supported(name: String): Charset? = try {
    if (Charset.isSupported(name)) Charset.forName(name) else null
  } catch (_: IllegalCharsetNameException) {
    null
  } catch (_: UnsupportedCharsetException) {
    null
  }

  /** UTF-16 o UTF-32 senza BOM, riconosciuti dagli zeri: servono almeno 16 byte, e un verso deve dominare. */
  private fun wideWithoutBom(b: ByteArray, n: Int): Charset? {
    if (n < 16) return null
    val zeros = IntArray(4)
    for (i in 0 until n) if (b[i].toInt() == 0) zeros[i % 4]++
    val quarter = n / 4
    // UTF-32: tre zeri su quattro in posizioni fisse (un testo latino o anche con accenti).
    if (zeros[1] > quarter * 9 / 10 && zeros[2] > quarter * 9 / 10 && zeros[3] > quarter * 9 / 10 && zeros[0] < quarter / 10) return Utf32Le
    if (zeros[0] > quarter * 9 / 10 && zeros[1] > quarter * 9 / 10 && zeros[2] > quarter * 9 / 10 && zeros[3] < quarter / 10) return Utf32Be
    val even = zeros[0] + zeros[2]
    val odd = zeros[1] + zeros[3]
    val half = n / 2
    return when {
      odd * 10 > half * 3 && even * 10 < half -> Utf16Le
      even * 10 > half * 3 && odd * 10 < half -> Utf16Be
      else -> null
    }
  }

  private fun isUtf8(b: ByteArray, n: Int, truncatedAllowed: Boolean): Boolean {
    var i = 0
    while (i < n) {
      val c = b[i].toInt() and 0xFF
      val extra = when {
        c < 0x80 -> 0
        c in 0xC2..0xDF -> 1
        c in 0xE0..0xEF -> 2
        c in 0xF0..0xF4 -> 3
        else -> return false
      }
      if (i + extra >= n) {
        return truncatedAllowed && (i + 1 until n).all { b[it].toInt() and 0xC0 == 0x80 }
      }
      for (k in 1..extra) if (b[i + k].toInt() and 0xC0 != 0x80) return false
      if (extra == 2) {
        val second = b[i + 1].toInt() and 0xFF
        if (c == 0xE0 && second < 0xA0 || c == 0xED && second > 0x9F) return false
      }
      if (extra == 3) {
        val second = b[i + 1].toInt() and 0xFF
        if (c == 0xF0 && second < 0x90 || c == 0xF4 && second > 0x8F) return false
      }
      i += extra + 1
    }
    return true
  }

  /**
   * Legge un testo con un tetto: al più [limitBytes] byte di [input], poi il charset, i caratteri e le righe.
   * Restituisce `null` se i byte non sono testo ([looksBinary]). Non chiude [input].
   *
   * - **Il tetto è esatto**: un file di esattamente [limitBytes] byte non è troncato (Universal Converter segnava il
   *   taglio anche lì); uno più lungo sì, e il taglio cade su un carattere intero, mai a metà di una sequenza.
   * - **Gli errori di codifica non fermano la lettura**: un byte che il charset non sa leggere diventa U+FFFD e
   *   [FluidTextDocument.malformed] lo dice.
   * - **Le righe**: a capo `\n`, `\r\n` o `\r` da solo; un a capo in fondo non apre una riga vuota; un file vuoto ha
   *   zero righe. Una riga oltre [maxLineChars] caratteri si spezza in righe da [maxLineChars] (mai a metà di una
   *   coppia surrogata), così un JSON minimizzato su una riga sola non ferma il visore; [FluidTextDocument.splitLines]
   *   conta i pezzi aggiunti.
   *
   * [totalBytes] è la dimensione del file se la si conosce (per dire «letti 4 MB di 120»). [language] di serie viene
   * dal nome e dal MIME, e se tacciono dal contenuto ([FluidSyntax.guessLanguage]).
   */
  fun read(
    input: InputStream,
    fileName: String = "",
    mimeType: String? = null,
    totalBytes: Long? = null,
    limitBytes: Int = FluidTextDocument.DefaultLimitBytes,
    maxLineChars: Int = FluidTextDocument.DefaultMaxLineChars,
    language: String? = FluidSyntax.languageFor(fileName, mimeType),
  ): FluidTextDocument? {
    require(limitBytes > 0) { "limitBytes deve essere positivo" }
    require(maxLineChars > 0) { "maxLineChars deve essere positivo" }
    // Il buffer parte dalla dimensione nota (più un byte, per accorgersi che il file è cresciuto) e raddoppia fino al
    // tetto: un file piccolo non alloca 4 MiB, uno grande non ne alloca di più.
    val known = totalBytes?.takeIf { it >= 0 }?.plus(1)?.coerceAtMost(limitBytes.toLong())?.toInt()
    var data = ByteArray((known ?: minOf(limitBytes, 64 * 1024)).coerceAtLeast(1))
    var filled = 0
    while (filled < limitBytes) {
      if (filled == data.size) data = data.copyOf(minOf(limitBytes, maxOf(data.size * 2, 8192)))
      val read = input.read(data, filled, data.size - filled)
      if (read < 0) break
      filled += read
    }
    val truncated = filled >= limitBytes && input.read() >= 0
    return decode(data, filled, truncated, fileName, mimeType, totalBytes, limitBytes, maxLineChars, language)
  }

  /** Come [read], su byte già letti; [truncated] dice se il file continua oltre [length]. */
  fun decode(
    bytes: ByteArray,
    length: Int = bytes.size,
    truncated: Boolean = false,
    fileName: String = "",
    mimeType: String? = null,
    totalBytes: Long? = null,
    limitBytes: Int = FluidTextDocument.DefaultLimitBytes,
    maxLineChars: Int = FluidTextDocument.DefaultMaxLineChars,
    language: String? = FluidSyntax.languageFor(fileName, mimeType),
  ): FluidTextDocument? {
    val charset = detect(bytes, length, declared = mimeType, complete = !truncated)
    if (looksBinary(bytes, length, charset)) return null
    val end = if (truncated) wholeCharEnd(bytes, charset.bomLength, length, charset.charset) else length
    val decoder: CharsetDecoder = charset.charset.newDecoder()
      .onMalformedInput(CodingErrorAction.REPLACE)
      .onUnmappableCharacter(CodingErrorAction.REPLACE)
    val replacement = decoder.replacement()
    val text = decoder.decode(ByteBuffer.wrap(bytes, charset.bomLength, (end - charset.bomLength).coerceAtLeast(0))).toString()
    // Un U+FFFD scritto nel file non è un errore: si contano solo quelli che il decodificatore ha messo.
    val malformed = replacement.isNotEmpty() && text.contains(replacement) &&
      strictFails(bytes, charset.bomLength, end, charset.charset)
    val (lines, split) = splitLines(text.removePrefix("﻿"), maxLineChars)
    return FluidTextDocument(
      lines = lines,
      language = language ?: FluidSyntax.guessLanguage(text.take(4096)),
      charset = charset,
      truncated = truncated,
      bytesRead = end.toLong(),
      totalBytes = totalBytes,
      malformed = malformed,
      splitLines = split,
      limitBytes = limitBytes,
    )
  }

  private fun strictFails(bytes: ByteArray, from: Int, to: Int, charset: Charset): Boolean = try {
    charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
      .decode(ByteBuffer.wrap(bytes, from, (to - from).coerceAtLeast(0)))
    false
  } catch (_: java.nio.charset.CharacterCodingException) {
    true
  }

  /** La fine dell'ultimo carattere intero prima di [length], per un testo tagliato dal tetto. */
  internal fun wholeCharEnd(bytes: ByteArray, from: Int, length: Int, charset: Charset): Int {
    val span = length - from
    if (span <= 0) return length
    return when {
      charset == Utf8 -> {
        var start = length - 1
        var back = 0
        while (start > from && back < 3 && bytes[start].toInt() and 0xC0 == 0x80) { start--; back++ }
        val lead = bytes[start].toInt() and 0xFF
        val need = when {
          lead < 0x80 -> 1
          lead in 0xC0..0xDF -> 2
          lead in 0xE0..0xEF -> 3
          lead in 0xF0..0xF7 -> 4
          else -> 1
        }
        if (length - start >= need) length else start
      }
      charset == Utf16Le || charset == Utf16Be -> {
        var end = from + span - span % 2
        if (end - 2 >= from) {
          val unit = if (charset == Utf16Le) {
            (bytes[end - 2].toInt() and 0xFF) or ((bytes[end - 1].toInt() and 0xFF) shl 8)
          } else {
            ((bytes[end - 2].toInt() and 0xFF) shl 8) or (bytes[end - 1].toInt() and 0xFF)
          }
          if (unit in 0xD800..0xDBFF) end -= 2
        }
        end
      }
      charset.name().startsWith("UTF-32") -> from + span - span % 4
      else -> length
    }
  }

  internal fun splitLines(text: String, maxLineChars: Int): Pair<List<String>, Int> {
    val lines = ArrayList<String>()
    var split = 0
    fun addLine(line: String) {
      if (line.length <= maxLineChars) { lines += line; return }
      var start = 0
      while (start < line.length) {
        var end = minOf(start + maxLineChars, line.length)
        if (end < line.length && Character.isHighSurrogate(line[end - 1])) end--
        lines += line.substring(start, end)
        start = end
        if (start < line.length) split++
      }
    }
    var start = 0
    var i = 0
    while (i < text.length) {
      val c = text[i]
      if (c == '\n' || c == '\r') {
        addLine(text.substring(start, i))
        if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
        start = i + 1
      }
      i++
    }
    if (start < text.length) addLine(text.substring(start))
    return lines to split
  }
}

/**
 * Un file di testo letto, pronto per il preset testo: tutte le righe in memoria (il tetto le tiene poche), la lingua
 * per i colori, il charset e cosa è successo leggendo. Si crea con [FluidTextDecoder.read] o
 * [fluidReadTextFile]; si passa al visore come `FluidViewerContent.Text(document)`.
 */
class FluidTextDocument internal constructor(
  /** Le righe, senza i caratteri di a capo. */
  val lines: List<String>,
  override val language: String?,
  val charset: FluidTextCharset,
  /** Vero se il file continua oltre il tetto: il preset lo dice in fondo. */
  val truncated: Boolean,
  /** I byte letti, BOM compreso. */
  val bytesRead: Long,
  /** La dimensione del file, se la si conosce. */
  val totalBytes: Long?,
  /** Vero se qualche byte non era valido nel charset ed è diventato U+FFFD. */
  val malformed: Boolean,
  /** Quante righe in più sono nate spezzando le righe troppo lunghe. */
  val splitLines: Int,
  /** Il tetto con cui è stato letto. */
  val limitBytes: Int,
) : dev.antigravity.fluidengine.ui.fluid.viewer.FluidStyledTextSource {

  override val lineCount: Int get() = lines.size

  override suspend fun readLines(start: Int, count: Int): List<String> {
    if (start >= lines.size || count <= 0) return emptyList()
    val from = start.coerceAtLeast(0)
    return lines.subList(from, minOf(lines.size, from + count)).toList()
  }

  /**
   * Le righe da [start] per [count] con i ruoli di sintassi ([fluidSyntaxAnnotated]): la forma che il preset testo
   * di engine-ui legge da `FluidStyledTextSource` (F6C.2) e colora col tema. Senza lingua, le righe senza annotazioni.
   */
  override suspend fun readStyledLines(start: Int, count: Int): List<androidx.compose.ui.text.AnnotatedString> {
    val plain = readLines(start, count)
    val spans = syntaxSpans(start, plain.size)
    return plain.mapIndexed { i, line -> fluidSyntaxAnnotated(line, spans.getOrElse(i) { emptyList() }) }
  }

  private val highlighter: FluidSyntaxHighlighter? by lazy { FluidSyntax.highlighter(language) }
  // Lo stato del colorante all'inizio di ogni riga già colorata: si colora una volta sola, in avanti.
  private var states = IntArray(0)
  private var statesKnown = 0
  private val lock = Any()

  /**
   * I pezzi colorati delle righe da [start] per [count], o liste vuote se la lingua è assente o sconosciuta.
   * Colora in avanti dalla prima riga non ancora vista e ricorda gli stati, così un pezzo a metà file costa solo le
   * righe nuove.
   */
  fun syntaxSpans(start: Int, count: Int): List<List<FluidSyntaxSpan>> {
    val from = start.coerceAtLeast(0)
    val to = minOf(lines.size, from + count.coerceAtLeast(0))
    if (from >= to) return emptyList()
    val highlighter = highlighter ?: return List(to - from) { emptyList() }
    synchronized(lock) {
      if (states.size < lines.size + 1) states = states.copyOf(lines.size + 1)
      while (statesKnown < from) {
        states[statesKnown + 1] = highlighter.line(lines[statesKnown], states[statesKnown]).endState
        statesKnown++
      }
      return (from until to).map { index ->
        val line = highlighter.line(lines[index], states[index])
        if (index + 1 > statesKnown) {
          states[index + 1] = line.endState
          statesKnown = index + 1
        }
        line.spans
      }
    }
  }

  companion object {
    /**
     * Il tetto di serie: 4 MiB. Un sorgente o un registro normale ci sta intero; uno di centinaia di MB si apre
     * subito e dice di essere troncato. In memoria sono al più circa 8 MB di caratteri. A occhio, 22 novembre 2026
     * (Universal Converter usava 256 KiB, che tagliava già un registro di un giorno).
     */
    const val DefaultLimitBytes: Int = 4 * 1024 * 1024

    /** La riga più lunga che il preset riceve intera: oltre si spezza. A occhio, 22 novembre 2026. */
    const val DefaultMaxLineChars: Int = 10_000
  }
}
