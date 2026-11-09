package dev.antigravity.fluidengine.ui.fluid

/** Chiude i canali di rete che Chromium apre senza passare da `shouldInterceptRequest`.
 *
 * `<link rel=preconnect>` e `<link rel=dns-prefetch>` non sono caricamenti di risorse: il renderer li
 * consegna al processo browser, che apre socket o risolve nomi senza creare una richiesta. Per questo
 * `blockNetworkLoads`, `setNetworkAvailable(false)` e la CSP non li vedono. Lo stesso vale per
 * `<meta http-equiv="x-dns-prefetch-control">`, che riaccende la risoluzione anticipata delle ancore, e
 * per un `<iframe srcdoc>`, che e' un secondo documento il cui HTML arriva codificato in un attributo.
 *
 * Nessuna impostazione di WebView spegne questi canali, quindi il documento viene riscritto prima di
 * arrivare al renderer. La riscrittura non analizza l'HTML come farebbe un browser, perche' un
 * analizzatore «quasi uguale» a quello di Chromium e' il difetto tipico di un filtro (e Chromium ne ha
 * due: il costruttore dell'albero tiene il primo attributo duplicato, lo scanner di precaricamento
 * l'ultimo `rel`; dentro `<svg>` il primo non crea un link HTML, il secondo si'). Si appoggia invece a
 * un solo fatto, vero per entrambi: un elemento `link`, `meta` o `iframe` nasce soltanto dalla sequenza
 * letterale `<` + nome, senza distinzione fra maiuscole e minuscole ASCII. Niente entita', NUL o
 * codifica la sostituiscono, perche' i byte serviti sono sempre UTF-8 dichiarato nell'intestazione.
 *
 * Le regole sono quindi letterali e senza contesto, cioe' valgono anche dentro commenti, CDATA,
 * attributi, `<style>`, `<svg>`, `<template>` e tag non chiusi:
 * - `<link` e `<meta` diventano `<basefont`: un elemento vuoto, ammesso in `head` e in `body` come loro,
 *   che Chromium non implementa. L'albero conserva la stessa forma e gli attributi originali restano
 *   attaccati a un elemento che non fa nulla.
 * - `<iframe` e `</iframe` diventano `<noframes` e `</noframes`: testo grezzo nascosto, com'era il
 *   contenuto di ripiego dell'iframe. Nessun documento annidato viene creato, nemmeno da `srcdoc`.
 * - Prima dell'originale neutralizzato viene emesso un elemento **canonico**, scritto qui e non copiato,
 *   solo quando una lettura prudente riconosce un caso utile e innocuo: un foglio di stile
 *   (`rel` uguale a `stylesheet` e basta), `<meta name="viewport">`, `<meta name="color-scheme">`.
 *   Ogni dubbio (attributo duplicato, entita' non banale, virgolette mancanti, NUL, `integrity`,
 *   `crossorigin`, `http-equiv`) fa rinunciare all'elemento canonico: si perde uno stile, mai la chiusura.
 *
 * Un errore di lettura puo' quindi solo togliere un foglio di stile o sbagliarne l'indirizzo, che passa
 * comunque da `onRequest`. Non puo' produrre un suggerimento di rete, perche' `rel` e' una costante.
 * [isSealed] ricontrolla l'esito con una grammatica indipendente; il contenitore non carica un documento
 * che non la supera.
 *
 * Costo: una passata. La lettura degli attributi ha un tetto per tag e un tetto totale proporzionale
 * alla lunghezza, cosi' un documento costruito apposta non diventa quadratico: finito il credito si
 * continua a neutralizzare senza piu' riemettere elementi canonici.
 */
internal object FluidWebSeal {
  /** Oltre questa distanza dal nome del tag la lettura rinuncia: un foglio di stile vero ne usa poche centinaia. */
  const val MaxTagCharacters: Int = 8192

  private const val LinkHead = "<link rel=\"stylesheet\" href=\""
  private const val MetaHead = "<meta name=\""
  private const val MetaContent = "\" content=\""
  private const val Neutral = "<basefont"
  private val metaNames = arrayOf("viewport", "color-scheme")

