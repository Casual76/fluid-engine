package dev.antigravity.fluidengine.ui.fluid

/**
 * Il Markdown ridotto a un albero: titoli, paragrafi, elenchi (anche annidati), citazioni, blocchi
 * di codice, tabelle, righe, e in linea grassetto, corsivo, barrato, codice, link. **Nessuna riga
 * di Compose qui dentro**: e' il pezzo che le app avevano scritto ognuna per conto suo (tre
 * `MarkdownLite` da un centinaio di righe, una libreria esterna per le note, un secondo resa a
 * blocchi per lo streaming di una chat) e che si puo' provare sul computer. Lo disegna
 * [FluidMarkdown]; chi vuole solo il testo senza segni usa [FluidMarkdownParser.plainText].
 *
 * Nasce anche per il **testo che sta arrivando** (la risposta di un modello, una trascrizione). Uno
 * stream si ferma ovunque, quindi ogni costrutto aperto e mai chiuso ha una lettura sola e innocua:
 *  - un `**` o un `` ` `` senza chiusura resta testo (come fa un renderer vero: non si porta dietro
 *    tutto il paragrafo);
 *  - un blocco di codice senza la chiusura e' un [FluidMdBlock.CodeBlock] con `closed = false`, che
 *    la resa mostra gia' come blocco e non come tre apici nudi;
 *  - un'intestazione di tabella senza la riga dei trattini e' un paragrafo, e diventa tabella quando
 *    la riga arriva.
 *
 * Non si parlano gli HTML, le note a pie' di pagina, le definizioni di link: un testo da leggere
 * in una card o in una nota non le usa. I marcatori `[[chip]]` che un assistente scrive per i chip
 * sotto la risposta si possono togliere dal testo ([FluidMarkdownOptions.hideChipMarkers], spento
 * di serie: in una nota `[[cosi']]` e' un collegamento fra pagine, non un rumore).
 */
sealed interface FluidMdInline {
  data class Text(val text: String) : FluidMdInline
  data class Bold(val children: List<FluidMdInline>) : FluidMdInline
  data class Italic(val children: List<FluidMdInline>) : FluidMdInline
  data class Strike(val children: List<FluidMdInline>) : FluidMdInline
  data class Code(val text: String) : FluidMdInline
  data class Link(val children: List<FluidMdInline>, val url: String) : FluidMdInline
}

/** Come si allinea una colonna di una tabella. */
enum class FluidMdAlign { Start, Center, End }

/** Una voce di elenco: il suo testo, e i blocchi che le stanno sotto (un sotto-elenco, un paragrafo rientrato). */
data class FluidMdListItem(
  val inlines: List<FluidMdInline>,
  val children: List<FluidMdBlock> = emptyList(),
  /**
   * Una voce di un elenco di cose da fare (`- [ ] …`, `- [x] …`, 3.0.1): fatta o no. Null per una voce
   * qualunque.
   */
  val checked: Boolean? = null,
)

sealed interface FluidMdBlock {
  data class Paragraph(val inlines: List<FluidMdInline>) : FluidMdBlock

  /** [level] da 1 a 6; di serie chi lo disegna ne fa due misure sole (vedi `FluidMarkdownStyle.headings`). */
  data class Heading(val level: Int, val inlines: List<FluidMdInline>) : FluidMdBlock

  /** [start] e' il numero della prima voce di un elenco numerato (un elenco che comincia da 3 resta da 3). */
  data class ListBlock(val ordered: Boolean, val start: Int, val items: List<FluidMdListItem>) : FluidMdBlock

  /** [closed] falso: il testo e' ancora in arrivo e i tre apici di chiusura non ci sono. */
  data class CodeBlock(val language: String?, val code: String, val closed: Boolean) : FluidMdBlock

  data class Quote(val blocks: List<FluidMdBlock>) : FluidMdBlock

  data class Table(
    val header: List<List<FluidMdInline>>,
    val aligns: List<FluidMdAlign>,
    val rows: List<List<List<FluidMdInline>>>,
  ) : FluidMdBlock {
    val columns: Int get() = maxOf(header.size, rows.maxOfOrNull { it.size } ?: 0)
  }

