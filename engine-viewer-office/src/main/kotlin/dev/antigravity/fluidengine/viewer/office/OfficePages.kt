package dev.antigravity.fluidengine.viewer.office

import android.graphics.*
import android.text.*
import android.text.style.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidPageSource
import kotlin.math.*
import kotlinx.coroutines.*

/**
 * StaticLayout accepts integer widths. Preserve fractional point sizes and use linear glyph
 * advances so that rounding each individual glyph does not move words across the right margin.
 */
internal const val Units = 16f

internal val Paragraph.textLeft get() = left + minOf(0f, indent)

internal fun layout(p: Paragraph, width: Float, fonts: OfficeFonts): StaticLayout {
  val text = SpannableStringBuilder()
  fun apply(paint: TextPaint, ink: Ink) {
    paint.typeface = fonts.face(ink)
    paint.textSize = Units * ink.pt.coerceIn(3f, 400f) * if (ink.baseline == 0) 1f else .7f
    paint.color = ink.color
    paint.bgColor = ink.highlight
    paint.isUnderlineText = ink.underline
    paint.isStrikeThruText = ink.strike
    paint.baselineShift = (ink.baseline * paint.textSize * .35f).toInt()
  }
  for (run in p.runs) {
    val start = text.length
    text.append(run.text)
    if (text.length > start)
      text.setSpan(
        object : MetricAffectingSpan() {
          override fun updateMeasureState(tp: TextPaint) = apply(tp, run.ink)

          override fun updateDrawState(tp: TextPaint) = apply(tp, run.ink)
        },
        start,
        text.length,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
      )
  }
  if (p.indent != 0f && text.isNotEmpty())
    text.setSpan(
      LeadingMarginSpan.Standard((maxOf(0f, p.indent) * Units).toInt(), (maxOf(0f, -p.indent) * Units).toInt()),
      0,
      text.length,
      Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
    )
  val paint =
    TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).also {
      apply(it, p.runs.firstOrNull()?.ink ?: Ink())
    }
  return StaticLayout.Builder.obtain(
      text,
      0,
      text.length,
      paint,
      (width * Units).toInt().coerceIn(1, (4000 * Units).toInt()),
    )
    .setAlignment(p.align)
    .setIncludePad(false)
    .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
    .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
    .setJustificationMode(
      if (p.justify) Layout.JUSTIFICATION_MODE_INTER_WORD else Layout.JUSTIFICATION_MODE_NONE
    )
    .setLineSpacing(0f, p.leading.coerceIn(.5f, 4f))
    .build()
}

internal fun interface Mark {
  fun draw(canvas: Canvas)
}

internal data class OfficePage(val paper: Paper, val marks: List<Mark>)

internal class LaidOutText(
  private val layout: StaticLayout,
  private val x: Float,
  private val y: Float,
  private val start: Float,
  private val height: Float,
) : Mark {
  fun lines(): List<String> =
    (0 until layout.lineCount)
      .filter {
        layout.getLineTop(it) / Units >= start && layout.getLineTop(it) / Units < start + height
      }
      .map {
        layout.text.subSequence(layout.getLineStart(it), layout.getLineEnd(it)).toString().trimEnd()
      }

  override fun draw(c: Canvas) {
    val save = c.save()
    c.translate(x, y)
    c.clipRect(0f, 0f, layout.width / Units, height)
    c.translate(0f, -start)
    c.scale(1f / Units, 1f / Units)
    layout.draw(c)
    c.restoreToCount(save)
  }
}

internal fun textMark(
  layout: StaticLayout,
  x: Float,
  y: Float,
  start: Float = 0f,
  height: Float = layout.height / Units,
) = LaidOutText(layout, x, y, start, height)

internal fun boxMark(
  x: Float,
  y: Float,
  w: Float,
  h: Float,
  fill: Int?,
  line: Int? = null,
  oval: Boolean = false,
) = Mark { c ->
  val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  val rect = RectF(x, y, x + w, y + h)
  fun draw() {
    if (oval) c.drawOval(rect, paint) else c.drawRect(rect, paint)
  }
  if (fill != null) {
    paint.color = fill
    draw()
  }
  if (line != null) {
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = .6f
    paint.color = line
    draw()
  }
}