  /** Restituisce la stessa istanza quando il documento non contiene nessuna delle sequenze trattate. */
  fun seal(html: String): String {
    val length = html.length
    var out: StringBuilder? = null
    var copied = 0
    var credit = length.toLong() + MaxTagCharacters
    val reader = AttributeReader(html)
    var at = html.indexOf('<')
    while (at >= 0) {
      val name = at + 1
      val end = when {
        matches(html, name, "link") -> name + 4
        matches(html, name, "meta") -> name + 4
        matches(html, name, "iframe") -> name + 6
        matches(html, name, "/iframe") -> name + 7
        else -> -1
      }
      if (end < 0) {
        at = html.indexOf('<', name)
        continue
      }
      val builder = out ?: StringBuilder(length + 256).also { out = it }
      builder.append(html, copied, at)
      when (end - name) {
        4 -> {
          if (credit > 0) {
            val attributes = reader.read(end)
            credit -= reader.cost
            if (attributes != null) {
              if ((html[name].code or 0x20) == 'l'.code) appendCanonicalLink(builder, attributes)
              else appendCanonicalMeta(builder, attributes)
            }
          }
          builder.append(Neutral)
        }
        6 -> builder.append("<noframes")
        else -> builder.append("</noframes")
      }
      copied = end
      at = html.indexOf('<', end)
    }
    val builder = out ?: return html
    builder.append(html, copied, length)
    return builder.toString()
  }

  /** Vero se ogni `<link` e `<meta` del testo e' esattamente un elemento canonico e non resta nessun `iframe`.
   *
   * Non riusa la lettura di [seal]: controlla la sola forma emessa, con valori privi di virgolette e
   * parentesi angolari, che qualunque tokenizzatore HTML legge allo stesso modo.
   */
  fun isSealed(html: String): Boolean {
    var at = html.indexOf('<')
    while (at >= 0) {
      val name = at + 1
      val end = when {
        matches(html, name, "link") -> canonicalLinkEnd(html, at)
        matches(html, name, "meta") -> canonicalMetaEnd(html, at)
        matches(html, name, "iframe") || matches(html, name, "/iframe") -> return false
        else -> name
      }
      if (end < 0) return false
      at = html.indexOf('<', end)
    }
    return true
  }

  /** Confronto ASCII esplicito: il ripiegamento Unicode (K di Kelvin, I con punto) non e' quello dell'HTML. */
  private fun matches(text: String, from: Int, name: String): Boolean {
    if (from + name.length > text.length) return false
    for (index in name.indices) {
      val expected = name[index]
      val actual = text[from + index]
      if (actual != expected && (expected !in 'a'..'z' || actual.code != expected.code - 0x20)) return false
    }
    return true
  }

  private fun appendCanonicalLink(out: StringBuilder, attributes: Map<String, String>) {
    // integrity e crossorigin non si possono onorare: le intestazioni della risposta sono del contenitore.
    if ("disabled" in attributes || "integrity" in attributes || "crossorigin" in attributes) return
    val rel = decode(attributes["rel"] ?: return) ?: return
    if (!sameWord(rel.trim(::isSpace), "stylesheet")) return
    attributes["type"]?.let { raw ->
      val type = (decode(raw) ?: return).trim(::isSpace)
      if (type.isNotEmpty() && !sameWord(type, "text/css")) return
    }
    val href = decode(attributes["href"] ?: return) ?: return
    if (href.all(::isSpace)) return
    val media = attributes["media"]?.let { decode(it) ?: return }
    val title = attributes["title"]?.let { decode(it) ?: return }
    out.append(LinkHead)
    appendEscaped(out, href)
    out.append('"')
    if (!media.isNullOrEmpty()) { out.append(" media=\""); appendEscaped(out, media); out.append('"') }
    if (!title.isNullOrEmpty()) { out.append(" title=\""); appendEscaped(out, title); out.append('"') }
    out.append('>')
  }

