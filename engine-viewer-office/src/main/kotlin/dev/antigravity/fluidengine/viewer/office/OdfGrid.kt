package dev.antigravity.fluidengine.viewer.office

import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.fluid.viewer.*

internal fun OdfReader.grid(): OfficeGrid {
  val body = content.child("body")?.child("spreadsheet") ?: damaged("Corpo ODS mancante")
  val settings =
    pkg
      .xml("settings.xml")
      ?.all("config-item-map-named")
      ?.firstOrNull { it["name"] == "Tables" }
      ?.kids
      .orEmpty()
      .associateBy { it["name"] }
  var cellCount = 0L
  return finishGrid(
    body.kids
      .filter { it.name == "table" }
      .map { table ->
        val name = table["name"] ?: "Foglio"
        val result = SheetCells(name)
        val merges = mutableListOf<FluidGridRange>()
        val heights = mutableMapOf<Int, Float>()
        val colDefs = mutableListOf<Triple<Int, Int, Float>>()
        var colBase = 0
        for (col in table.all("table-column")) {
          val repeat = (col["number-columns-repeated"]?.toIntOrNull() ?: 1).coerceIn(1, 16384)
          val width =
            if (col["visibility"] in listOf("collapse", "filter")) 0f
            else
              props(col["style-name"], "table-column-properties")["column-width"].points(60f) * 4 /
                3
          colDefs += Triple(colBase, colBase + repeat, width)
          colBase += repeat
        }
        var row = 0
        var lastRow = 0
        var cols = 0
        for (r in table.all("table-row")) {
          if (row >= 1048576) break
          val repeat = (r["number-rows-repeated"]?.toIntOrNull() ?: 1).coerceIn(1, 1048576 - row)
          var c = 0
          val line = mutableListOf<FluidGridCell>()
          for (cell in
            r.kids.filter { it.name == "table-cell" || it.name == "covered-table-cell" }) {
            if (c >= 16384) break
            val copies =
              (cell["number-columns-repeated"]?.toIntOrNull() ?: 1).coerceIn(1, 16384 - c)
            val displayed =
              cell.kids.filter { it.name == "p" }.joinToString("\n", transform = ::odfText)
            val raw =
              cell["string-value"] ?: cell["value"] ?: cell["date-value"] ?: cell["boolean-value"]
            val cs = (cell["number-columns-spanned"]?.toIntOrNull() ?: 1).coerceIn(1, 16384 - c)
            val rs = (cell["number-rows-spanned"]?.toIntOrNull() ?: 1).coerceIn(1, 1048576 - row)
            if (rs > 1 || cs > 1) merges += FluidGridRange(row, c, row + rs - 1, c + cs - 1)
            if (displayed.isNotEmpty() || raw != null || cell["formula"] != null) {
              val expanded = copies.toLong() * repeat
              cellCount += expanded
              if (cellCount > 250000) tooLarge("Oltre 250.000 celle")
              if (cell["formula"] != null) result.formulas += expanded.toInt()
              val style = cell["style-name"]
              val ink = ink(style, Ink())
              val look =
                FluidGridStyle(
                  bold = ink.bold,
                  italic = ink.italic,
                  fill =
                    props(style, "table-cell-properties")["background-color"].rgb()?.let(::Color)
                      ?: Color.Unspecified,
                )
              repeat(copies) { i ->
                line +=
                  FluidGridCell(
                    row,
                    c + i,
                    displayed.ifEmpty { raw.orEmpty() },
                    look,
                    cell["value-type"] in listOf("float", "percentage", "currency", "date", "time"),
                  )
              }
              cols = maxOf(cols, c + maxOf(copies, cs))
            }
            c += copies
          }
          if (line.isNotEmpty()) {
            val height =
              if (r["visibility"] in listOf("collapse", "filter")) 0f
              else
                props(r["style-name"], "table-row-properties")["row-height"]
                  ?.points()
                  ?.times(4f / 3)
            repeat(repeat) { i ->
              result.rows[row + i] = if (i == 0) line else line.map { it.copy(row = row + i) }
              if (height != null) heights[row + i] = height
            }
            lastRow = row + repeat
          }
          row += repeat
        }
        val widths = buildMap {
          for ((start, end, w) in colDefs) for (i in start until minOf(cols, end)) put(i, w)
        }
        val cfg = settings[name]?.kids.orEmpty().associate { it["name"] to it.text() }
        fun frozen(axis: String) =
          if (cfg["${axis}SplitMode"] == "2") cfg["${axis}SplitPosition"]?.toIntOrNull() ?: 0 else 0
        val tab = props(table["style-name"], "table-properties")["tab-color"].rgb()?.let(::Color)
        result.spec =
          FluidGridSheet(
            name,
            lastRow,
            cols,
            frozen("Vertical").coerceIn(0, lastRow),
            frozen("Horizontal").coerceIn(0, cols),
            columnWidths = widths,
            rowHeights = heights,
            merges = merges,
            color = tab,
          )
        result
      },
    info,
  )
}
