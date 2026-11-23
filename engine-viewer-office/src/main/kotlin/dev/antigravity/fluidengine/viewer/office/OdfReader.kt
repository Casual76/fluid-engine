package dev.antigravity.fluidengine.viewer.office

internal class OdfReader(val pkg: OfficePackage, val info: OfficeInfo) {
  val content = pkg.required("content.xml")
  private val common = pkg.xml("styles.xml")
  val styles = (common?.all("style").orEmpty() + content.all("style")).associateBy { it["name"] }
  val masters = common?.all("master-page").orEmpty().associateBy { it["name"] }
  private val layouts = common?.all("page-layout").orEmpty().associateBy { it["name"] }
  private val faces =
    (common?.all("font-face").orEmpty() + content.all("font-face")).associate {
      it["name"] to it["font-family"]?.trim('\'', '"')
    }

  fun props(style: String?, tag: String): Map<String, String> {
    val parents = mutableListOf<Node>()
    var n = styles[style]
    while (n != null && n !in parents && parents.size < 32) {
      parents += n
      n = styles[n["parent-style-name"]]
    }
    return buildMap {
      for (element in parents.asReversed()) element.child(tag)?.let { putAll(it.attrs) }
    }
  }

  fun paper(master: Node?): Paper {
    val p = layouts[master?.get("page-layout-name")]?.child("page-layout-properties")
    val m = p?.get("margin").points(56.7f)
    fun margin(tag: String) = buildMap {
      for ((type, name) in
        listOf("default" to tag, "first" to "$tag-first", "even" to "$tag-left")) {
        master?.child(name)?.let {
          if (it["display"] != "false")
            put(type, it.all("p").joinToString("\n") { para -> odfText(para) })
        }
      }
    }
    val headers = margin("header")
    val footers = margin("footer")
    return Paper(
        p?.get("page-width").points(595.28f),
        p?.get("page-height").points(841.89f),
        p?.get("margin-left").points(m),
        p?.get("margin-top").points(m),
        p?.get("margin-right").points(m),
        p?.get("margin-bottom").points(m),
        headers,
        footers,
        "first" in headers || "first" in footers,
      )
      .checked()
  }

  fun picture(n: Node): Picture? {
    val image = n.first("image") ?: return null
    val path = image["href"]?.removePrefix("./") ?: return null
    val bytes = pkg.bytes(path) ?: return null
    return Picture(
      bytes,
      n["width"].points(100f).coerceAtLeast(1f),
      n["height"].points(75f).coerceAtLeast(1f),
    )
  }

  fun ink(style: String?, parent: Ink): Ink {
    val p = props(style, "text-properties")
    val font = faces[p["font-name"]] ?: p["font-name"] ?: p["font-family"]?.trim('\'')
    val size = p["font-size"]
    return parent.copy(
      font = font ?: parent.font,
      pt =
        if (size?.endsWith('%') == true)
          parent.pt * (size.dropLast(1).toFloatOrNull() ?: 100f) / 100
        else size.points(parent.pt),
      bold = p["font-weight"]?.let { it == "bold" } ?: parent.bold,
      italic = p["font-style"]?.let { it == "italic" } ?: parent.italic,
      color = p["color"].rgb() ?: parent.color,
      highlight = p["background-color"].rgb() ?: parent.highlight,
      underline = p["text-underline-style"]?.let { it != "none" } ?: parent.underline,
      strike = p["text-line-through-style"]?.let { it != "none" } ?: parent.strike,
    )
  }

  fun para(n: Node, label: String = "", level: Int = 0): Paragraph {
    val style = n["style-name"]
    val p = props(style, "paragraph-properties")
    val base =
      ink(
        style,
        Ink(font = "Times New Roman", pt = if (n.name == "h") 18f else 12f, bold = n.name == "h"),
      )
    val runs = mutableListOf<Run>()
    val notes = mutableListOf<String>()
    fun walk(node: Node, k: Ink) {
      fun add(s: String) {
        runs += Run(s, k).also(info::observe)
      }
      for (v in node.content) when (v) {
        is String -> add(v)
        is Node ->
          when (v.name) {
            "s" -> add(" ".repeat((v["c"]?.toIntOrNull() ?: 1).coerceIn(1, 4096)))
            "tab" -> add("\t")
            "line-break" -> add("\n")
            "note" -> {
              val ref = v.child("note-citation")?.text().orEmpty()
              add(ref)
              notes += "$ref  " + v.child("note-body")?.text().orEmpty()
            }
            "frame",
            "soft-page-break" -> Unit
            else -> walk(v, ink(v["style-name"], k))
          }
      }
    }
    if (label.isNotEmpty()) runs += Run(label, base)
    walk(n, base)
    return Paragraph(
      runs,
      alignment(p["text-align"]),
      p["margin-top"].points(),
      p["margin-bottom"].points(),
      p["margin-left"].points(level * 14f),
      p["margin-right"].points(),
      p["text-indent"].points(),
      p["line-height"]?.removeSuffix("%")?.toFloatOrNull()?.div(100) ?: 1f,
      p["text-align"] == "justify",
      notes,
    )
  }

