package dev.antigravity.fluidengine.viewer.office

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

private data class Frame(val x: Float, val y: Float, val w: Float, val h: Float)

private fun slideShape(rect: Frame, kind: String, fill: Int?, stroke: Int?, weight: Float) =
  Mark { c ->
    val rectF = android.graphics.RectF(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h)
    val path = Path()
    fun polygon(vararg points: Pair<Float, Float>) {
      for ((index, point) in points.withIndex()) {
        val x = rect.x + rect.w * point.first
        val y = rect.y + rect.h * point.second
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
      }
      path.close()
    }
    when (kind) {
      "ellipse" -> path.addOval(rectF, Path.Direction.CW)
      "roundRect" -> {
        val radius = minOf(rect.w, rect.h) / 6
        path.addRoundRect(rectF, radius, radius, Path.Direction.CW)
      }
      "triangle" -> polygon(.5f to 0f, 1f to 1f, 0f to 1f)
      "diamond" -> polygon(.5f to 0f, 1f to .5f, .5f to 1f, 0f to .5f)
      "rightArrow",
      "leftArrow" -> {
        val head = if (kind == "rightArrow") 1f else 0f
        val neck = if (kind == "rightArrow") .65f else .35f
        val tail = 1f - head
        polygon(
          tail to .25f,
          neck to .25f,
          neck to 0f,
          head to .5f,
          neck to 1f,
          neck to .75f,
          tail to .75f,
        )
      }
      "line",
      "straightConnector1" -> {
        path.moveTo(rect.x, rect.y)
        path.lineTo(rectF.right, rectF.bottom)
      }
      else -> path.addRect(rectF, Path.Direction.CW)
    }
    val pen = Paint(Paint.ANTI_ALIAS_FLAG)
    if (fill != null) {
      pen.color = fill
      c.drawPath(path, pen)
    }
    if (stroke != null) {
      pen.style = Paint.Style.STROKE
      pen.strokeWidth = weight
      pen.color = stroke
      c.drawPath(path, pen)
    }
  }

private fun paragraphMarks(
  paragraphs: List<Paragraph>,
  rect: Frame,
  fonts: OfficeFonts,
  inset: Float = 3f,
  anchor: String? = null,
): Mark {
  val width = (rect.w - 2 * inset).coerceAtLeast(1f)
  var y = rect.y + inset
  val marks =
    paragraphs.map { p ->
      val l = layout(p, width - p.textLeft - p.right, fonts)
      y += p.before
      textMark(l, rect.x + inset + p.textLeft, y).also { y += l.height / Units + p.after }
    }
  return Mark { c ->
    val save = c.save()
    c.clipRect(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h)
    val free = (rect.y + rect.h - inset - y).coerceAtLeast(0f)
    c.translate(
      0f,
      when (anchor) {
        "ctr" -> free / 2
        "b" -> free
        else -> 0f
      },
    )
    marks.forEach { it.draw(c) }
    c.restoreToCount(save)
  }
}

private fun placeholder(
  label: String,
  rect: Frame,
  fonts: OfficeFonts,
  info: OfficeInfo,
): List<Mark> {
  info.omitted += "$label: segnaposto"
  return listOf(
    boxMark(rect.x, rect.y, rect.w, rect.h, 0xffedf0f2.toInt(), 0xff84888e.toInt()),
    paragraphMarks(listOf(simpleParagraph(label, Ink(pt = 13f))), rect, fonts),
  )
}

