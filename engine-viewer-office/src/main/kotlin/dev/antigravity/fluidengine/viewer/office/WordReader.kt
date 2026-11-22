package dev.antigravity.fluidengine.viewer.office

import android.graphics.Color

private fun Node.flag(tag: String, previous: Boolean) =
  child(tag)?.let { it["val"] !in setOf("0", "false", "none", "off") } ?: previous

internal class WordReader(private val pkg: OfficePackage, private val info: OfficeInfo) {
  private val path = "word/document.xml"
  private val rels = pkg.rels(path)

  private fun part(type: String) = pkg.target(path, type)?.let { pkg.xml(it) }

  private val styleFile = part("styles")
  private val styles =
    styleFile?.kids.orEmpty().filter { it.name == "style" }.associateBy { it["styleId"] }
  private val defaults = styleFile?.child("docDefaults")
  private val defaultStyle =
    styles.values.firstOrNull { it["type"] == "paragraph" && it["default"] == "1" }?.get("styleId")
  private val theme = part("theme")
  private val numbering = part("numbering")
  private val counters = mutableMapOf<String, IntArray>()
  private val footnotes = part("footnotes")?.kids.orEmpty().associateBy { it["id"] }
  private val endnotes = part("endnotes")?.kids.orEmpty().associateBy { it["id"] }
  private val end = linkedMapOf<String, String>()

  private fun chain(id: String?): List<Node> {
    val nodes = mutableListOf<Node>()
    var current = styles[id]
    while (current != null && current !in nodes && nodes.size < 32) {
      nodes += current
      current = styles[current.child("basedOn")?.get("val")]
    }
    return nodes.reversed()
  }

  private fun ink(original: Ink, r: Node?): Ink {
    if (r == null) return original
    val font = r.child("rFonts")
    val themed = font?.get("asciiTheme") ?: font?.get("hAnsiTheme")
    val themeFont =
      themed?.let {
        theme
          ?.first(if (it.startsWith("major")) "majorFont" else "minorFont")
          ?.child("latin")
          ?.get("typeface")
      }
    val highlight =
      r.child("highlight")?.get("val")?.let { name ->
        runCatching { Color.parseColor(name) }.getOrNull()
      }
    return original.copy(
      font = font?.get("ascii") ?: font?.get("hAnsi") ?: themeFont ?: original.font,
      pt = r.child("sz")?.get("val")?.toFloatOrNull()?.div(2) ?: original.pt,
      bold = r.flag("b", original.bold),
      italic = r.flag("i", original.italic),
      underline = r.flag("u", original.underline),
      strike = r.flag("strike", original.strike),
      color = r.child("color")?.get("val").rgb() ?: original.color,
      highlight = highlight ?: original.highlight,
      baseline =
        when (r.child("vertAlign")?.get("val")) {
          "superscript" -> -1
          "subscript" -> 1
          else -> original.baseline
        },
    )
  }

  private fun label(num: Node): Pair<String, Float> {
    val id = num.child("numId")?.get("val") ?: return "" to 0f
    if (id == "0") return "" to 0f
    val level = (num.child("ilvl")?.get("val")?.toIntOrNull() ?: 0).coerceIn(0, 8)
    val instance = numbering?.kids?.firstOrNull { it.name == "num" && it["numId"] == id }
    val abs = instance?.child("abstractNumId")?.get("val")
    val levels =
      numbering
        ?.kids
        ?.firstOrNull { it.name == "abstractNum" && it["abstractNumId"] == abs }
        ?.kids
        .orEmpty()
    fun spec(i: Int) = levels.firstOrNull { it.name == "lvl" && it["ilvl"] == i.toString() }
    val count = counters.getOrPut(id) { IntArray(9) }
    val override = instance?.kids?.firstOrNull { it["ilvl"] == level.toString() }
    count[level] =
      if (count[level] == 0)
        override?.child("startOverride")?.get("val")?.toIntOrNull()
          ?: spec(level)?.child("start")?.get("val")?.toIntOrNull()
          ?: 1
      else count[level] + 1
    for (i in level + 1..8) count[i] = 0
    var text = spec(level)?.child("lvlText")?.get("val") ?: "%${level+1}."
    if (spec(level)?.child("numFmt")?.get("val") == "bullet") text = "•"
    for (i in 0..level) {
      val v = count[i].coerceAtLeast(1)
      text = text.replace("%${i+1}", numberLabel(v, spec(i)?.child("numFmt")?.get("val")))
    }
    return "$text  " to (spec(level)?.first("ind")?.get("left").twips(18f * (level + 1)))
  }

