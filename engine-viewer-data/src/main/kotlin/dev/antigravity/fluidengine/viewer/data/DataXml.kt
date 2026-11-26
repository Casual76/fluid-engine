package dev.antigravity.fluidengine.viewer.data

import android.util.Base64
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.InputStream

/**
 * F6C.13 · XML e plist ad albero, con l'`XmlPullParser` della piattaforma (KXml), come dice la ricerca (§1.3).
 *
 * Un elemento diventa un nodo con i suoi attributi (prima) e i suoi figli; un elemento che contiene solo un testo si
 * mostra in una riga (`nome : "testo"`); i commenti e le istruzioni restano, come nel file. Un errore dà la riga e la
 * colonna che dice il lettore. Un file la cui radice è `<plist>` diventa l'albero dei tipi del plist (dizionari,
 * elenchi, stringhe, numeri, date, dati), con i punti colorati di D.
 */
internal object DataXml {

  /** Il lettore della piattaforma. Senza namespace: i prefissi restano nei nomi (`dc:title`), come nel file. */
  fun parser(relaxed: Boolean = false): XmlPullParser = Xml.newPullParser().apply {
    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    if (relaxed) try {
      setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true)
    } catch (_: Exception) {
    }
  }

  fun parse(input: InputStream, maxNodes: Int = DataProfile.MaxTreeNodes, plistByName: Boolean = false): DataTree {
    val parser = parser()
    val builder = DataTreeBuilder(maxNodes)
    val prolog = LinkedHashMap<String, String>()
    var root: DataNode? = null
    val stack = ArrayList<DataNode>()
    var error: DataTreeError? = null
    var namespaces = 0
    // Il testo di un elemento arriva a pezzi (testo, entita', CDATA): si unisce e si scrive quando arriva altro.
    val pending = StringBuilder()
    var pendingLine = 1
    fun flush() {
      val parent = stack.lastOrNull()
      val text = pending.toString().trim()
      if (text.isNotEmpty() && parent != null) {
        builder.node(DataKind.Text, null, text.take(DataJsonParser.MaxValueChars), pendingLine, stack.size)?.let { parent.children!!.add(it) }
      }
      pending.setLength(0)
    }
    try {
      parser.setInput(input, null)
      var event = parser.eventType
      while (event != XmlPullParser.END_DOCUMENT && !builder.full) {
        when (event) {
          XmlPullParser.START_DOCUMENT -> {
            parser.inputEncoding?.let { prolog["encoding"] = it }
          }
          XmlPullParser.DOCDECL -> prolog["doctype"] = parser.text?.trim().orEmpty()
          XmlPullParser.START_TAG -> {
            flush()
            val depth = stack.size
            // R2: lo stesso tetto del JSON ([DataProfile.MaxTreeDepth]). Oltre, un errore con la riga e quanto letto fin
            // qui, non uno stack esaurito più avanti: il plist, «Testo» e le righe dell'albero ricorrono sui nodi.
            if (depth > DataProfile.MaxTreeDepth) {
              error = DataTreeError(parser.lineNumber.coerceAtLeast(1), parser.columnNumber.coerceAtLeast(1),
                "annidato troppo in profondità (oltre ${DataProfile.MaxTreeDepth} livelli)")
              break
            }
            val node = builder.node(DataKind.Element, parser.name, null, parser.lineNumber, depth) ?: break
            for (i in 0 until parser.attributeCount) {
              val name = parser.getAttributeName(i)
              if (name == "xmlns" || name.startsWith("xmlns:")) namespaces++
              builder.node(DataKind.Attribute, name, parser.getAttributeValue(i), parser.lineNumber, depth + 1)
                ?.let { node.children!!.add(it) }
            }
            val parent = stack.lastOrNull()
            if (parent == null) {
              if (root == null) root = node
            } else {
              parent.children!!.add(node)
            }
            stack += node
          }
          XmlPullParser.END_TAG -> {
            flush()
            val node = stack.removeAt(stack.lastIndex)
            // Un elemento con un solo testo, senza attributi ne' altri figli, si legge in una riga.
            val children = node.children!!
            val only = children.singleOrNull()
            if (only != null && only.kind == DataKind.Text) {
              node.value = only.value
              children.clear()
            }
          }
          XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.IGNORABLE_WHITESPACE -> {
            if (pending.isEmpty()) pendingLine = parser.lineNumber
            if (pending.length < DataJsonParser.MaxValueChars) pending.append(parser.text.orEmpty())
          }
          XmlPullParser.ENTITY_REF -> {
            if (pending.isEmpty()) pendingLine = parser.lineNumber
            pending.append(parser.text ?: "&${parser.name};")
          }
          XmlPullParser.COMMENT -> {
            flush()
            builder.stats.comments++
            val parent = stack.lastOrNull()
            if (parent != null) builder.node(DataKind.Comment, null, parser.text?.trim().orEmpty(), parser.lineNumber, stack.size)
              ?.let { parent.children!!.add(it) }
          }
          XmlPullParser.PROCESSING_INSTRUCTION -> {
            flush()
            val text = parser.text.orEmpty()
            if (text.startsWith("xml ") || text.startsWith("xml\t")) {
              Regex("""(version|encoding|standalone)\s*=\s*["']([^"']*)["']""").findAll(text).forEach { prolog[it.groupValues[1]] = it.groupValues[2] }
            } else {
              stack.lastOrNull()?.let { parent ->
                builder.node(DataKind.Instruction, null, text, parser.lineNumber, stack.size)?.let { parent.children!!.add(it) }
              }
            }
          }
        }
        event = parser.nextToken()
      }
    } catch (e: XmlPullParserException) {
      error = DataTreeError(e.lineNumber.coerceAtLeast(1), e.columnNumber.coerceAtLeast(1), xmlMessage(e))
    } catch (e: java.io.IOException) {
      error = DataTreeError(parser.lineNumber.coerceAtLeast(1), parser.columnNumber.coerceAtLeast(1), "il file non si legge fino in fondo (${e.message})")
    }
    stack.forEach { it.complete = false }
    if (namespaces > 0) prolog["namespaces"] = namespaces.toString()
    if (root == null && error == null) error = DataTreeError(1, 1, "il file non ha un elemento")
    val tree = DataTree(root, error, builder.full, builder.stats, "XML", prolog)
    return if (root?.name == "plist" && error == null) DataPlist.fromXml(tree, maxNodes) else tree
  }

  /** I messaggi di KXml sono in inglese e ripetono la posizione: si tiene il motivo, in italiano dove si riconosce. */
  internal fun xmlMessage(e: XmlPullParserException): String {
    val raw = e.message.orEmpty().substringBefore(" (position:").substringBefore("@").trim()
    val lower = raw.lowercase()
    return when {
      "expected:" in lower && "end_tag" in lower -> "tag chiuso nel posto sbagliato: $raw"
      "unexpected eof" in lower || "unexpected end" in lower -> "il file finisce prima di chiudere tutti gli elementi"
      "unresolved" in lower || "entity" in lower -> "entità sconosciuta: $raw"
      "attr" in lower -> "attributo non valido: $raw"
      raw.isEmpty() -> "XML non valido"
      else -> "XML non valido: $raw"
    }
  }
}

