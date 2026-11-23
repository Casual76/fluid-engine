package dev.antigravity.fluidengine.viewer.data

import android.graphics.BitmapFactory
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import dev.antigravity.fluidengine.viewer.FluidSyntax
import dev.antigravity.fluidengine.viewer.FluidThumbnailPainter
import dev.antigravity.fluidengine.viewer.FluidViewerRequest
import java.io.StringReader
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * F6C.17 · Le miniature dei testi ricchi e dei dati, nella grammatica di File (`ricerca/visori-2.md` §2 e §3), con gli
 * attrezzi di `FluidThumbnailPainter`:
 *
 * | tipo | miniatura |
 * |---|---|
 * | Markdown | reso: titoli, grassetto, elenchi, codice, citazioni e tabelle sul foglio |
 * | HTML | reso con stili e immagini locali, senza rete n? script |
 * | CSV, TSV | un pezzo di tabella: intestazione, righe, griglia, numeri a destra |
 * | JSON | un pezzo dell'albero coi punti colorati dei tipi, come la pagina |
 * | NDJSON, XML, plist | il testo, coi colori; un plist binario come albero |
 * | EPUB | la copertina; senza, una copertina col titolo e l'autore |
 * | EML | mittente, data, oggetto e l'inizio del corpo |
 * | ICS | la tessera della data del primo evento |
 * | VCF | la foto del primo contatto, o le sue iniziali |
 *
 * Un file che non si legge dà `null`: il registro prova il candidato dopo (il testo di serie) e, in fondo, l'icona del
 * documento con l'estensione.
 */
internal object DataThumbnail {
  private val Hidden = Regex("(?is)<head\\b.*?</head>|<script\\b.*?</script>|<style\\b.*?</style>|<!--.*?-->|<noscript\\b.*?</noscript>")
  /** Quanti byte legge la miniatura di un testo: le prime righe stanno nei primi 64 KB. */
  private const val TextBytes = 64 * 1024

  /** Quanti nodi legge l'albero di un JSON per la miniatura: ne mostra una ventina. */
  private const val TreeNodes = 600

  suspend fun of(request: FluidViewerRequest, settings: FluidDataSettings, sizePx: Int): ImageBitmap? {
    val source = request.source
    suspend fun bytes(limit: Int = TextBytes): DataBytes = source.open().use { DataText.read(it, limit, source.sizeBytes) }
    suspend fun text(declared: (DataBytes) -> String? = { null }): String = bytes().let { DataText.decode(it, declared(it)).text }
    return when (request.type.id) {
      FluidDataFamily.Markdown -> markdown(text(), sizePx)
      FluidDataFamily.Html -> {
        val raw = bytes(HtmlThumbnail.MaxHtmlBytes)
        HtmlThumbnail.render(DataText.decode(raw, DataText.htmlCharset(raw.bytes, raw.length)).text, sizePx) { path ->
          settings.resources?.invoke(source, path)
        }
      }
      FluidDataFamily.Csv, FluidDataFamily.Tsv -> table(text(), source.name, sizePx)
      FluidDataFamily.Json -> tree(DataJsonParser.parse(StringReader(text()), TreeNodes), sizePx)
      FluidDataFamily.Ndjson -> code(text(), FluidSyntax.Json, sizePx)
      FluidDataFamily.Xml -> code(text { DataText.xmlCharset(it.bytes, it.length) }, FluidSyntax.Xml, sizePx)
      FluidDataFamily.Plist -> {
        val raw = bytes()
        if (DataPlist.isBinary(raw.bytes, raw.length)) tree(DataPlist.parseBinary(raw.bytes, raw.length, TreeNodes), sizePx)
        else code(DataText.decode(raw, DataText.xmlCharset(raw.bytes, raw.length)).text, FluidSyntax.Xml, sizePx)
      }
      FluidDataFamily.Epub -> epub(request, settings, sizePx)
      FluidDataFamily.Eml -> mail(DataMime.parse(bytes(DataProfile.MaxEntryBytes.toInt()).let { it.bytes.copyOf(it.length) }), sizePx)
      FluidDataFamily.Ics -> calendar(DataIcs.parse(text()), sizePx)
      FluidDataFamily.Vcf -> contact(DataVcard.parse(text()), sizePx)
      else -> null
    }
  }

  // --- Markdown ---------------------------------------------------------------------------------------------------