  private fun paragraph(node: Node): List<Block> {
    val pr = node.child("pPr")
    val base = chain(pr?.child("pStyle")?.get("val") ?: defaultStyle)
    val props =
      listOfNotNull(defaults?.first("pPr")) +
        base.mapNotNull { it.child("pPr") } +
        listOfNotNull(pr)
    fun value(tag: String, key: String) =
      props.asReversed().firstNotNullOfOrNull { it.child(tag)?.get(key) }
    val defaultInk =
      base.fold(ink(Ink(), defaults?.first("rPr"))) { a, s -> ink(a, s.child("rPr")) }
    val num = props.asReversed().firstNotNullOfOrNull { it.child("numPr") }?.let(::label)
    val left = value("ind", "left").twips(num?.second ?: 0f)
    val notes = mutableListOf<String>()
    val chunks = mutableListOf<Block>()
    val runs = mutableListOf<Run>()
    fun flush() {
      val alignment = value("jc", "val")
      chunks +=
        Paragraph(
          runs.toList(),
          alignment(alignment),
          value("spacing", "before").twips(),
          value("spacing", "after").twips(),
          left,
          value("ind", "right").twips(),
          value("ind", "firstLine").twips() - value("ind", "hanging").twips(),
          value("spacing", "line")?.toFloatOrNull()?.let {
            if (value("spacing", "lineRule") in listOf("exact", "atLeast"))
              it / (20 * defaultInk.pt)
            else it / 240
          } ?: 1f,
          alignment == "both",
          notes.toList(),
          props.any { it.flag("keepLines", false) },
        )
      notes.clear()
      runs.clear()
    }
    if (props.any { it.flag("pageBreakBefore", false) }) chunks += PageBreak
    if (num != null) runs += Run(num.first, defaultInk)
    fun visit(n: Node, style: Ink) {
      when (n.name) {
        "del",
        "pPr",
        "rPr",
        "instrText",
        "sectPr" -> return
        "t" -> runs += Run(n.text(), style).also(info::observe)
        "tab" -> runs += Run("\t", style)
        "br",
        "cr" ->
          if (n["type"] in listOf("page", "column")) {
            if (runs.isNotEmpty()) flush()
            chunks += PageBreak
          } else runs += Run("\n", style)
        "footnoteReference",
        "endnoteReference" -> {
          val id = n["id"].orEmpty()
          val fn = n.name == "footnoteReference"
          val body =
            (if (fn) footnotes[id] else endnotes[id])
              ?.all("t")
              ?.joinToString("") { it.text() }
              .orEmpty()
          runs += Run(id, style.copy(baseline = -1))
          if (fn) notes += "$id  $body" else end[id] = "$id  $body"
        }
        "drawing",
        "pict" -> {
          val embed = n.first("blip")?.get("embed") ?: n.first("imagedata")?.get("id")
          val bytes = rels[embed]?.path?.let { pkg.bytes(it) }
          if (bytes != null) {
            if (runs.isNotEmpty()) flush()
            val extent = n.first("extent")
            chunks += Picture(bytes, emu(extent?.get("cx"), 120f), emu(extent?.get("cy"), 90f))
          } else {
            info.omitted += "Disegno o grafico Word"
            runs += Run("[Disegno]", style)
          }
        }
        "r" -> {
          val r = n.child("rPr")
          val s =
            chain(r?.child("rStyle")?.get("val")).fold(style) { a, v -> ink(a, v.child("rPr")) }
          n.kids.forEach { visit(it, ink(s, r)) }
        }
        else -> n.kids.forEach { visit(it, style) }
      }
    }
    visit(node, defaultInk)
    if (runs.isNotEmpty() || chunks.isEmpty()) flush()
    return chunks
  }

  private fun table(node: Node): Table {
    val rows =
      node.kids
        .filter { it.name == "tr" }
        .map { row ->
          TableRow(
            row.kids
              .filter { it.name == "tc" }
              .map { c ->
                val pr = c.child("tcPr")
                Cell(
                  c.kids
                    .filter { it.name == "p" }
                    .flatMap(::paragraph)
                    .filterIsInstance<Paragraph>(),
                  (pr?.child("gridSpan")?.get("val")?.toIntOrNull() ?: 1).coerceIn(1, 256),
                  pr?.child("shd")?.get("fill").rgb(),
                  pr?.child("vMerge")?.let { it["val"] != "restart" } ?: false,
                )
              },
            row.child("trPr")?.flag("tblHeader", false) ?: false,
          )
        }
    val joined =
      rows.mapIndexed { i, row ->
        var col = 0
        val below = mutableSetOf<Int>()
        var j = 0
        for (c in rows.getOrNull(i + 1)?.cells.orEmpty()) {
          if (c.continued) below += j
          j += c.span
        }
        row.copy(
          cells = row.cells.map { c -> c.copy(continues = col in below).also { col += c.span } }
        )
      }
    return Table(joined, node.child("tblGrid")?.kids.orEmpty().map { it["w"].twips(72f) })
  }

