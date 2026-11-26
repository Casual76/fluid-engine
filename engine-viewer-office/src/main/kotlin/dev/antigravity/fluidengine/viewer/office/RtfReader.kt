package dev.antigravity.fluidengine.viewer.office

import java.nio.charset.Charset

private data class Control(val name: String, val n: Int? = null)

private data class Group(val items: MutableList<Any> = mutableListOf()) {
  val dest: String?
    get() = items.filterIsInstance<Control>().firstOrNull { it.name != "*" }?.name

  fun arg(key: String) = items.filterIsInstance<Control>().firstOrNull { it.name == key }?.n

  fun groups(key: String) = items.filterIsInstance<Group>().filter { it.dest == key }

  fun plain(): String = buildString {
    for (v in items) when (v) {
      is String -> append(v)
      is Group -> append(v.plain())
      is Control ->
        when (v.name) {
          "u" -> append((v.n ?: 0).toChar())
          "tab" -> append('\t')
        }
    }
  }
}

private fun parseRtf(data: String): Group {
  if (!data.startsWith("{\\rtf")) damaged("Intestazione RTF mancante")
  val stack = ArrayDeque<Group>()
  val root = Group()
  stack.add(root)
  var i = 0
  var tokens = 0
  while (i < data.length) {
    if (++tokens > 500000 || stack.size > 96) tooLarge("Struttura RTF oltre il limite")
    val group = stack.last()
    when (val c = data[i++]) {
      '{' -> {
        val child = Group()
        group.items += child
        stack.add(child)
      }
      '}' -> {
        if (stack.size <= 1) damaged("Gruppi RTF non bilanciati")
        stack.removeLast()
      }
      '\r',
      '\n' -> Unit
      '\\' -> {
        if (i >= data.length) damaged("Comando RTF troncato")
        val ch = data[i++]
        when {
          ch == '\'' -> {
            val code =
              data.substring(i, minOf(data.length, i + 2)).toIntOrNull(16)
                ?: damaged("Byte RTF non valido")
            group.items += Control("byte", code)
            i += 2
          }
          ch in "\\{}" -> group.items += ch.toString()
          ch == '~' -> group.items += "\u00a0"
          !ch.isLetter() -> group.items += Control(ch.toString())
          else -> {
            val from = i - 1
            while (i < data.length && data[i].isLetter()) i++
            val word = data.substring(from, i)
            val start = i
            if (i < data.length && data[i] == '-') i++
            while (i < data.length && data[i].isDigit()) i++
            val arg = data.substring(start, i).toIntOrNull()
            if (i < data.length && data[i] == ' ') i++
            group.items += Control(word, arg)
            if (word == "bin") {
              val length = arg ?: damaged("Lunghezza RTF assente")
              if (length !in 0..(data.length - i)) damaged("Blocco RTF troncato")
              group.items += data.substring(i, i + length).toByteArray(Charsets.ISO_8859_1)
              i += length
            }
          }
        }
      }
      else -> {
        val start = i - 1
        while (i < data.length && data[i] !in "{}\\\r\n") i++
        group.items += data.substring(start, i)
      }
    }
  }
  if (stack.size != 1 || root.items.filterIsInstance<Group>().size != 1) damaged("RTF incompleto")
  return root.items.filterIsInstance<Group>().single()
}