internal class PowerPointReader(
  private val pkg: OfficePackage,
  private val info: OfficeInfo,
  private val fonts: OfficeFonts,
) {
  private val cache = mutableMapOf<String, Node>()

  private fun node(path: String) = cache.getOrPut(path) { pkg.required(path) }

  private fun color(n: Node?, theme: Node?): Int? {
    if (n?.name in setOf("lnRef", "fillRef") && n?.get("idx") in setOf("0", "1000")) return null
    val fill =
      n?.child("solidFill")
        ?: n?.takeIf { it.name in setOf("lnRef", "fillRef", "gs") }
        ?: return null
    fill.child("srgbClr")?.get("val").rgb()?.let {
      return it
    }
    val key =
      when (val v = fill.child("schemeClr")?.get("val")) {
        "tx1" -> "dk1"
        "bg1" -> "lt1"
        "tx2" -> "dk2"
        "bg2" -> "lt2"
        else -> v
      }
    val c = theme?.first("clrScheme")?.kids?.firstOrNull { it.name == key }?.kids?.firstOrNull()
    return (c?.get("lastClr") ?: c?.get("val")).rgb()
  }

  private fun frame(n: Node?): Frame? {
    val tr = n?.child("spPr")?.child("xfrm") ?: n?.child("xfrm") ?: return null
    val off = tr.child("off") ?: return null
    val ext = tr.child("ext") ?: return null
    return Frame(
      (off["x"]?.toLongOrNull() ?: 0) / 12700f,
      (off["y"]?.toLongOrNull() ?: 0) / 12700f,
      emu(ext["cx"], 40f),
      emu(ext["cy"], 20f),
    )
  }

  private fun shapes(root: Node?): List<Node> {
    val supported = setOf("sp", "pic", "graphicFrame", "cxnSp", "grpSp")
    return root?.child("cSld")?.child("spTree")?.kids.orEmpty().flatMap { shape ->
      if (shape.name == "AlternateContent") {
        val branch =
          shape.kids.firstOrNull {
            it.name == "Choice" && it.kids.any { item -> item.name in supported }
          } ?: shape.child("Fallback")
        branch?.kids.orEmpty().filter { it.name in supported }
      } else listOfNotNull(shape.takeIf { it.name in supported })
    }
  }

  private fun match(shape: Node, candidates: List<Node>): Node? {
    val ph = shape.first("ph") ?: return null
    return candidates.firstOrNull {
      it.first("ph")?.let { other ->
        (ph["idx"] != null && ph["idx"] == other["idx"]) ||
          (ph["idx"] == null && (other["type"] ?: "obj") == (ph["type"] ?: "obj"))
      } == true
    }
  }

  fun read(): List<OfficePage> {
    val owner = "ppt/presentation.xml"
    val pres = node(owner)
    val size = pres.child("sldSz")
    val paper =
      Paper(emu(size?.get("cx"), 720f), emu(size?.get("cy"), 405f), 0f, 0f, 0f, 0f).checked()
    return pres
      .child("sldIdLst")
      ?.kids
      .orEmpty()
      .mapIndexed { index, item ->
        val file = pkg.rels(owner)[item["r:id"]]?.path ?: damaged("Slide senza relazione")
        val slide = node(file)
        val layoutPath = pkg.target(file, "slideLayout")
        val layout = layoutPath?.let(::node)
        val masterPath = layoutPath?.let { pkg.target(it, "slideMaster") }
        val master = masterPath?.let(::node)
        val theme = masterPath?.let { pkg.target(it, "theme") }?.let(::node)
        val masterShapes = shapes(master)
        val layoutShapes = shapes(layout)
        val marks = mutableListOf<Mark>()
        val bg =
          listOf(slide, layout, master).firstNotNullOfOrNull {
            color(it?.child("cSld")?.child("bg")?.child("bgPr"), theme)
          }
        if (bg != null) marks += boxMark(0f, 0f, paper.width, paper.height, bg)
        fun draw(s: Node, source: String, l: Node?, m: Node?, target: MutableList<Mark>) {
          if (s.name == "grpSp") {
            val local = mutableListOf<Mark>()
            for (child in
              s.kids.filter { it.name in setOf("sp", "pic", "graphicFrame", "grpSp") }) draw(
              child,
              source,
              null,
              null,
              local,
            )
            val tr = s.child("grpSpPr")?.child("xfrm")
            val off = tr?.child("off")
            val ext = tr?.child("ext")
            val co = tr?.child("chOff")
            val ce = tr?.child("chExt")
            fun unit(n: Node?, key: String) = (n?.get(key)?.toLongOrNull() ?: 0) / 12700f
            target += Mark { canvas ->
              val save = canvas.save()
              if (unit(ce, "cx") > 0 && unit(ce, "cy") > 0) {
                canvas.translate(unit(off, "x"), unit(off, "y"))
                canvas.scale(unit(ext, "cx") / unit(ce, "cx"), unit(ext, "cy") / unit(ce, "cy"))
                canvas.translate(-unit(co, "x"), -unit(co, "y"))
              }
              local.forEach { it.draw(canvas) }
              canvas.restoreToCount(save)
            }
            return
          }
          val rect = frame(s) ?: frame(l) ?: frame(m) ?: return
          val p = s.child("spPr")
          val style = s.child("style")
          val fill =
            if (p?.child("noFill") != null) null
            else
              color(p, theme)
                ?: color(p?.child("gradFill")?.first("gs"), theme)
                ?: color(style?.child("fillRef"), theme)
          val line =
            if (p?.child("ln")?.child("noFill") != null) null
            else color(p?.child("ln"), theme) ?: color(style?.child("lnRef"), theme)
          val geometry =
            p?.child("prstGeom")?.get("prst") ?: if (s.name == "cxnSp") "line" else "rect"
          if (
            geometry !in
              setOf(
                "rect",
                "ellipse",
                "roundRect",
                "triangle",
                "diamond",
                "rightArrow",
                "leftArrow",
                "line",
                "straightConnector1",
              )
          )
            info.omitted += "Geometria $geometry mostrata nel suo riquadro"
          if (p?.child("gradFill") != null) info.omitted += "Sfumatura della forma semplificata"
          if (fill != null || line != null)
            target += slideShape(rect, geometry, fill, line, emu(p?.child("ln")?.get("w"), .75f))
          val image =
            s.first("blip")?.get("embed")?.let { pkg.rels(source)[it] }?.path?.let(pkg::bytes)
          if (image != null)
            target += imageMark(Picture(image, rect.w, rect.h), rect.x, rect.y, rect.w, rect.h)
          val data = s.first("graphicData")
          if (data != null) {
            val table = data.child("tbl")
            if (table != null) {
              val widths = table.child("tblGrid")?.kids.orEmpty().map { emu(it["w"], 40f) }
              val rows = table.kids.filter { it.name == "tr" }
              val heights = rows.map { emu(it["h"], 20f) }
              var y = rect.y
              for ((i, row) in rows.withIndex()) {
                val h = heights[i] * rect.h / heights.sum()
                var x = rect.x
                for ((j, c) in row.kids.filter { it.name == "tc" }.withIndex()) {
                  val w = (widths.getOrNull(j) ?: 1f) * rect.w / widths.sum().coerceAtLeast(1f)
                  target += boxMark(x, y, w, h, color(c.child("tcPr"), theme), Color.GRAY)
                  target +=
                    paragraphMarks(
                      listOf(simpleParagraph(c.all("t").joinToString(" ") { it.text() })),
                      Frame(x, y, w, h),
                      fonts,
                    )
                  x += w
                }
                y += h
              }
            } else {
              val chart =
                data.first("chart")?.get("id")?.let { pkg.rels(source)[it]?.path }?.let(::node)
              val title = chart?.first("title")?.all("t")?.joinToString(" ") { it.text() }
              val label =
                when {
                  data["uri"]?.contains("diagram") == true -> "SmartArt"
                  chart != null -> if (title.isNullOrBlank()) "Grafico" else "Grafico · $title"
                  else -> "Oggetto incorporato"
                }
              target += placeholder(label, rect, fonts, info)
            }
          }
          val title =
            s.first("ph")?.get("type") in setOf("title", "ctrTitle") ||
              l?.first("ph")?.get("type") in setOf("title", "ctrTitle")
          val masterTextStyle = when {
            title -> "titleStyle"
            s.first("ph") == null -> "otherStyle"
            else -> "bodyStyle"
          }
          val body = s.child("txBody") ?: return
          val paras =
            body.kids
              .filter { it.name == "p" }
              .map { para ->
                val pp = para.child("pPr")
                val level = (pp?.get("lvl")?.toIntOrNull() ?: 0) + 1
                val style =
                  master?.first(masterTextStyle)?.child("lvl${level}pPr")
                val defaults =
                  listOfNotNull(
                    style?.child("defRPr"),
                    m?.first("defRPr"),
                    l?.first("defRPr"),
                    pp?.child("defRPr"),
                  )
                val runs =
                  para.kids.mapNotNull { run ->
                    if (run.name == "br") return@mapNotNull Run("\n")
                    val t = run.child("t") ?: return@mapNotNull null
                    val props = (defaults + listOfNotNull(run.child("rPr"))).asReversed()
                    val f =
                      props.firstNotNullOfOrNull { it.child("latin")?.get("typeface") } ?: "Calibri"
                    val font =
                      if (f.startsWith("+"))
                        theme
                          ?.first(if (f.startsWith("+mj")) "majorFont" else "minorFont")
                          ?.child("latin")
                          ?.get("typeface") ?: "Calibri"
                      else f
                    Run(
                        t.text(),
                        Ink(
                          font,
                          (props.firstNotNullOfOrNull { it["sz"]?.toFloatOrNull() }?.div(100)
                            ?: if (title) 30f else 18f),
                          props.firstNotNullOfOrNull { it["b"] } == "1",
                          props.firstNotNullOfOrNull { it["i"] } == "1",
                          color = props.firstNotNullOfOrNull { color(it, theme) } ?: Color.BLACK,
                        ),
                      )
                      .also(info::observe)
                  }
                val bullet =
                  if (pp?.child("buNone") != null) null
                  else (pp?.child("buChar") ?: style?.child("buChar"))?.get("char")
                val content =
                  if (bullet == null || runs.isEmpty()) runs
                  else listOf(Run("$bullet  ", runs.first().ink)) + runs
                fun distance(tag: String): Float {
                  val spacing = pp?.child(tag) ?: style?.child(tag)
                  return spacing?.child("spcPts")?.get("val")?.toFloatOrNull()?.div(100)
                    ?: ((spacing?.child("spcPct")?.get("val")?.toFloatOrNull() ?: 0f) / 100000 *
                      (runs.firstOrNull()?.ink?.pt ?: 18f))
                }
                Paragraph(
                  content,
                  alignment(
                    when (pp?.get("algn") ?: style?.get("algn")) {
                      "ctr" -> "center"
                      "r" -> "right"
                      else -> null
                    }
                  ),
                  before = distance("spcBef"),
                  after = distance("spcAft"),
                  left = (pp?.get("marL") ?: style?.get("marL"))?.toFloatOrNull()?.div(12700) ?: 0f,
                  indent =
                    (pp?.get("indent") ?: style?.get("indent"))?.toFloatOrNull()?.div(12700) ?: 0f,
                )
              }
          val bodyPr = listOfNotNull(body.child("bodyPr"), l?.first("bodyPr"), m?.first("bodyPr"))
          target +=
            paragraphMarks(
              paras,
              rect,
              fonts,
              anchor = bodyPr.firstNotNullOfOrNull { it["anchor"] },
            )
        }
        if (slide["showMasterSp"] != "0") {
          if (masterPath != null)
            masterShapes
              .filter { it.first("ph") == null }
              .forEach { draw(it, masterPath, null, null, marks) }
          if (layoutPath != null)
            layoutShapes
              .filter { it.first("ph") == null }
              .forEach { draw(it, layoutPath, null, null, marks) }
        }
        for (s in shapes(slide)) {
          val l = match(s, layoutShapes)
          draw(s, file, l, match(l ?: s, masterShapes), marks)
        }
        pkg.target(file, "notesSlide")?.let(::node)?.let { notes ->
          val lines =
            shapes(notes)
              .filter { it.first("ph")?.get("type") in setOf(null, "body") }
              .flatMap { it.all("t").map(Node::text).toList() }
          info.add("Slide ${index+1}", lines.joinToString("\n"), "Note del relatore")
        }
        OfficePage(paper, marks)
      }
      .also { if (it.isEmpty()) damaged("Presentazione senza slide") }
  }
}

