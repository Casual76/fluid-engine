package dev.antigravity.fluidengine.richtext

/**
 * Il documento da e verso **Markdown**, per le app che salvano testo (le note) e per chi vuole un
 * formato leggibile anche senza editor.
 *
 * **Una riga = un paragrafo.** E' il dialetto delle app di note e dei messaggi (l'a capo conta), non
 * quello di CommonMark in cui due righe consecutive si fondono: cosi' un documento scritto e riletto
 * torna identico, righe vuote comprese. Un lettore CommonMark che riceve questo testo mostra comunque
 * tutto, al piu' unendo due righe normali vicine.
 *
 * In uscita: `**grassetto**`, `*corsivo*`, `<u>sottolineato</u>` (il Markdown non ha un segno suo, e
 * l'HTML in linea e' quello che i lettori capiscono), `[testo](indirizzo)`, `- ` e `1. ` per gli elenchi,
 * `> ` per la citazione. I caratteri che il Markdown leggerebbe come segni (`*`, `_`, `[`, `]`, `\`, `` ` ``,
 * `<`, e in testa alla riga `#`, `>`, `-`, `+`, `1.`) escono preceduti da `\`.
 *
 * In ingresso, oltre a quanto sopra: `__grassetto__`, `_corsivo_`, `* `/`+ ` per l'elenco puntato, `1)`,
 * citazioni annidate (appiattite), i titoli `#` (diventano un paragrafo in grassetto), i link automatici
 * `<https://…>`, il codice (resta testo, senza segni), i blocchi di codice recintati (testo, riga per
 * riga). Gli indirizzi dei link passano da [FluidRichTextLinks.sanitize]. Il Markdown non e' il formato
 * di riferimento dell'editor: se l'app puo' scegliere, per salvare usi [FluidRichTextHtml].
 */
object FluidRichTextMarkdown {
  /** Il Markdown di [document]. */
  fun write(document: FluidRichTextDocument): String = buildString {
    for (index in 0 until document.paragraphCount) {
      if (index > 0) append('\n')
      val style = document.paragraphStyles[index]
      if (style.quote) append("> ")
      when (style.list) {
        FluidRichTextListKind.Bullet -> append("- ")
        FluidRichTextListKind.Numbered -> append(document.listNumber(index)).append(". ")
        null -> Unit
      }
      MarkdownInlineWriter(document, index, this, atLineStart = style.list == null).write()
    }
  }

  /** Il documento letto da [markdown]. Mai un'eccezione. */
  fun read(markdown: String): FluidRichTextDocument {
    val text = normalizeEditorText(
      if (markdown.length > FluidRichTextHtml.MaxInputLength) markdown.substring(0, FluidRichTextHtml.MaxInputLength) else markdown,
    )
    val assembler = DocumentAssembler()
    var fence: String? = null
    for (rawLine in text.split('\n')) {
      if (fence != null) {
        if (rawLine.trimStart().startsWith(fence)) {
          fence = null
        } else {
          assembler.begin(FluidRichTextParagraphStyle.Plain)
          assembler.append(rawLine, FluidRichTextInlineFormat.Plain)
          assembler.end()
        }
        continue
      }
      var line = rawLine
      val trimmedStart = line.trimStart()
      if (trimmedStart.startsWith("```") || trimmedStart.startsWith("~~~")) {
        fence = trimmedStart.take(3)
        continue
      }
      var quote = false
      while (true) {
        val probe = line.trimStart()
        if (probe.startsWith(">") && line.length - probe.length <= 3) {
          quote = true
          line = probe.substring(1).let { if (it.startsWith(' ')) it.substring(1) else it }
        } else {
          break
        }
      }
      var list: FluidRichTextListKind? = null
      var heading = false
      val bullet = BulletPattern.matchEntire(line)
      val numbered = NumberedPattern.matchEntire(line)
      val headingMatch = HeadingPattern.matchEntire(line)
      when {
        bullet != null && !RulePattern.matches(line) -> {
          list = FluidRichTextListKind.Bullet
          line = bullet.groupValues[1]
        }
        numbered != null -> {
          list = FluidRichTextListKind.Numbered
          line = numbered.groupValues[1]
        }
        headingMatch != null -> {
          heading = true
          line = headingMatch.groupValues[1].trimEnd().trimEnd('#').trimEnd()
        }
        RulePattern.matches(line) -> line = ""
      }
      assembler.begin(FluidRichTextParagraphStyle(list = list, quote = quote))
      MarkdownInlineReader(line, assembler, heading).read()
      assembler.end()
    }
    return assembler.build()
  }

  // Un solo spazio dopo il segno: quelli in piu' sono del testo della voce (lo scrittore ne mette uno).
  private val BulletPattern = Regex("^ {0,3}[-*+][ \\t](.*)$")
  private val NumberedPattern = Regex("^ {0,3}\\d{1,9}[.)][ \\t](.*)$")
  private val HeadingPattern = Regex("^ {0,3}#{1,6}[ \\t]+(.*)$")
  private val RulePattern = Regex("^ {0,3}([-*_])( *\\1){2,} *$")
}

