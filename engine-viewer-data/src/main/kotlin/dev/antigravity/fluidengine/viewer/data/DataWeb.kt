package dev.antigravity.fluidengine.viewer.data

import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.antigravity.fluidengine.ui.fluid.FluidWebRequest
import dev.antigravity.fluidengine.ui.fluid.FluidWebResponse
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** Come una pagina web dichiara i suoi colori ([DataWeb.colors]). */
internal enum class DataWebColors { None, Scheme, Own }

/** Dove sta una risorsa chiesta da una pagina della famiglia. */
internal sealed interface DataWebTarget {
  /** Dentro il file o la sua cartella: [path] pulito, senza «..» che esce e senza «/» in testa. */
  class Local(val path: String) : DataWebTarget
  /** Una parte di una mail, per il suo Content-ID (`cid:`). */
  class Part(val contentId: String) : DataWebTarget
  /** Fuori: un host vero, `file:`, `content:`. Bloccata, salvo il permesso dell'app. */
  class Remote(val url: Uri) : DataWebTarget
}

/**
 * F6C.13 · Le regole delle pagine web della famiglia (HTML, EPUB, mail) dentro `FluidWebContent`, la WebView chiusa di
 * engine-ui. Il contenitore serve il documento su un'origine casuale `https://<uuid>.invalid/document` e consegna ogni
 * altra richiesta a `onRequest`: un indirizzo relativo arriva quindi con lo stesso host `.invalid`, uno remoto con il
 * suo host. Qui si decide: **il locale si serve dai byte del file, il remoto si blocca**. Nessuna richiesta di rete parte
 * mai da questo modulo: chi carica un'immagine remota è solo il client dell'app, e solo se l'app lo dà
 * ([FluidDataSettings.remoteImages]).
 */
internal object DataWeb {

  /** Dove punta [url]. Un percorso che esce dalla radice («../../x») si ferma alla radice, come in un browser. */
  fun target(url: Uri): DataWebTarget {
    val scheme = url.scheme?.lowercase(Locale.ROOT)
    if (scheme == "cid" || scheme == "mid") {
      val id = url.schemeSpecificPart.orEmpty().removePrefix("//")
      return DataWebTarget.Part(Uri.decode(id).trim('<', '>'))
    }
    val host = url.host?.lowercase(Locale.ROOT)
    if ((scheme == "https" || scheme == "http") && host != null && host.endsWith(".invalid")) {
      val path = normalize(Uri.decode(url.encodedPath.orEmpty()))
      return if (path == null) DataWebTarget.Remote(url) else DataWebTarget.Local(path)
    }
    return DataWebTarget.Remote(url)
  }

  /** «a/./b/../c.png» → «a/c.png»; `null` per un percorso vuoto o con un NUL. */
  fun normalize(path: String): String? {
    if ('\u0000' in path) return null
    val out = ArrayList<String>()
    for (part in path.replace('\\', '/').split('/')) {
      when (part) {
        "", "." -> {}
        ".." -> if (out.isNotEmpty()) out.removeAt(out.lastIndex)
        else -> out += part
      }
    }
    return out.joinToString("/").takeIf { it.isNotEmpty() }
  }

