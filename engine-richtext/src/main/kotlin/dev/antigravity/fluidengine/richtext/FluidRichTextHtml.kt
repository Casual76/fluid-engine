package dev.antigravity.fluidengine.richtext

/**
 * Il documento da e verso un **HTML ristretto**: quello che si manda in una mail, si salva come bozza o
 * firma, si mette negli appunti; e la lettura di qualunque HTML (incollato, ricevuto, salvato) tenendo
 * solo cio' che l'editor sa mostrare.
 *
 * **In uscita** solo questi tag, e nessun attributo tranne `href`: `div` (un paragrafo; vuoto e'
 * `<div><br></div>`, come lo scrivono i client di posta, cosi' la spaziatura e' la stessa ovunque),
 * `ul`/`ol`/`li`, `blockquote`, `b`, `i`, `u`, `a href`. Spazi doppi, in testa e in coda diventano
 * `&nbsp;` (l'HTML li collasserebbe), il tab `&#9;`. Nessuno stile in linea, nessuna classe: chi lo
 * riceve lo mostra col suo carattere.
 *
 * **In ingresso** qualunque HTML, trattato come **ostile**: non si esegue niente e non si carica niente
 * (immagini, fogli di stile, font, frame: mai — il lettore non ha nemmeno un modo per farlo). Si tengono
 * il testo, i paragrafi (`p`, `div`, titoli, righe di tabella, `br`…), gli elenchi (appiattiti a un
 * livello), le citazioni, grassetto/corsivo/sottolineato (anche come `style="font-weight:…"`, che e' come
 * li copia Google Documenti, compreso il suo `<b style="font-weight:normal">` che **non** e' grassetto), e
 * i link con un indirizzo sicuro ([FluidRichTextLinks.sanitize]). Sparisce il contenuto di `script`,
 * `style`, `head`, `template`, `svg`, `math`, `iframe`, `object`, `noscript`, `select`, `textarea`; tutto il
 * resto (tag sconosciuti, colori, caratteri, tabelle) perde il tag e tiene il testo.
 *
 * Il lettore non e' un parser HTML5 completo: non ricostruisce l'albero come un browser nei casi
 * patologici (tag chiusi nell'ordine sbagliato, `<p>` dentro `<b>`). Il risultato resta sempre un
 * documento valido, con tutto il testo leggibile, e i test lo provano su input ostili.
 */
object FluidRichTextHtml {
  /**
   * La versione del dialetto scritto da [write]. Sale se un giorno l'uscita cambia in modo che un
   * lettore vecchio non capirebbe; [read] legge ogni versione (e ogni HTML).
   */
  const val Version: Int = 1

  /** Oltre questa lunghezza l'ingresso si legge fino a qui: un testo da incollare non e' mai cosi' lungo. */
  const val MaxInputLength: Int = 4_000_000

  /** L'HTML ristretto di [document]. */
  fun write(document: FluidRichTextDocument): String = HtmlWriter(document).write()

  /** Il documento letto da [html], qualunque cosa contenga. Mai un'eccezione per un input malformato. */
  fun read(html: String): FluidRichTextDocument = HtmlReader(if (html.length > MaxInputLength) html.substring(0, MaxInputLength) else html).read()
}

// ---------------------------------------------------------------------------------------------------
// Scrittura
// ---------------------------------------------------------------------------------------------------

private class HtmlWriter(private val document: FluidRichTextDocument) {
  private val out = StringBuilder()

  fun write(): String {
    val styles = document.paragraphStyles
    var index = 0
    while (index < styles.size) {
      if (styles[index].quote) {
        out.append("<blockquote>")
        while (index < styles.size && styles[index].quote) index = writeGroup(index, quote = true)
        out.append("</blockquote>")
      } else {
        index = writeGroup(index, quote = false)
      }
    }
    return out.toString()
  }

