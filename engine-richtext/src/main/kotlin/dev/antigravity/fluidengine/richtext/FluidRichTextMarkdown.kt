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
 * `> ` per la citazione. Gli annidamenti si scrivono come in CommonMark: un sotto-elenco rientra fin dove
 * comincia il testo della voce che lo contiene (`- a` / `  - b`, `1. a` / `   1. b`), una citazione nella
 * citazione e' `> > `, una citazione in una voce `  > `; la numerazione riparte in ogni sotto-elenco. I
 * caratteri che il Markdown leggerebbe come segni (`*`, `_`, `[`, `]`, `\`, `` ` ``, `<`, e in testa al
 * testo del paragrafo `#`, `>`, `-`, `+`, `1.`) escono preceduti da `\`.
 *
 * In ingresso, oltre a quanto sopra: `__grassetto__`, `_corsivo_`, `* `/`+ ` per l'elenco puntato, `1)`,
 * annidamenti con rientri di 2-4 spazi o tab e piu' segni sulla stessa riga (`- > citazione nella voce`),
 * fino a [FluidRichTextParagraphStyle.MaxDepth] livelli, i titoli `#` (diventano un paragrafo in grassetto), i link automatici
 * `<https://…>`, il codice (resta testo, senza segni), i blocchi di codice recintati (testo, riga per
 * riga). Gli indirizzi dei link passano da [FluidRichTextLinks.sanitize]. Il Markdown non e' il formato
 * di riferimento dell'editor: se l'app puo' scegliere, per salvare usi [FluidRichTextHtml].
 */
object FluidRichTextMarkdown {
  /** Il Markdown di [document]. */
  fun write(document: FluidRichTextDocument): String = buildString {
    val prefixes = ContainerPrefixWriter(document) { index, level, blocks, item, prefix ->
      when (blocks[level]) {
        FluidRichTextBlock.Quote -> "> "
        // Due puntati nuovi di fila sulla stessa riga alternano il segno: `- - - ` da solo sarebbe una
        // linea orizzontale, `- * - ` e' una voce in una voce in una voce.
        FluidRichTextBlock.Bullet -> if (prefix.endsWith("- ")) "* " else "- "
        // Una voce aperta solo per contenere un sotto-elenco (senza testo suo) e' la prima del suo elenco.
        FluidRichTextBlock.Numbered -> (if (item) document.listNumber(index) else 1).toString() + ". "
      }
    }
    for (index in 0 until document.paragraphCount) {
      if (index > 0) append('\n')
      append(prefixes.next(index))
      MarkdownInlineWriter(document, index, this).write()
    }
  }