  fun document(): List<Section> {
    val body = content.child("body")?.child("text") ?: damaged("Corpo ODT mancante")
    var sheet = paper(masters.values.firstOrNull())
    val sections = mutableListOf<Section>()
    val blocks = mutableListOf<Block>()
    val lists =
      (common?.all("list-style").orEmpty() + content.all("list-style")).associateBy { it["name"] }
    fun visit(n: Node, depth: Int = 0, label: String = "", list: String? = null) {
      when (n.name) {
        "h",
        "p" -> {
          val style = n["style-name"]
          val master = styles[style]?.get("master-page-name")
          if (master in masters && master != null) {
            if (blocks.isNotEmpty()) {
              sections += Section(sheet, blocks.toList())
              blocks.clear()
            }
            sheet = paper(masters[master])
          } else if (
            props(style, "paragraph-properties")["break-before"] == "page" && blocks.isNotEmpty()
          )
            blocks += PageBreak
          blocks += para(n, label, depth)
          for (f in n.all("frame")) picture(f)?.let(blocks::add)
        }
        "list" -> {
          val listName = n["style-name"] ?: list
          val level = lists[listName]?.kids?.firstOrNull { it["level"] == (depth + 1).toString() }
          var i = level?.get("start-value")?.toIntOrNull() ?: 1
          for (item in n.kids.filter { it.name == "list-item" }) {
            var prefix =
              if (level?.name == "list-level-style-number")
                numberLabel(
                  i,
                  mapOf(
                    "a" to "lowerLetter",
                    "A" to "upperLetter",
                    "i" to "lowerRoman",
                    "I" to "upperRoman",
                  )[level["num-format"]],
                ) + (level["num-suffix"] ?: ".") + " "
              else "• "
            for (child in item.kids) {
              visit(child, depth + 1, prefix, listName)
              prefix = ""
            }
            i++
          }
        }
        "table" -> {
          val vertical = mutableMapOf<Int, Int>()
          val rows =
            n.all("table-row")
              .mapIndexed { index, row ->
                var column = 0
                var horizontallyCovered = 0
                val cells = buildList {
                  for (c in row.kids) when (c.name) {
                    "table-cell" -> {
                      val span = (c["number-columns-spanned"]?.toIntOrNull() ?: 1).coerceIn(1, 256)
                      val height = (c["number-rows-spanned"]?.toIntOrNull() ?: 1).coerceIn(1, 3000)
                      if (height > 1) vertical[column] = index + height - 1
                      add(
                        Cell(
                          c.kids.filter { it.name == "p" || it.name == "h" }.map { para(it) },
                          span,
                          props(c["style-name"], "table-cell-properties")["background-color"].rgb(),
                          continues = height > 1,
                        )
                      )
                      horizontallyCovered = span - 1
                      column += span
                    }
                    "covered-table-cell" ->
                      repeat((c["number-columns-repeated"]?.toIntOrNull() ?: 1).coerceIn(1, 256)) {
                        if (horizontallyCovered > 0) horizontallyCovered--
                        else {
                          add(
                            Cell(
                              emptyList(),
                              continued = true,
                              continues = (vertical[column] ?: index) > index,
                            )
                          )
                          column++
                        }
                      }
                  }
                }
                TableRow(cells)
              }
              .toList()
          val widths = buildList {
            for (col in n.kids.filter { it.name == "table-column" }) {
              val width =
                props(col["style-name"], "table-column-properties")["column-width"].points(72f)
              repeat((col["number-columns-repeated"]?.toIntOrNull() ?: 1).coerceIn(1, 256 - size)) {
                add(width)
              }
              if (size >= 256) break
            }
          }
          blocks += Table(rows, widths)
        }
        "frame" -> picture(n)?.let(blocks::add)
        "section",
        "text" -> n.kids.forEach { visit(it) }
      }
    }
    visit(body)
    sections += Section(sheet, blocks.toList())
    return sections
  }
}

internal fun odfText(node: Node): String = buildString {
  for (v in node.content) when (v) {
    is String -> append(v)
    is Node ->
      when (v.name) {
        "page-number" -> append("{PAGE}")
        "page-count" -> append("{NUMPAGES}")
        "s" -> append(" ".repeat((v["c"]?.toIntOrNull() ?: 1).coerceIn(1, 4096)))
        "tab" -> append('\t')
        "line-break" -> append('\n')
        else -> append(odfText(v))
      }
  }
}