internal fun imageMark(pic: Picture, x: Float, y: Float, w: Float, h: Float) = Mark { c ->
  val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeByteArray(pic.bytes, 0, pic.bytes.size, opts)
  if (opts.outWidth > 0 && opts.outHeight > 0) {
    opts.inJustDecodeBounds = false
    opts.inSampleSize = 1
    while (
      opts.outWidth.toLong() * opts.outHeight / opts.inSampleSize / opts.inSampleSize > 3_000_000
    ) opts.inSampleSize *= 2
    BitmapFactory.decodeByteArray(pic.bytes, 0, pic.bytes.size, opts)?.let { bitmap ->
      try {
        c.drawBitmap(bitmap, null, RectF(x, y, x + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
      } finally {
        bitmap.recycle()
      }
    }
  }
}

/** [page] on white paper, stretched to [target] (at most 4 megapixels): the viewer's pages and the thumbnails. */
internal fun drawOfficePage(page: OfficePage, target: IntSize): Bitmap {
  val w = target.width.coerceAtLeast(1)
  val h = target.height.coerceAtLeast(1)
  val scale = minOf(1.0, sqrt(4_000_000.0 / (w.toDouble() * h)), 8192.0 / w, 8192.0 / h)
  val bitmap =
    Bitmap.createBitmap(max(1, (w * scale).toInt()), max(1, (h * scale).toInt()), Bitmap.Config.ARGB_8888)
  val c = Canvas(bitmap)
  c.drawColor(Color.WHITE)
  c.scale(bitmap.width / page.paper.width, bitmap.height / page.paper.height)
  synchronized(page) { page.marks.forEach { it.draw(c) } }
  return bitmap
}

internal class OfficePages(private val worker: CoroutineScope) :
  FluidPageSource, java.io.Closeable {
  private val pages = ArrayList<OfficePage>()
  private val first = CompletableDeferred<Unit>()
  override var count by mutableIntStateOf(0)
    private set

  override var countFinal by mutableStateOf(false)
    private set

  @Volatile private var failure: Throwable? = null

  suspend fun start(onPartial: (Exception) -> Unit, make: suspend (OfficePages) -> Unit) {
    val job =
      worker.launch {
        try {
          make(this@OfficePages)
          countFinal = true
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          failure = e
          countFinal = true
          if (!first.completeExceptionally(e)) onPartial(e)
        }
      }
    job.invokeOnCompletion { e ->
      if (!first.isCompleted)
        first.completeExceptionally(e ?: IllegalStateException("Nessuna pagina"))
    }
    try {
      first.await()
    } catch (e: Throwable) {
      close()
      throw e
    }
  }

  suspend fun append(page: OfficePage) {
    currentCoroutineContext().ensureActive()
    synchronized(pages) {
      if (pages.size >= 3000) tooLarge("Oltre 3000 pagine")
      pages += page
      count = pages.size
    }
    first.complete(Unit)
  }

  internal fun at(index: Int): OfficePage =
    synchronized(pages) { pages.getOrNull(index) ?: throw failure ?: IndexOutOfBoundsException() }

  override suspend fun sheet(index: Int) = at(index).paper.sheet()

  internal fun bodyLines(index: Int) =
    at(index).marks.filterIsInstance<LaidOutText>().flatMap { it.lines() }

  override suspend fun size(index: Int) =
    at(index).paper.let { IntSize(it.width.roundToInt(), it.height.roundToInt()) }

  override suspend fun render(index: Int, target: IntSize): ImageBitmap =
    withContext(Dispatchers.Default) { drawOfficePage(at(index), target).asImageBitmap() }

  override fun close() {
    worker.cancel()
    synchronized(pages) { pages.clear() }
  }
}

internal suspend fun paginate(sections: List<Section>, pages: OfficePages, fonts: OfficeFonts) {
  for (section in sections) {
    val paper = section.paper.checked()
    val width = paper.width - paper.left - paper.right
    var marks = mutableListOf<Mark>()
    var y = paper.top
    var sectionIndex = 0
    val notes = mutableListOf<StaticLayout>()
    var noteHeight = 0f
    fun room() = paper.height - paper.bottom - noteHeight - y
    suspend fun finish() {
      val number = pages.count + 1
      val variant =
        when {
          sectionIndex == 0 && paper.firstDifferent -> "first"
          number % 2 == 0 -> "even"
          else -> "default"
        }
      fun marginText(texts: Map<String, String>, position: Float) {
        val template =
          texts[variant] ?: (if (variant == "first") null else texts["default"]) ?: return
        // NUMPAGES is resolved when this page is rendered, after the progressive count has settled.
        marks += Mark { canvas ->
          val text =
            template
              .replace("{PAGE}", number.toString())
              .replace("{NUMPAGES}", if (pages.countFinal) pages.count.toString() else "…")
          textMark(layout(simpleParagraph(text, Ink(pt = 9f)), width, fonts), paper.left, position)
            .draw(canvas)
        }
      }
      marginText(paper.headers, (paper.headerDistance - 5).coerceAtLeast(0f))
      marginText(paper.footers, paper.height - paper.footerDistance - 8f)
      var noteY = paper.height - paper.bottom - noteHeight
      for (note in notes) {
        marks += textMark(note, paper.left, noteY)
        noteY += note.height / Units
      }
      pages.append(OfficePage(paper, marks.toList()))
      sectionIndex++
      marks = mutableListOf()
      y = paper.top
      notes.clear()
      noteHeight = 0f
    }
    suspend fun paragraph(p: Paragraph) {
      val l = layout(p, width - p.textLeft - p.right, fonts)
      val pending = p.notes.map { layout(simpleParagraph(it, Ink(pt = 8f)), width, fonts) }
      val reserve =
        minOf(
          pending.sumOf { it.height.toDouble() / Units }.toFloat(),
          (paper.height - paper.top - paper.bottom) * .4f,
        )
      if (
        y > paper.top &&
          (p.before + reserve + l.getLineBottom(0) / Units > room() ||
            p.keep &&
              l.height / Units + p.before <= paper.height - paper.top - paper.bottom &&
              l.height / Units + p.before > room())
      )
        finish()
      noteHeight += reserve
      notes += pending
      y += p.before.coerceAtMost(room().coerceAtLeast(0f))
      var firstLine = 0
      while (firstLine < l.lineCount) {
        val start = l.getLineTop(firstLine) / Units
        var end = firstLine + 1
        while (end < l.lineCount && l.getLineBottom(end) / Units - start <= room()) end++
        val h = l.getLineBottom(end - 1) / Units - start
        marks += textMark(l, paper.left + p.textLeft, y, start, h)
        y += h
        firstLine = end
        if (end < l.lineCount) finish()
      }
      y += p.after
    }
    suspend fun table(t: Table) {
      val n = (t.rows.maxOfOrNull { row -> row.cells.sumOf { it.span } } ?: 1).coerceIn(1, 256)
      val weights = List(n) { t.widths.getOrNull(it)?.coerceAtLeast(1f) ?: 1f }
      val total = weights.sum()
      val edges = FloatArray(n + 1)
      for (i in 0 until n) edges[i + 1] = edges[i] + width * weights[i] / total
      val headers = mutableListOf<Pair<Float, List<Mark>>>()
      for (row in t.rows) {
        var col = 0
        val rowMarks = mutableListOf<Mark>()
        val lines = mutableListOf<Pair<Float, Float>>()
        var h = 12f
        for (cell in row.cells) {
          if (col == n) break
          val end = (col + cell.span).coerceAtMost(n)
          val x = paper.left + edges[col]
          val w = edges[end] - edges[col]
          var pos = 2f
          val ink =
            cell.paragraphs.map { p ->
              val l = layout(p, w - 6f - p.textLeft - p.right, fonts)
              val top = pos + p.before
              for (i in 0 until l.lineCount) {
                lines += (top + l.getLineTop(i) / Units) to (top + l.getLineBottom(i) / Units)
              }
              textMark(l, x + 3f + p.textLeft, pos + p.before).also {
                pos += l.height / Units + p.before + p.after
              }
            }
          h = max(h, pos + 2f)
          rowMarks += Mark { c ->
            boxMark(x, 0f, w, h, cell.fill).draw(c)
            val pen =
              Paint().apply {
                color = Color.DKGRAY
                strokeWidth = .5f
              }
            c.drawLine(x, 0f, x, h, pen)
            c.drawLine(x + w, 0f, x + w, h, pen)
            if (!cell.continued) c.drawLine(x, 0f, x + w, 0f, pen)
            if (!cell.continues) c.drawLine(x, h, x + w, h, pen)
            ink.forEach { it.draw(c) }
          }
          col = end
        }
        fun slice(offset: Float, height: Float, draws: List<Mark>) {
          val top = y
          marks += Mark { c ->
            val saved = c.save()
            c.clipRect(paper.left, top, paper.width - paper.right, top + height)
            c.translate(0f, top - offset)
            draws.forEach { it.draw(c) }
            c.restoreToCount(saved)
          }
          y += height
        }
        // Intersect the free space of every cell. A continuation normally starts between lines,
        // including when the columns use different font sizes.
        val occupied = mutableListOf<Pair<Float, Float>>()
        for ((top, bottom) in lines.sortedBy { it.first }) {
          val previous = occupied.lastOrNull()
          if (previous != null && top < previous.second)
            occupied[occupied.lastIndex] = previous.first to maxOf(previous.second, bottom)
          else occupied += top to bottom
        }
        var offset = 0f
        while (offset < h) {
          if (
            offset > 0f ||
              room() < 1 ||
              (offset == 0f &&
                y > paper.top &&
                room() < h &&
                h < paper.height - paper.top - paper.bottom)
          ) {
            finish()
            if (!row.header && headers.sumOf { it.first.toDouble() } < room() / 2)
              headers.forEach { slice(0f, it.first, it.second) }
          }
          val limit = minOf(h, offset + room())
          val cut =
            occupied.firstOrNull { it.first > offset && it.first < limit && it.second > limit }
          val piece = (cut?.first ?: limit) - offset
          slice(offset, piece, rowMarks)
          offset += piece
        }
        if (row.header) headers += h to rowMarks
      }
    }
    for (block in section.blocks) {
      currentCoroutineContext().ensureActive()
      when (block) {
        PageBreak -> finish()
        is Paragraph -> paragraph(block)
        is Table -> table(block)
        is Picture -> {
          val scale =
            minOf(1f, width / block.w, (paper.height - paper.top - paper.bottom) / block.h)
          val h = block.h * scale
          if (h > room() && y > paper.top) finish()
          marks += imageMark(block, paper.left, y, block.w * scale, h)
          y += h
        }
      }
    }
    finish()
  }
}
