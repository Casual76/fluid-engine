package dev.antigravity.fluidengine.viewer

import androidx.compose.runtime.Immutable
import java.util.Locale

/**
 * Il ruolo di un pezzo di codice, da cui il preset testo sceglie il colore col tema ([fluidSyntaxAnnotated]).
 *
 * Non è un `enum` di proposito (regola 3.11): un ruolo nuovo è una costante in più, non un `when` rotto in un'app.
 */
@Immutable
@JvmInline
value class FluidSyntaxRole private constructor(private val id: Int) {

  override fun toString(): String = Names.getOrElse(id) { "Unknown" }

  companion object {
    private val Names = listOf(
      "Keyword", "Type", "String", "Number", "Comment", "Tag", "Attribute", "Heading", "Emphasis", "Link", "Code",
    )

    /** Parole riservate, letterali come `true`, `null`; nel Markdown i segni degli elenchi. */
    val Keyword: FluidSyntaxRole = FluidSyntaxRole(0)
    /** Nomi di tipo (per convenzione con la maiuscola), le ancore e i tag di YAML. */
    val Type: FluidSyntaxRole = FluidSyntaxRole(1)
    /** Stringhe e caratteri, i valori fra virgolette. */
    val String: FluidSyntaxRole = FluidSyntaxRole(2)
    /** Numeri. */
    val Number: FluidSyntaxRole = FluidSyntaxRole(3)
    /** Commenti; nel Markdown le citazioni. */
    val Comment: FluidSyntaxRole = FluidSyntaxRole(4)
    /** I tag di XML e HTML, le sezioni `[…]` di INI e TOML. */
    val Tag: FluidSyntaxRole = FluidSyntaxRole(5)
    /** Attributi, chiavi di JSON e YAML, annotazioni (`@Composable`), direttive (`#include`). */
    val Attribute: FluidSyntaxRole = FluidSyntaxRole(6)
    /** I titoli del Markdown. */
    val Heading: FluidSyntaxRole = FluidSyntaxRole(7)
    /** Corsivo e grassetto del Markdown. */
    val Emphasis: FluidSyntaxRole = FluidSyntaxRole(8)
    /** I collegamenti del Markdown. */
    val Link: FluidSyntaxRole = FluidSyntaxRole(9)
    /** Il codice dentro il Markdown (in linea e nei blocchi). */
    val Code: FluidSyntaxRole = FluidSyntaxRole(10)
  }
}

/** Un pezzo colorato di una riga, da [start] incluso a [end] escluso, in unità UTF-16 come `String`. */
@Immutable
data class FluidSyntaxSpan(val start: Int, val end: Int, val role: FluidSyntaxRole)

/** I pezzi di una riga e lo stato con cui comincia la successiva (un commento a blocco aperto, una stringa lunga). */
@Immutable
data class FluidSyntaxLine(val spans: List<FluidSyntaxSpan>, val endState: Int)

/**
 * Colora una riga alla volta. Lo stato è un intero opaco: `0` è l'inizio del file, e lo stato con cui comincia una
 * riga è l'[FluidSyntaxLine.endState] della precedente. Così un file letto a pezzi si colora a pezzi, e chi tiene
 * gli stati all'inizio dei pezzi riparte da metà file senza ricolorare tutto.
 */
class FluidSyntaxHighlighter internal constructor(
  /** La lingua, una di [FluidSyntax.languages]. */
  val language: String,
  private val lexer: Lexer,
) {
  /** I pezzi colorati di [text], partendo da [state]. Una riga senza niente da colorare dà una lista vuota. */
  fun line(text: String, state: Int = 0): FluidSyntaxLine {
    val out = Spans()
    val end = lexer.line(text, state, out)
    return FluidSyntaxLine(out.list, end)
  }

  internal interface Lexer {
    fun line(text: String, state: Int, out: Spans): Int
  }

  internal class Spans {
    val list = ArrayList<FluidSyntaxSpan>()
    fun add(start: Int, end: Int, role: FluidSyntaxRole) {
      if (end > start) list += FluidSyntaxSpan(start, end, role)
    }
  }
}

/**
 * I colori di sintassi per poche lingue, senza librerie: lessici scritti qui, una riga alla volta, che riconoscono
 * parole riservate, stringhe, numeri, commenti (anche a blocco su più righe), tag, attributi e i segni del Markdown.
 * Non sono parser: un costrutto che non riconoscono resta del colore del testo, mai un errore.
 *
 * Le lingue: Kotlin, Java, Groovy (anche `.gradle`), JavaScript, TypeScript, C, C++, C#, Swift, Go, Rust, Dart,
 * Python, JSON, XML, HTML, Markdown, YAML, INI (anche `.properties`, TOML, `.env`). Una lingua che non c'è dà
 * `null`: il testo resta semplice, ed è il ripiego giusto.
 */
