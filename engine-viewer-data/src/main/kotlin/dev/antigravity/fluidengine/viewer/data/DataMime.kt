package dev.antigravity.fluidengine.viewer.data

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale

/** Le intestazioni di una parte, in ordine, con i nomi come nel file. */
internal class MimeHeaders(val list: List<Pair<String, String>>) {
  operator fun get(name: String): String? = list.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second
  fun all(name: String): List<String> = list.filter { it.first.equals(name, ignoreCase = true) }.map { it.second }
}

/** Un valore con parametri: `text/html; charset="utf-8"`, `attachment; filename*=UTF-8''r%C3%A9sum%C3%A9.pdf`. */
internal class MimeValue(val value: String, val params: Map<String, String>)

/** Una parte MIME, già decodificata dal suo Content-Transfer-Encoding. */
internal class MimePart(
  val headers: MimeHeaders,
  val type: String,
  val params: Map<String, String>,
  val body: ByteArray,
  val children: List<MimePart>,
) {
  val disposition: MimeValue? = headers["Content-Disposition"]?.let(DataMime::parameters)
  val contentId: String? = headers["Content-ID"]?.trim()?.trim('<', '>')?.takeIf { it.isNotEmpty() }
  val contentLocation: String? = headers["Content-Location"]?.trim()
  val transferEncoding: String = headers["Content-Transfer-Encoding"]?.trim()?.lowercase(Locale.ROOT) ?: "7bit"
  val charset: String? = params["charset"]

  /** Il nome del file: `filename` della disposizione, poi `name` del tipo (RFC 2231 e 2047 compresi). */
  val filename: String? = (disposition?.params?.get("filename") ?: params["name"])?.let(DataMime::decodeWords)?.trim()?.takeIf { it.isNotEmpty() }
  val multipart: Boolean get() = type.startsWith("multipart/")
  val attachmentDisposition: Boolean get() = disposition?.value?.equals("attachment", ignoreCase = true) == true

  /** Il testo della parte, nel suo charset (o riconosciuto). */
  fun text(): String = DataText.decode(DataBytes(body, body.size, false), charset).text
}

/** Un indirizzo: «Mario Rossi <mario@example.com>». */
internal class MailAddress(val name: String?, val address: String?) {
  override fun toString(): String = when {
    name.isNullOrBlank() -> address.orEmpty()
    address.isNullOrBlank() -> name
    else -> "$name <$address>"
  }
  val display: String get() = name?.takeIf { it.isNotBlank() } ?: address.orEmpty()
}

/** Una mail letta: le intestazioni, il corpo HTML o di testo, le parti in linea per `cid:` e gli allegati. */
internal class DataMail(
  val headers: MimeHeaders,
  val subject: String?,
  val from: List<MailAddress>,
  val to: List<MailAddress>,
  val cc: List<MailAddress>,
  val replyTo: List<MailAddress>,
  val date: ZonedDateTime?,
  val dateRaw: String?,
  val html: String?,
  val text: String?,
  val inline: Map<String, MimePart>,
  val attachments: List<MimePart>,
  val parts: Int,
  val charsets: Set<String>,
  val encodings: Set<String>,
) {
  /** La parte in linea di un `cid:` o di un Content-Location. */
  fun part(id: String): MimePart? = inline[id] ?: inline[id.trim('<', '>')] ?: inline.values.firstOrNull { it.contentLocation == id }
}

/**
 * F6C.13 · Il lettore MIME delle mail (EML), in Kotlin puro (§1.3):
 * - intestazioni RFC 5322 con le righe piegate, parole codificate RFC 2047 (`=?UTF-8?B?…?=`, `=?ISO-8859-1?Q?…?=`) e
 *   byte a 8 bit grezzi;
 * - `multipart/alternative` (vince l'HTML), `mixed` (il primo corpo, poi gli allegati) e `related` (le immagini `cid:`);
 * - quoted-printable e base64, e i charset dichiarati (anche quelli vecchi: ISO-8859-x, windows-125x, KOI8-R, Shift_JIS);
 * - i parametri RFC 2231 (`filename*=UTF-8''…`, anche spezzati in `filename*0*`, `filename*1*`).
 * Un messaggio inoltrato (`message/rfc822`) è un allegato `.eml`, che si apre con lo stesso visore.
 */