internal fun OdfReader.slides(fonts: OfficeFonts): List<OfficePage> {
  val slides =
    content.child("body")?.child("presentation")?.kids.orEmpty().filter { it.name == "page" }
  if (slides.isEmpty()) damaged("Presentazione ODP senza slide")
  return slides.mapIndexed { i, page ->
    val master = masters[page["master-page-name"]]
    val paper = paper(master).copy(left = 0f, top = 0f, right = 0f, bottom = 0f)
    val marks = mutableListOf<Mark>()
    val bg =
      listOf(page, master).firstNotNullOfOrNull {
        props(it?.get("style-name"), "drawing-page-properties")["fill-color"].rgb()
      }
    if (bg != null) marks += boxMark(0f, 0f, paper.width, paper.height, bg)
    fun draw(n: Node) {
      if (n.name == "g") {
        n.kids.forEach(::draw)
        return
      }
      val rect = Frame(n["x"].points(), n["y"].points(), n["width"].points(), n["height"].points())
      if (rect.w <= 0 || rect.h <= 0) return
      val style = props(n["style-name"], "graphic-properties")
      marks +=
        boxMark(
          rect.x,
          rect.y,
          rect.w,
          rect.h,
          style["fill-color"].rgb(),
          style["stroke-color"].rgb(),
          n.name == "ellipse",
        )
      val pic = picture(n)
      if (pic != null) marks += imageMark(pic, rect.x, rect.y, rect.w, rect.h)
      if (n.first("object") != null && pic == null)
        marks += placeholder("Oggetto incorporato", rect, fonts, info)
      val paragraphs = n.all("p").map { para(it) }.toList()
      if (paragraphs.isNotEmpty()) marks += paragraphMarks(paragraphs, rect, fonts)
    }
    master
      ?.kids
      .orEmpty()
      .filter { it["placeholder"] != "true" && it["class"] == null && it.name != "notes" }
      .forEach(::draw)
    page.kids.filter { it.name != "notes" }.forEach(::draw)
    info.add(
      "Slide ${i+1}",
      page
        .child("notes")
        ?.kids
        ?.filter { it["class"] == "notes" || it.name == "p" }
        ?.flatMap { if (it.name == "p") listOf(it) else it.all("p").toList() }
        ?.joinToString("\n", transform = ::odfText),
      "Note del relatore",
    )
    OfficePage(paper, marks)
  }
}