/** Scrive i caratteri di un paragrafo con i segni del Markdown, ben annidati e con gli spazi fuori dai segni. */
private class MarkdownInlineWriter(
  private val document: FluidRichTextDocument,
  private val paragraph: Int,
  private val out: StringBuilder,
  private val atLineStart: Boolean,
) {
  private val open = ArrayList<Marker>()
  private val lineStart = out.length

  fun write() {
    val start = document.paragraphStart(paragraph)
    val end = document.paragraphEnd(paragraph)
    var linkOpen: String? = null
    document.forEachRun { runStart, runEnd, format ->
      val a = maxOf(runStart, start)
      val b = minOf(runEnd, end)
      if (a < b) {
        val piece = document.text.substring(a, b)
        if (format.link != linkOpen) {
          closeAll()
          if (linkOpen != null) out.append("](").append(escapeUrl(linkOpen!!)).append(')')
          linkOpen = format.link
          if (linkOpen != null) out.append('[')
        }
        if (piece.isBlank()) {
          // Spazi soli: il Markdown non sa dare uno stile a uno spazio (`** **` non e' grassetto, e una
          // coppia vuota di segni rovinerebbe il testo). Restano dentro i segni aperti, o fuori.
          appendEscaped(piece)
          return@forEachRun
        }
        val wanted = buildList {
          if (format.bold) add(Marker.Bold)
          if (format.italic) add(Marker.Italic)
          if (format.underline) add(Marker.Underline)
        }
        var common = 0
        while (common < open.size && common < wanted.size && open[common] == wanted[common]) common++
        closeDownTo(common)
        val leading = piece.takeWhile { it == ' ' || it == '\t' }
        if (common < wanted.size && leading.isNotEmpty() && leading.length < piece.length) {
          appendEscaped(leading)
          for (i in common until wanted.size) {
            out.append(wanted[i].open)
            open.add(wanted[i])
          }
          appendEscaped(piece.substring(leading.length))
        } else {
          for (i in common until wanted.size) {
            out.append(wanted[i].open)
            open.add(wanted[i])
          }
          appendEscaped(piece)
        }
      }
    }
    closeAll()
    if (linkOpen != null) out.append("](").append(escapeUrl(linkOpen!!)).append(')')
  }

  private fun closeAll() = closeDownTo(0)

  /** Chiude i segni sopra [keep], spostando gli spazi finali fuori dai segni (`**a **` non e' grassetto). */
  private fun closeDownTo(keep: Int) {
    if (open.size <= keep) return
    var trailing = 0
    while (trailing < out.length - lineStart && (out[out.length - 1 - trailing] == ' ' || out[out.length - 1 - trailing] == '\t')) trailing++
    val spaces = out.substring(out.length - trailing)
    out.setLength(out.length - trailing)
    for (i in open.size - 1 downTo keep) out.append(open[i].close)
    while (open.size > keep) open.removeAt(open.size - 1)
    out.append(spaces)
  }

  private fun appendEscaped(text: String) {
    for (char in text) {
      // In testa alla riga (spazi a parte) certi segni farebbero di un paragrafo un titolo, una
      // citazione o una voce: si proteggono. Il resto della riga no, perche' li' non significano niente.
      val atStart = atLineStart && onlyBlanks
      when {
        char in AlwaysEscaped -> out.append('\\').append(char)
        atStart && (char == '#' || char == '>' || char == '-' || char == '+') -> out.append('\\').append(char)
        (char == '.' || char == ')') && atLineStart && leadingDigits in 1..9 -> out.append('\\').append(char)
        else -> out.append(char)
      }
      // «2024. Un anno» in testa alla riga sarebbe una voce numerata: si contano le cifre iniziali.
      leadingDigits = when {
        atStart && char.isDigit() -> 1
        leadingDigits > 0 && char.isDigit() -> leadingDigits + 1
        else -> 0
      }
      if (char != ' ' && char != '\t') onlyBlanks = false
    }
  }

  private var onlyBlanks = true
  private var leadingDigits = 0

  private fun escapeUrl(url: String): String = url.replace("\\", "%5C").replace("(", "\\(").replace(")", "\\)")

  private enum class Marker(val open: String, val close: String) {
    Bold("**", "**"),
    Italic("*", "*"),
    Underline("<u>", "</u>"),
  }

  companion object {
    val AlwaysEscaped = setOf('\\', '*', '_', '[', ']', '`', '<')
  }
}

/** Legge i segni in linea di una riga di Markdown. */
private class MarkdownInlineReader(
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
            pending.append(line.substring(index + ticks, close).trim())
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