internal object DataMime {
  private const val MaxDepth = 32
  private const val MaxParts = 2_000

  fun parse(bytes: ByteArray, length: Int = bytes.size): DataMail {
    var count = 0
    val charsets = LinkedHashSet<String>()
    val encodings = LinkedHashSet<String>()
    fun part(from: Int, to: Int, depth: Int, defaultType: String): MimePart {
      count++
      val split = headerEnd(bytes, from, to)
      val headers = headers(bytes, from, split.first)
      val bodyStart = split.second
      val contentType = parameters(headers["Content-Type"] ?: defaultType)
      val type = contentType.value.lowercase(Locale.ROOT).ifEmpty { defaultType }
      contentType.params["charset"]?.let { charsets += it.lowercase(Locale.ROOT) }
      val encoding = headers["Content-Transfer-Encoding"]?.trim()?.lowercase(Locale.ROOT)
      encoding?.let { encodings += it }
      val boundary = contentType.params["boundary"]
      if (type.startsWith("multipart/") && boundary != null && depth < MaxDepth && count < MaxParts) {
        val childDefault = if (type == "multipart/digest") "message/rfc822" else "text/plain"
        val children = boundaries(bytes, bodyStart, to, boundary).map { (a, b) -> part(a, b, depth + 1, childDefault) }
        return MimePart(headers, type, contentType.params, ByteArray(0), children)
      }
      val raw = bytes.copyOfRange(bodyStart.coerceAtMost(to), to)
      val body = when (encoding) {
        "base64" -> base64(raw)
        "quoted-printable" -> quotedPrintable(raw)
        else -> raw
      }
      return MimePart(headers, type, contentType.params, body, emptyList())
    }
    val root = part(0, length, 0, "text/plain")
    val inline = LinkedHashMap<String, MimePart>()
    val attachments = ArrayList<MimePart>()
    var html: String? = null
    var text: String? = null

    fun collectInline(p: MimePart) {
      if (p.multipart) p.children.forEach(::collectInline)
      else {
        p.contentId?.let { inline[it] = p }
        p.contentLocation?.let { if (!inline.containsKey(it)) inline[it] = p }
      }
    }
    fun body(p: MimePart): Boolean {
      when {
        p.attachmentDisposition -> return false
        p.type == "text/html" -> { if (html == null) html = p.text(); return true }
        p.type == "text/plain" || p.type == "text/enriched" -> { if (text == null) text = p.text(); return true }
        p.type == "multipart/alternative" -> {
          // Le alternative vanno dalla più semplice alla più ricca: si tengono le due che sappiamo mostrare.
          var found = false
          for (child in p.children.asReversed()) if (body(child)) found = true
          return found
        }
        p.type == "multipart/related" -> {
          val start = p.params["start"]?.trim('<', '>')
          val rootPart = p.children.firstOrNull { it.contentId == start } ?: p.children.firstOrNull() ?: return false
          p.children.filter { it !== rootPart }.forEach(::collectInline)
          return body(rootPart)
        }
        p.multipart -> {
          var found = false
          for (child in p.children) {
            if (!found && body(child)) found = true
            else if (child.multipart) collectRest(child, attachments)
            else attachments += child
          }
          return found
        }
      }
      return false
    }
    if (!body(root)) {
      if (root.multipart) collectRest(root, attachments) else attachments += root
    }
    // Un'immagine «in linea» che il corpo HTML non usa è comunque un allegato da vedere.
    val used = html?.let { h -> inline.filter { (id, _) -> h.contains("cid:$id", ignoreCase = true) || h.contains(id) } } ?: emptyMap()
    for ((_, p) in inline) if (p !in used.values && p !in attachments && p.filename != null) attachments += p
    attachments.removeAll { a -> a in used.values }

    val headers = root.headers
    return DataMail(
      headers = headers,
      subject = headers["Subject"]?.let(::decodeWords)?.trim()?.takeIf { it.isNotEmpty() },
      from = addresses(headers["From"]),
      to = addresses(headers.all("To").joinToString(", ")),
      cc = addresses(headers.all("Cc").joinToString(", ")),
      replyTo = addresses(headers["Reply-To"]),
      date = headers["Date"]?.let(::date),
      dateRaw = headers["Date"]?.trim(),
      html = html,
      text = text,
      inline = inline,
      attachments = attachments.filter { it.body.isNotEmpty() || it.type == "message/rfc822" },
      parts = count,
      charsets = charsets,
      encodings = encodings,
    )
  }