  private fun appendCanonicalMeta(out: StringBuilder, attributes: Map<String, String>) {
    if ("http-equiv" in attributes) return
    val name = decode(attributes["name"] ?: return) ?: return
    val known = metaNames.firstOrNull { sameWord(name, it) } ?: return
    val content = decode(attributes["content"] ?: return) ?: return
    out.append(MetaHead).append(known).append(MetaContent)
    appendEscaped(out, content)
    out.append("\">")
  }

  private fun appendEscaped(out: StringBuilder, value: String) {
    for (char in value) when (char) {
      '&' -> out.append("&amp;")
      '"' -> out.append("&quot;")
      '<' -> out.append("&lt;")
      '>' -> out.append("&gt;")
      else -> out.append(char)
    }
  }

  private fun sameWord(value: String, word: String): Boolean = value.length == word.length && matches(value, 0, word)

  private fun isSpace(char: Char): Boolean = char == ' ' || char == '\t' || char == '\n' || char == '\u000C' || char == '\r'

  private fun isAlphanumeric(char: Char): Boolean = char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9'

  /** Riferimenti a carattere di un valore di attributo; `null` quando l'esito non e' certo senza la tabella completa.
   *
   * Certi: i numerici chiusi da `;` su un carattere ordinario, i cinque nomi dell'XML chiusi da `;`, e un nome
   * seguito da `=`, che in un attributo non viene mai decodificato (e' il caso di `?a=1&b=2`).
   */
  private fun decode(raw: String): String? {
    val first = raw.indexOf('&')
    if (first < 0) return raw
    val out = StringBuilder(raw.length)
    out.append(raw, 0, first)
    var index = first
    while (index < raw.length) {
      val char = raw[index]
      if (char != '&') { out.append(char); index++; continue }
      val next = if (index + 1 < raw.length) raw[index + 1] else ' '
      when {
        next == '#' -> {
          var cursor = index + 2
          val hex = cursor < raw.length && (raw[cursor] == 'x' || raw[cursor] == 'X')
          if (hex) cursor++
          val digits = cursor
          var code = 0
          while (cursor < raw.length && cursor - digits < 8) {
            val digit = Character.digit(raw[cursor], if (hex) 16 else 10)
            if (digit < 0 || raw[cursor].code > 0x7F) break
            code = code * (if (hex) 16 else 10) + digit
            if (code > 0x10FFFF) return null
            cursor++
          }
          if (cursor == digits || cursor >= raw.length || raw[cursor] != ';') return null
          val ordinary = code == 0x9 || code == 0xA || code == 0xC || code in 0x20..0x7E ||
            (code in 0xA0..0x10FFFF && code !in 0xD800..0xDFFF && code !in 0xFDD0..0xFDEF && (code and 0xFFFE) != 0xFFFE)
          if (!ordinary) return null
          out.appendCodePoint(code)
          index = cursor + 1
        }
        isAlphanumeric(next) -> {
          var cursor = index + 1
          while (cursor < raw.length && isAlphanumeric(raw[cursor])) cursor++
          val after = if (cursor < raw.length) raw[cursor] else ' '
          when (after) {
            '=' -> { out.append(raw, index, cursor); index = cursor }
            ';' -> {
              out.append(when (raw.substring(index + 1, cursor)) {
                "amp", "AMP" -> '&'
                "lt", "LT" -> '<'
                "gt", "GT" -> '>'
                "quot", "QUOT" -> '"'
                "apos" -> '\''
                else -> return null
              })
              index = cursor + 1
            }
            else -> return null
          }
        }
        else -> { out.append('&'); index++ }
      }
    }
    return out.toString()
  }

  /** Posizione dopo il `>` di un link canonico che inizia in [at], oppure -1. */
  private fun canonicalLinkEnd(html: String, at: Int): Int {
    if (!html.startsWith(LinkHead, at)) return -1
    var cursor = valueEnd(html, at + LinkHead.length)
    for (optional in arrayOf(" media=\"", " title=\"")) {
      if (cursor >= 0 && html.startsWith(optional, cursor)) cursor = valueEnd(html, cursor + optional.length)
    }
    return if (cursor >= 0 && cursor < html.length && html[cursor] == '>') cursor + 1 else -1
  }