/** I plist di Apple: XML (`<plist>`) e binari (`bplist00`), nello stesso albero dei tipi. */
internal object DataPlist {

  /** Dall'albero XML di un `<plist>` all'albero dei tipi: `dict` → oggetto, `array` → elenco, `integer` → numero… */
  fun fromXml(xml: DataTree, maxNodes: Int): DataTree {
    val builder = DataTreeBuilder(maxNodes)
    val plist = xml.root ?: return xml
    val value = plist.children?.firstOrNull { it.kind == DataKind.Element }
    val root = value?.let { convert(it, null, 0, builder) }
    val prolog = xml.prolog + ("version" to (plist.children?.firstOrNull { it.kind == DataKind.Attribute && it.name == "version" }?.value ?: "1.0"))
    return DataTree(root, null, builder.full || xml.truncated, builder.stats, "Property list XML", prolog)
  }

  private fun convert(element: DataNode, name: String?, depth: Int, builder: DataTreeBuilder): DataNode? {
    val text = element.value.orEmpty()
    return when (element.name) {
      "dict" -> {
        val node = builder.node(DataKind.Object, name, null, element.line, depth) ?: return null
        val items = element.children.orEmpty().filter { it.kind == DataKind.Element }
        var i = 0
        while (i < items.size) {
          val key = items[i]
          if (key.name == "key" && i + 1 < items.size) {
            convert(items[i + 1], key.value.orEmpty(), depth + 1, builder)?.let { node.children!!.add(it) } ?: break
            i += 2
          } else {
            i++
          }
        }
        node
      }
      "array" -> {
        val node = builder.node(DataKind.Array, name, null, element.line, depth) ?: return null
        element.children.orEmpty().filter { it.kind == DataKind.Element }.forEachIndexed { index, child ->
          convert(child, index.toString(), depth + 1, builder)?.let { node.children!!.add(it) } ?: return node
        }
        node
      }
      "string" -> builder.node(DataKind.String, name, text, element.line, depth)
      "integer", "real" -> builder.node(DataKind.Number, name, text, element.line, depth)
      "true", "false" -> builder.node(DataKind.Bool, name, element.name, element.line, depth)
      "date" -> builder.node(DataKind.Date, name, text, element.line, depth)
      "data" -> builder.node(DataKind.Data, name, dataLabel(text.filterNot { it.isWhitespace() }), element.line, depth)
      else -> builder.node(DataKind.String, name, text, element.line, depth)
    }
  }