  private val Heading = Regex("^(#{1,6})\\s+(.*)$")
  private val Bullet = Regex("^(\\s*)([-*+]|\\d+[.)])\\s+(.*)$")
  private val Task = Regex("^\\[([ xX])]\\s+(.*)$")
  private val Rule = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")
  private val Inline = Regex("!\\[([^]]*)]\\([^)]*\\)|\\[([^]]+)]\\(([^)]*)\\)|\\*\\*(.+?)\\*\\*|__(.+?)__|`([^`]+)`|\\*([^*]+)\\*|_([^_]+)_")

  /** Il Markdown reso sul foglio: abbastanza per riconoscere titoli, elenchi, tabelle e codice. */
  fun markdown(source: String, sizePx: Int): ImageBitmap {
    val out = SpannableStringBuilder()
    var fence = false
    var paragraph = false
    val table = ArrayList<List<String>>()
    fun line() {
      if (out.isNotEmpty() && out[out.length - 1] != '\n') out.append('\n')
    }
    fun flushTable() {
      if (table.isEmpty()) return
      val columns = table.maxOf { it.size }
      val widths = IntArray(columns) { c -> table.maxOf { row -> row.getOrNull(c)?.length ?: 0 }.coerceAtMost(14) }
      for ((index, row) in table.withIndex()) {
        line()
        val start = out.length
        out.append(row.mapIndexed { c, cell -> cell.take(14).padEnd(widths[c]) }.joinToString("  "))
        out.setSpan(TypefaceSpan("monospace"), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        out.setSpan(RelativeSizeSpan(0.86f), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (index == 0) out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      }
      table.clear()
    }
    for (raw in source.lineSequence().take(90)) {
      val text = raw.trimEnd()
      if (text.trimStart().startsWith("```") || text.trimStart().startsWith("~~~")) {
        fence = !fence
        continue
      }
      if (fence) {
        line()
        val start = out.length
        out.append(" ").append(text.replace("\t", "  ")).append(" ")
        out.setSpan(TypefaceSpan("monospace"), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        out.setSpan(RelativeSizeSpan(0.86f), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        out.setSpan(BackgroundColorSpan(FluidThumbnailPainter.Fill), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        out.setSpan(ForegroundColorSpan(0xFF643820.toInt()), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        continue
      }
      val trimmed = text.trim()
      val continues = paragraph
      paragraph = false
      if (trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.length > 1) {
        val cells = trimmed.trim('|').split('|').map { it.trim() }
        if (cells.all { it.matches(Regex(":?-{2,}:?")) }) continue
        table += cells.map(::plain)
        continue
      }
      flushTable()
      when {
        trimmed.isEmpty() -> {
          line()
          val start = out.length
          out.append('\n')
          out.setSpan(RelativeSizeSpan(0.45f), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        Rule.matches(trimmed) -> Unit
        Heading.matches(trimmed) -> {
          val match = requireNotNull(Heading.matchEntire(trimmed))
          val level = match.groupValues[1].length
          line()
          val start = out.length
          inline(out, match.groupValues[2])
          out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
          out.setSpan(RelativeSizeSpan(when (level) { 1 -> 1.55f; 2 -> 1.25f; 3 -> 1.1f; else -> 1f }), start, out.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        Bullet.matches(text) -> {
          val match = requireNotNull(Bullet.matchEntire(text))
          val indent = match.groupValues[1].length / 2
          val marker = match.groupValues[2]
          var body = match.groupValues[3]
          val task = Task.matchEntire(body)
          line()
          out.append("  ".repeat(indent))
          if (task != null) {
            out.append(if (task.groupValues[1].isBlank()) "☐ " else "☑ ")
            body = task.groupValues[2]
          } else {
            out.append(if (marker.first().isDigit()) "$marker " else "•  ")
          }
          inline(out, body)
        }
        trimmed.startsWith(">") -> {
          line()
          val start = out.length
          out.append("▍ ")
          out.setSpan(ForegroundColorSpan(FluidThumbnailPainter.Rule), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
          val body = out.length
          inline(out, trimmed.trimStart('>').trim())
          out.setSpan(ForegroundColorSpan(FluidThumbnailPainter.InkSecondary), body, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
          out.setSpan(StyleSpan(Typeface.ITALIC), body, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        else -> {
          // Un paragrafo continua sulla riga dopo, come in Markdown.
          if (continues) out.append(' ') else line()
          inline(out, trimmed)
          paragraph = true
        }
      }
      if (out.length > 4_000) break
    }
    flushTable()
    return FluidThumbnailPainter.prosePage(out, sizePx)
  }

  /** Il testo di una riga di Markdown con i suoi segni resi: grassetto, corsivo, codice, collegamenti, immagini. */
  private fun inline(out: SpannableStringBuilder, text: String) {
    var at = 0
    for (match in Inline.findAll(text)) {
      out.append(text, at, match.range.first)
      val start = out.length
      val g = match.groupValues
      when {
        match.value.startsWith("![") -> {
          out.append(g[1].ifEmpty { "immagine" })
          out.setSpan(StyleSpan(Typeface.ITALIC), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
          out.setSpan(ForegroundColorSpan(FluidThumbnailPainter.InkSecondary), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        g[2].isNotEmpty() -> {
          out.append(g[2])
          out.setSpan(ForegroundColorSpan(FluidThumbnailPainter.Link), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
          out.setSpan(UnderlineSpan(), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        g[4].isNotEmpty() || g[5].isNotEmpty() -> {
          out.append(g[4].ifEmpty { g[5] })
          out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        g[6].isNotEmpty() -> {
          out.append(" ").append(g[6]).append(" ")
          out.setSpan(TypefaceSpan("monospace"), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
          out.setSpan(BackgroundColorSpan(FluidThumbnailPainter.Fill), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        else -> {
          out.append(g[7].ifEmpty { g[8] })
          out.setSpan(StyleSpan(Typeface.ITALIC), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
      }
      at = match.range.last + 1
    }
    out.append(text, at, text.length)
  }

  private fun plain(text: String): String {
    val out = SpannableStringBuilder()
    inline(out, text)
    return out.toString().trim()
  }

  // --- CSV e TSV --------------------------------------------------------------------------------------------------

  /** Un pezzo di tabella su un foglio orizzontale: intestazione in grassetto su grigio, la griglia, i numeri a destra. */
  fun table(source: String, name: String, sizePx: Int): ImageBitmap? {
    val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val dialect = DataCsv.sniff(source.take(16 * 1024), extension)
    val rows = DataCsv.records(source, dialect, maxRecords = 14, dropLastPartial = false).filter { row -> row.any { it.isNotBlank() } }
    if (rows.isEmpty()) return null
    val header = DataCsv.hasHeader(rows)
    val columns = min(5, rows.maxOf { it.size })
    return FluidThumbnailPainter.page(sizePx, FluidThumbnailPainter.TableAspect) { canvas, content, unit ->
      val rowHeight = content.height() / 9f
      val paint = FluidThumbnailPainter.paint(rowHeight * 0.5f)
      val bold = FluidThumbnailPainter.paint(rowHeight * 0.5f, bold = true)
      val weights = FloatArray(columns) { c ->
        rows.take(10).maxOf { (it.getOrNull(c)?.replace('\n', ' ')?.length ?: 0).toFloat() }.coerceIn(3f, 14f)
      }
      val total = weights.sum()
      val widths = weights.map { it / total * content.width() }
      val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = FluidThumbnailPainter.Rule
        strokeWidth = max(1f, unit / 22f)
      }
      if (header) canvas.drawRect(content.left, content.top, content.right, content.top + rowHeight,
        Paint().apply { color = FluidThumbnailPainter.Fill })
      var y = content.top
      for ((r, row) in rows.withIndex()) {
        if (y > content.bottom) break
        var x = content.left
        val first = r == 0 && header
        for (c in 0 until columns) {
          val cell = row.getOrNull(c).orEmpty().replace('\n', ' ').trim()
          val pen = if (first) bold else paint
          val pad = rowHeight * 0.22f
          val shown = FluidThumbnailPainter.ellipsize(cell, pen, widths[c] - pad * 2).toString()
          val numeric = !first && DataCsv.looksNumeric(cell)
          val textWidth = pen.measureText(shown)
          val tx = if (numeric) x + widths[c] - pad - textWidth else x + pad
          canvas.drawText(shown, tx, y + rowHeight * 0.68f, pen)
          x += widths[c]
        }
        y += rowHeight
        canvas.drawLine(content.left, y, content.right, y, grid)
      }
      var x = content.left
      for (c in 0 until columns - 1) {
        x += widths[c]
        canvas.drawLine(x, content.top, x, min(content.bottom, y), grid)
      }
    }
  }

  // --- Alberi -----------------------------------------------------------------------------------------------------

  /** Un pezzo dell'albero come la pagina: i contenitori aperti fino al secondo livello, i punti colorati dei tipi. */
  fun tree(tree: DataTree, sizePx: Int): ImageBitmap? {
    val root = tree.root ?: return null
    val colors = DataTypeColors.Light
    val out = SpannableStringBuilder()
    var lines = 0
    fun color(kind: DataKind, node: DataNode): Int = when (kind) {
      DataKind.String, DataKind.Text -> colors.string
      DataKind.Number -> colors.number
      DataKind.Bool -> colors.bool
      DataKind.Null -> colors.nil
      DataKind.Date -> colors.date
      DataKind.Data -> colors.data
      DataKind.Element -> if (node.value != null) colors.string else colors.element
      DataKind.Attribute -> colors.attribute
      DataKind.Comment, DataKind.Instruction -> colors.comment
      DataKind.Object, DataKind.Array -> colors.container
    }.toArgb()
    fun span(what: Any, start: Int) = out.setSpan(what, start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    fun visit(node: DataNode, depth: Int) {
      if (lines >= FluidThumbnailPainter.CodeLinesPerPage + 1) return
      if (lines > 0) out.append('\n')
      lines++
      val open = node.kind.container && depth <= 1 && node.childCount > 0
      out.append("  ".repeat(depth))
      val chevron = out.length
      out.append(if (!node.kind.container) "  " else if (open) "⌄ " else "› ")
      span(ForegroundColorSpan(FluidThumbnailPainter.InkSecondary), chevron)
      val dot = out.length
      out.append("● ")
      span(ForegroundColorSpan(color(node.kind, node)), dot)
      span(RelativeSizeSpan(0.8f), dot)
      val name = when (node.kind) {
        DataKind.Attribute -> "@${node.name}"
        DataKind.Text -> null
        else -> node.name
      }
      if (name != null && depth > 0) {
        val key = out.length
        out.append(name)
        span(StyleSpan(Typeface.BOLD), key)
        out.append(" : ")
      }
      when (node.kind) {
        DataKind.Object, DataKind.Array -> {
          val mark = out.length
          out.append(if (node.kind == DataKind.Object) "{ }" else "[ ]")
          span(ForegroundColorSpan(colors.container.toArgb()), mark)
          val summary = out.length
          out.append("  ").append(node.summary())
          if (!open) node.preview()?.let { out.append(" · ").append(it) }
          span(ForegroundColorSpan(FluidThumbnailPainter.InkSecondary), summary)
        }
        DataKind.Element -> {
          val start = out.length
          out.append(if (node.value != null) "\"${node.value}\"" else node.summary())
          span(ForegroundColorSpan(if (node.value != null) colors.string.toArgb() else FluidThumbnailPainter.InkSecondary), start)
        }
        DataKind.String, DataKind.Text, DataKind.Attribute -> {
          val start = out.length
          out.append("\"").append(node.value.orEmpty().replace('\n', ' ').take(60)).append("\"")
          span(ForegroundColorSpan(color(node.kind, node)), start)
        }
        else -> {
          val start = out.length
          out.append(node.value.orEmpty().take(60))
          span(ForegroundColorSpan(color(node.kind, node)), start)
        }
      }
      if (open) for (child in node.children.orEmpty()) visit(child, depth + 1)
    }
    visit(root, 0)
    return FluidThumbnailPainter.linesPage(out, sizePx)
  }

  /** Il testo (NDJSON, XML, plist) coi colori della sua lingua. */
  private fun code(text: String, language: String, sizePx: Int): ImageBitmap? {
    val lines = text.lineSequence().take(FluidThumbnailPainter.CodeLinesPerPage + 1).map { it.take(200) }.toList()
    if (lines.all { it.isBlank() }) return null
    return FluidThumbnailPainter.codePage(lines, FluidSyntax.highlighter(language), sizePx)
  }

  // --- EPUB -------------------------------------------------------------------------------------------------------

  /** La copertina; senza, una copertina disegnata col titolo e l'autore. */
  private suspend fun epub(request: FluidViewerRequest, settings: FluidDataSettings, sizePx: Int): ImageBitmap? =
    DataOpen(request, settings).zip().use { zip ->
      val book = DataEpub.read(zip)
      book.coverPath?.let { zip.read(it) }?.let { FluidDataFamily.decodePicture(it, sizePx) }
        ?: cover(book.title ?: request.source.name.substringBeforeLast('.'), book.creators.firstOrNull(), sizePx)
    }

  private fun cover(title: String, author: String?, sizePx: Int): ImageBitmap =
    FluidThumbnailPainter.page(sizePx, aspect = 2f / 3f, clip = false) { canvas, content, unit ->
      val full = RectF(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat())
      canvas.drawRect(full, Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, full.bottom, 0xFF24425F.toInt(), 0xFF101C2B.toInt(), Shader.TileMode.CLAMP)
      })
      val paint = FluidThumbnailPainter.paint(unit * 1.2f, 0xFFFFFFFF.toInt(), bold = true).apply {
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
      }
      val y = FluidThumbnailPainter.draw(canvas, FluidThumbnailPainter.layout(title, paint, content.width().toInt()),
        content.left, content.top + content.height() * 0.18f)
      if (author != null) {
        val small = FluidThumbnailPainter.paint(unit * 0.7f, 0xCCFFFFFF.toInt())
        FluidThumbnailPainter.draw(canvas, FluidThumbnailPainter.layout(author, small, content.width().toInt()), content.left, y + unit)
      }
    }

  // --- EML --------------------------------------------------------------------------------------------------------

  /** La mail come in Mail: il mittente in grassetto con la data, l'oggetto, e l'inizio del corpo. */
  fun mail(mail: DataMail, sizePx: Int): ImageBitmap? {
    if (mail.headers.list.isEmpty() && mail.html == null && mail.text == null) return null
    val sender = mail.from.firstOrNull()?.display?.ifBlank { null } ?: "Senza mittente"
    val subject = mail.subject?.ifBlank { null } ?: "(nessun oggetto)"
    val body = mail.text?.takeIf { it.isNotBlank() }
      ?: mail.html?.let { Html.fromHtml(Hidden.replace(it, ""), Html.FROM_HTML_MODE_COMPACT, null, null).toString() }.orEmpty()
    val date = mail.date?.let { "${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, Locale.ITALIAN)}" }
    return FluidThumbnailPainter.page(sizePx) { canvas, content, unit ->
      val width = content.width().toInt()
      val avatar = unit * 1.5f
      val circle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, content.top, 0f, content.top + avatar, 0xFFA6ABB8.toInt(), 0xFF858A95.toInt(), Shader.TileMode.CLAMP)
      }
      canvas.drawCircle(content.left + avatar / 2f, content.top + avatar / 2f, avatar / 2f, circle)
      val initials = FluidThumbnailPainter.paint(avatar * 0.42f, 0xFFFFFFFF.toInt(), bold = true).apply { textAlign = Paint.Align.CENTER }
      val m = initials.fontMetrics
      canvas.drawText(initialsOf(sender), content.left + avatar / 2f, content.top + avatar / 2f - (m.ascent + m.descent) / 2f, initials)
      val textLeft = content.left + avatar + unit * 0.4f
      val name = FluidThumbnailPainter.paint(unit * 0.8f, bold = true)
      val small = FluidThumbnailPainter.paint(unit * 0.62f, FluidThumbnailPainter.InkSecondary)
      val dateWidth = date?.let { small.measureText(it) } ?: 0f
      canvas.drawText(FluidThumbnailPainter.ellipsize(sender, name, content.right - textLeft - dateWidth - unit * 0.3f).toString(),
        textLeft, content.top + avatar * 0.62f, name)
      if (date != null) canvas.drawText(date, content.right - dateWidth, content.top + avatar * 0.62f, small)
      var y = content.top + avatar + unit * 0.45f
      val subjectPaint = FluidThumbnailPainter.paint(unit * 0.78f, bold = true)
      y = FluidThumbnailPainter.draw(canvas, FluidThumbnailPainter.layout(subject, subjectPaint, width), content.left, y)
      y += unit * 0.3f
      canvas.drawLine(content.left, y, content.right, y, Paint().apply {
        color = FluidThumbnailPainter.Rule
        strokeWidth = max(1f, unit / 22f)
      })
      y += unit * 0.4f
      val text = FluidThumbnailPainter.paint(unit * 0.7f, 0xFF3A3A3C.toInt())
      FluidThumbnailPainter.draw(canvas, FluidThumbnailPainter.layout(body.trim().take(1_500), text, width, spacing = 1.1f), content.left, y)
    }
  }

  // --- ICS --------------------------------------------------------------------------------------------------------

  /** La tessera della data del primo evento, come l'icona di Calendario: il giorno della settimana in rosso, il numero grande. */
  fun calendar(calendar: DataCalendar, sizePx: Int): ImageBitmap? {
    val event = calendar.events.firstOrNull { it.start != null } ?: return null
    val date = requireNotNull(event.start).date
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ITALIAN)
    val month = date.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN)
    return FluidThumbnailPainter.symbol(sizePx) { canvas, size ->
      val tile = size * 0.64f
      val rect = RectF((size - tile) / 2f, (size - tile) / 2f, (size + tile) / 2f, (size + tile) / 2f)
      val radius = tile * 0.2237f
      canvas.drawRoundRect(RectF(rect).apply { offset(0f, size / 110f) }, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x24000000 })
      canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FluidThumbnailPainter.Paper })
      canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = FluidThumbnailPainter.Rule
        style = Paint.Style.STROKE
        strokeWidth = max(1f, size / 200f)
      })
      val red = FluidThumbnailPainter.paint(tile * 0.14f, 0xFFFF3B30.toInt(), bold = true).apply { textAlign = Paint.Align.CENTER }
      while (red.measureText(weekday) > tile * 0.8f && red.textSize > 4f) red.textSize *= 0.92f
      canvas.drawText(weekday, rect.centerX(), rect.top + tile * 0.25f, red)
      val day = FluidThumbnailPainter.paint(tile * 0.5f, FluidThumbnailPainter.Ink).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
      }
      canvas.drawText(date.dayOfMonth.toString(), rect.centerX(), rect.top + tile * 0.72f, day)
      val small = FluidThumbnailPainter.paint(tile * 0.1f, FluidThumbnailPainter.InkSecondary).apply { textAlign = Paint.Align.CENTER }
      canvas.drawText("$month ${date.year}", rect.centerX(), rect.top + tile * 0.89f, small)
    }
  }

  // --- VCF --------------------------------------------------------------------------------------------------------

  /** La foto del primo contatto in un cerchio, o le sue iniziali sul grigio di Contatti; «+N» se ce ne sono altri. */
  fun contact(contacts: List<DataContact>, sizePx: Int): ImageBitmap? {
    val first = contacts.firstOrNull() ?: return null
    val photo = first.photo?.let { bytes ->
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
      var sample = 1
      while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= sizePx) sample *= 2
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
    return FluidThumbnailPainter.symbol(sizePx) { canvas, size ->
      val d = size * 0.62f
      val rect = RectF((size - d) / 2f, (size - d) / 2f, (size + d) / 2f, (size + d) / 2f)
      val clip = Path().apply { addOval(rect, Path.Direction.CW) }
      if (photo != null) {
        canvas.save()
        canvas.clipPath(clip)
        val side = min(photo.width, photo.height)
        val src = android.graphics.Rect((photo.width - side) / 2, (photo.height - side) / 2, (photo.width + side) / 2, (photo.height + side) / 2)
        canvas.drawBitmap(photo, src, rect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        canvas.restore()
      } else {
        canvas.drawOval(rect, Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = LinearGradient(0f, rect.top, 0f, rect.bottom, 0xFFA6ABB8.toInt(), 0xFF858A95.toInt(), Shader.TileMode.CLAMP)
        })
        val paint = FluidThumbnailPainter.paint(d * 0.38f, 0xFFFFFFFF.toInt(), bold = true).apply { textAlign = Paint.Align.CENTER }
        val m = paint.fontMetrics
        canvas.drawText(initialsOf(first.name), rect.centerX(), rect.centerY() - (m.ascent + m.descent) / 2f, paint)
      }
      if (contacts.size > 1) {
        val label = "+${contacts.size - 1}"
        val paint = FluidThumbnailPainter.paint(size * 0.075f, 0xFFFFFFFF.toInt(), bold = true).apply { textAlign = Paint.Align.CENTER }
        val h = paint.textSize * 1.7f
        val w = max(h, paint.measureText(label) + h * 0.8f)
        val badge = RectF(rect.right - w * 0.8f, rect.bottom - h, rect.right - w * 0.8f + w, rect.bottom)
        canvas.drawRoundRect(badge, h / 2f, h / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF636366.toInt() })
        val m = paint.fontMetrics
        canvas.drawText(label, badge.centerX(), badge.centerY() - (m.ascent + m.descent) / 2f, paint)
      }
    }
  }

  /** Le iniziali di un nome: le prime lettere delle prime due parole, in maiuscolo. */
  internal fun initialsOf(name: String): String {
    val words = name.split(Regex("[\\s,.<>\"@]+")).filter { w -> w.isNotEmpty() && w.first().isLetter() }
    return words.take(2).joinToString("") { it.first().uppercase(Locale.ITALIAN) }.ifEmpty { "?" }
  }

}