  /** La cartella di un percorso di uno ZIP («OEBPS/Text/c1.xhtml» → «OEBPS/Text/»). */
  fun folderOf(path: String): String = path.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }

  /** [relative] visto da [folder], pulito; `null` se non è relativo (ha uno schema, è un frammento, è `//host`). */
  fun resolve(folder: String, relative: String): String? {
    val value = relative.trim()
    if (value.isEmpty() || value.startsWith("#") || value.startsWith("//") || hasScheme(value)) return null
    val noQuery = value.substringBefore('#').substringBefore('?')
    val decoded = try {
      Uri.decode(noQuery)
    } catch (_: Exception) {
      noQuery
    }
    return if (decoded.startsWith("/")) normalize(decoded) else normalize(folder + decoded)
  }

  private fun hasScheme(value: String): Boolean {
    val colon = value.indexOf(':')
    if (colon <= 0) return false
    val slash = value.indexOf('/')
    return (slash < 0 || colon < slash) && value.substring(0, colon).all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }
  }

  private val AttributeUrl = Regex("""(\s(?:src|href|xlink:href|poster|data|background)\s*=\s*)(["'])(.*?)\2""", RegexOption.IGNORE_CASE)
  private val SrcSet = Regex("""(\ssrcset\s*=\s*)(["'])(.*?)\2""", RegexOption.IGNORE_CASE)
  private val CssUrl = Regex("""url\(\s*(["']?)([^"')]+)\1\s*\)""", RegexOption.IGNORE_CASE)

  /**
   * Gli indirizzi relativi di [html] riscritti come percorsi assoluti dalla radice dello ZIP: il documento sta sempre in
   * `/document`, quindi «../Images/a.jpg» di un capitolo in `OEBPS/Text/` diventerebbe «/Images/a.jpg». Così diventa
   * «/OEBPS/Images/a.jpg», che il gestore delle richieste trova. Vale per attributi, `srcset` e `url()` dei CSS in linea.
   */
  fun rebase(html: String, folder: String): String {
    fun absolute(value: String): String {
      val resolved = resolve(folder, value) ?: return value
      val fragment = value.substringAfter('#', "").let { if (it.isEmpty()) "" else "#$it" }
      return "/" + resolved.split('/').joinToString("/") { Uri.encode(it) } + fragment
    }
    var out = AttributeUrl.replace(html) { m -> m.groupValues[1] + m.groupValues[2] + absolute(m.groupValues[3]) + m.groupValues[2] }
    out = SrcSet.replace(out) { m ->
      val list = m.groupValues[3].split(',').joinToString(", ") { item ->
        val parts = item.trim().split(Regex("\\s+"), limit = 2)
        (listOf(absolute(parts[0])) + parts.drop(1)).joinToString(" ")
      }
      m.groupValues[1] + m.groupValues[2] + list + m.groupValues[2]
    }
    return rebaseCss(out, folder)
  }

  /** Gli `url(…)` relativi di un foglio di stile visto da [folder]. */
  fun rebaseCss(css: String, folder: String): String = CssUrl.replace(css) { m ->
    val resolved = resolve(folder, m.groupValues[2]) ?: return@replace m.value
    "url(\"/" + resolved.split('/').joinToString("/") { Uri.encode(it) } + "\")"
  }

  private val LinkTag = Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE)
  private val Href = Regex("""\bhref\s*=\s*(["'])(.*?)\1""", RegexOption.IGNORE_CASE)
  private val RelStylesheet = Regex("""\brel\s*=\s*(["'])\s*stylesheet\s*\1""", RegexOption.IGNORE_CASE)

  /**
   * I fogli di stile collegati di un capitolo, messi dentro il documento: `<link rel="stylesheet" type="text/css">` per
   * il sigillo di `FluidWebContent` è un caso dubbio e verrebbe tolto. [read] dà il CSS di un percorso dello ZIP;
   * i suoi `url()` diventano assoluti dalla sua cartella.
   */
  fun inlineStylesheets(html: String, folder: String, read: (String) -> String?): String = LinkTag.replace(html) { m ->
    val tag = m.value
    if (!RelStylesheet.containsMatchIn(tag)) return@replace ""
    val href = Href.find(tag)?.groupValues?.get(2) ?: return@replace ""
    val path = resolve(folder, href) ?: return@replace ""
    val css = read(path) ?: return@replace ""
    "<style>\n" + rebaseCss(css, folderOf(path)).replace("</style", "<\\/style") + "\n</style>"
  }

  private val VoidElements = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source",
    "track", "wbr", "param", "keygen", "basefont", "frame", "image", "circle", "rect", "path", "line", "polygon",
    "polyline", "ellipse", "use", "stop")
  private val SelfClosing = Regex("""<([A-Za-z][\w:.-]*)(\s[^<>]*?)?\s*/>""")

  /**
   * Un capitolo XHTML letto come HTML: `<title/>`, `<div/>`, `<a id="x"/>` per il parser HTML non sono chiusi, e un
   * `<title/>` si mangerebbe il resto della pagina. Si aprono e chiudono, salvo gli elementi vuoti di HTML e di SVG.
   */
  fun expandSelfClosing(html: String): String = SelfClosing.replace(html) { m ->
    val name = m.groupValues[1]
    if (name.lowercase(Locale.ROOT) in VoidElements) m.value else "<$name${m.groupValues[2]}></$name>"
  }

  /** Il MIME di una risorsa dal suo nome, per servirla; `null` per ciò che non si serve (script, eseguibili). */
  fun mimeOf(path: String): String? = when (path.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
    "css" -> "text/css"
    "png" -> "image/png"
    "jpg", "jpeg", "jpe", "jfif" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "svg" -> "image/svg+xml"
    "bmp" -> "image/bmp"
    "ico" -> "image/x-icon"
    "avif" -> "image/avif"
    "heic" -> "image/heic"
    "ttf" -> "font/ttf"
    "otf" -> "font/otf"
    "woff" -> "font/woff"
    "woff2" -> "font/woff2"
    "html", "htm", "xhtml" -> "text/html"
    "xml" -> "application/xml"
    "mp3" -> "audio/mpeg"
    "m4a", "mp4", "m4v" -> if (path.endsWith("m4a")) "audio/mp4" else "video/mp4"
    "ogg", "oga" -> "audio/ogg"
    "webm" -> "video/webm"
    "vtt" -> "text/vtt"
    else -> null
  }

  /**
   * Il gestore di `onRequest` di una pagina: [local] serve un percorso pulito, [part] una parte `cid:`, [remote] (il
   * permesso dell'app) un indirizzo remoto. Ciò che manca si blocca; [blocked] conta le risorse remote rifiutate, per
   * dirlo nella pagina. Gira sui thread di I/O della WebView, anche in parallelo: niente stato condiviso oltre il conto.
   */
  class Policy(
    private val local: ((String) -> FluidWebResponse?)?,
    private val part: ((String) -> FluidWebResponse?)? = null,
    @Volatile var remote: ((Uri) -> FluidWebResponse)? = null,
  ) {
    val blocked = AtomicInteger(0)
    val served = AtomicInteger(0)

    fun handle(request: FluidWebRequest): FluidWebResponse {
      if (request.isMainFrame) return FluidWebResponse.Block
      val response = when (val target = target(request.url)) {
        is DataWebTarget.Local -> if (target.path == "document") null else local?.invoke(target.path)
        is DataWebTarget.Part -> part?.invoke(target.contentId)
        is DataWebTarget.Remote -> {
          val loader = remote
          if (loader == null || !request.method.equals("GET", ignoreCase = true)) {
            blocked.incrementAndGet()
            null
          } else {
            loader(target.url)
          }
        }
      }
      if (response is FluidWebResponse.Serve) served.incrementAndGet()
      return response ?: FluidWebResponse.Block
    }
  }

  fun serve(path: String, bytes: () -> InputStream?): FluidWebResponse? {
    val mime = mimeOf(path) ?: return null
    val stream = bytes() ?: return null
    return FluidWebResponse.Serve(mime, if (mime.startsWith("text/")) "UTF-8" else null) { stream }
  }

  fun serveBytes(mime: String, bytes: ByteArray): FluidWebResponse = FluidWebResponse.Serve(mime, null) { ByteArrayInputStream(bytes) }

  private fun css(color: Color): String = "#%06X".format(color.toArgb() and 0xFFFFFF)

  /**
   * Il foglio del lettore di un libro, come Libri: il fondo e l'inchiostro del tema, i link nell'accento; il corpo a
   * [DataProfile.BookFontSize] px con l'interlinea [DataProfile.BookLineHeight] (con la viewport alla larghezza dello
   * schermo un px CSS è un dp, e lo zoom del testo di WebView ci aggiunge la scala del testo di sistema); una **colonna
   * di lettura** larga al più [DataProfile.BookColumn], centrata sul tablet, con i margini della pagina sul telefono; i
   * titoli dei capitoli grandi; le immagini dentro la colonna. I caratteri del libro restano i suoi (il suo foglio viene
   * dopo e vince); i colori no, come in Libri.
   */
  fun readerCss(surface: Color, ink: Color, muted: Color, accent: Color, dark: Boolean, marginPx: Int): String = """
    :root { color-scheme: ${if (dark) "dark" else "light"}; }
    html { background: ${css(surface)} !important; font-size: ${DataProfile.BookFontSize}px !important; }
    body { background: ${css(surface)} !important; color: ${css(ink)} !important;
      max-width: ${DataProfile.BookColumn}px !important; margin: 0 auto !important; box-sizing: content-box !important;
      padding: 12px ${marginPx}px 72px ${marginPx}px !important;
      font-size: ${DataProfile.BookFontSize}px !important; line-height: ${DataProfile.BookLineHeight} !important;
      font-family: "Noto Serif", Georgia, serif; overflow-wrap: break-word; hyphens: auto; -webkit-hyphens: auto;
      -webkit-text-size-adjust: 100%; }
    p, li, dd, blockquote { line-height: ${DataProfile.BookLineHeight} !important; }
    p { margin-top: 0; margin-bottom: 0.9em; }
    h1 { font-size: 1.9em !important; line-height: 1.2 !important; margin: 0.5em 0 0.9em 0 !important; font-weight: 700 !important; }
    h2 { font-size: 1.45em !important; line-height: 1.25 !important; margin: 1.3em 0 0.6em 0 !important; font-weight: 700 !important; }
    h3 { font-size: 1.2em !important; line-height: 1.3 !important; margin: 1.2em 0 0.5em 0 !important; }
    body *:not(a) { color: inherit !important; background-color: transparent !important; border-color: ${css(muted)} !important; }
    a, a * { color: ${css(accent)} !important; }
    img, svg, video, image, object, embed { max-width: 100% !important; height: auto !important; }
    img { display: block; margin: 1.1em auto !important; border-radius: 6px; }
    pre, code { white-space: pre-wrap; font-size: 0.85em; }
    table { max-width: 100% !important; border-collapse: collapse; }
    td, th { padding: 4px 8px; }
  """.trimIndent()

  /**
   * Come una pagina web dichiara i suoi colori, per il tema scuro:
   * - [DataWebColors.Scheme]: dichiara il suo schema (`color-scheme`, `prefers-color-scheme`): sa da sé come stare al
   *   buio, e il contenitore glielo chiede (`darkening`);
   * - [DataWebColors.Own]: ha colori suoi (un fondo, un colore del testo, `bgcolor`, `<font color>`): si rispettano, e
   *   la pagina resta com'è anche nel tema scuro (scurirla cambierebbe i colori dell'autore, e un testo scuro su un fondo
   *   scurito non si leggerebbe);
   * - [DataWebColors.None]: nessun colore, il testo nero di serie su bianco di serie: nel tema scuro prende il fondo e
   *   l'inchiostro del tema ([themeCss]), come una pagina dell'app.
   */
  fun colors(html: String): DataWebColors {
    if (SchemeMeta.containsMatchIn(html) || SchemeCss.containsMatchIn(html)) return DataWebColors.Scheme
    if (OwnColor.containsMatchIn(html) || OwnBackground.containsMatchIn(html) || OwnAttribute.containsMatchIn(html)) return DataWebColors.Own
    return DataWebColors.None
  }

  private val SchemeMeta = Regex("""<meta[^>]+name\s*=\s*["']?color-scheme""", RegexOption.IGNORE_CASE)
  private val SchemeCss = Regex("""prefers-color-scheme|color-scheme\s*:""", RegexOption.IGNORE_CASE)
  // `color:` che non sia la coda di `background-color:` o `border-color:`, con un valore che non sia «inherit».
  private val OwnColor = Regex("""(?<![\w-])color\s*:\s*(?!inherit|initial|unset|currentcolor)[#\w(]""", RegexOption.IGNORE_CASE)
  private val OwnBackground = Regex("""background(-color)?\s*:\s*(?!none|transparent|inherit|initial|unset|url)[#\w(]""", RegexOption.IGNORE_CASE)
  private val OwnAttribute = Regex("""<[a-z]+[^>]*\s(bgcolor|text|color)\s*=""", RegexOption.IGNORE_CASE)

  /** Il fondo e l'inchiostro del tema per una pagina senza colori suoi ([DataWebColors.None]), nel tema scuro. */
  fun themeCss(surface: Color, ink: Color, accent: Color): String =
    ":root { color-scheme: dark; } html, body { background: ${css(surface)}; color: ${css(ink)}; } a { color: ${css(accent)}; }"

  /**
   * L'HTML da dare al contenitore e se chiedergli lo scurimento, per il tema corrente ([dark]). Nel tema chiaro la
   * pagina è sempre com'è.
   */
  fun forTheme(html: String, dark: Boolean, surface: Color, ink: Color, accent: Color): Pair<String, Boolean> {
    if (!dark) return html to false
    return when (colors(html)) {
      DataWebColors.None -> inject(html, themeCss(surface, ink, accent), colorScheme = "dark") to true
      DataWebColors.Scheme -> html to true
      DataWebColors.Own -> html to false
    }
  }

  /** Il foglio minimo di una mail: le immagini e le tabelle dentro lo schermo, nient'altro (i colori sono del mittente). */
  const val MailCss = "body { margin: 0; overflow-wrap: break-word; } img { max-width: 100%; height: auto; } pre { white-space: pre-wrap; }"

  /** [css], il `color-scheme` e (con [viewport]) la larghezza dello schermo in testa al documento, dentro `<head>` se c'è. */
  fun inject(html: String, css: String, colorScheme: String? = null, viewport: Boolean = false): String {
    val block = buildString {
      // Senza, WebView impagina a 980 px e rimpicciolisce: un libro o una mail semplice diventano minuscoli.
      if (viewport) append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
      if (colorScheme != null) append("<meta name=\"color-scheme\" content=\"").append(colorScheme).append("\">")
      append("<style>").append(css).append("</style>")
    }
    val head = Regex("<head\\b[^>]*>", RegexOption.IGNORE_CASE).find(html)
    if (head != null) return html.substring(0, head.range.last + 1) + block + html.substring(head.range.last + 1)
    val htmlTag = Regex("<html\\b[^>]*>", RegexOption.IGNORE_CASE).find(html)
    if (htmlTag != null) return html.substring(0, htmlTag.range.last + 1) + "<head>" + block + "</head>" + html.substring(htmlTag.range.last + 1)
    return "<!DOCTYPE html><html><head>$block</head><body>$html</body></html>"
  }

  private val RemoteReference = Regex("""(?:src|srcset|href|background|poster)\s*=\s*["']?\s*(?:https?:)?//""", RegexOption.IGNORE_CASE)
  private val RemoteCss = Regex("""url\(\s*["']?\s*(?:https?:)?//""", RegexOption.IGNORE_CASE)

  /** Vero se una mail è impaginata a larghezza fissa (le newsletter a 600 px): allora si rimpicciolisce per stare nello schermo. */
  fun fixedWidth(html: String): Boolean = Regex("""width\s*[:=]\s*["']?\s*([5-9]\d\d|[1-9]\d{3})(px)?["';\s>]""", RegexOption.IGNORE_CASE).containsMatchIn(html) ||
    Regex("""<meta[^>]+name\s*=\s*["']viewport""", RegexOption.IGNORE_CASE).containsMatchIn(html)

  /** Quante risorse remote dichiara [html] (immagini, fogli, sfondi): per il pannello info e «Carica immagini». */
  fun remoteReferences(html: String): Int {
    val tags = Regex("""<(img|image|link|source|video|audio|input|body|table|td)\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(html)
    return tags.count { RemoteReference.containsMatchIn(it.value) } + RemoteCss.findAll(html).count()
  }

  /** Il testo del `<title>` di una pagina. */
  fun title(html: String): String? = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    .find(html)?.groupValues?.get(1)?.let(::plainText)?.trim()?.takeIf { it.isNotEmpty() }

  /** Il testo di un frammento HTML senza tag, con le entità comuni: per i titoli e l'anteprima. */
  fun plainText(html: String): String = html
    .replace(Regex("<(script|style)[^>]*>.*?</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
    .replace(Regex("<br\\s*/?>|</p>|</div>|</li>|</h[1-6]>|</tr>", RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("<[^>]+>"), "")
    .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
    .replace("&#39;", "'").replace("&apos;", "'")
    .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value }
    .replace(Regex("[ \\t\\u00A0]+"), " ")
    .replace(Regex("\\n\\s*\\n+"), "\n\n")
}