object FluidSyntax {
  const val Kotlin = "kotlin"
  const val Java = "java"
  const val Groovy = "groovy"
  const val JavaScript = "javascript"
  const val TypeScript = "typescript"
  const val C = "c"
  const val Cpp = "cpp"
  const val CSharp = "csharp"
  const val Swift = "swift"
  const val Go = "go"
  const val Rust = "rust"
  const val Dart = "dart"
  const val Python = "python"
  const val Json = "json"
  const val Xml = "xml"
  const val Html = "html"
  const val Markdown = "markdown"
  const val Yaml = "yaml"
  const val Ini = "ini"

  private val ByExtension = mapOf(
    "kt" to Kotlin, "kts" to Kotlin, "java" to Java, "gradle" to Groovy, "groovy" to Groovy,
    "js" to JavaScript, "mjs" to JavaScript, "cjs" to JavaScript, "jsx" to JavaScript,
    "ts" to TypeScript, "tsx" to TypeScript, "c" to C, "h" to C, "cpp" to Cpp, "cc" to Cpp, "cxx" to Cpp,
    "hpp" to Cpp, "hh" to Cpp, "cs" to CSharp, "swift" to Swift, "go" to Go, "rs" to Rust, "dart" to Dart,
    "py" to Python, "pyw" to Python, "json" to Json, "jsonc" to Json, "json5" to Json, "geojson" to Json,
    "webmanifest" to Json, "xml" to Xml, "xsd" to Xml, "xsl" to Xml, "xslt" to Xml, "plist" to Xml, "kml" to Xml,
    "gpx" to Xml, "rss" to Xml, "atom" to Xml, "html" to Html, "htm" to Html, "xhtml" to Html,
    "md" to Markdown, "markdown" to Markdown, "mdown" to Markdown, "yaml" to Yaml, "yml" to Yaml,
    "ini" to Ini, "cfg" to Ini, "conf" to Ini, "properties" to Ini, "toml" to Ini, "env" to Ini,
    "editorconfig" to Ini, "gitconfig" to Ini,
  )

  private val ByMime = mapOf(
    "text/x-kotlin" to Kotlin, "text/x-java" to Java, "text/x-java-source" to Java, "text/javascript" to JavaScript,
    "application/javascript" to JavaScript, "application/x-javascript" to JavaScript,
    "application/ecmascript" to JavaScript, "text/x-python" to Python, "application/json" to Json,
    "application/xml" to Xml, "text/xml" to Xml, "text/html" to Html, "application/xhtml+xml" to Html,
    "text/markdown" to Markdown, "text/x-markdown" to Markdown, "application/x-yaml" to Yaml,
    "application/yaml" to Yaml, "text/yaml" to Yaml, "text/x-yaml" to Yaml, "application/toml" to Ini,
    "text/x-c" to C, "text/x-c++" to Cpp, "text/x-csharp" to CSharp, "text/x-swift" to Swift, "text/x-go" to Go,
    "text/x-rust" to Rust,
  )

  /** Le lingue che sa colorare. */
  val languages: Set<String> = (ByExtension.values + ByMime.values).toSet()

  /** La lingua di un file dal nome (l'ultima estensione) e, se il nome tace, dal MIME; `null` se non la conosce. */
  fun languageFor(fileName: String, mimeType: String? = null): String? {
    val leaf = fileName.substringAfterLast('/').substringAfterLast('\\')
    val extension = FluidViewerType.extensionOf(leaf).ifEmpty {
      // `.editorconfig`, `.env`: il nome intero fa da estensione.
      leaf.removePrefix(".").lowercase(Locale.ROOT).takeIf { leaf.startsWith('.') }.orEmpty()
    }
    ByExtension[extension]?.let { return it }
    val mime = FluidViewerType.normalizeMime(mimeType) ?: return null
    return ByMime[mime] ?: when {
      mime.endsWith("+json") -> Json
      mime.endsWith("+xml") -> Xml
      else -> null
    }
  }

  /**
   * La lingua dal contenuto, per un testo senza nome utile: un oggetto o una lista JSON, un documento XML o HTML,
   * uno script Python con lo shebang. Prudente: nel dubbio `null`.
   */
  fun guessLanguage(sample: String): String? {
    val text = sample.trimStart('﻿', ' ', '\t', '\r', '\n')
    val firstLine = text.lineSequence().firstOrNull().orEmpty()
    return when {
      firstLine.startsWith("#!") && "python" in firstLine -> Python
      text.startsWith("<?xml") -> Xml
      text.regionMatches(0, "<!doctype html", 0, 14, ignoreCase = true) ||
        text.regionMatches(0, "<html", 0, 5, ignoreCase = true) -> Html
      (text.startsWith("{") || text.startsWith("[")) && looksLikeJson(text) -> Json
      else -> null
    }
  }