  private fun collectRest(p: MimePart, out: MutableList<MimePart>) {
    if (p.multipart) p.children.forEach { collectRest(it, out) } else out += p
  }

  /** Dove finiscono le intestazioni: la prima riga vuota (CRLF CRLF o LF LF). Restituisce (fine intestazioni, inizio corpo). */
  private fun headerEnd(bytes: ByteArray, from: Int, to: Int): Pair<Int, Int> {
    var i = from
    // Una parte senza intestazioni comincia con la riga vuota.
    if (i < to && (bytes[i] == '\n'.code.toByte())) return from to from + 1
    if (i + 1 < to && bytes[i] == '\r'.code.toByte() && bytes[i + 1] == '\n'.code.toByte()) return from to from + 2
    while (i < to) {
      if (bytes[i] == '\n'.code.toByte()) {
        val next = i + 1
        if (next < to && bytes[next] == '\n'.code.toByte()) return i to next + 1
        if (next + 1 < to && bytes[next] == '\r'.code.toByte() && bytes[next + 1] == '\n'.code.toByte()) return i to next + 2
      }
      i++
    }
    return to to to
  }

  private fun headers(bytes: ByteArray, from: Int, to: Int): MimeHeaders {
    // I byte a 8 bit nelle intestazioni (RFC 6532, o mittenti scorretti): UTF-8 se lo sono, altrimenti windows-1252.
    val raw = DataText.decode(DataBytes(bytes.copyOfRange(from, to), to - from, false)).text
    val out = ArrayList<Pair<String, String>>()
    var name: String? = null
    val value = StringBuilder()
    for (line in raw.split('\n')) {
      val l = line.removeSuffix("\r")
      if (l.isEmpty()) continue
      if ((l[0] == ' ' || l[0] == '\t') && name != null) {
        value.append(' ').append(l.trim())
        continue
      }
      name?.let { out += it to value.toString().trim() }
      val colon = l.indexOf(':')
      if (colon <= 0) {
        name = null
        continue
      }
      name = l.substring(0, colon).trim()
      value.setLength(0)
      value.append(l.substring(colon + 1))
    }
    name?.let { out += it to value.toString().trim() }
    return MimeHeaders(out)
  }

  /** Le parti di un multipart: fra una riga `--boundary` e la successiva, fino a `--boundary--`. */
  private fun boundaries(bytes: ByteArray, from: Int, to: Int, boundary: String): List<Pair<Int, Int>> {
    val marker = "--$boundary".toByteArray(Charsets.ISO_8859_1)
    val out = ArrayList<Pair<Int, Int>>()
    var partStart = -1
    var i = from
    var lineStart = from
    while (i <= to) {
      if (i == to || bytes[i] == '\n'.code.toByte()) {
        val lineEnd = i
        if (startsWith(bytes, lineStart, lineEnd, marker)) {
          val after = lineStart + marker.size
          val closing = after + 1 < lineEnd + 1 && after + 1 <= to && after < to && bytes[after] == '-'.code.toByte() &&
            after + 1 < to && bytes[after + 1] == '-'.code.toByte()
          if (partStart >= 0) {
            // La parte finisce prima dell'a capo che precede il separatore.
            var end = lineStart
            if (end > partStart && bytes[end - 1] == '\n'.code.toByte()) end--
            if (end > partStart && bytes[end - 1] == '\r'.code.toByte()) end--
            out += partStart to end
          }
          if (closing) return out
          partStart = (lineEnd + 1).coerceAtMost(to)
        }
        lineStart = i + 1
      }
      i++
    }
    if (partStart in 0 until to) out += partStart to to
    return out
  }

  private fun startsWith(bytes: ByteArray, from: Int, to: Int, prefix: ByteArray): Boolean {
    if (to - from < prefix.size) return false
    for (k in prefix.indices) if (bytes[from + k] != prefix[k]) return false
    // Dopo il separatore solo «--», spazi o la fine della riga.
    var j = from + prefix.size
    if (j + 1 < to && bytes[j] == '-'.code.toByte() && bytes[j + 1] == '-'.code.toByte()) j += 2
    while (j < to) {
      val b = bytes[j].toInt()
      if (b != ' '.code && b != '\t'.code && b != '\r'.code) return false
      j++
    }
    return true
  }