internal fun readRtf(bytes: ByteArray, info: OfficeInfo): List<Section> {
  val root = parseRtf(bytes.toString(Charsets.ISO_8859_1))
  val fontTable = root.groups("fonttbl").firstOrNull()
  val fonts =
    fontTable?.items?.filterIsInstance<Group>().orEmpty().associate {
      it.arg("f") to it.plain().substringBefore(';').trim()
    }
  val charset =
    runCatching { Charset.forName("windows-" + (root.arg("ansicpg") ?: 1252)) }
      .getOrDefault(Charset.forName("windows-1252"))
  val colors = mutableListOf<Int>()
  var red = 0
  var green = 0
  var blue = 0
  for (v in root.groups("colortbl").firstOrNull()?.items.orEmpty()) when (v) {
    // R2: un valore fuori da 0..255 (\red999, fuori specifica) si taglia, come fanno i lettori di RTF; senza, i bit in
    // più di un canale finiscono negli altri e il colore esce sbagliato.
    is Control ->
      when (v.name) {
        "red" -> red = (v.n ?: 0).coerceIn(0, 255)
        "green" -> green = (v.n ?: 0).coerceIn(0, 255)
        "blue" -> blue = (v.n ?: 0).coerceIn(0, 255)
      }
    is String ->
      repeat(v.count { it == ';' }) {
        colors += android.graphics.Color.rgb(red, green, blue)
        red = 0
        green = 0
        blue = 0
      }
  }
  val defaultInk = Ink(font = fonts[root.arg("deff")] ?: "Times New Roman", pt = 12f)
  val styles =
    root
      .groups("stylesheet")
      .firstOrNull()
      ?.items
      ?.filterIsInstance<Group>()
      .orEmpty()
      .associateBy { it.arg("s") }
  fun styled(k: Ink, c: Control): Ink =
    when (c.name) {
      "f" -> k.copy(font = fonts[c.n] ?: k.font)
      "fs" -> k.copy(pt = (c.n ?: 24) / 2f)
      "b" -> k.copy(bold = c.n != 0)
      "i" -> k.copy(italic = c.n != 0)
      "ul",
      "ulnone" -> k.copy(underline = c.name == "ul" && c.n != 0)
      "strike" -> k.copy(strike = c.n != 0)
      "cf" -> k.copy(color = colors.getOrNull(c.n ?: 0) ?: k.color)
      "highlight" -> k.copy(highlight = colors.getOrNull(c.n ?: -1) ?: 0)
      "super",
      "sub",
      "nosupersub" ->
        k.copy(
          baseline =
            when (c.name) {
              "super" -> -1
              "sub" -> 1
              else -> 0
            }
        )
      "plain" -> defaultInk
      else -> k
    }
  var paper = Paper()
  val sections = mutableListOf<Section>()
  val blocks = mutableListOf<Block>()
  val runs = mutableListOf<Run>()
  val notes = mutableListOf<String>()
  var para = Paragraph(emptyList())
  var inTable = false
  val rows = mutableListOf<TableRow>()
  val cells = mutableListOf<Cell>()
  val paragraphs = mutableListOf<Paragraph>()
  val edges = mutableListOf<Float>()
  val rowEdges = mutableListOf<List<Float>>()
  val horizontalContinuation = mutableSetOf<Int>()
  val verticalStart = mutableSetOf<Int>()
  val verticalContinuation = mutableSetOf<Int>()
  var headerRow = false
  fun flushTable() {
    if (rows.isNotEmpty()) {
      val grid = rowEdges.flatten().distinct().sorted().take(256)
      val normalized =
        rows.mapIndexed { index, row ->
          var column = 0
          row.copy(
            cells =
              row.cells.mapIndexed { i, cell ->
                val right = rowEdges[index].getOrNull(i)
                val end = if (right == null) column + 1 else grid.indexOf(right) + 1
                cell.copy(span = (end - column).coerceAtLeast(1)).also { column = end }
              }
          )
        }
      blocks += Table(normalized, grid.mapIndexed { i, end -> end - (grid.getOrNull(i - 1) ?: 0f) })
      rows.clear()
      rowEdges.clear()
    }
  }
  fun flush() {
    val p = para.copy(runs = runs.toList(), notes = notes.toList())
    runs.clear()
    notes.clear()
    if (inTable) paragraphs += p
    else {
      flushTable()
      blocks += p
    }
  }
  fun endSection() {
    if (runs.isNotEmpty()) flush()
    flushTable()
    sections += Section(paper.checked(), blocks.toList())
    blocks.clear()
  }
  fun visit(g: Group, inherited: Ink, unicodeSkip: Int) {
    val dest = g.dest.orEmpty()
    val hdr =
      mapOf(
        "header" to "default",
        "headerf" to "first",
        "headerl" to "even",
        "headerr" to "default",
        "footer" to "default",
        "footerf" to "first",
        "footerl" to "even",
        "footerr" to "default",
      )
    if (dest in hdr) {
      val pair = hdr.getValue(dest) to g.plain()
      paper =
        if (dest.startsWith("header")) paper.copy(headers = paper.headers + pair)
        else paper.copy(footers = paper.footers + pair)
      return
    }
    if (dest == "footnote") {
      notes += g.plain()
      return
    }
    if (dest == "pict") {
      val raw =
        g.items.filterIsInstance<ByteArray>().firstOrNull()
          ?: run {
            val hex =
              g.items.filterIsInstance<String>().joinToString("").filterNot(Char::isWhitespace)
            if (hex.length > 8_000_000 || hex.length % 2 != 0) damaged("Immagine RTF non valida")
            ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
          }
      if (runs.isNotEmpty()) flush()
      blocks += Picture(raw, (g.arg("picwgoal") ?: 1800) / 20f, (g.arg("pichgoal") ?: 1200) / 20f)
      return
    }
    if (
      dest in
        setOf(
          "fonttbl",
          "colortbl",
          "stylesheet",
          "info",
          "listtable",
          "listoverridetable",
          "generator",
          "fldinst",
          "shpinst",
          "object",
          "datastore",
          "themedata",
          "xmlnstbl",
          "nonshppict",
        ) ||
        (g.items.firstOrNull() as? Control)?.name == "*" && dest !in setOf("shppict", "listtext")
    )
      return
    var ink = inherited
    var uc = unicodeSkip
    var skip = 0
    fun add(value: String) {
      val drop = minOf(skip, value.length)
      skip -= drop
      if (drop < value.length) runs += Run(value.substring(drop), ink).also(info::observe)
    }
    for (v in g.items) when (v) {
      is String -> add(v.toByteArray(Charsets.ISO_8859_1).toString(charset))
      is Group -> visit(v, ink, uc)
      is Control -> {
        val n = v.n ?: 0
        when (v.name) {
          "uc" -> uc = n.coerceIn(0, 16)
          "u" -> {
            skip = 0
            add(n.toChar().toString())
            skip = uc
          }
          "byte" -> add(byteArrayOf(n.toByte()).toString(charset))
          "tab" -> add("\t")
          "line" -> add("\n")
          "emdash" -> add("—")
          "endash" -> add("–")
          "bullet" -> add("•")
          "lquote",
          "rquote" -> add(if (v.name == "lquote") "‘" else "’")
          "ldblquote",
          "rdblquote" -> add(if (v.name == "ldblquote") "“" else "”")
          "par" -> flush()
          "page",
          "column" -> {
            if (runs.isNotEmpty()) flush()
            flushTable()
            blocks += PageBreak
          }
          "sect" -> endSection()
          "paperw",
          "pgwsxn" -> paper = paper.copy(width = n / 20f)
          "paperh",
          "pghsxn" -> paper = paper.copy(height = n / 20f)
          "margl",
          "marglsxn" -> paper = paper.copy(left = n / 20f)
          "margr",
          "margrsxn" -> paper = paper.copy(right = n / 20f)
          "margt",
          "margtsxn" -> paper = paper.copy(top = n / 20f)
          "margb",
          "margbsxn" -> paper = paper.copy(bottom = n / 20f)
          "titlepg" -> paper = paper.copy(firstDifferent = true)
          "pard" -> {
            para = Paragraph(emptyList())
            inTable = false
          }
          "s" -> styles[n]?.items?.filterIsInstance<Control>()?.forEach { ink = styled(ink, it) }
          "li" -> para = para.copy(left = n / 20f)
          "ri" -> para = para.copy(right = n / 20f)
          "fi" -> para = para.copy(indent = n / 20f)
          "sb" -> para = para.copy(before = n / 20f)
          "sa" -> para = para.copy(after = n / 20f)
          "qc",
          "qr",
          "ql",
          "qj" ->
            para =
              para.copy(
                align = alignment(mapOf("qc" to "center", "qr" to "right")[v.name]),
                justify = v.name == "qj",
              )
          "trowd" -> {
            inTable = true
            edges.clear()
            horizontalContinuation.clear()
            verticalStart.clear()
            verticalContinuation.clear()
            headerRow = false
          }
          "trhdr" -> headerRow = v.n != 0
          "clmrg" -> horizontalContinuation += edges.size
          "clvmgf" -> verticalStart += edges.size
          "clvmrg" -> verticalContinuation += edges.size
          "intbl" -> inTable = true
          "cellx" -> edges += n / 20f
          "cell" -> {
            inTable = true
            if (runs.isNotEmpty() || paragraphs.isEmpty()) flush()
            val index = cells.size
            cells +=
              Cell(
                paragraphs.toList(),
                continued = index in verticalContinuation,
                continues = index in verticalStart,
              )
            paragraphs.clear()
          }
          "row" -> {
            if (cells.isNotEmpty()) {
              val merged = mutableListOf<Cell>()
              val boundaries = mutableListOf<Float>()
              for ((i, cell) in cells.withIndex()) {
                val edge = edges.getOrNull(i) ?: (boundaries.lastOrNull() ?: 0f) + 72f
                if (i in horizontalContinuation && merged.isNotEmpty()) {
                  boundaries[boundaries.lastIndex] = edge
                } else {
                  merged += cell
                  boundaries += edge
                }
              }
              rows += TableRow(merged, headerRow)
              rowEdges += boundaries
            }
            cells.clear()
            inTable = false
          }
          else -> ink = styled(ink, v)
        }
      }
    }
  }
  val meta = root.groups("info").firstOrNull()
  for ((key, label) in
    listOf(
      "author" to "Autore",
      "title" to "Titolo",
      "creatim" to "Creato",
      "revtim" to "Modificato",
    )) {
    val field = meta?.groups(key)?.firstOrNull() ?: continue
    info.add(
      label,
      if (key.endsWith("tim")) rtfTimestamp(field)
      else field.plain(),
      "Documento",
    )
  }
  info.add(
    "Applicazione",
    root.groups("generator").firstOrNull()?.plain()?.trimEnd(';'),
    "Documento",
  )
  visit(root, defaultInk, 1)
  if (runs.isNotEmpty() || blocks.isNotEmpty() || sections.isEmpty()) endSection()
  return sections
}

private fun rtfTimestamp(field: Group): String {
  val date = listOf("yr", "mo", "dy").mapNotNull(field::arg)
  if (date.size != 3) return date.joinToString("-")
  val day = "%04d-%02d-%02d".format(java.util.Locale.ROOT, date[0], date[1], date[2])
  val hour = field.arg("hr") ?: return day
  val minute = field.arg("min") ?: 0
  return day + "T%02d:%02d:%02d".format(java.util.Locale.ROOT, hour, minute, field.arg("sec") ?: 0)
}