  private fun looksLikeJson(text: String): Boolean {
    val second = text.drop(1).trimStart().firstOrNull() ?: return false
    return second == '"' || second == '}' || second == ']' || second == '{' || second == '[' ||
      second.isDigit() || second == '-' || text.startsWith("[t") || text.startsWith("[f") || text.startsWith("[n")
  }

  /** Il colorante di una lingua, o `null` se la lingua è `null` o sconosciuta. */
  fun highlighter(language: String?): FluidSyntaxHighlighter? {
    val lexer: FluidSyntaxHighlighter.Lexer = when (language) {
      Kotlin -> CLike(KotlinWords, tripleQuote = true, annotations = true, capitalTypes = true)
      Java -> CLike(JavaWords, annotations = true, capitalTypes = true)
      Groovy -> CLike(JavaWords + GroovyWords, tripleQuote = true, annotations = true, capitalTypes = true)
      JavaScript -> CLike(JsWords, backtick = true, annotations = true, capitalTypes = true)
      TypeScript -> CLike(JsWords + TsWords, backtick = true, annotations = true, capitalTypes = true)
      C -> CLike(CWords, preprocessor = true)
      Cpp -> CLike(CWords + CppWords, preprocessor = true, capitalTypes = true)
      CSharp -> CLike(CSharpWords, preprocessor = true, capitalTypes = true)
      Swift -> CLike(SwiftWords, tripleQuote = true, annotations = true, capitalTypes = true)
      Go -> CLike(GoWords, backtick = true)
      Rust -> CLike(RustWords, preprocessor = true, capitalTypes = true, apostropheChars = false)
      Dart -> CLike(DartWords, tripleQuote = true, annotations = true, capitalTypes = true)
      Python -> PythonLexer
      Json -> JsonLexer
      Xml, Html -> XmlLexer
      Markdown -> MarkdownLexer
      Yaml -> YamlLexer
      Ini -> IniLexer
      else -> return null
    }
    return FluidSyntaxHighlighter(language!!, lexer)
  }

  /** Colora righe consecutive di un file, dalla prima. Per un file letto a pezzi usare [highlighter] e gli stati. */
  fun highlight(lines: List<String>, language: String?): List<List<FluidSyntaxSpan>> {
    val highlighter = highlighter(language) ?: return lines.map { emptyList() }
    var state = 0
    return lines.map { line -> highlighter.line(line, state).also { state = it.endState }.spans }
  }

  // --- Parole ----------------------------------------------------------------------------------------------------

  private fun words(text: String): Set<String> = text.trim().split(Regex("\\s+")).toSet()

  private val KotlinWords = words(
    """as break class continue do else false for fun if in interface is null object package return super this
    throw true try typealias typeof val var when while by catch constructor delegate dynamic field file finally get
    import init param property receiver set setparam where actual abstract annotation companion const crossinline
    data enum expect external final infix inline inner internal lateinit noinline open operator out override private
    protected public reified sealed suspend tailrec vararg value""",
  )
  private val JavaWords = words(
    """abstract assert boolean break byte case catch char class const continue default do double else enum extends
    final finally float for goto if implements import instanceof int interface long native new package private
    protected public return short static strictfp super switch synchronized this throw throws transient try void
    volatile while true false null var record yield sealed permits""",
  )
  private val GroovyWords = words("def as in trait it")
  private val JsWords = words(
    """break case catch class const continue debugger default delete do else export extends finally for function if
    import in instanceof new return super switch this throw try typeof var void while with yield let static async
    await of get set true false null undefined NaN Infinity""",
  )
  private val TsWords = words(
    """type interface enum implements private protected public readonly declare namespace module abstract as is
    keyof infer never unknown any number string boolean symbol bigint object satisfies override""",
  )
  private val CWords = words(
    """auto break case char const continue default do double else enum extern float for goto if inline int long
    register restrict return short signed sizeof static struct switch typedef union unsigned void volatile while
    bool true false NULL""",
  )
  private val CppWords = words(
    """class namespace template typename public private protected virtual override final new delete this operator
    friend using try catch throw nullptr constexpr noexcept decltype static_cast dynamic_cast reinterpret_cast
    const_cast explicit mutable concept requires co_await co_return co_yield""",
  )
  private val CSharpWords = words(
    """abstract as base bool break byte case catch char checked class const continue decimal default delegate do
    double else enum event explicit extern false finally fixed float for foreach goto if implicit in int interface
    internal is lock long namespace new null object operator out override params private protected public readonly
    ref return sbyte sealed short sizeof stackalloc static string struct switch this throw true try typeof uint
    ulong unchecked unsafe ushort using virtual void volatile while var async await record init get set value
    yield""",
  )
  private val SwiftWords = words(
    """associatedtype class deinit enum extension fileprivate func import init inout internal let open operator
    private protocol public rethrows static struct subscript typealias var break case continue default defer do
    else fallthrough for guard if in repeat return switch where while as catch false is nil super self Self throw
    throws true try async await actor some any mutating nonmutating override final lazy weak unowned convenience
    required dynamic optional indirect""",
  )
  private val GoWords = words(
    """break case chan const continue default defer else fallthrough for func go goto if import interface map
    package range return select struct switch type var true false nil iota""",
  )
  private val RustWords = words(
    """as async await break const continue crate dyn else enum extern false fn for if impl in let loop match mod
    move mut pub ref return self Self static struct super trait true type unsafe use where while""",
  )
  private val DartWords = words(
    """abstract as assert async await break case catch class const continue covariant default deferred do dynamic
    else enum export extends extension external factory false final finally for get hide if implements import in
    interface is late library mixin new null on operator part required rethrow return set show static super
    switch sync this throw true try typedef var void while with yield""",
  )
  private val PythonWords = words(
    """False None True and as assert async await break class continue def del elif else except finally for from
    global if import in is lambda nonlocal not or pass raise return try while with yield match case self""",
  )