  /** Il documento letto da [markdown]. Mai un'eccezione. */
  fun read(markdown: String): FluidRichTextDocument {
    val text = normalizeEditorText(
      if (markdown.length > FluidRichTextHtml.MaxInputLength) markdown.substring(0, FluidRichTextHtml.MaxInputLength) else markdown,
    )
    val assembler = DocumentAssembler()
    var fence: String? = null
    // I contenitori della riga prima, con la colonna dove comincia il loro contenuto: una riga che li
    // ripete (`> ` per la citazione, il rientro della voce per un elenco) ci resta dentro.
    var previous: List<MarkdownContainer> = emptyList()
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
      val trimmedStart = rawLine.trimStart()
      if (trimmedStart.startsWith("```") || trimmedStart.startsWith("~~~")) {
        fence = trimmedStart.take(3)
        previous = emptyList()
        continue
      }
      val parsed = parseContainers(rawLine, previous)
      previous = parsed.containers
      var line = rawLine.substring(parsed.contentStart)
      var heading = false
      val headingMatch = HeadingPattern.matchEntire(line)
      when {
        headingMatch != null -> {
          heading = true
          line = headingMatch.groupValues[1].trimEnd().trimEnd('#').trimEnd()
        }
        RulePattern.matches(line) -> line = ""
      }
      assembler.begin(FluidRichTextParagraphStyle(parsed.containers.map { it.block }))
      MarkdownInlineReader(line, assembler, heading).read()
      assembler.end()
    }
    return assembler.build()
  }

  /** Un contenitore di una riga letta: il suo tipo e la colonna dove comincia il suo contenuto. */
  private class MarkdownContainer(val block: FluidRichTextBlock, val contentColumn: Int)

  private class ParsedLine(val containers: List<MarkdownContainer>, val contentStart: Int)

  /**
   * I contenitori di una riga, come li legge CommonMark (semplificato): prima quelli della riga prima che
   * la riga continua (una citazione se c'e' di nuovo `>`, una voce se la riga e' rientrata almeno fin
   * dove comincia il testo della voce), poi quelli nuovi (`>`, `-`/`*`/`+`, `1.`/`1)`), anche piu' d'uno
   * sulla stessa riga (`- > testo` e' una citazione dentro una voce). Una riga solo rientrata, senza un
   * segno nuovo, non e' una voce: e' testo (e i suoi spazi in testa restano, come li ha scritti chi l'ha
   * scritta). Le colonne contano un tab fino al multiplo di 4 successivo.
   */
  private fun parseContainers(line: String, previous: List<MarkdownContainer>): ParsedLine {
    val containers = ArrayList<MarkdownContainer>()
    var position = 0
    var column = 0
    var matched = 0
    for (container in previous) {
      val (indentEnd, indentColumn) = skipIndent(line, position, column, Int.MAX_VALUE)
      if (container.block == FluidRichTextBlock.Quote) {
        if (indentColumn - column > 3 || indentEnd >= line.length || line[indentEnd] != '>') break
        position = indentEnd + 1
        column = indentColumn + 1
        if (position < line.length && line[position] == ' ') {
          position++
          column++
        }
        containers.add(MarkdownContainer(container.block, column))
      } else {
        if (indentEnd >= line.length || indentColumn < container.contentColumn) break
        // Si entra nella voce fino alla colonna del suo testo; il rientro in piu' resta da leggere.
        val (inside, insideColumn) = skipIndent(line, position, column, container.contentColumn)
        position = inside
        column = insideColumn
        containers.add(MarkdownContainer(container.block, container.contentColumn))
      }
      matched++
    }
    var opened = false
    while (containers.size < MaxLineContainers) {
      val (indentEnd, indentColumn) = skipIndent(line, position, column, Int.MAX_VALUE)
      if (indentColumn - column > 3 || indentEnd >= line.length) break
      val rest = line.substring(indentEnd)
      if (rest[0] == '>') {
        position = indentEnd + 1
        column = indentColumn + 1
        if (position < line.length && line[position] == ' ') {
          position++
          column++
        }
        containers.add(MarkdownContainer(FluidRichTextBlock.Quote, column))
        opened = true
        continue
      }
      if (RulePattern.matches(rest)) break
      val marker = ListMarker.find(rest)?.takeIf { it.range.first == 0 } ?: break
      val block = if (marker.value[0].isDigit()) FluidRichTextBlock.Numbered else FluidRichTextBlock.Bullet
      position = indentEnd + marker.value.length
      column = indentColumn + marker.value.length
      // Un solo spazio dopo il segno: quelli in piu' sono del testo della voce (lo scrittore ne mette uno).
      if (position < line.length && (line[position] == ' ' || line[position] == '\t')) {
        position++
        column++
      }
      containers.add(MarkdownContainer(block, column))
      opened = true
    }
    if (!opened && containers.isNotEmpty() && containers.last().block != FluidRichTextBlock.Quote) {
      // Solo rientrata nella voce di sopra: testo fuori dagli elenchi, dentro le citazioni ritrovate.
      val keep = containers.indexOfLast { it.block == FluidRichTextBlock.Quote } + 1
      val start = if (keep == 0) 0 else quoteContentStart(line, keep)
      return ParsedLine(containers.subList(0, keep).toList(), start)
    }
    return ParsedLine(containers, position)
  }

  /** Dove comincia il testo dopo la [count]-esima citazione ritrovata (rileggendo la riga). */
  private fun quoteContentStart(line: String, count: Int): Int {
    var seen = 0
    var index = 0
    while (index < line.length) {
      if (line[index] == '>') {
        seen++
        if (seen == count) return if (index + 1 < line.length && line[index + 1] == ' ') index + 2 else index + 1
      }
      index++
    }
    return line.length
  }

  /**
   * Salta spazi e tab da [start] (alla colonna [column]) finche' non arriva alla colonna [limit]; un tab
   * che la scavalca conta per intero (un rientro con un tab entra nella voce anche se va oltre).
   */
  private fun skipIndent(line: String, start: Int, column: Int, limit: Int): Pair<Int, Int> {
    var index = start
    var col = column
    while (index < line.length && col < limit) {
      col = when (line[index]) {
        ' ' -> col + 1
        '\t' -> (col / 4 + 1) * 4
        else -> break
      }
      index++
    }
    return index to col
  }

  /** Oltre questi contenitori in una riga il resto e' testo: il documento ne terra' comunque al piu' sei. */
  private const val MaxLineContainers = 64

  // Il segno di una voce: seguito da uno spazio o dalla fine della riga (una voce vuota).
  private val ListMarker = Regex("^(?:[-*+]|\\d{1,9}[.)])(?=[ \\t]|$)")
  private val HeadingPattern = Regex("^ {0,3}#{1,6}[ \\t]+(.*)$")
  private val RulePattern = Regex("^ {0,3}([-*_])( *\\1){2,} *$")
}

