package dev.antigravity.fluidengine.viewer.office

import android.graphics.Paint
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.fluid.viewer.*
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.*
import org.xmlpull.v1.XmlPullParser

internal class SheetCells(name: String) {
  var spec by mutableStateOf(FluidGridSheet(name, 0, 0))
  val rows = mutableMapOf<Int, List<FluidGridCell>>()
  var formulas = 0
  var advance: ((Int) -> Unit)? = null
}

internal class OfficeGrid(private val tables: List<SheetCells>) : FluidGridSource {
  override val sheets
    get() = tables.map { it.spec }

  override suspend fun cells(sheet: Int, rows: IntRange, columns: IntRange): List<FluidGridCell> =
    withContext(Dispatchers.Default) {
      val t = tables[sheet]
      synchronized(t) {
        t.advance?.invoke(rows.last)
        val first = maxOf(0, rows.first)
        val last = minOf(t.spec.rowCount - 1, rows.last)
        buildList { for (r in first..last) t.rows[r]?.filterTo(this) { it.column in columns } }
      }
    }
}

private fun colOf(ref: String): Int {
  var n = 0
  for (c in ref.takeWhile(Char::isLetter)) {
    n = n * 26 + (c.uppercaseChar() - 'A' + 1)
    if (n > 16384) tooLarge("Oltre 16.384 colonne")
  }
  return (n - 1).coerceAtLeast(0)
}

private fun rowOf(ref: String) =
  ((ref.dropWhile(Char::isLetter).toIntOrNull() ?: 1) - 1).coerceIn(0, 1048575)

private val builtins =
  mapOf(
    1 to "0",
    2 to "0.00",
    3 to "#,##0",
    4 to "#,##0.00",
    9 to "0%",
    10 to "0.00%",
    14 to "m/d/yy",
    15 to "d-mmm-yy",
    16 to "d-mmm",
    17 to "mmm-yy",
    18 to "h:mm AM/PM",
    19 to "h:mm:ss AM/PM",
    20 to "h:mm",
    21 to "h:mm:ss",
    22 to "m/d/yy h:mm",
    49 to "@",
  )

internal fun formatNumber(raw: String, code: String?, epoch1904: Boolean, locale: Locale): String {
  val n = raw.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return raw
  if (code.isNullOrEmpty() || code == "General" || code == "@") {
    val text = raw.removeSuffix(".0")
    return if ('.' in text)
      text.replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)
    else text
  }
  val sections = code.split(';')
  val format =
    if (n < 0 && sections.size > 1) sections[1]
    else if (n == 0.0 && sections.size > 2) sections[2] else sections[0]
  val pattern =
    format
      .replace(Regex("""\[\$([^\]-]*)[^\]]*]"""), "$1")
      .replace(Regex("""\[[^]]*]"""), "")
      .replace(Regex("""[_*]."""), "")
  val clean = pattern.replace(Regex("\"[^\"]*\""), "").lowercase(Locale.ROOT)
  return runCatching {
      if (Regex("[ydh]|m{3}|m+[/-]").containsMatchIn(clean)) {
        val day = kotlin.math.floor(n).toLong()
        val base =
          if (epoch1904) LocalDate.of(1904, 1, 1)
          else if (day < 60) LocalDate.of(1899, 12, 31) else LocalDate.of(1899, 12, 30)
        val date =
          base
            .plusDays(day)
            .atStartOfDay()
            .plusSeconds(kotlin.math.round((n - day) * 86400).toLong())
        val fmt =
          pattern
            .replace(Regex("AM/PM", RegexOption.IGNORE_CASE), "a")
            .replace("dddd", "EEEE")
            .replace("ddd", "EEE")
            .replace(Regex("m+", RegexOption.IGNORE_CASE)) { m ->
              val min =
                pattern.substring(0, m.range.first).endsWith(':') ||
                  pattern.substring(m.range.last + 1).startsWith(":")
              if (min) m.value.lowercase() else m.value.uppercase()
            }
            .replace('"', '\'')
            .replace("\\", "")
        date.format(
          DateTimeFormatter.ofPattern(if ('a' !in fmt) fmt.replace('h', 'H') else fmt, locale)
        )
      } else {
        val java = pattern.replace('"', '\'').replace(Regex("""\\(.)"""), "'$1'").replace("?", "#")
        DecimalFormat(java, DecimalFormatSymbols.getInstance(locale))
          .format(if (n < 0 && sections.size > 1) -n else n)
      }
    }
    .getOrDefault(raw)
}