  // --- Lessici ---------------------------------------------------------------------------------------------------

  private fun isWordStart(c: Char) = c.isLetter() || c == '_' || c == '$'
  private fun isWordPart(c: Char) = c.isLetterOrDigit() || c == '_' || c == '$'

  /** Un numero da [i]: cifre, prefissi 0x/0b/0o, separatori `_`, decimali, esponente e suffissi. */
  private fun numberEnd(text: String, i: Int): Int {
    var j = i
    if (text.startsWith("0x", j, ignoreCase = true) || text.startsWith("0b", j, ignoreCase = true) ||
      text.startsWith("0o", j, ignoreCase = true)
    ) {
      j += 2
      while (j < text.length && (text[j].isLetterOrDigit() || text[j] == '_')) j++
      return j
    }
    while (j < text.length && (text[j].isDigit() || text[j] == '_' || text[j] == '.')) {
      if (text[j] == '.' && (j + 1 >= text.length || !text[j + 1].isDigit())) break
      j++
    }
    if (j < text.length && (text[j] == 'e' || text[j] == 'E')) {
      var k = j + 1
      if (k < text.length && (text[k] == '+' || text[k] == '-')) k++
      if (k < text.length && text[k].isDigit()) {
        j = k
        while (j < text.length && text[j].isDigit()) j++
      }
    }
    while (j < text.length && text[j].isLetter() && text[j].lowercaseChar() in "uldf") j++
    return j
  }

  /** La fine di una stringa che comincia in [i] con [quote], con `\` che salta il carattere dopo; -1 se non chiude. */
  private fun quotedEnd(text: String, i: Int, quote: Char): Int {
    var j = i + 1
    while (j < text.length) {
      when (text[j]) {
        '\\' -> j += 2
        quote -> return j + 1
        else -> j++
      }
    }
    return -1
  }

  private const val Normal = 0
  private const val InBlockComment = 1
  private const val InTripleQuote = 2
  private const val InBacktick = 3
  private const val InTripleApostrophe = 4