/**
 * Il prefisso di ogni riga di un testo che mostra i contenitori (Markdown, testo semplice): per un
 * contenitore che la riga prima aveva gia' (e che non e' la voce stessa) si ripete la citazione o si
 * rientra fin dove comincia il testo della voce sopra; per uno nuovo si scrive il suo segno. Cosi' un
 * sotto-elenco sta sotto il testo della voce che lo contiene, e una voce nuova dello stesso elenco ha di
 * nuovo il suo segno.
 *
 * @param marker il segno di un contenitore nuovo al livello `level` del paragrafo `index` (`item` vero se
 *   e' la voce stessa, falso se e' una voce aperta solo per contenere qualcosa di piu' profondo), dopo
 *   il prefisso `prefix` gia' scritto.
 */
internal class ContainerPrefixWriter(
  private val document: FluidRichTextDocument,
  private val marker: (index: Int, level: Int, blocks: List<FluidRichTextBlock>, item: Boolean, prefix: CharSequence) -> String,
) {
  private var previous: List<FluidRichTextBlock> = emptyList()
  private var previousColumns = IntArray(0)

  fun next(index: Int): String {
    val style = document.paragraphStyles[index]
    val blocks = style.blocks
    val common = commonDepth(previous, blocks)
    val columns = IntArray(blocks.size)
    val prefix = StringBuilder()
    for (level in blocks.indices) {
      val item = level == blocks.size - 1 && style.list != null
      val block = blocks[level]
      if (level < common && !item && block != FluidRichTextBlock.Quote) {
        while (prefix.length < previousColumns[level]) prefix.append(' ')
      } else if (level < common && block == FluidRichTextBlock.Quote) {
        prefix.append("> ")
      } else {
        prefix.append(marker(index, level, blocks, item, prefix))
      }
      columns[level] = prefix.length
    }
    previous = blocks
    previousColumns = columns
    return prefix.toString()
  }
}

/** Scrive i caratteri di un paragrafo con i segni del Markdown, ben annidati e con gli spazi fuori dai segni. */
private class MarkdownInlineWriter(
  private val document: FluidRichTextDocument,
  private val paragraph: Int,
  private val out: StringBuilder,
) {
  // Il testo comincia sempre dopo i segni dei contenitori, e li' un altro segno aprirebbe un contenitore
  // (`- - x` e' un sotto-elenco): i segni in testa si proteggono anche nelle voci e nelle citazioni.
  private val atLineStart = true

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