  /** Scrive un paragrafo, o una serie di voci dello stesso elenco; ritorna l'indice del prossimo. */
  private fun writeGroup(start: Int, quote: Boolean): Int {
    val styles = document.paragraphStyles
    val kind = styles[start].list
    if (kind == null) {
      out.append("<div>")
      writeInline(start)
      out.append("</div>")
      return start + 1
    }
    val tag = if (kind == FluidRichTextListKind.Numbered) "ol" else "ul"
    out.append('<').append(tag).append('>')
    var index = start
    while (index < styles.size && styles[index].quote == quote && styles[index].list == kind) {
      out.append("<li>")
      writeInline(index)
      out.append("</li>")
      index++
    }
    out.append("</").append(tag).append('>')
    return index
  }

  private fun writeInline(paragraph: Int) {
    val start = document.paragraphStart(paragraph)
    val end = document.paragraphEnd(paragraph)
    if (start == end) {
      out.append("<br>")
      return
    }
    val open = ArrayList<Tag>()
    document.forEachRun { runStart, runEnd, format ->
      val a = maxOf(runStart, start)
      val b = minOf(runEnd, end)
      if (a < b) {
        val wanted = tagsFor(format)
        var common = 0
        while (common < open.size && common < wanted.size && open[common] == wanted[common]) common++
        for (i in open.size - 1 downTo common) out.append(open[i].close)
        while (open.size > common) open.removeAt(open.size - 1)
        for (i in common until wanted.size) {
          out.append(wanted[i].open)
          open.add(wanted[i])
        }
        for (i in a until b) appendChar(i, start, end)
      }
    }
    for (i in open.size - 1 downTo 0) out.append(open[i].close)
  }

  private fun appendChar(index: Int, start: Int, end: Int) {
    val text = document.text
    when (val char = text[index]) {
      '&' -> out.append("&amp;")
      '<' -> out.append("&lt;")
      '>' -> out.append("&gt;")
      '"' -> out.append("&quot;")
      '\t' -> out.append("&#9;")
      ' ' -> out.append("&nbsp;")
      ' ' -> {
        val hard = index == start || index == end - 1 || text[index - 1] == ' '
        out.append(if (hard) "&nbsp;" else " ")
      }
      else -> out.append(char)
    }
  }

  private fun tagsFor(format: FluidRichTextInlineFormat): List<Tag> = buildList {
    if (format.bold) add(Tag.Bold)
    if (format.italic) add(Tag.Italic)
    if (format.underline) add(Tag.Underline)
    format.link?.let { add(Tag.Link(it)) }
  }

  private sealed class Tag(val open: String, val close: String) {
    object Bold : Tag("<b>", "</b>")
    object Italic : Tag("<i>", "</i>")
    object Underline : Tag("<u>", "</u>")
    data class Link(val url: String) : Tag("<a href=\"" + escapeAttribute(url) + "\">", "</a>")
  }
}

private fun escapeAttribute(value: String): String = buildString(value.length) {
  for (char in value) {
    when (char) {
      '&' -> append("&amp;")
      '"' -> append("&quot;")
      '<' -> append("&lt;")
      '>' -> append("&gt;")
      else -> append(char)
    }
  }
}

// ---------------------------------------------------------------------------------------------------
// Lettura
// ---------------------------------------------------------------------------------------------------

private class HtmlReader(private val source: String) {
  private val assembler = DocumentAssembler()
  private var position = 0

  /** Gli elementi aperti, con cio' che cambiano del formato. */
  private val frames = ArrayList<Frame>()
  private val lists = ArrayList<FluidRichTextListKind>()
  private var listItemDepth = 0
  private var quoteDepth = 0
  private var preDepth = 0

  /** Uno spazio collassabile visto ma non ancora scritto (si scrive solo se dopo arriva del testo). */
  private var pendingSpace: FluidRichTextInlineFormat? = null

  fun read(): FluidRichTextDocument {
    while (position < source.length) {
      val char = source[position]
      if (char == '<') readMarkup() else readText()
    }
    return assembler.build()
  }

  // --- token ---------------------------------------------------------------------------------------

  private fun readText() {
    val end = source.indexOf('<', position).let { if (it < 0) source.length else it }
    appendSourceText(source, position, end)
    position = end
  }