  private fun canonicalMetaEnd(html: String, at: Int): Int {
    if (!html.startsWith(MetaHead, at)) return -1
    val name = at + MetaHead.length
    val known = metaNames.firstOrNull { html.startsWith(it, name) && html.startsWith(MetaContent, name + it.length) } ?: return -1
    val cursor = valueEnd(html, name + known.length + MetaContent.length)
    return if (cursor >= 0 && cursor < html.length && html[cursor] == '>') cursor + 1 else -1
  }

  /** Posizione dopo le virgolette che chiudono un valore senza `"`, `<`, `>` e NUL, oppure -1. */
  private fun valueEnd(html: String, from: Int): Int {
    var cursor = from
    while (cursor < html.length) {
      when (html[cursor]) {
        '"' -> return cursor + 1
        '<', '>', '\u0000' -> return -1
      }
      cursor++
    }
    return -1
  }

  /** Lettura prudente degli attributi di un tag: segue il tokenizzatore HTML nei casi ordinari e rinuncia negli altri. */
  private class AttributeReader(private val html: String) {
    /** Caratteri esaminati dall'ultima [read], riuscita o no. */
    var cost = 0
      private set

    private var cursor = 0

    fun read(from: Int): Map<String, String>? {
      cursor = from
      val result = attributes(minOf(html.length, from + MaxTagCharacters))
      cost = cursor - from + 1
      return result
    }

    private fun attributes(limit: Int): Map<String, String>? {
      // Il nome deve finire qui: `<linkx` e `<link` + NUL sono altri elementi.
      if (cursor >= limit || !(isSpace(html[cursor]) || html[cursor] == '/' || html[cursor] == '>')) return null
      val found = HashMap<String, String>(8)
      while (true) {
        while (cursor < limit && (isSpace(html[cursor]) || html[cursor] == '/')) cursor++
        if (cursor >= limit) return null
        if (html[cursor] == '>') return found
        val nameStart = cursor
        while (cursor < limit) {
          val char = html[cursor]
          if (isSpace(char) || char == '/' || char == '>' || char == '=') break
          if (char == '\u0000' || char == '"' || char == '\'' || char == '<') return null
          cursor++
        }
        if (cursor == nameStart) return null // un `=` iniziale fa parte del nome: caso anomalo
        val name = lower(html, nameStart, cursor)
        while (cursor < limit && isSpace(html[cursor])) cursor++
        var value = ""
        if (cursor < limit && html[cursor] == '=') {
          cursor++
          while (cursor < limit && isSpace(html[cursor])) cursor++
          if (cursor >= limit) return null
          val quote = html[cursor]
          if (quote == '"' || quote == '\'') {
            val valueStart = ++cursor
            while (cursor < limit && html[cursor] != quote) {
              if (html[cursor] == '\u0000') return null
              cursor++
            }
            if (cursor >= limit) return null
            value = html.substring(valueStart, cursor)
            cursor++
            // Dopo le virgolette serve un separatore: `a="b"c` e' un errore che non conviene interpretare.
            if (cursor >= limit || !(isSpace(html[cursor]) || html[cursor] == '/' || html[cursor] == '>')) return null
          } else {
            val valueStart = cursor
            while (cursor < limit && !isSpace(html[cursor]) && html[cursor] != '>') {
              val char = html[cursor]
              if (char == '\u0000' || char == '"' || char == '\'' || char == '<' || char == '=' || char == '`') return null
              cursor++
            }
            if (cursor == valueStart || cursor >= limit) return null
            value = html.substring(valueStart, cursor)
          }
        }
        // I due analizzatori di Chromium scelgono duplicati diversi: nessuno dei due e' «quello giusto».
        if (found.put(name, value) != null) return null
      }
    }

    private fun lower(text: String, from: Int, to: Int): String {
      val chars = CharArray(to - from) { index ->
        val char = text[from + index]
        if (char in 'A'..'Z') (char.code + 0x20).toChar() else char
      }
      return String(chars)
    }
  }
}
