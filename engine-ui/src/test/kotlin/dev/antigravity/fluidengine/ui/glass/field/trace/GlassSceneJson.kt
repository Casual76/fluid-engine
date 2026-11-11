package dev.antigravity.fluidengine.ui.glass.field.trace

/**
 * Un lettore JSON, quanto basta per rileggere una scena del banco (i `.json` di `tools/glassbench/scenes/rif`).
 *
 * Serve allo scrittore delle scene (`GlassScenes.kt`): le inquadrature di Apple sono scene scritte a mano, con
 * il fondale, la pagina e le finestre di misura, e la scena del materiale nuovo è **la stessa** con ogni lastra
 * sostituita da ciò che dà la ricetta. Rileggerle è l'unico modo di non tenerne due copie. I test di
 * `engine-ui` non hanno una libreria JSON (sul JVM `org.json` di Android è un guscio vuoto), e per cento righe
 * non vale una dipendenza.
 *
 * Gli oggetti escono come `LinkedHashMap` (l'ordine dei campi è quello del file: la scena riscritta si
 * confronta a occhio con quella di partenza), le liste come `List`, gli interi come `Long`, gli altri numeri
 * come `Double`.
 */
internal object GlassSceneJson {

  fun parse(text: String): Any? {
    val reader = Reader(text)
    val value = reader.value()
    reader.blank()
    require(reader.at == text.length) { "JSON: testo in più dopo il valore, al carattere ${reader.at}" }
    return value
  }

  @Suppress("UNCHECKED_CAST")
  fun parseObject(text: String): LinkedHashMap<String, Any?> =
    parse(text) as? LinkedHashMap<String, Any?> ?: error("JSON: in testa mi aspetto un oggetto")

  private class Reader(val text: String) {
    var at = 0

    fun blank() {
      while (at < text.length && text[at].isWhitespace()) at++
    }

    fun value(): Any? {
      blank()
      require(at < text.length) { "JSON: finito prima di un valore" }
      return when (val c = text[at]) {
        '{' -> obj()
        '[' -> array()
        '"' -> string()
        't' -> word("true", true)
        'f' -> word("false", false)
        'n' -> word("null", null)
        else -> if (c == '-' || c.isDigit()) number() else fail("carattere inatteso `$c`")
      }
    }

    private fun fail(what: String): Nothing = error("JSON: $what al carattere $at")

    private fun word(literal: String, value: Any?): Any? {
      if (!text.startsWith(literal, at)) fail("mi aspettavo `$literal`")
      at += literal.length
      return value
    }

    private fun obj(): LinkedHashMap<String, Any?> {
      val out = LinkedHashMap<String, Any?>()
      at++
      blank()
      if (text.getOrNull(at) == '}') {
        at++
        return out
      }
      while (true) {
        blank()
        if (text.getOrNull(at) != '"') fail("mi aspettavo il nome di un campo")
        val key = string()
        blank()
        if (text.getOrNull(at) != ':') fail("mi aspettavo `:`")
        at++
        out[key] = value()
        blank()
        when (text.getOrNull(at)) {
          ',' -> at++
          '}' -> {
            at++
            return out
          }
          else -> fail("mi aspettavo `,` o `}`")
        }
      }
    }

    private fun array(): List<Any?> {
      val out = ArrayList<Any?>()
      at++
      blank()
      if (text.getOrNull(at) == ']') {
        at++
        return out
      }
      while (true) {
        out += value()
        blank()
        when (text.getOrNull(at)) {
          ',' -> at++
          ']' -> {
            at++
            return out
          }
          else -> fail("mi aspettavo `,` o `]`")
        }
      }
    }

    private fun string(): String {
      val out = StringBuilder()
      at++
      while (true) {
        if (at >= text.length) fail("stringa mai chiusa")
        val c = text[at++]
        when (c) {
          '"' -> return out.toString()
          '\\' -> {
            if (at >= text.length) fail("stringa mai chiusa")
            when (val e = text[at++]) {
              '"', '\\', '/' -> out.append(e)
              'n' -> out.append('\n')
              'r' -> out.append('\r')
              't' -> out.append('\t')
              'b' -> out.append('\b')
              'f' -> out.append('\u000C')
              'u' -> {
                if (at + 4 > text.length) fail("`\\u` incompleto")
                out.append(text.substring(at, at + 4).toInt(16).toChar())
                at += 4
              }
              else -> fail("sequenza `\\$e` sconosciuta")
            }
          }
          else -> out.append(c)
        }
      }
    }

    private fun number(): Any {
      val start = at
      if (text[at] == '-') at++
      while (at < text.length && (text[at].isDigit() || text[at] in ".eE+-")) at++
      val raw = text.substring(start, at)
      return if (raw.none { it == '.' || it == 'e' || it == 'E' }) raw.toLong() else raw.toDouble()
    }
  }
}