  private fun readMarkup() {
    when {
      source.startsWith("<!--", position) -> {
        val end = source.indexOf("-->", position + 4)
        position = if (end < 0) source.length else end + 3
      }
      source.startsWith("<![CDATA[", position) -> {
        val end = source.indexOf("]]>", position + 9).let { if (it < 0) source.length else it }
        appendHard(source.substring(position + 9, end))
        position = minOf(source.length, end + 3)
      }
      source.startsWith("<!", position) || source.startsWith("<?", position) -> skipPast('>')
      source.startsWith("</", position) && position + 2 < source.length && source[position + 2].isLetter() -> {
        val (name, _) = readTagName(position + 2)
        skipPast('>')
        endTag(name)
      }
      position + 1 < source.length && source[position + 1].isLetter() -> readStartTag()
      else -> {
        appendSourceText(source, position, position + 1)
        position++
      }
    }
  }

  private fun skipPast(char: Char) {
    val end = source.indexOf(char, position)
    position = if (end < 0) source.length else end + 1
  }

  private fun readTagName(from: Int): Pair<String, Int> {
    var end = from
    while (end < source.length && (source[end].isLetterOrDigit() || source[end] == '-' || source[end] == ':')) end++
    return source.substring(from, end).lowercase() to end
  }

  private fun readStartTag() {
    val (name, afterName) = readTagName(position + 1)
    var cursor = afterName
    val attributes = HashMap<String, String>()
    var selfClosing = false
    while (cursor < source.length) {
      val char = source[cursor]
      when {
        char == '>' -> {
          cursor++
          break
        }
        char == '/' -> {
          selfClosing = cursor + 1 < source.length && source[cursor + 1] == '>'
          cursor++
        }
        char.isWhitespace() -> cursor++
        else -> {
          var nameEnd = cursor
          while (nameEnd < source.length && !source[nameEnd].isWhitespace() && source[nameEnd] != '=' && source[nameEnd] != '>' &&
            !(source[nameEnd] == '/' && nameEnd + 1 < source.length && source[nameEnd + 1] == '>')
          ) nameEnd++
          if (nameEnd == cursor) nameEnd++
          val attributeName = source.substring(cursor, nameEnd).lowercase()
          cursor = nameEnd
          while (cursor < source.length && source[cursor].isWhitespace()) cursor++
          var value = ""
          if (cursor < source.length && source[cursor] == '=') {
            cursor++
            while (cursor < source.length && source[cursor].isWhitespace()) cursor++
            if (cursor < source.length && (source[cursor] == '"' || source[cursor] == '\'')) {
              val quote = source[cursor]
              val close = source.indexOf(quote, cursor + 1).let { if (it < 0) source.length else it }
              value = source.substring(cursor + 1, close)
              cursor = minOf(source.length, close + 1)
            } else {
              val start = cursor
              while (cursor < source.length && !source[cursor].isWhitespace() && source[cursor] != '>') cursor++
              value = source.substring(start, cursor)
            }
          }
          if (attributeName.isNotEmpty() && attributes.size < MaxAttributes) attributes.putIfAbsent(attributeName, decodeEntities(value))
        }
      }
    }
    position = cursor
    startTag(name, attributes, selfClosing)
  }

  // --- elementi ------------------------------------------------------------------------------------

  private fun startTag(name: String, attributes: Map<String, String>, selfClosing: Boolean) {
    if (name in RawTextElements) {
      skipRawText(name)
      return
    }
    if (name in IgnoredElements) {
      if (!selfClosing) skipElement(name)
      return
    }
    when (name) {
      "br" -> {
        lineBreak()
        return
      }
      "hr" -> {
        blockBoundary()
        return
      }
      "img", "input", "meta", "link", "wbr", "area", "base", "col", "embed", "source", "track", "param", "keygen" -> return
    }
    if (name in BlockElements) blockBoundary()
    when (name) {
      "ul" -> lists.add(FluidRichTextListKind.Bullet)
      "ol" -> lists.add(FluidRichTextListKind.Numbered)
      "li" -> listItemDepth++
      "blockquote" -> quoteDepth++
      "pre" -> preDepth++
      "td", "th" -> softSeparator()
    }
    if (selfClosing) {
      endTag(name)
      return
    }
    if (frames.size < MaxDepth) frames.add(Frame(name, overrideFor(name, attributes)))
  }