  private fun dataLabel(base64: String): String {
    val bytes = try {
      Base64.decode(base64, Base64.DEFAULT)
    } catch (_: Exception) {
      null
    }
    return if (bytes == null) "dati non validi" else dataLabel(bytes)
  }

  private fun dataLabel(bytes: ByteArray): String {
    val head = bytes.take(8).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
    return "${DataText.count(bytes.size)} byte" + if (head.isNotEmpty()) " · $head${if (bytes.size > 8) " …" else ""}" else ""
  }

  /** Vero se i byte sono un plist binario. */
  fun isBinary(bytes: ByteArray, length: Int): Boolean =
    length >= 8 && String(bytes, 0, 8, Charsets.US_ASCII) == "bplist00"

  /**
   * Un plist binario (`bplist00`): la coda dice la misura dei riferimenti e dove sta la tabella degli scostamenti; ogni
   * oggetto comincia con un byte di tipo. Gli oggetti già visti non si visitano due volte (un plist costruito apposta può
   * avere cicli).
   */
  fun parseBinary(bytes: ByteArray, length: Int, maxNodes: Int = DataProfile.MaxTreeNodes): DataTree {
    val builder = DataTreeBuilder(maxNodes)
    fun bad(message: String) = DataTree(null, DataTreeError(1, 1, message), false, builder.stats, "Property list binaria")
    if (!isBinary(bytes, length) || length < 40) return bad("non è un plist binario")
    val trailer = length - 32
    val offsetSize = bytes[trailer + 6].toInt() and 0xFF
    val refSize = bytes[trailer + 7].toInt() and 0xFF
    val count = readLong(bytes, trailer + 8, 8)
    val top = readLong(bytes, trailer + 16, 8)
    val tableStart = readLong(bytes, trailer + 24, 8)
    if (offsetSize !in 1..8 || refSize !in 1..8 || count <= 0 || count > Int.MAX_VALUE / 8 || top >= count ||
      tableStart < 8 || tableStart + count * offsetSize > trailer
    ) return bad("la coda del plist binario non è valida")
    fun offsetOf(ref: Long): Int {
      if (ref < 0 || ref >= count) throw IllegalStateException("riferimento fuori dalla tabella")
      val at = readLong(bytes, (tableStart + ref * offsetSize).toInt(), offsetSize)
      if (at < 8 || at >= trailer) throw IllegalStateException("oggetto fuori dal file")
      return at.toInt()
    }
    val visiting = HashSet<Long>()
    fun lengthAt(marker: Int, at: Int): Pair<Int, Int> {
      val low = marker and 0x0F
      if (low != 0x0F) return low to at + 1
      val intMarker = bytes[at + 1].toInt() and 0xFF
      val size = 1 shl (intMarker and 0x0F)
      return readLong(bytes, at + 2, size).toInt() to at + 2 + size
    }
    fun obj(ref: Long, name: String?, depth: Int): DataNode? {
      if (depth > DataProfile.MaxTreeDepth || !visiting.add(ref)) {
        return builder.node(DataKind.Null, name, "riferimento circolare", 1, depth)
      }
      try {
        val at = offsetOf(ref)
        val marker = bytes[at].toInt() and 0xFF
        return when (marker shr 4) {
          0x0 -> when (marker) {
            0x08 -> builder.node(DataKind.Bool, name, "false", 1, depth)
            0x09 -> builder.node(DataKind.Bool, name, "true", 1, depth)
            else -> builder.node(DataKind.Null, name, "null", 1, depth)
          }
          0x1 -> {
            val size = 1 shl (marker and 0x0F)
            val v = readLong(bytes, at + 1, size)
            builder.node(DataKind.Number, name, (if (size == 8) v else v).toString(), 1, depth)
          }
          0x2 -> {
            val size = 1 shl (marker and 0x0F)
            val bits = readLong(bytes, at + 1, size)
            val v = if (size == 4) java.lang.Float.intBitsToFloat(bits.toInt()).toDouble() else java.lang.Double.longBitsToDouble(bits)
            builder.node(DataKind.Number, name, v.toString(), 1, depth)
          }
          0x3 -> {
            val seconds = java.lang.Double.longBitsToDouble(readLong(bytes, at + 1, 8))
            // L'epoca di Apple è il 1° gennaio 2001.
            val instant = java.time.Instant.ofEpochSecond(978_307_200L + seconds.toLong())
            builder.node(DataKind.Date, name, instant.toString(), 1, depth)
          }
          0x4 -> {
            val (n, start) = lengthAt(marker, at)
            builder.node(DataKind.Data, name, dataLabel(bytes.copyOfRange(start, minOf(start + n, trailer))), 1, depth)
          }
          0x5 -> {
            val (n, start) = lengthAt(marker, at)
            builder.node(DataKind.String, name, String(bytes, start, n.coerceAtMost(trailer - start), Charsets.US_ASCII), 1, depth)
          }
          0x6 -> {
            val (n, start) = lengthAt(marker, at)
            builder.node(DataKind.String, name, String(bytes, start, (n * 2).coerceAtMost(trailer - start), Charsets.UTF_16BE), 1, depth)
          }
          0x8 -> builder.node(DataKind.Number, name, "UID ${readLong(bytes, at + 1, (marker and 0x0F) + 1)}", 1, depth)
          0xA, 0xC -> {
            val (n, start) = lengthAt(marker, at)
            val node = builder.node(DataKind.Array, name, null, 1, depth) ?: return null
            for (i in 0 until n) {
              val child = obj(readLong(bytes, start + i * refSize, refSize), i.toString(), depth + 1) ?: run { node.complete = false; return node }
              node.children!!.add(child)
            }
            node
          }
          0xD -> {
            val (n, start) = lengthAt(marker, at)
            val node = builder.node(DataKind.Object, name, null, 1, depth) ?: return null
            for (i in 0 until n) {
              val keyRef = readLong(bytes, start + i * refSize, refSize)
              val valueRef = readLong(bytes, start + (n + i) * refSize, refSize)
              val keyAt = offsetOf(keyRef)
              val keyMarker = bytes[keyAt].toInt() and 0xFF
              val (kn, ks) = lengthAt(keyMarker, keyAt)
              val key = if (keyMarker shr 4 == 0x6) String(bytes, ks, kn * 2, Charsets.UTF_16BE) else String(bytes, ks, kn, Charsets.US_ASCII)
              val child = obj(valueRef, key, depth + 1) ?: run { node.complete = false; return node }
              node.children!!.add(child)
            }
            node
          }
          else -> builder.node(DataKind.Null, name, "tipo sconosciuto 0x%02X".format(marker), 1, depth)
        }
      } finally {
        visiting.remove(ref)
      }
    }
    return try {
      val root = obj(top, null, 0)
      DataTree(root, null, builder.full, builder.stats, "Property list binaria")
    } catch (e: Exception) {
      bad("plist binario rotto: ${e.message ?: e.javaClass.simpleName}")
    }
  }

  private fun readLong(bytes: ByteArray, at: Int, size: Int): Long {
    if (at < 0 || at + size > bytes.size) throw IllegalStateException("lettura fuori dal file")
    var v = 0L
    for (i in 0 until size) v = (v shl 8) or (bytes[at + i].toLong() and 0xFF)
    return v
  }
}
