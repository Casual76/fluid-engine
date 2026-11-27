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
 * testo del paragrafo `#`, `>`, `-`, `+`, `~`, `1.`) escono preceduti da `\`: un paragrafo che comincia con
 * `~~~` non apre un blocco di codice alla rilettura.
 *
 * In ingresso, oltre a quanto sopra: `__grassetto__`, `_corsivo_`, `* `/`+ ` per l'elenco puntato, `1)`,
 * annidamenti con rientri di 2-4 spazi o tab e piu' segni sulla stessa riga (`- > citazione nella voce`),
 * fino a [FluidRichTextParagraphStyle.MaxDepth] livelli, i titoli `#` (diventano un paragrafo in grassetto), i link automatici
 * `<https://…>`, il codice (resta testo, senza segni; dentro, al piu' uno spazio per lato se ci sono tutti e
 * due, come in CommonMark), i blocchi di codice recintati (testo, riga per riga; il recinto si chiude con
 * uno dello stesso segno lungo almeno quanto quello che l'ha aperto). Gli indirizzi dei link passano da [FluidRichTextLinks.sanitize]. Il Markdown non e' il formato
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
    // Il recinto aperto, intero (tre o piu' accenti o tilde): lo chiude una riga con almeno altrettanti.
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
        fence = trimmedStart.takeWhile { it == trimmedStart[0] }
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
    document.forEachRunIn(start, end) { runStart, runEnd, format ->
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
          return@forEachRunIn
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
        atStart && (char == '#' || char == '>' || char == '-' || char == '+' || char == '~') -> out.append('\\').append(char)
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

/**
 * Legge i segni in linea di una riga di Markdown.
 *
 * **In tempo lineare** anche su una riga ostile di milioni di caratteri (R2, difetto 54). Le domande che il
 * lettore fa sul resto della riga — c'e' piu' avanti chi chiude questo `*`? dove si chiude questa `[`? e
 * questo codice? — non si rifanno scorrendo la riga ogni volta: le risposte si preparano con una passata
 * sola, la prima volta che servono, e si leggono avanzando, perche' il lettore va solo in avanti. Le
 * risposte sono le stesse di prima: una barra rovesciata protegge il carattere dopo se ne ha un numero
 * dispari davanti, contando da dove comincia la loro fila.
 */
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
          var ticksEnd = index
          while (ticksEnd < line.length && line[ticksEnd] == '`') ticksEnd++
          val ticks = ticksEnd - index
          val close = ticks().closer(ticksEnd, ticks)
          if (close in 0 until to) {
            pending.append(codeContent(line.substring(ticksEnd, close)))
            index = close + ticks
          } else {
            pending.append(line, index, ticksEnd)
            index = ticksEnd
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
          val url = autolinkAt(index, to)
          if (url != null && link == null) {
            setFormat { link = url }
            pending.append(FluidRichTextLinks.displayText(url))
            setFormat { link = null }
            index = nextGreater + 1
          } else {
            pending.append(char)
            index++
          }
        }
        char == '[' && link == null -> {
          val closeBracket = brackets().match(index, to)
          if (closeBracket > 0 && closeBracket + 1 < to && line[closeBracket + 1] == '(') {
            val closeParen = parens().match(closeBracket + 1, to)
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

  /** Il testo di un codice in linea: al piu' uno spazio tolto per lato, se ci sono tutti e due e non e' solo spazi (CommonMark). */
  private fun codeContent(content: String): String =
    if (content.length >= 2 && content.first() == ' ' && content.last() == ' ' && content.any { it != ' ' }) {
      content.substring(1, content.length - 1)
    } else {
      content
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
      val closers = closers(char)
      if (!bold && remaining >= 2 && closers.has(end, to, 2)) {
        setFormat { bold = true }
        remaining -= 2
      }
      if (!italic && remaining >= 1 && closers.has(end, to, 1)) {
        setFormat { italic = true }
        remaining--
      }
    }
    repeat(remaining) { pending.append(char) }
    return end
  }

  // --- le risposte preparate ------------------------------------------------------------------------

  private var starClosers: DelimiterClosers? = null
  private var underscoreClosers: DelimiterClosers? = null
  private var tickRuns: TickRuns? = null
  private var bracketPairs: Pairs? = null
  private var parenPairs: Pairs? = null

  private fun closers(char: Char): DelimiterClosers =
    if (char == '*') starClosers ?: DelimiterClosers(line, char).also { starClosers = it }
    else underscoreClosers ?: DelimiterClosers(line, char).also { underscoreClosers = it }

  private fun ticks(): TickRuns = tickRuns ?: TickRuns(line).also { tickRuns = it }

  private fun brackets(): Pairs = bracketPairs ?: Pairs(line, '[', ']').also { bracketPairs = it }

  private fun parens(): Pairs = parenPairs ?: Pairs(line, '(', ')').also { parenPairs = it }

  // Il primo `>`, il primo spazio e il primo `<` dopo l'ultima domanda: il lettore va solo avanti, e cosi'
  // ognuno si cerca una volta. -1: non ce n'e' piu'. Una domanda piu' indietro (non succede) ricomincia.
  private var nextGreater = -2
  private var nextSpace = -2
  private var nextLess = -2
  private var lastAutolink = -1

  private fun advance(cursor: Int, after: Int, char: Char): Int =
    if (cursor != -2 && (cursor == -1 || cursor > after)) cursor else line.indexOf(char, after + 1)

  /**
   * L'indirizzo del link automatico `<…>` che comincia in [start], o null. E' quello di prima: il testo
   * fino al primo `>`, se non ha spazi e se [FluidRichTextLinks.sanitize] lo accetta. Un `<` in mezzo o un
   * testo oltre la lunghezza massima di un indirizzo non passerebbero comunque: si scartano senza
   * copiarli, cosi' una fila di `<` non costa il quadrato.
   */
  private fun autolinkAt(start: Int, to: Int): String? {
    if (start < lastAutolink) {
      nextGreater = -2
      nextSpace = -2
      nextLess = -2
    }
    lastAutolink = start
    nextGreater = advance(nextGreater, start, '>')
    if (nextGreater !in 0 until to) return null
    nextSpace = advance(nextSpace, start, ' ')
    if (nextSpace in 0 until nextGreater) return null
    nextLess = advance(nextLess, start, '<')
    if (nextLess in 0 until nextGreater) return null
    if (nextGreater - start - 1 > FluidRichTextLinks.MaxLength * 2) return null
    return FluidRichTextLinks.sanitize(line.substring(start + 1, nextGreater))
  }

  private fun Char.isAsciiPunctuation(): Boolean = this in '!'..'/' || this in ':'..'@' || this in '['..'`' || this in '{'..'~'
}

/** Una lista di interi che cresce, senza scatole. */
private class IntList {
  var values = IntArray(8)
    private set
  var size = 0
    private set

  fun add(value: Int) {
    if (size == values.size) values = values.copyOf(size * 2)
    values[size++] = value
  }

  fun removeLast() {
    size--
  }

  /** La prima posizione con un valore almeno [value] (i valori sono crescenti). */
  fun lowerBound(value: Int): Int {
    var low = 0
    var high = size
    while (low < high) {
      val mid = (low + high) ushr 1
      if (values[mid] < value) low = mid + 1 else high = mid
    }
    return low
  }
}

/**
 * Le sequenze di [char] della riga che possono chiudere un corsivo o un grassetto: non protette da una
 * barra rovesciata e non dopo uno spazio. Le trova una passata sola, con la regola di prima (una `\`
 * salta il carattere dopo). Da qualunque posizione il lettore chieda, la passata da li' avrebbe visto le
 * stesse sequenze: le posizioni in cui chiede sono fine di una sequenza, e una fila di `\` si legge a
 * coppie dal suo inizio sia da li' sia dall'inizio della riga.
 */
private class DelimiterClosers(line: String, char: Char) {
  private val starts = IntList()
  private val ends = IntList()
  private val longStarts = IntList()
  private val longEnds = IntList()

  init {
    var index = 0
    while (index < line.length) {
      val current = line[index]
      if (current == '\\') {
        index += 2
        continue
      }
      if (current == char) {
        var end = index
        while (end < line.length && line[end] == char) end++
        if (index > 0 && !line[index - 1].isWhitespace()) {
          starts.add(index)
          ends.add(end)
          if (end - index >= 2) {
            longStarts.add(index)
            longEnds.add(end)
          }
        }
        index = end
        continue
      }
      index++
    }
  }

  /** C'e' da [from] in poi, prima di [to], una sequenza di almeno [count] segni (1 o 2) che puo' chiudere? */
  fun has(from: Int, to: Int, count: Int): Boolean {
    val runStarts = if (count >= 2) longStarts else starts
    val runEnds = if (count >= 2) longEnds else ends
    // La prima da [from] in poi: se e' troppo corta perche' [to] la taglia, quelle dopo cominciano oltre [to].
    val at = runStarts.lowerBound(from)
    if (at >= runStarts.size) return false
    val start = runStarts.values[at]
    return start < to && minOf(runEnds.values[at], to) - start >= count
  }
}

/**
 * Le sequenze di accenti gravi della riga e, per ognuna, la prossima piu' lunga: la chiusura di un codice
 * di `n` accenti e' la prima sequenza che ne ha almeno `n` (quello che trovava `indexOf`), e saltando di
 * piu' lunga in piu' lunga la si trova in al piu' `n` passi.
 */
private class TickRuns(line: String) {
  private val starts = IntList()
  private val lengths = IntList()
  private val nextLonger: IntArray

  init {
    var index = 0
    while (index < line.length) {
      if (line[index] == '`') {
        val start = index
        while (index < line.length && line[index] == '`') index++
        starts.add(start)
        lengths.add(index - start)
      } else {
        index++
      }
    }
    val count = starts.size
    nextLonger = IntArray(count)
    val stack = IntArray(count)
    var top = 0
    for (run in count - 1 downTo 0) {
      while (top > 0 && lengths.values[stack[top - 1]] <= lengths.values[run]) top--
      nextLonger[run] = if (top > 0) stack[top - 1] else count
      stack[top++] = run
    }
  }

  /** Dove comincia la prima sequenza da [from] in poi con almeno [ticks] accenti, o -1. */
  fun closer(from: Int, ticks: Int): Int {
    var run = starts.lowerBound(from)
    while (run < starts.size && lengths.values[run] < ticks) run = nextLonger[run]
    return if (run < starts.size) starts.values[run] else -1
  }
}

/**
 * Le coppie [open]/[close] della riga (le parentesi di un link), accoppiate con una pila in una passata
 * sola, con la regola di prima: una `\` salta il carattere dopo, e si chiude la piu' recente ancora
 * aperta: e' la stessa risposta del conteggio della profondita' che si faceva da ogni apertura.
 */
private class Pairs(line: String, open: Char, close: Char) {
  private val opens = IntList()
  private val matches = IntList()

  init {
    val stack = IntList()
    var index = 0
    while (index < line.length) {
      when (line[index]) {
        '\\' -> index++
        open -> {
          stack.add(opens.size)
          opens.add(index)
          matches.add(-1)
        }
        close -> if (stack.size > 0) {
          matches.values[stack.values[stack.size - 1]] = index
          stack.removeLast()
        }
      }
      index++
    }
  }

  /** Dove si chiude l'apertura in [at], se prima di [to]; altrimenti -1. */
  fun match(at: Int, to: Int): Int {
    val index = opens.lowerBound(at)
    if (index >= opens.size || opens.values[index] != at) return -1
    val end = matches.values[index]
    return if (end in 0 until to) end else -1
  }
}