internal class ExcelReader(
  private val pkg: OfficePackage,
  private val info: OfficeInfo,
  private val locale: Locale,
  faces: OfficeFonts,
) {
  private val path = "xl/workbook.xml"
  private val book = pkg.required(path)
  private val epoch = book.child("workbookPr")?.get("date1904") in setOf("1", "true")
  private val styles = pkg.target(path, "styles")?.let(pkg::xml)
  private val formats =
    styles?.child("numFmts")?.kids.orEmpty().associate {
      it["numFmtId"]?.toIntOrNull() to it["formatCode"]
    }
  private val fonts = styles?.child("fonts")?.kids.orEmpty()
  // SpreadsheetML 18.3.1.13: widths use the widest digit in the workbook's Normal style at 96 dpi.
  private val digitWidth = run {
    val normal = styles?.child("cellStyles")?.kids?.firstOrNull { it["builtinId"] == "0" }
    val normalXf = styles?.child("cellStyleXfs")?.kids?.getOrNull(normal?.get("xfId")?.toIntOrNull() ?: 0)
    val font = fonts.getOrNull(normalXf?.get("fontId")?.toIntOrNull() ?: 0)
    val ink = Ink(font = font?.child("name")?.get("val") ?: "Calibri",
      pt = font?.child("sz")?.get("val")?.toFloatOrNull() ?: 11f,
      bold = font?.child("b") != null, italic = font?.child("i") != null)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
      typeface = faces.face(ink)
      textSize = ink.pt.coerceIn(1f, 400f) * 96f / 72f
    }
    ('0'..'9').maxOf { paint.measureText(it.toString()) }.roundToInt().coerceAtLeast(1)
  }
  private fun savedWidth(characters: Float): Float =
    (((256 * characters.coerceIn(0f, 65535f) + 128 / digitWidth) / 256) * digitWidth).toInt().toFloat()
  private val fills = styles?.child("fills")?.kids.orEmpty()
  private val borders = styles?.child("borders")?.kids.orEmpty()
  private val xf = styles?.child("cellXfs")?.kids.orEmpty()
  private val looks =
    xf.map { x ->
      val font = fonts.getOrNull(x["fontId"]?.toIntOrNull() ?: -1)
      font?.child("name")?.get("val")?.let(info.fonts::add)
      val border = borders.getOrNull(x["borderId"]?.toIntOrNull() ?: -1)
      fun edge(name: String): Color =
        border
          ?.child(name)
          ?.takeIf { it["style"] != null }
          ?.child("color")
          ?.get("rgb")
          .rgb()
          ?.let(::Color) ?: Color.Unspecified
      val a = x.child("alignment")
      FluidGridStyle(
        bold = font?.child("b") != null,
        italic = font?.child("i") != null,
        underline = font?.child("u") != null,
        strikethrough = font?.child("strike") != null,
        color = font?.child("color")?.get("rgb").rgb()?.let(::Color) ?: Color.Unspecified,
        fill =
          fills
            .getOrNull(x["fillId"]?.toIntOrNull() ?: -1)
            ?.first("fgColor")
            ?.get("rgb")
            .rgb()
            ?.let(::Color) ?: Color.Unspecified,
        wrap = a?.get("wrapText") == "1",
        horizontal =
          when (a?.get("horizontal")) {
            "center" -> Alignment.CenterHorizontally
            "right" -> Alignment.End
            "left" -> Alignment.Start
            else -> null
          },
        fontScale = (font?.child("sz")?.get("val")?.toFloatOrNull() ?: 11f) / 11f,
        borders = FluidGridBorders(edge("top"), edge("bottom"), edge("left"), edge("right")),
      )
    }
  private val strings =
    pkg.target(path, "sharedStrings")?.let(pkg::xml)?.kids.orEmpty().map { s ->
      s.all("t").joinToString("") { it.text() }
    }
  private val count = java.util.concurrent.atomic.AtomicInteger()

  private fun sheet(name: String, file: String): SheetCells {
    val table = SheetCells(name)
    var rows = 0
    var cols = 0
    var row = 0
    var col = 0
    var frozenRows = 0
    var frozenColumns = 0
    var color: Color? = null
    val heights = mutableMapOf<Int, Float>()
    val widths = mutableMapOf<Int, Float>()
    val merges = mutableListOf<FluidGridRange>()
    var line = mutableListOf<FluidGridCell>()
    val data = pkg.bytes(file) ?: damaged("Foglio mancante: $name")
    val large = data.size > 512_000
    var dimensionRows = 0
    var dimensionCols = 0
    var defaultWidth = FluidGridSheet.DefaultColumnWidth
    fun publish(final: Boolean) {
      val nr = maxOf(rows, dimensionRows)
      val nc = maxOf(cols, dimensionCols)
      table.spec =
        FluidGridSheet(
          name,
          nr,
          nc,
          frozenRows.coerceIn(0, nr),
          frozenColumns.coerceIn(0, nc),
          columnWidth = defaultWidth,
          columnWidths = widths.toMap(),
          rowHeights = heights.toMap(),
          merges = merges.toList(),
          color = color,
          countFinal = final,
        )
    }
    val cursor =
      sequence<Int> {
          val p = parseXml(data.inputStream())
          var event = p.eventType
          while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.DOCDECL || p.depth > 96) damaged("XML del foglio non valido")
            if (event == XmlPullParser.START_TAG)
              when (p.name) {
                "sheetFormatPr" -> {
                  p.attr("defaultColWidth")?.toFloatOrNull()?.let { defaultWidth = savedWidth(it) }
                    ?: p.attr("baseColWidth")?.toIntOrNull()?.let { defaultWidth = it.coerceIn(0,255) * digitWidth + 5f }
                }
                "dimension" ->
                  p.attr("ref")?.substringAfterLast(':')?.let { ref ->
                    dimensionRows = rowOf(ref) + 1
                    dimensionCols = colOf(ref) + 1
                  }
                "tabColor" -> color = p.attr("rgb").rgb()?.let(::Color)
                "pane" ->
                  if (p.attr("state") in setOf("frozen", "frozenSplit")) {
                    frozenRows = p.attr("ySplit")?.toFloatOrNull()?.toInt() ?: 0
                    frozenColumns = p.attr("xSplit")?.toFloatOrNull()?.toInt() ?: 0
                  }
                "col" -> {
                  val first = (p.attr("min")?.toIntOrNull() ?: 1).coerceIn(1, 16384)
                  val last = (p.attr("max")?.toIntOrNull() ?: first).coerceIn(first, 16384)
                  val w =
                    if (p.attr("hidden") == "1") 0f
                    else p.attr("width")?.toFloatOrNull()?.let(::savedWidth) ?: defaultWidth
                  for (i in first..last) widths[i - 1] = w
                }
                "row" -> {
                  row = (p.attr("r")?.toIntOrNull()?.minus(1) ?: rows).coerceIn(0, 1048575)
                  col = 0
                  line = mutableListOf()
                  if (p.attr("hidden") == "1") heights[row] = 0f
                  else p.attr("ht")?.toFloatOrNull()?.let { h -> heights[row] = h * 4 / 3 }
                }
                "c" -> {
                  if (count.incrementAndGet() > 250000) tooLarge("Oltre 250.000 celle")
                  p.attr("r")?.let { ref -> col = colOf(ref) }
                  val type = p.attr("t")
                  val style = p.attr("s")?.toIntOrNull() ?: 0
                  var raw = ""
                  var formula = false
                  while (!(p.eventType == XmlPullParser.END_TAG && p.name == "c")) {
                    when (p.next()) {
                      XmlPullParser.START_TAG ->
                        when (p.name) {
                          "v" -> raw = p.nextText()
                          "t" -> raw += p.nextText()
                          "f" -> formula = true
                        }
                      XmlPullParser.END_DOCUMENT -> damaged("Cella troncata")
                    }
                  }
                  if (formula) table.formulas++
                  val id = xf.getOrNull(style)?.get("numFmtId")?.toIntOrNull()
                  val text =
                    when (type) {
                      "s" ->
                        strings.getOrNull(raw.toIntOrNull() ?: -1)
                          ?: damaged("Indice testo non valido")
                      "b" -> if (raw == "1") "TRUE" else "FALSE"
                      "inlineStr",
                      "str",
                      "e",
                      "d" -> raw
                      else -> formatNumber(raw, formats[id] ?: builtins[id], epoch, locale)
                    }
                  line +=
                    FluidGridCell(
                      row,
                      col,
                      text,
                      looks.getOrNull(style) ?: FluidGridStyle.Default,
                      type == null || type == "n",
                    )
                  cols = maxOf(cols, col + 1)
                  col++
                }
                "mergeCell" -> {
                  val endpoints = p.attr("ref").orEmpty().split(':')
                  if (endpoints.size == 2)
                    merges +=
                      FluidGridRange(
                        rowOf(endpoints[0]),
                        colOf(endpoints[0]),
                        rowOf(endpoints[1]),
                        colOf(endpoints[1]),
                      )
                }
                "drawing" -> info.omitted += "Disegni e grafici ancorati al foglio"
              }
            else if (event == XmlPullParser.END_TAG && p.name == "row") {
              table.rows[row] = line
              rows = maxOf(rows, row + 1)
              if (large && dimensionRows > 0) yield(row)
            }
            event = p.nextToken()
          }
          publish(true)
        }
        .iterator()
    var last = -1
    fun advance(target: Int) {
      while (last < target) {
        if (!cursor.hasNext()) {
          table.advance = null
          return
        }
        last = cursor.next()
      }
      if (last + 1 >= dimensionRows && !cursor.hasNext()) table.advance = null else publish(false)
    }
    table.advance = ::advance
    advance(if (large) 0 else Int.MAX_VALUE)
    return table
  }

  fun read(): OfficeGrid =
    finishGrid(
      book.child("sheets")?.kids.orEmpty().map { s ->
        val file = pkg.rels(path)[s["r:id"]]?.path ?: damaged("Riferimento al foglio mancante")
        sheet(s["name"] ?: damaged("Nome del foglio mancante"), file)
      },
      info,
    )
}

internal fun finishGrid(sheets: List<SheetCells>, info: OfficeInfo): OfficeGrid {
  if (sheets.isEmpty()) damaged("Nessun foglio nel documento")
  info.add("Fogli", sheets.size, "Contenuto")
  for (s in sheets) info.add(s.spec.name, "${s.spec.rowCount} × ${s.spec.columnCount}", "Fogli")
  if (sheets.all { it.spec.countFinal })
    info.add("Formule", sheets.sumOf { it.formulas }, "Calcolo")
  else info.add("Formule", "Conteggio parziale durante la lettura", "Calcolo")
  info.add("Valori", "Risultati salvati nel file", "Calcolo")
  return OfficeGrid(sheets)
}
