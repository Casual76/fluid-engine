package dev.antigravity.fluidengine.viewer.data

import java.util.Locale

/** Come è scritto un CSV: il separatore e le virgolette (RFC 4180: le doppie, raddoppiate dentro un campo). */
internal data class CsvDialect(val delimiter: Char, val quote: Char = '"') {
  /** Per le persone: «; (punto e virgola)». */
  val label: String
    get() = when (delimiter) {
      ',' -> ", (virgola)"
      ';' -> "; (punto e virgola)"
      '\t' -> "tabulazione"
      '|' -> "| (barra verticale)"
      else -> "«$delimiter»"
    }
}

/**
 * F6C.13 · Il lettore CSV e TSV, RFC 4180, in Kotlin puro: riconosce il separatore (`,` `;` tab `|`), le virgolette
 * (anche con a capo e virgolette raddoppiate dentro), l'intestazione e i numeri. La lettura a flusso delle righe sta
 * in [DataCsvGrid]; qui ci sono le regole, uguali per il conteggio e per la lettura di un blocco.
 */
internal object DataCsv {
  private val Candidates = charArrayOf(',', ';', '\t', '|')

  /**
   * Il separatore di [sample] (le prime righe del file): quello che dà lo stesso numero di campi, maggiore di uno, nel
   * maggior numero di righe. Un file italiano di Excel («1,5;2,3») ha le virgole nei numeri e i punti e virgola fra i
   * campi: vince il punto e virgola, perché le sue righe tornano tutte uguali. A parità, il TSV preferisce la
   * tabulazione e gli altri la virgola.
   */
  fun sniff(sample: String, extension: String = ""): CsvDialect {
    val preferTab = extension == "tsv" || extension == "tab"
    var best: Char = if (preferTab) '\t' else ','
    var bestScore = -1.0
    for (candidate in Candidates) {
      val counts = records(sample, CsvDialect(candidate), maxRecords = 60, dropLastPartial = true).map { it.size }
      if (counts.isEmpty()) continue
      val mode = counts.groupingBy { it }.eachCount().maxWithOrNull(compareBy<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })!!
      if (mode.key < 2) continue
      val consistency = mode.value.toDouble() / counts.size
      // Più campi a pari coerenza vince (una virgola dentro un testo non fa una colonna in ogni riga).
      var score = consistency * 100 + minOf(mode.key, 50) * 0.01
      if (preferTab && candidate == '\t') score += 0.5
      if (score > bestScore + 1e-9) {
        bestScore = score
        best = candidate
      }
    }
    return CsvDialect(best)
  }

  /**
   * I record di [text]: un a capo fuori dalle virgolette chiude il record (`\r\n`, `\n`, o `\r` da solo nei file del
   * vecchio Mac); dentro le virgolette resta nel campo, e `""` è una virgoletta. Con [dropLastPartial] l'ultimo record,
   * forse tagliato dal campione, non conta.
   */
  fun records(text: String, dialect: CsvDialect, maxRecords: Int = Int.MAX_VALUE, dropLastPartial: Boolean = false): List<List<String>> {
    val out = ArrayList<List<String>>()
    val fields = ArrayList<String>()
    val field = StringBuilder()
    var inQuotes = false
    var quotedField = false
    var i = 0
    val n = text.length
    // R2: un campo smette di crescere oltre il tetto di una cella (un carattere in più basta a sapere che va tagliato):
    // un campo di centinaia di MB fra virgolette non si tiene tutto in memoria per mostrarne mille caratteri.
    fun add(c: Char) {
      if (field.length <= DataProfile.MaxCellChars) field.append(c)
    }
    fun endField() {
      fields += if (field.length > DataProfile.MaxCellChars) field.substring(0, DataProfile.MaxCellChars) + "…" else field.toString()
      field.setLength(0)
      quotedField = false
    }
    fun endRecord() {
      endField()
      out += fields.toList()
      fields.clear()
    }
    while (i < n && out.size < maxRecords) {
      val c = text[i]
      if (inQuotes) {
        if (c == dialect.quote) {
          if (i + 1 < n && text[i + 1] == dialect.quote) {
            add(c)
            i++
          } else {
            inQuotes = false
          }
        } else {
          add(c)
        }
      } else {
        when (c) {
          dialect.quote -> if (field.isEmpty() && !quotedField) {
            inQuotes = true
            quotedField = true
          } else {
            // Una virgoletta in mezzo a un campo senza virgolette resta com'è (file scritti a mano).
            add(c)
          }
          dialect.delimiter -> endField()
          '\r' -> {
            if (i + 1 < n && text[i + 1] == '\n') i++
            endRecord()
          }
          '\n' -> endRecord()
          else -> add(c)
        }
      }
      i++
    }
    if (out.size < maxRecords && (field.isNotEmpty() || fields.isNotEmpty() || quotedField)) {
      if (!dropLastPartial) endRecord()
    }
    return out
  }

  private val NumberPattern = Regex(
    """^[+\-\u2212]?[€$£¥]?\s?(\d{1,3}([.,'\u00A0\u202F ]\d{3})+|\d+)([.,]\d+)?([eE][+\-]?\d+)?\s?(%|€|\$|£|¥|EUR|USD)?$""",
  )

  /** Vero se [cell] è un numero come lo scrive una persona: «1.234,56», «-3.5e2», «12 %», «€ 4,20». Si allinea a destra. */
  fun looksNumeric(cell: String): Boolean {
    val t = cell.trim()
    if (t.isEmpty() || t.length > 40) return false
    return NumberPattern.matches(t)
  }

  /**
   * Vero se il primo record è un'intestazione. Come `csv.Sniffer` di Python, colonna per colonna:
   * - un'intestazione di testo sopra una colonna di numeri vota sì, un numero in testa vota no;
   * - una colonna di valori tutti lunghi uguali (codici, date) con la testa di un'altra lunghezza vota sì.
   * A pareggio decide l'aspetto: nomi brevi, senza cifre, tutti diversi dai valori sotto.
   */
  fun hasHeader(rows: List<List<String>>): Boolean {
    if (rows.size < 2) return false
    val head = rows[0]
    if (head.isEmpty() || head.any { it.isBlank() }) return false
    if (head.any { looksNumeric(it) }) return false
    val body = rows.drop(1).take(40)
    var votes = 0
    for (column in head.indices) {
      val values = body.mapNotNull { it.getOrNull(column)?.trim()?.takeIf { v -> v.isNotEmpty() } }
      if (values.isEmpty()) continue
      val numeric = values.count { looksNumeric(it) }
      if (numeric * 2 >= values.size) {
        votes++
        continue
      }
      val lengths = values.map { it.length }.toSet()
      if (lengths.size == 1) {
        if (head[column].trim().length != lengths.first()) votes++ else votes--
      }
    }
    if (votes != 0) return votes > 0
    val labels = head.all { h -> h.length <= 40 && h.none { it.isDigit() } }
    val distinct = head.map { it.lowercase(Locale.ROOT) }.toSet().size == head.size
    val repeated = head.indices.any { c -> body.any { it.getOrNull(c)?.trim().equals(head[c].trim(), ignoreCase = true) } }
    return labels && distinct && !repeated
  }

  /** La larghezza in dp di ogni colonna, dal testo più lungo delle prime righe. */
  fun columnWidths(rows: List<List<String>>): Map<Int, Float> {
    val widths = HashMap<Int, Float>()
    for (row in rows) {
      row.forEachIndexed { column, text ->
        val longest = text.lineSequence().maxOfOrNull { it.length } ?: 0
        val w = (longest * DataProfile.CsvCharWidth + 20f).coerceIn(DataProfile.CsvMinColumn, DataProfile.CsvMaxColumn)
        if (w > (widths[column] ?: 0f)) widths[column] = w
      }
    }
    return widths
  }
}