  /** Base64 tollerante: gli a capo e i caratteri fuori alfabeto si saltano, il riempimento mancante si aggiunge. */
  fun base64(raw: ByteArray): ByteArray {
    val clean = StringBuilder(raw.size)
    for (b in raw) {
      val c = (b.toInt() and 0xFF).toChar()
      if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '+' || c == '/') clean.append(c)
      else if (c == '-' ) clean.append('+') else if (c == '_') clean.append('/')
    }
    while (clean.length % 4 != 0) {
      if (clean.length % 4 == 1) clean.setLength(clean.length - 1) else clean.append('=')
    }
    return try {
      Base64.getDecoder().decode(clean.toString())
    } catch (_: IllegalArgumentException) {
      ByteArray(0)
    }
  }

  /** Quoted-printable: `=XX` è un byte, `=` in fondo alla riga la continua. */
  fun quotedPrintable(raw: ByteArray): ByteArray {
    val out = ByteArrayOutputStream(raw.size)
    var i = 0
    while (i < raw.size) {
      val b = raw[i]
      if (b == '='.code.toByte()) {
        if (i + 1 < raw.size && raw[i + 1] == '\n'.code.toByte()) { i += 2; continue }
        if (i + 2 < raw.size && raw[i + 1] == '\r'.code.toByte() && raw[i + 2] == '\n'.code.toByte()) { i += 3; continue }
        if (i + 2 < raw.size) {
          val hi = Character.digit(raw[i + 1].toInt(), 16)
          val lo = Character.digit(raw[i + 2].toInt(), 16)
          if (hi >= 0 && lo >= 0) {
            out.write(hi * 16 + lo)
            i += 3
            continue
          }
        }
      }
      out.write(b.toInt())
      i++
    }
    return out.toByteArray()
  }

  private val EncodedWord = Regex("""=\?([^?\s]+)\?([BbQq])\?([^?\s]*)\?=""")

  /** Le parole codificate RFC 2047; lo spazio fra due parole codificate vicine sparisce, come vuole la norma. */
  fun decodeWords(value: String): String {
    if (!value.contains("=?")) return value
    val glued = value.replace(Regex("""(\?=)\s+(=\?)"""), "$1$2")
    return EncodedWord.replace(glued) { m ->
      val charset = DataText.charsetOf(m.groupValues[1].substringBefore('*')) ?: Charsets.UTF_8
      val data = m.groupValues[3]
      val bytes = if (m.groupValues[2].equals("B", ignoreCase = true)) base64(data.toByteArray(Charsets.ISO_8859_1))
      else quotedPrintable(data.replace('_', ' ').toByteArray(Charsets.ISO_8859_1))
      String(bytes, charset)
    }
  }

  /**
   * Un valore con parametri. I valori fra virgolette tengono `;` e spazi; RFC 2231 dà `nome*=charset'lingua'valore`
   * (percent-encoded) e i pezzi `nome*0`, `nome*1*`, uniti in ordine.
   */
  fun parameters(header: String): MimeValue {
    val parts = ArrayList<String>()
    val current = StringBuilder()
    var quoted = false
    var escape = false
    for (c in header) {
      when {
        escape -> { current.append(c); escape = false }
        c == '\\' && quoted -> escape = true
        c == '"' -> quoted = !quoted
        c == ';' && !quoted -> { parts += current.toString(); current.setLength(0) }
        else -> current.append(c)
      }
    }
    parts += current.toString()
    val value = parts.first().trim()
    val simple = LinkedHashMap<String, String>()
    val continued = HashMap<String, MutableMap<Int, Pair<String, Boolean>>>()
    for (raw in parts.drop(1)) {
      val eq = raw.indexOf('=')
      if (eq <= 0) continue
      var key = raw.substring(0, eq).trim().lowercase(Locale.ROOT)
      val v = raw.substring(eq + 1).trim()
      val star = key.indexOf('*')
      if (star < 0) {
        simple[key] = v
        continue
      }
      val encoded = key.endsWith("*")
      val base = key.substring(0, star)
      val index = key.substring(star + 1).trimEnd('*').toIntOrNull() ?: 0
      continued.getOrPut(base) { HashMap() }[index] = v to encoded
      key = base
    }
    for ((base, pieces) in continued) {
      var charset: Charset = Charsets.UTF_8
      val bytes = ByteArrayOutputStream()
      for (index in pieces.keys.sorted()) {
        var (v, encoded) = pieces.getValue(index)
        if (encoded && index == pieces.keys.minOrNull()) {
          val first = v.indexOf('\'')
          val second = if (first >= 0) v.indexOf('\'', first + 1) else -1
          if (first >= 0 && second > first) {
            DataText.charsetOf(v.substring(0, first))?.let { charset = it }
            v = v.substring(second + 1)
          }
        }
        if (encoded) bytes.write(percentDecode(v)) else bytes.write(v.toByteArray(charset))
      }
      simple[base] = String(bytes.toByteArray(), charset)
    }
    return MimeValue(value, simple)
  }

  private fun percentDecode(v: String): ByteArray {
    val out = ByteArrayOutputStream(v.length)
    var i = 0
    while (i < v.length) {
      val c = v[i]
      if (c == '%' && i + 2 < v.length + 0 && i + 2 <= v.lastIndex) {
        val hi = Character.digit(v[i + 1], 16)
        val lo = Character.digit(v[i + 2], 16)
        if (hi >= 0 && lo >= 0) {
          out.write(hi * 16 + lo)
          i += 3
          continue
        }
      }
      out.write(c.code and 0xFF)
      i++
    }
    return out.toByteArray()
  }

  /** Gli indirizzi di un'intestazione: le virgole fra virgolette o fra parentesi angolari non separano. */
  fun addresses(header: String?): List<MailAddress> {
    val value = header?.let(::decodeWords)?.trim().orEmpty()
    if (value.isEmpty()) return emptyList()
    val items = ArrayList<String>()
    val current = StringBuilder()
    var quoted = false
    var angle = 0
    for (c in value) {
      when {
        c == '"' -> { quoted = !quoted; current.append(c) }
        c == '<' && !quoted -> { angle++; current.append(c) }
        c == '>' && !quoted -> { angle--; current.append(c) }
        (c == ',' || c == ';') && !quoted && angle <= 0 -> { items += current.toString(); current.setLength(0) }
        else -> current.append(c)
      }
    }
    items += current.toString()
    return items.mapNotNull { raw ->
      val item = raw.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
      val lt = item.lastIndexOf('<')
      val gt = item.lastIndexOf('>')
      if (lt >= 0 && gt > lt) {
        val name = item.substring(0, lt).trim().trim('"').replace("\\\"", "\"").trim().takeIf { it.isNotEmpty() }
        MailAddress(name, item.substring(lt + 1, gt).trim())
      } else if ('@' in item) {
        // «mario@example.com (Mario Rossi)»: il commento è il nome.
        val comment = Regex("""\(([^)]*)\)""").find(item)?.groupValues?.get(1)?.trim()
        MailAddress(comment, item.replace(Regex("""\([^)]*\)"""), "").trim())
      } else {
        MailAddress(item.trim('"'), null)
      }
    }
  }

  /** La data RFC 5322, tollerante: senza giorno della settimana, con un commento «(CET)», con l'anno a due cifre. */
  fun date(value: String): ZonedDateTime? {
    val clean = value.replace(Regex("""\([^)]*\)"""), "").replace(Regex("\\s+"), " ").trim()
    val candidates = listOf(clean, clean.substringAfter(", ", clean))
    for (c in candidates) {
      try {
        return ZonedDateTime.parse(c, DateTimeFormatter.RFC_1123_DATE_TIME)
      } catch (_: Exception) {
      }
      for (pattern in listOf("d MMM yyyy HH:mm:ss Z", "d MMM yyyy HH:mm Z", "EEE, d MMM yyyy HH:mm:ss Z", "d MMM yy HH:mm:ss Z", "d MMM yyyy HH:mm:ss z")) {
        try {
          return ZonedDateTime.parse(c, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
        } catch (_: Exception) {
        }
      }
    }
    return null
  }
}