  data object Rule : FluidMdBlock
}

/**
 * @param hideChipMarkers toglie `[[id]]` e `[[id:valore]]` dal testo. Sono i marcatori che un assistente
 *   scrive per i chip sotto la risposta (a fine risposta diventano chip, e mostrarli mentre arrivano
 *   non dice niente): li accende l'interfaccia dell'assistente, e di serie restano testo perche'
 *   un testo qualunque (una nota, un changelog) puo' usare le parentesi doppie per se'.
 * @param softBreak cosa diventa un a capo singolo dentro un paragrafo: uno spazio, come in Markdown.
 *   Chi vuole le righe cosi' come sono state scritte passa `"\n"`.
 */
data class FluidMarkdownOptions(
  val hideChipMarkers: Boolean = false,
  val softBreak: String = " ",
)

object FluidMarkdownParser {

  fun parse(markdown: String, options: FluidMarkdownOptions = FluidMarkdownOptions()): List<FluidMdBlock> =
    blocks(markdown.replace("\r\n", "\n").replace('\r', '\n').lines(), options)

  // ---- i blocchi ---------------------------------------------------------------------------------

  private fun blocks(lines: List<String>, options: FluidMarkdownOptions): List<FluidMdBlock> {
    val out = ArrayList<FluidMdBlock>()
    var i = 0
    val paragraph = ArrayList<String>()

    fun flushParagraph() {
      if (paragraph.isEmpty()) return
      val text = paragraph.joinToString(options.softBreak) { it.trim() }
      paragraph.clear()
      out += FluidMdBlock.Paragraph(inlines(text, options))
    }

    while (i < lines.size) {
      val line = lines[i]
      val trimmed = line.trim()
      when {
        trimmed.isEmpty() -> {
          flushParagraph()
          i++
        }

        isFence(trimmed) -> {
          flushParagraph()
          val fence = trimmed.takeWhile { it == trimmed[0] }
          val language = trimmed.removePrefix(fence).trim().substringBefore(' ').takeIf { it.isNotBlank() }
          val body = ArrayList<String>()
          i++
          var closed = false
          while (i < lines.size) {
            val inner = lines[i].trim()
            if (inner.startsWith(fence) && inner.all { it == fence[0] }) {
              closed = true
              i++
              break
            }
            body += lines[i]
            i++
          }
          out += FluidMdBlock.CodeBlock(language, body.joinToString("\n"), closed)
        }

        isRule(trimmed) -> {
          flushParagraph()
          out += FluidMdBlock.Rule
          i++
        }

        HeadingLine.matches(trimmed) -> {
          flushParagraph()
          val match = HeadingLine.matchEntire(trimmed)!!
          // I cancelletti di chiusura ("## Estate ##") si tolgono solo se staccati da uno spazio:
          // "Imparare C#" finisce con un cancelletto che e' parte del titolo.
          val content = match.groupValues[2].trim().replace(ClosingHashes, "").trim()
          out += FluidMdBlock.Heading(match.groupValues[1].length, inlines(content, options))
          i++
        }

        trimmed.startsWith(">") -> {
          flushParagraph()
          val quoted = ArrayList<String>()
          while (i < lines.size && lines[i].trim().startsWith(">")) {
            quoted += lines[i].trim().removePrefix(">").removePrefix(" ")
            i++
          }
          out += FluidMdBlock.Quote(blocks(quoted, options))
        }

        isTableStart(lines, i) -> {
          flushParagraph()
          val header = splitRow(lines[i])
          val aligns = splitRow(lines[i + 1]).map { alignOf(it) }
          i += 2
          val rows = ArrayList<List<List<FluidMdInline>>>()
          while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) {
            rows += splitRow(lines[i]).map { inlines(it, options) }
            i++
          }
          out += FluidMdBlock.Table(header.map { inlines(it, options) }, aligns, rows)
        }

        listMarker(line)?.let { marker -> paragraph.isEmpty() || marker.number == null || marker.number == 1 } == true -> {
          flushParagraph()
          val (block, next) = list(lines, i, options)
          out += block
          i = next
        }

        else -> {
          paragraph += line
          i++
        }
      }
    }
    flushParagraph()
    return out
  }

  /** Un elenco, e dove finisce: le voci piu' rientrate della prima sono figlie della voce sopra. */
  private fun list(lines: List<String>, from: Int, options: FluidMarkdownOptions): Pair<FluidMdBlock.ListBlock, Int> {
    val first = listMarker(lines[from])!!
    val baseIndent = first.indent
    val ordered = first.number != null
    val items = ArrayList<FluidMdListItem>()
    var i = from
    while (i < lines.size) {
      val marker = listMarker(lines[i])
      if (marker == null || marker.indent < baseIndent || (marker.indent == baseIndent && (marker.number != null) != ordered)) break
      if (marker.indent > baseIndent) break
      var text = marker.text
      val children = ArrayList<String>()
      i++
      while (i < lines.size) {
        val line = lines[i]
        if (line.isBlank()) {
          // Una riga vuota fra due voci non chiude l'elenco se la voce dopo e' ancora una voce.
          val next = lines.getOrNull(i + 1)?.let { listMarker(it) }
          if (next != null && next.indent >= baseIndent && (next.indent > baseIndent || (next.number != null) == ordered)) {
            i++
            continue
          }
          break
        }
        val inner = listMarker(line)
        if (inner != null) {
          if (inner.indent > baseIndent) {
            children += line
            i++
            continue
          }
          break
        }
        // Una riga che continua la voce: rientrata, e non e' un titolo ne' un blocco di codice.
        if (indentOf(line) > baseIndent && !isFence(line.trim()) && !HeadingLine.matches(line.trim())) {
          if (children.isEmpty()) text += options.softBreak + line.trim() else children += line
          i++
          continue
        }
        break
      }
      // Una voce da fare (3.0.1): `[ ]` o `[x]` in testa, e poi il testo. Senza lo spazio dopo la parentesi
      // non lo e' (`[x]y` e' testo), come su GitHub.
      val task = TaskItem.matchEntire(text)
      val checked = task?.groupValues?.get(1)?.let { it != " " }
      val body = task?.groupValues?.get(2) ?: text
      items += FluidMdListItem(
        inlines(body, options),
        if (children.isEmpty()) emptyList() else blocks(dedent(children), options),
        checked,
      )
    }
    return FluidMdBlock.ListBlock(ordered, first.number ?: 1, items) to i
  }

  private class Marker(val indent: Int, val number: Int?, val text: String)

  private fun listMarker(line: String): Marker? {
    val indent = indentOf(line)
    val rest = line.substring(indent.coerceAtMost(line.length))
    Bullet.matchEntire(rest)?.let { return Marker(indent, null, it.groupValues[1]) }
    Numbered.matchEntire(rest)?.let { return Marker(indent, it.groupValues[1].toIntOrNull(), it.groupValues[2]) }
    return null
  }

  private fun indentOf(line: String): Int {
    var n = 0
    for (c in line) {
      when (c) {
        ' ' -> n++
        '\t' -> n += 4
        else -> return n
      }
    }
    return n
  }

  /** Toglie il rientro comune: le voci figlie tornano a essere un blocco come un altro. */
  private fun dedent(lines: List<String>): List<String> {
    val common = lines.filter { it.isNotBlank() }.minOfOrNull { indentOf(it) } ?: 0
    return lines.map { if (it.isBlank()) "" else it.drop(minOf(common, it.length - it.trimStart().length)) }
  }

  private fun isFence(trimmed: String): Boolean =
    (trimmed.startsWith("```") || trimmed.startsWith("~~~")) && trimmed.takeWhile { it == trimmed[0] }.length >= 3

  private fun isRule(trimmed: String): Boolean =
    trimmed.length >= 3 && (Rule1.matches(trimmed) || Rule2.matches(trimmed) || Rule3.matches(trimmed))

  private fun isTableStart(lines: List<String>, i: Int): Boolean {
    val head = lines[i]
    val delimiter = lines.getOrNull(i + 1) ?: return false
    if (!head.contains('|') || !delimiter.contains('-')) return false
    val cells = splitRow(delimiter)
    return cells.isNotEmpty() && cells.size == splitRow(head).size && cells.all { TableDelimiter.matches(it.trim()) }
  }

  private fun splitRow(row: String): List<String> {
    var trimmed = row.trim()
    if (trimmed.startsWith("|")) trimmed = trimmed.substring(1)
    if (trimmed.endsWith("|") && !trimmed.endsWith("\\|")) trimmed = trimmed.dropLast(1)
    val cells = ArrayList<String>()
    val current = StringBuilder()
    var i = 0
    while (i < trimmed.length) {
      val c = trimmed[i]
      if (c == '\\' && i + 1 < trimmed.length && trimmed[i + 1] == '|') {
        current.append('|')
        i += 2
        continue
      }
      if (c == '|') {
        cells += current.toString().trim()
        current.setLength(0)
      } else {
        current.append(c)
      }
      i++
    }
    cells += current.toString().trim()
    return cells
  }

  private fun alignOf(cell: String): FluidMdAlign {
    val c = cell.trim()
    return when {
      c.startsWith(":") && c.endsWith(":") -> FluidMdAlign.Center
      c.endsWith(":") -> FluidMdAlign.End
      else -> FluidMdAlign.Start
    }
  }

  // ---- il testo in linea ---------------------------------------------------------------------------

  /** Grassetto, corsivo, barrato, codice e link in una riga; un segno aperto e mai chiuso resta testo. */
  fun inlines(text: String, options: FluidMarkdownOptions = FluidMarkdownOptions()): List<FluidMdInline> {
    val source = if (options.hideChipMarkers) ChipMarker.replace(text, "") else text
    return InlineParser(source).parse(0, source.length)
  }

  private class InlineParser(private val s: String) {

    fun parse(from: Int, to: Int): List<FluidMdInline> {
      val out = ArrayList<FluidMdInline>()
      val plain = StringBuilder()
      fun flush() {
        if (plain.isNotEmpty()) {
          out += FluidMdInline.Text(plain.toString())
          plain.setLength(0)
        }
      }
      var i = from
      while (i < to) {
        val c = s[i]
        when {
          c == '\\' && i + 1 < to && s[i + 1] in Escapable -> {
            plain.append(s[i + 1])
            i += 2
          }

          c == '`' -> {
            val run = runLength(i, '`', to)
            val close = findRun(i + run, '`', run, to)
            if (close < 0) {
              plain.append(s, i, i + run)
              i += run
            } else {
              flush()
              var code = s.substring(i + run, close)
              if (code.length >= 2 && code.startsWith(" ") && code.endsWith(" ") && code.isNotBlank()) code = code.substring(1, code.length - 1)
              out += FluidMdInline.Code(code)
              i = close + run
            }
          }

          (c == '*' || c == '_') && i + 1 < to && s[i + 1] == c -> {
            val end = closer(i + 2, c, 2, to)
            if (end > i + 2 && !s[i + 2].isWhitespace() && (c == '*' || boundaryBefore(i))) {
              flush()
              out += FluidMdInline.Bold(parse(i + 2, end))
              i = end + 2
            } else {
              plain.append(c).append(c)
              i += 2
            }
          }

          (c == '*' || c == '_') -> {
            val end = if (i + 1 < to && !s[i + 1].isWhitespace() && (c == '*' || boundaryBefore(i))) closer(i + 1, c, 1, to) else -1
            if (end > i + 1) {
              flush()
              out += FluidMdInline.Italic(parse(i + 1, end))
              i = end + 1
            } else {
              plain.append(c)
              i++
            }
          }

          c == '~' && i + 1 < to && s[i + 1] == '~' -> {
            val end = closer(i + 2, '~', 2, to)
            if (end > i + 2 && !s[i + 2].isWhitespace()) {
              flush()
              out += FluidMdInline.Strike(parse(i + 2, end))
              i = end + 2
            } else {
              plain.append("~~")
              i += 2
            }
          }

          c == '!' && i + 1 < to && s[i + 1] == '[' && linkAt(i + 1, to) != null -> {
            // Un'immagine non si disegna in una card: resta il suo testo alternativo, come un link.
            val link = linkAt(i + 1, to)!!
            flush()
            out += FluidMdInline.Link(parse(link.textFrom, link.textTo).ifEmpty { listOf(FluidMdInline.Text(link.url)) }, link.url)
            i = link.end
          }

          c == '[' -> {
            val link = linkAt(i, to)
            if (link != null) {
              flush()
              out += FluidMdInline.Link(parse(link.textFrom, link.textTo).ifEmpty { listOf(FluidMdInline.Text(link.url)) }, link.url)
              i = link.end
            } else {
              plain.append(c)
              i++
            }
          }

          (c == 'h') && (s.startsWith("http://", i) || s.startsWith("https://", i)) && (i == from || !s[i - 1].isLetterOrDigit()) -> {
            var end = i
            while (end < to && !s[end].isWhitespace() && s[end] !in UrlStop) end++
            // La punteggiatura che chiude la frase non e' dell'indirizzo.
            while (end > i && s[end - 1] in UrlTrail) end--
            val url = s.substring(i, end)
            flush()
            out += FluidMdInline.Link(listOf(FluidMdInline.Text(url)), url)
            i = end
          }

          else -> {
            plain.append(c)
            i++
          }
        }
      }
      flush()
      return out
    }

    private fun runLength(at: Int, ch: Char, to: Int): Int {
      var n = 0
      while (at + n < to && s[at + n] == ch) n++
      return n
    }

    /** Dove comincia, da [from], una serie di esattamente [length] [ch]; -1 se non c'e'. */
    private fun findRun(from: Int, ch: Char, length: Int, to: Int): Int {
      var i = from
      while (i < to) {
        if (s[i] == ch) {
          val run = runLength(i, ch, to)
          if (run == length) return i
          i += run
        } else {
          i++
        }
      }
      return -1
    }

    /**
     * Dove chiude il segno [ch] ripetuto [count] volte che si e' aperto prima di [from]: la prima serie
     * non preceduta da uno spazio (un segno attaccato a uno spazio e' un segno aperto, non uno chiuso).
     * Il corsivo a un segno salta le serie piu' lunghe (un `**` li' dentro e' un grassetto, aperto o
     * chiuso), il grassetto accetta una serie piu' lunga e chiude con le sue ultime due (`**a *b***`).
     * Non si guarda dentro il codice, e `_` dentro una parola ("snake_case") non chiude niente.
     * -1 se non chiude.
     */
    private fun closer(from: Int, ch: Char, count: Int, to: Int): Int {
      var i = from
      while (i < to) {
        val c = s[i]
        if (c == '`') {
          val run = runLength(i, '`', to)
          val close = findRun(i + run, '`', run, to)
          i = if (close < 0) i + run else close + run
          continue
        }
        if (c == '\\') {
          i += 2
          continue
        }
        if (c == ch) {
          val run = runLength(i, ch, to)
          val candidate = if (count == 1) (if (run == 1) i else -1) else (if (run >= count) i + (run - count) else -1)
          if (candidate > from && !s[candidate - 1].isWhitespace()) {
            val after = candidate + count
            val inWord = ch == '_' && after < to && s[after].isLetterOrDigit()
            if (!inWord) return candidate
          }
          i += run
          continue
        }
        i++
      }
      return -1
    }

    /** `_` apre un corsivo solo all'inizio di una parola: "snake_case_name" e' un nome. */
    private fun boundaryBefore(i: Int): Boolean = i == 0 || !s[i - 1].isLetterOrDigit()

    private class LinkSpan(val textFrom: Int, val textTo: Int, val url: String, val end: Int)

    /** `[testo](indirizzo)` che comincia in [open]; null se non e' un link intero. */
    private fun linkAt(open: Int, to: Int): LinkSpan? {
      var depth = 0
      var i = open
      var close = -1
      while (i < to) {
        when (s[i]) {
          '\\' -> i++
          '[' -> depth++
          ']' -> {
            depth--
            if (depth == 0) {
              close = i
              break
            }
          }
        }
        i++
      }
      if (close < 0 || close + 1 >= to || s[close + 1] != '(') return null
      var paren = 0
      var j = close + 1
      var end = -1
      while (j < to) {
        when (s[j]) {
          '(' -> paren++
          ')' -> {
            paren--
            if (paren == 0) {
              end = j
              break
            }
          }
          '\n' -> return null
        }
        j++
      }
      if (end < 0) return null
      val url = s.substring(close + 2, end).trim().substringBefore(' ').trim('<', '>')
      if (url.isEmpty()) return null
      return LinkSpan(open + 1, close, url, end + 1)
    }
  }

  // ---- il testo senza segni ------------------------------------------------------------------------

  /**
   * Lo stesso testo senza i segni: per la voce di sistema (che leggerebbe gli asterischi), per
   * un'anteprima in cronologia, per la condivisione in chiaro. Il codice e' rumore detto ad alta
   * voce e di default si salta; le voci di un elenco restano una per riga.
   *
   * @param bullets se mettere il segno davanti alle voci (per condividere) o no (per leggere).
   * @param options come si legge il testo (i marcatori dei chip, gli a capo): sono quelle di [parse].
   */
  fun plainText(
    markdown: String,
    includeCode: Boolean = false,
    bullets: Boolean = false,
    options: FluidMarkdownOptions = FluidMarkdownOptions(),
  ): String {
    val out = ArrayList<String>()
    fun walk(blocks: List<FluidMdBlock>) {
      for (block in blocks) when (block) {
        is FluidMdBlock.Paragraph -> out += plain(block.inlines)
        is FluidMdBlock.Heading -> out += plain(block.inlines)
        is FluidMdBlock.ListBlock -> block.items.forEachIndexed { index, item ->
          val mark = when {
            !bullets -> ""
            block.ordered -> "${block.start + index}. "
            else -> "- "
          }
          out += mark + plain(item.inlines)
          walk(item.children)
        }
        is FluidMdBlock.CodeBlock -> if (includeCode && block.code.isNotBlank()) out += block.code
        is FluidMdBlock.Quote -> walk(block.blocks)
        is FluidMdBlock.Table -> {
          out += block.header.joinToString(", ") { plain(it) }
          block.rows.forEach { row -> out += row.joinToString(", ") { plain(it) } }
        }
        FluidMdBlock.Rule -> Unit
      }
    }
    walk(parse(markdown, options))
    return out.filter { it.isNotBlank() }.joinToString("\n")
  }

  fun plain(inlines: List<FluidMdInline>): String = buildString {
    fun add(items: List<FluidMdInline>) {
      for (item in items) when (item) {
        is FluidMdInline.Text -> append(item.text)
        is FluidMdInline.Code -> append(item.text)
        is FluidMdInline.Bold -> add(item.children)
        is FluidMdInline.Italic -> add(item.children)
        is FluidMdInline.Strike -> add(item.children)
        is FluidMdInline.Link -> add(item.children)
      }
    }
    add(inlines)
  }

  private val HeadingLine = Regex("(#{1,6})\\s+(.*)")
  private val ClosingHashes = Regex("\\s+#+$")
  private val Bullet = Regex("[-*+•]\\s+(.*)")
  private val Numbered = Regex("(\\d{1,4})[.)]\\s+(.*)")
  private val TaskItem = Regex("\\[([ xX])]\\s+(.*)", RegexOption.DOT_MATCHES_ALL)
  private val Rule1 = Regex("(?:-\\s*){3,}")
  private val Rule2 = Regex("(?:\\*\\s*){3,}")
  private val Rule3 = Regex("(?:_\\s*){3,}")
  private val TableDelimiter = Regex(":?-{1,}:?")
  private val ChipMarker = Regex("\\[\\[[^\\]\\n]*]]")
  private const val Escapable = "\\`*_{}[]()#+-.!|~>"
  private const val UrlStop = "<>\"'`"
  private const val UrlTrail = ".,;:!?)]}"
}