  private fun endTag(name: String) {
    if (name in BlockElements) blockBoundary()
    when (name) {
      "ul", "ol" -> if (lists.isNotEmpty()) lists.removeAt(lists.size - 1)
      "li" -> if (listItemDepth > 0) listItemDepth--
      "blockquote" -> if (quoteDepth > 0) quoteDepth--
      "pre" -> if (preDepth > 0) preDepth--
      "td", "th" -> softSeparator()
    }
    val index = frames.indexOfLast { it.name == name }
    if (index >= 0) while (frames.size > index) frames.removeAt(frames.size - 1)
  }

  /** Salta fino alla chiusura di un elemento di solo testo (script, style…): il suo contenuto non e' HTML. */
  private fun skipRawText(name: String) {
    val close = source.indexOf("</$name", position, ignoreCase = true)
    position = if (close < 0) source.length else close
    if (close >= 0) skipPast('>')
  }

  /** Salta un elemento e tutto cio' che contiene, contando gli annidamenti dello stesso nome. */
  private fun skipElement(name: String) {
    var depth = 1
    while (position < source.length && depth > 0) {
      val next = source.indexOf('<', position)
      if (next < 0) {
        position = source.length
        return
      }
      position = next
      when {
        source.startsWith("<!--", position) -> {
          val end = source.indexOf("-->", position + 4)
          position = if (end < 0) source.length else end + 3
        }
        source.startsWith("</", position) -> {
          val (closing, _) = readTagName(position + 2)
          skipPast('>')
          if (closing == name) depth--
        }
        else -> {
          val (opening, _) = readTagName(position + 1)
          // `</head>` si puo' omettere: il `<body>` la chiude da se'. Senza questo, un HTML senza
          // chiusura della testa (legale) perderebbe tutto il testo.
          if (name == "head" && opening == "body") return
          skipPast('>')
          if (opening == name && source[position - 2] != '/') depth++
        }
      }
    }
  }

  private fun overrideFor(name: String, attributes: Map<String, String>): Override {
    var bold: Boolean? = null
    var italic: Boolean? = null
    var underline: Boolean? = null
    var link: String? = null
    var setsLink = false
    when (name) {
      "b", "strong" -> bold = true
      "i", "em", "cite", "var", "dfn" -> italic = true
      "u", "ins" -> underline = true
      "h1", "h2", "h3", "h4", "h5", "h6", "th" -> bold = true
      "a" -> {
        setsLink = true
        link = attributes["href"]?.let { FluidRichTextLinks.sanitize(it) }
      }
    }
    attributes["style"]?.let { style ->
      for (declaration in style.split(';')) {
        val property = declaration.substringBefore(':').trim().lowercase()
        val value = declaration.substringAfter(':', "").trim().lowercase().removeSuffix("!important").trim()
        when (property) {
          "font-weight" -> bold = when {
            value == "bold" || value == "bolder" -> true
            value == "normal" || value == "lighter" -> false
            value.toIntOrNull() != null -> value.toInt() >= 600
            else -> bold
          }
          "font-style" -> italic = when (value) {
            "italic", "oblique" -> true
            "normal" -> false
            else -> italic
          }
          "text-decoration", "text-decoration-line" -> underline = when {
            value.contains("underline") -> true
            value.contains("none") -> false
            else -> underline
          }
        }
      }
    }
    return Override(bold, italic, underline, setsLink, link)
  }

  private fun currentFormat(): FluidRichTextInlineFormat {
    var format = FluidRichTextInlineFormat.Plain
    for (frame in frames) {
      val o = frame.override
      if (o.bold != null) format = format.copy(bold = o.bold)
      if (o.italic != null) format = format.copy(italic = o.italic)
      if (o.underline != null) format = format.copy(underline = o.underline)
      if (o.setsLink) format = format.copy(link = o.link)
    }
    return format
  }

