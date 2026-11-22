package dev.antigravity.fluidengine.viewer.data

import java.io.Reader
import java.io.StringReader

/**
 * F6C.13 · Il lettore JSON della famiglia, a flusso, in Kotlin puro. Non è `android.util.JsonReader`, di proposito:
 * - dice **la riga e la colonna** esatte di un errore, con il motivo in italiano;
 * - accetta i commenti (`//` e `/* */`) e le virgole finali di **JSONC** (i file di impostazioni di VS Code, i
 *   `tsconfig`), e li conta per il pannello info;
 * - si ferma al tetto dei nodi ([DataProfile.MaxTreeNodes]) senza leggere il resto del file;
 * - l'annidamento ha un tetto ([DataProfile.MaxTreeDepth]): un file di centomila «[» è un errore, non uno stack esaurito;
 * - gira sulla JVM, quindi si prova senza dispositivo.
 *
 * L'albero letto fino all'errore resta: la pagina lo mostra sotto l'avviso.
 */
internal class DataJsonParser private constructor(
  private val reader: Reader,
  private val builder: DataTreeBuilder,
  private val firstLine: Int,
) {
  private val buffer = CharArray(16 * 1024)
  private var length = 0
  private var position = 0
  private var line = firstLine
  private var column = 1
  private val open = ArrayList<DataNode>()

  private class Fail(val error: DataTreeError) : Exception(null, null, false, false)
  private class Full : Exception(null, null, false, false)

  private fun peek(): Int {
    if (position >= length) {
      length = reader.read(buffer, 0, buffer.size)
      position = 0
      if (length <= 0) {
        length = 0
        return -1
      }
    }
    return buffer[position].code
  }

  private fun next(): Int {
    val c = peek()
    if (c < 0) return c
    position++
    if (c == '\n'.code) {
      line++
      column = 1
    } else {
      column++
    }
    return c
  }

  private fun fail(message: String, atLine: Int = line, atColumn: Int = column): Nothing =
    throw Fail(DataTreeError(atLine, atColumn, message))

  private fun describe(c: Int): String = when {
    c < 0 -> "la fine del file"
    c == '\n'.code -> "un a capo"
    c < 0x20 -> "un carattere di controllo"
    else -> "«${c.toChar()}»"
  }

  /** Spazi, a capo e commenti; i commenti sono di JSONC e si contano. */
  private fun skipSpace() {
    while (true) {
      val c = peek()
      when {
        c == ' '.code || c == '\t'.code || c == '\n'.code || c == '\r'.code || c == 0xFEFF -> next()
        c == '/'.code -> {
          val l = line
          val col = column
          next()
          when (peek()) {
            '/'.code -> {
              while (peek() >= 0 && peek() != '\n'.code) next()
              builder.stats.comments++
            }
            '*'.code -> {
              next()
              var closed = false
              while (peek() >= 0) {
                if (next() == '*'.code && peek() == '/'.code) {
                  next()
                  closed = true
                  break
                }
              }
              if (!closed) fail("commento non chiuso", l, col)
              builder.stats.comments++
            }
            else -> fail("«/» da solo: un commento comincia con «//» o «/*»", l, col)
          }
        }
        else -> return
      }
    }
  }

  private fun value(name: String?, depth: Int, parent: DataNode?) {
    if (depth > DataProfile.MaxTreeDepth) fail("annidato troppo in profondità (oltre ${DataProfile.MaxTreeDepth} livelli)")
    skipSpace()
    val l = line
    val col = column
    when (val c = peek()) {
      '{'.code -> container(DataKind.Object, name, depth, parent, l)
      '['.code -> container(DataKind.Array, name, depth, parent, l)
      '"'.code -> add(DataKind.String, name, string(), l, depth, parent)
      '-'.code, in '0'.code..'9'.code -> add(DataKind.Number, name, number(), l, depth, parent)
      't'.code -> add(DataKind.Bool, name, literal("true"), l, depth, parent)
      'f'.code -> add(DataKind.Bool, name, literal("false"), l, depth, parent)
      'n'.code -> add(DataKind.Null, name, literal("null"), l, depth, parent)
      -1 -> fail("il file finisce dove serve un valore", l, col)
      '\''.code -> fail("le stringhe JSON vogliono le virgolette doppie", l, col)
      else -> fail("valore inatteso: ${describe(c)}", l, col)
    }
  }

  private fun add(kind: DataKind, name: String?, text: String, line: Int, depth: Int, parent: DataNode?) {
    val node = builder.node(kind, name, text, line, depth) ?: throw Full()
    parent?.children?.add(node) ?: run { root = node }
  }

  var root: DataNode? = null
    private set

  private fun container(kind: DataKind, name: String?, depth: Int, parent: DataNode?, startLine: Int) {
    val node = builder.node(kind, name, null, startLine, depth) ?: throw Full()
    parent?.children?.add(node) ?: run { root = node }
    open += node
    next() // { o [
    val close = if (kind == DataKind.Object) '}'.code else ']'.code
    var index = 0
    skipSpace()
    if (peek() == close) {
      next()
      open.removeAt(open.lastIndex)
      return
    }
    while (true) {
      skipSpace()
      if (kind == DataKind.Object) {
        val l = line
        val col = column
        val c = peek()
        if (c != '"'.code) {
          if (c == '\''.code) fail("le chiavi JSON vogliono le virgolette doppie", l, col)
          fail("attesa una chiave fra virgolette, trovato ${describe(c)}", l, col)
        }
        val key = string()
        skipSpace()
        val cl = line
        val cc = column
        val colon = next()
        if (colon != ':'.code) fail("atteso «:» dopo la chiave «${key.take(40)}», trovato ${describe(colon)}", cl, cc)
        value(key, depth + 1, node)
      } else {
        value(index.toString(), depth + 1, node)
      }
      index++
      skipSpace()
      val l = line
      val col = column
      val c = next()
      when (c) {
        ','.code -> {
          skipSpace()
          if (peek() == close) {
            // La virgola finale di JSONC.
            builder.stats.trailingCommas++
            next()
            break
          }
        }
        close -> break
        else -> fail("atteso «,» o «${close.toChar()}», trovato ${describe(c)}", l, col)
      }
    }
    open.removeAt(open.lastIndex)
  }

  private fun string(): String {
    val l = line
    val col = column
    next() // "
    val out = StringBuilder()
    while (true) {
      val cl = line
      val cc = column
      val c = next()
      when {
        c < 0 -> fail("stringa non chiusa", l, col)
        c == '"'.code -> break
        c == '\\'.code -> {
          val el = line
          val ec = column - 1
          when (val e = next()) {
            '"'.code -> out.append('"')
            '\\'.code -> out.append('\\')
            '/'.code -> out.append('/')
            'b'.code -> out.append('\b')
            'f'.code -> out.append('\u000C')
            'n'.code -> out.append('\n')
            'r'.code -> out.append('\r')
            't'.code -> out.append('\t')
            'u'.code -> {
              var code = 0
              repeat(4) {
                val h = next()
                val digit = Character.digit(h, 16)
                if (h < 0 || digit < 0) fail("«\\u» vuole quattro cifre esadecimali", el, ec)
                code = code * 16 + digit
              }
              out.append(code.toChar())
            }
            else -> fail("sequenza di escape non valida: «\\${if (e < 0) "" else e.toChar().toString()}»", el, ec)
          }
        }
        c < 0x20 -> fail(if (c == '\n'.code) "stringa non chiusa prima dell'a capo" else "carattere di controllo dentro una stringa", cl, cc)
        else -> if (out.length < MaxValueChars) out.append(c.toChar())
      }
    }
    return if (out.length >= MaxValueChars) out.append('…').toString() else out.toString()
  }

  private fun number(): String {
    val l = line
    val col = column
    val out = StringBuilder()
    fun digits(): Int {
      var n = 0
      while (peek() in '0'.code..'9'.code) {
        out.append(next().toChar())
        n++
      }
      return n
    }
    if (peek() == '-'.code) out.append(next().toChar())
    if (peek() == '0'.code) {
      out.append(next().toChar())
      if (peek() in '0'.code..'9'.code) fail("un numero JSON non comincia con 0 seguito da cifre", l, col)
    } else if (digits() == 0) {
      fail("numero non valido: «${out}»", l, col)
    }
    if (peek() == '.'.code) {
      out.append(next().toChar())
      if (digits() == 0) fail("dopo il punto decimale servono cifre", line, column)
    }
    if (peek() == 'e'.code || peek() == 'E'.code) {
      out.append(next().toChar())
      if (peek() == '+'.code || peek() == '-'.code) out.append(next().toChar())
      if (digits() == 0) fail("l'esponente vuole almeno una cifra", line, column)
    }
    val after = peek()
    if (after >= 0 && (Character.isLetterOrDigit(after) || after == '.'.code)) fail("numero non valido: ${describe(after)} dopo «$out»", line, column)
    return out.toString()
  }

  private fun literal(word: String): String {
    val l = line
    val col = column
    for (ch in word) {
      val c = next()
      if (c != ch.code) fail("valore inatteso: atteso «$word»", l, col)
    }
    val after = peek()
    if (after >= 0 && Character.isLetterOrDigit(after)) fail("valore inatteso dopo «$word»", line, column)
    return word
  }

  /** Un documento intero: un valore solo, poi solo spazi e commenti. */
  private fun document(): DataTreeError? = try {
    skipSpace()
    if (peek() < 0) fail(if (rootParent != null) "riga vuota" else "il file è vuoto")
    value(rootName, if (rootParent != null) 1 else 0, rootParent)
    skipSpace()
    val c = peek()
    if (c >= 0) fail("dopo il valore c'è dell'altro (${describe(c)}): un file con un valore per riga è NDJSON")
    null
  } catch (f: Fail) {
    open.forEach { it.complete = false }
    f.error
  } catch (_: Full) {
    open.forEach { it.complete = false }
    null
  }

  companion object {
    /** Il testo più lungo tenuto per un valore: il resto si legge ma non si conserva. */
    const val MaxValueChars = 4_000

    /** Un documento JSON o JSONC. */
    fun parse(reader: Reader, maxNodes: Int = DataProfile.MaxTreeNodes): DataTree {
      val builder = DataTreeBuilder(maxNodes)
      val parser = DataJsonParser(reader, builder, 1)
      val error = parser.document()
      val stats = builder.stats
      val format = if (stats.comments > 0 || stats.trailingCommas > 0) "JSONC" else "JSON"
      return DataTree(parser.root, error, builder.full, stats, format)
    }

    fun parse(text: String, maxNodes: Int = DataProfile.MaxTreeNodes): DataTree = parse(StringReader(text), maxNodes)

    /**
     * NDJSON (JSON Lines): un valore per riga. La radice è l'elenco delle righe; una riga non valida non ferma le altre,
     * e l'errore della prima resta con la sua riga e colonna.
     */
    fun parseLines(reader: Reader, maxNodes: Int = DataProfile.MaxTreeNodes): DataTree {
      val builder = DataTreeBuilder(maxNodes)
      val root = builder.node(DataKind.Array, null, null, 1, 0)!!
      var firstError: DataTreeError? = null
      var bad = 0
      var index = 0
      var number = 0
      val lines = reader.buffered()
      while (true) {
        val text = lines.readLine() ?: break
        number++
        if (text.isBlank()) continue
        val parser = DataJsonParser(StringReader(text), builder, number)
        parser.rootParent = root
        parser.rootName = index.toString()
        val before = root.childCount
        val error = parser.document()
        if (error != null) {
          bad++
          if (firstError == null) firstError = error
          // Una riga rotta a metà resta col pezzo letto (segnato incompleto); una che non ha dato niente ha il suo motivo.
          if (root.childCount == before) {
            builder.node(DataKind.Null, index.toString(), "riga non valida: ${error.message}", number, 1)?.let { root.children!!.add(it) }
          }
        }
        index++
        if (builder.full) {
          root.complete = false
          break
        }
      }
      return DataTree(root, firstError, builder.full, builder.stats, "NDJSON", mapOf("invalidLines" to bad.toString()))
    }
  }

  // Per NDJSON: ogni riga si attacca alla radice comune invece di diventare una radice sua.
  private var rootParent: DataNode? = null
  private var rootName: String? = null
}