  private fun margin(pr: Node?, tag: String, previous: Map<String, String>): Map<String, String> =
    previous.toMutableMap().also { result ->
      for (ref in pr?.kids.orEmpty().filter { it.name == tag }) {
        val xml = rels[ref["id"]]?.path?.let { pkg.xml(it) } ?: continue
        result[ref["type"] ?: "default"] =
          xml.all("p").joinToString("\n") { p ->
            var instruction = ""
            var fieldValue = false
            buildString {
              for (r in p.kids) {
                if (r.name == "fldSimple") {
                  val field = r["instr"]?.trim()?.substringBefore(' ')
                  append(if (field in setOf("PAGE", "NUMPAGES")) "{$field}" else r.text())
                } else
                  for (part in r.kids) when (part.name) {
                    "instrText" -> instruction += part.text()
                    "fldChar" ->
                      when (part["fldCharType"]) {
                        "begin" -> {
                          instruction = ""
                          fieldValue = false
                        }
                        "separate" -> {
                          val field = instruction.trim().substringBefore(' ')
                          fieldValue = field in setOf("PAGE", "NUMPAGES")
                          if (fieldValue) append("{$field}")
                        }
                        "end" -> fieldValue = false
                      }
                    "t" -> if (!fieldValue) append(part.text())
                  }
              }
            }
          }
      }
    }

  fun read(): List<Section> {
    val doc = pkg.required(path)
    val body = doc.child("body") ?: damaged("Corpo Word mancante")
    val sections = mutableListOf<Section>()
    val blocks = mutableListOf<Block>()
    var prev = Paper()
    fun close(pr: Node?) {
      val size = pr?.child("pgSz")
      val m = pr?.child("pgMar")
      val paper =
        Paper(
            size?.get("w").twips(prev.width),
            size?.get("h").twips(prev.height),
            m?.get("left").twips(prev.left),
            m?.get("top").twips(prev.top),
            m?.get("right").twips(prev.right),
            m?.get("bottom").twips(prev.bottom),
            margin(pr, "headerReference", prev.headers),
            margin(pr, "footerReference", prev.footers),
            pr?.flag("titlePg", false) ?: false,
            m?.get("header").twips(36f),
            m?.get("footer").twips(36f),
          )
          .checked()
      if ((pr?.child("cols")?.get("num")?.toIntOrNull() ?: 1) > 1)
        info.omitted += "Colonne impaginate in una colonna"
      sections += Section(paper, blocks.toList())
      blocks.clear()
      prev = paper
    }
    for (node in body.kids) when (node.name) {
      "p" -> {
        blocks += paragraph(node)
        node.child("pPr")?.child("sectPr")?.let(::close)
      }
      "tbl" -> blocks += table(node)
      "sectPr" -> {
        blocks += end.values.map { simpleParagraph(it, Ink(pt = 9f)) }
        close(node)
      }
      else -> info.omitted += "Blocco Word: ${node.name}"
    }
    if (blocks.isNotEmpty() || sections.isEmpty()) close(null)
    if (doc.first("commentRangeStart") != null) info.omitted += "Commenti"
    if (doc.first("ins") != null || doc.first("del") != null)
      info.omitted += "Revisioni: mostrato il testo finale"
    return sections
  }
}

internal fun emu(value: String?, default: Float) =
  value?.toLongOrNull()?.div(12700f)?.coerceIn(.1f, 10000f) ?: default

internal fun numberLabel(n: Int, format: String?): String {
  val lower =
    when (format) {
      "upperLetter",
      "lowerLetter" -> {
        var num = n
        buildString {
            while (num > 0) {
              append('a' + (num - 1) % 26)
              num = (num - 1) / 26
            }
          }
          .reversed()
      }
      "upperRoman",
      "lowerRoman" -> {
        var num = n.coerceAtMost(3999)
        buildString {
          for ((v, s) in
            listOf(
              1000 to "m",
              900 to "cm",
              500 to "d",
              400 to "cd",
              100 to "c",
              90 to "xc",
              50 to "l",
              40 to "xl",
              10 to "x",
              9 to "ix",
              5 to "v",
              4 to "iv",
              1 to "i",
            )) while (num >= v) {
            append(s)
            num -= v
          }
        }
      }
      else -> n.toString()
    }
  return if (format?.startsWith("upper") == true) lower.uppercase() else lower
}