  private fun currentParagraphStyle(): FluidRichTextParagraphStyle = FluidRichTextParagraphStyle(
    list = if (listItemDepth > 0) lists.lastOrNull() ?: FluidRichTextListKind.Bullet else null,
    quote = quoteDepth > 0,
  )

  // --- testo e paragrafi ---------------------------------------------------------------------------

  private fun blockBoundary() {
    pendingSpace = null
    if (assembler.isOpen) assembler.end()
  }

  private fun lineBreak() {
    pendingSpace = null
    if (assembler.isOpen) {
      assembler.end()
    } else {
      assembler.begin(currentParagraphStyle())
      assembler.end()
    }
  }

  /** Fra due celle di una riga: uno spazio, non un paragrafo (le mail impaginate a tabelle sono tantissime). */
  private fun softSeparator() {
    if (assembler.isOpen && assembler.currentLength > 0) pendingSpace = pendingSpace ?: FluidRichTextInlineFormat.Plain
  }

  /** Testo del sorgente: gli spazi letterali collassano, le entita' no (`&nbsp;` resta uno spazio vero). */
  private fun appendSourceText(text: String, start: Int, end: Int) {
    var index = start
    val hard = StringBuilder()
    fun flush() {
      if (hard.isNotEmpty()) {
        appendHard(hard)
        hard.setLength(0)
      }
    }
    while (index < end) {
      val char = text[index]
      when {
        char == '&' -> {
          val (decoded, next) = decodeEntityAt(text, index, end)
          hard.append(decoded)
          index = next
          continue
        }
        preDepth > 0 && char == '\n' -> {
          flush()
          lineBreak()
        }
        preDepth > 0 && (char == ' ' || char == '\t') -> hard.append(char)
        char == ' ' || char == '\n' || char == '\t' || char == '\r' || char == '\u000C' -> {
          flush()
          if (assembler.isOpen && assembler.currentLength > 0) pendingSpace = pendingSpace ?: currentFormat()
        }
        else -> hard.append(char)
      }
      index++
    }
    flush()
  }

  private fun appendHard(content: CharSequence) {
    val clean = buildString(content.length) {
      for (char in content) {
        when {
          char == ' ' -> append(' ')
          char == '\t' -> append('\t')
          char == '\n' || char == '\r' -> append(' ')
          char < ' ' || char == '\u007F' || char == '­' -> Unit
          else -> append(char)
        }
      }
    }
    if (clean.isEmpty()) return
    if (!assembler.isOpen) assembler.begin(currentParagraphStyle())
    pendingSpace?.let { assembler.append(" ", it) }
    pendingSpace = null
    assembler.append(clean, currentFormat())
  }

  private class Override(
    val bold: Boolean?,
    val italic: Boolean?,
    val underline: Boolean?,
    val setsLink: Boolean,
    val link: String?,
  )

  private class Frame(val name: String, val override: Override)

  companion object {
    const val MaxDepth = 256
    const val MaxAttributes = 32

    val RawTextElements = setOf("script", "style", "textarea", "title", "xmp", "iframe", "noembed", "noframes", "noscript", "plaintext")
    val IgnoredElements = setOf("head", "template", "svg", "math", "object", "select", "datalist", "audio", "video", "canvas", "map")
    val BlockElements = setOf(
      "address", "article", "aside", "blockquote", "center", "dd", "details", "dialog", "div", "dl", "dt",
      "fieldset", "figcaption", "figure", "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "header",
      "hgroup", "li", "main", "nav", "ol", "p", "pre", "section", "summary", "table", "tbody", "thead",
      "tfoot", "tr", "ul", "caption", "body", "html", "legend", "menu",
    )
  }
}

/** Decodifica le entita' di un valore d'attributo. */
internal fun decodeEntities(value: String): String {
  if (!value.contains('&')) return value
  val out = StringBuilder(value.length)
  var index = 0
  while (index < value.length) {
    if (value[index] == '&') {
      val (decoded, next) = decodeEntityAt(value, index, value.length)
      out.append(decoded)
      index = next
    } else {
      out.append(value[index])
      index++
    }
  }
  return out.toString()
}