  private class CLike(
    val keywords: Set<String>,
    val tripleQuote: Boolean = false,
    val backtick: Boolean = false,
    val annotations: Boolean = false,
    val preprocessor: Boolean = false,
    val capitalTypes: Boolean = false,
    /** In Rust l'apostrofo apre anche le vite (`'a`): lì non è una stringa. */
    val apostropheChars: Boolean = true,
  ) : FluidSyntaxHighlighter.Lexer {
    override fun line(text: String, state: Int, out: FluidSyntaxHighlighter.Spans): Int {
      var i = 0
      when (state) {
        InBlockComment -> {
          val close = text.indexOf("*/")
          if (close < 0) { out.add(0, text.length, FluidSyntaxRole.Comment); return InBlockComment }
          out.add(0, close + 2, FluidSyntaxRole.Comment)
          i = close + 2
        }
        InTripleQuote -> {
          val close = text.indexOf("\"\"\"")
          if (close < 0) { out.add(0, text.length, FluidSyntaxRole.String); return InTripleQuote }
          out.add(0, close + 3, FluidSyntaxRole.String)
          i = close + 3
        }
        InBacktick -> {
          val close = closingBacktick(text, 0)
          if (close < 0) { out.add(0, text.length, FluidSyntaxRole.String); return InBacktick }
          out.add(0, close, FluidSyntaxRole.String)
          i = close
        }
      }
      while (i < text.length) {
        val c = text[i]
        when {
          text.startsWith("//", i) -> { out.add(i, text.length, FluidSyntaxRole.Comment); return Normal }
          text.startsWith("/*", i) -> {
            val close = text.indexOf("*/", i + 2)
            if (close < 0) { out.add(i, text.length, FluidSyntaxRole.Comment); return InBlockComment }
            out.add(i, close + 2, FluidSyntaxRole.Comment)
            i = close + 2
          }
          preprocessor && c == '#' && text.substring(0, i).isBlank() -> {
            out.add(i, text.length, FluidSyntaxRole.Attribute)
            return Normal
          }
          tripleQuote && text.startsWith("\"\"\"", i) -> {
            val close = text.indexOf("\"\"\"", i + 3)
            if (close < 0) { out.add(i, text.length, FluidSyntaxRole.String); return InTripleQuote }
            out.add(i, close + 3, FluidSyntaxRole.String)
            i = close + 3
          }
          c == '"' || c == '\'' && apostropheChars -> {
            val end = quotedEnd(text, i, c)
            val stop = if (end < 0) text.length else end
            out.add(i, stop, FluidSyntaxRole.String)
            i = stop
          }
          backtick && c == '`' -> {
            val close = closingBacktick(text, i + 1)
            if (close < 0) { out.add(i, text.length, FluidSyntaxRole.String); return InBacktick }
            out.add(i, close, FluidSyntaxRole.String)
            i = close
          }
          annotations && c == '@' && i + 1 < text.length && isWordStart(text[i + 1]) -> {
            var j = i + 1
            while (j < text.length && (isWordPart(text[j]) || text[j] == '.')) j++
            out.add(i, j, FluidSyntaxRole.Attribute)
            i = j
          }
          c.isDigit() && (i == 0 || !isWordPart(text[i - 1])) -> {
            val end = numberEnd(text, i)
            out.add(i, end, FluidSyntaxRole.Number)
            i = end
          }
          isWordStart(c) -> {
            var j = i + 1
            while (j < text.length && isWordPart(text[j])) j++
            val word = text.substring(i, j)
            when {
              word in keywords -> out.add(i, j, FluidSyntaxRole.Keyword)
              capitalTypes && word[0].isUpperCase() && word.any { it.isLowerCase() } ->
                out.add(i, j, FluidSyntaxRole.Type)
            }
            i = j
          }
          else -> i++
        }
      }
      return Normal
    }

    /** L'indice dopo il backtick che chiude, da [from]; -1 se la stringa continua alla riga dopo. */
    private fun closingBacktick(text: String, from: Int): Int {
      var j = from
      while (j < text.length) {
        when (text[j]) {
          '\\' -> j += 2
          '`' -> return j + 1
          else -> j++
        }
      }
      return -1
    }
  }

  private object PythonLexer : FluidSyntaxHighlighter.Lexer {
    override fun line(text: String, state: Int, out: FluidSyntaxHighlighter.Spans): Int {
      var i = 0
      if (state == InTripleQuote || state == InTripleApostrophe) {
        val fence = if (state == InTripleQuote) "\"\"\"" else "'''"
        val close = text.indexOf(fence)
        if (close < 0) { out.add(0, text.length, FluidSyntaxRole.String); return state }
        out.add(0, close + 3, FluidSyntaxRole.String)
        i = close + 3
      }
      while (i < text.length) {
        val c = text[i]
        when {
          c == '#' -> { out.add(i, text.length, FluidSyntaxRole.Comment); return Normal }
          c == '"' || c == '\'' -> {
            val start = stringPrefixStart(text, i)
            val fence = "$c$c$c"
            if (text.startsWith(fence, i)) {
              val close = text.indexOf(fence, i + 3)
              val next = if (c == '"') InTripleQuote else InTripleApostrophe
              if (close < 0) { out.add(start, text.length, FluidSyntaxRole.String); return next }
              out.add(start, close + 3, FluidSyntaxRole.String)
              i = close + 3
            } else {
              val end = quotedEnd(text, i, c)
              val stop = if (end < 0) text.length else end
              out.add(start, stop, FluidSyntaxRole.String)
              i = stop
            }
          }
          c == '@' && (i == 0 || text.substring(0, i).isBlank()) -> {
            var j = i + 1
            while (j < text.length && (isWordPart(text[j]) || text[j] == '.')) j++
            out.add(i, j, FluidSyntaxRole.Attribute)
            i = j
          }
          c.isDigit() && (i == 0 || !isWordPart(text[i - 1])) -> {
            val end = numberEnd(text, i)
            out.add(i, end, FluidSyntaxRole.Number)
            i = end
          }
          isWordStart(c) -> {
            var j = i + 1
            while (j < text.length && isWordPart(text[j])) j++
            // `r"…"`, `f'…'`, `rb"…"`: il prefisso fa parte della stringa, che lo prende da sé.
            if (j < text.length && (text[j] == '"' || text[j] == '\'') && j - i <= 2 &&
              text.substring(i, j).all { it.lowercaseChar() in "rbfu" }
            ) {
              i = j
              continue
            }
            val word = text.substring(i, j)
            when {
              word in PythonWords -> out.add(i, j, FluidSyntaxRole.Keyword)
              word[0].isUpperCase() && word.any { it.isLowerCase() } -> out.add(i, j, FluidSyntaxRole.Type)
            }
            i = j
          }
          else -> i++
        }
      }
      return Normal
    }

