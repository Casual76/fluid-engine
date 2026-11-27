package dev.antigravity.fluidengine.richtext

// L'oracolo di FluidRichTextR2Test.theFastReaderReadsLikeTheOldOne: il lettore in linea del Markdown com'era
// prima della R2 (difetto 54), copiato com'era. L'unica differenza voluta e' il codice in linea, che toglie al
// piu' uno spazio per lato come CommonMark (un minore della stessa review).

/** Una riga di Markdown (senza contenitori ne' titoli) letta col lettore di prima. */
internal fun referenceMarkdownLine(line: String): FluidRichTextDocument {
  val assembler = DocumentAssembler()
  assembler.begin(FluidRichTextParagraphStyle.Plain)
  ReferenceMarkdownInlineReader(line, assembler, false).read()
  assembler.end()
  return assembler.build()
}

private fun referenceCodeContent(content: String): String =
  if (content.length >= 2 && content.first() == ' ' && content.last() == ' ' && content.any { it != ' ' }) content.substring(1, content.length - 1) else content

/** Il lettore in linea di prima della R2 (quadratico sulle righe ostili), con la sola correzione degli spazi del codice. */
private class ReferenceMarkdownInlineReader(
  private val line: String,
  private val assembler: DocumentAssembler,
  heading: Boolean,
) {
  private var bold = heading
  private var italic = false
  private var underline = false
  private var link: String? = null
  private val pending = StringBuilder()

  fun read() = parse(0, line.length)

  private fun parse(from: Int, to: Int) {
    var index = from
    while (index < to) {
      val char = line[index]
      when {
        char == '\\' && index + 1 < to && line[index + 1].isAsciiPunctuation() -> {
          pending.append(line[index + 1])
          index += 2
        }
        char == '`' -> {
          val ticks = line.drop(index).takeWhile { it == '`' }.length
          val close = line.indexOf("`".repeat(ticks), index + ticks)
          if (close in 0 until to) {
            pending.append(referenceCodeContent(line.substring(index + ticks, close)))
            index = close + ticks
          } else {
            pending.append(line, index, index + ticks)
            index += ticks
          }
        }
        char == '*' || char == '_' -> index = delimiterRun(index, to, char)
        line.startsWith("<u>", index, ignoreCase = true) -> {
          setFormat { underline = true }
          index += 3
        }
        line.startsWith("</u>", index, ignoreCase = true) -> {
          setFormat { underline = false }
          index += 4
        }
        char == '<' -> {
          val close = line.indexOf('>', index + 1)
          val candidate = if (close in 0 until to) line.substring(index + 1, close) else null
          val url = candidate?.takeIf { !it.contains(' ') }?.let { FluidRichTextLinks.sanitize(it) }
          if (url != null && link == null) {
            setFormat { link = url }
            pending.append(FluidRichTextLinks.displayText(url))
            setFormat { link = null }
            index = close + 1
          } else {
            pending.append(char)
            index++
          }
        }
        char == '[' && link == null -> {
          val closeBracket = matchingBracket(index, to)
          if (closeBracket > 0 && closeBracket + 1 < to && line[closeBracket + 1] == '(') {
            val closeParen = closingParen(closeBracket + 2, to)
            if (closeParen in 0 until to) {
              val target = line.substring(closeBracket + 2, closeParen).trim().substringBefore(' ').removePrefix("<").removeSuffix(">")
              val url = FluidRichTextLinks.sanitize(target.replace("\\(", "(").replace("\\)", ")"))
              setFormat { link = url }
              parse(index + 1, closeBracket)
              setFormat { link = null }
              index = closeParen + 1
              continue
            }
          }
          pending.append(char)
          index++
        }
        else -> {
          pending.append(char)
          index++
        }
      }
    }
    flush()
  }

  private inline fun setFormat(change: () -> Unit) {
    flush()
    change()
  }

  private fun flush() {
    if (pending.isEmpty()) return
    assembler.append(pending.toString(), FluidRichTextInlineFormat(bold, italic, underline, link))
    pending.setLength(0)
  }

  /**
   * Un gruppo di `*` (o di `_`) consecutivi, letto come fa CommonMark: prima chiude quel che e' aperto
   * (il corsivo con un segno, il grassetto con due), poi apre con quel che resta (due per il grassetto,
   * uno per il corsivo), e solo se piu' avanti c'e' chi chiude; i segni avanzati sono testo. Cosi'
   * `***a***`, `*a****b***` e `**a***b*` si leggono come li ha scritti [FluidRichTextMarkdown.write].
   * Il `_` dentro una parola (`snake_case`) non e' mai un segno.
   */
  private fun delimiterRun(start: Int, to: Int, char: Char): Int {
    var end = start
    while (end < to && line[end] == char) end++
    var remaining = end - start
    val previous = line.getOrNull(start - 1)
    val next = line.getOrNull(end)?.takeIf { end < to }
    val canClose = previous != null && !previous.isWhitespace() && (char == '*' || next == null || !next.isLetterOrDigit())
    val canOpen = next != null && !next.isWhitespace() && (char == '*' || previous == null || !previous.isLetterOrDigit())
    if (canClose) {
      if (italic && remaining >= 1) {
        setFormat { italic = false }
        remaining--
      }
      if (bold && remaining >= 2) {
        setFormat { bold = false }
        remaining -= 2
      }
    }
    if (canOpen) {
      if (!bold && remaining >= 2 && hasCloser(end, to, char, 2)) {
        setFormat { bold = true }
        remaining -= 2
      }
      if (!italic && remaining >= 1 && hasCloser(end, to, char, 1)) {
        setFormat { italic = true }
        remaining--
      }
    }
    repeat(remaining) { pending.append(char) }
    return end
  }

  /** C'e' piu' avanti un gruppo di almeno [count] segni [char] che puo' chiudere (non dopo uno spazio, non protetto)? */
  private fun hasCloser(from: Int, to: Int, char: Char, count: Int): Boolean {
    var index = from
    while (index < to) {
      if (line[index] == '\\') {
        index += 2
        continue
      }
      if (line[index] == char) {
        var end = index
        while (end < to && line[end] == char) end++
        if (end - index >= count && index > from && !line[index - 1].isWhitespace()) return true
        index = end
        continue
      }
      index++
    }
    return false
  }

  /** La `)` che chiude l'indirizzo di un link: non protetta da `\`, e con le parentesi interne in pari. */
  private fun closingParen(from: Int, to: Int): Int {
    var depth = 0
    var index = from
    while (index < to) {
      when (line[index]) {
        '\\' -> index++
        '(' -> depth++
        ')' -> if (depth == 0) return index else depth--
      }
      index++
    }
    return -1
  }

  private fun matchingBracket(open: Int, to: Int): Int {
    var depth = 0
    var index = open
    while (index < to) {
      when (line[index]) {
        '\\' -> index++
        '[' -> depth++
        ']' -> {
          depth--
          if (depth == 0) return index
        }
      }
      index++
    }
    return -1
  }

  private fun Char.isAsciiPunctuation(): Boolean = this in '!'..'/' || this in ':'..'@' || this in '['..'`' || this in '{'..'~'
}