/** L'entita' che comincia in [start] (un `&`): il testo e la posizione dopo. Una sconosciuta resta com'e'. */
internal fun decodeEntityAt(text: String, start: Int, end: Int): Pair<String, Int> {
  var cursor = start + 1
  if (cursor < end && text[cursor] == '#') {
    cursor++
    val hex = cursor < end && (text[cursor] == 'x' || text[cursor] == 'X')
    if (hex) cursor++
    val digitsStart = cursor
    while (cursor < end && cursor - digitsStart < 16 && (if (hex) text[cursor].isHexDigit() else text[cursor].isDigit())) cursor++
    if (cursor == digitsStart) return "&" to start + 1
    val code = text.substring(digitsStart, cursor).toLongOrNull(if (hex) 16 else 10) ?: -1L
    if (cursor < end && text[cursor] == ';') cursor++
    val decoded = when {
      code <= 0 || code > 0x10FFFF || code in 0xD800..0xDFFF -> "�"
      else -> String(Character.toChars(code.toInt()))
    }
    return decoded to cursor
  }
  val nameStart = cursor
  while (cursor < end && cursor - nameStart < 12 && text[cursor].isLetterOrDigit()) cursor++
  if (cursor < end && text[cursor] == ';') {
    val name = text.substring(nameStart, cursor)
    NamedEntities[name]?.let { return it to cursor + 1 }
  }
  return "&" to start + 1
}

private fun Char.isHexDigit(): Boolean = isDigit() || this in 'a'..'f' || this in 'A'..'F'

private val NamedEntities: Map<String, String> = mapOf(
  "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
  "copy" to "©", "reg" to "®", "trade" to "™", "hellip" to "…", "mdash" to "—", "ndash" to "–",
  "lsquo" to "‘", "rsquo" to "’", "sbquo" to "‚", "ldquo" to "“", "rdquo" to "”", "bdquo" to "„",
  "laquo" to "«", "raquo" to "»", "lsaquo" to "‹", "rsaquo" to "›", "bull" to "•", "middot" to "·",
  "euro" to "€", "pound" to "£", "yen" to "¥", "cent" to "¢", "sect" to "§", "para" to "¶", "deg" to "°",
  "plusmn" to "±", "times" to "×", "divide" to "÷", "frac12" to "½", "frac14" to "¼", "frac34" to "¾",
  "iexcl" to "¡", "iquest" to "¿", "shy" to "­", "zwj" to "‍", "zwnj" to "‌",
  "lrm" to "‎", "rlm" to "‏", "ensp" to " ", "emsp" to " ", "thinsp" to " ",
  "agrave" to "à", "aacute" to "á", "acirc" to "â", "atilde" to "ã", "auml" to "ä", "aring" to "å",
  "egrave" to "è", "eacute" to "é", "ecirc" to "ê", "euml" to "ë", "igrave" to "ì", "iacute" to "í",
  "icirc" to "î", "iuml" to "ï", "ograve" to "ò", "oacute" to "ó", "ocirc" to "ô", "otilde" to "õ",
  "ouml" to "ö", "ugrave" to "ù", "uacute" to "ú", "ucirc" to "û", "uuml" to "ü", "ccedil" to "ç",
  "ntilde" to "ñ", "szlig" to "ß", "oslash" to "ø", "aelig" to "æ", "oelig" to "œ", "yuml" to "ÿ",
  "Agrave" to "À", "Aacute" to "Á", "Egrave" to "È", "Eacute" to "É", "Igrave" to "Ì", "Iacute" to "Í",
  "Ograve" to "Ò", "Oacute" to "Ó", "Ugrave" to "Ù", "Uacute" to "Ú", "Ccedil" to "Ç", "Ntilde" to "Ñ",
  "Auml" to "Ä", "Ouml" to "Ö", "Uuml" to "Ü", "hearts" to "♥", "rarr" to "→", "larr" to "←",
  "uarr" to "↑", "darr" to "↓", "check" to "✓", "minus" to "−",
)