    private fun stringPrefixStart(text: String, quote: Int): Int {
      var j = quote
      while (j > 0 && quote - j < 2 && text[j - 1].lowercaseChar() in "rbfu") j--
      return if (j > 0 && isWordPart(text[j - 1])) quote else j
    }
  }

  private object JsonLexer : FluidSyntaxHighlighter.Lexer {
    private val Literals = setOf("true", "false", "null")

    override fun line(text: String, state: Int, out: FluidSyntaxHighlighter.Spans): Int {
      var i = 0
      if (state == InBlockComment) {
        val close = text.indexOf("*/")
        if (close < 0) { out.add(0, text.length, FluidSyntaxRole.Comment); return InBlockComment }
        out.add(0, close + 2, FluidSyntaxRole.Comment)
        i = close + 2
      }
      while (i < text.length) {
        val c = text[i]
        when {
          text.startsWith("//", i) -> { out.add(i, text.length, FluidSyntaxRole.Comment); return Normal }
          text.startsWith("/*", i) -> {
            val close = text.indexOf("*/", i + 2)
            if (close < 0) { out.add(i, text.length, FluidSyntaxRole.Comment); return InBlockComment }
            out.add(i, close + 2, FluidSyntaxRole.Comment)
            i = close + 2
          }
          c == '"' -> {
            val end = quotedEnd(text, i, '"')
            val stop = if (end < 0) text.length else end
            var k = stop
            while (k < text.length && text[k].isWhitespace()) k++
            out.add(i, stop, if (k < text.length && text[k] == ':') FluidSyntaxRole.Attribute else FluidSyntaxRole.String)
            i = stop
          }
          c == '-' || c.isDigit() -> {
            val from = if (c == '-') i + 1 else i
            if (from < text.length && text[from].isDigit()) {
              val end = numberEnd(text, from)
              out.add(i, end, FluidSyntaxRole.Number)
              i = end
            } else {
              i++
            }
          }
          c.isLetter() -> {
            var j = i + 1
            while (j < text.length && text[j].isLetter()) j++
            if (text.substring(i, j) in Literals) out.add(i, j, FluidSyntaxRole.Keyword)
            i = j
          }
          else -> i++
        }
      }
      return Normal
    }
  }

  private const val InXmlTag = 5
  private const val InCData = 6

  private object XmlLexer : FluidSyntaxHighlighter.Lexer {
    override fun line(text: String, state: Int, out: FluidSyntaxHighlighter.Spans): Int {
      var i = 0
      var current = state
      while (i < text.length) {
        when (current) {
          InBlockComment -> {
            val close = text.indexOf("-->", i)
            if (close < 0) { out.add(i, text.length, FluidSyntaxRole.Comment); return InBlockComment }
            out.add(i, close + 3, FluidSyntaxRole.Comment)
            i = close + 3
            current = Normal
          }
          InCData -> {
            val close = text.indexOf("]]>", i)
            if (close < 0) { out.add(i, text.length, FluidSyntaxRole.String); return InCData }
            out.add(i, close + 3, FluidSyntaxRole.String)
            i = close + 3
            current = Normal
          }
          InXmlTag -> {
            val c = text[i]
            when {
              c == '>' -> { out.add(i, i + 1, FluidSyntaxRole.Tag); i++; current = Normal }
              text.startsWith("/>", i) || text.startsWith("?>", i) -> {
                out.add(i, i + 2, FluidSyntaxRole.Tag); i += 2; current = Normal
              }
              c == '"' || c == '\'' -> {
                val close = text.indexOf(c, i + 1)
                val stop = if (close < 0) text.length else close + 1
                out.add(i, stop, FluidSyntaxRole.String)
                i = stop
              }
              isWordStart(c) || c == ':' -> {
                var j = i + 1
                while (j < text.length && (isWordPart(text[j]) || text[j] in ":-.")) j++
                out.add(i, j, FluidSyntaxRole.Attribute)
                i = j
              }
              else -> i++
            }
          }
          else -> {
            val c = text[i]
            when {
              text.startsWith("<!--", i) -> {
                out.add(i, i + 4, FluidSyntaxRole.Comment)
                i += 4
                current = InBlockComment
              }
              text.startsWith("<![CDATA[", i) -> {
                out.add(i, i + 9, FluidSyntaxRole.String)
                i += 9
                current = InCData
              }
              text.startsWith("<!", i) || text.startsWith("<?", i) -> {
                var j = i + 2
                while (j < text.length && isWordPart(text[j])) j++
                out.add(i, j, FluidSyntaxRole.Keyword)
                i = j
                current = InXmlTag
              }
              c == '<' && i + 1 < text.length && (isWordStart(text[i + 1]) || text[i + 1] == '/') -> {
                var j = i + 1
                if (text[j] == '/') j++
                while (j < text.length && (isWordPart(text[j]) || text[j] in ":-.")) j++
                out.add(i, j, FluidSyntaxRole.Tag)
                i = j
                current = InXmlTag
              }
              c == '&' -> {
                val semi = text.indexOf(';', i + 1)
                if (semi in (i + 2)..(i + 10) && text.substring(i + 1, semi).all { it.isLetterOrDigit() || it == '#' }) {
                  out.add(i, semi + 1, FluidSyntaxRole.Keyword)
                  i = semi + 1
                } else {
                  i++
                }
              }
              else -> i++
            }
          }
        }
      }
      return current
    }
  }

  private const val InFence = 7
  private val FenceRegex = Regex("^ {0,3}(```|~~~)")
  private val HeadingRegex = Regex("^ {0,3}#{1,6}(\\s|$)")
  private val QuoteRegex = Regex("^ {0,3}>")
  private val ListRegex = Regex("^\\s*([-*+]|\\d{1,9}[.)])\\s")
  private val RuleRegex = Regex("^ {0,3}([-*_])(\\s*\\1){2,}\\s*$")
  private val LinkRegex = Regex("!?\\[[^\\]]*]\\([^)\\s]*(\\s+\"[^\"]*\")?\\)|<https?://[^>\\s]+>")
  private val StrongRegex = Regex("(\\*\\*|__)(?=\\S)(.+?)(?<=\\S)\\1")
  private val EmRegex = Regex("(?<![*_\\w])([*_])(?=\\S)(.+?)(?<=\\S)\\1(?![*_\\w])")

  private object MarkdownLexer : FluidSyntaxHighlighter.Lexer {
    override fun line(text: String, state: Int, out: FluidSyntaxHighlighter.Spans): Int {
      if (FenceRegex.containsMatchIn(text)) {
        out.add(0, text.length, FluidSyntaxRole.Code)
        return if (state == InFence) Normal else InFence
      }
      if (state == InFence) { out.add(0, text.length, FluidSyntaxRole.Code); return InFence }
      when {
        HeadingRegex.containsMatchIn(text) -> { out.add(0, text.length, FluidSyntaxRole.Heading); return Normal }
        QuoteRegex.containsMatchIn(text) -> { out.add(0, text.length, FluidSyntaxRole.Comment); return Normal }
        RuleRegex.matches(text) -> { out.add(0, text.length, FluidSyntaxRole.Keyword); return Normal }
      }
      ListRegex.find(text)?.let { out.add(it.groups[1]!!.range.first, it.groups[1]!!.range.last + 1, FluidSyntaxRole.Keyword) }
      // Il codice in linea prima di tutto: dentro i backtick non c'è enfasi né collegamento.
      val taken = BooleanArray(text.length)
      var i = 0
      while (i < text.length) {
        if (text[i] == '`') {
          var ticks = 1
          while (i + ticks < text.length && text[i + ticks] == '`') ticks++
          val fence = "`".repeat(ticks)
          val close = text.indexOf(fence, i + ticks)
          if (close >= 0) {
            out.add(i, close + ticks, FluidSyntaxRole.Code)
            for (k in i until close + ticks) taken[k] = true
            i = close + ticks
            continue
          }
          i += ticks
        } else {
          i++
        }
      }
      fun mark(regex: Regex, role: FluidSyntaxRole) {
        for (m in regex.findAll(text)) {
          if (m.range.any { taken[it] }) continue
          out.add(m.range.first, m.range.last + 1, role)
          for (k in m.range) taken[k] = true
        }
      }
      mark(LinkRegex, FluidSyntaxRole.Link)
      mark(StrongRegex, FluidSyntaxRole.Emphasis)
      mark(EmRegex, FluidSyntaxRole.Emphasis)
      out.list.sortBy { it.start }
      return Normal
    }
  }

  private val YamlLiterals = setOf("true", "false", "null", "yes", "no", "on", "off", "~")
  private val YamlKeyRegex = Regex("^(\\s*(?:-\\s+)*)((?:\"[^\"]*\"|'[^']*'|[^\\s#:'\"][^:#]*?))\\s*:(\\s|$)")

  private object YamlLexer : FluidSyntaxHighlighter.Lexer {
    override fun line(text: String, state: Int, out: FluidSyntaxHighlighter.Spans): Int {
      val trimmed = text.trim()
      if (trimmed == "---" || trimmed == "...") { out.add(0, text.length, FluidSyntaxRole.Keyword); return Normal }
      var i = 0
      val key = YamlKeyRegex.find(text)
      if (key != null) {
        val dash = key.groups[1]!!.range
        if (!dash.isEmpty()) markDashes(text, dash.first, dash.last + 1, out)
        val name = key.groups[2]!!.range
        out.add(name.first, name.last + 1, FluidSyntaxRole.Attribute)
        i = key.range.last + 1 // dopo i due punti e lo spazio
      } else {
        val lead = text.length - text.trimStart().length
        var j = lead
        while (j < text.length && text[j] == '-' && (j + 1 == text.length || text[j + 1] == ' ')) {
          out.add(j, j + 1, FluidSyntaxRole.Keyword)
          j += 1
          while (j < text.length && text[j] == ' ') j++
        }
        i = j
      }
      value(text, i, out)
      return Normal
    }

    private fun markDashes(text: String, from: Int, to: Int, out: FluidSyntaxHighlighter.Spans) {
      for (k in from until to) if (text[k] == '-') out.add(k, k + 1, FluidSyntaxRole.Keyword)
    }

    private fun value(text: String, from: Int, out: FluidSyntaxHighlighter.Spans) {
      var i = from
      while (i < text.length) {
        val c = text[i]
        when {
          c == '#' && (i == 0 || text[i - 1].isWhitespace()) -> { out.add(i, text.length, FluidSyntaxRole.Comment); return }
          c == '"' || c == '\'' -> {
            val end = if (c == '"') quotedEnd(text, i, c) else text.indexOf('\'', i + 1).let { if (it < 0) -1 else it + 1 }
            val stop = if (end < 0) text.length else end
            out.add(i, stop, FluidSyntaxRole.String)
            i = stop
          }
          (c == '&' || c == '*' || c == '!') && i + 1 < text.length && !text[i + 1].isWhitespace() -> {
            var j = i + 1
            while (j < text.length && !text[j].isWhitespace()) j++
            out.add(i, j, FluidSyntaxRole.Type)
            i = j
          }
          c.isWhitespace() || c in ",[]{}" -> i++
          else -> {
            // Uno scalare semplice fino alla virgola, alla parentesi o al commento: numero, letterale o testo.
            var j = i
            while (j < text.length && text[j] !in ",[]{}" && !(text[j] == '#' && text[j - 1].isWhitespace())) j++
            val word = text.substring(i, j).trimEnd()
            val stop = i + word.length
            when {
              word.lowercase(Locale.ROOT) in YamlLiterals -> out.add(i, stop, FluidSyntaxRole.Keyword)
              word.toDoubleOrNull() != null -> out.add(i, stop, FluidSyntaxRole.Number)
            }
            i = maxOf(j, i + 1)
          }
        }
      }
    }
  }

  private val IniLiterals = setOf("true", "false", "yes", "no", "on", "off")

  private object IniLexer : FluidSyntaxHighlighter.Lexer {
    override fun line(text: String, state: Int, out: FluidSyntaxHighlighter.Spans): Int {
      val lead = text.length - text.trimStart().length
      if (lead == text.length) return Normal
      val first = text[lead]
      if (first == '#' || first == ';' || first == '!') { out.add(lead, text.length, FluidSyntaxRole.Comment); return Normal }
      if (first == '[') {
        val close = text.lastIndexOf(']')
        out.add(lead, if (close > lead) close + 1 else text.length, FluidSyntaxRole.Tag)
        return Normal
      }
      val sep = text.indexOfFirst { it == '=' || it == ':' }
      if (sep <= lead) return Normal
      out.add(lead, text.substring(0, sep).trimEnd().length, FluidSyntaxRole.Attribute)
      var i = sep + 1
      while (i < text.length && text[i].isWhitespace()) i++
      if (i >= text.length) return Normal
      val c = text[i]
      val rest = text.substring(i).trimEnd()
      when {
        c == '"' || c == '\'' -> {
          val end = quotedEnd(text, i, c)
          out.add(i, if (end < 0) text.length else end, FluidSyntaxRole.String)
        }
        rest.lowercase(Locale.ROOT) in IniLiterals -> out.add(i, i + rest.length, FluidSyntaxRole.Keyword)
        rest.replace("_", "").toDoubleOrNull() != null -> out.add(i, i + rest.length, FluidSyntaxRole.Number)
      }
      return Normal
    }
  }
}
